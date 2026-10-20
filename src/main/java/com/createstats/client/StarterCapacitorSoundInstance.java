package com.createstats.client;

import com.createstats.blockentity.StarterCapacitorBE;
import com.createstats.power.CapacitorDischarge;
import com.createstats.power.StarterMode;
import com.createstats.power.StarterTrigger;
import com.createstats.registry.ModSounds;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

public class StarterCapacitorSoundInstance extends AbstractTickableSoundInstance {
    private final StarterCapacitorBE be;
    private final boolean whine;

    private StarterCapacitorSoundInstance(StarterCapacitorBE be, boolean whine) {
        super(whine ? ModSounds.STARTER_CAPACITOR_WHINE.get() : ModSounds.STARTER_CAPACITOR_HUM.get(),
                SoundSource.BLOCKS, RandomSource.create());
        this.be = be;
        this.whine = whine;
        final BlockPos pos = be.getBlockPos();
        this.x = pos.getX() + 0.5;
        this.y = pos.getY() + 0.9;
        this.z = pos.getZ() + 0.5;
        this.looping = true;
        this.delay = 0;
        this.volume = whine ? 1.0f : 0.12f;
        this.pitch = whine ? CapacitorDischarge.whinePitch(1.0f) : 0.8f;
    }

    public static void start(StarterCapacitorBE be, boolean whine) {
        Minecraft.getInstance().getSoundManager().play(new StarterCapacitorSoundInstance(be, whine));
    }

    @Override
    public void tick() {
        if (be.isRemoved() || be.getLevel() == null) {
            stopNow();
            return;
        }
        final StarterTrigger t = be.trigger();
        if (whine) {
            if (t.phase() != StarterTrigger.Phase.FIRING) {
                fadeOut();
                return;
            }
            final float r = CapacitorDischarge.rampAt(t.phaseTick());
            this.pitch = CapacitorDischarge.whinePitch(r);
            this.volume = 0.35f + 0.65f * r;
        } else {
            final boolean counting = t.phase() == StarterTrigger.Phase.IDLE && t.shortTicks() > 0
                    && be.mode() == StarterMode.AUTO && be.port().stored() >= CapacitorDischarge.CAPACITY;
            if (!counting) {
                fadeOut();
                return;
            }
            final float u = t.shortTicks() / (float) StarterTrigger.SUSTAIN_TICKS;
            this.pitch = 0.8f + 0.5f * u;
            this.volume = 0.12f + 0.55f * u;
        }
    }

    private void fadeOut() {
        this.volume = Math.max(0.0f, this.volume - (whine ? 0.12f : 0.06f));
        if (this.volume <= 0.001f) {
            stopNow();
        }
    }

    private void stopNow() {
        if (whine) {
            be.setWhinePlaying(false);
        } else {
            be.setHumPlaying(false);
        }
        this.stop();
    }
}
