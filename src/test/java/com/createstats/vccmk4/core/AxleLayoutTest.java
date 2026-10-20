package com.createstats.vccmk4.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AxleLayoutTest {
    @Test
    void fourWheelsAreTwoAxlesFrontFirstLeftFirst() {
        List<AxleLayout.WheelSpec> w = List.of(
                new AxleLayout.WheelSpec(1.0, -1.5),
                new AxleLayout.WheelSpec(-1.0, 1.5),
                new AxleLayout.WheelSpec(1.0, 1.5),
                new AxleLayout.WheelSpec(-1.0, -1.5));
        List<AxleLayout.Axle> axles = AxleLayout.cluster(w);
        assertEquals(2, axles.size());
        assertEquals(0, axles.get(0).index());
        assertEquals(1.5, axles.get(0).longitudinal(), 1.0e-9);
        assertArrayEquals(new int[]{1, 2}, axles.get(0).wheels(), "front axle, left then right");
        assertArrayEquals(new int[]{3, 0}, axles.get(1).wheels());
        assertArrayEquals(new int[]{1, 0, 0, 1}, AxleLayout.axleOf(4, axles));
    }

    @Test
    void wheelsWithinToleranceShareAnAxle() {
        List<AxleLayout.WheelSpec> w = List.of(
                new AxleLayout.WheelSpec(-1.0, 2.0),
                new AxleLayout.WheelSpec(1.0, 1.6),
                new AxleLayout.WheelSpec(-1.0, -2.0),
                new AxleLayout.WheelSpec(1.0, -2.0));
        List<AxleLayout.Axle> axles = AxleLayout.cluster(w);
        assertEquals(2, axles.size());
        assertEquals(1.8, axles.get(0).longitudinal(), 1.0e-9, "an axle sits at the mean of its wheels");
    }

    @Test
    void aBlockApartIsTwoAxles() {
        List<AxleLayout.WheelSpec> w = List.of(
                new AxleLayout.WheelSpec(-1.0, 1.0),
                new AxleLayout.WheelSpec(-1.0, 0.0),
                new AxleLayout.WheelSpec(-1.0, -1.0));
        assertEquals(3, AxleLayout.cluster(w).size());
    }

    @Test
    void sixteenWheelsAreEightAxles() {
        java.util.ArrayList<AxleLayout.WheelSpec> w = new java.util.ArrayList<>();
        for (int i = 0; i < 8; i++) {
            w.add(new AxleLayout.WheelSpec(-1.5, 7 - 2 * i));
            w.add(new AxleLayout.WheelSpec(1.5, 7 - 2 * i));
        }
        List<AxleLayout.Axle> axles = AxleLayout.cluster(w);
        assertEquals(8, axles.size());
        for (int i = 1; i < 8; i++) {
            assertTrue(axles.get(i).longitudinal() < axles.get(i - 1).longitudinal(), "front to back");
        }
    }

    @Test
    void emptyAndSingle() {
        assertTrue(AxleLayout.cluster(List.of()).isEmpty());
        List<AxleLayout.Axle> one = AxleLayout.cluster(List.of(new AxleLayout.WheelSpec(0.0, 0.0)));
        assertEquals(1, one.size());
        assertArrayEquals(new int[]{0}, one.get(0).wheels());
    }
}
