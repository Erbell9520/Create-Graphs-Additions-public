package com.createstats.power;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Main Breaker grid walk")
class MainBreakerWalkTest {
    private static final Path NETWORK = Path.of("src/main/java/com/createstats/power/FeWireNetwork.java");
    private static final Path BREAKER =
            Path.of("src/main/java/com/createstats/blockentity/MainBreakerBE.java");

    private static String network() throws IOException {
        return Files.readString(NETWORK, StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("the wire walk answers a Main Breaker before it stops at a stud panel")
    void breakerIsAnsweredBeforeTheStudPanelStop() throws IOException {
        final String network = network();
        final int walk = network.indexOf("private static void walkTerminalLinks(");
        assertTrue(walk >= 0, "walkTerminalLinks has been renamed; this test can no longer find it");
        final int breaker = network.indexOf("MainBreakerBE breaker", walk);
        final int studPanelStop = network.indexOf("linkBE instanceof FeStudPanel", walk);
        assertTrue(breaker >= 0,
                "walkTerminalLinks has no Main Breaker branch, so every wire onto one is enqueued as "
                        + "nothing and carries nothing");
        assertTrue(studPanelStop >= 0, "the stud panel stop has moved; this test can no longer find it");
        assertTrue(breaker < studPanelStop,
                "the Main Breaker branch must come BEFORE the general stud panel stop. A breaker is "
                        + "not driven from outside like a distribution panel; it is a boundary the "
                        + "circuit that found it has to reach. Behind the stop it carries nothing, "
                        + "silently and for ever.");
    }

    @Test
    @DisplayName("only a closed breaker is enqueued, which is the whole mechanism of the handle")
    void onlyAClosedBreakerIsWalked() throws IOException {
        final String network = network();
        final int walk = network.indexOf("private static void walkTerminalLinks(");
        final int breaker = network.indexOf("MainBreakerBE breaker", walk);
        final int studPanelStop = network.indexOf("linkBE instanceof FeStudPanel", walk);

        assertTrue(breaker >= 0 && studPanelStop > breaker,
                "the Main Breaker branch is missing or sits behind the stud panel stop");
        final String branch = network.substring(breaker, studPanelStop);
        assertTrue(branch.contains("breaker.isClosed()"),
                "the Main Breaker branch must enqueue only while the contacts are made. Without that "
                        + "gate the flood crosses an open breaker, both sides stay one circuit, and "
                        + "throwing the handle does nothing at all.");
    }

    @Test
    @DisplayName("a breaker joins the grid as an attachment on one stud's port, never as a member")
    void breakerIsAnAttachmentOnTheRightPort() throws IOException {
        final String breakerSource = Files.readString(BREAKER, StandardCharsets.UTF_8);
        final Matcher declaration = Pattern
                .compile("public\\s+class\\s+MainBreakerBE\\b[^{]*\\{", Pattern.DOTALL)
                .matcher(breakerSource);
        assertTrue(declaration.find(), "MainBreakerBE's declaration could not be read");
        assertTrue(!declaration.group().contains("FeGridMember"),
                "the Main Breaker must not be an FeGridMember. A member is walked THROUGH, which "
                        + "fuses the two circuits the block exists to keep apart, and its buffer -- "
                        + "energy in transit across the contacts -- would be counted as one side's "
                        + "reserve.");

        final String network = network();
        final int branch = network.indexOf("MainBreakerBE breaker) {");
        assertTrue(branch >= 0, "the walk has no Main Breaker branch");

        final int chainEnd = network.indexOf("// A machine already served by a connector", branch);
        assertTrue(chainEnd > branch, "the walk's shape changed; this test can no longer find its end");
        final String body = network.substring(branch, chainEnd);
        assertTrue(body.contains("new FeAttachment("),
                "the Main Breaker branch must join the grid as an FeAttachment");
        assertTrue(body.contains("breaker.portFor("),
                "the attachment must be built on the port for the stud the wire actually landed on. "
                        + "Serving both circuits through one handler would make INPUT and OUTPUT "
                        + "mean the same thing.");
        assertTrue(body.contains("breaker.studOf("),
                "which stud a wire is on can only come from the breaker's own link list: the far end "
                        + "can be in any direction from either lug, so there is nothing in the world "
                        + "to re-derive it from.");
        assertTrue(!body.contains("walkTerminalLinks(breaker"),
                "the Main Breaker branch must not walk its own links: the other stud's wires belong "
                        + "to the other circuit, and following them puts both sides on one grid.");
    }

    @Test
    @DisplayName("a passthrough breaker is walked through, once, and only into plain terminals")
    void passthroughCarriesTheFloodAcross() throws IOException {
        final String network = network();
        final int branch = network.indexOf("MainBreakerBE breaker) {");
        assertTrue(branch >= 0, "the walk has no Main Breaker branch");
        final int chainEnd = network.indexOf("// A machine already served by a connector", branch);
        assertTrue(chainEnd > branch, "the walk's shape changed; this test can no longer find its end");
        final String body = network.substring(branch, chainEnd);

        final int bypass = body.indexOf("breaker.isPassthrough()");
        final int attach = body.indexOf("new FeAttachment(");
        assertTrue(bypass >= 0,
                "the Main Breaker branch has no passthrough case, so the paddle changes a blockstate "
                        + "and nothing else: the block goes on metering while its readout, its bar "
                        + "and its own tooltip all say it does not.");
        assertTrue(bypass < attach,
                "the passthrough case must be answered BEFORE the attachment is built. Behind it the "
                        + "breaker is an attachment as well as a bypass, so the same FE is offered "
                        + "twice: once through the port and once through the fused circuit.");

        final String bypassBody = body.substring(bypass, attach);
        assertTrue(bypassBody.contains("walk.crossPassthrough()"),
                "the bypass must claim the walk's single crossing. Without it a run of passthrough "
                        + "breakers chains into one grid the size of a base, which is exactly what "
                        + "'it stops at the next breaker' promises it will not do.");
        assertTrue(bypassBody.contains("MainBreakerStuds.otherStud("),
                "the flood arrived on one terminal and has to carry on out of the OTHER one; "
                        + "re-walking the terminal it came in on would loop the flood on itself.");
        assertTrue(bypassBody.contains("instanceof FeWireTerminal"),
                "only a plain wire terminal may be enqueued by the bypass. That guard is the whole of "
                        + "'not beyond a distribution panel, a battery controller or a junction box': "
                        + "those are not FeWireTerminals, so they are left to the ordinary rules and "
                        + "met as the boundaries they are.");
        assertTrue(!bypassBody.contains("found.add("),
                "a bypassed breaker must not become a member of the grid it just fused. It has no "
                        + "buffer in play and no capability at all, so a member would report a "
                        + "reserve that does not exist and be solved against a port that refuses.");
    }

    @Test
    @DisplayName("both ports go inert while the paddle is at passthrough")
    void passthroughSilencesBothPorts() throws IOException {
        final String breakerSource = Files.readString(BREAKER, StandardCharsets.UTF_8);
        for (String gate : new String[] {"private int intakeRoom(int wanted) {",
                "private int outflow(int wanted) {"}) {
            final int at = breakerSource.indexOf(gate);
            assertTrue(at >= 0, gate + " has been renamed; this test can no longer find it");
            final String guard = breakerSource.substring(at, breakerSource.indexOf('}', at));
            assertTrue(guard.contains("isPassthrough()"),
                    gate + " must refuse while the breaker is bypassed. A block that is both a fused "
                            + "conductor and a live port hands the same circuit its energy twice.");
        }
        assertTrue(breakerSource.contains("return isClosed() && !isPassthrough();"),
                "isMetering() is what canExtract and canReceive are written against; if it stops "
                        + "meaning 'closed and not bypassed' the ports stop agreeing with the walk");
    }

    @Test
    @DisplayName("a Power Connector standing on a breaker does not delete the breaker's attachment")
    void theConnectorDedupeSkipsBreakers() throws IOException {
        final String network = network();
        final int dedupe = network.indexOf("// A machine already served by a connector");
        assertTrue(dedupe >= 0, "the connector dedupe has moved; this test can no longer find it");
        final int end = network.indexOf("flowOwners.keySet().retainAll", dedupe);
        assertTrue(end > dedupe, "the dedupe's shape changed; this test can no longer find its end");
        assertTrue(network.substring(dedupe, end).contains("MainBreakerBE"),
                "the dedupe drops the attachment for the block a connector is mounted on, because "
                        + "that block is served through the connector instead. A Main Breaker never "
                        + "is -- it publishes no energy capability at all -- so this would silently "
                        + "disconnect a correctly wired breaker the moment somebody put a connector "
                        + "on its housing.");
    }

    @Test
    @DisplayName("the two ports face opposite ways, and both give on the Creative twin")
    void thePortsFaceTheWayTheirStudsMean() throws IOException {
        final String breakerSource = Files.readString(BREAKER, StandardCharsets.UTF_8);
        final int in = breakerSource.indexOf("private final class InputPort");
        final int out = breakerSource.indexOf("private final class OutputPort");
        assertTrue(in >= 0 && out > in, "the two port classes could not be found");
        final int end = breakerSource.indexOf("// ---------------------------- Tick", out);
        assertTrue(end > out, "the port section's shape changed; this test cannot find its end");

        final String loadBody = breakerSource.substring(out, end);
        assertTrue(Pattern.compile("public int receiveEnergy\\([^)]*\\)\\s*\\{\\s*return 0;")
                        .matcher(loadBody).find(),
                "the OUTPUT terminal must never receive. A port that took as well as gave would be "
                        + "read as storage, and the protected circuit would push its own energy back "
                        + "into the breaker instead of spending it.");
        assertTrue(loadBody.contains("noteDemand("),
                "the OUTPUT terminal is the only place an overload can be measured: the supply grid "
                        + "asks how much will be taken before it delivers anything, so it can never "
                        + "offer more than the contacts pass. Without this the thermal element sees "
                        + "nothing to be hot about and the breaker never trips at all.");
        assertTrue(loadBody.contains("budget.energy() > 0"),
                "demand must be counted only while the buffer had something to give, or a STARVED "
                        + "breaker reads as an overloaded one and trips because its supply died.");

        final String lineBody = breakerSource.substring(in, out);
        assertTrue(lineBody.contains("isCreative()"),
                "the INPUT terminal has to know about the Creative twin: on one it is a second outlet "
                        + "rather than a feeder, which is the whole answer to which terminal a "
                        + "creative breaker supplies -- both of them.");
    }
}
