package com.createstats.client;

import com.createstats.block.DoorMaterial;
import com.createstats.block.DoorOpening;
import com.createstats.block.PoweredDoorBlock;
import com.createstats.blockentity.PoweredDoorBE;
import com.createstats.sound.DoorClip;
import com.createstats.sound.DoorSound;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class PoweredDoorEffects {
    private PoweredDoorEffects() {
    }

    private static final int CUE_MIDWAY = 0;
    private static final int CUE_ARRIVAL = 1;

    public static void tick(PoweredDoorBE be) {
        final Level level = be.getLevel();
        final BlockState state = be.getBlockState();
        if (level == null || !(state.getBlock() instanceof PoweredDoorBlock block)) {
            return;
        }
        final DoorOpening style = be.effectiveStyle();
        final DoorSound.Kit kit = DoorSound.kit(style);

        final float openness = be.progress(0.0f);
        final float travelled = be.isOpen() ? openness : 1.0f - openness;

        if (!be.settled()) {
            if (speaksForDoor(level, be)) {
                if (kit.loop() != null) {
                    PoweredDoorSoundInstance.startIfNeeded(be, kit.loop(),
                            kit.loopFrom(), kit.loopTo());
                }
                final int ticks = be.moveTicks();
                final int elapsed = be.elapsedTicks();
                if (kit.midway() != null && elapsed >= cueTick(kit.midwayAt(), ticks)
                        && be.claimCue(CUE_MIDWAY)) {
                    play(level, be, block.material(), kit.midway(), 0.75f);
                }
                if (elapsed >= cueTick(kit.arriveAt(), ticks) && be.claimCue(CUE_ARRIVAL)) {
                    arrive(level, be, block.material(), kit, be.isOpen());
                }
            }
            particles(level, be, style, travelled);
        }
    }

    static int cueTick(float fraction, int ticks) {
        final int wanted = (int) (fraction * ticks);
        return Math.max(0, Math.min(ticks - 1, wanted));
    }

    private static boolean speaksForDoor(Level level, PoweredDoorBE be) {
        final BlockPos here = be.getBlockPos();
        final BlockPos partner = PoweredDoorBlock.partnerOf(level, here, be.getBlockState());
        return partner == null || here.compareTo(partner) < 0;
    }

    private static void arrive(Level level, PoweredDoorBE be, DoorMaterial material,
                               DoorSound.Kit kit, boolean opening) {
        play(level, be, material, kit.arrival(opening), 1.0f);
        final BlockPos pos = be.getBlockPos();
        level.playLocalSound(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5,
                DoorSound.substance(material), SoundSource.BLOCKS,
                DoorSound.volume(material) * DoorSound.SUBSTANCE_GAIN,
                DoorSound.substancePitch(material), false);
    }

    private static void play(Level level, PoweredDoorBE be, DoorMaterial material,
                             DoorClip clip, float trim) {
        final BlockPos pos = be.getBlockPos();
        level.playLocalSound(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5,
                DoorSound.event(clip), SoundSource.BLOCKS,
                DoorSound.volume(material) * trim,
                DoorSound.pitch(material), false);
    }

    private static void particles(Level level, PoweredDoorBE be, DoorOpening style,
                                  float travelled) {
        final RandomSource rng = level.getRandom();
        final BlockPos pos = be.getBlockPos();
        final BlockState state = be.getBlockState();
        final Direction facing = state.getValue(PoweredDoorBlock.FACING);

        switch (style) {
            case SWING, WINGS -> {
            }

            case SLIDE, STACK_SLIDE, FOLD_RETRACT -> {
                if (travelled < 0.05f || travelled > 0.45f || rng.nextFloat() > 0.35f) {
                    return;
                }
                final Direction away = pocketSide(state, facing);
                puff(level, rng,
                        pos.getX() + 0.5 + away.getStepX() * 0.55 + facing.getStepX() * -0.35,
                        pos.getY() + 0.15 + rng.nextFloat() * 1.6,
                        pos.getZ() + 0.5 + away.getStepZ() * 0.55 + facing.getStepZ() * -0.35);
            }

            case PLUG_RETRACT -> {
                if (travelled > 0.18f || rng.nextFloat() > 0.5f) {
                    return;
                }
                puff(level, rng,
                        pos.getX() + 0.5 + facing.getStepX() * -0.4 + rng.nextFloat() * 0.4 - 0.2,
                        pos.getY() + 0.4 + rng.nextFloat() * 1.2,
                        pos.getZ() + 0.5 + facing.getStepZ() * -0.4 + rng.nextFloat() * 0.4 - 0.2);
            }

            case ROLL_UP -> {
                if (rng.nextFloat() > 0.45f) {
                    return;
                }
                level.addParticle(ParticleTypes.SMOKE,
                        pos.getX() + 0.15 + rng.nextFloat() * 0.7,
                        pos.getY() + 1.85,
                        pos.getZ() + 0.15 + rng.nextFloat() * 0.7,
                        0.0, -0.04 - rng.nextFloat() * 0.03, 0.0);
            }

            case FOLD_PARK, CONCERTINA -> {
                if (Math.abs(travelled - 0.5f) > 0.18f || rng.nextFloat() > 0.2f) {
                    return;
                }
                puff(level, rng, pos.getX() + 0.5, pos.getY() + 0.6 + rng.nextFloat() * 1.0,
                        pos.getZ() + 0.5);
            }
        }
    }

    private static Direction pocketSide(BlockState state, Direction facing) {
        return state.getValue(PoweredDoorBlock.HINGE) == DoorHingeSide.RIGHT
                ? facing.getCounterClockWise() : facing.getClockWise();
    }

    private static void puff(Level level, RandomSource rng, double x, double y, double z) {
        level.addParticle(ParticleTypes.SMOKE, x, y, z,
                (rng.nextDouble() - 0.5) * 0.01,
                0.005 + rng.nextDouble() * 0.01,
                (rng.nextDouble() - 0.5) * 0.01);
    }
}
