package com.createstats.util;

import com.createstats.topology.NodeType;
import com.simibubi.create.content.kinetics.KineticNetwork;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

public final class KineticNetworkMetrics
{
    private static final float EPSILON = 0.001f;

    private KineticNetworkMetrics() {
    }

    public static boolean supportsStressReadout(BlockEntity be) {
        if (!(be instanceof KineticBlockEntity)) {
            return false;
        }
        return supportsStressReadout(classify(be));
    }

    public static boolean supportsStressReadout(NodeType nodeType) {
        return switch (nodeType) {
            case SHAFT, BELT, CRUSHING_WHEEL -> false;
            default -> true;
        };
    }

    public static Readout read(KineticBlockEntity kinetic) {
        float networkStress = 0.0f;
        float networkCapacity = 0.0f;
        if (kinetic.hasNetwork()) {
            final KineticNetwork network = kinetic.getOrCreateNetwork();
            if (network != null) {
                networkStress = Math.max(0.0f, network.calculateStress());
                networkCapacity = Math.max(0.0f, network.calculateCapacity());
            }
        }
        final float stressPercent = networkCapacity > EPSILON ? networkStress / networkCapacity * 100.0f : 0.0f;
        final float remainingCapacity = Math.max(0.0f, networkCapacity - networkStress);
        return new Readout(stressPercent, networkStress, networkCapacity, remainingCapacity, kinetic.isOverStressed());
    }

    public static NodeType classify(BlockEntity be) {
        return NodeType.fromIdentity(be.getBlockState().getBlock().getName().getString(),
                be.getBlockState().getBlock().getClass().getSimpleName(),
                be.getClass().getSimpleName());
    }

    @Nullable
    public static NodeType classifySourceType(String sourceType) {
        if (sourceType == null || sourceType.isBlank()) {
            return null;
        }
        final NodeType nodeType = NodeType.fromIdentity(sourceType, "", "");
        return nodeType == NodeType.OTHER ? null : nodeType;
    }

    public record Readout(float stressPercent, float networkStress, float networkCapacity, float remainingCapacity,
                          boolean overstressed)
    {
    }
}
