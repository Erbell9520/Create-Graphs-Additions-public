package com.createstats.client.statspower;

import com.createstats.blockentity.FlowMeterBE;
import com.createstats.data.TimeSeriesStore;
import com.createstats.gui.pdu.PduRect;
import com.createstats.gui.pdu.PduTheme;
import com.createstats.renderer.FluidPalette;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

// rate, direction, medium, total. same order as every real flow computer
@OnlyIn(Dist.CLIENT)
public final class PowerFlowView {
    private PowerFlowView() {
    }

    private static final int GAP = 5;

    public static void render(GuiGraphics g, Font font, PduRect area, PowerReadings.Meter meter,
                              PowerHover hover, float phase, boolean reduceMotion) {
        if (meter == null || area.w() < 60 || area.h() < 40) {
            return;
        }
        final FlowMeterBE live = liveMeter(meter.pos());
        final TimeSeriesStore store = meter.history();

        int top = area.y();
        int bot = area.bottom();

        final int kpiH = area.h() >= 300 ? 46 : 38;
        if (bot - top - kpiH - GAP >= 64) {
            kpiStrip(g, font, new PduRect(area.x(), top, area.w(), kpiH), meter, store, hover);
            top += kpiH + GAP;
        }
        if (bot - top - 13 >= 70) {
            bot -= 13;
            diagnosis(g, font, area.x(), bot + 2, area.w(), meter, live);
        }

        final PduRect main = new PduRect(area.x(), top, area.w(), Math.max(0, bot - top));
        if (main.h() < 30) {
            return;
        }

        if (area.w() >= 430 && main.h() >= 150) {
            final int leftW = Mth.clamp(Math.round(area.w() * 0.44f), 190, 320);
            final int pipeH = Mth.clamp(Math.round(main.h() * 0.34f), 46, 72);
            final int rangeH = 42;
            final int totalH = main.h() - pipeH - rangeH - GAP * 2;
            pipe(g, font, new PduRect(main.x(), main.y(), leftW, pipeH), meter, live, hover, phase,
                    reduceMotion);
            range(g, font, new PduRect(main.x(), main.y() + pipeH + GAP, leftW, rangeH), meter, store,
                    live, hover, phase);
            if (totalH >= 24) {
                totaliser(g, font, new PduRect(main.x(), main.y() + pipeH + rangeH + GAP * 2, leftW,
                        totalH), meter, hover, reduceMotion);
            }

            final int rx = main.x() + leftW + GAP;
            final int rw = main.right() - rx;
            final int trendH = Mth.clamp(Math.round(main.h() * 0.56f), 62, main.h());
            final int statsH = main.h() - trendH - GAP;
            trend(g, font, new PduRect(rx, main.y(), rw, statsH >= 40 ? trendH : main.h()), meter,
                    store, hover, phase);
            if (statsH >= 40) {
                statistics(g, font, new PduRect(rx, main.y() + trendH + GAP, rw, statsH), meter,
                        store, hover);
            }
        } else {
            final int pipeH = Mth.clamp(Math.round(main.h() * 0.45f), 40, 64);
            final int rest = main.h() - pipeH - GAP;
            pipe(g, font, new PduRect(main.x(), main.y(), main.w(), pipeH), meter, live, hover, phase,
                    reduceMotion);
            if (rest >= 40) {
                final int trendH = Mth.clamp(Math.round(rest * 0.62f), 50, rest);
                final int totalH = rest - trendH - GAP;
                trend(g, font, new PduRect(main.x(), main.y() + pipeH + GAP, main.w(),
                        totalH >= 22 ? trendH : rest), meter, store, hover, phase);
                if (totalH >= 22) {
                    totaliser(g, font, new PduRect(main.x(), main.y() + pipeH + trendH + GAP * 2,
                            main.w(), totalH), meter, hover, reduceMotion);
                }
            }
        }
    }

    private static void kpiStrip(GuiGraphics g, Font font, PduRect r, PowerReadings.Meter meter,
                                 TimeSeriesStore store, PowerHover hover) {
        final int wide = r.w() >= 560 ? 6 : (r.w() >= 400 ? 5 : (r.w() >= 280 ? 4 : 3));
        final int fluidTint = meter.known() ? FluidPalette.colorForFluidId(meter.fluidId())
                : PduTheme.TEXT_FAINT;
        final float[] rateSpark = PowerArt.tail(store, 40);

        final String[][] labels = new String[6][];
        final int[] tints = new int[6];
        final float[][] sparks = new float[6][];
        final String[][] tips = new String[6][];

        labels[0] = new String[]{"FLOW RATE", PowerFmt.compact(meter.rate()), "mB/t"};
        tints[0] = meter.flowing() ? fluidTint : PduTheme.TEXT_FAINT;
        sparks[0] = rateSpark;
        tips[0] = new String[]{"What is crossing this segment right now",
                "Create moves a pipe's contents once per tick; this is that figure",
                meter.flowing() ? (PowerFmt.compact(meter.rate() * 20.0f) + " mB per second")
                        : "Nothing is moving through this segment"};

        labels[1] = new String[]{"MEDIUM", meter.known() ? meter.fluidName() : "none", ""};
        tints[1] = fluidTint;
        sparks[1] = null;
        tips[1] = new String[]{meter.known() ? meter.fluidName() : "No fluid in this segment",
                meter.known() ? meter.fluidId() : "The meter reports the medium only while it moves"};

        final int sign = meter.sign();
        labels[2] = new String[]{"DIRECTION",
                sign > 0 ? "forward" : (sign < 0 ? "reverse" : "at rest"), ""};
        tints[2] = sign == 0 ? PduTheme.TEXT_FAINT : PduTheme.INFO;
        sparks[2] = null;
        tips[2] = new String[]{"Which way the segment is carrying",
                "Forward is along the pipe's own axis; reverse is against it",
                "A direction that keeps flipping is a network with no settled pressure gradient"};

        labels[3] = new String[]{"TOTALISED", PowerFmt.compact(meter.total()), "mB"};
        tints[3] = PduTheme.ACCENT;
        sparks[3] = null;
        tips[3] = new String[]{"Everything this meter has passed since it was placed",
                PowerFmt.volume(meter.total()),
                "Buckets: " + PowerFmt.compact(meter.total() / 1000.0f)};

        labels[4] = new String[]{"PER MINUTE", PowerFmt.compact(meter.bucketsPerMinute()), "B/min"};
        tints[4] = meter.flowing() ? PduTheme.OK : PduTheme.TEXT_FAINT;
        sparks[4] = null;
        tips[4] = new String[]{"The rate in the unit a build is actually planned in",
                "Buckets a minute at the present rate",
                "A vanilla pump run flat out is roughly this figure times the pipes feeding it"};

        final float peak = store == null ? 0.0f : store.getMax();
        labels[5] = new String[]{"WINDOW PEAK", PowerFmt.compact(peak), "mB/t"};
        tints[5] = PduTheme.WARN;
        sparks[5] = null;
        tips[5] = new String[]{"The highest rate in the recorded window",
                "An inline meter has no rating of its own: the circuits on either side set the limit,"
                        + " so its own peak is the only ceiling it can know"};

        final int n = Math.min(wide, 6);
        final int tileW = (r.w() - GAP * (n - 1)) / n;
        for (int i = 0; i < n; i++) {
            final int tx = r.x() + i * (tileW + GAP);
            final PduRect tile = new PduRect(tx, r.y(), i == n - 1 ? r.right() - tx : tileW, r.h());
            PowerArt.kpi(g, font, tile, labels[i][0], labels[i][1], labels[i][2], tints[i],
                    sparks[i], sparks[i] == null || sparks[i].length < 4 ? Float.NaN
                            : sparks[i][sparks[i].length - 1] - sparks[i][0]);
            hover.tip(tile, tips[i]);
        }
    }

    private static void pipe(GuiGraphics g, Font font, PduRect outer, PowerReadings.Meter meter,
                             FlowMeterBE live, PowerHover hover, float phase, boolean reduceMotion) {
        final PduRect r = PowerArt.cardTitled(g, font, outer, "INLINE SECTION",
                meter.known() ? meter.fluidName() : "dry", PduTheme.INFO);

        if (r.h() < 10) {
            return;
        }
        final int tint = meter.known() ? FluidPalette.colorForFluidId(meter.fluidId())
                : PduTheme.STROKE_HOT;
        final int sign = meter.sign();

        final int wallH = Math.min(r.h(), 30);
        final int py = r.y() + (r.h() - wallH) / 2;
        final int flangeW = 6;

        g.fill(r.x(), py, r.right(), py + wallH, PduTheme.BG_DEEP);
        g.fillGradient(r.x(), py, r.right(), py + 3, PowerArt.alpha(PduTheme.STROKE_HOT, 0xFF),
                PowerArt.alpha(PduTheme.STROKE, 0xFF));
        g.fillGradient(r.x(), py + wallH - 3, r.right(), py + wallH,
                PowerArt.alpha(PduTheme.STROKE, 0xFF), PowerArt.alpha(PduTheme.BG_DEEP, 0xFF));

        final int bx = r.x() + flangeW;
        final int by = py + 4;
        final int bw = Math.max(4, r.w() - flangeW * 2);
        final int bh = Math.max(4, wallH - 8);

        g.fill(bx, by, bx + bw, by + bh, PowerArt.alpha(PowerArt.scale(tint, 0.5f),
                meter.flowing() ? 0xE0 : 0x50));

        final TextureAtlasSprite sprite = meter.known()
                ? FluidPalette.stillSpriteForFluidId(meter.fluidId()) : null;
        if (sprite != null) {
            g.enableScissor(bx, by, bx + bw, by + bh);
            final float cr = ((tint >> 16) & 0xFF) / 255.0f;
            final float cg = ((tint >> 8) & 0xFF) / 255.0f;
            final float cb = (tint & 0xFF) / 255.0f;
            g.setColor(cr, cg, cb, meter.flowing() ? 1.0f : 0.55f);
            final int tile = 16;
            final float speed = reduceMotion ? 0.0f
                    : Mth.clamp(0.25f + meter.rate() * 0.05f, 0.0f, 4.0f);
            int scroll = (int) ((phase * speed * (sign < 0 ? -1 : 1)) % tile);
            if (scroll < 0) {
                scroll += tile;
            }
            for (int px = bx - tile - scroll; px < bx + bw + tile; px += tile) {
                for (int qy = by - tile; qy < by + bh + tile; qy += tile) {
                    g.blit(px, qy, 0, tile, tile, sprite);
                }
            }
            g.setColor(1.0f, 1.0f, 1.0f, 1.0f);
            g.disableScissor();
        }

        if (meter.flowing() && sign != 0) {
            g.enableScissor(bx, by, bx + bw, by + bh);
            final float speed = reduceMotion ? 0.0f : (0.6f + Mth.clamp(meter.rate() * 0.08f, 0.0f, 3.5f));
            final int spacing = 18;
            final float drift = (phase * speed * sign) % spacing;
            for (int i = -spacing; i < bw + spacing; i += spacing) {
                final int sx = bx + Math.floorMod(Math.round(i + drift), bw + spacing) - spacing / 2;
                for (int lane = 0; lane < 3; lane++) {
                    final int ly = by + 2 + lane * Math.max(2, (bh - 4) / 3);
                    final int len = 5 + lane;
                    g.fill(sx, ly, sx + len, ly + 1, PowerArt.alpha(0xFFFFFF, 0x55));
                }
            }
            g.disableScissor();
        }

        g.fill(bx, by, bx + bw, by + 1, PowerArt.alpha(PowerArt.scale(tint, 1.7f), 0xAA));
        g.fill(bx, by + 1, bx + bw, by + 3, PowerArt.alpha(0xFFFFFF, 0x12));
        PduTheme.outline(g, bx - 1, by - 1, bw + 2, bh + 2, PduTheme.STROKE);

        g.fill(r.x(), py, r.x() + flangeW, py + wallH, PduTheme.BG_RAISED);
        g.fill(r.right() - flangeW, py, r.right(), py + wallH, PduTheme.BG_RAISED);
        PduTheme.outline(g, r.x(), py, flangeW, wallH, PduTheme.STROKE);
        PduTheme.outline(g, r.right() - flangeW, py, flangeW, wallH, PduTheme.STROKE);

        if (sign != 0 && bw > 40) {
            final int cyc = py + wallH / 2;
            for (int i = 0; i < 3; i++) {
                final int ox = r.centerX() - 8 + i * 8;
                final int dir = sign;
                PowerArt.line(g, ox, cyc - 3, ox + 3 * dir, cyc, PowerArt.alpha(0xFFFFFF, 0x99));
                PowerArt.line(g, ox, cyc + 3, ox + 3 * dir, cyc, PowerArt.alpha(0xFFFFFF, 0x99));
            }
        }

        final String reading = meter.flowing()
                ? (PowerFmt.compact(meter.rate()) + " mB/t") : "no flow";
        g.drawString(font, reading, r.x() + 2, py - 1,
                meter.flowing() ? PduTheme.TEXT : PduTheme.TEXT_FAINT, false);

        hover.tip(r.x(), py, r.w(), wallH,
                "Inline section",
                meter.known() ? ("Carrying " + meter.fluidName()) : "No fluid in this segment",
                meter.flowing() ? (PowerFmt.mbt(meter.rate()) + " · "
                        + (sign > 0 ? "forward" : "reverse")) : "At rest",
                live != null && live.isTriggerEnabled()
                        ? ("Redstone armed: " + triggerText(live)) : null,
                "The block is an ordinary Create pipe segment: fitting one does not change the run");
    }

    private static void range(GuiGraphics g, Font font, PduRect outer, PowerReadings.Meter meter,
                              TimeSeriesStore store, FlowMeterBE live, PowerHover hover, float phase) {
        final PduRect r = PowerArt.cardTitled(g, font, outer, "RANGE", "against window peak",
                PduTheme.WARN);
        if (r.h() < 12) {
            return;
        }
        final float span = Math.max(1.0f, store == null ? meter.rate() : Math.max(store.getMax(), meter.rate()));
        final float fraction = Mth.clamp(meter.rate() / span, 0.0f, 1.0f);
        final int tint = meter.known() ? FluidPalette.colorForFluidId(meter.fluidId()) : PduTheme.INFO;

        final PduRect bar = new PduRect(r.x(), r.y(), r.w(), 11);
        PowerArt.bar(g, bar.x(), bar.y(), bar.w(), bar.h(), fraction, tint);
        for (int q = 1; q < 4; q++) {
            final int qx = bar.x() + bar.w() * q / 4;
            g.fill(qx, bar.y(), qx + 1, bar.bottom(), PowerArt.alpha(PduTheme.BG_DEEP, 0x99));
        }

        if (live != null && live.isTriggerEnabled() && live.getTriggerMode() == 1) {
            final float set = Mth.clamp(live.getTriggerThresholdMb() / span, 0.0f, 1.0f);
            final int sx = bar.x() + Math.round(bar.w() * set);
            PowerArt.dashedV(g, sx, bar.y() - 2, bar.bottom() + 2, PduTheme.CRIT, 2, 2);
            g.fill(sx - 2, bar.y() - 4, sx + 3, bar.y() - 1, PduTheme.CRIT);
        }

        final String left = "0";
        final String right = PowerFmt.compact(span) + " mB/t";
        g.drawString(font, left, r.x(), bar.bottom() + 2, PduTheme.TEXT_FAINT, false);
        g.drawString(font, right, r.right() - font.width(right), bar.bottom() + 2,
                PduTheme.TEXT_FAINT, false);
        final String mid = PowerFmt.pct0(fraction * 100.0f) + " of span";
        g.drawString(font, mid, r.centerX() - font.width(mid) / 2, bar.bottom() + 2,
                PduTheme.TEXT_DIM, false);

        hover.tip(r.x(), r.y() - 2, r.w(), r.h() + 2,
                "Percentage of span",
                PowerFmt.mbt(meter.rate()) + " of a " + PowerFmt.compact(span) + " mB/t span",
                "The span is the highest rate recorded in this window, not a rating",
                live != null && live.isTriggerEnabled() ? ("Alarm: " + triggerText(live)) : null);
    }

    private static String triggerText(FlowMeterBE live) {
        if (live.getTriggerMode() == 1) {
            return (live.isTriggerAbove() ? "above " : "below ")
                    + live.getTriggerThresholdMb() + " mB/t"
                    + (live.getEmittedRedstone() > 0 ? " · firing" : "");
        }
        return "while flowing" + (live.getEmittedRedstone() > 0 ? " · firing" : "");
    }

    private static void totaliser(GuiGraphics g, Font font, PduRect outer, PowerReadings.Meter meter,
                                  PowerHover hover, boolean reduceMotion) {
        final PduRect r = PowerArt.cardTitled(g, font, outer, "TOTALISER", "since placed",
                PduTheme.ACCENT);
        if (r.h() < 14) {
            return;
        }
        final int digits = r.w() >= 190 ? 9 : (r.w() >= 150 ? 8 : 6);

        final double rolling = meter.total() + (reduceMotion ? 0.0
                : (meter.rate() > 0.0f
                        ? (com.createstats.statsmk3.ui.Motion.wallMillis() % 1000L) / 1000.0 : 0.0));
        PowerArt.odometer(g, font, r.x(), r.y(), rolling, digits, PduTheme.ACCENT,
                meter.flowing() && !reduceMotion);
        final int used = digits * (font.width("0") + 5);
        g.drawString(font, "mB", r.x() + used + 3, r.y() + 3, PduTheme.TEXT_FAINT, false);

        if (r.h() >= 28) {
            final String buckets = PowerFmt.volume(meter.total());
            g.drawString(font, buckets, r.x(), r.y() + 16, PduTheme.TEXT, false);
            final String stacks = PowerFmt.compact(meter.total() / 1000.0f) + " buckets";
            if (r.w() > font.width(buckets) + font.width(stacks) + 12) {
                g.drawString(font, stacks, r.right() - font.width(stacks), r.y() + 16,
                        PduTheme.TEXT_FAINT, false);
            }
        }
        hover.tip(r.x(), r.y() - 4, r.w(), r.h() + 4,
                "Totaliser",
                PowerFmt.volume(meter.total()) + " has crossed this segment",
                "Counts from the moment the meter was placed and never resets",
                meter.flowing() ? ("Currently adding " + PowerFmt.compact(meter.rate() * 20.0f)
                        + " mB every second") : "Not counting: nothing is moving");
    }

    private static void trend(GuiGraphics g, Font font, PduRect outer, PowerReadings.Meter meter,
                              TimeSeriesStore store, PowerHover hover, float phase) {
        final PduRect r = PowerArt.cardTitled(g, font, outer, "FLOW TREND",
                "rate over time", PduTheme.INFO);
        if (r.h() < 26) {
            return;
        }
        final int axisW = 28;
        final PduRect plot = new PduRect(r.x(), r.y(), Math.max(20, r.w() - axisW),
                Math.max(14, r.h() - 11));
        PowerArt.plotFrame(g, plot, 4);

        final float[] data = PowerArt.tail(store, Math.max(8, plot.w() - 2));
        if (data.length < 2) {
            final String msg = "Gathering history";
            g.drawString(font, msg, plot.centerX() - font.width(msg) / 2, plot.centerY() - 4,
                    PduTheme.TEXT_FAINT, false);
            return;
        }
        final int tint = meter.known() ? FluidPalette.colorForFluidId(meter.fluidId()) : PduTheme.INFO;
        final float axisMax = PowerArt.niceMax(Math.max(1.0f, PowerArt.peak(data)) * 1.1f);
        PowerArt.loadProfile(g, plot, data, null, axisMax, tint, phase);

        final float mean = store.getAverage();
        if (mean > 0.0f && mean <= axisMax) {
            final int my = plot.bottom() - 1
                    - Math.round((plot.h() - 2) * Mth.clamp(mean / axisMax, 0.0f, 1.0f));
            PowerArt.dashedH(g, plot.x() + 1, plot.right() - 1, my,
                    PowerArt.alpha(PduTheme.ACCENT, 0xCC), 3, 3);
            if (plot.w() > 90) {
                g.drawString(font, "mean", plot.x() + 3, my - 9,
                        PowerArt.alpha(PduTheme.ACCENT, 0xDD), false);
            }
        }

        PowerArt.valueAxis(g, font, plot, axisMax, "mB/t");
        final int cadence = store.getSampleIntervalTicks();
        PowerArt.timeAxis(g, font, plot, Math.max(1, data.length * cadence / 20), null);

        if (hover.over(plot)) {
            final int idx = Mth.clamp(Math.round((hover.mx() - plot.x() - 1)
                    * (data.length - 1) / (float) Math.max(1, plot.w() - 2)), 0, data.length - 1);
            final int px = plot.x() + 1 + Math.round((plot.w() - 2) * (idx / (float) (data.length - 1)));
            g.fill(px, plot.y() + 1, px + 1, plot.bottom() - 1, PowerArt.alpha(PduTheme.TEXT, 0x66));
            final int agoSeconds = (data.length - 1 - idx) * cadence / 20;
            hover.set(PowerFmt.ago(agoSeconds) + " ago",
                    PowerFmt.mbt(data[idx]),
                    data[idx] <= 0.0f ? "The pipe was idle at this point" : null);
        }
    }

    private static void statistics(GuiGraphics g, Font font, PduRect outer,
                                   PowerReadings.Meter meter, TimeSeriesStore store,
                                   PowerHover hover) {
        final PduRect r = PowerArt.cardTitled(g, font, outer, "WINDOW STATISTICS",
                store == null ? "" : (store.getSampleCount() + " samples"), PduTheme.OK);
        if (r.h() < 16 || store == null || store.getSampleCount() < 2) {
            if (r.h() >= 10) {
                g.drawString(font, "Gathering samples", r.x(), r.y() + 2, PduTheme.TEXT_FAINT, false);
            }
            return;
        }
        final float[] data = store.getData();
        final float min = store.getMin();
        final float max = store.getMax();
        final float avg = store.getAverage();
        final float sd = store.getStdDev();

        int flowing = 0;
        for (final float v : data) {
            if (v > 0.0f) {
                flowing++;
            }
        }
        final float duty = flowing * 100.0f / Math.max(1, data.length);

        final float steadiness = avg <= 0.0f ? 0.0f
                : Mth.clamp(100.0f * (1.0f - Math.min(1.0f, sd / avg)), 0.0f, 100.0f);

        final int colW = r.w() >= 240 ? r.w() / 2 - 4 : r.w();
        int y = r.y();
        y = stat(g, font, r.x(), y, colW, "min", PowerFmt.compact(min) + " mB/t");
        y = stat(g, font, r.x(), y, colW, "mean", PowerFmt.compact(avg) + " mB/t");
        y = stat(g, font, r.x(), y, colW, "max", PowerFmt.compact(max) + " mB/t");
        if (r.h() >= 46) {
            y = stat(g, font, r.x(), y, colW, "spread", "±" + PowerFmt.compact(sd));
        }

        if (r.w() >= 240) {
            final int hx = r.x() + colW + 8;
            final int hw = r.right() - hx;
            int hy = r.y();
            g.drawString(font, "duty", hx, hy, PduTheme.TEXT_FAINT, false);
            final String dutyText = PowerFmt.pct0(duty);
            g.drawString(font, dutyText, r.right() - font.width(dutyText), hy, PduTheme.TEXT, false);
            PowerArt.bar(g, hx, hy + 10, hw, 4, duty / 100.0f,
                    duty > 66.0f ? PduTheme.OK : duty > 20.0f ? PduTheme.WARN : PduTheme.CRIT);
            hy += 18;
            g.drawString(font, "steady", hx, hy, PduTheme.TEXT_FAINT, false);
            final String steadyText = PowerFmt.pct0(steadiness);
            g.drawString(font, steadyText, r.right() - font.width(steadyText), hy, PduTheme.TEXT, false);
            PowerArt.bar(g, hx, hy + 10, hw, 4, steadiness / 100.0f, PowerArt.loadTint(1.0f - steadiness / 100.0f));
            hy += 18;
            if (r.bottom() - hy >= 16) {
                histogram(g, new PduRect(hx, hy, hw, r.bottom() - hy), data, max,
                        meter.known() ? FluidPalette.colorForFluidId(meter.fluidId()) : PduTheme.INFO);
            }
            hover.tip(hx, r.y() - 2, hw, r.h() + 2,
                    "Distribution",
                    "Duty: the pipe carried something in " + PowerFmt.pct0(duty)
                            + " of the recorded window",
                    "Steady: how little the rate wandered around its own mean",
                    "The bars underneath are how often each rate occurred — one tall bar is a steady"
                            + " pipe, two bars is a stop-start one");
        }
    }

    private static int stat(GuiGraphics g, Font font, int x, int y, int w, String label,
                            String value) {
        g.drawString(font, label, x, y, PduTheme.TEXT_FAINT, false);
        g.drawString(font, value, x + w - font.width(value), y, PduTheme.TEXT, false);
        return y + 11;
    }

    private static void histogram(GuiGraphics g, PduRect r, float[] data, float max, int tint) {
        final int bins = Math.max(4, Math.min(16, r.w() / 5));
        final int[] counts = new int[bins];
        final float span = Math.max(0.0001f, max);
        int peak = 1;
        for (final float v : data) {
            final int b = Mth.clamp((int) (v / span * (bins - 1)), 0, bins - 1);
            counts[b]++;
            peak = Math.max(peak, counts[b]);
        }
        g.fill(r.x(), r.y(), r.right(), r.bottom(), PduTheme.BG_DEEP);
        final int barW = Math.max(1, r.w() / bins);
        for (int i = 0; i < bins; i++) {
            final int h = Math.round((r.h() - 2) * counts[i] / (float) peak);
            if (h <= 0) {
                continue;
            }
            final int bx = r.x() + i * barW;
            g.fillGradient(bx, r.bottom() - 1 - h, bx + Math.max(1, barW - 1), r.bottom() - 1,
                    PowerArt.alpha(tint, 0xEE), PowerArt.alpha(tint, 0x60));
        }
        PduTheme.outline(g, r.x(), r.y(), r.w(), r.h(), PowerArt.alpha(PduTheme.STROKE, 0xCC));
    }

    private static void diagnosis(GuiGraphics g, Font font, int x, int y, int w,
                                  PowerReadings.Meter meter, FlowMeterBE live) {
        final boolean armed = live != null && live.isTriggerEnabled();
        final boolean firing = live != null && live.getEmittedRedstone() > 0;
        final int tint = firing ? PduTheme.ACCENT : (meter.flowing() ? PduTheme.OK : PduTheme.TEXT_FAINT);
        g.fill(x, y + 2, x + 3, y + 8, tint);
        final String text;
        if (!meter.flowing()) {
            text = armed
                    ? ("Idle · redstone armed " + triggerText(live))
                    : "Idle — nothing is moving through this segment";
        } else {
            // don't name a fluid we haven't identified

            final String what = meter.known() ? meter.fluidName() : "fluid";
            final String base = "Carrying " + what + " at " + PowerFmt.mbt(meter.rate())
                    + " · " + PowerFmt.compact(meter.bucketsPerMinute()) + " B/min";
            text = armed ? (base + " · alarm " + triggerText(live)) : base;
        }
        g.drawString(font, fit(font, text, w - 8), x + 7, y + 1, tint, false);
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

    private static FlowMeterBE liveMeter(BlockPos pos) {
        if (pos == null) {
            return null;
        }
        final Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null || !mc.level.isLoaded(pos)) {
            return null;
        }
        final BlockEntity be = mc.level.getBlockEntity(pos);
        return be instanceof FlowMeterBE meter ? meter : null;
    }
}
