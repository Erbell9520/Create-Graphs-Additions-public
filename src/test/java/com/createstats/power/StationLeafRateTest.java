package com.createstats.power;

import com.createstats.chunkloader.core.CrownHold;
import com.createstats.chunkloader.core.CrownLaw;
import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StationLeafRateTest {
    private static final CrownLaw LAW = CrownLaw.DEFAULT;
    private static final BlockPos AT = new BlockPos(158, 67, -382);

    private static final int FILL_TICKS = 40;

    private static final class Crown implements FeGridNode {
        int picks = 1;
        int held;
        long now;
        int releases;
        final CrownHold hold = new CrownHold();
        final FeIntakeBudget intake = new FeIntakeBudget();

        final IEnergyStorage handler = new IEnergyStorage() {
            @Override
            public int receiveEnergy(int maxReceive, boolean simulate) {
                if (maxReceive <= 0) {
                    return 0;
                }
                final long capacity = LAW.storeCapacity(picks);
                final long draw = LAW.totalDraw(held);
                final int rate = transferRate();
                final int take = (int) Math.min(Math.min(maxReceive, hold.room(capacity, draw)),
                        intake.remaining(rate, now));
                if (take > 0 && !simulate) {
                    hold.accept(take, capacity, draw);
                    intake.spend(rate, now, take);
                }
                return Math.max(0, take);
            }

            @Override
            public int extractEnergy(int maxExtract, boolean simulate) {
                return 0;
            }

            @Override
            public int getEnergyStored() {
                return (int) Math.min(Integer.MAX_VALUE, hold.stored());
            }

            @Override
            public int getMaxEnergyStored() {
                return (int) Math.min(Integer.MAX_VALUE,
                        Math.max(LAW.storeCapacity(picks) + LAW.totalDraw(held), hold.stored()));
            }

            @Override
            public boolean canExtract() {
                return false;
            }

            @Override
            public boolean canReceive() {
                return true;
            }
        };

        @Override
        public int transferRate() {
            return 2 * Math.max(LAW.totalDraw(1), LAW.totalDraw(picks));
        }

        @Override
        public long nodeKey() {
            return AT.asLong();
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
        public IEnergyStorage attachedStorage() {
            return handler;
        }

        void step() {
            final CrownHold.Verdict v = hold.step(held, picks, LAW, true);
            if (v == CrownHold.Verdict.ACQUIRE) {
                held++;
            } else if (v == CrownHold.Verdict.RELEASE_ALL) {
                held = 0;
                releases++;
            }
        }
    }

    private static int serve(Crown crown, FeGridNode leaf) {
        crown.now++;
        final Map<Long, Integer> flow = new HashMap<>();
        final int cost = LAW.totalDraw(crown.picks);
        final int fill = Math.max(1, crown.handler.getMaxEnergyStored() / FILL_TICKS);
        final int run = FeGridSolver.deliver(List.of(leaf), 1_000_000, 0, flow, Map.of(leaf.nodeKey(), cost));
        final int topUp = FeGridSolver.deliver(List.of(leaf), 1_000_000, 0, flow, Map.of(leaf.nodeKey(), fill));
        crown.step();
        return run + topUp;
    }

    @Test
    @DisplayName("KI-180: a leaf copied at one pick takes 4,096 a tick at four picks, and four chunks drop")
    void theCopiedRatingIsTheOwnersReading() {
        final Crown crown = new Crown();

        final FeAttachment frozen = new FeAttachment(AT, crown.handler, crown.transferRate());
        assertEquals(2_048, frozen.transferRate());
        crown.picks = 4;
        int mostIntake = 0;
        int mostHeld = 0;
        for (int t = 0; t < 6_000; t++) {
            mostIntake = Math.max(mostIntake, serve(crown, frozen));
            mostHeld = Math.max(mostHeld, crown.held);
        }
        assertEquals(4_096, mostIntake, "two serves a tick, each held to the copied 2,048");
        assertEquals(4, mostHeld, "it reaches four chunks");
        assertTrue(crown.releases > 0, "and four chunks (4,752) on 4,096 ride the store down and drop");
    }

    @Test
    @DisplayName("KI-180: a station's leaf asks the station, so four picks hold on the same wire")
    void theLiveRatingHoldsFourChunks() {
        final Crown crown = new Crown();
        final FeAttachment leaf = FeAttachment.station(AT, crown.handler, crown);
        assertEquals(2_048, leaf.transferRate());
        crown.picks = 4;
        assertEquals(9_504, leaf.transferRate(), "twice four picks' draw, read when asked");
        for (int t = 0; t < 6_000; t++) {
            serve(crown, leaf);
        }
        assertEquals(4, crown.held);
        assertEquals(0, crown.releases, "never dropped");
        assertEquals(CrownHold.Phase.FED, crown.hold.phase());
    }

    @Test
    @DisplayName("KI-180: every flood-time wrap keeps the station's rating live, capped by what the wrap adds")
    void aWrapKeepsTheSource() {
        final Crown crown = new Crown();
        final FeAttachment leaf = FeAttachment.station(AT, crown.handler, crown);
        final FeAttachment wrapped = leaf.over(crown.handler, leaf.rate());
        final FeAttachment capped = leaf.over(crown.handler, 3_000);
        crown.picks = 25;
        assertEquals(2 * LAW.totalDraw(25), wrapped.transferRate());
        assertEquals(3_000, capped.transferRate(), "a run budget's cap still binds below the station's rating");
        final FeAttachment plain = new FeAttachment(AT, crown.handler, 8_192);
        assertEquals(8_192, plain.over(crown.handler, plain.rate()).transferRate(),
                "an attachment rated by its wiring is unchanged");
        assertNull(plain.rateOf(), "and names no source");
    }
}
