package com.createstats.stormcoil;

import com.createstats.security.SecurityMode;
import com.createstats.stormcoil.CoilRules.Grid;
import com.createstats.stormcoil.CoilRules.HouseState;
import com.createstats.stormcoil.CoilRules.Person;
import com.createstats.stormcoil.CoilRules.Policy;
import com.createstats.stormcoil.CoilRules.Rule;
import com.createstats.stormcoil.CoilTargets.Kind;
import com.createstats.stormcoil.CoilTargets.Mode;
import com.createstats.stormcoil.CoilTargets.Target;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoilRulesTest {
    private static final Rule ALL = new Rule(true, true, true, CoilRules.MAX_BELOW);

    private static Target plain(Kind kind) {
        return new Target(kind, true, false, false, false, false, false, false);
    }

    private static List<Target> everyFlag(Kind kind) {
        final List<Target> out = new ArrayList<>();
        for (int bits = 0; bits < 128; bits++) {
            out.add(new Target(kind, (bits & 1) != 0, (bits & 2) != 0, (bits & 4) != 0, (bits & 8) != 0,
                    (bits & 16) != 0, (bits & 32) != 0, (bits & 64) != 0));
        }
        return out;
    }

    @Test
    @DisplayName("each mob group arcs only where its cell is on, in every state")
    void mobGroupsByState() {
        for (HouseState state : HouseState.all()) {
            for (int bits = 0; bits < 16; bits++) {
                final Rule r = new Rule((bits & 1) != 0, (bits & 2) != 0, (bits & 4) != 0, (bits & 8) != 0 ? 3 : 0);
                final Grid g = Grid.NOTHING.with(state, r);
                final Rule got = g.rule(state);
                assertEquals(r, got, "the grid keeps the cell for " + state);
                assertEquals(r.hostile(), CoilRules.allows(got, plain(Kind.HOSTILE), Person.NOT_A_PLAYER, true));
                assertEquals(r.hostile() || r.transform(),
                        CoilRules.allows(got, plain(Kind.CREEPER), Person.NOT_A_PLAYER, true),
                        "a creeper is hostile, and one of the four conversions; " + r);
                for (Kind k : new Kind[] {Kind.VILLAGER, Kind.PIG, Kind.MOOSHROOM}) {
                    assertEquals(r.transform(), CoilRules.allows(got, plain(k), Person.NOT_A_PLAYER, true), k + " " + r);
                }
                for (HouseState other : HouseState.all()) {
                    if (other != state) {
                        assertEquals(Rule.NOTHING, g.rule(other), "editing " + state + " moved " + other);
                    }
                }
            }
        }
    }

    @Test
    @DisplayName("Transform converts each once, skips a charged creeper; Hostile repeats until dead")
    void transformOnceHostileAgain() {
        final Rule transform = new Rule(false, true, false, 0);
        for (Kind k : new Kind[] {Kind.CREEPER, Kind.VILLAGER, Kind.PIG, Kind.MOOSHROOM}) {
            assertTrue(CoilRules.allows(transform, plain(k), Person.NOT_A_PLAYER, true), k.name());
            assertFalse(CoilRules.allows(transform, new Target(k, true, false, false, false, false, false, true),
                    Person.NOT_A_PLAYER, true), "a second arc on the same " + k);
        }
        assertFalse(CoilRules.allows(transform, new Target(Kind.CREEPER, true, false, false, false, false, true, false),
                Person.NOT_A_PLAYER, true));
        assertFalse(CoilRules.allows(transform, plain(Kind.HOSTILE), Person.NOT_A_PLAYER, true));
        final Rule hostile = new Rule(true, false, false, 0);
        assertTrue(CoilRules.allows(hostile, new Target(Kind.HOSTILE, true, false, false, false, false, false, true),
                Person.NOT_A_PLAYER, true), "until dead");
        assertTrue(CoilRules.allows(hostile, new Target(Kind.CREEPER, true, false, false, false, false, true, true),
                Person.NOT_A_PLAYER, true), "a charged creeper is still hostile");
    }

    @Test
    @DisplayName("players: strangers by their cell, members only below the cell's level, keyholders never")
    void players() {
        final Target player = plain(Kind.PLAYER);
        for (HouseState state : HouseState.all()) {
            for (boolean strangers : new boolean[] {false, true}) {
                for (int below : new int[] {0, 2, 3, 4, 5}) {
                    final Rule r = Grid.NOTHING.with(state, new Rule(false, false, strangers, below)).rule(state);
                    assertEquals(strangers, CoilRules.allows(r, player, Person.STRANGER, true), state + " " + r);
                    for (int level = 1; level <= 5; level++) {
                        assertEquals(below != 0 && level < below,
                                CoilRules.allows(r, player, Person.member(level), true),
                                state + " member at " + level + " under " + r);
                    }
                    assertFalse(CoilRules.allows(r, player, Person.KEYHOLDER, true), "owner or admin, " + r);
                    assertFalse(CoilRules.allows(r, player, Person.UNKNOWN, true), "out of contact, " + r);
                    assertFalse(CoilRules.allows(r, player, Person.NOT_A_PLAYER, true), "a player labelled a mob");
                }
            }
        }
    }

    @Test
    @DisplayName("PvP off: no player is ever arced, whatever the rule")
    void pvpOff() {
        for (Person who : new Person[] {Person.STRANGER, Person.member(1), Person.member(4), Person.KEYHOLDER}) {
            assertFalse(CoilRules.allows(ALL, plain(Kind.PLAYER), who, false), who.toString());
        }
        assertTrue(CoilRules.allows(ALL, plain(Kind.HOSTILE), Person.NOT_A_PLAYER, false), "mobs do not care about PvP");
    }

    @Test
    @DisplayName("never, under the widest rule: creative or spectator, golems, tamed, named, leashed, the dead")
    void refusals() {
        for (Kind kind : Kind.values()) {
            final Person who = kind == Kind.PLAYER ? Person.STRANGER : Person.NOT_A_PLAYER;
            assertFalse(CoilRules.allows(ALL, new Target(kind, false, false, false, false, false, false, false), who, true),
                    "dead " + kind);
            assertFalse(CoilRules.allows(ALL, new Target(kind, true, false, false, false, true, false, false), who, true),
                    "creative or spectator " + kind);
            if (kind != Kind.PLAYER) {
                assertFalse(CoilRules.allows(ALL, new Target(kind, true, true, false, false, false, false, false), who, true),
                        "tamed or owned " + kind);
                assertFalse(CoilRules.allows(ALL, new Target(kind, true, false, true, false, false, false, false), who, true),
                        "named " + kind);
                assertFalse(CoilRules.allows(ALL, new Target(kind, true, false, false, true, false, false, false), who, true),
                        "leashed " + kind);
            }
        }
        assertFalse(CoilRules.allows(ALL, plain(Kind.GOLEM), Person.NOT_A_PLAYER, true));
        assertFalse(CoilRules.allows(ALL, plain(Kind.OTHER), Person.NOT_A_PLAYER, true));
        assertFalse(CoilRules.allows(null, plain(Kind.HOSTILE), Person.NOT_A_PLAYER, true));
    }

    @Test
    @DisplayName("a seeded grid arcs at exactly what the coil's own Targets setting did")
    void seedingChangesNothing() {
        for (Mode mode : Mode.values()) {
            final Grid seeded = Grid.ofMode(mode);
            for (HouseState state : HouseState.all()) {
                final Rule r = seeded.rule(state);
                assertFalse(r.arcsAtPlayers(), mode + " never arced at a player");
                for (Kind kind : Kind.values()) {
                    for (Target t : everyFlag(kind)) {
                        final Person who = kind == Kind.PLAYER ? Person.STRANGER : Person.NOT_A_PLAYER;
                        assertEquals(CoilTargets.allows(mode, t), CoilRules.allows(r, t, who, true),
                                mode + " " + state + " " + t);
                    }
                }
            }
            assertEquals(mode.zaps(), seeded.arcsAtAnything(), mode + " zaps");
        }
    }

    @Test
    @DisplayName("an ungoverned policy is CoilTargets unchanged; a governed one is the house's rule")
    void policy() {
        for (Mode mode : Mode.values()) {
            final Policy own = Policy.own(mode);
            assertFalse(own.governedByAHouse());
            assertEquals(mode.zaps(), own.arcsAtAnything());
            for (Kind kind : Kind.values()) {
                for (Target t : everyFlag(kind)) {
                    assertEquals(CoilTargets.allows(mode, t), own.allows(t, Person.STRANGER), mode + " " + t);
                }
            }
        }
        final Policy strangersOnly = Policy.governed(new Rule(false, false, true, 0), true);
        assertTrue(strangersOnly.governedByAHouse());
        assertTrue(strangersOnly.allows(plain(Kind.PLAYER), Person.STRANGER));
        assertFalse(strangersOnly.allows(plain(Kind.HOSTILE), Person.NOT_A_PLAYER));
        assertFalse(Policy.governed(null, true).arcsAtAnything(), "a null rule governs as nothing");
    }

    @Test
    @DisplayName("Lockdown outranks every mode; each mode is its own state")
    void houseState() {
        assertEquals(HouseState.HOME, HouseState.of(SecurityMode.HOME, false));
        assertEquals(HouseState.AWAY, HouseState.of(SecurityMode.AWAY, false));
        assertEquals(HouseState.CUSTOM, HouseState.of(SecurityMode.CUSTOM, false));
        assertEquals(HouseState.AWAY_PLUS, HouseState.of(SecurityMode.AWAY_PLUS, false));
        for (SecurityMode m : SecurityMode.all()) {
            assertEquals(HouseState.LOCKDOWN, HouseState.of(m, true), m.name());
        }
        assertEquals(HouseState.HOME, HouseState.of(null, false));
        assertEquals(HouseState.HOME, HouseState.byOrdinal(-1));
        assertEquals(HouseState.HOME, HouseState.byOrdinal(99));
    }

    @Test
    @DisplayName("rules and grids round-trip; members below normalises to none or 2 to 5")
    void packing() {
        for (int bits = 0; bits < 64; bits++) {
            final Rule r = Rule.unpack(bits);
            assertEquals(r, Rule.unpack(r.pack()), "rule " + bits);
            assertTrue(r.membersBelow() == 0 || (r.membersBelow() >= 2 && r.membersBelow() <= 5), r.toString());
        }
        assertEquals(0, new Rule(false, false, false, 1).membersBelow(), "below 1 is nobody");
        assertEquals(5, new Rule(false, false, false, 9).membersBelow());
        assertEquals(0, new Rule(false, false, false, -3).membersBelow());
        Grid g = Grid.NOTHING;
        int step = 0;
        for (HouseState s : HouseState.all()) {
            g = g.with(s, Rule.unpack(step * 13 + 7));
            step++;
        }
        assertEquals(g, new Grid(g.bits()));
        assertTrue(g.bits() >= 0, "never negative: -1 is the household's 'no grid'");
        assertEquals(Grid.NOTHING, new Grid(-1).with(HouseState.HOME, Rule.NOTHING).with(HouseState.AWAY, Rule.NOTHING)
                .with(HouseState.CUSTOM, Rule.NOTHING).with(HouseState.AWAY_PLUS, Rule.NOTHING)
                .with(HouseState.LOCKDOWN, Rule.NOTHING), "the mask keeps a stray high bit out");
    }

    @Test
    @DisplayName("the members cell steps none, 2, 3, 4, 5 and round, both ways")
    void membersCell() {
        int b = 0;
        final int[] seen = new int[5];
        for (int i = 0; i < 5; i++) {
            seen[i] = b;
            b = CoilRules.nextBelow(b);
        }
        assertEquals(0, b, "five presses come back to none");
        assertEquals(List.of(0, 2, 3, 4, 5), List.of(seen[0], seen[1], seen[2], seen[3], seen[4]));
        for (int v : new int[] {0, 2, 3, 4, 5}) {
            assertEquals(v, CoilRules.previousBelow(CoilRules.nextBelow(v)));
        }
    }

    @Test
    @DisplayName("a cell edit carries its state and its whole rule, and nothing a screen could not send")
    void edits() {
        for (HouseState s : HouseState.all()) {
            for (int bits = 0; bits < 64; bits++) {
                final Rule r = Rule.unpack(bits);
                final int v = CoilRules.edit(s, r);
                assertEquals(s, CoilRules.editState(v));
                assertEquals(r, CoilRules.editRule(v));
            }
        }
        assertNull(CoilRules.editState(-1));
        assertNull(CoilRules.editState(HouseState.all().length * 64));
    }

    @Test
    @DisplayName("what a rule arcs at, for the log")
    void words() {
        assertEquals("nothing", Rule.NOTHING.plain());
        assertEquals("hostile mobs, strangers, members below 3", new Rule(true, false, true, 3).plain());
        assertTrue(new Rule(false, false, false, 2).arcsAtPlayers());
        assertFalse(new Rule(true, true, false, 0).arcsAtPlayers());
    }
}
