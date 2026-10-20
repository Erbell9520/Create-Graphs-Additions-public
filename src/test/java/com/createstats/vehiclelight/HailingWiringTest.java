package com.createstats.vehiclelight;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class HailingWiringTest {
    private static final Path JAVA = Path.of("src/main/java/com/createstats");

    private static String read(String file) throws IOException {
        return Files.readString(JAVA.resolve(file), StandardCharsets.UTF_8);
    }

    static String body(String source, String signature) {
        final StringBuilder code = new StringBuilder();
        for (String line : source.split("\n", -1)) {
            final int comment = line.indexOf("//");
            code.append(comment >= 0 ? line.substring(0, comment) : line).append('\n');
        }
        final String text = code.toString();
        final int at = text.indexOf(signature);
        assertTrue(at >= 0, "no method " + signature);
        final int open = text.indexOf('{', at);
        int depth = 0;
        for (int i = open; i < text.length(); i++) {
            final char c = text.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}' && --depth == 0) {
                return text.substring(open, i + 1);
            }
        }
        throw new AssertionError("unbalanced braces after " + signature);
    }

    @Test
    void theGreetingReachesEveryClientInTheLampsUpdate() throws IOException {
        final String lamp = read("vehiclelight/VehicleLightBE.java");
        assertTrue(body(lamp, "public void hail(").contains("sendBlockUpdated("),
                "VehicleLightBE.hail sends the lamp's update, or no client hears of the greeting");
        assertTrue(body(lamp, "public CompoundTag getUpdateTag(").contains("tag.putLong(\"HailStart\""),
                "the update carries the greeting's start");
        assertTrue(body(lamp, "public Packet<ClientGamePacketListener> getUpdatePacket(")
                .contains("ClientboundBlockEntityDataPacket.create(this)"), "and is sent as a block entity update");
        assertTrue(body(lamp, "protected void loadAdditional(").contains("tag.getLong(\"HailStart\")"),
                "and the client reads it back");
    }

    @Test
    void takingTheHelmStartsTheGreetingAndBootsTheHelmet() throws IOException {
        assertTrue(body(read("vccmk4/Vcc4BE.java"), "private void updateDriver(").contains("hail(server, now);"),
                "Vcc4BE.updateDriver greets the driver it has just taken");
        assertTrue(body(read("vccmk4/client/Vcc4ClientInput.java"), "public static void setHelm(")
                .contains("HudSession.helmTaken()"), "and the driver's helmet boots with the lamps");
    }

    @Test
    void theLensAndTheLightOnTheRoadTakeOneAnswer() throws IOException {
        assertTrue(body(read("renderer/VehicleLightRenderer.java"), "public void render(").contains("LampShow.lens("),
                "the renderer draws the lens from LampShow");
        assertTrue(body(read("client/dynlight/VehicleLightLights.java"), "static void collect(").contains("LampShow.lens("),
                "and the light on the road comes from the same answer, or a lens could glow over a dark road");
        assertTrue(body(read("vehiclelight/VehicleLightBE.java"), "void serverTick(")
                        .contains("glow(input(), level.getGameTime(), onCraft)"),
                "a signal on a craft is held steady by the server, or the client's flash is flashed again");
    }
}
