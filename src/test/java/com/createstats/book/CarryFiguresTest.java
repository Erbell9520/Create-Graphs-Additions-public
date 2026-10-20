package com.createstats.book;

import com.createstats.blockentity.DiagnosticJunctionBE;
import com.createstats.book.scene.BlockFigure;
import com.createstats.book.scene.BlockFigures;
import com.createstats.book.scene.FigureWorld;
import com.createstats.book.specimen.CarryFixtures;
import com.createstats.book.specimen.StoreFixtures;
import com.createstats.power.CableLinks;
import com.createstats.power.CableTier;
import com.createstats.power.CordPlug;
import com.createstats.power.CordRoute;
import com.createstats.power.FeRates;
import com.createstats.power.IndoorCableLinks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CarryFiguresTest {
    private static final Path CAPABILITIES = Path.of("src/main/java/com/createstats/power/FeCapabilities.java");

    enum Rule { EVERY_FACE, BACK, SIDES }

    record Registration(String type, Rule rule, String lambda) {
    }

    static final Map<String, Registration> REGISTRATIONS = Map.ofEntries(
            Map.entry("create_stats:kinetic_dynamo", new Registration("KINETIC_DYNAMO", Rule.EVERY_FACE,
                    "(be, side) -> be.energyHandle()")),
            Map.entry("create_stats:cable_diagnostic_junction", new Registration("DIAGNOSTIC_JUNCTION", Rule.EVERY_FACE,
                    "(be, side) -> be.energyHandle()")),
            Map.entry("create_stats:power_outlet", new Registration("WALL_OUTLET", Rule.EVERY_FACE,
                    "(be, side) -> be.feConsumer()")),
            Map.entry("create_stats:duplex_power_outlet", new Registration("WALL_OUTLET", Rule.EVERY_FACE,
                    "(be, side) -> be.feConsumer()")),
            Map.entry("create_stats:induction_smelter", new Registration("INDUCTION_SMELTER", Rule.BACK,
                    "(be, side) -> side == null || side == be.backFace() ? be.energyHandle() : null")),
            Map.entry("create_stats:water_cooler", new Registration("WATER_COOLER", Rule.BACK,
                    "(be, side) -> side == null || side == be.backFace() ? be.energyHandle() : null")),
            Map.entry("create_stats:toaster", new Registration("TOASTER", Rule.SIDES,
                    "(be, side) -> side == null || side.getAxis().isHorizontal() ? be.energyHandle() : null")),
            Map.entry("create_stats:bread_slicer", new Registration("BREAD_SLICER", Rule.SIDES,
                    "(be, side) -> side == null || side.getAxis().isHorizontal() ? be.energyHandle() : null")),

            Map.entry("create_stats:smart_home_panel", new Registration("SMART_HOME_PANEL", Rule.EVERY_FACE,
                    "(be, side) -> be.energyHandle()")),
            Map.entry("create_stats:fe_motor_mk1", new Registration("FE_MOTOR", Rule.EVERY_FACE,
                    "(be, side) -> be.energyHandle()")),
            Map.entry("create_stats:arc_furnace", new Registration("ARC_FURNACE", Rule.EVERY_FACE,
                    "(be, side) -> be.energyHandle()")),
            Map.entry("create_stats:pendant_lamp", new Registration("LAMP", Rule.EVERY_FACE,
                    "(be, side) -> be.energyHandle()")),
            Map.entry("create_stats:stage_light", new Registration("STAGE_LIGHT", Rule.EVERY_FACE,
                    "(be, side) -> be.feConsumer()")),
            Map.entry("create_stats:rocker_switch", new Registration("SWITCH", Rule.EVERY_FACE,
                    "(be, side) -> be.energyHandle()")));

    private static final Pattern COMMENTS = Pattern.compile("(?s)/\\*.*?\\*/|//[^\\n]*");

    static String capabilities(Path file) throws IOException {
        final String code = Files.readString(file, StandardCharsets.UTF_8).replace("\r\n", "\n");
        return COMMENTS.matcher(code).replaceAll("").replaceAll("\\s+", " ").replace("( ", "(").replace(" )", ")");
    }

    static List<String> registrations(String code, String type) {
        final List<String> out = new ArrayList<>();
        final Matcher m = Pattern.compile("registerBlockEntity\\(Capabilities\\.EnergyStorage\\.BLOCK, ?"
                + "ModBlockEntities\\." + type + "\\.get\\(\\),").matcher(code);
        while (m.find()) {
            final int end = code.indexOf(';', m.end());
            out.add(code.substring(m.start(), end < 0 ? code.length() : end + 1));
        }
        return out;
    }

    @Test
    void theDeviceTableIsFeCapabilities() throws IOException {
        assertEquals(REGISTRATIONS.keySet(), FigureWorld.DEVICES.keySet(), "a device with no registration to hold it to");
        final String code = capabilities(CAPABILITIES);
        final List<String> bad = new ArrayList<>();
        for (final Map.Entry<String, Registration> e : REGISTRATIONS.entrySet()) {
            final List<String> found = registrations(code, e.getValue().type());
            if (found.size() != 1) {
                bad.add(e.getKey() + ": " + found.size() + " energy registrations of " + e.getValue().type());
                continue;
            }
            if (!found.get(0).contains(e.getValue().lambda())) {
                bad.add(e.getKey() + ": " + e.getValue().type() + " no longer reads " + e.getValue().lambda());
            }
            for (final String facing : new String[]{"north", "east", "south", "west"}) {
                final Map<String, String> props = Map.of("facing", facing, "half", "lower");
                final Direction back = Direction.byName(facing).getOpposite();
                for (final Direction side : Direction.values()) {
                    final boolean want = switch (e.getValue().rule()) {
                        case EVERY_FACE -> true;
                        case BACK -> side == back;
                        case SIDES -> side.getAxis().isHorizontal();
                    };
                    if (FigureWorld.DEVICES.get(e.getKey()).test(props, side) != want) {
                        bad.add(e.getKey() + " facing " + facing + ": the table answers " + !want + " on " + side);
                    }
                }
            }
        }

        if (FigureWorld.DEVICES.get("create_stats:water_cooler").test(Map.of("facing", "east", "half", "upper"),
                Direction.WEST)) {
            bad.add("the Water Cooler's upper half takes power in the table");
        }
        assertTrue(bad.isEmpty(), String.join("\n", bad));
    }

    private static int indexOf(BlockFigure figure, int x, int y, int z) {
        final int i = FigureWorld.of(figure).indexAt(new BlockPos(x, y, z));
        assertTrue(i >= 0, figure.id() + " has nothing at " + x + " " + y + " " + z);
        return i;
    }

    @Test
    void theRunJoinsAsTheGameJoinsIt() {
        final FigureWorld w = FigureWorld.of(BlockFigures.CABLE_RUN);
        final Direction floor = Direction.DOWN;
        assertEquals(CableLinks.Link.DEVICE, w.link(new BlockPos(1, 0, 1), floor, Direction.WEST), "into the dynamo");
        assertEquals(CableLinks.Link.FLAT, w.link(new BlockPos(1, 0, 1), floor, Direction.EAST));
        assertEquals(CableLinks.Link.FLAT, w.link(new BlockPos(2, 0, 1), floor, Direction.EAST), "duct to surface");
        assertTrue(w.otherGrade(new BlockPos(2, 0, 1), floor, Direction.EAST, CableLinks.Link.FLAT), "a reducer");
        assertTrue(w.otherGrade(new BlockPos(3, 0, 1), floor, Direction.WEST, CableLinks.Link.FLAT), "both sides");
        assertEquals(CableLinks.Link.FLAT, w.link(new BlockPos(3, 0, 1), floor, Direction.EAST));
        assertEquals(CableLinks.Link.DEVICE, w.link(new BlockPos(4, 0, 1), floor, Direction.NORTH),
                "into the Smelter's back");
        assertEquals(CableLinks.Link.NONE, w.link(new BlockPos(4, 0, 1), floor, Direction.EAST), "a corner, not a tee");
        assertEquals(CableLinks.Link.NONE, w.link(new BlockPos(4, 0, 1), floor, Direction.SOUTH));

        assertTrue(w.takesPower(new BlockPos(4, 0, 0), Direction.SOUTH));
        assertFalse(w.takesPower(new BlockPos(4, 0, 0), Direction.EAST));

        for (int x = 1; x <= 4; x++) {
            assertEquals(w.dyeAt(new BlockPos(1, 0, 1)), w.dyeAt(new BlockPos(x, 0, 1)));
        }

        final FigureWorld g = FigureWorld.of(BlockFigures.CABLE_GRADES);
        final CableTier[] grades = {CableTier.TRUNK, CableTier.DUCT, CableTier.TRAY, CableTier.SURFACE};
        for (int z = 0; z < grades.length; z++) {
            for (int x = 0; x < 4; x++) {
                final BlockPos at = new BlockPos(x, 0, z);
                assertEquals(grades[z], g.cableTier(at));
                assertEquals(g.dyeAt(new BlockPos(0, 0, z)), g.dyeAt(at), "one dye along a run");
                for (final Direction across : new Direction[]{Direction.NORTH, Direction.SOUTH}) {
                    assertEquals(CableLinks.Link.NONE, g.link(at, floor, across), "runs " + z + " join at " + x);
                }
            }
        }
    }

    @Test
    void theIndoorRunGoesThroughTheWall() {
        final FigureWorld w = FigureWorld.of(BlockFigures.INDOOR_RUN);
        assertEquals(IndoorCableLinks.Link.BORE, w.indoorLink(new BlockPos(2, 1, 0), Direction.WEST), "the gland");
        assertEquals(IndoorCableLinks.Link.BORE, w.indoorLink(new BlockPos(0, 1, 0), Direction.EAST), "its twin");
        assertEquals(IndoorCableLinks.Link.CABLE, w.indoorLink(new BlockPos(2, 1, 0), Direction.DOWN));
        assertEquals(IndoorCableLinks.Link.CABLE, w.indoorLink(new BlockPos(2, 0, 0), Direction.EAST));
        assertEquals(IndoorCableLinks.Link.CABLE, w.indoorLink(new BlockPos(3, 0, 0), Direction.WEST));
        assertEquals(IndoorCableLinks.Link.DEVICE, w.indoorLink(new BlockPos(3, 0, 0), Direction.EAST), "the collar");
        assertTrue(w.takesPower(new BlockPos(4, 0, 0), Direction.WEST), "the cooler's back meets the cord");

        final BlockFigure.Tie bore = BlockFigures.INDOOR_RUN.ties().get(0);
        assertEquals(BlockFigure.Tie.Kind.BORE, bore.kind());
        for (final BlockPos at : com.createstats.power.BoreLinks.crossings(new BlockPos(bore.from()[0], bore.from()[1],
                bore.from()[2]), new BlockPos(bore.to()[0], bore.to()[1], bore.to()[2]))) {
            assertNotNull(w.at(at), "the bore passes through the wall");
            assertEquals("minecraft:smooth_stone", w.at(at).block());
        }

        assertEquals("west", w.anchorAt(new BlockPos(2, 0, 0)).getSerializedName());
        assertEquals("free", w.anchorAt(new BlockPos(3, 0, 0)).getSerializedName());
    }

    @Test
    void theOutletIsFedAndItsCordLandsInView() {
        final BlockFigure f = BlockFigures.OUTLET;
        final FigureWorld w = FigureWorld.of(f);
        assertEquals(CableLinks.Link.DEVICE, w.link(new BlockPos(1, 1, 0), Direction.NORTH, Direction.WEST),
                "the feed lands in the plate");

        assertEquals(CableLinks.Link.CONVEX, w.link(new BlockPos(1, 1, 0), Direction.NORTH, Direction.EAST));
        assertEquals(CableLinks.Link.CONVEX, w.link(new BlockPos(2, 1, -1), Direction.WEST, Direction.SOUTH));
        assertEquals(CableLinks.Link.CONVEX, w.link(new BlockPos(2, 1, -1), Direction.WEST, Direction.NORTH));
        assertEquals(CableLinks.Link.CONVEX, w.link(new BlockPos(1, 1, -2), Direction.SOUTH, Direction.EAST));
        final int socket = indexOf(f, 0, 1, 0);
        final CarryFixtures.Cord cord = CarryFixtures.cord(f, socket);
        assertNotNull(cord, "the cord is laid");
        assertEquals(new BlockPos(-2, 0, 0), cord.machine());
        assertEquals(Direction.EAST, cord.face(), "the face the reader sees");
        assertTrue(cord.route().size() >= 2);
        final Vec3 first = cord.route().get(0);
        final Vec3 last = cord.route().get(cord.route().size() - 1);
        assertTrue(first.distanceTo(last) <= CordRoute.MAX_REACH, "within reach");
        assertEquals(0.0, last.distanceTo(CordPlug.terminal(cord.machine(), cord.face())), 1.0e-6, "on the face");
        final CordRoute.Solid solid = w.solid(new BlockPos(0, 1, 0), cord.machine());
        for (final Vec3 v : cord.route()) {
            assertNull(solid.at(v.x, v.y, v.z), "the cord passes through a block at " + v);
        }
    }

    @Test
    void theJunctionsReadTheirExamples() {
        final CarryFixtures.JunctionReading c = CarryFixtures.cableBox();
        assertEquals(StoreFixtures.dynamoFlow(), c.flow(), "one Kinetic Dynamo at top speed");
        assertEquals(FeRates.cable(CableTier.SURFACE), c.capacity(), "the heaviest cable on it, Surface");
        assertEquals(DiagnosticJunctionBE.Fault.OK, c.fault());
        assertFalse(c.wire());
        final CarryFixtures.JunctionReading s = CarryFixtures.wireBox();
        assertTrue(s.wire());
        assertEquals(DiagnosticJunctionBE.Fault.NO_BUS, s.fault(), "nothing on its studs");
        assertFalse(s.merged(), "separate, as it comes");
        assertFalse(s.cutOff());
        for (final DiagnosticJunctionBE.Fault ch : s.channels()) {
            assertEquals(DiagnosticJunctionBE.Fault.NO_BUS, ch);
        }

        final FigureWorld w = FigureWorld.of(BlockFigures.JUNCTIONS);
        assertTrue(w.cableTier(new BlockPos(-2, 2, 0)) != null && w.cableTier(new BlockPos(-2, 0, 0)) != null,
                "two runs on the box");
    }

    @Test
    void everyFixtureAnswersFromItsFigure() {
        for (final BlockFigure f : BlockFigures.all()) {
            for (int i = 0; i < f.placements().size(); i++) {
                final BlockFigure.Placement p = f.placements().get(i);
                final Map<String, Object> draw = CarryFixtures.draw(f, i);
                if (p.fixture() != null && CarryFixtures.IDS.contains(p.fixture())) {
                    assertNotNull(draw, f.id() + " placement " + i);
                    assertTrue(p.renderer(), f.id() + " placement " + i + ": a fixture is seen through its renderer");
                } else {
                    assertNull(draw, f.id() + " placement " + i);
                }
            }
        }
    }
}
