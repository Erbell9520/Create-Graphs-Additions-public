package com.createstats.client;

import com.createstats.ModConfiguration;
import com.createstats.blockentity.KineticFlywheelBE;
import com.createstats.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;

import java.util.HashSet;
import java.util.Set;

/** [1] */
public class FlywheelSoundInstance extends AbstractTickableSoundInstance {

    private static final Set<BlockPos> ACTIVE = new HashSet<>();
    private static final double MAX_DISTANCE_SQR = 48.0 * 48.0;

    private final KineticFlywheelBE be;

    private FlywheelSoundInstance(KineticFlywheelBE be) {
        super(ModSounds.FLYWHEEL_LOOP.get(), SoundSource.BLOCKS, RandomSource.create());
        this.be = be;
        BlockPos pos = be.getBlockPos();
        this.x = pos.getX() + 0.5;
        this.y = pos.getY() + 0.5;
        this.z = pos.getZ() + 0.5;
        this.looping = true;
        this.delay = 0;
        // [2]
        this.volume = 0.45f;
        this.pitch = 1.0f;
    }

    /** Start a hum for this flywheel if one is not already playing at its position. */
    public static void startIfNeeded(KineticFlywheelBE be) {
        if (ModConfiguration.CLIENT.flywheelSoundVolume.get() <= 0.0) {
            return;
        }
        BlockPos pos = be.getBlockPos();
        if (ACTIVE.contains(pos)) {
            return;
        }
        ACTIVE.add(pos);
        Minecraft.getInstance().getSoundManager().play(new FlywheelSoundInstance(be));
    }

    @Override
    public void tick() {
        Player player = Minecraft.getInstance().player;
        if (be.isRemoved() || be.getLevel() == null || player == null) {
            stopNow();
            return;
        }
        KineticFlywheelBE.Mode mode = be.getMode();
        boolean active = mode != KineticFlywheelBE.Mode.IDLE;
        double distSqr = player.distanceToSqr(this.x, this.y, this.z);

        if (!active || distSqr > MAX_DISTANCE_SQR) {
            this.volume = Math.max(0.0f, this.volume - 0.08f);
            if (this.volume <= 0.001f) {
                stopNow();
            }
            return;
        }

        // [3]
        float speed = mode == KineticFlywheelBE.Mode.DISCHARGING
            ? Math.abs(be.effectiveOutputRpm())
            : Math.min(KineticFlywheelBE.OUTPUT_RPM, Math.abs(be.getSpeed()));
        float frac = clamp(speed / KineticFlywheelBE.OUTPUT_RPM, 0.0f, 1.0f);
        this.pitch = 0.55f + 0.9f * frac;

        float cfg = (float) (double) ModConfiguration.CLIENT.flywheelSoundVolume.get();
        if (cfg <= 0.0f) {
            this.volume = Math.max(0.0f, this.volume - 0.08f);
            if (this.volume <= 0.001f) {
                stopNow();
            }
            return;
        }
        float base = mode == KineticFlywheelBE.Mode.DISCHARGING ? 0.85f : 0.65f;
        float target = base * clamp(0.5f + frac, 0.5f, 1.0f) * cfg;
        this.volume += (target - this.volume) * 0.14f;
    }

    private void stopNow() {
        ACTIVE.remove(be.getBlockPos());
        this.stop();
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
