package com.createstats.vccmk3.client;

import net.createmod.catnip.outliner.Outliner;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;

public final class VccMk3Highlight {
    private static final Object SLOT = new Object();

    private static final long DURATION_MS = 9_000L;

    private static final int COLOUR = 0xFF62D5FF;

    @Nullable private static BlockPos target;
    private static long expiresAt;

    private VccMk3Highlight() {}

    public static boolean canShow(@Nullable BlockPos pos) {
        Minecraft minecraft = Minecraft.getInstance();
        if (pos == null || minecraft.level == null) return false;
        if (!minecraft.level.isLoaded(pos)) return false;
        return !minecraft.level.getBlockState(pos).isAir();
    }

    public static void show(@Nullable BlockPos pos) {
        if (!canShow(pos)) {
            clear();
            return;
        }
        target = pos;
        expiresAt = System.currentTimeMillis() + DURATION_MS;
    }

    public static void tick() {
        if (target == null) return;
        if (System.currentTimeMillis() > expiresAt) {
            clear();
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || !minecraft.level.isLoaded(target)) {
            clear();
            return;
        }

        Outliner.getInstance()
                .showAABB(SLOT, new AABB(target).inflate(0.02))
                .colored(COLOUR)
                .disableLineNormals()
                .lineWidth(1 / 16f);
    }

    public static void clear() {
        if (target == null) return;
        target = null;
        Outliner.getInstance().remove(SLOT);
    }
}
