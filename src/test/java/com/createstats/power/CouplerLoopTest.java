package com.createstats.power;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CouplerLoopTest {
    private static CouplerLoop.Node node(boolean pulling, Set<Object> ours, Set<Object> theirs) {
        return new CouplerLoop.Node(pulling, ours, theirs, false);
    }

    @Test
    @DisplayName("one PULL and one PUSH on one circuit and one foreign network circle FE")
    void opposedPairOnSharedIdentitiesLoops() {
        final CouplerLoop.Node pull = node(true, Set.of("grid A"), Set.of("cna:7", 100L));
        final CouplerLoop.Node push = node(false, Set.of("grid A"), Set.of("cna:7", 200L));
        assertTrue(CouplerLoop.looped(pull, List.of(push)));
        assertTrue(CouplerLoop.looped(push, List.of(pull)));
    }

    @Test
    @DisplayName("wired Couplers sharing a far block loop, though neither is bolted to anything")
    void wiredPairOnOneFarBlock() {
        final CouplerLoop.Node a = node(true, Set.of("grid A"), Set.of(555L));
        final CouplerLoop.Node b = node(false, Set.of("grid A"), Set.of(555L));
        assertTrue(CouplerLoop.looped(a, List.of(b)));
    }

    @Test
    @DisplayName("a wire-fed grid side counts: the circuit comes from the far ends of the grid wires too")
    void wireFedGridSide() {
        final CouplerLoop.Node a = node(true, Set.of("grid via wire"), Set.of("ca:net 3"));
        final CouplerLoop.Node b = node(false, Set.of("grid on face", "grid via wire"), Set.of("ca:net 3"));
        assertTrue(CouplerLoop.looped(a, List.of(b)));
    }

    @Test
    @DisplayName("facing the same way is no loop: two importers both fill, two exporters both drain")
    void sameModeIsNoLoop() {
        assertFalse(CouplerLoop.looped(node(true, Set.of("A"), Set.of(1L)), List.of(node(true, Set.of("A"), Set.of(1L)))));
        assertFalse(CouplerLoop.looped(node(false, Set.of("A"), Set.of(1L)), List.of(node(false, Set.of("A"), Set.of(1L)))));
    }

    @Test
    @DisplayName("different circuits, or different foreign sides, are no loop")
    void needsBothIdentities() {
        assertFalse(CouplerLoop.looped(node(true, Set.of("A"), Set.of(1L)), List.of(node(false, Set.of("B"), Set.of(1L)))));
        assertFalse(CouplerLoop.looped(node(true, Set.of("A"), Set.of(1L)), List.of(node(false, Set.of("A"), Set.of(2L)))));
        assertFalse(CouplerLoop.looped(node(true, Set.of(), Set.of(1L)), List.of(node(false, Set.of(), Set.of(1L)))),
                "a Coupler on no circuit of ours circles nothing");
    }

    @Test
    @DisplayName("one Coupler whose foreign battery our own cable also touches circles on its own, either mode")
    void endpointOnOurCircuit() {
        for (boolean pulling : new boolean[] {true, false}) {
            final CouplerLoop.Node self = new CouplerLoop.Node(pulling, Set.of("A"), Set.of(9L), true);
            assertTrue(CouplerLoop.looped(self, List.of()), "pulling=" + pulling);
        }
        assertFalse(CouplerLoop.looped(new CouplerLoop.Node(true, Set.of(), Set.of(9L), true), List.of()),
                "with no circuit of ours there is no circle to close");
    }

    @Test
    @DisplayName("the registry keys on game time: a node older than two beats stops counting, and forget drops one")
    void registryAges() {
        final CouplerLoop.Registry reg = new CouplerLoop.Registry();
        final CouplerLoop.Node pull = node(true, Set.of("A"), Set.of(1L));
        final CouplerLoop.Node push = node(false, Set.of("A"), Set.of(1L));
        assertFalse(reg.publishAndAsk(1L, pull, 100), "alone");
        assertTrue(reg.publishAndAsk(2L, push, 100), "the partner published this beat");
        assertTrue(reg.publishAndAsk(1L, pull, 120), "the partner is one beat old");
        assertFalse(reg.publishAndAsk(1L, pull, 100 + CouplerLoop.Registry.STALE_TICKS + 1), "the partner went quiet");
        assertEquals(1, reg.size());
        reg.forget(1L);
        assertEquals(0, reg.size());
        assertFalse(CouplerLoop.circles(pull, pull), "a Coupler never loops with itself");
    }
}
