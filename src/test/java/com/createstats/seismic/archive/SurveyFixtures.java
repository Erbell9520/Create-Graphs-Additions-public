package com.createstats.seismic.archive;

import com.createstats.seismic.SeismicCatalog;

import java.util.ArrayList;
import java.util.List;

public final class SurveyFixtures {
    public static final int PANEL_X = 199;
    public static final int PANEL_Y = 69;
    public static final int PANEL_Z = -374;

    public static final long T0 = 2_395_242L;
    public static final long HOUR = 72_000L;

    private SurveyFixtures() {
    }

    public record Shot(String name, SurveyScene scene, long now) {
    }

    private static long key(int[] rig) {
        return ArchiveBlow.key(rig[0], rig[1], rig[2]);
    }

    private static SurveyScene scene(SeismicArchive a, List<int[]> known, List<int[]> linked, long now) {
        final List<Long> k = new ArrayList<>();
        for (final int[] r : known) {
            k.add(key(r));
        }
        final List<Long> l = new ArrayList<>();
        for (final int[] r : linked) {
            l.add(key(r));
        }
        return SceneBuilder.build(a, k, l, Long.MIN_VALUE, PANEL_X, PANEL_Y, PANEL_Z, SceneBuilder.DEFAULT_BUDGET, now,
                a.revision());
    }

    public static Shot ownerToday() {
        final SiteGround g = SiteGround.owner();
        final SeismicArchive a = new SeismicArchive();
        g.strike(a, SiteGround.OWNER_DIAMOND_RIG, "c:ores/diamond", 64, T0 - 155L);
        g.strike(a, SiteGround.OWNER_IRON_RIG, "c:ores/iron", 64, T0);
        final List<int[]> rigs = List.of(SiteGround.OWNER_IRON_RIG, SiteGround.OWNER_DIAMOND_RIG);
        return new Shot("owner, today", scene(a, rigs, rigs, T0 + 1_200L), T0 + 1_200L);
    }

    public static Shot oneSurvey() {
        final SiteGround g = SiteGround.owner();
        final SeismicArchive a = new SeismicArchive();
        g.strike(a, SiteGround.OWNER_IRON_RIG, "c:ores/iron", 64, T0);
        final List<int[]> rigs = List.of(SiteGround.OWNER_IRON_RIG);
        return new Shot("one survey", scene(a, rigs, rigs, T0 + 1_200L), T0 + 1_200L);
    }

    public static Shot afternoon() {
        final SiteGround g = SiteGround.owner();
        final SeismicArchive a = new SeismicArchive();
        long t = T0;
        g.strike(a, SiteGround.OWNER_IRON_RIG, "c:ores/iron", 64, t);
        g.strike(a, SiteGround.OWNER_DIAMOND_RIG, "c:ores/diamond", 64, t += 1_200L);
        g.strike(a, SiteGround.EAST_RIG, "c:ores/copper", 48, t += 9_000L);
        g.strike(a, SiteGround.EAST_RIG, "c:ores/coal", 48, t += 2_400L);
        g.strike(a, SiteGround.WEST_RIG, "c:ores/gold", 64, t += 14_000L);
        g.strike(a, SiteGround.WEST_RIG, "c:ores/iron", 48, t += 3_000L);
        g.strike(a, SiteGround.NORTH_RIG, "c:ores/redstone", 64, t += 20_000L);
        g.strike(a, SiteGround.OWNER_IRON_RIG, SeismicCatalog.VOIDS, 64, t += 30_000L);
        g.strike(a, SiteGround.OWNER_DIAMOND_RIG, SeismicCatalog.LIQUIDS, 64, t += 4_000L);
        g.strike(a, SiteGround.DEEP_RIG, SeismicCatalog.LIQUIDS, 64, t += 40_000L);
        g.strike(a, SiteGround.DEEP_RIG, "c:ores/lapis", 48, t += 6_000L);

        mineNearest(g, a, "c:ores/iron", SiteGround.OWNER_IRON_RIG);
        g.strike(a, SiteGround.OWNER_IRON_RIG, "c:ores/iron", 48, t += 50_000L);
        g.strike(a, SiteGround.DEEP_RIG, "c:ores/diamond", 64, t += 20_000L);
        final long now = t + 1_200L;
        final List<int[]> known = List.of(SiteGround.OWNER_IRON_RIG, SiteGround.OWNER_DIAMOND_RIG, SiteGround.EAST_RIG,
                SiteGround.WEST_RIG, SiteGround.NORTH_RIG, SiteGround.DEEP_RIG);
        final List<int[]> linked = List.of(SiteGround.OWNER_IRON_RIG, SiteGround.OWNER_DIAMOND_RIG, SiteGround.DEEP_RIG);
        return new Shot("an afternoon on the site", scene(a, known, linked, now), now);
    }

    public static Shot live() {
        final Shot s = afternoon();
        final long landed = s.scene().newestBlow();
        return new Shot("a blow landing", s.scene(), landed + 34L);
    }

    public static Shot caves() {
        final SiteGround g = SiteGround.owner();
        final SeismicArchive a = new SeismicArchive();
        long t = T0;
        g.strike(a, SiteGround.OWNER_IRON_RIG, SeismicCatalog.VOIDS, 64, t);
        g.strike(a, SiteGround.WEST_RIG, SeismicCatalog.VOIDS, 64, t += 6_000L);
        g.strike(a, SiteGround.DEEP_RIG, SeismicCatalog.VOIDS, 64, t += 6_000L);
        g.strike(a, SiteGround.DEEP_RIG, "c:ores/diamond", 64, t += 6_000L);
        final List<int[]> rigs = List.of(SiteGround.OWNER_IRON_RIG, SiteGround.WEST_RIG, SiteGround.DEEP_RIG);
        return new Shot("cave surveys", scene(a, rigs, rigs, t + 2_400L), t + 2_400L);
    }

    public static Shot linkedNoSurvey() {
        final SeismicArchive a = new SeismicArchive();
        final List<int[]> rigs = List.of(SiteGround.OWNER_IRON_RIG);
        return new Shot("linked, no survey yet", scene(a, rigs, rigs, T0), T0);
    }

    private static void mineNearest(SiteGround g, SeismicArchive a, String target, int[] rig) {
        ArchiveBlow best = null;
        for (final ArchiveBlow b : a.blows()) {
            if (b.target.equals(target) && b.x == rig[0] && b.y == rig[1] && b.z == rig[2]) {
                best = b;
            }
        }
        if (best == null || best.cellCount() == 0) {
            return;
        }
        int pick = best.cells()[0];
        double d = Double.MAX_VALUE;
        for (final int c : best.cells()) {
            final double dd = best.distanceTo(best.cellX(c), best.cellY(c), best.cellZ(c));
            if (dd < d) {
                d = dd;
                pick = c;
            }
        }

        final int px = best.cellX(pick);
        final int py = best.cellY(pick);
        final int pz = best.cellZ(pick);
        for (int x = px - 2; x <= px + 2; x++) {
            for (int y = py - 2; y <= py + 2; y++) {
                for (int z = pz - 2; z <= pz + 2; z++) {
                    g.mine(x, y, z);
                }
            }
        }
    }
}
