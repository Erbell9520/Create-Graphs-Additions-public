package com.createstats.dynlight;

// two shapes only, point and beam
public sealed interface Light permits PointLight, BeamLight {
    double x();

    double y();

    double z();

    default boolean isValid() {
        return Double.isFinite(this.x()) && Double.isFinite(this.y()) && Double.isFinite(this.z());
    }
}
