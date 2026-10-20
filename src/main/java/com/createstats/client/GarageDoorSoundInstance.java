package com.createstats.client;

import com.createstats.blockentity.GarageDoorBE;
import com.createstats.sound.DoorClip;
import com.createstats.sound.DoorSound;
import com.createstats.sound.GarageDoorSound;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public class GarageDoorSoundInstance extends AbstractTickableSoundInstance {
    private static final double MAX_DISTANCE_SQR = 16.0 * 16.0;

    private static final float FADE = 0.30f;

    private final GarageDoorBE be;
    private final float baseVolume;
    private final float basePitch;
    private final float from;
    private final float to;

    private GarageDoorSoundInstance(GarageDoorBE be, DoorClip clip, int area, float from, float to) {
        super(DoorSound.event(clip), SoundSource.BLOCKS, RandomSource.create());
        this.be = be;
        this.from = from;
        this.to = to;
        this.baseVolume = GarageDoorSound.volume(area);
        this.basePitch = GarageDoorSound.pitch(area);
        final Vec3 at = be.audiblePos();
        this.x = at.x;
        this.y = at.y;
        this.z = at.z;
        this.looping = true;
        this.delay = 0;

        this.volume = 0.01f;
        this.pitch = basePitch;
    }

    public static void startIfNeeded(GarageDoorBE be, DoorClip clip, float from, float to) {
        if (be.loopRunning()) {
            return;
        }
        be.setLoopRunning(true);
        Minecraft.getInstance().getSoundManager()
                .play(new GarageDoorSoundInstance(be, clip, be.area(), from, to));
    }

    @Override
    public void tick() {
        final Minecraft mc = Minecraft.getInstance();
        final Player player = mc.player;
        if (be.isRemoved() || player == null || mc.level != be.getLevel() || be.settled()) {
            stopHere();
            return;
        }
        if (player.distanceToSqr(x, y, z) > MAX_DISTANCE_SQR) {
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
