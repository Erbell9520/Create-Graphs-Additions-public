package com.createstats.vccmk4;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

class Vcc4WiringTest {
    private static final Path PACKETS = Path.of("src/main/java/com/createstats/network/ModPackets.java");
    private static final Path MIXINS = Path.of("src/main/resources/create_stats.mixins.json");
    private static final Path BLOCKS = Path.of("src/main/java/com/createstats/registry/ModBlocks.java");
    private static final Path BLOCK_ENTITIES = Path.of("src/main/java/com/createstats/registry/ModBlockEntities.java");
    private static final Path TAB = Path.of("src/main/java/com/createstats/registry/ModCreativeTab.java");
    private static final Path[] CONSOLE = {
            Path.of("src/main/java/com/createstats/vccmk4/console/Vcc4Console.java"),
            Path.of("src/main/java/com/createstats/vccmk4/console/BoardView.java")};
    private static final Path LANG = Path.of("src/main/resources/assets/create_stats/lang/en_us.json");
    private static final Path RECIPE = Path.of("src/main/resources/data/create_stats/recipe/vcc_mk4.json");
    private static final Path ASSETS = Path.of("src/main/resources/assets/create_stats");

    private static final Path BLOCK_ENTITY = Path.of("src/main/java/com/createstats/vccmk4/Vcc4BE.java");
    private static final Path SCANNER = Path.of("src/main/java/com/createstats/vccmk4/Vcc4Scanner.java");

    private static final Pattern CLASSIFY = Pattern.compile("private static void classify\\([^)]*\\) \\{(.*?)\\n    \\}", Pattern.DOTALL);

    private static final Path MIXIN_DIR = Path.of("src/main/java/com/createstats/mixin");
    private static final Pattern WHEEL_MOUNT_TARGET = Pattern.compile("@Mixin\\([^)]*\"dev\\.ryanhcode\\.offroad\\.content\\.blocks\\.wheel_mount\\.WheelMountBlockEntity\"");

    private static final Pattern REDIRECT_ANNOTATION = Pattern.compile("(?m)^\\s*@(?:org\\.spongepowered\\.asm\\.mixin\\.injection\\.)?Redirect\\b");

    private static final Pattern LANG_KEY =Pattern.compile("\"([^\"]+)\"\\s*:");

    private static final Pattern SCREEN_KEY = Pattern.compile("\\btr\\(\"([a-z0-9_.]+)\"");

    private static final Pattern SILENCE_CLOSES_THROTTLE = Pattern.compile(
            "boolean (\\w+) = now - lastInputTick > INPUT_TIMEOUT_TICKS;\\s*"
                    + "boolean (\\w+) = coDriver == null \\|\\| now - coLastInputTick > INPUT_TIMEOUT_TICKS;[^}]*?"
                    + "boolean (\\w+) = \\1 && \\2;[^}]*?"
                    + "if \\(\\3(?: && !routeDriving)?\\) drive\\.closeThrottle\\(\\);[^}]*?driveTick\\(");

    @Test
    void everyWiringSiteIsPresent() throws IOException {
        List<String> problems = new ArrayList<>();
        String packets = read(PACKETS);
        for (String payload : new String[]{"Vcc4SnapshotPacket", "Vcc4HelmPacket", "Vcc4InputPacket", "Vcc4ActionPacket"}) {
            if (!packets.contains(payload + ".TYPE")) {
                problems.add("ModPackets does not register " + payload + "; the first send would throw");
            }
        }
        if (!read(MIXINS).contains("\"WheelMountBrakeMixin\"")) {
            problems.add("create_stats.mixins.json does not list WheelMountBrakeMixin; the brake key would do nothing");
        }
        if (!read(MIXINS).contains("\"RapierReadPoseGuardMixin\"")) {
            problems.add("create_stats.mixins.json does not list RapierReadPoseGuardMixin; driving away from other bodies would abort the game again (Sable #1553)");
        }
        if (!read(BLOCKS).contains("registrate().block(\"vcc_mk4\"")) problems.add("ModBlocks does not register vcc_mk4");
        if (!read(BLOCK_ENTITIES).contains("blockEntity(\"vcc_mk4\"")) problems.add("ModBlockEntities does not register vcc_mk4");
        if (!read(TAB).contains("ModBlocks.VCC_MK4::asItem")) problems.add("ModCreativeTab does not list VCC_MK4");

        for (String asset : new String[]{"blockstates/vcc_mk4.json", "models/item/vcc_mk4.json", "models/block/bta_g1.json",
                "models/item/bta_g1.json", "textures/block/bta_g1_shell.png", "textures/block/bta_g1_front.png",
                "textures/block/bta_g1_cluster.png", "textures/block/bta_g1_glass.png"}) {
            if (!Files.isRegularFile(ASSETS.resolve(asset))) problems.add("missing asset " + asset);
        }
        if (!Files.isRegularFile(RECIPE) || !read(RECIPE).contains("\"create_stats:vcc_mk4\"")) {
            problems.add("recipe vcc_mk4.json is missing or does not make create_stats:vcc_mk4");
        }
        if (!SILENCE_CLOSES_THROTTLE.matcher(read(BLOCK_ENTITY)).find()) {
            problems.add("Vcc4BE does not close the throttle when the driver's keys stop arriving: a crashed client would ease off over the release");
        }

        Matcher classify = CLASSIFY.matcher(read(SCANNER));
        if (!classify.find()) {
            problems.add("Vcc4Scanner.classify was not found; this scan has drifted from how it is declared");
        } else {
            String body = classify.group(1);
            int lookup = body.indexOf("Vcc4Jets.of(");
            int branch = body.indexOf("direct != null");
            int links = body.indexOf("instanceof SmartBlockEntity");

            if (lookup < 0 || branch < 0 || links < 0 || lookup > branch || branch > links) {
                problems.add("Vcc4Scanner.classify does not take a direct jet (Vcc4Jets.of, then its branch) before the SmartBlockEntity link fallback: thrusters would be scanned as lights");
            }
        }

        Set<String> lang = new TreeSet<>();
        Matcher lm = LANG_KEY.matcher(read(LANG));
        while (lm.find()) lang.add(lm.group(1));
        StringBuilder console = new StringBuilder();
        for (Path p : CONSOLE) console.append(read(p)).append('\n');
        Matcher sm = SCREEN_KEY.matcher(console);
        Set<String> checked = new TreeSet<>();
        while (sm.find()) {
            String suffix = sm.group(1);
            if (!checked.add(suffix)) continue;
            String key = "gui.create_stats.vcc_mk4." + suffix;
            if (suffix.endsWith(".")) {
                if (lang.stream().noneMatch(k -> k.startsWith(key))) problems.add("no en_us.json key under " + key);
            } else if (!lang.contains(key)) {
                problems.add("The console uses " + key + " which is not in en_us.json, so it prints raw");
            }
        }
        assertTrue(checked.size() > 60, "Found only " + checked.size() + " console keys; SCREEN_KEY has drifted from how the console names them");
        assertTrue(problems.isEmpty(), "VCC Mk IV wiring:\n  " + String.join("\n  ", problems));
    }

    @Test
    void wheelMountMixinsNeverRedirect() throws IOException {
        List<Path> wheelMount = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        try (Stream<Path> files = Files.list(MIXIN_DIR)) {
            for (Path p : (Iterable<Path>) files.sorted()::iterator) {
                String src = read(p);
                if (!WHEEL_MOUNT_TARGET.matcher(src).find()) continue;
                wheelMount.add(p);
                if (REDIRECT_ANNOTATION.matcher(src).find()) {
                    problems.add(p.getFileName() + " uses @Redirect on Offroad's wheel mount; with Drive By Wire installed Create Aeronautics will not load");
                }
            }
        }

        assertTrue(wheelMount.size() >= 3, "Found only " + wheelMount + "; WHEEL_MOUNT_TARGET has drifted from how the mixins name their target");
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    private static String read(Path p) throws IOException {
        return Files.readString(p, StandardCharsets.UTF_8);
    }
}
