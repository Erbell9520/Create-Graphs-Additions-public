package com.createstats.vacuum;

public final class LooseBlocks {
    private LooseBlocks() {
    }

    public static final float MAX_GRAIN_HARDNESS = 1.0f;

    public static final int MIN_LIFT_TICKS = 4;

    public static final int TICKS_PER_HARDNESS = 8;

    public static final int MAX_LIFT_TICKS = 100;

    public static final float NETHERITE_SHOVEL_SPEED = 9.0f;

    public static boolean isLoose(boolean tagged, boolean falls, boolean hasBlockEntity, float hardness) {
        if (hasBlockEntity || hardness < 0.0f || Float.isNaN(hardness)) {
            return false;
        }
        return tagged || falls && hardness <= MAX_GRAIN_HARDNESS;
    }

    public static int ticksToLift(float hardness, boolean shovelTool, boolean needsTool) {
        final int dig = digTicks(hardness, shovelTool ? NETHERITE_SHOVEL_SPEED : 1.0f, shovelTool || !needsTool);
        return Math.min(ticksBefore(hardness), dig);
    }

    static int ticksBefore(float hardness) {
        final float h = hardness > 0.0f ? hardness : 0.0f;
        return Math.min(MAX_LIFT_TICKS, MIN_LIFT_TICKS + Math.round(h * TICKS_PER_HARDNESS));
    }

    static int digTicks(float hardness, float speed, boolean harvests) {
        if (!(hardness > 0.0f)) {
            return 1;
        }
        final float perTick = speed / hardness / (harvests ? 30.0f : 100.0f);
        float progress = 0.0f;
        int ticks = 0;
        while (progress < 1.0f && ticks < MAX_LIFT_TICKS) {
            progress += perTick;
            ticks++;
        }
        return ticks;
    }

    public static int crackStage(int ticks, int needed) {
        if (needed <= 0 || ticks <= 0) {
            return 0;
        }
        return Math.min(9, (int) ((long) ticks * 10 / needed));
    }
}
