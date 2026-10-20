package com.createstats.driverhelmet;

import com.createstats.CreateStats;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

public final class OffroadWheels {
    private static final String WHEEL_MOUNT = "dev.ryanhcode.offroad.content.blocks.wheel_mount.WheelMountBlockEntity";
    private static final ResourceLocation TIRE = ResourceLocation.fromNamespaceAndPath("offroad", "tire");

    private static Class<?> mountClass;
    private static Field friction;
    private static Field extension;
    private static Method heldItem;
    private static DataComponentType<?> tireType;
    private static Method radius;
    private static boolean resolved;
    private static boolean broken;

    private OffroadWheels() {
    }

    public static boolean isWheel(BlockEntity be) {
        return be != null && resolve() && mountClass.isInstance(be);
    }

    public static double radius(BlockEntity be) {
        if (!isWheel(be)) {
            return Double.NaN;
        }
        try {
            final ItemStack stack = (ItemStack) heldItem.invoke(be);
            final Object tire = stack == null || tireType == null ? null : stack.get(tireType);
            return tire == null ? Double.NaN : ((Number) radius.invoke(tire)).doubleValue();
        } catch (ReflectiveOperationException | RuntimeException e) {
            fail(e);
            return Double.NaN;
        }
    }

    public static double friction(BlockEntity be) {
        return read(friction, be);
    }

    public static double extension(BlockEntity be) {
        return read(extension, be);
    }

    private static double read(Field field, BlockEntity be) {
        if (!isWheel(be)) {
            return Double.NaN;
        }
        try {
            return field.getDouble(be);
        } catch (ReflectiveOperationException | RuntimeException e) {
            fail(e);
            return Double.NaN;
        }
    }

    private static synchronized boolean resolve() {
        if (resolved) {
            return !broken;
        }
        resolved = true;
        try {
            mountClass = Class.forName(WHEEL_MOUNT);
            friction = mountClass.getDeclaredField("touchingFriction");
            friction.setAccessible(true);
            extension = mountClass.getDeclaredField("extension");
            extension.setAccessible(true);
            heldItem = mountClass.getMethod("getHeldItem");
            tireType = BuiltInRegistries.DATA_COMPONENT_TYPE.get(TIRE);
            final Class<?> tireLike = Class.forName("dev.ryanhcode.offroad.content.components.TireLike");
            radius = tireLike.getMethod("radius");
        } catch (ReflectiveOperationException | RuntimeException e) {
            broken = true;
            CreateStats.LOGGER.warn("[Driver Helmet] Create Offroad's wheel mount could not be read; "
                    + "the HUD's per-wheel figures will show dashes: {}", e.toString());
        }
        return !broken;
    }

    private static void fail(Exception e) {
        if (!broken) {
            broken = true;
            CreateStats.LOGGER.warn("[Driver Helmet] reading a Create Offroad wheel mount failed; "
                    + "per-wheel figures off from now on: {}", e.toString());
        }
    }
}
