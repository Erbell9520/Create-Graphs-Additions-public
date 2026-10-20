package com.createstats.ground;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import com.createstats.aerial.V3;
import com.createstats.ground.GroundSnapshot.Shape;
import com.createstats.vccmk4.core.SteerPreset;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GroundContractTest {
    @Test
    void theKindIsWhatAPlayerWouldCallItFirst() {
        final SteerPreset F = SteerPreset.FRONT;

        assertEquals(VehicleKind.CAR, VehicleKind.classify(2, 4, 0, F, false, false, 0, 0));
        assertEquals(VehicleKind.CAR, VehicleKind.classify(3, 6, 0, F, false, false, 0, 0), "a 6x6 is still a car");
        assertEquals(VehicleKind.TRUCK, VehicleKind.classify(3, 8, 0, F, false, false, 0, 0), "eight wheels");
        assertEquals(VehicleKind.TRUCK, VehicleKind.classify(SteerPreset.TWIN_FRONT_FROM_AXLES, 8, 0,
                SteerPreset.TWIN_FRONT, false, false, 0, 0));
        assertEquals(VehicleKind.FORKLIFT, VehicleKind.classify(2, 4, 0, SteerPreset.REAR, false, false, 0, 1),
                "a forklift's mast does not make it a work vehicle");
        assertEquals(VehicleKind.TRACKED, VehicleKind.classify(2, 4, 0, SteerPreset.SKID, false, false, 0, 2),
                "skid steer makes a tank even with a crane on it");
        assertEquals(VehicleKind.ARTICULATED, VehicleKind.classify(2, 4, 0, SteerPreset.NONE, true, false, 0, 0));
        assertEquals(VehicleKind.HOVER, VehicleKind.classify(2, 4, 3, F, false, true, 0, 0), "jets that drive");
        assertEquals(VehicleKind.HOVER, VehicleKind.classify(0, 0, 4, F, false, false, 0, 0), "no wheels at all");
        assertEquals(VehicleKind.EMPTY, VehicleKind.classify(0, 0, 0, F, false, false, 0, 0));
        assertEquals(VehicleKind.ROAD_TRAIN, VehicleKind.classify(3, 6, 0, F, false, false, 2, 0));
        assertEquals(VehicleKind.ROAD_TRAIN, VehicleKind.classify(2, 4, 0, SteerPreset.NONE, true, false, 1, 0),
                "a loader towing a trailer is a road train");
        assertEquals(VehicleKind.WORK, VehicleKind.classify(3, 6, 0, F, false, false, 0, 2));
        assertEquals(VehicleKind.TRUCK, VehicleKind.classify(4, 8, 0, F, false, false, 0, 0));
    }

    @Test
    void theFixturesAreTheKindTheySayTheyAre() {
        assertEquals(VehicleKind.CAR, GroundFixtures.car().identity().kind());
        assertEquals(VehicleKind.ROAD_TRAIN, GroundFixtures.roadTrain().identity().kind());
        assertEquals(VehicleKind.TRACKED, GroundFixtures.tank().identity().kind());
        assertEquals(VehicleKind.FORKLIFT, GroundFixtures.forklift().identity().kind());
        assertEquals(VehicleKind.ARTICULATED, GroundFixtures.articulated().identity().kind());
        assertEquals(VehicleKind.HOVER, GroundFixtures.hovercraft().identity().kind());
        assertEquals(VehicleKind.WORK, GroundFixtures.crane().identity().kind());
        assertEquals(VehicleKind.TRUCK, GroundFixtures.truck16().identity().kind());
    }

    @Test
    void theOrdinalsOnTheWireAndInSavesOnlyEverGrow() {
        assertEquals(List.of("EMPTY", "CAR", "TRUCK", "FORKLIFT", "TRACKED", "ARTICULATED", "ROAD_TRAIN", "HOVER", "WORK"),
                names(VehicleKind.values()));
        assertEquals(List.of("RESET_TRIP", "CLEAR_TRAIL", "RESET_LEARNED"), names(GroundCommand.values()));
        assertEquals(List.of("HELM", "LEFT", "IMPACT", "AIRTIME", "COUPLED", "LOST", "PROBLEM", "CLEARED", "TOP_SPEED",
                "TRIP"), names(GroundSnapshot.EventKind.values()));
        for (final VehicleKind k : VehicleKind.values()) {
            assertEquals(k, VehicleKind.byOrdinal(k.ordinal()));
        }
        assertEquals(VehicleKind.EMPTY, VehicleKind.byOrdinal(99), "an unknown kind reads as nothing, not a crash");
        assertEquals(null, GroundCommand.byOrdinal(99));
        assertEquals(0b111, GroundCommand.all());
    }

    private static List<String> names(Enum<?>[] values) {
        final List<String> out = new ArrayList<>();
        for (final Enum<?> e : values) {
            out.add(e.name());
        }
        return out;
    }

    @Test
    void anOutlineIsTheBlocksItWasGiven() {
        final List<V3> blocks = new ArrayList<>();
        for (int x = 0; x < 2; x++) {
            for (int z = 0; z < 4; z++) {
                blocks.add(new V3(x - 0.5, 0.0, z - 1.5));
            }
        }
        final Shape s = GroundSilhouette.of(3, blocks);
        assertEquals(3, s.unit());
        assertEquals(1.0, s.cell(), 0.0);
        assertEquals(4, s.cols());
        assertEquals(2, s.topRows());
        assertEquals(1, s.sideRows());
        assertEquals(-2.0, s.originZ(), 1e-9);
        assertEquals(-1.0, s.originX(), 1e-9);
        assertEquals(8, bits(s.top()));
        assertEquals(4, bits(s.side()));
    }

    @Test
    void aLongBodyCoarsensItsCellsRatherThanOverflowing() {
        final List<V3> blocks = new ArrayList<>();
        for (int z = 0; z < 200; z++) {
            blocks.add(new V3(0.0, 0.0, z - 100.0));
            blocks.add(new V3(0.0, 1.0, z - 100.0));
        }
        final Shape s = GroundSilhouette.of(0, blocks);
        assertEquals(4.0, s.cell(), 0.0);
        assertEquals(50, s.cols());
        assertTrue(s.cols() <= Shape.MAX_CELLS && s.topRows() <= Shape.MAX_CELLS && s.sideRows() <= Shape.MAX_CELLS);
        assertEquals(50, bits(s.top()), "every cell of the run is filled");
    }

    @Test
    void anyCloudOfBlocksStaysInsideItsBitmaps() {
        final Random rnd = new Random(7);
        for (int trial = 0; trial < 200; trial++) {
            final List<V3> blocks = new ArrayList<>();
            final int n = 1 + rnd.nextInt(400);
            final double spread = 1 + rnd.nextInt(300);
            for (int i = 0; i < n; i++) {
                blocks.add(new V3(Math.floor(rnd.nextGaussian() * spread) + 0.5, Math.floor(rnd.nextGaussian() * 3) + 0.5,
                        Math.floor(rnd.nextGaussian() * spread) + 0.5));
            }
            final Shape s = GroundSilhouette.of(0, blocks);
            assertTrue(s.cols() >= 1 && s.cols() <= Shape.MAX_CELLS, "trial " + trial);
            assertEquals((s.cols() * s.topRows() + 7) >> 3, s.top().length, "trial " + trial);
            assertEquals((s.cols() * s.sideRows() + 7) >> 3, s.side().length, "trial " + trial);
            assertTrue(bits(s.top()) >= 1, "trial " + trial);
        }
        assertEquals(0, GroundSilhouette.of(1, List.of()).cols(), "no blocks, no outline");
    }

    private static int bits(byte[] b) {
        int n = 0;
        for (final byte x : b) {
            n += Integer.bitCount(x & 0xFF);
        }
        return n;
    }

    static Map<String, Float> channels(GroundSnapshot s) {
        final DriveReadout r = DriveMath.read(s);
        final Map<String, Float> out = new LinkedHashMap<>();
        GroundChannels.publish(r, GroundAlerts.evaluate(r, List.of(), 0.0), (id, label, value, type) -> {
            assertTrue(id.startsWith(GroundChannels.PREFIX), id);
            assertFalse(label.isBlank(), id);
            assertTrue(Float.isFinite(value), id + " published " + value);
            assertEquals(null, out.put(id, value), id + " published twice");
        });
        return out;
    }

    @Test
    void theChannelIdsASavedPanelBindsToAreFixed() {
        // ids never change meaning once shipped
        final Set<String> car = channels(GroundFixtures.car()).keySet();
        for (final String id : List.of("speed", "speed_kmh", "gear", "throttle", "brake", "steer", "lateral_g",
                "longitudinal_g", "worst_slip", "least_grip", "pitch", "roll", "heading", "altitude", "odometer", "trip",
                "top_speed", "cruise", "alerts")) {
            assertTrue(car.contains("drive_" + id), id);
        }
        final Set<String> train = channels(GroundFixtures.roadTrain()).keySet();
        assertTrue(train.contains("drive_articulation"));
        assertTrue(train.contains("drive_trailers"));
        assertFalse(car.contains("drive_trailers"), "a car tows nothing, and says nothing about it");
    }

    @Test
    void theChannelsCarryTheReadings() {
        final GroundSnapshot s = GroundFixtures.car();
        final DriveReadout r = DriveMath.read(s);
        final Map<String, Float> c = channels(s);
        assertEquals((float) r.speed, c.get("drive_speed"), 1e-6f);
        assertEquals((float) (Math.abs(r.speed) * 3.6), c.get("drive_speed_kmh"), 1e-4f);
        assertEquals(3.0f, c.get("drive_gear"), 0.0f);
        assertEquals((float) s.odometer().odometer(), c.get("drive_odometer"), 1e-3f);
        assertEquals((float) r.model.cruise(), c.get("drive_cruise"), 1e-6f);
    }

    @Test
    void anUnknownReadingIsNotAChannel() {
        final Set<String> fresh = channels(GroundSnapshot.empty(100L)).keySet();
        assertFalse(fresh.contains("drive_top_speed"));
        assertFalse(fresh.contains("drive_cruise"));

        assertFalse(fresh.contains("drive_speed"));
        assertFalse(fresh.contains("drive_heading"));
        assertFalse(fresh.contains("drive_altitude"));
        assertTrue(fresh.contains("drive_odometer"), "the recorder's own figures are still real");
        assertTrue(fresh.contains("drive_alerts"));
    }

    @Test
    void theGearCodeReadsAsTheStoryFromParked() {
        final Map<Character, Integer> expect = Map.of('P', 0, 'H', 1, 'N', 2, 'D', 3, 'R', 4, 'B', 5, '-', 6);
        expect.forEach((gear, code) -> assertEquals(code, GroundChannels.gearCode(gear), "gear " + gear));
    }

    @Test
    void theAlertsChannelCountsCautionsAndCriticalsOnly() {
        final List<GroundAlerts.Alert> a = List.of(
                new GroundAlerts.Alert(GroundAlerts.Code.FUEL, GroundAlerts.Level.ADVISORY, "FUEL  low"),
                new GroundAlerts.Alert(GroundAlerts.Code.SWAY, GroundAlerts.Level.CAUTION, "SWAY  B2"),
                new GroundAlerts.Alert(GroundAlerts.Code.LINK, GroundAlerts.Level.CRITICAL, "LINK  B3"));
        assertEquals(2, GroundChannels.countAtLeast(a, GroundAlerts.Level.CAUTION));
    }

    static List<String> board(GroundSnapshot s, int max) {
        final DriveReadout r = DriveMath.read(s);
        return GroundText.lines(r, GroundAlerts.evaluate(r, List.of(), 0.0), max);
    }

    @Test
    void theBoardLeadsWithTheVehicleAndItsSpeed() {
        final List<String> lines = board(GroundFixtures.car(), 12);
        final DriveReadout r = DriveMath.read(GroundFixtures.car());
        assertEquals("Rosie  " + Math.round(r.speedKmh) + " km/h  D", lines.get(0));
        assertTrue(lines.get(1).startsWith("DRV +100%"), lines.get(1));
        assertTrue(lines.stream().anyMatch(l -> l.startsWith("ODO ")));
        assertTrue(lines.stream().anyMatch(l -> l.contains("CRUISE") && l.endsWith("model")), "a modelled figure says so");
    }

    @Test
    void aWarningJumpsToTheSecondLine() {
        final List<String> lines = board(GroundFixtures.runaway(), 4);
        assertTrue(lines.get(0).contains("ROLLING"), lines.get(0));
        assertTrue(lines.get(1).startsWith("RUNAWAY"), lines.get(1));
        final List<String> link = board(GroundFixtures.lostLink(), 2);
        assertTrue(link.get(1).startsWith("LINK"), link.get(1));
    }

    @Test
    void theBoardFitsWhatItIsGiven() {
        for (final GroundFixtures.Named n : GroundFixtures.all()) {
            for (int max = 0; max <= 9; max++) {
                assertTrue(board(n.snapshot(), max).size() <= max, n.name() + " max " + max);
            }
        }
    }

    @Test
    void noBoardEverPrintsANumberItDoesNotHave() {
        final List<GroundSnapshot> all = new ArrayList<>();
        for (final GroundFixtures.Named n : GroundFixtures.all()) {
            all.add(n.snapshot());
        }
        all.add(GroundSnapshot.empty(0L));
        for (final GroundSnapshot s : all) {
            for (final String line : board(s, 20)) {
                assertFalse(line.contains("NaN") || line.contains("Infinity"), line);
            }
        }
        final List<String> off = board(GroundSnapshot.empty(0L), 20);
        assertEquals("Vehicle  not on a vehicle", off.get(0));
        assertTrue(off.stream().noneMatch(l -> l.startsWith("HDG")), "off a craft there is no heading to give");
    }

    @Test
    void theTrainLineNamesEachLink() {
        final String line = GroundText.train(DriveMath.read(GroundFixtures.roadTrain()));
        assertTrue(line.startsWith("TOWING 2: B2 hitch 12 "), line);
        assertTrue(line.contains("B3 rope"), line);
        final String springs = GroundText.train(DriveMath.read(GroundFixtures.snapping()));
        assertTrue(springs.contains("B3 springs 92% NEAR SNAP"), springs);
    }

    @Test
    void distancesAndTimesReadAsABoardShowsThem() {
        assertEquals("850 b", GroundText.distance(850.0));
        assertEquals("12.4 km", GroundText.distance(12_400.0));
        assertEquals("250 km", GroundText.distance(250_000.0));
        assertEquals("--", GroundText.distance(Double.NaN));
        assertEquals("40 s", GroundText.duration(40.0));
        assertEquals("2:05", GroundText.duration(125.0));
        assertEquals("41 min", GroundText.duration(2460.0));
        assertEquals("--", GroundText.duration(Double.NaN));
        assertEquals("Mechanical Piston", GroundText.prettyId("block.create.mechanical_piston"));
        assertEquals("", GroundText.prettyId(null));
    }

    @Test
    void everyFixtureIsDistinct() {
        final Set<String> names = new HashSet<>();
        for (final GroundFixtures.Named n : GroundFixtures.all()) {
            assertTrue(names.add(n.name()), n.name());
        }
        assertTrue(names.containsAll(Arrays.asList("car", "road train", "tank", "realistic", "empty")), names.toString());
    }
}
