package com.createstats.client;

import com.createstats.ModConfiguration;
import com.createstats.blockentity.FlywheelVoice;
import com.createstats.blockentity.KineticFlywheelBE;
import com.createstats.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;

// ONE loop for every flywheel. one per block comb filtered into that awful jet sound
public final class FlywheelSoundInstance extends AbstractTickableSoundInstance {
    private static final int GRACE_TICKS = 3;

    private static final int RETRY_TICKS = 20;

    private static FlywheelSoundInstance current;
    private static long retryAt = Long.MIN_VALUE;

    private static double weight;
    private static double weightedX;
    private static double weightedY;
    private static double weightedZ;
    private static double weightedPitch;
    private static float loudest;
    private static int voices;

    private float targetVolume;
    private float targetPitch = 1.0f;
    private int quietTicks;
    private boolean placed;

    private FlywheelSoundInstance() {
        super(ModSounds.FLYWHEEL_LOOP.get(), SoundSource.BLOCKS, SoundInstance.createUnseededRandom());
        this.looping = true;
        this.delay = 0;
        this.volume = 0.0f;
        this.pitch = 1.0f;
        this.relative = false;
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }

    public static void report(KineticFlywheelBE be) {
        float activity = be.getHumActivity();
        if (activity < FlywheelVoice.ACTIVITY_FLOOR || !ensurePlaying()) {
            return;
        }
        BlockPos pos = be.getBlockPos();
        weight += activity;
        weightedX += activity * (pos.getX() + 0.5);
        weightedY += activity * (pos.getY() + 0.5);
        weightedZ += activity * (pos.getZ() + 0.5);
        weightedPitch += activity * FlywheelVoice.humPitch(be.getRotorFraction());
        loudest = Math.max(loudest, activity);
        voices++;
    }

    private static boolean ensurePlaying() {
        Minecraft mc = Minecraft.getInstance();
        SoundManager sounds = mc.getSoundManager();
        if (current != null && !current.isStopped() && sounds.isActive(current)) {
            return true;
        }
        long now = mc.level != null ? mc.level.getGameTime() : 0L;
        if (now < retryAt) {
            return false;
        }
        clearReports();
        FlywheelSoundInstance fresh = new FlywheelSoundInstance();
        sounds.play(fresh);
        if (!sounds.isActive(fresh)) {
            retryAt = now + RETRY_TICKS;
            current = null;
            return false;
        }
        current = fresh;
        return true;
    }

    private static void clearReports() {
        weight = 0.0;
        weightedX = 0.0;
        weightedY = 0.0;
        weightedZ = 0.0;
        weightedPitch = 0.0;
        loudest = 0.0f;
        voices = 0;
    }

    @Override
    public void tick() {
        if (voices > 0 && weight > 0.0) {
            targetVolume = Math.min(1.0f, FlywheelVoice.humVolume(loudest, configVolume(), voices));
            targetPitch = (float) (weightedPitch / weight);
            double tx = weightedX / weight;
            double ty = weightedY / weight;
            double tz = weightedZ / weight;
            if (!placed) {
                this.x = tx;
                this.y = ty;
                this.z = tz;
                this.pitch = targetPitch;
                placed = true;
            } else {
                this.x += (tx - this.x) * 0.25;
                this.y += (ty - this.y) * 0.25;
                this.z += (tz - this.z) * 0.25;
            }
            quietTicks = 0;
        } else if (++quietTicks > GRACE_TICKS) {
            targetVolume = 0.0f;
        }
        clearReports();

        this.volume += (targetVolume - this.volume) * (targetVolume > this.volume ? 0.06f : 0.12f);
        this.pitch += (targetPitch - this.pitch) * 0.08f;
        if (targetVolume <= 0.0f && this.volume < 0.004f) {
            this.volume = 0.0f;
            stop();
            if (current == this) {
                current = null;
            }
        }
    }

    static float configVolume() {
        return (float) (double) ModConfiguration.CLIENT.flywheelSoundVolume.get();
    }
}
