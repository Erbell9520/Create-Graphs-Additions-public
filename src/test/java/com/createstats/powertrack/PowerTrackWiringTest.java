package com.createstats.powertrack;

import com.createstats.powertrack.core.CollectorPose;
import com.createstats.powertrack.core.FeederMeter;
import com.createstats.powertrack.core.RailProfile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PowerTrackWiringTest {
    private static final String J = "src/main/java/com/createstats/";
    private static final String A = "src/main/resources/assets/create_stats/";
    private static final String DATA = "src/main/resources/data/";

    private static String read(String path) throws IOException {
        final Path p = Path.of(path);
        assertTrue(Files.exists(p), "missing file: " + path);
        return Files.readString(p, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    private static String code(String text) {
        return text.replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("//[^\\n]*", " ");
    }

    private static String method(String source, String signature) {
        final int at = source.indexOf(signature);
        assertTrue(at >= 0, "no method " + signature);
        final int open = source.indexOf('{', at);
        int depth = 0;
        for (int i = open; i < source.length(); i++) {
            final char c = source.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}' && --depth == 0) {
                return source.substring(open + 1, i);
            }
        }
        throw new AssertionError("unbalanced " + signature);
    }

    @Test
    @DisplayName("the material is standard track, registered before the block, and in Create's track block entity type")
    void material() throws IOException {
        final String pt = code(read(J + "powertrack/PowerTrack.java"));
        final String build = pt.substring(pt.indexOf("TrackMaterialFactory.make(MATERIAL_ID)"), pt.indexOf(".build();"));
        assertTrue(!build.contains(".trackType("), "a custom track type makes every train refuse Power Track");
        assertTrue(build.contains(".defaultModels()"), "without models every Power Track curve throws in TrackVisual");
        assertTrue(build.contains(".customBlockFactory(PowerTrackBlock::new)"), "the block would lose its tooltip");
        assertTrue(method(pt, "public static void addTrackBlock(BlockEntityTypeAddBlocksEvent event)")
                        .contains("event.modify(AllBlockEntityTypes.TRACK.get(), ModBlocks.POWER_TRACK.get())"),
                "Power Track missing from Create's track block entity type: the first curve crashes");
        final String blocks = code(read(J + "registry/ModBlocks.java"));
        final int init = blocks.indexOf("com.createstats.powertrack.PowerTrack.init();");
        final int reg = blocks.indexOf("POWER_TRACK = CreateStats.registrate()");
        assertTrue(init >= 0 && reg > init, "the material must be touched before the block is registered");
        assertTrue(blocks.contains("TrackTargetingBlockItem.ofType(com.createstats.powertrack.PowerTrack.FEEDER)"),
                "the Track Feeder must be placed with Create's targeting item, or it never finds its rail");
        assertTrue(blocks.contains("TrackTargetingBlockItem.ofType(com.createstats.powertrack.PowerTrack.INSULATOR)"),
                "the Section Insulator must be placed with Create's targeting item");
        assertTrue(blocks.contains("movementBehaviour(new com.createstats.powertrack.CurrentCollectorMovement())"),
                "without its movement behaviour a collector rides a train as dead weight");
        assertTrue(blocks.contains("TrackBlockItem::new"), "Power Track must place like Create track");
    }

    @Test
    @DisplayName("a car honours the Portable Power Interface's claim both ways, stamps its own, and tells the sync service")
    void loopGuardAndSync() throws IOException {
        final String svc = code(read(J + "powertrack/PowerTrackService.java"));
        assertTrue(svc.contains("PortablePowerInterfaceBE.directionLock(level)"), "the rail keeps its own lock, not the station's");
        assertTrue(svc.contains("stationLock.mayMove(id, PortableFeTransfer.Mode.CHARGE"), "a car held drawing could be charged");
        assertTrue(svc.contains("stationLock.mayMove(id, PortableFeTransfer.Mode.DRAW"), "a car held charging could give");
        final String run = method(svc, "private static void runLayout(");
        final int commit = run.indexOf("car.stores.commit(car.ledger)");
        final int touched = run.indexOf("CarriedDeviceSync.touched(car.entity)");
        final int stamp = run.indexOf("stationLock.stamp(car.entity.getId()");
        assertTrue(commit >= 0 && touched > commit, "a committed charge must wake the sync service (KI-40)");
        assertTrue(stamp > commit, "the rail never claims the car, so a station could take turns with it");
        assertTrue(run.contains("RailTick.settle(result.feederDelta(), feederSection, result.carDelta(), actual,"),
                "what the banks really took must land back in a feeder on the same section (KI-141)");
        assertTrue(run.contains("railResult(feederDelta[i]"), "feeders must be booked the settled deltas");
        final String ppi = code(read(J + "blockentity/PortablePowerInterfaceBE.java"));
        assertTrue(method(ppi, "public static PortableFeDirectionLock directionLock(Level level)")
                .contains("LOCKS.computeIfAbsent(level"), "the accessor must hand out the interface's own lock");
        final String move = code(read(J + "powertrack/CurrentCollectorMovement.java"));
        assertTrue(method(move, "public void tick(MovementContext context)").contains("PowerTrackService.noteShoe(context)"),
                "a collector that notes no shoe never exchanges anything");
        assertTrue(method(move, "public boolean disableBlockEntityRendering()").contains("return true"),
                "the arm would be drawn twice on a train");
    }

    @Test
    @DisplayName("a feeder makes one offer a tick whoever asks, and a split or merge rereads every graph (KI-139)")
    void oneOfferATick() throws IOException {
        final String feeder = code(read(J + "blockentity/TrackFeederBE.java"));
        final String offer = method(feeder, "public SectionExchange.Feeder railOffer(long now)");
        final int guard = offer.indexOf("offeredAt == now");
        final int stamp = offer.indexOf("offeredAt = now;");
        final int answer = offer.indexOf("new SectionExchange.Feeder(");
        assertTrue(guard >= 0, "a second layout listing this feeder could take a second dial's worth this tick");
        assertTrue(stamp > guard && answer > stamp, "the offer must be stamped before it is made, once a tick");
        final String svc = code(read(J + "powertrack/PowerTrackService.java"));
        final String refresh = method(svc, "private static void refreshGraphs(long now)");
        assertTrue(refresh.contains("setChanged ||"),
                "a graph appearing or going must make every graph due, or a stale layout outlives a split");
    }

    @Test
    @DisplayName("a supplying feeder opens its buffer on the plan's reason, never on the room before the rail takes (KI-144)")
    void feederOpensOnTheReason() throws IOException {
        final String step = method(code(read(J + "blockentity/TrackFeederBE.java")), "private void step(long now)");
        final int call = step.indexOf("buffer.configure(");
        assertTrue(call >= 0, "the feeder no longer configures its buffer in step");

        final String configure = step.substring(call, step.indexOf(';', call));
        assertTrue(configure.contains("plan.chargeOpen()"),
                "RailConservationTest pins FeederPlan.Plan.chargeOpen, so the block must open on it");
        assertFalse(configure.contains("gridIntake"),
                "gated on the room the plan sees, the buffer is refilled every other tick and a heavy train gets half the dial");
    }

    @Test
    @DisplayName("the shoe conducts exactly where the drawn shoe lands: one solve, sled and arm, on both sides (KI-140)")
    void contactIsTheDrawnShoe() throws IOException {
        final String geo = code(read(J + "powertrack/CollectorGeometry.java"));
        final String place = method(geo, "public static Placement place(");
        assertTrue(place.contains("ShoeSlide.onCar(") && place.contains("ShoeSlide.free("),
                "the sled must be sent across to the rail, on a car from its own centre line");
        assertTrue(place.contains("rest(slid,"), "the arm must rest on the rail under the slid shoe, not the unslid one");
        final String svc = code(read(J + "powertrack/PowerTrackService.java"));
        assertTrue(method(svc, "static int shoeSection(").contains("CollectorGeometry.place("),
                "the server's contact must come from the landing, not a fixed reach");
        assertTrue(method(svc, "static int shoeSection(").contains("placed.slid()"),
                "the section must be read under the slid shoe");
        assertTrue(method(svc, "private static void runLayout(").contains("shoeSection(layout,"),
                "the tick must decide contact through shoeSection");
        assertTrue(method(svc, "public static void noteShoe(").contains("CollectorGeometry.centreOffset("),
                "a collector a block off the centre line must say so, or its sled looks for rail under itself");
        final String client = code(read(J + "powertrack/client/ClientRail.java"));
        assertTrue(method(client, "public static CollectorGeometry.Placement place(").contains("CollectorGeometry.place("));
        assertTrue(method(client, "public static void collectorTick(").contains("place(level, centre, trail, side, offset, true)"),
                "the sparks and the arc must read the same landing as the server");
        final String renderer = code(read(J + "powertrack/client/CurrentCollectorRenderer.java"));
        assertTrue(method(renderer, "public static void renderInContraption(").contains("ClientRail.place("),
                "the gear on a train must be drawn at the landing the server conducts by");
        assertTrue(method(renderer, "protected void renderSafe(").contains("ClientRail.place("));
        assertTrue(!svc.contains("shoePoint(") && !client.contains("shoePoint("),
                "the old fixed-reach point is back somewhere");
    }

    @Test
    @DisplayName("a collector hangs from the floor above it, so a car takes it without glue (KI-145)")
    void collectorHangs() throws IOException {
        final String block = code(read(J + "powertrack/CurrentCollectorBlock.java"));
        final String hang = method(block, "public static void registerHanging(");
        assertTrue(hang.contains("registerAttachedCheck(") && hang.contains("Direction.UP")
                        && hang.contains("CheckResult.SUCCESS"),
                "the collector must answer attached towards the block above it");
        final String mod = code(read(J + "CreateStats.java"));
        assertTrue(method(mod, "private void commonSetup(").contains("CurrentCollectorBlock.registerHanging();"),
                "the check must be registered, or an unglued collector stays on the track");
    }

    @Test
    @DisplayName("the client gets what it needs: the feeder's block position, the partials, the track's goggles")
    void clientSites() throws IOException {
        final String point = code(read(J + "powertrack/FeederPoint.java"));
        assertTrue(method(point, "public void write(FriendlyByteBuf buffer").contains("buffer.writeBlockPos("));
        assertTrue(method(point, "public void read(FriendlyByteBuf buffer").contains("blockEntityPos = buffer.readBlockPos()"),
                "write and read must match or every client's track graph sync breaks");
        assertTrue(method(code(read(J + "client/ModPartialModels.java")), "public static void init()")
                .contains("PowerTrackPartials.init()"), "partials made after baking draw nothing");
        assertTrue(code(read(J + "mixin/GoggleOverlayRendererMixin.java")).contains("PowerTrackGoggles.render(graphics, delta)"),
                "straight Power Track has no block entity: without the hook goggles show nothing on it");
        final String snapshot = code(read(J + "train/smart/RailSnapshot.java"));
        assertTrue(snapshot.contains("t.putInt(\"rs\", this.railState)") && snapshot.contains("s.railState = t.getInt(\"rs\")"),
                "the Mk III rail state must ride the snapshot both ways");
        assertTrue(code(read(J + "train/smart/RailScanner.java")).contains("PowerTrackService.fillRail(train, t)"));
        assertTrue(code(read(J + "statsmk3/page/TrainView.java")).contains("PowerTrackFacts.line(t.railState, t.railFlow)"));
        final String rail = code(read(J + "powertrack/client/PowerRailRenderer.java"));
        assertEquals(2, rail.split("level\\.hasChunk\\(", -1).length - 1,
                "both of the rail pass's bucket loops must skip chunks the client has not loaded");
    }

    @Test
    @DisplayName("tags, loot, recipes, the sound and every lang key the code asks for")
    void resources() throws IOException {
        for (String tag : List.of("create/tags/block/tracks.json", "create/tags/block/girdable_tracks.json",
                "create/tags/item/tracks.json")) {
            assertTrue(read(DATA + tag).contains("\"create_stats:power_track\""),
                    tag + ": placement, girders and curve end blocks read this tag");
        }
        for (String block : List.of("power_track", "track_feeder", "section_insulator", "current_collector")) {
            assertTrue(Files.exists(Path.of(DATA + "create_stats/loot_table/blocks/" + block + ".json")), "no loot: " + block);
            assertTrue(Files.exists(Path.of(DATA + "create_stats/recipe/" + block + ".json")), "no recipe: " + block);
            assertTrue(Files.exists(Path.of(A + "blockstates/" + block + ".json")), "no blockstate: " + block);
            assertTrue(Files.exists(Path.of(A + "models/item/" + block + ".json")), "no item model: " + block);
        }
        assertTrue(read(A + "sounds.json").contains("\"power_track_arc\""));
        assertTrue(Files.exists(Path.of(A + "sounds/power_track_arc.ogg")), "the arc's sound file is missing");
        final String lang = read(A + "lang/en_us.json");
        final Set<String> keys = new HashSet<>();
        final Matcher m = Pattern.compile("\"([^\"]+)\"\\s*:").matcher(lang);
        while (m.find()) {
            keys.add(m.group(1));
        }
        final List<String> missing = new ArrayList<>();
        final Pattern used = Pattern.compile("\"((?:block\\.)?create_stats\\.[a-z_]+(?:\\.[a-z_]+)*)\"");
        for (String file : List.of(J + "blockentity/TrackFeederBE.java", J + "powertrack/PowerTrackBlock.java",
                J + "powertrack/TrackFeederBlock.java", J + "powertrack/CurrentCollectorBlock.java",
                J + "powertrack/SectionInsulatorBlock.java")) {
            final Matcher u = used.matcher(code(read(file)));
            while (u.find()) {
                final String key = u.group(1);

                final String want = key.endsWith(".power_track") || key.endsWith(".track_feeder")
                        || key.endsWith(".current_collector") || key.endsWith(".section_insulator") ? key + ".tooltip" : key;
                if (!keys.contains(want)) {
                    missing.add(want + " (" + file + ")");
                }
            }
        }
        for (String key : List.of("block.create_stats.power_track", "block.create_stats.track_feeder",
                "block.create_stats.section_insulator", "block.create_stats.current_collector",
                "create_stats.display_source.track_feeder", "subtitles.create_stats.power_track_arc",
                "block.create_stats.power_track.tooltip.station")) {
            if (!keys.contains(key)) {
                missing.add(key);
            }
        }
        assertTrue(missing.isEmpty(), "lang keys the code reads and the file lacks: " + missing);
    }

    private static double py(String script, String name) throws IOException {
        final String text = read(script);

        final Matcher m = Pattern.compile("(?m)^([A-Z_, ]+)=\\s*([-0-9., +]+?)\\s*(?:#.*)?$").matcher(text);
        while (m.find()) {
            final String[] names = m.group(1).split(",");
            final String[] values = m.group(2).split(",");
            for (int i = 0; i < names.length && i < values.length; i++) {
                if (names[i].trim().equals(name)) {
                    double sum = 0;
                    for (String part : values[i].split("\\+")) {
                        sum += Double.parseDouble(part.trim());
                    }
                    return sum;
                }
            }
        }
        throw new AssertionError(name + " not assigned in " + script);
    }

    private static double[] pyTuple(String script, String name) throws IOException {
        final Matcher m = Pattern.compile("(?m)^" + name + "\\s*=\\s*\\(([-0-9., ]+)\\)").matcher(read(script));
        assertTrue(m.find(), name + " not a tuple in " + script);
        final String[] parts = m.group(1).split(",");
        final double[] out = new double[parts.length];
        for (int i = 0; i < parts.length; i++) {
            out[i] = Double.parseDouble(parts[i].trim());
        }
        return out;
    }
}
