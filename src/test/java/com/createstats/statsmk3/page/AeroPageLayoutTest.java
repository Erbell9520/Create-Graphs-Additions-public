package com.createstats.statsmk3.page;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.createstats.aerial.AerialAlerts;
import com.createstats.aerial.AerialFixtures;
import com.createstats.aerial.AerialFixtures.Scene;
import com.createstats.aerial.AerialFrame;
import com.createstats.aerial.AerialRealistic;
import com.createstats.aerial.AerialSnapshot;
import com.createstats.aerial.AirframeMath;
import com.createstats.aerial.AirframeReadout;
import com.createstats.statsmk3.CountingSurface;
import com.createstats.statsmk3.ui.PanelSurface;
import com.createstats.statsmk3.ui.Rect;
import com.createstats.statsmk3.ui.Text;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AeroPageLayoutTest {
    private static final int[][] SCREENS = {{640, 360}, {853, 480}, {960, 540}};

    static List<AerialFrame> ships() {
        final List<AerialFrame> out = new ArrayList<>();
        for (final AerialSnapshot s : new AerialSnapshot[]{AerialFixtures.cruise(), AerialFixtures.sinking(),
                AerialFixtures.docking(), AerialFixtures.full(Scene.CRUISE), AerialFixtures.full(Scene.SINKING),
                AerialFixtures.full(Scene.MOORED), AerialRealistic.scene(Scene.CRUISE), AerialRealistic.scene(Scene.SINKING),
                AerialRealistic.scene(Scene.MOORED), AerialFixtures.anchored(AerialRealistic.scene(Scene.CRUISE)),
                AerialFixtures.weighing(AerialRealistic.scene(Scene.MOORED))}) {
            out.add(AerialFrame.of(s));
        }
        final AirframeReadout r = AirframeMath.read(AerialFixtures.full(Scene.CRUISE));
        out.add(new AerialFrame(r, AerialAlerts.evaluate(r, List.of(), 0.0), 0.0, 0.3));
        return out;
    }

    @Test
    void noWordsSitOnOtherWords() {
        final List<AerialFrame> ships = ships();
        final List<String> failures = new ArrayList<>();
        for (final int[] screen : SCREENS) {
            final Rect area = AeroPreviewTest.pageArea(screen[0], screen[1]);
            for (int tab = 0; tab < AeroBridge.TABS.length; tab++) {
                for (int i = 0; i < ships.size(); i++) {
                    final TextBoxes s = new TextBoxes();
                    AeroView.paintPage(s, area, ships.get(i), tab, AeroSkin.DECK);
                    final String clash = s.firstOverlap();
                    if (clash != null) {
                        failures.add(String.format(Locale.ROOT, "  %dx%d / %s / ship %d: %s", (int) area.w, (int) area.h,
                                AeroBridge.TABS[tab], i, clash));
                    }
                }
            }
        }
        assertTrue(failures.isEmpty(), failures.size() + " places where words sit on words:\n"
                + String.join("\n", failures.subList(0, Math.min(40, failures.size()))));
    }

    static final class TextBoxes implements PanelSurface {
        private final List<float[]> boxes = new ArrayList<>();
        private final List<String> words = new ArrayList<>();

        @Override
        public void tri(float x1, float y1, int c1, float x2, float y2, int c2, float x3, float y3, int c3) {
        }

        @Override
        public void text(String s, float x, float y, int color, float scale) {
            if (s == null || s.isBlank() || (color >>> 24) == 0) {
                return;
            }
            this.boxes.add(new float[]{x, y, x + textWidth(s, scale), y + Text.lineHeight(scale)});
            this.words.add(s);
        }

        @Override
        public void textCenter(String s, float centerX, float y, int color, float scale) {
            text(s, centerX - textWidth(s, scale) * 0.5f, y, color, scale);
        }

        @Override
        public void textRight(String s, float rightX, float y, int color, float scale) {
            text(s, rightX - textWidth(s, scale), y, color, scale);
        }

        @Override
        public float textWidth(String s, float scale) {
            return CountingSurface.advance(s, scale);
        }

        @Override
        public void clip(Rect r) {
        }

        @Override
        public void unclip() {
        }

        String firstOverlap() {
            for (int i = 0; i < this.boxes.size(); i++) {
                final float[] a = this.boxes.get(i);
                for (int j = i + 1; j < this.boxes.size(); j++) {
                    final float[] b = this.boxes.get(j);
                    final float ox = Math.min(a[2], b[2]) - Math.max(a[0], b[0]);
                    final float oy = Math.min(a[3], b[3]) - Math.max(a[1], b[1]);
                    if (ox > 1.0f && oy > 1.5f) {
                        return "\"" + this.words.get(i) + "\" and \"" + this.words.get(j) + "\"";
                    }
                }
            }
            return null;
        }
    }
}
