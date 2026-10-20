package com.createstats.battle.world;

import com.createstats.battle.BattleLightSnapshot;
import com.createstats.battle.BattleObserverSnapshot;
import com.createstats.battle.BattleWeaponSnapshot;
import com.createstats.battle.core.BattleReport;
import com.createstats.battle.core.BattleSystem;
import com.createstats.battle.core.GunState;
import com.createstats.blockentity.DamageObserverSettings;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;

// temporary, keeps the old stats display views alive until they read the report
public final class LegacySnapshots {
    private LegacySnapshots() {
    }

    public static List<BattleObserverSnapshot> from(BattleReport report, BlockPos bridge, String dimension, long now) {
        List<BattleObserverSnapshot> out = new ArrayList<>();
        if (report == null || !report.hasReference()) return out;
        for (BattleSystem s : BattleSystem.values()) {
            BattleReport.SystemLine line = report.system(s);
            if (line.total() <= 0 && s != BattleSystem.HULL) continue;
            int total = line.total();
            int intact = line.intact();
            double health = total <= 0 ? 100.0 : 100.0 * intact / total;
            out.add(new BattleObserverSnapshot(
                    name(s), category(s), DamageObserverSettings.WatchMode.LOCAL_AREA, bridge, bridge, 1, Math.max(1, total), 0,
                    dimension, now, health, total, total, intact, total - intact,
                    s == BattleSystem.HULL ? Math.max(0, report.newBlocks()) : 0,
                    0, 0L, 0L, 0L, 0L, 0L, 0L, BattleWeaponSnapshot.empty(), BattleLightSnapshot.empty()));
        }
        for (BattleReport.GunLine g : report.guns()) {
            boolean down = g.gunState() == GunState.DOWN;
            out.add(new BattleObserverSnapshot(
                    g.name(), DamageObserverSettings.ObserverCategory.WEAPONS, DamageObserverSettings.WatchMode.LOCAL_AREA,
                    bridge, bridge, 1, 1, 0, dimension, now, down ? 0.0 : 100.0, 1, 1, down ? 0 : 1, down ? 1 : 0, 0,
                    0, 0L, 0L, 0L, 0L, 0L, 0L, weapon(g), BattleLightSnapshot.empty()));
        }
        return out;
    }

    private static BattleWeaponSnapshot weapon(BattleReport.GunLine g) {
        GunState st = g.gunState();
        String kind = g.kind() == 1 ? "AUTOCANNON" : g.kind() == 0 ? "BIG_CANNON" : "NONE";
        String readiness = switch (st) {
            case READY, FIRING, SAFE, SLEWING, BLOCKED, OUT_OF_RANGE, OUT_OF_ARC, OFF_BORE, IDLE, HOLD -> "READY";
            case LOADING -> "LOADING";
            case DOWN, NOT_ASSEMBLED -> "DAMAGED";
            default -> "UNKNOWN";
        };
        String loaded = g.load() < 0 ? "UNKNOWN" : g.load() > 0 ? "LOADED" : "EMPTY";
        int rounds = g.kind() == 1 ? Math.max(0, Math.min(g.load(), 9998)) : 0;
        boolean creative = g.kind() == 1 && g.load() >= 9999;
        return new BattleWeaponSnapshot(true, kind, readiness, loaded, "Unknown", g.name(), 0, 0, 0, st == GunState.DOWN ? 1 : 0,
                0, 0, 0, 0, st == GunState.DOWN ? 0 : 1, 0, 0, g.kind() == 1 ? 1 : 0, rounds, 0, 0, creative, 0,
                g.kind() == 0 && g.load() > 0 ? 1 : 0, g.kind() == 0 && g.load() > 0 ? 1 : 0, 0, 0, 0, 0, 0, 0, 0, 0,
                st == GunState.DOWN ? 1 : 0, 0);
    }

    private static String name(BattleSystem s) {
        return switch (s) {
            case HULL -> "Hull";
            case LIFT -> "Lift";
            case PROPULSION -> "Propulsion";
            case POWER -> "Power";
            case GUNS -> "Guns";
            case CONTROL -> "Control";
            case CARGO -> "Cargo";
        };
    }

    private static DamageObserverSettings.ObserverCategory category(BattleSystem s) {
        return switch (s) {
            case HULL -> DamageObserverSettings.ObserverCategory.HULL;
            case LIFT -> DamageObserverSettings.ObserverCategory.LIFT;
            case PROPULSION, POWER -> DamageObserverSettings.ObserverCategory.POWER;
            case GUNS -> DamageObserverSettings.ObserverCategory.WEAPONS;
            case CONTROL -> DamageObserverSettings.ObserverCategory.CONTROL;
            case CARGO -> DamageObserverSettings.ObserverCategory.CARGO;
        };
    }
}
