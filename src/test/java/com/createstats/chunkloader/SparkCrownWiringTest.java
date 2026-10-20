package com.createstats.chunkloader;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SparkCrownWiringTest {
    private static final Path MAIN = Path.of("src/main/java/com/createstats");

    private static String read(String relative) throws IOException {
        final Path p = MAIN.resolve(relative);
        assertTrue(Files.exists(p), "missing source: " + p);

        return Files.readString(p, StandardCharsets.UTF_8).replace("\r\n", "\n")
                .replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("//[^\\n]*", " ");
    }

    private static String window(String text, String start, String end) {
        final int at = text.indexOf(start);
        assertTrue(at >= 0, "not found, so this test guards nothing: " + start);
        final int stop = text.indexOf(end, at + start.length());
        return text.substring(at, stop < 0 ? text.length() : stop);
    }

    @Test
    @DisplayName("the walk branch makes it a member and walks out along its home input only, back face or one stud (KI-178)")
    void theWalkBranch() throws IOException {
        final String net = read("power/FeWireNetwork.java");
        final String branch = window(net, "node instanceof com.createstats.blockentity.SparkCrownBE", "node instanceof");
        assertTrue(branch.contains("found.add(crown)"), "the branch does not make it a member: " + branch);
        assertTrue(branch.contains("spreadFromFace(level, crown.getBlockPos(), crown.backFace(), walk)"),
                "power in on the back face and nowhere else (the owner's one-side rule): " + branch);
        assertTrue(!branch.contains("spreadFromBlock("), "a six-face spread would take power on every face: " + branch);
        assertTrue(branch.contains("walk.isOrigin(crown.getBlockPos())"),
                "walked out of when the walk merely reached it: a junction between whatever its inputs land on: " + branch);
        assertTrue(branch.contains("final BlockPos home = crown.homeLink();")
                        && branch.contains("walkTerminalLinks(crown, level, walk, home)"),
                "its home stud is never walked, so a station flooded from itself has no circuit: " + branch);
        assertTrue(!branch.contains("walkTerminalLinks(crown, level, walk)"),
                "every stud walked: two grids joined through the station, which is KI-178: " + branch);
    }

    @Test
    @DisplayName("a run against the back face reaches it as a parallel station; against any other face it does not")
    void theSpreadBranch() throws IOException {
        final String net = read("power/FeWireNetwork.java");
        final String branch = window(net, "be instanceof com.createstats.blockentity.SparkCrownBE crown", "continue;");
        assertTrue(branch.contains("crown.takesPowerOn(dir.getOpposite())"),
                "the face asked must be the crown's own face touching the run: " + branch);
        assertTrue(branch.contains("reachParallel(crown, be, neighbour, at, null, back,"),
                "found and never reached: a run on its back draws an arm and feeds nothing: " + branch);
        assertTrue(!branch.contains("walk.enqueue("),
                "put on the tree by any run against it, whatever its home: a junction again (KI-178): " + branch);
    }

    @Test
    @DisplayName("the capability is guarded and answers on the back face only, the same question the walk asks")
    void theCapability() throws IOException {
        final String caps = read("power/FeCapabilities.java");
        final String reg = window(caps, "ModBlockEntities.SPARK_CROWN.get()", ";");
        assertTrue(reg.contains("guard((be, side) -> be.takesPowerOn(side) ? be.energyHandle() : null)"),
                "unguarded, or on faces the walk does not take power on: " + reg);
        final String be = read("blockentity/SparkCrownBE.java");
        final String takes = window(be, "public boolean takesPowerOn(", "}");
        assertTrue(takes.contains("side == null || side == backFace()"), "the one face rule moved: " + takes);
    }

    @Test
    @DisplayName("its own tick builds and solves its circuit, and its supply is counted in its own handler")
    void itSolvesItsOwnCircuit() throws IOException {
        final String be = read("blockentity/SparkCrownBE.java");
        final String tick = window(be, "public void tick()", "private void firstTick(");
        assertTrue(tick.contains("FeWireNetwork.buildFrom(this)"), "KI-46: a member that never floods its own circuit");
        assertTrue(tick.contains("network.solveOnce(level)"), "KI-46: a member that never solves its own circuit");
        final String handler = window(be, "public int receiveEnergy(", "public int extractEnergy(");
        assertTrue(handler.contains("hold.accept("),
                "what arrives must be counted in the station's own store, never read off the circuit: " + handler);
        final String pool = window(be, "public int bufferCapacity()", "}");
        assertTrue(pool.contains("return 0;"), "INV-18: the store is behind attachedStorage, the node holds none");
    }

    @Test
    @DisplayName("a board is born with it at Critical (AUTO_TIERS has no scan of its own, KI-71)")
    void theBoardTier() throws IOException {
        final String pdu = read("blockentity/PowerDistributionUnitBE.java");
        final String tiers = window(pdu, "private static final Map<String, FePriority> AUTO_TIERS", ");\n");
        assertTrue(Pattern.compile("Map\\.entry\\(\\s*\"spark_crown\"\\s*,\\s*FePriority\\.CRITICAL\\s*\\)")
                .matcher(tiers).find(), "no CRITICAL row: ASSIGN resets a chunk loader to the middle of the list");
        final String be = read("blockentity/SparkCrownBE.java");
        assertTrue(be.contains("managed.setPriority(FePriority.CRITICAL)"), "a fresh station is not born Critical");
    }

    @Test
    @DisplayName("a wire lands on its back face only, and the station draws its own wires from the right stud")
    void theWireFaceAndTheDrawing() throws IOException {
        final String wire = read("item/CopperWireItem.java");
        final String faceOf = window(wire, "private static Direction wireFaceOf(", "private static boolean takesWireOn(");
        assertTrue(faceOf.contains("block instanceof com.createstats.block.SparkCrownBlock")
                        && faceOf.contains("SparkCrownBlock.wireFace(state)"),
                "a wire may be clicked on any face of the crown: " + faceOf);
        final String renderer = read("renderer/PowerConnectorRenderer.java");
        final String panel = window(renderer, "public static boolean isStudPanel(", "\n    }");
        assertTrue(panel.contains("block instanceof com.createstats.block.SparkCrownBlock"),
                "a connector would draw a span to the middle of the bay instead of to its stud: " + panel);
    }

    @Test
    @DisplayName("a click with anything in hand passes, so Copper Wire still lands on the studs")
    void aHeldItemGetsItsTurn() throws IOException {
        final String block = read("block/SparkCrownBlock.java");
        final String use = window(block, "public static InteractionResult use(", "\n    }");
        final int gate = use.indexOf("if (!player.getMainHandItem().isEmpty())");
        assertTrue(gate >= 0, "no empty-hand gate: a block's use runs before the held item's, so a player stringing"
                + " a wire to the studs would pick a chunk instead: " + use);
        final int pass = use.indexOf("return InteractionResult.PASS;", gate);
        final int controls = use.indexOf("CrownScreenAccess.openMap(pos)");
        assertTrue(pass > gate && controls > pass, "the gate must pass before the map is opened: " + use);
    }

    @Test
    @DisplayName("its ticket controller is registered, and its validator is the one that keeps a holding station's chunks")
    void theTicketController() throws IOException {
        final String tickets = read("chunkloader/CrownTickets.java");
        final Matcher controller = Pattern.compile("new TicketController\\(\\s*ResourceLocation\\.fromNamespaceAndPath\\("
                + "\\s*CreateStats\\.MOD_ID\\s*,\\s*\"chunk_station\"\\s*\\)\\s*,\\s*CrownTickets::validate\\s*\\)")
                .matcher(tickets);
        assertTrue(controller.find(), "the controller is not create_stats:chunk_station with its own validator");
        final String register = window(tickets, "public static void register(RegisterTicketControllersEvent event)", "}");
        assertTrue(register.contains("event.register(CONTROLLER)"),
                "unregistered: NeoForge throws on the first forceChunk and drops every saved ticket at load");
        assertTrue(tickets.contains("@EventBusSubscriber(modid = CreateStats.MOD_ID, bus = EventBusSubscriber.Bus.MOD)"),
                "the registration handler is on no bus");
        final String validate = window(tickets, "static void validate(", "public static boolean hold(");
        assertTrue(validate.contains("registry.keepsTicket(dimension, owner.asLong(), chunk)"),
                "the validator does not ask the registry: " + validate);
        assertTrue(validate.contains("helper.removeTicket(owner, chunk, true)"),
                "a ticket the registry does not vouch for is never dropped: " + validate);
    }
}
