package com.createstats.sound;

import com.createstats.block.DoorMaterial;
import com.createstats.block.DoorOpening;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

public final class DoorSound {
    private DoorSound() {
    }

    public record Kit(DoorClip depart, @Nullable DoorClip departLayer,
                      @Nullable DoorClip loop, float loopFrom, float loopTo,
                      @Nullable DoorClip midway, float midwayAt,
                      float arriveAt, @Nullable DoorClip arriveShut) {
        public DoorClip arrival(boolean opening) {
            return !opening && arriveShut != null ? arriveShut : DoorClip.SEAT;
        }

        public java.util.List<DoorClip> clips() {
            final java.util.List<DoorClip> out = new java.util.ArrayList<>();
            out.add(depart);
            for (final DoorClip maybe : new DoorClip[]{departLayer, loop, midway, arriveShut}) {
                if (maybe != null) {
                    out.add(maybe);
                }
            }
            out.add(DoorClip.SEAT);
            return out;
        }
    }

    public static Kit kit(DoorOpening style) {
        return switch (style) {
            case SWING -> new Kit(DoorClip.LATCH, DoorClip.HINGE_SWEEP,
                    null, 0.0f, 0.0f, null, 0.0f, 0.92f, null);

            case SLIDE -> new Kit(DoorClip.LATCH, null,
                    DoorClip.SERVO_LOOP, 0.05f, 0.90f, null, 0.0f, 0.95f, null);

            case STACK_SLIDE -> new Kit(DoorClip.LATCH, null,
                    DoorClip.SERVO_LOOP, 0.05f, 0.92f, null, 0.0f, 0.96f, null);

            case PLUG_RETRACT -> new Kit(DoorClip.SEAL_HISS, null,
                    DoorClip.SERVO_LOOP, 0.10f, 0.90f, null, 0.0f, 0.95f, null);

            case FOLD_PARK, CONCERTINA, WINGS -> new Kit(DoorClip.LATCH, null,
                    DoorClip.SERVO_LOOP, 0.05f, 0.90f,
                    DoorClip.KNUCKLE, 0.50f, 0.95f, null);

            case FOLD_RETRACT -> new Kit(DoorClip.LATCH, null,
                    DoorClip.SERVO_LOOP, 0.05f, 0.92f,
                    DoorClip.KNUCKLE, 0.45f, 0.96f, null);

            case ROLL_UP -> new Kit(DoorClip.LATCH, null,
                    DoorClip.DRUM_LOOP, 0.03f, 0.95f, null, 0.0f, 0.97f,
                    DoorClip.FLOOR_BAR);
        };
    }

    public static SoundEvent event(DoorClip clip) {
        return com.createstats.registry.ModSounds.doorClip(clip);
    }

    private static final float LIGHTEST = 2.5f;
    private static final float HEAVIEST = 5.0f;

    private static final float PITCH_LIGHT = 1.15f;
    private static final float PITCH_HEAVY = 0.85f;

    private static final float GAIN_LIGHT = 0.30f;
    private static final float GAIN_HEAVY = 0.46f;

    private static float mass(float hardness) {
        return Mth.clamp((hardness - LIGHTEST) / (HEAVIEST - LIGHTEST), 0.0f, 1.0f);
    }

    public static float pitchForHardness(float hardness) {
        return Mth.lerp(mass(hardness), PITCH_LIGHT, PITCH_HEAVY);
    }

    public static float volumeForHardness(float hardness) {
        return Mth.lerp(mass(hardness), GAIN_LIGHT, GAIN_HEAVY);
    }

    public static float pitch(DoorMaterial material) {
        return pitchForHardness(material.hardness());
    }

    public static float volume(DoorMaterial material) {
        return volumeForHardness(material.hardness());
    }

    public static SoundEvent substance(DoorMaterial material) {
        return material.soundType().getPlaceSound();
    }

    public static final float SUBSTANCE_GAIN = 0.22f;

    public static float substancePitch(DoorMaterial material) {
        return pitch(material) * SUBSTANCE_PITCH;
    }

    public static final float SUBSTANCE_PITCH = 0.80f;
}
