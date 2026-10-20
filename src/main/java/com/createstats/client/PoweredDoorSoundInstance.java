package com.createstats.client;

import com.createstats.block.DoorMaterial;
import com.createstats.block.PoweredDoorBlock;
import com.createstats.blockentity.PoweredDoorBE;
import com.createstats.sound.DoorClip;
import com.createstats.sound.DoorSound;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;

public class PoweredDoorSoundInstance extends AbstractTickableSoundInstance {
    private static final double MAX_DISTANCE_SQR = 12.0 * 12.0;

    private static final float FADE = 0.35f;

    private final PoweredDoorBE be;
    private final float baseVolume;
    private final float basePitch;
    private final float from;
    private final float to;

    private PoweredDoorSoundInstance(PoweredDoorBE be, DoorClip clip, DoorMaterial material,
                                     float from, float to) {
        super(DoorSound.event(clip), SoundSource.BLOCKS, RandomSource.create());
        this.be = be;
        this.from = from;
        this.to = to;
        this.baseVolume = DoorSound.volume(material);
        this.basePitch = DoorSound.pitch(material);
        final BlockPos pos = be.getBlockPos();
        this.x = pos.getX() + 0.5;
        this.y = pos.getY() + 1.0;
        this.z = pos.getZ() + 0.5;
        this.looping = true;
        this.delay = 0;

        this.volume = 0.01f;
        this.pitch = basePitch;
    }

    public static void startIfNeeded(PoweredDoorBE be, DoorClip clip, float from, float to) {
        if (be.loopRunning()) {
            return;
        }
        final DoorMaterial material = be.getBlockState().getBlock() instanceof PoweredDoorBlock door
                ? door.material() : DoorMaterial.IRON;
        be.setLoopRunning(true);
        Minecraft.getInstance().getSoundManager()
                .play(new PoweredDoorSoundInstance(be, clip, material, from, to));
    }

    @Override
    public void tick() {
        final Minecraft mc = Minecraft.getInstance();
        final Player player = mc.player;
        if (be.isRemoved() || player == null || mc.level != be.getLevel()) {
            stopHere();
            return;
        }
        if (player.distanceToSqr(x, y, z) > MAX_DISTANCE_SQR) {
            stopHere();
            return;
        }
        if (be.settled()) {
            stopHere();
            return;
        }

        final float openness = be.progress(0.0f);
        final float travelled = be.isOpen() ? openness : 1.0f - openness;

        this.volume = baseVolume * window(travelled);

        this.pitch = basePitch * (0.97f + 0.05f * Mth.sin(travelled * Mth.PI));
    }

    private float window(float travelled) {
        if (travelled <= from || travelled >= to) {
            return 0.0f;
        }
        final float rise = Mth.clamp((travelled - from) / FADE, 0.0f, 1.0f);
        final float fall = Mth.clamp((to - travelled) / FADE, 0.0f, 1.0f);
        return Math.min(rise, fall);
    }

    private void stopHere() {
        be.setLoopRunning(false);
        stop();
    }
}
