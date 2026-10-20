package com.createstats.vccmk4.air;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AerialWiringTest {
    private static final Path AIR = Path.of("src/main/java/com/createstats/vccmk4/air");
    private static final Path INPUT = Path.of("src/main/java/com/createstats/vccmk4/network/Vcc4InputPacket.java");
    private static final Path BLOCKS = Path.of("src/main/java/com/createstats/registry/ModBlocks.java");
    private static final Path BLOCK_ENTITIES = Path.of("src/main/java/com/createstats/registry/ModBlockEntities.java");
    private static final Path TAB = Path.of("src/main/java/com/createstats/registry/ModCreativeTab.java");
    private static final Path LANG = Path.of("src/main/resources/assets/create_stats/lang/en_us.json");
    private static final Path ASSETS = Path.of("src/main/resources/assets/create_stats");
    private static final Path DATA = Path.of("src/main/resources/data/create_stats");

    private static Pattern declares(String type, String iface) {
        return Pattern.compile("\\bclass " + type + "\\b[^{]*\\b" + iface + "\\b[^{]*\\{");
    }

    private static final Pattern DISPATCH = Pattern.compile(
            "instanceof HelmConsole (\\w+)\\)\\s*\\1\\.acceptInput\\(");

    private static final Pattern TICKER = Pattern.compile(
            "getTicker\\([^)]*\\)\\s*\\{[^}]*ConsoleFault\\.tick\\((\\w+), \\(\\(AerialBE\\) \\1\\)::serverTick\\)");

    @Test
    void everyWiringSiteIsPresent() throws IOException {
        List<String> problems = new ArrayList<>();
        String block = read(AIR.resolve("AerialBlock.java"));
        String be = read(AIR.resolve("AerialBE.java"));
        if (!declares("AerialBlock", "AerialTelemetryBlock").matcher(block).find()) {
            problems.add("AerialBlock does not implement AerialTelemetryBlock: no Display Link would offer the Aerial");
        }
        if (!declares("AerialBE", "AerialTelemetrySource").matcher(be).find()) {
            problems.add("AerialBE does not implement AerialTelemetrySource: a linked Stats Display would show nothing");
        }
        if (!declares("AerialBE", "HelmConsole").matcher(be).find()) {
            problems.add("AerialBE does not implement HelmConsole: the pilot's keys would be dropped");
        }
        if (!DISPATCH.matcher(read(INPUT)).find()) {
            problems.add("Vcc4InputPacket does not hand keys to any HelmConsole: the Aerial would never hear them");
        }
        if (!TICKER.matcher(block).find()) problems.add("AerialBlock's ticker does not run AerialBE.serverTick through ConsoleFault");
        if (!read(BLOCKS).contains("registrate().block(\"vcc_mk4_aerial\"")) problems.add("ModBlocks does not register vcc_mk4_aerial");
        if (!read(BLOCK_ENTITIES).contains("blockEntity(\"vcc_mk4_aerial\"")) problems.add("ModBlockEntities does not register vcc_mk4_aerial");
        if (!read(TAB).contains("ModBlocks.VCC_MK4_AERIAL::asItem")) problems.add("ModCreativeTab does not list VCC_MK4_AERIAL");

        for (String asset : new String[]{"blockstates/vcc_mk4_aerial.json", "models/block/vcc_mk4_aerial_cockpit/body.json",
                "models/item/vcc_mk4_aerial.json", "models/block/vcc_mk4_aerial_cockpit/item.json",
                "textures/block/vcc_mk4_aerial_cockpit/body.png", "textures/block/vcc_mk4_aerial_cockpit/lume_upper.png",
                "textures/block/vcc_mk4_aerial_cockpit/lume_lower.png", "textures/block/vcc_mk4_aerial_cockpit/yoke.png"}) {
            if (!Files.isRegularFile(ASSETS.resolve(asset))) problems.add("missing asset " + asset);
        }
        Path loot = DATA.resolve("loot_table/blocks/vcc_mk4_aerial.json");
        if (!Files.isRegularFile(loot) || !read(loot).contains("\"create_stats:vcc_mk4_aerial\"")) {
            problems.add("loot table vcc_mk4_aerial.json is missing or drops something else: the block would drop nothing");
        }
        Path recipe = DATA.resolve("recipe/vcc_mk4_aerial.json");
        if (!Files.isRegularFile(recipe) || !read(recipe).contains("\"create_stats:vcc_mk4_aerial\"")) {
            problems.add("recipe vcc_mk4_aerial.json is missing or makes something else");
        }
        String lang = read(LANG);
        for (String suffix : new String[]{"", ".tooltip", ".tooltip.placement", ".tooltip.usage"}) {
            String key = "\"block.create_stats.vcc_mk4_aerial" + suffix + "\":";
            if (!lang.contains(key)) problems.add("en_us.json has no " + key);
        }
        assertTrue(problems.isEmpty(), "VCC Mk IV Aerial wiring:\n  " + String.join("\n  ", problems));
    }

    private static final Path PACKETS = Path.of("src/main/java/com/createstats/network/ModPackets.java");
    private static final Path NET = Path.of("src/main/java/com/createstats/vccmk4/network");
    private static final Path BRIDGE = Path.of("src/main/java/com/createstats/vccmk4/Vcc4ClientBridge.java");
    private static final Path CLIENT = Path.of("src/main/java/com/createstats/vccmk4/client");

    private static Pattern registers(String direction, String packet) {
        String q = "(?:com\\.createstats\\.vccmk4\\.network\\.)?" + packet;
        return Pattern.compile("registrar\\." + direction + "\\(\\s*" + q + "\\.TYPE,\\s*" + q + "\\.STREAM_CODEC,\\s*" + q
                + "::handle\\);");
    }

    private static final Pattern VERSION = Pattern.compile("\\.versioned\\(\"(\\d+)\"\\)");
    private static final Pattern ACTION_DISPATCH = Pattern.compile("instanceof AerialBE (\\w+)\\)\\s*\\1\\.handleAction\\(");
    private static final Pattern BRIDGE_HOOK = Pattern.compile(
            "static void aerialView\\(AerialViewPacket \\w+\\)\\s*\\{\\s*invoke\\(\"aerialView\"");

    private static final Pattern CLIENT_HOOK = Pattern.compile(
            "public static void aerialView\\(AerialViewPacket \\w+\\)\\s*\\{((?!\\n    \\}).)*new AerialScreen\\(", Pattern.DOTALL);
    private static final Pattern PASSES_KEYS = Pattern.compile(
            "mc\\.screen instanceof HelmScreen (\\w+) && \\1\\.pos\\(\\)\\.equals\\(helm\\) && \\1\\.passesKeys\\(\\)");
    private static final Pattern OPENS = Pattern.compile("\\} else \\{\\s*aerial\\.openFor\\(sp\\);\\s*\\}");

    @Test
    void theConsoleIsWiredEndToEnd() throws IOException {
        List<String> problems = new ArrayList<>();
        String packets = read(PACKETS);
        if (!registers("playToClient", "AerialViewPacket").matcher(packets).find()) {
            problems.add("ModPackets does not register AerialViewPacket to the client: the first view would drop the player");
        }
        if (!registers("playToServer", "AerialActionPacket").matcher(packets).find()) {
            problems.add("ModPackets does not register AerialActionPacket to the server: every click would drop the player");
        }
        var version = VERSION.matcher(packets);
        if (!version.find() || Integer.parseInt(version.group(1)) < 47) {
            problems.add("the payload channel's version is not past 46, the last without the Aerial's console");
        }
        if (!ACTION_DISPATCH.matcher(read(NET.resolve("AerialActionPacket.java"))).find()) {
            problems.add("AerialActionPacket does not hand clicks to AerialBE.handleAction: the console would change nothing");
        }
        if (!BRIDGE_HOOK.matcher(read(BRIDGE)).find()) problems.add("Vcc4ClientBridge has no aerialView hook");
        if (!CLIENT_HOOK.matcher(read(CLIENT.resolve("Vcc4ClientHooks.java"))).find()) {
            problems.add("Vcc4ClientHooks.aerialView does not open an AerialScreen: the console would never show");
        }
        if (!declares("AerialScreen", "HelmScreen").matcher(read(CLIENT.resolve("AerialScreen.java"))).find()) {
            problems.add("AerialScreen is not a HelmScreen: the ship would stop answering its keys while the console is open");
        }
        if (!PASSES_KEYS.matcher(read(CLIENT.resolve("Vcc4ClientInput.java"))).find()) {
            problems.add("Vcc4ClientInput does not pass keys through an open HelmScreen");
        }
        if (!OPENS.matcher(read(AIR.resolve("AerialBlock.java"))).find()) problems.add("a plain use of the Aerial does not open its console");
        assertTrue(problems.isEmpty(), "VCC Mk IV Aerial console wiring:\n  " + String.join("\n  ", problems));
    }

    private static final String[] REACHES = {"handsOff(", "tools.release(", "lift.handBack(", "props.release(", "jets.release("};

    @Test
    void anUnloadingChunkIsNeverReachedInto() throws IOException {
        String be = read(AIR.resolve("AerialBE.java"));
        List<String> problems = new ArrayList<>();
        Matcher unload = Pattern.compile("public void onChunkUnloaded\\(\\)\\s*\\{([^}]*)\\}").matcher(be);
        if (!unload.find() || !unload.group(1).contains("unloading = true;")) problems.add("onChunkUnloaded does not set unloading");
        String body = body(be, "public void setRemoved()");
        if (body == null) {
            problems.add("AerialBE has no setRemoved");
        } else {
            int guard = body.indexOf("if (!unloading) {");
            int end = guard < 0 ? -1 : body.indexOf("\n            }", guard);
            if (guard < 0 || end < 0) problems.add("setRemoved has no if (!unloading) { ... } around its hand-back");
            for (String reach : REACHES) {
                for (int at = body.indexOf(reach); at >= 0; at = body.indexOf(reach, at + 1)) {
                    if (guard < 0 || at < guard || at > end) problems.add("setRemoved calls " + reach + ") outside if (!unloading)");
                }
            }
            int hands = body.indexOf("handsOff(");
            if (guard >= 0 && (hands < guard || hands > end)) problems.add("a broken Aerial no longer hands everything back");
        }
        assertTrue(problems.isEmpty(), "VCC Mk IV Aerial unloading:\n  " + String.join("\n  ", problems));
    }

    private static final Pattern WAKES = Pattern.compile(
            "if \\(command\\.mode\\(\\)\\.flying\\(\\)\\) AerialReflect\\.wake\\(server, SableBridge\\.serverSubLevelOf\\(this\\)\\);");
    private static final Pattern MOORS = Pattern.compile(
            "if \\(c\\.mode\\(\\) == AerialPilot\\.Mode\\.MOORED\\) moor\\(server, now\\);\\s*else handsOff\\(server\\);");

    @Test
    void theShipIsKeptAwakeInFlightAndItsBurnersBankedWhenMoored() throws IOException {
        String be = read(AIR.resolve("AerialBE.java"));
        List<String> problems = new ArrayList<>();
        String tick = body(be, "public void serverTick()");
        if (tick == null || !WAKES.matcher(tick).find()) problems.add("serverTick does not wake the ship while it flies it");
        String act = body(be, "private void act(");
        if (act == null || !MOORS.matcher(act).find()) problems.add("act does not moor a MOORED command (it would hand the burners back)");
        String moor = body(be, "private void moor(");
        if (moor == null || !moor.contains("lift.bank(server, now);")) problems.add("moor does not bank the burners");
        else if (moor.contains("lift.handBack(")) problems.add("moor hands the burners back to the player's redstone");
        if (!read(AIR.resolve("AerialLift.java")).contains("void bank(ServerLevel level, long tick)")) problems.add("AerialLift has no bank");
        assertTrue(problems.isEmpty(), "VCC Mk IV Aerial flight wiring:\n  " + String.join("\n  ", problems));
    }

    @Test
    void theThrustersAreFlownAndHandedBack() throws IOException {
        String be = read(AIR.resolve("AerialBE.java"));
        List<String> problems = new ArrayList<>();
        String readBody = body(be, "private Reading read(");
        if (readBody == null || !readBody.contains("actuators.addAll(jets.actuators(frame, setup));")) {
            problems.add("read does not put the thrusters in the split");
        }
        String act = body(be, "private void act(");
        if (act == null || !act.contains("jets.drive(server,")) problems.add("act does not drive the thrusters");

        if (act == null || !act.contains("jets.windowed(r.actuators, governors, setup.feel);")
                || !act.contains("ThrustSplit.solve(reach,")) {
            problems.add("act does not solve inside the thrusters' ramp windows");
        }
        String hands = body(be, "private void handsOff(");
        if (hands == null || !hands.contains("jets.settle(server, controllerId, setup.feel);")) problems.add("handsOff does not wind the thrusters down and hand them back");
        String moor = body(be, "private void moor(");
        if (moor == null || !moor.contains("jets.settle(server, controllerId, setup.feel);")) problems.add("moor does not wind the thrusters down and hand them back");
        String removed = body(be, "public void setRemoved()");
        if (removed == null || !removed.contains("jets.release(server, controllerId);")) problems.add("a broken Aerial does not let its thrusters go at once");
        String jets = read(AIR.resolve("AerialJets.java"));
        if (!jets.contains("Vcc4DirectJets.command(level, j.pos, owner, j.command)")) {
            problems.add("AerialJets does not command its thrusters through the ground Mk IV's leases");
        }
        if (!jets.contains("j.level = JetRamp.toward(j.level, asked, feel);")) problems.add("AerialJets does not walk its thrusters at the Feel's pace");
        assertTrue(problems.isEmpty(), "VCC Mk IV Aerial thrusters:\n  " + String.join("\n  ", problems));
    }

    @Test
    void theStaffIsAskedAndGoToFliesToTheCompass() throws IOException {
        String be = read(AIR.resolve("AerialBE.java"));
        List<String> problems = new ArrayList<>();
        String tick = body(be, "public void serverTick()");
        if (tick == null) {
            problems.add("AerialBE has no serverTick");
        } else {
            int asked = tick.indexOf("staff = AerialReflect.staff(server, SableBridge.serverSubLevelOf(this));");
            int told = tick.indexOf("pilot.staff(staff != AerialReflect.STAFF_NONE);");
            int nav = tick.indexOf("nav = navDestination(server);");
            int flies = tick.indexOf("command = pilot.tick(r.sense, keys(), nav.xz());");
            if (asked < 0 || told < 0) problems.add("serverTick does not ask Simulated whether a staff holds the ship");
            if (nav < 0) problems.add("serverTick does not work out where Go to flies");
            if (flies < 0) problems.add("the pilot is not given the compass's destination");
            if (flies >= 0 && (told > flies || nav > flies)) problems.add("the staff or the destination is set after the pilot has flown the tick");
        }
        if (be.contains("SableBridge.navTableTarget(")) problems.add("the Aerial still reads the table's raw target");
        String dest = body(be, "private NavDestination navDestination(");
        if (dest == null || !dest.contains("DataComponents.LODESTONE_TRACKER") || !dest.contains("SableBridge.navTableProjectedTarget(be)")) {
            problems.add("navDestination does not read the compass's lodestone and the table's projected target");
        }
        assertTrue(problems.isEmpty(), "VCC Mk IV Aerial staff and Go to wiring:\n  " + String.join("\n  ", problems));
    }

    @Test
    void aGunIsKnownByItsMount() {
        java.util.function.Function<List<String>, com.createstats.vccmk4.Vcc4Scanner.Machine> machine = names ->
                new com.createstats.vccmk4.Vcc4Scanner.Machine(net.minecraft.core.BlockPos.ZERO, names, false, 0, java.util.Set.of());
        assertTrue(AerialBE.turnsAGun(machine.apply(List.of("block.createbigcannons.cannon_mount_extension", "block.createbigcannons.cannon_mount"))));
        assertTrue(AerialBE.turnsAGun(machine.apply(List.of("block.createbigcannons.fixed_cannon_mount"))));
        assertTrue(!AerialBE.turnsAGun(machine.apply(List.of("block.create.rope_pulley", "block.create.mechanical_drill"))));
        assertTrue(!AerialBE.turnsAGun(machine.apply(List.of())));
    }

    private static String body(String src, String signature) {
        int at = src.indexOf(signature);
        if (at < 0) return null;
        int open = src.indexOf('{', at);
        int close = open < 0 ? -1 : src.indexOf("\n    }", open);
        return close < 0 ? null : src.substring(open, close);
    }

    private static String read(Path p) throws IOException {
        return Files.readString(p, StandardCharsets.UTF_8);
    }
}
