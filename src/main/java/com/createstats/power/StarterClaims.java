package com.createstats.power;

import java.util.Map;
import java.util.WeakHashMap;

// weak keyed on the network object, rebuilt networks drop their claim. expiry not a flag
public final class StarterClaims {
    private record Claim(Object holder, long until) {
    }

    private final Map<Object, Claim> claims = new WeakHashMap<>();

    public boolean claim(Object network, Object holder, long now, long until) {
        if (network == null) {
            return true;
        }
        final Claim c = claims.get(network);
        if (c != null && c.holder() != holder && c.until() >= now) {
            return false;
        }
        claims.put(network, new Claim(holder, until));
        return true;
    }

    public boolean heldByOther(Object network, Object holder, long now) {
        if (network == null) {
            return false;
        }
        final Claim c = claims.get(network);
        return c != null && c.holder() != holder && c.until() >= now;
    }
}
