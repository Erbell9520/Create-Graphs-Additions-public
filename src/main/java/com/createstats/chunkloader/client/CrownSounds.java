package com.createstats.chunkloader.client;

import com.createstats.blockentity.SparkCrownBE;
import com.createstats.chunkloader.look.CrownVoice;
import com.createstats.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;

// silent in ponder, the scene sits near origin and would play from nowhere
public final class CrownSounds {
    public static final double EARSHOT = 24.0;

    private static final Map<SparkCrownBE, Voiced> VOICES = new WeakHashMap<>();

    private static final class Voiced {
        final CrownVoice voice = new CrownVoice();
        boolean humPlaying;
        WeakReference<Hum> hum = new WeakReference<>(null);
        int age;
    }

    private static final int START_GRACE = 5;

    private CrownSounds() {
    }

    public static void tick(SparkCrownBE be) {
        final Minecraft mc = Minecraft.getInstance();
        final Level level = be.getLevel();
        if (level == null || level != mc.level) {
            return;
        }
        final Voiced v = VOICES.computeIfAbsent(be, k -> new Voiced());
        final CrownVoice voice = v.voice;
        voice.tick(be.crown());
        if (!inEarshot(mc, be.getBlockPos(), EARSHOT)) {
            return;
        }
        v.age++;

        if (v.humPlaying && refused(mc, v.hum.get(), v.age)) {
            v.humPlaying = false;
        }
        if (voice.hum() > 0f && !v.humPlaying) {
            v.humPlaying = true;
            final Hum hum = new Hum(be, v, v.age);
            v.hum = new WeakReference<>(hum);
            mc.getSoundManager().play(hum);
        }
        final BlockPos p = be.getBlockPos();
        for (int i = 0; i < voice.shotCount(); i++) {
            final CrownVoice.Shot s = voice.shot(i);

            level.playLocalSound(p.getX() + 0.5, p.getY() + 0.9, p.getZ() + 0.5,
                    switch (s.sound()) {
                        case LATCH -> ModSounds.SPARK_CROWN_LATCH.get();
                        case UNLATCH -> ModSounds.SPARK_CROWN_UNLATCH.get();
                        case LAND -> ModSounds.SPARK_CROWN_LAND.get();
                    },
                    SoundSource.BLOCKS, s.volume(), s.pitch(), false);
        }
    }

    private static boolean refused(Minecraft mc, Hum hum, int age) {
        return hum == null || (age - hum.startedAt > START_GRACE && !mc.getSoundManager().isActive(hum));
    }

    private static boolean inEarshot(Minecraft mc, BlockPos p, double reach) {
        final Entity listener = mc.cameraEntity;
        return listener != null
                && listener.distanceToSqr(p.getX() + 0.5, p.getY() + 1.0, p.getZ() + 0.5) <= reach * reach;
    }

    private static final class Hum extends AbstractTickableSoundInstance {
        private static final float STEP = CrownVoice.HUM_VOLUME / CrownVoice.HUM_FADE;

        private final SparkCrownBE be;
        private final Voiced v;
        final int startedAt;

        Hum(SparkCrownBE be, Voiced v, int startedAt) {
            super(ModSounds.SPARK_CROWN_HUM.get(), SoundSource.BLOCKS, RandomSource.create());
            this.be = be;
            this.v = v;
            this.startedAt = startedAt;
            final BlockPos p = be.getBlockPos();
            this.x = p.getX() + 0.5;
            this.y = p.getY() + 1.2;
            this.z = p.getZ() + 0.5;
            this.looping = true;
            this.delay = 0;
            this.volume = STEP;
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
            final float want = v.voice.hum();
            this.volume += Math.max(-STEP, Math.min(STEP, want - this.volume));
            if (want <= 0f && this.volume <= 1e-5f) {
                end();
            }
        }

        private void end() {
            v.humPlaying = false;
            stop();
        }
    }
}
