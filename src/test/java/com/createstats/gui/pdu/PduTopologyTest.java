package com.createstats.gui.pdu;

import com.createstats.gui.pdu.PduTopology.BranchNode;
import com.createstats.gui.pdu.PduTopology.Kind;
import com.createstats.gui.pdu.PduTopology.RawNode;
import com.createstats.gui.pdu.PduTopology.Result;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** [1] */
class PduTopologyTest {

    private static RawNode node(BlockPos pos, Kind kind) {
        return new RawNode(pos, (byte) kind.ordinal(), (byte) 0, false, List.of());
    }

    private static BlockPos at(int x) {
        return new BlockPos(x, 64, 0);
    }

    @Test
    void aLoneRootIsItsOwnTree() {
        final Result r = PduTopology.build(List.of(node(at(0), Kind.PDU)), at(0), 256);
        assertEquals(1, r.nodes().size());
        assertEquals(-1, r.nodes().get(0).parentIndex());
        assertFalse(r.truncated());
    }

    @Test
    void aChainGetsSequentialParents() {
        // PDU at 0, cable at 1, device at 2, each touching the next.
        final List<RawNode> raws = List.of(
                node(at(0), Kind.PDU), node(at(1), Kind.CABLE), node(at(2), Kind.DEVICE));
        final Result r = PduTopology.build(raws, at(0), 256);
        assertEquals(3, r.nodes().size());
        assertEquals(-1, r.nodes().get(0).parentIndex());
        assertEquals(0, r.nodes().get(1).parentIndex(), "cable hangs off the PDU");
        assertEquals(1, r.nodes().get(2).parentIndex(), "device hangs off the cable");
    }

    @Test
    void aForkGivesTwoChildrenTheSameParent() {
        // PDU at 0, with a device at each of two neighbours.
        final List<RawNode> raws = List.of(
                node(new BlockPos(0, 64, 0), Kind.PDU),
                node(new BlockPos(1, 64, 0), Kind.DEVICE),
                node(new BlockPos(0, 64, 1), Kind.DEVICE));
        final Result r = PduTopology.build(raws, new BlockPos(0, 64, 0), 256);
        assertEquals(3, r.nodes().size());
        assertEquals(0, r.nodes().get(1).parentIndex());
        assertEquals(0, r.nodes().get(2).parentIndex());
    }

    @Test
    void aConnectorLinkConnectsAcrossOpenAir() {
        // Two connectors ten blocks apart, not touching, joined only by a wire link.
        final RawNode a = new RawNode(at(0), (byte) Kind.PDU.ordinal(), (byte) 0, false, List.of(at(10)));
        final RawNode b = node(at(10), Kind.CONNECTOR);
        final Result r = PduTopology.build(List.of(a, b), at(0), 256);
        assertEquals(2, r.nodes().size(), "the link bridges the gap");
        assertEquals(0, r.nodes().get(1).parentIndex());
    }

    @Test
    void anUnreachableNodeIsLeftOut() {
        final List<RawNode> raws = List.of(
                node(at(0), Kind.PDU),
                node(at(1), Kind.CABLE),
                node(at(50), Kind.DEVICE)); // an island, touching nothing
        final Result r = PduTopology.build(raws, at(0), 256);
        assertEquals(2, r.nodes().size(), "the island is not on this branch");
    }

    @Test
    void aMissingRootYieldsNothing() {
        assertTrue(PduTopology.build(List.of(node(at(1), Kind.CABLE)), at(999), 256).nodes().isEmpty());
    }

    @Test
    void aBranchLargerThanTheCapTruncates() {
        final List<RawNode> raws = new ArrayList<>();
        raws.add(node(at(0), Kind.PDU));
        for (int i = 1; i < 20; i++) {
            raws.add(node(at(i), Kind.CABLE));
        }
        final Result r = PduTopology.build(raws, at(0), 5);
        assertEquals(5, r.nodes().size());
        assertTrue(r.truncated());
    }

    @Test
    void parentsAlwaysPrecedeChildren() {
        final List<RawNode> raws = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            raws.add(node(at(i), i == 0 ? Kind.PDU : Kind.CABLE));
        }
        final Result r = PduTopology.build(raws, at(0), 256);
        for (BranchNode n : r.nodes()) {
            assertTrue(n.parentIndex() < r.nodes().indexOf(n), "a parent must be earlier in the list");
        }
    }
}
