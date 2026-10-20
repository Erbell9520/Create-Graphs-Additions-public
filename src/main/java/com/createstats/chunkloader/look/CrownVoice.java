package com.createstats.chunkloader.look;

import com.createstats.chunkloader.core.CrownState;

public final class CrownVoice {
    public static final float HUM_VOLUME = 0.0285f;

    public static final int HUM_FADE = 20;

    public static final float LATCH_VOLUME = 0.4f;
    public static final float RING_VOLUME = 0.5f;
    public static final float RING_PITCH = 0.9f;

    public static final float LOCK_VOLUME = 0.6f;
    public static final float LOCK_PITCH = 0.75f;

    public static final float UNLATCH_VOLUME = 0.32f;

    public static final float LAND_VOLUME = 0.55f;
    public static final float LAND_PITCH = 0.8f;

    public enum Sound { LATCH, UNLATCH, LAND }

    public record Shot(Sound sound, float volume, float pitch) {
    }

    private final CrownMotion motion = new CrownMotion();
    private final Shot[] shots = new Shot[4];
    private int shotCount;
    private float hum;

    public void tick(CrownState s) {
        motion.tick(s);
        final int cues = motion.cues();
        hum = s.held() > 0 ? HUM_VOLUME : 0f;

        shotCount = 0;
        if ((cues & CrownMotion.CUE_LATCH) != 0) {
            final boolean ring = (cues & CrownMotion.CUE_RING) != 0;
            shot(Sound.LATCH, ring ? RING_VOLUME : LATCH_VOLUME, ring ? RING_PITCH : 1.0f);
        }
        if ((cues & CrownMotion.CUE_UNLATCH) != 0) {
            shot(Sound.UNLATCH, UNLATCH_VOLUME, 1.0f);
        }
        if ((cues & CrownMotion.CUE_LOCK) != 0) {
            shot(Sound.LATCH, LOCK_VOLUME, LOCK_PITCH);
        }
        if ((cues & CrownMotion.CUE_LANDED) != 0) {
            shot(Sound.LAND, LAND_VOLUME, LAND_PITCH);
        }
    }

    private void shot(Sound sound, float volume, float pitch) {
        if (shotCount < shots.length) {
            shots[shotCount++] = new Shot(sound, volume, pitch);
        }
    }

    public int shotCount() {
        return shotCount;
    }

    public Shot shot(int i) {
        return shots[i];
    }

    public float hum() {
        return hum;
    }
}
