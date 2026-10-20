package com.createstats.contactor;

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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContactorWiringTest {
    private static final Path MAIN = Path.of("src/main/java/com/createstats");
    private static final Path ASSETS = Path.of("src/main/resources/assets/create_stats");
    private static final Path DATA = Path.of("src/main/resources/data/create_stats");

    private static String read(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8).replace("\r\n", "\n");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String code(String text) {
        return text.replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("//[^\\n]*", " ");
    }

    private static String methodBody(String source, String signature) {
        final int at = source.indexOf(signature);
        assertTrue(at >= 0, "not found, so this test guards nothing: " + signature);
        final int open = source.indexOf('{', at);
        int depth = 0;
        for (int i = open; i < source.length(); i++) {
            final char c = source.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}' && --depth == 0) {
                return source.substring(at, i + 1);
            }
        }
        throw new AssertionError("unbalanced braces after " + signature);
    }

    private static String statement(String source, String anchor) {
        final int at = source.indexOf(anchor);
        assertTrue(at >= 0, "not found: " + anchor);
        return source.substring(at, source.indexOf(';', at) + 1);
    }

    private static JsonObject json(Path path) {
        return JsonParser.parseString(read(path)).getAsJsonObject();
    }

    @Test
    @DisplayName("it gives no redstone at all: no source, no weak signal, no strong one")
    void noRedstoneOut() {
        final String block = code(read(MAIN.resolve("block/ContactorBlock.java")));
        assertTrue(methodBody(block, "protected boolean isSignalSource(").contains("return false;"));
        assertTrue(methodBody(block, "protected int getSignal(").contains("return 0;"));
        assertTrue(methodBody(block, "protected int getDirectSignal(").contains("return 0;"));
    }

    @Test
    @DisplayName("the coils read only the left and right neighbours, and dust bends into only those sides")
    void coilsReadTheirOwnSides() {
        final String block = code(read(MAIN.resolve("block/ContactorBlock.java")));
        final String coil = methodBody(block, "public static boolean coil(");
        assertTrue(coil.contains("side(state, side)") && coil.contains("hasSignal(pos.relative(toward), toward)"));
        assertFalse(block.contains("hasNeighborSignal"), "a contactor that reads every neighbour has no left or right");
        final String dust = methodBody(block, "public boolean canConnectRedstone(");
        assertTrue(dust.contains("LEFT_SIDE") && dust.contains("getOpposite()"));
    }

    @Test
    @DisplayName("the armatures follow their coils through the dwell: decided by step, a wait re-reads, moves are stamped")
    void theDwellIsKept() {
        final String block = code(read(MAIN.resolve("block/ContactorBlock.java")));
        final String follow = methodBody(block, "public void followCoils(");
        assertTrue(follow.contains("ContactorLogic.step("), "followCoils must decide with ContactorLogic.step");
        assertTrue(follow.contains("scheduleTick(pos, this, wait)"), "a wait must schedule its re-read");
        assertTrue(follow.contains("recordMove(side, now)"), "a move must stamp its armature's dwell");
        assertTrue(follow.contains("invalidatePoles("), "a move must tell the grids its poles join");
        assertTrue(methodBody(block, "protected void tick(").contains("followCoils("));
        assertTrue(methodBody(block, "protected void onPlace(").contains("scheduleTick("));
        assertFalse(methodBody(block, "protected void onPlace(").contains("followCoils("),
                "never inside onPlace: the block entity does not exist yet");
        final String form = methodBody(block, "public boolean setForm(");
        assertTrue(form.contains("invalidatePoles(") && form.contains("recordMove("));
    }

    @Test
    @DisplayName("the server ticks its block entity and the client ticks the hum, each on the contactor's own type")
    void theTickers() {
        final String block = code(read(MAIN.resolve("block/ContactorBlock.java")));
        final String ticker = methodBody(block, "BlockEntityTicker<T> getTicker(");
        assertTrue(ticker.contains("ModBlockEntities.CONTACTOR.get()"));
        assertTrue(ticker.contains("ContactorBE.serverTick(") && ticker.contains("ContactorBE.clientTick("));
        final String be = code(read(MAIN.resolve("blockentity/ContactorBE.java")));
        assertTrue(methodBody(be, "private void tickServer(").contains("followCoils("),
                "the first tick after a load must re-read the coils");
        assertTrue(methodBody(be, "public static void clientTick(").contains("ContactorHumSound.keep("));
    }

    @Test
    @DisplayName("broken, it gives its wires back; removed, it tells their grids")
    void wiresComeBack() {
        final String block = code(read(MAIN.resolve("block/ContactorBlock.java")));
        assertTrue(methodBody(block, "protected void onRemove(").contains("dropAllStudWires()"));
        final String be = code(read(MAIN.resolve("blockentity/ContactorBE.java")));
        assertTrue(methodBody(be, "public void setRemoved(").contains("invalidateLinkedTerminals()"));
    }

    @Test
    @DisplayName("its own block entity type is registered with its renderer, and it is off the switches' list")
    void registered() {
        final String reg = code(read(MAIN.resolve("registry/ModBlockEntities.java")));
        final String contactor = statement(reg, "CONTACTOR = CreateStats.registrate().blockEntity(\"contactor\"");
        assertTrue(contactor.contains("ContactorBE::new"));
        assertTrue(contactor.contains("ModBlocks.CONTACTOR.get()"));
        assertTrue(contactor.contains("ContactorRenderer::new"));
        final String sw = statement(reg, "SWITCH = CreateStats.registrate().blockEntity(\"switch\"");
        assertFalse(sw.contains("CONTACTOR"), "a contactor on the switch type would get a SwitchBE and none of this");
        final String block = code(read(MAIN.resolve("block/ContactorBlock.java")));
        assertTrue(methodBody(block, "public BlockEntity newBlockEntity(").contains("new ContactorBE("));
        assertFalse(block.contains("extends SwitchBlock"), "no longer a plate: it joins no cable run");
    }

    @Test
    @DisplayName("every partial the renderer draws is registered, and every registered one exists on disk")
    void partials() {
        final String partials = code(read(MAIN.resolve("client/ModPartialModels.java")));
        final String renderer = code(read(MAIN.resolve("renderer/ContactorRenderer.java")));
        final Matcher used = Pattern.compile("ModPartialModels\\.(CONTACTOR_[A-Z_]+)").matcher(renderer);
        final Set<String> names = new LinkedHashSet<>();
        while (used.find()) {
            names.add(used.group(1));
        }
        assertTrue(names.size() >= 10, "only " + names + " found; the scan has stopped reading the renderer");
        for (String name : names) {
            final Matcher decl = Pattern.compile(name + " = block\\(\"([^\"]+)\"\\)").matcher(partials);
            assertTrue(decl.find(), name + " is drawn and never registered");
            final Path model = ASSETS.resolve("models/block/" + decl.group(1) + ".json");
            assertTrue(Files.isRegularFile(model), name + " points at " + model + ", which is not there");
        }
    }

    @Test
    @DisplayName("the client ticks, draws and clears the guidance boxes")
    void theHintIsHooked() {
        final String events = code(read(MAIN.resolve("client/ClientEvents.java")));
        assertTrue(methodBody(events, "public static void onClientTick(").contains("ContactorBindHint.tick()"));
        assertTrue(methodBody(events, "public static void onRenderGui(").contains("ContactorBindHint.renderHud("));
        assertTrue(methodBody(events, "public static void onWorldUnload(").contains("ContactorBindHint.clear()"));
        final String hint = code(read(MAIN.resolve("client/ContactorBindHint.java")));
        assertTrue(methodBody(hint, "private static int aimedStud(").contains("ContactorStuds.pick("),
                "the box and the click must ask the same question");
    }

    @Test
    @DisplayName("the flood walks through a made pole to its pair's wire, rates the edge, and stops at an open one")
    void theWalk() {
        final String net = code(read(MAIN.resolve("power/FeWireNetwork.java")));
        final String walk = methodBody(net, "private static void walkTerminalLinks(");
        final int branch = walk.indexOf("linkBE instanceof com.createstats.blockentity.ContactorBE contactor");
        assertTrue(branch >= 0, "no Contactor branch in walkTerminalLinks: a wire onto a stud would carry nothing");
        final int stop = walk.indexOf("linkBE instanceof FeStudPanel");
        assertTrue(stop > branch, "the Contactor branch must come before the stud panels' stop, or the flood stops first");
        final String body = walk.substring(branch, stop);
        assertTrue(body.contains("contactor.studOf(from)"), "the pole must come from the stored link");
        assertTrue(body.contains("contactor.throughFrom(stud)"), "only the stud's own pair may be followed");
        assertTrue(body.contains("walk.enqueue(from, far.to(), farBE)"));
        assertTrue(body.contains("walk.ratePassThrough(far.to(), passThroughRate("), "the pass-through edge must be rated");
        assertTrue(body.contains("terminal.removeDeadLink(link)"), "a wire the contactor no longer holds is dead");
        final String be = code(read(MAIN.resolve("blockentity/ContactorBE.java")));
        final String through = methodBody(be, "public List<StudLink> throughFrom(");
        assertTrue(through.contains("ContactorBlock.poleClosed(") && through.contains("ContactorLogic.pairedStud("));
    }

    @Test
    @DisplayName("the wire item finds the stud with the guidance boxes' own pick, and a stud panel draws its own wires")
    void theWireItem() {
        final String item = code(read(MAIN.resolve("item/CopperWireItem.java")));
        assertTrue(methodBody(item, "public static InteractionResult useWith(")
                .contains("useOnContactor(context, level, pos, player, tier, stock, strip)"));
        final String on = methodBody(item, "private static InteractionResult useOnContactor(");
        assertTrue(on.contains("ContactorStuds.pick("));
        assertTrue(on.contains("connect(context, player, level, PduStudWiring.Endpoint.stud("));
        assertTrue(on.contains("dropStudWires(stud)"));
        assertTrue(on.contains("refuseBridgeFromStud(player)"));
        final String renderer = code(read(MAIN.resolve("renderer/PowerConnectorRenderer.java")));
        assertTrue(methodBody(renderer, "public static boolean isStudPanel(").contains("ContactorBlock"),
                "the far end would draw every span again, from the middle of the contactor");
    }

    @Test
    @DisplayName("one wire a stud, saved and synced; the loads ride the update tag and never the save")
    void theStudsPersist() {
        final String be = code(read(MAIN.resolve("blockentity/ContactorBE.java")));
        assertTrue(be.contains("MAX_LINKS_PER_STUD = 1;"));
        assertTrue(methodBody(be, "protected void saveAdditional(").contains("tag.put(\"StudWires\""));
        assertFalse(methodBody(be, "protected void saveAdditional(").contains("\"Load\""));
        assertTrue(methodBody(be, "public CompoundTag getUpdateTag(").contains("tag.putIntArray(\"Load\""));
        assertTrue(methodBody(be, "protected void loadAdditional(").contains("tag.getList(\"StudWires\""));
        assertTrue(methodBody(be, "private void syncStudLinks(").contains("sendBlockUpdated("));
        assertTrue(methodBody(be, "public void writeSettings(").contains("\"NormallyClosed\""));
        assertTrue(methodBody(be, "public String settingsKey(").contains("\"create_stats:contactor\""));
    }

    private static List<double[][]> elements(String model, String name) {
        final List<double[][]> out = new ArrayList<>();
        for (JsonElement e : json(ASSETS.resolve("models/block/contactor/" + model + ".json")).getAsJsonArray("elements")) {
            final JsonObject el = e.getAsJsonObject();
            if (el.has("__comment") && el.get("__comment").getAsString().equals(name)) {
                out.add(new double[][] {vec(el.getAsJsonArray("from")), vec(el.getAsJsonArray("to"))});
            }
        }
        assertFalse(out.isEmpty(), "no element " + name + " in " + model);
        return out;
    }

    private static double[] vec(JsonArray a) {
        return new double[] {a.get(0).getAsDouble(), a.get(1).getAsDouble(), a.get(2).getAsDouble()};
    }

    private static double[] view(double[][] box) {
        return new double[] {16.0 - box[1][0], box[0][2], box[0][1], 16.0 - box[0][0], box[1][2], box[1][1]};
    }

    @Test
    @DisplayName("the Java geometry is the shipped models': studs, contacts, rest positions, pivots, pilots")
    void geometryMatchesTheModels() {
        final double eps = 1e-3;

        for (int stud = 0; stud < ContactorLogic.STUDS; stud++) {
            final String name = (ContactorLogic.isLine(stud) ? "line" : "load") + "_screw_" + ContactorLogic.poleOf(stud);
            final double[] v = view(elements("base", name).get(0));
            final double[] a = ContactorGeometry.studAnchor(stud);
            assertEquals((v[0] + v[3]) / 2.0, a[0], eps, name);
            assertEquals((v[1] + v[4]) / 2.0, a[1], eps, name);
            assertEquals(v[5], a[2], eps, name);
        }

        final double[] arm = view(elements("base", "arm_0_l").get(0));
        assertEquals(ContactorGeometry.ARM_Y0, arm[1], eps);
        assertEquals(ContactorGeometry.ARM_Y1, arm[4], eps);
        final double[] no = view(elements("bridge_no", "bridge").get(0));
        assertEquals(ContactorGeometry.ARM_Y1 + ContactorGeometry.NO_GAP, no[1], eps);
        final double[] nc = view(elements("bridge_nc", "bridge").get(0));
        assertEquals(ContactorGeometry.ARM_Y0, nc[4], eps);

        final double[] armature = view(elements("carrier", "armature").get(0));
        final double[] leg = view(elements("base", "leg_0_0").get(0));
        assertEquals(leg[4] + ContactorGeometry.STROKE, armature[1], eps);

        final double[] pin0 = view(elements("evener", "pin_0").get(0));
        final double[] pin1 = view(elements("evener", "pin_1").get(0));
        final double[] pin2 = view(elements("evener", "pin_2").get(0));
        final double[] bar = view(elements("evener", "bar").get(0));
        assertEquals((pin1[0] + pin1[3]) / 2.0, ContactorGeometry.BAR_PIVOT_X, eps);
        assertEquals((bar[1] + bar[4]) / 2.0, ContactorGeometry.BAR_PIVOT_Y, eps);
        assertEquals((bar[2] + bar[5]) / 2.0, ContactorGeometry.BAR_PIVOT_Z, eps);
        assertEquals((pin2[0] + pin2[3]) / 2.0 - (pin0[0] + pin0[3]) / 2.0, ContactorGeometry.BAR_SPAN, eps);

        final double[] flag = view(elements("flag", "flag").get(0));
        assertEquals((flag[1] + flag[4]) / 2.0, ContactorGeometry.FLAG_PIVOT_Y, eps);
        assertEquals((flag[2] + flag[5]) / 2.0, ContactorGeometry.FLAG_PIVOT_Z, eps);
        assertEquals(ContactorGeometry.POLE_X[0], (flag[0] + flag[3]) / 2.0, eps);

        final double[] lens = view(elements("pilot", "lens").get(0));
        assertEquals(ContactorGeometry.PILOT_X0, lens[0], eps);
        assertEquals(ContactorGeometry.PILOT_X1, lens[3], eps);

        assertEquals(ContactorGeometry.UNIT_PITCH, ContactorGeometry.POLE_X[2] - ContactorGeometry.POLE_X[0], eps);
    }

    @Test
    @DisplayName("the blockstate names the base for every face and turn, and the item is a 3D block icon")
    void blockstateAndItem() {
        final JsonObject variants = json(ASSETS.resolve("blockstates/contactor.json")).getAsJsonObject("variants");
        assertEquals(24, variants.size());
        final JsonObject item = json(ASSETS.resolve("models/item/contactor.json"));
        assertEquals("create_stats:block/contactor/item", item.get("parent").getAsString());
        final JsonObject gui = json(ASSETS.resolve("models/block/contactor/item.json"))
                .getAsJsonObject("display").getAsJsonObject("gui");
        final JsonArray rot = gui.getAsJsonArray("rotation");
        assertEquals(30, rot.get(0).getAsInt(), "the inventory shows it in vanilla's block turn, (30, 225)");
        assertEquals(225, rot.get(1).getAsInt());
        assertEquals(0, rot.get(2).getAsInt());
    }

    @Test
    @DisplayName("every texture a contactor model names is shipped, and none of the first build's art is left behind")
    void textures() {
        final Path dir = ASSETS.resolve("models/block/contactor");
        final Pattern ref = Pattern.compile("\"create_stats:block/(contactor/[a-z_]+)\"");
        try (var files = Files.list(dir)) {
            for (Path model : files.toList()) {
                final Matcher m = ref.matcher(read(model));
                while (m.find()) {
                    assertTrue(Files.isRegularFile(ASSETS.resolve("textures/block/" + m.group(1) + ".png")),
                            model + " names " + m.group(1));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        assertFalse(Files.exists(ASSETS.resolve("models/block/contactor_base.json")), "the first build's base is still there");
        assertFalse(Files.exists(ASSETS.resolve("textures/block/contactor_hood.png")), "the first build's hood is still there");
    }

    @Test
    @DisplayName("its sounds, loot, recipe and every lang key it prints are shipped")
    void shipped() {
        final JsonObject sounds = json(ASSETS.resolve("sounds.json"));
        for (String s : new String[] {"contactor_pull_in", "contactor_drop_out", "contactor_hum"}) {
            assertTrue(sounds.has(s), s + " missing from sounds.json");
            assertTrue(Files.isRegularFile(ASSETS.resolve("sounds/" + s + ".ogg")), s + ".ogg missing");
        }
        assertTrue(Files.isRegularFile(DATA.resolve("loot_table/blocks/contactor.json")));
        assertTrue(Files.isRegularFile(DATA.resolve("recipe/contactor.json")));
        final JsonObject lang = json(ASSETS.resolve("lang/en_us.json"));
        final Pattern key = Pattern.compile("\"((?:create_stats|message\\.create_stats|block\\.create_stats)\\.contactor[a-z_.0-9]*)\"");
        final Set<String> keys = new LinkedHashSet<>();
        for (String file : new String[] {"block/ContactorBlock.java", "blockentity/ContactorBE.java",
                "client/ContactorBindHint.java", "item/CopperWireItem.java"}) {
            final Matcher m = key.matcher(read(MAIN.resolve(file)));
            while (m.find()) {
                keys.add(m.group(1));
            }
        }

        keys.add("message.create_stats.contactor.pole.0");
        keys.add("message.create_stats.contactor.pole.1");
        keys.add("message.create_stats.contactor.pole.2");
        assertTrue(keys.size() >= 15, "only " + keys + ": the scan has stopped reading the sources");
        for (String k : keys) {
            if (k.endsWith(".")) {
                continue;
            }
            assertTrue(lang.has(k), "lang key missing: " + k);
        }
        for (String suffix : new String[] {"", ".tooltip", ".tooltip.placement", ".tooltip.usage", ".tooltip.shift"}) {
            assertNotNull(lang.get("block.create_stats.contactor" + suffix), "block.create_stats.contactor" + suffix);
        }
    }
}
