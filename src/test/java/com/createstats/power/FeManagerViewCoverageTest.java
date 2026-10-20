package com.createstats.power;

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeManagerViewCoverageTest {
    private static final Path SOURCES = Path.of("src/main/java/com/createstats");
    private static final Path PDU = SOURCES.resolve("blockentity/PowerDistributionUnitBE.java");
    private static final Path PPI = SOURCES.resolve("blockentity/PortablePowerInterfaceBE.java");

    private static String code(String text) {
        return text.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("//[^\\n]*", "");
    }

    private static int count(String code, String call) {
        int n = 0;
        for (int at = code.indexOf(call); at >= 0; at = code.indexOf(call, at + 1)) {
            n++;
        }
        return n;
    }

    private static String method(String code, String declaration) {
        final Matcher m = Pattern.compile(declaration + "\\s*\\([^)]*\\)\\s*\\{").matcher(code);
        assertTrue(m.find(), "no method " + declaration);
        return block(code, m.end() - 1);
    }

    private static String block(String code, int open) {
        int depth = 0;
        for (int i = open; i < code.length(); i++) {
            final char c = code.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}' && --depth == 0) {
                return code.substring(open + 1, i);
            }
        }
        throw new IllegalStateException("unbalanced braces");
    }

    @Test
    @DisplayName("every manager stamps a view wherever it marks a circuit managed")
    void managersStampWhereTheyMark() throws IOException {
        final List<String> managers = new ArrayList<>();
        final List<Path> files;
        try (Stream<Path> walk = Files.walk(SOURCES)) {
            files = walk.filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> !p.getFileName().toString().equals("FeWireNetwork.java"))
                    .toList();
        }
        for (Path file : files) {
            final String src = code(Files.readString(file, StandardCharsets.UTF_8));
            final int marks = count(src, ".markPduManaged(");
            if (marks == 0) {
                continue;
            }
            final String name = file.getFileName().toString();
            managers.add(name);
            assertTrue(count(src, ".noteManagerView(") >= marks,
                    name + " marks " + marks + " circuit(s) managed and stamps fewer views (KI-36)");
        }
        assertTrue(managers.contains("PowerDistributionUnitBE.java")
                && managers.contains("BatteryControllerBE.java"), "scan missed a manager: " + managers);
    }

    @Test
    @DisplayName("the Portable Power Interface reads the stamp and hands both facts to the plan")
    void interfaceReadsTheStamp() throws IOException {
        final String step = method(code(Files.readString(PPI, StandardCharsets.UTF_8)), "void\\s+step");
        assertTrue(step.contains(".managerView(now)"), "step() does not read the manager's stamp");
        assertTrue(step.contains("stationHolds, wrongTerminal)"), "step() does not pass the stamp to the plan");
    }

    @Test
    @DisplayName("KI-37: a leg source is tagged with its caps and refused behind one")
    void legSourcesRespectCaps() throws IOException {
        final String src = code(Files.readString(PDU, StandardCharsets.UTF_8));
        final String drain = method(src, "int\\s+drainBranchSources");
        assertTrue(drain.contains("capsOfNode.get(node.nodeKey())"), "drainBranchSources ignores caps");
        final int generator = src.indexOf("role.kind() == com.createstats.power.FeNodeRole.Kind.GENERATOR");
        assertTrue(generator > 0, "no GENERATOR branch in the walk");
        final String branch = block(src, src.indexOf('{', generator));
        assertTrue(branch.contains("capsOfNode.put(node.nodeKey(), grid.instruments())"),
                "a leg source is never tagged with the limiters in front of it");
        assertEquals(1, count(branch, "branchSources.add(node)"), "not the GENERATOR branch");

        assertTrue(src.contains("cappedNetworks.add(network)"), "capped grids are never recorded");
        final String publish = method(src, "void\\s+publishManagerView");

        assertTrue(publish.contains("capped = view.refusingSources()"), "the capped stamp still takes sources");
        assertTrue(publish.contains("containsIdentity(cappedNetworks, network) ? capped : view"),
                "every grid gets the uncapped stamp");
    }

    private static final Path CONTROLLER = SOURCES.resolve("blockentity/BatteryControllerBE.java");
    private static final Path STARTER = SOURCES.resolve("blockentity/StarterCapacitorBE.java");

    private static List<Integer> argCounts(String code, String call) {
        final List<Integer> out = new ArrayList<>();
        for (int at = code.indexOf(call); at >= 0; at = code.indexOf(call, at + 1)) {
            int depth = 0;
            int args = 1;
            for (int i = at + call.length() - 1; i < code.length(); i++) {
                final char c = code.charAt(i);
                if (c == '(') {
                    depth++;
                } else if (c == ')' && --depth == 0) {
                    break;
                } else if (c == ',' && depth == 1) {
                    args++;
                }
            }
            out.add(args);
        }
        return out;
    }

    @Test
    @DisplayName("KI-69: both managers work out what they left short and stamp it on every circuit")
    void managersStampTheirShortfall() throws IOException {
        final String pdu = code(Files.readString(PDU, StandardCharsets.UTF_8));
        assertTrue(method(pdu, "public\\s+void\\s+tick")
                        .contains("lastBurstRelief = loads.isEmpty() ? 0 : burstReliefOf(loads);"),
                "the panel never works out what its serve left short");
        assertTrue(method(pdu, "void\\s+publishManagerView").contains("upstreamHoldsCharging(), lastBurstRelief)"),
                "the leg stamp carries no shortfall");
        assertTrue(method(pdu, "int\\s+burstReliefOf").contains("isBurstSource(node), reachable)"),
                "a capacitor's own charging counts as the branch going short");

        final String bc = code(Files.readString(CONTROLLER, StandardCharsets.UTF_8));
        final List<Integer> inputs = argCounts(bc, "FeManagerView.input(");
        final List<Integer> outputs = argCounts(bc, "FeManagerView.output(");
        assertTrue(!inputs.isEmpty() && !outputs.isEmpty(), "the controller stamps no view");
        assertTrue(inputs.stream().allMatch(n -> n == 3), "an input stamp carries no shortfall: " + inputs);
        assertTrue(outputs.stream().allMatch(n -> n == 4), "an output stamp carries no shortfall: " + outputs);
        assertTrue(method(bc, "public\\s+void\\s+tick").contains("outputBurstRelief[i] = burstReliefOf(i, loadsByOutput.get(i));"),
                "the controller never works out what each output was left short of");
        assertTrue(method(bc, "int\\s+burstReliefOf").contains("burstLoads.contains(key) || outputTanks.contains(key), reachable)"),
                "a capacitor's charge or a bank's room counts as an output going short");
    }

    @Test
    @DisplayName("KI-73, KI-75: a controller draws a burst off an output and charges a capacitor last")
    void controllerDrawsAndOrdersBursts() throws IOException {
        final String bc = code(Files.readString(CONTROLLER, StandardCharsets.UTF_8));
        final String tick = method(bc, "public\\s+void\\s+tick");
        final int generator = tick.indexOf("kind == com.createstats.power.FeNodeRole.Kind.GENERATOR");
        assertTrue(generator > 0, "the output walk has no GENERATOR branch");
        assertTrue(block(tick, tick.indexOf('{', generator)).contains("noteOutputBurst(i, node, attached,"),
                "a capacitor firing on an output is skipped like any source, and burns off");
        assertTrue(tick.contains("final int burst = drawOutputBursts();"), "the bursts found are never drawn");
        assertTrue(method(bc, "int\\s+serveOutput").contains("portFlowByNode, null, servePolicy)"),
                "the output serve ignores the capacitor's place at the back of the queue");
    }

    @Test
    @DisplayName("KI-69, KI-75: the capacitor fires on the manager's word and shuts its gate on it")
    void capacitorReadsTheShortfall() throws IOException {
        final String cap = code(Files.readString(STARTER, StandardCharsets.UTF_8));
        assertTrue(method(cap, "public\\s+void\\s+tick").contains("shortNow |= StarterPort.managedShort(view);"),
                "Auto never sees a managed circuit going short");
        final String gate = method(cap, "boolean\\s+gateFor");
        assertTrue(gate.contains("view != null && view.unserved() > 0,"),
                "the gate charges beside machines the manager left short");

        assertTrue(gate.contains("view != null && view.fundsStores())"),
                "the gate never hears that the manager funds this charge out of its refill");
    }

    @Test
    @DisplayName("KI-95: a Starter Capacitor is refilled out of the refill, before the banks, wherever it hangs")
    void storesAreRefilledBeforeTheBanks() throws IOException {
        final String bc = code(Files.readString(CONTROLLER, StandardCharsets.UTF_8));
        final String tick = method(bc, "public\\s+void\\s+tick");

        assertEquals(2, count(tick, "noteStore(port, node, attached, seenHandlers, seenDevices);"),
                "a capacitor on one side of the controller is never found to be refilled");

        final int stores = tick.indexOf("chargeStores(tanks, ");
        final int absorb = tick.indexOf("chargeReserve(tanks, spare)");
        assertTrue(stores > 0 && absorb > stores, "the banks are refilled before the capacitors, or never");

        assertTrue(count(tick, ".withStoresFunded(!upstreamHoldsCharging())") >= 2,
                "an input or output stamp never says the controller refills stores");

        final String pdu = code(Files.readString(PDU, StandardCharsets.UTF_8));
        final String pduTick = method(pdu, "public\\s+void\\s+tick");
        final int banks = pduTick.indexOf("final int charged = chargeBranchStorage();");
        final int boardStores = pduTick.indexOf("chargeBranchStores(loads, level.getGameTime(), charged)");
        assertTrue(banks > 0 && boardStores > banks, "a board never spends a store grant, or spends it first");
        assertTrue(method(pdu, "void\\s+publishManagerView").contains(".withStoresFunded(storeGrantActive(now))"),
                "a board with a store grant never says so on its legs");

        final String cap = code(Files.readString(STARTER, StandardCharsets.UTF_8));
        assertTrue(method(cap, "public\\s+void\\s+tick").contains("(!managed.isSuppressed() || refillFunded)"),
                "a shed capacitor refuses the refill paid for it");
    }

    @Test
    @DisplayName("KI-38: what the leg feeds could give is generation, and boards are left out of it")
    void legFeedIsGeneration() throws IOException {
        final String src = code(Files.readString(PDU, StandardCharsets.UTF_8));
        assertTrue(method(src, "int\\s+measuredGeneration").contains("legFeedGeneration"),
                "measuredGeneration() leaves out the leg feeds");
        final String drain = method(src, "int\\s+drainBranchSources");
        final int at = drain.indexOf("legFeedGeneration = com.createstats.power.FeRates.plus(");
        assertTrue(at > 0, "drainBranchSources never counts a leg feed");

        final int guard = drain.lastIndexOf("if (boardPeerOf(node) == null)", at);
        assertTrue(guard >= 0, "a downstream board's export is counted as generation");
        final String guarded = block(drain, drain.indexOf('{', guard));
        assertTrue(guarded.contains("legFeedGeneration = com.createstats.power.FeRates.plus("),
                "the leg feed is counted outside the board guard");
    }

    @Test
    @DisplayName("KI-55: an own generator on a leg is priced by what it makes, and the leg caps the sum")
    void legFeedIsPricedByOutput() throws IOException {
        final String src = code(Files.readString(PDU, StandardCharsets.UTF_8));
        final String drain = method(src, "int\\s+drainBranchSources");
        final int guard = drain.indexOf("if (boardPeerOf(node) == null)");
        assertTrue(guard >= 0, "no board guard in drainBranchSources");
        final String guarded = block(drain, drain.indexOf('{', guard));
        assertTrue(guarded.contains("com.createstats.power.PduLegFeed.price("),
                "a leg source is priced by a bare simulate, which is stock for a buffered generator");
        assertTrue(guarded.contains(".potentialFePerTick()"),
                "the leg price never asks a generator what it makes");
        assertTrue(guarded.contains("com.createstats.power.PduLegFeed.addToLeg("),
                "nothing holds a leg's priced sum to the leg's rating");
    }
}
