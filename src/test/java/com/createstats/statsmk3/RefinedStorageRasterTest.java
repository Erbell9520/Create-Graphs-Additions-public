package com.createstats.statsmk3;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.createstats.data.TimeSeriesStore;
import com.createstats.data.WindowedTrend;
import com.createstats.rs.RsNetworkState;
import com.createstats.statsmk3.Mk3Settings.FaceMode;
import com.createstats.statsmk3.page.RsArt;
import com.createstats.statsmk3.page.RsReadings;
import com.createstats.statsmk3.paint.Lod;
import com.createstats.statsmk3.paint.PanelModel;
import com.createstats.statsmk3.paint.RefinedStorageFacePainter;
import com.createstats.statsmk3.ui.Rect;
import com.createstats.util.PreviewImages;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RefinedStorageRasterTest {
    private static final File OUT = new File("fixture_preview");

    private static final int[][] FACE_SIZES = {{1, 1}, {2, 1}, {3, 2}, {5, 3}};

    private static RsNetworkState network(String focusItemId, int minutes) {
        final RsNetworkState server = new RsNetworkState();
        for (int second = 0; second <= minutes * 60; second++) {
            server.ingest(reading(second), second * 20L);
        }
        return RsNetworkState.loadSync(server.saveSync(focusItemId));
    }

    private static com.createstats.compat.RefinedStorageCompatHelper.NetworkRead reading(int second) {
        final List<com.createstats.compat.RefinedStorageCompatHelper.Entry> items = new ArrayList<>();

        items.add(entry("minecraft:iron_ingot", 128_450L + Math.round(900.0 * Math.sin(second / 41.0))));

        items.add(entry("minecraft:coal", Math.max(0L, 96_120L - second * 7L)));

        items.add(entry("minecraft:cobblestone", 52_004L + second * 23L));
        items.add(entry("minecraft:oak_log", 41_880L + Math.round(2400.0 * Math.sin(second / 73.0))));
        items.add(entry("minecraft:redstone", 18_204L));
        items.add(entry("minecraft:gold_ingot", 9_431L - second / 3L));
        items.add(entry("minecraft:diamond", 812L));
        items.add(entry("minecraft:netherite_scrap", 44L));
        long total = 0L;
        for (final var e : items) {
            total += e.amount();
        }
        return new com.createstats.compat.RefinedStorageCompatHelper.NetworkRead(
                true, total, 2_048_000L, 16_000L, items.size(), 2,
                18_400L + Math.round(600.0 * Math.sin(second / 17.0)), 32_000L,
                312, 18, items, List.of());
    }

    private static com.createstats.compat.RefinedStorageCompatHelper.Entry entry(String id, long n) {
        return new com.createstats.compat.RefinedStorageCompatHelper.Entry(id, name(id), null, n);
    }

    private static String name(String id) {
        return RsReadings.prettyId(id);
    }

    private static RsReadings.Board board(RsNetworkState state, boolean online) {
        final List<RsReadings.Stock> stock = new ArrayList<>();
        for (final RsNetworkState.Row row : state.rows()) {
            stock.add(new RsReadings.Stock(row.itemId(), null, name(row.itemId()), row.amount(),
                    row, channelHistory(row)));
        }
        return new RsReadings.Board("Main Store", online, state, stock);
    }

    private static TimeSeriesStore channelHistory(RsNetworkState.Row row) {
        final TimeSeriesStore store = TimeSeriesStore.forPanel();
        final float[] fine = row.seriesFor(WindowedTrend.Window.MINUTE);
        if (fine.length == 0) {
            for (int i = 0; i < 60; i++) {
                store.addSample(row.amount(), i * 20L);
            }
            return store;
        }
        for (int i = 0; i < fine.length; i++) {
            store.addSample(fine[i], i * 20L);
        }
        return store;
    }

    @Test
    void studioPageDrawsAtEverySize() throws IOException {
        OUT.mkdirs();
        final RsNetworkState full = network("minecraft:coal", 12);
        assertTrue(full.rows().size() >= 8, "the fixture network should have a real stock list");

        final int[][] sizes = {{960, 540}, {720, 420}, {1280, 720}};
        for (final int[] size : sizes) {
            for (final WindowedTrend.Window window : WindowedTrend.Window.values()) {
                final RasterSurface c = new RasterSurface(size[0], size[1], 0x0A0B0E);
                RsArt.page(c, new Rect(16.0f, 16.0f, size[0] - 32.0f, size[1] - 32.0f),
                        board(full, true), 1, window, "minecraft:coal", 96_120.0, 3.2f, 0.0f, "",
                        null);
                final BufferedImage image = c.finish();
                if (size[0] == 960 && window == WindowedTrend.Window.FIVE_MINUTES) {
                    write(image, "rs_page_960x540_5m");
                } else if (size[0] == 1280) {
                    write(image, "rs_page_1280x720_" + window.label());
                }
                assertNotNull(image);
            }
        }
    }

    @Test
    void studioPageSurvivesEveryDegenerateNetwork() {
        final Rect area = new Rect(0.0f, 0.0f, 900.0f, 500.0f);

        draw(area, null, WindowedTrend.Window.MINUTE, "");

        draw(area, new RsReadings.Board("Empty", true, new RsNetworkState(), List.of()),
                WindowedTrend.Window.HOUR, "");

        draw(area, board(network("", 3), false), WindowedTrend.Window.FIVE_MINUTES, "");

        draw(area, board(network("minecraft:iron_ingot", 6), true),
                WindowedTrend.Window.MINUTE, "minecraft:bedrock");

        draw(area, board(network("minecraft:coal", 0), true), WindowedTrend.Window.HOUR,
                "minecraft:coal");
    }

    private void draw(Rect area, RsReadings.Board board, WindowedTrend.Window window, String focus) {
        final RasterSurface c = new RasterSurface((int) area.w + 32, (int) area.h + 32, 0x0A0B0E);
        RsArt.page(c, new Rect(16.0f, 16.0f, area.w, area.h), board, 1, window, focus,
                board == null || board.stock().isEmpty() ? 0.0 : board.stock().get(0).amount(),
                1.0f, 0.0f, "", null);
        c.finish();
    }

    @Test
    void everyRowIsReportedInOrder() {
        final RsReadings.Board board = board(network("minecraft:coal", 8), true);
        final List<String> seen = new ArrayList<>();
        final List<Rect> rects = new ArrayList<>();
        final RasterSurface c = new RasterSurface(900, 500, 0x0A0B0E);
        RsArt.page(c, new Rect(0.0f, 0.0f, 900.0f, 500.0f), board, 1,
                WindowedTrend.Window.FIVE_MINUTES, "minecraft:coal", 0.0, 0.0f, 0.0f, "",
                (rect, itemId) -> {
                    rects.add(rect);
                    seen.add(itemId);
                });
        c.finish();
        assertEquals(board.stock().size(), seen.size(), "every row must be reported");
        for (int i = 0; i < seen.size(); i++) {
            assertEquals(board.stock().get(i).itemId(), seen.get(i), "row " + i + " is out of order");
        }
        for (int i = 1; i < rects.size(); i++) {
            assertEquals(RsArt.ROW_H, rects.get(i).y - rects.get(i - 1).y, 0.01f,
                    "rows must be evenly pitched, or a hit test lands between two of them");
        }
    }

    @Test
    void blockFaceDrawsAtEverySizeAndDetail() throws IOException {
        OUT.mkdirs();
        final RsNetworkState state = network("minecraft:coal", 12);
        for (final int[] size : FACE_SIZES) {
            for (final Lod lod : Lod.values()) {
                final PanelModel model = faceModel(board(state, true));
                final Rect face = faceRect(size[0], size[1]);
                final RasterSurface c = new RasterSurface(
                        Math.round(face.w), Math.round(face.h), 0x05070A);
                RefinedStorageFacePainter.paint(c, face, model, lod);
                final BufferedImage image = c.finish();
                if (lod == Lod.FULL) {
                    write(image, "rs_face_" + size[0] + "x" + size[1]);
                }
                assertNotNull(image);
            }
        }

        final PanelModel empty = faceModel(RsReadings.Board.NONE);
        empty.refinedStorage.clear();
        writeFace(empty, "rs_face_unlinked");
        writeFace(faceModel(board(network("", 4), false)), "rs_face_offline");
        writeFace(faceModel(new RsReadings.Board("New Store", true, new RsNetworkState(), List.of())),
                "rs_face_empty");
    }

    private void writeFace(PanelModel model, String name) throws IOException {
        final Rect face = faceRect(3, 2);
        final RasterSurface c = new RasterSurface(Math.round(face.w), Math.round(face.h), 0x05070A);
        RefinedStorageFacePainter.paint(c, face, model, Lod.FULL);
        write(c.finish(), name);
    }

    private static Rect faceRect(int blocksWide, int blocksTall) {
        final float px = PANEL_PIXELS_PER_BLOCK;
        final float texel = px / 16.0f;
        return new Rect(0.0f, 0.0f,
                blocksWide * px - texel * 4.0f,
                blocksTall * px - texel * 5.0f);
    }

    private static final float PANEL_PIXELS_PER_BLOCK = 128.0f;

    private static PanelModel faceModel(RsReadings.Board board) {
        final PanelModel model = new PanelModel();
        model.faceMode = FaceMode.REFINED_STORAGE;
        model.rsFocusItem = "minecraft:coal";
        model.rsWindow = WindowedTrend.Window.FIVE_MINUTES.ordinal();
        model.time = 4.0f;
        model.refinedStorage.add(board);
        return model;
    }

    private static void write(BufferedImage image, String name) throws IOException {
        PreviewImages.write(image, "PNG", new File(OUT, name + ".png"));
    }
}
