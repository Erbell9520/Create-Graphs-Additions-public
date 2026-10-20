package com.createstats.statsmk3.page;

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

// create generators burn at a fixed rate used or not. nobody shows you that
@OnlyIn(Dist.CLIENT)
public final class DieselView extends StudioView {
    static final String[] FAMILIES = {"diesel.engine", "diesel.portable_engine"};

    private static final float SAFETY_MARGIN = 0.25f;

    private final Spring waste = Spring.ui(0.0f);

    public DieselView(StudioContext ctx) {
        super(ctx);
    }

    @Override
    public String id() {
        return "diesel";
    }

    @Override
    public String label() {
        return "Diesel";
    }

    @Override
    public void drawIcon(PanelSurface c, float cx, float cy, int color) {
        Shapes.roundedRectOutline(c, new Rect(cx - 6.5f, cy - 3.0f, 13.0f, 9.0f), 2.0f, 1.2f, color);
        Shapes.rect(c, cx - 3.5f, cy - 6.5f, 2.4f, 4.0f, color);
        Shapes.rect(c, cx + 1.0f, cy - 6.5f, 2.4f, 4.0f, color);
    }

    @Override
    protected void build() {
    }

    @Override
    public void update(float dt) {
        super.update(dt);
        this.waste.set(read().wasteFraction()).step(dt);
    }

    @Override
    protected void drawContent(PanelSurface c) {
        paint(c, this.area, read(), this.waste.value(), this.ctx.time);
    }

    record Bank(int engines, int firing, int stalling, int off,
                float producedSu, float suPerEngine, float networkLoad, float networkCapacity,
                float otherCapacity,
                float fuelAmount, float fuelCapacity, String fuel,
                float fuelPerSecond, float[] fuelHistory, float historySeconds,
                boolean validFuel, boolean redstoneStopped) {
        boolean isEmpty() {
            return this.engines <= 0;
        }

        float fuelFraction() {
            return this.fuelCapacity > 0.0f
                    ? Math.max(0.0f, Math.min(1.0f, this.fuelAmount / this.fuelCapacity)) : 0.0f;
        }

        float idleCapacity() {
            return Math.max(0.0f, this.producedSu - usedCapacity());
        }

        float usedCapacity() {
            final float mine = Math.max(0.0f, this.networkLoad - this.otherCapacity);
            return Math.min(this.producedSu, mine);
        }

        float wasteFraction() {
            return this.producedSu > 0.0f ? idleCapacity() / this.producedSu : 0.0f;
        }

        int surplusEngines() {
            if (this.firing <= 1 || this.suPerEngine <= 0.0f || this.redstoneStopped) {
                return 0;
            }
            final float needed = usedCapacity() * (1.0f + SAFETY_MARGIN);
            if (needed <= 0.0f) {
                return this.firing - 1;
            }
            final int required = (int) Math.ceil(needed / this.suPerEngine);
            return Math.max(0, this.firing - Math.max(1, required));
        }

        float secondsToDry() {
            return this.fuelPerSecond >= -1.0e-4f ? -1.0f : this.fuelAmount / -this.fuelPerSecond;
        }

        float secondsToDryReduced() {
            final int surplus = surplusEngines();
            if (surplus <= 0 || this.firing <= 0) {
                return -1.0f;
            }
            final float remaining = this.firing - surplus;
            final float reducedRate = -this.fuelPerSecond * (remaining / this.firing);
            return reducedRate <= 1.0e-5f ? -1.0f : this.fuelAmount / reducedRate;
        }

        String headline() {
            if (this.engines <= 0) {
                return "No engine linked";
            }
            if (this.redstoneStopped) {
                return "Held off by redstone";
            }
            if (!this.validFuel) {
                return "Fuel is not burnable";
            }
            if (this.firing <= 0) {
                return "Stopped";
            }
            final StringBuilder sb = new StringBuilder();
            sb.append(this.firing).append(this.firing == 1 ? " engine on " : " engines on ");
            sb.append(this.fuel == null || this.fuel.isEmpty() ? "fuel" : this.fuel);
            if (this.stalling > 0) {
                sb.append(", ").append(this.stalling).append(" stalling");
            }
            return sb.toString();
        }

        int headlineColor() {
            if (this.engines <= 0 || this.firing <= 0 || !this.validFuel) {
                return Tokens.CRITICAL;
            }
            return this.stalling > 0 ? Tokens.CAUTION : Tokens.GOOD;
        }
    }

    private Bank read() {
        final List<SourceProbe.Unit> units = SourceProbe.byFamily(this.ctx.panel, FAMILIES);
        if (units.isEmpty()) {
            return new Bank(0, 0, 0, 0, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, "",
                    0.0f, new float[0], 0.0f, true, false);
        }

        int engines = 0;
        int firing = 0;
        int stalling = 0;
        int off = 0;
        float produced = 0.0f;
        float perEngine = 0.0f;
        float load = 0.0f;
        float capacity = 0.0f;
        float fuel = 0.0f;
        float fuelCap = 0.0f;
        String fuelName = "";
        boolean validFuel = true;
        boolean redstone = false;
        float[] history = new float[0];
        float historySeconds = 0.0f;
        float perSecond = 0.0f;

        for (final SourceProbe.Unit unit : units) {
            final int count = Math.max(1, Math.round(unit.value("engine_count", 1.0f)));
            engines += count;
            if (unit.has("ok_engines")) {
                firing += Math.round(unit.value("ok_engines", 0.0f));
                stalling += Math.round(unit.value("stalling_engines", 0.0f));
                off += Math.round(unit.value("off_engines", 0.0f));
            } else if (unit.flag("running")) {
                firing += count;
            } else if (unit.flag("enabled")) {
                stalling += count;
            } else {
                off += count;
            }

            produced += unit.value("generated_stress_su", 0.0f);
            perEngine = Math.max(perEngine, unit.value("su_per_engine", 0.0f));
            load = Math.max(load, unit.value("stress_su", 0.0f));
            capacity = Math.max(capacity, unit.value("capacity_su", 0.0f));
            fuel += unit.value("fuel_amount", 0.0f);
            fuelCap += unit.value("fuel_capacity", 0.0f);
            validFuel &= unit.value("valid_fuel", 1.0f) > 0.5f;
            redstone |= unit.flag("redstone_disabled");
            if (fuelName.isEmpty()) {
                fuelName = unit.labelSuffix("fuel_present", "Fuel", "");
            }

            final TimeSeriesStore store = unit.store("fuel_amount");
            if (store != null && store.getSampleCount() > history.length) {
                history = store.getDataWindow(180);
                final float interval = Math.max(0.05f, store.getSampleIntervalTicks() / 20.0f);
                historySeconds = history.length * interval;
                perSecond = PageArt.slope(history, history.length) / interval;
            }
        }

        if (history.length < 6) {
            perSecond = 0.0f;
        }

        if (perEngine <= 0.0f && firing > 0) {
            perEngine = produced / firing;
        }

        final float other = Math.max(0.0f, capacity - produced);
        return new Bank(engines, firing, stalling, off, produced, perEngine, load, capacity, other,
                fuel, fuelCap, fuelName, perSecond, history, historySeconds, validFuel, redstone);
    }

    static void paint(PanelSurface c, Rect area, Bank bank, float wasteSpring, float time) {
        c.text("Diesel", area.x, area.y, Tokens.INK, Text.SUB);
        c.text(bank.headline(), area.x, area.y + 20.0f, bank.headlineColor(), Text.META);

        final Rect body = area.inset(0.0f, 46.0f, 0.0f, 0.0f);
        if (bank.isEmpty()) {
            drawEmpty(c, body.top(104.0f));
            return;
        }

        final float ledgerH = Math.max(112.0f, Math.min(body.h * 0.44f, 178.0f));
        drawLedger(c, body.top(ledgerH), bank, wasteSpring, time);

        final Rect horizon = body.below(ledgerH + Tokens.S3);
        if (horizon.h > 60.0f) {
            drawHorizon(c, horizon, bank);
        }
    }

    private static void drawLedger(PanelSurface c, Rect a, Bank bank, float wasteSpring, float time) {
        if (a.isEmpty()) {
            return;
        }
        Shapes.card(c, a, Tokens.R_CARD, Tokens.SURFACE, Tokens.LINE);
        final Rect inner = a.inset(Tokens.S3);

        c.text(Text.track("What the fuel buys"), inner.x, inner.y, Tokens.INK_3, Text.MICRO);
        final float burn = Math.abs(bank.fuelPerSecond());
        c.textRight(burn > 0.001f ? Text.value(burn) + " mB/s burning" : "not burning",
                inner.right(), inner.y, burn > 0.001f ? Tokens.INK_2 : Tokens.INK_3, Text.MICRO);

        final Rect bar = new Rect(inner.x, inner.y + 22.0f, inner.w, 26.0f);
        final float produced = Math.max(1.0f, bank.producedSu());
        final float usedFrac = Math.max(0.0f, Math.min(1.0f, bank.usedCapacity() / produced));
        final float usedW = bar.w * usedFrac;

        Shapes.roundedRect(c, bar, 4.0f, Tokens.alpha(Tokens.CANVAS, 0.9f));
        if (usedW > 1.0f) {
            Shapes.roundedRect(c, bar.withWidth(usedW), 4.0f, Tokens.GOOD);
        }

        final Rect idle = new Rect(bar.x + usedW, bar.y, Math.max(0.0f, bar.w - usedW), bar.h);
        if (idle.w > 2.0f) {
            Shapes.roundedRect(c, idle, 4.0f, Tokens.alpha(Tokens.CAUTION, 0.14f));
            for (float x = idle.x + 3.0f; x < idle.right(); x += 7.0f) {
                final float span = Math.min(idle.h, idle.right() - x);
                Shapes.line(c, x, idle.bottom(), x + span, idle.bottom() - span, 1.0f,
                        Tokens.alpha(Tokens.CAUTION, 0.45f));
            }
        }
        Shapes.roundedRectOutline(c, bar, 4.0f, 1.0f, Tokens.LINE_HI);

        final float labelY = bar.bottom() + 5.0f;
        c.text("used  " + Text.value(bank.usedCapacity()) + " SU", bar.x, labelY,
                Tokens.GOOD, Text.MICRO);
        if (idle.w > 60.0f) {
            c.textRight("idle  " + Text.value(bank.idleCapacity()) + " SU", bar.right(), labelY,
                    Tokens.CAUTION, Text.MICRO);
        }

        drawVerdict(c, new Rect(inner.x, labelY + 15.0f, inner.w,
                Math.max(0.0f, inner.bottom() - labelY - 15.0f)), bank, wasteSpring, time);
    }

    private static void drawVerdict(PanelSurface c, Rect a, Bank bank, float wasteSpring, float time) {
        if (a.h < 26.0f) {
            return;
        }
        final int surplus = bank.surplusEngines();
        final float wastePct = Math.max(0.0f, Math.min(1.0f, wasteSpring)) * 100.0f;

        final int drawn = Math.min(bank.firing(), 12);
        final float pipW = 9.0f;
        final float pipGap = 3.0f;
        final float pipsW = drawn * pipW + (drawn - 1) * pipGap;
        final float pipX = a.right() - pipsW;
        for (int i = 0; i < drawn; i++) {
            final Rect pip = new Rect(pipX + i * (pipW + pipGap), a.y + 2.0f, pipW, 16.0f);
            final boolean keep = i < drawn - Math.min(surplus, drawn);
            if (keep) {
                Shapes.roundedRect(c, pip, 2.0f, Tokens.GOOD);
            } else {
                Shapes.roundedRectOutline(c, pip, 2.0f, 1.0f, Tokens.CAUTION);

                final float pulse = 0.18f + 0.16f * (float) Math.sin(time * 2.2 + i * 0.4);
                Shapes.roundedRect(c, pip, 2.0f, Tokens.alpha(Tokens.CAUTION, pulse));
            }
        }

        final float textW = Math.max(40.0f, pipX - a.x - 10.0f);
        if (bank.redstoneStopped() || bank.firing() <= 0) {
            c.textV("Not burning. Nothing to recover.", a.x, a.y + 10.0f, Tokens.INK_3, Text.MICRO);
            return;
        }
        if (surplus <= 0) {
            final String line = wastePct < 12.0f
                    ? "Well matched: almost all of this fuel is doing work."
                    : "Spare capacity is under one engine's worth. Nothing to switch off.";
            c.textV(Text.truncate(c, line, textW, Text.MICRO), a.x, a.y + 10.0f,
                    wastePct < 12.0f ? Tokens.GOOD : Tokens.INK_2, Text.MICRO);
            return;
        }

        final String headline = Math.round(wastePct) + "% of this fuel makes stress nothing uses";
        c.textV(Text.truncate(c, headline, textW, Text.META), a.x, a.y + 9.0f,
                Tokens.CAUTION, Text.META);

        final String dryNow = PageArt.duration(bank.secondsToDry(), 86_400.0f);
        final String dryCut = PageArt.duration(bank.secondsToDryReduced(), 86_400.0f);
        final String advice = dryNow != null && dryCut != null
                ? "Switch off " + surplus + " and the tank lasts " + dryCut + " instead of " + dryNow
                : "Switch off " + surplus + (surplus == 1 ? " engine" : " engines")
                        + " and the load is still covered";
        c.textV(Text.truncate(c, advice, textW, Text.MICRO), a.x, a.y + 25.0f,
                Tokens.INK_2, Text.MICRO);
    }

    private static void drawHorizon(PanelSurface c, Rect a, Bank bank) {
        Shapes.card(c, a, Tokens.R_CARD, Tokens.SURFACE, Tokens.LINE);
        final Rect inner = a.inset(Tokens.S3);
        c.text(Text.track("Fuel horizon"), inner.x, inner.y, Tokens.INK_3, Text.MICRO);
        final String fuelName = bank.fuel() == null || bank.fuel().isEmpty() ? "Fuel" : bank.fuel();
        c.textRight(fuelName + "  " + Text.value(bank.fuelAmount()) + " mB",
                inner.right(), inner.y, Tokens.INK_3, Text.MICRO);

        final Rect plot = inner.below(16.0f);
        if (plot.h < 34.0f) {
            return;
        }
        final float nowX = plot.x + plot.w * 0.34f;
        final float capacity = Math.max(bank.fuelCapacity(), Math.max(bank.fuelAmount(), 1.0f));
        final int fuelColor = 0xFFBC8309;

        final float[] history = bank.fuelHistory();
        if (history != null && history.length >= 2) {
            final int n = history.length;
            final float[] xy = new float[n * 2];
            for (int i = 0; i < n; i++) {
                xy[i * 2] = plot.x + (nowX - plot.x) * (i / (float) (n - 1));
                xy[i * 2 + 1] = plot.bottom()
                        - plot.h * Math.max(0.0f, Math.min(1.0f, history[i] / capacity));
            }
            Shapes.areaFill(c, xy, n, plot.bottom(),
                    Tokens.alpha(fuelColor, 0.26f), Tokens.alpha(fuelColor, 0.0f));
            Shapes.polyline(c, xy, n, Tokens.DATA_STROKE, fuelColor);
        } else {
            c.textV("no history yet", plot.x + 4.0f, plot.centerY(), Tokens.INK_3, Text.MICRO);
        }

        final float nowY = plot.bottom() - plot.h * bank.fuelFraction();
        final float dry = bank.secondsToDry();
        final float dryCut = bank.secondsToDryReduced();

        final float furthest = Math.max(dry, dryCut);
        final float window = furthest > 0.0f
                ? Math.max(60.0f, Math.min(furthest * 1.15f, 14_400.0f)) : 1800.0f;

        drawForecast(c, plot, nowX, nowY, bank, dry, dryCut, window, fuelColor);

        Shapes.rect(c, Math.round(nowX), plot.y, 1.0f, plot.h, Tokens.alpha(Tokens.INK, 0.55f));
        c.text("now", nowX + 3.0f, plot.y - 1.0f, Tokens.INK_3, Text.MICRO);
        if (bank.historySeconds() > 1.0f) {
            final String span = PageArt.duration(bank.historySeconds(), 86_400.0f);
            if (span != null) {
                c.text("-" + span, plot.x, plot.y - 1.0f, Tokens.INK_3, Text.MICRO);
            }
        }
        final String windowLabel = PageArt.duration(window, 86_400.0f);
        if (windowLabel != null) {
            c.textRight("+" + windowLabel, plot.right(), plot.y - 1.0f, Tokens.INK_3, Text.MICRO);
        }
    }

    private static void drawForecast(PanelSurface c, Rect plot, float nowX, float nowY, Bank bank,
                                     float dry, float dryCut, float window, int fuelColor) {
        final float futureW = plot.right() - nowX;
        if (futureW < 12.0f) {
            return;
        }
        for (float x = nowX; x < plot.right(); x += 9.0f) {
            final float span = Math.min(plot.h, plot.right() - x);
            Shapes.line(c, x, plot.bottom(), x + span, plot.bottom() - span, 1.0f,
                    Tokens.alpha(Tokens.LINE_HI, 0.28f));
        }

        if (bank.fuelPerSecond() >= -1.0e-4f) {
            final String note = bank.fuelPerSecond() > 1.0e-4f
                    ? "supply is outpacing the burn" : "not burning";
            Shapes.line(c, nowX, nowY, plot.right(), nowY, 1.5f, Tokens.alpha(Tokens.GOOD, 0.5f));
            c.textV(note, nowX + 8.0f, nowY - 10.0f, Tokens.GOOD, Text.MICRO);
            return;
        }

        if (dryCut > 0.0f) {
            drawDecline(c, plot, nowX, nowY, dryCut, window, Tokens.alpha(Tokens.GOOD, 0.75f), 3);
            final float cutX = crossingX(plot, nowX, dryCut, window);
            if (cutX <= plot.right() - 4.0f) {
                Shapes.circle(c, cutX, plot.bottom(), 3.0f, Tokens.GOOD);
                final String label = "with " + bank.surplusEngines() + " off: "
                        + PageArt.duration(dryCut, 86_400.0f);
                labelAt(c, plot, cutX, plot.y + 16.0f, label, Tokens.GOOD);
            }
        }

        drawDecline(c, plot, nowX, nowY, dry, window, Tokens.alpha(fuelColor, 0.9f), 2);
        final float dryX = crossingX(plot, nowX, dry, window);
        if (dryX <= plot.right()) {
            final int color = dry < 300.0f ? Tokens.CRITICAL : Tokens.CAUTION;
            Shapes.rect(c, Math.round(dryX), plot.y, 1.0f, plot.h, Tokens.alpha(color, 0.75f));
            Shapes.circle(c, dryX, plot.bottom(), 3.2f, color);
            labelAt(c, plot, dryX, plot.y + 4.0f,
                    "dry in " + PageArt.duration(dry, 86_400.0f), color);
        }
    }

    private static float crossingX(Rect plot, float nowX, float seconds, float window) {
        if (seconds <= 0.0f) {
            return plot.right();
        }
        return nowX + (plot.right() - nowX) * Math.max(0.0f, Math.min(1.0f, seconds / window));
    }

    private static void drawDecline(PanelSurface c, Rect plot, float nowX, float nowY,
                                    float seconds, float window, int color, int dashOn) {
        final float endX = Math.min(crossingX(plot, nowX, seconds, window), plot.right());
        final float endY = plot.bottom();
        final int dashes = Math.max(4, Math.round((endX - nowX) / 7.0f));
        for (int i = 0; i < dashes; i += dashOn + 1) {
            final float t0 = i / (float) dashes;
            final float t1 = Math.min(1.0f, (i + dashOn) / (float) dashes);
            Shapes.line(c, nowX + (endX - nowX) * t0, nowY + (endY - nowY) * t0,
                    nowX + (endX - nowX) * t1, nowY + (endY - nowY) * t1,
                    Tokens.DATA_STROKE, color);
        }
    }

    private static void labelAt(PanelSurface c, Rect plot, float x, float y, String label,
                                int color) {
        if (label == null) {
            return;
        }
        final float w = c.textWidth(label, Text.MICRO);
        if (x + w + 10.0f > plot.right()) {
            c.textRight(label, x - 6.0f, y, color, Text.MICRO);
        } else {
            c.text(label, x + 5.0f, y, color, Text.MICRO);
        }
    }

    private static void drawEmpty(PanelSurface c, Rect box) {
        Shapes.card(c, box, Tokens.R_CARD, Tokens.SURFACE, Tokens.LINE);
        c.textCenter("No engine linked", box.centerX(), box.centerY() - 14.0f,
                Tokens.INK_2, Text.BODY);
        c.textCenter("Point a Display Link from a diesel engine or engine group at this panel.",
                box.centerX(), box.centerY() + 2.0f, Tokens.INK_3, Text.MICRO);
    }
}
