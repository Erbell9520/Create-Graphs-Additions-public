package com.createstats.power;

import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class FeGridMemberDefaultsTest {
    private record BareMember(long key) implements FeGridMember {
        @Override
        public long nodeKey() {
            return key;
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
            return 0;
        }

        @Override
        public IEnergyStorage attachedStorage() {
            return null;
        }

        @Override
        public boolean isGone(net.minecraft.world.level.Level level) {
            return false;
        }

        @Override
        public void setLastFlow(int flow) {
        }

        @Override
        public void invalidateNetwork() {
        }
    }

    @Test
    void aMemberThatKeepsNoNetworkAnswersNull() {
        assertNull(new BareMember(BlockPos.ZERO.asLong()).network());
    }

    @Test
    void aMemberThatKeepsNoFlowAnswersZeroRatherThanTheLastValueItWasHanded() {
        final BareMember member = new BareMember(BlockPos.ZERO.asLong());
        member.setLastFlow(4096);
        assertEquals(0, member.lastFlow());
    }

    @Test
    void aMemberThatKeepsNoNetworkIsNotChargedForItsConductors() {
        final BareMember member = new BareMember(BlockPos.ZERO.asLong());
        assertEquals(FeGridSolver.NO_CEILING, member.deliveryAllowance());

        member.bookDelivery(4096);
        assertEquals(FeGridSolver.NO_CEILING, member.deliveryAllowance());
    }
}
