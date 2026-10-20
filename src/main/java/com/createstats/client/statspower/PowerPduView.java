package com.createstats.client.statspower;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.createstats.data.TimeSeriesStore;
import com.createstats.gui.pdu.PduRect;
import com.createstats.gui.pdu.PduTheme;
import com.createstats.power.FePriority;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class PowerPduView {
    private PowerPduView() {
    }

    private static final int GAP = 5;

    public static void render(GuiGraphics g, Font font, PduRect area, PowerReadings.Pdu pdu,
                              PowerHover hover, float phase, boolean reduceMotion) {
        if (pdu == null || area.w() < 60 || area.h() < 40) {
            return;
        }
        // claim sections while the diagram still fits. "whatever remains" drew past the bottom at gui scale 4

        int top = area.y();
        int bot = area.bottom();

        final int kpiH = area.h() >= 300 ? 46 : 38;
        if (bot - top - kpiH - GAP >= 64) {
            kpiStrip(g, font, new PduRect(area.x(), top, area.w(), kpiH), pdu, hover);
            top += kpiH + GAP;
        }

        final int balanceH = 26;
        if (bot - top - balanceH - GAP >= 58) {
            balance(g, font, new PduRect(area.x(), top, area.w(), balanceH), pdu, hover, phase);
            top += balanceH + GAP;
        }

        if (pdu.devices() > 0 && bot - top - 48 - GAP >= 96) {
            bot -= 48;
            tiers(g, font, new PduRect(area.x(), bot, area.w(), 48), pdu, hover);
            bot -= GAP;
        }
        if (bot - top - 13 >= 70) {
            bot -= 13;
            diagnosis(g, font, area.x(), bot + 2, area.w(), pdu);
        }

        final PduRect main = new PduRect(area.x(), top, area.w(), Math.max(0, bot - top));
        if (main.h() < 30) {
            return;
        }
        if (area.w() >= 430) {
            final int riserW = Mth.clamp(Math.round(area.w() * 0.34f), 150, 250);
            riser(g, font, new PduRect(main.x(), main.y(), riserW, main.h()), pdu, hover, phase,
                    reduceMotion);
            final int rx = main.x() + riserW + GAP;
            final int rw = main.right() - rx;
            final int profileH = Mth.clamp(Math.round(main.h() * 0.52f), 60, main.h());
            final int scheduleH = main.h() - profileH - GAP;
            profile(g, font, new PduRect(rx, main.y(), rw, scheduleH >= 34 ? profileH : main.h()),
                    pdu, hover, phase);
            if (scheduleH >= 34) {
                schedule(g, font, new PduRect(rx, main.y() + profileH + GAP, rw, scheduleH),
                        pdu, hover, phase);
            }
        } else {
            final int riserH = Math.min(main.h(), Math.max(96, Math.round(main.h() * 0.58f)));
            final int profileH = main.h() - riserH - GAP;
            if (profileH >= 56) {
                profile(g, font, new PduRect(main.x(), main.y(), main.w(), profileH), pdu, hover,
                        phase);
                riser(g, font, new PduRect(main.x(), main.y() + profileH + GAP, main.w(), riserH),
                        pdu, hover, phase, reduceMotion);
            } else {
                riser(g, font, main, pdu, hover, phase, reduceMotion);
            }
        }
    }

    private static void kpiStrip(GuiGraphics g, Font font, PduRect r, PowerReadings.Pdu pdu,
                                 PowerHover hover) {
        final List<String[]> tiles = new ArrayList<>();
        final List<Integer> tints = new ArrayList<>();
        final List<float[]> sparks = new ArrayList<>();
        final List<String[]> tips = new ArrayList<>();

        final int wide = r.w() >= 560 ? 6 : (r.w() >= 400 ? 5 : (r.w() >= 280 ? 4 : 3));

        add(tiles, tints, sparks, tips, "SUPPLY", PowerFmt.compact(pdu.generation()), "FE/t",
                PduTheme.INFO, PowerArt.tail(pdu.history("pdu_generation"), 40),
                new String[]{"Upstream generation this panel can see",
                        "Measured from the grid solve, not inferred from a buffer",
                        pdu.upstreamSources() + " upstream source"
                                + (pdu.upstreamSources() == 1 ? "" : "s")});
        add(tiles, tints, sparks, tips, "DEMAND", PowerFmt.compact(pdu.demand()), "FE/t",
                PduTheme.WARN, PowerArt.tail(pdu.history("pdu_demand"), 40),
                new String[]{"What the managed branch costs to run in full",
                        pdu.devices() + " managed device" + (pdu.devices() == 1 ? "" : "s")});
        add(tiles, tints, sparks, tips, "SERVED", PowerFmt.compact(pdu.served()), "FE/t",
                pdu.shedding() ? PduTheme.WARN : PduTheme.OK,
                PowerArt.tail(pdu.history("pdu_served_demand"), 40),
                new String[]{"What is actually reaching the branch right now",
                        pdu.servedDevices() + " of " + pdu.devices() + " devices running"});
        add(tiles, tints, sparks, tips, "SHED", String.valueOf(pdu.shedDevices()),
                pdu.shedDevices() == 1 ? "device" : "devices",
                pdu.shedDevices() > 0 ? PduTheme.CRIT : PduTheme.TEXT_FAINT, null,
                new String[]{"Devices the panel switched off to stay inside its supply",
                        pdu.shedDevices() > 0
                                ? ("Short by " + PowerFmt.rate(pdu.shortfall()))
                                : "Nothing is being shed"});
        if (wide >= 5) {
            add(tiles, tints, sparks, tips, "BUFFER", PowerFmt.pct0(pdu.fillPct()), "",
                    PowerArt.chargeTint(pdu.fillPct()),
                    PowerArt.tail(pdu.history("energy_fill_pct"), 40),
                    new String[]{"The panel's own working buffer",
                            PowerFmt.fe(pdu.stored()) + " of " + PowerFmt.fe(pdu.capacity()) + " FE",
                            "A panel holds only enough to ride out a tick, not a reserve"});
        }
        if (wide >= 6) {
            final boolean draining = pdu.net() < -0.5f;
            add(tiles, tints, sparks, tips, "ENDURANCE",
                    PowerFmt.endurance(pdu.enduranceTicks(), draining), "",
                    draining ? PduTheme.WARN : PduTheme.OK, null,
                    new String[]{"Runtime left in the buffer at the present net drain",
                            draining ? ("Net " + PowerFmt.rate(pdu.net()))
                                    : "Net positive — the buffer is holding or filling"});
        }

        final int n = Math.min(wide, tiles.size());
        final int tileW = (r.w() - GAP * (n - 1)) / n;
        for (int i = 0; i < n; i++) {
            final int tx = r.x() + i * (tileW + GAP);
            final PduRect tile = new PduRect(tx, r.y(), i == n - 1 ? r.right() - tx : tileW, r.h());
            final String[] t = tiles.get(i);
            PowerArt.kpi(g, font, tile, t[0], t[1], t[2], tints.get(i), sparks.get(i),
                    trend(sparks.get(i)));
            hover.tip(tile, tips.get(i));
        }
    }

    private static void add(List<String[]> tiles, List<Integer> tints, List<float[]> sparks,
                            List<String[]> tips, String label, String value, String unit, int tint,
                            float[] spark, String[] tip) {
        tiles.add(new String[]{label, value, unit});
        tints.add(tint);
        sparks.add(spark);
        tips.add(tip);
    }

    private static float trend(float[] data) {
        if (data == null || data.length < 4) {
            return Float.NaN;
        }
        return data[data.length - 1] - data[0];
    }

    private static void balance(GuiGraphics g, Font font, PduRect r, PowerReadings.Pdu pdu,
                                PowerHover hover, float phase) {
        final PduRect bar = new PduRect(r.x(), r.y(), r.w(), 13);
        PowerArt.balanceBar(g, font, bar, pdu.served(), pdu.shortfall(), pdu.generation(),
                pdu.budget(), pdu.ceiling(), phase);
        hover.tip(bar,
                "Supply balance",
                "Served " + PowerFmt.rate(pdu.served()),
                "Refused " + PowerFmt.rate(pdu.shortfall()),
                "Generation " + PowerFmt.rate(pdu.generation()),
                "Budget " + PowerFmt.rate(pdu.budget()) + " — the panel's own spending limit",
                "Wiring ceiling " + PowerFmt.rate(pdu.ceiling()) + " — what the conductors can carry",
                pdu.throughputLimited()
                        ? "This panel is wiring limited: more generation will not help"
                        : ("Headroom " + PowerFmt.rate(pdu.headroom())));
    }

    private static void riser(GuiGraphics g, Font font, PduRect outer, PowerReadings.Pdu pdu,
                              PowerHover hover, float phase, boolean reduceMotion) {
        final PduRect r = PowerArt.cardTitled(g, font, outer, "SINGLE-LINE",
                pdu.mode().getDisplayName(), PduTheme.ACCENT);
        if (r.h() < 40) {
            return;
        }

        final int feedH = 15;
        final PduRect feed = new PduRect(r.x(), r.y(), r.w(), feedH);
        g.fill(feed.x(), feed.y(), feed.right(), feed.bottom(), PowerArt.alpha(PduTheme.INFO, 0x1E));
        PduTheme.outline(g, feed.x(), feed.y(), feed.w(), feed.h(), PowerArt.alpha(PduTheme.INFO, 0x80));
        g.drawString(font, "SUPPLY", feed.x() + 4, feed.y() + 4, PduTheme.INFO, false);
        final String supply = PowerFmt.compact(pdu.generation()) + " FE/t";
        g.drawString(font, supply, feed.right() - 4 - font.width(supply), feed.y() + 4,
                PduTheme.TEXT, false);
        hover.tip(feed, "Incoming supply", PowerFmt.rate(pdu.generation()) + " visible upstream",
                pdu.upstreamSources() + " source" + (pdu.upstreamSources() == 1 ? "" : "s")
                        + (pdu.reserveTanks() > 0 ? (", " + pdu.reserveTanks() + " reserve banks") : ""));

        final int busX = r.x() + 12;
        final int busTop = feed.bottom();
        final List<PowerReadings.Leg> legs = pdu.legs();

        int rows = legs.size();
        int rowH = (r.bottom() - busTop - 6) / Math.max(1, rows);
        List<PowerReadings.Leg> shown = legs;
        if (rowH < 13) {
            shown = new ArrayList<>();
            for (final PowerReadings.Leg leg : legs) {
                if (leg.wired()) {
                    shown.add(leg);
                }
            }
            if (shown.isEmpty()) {
                shown = legs;
            }
            rows = shown.size();
            rowH = (r.bottom() - busTop - 6) / Math.max(1, rows);
        }
        rowH = Mth.clamp(rowH, 9, 22);

        final int fits = Math.max(1, (r.bottom() - busTop - 2) / rowH);
        int hidden = 0;
        if (rows > fits) {
            hidden = rows - fits;
            shown = shown.subList(0, fits);
            rows = fits;
        }

        final int busBottom = Math.min(r.bottom() - 2, busTop + rows * rowH);

        g.fill(busX - 1, busTop, busX + 2, busBottom, PowerArt.scale(PduTheme.ACCENT, 0.55f));
        g.fill(busX, busTop, busX + 1, busBottom, PduTheme.ACCENT);
        final float busLoad = pdu.generation() > 0.0f
                ? Mth.clamp(pdu.served() / pdu.generation(), 0.0f, 1.0f) : (pdu.served() > 0 ? 1.0f : 0.0f);
        PowerArt.currentDots(g, busX, busTop, busX, busBottom, busLoad,
                PduTheme.ACCENT, phase, reduceMotion);

        PowerArt.currentDots(g, busX, feed.y() + feedH / 2, busX, busTop, busLoad,
                PduTheme.INFO, phase, reduceMotion);

        final int labelW = Math.max(24, font.width("Leg 9") + 2);
        final int valueW = 34;
        for (int i = 0; i < rows; i++) {
            final PowerReadings.Leg leg = shown.get(i);
            final int ry = busTop + i * rowH;
            final int cy = ry + rowH / 2;
            final boolean live = leg.wired() && leg.served() > 0.5f;
            final int tint = !leg.wired() ? PduTheme.TEXT_FAINT
                    : leg.tripped() ? PduTheme.CRIT
                    : leg.open() ? PduTheme.TEXT_FAINT
                    : PowerArt.loadTint(leg.loadFraction());

            g.fill(busX + 1, cy, busX + 12, cy + 1,
                    leg.wired() ? PowerArt.alpha(tint, 0xCC) : PowerArt.alpha(PduTheme.STROKE, 0xCC));
            if (live) {
                PowerArt.currentDots(g, busX + 1, cy, busX + 12, cy,
                        Math.max(0.15f, leg.loadFraction()), tint, phase * 1.4f, reduceMotion);
            }
            PowerArt.breaker(g, busX + 18, cy, leg.wired() && !leg.open(), leg.tripped(), live, tint);

            final int textX = busX + 26;
            g.drawString(font, leg.name(), textX, cy - 4,
                    leg.wired() ? PduTheme.TEXT_DIM : PduTheme.TEXT_FAINT, false);

            final int barX = textX + labelW;
            final int barW = r.right() - barX - valueW - 3;
            if (barW > 14 && rowH >= 11) {
                if (leg.wired()) {
                    PowerArt.bar(g, barX, cy - 3, barW, 6, leg.loadFraction(), tint);
                    if (leg.rating() > 0.0f) {
                        final int tickX = barX + Math.round(barW * PowerArt.CONTINUOUS_LOAD);
                        g.fill(tickX, cy - 4, tickX + 1, cy + 4,
                                PowerArt.alpha(PduTheme.TEXT_FAINT, 0xAA));
                    }
                } else {
                    g.fill(barX, cy, barX + barW, cy + 1, PowerArt.alpha(PduTheme.STROKE, 0x99));
                }
            }
            final String value = leg.wired() ? PowerFmt.compact(leg.served()) : "—";
            g.drawString(font, value, r.right() - font.width(value), cy - 4,
                    leg.wired() ? PduTheme.TEXT : PduTheme.TEXT_FAINT, false);

            hover.tip(r.x(), ry, r.w(), rowH, legTip(leg));
        }

        if (hidden > 0 && r.bottom() - busBottom >= 10) {
            final String more = "+" + hidden + " more";
            g.drawString(font, more, r.right() - font.width(more), busBottom + 1,
                    PduTheme.TEXT_FAINT, false);
        }
        if (pdu.reserveLinked() && hidden == 0 && r.bottom() - busBottom >= 12) {
            final String stance = pdu.stance() == null ? "linked" : pdu.stance().label();
            final String text = "▲ reserve " + PowerFmt.pct0(pdu.reservePct()) + " · " + stance;
            g.drawString(font, text, r.x(), r.bottom() - 9,
                    pdu.stance() != null && pdu.stance().isRationing() ? PduTheme.WARN : PduTheme.INFO,
                    false);
            hover.tip(r.x(), r.bottom() - 10, r.w(), 10,
                    "Upstream battery controller",
                    "Reserve at " + PowerFmt.pct(pdu.reservePct()),
                    pdu.stance() == null ? "Stance unknown" : (pdu.stance().label() + " — " + pdu.stance().blurb()),
                    "Sustained allowance " + PowerFmt.rate(pdu.reserveAllowance()),
                    pdu.coreProtected() ? "Core loads are protected from shedding" : null);
        }
    }

    private static String[] legTip(PowerReadings.Leg leg) {
        if (!leg.wired()) {
            return new String[]{leg.name(), "Nothing wired to this terminal"};
        }
        final List<String> lines = new ArrayList<>();
        lines.add(leg.name() + " · " + leg.stateLabel());
        lines.add("Carrying " + PowerFmt.rate(leg.served()));
        lines.add("Asked for " + PowerFmt.rate(leg.demand()));
        if (leg.rating() > 0.0f) {
            lines.add("Rated " + PowerFmt.rate(leg.rating())
                    + " · " + PowerFmt.pct0(leg.loadFraction() * 100.0f) + " loaded");
        } else {
            lines.add("No conductor rating on this terminal");
        }
        lines.add(leg.devices() + " device" + (leg.devices() == 1 ? "" : "s"));
        if (leg.tripped()) {
            lines.add("Sustained overload threw this breaker");
        } else if (leg.open()) {
            lines.add("You opened this breaker — nothing is being offered to it");
        } else if (leg.overCapacity()) {
            lines.add("Over its rating: heavier cable, not more generation");
        } else if (leg.shortfall() > 0.5f) {
            lines.add("Short by " + PowerFmt.rate(leg.shortfall()));
        }
        return lines.toArray(new String[0]);
    }

    private static void profile(GuiGraphics g, Font font, PduRect outer, PowerReadings.Pdu pdu,
                                PowerHover hover, float phase) {
        final PduRect r = PowerArt.cardTitled(g, font, outer, "LOAD PROFILE",
                "demand over served", PduTheme.INFO);
        if (r.h() < 26) {
            return;
        }
        final int axisW = 26;
        final PduRect plot = new PduRect(r.x(), r.y(), Math.max(20, r.w() - axisW), Math.max(14, r.h() - 11));

        final TimeSeriesStore servedStore = pdu.history("pdu_served_demand");
        final TimeSeriesStore demandStore = pdu.history("pdu_demand");
        final int want = Math.max(8, plot.w() - 2);
        final float[] served = PowerArt.tail(servedStore, want);
        final float[] demand = PowerArt.tail(demandStore, want);

        PowerArt.plotFrame(g, plot, 4);
        if (served.length < 2) {
            final String msg = "Gathering history";
            g.drawString(font, msg, plot.centerX() - font.width(msg) / 2, plot.centerY() - 4,
                    PduTheme.TEXT_FAINT, false);
            return;
        }

        final float axisMax = PowerArt.niceMax(Math.max(
                PowerArt.peak(served, demand),
                Math.max(pdu.ceiling(), pdu.budget())) * 1.08f);
        PowerArt.loadProfile(g, plot, served, demand.length >= served.length ? demand : null,
                axisMax, PduTheme.OK, phase);

        final int wiringY = reference(g, font, plot, pdu.ceiling(), axisMax, PduTheme.CRIT,
                "wiring", Integer.MIN_VALUE);
        reference(g, font, plot, pdu.budget(), axisMax, PduTheme.ACCENT, "budget", wiringY);
        PowerArt.valueAxis(g, font, plot, axisMax, "FE/t");

        final int cadence = servedStore == null ? 20 : servedStore.getSampleIntervalTicks();
        PowerArt.timeAxis(g, font, plot, Math.max(1, served.length * cadence / 20), null);

        if (hover.over(plot)) {
            final int idx = Mth.clamp(Math.round((hover.mx() - plot.x() - 1)
                    * (served.length - 1) / (float) Math.max(1, plot.w() - 2)), 0, served.length - 1);
            final int px = plot.x() + 1 + Math.round((plot.w() - 2) * (idx / (float) (served.length - 1)));
            g.fill(px, plot.y() + 1, px + 1, plot.bottom() - 1, PowerArt.alpha(PduTheme.TEXT, 0x66));
            final float d = idx < demand.length ? demand[idx] : served[idx];
            final int agoSeconds = (served.length - 1 - idx) * cadence / 20;
            hover.set(PowerFmt.ago(agoSeconds) + " ago",
                    "Served " + PowerFmt.rate(served[idx]),
                    "Demand " + PowerFmt.rate(d),
                    d - served[idx] > 0.5f ? ("Shed " + PowerFmt.rate(d - served[idx])) : "Fully served");
        }
    }

    private static int reference(GuiGraphics g, Font font, PduRect plot, float value, float axisMax,
                                 int tint, String label, int taken) {
        if (value <= 0.0f || value > axisMax) {
            return taken;
        }
        final int ly = plot.bottom() - 1
                - Math.round((plot.h() - 2) * Mth.clamp(value / axisMax, 0.0f, 1.0f));
        PowerArt.dashedH(g, plot.x() + 1, plot.right() - 1, ly, PowerArt.alpha(tint, 0xCC), 3, 3);
        if (plot.w() <= 90 || ly - 9 < plot.y() || Math.abs(ly - taken) < 10) {
            return taken;
        }
        g.drawString(font, label, plot.x() + 3, ly - 9, PowerArt.alpha(tint, 0xDD), false);
        return ly;
    }

    private static void schedule(GuiGraphics g, Font font, PduRect outer, PowerReadings.Pdu pdu,
                                 PowerHover hover, float phase) {
        final List<PowerReadings.Leg> wired = new ArrayList<>();
        for (final PowerReadings.Leg leg : pdu.legs()) {
            if (leg.wired()) {
                wired.add(leg);
            }
        }
        wired.sort(Comparator.comparingDouble((PowerReadings.Leg l) -> -l.served()));

        final PduRect r = PowerArt.cardTitled(g, font, outer, "PANEL SCHEDULE",
                wired.size() + (wired.size() == 1 ? " circuit" : " circuits"), PduTheme.OK);
        if (r.h() < 16) {
            return;
        }
        if (wired.isEmpty()) {
            g.drawString(font, "No circuits wired to this panel", r.x(), r.y() + 2,
                    PduTheme.TEXT_FAINT, false);
            return;
        }

        final int header = 9;
        final int labelW = Math.max(font.width("circuit") + 4, font.width("Leg 9") + 4);
        final int valueW = 46;
        final int stateW = r.w() >= 250 ? 62 : 0;
        final int barX = r.x() + labelW;
        final int barW = Math.max(20, r.w() - labelW - valueW - stateW - 4);

        if (font.width("circuit") <= labelW - 3) {
            g.drawString(font, "circuit", r.x(), r.y(), PduTheme.TEXT_FAINT, false);
        }
        final String barHead = font.width("load against rating") <= barW - 3 ? "load against rating"
                : (font.width("load") <= barW - 3 ? "load" : "");
        if (!barHead.isEmpty()) {
            g.drawString(font, barHead, barX, r.y(), PduTheme.TEXT_FAINT, false);
        }
        if (font.width("carrying") <= valueW - 2) {
            g.drawString(font, "carrying", barX + barW + 4, r.y(), PduTheme.TEXT_FAINT, false);
        }

        final int avail = r.h() - header;
        final int rowH = Mth.clamp(avail / Math.max(1, wired.size()), 9, 18);
        final int visible = Math.max(1, Math.min(wired.size(), avail / rowH));
        for (int i = 0; i < visible; i++) {
            final PowerReadings.Leg leg = wired.get(i);
            final int ry = r.y() + header + i * rowH;
            final int cy = ry + rowH / 2;
            if (i % 2 == 1) {
                g.fill(r.x(), ry, r.right(), ry + rowH, PowerArt.alpha(PduTheme.BG_RAISED, 0x55));
            }
            g.drawString(font, leg.name(), r.x(), cy - 4, PduTheme.TEXT_DIM, false);
            PowerArt.scheduleBar(g, barX, cy - 4, barW, 8, leg.served(), leg.demand(), leg.rating(),
                    phase);
            final String value = PowerFmt.compact(leg.served());
            g.drawString(font, value, barX + barW + 4, cy - 4, PduTheme.TEXT, false);
            if (stateW > 0) {
                final int tint = leg.tripped() || leg.overCapacity() ? PduTheme.CRIT
                        : leg.open() ? PduTheme.TEXT_FAINT
                        : leg.shortfall() > 0.5f ? PduTheme.WARN : PduTheme.OK;
                final String state = leg.stateLabel();
                g.drawString(font, state, r.right() - font.width(state), cy - 4, tint, false);
            }
            hover.tip(r.x(), ry, r.w(), rowH, legTip(leg));
        }
        if (visible < wired.size()) {
            final String more = "+" + (wired.size() - visible) + " more";
            g.drawString(font, more, r.right() - font.width(more), r.bottom() - 8,
                    PduTheme.TEXT_FAINT, false);
        }
    }

    private static void tiers(GuiGraphics g, Font font, PduRect outer, PowerReadings.Pdu pdu,
                              PowerHover hover) {
        final FePriority[] tiers = FePriority.values();
        int shedLine = -1;
        for (int i = 0; i < tiers.length; i++) {
            final int total = i < pdu.tierTotal().length ? pdu.tierTotal()[i] : 0;
            final int served = i < pdu.tierServed().length ? pdu.tierServed()[i] : 0;
            if (total > 0 && served < total && shedLine < 0) {
                shedLine = i;
            }
        }

        final PduRect r = PowerArt.cardTitled(g, font, outer, "SHEDDING SCHEME",
                shedLine >= 0
                        ? ("shedding from " + tiers[shedLine].getDisplayName().toLowerCase(java.util.Locale.ROOT))
                        : (pdu.servedDevices() + " of " + pdu.devices() + " running"),
                PduTheme.WARN);
        if (r.h() < 16) {
            return;
        }
        final int colW = r.w() / tiers.length;

        for (int i = 0; i < tiers.length; i++) {
            final int cx = r.x() + i * colW;
            final int total = i < pdu.tierTotal().length ? pdu.tierTotal()[i] : 0;
            final int served = i < pdu.tierServed().length ? pdu.tierServed()[i] : 0;
            final boolean below = shedLine >= 0 && i > shedLine;
            final int tint = total == 0 ? PduTheme.TEXT_FAINT
                    : served >= total ? PduTheme.OK
                    : served > 0 ? PduTheme.WARN : PduTheme.CRIT;

            g.drawString(font, tiers[i].getDisplayName(), cx, r.y(),
                    total == 0 ? PduTheme.TEXT_FAINT : PduTheme.TEXT_DIM, false);
            final String count = total == 0 ? "—" : (served + "/" + total);
            g.drawString(font, count, cx, r.y() + 10, tint, false);
            if (total > 0 && colW > 34) {
                PowerArt.pips(g, cx + font.width(count) + 5, r.y() + 11, served, total, tint,
                        PowerArt.alpha(PduTheme.STROKE, 0xEE));
            }
            if (r.h() >= 26) {
                PowerArt.bar(g, cx, r.y() + 20, colW - 4, 4,
                        total == 0 ? 0.0f : served / (float) total, tint);
            }
            if (below) {
                g.fill(cx, r.y() - 1, cx + colW - 4, r.bottom(), PowerArt.alpha(PduTheme.CRIT, 0x12));
            }
            hover.tip(cx, r.y() - 2, colW, r.h() + 2,
                    tiers[i].getDisplayName() + " tier",
                    total == 0 ? "Nothing on this tier"
                            : (served + " of " + total + " running"),
                    i == 0 ? "Served first, and effectively never shed"
                            : (i == tiers.length - 1 ? "Shed first when supply runs short"
                            : "Shed after every tier below it"));
        }

        if (shedLine > 0) {
            final int lx = r.x() + shedLine * colW - 2;
            PowerArt.dashedV(g, lx, r.y() - 2, r.bottom(), PduTheme.CRIT, 2, 2);
            g.fill(lx - 1, r.y() - 4, lx + 2, r.y() - 1, PduTheme.CRIT);
        }
    }

    private static void diagnosis(GuiGraphics g, Font font, int x, int y, int w,
                                  PowerReadings.Pdu pdu) {
        final String message = pdu.diagnosis();
        final boolean ok = message == null;
        final int tint = ok ? PduTheme.OK : (pdu.throughputLimited() ? PduTheme.CRIT : PduTheme.WARN);
        g.fill(x, y + 2, x + 3, y + 8, tint);
        final String text = ok
                ? ("All " + pdu.wiredLegs() + " circuits served · "
                    + PowerFmt.rate(pdu.headroom()) + " of supply headroom")
                : message;
        g.drawString(font, trim(font, text, w - 8), x + 7, y + 1, tint, false);
    }

    private static String trim(Font font, String text, int max) {
        if (font.width(text) <= max) {
            return text;
        }
        String cut = text;
        while (cut.length() > 4 && font.width(cut + "…") > max) {
            cut = cut.substring(0, cut.length() - 1);
        }
        return cut + "…";
    }
}
