package com.createstats.battle.console;

import com.createstats.battle.core.BattleReport;
import com.createstats.battle.core.ComparatorScale;
import com.createstats.battle.core.GunArc;
import com.createstats.battle.core.GunState;
import com.createstats.battle.core.SalvoResult;
import com.createstats.battle.core.PackedPos;
import com.createstats.battle.core.PlaceName;
import com.createstats.battle.core.Projection;
import com.createstats.battle.core.ShipFrame;
import com.createstats.battle.net.BattleBridgeActionPacket;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IllegalFormatException;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BridgeConsoleLayoutTest {
    private static final Path LANG = Path.of("src/main/resources/assets/create_stats/lang/en_us.json");
    private static final Path BATTLE_LANG = Path.of("src/main/resources/assets/create_stats_battle/lang/en_us.json");
    private static final Path FONT = Path.of("assets_ascii.png");
    private static final Path OUT = Path.of("build/battle-console");

    private static final int[][] SCREENS = {{464, 280}, {544, 334}, {853, 533}};
    private static final long NOW = 240_000L;

    @Test
    void thePanelGrowsWithTheScreenAndStopsAtItsLimits() {
        assertEquals(BridgeConsole.MAX_W, BridgeConsole.width(853));
        assertEquals(BridgeConsole.MAX_H, BridgeConsole.height(533));
        assertEquals(BridgeConsole.MIN_W, BridgeConsole.width(300));
        assertTrue(BridgeConsole.width(544) > BridgeConsole.MIN_W && BridgeConsole.width(544) < BridgeConsole.MAX_W);
    }

    @Test
    void theConsolesActionIdsAreThePackets() {
        assertEquals(BattleBridgeActionPacket.WATCH, BridgeConsole.BattleBridgeAction.WATCH);
        assertEquals(BattleBridgeActionPacket.REQUEST_SHAPE, BridgeConsole.BattleBridgeAction.REQUEST_SHAPE);
        assertEquals(BattleBridgeActionPacket.REQUEST_DETAIL, BridgeConsole.BattleBridgeAction.REQUEST_DETAIL);
        assertEquals(BattleBridgeActionPacket.ACCEPT_SHAPE, BridgeConsole.BattleBridgeAction.ACCEPT_SHAPE);
        assertEquals(BattleBridgeActionPacket.ACCEPT_PLACE, BridgeConsole.BattleBridgeAction.ACCEPT_PLACE);
        assertEquals(BattleBridgeActionPacket.ACCEPT_REFIT, BridgeConsole.BattleBridgeAction.ACCEPT_REFIT);
        assertEquals(BattleBridgeActionPacket.TURN_BOW, BridgeConsole.BattleBridgeAction.TURN_BOW);
        assertEquals(BattleBridgeActionPacket.SET_GROUND_SIZE, BridgeConsole.BattleBridgeAction.SET_GROUND_SIZE);
        assertEquals(BattleBridgeActionPacket.SET_OPTION, BridgeConsole.BattleBridgeAction.SET_OPTION);
        assertEquals(BattleBridgeActionPacket.DESIGNATE, BridgeConsole.BattleBridgeAction.DESIGNATE);
        assertEquals(BattleBridgeActionPacket.CLEAR_TARGET, BridgeConsole.BattleBridgeAction.CLEAR_TARGET);
        assertEquals(BattleBridgeActionPacket.SET_MODE, BridgeConsole.BattleBridgeAction.SET_MODE);
        assertEquals(BattleBridgeActionPacket.SET_WEAPONS, BridgeConsole.BattleBridgeAction.SET_WEAPONS);
        assertEquals(BattleBridgeActionPacket.FIRE_READY, BridgeConsole.BattleBridgeAction.FIRE_READY);
        assertEquals(BattleBridgeActionPacket.RENAME_GUN, BridgeConsole.BattleBridgeAction.RENAME_GUN);
        assertEquals(BattleBridgeActionPacket.RELEARN_AIM, BridgeConsole.BattleBridgeAction.RELEARN_AIM);
        assertEquals(BattleBridgeActionPacket.SET_ARC, BridgeConsole.BattleBridgeAction.SET_ARC);
    }

    @Test
    void theShapeViewReadsBlockPositionsAsMinecraftPacksThem() {
        int[][] points = {{20_480_013, 71, -20_480_007}, {0, 0, 0}, {-1, -64, 1}, {33_000_000, 319, -33_000_000}};
        for (int[] p : points) {
            long mc = BlockPos.asLong(p[0], p[1], p[2]);
            assertEquals(p[0], ShapeView.BlockPosBits.x(mc));
            assertEquals(p[1], ShapeView.BlockPosBits.y(mc));
            assertEquals(p[2], ShapeView.BlockPosBits.z(mc));
            assertEquals(mc, ShapeView.BlockPosBits.of(p[0], p[1], p[2]));
        }
    }

    private record Scenario(String name, BattleReport report, ShapeView shape, DetailView detail, int[] mouse, WatchView watch,
                            boolean picker, int arcs) {
        Scenario(String name, BattleReport report, ShapeView shape, DetailView detail, int[] mouse) {
            this(name, report, shape, detail, mouse, WatchView.empty(report.key()), false, -1);
        }

        Scenario(String name, BattleReport report, ShapeView shape, DetailView detail, int[] mouse, WatchView watch,
                 boolean picker) {
            this(name, report, shape, detail, mouse, watch, picker, -1);
        }

        int mouseX(int[] screen) {
            return mouse == null ? -1 : screen[0] * mouse[0] / 100;
        }

        int mouseY(int[] screen) {
            return mouse == null ? -1 : screen[1] * mouse[1] / 100;
        }
    }

    private record MarkerScenario(String id, String name, int cover, int permille, boolean attached, String reportsTo) {
    }

    private static List<Scenario> scenarios() {
        List<Scenario> out = new ArrayList<>();
        out.add(new Scenario("healthy", healthy(), shipShape(), DetailView.empty("s:ship"), null));
        out.add(new Scenario("damaged", damaged(), shipShape(), shipDetail(), null));
        out.add(new Scenario("damaged_hover", damaged(), shipShape(), shipDetail(), new int[]{30, 40}));
        out.add(new Scenario("banner", banner(), shipShape(), shipDetail(), null));
        out.add(new Scenario("no_power", noPower(), shipShape(), shipDetail(), null));
        out.add(new Scenario("surveying", surveying(), null, null, null));
        out.add(new Scenario("unpowered_new", unpoweredNew(), null, null, null));
        out.add(new Scenario("ground", ground(), groundShape(), DetailView.empty("g:ground"), null));
        out.add(new Scenario("long_names", longNames(), shipShape(), shipDetail(), null));
        out.add(new Scenario("fire", fire(), shipShape(), shipDetail(), null));
        out.add(new Scenario("guns", withGuns(damaged(), false), shipShape(), shipDetail(), null, watch(4), false));
        out.add(new Scenario("guns_hover", withGuns(damaged(), false), shipShape(), shipDetail(), new int[]{70, 48}, watch(4), false));
        out.add(new Scenario("guns_salvo", withGuns(damaged(), true), shipShape(), shipDetail(), null, watch(4), false));
        out.add(new Scenario("guns_many", manyGuns(), shipShape(), shipDetail(), null, watch(16), false));
        out.add(new Scenario("guns_picker", withGuns(damaged(), false), shipShape(), shipDetail(), null, watch(4), true));
        out.add(new Scenario("guns_long", longGuns(), shipShape(), shipDetail(), null, watch(4), false));
        out.add(new Scenario("guns_ground", groundGuns(), groundShape(), DetailView.empty("g:ground"), null, watch(2), false));
        out.add(new Scenario("guns_rest", restGuns(), shipShape(), shipDetail(), null, watch(7), false));
        out.add(new Scenario("guns_bore", boreGuns(), shipShape(), shipDetail(), null, watch(14), false));
        out.add(new Scenario("guns_arcs", arcGuns(), shipShape(), shipDetail(), null, watch(5), false, 1));
        out.add(new Scenario("guns_arcs_aft", arcGuns(), shipShape(), shipDetail(), null, watch(5), false, 3));
        out.add(new Scenario("guns_arcs_free", withGuns(damaged(), false), shipShape(), shipDetail(), null, watch(4), false, 2));
        out.add(new Scenario("guns_arcs_fixed", arcGuns(), shipShape(), shipDetail(), null, watch(5), false, 5));
        out.add(new Scenario("guns_arcs_many", manyGuns(), shipShape(), shipDetail(), null, watch(16), false, 16));
        out.add(new Scenario("guns_arcs_ground", groundArcGuns(), groundShape(), DetailView.empty("g:ground"), null, watch(2), false, 2));
        out.add(new Scenario("jobs_done", withGuns(damaged(), false), shipShape(), shipDetail(), null, watch(4), false));
        out.add(new Scenario("skyhook", skyhook(), skyhookShape(), skyhookDetail(), null, watch(4), false));
        return out;
    }

    private static BattleReport copy(BattleReport b, boolean byRedstone, BattleReport.TargetLine t, List<BattleReport.GunLine> guns,
                                     int count, long salvoAt, String salvo) {
        return new BattleReport(b.key(), b.ground(), b.shipName(), b.powered(), b.master(), b.now(), b.surveyPermille(),
                b.surveyedAt(), b.refCount(), b.lostCount(), b.placeCount(), b.newBlocks(), b.banner(), b.capped(), b.bow(),
                b.bowSource(), b.groundSize(), b.options(), b.redstoneIn(), b.comparatorMode(), b.systems(), b.hits(),
                b.lastHitAt(), b.firePlace(), b.sections(), b.missing(), b.shapeVersion(), b.lostVersion(), b.face(),
                b.refitBy(), b.refitCount(), b.refitAt(), b.weaponsFree(), byRedstone, t, guns, count, salvoAt, salvo);
    }

    private static BattleReport.TargetLine kestrel() {
        return new BattleReport.TargetLine(1, "Kestrel", -1, "0f8e2a4c-1b2d-4e5f-8a9b-0c1d2e3f4a5b", 212f, 45f, 2f, 9.5f,
                200f, 11f, "Alex", NOW - 300, -1L);
    }

    private static BattleReport.GunLine gun(int id, String name, GunState st, int mode, int kind, int load, float train,
                                            float argA, float argB, String argS, int axis) {
        return new BattleReport.GunLine(id, name, st.ordinal(), mode, kind, load, train, 8f, axis, argA, argB, argS, GunArc.FREE,
                mount(id));
    }

    static BattleReport withGuns(BattleReport base, boolean salvo) {
        List<BattleReport.GunLine> guns = List.of(
                gun(1, "Big Bertha", GunState.READY, GunState.MODE_FIRE, 0, 1, 45f, 0, 0, "", 0),
                gun(2, "G2", GunState.SLEWING, GunState.MODE_TRACK, 1, 240, -30f, 3, 0, "", 0),
                gun(3, "G3", GunState.EMPTY, GunState.MODE_TRACK, 1, 0, 170f, 0, 0, "", 0),
                gun(4, "G4", GunState.NO_PITCH_DRIVE, GunState.MODE_FIRE, 0, 1, -90f, 0, 0, "", 1));
        String answer = salvo ? new SalvoResult.Builder().fired("Big Bertha").notReady("G2", GunState.SLEWING, 2)
                .notReady("G3", GunState.EMPTY, -1).notReady("G4", GunState.NO_PITCH_DRIVE, -1).build().encode() : "";
        return copy(base, false, kestrel(), guns, guns.size(), salvo ? NOW - 20 : -1L, answer);
    }

    static long mount(int id) {
        return switch (id) {
            case 1 -> ShapeView.BlockPosBits.of(34, 4, 0);
            case 2 -> ShapeView.BlockPosBits.of(22, 4, -4);
            case 3 -> ShapeView.BlockPosBits.of(22, 4, 4);
            case 4 -> ShapeView.BlockPosBits.of(8, 4, 0);
            default -> id >= 13 ? BattleReport.GunLine.NO_MOUNT : ShapeView.BlockPosBits.of(4 + id * 3, 4, id % 2 == 0 ? -2 : 2);
        };
    }

    static JobBoard primed(String scenario) {
        if (scenario.equals("skyhook")) {
            JobBoard b = new JobBoard();
            BattleReport sky = skyhook();
            List<BattleReport.GunLine> empty = new ArrayList<>(sky.guns());
            BattleReport.GunLine g2 = empty.get(1);
            empty.set(1, new BattleReport.GunLine(g2.id(), g2.name(), GunState.EMPTY.ordinal(), g2.mode(), g2.kind(), 0, g2.train(),
                    g2.elev(), 0, 0f, 0f, "", g2.arc(), g2.mount()));
            b.update(copy(sky, false, sky.target(), empty, empty.size(), -1L, ""), skyhookDetail(), NOW - 900);
            b.update(sky, skyhookDetail(), NOW - 820);
            return b;
        }
        if (!scenario.equals("jobs_done")) return null;
        JobBoard b = new JobBoard();
        BattleReport before = withGuns(fire(), false);
        List<DetailView.Place> places = new ArrayList<>(shipDetail().places());
        places.add(new DetailView.Place(9, PlaceName.ship(0, 1, 1), 0, 7, PackedPos.pack(10, 2, 3)));
        DetailView d = new DetailView("s:ship", 1, new int[0], new int[0], new byte[0], new int[]{PackedPos.pack(14, 2, 1)},
                places, NOW - 900);
        b.update(before, d, NOW - 900);
        return b;
    }

    private static Map<Long, Integer> skyhookBlocks() {
        Map<Long, Integer> k = new java.util.LinkedHashMap<>();
        java.util.function.BiConsumer<int[], Integer> put = (p, v) -> k.put(ShapeView.BlockPosBits.of(p[0], p[1], p[2] - 8), v);
        for (int x = 0; x < 64; x++) {
            for (int y = 0; y < 28; y++) {
                for (int z = 0; z < 17; z++) {
                    if (!skyEnv(x, y, z)) continue;
                    boolean edge = !skyEnv(x + 1, y, z) || !skyEnv(x - 1, y, z) || !skyEnv(x, y + 1, z) || !skyEnv(x, y - 1, z)
                            || !skyEnv(x, y, z + 1) || !skyEnv(x, y, z - 1);
                    if (edge) put.accept(new int[]{x, y, z}, 1);
                }
            }
        }
        for (int x = 15; x < 55; x++) {
            double hw = 4.0;
            if (x > 47) hw = Math.max(0.0, 4.0 - (x - 47) * 0.62);
            if (x < 20) hw = Math.max(0.6, 4.0 - (20 - x) * 0.75);
            int ybot = 1 + Math.max(0, (x - 49) / 2) + Math.max(0, (19 - x) / 2);
            int zl = (int) Math.round(8 - hw);
            int zr = (int) Math.round(8 + hw);
            for (int y = ybot; y < 7; y++) {
                for (int z = zl; z <= zr; z++) {
                    boolean wall = z == zl || z == zr || y == ybot || y == 6;
                    boolean bulkhead = (x == 24 || x == 40) && y < 6;
                    if (wall || bulkhead) put.accept(new int[]{x, y, z}, 0);
                }
            }
        }
        for (int x = 17; x < 22; x++) for (int y = 2; y < 5; y++) for (int z = 7; z < 10; z++) put.accept(new int[]{x, y, z}, 0);
        for (int x = 27; x < 33; x++) for (int z : new int[]{6, 10}) for (int y = 2; y < 4; y++) put.accept(new int[]{x, y, z}, 0);
        for (int x : new int[]{20, 28, 36, 44}) {
            for (int z : new int[]{5, 11}) {
                for (int y = 7; y < 28 && !skyEnv(x, y, z); y++) put.accept(new int[]{x, y, z}, 0);
            }
        }
        int[] reach = {4, 6, 6, 6, 6, 6, 6, 6, 5, 4, 3, 2, 1, 0};
        for (int x = 1; x < 15; x++) {
            for (int d = 0; d <= reach[x - 1]; d++) {
                for (int[] p : new int[][]{{x, 17 + d, 8}, {x, 17 - d, 8}, {x, 17, 8 - d}, {x, 17, 8 + d}}) {
                    if (p[2] >= 0 && p[2] < 17 && !skyEnv(p[0], p[1], p[2])) put.accept(p, 0);
                }
            }
        }
        for (int z = 1; z < 16; z++) put.accept(new int[]{16, 4, z}, 0);
        for (int z : new int[]{1, 15}) {
            put.accept(new int[]{13, 4, z}, 0);
            put.accept(new int[]{14, 4, z}, 0);
            put.accept(new int[]{15, 4, z}, 0);
        }
        for (int[] g : new int[][]{{50, 7, 8}, {37, 7, 4}, {37, 7, 12}, {22, 7, 8}, {44, 7, 10}}) put.accept(g, 0);
        return k;
    }

    private static boolean skyEnv(int x, int y, int z) {
        double kx = (x - 33.0) / 29.5;
        if (kx < 0) kx *= 1.06;
        return kx * kx + Math.pow((y - 17.0) / 7.6, 2) + Math.pow((z - 8.0) / 8.1, 2) <= 1.0;
    }

    private static int skyPack(int x, int y, int z) {
        return PackedPos.pack(x, y, z - 8);
    }

    static ShapeView skyhookShape() {
        List<int[]> blocks = new ArrayList<>();
        for (Map.Entry<Long, Integer> e : skyhookBlocks().entrySet()) {
            long p = e.getKey();
            blocks.add(new int[]{ShapeView.BlockPosBits.x(p), ShapeView.BlockPosBits.y(p), ShapeView.BlockPosBits.z(p), e.getValue()});
        }
        return shape("s:sky", false, 0, blocks, new int[]{1, skyPack(13, 4, 1), 2, skyPack(13, 4, 15)},
                new int[]{skyPack(44, 7, 10)});
    }

    static DetailView skyhookDetail() {
        Map<Long, Integer> blocks = skyhookBlocks();
        List<int[]> lost = new ArrayList<>();
        List<int[]> hull = new ArrayList<>();
        List<int[]> env = new ArrayList<>();
        for (Map.Entry<Long, Integer> e : blocks.entrySet()) {
            long p = e.getKey();
            int[] v = {ShapeView.BlockPosBits.x(p), ShapeView.BlockPosBits.y(p), ShapeView.BlockPosBits.z(p) + 8};
            if (e.getValue() == 1) env.add(v);
            else hull.add(v);
        }
        List<int[]> bow = hull.stream().filter(v -> v[0] >= 48 && v[0] <= 54 && v[2] <= 6 && v[1] <= 6)
                .sorted((a, b) -> a[0] == b[0] ? b[1] - a[1] : Math.abs(a[0] - 51) - Math.abs(b[0] - 51)).limit(20).toList();
        for (int[] v : bow) lost.add(new int[]{skyPack(v[0], v[1], v[2]), 80});
        lost.add(new int[]{skyPack(13, 4, 15), 80});
        List<int[]> mid = hull.stream().filter(v -> v[0] >= 28 && v[0] <= 37 && v[2] <= 6 && v[1] <= 3)
                .sorted((a, b) -> a[1] != b[1] ? a[1] - b[1] : Math.abs(a[0] - 32) - Math.abs(b[0] - 32)).limit(14).toList();
        for (int[] v : mid) lost.add(new int[]{skyPack(v[0], v[1], v[2]), 740});
        List<int[]> top = env.stream().filter(v -> v[0] >= 7 && v[0] <= 12 && v[1] >= 19 && v[2] >= 5 && v[2] <= 11)
                .sorted((a, b) -> a[1] != b[1] ? b[1] - a[1] : a[0] != b[0] ? a[0] - b[0] : a[2] - b[2]).limit(6).toList();
        for (int[] v : top) lost.add(new int[]{skyPack(v[0], v[1], v[2]), 1440});
        for (int[] v : new int[][]{{18, 4, 8}, {19, 4, 8}, {18, 4, 9}}) lost.add(new int[]{skyPack(v[0], v[1], v[2]), 2500});
        int[] lostArr = lost.stream().mapToInt(a -> a[0]).toArray();
        int[] ages = lost.stream().mapToInt(a -> a[1]).toArray();
        int[] burning = {skyPack(17, 4, 7), skyPack(19, 4, 9), skyPack(20, 4, 8), skyPack(17, 3, 9), skyPack(20, 3, 7)};
        List<DetailView.Place> places = List.of(
                new DetailView.Place(1, PlaceName.ship(2, 0, 1), 0, 20, skyPack(51, 4, 5)),
                new DetailView.Place(2, PlaceName.propeller(2), 0, 1, skyPack(13, 4, 15)),
                new DetailView.Place(3, PlaceName.ship(1, 0, 0), 0, 14, skyPack(32, 1, 4)),
                new DetailView.Place(4, PlaceName.envelope(0), 0, 6, skyPack(9, 23, 8)),
                new DetailView.Place(5, PlaceName.marker("Engine room"), 0, 3, skyPack(18, 4, 8)));
        return new DetailView("s:sky", 1, lostArr, ages, new byte[lostArr.length], burning, places, NOW);
    }

    static BattleReport skyhook() {
        List<BattleReport.HitLine> hits = List.of(
                new BattleReport.HitLine(NOW - 80, PlaceName.ship(2, 0, 1), 20, ""),
                new BattleReport.HitLine(NOW - 80, PlaceName.propeller(2), 1, "block.aeronautics.andesite_propeller"),
                new BattleReport.HitLine(NOW - 740, PlaceName.ship(1, 0, 0), 14, ""),
                new BattleReport.HitLine(NOW - 1440, PlaceName.envelope(0), 6, "block.aeronautics.white_envelope"),
                new BattleReport.HitLine(NOW - 2500, PlaceName.marker("Engine room"), 3, ""));
        int total = skyhookBlocks().size();
        List<BattleReport.SectionLine> sections = List.of(
                new BattleReport.SectionLine(ShapeView.BlockPosBits.of(19, 3, 0), "Engine room", 900, PlaceName.ship(0, 1, 0)),
                new BattleReport.SectionLine(ShapeView.BlockPosBits.of(30, 2, 0), "Magazine", 1000, PlaceName.ship(1, 1, 0)));
        BattleReport base = new BattleReport("s:sky", false, "Airship Skyhook", true, true, NOW, -1, NOW - 14 * 60 * 20, total, 44, 5,
                0, 0, false, 0, BattleReport.BOW_THRUST, 33, BattleReport.OPT_BELL | BattleReport.OPT_LIGHTS, 0,
                ComparatorScale.MODE_INTEGRITY, systems(new int[][]{{1141, 1175}, {1884, 1890}, {1, 2}, {66, 69}, {4, 4}, {0, 0}, {4, 4}}),
                hits, NOW - 80, PlaceName.marker("Engine room"), sections, List.of("minecraft:spruce_planks"), 1, 1, new byte[0], "",
                0, -1L, true, false, BattleReport.TargetLine.NONE, List.of(), 0, -1L, "");
        List<BattleReport.GunLine> guns = List.of(
                new BattleReport.GunLine(1, "Big Bertha", GunState.READY.ordinal(), GunState.MODE_FIRE, 0, 1, 45f, 8f, 0, 0f, 0f, "",
                        GunArc.FREE, ShapeView.BlockPosBits.of(50, 7, 0)),
                new BattleReport.GunLine(2, "G2", GunState.SLEWING.ordinal(), GunState.MODE_TRACK, 1, 240, 31f, 6f, 0, 2f, 0f, "",
                        GunArc.FREE, ShapeView.BlockPosBits.of(37, 7, -4)),
                new BattleReport.GunLine(3, "G3", GunState.EMPTY.ordinal(), GunState.MODE_TRACK, 1, 0, 45f, 7f, 0, 0f, 0f, "",
                        GunArc.FREE, ShapeView.BlockPosBits.of(37, 7, 4)),
                new BattleReport.GunLine(4, "G4", GunState.NO_PITCH_DRIVE.ordinal(), GunState.MODE_FIRE, 0, 1, 180f, 0f, 1, 0f, 0f, "",
                        GunArc.FREE, ShapeView.BlockPosBits.of(22, 7, 0)));
        return new BattleReport(base.key(), false, base.shipName(), true, true, NOW, -1, base.surveyedAt(), base.refCount(),
                base.lostCount(), base.placeCount(), base.newBlocks(), base.banner(), base.capped(), base.bow(), base.bowSource(),
                base.groundSize(), base.options(), base.redstoneIn(), base.comparatorMode(), base.systems(), base.hits(),
                base.lastHitAt(), base.firePlace(), base.sections(), base.missing(), base.shapeVersion(), base.lostVersion(),
                base.face(), base.refitBy(), base.refitCount(), base.refitAt(), true, false, kestrel(), guns, guns.size(), -1L, "");
    }

    static BattleReport manyGuns() {
        List<BattleReport.GunLine> guns = new ArrayList<>();
        GunState[] states = GunState.values();
        for (int i = 0; i < 16; i++) {
            GunState st = states[i % states.length];
            BattleReport.GunLine g = gun(i + 1, "G" + (i + 1), st, i % 3, i % 2, i % 2 == 0 ? 1 : 9999, i * 22.5f - 180f, 412, 300,
                    i % 2 == 0 ? PlaceName.envelope(1) : "p:Alex", i % 2);

            guns.add(i % 4 != 3 ? g : new BattleReport.GunLine(g.id(), g.name(), g.state(), g.mode(), g.kind(), g.load(), g.train(),
                    g.elev(), g.axis(), g.argA(), g.argB(), g.argS(), new GunArc(120, -120, -10, 30), g.mount()));
        }
        return copy(damaged(), true, kestrel(), guns, 20, -1L, "");
    }

    static BattleReport longGuns() {
        List<BattleReport.GunLine> guns = List.of(
                gun(1, "The Long Named Forward Gun", GunState.BLOCKED, GunState.MODE_FIRE, 0, 1, 10f, 0, 0, PlaceName.ship(2, 1, 1), 0),
                gun(2, "Starboard Autocannon Two", GunState.OUT_OF_ARC, GunState.MODE_TRACK, 1, 9999, 95f, -40, -30, "", GunState.AXIS_PITCH),
                gun(3, "Aft Mortar Battery Lead", GunState.HELD_ELSEWHERE, GunState.MODE_TRACK, 0, 0, 180f, 0, 0, "vcc", 0),
                gun(4, "G4", GunState.OFF_BORE, GunState.MODE_FIRE, 0, 1, 0f, 12, -3, "", 0));
        BattleReport.TargetLine t = new BattleReport.TargetLine(0, "#entity.minecraft.pillager", 55, "", 64f, -120f, -4f, -2f,
                10f, 3f, "A Very Long Player Name", NOW - 40, -1L);
        return copy(longNames(), false, t, guns, 4, -1L, "");
    }

    static BattleReport restGuns() {
        List<BattleReport.GunLine> guns = List.of(
                gun(1, "G1", GunState.EMPTY, GunState.MODE_TRACK, 1, 0, 0f, 0, 0, "", 0),
                gun(2, "G2", GunState.EMPTY, GunState.MODE_TRACK, 0, 0, 10f, 1, 0, "", 0),
                gun(3, "G3", GunState.EMPTY, GunState.MODE_TRACK, 0, 1, 20f, 2, 0, "", 0),
                gun(4, "G4", GunState.LOADING, GunState.MODE_TRACK, 0, 0, 30f, 0, 0, "", 0),
                gun(5, "G5", GunState.SAFE, GunState.MODE_FIRE, 0, 1, 40f, 0, 0, "", 0),
                gun(6, "G6", GunState.READY, GunState.MODE_TRACK, 1, 120, 50f, 0, 0, "", 0),
                gun(7, "G7", GunState.FIRING, GunState.MODE_FIRE, 1, 119, 60f, 0, 0, "", 0));
        return copy(damaged(), false, kestrel(), guns, guns.size(), -1L, "");
    }

    static BattleReport boreGuns() {
        List<BattleReport.GunLine> guns = new ArrayList<>();
        int[] empties = {GunState.EMPTY_NO_CHARGE, GunState.EMPTY_NO_SHELL, GunState.EMPTY_DEAD_CHARGE,
                GunState.EMPTY_STUCK_SHELL, GunState.EMPTY_DROP_MORTAR};
        for (int why : empties) {
            guns.add(gun(guns.size() + 1, "G" + (guns.size() + 1), GunState.EMPTY, GunState.MODE_FIRE, 0, 0, guns.size() * 20f,
                    why, 0, "", 0));
        }
        for (int why : new int[]{GunState.LOADING_BREECH, GunState.LOADING_SETTLE}) {
            guns.add(gun(guns.size() + 1, "G" + (guns.size() + 1), GunState.LOADING, GunState.MODE_FIRE, 0, 1, guns.size() * 20f,
                    why, 0, "", 0));
        }
        guns.add(gun(guns.size() + 1, "Big Bertha", GunState.UNSAFE, GunState.MODE_FIRE, 0, 1, 150f, GunState.UNSAFE_OVERLOAD,
                50, "3.5/2", 0));
        float[][] rest = {{GunState.UNSAFE_BARREL, 20}, {GunState.UNSAFE_ORDER, 100}, {GunState.UNSAFE_OBSTRUCTED, 100},
                {GunState.UNSAFE_SHELL, 100}, {GunState.UNSAFE_UNDERCHARGED, 0}, {GunState.UNSAFE_UNREADABLE, 0}};
        for (float[] why : rest) {
            guns.add(gun(guns.size() + 1, "G" + (guns.size() + 1), GunState.UNSAFE, GunState.MODE_TRACK, 0, 1,
                    guns.size() * 20f - 180f, why[0], why[1], "", 0));
        }
        return copy(damaged(), false, kestrel(), guns, guns.size(), -1L, "");
    }

    private static BattleReport.GunLine arcGun(int id, String name, GunState st, float train, GunArc arc, float argA, float argB,
                                               String argS, int axis) {
        return new BattleReport.GunLine(id, name, st.ordinal(), GunState.MODE_FIRE, 1, 120, train, 8f, axis, argA, argB, argS, arc,
                mount(id));
    }

    static BattleReport arcGuns() {
        List<BattleReport.GunLine> guns = List.of(
                arcGun(1, "Big Bertha", GunState.READY, 20f, new GunArc(-60, 60, -10, 30), 0, 0, "", 0),
                arcGun(2, "G2", GunState.OUT_OF_ARC, 60f, new GunArc(-60, 60, -90, 90), 150f, 0, "", GunState.AXIS_YAW),
                arcGun(3, "Aft Autocannon", GunState.OUT_OF_ARC, 175f, new GunArc(120, -120, -5, 45), 75f, 45f, "", GunState.AXIS_PITCH),
                arcGun(4, "G4", GunState.BLOCKED, -30f, GunArc.FREE, 0, 0, "?survey", 0),
                arcGun(5, "G5", GunState.BLOCKED, 10f, GunArc.FREE, 0, 0, "?far", 0));
        return copy(damaged(), false, kestrel(), guns, guns.size(), -1L, "");
    }

    static BattleReport groundArcGuns() {
        List<BattleReport.GunLine> guns = List.of(
                arcGun(1, "G1", GunState.IDLE, 0f, GunArc.FREE, 0, 0, "", 0),
                arcGun(2, "Harbour Battery", GunState.OUT_OF_ARC, 90f, new GunArc(45, 135, 0, 60), 200f, 0, "", GunState.AXIS_YAW));
        return copy(ground(), true, BattleReport.TargetLine.NONE, guns, 2, -1L, "");
    }

    static BattleReport groundGuns() {
        List<BattleReport.GunLine> guns = List.of(
                gun(1, "G1", GunState.IDLE, GunState.MODE_TRACK, 0, 1, 0f, 0, 0, "", 0),
                gun(2, "G2", GunState.OUT_OF_RANGE, GunState.MODE_FIRE, 1, 64, 90f, 412, 300, "", 0));
        return copy(ground(), true, BattleReport.TargetLine.NONE, guns, 2, -1L, "");
    }

    static WatchView watch(int guns) {
        List<WatchView.Contact> contacts = new ArrayList<>();
        contacts.add(new WatchView.Contact(WatchView.KIND_SHIP, -1, "0f8e2a4c-1b2d-4e5f-8a9b-0c1d2e3f4a5b", "Kestrel", 212f, 45f,
                2f, 9.5f, false, false, 6f, 0));
        contacts.add(new WatchView.Contact(WatchView.KIND_SHIP, -1, "1a2b", "Merchant Brig Albatross of the Southern Isles", 180f,
                -60f, 0f, 1.5f, false, false, 5f, 1));
        for (int i = 0; i < 20; i++) {
            contacts.add(new WatchView.Contact(WatchView.KIND_ENTITY, 100 + i, "", i % 2 == 0 ? "#entity.minecraft.zombie" : "Named Pet " + i,
                    20f + i * 7, i * 17f - 170f, 0f, 0f, i % 2 == 0, i % 4 == 0, 0.55f, i % 2 == 0 ? 2 : 5));
        }
        contacts.add(new WatchView.Contact(WatchView.KIND_PLAYER, 7, "", "Alex", 40f, 90f, 0f, 0f, false, false, 0.55f, 6));
        contacts.add(new WatchView.Contact(WatchView.KIND_PLAYER, 8, "", "Steve", 90f, -10f, 0f, 0f, false, false, 0.55f, 6));
        List<WatchView.Gun> details = new ArrayList<>();
        for (int i = 1; i <= guns; i++) {
            details.add(new WatchView.Gun(i, i % 5 == 0 ? 2 : i % 3 == 0 ? 1 : 0, i % 4 == 0 ? "" : PlaceName.ship(1, 0, 1),
                    i % 4 == 3 ? "!" : PlaceName.ship(1, 1, 1), i % 6 == 0 ? 1 : 0, 0, i % 3, 36f, 4.2f, 0.8f, 1.1f, 8f, 12, -3,
                    0.0375f, -0.0412f, 300f));
        }
        return new WatchView("s:ship", List.copyOf(contacts), List.copyOf(details), NOW);
    }

    static WatchView contactsOnly(List<WatchView.Contact> contacts) {
        return new WatchView("s:ship", contacts, List.of(), NOW);
    }

    private static List<MarkerScenario> markerScenarios() {
        return List.of(
                new MarkerScenario("attached", "Engine room", 4, 1000, true, "Airship Skyhook"),
                new MarkerScenario("alone", "", 8, -1, false, ""),
                new MarkerScenario("long", "Forward magazine port side", 1, 873, true, "The Very Long Named Dreadnought of the North"));
    }

    private static List<BattleReport.SystemLine> systems(int[][] v) {
        List<BattleReport.SystemLine> out = new ArrayList<>();
        for (int[] s : v) out.add(new BattleReport.SystemLine(s[0], s[1]));
        return out;
    }

    private static BattleReport report(String name, boolean ground, boolean powered, int survey, int ref, int lost, int places,
                                       List<BattleReport.SystemLine> systems, List<BattleReport.HitLine> hits, long lastHit,
                                       String fire, List<BattleReport.SectionLine> sections, int banner, int newBlocks) {
        return new BattleReport(ground ? "g:ground" : "s:ship", ground, name, powered, true, NOW, survey,
                survey >= 0 ? -1L : NOW - 14 * 60 * 20, ref, lost, places, newBlocks, banner, false, 0, BattleReport.BOW_THRUST,
                33, BattleReport.OPT_BELL | BattleReport.OPT_LIGHTS, 0, ComparatorScale.MODE_INTEGRITY, systems, hits, lastHit,
                fire, sections, List.of("minecraft:spruce_planks"), 1, 1, new byte[0], "Builder", 12, NOW - 3 * 60 * 20, false,
                false, BattleReport.TargetLine.NONE, List.of(), 0, -1L, "");
    }

    private static List<BattleReport.SectionLine> sections() {
        return List.of(new BattleReport.SectionLine(ShapeView.BlockPosBits.of(4, 1, 0), "Engine room", 1000, PlaceName.ship(0, 1, 1)),
                new BattleReport.SectionLine(ShapeView.BlockPosBits.of(18, 1, 2), "Magazine", 1000, PlaceName.ship(1, 1, 0)));
    }

    static BattleReport healthy() {
        return report("Airship Skyhook", false, true, -1, 1841, 0, 0,
                systems(new int[][]{{1533, 1533}, {206, 206}, {2, 2}, {30, 30}, {0, 0}, {12, 12}, {20, 20}}),
                List.of(), -1L, "", sections(), 0, 0);
    }

    static BattleReport damaged() {
        List<BattleReport.HitLine> hits = List.of(
                new BattleReport.HitLine(NOW - 80, PlaceName.ship(2, 0, 1), 20, ""),
                new BattleReport.HitLine(NOW - 80, PlaceName.propeller(2), 1, "block.aeronautics.andesite_propeller"),
                new BattleReport.HitLine(NOW - 740, PlaceName.ship(1, 0, 0), 14, ""),
                new BattleReport.HitLine(NOW - 1440, PlaceName.envelope(0), 6, "block.aeronautics.white_envelope"),
                new BattleReport.HitLine(NOW - 2500, PlaceName.marker("Engine room"), 3, ""));
        return report("Airship Skyhook", false, true, -1, 1841, 42, 5,
                systems(new int[][]{{1500, 1533}, {200, 206}, {1, 2}, {30, 30}, {0, 0}, {12, 12}, {19, 20}}),
                hits, NOW - 80, "", sections(), 0, 213);
    }

    static BattleReport banner() {
        return report("Airship Skyhook", false, true, -1, 1841, 43, 3,
                systems(new int[][]{{1490, 1533}, {206, 206}, {2, 2}, {30, 30}, {0, 0}, {12, 12}, {20, 20}}),
                List.of(new BattleReport.HitLine(NOW - 3000, PlaceName.ship(1, 2, 1), 43, "")), NOW - 3000, "", sections(), 43, 0);
    }

    static BattleReport noPower() {
        return report("Airship Skyhook", false, false, -1, 1841, 42, 5,
                systems(new int[][]{{1500, 1533}, {200, 206}, {1, 2}, {30, 30}, {0, 0}, {12, 12}, {19, 20}}),
                List.of(), -1L, "", sections(), 0, 0);
    }

    static BattleReport surveying() {
        return report("Airship Skyhook", false, true, 612, 0, 0, 0,
                systems(new int[][]{{0, 0}, {0, 0}, {0, 0}, {0, 0}, {0, 0}, {0, 0}, {0, 0}}), List.of(), -1L, "", List.of(), 0, -1);
    }

    static BattleReport unpoweredNew() {
        return report("", false, false, 0, 0, 0, 0,
                systems(new int[][]{{0, 0}, {0, 0}, {0, 0}, {0, 0}, {0, 0}, {0, 0}, {0, 0}}), List.of(), -1L, "", List.of(), 0, -1);
    }

    static BattleReport ground() {
        return report("", true, true, -1, 3900, 12, 1,
                systems(new int[][]{{3800, 3812}, {0, 0}, {0, 0}, {40, 40}, {0, 0}, {8, 8}, {40, 40}}),
                List.of(new BattleReport.HitLine(NOW - 300, PlaceName.compass(0, 2, 1), 12, "")), NOW - 300, "", List.of(), 0, 0);
    }

    static BattleReport longNames() {
        List<BattleReport.SectionLine> many = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            many.add(new BattleReport.SectionLine(ShapeView.BlockPosBits.of(i * 3, 1, 0), "Section with a long name " + i,
                    i % 3 == 0 ? 870 : 1000, PlaceName.ship(i % 3, i % 3, i % 3)));
        }
        List<BattleReport.HitLine> hits = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            hits.add(new BattleReport.HitLine(NOW - 20L * i, PlaceName.marker("Section with a long name " + i), 1200 + i,
                    "block.create.very_long_modded_structural_block_name"));
        }
        return report("The Very Long Named Dreadnought of the Northern Fleet", false, true, -1, 65_536, 12_345, 32,
                systems(new int[][]{{40_000, 50_000}, {9_000, 10_000}, {7, 12}, {900, 1000}, {3, 12}, {100, 120}, {2000, 2416}}),
                hits, NOW - 20, "", many, 0, 99_999);
    }

    static BattleReport fire() {
        return report("Airship Skyhook", false, true, -1, 1841, 7, 1,
                systems(new int[][]{{1526, 1533}, {206, 206}, {2, 2}, {30, 30}, {0, 0}, {12, 12}, {20, 20}}),
                List.of(new BattleReport.HitLine(NOW - 30, PlaceName.ship(1, 1, 0), 7, "")), NOW - 30,
                PlaceName.ship(1, 1, 0), sections(), 0, 0);
    }

    static ShapeView shipShape() {
        List<int[]> blocks = new ArrayList<>();
        for (int x = 0; x < 40; x++) {
            double t = (x - 19.5) / 20.0;
            int half = (int) Math.round(4.5 * Math.sqrt(Math.max(0, 1 - t * t * t * t)));
            for (int z = -half; z <= half; z++) {
                for (int y = 0; y <= 3; y++) blocks.add(new int[]{x, y, z, 0});
            }
            int ehalf = (int) Math.round(6.5 * Math.sqrt(Math.max(0, 1 - t * t)));
            for (int z = -ehalf; z <= ehalf; z++) {
                for (int y = 6; y <= 12; y++) {
                    if (x > 3 && x < 37) blocks.add(new int[]{x, y, z, 1});
                }
            }
            if (x % 8 == 0) for (int y = 4; y <= 5; y++) blocks.add(new int[]{x, y, 0, 0});
        }
        return shape("s:ship", false, 0, blocks, new int[]{1, PackedPos.pack(0, 1, -3), 2, PackedPos.pack(0, 1, 3)},
                new int[]{PackedPos.pack(30, 1, 0)});
    }

    static ShapeView groundShape() {
        List<int[]> blocks = new ArrayList<>();
        for (int x = -16; x <= 16; x++) {
            for (int z = -16; z <= 16; z++) {
                boolean wall = Math.abs(x) >= 14 || Math.abs(z) >= 14;
                if (!wall) continue;
                for (int y = 0; y <= 5; y++) blocks.add(new int[]{x, y, z, 0});
            }
        }
        return shape("g:ground", true, 0, blocks, new int[0], new int[]{PackedPos.pack(0, 0, 0)});
    }

    private static ShapeView shape(String key, boolean ground, int bow, List<int[]> blocks, int[] props, int[] bridges) {
        ShipFrame f = ground ? ShipFrame.ground() : new ShipFrame(bow);
        int minF = Integer.MAX_VALUE, maxF = Integer.MIN_VALUE, minS = Integer.MAX_VALUE, maxS = Integer.MIN_VALUE;
        int minU = Integer.MAX_VALUE, maxU = Integer.MIN_VALUE;
        for (int[] b : blocks) {
            int fore = f.fore(b[0], b[2]);
            int stbd = f.stbd(b[0], b[2]);
            minF = Math.min(minF, fore);
            maxF = Math.max(maxF, fore);
            minS = Math.min(minS, stbd);
            maxS = Math.max(maxS, stbd);
            minU = Math.min(minU, b[1]);
            maxU = Math.max(maxU, b[1]);
        }
        Projection.Frame frame = Projection.frame(minF, maxF, minS, maxS, minU, maxU, Projection.MAX_W, Projection.MAX_H);
        byte[] plan = new byte[frame.planW() * frame.planH()];
        byte[] profile = new byte[frame.profileW() * frame.profileH()];
        for (int[] b : blocks) {
            byte s = b[3] == 1 ? Projection.LIFT : Projection.STRUCTURE;
            int x = frame.x(f.fore(b[0], b[2]));
            Projection.put(plan, frame.planW(), x, frame.planY(f.stbd(b[0], b[2])), s);
            Projection.put(profile, frame.profileW(), x, frame.profileY(b[1]), s);
        }
        return new ShapeView(key, 1, ground, bow, ShapeView.BlockPosBits.of(0, 0, 0), frame, plan, profile, props, bridges);
    }

    static DetailView shipDetail() {
        List<Integer> lost = new ArrayList<>();
        List<Integer> ages = new ArrayList<>();
        for (int x = 27; x <= 30; x++) {
            for (int z = -4; z <= -2; z++) {
                lost.add(PackedPos.pack(x, 2, z));
                ages.add(80);
            }
        }
        for (int x = 14; x <= 16; x++) {
            lost.add(PackedPos.pack(x, 1, 3));
            ages.add(740);
        }
        lost.add(PackedPos.pack(0, 1, 3));
        ages.add(80);
        for (int x = 5; x <= 7; x++) {
            lost.add(PackedPos.pack(x, 8, -6));
            ages.add(1440);
        }
        int[] lostArr = lost.stream().mapToInt(Integer::intValue).toArray();
        int[] ageArr = ages.stream().mapToInt(Integer::intValue).toArray();
        List<DetailView.Place> places = List.of(
                new DetailView.Place(1, PlaceName.ship(2, 0, 1), 0, 12, PackedPos.pack(27, 2, -4)),
                new DetailView.Place(2, PlaceName.propeller(2), 0, 1, PackedPos.pack(0, 1, 3)),
                new DetailView.Place(3, PlaceName.ship(1, 2, 1), 0, 3, PackedPos.pack(14, 1, 3)),
                new DetailView.Place(4, PlaceName.envelope(0), 0, 3, PackedPos.pack(5, 8, -6)));
        return new DetailView("s:ship", 1, lostArr, ageArr, new byte[lostArr.length], new int[0], places, NOW);
    }

    static final class TestHost implements ConsoleHost {
        private static final String GRID = "\u0000\u0001\u0002\u0003\u0004\u0005\u0006\u0007\b\t\n\u000b\f\r\u000e\u000f"
                + "\u0010\u0011\u0012\u0013\u0014\u0015\u0016\u0017\u0018\u0019\u001a\u001b\u001c\u001d\u001e\u001f"
                + " !\"#$%&'()*+,-./0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWXYZ[\\]^_`abcdefghijklmnopqrstuvwxyz{|}~\u007f";

        private final Map<Character, Integer> advance = new HashMap<>();
        private final Map<String, String> lang = new HashMap<>();
        final List<Integer> sent = new ArrayList<>();

        final List<int[]> sentArgs = new ArrayList<>();

        TestHost() throws IOException {
            BufferedImage sheet = ImageIO.read(FONT.toFile());
            int cell = sheet.getWidth() / 16;
            for (int i = 0; i < GRID.length(); i++) {
                int cx = (i % 16) * cell;
                int cy = (i / 16) * cell;
                int right = -1;
                for (int x = 0; x < cell; x++) {
                    for (int y = 0; y < cell; y++) {
                        if ((sheet.getRGB(cx + x, cy + y) >>> 24) != 0) {
                            right = Math.max(right, x);
                            break;
                        }
                    }
                }
                advance.put(GRID.charAt(i), right < 0 ? 0 : right + 2);
            }
            advance.put(' ', 4);
            for (Path p : new Path[]{LANG, BATTLE_LANG}) {
                JsonObject json = JsonParser.parseString(Files.readString(p, StandardCharsets.UTF_8)).getAsJsonObject();
                for (Map.Entry<String, JsonElement> e : json.entrySet()) lang.put(e.getKey(), e.getValue().getAsString());
            }
        }

        @Override
        public int width(String text) {
            int w = 0;
            for (int i = 0; i < text.length(); i++) w += advance.getOrDefault(text.charAt(i), 6);
            return w;
        }

        @Override
        public String tr(String key, Object... args) {
            String pattern = lang.get(key);
            if (pattern == null) throw new AssertionError("missing lang key " + key);
            try {
                return String.format(Locale.ROOT, pattern, args);
            } catch (IllegalFormatException e) {
                throw new AssertionError("bad format in " + key + ": " + pattern, e);
            }
        }

        @Override
        public String name(String idOrName) {
            if (idOrName == null || idOrName.isEmpty()) return "";
            if (!idOrName.startsWith("block.") && !idOrName.startsWith("item.")) return idOrName;
            String last = idOrName.substring(idOrName.lastIndexOf('.') + 1);
            StringBuilder out = new StringBuilder();
            for (String word : last.split("_")) {
                if (word.isEmpty()) continue;
                if (!out.isEmpty()) out.append(' ');
                out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
            }
            return out.toString();
        }

        @Override
        public void send(int action, int a, int b, String s) {
            sent.add(action);
            sentArgs.add(new int[]{action, a, b});
        }

        @Override
        public void click() {
        }
    }

    static final class Recorder implements ConsoleCanvas {
        private record Op(char kind, int layer, int[] box, String text, int colour, int scale, boolean bold) {
        }

        private final TestHost host;
        private final List<Op> ops = new ArrayList<>();
        private int layer;

        Recorder(TestHost host) {
            this.host = host;
        }

        @Override
        public void fill(int x0, int y0, int x1, int y1, int argb) {
            ops.add(new Op('f', layer, new int[]{x0, y0, x1, y1}, null, argb, 1, false));
        }

        @Override
        public void text(String text, int x, int y, int argb) {
            if (text == null || text.isEmpty()) return;
            ops.add(new Op('t', layer, new int[]{x, y, x + host.width(text) - 1, y + 8}, text, argb, 1, false));
        }

        @Override
        public void text(String text, int x, int y, int argb, int scale, boolean bold) {
            if (text == null || text.isEmpty()) return;
            int wide = (host.width(text) + (bold ? text.length() : 0)) * scale;
            ops.add(new Op('t', layer, new int[]{x, y, x + wide - 1, y + 8 * scale}, text, argb, scale, bold));
        }

        @Override
        public void layer() {
            layer++;
        }

        int[] boxOf(String text) {
            for (Op o : ops) {
                if (o.kind() == 't' && o.text().equals(text)) return o.box();
            }
            throw new AssertionError("no text \"" + text + "\" drawn");
        }

        int count(String text) {
            return (int) ops.stream().filter(o -> o.kind() == 't' && o.text().equals(text)).count();
        }

        List<String> faults(String id, int left, int top, int w, int h) {
            List<String> out = new ArrayList<>();
            List<Op> texts = ops.stream().filter(o -> o.kind() == 't').toList();
            for (Op t : texts) {
                int[] b = t.box();
                if (b[0] < left + 2 || b[1] < top + 2 || b[2] > left + w - 2 || b[3] > top + h - 1) {
                    out.add(id + ": \"" + t.text() + "\" at " + b[0] + "," + b[1] + " leaves the panel");
                }
            }
            for (int i = 0; i < texts.size(); i++) {
                for (int j = i + 1; j < texts.size(); j++) {
                    Op a = texts.get(i);
                    Op b = texts.get(j);
                    if (a.layer() != b.layer()) continue;
                    int[] p = a.box();
                    int[] q = b.box();
                    if (p[0] < q[2] && q[0] < p[2] && p[1] < q[3] && q[1] < p[3]) {
                        out.add(id + ": \"" + a.text() + "\" at " + p[0] + "," + p[1] + " overlaps \"" + b.text() + "\" at " + q[0] + "," + q[1]);
                    }
                }
            }
            return out;
        }

        String json(int[] screen, int left, int top, int w, int h, List<String> tooltip, int mx, int my) {
            StringBuilder sb = new StringBuilder();
            sb.append("{\"screen\":[").append(screen[0]).append(',').append(screen[1]).append("],");
            sb.append("\"panel\":[").append(left).append(',').append(top).append(',').append(w).append(',').append(h).append("],");
            sb.append("\"mouse\":[").append(mx).append(',').append(my).append("],\"tooltip\":[");
            for (int i = 0; i < tooltip.size(); i++) sb.append(i == 0 ? "" : ",").append(quote(tooltip.get(i)));
            sb.append("],\"ops\":[");
            for (int i = 0; i < ops.size(); i++) {
                Op o = ops.get(i);
                sb.append(i == 0 ? "" : ",\n");
                String colour = String.format(Locale.ROOT, "\"#%08X\"", o.colour());
                if (o.kind() == 'f') {
                    sb.append("[\"f\",").append(o.box()[0]).append(',').append(o.box()[1]).append(',').append(o.box()[2]).append(',')
                            .append(o.box()[3]).append(',').append(colour).append(',').append(o.layer()).append(']');
                } else {
                    sb.append("[\"t\",").append(quote(o.text())).append(',').append(o.box()[0]).append(',').append(o.box()[1])
                            .append(',').append(colour).append(',').append(o.layer()).append(',').append(o.scale()).append(',')
                            .append(o.bold()).append(']');
                }
            }
            sb.append("]}");
            return sb.toString();
        }

        private static String quote(String s) {
            return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
        }
    }
}
