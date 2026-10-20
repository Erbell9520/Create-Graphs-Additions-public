package com.createstats.block;

import com.createstats.timeswitch.TimeSwitchDial;
import com.createstats.timeswitch.TimeSwitchProgram;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimeSwitchMountTest {
    private static final Path BLOCKSTATE =
            Path.of("src/main/resources/assets/create_stats/blockstates/time_switch.json");
    private static final double EPS = 1.0e-9;

    @Test
    void theBlockstateTurnsTheShellExactlyAsTheRendererTurnsTheParts() throws IOException {
        final JsonObject variants = JsonParser
                .parseString(Files.readString(BLOCKSTATE, StandardCharsets.UTF_8))
                .getAsJsonObject().getAsJsonObject("variants");
        int seen = 0;
        for (Direction facing : Direction.values()) {
            final JsonObject v = variants.getAsJsonObject("facing=" + facing.getSerializedName());
            assertTrue(v != null, "time_switch.json has no variant for facing=" + facing);
            final int x = v.has("x") ? v.get("x").getAsInt() : 0;
            final int y = v.has("y") ? v.get("y").getAsInt() : 0;
            for (double[] p : new double[][] {{1.5, 0.5, 0.5}, {0.5, 1.5, 0.5}, {0.5, 0.5, 1.5}, {0.2, 0.9, 0.35}}) {
                double[] q = {p[0] - 0.5, p[1] - 0.5, p[2] - 0.5};
                q = aboutX(q, -x);
                q = aboutY(q, -y);
                final double[] json = {q[0] + 0.5, q[1] + 0.5, q[2] + 0.5};
                assertArrayEquals(json, MountedPart.toBlockSpace(facing, p[0], p[1], p[2]), EPS,
                        "facing=" + facing + " (x=" + x + ", y=" + y + ") point " + java.util.Arrays.toString(p));
            }
            seen++;
        }
        assertEquals(6, seen);
    }

    @Test
    void toAuthoredIsTheInverseOfMountedPart() {
        for (Direction facing : Direction.values()) {
            for (double u = 0.0; u <= 1.0; u += 0.25) {
                for (double h = 0.0; h <= 1.0; h += 0.25) {
                    for (double v = 0.0; v <= 1.0; v += 0.25) {
                        final double[] b = MountedPart.toBlockSpace(facing, u, h, v);
                        assertArrayEquals(new double[] {u, h, v},
                                TimeSwitchMount.toAuthored(facing, b[0], b[1], b[2], true), EPS,
                                "point, facing=" + facing);
                        final double[] b2 = MountedPart.toBlockSpace(facing, u + 0.3, h - 0.2, v + 0.7);
                        assertArrayEquals(new double[] {0.3, -0.2, 0.7},
                                TimeSwitchMount.toAuthored(facing, b2[0] - b[0], b2[1] - b[1], b2[2] - b[2], false),
                                EPS, "direction, facing=" + facing);
                    }
                }
            }
        }
    }

    @Test
    void aSliderLookedAtIsTheSliderPickedOnEveryFace() {
        final double r = (TimeSwitchDial.SLOT_R0 + TimeSwitchDial.SLOT_R1) / 2.0;
        for (Direction facing : Direction.values()) {
            for (double hourF : new double[] {0.0, 6.5, 13.25, 19.0, 23.9}) {
                final double dial = TimeSwitchDial.dialDegrees(hourF);
                for (int pin = 0; pin < TimeSwitchProgram.HOURS; pin++) {
                    final double a = Math.toRadians(TimeSwitchDial.pinViewAngle(pin, dial));

                    final double u = TimeSwitchDial.CU - r * Math.sin(a);
                    final double v = TimeSwitchDial.CV + r * Math.cos(a);
                    final double[] target = MountedPart.toBlockSpace(facing, u / 16.0,
                            TimeSwitchDial.PIN_TOP_H / 16.0, v / 16.0);

                    final double[] eye = MountedPart.toBlockSpace(facing, (u + 3.0) / 16.0, 0.5 + 2.0 / 16.0,
                            (v - 2.0) / 16.0);
                    final double[] o = TimeSwitchMount.toAuthored(facing, eye[0], eye[1], eye[2], true);
                    final double[] d = TimeSwitchMount.toAuthored(facing, target[0] - eye[0], target[1] - eye[1],
                            target[2] - eye[2], false);
                    final TimeSwitchDial.Aim aim = TimeSwitchDial.aim(o[0] * 16.0, o[1] * 16.0, o[2] * 16.0,
                            d[0], d[1], d[2], dial, TimeSwitchProgram.Mode.AUTO);
                    assertEquals(TimeSwitchDial.Kind.PIN, aim.kind(),
                            "facing=" + facing + " at " + hourF + " looking at pin " + pin);
                    assertEquals(pin, aim.index(), "facing=" + facing + " at " + hourF);
                }
            }
        }
    }

    private static double[] aboutX(double[] v, int degrees) {
        final double c = Math.cos(Math.toRadians(degrees));
        final double s = Math.sin(Math.toRadians(degrees));
        return new double[] {v[0], v[1] * c - v[2] * s, v[1] * s + v[2] * c};
    }

    private static double[] aboutY(double[] v, int degrees) {
        final double c = Math.cos(Math.toRadians(degrees));
        final double s = Math.sin(Math.toRadians(degrees));
        return new double[] {v[0] * c + v[2] * s, v[1], -v[0] * s + v[2] * c};
    }
}
