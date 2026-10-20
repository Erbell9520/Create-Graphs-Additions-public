package com.createstats.gui.battery;

import com.createstats.gui.pdu.PduRect;
import com.createstats.gui.pdu.PduTheme;
import com.createstats.power.BatteryControllerPlan;
import com.createstats.power.BatteryControllerPorts;
import com.createstats.power.BatteryOutputMode;
import com.createstats.power.FeBandSettings;
import com.createstats.power.FePriority;
import com.createstats.power.FeReserveBand;
import com.createstats.power.FeReserveReport;
import com.createstats.power.ReserveProjection;
import com.createstats.power.ReserveWords;

import java.util.List;

public final class BatteryPages {
    private BatteryPages() {
    }

    public enum Tab {
        NOW("Now"), FORECAST("Forecast"), RULES("How it decides");

        public final String label;

        Tab(String label) {
            this.label = label;
        }
    }

    public static final int A_TAB = -10;
    public static final int A_SELECT = -11;
    public static final int A_RANGE = -12;

    public static final int A_FLOOR_TRACK = -13;
    public static final int A_LIMIT_TRACK = -14;
    public static final int A_DESELECT = -15;

    public static final int A_BAND_TRACK = -16;

    public static final int A_CAP_TRACK = -17;

    public static final int A_BAND_HANDLE = -18;

    public static final int A_FLOOR_PIN = -19;

    public record Hit(PduRect r, int action, int index, int value) {
    }

    public record Region(String id, PduRect r) {
    }

    public record State(Tab tab, int selected, int mouseX, int mouseY, BatteryHistory history, int range,
                        int portRate, List<Hit> hits, List<Region> regions) {
        public State(Tab tab, int selected, int mouseX, int mouseY, BatteryHistory history, int range,
                     int portRate, List<Hit> hits) {
            this(tab, selected, mouseX, mouseY, history, range, portRate, hits, null);
        }

        void region(String id, PduRect r) {
            if (regions != null) {
                regions.add(new Region(id, r));
            }
        }
    }

    public static final int HEADER_H = 22;
    public static final int HEADLINE_H = 28;
    public static final int FOOTER_H = 14;
    private static final int PAD = 8;

    private static final int BG = PduTheme.BG_DEEP;
    private static final int CARD = PduTheme.BG_PANEL;
    private static final int RAISED = PduTheme.BG_RAISED;
    private static final int STROKE = PduTheme.STROKE;
    private static final int HOT = PduTheme.STROKE_HOT;
    private static final int TEXT = PduTheme.TEXT;
    private static final int DIM = PduTheme.TEXT_DIM;
    private static final int FAINT = PduTheme.TEXT_FAINT;
    private static final int OK = PduTheme.OK;
    private static final int WARN = PduTheme.WARN;
    private static final int CRIT = PduTheme.CRIT;
    private static final int INFO = PduTheme.INFO;
    private static final int ACCENT = PduTheme.ACCENT;

    static final int STORE = 0xFFA884D8;

    static int alpha(int argb, int a) {
        return (argb & 0x00FFFFFF) | ((a & 0xFF) << 24);
    }

    private static String fe(long v) {
        return ReserveWords.fe(v);
    }

    public static void draw(BatteryCanvas c, PduRect p, BatteryView v, State s) {
        c.fill(p.x(), p.y(), p.right(), p.bottom(), BG);
        c.outline(p.x(), p.y(), p.w(), p.h(), STROKE);
        header(c, p, v, s);
        headline(c, p, v, s);
        final PduRect body = body(p);
        switch (s.tab()) {
            case NOW -> nowPage(c, body, v, s);
            case FORECAST -> forecastPage(c, body, v, s);
            case RULES -> rulesPage(c, body, v, s);
        }
        footer(c, p, v, s);
    }

    public static PduRect body(PduRect p) {
        final int top = p.y() + HEADER_H + HEADLINE_H + 4;
        return new PduRect(p.x() + PAD, top, p.w() - PAD * 2, Math.max(0, p.bottom() - FOOTER_H - 4 - top));
    }

    private static void header(BatteryCanvas c, PduRect p, BatteryView v, State s) {
        c.fill(p.x() + 1, p.y() + 1, p.right() - 1, p.y() + HEADER_H, CARD);
        c.fill(p.x() + 1, p.y() + HEADER_H, p.right() - 1, p.y() + HEADER_H + 1, STROKE);
        final String title = "BATTERY CONTROLLER";
        c.bold(title, p.x() + PAD, p.y() + 7, TEXT);
        int x = p.x() + PAD + c.boldWidth(title) + 8;
        if (v.capacity() > 0) {
            final String word = v.stance().label().toUpperCase(java.util.Locale.ROOT);
            final int col = stanceColour(v.stance());
            final int w = c.width(word) + 8;
            c.fill(x, p.y() + 5, x + w, p.y() + 17, alpha(col, 0x2A));
            c.outline(x, p.y() + 5, w, 12, alpha(col, 0xAA));
            c.text(word, x + 4, p.y() + 7, col);
            s.region("stance", new PduRect(x, p.y() + 5, w, 12));
        }

        int tx = p.right() - PAD;
        final Tab[] tabs = Tab.values();
        for (int i = tabs.length - 1; i >= 0; i--) {
            final Tab t = tabs[i];
            final int w = c.width(t.label) + 14;
            tx -= w;
            final PduRect r = new PduRect(tx, p.y() + 4, w, 15);
            final boolean on = s.tab() == t;
            final boolean hover = r.contains(s.mouseX(), s.mouseY());
            c.fill(r.x(), r.y(), r.right(), r.bottom(), on ? RAISED : hover ? CARD : BG);
            c.outline(r.x(), r.y(), r.w(), r.h(), on ? alpha(ACCENT, 0xCC) : hover ? HOT : STROKE);
            c.text(t.label, r.x() + 7, r.y() + 4, on ? TEXT : DIM);
            if (on) {
                c.fill(r.x() + 4, r.bottom() - 2, r.right() - 4, r.bottom() - 1, ACCENT);
            }
            s.hits().add(new Hit(r, A_TAB, t.ordinal(), t.ordinal()));
            tx -= 3;
        }
    }

    private static void headline(BatteryCanvas c, PduRect p, BatteryView v, State s) {
        final int y = p.y() + HEADER_H + 4;
        s.region("headline", new PduRect(p.x() + PAD, y, p.w() - PAD * 2, 20));
        final int sev = BatteryText.severity(v);
        final int col = sev == 2 ? CRIT : sev == 1 ? WARN : OK;
        c.fill(p.x() + PAD, y, p.x() + PAD + 2, y + 20, col);
        final int tx = p.x() + PAD + 7;
        final int max = p.right() - PAD - tx;
        c.text(c.fit(BatteryText.now(v), max), tx, y + 1, TEXT);
        c.text(c.fit(BatteryText.foreseen(v), max), tx, y + 12, DIM);
    }

    private static void footer(BatteryCanvas c, PduRect p, BatteryView v, State s) {
        final int y = p.bottom() - FOOTER_H;
        s.region("footer", new PduRect(p.x() + 1, y, p.w() - 2, FOOTER_H - 1));
        c.fill(p.x() + 1, y, p.right() - 1, p.bottom() - 1, CARD);
        c.fill(p.x() + 1, y, p.right() - 1, y + 1, STROKE);
        final boolean chosen = s.selected() >= 0 && s.selected() < v.outputs().size();
        final String hint = switch (s.tab()) {
            case NOW -> chosen ? "Output " + (s.selected() + 1) + ": "
                    + BatteryText.outputWhy(v.outputs().get(s.selected()), v) + ". Changes apply at once."
                    : "Drag a band edge or an output's pin, or set the band below. Click an output for its controls.";
            case FORECAST -> "Solar follows the sun and today's weather, burners their fuel, demand holds. Next "
                    + ReserveWords.horizon() + ".";
            case RULES -> "The controller runs these five steps every tick, in this order.";
        };
        c.text(c.fit(hint, p.w() - PAD * 2), p.x() + PAD, y + 3, FAINT);
    }

    static final int INSPECTOR_H = 52;
    static final int BALANCE_H = 38;

    private static void nowPage(BatteryCanvas c, PduRect b, BatteryView v, State s) {
        final boolean small = b.h() < 270;
        final int gapY = small ? 4 : 6;

        final int balanceH = small ? 24 : BALANCE_H;
        final PduRect bal = new PduRect(b.x(), b.y(), b.w(), balanceH);
        balance(small ? withoutTextFrom(c, bal.y() + 26) : c, bal, v);
        s.region("balance", bal);

        final boolean outputOpen = s.selected() >= 0 && s.selected() < v.outputs().size()
                && v.outputs().get(s.selected()).wired();
        final PduRect insp = new PduRect(b.x(), b.bottom() - INSPECTOR_H, b.w(), INSPECTOR_H);

        int rowHot = -1;
        if (!outputOpen && v.capacity() > 0) {
            for (int i = 0; i < 3; i++) {
                final int ry = insp.y() + BAND_ROW_TOP + i * BAND_ROW_H;
                if (s.mouseX() >= insp.x() && s.mouseX() < insp.right() && s.mouseY() >= ry - 1
                        && s.mouseY() < ry + BAND_ROW_H - 1) {
                    rowHot = i;
                }
            }
        }
        final PduRect bandR = new PduRect(b.x(), bal.bottom() + gapY, b.w(), small ? BAND_H_SMALL : BAND_H);
        final int hot = bandRuler(c, bandR, v, s, rowHot);
        s.region("band", bandR);

        final int top = bandR.bottom() + gapY;
        final int colH = Math.max(30, insp.y() - gapY - top);
        final int gap = 6;
        final int inW = Math.round((b.w() - gap) * 0.38f);
        final PduRect inCol = new PduRect(b.x(), top, inW, colH);
        final PduRect outCol = new PduRect(inCol.right() + gap, top, b.w() - inW - gap, colH);
        columnHeading(c, inCol, "INPUTS");
        columnHeading(c, outCol, "OUTPUTS");
        s.region("inputs", inCol);
        s.region("outputs", outCol);
        final int rowTop = top + 11;
        final int rowGap = small ? 3 : 4;
        final int rowH = (colH - 11 - rowGap * 2) / 3;
        for (int i = 0; i < 3; i++) {
            if (i < v.inputs().size()) {
                inputRow(c, new PduRect(inCol.x(), rowTop + i * (rowH + rowGap), inCol.w(), rowH), v.inputs().get(i),
                        v, s);
            }
            if (i < v.outputs().size()) {
                outputRow(c, new PduRect(outCol.x(), rowTop + i * (rowH + rowGap), outCol.w(), rowH),
                        v.outputs().get(i), v, s);
            }
        }
        if (outputOpen) {
            inspector(c, insp, v, s);
        } else {
            bandPanel(c, insp, v, s, hot);
        }
        s.region("inspector", insp);
    }

    private static BatteryCanvas withoutTextFrom(BatteryCanvas c, int fromY) {
        return new BatteryCanvas() {
            @Override
            public void fill(int x0, int y0, int x1, int y1, int argb) {
                c.fill(x0, y0, x1, y1, argb);
            }

            @Override
            public void text(String text, int x, int y, int argb) {
                if (y < fromY) {
                    c.text(text, x, y, argb);
                }
            }

            @Override
            public int width(String text) {
                return c.width(text);
            }
        };
    }

    private static void columnHeading(BatteryCanvas c, PduRect col, String text) {
        c.text(text, col.x() + 1, col.y(), FAINT);
    }

    private static void balance(BatteryCanvas c, PduRect r, BatteryView v) {
        final int labelW = 40;
        final int bx = r.x() + labelW;
        final int bw = Math.max(10, r.w() - labelW);
        final int gen = Math.max(0, v.generation());
        final int drawn = Math.max(0, v.bankDrawn());
        final int out = Math.max(0, v.delivered());
        final int stores = Math.max(0, v.storeCharged());
        final int banked = Math.max(0, v.bankCharged());
        int unmet = 0;
        for (BatteryView.Output o : v.outputs()) {
            if (o.wired() && o.enabled() && o.isShort()) {
                unmet = com.createstats.power.FeRates.plus(unmet, Math.max(0, o.asked() - o.flow()));
            }
        }
        final long supply = (long) gen + drawn;
        final long use = (long) out + stores + banked + unmet;
        final long scale = Math.max(1, Math.max(supply, use));

        c.text("SUPPLY", r.x(), r.y() + 2, FAINT);
        c.text("USE", r.x(), r.y() + 15, FAINT);
        final int y1 = r.y() + 1;
        final int y2 = r.y() + 14;
        final int h = 10;
        c.fill(bx, y1, bx + bw, y1 + h, CARD);
        c.fill(bx, y2, bx + bw, y2 + h, CARD);
        int x = bx;
        x = segment(c, x, y1, h, bw, gen, scale, OK, "generated " + fe(gen));
        segment(c, x, y1, h, bw, drawn, scale, INFO, "from banks " + fe(drawn));
        x = bx;
        for (BatteryView.Output o : v.outputs()) {
            if (o.flow() > 0) {
                x = segment(c, x, y2, h, bw, o.flow(), scale, reasonColour(o), "Output " + (o.index() + 1) + ": " + fe(o.flow()));
                c.fill(x - 1, y2, x, y2 + h, BG);
            }
        }
        x = segment(c, x, y2, h, bw, stores, scale, STORE, "capacitors " + fe(stores));
        x = segment(c, x, y2, h, bw, banked, scale, alpha(INFO, 0x99), "into banks " + fe(banked));
        if (unmet > 0) {
            final int w = (int) Math.max(2, Math.round(bw * (unmet / (double) scale)));
            final int end = Math.min(bx + bw, x + w);
            for (int hx = x; hx < end; hx++) {
                for (int hy = y2; hy < y2 + h; hy++) {
                    if (((hx + hy) & 3) == 0) {
                        c.fill(hx, hy, hx + 1, hy + 1, WARN);
                    }
                }
            }
            c.outline(x, y2, Math.max(1, end - x), h, alpha(WARN, 0xAA));
            label(c, x, y2, end - x, "short " + fe(unmet), WARN);
        }
        c.outline(bx, y1, bw, h, STROKE);
        c.outline(bx, y2, bw, h, STROKE);

        final String totals = "In " + fe(supply) + " FE/t" + (drawn > 0 ? " (" + fe(drawn) + " from the banks)" : "")
                + "     Out " + fe(out) + (stores + banked > 0 ? ", stored " + fe(stores + banked) : "")
                + (unmet > 0 ? ", short " + fe(unmet) : "");
        c.text(c.fit(totals, bw), bx, r.y() + 27, DIM);
    }

    private static int segment(BatteryCanvas c, int x, int y, int h, int bw, long value, long scale, int colour,
                               String text) {
        if (value <= 0) {
            return x;
        }
        final int w = (int) Math.max(1, Math.round(bw * (value / (double) scale)));
        c.fill(x, y, x + w, y + h, alpha(colour, 0x66));
        c.fill(x, y, x + w, y + 1, colour);
        label(c, x, y, w, text, TEXT);
        return x + w;
    }

    private static void label(BatteryCanvas c, int x, int y, int w, String text, int colour) {
        if (c.width(text) + 4 <= w) {
            c.text(text, x + 3, y + 1, colour);
        }
    }

    private static void inputRow(BatteryCanvas c, PduRect r, BatteryView.Input in, BatteryView v, State s) {
        card(c, r, false, false);
        s.region("input." + in.index(), r);
        final int badgeCol = !in.enabled() ? CRIT : in.strayPanel() ? WARN : in.flow() > 0 || in.storeFlow() > 0 ? OK
                : in.wired() ? INFO : FAINT;
        badge(c, r.x(), r.y(), r.h(), BatteryControllerPorts.label(in.index()), badgeCol, true);
        final int tx = r.x() + 17;
        final PduRect sw = new PduRect(r.right() - 27, r.y() + 4, 22, 10);
        toggle(c, sw, in.enabled(), s);
        s.hits().add(new Hit(sw, com.createstats.network.BatteryControllerSetPacket.ACTION_INPUT_ENABLED,
                in.index(), in.enabled() ? 0 : 1));
        c.text(c.fit(BatteryText.inputWhat(in), sw.x() - tx - 3), tx, r.y() + 5,
                in.strayPanel() ? WARN : in.wired() ? TEXT : FAINT);
        final String flow = !in.enabled() ? "Isolated" : in.flow() > 0 ? "+" + fe(in.flow()) + " FE/t in"
                : in.tanks() > 0 ? (in.bankMove() > 0 ? "Banks moving " + fe(in.bankMove()) + " FE/t" : "Banks idle")
                : in.wired() ? "Idle" : "";
        c.text(c.fit(flow, r.right() - tx - 4), tx, r.y() + 16, !in.enabled() ? CRIT : in.flow() > 0 ? OK : DIM);
        if (r.h() >= 38) {
            final int barY = r.y() + 27;
            final int barW = r.right() - tx - 5;
            c.fill(tx, barY, tx + barW, barY + 3, BG);
            final int total = Math.max(1, v.generation());
            if (in.enabled() && in.flow() > 0) {
                c.fill(tx, barY, tx + Math.round(barW * Math.min(1.0f, in.flow() / (float) total)), barY + 3, OK);
            }
            if (in.storeFlow() > 0 && r.h() >= 44) {
                c.text(c.fit("capacitor +" + fe(in.storeFlow()), barW), tx, barY + 5, STORE);
            }
        }
    }

    private static void outputRow(BatteryCanvas c, PduRect r, BatteryView.Output o, BatteryView v, State s) {
        final boolean selected = s.selected() == o.index();
        final boolean hover = r.contains(s.mouseX(), s.mouseY()) && o.wired();
        card(c, r, selected, hover);
        s.region("output." + o.index(), r);
        final int col = reasonColour(o);
        badge(c, r.right() - 14, r.y(), r.h(), String.valueOf(o.index() + 1), col, false);
        final int tx = r.x() + 5;
        final int right = r.right() - 18;
        final String tier = o.tier().getDisplayName().toUpperCase(java.util.Locale.ROOT);
        if (o.wired()) {
            final int tw = c.width(tier) + 6;
            c.outline(right - tw, r.y() + 3, tw, 11, alpha(tierColour(o.tier()), 0x88));
            c.text(tier, right - tw + 3, r.y() + 5, tierColour(o.tier()));
            c.text(c.fit(BatteryText.outputWhat(o), right - tw - tx - 4), tx, r.y() + 5, TEXT);
        } else {
            c.text("Not wired", tx, r.y() + 5, FAINT);
        }
        if (!o.wired()) {
            return;
        }

        final int barY = r.y() + 16;
        final String nums = fe(o.flow()) + " / " + fe(o.asked());
        final int numW = c.width(nums);
        final int barW = Math.max(10, right - tx - numW - 6);
        c.fill(tx, barY, tx + barW, barY + 5, BG);
        final int scale = Math.max(1, Math.max(o.asked(), o.flow()));
        final int served = Math.round(barW * Math.min(1.0f, o.flow() / (float) scale));
        final int want = Math.round(barW * Math.min(1.0f, o.asked() / (float) scale));
        if (want > served) {
            c.fill(tx + served, barY, tx + want, barY + 5, alpha(WARN, 0x88));
        }
        if (served > 0) {
            c.fill(tx, barY, tx + served, barY + 5, alpha(col, 0xCC));
        }
        c.text(nums, right - numW, barY - 1, o.flow() > 0 ? TEXT : DIM);
        if (r.h() >= 34) {
            c.text(c.fit(BatteryText.outputWhy(o, v), right - tx), tx, r.y() + 25, col == OK ? DIM : col);
        }
        if (r.h() >= 46) {
            final String setup = (o.enabled() ? "On" : "Off") + ", " + BatteryText.modeWords(o)
                    + (o.floorPct() > 0 ? ", floor " + o.floorPct() + "%" : ", no floor")
                    + (o.storeFlow() > 0 ? ", capacitor +" + fe(o.storeFlow()) : "");
            c.text(c.fit(setup, right - tx), tx, r.y() + 36, FAINT);
        }
        s.hits().add(new Hit(r, A_SELECT, o.index(), 0));
    }

    static final int BAND_H = 56;

    static final int BAND_H_SMALL = 46;

    static final int BAND_ROW_TOP = 17;
    static final int BAND_ROW_H = 11;
    private static final int RULER_H = 13;

    public static final int BAND_SLIDER_MINUTES = 120;

    public static int packRuler(int x0, int span) {
        return ((x0 & 0xFFFF) << 16) | (span & 0xFFFF);
    }

    public static int rulerX0(int packed) {
        return (packed >>> 16) & 0xFFFF;
    }

    public static int rulerSpan(int packed) {
        return packed & 0xFFFF;
    }

    private record Mark(int edge, int pct, String name, String why, int colour) {
    }

    private static int bandRuler(BatteryCanvas c, PduRect r, BatteryView v, State s, int rowHot) {
        final boolean twoLines = r.h() >= BAND_H;
        c.text("THE BAND", r.x() + 1, r.y(), FAINT);
        final int tx = r.x() + 1 + c.width("THE BAND") + 8;
        s.hits().add(new Hit(r, A_DESELECT, -1, 0));
        final int labelsY = r.y() + 12;
        final int labelsH = twoLines ? 18 : 9;
        final int rulerY = labelsY + labelsH + 3;
        final int x0 = r.x() + 1;
        final int span = r.w() - 3;
        if (v.capacity() <= 0) {
            c.text(c.fit("No banks: wire a Capacitor Bank to an input, and the band has a reserve to steer.",
                    r.right() - tx - 1), tx, r.y(), WARN);
            c.fill(x0, rulerY, x0 + span + 1, rulerY + RULER_H, BG);
            c.outline(x0, rulerY, span + 1, RULER_H, STROKE);
            return -1;
        }
        c.text(c.fit(bandMeaning(v), r.right() - tx - 1), tx, r.y(), stanceColour(v.stance()));

        final FeBandSettings bs = v.band();
        final FeReserveBand.Band sized = bs.band(v.capacity(), v.bandWant(), v.fullRate(),
                FeReserveBand.DEFAULT_RIDE_TICKS);
        final int soc = v.socPct();
        final int floor = sized.floorPct();
        final int topPct = sized.ceilingPct();

        final int healthy = Math.max(topPct, v.healthyPct());
        final boolean held = v.holdPct() > floor;
        final int effFloor = held ? v.holdPct() : floor;
        final int topEff = Math.max(effFloor, topPct);
        final int fx = rulerPx(x0, span, soc);
        final boolean noWant = bs.unit() == FeBandSettings.Unit.MINUTES && v.bandWant() <= 0;
        final int[] edgePct = {floor, topPct, healthy};

        int hot = rowHot;
        for (int e = 0; e < 3; e++) {
            if (noWant && e < 2) {
                continue;
            }
            final int ex = rulerPx(x0, span, edgePct[e]);
            if (new PduRect(ex - 4, rulerY - 6, 9, RULER_H + 7).contains(s.mouseX(), s.mouseY())) {
                hot = e;
            }
        }

        c.fill(x0, rulerY, x0 + span + 1, rulerY + RULER_H, BG);
        rulerZone(c, x0, span, rulerY, fx, 0, effFloor, CRIT);
        rulerZone(c, x0, span, rulerY, fx, effFloor, topEff, WARN);
        rulerZone(c, x0, span, rulerY, fx, topEff, healthy, INFO);
        rulerZone(c, x0, span, rulerY, fx, healthy, 100, OK);

        if (held) {
            final int hx1 = rulerPx(x0, span, v.holdPct());
            for (int hx = rulerPx(x0, span, floor); hx < hx1; hx++) {
                for (int hy = rulerY + 1; hy < rulerY + RULER_H - 1; hy++) {
                    if (((hx + hy) & 3) == 0) {
                        c.fill(hx, hy, hx + 1, hy + 1, alpha(INFO, 0xDD));
                    }
                }
            }
        }

        for (int pct = 10; pct < 100; pct += 10) {
            final int x = rulerPx(x0, span, pct);
            c.fill(x, rulerY + RULER_H - 3, x + 1, rulerY + RULER_H - 1, alpha(TEXT, 0x50));
        }
        rulerWord(c, x0, span, rulerY, fx, 0, effFloor, held ? "HELD" : "CRITICAL ONLY", CRIT);
        rulerWord(c, x0, span, rulerY, fx, effFloor, topEff, "NOTHING COMES BACK", WARN);
        rulerWord(c, x0, span, rulerY, fx, topEff, healthy, "DRAWN DOWN", INFO);
        rulerWord(c, x0, span, rulerY, fx, healthy, 100, "SPENT FREELY", OK);
        c.outline(x0, rulerY, span + 1, RULER_H, STROKE);
        s.region("ruler", new PduRect(x0, rulerY, span + 1, RULER_H));

        final List<int[]> pins = new java.util.ArrayList<>();
        for (BatteryView.Output o : v.outputs()) {
            if (!o.wired() || o.floorPct() <= 0) {
                continue;
            }
            final int ox = rulerPx(x0, span, o.floorPct());
            final boolean chosen = s.selected() == o.index();
            final int col = chosen ? ACCENT : soc < o.floorPct() ? WARN : DIM;
            for (int y = rulerY + 1; y < rulerY + RULER_H - 1; y += 2) {
                c.fill(ox, y, ox + 1, y + 1, col);
            }
            final String n = String.valueOf(o.index() + 1);
            final int pw = c.width(n) + 4;
            final PduRect pin = new PduRect(ox - pw / 2, rulerY + RULER_H + 1, pw, 10);
            final boolean hover = pin.contains(s.mouseX(), s.mouseY());
            c.fill(pin.x(), pin.y(), pin.right(), pin.bottom(), alpha(col, hover ? 0x70 : 0x40));
            c.outline(pin.x(), pin.y(), pin.w(), pin.h(), chosen || hover ? TEXT : col);
            c.text(n, pin.x() + 2, pin.y() + 1, TEXT);
            pins.add(new int[]{pin.x() - 4, pin.right() + 4});
            s.hits().add(new Hit(pin, A_FLOOR_PIN, o.index(), packRuler(x0, span)));
        }

        c.fill(fx - 2, rulerY + 1, fx - 1, rulerY + RULER_H - 1, alpha(BG, 0xCC));
        c.fill(fx + 1, rulerY + 1, fx + 2, rulerY + RULER_H - 1, alpha(BG, 0xCC));
        c.fill(fx - 1, rulerY - 1, fx + 1, rulerY + RULER_H + 1, TEXT);
        c.fill(fx - 1, rulerY + RULER_H + 1, fx + 1, rulerY + RULER_H + 2, TEXT);
        c.fill(fx - 2, rulerY + RULER_H + 2, fx + 2, rulerY + RULER_H + 3, TEXT);
        final int rate = v.bankCharged() - v.bankDrawn();
        String now = soc + "% now, " + fe(v.stored()) + (rate < 0 ? ", falling " + fe(-rate) + " FE/t"
                : rate > 0 ? ", rising " + fe(rate) + " FE/t" : "");
        int nx = fx + 5;
        if (nx + c.width(now) > r.right() || pinsCross(pins, nx, nx + c.width(now))) {
            nx = fx - 5 - c.width(now);
            if (nx < r.x() || pinsCross(pins, nx, nx + c.width(now))) {
                now = soc + "% now";
                nx = fx + 5;
                if (pinsCross(pins, nx, nx + c.width(now)) || nx + c.width(now) > r.right()) {
                    nx = fx - 5 - c.width(now);
                }
            }
        }
        c.text(now, nx, rulerY + RULER_H + 3, TEXT);

        final List<Mark> marks = new java.util.ArrayList<>();
        if (held) {
            marks.add(new Mark(5, floor, "", "", CRIT));
        } else if (floor == topPct) {
            marks.add(new Mark(0, floor, floor == 0 ? "NO FLOOR" : "FLOOR, TOP " + floor + "%", edgeWhy(v, 0, sized),
                    CRIT));
        }
        if (floor != topPct || held) {
            if (!held) {
                marks.add(new Mark(0, floor, "FLOOR " + floor + "%", edgeWhy(v, 0, sized), CRIT));
            }

            final boolean buried = held && topPct <= v.holdPct();
            marks.add(buried ? new Mark(4, topPct, "", "", WARN)
                    : new Mark(1, topPct, "TOP " + topPct + "%", edgeWhy(v, 1, sized), WARN));
        }
        if (held) {
            marks.add(new Mark(3, v.holdPct(), "HOLD " + v.holdPct() + "%", "a board's night", INFO));
        }
        marks.add(new Mark(2, healthy, "HEALTHY " + healthy + "%", edgeWhy(v, 2, sized), OK));
        marks.sort((m1, m2) -> Integer.compare(m1.pct(), m2.pct()));
        int lastRight = Integer.MIN_VALUE;
        for (Mark m : marks) {
            final int ex = rulerPx(x0, span, m.pct());

            final int dragEdge = m.edge() == 4 ? 1 : m.edge() == 5 ? 0 : m.edge();
            final boolean movable = dragEdge <= 2 && !(noWant && dragEdge < 2);
            final boolean lit = hot == dragEdge && movable;
            if (!m.name().isEmpty()) {
                final String l2 = twoLines ? m.why() : "";
                final int w = Math.max(c.width(m.name()), c.width(l2)) + 4;
                int lx = ex - 1;
                if (lx + w > r.right()) {
                    lx = ex - w + 2;
                }
                if (lx < lastRight + 6) {
                    lx = lastRight + 6;
                }

                final int tickCol = lit ? ACCENT : alpha(TEXT, 0x70);
                final int ly = labelsY + labelsH;
                c.fill(ex, ly, ex + 1, rulerY - 4, tickCol);
                if (lx > ex + 1 || lx + w < ex) {
                    for (int dx = Math.min(ex, lx + 1); dx < Math.max(ex + 1, lx + 2); dx += 2) {
                        c.fill(dx, ly, dx + 1, ly + 1, tickCol);
                    }
                }
                if (lit) {
                    c.fill(lx, labelsY - 1, lx + w, labelsY + labelsH, RAISED);
                    c.outline(lx, labelsY - 1, w, labelsH + 1, ACCENT);
                }
                c.text(m.name(), lx + 2, labelsY, lit ? TEXT : m.colour());
                if (twoLines) {
                    c.text(l2, lx + 2, labelsY + 9, lit ? ACCENT : DIM);
                }
                lastRight = lx + w;
                if (!movable && m.edge() <= 2) {
                    c.fill(ex, rulerY - 4, ex + 1, rulerY, tickCol);
                }
            }

            if (movable) {
                final int hc = lit ? ACCENT : alpha(ACCENT, m.edge() >= 4 ? 0x70 : 0xB0);
                c.fill(ex - 2, rulerY - 4, ex + 3, rulerY - 1, hc);
                c.fill(ex - 1, rulerY - 1, ex + 2, rulerY, hc);
                s.hits().add(new Hit(new PduRect(ex - 4, rulerY - 6, 9, RULER_H + 7), A_BAND_HANDLE, dragEdge,
                        packRuler(x0, span)));
            }
        }
        return hot;
    }

    private static boolean pinsCross(List<int[]> pins, int from, int to) {
        for (int[] p : pins) {
            if (from < p[1] && p[0] < to) {
                return true;
            }
        }
        return false;
    }

    private static int rulerPx(int x0, int span, int pct) {
        return x0 + Math.round(span * Math.max(0, Math.min(100, pct)) / 100.0f);
    }

    private static void rulerZone(BatteryCanvas c, int x0, int span, int y, int fx, int from, int to, int colour) {
        if (to <= from) {
            return;
        }
        final int a = rulerPx(x0, span, from);
        final int b = rulerPx(x0, span, to);
        final int lit = Math.min(b, Math.max(a, fx));
        if (lit > a) {
            c.fill(a, y + 1, lit, y + RULER_H - 1, alpha(colour, 0x9A));
        }
        if (b > lit) {
            c.fill(lit, y + 1, b, y + RULER_H - 1, alpha(colour, 0x2C));
        }
    }

    private static void rulerWord(BatteryCanvas c, int x0, int span, int y, int fx, int from, int to, String word,
                                  int colour) {
        final int a = rulerPx(x0, span, from) + 3;
        final int b = rulerPx(x0, span, to) - 3;
        final int w = c.width(word);
        int lo = a;
        int hi = b;
        if (fx > a - 3 && fx < b + 3) {
            if (fx - a >= b - fx) {
                hi = fx - 4;
            } else {
                lo = fx + 4;
            }
        }
        if (w <= hi - lo) {
            final int x = (lo + hi) / 2 - w / 2;
            c.text(word, x, y + 3, x + w <= fx ? alpha(TEXT, 0xE0) : colour);
        }
    }

    static String bandMeaning(BatteryView v) {
        final int soc = v.socPct();
        return switch (v.stance()) {
            case SURPLUS -> "Surplus at " + soc + "%: spent freely, up to " + fe(v.sustainedDraw()) + " FE/t.";
            case NOMINAL -> "Nominal at " + soc + "%: ordinary load may take " + fe(v.sustainedDraw())
                    + " FE/t, and shed machines come back.";
            case CONSERVE -> "Conserve at " + soc + "%: ordinary load may take " + fe(v.sustainedDraw())
                    + " FE/t, and nothing shed comes back.";
            case CRITICAL -> v.holdPct() > v.configuredFloorPct()
                    ? "Critical at " + soc + "%: a board's night hold keeps " + v.holdPct() + "%; only Critical draws."
                    : "Critical at " + soc + "%: only Critical draws, up to " + fe(v.criticalDraw()) + " FE/t.";
        };
    }

    private static int minutesAt(BatteryView v, int pct) {
        if (v.bandWant() <= 0 || v.capacity() <= 0) {
            return -1;
        }
        return (int) ((long) v.capacity() * pct / 100L / ((long) v.bandWant() * FeReserveBand.TICKS_PER_MINUTE));
    }

    private static int pctUp(long fe, int capacity) {
        return fe <= 0 || capacity <= 0 ? 0
                : (int) Math.min(100L, (Math.min(fe, capacity) * 100L + capacity - 1) / capacity);
    }

    private enum EdgeWhy {
        SET,

        NO_WANT,

        MINUTES,

        CAPPED,

        RATE
    }

    private static EdgeWhy edgeReason(BatteryView v, int edge, FeReserveBand.Band sized) {
        final FeBandSettings bs = v.band();
        if (bs.unit() == FeBandSettings.Unit.PERCENT) {
            return EdgeWhy.SET;
        }
        final int pct = edge == 0 ? sized.floorPct() : edge == 1 ? sized.ceilingPct()
                : Math.max(sized.ceilingPct(), v.healthyPct());
        final long need = (long) v.bandWant() * bs.edge(edge) * FeReserveBand.TICKS_PER_MINUTE;
        final long raw = edge < 2 ? need : Math.max(need, (long) v.capacity() * sized.floorPct() / 100L
                + (long) v.fullRate() * FeReserveBand.DEFAULT_RIDE_TICKS);
        if (pctUp(raw, v.capacity()) > bs.cap(edge) && pct == bs.cap(edge)) {
            return EdgeWhy.CAPPED;
        }
        if (v.bandWant() <= 0 && edge < 2) {
            return EdgeWhy.NO_WANT;
        }
        if (edge == 2 && pctUp(need, v.capacity()) < pct) {
            return EdgeWhy.RATE;
        }
        return EdgeWhy.MINUTES;
    }

    static String edgeWhy(BatteryView v, int edge, FeReserveBand.Band sized) {
        final int pct = edge == 0 ? sized.floorPct() : edge == 1 ? sized.ceilingPct()
                : Math.max(sized.ceilingPct(), v.healthyPct());
        final int held = minutesAt(v, pct);
        return switch (edgeReason(v, edge, sized)) {
            case SET -> held < 0 ? "set" : "set, " + bandSpan(held);
            case NO_WANT -> "no Critical";
            case CAPPED -> held < 0 ? "capped" : "capped, " + bandSpan(held);
            case RATE -> "1 min at full rate";
            case MINUTES -> v.band().edge(edge) + " min";
        };
    }

    static String bandSpan(int minutes) {
        return minutes < 1 ? "under 1 min" : minutes >= 120 ? (minutes / 60) + " h" : minutes + " min";
    }

    private static String criticalSources(BatteryView v) {
        final List<String> from = new java.util.ArrayList<>();
        for (BatteryView.Output o : v.outputs()) {
            if (o.wired() && (o.critical() > 0 || o.tier() == FePriority.CRITICAL && o.asked() > 0)) {
                from.add(String.valueOf(o.index() + 1));
            }
        }
        return from.isEmpty() ? "" : from.size() == 1 ? " (output " + from.get(0) + ")"
                : " (outputs " + String.join(", ", from) + ")";
    }

    private static void bandPanel(BatteryCanvas c, PduRect r, BatteryView v, State s, int hot) {
        card(c, r, false, false);
        final FeBandSettings bs = v.band();
        final boolean minutes = bs.unit() == FeBandSettings.Unit.MINUTES;
        int x = r.x() + 6;
        final int y = r.y() + 5;
        c.bold("BAND", x, y + 1, TEXT);
        x += c.boldWidth("BAND") + 8;
        if (r.w() >= 600) {
            c.text("Measure", x, y + 1, FAINT);
            x += c.width("Measure") + 4;
        }
        x = segmentButton(c, x, y - 1, "Minutes of Critical", minutes, s,
                com.createstats.network.BatteryControllerSetPacket.ACTION_BAND_UNIT, 0,
                FeBandSettings.Unit.MINUTES.ordinal());
        x = segmentButton(c, x, y - 1, "Percent", !minutes, s,
                com.createstats.network.BatteryControllerSetPacket.ACTION_BAND_UNIT, 0,
                FeBandSettings.Unit.PERCENT.ordinal());

        final String def = "Defaults";
        final int dw = c.width(def) + 8;
        final PduRect defR = new PduRect(r.right() - 6 - dw, y - 1, dw, 12);
        final boolean defHover = v.bandCustom() && defR.contains(s.mouseX(), s.mouseY());
        c.fill(defR.x(), defR.y(), defR.right(), defR.bottom(), v.bandCustom() ? alpha(ACCENT, defHover ? 0x55 : 0x30)
                : BG);
        c.outline(defR.x(), defR.y(), defR.w(), defR.h(), v.bandCustom() ? ACCENT : STROKE);
        c.text(def, defR.x() + 4, y + 1, v.bandCustom() ? TEXT : FAINT);
        if (v.bandCustom()) {
            s.hits().add(new Hit(defR, com.createstats.network.BatteryControllerSetPacket.ACTION_BAND_DEFAULTS, 0, 0));
        }
        final int nowMin = v.bandWant() <= 0 ? -1
                : (int) (v.stored() / ((long) v.bandWant() * FeReserveBand.TICKS_PER_MINUTE));
        final String src = v.bandWant() > 0 ? "Critical peaks at " + fe(v.bandWant()) + " FE/t" + criticalSources(v)
                + "; " + bandSpan(nowMin) + " of it in the banks"
                : minutes ? "No output is Critical: set one to CRIT, or measure by percent"
                : "No output is Critical";
        c.text(c.fit(src, defR.x() - 8 - (x + 8)), x + 8, y + 1, v.bandWant() > 0 || !minutes ? DIM : WARN);
        s.region("bandhead", new PduRect(r.x() + 6, y - 1, defR.right() - r.x() - 6, 12));

        final FeReserveBand.Band sized = bs.band(v.capacity(), v.bandWant(), v.fullRate(),
                FeReserveBand.DEFAULT_RIDE_TICKS);
        final String[] names = {"Floor", "Top", "Healthy"};
        final int sx = r.x() + 6 + 38;
        final int sw = Math.round(r.w() * (minutes ? 0.22f : 0.27f));
        final int resultX = sx + sw + 5 + 38;
        final String[] results = new String[3];
        int resultW = 0;
        for (int i = 0; i < 3; i++) {
            final int pct = i == 0 ? sized.floorPct() : i == 1 ? sized.ceilingPct()
                    : Math.max(sized.ceilingPct(), v.healthyPct());
            final int heldMin = minutesAt(v, pct);
            results[i] = pct <= 0 || v.capacity() <= 0 ? "= nothing" : minutes
                    ? "= " + fe((long) v.capacity() * pct / 100L) + ", " + pct + "%"
                    : "= " + fe((long) v.capacity() * pct / 100L) + (heldMin >= 0 ? ", " + bandSpan(heldMin) : "");
            resultW = Math.max(resultW, c.width(results[i]) + 8);
        }

        final int capX = resultX + resultW;
        final int capW = 40;
        final int noteX = minutes ? capX + c.width("max") + 4 + capW + 5 + c.width("100%") + 6 : capX;
        final int max = minutes ? BAND_SLIDER_MINUTES : 100;
        final int charge = minutes ? nowMin : v.socPct();
        for (int i = 0; i < 3; i++) {
            final int ry = r.y() + BAND_ROW_TOP + i * BAND_ROW_H;
            final boolean lit = hot == i;
            if (lit) {
                c.fill(r.x() + 2, ry - 1, r.right() - 2, ry + BAND_ROW_H - 1, RAISED);
            }
            c.text(names[i], r.x() + 6, ry + 1, lit ? TEXT : FAINT);
            final int value = bs.edge(i);
            slider(c, new PduRect(sx, ry, sw, 10), Math.min(value, max), max, minutes ? value + " min" : value + "%",
                    charge > max ? -1 : charge, s, A_BAND_TRACK, i);
            c.text(results[i], resultX, ry + 1, DIM);
            if (minutes) {
                c.text("max", capX, ry + 1, FAINT);
                slider(c, new PduRect(capX + c.width("max") + 4, ry, capW, 10), bs.cap(i), 100, bs.cap(i) + "%", -1,
                        s, A_CAP_TRACK, i);
            }
            final String[] note = bandNote(v, i, sized);
            c.text(c.fit(note[0], r.right() - 6 - noteX), noteX, ry + 1,
                    note[1].equals("w") ? WARN : note[1].equals("i") ? INFO : FAINT);
        }
    }

    private static String[] bandNote(BatteryView v, int edge, FeReserveBand.Band sized) {
        final FeBandSettings bs = v.band();
        if (edge == 0 && v.holdPct() > sized.floorPct()) {
            return new String[]{"Raised to " + v.holdPct() + "% by a board's night hold.", "i"};
        }
        if (edge == 1 && v.holdPct() > sized.floorPct() && sized.ceilingPct() <= v.holdPct()) {
            return new String[]{"Inside the hold until the board's supply is back.", "i"};
        }
        final int pct = edge == 0 ? sized.floorPct() : edge == 1 ? sized.ceilingPct()
                : Math.max(sized.ceilingPct(), v.healthyPct());
        final int held = minutesAt(v, pct);
        switch (edgeReason(v, edge, sized)) {
            case NO_WANT -> {
                return new String[]{"No Critical declared: nothing to keep.", "w"};
            }
            case CAPPED -> {
                return new String[]{"Capped at " + bs.cap(edge) + "%" + (held < 0 ? "." : ": holds " + bandSpan(held)
                        + "."), "w"};
            }
            case RATE -> {
                return new String[]{"Raised to last a minute at the full " + fe(v.fullRate()) + " FE/t.", "n"};
            }
            default -> {
            }
        }
        return new String[]{switch (edge) {
            case 0 -> "Under it only Critical draws.";
            case 1 -> "Under it nothing shed comes back.";
            default -> "Above it the banks are spent freely.";
        }, "n"};
    }

    private static void inspector(BatteryCanvas c, PduRect r, BatteryView v, State s) {
        card(c, r, true, false);
        if (s.selected() < 0 || s.selected() >= v.outputs().size() || !v.outputs().get(s.selected()).wired()) {
            return;
        }
        final BatteryView.Output o = v.outputs().get(s.selected());
        int x = r.x() + 6;
        final int y = r.y() + 5;
        c.bold("OUTPUT " + (o.index() + 1), x, y + 1, TEXT);
        x += c.boldWidth("OUTPUT " + (o.index() + 1)) + 8;
        final PduRect sw = new PduRect(x, y, 22, 10);
        toggle(c, sw, o.enabled(), s);
        s.hits().add(new Hit(sw, com.createstats.network.BatteryControllerSetPacket.ACTION_OUTPUT_ENABLED,
                o.index(), o.enabled() ? 0 : 1));
        x += 26;
        c.text(o.enabled() ? "Breaker on" : "Breaker off", x, y + 1, o.enabled() ? DIM : CRIT);
        x += c.width("Breaker off") + 10;

        c.text("Mode", x, y + 1, FAINT);
        x += c.width("Mode") + 4;
        for (BatteryOutputMode m : BatteryOutputMode.values()) {
            final String label = m == BatteryOutputMode.SURPLUS ? "Surplus" : m.label();
            x = segmentButton(c, x, y - 1, label, o.mode() == m, s,
                    com.createstats.network.BatteryControllerSetPacket.ACTION_OUTPUT_MODE, o.index(), m.ordinal());
        }

        int tierW = c.width("Tier") + 4;
        for (FePriority t : FePriority.values()) {
            tierW += c.width(t.getShortName()) + 7;
        }
        int row2x = r.x() + 6;
        final int y2 = r.y() + 20;
        if (x + 8 + tierW <= r.right() - 6) {
            x += 8;
            tierButtons(c, x, y, o, s);
        } else {
            row2x = tierButtons(c, row2x, y2, o, s) + 10;
        }

        int sx = row2x;
        final int valueW = 34;
        c.text("Floor", sx, y2 + 1, FAINT);
        sx += c.width("Floor") + 6;
        final boolean limit = o.mode() == BatteryOutputMode.LIMIT;
        final boolean limitHere = limit && r.right() - 6 - sx >= 2 * (60 + valueW + 6) + c.width("Limit") + 6;
        final int floorW = limitHere ? (r.right() - 6 - sx - c.width("Limit") - 6) / 2 - valueW - 6
                : r.right() - 6 - sx - valueW;
        slider(c, new PduRect(sx, y2, Math.max(30, floorW), 10), o.floorPct(), 95,
                o.floorPct() > 0 ? o.floorPct() + "%" : "off", v.socPct(), s, A_FLOOR_TRACK, o.index());
        if (limit) {
            final int ly = limitHere ? y2 : r.y() + 35;
            int lx = limitHere ? sx + Math.max(30, floorW) + valueW + 6 : r.x() + 6;
            c.text("Limit", lx, ly + 1, FAINT);
            lx += c.width("Limit") + 6;
            slider(c, new PduRect(lx, ly, Math.max(30, r.right() - 6 - lx - valueW), 10), o.limit(),
                    Math.max(1, s.portRate()), fe(o.limit()), -1, s, A_LIMIT_TRACK, o.index());
            if (limitHere) {
                c.text(c.fit(BatteryText.modeBlurb(o.mode()), r.w() - 12), r.x() + 6, r.y() + 37, DIM);
            }
        } else {
            c.text(c.fit(BatteryText.modeBlurb(o.mode()), r.w() - 12), r.x() + 6, r.y() + 37, DIM);
        }
    }

    private static int tierButtons(BatteryCanvas c, int x, int y, BatteryView.Output o, State s) {
        c.text("Tier", x, y + 1, FAINT);
        x += c.width("Tier") + 4;
        for (FePriority t : FePriority.values()) {
            x = segmentButton(c, x, y - 1, t.getShortName(), o.tier() == t, s,
                    com.createstats.network.BatteryControllerSetPacket.ACTION_OUTPUT_PRIORITY, o.index(), t.ordinal());
        }
        return x;
    }

    private static int segmentButton(BatteryCanvas c, int x, int y, String label, boolean on, State s, int action,
                                     int index, int value) {
        final int w = c.width(label) + 8;
        final PduRect r = new PduRect(x, y, w, 12);
        final boolean hover = r.contains(s.mouseX(), s.mouseY());
        c.fill(r.x(), r.y(), r.right(), r.bottom(), on ? alpha(ACCENT, 0x44) : hover ? RAISED : BG);
        c.outline(r.x(), r.y(), r.w(), r.h(), on ? ACCENT : hover ? HOT : STROKE);
        c.text(label, x + 4, y + 2, on ? TEXT : DIM);
        s.hits().add(new Hit(r, action, index, value));
        return x + w - 1;
    }

    private static void slider(BatteryCanvas c, PduRect r, int value, int max, String text, int marker, State s,
                               int action, int index) {
        final boolean hover = r.contains(s.mouseX(), s.mouseY());
        c.fill(r.x(), r.y() + 4, r.right(), r.y() + 6, hover ? HOT : STROKE);
        final int kx = r.x() + Math.round((r.w() - 1) * Math.min(1.0f, Math.max(0, value) / (float) max));
        c.fill(r.x(), r.y() + 4, kx, r.y() + 6, ACCENT);
        if (marker >= 0 && marker <= max) {
            final int mx = r.x() + Math.round((r.w() - 1) * marker / (float) max);
            c.fill(mx, r.y() + 1, mx + 1, r.y() + 9, INFO);
        }
        c.fill(kx - 2, r.y(), kx + 3, r.y() + 10, hover ? TEXT : DIM);
        c.text(text, r.right() + 5, r.y() + 1, TEXT);
        s.hits().add(new Hit(new PduRect(r.x() - 3, r.y() - 2, r.w() + 6, r.h() + 4), action, index, max));
    }

    private static void forecastPage(BatteryCanvas c, PduRect b, BatteryView v, State s) {
        final int chartH = Math.max(60, Math.round(b.h() * 0.56f));
        final PduRect chart = new PduRect(b.x(), b.y(), b.w(), chartH);
        chart(c, chart, v, s);
        s.region("chart", chart);
        final int listTop = chart.bottom() + 8;
        final PduRect list = new PduRect(b.x(), listTop, Math.round(b.w() * 0.58f), b.bottom() - listTop);
        final PduRect assume = new PduRect(list.right() + 6, listTop, b.right() - list.right() - 6, list.h());
        milestoneList(c, list, v);
        assumptions(c, assume, v);
        s.region("next", list);
        s.region("assumes", assume);
    }

    private static final float PAST_SHARE = 0.26f;

    private static void chart(BatteryCanvas c, PduRect r, BatteryView v, State s) {
        card(c, r, false, false);
        final PduRect area = new PduRect(r.x() + 26, r.y() + 20, r.w() - 32, r.h() - 32);

        final int stripH = area.h() >= 60 ? Math.max(14, Math.round(area.h() * 0.28f)) : 0;
        final PduRect plot = new PduRect(area.x(), area.y(), area.w(), area.h() - (stripH > 0 ? stripH + 7 : 0));
        final PduRect strip = stripH > 0 ? new PduRect(area.x(), plot.bottom() + 7, area.w(), stripH) : null;
        s.region("plot", plot);
        if (strip != null) {
            s.region("strip", strip);
        }
        c.fill(plot.x(), plot.y(), plot.right(), plot.bottom(), BG);
        if (v.capacity() > 0) {
            hZone(c, plot, 0, v.floorPct(), alpha(CRIT, 0x26));
            hZone(c, plot, v.floorPct(), v.ceilingPct(), alpha(WARN, 0x1C));
            hZone(c, plot, v.healthyPct(), 100, alpha(OK, 0x12));
        }
        for (int pct = 0; pct <= 100; pct += 25) {
            final int gy = pctY(plot, pct);
            c.fill(plot.x(), gy, plot.right(), gy + 1, alpha(STROKE, 0x99));
            c.textRight(pct + "%", plot.x() - 3, gy - 4, FAINT);
        }
        final int nowX = plot.x() + Math.round(plot.w() * PAST_SHARE);
        final PduRect past = new PduRect(plot.x(), plot.y(), nowX - plot.x(), plot.h());
        final PduRect future = new PduRect(nowX, plot.y(), plot.right() - nowX, plot.h());

        final BatteryHistory h = s.history();
        if (h != null && h.count() > 1) {
            int px = -1;
            int py = -1;
            for (int i = 0; i < h.count(); i++) {
                final int x = past.x() + Math.round((past.w() - 1) * (i / (float) (h.count() - 1)));
                final int y = pctY(plot, h.socAt(i));

                c.fill(px < 0 ? x : px + 1, y, x + 1, plot.bottom(), alpha(INFO, 0x22));
                if (px >= 0) {
                    line(c, px, py, x, y, INFO);
                }
                px = x;
                py = y;
            }
        } else {
            c.textCentred("gathering", past.centerX(), past.centerY() - 4, FAINT);
        }

        final byte[] trace = v.outlook().trace();
        if (trace.length > 1) {
            int px = -1;
            int py = -1;
            for (int i = 0; i < trace.length; i++) {
                final int x = future.x() + Math.round((future.w() - 1) * (i / (float) (trace.length - 1)));
                final int y = pctY(plot, trace[i]);
                if (px >= 0) {
                    dotted(c, px, py, x, y, ACCENT);
                }
                px = x;
                py = y;
            }

            int n = 1;
            int lastRight = Integer.MIN_VALUE;
            int lastY = Integer.MIN_VALUE;
            for (ReserveProjection.Milestone m : v.outlook().milestones()) {
                if (n > 9) {
                    break;
                }
                final int x = future.x() + Math.round((future.w() - 1)
                        * Math.min(1.0f, m.tick() / (float) ReserveProjection.HORIZON_TICKS));
                final int y = pctY(plot, m.socPct());
                final int col = milestoneColour(m);
                if (n == 1) {
                    s.region("marker", new PduRect(x - 2, y - 2, 5, 5));
                }
                c.fill(x - 2, y - 2, x + 3, y + 3, col);
                c.fill(x - 1, y - 1, x + 2, y + 2, BG);
                final String label = String.valueOf(n);
                int lx = x + 3;
                int ly = Math.max(plot.y() + 1, y - 10);
                if (lx < lastRight + 2 && Math.abs(ly - lastY) < 9) {
                    lx = lastRight + 2;
                    ly = lastY;
                }
                c.text(label, lx, ly, col);
                lastRight = lx + c.width(label);
                lastY = ly;
                n++;
            }
        } else if (v.capacity() > 0) {
            c.textCentred("working it out", future.centerX(), future.centerY() - 4, FAINT);
        }
        if (strip != null) {
            supplyStrip(c, strip, nowX, v, h);
        }
        c.fill(nowX, plot.y() - 2, nowX + 1, area.bottom() + 2, DIM);
        c.textCentred("now", nowX, area.bottom() + 3, DIM);
        s.region("now", new PduRect(nowX - 1, plot.y() - 2, 3, area.bottom() + 12 - plot.y()));

        for (int m = 5; m * 1_200 <= ReserveProjection.HORIZON_TICKS; m += 5) {
            final int ticks = m * 1_200;
            final int x = future.x() + Math.round((future.w() - 1) * (ticks / (float) ReserveProjection.HORIZON_TICKS));
            c.fill(x, area.bottom(), x + 1, area.bottom() + 2, FAINT);
            if (m % 10 == 0) {
                final String t = "+" + m + "m";
                c.text(t, Math.min(area.right() - c.width(t), x - c.width(t) / 2), area.bottom() + 3, FAINT);
            }
        }
        final int cadence = BatteryHistory.CADENCE[BatteryHistory.clampRange(s.range())];
        final String span = h == null || h.count() < 2 ? ""
                : "-" + ReserveWords.span(Math.max(0, h.count() - 1) * cadence);
        c.text(span, past.x(), area.bottom() + 3, FAINT);
        c.text("CHARGE, PAST AND FORESEEN", r.x() + 6, r.y() + 4, FAINT);
        rangeChips(c, r, s);

        if (area.contains(s.mouseX(), s.mouseY())) {
            c.fill(s.mouseX(), plot.y(), s.mouseX() + 1, area.bottom(), alpha(TEXT, 0x88));
            String read = "";
            if (s.mouseX() >= nowX && trace.length > 1) {
                final float f = (s.mouseX() - future.x()) / (float) Math.max(1, future.w() - 1);
                final int i = Math.max(0, Math.min(trace.length - 1, Math.round(f * (trace.length - 1))));
                final int[] supply = v.outlook().supply();
                read = "in " + ReserveWords.span(i * ReserveProjection.SAMPLE_TICKS) + ": " + trace[i] + "%"
                        + (i < supply.length ? ", " + fe(supply[i]) + " FE/t in" : "");
            } else if (h != null && h.count() > 0) {
                final float f = (s.mouseX() - past.x()) / (float) Math.max(1, past.w() - 1);
                final int i = Math.max(0, Math.min(h.count() - 1, Math.round(f * (h.count() - 1))));
                read = ReserveWords.span((h.count() - 1 - i) * cadence) + " ago: " + h.socAt(i) + "%, "
                        + fe(h.inAt(i)) + " FE/t in";
            }
            if (!read.isEmpty()) {
                final int w = c.width(read) + 6;
                final int x = Math.min(plot.right() - w, s.mouseX() + 4);
                c.fill(x, plot.y() + 1, x + w, plot.y() + 11, alpha(BG, 0xE8));
                c.outline(x, plot.y() + 1, w, 10, STROKE);
                c.text(read, x + 3, plot.y() + 2, TEXT);
            }
        }
    }

    private static void supplyStrip(BatteryCanvas c, PduRect strip, int nowX, BatteryView v, BatteryHistory h) {
        c.fill(strip.x(), strip.y(), strip.right(), strip.bottom(), BG);
        final int[] supply = v.outlook().supply();
        final int asked = v.asked();
        long scale = Math.max(1, asked);
        for (int f : supply) {
            scale = Math.max(scale, f);
        }
        final boolean past = h != null && h.count() > 1;
        if (past) {
            for (int i = 0; i < h.count(); i++) {
                scale = Math.max(scale, Math.max(h.inAt(i), h.outAt(i)));
            }
        }
        final long top = scale;
        final java.util.function.IntUnaryOperator yOf = fe -> strip.bottom() - 1
                - (int) Math.round((strip.h() - 2) * Math.min(1.0, Math.max(0, fe) / (double) top));
        if (past) {
            final int w = nowX - strip.x();
            int px = -1;
            int pyIn = -1;
            int pyOut = -1;
            for (int i = 0; i < h.count(); i++) {
                final int x = strip.x() + Math.round((w - 1) * (i / (float) (h.count() - 1)));
                final int yIn = yOf.applyAsInt(h.inAt(i));
                final int yOut = yOf.applyAsInt(h.outAt(i));
                c.fill(px < 0 ? x : px + 1, yIn, x + 1, strip.bottom(), alpha(OK, 0x2A));
                if (px >= 0) {
                    line(c, px, pyIn, x, yIn, alpha(OK, 0xAA));
                    line(c, px, pyOut, x, yOut, alpha(WARN, 0x99));
                }
                px = x;
                pyIn = yIn;
                pyOut = yOut;
            }
        }
        final int fw = strip.right() - nowX;
        if (supply.length > 1) {
            int px = -1;
            int py = -1;
            for (int i = 0; i < supply.length; i++) {
                final int x = nowX + Math.round((fw - 1) * (i / (float) (supply.length - 1)));
                final int y = yOf.applyAsInt(supply[i]);
                c.fill(px < 0 ? x : px + 1, y, x + 1, strip.bottom(), alpha(OK, 0x22));
                if (px >= 0) {
                    dotted(c, px, py, x, y, OK);
                }
                px = x;
                py = y;
            }
            final int ya = yOf.applyAsInt(asked);
            dotted(c, nowX, ya, strip.right() - 1, ya, WARN);

            for (ReserveProjection.Milestone m : v.outlook().milestones()) {
                if (m.kind() != ReserveProjection.Kind.SUN_UP && m.kind() != ReserveProjection.Kind.SUN_DOWN
                        && m.kind() != ReserveProjection.Kind.FUEL_OUT) {
                    continue;
                }
                final int x = nowX + Math.round((fw - 1)
                        * Math.min(1.0f, m.tick() / (float) ReserveProjection.HORIZON_TICKS));
                c.fill(x, strip.y(), x + 1, strip.bottom(), alpha(milestoneColour(m), 0x99));
            }
        }
        c.outline(strip.x(), strip.y(), strip.w(), strip.h(), alpha(STROKE, 0x99));

        c.textRight(fe(top), strip.x() - 3, strip.y(), FAINT);
        if (strip.h() >= 16) {
            c.textRight("FE/t", strip.x() - 3, strip.bottom() - 8, FAINT);
        }
    }

    private static void rangeChips(BatteryCanvas c, PduRect r, State s) {
        final String[] labels = {"1m", "5m", "15m"};
        int x = r.right() - 6;
        for (int i = labels.length - 1; i >= 0; i--) {
            final int w = c.width(labels[i]) + 8;
            x -= w + 2;
            final PduRect chip = new PduRect(x, r.y() + 2, w, 11);
            final boolean on = i == s.range();
            final boolean hover = chip.contains(s.mouseX(), s.mouseY());
            c.fill(chip.x(), chip.y(), chip.right(), chip.bottom(), on ? alpha(ACCENT, 0x44) : hover ? RAISED : BG);
            c.outline(chip.x(), chip.y(), chip.w(), chip.h(), on ? ACCENT : hover ? HOT : STROKE);
            c.text(labels[i], chip.x() + 4, chip.y() + 2, on ? TEXT : DIM);
            s.hits().add(new Hit(chip, A_RANGE, i, i));
        }
        c.textRight("past:", x - 3, r.y() + 4, FAINT);
    }

    private static void milestoneList(BatteryCanvas c, PduRect r, BatteryView v) {
        card(c, r, false, false);
        c.text("WHAT HAPPENS NEXT", r.x() + 6, r.y() + 4, FAINT);
        int y = r.y() + 16;
        final List<ReserveProjection.Milestone> ms = v.outlook().milestones();
        if (v.capacity() <= 0) {
            c.text(c.fit("Nothing to forecast without banks.", r.w() - 12), r.x() + 6, y, DIM);
            return;
        }
        if (v.outlook().shortNow()) {
            c.text(c.fit("Now: an output is already short (see Now).", r.w() - 12), r.x() + 6, y, WARN);
            y += 11;
        }
        if (ms.isEmpty()) {
            c.text(c.fit("Nothing changes in the next " + ReserveWords.horizon() + ".", r.w() - 12),
                    r.x() + 6, y, OK);
            return;
        }
        int n = 1;
        for (ReserveProjection.Milestone m : ms) {
            if (y + 9 > r.bottom() - 3 || n > 9) {
                break;
            }
            final String when = "in " + ReserveWords.span(m.tick());
            final int col = milestoneColour(m);
            c.text(String.valueOf(n), r.x() + 6, y, col);
            c.text(when, r.x() + 16, y, DIM);
            final int tx = r.x() + 16 + 58;
            c.text(c.fit(ReserveWords.milestone(m), r.right() - tx - 4), tx, y, col);
            y += 11;
            n++;
        }
    }

    private static void assumptions(BatteryCanvas c, PduRect r, BatteryView v) {
        card(c, r, false, false);
        c.text("ASSUMES", r.x() + 6, r.y() + 4, FAINT);
        final int max = r.w() - 12;
        int y = r.y() + 16;
        y = para(c, BatteryText.supplyAssumption(v), r.x() + 6, y, max, DIM, r.bottom());
        y = para(c, "The outputs keep asking " + fe(v.asked()) + " FE/t; a panel sheds as it is metered, so "
                + "past the first shortfall this is the worst case.", r.x() + 6, y, max, DIM, r.bottom());
        if (v.holdPct() > v.configuredFloorPct()) {
            y = para(c, "Weather: a board upstream expects to run " + fe(v.holdFe()) + " FE short before its "
                    + "supply is back, so the floor is raised to " + v.holdPct() + "% (it is "
                    + v.configuredFloorPct() + "% without the hold).", r.x() + 6, y, max, INFO, r.bottom());
        } else {
            y = para(c, "No weather hold: no board upstream has asked for charge to be kept back.", r.x() + 6, y,
                    max, FAINT, r.bottom());
        }
        if (v.storeCount() > 0) {
            para(c, "Starter Capacitors refill first out of anything spare.", r.x() + 6, y, max, STORE, r.bottom());
        }
    }

    private static void rulesPage(BatteryCanvas c, PduRect b, BatteryView v, State s) {
        int y = b.y();
        final int gap = 4;

        final int h1 = 52;
        final PduRect s1 = new PduRect(b.x(), y, b.w(), h1);
        step(c, s1, 1, "THE BAND", "How hard the banks may be spent, from how full they are.");
        s.region("step1", s1);
        if (v.capacity() > 0) {
            final int barW = Math.min(160, s1.w() / 4);
            bandBar(c, new PduRect(s1.x() + 22, s1.y() + 18, barW, 10), v);
            s.region("bandbar", new PduRect(s1.x() + 22, s1.y() + 16, barW, 22));
            final int tx = s1.x() + 22 + barW + 12;
            c.text(c.fit(BatteryText.bandRule(v), s1.right() - tx - 6), tx, s1.y() + 16, stanceColour(v.stance()));
            c.text(c.fit(BatteryText.bandAllows(v), s1.right() - tx - 6), tx, s1.y() + 27, DIM);
            c.text(c.fit(BatteryText.bandWhy(v), s1.right() - tx - 6), tx, s1.y() + 38, FAINT);
        } else {
            c.text("No banks: nothing to steer.", s1.x() + 22, s1.y() + 16, WARN);
        }
        y += h1 + gap;

        final PduRect s2 = new PduRect(b.x(), y, b.w(), 30);
        step(c, s2, 2, "REFILL", "Generation held back for the banks before any output is served.");
        s.region("step2", s2);
        c.text(c.fit(BatteryText.claimRule(v), s2.w() - 30), s2.x() + 22, s2.y() + 16,
                v.chargeClaim() > 0 ? INFO : DIM);
        y += 30 + gap;

        final int h3 = 16 + 11 * Math.max(1, v.outputs().size()) + 3;
        final PduRect s3 = new PduRect(b.x(), y, b.w(), h3);
        step(c, s3, 3, "OUTPUTS", "Breaker, then floor, then mode, then tier: highest tier served first.");
        s.region("step3", s3);
        int oy = s3.y() + 16;
        for (BatteryView.Output o : v.outputs()) {
            final String line = BatteryText.outputRule(o, v);
            final int lw = c.width(line);
            final int maxW = s3.w() - 30;
            c.text(c.fit(line, maxW), s3.x() + 22, oy, o.wired() ? TEXT : FAINT);
            if (o.wired() && lw + 10 < maxW) {
                c.text(c.fit(BatteryText.outputWhy(o, v), maxW - lw - 10), s3.x() + 22 + lw + 10, oy,
                        reasonColour(o) == OK ? DIM : reasonColour(o));
            }
            oy += 11;
        }
        y += h3 + gap;

        final PduRect s4 = new PduRect(b.x(), y, b.w(), 30);
        step(c, s4, 4, "STARTER CAPACITORS", "Reserve too: refilled before the banks, never from a machine's share.");
        s.region("step4", s4);
        c.text(c.fit(BatteryText.storeRule(v), s4.w() - 30), s4.x() + 22, s4.y() + 16,
                v.storeCharged() > 0 ? STORE : DIM);
        y += 30 + gap;

        if (y + 30 <= b.bottom()) {
            final PduRect s5 = new PduRect(b.x(), y, b.w(), 30);
            step(c, s5, 5, "BANKS", "Whatever is left over.");
            s.region("step5", s5);
            c.text(c.fit(BatteryText.bankRule(v), s5.w() - 30), s5.x() + 22, s5.y() + 16,
                    v.bankCharged() > 0 ? OK : DIM);
        }
    }

    private static void step(BatteryCanvas c, PduRect r, int n, String title, String sub) {
        card(c, r, false, false);
        c.fill(r.x() + 5, r.y() + 4, r.x() + 15, r.y() + 14, alpha(ACCENT, 0x44));
        c.outline(r.x() + 5, r.y() + 4, 10, 10, ACCENT);
        c.textCentred(String.valueOf(n), r.x() + 10, r.y() + 5, TEXT);
        c.bold(title, r.x() + 22, r.y() + 5, TEXT);
        final int tx = r.x() + 22 + c.boldWidth(title) + 8;
        c.text(c.fit(sub, r.right() - tx - 6), tx, r.y() + 5, FAINT);
    }

    private static void bandBar(BatteryCanvas c, PduRect r, BatteryView v) {
        c.fill(r.x(), r.y(), r.right(), r.bottom(), BG);
        vZone(c, r, 0, v.floorPct(), alpha(CRIT, 0x99));
        vZone(c, r, v.floorPct(), v.ceilingPct(), alpha(WARN, 0x88));
        vZone(c, r, v.ceilingPct(), v.healthyPct(), alpha(INFO, 0x66));
        vZone(c, r, v.healthyPct(), 100, alpha(OK, 0x77));
        c.outline(r.x(), r.y(), r.w(), r.h(), STROKE);
        final int mx = r.x() + Math.round((r.w() - 1) * v.socPct() / 100.0f);
        c.fill(mx - 1, r.y() - 2, mx + 2, r.bottom() + 2, TEXT);
        for (int pct : new int[]{v.floorPct(), v.ceilingPct(), v.healthyPct()}) {
            final int x = r.x() + Math.round((r.w() - 1) * pct / 100.0f);
            c.textCentred(String.valueOf(pct), x, r.bottom() + 2, FAINT);
        }
    }

    private static void card(BatteryCanvas c, PduRect r, boolean selected, boolean hover) {
        c.fill(r.x(), r.y(), r.right(), r.bottom(), hover ? RAISED : CARD);
        c.outline(r.x(), r.y(), r.w(), r.h(), selected ? ACCENT : hover ? HOT : STROKE);
    }

    private static void badge(BatteryCanvas c, int x, int y, int h, String label, int colour, boolean left) {
        c.fill(x + 1, y + 1, x + 13, y + h - 1, alpha(colour, 0x30));
        c.fill(left ? x + 1 : x + 11, y + 1, left ? x + 3 : x + 13, y + h - 1, colour);
        c.textCentred(label, x + 7, y + h / 2 - 4, TEXT);
    }

    private static void toggle(BatteryCanvas c, PduRect r, boolean on, State s) {
        final boolean hover = r.contains(s.mouseX(), s.mouseY());
        c.fill(r.x(), r.y(), r.right(), r.bottom(), on ? alpha(OK, 0x44) : BG);
        c.outline(r.x(), r.y(), r.w(), r.h(), hover ? HOT : on ? OK : STROKE);
        final int kw = r.w() / 2 - 2;
        final int kx = on ? r.right() - kw - 2 : r.x() + 2;
        c.fill(kx, r.y() + 2, kx + kw, r.bottom() - 2, on ? OK : FAINT);
    }

    private static int para(BatteryCanvas c, String text, int x, int y, int max, int colour, int bottom) {
        final String[] words = text.split(" ");
        StringBuilder line = new StringBuilder();
        for (String w : words) {
            final String next = line.length() == 0 ? w : line + " " + w;
            if (c.width(next) > max && line.length() > 0) {
                if (y + 9 > bottom - 2) {
                    return y;
                }
                c.text(line.toString(), x, y, colour);
                y += 10;
                line = new StringBuilder(w);
            } else {
                line = new StringBuilder(next);
            }
        }
        if (line.length() > 0 && y + 9 <= bottom - 2) {
            c.text(c.fit(line.toString(), max), x, y, colour);
            y += 10;
        }
        return y + 2;
    }

    private static void hZone(BatteryCanvas c, PduRect plot, int fromPct, int toPct, int colour) {
        if (toPct <= fromPct) {
            return;
        }
        c.fill(plot.x(), pctY(plot, toPct), plot.right(), pctY(plot, fromPct), colour);
    }

    private static void vZone(BatteryCanvas c, PduRect r, int fromPct, int toPct, int colour) {
        if (toPct <= fromPct) {
            return;
        }
        final int x0 = r.x() + Math.round((r.w() - 1) * fromPct / 100.0f);
        final int x1 = r.x() + Math.round((r.w() - 1) * toPct / 100.0f);
        c.fill(x0, r.y() + 1, x1, r.bottom() - 1, colour);
    }

    private static int pctY(PduRect plot, int pct) {
        return plot.bottom() - 1 - Math.round((plot.h() - 2) * Math.max(0, Math.min(100, pct)) / 100.0f);
    }

    private static void line(BatteryCanvas c, int x0, int y0, int x1, int y1, int colour) {
        int dx = Math.abs(x1 - x0);
        int dy = Math.abs(y1 - y0);
        final int sx = x0 < x1 ? 1 : -1;
        final int sy = y0 < y1 ? 1 : -1;
        int err = dx - dy;
        while (true) {
            c.fill(x0, y0, x0 + 1, y0 + 1, colour);
            if (x0 == x1 && y0 == y1) {
                break;
            }
            final int e2 = 2 * err;
            if (e2 > -dy) {
                err -= dy;
                x0 += sx;
            }
            if (e2 < dx) {
                err += dx;
                y0 += sy;
            }
        }
    }

    private static void dotted(BatteryCanvas c, int x0, int y0, int x1, int y1, int colour) {
        final int steps = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
        for (int i = 0; i <= steps; i++) {
            final int x = steps == 0 ? x0 : x0 + Math.round((x1 - x0) * (i / (float) steps));
            final int y = steps == 0 ? y0 : y0 + Math.round((y1 - y0) * (i / (float) steps));
            if (Math.floorMod(x, 3) != 2) {
                c.fill(x, y, x + 1, y + 1, colour);
            }
        }
    }

    static int stanceColour(FeReserveReport.Stance stance) {
        return switch (stance) {
            case SURPLUS -> OK;
            case NOMINAL -> INFO;
            case CONSERVE -> WARN;
            case CRITICAL -> CRIT;
        };
    }

    static int reasonColour(BatteryView.Output o) {
        if (!o.wired()) {
            return FAINT;
        }
        if (o.panels() > 0 && o.boardGranted() < o.boardAsked() && o.enabled()) {
            return WARN;
        }
        return switch (o.reason()) {
            case SERVED -> OK;
            case RATIONED -> ACCENT;
            case RECHARGE -> INFO;
            case PARTIAL, FLOOR, SHED, NO_SURPLUS -> WARN;
            case OFF -> CRIT;
            case IDLE -> o.storeFlow() > 0 ? STORE : DIM;
            case UNWIRED -> FAINT;
        };
    }

    private static int tierColour(FePriority tier) {
        return switch (tier) {
            case CRITICAL -> CRIT;
            case HIGH -> WARN;
            case NORMAL -> TEXT;
            default -> DIM;
        };
    }

    private static int milestoneColour(ReserveProjection.Milestone m) {
        return switch (m.kind()) {
            case SHORT, FLOOR -> WARN;
            case STANCE -> m.value() == FeReserveReport.Stance.CRITICAL.ordinal() ? CRIT : INFO;
            case FULL -> OK;
            case STORES_FULL -> STORE;
            case SETTLES -> DIM;
            case SUN_UP -> OK;
            case SUN_DOWN, FUEL_OUT -> WARN;
        };
    }

    static boolean isAmber(BatteryControllerPlan.Reason r) {
        return r == BatteryControllerPlan.Reason.FLOOR || r == BatteryControllerPlan.Reason.SHED
                || r == BatteryControllerPlan.Reason.NO_SURPLUS || r == BatteryControllerPlan.Reason.PARTIAL;
    }
}
