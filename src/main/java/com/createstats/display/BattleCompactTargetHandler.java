package com.createstats.display;

import com.createstats.battle.BattleObserverSnapshot;
import com.createstats.battle.BattleWeaponSnapshot;
import com.createstats.blockentity.BattleScreenDisplayBE;
import com.createstats.blockentity.MiniDisplayBE;
import com.createstats.blockentity.StatusTowerLightBE;
import com.createstats.data.MetricType;
import com.createstats.util.InstrumentSourceHelper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;

public final class BattleCompactTargetHandler {
    public static final String SOURCE_TYPE = "Battle Bridge";
    public static final String ALERT_METRIC = "battle_alert_pct";
    public static final String INTEGRITY_METRIC = "battle_integrity_pct";
    public static final String READINESS_METRIC = "battle_ready_pct";
    public static final String OBSERVER_COUNT_METRIC = "battle_observer_count";
    public static final String WEAPON_COUNT_METRIC = "battle_weapon_count";
    public static final String READY_WEAPONS_METRIC = "battle_ready_weapons";
    public static final String MISSING_BLOCKS_METRIC = "battle_missing_blocks";
    public static final String REBUILD_BLOCKS_METRIC = "battle_rebuild_blocks";

    private BattleCompactTargetHandler() {}

    public static boolean isBattleSource(String sourceType) {
        return "battle.bridge".equals(CustomScreenSourceFamilyCatalog.familyForSourceType(sourceType).getFamilyId());
    }

    public static boolean hasBattleMetrics(Map<String, InstrumentSourceHelper.MetricSample> metrics) {
        return metrics.containsKey(ALERT_METRIC) || metrics.containsKey(INTEGRITY_METRIC);
    }

    public static void handleForTowerLight(StatusTowerLightBE towerLight, BlockPos sourcePos, BattleScreenDisplayBE battleScreen) {
        final BattleCompactSnapshot snapshot = captureSnapshot(battleScreen);
        towerLight.onAutoData(sourcePos, SOURCE_TYPE, ALERT_METRIC, "Battle Alert", snapshot.alertPercent(), MetricType.FILL_LEVEL);
        towerLight.onAutoData(sourcePos, SOURCE_TYPE, INTEGRITY_METRIC, "Hull Integrity", snapshot.integrityPercent(), MetricType.FILL_LEVEL);
        towerLight.onAutoData(sourcePos, SOURCE_TYPE, READINESS_METRIC, "Weapon Readiness", snapshot.readinessPercent(), MetricType.FILL_LEVEL);
        towerLight.onAutoData(sourcePos, SOURCE_TYPE, OBSERVER_COUNT_METRIC, "Observers", snapshot.observerCount(), MetricType.ITEM_COUNT);
        towerLight.onAutoData(sourcePos, SOURCE_TYPE, MISSING_BLOCKS_METRIC, "Breach Blocks", snapshot.missingBlocks(), MetricType.ITEM_COUNT);
    }

    public static void handleForMiniDisplay(MiniDisplayBE miniDisplay, BlockPos sourcePos, BattleScreenDisplayBE battleScreen) {
        final BattleCompactSnapshot snapshot = captureSnapshot(battleScreen);
        final Map<String, InstrumentSourceHelper.MetricSample> metrics = new LinkedHashMap<>();
        metrics.put(ALERT_METRIC, new InstrumentSourceHelper.MetricSample(ALERT_METRIC, "Battle Alert", snapshot.alertPercent(), MetricType.FILL_LEVEL));
        metrics.put(INTEGRITY_METRIC, new InstrumentSourceHelper.MetricSample(INTEGRITY_METRIC, "Hull Integrity", snapshot.integrityPercent(), MetricType.FILL_LEVEL));
        metrics.put(READINESS_METRIC, new InstrumentSourceHelper.MetricSample(READINESS_METRIC, "Weapon Readiness", snapshot.readinessPercent(), MetricType.FILL_LEVEL));
        metrics.put(OBSERVER_COUNT_METRIC, new InstrumentSourceHelper.MetricSample(OBSERVER_COUNT_METRIC, "Observers", snapshot.observerCount(), MetricType.ITEM_COUNT));
        metrics.put(WEAPON_COUNT_METRIC, new InstrumentSourceHelper.MetricSample(WEAPON_COUNT_METRIC, "Weapon Links", snapshot.weaponCount(), MetricType.ITEM_COUNT));
        metrics.put(READY_WEAPONS_METRIC, new InstrumentSourceHelper.MetricSample(READY_WEAPONS_METRIC, "Ready Guns", snapshot.readyWeapons(), MetricType.ITEM_COUNT));
        metrics.put(MISSING_BLOCKS_METRIC, new InstrumentSourceHelper.MetricSample(MISSING_BLOCKS_METRIC, "Breaches", snapshot.missingBlocks(), MetricType.ITEM_COUNT));
        metrics.put(REBUILD_BLOCKS_METRIC, new InstrumentSourceHelper.MetricSample(REBUILD_BLOCKS_METRIC, "Rebuild", snapshot.rebuildBlocks(), MetricType.ITEM_COUNT));
        miniDisplay.replaceAutoData(sourcePos, SOURCE_TYPE, metrics);
    }

    public static String alertLabel(float alertPercent) {
        if (alertPercent >= 90.0f) {
            return "CRITICAL";
        }
        if (alertPercent >= 75.0f) {
            return "WATCH";
        }
        if (alertPercent > 0.0f) {
            return "STABLE";
        }
        return "WAITING";
    }

    private static BattleCompactSnapshot captureSnapshot(BattleScreenDisplayBE battleScreen) {
        final List<BattleObserverSnapshot> snapshots = battleScreen.getBattleFeedSnapshots();
        if (snapshots.isEmpty()) {
            return new BattleCompactSnapshot(0.0f, 0.0f, 0, 0, 0, 0, 0, 0.0f);
        }

        int weaponCount = 0;
        int readyWeapons = 0;
        int totalMissing = 0;
        int totalNew = 0;
        double totalHealth = 0.0;
        for (BattleObserverSnapshot snapshot : snapshots) {
            totalHealth += snapshot.healthPercent();
            totalMissing += snapshot.missingBlocks();
            totalNew += snapshot.newBlocks();
            final BattleWeaponSnapshot weapon = snapshot.weapon();
            if (weapon.detected()) {
                weaponCount++;
                if (weapon.missingBlocks() == 0 && "READY".equals(weapon.readiness())) {
                    readyWeapons++;
                }
            }
        }

        final float integrityPercent = (float)(totalHealth / snapshots.size());
        final float readinessPercent = weaponCount > 0 ? readyWeapons * 100.0f / weaponCount : 100.0f;
        final float alertPercent = computeAlertPercent(integrityPercent, totalMissing, totalNew, weaponCount, readyWeapons);
        return new BattleCompactSnapshot(integrityPercent, alertPercent, snapshots.size(), weaponCount, readyWeapons, totalMissing, totalNew, readinessPercent);
    }

    private static float computeAlertPercent(float integrityPercent, int totalMissing, int totalNew, int weaponCount, int readyWeapons) {
        if (integrityPercent <= 40.0f || totalMissing >= 20) {
            return 100.0f;
        }
        if (weaponCount > 0 && readyWeapons <= 0) {
            return 100.0f;
        }
        if (totalMissing > 0 || totalNew > 0 || integrityPercent < 80.0f || readyWeapons < weaponCount) {
            return 80.0f;
        }
        return 20.0f;
    }

    private static record BattleCompactSnapshot(
            float integrityPercent,
            float alertPercent,
            int observerCount,
            int weaponCount,
            int readyWeapons,
            int missingBlocks,
            int rebuildBlocks,
            float readinessPercent) {}
}
