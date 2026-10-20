package com.createstats.vccmk4.console;

import com.createstats.surveyor.core.Corner;
import com.createstats.surveyor.core.PathKind;
import com.createstats.surveyor.core.PathNode;
import com.createstats.surveyor.core.Pathway;
import com.createstats.surveyor.core.RouteRunner;
import com.createstats.surveyor.core.Stop;
import com.createstats.surveyor.core.StopCondition;
import org.junit.jupiter.api.Test;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PathwayBoardLayoutTest {
    private static final Path OUT = Path.of("build/pathway-board");

    private static final int[][] SCREENS = {{464, 280}, {544, 334}, {853, 533}};
    static final UUID ME = new UUID(0x51L, 0x1L);
    static final UUID OTHER = new UUID(0x51L, 0x2L);
    static final UUID QUARRY = new UUID(0x77L, 0x1L);
    static final UUID FARM = new UUID(0x77L, 0x2L);
    static final UUID SHARED = new UUID(0x77L, 0x3L);
    static final UUID HAULER = new UUID(0x99L, 0x1L);
    static final UUID TIPPER = new UUID(0x99L, 0x2L);
    static final UUID LORRY = new UUID(0x99L, 0x3L);

    @Test
    void thePanelGrowsWithTheScreenAndStopsAtItsLimits() {
        assertEquals(PathwayBoard.MAX_W, PathwayBoard.width(853), "the owner's 2560x1600 at scale 3 gets the largest panel");
        assertEquals(PathwayBoard.MIN_W, PathwayBoard.width(300), "never narrower than the layout was checked at");
        assertEquals(PathwayBoard.MIN_H, PathwayBoard.height(200));
    }

    private static Vcc4ConsoleLayoutTest.Recorder frame(PathwayBoard board, Vcc4ConsoleLayoutTest.TestHost base) {
        board.render(new Vcc4ConsoleLayoutTest.Recorder(base), 853, 533, -1, -1);
        Vcc4ConsoleLayoutTest.Recorder r = new Vcc4ConsoleLayoutTest.Recorder(base);
        board.render(r, 853, 533, -1, -1);
        return r;
    }

    record World(List<BoardHost.Line> lines, List<BoardHost.Vehicle> vehicles, List<BoardHost.LogLine> log,
                 java.util.Map<UUID, List<BoardHost.Health>> health, List<BoardHost.Terrain> terrain) {
        World(List<BoardHost.Line> lines, List<BoardHost.Vehicle> vehicles, List<BoardHost.LogLine> log) {
            this(lines, vehicles, log, java.util.Map.of(), List.of());
        }

        World(List<BoardHost.Line> lines, List<BoardHost.Vehicle> vehicles, List<BoardHost.LogLine> log,
              java.util.Map<UUID, List<BoardHost.Health>> health) {
            this(lines, vehicles, log, health, List.of());
        }
    }

    private record Scenario(String name, World world, @Nullable UUID pathway, @Nullable UUID vehicle, int node, int[] mouse,
                            int centre) {
        Scenario(String name, World world, @Nullable UUID pathway, @Nullable UUID vehicle, int node, int[] mouse) {
            this(name, world, pathway, vehicle, node, mouse, PathwayBoard.CENTRE_MAP);
        }

        int mouseX(int[] screen) {
            return mouse == null ? -1 : screen[0] * mouse[0] / 100;
        }

        int mouseY(int[] screen) {
            return mouse == null ? -1 : screen[1] * mouse[1] / 100;
        }
    }

    private static List<Scenario> scenarios() {
        World quarry = quarry();
        Pathway loop = quarry.lines().get(0).pathway();
        int stopNode = loop.nodes().get(3).id();
        int plainNode = loop.nodes().get(6).id();
        List<Scenario> out = new ArrayList<>();
        out.add(new Scenario("empty", new World(List.of(), List.of(), List.of()), null, null, -1, null));
        out.add(new Scenario("quarry", quarry, null, null, -1, null));
        out.add(new Scenario("quarry_hover", quarry, null, null, -1, new int[]{50, 40}));
        out.add(new Scenario("quarry_pathway", quarry, QUARRY, null, -1, null));
        out.add(new Scenario("quarry_vehicle", quarry, null, TIPPER, -1, null));
        out.add(new Scenario("quarry_blocked", quarry, null, LORRY, -1, null));
        out.add(new Scenario("quarry_stop", quarry, QUARRY, null, stopNode, null));
        out.add(new Scenario("quarry_node", quarry, QUARRY, null, plainNode, null));
        out.add(new Scenario("shared", quarry, SHARED, null, -1, null));
        out.add(new Scenario("quarry_health", withHealth(quarry, loop), QUARRY, null, -1, null));
        out.add(new Scenario("farm_clean", new World(quarry.lines(), quarry.vehicles(), quarry.log(),
                java.util.Map.of(FARM, List.of())), FARM, null, -1, null));
        out.add(new Scenario("many", many(), null, null, -1, null));
        out.add(new Scenario("stations", quarry, null, null, -1, null, PathwayBoard.CENTRE_STATIONS));
        out.add(new Scenario("stations_none", new World(List.of(quarry.lines().get(1)), List.of(), List.of()), null, null, -1, null,
                PathwayBoard.CENTRE_STATIONS));
        out.add(new Scenario("timetable", quarry, QUARRY, null, -1, null, PathwayBoard.CENTRE_GRAPH));
        out.add(new Scenario("timetable_pick", quarry, null, null, -1, null, PathwayBoard.CENTRE_GRAPH));
        return out;
    }

    static List<BoardHost.Trail> quarryTrails(Pathway loop, List<BoardHost.Vehicle> vehicles, long now) {
        com.createstats.surveyor.core.PathSpline sp = com.createstats.surveyor.core.PathSpline.of(loop);
        double len = sp.length();
        double face = sp.nodeS(3);
        List<BoardHost.Trail> out = new ArrayList<>();
        for (BoardHost.Vehicle v : vehicles) {
            if (!v.pathway().equals(loop.id())) continue;
            double speed;
            long flat;
            if (v.console().equals(HAULER)) {
                speed = 6.2;
                flat = 0;
            } else if (v.console().equals(TIPPER)) {
                speed = 5.0;
                flat = 1200;
            } else {
                speed = 4.0;
                flat = 1500;
            }
            int n = 301;
            long[] time = new long[n];
            float[] s = new float[n];
            int[] laps = new int[n];
            double at = v.s();
            int lap = v.laps();
            long waitLeft = 0;
            boolean waited = false;
            for (int i = n - 1; i >= 0; i--) {
                long t = now - (long) (n - 1 - i) * 20L;
                time[i] = t;
                s[i] = (float) at;
                laps[i] = lap;
                if (now - t < flat) continue;
                if (waitLeft > 0) {
                    waitLeft -= 20;
                    continue;
                }
                double before = at - speed;
                if (v.console().equals(HAULER) && !waited && before < face && at >= face) {
                    waited = true;
                    waitLeft = 800;
                    at = face;
                    continue;
                }
                at = before;
                if (at < 0) {
                    at += len;
                    lap--;
                }
            }
            out.add(new BoardHost.Trail(v.console(), v.name(), time, s, laps));
        }
        return out;
    }

    static World withHealth(World w, Pathway loop) {
        int n3 = loop.nodes().get(2).id();
        int n9 = loop.nodes().get(8).id();
        List<BoardHost.Health> items = List.of(
                new BoardHost.Health(PathwayBoard.HEALTH_RED, 40.0, 52.0, "water at N3", PathwayBoard.FIX_MOVE_NODE, n3, 46.0),
                new BoardHost.Health(PathwayBoard.HEALTH_RED, 150.0, 166.0,
                        "the bend at N9 is tighter than Six-wheel tipper can turn: radius 3.6, its own 6.3", PathwayBoard.FIX_WIDEN, n9, 158.0),
                new BoardHost.Health(PathwayBoard.HEALTH_RED, 200.0, 201.0, "cobblestone at 12 65 -30", PathwayBoard.FIX_CLEAR, -1, 200.5),
                new BoardHost.Health(PathwayBoard.HEALTH_AMBER, 90.0, 110.0, "ice: a tenth of the grip, slow and early", 0, -1, 100.0),
                new BoardHost.Health(PathwayBoard.HEALTH_AMBER, 230.0, 236.0, "a 22 degree lean across the road", 0, -1, 233.0),
                new BoardHost.Health(PathwayBoard.HEALTH_UNKNOWN, 260.0, 300.0, "not surveyed yet", 0, -1, 280.0));
        return new World(w.lines(), w.vehicles(), w.log(), java.util.Map.of(QUARRY, items), List.of(lake()));
    }

    static BoardHost.Terrain lake() {
        int cell = 2;
        int x0 = -90;
        int z0 = -60;
        int w = 95;
        int h = 60;
        int[] argb = new int[w * h];
        for (int k = 0; k < h; k++) {
            for (int i = 0; i < w; i++) {
                double x = x0 + i * cell;
                double z = z0 + k * cell;
                int grass = ((i * 7 + k * 13) % 5 == 0) ? 0xFF6A9A3A : 0xFF7FB238;
                int c = grass;
                if (Math.hypot(x - 55.0, (z - 5.0) * 1.3) < 28.0) c = ((i + k) & 1) == 0 ? 0xFF3F76E4 : 0xFF3A6BD0;
                else if (x > -30 && x < 10 && z > -20 && z < 0) c = 0xFF707070;
                argb[k * w + i] = c;
            }
        }
        return new BoardHost.Terrain(x0, z0, cell, w, h, argb);
    }

    static Pathway loop(UUID id, String name, int colour, UUID owner, double cx, double cz, double r, int n) {
        Pathway p = Pathway.create(id, name, colour, owner);
        for (int i = 0; i < n; i++) {
            double a = 2.0 * Math.PI * i / n;
            p = p.append(cx + r * Math.cos(a), 64.0, cz + r * 0.6 * Math.sin(a), false);
        }
        return p.withKind(PathKind.LOOP);
    }

    static World quarry() {
        Pathway loop = loop(QUARRY, "Quarry loop", 3, ME, 0.0, 0.0, 60.0, 12);
        Stop face = new Stop(List.of(
                List.of(new StopCondition.Items(StopCondition.Op.GREATER, 1000, false, "minecraft:cobblestone"),
                        new StopCondition.Idle(20)),
                List.of(new StopCondition.Wait(120), new StopCondition.Link("minecraft:redstone", "minecraft:lever", true)),
                List.of(new StopCondition.Fluid(StopCondition.Op.LESS, 8, "minecraft:water"))),
                600, "minecraft:redstone", "minecraft:quartz", true, 1);
        loop = loop.update(loop.nodes().get(3).id(), n -> n.withStop(face).withName("Quarry face"));
        loop = loop.update(loop.nodes().get(7).id(), n -> n.withStop(Stop.DEFAULT).withName("Crusher"));
        loop = loop.update(loop.nodes().get(10).id(), n -> n.withStop(new Stop(List.of(), 30, "", "", false, -1)));
        loop = loop.update(loop.nodes().get(6).id(), n -> n.withSpeedLimit(3.5).withCorner(Corner.SHARP));
        Pathway farm = Pathway.create(FARM, "Farm run", 5, ME);
        for (int i = 0; i < 6; i++) farm = farm.append(80.0 + i * 14.0, 64.0, -40.0 + (i % 2) * 9.0, false);
        Pathway shared = loop(SHARED, "Northern ore line to the smeltery", 14, OTHER, -120.0, 90.0, 35.0, 8).withShared(true);
        List<BoardHost.Line> lines = List.of(
                new BoardHost.Line(loop, "Bolat", 3, 1, "tight corner at N7", true, true),
                new BoardHost.Line(farm, "Bolat", 0, 0, "", true, true),
                new BoardHost.Line(shared, "Someone_with_a_long_name", 0, 2, "stone at 120 64 -30", false, true));
        List<BoardHost.Vehicle> vehicles = List.of(
                new BoardHost.Vehicle(HAULER, QUARRY, "Hauler 2", RouteRunner.State.RUNNING.ordinal(), "", 30.0, 40.0, 30f, 6.2f,
                        80f, loop.nodes().get(7).id(), 42f, -1f, "", 3, 131f, 640),
                new BoardHost.Vehicle(TIPPER, QUARRY, "Six-wheel tipper", RouteRunner.State.WAITING.ordinal(), "", 20.0, 30.0, 120f,
                        0f, 150f, loop.nodes().get(3).id(), -1f, 0.62f, "cargo 620 > 1000 items, idle 12 of 20 s, wait 34 of 120 s",
                        1, 204f, 620),
                new BoardHost.Vehicle(LORRY, QUARRY, "Lorry with a very long name indeed", RouteRunner.State.BLOCKED.ordinal(),
                        "a player (Steve) at 12 64 -30", -50.0, -10.0, 250f, 0f, 260f, loop.nodes().get(10).id(), 95f, -1f, "",
                        0, -1f, -1));
        List<BoardHost.LogLine> log = new ArrayList<>();
        String[] said = {"Hauler 2 waited 42 s at Quarry face (cargo > 1000 items)", "Lorry with a very long name indeed blocked by a player (Steve) at N11",
                "Six-wheel tipper arrived at Quarry face", "Hauler 2 lapped Quarry loop in 2 min 11 s", "Hauler 2 joined Quarry loop at N2",
                "Six-wheel tipper set off on Quarry loop", "Lorry with a very long name indeed assigned to Quarry loop",
                "Hauler 2 recorded Farm run: 6 nodes, 0 stops"};
        for (int i = 0; i < said.length; i++) log.add(new BoardHost.LogLine(24000L - 200L * i - 15L, QUARRY, said[i]));
        return new World(lines, vehicles, log);
    }

    static World many() {
        List<BoardHost.Line> lines = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            Pathway p = loop(new UUID(0x88L, i), String.format(Locale.ROOT, "Route %02d to the far field", i), i % 16,
                    i % 3 == 0 ? OTHER : ME, i * 40.0, (i % 5) * 40.0, 15.0, 5 + i % 7);
            lines.add(new BoardHost.Line(p, i % 3 == 0 ? "Steve" : "Bolat", i % 4, i % 3, i % 3 == 0 ? "" : "steep at N3", i % 3 != 0, true));
        }
        List<BoardHost.LogLine> log = new ArrayList<>();
        for (int i = 0; i < 60; i++) log.add(new BoardHost.LogLine(24000L - 100L * i, new UUID(0x88L, i % 30), "Vehicle " + i + " arrived at N" + (i % 9)));
        return new World(lines, List.of(), log);
    }

    static final class Host implements BoardHost {
        final Vcc4ConsoleLayoutTest.TestHost base;
        final World world;
        final List<String> sent = new ArrayList<>();

        Host(Vcc4ConsoleLayoutTest.TestHost base, World world) {
            this.base = base;
            this.world = world;
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
        }

        @Override
        public void click() {
        }

        @Override
        public List<Line> pathways() {
            return world.lines();
        }

        @Override
        public List<Vehicle> vehicles() {
            return world.vehicles();
        }

        @Override
        public List<LogLine> log() {
            return world.log();
        }

        @Override
        public List<Health> health(UUID pathway) {
            return world.health().getOrDefault(pathway, List.of());
        }

        @Override
        public boolean healthKnown(UUID pathway) {
            return world.health().containsKey(pathway);
        }

        @Override
        public List<Terrain> terrain() {
            return world.terrain();
        }

        @Override
        public List<Trail> trails(UUID pathway) {
            for (Line l : world.lines()) {
                if (l.pathway().id().equals(pathway) && pathway.equals(QUARRY)) return quarryTrails(l.pathway(), world.vehicles(), now());
            }
            return List.of();
        }

        @Override
        public long now() {
            return 24000L;
        }

        @Nullable
        @Override
        public double[] you() {
            return new double[]{10.0, 20.0};
        }

        @Override
        public double groundY(double x, double z, double fallback) {
            return fallback;
        }

        @Override
        public String heldItem() {
            return "";
        }

        @Override
        public String heldFluid() {
            return "";
        }

        @Override
        public String itemName(String id) {
            return base.name("item." + id.replace(':', '.'));
        }

        @Override
        public void sendEdit(int op, UUID pathway, long version, int node, double x, double y, double z, int a, String text, byte[] data) {
            sent.add("edit " + op + " " + pathway + " " + node + " " + a);
        }

        @Override
        public void sendOrder(int op, UUID console, UUID pathway, int a) {
            sent.add("order " + op + " " + console + " " + a);
        }

        @Override
        public void useWithStaff(UUID pathway) {
        }

        @Override
        public void startNew() {
        }

        @Override
        public void askText(String current, int maxLength, Consumer<String> done) {
        }

        @Override
        public void pin(UUID console) {
        }
    }

    @Test
    void theQuarryIsWhatTheOtherTestsThinkItIs() {
        Pathway loop = quarry().lines().get(0).pathway();
        assertEquals(12, loop.nodes().size());
        assertEquals(3, loop.stopCount());
        PathNode face = loop.nodes().get(3);
        assertTrue(face.isStop());
        assertEquals(3, face.stop().rules().size(), "three ways to leave the quarry face");
        assertFalse(loop.nodes().get(6).isStop());
    }
}
