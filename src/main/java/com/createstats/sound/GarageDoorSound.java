package com.createstats.sound;

import com.createstats.block.DoorOpening;
import net.minecraft.util.Mth;

public final class GarageDoorSound {
    public static final int LIGHTEST_AREA = 4;

    public static final int HEAVIEST_AREA = 54;

    private static final float LIGHT_HARDNESS = 2.6f;
    private static final float HEAVY_HARDNESS = 4.4f;

    private GarageDoorSound() {
    }

    public static DoorSound.Kit kit() {
        return DoorSound.kit(DoorOpening.ROLL_UP);
    }

    public static float hardnessFor(int area) {
        final float t = Mth.clamp(
                (float) (area - LIGHTEST_AREA) / (HEAVIEST_AREA - LIGHTEST_AREA), 0.0f, 1.0f);
        return Mth.lerp(t, LIGHT_HARDNESS, HEAVY_HARDNESS);
    }

    public static float pitch(int area) {
        return DoorSound.pitchForHardness(hardnessFor(area));
    }

    public static float volume(int area) {
        return DoorSound.volumeForHardness(hardnessFor(area));
    }
}
