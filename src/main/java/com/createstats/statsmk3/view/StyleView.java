package com.createstats.statsmk3.view;

import com.createstats.statsmk3.Mk3Settings;
import com.createstats.statsmk3.ui.PanelSurface;
import com.createstats.statsmk3.ui.Rect;
import com.createstats.statsmk3.ui.Scroll;
import com.createstats.statsmk3.ui.Segmented;
import com.createstats.statsmk3.ui.Shapes;
import com.createstats.statsmk3.ui.Slider;
import com.createstats.statsmk3.ui.Text;
import com.createstats.statsmk3.ui.Toggle;
import com.createstats.statsmk3.ui.Tokens;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class StyleView extends StudioView {
    private static final int[] ACCENTS = {
            0xFFFF5C90, 0xFF3B82F6, 0xFF34D399, 0xFFFBBF24, 0xFF8B5CF6, 0xFFE4622A
    };
    private static final int[] GROUNDS = {0xFF05070A, 0xFF0C1016, 0xFF101820, 0xFF160F14};
    private static final float[] DENSITIES = {0.85f, 1.0f, 1.18f};

    private static final float ROW_H = 40.0f;

    private static final int ROW_ACCENT = 0;
    private static final int ROW_GROUND = 1;
    private static final int ROW_GRID = 2;
    private static final int ROW_GLASS = 3;
    private static final int ROW_GLOW = 4;
    private static final int ROW_DENSITY = 5;
    private static final int ROW_FACE_SCALE = 6;
    private static final int ROW_LAMPS = 7;
    private static final int ROW_COUNT = 8;

    private final Scroll scroll = new Scroll();

    private Rect rowsArea = Rect.EMPTY;

    private final java.util.Map<Integer, com.createstats.statsmk3.ui.Widget> rowWidgets =
            new java.util.HashMap<>();

    public StyleView(StudioContext ctx) {
        super(ctx);
    }

    @Override
    public String id() {
        return "style";
    }

    @Override
    public String label() {
        return "Style";
    }

    @Override
    public void drawIcon(PanelSurface c, float cx, float cy, int color) {
        Shapes.circle(c, cx, cy, 6.0f, Tokens.alpha(color, 0.28f));
        Shapes.circle(c, cx - 2.0f, cy - 1.5f, 1.8f, color);
        Shapes.circle(c, cx + 2.5f, cy + 1.0f, 1.8f, color);
    }

    @Override
    protected void build() {
        this.rowsArea = this.area.inset(0.0f, 46.0f, 0.0f, 0.0f);
        this.rowWidgets.clear();
        this.scroll.setMetrics(ROW_COUNT * ROW_H, this.rowsArea.h);

        final Toggle grid = new Toggle(() -> this.ctx.settings().grid, v -> {
            this.ctx.settings().grid = v;
            this.ctx.commit();
        });
        this.rowWidgets.put(ROW_GRID, grid);
        this.widgets.add(grid);

        final Toggle glass = new Toggle(() -> this.ctx.settings().glass, v -> {
            this.ctx.settings().glass = v;
            this.ctx.commit();
        });
        this.rowWidgets.put(ROW_GLASS, glass);
        this.widgets.add(glass);

        final Slider glow = new Slider(() -> this.ctx.settings().glow, v -> {
            this.ctx.settings().glow = v;
            this.ctx.commit();
        });
        this.rowWidgets.put(ROW_GLOW, glow);
        this.widgets.add(glow);

        final Segmented density = new Segmented(
                new String[]{"Compact", "Normal", "Roomy"},
                () -> densityIndex(this.ctx.settings().density),
                i -> {
                    this.ctx.settings().density = DENSITIES[i];
                    this.ctx.commit();
                });
        this.rowWidgets.put(ROW_DENSITY, density);
        this.widgets.add(density);

        final Segmented faceScale = new Segmented(
                Mk3Settings.FACE_SCALE_LABELS,
                () -> Mk3Settings.faceScaleIndex(this.ctx.settings().faceScale),
                i -> {
                    this.ctx.settings().faceScale = Mk3Settings.FACE_SCALES[i];
                    this.ctx.commit();
                });
        this.rowWidgets.put(ROW_FACE_SCALE, faceScale);
        this.widgets.add(faceScale);

        placeWidgets();
    }

    // re-place every frame, rows move
    private void placeWidgets() {
        for (final java.util.Map.Entry<Integer, com.createstats.statsmk3.ui.Widget> e
                : this.rowWidgets.entrySet()) {
            final Rect row = rowRect(e.getKey());
            final com.createstats.statsmk3.ui.Widget w = e.getValue();
            if (w instanceof Toggle) {
                w.setBounds(new Rect(row.right() - Toggle.WIDTH,
                        row.centerY() - Toggle.HEIGHT * 0.5f, Toggle.WIDTH, Toggle.HEIGHT));
            } else if (w instanceof Slider) {
                w.setBounds(new Rect(row.right() - 150.0f, row.centerY() - Slider.HEIGHT * 0.5f,
                        150.0f, Slider.HEIGHT));
            } else {
                w.setBounds(new Rect(row.right() - 180.0f, row.centerY() - 13.0f, 180.0f, 26.0f));
            }

            w.setVisible(rowVisible(row));
        }
    }

    @Override
    public void update(float dt) {
        super.update(dt);
        this.scroll.setMetrics(ROW_COUNT * ROW_H, this.rowsArea.h);
        this.scroll.update(dt);
        placeWidgets();
    }

    private static int densityIndex(float d) {
        int best = 1;
        float bestDelta = Float.MAX_VALUE;
        for (int i = 0; i < DENSITIES.length; i++) {
            final float delta = Math.abs(DENSITIES[i] - d);
            if (delta < bestDelta) {
                bestDelta = delta;
                best = i;
            }
        }
        return best;
    }

    private Rect rowRect(int index) {
        return new Rect(this.rowsArea.x, this.rowsArea.y + index * ROW_H - this.scroll.value(),
                this.rowsArea.w - (this.scroll.scrollable() ? 8.0f : 0.0f), ROW_H);
    }

    private boolean rowVisible(Rect row) {
        return row.bottom() > this.rowsArea.y && row.y < this.rowsArea.bottom();
    }

    private Rect track() {
        return new Rect(this.rowsArea.right() - 3.0f, this.rowsArea.y, 3.0f, this.rowsArea.h);
    }

    @Override
    public void draw(PanelSurface c) {
        c.text("Style", this.area.x, this.area.y, Tokens.INK, Text.SUB);
        c.text("Good, caution and critical stay reserved and never follow the accent.",
                this.area.x, this.area.y + 20.0f, Tokens.INK_3, Text.META);

        c.clip(this.rowsArea);
        super.draw(c);
        c.unclip();

        this.scroll.drawBar(c, track());
    }

    @Override
    protected void drawContent(PanelSurface c) {
        drawRow(c, ROW_ACCENT, "Accent", "Structure only: active step, focus ring, primary action");
        drawSwatches(c, rowRect(ROW_ACCENT), ACCENTS, this.ctx.settings().accent);

        drawRow(c, ROW_GROUND, "Ground", "The panel surface behind the data");
        drawSwatches(c, rowRect(ROW_GROUND), GROUNDS, this.ctx.settings().ground);

        drawRow(c, ROW_GRID, "Grid", "Recessive rules behind plots");
        drawRow(c, ROW_GLASS, "Glass", "Specular sweep across the cover");
        drawRow(c, ROW_GLOW, "Emission", "How much the face glows in a dark room");
        drawRow(c, ROW_DENSITY, "Density", "Padding and type scale on the face");
        drawRow(c, ROW_FACE_SCALE, "Screen scale", faceScaleHint());
        drawRow(c, ROW_LAMPS, "Alert lamps", lampHint());
        drawLamps(c, rowRect(ROW_LAMPS));
    }

    private String faceScaleHint() {
        final int pct = Math.round(this.ctx.settings().faceScale * 100.0f);
        return "Size of everything drawn on the block face, now " + pct + "%";
    }

    private void drawLamps(PanelSurface c, Rect row) {
        if (!rowVisible(row)) {
            return;
        }
        final String[] bound = this.ctx.settings().lampChannels;
        for (int i = 0; i < bound.length; i++) {
            final Rect slot = lampSlot(row, i);
            final boolean isBound = bound[i] != null && !bound[i].isEmpty();
            final int color = isBound
                    ? Tokens.seriesFor(bound[i])
                    : Tokens.LINE_HI;
            c.card(slot, slot.h * 0.5f, Tokens.alpha(color, isBound ? 0.16f : 0.06f),
                    Tokens.alpha(color, isBound ? 0.55f : 0.30f));
            Shapes.circle(c, slot.centerX(), slot.centerY(), 4.0f, color);
        }
    }

    private String lampHint() {
        final Rect row = rowRect(ROW_LAMPS);
        final String[] bound = this.ctx.settings().lampChannels;
        for (int i = 0; i < bound.length; i++) {
            if (!lampSlot(row, i).contains(this.hoverX, this.hoverY)) {
                continue;
            }
            if (bound[i] == null || bound[i].isEmpty()) {
                return "Lamp " + (i + 1) + ": follows signal " + (i + 1) + ". Click to bind.";
            }
            final var ch = this.ctx.panel.getChannel(bound[i]);
            return "Lamp " + (i + 1) + ": " + (ch == null ? bound[i] : ch.getLabel())
                    + ". Right click to clear.";
        }
        return "Six LEDs on the bezel. Click one to bind the selected signal.";
    }

    private static Rect lampSlot(Rect row, int index) {
        final float size = 22.0f;
        final float gap = 6.0f;
        final float x = row.right() - (6 * size + 5 * gap) + index * (size + gap);
        return new Rect(x, row.centerY() - size * 0.5f, size, size);
    }

    private double hoverX = -1.0;
    private double hoverY = -1.0;

    @Override
    public boolean mouseMoved(double mx, double my) {
        this.hoverX = mx;
        this.hoverY = my;
        return super.mouseMoved(mx, my);
    }

    private void drawRow(PanelSurface c, int index, String name, String hint) {
        final Rect r = rowRect(index);
        if (!rowVisible(r)) {
            return;
        }
        c.textV(name, r.x, r.centerY() - 5.0f, Tokens.INK, Text.META);
        c.textV(hint, r.x, r.centerY() + 5.0f, Tokens.INK_3, Text.MICRO);
        c.hairlineH(r.x, r.bottom() - 1.0f, r.w, Tokens.LINE);
    }

    private void drawSwatches(PanelSurface c, Rect row, int[] colors, int current) {
        if (!rowVisible(row)) {
            return;
        }
        final float size = 22.0f;
        final float gap = 6.0f;
        float x = row.right() - (colors.length * size + (colors.length - 1) * gap);
        for (final int color : colors) {
            final Rect r = new Rect(x, row.centerY() - size * 0.5f, size, size);
            Shapes.roundedRect(c, r, 6.0f, color);
            if (color == current) {
                Shapes.roundedRectOutline(c, r.inset(-3.0f), 9.0f, 1.5f, Tokens.INK);
            }
            x += size + gap;
        }
    }

    @Override
    protected boolean onScroll(double mx, double my, double amount) {
        return this.rowsArea.contains(mx, my) && this.scroll.wheel(amount);
    }

    @Override
    protected boolean onMouseDown(double mx, double my, int button) {
        if (this.scroll.pressBar(track(), mx, my)) {
            return true;
        }
        if (!this.rowsArea.contains(mx, my)) {
            return false;
        }
        if (button == 0 || button == 1) {
            final Rect lampRow = rowRect(ROW_LAMPS);
            final String[] bound = this.ctx.settings().lampChannels;
            for (int i = 0; i < bound.length; i++) {
                if (!rowVisible(lampRow) || !lampSlot(lampRow, i).contains(mx, my)) {
                    continue;
                }
                bound[i] = button == 1 ? "" : this.ctx.focusedChannelId;
                this.ctx.commit();
                return true;
            }
        }
        if (button != 0) {
            return false;
        }
        final Integer accent = swatchAt(rowRect(ROW_ACCENT), ACCENTS, mx, my);
        if (accent != null) {
            this.ctx.settings().accent = accent;
            this.ctx.commit();
            return true;
        }
        final Integer ground = swatchAt(rowRect(ROW_GROUND), GROUNDS, mx, my);
        if (ground != null) {
            this.ctx.settings().ground = ground;
            this.ctx.commit();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (this.scroll.dragBar(track(), my)) {
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseUp(double mx, double my, int button) {
        this.scroll.endDrag();
        return super.mouseUp(mx, my, button);
    }

    private Integer swatchAt(Rect row, int[] colors, double mx, double my) {
        if (!rowVisible(row)) {
            return null;
        }
        final float size = 22.0f;
        final float gap = 6.0f;
        float x = row.right() - (colors.length * size + (colors.length - 1) * gap);
        for (final int color : colors) {
            if (new Rect(x, row.centerY() - size * 0.5f, size, size).contains(mx, my)) {
                return color;
            }
            x += size + gap;
        }
        return null;
    }
}
