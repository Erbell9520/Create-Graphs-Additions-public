package com.createstats.vehiclelight;

public final class LampShow {
    public static final double LIT = 0.3;

    private LampShow() {
    }

    public record Lens(double level, VehicleLightGlow shown, boolean hailing) {
        public boolean lit() {
            return this.level >= LIT;
        }

        public int emission(VehicleLightKind kind) {
            if (!this.hailing) {
                return kind.emission(this.shown);
            }
            return (int) Math.round(kind.emission(VehicleLightGlow.BRIGHT) * Math.max(0.0, Math.min(1.0, this.level)));
        }
    }

    public static Lens lens(VehicleLightKind kind, VehicleLightGlow state, long gameTime, float partialTick,
                            HailSequence.Hail hail, boolean cut) {
        final VehicleLightGlow shown = kind.shown(state, gameTime);
        final double real = shown.level();
        if (hail == null || cut) {
            return new Lens(real, shown, false);
        }
        final double t = hail.seconds(gameTime, partialTick);
        final double o = HailSequence.lens(hail.role(), t, hail.place(), real,
                HailSequence.signalling(hail.role(), state));
        return Double.isNaN(o) ? new Lens(real, shown, false) : new Lens(o, shown, true);
    }
}
