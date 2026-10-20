package com.createstats.statsmk3.page;

import java.util.ArrayList;
import java.util.List;

import com.createstats.data.TimeSeriesStore;
import com.createstats.statsmk3.ui.PanelSurface;
import com.createstats.statsmk3.ui.Rect;
import com.createstats.statsmk3.ui.Shapes;
import com.createstats.statsmk3.ui.Text;
import com.createstats.statsmk3.ui.Tokens;
import com.createstats.statsmk3.view.StudioContext;
import com.createstats.statsmk3.view.StudioView;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class FluidsView extends StudioView {
    static final String[] FAMILIES = {"fluid.tank", "fluid.flow_meter"};

    private static final float FLOW_SCALE = 40.0f;

    public FluidsView(StudioContext ctx) {
        super(ctx);
    }

    @Override
    public String id() {
        return "fluids";
    }

    @Override
    public String label() {
        return "Fluids";
    }

    @Override
    public void drawIcon(PanelSurface c, float cx, float cy, int color) {
        Shapes.roundedRectOutline(c, new Rect(cx - 5.0f, cy - 6.0f, 10.0f, 12.0f), 2.0f, 1.2f, color);
        Shapes.rect(c, cx - 3.5f, cy, 7.0f, 4.5f, color);
    }

    @Override
    protected void build() {
    }

    @Override
    protected void drawContent(PanelSurface c) {
        paint(c, this.area, read(), this.ctx.time);
    }

    record Tank(String name, String fluid, float fillPct, float amount, float capacity,
                float changePerSecond) {
        float fill() {
            return Math.max(0.0f, Math.min(1.0f, this.fillPct / 100.0f));
        }

        float agitation() {
            return Math.max(0.0f, Math.min(1.0f, Math.abs(this.changePerSecond) / 400.0f));
        }

        String state() {
            if (Math.abs(this.changePerSecond) < 0.5f) {
                return "SETTLED";
            }
            return this.changePerSecond > 0.0f ? "FILLING" : "DRAINING";
        }

        int stateColor() {
            if (Math.abs(this.changePerSecond) < 0.5f) {
                return Tokens.INK_3;
            }
            return this.changePerSecond > 0.0f ? Tokens.GOOD : Tokens.CAUTION;
        }
    }

    record Meter(String name, String fluid, float rateMbPerTick, float totalMoved, float direction) {
        float signedRate() {
            return this.direction < 0.0f ? -Math.abs(this.rateMbPerTick) : Math.abs(this.rateMbPerTick);
        }

        boolean flowing() {
            return Math.abs(this.rateMbPerTick) > 0.001f;
        }
    }

    record Plant(List<Tank> tanks, List<Meter> meters) {
        boolean isEmpty() {
            return this.tanks.isEmpty() && this.meters.isEmpty();
        }
    }

    private Plant read() {
        final List<Tank> tanks = new ArrayList<>();
        final List<Meter> meters = new ArrayList<>();

        for (final SourceProbe.Unit unit : SourceProbe.byFamily(this.ctx.panel, FAMILIES)) {
            if (unit.has("flow_rate_mbt")) {
                meters.add(new Meter(unit.label(),
                        unit.labelOf("fluid_id", "Fluid"),
                        unit.value("flow_rate_mbt", 0.0f),
                        unit.value("flow_total_mb", 0.0f),
                        unit.value("flow_direction", 1.0f)));
                continue;
            }
            final TimeSeriesStore store = unit.store("fluid_amount");
            final float[] history = store == null ? new float[0] : store.getDataWindow(120);
            final float seconds = store == null ? 1.0f
                    : Math.max(0.05f, store.getSampleIntervalTicks() / 20.0f);
            tanks.add(new Tank(unit.label(),
                    unit.labelSuffix("fluid_present", "Fluid",
                            unit.labelSuffix("fuel_present", "Fuel", "")),
                    unit.any(0.0f, "tank_fill", "fluid_fill_pct"),
                    unit.any(0.0f, "fluid_amount", "fuel_amount"),
                    unit.any(0.0f, "tank_capacity", "fluid_capacity", "fuel_capacity"),
                    PageArt.slope(history, history.length) / seconds));
        }
        return new Plant(tanks, meters);
    }

    static void paint(PanelSurface c, Rect area, Plant plant, float time) {
        c.text("Fluids", area.x, area.y, Tokens.INK, Text.SUB);
        c.text(plant.isEmpty() ? "No tank or flow meter linked"
                        : plant.tanks().size() + " tanks, " + plant.meters().size() + " meters",
                area.x, area.y + 20.0f, Tokens.INK_3, Text.META);

        final Rect body = area.inset(0.0f, 46.0f, 0.0f, 0.0f);
        if (plant.isEmpty()) {
            drawEmpty(c, body.top(104.0f));
            return;
        }

        final float metersH = plant.meters().isEmpty() ? 0.0f
                : Math.min(52.0f + plant.meters().size() * 34.0f, body.h * 0.45f);
        final Rect tankArea = plant.meters().isEmpty() ? body
                : body.top(Math.max(90.0f, body.h - metersH - Tokens.S3));

        drawTanks(c, tankArea, plant.tanks(), time);
        if (!plant.meters().isEmpty()) {
            drawMeters(c, body.below(tankArea.h + Tokens.S3), plant.meters(), time);
        }
    }

    private static void drawTanks(PanelSurface c, Rect a, List<Tank> tanks, float time) {
        if (a.isEmpty()) {
            return;
        }
        Shapes.card(c, a, Tokens.R_CARD, Tokens.SURFACE, Tokens.LINE);
        final Rect inner = a.inset(Tokens.S3);
        c.text(Text.track("Tanks"), inner.x, inner.y, Tokens.INK_3, Text.MICRO);
        if (tanks.isEmpty()) {
            c.textV("No tank linked", inner.x, inner.y + 26.0f, Tokens.INK_3, Text.META);
            return;
        }

        final Rect row = inner.below(16.0f);
        final int columns = Math.min(tanks.size(), Math.max(1, (int) (row.w / 96.0f)));
        final float gap = Tokens.S3;
        final float cellW = (row.w - gap * (columns - 1)) / columns;

        for (int i = 0; i < tanks.size() && i < columns; i++) {
            drawTank(c, new Rect(row.x + i * (cellW + gap), row.y, cellW, row.h),
                    tanks.get(i), time);
        }
    }

    private static void drawTank(PanelSurface c, Rect a, Tank tank, float time) {
        if (a.h < 50.0f) {
            return;
        }
        final int fluidColor = fluidColor(tank.fluid());
        final float labelH = 30.0f;
        final Rect vessel = new Rect(a.x + a.w * 0.14f, a.y,
                a.w * 0.72f, Math.max(24.0f, a.h - labelH - 14.0f));

        PageArt.vessel(c, vessel, tank.fill(), fluidColor, tank.agitation(), time);

        c.textCenterV(Math.round(tank.fillPct()) + "%", vessel.centerX(), vessel.centerY(),
                tank.fill() > 0.55f ? 0xFF0A0B0E : Tokens.INK, Text.BODY);

        final float y = vessel.bottom() + 6.0f;
        c.textCenter(Text.truncate(c, tank.name(), a.w, Text.MICRO), a.centerX(), y,
                Tokens.INK_2, Text.MICRO);
        c.textCenter(Text.value(tank.amount()) + " mB", a.centerX(), y + 11.0f,
                Tokens.INK_3, Text.MICRO);

        // settled = no forecast. inventing one would be the only dishonest thing on the page

        final float rate = tank.changePerSecond();
        if (Math.abs(rate) >= 0.5f) {
            final float room = rate > 0.0f
                    ? Math.max(0.0f, tank.capacity() - tank.amount()) : tank.amount();
            final String eta = PageArt.duration(room / Math.abs(rate), 36_000.0f);
            final String word = rate > 0.0f ? "full" : "empty";
            c.textCenter(eta == null ? tank.state().toLowerCase(java.util.Locale.ROOT)
                            : word + " in " + eta,
                    a.centerX(), y + 22.0f, tank.stateColor(), Text.MICRO);
        } else {
            c.textCenter("settled", a.centerX(), y + 22.0f, Tokens.INK_3, Text.MICRO);
        }
    }

    private static void drawMeters(PanelSurface c, Rect a, List<Meter> meters, float time) {
        if (a.isEmpty() || a.h < 40.0f) {
            return;
        }
        Shapes.card(c, a, Tokens.R_CARD, Tokens.SURFACE, Tokens.LINE);
        final Rect inner = a.inset(Tokens.S3);
        c.text(Text.track("Flow"), inner.x, inner.y, Tokens.INK_3, Text.MICRO);

        final float top = inner.y + 16.0f;
        final float rowH = 32.0f;
        for (int i = 0; i < meters.size() && top + (i + 1) * rowH <= inner.bottom() + 4.0f; i++) {
            final Meter m = meters.get(i);
            final Rect r = new Rect(inner.x, top + i * rowH, inner.w, rowH - 4.0f);
            final int color = fluidColor(m.fluid());

            c.textV(Text.truncate(c, m.name(), r.w * 0.26f, Text.MICRO), r.x, r.centerY(),
                    Tokens.INK_2, Text.MICRO);

            final float pipeX = r.x + r.w * 0.30f;
            final float pipeW = r.w * 0.40f;

            Shapes.rect(c, pipeX, r.centerY() - 6.0f, pipeW, 1.0f, Tokens.alpha(Tokens.LINE_HI, 0.8f));
            Shapes.rect(c, pipeX, r.centerY() + 5.0f, pipeW, 1.0f, Tokens.alpha(Tokens.LINE_HI, 0.8f));
            PageArt.flow(c, pipeX, r.centerY(), pipeX + pipeW, r.centerY(), 3.0f,
                    color, m.signedRate(), FLOW_SCALE, time);

            c.textRightV(m.flowing() ? Text.value(Math.abs(m.rateMbPerTick())) + " mB/t" : "no flow",
                    r.right(), r.centerY(), m.flowing() ? color : Tokens.INK_3, Text.MICRO);
        }
    }

    private static int fluidColor(String fluid) {
        return PageArt.fluidColor(fluid);
    }

    private static void drawEmpty(PanelSurface c, Rect box) {
        Shapes.card(c, box, Tokens.R_CARD, Tokens.SURFACE, Tokens.LINE);
        c.textCenter("No fluid source linked", box.centerX(), box.centerY() - 14.0f,
                Tokens.INK_2, Text.BODY);
        c.textCenter("Point a Display Link from a Fluid Tank or a Flow Meter at this panel.",
                box.centerX(), box.centerY() + 2.0f, Tokens.INK_3, Text.MICRO);
    }
}
