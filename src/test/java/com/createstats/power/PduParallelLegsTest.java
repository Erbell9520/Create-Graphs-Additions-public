package com.createstats.power;

import com.createstats.blockentity.PowerConnectorBE;
import com.createstats.blockentity.PowerDistributionUnitBE;
import com.createstats.chunkloader.core.CrownLaw;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PduParallelLegsTest {
    private static final int LEGS = PduStuds.COUNT;
    private static final CrownLaw LAW = CrownLaw.DEFAULT;

    private static int hvLeg(int leg) {
        return PduStudWiring.ceiling(PowerDistributionUnitBE.studRate(leg), PowerConnectorBE.baseTransferRate(),
                WireTier.HV);
    }

    private static PduLegPlan.Leg[] hvLegs() {
        final PduLegPlan.Leg[] legs = new PduLegPlan.Leg[LEGS];
        for (int i = 0; i < LEGS; i++) {
            legs[i] = PduLegPlan.Leg.open(hvLeg(i));
        }
        return legs;
    }

    private static PduLegPlan.Device crown(long key, int home, int demand, int... also) {
        return PduLegPlan.Device.station(key, home, FePriority.CRITICAL.ordinal(), demand, FeLock.NONE, also);
    }

    private static void assertNoStationTrips(List<PduLegPlan.Device> devices, PduLegPlan.Leg[] legs,
                                             PduLegPlan.Screened s) {
        final PduLegPlan.Screened without = PduLegPlan.screen(
                devices.stream().filter(d -> !d.station()).toList(), legs);
        for (int leg = 0; leg < legs.length; leg++) {
            assertEquals(without.isAtCapacity(leg), s.isAtCapacity(leg), "leg " + leg + ": a station changed its trip");
        }
    }

    private static PduLegPlan.Device dev(long key, int leg, FePriority tier, int demand) {
        return new PduLegPlan.Device(key, leg, tier.ordinal(), demand, FeLock.NONE);
    }

    @Test
    @DisplayName("KI-181: studs 1 and 2 carry an HV Line's 65,536; the rest stay at 32,768")
    void studsOneAndTwoAreHv() {
        assertEquals(2, PowerDistributionUnitBE.HV_STUDS);
        assertEquals(65_536, hvLeg(0));
        assertEquals(65_536, hvLeg(1));
        for (int leg = 2; leg < LEGS; leg++) {
            assertEquals(32_768, hvLeg(leg), "stud " + (leg + 1));
        }

        assertEquals(32_768, PduStudWiring.ceiling(PowerDistributionUnitBE.studRate(0),
                PowerConnectorBE.baseTransferRate(), WireTier.SHIELDED));
    }

    @Test
    @DisplayName("KI-183: a full station on HV studs 1 and 2 is fed in full, half on each")
    void aFullStationOnTwoHvLegs() {
        final int full = LAW.totalDraw(25);
        assertEquals(100_707, full);
        final PduLegPlan.Screened s = PduLegPlan.screen(List.of(crown(7, 1, full, 0)), hvLegs());
        assertTrue(s.blocked().isEmpty());
        assertEquals(1, s.survivors().size());
        assertEquals(full, s.survivors().get(0).demand());
        final int[] share = s.shares().get(7L);
        assertEquals(50_354, share[1], "two equal studs carry it half and half, the odd FE on its listed leg");
        assertEquals(50_353, share[0]);
        assertEquals(full, share[0] + share[1]);
        assertFalse(s.isAtCapacity(0));
        assertFalse(s.isAtCapacity(1));
        assertFalse(s.stations().get(7L).wireShort());
        assertEquals(131_072, s.stations().get(7L).carried());
        assertEquals(full, s.totalDemand(), "costed once");
    }

    @Test
    @DisplayName("KI-183: the owner's 1.15.7 wiring, studs 3 and 2: they carry 98,304, so 24 chunks fit and 25 are short")
    void theOwnersStudsThreeAndTwo() {
        final PduLegPlan.Screened s24 = PduLegPlan.screen(List.of(crown(7, 1, LAW.totalDraw(24), 2)), hvLegs());
        assertEquals(98_304, s24.stations().get(7L).carried());
        assertFalse(s24.stations().get(7L).wireShort());
        final int[] share = s24.shares().get(7L);
        assertEquals(LAW.totalDraw(24), share[1] + share[2]);
        assertEquals(2, (double) share[1] / share[2], 0.001, "in proportion to what each has spare: 65,536 to 32,768");
        final PduLegPlan.Screened s25 = PduLegPlan.screen(List.of(crown(7, 1, LAW.totalDraw(25), 2)), hvLegs());
        assertTrue(s25.blocked().isEmpty(), "short, never refused: 1.15.8 refused it and every chunk dropped");
        assertTrue(s25.stations().get(7L).wireShort());
        assertEquals(98_304, s25.stations().get(7L).fed());
        assertEquals(98_304, s25.survivors().get(0).demand(), "costed to supply at what its studs carry");
        assertFalse(s25.isAtCapacity(1), "1.15.8 tripped stud 2 here");
        assertFalse(s25.isAtCapacity(2));
    }

    @Test
    @DisplayName("KI-183: on one leg, or on two Advanced studs, a full station is fed what they carry and trips nothing")
    void oneLegFeedsWhatItCarries() {
        final int full = LAW.totalDraw(25);
        final PduLegPlan.Screened one = PduLegPlan.screen(List.of(crown(7, 0, full)), hvLegs());
        assertNull(one.blocked().get(7L));
        assertEquals(65_536, one.stations().get(7L).fed());
        assertEquals(65_536, one.shares().get(7L)[0]);
        assertFalse(one.isAtCapacity(0), "1.15.8 tripped this leg");
        final PduLegPlan.Screened advanced = PduLegPlan.screen(List.of(crown(7, 2, full, 3)), hvLegs());
        assertEquals(65_536, advanced.stations().get(7L).fed(), "two Advanced legs carry 65,536");
        assertTrue(advanced.stations().get(7L).wireShort());
        assertFalse(advanced.isAtCapacity(2));
        assertFalse(advanced.isAtCapacity(3));
        final PduLegPlan.Screened twenty = PduLegPlan.screen(List.of(crown(7, 2, LAW.totalDraw(20), 3)), hvLegs());
        assertFalse(twenty.stations().get(7L).wireShort(), "twenty chunks (58,650) fit two of them");
    }

    @Test
    @DisplayName("KI-183: a leg's own devices keep their room; the station is fed what they leave")
    void aLegsOwnDevicesComeFirst() {
        final int full = LAW.totalDraw(25);
        final List<PduLegPlan.Device> devices = List.of(
                crown(7, 1, full, 0),
                dev(1, 0, FePriority.CRITICAL, 20_000),
                dev(2, 0, FePriority.DEFAULT, 11_000),
                dev(3, 1, FePriority.DEFAULT, 1));
        final PduLegPlan.Screened s = PduLegPlan.screen(devices, hvLegs());
        assertNull(s.blocked().get(1L));
        assertNull(s.blocked().get(2L), "1.15.8 pushed it off its leg for the station");
        assertNull(s.blocked().get(3L), "and this one too, on the station's home leg");

        assertEquals(100_071, s.stations().get(7L).carried());
        assertEquals(100_071, s.stations().get(7L).fed());
        assertTrue(s.stations().get(7L).wireShort());
        for (int leg = 0; leg < LEGS; leg++) {
            assertTrue(s.legAdmitted()[leg] <= hvLeg(leg), "leg " + leg + " over its rating");
            assertFalse(s.isAtCapacity(leg));
        }
    }

    @Test
    @DisplayName("KI-183: one leg open, the other carries it alone and does not trip; both open, it is off at the breaker")
    void anOpenLeg() {
        final PduLegPlan.Leg[] legs = hvLegs();
        legs[1] = new PduLegPlan.Leg(false, legs[1].ceiling());
        final PduLegPlan.Screened small = PduLegPlan.screen(List.of(crown(7, 1, 40_000, 0)), legs);
        assertTrue(small.blocked().isEmpty());
        assertEquals(40_000, small.shares().get(7L)[0]);
        assertEquals(0, small.shares().get(7L)[1]);
        final PduLegPlan.Screened full = PduLegPlan.screen(List.of(crown(7, 1, LAW.totalDraw(25), 0)), legs);
        assertNull(full.blocked().get(7L), "1.15.8 refused it here and tripped its other leg");
        assertEquals(65_536, full.stations().get(7L).fed());
        assertFalse(full.isAtCapacity(0));
        assertFalse(full.isAtCapacity(1));
        legs[0] = new PduLegPlan.Leg(false, legs[0].ceiling());
        final PduLegPlan.Screened none = PduLegPlan.screen(List.of(crown(7, 1, 1_000, 0)), legs);
        assertEquals(PduReason.BREAKER_OPEN, none.blocked().get(7L));
        assertEquals(0, none.stations().get(7L).live());
        assertFalse(none.isAtCapacity(0));
        assertFalse(none.isAtCapacity(1));
    }

    @Test
    @DisplayName("KI-183: the split is exact, proportional, and never past a leg's spare")
    void theSplit() {
        final int[] spare = new int[LEGS];
        spare[0] = 65_536;
        spare[2] = 32_768;
        final int[] share = PduLegPlan.split(90_621, new int[] {0, 2}, spare);
        assertEquals(90_621, share[0] + share[2]);
        assertEquals(60_414, share[0]);
        assertEquals(30_207, share[2]);
        final int[] over = PduLegPlan.split(200_000, new int[] {0, 2}, spare);
        assertEquals(65_536, over[0]);
        assertEquals(32_768, over[2]);
        assertEquals(0, PduLegPlan.split(5, new int[0], spare)[0]);
        final Random r = new Random(183);
        for (int round = 0; round < 5_000; round++) {
            final int[] sp = new int[LEGS];
            final int[] feeds = new int[1 + r.nextInt(3)];
            for (int i = 0; i < feeds.length; i++) {
                feeds[i] = (i * 3 + r.nextInt(2)) % LEGS;
                sp[feeds[i]] = r.nextInt(70_000);
            }
            final int[] uniq = java.util.Arrays.stream(feeds).distinct().toArray();
            final int demand = r.nextInt(200_000);
            final int[] got = PduLegPlan.split(demand, uniq, sp);
            long sum = 0;
            long carried = 0;
            for (int leg : uniq) {
                assertTrue(got[leg] >= 0 && got[leg] <= sp[leg], "a part past its leg's spare");
                sum += got[leg];
                carried += sp[leg];
            }
            assertEquals(Math.min(demand, carried), sum);
        }
    }

    @Test
    @DisplayName("KI-181: a board with no two-wire load screens exactly as it did")
    void noStationNoChange() {
        final Random r = new Random(181);
        for (int round = 0; round < 2_000; round++) {
            final List<PduLegPlan.Device> devices = new ArrayList<>();
            final int n = 1 + r.nextInt(30);
            for (int i = 0; i < n; i++) {
                devices.add(dev(i, r.nextInt(LEGS + 1) - (r.nextInt(20) == 0 ? 1 : 0),
                        FePriority.values()[r.nextInt(FePriority.values().length)], r.nextInt(30_000)));
            }
            final PduLegPlan.Leg[] legs = new PduLegPlan.Leg[LEGS];
            for (int i = 0; i < LEGS; i++) {
                legs[i] = new PduLegPlan.Leg(r.nextInt(8) != 0, r.nextInt(70_000));
            }
            final PduLegPlan.Screened s = PduLegPlan.screen(devices, legs);
            assertTrue(s.shares().isEmpty());

            for (int leg = 0; leg < LEGS; leg++) {
                final int here = leg;
                final List<PduLegPlan.Device> on = new ArrayList<>(devices.stream().filter(d -> d.leg() == here).toList());
                on.sort((a, b) -> a.tierOrdinal() != b.tierOrdinal() ? Integer.compare(a.tierOrdinal(), b.tierOrdinal())
                        : a.demand() != b.demand() ? Integer.compare(a.demand(), b.demand()) : Long.compare(a.key(), b.key()));
                int demand = 0;
                int admitted = 0;
                for (PduLegPlan.Device d : on) {
                    demand += d.demand();
                    if (legs[leg].energised() && admitted + d.demand() <= legs[leg].ceiling()) {
                        admitted += d.demand();
                        assertNull(s.blocked().get(d.key()));
                    } else {
                        assertEquals(legs[leg].energised() ? PduReason.OVER_CAPACITY : PduReason.BREAKER_OPEN,
                                s.blocked().get(d.key()));
                    }
                }
                assertEquals(demand, s.legDemand()[leg]);
                assertEquals(legs[leg].energised() ? admitted : 0, s.legAdmitted()[leg]);
            }
        }
    }

    @Test
    @DisplayName("KI-183: over random boards, every leg holds its rating, no station trips a leg, each is fed what fits")
    void randomBoards() {
        final Random r = new Random(1815);
        for (int round = 0; round < 3_000; round++) {
            final PduLegPlan.Leg[] legs = new PduLegPlan.Leg[LEGS];
            for (int i = 0; i < LEGS; i++) {
                legs[i] = new PduLegPlan.Leg(r.nextInt(6) != 0, r.nextInt(70_000));
            }
            final List<PduLegPlan.Device> devices = new ArrayList<>();
            int offered = 0;
            for (int i = 0; i < 1 + r.nextInt(20); i++) {
                final int demand = r.nextInt(40_000);
                offered += demand;
                devices.add(dev(i, r.nextInt(LEGS), FePriority.values()[r.nextInt(FePriority.values().length)], demand));
            }
            for (int c = 0; c < r.nextInt(3); c++) {
                final int demand = r.nextInt(130_000);
                offered += demand;
                final int home = r.nextInt(LEGS);
                int other = r.nextInt(LEGS);
                if (other == home) {
                    other = (other + 1) % LEGS;
                }
                devices.add(r.nextInt(4) == 0 ? crown(100 + c, home, demand) : crown(100 + c, home, demand, other));
            }
            final PduLegPlan.Screened s = PduLegPlan.screen(devices, legs);
            for (int leg = 0; leg < LEGS; leg++) {
                assertTrue(s.legAdmitted()[leg] <= Math.max(0, legs[leg].ceiling()), "leg over its rating");
                assertTrue(s.legAdmitted()[leg] <= s.legDemand()[leg]);
            }
            assertEquals(offered, s.totalDemand(), "every device's demand offered once");
            assertNoStationTrips(devices, legs, s);
            for (PduLegPlan.Device d : devices) {
                if (!d.station()) {
                    continue;
                }
                final PduLegPlan.StationFit fit = s.stations().get(d.key());
                assertEquals(Math.min(d.demand(), fit.carried()), fit.fed(), "fed what fits");
                final int[] share = s.shares().get(d.key());
                if (share == null) {
                    assertEquals(0, fit.fed());
                    continue;
                }
                int sum = 0;
                for (int leg = 0; leg < LEGS; leg++) {
                    assertTrue(share[leg] == 0 || leg == d.leg()
                            || (d.alsoLegs().length > 0 && leg == d.alsoLegs()[0]), "a share off its legs");
                    sum += share[leg];
                }
                assertEquals(fit.fed(), sum, "a station's shares add to what it is fed");
            }
        }
    }
}
