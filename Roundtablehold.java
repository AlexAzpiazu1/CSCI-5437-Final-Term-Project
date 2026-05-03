import com.sun.j3d.utils.universe.SimpleUniverse;
import com.sun.j3d.utils.geometry.Cylinder;

import javax.media.j3d.*;
import javax.vecmath.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
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
    private static final float LEG_R = 0.08f;
    private static final float LEG_H = 1.1f;
    private static final float TABLE_Y = FLOOR_Y + LEG_H + TABLE_H / 2f;
    private static final float LEG_Y = FLOOR_Y + LEG_H / 2f;

    // ── gem ───────────────────────────────────────────────────────────────────
    private static final float GEM_Y = TABLE_Y + TABLE_H / 2f + 1.8f;
    private static final float GEM_SIZE = 0.22f;
    private static final float GEM_SCALE_Y = 2.8f;

    // ── archway opening ───────────────────────────────────────────────────────
    private static final float ARCH_W = 5.5f;
    private static final float ARCH_SH = 3.2f;
    private static final float ARCH_CR = ARCH_W / 2f;
    private static final float ARCH_TOTAL = ARCH_SH + ARCH_CR;
    private static final float ARCH_TRIM = 0.55f;

    // ── hallway ───────────────────────────────────────────────────────────────
    private static final float HALL_DEPTH = 10.0f;
    private static final float NORTH_Z = -(float) Math.sqrt(ROOM_R * ROOM_R - (ARCH_W / 2f) * (ARCH_W / 2f)) + 0.1f;
    private static final float HALL_END_Z = NORTH_Z - HALL_DEPTH;
    private static final float SOUTH_Z = (float) Math.sqrt(ROOM_R * ROOM_R - (ARCH_W / 2f) * (ARCH_W / 2f)) - 0.1f;
    private static final float SOUTH_END_Z = SOUTH_Z + HALL_DEPTH;

    private static final double SE_ANGLE = Math.PI / 4.0;
    private static final double SW_ANGLE = 3.0 * Math.PI / 4.0;
    private static final float DIAG_CHORD = (float) Math.sqrt(ROOM_R * ROOM_R - (ARCH_W / 2f) * (ARCH_W / 2f)) - 0.1f;

    // ── colours ───────────────────────────────────────────────────────────────
    private static final Color3f YELLOW = new Color3f(1.0f, 0.85f, 0.2f);
    private static final Color3f AMB_COL = new Color3f(0.35f, 0.30f, 0.38f);

    // ── first-person ──────────────────────────────────────────────────────────
    private static final float PLAYER_RADIUS = 0.38f;
    private static final float PLAYER_EYE_Y = FLOOR_Y + 2.35f;
    private static final float WALK_SPEED = 0.16f;
    private static final float MOUSE_SENS = 0.0022f;
    private static final double FOV_DEG = 90.0;

    private final TransformGroup[] keyTGs = new TransformGroup[3];
    private final boolean[] keyTaken = new boolean[3];
    private final Shape3D[] doorLightShapes = new Shape3D[3];
    private final PointLight[] doorLightNodes = new PointLight[3];
    private TransformGroup doorTG;
    private int collectedKeys = 0;

    // Fire switch state
    private final TransformGroup[] fireDiamondTGs = new TransformGroup[3];
    private final PointLight[] fireDiamondLights = new PointLight[3];
    private boolean fireOn = true;
    private TransformGroup switchLeverTG;
    private TransformGroup fireplaceKeyTG; // rises from floor when fire is switched on
    private boolean fireplaceKeyTaken = false;

    private static final Point3f[] KEY_POSITIONS = new Point3f[] {
            new Point3f(5.5f, FLOOR_Y + 0.45f, 4.8f),
            new Point3f(-5.8f, FLOOR_Y + 0.45f, -3.8f),
            new Point3f(2.2f, FLOOR_Y + 0.45f, -9.2f)
    };
    // The fireplace key starts underground and rises when the fire is switched on
    private static final Point3f FIRE_KEY_POS = new Point3f(ROOM_R - 2.5f, FLOOR_Y + 0.45f, 0.0f);

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
        View view = universe.getViewer().getView();
        view.setFieldOfView(Math.toRadians(FOV_DEG));

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
        root.addChild(buildChairs());
        root.addChild(buildFireplace());
        root.addChild(buildFireSign());
        root.addChild(buildLightSwitch());
        root.addChild(buildGem());
        root.addChild(buildKeys());
        root.addChild(buildFireplaceKey());
        root.addChild(buildExitDoor());

        FirstPersonController controller = new FirstPersonController(vpTG, canvas);
        controller.setSchedulingBounds(wb());
        canvas.addKeyListener(controller);
        canvas.addMouseMotionListener(controller);
        canvas.addMouseListener(controller);
        root.addChild(controller);

        return root;
    }

    // =========================================================================
    private void addLights(BranchGroup root) {
        // Keep total scene lights low — Java3D silently ignores lights beyond
        // the fixed-function OpenGL limit of 8 per surface.
        // Budget: 1 ambient + 1 gem + 1 fill + 1 hall = 4 fixed lights,
        // leaving 4 slots free for the 3 fire diamond lights + flicker light.

        AmbientLight al = new AmbientLight(new Color3f(0.55f, 0.50f, 0.58f)); // brighter ambient replaces directionals
        al.setInfluencingBounds(wb());
        root.addChild(al);

        PointLight gem = new PointLight(YELLOW, new Point3f(0, GEM_Y, 0),
                new Point3f(0.06f, 0.05f, 0.005f));
        gem.setInfluencingBounds(wb());
        root.addChild(gem);

        PointLight fill = new PointLight(new Color3f(0.3f, 0.22f, 0.05f),
                new Point3f(0, TABLE_Y - 0.5f, 0), new Point3f(0.15f, 0.08f, 0));
        fill.setInfluencingBounds(wb());
        root.addChild(fill);

        PointLight hall = new PointLight(new Color3f(0.28f, 0.22f, 0.14f),
                new Point3f(0, FLOOR_Y + ARCH_TOTAL - 0.8f, NORTH_Z - HALL_DEPTH / 2f),
                new Point3f(0.04f, 0.03f, 0.002f));
        hall.setInfluencingBounds(wb());
        root.addChild(hall);

        // Fire diamond lights. World pos: X=(ROOM_R-0.17) is too close to the wall
        // causing a grazing cone. Pull 1.5 units into the room (lower X).
        float[] fdX = { -0.38f, 0.00f, 0.38f };
        Color3f[] fdCol = {
                new Color3f(0.95f, 0.08f, 0.02f),
                new Color3f(1.00f, 0.45f, 0.03f),
                new Color3f(1.00f, 0.82f, 0.10f),
        };
        for (int fi = 0; fi < 3; fi++) {
            PointLight fl = new PointLight(
                    fdCol[fi],
                    new Point3f(ROOM_R - 1.7f, FLOOR_Y + 0.60f, fdX[fi]),
                    new Point3f(0.05f, 0.18f, 0.04f));
            fl.setCapability(PointLight.ALLOW_STATE_WRITE);
            fl.setInfluencingBounds(wb());
            root.addChild(fl);
            fireDiamondLights[fi] = fl;
        }
    }

    // =========================================================================
    // ROOM
    // =========================================================================
    private TransformGroup buildRoom() {
        TransformGroup tg = new TransformGroup();
        Appearance wallApp = textureAppearance(new Color(80, 68, 58), new Color(135, 120, 105), 128, false);
        Appearance floorApp = matEmissive(c(0.14f, 0.11f, 0.09f), c(0.32f, 0.26f, 0.20f), c(0.16f, 0.12f, 0.09f));
        Appearance ceilApp = matEmissive(c(0.12f, 0.09f, 0.11f), c(0.28f, 0.22f, 0.26f), c(0.12f, 0.09f, 0.11f));
        tg.addChild(new Shape3D(buildWallGeo(), wallApp));
        tg.addChild(placedDisc(ROOM_R, 96, 1f, FLOOR_Y, floorApp));
        tg.addChild(placedDisc(ROOM_R, 96, -1f, CEIL_Y, ceilApp));
        return tg;
    }

    private GeometryArray buildWallGeo() {
        final int ROWS = 600;
        ArrayList<float[]> vv = new ArrayList<>(), nn = new ArrayList<>();
        float half = ROOM_H / 2f;
        double halfSpan = Math.asin((ARCH_W / 2.0) / ROOM_R);
        double northA = 3.0 * Math.PI / 2.0;
        double southA = Math.PI / 2.0;
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
                addTriN(vv, nn, nx, 0, nz, p(x1, y0, z1), p(x1, y1, z1), p(x0, y1, z0));
                addTriN(vv, nn, nx, 0, nz, p(x1, y0, z1), p(x0, y1, z0), p(x0, y0, z0));
            }
        }
        return listToGeo(vv, nn);
    }

    private boolean insideArch(float wx, float wy) {
        if (Math.abs(wx) > ARCH_W / 2f)
            return false;
        float archBase = FLOOR_Y;
        if (wy < archBase)
            return false;
        if (wy <= archBase + ARCH_SH)
            return true;
        float dy = wy - (archBase + ARCH_SH);
        return (wx * wx + dy * dy) < ARCH_CR * ARCH_CR;
    }

    // =========================================================================
    // ARCHWAY TRIM (north)
    // =========================================================================
    private TransformGroup buildArchway() {
        TransformGroup tg = new TransformGroup();
        Appearance stone = matEmissive(c(0.24f, 0.19f, 0.16f), c(0.58f, 0.48f, 0.40f), c(0.16f, 0.13f, 0.10f));
        float z = NORTH_Z + 0.03f, zB = z - ARCH_TRIM;
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

    // =========================================================================
    // HALLWAY (north)
    // =========================================================================
    private TransformGroup buildHallway() {
        TransformGroup tg = new TransformGroup();
        Appearance stone = matEmissive(c(0.18f, 0.14f, 0.12f), c(0.42f, 0.34f, 0.28f), c(0.12f, 0.09f, 0.07f));
        float hw = ARCH_W / 2f, flY = FLOOR_Y, ceY = FLOOR_Y + ARCH_TOTAL, z0 = NORTH_Z, z1 = HALL_END_Z;
        tg.addChild(new Shape3D(
                quadGeo(p(-hw, flY, z1), p(-hw, ceY, z1), p(-hw, ceY, z0), p(-hw, flY, z0), new float[] { 1, 0, 0 }),
                stone));
        tg.addChild(new Shape3D(
                quadGeo(p(hw, flY, z0), p(hw, ceY, z0), p(hw, ceY, z1), p(hw, flY, z1), new float[] { -1, 0, 0 }),
                stone));
        tg.addChild(new Shape3D(
                quadGeo(p(-hw, flY, z0), p(hw, flY, z0), p(hw, flY, z1), p(-hw, flY, z1), new float[] { 0, 1, 0 }),
                stone));
        tg.addChild(new Shape3D(
                quadGeo(p(-hw, flY, z1), p(-hw, ceY, z1), p(hw, ceY, z1), p(hw, flY, z1), new float[] { 0, 0, 1 }),
                stone));
        tg.addChild(new Shape3D(
                quadGeo(p(-hw, ceY, z0), p(-hw, ceY, z1), p(hw, ceY, z1), p(hw, ceY, z0), new float[] { 0, -1, 0 }),
                stone));
        tg.addChild(new Shape3D(buildArchCeilingGeo(z0, z1), stone));
        tg.addChild(makeFogPlanes(z0, z1));
        return tg;
    }

    // =========================================================================
    // SOUTH ARCHWAY TRIM
    // =========================================================================
    private TransformGroup buildSouthArchway() {
        TransformGroup tg = new TransformGroup();
        Appearance stone = matEmissive(c(0.24f, 0.19f, 0.16f), c(0.58f, 0.48f, 0.40f), c(0.16f, 0.13f, 0.10f));
        float z = SOUTH_Z - 0.03f, zB = z + ARCH_TRIM;
        float inner = ARCH_W / 2f, outer = inner + ARCH_TRIM;
        float base = FLOOR_Y, top = base + ARCH_SH, archCY = top;
        int segs = 32;

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

        ArrayList<float[]> tv = new ArrayList<>(), tn = new ArrayList<>();
        addQuadN(tv, tn, 0, 1, 0, p(-inner, top, z), p(-outer, top, z), p(-outer, top, zB), p(-inner, top, zB));
        addQuadN(tv, tn, 0, 1, 0, p(outer, top, z), p(inner, top, z), p(inner, top, zB), p(outer, top, zB));
        tg.addChild(new Shape3D(listToGeo(tv, tn), stone));
        return tg;
    }

    // =========================================================================
    // SOUTH HALLWAY
    // =========================================================================
    private TransformGroup buildSouthHallway() {
        TransformGroup tg = new TransformGroup();
        Appearance stone = matEmissive(c(0.18f, 0.14f, 0.12f), c(0.42f, 0.34f, 0.28f), c(0.12f, 0.09f, 0.07f));
        float hw = ARCH_W / 2f, flY = FLOOR_Y, ceY = FLOOR_Y + ARCH_TOTAL, z0 = SOUTH_Z, z1 = SOUTH_END_Z;
        tg.addChild(new Shape3D(
                quadGeo(p(-hw, flY, z0), p(-hw, ceY, z0), p(-hw, ceY, z1), p(-hw, flY, z1), new float[] { 1, 0, 0 }),
                stone));
        tg.addChild(new Shape3D(
                quadGeo(p(hw, flY, z1), p(hw, ceY, z1), p(hw, ceY, z0), p(hw, flY, z0), new float[] { -1, 0, 0 }),
                stone));
        tg.addChild(new Shape3D(
                quadGeo(p(-hw, flY, z1), p(hw, flY, z1), p(hw, flY, z0), p(-hw, flY, z0), new float[] { 0, 1, 0 }),
                stone));
        tg.addChild(new Shape3D(
                quadGeo(p(hw, flY, z1), p(hw, ceY, z1), p(-hw, ceY, z1), p(-hw, flY, z1), new float[] { 0, 0, -1 }),
                stone));
        tg.addChild(new Shape3D(
                quadGeo(p(-hw, ceY, z1), p(hw, ceY, z1), p(hw, ceY, z0), p(-hw, ceY, z0), new float[] { 0, -1, 0 }),
                stone));
        tg.addChild(new Shape3D(buildArchCeilingGeo(z0, z1), stone));
        tg.addChild(makeFogPlanes(z0, z1));
        return tg;
    }

    // =========================================================================
    // DIAGONAL ARCHWAY + HALLWAY
    // =========================================================================
    private TransformGroup buildDiagArchwayAndHallway(double wallAngle) {
        double rotY = wallAngle - (3.0 * Math.PI / 2.0);
        Transform3D rot = new Transform3D();
        rot.rotY(rotY);
        TransformGroup tg = new TransformGroup(rot);
        tg.addChild(buildNorthArchway());
        tg.addChild(buildNorthHallway());
        return tg;
    }

    private TransformGroup buildNorthArchway() {
        TransformGroup tg = new TransformGroup();
        Appearance stone = matEmissive(c(0.24f, 0.19f, 0.16f), c(0.58f, 0.48f, 0.40f), c(0.16f, 0.13f, 0.10f));
        float z = NORTH_Z + 0.03f, zB = z - ARCH_TRIM;
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

    private TransformGroup buildNorthHallway() {
        TransformGroup tg = new TransformGroup();
        Appearance stone = matEmissive(c(0.18f, 0.14f, 0.12f), c(0.42f, 0.34f, 0.28f), c(0.12f, 0.09f, 0.07f));
        float hw = ARCH_W / 2f, flY = FLOOR_Y, ceY = FLOOR_Y + ARCH_TOTAL, z0 = NORTH_Z, z1 = HALL_END_Z;
        tg.addChild(new Shape3D(
                quadGeo(p(-hw, flY, z1), p(-hw, ceY, z1), p(-hw, ceY, z0), p(-hw, flY, z0), new float[] { 1, 0, 0 }),
                stone));
        tg.addChild(new Shape3D(
                quadGeo(p(hw, flY, z0), p(hw, ceY, z0), p(hw, ceY, z1), p(hw, flY, z1), new float[] { -1, 0, 0 }),
                stone));
        tg.addChild(new Shape3D(
                quadGeo(p(-hw, flY, z0), p(hw, flY, z0), p(hw, flY, z1), p(-hw, flY, z1), new float[] { 0, 1, 0 }),
                stone));
        tg.addChild(new Shape3D(
                quadGeo(p(-hw, flY, z1), p(-hw, ceY, z1), p(hw, ceY, z1), p(hw, flY, z1), new float[] { 0, 0, 1 }),
                stone));
        tg.addChild(new Shape3D(
                quadGeo(p(-hw, ceY, z0), p(-hw, ceY, z1), p(hw, ceY, z1), p(hw, ceY, z0), new float[] { 0, -1, 0 }),
                stone));
        tg.addChild(new Shape3D(buildArchCeilingGeo(z0, z1), stone));
        tg.addChild(makeFogPlanes(z0, z1));
        return tg;
    }

    private TransformGroup makeFogPlanes(float z0, float z1) {
        TransformGroup tg = new TransformGroup();
        int FOG_COUNT = 8;
        float planeW = ARCH_W * 2.5f, planeH = ARCH_TOTAL * 2f, flY = FLOOR_Y;
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
            float t = (i + 1f) / (FOG_COUNT + 1f), planeZ = z0 + (z1 - z0) * t;
            float px = planeW / 2f, pyB = flY - (planeH - ARCH_TOTAL) / 2f, pyT = pyB + planeH;
            tg.addChild(new Shape3D(quadGeo(p(-px, pyB, planeZ), p(px, pyB, planeZ), p(px, pyT, planeZ),
                    p(-px, pyT, planeZ), new float[] { 0, 0, 1 }), fog));
        }
        return tg;
    }

    private GeometryArray buildArchCeilingGeo(float z0, float z1) {
        int segs = 32;
        float archCY = FLOOR_Y + ARCH_SH;
        ArrayList<float[]> vv = new ArrayList<>(), nn = new ArrayList<>();
        for (int i = 0; i < segs; i++) {
            double a0 = Math.PI * i / segs, a1 = Math.PI * (i + 1) / segs;
            float x0 = ARCH_CR * (float) Math.cos(a0), y0 = archCY + ARCH_CR * (float) Math.sin(a0);
            float x1 = ARCH_CR * (float) Math.cos(a1), y1 = archCY + ARCH_CR * (float) Math.sin(a1);
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
        Appearance wood = textureAppearance(new Color(92, 50, 22), new Color(145, 88, 38), 128, true);
        int flags = Cylinder.GENERATE_NORMALS | Cylinder.GENERATE_TEXTURE_COORDS;
        tg.addChild(translated(0, TABLE_Y, 0, new Cylinder(TABLE_R, TABLE_H, flags, 64, 1, wood)));
        float ld = TABLE_R * 0.6f;
        for (float[] o : new float[][] { { ld, ld }, { -ld, ld }, { -ld, -ld }, { ld, -ld } })
            tg.addChild(translated(o[0], LEG_Y, o[1], new Cylinder(LEG_R, LEG_H, flags, 16, 1, wood)));
        return tg;
    }

    // =========================================================================
    // CHAIRS
    // =========================================================================
    private TransformGroup buildChairs() {
        TransformGroup group = new TransformGroup();
        Appearance chairWood = textureAppearance(new Color(75, 38, 18), new Color(125, 70, 32), 96, true);
        float chairDistance = TABLE_R + 1.05f;
        for (int i = 0; i < 6; i++) {
            double angle = i * Math.PI * 2.0 / 6.0;
            float x = (float) Math.cos(angle) * chairDistance, z = (float) Math.sin(angle) * chairDistance;
            Transform3D pos = new Transform3D();
            pos.setTranslation(new Vector3f(x, FLOOR_Y + 0.55f, z));
            Transform3D rot = new Transform3D();
            rot.rotY(-angle + Math.PI / 2.0);
            pos.mul(rot);
            TransformGroup chairTG = new TransformGroup(pos);
            chairTG.addChild(makeChair(chairWood));
            group.addChild(chairTG);
        }
        return group;
    }

    private TransformGroup makeChair(Appearance app) {
        TransformGroup chair = new TransformGroup();
        int flags = com.sun.j3d.utils.geometry.Primitive.GENERATE_NORMALS
                | com.sun.j3d.utils.geometry.Primitive.GENERATE_TEXTURE_COORDS;
        chair.addChild(translated(0f, 0.25f, 0f, new com.sun.j3d.utils.geometry.Box(0.45f, 0.08f, 0.45f, flags, app)));
        chair.addChild(
                translated(0f, 0.78f, 0.38f, new com.sun.j3d.utils.geometry.Box(0.48f, 0.55f, 0.08f, flags, app)));
        int cylFlags = Cylinder.GENERATE_NORMALS | Cylinder.GENERATE_TEXTURE_COORDS;
        for (float[] pt : new float[][] { { 0.32f, 0.32f }, { -0.32f, 0.32f }, { 0.32f, -0.32f }, { -0.32f, -0.32f } })
            chair.addChild(translated(pt[0], -0.23f, pt[1], new Cylinder(0.045f, 0.85f, cylFlags, 12, 1, app)));
        return chair;
    }

    // =========================================================================
    // FIREPLACE
    // =========================================================================
    private TransformGroup buildFireplace() {
        TransformGroup group = new TransformGroup();

        Appearance stone = textureAppearance(new Color(70, 65, 60), new Color(125, 115, 105), 64, false);
        Appearance dark = matEmissive(c(0.02f, 0.015f, 0.01f), c(0.05f, 0.035f, 0.02f), c(0.01f, 0.005f, 0.002f));
        Appearance chimneyApp = textureAppearance(new Color(60, 58, 55), new Color(115, 110, 105), 64, false);
        Appearance logApp = textureAppearance(new Color(78, 42, 19), new Color(135, 82, 36), 64, true);

        int flags = com.sun.j3d.utils.geometry.Primitive.GENERATE_NORMALS
                | com.sun.j3d.utils.geometry.Primitive.GENERATE_TEXTURE_COORDS;
        int cylFlags = Cylinder.GENERATE_NORMALS | Cylinder.GENERATE_TEXTURE_COORDS;

        // The fireplace local frame: rotated 90° around Y so local +Z points into
        // the room. The base transform places it against the east wall.
        Transform3D base = new Transform3D();
        base.setTranslation(new Vector3f(ROOM_R - 0.22f, FLOOR_Y + 1.05f, 0f));
        Transform3D rot = new Transform3D();
        rot.rotY(Math.PI / 2.0);
        base.mul(rot);
        TransformGroup fp = new TransformGroup(base);

        // ── back wall of fireplace opening ────────────────────────────────────
        fp.addChild(translated(0f, 0f, -0.06f,
                new com.sun.j3d.utils.geometry.Box(0.85f, 0.75f, 0.08f, flags, dark)));

        // ── side pillars — deeper into the room (Z half-extent 0.25 → 0.65) ──
        fp.addChild(translated(-1.05f, 0f, 0f,
                new com.sun.j3d.utils.geometry.Box(0.22f, 0.95f, 0.65f, flags, stone)));
        fp.addChild(translated(1.05f, 0f, 0f,
                new com.sun.j3d.utils.geometry.Box(0.22f, 0.95f, 0.65f, flags, stone)));

        // ── mantle — wider and deeper (X half 1.35→1.40, Z half 0.28→0.68) ──
        fp.addChild(translated(0f, 0.95f, 0f,
                new com.sun.j3d.utils.geometry.Box(1.40f, 0.22f, 0.68f, flags, stone)));

        // ── hearth — deeper (Z half 0.55→0.85) ───────────────────────────────
        fp.addChild(translated(0f, -0.78f, 0.12f,
                new com.sun.j3d.utils.geometry.Box(1.45f, 0.18f, 0.85f, flags, stone)));

        // ── logs ──────────────────────────────────────────────────────────────
        Transform3D log1Rot = new Transform3D();
        log1Rot.rotZ(Math.PI / 2.0);
        Transform3D log1Pos = new Transform3D();
        log1Pos.setTranslation(new Vector3f(0f, -0.58f, 0.17f));
        log1Pos.mul(log1Rot);
        TransformGroup log1TG = new TransformGroup(log1Pos);
        log1TG.addChild(new Cylinder(0.06f, 0.85f, cylFlags, 16, 1, logApp));
        fp.addChild(log1TG);

        Transform3D log2Rot = new Transform3D();
        log2Rot.rotZ(Math.PI / 2.0);
        Transform3D log2Yaw = new Transform3D();
        log2Yaw.rotY(0.35);
        Transform3D log2Pos = new Transform3D();
        log2Pos.setTranslation(new Vector3f(0f, -0.48f, 0.22f));
        log2Pos.mul(log2Yaw);
        log2Pos.mul(log2Rot);
        TransformGroup log2TG = new TransformGroup(log2Pos);
        log2TG.addChild(new Cylinder(0.06f, 0.75f, cylFlags, 16, 1, logApp));
        fp.addChild(log2TG);

        // ── fire diamonds — three large static glowing octahedra ─────────────
        // fp transform: translate(ROOM_R-0.22, FLOOR_Y+1.05, 0) then rotY(PI/2).
        // rotY(PI/2): localX→worldZ, localZ→worldX (negated), localY→worldY.
        // Diamond local pos (fireX[fi], -0.10, -0.05):
        // worldX = (ROOM_R-0.22) - (-0.05) = ROOM_R - 0.17
        // worldY = (FLOOR_Y+1.05) + (-0.10) = FLOOR_Y + 0.95
        // worldZ = fireX[fi]
        float[] fireX = { -0.38f, 0.00f, 0.38f };
        Color3f[] fireCols = {
                new Color3f(0.95f, 0.08f, 0.02f), // red
                new Color3f(1.00f, 0.45f, 0.03f), // orange
                new Color3f(1.00f, 0.82f, 0.10f), // yellow
        };
        for (int fi = 0; fi < 3; fi++) {
            Shape3D fireGem = new Shape3D(octahedron(0.32f), fireGlowMat(fireCols[fi]));
            Transform3D fScale = new Transform3D();
            fScale.setScale(new Vector3d(1.0, 2.8, 1.0));
            TransformGroup fScaleTG = new TransformGroup(fScale);
            fScaleTG.addChild(fireGem);

            Transform3D fPos = new Transform3D();
            fPos.setTranslation(new Vector3f(fireX[fi], -0.45f, -0.05f));
            fireDiamondTGs[fi] = new TransformGroup(fPos);
            fireDiamondTGs[fi].setCapability(TransformGroup.ALLOW_TRANSFORM_WRITE);
            fireDiamondTGs[fi].addChild(fScaleTG);
            fp.addChild(fireDiamondTGs[fi]);
        }

        // fp origin is at world Y = FLOOR_Y + 1.05.
        // Mantle top is at local Y = 0.95 + 0.22 = 1.17.
        // Ceiling in local Y = CEIL_Y - (FLOOR_Y + 1.05) = 3.0 - (-1.95) = 4.95.
        // We want the chimney bottom flush with the mantle top (local Y 1.17)
        // and the top to poke past the ceiling (local Y ~5.1).
        // Half-height = (5.1 - 1.17) / 2 = 1.965 ≈ 2.0; centre = 1.17 + 2.0 = 3.17.
        fp.addChild(translated(0f, 3.17f, -0.04f,
                new com.sun.j3d.utils.geometry.Box(0.45f, 2.00f, 0.38f, flags, chimneyApp)));

        // Chimney cap sits just above the ceiling
        fp.addChild(translated(0f, 5.30f, -0.04f,
                new com.sun.j3d.utils.geometry.Box(0.60f, 0.18f, 0.50f, flags, chimneyApp)));

        group.addChild(fp);

        return group;
    }

    /**
     * Fully opaque emissive material for fire cones — same approach as gemMat().
     * The strong emissiveColor guarantees the cone always glows regardless of
     * lighting, depth sort, or transparency issues.
     */
    private Appearance fireGlowMat(Color3f color) {
        Appearance app = new Appearance();
        Material m = new Material();
        // Full-strength emissive — same principle as gemMat().
        m.setEmissiveColor(color);
        m.setAmbientColor(color);
        m.setDiffuseColor(color);
        m.setSpecularColor(new Color3f(1.0f, 0.9f, 0.4f));
        m.setShininess(96f);
        m.setLightingEnable(true);
        app.setMaterial(m);
        PolygonAttributes pa = new PolygonAttributes();
        pa.setCullFace(PolygonAttributes.CULL_NONE);
        app.setPolygonAttributes(pa);
        return app;
    }

    // =========================================================================
    // FIREPLACE SIGN
    // =========================================================================
    private TransformGroup buildFireSign() {
        TransformGroup group = new TransformGroup();

        // Sign board — pulled 1.2 units from the wall into the room so it clears the
        // pillar.
        float signX = ROOM_R - 1.4f;
        float signY = FLOOR_Y + 2.85f;
        float signZ = 0f;

        Appearance boardApp = matEmissive(c(0.25f, 0.15f, 0.07f), c(0.55f, 0.35f, 0.15f), c(0.06f, 0.03f, 0.01f));
        Appearance textApp = matEmissive(c(0.85f, 0.78f, 0.55f), c(1.0f, 0.92f, 0.70f), c(0.30f, 0.25f, 0.10f));

        int flags = com.sun.j3d.utils.geometry.Primitive.GENERATE_NORMALS;

        // Compose: first rotate around Y so the sign faces into the room (-X
        // direction),
        // then translate to world position. Use mul() so both are applied.
        Transform3D boardRot = new Transform3D();
        boardRot.rotY(Math.PI / 2.0);
        Transform3D boardTrans = new Transform3D();
        boardTrans.setTranslation(new Vector3f(signX, signY, signZ));
        boardTrans.mul(boardRot);
        TransformGroup boardTG = new TransformGroup(boardTrans);
        boardTG.addChild(new com.sun.j3d.utils.geometry.Box(0.85f, 0.28f, 0.06f, flags, boardApp));

        TransformGroup textTG = new TransformGroup();
        Transform3D textPos = new Transform3D();
        textPos.setTranslation(new Vector3f(0f, 0f, 0.065f));
        textTG.setTransform(textPos);
        textTG.addChild(new com.sun.j3d.utils.geometry.Box(0.75f, 0.18f, 0.015f, flags, textApp));
        boardTG.addChild(textTG);

        group.addChild(boardTG);
        return group;
    }

    // =========================================================================
    // LIGHT SWITCH
    // =========================================================================
    private TransformGroup buildLightSwitch() {
        TransformGroup group = new TransformGroup();

        // Switch plate — to the right of the fireplace opening (+Z side in world).
        // World position: X ≈ ROOM_R-0.32 (just inside wall), Y = eye height, Z = +1.5
        float swX = ROOM_R - 0.28f;
        float swY = FLOOR_Y + 2.1f;
        float swZ = 1.6f;

        Appearance plateApp = matEmissive(c(0.70f, 0.70f, 0.70f), c(0.95f, 0.95f, 0.95f), c(0.15f, 0.15f, 0.15f));
        Appearance leverApp = matEmissive(c(0.50f, 0.50f, 0.50f), c(0.80f, 0.80f, 0.80f), c(0.10f, 0.10f, 0.10f));
        int flags = com.sun.j3d.utils.geometry.Primitive.GENERATE_NORMALS;

        // Switch plate — thin box face-on to the room (rotated to face -X, i.e. into
        // room)
        Transform3D plateT = new Transform3D();
        plateT.rotY(Math.PI / 2.0);
        plateT.setTranslation(new Vector3f(swX, swY, swZ));
        TransformGroup plateTG = new TransformGroup(plateT);
        plateTG.addChild(new com.sun.j3d.utils.geometry.Box(0.18f, 0.30f, 0.04f, flags, plateApp));

        // Lever — small box, half-embedded in plate, tilted 30° (upward = on)
        switchLeverTG = new TransformGroup();
        switchLeverTG.setCapability(TransformGroup.ALLOW_TRANSFORM_WRITE);
        Transform3D leverRot = new Transform3D();
        leverRot.rotX(Math.toRadians(-30)); // tilted up = "on"
        switchLeverTG.setTransform(leverRot);
        switchLeverTG.addChild(new com.sun.j3d.utils.geometry.Box(0.06f, 0.14f, 0.07f, flags, leverApp));
        plateTG.addChild(switchLeverTG);

        group.addChild(plateTG);
        return group;
    }

    private TransformGroup buildFireplaceKey() {
        Appearance keyApp = matEmissive(c(0.45f, 0.30f, 0.04f), c(1.0f, 0.78f, 0.18f), c(0.45f, 0.28f, 0.02f));
        Shape3D keyShape = new Shape3D(octahedron(0.22f), keyApp);

        TransformGroup spinTG = new TransformGroup();
        spinTG.setCapability(TransformGroup.ALLOW_TRANSFORM_WRITE);
        RotationInterpolator spin = new RotationInterpolator(new Alpha(-1, 2200), spinTG,
                new Transform3D(), 0f, (float) (2 * Math.PI));
        spin.setSchedulingBounds(wb());
        spinTG.addChild(keyShape);
        spinTG.addChild(spin);

        // Start hidden below the floor
        Transform3D pos = new Transform3D();
        pos.setTranslation(new Vector3f(FIRE_KEY_POS.x, FLOOR_Y - 1.5f, FIRE_KEY_POS.z));
        fireplaceKeyTG = new TransformGroup(pos);
        fireplaceKeyTG.setCapability(TransformGroup.ALLOW_TRANSFORM_WRITE);
        fireplaceKeyTG.addChild(spinTG);
        return fireplaceKeyTG;
    }

    private void toggleFire() {
        fireOn = !fireOn;

        float[] fireX = { -0.38f, 0.00f, 0.38f };
        for (int fi = 0; fi < 3; fi++) {
            Transform3D t = new Transform3D();
            if (fireOn) {
                t.setTranslation(new Vector3f(fireX[fi], -0.45f, -0.05f));
            } else {
                t.setTranslation(new Vector3f(fireX[fi], -0.45f, 5.0f));
            }
            fireDiamondTGs[fi].setTransform(t);
            fireDiamondLights[fi].setEnable(fireOn);
        }

        // Tilt lever
        Transform3D leverRot = new Transform3D();
        leverRot.rotX(Math.toRadians(fireOn ? -30 : 30));
        switchLeverTG.setTransform(leverRot);

        // Animate the fireplace key rising from underground when fire is turned on
        if (fireOn) {
            new Thread(() -> {
                float startY = FLOOR_Y - 1.5f;
                float endY = FIRE_KEY_POS.y;
                long duration = 1200;
                long start = System.currentTimeMillis();
                while (true) {
                    long elapsed = System.currentTimeMillis() - start;
                    float t = Math.min(elapsed / (float) duration, 1.0f);
                    float ease = 1f - (1f - t) * (1f - t);
                    float curY = startY + (endY - startY) * ease;
                    Transform3D pos = new Transform3D();
                    pos.setTranslation(new Vector3f(FIRE_KEY_POS.x, curY, FIRE_KEY_POS.z));
                    fireplaceKeyTG.setTransform(pos);
                    if (t >= 1.0f)
                        break;
                    try {
                        Thread.sleep(16);
                    } catch (InterruptedException ignored) {
                    }
                }
            }, "key-rise").start();
        } else {
            // Hide key back below floor
            Transform3D pos = new Transform3D();
            pos.setTranslation(new Vector3f(FIRE_KEY_POS.x, FLOOR_Y - 1.5f, FIRE_KEY_POS.z));
            fireplaceKeyTG.setTransform(pos);
        }
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
        // NOTE: glow PointLight is NOT added here — it is added directly to the
        // scene root in buildScene() with the correct world-space position.
        // Placing lights inside a scaled TransformGroup corrupts their position.
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
    // GAMEPLAY OBJECTS
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
        Appearance doorApp = matEmissive(c(0.22f, 0.13f, 0.06f), c(0.45f, 0.26f, 0.12f), c(0.06f, 0.03f, 0.01f));

        float doorHalfW = ARCH_W / 2f;
        float doorHalfH = ARCH_TOTAL / 2f;
        float doorCentreY = FLOOR_Y + ARCH_TOTAL / 2f;
        float doorZ = NORTH_Z - 0.12f;

        doorTG = new TransformGroup();
        doorTG.setCapability(TransformGroup.ALLOW_TRANSFORM_WRITE);
        Transform3D doorPos = new Transform3D();
        doorPos.setTranslation(new Vector3f(0f, doorCentreY, doorZ));
        doorTG.setTransform(doorPos);
        doorTG.addChild(new com.sun.j3d.utils.geometry.Box(
                doorHalfW - 0.05f, doorHalfH, 0.10f,
                com.sun.j3d.utils.geometry.Primitive.GENERATE_NORMALS, doorApp));
        group.addChild(doorTG);

        Appearance lightOff = lightAppearance(false);
        float[] xs = { -0.9f, 0f, 0.9f };
        float lightY = FLOOR_Y + ARCH_TOTAL + ARCH_TRIM + 0.35f;
        float lightZ = NORTH_Z + 0.5f;
        for (int i = 0; i < 3; i++) {
            doorLightShapes[i] = new Shape3D(octahedron(0.18f), lightOff);
            doorLightShapes[i].setCapability(Shape3D.ALLOW_APPEARANCE_WRITE);
            group.addChild(translated(xs[i], lightY, lightZ, doorLightShapes[i]));
            doorLightNodes[i] = new PointLight(YELLOW, new Point3f(xs[i], lightY, lightZ),
                    new Point3f(0.10f, 0.06f, 0.01f));
            doorLightNodes[i].setCapability(PointLight.ALLOW_STATE_WRITE);
            doorLightNodes[i].setEnable(false);
            doorLightNodes[i].setInfluencingBounds(wb());
            group.addChild(doorLightNodes[i]);
        }
        return group;
    }

    private Appearance lightAppearance(boolean on) {
        return on ? matEmissive(c(1.0f, 0.75f, 0.10f), c(1.0f, 0.85f, 0.20f), c(1.0f, 0.60f, 0.04f))
                : matEmissive(c(0.08f, 0.07f, 0.05f), c(0.18f, 0.16f, 0.12f), c(0.02f, 0.018f, 0.012f));
    }

    private void collectKey(int index) {
        if (keyTaken[index])
            return;
        keyTaken[index] = true;
        collectedKeys++;
        Transform3D hide = new Transform3D();
        hide.setScale(0.001);
        hide.setTranslation(new Vector3f(KEY_POSITIONS[index].x, -100f, KEY_POSITIONS[index].z));
        keyTGs[index].setTransform(hide);
        int li = collectedKeys - 1;
        doorLightShapes[li].setAppearance(lightAppearance(true));
        doorLightNodes[li].setEnable(true);
        if (collectedKeys == 3)
            openDoor();
    }

    private void openDoor() {
        float doorCentreY = FLOOR_Y + ARCH_TOTAL / 2f;
        float doorZ = NORTH_Z - 0.12f;
        float startY = doorCentreY;
        float endY = FLOOR_Y + ARCH_TOTAL + ARCH_TOTAL;
        new Thread(() -> {
            long durationMs = 1800, startTime = System.currentTimeMillis();
            while (true) {
                long elapsed = System.currentTimeMillis() - startTime;
                float t = Math.min(elapsed / (float) durationMs, 1.0f);
                float ease = 1f - (1f - t) * (1f - t);
                float currentY = startY + (endY - startY) * ease;
                Transform3D tx = new Transform3D();
                tx.setTranslation(new Vector3f(0f, currentY, doorZ));
                doorTG.setTransform(tx);
                if (t >= 1.0f)
                    break;
                try {
                    Thread.sleep(16);
                } catch (InterruptedException ignored) {
                }
            }
        }, "door-open").start();
    }

    private boolean isWalkable(float x, float z) {
        if ((x * x + z * z) <= (ROOM_R - PLAYER_RADIUS) * (ROOM_R - PLAYER_RADIUS))
            return true;
        float hw = ARCH_W / 2f - PLAYER_RADIUS;
        if (Math.abs(x) <= hw && z <= NORTH_Z + 0.25f && z >= HALL_END_Z + PLAYER_RADIUS)
            return true;
        if (Math.abs(x) <= hw && z >= SOUTH_Z - 0.25f && z <= SOUTH_END_Z - PLAYER_RADIUS)
            return true;
        return inRotatedNorthHall(x, z, SE_ANGLE) || inRotatedNorthHall(x, z, SW_ANGLE);
    }

    private boolean inRotatedNorthHall(float x, float z, double wallAngle) {
        double inv = -(wallAngle - (3.0 * Math.PI / 2.0));
        float lx = (float) (Math.cos(inv) * x + Math.sin(inv) * z);
        float lz = (float) (-Math.sin(inv) * x + Math.cos(inv) * z);
        float hw = ARCH_W / 2f - PLAYER_RADIUS;
        return Math.abs(lx) <= hw && lz <= NORTH_Z + 0.25f && lz >= HALL_END_Z + PLAYER_RADIUS;
    }

    // =========================================================================
    // FIRE FLICKER BEHAVIOR
    // =========================================================================
    private class FireFlickerBehavior extends Behavior {
        private final PointLight mainLight, upperGlow;
        private final WakeupOnElapsedFrames wakeup = new WakeupOnElapsedFrames(0);

        FireFlickerBehavior(PointLight m, PointLight u) {
            mainLight = m;
            upperGlow = u;
        }

        public void initialize() {
            wakeupOn(wakeup);
        }

        public void processStimulus(java.util.Enumeration criteria) {
            float flicker = 0.85f + (float) Math.random() * 0.25f;
            float r = 0.85f + (float) Math.random() * 0.15f, g = 0.32f + (float) Math.random() * 0.35f,
                    b = 0.03f + (float) Math.random() * 0.05f;
            mainLight.setColor(new Color3f(r, g, b));
            upperGlow.setColor(new Color3f(0.85f * flicker, 0.42f * flicker, 0.08f * flicker));
            mainLight.setAttenuation(new Point3f(
                    0.008f + (float) Math.random() * 0.006f,
                    0.015f + (float) Math.random() * 0.010f,
                    0.001f + (float) Math.random() * 0.002f));
            upperGlow.setAttenuation(new Point3f(
                    0.008f + (float) Math.random() * 0.006f,
                    0.012f + (float) Math.random() * 0.006f,
                    0.001f + (float) Math.random() * 0.002f));
            wakeupOn(wakeup);
        }
    }

    // =========================================================================
    // FIRST-PERSON CONTROLLER
    // =========================================================================
    private class FirstPersonController extends Behavior
            implements KeyListener, MouseMotionListener, MouseListener {
        private final TransformGroup viewTG;
        private final Canvas3D canvas;
        private final WakeupOnElapsedFrames wakeup = new WakeupOnElapsedFrames(0);
        private final boolean[] keys = new boolean[256];
        private float x = 0f, z = 10f, yaw = 0f, pitch = 0f;
        private Robot robot;
        private boolean robotReady = false, firstWarp = true;

        FirstPersonController(TransformGroup viewTG, Canvas3D canvas) {
            this.viewTG = viewTG;
            this.canvas = canvas;
            BufferedImage blank = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
            canvas.setCursor(Toolkit.getDefaultToolkit().createCustomCursor(blank, new Point(0, 0), "invisible"));
            try {
                robot = new Robot();
                robotReady = true;
            } catch (AWTException e) {
                System.err.println("Robot unavailable: " + e.getMessage());
            }
        }

        public void initialize() {
            wakeupOn(wakeup);
        }

        public void processStimulus(java.util.Enumeration criteria) {
            updateMovement();
            checkKeyPickups();
            if (robotReady && canvas.isShowing()) {
                Point loc = canvas.getLocationOnScreen();
                robot.mouseMove(loc.x + canvas.getWidth() / 2, loc.y + canvas.getHeight() / 2);
                firstWarp = false;
            }
            wakeupOn(wakeup);
        }

        private void updateMovement() {
            float forward = 0f, strafe = 0f;
            if (down(KeyEvent.VK_W))
                forward += 1f;
            if (down(KeyEvent.VK_S))
                forward -= 1f;
            if (down(KeyEvent.VK_A))
                strafe -= 1f;
            if (down(KeyEvent.VK_D))
                strafe += 1f;
            if (down(KeyEvent.VK_LEFT))
                yaw += 0.045f;
            if (down(KeyEvent.VK_RIGHT))
                yaw -= 0.045f;
            if (down(KeyEvent.VK_UP))
                pitch += 0.035f;
            if (down(KeyEvent.VK_DOWN))
                pitch -= 0.035f;
            clampPitch();
            if (forward != 0f || strafe != 0f) {
                float len = (float) Math.sqrt(forward * forward + strafe * strafe);
                forward /= len;
                strafe /= len;
                float sin = (float) Math.sin(yaw), cos = (float) Math.cos(yaw);
                float dx = (-sin * forward + cos * strafe) * WALK_SPEED,
                        dz = (-cos * forward - sin * strafe) * WALK_SPEED;
                if (isWalkable(x + dx, z))
                    x += dx;
                if (isWalkable(x, z + dz))
                    z += dz;
            }
            Transform3D yawT = new Transform3D();
            yawT.rotY(yaw);
            Transform3D pitchT = new Transform3D();
            pitchT.rotX(pitch);
            yawT.mul(pitchT);
            yawT.setTranslation(new Vector3f(x, PLAYER_EYE_Y, z));
            viewTG.setTransform(yawT);
        }

        private void clampPitch() {
            float l = (float) Math.toRadians(80);
            if (pitch > l)
                pitch = l;
            if (pitch < -l)
                pitch = -l;
        }

        private void checkKeyPickups() {
            for (int i = 0; i < KEY_POSITIONS.length; i++) {
                if (keyTaken[i])
                    continue;
                float dx = x - KEY_POSITIONS[i].x, dz = z - KEY_POSITIONS[i].z;
                if (dx * dx + dz * dz < 0.85f * 0.85f)
                    collectKey(i);
            }
            // Fireplace key — only collectable once fire is on and it has risen above floor
            if (!fireplaceKeyTaken && fireOn) {
                float dx = x - FIRE_KEY_POS.x, dz = z - FIRE_KEY_POS.z;
                if (dx * dx + dz * dz < 0.85f * 0.85f) {
                    fireplaceKeyTaken = true;
                    Transform3D hide = new Transform3D();
                    hide.setScale(0.001);
                    hide.setTranslation(new Vector3f(FIRE_KEY_POS.x, -100f, FIRE_KEY_POS.z));
                    fireplaceKeyTG.setTransform(hide);
                    // Find first uncollected regular key slot and collect it
                    for (int i = 0; i < KEY_POSITIONS.length; i++) {
                        if (!keyTaken[i]) {
                            collectKey(i);
                            break;
                        }
                    }
                }
            }
        }

        private boolean down(int code) {
            return code >= 0 && code < keys.length && keys[code];
        }

        public void keyPressed(KeyEvent e) {
            if (e.getKeyCode() < keys.length)
                keys[e.getKeyCode()] = true;
        }

        public void keyReleased(KeyEvent e) {
            if (e.getKeyCode() < keys.length)
                keys[e.getKeyCode()] = false;
        }

        public void keyTyped(KeyEvent e) {
        }

        public void mouseDragged(MouseEvent e) {
            mouseMoved(e);
        }

        public void mouseMoved(MouseEvent e) {
            if (!canvas.hasFocus())
                canvas.requestFocusInWindow();
            if (!robotReady || firstWarp || !canvas.isShowing())
                return;
            int dx = e.getX() - canvas.getWidth() / 2, dy = e.getY() - canvas.getHeight() / 2;
            if (dx == 0 && dy == 0)
                return;
            yaw -= dx * MOUSE_SENS;
            pitch -= dy * MOUSE_SENS;
            clampPitch();
        }

        // ── MouseListener ────────────────────────────────────────────────────
        public void mouseClicked(MouseEvent e) {
            if (e.getButton() != MouseEvent.BUTTON1)
                return;
            // Check if player is close enough to the switch and roughly facing it.
            // Switch world pos ≈ (ROOM_R-0.28, FLOOR_Y+2.1, 1.6)
            float swX = ROOM_R - 0.28f, swY = FLOOR_Y + 2.1f, swZ = 1.6f;
            float dx = x - swX, dz = z - swZ;
            float dist = (float) Math.sqrt(dx * dx + dz * dz);
            if (dist < 3.5f) {
                toggleFire();
            }
        }

        public void mousePressed(MouseEvent e) {
        }

        public void mouseReleased(MouseEvent e) {
        }

        public void mouseEntered(MouseEvent e) {
        }

        public void mouseExited(MouseEvent e) {
        }
    }

    // =========================================================================
    // GEOMETRY UTILITIES
    // =========================================================================
    private void addTriN(ArrayList<float[]> vv, ArrayList<float[]> nn, float nx, float ny, float nz, float[] a,
            float[] b, float[] c) {
        float[] n = { nx, ny, nz };
        vv.add(a);
        vv.add(b);
        vv.add(c);
        nn.add(n);
        nn.add(n);
        nn.add(n);
    }

    private void addQuadN(ArrayList<float[]> vv, ArrayList<float[]> nn, float nx, float ny, float nz, float[] bl,
            float[] br, float[] tr, float[] tl) {
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

    private GeometryArray quadGeo(float[] bl, float[] br, float[] tr, float[] tl, float[] n) {
        ArrayList<float[]> vv = new ArrayList<>(), nn = new ArrayList<>();
        addQuadN(vv, nn, n[0], n[1], n[2], bl, br, tr, tl);
        return listToGeo(vv, nn);
    }

    private TransformGroup placedDisc(float r, int seg, float normalY, float y, Appearance app) {
        int vCount = seg + 2;
        TriangleFanArray geo = new TriangleFanArray(vCount, GeometryArray.COORDINATES | GeometryArray.NORMALS,
                new int[] { vCount });
        float[] co = new float[vCount * 3], no = new float[vCount * 3];
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
        float[][] v = { { s, 0, 0 }, { 0, s, 0 }, { 0, 0, s }, { 0, 0, s }, { 0, s, 0 }, { -s, 0, 0 }, { -s, 0, 0 },
                { 0, s, 0 }, { 0, 0, -s }, { 0, 0, -s }, { 0, s, 0 }, { s, 0, 0 },
                { s, 0, 0 }, { 0, 0, s }, { 0, -s, 0 }, { 0, 0, s }, { -s, 0, 0 }, { 0, -s, 0 }, { -s, 0, 0 },
                { 0, 0, -s }, { 0, -s, 0 }, { 0, 0, -s }, { s, 0, 0 }, { 0, -s, 0 } };
        TriangleArray geo = new TriangleArray(v.length, GeometryArray.COORDINATES | GeometryArray.NORMALS);
        float[] co = new float[v.length * 3], no = new float[v.length * 3];
        for (int i = 0; i < v.length; i += 3) {
            Vector3f a = vf(v[i]), ab = new Vector3f(), ac = new Vector3f(), n = new Vector3f();
            ab.sub(vf(v[i + 1]), a);
            ac.sub(vf(v[i + 2]), a);
            n.cross(ab, ac);
            n.normalize();
            for (int j = 0; j < 3; j++) {
                int k = (i + j) * 3;
                co[k] = v[i + j][0];
                co[k + 1] = v[i + j][1];
                co[k + 2] = v[i + j][2];
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

    private Appearance textureAppearance(Color base, Color line, int size, boolean woodGrain) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(base);
        g.fillRect(0, 0, size, size);
        g.setColor(line);
        if (woodGrain) {
            for (int y = 0; y < size; y += 8) {
                int w = (int) (Math.sin(y * 0.25) * 6);
                g.drawLine(0, y, size, Math.max(0, Math.min(size - 1, y + w)));
            }
            g.setColor(base.darker());
            for (int x = 0; x < size; x += 32)
                g.drawLine(x, 0, x, size);
        } else {
            int bh = 16, bw = 32;
            for (int y = 0; y < size; y += bh) {
                g.drawLine(0, y, size, y);
                int off = ((y / bh) % 2) * (bw / 2);
                for (int x = -off; x < size; x += bw)
                    g.drawLine(x, y, x, y + bh);
            }
        }
        g.dispose();
        Texture2D tex = new Texture2D(Texture.BASE_LEVEL, Texture.RGB, size, size);
        tex.setImage(0, new ImageComponent2D(ImageComponent2D.FORMAT_RGB, img));
        tex.setEnable(true);
        Appearance app = matEmissive(
                c(base.getRed() / 255f * 0.35f, base.getGreen() / 255f * 0.35f, base.getBlue() / 255f * 0.35f),
                c(base.getRed() / 255f, base.getGreen() / 255f, base.getBlue() / 255f), c(0.03f, 0.02f, 0.015f));
        app.setTexture(tex);
        TextureAttributes ta = new TextureAttributes();
        ta.setTextureMode(TextureAttributes.MODULATE);
        app.setTextureAttributes(ta);
        TexCoordGeneration tcg = new TexCoordGeneration(TexCoordGeneration.OBJECT_LINEAR,
                TexCoordGeneration.TEXTURE_COORDINATE_2);
        app.setTexCoordGeneration(tcg);
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
