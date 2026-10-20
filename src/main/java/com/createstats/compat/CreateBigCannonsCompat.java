package com.createstats.compat;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.ModList;

public final class CreateBigCannonsCompat {
    public static final String MOD_ID = "createbigcannons";

    private CreateBigCannonsCompat() {
    }

    public static boolean isAvailable() {
        return ModList.get().isLoaded(MOD_ID);
    }

    public static boolean isCreateBigCannonsBlock(BlockState state) {
        return MOD_ID.equals(blockId(state).getNamespace());
    }

    public static boolean isCreateBigCannonsItem(ItemStack stack) {
        return !stack.isEmpty() && MOD_ID.equals(itemId(stack).getNamespace());
    }

    public static boolean isLikelyWeaponBlock(BlockState state) {
        if (!isCreateBigCannonsBlock(state)) {
            return false;
        }
        return classifyPart(state) != WeaponPart.UNKNOWN || weaponKind(state) != WeaponKind.NONE;
    }

    public static WeaponKind weaponKind(BlockState state) {
        if (!isCreateBigCannonsBlock(state)) {
            return WeaponKind.NONE;
        }
        String path = blockPath(state);
        if (path.contains("autocannon")) {
            return WeaponKind.AUTOCANNON;
        }
        if (path.contains("drop_mortar")) {
            return WeaponKind.DROP_MORTAR;
        }
        if (path.contains("cannon_mount") || path.contains("fixed_cannon_mount") || path.contains("cannon_carriage")) {
            return WeaponKind.MOUNT;
        }
        if (path.contains("cannon_loader") || path.contains("ram_head") || path.contains("worm_head")) {
            return WeaponKind.LOADER;
        }
        if (path.contains("cannon") || path.contains("breech") || path.contains("shot") || path.contains("shell") || path.contains("cartridge")) {
            return WeaponKind.BIG_CANNON;
        }
        return WeaponKind.NONE;
    }

    public static WeaponPart classifyPart(BlockState state) {
        if (!isCreateBigCannonsBlock(state)) {
            return WeaponPart.UNKNOWN;
        }
        String path = blockPath(state);
        if (path.contains("autocannon_recoil_spring")) {
            return WeaponPart.RECOIL;
        }
        if (path.contains("ammo_container")) {
            return WeaponPart.AMMO_CONTAINER;
        }
        if (path.contains("cannon_mount") || path.contains("fixed_cannon_mount") || path.contains("cannon_carriage")) {
            return WeaponPart.MOUNT;
        }
        if (path.contains("cannon_loader")) {
            return WeaponPart.LOADER;
        }
        if (path.contains("ram_head")) {
            return WeaponPart.RAM_HEAD;
        }
        if (path.contains("worm_head")) {
            return WeaponPart.WORM_HEAD;
        }
        if (path.contains("breech")) {
            return WeaponPart.BREECH;
        }
        if (path.contains("cannon_end") || path.contains("drop_mortar_end")) {
            return WeaponPart.MUZZLE;
        }
        if (path.contains("cannon_barrel") || path.contains("autocannon_barrel")) {
            return WeaponPart.BARREL;
        }
        if (path.contains("cannon_chamber") || path.contains("cannon_layer")) {
            return WeaponPart.CHAMBER;
        }
        if (path.contains("big_cartridge") || path.contains("powder_charge") || path.contains("propellant")) {
            return WeaponPart.PROPELLANT;
        }
        if (path.contains("shot") || path.contains("shell") || path.contains("mortar_stone")) {
            return WeaponPart.PROJECTILE;
        }
        return WeaponPart.UNKNOWN;
    }

    public static String displayName(BlockState state) {
        return BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath().replace('_', ' ');
    }

    public static WeaponPart classifyPayloadItem(ItemStack stack) {
        if (!isCreateBigCannonsItem(stack)) {
            return WeaponPart.UNKNOWN;
        }
        String path = itemPath(stack);
        if (path.contains("powder_charge") || path.contains("propellant")) {
            return WeaponPart.PROPELLANT;
        }
        if (path.contains("big_cartridge") && !path.contains("autocannon")) {
            return WeaponPart.PROPELLANT;
        }
        if (path.contains("round") || path.contains("shot") || path.contains("shell") || path.contains("cartridge") || path.contains("tracer")) {
            return WeaponPart.PROJECTILE;
        }
        return WeaponPart.UNKNOWN;
    }

    public static String materialHint(BlockState state) {
        String path = blockPath(state);
        if (path.contains("nethersteel")) {
            return "Nethersteel";
        }
        if (path.contains("cast_iron")) {
            return "Cast Iron";
        }
        if (path.contains("wrought_iron")) {
            return "Wrought Iron";
        }
        if (path.contains("bronze")) {
            return "Bronze";
        }
        if (path.contains("steel")) {
            return "Steel";
        }
        if (path.contains("log")) {
            return "Log";
        }
        return "Unknown";
    }

    public static boolean hasBooleanProperty(BlockState state, String propertyName, boolean expectedValue) {
        for (Property<?> property : state.getProperties()) {
            if (!propertyName.equals(property.getName())) {
                continue;
            }
            Object value = state.getValue(property);
            return value instanceof Boolean bool && bool == expectedValue;
        }
        return false;
    }

    public static Direction cannonAxisDirection(BlockState state, Direction fallback) {
        for (Property<?> property : state.getProperties()) {
            Object value = state.getValue(property);
            if (value instanceof Direction direction && !direction.getAxis().isVertical()) {
                return direction;
            }
            if (value instanceof Direction.Axis axis && !axis.isVertical()) {
                return axis == Direction.Axis.X ? Direction.EAST : Direction.SOUTH;
            }
        }
        return fallback != null && !fallback.getAxis().isVertical() ? fallback : Direction.NORTH;
    }

    private static String blockPath(BlockState state) {
        return blockId(state).getPath();
    }

    private static ResourceLocation blockId(BlockState state) {
        return BuiltInRegistries.BLOCK.getKey(state.getBlock());
    }

    private static String itemPath(ItemStack stack) {
        return itemId(stack).getPath();
    }

    private static ResourceLocation itemId(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem());
    }

    public enum WeaponKind {
        NONE,
        BIG_CANNON,
        DROP_MORTAR,
        AUTOCANNON,
        MOUNT,
        LOADER
    }

    public enum WeaponPart {
        UNKNOWN,
        BARREL,
        CHAMBER,
        BREECH,
        MUZZLE,
        MOUNT,
        LOADER,
        RAM_HEAD,
        WORM_HEAD,
        RECOIL,
        AMMO_CONTAINER,
        PROJECTILE,
        PROPELLANT
    }
}
