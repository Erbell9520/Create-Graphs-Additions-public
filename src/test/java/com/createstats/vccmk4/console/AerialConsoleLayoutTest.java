package com.createstats.vccmk4.console;

import com.createstats.vccmk4.air.core.AerialSetup;
import com.createstats.vccmk4.air.core.AirLight;
import com.createstats.vccmk4.air.core.Telegraph;
import com.createstats.vccmk4.core.Channel;
import com.createstats.vccmk4.core.Control;
import com.createstats.vccmk4.core.KeyIds;
import com.createstats.vccmk4.core.Keymap;
import com.createstats.vccmk4.core.PartInput;
import com.createstats.vccmk4.network.AerialActionPacket;
import com.createstats.vccmk4.network.AerialView;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AerialConsoleLayoutTest {
    private static final Path OUT = Path.of("build/aerial-console");
    private static final int[][] SCREENS = {{464, 280}, {544, 334}, {853, 533}};

    @Test
    void theDialPutsEveryOrderInItsOwnSector() {
        int n = Telegraph.Notch.values().length;
        assertEquals(90.0, AerialConsole.dialAngle(0.0), 1.0e-9, "Stop straight up");
        assertEquals(180.0 - 6.5 * 180.0 / n, AerialConsole.dialAngle(1.0), 1.0e-9, "Full ahead at the right");
        assertEquals(180.0 - 0.5 * 180.0 / n, AerialConsole.dialAngle(-1.0), 1.0e-9, "Full astern at the left");
        double last = Double.POSITIVE_INFINITY;
        for (double p = -1.0; p <= 1.0; p += 0.01) {
            double a = AerialConsole.dialAngle(p);
            assertTrue(a < last, "the needle sweeps one way: " + p);
            last = a;
        }
        for (Telegraph.Notch notch : Telegraph.Notch.values()) {
            double a = Math.toRadians(AerialConsole.dialAngle(notch.value));
            double px = 100 + 30 * Math.cos(a);
            double py = 100 - 30 * Math.sin(a);
            assertEquals(notch.ordinal(), AerialConsole.dialSector(100, 100, 40, px, py), "the needle points into its own sector: " + notch);
        }
        assertEquals(-1, AerialConsole.dialSector(100, 100, 40, 100, 150), "below the dial is not the dial");
    }

    private static AerialView withOrder(float order) {
        return new AerialView("Graf Spee", "Dev", true, true, "", 1, 0, 1, flight(order, order, false, Float.NaN), side(), twoBalloons(),
                threeEngines(), fins(), tools(), lights(), keymap(), new int[0], 0, true, 2140, 1840f, 11f, learned(), List.of());
    }

    private interface Setup {
        void accept(AerialConsole console, int[] screen);
    }

    private record Scenario(String name, AerialView view, Setup setup, int[] mouse) {
        int mouseX(int[] screen) {
            return mouse == null ? -1 : screen[0] * mouse[0] / 100;
        }

        int mouseY(int[] screen) {
            return mouse == null ? -1 : screen[1] * mouse[1] / 100;
        }
    }

    private static List<Scenario> scenarios() {
        List<Scenario> out = new ArrayList<>();
        out.add(new Scenario("zeppelin", zeppelin(), (c, s) -> { }, null));
        out.add(new Scenario("zeppelin_dial", zeppelin(), (c, s) -> { }, new int[] {24, 30}));
        out.add(new Scenario("zeppelin_tape", zeppelin(), (c, s) -> { }, new int[] {44, 40}));
        out.add(new Scenario("between", withOrder(0.62f), (c, s) -> { }, null));
        out.add(new Scenario("goto", goingTo(), (c, s) -> { }, null));
        out.add(new Scenario("armed", zeppelin(), (c, s) -> c.arm("reset_keys"), null));
        out.add(new Scenario("anchored", anchored(), (c, s) -> { }, null));
        out.add(new Scenario("astern", astern(), (c, s) -> { }, null));
        out.add(new Scenario("trouble", trouble(), (c, s) -> { }, new int[] {50, 88}));
        out.add(new Scenario("carrier", carrier(), (c, s) -> { }, null));
        out.add(new Scenario("moored", moored(), (c, s) -> { }, null));
        out.add(new Scenario("capture", zeppelin(), (c, s) -> c.pick(Control.LIFT, Keymap.MINUS), null));
        out.add(new Scenario("menu", zeppelin(), (c, s) -> c.openLightMenu(AerialConsole.width(s[0]) / 2 + (s[0] - AerialConsole.width(s[0])) / 2,
                (s[1] - AerialConsole.height(s[1])) / 2 + 80, 9001L, AirLight.BEACON.ordinal(), AirLight.RED), null));
        out.add(new Scenario("empty", empty(), (c, s) -> { }, null));
        out.add(new Scenario("nothing", nothing(), (c, s) -> { }, null));
        out.add(new Scenario("nose_auto", noseAuto(), (c, s) -> { }, null));
        out.add(new Scenario("thrusters", thrusters(), (c, s) -> { }, null));
        out.add(new Scenario("gunnery", gunnery(), (c, s) -> { }, null));
        out.add(new Scenario("weighing", weighing(), (c, s) -> { }, null));
        out.add(new Scenario("gantry", gantry(), (c, s) -> { }, null));
        out.add(new Scenario("goto_lost", gotoLost(), (c, s) -> { }, null));
        out.add(new Scenario("staff", staffed(), (c, s) -> { }, null));
        out.add(new Scenario("owner_goto", ownerGoTo(), (c, s) -> c.pickEngine(2100L), null));
        out.add(new Scenario("leave_menu", zeppelin(), (c, s) -> c.openLeaveMenu(AerialConsole.width(s[0]) / 2 + (s[0] - AerialConsole.width(s[0])) / 2,
                (s[1] + AerialConsole.height(s[1])) / 2 - 22), null));
        return out;
    }

    static AerialView thrusters() {
        AerialView z = zeppelin();
        List<AerialView.Engine> engines = new ArrayList<>(threeEngines());
        engines.add(new AerialView.Engine(2101L, AerialSetup.EngineJob.AUTO.ordinal(), false, 100, 62, 0.6f, 440f, 1, -20f, -6f, 0f,
                AerialView.DIR_AHEAD, AerialView.ENGINE_OK, true, AerialView.KIND_VECTOR, 0, 0.8f));
        engines.add(new AerialView.Engine(2102L, AerialSetup.EngineJob.AUTO.ordinal(), false, 100, 0, 0f, 0f, 1, 18f, -3f, -5f,
                AerialView.DIR_RIGHT, AerialView.ENGINE_STARVED, false, AerialView.KIND_THRUSTER, 3));
        engines.add(new AerialView.Engine(2103L, AerialSetup.EngineJob.OFF.ordinal(), false, 100, 0, 0f, 0f, 1, 0f, -9f, 0f,
                AerialView.DIR_UP, AerialView.ENGINE_OFF, false, AerialView.KIND_BOOSTER, 1));
        return new AerialView(z.name(), z.pilot(), z.youPilot(), z.onCraft(), z.standDown(), z.mode(), z.leave(), z.feel(), z.flight(),
                z.side(), z.balloons(), engines, z.surfaces(), z.tools(), z.lights(), z.keymap(), z.held(), z.noseTurn(), z.noseAuto(),
                z.bell(), z.blocks(), z.mass(), z.gravity(), z.learned(), z.notes(), false);
    }

    static AerialView gunnery() {
        AerialView z = zeppelin();
        List<AerialView.Tool> tools = List.of(
                new AerialView.Tool(4001L, "block.createbigcannons.cannon_mount", PartInput.OFF.ordinal(), 32, false, false, 0f, false, true, true),
                new AerialView.Tool(4002L, "block.createbigcannons.cannon_mount", PartInput.OFF.ordinal(), 32, false, false, 0f, false, true, false),
                new AerialView.Tool(4003L, "block.create.rope_pulley", PartInput.AUX_A.ordinal(), 64, false, false, 0.5f, false));
        return new AerialView(z.name(), z.pilot(), z.youPilot(), z.onCraft(), z.standDown(), z.mode(), z.leave(), z.feel(), z.flight(),
                z.side(), z.balloons(), z.engines(), z.surfaces(), tools, z.lights(), z.keymap(), z.held(), z.noseTurn(), z.noseAuto(),
                z.bell(), z.blocks(), z.mass(), z.gravity(), z.learned(), z.notes(), true);
    }

    static AerialView weighing() {
        AerialView z = zeppelin();
        AerialView.Flight f = new AerialView.Flight(66.4f, 0f, 69.4f, 150f, 150f, Float.NaN, 63f, 0.2f, 320f, 0f, 0f, 0f, 0f, 16f, 16f,
                0f, 0f, 0f, 0f, 0f, false, Float.NaN, 5.1f, 3, 0.94f, false, 0f, 0f, 0f, 1f, 4600f, 2300f, 51000f, true);
        AerialView.Learned learned = new AerialView.Learned(0.42f, 0f, 0f, -0.31f * 1840f * 11f, 3100f, 2, 5);
        return new AerialView(z.name(), z.pilot(), z.youPilot(), z.onCraft(), z.standDown(), 1, z.leave(), z.feel(), f, z.side(),
                z.balloons(), z.engines(), z.surfaces(), z.tools(), z.lights(), z.keymap(), z.held(), z.noseTurn(), z.noseAuto(), z.bell(),
                z.blocks(), z.mass(), z.gravity(), learned,
                List.of("Weighing off: the ship is heavier than its own blocks (a body resting on it?), so the balloons take up lift until it rises."),
                false);
    }

    static AerialView gantry() {
        int cols = 60;
        int rows = 24;
        byte[] bits = new byte[(cols * rows + 7) >> 3];
        byte[] env = new byte[(cols * rows + 7) >> 3];
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                boolean envelope = row >= 12 && row <= 22 && col >= 2 && col <= 57 && !(row >= 21 && (col < 5 || col > 54));
                boolean deck = row == 11 && col >= 1 && col <= 58;
                boolean leg = row <= 10 && (col >= 6 && col <= 8 || col >= 51 && col <= 53);
                boolean floor = row <= 1 && col >= 6 && col <= 53;
                boolean post = row >= 2 && row <= 5 && col >= 22 && col <= 23;
                int i = row * cols + col;
                if (envelope) env[i >> 3] |= (byte) (1 << (i & 7));
                if (envelope || deck || leg || floor || post) bits[i >> 3] |= (byte) (1 << (i & 7));
            }
        }
        int topRows = 22;
        byte[] top = new byte[(cols * topRows + 7) >> 3];
        byte[] topEnv = new byte[(cols * topRows + 7) >> 3];
        for (int row = 0; row < topRows; row++) {
            for (int col = 0; col < cols; col++) {
                boolean envelope = row >= 3 && row <= 18 && col >= 2 && col <= 57;
                boolean frame = row >= 1 && row <= 20 && (col == 7 || col == 52) || (row == 1 || row == 20) && col >= 7 && col <= 52;
                int i = row * cols + col;
                if (envelope) topEnv[i >> 3] |= (byte) (1 << (i & 7));
                if (envelope || frame) top[i >> 3] |= (byte) (1 << (i & 7));
            }
        }
        AerialView.Side side = new AerialView.Side(1f, cols, rows, -30f, -12f, bits, topRows, -11f, top, env, topEnv);
        List<AerialView.Engine> engines = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            engines.add(new AerialView.Engine(2000L + i, 0, true, 100, 16, 0.35f, 120f, 1, -24f + 6f * i, 11f, 0f, AerialView.DIR_AHEAD,
                    AerialView.ENGINE_OK, true));
        }
        engines.add(new AerialView.Engine(2100L, 0, false, 100, 40, 0.4f, 267f, 1, -28f, 2f, 0f, AerialView.DIR_AHEAD, AerialView.ENGINE_OK,
                true, AerialView.KIND_THRUSTER, 0));
        List<AerialView.Balloon> balloons = List.of(
                new AerialView.Balloon(1001L, "hot_air", 560f, 600f, 1924f, 1924f, 6, 4, 0.99f, true, 0f, 5f, 1.21f));
        AerialView.Flight f = new AerialView.Flight(75.8f, 0.1f, 78f, 190f, 190f, Float.NaN, 63f, 0.9f, 320f, 0.4f, 0.3f, 0f, 0.4f, 16f, 16f,
                0f, 1.2f, -0.3f, (float) Telegraph.Notch.SLOW.value, 0.25f, false, Float.NaN, 5.1f, 3, 0.62f, false, 0f, 0f, 0f, 0.98f,
                3200f, 1600f, 40000f, false);
        List<AerialView.Tool> tools = List.of(
                new AerialView.Tool(4001L, "block.createbigcannons.cannon_mount", PartInput.OFF.ordinal(), 32, false, false, 0f, false, true, false),
                new AerialView.Tool(4002L, "block.createbigcannons.cannon_mount", PartInput.OFF.ordinal(), 32, false, false, 0f, false, true, true));
        return new AerialView("Ship 53da7fe2", "AEA19", true, true, "", 1, 0, 1, f, side, balloons, engines, List.of(), tools, List.of(),
                keymap(), new int[0], 1, true, true, 1562, 697.5f, 11f, new AerialView.Learned(0.63f, 0f, 0f, -0.32f * 697.5f * 11f, 2400f, 9, 9),
                List.of(), true);
    }

    static AerialView noseAuto() {
        AerialView z = zeppelin();
        return new AerialView(z.name(), z.pilot(), z.youPilot(), z.onCraft(), z.standDown(), z.mode(), z.leave(), z.feel(),
                z.flight(), z.side(), z.balloons(), z.engines(), z.surfaces(), z.tools(), z.lights(), z.keymap(), z.held(), 3,
                true, z.bell(), z.blocks(), z.mass(), z.gravity(), z.learned(), z.notes());
    }

    private static int[] keymap() {
        return AerialSetup.defaultKeys().toInts();
    }

    private static AerialView.Side side() {
        int cols = 48;
        int rows = 18;
        byte[] bits = new byte[(cols * rows + 7) >> 3];
        byte[] env = new byte[(cols * rows + 7) >> 3];
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                double x = (col - 23.5) / 22.0;
                double y = (row - 10.5) / 6.5;
                boolean envelope = x * x + y * y <= 1.0;
                boolean gondola = row >= 1 && row <= 3 && col >= 18 && col <= 30;
                boolean strut = row >= 3 && row <= 5 && (col == 20 || col == 28);
                boolean fin = col <= 5 && row >= 7 && row <= 16 && Math.abs(row - 10.5) <= 6 - col;
                int i = row * cols + col;
                if (envelope && !fin) env[i >> 3] |= (byte) (1 << (i & 7));
                if (envelope || gondola || strut || fin) bits[i >> 3] |= (byte) (1 << (i & 7));
            }
        }
        int topRows = 14;
        byte[] top = new byte[(cols * topRows + 7) >> 3];
        byte[] topEnv = new byte[(cols * topRows + 7) >> 3];
        for (int row = 0; row < topRows; row++) {
            for (int col = 0; col < cols; col++) {
                double x = (col - 23.5) / 22.0;
                double y = (row - 6.5) / 6.0;
                boolean envelope = x * x + y * y <= 1.0;
                boolean fin = col <= 5 && Math.abs(row - 6.5) <= 6 - col && Math.abs(row - 6.5) >= 4;
                int i = row * cols + col;
                if (envelope && !fin) topEnv[i >> 3] |= (byte) (1 << (i & 7));
                if (envelope || fin) top[i >> 3] |= (byte) (1 << (i & 7));
            }
        }
        return new AerialView.Side(1f, cols, rows, -24f, -11f, bits, topRows, -7f, top, env, topEnv);
    }

    private static AerialView.Flight flight(float order, float engine, boolean braking, float targetSpeed) {
        return new AerialView.Flight(142.3f, 0.8f, 150f, 211f, 230f, Float.NaN, 96f, 41.5f, 320f, 4.4f, 4.2f, -0.3f, 5.1f, 88f,
                95f, 1.2f, -1.5f, 0.8f, order, engine, braking, targetSpeed, 9.4f, 12, 0.72f, false, 0.25f, 3.2f, 240f, 0.96f,
                4600f, 2300f, 51000f);
    }

    private static List<AerialView.Balloon> twoBalloons() {
        return List.of(
                new AerialView.Balloon(1001L, "hot_air", 812f, 840f, 1024f, 960f, 4, 3, 0.96f, true, 7.5f, 5.0f, 0.52f),
                new AerialView.Balloon(1002L, "hot_air", 760f, 800f, 1024f, 960f, 4, 2, 0.96f, true, -8.0f, 5.0f, 0.48f));
    }

    private static List<AerialView.Engine> threeEngines() {
        return List.of(
                new AerialView.Engine(2001L, AerialSetup.EngineJob.AUTO.ordinal(), true, 100, 128, 0.5f, 1150f, 2, -14f, -9f, -4f,
                        AerialView.DIR_AHEAD, AerialView.ENGINE_OK, true),
                new AerialView.Engine(2002L, AerialSetup.EngineJob.AUTO.ordinal(), true, 100, 120, 0.46f, 1060f, 2, -14f, -9f, 4f,
                        AerialView.DIR_AHEAD, AerialView.ENGINE_OK, true),
                new AerialView.Engine(2003L, AerialSetup.EngineJob.LIFT.ordinal(), false, 75, 40, 0.2f, 300f, 1, 2f, -10f, 0f,
                        AerialView.DIR_UP, AerialView.ENGINE_OK, false));
    }

    private static List<AerialView.Surface> fins() {
        return List.of(
                new AerialView.Surface(3001L, AerialSetup.SurfaceRole.RUDDER.ordinal(), 30, false, PartInput.OFF.ordinal(), false,
                        12.0f, true, true, -21f, 0),
                new AerialView.Surface(3002L, AerialSetup.SurfaceRole.ELEVATOR.ordinal(), 20, true, PartInput.OFF.ordinal(), false,
                        -5.0f, true, true, -21f, 1),
                new AerialView.Surface(3003L, AerialSetup.SurfaceRole.TOOL.ordinal(), 180, false, PartInput.AUX_B.ordinal(), true,
                        -135.0f, true, true, 6f, 2));
    }

    private static List<AerialView.Tool> tools() {
        return List.of(
                new AerialView.Tool(4001L, "block.create.rope_pulley", PartInput.AUX_A.ordinal(), 64, false, false, 1.0f, false),
                new AerialView.Tool(4002L, "block.create.mechanical_drill", PartInput.AUX_C.ordinal(), 96, true, false, 0f, false));
    }

    private static List<AerialView.Light> lights() {
        return List.of(
                new AerialView.Light(5001L, AirLight.RED, "block.create_stats.vehicle_light_red", "", AirLight.BEACON.ordinal(), false,
                        15, 0f, 12f, 0f),
                new AerialView.Light(5002L, AirLight.RED, "block.create_stats.vehicle_light_red", "", AirLight.POSITION.ordinal(), false,
                        15, 2f, -2f, -7f),
                new AerialView.Light(5003L, AirLight.WHITE, "block.create_stats.vehicle_light_white", "", AirLight.LANDING.ordinal(),
                        false, 0, 8f, -10f, 0f),
                new AerialView.Light(5004L, AirLight.RECEIVER, "item.minecraft.iron_ingot", "item.minecraft.gold_ingot",
                        AirLight.AUX_D_PLUS.ordinal(), true, 0, -4f, -9f, 2f));
    }

    private static AerialView.Learned learned() {
        return new AerialView.Learned(0.42f, 2.1f, 0.03f, 150f, 3100f, 4, 5);
    }

    static AerialView zeppelin() {
        return new AerialView("Graf Spee", "Dev", true, true, "", 1, 0, 1,
                flight((float) Telegraph.Notch.HALF.value, 0.5f, false, Float.NaN), side(), twoBalloons(), threeEngines(), fins(), tools(),
                lights(), keymap(), new int[] {Channel.FORWARD.id()}, 0, true, 2140, 1840f, 11f, learned(), List.of());
    }

    private static AerialView goingTo() {
        return new AerialView("Graf Spee", "", false, true, "", 4, 2, 1, flight(0f, 0f, false, 4.0f), side(), twoBalloons(),
                threeEngines(), fins(), tools(), lights(), keymap(), new int[0], 0, true, true, 2140, 1840f, 11f, learned(), List.of(),
                false, new AerialView.Nav(AerialView.NAV_LODESTONE, 448.5f, 63.5f, 540.5f, 312f, 45f, true, ""), AerialView.STAFF_NONE);
    }

    static AerialView gotoLost() {
        AerialView z = zeppelin();
        return new AerialView(z.name(), z.pilot(), z.youPilot(), z.onCraft(), z.standDown(), 1, 2, z.feel(), z.flight(), z.side(),
                z.balloons(), z.engines(), z.surfaces(), z.tools(), z.lights(), z.keymap(), z.held(), z.noseTurn(), z.noseAuto(),
                z.bell(), z.blocks(), z.mass(), z.gravity(), z.learned(),
                List.of("The compass on the Navigation Table has lost its lodestone: lock it on one again for Go to."), false,
                new AerialView.Nav(AerialView.NAV_NONE, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, false,
                        "The compass on the Navigation Table has lost its lodestone: lock it on one again for Go to."),
                AerialView.STAFF_NONE);
    }

    static AerialView staffed() {
        AerialView z = zeppelin();
        return new AerialView(z.name(), z.pilot(), z.youPilot(), z.onCraft(), z.standDown(), 2, z.leave(), z.feel(), z.flight(),
                z.side(), z.balloons(), z.engines(), z.surfaces(), z.tools(), z.lights(), z.keymap(), z.held(), z.noseTurn(),
                z.noseAuto(), z.bell(), z.blocks(), z.mass(), z.gravity(), z.learned(),
                List.of("A physics staff holds the ship: the engines idle, the gas holds a hover and nothing is learned. Let go and it"
                        + " stays where you put it."), false, AerialView.Nav.NONE, AerialView.STAFF_DRAGGED);
    }

    static AerialView ownerGoTo() {
        AerialView g = gantry();
        List<AerialView.Engine> engines = new ArrayList<>(g.engines());
        AerialView.Engine t = engines.remove(engines.size() - 1);
        engines.add(new AerialView.Engine(t.pos(), t.job(), t.reverse(), t.limit(), 40, 0.4f, t.thrust(), 1, t.lon(), t.up(), t.lat(),
                t.dir(), t.state(), true, AerialView.KIND_THRUSTER, 0, 0.85f));
        return new AerialView(g.name(), "", false, true, "", 4, 2, 2, g.flight(), g.side(), g.balloons(), engines, g.surfaces(),
                g.tools(), g.lights(), g.keymap(), new int[0], g.noseTurn(), g.noseAuto(), g.bell(), g.blocks(), g.mass(), g.gravity(),
                g.learned(), List.of(), true, new AerialView.Nav(AerialView.NAV_LODESTONE, 448.5f, 63.5f, 540.5f, 311.6f, 26f, true, ""),
                AerialView.STAFF_NONE);
    }

    private static AerialView anchored() {
        return new AerialView("Graf Spee", "", false, true, "", 2, 0, 1,
                flight(0f, 0f, true, 0f), side(), twoBalloons(), threeEngines(), fins(), tools(), lights(), keymap(), new int[0], 0,
                true, 2140, 1840f, 11f, learned(), List.of());
    }

    private static AerialView astern() {
        return new AerialView("Hindenburg the Second of Her Name, Pride of the Northern Skies", "Somebody_With_A_Long_Name", false, true,
                "", 1, 1, 2, flight((float) Telegraph.Notch.FULL_ASTERN.value, -0.6f, false, Float.NaN), side(), twoBalloons(),
                threeEngines(), fins(), tools(), lights(), keymap(), new int[0], 1, false, 2140, 1840f, 11f, learned(), List.of());
    }

    private static AerialView trouble() {
        List<AerialView.Balloon> balloons = List.of(
                new AerialView.Balloon(1001L, "steam", 1024f, 1024f, 1024f, 1024f, 6, 6, 0.8f, true, 3f, 4f, 0.61f),
                new AerialView.Balloon(1002L, "hot_air", 200f, 0f, 1024f, 400f, 2, 0, 0.8f, false, -6f, 4f, 0.12f));
        List<AerialView.Engine> engines = List.of(
                new AerialView.Engine(2001L, 0, true, 50, 0, 0f, 0f, 1, -9f, -3f, 0f, AerialView.DIR_AHEAD, AerialView.ENGINE_STARVED, false),
                new AerialView.Engine(2002L, 1, false, 25, 256, 1f, 900f, 4, 0f, -3f, 6f, AerialView.DIR_RIGHT, AerialView.ENGINE_OVERSTRESSED, true),
                new AerialView.Engine(2003L, 0, true, 100, 0, 0f, 0f, 1, 9f, -3f, 0f, AerialView.DIR_ASTERN, AerialView.ENGINE_FOREIGN, true),
                new AerialView.Engine(2004L, 3, true, 100, 0, 0f, 0f, 1, 9f, 3f, 0f, AerialView.DIR_DOWN, AerialView.ENGINE_OFF, true));
        List<AerialView.Surface> surfaces = List.of(
                new AerialView.Surface(3001L, AerialSetup.SurfaceRole.RUDDER.ordinal(), 45, false, 8, false, 0f, false, false, -12f, 0),
                new AerialView.Surface(3002L, AerialSetup.SurfaceRole.TOOL.ordinal(), 10, true, PartInput.SLIDE.ordinal(), false, -10f,
                        true, false, 2f, 2),
                new AerialView.Surface(3003L, AerialSetup.SurfaceRole.OFF.ordinal(), 10, false, 8, false, 0f, true, true, 0f, 1));
        List<AerialView.Tool> tools = List.of(
                new AerialView.Tool(4001L, "block.create.mechanical_bearing", PartInput.AUX_A.ordinal(), 256, true, true, -1f, true),
                new AerialView.Tool(4002L, "", PartInput.OFF.ordinal(), 8, false, false, 0f, false));
        AerialView.Flight f = new AerialView.Flight(250.8f, -2.4f, 211f, 211f, 211f, 200f, 64f, 180f, 320f, 0.4f, 0.1f, 0.4f, 0.6f,
                359.6f, 1.0f, -9.9f, 12.0f, -8.0f, 0.37f, 0.3f, false, Float.NaN, Float.NaN, 40, 1.35f, true, -1.0f, Float.NaN, 0f,
                0.6f, 0f, 0f, 0f);
        return new AerialView("Wreck", "", false, true, "", 3, 1, 0, f, AerialView.Side.NONE, balloons, engines, surfaces, tools,
                List.of(), keymap(), new int[0], 3, true, 900, 5200f, 11f, AerialView.Learned.NONE,
                List.of("Lift short: even full burners cannot hold this. Lighten the ship or add burners.",
                        "G1 gets no rotation from its engine: is the engine running and its shaft connected?",
                        "G2's shaft network is overstressed, so every prop on it stops: it needs more stress capacity.",
                        "S1 is not locked, so it cannot hold an angle: give its swivel bearing a redstone signal.",
                        "The engines push across the Aerial's nose: turn the nose on the console's Ship page, or with a wrench."));
    }

    private static AerialView carrier() {
        List<AerialView.Balloon> balloons = new ArrayList<>();
        for (int i = 0; i < 14; i++) {
            balloons.add(new AerialView.Balloon(1000L + i, i % 3 == 0 ? "steam" : "hot_air", 400f + 40 * i, 500f, 1024f, 900f, 3 + i % 4,
                    i % 4, 0.9f, i % 5 != 0, -30f + 4.5f * i, 6f, 0.07f));
        }
        List<AerialView.Engine> engines = new ArrayList<>();
        for (int i = 0; i < 16; i++) {
            engines.add(new AerialView.Engine(2000L + i, i % 4, i % 2 == 0, AerialSetup.LIMITS[i % 4], 16 * i, (i % 5 - 2) / 2f,
                    120f * i, 1 + i % 3, -30f + 4f * i, -8f + i % 3, -12f + 1.5f * i, i % 6, i % 5 == 4 ? AerialView.ENGINE_STARVED : 0,
                    i % 3 != 0));
        }
        List<AerialView.Surface> surfaces = new ArrayList<>();
        for (int i = 0; i < 11; i++) {
            surfaces.add(new AerialView.Surface(3000L + i, i % 4, AerialSetup.THROWS[i % 5], i % 2 == 0, i % 9, i % 3 == 0,
                    (i % 7 - 3) * 5f, true, true, -30f + 5f * i, i % 3));
        }
        List<AerialView.Tool> tools = new ArrayList<>();
        String[] kinds = {"block.create.rope_pulley", "block.create.mechanical_piston", "block.create.mechanical_drill",
                "block.create.gantry_carriage", "block.create.mechanical_saw", "block.create.encased_fan"};
        for (int i = 0; i < 9; i++) {
            tools.add(new AerialView.Tool(4000L + i, kinds[i % kinds.length], i % 9, 8 + 24 * i, i % 2 == 0, i % 3 == 0,
                    (i % 3 - 1), false));
        }
        List<AerialView.Light> lights = new ArrayList<>();
        for (int i = 0; i < AirLight.values().length; i++) {
            lights.add(new AerialView.Light(5000L + i, (i % 4) - 1, "item.minecraft.redstone", "item.minecraft.glowstone_dust", i,
                    i % 2 == 0, i % 3 == 0 ? 15 : i % 3 == 1 ? 8 : 0, -20f + 2f * i, (i % 5) - 2f, (i % 7) - 3f));
        }
        return new AerialView("Carrier", "Dev", true, true, "", 1, 2, 1, flight((float) Telegraph.Notch.SLOW.value, 0.25f, false, Float.NaN),
                side(), balloons, engines, surfaces, tools, lights, keymap(), new int[] {Channel.JUMP.id(), KeyIds.key(90)}, 2, true,
                12000, 26000f, 11f, learned(), List.of("No Navigation Table target to go to: holding this spot."));
    }

    private static AerialView moored() {
        AerialView.Flight f = new AerialView.Flight(66f, 0f, Float.NaN, 230f, 230f, Float.NaN, 63f, 0.2f, 320f, 0f, 0f, 0f, 0f,
                180f, Float.NaN, 0f, 0f, 0f, 0f, 0f, false, Float.NaN, Float.NaN, 0, Float.NaN, false, 0f, 0.2f, 90f, 1f, 4600f,
                2300f, 51000f);
        return new AerialView("Graf Spee", "", false, true, "", 5, 0, 1, f, side(), twoBalloons(), threeEngines(), fins(), tools(),
                lights(), keymap(), new int[0], 0, true, 2140, 1840f, 11f, learned(), List.of());
    }

    private static AerialView empty() {
        return new AerialView("", "", false, false,
                "Not on a craft. Build it into a physics craft (Physics Assembler) with the balloons and props it should fly.", 0, 0,
                1, AerialView.Flight.NONE, AerialView.Side.NONE, List.of(), List.of(), List.of(), List.of(), List.of(), keymap(),
                new int[0], 0, true, -1, Float.NaN, Float.NaN, AerialView.Learned.NONE,
                List.of("Not on a craft. Build it into a physics craft (Physics Assembler) with the balloons and props it should fly."));
    }

    static AerialView nothing() {
        return new AerialView("Raft", "", false, true, "", 0, 0, 1, flight(0f, 0f, false, Float.NaN), AerialView.Side.NONE, List.of(),
                List.of(), List.of(), List.of(), List.of(), keymap(), new int[0], 0, true, 40, 400f, 11f, AerialView.Learned.NONE,
                List.of("No balloon with a burner or vent under it: nothing to hold the ship up with."));
    }

    static final class Winding implements ConsoleCanvas {
        final Vcc4ConsoleLayoutTest.Recorder recorder;
        final List<String> wrong = new ArrayList<>();

        Winding(Vcc4ConsoleLayoutTest.Recorder recorder) {
            this.recorder = recorder;
        }

        @Override
        public void fill(int x0, int y0, int x1, int y1, int argb) {
            recorder.fill(x0, y0, x1, y1, argb);
        }

        @Override
        public void text(String text, int x, int y, int argb) {
            recorder.text(text, x, y, argb);
        }

        @Override
        public void quad(float x0, float y0, float x1, float y1, float x2, float y2, float x3, float y3, int argb) {
            double area = (x0 * y1 - x1 * y0) + (x1 * y2 - x2 * y1) + (x2 * y3 - x3 * y2) + (x3 * y0 - x0 * y3);
            if (area < -1.0e-3 && wrong.size() < 5) {
                wrong.add(String.format(Locale.ROOT, "quad wound anticlockwise (%.1f,%.1f %.1f,%.1f %.1f,%.1f %.1f,%.1f)", x0, y0, x1, y1,
                        x2, y2, x3, y3));
            }
            recorder.quad(x0, y0, x1, y1, x2, y2, x3, y3, argb);
        }

        @Override
        public void layer() {
            recorder.layer();
        }
    }

    static final class Sent implements ConsoleHost {
        final Vcc4ConsoleLayoutTest.TestHost base;
        final List<String> sent = new ArrayList<>();

        Sent(Vcc4ConsoleLayoutTest.TestHost base) {
            this.base = base;
        }

        @Override
        public int width(String text) {
            return base.width(text);
        }

        @Override
        public String tr(String key, Object... args) {
            return base.tr(key, args);
        }

        @Override
        public String name(String idOrName) {
            return base.name(idOrName);
        }

        @Override
        public String keyName(int keyId) {
            return base.keyName(keyId);
        }

        @Override
        public List<String> keyAlso(int keyId) {
            return base.keyAlso(keyId);
        }

        @Override
        public boolean keyLeavesSeat(int keyId) {
            return base.keyLeavesSeat(keyId);
        }

        @Override
        public int resolveKey(int glfwKey, int scanCode) {
            return base.resolveKey(glfwKey, scanCode);
        }

        @Override
        public int resolveMouse(int button) {
            return base.resolveMouse(button);
        }

        @Override
        public void send(int action, int a, int b, long target) {
            sent.add(action + ":" + a);
        }

        @Override
        public void click() {
        }
    }
}
