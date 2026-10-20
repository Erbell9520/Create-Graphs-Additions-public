package com.createstats.battle.core;

import java.util.Set;

public final class SystemRules {
    public static final String TAG_PREFIX = "create_stats:battle/";
    public static final String TAG_NATURAL = TAG_PREFIX + "natural";
    public static final String TAG_IGNORE = TAG_PREFIX + "ignore";

    private static final BattleSystem[] TAG_ORDER = {
            BattleSystem.GUNS, BattleSystem.LIFT, BattleSystem.PROPULSION, BattleSystem.POWER,
            BattleSystem.CONTROL, BattleSystem.CARGO, BattleSystem.HULL};

    private static final Set<String> LIFT_TAGS = Set.of("aeronautics:envelope", "aeronautics:levitite");
    private static final Set<String> LIFT_IDS = Set.of("aeronautics:adjustable_burner", "aeronautics:steam_vent");

    private SystemRules() {
    }

    public record Descriptor(String id, Set<String> tags, boolean propeller, boolean kineticSource, boolean governor,
                             boolean kinetic, boolean energy, boolean storage, boolean control) {
        public static Descriptor plain(String id) {
            return new Descriptor(id, Set.of(), false, false, false, false, false, false, false);
        }

        String namespace() {
            int colon = id.indexOf(':');
            return colon < 0 ? "minecraft" : id.substring(0, colon);
        }
    }

    public static BattleSystem classify(Descriptor d) {
        for (BattleSystem s : TAG_ORDER) {
            if (d.tags().contains(TAG_PREFIX + s.key())) return s;
        }
        if ("createbigcannons".equals(d.namespace())) return BattleSystem.GUNS;
        if (LIFT_IDS.contains(d.id())) return BattleSystem.LIFT;
        for (String t : LIFT_TAGS) {
            if (d.tags().contains(t)) return BattleSystem.LIFT;
        }
        if (d.propeller() || d.kineticSource() || d.governor()) return BattleSystem.PROPULSION;
        if (d.energy()) return BattleSystem.POWER;
        if (d.control()) return BattleSystem.CONTROL;
        if (d.storage() && !d.kinetic()) return BattleSystem.CARGO;
        return BattleSystem.HULL;
    }
}
