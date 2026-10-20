package com.createstats.vccmk4.air.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PressureModelTest {
    @Test
    void sableDefaultCurve() {
        PressureModel p = PressureModel.sable(63.0);
        assertEquals(1.0, p.at(63.0), 1.0e-12);

        assertEquals(0.86, p.at(100.0), 0.005);
        assertEquals(0.71, p.at(150.0), 0.005);
        assertEquals(0.58, p.at(200.0), 0.005);
        assertEquals(0.46, p.at(256.0), 0.005);
    }

    @Test
    void cappedBelowSeaLevel() {
        assertEquals(1.5, PressureModel.sable(63.0).at(-200.0), 1.0e-12);
    }

    @Test
    void twoReadingsGiveTheLapse() {
        PressureModel truth = new PressureModel(63.0, 1.0, 0.006);
        PressureModel fit = PressureModel.measured(120.0, truth.at(120.0), truth.at(136.0), 16.0, 63.0);
        assertEquals(0.006, fit.lapse(), 1.0e-9);
        assertEquals(truth.at(180.0), fit.at(180.0), 1.0e-9);
    }

    @Test
    void aFlatOrMissingReadingFallsBackToTheDefaultLapse() {
        assertEquals(PressureModel.SABLE_LAPSE, PressureModel.measured(100.0, 0.9, 0.9, 16.0, 63.0).lapse(), 1.0e-12);
        assertEquals(PressureModel.SABLE_LAPSE, PressureModel.measured(100.0, 0.9, Double.NaN, 16.0, 63.0).lapse(), 1.0e-12);
        PressureModel none = PressureModel.measured(100.0, Double.NaN, Double.NaN, 16.0, 70.0);
        assertEquals(70.0, none.y0(), 1.0e-12);
        assertEquals(1.0, none.p0(), 1.0e-12);
    }

    @Test
    void altitudeOfInvertsAt() {
        PressureModel p = PressureModel.sable(63.0);
        for (double y = 63.0; y < 300.0; y += 17.0) assertEquals(y, p.altitudeOf(p.at(y)), 1.0e-9);
    }

    @Test
    void airDenserThanTheCapIsNeverReached() {
        assertTrue(Double.isNaN(PressureModel.sable(63.0).altitudeOf(1.6)));
        assertTrue(Double.isNaN(PressureModel.sable(63.0).altitudeOf(0.0)));
    }
}
