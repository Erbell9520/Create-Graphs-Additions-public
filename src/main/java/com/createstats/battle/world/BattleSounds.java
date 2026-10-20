package com.createstats.battle.world;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;

// vanilla sounds only. plot pos is 20M blocks out but sable measures sound distance per sub-level so it still works
public final class BattleSounds {
    private BattleSounds() {
    }

    public static void bell(Level level, BlockPos pos) {
        play(level, pos, SoundEvents.BELL_BLOCK, 2.0f, 1.0f);
    }

    public static void fire(Level level, BlockPos pos) {
        play(level, pos, SoundEvents.BELL_RESONATE, 1.5f, 1.0f);
    }

    public static void gunDown(Level level, BlockPos pos) {
        play(level, pos, SoundEvents.NOTE_BLOCK_BASS.value(), 1.0f, 0.5f);
    }

    public static void targetDown(Level level, BlockPos pos) {
        play(level, pos, SoundEvents.NOTE_BLOCK_CHIME.value(), 1.0f, 1.0f);
    }

    private static void play(Level level, BlockPos pos, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, pos, sound, SoundSource.BLOCKS, volume, pitch);
    }
}
