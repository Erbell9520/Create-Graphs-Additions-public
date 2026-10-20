package com.createstats.aviatorhelmet;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AviatorHudLayoutTest {
    private static final int[][] SCREENS = {
        {427, 240}, {640, 360}, {640, 360}, {480, 270}, {640, 360}, {853, 533}, {640, 400}, {960, 540},
    };

    @Test
    void theMocksScreenIsTheMock() {
        final AviatorHudLayout at = AviatorHudLayout.of(853, 533);
        assertEquals(100, at.compassHalf, "200 wide, as the mock");
        assertEquals(46, at.tapeX);
        assertEquals(533 / 2 - 10, at.tapeMid);
        assertEquals(90, at.tapeHalf, "sixty blocks of tape");
        assertEquals(96, at.statoscopeX);
        assertEquals(853 - 78, at.telegraphX);
        assertEquals(200, at.telegraphY);
        assertEquals(440, at.bannerWidth);
        assertEquals(533 - 58, at.bannerTop(1));
    }

    @Test
    void everythingFitsAndNothingOverlaps() {
        final List<String> problems = new ArrayList<>();
        for (int[] s : SCREENS) {
            for (int lines = 1; lines <= 2; lines++) {
                final AviatorHudLayout at = AviatorHudLayout.of(s[0], s[1]);
                final Map<String, int[]> boxes = at.boxes(lines);
                for (Map.Entry<String, int[]> e : boxes.entrySet()) {
                    final int[] b = e.getValue();
                    if (b[0] < 0 || b[1] < 0 || b[2] > s[0] || b[3] > s[1]) {
                        problems.add(s[0] + "x" + s[1] + " " + e.getKey() + " off the screen: " + java.util.Arrays.toString(b));
                    }
                }
                final List<Map.Entry<String, int[]>> list = new ArrayList<>(boxes.entrySet());
                for (int i = 0; i < list.size(); i++) {
                    for (int j = i + 1; j < list.size(); j++) {
                        final int[] a = list.get(i).getValue();
                        final int[] b = list.get(j).getValue();
                        if (a[0] < b[2] && b[0] < a[2] && a[1] < b[3] && b[1] < a[3]) {
                            problems.add(s[0] + "x" + s[1] + " (" + lines + " banner lines) " + list.get(i).getKey()
                                    + " overlaps " + list.get(j).getKey());
                        }
                    }
                }
            }
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    @Test
    void theCompassMovesDownUnderWhatHoldsTheTop() {
        final AviatorHudLayout at = AviatorHudLayout.of(853, 533, 60);
        assertEquals(60, at.compassTop);
        assertEquals(14, AviatorHudLayout.of(853, 533, 3).compassTop, "never above its own place");
    }
}
