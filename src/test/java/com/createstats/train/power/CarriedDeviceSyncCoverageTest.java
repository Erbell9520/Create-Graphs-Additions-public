package com.createstats.train.power;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CarriedDeviceSyncCoverageTest {
    private static final Path MAIN = Path.of("src/main/java/com/createstats");
    private static final Path MIXINS = Path.of("src/main/resources/create_stats.mixins.json");

    private static String code(Path file) throws IOException {
        final String text = Files.readString(file, StandardCharsets.UTF_8).replace("\r\n", "\n");
        return text.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("//[^\\n]*", "");
    }

    private static String blockAfter(String code, String headRegex, String what) {
        final Matcher m = Pattern.compile(headRegex).matcher(code);
        assertTrue(m.find(), "no " + what);
        final int open = code.indexOf('{', m.end() - 1);
        assertTrue(open >= 0, "no body for " + what);
        int depth = 0;
        for (int i = open; i < code.length(); i++) {
            final char c = code.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}' && --depth == 0) {
                return code.substring(open + 1, i);
            }
        }
        throw new IllegalStateException("unbalanced braces after " + what);
    }

    private static String method(String code, String nameRegex) {
        return blockAfter(code, nameRegex + "\\s*\\([^)]*\\)\\s*\\{", "method " + nameRegex);
    }

    private static String jsonArray(String json, String key) {
        final Matcher m = Pattern.compile("\"" + key + "\"\\s*:\\s*\\[([^\\]]*)\\]").matcher(json);
        assertTrue(m.find(), "no " + key + " array");
        return m.group(1);
    }

    @Test
    @DisplayName("the Portable Power Interface wakes the sync inside the block its commit answer guards")
    void interfaceWakesSync() throws IOException {
        final String step = method(code(MAIN.resolve("blockentity/PortablePowerInterfaceBE.java")),
                "void\\s+step");
        final String guarded = blockAfter(step, "if\\s*\\(\\s*stores\\.commit\\(ledger\\)\\s*\\)\\s*\\{",
                "if (stores.commit(ledger))");
        assertTrue(guarded.contains("CarriedDeviceSync.touched(docked)"), "the sync is not woken by the commit");
    }

    @Test
    @DisplayName("the level tick runs the sync before any early return past the side guard")
    void levelTickRunsSync() throws IOException {
        final String tick = method(code(MAIN.resolve("CreateStats.java")), "void\\s+onLevelTick");
        final int call = tick.indexOf("CarriedDeviceSync.tick(level)");
        assertTrue(call >= 0, "the service never ticks");
        final int sideGuard = tick.indexOf("return;");
        final int nextReturn = tick.indexOf("return", sideGuard + 1);
        assertTrue(sideGuard >= 0 && sideGuard < call, "the call is above the client-side guard");
        assertTrue(nextReturn < 0 || call < nextReturn, "an early return can skip the service");
    }

    @Test
    @DisplayName("both packets are registered in the payload registration")
    void packetsRegistered() throws IOException {
        final String reg = method(code(MAIN.resolve("network/ModPackets.java")), "void\\s+register");
        assertTrue(reg.contains("playToClient(DeviceSyncPacket.TYPE, DeviceSyncPacket.STREAM_CODEC, DeviceSyncPacket::handle)"),
                "device_sync is not registered");
        assertTrue(reg.contains("playToClient(TrainChargeSyncPacket.TYPE, TrainChargeSyncPacket.STREAM_CODEC, TrainChargeSyncPacket::handle)"),
                "train_charge_sync is not registered");
    }

    @Test
    @DisplayName("the goggle mixin is a client mixin, and it draws and cancels at the head of Create's overlay")
    void goggleMixinWired() throws IOException {
        final String json = Files.readString(MIXINS, StandardCharsets.UTF_8);
        assertTrue(jsonArray(json, "client").contains("\"GoggleOverlayRendererMixin\""),
                "the goggle mixin is not in the client list");
        assertFalse(jsonArray(json, "mixins").contains("\"GoggleOverlayRendererMixin\""),
                "a client-only mixin in the common list crashes a dedicated server");
        final String mixin = code(MAIN.resolve("mixin/GoggleOverlayRendererMixin.java"));
        assertTrue(Pattern.compile("@Inject\\(method\\s*=\\s*\"renderOverlay\",\\s*at\\s*=\\s*@At\\(\"HEAD\"\\),\\s*cancellable\\s*=\\s*true\\)")
                .matcher(mixin).find(), "the mixin is not a cancellable head injection into renderOverlay");
        final String drew = blockAfter(mixin, "if\\s*\\(\\s*ContraptionGoggleOverlay\\.render\\(graphics,\\s*delta\\)\\s*\\)\\s*\\{",
                "the overlay call");
        assertTrue(drew.contains("ci.cancel()"), "Create's overlay is never skipped, so two tooltips draw");
    }

    @Test
    @DisplayName("the service refreshes the update tag and sends to trackers")
    void serviceRefreshesAndSends() throws IOException {
        final String sync = code(MAIN.resolve("train/power/CarriedDeviceSync.java"));
        final String step = method(sync, "boolean\\s+step");
        assertTrue(step.contains("refreshUpdateTag(contraption, pos, tag)"),
                "a late-joining client is sent the assembly charge");
        assertTrue(step.contains("PacketDistributor.sendToPlayersTrackingEntity(entity,"),
                "nothing is ever sent");
        assertTrue(step.contains("blocks.put(pos, new StructureBlockInfo(info.pos(), state, info.nbt()))"),
                "the LED state never reaches the server's block map, so disassembly and saves lose it");
        assertTrue(method(sync, "void\\s+refreshUpdateTag").contains("update.merge(saved)"),
                "refreshUpdateTag does not write");
        final String json = Files.readString(MIXINS, StandardCharsets.UTF_8);
        assertTrue(jsonArray(json, "mixins").contains("\"ContraptionUpdateTagsAccessor\""),
                "the update-tag accessor is not a common mixin, so the server cast fails");
    }

    @Test
    @DisplayName("the client applier patches both the copy and the block map, and never resets the car")
    void applierPatchesBoth() throws IOException {
        final String apply = method(code(MAIN.resolve("client/train/CarriedDeviceClient.java")), "void\\s+apply");
        assertTrue(apply.contains("nbt.merge(entry.tag())"), "the block map keeps the assembly nbt");
        assertTrue(apply.contains("blocks.put(entry.localPos(),"), "a new LED state never reaches the block map");
        assertTrue(apply.contains("copy.handleUpdateTag(entry.tag()"), "the rendered copy is never updated");
        assertTrue(apply.contains("invalidateClientContraptionStructure()"), "an LED change never re-meshes");
        assertFalse(apply.contains("resetClientContraption()") || apply.contains("resetRenderLevel()"),
                "a full rebuild per packet is the cost this packet exists to avoid");
    }

    @Test
    @DisplayName("the overlay marks the carried read and clears it in a finally")
    void overlayMarksCarriedRead() throws IOException {
        final String render = method(code(MAIN.resolve("client/train/ContraptionGoggleOverlay.java")),
                "boolean\\s+render");
        final String tried = blockAfter(render, "CarriedReadout\\.set\\(true\\);\\s*try\\s*\\{", "the marked try");
        assertTrue(tried.contains("addToGoggleTooltip(tooltip, sneaking)"), "the tooltip call is not inside the mark");
        final String cleanup = blockAfter(render, "addToGoggleTooltip\\(tooltip, sneaking\\);\\s*\\}\\s*finally\\s*\\{",
                "the finally");
        assertTrue(cleanup.contains("CarriedReadout.set(false)"), "a throwing tooltip leaves every placed block reading as carried");
    }

    @Test
    @DisplayName("the client forgets train summaries on world unload")
    void summariesClearedOnUnload() throws IOException {
        final String unload = method(code(MAIN.resolve("client/ClientEvents.java")), "void\\s+onWorldUnload");
        assertTrue(unload.contains("ClientTrainCharge.clear()"), "a summary follows the player into another save");
    }
}
