package com.createstats.security;

import com.createstats.stormcoil.CoilRules;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.List;

public final class CoilWords {
    private CoilWords() {
    }

    public static MutableComponent rule(CoilRules.Rule r) {
        final List<Component> parts = new ArrayList<>(4);
        if (r.hostile()) {
            parts.add(Component.translatable("coil.create_stats.rule.hostile"));
        }
        if (r.transform()) {
            parts.add(Component.translatable("coil.create_stats.rule.transform"));
        }
        if (r.strangers()) {
            parts.add(Component.translatable("coil.create_stats.rule.strangers"));
        }
        if (r.membersBelow() != CoilRules.NO_MEMBERS) {
            parts.add(Component.translatable("coil.create_stats.rule.members", r.membersBelow()));
        }
        if (parts.isEmpty()) {
            return Component.translatable("coil.create_stats.rule.nothing");
        }
        if (parts.size() == 1) {
            return parts.get(0).copy();
        }
        final MutableComponent head = parts.get(0).copy();
        for (int i = 1; i < parts.size() - 1; i++) {
            head.append(", ").append(parts.get(i));
        }
        return Component.translatable("coil.create_stats.rule.and", head, parts.get(parts.size() - 1));
    }

    public static MutableComponent state(CoilRules.HouseState s) {
        return Component.translatable("coil.create_stats.state." + s.key());
    }

    public static MutableComponent status(CoilStatus s) {
        return Component.translatable("coil.create_stats.status." + s.key());
    }
}
