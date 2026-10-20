package com.createstats.inventory.core;

import com.createstats.power.ReserveProjection;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class EnergyReadoutTest {
    private static Map<String, Float> m(Object... kv) {
        final Map<String, Float> out = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            out.put((String) kv[i], ((Number) kv[i + 1]).floatValue());
        }
        return out;
    }

    private static Map<String, Float> connector() {
        return m("energy_stored", 3_200, "energy_capacity", 16_000, "network_buffer", 3_200, "energy_input_rate", 900,
                "energy_output_rate", 880, "energy_net_rate", 20, "connector_count", 3);
    }

    @Test
    void aConnectorNeverDrawsItsConduitAsCharge() {
        final EnergyReadout.Figures noReserve = EnergyReadout.read(EnergyReadout.Shape.CONNECTOR, connector(),
                EnergyReadout.Reserve.NONE);
        assertFalse(noReserve.hasStored(), "a grid with no batteries has no charge to show");
        assertTrue(noReserve.hasFlows());
        final EnergyReadout.Figures banked = EnergyReadout.read(EnergyReadout.Shape.CONNECTOR, connector(),
                new EnergyReadout.Reserve(true, 1_240_000, 2_000_000, 400, 1_800));
        assertTrue(banked.hasStored());
        assertEquals(1_240_000L, banked.stored(), "the batteries, not the wire's 3,200");
        assertEquals(2_000_000L, banked.capacity());
        assertEquals(-1_400L, banked.netPerTick());
        assertEquals(EnergyReadout.Count.WIRES, banked.countKind());
        assertEquals(3L, banked.count());
    }

    @Test
    void aBoardShowsTheReserveBehindItNotItsWorkingBuffer() {
        final Map<String, Float> pdu = m("energy_stored", 512_000, "energy_capacity", 512_000, "energy_input_rate",
                2_480, "energy_output_rate", 2_400);
        assertFalse(EnergyReadout.read(EnergyReadout.Shape.BOARD, pdu, EnergyReadout.Reserve.NONE).hasStored());
        final EnergyReadout.Figures f = EnergyReadout.read(EnergyReadout.Shape.BOARD, pdu,
                new EnergyReadout.Reserve(true, 1_860_000, 2_400_000, 0, 0));
        assertEquals(1_860_000L, f.stored());
        assertEquals(2_480L, f.inPerTick());
        assertEquals(2_400L, f.outPerTick());
    }

    @Test
    void aMeterHoldsNothingAndSaysSo() {
        final EnergyReadout.Figures f = EnergyReadout.read(EnergyReadout.Shape.METER,
                m("meter_rate", 640, "energy_input_rate", 640, "energy_output_rate", 640), EnergyReadout.Reserve.NONE);
        assertFalse(f.hasStored());
        assertTrue(f.hasFlows());
        assertEquals(640L, f.inPerTick());
        assertEquals(0L, f.netPerTick());
    }

    @Test
    void aBankReadsItsChargeAndItsBlocks() {
        final EnergyReadout.Figures f = EnergyReadout.read(EnergyReadout.Shape.BANK,
                m("energy_stored", 1_240_000, "energy_capacity", 2_000_000, "energy_input_rate", 400,
                        "energy_output_rate", 1_800, "bank_blocks", 12), EnergyReadout.Reserve.NONE);
        assertTrue(f.hasStored());
        assertEquals(0.62, f.fill(), 1e-9);
        assertEquals(EnergyReadout.Count.BLOCKS, f.countKind());
        assertEquals(12L, f.count(), "bank_blocks, not the C&A key the shipped page read (DI-2)");
    }

    @Test
    void aFigureTheSourceDoesNotPublishIsNotDrawn() {
        final EnergyReadout.Figures f = EnergyReadout.read(EnergyReadout.Shape.BANK,
                m("energy_stored", 100, "energy_capacity", 1_000), EnergyReadout.Reserve.NONE);
        assertFalse(f.hasFlows(), "no rates published: no IN or OUT rows, never a zero");
        assertEquals(EnergyReadout.Count.NONE, f.countKind());
        final EnergyReadout.Figures accumulator = EnergyReadout.read(EnergyReadout.Shape.BANK,
                m("energy_stored", 100, "energy_capacity", 1_000, "accumulator_size", 8), EnergyReadout.Reserve.NONE);
        assertEquals(8L, accumulator.count(), "Create Crafts and Additions' key still counts");
    }

    @Test
    void aNetAloneRebuildsTheMissingHalf() {
        final EnergyReadout.Figures f = EnergyReadout.read(EnergyReadout.Shape.DEVICE,
                m("energy_stored", 50, "energy_capacity", 100, "energy_net_rate", -30), EnergyReadout.Reserve.NONE);
        assertEquals(0L, f.inPerTick());
        assertEquals(30L, f.outPerTick());
        final EnergyReadout.Figures g = EnergyReadout.read(EnergyReadout.Shape.DEVICE,
                m("energy_stored", 50, "energy_capacity", 100, "energy_net_rate", -30, "energy_output_rate", 130),
                EnergyReadout.Reserve.NONE);
        assertEquals(100L, g.inPerTick());
    }

    private static FlowClock.Projection neverOurs() {
        return fail("a managed reserve's clock divided the reserve by its drain (INV-25)");
    }

    private static Map<String, Float> savesController() {
        return m("energy_stored", 82_348_375, "energy_capacity", 82_430_000, "energy_input_rate", 8_418,
                "energy_output_rate", 10_226, "energy_net_rate", -1_808, "energy_endurance", 45_546);
    }

    private static ReserveProjection.Result savesForecast() {
        return new ReserveProjection.Result(new byte[]{100, 96, 91}, List.of(
                new ReserveProjection.Milestone(18_000, ReserveProjection.Kind.STANCE, -1, 1, 60),
                new ReserveProjection.Milestone(33_600, ReserveProjection.Kind.SHORT, 2, 0, 19),
                new ReserveProjection.Milestone(35_400, ReserveProjection.Kind.SETTLES, -1, 16, 16)), false);
    }

    @Test
    void aControllerQuotesItsForecastNeverItsPoolOverItsDrain() {
        final EnergyReadout.Figures f = EnergyReadout.read(EnergyReadout.Shape.CONTROLLER, savesController(),
                EnergyReadout.Reserve.NONE, EnergyReadout.Outlook.ofForecast(savesForecast()));
        assertTrue(f.hasStored());
        assertEquals(EnergyReadout.Clock.QUOTED, f.quote().clock());
        assertFalse(f.quote().full());
        assertEquals(33_600, f.quote().ticks(), "when output 3 first goes short, as the goggle says");
        final FlowClock.Projection p = EnergyReadout.clock(f.quote(), EnergyReadoutTest::neverOurs);
        assertEquals(FlowClock.Kind.RUNTIME, p.kind());
        assertEquals(1_680.0, p.seconds(), 1e-9);
        assertNotEquals(45_546 / 20.0, p.seconds(), "not the published energy_endurance, the old Empty in ~37:57");
        assertNotEquals(82_348_375 / 1_808 / 20.0, p.seconds(), "not the pool over its drain");
    }

    @Test
    void aControllerNeverGetsThePagesOwnClockWhateverItIsHanded() {
        final ReserveProjection.Result settles = new ReserveProjection.Result(new byte[]{50, 40}, List.of(
                new ReserveProjection.Milestone(9_000, ReserveProjection.Kind.SETTLES, -1, 16, 16)), false);
        final ReserveProjection.Result shortNow = new ReserveProjection.Result(new byte[]{12}, List.of(), true);
        final List<EnergyReadout.Outlook> outlooks = List.of(
                EnergyReadout.Outlook.UNMANAGED,
                EnergyReadout.Outlook.ofForecast(null),
                EnergyReadout.Outlook.ofForecast(ReserveProjection.Result.NONE),
                EnergyReadout.Outlook.ofForecast(settles),
                EnergyReadout.Outlook.ofForecast(shortNow),
                EnergyReadout.Outlook.managed(-1, -1));
        final List<Map<String, Float>> sources = List.of(savesController(),
                m("energy_stored", 1_000_000, "energy_capacity", 2_000_000, "energy_net_rate", 4_000,
                        "energy_endurance", 250),
                m("energy_stored", 10, "energy_capacity", 2_000_000, "energy_output_rate", 9_000,
                        "energy_endurance", 0));
        for (EnergyReadout.Shape shape : List.of(EnergyReadout.Shape.CONTROLLER, EnergyReadout.Shape.BOARD)) {
            for (Map<String, Float> source : sources) {
                for (EnergyReadout.Outlook o : outlooks) {
                    final EnergyReadout.Figures f = EnergyReadout.read(shape, source,
                            new EnergyReadout.Reserve(true, 1_000_000, 2_000_000, 0, 0), o);
                    assertTrue(f.hasStored());
                    assertNotEquals(EnergyReadout.Clock.OWN, f.quote().clock(), shape + " handed " + o);
                    assertEquals(EnergyReadout.Clock.NONE, f.quote().clock(),
                            shape + ": nothing quotable in " + o + " is no clock at all");
                    assertSame(FlowClock.Projection.NONE, EnergyReadout.clock(f.quote(), EnergyReadoutTest::neverOurs));
                }
            }
        }
    }

    @Test
    void aBoardQuotesItsControllersForecastNeverItsOwnBuffer() {
        final Map<String, Float> pdu = m("energy_stored", 512_000, "energy_capacity", 512_000, "energy_input_rate",
                2_480, "energy_output_rate", 2_400, "energy_endurance", 2_400);
        final EnergyReadout.Reserve behind = new EnergyReadout.Reserve(true, 1_860_000, 2_400_000, 0, 0);
        final EnergyReadout.Figures linked = EnergyReadout.read(EnergyReadout.Shape.BOARD, pdu, behind,
                EnergyReadout.Outlook.managed(7_200, -1));
        assertEquals(EnergyReadout.Clock.QUOTED, linked.quote().clock());
        assertEquals(7_200, linked.quote().ticks(), "what its controller foresees for its output");
        assertEquals(360.0, EnergyReadout.clock(linked.quote(), EnergyReadoutTest::neverOurs).seconds(), 1e-9);
        assertEquals(EnergyReadout.Clock.NONE, EnergyReadout.read(EnergyReadout.Shape.BOARD, pdu, behind,
                EnergyReadout.Outlook.managed(0, -1)).quote().clock(), "short now has no time left to quote");
        assertEquals(EnergyReadout.Clock.NONE, EnergyReadout.read(EnergyReadout.Shape.BOARD, pdu, behind,
                EnergyReadout.Outlook.managed(-1, -1)).quote().clock(), "unlinked, or nothing foreseen");
    }

    @Test
    void aBankUnderAControllerQuotesItAndAFreeBankKeepsThePagesOwnClock() {
        final Map<String, Float> bank = m("energy_stored", 1_240_000, "energy_capacity", 2_000_000,
                "energy_input_rate", 400, "energy_output_rate", 1_800, "bank_blocks", 12);
        final EnergyReadout.Figures free = EnergyReadout.read(EnergyReadout.Shape.BANK, bank, EnergyReadout.Reserve.NONE);
        assertSame(EnergyReadout.Quote.OWN, free.quote(), "nothing manages it: it does empty at its rate");
        final AtomicInteger asked = new AtomicInteger();
        final FlowClock.Projection mine = new FlowClock.Projection(FlowClock.Kind.RUNTIME, 44.0);
        assertSame(mine, EnergyReadout.clock(free.quote(), () -> {
            asked.incrementAndGet();
            return mine;
        }));
        assertEquals(1, asked.get());

        final EnergyReadout.Figures filling = EnergyReadout.read(EnergyReadout.Shape.BANK, bank,
                EnergyReadout.Reserve.NONE, EnergyReadout.Outlook.managed(-1, 3_000));
        assertEquals(new EnergyReadout.Quote(EnergyReadout.Clock.QUOTED, true, 3_000), filling.quote(),
                "the controller's full-in, as the bank's own face quotes it (KI-118)");
        assertEquals(FlowClock.Kind.FULL, EnergyReadout.clock(filling.quote(), EnergyReadoutTest::neverOurs).kind());
        assertEquals(EnergyReadout.Clock.NONE, EnergyReadout.read(EnergyReadout.Shape.BANK, bank,
                EnergyReadout.Reserve.NONE, EnergyReadout.Outlook.managed(-1, -1)).quote().clock(),
                "draining under a controller: it settles, so no EMPTY");
    }

    @Test
    void aConnectorOnAManagedGridHasNoClockAndNothingHeldHasNone() {
        final EnergyReadout.Reserve banked = new EnergyReadout.Reserve(true, 1_240_000, 2_000_000, 400, 1_800);
        assertSame(EnergyReadout.Quote.OWN, EnergyReadout.read(EnergyReadout.Shape.CONNECTOR, connector(), banked).quote());
        assertSame(EnergyReadout.Quote.NONE, EnergyReadout.read(EnergyReadout.Shape.CONNECTOR, connector(), banked,
                EnergyReadout.Outlook.managed(-1, -1)).quote(), "a board or a controller runs that grid's batteries");
        assertSame(EnergyReadout.Quote.NONE, EnergyReadout.read(EnergyReadout.Shape.CONNECTOR, connector(),
                EnergyReadout.Reserve.NONE).quote(), "nothing banked: nothing to time");
        assertSame(EnergyReadout.Quote.NONE, EnergyReadout.read(EnergyReadout.Shape.METER,
                m("meter_rate", 640), EnergyReadout.Reserve.NONE).quote());
        assertSame(EnergyReadout.Quote.OWN, EnergyReadout.read(EnergyReadout.Shape.DEVICE,
                m("energy_stored", 50, "energy_capacity", 100, "energy_net_rate", -30), EnergyReadout.Reserve.NONE).quote(),
                "a machine's own buffer runs out at its rate");
    }
}
