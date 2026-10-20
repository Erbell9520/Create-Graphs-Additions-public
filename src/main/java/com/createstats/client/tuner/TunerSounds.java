package com.createstats.client.tuner;

import com.createstats.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;

public final class TunerSounds {
    private TunerSounds() {
    }

    public static void sweep(double fraction) {
        final double f = Math.max(0.0, Math.min(1.0, fraction));
        Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(ModSounds.TUNER_SWEEP.get(), (float) (0.9 + 0.25 * f), 0.8f));
    }

    public static void detent(int notch, int notches) {
        final double f = notches <= 1 ? 0.5 : notch / (double) (notches - 1);
        Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(ModSounds.TUNER_DETENT.get(), (float) (0.8 + 0.6 * f), 0.5f));
    }

    public static void stop() {
        Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(ModSounds.TUNER_DETENT.get(), 0.55f, 0.6f));
    }
}
