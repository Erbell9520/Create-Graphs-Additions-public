package com.createstats.gui.battery;

import com.createstats.gui.pdu.PduRect;
import com.createstats.power.BatteryControllerPlan;
import com.createstats.power.BatteryOutputMode;
import com.createstats.power.FeBoardDeal;
import com.createstats.power.FePriority;
import com.createstats.power.FeReserveBand;
import com.createstats.power.FeReserveReport;
import com.createstats.power.ReserveProjection;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.fail;

class BatteryScreenPreviewTest {
    private static final Path OUT = Path.of("build/battery-previews");
    private static final int[][] SIZES = {{640, 400}, {560, 360}, {500, 330}};

    private static final List<String> PROBLEMS = new ArrayList<>();

    private static BatteryView.Output unwired(int i) {
        return new BatteryView.Output(i, false, true, BatteryOutputMode.DIRECT, 0, 0, FePriority.NORMAL,
                BatteryControllerPlan.Reason.UNWIRED, 0, 0, 0, 0, 0, 0, 0, 0, FeBoardDeal.Reason.GRANTED, 0, 0);
    }

    static BatteryView save() {
        final List<BatteryControllerPlan.Output> plan = List.of(
                new BatteryControllerPlan.Output(false, true, BatteryOutputMode.DIRECT, 0, 0, 0, 0, FePriority.NORMAL, false),
                new BatteryControllerPlan.Output(false, true, BatteryOutputMode.DIRECT, 0, 0, 0, 0, FePriority.NORMAL, false),
                new BatteryControllerPlan.Output(true, true, BatteryOutputMode.DIRECT, 10_291, 32_768, 0, 0,
                        FePriority.NORMAL, false, true, 0));
        final ReserveProjection.Result outlook = ReserveProjection.project(new ReserveProjection.Setup(82_348_375,
                82_401_116, 8_418, plan, FeReserveBand.Band.DEFAULT, 1_200, 98_304, 98_304, 60, 32_768, true, 0, 0));
        return new BatteryView(82_348_375, 82_401_116, 8_418, 10_291, 1_873, 0, -1_808, 0,
                FeReserveReport.Stance.SURPLUS, 10, 30, 60, 10, 0, 0, 96_705, 96_705, 60, 1, 10_485_760, 10_485_760,
                0, 0, 0, false,
                List.of(new BatteryView.Input(0, true, 0, 1, 0, 0, 0, false, 1_873),
                        new BatteryView.Input(1, true, 1_620, 0, 9, 0, 0, false, 0),
                        new BatteryView.Input(2, true, 6_798, 0, 308, 0, 0, false, 0)),
                List.of(unwired(0), unwired(1),
                        new BatteryView.Output(2, true, true, BatteryOutputMode.DIRECT, 0, 0, FePriority.NORMAL,
                                BatteryControllerPlan.Reason.SERVED, 10_291, 10_291, 32_768, 1, 1, 0, 10_291, 10_291,
                                FeBoardDeal.Reason.GRANTED, 0, 0)),
                outlook);
    }

    static BatteryView trouble() {
        final List<BatteryControllerPlan.Output> plan = List.of(
                new BatteryControllerPlan.Output(true, true, BatteryOutputMode.DIRECT, 2_400, 32_768, 0, 0, FePriority.HIGH, false),
                new BatteryControllerPlan.Output(true, true, BatteryOutputMode.LIMIT, 3_000, 32_768, 2_048, 40, FePriority.LOW, true),
                new BatteryControllerPlan.Output(true, true, BatteryOutputMode.DIRECT, 9_000, 16_384, 0, 0,
                        FePriority.NORMAL, false, true, 1_500));
        final ReserveProjection.Result outlook = ReserveProjection.project(new ReserveProjection.Setup(4_600_000,
                20_000_000, 9_000, plan, new FeReserveBand.Band(18, 30, 60), 1_200, 98_304, 98_304, 60, 32_768, true,
                6_000_000, 3_072));
        return new BatteryView(4_600_000, 20_000_000, 9_000, 8_931, 0, 0, 1_320, 2_130,
                FeReserveReport.Stance.CONSERVE, 18, 30, 60, 10, 18, 2_400_000, 383, 3_833, 60, 2, 14_971_520,
                20_971_520, 3_072, 3_072, 3_133, false,
                List.of(new BatteryView.Input(0, true, 0, 4, 0, 1, 1_536, false, 1_536),
                        new BatteryView.Input(1, true, 9_000, 0, 12, 0, 0, false, 0),
                        new BatteryView.Input(2, false, 0, 0, 3, 0, 0, false, 0)),
                List.of(new BatteryView.Output(0, true, true, BatteryOutputMode.DIRECT, 0, 0, FePriority.HIGH,
                                BatteryControllerPlan.Reason.SERVED, 2_400, 2_400, 32_768, 14, 0, 0, 0, 0,
                                FeBoardDeal.Reason.GRANTED, 0, 0),
                        new BatteryView.Output(1, true, true, BatteryOutputMode.LIMIT, 2_048, 40, FePriority.LOW,
                                BatteryControllerPlan.Reason.FLOOR, 0, 3_000, 32_768, 6, 0, 0, 0, 0,
                                FeBoardDeal.Reason.GRANTED, 0, 0),
                        new BatteryView.Output(2, true, true, BatteryOutputMode.DIRECT, 0, 0, FePriority.NORMAL,
                                BatteryControllerPlan.Reason.PARTIAL, 6_531, 16_384, 16_384, 1, 1, 1_500, 9_000, 6_531,
                                FeBoardDeal.Reason.CHARGING, 1, 1_536)),

                outlook, 1_500);
    }

    static BatteryView dusk() {
        final long dayTime = 10_000;
        final java.util.function.IntUnaryOperator roof = ahead -> com.createstats.power.SolarCurve
                .fePerTickIn(6_000, 1, dayTime, ahead, com.createstats.power.SolarCurve.Sky.CLEAR, 1.0, true);
        final int solarNow = roof.applyAsInt(0);
        final com.createstats.power.SupplyForecast.Result supply = new com.createstats.power.SupplyForecast.Builder(
                3, ReserveProjection.HORIZON_TICKS)
                .port(0, solarNow, 16_384)
                .add(0, com.createstats.power.SupplyForecast.Kind.SUN, roof)
                .port(1, 900, 8_192)
                .add(1, com.createstats.power.SupplyForecast.Kind.FUEL,
                        ahead -> com.createstats.power.BurnerFuelOutlook.outputIn(360, 9_600, false, ahead))
                .add(1, com.createstats.power.SupplyForecast.Kind.STEADY, ahead -> 540)
                .build();
        final List<BatteryControllerPlan.Output> plan = List.of(
                new BatteryControllerPlan.Output(true, true, BatteryOutputMode.DIRECT, 3_500, 32_768, 0, 0,
                        FePriority.NORMAL, false),
                new BatteryControllerPlan.Output(false, true, BatteryOutputMode.DIRECT, 0, 0, 0, 0, FePriority.NORMAL, false),
                new BatteryControllerPlan.Output(false, true, BatteryOutputMode.DIRECT, 0, 0, 0, 0, FePriority.NORMAL, false));
        final ReserveProjection.Result outlook = ReserveProjection.project(new ReserveProjection.Setup(11_000_000,
                20_000_000, solarNow + 900, plan, FeReserveBand.Band.DEFAULT, 1_200, 98_304, 98_304, 60, 32_768, true,
                0, 0, supply));
        return new BatteryView(11_000_000, 20_000_000, solarNow + 900, 3_500, 0, 400, 400, 0,
                FeReserveReport.Stance.NOMINAL, 10, 30, 60, 10, 0, 0, 5_000, 5_000, 60, 0, 0, 0, 0, 0, 0, false,
                List.of(new BatteryView.Input(0, true, solarNow, 0, 40, 0, 0, false, 0),
                        new BatteryView.Input(1, true, 900, 0, 3, 0, 0, false, 0),
                        new BatteryView.Input(2, true, 0, 6, 0, 0, 0, false, 400)),
                List.of(new BatteryView.Output(0, true, true, BatteryOutputMode.DIRECT, 0, 0, FePriority.NORMAL,
                                BatteryControllerPlan.Reason.SERVED, 3_500, 3_500, 32_768, 12, 0, 0, 0, 0,
                                FeBoardDeal.Reason.GRANTED, 0, 0), unwired(1), unwired(2)),
                outlook);
    }

    static BatteryView noBanks() {
        return new BatteryView(0, 0, 2_000, 1_500, 0, 0, 0, 0, FeReserveReport.Stance.CONSERVE, 10, 30, 60, 10, 0, 0,
                0, 0, 60, 0, 0, 0, 0, 0, 0, false,
                List.of(new BatteryView.Input(0, true, 2_000, 0, 2, 0, 0, false, 0),
                        new BatteryView.Input(1, true, 0, 0, 0, 0, 0, true, 0),
                        new BatteryView.Input(2, true, 0, 0, 0, 0, 0, false, 0)),
                List.of(new BatteryView.Output(0, true, true, BatteryOutputMode.DIRECT, 0, 0, FePriority.NORMAL,
                                BatteryControllerPlan.Reason.SERVED, 1_500, 1_500, 8_192, 3, 0, 0, 0, 0,
                                FeBoardDeal.Reason.GRANTED, 0, 0), unwired(1), unwired(2)),
                ReserveProjection.Result.NONE);
    }

    private static BatteryHistory history(int from, int to) {
        final BatteryHistory h = new BatteryHistory();
        for (int i = 0; i < 120; i++) {
            h.record(from + (to - from) * i / 119, 8_000 + (i % 7) * 100, 9_500);
        }
        return h;
    }

    private static PngCanvas render(BatteryView v, BatteryPages.Tab tab, int selected, int w, int h, String name)
            throws Exception {
        final PngCanvas c = PngCanvas.open(w + 16, h + 16);
        if (c == null) {
            return null;
        }
        final List<BatteryPages.Hit> hits = new ArrayList<>();
        final PduRect panel = new PduRect(8, 8, w, h);
        BatteryPages.draw(c, panel, v, new BatteryPages.State(tab, selected, -1, -1,
                history(v.socPct() + 3, v.socPct()), 1, 32_768, hits));
        c.writeScaled(OUT.resolve(name + ".png"), 2);
        check(c, panel, hits, name);
        return c;
    }

    private static void check(PngCanvas c, PduRect panel, List<BatteryPages.Hit> hits, String name) {
        for (PngCanvas.TextRun run : c.runs) {
            if (run.x() < panel.x() || run.x() + run.w() > panel.right() || run.y() < panel.y()
                    || run.y() + 8 > panel.bottom()) {
                PROBLEMS.add(name + ": \"" + run.text() + "\" leaves the panel at " + run.x() + "," + run.y());
            }
        }
        for (int i = 0; i < c.runs.size(); i++) {
            final PngCanvas.TextRun a = c.runs.get(i);
            for (int j = i + 1; j < c.runs.size(); j++) {
                final PngCanvas.TextRun b = c.runs.get(j);
                final boolean rows = a.y() < b.y() + 8 && b.y() < a.y() + 8;
                final boolean cols = a.x() < b.x() + b.w() - 1 && b.x() < a.x() + a.w() - 1;
                if (rows && cols && !a.text().isEmpty() && !b.text().isEmpty()) {
                    PROBLEMS.add(name + ": \"" + a.text() + "\" at " + a.x() + "," + a.y() + " overlaps \"" + b.text()
                            + "\" at " + b.x() + "," + b.y());
                }
            }
        }
        for (BatteryPages.Hit hit : hits) {
            if (!(hit.r().x() >= panel.x() - 3 && hit.r().right() <= panel.right() + 3
                    && hit.r().bottom() <= panel.bottom())) {
                PROBLEMS.add(name + ": a control outside the panel " + hit);
            }
        }
    }

    @Test
    @DisplayName("every page, every size, three controllers: nothing leaves the panel or overlaps")
    void everyPageFits() throws Exception {
        PROBLEMS.clear();
        boolean any = false;
        for (int[] size : SIZES) {
            final String tag = size[0] + "x" + size[1];
            for (BatteryPages.Tab tab : BatteryPages.Tab.values()) {
                any |= render(save(), tab, -1, size[0], size[1], "save-" + tab.name().toLowerCase() + "-" + tag) != null;
                render(trouble(), tab, -1, size[0], size[1], "trouble-" + tab.name().toLowerCase() + "-" + tag);
                render(noBanks(), tab, -1, size[0], size[1], "nobanks-" + tab.name().toLowerCase() + "-" + tag);
                render(dusk(), tab, -1, size[0], size[1], "dusk-" + tab.name().toLowerCase() + "-" + tag);
            }
            render(trouble(), BatteryPages.Tab.NOW, 1, size[0], size[1], "trouble-now-selected-" + tag);
            render(save(), BatteryPages.Tab.NOW, 2, size[0], size[1], "save-now-selected-" + tag);
        }
        Assumptions.assumeTrue(any, "no Minecraft client jar to read the font from");
        if (!PROBLEMS.isEmpty()) {
            fail(PROBLEMS.size() + " layout problem(s):\n" + String.join("\n", PROBLEMS));
        }
    }
}
