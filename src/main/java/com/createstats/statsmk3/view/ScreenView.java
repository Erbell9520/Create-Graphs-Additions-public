package com.createstats.statsmk3.view;

import java.util.Locale;

import com.createstats.client.DisplayPrefs;
import com.createstats.statsmk3.paint.Lod;
import com.createstats.statsmk3.paint.PanelModel;
import com.createstats.statsmk3.paint.PanelPainter;
import com.createstats.statsmk3.ui.PanelSurface;
import com.createstats.statsmk3.ui.Rect;
import com.createstats.statsmk3.ui.Segmented;
import com.createstats.statsmk3.ui.Shapes;
import com.createstats.statsmk3.ui.Slider;
import com.createstats.statsmk3.ui.Text;
import com.createstats.statsmk3.ui.Toggle;
import com.createstats.statsmk3.ui.Tokens;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

// tell the player what changes with distance, it used to just happen
@OnlyIn(Dist.CLIENT)
public final class ScreenView extends StudioView {
    private float previewDistance = 6.0f;

    private Rect bandArea = Rect.EMPTY;
    private Rect previewArea = Rect.EMPTY;
    private Rect controlsArea = Rect.EMPTY;
    private boolean draggingMarker;

    public ScreenView(StudioContext ctx) {
        super(ctx);
    }

    @Override
    public String id() {
        return "screen";
    }

    @Override
    public String label() {
        return "Screen";
    }

    @Override
    public void drawIcon(PanelSurface c, float cx, float cy, int color) {
        Shapes.arc(c, cx, cy + 3.0f, 6.5f, 1.2f, -2.6f, 1.4f, color);
        Shapes.arc(c, cx, cy - 3.0f, 6.5f, 1.2f, 0.55f, 1.4f, color);
        Shapes.circle(c, cx, cy, 2.1f, color);
    }

    @Override
    public boolean usesDock() {
        return false;
    }

    @Override
    protected void build() {
        final Rect body = this.area.inset(0.0f, 46.0f, 0.0f, 0.0f);

        this.bandArea = body.top(58.0f);
        final Rect rest = body.below(58.0f + Tokens.S4);

        final float previewW = Math.min(rest.w * 0.42f, rest.h * 1.05f);
        this.previewArea = rest.left(previewW);
        this.controlsArea = rest.afterLeft(previewW + Tokens.S5);

        buildControls();
    }

    private void buildControls() {
        final DisplayPrefs.Simplify[] modes = DisplayPrefs.Simplify.values();
        final String[] labels = new String[modes.length];
        for (int i = 0; i < modes.length; i++) {
            labels[i] = modes[i].label();
        }

        final Rect simplifyRow = controlRow(0);
        final Segmented simplify = new Segmented(labels,
                () -> DisplayPrefs.simplify().ordinal(),
                i -> DisplayPrefs.setSimplify(modes[i]));
        simplify.setBounds(new Rect(simplifyRow.x, simplifyRow.bottom() - 30.0f,
                Math.min(simplifyRow.w, 320.0f), 26.0f));
        this.widgets.add(simplify);

        final Rect fadeRow = controlRow(1);
        final Toggle fade = new Toggle(DisplayPrefs::crossFade, DisplayPrefs::setCrossFade);
        fade.setBounds(new Rect(fadeRow.right() - Toggle.WIDTH,
                fadeRow.centerY() - Toggle.HEIGHT * 0.5f, Toggle.WIDTH, Toggle.HEIGHT));
        this.widgets.add(fade);

        final Rect headerRow = controlRow(2);
        final Toggle header = new Toggle(DisplayPrefs::keepHeader, DisplayPrefs::setKeepHeader);
        header.setBounds(new Rect(headerRow.right() - Toggle.WIDTH,
                headerRow.centerY() - Toggle.HEIGHT * 0.5f, Toggle.WIDTH, Toggle.HEIGHT));
        this.widgets.add(header);

        final Rect renderRow = controlRow(3);
        final Slider render = new Slider(
                () -> (DisplayPrefs.maxRenderDistance() - 16.0f) / 112.0f,
                v -> DisplayPrefs.setMaxRenderDistance(Math.round(16.0f + v * 112.0f)));
        render.setBounds(new Rect(renderRow.right() - 170.0f,
                renderRow.centerY() - Slider.HEIGHT * 0.5f, 170.0f, Slider.HEIGHT));
        this.widgets.add(render);
    }

    private static final float CONTROL_ROW_H = 54.0f;

    private Rect controlRow(int index) {
        return new Rect(this.controlsArea.x, this.controlsArea.y + 22.0f + index * CONTROL_ROW_H,
                this.controlsArea.w, CONTROL_ROW_H - 6.0f);
    }

    @Override
    protected void drawContent(PanelSurface c) {
        final int mbW = Math.max(1, this.ctx.panel.getMultiblockWidth());
        final int mbH = Math.max(1, this.ctx.panel.getMultiblockHeight());
        final float full = DisplayPrefs.fullRangeFor(mbW, mbH);
        final float glyph = DisplayPrefs.glyphRangeFor(mbW, mbH);

        c.text("Screen", this.area.x, this.area.y, Tokens.INK, Text.SUB);
        c.text(summary(mbW, mbH, full), this.area.x, this.area.y + 20.0f, Tokens.INK_3, Text.META);

        drawBands(c, full, glyph);
        drawPreview(c, mbW, mbH);
        drawControls(c, full, glyph);
    }

    private String summary(int mbW, int mbH, float full) {
        if (Float.isInfinite(full)) {
            return "This " + mbW + " x " + mbH + " panel never simplifies. It draws every "
                    + "instrument at any range it renders at.";
        }
        return "This " + mbW + " x " + mbH + " panel holds full detail out to "
                + metres(full) + ", because a larger face carries larger type.";
    }

    private void drawBands(PanelSurface c, float full, float glyph) {
        final Rect a = this.bandArea;
        if (a.isEmpty()) {
            return;
        }
        final float renderRange = DisplayPrefs.maxRenderDistance();
        final Rect track = new Rect(a.x, a.y + 20.0f, a.w, 18.0f);
        c.roundedRect(track, 4.0f, Tokens.CANVAS);

        final float fullEnd = Math.min(full, renderRange);
        final float glyphEnd = Math.min(glyph, renderRange);

        band(c, track, 0.0f, fullEnd, renderRange, Tokens.alpha(Tokens.GOOD, 0.55f));
        band(c, track, fullEnd, glyphEnd, renderRange, Tokens.alpha(Tokens.CAUTION, 0.45f));
        band(c, track, glyphEnd, renderRange, renderRange, Tokens.alpha(Tokens.INK_3, 0.40f));

        c.text(Text.track("Detail by distance"), a.x, a.y, Tokens.INK_3, Text.MICRO);
        c.textRight("renders to " + (int) renderRange + " m", a.right(), a.y,
                Tokens.INK_3, Text.MICRO);

        if (full < renderRange) {
            tick(c, track, full, renderRange, metres(full), Tokens.GOOD);
        }
        if (glyph < renderRange) {
            tick(c, track, glyph, renderRange, metres(glyph), Tokens.CAUTION);
        }

        final float mx = track.x + track.w * clamp01(this.previewDistance / renderRange);
        Shapes.roundedRect(c, new Rect(mx - 1.5f, track.y - 5.0f, 3.0f, track.h + 10.0f),
                1.5f, Tokens.INK);
        c.textCenter(metres(this.previewDistance), mx, track.bottom() + 8.0f, Tokens.INK,
                Text.MICRO);
    }

    private void band(PanelSurface c, Rect track, float from, float to, float range, int color) {
        if (to <= from || range <= 0.0f) {
            return;
        }
        final float x0 = track.x + track.w * clamp01(from / range);
        final float x1 = track.x + track.w * clamp01(to / range);
        Shapes.rect(c, x0, track.y, Math.max(1.0f, x1 - x0), track.h, color);
    }

    private void tick(PanelSurface c, Rect track, float at, float range, String label, int color) {
        final float x = track.x + track.w * clamp01(at / range);
        Shapes.rect(c, x - 0.5f, track.y - 3.0f, 1.0f, track.h + 6.0f, Tokens.fade(color, 0.9f));
        c.textCenter(label, x, track.y - 14.0f, Tokens.fade(color, 0.9f), Text.MICRO);
    }

    private void drawPreview(PanelSurface c, int mbW, int mbH) {
        final Rect a = this.previewArea;
        if (a.isEmpty()) {
            return;
        }
        c.text(Text.track("From " + metres(this.previewDistance)), a.x, a.y, Tokens.INK_3,
                Text.MICRO);

        final Rect frame = a.inset(0.0f, 16.0f, 0.0f, 0.0f);
        final float aspect = mbW / (float) mbH;
        float w = frame.w;
        float h = w / aspect;
        if (h > frame.h) {
            h = frame.h;
            w = h * aspect;
        }
        final Rect bezel = new Rect(frame.centerX() - w * 0.5f, frame.y, w, h);
        if (bezel.h <= 24.0f) {
            return;
        }

        c.roundedRect(bezel, Tokens.R_CARD, 0xFF20242C);
        c.roundedRectOutline(bezel, Tokens.R_CARD, 1.0f, 0xFF39414E);
        final Rect face = bezel.inset(7.0f);
        if (face.isEmpty()) {
            return;
        }

        final PanelModel model = this.ctx.currentModel;

        final Lod lod = Lod.forView(this.previewDistance * (double) this.previewDistance, mbW, mbH,
                this.ctx.settings().faceScale);
        if (model != null) {
            c.clip(face);
            PanelPainter.paint(c, face, model, lod);
            c.unclip();
        }

        c.text(levelName(lod), a.x, bezel.bottom() + Tokens.S2, Tokens.INK_2, Text.META);
        c.text(levelDetail(lod), a.x, bezel.bottom() + Tokens.S2 + 13.0f, Tokens.INK_3, Text.MICRO);
    }

    private static String levelName(Lod lod) {
        if (lod.isGlyph()) {
            return "Status glyph";
        }
        if (lod.collapsesToPrimary()) {
            return "Reduced";
        }
        return lod.showsHero() ? "Full, simplifying" : "Full detail";
    }

    private static String levelDetail(Lod lod) {
        if (lod.isGlyph()) {
            return lod.showsHeader()
                    ? "Status colour, one magnitude bar, and the title band"
                    : "Status colour and one magnitude bar";
        }
        if (lod.collapsesToPrimary()) {
            return "Title, status, the oversized readout and your largest tile";
        }
        if (lod.showsHero()) {
            return "Labels fading out, the oversized readout growing in";
        }
        return "Every tile, its labels, gridlines and " + lod.historyBudget() + " history points";
    }

    private void drawControls(PanelSurface c, float full, float glyph) {
        if (this.controlsArea.isEmpty()) {
            return;
        }
        c.text(Text.track("Settings"), this.controlsArea.x, this.controlsArea.y, Tokens.INK_3,
                Text.MICRO);

        final DisplayPrefs.Simplify mode = DisplayPrefs.simplify();
        row(c, 0, "Simplify at distance", mode.detail());
        row(c, 1, "Smooth transitions",
                "Blend between levels instead of switching on one frame");
        row(c, 2, "Always show the title",
                "A distant panel still says which machine it is watching");
        row(c, 3, "Render distance",
                "The hard ceiling. Past this the face is not drawn at all");

        final Rect foot = new Rect(this.controlsArea.x,
                this.controlsArea.y + 22.0f + 4 * CONTROL_ROW_H + Tokens.S2,
                this.controlsArea.w, 40.0f);
        if (foot.bottom() <= this.controlsArea.bottom()) {
            c.card(foot, Tokens.R_CARD, Tokens.RAISED, Tokens.LINE);
            c.textV(consequence(full, glyph), foot.x + Tokens.S3, foot.centerY(),
                    Tokens.INK_2, Text.MICRO);
        }
    }

    private String consequence(float full, float glyph) {
        final int render = DisplayPrefs.maxRenderDistance();
        if (Float.isInfinite(full)) {
            return "Full detail everywhere, out to " + render + " m.";
        }
        if (glyph >= render) {
            return "Full to " + metres(full) + ", reduced from there to the " + render
                    + " m render limit.";
        }
        return "Full to " + metres(full) + ", reduced to " + metres(glyph)
                + ", a status glyph out to " + render + " m.";
    }

    private void row(PanelSurface c, int index, String name, String hint) {
        final Rect r = controlRow(index);
        if (r.bottom() > this.controlsArea.bottom()) {
            return;
        }
        c.text(name, r.x, r.y, Tokens.INK, Text.META);
        c.text(hint, r.x, r.y + 13.0f, Tokens.INK_3, Text.MICRO);
        if (index == 3) {
            c.textRight(DisplayPrefs.maxRenderDistance() + " m", r.right(), r.y,
                    Tokens.INK_2, Text.META);
        }
        c.hairlineH(r.x, r.bottom(), r.w, Tokens.LINE);
    }

    @Override
    protected boolean onMouseDown(double mx, double my, int button) {
        if (button == 0 && markerTrack().contains(mx, my)) {
            this.draggingMarker = true;
            moveMarker(mx);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (super.mouseDragged(mx, my, button, dx, dy)) {
            return true;
        }
        if (this.draggingMarker) {
            moveMarker(mx);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseUp(double mx, double my, int button) {
        this.draggingMarker = false;
        return super.mouseUp(mx, my, button);
    }

    @Override
    protected boolean onScroll(double mx, double my, double amount) {
        if (!markerTrack().contains(mx, my)) {
            return false;
        }
        this.previewDistance = clamp((float) (this.previewDistance + amount), 0.5f,
                DisplayPrefs.maxRenderDistance());
        return true;
    }

    private Rect markerTrack() {
        return this.bandArea.isEmpty() ? Rect.EMPTY
                : new Rect(this.bandArea.x, this.bandArea.y + 12.0f, this.bandArea.w, 34.0f);
    }

    private void moveMarker(double mx) {
        final Rect track = markerTrack();
        if (track.w <= 0.0f) {
            return;
        }
        final float t = clamp01((float) ((mx - track.x) / track.w));
        this.previewDistance = Math.max(0.5f, t * DisplayPrefs.maxRenderDistance());
    }

    private static String metres(float d) {
        if (Float.isInfinite(d)) {
            return "any range";
        }
        return d < 10.0f
                ? String.format(Locale.ROOT, "%.1f m", d)
                : Math.round(d) + " m";
    }

    private static float clamp01(float v) {
        return clamp(v, 0.0f, 1.0f);
    }

    private static float clamp(float v, float lo, float hi) {
        return Float.isNaN(v) ? lo : v < lo ? lo : Math.min(v, hi);
    }
}
