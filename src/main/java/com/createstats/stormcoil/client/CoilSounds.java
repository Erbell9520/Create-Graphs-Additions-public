package com.createstats.stormcoil.client;

import com.createstats.blockentity.StormCoilBE;
import com.createstats.registry.ModSounds;
import com.createstats.stormcoil.CoilVoice;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Supplier;

public final class CoilSounds {
    public static final double EARSHOT = 32.0;

    private static final int START_GRACE = 5;

    private static final Map<StormCoilBE, Voiced> VOICES = new WeakHashMap<>();

    private static final class Voiced {
        final CoilVoice voice = new CoilVoice();
        final Slot hum = new Slot();
        final Slot run = new Slot();
        int age;
    }

    private static final class Slot {
        boolean playing;
        WeakReference<Loop> loop = new WeakReference<>(null);
    }

    private CoilSounds() {
    }

    public static void tick(StormCoilBE be) {
        final Minecraft mc = Minecraft.getInstance();
        final Level level = be.getLevel();
        if (level == null || level != mc.level) {
            return;
        }
        final Voiced v = VOICES.computeIfAbsent(be, k -> new Voiced());
        final long now = level.getGameTime();
        v.voice.tick(now, be.shownCapacitor() + be.shownBank(), be.shownIn() > 0, be.windingUp(now), be.lastZap(),
                be.heldFromArcing());
        if (!inEarshot(mc, be.getBlockPos(), EARSHOT)) {
            return;
        }
        v.age++;

        keep(mc, be, v, v.hum, ModSounds.STORM_COIL_HUM::get, () -> v.voice.hum(), CoilVoice.HUM_VOLUME,
                CoilVoice.HUM_FADE, CoilVoice.HUM_FADE, 0.6);
        keep(mc, be, v, v.run, ModSounds.STORM_COIL_RUN::get, () -> v.voice.run(), CoilVoice.RUN_VOLUME,
                CoilVoice.RUN_IN, CoilVoice.RUN_OUT, 0.4);
    }

    private static void keep(Minecraft mc, StormCoilBE be, Voiced v, Slot slot, Supplier<SoundEvent> sound,
                             Supplier<Float> want, float full, int in, int out, double lift) {
        final Loop playing = slot.loop.get();
        if (slot.playing && (playing == null
                || (v.age - playing.startedAt > START_GRACE && !mc.getSoundManager().isActive(playing)))) {
            slot.playing = false;
        }
        if (want.get() > 0f && !slot.playing) {
            slot.playing = true;
            final Loop loop = new Loop(be, slot, sound.get(), want, full, in, out, lift, v.age);
            slot.loop = new WeakReference<>(loop);
            mc.getSoundManager().play(loop);
        }
    }

    private static boolean inEarshot(Minecraft mc, BlockPos p, double reach) {
        final Entity listener = mc.cameraEntity;
        return listener != null
                && listener.distanceToSqr(p.getX() + 0.5, p.getY() + 1.0, p.getZ() + 0.5) <= reach * reach;
    }

    private static final class Loop extends AbstractTickableSoundInstance {
        private final StormCoilBE be;
        private final Slot slot;
        private final Supplier<Float> want;
        private final float full;
        private final int in;
        private final int out;
        final int startedAt;

        Loop(StormCoilBE be, Slot slot, SoundEvent sound, Supplier<Float> want, float full, int in, int out,
             double lift, int startedAt) {
            super(sound, SoundSource.BLOCKS, RandomSource.create());
            this.be = be;
            this.slot = slot;
            this.want = want;
            this.full = full;
            this.in = in;
            this.out = out;
            this.startedAt = startedAt;
            final BlockPos p = be.getBlockPos();
            this.x = p.getX() + 0.5;
            this.y = p.getY() + lift;
            this.z = p.getZ() + 0.5;
            this.looping = true;
            this.delay = 0;
            this.volume = full / Math.max(1, in);
            this.pitch = 1.0f;
        }

        @Override
        public void tick() {
            final Minecraft mc = Minecraft.getInstance();
            final Level level = be.getLevel();
            if (be.isRemoved() || level == null || level != mc.level || level.getBlockEntity(be.getBlockPos()) != be
                    || !inEarshot(mc, be.getBlockPos(), EARSHOT + 8.0)) {
                end();
                return;
            }
            final float w = want.get();
            this.volume = CoilVoice.ease(this.volume, w, full, in, out);
            if (w <= 0f && this.volume <= 1e-5f) {
                end();
            }
        }

        private void end() {
            slot.playing = false;
            stop();
        }
    }
}
