package com.createstats.client.ponder.seismic;

import com.createstats.seismic.SeismicCharge;
import com.createstats.seismic.SeismicGeometry;
import com.createstats.seismic.SeismicRig;

// singleplayer ponder pauses the clock, step the lantern ourselves
public final class RigLook {
    public static final float EASE = 0.35f;

    public static final double DOGS = 0.25;

    public static final float LAMP_WARM = 0.6f;
    public static final float LAMP_COOL = 0.42f;
    public static final float LAMP_HUE = 0.45f;

    public static final float SWAP = 0.25f;

    public static final float[] RED = {1.0f, 0.24f, 0.18f};
    public static final float[] GREEN = {0.15f, 1.0f, 0.68f};
    public static final float[] WHITE = {1.0f, 1.0f, 0.94f};
    public static final float[] AMBER = {1.0f, 0.64f, 0.14f};

    public float weight;
    public float weightOld;
    public float dogs;
    public float dogsOld;
    public float glow;
    public float glowOld;
    public final float[] rgb = AMBER.clone();
    public final float[] rgbOld = AMBER.clone();
    public float paper;
    public float paperOld;
    public String specimen;
    public float specimenOut;
    public float specimenOutOld;

    public void settle(RigScript.Frame f, int tick) {
        weight = weightOld = (float) f.height();
        dogs = dogsOld = (float) SeismicCharge.heightFor(f.radius());
        final boolean lit = SeismicRig.lampLit(f.lamp(), tick);
        glow = glowOld = lit ? 1.0f : 0.0f;
        final float[] c = colour(f.lamp());
        System.arraycopy(c, 0, rgb, 0, 3);
        System.arraycopy(c, 0, rgbOld, 0, 3);
        specimen = f.target();
        specimenOut = specimenOutOld = 0.0f;
    }

    public void step(RigScript.Frame f, int tick) {
        weightOld = weight;
        final boolean falling = f.dropTick() >= 0 && f.impactTick() < 0 && f.state() == SeismicRig.State.DROPPING;
        if (falling) {
            weight = (float) (f.dropHeight() * (1.0 - SeismicCharge.fallen(tick - f.dropTick(), f.fallTicks())));
        } else if (f.impactTick() >= 0 && tick - f.impactTick() < 2 && weight > f.height()) {
            weight = weightOld = (float) f.height();
        } else {
            final float eased = weight + (float) ((f.height() - weight) * EASE);
            weight = f.state() == SeismicRig.State.CHARGING ? Math.max(weight, eased) : eased;
        }
        dogsOld = dogs;
        dogs += (float) ((SeismicCharge.heightFor(f.radius()) - dogs) * DOGS);
        paperOld = paper;
        paper += (float) SeismicGeometry.PAPER_DEG_PER_TICK;
        if (paper >= 360.0f) {
            paper -= 360.0f;
            paperOld -= 360.0f;
        }
        lamp(f.lamp(), tick);
        specimenOutOld = specimenOut;
        if (specimen == null) {
            specimen = f.target();
        } else if (!specimen.equals(f.target())) {
            specimenOut = Math.min(1.0f, specimenOut + SWAP);
            if (specimenOut >= 1.0f) {
                specimen = f.target();
            }
        } else if (specimenOut > 0.0f) {
            specimenOut = Math.max(0.0f, specimenOut - SWAP);
        }
    }

    public void lamp(SeismicRig.Lamp lamp, int tick) {
        final boolean lit = SeismicRig.lampLit(lamp, tick);
        glowOld = glow;
        glow += ((lit ? 1.0f : 0.0f) - glow) * (lit ? LAMP_WARM : LAMP_COOL);
        System.arraycopy(rgb, 0, rgbOld, 0, 3);
        if (lit) {
            final float[] c = colour(lamp);
            for (int i = 0; i < 3; i++) {
                rgb[i] += (c[i] - rgb[i]) * LAMP_HUE;
            }
        }
    }

    public static float[] colour(SeismicRig.Lamp lamp) {
        return switch (lamp) {
            case RED -> RED;
            case GREEN -> GREEN;
            case WHITE -> WHITE;
            default -> AMBER;
        };
    }
}
