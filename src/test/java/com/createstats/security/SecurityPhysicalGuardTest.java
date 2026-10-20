package com.createstats.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SecurityPhysicalGuardTest {
    private static final Path DOOR = Path.of("src/main/java/com/createstats/block/PoweredDoorBlock.java");
    private static final Path GARAGE = Path.of("src/main/java/com/createstats/blockentity/GarageDoorBE.java");

    private static final List<String> SECURITY_BLOCKS = List.of(
            "PoweredDoorBlock", "GarageDoorBodyBlock", "GarageDoorHeaderBlock", "BlastShutterBlock",
            "SecurityLockerBlock", "HomeSecurityPanelBlock", "SecuritySensorBlock", "SecurityRelayBlock");

    private static String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    private static String methodBody(String source, String declaration) {
        final int start = source.indexOf(declaration);
        assertTrue(start >= 0, declaration + " is gone; this guard has lost its anchor");
        final int open = source.indexOf('{', start);
        assertTrue(open > start, "could not find the body of " + declaration);
        int depth = 0;
        for (int i = open; i < source.length(); i++) {
            final char c = source.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return source.substring(start, i + 1);
                }
            }
        }
        throw new AssertionError(declaration + " has no closing brace; the file is malformed");
    }

    @Test
    @DisplayName("a Powered Door on a roster refuses redstone, and still records the edge")
    void theGovernedDoorRefusesRedstone() throws IOException {
        final String body = methodBody(read(DOOR), "protected void neighborChanged");

        final int edge = body.indexOf("updatePowered(powered)");
        final int gate = body.indexOf("door.governed()");

        final int drive = body.indexOf("toggle(level, lower, powered, Drive.REDSTONE");

        assertTrue(gate >= 0,
                "neighborChanged must refuse a governed door. Without this one redstone block beside "
                        + "either half opens any door at any clearance, through a lockdown and through "
                        + "an AWAY seal, which is the whole ladder for the price of a block.");
        assertTrue(drive > gate,
                "the governed() gate must come before the Drive.REDSTONE toggle. After it, the door "
                        + "has already opened and the check is decoration.");
        assertTrue(edge >= 0 && edge < gate,
                "updatePowered must be called before the refusal, not after it. Skipped while the "
                        + "door ignores the wire, a signal that has been standing for an hour reads as "
                        + "a fresh edge the moment the door is unlinked, and the door swings on its own.");
    }

    @Test
    @DisplayName("a Garage Door bay on a roster refuses redstone, and still records the edge")
    void theGovernedBayRefusesRedstone() throws IOException {
        final String body = methodBody(read(GARAGE), "public void onRedstone");

        final int edge = body.indexOf("updatePowered(powered)");
        final int gate = body.indexOf("governed()");
        final int open = body.indexOf("setOpen(powered");

        assertTrue(gate >= 0,
                "onRedstone must refuse a governed bay. A wire reaching any hood of the run opened a "
                        + "sealed bay at any clearance while this was missing.");
        assertTrue(open > gate, "the governed() gate must come before setOpen, or the bay is already open");
        assertTrue(edge >= 0 && edge < gate,
                "updatePowered must be called before the refusal, for the reason the Powered Door's "
                        + "own test gives: a stale signal becomes an edge when the bay is unlinked.");
    }

    @Test
    @DisplayName("every security block refuses to be moved by a contraption")
    void everySecurityBlockRefusesAContraption() throws IOException {
        for (String name : SECURITY_BLOCKS) {
            final String source = read(Path.of("src/main/java/com/createstats/block/" + name + ".java"));
            final String body = methodBody(source, "public PushReaction getPistonPushReaction");
            assertTrue(body.contains("SecurityImmobility.REACTION"),
                    name + " must answer getPistonPushReaction with SecurityImmobility.REACTION. "
                            + "Without it a Create contraption lifts this block out of the wall, and "
                            + "an assembly fires no BreakEvent, so neither PanelGuard nor a Tamper "
                            + "Sensor sees it happen. See SecurityImmobility.");
        }
    }
}
