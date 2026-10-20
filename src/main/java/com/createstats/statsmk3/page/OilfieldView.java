package com.createstats.statsmk3.page;

import java.util.ArrayList;
import java.util.List;

import com.createstats.data.TimeSeriesStore;
import com.createstats.statsmk3.ui.PanelSurface;
import com.createstats.statsmk3.ui.Rect;
import com.createstats.statsmk3.ui.Shapes;
import com.createstats.statsmk3.ui.Spring;
import com.createstats.statsmk3.ui.Text;
import com.createstats.statsmk3.ui.Tokens;
import com.createstats.statsmk3.view.StudioContext;
import com.createstats.statsmk3.view.StudioView;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class OilfieldView extends StudioView {
    static final String[] FAMILIES = {"diesel.pumpjack"};

    private static final float STROKE_HZ = 0.42f;

    private final Spring beam = Spring.ui(0.0f);
    private float phase;

    public OilfieldView(StudioContext ctx) {
        super(ctx);
    }

    @Override
    public String id() {
        return "oilfield";
    }

    @Override
    public String label() {
        return "Oilfield";
    }

    @Override
    public void drawIcon(PanelSurface c, float cx, float cy, int color) {
        Shapes.line(c, cx - 6.0f, cy + 5.0f, cx - 1.0f, cy - 3.0f, 1.4f, color);
        Shapes.line(c, cx - 4.0f, cy - 1.0f, cx + 6.0f, cy - 4.0f, 1.4f, color);
        Shapes.rect(c, cx + 4.0f, cy - 4.0f, 1.3f, 9.0f, color);
    }

    @Override
    protected void build() {
    }

    @Override
    public void update(float dt) {
        super.update(dt);

        final Field field = read();
        this.phase = (this.phase + dt * STROKE_HZ * field.dutyCycle()) % 1.0f;
        this.beam.set(field.dutyCycle()).step(dt);
    }

    @Override
    protected void drawContent(PanelSurface c) {
        paint(c, this.area, read(), this.phase, this.beam.value());
    }

    record Well(String name, boolean started, boolean pipeValid, float pipeLength,
                float oilRemaining, float[] history, float depletionPerSecond) {
        float duty() {
            return this.started && this.pipeValid && this.oilRemaining > 0.0f ? 1.0f : 0.0f;
        }

        String state() {
            if (!this.pipeValid) {
                return "PIPE BROKEN";
            }
            if (this.oilRemaining <= 0.0f) {
                return "DRY";
            }
            return this.started ? "PUMPING" : "STOPPED";
        }

        int stateColor() {
            if (!this.pipeValid || this.oilRemaining <= 0.0f) {
                return Tokens.CRITICAL;
            }
            return this.started ? Tokens.GOOD : Tokens.INK_3;
        }
    }

    record Field(List<Well> wells) {
        boolean isEmpty() {
            return this.wells.isEmpty();
        }

        Well primary() {
            return this.wells.isEmpty() ? null : this.wells.get(0);
        }

        float dutyCycle() {
            float best = 0.0f;
            for (final Well w : this.wells) {
                best = Math.max(best, w.duty());
            }
            return best;
        }

        float totalOil() {
            float total = 0.0f;
            for (final Well w : this.wells) {
                total += w.oilRemaining();
            }
            return total;
        }
    }

    private Field read() {
        final List<Well> wells = new ArrayList<>();
        for (final SourceProbe.Unit unit : SourceProbe.byFamily(this.ctx.panel, FAMILIES)) {
            final TimeSeriesStore store = unit.store("oil_remaining_mb");
            final float[] history = store == null ? new float[0] : store.getDataWindow(160);

            final float perSample = -PageArt.slope(history, history.length);
            final float seconds = store == null ? 1.0f
                    : Math.max(0.05f, store.getSampleIntervalTicks() / 20.0f);
            wells.add(new Well(unit.label(),
                    unit.flag("pumpjack_started"),
                    unit.flag("pumpjack_valid"),
                    unit.value("pipe_length", 0.0f),
                    unit.value("oil_remaining_mb", 0.0f),
                    history,
                    perSample / seconds));
        }
        return new Field(wells);
    }

    static void paint(PanelSurface c, Rect area, Field field, float phase, float duty) {
        c.text("Oilfield", area.x, area.y, Tokens.INK, Text.SUB);
        c.text(field.isEmpty() ? "No pumpjack linked"
                        : field.wells().size() + (field.wells().size() == 1 ? " well" : " wells")
                                + ", " + Text.value(field.totalOil()) + " mB in reserve",
                area.x, area.y + 20.0f, Tokens.INK_3, Text.META);

        final Rect body = area.inset(0.0f, 46.0f, 0.0f, 0.0f);
        if (field.isEmpty()) {
            drawEmpty(c, body.top(104.0f));
            return;
        }

        final Well well = field.primary();
        final float sceneH = Math.max(150.0f, Math.min(body.h * 0.62f, 300.0f));
        drawScene(c, body.top(sceneH), well, phase, duty);

        final Rect rest = body.below(sceneH + Tokens.S3);
        if (rest.h > 40.0f) {
            drawStats(c, rest.top(46.0f), well);
        }
        if (rest.h > 96.0f && field.wells().size() > 1) {
            drawWellList(c, rest.below(52.0f), field);
        }
    }

    private static void drawScene(PanelSurface c, Rect a, Well well, float phase, float duty) {
        if (a.isEmpty()) {
            return;
        }
        Shapes.card(c, a, Tokens.R_CARD, Tokens.SURFACE, Tokens.LINE);
        final Rect inner = a.inset(Tokens.S3);
        PageArt.machineHeader(c, inner, well.name(), well.state(), well.stateColor());

        final Rect scene = inner.below(20.0f);
        if (scene.h < 60.0f) {
            return;
        }

        final float surfaceY = scene.y + scene.h * 0.46f;
        final Rect ground = new Rect(scene.x, surfaceY, scene.w, scene.bottom() - surfaceY);

        Shapes.rect(c, ground, 0xFF14100C);

        for (int i = 1; i <= 3; i++) {
            final float y = surfaceY + ground.h * (i / 4.0f);
            Shapes.rect(c, ground.x, Math.round(y), ground.w, 1.0f,
                    Tokens.alpha(0xFF3A2E20, 0.55f - i * 0.12f));
        }
        Shapes.rect(c, scene.x, Math.round(surfaceY), scene.w, 1.0f, Tokens.alpha(0xFF8A6A45, 0.8f));

        final Rect rig = new Rect(scene.x + scene.w * 0.04f, scene.y,
                scene.w * 0.46f, surfaceY - scene.y);

        final float wellheadX = wellheadX(rig);
        drawPumpjack(c, rig, phase, duty);
        drawReservoir(c, ground, well, wellheadX);
    }

    private static float beamHalf(Rect rig) {
        return Math.min(rig.w * 0.34f, rig.h * 0.66f * 1.35f);
    }

    private static float wellheadX(Rect rig) {
        final float half = beamHalf(rig);
        final float postX = rig.x + half * 0.85f + 6.0f;
        final float headR = Math.max(4.5f, rig.h * 0.66f * 0.13f);
        return postX + half + headR * 0.9f;
    }

    private static void drawPumpjack(PanelSurface c, Rect a, float phase, float duty) {
        if (a.h < 30.0f || a.w < 40.0f) {
            return;
        }
        final int metal = 0xFF4A5260;
        final int metalLit = 0xFF6E7787;

        final float baseY = a.bottom();
        final float postH = a.h * 0.66f;

        final float beamHalf = beamHalf(a);
        final float postX = a.x + beamHalf * 0.85f + 6.0f;
        final float pivotY = baseY - postH;
        final float legSpread = Math.max(6.0f, postH * 0.30f);

        Shapes.line(c, postX - legSpread, baseY, postX, pivotY, 2.4f, metal);
        Shapes.line(c, postX + legSpread, baseY, postX, pivotY, 2.4f, metal);
        Shapes.line(c, postX - legSpread * 0.55f, baseY - postH * 0.44f,
                postX + legSpread * 0.55f, baseY - postH * 0.44f, 1.6f, metal);
        Shapes.rect(c, postX - beamHalf * 0.9f, baseY - 3.0f, beamHalf * 2.0f, 3.0f, metal);

        final float swing = (float) Math.sin(phase * Math.PI * 2.0) * 0.34f * Math.max(0.12f, duty);
        final float headX = postX + (float) Math.cos(swing) * beamHalf;
        final float headY = pivotY + (float) Math.sin(swing) * beamHalf;
        final float tailX = postX - (float) Math.cos(swing) * beamHalf * 0.72f;
        final float tailY = pivotY - (float) Math.sin(swing) * beamHalf * 0.72f;

        Shapes.line(c, tailX, tailY, headX, headY, 3.4f, metalLit);
        Shapes.circle(c, postX, pivotY, 3.4f, metalLit);

        final float headR = Math.max(4.5f, postH * 0.13f);
        Shapes.circle(c, tailX, tailY, Math.max(4.5f, postH * 0.15f), metal);
        Shapes.circle(c, tailX, tailY, Math.max(2.0f, postH * 0.06f), metalLit);
        Shapes.arc(c, headX, headY, headR, 2.6f, (float) Math.PI * 0.10f,
                (float) Math.PI * 0.85f, metalLit);
        final float rodX = headX + headR * 0.9f;
        Shapes.line(c, rodX, headY + headR * 0.5f, rodX, baseY - 4.0f, 1.6f,
                Tokens.alpha(metalLit, 0.9f));
        Shapes.rect(c, rodX - 3.5f, baseY - 6.0f, 7.0f, 6.0f, metal);
    }

    private static void drawReservoir(PanelSurface c, Rect a, Well well, float pipeX) {
        if (a.h < 24.0f) {
            return;
        }

        final float depth = Math.max(0.18f, Math.min(1.0f, well.pipeLength() / 32.0f));
        final float pipeBottom = a.y + a.h * depth;
        final int pipeColor = well.pipeValid() ? 0xFF6E7787 : Tokens.CRITICAL;
        Shapes.rect(c, pipeX - 1.5f, a.y, 3.0f, pipeBottom - a.y, pipeColor);
        for (float y = a.y + 8.0f; y < pipeBottom - 4.0f; y += 12.0f) {
            Shapes.rect(c, pipeX - 3.5f, y, 7.0f, 1.5f, Tokens.alpha(pipeColor, 0.8f));
        }

        final float peak = peakOf(well);
        final float remaining = peak > 0.0f
                ? Math.max(0.0f, Math.min(1.0f, well.oilRemaining() / peak)) : 0.0f;
        final float poolH = Math.max(6.0f, a.h * 0.30f);
        final Rect pool = new Rect(a.x + 8.0f, a.bottom() - poolH - 4.0f,
                Math.max(0.0f, (a.w - 16.0f) * remaining), poolH);

        if (pool.w > 1.0f) {
            final int oil = remaining < 0.15f ? Tokens.CRITICAL
                    : remaining < 0.35f ? Tokens.CAUTION : 0xFF3A3160;
            Shapes.roundedRect(c, pool, 3.0f, Tokens.alpha(oil, 0.9f));
            Shapes.rect(c, pool.x, Math.round(pool.y), pool.w, 1.4f,
                    Tokens.alpha(remaining < 0.35f ? oil : 0xFF6C5CE0, 0.9f));
        }

        Shapes.rect(c, pool.right() + 2.0f, Math.round(pool.bottom() - 1.0f),
                Math.max(0.0f, a.right() - 8.0f - pool.right() - 2.0f), 1.0f,
                Tokens.alpha(Tokens.LINE_HI, 0.6f));

        c.text(Math.round(remaining * 100.0f) + "% of peak", a.x + 10.0f,
                pool.y - 12.0f, Tokens.INK_3, Text.MICRO);
        c.textRight(Math.round(well.pipeLength()) + " block pipe", a.right() - 8.0f,
                a.y + 4.0f, Tokens.INK_3, Text.MICRO);
    }

    private static float peakOf(Well well) {
        float peak = well.oilRemaining();
        for (final float v : well.history()) {
            peak = Math.max(peak, v);
        }
        return peak;
    }

    private static void drawStats(PanelSurface c, Rect a, Well well) {
        final float colW = a.w / 3.0f;
        PageArt.stat(c, new Rect(a.x, a.y, colW - 8.0f, a.h), "Oil remaining",
                Text.value(well.oilRemaining()), "mB", Tokens.INK, Text.SUB);

        final float perSecond = well.depletionPerSecond();
        PageArt.stat(c, new Rect(a.x + colW, a.y, colW - 8.0f, a.h), "Extraction",
                perSecond > 0.001f ? Text.value(perSecond * 60.0f) : "idle",
                perSecond > 0.001f ? "mB/min" : "", perSecond > 0.001f ? Tokens.GOOD : Tokens.INK_3,
                Text.SUB);

        // stopped well has no dry date

        String dry = "n/a";
        int dryColor = Tokens.INK_3;
        if (perSecond > 0.001f && well.oilRemaining() > 0.0f) {
            final String d = PageArt.duration(well.oilRemaining() / perSecond, 86_400.0f);
            if (d != null) {
                dry = d;
                dryColor = Tokens.CAUTION;
            }
        }
        PageArt.stat(c, new Rect(a.x + colW * 2.0f, a.y, colW - 8.0f, a.h), "Dry in",
                dry, "", dryColor, Text.SUB);
    }

    private static void drawWellList(PanelSurface c, Rect a, Field field) {
        if (a.isEmpty()) {
            return;
        }
        c.text(Text.track("Other wells"), a.x, a.y, Tokens.INK_3, Text.MICRO);
        final float top = a.y + 15.0f;
        final float rowH = 22.0f;
        for (int i = 1; i < field.wells().size() && top + i * rowH <= a.bottom(); i++) {
            final Well w = field.wells().get(i);
            final Rect r = new Rect(a.x, top + (i - 1) * rowH, a.w, rowH - 3.0f);
            Shapes.card(c, r, Tokens.R_CHIP, Tokens.SURFACE, Tokens.LINE);
            Shapes.circle(c, r.x + 10.0f, r.centerY(), 3.0f, w.stateColor());
            c.textV(Text.truncate(c, w.name(), r.w * 0.4f, Text.MICRO),
                    r.x + 20.0f, r.centerY(), Tokens.INK_2, Text.MICRO);
            c.textRightV(Text.value(w.oilRemaining()) + " mB", r.right() - 10.0f, r.centerY(),
                    Tokens.INK_3, Text.MICRO);
        }
    }

    private static void drawEmpty(PanelSurface c, Rect box) {
        Shapes.card(c, box, Tokens.R_CARD, Tokens.SURFACE, Tokens.LINE);
        c.textCenter("No well linked", box.centerX(), box.centerY() - 14.0f,
                Tokens.INK_2, Text.BODY);
        c.textCenter("Point a Display Link from an Oil Pumpjack at this panel.",
                box.centerX(), box.centerY() + 2.0f, Tokens.INK_3, Text.MICRO);
    }
}
