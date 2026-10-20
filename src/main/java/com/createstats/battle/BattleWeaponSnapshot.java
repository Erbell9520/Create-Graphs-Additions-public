package com.createstats.battle;

import net.minecraft.nbt.CompoundTag;

public record BattleWeaponSnapshot(
        boolean detected,
        String kind,
        String readiness,
        String loadedState,
        String material,
        String displayName,
        int blocks,
        int baselineBlocks,
        int presentBlocks,
        int missingBlocks,
        int barrelBlocks,
        int chamberBlocks,
        int breechBlocks,
        int muzzleBlocks,
        int mountBlocks,
        int loaderBlocks,
        int recoilBlocks,
        int ammoContainers,
        int ammoRounds,
        int ammoCapacity,
        int tracerRounds,
        boolean ammoCreative,
        int fireRateRpm,
        int projectileBlocks,
        int propellantBlocks,
        int barrelPayloadBlocks,
        int chamberPayloadBlocks,
        int breechPayloadBlocks,
        int unknownPayloadBlocks,
        int inspectedPayloadBlocks,
        int emptyPayloadBlocks,
        int missingBarrelBlocks,
        int missingBreechBlocks,
        int missingMountBlocks,
        int missingLoaderBlocks) {
    public static BattleWeaponSnapshot empty() {
        return new BattleWeaponSnapshot(
                false,
                "NONE",
                "OFFLINE",
                "NOT_APPLICABLE",
                "Unknown",
                "",
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                false,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("Detected", this.detected);
        tag.putString("Kind", this.kind);
        tag.putString("Readiness", this.readiness);
        tag.putString("LoadedState", this.loadedState);
        tag.putString("Material", this.material);
        tag.putString("DisplayName", this.displayName);
        tag.putInt("Blocks", this.blocks);
        tag.putInt("BaselineBlocks", this.baselineBlocks);
        tag.putInt("PresentBlocks", this.presentBlocks);
        tag.putInt("MissingBlocks", this.missingBlocks);
        tag.putInt("BarrelBlocks", this.barrelBlocks);
        tag.putInt("ChamberBlocks", this.chamberBlocks);
        tag.putInt("BreechBlocks", this.breechBlocks);
        tag.putInt("MuzzleBlocks", this.muzzleBlocks);
        tag.putInt("MountBlocks", this.mountBlocks);
        tag.putInt("LoaderBlocks", this.loaderBlocks);
        tag.putInt("RecoilBlocks", this.recoilBlocks);
        tag.putInt("AmmoContainers", this.ammoContainers);
        tag.putInt("AmmoRounds", this.ammoRounds);
        tag.putInt("AmmoCapacity", this.ammoCapacity);
        tag.putInt("TracerRounds", this.tracerRounds);
        tag.putBoolean("AmmoCreative", this.ammoCreative);
        tag.putInt("FireRateRpm", this.fireRateRpm);
        tag.putInt("ProjectileBlocks", this.projectileBlocks);
        tag.putInt("PropellantBlocks", this.propellantBlocks);
        tag.putInt("BarrelPayloadBlocks", this.barrelPayloadBlocks);
        tag.putInt("ChamberPayloadBlocks", this.chamberPayloadBlocks);
        tag.putInt("BreechPayloadBlocks", this.breechPayloadBlocks);
        tag.putInt("UnknownPayloadBlocks", this.unknownPayloadBlocks);
        tag.putInt("InspectedPayloadBlocks", this.inspectedPayloadBlocks);
        tag.putInt("EmptyPayloadBlocks", this.emptyPayloadBlocks);
        tag.putInt("MissingBarrelBlocks", this.missingBarrelBlocks);
        tag.putInt("MissingBreechBlocks", this.missingBreechBlocks);
        tag.putInt("MissingMountBlocks", this.missingMountBlocks);
        tag.putInt("MissingLoaderBlocks", this.missingLoaderBlocks);
        return tag;
    }

    public static BattleWeaponSnapshot load(CompoundTag tag) {
        return new BattleWeaponSnapshot(
                tag.getBoolean("Detected"),
                tag.contains("Kind") ? tag.getString("Kind") : "NONE",
                tag.contains("Readiness") ? tag.getString("Readiness") : "OFFLINE",
                tag.contains("LoadedState") ? tag.getString("LoadedState") : "NOT_APPLICABLE",
                tag.contains("Material") ? tag.getString("Material") : "Unknown",
                tag.contains("DisplayName") ? tag.getString("DisplayName") : "",
                tag.getInt("Blocks"),
                tag.getInt("BaselineBlocks"),
                tag.getInt("PresentBlocks"),
                tag.getInt("MissingBlocks"),
                tag.getInt("BarrelBlocks"),
                tag.getInt("ChamberBlocks"),
                tag.getInt("BreechBlocks"),
                tag.getInt("MuzzleBlocks"),
                tag.getInt("MountBlocks"),
                tag.getInt("LoaderBlocks"),
                tag.getInt("RecoilBlocks"),
                tag.getInt("AmmoContainers"),
                tag.getInt("AmmoRounds"),
                tag.getInt("AmmoCapacity"),
                tag.getInt("TracerRounds"),
                tag.getBoolean("AmmoCreative"),
                tag.getInt("FireRateRpm"),
                tag.getInt("ProjectileBlocks"),
                tag.getInt("PropellantBlocks"),
                tag.getInt("BarrelPayloadBlocks"),
                tag.getInt("ChamberPayloadBlocks"),
                tag.getInt("BreechPayloadBlocks"),
                tag.getInt("UnknownPayloadBlocks"),
                tag.getInt("InspectedPayloadBlocks"),
                tag.getInt("EmptyPayloadBlocks"),
                tag.getInt("MissingBarrelBlocks"),
                tag.getInt("MissingBreechBlocks"),
                tag.getInt("MissingMountBlocks"),
                tag.getInt("MissingLoaderBlocks"));
    }
}
