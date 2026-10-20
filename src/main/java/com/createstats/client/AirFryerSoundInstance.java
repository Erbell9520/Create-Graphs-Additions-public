package com.createstats.client;

import com.createstats.blockentity.AirFryerBE;
import com.createstats.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;

public class AirFryerSoundInstance extends AbstractTickableSoundInstance {
    private static final double MAX_DISTANCE_SQR = 8.0 * 8.0;

    private static final float TARGET_VOLUME = 0.10f;

    private final AirFryerBE be;

    private AirFryerSoundInstance(AirFryerBE be) {
        super(ModSounds.AIR_FRYER_FAN.get(), SoundSource.BLOCKS, RandomSource.create());
        this.be = be;
        final BlockPos pos = be.getBlockPos();
        this.x = pos.getX() + 0.5;
        this.y = pos.getY() + 0.5;
        this.z = pos.getZ() + 0.5;
        this.looping = true;
        this.delay = 0;
        // must start non-zero, the engine culls a 0 volume instance. the flywheel was silent for a whole release because of this

        this.volume = 0.02f;
        this.pitch = 1.0f;
    }

    public static void startIfNeeded(AirFryerBE be) {
        if (be.isSoundPlaying()) {
            return;
        }
        be.setSoundPlaying(true);
        Minecraft.getInstance().getSoundManager().play(new AirFryerSoundInstance(be));
    }

    @Override
    public void tick() {
        final Player player = Minecraft.getInstance().player;
        if (be.isRemoved() || be.getLevel() == null || player == null) {
            stopNow();
            return;
        }
        final double distSqr = player.distanceToSqr(this.x, this.y, this.z);
        if (!be.isCooking() || distSqr > MAX_DISTANCE_SQR) {
            fadeOut();
            return;
        }
        this.volume += (TARGET_VOLUME - this.volume) * 0.12f;
    }

    private void fadeOut() {
        this.volume = Math.max(0.0f, this.volume - 0.03f);
        if (this.volume <= 0.001f) {
            stopNow();
        }
    }

    private void stopNow() {
        be.setSoundPlaying(false);
        this.stop();
    }
}
