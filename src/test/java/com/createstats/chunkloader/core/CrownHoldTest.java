package com.createstats.chunkloader.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrownHoldTest {
    private static final CrownLaw LAW = CrownLaw.DEFAULT;

    private static final class Station {
        final CrownHold hold = new CrownHold();
        final int picked;
        int held;
        long fed;
        long paid;
        int heldTicks;

        Station(int picked) {
            this.picked = picked;
        }

        long capacity() {
            return LAW.storeCapacity(picked);
        }

        long draw() {
            return LAW.totalDraw(held);
        }

        CrownHold.Verdict tick(long fe, boolean mayHold) {
            fed += hold.accept(fe, capacity(), draw());
            final long before = hold.stored();
            final int heldBefore = held;
            final CrownHold.Verdict v = hold.step(held, picked, LAW, mayHold);
            if ((v == CrownHold.Verdict.HOLD || v == CrownHold.Verdict.ACQUIRE) && heldBefore > 0) {
                heldTicks++;
                paid += before - hold.stored();
            }
            if (v == CrownHold.Verdict.ACQUIRE) {
                held++;
            } else if (v == CrownHold.Verdict.RELEASE_ALL) {
                held = 0;
            }
            return v;
        }

        CrownHold.Verdict tick(long fe) {
            return tick(fe, true);
        }

        void bringUp() {
            for (int i = 0; i < 10_000 && (held < picked || hold.stored() < capacity()); i++) {
                tick(LAW.totalDraw(picked) * 4L);
            }
            assertEquals(picked, held, "the station came up");
            assertEquals(capacity(), hold.stored(), "with a full store");
        }
    }

    @Test
    @DisplayName("the store fills only from intake above the draw: a feed exactly at the draw leaves it where it is")
    void theStoreFillsOnlyAboveTheDraw() {
        final Station s = new Station(6);
        s.bringUp();
        final long draw = LAW.totalDraw(6);

        while (s.hold.stored() > s.capacity() / 2) {
            s.tick(0);
        }
        final long before = s.hold.stored();
        for (int t = 0; t < 400; t++) {
            assertEquals(CrownHold.Verdict.HOLD, s.tick(draw));
            assertEquals(CrownHold.Phase.FED, s.hold.phase());
        }
        assertEquals(before, s.hold.stored(), "a feed at the draw neither fills nor drains it");
        for (int t = 0; t < 10; t++) {
            s.tick(draw + 100);
        }
        assertEquals(before + 1_000, s.hold.stored(), "only the hundred FE a tick above the draw went in");
    }

    @Test
    @DisplayName("a feed exactly at the draw on a spent store has no ride-through: one FE short drops it all")
    void aFeedAtTheDrawHasNoRideThrough() {
        final Station s = new Station(3);
        s.bringUp();
        final long draw = LAW.totalDraw(3);
        while (s.hold.stored() >= 2 * draw) {
            s.tick(0);
        }

        s.tick(draw - (s.hold.stored() - draw));
        assertEquals(CrownHold.Verdict.HOLD, s.tick(draw - s.hold.stored()));
        assertEquals(0L, s.hold.stored(), "spent");
        for (int t = 0; t < 50; t++) {
            assertEquals(CrownHold.Verdict.HOLD, s.tick(draw));
            assertEquals(CrownHold.Phase.FED, s.hold.phase());
        }
        assertEquals(0L, s.hold.stored(), "a feed at the draw puts nothing by");
        assertEquals(0L, s.hold.rideThroughTicks(draw), "no ride-through");
        assertEquals(CrownHold.Verdict.RELEASE_ALL, s.tick(draw - 1), "one FE short, and everything goes at once");
        assertEquals(0, s.held);
    }

    @Test
    @DisplayName("a full store on a feed at its draw reads fed every tick, the tick after a chunk is taken included")
    void aFullStoreOnAFeedAtItsDrawIsFed() {
        final Station s = new Station(1);

        while (s.held == 0) {
            s.tick(LAW.totalDraw(1) * 3L);
        }
        assertEquals(s.capacity(), s.hold.stored(), "it came up with exactly a full store");
        for (int t = 0; t < 100; t++) {
            s.tick(LAW.totalDraw(1));
            assertEquals(CrownHold.Phase.FED, s.hold.phase(),
                    "the one-tick slot above the capacity takes this tick's feed before the draw is paid, at " + t);
        }
        assertEquals(s.capacity(), s.hold.stored());
    }

    @Test
    @DisplayName("a full store rides through exactly its five seconds, then every chunk goes in the same tick")
    void aFullStoreRidesThroughFiveSeconds() {
        final Station s = new Station(10);
        s.bringUp();
        int rode = 0;
        CrownHold.Verdict v;
        while ((v = s.tick(0)) == CrownHold.Verdict.HOLD) {
            assertEquals(CrownHold.Phase.RIDING, s.hold.phase());
            rode++;
        }
        assertEquals(100, rode, "5 seconds, 100 ticks, of the full draw");
        assertEquals(CrownHold.Verdict.RELEASE_ALL, v);
        assertEquals(0, s.held, "all ten released together, the station's own chunk included");
        assertEquals(CrownHold.Phase.DARK, s.hold.phase());
    }

    @Test
    @DisplayName("never a free hold: a pulse every two seconds holds nothing it did not pay for")
    void aPulseEveryTwoSecondsHoldsNothingItDidNotPayFor() {
        final Station s = new Station(1);
        final long draw = LAW.totalDraw(1);
        for (int t = 0; t < 40_000; t++) {
            s.tick(t % 40 == 0 ? draw : 0);
        }
        assertTrue(s.heldTicks > 0, "it does come up, on the energy it banked");
        assertEquals((long) s.heldTicks * draw, s.paid, "every held tick was paid the full draw");
        assertTrue(s.paid <= s.fed, "and never more than was fed: " + s.paid + " of " + s.fed);
        assertTrue(s.heldTicks <= 40_000 / 40, "one tick's pay every 40 ticks buys one tick in 40: " + s.heldTicks);

        final Station t = new Station(1);
        t.bringUp();
        while (t.hold.stored() >= draw) {
            assertEquals(CrownHold.Verdict.HOLD, t.tick(0));
        }
        assertEquals(CrownHold.Verdict.RELEASE_ALL, t.tick(0), "no grace counter without stored FE");
    }

    @Test
    @DisplayName("raising the count raises the capacity, never the stored FE; lowering it deletes nothing")
    void raisingTheCountRaisesTheCapacityOnly() {
        final Station s = new Station(4);
        s.bringUp();
        final long stored = s.hold.stored();
        final long draw = LAW.totalDraw(4);
        assertTrue(s.hold.room(LAW.storeCapacity(9), draw) > draw, "nine picks: more room");
        assertEquals(stored, s.hold.stored(), "and not one FE more in it");
        assertEquals(0L, s.hold.room(LAW.storeCapacity(2), draw), "two picks: overfull, it takes nothing");
        assertEquals(0L, s.hold.accept(5_000, LAW.storeCapacity(2), draw));
        assertEquals(stored, s.hold.stored(), "and keeps every FE it had");
    }

    @Test
    @DisplayName("after a blackout it comes back one chunk at a time, each with a full ride-through for its count")
    void itComesBackOneChunkAtATime() {
        final Station s = new Station(8);
        final long feed = LAW.totalDraw(8) * 3L;
        int lastAcquire = Integer.MIN_VALUE / 2;
        int acquired = 0;
        for (int t = 0; t < 5_000 && s.held < 8; t++) {
            final int heldBefore = s.held;
            final CrownHold.Verdict v = s.tick(feed);
            if (v == CrownHold.Verdict.ACQUIRE) {
                acquired++;
                assertTrue(t - lastAcquire >= CrownHold.REACQUIRE_TICKS, "half a second apart at least");
                assertTrue(s.hold.stored() >= LAW.rideTicks() * LAW.totalDraw(heldBefore + 1),
                        "chunk " + (heldBefore + 1) + " was taken with five seconds of its count in hand");
                lastAcquire = t;
            }
        }
        assertEquals(8, s.held);
        assertEquals(8, acquired, "one at a time, in pick order");
        assertEquals(CrownHold.Phase.FED, s.hold.phase());
    }

    @Test
    @DisplayName("a station that may not hold lets everything go and takes nothing back, full store or not")
    void aBlockedStationHoldsNothing() {
        final Station s = new Station(5);
        s.bringUp();
        assertEquals(CrownHold.Verdict.RELEASE_ALL, s.tick(LAW.totalDraw(5), false));
        assertEquals(0, s.held);
        for (int t = 0; t < 500; t++) {
            assertEquals(CrownHold.Verdict.STAY_DARK, s.tick(LAW.totalDraw(5) * 2, false));
        }

        assertTrue(s.hold.stored() >= s.capacity() && s.hold.stored() <= s.capacity() + LAW.totalDraw(5),
                "its store is full, and keeps the FE that arrived for the tick it was released on");
        assertEquals(0L, s.hold.accept(1_000, s.capacity(), 0), "a full dark store takes nothing more");
        assertEquals(CrownHold.Verdict.ACQUIRE, s.tick(0, true), "and the moment it may, it comes back");
    }

    @Test
    @DisplayName("conservation over random feeds: paid equals accepted less what is still stored; never negative")
    void conservationOverRandomFeeds() {
        final Random random = new Random(20261008L);
        for (int run = 0; run < 40; run++) {
            final Station s = new Station(1 + random.nextInt(25));
            final long full = LAW.totalDraw(s.picked);
            long allPaid = 0;
            for (int t = 0; t < 3_000; t++) {
                final long before = s.hold.stored();
                final long offer = random.nextInt(4) == 0 ? 0 : (long) (random.nextDouble() * full * 2.2);
                final long took = s.hold.accept(offer, s.capacity(), s.draw());
                s.fed += took;
                final int heldBefore = s.held;
                final CrownHold.Verdict v = s.hold.step(s.held, s.picked, LAW, true);
                final long spent = before + took - s.hold.stored();
                if (heldBefore > 0 && v != CrownHold.Verdict.RELEASE_ALL) {
                    assertEquals(LAW.totalDraw(heldBefore), spent, "a held tick costs exactly its draw");
                } else {
                    assertEquals(0L, spent, "a dark or released tick spends nothing");
                }
                allPaid += spent;
                if (v == CrownHold.Verdict.ACQUIRE) {
                    s.held++;
                } else if (v == CrownHold.Verdict.RELEASE_ALL) {
                    s.held = 0;
                }
                assertTrue(s.hold.stored() >= 0 && s.hold.stored() <= s.capacity(),
                        "after a step the store is within its capacity");
            }
            assertEquals(s.fed - s.hold.stored(), allPaid, "FE in equals FE paid plus FE still stored");
        }
    }
}
