package com.createstats.security.relay;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RelayFaceTest {
    private static final int[][] FACINGS = {{0, -1}, {0, 1}, {1, 0}, {-1, 0}};

    private static double[] local(int nx, int nz, float u, float v) {
        final double s = u / 16.0 - 0.5;
        final int rx = nz;
        final int rz = -nx;

        final double x = 0.5 + s * rx + 0.5 * nx;
        final double z = 0.5 + s * rz + 0.5 * nz;
        return new double[]{x, v / 16.0, z};
    }

    @Test
    @DisplayName("A click where the button is drawn lands on the button, on every facing")
    void buttonRoundTrip() {
        for (int[] n : FACINGS) {
            final double[] p = local(n[0], n[1], RelayFace.BUTTON_U, RelayFace.BUTTON_V);
            final float u = RelayFace.u(n[0], n[1], p[0], p[2]);
            final float v = RelayFace.v(p[1]);
            assertEquals(RelayFace.BUTTON_U, u, 1e-4, "facing " + n[0] + "," + n[1]);
            assertEquals(RelayFace.BUTTON_V, v, 1e-4);
            assertTrue(RelayFace.onButton(u, v));
        }
    }

    @Test
    @DisplayName("The viewer's right is the faceplate normal turned counter-clockwise")
    void viewersRight() {
        assertTrue(RelayFace.u(0, -1, 0.1, 0.0) > RelayFace.u(0, -1, 0.9, 0.0));

        assertTrue(RelayFace.u(1, 0, 1.0, 0.1) > RelayFace.u(1, 0, 1.0, 0.9));

        assertTrue(RelayFace.u(0, 1, 0.9, 1.0) > RelayFace.u(0, 1, 0.1, 1.0));

        assertTrue(RelayFace.u(-1, 0, 0.0, 0.9) > RelayFace.u(-1, 0, 0.0, 0.1));
    }

    @Test
    @DisplayName("The lamps, the window and the flag are not the button")
    void otherPartsAreNotTheButton() {
        assertFalse(RelayFace.onButton(RelayFace.CONTACT_LAMP_U, RelayFace.LAMP_V));
        assertFalse(RelayFace.onButton(RelayFace.OUTPUT_LAMP_U, RelayFace.LAMP_V));
        assertFalse(RelayFace.onButton((RelayFace.WINDOW_U0 + RelayFace.WINDOW_U1) / 2,
                (RelayFace.WINDOW_V0 + RelayFace.WINDOW_V1) / 2));
        assertFalse(RelayFace.onButton(RelayFace.FLAG_U, RelayFace.FLAG_V + 1.5f));
        assertTrue(RelayFace.WINDOW_U1 <= RelayFace.BUTTON_U - RelayFace.BUTTON_HALF,
                "the window must end before the button begins, or one click means two parts");
    }
}
