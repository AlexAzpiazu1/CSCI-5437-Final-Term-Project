import com.sun.j3d.utils.universe.SimpleUniverse;
import com.sun.j3d.utils.geometry.Cylinder;
import com.sun.j3d.utils.picking.PickCanvas;
import com.sun.j3d.utils.picking.PickResult;
import com.sun.j3d.utils.picking.PickTool;

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
    // Legs touch the floor: bottom of leg = FLOOR_Y, so leg centre = FLOOR_Y +
    // LEG_H/2
    // Table top centre = leg centre + LEG_H/2 + TABLE_H/2
    private static final float TABLE_Y = FLOOR_Y + LEG_H + TABLE_H / 2f; // -1.81
    private static final float LEG_Y = FLOOR_Y + LEG_H / 2f; // -2.45

    // ── gem ───────────────────────────────────────────────────────────────────
    private static final float GEM_Y = TABLE_Y + TABLE_H / 2f + 1.8f; // raised above table
    private static final float GEM_SIZE = 0.22f;
    private static final float GEM_SCALE_Y = 2.8f;

    // ── archway opening (upside-down U on the north wall) ─────────────────────
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
    private static final float WALK_SPEED = 5.5f; // units per second
    private static final float TURN_SPEED = 2.8f; // radians per second
    private static final float MOUSE_SENS = 0.0022f;
    // Horizontal FOV in degrees — wider feels less zoomed-in
    private static final double FOV_DEG = 90.0;

    private final TransformGroup[] keyTGs = new TransformGroup[3];
    private final boolean[] keyTaken = new boolean[3];
    private final boolean[] keySpawned = new boolean[3];
    private final boolean[] puzzleSolved = new boolean[3];

    private static final String PUZZLE1_CORRECT = "PUZZLE1_CORRECT";
    private static final String PUZZLE1_WRONG = "PUZZLE1_WRONG";

    // The puzzle paper is stored so we can redraw it with a check/x mark when
    // answered.
    private Shape3D puzzle1PaperShape;
    // 0-3 = cursor position on the board (cycles with SHIFT when near board)
    private int puzzle1CursorPos = 0;
    // -1 = no wrong guess yet, 0/2/3 = index of last wrong guess (shown with ✗)
    private int puzzle1LastWrong = -1;
    // Board world position — used for proximity check
    private static final float BOARD_Z = (float) (Math.sqrt(14.0 * 14.0 - (5.5f / 2f) * (5.5f / 2f)) - 0.1f - 0.18f);
    private static final float BOARD_INTERACT_DIST = 5.5f;

    // ── puzzle 2 : stray chair ─────────────────────────────────────────────────
    // The stray chair sits pulled OUT from the table; all others are tucked under.
    // Player must look at the stray chair and press E (or click) to push it under.
    private static final String PUZZLE2_CHAIR = "PUZZLE2_CHAIR";
    private TransformGroup strayChairTG; // animated when solved
    private Shape3D strayChairHitbox; // invisible pickable hitbox
    // World position of the stray chair (pulled out from seat i=2, angle≈2π/6*2)
    private static final double STRAY_ANGLE = Math.PI * 2.0 / 6.0 * 2.0; // 120°
    private static final float STRAY_CHAIR_DIST = TABLE_R + 2.35f; // pulled out extra
    private static final float STRAY_CHAIR_X = (float) (Math.cos(STRAY_ANGLE) * STRAY_CHAIR_DIST);
    private static final float STRAY_CHAIR_Z = (float) (Math.sin(STRAY_ANGLE) * STRAY_CHAIR_DIST);
    // Tucked-under destination (same angle, close to table edge)
    private static final float TUCKED_DIST = TABLE_R - 0.30f;
    private static final float TUCKED_X = (float) (Math.cos(STRAY_ANGLE) * TUCKED_DIST);
    private static final float TUCKED_Z = (float) (Math.sin(STRAY_ANGLE) * TUCKED_DIST);
    private final Shape3D[] doorLightShapes = new Shape3D[3];
    private final PointLight[] doorLightNodes = new PointLight[3];
    private TransformGroup doorTG;
    private int collectedKeys = 0;

    // ── fireplace switch / key ────────────────────────────────────────────────
    private final TransformGroup[] fireDiamondTGs = new TransformGroup[3];
    private final PointLight[] fireDiamondLights = new PointLight[3];
    private boolean fireOn = false; // starts OFF — click switch to reveal the key
    private TransformGroup switchLeverTG;
    private TransformGroup fireplaceKeyTG; // rises from floor when fire is switched on
    private boolean fireplaceKeyTaken = false;
    private static final Point3f FIRE_KEY_POS = new Point3f(ROOM_R - 2.5f, FLOOR_Y + 0.45f, 0.0f);

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
        installCenterCursor();

        SimpleUniverse universe = new SimpleUniverse(canvas);
        universe.getViewingPlatform().setNominalViewingTransform();

        // Widen the field of view so the scene feels less zoomed-in
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

    private void installCenterCursor() {
        CrosshairGlassPane crosshair = new CrosshairGlassPane();
        setGlassPane(crosshair);
        crosshair.setVisible(true);
    }

    private static class CrosshairGlassPane extends JComponent {
        CrosshairGlassPane() {
            setOpaque(false);
        }

        protected void paintComponent(Graphics g) {
            super.paintComponent(g);

            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int cx = getWidth() / 2;
            int cy = getHeight() / 2;
            int len = 10;
            int gap = 4;

            g2.setStroke(new BasicStroke(3f));
            g2.setColor(new Color(0, 0, 0, 170));
            g2.drawLine(cx - len - 1, cy, cx - gap - 1, cy);
            g2.drawLine(cx + gap + 1, cy, cx + len + 1, cy);
            g2.drawLine(cx, cy - len - 1, cx, cy - gap - 1);
            g2.drawLine(cx, cy + gap + 1, cx, cy + len + 1);

            g2.setStroke(new BasicStroke(2f));
            g2.setColor(new Color(255, 245, 210, 230));
            g2.drawLine(cx - len, cy, cx - gap, cy);
            g2.drawLine(cx + gap, cy, cx + len, cy);
            g2.drawLine(cx, cy - len, cx, cy - gap);
            g2.drawLine(cx, cy + gap, cx, cy + len);

            g2.dispose();
        }

        public boolean contains(int x, int y) {
            return false;
        }
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
        root.addChild(buildStrayChairHitbox());
        root.addChild(buildFireplace());
        root.addChild(buildFireSign());
        root.addChild(buildLightSwitch());
        root.addChild(buildGem());
        root.addChild(buildPuzzleBoards());
        root.addChild(buildKeys());
        root.addChild(buildFireplaceKey());
        root.addChild(buildExitDoor());

        FirstPersonController controller = new FirstPersonController(vpTG, canvas, root);
        controller.setSchedulingBounds(wb());
        canvas.addKeyListener(controller);
        canvas.addMouseMotionListener(controller);
        canvas.addMouseListener(controller); // tracks mouseButtonHeld for robot suppression
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
            fl.setEnable(false); // fire starts off
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

    /** Shared fog-plane factory — works for any z0→z1 hallway span. */
    private TransformGroup makeFogPlanes(float z0, float z1) {
        TransformGroup tg = new TransformGroup();
        int FOG_COUNT = 8;
        float planeW = ARCH_W * 2.5f;
        float planeH = ARCH_TOTAL * 2f;
        float flY = FLOOR_Y;

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

        Cylinder top = new Cylinder(TABLE_R, TABLE_H, flags, 64, 1, wood);
        tg.addChild(translated(0, TABLE_Y, 0, top));

        float ld = TABLE_R * 0.6f;
        for (float[] o : new float[][] { { ld, ld }, { -ld, ld }, { -ld, -ld }, { ld, -ld } }) {
            tg.addChild(translated(o[0], LEG_Y, o[1],
                    new Cylinder(LEG_R, LEG_H, flags, 16, 1, wood)));
        }
        return tg;
    }

    // =========================================================================
    // CHAIRS
    // =========================================================================
    private TransformGroup buildChairs() {
        TransformGroup group = new TransformGroup();
        Appearance chairWood = textureAppearance(new Color(75, 38, 18), new Color(125, 70, 32), 96, true);

        // Tucked-under distance — seat is slid beneath the table top
        float tuckedDist = TABLE_R - 0.30f;

        for (int i = 0; i < 6; i++) {
            double angle = i * Math.PI * 2.0 / 6.0;

            // Chair 2 (i==2) is the STRAY chair — built separately below
            if (i == 2)
                continue;

            float x = (float) Math.cos(angle) * tuckedDist;
            float z = (float) Math.sin(angle) * tuckedDist;

            Transform3D pos = new Transform3D();
            pos.setTranslation(new Vector3f(x, FLOOR_Y + 0.55f, z));
            Transform3D rot = new Transform3D();
            rot.rotY(-angle + Math.PI / 2.0);
            pos.mul(rot);

            TransformGroup chairTG = new TransformGroup(pos);
            chairTG.addChild(makeChair(chairWood));
            group.addChild(chairTG);
        }

        // ── stray chair (puzzle 2) ────────────────────────────────────────────
        // Sits pulled out from the table, clearly out of place
        Transform3D strayPos = new Transform3D();
        strayPos.setTranslation(new Vector3f(STRAY_CHAIR_X, FLOOR_Y + 0.55f, STRAY_CHAIR_Z));
        Transform3D strayRot = new Transform3D();
        strayRot.rotY(-(float) STRAY_ANGLE + (float) (Math.PI / 2.0));
        strayPos.mul(strayRot);

        strayChairTG = new TransformGroup(strayPos);
        strayChairTG.setCapability(TransformGroup.ALLOW_TRANSFORM_WRITE);
        strayChairTG.addChild(makeChair(chairWood));
        group.addChild(strayChairTG);

        return group;
    }

    private TransformGroup makeChair(Appearance app) {
        TransformGroup chair = new TransformGroup();
        int flags = com.sun.j3d.utils.geometry.Primitive.GENERATE_NORMALS
                | com.sun.j3d.utils.geometry.Primitive.GENERATE_TEXTURE_COORDS;

        chair.addChild(translated(0f, 0.25f, 0f,
                new com.sun.j3d.utils.geometry.Box(0.45f, 0.08f, 0.45f, flags, app)));

        chair.addChild(translated(0f, 0.78f, 0.38f,
                new com.sun.j3d.utils.geometry.Box(0.48f, 0.55f, 0.08f, flags, app)));

        float lx = 0.32f, lz = 0.32f;
        int cylFlags = Cylinder.GENERATE_NORMALS | Cylinder.GENERATE_TEXTURE_COORDS;
        for (float[] pt : new float[][] { { lx, lz }, { -lx, lz }, { lx, -lz }, { -lx, -lz } }) {
            chair.addChild(translated(pt[0], -0.23f, pt[1],
                    new Cylinder(0.045f, 0.85f, cylFlags, 12, 1, app)));
        }

        return chair;
    }

    /**
     * Invisible pickable box that surrounds the stray chair so the player can
     * interact with it.
     */
    private TransformGroup buildStrayChairHitbox() {
        TransformGroup group = new TransformGroup();

        // A tall-ish box centred on the stray chair's world position
        float hx = 0.60f, hy = 0.80f, hz = 0.60f;
        float cx = STRAY_CHAIR_X;
        float cy = FLOOR_Y + 0.55f + hy / 2f;
        float cz = STRAY_CHAIR_Z;

        // Six-sided invisible hitbox (top face is the most reliably picked by
        // crosshair)
        // Top face — stored as the primary hitbox reference
        strayChairHitbox = makeHitboxFace(
                p(cx - hx, cy + hy, cz - hz), p(cx + hx, cy + hy, cz - hz),
                p(cx + hx, cy + hy, cz + hz), p(cx - hx, cy + hy, cz + hz),
                new float[] { 0, 1, 0 });
        group.addChild(strayChairHitbox);

        // Front face
        group.addChild(makeHitboxFace(
                p(cx - hx, cy - hy, cz + hz), p(cx + hx, cy - hy, cz + hz),
                p(cx + hx, cy + hy, cz + hz), p(cx - hx, cy + hy, cz + hz),
                new float[] { 0, 0, 1 }));

        // Back face
        group.addChild(makeHitboxFace(
                p(cx + hx, cy - hy, cz - hz), p(cx - hx, cy - hy, cz - hz),
                p(cx - hx, cy + hy, cz - hz), p(cx + hx, cy + hy, cz - hz),
                new float[] { 0, 0, -1 }));

        // Left face
        group.addChild(makeHitboxFace(
                p(cx - hx, cy - hy, cz - hz), p(cx - hx, cy - hy, cz + hz),
                p(cx - hx, cy + hy, cz + hz), p(cx - hx, cy + hy, cz - hz),
                new float[] { -1, 0, 0 }));

        // Right face
        group.addChild(makeHitboxFace(
                p(cx + hx, cy - hy, cz + hz), p(cx + hx, cy - hy, cz - hz),
                p(cx + hx, cy + hy, cz - hz), p(cx + hx, cy + hy, cz + hz),
                new float[] { 1, 0, 0 }));

        return group;
    }

    /** Creates one invisible pickable quad face for the stray-chair hitbox. */
    private Shape3D makeHitboxFace(float[] bl, float[] br, float[] tr, float[] tl, float[] n) {
        Shape3D face = new Shape3D(quadGeo(bl, br, tr, tl, n), invisiblePickAppearance());
        face.setUserData(PUZZLE2_CHAIR);
        face.setCapability(Shape3D.ALLOW_APPEARANCE_WRITE);
        face.setCapability(Node.ALLOW_PICKABLE_WRITE); // required to call setPickable() at runtime
        face.setPickable(true);
        return face;
    }

    private TransformGroup buildFireplace() {
        TransformGroup group = new TransformGroup();

        Appearance stone = textureAppearance(new Color(70, 65, 60), new Color(125, 115, 105), 64, false);
        Appearance dark = matEmissive(c(0.02f, 0.015f, 0.01f), c(0.05f, 0.035f, 0.02f), c(0.01f, 0.005f, 0.002f));
        Appearance chimneyApp = textureAppearance(new Color(60, 58, 55), new Color(115, 110, 105), 64, false);
        Appearance logApp = textureAppearance(new Color(78, 42, 19), new Color(135, 82, 36), 64, true);

        int flags = com.sun.j3d.utils.geometry.Primitive.GENERATE_NORMALS
                | com.sun.j3d.utils.geometry.Primitive.GENERATE_TEXTURE_COORDS;
        int cylFlags = Cylinder.GENERATE_NORMALS | Cylinder.GENERATE_TEXTURE_COORDS;

        Transform3D base = new Transform3D();
        base.setTranslation(new Vector3f(ROOM_R - 0.22f, FLOOR_Y + 1.05f, 0f));
        Transform3D rot = new Transform3D();
        rot.rotY(Math.PI / 2.0);
        base.mul(rot);
        TransformGroup fp = new TransformGroup(base);

        // ── back wall of fireplace opening ────────────────────────────────────
        fp.addChild(translated(0f, 0f, -0.06f,
                new com.sun.j3d.utils.geometry.Box(0.85f, 0.75f, 0.08f, flags, dark)));

        // ── side pillars — deeper into the room (Z half-extent 0.65) ──────────
        fp.addChild(translated(-1.05f, 0f, 0f,
                new com.sun.j3d.utils.geometry.Box(0.22f, 0.95f, 0.65f, flags, stone)));
        fp.addChild(translated(1.05f, 0f, 0f,
                new com.sun.j3d.utils.geometry.Box(0.22f, 0.95f, 0.65f, flags, stone)));

        // ── mantle — wider and deeper ──────────────────────────────────────────
        fp.addChild(translated(0f, 0.95f, 0f,
                new com.sun.j3d.utils.geometry.Box(1.40f, 0.22f, 0.68f, flags, stone)));

        // ── hearth — deeper ───────────────────────────────────────────────────
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

        // ── fire diamonds — three glowing octahedra (hidden until switch is on) ─
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

            // Start hidden (pushed behind wall) since fireOn = false initially
            Transform3D fPos = new Transform3D();
            fPos.setTranslation(new Vector3f(fireX[fi], -0.45f, 5.0f));
            fireDiamondTGs[fi] = new TransformGroup(fPos);
            fireDiamondTGs[fi].setCapability(TransformGroup.ALLOW_TRANSFORM_WRITE);
            fireDiamondTGs[fi].addChild(fScaleTG);
            fp.addChild(fireDiamondTGs[fi]);
        }

        // ── chimney ───────────────────────────────────────────────────────────
        fp.addChild(translated(0f, 3.17f, -0.04f,
                new com.sun.j3d.utils.geometry.Box(0.45f, 2.00f, 0.38f, flags, chimneyApp)));
        fp.addChild(translated(0f, 5.30f, -0.04f,
                new com.sun.j3d.utils.geometry.Box(0.60f, 0.18f, 0.50f, flags, chimneyApp)));

        group.addChild(fp);

        // ── flickering fire lights (always present; diamonds hidden until switch on) ─
        PointLight fireLight = new PointLight(
                new Color3f(1.0f, 0.5f, 0.1f),
                new Point3f(ROOM_R - 1.0f, FLOOR_Y + 1.1f, 0f),
                new Point3f(0.1f, 0.05f, 0.01f));
        fireLight.setCapability(PointLight.ALLOW_COLOR_WRITE);
        fireLight.setCapability(PointLight.ALLOW_ATTENUATION_WRITE);
        fireLight.setEnable(false);
        fireLight.setInfluencingBounds(wb());
        group.addChild(fireLight);

        PointLight upperFireGlow = new PointLight(
                new Color3f(1.0f, 0.72f, 0.22f),
                new Point3f(ROOM_R - 1.0f, FLOOR_Y + 1.9f, 0f),
                new Point3f(0.05f, 0.02f, 0.005f));
        upperFireGlow.setCapability(PointLight.ALLOW_COLOR_WRITE);
        upperFireGlow.setCapability(PointLight.ALLOW_ATTENUATION_WRITE);
        upperFireGlow.setEnable(false);
        upperFireGlow.setInfluencingBounds(wb());
        group.addChild(upperFireGlow);

        FireFlickerBehavior flicker = new FireFlickerBehavior(fireLight, upperFireGlow);
        flicker.setSchedulingBounds(wb());
        group.addChild(flicker);

        return group;
    }

    /**
     * Fully opaque emissive material for fire diamonds.
     */
    private Appearance fireGlowMat(Color3f color) {
        Appearance app = new Appearance();
        Material m = new Material();
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

        float signX = ROOM_R - 0.7f;
        float signY = FLOOR_Y + 2.85f;
        float signZ = 0f;

        Appearance boardApp = matEmissive(c(0.25f, 0.15f, 0.07f), c(0.55f, 0.35f, 0.15f), c(0.06f, 0.03f, 0.01f));
        int flags = com.sun.j3d.utils.geometry.Primitive.GENERATE_NORMALS;

        Transform3D boardT = new Transform3D();
        boardT.setTranslation(new Vector3f(signX, signY, signZ));
        TransformGroup boardTG = new TransformGroup(boardT);
        boardTG.addChild(new com.sun.j3d.utils.geometry.Box(0.06f, 0.28f, 0.85f, flags, boardApp));

        float halfWidth = 0.45f;
        float halfHeight = 0.04f;

        com.sun.j3d.utils.geometry.Text2D text = new com.sun.j3d.utils.geometry.Text2D(
                "Turn the light on!",
                new Color3f(1.0f, 0.92f, 0.60f),
                "SansSerif", 24, java.awt.Font.BOLD);
        text.setRectangleScaleFactor(0.004f);

        Transform3D tCentre = new Transform3D();
        tCentre.setTranslation(new Vector3f(-halfWidth, -halfHeight, 0f));
        Transform3D tRot = new Transform3D();
        tRot.rotY(-Math.PI / 2.0);
        Transform3D tFront = new Transform3D();
        tFront.setTranslation(new Vector3f(-0.08f, 0f, 0f));
        Transform3D combined = new Transform3D(tFront);
        combined.mul(tRot);
        combined.mul(tCentre);

        TransformGroup textTG = new TransformGroup(combined);
        textTG.addChild(text);
        boardTG.addChild(textTG);

        group.addChild(boardTG);
        return group;
    }

    // =========================================================================
    // LIGHT SWITCH
    // =========================================================================
    private TransformGroup buildLightSwitch() {
        TransformGroup group = new TransformGroup();

        float swX = ROOM_R - 0.28f;
        float swY = FLOOR_Y + 2.1f;
        float swZ = 1.6f;

        Appearance plateApp = matEmissive(c(0.70f, 0.70f, 0.70f), c(0.95f, 0.95f, 0.95f), c(0.15f, 0.15f, 0.15f));
        Appearance leverApp = matEmissive(c(0.50f, 0.50f, 0.50f), c(0.80f, 0.80f, 0.80f), c(0.10f, 0.10f, 0.10f));
        int flags = com.sun.j3d.utils.geometry.Primitive.GENERATE_NORMALS;

        Transform3D plateT = new Transform3D();
        plateT.rotY(Math.PI / 2.0);
        plateT.setTranslation(new Vector3f(swX, swY, swZ));
        TransformGroup plateTG = new TransformGroup(plateT);
        plateTG.addChild(new com.sun.j3d.utils.geometry.Box(0.18f, 0.30f, 0.04f, flags, plateApp));

        switchLeverTG = new TransformGroup();
        switchLeverTG.setCapability(TransformGroup.ALLOW_TRANSFORM_WRITE);
        Transform3D leverRot = new Transform3D();
        leverRot.rotX(Math.toRadians(30)); // down = off
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

        // Tilt lever: up (-30°) = on, down (+30°) = off
        Transform3D leverRot = new Transform3D();
        leverRot.rotX(Math.toRadians(fireOn ? -30 : 30));
        switchLeverTG.setTransform(leverRot);

        // Only animate the key if it hasn't been collected yet
        if (!fireplaceKeyTaken) {
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
                        Transform3D pos2 = new Transform3D();
                        pos2.setTranslation(new Vector3f(FIRE_KEY_POS.x, curY, FIRE_KEY_POS.z));
                        fireplaceKeyTG.setTransform(pos2);
                        if (t >= 1.0f)
                            break;
                        try {
                            Thread.sleep(16);
                        } catch (InterruptedException ignored) {
                        }
                    }
                }, "key-rise").start();
            } else {
                Transform3D pos2 = new Transform3D();
                pos2.setTranslation(new Vector3f(FIRE_KEY_POS.x, FLOOR_Y - 1.5f, FIRE_KEY_POS.z));
                fireplaceKeyTG.setTransform(pos2);
            }
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

            keyTGs[i] = new TransformGroup(hiddenKeyTransform(i));
            keyTGs[i].setCapability(TransformGroup.ALLOW_TRANSFORM_WRITE);
            keyTGs[i].addChild(spinTG);
            group.addChild(keyTGs[i]);
        }
        return group;
    }

    private Transform3D visibleKeyTransform(int index) {
        Transform3D t = new Transform3D();
        t.setTranslation(new Vector3f(KEY_POSITIONS[index].x, KEY_POSITIONS[index].y, KEY_POSITIONS[index].z));
        return t;
    }

    private Transform3D hiddenKeyTransform(int index) {
        Transform3D t = new Transform3D();
        t.setTranslation(new Vector3f(
                KEY_POSITIONS[index].x,
                FLOOR_Y - 0.55f,
                KEY_POSITIONS[index].z));
        return t;
    }

    private void spawnKey(int index) {
        if (index < 0 || index >= keyTGs.length)
            return;
        if (keySpawned[index] || keyTaken[index])
            return;

        keySpawned[index] = true;

        new Thread(() -> {
            long durationMs = 1200;
            long startTime = System.currentTimeMillis();

            float startY = FLOOR_Y - 0.55f;
            float endY = KEY_POSITIONS[index].y;

            while (true) {
                long elapsed = System.currentTimeMillis() - startTime;
                float t = Math.min(elapsed / (float) durationMs, 1.0f);

                // smooth ease-out
                float ease = 1f - (1f - t) * (1f - t);

                float currentY = startY + (endY - startY) * ease;

                Transform3D tx = new Transform3D();
                tx.setTranslation(new Vector3f(
                        KEY_POSITIONS[index].x,
                        currentY,
                        KEY_POSITIONS[index].z));

                keyTGs[index].setTransform(tx);

                if (t >= 1.0f)
                    break;

                try {
                    Thread.sleep(16);
                } catch (InterruptedException ignored) {
                }
            }
        }, "key-rise-" + index).start();
    }

    private void answerPuzzle1Wrong(int chosenIndex) {
        if (puzzleSolved[0])
            return;
        puzzle1LastWrong = chosenIndex;
        // Redraw board: show ✗ on the wrong guess but keep cursor visible so player can
        // retry
        if (puzzle1PaperShape != null) {
            puzzle1PaperShape.setAppearance(puzzlePaperAppearance(puzzle1LastWrong, puzzle1CursorPos));
        }
    }

    private TransformGroup buildPuzzleBoards() {
        TransformGroup group = new TransformGroup();

        float boardW = 6.2f;
        float boardH = 3.4f;
        float centerX = 0f;
        float centerY = FLOOR_Y + 2.75f;
        float z = SOUTH_Z - 0.18f;

        puzzle1PaperShape = new Shape3D(
                texturedQuadGeo(centerX - boardW / 2f, centerY - boardH / 2f, z,
                        centerX + boardW / 2f, centerY + boardH / 2f, z,
                        new float[] { 0, 0, -1 }),
                puzzlePaperAppearance(-1, 0));
        puzzle1PaperShape.setCapability(Shape3D.ALLOW_APPEARANCE_WRITE);
        puzzle1PaperShape.setPickable(false);
        group.addChild(puzzle1PaperShape);

        return group;
    }

    /**
     * Returns true if the player is close enough to the south puzzle board to
     * interact.
     */
    private boolean nearPuzzleBoard(float px, float pz) {
        float dz = pz - BOARD_Z;
        return (px * px + dz * dz) < BOARD_INTERACT_DIST * BOARD_INTERACT_DIST;
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

            doorLightNodes[i] = new PointLight(YELLOW,
                    new Point3f(xs[i], lightY, lightZ),
                    new Point3f(0.10f, 0.06f, 0.01f));
            doorLightNodes[i].setCapability(PointLight.ALLOW_STATE_WRITE);
            doorLightNodes[i].setEnable(false);
            doorLightNodes[i].setInfluencingBounds(wb());
            group.addChild(doorLightNodes[i]);
        }
        return group;
    }

    private Appearance lightAppearance(boolean on) {
        return on
                ? matEmissive(c(1.0f, 0.75f, 0.10f), c(1.0f, 0.85f, 0.20f), c(1.0f, 0.60f, 0.04f))
                : matEmissive(c(0.08f, 0.07f, 0.05f), c(0.18f, 0.16f, 0.12f), c(0.02f, 0.018f, 0.012f));
    }

    private void collectKey(int index) {
        if (!keySpawned[index] || keyTaken[index])
            return;
        keyTaken[index] = true;
        collectedKeys++;
        Transform3D gone = new Transform3D();
        gone.setScale(0.001);
        gone.setTranslation(new Vector3f(KEY_POSITIONS[index].x, FLOOR_Y - 2f, KEY_POSITIONS[index].z));
        keyTGs[index].setTransform(gone);
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
            long durationMs = 1800;
            long startTime = System.currentTimeMillis();
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

    private boolean victoryShown = false;

    private void showVictoryScreen() {
        if (victoryShown)
            return;
        victoryShown = true;
        SwingUtilities.invokeLater(() -> {
            JPanel panel = new JPanel() {
                protected void paintComponent(Graphics g) {
                    super.paintComponent(g);
                    Graphics2D g2 = (Graphics2D) g;
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                    // Dark vignette background
                    g2.setColor(new Color(8, 5, 3));
                    g2.fillRect(0, 0, getWidth(), getHeight());

                    // Faint golden glow behind title
                    int cx = getWidth() / 2;
                    int cy = getHeight() / 2 - 60;
                    java.awt.RadialGradientPaint glow = new java.awt.RadialGradientPaint(
                            cx, cy, 260,
                            new float[] { 0f, 1f },
                            new Color[] { new Color(200, 150, 20, 80), new Color(0, 0, 0, 0) });
                    g2.setPaint(glow);
                    g2.fillOval(cx - 260, cy - 260, 520, 520);

                    // Title
                    g2.setFont(new Font("Serif", Font.BOLD, 64));
                    String title = "YOU ESCAPED";
                    FontMetrics fm = g2.getFontMetrics();
                    int tx = (getWidth() - fm.stringWidth(title)) / 2;
                    g2.setColor(new Color(30, 20, 10));
                    g2.drawString(title, tx + 3, cy + 3);
                    g2.setColor(new Color(230, 185, 60));
                    g2.drawString(title, tx, cy);

                    // Subtitle
                    g2.setFont(new Font("Serif", Font.ITALIC, 28));
                    String sub = "The Roundtable Hold fades behind you...";
                    FontMetrics fm2 = g2.getFontMetrics();
                    int sx = (getWidth() - fm2.stringWidth(sub)) / 2;
                    g2.setColor(new Color(180, 150, 90, 200));
                    g2.drawString(sub, sx, cy + 60);

                    // Instruction
                    g2.setFont(new Font("SansSerif", Font.PLAIN, 18));
                    String hint = "Press ESC to exit";
                    FontMetrics fm3 = g2.getFontMetrics();
                    int hx = (getWidth() - fm3.stringWidth(hint)) / 2;
                    g2.setColor(new Color(130, 110, 70, 160));
                    g2.drawString(hint, hx, getHeight() - 50);
                }
            };
            panel.setOpaque(true);
            panel.setBackground(new Color(8, 5, 3));
            panel.addKeyListener(new KeyAdapter() {
                public void keyPressed(KeyEvent e) {
                    if (e.getKeyCode() == KeyEvent.VK_ESCAPE)
                        System.exit(0);
                }
            });

            JFrame win = new JFrame("Roundtable Hold");
            win.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            win.setUndecorated(true);
            win.setSize(RoundtableHold.this.getSize());
            win.setLocationRelativeTo(RoundtableHold.this);
            win.add(panel);
            win.setVisible(true);
            panel.setFocusable(true);
            panel.requestFocusInWindow();

            // Fade in from black
            new Thread(() -> {
                for (int alpha = 255; alpha >= 0; alpha -= 5) {
                    final int a = alpha;
                    SwingUtilities.invokeLater(() -> {
                        panel.putClientProperty("overlay_alpha", a);
                        panel.repaint();
                    });
                    try {
                        Thread.sleep(16);
                    } catch (InterruptedException ignored) {
                    }
                }
            }, "fade-in").start();
        });
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
        private final PointLight mainLight;
        private final PointLight upperGlow;
        private final WakeupOnElapsedFrames wakeup = new WakeupOnElapsedFrames(4);

        FireFlickerBehavior(PointLight mainLight, PointLight upperGlow) {
            this.mainLight = mainLight;
            this.upperGlow = upperGlow;
        }

        public void initialize() {
            wakeupOn(wakeup);
        }

        public void processStimulus(java.util.Enumeration criteria) {
            float flicker = 0.85f + (float) Math.random() * 0.25f;
            float r = 0.85f + (float) Math.random() * 0.15f;
            float g = 0.32f + (float) Math.random() * 0.35f;
            float b = 0.03f + (float) Math.random() * 0.05f;
            mainLight.setColor(new Color3f(r, g, b));
            upperGlow.setColor(new Color3f(
                    0.85f * flicker,
                    0.42f * flicker,
                    0.08f * flicker));
            mainLight.setAttenuation(new Point3f(
                    0.025f + (float) Math.random() * 0.015f,
                    0.010f + (float) Math.random() * 0.010f,
                    0.001f + (float) Math.random() * 0.002f));
            upperGlow.setAttenuation(new Point3f(
                    0.045f + (float) Math.random() * 0.015f,
                    0.018f + (float) Math.random() * 0.008f,
                    0.004f + (float) Math.random() * 0.002f));
            wakeupOn(wakeup);
        }
    }

    private void selectPuzzle1Answer(int chosenIndex) {
        if (puzzleSolved[0])
            return;

        if (chosenIndex == 1) {
            // Correct answer: (b) Ambient Light
            puzzleSolved[0] = true;
            if (puzzle1PaperShape != null) {
                // Show final solved state: ✓ on correct answer, no cursor
                puzzle1PaperShape.setAppearance(puzzlePaperAppearance(chosenIndex, puzzle1CursorPos));
            }
            spawnKey(0);
        } else {
            answerPuzzle1Wrong(chosenIndex);
        }
    }

    // ── puzzle 2 solution ─────────────────────────────────────────────────────
    private void solvePuzzle2() {
        if (puzzleSolved[1])
            return;
        puzzleSolved[1] = true;

        // Hide the hitbox so it can no longer be interacted with
        strayChairHitbox.setPickable(false);

        // Animate the stray chair sliding under the table
        float startX = STRAY_CHAIR_X;
        float startZ = STRAY_CHAIR_Z;
        float endX = TUCKED_X;
        float endZ = TUCKED_Z;
        float chairY = FLOOR_Y + 0.55f;
        float chairRot = -(float) STRAY_ANGLE + (float) (Math.PI / 2.0);

        new Thread(() -> {
            long durationMs = 900;
            long startTime = System.currentTimeMillis();
            while (true) {
                long elapsed = System.currentTimeMillis() - startTime;
                float t = Math.min(elapsed / (float) durationMs, 1.0f);
                // ease-out cubic
                float ease = 1f - (1f - t) * (1f - t) * (1f - t);

                float cx = startX + (endX - startX) * ease;
                float cz = startZ + (endZ - startZ) * ease;

                Transform3D tx = new Transform3D();
                tx.setTranslation(new Vector3f(cx, chairY, cz));
                Transform3D rot = new Transform3D();
                rot.rotY(chairRot);
                tx.mul(rot);
                strayChairTG.setTransform(tx);

                if (t >= 1.0f)
                    break;
                try {
                    Thread.sleep(16);
                } catch (InterruptedException ignored) {
                }
            }
            // Once the chair is fully tucked, rise the key
            spawnKey(1);
        }, "chair-push").start();
    }

    // =========================================================================
    // PUZZLE CLICK HANDLER
    // =========================================================================
    // =========================================================================
    // FIRST-PERSON CONTROLLER
    // =========================================================================
    private class FirstPersonController extends Behavior implements KeyListener, MouseMotionListener, MouseListener {
        private final TransformGroup viewTG;
        private final Canvas3D canvas;
        private final PickCanvas hoverPickCanvas;
        private final WakeupOnElapsedFrames wakeup = new WakeupOnElapsedFrames(0);
        private final boolean[] keys = new boolean[256];
        private float x = 0f, z = 10f, yaw = 0f, pitch = 0f;
        private long lastFrameTime = System.nanoTime();
        private int hoverFrameSkip = 0;
        private Robot robot;
        private boolean robotReady = false;
        private boolean firstWarp = true;
        private volatile boolean mouseButtonHeld = false; // suppress warp during click

        FirstPersonController(TransformGroup viewTG, Canvas3D canvas, BranchGroup root) {
            this.viewTG = viewTG;
            this.canvas = canvas;
            this.hoverPickCanvas = new PickCanvas(canvas, root);
            this.hoverPickCanvas.setMode(PickTool.BOUNDS);
            this.hoverPickCanvas.setTolerance(6.0f);

            BufferedImage cursorImg = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = cursorImg.createGraphics();
            g.setColor(Color.WHITE);
            g.setStroke(new BasicStroke(2f));
            g.drawLine(16, 6, 16, 13);
            g.drawLine(16, 19, 16, 26);
            g.drawLine(6, 16, 13, 16);
            g.drawLine(19, 16, 26, 16);
            g.dispose();

            Cursor crosshairCursor = Toolkit.getDefaultToolkit().createCustomCursor(
                    cursorImg, new Point(16, 16), "crosshair");
            canvas.setCursor(crosshairCursor);

            try {
                robot = new Robot();
                robotReady = true;
            } catch (AWTException e) {
                System.err.println("Robot unavailable — mouse capture disabled: " + e.getMessage());
            }
        }

        public void initialize() {
            wakeupOn(wakeup);
        }

        public void processStimulus(java.util.Enumeration criteria) {
            long now = System.nanoTime();
            float dt = (now - lastFrameTime) / 1_000_000_000.0f;
            lastFrameTime = now;

            // Prevent giant jumps after lag/spikes
            if (dt > 0.05f)
                dt = 0.05f;

            updateMovement(dt);
            checkKeyPickups();

            // Victory: player reached the far end of the north hallway
            if (collectedKeys >= 3 && z <= HALL_END_Z + 1.0f) {
                showVictoryScreen();
            }

            // Picking every frame is expensive, so only do hover checks every 5 frames
            hoverFrameSkip++;
            if (hoverFrameSkip >= 5) {
                updateHoverHighlight();
                hoverFrameSkip = 0;
            }

            if (robotReady && canvas.isShowing() && !mouseButtonHeld) {
                Point loc = canvas.getLocationOnScreen();
                int cx = loc.x + canvas.getWidth() / 2;
                int cy = loc.y + canvas.getHeight() / 2;
                robot.mouseMove(cx, cy);
                firstWarp = false;
            }

            wakeupOn(wakeup);
        }

        private boolean hoveringStrayChair = false;

        private void updateHoverHighlight() {
            hoveringStrayChair = false;

            if (puzzleSolved[1])
                return; // stray chair already solved, skip pick
            if (hoverPickCanvas == null || canvas == null || !canvas.isShowing())
                return;

            hoverPickCanvas.setShapeLocation(canvas.getWidth() / 2, canvas.getHeight() / 2);
            PickResult result = hoverPickCanvas.pickClosest();
            if (result == null)
                return;

            Node node = result.getNode(PickResult.SHAPE3D);
            if (node instanceof Shape3D && PUZZLE2_CHAIR.equals(node.getUserData())) {
                hoveringStrayChair = true;
            }
        }

        private void updateMovement(float dt) {
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
                yaw += TURN_SPEED * dt;
            if (down(KeyEvent.VK_RIGHT))
                yaw -= TURN_SPEED * dt;
            if (down(KeyEvent.VK_UP))
                pitch += TURN_SPEED * dt;
            if (down(KeyEvent.VK_DOWN))
                pitch -= TURN_SPEED * dt;

            clampPitch();

            if (forward != 0f || strafe != 0f) {
                float len = (float) Math.sqrt(forward * forward + strafe * strafe);
                forward /= len;
                strafe /= len;

                float sin = (float) Math.sin(yaw);
                float cos = (float) Math.cos(yaw);

                float moveAmount = WALK_SPEED * dt;

                float dx = (-sin * forward + cos * strafe) * moveAmount;
                float dz = (-cos * forward - sin * strafe) * moveAmount;

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
            float limit = (float) Math.toRadians(80);
            if (pitch > limit)
                pitch = limit;
            if (pitch < -limit)
                pitch = -limit;
        }

        private void checkKeyPickups() {
            for (int i = 0; i < KEY_POSITIONS.length; i++) {
                if (!keySpawned[i] || keyTaken[i])
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
                    // Light up the next door gem and count toward the 3 needed
                    int li = collectedKeys;
                    collectedKeys++;
                    doorLightShapes[li].setAppearance(lightAppearance(true));
                    doorLightNodes[li].setEnable(true);
                    if (collectedKeys == 3)
                        openDoor();
                }
            }
        }

        private boolean down(int code) {
            return code >= 0 && code < keys.length && keys[code];
        }

        public void keyPressed(KeyEvent e) {
            int code = e.getKeyCode();
            if (code >= 0 && code < keys.length)
                keys[code] = true;

            boolean nearBoard = !puzzleSolved[0] && nearPuzzleBoard(x, z);

            // SHIFT → cycle cursor downward through answers (wraps bottom back to top)
            if (nearBoard && code == KeyEvent.VK_SHIFT) {
                puzzle1CursorPos = (puzzle1CursorPos + 1) % 4;
                if (puzzle1PaperShape != null)
                    puzzle1PaperShape.setAppearance(puzzlePaperAppearance(puzzle1LastWrong, puzzle1CursorPos));
                return;
            }

            // E → confirm quiz answer OR interact with stray chair OR toggle fire switch
            if (code == KeyEvent.VK_E) {
                if (nearBoard) {
                    selectPuzzle1Answer(puzzle1CursorPos);
                } else if (hoveringStrayChair) {
                    solvePuzzle2();
                } else {
                    float swX = ROOM_R - 0.28f, swZ = 1.6f;
                    float dx = x - swX, dz = z - swZ;
                    if (dx * dx + dz * dz < 3.5f * 3.5f) {
                        toggleFire();
                    }
                }
            }
        }

        public void keyReleased(KeyEvent e) {
            int code = e.getKeyCode();
            if (code >= 0 && code < keys.length)
                keys[code] = false;
        }

        public void keyTyped(KeyEvent e) {
        }

        // MouseListener — track button state so robot warp is suppressed during clicks
        public void mousePressed(MouseEvent e) {
            mouseButtonHeld = true;
        }

        public void mouseReleased(MouseEvent e) {
            mouseButtonHeld = false;
        }

        public void mouseClicked(MouseEvent e) {
        }

        public void mouseEntered(MouseEvent e) {
        }

        public void mouseExited(MouseEvent e) {
        }

        public void mouseDragged(MouseEvent e) {
            mouseMoved(e);
        }

        public void mouseMoved(MouseEvent e) {
            if (!canvas.hasFocus())
                canvas.requestFocusInWindow();
            if (!robotReady || firstWarp || !canvas.isShowing())
                return;
            int cx = canvas.getWidth() / 2;
            int cy = canvas.getHeight() / 2;
            int dx = e.getX() - cx;
            int dy = e.getY() - cy;
            if (dx == 0 && dy == 0)
                return;
            yaw -= dx * MOUSE_SENS;
            pitch -= dy * MOUSE_SENS;
            clampPitch();
        }
    }

    // =========================================================================
    // GEOMETRY UTILITIES
    // =========================================================================
    private void addTriN(ArrayList<float[]> vv, ArrayList<float[]> nn,
            float nx, float ny, float nz, float[] a, float[] b, float[] c) {
        float[] n = { nx, ny, nz };
        vv.add(a);
        vv.add(b);
        vv.add(c);
        nn.add(n);
        nn.add(n);
        nn.add(n);
    }

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

    private GeometryArray quadGeo(float[] bl, float[] br, float[] tr, float[] tl, float[] n) {
        ArrayList<float[]> vv = new ArrayList<>(), nn = new ArrayList<>();
        addQuadN(vv, nn, n[0], n[1], n[2], bl, br, tr, tl);
        return listToGeo(vv, nn);
    }

    private TransformGroup placedDisc(float r, int seg, float normalY, float y, Appearance app) {
        int vCount = seg + 2;
        TriangleFanArray geo = new TriangleFanArray(vCount,
                GeometryArray.COORDINATES | GeometryArray.NORMALS, new int[] { vCount });
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
        float[][] verts = {
                { s, 0, 0 }, { 0, s, 0 }, { 0, 0, s }, { 0, 0, s }, { 0, s, 0 }, { -s, 0, 0 },
                { -s, 0, 0 }, { 0, s, 0 }, { 0, 0, -s }, { 0, 0, -s }, { 0, s, 0 }, { s, 0, 0 },
                { s, 0, 0 }, { 0, 0, s }, { 0, -s, 0 }, { 0, 0, s }, { -s, 0, 0 }, { 0, -s, 0 },
                { -s, 0, 0 }, { 0, 0, -s }, { 0, -s, 0 }, { 0, 0, -s }, { s, 0, 0 }, { 0, -s, 0 }
        };
        TriangleArray geo = new TriangleArray(verts.length, GeometryArray.COORDINATES | GeometryArray.NORMALS);
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
    // chosenIndex: -1 = no guess, 0/2/3 = last wrong guess, 1 = correct (solved)
    // cursorPos: 0-3 = cursor row (shown as ► while puzzle unsolved)
    private Appearance puzzlePaperAppearance(int chosenIndex, int cursorPos) {
        BufferedImage img = new BufferedImage(1024, 768, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g.setColor(new Color(120, 78, 38));
        g.fillRoundRect(28, 28, 968, 712, 60, 60);
        g.setColor(new Color(221, 190, 122));
        g.fillRoundRect(60, 60, 904, 648, 48, 48);
        g.setColor(new Color(155, 100, 48));
        g.setStroke(new BasicStroke(8f));
        g.drawRoundRect(60, 60, 904, 648, 48, 48);

        // Fixed-seed speckles so they don't re-randomise on every redraw
        g.setColor(new Color(130, 84, 40, 55));
        java.util.Random rng = new java.util.Random(42);
        for (int i = 0; i < 450; i++) {
            int x = 70 + (int) (rng.nextFloat() * 884);
            int y = 70 + (int) (rng.nextFloat() * 628);
            int r = 1 + (int) (rng.nextFloat() * 3);
            g.fillOval(x, y, r, r);
        }

        g.setColor(new Color(55, 34, 18));
        g.setFont(new Font("Serif", Font.BOLD, 42));
        g.drawString("Puzzle I", 420, 125);

        g.setFont(new Font("Serif", Font.BOLD, 30));
        drawWrapped(g,
                "Which of the following light types provides a uniform illumination in all directions and locations, generally serving as a simplified representation of the numerous weak interobject reflections in a real-world scene?",
                115, 185, 800, 38);

        String[] labels = { "(a) Point Light", "(b) Ambient Light", "(c) Directional Light", "(d) Spotlight" };
        int[] labelYs = { 455, 520, 585, 650 };
        int correctIdx = 1; // (b) Ambient Light

        // Controls hint at bottom
        if (chosenIndex < 0) {
            g.setFont(new Font("Serif", Font.ITALIC, 24));
            g.setColor(new Color(90, 58, 28, 180));
            g.drawString("SHIFT to cycle  -  E to confirm", 315, 710);
        }

        boolean solved = (chosenIndex == correctIdx);

        g.setFont(new Font("Serif", Font.BOLD, 34));
        for (int i = 0; i < 4; i++) {
            // ── mark the last wrong guess with ✗ (only while unsolved) ──────
            if (!solved && chosenIndex >= 0 && i == chosenIndex) {
                g.setColor(new Color(180, 30, 30));
                g.setFont(new Font("Serif", Font.BOLD, 46));
                g.drawString("\u2717", 110, labelYs[i]);
                g.setFont(new Font("Serif", Font.BOLD, 34));
            }
            // ── mark the correct answer with ✓ when solved ──────────────────
            if (solved && i == correctIdx) {
                g.setColor(new Color(30, 120, 35));
                g.setFont(new Font("Serif", Font.BOLD, 46));
                g.drawString("\u2713", 110, labelYs[i]);
                g.setFont(new Font("Serif", Font.BOLD, 34));
            }
            // ── cursor arrow while puzzle is unsolved ───────────────────────
            if (!solved && i == cursorPos) {
                g.setColor(new Color(180, 110, 20));
                g.setFont(new Font("Serif", Font.BOLD, 40));
                g.drawString("\u25ba", 112, labelYs[i]);
                g.setFont(new Font("Serif", Font.BOLD, 34));
                // Subtle row highlight
                g.setColor(new Color(180, 130, 50, 60));
                g.fillRoundRect(100, labelYs[i] - 36, 820, 44, 10, 10);
            }
            g.setColor(new Color(55, 34, 18));
            g.drawString(labels[i], 170, labelYs[i]);
        }

        g.dispose();

        Texture2D tex = new Texture2D(Texture.BASE_LEVEL, Texture.RGBA, 1024, 768);
        tex.setImage(0, new ImageComponent2D(ImageComponent2D.FORMAT_RGBA, img));
        tex.setEnable(true);

        Appearance app = new Appearance();
        app.setTexture(tex);
        TextureAttributes ta = new TextureAttributes();
        ta.setTextureMode(TextureAttributes.REPLACE);
        app.setTextureAttributes(ta);
        PolygonAttributes pa = new PolygonAttributes();
        pa.setCullFace(PolygonAttributes.CULL_NONE);
        app.setPolygonAttributes(pa);
        return app;
    }

    private void drawWrapped(Graphics2D g, String text, int x, int y, int maxWidth, int lineHeight) {
        FontMetrics fm = g.getFontMetrics();
        String[] words = text.split(" ");
        String line = "";
        for (String word : words) {
            String test = line.isEmpty() ? word : line + " " + word;
            if (fm.stringWidth(test) > maxWidth) {
                g.drawString(line, x, y);
                y += lineHeight;
                line = word;
            } else {
                line = test;
            }
        }
        if (!line.isEmpty())
            g.drawString(line, x, y);
    }

    private Appearance answerNormalAppearance() {
        return invisiblePickAppearance();
    }

    private Appearance answerHighlightAppearance() {
        Appearance app = new Appearance();

        ColoringAttributes ca = new ColoringAttributes(
                1.0f, 0.86f, 0.22f, ColoringAttributes.SHADE_FLAT);
        app.setColoringAttributes(ca);

        TransparencyAttributes tr = new TransparencyAttributes(
                TransparencyAttributes.BLENDED, 0.45f);
        app.setTransparencyAttributes(tr);

        PolygonAttributes pa = new PolygonAttributes();
        pa.setCullFace(PolygonAttributes.CULL_NONE);
        app.setPolygonAttributes(pa);

        RenderingAttributes ra = new RenderingAttributes();
        ra.setDepthBufferWriteEnable(false);
        ra.setDepthBufferEnable(true);
        app.setRenderingAttributes(ra);

        return app;
    }

    private Appearance invisiblePickAppearance() {
        Appearance app = new Appearance();

        ColoringAttributes ca = new ColoringAttributes(0f, 0f, 0f, ColoringAttributes.SHADE_FLAT);
        app.setColoringAttributes(ca);

        // 1.0f is often skipped entirely by the pick traversal — use 0.99f instead
        TransparencyAttributes tr = new TransparencyAttributes(TransparencyAttributes.BLENDED, 0.99f);
        app.setTransparencyAttributes(tr);

        PolygonAttributes pa = new PolygonAttributes();
        pa.setCullFace(PolygonAttributes.CULL_NONE);
        app.setPolygonAttributes(pa);

        RenderingAttributes ra = new RenderingAttributes();
        ra.setDepthBufferWriteEnable(false);
        ra.setDepthBufferEnable(true);
        app.setRenderingAttributes(ra);

        return app;
    }

    private GeometryArray texturedQuadGeo(float x1, float y1, float z1,
            float x2, float y2, float z2, float[] n) {
        QuadArray qa = new QuadArray(4,
                GeometryArray.COORDINATES | GeometryArray.NORMALS | GeometryArray.TEXTURE_COORDINATE_2);
        qa.setCoordinate(0, new Point3f(x1, y1, z1));
        qa.setCoordinate(1, new Point3f(x2, y1, z1));
        qa.setCoordinate(2, new Point3f(x2, y2, z2));
        qa.setCoordinate(3, new Point3f(x1, y2, z2));
        Vector3f normal = new Vector3f(n[0], n[1], n[2]);
        for (int i = 0; i < 4; i++)
            qa.setNormal(i, normal);
        qa.setTextureCoordinate(0, 0, new TexCoord2f(1f, 0f));
        qa.setTextureCoordinate(0, 1, new TexCoord2f(0f, 0f));
        qa.setTextureCoordinate(0, 2, new TexCoord2f(0f, 1f));
        qa.setTextureCoordinate(0, 3, new TexCoord2f(1f, 1f));
        return qa;
    }

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
                int wobble = (int) (Math.sin(y * 0.25) * 6);
                g.drawLine(0, y, size, Math.max(0, Math.min(size - 1, y + wobble)));
            }
            g.setColor(base.darker());
            for (int x = 0; x < size; x += 32)
                g.drawLine(x, 0, x, size);
        } else {
            int brickH = 16, brickW = 32;
            for (int y = 0; y < size; y += brickH) {
                g.drawLine(0, y, size, y);
                int offset = ((y / brickH) % 2) * (brickW / 2);
                for (int x = -offset; x < size; x += brickW)
                    g.drawLine(x, y, x, y + brickH);
            }
        }
        g.dispose();

        Texture2D tex = new Texture2D(Texture.BASE_LEVEL, Texture.RGB, size, size);
        tex.setImage(0, new ImageComponent2D(ImageComponent2D.FORMAT_RGB, img));
        tex.setEnable(true);

        Appearance app = matEmissive(
                c(base.getRed() / 255f * 0.35f, base.getGreen() / 255f * 0.35f, base.getBlue() / 255f * 0.35f),
                c(base.getRed() / 255f, base.getGreen() / 255f, base.getBlue() / 255f),
                c(0.03f, 0.02f, 0.015f));
        app.setTexture(tex);
        TextureAttributes ta = new TextureAttributes();
        ta.setTextureMode(TextureAttributes.MODULATE);
        app.setTextureAttributes(ta);
        TexCoordGeneration tcg = new TexCoordGeneration(
                TexCoordGeneration.OBJECT_LINEAR, TexCoordGeneration.TEXTURE_COORDINATE_2);
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
