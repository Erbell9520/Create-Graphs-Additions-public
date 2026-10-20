package com.createstats.security;

import com.createstats.security.relay.RelayContact;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public interface RelayDevice extends GovernedDevice {
    @Nullable
    UUID relayId();

    RelayContact contact();

    boolean powered();

    int output();

    @Nullable
    UUID placer();
}
