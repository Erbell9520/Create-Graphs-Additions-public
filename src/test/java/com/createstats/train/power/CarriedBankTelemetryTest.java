package com.createstats.train.power;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CarriedBankTelemetryTest {
    private static final int CAP = 1_000;

    private static List<CarriedBankTelemetry.Cell> column(int... energies) {
        final List<CarriedBankTelemetry.Cell> cells = new ArrayList<>();
        for (int y = 0; y < energies.length; y++) {
            cells.add(new CarriedBankTelemetry.Cell(new BlockPos(0, y, 0), energies[y], CAP));
        }
        return cells;
    }

    @Test
    @DisplayName("a touching column is one bank, controlled by its lowest block")
    void columnIsOneBank() {
        final CarriedBankTelemetry t = new CarriedBankTelemetry();
        final List<CarriedBankTelemetry.Bank> banks = t.step(column(500, 0, 0), 3, 8);
        assertEquals(1, banks.size());
        assertEquals(new BlockPos(0, 0, 0), banks.get(0).controller());
        assertEquals(3, banks.get(0).members().size());
        assertEquals(500, banks.get(0).stored());
        assertEquals(3 * CAP, banks.get(0).capacity());
    }

    @Test
    @DisplayName("two banks apart stay two banks")
    void apartIsTwo() {
        final CarriedBankTelemetry t = new CarriedBankTelemetry();
        final List<CarriedBankTelemetry.Cell> cells = List.of(
                new CarriedBankTelemetry.Cell(new BlockPos(0, 0, 0), 10, CAP),
                new CarriedBankTelemetry.Cell(new BlockPos(5, 0, 0), 20, CAP));
        assertEquals(2, t.step(cells, 3, 8).size());
    }

    @Test
    @DisplayName("a column taller than the height limit splits the way the ground bank splits it")
    void tallColumnSplits() {
        final CarriedBankTelemetry t = new CarriedBankTelemetry();
        final List<CarriedBankTelemetry.Bank> banks = t.step(column(1, 2, 3, 4, 5), 3, 3);
        assertEquals(2, banks.size());
        assertEquals(new BlockPos(0, 0, 0), banks.get(0).controller());
        assertEquals(new BlockPos(0, 3, 0), banks.get(1).controller());
        assertEquals(1 + 2 + 3, banks.get(0).stored());
        assertEquals(4 + 5, banks.get(1).stored());
    }

    @Test
    @DisplayName("first step reports no flow; a rise is inflow, filtered like the ground bank")
    void flowIsFiltered() {
        final CarriedBankTelemetry t = new CarriedBankTelemetry();
        assertEquals(0, t.step(column(0), 3, 8).get(0).inRate());

        final CarriedBankTelemetry.Bank b = t.step(column(400), 3, 8).get(0);
        assertEquals(100, b.inRate());
        assertEquals(0, b.outRate());

        assertEquals(75, t.step(column(400), 3, 8).get(0).inRate());
    }

    @Test
    @DisplayName("a fall is outflow, and a trickle survives the filter as at least 1")
    void outflowAndTrickle() {
        final CarriedBankTelemetry t = new CarriedBankTelemetry();
        t.step(column(900), 3, 8);
        final CarriedBankTelemetry.Bank b = t.step(column(899), 3, 8).get(0);
        assertEquals(0, b.inRate());
        assertEquals(1, b.outRate());
    }

    @Test
    @DisplayName("flow decays to zero and the telemetry then reports settled")
    void settles() {
        final CarriedBankTelemetry t = new CarriedBankTelemetry();
        t.step(column(0), 3, 8);
        t.step(column(400), 3, 8);
        assertFalse(t.isSettled());
        for (int i = 0; i < 60; i++) {
            t.step(column(400), 3, 8);
        }
        assertTrue(t.isSettled());
        assertEquals(0, t.step(column(400), 3, 8).get(0).inRate());
    }

    @Test
    @DisplayName("LEDs follow the whole bank, round to the nearest of eight, and hold inside the deadband")
    void ledDeadband() {
        final CarriedBankTelemetry t = new CarriedBankTelemetry();

        assertEquals(4, t.step(column(500), 3, 8).get(0).ledLevel());

        assertEquals(4, t.step(column(575), 3, 8).get(0).ledLevel());

        assertEquals(5, t.step(column(600), 3, 8).get(0).ledLevel());

        assertEquals(5, t.step(column(550), 3, 8).get(0).ledLevel());

        assertEquals(4, t.step(column(530), 3, 8).get(0).ledLevel());
    }

    @Test
    @DisplayName("an empty bank is dark, and a trace charge rounds dark exactly as the ground block state does")
    void emptyAndTraceAreDark() {
        assertEquals(0, new CarriedBankTelemetry().step(column(0), 3, 8).get(0).ledLevel());

        assertEquals(0, new CarriedBankTelemetry().step(column(1), 3, 8).get(0).ledLevel());

        assertEquals(1, new CarriedBankTelemetry().step(column(70), 3, 8).get(0).ledLevel());
    }

    @Test
    @DisplayName("the deadband holds a dark bank dark too, as the ground bank's does")
    void deadbandFromDark() {
        final CarriedBankTelemetry t = new CarriedBankTelemetry();
        assertEquals(0, t.step(column(0), 3, 8).get(0).ledLevel());

        assertEquals(0, t.step(column(75), 3, 8).get(0).ledLevel());

        assertEquals(1, t.step(column(90), 3, 8).get(0).ledLevel());
    }

    @Test
    @DisplayName("the first step holds the LED level the bank was assembled with, inside the deadband")
    void seedsFromAssemblyState() {
        final CarriedBankTelemetry t = new CarriedBankTelemetry();

        final List<CarriedBankTelemetry.Cell> cells = List.of(
                new CarriedBankTelemetry.Cell(new BlockPos(0, 0, 0), 575, CAP, 4));
        assertEquals(4, t.step(cells, 3, 8).get(0).ledLevel());
        final CarriedBankTelemetry u = new CarriedBankTelemetry();
        assertEquals(5, u.step(column(575), 3, 8).get(0).ledLevel(), "no seed, plain rounding");
    }
}
