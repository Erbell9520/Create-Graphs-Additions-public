package com.createstats.seismic.archive;

import com.createstats.seismic.SeismicCatalog;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

// most useful line first so a one row sign still says something
public final class SurveyText {
    private SurveyText() {
    }

    public static List<String> lines(SurveyScene scene, String target, double fromX, double fromZ, int rows, long now) {
        final List<String> out = new ArrayList<>();
        if (scene.bowls.isEmpty()) {
            out.add("Seismic Scanner: no survey yet");
            return out;
        }
        String headline = target == null || target.isEmpty() ? lastTarget(scene) : target;
        if (headline.isEmpty()) {
            headline = scene.bowls.get(scene.bowls.size() - 1).target;
        }
        out.add(nearestLine(scene, headline, fromX, fromZ));
        final SurveyScene.Layer layer = scene.layer(headline);
        if (layer != null) {
            out.add(String.format(Locale.ROOT, "%s: %d %s, %s blocks", cap(name(headline)), layer.bodies.size(),
                    SurveyFinds.plural(headline, layer.bodies.size()), SurveyFinds.thousands(layer.blocks)));
            final SurveyFinds.Find near = SurveyFinds.nearest(scene, headline, -1, fromX, fromZ);
            final SurveyFinds.Find big = SurveyFinds.biggest(scene, headline, -1, fromX, fromZ);
            if (big != null && near != null && big.body() != near.body()) {
                out.add(String.format(Locale.ROOT, "Biggest: %s blocks, %s", SurveyFinds.thousands(big.body().count),
                        big.where()));
            }
        }
        final SurveyScene.Bowl last = scene.bowls.get(scene.bowls.size() - 1);
        out.add(String.format(Locale.ROOT, "Last blow %s ago, radius %d%s", SurveyFinds.ago(now, last.at), last.reach,
                last.done && last.front + 1.0e-3 < last.reach ? ", cut short" : ""));
        for (final String other : scene.targetsSurveyed()) {
            if (!other.equals(headline)) {
                out.add(nearestLine(scene, other, fromX, fromZ));
            }
        }
        while (out.size() > Math.max(1, rows)) {
            out.remove(out.size() - 1);
        }
        return out;
    }

    public static List<String> signLines(java.util.Map<String, SurveyChannels.Figures> figures,
                                         List<ArchiveBlow> blows, String target, double fromX, double fromZ, int rows,
                                         int cols, long now) {
        final List<String> out = new ArrayList<>();
        final boolean narrow = cols > 0 && cols < 30;
        if (blows.isEmpty()) {
            out.add(narrow ? "No survey yet" : "Seismic Scanner: no survey yet");
            return trim(out, rows);
        }
        ArchiveBlow last = blows.get(0);
        for (final ArchiveBlow b : blows) {
            if (b.at > last.at) {
                last = b;
            }
        }
        final String headline = target == null || target.isEmpty() ? last.target : target;
        final SurveyChannels.Figures h = figures.get(headline);
        signNearest(out, h, headline, fromX, fromZ, narrow);
        if (h != null && h.blocks() > 0) {
            out.add(narrow ? SurveyFinds.thousands(h.blocks()) + " blocks"
                    : String.format(Locale.ROOT, "%s: %s blocks known", cap(name(headline)),
                    SurveyFinds.thousands(h.blocks())));
        }
        out.add(narrow ? "Blow " + SurveyFinds.ago(now, last.at) + " ago"
                : String.format(Locale.ROOT, "Last blow %s ago, %d m%s", SurveyFinds.ago(now, last.at), last.reach,
                last.cutShort() ? ", cut short" : ""));
        for (final SurveyChannels.Figures f : figures.values()) {
            if (!f.target().equals(headline)) {
                signNearest(out, f, f.target(), fromX, fromZ, narrow);
            }
        }
        return trim(out, rows);
    }

    private static void signNearest(List<String> out, SurveyChannels.Figures f, String target, double fromX,
                                    double fromZ, boolean narrow) {
        if (f == null || Double.isNaN(f.nearest())) {
            out.add(narrow ? "No " + name(target) : "No " + name(target) + " heard in the ground read");
            return;
        }
        final String noun = SurveyFinds.noun(target, f.nearestKind());
        final String way = f.nearest() < 1.5 ? "below" : Math.round(f.nearest()) + (narrow ? "m " : " m ")
                + SurveyFinds.compass(SurveyFinds.bearing(fromX, fromZ, f.nearestX() + 0.5, f.nearestZ() + 0.5));
        if (narrow) {
            out.add(cap(noun) + " " + way);
            out.add("Y " + f.nearestY());
        } else {
            out.add("Nearest " + noun + " " + way + ", Y " + f.nearestY());
        }
    }

    private static List<String> trim(List<String> out, int rows) {
        while (out.size() > Math.max(1, rows)) {
            out.remove(out.size() - 1);
        }
        return out;
    }

    public static String nearestLine(SurveyScene scene, String target, double fromX, double fromZ) {
        if (SeismicCatalog.LIQUIDS.equals(target)) {
            final SurveyFinds.Find lava = SurveyFinds.nearest(scene, target, SeismicCatalog.KIND_LAVA, fromX, fromZ);
            final SurveyFinds.Find water = SurveyFinds.nearest(scene, target, SeismicCatalog.KIND_WATER, fromX, fromZ);
            final SurveyFinds.Find f = lava != null ? lava : water;
            if (f != null) {
                return "Nearest " + SurveyFinds.noun(target, f.body().kind) + " " + f.where();
            }
        } else {
            final SurveyFinds.Find f = SurveyFinds.nearest(scene, target, -1, fromX, fromZ);
            if (f != null) {
                return "Nearest " + SurveyFinds.noun(target, f.body().kind) + " " + f.where();
            }
        }
        int reach = 0;
        for (final SurveyScene.Bowl b : scene.bowls) {
            if (b.target.equals(target)) {
                reach = Math.max(reach, Math.round(b.recorded));
            }
        }
        return String.format(Locale.ROOT, "No %s within %d m of a rig", name(target), reach);
    }

    public static String name(String target) {
        return SurveyFinds.station(target).toLowerCase(java.util.Locale.ROOT);
    }

    private static String lastTarget(SurveyScene scene) {
        return scene.bowls.isEmpty() ? "" : scene.bowls.get(scene.bowls.size() - 1).target;
    }

    private static String cap(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
