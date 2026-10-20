package com.createstats.vccmk4.core;

import com.createstats.vehiclelight.VehicleLightKind;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LampProposalTest {
    @Test
    void whiteForwardIsAHeadlightAndWhiteBackIsAReverseLight() {
        assertEquals(LinkSource.HEADLIGHT, LampProposal.propose(VehicleLightKind.WHITE, 1, 0, -1.5),
                "a headlight: dipped while driving, full on the Full beam key (Driving would be full beam always)");
        assertEquals(LinkSource.HEADLIGHT, LampProposal.propose(VehicleLightKind.WHITE, 0.7, 0.7, 0), "a corner lamp angled out");
        assertEquals(LinkSource.REVERSE, LampProposal.propose(VehicleLightKind.WHITE, -1, 0, 0));
        assertEquals(LinkSource.OFF, LampProposal.propose(VehicleLightKind.WHITE, 0, 1, 1), "a side work light is not guessed");
        assertEquals(LinkSource.OFF, LampProposal.propose(VehicleLightKind.WHITE, 0, 0, 0), "nor a roof light pointing up");
    }

    @Test
    void redIsTailAndBrakeWherever() {
        for (double f : new double[] {-1, 0, 1}) {
            assertEquals(LinkSource.TAIL_BRAKE, LampProposal.propose(VehicleLightKind.RED, f, 0, 2));
        }
    }

    @Test
    void amberIsTheIndicatorForItsSide() {
        assertEquals(LinkSource.LEFT, LampProposal.propose(VehicleLightKind.AMBER, 1, 0, -1.0));
        assertEquals(LinkSource.RIGHT, LampProposal.propose(VehicleLightKind.AMBER, -1, 0, 1.0));
        assertEquals(LinkSource.LEFT, LampProposal.propose(VehicleLightKind.AMBER, 0, -1, 0.1),
                "on the line, a repeater pointing left is the left one");
        assertEquals(LinkSource.RIGHT, LampProposal.propose(VehicleLightKind.AMBER, 0, 1, 0.0));
        assertEquals(LinkSource.OFF, LampProposal.propose(VehicleLightKind.AMBER, 1, 0, 0.0),
                "on the line and pointing ahead it could be either");
        assertEquals(LinkSource.LEFT, LampProposal.propose(VehicleLightKind.AMBER, 0, 1, -2.0),
                "where it sits beats which way it points");
    }

    @Test
    void theCentreLineIsHalfwayBetweenTheOutermostWheelsNotTheConsole() {
        assertEquals(-1.0, LampProposal.centreLine(new double[] {-2, 0, -2, 0}, new double[] {5}), 1e-12);

        assertEquals(0.0, LampProposal.centreLine(new double[] {-1, -1, -1, 1}, null), 1e-12);
        assertEquals(0.5, LampProposal.centreLine(new double[0], new double[] {-1, 2}), 1e-12, "no wheels: the lamps");
        assertEquals(0.0, LampProposal.centreLine(null, null), 1e-12, "nothing at all: the console");
    }
}
