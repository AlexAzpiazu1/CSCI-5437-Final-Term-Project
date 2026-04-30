import com.sun.j3d.utils.universe.SimpleUniverse;
import com.sun.j3d.utils.geometry.Cylinder;

import javax.media.j3d.*;
import javax.vecmath.*;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import java.util.ArrayList;

/**
 * Roundtable Hold - Elden Ring inspired scene (Java3D 1.5 / jogamp) v2
 *
 * Build (Windows):
 * javac -cp j3dcore.jar;j3dutils.jar;vecmath.jar RoundtableHold.java
 * java -cp .;j3dcore.jar;j3dutils.jar;vecmath.jar RoundtableHold
 */
public class RoundtableHold extends JFrame {

    // ── room ──────────────────────────────────────────────────────────────────
    private static final int ROOM_SEGS = 512;
    private static final float ROOM_R = 14.0f;
    private static final float ROOM_H = 6.0f;
    private static final float FLOOR_Y = -ROOM_H / 2f; // -3.0
    private static final float CEIL_Y = ROOM_H / 2f; // 3.0

    // ── table ─────────────────────────────────────────────────────────────────
    private static final float TABLE_R = 3.5f;
    private static final float TABLE_H = 0.18f;
    private static final float TABLE_Y = -1.2f;
    private static final float LEG_R = 0.08f;
    private static final float LEG_H = 1.1f;
    private static final float LEG_Y = TABLE_Y - LEG_H / 2f - TABLE_H / 2f;

    // ── gem ───────────────────────────────────────────────────────────────────
    private static final float GEM_Y = 0.3f;
    private static final float GEM_SIZE = 0.22f;
    private static final float GEM_SCALE_Y = 2.8f;

    // ── archway opening (upside-down U on the north wall) ─────────────────────
    // Opening: rectangular base (width x ARCH_SH) capped by a semicircle.
    private static final float ARCH_W = 5.5f; // opening width
    private static final float ARCH_SH = 3.2f; // straight-side height
    private static final float ARCH_CR = ARCH_W / 2f; // semicircle radius = 2.0
    private static final float ARCH_TOTAL = ARCH_SH + ARCH_CR; // 4.5 (< ROOM_H 6.0)
    private static final float ARCH_TRIM = 0.55f; // trim ring thickness

    // ── hallway ───────────────────────────────────────────────────────────────
    private static final float HALL_DEPTH = 10.0f;
    // Push the arch face inward (toward +Z) so it clears the curved cylinder wall.
    // The cylinder wall at the north point peaks at z = -ROOM_R, but the chord
    // across the ARCH_W opening sits at z = -sqrt(ROOM_R^2 - (ARCH_W/2)^2) ≈
    // -13.71.
    // We place the trim face slightly inside that chord so it's fully visible.
    private static final float NORTH_Z = -(float) Math.sqrt(ROOM_R * ROOM_R - (ARCH_W / 2f) * (ARCH_W / 2f)) + 0.1f;
    private static final float HALL_END_Z = NORTH_Z - HALL_DEPTH;
    // South wall mirror (positive Z)
    private static final float SOUTH_Z = (float) Math.sqrt(ROOM_R * ROOM_R - (ARCH_W / 2f) * (ARCH_W / 2f)) - 0.1f;
    private static final float SOUTH_END_Z = SOUTH_Z + HALL_DEPTH;

    // Diagonal wall angles (in cylinder parameterisation x=R·cos(a), z=R·sin(a))
    // SE: a=π/4 → (x=+R/√2, z=+R/√2) SW: a=3π/4 → (x=-R/√2, z=+R/√2)
    private static final double SE_ANGLE = Math.PI / 4.0;
    private static final double SW_ANGLE = 3.0 * Math.PI / 4.0;
    // Distance from origin to chord face along the wall's outward radial
    private static final float DIAG_CHORD = (float) Math.sqrt(ROOM_R * ROOM_R - (ARCH_W / 2f) * (ARCH_W / 2f)) - 0.1f;

    // ── colours ───────────────────────────────────────────────────────────────
    private static final Color3f YELLOW = new Color3f(1.0f, 0.85f, 0.2f);
    private static final Color3f AMB_COL = new Color3f(0.35f, 0.30f, 0.38f);

    // ── first-person player / puzzle gameplay ────────────────────────────────
    private static final float PLAYER_RADIUS = 0.38f;
    private static final float PLAYER_EYE_Y = FLOOR_Y + 2.35f;
    private static final float WALK_SPEED = 0.16f;
    private static final float MOUSE_SENS = 0.006f;

    private final TransformGroup[] keyTGs = new TransformGroup[3];
    private final boolean[] keyTaken = new boolean[3];
    private final Shape3D[] doorLightShapes = new Shape3D[3];
    private final PointLight[] doorLightNodes = new PointLight[3];
    private TransformGroup doorTG;
    private int collectedKeys = 0;

    private static final Point3f[] KEY_POSITIONS = new Point3f[] {
            new Point3f(5.5f, FLOOR_Y + 0.45f, 4.8f),
            new Point3f(-5.8f, FLOOR_Y + 0.45f, -3.8f),
            new Point3f(2.2f, FLOOR_Y + 0.45f, -9.2f)
    };

    // =========================================================================
    public RoundtableHold() {
        super("Roundtable Hold — Elden Ring");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1100, 750);
        setLocationRelativeTo(null);

        GraphicsConfiguration gc = SimpleUniverse.getPreferredConfiguration();
        Canvas3D canvas = new Canvas3D(gc);
        add(canvas, BorderLayout.CENTER);

        SimpleUniverse universe = new SimpleUniverse(canvas);
        universe.getViewingPlatform().setNominalViewingTransform();
        TransformGroup vpTG = universe.getViewingPlatform().getViewPlatformTransform();
        Transform3D cam = new Transform3D();
        cam.setTranslation(new Vector3f(0f, PLAYER_EYE_Y, 10.0f));
        vpTG.setTransform(cam);

        BranchGroup scene = buildScene(universe, canvas, vpTG);
        scene.compile();
        universe.addBranchGraph(scene);
        setVisible(true);
        canvas.setFocusable(true);
        canvas.requestFocusInWindow();
    }

    // =========================================================================
    private BranchGroup buildScene(SimpleUniverse universe, Canvas3D canvas, TransformGroup vpTG) {
        BranchGroup root = new BranchGroup();

        Background bg = new Background(new Color3f(0.03f, 0.02f, 0.05f));
        bg.setApplicationBounds(wb());
        root.addChild(bg);

        addLights(root);

        root.addChild(buildRoom());
        root.addChild(buildArchway());
        root.addChild(buildHallway());
        root.addChild(buildSouthArchway());
        root.addChild(buildSouthHallway());
        root.addChild(buildDiagArchwayAndHallway(SE_ANGLE));
        root.addChild(buildDiagArchwayAndHallway(SW_ANGLE));
        root.addChild(buildTable());
        root.addChild(buildGem());
        root.addChild(buildKeys());
        root.addChild(buildExitDoor());

        FirstPersonController controller = new FirstPersonController(vpTG, canvas);
        controller.setSchedulingBounds(wb());
        canvas.addKeyListener(controller);
        canvas.addMouseMotionListener(controller);
        root.addChild(controller);

        return root;
    }

    // =========================================================================
    private void addLights(BranchGroup root) {
        AmbientLight al = new AmbientLight(AMB_COL);
        al.setInfluencingBounds(wb());
        root.addChild(al);

        PointLight gem = new PointLight(YELLOW, new Point3f(0, GEM_Y, 0),
                new Point3f(0.08f, 0.04f, 0.01f));
        gem.setInfluencingBounds(wb());
        root.addChild(gem);

        PointLight fill = new PointLight(new Color3f(0.3f, 0.22f, 0.05f),
                new Point3f(0, TABLE_Y - 0.5f, 0), new Point3f(0.15f, 0.08f, 0));
        fill.setInfluencingBounds(wb());
        root.addChild(fill);

        PointLight hall = new PointLight(new Color3f(0.28f, 0.22f, 0.14f),
                new Point3f(0, FLOOR_Y + ARCH_TOTAL - 0.8f, NORTH_Z - HALL_DEPTH / 2f),
                new Point3f(0.06f, 0.04f, 0.003f));
        hall.setInfluencingBounds(wb());
        root.addChild(hall);

        PointLight hallS = new PointLight(new Color3f(0.28f, 0.22f, 0.14f),
                new Point3f(0, FLOOR_Y + ARCH_TOTAL - 0.8f, SOUTH_Z + HALL_DEPTH / 2f),
                new Point3f(0.06f, 0.04f, 0.003f));
        hallS.setInfluencingBounds(wb());
        root.addChild(hallS);

        // Diagonal hallway lights (SE and SW)
        float dh = HALL_DEPTH / 2f;
        for (double wa : new double[] { SE_ANGLE, SW_ANGLE }) {
            float cx = (float) (Math.cos(wa) * (DIAG_CHORD + dh));
            float cz = (float) (Math.sin(wa) * (DIAG_CHORD + dh));
            PointLight dl = new PointLight(new Color3f(0.28f, 0.22f, 0.14f),
                    new Point3f(cx, FLOOR_Y + ARCH_TOTAL - 0.8f, cz),
                    new Point3f(0.06f, 0.04f, 0.003f));
            dl.setInfluencingBounds(wb());
            root.addChild(dl);
        }

        Color3f df = new Color3f(0.32f, 0.28f, 0.36f);
        float[][] dirs = { { 0, -1, 0 }, { 0, 1, 0 }, { 1, 0, 0 }, { -1, 0, 0 }, { 0, 0, 1 }, { 0, 0, -1 } };
        for (float[] d : dirs) {
            DirectionalLight dl = new DirectionalLight(df, new Vector3f(d[0], d[1], d[2]));
            dl.setInfluencingBounds(wb());
            root.addChild(dl);
        }
    }

    // =========================================================================
    // ROOM
    // =========================================================================
    private TransformGroup buildRoom() {
        TransformGroup tg = new TransformGroup();

        Appearance wallApp = matEmissive(c(0.20f, 0.16f, 0.14f), c(0.50f, 0.42f, 0.36f), c(0.08f, 0.06f, 0.05f));
        Appearance floorApp = matEmissive(c(0.14f, 0.11f, 0.09f), c(0.32f, 0.26f, 0.20f), c(0.16f, 0.12f, 0.09f));
        Appearance ceilApp = matEmissive(c(0.12f, 0.09f, 0.11f), c(0.28f, 0.22f, 0.26f), c(0.12f, 0.09f, 0.11f));

        tg.addChild(new Shape3D(buildWallGeo(), wallApp));
        tg.addChild(placedDisc(ROOM_R, 96, 1f, FLOOR_Y, floorApp));
        tg.addChild(placedDisc(ROOM_R, 96, -1f, CEIL_Y, ceilApp));

        return tg;
    }

    /**
     * Builds the inward-facing cylindrical wall with an upside-down-U hole cut
     * out on the north face.
     *
     * The wall is subdivided into (ROOM_SEGS x WALL_ROWS) cells.
     * Each cell is skipped if its centre falls inside the arch opening.
     *
     * Arch opening (in world space, centred at x=0, on z = -ROOM_R):
     * • Rectangular base: |x| < ARCH_W/2, FLOOR_Y <= y <= FLOOR_Y+ARCH_SH
     * • Semicircular top: x²+(y-(FLOOR_Y+ARCH_SH))² < ARCH_CR², upper half
     */
    private GeometryArray buildWallGeo() {
        final int ROWS = 600;
        ArrayList<float[]> vv = new ArrayList<>(), nn = new ArrayList<>();

        float half = ROOM_H / 2f;
        // North wall is at a = 3π/2 (x=0, z=-ROOM_R).
        // South wall is at a = π/2 (x=0, z=+ROOM_R).
        // Angular half-span of the arch chord on the cylinder surface.
        double halfSpan = Math.asin((ARCH_W / 2.0) / ROOM_R);
        double northA = 3.0 * Math.PI / 2.0; // 270°
        double southA = Math.PI / 2.0; // 90°
        double[] holeAngles = { northA, southA, SE_ANGLE, SW_ANGLE };

        for (int si = 0; si < ROOM_SEGS; si++) {
            double a0 = 2.0 * Math.PI * si / ROOM_SEGS;
            double a1 = 2.0 * Math.PI * (si + 1) / ROOM_SEGS;
            double aMid = (a0 + a1) / 2.0;

            float x0 = (float) (ROOM_R * Math.cos(a0)), z0 = (float) (ROOM_R * Math.sin(a0));
            float x1 = (float) (ROOM_R * Math.cos(a1)), z1 = (float) (ROOM_R * Math.sin(a1));
            float nx = -(float) Math.cos(aMid), nz = -(float) Math.sin(aMid);

            for (int ri = 0; ri < ROWS; ri++) {
                float y0 = -half + ROOM_H * ri / ROWS;
                float y1 = -half + ROOM_H * (ri + 1) / ROWS;

                // Check this cell against every hole angle generically.
                // For a hole at angle holeA, the lateral (X-like) offset of a wall
                // point is its projection onto the tangent of holeA:
                // cellX = -wx*sin(holeA) + wz*cos(holeA)
                boolean skip = false;
                for (double holeA : holeAngles) {
                    double d = Math.abs(aMid - holeA) % (2 * Math.PI);
                    if (d > Math.PI)
                        d = 2 * Math.PI - d;
                    if (d < halfSpan + 0.05) {
                        float wx = (float) (ROOM_R * Math.cos(aMid));
                        float wz2 = (float) (ROOM_R * Math.sin(aMid));
                        float cellX = (float) (-wx * Math.sin(holeA) + wz2 * Math.cos(holeA));
                        float cellY = (y0 + y1) / 2f;
                        if (insideArch(cellX, cellY)) {
                            skip = true;
                            break;
                        }
                    }
                }
                if (skip)
                    continue;

                // emit inward-facing quad (two triangles)
                addTriN(vv, nn, nx, 0, nz, p(x1, y0, z1), p(x1, y1, z1), p(x0, y1, z0));
                addTriN(vv, nn, nx, 0, nz, p(x1, y0, z1), p(x0, y1, z0), p(x0, y0, z0));
            }
        }
        return listToGeo(vv, nn);
    }

    /** True if (wx, wy) is inside the arch-shaped opening profile. */
    private boolean insideArch(float wx, float wy) {
        if (Math.abs(wx) > ARCH_W / 2f)
            return false;
        float archBase = FLOOR_Y;
        if (wy < archBase)
            return false;
        if (wy <= archBase + ARCH_SH)
            return true; // rectangular part
        // semicircular cap
        float dy = wy - (archBase + ARCH_SH);
        return (wx * wx + dy * dy) < ARCH_CR * ARCH_CR;
    }

    // =========================================================================
    // ARCHWAY TRIM
    // =========================================================================
    private TransformGroup buildArchway() {
        TransformGroup tg = new TransformGroup();
        Appearance stone = matEmissive(c(0.24f, 0.19f, 0.16f), c(0.58f, 0.48f, 0.40f), c(0.16f, 0.13f, 0.10f));

        // The trim ring sits on the room-facing side of the north wall (z = NORTH_Z +
        // epsilon)
        float z = NORTH_Z + 0.03f;
        float zB = z - ARCH_TRIM; // back face of trim (into the wall / hallway side)

        float inner = ARCH_W / 2f;
        float outer = inner + ARCH_TRIM;
        float base = FLOOR_Y;
        float top = base + ARCH_SH;
        float archCY = top; // semicircle centre Y

        // ── room-facing face of the trim (flat, normal +Z) ────────────────────
        ArrayList<float[]> fv = new ArrayList<>(), fn = new ArrayList<>();

        // Left bar
        addQuadN(fv, fn, 0, 0, 1,
                p(-outer, base, z), p(-inner, base, z), p(-inner, top, z), p(-outer, top, z));
        // Right bar
        addQuadN(fv, fn, 0, 0, 1,
                p(inner, base, z), p(outer, base, z), p(outer, top, z), p(inner, top, z));
        // Semicircular ring (outer - inner)
        int segs = 32;
        for (int i = 0; i < segs; i++) {
            double a0 = Math.PI * i / segs;
            double a1 = Math.PI * (i + 1) / segs;
            float ox0 = 0 + (outer * (float) Math.cos(a0)), oy0 = archCY + (outer * (float) Math.sin(a0));
            float ox1 = 0 + (outer * (float) Math.cos(a1)), oy1 = archCY + (outer * (float) Math.sin(a1));
            float ix0 = 0 + (inner * (float) Math.cos(a0)), iy0 = archCY + (inner * (float) Math.sin(a0));
            float ix1 = 0 + (inner * (float) Math.cos(a1)), iy1 = archCY + (inner * (float) Math.sin(a1));
            addTriN(fv, fn, 0, 0, 1, p(ox0, oy0, z), p(ox1, oy1, z), p(ix1, iy1, z));
            addTriN(fv, fn, 0, 0, 1, p(ox0, oy0, z), p(ix1, iy1, z), p(ix0, iy0, z));
        }
        tg.addChild(new Shape3D(listToGeo(fv, fn), stone));

        // ── inner edge of the trim (the soffit — faces the opening interior) ──
        // This is the face you see when standing in the opening looking up.
        // Left inner edge face (normal +X)
        ArrayList<float[]> sv = new ArrayList<>(), sn = new ArrayList<>();
        addQuadN(sv, sn, 1, 0, 0,
                p(-inner, base, zB), p(-inner, base, z), p(-inner, top, z), p(-inner, top, zB));
        // Right inner edge face (normal -X)
        addQuadN(sv, sn, -1, 0, 0,
                p(inner, base, z), p(inner, base, zB), p(inner, top, zB), p(inner, top, z));
        // Semicircular inner soffit (curved, inward-facing radial normals)
        for (int i = 0; i < segs; i++) {
            double a0 = Math.PI * i / segs;
            double a1 = Math.PI * (i + 1) / segs;
            float ix0 = 0 + (inner * (float) Math.cos(a0)), iy0 = archCY + (inner * (float) Math.sin(a0));
            float ix1 = 0 + (inner * (float) Math.cos(a1)), iy1 = archCY + (inner * (float) Math.sin(a1));
            // inward normal = toward arch centre = negative radial
            float inx = -(float) Math.cos((a0 + a1) / 2), iny = -(float) Math.sin((a0 + a1) / 2);
            addTriN(sv, sn, inx, iny, 0, p(ix0, iy0, z), p(ix1, iy1, z), p(ix1, iy1, zB));
            addTriN(sv, sn, inx, iny, 0, p(ix0, iy0, z), p(ix1, iy1, zB), p(ix0, iy0, zB));
        }
        tg.addChild(new Shape3D(listToGeo(sv, sn), stone));

        // ── outer edge soffit (the outer ring face visible from outside) ───────
        // (small face at top of outer arc facing away from centre — mostly hidden)
        ArrayList<float[]> ov = new ArrayList<>(), on2 = new ArrayList<>();
        for (int i = 0; i < segs; i++) {
            double a0 = Math.PI * i / segs;
            double a1 = Math.PI * (i + 1) / segs;
            float ox0 = 0 + (outer * (float) Math.cos(a0)), oy0 = archCY + (outer * (float) Math.sin(a0));
            float ox1 = 0 + (outer * (float) Math.cos(a1)), oy1 = archCY + (outer * (float) Math.sin(a1));
            float onx = (float) Math.cos((a0 + a1) / 2), ony = (float) Math.sin((a0 + a1) / 2);
            addTriN(ov, on2, onx, ony, 0, p(ox0, oy0, zB), p(ox1, oy1, zB), p(ox1, oy1, z));
            addTriN(ov, on2, onx, ony, 0, p(ox0, oy0, zB), p(ox1, oy1, z), p(ox0, oy0, z));
        }
        // left outer bar side (faces -X)
        addQuadN(ov, on2, -1, 0, 0,
                p(-outer, top, z), p(-outer, top, zB), p(-outer, base, zB), p(-outer, base, z));
        // right outer bar side (faces +X)
        addQuadN(ov, on2, 1, 0, 0,
                p(outer, base, z), p(outer, base, zB), p(outer, top, zB), p(outer, top, z));
        tg.addChild(new Shape3D(listToGeo(ov, on2), stone));

        // ── top face of the trim bars (horizontal ledge, normal +Y) ──────────
        ArrayList<float[]> tv = new ArrayList<>(), tn = new ArrayList<>();
        addQuadN(tv, tn, 0, 1, 0,
                p(-outer, top, zB), p(-inner, top, zB), p(-inner, top, z), p(-outer, top, z));
        addQuadN(tv, tn, 0, 1, 0,
                p(inner, top, zB), p(outer, top, zB), p(outer, top, z), p(inner, top, z));
        tg.addChild(new Shape3D(listToGeo(tv, tn), stone));

        return tg;
    }

    // =========================================================================
    // HALLWAY
    // =========================================================================
    private TransformGroup buildHallway() {
        TransformGroup tg = new TransformGroup();
        Appearance stone = matEmissive(c(0.18f, 0.14f, 0.12f), c(0.42f, 0.34f, 0.28f), c(0.12f, 0.09f, 0.07f));

        float hw = ARCH_W / 2f; // half-width
        float flY = FLOOR_Y;
        float ceY = FLOOR_Y + ARCH_TOTAL; // ceiling height matches arch top
        float z0 = NORTH_Z;
        float z1 = HALL_END_Z;

        // Left wall (normal -X from inside)
        tg.addChild(new Shape3D(quadGeo(
                p(-hw, flY, z1), p(-hw, ceY, z1), p(-hw, ceY, z0), p(-hw, flY, z0),
                new float[] { 1, 0, 0 }), stone));
        // Right wall (normal +X from inside)
        tg.addChild(new Shape3D(quadGeo(
                p(hw, flY, z0), p(hw, ceY, z0), p(hw, ceY, z1), p(hw, flY, z1),
                new float[] { -1, 0, 0 }), stone));
        // Floor
        tg.addChild(new Shape3D(quadGeo(
                p(-hw, flY, z0), p(hw, flY, z0), p(hw, flY, z1), p(-hw, flY, z1),
                new float[] { 0, 1, 0 }), stone));
        // Back (end) wall
        tg.addChild(new Shape3D(quadGeo(
                p(-hw, flY, z1), p(-hw, ceY, z1), p(hw, ceY, z1), p(hw, flY, z1),
                new float[] { 0, 0, 1 }), stone));
        // Flat ceiling above the straight portion
        tg.addChild(new Shape3D(quadGeo(
                p(-hw, ceY, z0), p(-hw, ceY, z1), p(hw, ceY, z1), p(hw, ceY, z0),
                new float[] { 0, -1, 0 }), stone));

        // Curved arch ceiling inside the hallway (follows semicircle profile)
        tg.addChild(new Shape3D(buildArchCeilingGeo(z0, z1), stone));

        // ── darkening fog planes ───────────────────────────────────────────────
        int FOG_COUNT = 8;
        float planeW = ARCH_W * 2.5f;
        float planeH = ARCH_TOTAL * 2f;
        float alpha = 0.70f;

        Appearance fog = new Appearance();
        fog.setColoringAttributes(new ColoringAttributes(0f, 0f, 0f, ColoringAttributes.SHADE_FLAT));
        fog.setTransparencyAttributes(new TransparencyAttributes(
                TransparencyAttributes.BLENDED, alpha));
        PolygonAttributes pa = new PolygonAttributes();
        pa.setCullFace(PolygonAttributes.CULL_NONE);
        fog.setPolygonAttributes(pa);
        RenderingAttributes ra = new RenderingAttributes();
        ra.setDepthBufferWriteEnable(false);
        fog.setRenderingAttributes(ra);

        for (int i = 0; i < FOG_COUNT; i++) {
            float t = (i + 1f) / (FOG_COUNT + 1f);
            float planeZ = z0 + (z1 - z0) * t;
            float px = planeW / 2f;
            float pyB = flY - (planeH - ARCH_TOTAL) / 2f;
            float pyT = pyB + planeH;

            tg.addChild(new Shape3D(quadGeo(
                    p(-px, pyB, planeZ), p(px, pyB, planeZ),
                    p(px, pyT, planeZ), p(-px, pyT, planeZ),
                    new float[] { 0, 0, 1 }), fog));
        }

        return tg;
    }

    // =========================================================================
    // SOUTH ARCHWAY TRIM (mirror of north, faces -Z into room)
    // =========================================================================
    private TransformGroup buildSouthArchway() {
        TransformGroup tg = new TransformGroup();
        Appearance stone = matEmissive(c(0.24f, 0.19f, 0.16f), c(0.58f, 0.48f, 0.40f), c(0.16f, 0.13f, 0.10f));

        float z = SOUTH_Z - 0.03f; // room-facing side of south wall (faces -Z)
        float zB = z + ARCH_TRIM; // back face of trim (into south hallway = +Z)

        float inner = ARCH_W / 2f;
        float outer = inner + ARCH_TRIM;
        float base = FLOOR_Y;
        float top = base + ARCH_SH;
        float archCY = top;
        int segs = 32;

        // room-facing face (normal -Z)
        ArrayList<float[]> fv = new ArrayList<>(), fn = new ArrayList<>();
        addQuadN(fv, fn, 0, 0, -1, p(-inner, base, z), p(-outer, base, z), p(-outer, top, z), p(-inner, top, z));
        addQuadN(fv, fn, 0, 0, -1, p(outer, base, z), p(inner, base, z), p(inner, top, z), p(outer, top, z));
        for (int i = 0; i < segs; i++) {
            double a0 = Math.PI * i / segs, a1 = Math.PI * (i + 1) / segs;
            float ox0 = outer * (float) Math.cos(a0), oy0 = archCY + outer * (float) Math.sin(a0);
            float ox1 = outer * (float) Math.cos(a1), oy1 = archCY + outer * (float) Math.sin(a1);
            float ix0 = inner * (float) Math.cos(a0), iy0 = archCY + inner * (float) Math.sin(a0);
            float ix1 = inner * (float) Math.cos(a1), iy1 = archCY + inner * (float) Math.sin(a1);
            addTriN(fv, fn, 0, 0, -1, p(ox1, oy1, z), p(ox0, oy0, z), p(ix0, iy0, z));
            addTriN(fv, fn, 0, 0, -1, p(ox1, oy1, z), p(ix0, iy0, z), p(ix1, iy1, z));
        }
        tg.addChild(new Shape3D(listToGeo(fv, fn), stone));

        // inner soffit
        ArrayList<float[]> sv = new ArrayList<>(), sn = new ArrayList<>();
        addQuadN(sv, sn, 1, 0, 0, p(-inner, base, z), p(-inner, base, zB), p(-inner, top, zB), p(-inner, top, z));
        addQuadN(sv, sn, -1, 0, 0, p(inner, base, zB), p(inner, base, z), p(inner, top, z), p(inner, top, zB));
        for (int i = 0; i < segs; i++) {
            double a0 = Math.PI * i / segs, a1 = Math.PI * (i + 1) / segs;
            float ix0 = inner * (float) Math.cos(a0), iy0 = archCY + inner * (float) Math.sin(a0);
            float ix1 = inner * (float) Math.cos(a1), iy1 = archCY + inner * (float) Math.sin(a1);
            float inx = -(float) Math.cos((a0 + a1) / 2), iny = -(float) Math.sin((a0 + a1) / 2);
            addTriN(sv, sn, inx, iny, 0, p(ix1, iy1, z), p(ix0, iy0, z), p(ix0, iy0, zB));
            addTriN(sv, sn, inx, iny, 0, p(ix1, iy1, z), p(ix0, iy0, zB), p(ix1, iy1, zB));
        }
        tg.addChild(new Shape3D(listToGeo(sv, sn), stone));

        // outer edge
        ArrayList<float[]> ov = new ArrayList<>(), on2 = new ArrayList<>();
        for (int i = 0; i < segs; i++) {
            double a0 = Math.PI * i / segs, a1 = Math.PI * (i + 1) / segs;
            float ox0 = outer * (float) Math.cos(a0), oy0 = archCY + outer * (float) Math.sin(a0);
            float ox1 = outer * (float) Math.cos(a1), oy1 = archCY + outer * (float) Math.sin(a1);
            float onx = (float) Math.cos((a0 + a1) / 2), ony = (float) Math.sin((a0 + a1) / 2);
            addTriN(ov, on2, onx, ony, 0, p(ox1, oy1, z), p(ox0, oy0, z), p(ox0, oy0, zB));
            addTriN(ov, on2, onx, ony, 0, p(ox1, oy1, z), p(ox0, oy0, zB), p(ox1, oy1, zB));
        }
        addQuadN(ov, on2, -1, 0, 0, p(-outer, base, z), p(-outer, base, zB), p(-outer, top, zB), p(-outer, top, z));
        addQuadN(ov, on2, 1, 0, 0, p(outer, top, z), p(outer, top, zB), p(outer, base, zB), p(outer, base, z));
        tg.addChild(new Shape3D(listToGeo(ov, on2), stone));

        // top ledge
        ArrayList<float[]> tv = new ArrayList<>(), tn = new ArrayList<>();
        addQuadN(tv, tn, 0, 1, 0, p(-inner, top, z), p(-outer, top, z), p(-outer, top, zB), p(-inner, top, zB));
        addQuadN(tv, tn, 0, 1, 0, p(outer, top, z), p(inner, top, z), p(inner, top, zB), p(outer, top, zB));
        tg.addChild(new Shape3D(listToGeo(tv, tn), stone));

        return tg;
    }

    // =========================================================================
    // SOUTH HALLWAY (mirror of north, extends in +Z direction)
    // =========================================================================
    private TransformGroup buildSouthHallway() {
        TransformGroup tg = new TransformGroup();
        Appearance stone = matEmissive(c(0.18f, 0.14f, 0.12f), c(0.42f, 0.34f, 0.28f), c(0.12f, 0.09f, 0.07f));

        float hw = ARCH_W / 2f;
        float flY = FLOOR_Y;
        float ceY = FLOOR_Y + ARCH_TOTAL;
        float z0 = SOUTH_Z; // room-side entrance
        float z1 = SOUTH_END_Z; // far end (+Z direction)

        // Left wall
        tg.addChild(new Shape3D(quadGeo(
                p(-hw, flY, z0), p(-hw, ceY, z0), p(-hw, ceY, z1), p(-hw, flY, z1),
                new float[] { 1, 0, 0 }), stone));
        // Right wall
        tg.addChild(new Shape3D(quadGeo(
                p(hw, flY, z1), p(hw, ceY, z1), p(hw, ceY, z0), p(hw, flY, z0),
                new float[] { -1, 0, 0 }), stone));
        // Floor
        tg.addChild(new Shape3D(quadGeo(
                p(-hw, flY, z1), p(hw, flY, z1), p(hw, flY, z0), p(-hw, flY, z0),
                new float[] { 0, 1, 0 }), stone));
        // Back (end) wall — faces -Z
        tg.addChild(new Shape3D(quadGeo(
                p(hw, flY, z1), p(hw, ceY, z1), p(-hw, ceY, z1), p(-hw, flY, z1),
                new float[] { 0, 0, -1 }), stone));
        // Flat ceiling
        tg.addChild(new Shape3D(quadGeo(
                p(-hw, ceY, z1), p(hw, ceY, z1), p(hw, ceY, z0), p(-hw, ceY, z0),
                new float[] { 0, -1, 0 }), stone));

        // Curved arch ceiling
        tg.addChild(new Shape3D(buildArchCeilingGeo(z0, z1), stone));

        // Fog planes
        int FOG_COUNT = 8;
        float planeW = ARCH_W * 2.5f;
        float planeH = ARCH_TOTAL * 2f;
        float alpha = 0.70f;

        Appearance fog = new Appearance();
        fog.setColoringAttributes(new ColoringAttributes(0f, 0f, 0f, ColoringAttributes.SHADE_FLAT));
        fog.setTransparencyAttributes(new TransparencyAttributes(TransparencyAttributes.BLENDED, alpha));
        PolygonAttributes pa = new PolygonAttributes();
        pa.setCullFace(PolygonAttributes.CULL_NONE);
        fog.setPolygonAttributes(pa);
        RenderingAttributes ra = new RenderingAttributes();
        ra.setDepthBufferWriteEnable(false);
        fog.setRenderingAttributes(ra);

        for (int i = 0; i < FOG_COUNT; i++) {
            float t = (i + 1f) / (FOG_COUNT + 1f);
            float planeZ = z0 + (z1 - z0) * t; // z0→z1 is in +Z direction
            float px = planeW / 2f;
            float pyB = flY - (planeH - ARCH_TOTAL) / 2f;
            float pyT = pyB + planeH;
            tg.addChild(new Shape3D(quadGeo(
                    p(-px, pyB, planeZ), p(px, pyB, planeZ),
                    p(px, pyT, planeZ), p(-px, pyT, planeZ),
                    new float[] { 0, 0, -1 }), fog));
        }

        return tg;
    }

    // =========================================================================
    // DIAGONAL ARCHWAY + HALLWAY (SE or SW, built as north then Y-rotated)
    // =========================================================================
    /**
     * Builds an archway + hallway identical to the north one, then rotates the
     * entire assembly around Y so it faces the given wall angle.
     * North in the cylinder is a=3π/2. A rotation of (wallAngle - 3π/2) around
     * Y maps the north-facing geometry to wallAngle.
     */
    private TransformGroup buildDiagArchwayAndHallway(double wallAngle) {
        // Rotation angle to map "facing -Z (north)" to wallAngle
        double rotY = wallAngle - (3.0 * Math.PI / 2.0);

        Transform3D rot = new Transform3D();
        rot.rotY(rotY);
        TransformGroup tg = new TransformGroup(rot);

        // Reuse north archway geometry (it faces -Z / north)
        tg.addChild(buildNorthArchway());
        tg.addChild(buildNorthHallway());

        return tg;
    }

    /**
     * Builds the north archway trim exactly as buildArchway() does —
     * extracted so it can be reused by the diagonal builder.
     */
    private TransformGroup buildNorthArchway() {
        TransformGroup tg = new TransformGroup();
        Appearance stone = matEmissive(c(0.24f, 0.19f, 0.16f), c(0.58f, 0.48f, 0.40f), c(0.16f, 0.13f, 0.10f));

        float z = NORTH_Z + 0.03f;
        float zB = z - ARCH_TRIM;
        float inner = ARCH_W / 2f, outer = inner + ARCH_TRIM;
        float base = FLOOR_Y, top = base + ARCH_SH, archCY = top;
        int segs = 32;

        ArrayList<float[]> fv = new ArrayList<>(), fn = new ArrayList<>();
        addQuadN(fv, fn, 0, 0, 1, p(-outer, base, z), p(-inner, base, z), p(-inner, top, z), p(-outer, top, z));
        addQuadN(fv, fn, 0, 0, 1, p(inner, base, z), p(outer, base, z), p(outer, top, z), p(inner, top, z));
        for (int i = 0; i < segs; i++) {
            double a0 = Math.PI * i / segs, a1 = Math.PI * (i + 1) / segs;
            float ox0 = outer * (float) Math.cos(a0), oy0 = archCY + outer * (float) Math.sin(a0);
            float ox1 = outer * (float) Math.cos(a1), oy1 = archCY + outer * (float) Math.sin(a1);
            float ix0 = inner * (float) Math.cos(a0), iy0 = archCY + inner * (float) Math.sin(a0);
            float ix1 = inner * (float) Math.cos(a1), iy1 = archCY + inner * (float) Math.sin(a1);
            addTriN(fv, fn, 0, 0, 1, p(ox0, oy0, z), p(ox1, oy1, z), p(ix1, iy1, z));
            addTriN(fv, fn, 0, 0, 1, p(ox0, oy0, z), p(ix1, iy1, z), p(ix0, iy0, z));
        }
        tg.addChild(new Shape3D(listToGeo(fv, fn), stone));

        ArrayList<float[]> sv = new ArrayList<>(), sn = new ArrayList<>();
        addQuadN(sv, sn, 1, 0, 0, p(-inner, base, zB), p(-inner, base, z), p(-inner, top, z), p(-inner, top, zB));
        addQuadN(sv, sn, -1, 0, 0, p(inner, base, z), p(inner, base, zB), p(inner, top, zB), p(inner, top, z));
        for (int i = 0; i < segs; i++) {
            double a0 = Math.PI * i / segs, a1 = Math.PI * (i + 1) / segs;
            float ix0 = inner * (float) Math.cos(a0), iy0 = archCY + inner * (float) Math.sin(a0);
            float ix1 = inner * (float) Math.cos(a1), iy1 = archCY + inner * (float) Math.sin(a1);
            float inx = -(float) Math.cos((a0 + a1) / 2), iny = -(float) Math.sin((a0 + a1) / 2);
            addTriN(sv, sn, inx, iny, 0, p(ix0, iy0, z), p(ix1, iy1, z), p(ix1, iy1, zB));
            addTriN(sv, sn, inx, iny, 0, p(ix0, iy0, z), p(ix1, iy1, zB), p(ix0, iy0, zB));
        }
        tg.addChild(new Shape3D(listToGeo(sv, sn), stone));

        ArrayList<float[]> ov = new ArrayList<>(), on2 = new ArrayList<>();
        for (int i = 0; i < segs; i++) {
            double a0 = Math.PI * i / segs, a1 = Math.PI * (i + 1) / segs;
            float ox0 = outer * (float) Math.cos(a0), oy0 = archCY + outer * (float) Math.sin(a0);
            float ox1 = outer * (float) Math.cos(a1), oy1 = archCY + outer * (float) Math.sin(a1);
            float onx = (float) Math.cos((a0 + a1) / 2), ony = (float) Math.sin((a0 + a1) / 2);
            addTriN(ov, on2, onx, ony, 0, p(ox0, oy0, zB), p(ox1, oy1, zB), p(ox1, oy1, z));
            addTriN(ov, on2, onx, ony, 0, p(ox0, oy0, zB), p(ox1, oy1, z), p(ox0, oy0, z));
        }
        addQuadN(ov, on2, -1, 0, 0, p(-outer, top, z), p(-outer, top, zB), p(-outer, base, zB), p(-outer, base, z));
        addQuadN(ov, on2, 1, 0, 0, p(outer, base, z), p(outer, base, zB), p(outer, top, zB), p(outer, top, z));
        tg.addChild(new Shape3D(listToGeo(ov, on2), stone));

        ArrayList<float[]> tv = new ArrayList<>(), tn = new ArrayList<>();
        addQuadN(tv, tn, 0, 1, 0, p(-outer, top, zB), p(-inner, top, zB), p(-inner, top, z), p(-outer, top, z));
        addQuadN(tv, tn, 0, 1, 0, p(inner, top, zB), p(outer, top, zB), p(outer, top, z), p(inner, top, z));
        tg.addChild(new Shape3D(listToGeo(tv, tn), stone));

        return tg;
    }

    /**
     * Builds the north hallway exactly as buildHallway() does —
     * extracted so it can be reused by the diagonal builder.
     */
    private TransformGroup buildNorthHallway() {
        TransformGroup tg = new TransformGroup();
        Appearance stone = matEmissive(c(0.18f, 0.14f, 0.12f), c(0.42f, 0.34f, 0.28f), c(0.12f, 0.09f, 0.07f));

        float hw = ARCH_W / 2f;
        float flY = FLOOR_Y;
        float ceY = FLOOR_Y + ARCH_TOTAL;
        float z0 = NORTH_Z;
        float z1 = HALL_END_Z;

        tg.addChild(new Shape3D(quadGeo(
                p(-hw, flY, z1), p(-hw, ceY, z1), p(-hw, ceY, z0), p(-hw, flY, z0), new float[] { 1, 0, 0 }), stone));
        tg.addChild(new Shape3D(quadGeo(
                p(hw, flY, z0), p(hw, ceY, z0), p(hw, ceY, z1), p(hw, flY, z1), new float[] { -1, 0, 0 }), stone));
        tg.addChild(new Shape3D(quadGeo(
                p(-hw, flY, z0), p(hw, flY, z0), p(hw, flY, z1), p(-hw, flY, z1), new float[] { 0, 1, 0 }), stone));
        tg.addChild(new Shape3D(quadGeo(
                p(-hw, flY, z1), p(-hw, ceY, z1), p(hw, ceY, z1), p(hw, flY, z1), new float[] { 0, 0, 1 }), stone));
        tg.addChild(new Shape3D(quadGeo(
                p(-hw, ceY, z0), p(-hw, ceY, z1), p(hw, ceY, z1), p(hw, ceY, z0), new float[] { 0, -1, 0 }), stone));
        tg.addChild(new Shape3D(buildArchCeilingGeo(z0, z1), stone));

        // Fog planes
        int FOG_COUNT = 8;
        float planeW = ARCH_W * 2.5f;
        float planeH = ARCH_TOTAL * 2f;

        Appearance fog = new Appearance();
        fog.setColoringAttributes(new ColoringAttributes(0f, 0f, 0f, ColoringAttributes.SHADE_FLAT));
        fog.setTransparencyAttributes(new TransparencyAttributes(TransparencyAttributes.BLENDED, 0.70f));
        PolygonAttributes pa = new PolygonAttributes();
        pa.setCullFace(PolygonAttributes.CULL_NONE);
        fog.setPolygonAttributes(pa);
        RenderingAttributes ra = new RenderingAttributes();
        ra.setDepthBufferWriteEnable(false);
        fog.setRenderingAttributes(ra);

        for (int i = 0; i < FOG_COUNT; i++) {
            float t = (i + 1f) / (FOG_COUNT + 1f);
            float planeZ = z0 + (z1 - z0) * t;
            float px = planeW / 2f;
            float pyB = flY - (planeH - ARCH_TOTAL) / 2f;
            float pyT = pyB + planeH;
            tg.addChild(new Shape3D(quadGeo(
                    p(-px, pyB, planeZ), p(px, pyB, planeZ),
                    p(px, pyT, planeZ), p(-px, pyT, planeZ),
                    new float[] { 0, 0, 1 }), fog));
        }
        return tg;
    }

    /**
     * Curved ceiling strip that follows the arch semicircle through the hallway.
     */
    private GeometryArray buildArchCeilingGeo(float z0, float z1) {
        int segs = 32;
        float archCY = FLOOR_Y + ARCH_SH;
        ArrayList<float[]> vv = new ArrayList<>(), nn = new ArrayList<>();
        for (int i = 0; i < segs; i++) {
            double a0 = Math.PI * i / segs;
            double a1 = Math.PI * (i + 1) / segs;
            float x0 = ARCH_CR * (float) Math.cos(a0), y0 = archCY + ARCH_CR * (float) Math.sin(a0);
            float x1 = ARCH_CR * (float) Math.cos(a1), y1 = archCY + ARCH_CR * (float) Math.sin(a1);
            // inward-facing normal (toward interior = negative radial)
            float nx = -(float) Math.cos((a0 + a1) / 2), ny = -(float) Math.sin((a0 + a1) / 2);
            addTriN(vv, nn, nx, ny, 0, p(x0, y0, z0), p(x1, y1, z0), p(x1, y1, z1));
            addTriN(vv, nn, nx, ny, 0, p(x0, y0, z0), p(x1, y1, z1), p(x0, y0, z1));
        }
        return listToGeo(vv, nn);
    }

    // =========================================================================
    // TABLE
    // =========================================================================
    private TransformGroup buildTable() {
        TransformGroup tg = new TransformGroup();
        Appearance wood = matEmissive(c(0.15f, 0.09f, 0.05f), c(0.40f, 0.26f, 0.14f), c(0.08f, 0.05f, 0.02f));

        Cylinder top = new Cylinder(TABLE_R, TABLE_H, Cylinder.GENERATE_NORMALS, 48, 1, wood);
        tg.addChild(translated(0, TABLE_Y, 0, top));

        float ld = TABLE_R * 0.6f;
        for (float[] o : new float[][] { { ld, ld }, { -ld, ld }, { -ld, -ld }, { ld, -ld } }) {
            tg.addChild(translated(o[0], LEG_Y, o[1],
                    new Cylinder(LEG_R, LEG_H, Cylinder.GENERATE_NORMALS, 12, 1, wood)));
        }
        return tg;
    }

    // =========================================================================
    // GEM
    // =========================================================================
    private TransformGroup buildGem() {
        Shape3D gem = new Shape3D(octahedron(GEM_SIZE), gemMat());

        TransformGroup spinTG = new TransformGroup();
        spinTG.setCapability(TransformGroup.ALLOW_TRANSFORM_WRITE);
        RotationInterpolator rot = new RotationInterpolator(
                new Alpha(-1, 8000), spinTG, new Transform3D(), 0f, (float) (2 * Math.PI));
        rot.setSchedulingBounds(wb());
        spinTG.addChild(gem);
        spinTG.addChild(rot);

        PointLight glow = new PointLight(YELLOW, new Point3f(0, 0, 0), new Point3f(0.05f, 0.1f, 0f));
        glow.setInfluencingBounds(wb());
        spinTG.addChild(glow);

        Transform3D t = new Transform3D();
        t.setTranslation(new Vector3f(0, GEM_Y, 0));
        Transform3D s = new Transform3D();
        s.setScale(new Vector3d(1, GEM_SCALE_Y, 1));
        t.mul(s);
        TransformGroup posTG = new TransformGroup(t);
        posTG.addChild(spinTG);
        return posTG;
    }



    // =========================================================================
    // GAMEPLAY OBJECTS: keys, door, lights, and first-person movement
    // =========================================================================
    private TransformGroup buildKeys() {
        TransformGroup group = new TransformGroup();
        Appearance keyApp = matEmissive(c(0.45f, 0.30f, 0.04f), c(1.0f, 0.78f, 0.18f), c(0.45f, 0.28f, 0.02f));

        for (int i = 0; i < KEY_POSITIONS.length; i++) {
            Shape3D keyShape = new Shape3D(octahedron(0.22f), keyApp);
            TransformGroup spinTG = new TransformGroup();
            spinTG.setCapability(TransformGroup.ALLOW_TRANSFORM_WRITE);
            RotationInterpolator spin = new RotationInterpolator(new Alpha(-1, 2200), spinTG,
                    new Transform3D(), 0f, (float) (2 * Math.PI));
            spin.setSchedulingBounds(wb());
            spinTG.addChild(keyShape);
            spinTG.addChild(spin);

            Transform3D pos = new Transform3D();
            pos.setTranslation(new Vector3f(KEY_POSITIONS[i].x, KEY_POSITIONS[i].y, KEY_POSITIONS[i].z));
            keyTGs[i] = new TransformGroup(pos);
            keyTGs[i].setCapability(TransformGroup.ALLOW_TRANSFORM_WRITE);
            keyTGs[i].addChild(spinTG);
            group.addChild(keyTGs[i]);
        }
        return group;
    }

    private TransformGroup buildExitDoor() {
        TransformGroup group = new TransformGroup();

        Appearance doorApp = matEmissive(c(0.18f, 0.10f, 0.05f), c(0.36f, 0.20f, 0.10f), c(0.04f, 0.02f, 0.01f));
        Appearance frameApp = matEmissive(c(0.20f, 0.16f, 0.14f), c(0.48f, 0.40f, 0.34f), c(0.08f, 0.06f, 0.05f));

        // Door is placed at the far end of the north hallway.
        doorTG = new TransformGroup();
        doorTG.setCapability(TransformGroup.ALLOW_TRANSFORM_WRITE);
        Transform3D doorPos = new Transform3D();
        doorPos.setTranslation(new Vector3f(0f, FLOOR_Y + 1.65f, HALL_END_Z + 0.06f));
        doorTG.setTransform(doorPos);
        doorTG.addChild(new com.sun.j3d.utils.geometry.Box(ARCH_W / 2f - 0.25f, 1.65f, 0.08f,
                com.sun.j3d.utils.geometry.Primitive.GENERATE_NORMALS, doorApp));
        group.addChild(doorTG);

        // Small stone header around the door.
        group.addChild(translated(0, FLOOR_Y + 3.45f, HALL_END_Z + 0.02f,
                new com.sun.j3d.utils.geometry.Box(ARCH_W / 2f, 0.16f, 0.16f,
                        com.sun.j3d.utils.geometry.Primitive.GENERATE_NORMALS, frameApp)));

        Appearance lightOff = lightAppearance(false);
        float[] xs = { -0.9f, 0f, 0.9f };
        for (int i = 0; i < 3; i++) {
            doorLightShapes[i] = new Shape3D(octahedron(0.16f), lightOff);
            doorLightShapes[i].setCapability(Shape3D.ALLOW_APPEARANCE_WRITE);
            TransformGroup lightTG = translated(xs[i], FLOOR_Y + 3.78f, HALL_END_Z + 0.22f, doorLightShapes[i]);
            group.addChild(lightTG);

            doorLightNodes[i] = new PointLight(YELLOW, new Point3f(xs[i], FLOOR_Y + 3.78f, HALL_END_Z + 0.22f),
                    new Point3f(0.08f, 0.04f, 0.01f));
            doorLightNodes[i].setCapability(PointLight.ALLOW_STATE_WRITE);
            doorLightNodes[i].setEnable(false);
            doorLightNodes[i].setInfluencingBounds(wb());
            group.addChild(doorLightNodes[i]);
        }
        return group;
    }

    private Appearance lightAppearance(boolean on) {
        if (on) {
            return matEmissive(c(1.0f, 0.75f, 0.10f), c(1.0f, 0.85f, 0.20f), c(1.0f, 0.60f, 0.04f));
        }
        return matEmissive(c(0.08f, 0.07f, 0.05f), c(0.18f, 0.16f, 0.12f), c(0.02f, 0.018f, 0.012f));
    }

    private void collectKey(int index) {
        if (keyTaken[index]) return;
        keyTaken[index] = true;
        collectedKeys++;

        Transform3D hide = new Transform3D();
        hide.setScale(0.001);
        hide.setTranslation(new Vector3f(KEY_POSITIONS[index].x, -100f, KEY_POSITIONS[index].z));
        keyTGs[index].setTransform(hide);

        int lightIndex = collectedKeys - 1;
        doorLightShapes[lightIndex].setAppearance(lightAppearance(true));
        doorLightNodes[lightIndex].setEnable(true);

        if (collectedKeys == 3) {
            openDoor();
        }
    }

    private void openDoor() {
        Transform3D opened = new Transform3D();
        // Slides upward so the doorway visibly opens.
        opened.setTranslation(new Vector3f(0f, FLOOR_Y + 4.35f, HALL_END_Z + 0.06f));
        doorTG.setTransform(opened);
    }

    private boolean isWalkable(float x, float z) {
        if ((x * x + z * z) <= (ROOM_R - PLAYER_RADIUS) * (ROOM_R - PLAYER_RADIUS)) {
            return true;
        }

        float hw = ARCH_W / 2f - PLAYER_RADIUS;
        if (Math.abs(x) <= hw && z <= NORTH_Z + 0.25f && z >= HALL_END_Z + PLAYER_RADIUS) return true;
        if (Math.abs(x) <= hw && z >= SOUTH_Z - 0.25f && z <= SOUTH_END_Z - PLAYER_RADIUS) return true;

        return inRotatedNorthHall(x, z, SE_ANGLE) || inRotatedNorthHall(x, z, SW_ANGLE);
    }

    private boolean inRotatedNorthHall(float x, float z, double wallAngle) {
        double rotY = wallAngle - (3.0 * Math.PI / 2.0);
        double inv = -rotY;
        float lx = (float) (Math.cos(inv) * x + Math.sin(inv) * z);
        float lz = (float) (-Math.sin(inv) * x + Math.cos(inv) * z);
        float hw = ARCH_W / 2f - PLAYER_RADIUS;
        return Math.abs(lx) <= hw && lz <= NORTH_Z + 0.25f && lz >= HALL_END_Z + PLAYER_RADIUS;
    }

    private class FirstPersonController extends Behavior implements KeyListener, MouseMotionListener {
        private final TransformGroup viewTG;
        private final Canvas3D canvas;
        private final WakeupOnElapsedFrames wakeup = new WakeupOnElapsedFrames(0);
        private final boolean[] keys = new boolean[256];
        private float x = 0f, z = 10f, yaw = 0f, pitch = 0f;
        private int lastMouseX = -1;
        private int lastMouseY = -1;

        FirstPersonController(TransformGroup viewTG, Canvas3D canvas) {
            this.viewTG = viewTG;
            this.canvas = canvas;
        }

        public void initialize() {
            wakeupOn(wakeup);
        }

        public void processStimulus(java.util.Enumeration criteria) {
            updateMovement();
            checkKeyPickups();
            wakeupOn(wakeup);
        }

        private void updateMovement() {
            float forward = 0f, strafe = 0f;
            if (down(KeyEvent.VK_W)) forward += 1f;
            if (down(KeyEvent.VK_S)) forward -= 1f;
            if (down(KeyEvent.VK_A)) strafe -= 1f;
            if (down(KeyEvent.VK_D)) strafe += 1f;
            if (down(KeyEvent.VK_LEFT)) yaw += 0.045f;
            if (down(KeyEvent.VK_RIGHT)) yaw -= 0.045f;
            if (down(KeyEvent.VK_UP)) pitch += 0.035f;
            if (down(KeyEvent.VK_DOWN)) pitch -= 0.035f;
            clampPitch();

            if (forward != 0f || strafe != 0f) {
                float len = (float) Math.sqrt(forward * forward + strafe * strafe);
                forward /= len;
                strafe /= len;

                float sin = (float) Math.sin(yaw);
                float cos = (float) Math.cos(yaw);
                float dx = (-sin * forward + cos * strafe) * WALK_SPEED;
                float dz = (-cos * forward - sin * strafe) * WALK_SPEED;

                tryMove(dx, dz);
            }

            Transform3D yawT = new Transform3D();
            yawT.rotY(yaw);

            Transform3D pitchT = new Transform3D();
            pitchT.rotX(pitch);

            yawT.mul(pitchT);
            yawT.setTranslation(new Vector3f(x, PLAYER_EYE_Y, z));
            viewTG.setTransform(yawT);
        }

        private void tryMove(float dx, float dz) {
            // Axis-separated movement gives a smooth slide along walls.
            if (isWalkable(x + dx, z)) x += dx;
            if (isWalkable(x, z + dz)) z += dz;
        }

        private void clampPitch() {
            float limit = (float) Math.toRadians(80);
            if (pitch > limit) pitch = limit;
            if (pitch < -limit) pitch = -limit;
        }

        private void checkKeyPickups() {
            for (int i = 0; i < KEY_POSITIONS.length; i++) {
                if (keyTaken[i]) continue;
                float dx = x - KEY_POSITIONS[i].x;
                float dz = z - KEY_POSITIONS[i].z;
                if (dx * dx + dz * dz < 0.85f * 0.85f) {
                    collectKey(i);
                }
            }
        }

        private boolean down(int code) {
            return code >= 0 && code < keys.length && keys[code];
        }

        public void keyPressed(KeyEvent e) {
            if (e.getKeyCode() < keys.length) keys[e.getKeyCode()] = true;
        }

        public void keyReleased(KeyEvent e) {
            if (e.getKeyCode() < keys.length) keys[e.getKeyCode()] = false;
        }

        public void keyTyped(KeyEvent e) { }

        public void mouseDragged(MouseEvent e) {
            mouseMoved(e);
        }

        public void mouseMoved(MouseEvent e) {
            if (!canvas.hasFocus()) canvas.requestFocusInWindow();
            if (lastMouseX >= 0 && lastMouseY >= 0) {
                int dx = e.getX() - lastMouseX;
                int dy = e.getY() - lastMouseY;
                yaw -= dx * MOUSE_SENS;
                pitch -= dy * MOUSE_SENS;
                clampPitch();
            }
            lastMouseX = e.getX();
            lastMouseY = e.getY();
        }
    }

    // =========================================================================
    // GEOMETRY UTILITIES
    // =========================================================================

    /** Add 3 vertices + normals for one triangle. */
    private void addTriN(ArrayList<float[]> vv, ArrayList<float[]> nn,
            float nx, float ny, float nz,
            float[] a, float[] b, float[] c) {
        float[] n = { nx, ny, nz };
        vv.add(a);
        vv.add(b);
        vv.add(c);
        nn.add(n);
        nn.add(n);
        nn.add(n);
    }

    /** Add two triangles forming a quad (vertices: BL, BR, TR, TL). */
    private void addQuadN(ArrayList<float[]> vv, ArrayList<float[]> nn,
            float nx, float ny, float nz,
            float[] bl, float[] br, float[] tr, float[] tl) {
        addTriN(vv, nn, nx, ny, nz, bl, br, tr);
        addTriN(vv, nn, nx, ny, nz, bl, tr, tl);
    }

    private GeometryArray listToGeo(ArrayList<float[]> vv, ArrayList<float[]> nn) {
        int cnt = vv.size();
        TriangleArray geo = new TriangleArray(cnt, GeometryArray.COORDINATES | GeometryArray.NORMALS);
        float[] vc = new float[cnt * 3], nc = new float[cnt * 3];
        for (int i = 0; i < cnt; i++) {
            float[] v = vv.get(i), n = nn.get(i);
            vc[i * 3] = v[0];
            vc[i * 3 + 1] = v[1];
            vc[i * 3 + 2] = v[2];
            nc[i * 3] = n[0];
            nc[i * 3 + 1] = n[1];
            nc[i * 3 + 2] = n[2];
        }
        geo.setCoordinates(0, vc);
        geo.setNormals(0, nc);
        return geo;
    }

    /** Inline quad geometry (BL, BR, TR, TL + normal). */
    private GeometryArray quadGeo(float[] bl, float[] br, float[] tr, float[] tl, float[] n) {
        ArrayList<float[]> vv = new ArrayList<>(), nn = new ArrayList<>();
        addQuadN(vv, nn, n[0], n[1], n[2], bl, br, tr, tl);
        return listToGeo(vv, nn);
    }

    /** Flat disc at (0,y,0). normalY=+1→up, -1→down. */
    private TransformGroup placedDisc(float r, int seg, float normalY, float y, Appearance app) {
        int vCount = seg + 2;
        TriangleFanArray geo = new TriangleFanArray(vCount,
                GeometryArray.COORDINATES | GeometryArray.NORMALS, new int[] { vCount });
        float[] co = new float[vCount * 3], no = new float[vCount * 3];
        co[0] = 0;
        co[1] = 0;
        co[2] = 0;
        no[1] = normalY;
        for (int i = 0; i <= seg; i++) {
            double a = normalY > 0 ? 2 * Math.PI * i / seg : -2 * Math.PI * i / seg;
            int b = (i + 1) * 3;
            co[b] = (float) (r * Math.cos(a));
            co[b + 2] = (float) (r * Math.sin(a));
            no[b + 1] = normalY;
        }
        geo.setCoordinates(0, co);
        geo.setNormals(0, no);
        Transform3D t = new Transform3D();
        t.setTranslation(new Vector3f(0, y, 0));
        TransformGroup tg = new TransformGroup(t);
        tg.addChild(new Shape3D(geo, app));
        return tg;
    }

    private GeometryArray octahedron(float s) {
        float[][] verts = {
                { s, 0, 0 }, { 0, s, 0 }, { 0, 0, s }, { 0, 0, s }, { 0, s, 0 }, { -s, 0, 0 },
                { -s, 0, 0 }, { 0, s, 0 }, { 0, 0, -s }, { 0, 0, -s }, { 0, s, 0 }, { s, 0, 0 },
                { s, 0, 0 }, { 0, 0, s }, { 0, -s, 0 }, { 0, 0, s }, { -s, 0, 0 }, { 0, -s, 0 },
                { -s, 0, 0 }, { 0, 0, -s }, { 0, -s, 0 }, { 0, 0, -s }, { s, 0, 0 }, { 0, -s, 0 }
        };
        TriangleArray geo = new TriangleArray(verts.length,
                GeometryArray.COORDINATES | GeometryArray.NORMALS);
        float[] co = new float[verts.length * 3], no = new float[verts.length * 3];
        for (int i = 0; i < verts.length; i += 3) {
            Vector3f a = vf(verts[i]), ab = new Vector3f(), ac = new Vector3f(), n = new Vector3f();
            ab.sub(vf(verts[i + 1]), a);
            ac.sub(vf(verts[i + 2]), a);
            n.cross(ab, ac);
            n.normalize();
            for (int j = 0; j < 3; j++) {
                int k = (i + j) * 3;
                co[k] = verts[i + j][0];
                co[k + 1] = verts[i + j][1];
                co[k + 2] = verts[i + j][2];
                no[k] = n.x;
                no[k + 1] = n.y;
                no[k + 2] = n.z;
            }
        }
        geo.setCoordinates(0, co);
        geo.setNormals(0, no);
        return geo;
    }

    // ── material helpers ──────────────────────────────────────────────────────
    private Appearance matEmissive(Color3f amb, Color3f diff, Color3f emis) {
        Appearance app = new Appearance();
        Material m = new Material();
        m.setAmbientColor(amb);
        m.setDiffuseColor(diff);
        m.setEmissiveColor(emis);
        m.setSpecularColor(c(0.25f, 0.22f, 0.15f));
        m.setShininess(18);
        m.setLightingEnable(true);
        app.setMaterial(m);
        PolygonAttributes pa = new PolygonAttributes();
        pa.setCullFace(PolygonAttributes.CULL_NONE);
        app.setPolygonAttributes(pa);
        return app;
    }

    private Appearance gemMat() {
        Appearance app = new Appearance();
        Material m = new Material();
        m.setEmissiveColor(c(0.9f, 0.7f, 0));
        m.setAmbientColor(c(1, 0.8f, 0));
        m.setDiffuseColor(c(1, 0.9f, 0.3f));
        m.setSpecularColor(c(1, 1, 0.6f));
        m.setShininess(128);
        m.setLightingEnable(true);
        app.setMaterial(m);
        // Fully opaque — removes all transparency depth-sorting issues.
        // The strong emissive color makes it look glowing without needing alpha.
        PolygonAttributes pa = new PolygonAttributes();
        pa.setCullFace(PolygonAttributes.CULL_NONE);
        app.setPolygonAttributes(pa);
        return app;
    }

    // ── tiny helpers ──────────────────────────────────────────────────────────
    private Color3f c(float r, float g, float b) {
        return new Color3f(r, g, b);
    }

    private float[] p(float x, float y, float z) {
        return new float[] { x, y, z };
    }

    private Vector3f vf(float[] a) {
        return new Vector3f(a[0], a[1], a[2]);
    }

    private BoundingSphere wb() {
        return new BoundingSphere(new Point3d(), 60);
    }

    private TransformGroup translated(float x, float y, float z, Node child) {
        Transform3D t = new Transform3D();
        t.setTranslation(new Vector3f(x, y, z));
        TransformGroup tg = new TransformGroup(t);
        tg.addChild(child);
        return tg;
    }

    // =========================================================================
    public static void main(String[] args) {
        SwingUtilities.invokeLater(RoundtableHold::new);
    }
}
