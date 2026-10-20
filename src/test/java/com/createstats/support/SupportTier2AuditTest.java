package com.createstats.support;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

class SupportTier2AuditTest {
    private static final Path SUPPORT = Path.of("src/main/java/com/createstats/support");

    private static String read(String file) throws Exception {
        return Files.readString(SUPPORT.resolve(file), StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    private static List<String> handlers(String source, String casePrefix) {
        List<String> out = new ArrayList<>();
        Matcher m = Pattern.compile("case " + casePrefix + "\\w+ -> (\\w+)\\(").matcher(source);
        while (m.find()) {
            out.add(m.group(1));
        }
        return out;
    }

    private static String body(String source, String name) {
        Matcher start = Pattern.compile("\\n    private static void " + name + "\\(").matcher(source);
        assertTrue(start.find(), "no method " + name);
        Matcher next = Pattern.compile("\\n    (private|public|static)[^\\n]*\\(").matcher(source);
        int end = next.find(start.end()) ? next.start() : source.length();
        return source.substring(start.end(), end);
    }

    @Test
    void everyActionVerbReportsThroughTheAlwaysOnPath() throws Exception {
        String source = read("SupportActionPacket.java");
        List<String> handlers = handlers(source, "VERB_");
        assertTrue(handlers.containsAll(List.of("give", "gamemode", "fly", "heal")), "dispatch not found: " + handlers);

        for (String handler : handlers) {
            assertTrue(body(source, handler).contains("SupportSessions.tier2("),
                "Tier 2 verb handler " + handler + " does not report through SupportSessions.tier2");
        }
    }

    @Test
    void everyRigReportsThroughTheAlwaysOnPath() throws Exception {
        String source = read("SupportTestRig.java");
        List<String> handlers = handlers(source, "RIG_");
        assertTrue(handlers.containsAll(List.of("clear", "kinetic")), "rig dispatch not found: " + handlers);
        for (String handler : handlers) {
            assertTrue(body(source, handler).contains("SupportSessions.tier2("),
                "rig " + handler + " does not report through SupportSessions.tier2");
        }
    }

    @Test
    void theTier1LogIsNeverUsedForWorldActions() throws Exception {
        for (String file : List.of("SupportActionPacket.java", "SupportTestRig.java", "SupportSessions.java")) {
            assertFalse(read(file).contains("SupportAccess.audit("),
                file + " logs through the Tier 1 channel SupportAccess.audit; world actions must use auditTier2");
        }
    }

    @Test
    void tier2ReportsThroughTheTier2Channel() throws Exception {
        String source = read("SupportSessions.java");
        Matcher start = Pattern.compile("\\n    public static void tier2\\(").matcher(source);
        assertTrue(start.find(), "no SupportSessions.tier2");
        Matcher next = Pattern.compile("\\n    (private|public|static)[^\\n]*\\(").matcher(source);
        String body = source.substring(start.end(), next.find(start.end()) ? next.start() : source.length());
        assertTrue(body.contains("SupportAccess.auditTier2(player, \"tier2\""),
            "SupportSessions.tier2 no longer writes its action line through auditTier2");
    }

    @Test
    void supportIsLogOnlyAndOnlyOnFlipsAreLogged() throws Exception {
        for (String file : List.of("SupportSessions.java", "SupportActionPacket.java", "SupportTestRig.java")) {
            assertFalse(read(file).contains(".isOp("), file + " posts support activity to operators' chat");
        }
        assertFalse(read("SupportAccess.java").contains("\"DISABLED\""),
            "a log channel being switched off must not write a line");
    }

    @Test
    void tier2LoggingStartsOn() throws Exception {
        String access = read("SupportAccess.java");
        assertTrue(access.contains("private static final boolean DEFAULT_TIER2_AUDIT_ENABLED = true;"),
            "Tier 2 logging must default to on; off is only ever someone's explicit choice");

        assertTrue(access.contains("\n        tier2AuditEnabled = DEFAULT_TIER2_AUDIT_ENABLED;"),
            "server stop must put Tier 2 logging back to its default");
    }
}
