package com.createstats.inventory.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChartPaperLayoutTest {
    private static ChartHistory steady(int n, int lastStamp) {
        final ChartHistory h = new ChartHistory();
        for (int i = 0; i < n; i++) {
            h.record(1000 + i, lastStamp - (n - 1 - i), i);
        }
        return h;
    }

    @Test
    void theNewestSampleIsTheLastColumnAtBothWidths() {
        for (int days : new int[]{1, 3}) {
            for (int width : new int[]{138, 310}) {
                final ChartHistory h = steady(ChartHistory.CAPACITY, 5000);
                final ChartPaperLayout p = ChartPaperLayout.of(h, width, days);
                assertEquals(width, p.columns().size());
                assertEquals(h.size() - 1, p.columns().get(width - 1).sample(), days + " days at " + width);
                assertEquals(ChartPaperLayout.spanIndex(width - 1, width, days * 120), days * 120 - 1);
            }
        }
    }

    @Test
    void aYoungRecorderLeavesTheOldPaperBlank() {
        final ChartHistory h = steady(30, 600);
        final ChartPaperLayout p = ChartPaperLayout.of(h, 138, 1);
        assertTrue(p.columns().get(0).blank(), "paper older than the recorder");
        assertFalse(p.columns().get(137).blank());
        assertEquals(29, p.columns().get(137).sample());
    }

    @Test
    void aSleptNightIsOneSkipAndNoRulesInsideIt() {
        final ChartHistory h = new ChartHistory();

        int period = 0;
        for (int s = 39 * 120; s <= 39 * 120 + 68; s++) {
            h.record(period++, s, 1);
        }
        for (int s = 40 * 120; s <= 40 * 120 + 50; s++) {
            h.record(period++, s, 1);
        }
        final ChartPaperLayout p = ChartPaperLayout.of(h, 138, 1);
        assertEquals(1, p.skips().size(), "one double rule for the night slept through");
        final int skip = p.skips().get(0);
        for (ChartPaperLayout.Rule r : p.rules()) {
            assertTrue(r.x() != skip, "no hour rule on the jump");
        }

        assertTrue(p.rules().stream().noneMatch(r -> r.hour() == 0 || r.hour() == 22));
    }

    @Test
    void theDaylightCycleOffPrintsNoRules() {
        final ChartHistory h = new ChartHistory();
        for (int i = 0; i < 120; i++) {
            h.record(i, 777, i);
        }
        final ChartPaperLayout p = ChartPaperLayout.of(h, 138, 1);
        assertTrue(p.rules().isEmpty());
        assertTrue(p.skips().isEmpty());
        assertTrue(p.labels().isEmpty());
    }

    @Test
    void labelsNeverOverlapOrLeaveThePaper() {
        for (int width : new int[]{138, 310}) {
            for (int days : new int[]{1, 3}) {
                for (int last = 0; last < 240; last += 7) {
                    final ChartPaperLayout p = ChartPaperLayout.of(steady(ChartHistory.CAPACITY, 5000 + last), width, days);
                    final List<ChartPaperLayout.Label> labels = p.labels();
                    for (int i = 0; i < labels.size(); i++) {
                        final ChartPaperLayout.Label a = labels.get(i);
                        final int aw = FaceFormat.glassWidth(a.text(), 1);
                        assertTrue(a.x() >= 1 && a.x() + aw <= width - 1, a + " leaves a paper of " + width);
                        for (int j = i + 1; j < labels.size(); j++) {
                            final ChartPaperLayout.Label b = labels.get(j);
                            final int bw = FaceFormat.glassWidth(b.text(), 1);
                            assertTrue(a.x() + aw + 3 <= b.x() || b.x() + bw + 3 <= a.x(), a + " touches " + b);
                        }
                    }
                }
            }
        }
    }

    @Test
    void oneDayLabelsTheHeavyHoursAndThreeDaysLabelTheDays() {
        final ChartPaperLayout one = ChartPaperLayout.of(steady(ChartHistory.CAPACITY, 41 * 120 + 48), 138, 1);
        assertFalse(one.labels().isEmpty());
        for (ChartPaperLayout.Label l : one.labels()) {
            assertTrue(l.text().matches("0|6|12|18"), l.text());
        }
        final ChartPaperLayout three = ChartPaperLayout.of(steady(ChartHistory.CAPACITY, 41 * 120 + 48), 310, 3);
        assertTrue(three.labels().stream().anyMatch(l -> l.text().equals("DAY 40")));
        assertTrue(three.rules().stream().allMatch(ChartPaperLayout.Rule::major), "three days: heavy rules only");
        assertTrue(three.rules().stream().anyMatch(ChartPaperLayout.Rule::dayRule), "06:00 marks each day");
    }

    @Test
    void nightIsShadedWhereTheStampSaysItFell() {
        final ChartPaperLayout p = ChartPaperLayout.of(steady(ChartHistory.CAPACITY, 41 * 120 + 119), 138, 1);

        assertTrue(p.columns().get(100).night());
        assertFalse(p.columns().get(10).night());
    }
}
