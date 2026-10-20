package com.createstats.seismic.archive;

import com.createstats.seismic.SeismicCatalog;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class SurveyFinds {
    private static final String[] POINTS16 = {"N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE", "S", "SSW", "SW", "WSW",
            "W", "WNW", "NW", "NNW"};

    private SurveyFinds() {
    }

    public record Find(SurveyScene.Body body, int x, int y, int z, double horiz, double bearing) {
        public String where() {
            if (horiz < 1.5) {
                return String.format(Locale.ROOT, "below, Y %d", y);
            }
            return String.format(Locale.ROOT, "%d m %s, Y %d", Math.round(horiz), compass(bearing), y);
        }
    }

    public static double bearing(double fromX, double fromZ, double toX, double toZ) {
        final double b = Math.toDegrees(Math.atan2(toX - fromX, -(toZ - fromZ)));
        return b < 0.0 ? b + 360.0 : b;
    }

    public static String compass(double bearing) {
        final double b = ScanCamera.wrap(bearing);
        return POINTS16[(int) Math.floor((b + 11.25) / 22.5) % 16];
    }

    public static Find nearest(SurveyScene scene, String target, int kind, double fromX, double fromZ) {
        final SurveyScene.Layer layer = scene.layer(target);
        if (layer == null) {
            return null;
        }
        Find best = null;
        for (final SurveyScene.Body b : layer.bodies) {
            if (kind >= 0 && b.kind != kind) {
                continue;
            }
            final Find f = place(b, b.nearX, b.nearY, b.nearZ, fromX, fromZ);
            if (best == null || closer(f, best) < 0) {
                best = f;
            }
        }
        return best;
    }

    public static int closer(Find a, Find b) {
        final int byGround = Double.compare(a.horiz(), b.horiz());
        return byGround != 0 ? byGround : Float.compare(a.body().nearDist, b.body().nearDist);
    }

    public static String station(String target) {
        if (SeismicCatalog.VOIDS.equals(target)) {
            return "Voids";
        }
        if (SeismicCatalog.LIQUIDS.equals(target)) {
            return "Liquids";
        }
        final String label = SeismicCatalog.veinLabel(target, 0).toLowerCase(Locale.ROOT);
        return label.isEmpty() ? label : Character.toUpperCase(label.charAt(0)) + label.substring(1);
    }

    public static String findName(String target, int kind) {
        if (SeismicCatalog.LIQUIDS.equals(target)) {
            return "Liquids: " + (kind == SeismicCatalog.KIND_LAVA ? "lava" : "water");
        }
        return station(target);
    }

    public static Find biggest(SurveyScene scene, String target, int kind, double fromX, double fromZ) {
        final SurveyScene.Layer layer = scene.layer(target);
        if (layer == null) {
            return null;
        }
        SurveyScene.Body best = null;
        for (final SurveyScene.Body b : layer.bodies) {
            if ((kind < 0 || b.kind == kind) && (best == null || b.count > best.count
                    || (b.count == best.count && b.nearDist < best.nearDist))) {
                best = b;
            }
        }
        return best == null ? null : place(best, best.nearX, best.nearY, best.nearZ, fromX, fromZ);
    }

    public static Find place(SurveyScene.Body b, int x, int y, int z, double fromX, double fromZ) {
        final double dx = x + 0.5 - fromX;
        final double dz = z + 0.5 - fromZ;
        return new Find(b, x, y, z, Math.sqrt(dx * dx + dz * dz), bearing(fromX, fromZ, x + 0.5, z + 0.5));
    }

    public static List<SurveyScene.Body> newest(SurveyScene scene, long since) {
        final List<SurveyScene.Body> out = new ArrayList<>();
        for (final SurveyScene.Layer l : scene.layers) {
            for (final SurveyScene.Body b : l.bodies) {
                if (b.newest >= since) {
                    out.add(b);
                }
            }
        }
        out.sort((a, b) -> Long.compare(b.newest, a.newest));
        return out;
    }

    public static String noun(String target, int kind) {
        if (SeismicCatalog.VOIDS.equals(target)) {
            return "void";
        }
        return SeismicCatalog.veinLabel(target, kind).toLowerCase(Locale.ROOT);
    }

    public static String plural(String target, int count) {
        if (SeismicCatalog.VOIDS.equals(target)) {
            return count == 1 ? "void" : "voids";
        }
        if (SeismicCatalog.LIQUIDS.equals(target)) {
            return count == 1 ? "pocket" : "pockets";
        }
        return count == 1 ? "vein" : "veins";
    }

    public static String thousands(long n) {
        return String.format(Locale.ROOT, "%,d", n);
    }

    public static String ago(long now, long then) {
        if (then == Long.MIN_VALUE) {
            return "never";
        }
        final long t = Math.max(0L, now - then);
        if (t < 40L) {
            return "now";
        }
        if (t < 1_200L) {
            return (t / 20L) + " s";
        }
        if (t < 72_000L) {
            return (t / 1_200L) + " min";
        }
        if (t < 1_728_000L) {
            return (t / 72_000L) + " h";
        }
        return (t / 1_728_000L) + " d";
    }
}
