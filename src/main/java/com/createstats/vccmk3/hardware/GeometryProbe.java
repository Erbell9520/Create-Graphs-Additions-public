package com.createstats.vccmk3.hardware;

import com.createstats.flight.bridge.SableBridge;
import com.createstats.vccmk3.avionics.SensorSource;
import com.createstats.vccmk3.model.DeviceGeometry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

import javax.annotation.Nullable;

public final class GeometryProbe {
    private GeometryProbe() {}

    public static DeviceGeometry probe(@Nullable Level level, @Nullable BlockPos pos,
                                       @Nullable DeviceCategory category) {
        if (category == DeviceCategory.SIGNAL_LINK) {
            return DeviceGeometry.notSpatial();
        }
        if (level == null || pos == null || category == null) {
            return DeviceGeometry.UNKNOWN;
        }

        return switch (category) {
            case PROPELLER, GYRO_BEARING -> probePropeller(level, pos);
            case CONTROL_SURFACE -> probeSurface(level, pos);
            case WHEEL -> probeWheel(level, pos);
            case SIGNAL_LINK -> DeviceGeometry.notSpatial();
        };
    }

    private static DeviceGeometry probePropeller(Level level, BlockPos pos) {
        BlockEntity be = resolve(level, pos);
        if (be == null) {
            return DeviceGeometry.unavailable("The propeller bearing is not loaded, so its thrust direction is unknown.");
        }
        SableBridge.PropData prop = SableBridge.readProp(be);
        if (prop == null) {
            net.minecraft.core.Direction facing = SableBridge.propellerBlockDirection(be);
            if (facing != null) {
                return DeviceGeometry.pushing(
                        new Vec3(facing.getStepX(), facing.getStepY(), facing.getStepZ()),
                        SensorSource.SENSOR);
            }
            return DeviceGeometry.unavailable("This block did not answer as a propeller.");
        }
        Vector3d dir = prop.thrustDirLocal();
        if (dir == null) {
            return DeviceGeometry.unavailable("The propeller bearing reports no thrust direction yet.");
        }
        return DeviceGeometry.pushing(new Vec3(dir.x, dir.y, dir.z), SensorSource.SENSOR);
    }

    private static DeviceGeometry probeSurface(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        Direction facing = facingOf(state);
        if (facing == null) {
            return DeviceGeometry.unavailable("This bearing has no facing, so its hinge axis cannot be determined.");
        }
        int commandSign = facing.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1 : -1;
        Vec3 axis = new Vec3(facing.getStepX(), facing.getStepY(), facing.getStepZ());
        return DeviceGeometry.swinging(axis, commandSign, SensorSource.DERIVED);
    }

    private static DeviceGeometry probeWheel(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        Direction facing = facingOf(state);
        if (facing == null) {
            return DeviceGeometry.unavailable("This wheel mount has no facing, so its axle cannot be determined.");
        }
        Vec3 axle = new Vec3(facing.getStepX(), facing.getStepY(), facing.getStepZ());
        return DeviceGeometry.swinging(axle, 1, SensorSource.DERIVED);
    }

    @Nullable
    private static Direction facingOf(@Nullable BlockState state) {
        if (state == null) return null;
        Property<?> property = state.getBlock().getStateDefinition().getProperty("facing");
        if (!(property instanceof EnumProperty<?> enumProperty)) return null;
        if (!Direction.class.isAssignableFrom(enumProperty.getValueClass())) return null;
        Comparable<?> value = state.getValue(enumProperty);
        return value instanceof Direction direction ? direction : null;
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
}
