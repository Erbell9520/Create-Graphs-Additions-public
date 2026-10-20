package com.createstats.security;

public record SecurityEvent(long sequence, String kind, SecurityEndpoint source, long acceptedAt) {
    public static final String TAMPER_ACCEPTED = "create_stats:tamper_accepted";

    public static final String ACCESS_DENIED = "create_stats:access_denied";

    public static final String INTRUSION_ACCEPTED = "create_stats:intrusion_accepted";
}
