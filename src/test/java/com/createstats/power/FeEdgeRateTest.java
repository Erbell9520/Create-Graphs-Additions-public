package com.createstats.power;

import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FeEdgeRateTest {
    private static final int BASE = 8_192;

    private static final BlockPos LEFT = new BlockPos(0, 70, 0);
    private static final BlockPos RIGHT = new BlockPos(6, 70, 0);

    private static final class Terminal implements FeGridMember, FeWireTerminal {
        private final BlockPos at;
        private final Map<BlockPos, WireTier> spans = new HashMap<>();

        Terminal(BlockPos at) {
            this.at = at;
        }

        Terminal strung(BlockPos to, WireTier tier) {
            spans.put(to, tier);
            return this;
        }

        @Override
        public BlockPos terminalPos() {
            return at;
        }

        @Override
        public List<BlockPos> links() {
            return new ArrayList<>(spans.keySet());
        }

        @Override
        public WireTier tierOf(BlockPos link) {
            return spans.getOrDefault(link, WireTier.COPPER);
        }

        @Override
        public void addLink(BlockPos far, WireTier tier) {
            spans.put(far, tier);
        }

        @Override
        public void removeDeadLink(BlockPos far) {
            spans.remove(far);
        }

        @Override
        public void dropWiresAndUnlinkAll() {
            spans.clear();
        }

        @Override
        public long nodeKey() {
            return at.asLong();
        }

        @Override
        public int storedEnergy() {
            return 0;
        }

        @Override
        public void setStoredEnergy(int amount) {
        }

        @Override
        public int bufferCapacity() {
            return 0;
        }

        @Override
        public int transferRate() {
            return BASE;
        }

        @Override
        public IEnergyStorage attachedStorage() {
            return null;
        }

        @Override
        public boolean isGone(Level level) {
            return false;
        }

        @Override
        public void setLastFlow(int flow) {
        }

        @Override
        public void invalidateNetwork() {
        }
    }

    private static Map<Long, FeGridMember> grid(FeGridMember... members) {
        final Map<Long, FeGridMember> out = new HashMap<>();
        for (FeGridMember member : members) {
            out.put(member.nodeKey(), member);
        }
        return out;
    }

    @Test
    @DisplayName("a wire span is rated by the grade it was strung with")
    void aSpanIsRatedByItsGrade() {
        final Terminal left = new Terminal(LEFT).strung(RIGHT, WireTier.COPPER);
        final Terminal right = new Terminal(RIGHT).strung(LEFT, WireTier.COPPER);
        assertEquals(BASE, FeWireNetwork.cableEdgeRate(grid(left, right),
                LEFT.asLong(), RIGHT.asLong()));
    }

    @Test
    @DisplayName("the thinner end of a mismatched span decides, whichever end it is")
    void theThinnerEndDecides() {
        final Terminal left = new Terminal(LEFT).strung(RIGHT, WireTier.SHIELDED);
        final Terminal right = new Terminal(RIGHT).strung(LEFT, WireTier.COPPER);
        assertEquals(BASE, FeWireNetwork.cableEdgeRate(grid(left, right),
                LEFT.asLong(), RIGHT.asLong()));
        assertEquals(BASE, FeWireNetwork.cableEdgeRate(grid(left, right),
                RIGHT.asLong(), LEFT.asLong()),
                "and the answer cannot depend on which way round the edge is read");
    }

    @Test
    @DisplayName("a better grade of span carries its multiple")
    void aBetterSpanCarriesMore() {
        final Terminal left = new Terminal(LEFT).strung(RIGHT, WireTier.SHIELDED);
        final Terminal right = new Terminal(RIGHT).strung(LEFT, WireTier.SHIELDED);
        assertEquals(BASE * WireTier.SHIELDED.multiplier(),
                FeWireNetwork.cableEdgeRate(grid(left, right), LEFT.asLong(), RIGHT.asLong()));
    }

    @Test
    @DisplayName("an HV Line span carries eight times the base, the whole of an Industrial Connector")
    void anHvSpanCarriesEightTimesTheBase() {
        final Terminal left = new Terminal(LEFT).strung(RIGHT, WireTier.HV);
        final Terminal right = new Terminal(RIGHT).strung(LEFT, WireTier.HV);
        assertEquals(65_536, FeWireNetwork.cableEdgeRate(grid(left, right), LEFT.asLong(), RIGHT.asLong()));
        assertEquals(BASE * ConnectorTier.INDUSTRIAL.multiplier(),
                FeWireNetwork.cableEdgeRate(grid(left, right), LEFT.asLong(), RIGHT.asLong()),
                "one HV span is exactly one Industrial Connector");
    }

    @Test
    @DisplayName("two terminals touching by face are joined by no conductor at all")
    void touchingIsNotASpan() {
        final Terminal left = new Terminal(LEFT);
        final Terminal right = new Terminal(RIGHT);
        assertEquals(FeGridSolver.NO_CEILING, FeWireNetwork.cableEdgeRate(grid(left, right),
                LEFT.asLong(), RIGHT.asLong()));
    }

    @Test
    @DisplayName("an edge to a node this grid has never heard of has no rate")
    void anUnknownNodeHasNoRate() {
        final Terminal left = new Terminal(LEFT).strung(RIGHT, WireTier.COPPER);
        assertEquals(BASE, FeWireNetwork.cableEdgeRate(grid(left), LEFT.asLong(), RIGHT.asLong()),
                "the end that does know about the span still rates it");
        assertEquals(FeGridSolver.NO_CEILING, FeWireNetwork.cableEdgeRate(grid(),
                LEFT.asLong(), RIGHT.asLong()));
    }
}
