package com.createstats.client;

import com.createstats.blockentity.FlywheelVoice;
import com.createstats.blockentity.KineticFlywheelBE;
import com.createstats.registry.ModSounds;
import net.createmod.ponder.api.level.PonderLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

import java.util.ArrayDeque;
import java.util.Iterator;

public final class FlywheelClientFx {
    private static final double EARSHOT_SQR = 16.0 * 16.0;

    private static final double CLUSTER_SQR = 8.0 * 8.0;
    private static final int CLUSTER_TICKS = 40;
    private static final float CHIME_VOLUME = 0.45f;
    private static final float THUNK_VOLUME = 0.5f;

    private record Cue(int level, BlockPos pos, long time, int kind) {
    }

    private static final ArrayDeque<Cue> RECENT = new ArrayDeque<>();

    private FlywheelClientFx() {
    }

    public static boolean isPonder(Level level) {
        return level instanceof PonderLevel;
    }

    public static void tick(KineticFlywheelBE be) {
        Minecraft mc = Minecraft.getInstance();
        Level level = be.getLevel();
        if (level == null || level != mc.level) {
            return;
        }

        int owed = be.getCues().update(be.getChargePercent(), be.getMode() == KineticFlywheelBE.Mode.CHARGING,
            be.isOverstressHalted(), level.getGameTime());
        Entity listener = mc.cameraEntity;
        float cfg = FlywheelSoundInstance.configVolume();
        if (listener == null || cfg <= 0.0f) {
            return;
        }
        BlockPos pos = be.getBlockPos();
        if (listener.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > EARSHOT_SQR) {
            return;
        }
        if ((owed & FlywheelVoice.Cues.CHIME) != 0) {
            cue(level, pos, FlywheelVoice.Cues.CHIME, ModSounds.FLYWHEEL_CHARGED.get(), CHIME_VOLUME * cfg);
        }
        if ((owed & FlywheelVoice.Cues.THUNK) != 0) {
            cue(level, pos, FlywheelVoice.Cues.THUNK, ModSounds.FLYWHEEL_OVERSTRESS.get(), THUNK_VOLUME * cfg);
        }
        FlywheelSoundInstance.report(be);
    }

    private static void cue(Level level, BlockPos pos, int kind, SoundEvent sound, float volume) {
        long now = level.getGameTime();
        int levelId = System.identityHashCode(level);
        for (Iterator<Cue> it = RECENT.iterator(); it.hasNext(); ) {
            Cue c = it.next();
            if (c.level() != levelId || now - c.time() > CLUSTER_TICKS || now < c.time()) {
                it.remove();
            }
        }
        for (Cue c : RECENT) {
            if (c.kind() == kind && c.pos().distSqr(pos) <= CLUSTER_SQR) {
                return;
            }
        }
        RECENT.add(new Cue(levelId, pos.immutable(), now, kind));
        level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, sound, SoundSource.BLOCKS,
            Math.min(1.0f, volume), 1.0f, false);
    }
}
