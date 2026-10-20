package com.createstats.security.relay;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SecurityRelayWiringTest {
    private static final Path ROOT = Path.of("src/main/java/com/createstats");
    private static final Path NETWORK = ROOT.resolve("power/FeWireNetwork.java");
    private static final Path CAPS = ROOT.resolve("power/FeCapabilities.java");
    private static final Path BE = ROOT.resolve("blockentity/SecurityRelayBE.java");
    private static final Path BLOCK = ROOT.resolve("block/SecurityRelayBlock.java");
    private static final Path PDU = ROOT.resolve("blockentity/PowerDistributionUnitBE.java");
    private static final Path KEYPAD = ROOT.resolve("network/PanelKeypadPacket.java");
    private static final Path ACTION = ROOT.resolve("network/SecurityRelayActionPacket.java");

    private static String code(Path p) throws IOException {
        return Files.readString(p, StandardCharsets.UTF_8).replace("\r\n", "\n")
                .replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("//[^\\n]*", " ");
    }

    private static String body(String source, String signature) {
        final int at = source.indexOf(signature);
        assertTrue(at >= 0, "not found, so this test guards nothing: " + signature);
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
        throw new IllegalStateException("unbalanced braces after " + signature);
    }

    private static String statementWith(String source, String needle) {
        final int at = source.indexOf(needle);
        assertTrue(at >= 0, "not found: " + needle);
        int start = at;
        while (start > 0 && source.charAt(start - 1) != ';' && source.charAt(start - 1) != '}'
                && source.charAt(start - 1) != '{') {
            start--;
        }
        int end = at;
        while (end < source.length() && source.charAt(end) != ';' && source.charAt(end) != '{') {
            end++;
        }
        return source.substring(start, Math.min(source.length(), end + 1));
    }

    @Test
    @DisplayName("The flood walks the relay as a member, from any face, bores included")
    void walkBranch() throws IOException {
        final String net = code(NETWORK);
        final int at = net.indexOf("node instanceof com.createstats.blockentity.SecurityRelayBE relay");
        assertTrue(at >= 0, "no walk branch: a relay would charge nothing, forever");

        final int next = net.indexOf("} else if", at);
        final String branch = net.substring(at, next < 0 ? net.length() : next);
        assertTrue(branch.contains("found.add(relay)"), "the branch must make it a member: " + branch);
        assertTrue(branch.contains("spreadFromBlock(level, relay.getBlockPos(), walk, true)"),
                "the flood must spread on from it on every face: " + branch);
        assertTrue(branch.contains("spreadAlongBores(level, relay, walk)"), "bores: " + branch);
    }

    @Test
    @DisplayName("A cable beside the relay reaches it: it is in the every-face group and the bore group")
    void faceAndBoreGroups() throws IOException {
        final String net = code(NETWORK);

        final String group = statementWith(net, "if (be instanceof com.createstats.blockentity.PaSpeakerBE");
        assertTrue(group.contains("SecuritySensorBE") && group.contains("SecurityRelayBE"),
                "the every-face group the sensors sit in must name the relay: " + group);

        final String bores = body(net, "private static void spreadAlongBores(");
        assertTrue(statementWith(bores, "be instanceof com.createstats.blockentity.SecurityRelayBE")
                        .contains("be instanceof CableBE"),
                "a bore landing on a relay must be walked like one landing on a sensor");
        assertTrue(net.contains("public static FeWireNetwork buildFrom(com.createstats.blockentity.SecurityRelayBE origin)"),
                "the relay's buildFrom overload is missing");
    }

    @Test
    @DisplayName("The capability is registered, guarded, for every face")
    void capability() throws IOException {
        final String caps = code(CAPS);
        final String reg = statementWith(caps, "ModBlockEntities.SECURITY_RELAY.get()");
        assertTrue(reg.contains("Capabilities.EnergyStorage.BLOCK") && reg.contains("guard((be, side) -> be.energyHandle())"),
                "the relay's energy capability must be registered through guard: " + reg);
    }

    @Test
    @DisplayName("The relay builds and solves its own circuit (KI-46), and prices its peak to a board")
    void selfSolvesAndPricesPeak() throws IOException {
        final String tick = body(code(BE), "public void tick()");
        assertTrue(tick.contains("FeWireNetwork.buildFrom(this)"), "the relay must build its own circuit");
        assertTrue(tick.contains("network.solveOnce(level)"), "and solve it, or it is solved only by a neighbour");
        assertTrue(tick.contains("ratedDraw = RelayPower.rated(managed.isForcedOff())"),
                "a board must be quoted the peak, standby plus coil");
        assertTrue(tick.contains("RelayPower.step(energy, managed.isSuppressed()"),
                "a shed or locked-off relay must run nothing");
        final String pdu = code(PDU);
        assertTrue(pdu.contains("Map.entry(\"security_relay\", FePriority.CRITICAL)"),
                "the board's ASSIGN table must put a relay at Critical, as its constructor does");
    }

    @Test
    @DisplayName("Weak power on the back face only, never strong")
    void weakBackFaceOnly() throws IOException {
        final String block = code(BLOCK);
        final String signal = body(block, "protected int getSignal(");
        assertTrue(signal.contains("direction != state.getValue(FACING)") && signal.contains("return 0;"),
                "getSignal must answer only the face behind the post: " + signal);
        final String direct = body(block, "protected int getDirectSignal(");
        assertTrue(direct.replaceAll("\\s+", "").equals("return0;"),
                "getDirectSignal must be 0: a relay does not power through the wall behind it");
        assertTrue(body(block, "public PushReaction getPistonPushReaction(").contains("SecurityImmobility.REACTION"),
                "a relay is immobile like every security block");
    }

    @Test
    @DisplayName("Only the household mutates a subscription")
    void subscriptionBoundary() throws IOException {
        final Pattern mutator = Pattern.compile("\\.(apply|setState|moveTo|setReading)\\(");
        final List<String> wrong = new ArrayList<>();
        try (Stream<Path> files = Files.walk(ROOT)) {
            for (Path p : (Iterable<Path>) files.filter(f -> f.toString().endsWith(".java"))::iterator) {
                final String name = p.getFileName().toString();
                if (name.equals("SecurityHousehold.java") || name.equals("RelaySubscription.java")) {
                    continue;
                }
                final String src = code(p);
                if (!src.contains("RelaySubscription")) {
                    continue;
                }
                final Matcher m = mutator.matcher(src);
                while (m.find()) {
                    final String before = src.substring(Math.max(0, m.start() - 40), m.start());
                    if (before.matches("(?s).*\\b(sub|subscription|s|copy|loaded)\\s*$")) {
                        wrong.add(name + ": " + src.substring(Math.max(0, m.start() - 40), m.end()).trim());
                    }
                }
            }
        }
        assertTrue(wrong.isEmpty(), "a subscription mutated outside SecurityHousehold: " + wrong);
    }

    @Test
    @DisplayName("SEC-10: a password house can open a relay: keypad raised, digits routed, admission kept alive")
    void faceplateAdmission() throws IOException {
        final String use = body(code(BLOCK), "protected InteractionResult useWithoutItem(");
        final int admits = use.indexOf("if (relay.admits(sp))");
        assertTrue(admits >= 0, "the admission branch moved; this test guards nothing");
        final String refused = body(use.substring(admits), "} else");
        assertTrue(refused.contains("relay.askPassword(sp)"),
                "a player the house asks for its code must get the keypad, not a dead end: " + refused);
        final String routed = body(code(KEYPAD), "be instanceof com.createstats.blockentity.SecurityRelayBE relay");
        assertTrue(routed.contains("relay.offerPassword(sender, packet.code())"),
                "digits typed at a relay's keypad must reach the relay: " + routed);
        final String handle = body(code(ACTION), "public static void handle(");
        final int refusal = handle.indexOf("if (!relay.admits(sender))");
        final int kept = handle.indexOf("relay.refreshAdmission(sender);");
        final int acts = handle.indexOf("switch (packet.action())");
        assertTrue(refusal >= 0 && acts > refusal, "the faceplate branch moved; this test guards nothing");
        final int open = handle.indexOf('{', refusal);
        final int close = open + 1 + body(handle.substring(refusal), "if (!relay.admits(sender))").length();
        assertTrue(kept > close && kept < acts,
                "the faceplate's poll must refresh its admission once admitted, or it shuts three seconds in");
    }
}
