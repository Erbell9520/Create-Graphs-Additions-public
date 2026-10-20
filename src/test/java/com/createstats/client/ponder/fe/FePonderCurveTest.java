package com.createstats.client.ponder.fe;

import com.createstats.power.WireTension;
import com.createstats.renderer.PowerConnectorRenderer;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FePonderCurveTest {
    private static double[][] renderer(Vec3 a, Vec3 b, int segs, double slack) throws Exception {
        final Method m = PowerConnectorRenderer.class.getDeclaredMethod("catenary", double.class, double.class,
                double.class, double.class, double.class, double.class, int.class, double.class);
        m.setAccessible(true);
        return (double[][]) m.invoke(null, a.x, a.y, a.z, b.x, b.y, b.z, segs, slack);
    }

    @Test
    void theKitsCurveIsTheRenderersCurveAtEveryTension() throws Exception {
        final Vec3[][] spans = {
                {new Vec3(0.5, 3.2, 0.5), new Vec3(6.5, 1.7, 2.5)},
                {new Vec3(6.4, 3.19, 3.6), new Vec3(2.5, 1.1, 5.9)},
                {new Vec3(0, 2, 0), new Vec3(24, 2, 0)},
                {new Vec3(0, 1, 0), new Vec3(3, 6, 1)},
                {new Vec3(0, 5, 0), new Vec3(1.2, 1, 0.4)},
                {new Vec3(2, 1, 2), new Vec3(2, 7, 2)},
                {new Vec3(0, 1, 0), new Vec3(0.8, 1.05, 0.3)},
        };
        for (WireTension tension : WireTension.values()) {
            for (Vec3[] span : spans) {
                final List<Vec3> kit = FePaths.catenary(span[0], span[1], tension.slack());
                final double[][] game = renderer(span[0], span[1], FePaths.segments(span[0].distanceTo(span[1])),
                        tension.slack());
                assertEquals(game.length, kit.size(), tension + " " + span[0] + " to " + span[1] + ": segments");
                for (int i = 0; i < game.length; i++) {
                    final Vec3 p = kit.get(i);
                    assertEquals(game[i][0], p.x, 1.0e-3, tension + " point " + i + " x");
                    assertEquals(game[i][1], p.y, 1.0e-3, tension + " point " + i + " y");
                    assertEquals(game[i][2], p.z, 1.0e-3, tension + " point " + i + " z");
                }
            }
        }
    }

    @Test
    void theSegmentCountIsTheRenderers() {
        assertEquals(6, FePaths.segments(0.4));
        assertEquals(6, FePaths.segments(3.0));
        assertEquals(10, FePaths.segments(5.0));
        assertEquals(48, FePaths.segments(40.0));
    }
}
