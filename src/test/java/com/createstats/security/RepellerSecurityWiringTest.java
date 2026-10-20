package com.createstats.security;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RepellerSecurityWiringTest {
    private static final Path BE = Path.of("src/main/java/com/createstats/blockentity/MobRepellerBE.java");
    private static final Path PANEL = Path.of("src/main/java/com/createstats/blockentity/HomeSecurityPanelBE.java");
    private static final Path HOUSE = Path.of("src/main/java/com/createstats/security/SecurityHousehold.java");

    private static String code(Path p) throws IOException {
        return Files.readString(p, StandardCharsets.UTF_8)
                .replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("//[^\\n]*", " ");
    }

    private static String body(String source, String signature) {
        final int at = source.indexOf(signature);
        assertTrue(at >= 0, "method not found, so this test now guards nothing: " + signature);
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

    @Test
    void theTunerAsksTheGateBeforeItRetunes() throws IOException {
        final String b = body(code(BE), "public void applyTuning(ServerPlayer player");
        final int gate = b.indexOf("gateFor(player)");
        final int refuse = b.indexOf("return;", gate);
        final int retune = b.indexOf("retune(");
        assertTrue(gate >= 0, "applyTuning must ask the gate: " + b);
        assertTrue(retune > gate, "the gate must be asked before retune: " + b);
        assertTrue(refuse > gate && refuse < retune, "a refusal must return before retune: " + b);
    }

    @Test
    void theGateReadsTheLockAndTheRoster() throws IOException {
        final String b = body(code(BE), "private RepellerGate gateFor(");
        assertTrue(b.contains("RepellerGate.decide(panelPos != null, locked, roster != null"), b);
    }

    @Test
    void thePanelsWritesIntoTheBlockAskWhoseItIs() throws IOException {
        final String panel = code(PANEL);
        assertTrue(body(panel, "public boolean tuneRepeller(").contains("governs(device)"),
                "tuneRepeller writes into a block and must ask governs");
        final String survey = body(panel, "public boolean surveyRepeller(");
        assertTrue(survey.contains("governs(device)") && survey.contains("onRoster(device)"), survey);
        final String gate = body(panel, "private boolean mayEditRepeller(");
        assertTrue(gate.contains("onRoster(device)") && gate.contains("mayEditDevices()"), gate);
    }

    @Test
    void aPanelEditRepollsEveryRepeller() throws IOException {
        final String b = body(code(PANEL), "private void pushToDevices()");
        assertTrue(b.contains("instanceof GovernedDevice governed") && b.contains("governed.repoll()"), b);
        final String be = code(BE);
        final int decl = be.indexOf("public class MobRepellerBE");
        final int open = be.indexOf('{', decl);
        assertTrue(decl >= 0 && be.substring(decl, open).contains("GovernedDevice"),
                "MobRepellerBE must implement GovernedDevice, or no edit ever repolls it");
    }

    @Test
    void aSilentRepellerIsAFault() throws IOException {
        assertTrue(body(code(PANEL), "private int faultCount()").contains("silentRepeller(d)"));
    }

    @Test
    void everyFlagIsKeptWherePolicyHides() throws IOException {
        final String h = code(HOUSE);
        final String tables = body(h, "private List<EndpointTable<?>> tables()");
        final String write = body(h, "private void writePolicy(");
        final String read = body(h, "private void readPolicy(");
        final String legacy = body(h, "private void readLegacyPolicy(");
        for (String[] flag : new String[][] {
                {"repellersOn", "RepellerOn", "RepellersOff"},
                {"repellersLocked", "RepellerLocked", "RepellersLocked"},
                {"repellersHear", "RepellerHears", "RepellersDeaf"}}) {
            assertTrue(tables.contains("this." + flag[0]), "tables() misses " + flag[0]);
            assertTrue(write.contains("policy.put(\"" + flag[1] + "\", this." + flag[0] + ".save("),
                    "writePolicy misses " + flag[0]);
            assertTrue(read.contains("this." + flag[0] + ".load(list(policy, \"" + flag[1] + "\")"),
                    "readPolicy misses " + flag[0]);
            assertTrue(legacy.contains("readLegacyFlags(tag, \"" + flag[2] + "\", this." + flag[0]),
                    "the legacy load misses " + flag[2]);
        }
        for (String walker : new String[] {"public boolean knows(SecurityEndpoint anchor)", "public void prune(",
                "public boolean isEmpty()", "public boolean settleLegacy()"}) {
            assertTrue(body(h, walker).contains("tables()"), walker + " no longer walks tables()");
        }
    }
}
