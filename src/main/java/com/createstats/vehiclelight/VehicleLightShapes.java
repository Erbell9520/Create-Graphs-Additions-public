package com.createstats.vehiclelight;

import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.EnumMap;
import java.util.Map;

public final class VehicleLightShapes {
    private static final Map<VehicleLightKind, VoxelShape> UP = new EnumMap<>(VehicleLightKind.class);

    private VehicleLightShapes() {
    }

    public static synchronized VoxelShape up(VehicleLightKind kind) {
        return UP.computeIfAbsent(kind, k -> {
            final float[] f = k.flange();
            final float[] b = k.body();
            return Shapes.or(Shapes.box(f[0] / 16.0, 0.0, f[1] / 16.0, f[2] / 16.0, 1.0 / 16.0, f[3] / 16.0),
                    Shapes.box(b[0] / 16.0, 1.0 / 16.0, b[1] / 16.0, b[2] / 16.0, k.bezelTop() / 16.0, b[3] / 16.0));
        });
    }
}
