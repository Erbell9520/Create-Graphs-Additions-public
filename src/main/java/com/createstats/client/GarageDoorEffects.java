package com.createstats.client;

import com.createstats.block.GarageDoorBodyBlock;
import com.createstats.block.GarageDoorCurl;
import com.createstats.block.GarageDoorHeaderBlock;
import com.createstats.block.GarageDoorSite;
import com.createstats.blockentity.GarageDoorBE;
import com.createstats.sound.DoorClip;
import com.createstats.sound.DoorSound;
import com.createstats.sound.GarageDoorSound;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class GarageDoorEffects {
    private GarageDoorEffects() {
    }

    private static final int CUE_ARRIVAL = 0;

    private static final double CURTAIN_FACE =
            0.5 - GarageDoorBodyBlock.CURTAIN_Z0 / (double) GarageDoorCurl.PX;

    public static void tick(GarageDoorBE be) {
        final Level level = be.getLevel();
        final BlockState state = be.getBlockState();
        if (level == null || !be.formed()
                || !(state.getBlock() instanceof GarageDoorHeaderBlock)) {
            return;
        }
        final DoorSound.Kit kit = GarageDoorSound.kit();

        if (kit.loop() != null) {
            GarageDoorSoundInstance.startIfNeeded(be, kit.loop(), kit.loopFrom(), kit.loopTo());
        }
        final int ticks = be.moveTicks();
        if (be.elapsedTicks() >= cueTick(kit.arriveAt(), ticks) && be.claimCue(CUE_ARRIVAL)) {
            arrive(level, be, kit);
        }
        particles(level, be, state);
    }

    static int cueTick(float fraction, int ticks) {
        final int wanted = (int) (fraction * ticks);
        return Math.max(0, Math.min(ticks - 1, wanted));
    }

    private static void arrive(Level level, GarageDoorBE be, DoorSound.Kit kit) {
        final Vec3 at = be.audiblePos();
        final int area = be.area();
        final float volume = GarageDoorSound.volume(area);
        final float pitch = GarageDoorSound.pitch(area);
        level.playLocalSound(at.x, at.y, at.z, DoorSound.event(kit.arrival(be.isOpen())),
                SoundSource.BLOCKS, volume, pitch, false);

        level.playLocalSound(at.x, at.y, at.z, SoundType.METAL.getPlaceSound(), SoundSource.BLOCKS,
                volume * DoorSound.SUBSTANCE_GAIN, pitch * DoorSound.SUBSTANCE_PITCH, false);
    }

    private static void particles(Level level, GarageDoorBE be, BlockState state) {
        final RandomSource rng = level.getRandom();
        if (rng.nextFloat() > 0.5f) {
            return;
        }
        final Direction facing = state.getValue(GarageDoorHeaderBlock.FACING);
        final Direction axis = GarageDoorSite.widthAxis(facing);

        final BlockPos pos = be.originPos().relative(axis, rng.nextInt(be.width()));
        level.addParticle(ParticleTypes.SMOKE,
                pos.getX() + 0.5 + facing.getStepX() * CURTAIN_FACE + rng.nextDouble() * 0.4 - 0.2,
                pos.getY() - 0.05,
                pos.getZ() + 0.5 + facing.getStepZ() * CURTAIN_FACE + rng.nextDouble() * 0.4 - 0.2,
                0.0, -0.03 - rng.nextFloat() * 0.02, 0.0);
    }
}
