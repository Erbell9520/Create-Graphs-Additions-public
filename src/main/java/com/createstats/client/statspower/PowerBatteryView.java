package com.createstats.client.statspower;

import java.util.ArrayList;
import java.util.List;

import com.createstats.data.TimeSeriesStore;
import com.createstats.gui.pdu.PduRect;
import com.createstats.gui.pdu.PduTheme;
import com.createstats.power.BatteryControllerPlan;
import com.createstats.power.FeReserveReport;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

// zero in the middle, charge above
@OnlyIn(Dist.CLIENT)
public final class PowerBatteryView {
    private PowerBatteryView() {
    }

    private static final int GAP = 5;

    public static void render(GuiGraphics g, Font font, PduRect area, PowerReadings.Bank bank,
                              PowerHover hover, float phase, boolean reduceMotion) {
        if (bank == null || area.w() < 60 || area.h() < 40) {
            return;
        }
        int top = area.y();
        int bot = area.bottom();

        final int kpiH = area.h() >= 300 ? 46 : 38;
        if (bot - top - kpiH - GAP >= 64) {
            kpiStrip(g, font, new PduRect(area.x(), top, area.w(), kpiH), bank, hover);
            top += kpiH + GAP;
        }
        if (bot - top - 44 - GAP >= 96) {
            bot -= 44;
            feeds(g, font, new PduRect(area.x(), bot, area.w(), 44), bank, hover, phase,
                    reduceMotion);
            bot -= GAP;
        }
        if (bot - top - 13 >= 70) {
            bot -= 13;
            diagnosis(g, font, area.x(), bot + 2, area.w(), bank);
        }

        final PduRect main = new PduRect(area.x(), top, area.w(), Math.max(0, bot - top));
        if (main.h() < 30) {
            return;
        }
        final int reserveW = Mth.clamp(Math.round(area.w() * 0.30f), 118, 200);
        if (area.w() >= 400) {
            reserve(g, font, new PduRect(main.x(), main.y(), reserveW, main.h()), bank, hover, phase);
            final int rx = main.x() + reserveW + GAP;
            final int rw = main.right() - rx;
            final int flowH = Mth.clamp(Math.round(main.h() * 0.5f), 58, main.h());
            final int outH = main.h() - flowH - GAP;
            powerFlow(g, font, new PduRect(rx, main.y(), rw, outH >= 34 ? flowH : main.h()), bank,
                    hover);
            if (outH >= 34) {
                outputs(g, font, new PduRect(rx, main.y() + flowH + GAP, rw, outH), bank, hover,
                        phase);
            }
        } else {
            final int flowH = Mth.clamp(Math.round(main.h() * 0.42f), 54, main.h());
            final int restH = main.h() - flowH - GAP;
            if (restH >= 40) {
                powerFlow(g, font, new PduRect(main.x(), main.y(), main.w(), flowH), bank, hover);
                final int restY = main.y() + flowH + GAP;
                final int rw = Math.min(reserveW, Math.max(60, main.w() / 2));
                reserve(g, font, new PduRect(main.x(), restY, rw, restH), bank, hover, phase);
                outputs(g, font, new PduRect(main.x() + rw + GAP, restY,
                        main.right() - main.x() - rw - GAP, restH), bank, hover, phase);
            } else {
                powerFlow(g, font, main, bank, hover);
            }
        }
    }

    private static void kpiStrip(GuiGraphics g, Font font, PduRect r, PowerReadings.Bank bank,
                                 PowerHover hover) {
        final int wide = r.w() >= 560 ? 6 : (r.w() >= 400 ? 5 : (r.w() >= 280 ? 4 : 3));
        final String[][] labels = new String[6][];
        final int[] tints = new int[6];
        final float[][] sparks = new float[6][];
        final String[][] tips = new String[6][];

        labels[0] = new String[]{"RESERVE", PowerFmt.pct0(bank.fillPct()), ""};
        tints[0] = PowerArt.chargeTint(bank.fillPct());
        sparks[0] = PowerArt.tail(bank.history("energy_fill_pct"), 40);
        tips[0] = new String[]{"Pooled charge across every bank on this controller",
                PowerFmt.fe(bank.reserve()) + " of " + PowerFmt.fe(bank.capacity()) + " FE"};

        labels[1] = new String[]{bank.charging() ? "CHARGING" : (bank.discharging() ? "DISCHARGING" : "AT REST"),
                PowerFmt.signed(bank.net()), "FE/t"};
        tints[1] = bank.charging() ? PduTheme.OK : (bank.discharging() ? PduTheme.WARN : PduTheme.TEXT_FAINT);
        sparks[1] = PowerArt.tail(bank.history("energy_net_rate"), 40);
        tips[1] = new String[]{"Net movement of the pooled reserve",
                bank.charging() ? "Generation is ahead of load — the banks are filling"
                        : bank.discharging() ? "Load is ahead of generation — the banks are covering it"
                        : "Generation and load are matched"};

        labels[2] = new String[]{"INPUT", PowerFmt.compact(bank.generation()), "FE/t"};
        tints[2] = PduTheme.INFO;
        sparks[2] = PowerArt.tail(bank.history("energy_input_rate"), 40);
        tips[2] = new String[]{"Generation arriving at the input terminals"};

        labels[3] = new String[]{"OUTPUT", PowerFmt.compact(bank.delivered()), "FE/t"};
        tints[3] = PduTheme.ACCENT;
        sparks[3] = PowerArt.tail(bank.history("energy_output_rate"), 40);
        tips[3] = new String[]{"Leaving down all three output circuits",
                bank.totalShortfall() > 0.5f
                        ? ("Circuits are short by " + PowerFmt.rate(bank.totalShortfall()))
                        : "Every circuit is getting what it asked for"};

        final boolean draining = bank.discharging();
        labels[4] = new String[]{draining ? "TIME LEFT" : "TIME TO FULL",
                draining ? PowerFmt.endurance(bank.enduranceTicks(), true)
                        : PowerFmt.endurance(bank.ticksToFull(), bank.ticksToFull() > 0.0f), ""};
        tints[4] = draining ? (bank.fillPct() < 20.0f ? PduTheme.CRIT : PduTheme.WARN) : PduTheme.OK;
        sparks[4] = null;
        tips[4] = new String[]{draining
                ? "How long the reserve lasts at the present drain"
                : "How long until the banks are full at the present charge rate",
                "Projected from the current rate, which will move"};

        final FeReserveReport.Stance stance = bank.stance();
        labels[5] = new String[]{"STANCE", stance == null ? "—" : stance.label(), ""};
        tints[5] = stance == null ? PduTheme.TEXT_FAINT
                : stance.isRationing() ? PduTheme.WARN : PduTheme.OK;
        sparks[5] = null;
        tips[5] = new String[]{"The policy the controller is running right now",
                stance == null ? "No stance reported" : stance.blurb(),
                bank.managedDownstream()
                        ? "Distribution panels downstream follow this stance"
                        : "No distribution panel is following this controller"};

        final int n = Math.min(wide, 6);
        final int tileW = (r.w() - GAP * (n - 1)) / n;
        for (int i = 0; i < n; i++) {
            final int tx = r.x() + i * (tileW + GAP);
            final PduRect tile = new PduRect(tx, r.y(), i == n - 1 ? r.right() - tx : tileW, r.h());
            PowerArt.kpi(g, font, tile, labels[i][0], labels[i][1], labels[i][2], tints[i],
                    sparks[i], trend(sparks[i]));
            hover.tip(tile, tips[i]);
        }
    }

    private static float trend(float[] data) {
        if (data == null || data.length < 4) {
            return Float.NaN;
        }
        return data[data.length - 1] - data[0];
    }

    private static void reserve(GuiGraphics g, Font font, PduRect outer, PowerReadings.Bank bank,
                                PowerHover hover, float phase) {
        final PduRect r = PowerArt.cardTitled(g, font, outer, "RESERVE",
                PowerFmt.fe(bank.capacity()) + " FE", PowerArt.chargeTint(bank.fillPct()));
        if (r.h() < 30) {
            return;
        }

        final List<Float> floorList = new ArrayList<>();
        final List<Integer> floorTints = new ArrayList<>();
        for (final PowerReadings.Circuit c : bank.circuits()) {
            if (!c.wired() || c.floorPct() <= 0.0f) {
                continue;
            }
            floorList.add(c.floorPct());
            floorTints.add(bank.fillPct() < c.floorPct() ? PduTheme.CRIT : PduTheme.WARN);
        }
        final float[] floors = new float[floorList.size()];
        final int[] tints = new int[floorList.size()];
        for (int i = 0; i < floors.length; i++) {
            floors[i] = floorList.get(i);
            tints[i] = floorTints.get(i);
        }

        final int colW = Mth.clamp(r.w() / 5, 22, 42);
        final PduRect column = new PduRect(r.x() + 4, r.y() + 1, colW, Math.max(20, r.h() - 2));
        PowerArt.socColumn(g, font, column, bank.fillPct(), bank.net(), floors, tints, phase);
        hover.tip(column.x() - 4, column.y(), column.w() + 8, column.h(),
                "Pooled reserve",
                PowerFmt.fe(bank.reserve()) + " of " + PowerFmt.fe(bank.capacity()) + " FE",
                PowerFmt.pct(bank.fillPct()) + " charged",
                floors.length == 0 ? "No output has a reserve floor set"
                        : (floors.length + " output floor" + (floors.length == 1 ? "" : "s")
                            + " marked on the column"));

        int tx = column.right() + 8;
        int ty = r.y() + 1;
        final int tw = Math.max(10, r.right() - tx);
        if (tw < 40) {
            return;
        }

        final FeReserveReport.Stance stance = bank.stance();
        if (stance != null) {
            final int chipTint = stance.isRationing() ? PduTheme.WARN : PduTheme.OK;
            PowerArt.chip(g, font, tx, ty, stance.label(), chipTint,
                    PowerArt.alpha(chipTint, 0x22));
            ty += 15;
        }

        ty = stat(g, font, tx, ty, tw, "charge", PowerFmt.fe(bank.reserve()) + " FE",
                PduTheme.TEXT);
        ty = stat(g, font, tx, ty, tw, bank.charging() ? "filling" : bank.discharging() ? "draining" : "net",
                PowerFmt.signed(bank.net()) + " FE/t",
                bank.charging() ? PduTheme.OK : bank.discharging() ? PduTheme.WARN : PduTheme.TEXT_DIM);
        if (r.h() >= 62) {
            ty = stat(g, font, tx, ty, tw, "bus", PowerFmt.fe(bank.bus()) + " FE", PduTheme.TEXT_DIM);
        }
        if (r.h() >= 78) {
            final float toFull = bank.ticksToFull();
            ty = stat(g, font, tx, ty, tw,
                    bank.discharging() ? "empty in" : "full in",
                    bank.discharging() ? PowerFmt.endurance(bank.enduranceTicks(), true)
                            : PowerFmt.endurance(toFull, toFull > 0.0f),
                    bank.discharging() && bank.fillPct() < 20.0f ? PduTheme.CRIT : PduTheme.TEXT_DIM);
        }

        if (Math.abs(bank.net()) > 0.5f && column.h() > 30) {
            final int level = column.bottom() - Math.round(column.h() * Mth.clamp(bank.fillPct(), 0.0f, 100.0f) / 100.0f);
            final boolean up = bank.net() > 0.0f;
            final int ax = column.x() + column.w() / 2;
            final int ay = Mth.clamp(level + (up ? -6 : 6), column.y() + 4, column.bottom() - 4);
            final int tint = up ? PduTheme.OK : PduTheme.WARN;
            PowerArt.line(g, ax, ay + (up ? 4 : -4), ax, ay, tint);
            PowerArt.line(g, ax - 2, ay + (up ? 2 : -2), ax, ay, tint);
            PowerArt.line(g, ax + 2, ay + (up ? 2 : -2), ax, ay, tint);
        }
    }

    private static int stat(GuiGraphics g, Font font, int x, int y, int w, String label, String value,
                            int tint) {
        g.drawString(font, label, x, y, PduTheme.TEXT_FAINT, false);
        final int vx = x + w - font.width(value);
        g.drawString(font, value, Math.max(x + font.width(label) + 4, vx), y, tint, false);
        return y + 11;
    }

    private static void powerFlow(GuiGraphics g, Font font, PduRect outer, PowerReadings.Bank bank,
                                  PowerHover hover) {
        final PduRect r = PowerArt.cardTitled(g, font, outer, "POWER FLOW", "", PduTheme.INFO);
        if (r.h() < 26) {
            return;
        }
        final int axisW = 26;
        final PduRect plot = new PduRect(r.x(), r.y(), Math.max(20, r.w() - axisW),
                Math.max(16, r.h() - 11));

        final TimeSeriesStore netStore = bank.history("energy_net_rate");
        final int want = Math.max(8, plot.w() - 2);
        final float[] net = PowerArt.tail(netStore, want);
        final float[] soc = PowerArt.tail(bank.history("energy_fill_pct"), want);

        PowerArt.plotFrame(g, plot, 4);
        if (net.length < 2) {
            final String msg = "Gathering history";
            g.drawString(font, msg, plot.centerX() - font.width(msg) / 2, plot.centerY() - 4,
                    PduTheme.TEXT_FAINT, false);
            return;
        }

        final float axisMax = PowerArt.niceMax(Math.max(1.0f, PowerArt.peak(net)) * 1.1f);
        PowerArt.bipolar(g, plot, net, axisMax, PduTheme.OK, PduTheme.WARN);

        if (soc.length >= 2) {
            g.enableScissor(plot.x() + 1, plot.y() + 1, plot.right() - 1, plot.bottom() - 1);
            int prevX = plot.x() + 1;
            int prevY = plot.bottom() - 1
                    - Math.round((plot.h() - 2) * Mth.clamp(soc[0] / 100.0f, 0.0f, 1.0f));
            for (int i = 1; i < soc.length; i++) {
                final int px = plot.x() + 1
                        + Math.round((plot.w() - 2) * (i / (float) (soc.length - 1)));
                final int py = plot.bottom() - 1
                        - Math.round((plot.h() - 2) * Mth.clamp(soc[i] / 100.0f, 0.0f, 1.0f));
                PowerArt.line(g, prevX, prevY, px, py, PowerArt.alpha(PduTheme.ACCENT, 0xE0));
                prevX = px;
                prevY = py;
            }
            g.disableScissor();
        }

        PowerArt.valueAxis(g, font, plot, axisMax, "FE/t");
        final int cadence = netStore == null ? 20 : netStore.getSampleIntervalTicks();
        PowerArt.timeAxis(g, font, plot, Math.max(1, net.length * cadence / 20), null);

        if (plot.w() > 150) {
            legend(g, font, plot.x() + 4, plot.y() + 3, PduTheme.OK, "charge");
            legend(g, font, plot.x() + 4 + 46, plot.y() + 3, PduTheme.WARN, "discharge");
            legend(g, font, plot.x() + 4 + 106, plot.y() + 3, PduTheme.ACCENT, "SoC");
        }

        if (hover.over(plot)) {
            final int idx = Mth.clamp(Math.round((hover.mx() - plot.x() - 1)
                    * (net.length - 1) / (float) Math.max(1, plot.w() - 2)), 0, net.length - 1);
            final int px = plot.x() + 1
                    + Math.round((plot.w() - 2) * (idx / (float) (net.length - 1)));
            g.fill(px, plot.y() + 1, px + 1, plot.bottom() - 1, PowerArt.alpha(PduTheme.TEXT, 0x66));
            final int agoSeconds = (net.length - 1 - idx) * cadence / 20;
            hover.set(PowerFmt.ago(agoSeconds) + " ago",
                    (net[idx] >= 0.0f ? "Charging " : "Discharging ")
                            + PowerFmt.rate(Math.abs(net[idx])),
                    idx < soc.length ? ("Reserve " + PowerFmt.pct(soc[idx])) : null);
        }
    }

    private static void legend(GuiGraphics g, Font font, int x, int y, int tint, String label) {
        g.fill(x, y + 3, x + 6, y + 5, tint);
        g.drawString(font, label, x + 9, y, PduTheme.TEXT_FAINT, false);
    }

    private static void outputs(GuiGraphics g, Font font, PduRect outer, PowerReadings.Bank bank,
                                PowerHover hover, float phase) {
        int wired = 0;
        for (final PowerReadings.Circuit c : bank.circuits()) {
            if (c.wired()) {
                wired++;
            }
        }
        final PduRect r = PowerArt.cardTitled(g, font, outer, "OUTPUT CIRCUITS",
                wired + " of " + bank.circuits().size() + " wired", PduTheme.ACCENT);
        if (r.h() < 16) {
            return;
        }

        final int rowH = Mth.clamp(r.h() / Math.max(1, bank.circuits().size()), 12, 26);
        final boolean roomy = rowH >= 20 && r.w() >= 260;
        final int labelW = Math.max(38, font.width("Output 3") + 6);
        final int reasonW = r.w() >= 300 ? 74 : (r.w() >= 220 ? 58 : 0);
        final int valueW = 44;
        final int barX = r.x() + labelW;
        final int barW = Math.max(18, r.w() - labelW - valueW - reasonW - 6);

        for (int i = 0; i < bank.circuits().size(); i++) {
            final PowerReadings.Circuit c = bank.circuits().get(i);
            final int ry = r.y() + i * rowH;
            if (ry + rowH > r.bottom() + 2) {
                break;
            }
            final int cy = ry + (roomy ? 6 : rowH / 2);
            final int tint = !c.wired() ? PduTheme.TEXT_FAINT
                    : c.reason() == BatteryControllerPlan.Reason.SERVED ? PduTheme.OK
                    : c.withheld() ? PduTheme.CRIT
                    : c.faulted() ? PduTheme.WARN : PduTheme.TEXT_DIM;

            g.drawString(font, c.name(), r.x(), cy - 4,
                    c.wired() ? PduTheme.TEXT_DIM : PduTheme.TEXT_FAINT, false);
            if (c.wired()) {
                PowerArt.scheduleBar(g, barX, cy - 4, barW, 8, c.flow(), c.demand(), c.ceiling(), phase);
            } else {
                g.fill(barX, cy, barX + barW, cy + 1, PowerArt.alpha(PduTheme.STROKE, 0x99));
            }
            final String value = c.wired() ? PowerFmt.compact(c.flow()) : "—";
            g.drawString(font, value, barX + barW + 4, cy - 4,
                    c.wired() ? PduTheme.TEXT : PduTheme.TEXT_FAINT, false);
            if (reasonW > 0) {
                final String reason = c.reasonLabel();
                g.drawString(font, fit(font, reason, reasonW), r.right() - reasonW, cy - 4, tint, false);
            }

            if (roomy) {
                int cx = barX;
                cx += PowerArt.chip(g, font, cx, cy + 6, c.mode().label(), PduTheme.INFO,
                        PowerArt.alpha(PduTheme.INFO, 0x1E)) + 3;
                cx += PowerArt.chip(g, font, cx, cy + 6, c.tier().getDisplayName(), PduTheme.ACCENT,
                        PowerArt.alpha(PduTheme.ACCENT, 0x1E)) + 3;
                if (c.floorPct() > 0.0f && cx + 44 < r.right()) {
                    PowerArt.chip(g, font, cx, cy + 6, "floor " + PowerFmt.pct0(c.floorPct()),
                            bank.fillPct() < c.floorPct() ? PduTheme.CRIT : PduTheme.TEXT_FAINT,
                            PowerArt.alpha(PduTheme.STROKE, 0x66));
                }
            }
            hover.tip(r.x(), ry, r.w(), rowH, circuitTip(bank, c));
        }
    }

    private static String[] circuitTip(PowerReadings.Bank bank, PowerReadings.Circuit c) {
        if (!c.wired()) {
            return new String[]{c.name(), "Nothing wired to this terminal"};
        }
        final List<String> lines = new ArrayList<>();
        lines.add(c.name() + " · " + c.reasonLabel());
        lines.add("Delivering " + PowerFmt.rate(c.flow()));
        lines.add("Asked for " + PowerFmt.rate(c.demand()));
        if (c.ceiling() > 0.0f) {
            lines.add("Rated " + PowerFmt.rate(c.ceiling()));
        }
        lines.add("Mode " + c.mode().label() + " — " + c.mode().blurb());
        lines.add("Tier " + c.tier().getDisplayName());
        if (c.floorPct() > 0.0f) {
            lines.add("Reserve floor " + PowerFmt.pct0(c.floorPct())
                    + (bank.fillPct() < c.floorPct() ? " — currently below it" : " — currently above it"));
        }
        if (c.panels() > 0) {
            lines.add(c.panels() + " distribution panel" + (c.panels() == 1 ? "" : "s")
                    + " on this circuit");
            if (c.critical() > 0.0f) {
                lines.add("Critical load declared: " + PowerFmt.rate(c.critical()));
            }
        }
        lines.add(c.devices() + " device" + (c.devices() == 1 ? "" : "s"));
        if (c.reason() == BatteryControllerPlan.Reason.RECHARGE) {
            lines.add("Metered back on purpose so the banks refill — this ends on its own");
        } else if (c.reason() == BatteryControllerPlan.Reason.SHED) {
            lines.add("Higher tiers took the whole reserve — more generation is the fix");
        } else if (c.reason() == BatteryControllerPlan.Reason.NO_SURPLUS) {
            lines.add("Surplus-only circuit with no surplus to give it");
        }
        return lines.toArray(new String[0]);
    }

    private static String fit(Font font, String text, int max) {
        if (font.width(text) <= max) {
            return text;
        }
        String cut = text;
        while (cut.length() > 3 && font.width(cut + "…") > max) {
            cut = cut.substring(0, cut.length() - 1);
        }
        return cut + "…";
    }

    private static void feeds(GuiGraphics g, Font font, PduRect outer, PowerReadings.Bank bank,
                              PowerHover hover, float phase, boolean reduceMotion) {
        final PduRect r = PowerArt.cardTitled(g, font, outer, "INPUT TERMINALS",
                PowerFmt.rate(bank.generation()) + " arriving", PduTheme.INFO);
        if (r.h() < 12) {
            return;
        }
        final int colW = r.w() / Math.max(1, bank.feeds().size());
        final float busiest = Math.max(1.0f, maxFlow(bank));
        for (int i = 0; i < bank.feeds().size(); i++) {
            final PowerReadings.Feed feed = bank.feeds().get(i);
            final int cx = r.x() + i * colW;
            final boolean wired = feed.wired();
            final int tint = wired ? PduTheme.INFO : PduTheme.TEXT_FAINT;

            g.drawString(font, feed.name(), cx, r.y(), wired ? PduTheme.TEXT_DIM : PduTheme.TEXT_FAINT,
                    false);
            final String value = wired ? (PowerFmt.compact(feed.flow()) + " FE/t") : "not wired";
            g.drawString(font, value, cx, r.y() + 10, wired ? PduTheme.TEXT : PduTheme.TEXT_FAINT, false);

            if (wired && colW > 60) {
                final int barY = r.y() + 21;
                final int barW = colW - 8;
                PowerArt.bar(g, cx, barY, barW, 5, feed.flow() / busiest, tint);

                final int carried = Math.round(barW * Mth.clamp(feed.flow() / busiest, 0.0f, 1.0f));
                if (carried > 3) {
                    PowerArt.currentDots(g, cx + 1, barY + 2, cx + carried - 1, barY + 2,
                            Mth.clamp(feed.flow() / busiest, 0.0f, 1.0f), PowerArt.scale(tint, 1.6f),
                            phase, reduceMotion);
                }
            }
            hover.tip(cx, r.y() - 2, colW, r.h() + 2,
                    feed.name(),
                    wired ? ("Carrying " + PowerFmt.rate(feed.flow())) : "Nothing wired here",
                    feed.tanks() + " bank" + (feed.tanks() == 1 ? "" : "s") + " pooled",
                    feed.sources() + " source" + (feed.sources() == 1 ? "" : "s") + " feeding it");
        }
    }

    private static float maxFlow(PowerReadings.Bank bank) {
        float max = 0.0f;
        for (final PowerReadings.Feed feed : bank.feeds()) {
            max = Math.max(max, feed.flow());
        }
        return max;
    }

    private static void diagnosis(GuiGraphics g, Font font, int x, int y, int w,
                                  PowerReadings.Bank bank) {
        final String message = bank.diagnosis();
        final boolean ok = message == null;
        final int tint = ok ? PduTheme.OK
                : (bank.stance() == FeReserveReport.Stance.CRITICAL ? PduTheme.CRIT : PduTheme.WARN);
        g.fill(x, y + 2, x + 3, y + 8, tint);
        final String text = ok
                ? (bank.charging()
                    ? ("Reserve filling at " + PowerFmt.rate(bank.net()) + " · every circuit served")
                    : "Every circuit served · reserve holding")
                : message;
        g.drawString(font, fit(font, text, w - 8), x + 7, y + 1, tint, false);
    }
}
