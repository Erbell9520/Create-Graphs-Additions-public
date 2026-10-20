package com.createstats.client;

import com.createstats.block.ContactorBlock;
import com.createstats.contactor.ContactorState;
import com.createstats.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.HashMap;
import java.util.Map;

@OnlyIn(Dist.CLIENT)
public final class ContactorHumSound extends AbstractTickableSoundInstance {
    static final double REACH = 12.0;

    private static final double DROP = 16.0;

    private static final float ONE_COIL = 0.18f;
    private static final float TWO_COILS = 0.27f;

    private static final float RAMP = 0.045f;

    private static final Map<BlockPos, ContactorHumSound> PLAYING = new HashMap<>();

    private final BlockPos pos;
    private final Level world;

    private ContactorHumSound(Level world, BlockPos pos) {
        super(ModSounds.CONTACTOR_HUM.get(), SoundSource.BLOCKS, RandomSource.create(pos.asLong()));
        this.world = world;
        this.pos = pos;
        this.x = pos.getX() + 0.5;
        this.y = pos.getY() + 0.5;
        this.z = pos.getZ() + 0.5;
        this.looping = true;
        this.delay = 0;

        this.volume = RAMP * 2.0f;

        this.pitch = 0.97f + 0.06f * (Math.floorMod(pos.hashCode(), 17) / 16.0f);
    }

    public static void keep(Level level, BlockPos pos) {
        final Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)
                > REACH * REACH) {
            return;
        }
        final ContactorHumSound running = PLAYING.get(pos);
        // ask the sound manager, leaving and rejoining stops sounds without telling us

        if (running != null && running.world == level && mc.getSoundManager().isActive(running)) {
            return;
        }
        final ContactorHumSound hum = new ContactorHumSound(level, pos.immutable());
        PLAYING.put(hum.pos, hum);
        mc.getSoundManager().play(hum);
    }

    private static int coilsIn(Level level, BlockPos pos, BlockState state) {
        return (ContactorBlock.coil(level, pos, state, ContactorState.LEFT_SIDE) ? 1 : 0)
                + (ContactorBlock.coil(level, pos, state, ContactorState.RIGHT_SIDE) ? 1 : 0);
    }

    @Override
    public void tick() {
        final Minecraft mc = Minecraft.getInstance();
        final boolean here = mc.level == world && world.isLoaded(pos) && mc.player != null
                && mc.player.distanceToSqr(x, y, z) <= DROP * DROP;
        final int coils = here ? coilsIn(world, pos, world.getBlockState(pos)) : 0;
        final float target = coils == 0 ? 0.0f : coils == 1 ? ONE_COIL : TWO_COILS;
        volume = volume < target ? Math.min(target, volume + RAMP) : Math.max(target, volume - RAMP);
        if (volume <= 0.001f && coils == 0) {
            PLAYING.remove(pos, this);
            stop();
        }
    }
}
