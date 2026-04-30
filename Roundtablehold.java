import com.sun.j3d.utils.universe.SimpleUniverse;
import com.sun.j3d.utils.geometry.Cylinder;
import com.sun.j3d.utils.geometry.Sphere;
import com.sun.j3d.utils.picking.PickCanvas;
import com.sun.j3d.utils.picking.PickResult;

import javax.media.j3d.*;
import javax.vecmath.*;
import java.awt.*;
import java.awt.event.*;
import java.util.Enumeration;
import javax.swing.*;

/**
 * RoundtableHold -- Java3D edition
 *
 * Navigation  : OrbitBehavior (drag to orbit, scroll to zoom).
 *               A ViewpointRestrictor Behavior runs every frame and clamps
 *               the camera inside the room cylinder and above the floor so
 *               the viewer can never escape through the walls.
 *
 * Gameplay    : Click one of the three glowing keys to collect it.
 *               Each collected key lights the matching lantern above the
 *               exit door.  Once all three are found the door panel is
 *               hidden via a Switch node and a message appears.
 *
 */
public class RoundtableHold extends JFrame {

    // -- Room geometry constants --------------------------------------------
    private static final int   ROOM_SEGMENTS = 64;
    private static final float ROOM_RADIUS   = 14.0f;
    private static final float ROOM_HEIGHT   = 6.0f;

    private static final int   TABLE_SEGMENTS = 48;
    private static final float TABLE_RADIUS   = 3.5f;
    private static final float TABLE_HEIGHT   = 0.18f;
    private static final float TABLE_Y        = -1.2f;

    private static final float LEG_RADIUS = 0.06f;
    private static final float LEG_HEIGHT = 1.1f;
    private static final float LEG_Y      = TABLE_Y - (LEG_HEIGHT / 2f) - (TABLE_HEIGHT / 2f);

    private static final float GEM_Y       = TABLE_Y + TABLE_HEIGHT / 2f + 0.35f;
    private static final float GEM_SIZE    = 0.22f;
    private static final float GEM_SCALE_Y = 2.8f;

    // -- Door constants -----------------------------------------------------
    private static final float DOOR_WIDTH   = 1.6f;
    private static final float DOOR_HEIGHT  = 2.8f;
    private static final float DOOR_Z       = -(ROOM_RADIUS - 0.2f);
    private static final float DOOR_PANEL_Y = -ROOM_HEIGHT / 2f + DOOR_HEIGHT / 2f;

    // -- Colours ------------------------------------------------------------
    private static final Color3f YELLOW_LIGHT = new Color3f(1.0f, 0.85f, 0.2f);
    private static final Color3f AMBIENT_COL  = new Color3f(0.35f, 0.30f, 0.38f);
    private static final Color3f LANTERN_OFF  = new Color3f(0.05f, 0.04f, 0.02f);
    private static final Color3f LANTERN_ON   = new Color3f(1.00f, 0.80f, 0.20f);

    // -- Key world positions ------------------------------------------------
    private static final float[][] KEY_POSITIONS = {
        { -7.5f, -ROOM_HEIGHT / 2f + 0.4f,  3.5f },
        {  5.0f, -ROOM_HEIGHT / 2f + 0.4f, -8.0f },
        {  8.5f, -ROOM_HEIGHT / 2f + 0.4f,  5.0f }
    };

    // -- Mutable scene state ------------------------------------------------
    private int keysFound = 0;
    private final boolean[]      keyCollected  = { false, false, false };
    private final BranchGroup[]  keyBGs        = new BranchGroup[3];
    private final PointLight[]   lanternLights = new PointLight[3];
    private final Material[]     lanternMats   = new Material[3];
    private Switch doorSwitch;
    private JLabel statusLabel;

    // -- Picking ------------------------------------------------------------
    private PickCanvas pickCanvas;

    // -----------------------------------------------------------------------
    public RoundtableHold() {
        super("Roundtable Hold -- Elden Ring");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        statusLabel = new JLabel(
            "  Find the three keys to open the door.   Keys: 0 / 3",
            SwingConstants.CENTER);
        statusLabel.setForeground(new Color(220, 180, 80));
        statusLabel.setBackground(new Color(10, 8, 15));
        statusLabel.setOpaque(true);
        statusLabel.setFont(new Font("Serif", Font.ITALIC, 14));
        statusLabel.setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0));
        add(statusLabel, BorderLayout.SOUTH);

        setSize(1100, 750);
        setLocationRelativeTo(null);

        GraphicsConfiguration gc = SimpleUniverse.getPreferredConfiguration();
        Canvas3D canvas = new Canvas3D(gc);
        canvas.setFocusable(true);
        add(canvas, BorderLayout.CENTER);

        SimpleUniverse universe = new SimpleUniverse(canvas);

        TransformGroup vpTG =
            universe.getViewingPlatform().getViewPlatformTransform();
        Transform3D camT = new Transform3D();
        camT.lookAt(new Point3d(0, 2.5, 10.5),
                    new Point3d(0, 0, 0),
                    new Vector3d(0, 1, 0));
        camT.invert();
        vpTG.setTransform(camT);

        BranchGroup scene = buildScene(universe, canvas);
        scene.compile();
        universe.addBranchGraph(scene);

        pickCanvas = new PickCanvas(canvas, scene);
        pickCanvas.setMode(PickCanvas.GEOMETRY_INTERSECT_INFO);
        pickCanvas.setTolerance(4.0f);

        canvas.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { handlePick(e); }
        });

        setVisible(true);
        canvas.requestFocusInWindow();
    }

    // =======================================================================
    // SCENE GRAPH
    // =======================================================================
    private BranchGroup buildScene(SimpleUniverse universe, Canvas3D canvas) {
        BranchGroup root = new BranchGroup();
        root.setCapability(BranchGroup.ALLOW_CHILDREN_READ);
        root.setCapability(BranchGroup.ALLOW_CHILDREN_WRITE);

        Background bg = new Background(new Color3f(0.03f, 0.02f, 0.05f));
        bg.setApplicationBounds(worldBounds());
        root.addChild(bg);

        addLighting(root);
        root.addChild(buildRoom());
        root.addChild(buildTable());
        root.addChild(buildGem());
        root.addChild(buildDoor());

        for (int i = 0; i < 3; i++) root.addChild(buildKey(i));

        // First-person video-game style movement.
        // WASD moves, mouse drag looks, Space moves up, Shift moves down.
        // This replaces OrbitBehavior and also clamps the camera inside the room.
        TransformGroup viewTG =
            universe.getViewingPlatform().getViewPlatformTransform();

        FirstPersonControls controls = new FirstPersonControls(
            canvas,
            viewTG,
            new Vector3f(0f, 0.6f, 10.5f),
            ROOM_RADIUS - 1.0f,
            -ROOM_HEIGHT / 2f + 0.5f,
             ROOM_HEIGHT / 2f - 0.3f);
        controls.setSchedulingBounds(worldBounds());
        root.addChild(controls);

        return root;
    }

    // -- Lighting -----------------------------------------------------------
    private void addLighting(BranchGroup root) {
        AmbientLight ambient = new AmbientLight(AMBIENT_COL);
        ambient.setInfluencingBounds(worldBounds());
        root.addChild(ambient);

        DirectionalLight fillDown = new DirectionalLight(
            new Color3f(0.40f, 0.35f, 0.45f), new Vector3f(0f, -1f, 0f));
        fillDown.setInfluencingBounds(worldBounds());
        root.addChild(fillDown);

        DirectionalLight fillUp = new DirectionalLight(
            new Color3f(0.25f, 0.22f, 0.28f), new Vector3f(0f, 1f, 0f));
        fillUp.setInfluencingBounds(worldBounds());
        root.addChild(fillUp);

        Color3f wallFill = new Color3f(0.30f, 0.26f, 0.32f);
        float[][] wallDirs = { {1,0,0},{-1,0,0},{0,0,1},{0,0,-1} };
        for (float[] d : wallDirs) {
            DirectionalLight wl = new DirectionalLight(
                wallFill, new Vector3f(d[0], d[1], d[2]));
            wl.setInfluencingBounds(worldBounds());
            root.addChild(wl);
        }

        PointLight gemLight = new PointLight(YELLOW_LIGHT,
            new Point3f(0f, GEM_Y, 0f), new Point3f(0.1f, 0.05f, 0.02f));
        gemLight.setInfluencingBounds(worldBounds());
        root.addChild(gemLight);

        PointLight fillLight = new PointLight(
            new Color3f(0.3f, 0.22f, 0.05f),
            new Point3f(0f, TABLE_Y - 0.5f, 0f),
            new Point3f(0.2f, 0.1f, 0.0f));
        fillLight.setInfluencingBounds(worldBounds());
        root.addChild(fillLight);

        Color3f torchColor = new Color3f(1.0f, 0.55f, 0.1f);
        double[] torchAngles = { 0, Math.PI/2, Math.PI, 3*Math.PI/2 };
        for (double a : torchAngles) {
            float tx = (ROOM_RADIUS - 0.8f) * (float) Math.cos(a);
            float tz = (ROOM_RADIUS - 0.8f) * (float) Math.sin(a);
            PointLight tl = new PointLight(torchColor,
                new Point3f(tx, 1.2f, tz), new Point3f(0.05f, 0.12f, 0.0f));
            tl.setInfluencingBounds(worldBounds());
            root.addChild(tl);
        }
    }

    // =======================================================================
    // ROOM (unchanged from original)
    // =======================================================================
    private TransformGroup buildRoom() {
        TransformGroup tg = new TransformGroup();
        Appearance wallApp = stoneMaterial(
            new Color3f(0.20f, 0.16f, 0.14f),
            new Color3f(0.50f, 0.42f, 0.36f));
        tg.addChild(new Shape3D(
            buildInwardCylinderGeo(ROOM_RADIUS, ROOM_HEIGHT, ROOM_SEGMENTS), wallApp));
        tg.addChild(disc(ROOM_RADIUS, ROOM_SEGMENTS, -ROOM_HEIGHT / 2f,
            stoneMaterialEmissive(
                new Color3f(0.14f, 0.11f, 0.09f),
                new Color3f(0.32f, 0.26f, 0.20f),
                new Color3f(0.18f, 0.14f, 0.10f))));
        tg.addChild(discFlipped(ROOM_RADIUS, ROOM_SEGMENTS, ROOM_HEIGHT / 2f,
            stoneMaterialEmissive(
                new Color3f(0.12f, 0.09f, 0.11f),
                new Color3f(0.28f, 0.22f, 0.26f),
                new Color3f(0.14f, 0.11f, 0.13f))));
        return tg;
    }

    private GeometryArray buildInwardCylinderGeo(float r, float h, int seg) {
        int triCount = seg * 2;
        TriangleArray geo = new TriangleArray(triCount * 3,
            GeometryArray.COORDINATES | GeometryArray.NORMALS);
        float half = h / 2f;
        float[] coords  = new float[triCount * 3 * 3];
        float[] normals = new float[triCount * 3 * 3];
        int idx = 0;
        for (int i = 0; i < seg; i++) {
            double a0 = 2.0 * Math.PI * i / seg;
            double a1 = 2.0 * Math.PI * (i + 1) / seg;
            float x0=(float)(r*Math.cos(a0)), z0=(float)(r*Math.sin(a0));
            float x1=(float)(r*Math.cos(a1)), z1=(float)(r*Math.sin(a1));
            float mx=-(float)(Math.cos((a0+a1)/2));
            float mz=-(float)(Math.sin((a0+a1)/2));
            float[][] tri1 = { {x1,-half,z1},{x1,half,z1},{x0,half,z0} };
            float[][] tri2 = { {x1,-half,z1},{x0,half,z0},{x0,-half,z0} };
            for (float[][] tri : new float[][][]{tri1, tri2}) {
                for (float[] v : tri) {
                    coords[idx]=v[0]; coords[idx+1]=v[1]; coords[idx+2]=v[2];
                    normals[idx]=mx;  normals[idx+1]=0f;  normals[idx+2]=mz;
                    idx += 3;
                }
            }
        }
        geo.setCoordinates(0, coords);
        geo.setNormals(0, normals);
        return geo;
    }

    private TransformGroup disc(float radius, int seg, float y, Appearance app) {
        TransformGroup tg = new TransformGroup();
        Transform3D t = new Transform3D();
        t.setTranslation(new Vector3f(0f, y, 0f));
        tg.setTransform(t);
        tg.addChild(new Shape3D(buildDiscGeo(radius, seg, 1f), app));
        return tg;
    }

    private TransformGroup discFlipped(float radius, int seg, float y, Appearance app) {
        TransformGroup tg = new TransformGroup();
        Transform3D t = new Transform3D();
        t.setTranslation(new Vector3f(0f, y, 0f));
        tg.setTransform(t);
        tg.addChild(new Shape3D(buildDiscGeo(radius, seg, -1f), app));
        return tg;
    }

    private GeometryArray buildDiscGeo(float radius, int seg, float normalY) {
        int vCount = seg + 2;
        TriangleFanArray geo = new TriangleFanArray(vCount,
            GeometryArray.COORDINATES | GeometryArray.NORMALS, new int[]{vCount});
        float[] coords  = new float[vCount * 3];
        float[] normals = new float[vCount * 3];
        coords[0]=0; coords[1]=0; coords[2]=0;
        normals[0]=0; normals[1]=normalY; normals[2]=0;
        for (int i = 0; i <= seg; i++) {
            double angle = normalY > 0
                ? 2.0 * Math.PI * i / seg
                : -2.0 * Math.PI * i / seg;
            int base = (i + 1) * 3;
            coords[base]   = (float)(radius * Math.cos(angle));
            coords[base+1] = 0;
            coords[base+2] = (float)(radius * Math.sin(angle));
            normals[base]=0; normals[base+1]=normalY; normals[base+2]=0;
        }
        geo.setCoordinates(0, coords);
        geo.setNormals(0, normals);
        return geo;
    }

    // =======================================================================
    // TABLE (unchanged from original)
    // =======================================================================
    private TransformGroup buildTable() {
        TransformGroup tg = new TransformGroup();
        Appearance woodApp = stoneMaterial(
            new Color3f(0.15f, 0.09f, 0.05f),
            new Color3f(0.38f, 0.24f, 0.14f));
        Cylinder top = new Cylinder(TABLE_RADIUS, TABLE_HEIGHT,
            Cylinder.GENERATE_NORMALS, TABLE_SEGMENTS, 1, woodApp);
        TransformGroup topTG = new TransformGroup();
        Transform3D topT = new Transform3D();
        topT.setTranslation(new Vector3f(0f, TABLE_Y, 0f));
        topTG.setTransform(topT);
        topTG.addChild(top);
        tg.addChild(topTG);
        float legDist = TABLE_RADIUS * 0.6f;
        float[][] offsets = {
            { legDist, legDist},{-legDist, legDist},
            {-legDist,-legDist},{ legDist,-legDist}
        };
        for (float[] off : offsets) tg.addChild(tableLeg(off[0], off[1], woodApp));
        return tg;
    }

    private TransformGroup tableLeg(float x, float z, Appearance app) {
        Cylinder leg = new Cylinder(LEG_RADIUS, LEG_HEIGHT,
            Cylinder.GENERATE_NORMALS, 12, 1, app);
        Transform3D t = new Transform3D();
        t.setTranslation(new Vector3f(x, LEG_Y, z));
        TransformGroup tg = new TransformGroup(t);
        tg.addChild(leg);
        return tg;
    }

    // =======================================================================
    // GEM (unchanged from original)
    // =======================================================================
    private TransformGroup buildGem() {
        Shape3D gemShape = new Shape3D(buildOctahedronGeo(GEM_SIZE), gemMaterial());
        TransformGroup spinTG = new TransformGroup();
        spinTG.setCapability(TransformGroup.ALLOW_TRANSFORM_WRITE);
        Alpha rotAlpha = new Alpha(-1, 8000);
        RotationInterpolator rotator = new RotationInterpolator(
            rotAlpha, spinTG, new Transform3D(), 0f, (float)(2 * Math.PI));
        rotator.setSchedulingBounds(worldBounds());
        spinTG.addChild(gemShape);
        spinTG.addChild(rotator);
        PointLight glow = new PointLight(YELLOW_LIGHT,
            new Point3f(0, 0, 0), new Point3f(0.05f, 0.1f, 0.0f));
        glow.setInfluencingBounds(worldBounds());
        spinTG.addChild(glow);
        Transform3D pos = new Transform3D();
        pos.setTranslation(new Vector3f(0f, GEM_Y, 0f));
        Transform3D stretch = new Transform3D();
        stretch.setScale(new Vector3d(1.0, GEM_SCALE_Y, 1.0));
        pos.mul(stretch);
        TransformGroup posTG = new TransformGroup(pos);
        posTG.addChild(spinTG);
        return posTG;
    }

    private GeometryArray buildOctahedronGeo(float s) {
        float[][] verts = {
            {s,0,0},{0,s,0},{0,0,s},   {0,0,s},{0,s,0},{-s,0,0},
            {-s,0,0},{0,s,0},{0,0,-s}, {0,0,-s},{0,s,0},{s,0,0},
            {s,0,0},{0,0,s},{0,-s,0},  {0,0,s},{-s,0,0},{0,-s,0},
            {-s,0,0},{0,0,-s},{0,-s,0},{0,0,-s},{s,0,0},{0,-s,0},
        };
        TriangleArray geo = new TriangleArray(verts.length,
            GeometryArray.COORDINATES | GeometryArray.NORMALS);
        float[] coords  = new float[verts.length * 3];
        float[] normals = new float[verts.length * 3];
        for (int i = 0; i < verts.length; i += 3) {
            Vector3f a = v(verts[i]), b = v(verts[i+1]), c = v(verts[i+2]);
            Vector3f ab = new Vector3f(); ab.sub(b, a);
            Vector3f ac = new Vector3f(); ac.sub(c, a);
            Vector3f n  = new Vector3f(); n.cross(ab, ac); n.normalize();
            for (int j = 0; j < 3; j++) {
                int idx = (i + j) * 3;
                coords[idx]=verts[i+j][0]; coords[idx+1]=verts[i+j][1]; coords[idx+2]=verts[i+j][2];
                normals[idx]=n.x; normals[idx+1]=n.y; normals[idx+2]=n.z;
            }
        }
        geo.setCoordinates(0, coords);
        geo.setNormals(0, normals);
        return geo;
    }

    private Vector3f v(float[] a) { return new Vector3f(a[0], a[1], a[2]); }

    // =======================================================================
    // DOOR -- frame + Switch-controlled panel + three lanterns
    // =======================================================================
    private TransformGroup buildDoor() {
        TransformGroup tg = new TransformGroup();

        Appearance frameApp = stoneMaterial(
            new Color3f(0.18f, 0.14f, 0.10f),
            new Color3f(0.40f, 0.32f, 0.22f));

        tg.addChild(box(0.15f, DOOR_HEIGHT + 0.3f, 0.25f,
            -DOOR_WIDTH/2f - 0.075f,
            -ROOM_HEIGHT/2f + (DOOR_HEIGHT + 0.3f)/2f, DOOR_Z, frameApp));
        tg.addChild(box(0.15f, DOOR_HEIGHT + 0.3f, 0.25f,
             DOOR_WIDTH/2f + 0.075f,
            -ROOM_HEIGHT/2f + (DOOR_HEIGHT + 0.3f)/2f, DOOR_Z, frameApp));
        tg.addChild(box(DOOR_WIDTH + 0.4f, 0.25f, 0.25f,
            0f, -ROOM_HEIGHT/2f + DOOR_HEIGHT + 0.25f/2f, DOOR_Z, frameApp));

        // FIX 3: use a Switch node instead of TransparencyAttributes.setValue().
        // CHILD_ALL = child 0 is rendered (door closed).
        // CHILD_NONE = nothing rendered (door open).
        doorSwitch = new Switch(Switch.CHILD_ALL);
        doorSwitch.setCapability(Switch.ALLOW_SWITCH_WRITE);

        Appearance doorApp = new Appearance();
        Material doorMat = new Material(
            new Color3f(0.10f, 0.07f, 0.04f),
            new Color3f(0f, 0f, 0f),
            new Color3f(0.25f, 0.18f, 0.10f),
            new Color3f(0.15f, 0.12f, 0.08f), 18f);
        doorMat.setLightingEnable(true);
        doorApp.setMaterial(doorMat);
        doorSwitch.addChild(
            box(DOOR_WIDTH, DOOR_HEIGHT, 0.08f, 0f, DOOR_PANEL_Y, DOOR_Z, doorApp));
        tg.addChild(doorSwitch);

        // Three lanterns
        float[] lanternX = { -0.7f, 0f, 0.7f };
        for (int i = 0; i < 3; i++) {
            float lx = lanternX[i];
            float ly = -ROOM_HEIGHT/2f + DOOR_HEIGHT + 0.55f;

            Material lm = new Material(LANTERN_OFF, new Color3f(0f,0f,0f),
                LANTERN_OFF, new Color3f(0.1f,0.08f,0.05f), 10f);
            lm.setLightingEnable(true);
            lm.setCapability(Material.ALLOW_COMPONENT_WRITE);
            lanternMats[i] = lm;

            Appearance lApp = new Appearance();
            lApp.setMaterial(lm);
            Sphere lanternSphere = new Sphere(0.10f, Sphere.GENERATE_NORMALS, 12, lApp);
            TransformGroup lTG = new TransformGroup();
            Transform3D lt = new Transform3D();
            lt.setTranslation(new Vector3f(lx, ly, DOOR_Z - 0.05f));
            lTG.setTransform(lt);
            lTG.addChild(lanternSphere);
            tg.addChild(lTG);

            PointLight pl = new PointLight(LANTERN_ON,
                new Point3f(lx, ly, DOOR_Z - 0.05f),
                new Point3f(0.05f, 0.1f, 0.0f));
            pl.setEnable(false);
            pl.setCapability(Light.ALLOW_STATE_WRITE);
            pl.setInfluencingBounds(worldBounds());
            tg.addChild(pl);
            lanternLights[i] = pl;
        }
        return tg;
    }

    // =======================================================================
    // KEYS
    // =======================================================================
    private BranchGroup buildKey(int index) {
        float[] pos = KEY_POSITIONS[index];
        Appearance keyApp = keyMaterial();

        TransformGroup keyTG = new TransformGroup();
        keyTG.setCapability(TransformGroup.ALLOW_TRANSFORM_WRITE);
        keyTG.setCapability(TransformGroup.ALLOW_CHILDREN_READ);
        keyTG.setCapability(TransformGroup.ALLOW_CHILDREN_WRITE);

        Shape3D ring = new Shape3D(buildTorusGeo(0.14f, 0.035f, 16, 12), keyApp);
        ring.setUserData("key_" + index);
        ring.setCapability(Shape3D.ALLOW_GEOMETRY_READ);
        TransformGroup ringTG = translated(0f, 0.18f, 0f);
        ringTG.addChild(ring);
        keyTG.addChild(ringTG);

        Shape3D shaft = new Shape3D(buildShaftGeo(0.025f, 0.35f, 8), keyApp);
        shaft.setUserData("key_" + index);
        keyTG.addChild(shaft);

        Shape3D tooth1 = new Shape3D(buildBoxGeo(0.06f, 0.06f, 0.025f), keyApp);
        tooth1.setUserData("key_" + index);
        TransformGroup toothTG1 = translated(0.07f, -0.12f, 0f);
        toothTG1.addChild(tooth1);
        keyTG.addChild(toothTG1);

        Shape3D tooth2 = new Shape3D(buildBoxGeo(0.06f, 0.06f, 0.025f), keyApp);
        tooth2.setUserData("key_" + index);
        TransformGroup toothTG2 = translated(0.07f, -0.04f, 0f);
        toothTG2.addChild(tooth2);
        keyTG.addChild(toothTG2);

        Alpha spinAlpha = new Alpha(-1, 5000 + index * 700);
        RotationInterpolator spin = new RotationInterpolator(
            spinAlpha, keyTG, new Transform3D(), 0f, (float)(2 * Math.PI));
        spin.setSchedulingBounds(worldBounds());
        keyTG.addChild(spin);

        PointLight kl = new PointLight(
            new Color3f(1.0f, 0.85f, 0.1f),
            new Point3f(pos[0], pos[1], pos[2]),
            new Point3f(0.1f, 0.2f, 0.0f));
        kl.setInfluencingBounds(worldBounds());

        Transform3D t = new Transform3D();
        t.setTranslation(new Vector3f(pos[0], pos[1], pos[2]));
        TransformGroup outerTG = new TransformGroup(t);
        outerTG.setCapability(TransformGroup.ALLOW_CHILDREN_READ);
        outerTG.setCapability(TransformGroup.ALLOW_CHILDREN_WRITE);
        outerTG.addChild(keyTG);
        outerTG.addChild(kl);

        // FIX 2: ALLOW_DETACH belongs on BranchGroup, not TransformGroup.
        BranchGroup bg = new BranchGroup();
        bg.setCapability(BranchGroup.ALLOW_DETACH);
        bg.setCapability(BranchGroup.ALLOW_CHILDREN_READ);
        bg.addChild(outerTG);

        keyBGs[index] = bg;
        return bg;
    }

    // =======================================================================
    // PICKING
    // =======================================================================
    private void handlePick(MouseEvent e) {
        pickCanvas.setShapeLocation(e);
        PickResult result = pickCanvas.pickClosest();
        if (result == null) return;
        Node node = result.getObject();
        while (node != null) {
            if (node instanceof Shape3D) {
                Object ud = ((Shape3D) node).getUserData();
                if (ud instanceof String && ((String) ud).startsWith("key_")) {
                    collectKey(Integer.parseInt(((String) ud).substring(4)));
                    return;
                }
            }
            try { node = node.getParent(); }
            catch (Exception ex) { break; }
        }
    }

    private void collectKey(int index) {
        if (keyCollected[index]) return;
        keyCollected[index] = true;
        keysFound++;

        keyBGs[index].detach();

        lanternLights[index].setEnable(true);
        lanternMats[index].setAmbientColor(LANTERN_ON);
        lanternMats[index].setDiffuseColor(LANTERN_ON);
        lanternMats[index].setEmissiveColor(new Color3f(0.8f, 0.6f, 0.05f));

        if (keysFound < 3) {
            statusLabel.setText("  Key " + keysFound +
                " of 3 found -- keep searching...   Keys: " + keysFound + " / 3");
        } else {
            statusLabel.setText("  All three keys found!  The door is open.");
            // FIX 3: flip Switch to hide the door panel immediately
            doorSwitch.setWhichChild(Switch.CHILD_NONE);
        }
    }

    // =======================================================================
    // FIRST-PERSON CONTROLS
    //   WASD = move
    //   Mouse drag = look around
    //   Space = move up
    //   Shift = move down
    //
    // This Behavior updates the camera every frame and clamps it inside the
    // cylindrical room, so the player cannot walk through the wall/floor/ceiling.
    // =======================================================================
    private static class FirstPersonControls extends Behavior
            implements KeyListener, MouseMotionListener, MouseListener {

        private final Canvas3D canvas;
        private final TransformGroup vpTG;
        private final Vector3f position;
        private final float maxRadius;
        private final float minY;
        private final float maxY;

        private final WakeupOnElapsedFrames wakeup =
            new WakeupOnElapsedFrames(0, true);

        private boolean forward, backward, left, right, up, down;
        private int lastMouseX;
        private int lastMouseY;
        private boolean dragging = false;

        // Camera rotation.
        // yaw = left/right turn. pitch = up/down look.
        private float yaw = 0.0f;
        private float pitch = -0.10f;

        // Tweak these to change feel.
        private static final float MOVE_SPEED = 0.16f;
        private static final float LOOK_SPEED = 0.006f;
        private static final float PITCH_LIMIT = 1.35f;

        FirstPersonControls(Canvas3D canvas,
                            TransformGroup vpTG,
                            Vector3f startPosition,
                            float maxRadius,
                            float minY,
                            float maxY) {
            this.canvas = canvas;
            this.vpTG = vpTG;
            this.position = new Vector3f(startPosition);
            this.maxRadius = maxRadius;
            this.minY = minY;
            this.maxY = maxY;

            canvas.addKeyListener(this);
            canvas.addMouseMotionListener(this);
            canvas.addMouseListener(this);
        }

        @Override
        public void initialize() {
            applyCameraTransform();
            wakeupOn(wakeup);
        }

        @Override
        @SuppressWarnings("rawtypes")
        public void processStimulus(Enumeration criteria) {
            updateMovement();
            applyCameraTransform();
            wakeupOn(wakeup);
        }

        private void updateMovement() {
            float dx = 0f;
            float dy = 0f;
            float dz = 0f;

            // Forward direction for Java3D camera looking down local -Z.
            float forwardX = -(float) Math.sin(yaw);
            float forwardZ = -(float) Math.cos(yaw);

            // Right/left strafe direction.
            float rightX = (float) Math.cos(yaw);
            float rightZ = -(float) Math.sin(yaw);

            if (forward) {
                dx += forwardX * MOVE_SPEED;
                dz += forwardZ * MOVE_SPEED;
            }
            if (backward) {
                dx -= forwardX * MOVE_SPEED;
                dz -= forwardZ * MOVE_SPEED;
            }
            if (right) {
                dx += rightX * MOVE_SPEED;
                dz += rightZ * MOVE_SPEED;
            }
            if (left) {
                dx -= rightX * MOVE_SPEED;
                dz -= rightZ * MOVE_SPEED;
            }
            if (up) {
                dy += MOVE_SPEED;
            }
            if (down) {
                dy -= MOVE_SPEED;
            }

            // Normalize diagonal movement so W+D is not faster than W alone.
            float horizontalLength = (float) Math.sqrt(dx * dx + dz * dz);
            if (horizontalLength > MOVE_SPEED) {
                float scale = MOVE_SPEED / horizontalLength;
                dx *= scale;
                dz *= scale;
            }

            position.x += dx;
            position.y += dy;
            position.z += dz;

            clampPosition();
        }

        private void clampPosition() {
            if (position.y < minY) position.y = minY;
            if (position.y > maxY) position.y = maxY;

            float r = (float) Math.sqrt(position.x * position.x + position.z * position.z);
            if (r > maxRadius && r > 0.001f) {
                float scale = maxRadius / r;
                position.x *= scale;
                position.z *= scale;
            }
        }

        private void applyCameraTransform() {
            Transform3D yawT = new Transform3D();
            yawT.rotY(yaw);

            Transform3D pitchT = new Transform3D();
            pitchT.rotX(pitch);

            yawT.mul(pitchT);
            yawT.setTranslation(position);

            vpTG.setTransform(yawT);
        }

        private void setKey(int keyCode, boolean pressed) {
            switch (keyCode) {
                case KeyEvent.VK_W:
                case KeyEvent.VK_UP:
                    forward = pressed;
                    break;
                case KeyEvent.VK_S:
                case KeyEvent.VK_DOWN:
                    backward = pressed;
                    break;
                case KeyEvent.VK_A:
                case KeyEvent.VK_LEFT:
                    left = pressed;
                    break;
                case KeyEvent.VK_D:
                case KeyEvent.VK_RIGHT:
                    right = pressed;
                    break;
                case KeyEvent.VK_SPACE:
                    up = pressed;
                    break;
                case KeyEvent.VK_SHIFT:
                    down = pressed;
                    break;
                default:
                    break;
            }
        }

        @Override
        public void keyPressed(KeyEvent e) {
            setKey(e.getKeyCode(), true);
        }

        @Override
        public void keyReleased(KeyEvent e) {
            setKey(e.getKeyCode(), false);
        }

        @Override
        public void keyTyped(KeyEvent e) {
            // Not needed.
        }

        @Override
        public void mousePressed(MouseEvent e) {
            canvas.requestFocusInWindow();
            dragging = true;
            lastMouseX = e.getX();
            lastMouseY = e.getY();
        }

        @Override
        public void mouseReleased(MouseEvent e) {
            dragging = false;
        }

        @Override
        public void mouseDragged(MouseEvent e) {
            if (!dragging) return;

            int dx = e.getX() - lastMouseX;
            int dy = e.getY() - lastMouseY;

            yaw -= dx * LOOK_SPEED;
            pitch -= dy * LOOK_SPEED;

            if (pitch > PITCH_LIMIT) pitch = PITCH_LIMIT;
            if (pitch < -PITCH_LIMIT) pitch = -PITCH_LIMIT;

            lastMouseX = e.getX();
            lastMouseY = e.getY();
        }

        @Override
        public void mouseMoved(MouseEvent e) {
            // Not needed.
        }

        @Override
        public void mouseClicked(MouseEvent e) {
            canvas.requestFocusInWindow();
        }

        @Override
        public void mouseEntered(MouseEvent e) {
            canvas.requestFocusInWindow();
        }

        @Override
        public void mouseExited(MouseEvent e) {
            // Not needed.
        }
    }

    // =======================================================================
    // GEOMETRY BUILDERS
    // =======================================================================

    private GeometryArray buildTorusGeo(float R, float r, int majorSeg, int minorSeg) {
        int triCount = majorSeg * minorSeg * 2;
        TriangleArray geo = new TriangleArray(triCount * 3,
            GeometryArray.COORDINATES | GeometryArray.NORMALS);
        float[] coords = new float[triCount * 3 * 3];
        float[] norms  = new float[triCount * 3 * 3];
        int idx = 0;
        for (int i = 0; i < majorSeg; i++) {
            double a0 = 2.0 * Math.PI * i / majorSeg;
            double a1 = 2.0 * Math.PI * (i + 1) / majorSeg;
            for (int j = 0; j < minorSeg; j++) {
                double b0 = 2.0 * Math.PI * j / minorSeg;
                double b1 = 2.0 * Math.PI * (j + 1) / minorSeg;
                float[][] pts = new float[4][3];
                float[][] ns  = new float[4][3];
                double[][] ab = { {a0,b0},{a1,b0},{a1,b1},{a0,b1} };
                for (int k = 0; k < 4; k++) {
                    double ca=Math.cos(ab[k][0]), sa=Math.sin(ab[k][0]);
                    double cb=Math.cos(ab[k][1]), sb=Math.sin(ab[k][1]);
                    pts[k][0]=(float)((R+r*cb)*ca);
                    pts[k][1]=(float)(r*sb);
                    pts[k][2]=(float)((R+r*cb)*sa);
                    ns[k][0]=(float)(cb*ca); ns[k][1]=(float)(sb); ns[k][2]=(float)(cb*sa);
                }
                int[][] tris = { {0,1,2},{0,2,3} };
                for (int[] tri : tris) {
                    for (int k : tri) {
                        coords[idx]=pts[k][0]; coords[idx+1]=pts[k][1]; coords[idx+2]=pts[k][2];
                        norms[idx]=ns[k][0];   norms[idx+1]=ns[k][1];   norms[idx+2]=ns[k][2];
                        idx += 3;
                    }
                }
            }
        }
        geo.setCoordinates(0, coords);
        geo.setNormals(0, norms);
        return geo;
    }

    private GeometryArray buildShaftGeo(float radius, float height, int seg) {
        int triCount = seg * 2;
        TriangleArray geo = new TriangleArray(triCount * 3,
            GeometryArray.COORDINATES | GeometryArray.NORMALS);
        float half = height / 2f;
        float[] coords = new float[triCount * 3 * 3];
        float[] norms  = new float[triCount * 3 * 3];
        int idx = 0;
        for (int i = 0; i < seg; i++) {
            double a0 = 2.0 * Math.PI * i / seg;
            double a1 = 2.0 * Math.PI * (i + 1) / seg;
            float x0=(float)(radius*Math.cos(a0)), z0=(float)(radius*Math.sin(a0));
            float x1=(float)(radius*Math.cos(a1)), z1=(float)(radius*Math.sin(a1));
            float nx=(float)(Math.cos((a0+a1)/2)), nz=(float)(Math.sin((a0+a1)/2));
            float[][] tri1 = { {x0,-half,z0},{x1,-half,z1},{x1,half,z1} };
            float[][] tri2 = { {x0,-half,z0},{x1,half,z1},{x0,half,z0} };
            for (float[][] tri : new float[][][]{tri1,tri2}) {
                for (float[] vv : tri) {
                    coords[idx]=vv[0]; coords[idx+1]=vv[1]; coords[idx+2]=vv[2];
                    norms[idx]=nx;     norms[idx+1]=0f;     norms[idx+2]=nz;
                    idx += 3;
                }
            }
        }
        geo.setCoordinates(0, coords);
        geo.setNormals(0, norms);
        return geo;
    }

    private GeometryArray buildBoxGeo(float w, float h, float d) {
        float hw=w/2, hh=h/2, hd=d/2;
        float[][] verts = {
            {-hw,-hh, hd},{ hw,-hh, hd},{ hw, hh, hd},{-hw,-hh, hd},{ hw, hh, hd},{-hw, hh, hd},
            { hw,-hh,-hd},{-hw,-hh,-hd},{-hw, hh,-hd},{ hw,-hh,-hd},{-hw, hh,-hd},{ hw, hh,-hd},
            {-hw, hh, hd},{ hw, hh, hd},{ hw, hh,-hd},{-hw, hh, hd},{ hw, hh,-hd},{-hw, hh,-hd},
            {-hw,-hh,-hd},{ hw,-hh,-hd},{ hw,-hh, hd},{-hw,-hh,-hd},{ hw,-hh, hd},{-hw,-hh, hd},
            { hw,-hh, hd},{ hw,-hh,-hd},{ hw, hh,-hd},{ hw,-hh, hd},{ hw, hh,-hd},{ hw, hh, hd},
            {-hw,-hh,-hd},{-hw,-hh, hd},{-hw, hh, hd},{-hw,-hh,-hd},{-hw, hh, hd},{-hw, hh,-hd},
        };
        float[][] faceN = {
            {0,0,1},{0,0,1},{0,0,1},{0,0,1},{0,0,1},{0,0,1},
            {0,0,-1},{0,0,-1},{0,0,-1},{0,0,-1},{0,0,-1},{0,0,-1},
            {0,1,0},{0,1,0},{0,1,0},{0,1,0},{0,1,0},{0,1,0},
            {0,-1,0},{0,-1,0},{0,-1,0},{0,-1,0},{0,-1,0},{0,-1,0},
            {1,0,0},{1,0,0},{1,0,0},{1,0,0},{1,0,0},{1,0,0},
            {-1,0,0},{-1,0,0},{-1,0,0},{-1,0,0},{-1,0,0},{-1,0,0},
        };
        TriangleArray geo = new TriangleArray(verts.length,
            GeometryArray.COORDINATES | GeometryArray.NORMALS);
        float[] coords = new float[verts.length * 3];
        float[] norms  = new float[verts.length * 3];
        for (int i = 0; i < verts.length; i++) {
            coords[i*3]=verts[i][0]; coords[i*3+1]=verts[i][1]; coords[i*3+2]=verts[i][2];
            norms[i*3]=faceN[i][0];  norms[i*3+1]=faceN[i][1];  norms[i*3+2]=faceN[i][2];
        }
        geo.setCoordinates(0, coords);
        geo.setNormals(0, norms);
        return geo;
    }

    // -----------------------------------------------------------------------
    private TransformGroup translated(float x, float y, float z) {
        Transform3D t = new Transform3D();
        t.setTranslation(new Vector3f(x, y, z));
        return new TransformGroup(t);
    }

    private Shape3D box(float w, float h, float d,
                        float x, float y, float z, Appearance app) {
        float hw=w/2, hh=h/2, hd=d/2;
        float[][] verts = {
            {x-hw,y-hh,z+hd},{x+hw,y-hh,z+hd},{x+hw,y+hh,z+hd},{x-hw,y-hh,z+hd},{x+hw,y+hh,z+hd},{x-hw,y+hh,z+hd},
            {x+hw,y-hh,z-hd},{x-hw,y-hh,z-hd},{x-hw,y+hh,z-hd},{x+hw,y-hh,z-hd},{x-hw,y+hh,z-hd},{x+hw,y+hh,z-hd},
            {x-hw,y+hh,z+hd},{x+hw,y+hh,z+hd},{x+hw,y+hh,z-hd},{x-hw,y+hh,z+hd},{x+hw,y+hh,z-hd},{x-hw,y+hh,z-hd},
            {x-hw,y-hh,z-hd},{x+hw,y-hh,z-hd},{x+hw,y-hh,z+hd},{x-hw,y-hh,z-hd},{x+hw,y-hh,z+hd},{x-hw,y-hh,z+hd},
            {x+hw,y-hh,z+hd},{x+hw,y-hh,z-hd},{x+hw,y+hh,z-hd},{x+hw,y-hh,z+hd},{x+hw,y+hh,z-hd},{x+hw,y+hh,z+hd},
            {x-hw,y-hh,z-hd},{x-hw,y-hh,z+hd},{x-hw,y+hh,z+hd},{x-hw,y-hh,z-hd},{x-hw,y+hh,z+hd},{x-hw,y+hh,z-hd},
        };
        float[][] faceN = {
            {0,0,1},{0,0,1},{0,0,1},{0,0,1},{0,0,1},{0,0,1},
            {0,0,-1},{0,0,-1},{0,0,-1},{0,0,-1},{0,0,-1},{0,0,-1},
            {0,1,0},{0,1,0},{0,1,0},{0,1,0},{0,1,0},{0,1,0},
            {0,-1,0},{0,-1,0},{0,-1,0},{0,-1,0},{0,-1,0},{0,-1,0},
            {1,0,0},{1,0,0},{1,0,0},{1,0,0},{1,0,0},{1,0,0},
            {-1,0,0},{-1,0,0},{-1,0,0},{-1,0,0},{-1,0,0},{-1,0,0},
        };
        TriangleArray geo = new TriangleArray(verts.length,
            GeometryArray.COORDINATES | GeometryArray.NORMALS);
        float[] coords = new float[verts.length * 3];
        float[] norms  = new float[verts.length * 3];
        for (int i = 0; i < verts.length; i++) {
            coords[i*3]=verts[i][0]; coords[i*3+1]=verts[i][1]; coords[i*3+2]=verts[i][2];
            norms[i*3]=faceN[i][0];  norms[i*3+1]=faceN[i][1];  norms[i*3+2]=faceN[i][2];
        }
        geo.setCoordinates(0, coords);
        geo.setNormals(0, norms);
        Shape3D s = new Shape3D(geo, app);
        s.setCapability(Shape3D.ALLOW_APPEARANCE_READ);
        return s;
    }

    // -- Appearance factories (identical to original) -----------------------
    private Appearance stoneMaterial(Color3f ambient, Color3f diffuse) {
        return stoneMaterialEmissive(ambient, diffuse, new Color3f(0f, 0f, 0f));
    }

    private Appearance stoneMaterialEmissive(Color3f ambient, Color3f diffuse,
                                             Color3f emissive) {
        Appearance app = new Appearance();
        Material mat = new Material();
        mat.setAmbientColor(ambient);
        mat.setDiffuseColor(diffuse);
        mat.setEmissiveColor(emissive);
        mat.setSpecularColor(new Color3f(0.25f, 0.22f, 0.15f));
        mat.setShininess(18f);
        mat.setLightingEnable(true);
        app.setMaterial(mat);
        PolygonAttributes pa = new PolygonAttributes();
        pa.setCullFace(PolygonAttributes.CULL_NONE);
        app.setPolygonAttributes(pa);
        return app;
    }

    private Appearance gemMaterial() {
        Appearance app = new Appearance();
        Material mat = new Material();
        mat.setEmissiveColor(new Color3f(0.9f, 0.7f, 0.0f));
        mat.setAmbientColor(new Color3f(1.0f, 0.8f, 0.0f));
        mat.setDiffuseColor(new Color3f(1.0f, 0.9f, 0.3f));
        mat.setSpecularColor(new Color3f(1.0f, 1.0f, 0.6f));
        mat.setShininess(128f);
        mat.setLightingEnable(true);
        app.setMaterial(mat);
        app.setTransparencyAttributes(
            new TransparencyAttributes(TransparencyAttributes.BLENDED, 0.25f));
        PolygonAttributes pa = new PolygonAttributes();
        pa.setCullFace(PolygonAttributes.CULL_NONE);
        app.setPolygonAttributes(pa);
        return app;
    }

    private Appearance keyMaterial() {
        Appearance app = new Appearance();
        Material mat = new Material();
        mat.setAmbientColor(new Color3f(0.5f, 0.38f, 0.02f));
        mat.setDiffuseColor(new Color3f(0.85f, 0.65f, 0.08f));
        mat.setEmissiveColor(new Color3f(0.25f, 0.18f, 0.01f));
        mat.setSpecularColor(new Color3f(1.0f, 0.92f, 0.5f));
        mat.setShininess(80f);
        mat.setLightingEnable(true);
        app.setMaterial(mat);
        return app;
    }

    private BoundingSphere worldBounds() {
        return new BoundingSphere(new Point3d(0, 0, 0), 60.0);
    }

    // =======================================================================
    public static void main(String[] args) {
        SwingUtilities.invokeLater(RoundtableHold::new);
    }
}
