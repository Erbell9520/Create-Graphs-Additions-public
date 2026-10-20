package com.createstats.vccmk3.runtime;

import com.createstats.flight.bridge.SableBridge;
import com.createstats.vccmk3.avionics.SensorSource;
import com.createstats.vccmk3.model.ControlAxis;
import com.createstats.vccmk3.model.VccDevice;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

import javax.annotation.Nullable;
import java.util.List;

public record CraftFrame(
        Vec3 forward,
        Vec3 up,
        Vec3 right,
        Vec3 centreOfMass,
        SensorSource pivotSource,
        String note
) {
    public static final CraftFrame UNKNOWN = new CraftFrame(
            new Vec3(0, 0, -1), new Vec3(0, 1, 0), new Vec3(1, 0, 0), Vec3.ZERO,
            SensorSource.NONE, "The craft frame has not been established.");

    public static CraftFrame of(@Nullable BlockEntity controller, @Nullable Direction facing,
                                @Nullable List<VccDevice> devices) {
        Direction nose = facing == null ? Direction.NORTH : facing;
        Vec3 forward = new Vec3(nose.getStepX(), 0, nose.getStepZ());
        Vec3 up = new Vec3(0, 1, 0);

        Vec3 right = forward.cross(up);

        Vector3d com = controller == null ? null
                : SableBridge.centerOfMassLocal(SableBridge.subLevelAccessOf(controller));
        if (com != null && Double.isFinite(com.x) && Double.isFinite(com.y) && Double.isFinite(com.z)) {
            return new CraftFrame(forward, up, right, new Vec3(com.x, com.y, com.z),
                    SensorSource.SENSOR, "");
        }

        Vec3 centroid = centroidOf(devices);
        if (centroid == null) {
            return new CraftFrame(forward, up, right, Vec3.ZERO, SensorSource.NONE,
                    "No centre of mass. Assemble the craft, or link some hardware, before mixing.");
        }
        return new CraftFrame(forward, up, right, centroid, SensorSource.DERIVED,
                "Pivoting about the middle of your hardware. Assemble the craft for the real centre of mass.");
    }

    @Nullable
    public Vec3 reference(@Nullable ControlAxis axis) {
        if (axis == null) return null;
        return switch (axis) {
            case FORWARD -> forward;
            case UP -> up;
            case YAW -> up.scale(-1.0);
            case PITCH -> right;
            case ROLL -> forward;
            case AUX -> null;
        };
    }

    public boolean hasPivot() {
        return pivotSource.hasReading();
    }

    public Vec3 armTo(net.minecraft.core.BlockPos pos) {
        return new Vec3(pos.getX() + 0.5 - centreOfMass.x,
                pos.getY() + 0.5 - centreOfMass.y,
                pos.getZ() + 0.5 - centreOfMass.z);
    }

    @Nullable
    private static Vec3 centroidOf(@Nullable List<VccDevice> devices) {
        if (devices == null || devices.isEmpty()) return null;
        double x = 0, y = 0, z = 0;
        int n = 0;
        for (VccDevice device : devices) {
            if (device == null || device.actuatorPos() == null) continue;
            x += device.actuatorPos().getX() + 0.5;
            y += device.actuatorPos().getY() + 0.5;
            z += device.actuatorPos().getZ() + 0.5;
            n++;
        }
        return n == 0 ? null : new Vec3(x / n, y / n, z / n);
    }
}
