import com.sun.j3d.utils.universe.SimpleUniverse;
import com.sun.j3d.utils.geometry.Cylinder;
import com.sun.j3d.utils.geometry.Box;
import com.sun.j3d.utils.behaviors.vp.OrbitBehavior;

import javax.media.j3d.*;
import javax.vecmath.*;
import java.awt.*;
import javax.swing.*;

public class RoundtableHold extends JFrame {

    private static final int ROOM_SEGMENTS = 64; // polygon count for room cylinder
    private static final float ROOM_RADIUS = 14.0f;
    private static final float ROOM_HEIGHT = 6.0f;

    private static final int TABLE_SEGMENTS = 48;
    private static final float TABLE_RADIUS = 3.5f;
    private static final float TABLE_HEIGHT = 0.18f;
    private static final float TABLE_Y = -1.2f; // height from scene origin

    private static final float LEG_RADIUS = 0.06f;
    private static final float LEG_HEIGHT = 1.1f;
    private static final float LEG_Y = TABLE_Y - (LEG_HEIGHT / 2f) - (TABLE_HEIGHT / 2f);

    private static final float GEM_Y = 0.3f; // just above table surface
    private static final float GEM_SIZE = 0.22f;
    private static final float GEM_SCALE_Y = 2.8f; // stretch vertically

    private static final Color3f YELLOW_LIGHT = new Color3f(1.0f, 0.85f, 0.2f);
    private static final Color3f AMBIENT_COL = new Color3f(0.35f, 0.30f, 0.38f);
    // ─────────────────────────────────────────────────────────────────────────

    public RoundtableHold() {
        super("Roundtable Hold — Elden Ring");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1000, 700);
        setLocationRelativeTo(null);

        // ── canvas & universe ────────────────────────────────────────────────
        GraphicsConfiguration gc = SimpleUniverse.getPreferredConfiguration();
        Canvas3D canvas = new Canvas3D(gc);
        add(canvas, BorderLayout.CENTER);

        SimpleUniverse universe = new SimpleUniverse(canvas);

        // position camera: slightly above and outside the table looking inward
        universe.getViewingPlatform().setNominalViewingTransform();
        TransformGroup vpTG = universe.getViewingPlatform().getViewPlatformTransform();
        Transform3D cameraT = new Transform3D();
        cameraT.setTranslation(new Vector3f(0f, 1.5f, 10.0f));
        vpTG.setTransform(cameraT);

        // ── scene graph ──────────────────────────────────────────────────────
        BranchGroup scene = buildScene(universe, canvas);
        scene.compile();
        universe.addBranchGraph(scene);

        setVisible(true);
    }

    private BranchGroup buildScene(SimpleUniverse universe, Canvas3D canvas) {
        BranchGroup root = new BranchGroup();

        // ── background (deep smoky purple-black) ─────────────────────────────
        Background bg = new Background(new Color3f(0.03f, 0.02f, 0.05f));
        bg.setApplicationBounds(worldBounds());
        root.addChild(bg);

        // ── lighting ─────────────────────────────────────────────────────────
        // Ambient -- cool stone tone, bright enough to see the whole room
        AmbientLight ambient = new AmbientLight(AMBIENT_COL);
        ambient.setInfluencingBounds(worldBounds());
        root.addChild(ambient);

        // Directional fill from above -- illuminates walls and floor uniformly
        DirectionalLight fillDown = new DirectionalLight(
                new Color3f(0.40f, 0.35f, 0.45f),
                new Vector3f(0f, -1f, 0f));
        fillDown.setInfluencingBounds(worldBounds());
        root.addChild(fillDown);

        // Directional fill from below -- brightens ceiling and upper walls
        DirectionalLight fillUp = new DirectionalLight(
                new Color3f(0.25f, 0.22f, 0.28f),
                new Vector3f(0f, 1f, 0f));
        fillUp.setInfluencingBounds(worldBounds());
        root.addChild(fillUp);

        // Horizontal directional lights hitting the walls from the inside outward
        // (direction points outward = toward wall normal which now faces inward)
        Color3f wallFill = new Color3f(0.30f, 0.26f, 0.32f);
        float[][] wallDirs = { { 1, 0, 0 }, { -1, 0, 0 }, { 0, 0, 1 }, { 0, 0, -1 } };
        for (float[] d : wallDirs) {
            DirectionalLight wl = new DirectionalLight(wallFill, new Vector3f(d[0], d[1], d[2]));
            wl.setInfluencingBounds(worldBounds());
            root.addChild(wl);
        }

        // Main point light from the gem position (unchanged)
        PointLight gemLight = new PointLight();
        gemLight.setColor(YELLOW_LIGHT);
        gemLight.setPosition(0f, GEM_Y, 0f);
        gemLight.setAttenuation(0.1f, 0.05f, 0.02f);
        gemLight.setInfluencingBounds(worldBounds());
        root.addChild(gemLight);

        // Warm fill from below table to catch floor grain
        PointLight fillLight = new PointLight();
        fillLight.setColor(new Color3f(0.3f, 0.22f, 0.05f));
        fillLight.setPosition(0f, TABLE_Y - 0.5f, 0f);
        fillLight.setAttenuation(0.2f, 0.1f, 0.0f);
        fillLight.setInfluencingBounds(worldBounds());
        root.addChild(fillLight);

        // ── room ─────────────────────────────────────────────────────────────
        root.addChild(buildRoom());

        // ── table ────────────────────────────────────────────────────────────
        root.addChild(buildTable());

        // ── floating diamond ─────────────────────────────────────────────────
        root.addChild(buildGem());

        // ── orbit mouse behaviour ─────────────────────────────────────────────
        OrbitBehavior orbit = new OrbitBehavior(canvas,
                OrbitBehavior.REVERSE_ALL | OrbitBehavior.STOP_ZOOM);
        orbit.setSchedulingBounds(worldBounds());
        universe.getViewingPlatform().setViewPlatformBehavior(orbit);

        return root;
    }

    // =========================================================================
    // ROOM — hollow cylinder (floor + ceiling discs + wall ring)
    // =========================================================================
    private TransformGroup buildRoom() {
        TransformGroup tg = new TransformGroup();

        // Wall built manually so normals point INWARD (toward viewer inside the
        // cylinder)
        Appearance wallApp = stoneMaterial(
                new Color3f(0.20f, 0.16f, 0.14f),
                new Color3f(0.50f, 0.42f, 0.36f));

        tg.addChild(new Shape3D(buildInwardCylinderGeo(ROOM_RADIUS, ROOM_HEIGHT, ROOM_SEGMENTS), wallApp));

        // floor disc — emissive ensures it's always visible
        tg.addChild(disc(ROOM_RADIUS, ROOM_SEGMENTS, -ROOM_HEIGHT / 2f,
                stoneMaterialEmissive(
                        new Color3f(0.14f, 0.11f, 0.09f),
                        new Color3f(0.32f, 0.26f, 0.20f),
                        new Color3f(0.18f, 0.14f, 0.10f))));

        // ceiling disc — emissive ensures it's always visible
        tg.addChild(discFlipped(ROOM_RADIUS, ROOM_SEGMENTS, ROOM_HEIGHT / 2f,
                stoneMaterialEmissive(
                        new Color3f(0.12f, 0.09f, 0.11f),
                        new Color3f(0.28f, 0.22f, 0.26f),
                        new Color3f(0.14f, 0.11f, 0.13f))));

        return tg;
    }

    /**
     * Cylinder whose normals point INWARD so lighting works when viewed from
     * inside.
     * Each quad is two triangles; normal = -radial direction.
     */
    private GeometryArray buildInwardCylinderGeo(float r, float h, int seg) {
        int triCount = seg * 2;
        TriangleArray geo = new TriangleArray(triCount * 3,
                GeometryArray.COORDINATES | GeometryArray.NORMALS);

        float half = h / 2f;
        float[] coords = new float[triCount * 3 * 3];
        float[] normals = new float[triCount * 3 * 3];
        int idx = 0;

        for (int i = 0; i < seg; i++) {
            double a0 = 2.0 * Math.PI * i / seg;
            double a1 = 2.0 * Math.PI * (i + 1) / seg;

            float x0 = (float) (r * Math.cos(a0)), z0 = (float) (r * Math.sin(a0));
            float x1 = (float) (r * Math.cos(a1)), z1 = (float) (r * Math.sin(a1));

            // inward normal for this strip segment (negated radial, averaged)
            float mx = -(float) (Math.cos((a0 + a1) / 2));
            float mz = -(float) (Math.sin((a0 + a1) / 2));

            // quad as 2 triangles, wound so front face is inward
            // tri 1: bottom-left, top-left, top-right (viewed from inside)
            float[][] tri1 = { { x1, -half, z1 }, { x1, half, z1 }, { x0, half, z0 } };
            // tri 2: bottom-left, top-right, bottom-right
            float[][] tri2 = { { x1, -half, z1 }, { x0, half, z0 }, { x0, -half, z0 } };

            for (float[][] tri : new float[][][] { tri1, tri2 }) {
                for (float[] v : tri) {
                    coords[idx] = v[0];
                    coords[idx + 1] = v[1];
                    coords[idx + 2] = v[2];
                    normals[idx] = mx;
                    normals[idx + 1] = 0f;
                    normals[idx + 2] = mz;
                    idx += 3;
                }
            }
        }

        geo.setCoordinates(0, coords);
        geo.setNormals(0, normals);
        return geo;
    }

    /** Flat disc centred at (0, y, 0) — normals pointing UP */
    private TransformGroup disc(float radius, int segments, float y, Appearance app) {
        GeometryArray geo = buildDiscGeo(radius, segments, 1f);
        Shape3D shape = new Shape3D(geo, app);

        TransformGroup tg = new TransformGroup();
        Transform3D t = new Transform3D();
        t.setTranslation(new Vector3f(0f, y, 0f));
        tg.setTransform(t);
        tg.addChild(shape);
        return tg;
    }

    /** Flat disc centred at (0, y, 0) — normals pointing DOWN (for ceiling) */
    private TransformGroup discFlipped(float radius, int segments, float y, Appearance app) {
        GeometryArray geo = buildDiscGeo(radius, segments, -1f);
        Shape3D shape = new Shape3D(geo, app);

        TransformGroup tg = new TransformGroup();
        Transform3D t = new Transform3D();
        t.setTranslation(new Vector3f(0f, y, 0f));
        tg.setTransform(t);
        tg.addChild(shape);
        return tg;
    }

    private GeometryArray buildDiscGeo(float radius, int seg, float normalY) {
        // triangle fan
        int vCount = seg + 2;
        TriangleFanArray geo = new TriangleFanArray(vCount,
                GeometryArray.COORDINATES | GeometryArray.NORMALS,
                new int[] { vCount });

        float[] coords = new float[vCount * 3];
        float[] normals = new float[vCount * 3];

        // centre
        coords[0] = 0;
        coords[1] = 0;
        coords[2] = 0;
        normals[0] = 0;
        normals[1] = normalY;
        normals[2] = 0;

        // wind CW for normalY<0 (ceiling), CCW for normalY>0 (floor)
        for (int i = 0; i <= seg; i++) {
            double angle = normalY > 0
                    ? 2.0 * Math.PI * i / seg
                    : -2.0 * Math.PI * i / seg;
            int base = (i + 1) * 3;
            coords[base] = (float) (radius * Math.cos(angle));
            coords[base + 1] = 0;
            coords[base + 2] = (float) (radius * Math.sin(angle));
            normals[base] = 0;
            normals[base + 1] = normalY;
            normals[base + 2] = 0;
        }

        geo.setCoordinates(0, coords);
        geo.setNormals(0, normals);
        return geo;
    }

    // =========================================================================
    // TABLE — thick disc top + four legs
    // =========================================================================
    private TransformGroup buildTable() {
        TransformGroup tg = new TransformGroup();

        Appearance woodApp = stoneMaterial(
                new Color3f(0.15f, 0.09f, 0.05f),
                new Color3f(0.38f, 0.24f, 0.14f));

        // tabletop
        Cylinder top = new Cylinder(TABLE_RADIUS, TABLE_HEIGHT,
                Cylinder.GENERATE_NORMALS,
                TABLE_SEGMENTS, 1, woodApp);
        Transform3D topT = new Transform3D();
        topT.setTranslation(new Vector3f(0f, TABLE_Y, 0f));
        TransformGroup topTG = new TransformGroup(topT);
        topTG.addChild(top);
        tg.addChild(topTG);

        // four legs arranged symmetrically
        float legDist = TABLE_RADIUS * 0.6f;
        float[][] offsets = {
                { legDist, legDist },
                { -legDist, legDist },
                { -legDist, -legDist },
                { legDist, -legDist }
        };
        for (float[] off : offsets) {
            tg.addChild(tableLeg(off[0], off[1], woodApp));
        }

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

    // =========================================================================
    // GEM — octahedron-like diamond with emissive yellow glow + slow rotation
    // =========================================================================
    private TransformGroup buildGem() {
        // We build a custom octahedron from a GeometryArray
        Shape3D gemShape = new Shape3D(buildOctahedronGeo(GEM_SIZE), gemMaterial());

        // rotation behaviour (continuous spin around Y)
        TransformGroup spinTG = new TransformGroup();
        spinTG.setCapability(TransformGroup.ALLOW_TRANSFORM_WRITE);

        Alpha rotAlpha = new Alpha(-1, 8000); // infinite, 8 s per revolution
        RotationInterpolator rotator = new RotationInterpolator(
                rotAlpha, spinTG, new Transform3D(),
                0f, (float) (2 * Math.PI));
        rotator.setSchedulingBounds(worldBounds());

        spinTG.addChild(gemShape);
        spinTG.addChild(rotator);

        // also add a small point light that travels with the gem (extra sparkle)
        PointLight glow = new PointLight(YELLOW_LIGHT, new Point3f(0, 0, 0),
                new Point3f(0.05f, 0.1f, 0.0f));
        glow.setInfluencingBounds(worldBounds());
        spinTG.addChild(glow);

        // position TG — translate and apply vertical stretch
        Transform3D pos = new Transform3D();
        pos.setTranslation(new Vector3f(0f, GEM_Y, 0f));
        Transform3D stretch = new Transform3D();
        stretch.setScale(new Vector3d(1.0, GEM_SCALE_Y, 1.0));
        pos.mul(stretch);
        TransformGroup posTG = new TransformGroup(pos);
        posTG.addChild(spinTG);

        return posTG;
    }

    /** Unit octahedron scaled to `size`, with normals per face. */
    private GeometryArray buildOctahedronGeo(float s) {
        // 8 faces × 3 vertices each
        float[][] verts = {
                // top pyramid (apex at +Y)
                { s, 0, 0 }, { 0, s, 0 }, { 0, 0, s },
                { 0, 0, s }, { 0, s, 0 }, { -s, 0, 0 },
                { -s, 0, 0 }, { 0, s, 0 }, { 0, 0, -s },
                { 0, 0, -s }, { 0, s, 0 }, { s, 0, 0 },
                // bottom pyramid (apex at -Y)
                { s, 0, 0 }, { 0, 0, s }, { 0, -s, 0 },
                { 0, 0, s }, { -s, 0, 0 }, { 0, -s, 0 },
                { -s, 0, 0 }, { 0, 0, -s }, { 0, -s, 0 },
                { 0, 0, -s }, { s, 0, 0 }, { 0, -s, 0 },
        };

        TriangleArray geo = new TriangleArray(verts.length,
                GeometryArray.COORDINATES | GeometryArray.NORMALS);

        float[] coords = new float[verts.length * 3];
        float[] normals = new float[verts.length * 3];

        for (int i = 0; i < verts.length; i += 3) {
            // compute face normal
            Vector3f a = v(verts[i]), b = v(verts[i + 1]), c = v(verts[i + 2]);
            Vector3f ab = new Vector3f();
            ab.sub(b, a);
            Vector3f ac = new Vector3f();
            ac.sub(c, a);
            Vector3f n = new Vector3f();
            n.cross(ab, ac);
            n.normalize();

            for (int j = 0; j < 3; j++) {
                int idx = (i + j) * 3;
                coords[idx] = verts[i + j][0];
                coords[idx + 1] = verts[i + j][1];
                coords[idx + 2] = verts[i + j][2];
                normals[idx] = n.x;
                normals[idx + 1] = n.y;
                normals[idx + 2] = n.z;
            }
        }

        geo.setCoordinates(0, coords);
        geo.setNormals(0, normals);
        return geo;
    }

    private Vector3f v(float[] a) {
        return new Vector3f(a[0], a[1], a[2]);
    }

    // =========================================================================
    // HELPERS
    // =========================================================================

    /** Phong-shaded stone-like material */
    private Appearance stoneMaterial(Color3f ambient, Color3f diffuse) {
        return stoneMaterialEmissive(ambient, diffuse, new Color3f(0f, 0f, 0f));
    }

    /**
     * Stone material with an emissive component — guarantees base visibility
     * regardless of lighting.
     */
    private Appearance stoneMaterialEmissive(Color3f ambient, Color3f diffuse, Color3f emissive) {
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
        pa.setCullFace(PolygonAttributes.CULL_NONE); // visible from both sides
        app.setPolygonAttributes(pa);

        return app;
    }

    /** Emissive golden material for the gem */
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

        // slight transparency for a gem-like look
        TransparencyAttributes ta = new TransparencyAttributes(
                TransparencyAttributes.BLENDED, 0.25f);
        app.setTransparencyAttributes(ta);

        PolygonAttributes pa = new PolygonAttributes();
        pa.setCullFace(PolygonAttributes.CULL_NONE); // see both faces of thin gem
        app.setPolygonAttributes(pa);

        return app;
    }

    private BoundingSphere worldBounds() {
        return new BoundingSphere(new Point3d(0, 0, 0), 50.0);
    }

    // =========================================================================
    public static void main(String[] args) {
        SwingUtilities.invokeLater(RoundtableHold::new);
    }
}
