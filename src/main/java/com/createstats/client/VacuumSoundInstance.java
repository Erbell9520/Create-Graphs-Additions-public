package com.createstats.client;

import com.createstats.registry.ModSounds;
import com.createstats.vacuum.VacuumSoundHost;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;

// airfryer style incl the non-zero start volume
public class VacuumSoundInstance extends AbstractTickableSoundInstance {
    private static final double MAX_DISTANCE_SQR = 12.0 * 12.0;
    private static final float TARGET_VOLUME = 0.22f;

    private final VacuumSoundHost host;

    private VacuumSoundInstance(VacuumSoundHost host, BlockPos pos, float pitch) {
        super(ModSounds.VACUUM_HUM.get(), SoundSource.BLOCKS, RandomSource.create());
        this.host = host;
        this.x = pos.getX() + 0.5;
        this.y = pos.getY() + 0.5;
        this.z = pos.getZ() + 0.5;
        this.looping = true;
        this.delay = 0;
        this.volume = 0.02f;
        this.pitch = pitch;
    }

    public static void startIfNeeded(VacuumSoundHost host, BlockPos pos, float pitch) {
        if (host.isSoundPlaying()) {
            return;
        }
        final Player player = Minecraft.getInstance().player;
        if (player == null || player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > MAX_DISTANCE_SQR) {
            return;
        }
        host.setSoundPlaying(true);
        Minecraft.getInstance().getSoundManager().play(new VacuumSoundInstance(host, pos, pitch));
    }

    @Override
    public void tick() {
        final Player player = Minecraft.getInstance().player;
        if (host.isRemoved() || player == null) {
            stopNow();
            return;
        }
        if (!host.fanRunning() || player.distanceToSqr(this.x, this.y, this.z) > MAX_DISTANCE_SQR) {
            this.volume = Math.max(0.0f, this.volume - 0.02f);
            if (this.volume <= 0.001f) {
                stopNow();
            }
            return;
        }
        this.volume += (TARGET_VOLUME - this.volume) * 0.1f;
    }

    private void stopNow() {
        host.setSoundPlaying(false);
        this.stop();
    }
}
