package com.createstats.vccmk3.hardware;

import com.createstats.flight.bridge.SableBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.Locale;

public final class HardwareClassifier {
    private HardwareClassifier() {}

    @Nullable
    public static DeviceCategory classify(@Nullable Level level, @Nullable BlockPos pos) {
        if (level == null || pos == null) return null;
        BlockEntity be = resolve(level, pos);

        SableBridge.PropData prop = be != null ? SableBridge.readProp(be) : null;
        if (prop != null) {
            return prop.isGyro() ? DeviceCategory.GYRO_BEARING : DeviceCategory.PROPELLER;
        }
        // standalone propellers are thrust too. "cannot be driven" while visibly pushing was embarrassing

        if (be != null && SableBridge.matchesAnyPropeller(be)) {
            return DeviceCategory.PROPELLER;
        }
        if (be != null && SableBridge.matchesSwivelBearing(be)) {
            return DeviceCategory.CONTROL_SURFACE;
        }

        BlockState state = level.getBlockState(pos);
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        String namespace = id.getNamespace().toLowerCase(Locale.ROOT);
        String path = id.getPath().toLowerCase(Locale.ROOT);
        String blockClass = state.getBlock().getClass().getName().toLowerCase(Locale.ROOT);
        String beClass = be != null ? be.getClass().getName().toLowerCase(Locale.ROOT) : "";

        if ("simulated".equals(namespace) && path.equals("swivel_bearing")) {
            return DeviceCategory.CONTROL_SURFACE;
        }
        if (isWheelMount(namespace, path, blockClass, beClass)) {
            return DeviceCategory.WHEEL;
        }
        if (isRedstoneLink(namespace, path, blockClass, beClass)) {
            return DeviceCategory.SIGNAL_LINK;
        }
        if (blockClass.contains("propellerbearing") || beClass.contains("propellerbearing")
                || blockClass.contains("propeller_bearing") || beClass.contains("propeller_bearing")) {
            return DeviceCategory.PROPELLER;
        }
        return null;
    }

    public static boolean isGovernor(@Nullable Level level, @Nullable BlockPos pos) {
        if (level == null || pos == null) return false;
        return resolve(level, pos) instanceof com.createstats.flight.blockentity.RpmGovernorBE;
    }

    public static String describe(@Nullable Level level, @Nullable BlockPos pos) {
        if (level == null || pos == null) return "nothing";
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock());
        return id == null ? "unknown block" : id.getPath().replace('_', ' ');
    }

    @Nullable
    private static BlockEntity resolve(Level level, BlockPos pos) {
        BlockEntity direct = level.getBlockEntity(pos);
        if (direct != null) return direct;
        try {
            return SableBridge.getBlockEntityOnContraption(level, pos);
        } catch (RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    private static boolean isWheelMount(String namespace, String path, String blockClass, String beClass) {
        if ("offroad".equals(namespace) && (path.equals("wheel_mount") || path.endsWith("/wheel_mount"))) {
            return true;
        }
        boolean offroad = blockClass.contains("dev.ryanhcode.offroad") || beClass.contains("dev.ryanhcode.offroad");
        boolean wheel = blockClass.contains("wheel_mount") || beClass.contains("wheel_mount")
                || blockClass.contains("wheelmount") || beClass.contains("wheelmount");
        return offroad && wheel;
    }

    private static boolean isRedstoneLink(String namespace, String path, String blockClass, String beClass) {
        if ("create".equals(namespace) && path.contains("redstone_link")) return true;
        return blockClass.contains("redstonelink") || beClass.contains("redstonelink");
    }
}
