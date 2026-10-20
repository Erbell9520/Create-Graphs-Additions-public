package com.createstats.block;

import com.createstats.blockentity.SwitchBE;
import com.createstats.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.function.Supplier;

// a dimmer is a switch with a level on top
public class RotaryDimmerBlock extends SwitchBlock {
    public static final int LEVELS = LampBlock.DIM_FRACTION.length;

    public enum Kind {
        DIAL(ModSounds.DIMMER_DIAL_TICK, ModSounds.DIMMER_DIAL_STOP, ModSounds.DIMMER_DIAL_PUSH,
                0.36f, 0.42f, 0.46f, 12.0),

        PUCK(ModSounds.DIMMER_PUCK_TICK, ModSounds.DIMMER_PUCK_STOP, ModSounds.DIMMER_PUCK_PUSH,
                0.30f, 0.32f, 0.40f, 9.0);

        private final Supplier<SoundEvent> tick;
        private final Supplier<SoundEvent> stop;
        private final Supplier<SoundEvent> push;
        private final float tickVolume;
        private final float stopVolume;
        private final float pushVolume;

        private final double notchesPerOctave;

        Kind(Supplier<SoundEvent> tick, Supplier<SoundEvent> stop, Supplier<SoundEvent> push,
             float tickVolume, float stopVolume, float pushVolume, double notchesPerOctave) {
            this.tick = tick;
            this.stop = stop;
            this.push = push;
            this.tickVolume = tickVolume;
            this.stopVolume = stopVolume;
            this.pushVolume = pushVolume;
            this.notchesPerOctave = notchesPerOctave;
        }

        public float tickPitch(int level) {
            return (float) Math.pow(2.0, (level - (LEVELS - 1) / 2.0) / notchesPerOctave);
        }
    }

    private final Kind kind;

    public RotaryDimmerBlock(Properties properties, VoxelShape upShape, Kind kind) {
        super(properties, upShape, false, Click.ROCKER);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (player.isShiftKeyDown()) {
            turn(level, pos, -1);
            return InteractionResult.CONSUME;
        }
        final boolean now = !state.getValue(POWERED);
        final BlockState next = state.setValue(POWERED, now);
        level.setBlock(pos, next, Block.UPDATE_ALL);
        level.playSound(null, pos, kind.push.get(), SoundSource.BLOCKS, kind.pushVolume, now ? 1.0f : 0.9f);
        updateNeighbours(next, level, pos);
        onContactsMoved(level, pos);
        return InteractionResult.CONSUME;
    }

    public void turn(Level level, BlockPos pos, int delta) {
        if (level.isClientSide() || delta == 0 || !(level.getBlockEntity(pos) instanceof SwitchBE be)) {
            return;
        }
        if (be.stepDimLevel(Integer.signum(delta))) {
            level.playSound(null, pos, kind.tick.get(), SoundSource.BLOCKS, kind.tickVolume,
                    kind.tickPitch(be.dimLevel()));
        } else {
            level.playSound(null, pos, kind.stop.get(), SoundSource.BLOCKS, kind.stopVolume, 1.0f);
        }
    }
}
