package com.createstats.battle;

import com.createstats.blockentity.DamageObserverBE;
import com.createstats.blockentity.DamageObserverSettings;
import net.minecraft.core.BlockPos;

public final class BattleObserverSnapshotFactory {

    private BattleObserverSnapshotFactory() {
    }

    public static BattleObserverSnapshot capture(DamageObserverBE observer, BlockPos displayPos, BlockPos sourcePos, long seenTick) {
        DamageObserverSettings settings = observer.getSettings();
        int diameter = observer.getObservedDiameter();
        String dimension = observer.getLevel() == null ? "" : observer.getLevel().dimension().location().toString();
        return new BattleObserverSnapshot(
                settings.getObserverName(),
                settings.getCategory(),
                settings.getWatchMode(),
                sourcePos.immutable(),
                observer.getObservedAnchorPos().immutable(),
                diameter,
                diameter * diameter * diameter,
                displayPos.distManhattan(sourcePos),
                dimension,
                seenTick,
                observer.getHealthPercent(),
                observer.getWatchedBlocks(),
                observer.getBaselineBlocks(),
                observer.getPresentBaselineBlocks(),
                observer.getMissingBlocks(),
                observer.getNewBlocks(),
                observer.getCargoContainers(),
                observer.getCargoItems(),
                observer.getCargoItemCapacity(),
                observer.getCargoFluidAmount(),
                observer.getCargoFluidCapacity(),
                observer.getCargoEnergyStored(),
                observer.getCargoEnergyCapacity(),
                new BattleWeaponSnapshot(
                        observer.isWeaponDetected(),
                        observer.getWeaponKind(),
                        observer.getWeaponReadiness(),
                        observer.getWeaponLoadedState(),
                        observer.getWeaponMaterial(),
                        observer.getWeaponDisplayName(),
                        observer.getWeaponBlocks(),
                        observer.getWeaponBaselineBlocks(),
                        observer.getWeaponPresentBlocks(),
                        observer.getWeaponMissingBlocks(),
                        observer.getWeaponBarrelBlocks(),
                        observer.getWeaponChamberBlocks(),
                        observer.getWeaponBreechBlocks(),
                        observer.getWeaponMuzzleBlocks(),
                        observer.getWeaponMountBlocks(),
                        observer.getWeaponLoaderBlocks(),
                        observer.getWeaponRecoilBlocks(),
                        observer.getWeaponAmmoContainers(),
                        observer.getWeaponAmmoRounds(),
                        observer.getWeaponAmmoCapacity(),
                        observer.getWeaponTracerRounds(),
                        observer.isWeaponAmmoCreative(),
                        observer.getWeaponFireRateRpm(),
                        observer.getWeaponProjectileBlocks(),
                        observer.getWeaponPropellantBlocks(),
                        observer.getWeaponBarrelPayloadBlocks(),
                        observer.getWeaponChamberPayloadBlocks(),
                        observer.getWeaponBreechPayloadBlocks(),
                        observer.getWeaponUnknownPayloadBlocks(),
                        observer.getWeaponInspectedPayloadBlocks(),
                        observer.getWeaponEmptyPayloadBlocks(),
                        observer.getWeaponMissingBarrelBlocks(),
                        observer.getWeaponMissingBreechBlocks(),
                        observer.getWeaponMissingMountBlocks(),
                        observer.getWeaponMissingLoaderBlocks()),
                new BattleLightSnapshot(observer.getDetectedLights()));
    }
}