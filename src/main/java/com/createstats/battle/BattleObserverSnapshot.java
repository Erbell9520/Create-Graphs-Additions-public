package com.createstats.battle;

import com.createstats.blockentity.DamageObserverSettings;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

public record BattleObserverSnapshot(
        String name,
        DamageObserverSettings.ObserverCategory category,
        DamageObserverSettings.WatchMode mode,
        BlockPos sourcePos,
        BlockPos anchor,
        int diameter,
        int volume,
        int distance,
        String dimension,
        long lastSeenTick,
        double healthPercent,
        int watchedBlocks,
        int baselineBlocks,
        int presentBaselineBlocks,
        int missingBlocks,
        int newBlocks,
        int cargoContainers,
        long cargoItems,
        long cargoItemCapacity,
        long cargoFluidAmount,
        long cargoFluidCapacity,
        long cargoEnergyStored,
        long cargoEnergyCapacity,
        BattleWeaponSnapshot weapon,
        BattleLightSnapshot lights) {

    public CompoundTag save() {
        CompoundTag entry = new CompoundTag();
        entry.putString("Name", this.name);
        entry.putString("Category", this.category.name());
        entry.putString("Mode", this.mode.name());
        entry.putLong("SourcePos", this.sourcePos.asLong());
        entry.putLong("Anchor", this.anchor.asLong());
        entry.putInt("Diameter", this.diameter);
        entry.putInt("Volume", this.volume);
        entry.putInt("Distance", this.distance);
        entry.putString("Dimension", this.dimension);
        entry.putLong("LastSeenTick", this.lastSeenTick);
        entry.putDouble("HealthPercent", this.healthPercent);
        entry.putInt("WatchedBlocks", this.watchedBlocks);
        entry.putInt("BaselineBlocks", this.baselineBlocks);
        entry.putInt("PresentBaselineBlocks", this.presentBaselineBlocks);
        entry.putInt("MissingBlocks", this.missingBlocks);
        entry.putInt("NewBlocks", this.newBlocks);
        entry.putInt("CargoContainers", this.cargoContainers);
        entry.putLong("CargoItems", this.cargoItems);
        entry.putLong("CargoItemCapacity", this.cargoItemCapacity);
        entry.putLong("CargoFluidAmount", this.cargoFluidAmount);
        entry.putLong("CargoFluidCapacity", this.cargoFluidCapacity);
        entry.putLong("CargoEnergyStored", this.cargoEnergyStored);
        entry.putLong("CargoEnergyCapacity", this.cargoEnergyCapacity);
        entry.put("Weapon", this.weapon.save());
        entry.put("Lighting", this.lights.save());
        return entry;
    }

    public static BattleObserverSnapshot load(CompoundTag entry) {
        int diameter = Math.max(1, entry.getInt("Diameter"));
        return new BattleObserverSnapshot(
                entry.getString("Name"),
                safeCategory(entry.getString("Category")),
                safeMode(entry.getString("Mode")),
                BlockPos.of(entry.getLong("SourcePos")),
                BlockPos.of(entry.getLong("Anchor")),
                diameter,
                entry.contains("Volume") ? Math.max(1, entry.getInt("Volume")) : diameter * diameter * diameter,
                Math.max(0, entry.getInt("Distance")),
                entry.getString("Dimension"),
                entry.getLong("LastSeenTick"),
                entry.contains("HealthPercent") ? entry.getDouble("HealthPercent") : 100.0,
                entry.getInt("WatchedBlocks"),
                entry.getInt("BaselineBlocks"),
                entry.getInt("PresentBaselineBlocks"),
                entry.getInt("MissingBlocks"),
                entry.getInt("NewBlocks"),
                entry.getInt("CargoContainers"),
                entry.getLong("CargoItems"),
                entry.getLong("CargoItemCapacity"),
                entry.getLong("CargoFluidAmount"),
                entry.getLong("CargoFluidCapacity"),
                entry.getLong("CargoEnergyStored"),
                entry.getLong("CargoEnergyCapacity"),
                entry.contains("Weapon", Tag.TAG_COMPOUND) ? BattleWeaponSnapshot.load(entry.getCompound("Weapon")) : BattleWeaponSnapshot.empty(),
                entry.contains("Lighting", Tag.TAG_COMPOUND) ? BattleLightSnapshot.load(entry.getCompound("Lighting")) : BattleLightSnapshot.empty());
    }

    public boolean sameDisplayData(BattleObserverSnapshot other) {
        return other != null
                && this.name.equals(other.name)
                && this.category == other.category
                && this.mode == other.mode
                && this.sourcePos.equals(other.sourcePos)
                && this.anchor.equals(other.anchor)
                && this.diameter == other.diameter
                && this.volume == other.volume
                && this.distance == other.distance
                && this.dimension.equals(other.dimension)
                && Double.compare(this.healthPercent, other.healthPercent) == 0
                && this.watchedBlocks == other.watchedBlocks
                && this.baselineBlocks == other.baselineBlocks
                && this.presentBaselineBlocks == other.presentBaselineBlocks
                && this.missingBlocks == other.missingBlocks
                && this.newBlocks == other.newBlocks
                && this.cargoContainers == other.cargoContainers
                && this.cargoItems == other.cargoItems
                && this.cargoItemCapacity == other.cargoItemCapacity
                && this.cargoFluidAmount == other.cargoFluidAmount
                && this.cargoFluidCapacity == other.cargoFluidCapacity
                && this.cargoEnergyStored == other.cargoEnergyStored
                && this.cargoEnergyCapacity == other.cargoEnergyCapacity
                && this.weapon.equals(other.weapon)
                && this.lights.equals(other.lights);
    }

    private static DamageObserverSettings.ObserverCategory safeCategory(String value) {
        try {
            return DamageObserverSettings.ObserverCategory.valueOf(value);
        } catch (Exception ignored) {
            return DamageObserverSettings.ObserverCategory.HULL;
        }
    }

    private static DamageObserverSettings.WatchMode safeMode(String value) {
        try {
            return DamageObserverSettings.WatchMode.valueOf(value);
        } catch (Exception ignored) {
            return DamageObserverSettings.WatchMode.SINGLE_BLOCK;
        }
    }
}