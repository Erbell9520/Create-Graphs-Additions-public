package com.createstats.security.relay;

import com.createstats.security.SecurityEndpoint;
import com.createstats.security.Truth;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

// no baseline saved, after a reload it's a claim about a world nobody's looked at
public final class RelayCodec {
    private RelayCodec() {
    }

    public static CompoundTag save(RelaySubscription sub) {
        if (sub.unsupportedTag() instanceof CompoundTag raw) {
            return raw.copy();
        }
        final CompoundTag tag = new CompoundTag();
        tag.putUUID("Relay", sub.relayId());
        putEndpoint(tag, "", sub.endpoint());
        final RelayConfig c = sub.config();
        tag.putString("Cond", c.conditionId());
        tag.putString("Beh", c.behavior().key());
        tag.putInt("Pulse", c.pulseTicks());
        tag.putString("ModeKey", c.mode());
        if (c.scope() != null) {
            putEndpoint(tag, "Scope", c.scope());
        }
        tag.putLong("Rev", sub.revision());
        final RelayState s = sub.state();
        tag.putLong("Seq", s.lastEventSequence());
        tag.putLong("Until", s.pulseUntil());
        tag.putBoolean("Held", s.pending());
        tag.putLong("Trig", s.lastTriggerAt());
        tag.putLong("At", sub.registeredAt());
        return tag;
    }

    @Nullable
    public static RelaySubscription load(CompoundTag tag) {
        if (!tag.hasUUID("Relay")) {
            return null;
        }
        final SecurityEndpoint endpoint = endpoint(tag, "");
        if (endpoint == null) {
            return null;
        }
        final UUID id = tag.getUUID("Relay");
        final RelayBehavior behavior = RelayBehavior.byKey(tag.getString("Beh"));
        final String cond = tag.getString("Cond");
        final boolean known = behavior != null && RelayConditions.byId(cond) != null;
        final int pulse = Math.max(RelayConfig.MIN_PULSE, Math.min(RelayConfig.MAX_PULSE,
                tag.contains("Pulse") ? tag.getInt("Pulse") : RelayConfig.DEFAULT_PULSE));
        final RelayConfig config = new RelayConfig(cond, behavior == null ? RelayBehavior.FOLLOW : behavior,
                pulse, tag.getString("ModeKey"), endpoint(tag, "Scope"));
        final RelayState state = new RelayState(tag.getLong("Seq"),
                tag.contains("Until") ? tag.getLong("Until") : RelayState.NEVER,
                tag.getBoolean("Held"), Truth.UNKNOWN,
                tag.contains("Trig") ? tag.getLong("Trig") : RelayState.NEVER);
        return new RelaySubscription(id, endpoint, config, Math.max(0L, tag.getLong("Rev")), state,
                tag.getLong("At"), known ? null : tag.copy());
    }

    public static void putEndpoint(CompoundTag tag, String prefix, SecurityEndpoint e) {
        tag.putString(prefix + "D", e.dimension());
        tag.putLong(prefix + "P", e.pos());
    }

    @Nullable
    public static SecurityEndpoint endpoint(CompoundTag tag, String prefix) {
        if (!tag.contains(prefix + "P", Tag.TAG_LONG)) {
            return null;
        }
        return SecurityEndpoint.of(tag.getString(prefix + "D"), tag.getLong(prefix + "P"));
    }
}
