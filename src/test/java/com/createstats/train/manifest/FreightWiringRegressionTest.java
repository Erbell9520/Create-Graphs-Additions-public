package com.createstats.train.manifest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FreightWiringRegressionTest {
    private static String source(String path) throws IOException {
        return Files.readString(Path.of("src/main/java/com/createstats/" + path))
            .replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)//[^\\n]*", "");
    }

    private static String method(String text, String declaration) {
        int start = text.indexOf(declaration);
        assertTrue(start >= 0, declaration);
        start = text.indexOf('{', start);
        int depth = 1;
        for (int end = start + 1; end < text.length(); end++) {
            if (text.charAt(end) == '{') depth++;
            if (text.charAt(end) == '}' && --depth == 0) return text.substring(start, end + 1);
        }
        throw new AssertionError("Unclosed method " + declaration);
    }

    @Test
    void identityLookupPrecedesPhysicalFallbackAndChecksDimension() throws IOException {
        String body = method(source("train/manifest/FreightManifestStationResolver.java"),
            "public static GlobalStation resolveStation(");
        assertTrue(Pattern.compile("graph\\.getPoint\\(EdgePointType\\.STATION, link\\.getStationId\\(\\)\\)").matcher(body).find());
        int fallback = body.indexOf("resolveStationBlockEntity(");
        assertTrue(fallback > 0);
        String identityBranch = body.substring(0, fallback);
        assertTrue(identityBranch.contains("stationLevel.dimension().equals(station.getBlockEntityDimension())"));
        assertTrue(identityBranch.contains("return station;"));
    }

    @Test
    void newLinksUseTheClickedWorldPosition() throws IOException {
        String body = method(source("item/FreightManifestClipboardItem.java"), "public InteractionResult useOn(");
        assertTrue(body.contains("ResourceLocation dimensionId = level.dimension().location();"));
        assertTrue(body.contains("new FreightStationLink(station.getId(), stationName, dimensionId, pos)"));
        assertFalse(body.contains("station.getBlockEntityPos()"));
    }

    @Test
    void bothScreensAndWallUseOnePostEvaluationCapture() throws IOException {
        String resolver = method(source("train/manifest/FreightManifestStationResolver.java"),
            "private static StationView buildStationView(");
        assertTrue(resolver.indexOf("evaluateStation(") < resolver.indexOf("stationState.getManifest()"));
        assertTrue(resolver.contains("new StationView(manifest, cargoView, boardView)"));
        for (String path : new String[]{"network/RequestFreightManifestClipboardStatePacket.java",
                "block/FreightManifestClipboardBlock.java", "blockentity/DispatchBoardBE.java"}) {
            String text = source(path);
            assertTrue(text.contains("captureStationView("), path);
            assertFalse(text.contains("captureManifest("), path);
        }
        assertFalse(source("blockentity/DispatchBoardBE.java").contains("resolveStationBlockEntity("));
    }

    @Test
    void retirementCannotOpenATransferWindowOrRequireADockedTrain() throws IOException {
        String allowance = method(source("train/manifest/ManifestAllowance.java"),
            "public static ManifestAllowance capture(ServerLevel");
        String afterEvaluation = allowance.substring(allowance.indexOf("FreightManifestLogic.evaluateStation("));
        assertTrue(afterEvaluation.contains("withCompletedOnceGate(stationState.hasCompletedOnceGate(operation, cargoKind))"));
        String evaluation = method(source("train/manifest/FreightManifestLogic.java"),
            "public static FreightManifestEvaluationResult evaluateStation(");
        assertTrue(evaluation.indexOf("retireCompletedOnceOrders()") >= 0);
        assertTrue(evaluation.indexOf("retireCompletedOnceOrders()") < evaluation.indexOf("if (train == null)"));
        assertTrue(source("network/FreightManifestDeliveryOrderActionPacket.java").contains("stationState.retireCompletedOnceOrders()"));
    }

    @Test
    void publishingReleasesOnlyTheGatesTheNewManifestGoverns() throws IOException {
        String body = method(source("train/manifest/FreightManifestStationState.java"),
            "public void setManifest(FreightManifest manifest, long currentTick)");
        assertFalse(body.contains("completedOnceGates.clear()"));
        assertTrue(body.contains("DeliveryOrderLifecyclePolicy.publishKeepsOnceGate("));
    }

    @Test
    void everySaveOutcomeIsAnswered() throws IOException {
        String body = method(source("network/SaveFreightManifestPacket.java"),
            "public static void handle(SaveFreightManifestPacket packet");
        int answered = body.indexOf("try {");
        assertTrue(answered > 0);
        String beforeTry = body.substring(0, answered);
        assertEquals(1, beforeTry.split("return;", -1).length - 1,
            "only the missing-player return may skip the reply: " + beforeTry);
        assertTrue(body.substring(answered).contains("finally"));
    }

    @Test
    void saveAcknowledgementIsEncodedDecodedAndRoutedToTheDraft() throws IOException {
        String packet = source("network/FreightManifestClipboardStatePacket.java");
        String encode = method(packet, "public void encode(");
        assertTrue(encode.contains("buf.writeBoolean(this.saveReply);"));
        assertTrue(encode.contains("buf.writeBoolean(this.saveAccepted);"));
        String decode = method(packet, "public FreightManifestClipboardStatePacket(RegistryFriendlyByteBuf");
        assertTrue(decode.contains("this.saveReply = buf.readBoolean();"));
        assertTrue(decode.contains("this.saveAccepted = buf.readBoolean();"));
        assertTrue(method(packet, "public static void handle(").contains("packet.saveReply, packet.saveAccepted"));
        assertTrue(source("network/StatsClientHooks.java").contains("pos, manifest, cargoView, deliveryBoard, saveReply, saveAccepted"));
        assertTrue(source("client/ClientPacketHandler.java").contains("screen.updateLiveState(manifest, cargoView, deliveryBoard, saveReply, saveAccepted)"));
        String screen = source("gui/FreightManifestClipboardScreen.java");
        assertFalse(method(screen, "private void triggerSave()").contains("this.saveFlash = 1.0f"));
        assertTrue(method(screen, "public void updateLiveState(").contains("this.draftState.receive(manifest.getVersion(), saveReply, saveAccepted)"));
    }
}
