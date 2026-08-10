package com.createstats.display;

import com.createstats.battle.BattleLightSnapshot;
import com.createstats.blockentity.DamageObserverBE;
import com.createstats.blockentity.DamageObserverSettings;
import com.simibubi.create.api.behaviour.display.DisplaySource;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import com.simibubi.create.content.redstone.displayLink.target.DisplayTargetStats;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.List;

public class DamageObserverDisplaySource extends DisplaySource {

    @Override
    public List<MutableComponent> provideText(DisplayLinkContext context, DisplayTargetStats stats) {
        BlockEntity blockEntity = context.getSourceBlockEntity();
        if (!(blockEntity instanceof DamageObserverBE observer)) {
            return EMPTY;
        }
        observer.forceScanObservedVolume();
        DamageObserverSettings settings = observer.getSettings();
        int maxRows = Math.max(1, stats.maxRows());
        List<MutableComponent> lines = new ArrayList<>();
        this.addLine(lines, maxRows, Component.literal(settings.getObserverName()));
        this.addLine(lines, maxRows, Component.translatable("create_stats.damage_observer.display.role", settings.getCategory().getDisplayName()));
        this.addLine(lines, maxRows, Component.translatable("create_stats.damage_observer.display.integrity", String.format(java.util.Locale.ROOT, "%.0f", observer.getHealthPercent())));
        this.addLine(lines, maxRows, Component.translatable("create_stats.damage_observer.display.lost_added", observer.getMissingBlocks(), observer.getNewBlocks()));
        java.util.List<BattleLightSnapshot.Entry> lights = observer.getDetectedLights();
        if (!lights.isEmpty()) {
            int on = 0;
            for (BattleLightSnapshot.Entry light : lights) {
                if (light.lit()) {
                    on++;
                }
            }
            this.addLine(lines, maxRows, Component.translatable("create_stats.damage_observer.display.lights", lights.size(), on));
        }
        if (settings.getCategory() == DamageObserverSettings.ObserverCategory.CARGO) {
            this.addLine(lines, maxRows, Component.translatable("create_stats.damage_observer.display.cargo_items", observer.getCargoItems(), observer.getCargoItemCapacity()));
            this.addLine(lines, maxRows, Component.translatable("create_stats.damage_observer.display.cargo_fluid", observer.getCargoFluidAmount(), observer.getCargoFluidCapacity()));
            this.addLine(lines, maxRows, Component.translatable("create_stats.damage_observer.display.energy", observer.getCargoEnergyStored(), observer.getCargoEnergyCapacity()));
        } else if (settings.getCategory() == DamageObserverSettings.ObserverCategory.WEAPONS) {
            this.addLine(lines, maxRows, Component.translatable("create_stats.damage_observer.display.weapon", this.weaponKindText(observer)));
            this.addLine(lines, maxRows, Component.translatable("create_stats.damage_observer.display.status", observer.getWeaponReadiness()));
            this.addLine(lines, maxRows, Component.translatable("create_stats.damage_observer.display.parts", observer.getWeaponBarrelBlocks(), observer.getWeaponChamberBlocks(), observer.getWeaponBreechBlocks(), observer.getWeaponMountBlocks()));
            this.addLine(lines, maxRows, Component.translatable("create_stats.damage_observer.display.loaded", observer.getWeaponLoadedState()));
            this.addLine(lines, maxRows, Component.translatable("create_stats.damage_observer.display.mode", settings.getWatchMode().getDisplayName()));
            this.addLine(lines, maxRows, Component.translatable("create_stats.damage_observer.display.anchor", observer.getObservedAnchorPos().toShortString()));
        } else {
            this.addLine(lines, maxRows, Component.translatable("create_stats.damage_observer.display.mode", settings.getWatchMode().getDisplayName()));
            this.addLine(lines, maxRows, Component.translatable("create_stats.damage_observer.display.anchor", observer.getObservedAnchorPos().toShortString()));
        }
        return lines;
    }

    private Component weaponKindText(DamageObserverBE observer) {
        return switch (observer.getWeaponKind()) {
            case "AUTOCANNON" -> Component.translatable("create_stats.damage_observer.weapon.autocannon");
            case "DROP_MORTAR" -> Component.translatable("create_stats.damage_observer.weapon.drop_mortar");
            case "MOUNT" -> Component.translatable("create_stats.damage_observer.weapon.mount");
            case "LOADER" -> Component.translatable("create_stats.damage_observer.weapon.loader");
            case "BIG_CANNON" -> observer.getWeaponMaterial().equals("Unknown")
                ? Component.translatable("create_stats.damage_observer.weapon.big_cannon")
                : Component.translatable("create_stats.damage_observer.weapon.material_cannon", observer.getWeaponMaterial());
            default -> observer.isWeaponDetected()
                ? Component.translatable("create_stats.damage_observer.weapon.generic")
                : Component.translatable("create_stats.damage_observer.weapon.none");
        };
    }

    private void addLine(List<MutableComponent> lines, int maxRows, MutableComponent text) {
        if (lines.size() < maxRows) {
            lines.add(text);
        }
    }

    @Override
    protected String getTranslationKey() {
        return "damage_observer";
    }
}