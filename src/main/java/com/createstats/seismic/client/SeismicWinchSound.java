package com.createstats.seismic.client;

import com.createstats.blockentity.SeismicScannerBE;
import com.createstats.flight.bridge.SableBridge;
import com.createstats.registry.ModSounds;
import com.createstats.seismic.SeismicCharge;
import com.createstats.seismic.SeismicRig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.HashMap;
import java.util.Map;

@OnlyIn(Dist.CLIENT)
public final class SeismicWinchSound extends AbstractTickableSoundInstance {
    static final double REACH = 14.0;
    private static final double DROP = 18.0;
    private static final float RAMP = 0.06f;

    private static final Map<BlockPos, SeismicWinchSound> PLAYING = new HashMap<>();

    private final BlockPos pos;
    private final Level world;

    private SeismicWinchSound(Level world, BlockPos pos) {
        super(ModSounds.SEISMIC_WINCH.get(), SoundSource.BLOCKS, RandomSource.create(pos.asLong()));
        this.world = world;
        this.pos = pos;
        this.x = pos.getX() + 0.8;
        this.y = pos.getY() + 0.3;
        this.z = pos.getZ() + 0.5;
        this.looping = true;
        this.delay = 0;

        this.volume = RAMP * 2.0f;
        this.pitch = 0.8f;
    }

    public static void keep(Level level, BlockPos pos) {
        final Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !(level.getBlockEntity(pos) instanceof SeismicScannerBE be)
                || SeismicClient.away(be) > REACH * REACH) {
            return;
        }
        final SeismicWinchSound running = PLAYING.get(pos);
        if (running != null && running.world == level && mc.getSoundManager().isActive(running)) {
            return;
        }
        final SeismicWinchSound hum = new SeismicWinchSound(level, pos.immutable());
        hum.place(be);
        PLAYING.put(hum.pos, hum);
        mc.getSoundManager().play(hum);
    }

    private void place(SeismicScannerBE be) {
        final Vec3 at = SableBridge.worldPointOf(be, new Vec3(pos.getX() + 0.8, pos.getY() + 0.3, pos.getZ() + 0.5));
        if (at != null) {
            x = at.x;
            y = at.y;
            z = at.z;
        }
    }

    @Override
    public void tick() {
        final Minecraft mc = Minecraft.getInstance();
        final SeismicScannerBE be = mc.level == world && world.isLoaded(pos) && mc.player != null
                && world.getBlockEntity(pos) instanceof SeismicScannerBE rig ? rig : null;
        if (be != null) {
            place(be);
        }
        final boolean here = be != null && mc.player.distanceToSqr(x, y, z) <= DROP * DROP;
        float target = 0.0f;
        if (here && be.state() == SeismicRig.State.CHARGING) {
            final float load = Math.min(1.0f, be.intakeShown() / (float) SeismicCharge.WINCH_FE_PER_TICK);
            target = 0.18f + 0.32f * load;
            pitch += ((0.75f + 0.55f * load) - pitch) * 0.2f;
        }
        volume = volume < target ? Math.min(target, volume + RAMP) : Math.max(target, volume - RAMP);
        if (volume <= 0.001f && target <= 0.0f) {
            PLAYING.remove(pos, this);
            stop();
        }
    }
}
