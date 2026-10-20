package com.createstats.security.relay;

import com.createstats.security.SecurityEndpoint;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public final class RelaySubscription {
    private final UUID relayId;
    private SecurityEndpoint endpoint;
    private RelayConfig config;
    private long revision;
    private RelayState state;
    private final long registeredAt;

    private RelayReading reading = RelayReading.NONE;

    @Nullable
    private final Object unsupported;

    public RelaySubscription(UUID relayId, SecurityEndpoint endpoint, RelayConfig config, long revision,
                             RelayState state, long registeredAt, @Nullable Object unsupported) {
        this.relayId = relayId;
        this.endpoint = endpoint;
        this.config = config;
        this.revision = revision;
        this.state = state;
        this.registeredAt = registeredAt;
        this.unsupported = unsupported;
    }

    public UUID relayId() {
        return this.relayId;
    }

    public SecurityEndpoint endpoint() {
        return this.endpoint;
    }

    public RelayConfig config() {
        return this.config;
    }

    public long revision() {
        return this.revision;
    }

    public RelayState state() {
        return this.state;
    }

    public long registeredAt() {
        return this.registeredAt;
    }

    public RelayReading reading() {
        return this.reading;
    }

    public boolean supported() {
        return this.unsupported == null && this.config.condition() != null;
    }

    @Nullable
    public Object unsupportedTag() {
        return this.unsupported;
    }

    public void moveTo(SecurityEndpoint where) {
        this.endpoint = where;
    }

    public void apply(RelayConfig next, RelayState seeded) {
        this.config = next;
        this.state = seeded;
        this.revision++;
    }

    public void setState(RelayState next) {
        this.state = next;
    }

    public void setReading(RelayReading next) {
        this.reading = next == null ? RelayReading.NONE : next;
    }
}
