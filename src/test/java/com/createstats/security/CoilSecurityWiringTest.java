package com.createstats.security;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CoilSecurityWiringTest {
    private static final Path PANEL = Path.of("src/main/java/com/createstats/blockentity/HomeSecurityPanelBE.java");
    private static final Path HOUSE = Path.of("src/main/java/com/createstats/security/SecurityHousehold.java");
    private static final Path ACTION = Path.of("src/main/java/com/createstats/network/SecurityActionPacket.java");
    private static final Path CONTEXT = Path.of("src/main/java/com/createstats/gui/security/SecurityContext.java");
    private static final Path THEME = Path.of("src/main/java/com/createstats/gui/security/SecurityTheme.java");
    private static final Path WATCH = Path.of("src/main/java/com/createstats/security/CoilBreakWatch.java");

    private static String code(Path p) throws IOException {
        return Files.readString(p, StandardCharsets.UTF_8).replace("\r\n", "\n")
                .replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("//[^\\n]*", " ");
    }

    private static String body(String source, String marker) {
        final int at = source.indexOf(marker);
        assertTrue(at >= 0, "not found, so this test now guards nothing: " + marker);
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
        throw new IllegalStateException("unbalanced braces after " + marker);
    }

    @Test
    void everyCoilFlagIsKeptWherePolicyHides() throws IOException {
        final String h = code(HOUSE);
        final String tables = body(h, "private List<EndpointTable<?>> tables()");
        final String write = body(h, "private void writePolicy(");
        final String read = body(h, "private void readPolicy(");
        for (String[] flag : new String[][] {
                {"coilRules", "CoilRules", "putInt", "getInt"},
                {"coilsOn", "CoilOn", "putBoolean", "getBoolean"},
                {"coilsLocked", "CoilLocked", "putBoolean", "getBoolean"},
                {"coilsHear", "CoilHears", "putBoolean", "getBoolean"}}) {
            assertTrue(tables.contains("this." + flag[0]), "tables() misses " + flag[0]);
            assertTrue(write.contains("policy.put(\"" + flag[1] + "\", this." + flag[0] + ".save((row, v) -> row."
                    + flag[2] + "(\"V\", v)))"), "writePolicy misses " + flag[0]);
            assertTrue(read.contains("this." + flag[0] + ".load(list(policy, \"" + flag[1] + "\"), row -> row."
                    + flag[3] + "(\"V\"))"), "readPolicy misses " + flag[0]);
        }
        final String monitoring = body(h, "public boolean requiredForMonitoring(");
        assertTrue(monitoring.contains("SecurityKinds.COIL.equals(kind)") && monitoring.contains("return coilOn(anchor);"),
                "a stood-down coil must not count as a hole in the house: " + monitoring);
    }

    @Test
    void everyPanelCoilEditIsGatedAndPushed() throws IOException {
        final String panel = code(PANEL);
        for (String edit : new String[] {"public boolean setCoilOn(", "public boolean setCoilLocked(",
                "public boolean setCoilRedstone(", "public boolean setCoilRule("}) {
            final String b = body(panel, edit);
            assertTrue(b.contains("mayEditCoil(h, device, actor)"), edit + " must ask the gate: " + b);
            assertTrue(b.contains("pushToDevices();"), edit + " must repoll: " + b);
        }
        final String all = body(panel, "public boolean switchAllCoils(");
        assertTrue(all.contains("roleOf(actor).mayEditPanel()") && all.contains("pushToDevices();"), all);
        final String gate = body(panel, "private boolean mayEditCoil(");
        assertTrue(gate.contains("onRoster(device)") && gate.contains("roleOf(actor).mayEditDevices()")
                && gate.contains("instanceof CoilDevice"), gate);
    }

    @Test
    void aCoilFaultCountsAndANewCoilIsSeeded() throws IOException {
        final String panel = code(PANEL);
        assertTrue(body(panel, "private int faultCount()").contains("coilFault(d)"), "the banner misses coil faults");
        final String observe = body(panel, "private void observe(SecurityHousehold h, long now)");
        assertTrue(observe.contains("silentRepeller(d) || coilFault(d)"), "monitoring misses coil faults");
        final String ours = body(observe, "if (h.id().equals(device.governingHousehold()))");
        assertTrue(ours.contains("h.enroll(at, kind, now);") && ours.contains("seedCoil(h, at, be);"),
                "a coil must be seeded where it is enrolled, and only there: " + ours);
        final String seed = body(panel, "private void seedCoil(");
        assertTrue(seed.contains("!h.hasCoilRules(at)") && seed.contains("CoilRules.Grid.ofMode(coil.coilReading().targets())"),
                "seeding must copy the coil's own setting, once: " + seed);
    }

    @Test
    void theActionPacketRoutesEveryCoilAction() throws IOException {
        final String handle = body(code(ACTION), "public static void handle(SecurityActionPacket packet");
        for (String route : new String[] {
                "case SET_COIL_ON -> panel.setCoilOn(packet.target(), packet.value() != 0, sender);",
                "case SET_COIL_LOCK -> panel.setCoilLocked(packet.target(), packet.value() != 0, sender);",
                "case SET_COIL_REDSTONE -> panel.setCoilRedstone(packet.target(), packet.value() != 0, sender);",
                "case SET_COIL_RULE -> panel.setCoilRule(packet.target(),",
                "case SWITCH_ALL_COILS -> panel.switchAllCoils(packet.value() != 0, sender);"}) {
            assertTrue(handle.contains(route), "the packet does not route: " + route);
        }
    }

    @Test
    void thePanelsKindTablesKnowTheCoil() throws IOException {
        final String panel = code(PANEL);
        assertTrue(body(panel, "static String kindOf(SecurityDevice.DeviceClass cls").contains(
                "case COIL -> SecurityKinds.COIL;"));
        assertTrue(body(panel, "static SecurityDevice.DeviceClass classOf(String kind)").contains(
                "case SecurityKinds.COIL -> SecurityDevice.DeviceClass.COIL;"), "an offline coil row would be a door");
        assertTrue(body(panel, "static String kindName(String kind)").contains(
                "case SecurityKinds.COIL -> \"Storm Coil\";"), "an offline coil would be called a Door");
    }

    @Test
    void theScreenSeesACoilFault() throws IOException {
        assertTrue(body(code(CONTEXT), "public boolean hasFault()").contains("coilFault(row)"),
                "the titlebar would read SECURE over a flat coil");
        assertTrue(body(code(THEME), "public static int outlineColor(").contains("row.isCoil()"),
                "a faulted coil would be outlined as a resting one");
    }

    @Test
    void theBreakWatchFindsTheBaseOfAnyCell() throws IOException {
        final String b = body(code(WATCH), "public static void onBreak(");
        assertTrue(b.contains("StormCoilPartBlock.master(level, event.getPos(), event.getState())"), b);
        assertTrue(b.contains("CoilGate.removalTrips(coil.clears(player))"), b);
        assertTrue(b.contains("panel.tripped(base, SensorKind.TAMPER"), b);
    }
}
