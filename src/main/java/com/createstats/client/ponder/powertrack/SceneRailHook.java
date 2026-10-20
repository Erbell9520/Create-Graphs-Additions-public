package com.createstats.client.ponder.powertrack;

import com.createstats.powertrack.client.ClientRail;
import com.createstats.powertrack.core.RailLayout;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;

final class SceneRailHook implements ClientRail.SceneRail {
    private final RailLayout layout;
    private final int carX0;
    private final int carZ;
    private final int carY;
    private final Map<Long, Integer> collectors = new HashMap<>();
    private double carX;
    private double carXOld;
    private final double[] angle;
    private final double[] angleOld;

    SceneRailHook(RailLayout layout, int carX0, int carY, int carZ, int[] collectorsLocal) {
        this.layout = layout;
        this.carX0 = carX0;
        this.carY = carY;
        this.carZ = carZ;
        this.angle = new double[collectorsLocal.length];
        this.angleOld = new double[collectorsLocal.length];
        for (int i = 0; i < collectorsLocal.length; i++) {
            collectors.put(BlockPos.asLong(carX0 + collectorsLocal[i], carY, carZ), i);
        }
    }

    void update(RailStory.Frame frame) {
        carXOld = frame.carXOld();
        carX = frame.carX();
        for (int i = 0; i < angle.length && i < frame.shoes().length; i++) {
            angleOld[i] = frame.shoes()[i].angleOld();
            angle[i] = frame.shoes()[i].angle();
        }
    }

    @Override
    public RailLayout layout() {
        return layout;
    }

    @Override
    public Vec3 shift(BlockPos local, float partialTicks) {
        if (local.getY() < carY || local.getY() > carY + 1 || local.getZ() != carZ
                || local.getX() < carX0 || local.getX() >= carX0 + PowerTrackStage.CAR_LENGTH) {
            return Vec3.ZERO;
        }
        return new Vec3(Mth.lerp(partialTicks, carXOld, carX), 0, 0);
    }

    @Override
    public int trailSign(BlockPos local) {
        return PowerTrackStage.trailSign(local.getX() - carX0);
    }

    @Override
    public double angle(BlockPos local, float partialTicks) {
        final Integer i = collectors.get(local.asLong());
        if (i == null) {
            return Double.NaN;
        }
        return Mth.lerp(partialTicks, angleOld[i], angle[i]);
    }
}
