package com.createstats.renderer;

import com.createstats.power.BatteryControllerPlan;
import com.createstats.power.PduMode;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class InstrumentFaceSignatureTest {
    private static final int[] PDU_TOTAL = {2, 1, 3, 0, 1};

    private static final int[] PDU_SERVED = {2, 1, 1, 0, 0};

    private static PowerDistributionUnitTextureManager.Face pduFace() {
        return new PowerDistributionUnitTextureManager.Face(PduMode.AUTO, 40_000, 0.5f, 0.2f,
                900, 12, 1_400, 1_100, PDU_TOTAL, PDU_SERVED, 4, 1);
    }

    private static int pdu(PowerDistributionUnitTextureManager.Face face, long blink) {
        return PowerDistributionUnitTextureManager.signatureOf(face, blink);
    }

    @Test
    void pduSignatureIsStableForUnchangedValues() {
        assertEquals(pdu(pduFace(), 0L), pdu(pduFace(), 0L));
    }

    @Test
    void pduSignatureMovesWithEveryValueOnTheFace() {
        final int base = pdu(pduFace(), 0L);
        final var f = pduFace();
        assertNotEquals(base, pdu(new PowerDistributionUnitTextureManager.Face(PduMode.MANUAL,
                f.storedEnergy(), f.fill(), f.reserveFloor(), f.generation(), f.upkeep(),
                f.demand(), f.servedDemand(), f.tierTotal(), f.tierServed(),
                f.branchServed(), f.branchShed()), 0L), "mode");
        assertNotEquals(base, pdu(new PowerDistributionUnitTextureManager.Face(f.mode(), 0,
                f.fill(), f.reserveFloor(), f.generation(), f.upkeep(), f.demand(),
                f.servedDemand(), f.tierTotal(), f.tierServed(),
                f.branchServed(), f.branchShed()), 0L), "no input");
        assertNotEquals(base, pdu(new PowerDistributionUnitTextureManager.Face(f.mode(),
                f.storedEnergy(), 0.6f, f.reserveFloor(), f.generation(), f.upkeep(), f.demand(),
                f.servedDemand(), f.tierTotal(), f.tierServed(),
                f.branchServed(), f.branchShed()), 0L), "buffer fill");
        assertNotEquals(base, pdu(new PowerDistributionUnitTextureManager.Face(f.mode(),
                f.storedEnergy(), f.fill(), 0.35f, f.generation(), f.upkeep(), f.demand(),
                f.servedDemand(), f.tierTotal(), f.tierServed(),
                f.branchServed(), f.branchShed()), 0L), "reserve floor tick");
        assertNotEquals(base, pdu(new PowerDistributionUnitTextureManager.Face(f.mode(),
                f.storedEnergy(), f.fill(), f.reserveFloor(), 950, f.upkeep(), f.demand(),
                f.servedDemand(), f.tierTotal(), f.tierServed(),
                f.branchServed(), f.branchShed()), 0L), "generation cue");
        assertNotEquals(base, pdu(new PowerDistributionUnitTextureManager.Face(f.mode(),
                f.storedEnergy(), f.fill(), f.reserveFloor(), f.generation(), 20, f.demand(),
                f.servedDemand(), f.tierTotal(), f.tierServed(),
                f.branchServed(), f.branchShed()), 0L), "upkeep");
        assertNotEquals(base, pdu(new PowerDistributionUnitTextureManager.Face(f.mode(),
                f.storedEnergy(), f.fill(), f.reserveFloor(), f.generation(), f.upkeep(), 1_500,
                f.servedDemand(), f.tierTotal(), f.tierServed(),
                f.branchServed(), f.branchShed()), 0L), "branch demand");
        assertNotEquals(base, pdu(new PowerDistributionUnitTextureManager.Face(f.mode(),
                f.storedEnergy(), f.fill(), f.reserveFloor(), f.generation(), f.upkeep(),
                f.demand(), 1_200, f.tierTotal(), f.tierServed(),
                f.branchServed(), f.branchShed()), 0L), "served demand");
        assertNotEquals(base, pdu(new PowerDistributionUnitTextureManager.Face(f.mode(),
                f.storedEnergy(), f.fill(), f.reserveFloor(), f.generation(), f.upkeep(),
                f.demand(), f.servedDemand(), new int[]{2, 1, 4, 0, 1}, f.tierServed(),
                f.branchServed(), f.branchShed()), 0L), "a tier gained a device");
        assertNotEquals(base, pdu(new PowerDistributionUnitTextureManager.Face(f.mode(),
                f.storedEnergy(), f.fill(), f.reserveFloor(), f.generation(), f.upkeep(),
                f.demand(), f.servedDemand(), f.tierTotal(), new int[]{2, 1, 3, 0, 1},
                f.branchServed(), f.branchShed()), 0L), "a tier lamp came back on");
        assertNotEquals(base, pdu(new PowerDistributionUnitTextureManager.Face(f.mode(),
                f.storedEnergy(), f.fill(), f.reserveFloor(), f.generation(), f.upkeep(),
                f.demand(), f.servedDemand(), f.tierTotal(), f.tierServed(),
                5, f.branchShed()), 0L), "roll-call served count");
        assertNotEquals(base, pdu(new PowerDistributionUnitTextureManager.Face(f.mode(),
                f.storedEnergy(), f.fill(), f.reserveFloor(), f.generation(), f.upkeep(),
                f.demand(), f.servedDemand(), f.tierTotal(), f.tierServed(),
                f.branchServed(), 2), 0L), "roll-call shed count");
    }

    @Test
    void pduBlinkCountsOnlyWhileALampIsBlinking() {
        final int[] allServed = {2, 1, 3, 0, 1};
        final var still = new PowerDistributionUnitTextureManager.Face(PduMode.AUTO, 40_000,
                0.5f, 0.2f, 900, 12, 1_400, 1_100, allServed, allServed, 4, 0);
        assertEquals(pdu(still, 0L), pdu(still, 1L));

        assertNotEquals(pdu(pduFace(), 0L), pdu(pduFace(), 1L));
    }

    private static BatteryControllerPlan.Reason[] lamps(BatteryControllerPlan.Reason... r) {
        return r;
    }

    private static BatteryControllerTextureManager.Face bcFace(
            BatteryControllerPlan.Reason... lamps) {
        return new BatteryControllerTextureManager.Face(4_000_000, 62, 850, 3_200, 1_400, 980,
                lamps.length == 0
                        ? lamps(BatteryControllerPlan.Reason.SERVED,
                                BatteryControllerPlan.Reason.PARTIAL,
                                BatteryControllerPlan.Reason.UNWIRED)
                        : lamps);
    }

    private static int bc(BatteryControllerTextureManager.Face face, long blink) {
        return BatteryControllerTextureManager.signatureOf(face, blink);
    }

    private static BatteryControllerTextureManager.Face bcWith(int cap, int pct, int rate,
                                                               int endurance, int gen,
                                                               int delivered) {
        return new BatteryControllerTextureManager.Face(cap, pct, rate, endurance, gen, delivered,
                bcFace().lamps());
    }

    @Test
    void batteryControllerSignatureIsStableForUnchangedValues() {
        assertEquals(bc(bcFace(), 0L), bc(bcFace(), 0L));
    }

    @Test
    void batteryControllerSignatureMovesWithEveryValueOnTheFace() {
        final int base = bc(bcFace(), 0L);
        assertNotEquals(base, bc(bcWith(0, 62, 850, 3_200, 1_400, 980), 0L), "no storage wired");
        assertNotEquals(base, bc(bcWith(4_000_000, 63, 850, 3_200, 1_400, 980), 0L), "reserve percent");
        assertNotEquals(base, bc(bcWith(4_000_000, 62, -850, 3_200, 1_400, 980), 0L), "charge to discharge");
        assertNotEquals(base, bc(bcWith(4_000_000, 62, 850, 3_260, 1_400, 980), 0L), "endurance clock");
        assertNotEquals(base, bc(bcWith(4_000_000, 62, 850, 3_200, 1_500, 980), 0L), "throughput in");
        assertNotEquals(base, bc(bcWith(4_000_000, 62, 850, 3_200, 1_400, 1_100), 0L), "throughput out");
    }

    @Test
    void batteryControllerSignatureMovesWithEachLampIndependently() {
        final BatteryControllerPlan.Reason[] all = lamps(
                BatteryControllerPlan.Reason.SERVED, BatteryControllerPlan.Reason.SERVED,
                BatteryControllerPlan.Reason.SERVED, BatteryControllerPlan.Reason.SERVED,
                BatteryControllerPlan.Reason.SERVED, BatteryControllerPlan.Reason.SERVED);
        final int base = bc(bcFace(all), 0L);
        for (int i = 0; i < all.length; i++) {
            final BatteryControllerPlan.Reason[] changed = all.clone();
            changed[i] = BatteryControllerPlan.Reason.OFF;
            assertNotEquals(base, bc(bcFace(changed), 0L), "output lamp " + (i + 1));
        }
    }

    @Test
    void batteryControllerBlinkCountsOnlyWhileALampIsBlinking() {
        assertEquals(bc(bcFace(), 0L), bc(bcFace(), 1L));

        final BatteryControllerTextureManager.Face held = bcFace(
                BatteryControllerPlan.Reason.SERVED,
                BatteryControllerPlan.Reason.FLOOR,
                BatteryControllerPlan.Reason.UNWIRED);
        assertNotEquals(bc(held, 0L), bc(held, 1L));
    }

    private static int bank(float fill, int stored, int capacity, int rate, int in, int out,
                            int endurance) {
        return CapacitorBankTextureManager.signatureOf(new CapacitorBankTextureManager.Face(
                fill, stored, capacity, rate, in, out, endurance));
    }

    private static int bankBaseline() {
        return bank(0.5f, 5_000_000, 10_000_000, 1_200, 1_200, 700, 6_400);
    }

    @Test
    void capacitorBankSignatureIsStableForUnchangedValues() {
        assertEquals(bankBaseline(), bankBaseline());
    }

    @Test
    void capacitorBankSignatureMovesWithEveryValueOnTheFace() {
        final int base = bankBaseline();
        assertNotEquals(base, bank(0.6f, 5_000_000, 10_000_000, 1_200, 1_200, 700, 6_400), "fill");
        assertNotEquals(base, bank(0.5f, 5_100_000, 10_000_000, 1_200, 1_200, 700, 6_400), "stored");
        assertNotEquals(base, bank(0.5f, 5_000_000, 12_000_000, 1_200, 1_200, 700, 6_400), "capacity");
        assertNotEquals(base, bank(0.5f, 5_000_000, 10_000_000, 1_900, 1_200, 700, 6_400), "rate magnitude");
        assertNotEquals(base, bank(0.5f, 5_000_000, 10_000_000, 1_200, 1_900, 700, 6_400), "flow in magnitude");
        assertNotEquals(base, bank(0.5f, 5_000_000, 10_000_000, 1_200, 1_200, 1_900, 6_400), "flow out magnitude");
        assertNotEquals(base, bank(0.5f, 5_000_000, 10_000_000, 1_200, 1_200, 700, 6_460), "endurance clock");
        assertNotEquals(base, bank(0.5f, 5_000_000, 10_000_000, 1_200, 1_200, 700, -1), "no estimate");
    }

    @Test
    void capacitorBankSignatureMovesWithEveryFlowDirection() {
        final int base = bankBaseline();
        assertNotEquals(base, bank(0.5f, 5_000_000, 10_000_000, -1_200, 1_200, 700, 6_400), "charge to draw");
        assertNotEquals(base, bank(0.5f, 5_000_000, 10_000_000, 0, 1_200, 700, 6_400), "charge to idle");
        assertNotEquals(base, bank(0.5f, 5_000_000, 10_000_000, 1_200, 0, 700, 6_400), "intake stopped");
        assertNotEquals(base, bank(0.5f, 5_000_000, 10_000_000, 1_200, 1_200, 0, 6_400), "outflow stopped");
    }

    @Test
    void capacitorBankCompactKeyTracksWhatCompactPrints() {
        final Map<Integer, String> seen = new HashMap<>();

        for (int magnitude = 0; magnitude < 10; magnitude++) {
            final long from = magnitude == 0 ? 0L : (long) Math.pow(10, magnitude);
            final long to = Math.min(Integer.MAX_VALUE, (long) Math.pow(10, magnitude + 1));
            final long step = Math.max(1L, (to - from) / 4_000L);
            for (long v = from; v < to; v += step) {
                final int value = (int) v;
                final String printed = CapacitorBankTextureManager.compact(value);
                final String previous = seen.putIfAbsent(
                        CapacitorBankTextureManager.compactKey(value), printed);
                if (previous != null) {
                    assertEquals(previous, printed, "compactKey(" + value + ") collides with a value"
                            + " that prints differently, so this figure would freeze on the glass."
                            + " Keep compactKey's bands disjoint and in step with compact's.");
                }
            }
        }
    }
}
