package com.createstats.stormcoil;

import com.createstats.security.SecurityMode;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class CoilRules {
    private CoilRules() {
    }

    public enum HouseState {
        HOME("home"),
        AWAY("away"),
        CUSTOM("custom"),
        AWAY_PLUS("away_plus"),
        LOCKDOWN("lockdown");

        private static final HouseState[] ORDER = values();

        private final String key;

        HouseState(String key) {
            this.key = key;
        }

        public String key() {
            return this.key;
        }

        public String plain() {
            return this == AWAY_PLUS ? "AWAY+" : name();
        }

        public static HouseState of(@Nullable SecurityMode mode, boolean lockdown) {
            if (lockdown) {
                return LOCKDOWN;
            }
            if (mode == null) {
                return HOME;
            }
            return switch (mode) {
                case HOME -> HOME;
                case AWAY -> AWAY;
                case CUSTOM -> CUSTOM;
                case AWAY_PLUS -> AWAY_PLUS;
            };
        }

        public static HouseState byOrdinal(int ordinal) {
            return ordinal >= 0 && ordinal < ORDER.length ? ORDER[ordinal] : HOME;
        }

        public static HouseState[] all() {
            return ORDER;
        }
    }

    public enum Standing {
        NOT_A_PLAYER,

        STRANGER,

        MEMBER,

        KEYHOLDER,

        UNKNOWN
    }

    public record Person(Standing standing, int level) {
        public static final Person NOT_A_PLAYER = new Person(Standing.NOT_A_PLAYER, 0);
        public static final Person STRANGER = new Person(Standing.STRANGER, 0);
        public static final Person KEYHOLDER = new Person(Standing.KEYHOLDER, 0);
        public static final Person UNKNOWN = new Person(Standing.UNKNOWN, 0);

        public Person {
            standing = standing == null ? Standing.UNKNOWN : standing;
        }

        public static Person member(int level) {
            return new Person(Standing.MEMBER, level);
        }
    }

    public static final int NO_MEMBERS = 0;

    public static final int MIN_BELOW = 2;

    public static final int MAX_BELOW = 5;

    public record Rule(boolean hostile, boolean transform, boolean strangers, int membersBelow) {
        public static final Rule NOTHING = new Rule(false, false, false, NO_MEMBERS);

        public Rule {
            membersBelow = normaliseBelow(membersBelow);
        }

        public boolean arcsAtAnything() {
            return this.hostile || this.transform || arcsAtPlayers();
        }

        public boolean arcsAtPlayers() {
            return this.strangers || this.membersBelow != NO_MEMBERS;
        }

        public Rule withHostile(boolean on) {
            return new Rule(on, this.transform, this.strangers, this.membersBelow);
        }

        public Rule withTransform(boolean on) {
            return new Rule(this.hostile, on, this.strangers, this.membersBelow);
        }

        public Rule withStrangers(boolean on) {
            return new Rule(this.hostile, this.transform, on, this.membersBelow);
        }

        public Rule withMembersBelow(int below) {
            return new Rule(this.hostile, this.transform, this.strangers, below);
        }

        public int pack() {
            return (this.hostile ? 1 : 0) | (this.transform ? 2 : 0) | (this.strangers ? 4 : 0)
                    | (this.membersBelow << 3);
        }

        public static Rule unpack(int bits) {
            return new Rule((bits & 1) != 0, (bits & 2) != 0, (bits & 4) != 0, (bits >>> 3) & 7);
        }

        public String plain() {
            final List<String> parts = new ArrayList<>(4);
            if (this.hostile) {
                parts.add("hostile mobs");
            }
            if (this.transform) {
                parts.add("transforms");
            }
            if (this.strangers) {
                parts.add("strangers");
            }
            if (this.membersBelow != NO_MEMBERS) {
                parts.add("members below " + this.membersBelow);
            }
            return parts.isEmpty() ? "nothing" : String.join(", ", parts);
        }
    }

    public static int nextBelow(int below) {
        final int b = normaliseBelow(below);
        return b == NO_MEMBERS ? MIN_BELOW : b >= MAX_BELOW ? NO_MEMBERS : b + 1;
    }

    public static int previousBelow(int below) {
        final int b = normaliseBelow(below);
        return b == NO_MEMBERS ? MAX_BELOW : b <= MIN_BELOW ? NO_MEMBERS : b - 1;
    }

    private static int normaliseBelow(int below) {
        if (below < MIN_BELOW) {
            return NO_MEMBERS;
        }
        return Math.min(MAX_BELOW, below);
    }

    public record Grid(int bits) {
        public static final Grid NOTHING = new Grid(0);

        public Grid {
            bits = bits & ((1 << (6 * HouseState.all().length)) - 1);
        }

        public Rule rule(HouseState state) {
            return Rule.unpack(this.bits >>> (6 * state.ordinal()) & 63);
        }

        public Grid with(HouseState state, Rule rule) {
            final int shift = 6 * state.ordinal();
            return new Grid((this.bits & ~(63 << shift)) | (rule.pack() << shift));
        }

        public boolean arcsAtAnything() {
            for (HouseState s : HouseState.all()) {
                if (rule(s).arcsAtAnything()) {
                    return true;
                }
            }
            return false;
        }

        public static Grid every(Rule rule) {
            Grid g = NOTHING;
            for (HouseState s : HouseState.all()) {
                g = g.with(s, rule);
            }
            return g;
        }

        public static Grid ofMode(CoilTargets.Mode mode) {
            return every(ruleOf(mode));
        }
    }

    public static Rule ruleOf(CoilTargets.Mode mode) {
        if (mode == null) {
            return Rule.NOTHING;
        }
        return switch (mode) {
            case HOSTILE -> new Rule(true, false, false, NO_MEMBERS);
            case TRANSFORM -> new Rule(false, true, false, NO_MEMBERS);
            case STORM_ONLY -> Rule.NOTHING;
        };
    }

    public static boolean allows(@Nullable Rule rule, CoilTargets.Target t, Person who, boolean pvp) {
        if (rule == null || t == null || !t.alive() || t.creativeOrSpectator()) {
            return false;
        }
        if (t.kind() == CoilTargets.Kind.PLAYER) {
            if (!pvp || who == null) {
                return false;
            }
            return switch (who.standing()) {
                case STRANGER -> rule.strangers();
                case MEMBER -> rule.membersBelow() != NO_MEMBERS && who.level() < rule.membersBelow();
                case KEYHOLDER, UNKNOWN, NOT_A_PLAYER -> false;
            };
        }
        if (t.kind() == CoilTargets.Kind.GOLEM || t.tamedOrOwned() || t.named() || t.leashed()) {
            return false;
        }
        if (rule.hostile() && (t.kind() == CoilTargets.Kind.HOSTILE || t.kind() == CoilTargets.Kind.CREEPER)) {
            return true;
        }
        return rule.transform() && CoilTargets.transforms(t.kind()) && !t.alreadyZapped()
                && !(t.kind() == CoilTargets.Kind.CREEPER && t.charged());
    }

    public record Policy(CoilTargets.Mode mode, @Nullable Rule rule, boolean pvp) {
        public static Policy own(CoilTargets.Mode mode) {
            return new Policy(mode, null, false);
        }

        public static Policy governed(Rule rule, boolean pvp) {
            return new Policy(CoilTargets.Mode.HOSTILE, rule == null ? Rule.NOTHING : rule, pvp);
        }

        public boolean governedByAHouse() {
            return this.rule != null;
        }

        public boolean allows(CoilTargets.Target t, Person who) {
            return this.rule == null ? CoilTargets.allows(this.mode, t) : CoilRules.allows(this.rule, t, who, this.pvp);
        }

        public boolean arcsAtAnything() {
            return this.rule == null ? this.mode.zaps() : this.rule.arcsAtAnything();
        }
    }

    public static int edit(HouseState state, Rule rule) {
        return state.ordinal() * 64 + rule.pack();
    }

    @Nullable
    public static HouseState editState(int value) {
        if (value < 0) {
            return null;
        }
        final int s = value >>> 6;
        return s < HouseState.all().length ? HouseState.all()[s] : null;
    }

    public static Rule editRule(int value) {
        return Rule.unpack(value & 63);
    }
}
