package com.createstats.vehiclelight;

import com.createstats.vccmk4.core.LinkLogic;

public enum VehicleLightKind {
    WHITE("white", 10, 14, false,
            new float[] {2.0f, 3.0f, 14.0f, 13.0f},
            new float[] {2.5f, 3.5f, 13.5f, 12.5f}, 4.0f, 5.0f,
            new float[][] {{3.5f, 4.0f, 4.5f, 12.5f, 4.6f, 11.5f}}),
    RED("red", 3, 8, false,
            new float[] {1.0f, 4.0f, 15.0f, 12.0f},
            new float[] {1.5f, 4.5f, 14.5f, 11.5f}, 3.5f, 4.5f,
            new float[][] {{2.5f, 3.5f, 5.5f, 13.5f, 4.1f, 10.5f}}),

    AMBER("amber", 0, 7, true,
            new float[] {4.0f, 4.0f, 12.0f, 12.0f},
            new float[] {4.5f, 4.5f, 11.5f, 11.5f}, 3.5f, 4.5f,
            new float[][] {{5.5f, 3.5f, 5.5f, 10.5f, 4.1f, 10.5f}, {6.5f, 4.1f, 6.5f, 9.5f, 4.6f, 9.5f}});

    public static final int LEASE_TICKS = 3;

    private final String id;
    private final int dimEmission;
    private final int brightEmission;
    private final boolean flashes;
    private final float[] flange;
    private final float[] body;
    private final float bodyTop;
    private final float bezelTop;
    private final float[][] lens;

    VehicleLightKind(String id, int dimEmission, int brightEmission, boolean flashes, float[] flange, float[] body,
                     float bodyTop, float bezelTop, float[][] lens) {
        this.id = id;
        this.dimEmission = dimEmission;
        this.brightEmission = brightEmission;
        this.flashes = flashes;
        this.flange = flange;
        this.body = body;
        this.bodyTop = bodyTop;
        this.bezelTop = bezelTop;
        this.lens = lens;
    }

    public String id() {
        return id;
    }

    public boolean flashes() {
        return flashes;
    }

    public VehicleLightGlow glow(int strength, long gameTime) {
        return glow(strength, gameTime, false);
    }

    public VehicleLightGlow glow(int strength, long gameTime, boolean clientFlash) {
        if (strength <= 0) {
            return VehicleLightGlow.OFF;
        }
        if (flashes) {
            if (clientFlash) {
                return VehicleLightGlow.DIM;
            }
            return flashOn(gameTime) ? VehicleLightGlow.BRIGHT : VehicleLightGlow.OFF;
        }
        return strength >= LinkLogic.FULL ? VehicleLightGlow.BRIGHT : VehicleLightGlow.DIM;
    }

    public VehicleLightGlow shown(VehicleLightGlow state, long gameTime) {
        if (flashes && state == VehicleLightGlow.DIM) {
            return flashOn(gameTime) ? VehicleLightGlow.BRIGHT : VehicleLightGlow.OFF;
        }
        return state;
    }

    public int emission(VehicleLightGlow glow) {
        return switch (glow) {
            case OFF -> 0;
            case DIM -> dimEmission;
            case BRIGHT -> brightEmission;
        };
    }

    public static boolean flashOn(long gameTime) {
        return Math.floorMod(gameTime / LinkLogic.BLINK_TICKS, 2L) == 0L;
    }

    public float[] flange() {
        return flange.clone();
    }

    public float[] body() {
        return body.clone();
    }

    public float bodyTop() {
        return bodyTop;
    }

    public float bezelTop() {
        return bezelTop;
    }

    public float[][] lens() {
        final float[][] out = new float[lens.length][];
        for (int i = 0; i < lens.length; i++) {
            out[i] = lens[i].clone();
        }
        return out;
    }

    public float lensFront() {
        float front = 0.0f;
        for (float[] box : lens) {
            front = Math.max(front, box[4]);
        }
        return front;
    }
}
