package com.createstats.vccmk3.client;

import com.createstats.vccmk3.model.Airframe;
import com.createstats.vccmk3.model.ControlAxis;
import com.createstats.vccmk3.model.SlotSpec;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import javax.annotation.Nullable;
import java.util.Map;

// a slot is a part of the aircraft, not a box on top of one
public final class RigBoardRenderer {
    static final int PANEL_DEEP = 0xFF0A0E13;
    static final int GRID = 0xFF171F29;
    static final int SKIN = 0xFF1C2936;
    static final int SKIN_LIT = 0xFF253545;
    static final int SKIN_EDGE = 0xFF344E64;
    static final int GLASS = 0xFF2E4A60;
    static final int EDGE = 0xFF3D4C5C;
    static final int EDGE_BRIGHT = 0xFF6E8398;
    static final int TEXT = 0xFFE4EEF8;
    static final int TEXT_DIM = 0xFF8CA1B4;
    static final int TEXT_FAINT = 0xFF53677A;
    static final int BRASS = 0xFFD4AF37;
    static final int WARN = 0xFFFFB454;
    static final int BAD = 0xFFFF6B6B;
    static final int GOOD = 0xFF6EE7A8;

    public record SlotView(String key, State state, float deflection, int deviceCount,
                           float rpmFraction, String note) {
        public enum State {
            FILLED,

            EMPTY,

            FAULT
        }
    }

    public record DevicePlot(java.util.UUID id, String label, int categoryOrdinal,
                             double forward, double right, @Nullable String slotKey,
                             float rpmFraction, boolean selected) {}

    public record BoardState(Airframe airframe,
                             Map<String, SlotView> slots,
                             @Nullable String selectedSlot,
                             @Nullable String hoveredSlot,
                             float steer,
                             float driveScroll,
                             boolean live,
                             java.util.List<DevicePlot> plots) {}

    private RigBoardRenderer() {}

    public static Map<String, RigShapes.SlotShape> draw(GuiGraphics graphics, Font font,
                                                        int x, int y, int w, int h,
                                                        BoardState state) {
        graphics.fill(x, y, x + w, y + h, PANEL_DEEP);
        for (int gx = x + 24; gx < x + w; gx += 32) graphics.fill(gx, y + 6, gx + 1, y + h - 6, GRID);
        for (int gy = y + 24; gy < y + h; gy += 32) graphics.fill(x + 6, gy, x + w - 6, gy + 1, GRID);

        Airframe frame = state.airframe();

        double scale = Math.min((w - 40) / RigShapes.designWidth(frame),
                (h - 120) / RigShapes.designHeight(frame));
        scale = Math.max(0.28, Math.min(1.0, scale));

        int cx = x + w / 2;
        int cy = y + h / 2 + (frame == Airframe.AIRSHIP ? (int) (26 * scale) : (int) (8 * scale));
        RigShapes.Project p = RigShapes.projector(cx, cy, scale);
        Map<String, RigShapes.SlotShape> shapes = RigShapes.layout(frame, p);

        if (frame.isFreeform()) {
            drawFreeform(graphics, font, x, y, w, h, state);
            return shapes;
        }
        switch (frame) {
            case FIXED_WING -> drawFixedWingStructure(graphics, p);
            case AIRSHIP -> drawAirshipStructure(graphics, p);
            case GROUND -> drawGroundStructure(graphics, p);
            default -> { }
        }

        for (SlotSpec spec : frame.slotList()) {
            RigShapes.SlotShape shape = shapes.get(spec.key());
            if (shape == null) continue;
            SlotView view = state.slots().get(spec.key());
            if (view == null) {
                view = new SlotView(spec.key(), SlotView.State.EMPTY, 0f, 0, 0f, "");
            }
            boolean selected = spec.key().equals(state.selectedSlot());
            boolean hovered = spec.key().equals(state.hoveredSlot());
            drawSlot(graphics, font, shape, spec, view, state, selected, hovered);
        }

        graphics.fill(cx, y + 16, cx + 1, y + 30, EDGE);
        ConsoleDraw.polygon(graphics, new int[] {cx - 5, cx + 5, cx},
                new int[] {y + 22, y + 22, y + 13}, EDGE);
        graphics.drawString(font, "NOSE", cx - font.width("NOSE") / 2, y + 33, TEXT_FAINT, false);

        return shapes;
    }

    public static final java.util.Map<java.util.UUID, int[]> LAST_PLOT_HITS = new java.util.HashMap<>();

    private static void drawFreeform(GuiGraphics graphics, Font font, int x, int y, int w, int h,
                                     BoardState state) {
        LAST_PLOT_HITS.clear();
        java.util.List<DevicePlot> plots = state.plots();

        int cx = x + w / 2;
        int cy = y + h / 2;

        if (plots.isEmpty()) {
            String line = "Bind hardware and it appears here, where it really is.";
            graphics.drawString(font, line, cx - font.width(line) / 2, cy - 4, TEXT_FAINT, false);
            return;
        }

        double minF = Double.MAX_VALUE;
        double maxF = -Double.MAX_VALUE;
        double minR = Double.MAX_VALUE;
        double maxR = -Double.MAX_VALUE;
        for (DevicePlot plot : plots) {
            minF = Math.min(minF, plot.forward());
            maxF = Math.max(maxF, plot.forward());
            minR = Math.min(minR, plot.right());
            maxR = Math.max(maxR, plot.right());
        }

        double spanF = Math.max(6.0, maxF - minF);
        double spanR = Math.max(6.0, maxR - minR);
        double midF = (minF + maxF) / 2.0;
        double midR = (minR + maxR) / 2.0;

        double scale = Math.min((w - 120) / (spanR + 4.0), (h - 150) / (spanF + 4.0));
        scale = Math.max(3.0, Math.min(34.0, scale));

        int hx0 = (int) Math.round(cx + (minR - midR - 1.6) * scale);
        int hx1 = (int) Math.round(cx + (maxR - midR + 1.6) * scale);
        int hy0 = (int) Math.round(cy - (maxF - midF + 1.6) * scale);
        int hy1 = (int) Math.round(cy - (minF - midF - 1.6) * scale);
        int chamfer = Math.max(4, (int) (scale * 1.2));
        int[] hxs = {hx0 + chamfer, hx1 - chamfer, hx1, hx1, hx1 - chamfer, hx0 + chamfer, hx0, hx0};
        int[] hys = {hy0, hy0, hy0 + chamfer, hy1 - chamfer, hy1, hy1, hy1 - chamfer, hy0 + chamfer};
        ConsoleDraw.polygon(graphics, hxs, hys, SKIN);
        ConsoleDraw.polyOutline(graphics, hxs, hys, SKIN_EDGE);
        ConsoleDraw.line(graphics, cx, hy0, cx, hy1, ConsoleDraw.blend(SKIN_EDGE, PANEL_DEEP, 0.4f));

        graphics.fill(cx, y + 16, cx + 1, y + 30, EDGE);
        ConsoleDraw.polygon(graphics, new int[] {cx - 5, cx + 5, cx},
                new int[] {y + 22, y + 22, y + 13}, EDGE);
        graphics.drawString(font, "NOSE", cx - font.width("NOSE") / 2, y + 33, TEXT_FAINT, false);

        int comx = (int) Math.round(cx + (0 - midR) * scale);
        int comy = (int) Math.round(cy - (0 - midF) * scale);
        ConsoleDraw.circle(graphics, comx, comy, 7, BRASS);
        graphics.drawString(font, "VCC", comx + 11, comy - 3, BRASS, false);

        for (DevicePlot plot : plots) {
            int px = (int) Math.round(cx + (plot.right() - midR) * scale);
            int py = (int) Math.round(cy - (plot.forward() - midF) * scale);
            drawPlot(graphics, font, px, py, plot, state);
            LAST_PLOT_HITS.put(plot.id(), new int[] {px - 13, py - 13, 26, 26});
        }
    }

    private static void drawPlot(GuiGraphics graphics, Font font, int px, int py,
                                 DevicePlot plot, BoardState state) {
        int colour = WARN;
        String tag = "free";
        if (plot.slotKey() != null) {
            SlotSpec spec = state.airframe().slot(plot.slotKey());
            if (spec != null) {
                colour = spec.axis().color();
                tag = spec.shortLabel();
            }
        }
        boolean picked = plot.slotKey() != null && plot.slotKey().equals(state.selectedSlot());

        drawCategoryGlyph(graphics, px, py, plot.categoryOrdinal(), colour, plot.rpmFraction(),
                plot.slotKey() != null);

        if (plot.selected() || picked) {
            ConsoleDraw.outline(graphics, px - 13, py - 13, 26, 26, EDGE_BRIGHT);
        }
        graphics.drawString(font, tag, px - font.width(tag) / 2, py + 14, colour, false);
    }

    private static void drawCategoryGlyph(GuiGraphics graphics, int cx, int cy, int categoryOrdinal,
                                          int colour, float rpm, boolean assigned) {
        int fill = ConsoleDraw.blend(PANEL_DEEP, colour, assigned ? 0.30f : 0.12f);
        switch (categoryOrdinal) {
            case 0, 1 -> {
                ConsoleDraw.disc(graphics, cx, cy, 10, fill);
                ConsoleDraw.circle(graphics, cx, cy, 10, colour);
                for (int i = 0; i < 3; i++) {
                    double a = Math.toRadians(i * 120 + rpm * 220f);
                    ConsoleDraw.line(graphics, cx, cy,
                            cx + (int) (Math.cos(a) * 8), cy + (int) (Math.sin(a) * 8), colour);
                }
            }
            case 2 -> {
                graphics.fill(cx - 5, cy - 10, cx + 6, cy + 11, fill);
                ConsoleDraw.outline(graphics, cx - 5, cy - 10, 11, 21, colour);
                for (int t = -6; t <= 6; t += 6) {
                    graphics.fill(cx - 3, cy + t, cx + 4, cy + t + 1, colour);
                }
            }
            case 3 -> {
                graphics.fill(cx - 10, cy - 5, cx + 11, cy + 6, fill);
                ConsoleDraw.outline(graphics, cx - 10, cy - 5, 21, 11, colour);
                graphics.fill(cx - 10, cy - 5, cx + 11, cy - 4, ConsoleDraw.blend(colour, TEXT, 0.4f));
            }
            default -> {
                ConsoleDraw.disc(graphics, cx, cy, 5, colour);
                ConsoleDraw.circle(graphics, cx, cy, 9, ConsoleDraw.blend(colour, PANEL_DEEP, 0.4f));
            }
        }
    }

    private static void drawFixedWingStructure(GuiGraphics graphics, RigShapes.Project p) {
        for (int side : new int[] {-1, 1}) {
            int steps = 18;
            int[] xs = new int[steps * 2];
            int[] ys = new int[steps * 2];
            for (int i = 0; i < steps; i++) {
                double ax = RigShapes.SPAN_ROOT
                        + (RigShapes.SPAN_TIP - RigShapes.SPAN_ROOT) * i / (steps - 1.0);
                xs[i] = p.x(side * ax);
                ys[i] = p.y(RigShapes.wingLe(ax));
                xs[steps * 2 - 1 - i] = p.x(side * ax);
                ys[steps * 2 - 1 - i] = p.y(RigShapes.wingHinge(ax));
            }
            ConsoleDraw.polygon(graphics, xs, ys, SKIN);
            ConsoleDraw.polyOutline(graphics, xs, ys, SKIN_EDGE);

            double[][] gaps = {{RigShapes.SPAN_ROOT, 46}, {152, 172}, {292, RigShapes.SPAN_TIP}};
            for (double[] gap : gaps) {
                fillSpan(graphics, p, side, gap[0], gap[1], RigShapes::wingHinge, RigShapes::wingTe, SKIN);
            }
        }

        for (int side : new int[] {-1, 1}) {
            fillSpan(graphics, p, side, RigShapes.TAIL_ROOT, RigShapes.TAIL_TIP,
                    RigShapes::tailLe, RigShapes::tailHinge, SKIN);
            fillSpan(graphics, p, side, RigShapes.TAIL_ROOT, 30, RigShapes::tailHinge, RigShapes::tailTe, SKIN);
            fillSpan(graphics, p, side, 134, RigShapes.TAIL_TIP, RigShapes::tailHinge, RigShapes::tailTe, SKIN);
        }

        double[][] body = {{0, -304}, {9, -252}, {14, -150}, {14, 110}, {10, 232},
                {-10, 232}, {-14, 110}, {-14, -150}, {-9, -252}};
        polyFromDesign(graphics, p, body, SKIN_LIT, SKIN_EDGE);

        int cpx = p.x(0);
        int cpy = p.y(-196);
        int rx = Math.max(3, p.x(8) - p.x(0));
        int ry = Math.max(6, p.y(24) - p.y(0));
        for (int dy = -ry; dy <= ry; dy++) {
            int span = (int) (rx * Math.sqrt(Math.max(0.0, 1.0 - (double) dy * dy / ((double) ry * ry))));
            graphics.fill(cpx - span, cpy + dy, cpx + span + 1, cpy + dy + 1, GLASS);
        }

        polyFromDesign(graphics, p, new double[][] {{0, 132}, {8, 186}, {8, 214}, {-8, 214}, {-8, 186}},
                SKIN, SKIN_EDGE);

        for (int side : new int[] {-1, 1}) {
            polyFromDesign(graphics, p, new double[][] {{side * 132, -96}, {side * 168, -96},
                    {side * 168, -34}, {side * 132, -34}}, SKIN, SKIN_EDGE);
        }
    }

    private static void drawAirshipStructure(GuiGraphics graphics, RigShapes.Project p) {
        int steps = 44;
        int[] xs = new int[steps * 2];
        int[] ys = new int[steps * 2];
        for (int i = 0; i < steps; i++) {
            double sy = RigShapes.ENV_NOSE + (RigShapes.ENV_TAIL - RigShapes.ENV_NOSE) * i / (steps - 1.0);
            double hw = RigShapes.envelopeHalf(sy);
            xs[i] = p.x(-hw);
            ys[i] = p.y(sy);
            xs[steps * 2 - 1 - i] = p.x(hw);
            ys[steps * 2 - 1 - i] = p.y(sy);
        }
        ConsoleDraw.polygon(graphics, xs, ys, SKIN);
        ConsoleDraw.polyOutline(graphics, xs, ys, SKIN_EDGE);

        for (double frac : new double[] {-0.66, -0.34, 0.34, 0.66}) {
            int prevX = Integer.MIN_VALUE;
            int prevY = 0;
            for (int i = 0; i < steps; i++) {
                double sy = RigShapes.ENV_NOSE + 14
                        + (RigShapes.ENV_TAIL - RigShapes.ENV_NOSE - 28) * i / (steps - 1.0);
                int gx = p.x(RigShapes.envelopeHalf(sy) * frac);
                int gy = p.y(sy);
                if (prevX != Integer.MIN_VALUE) ConsoleDraw.line(graphics, prevX, prevY, gx, gy, SKIN_EDGE);
                prevX = gx;
                prevY = gy;
            }
        }

        polyFromDesign(graphics, p, new double[][] {{-34, 26}, {34, 26}, {30, 158}, {-30, 158}},
                SKIN_LIT, SKIN_EDGE);
        for (int i = 0; i < 4; i++) {
            double wy = 42 + i * 27;
            graphics.fill(p.x(-22), p.y(wy), p.x(22), p.y(wy + 11), GLASS);
        }

        for (int side : new int[] {-1, 1}) {
            polyFromDesign(graphics, p, new double[][] {{side * 120, 34}, {side * 158, 34},
                    {side * 158, 56}, {side * 120, 56}}, SKIN, SKIN_EDGE);

            polyFromDesign(graphics, p, new double[][] {{side * 38, 212}, {side * 102, 222},
                    {side * 102, 258}, {side * 38, 250}}, SKIN, SKIN_EDGE);
        }
        polyFromDesign(graphics, p, new double[][] {{-17, 204}, {17, 204}, {17, 250}, {-17, 250}},
                SKIN, SKIN_EDGE);
    }

    private static void drawGroundStructure(GuiGraphics graphics, RigShapes.Project p) {
        for (int side : new int[] {-1, 1}) {
            polyFromDesign(graphics, p, new double[][] {{side * 58, -240}, {side * 86, -240},
                    {side * 86, 240}, {side * 58, 240}}, SKIN, SKIN_EDGE);
        }
        polyFromDesign(graphics, p, new double[][] {{-96, -254}, {96, -254}, {106, -198},
                {106, 212}, {88, 254}, {-88, 254}, {-106, 212}, {-106, -198}}, SKIN_LIT, SKIN_EDGE);

        polyFromDesign(graphics, p, new double[][] {{-70, -208}, {70, -208}, {64, -112}, {-64, -112}},
                GLASS, SKIN_EDGE);
        graphics.fill(p.x(-78), p.y(-86), p.x(78), p.y(228), 0xFF18232E);
        for (int i = 0; i < 6; i++) {
            double gy = -72 + i * 50;
            graphics.fill(p.x(-72), p.y(gy), p.x(72), p.y(gy + 3), 0xFF212F3D);
        }
        for (int side : new int[] {-1, 1}) {
            polyFromDesign(graphics, p, new double[][] {{side * 86, -198}, {side * 106, -198},
                    {side * 106, 212}, {side * 86, 212}}, SKIN, SKIN_EDGE);

            for (int ay : RigShapes.AXLE_Y) {
                polyFromDesign(graphics, p, new double[][] {{side * 96, ay - 44}, {side * 126, ay - 40},
                        {side * 126, ay + 40}, {side * 96, ay + 44}}, 0xFF16202B, SKIN_EDGE);
            }

            graphics.fill(p.x(side * 44 - 14), p.y(-250), p.x(side * 44 + 14), p.y(-240), 0xFFD4C896);
        }
    }

    private static void drawSlot(GuiGraphics graphics, Font font, RigShapes.SlotShape shape,
                                 SlotSpec spec, SlotView view, BoardState state,
                                 boolean selected, boolean hovered) {
        int axisColour = spec.axis().color();
        int body;
        int line;
        int labelColour;
        switch (view.state()) {
            case EMPTY -> {
                body = PANEL_DEEP;
                line = WARN;
                labelColour = WARN;
            }
            case FAULT -> {
                body = ConsoleDraw.blend(PANEL_DEEP, BAD, 0.22f);
                line = BAD;
                labelColour = BAD;
            }
            default -> {
                float lit = Math.max(0.05f, Math.min(0.62f, 0.22f + 0.34f * view.deflection()));
                body = ConsoleDraw.blend(PANEL_DEEP, axisColour, lit);
                line = axisColour;
                labelColour = axisColour;
            }
        }
        if (hovered) line = EDGE_BRIGHT;

        switch (shape.kind()) {
            case DISC -> drawDisc(graphics, font, shape, spec, view, labelColour, axisColour);
            case BLADE -> drawBlade(graphics, font, shape, spec, view, body, line, labelColour);
            case WHEEL -> drawWheel(graphics, font, shape, spec, view, state, body, line, labelColour);
            default -> drawPanel(graphics, font, shape, spec, view, body, line, labelColour);
        }

        if (selected) outlineOf(graphics, shape, EDGE_BRIGHT);
    }

    private static void drawPanel(GuiGraphics graphics, Font font, RigShapes.SlotShape shape,
                                  SlotSpec spec, SlotView view, int body, int line, int labelColour) {
        int[] xs = shape.xs().clone();
        int[] ys = shape.ys().clone();

        if (view.state() == SlotView.State.FILLED && Math.abs(view.deflection()) > 0.01f) {
            double k = 1.0 - 0.45 * Math.abs(view.deflection());
            xs[2] = (int) Math.round(xs[1] + (xs[2] - xs[1]) * k);
            ys[2] = (int) Math.round(ys[1] + (ys[2] - ys[1]) * k);
            xs[3] = (int) Math.round(xs[0] + (xs[3] - xs[0]) * k);
            ys[3] = (int) Math.round(ys[0] + (ys[3] - ys[0]) * k);
        }

        ConsoleDraw.polygon(graphics, xs, ys, body);
        ConsoleDraw.polyOutline(graphics, xs, ys, line);
        if (view.state() == SlotView.State.EMPTY) ConsoleDraw.polyDashed(graphics, xs, ys, WARN);

        ConsoleDraw.line(graphics, xs[0], ys[0], xs[1], ys[1], ConsoleDraw.blend(line, TEXT, 0.35f));

        int depth = maxY(ys) - minY(ys);
        String label = spec.shortLabel();
        if (depth >= 26) {
            centred(graphics, font, label, shape.anchorX(), shape.anchorY() - 7, labelColour);
            if (view.state() == SlotView.State.FILLED && Math.abs(view.deflection()) > 0.01f) {
                String deg = String.format(java.util.Locale.ROOT, "%+.0f deg", view.deflection() * 24f);
                centred(graphics, font, deg, shape.anchorX(), shape.anchorY() + 3,
                        ConsoleDraw.blend(labelColour, TEXT, 0.4f));
            }
        } else {
            centred(graphics, font, label, shape.anchorX(), shape.anchorY() - 3, labelColour);
        }
        if (view.state() == SlotView.State.FAULT) {
            graphics.fill(maxX(xs) - 9, minY(ys) + 2, maxX(xs) - 3, minY(ys) + 8, BAD);
        }
    }

    private static void drawDisc(GuiGraphics graphics, Font font, RigShapes.SlotShape shape,
                                 SlotSpec spec, SlotView view, int labelColour, int axisColour) {
        boolean live = view.state() == SlotView.State.FILLED;
        int cx = shape.anchorX();
        int cy = shape.anchorY();
        int r = shape.radius();
        int colour = live ? axisColour : TEXT_FAINT;

        ConsoleDraw.disc(graphics, cx, cy, r, ConsoleDraw.blend(PANEL_DEEP, colour, 0.10f));
        ConsoleDraw.circle(graphics, cx, cy, r, live ? ConsoleDraw.blend(colour, EDGE, 0.35f) : EDGE);
        for (int i = 0; i < 3; i++) {
            double a = Math.toRadians(i * 120 + view.rpmFraction() * 220f);
            ConsoleDraw.thickLine(graphics, cx, cy,
                    cx + (int) (Math.cos(a) * (r - 3)), cy + (int) (Math.sin(a) * (r - 3)),
                    2, ConsoleDraw.blend(PANEL_DEEP, colour, live ? 0.8f : 0.3f));
        }

        if (live && view.rpmFraction() > 0.05f) {
            for (int ring = 0; ring < 2; ring++) {
                ConsoleDraw.circle(graphics, cx, cy, r - 2 - ring * 3,
                        ConsoleDraw.blend(PANEL_DEEP, colour, 0.5f - ring * 0.18f));
            }
        }
        ConsoleDraw.disc(graphics, cx, cy, 2, colour);
        if (view.state() == SlotView.State.EMPTY) {
            ConsoleDraw.circle(graphics, cx, cy, r, WARN);
        }
        centred(graphics, font, spec.shortLabel(), cx, cy + r + 4,
                view.state() == SlotView.State.EMPTY ? WARN : labelColour);
    }

    private static void drawBlade(GuiGraphics graphics, Font font, RigShapes.SlotShape shape,
                                  SlotSpec spec, SlotView view, int body, int line, int labelColour) {
        int swing = Math.round(view.deflection() * 22f);
        int[] xs = shape.xs().clone();
        int[] ys = shape.ys().clone();
        xs[2] += swing;
        xs[3] += swing;

        ConsoleDraw.polygon(graphics, xs, ys, body);
        ConsoleDraw.polyOutline(graphics, xs, ys, line);
        if (view.state() == SlotView.State.EMPTY) ConsoleDraw.polyDashed(graphics, xs, ys, WARN);
        if (view.state() == SlotView.State.FAULT) {
            graphics.fill(maxX(xs) - 7, minY(ys) + 2, maxX(xs) - 1, minY(ys) + 8, BAD);
        }

        String label = spec.shortLabel();
        int lx = shape.side() > 0 ? shape.anchorX() + 14 : shape.anchorX() - 14 - font.width(label);
        graphics.drawString(font, label, lx, shape.anchorY() - 3, labelColour, false);
    }

    private static void drawWheel(GuiGraphics graphics, Font font, RigShapes.SlotShape shape,
                                  SlotSpec spec, SlotView view, BoardState state,
                                  int body, int line, int labelColour) {
        double ang = shape.steered() ? Math.toRadians(state.steer() * 26.0) : 0.0;
        double ca = Math.cos(ang);
        double sa = Math.sin(ang);
        int cx = shape.anchorX();
        int cy = shape.anchorY();
        int hw = (shape.xs()[1] - shape.xs()[0]) / 2;
        int hh = (shape.ys()[2] - shape.ys()[0]) / 2;

        int[] xs = new int[4];
        int[] ys = new int[4];
        int[][] corners = {{-hw, -hh}, {hw, -hh}, {hw, hh}, {-hw, hh}};
        for (int i = 0; i < 4; i++) {
            xs[i] = (int) Math.round(cx + corners[i][0] * ca - corners[i][1] * sa);
            ys[i] = (int) Math.round(cy + corners[i][0] * sa + corners[i][1] * ca);
        }
        ConsoleDraw.polygon(graphics, xs, ys, body);
        ConsoleDraw.polyOutline(graphics, xs, ys, line);
        if (view.state() == SlotView.State.EMPTY) {
            ConsoleDraw.polyDashed(graphics, xs, ys, WARN);
        } else {
            double phase = state.driveScroll() % 18.0;
            for (double t = -hh + 4 + phase; t < hh - 4; t += 18.0) {
                int ax = (int) Math.round(cx + (-hw + 3) * ca - t * sa);
                int ay = (int) Math.round(cy + (-hw + 3) * sa + t * ca);
                int bx = (int) Math.round(cx + (hw - 3) * ca - t * sa);
                int by = (int) Math.round(cy + (hw - 3) * sa + t * ca);
                ConsoleDraw.line(graphics, ax, ay, bx, by, ConsoleDraw.blend(body, line, 0.5f));
            }
        }
        centred(graphics, font, spec.shortLabel(), cx, cy - 3, labelColour);
    }

    private static void fillSpan(GuiGraphics graphics, RigShapes.Project p, int side,
                                 double a, double b,
                                 java.util.function.DoubleUnaryOperator front,
                                 java.util.function.DoubleUnaryOperator back, int colour) {
        int[] xs = {p.x(side * a), p.x(side * b), p.x(side * b), p.x(side * a)};
        int[] ys = {p.y(front.applyAsDouble(a)), p.y(front.applyAsDouble(b)),
                p.y(back.applyAsDouble(b)), p.y(back.applyAsDouble(a))};
        ConsoleDraw.polygon(graphics, xs, ys, colour);
        ConsoleDraw.polyOutline(graphics, xs, ys, SKIN_EDGE);
    }

    private static void polyFromDesign(GuiGraphics graphics, RigShapes.Project p,
                                       double[][] pts, int fill, int outline) {
        int[] xs = new int[pts.length];
        int[] ys = new int[pts.length];
        for (int i = 0; i < pts.length; i++) {
            xs[i] = p.x(pts[i][0]);
            ys[i] = p.y(pts[i][1]);
        }
        ConsoleDraw.polygon(graphics, xs, ys, fill);
        ConsoleDraw.polyOutline(graphics, xs, ys, outline);
    }

    static void outlineOf(GuiGraphics graphics, RigShapes.SlotShape shape, int colour) {
        if (shape.kind() == RigShapes.Kind.DISC) {
            ConsoleDraw.circle(graphics, shape.anchorX(), shape.anchorY(), shape.radius() + 3, colour);
            return;
        }
        int x0 = minY(shape.xs());
        int x1 = maxX(shape.xs());
        int y0 = minY(shape.ys());
        int y1 = maxY(shape.ys());
        ConsoleDraw.outline(graphics, x0 - 4, y0 - 4, x1 - x0 + 8, y1 - y0 + 8, colour);
    }

    private static void centred(GuiGraphics graphics, Font font, String text, int cx, int y, int colour) {
        graphics.drawString(font, text, cx - font.width(text) / 2, y, colour, false);
    }

    private static int minY(int[] v) {
        int m = Integer.MAX_VALUE;
        for (int i : v) m = Math.min(m, i);
        return m;
    }

    private static int maxY(int[] v) {
        int m = Integer.MIN_VALUE;
        for (int i : v) m = Math.max(m, i);
        return m;
    }

    private static int maxX(int[] v) {
        return maxY(v);
    }

    public static int axisColour(ControlAxis axis) {
        return axis.color();
    }
}
