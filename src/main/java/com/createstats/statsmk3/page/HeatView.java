package com.createstats.statsmk3.page;

import java.util.ArrayList;
import java.util.List;

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
public final class HeatView extends StudioView {
    static final String[] FAMILIES = {"heat.induction_heater", "steam.boiler"};

    private static final String[] INDUCTION_TIERS = {"Off", "Smouldering", "Fading", "Kindled", "Seething"};

    private static final String[] BOILER_TIERS = {"Idle", "Passive", "Lvl 1", "Lvl 2", "Lvl 3"};

    private final Spring glow = Spring.ui(0.0f);

    public HeatView(StudioContext ctx) {
        super(ctx);
    }

    @Override
    public String id() {
        return "heat";
    }

    @Override
    public String label() {
        return "Heat";
    }

    @Override
    public void drawIcon(PanelSurface c, float cx, float cy, int color) {
        Shapes.arc(c, cx, cy, 5.5f, 1.3f, 0.0f, (float) Math.PI * 2.0f, Tokens.alpha(color, 0.55f));
        Shapes.circle(c, cx, cy, 2.4f, color);
    }

    @Override
    protected void build() {
    }

    @Override
    public void update(float dt) {
        super.update(dt);
        final Bank bank = read();
        this.glow.set(bank.primary() == null ? 0.0f : bank.primary().heatFraction()).step(dt);
    }

    @Override
    protected void drawContent(PanelSurface c) {
        paint(c, this.area, read(), this.ctx.time, this.glow.value());
    }

    record Element(String name, boolean boiler, int tier, int maxTier, int reachableTier,
                   float suDraw, float rpm, float chargePct,
                   boolean coilPresent, float coilHealthPct, float coilSeconds,
                   boolean cooking, float cookPct, float cookSeconds, boolean cookDone,
                   String workpiece,
                   float waterSupply, int engines, int whistles) {
        float heatFraction() {
            return this.maxTier <= 0 ? 0.0f
                    : Math.max(0.0f, Math.min(1.0f, this.tier / (float) this.maxTier));
        }

        String tierName() {
            final String[] names = this.boiler ? BOILER_TIERS : INDUCTION_TIERS;
            return this.tier >= 0 && this.tier < names.length ? names[this.tier] : "Tier " + this.tier;
        }

        String state() {
            if (this.tier <= 0) {
                return "COLD";
            }
            if (!this.boiler && !this.coilPresent) {
                return "NO COIL";
            }
            if (this.cookDone) {
                return "READY";
            }
            return this.cooking ? "COOKING" : "HOT";
        }

        int stateColor() {
            if (this.tier <= 0 || (!this.boiler && !this.coilPresent)) {
                return Tokens.CRITICAL;
            }
            return this.cookDone ? Tokens.GOOD : PageArt.heatColor(heatFraction());
        }

        boolean coilCritical() {
            return !this.boiler && this.coilPresent && this.coilHealthPct < 20.0f;
        }
    }

    record Bank(List<Element> elements) {
        boolean isEmpty() {
            return this.elements.isEmpty();
        }

        Element primary() {
            return this.elements.isEmpty() ? null : this.elements.get(0);
        }
    }

    private Bank read() {
        final List<Element> elements = new ArrayList<>();
        for (final SourceProbe.Unit unit : SourceProbe.byFamily(this.ctx.panel, FAMILIES)) {
            final boolean boiler = unit.type().contains("Boiler");
            final int tier = Math.max(0, Math.round(unit.value("heat_level", 0.0f)));

            final int reachable = boiler
                    ? Math.max(tier, Math.round(unit.value("theoretical_heat", tier)))
                    : tier;
            elements.add(new Element(unit.label(), boiler, tier,
                    boiler ? BOILER_TIERS.length - 1 : INDUCTION_TIERS.length - 1, reachable,
                    unit.value("draw_su", 0.0f),
                    unit.value("rpm", 0.0f),
                    unit.value("charge_pct", 0.0f),
                    unit.flag("coil_present"),
                    unit.value("coil_health_pct", 0.0f),
                    unit.value("coil_seconds", 0.0f),
                    unit.flag("cooking"),
                    unit.value("cook_pct", 0.0f),
                    unit.value("cook_seconds", 0.0f),
                    unit.flag("cook_done"),
                    unit.labelOf("cook_item", ""),
                    unit.value("water_supply", 0.0f),
                    Math.round(unit.value("engines", 0.0f)),
                    Math.round(unit.value("whistles", 0.0f))));
        }
        return new Bank(elements);
    }

    static void paint(PanelSurface c, Rect area, Bank bank, float time, float glow) {
        c.text("Heat", area.x, area.y, Tokens.INK, Text.SUB);
        c.text(bank.isEmpty() ? "No heater or boiler linked"
                        : bank.elements().size()
                                + (bank.elements().size() == 1 ? " element" : " elements"),
                area.x, area.y + 20.0f, Tokens.INK_3, Text.META);

        final Rect body = area.inset(0.0f, 46.0f, 0.0f, 0.0f);
        if (bank.isEmpty()) {
            drawEmpty(c, body.top(104.0f));
            return;
        }

        final Element element = bank.primary();
        final float mainH = Math.max(140.0f, Math.min(body.h * 0.58f, 250.0f));
        drawMain(c, body.top(mainH), element, time, glow);

        final Rect rest = body.below(mainH + Tokens.S3);
        if (rest.h > 40.0f) {
            drawStats(c, rest.top(46.0f), element);
        }
        if (rest.h > 96.0f && bank.elements().size() > 1) {
            drawOthers(c, rest.below(52.0f), bank);
        }
    }

    private static void drawMain(PanelSurface c, Rect a, Element e, float time, float glow) {
        if (a.isEmpty()) {
            return;
        }
        Shapes.card(c, a, Tokens.R_CARD, Tokens.SURFACE, Tokens.LINE);
        final Rect inner = a.inset(Tokens.S3);
        PageArt.machineHeader(c, inner, e.name(), e.state(), e.stateColor());

        final Rect scene = inner.below(22.0f);
        if (scene.h < 60.0f) {
            return;
        }
        final float coreW = Math.min(scene.w * 0.42f, scene.h * 1.05f);
        drawCore(c, scene.left(coreW), e, time, glow);
        drawLadder(c, scene.afterLeft(coreW + Tokens.S4), e);
    }

    private static void drawCore(PanelSurface c, Rect a, Element e, float time, float glow) {
        final float cx = a.centerX();
        final float cy = a.y + a.h * 0.46f;
        final float radius = Math.min(a.w, a.h * 1.1f) * 0.36f;
        if (radius < 12.0f) {
            return;
        }

        PageArt.element(c, cx, cy, radius, glow, time);

        if (!e.boiler()) {
            final float ringR = radius + 8.0f;
            final float health = Math.max(0.0f, Math.min(1.0f, e.coilHealthPct() / 100.0f));
            final int coilColor = !e.coilPresent() ? Tokens.CRITICAL
                    : health < 0.20f ? Tokens.CRITICAL : health < 0.45f ? Tokens.CAUTION : Tokens.GOOD;
            Shapes.arc(c, cx, cy, ringR, 3.0f, 0.0f, (float) Math.PI * 2.0f,
                    Tokens.alpha(Tokens.LINE_HI, 0.9f));
            if (e.coilPresent() && health > 0.001f) {
                final float start = (float) (-Math.PI * 0.5);
                Shapes.arc(c, cx, cy, ringR, 3.0f, start, (float) (Math.PI * 2.0 * health), coilColor);

                if (e.coilCritical()) {
                    final float pulse = 0.4f + 0.6f * (float) Math.abs(Math.sin(time * 3.4));
                    Shapes.arc(c, cx, cy, ringR + 4.0f, 1.6f, start,
                            (float) (Math.PI * 2.0 * health), Tokens.alpha(Tokens.CRITICAL, pulse));
                }
            }
        }

        c.textCenterV(e.tierName(), cx, a.bottom() - 20.0f,
                e.tier() <= 0 ? Tokens.INK_3 : PageArt.heatColor(e.heatFraction()), Text.BODY);
        final String sub = e.boiler()
                ? e.engines() + " engines, " + e.whistles() + " whistles"
                : e.coilPresent()
                        ? "coil " + Math.round(e.coilHealthPct()) + "%"
                        : "no coil fitted";
        c.textCenterV(sub, cx, a.bottom() - 7.0f, Tokens.INK_3, Text.MICRO);
    }

    private static void drawLadder(PanelSurface c, Rect a, Element e) {
        if (a.w < 60.0f) {
            return;
        }
        c.text(Text.track("Heat level"), a.x, a.y, Tokens.INK_3, Text.MICRO);

        final int rungs = e.maxTier();
        final float top = a.y + 15.0f;
        final float rungH = Math.min(20.0f, (a.h * 0.52f) / Math.max(1, rungs));
        for (int i = rungs; i >= 1; i--) {
            final Rect r = new Rect(a.x, top + (rungs - i) * (rungH + 3.0f),
                    Math.min(a.w * 0.62f, 150.0f), rungH);
            if (r.bottom() > a.bottom()) {
                break;
            }
            final boolean held = i <= e.tier();
            final boolean reachable = i <= e.reachableTier();
            final int color = PageArt.heatColor(i / (float) rungs);
            if (held) {
                Shapes.roundedRect(c, r, 3.0f, Tokens.alpha(color, 0.85f));
            } else {
                Shapes.roundedRect(c, r, 3.0f, Tokens.alpha(Tokens.LINE, 0.8f));
                if (reachable) {
                    Shapes.roundedRectOutline(c, r, 3.0f, 1.0f, Tokens.alpha(color, 0.55f));
                }
            }
            final String name = e.boiler() ? BOILER_TIERS[i] : INDUCTION_TIERS[i];
            c.textV(name, r.x + 8.0f, r.centerY(),
                    held ? 0xFF14100C : reachable ? Tokens.INK_2 : Tokens.INK_3, Text.MICRO);
        }

        if (e.boiler()) {
            if (e.reachableTier() > e.tier()) {
                c.text("short of water for level " + e.reachableTier(), a.x,
                        a.bottom() - 22.0f, Tokens.CAUTION, Text.MICRO);
            }
            c.text("water supply " + Text.value(e.waterSupply()), a.x, a.bottom() - 10.0f,
                    Tokens.INK_3, Text.MICRO);
            return;
        }
        drawWorkpiece(c, new Rect(a.x, a.bottom() - 44.0f, a.w, 44.0f), e);
    }

    private static void drawWorkpiece(PanelSurface c, Rect a, Element e) {
        if (a.h < 24.0f) {
            return;
        }
        if (!e.cooking() && !e.cookDone()) {
            c.textV("Nothing loaded", a.x, a.centerY(), Tokens.INK_3, Text.MICRO);
            return;
        }
        final float progress = e.cookDone() ? 1.0f
                : Math.max(0.0f, Math.min(1.0f, e.cookPct() / 100.0f));
        final int color = e.cookDone() ? Tokens.GOOD : PageArt.heatColor(0.7f);

        final float r = Math.min(16.0f, a.h * 0.42f);
        final float cx = a.x + r + 2.0f;
        final float cy = a.centerY();
        Shapes.arc(c, cx, cy, r, 3.0f, 0.0f, (float) Math.PI * 2.0f, Tokens.LINE);
        if (progress > 0.001f) {
            Shapes.arc(c, cx, cy, r, 3.0f, (float) (-Math.PI * 0.5),
                    (float) (Math.PI * 2.0 * progress), color);
        }
        c.textCenterV(Math.round(progress * 100.0f) + "%", cx, cy, color, Text.MICRO);

        final String name = e.workpiece() == null || e.workpiece().isEmpty()
                ? "Workpiece" : e.workpiece();
        c.textV(Text.truncate(c, name, a.w - r * 2.0f - 16.0f, Text.META),
                cx + r + 8.0f, cy - 5.0f, Tokens.INK, Text.META);
        final String remaining = e.cookDone() ? "ready to collect"
                : PageArt.duration(e.cookSeconds(), 3600.0f) == null
                        ? "working" : PageArt.duration(e.cookSeconds(), 3600.0f) + " left";
        c.textV(remaining, cx + r + 8.0f, cy + 6.0f, Tokens.INK_3, Text.MICRO);
    }

    private static void drawStats(PanelSurface c, Rect a, Element e) {
        final float colW = a.w / 4.0f;
        if (e.boiler()) {
            PageArt.stat(c, new Rect(a.x, a.y, colW - 8.0f, a.h), "Level",
                    String.valueOf(e.tier()), "of " + e.maxTier(),
                    PageArt.heatColor(e.heatFraction()), Text.SUB);
            PageArt.stat(c, new Rect(a.x + colW, a.y, colW - 8.0f, a.h), "Water",
                    Text.value(e.waterSupply()), "", Tokens.SERIES[0], Text.SUB);
            PageArt.stat(c, new Rect(a.x + colW * 2.0f, a.y, colW - 8.0f, a.h), "Engines",
                    String.valueOf(e.engines()), "", Tokens.INK, Text.SUB);
            PageArt.stat(c, new Rect(a.x + colW * 3.0f, a.y, colW - 8.0f, a.h), "Whistles",
                    String.valueOf(e.whistles()), "", Tokens.INK, Text.SUB);
            return;
        }
        PageArt.stat(c, new Rect(a.x, a.y, colW - 8.0f, a.h), "Charge",
                Text.value(e.chargePct()), "%", PageArt.heatColor(e.heatFraction()), Text.SUB);
        PageArt.stat(c, new Rect(a.x + colW, a.y, colW - 8.0f, a.h), "Stress draw",
                Text.value(e.suDraw()), "SU", Tokens.SERIES[0], Text.SUB);
        PageArt.stat(c, new Rect(a.x + colW * 2.0f, a.y, colW - 8.0f, a.h), "Speed",
                Text.value(Math.abs(e.rpm())), "rpm", Tokens.INK, Text.SUB);

        final String life = !e.coilPresent() ? "none"
                : PageArt.duration(e.coilSeconds(), 86_400.0f) == null
                        ? Math.round(e.coilHealthPct()) + "%"
                        : PageArt.duration(e.coilSeconds(), 86_400.0f);
        PageArt.stat(c, new Rect(a.x + colW * 3.0f, a.y, colW - 8.0f, a.h), "Coil life",
                life, "", e.coilCritical() || !e.coilPresent() ? Tokens.CRITICAL : Tokens.INK,
                Text.SUB);
    }

    private static void drawOthers(PanelSurface c, Rect a, Bank bank) {
        if (a.isEmpty()) {
            return;
        }
        c.text(Text.track("Other elements"), a.x, a.y, Tokens.INK_3, Text.MICRO);
        final float top = a.y + 15.0f;
        final float rowH = 22.0f;
        for (int i = 1; i < bank.elements().size() && top + (i - 1) * rowH + rowH <= a.bottom(); i++) {
            final Element e = bank.elements().get(i);
            final Rect r = new Rect(a.x, top + (i - 1) * rowH, a.w, rowH - 3.0f);
            Shapes.card(c, r, Tokens.R_CHIP, Tokens.SURFACE, Tokens.LINE);
            Shapes.circle(c, r.x + 10.0f, r.centerY(), 3.0f, e.stateColor());
            c.textV(Text.truncate(c, e.name(), r.w * 0.4f, Text.MICRO), r.x + 20.0f, r.centerY(),
                    Tokens.INK_2, Text.MICRO);
            c.textRightV(e.tierName(), r.right() - 10.0f, r.centerY(),
                    e.tier() <= 0 ? Tokens.INK_3 : PageArt.heatColor(e.heatFraction()), Text.MICRO);
        }
    }

    private static void drawEmpty(PanelSurface c, Rect box) {
        Shapes.card(c, box, Tokens.R_CARD, Tokens.SURFACE, Tokens.LINE);
        c.textCenter("No heat source linked", box.centerX(), box.centerY() - 14.0f,
                Tokens.INK_2, Text.BODY);
        c.textCenter("Point a Display Link from an Induction Heater or a Steam Boiler here.",
                box.centerX(), box.centerY() + 2.0f, Tokens.INK_3, Text.MICRO);
    }
}
