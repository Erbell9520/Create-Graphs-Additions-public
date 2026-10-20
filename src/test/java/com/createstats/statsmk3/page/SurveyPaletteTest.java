package com.createstats.statsmk3.page;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.createstats.seismic.SeismicCatalog;
import com.createstats.statsmk3.ui.Tokens;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SurveyPaletteTest {
    static List<Entry> legend() {
        final List<String> targets = new ArrayList<>(List.of("c:ores/coal", "c:ores/iron", "c:ores/copper",
                "c:ores/gold", "c:ores/redstone", "c:ores/lapis", "c:ores/diamond", "c:ores/emerald", "c:ores/quartz",
                "c:ores/netherite_scrap", SeismicCatalog.VOIDS, SeismicCatalog.LIQUIDS));
        targets.sort((a, b) -> SeismicCatalog.compare(b, a));
        final List<Entry> out = new ArrayList<>();
        for (final String t : targets) {
            if (SeismicCatalog.LIQUIDS.equals(t)) {
                out.add(new Entry("lava", SurveyKit.colour(t, SeismicCatalog.KIND_LAVA), false));
                out.add(new Entry("water", SurveyKit.colour(t, SeismicCatalog.KIND_WATER), false));
            } else {
                out.add(new Entry(SeismicCatalog.veinLabel(t, 0).toLowerCase(Locale.ROOT), SurveyKit.colour(t, 0),
                        SeismicCatalog.isOre(t)));
            }
        }
        return out;
    }

    record Entry(String name, int argb, boolean ore) {
    }

    @Test
    void everyVeinReadsOnTheSurfaceAndTheFace() {
        final List<String> failures = new ArrayList<>();
        for (final Entry e : legend()) {
            final double page = contrast(e.argb, Tokens.SURFACE);
            final double face = contrast(e.argb, 0xFF05070A);
            if (page < 3.0 || face < 3.0) {
                failures.add(String.format(Locale.ROOT, "  %s: %.2f on the page, %.2f on a face", e.name, page, face));
            }
        }
        assertTrue(failures.isEmpty(), "under 3:1:\n" + String.join("\n", failures));
    }

    @Test
    void adjacentLegendEntriesStayApart() {
        final List<Entry> l = legend();
        final StringBuilder report = new StringBuilder("legend neighbours (OKLab x100, normal / deutan):\n");
        final List<String> failures = new ArrayList<>();
        for (int i = 1; i < l.size(); i++) {
            final Entry a = l.get(i - 1);
            final Entry b = l.get(i);
            final double normal = deltaE(oklab(a.argb), oklab(b.argb));
            final double deutan = deltaE(oklab(deutan(a.argb)), oklab(deutan(b.argb)));
            report.append(String.format(Locale.ROOT, "  %-8s %-8s %5.1f / %5.1f%s%n", a.name, b.name, normal, deutan,
                    a.ore == b.ore ? "" : "  (different forms)"));

            if (a.ore == b.ore && (normal < 18.5 || deutan < 8.0)) {
                failures.add(String.format(Locale.ROOT, "  %s / %s: %.1f / %.1f", a.name, b.name, normal, deutan));
            }

            if (a.ore != b.ore && (normal < 15.0 || deutan < 12.0)) {
                failures.add(String.format(Locale.ROOT, "  %s / %s (forms differ): %.1f / %.1f", a.name, b.name, normal,
                        deutan));
            }
        }
        System.out.println(report);
        assertTrue(failures.isEmpty(), "legend neighbours too close:\n" + String.join("\n", failures));
    }

    @Test
    void anyTwoTargetsOfOneFormStayApart() {
        final List<Entry> l = legend();
        final java.util.Set<String> accepted = java.util.Set.of("iron/emerald", "redstone/copper", "debris/redstone",
                "iron/coal", "debris/copper", "emerald/copper", "diamond/quartz", "iron/copper");
        final List<String> failures = new ArrayList<>();
        final StringBuilder report = new StringBuilder("pairs of one form under the deutan floor (normal / deutan):" + System.lineSeparator());
        for (int i = 0; i < l.size(); i++) {
            for (int j = i + 1; j < l.size(); j++) {
                final Entry a = l.get(i);
                final Entry b = l.get(j);
                if (a.ore != b.ore) {
                    continue;
                }
                final double normal = deltaE(oklab(a.argb), oklab(b.argb));
                final double deutan = deltaE(oklab(deutan(a.argb)), oklab(deutan(b.argb)));
                if (normal < 7.4) {
                    failures.add(String.format(Locale.ROOT, "  %s / %s: %.1f normal", a.name, b.name, normal));
                }
                if (deutan < 9.2) {
                    report.append(String.format(Locale.ROOT, "  %-8s %-8s %5.1f / %5.1f%n", a.name, b.name, normal,
                            deutan));
                    if (!accepted.contains(a.name + "/" + b.name) && !accepted.contains(b.name + "/" + a.name)) {
                        failures.add(String.format(Locale.ROOT, "  %s / %s: %.1f deutan, not a known pair", a.name,
                                b.name, deutan));
                    }
                }
            }
        }
        System.out.println(report);
        assertTrue(failures.isEmpty(), "targets of one form too close: " + String.join(System.lineSeparator(), failures));
    }

    @Test
    void voidsAreAHazeNotACyan() {
        final int diamond = SurveyKit.colour("c:ores/diamond", 0);
        final int voids = SurveyKit.colour(SeismicCatalog.VOIDS, 0);
        assertTrue(deltaE(oklab(diamond), oklab(voids)) >= 12.0, "diamond and voids too close");
        final double[] lab = oklab(voids);
        assertTrue(Math.hypot(lab[1], lab[2]) < 0.03, "a haze has almost no hue");
    }

    static double lin(int c) {
        final double v = c / 255.0;
        return v <= 0.04045 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
    }

    static double[] linear(int argb) {
        return new double[] {lin((argb >> 16) & 0xFF), lin((argb >> 8) & 0xFF), lin(argb & 0xFF)};
    }

    static double[] oklab(double[] rgb) {
        final double l = Math.cbrt(0.4122214708 * rgb[0] + 0.5363325363 * rgb[1] + 0.0514459929 * rgb[2]);
        final double m = Math.cbrt(0.2119034982 * rgb[0] + 0.6806995451 * rgb[1] + 0.1073969566 * rgb[2]);
        final double s = Math.cbrt(0.0883024619 * rgb[0] + 0.2817188376 * rgb[1] + 0.6299787005 * rgb[2]);
        return new double[] {0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s,
                1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s,
                0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s};
    }

    static double[] oklab(int argb) {
        return oklab(linear(argb));
    }

    static double[] deutan(int argb) {
        final double[] c = linear(argb);
        final double[][] m = {{0.367322, 0.860646, -0.227968}, {0.280085, 0.672501, 0.047413},
                {-0.011820, 0.042940, 0.968881}};
        final double[] out = new double[3];
        for (int i = 0; i < 3; i++) {
            out[i] = Math.max(0.0, Math.min(1.0, m[i][0] * c[0] + m[i][1] * c[1] + m[i][2] * c[2]));
        }
        return out;
    }

    static double deltaE(double[] a, double[] b) {
        return 100.0 * Math.sqrt((a[0] - b[0]) * (a[0] - b[0]) + (a[1] - b[1]) * (a[1] - b[1])
                + (a[2] - b[2]) * (a[2] - b[2]));
    }

    static double contrast(int a, int b) {
        final double la = luminance(a);
        final double lb = luminance(b);
        return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
    }

    static double luminance(int argb) {
        final double[] c = linear(argb);
        return 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2];
    }
}
