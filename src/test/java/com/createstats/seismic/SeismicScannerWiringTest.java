package com.createstats.seismic;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

// reads source, these all fail with no error at all
class SeismicScannerWiringTest {
    private static final Path MAIN = Path.of("src/main/java/com/createstats");
    private static final Path RES = Path.of("src/main/resources");
    private static final Path ASSETS = RES.resolve("assets/create_stats");
    private static final Path BE = MAIN.resolve("blockentity/SeismicScannerBE.java");
    private static final Path BLOCK = MAIN.resolve("block/SeismicScannerBlock.java");
    private static final Path NETWORK = MAIN.resolve("power/FeWireNetwork.java");
    private static final Path CAPS = MAIN.resolve("power/FeCapabilities.java");
    private static final Path PDU = MAIN.resolve("blockentity/PowerDistributionUnitBE.java");
    private static final Path PACKETS = MAIN.resolve("network/ModPackets.java");
    private static final Path CLIENT = MAIN.resolve("seismic/client");
    private static final Path LANG = ASSETS.resolve("lang/en_us.json");

    @Test
    @DisplayName("FE: the capability is registered on the block entity type, through takesPowerOn")
    void theCapabilityIsRegistered() {
        final String caps = code(read(CAPS));
        final Matcher m = Pattern.compile("registerBlockEntity\\(\\s*Capabilities\\.EnergyStorage\\.BLOCK,\\s*"
                + "ModBlockEntities\\.SEISMIC_SCANNER\\.get\\(\\)\\s*,([^;]*);").matcher(caps);
        assertTrue(m.find(), "FeCapabilities registers no energy capability for SEISMIC_SCANNER: no cable, cord or bore"
                + " can reach the winch");
        assertTrue(m.group(1).contains("takesPowerOn(side)") && m.group(1).contains("energyHandle()"),
                "the registration must publish energyHandle() on the faces takesPowerOn allows: " + m.group(1));
    }

    @Test
    @DisplayName("FE: the walk has a branch, spreadFromBlock enqueues it, and the rig builds and solves its circuit")
    void theWalkAndTheTick() {
        final String net = code(read(NETWORK));
        final String branch = branch(net, "node instanceof com.createstats.blockentity.SeismicScannerBE");
        assertNotNull(branch, "no walk branch matches SeismicScannerBE: it joins no grid and never charges");
        assertTrue(branch.contains("found.add(scanner)") && branch.contains("takesPowerOn(side)")
                && branch.contains("spreadFromFace("), "the walk branch must add the rig and spread from its powered faces");
        final String spread = branch(net, "be instanceof com.createstats.blockentity.SeismicScannerBE");
        assertNotNull(spread, "spreadFromBlock never enqueues a Seismic Scanner: a cable beside it reaches nothing");
        assertTrue(spread.contains("walk.enqueue(") && spread.contains("takesPowerOn(dir.getOpposite())"),
                "spreadFromBlock's branch must enqueue through the face the cable touches: " + spread);
        assertTrue(net.contains("buildFrom(com.createstats.blockentity.SeismicScannerBE origin)"),
                "no buildFrom overload for the rig");
        final String tick = body(code(read(BE)), "public void tick()");
        assertNotNull(tick);
        assertTrue(tick.contains("FeWireNetwork.buildFrom(this)") && tick.contains("network.solveOnce(level)"),
                "the rig must build and solve its own circuit (FE-REF KI-46)");
    }

    @Test
    @DisplayName("FE: a managed load born LOW, shed by a board, priced by what it costs and not by the shed")
    void theManagedLoad() {
        final String be = code(read(BE));
        assertTrue(Pattern.compile("class SeismicScannerBE[^{]*FeGridMember[^{]*FeManagedLoad").matcher(be).find(),
                "the rig must be a grid member and a managed load");
        assertTrue(code(read(PDU)).contains("Map.entry(\"seismic_scanner\", FePriority.LOW)"),
                "the board's AUTO_TIERS must give the rig its LOW tier, or ASSIGN resets it to the middle");
        final String tick = body(be, "public void tick()");
        assertTrue(tick.contains("setManagedShed(false)"), "a shed must lapse when no board drives the circuit");
        final String takes = body(be, "private boolean winchTakes()");
        assertNotNull(takes);
        assertTrue(takes.contains("managed.isSuppressed()"), "the winch must stop taking FE the moment it is shed");
        final int rated = tick.indexOf("ratedDraw =");
        assertTrue(rated >= 0, "the rated draw is never set");
        final String ratedLine = tick.substring(rated, tick.indexOf(';', rated));
        assertTrue(ratedLine.contains("managed.isForcedOff()") && !ratedLine.contains("isSuppressed"),
                "the rated draw is zeroed by being switched off, never by a shed (FeManagedState): " + ratedLine);
        final String receive = body(be, "public int receiveEnergy(int maxReceive, boolean simulate)");
        assertNotNull(receive);
        assertTrue(receive.contains("winchTakes()") && receive.contains("SeismicCharge.winchTake(")
                        && receive.contains("if (!simulate)") && receive.contains("intake.record("),
                "the handler must ask the winch, cap by the law, book only on a real transfer, and measure intake");
        final String extract = body(be, "public int extractEnergy(int maxExtract, boolean simulate)");
        assertTrue(extract != null && extract.trim().equals("return 0;"), "nothing comes back out of the weight but the drop");
        final String capacity = body(be, "public int bufferCapacity()");
        assertTrue(capacity != null && capacity.trim().equals("return 0;"), "the node holds no pool FE (INV-18)");
    }

    @Test
    @DisplayName("client: every client class subscribes on the client only and nothing common names one")
    void clientStaysOnTheClient() {
        for (final String name : List.of("SeismicReveals", "SeismicShake", "SeismicPreview")) {
            final String src = read(CLIENT.resolve(name + ".java"));
            assertTrue(src.contains("value = Dist.CLIENT"), name + " must subscribe on the client only");
        }

        final List<String> wrong = new ArrayList<>();
        for (final Path p : javaFiles(MAIN)) {
            if (p.startsWith(CLIENT) || p.startsWith(MAIN.resolve("client"))) {
                continue;
            }
            final String src = code(read(p));
            for (final String name : List.of("SeismicReveals", "SeismicShake", "SeismicPreview", "SeismicWinchSound",
                    "SeismicScannerRenderer", "SeismicClient", "ThroughWalls")) {
                if (src.contains(name)) {
                    wrong.add(MAIN.relativize(p) + " names " + name);
                }
            }
        }
        wrong.remove("network" + java.io.File.separator + "SeismicVeinsPacket.java names SeismicReveals");
        wrong.remove("blockentity" + java.io.File.separator + "SeismicScannerBE.java names SeismicClient");
        wrong.remove("registry" + java.io.File.separator + "ModBlockEntities.java names SeismicScannerRenderer");
        assertTrue(wrong.isEmpty(), "common code names a client class, which a dedicated server would load: " + wrong);
        final String packet = code(read(MAIN.resolve("network/SeismicVeinsPacket.java")));
        assertTrue(packet.contains("ctx.enqueueWork(() -> com.createstats.seismic.client.SeismicReveals.accept(packet))"),
                "the packet reaches the client store only inside the handler's lambda");
        final String tick = body(code(read(BE)), "public void tick()");
        final int client = tick.indexOf("SeismicClient.tick(this)");
        final int guard = tick.indexOf("level.isClientSide()");
        assertTrue(guard >= 0 && client > guard && tick.indexOf("return;", client) > client,
                "the block entity reaches SeismicClient only on the client branch of its tick");
    }

    @Test
    @DisplayName("client: the renderer is registered, and the outlines draw through walls only for the witnesses")
    void rendererAndWitnesses() {
        final String bes = code(read(MAIN.resolve("registry/ModBlockEntities.java")));
        assertTrue(bes.contains(".renderer(() -> com.createstats.seismic.client.SeismicScannerRenderer::new)"),
                "the rig's block entity registers no renderer: no weight, no drum, no pen");
        final String reveals = code(read(CLIENT.resolve("SeismicReveals.java")));
        assertTrue(reveals.contains("ThroughWalls.type()"), "the veins must be drawn through the ground");

        assertTrue(reveals.contains("ThroughWalls.always(() -> labels("), "the labels must draw inside ThroughWalls.always");
        final String labels = body(reveals, "private static void labels(");
        assertNotNull(labels, "SeismicReveals draws no labels");
        assertTrue(labels.contains("Font.DisplayMode.SEE_THROUGH") && !labels.contains("Font.DisplayMode.NORMAL")
                        && labels.contains("Gpu.LABELS") && labels.contains("buffers.endBatch()")
                        && !labels.contains("renderBuffers()"),
                "the labels must draw see-through into the view's own buffer and flush it before always() restores the"
                        + " depth function; the shared buffer would draw them after: " + labels);
        final String be = code(read(BE));
        assertTrue(be.contains("WITNESS_RADIUS = 8.0"), "the witnesses are the players within eight blocks");
        final String send = body(be, "private void sendVeins(");
        assertNotNull(send);
        assertTrue(send.contains("for (final UUID who : witnesses)") && send.contains("PacketDistributor.sendToPlayer(p, pkt)"),
                "veins go to each witness and to nobody else");
        assertTrue(!be.contains("sendToPlayersTrackingChunk") && !be.contains("sendToAllPlayers"),
                "an outline sent to every player near the chunk is x-ray for people who never stood at the rig");
    }

    @Test
    @DisplayName("network: the veins packet is registered client-bound, under a bumped protocol version")
    void thePacketIsRegistered() {
        final String packets = code(read(PACKETS));
        assertTrue(packets.contains("registrar.playToClient(SeismicVeinsPacket.TYPE, SeismicVeinsPacket.STREAM_CODEC, "
                + "SeismicVeinsPacket::handle)"), "seismic_veins is not registered: every survey's outlines are dropped");
        final Matcher v = Pattern.compile("versioned\\(\"(\\d+)\"\\)").matcher(packets);
        assertTrue(v.find() && Integer.parseInt(v.group(1)) >= 57, "a new payload needs the protocol version bumped");
    }

    @Test
    @DisplayName("a load is never a pulse: the signal is saved, and read as it stands on the first tick (review P0-1)")
    void theLoadIsNoPulse() {
        final String be = code(read(BE));
        final String tick = body(be, "public void tick()");
        assertNotNull(tick, "SeismicScannerBE has no tick()");
        final int fires = tick.indexOf("SeismicRig.signalFires(powered, wasPowered, signalSettled)");
        final int settles = tick.indexOf("signalSettled = true;");
        assertTrue(fires >= 0 && settles > fires,
                "the tick must ask signalFires with the settled flag, and settle only after asking: " + tick);
        final String write = body(be, "protected void write(");
        assertTrue(write != null && write.contains("tag.putBoolean(\"Powered\", wasPowered)"),
                "the last tick's signal must be saved, or a lever left on reads as a new pulse at every load");
        final String read = body(be, "protected void read(");
        assertTrue(read != null && read.contains("wasPowered = tag.getBoolean(\"Powered\")"), "and read back");
        assertTrue(!be.contains("signalSettled = tag") && !be.contains("putBoolean(\"Settled\""),
                "the settled flag must start false after every load, never be saved");
    }

    @Test
    @DisplayName("taken up, the rig keeps its tuning: the loot copies the custom data the rig writes (review P1-7)")
    void theTuningTravels() {
        final JsonObject loot = JsonParser.parseString(read(RES.resolve("data/create_stats/loot_table/blocks/seismic_scanner.json")))
                .getAsJsonObject();
        boolean copies = false;
        for (final var pool : loot.getAsJsonArray("pools")) {
            for (final var entry : pool.getAsJsonObject().getAsJsonArray("entries")) {
                final var functions = entry.getAsJsonObject().getAsJsonArray("functions");
                for (int i = 0; functions != null && i < functions.size(); i++) {
                    final JsonObject f = functions.get(i).getAsJsonObject();
                    copies |= f.get("function").getAsString().equals("minecraft:copy_components")
                            && f.get("source").getAsString().equals("block_entity")
                            && f.getAsJsonArray("include").toString().contains("\"minecraft:custom_data\"");
                }
            }
        }
        assertTrue(copies, "the rig's loot must copy its custom data from the block entity");
        final String be = code(read(BE));
        final String collect = body(be, "protected void collectImplicitComponents(");
        assertTrue(collect != null && collect.contains("DataComponents.CUSTOM_DATA") && collect.contains("writeSettings("),
                "the rig must write its tuning, and only its tuning, as custom data");
        final String apply = body(be, "protected void applyImplicitComponents(");
        assertTrue(apply != null && apply.contains("SeismicTargets.valid(level, wanted)"),
                "a placed rig must check the item's target against this world's stations");
    }

    @Test
    @DisplayName("the JEI page is registered, with every figure the code's own (review P1-1, INV-12)")
    void theJeiPage() {
        final String jei = code(read(MAIN.resolve("compat/jei/CreateStatsJeiPlugin.java")));
        final int at = jei.indexOf("registration.addIngredientInfo(ModBlocks.SEISMIC_SCANNER.get(),");
        assertTrue(at >= 0, "CreateStatsJeiPlugin must register the Seismic Scanner's information page");

        final String call = jei.substring(at, jei.indexOf(';', at));
        for (final String arg : List.of("\"create_stats.jei.seismic_scanner.info\"", "SeismicCharge.MIN_RADIUS",
                "SeismicCharge.MAX_RADIUS", "SeismicRig.comparator(", "SeismicScannerBE.WITNESS_RADIUS",
                "VeinVisibility.OVERVIEW_TICKS", "VeinVisibility.NEAR", "VeinVisibility.FAR")) {
            assertTrue(call.contains(arg), "the page's call lacks " + arg + ": " + call);
        }
        final String page = JsonParser.parseString(read(LANG)).getAsJsonObject()
                .get("create_stats.jei.seismic_scanner.info").getAsString();
        assertTrue(page.contains("%9$s") && !page.contains("%10$s") && !page.matches("(?s).*%s.*"),
                "the page takes the call's nine figures by position");
    }

    @Test
    @DisplayName("tuner: either half reaches the host, the lever is never a hold, and the cost is the blow")
    void theTuner() {
        final String hold = code(read(MAIN.resolve("client/tuner/TunerHold.java")));
        assertTrue(hold.contains("TunerHost.hostPos(mc.level, blockHit.getBlockPos())")
                && hold.contains("tunesAt(blockHit.getBlockPos(), blockHit.getLocation())"),
                "the ring must resolve the upper half to the host and keep off the lever");
        final String panel = code(read(MAIN.resolve("client/tuner/TunerWrenchPanel.java")));
        assertTrue(panel.contains("TunerHost.hostPos(mc.level, blockHit.getBlockPos())"), "the wrench panel must too");
        assertTrue(panel.contains("host.costLabel("), "the wrench panel must word the cost through the host");
        assertTrue(code(read(MAIN.resolve("client/tuner/TunerScreen.java"))).contains("host.costLabel("),
                "the ring must word the cost through the host");

        assertTrue(code(read(MAIN.resolve("client/tuner/TunerScreen.java"))).contains("host.stationLine(station, range, true)"),
                "the ring must word the station's line through the host, short");
        assertTrue(panel.contains("host.stationLine(s, range, false)"), "the wrench panel must too, in full");
        final String block = code(read(BLOCK));
        assertTrue(Pattern.compile("class SeismicScannerBlock[^{]*IProxyHoveringInformation[^{]*TunerHost\\.Part")
                .matcher(block).find(), "the upper half must hand the goggle and the tuner to the lower");
        final String newBe = body(block, "public BlockEntity newBlockEntity(");
        assertTrue(newBe != null && newBe.contains("DoubleBlockHalf.LOWER"), "only the lower half carries the machine");
        final String use = body(block, "protected InteractionResult useWithoutItem(");
        assertTrue(use != null && use.contains("SeismicGeometry.onLever(") && use.contains("be.pullLever(player)"),
                "a click pulls the lever only on the lever");
    }

    @Test
    @DisplayName("lang: every key the rig, its block and its targets print is in en_us.json")
    void langKeys() {
        final JsonObject lang = JsonParser.parseString(read(LANG)).getAsJsonObject();
        final List<String> missing = new ArrayList<>();
        final Pattern tr = Pattern.compile("\\btr\\(\"([a-z_.]+)\"");
        for (final String src : List.of(code(read(BE)))) {
            final Matcher m = tr.matcher(src);
            while (m.find()) {
                final String key = "block.create_stats.seismic_scanner." + m.group(1);
                if (!lang.has(key)) {
                    missing.add(key);
                }
            }
        }
        final Pattern literal = Pattern.compile("\"((?:block\\.)?create_stats\\.seismic_scanner[a-z_.]*|"
                + "block\\.create_stats\\.seismic_scanner)\"");
        for (final Path p : List.of(BE, BLOCK, MAIN.resolve("seismic/SeismicTargets.java"))) {
            final Matcher m = literal.matcher(code(read(p)));
            while (m.find()) {
                final String key = m.group(1);
                if (!key.endsWith(".") && !lang.has(key)) {
                    missing.add(key);
                }
            }
        }
        final JsonObject sounds = JsonParser.parseString(read(ASSETS.resolve("sounds.json"))).getAsJsonObject();
        for (final String name : sounds.keySet()) {
            if (name.startsWith("seismic_")) {
                final String sub = sounds.getAsJsonObject(name).get("subtitle").getAsString();
                if (!lang.has(sub)) {
                    missing.add(sub);
                }
            }
        }
        assertTrue(missing.isEmpty(), "keys the rig prints and the lang file lacks (a goggle would print its key): " + missing);
    }

    @Test
    @DisplayName("sounds: every event registered, in sounds.json, and shipped as a file")
    void soundsShip() {
        final String sounds = code(read(MAIN.resolve("registry/ModSounds.java")));
        final JsonObject json = JsonParser.parseString(read(ASSETS.resolve("sounds.json"))).getAsJsonObject();
        final Matcher m = Pattern.compile("SOUNDS\\.register\\(\"(seismic_[a-z_]+)\"").matcher(sounds);
        int n = 0;
        while (m.find()) {
            n++;
            final String name = m.group(1);
            assertTrue(json.has(name), name + " is registered and missing from sounds.json");
            assertTrue(Files.isRegularFile(ASSETS.resolve("sounds/" + name + ".ogg")), name + ".ogg is missing");
        }
        assertEquals(7, n, "the rig's seven one-off sounds: winch, ratchet, pawl, lever, thud, rumble, pen");

        assertTrue(sounds.contains("for (int ticks = 1; ticks <= SeismicCharge.FULL_FALL_TICKS; ticks++)")
                        && sounds.contains("final String name = \"seismic_fall_\" + ticks;") && sounds.contains("SOUNDS.register(name,"),
                "ModSounds must register seismic_fall_1 to seismic_fall_" + SeismicCharge.FULL_FALL_TICKS + " in its loop");
        for (int ticks = 1; ticks <= SeismicCharge.FULL_FALL_TICKS; ticks++) {
            final String name = "seismic_fall_" + ticks;
            assertTrue(json.has(name), name + " is registered and missing from sounds.json");
            assertTrue(Files.isRegularFile(ASSETS.resolve("sounds/" + name + ".ogg")), name + ".ogg is missing");
        }
        assertTrue(!json.has("seismic_whistle") && !Files.exists(ASSETS.resolve("sounds/seismic_whistle.ogg")),
                "the old whistle's event or file is back: an event with no registration, or a file nothing plays");
        final String release = body(code(read(BE)), "private void release(");
        assertNotNull(release, "SeismicScannerBE has no release()");
        assertTrue(release.contains("Mth.clamp(fallTicks, 1, SeismicCharge.FULL_FALL_TICKS) - 1")
                        && release.contains("ModSounds.SEISMIC_FALL.get(clip).get()"),
                "the drop must play the clip for its own fall length: " + release);
    }

    @Test
    @DisplayName("data: mineable, refused by contraptions, coupled by tag, dropped once, crafted, both halves modelled")
    void data() {
        assertTrue(read(RES.resolve("data/minecraft/tags/block/mineable/pickaxe.json")).contains("\"create_stats:seismic_scanner\""));
        assertTrue(read(RES.resolve("data/c/tags/block/relocation_not_supported.json")).contains("\"create_stats:seismic_scanner\""),
                "a contraption must refuse the rig");
        final String coupling = read(RES.resolve("data/create_stats/tags/block/seismic_coupling.json"));
        for (final String ground : List.of("#minecraft:base_stone_overworld", "#minecraft:dirt", "#minecraft:sand",
                "minecraft:gravel", "minecraft:clay", "#minecraft:base_stone_nether", "minecraft:end_stone")) {
            assertTrue(coupling.contains("\"" + ground + "\""), "the coupling tag lacks " + ground);
        }
        assertTrue(code(read(BE)).contains("\"seismic_coupling\""), "the rig must read the coupling tag");
        final String loot = read(RES.resolve("data/create_stats/loot_table/blocks/seismic_scanner.json"));
        assertTrue(loot.contains("\"half\": \"lower\""), "only the lower half drops the rig, or breaking it drops two");
        assertTrue(Files.isRegularFile(RES.resolve("data/create_stats/recipe/seismic_scanner.json")));
        final JsonObject variants = JsonParser.parseString(read(ASSETS.resolve("blockstates/seismic_scanner.json")))
                .getAsJsonObject().getAsJsonObject("variants");
        assertEquals(8, variants.size(), "four facings, two halves");
        for (final String key : variants.keySet()) {
            final String model = variants.getAsJsonObject(key).get("model").getAsString();
            assertTrue(model.endsWith(key.contains("half=upper") ? "/upper" : "/lower"), key + " shows " + model);
        }
    }

    @Test
    @DisplayName("models: every partial exists, and the generator's parts sit where SeismicGeometry poses them")
    void partialsAndGeometry() {
        final String partials = code(read(MAIN.resolve("client/ModPartialModels.java")));
        final Matcher m = Pattern.compile("SEISMIC_[A-Z]+ = block\\(\"(seismic_scanner/[a-z]+)\"\\)").matcher(partials);
        int n = 0;
        while (m.find()) {
            n++;
            assertTrue(Files.isRegularFile(ASSETS.resolve("models/block/" + m.group(1) + ".json")), m.group(1) + " has no model");
        }
        assertEquals(11, n, "the rig's eleven moving parts");
        final Path models = ASSETS.resolve("models/block/seismic_scanner");

        final double[] rope = box(models.resolve("rope.json"), "rope");
        assertEquals(SeismicGeometry.ROPE_LAY, rope[4] - rope[1], 1e-6);
        assertEquals(0.0, rope[1], 1e-6);
        assertEquals(SeismicGeometry.CABLE_X, (rope[0] + rope[3]) / 2.0, 1e-3);
        assertEquals(SeismicGeometry.CABLE_Z, (rope[2] + rope[5]) / 2.0, 1e-3);

        final double[] rim = box(models.resolve("sheave.json"), "rim_f_0");
        assertEquals(SeismicGeometry.SHEAVE_X, (rim[0] + rim[3]) / 2.0, 1e-3);
        assertEquals(SeismicGeometry.SHEAVE_Y, (rim[1] + rim[4]) / 2.0, 1e-3);

        assertEquals(SeismicGeometry.SHEAVE_PITCH_R, (rim[3] - rim[0]) / 2.0 / Math.cos(Math.PI / 16.0), 1e-3);

        final double[] spoke = box(models.resolve("sheave.json"), "spoke_f_h");
        assertEquals(rim[2], spoke[5], 1e-6, "the spokes stand on the rim's outer face, not off it");
        assertEquals(SeismicGeometry.SHEAVE_X, (spoke[0] + spoke[3]) / 2.0, 1e-3);
        assertEquals(SeismicGeometry.SHEAVE_Y, (spoke[1] + spoke[4]) / 2.0, 1e-3);

        assertEquals(0.0, rotationAngle(models.resolve("ratchet.json"), "tooth_0_face"), 1e-9, "the face is radial");
        assertEquals(45.0, rotationAngle(models.resolve("ratchet.json"), "tooth_0_back"), 1e-9,
                "a back sloping the other way would let the weight's pull ride over the pawl");

        assertEquals(SeismicGeometry.WEIGHT_BOTTOM, bound(models.resolve("weight.json"), "body", 1, false), 1e-6);
        assertEquals(SeismicGeometry.EYE_TOP, bound(models.resolve("weight.json"), "eye_top", 1, true), 1e-6);

        final double[] barrel = box(models.resolve("drum.json"), "barrel_0");
        assertEquals(SeismicGeometry.DRUM_Y, (barrel[1] + barrel[4]) / 2.0, 1e-3);
        assertEquals(SeismicGeometry.DRUM_Z, (barrel[2] + barrel[5]) / 2.0, 1e-3);
        assertEquals(SeismicGeometry.LIFT / (2.0 * Math.PI) * Math.cos(Math.PI / 16.0), (barrel[5] - barrel[2]) / 2.0, 1e-3);

        assertTrue(rotationOrigin(models.resolve("lever.json"), "lever_arm",
                new double[] {SeismicGeometry.LEVER_X, SeismicGeometry.LEVER_Y, SeismicGeometry.LEVER_Z}));
        assertTrue(rotationOrigin(models.resolve("pawl.json"), "pawl_arm",
                new double[] {SeismicGeometry.PAWL_X, SeismicGeometry.PAWL_Y, SeismicGeometry.PAWL_Z}));

        final double[] paper = box(models.resolve("paper.json"), "paper_0");
        assertEquals(SeismicGeometry.PAPER_X, (paper[0] + paper[3]) / 2.0, 1e-3);
        assertEquals(SeismicGeometry.PAPER_Z, (paper[2] + paper[5]) / 2.0, 1e-3);
        assertEquals(SeismicGeometry.PAPER_Y0, paper[1], 1e-3);

        final double[] dog = box(models.resolve("tick.json"), "dog_w");
        assertEquals(SeismicGeometry.WEIGHT_BOTTOM + 6.0, dog[1], 1e-3, "the dog's underside is the line the weight's top meets");

        final double[] body = box(models.resolve("weight.json"), "body");
        assertTrue(box(models.resolve("tick.json"), "flag_w")[0] < body[0] - 1.0
                && box(models.resolve("tick.json"), "flag_e")[3] > body[3] + 1.0, "the flags must stand out past the weight");

        final double[] lens = extent(models.resolve("lamp.json"));
        assertTrue(lens[3] - lens[0] >= 2.0 && lens[4] - lens[1] >= 1.8 && lens[5] - lens[2] >= 2.0,
                "the lantern's glass must stay at least 2 px across and 1.8 px tall");
        assertTrue(lens[4] <= 32.0, "nothing stands above the second block");
    }

    @Test
    @DisplayName("models: a moving part whose textures have clear texels draws in cutout, or they draw as solid colour")
    void clearTexelsDrawInCutout() throws IOException {
        final String partials = code(read(MAIN.resolve("client/ModPartialModels.java")));
        final String renderer = code(read(CLIENT.resolve("SeismicScannerRenderer.java")));
        final Matcher m = Pattern.compile("(SEISMIC_[A-Z]+) = block\\(\"seismic_scanner/([a-z]+)\"\\)").matcher(partials);
        int clear = 0;
        int seen = 0;
        while (m.find()) {
            seen++;
            final JsonObject textures = JsonParser.parseString(read(ASSETS.resolve("models/block/seismic_scanner/"
                    + m.group(2) + ".json"))).getAsJsonObject().getAsJsonObject("textures");
            boolean holes = false;
            for (final String key : textures.keySet()) {
                final String ref = textures.get(key).getAsString();
                final Path png = ASSETS.resolve("textures/" + ref.substring(ref.indexOf(':') + 1) + ".png");
                final java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(png.toFile());
                assertNotNull(img, png + " is not a readable PNG");
                for (int y = 0; y < img.getHeight() && !holes; y++) {
                    for (int x = 0; x < img.getWidth() && !holes; x++) {
                        holes = (img.getRGB(x, y) >>> 24) < 26;
                    }
                }
            }
            if (!holes) {
                continue;
            }
            clear++;
            final int at = renderer.indexOf("ModPartialModels." + m.group(1));
            assertTrue(at >= 0, m.group(2) + " has clear texels and the renderer never draws it");
            final int into = renderer.indexOf(".renderInto(ms, ", at);
            final String target = renderer.substring(into + ".renderInto(ms, ".length(), renderer.indexOf(';', into));
            assertTrue(target.startsWith("buffers.getBuffer(RenderType.cutout())") || target.startsWith("cutout)"),
                    m.group(2) + " has clear texels and draws into " + target + ": they would draw as solid colour");
        }

        assertEquals(11, seen, "every moving part's textures were read: " + clear + " with clear texels");
    }

    private static double bound(Path model, String name, int axis, boolean max) {
        final double[] b = box(model, name);
        return max ? b[axis + 3] : b[axis];
    }

    private static double[] box(Path model, String name) {
        final JsonArray elements = JsonParser.parseString(read(model)).getAsJsonObject().getAsJsonArray("elements");
        for (final JsonElement e : elements) {
            final JsonObject o = e.getAsJsonObject();
            if (o.has("__comment") && o.get("__comment").getAsString().equals(name)) {
                final JsonArray f = o.getAsJsonArray("from");
                final JsonArray t = o.getAsJsonArray("to");
                return new double[] {f.get(0).getAsDouble(), f.get(1).getAsDouble(), f.get(2).getAsDouble(),
                        t.get(0).getAsDouble(), t.get(1).getAsDouble(), t.get(2).getAsDouble()};
            }
        }
        throw new AssertionError(model + " has no element " + name);
    }

    private static double[] extent(Path model) {
        final JsonArray elements = JsonParser.parseString(read(model)).getAsJsonObject().getAsJsonArray("elements");
        final double[] b = {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE,
                -Double.MAX_VALUE};
        for (final JsonElement e : elements) {
            final JsonArray f = e.getAsJsonObject().getAsJsonArray("from");
            final JsonArray t = e.getAsJsonObject().getAsJsonArray("to");
            for (int i = 0; i < 3; i++) {
                b[i] = Math.min(b[i], f.get(i).getAsDouble());
                b[i + 3] = Math.max(b[i + 3], t.get(i).getAsDouble());
            }
        }
        return b;
    }

    private static double rotationAngle(Path model, String name) {
        final JsonArray elements = JsonParser.parseString(read(model)).getAsJsonObject().getAsJsonArray("elements");
        for (final JsonElement e : elements) {
            final JsonObject o = e.getAsJsonObject();
            if (o.has("__comment") && o.get("__comment").getAsString().equals(name)) {
                return o.has("rotation") ? o.getAsJsonObject("rotation").get("angle").getAsDouble() : 0.0;
            }
        }
        throw new AssertionError(model + " has no element " + name);
    }

    private static boolean rotationOrigin(Path model, String name, double[] pivot) {
        final JsonArray elements = JsonParser.parseString(read(model)).getAsJsonObject().getAsJsonArray("elements");
        for (final JsonElement e : elements) {
            final JsonObject o = e.getAsJsonObject();
            if (o.has("__comment") && o.get("__comment").getAsString().equals(name) && o.has("rotation")) {
                final JsonArray origin = o.getAsJsonObject("rotation").getAsJsonArray("origin");
                for (int i = 0; i < 3; i++) {
                    if (Math.abs(origin.get(i).getAsDouble() - pivot[i]) > 1e-3) {
                        return false;
                    }
                }
                return true;
            }
        }
        return false;
    }

    private static String branch(String code, String head) {
        final int at = code.indexOf(head);
        if (at < 0) {
            return null;
        }
        final int open = code.indexOf('{', at);
        int depth = 0;
        for (int i = open; i < code.length(); i++) {
            final char c = code.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}' && --depth == 0) {
                return code.substring(open + 1, i);
            }
        }
        return null;
    }

    private static String body(String text, String signature) {
        final String code = code(text);
        final int at = code.indexOf(signature);
        if (at < 0) {
            return null;
        }
        final int open = code.indexOf('{', at);
        int depth = 0;
        for (int i = open; i < code.length(); i++) {
            final char c = code.charAt(i);
            if (c == '"' || c == '\'') {
                i = skipLiteral(code, i, c);
            } else if (c == '{') {
                depth++;
            } else if (c == '}' && --depth == 0) {
                return code.substring(open + 1, i);
            }
        }
        return null;
    }

    private static String code(String s) {
        final StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            final char c = s.charAt(i);
            if (c == '"' || c == '\'') {
                final int close = skipLiteral(s, i, c);
                out.append(s, i, Math.min(s.length(), close + 1));
                i = close;
            } else if (c == '/' && i + 1 < s.length() && s.charAt(i + 1) == '/') {
                final int nl = s.indexOf('\n', i);
                i = nl < 0 ? s.length() : nl - 1;
            } else if (c == '/' && i + 1 < s.length() && s.charAt(i + 1) == '*') {
                final int end = s.indexOf("*/", i + 2);
                i = end < 0 ? s.length() : end + 1;
                out.append(' ');
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    private static int skipLiteral(String s, int start, char quote) {
        for (int i = start + 1; i < s.length(); i++) {
            final char c = s.charAt(i);
            if (c == '\\') {
                i++;
            } else if (c == quote) {
                return i;
            }
        }
        return s.length();
    }

    private static List<Path> javaFiles(Path root) {
        try (Stream<Path> files = Files.walk(root)) {
            return files.filter(p -> p.toString().endsWith(".java")).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String read(Path p) {
        try {
            return Files.readString(p, StandardCharsets.UTF_8).replace("\r\n", "\n");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
