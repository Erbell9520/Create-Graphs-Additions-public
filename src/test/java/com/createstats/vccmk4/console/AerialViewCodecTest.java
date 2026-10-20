package com.createstats.vccmk4.console;

import com.createstats.vccmk4.air.core.AerialSetup;
import com.createstats.vccmk4.air.core.AirLight;
import com.createstats.vccmk4.air.core.Feel;
import com.createstats.vccmk4.air.core.LeaveMode;
import com.createstats.vccmk4.air.core.Telegraph;
import com.createstats.vccmk4.core.PartInput;
import com.createstats.vccmk4.network.AerialView;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AerialViewCodecTest {
    @Test
    void aViewReadsBackAsItWasWritten() {
        AerialView z = AerialConsoleLayoutTest.zeppelin();
        AerialView auto = new AerialView(z.name(), z.pilot(), z.youPilot(), z.onCraft(), z.standDown(), z.mode(), z.leave(),
                z.feel(), z.flight(), z.side(), z.balloons(), z.engines(), z.surfaces(), z.tools(), z.lights(), z.keymap(),
                z.held(), 1, true, z.bell(), z.blocks(), z.mass(), z.gravity(), z.learned(), z.notes());

        List<AerialView.Engine> ramped = new java.util.ArrayList<>(z.engines());
        ramped.add(new AerialView.Engine(9001L, 0, false, 100, 40, 0.4f, 267f, 1, -28f, 2f, 0f, AerialView.DIR_AHEAD,
                AerialView.ENGINE_OK, true, AerialView.KIND_THRUSTER, 0, 0.8f));
        AerialView going = new AerialView(z.name(), z.pilot(), z.youPilot(), z.onCraft(), z.standDown(), z.mode(), z.leave(),
                z.feel(), z.flight(), z.side(), z.balloons(), ramped, z.surfaces(), z.tools(), z.lights(), z.keymap(),
                z.held(), z.noseTurn(), z.noseAuto(), z.bell(), z.blocks(), z.mass(), z.gravity(), z.learned(), z.notes(), true,
                new AerialView.Nav(AerialView.NAV_LODESTONE, 448.5f, 63.5f, 540.5f, 312f, 45f, true, ""), AerialView.STAFF_DRAGGED);
        AerialView lost = new AerialView(z.name(), z.pilot(), z.youPilot(), z.onCraft(), z.standDown(), z.mode(), z.leave(),
                z.feel(), z.flight(), z.side(), z.balloons(), z.engines(), z.surfaces(), z.tools(), z.lights(), z.keymap(),
                z.held(), z.noseTurn(), z.noseAuto(), z.bell(), z.blocks(), z.mass(), z.gravity(), z.learned(), z.notes(), false,
                new AerialView.Nav(AerialView.NAV_NONE, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, false,
                        "The compass on the Navigation Table has lost its lodestone."), AerialView.STAFF_LOCKED);

        for (AerialView v : List.of(z, AerialConsoleLayoutTest.nothing(), auto, AerialConsoleLayoutTest.thrusters(),
                AerialConsoleLayoutTest.gunnery(), AerialConsoleLayoutTest.weighing(), AerialConsoleLayoutTest.gantry(), going,
                lost)) {
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            AerialView.write(buf, v);
            AerialView back = AerialView.read(buf);
            assertEquals(0, buf.readableBytes(), "every byte written was read");
            assertEquals(canonical(v), canonical(back));
        }
    }

    private static String canonical(AerialView v) {
        StringBuilder sb = new StringBuilder();
        sb.append(v.name()).append('|').append(v.pilot()).append('|').append(v.youPilot()).append('|').append(v.onCraft())
                .append('|').append(v.standDown()).append('|').append(v.mode()).append(v.leave()).append(v.feel()).append('|')
                .append(v.flight()).append('|');
        AerialView.Side s = v.side();
        sb.append(s.cell()).append(s.cols()).append(s.rows()).append(s.originLon()).append(s.originUp())
                .append(Arrays.toString(s.bits())).append(s.topRows()).append(s.originLat()).append(Arrays.toString(s.topBits()))
                .append(Arrays.toString(s.envBits())).append(Arrays.toString(s.topEnvBits()));
        sb.append(v.balloons()).append(v.engines()).append(v.surfaces()).append(v.tools()).append(v.lights());
        sb.append(Arrays.toString(v.keymap())).append(Arrays.toString(v.held())).append(v.noseTurn()).append(v.noseAuto()).append(v.bell())
                .append(v.blocks()).append(v.mass()).append(v.gravity()).append(v.learned()).append(v.notes()).append(v.bridge())
                .append(v.nav()).append(v.staff());
        return sb.toString();
    }
}
