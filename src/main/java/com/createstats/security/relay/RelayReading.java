package com.createstats.security.relay;

import com.createstats.security.Truth;

public record RelayReading(Truth truth, int sources, int unavailable) {
    public static final RelayReading NONE = new RelayReading(Truth.UNKNOWN, 0, 0);

    public RelayReading {
        truth = truth == null ? Truth.UNKNOWN : truth;
        sources = Math.max(0, sources);
        unavailable = Math.max(0, Math.min(sources, unavailable));
    }

    public static RelayReading of(Truth truth) {
        return new RelayReading(truth, 0, 0);
    }
}
