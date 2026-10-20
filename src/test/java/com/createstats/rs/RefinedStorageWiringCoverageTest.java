package com.createstats.rs;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RefinedStorageWiringCoverageTest {
    private static final Path MAIN = Path.of("src/main/java/com/createstats");

    @Test
    void displaySourceIsRegisteredAndMappedToTheirBlocks() throws IOException {
        final String createStats = read(MAIN.resolve("CreateStats.java"));
        assertTrue(createStats.contains("REFINED_STORAGE_SOURCE = CreateStats.DISPLAY_SOURCES.register("),
                "RefinedStorageDisplaySource is never registered, so it cannot be selected on a link");
        final String mapping = block(createStats, "RefinedStorageCompat.isAvailable()");
        assertTrue(mapping.contains("DisplaySource.BY_BLOCK.addProvider"),
                "The source is registered but never mapped to a block, so it is offered on nothing");
        assertTrue(mapping.contains("RefinedStorageCompatHelper.supportsStatsLinkBlock"),
                "The block mapping does not consult the compat helper's block list");
    }

    @Test
    void sourceTypeResolvesBeforeTheGenericFallThroughs() throws IOException {
        final String target = read(MAIN.resolve("display/StatsDisplayTarget.java"));
        final int rs = target.indexOf("RefinedStorageCompatHelper.getSourceType(sourceBE)");
        final int energyFallThrough = target.indexOf("InstrumentSourceHelper.hasEnergyStorageCapability(sourceBE)");
        final int containerFallThrough = target.indexOf("if (holdsSomething(sourceBE))");
        assertTrue(rs >= 0, "getStatsDisplayLinkSourceType never asks the Refined Storage helper");
        assertTrue(energyFallThrough >= 0 && rs < energyFallThrough,
                "The Refined Storage check must come before the energy fall-through, or a Controller "
                        + "is filed under its own translated block name");
        assertTrue(containerFallThrough >= 0 && rs < containerFallThrough,
                "The Refined Storage check must come before the container fall-through, or a Disk "
                        + "Drive is filed as a plain container");
    }

    @Test
    void autoReadRunsTheNetworkReader() throws IOException {
        final String target = read(MAIN.resolve("display/StatsDisplayTarget.java"));
        assertTrue(target.contains("com.createstats.rs.RsLinkReader.emit(panel, sourcePos, sourceBE)"),
                "The panel path never reads the network, so a linked Grid produces no channels");
        assertTrue(target.contains("com.createstats.rs.RsLinkReader.emit(receiver, sourcePos, sourceBE)"),
                "The generic receiver path never reads the network, so a tower light or projector "
                        + "linked to a Grid stays dark");
        assertTrue(block(target, "private boolean isAutoReadSource")
                        .contains("RefinedStorageCompatHelper.isNetworkBlockEntity(sourceBE)"),
                "isAutoReadSource does not claim Refined Storage blocks, so a refresh whose text "
                        + "line came back empty skips the read entirely");
    }

    @Test
    void familyIsRegisteredAgainstTheHelpersSourceType() throws IOException {
        final String catalog = read(MAIN.resolve("display/CustomScreenSourceFamilyCatalog.java"));
        assertTrue(catalog.contains("\"storage.refined_storage\""),
                "The family is not registered, so every network resolves to generic.unknown");
        final String helper = read(MAIN.resolve("compat/RefinedStorageCompatHelper.java"));
        final String sourceType = quotedAfter(helper, "String SOURCE_TYPE =");
        assertTrue(sourceType != null && catalog.contains('"' + sourceType + '"'),
                "The family's exact source type does not match RefinedStorageCompatHelper.SOURCE_TYPE ("
                        + sourceType + "), so the two can never join up");
        final String readings = read(MAIN.resolve("statsmk3/page/RsReadings.java"));
        assertTrue(readings.contains("FAMILY = \"storage.refined_storage\""),
                "RsReadings looks up a different family id than the catalog registers");
    }

    @Test
    void pageIsInTheRegistry() throws IOException {
        assertTrue(read(MAIN.resolve("statsmk3/page/Mk3PageRegistry.java"))
                        .contains("register(new RefinedStoragePage())"),
                "RefinedStoragePage is never registered, so the tab never appears");
    }

    @Test
    void faceModeIsDeclaredDispatchedAndFed() throws IOException {
        assertTrue(read(MAIN.resolve("statsmk3/Mk3Settings.java"))
                        .matches("(?s).*\\bREFINED_STORAGE\\s*[,;].*"),
                "FaceMode has no REFINED_STORAGE constant");
        assertTrue(read(MAIN.resolve("statsmk3/paint/PanelPainter.java"))
                        .contains("RefinedStorageFacePainter.paint(s, area, model, lod)"),
                "PanelPainter never dispatches to the Refined Storage face painter");
        final String cache = read(MAIN.resolve("statsmk3/paint/PanelModelCache.java"));
        assertTrue(cache.contains("FaceMode.REFINED_STORAGE")
                        && cache.contains("safeRefinedStorage(be)"),
                "PanelModelCache never resolves the networks, so the face has nothing to draw");
    }

    @Test
    void panelPersistsAndSyncsTheNetworkStateUnderDistinctKeys() throws IOException {
        final String panel = read(MAIN.resolve("blockentity/StatsDisplayPanelBE.java"));
        assertTrue(panel.contains("this.writeRsNetworks(tag, false)"),
                "saveAdditional never writes the full network state, so a reload loses the history");
        assertTrue(panel.contains("this.writeRsNetworks(tag, true)"),
                "saveSyncAdditional never writes the compact state, so no client ever sees a network");
        assertTrue(panel.contains("this.readRsNetworks(tag)"),
                "loadAdditional never reads it back");
        final String write = block(panel, "private void writeRsNetworks");
        assertTrue(write.contains("\"RsNetSync\"") && write.contains("\"RsNet\""),
                "The disk and sync forms must use distinct tag keys");
    }

    private static String block(String source, String marker) {
        final int start = source.indexOf(marker);
        if (start < 0) {
            return "";
        }
        final int open = source.indexOf('{', start);
        if (open < 0) {
            return source.substring(start);
        }
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
        return source.substring(start);
    }

    private static String quotedAfter(String source, String marker) {
        final int at = source.indexOf(marker);
        if (at < 0) {
            return null;
        }
        final int open = source.indexOf('"', at);
        final int close = open < 0 ? -1 : source.indexOf('"', open + 1);
        return close < 0 ? null : source.substring(open + 1, close);
    }

    private static String read(Path path) throws IOException {
        assertTrue(Files.exists(path), path + " is missing");
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
