package com.createstats.security;

import java.util.Comparator;

public record SecurityEndpoint(String dimension, long pos) implements Comparable<SecurityEndpoint> {
    public static final String NO_DIMENSION = "";

    private static final Comparator<SecurityEndpoint> ORDER = Comparator
            .comparing(SecurityEndpoint::dimension)
            .thenComparingLong(SecurityEndpoint::pos);

    public SecurityEndpoint {
        dimension = dimension == null ? NO_DIMENSION : dimension;
    }

    public static SecurityEndpoint of(String dimension, long pos) {
        return new SecurityEndpoint(dimension, pos);
    }

    public static SecurityEndpoint legacy(long pos) {
        return new SecurityEndpoint(NO_DIMENSION, pos);
    }

    public boolean isLegacy() {
        return this.dimension.isEmpty();
    }

    public boolean inDimension(String other) {
        return !isLegacy() && this.dimension.equals(other);
    }

    public SecurityEndpoint in(String settled) {
        return new SecurityEndpoint(settled, this.pos);
    }

    @Override
    public int compareTo(SecurityEndpoint other) {
        return ORDER.compare(this, other);
    }
}
