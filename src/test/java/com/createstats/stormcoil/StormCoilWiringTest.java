package com.createstats.stormcoil;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StormCoilWiringTest {
    private static final Path NETWORK = Path.of("src/main/java/com/createstats/power/FeWireNetwork.java");
    private static final Path BE = Path.of("src/main/java/com/createstats/blockentity/StormCoilBE.java");
    private static final Path WIRE_RENDER = Path.of("src/main/java/com/createstats/renderer/PowerConnectorRenderer.java");
    private static final Path WIRE_ITEM = Path.of("src/main/java/com/createstats/item/CopperWireItem.java");
    private static final Path PDU = Path.of("src/main/java/com/createstats/blockentity/PowerDistributionUnitBE.java");

    private static String read(Path p) throws IOException {
        assertTrue(Files.exists(p), "missing " + p);
        return Files.readString(p, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    private static String block(String s, int at) {
        final int open = s.indexOf('{', at);
        assertTrue(open >= 0, "no block after " + at);
        int depth = 0;
        for (int i = open; i < s.length(); i++) {
            if (s.charAt(i) == '{') {
                depth++;
            } else if (s.charAt(i) == '}' && --depth == 0) {
                return s.substring(open + 1, i);
            }
        }
        throw new AssertionError("unbalanced braces after " + at);
    }

    private static String method(String s, String signature) {
        final int at = s.indexOf(signature);
        assertTrue(at >= 0, "method not found: " + signature);
        return block(s, at);
    }

    @Test
    @DisplayName("the walk attaches the coil by stud and never walks its links")
    void walkBranch() throws IOException {
        final String net = read(NETWORK);
        final int at = net.indexOf("node instanceof com.createstats.blockentity.StormCoilBE coil");
        assertTrue(at >= 0, "the walk has no Storm Coil branch: a wire onto a stud would carry nothing");
        final String body = block(net, at);
        assertTrue(body.contains("new FeAttachment(") && body.contains("coil.portFor(stud)"),
                "the coil joins a circuit as an FeAttachment on the port of the stud the wire landed on\n" + body);
        assertTrue(body.contains("coil.rateNode(stud)"),
                "the attachment reads the port's rating live (rateOf, INV-30)\n" + body);
        assertTrue(body.contains("coil.studOf("), "the stud is asked of the coil's own link list\n" + body);
        assertFalse(body.contains("walkTerminalLinks(coil") || body.contains("spreadFrom"),
                "the coil's own links must never be walked: that would join its two circuits through it\n" + body);
    }

    @Test
    @DisplayName("a wire reaching a stud enqueues the coil before the stud-panel stop")
    void studBranch() throws IOException {
        final String net = read(NETWORK);
        final int walk = net.indexOf("private static void walkTerminalLinks(FeWireTerminal terminal, Level level, Walk walk, @Nullable BlockPos only)");
        assertTrue(walk >= 0, "walkTerminalLinks moved");
        final String body = block(net, walk);
        final int coil = body.indexOf("linkBE instanceof com.createstats.blockentity.StormCoilBE");
        final int stop = body.indexOf("if (linkBE instanceof FeStudPanel) {");
        assertTrue(coil >= 0, "walkTerminalLinks has no Storm Coil branch");
        assertTrue(stop >= 0 && coil < stop, "the coil's branch must come before the stud-panel stop, which would end "
                + "the walk at it and leave its wires carrying nothing");
        assertTrue(block(body, coil).contains("walk.enqueue("), "the coil's branch must enqueue it");
    }

    @Test
    @DisplayName("a connector bolted to the coil does not drop its stud attachment")
    void dedupeExempt() throws IOException {
        final String net = read(NETWORK);
        final int remove = net.indexOf("attachments.remove(host);");
        assertTrue(remove >= 0, "the connector-host dedupe moved");
        final int cond = net.lastIndexOf("if (", remove);
        final String statement = net.substring(cond, remove);
        assertTrue(statement.contains("StormCoilBE"),
                "the dedupe must exempt the coil as it does the breaker\n" + statement);
    }

    @Test
    @DisplayName("the coil is a priced stud panel, never a member or a terminal")
    void declaration() throws IOException {
        final String be = read(BE);
        final Matcher m = Pattern.compile("public\\s+class\\s+StormCoilBE\\b[^{]*\\{", Pattern.DOTALL).matcher(be);
        assertTrue(m.find(), "declaration not found");
        final String decl = m.group();
        assertTrue(decl.contains("FeStudPanel"), "a stud panel");
        assertTrue(decl.contains("FeSupply"), "OUT puts FE on a circuit, so a board must be able to price it");
        assertFalse(decl.contains("FeGridMember"), "a member would be walked through and join its two circuits");
        assertFalse(decl.contains("FeWireTerminal"), "one link list for the block could not say which stud");
    }

    @Test
    @DisplayName("the coil draws its own wires, and the wire item knows its studs")
    void drawnAndClicked() throws IOException {
        assertTrue(method(read(WIRE_RENDER), "public static boolean isStudPanel(").contains("StormCoilBlock"),
                "the far end would draw a coil's wire from the middle of the block");
        final String use = method(read(WIRE_ITEM), "public static InteractionResult useWith(");
        final int dispatch = use.indexOf("instanceof com.createstats.block.StormCoilBlock");
        assertTrue(dispatch >= 0 && block(use, dispatch).contains("useOnStormCoil("),
                "the wire item must pick IN or OUT from the hit");
    }

    @Test
    @DisplayName("a board prices the coil by what it states and talks to it as a station (KI-193)")
    void boardSeesTheCoil() throws IOException {
        final String be = read(BE);
        final Matcher m = Pattern.compile("public\\s+class\\s+StormCoilBE\\b[^{]*\\{", Pattern.DOTALL).matcher(be);
        assertTrue(m.find(), "declaration not found");
        final String decl = m.group().replaceAll("//[^\\n]*|/\\*.*?\\*/", "");
        assertTrue(decl.contains("FeRatedLoad"), "without a stated price a board learns one off the capacitor, and an"
                + " arc empties 160,000 FE of it in a tick");
        assertTrue(decl.contains("FeStationLoad"), "without being a station the coil can be the load that trips its leg");
        final String rated = method(be, "public int ratedDrawFePerTick(");
        assertTrue(rated.contains("stores.ratedDraw(") && rated.contains("CoilLaw.engaged("),
                "the price is the pure rule's, fights included\n" + rated);
        assertTrue(method(be, "public void acceptBoardFeed(").contains("boardFeed = feed"),
                "the board's report must be kept\n");
        assertTrue(method(be, "private void syncReadouts(").contains("boardFeed.fresh("),
                "a report is believed only while fresh (INV-06)");
        final String pdu = read(PDU);
        final String profile = method(pdu, "private com.createstats.power.FeDeviceDemand profileOf(FeGridNode node)");
        assertTrue(profile.contains("FeDeviceDemand.learned(learned, node.transferRate()"),
                "a learned price is held to what the node carries in a tick\n" + profile);
        assertFalse(profile.contains("FeDeviceDemand.observed("), "the uncapped observation is back\n" + profile);
        assertTrue(pdu.contains("Map.entry(\"storm_coil\", FePriority.HIGH)"),
                "ASSIGN gives the coil the Mob Repeller's tier (KI-71's scar is a missing row)");
    }

    @Test
    @DisplayName("every field the goggles print rides the client packet (INV-15)")
    void goggleSync() throws IOException {
        final String be = read(BE);
        final String goggle = method(be, "public boolean addToGoggleTooltip(") + method(be, "private void addBoardLines(");
        final String write = method(be, "protected void write(CompoundTag tag");
        final String readBack = method(be, "protected void read(CompoundTag tag");
        final Map<String, String> keys = new LinkedHashMap<>();
        keys.put("syncedCapacitor", "Capacitor");
        keys.put("syncedCapacitorSize", "CapacitorSize");
        keys.put("syncedBank", "Bank");
        keys.put("syncedBankSize", "BankSize");
        keys.put("syncedIn", "InDraw");
        keys.put("syncedOut", "OutExport");
        keys.put("blockedAt", "BlockedAt");
        keys.put("blocker", "Blocker");
        keys.put("redstone", "Redstone");
        keys.put("shared", "Shared");
        keys.put("corona", "Corona");
        keys.put("lastStrike", "LastStrike");
        keys.put("strikes", "Strikes");
        keys.put("stage", "Stage");
        keys.put("syncedBoardStud", "BoardStud");
        keys.put("syncedBoardLive", "BoardLive");
        keys.put("syncedBoardGive", "BoardGive");
        keys.put("syncedBoardCarried", "BoardCarried");
        keys.put("syncedBoardCost", "BoardCost");
        keys.put("syncedBoardReason", "BoardReason");
        final Matcher used = Pattern.compile("\\b(synced[A-Z]\\w*|blockedAt|blocker|redstone|shared|corona|lastStrike"
                + "|strikes|stage)\\b").matcher(goggle);
        int seen = 0;
        while (used.find()) {
            final String field = used.group(1);
            seen++;
            assertTrue(keys.containsKey(field), "the goggles print " + field + ", which this test has no key for");
            final String key = "\"" + keys.get(field) + "\"";
            assertTrue(write.contains(key), field + " is printed by the goggles and never written as " + key);
            final Matcher assigned = Pattern.compile("\\b" + field + "\\s*=[^;]*" + Pattern.quote(key)).matcher(readBack);
            final boolean restored = assigned.find()
                    || (field.equals("syncedCapacitor") || field.equals("syncedBank")) && readBack.contains(key);
            assertTrue(restored, field + " is written and never read back from " + key + " on the client");
        }
        assertTrue(seen >= 8, "the goggle scan found only " + seen + " fields; it has stopped working");
    }
}
