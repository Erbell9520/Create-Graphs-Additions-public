package com.createstats.statsmk3;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Set;

import com.createstats.statsmk3.paint.Lod;
import com.createstats.statsmk3.paint.PanelModel;
import com.createstats.train.map.ClientRailMap;
import com.createstats.train.map.RailHistory;
import com.createstats.train.map.RailMap;
import com.createstats.train.smart.ClientRailNetwork;
import com.createstats.train.smart.RailSnapshot;
import com.createstats.util.PreviewImages;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TrainFaceSheetTest {
    private static final File OUT = new File("fixture_preview");

    private static final float SCALE = 0.5f;

    @BeforeEach
    void seed() {
        RailHistory.clear();
        ClientRailMap.clear();
        ClientRailNetwork.accept(RailFixtures.network(), true);
    }

    @Test
    void board() throws IOException {
        final int[][] sizes = {{1, 1}, {2, 1}, {2, 2}, {3, 2}, {6, 1}, {2, 4}, {4, 3}, {6, 3}};
        sheet("mk3_face_train_board.png",
                "Departure board. One station on the left of each pair, the whole Central complex "
                        + "on the right.", sizes, size -> {
                    final PanelModel m = model(Mk3Settings.FaceMode.TRAIN_BOARD);
                    m.boardStation = "Central 1";
                    return m;
                }, size -> {
                    final PanelModel m = model(Mk3Settings.FaceMode.TRAIN_BOARD);
                    m.boardStation = "Central *";
                    return m;
                });
    }

    @Test
    void map() throws IOException {
        final int[][] sizes = {{1, 1}, {2, 2}, {3, 2}, {4, 3}};
        final Mk3Settings.MapFocus[] focuses = {Mk3Settings.MapFocus.NETWORK,
                Mk3Settings.MapFocus.LINE, Mk3Settings.MapFocus.STATION, Mk3Settings.MapFocus.NEAR};
        grid("mk3_face_train_map.png", "The wall map. Rows are focus; columns are assembly size.",
                sizes, focuses.length, (row, size) -> {
                    final PanelModel m = model(Mk3Settings.FaceMode.TRAIN_MAP);
                    m.mapFocus = focuses[row];
                    m.diagramLine = "Stone circuit";
                    m.boardStation = "Central *";
                    m.faceX = -40.0;
                    m.faceZ = 20.0;
                    m.mapRadius = 200;
                    return m;
                }, row -> focuses[row].name().toLowerCase(java.util.Locale.ROOT));
    }

    @Test
    void route() throws IOException {
        final int[][] sizes = {{1, 1}, {2, 1}, {4, 1}, {1, 3}, {2, 3}, {3, 2}, {6, 2}, {5, 3}};
        sheet("mk3_face_train_route.png",
                "Route strip. It turns vertical on a tall face, which is the shape beside a door.",
                sizes, size -> {
                    final PanelModel m = model(Mk3Settings.FaceMode.TRAIN_ROUTE);
                    m.diagramLine = "Stone circuit";
                    return m;
                }, null);
    }

    @Test
    void focus() throws IOException {
        final int[][] sizes = {{1, 1}, {2, 1}, {2, 2}, {3, 2}, {4, 2}, {5, 3}};
        sheet("mk3_face_train_focus.png",
                "One train. Left of each pair is running to time; right is held at a signal.",
                sizes, size -> {
                    final PanelModel m = model(Mk3Settings.FaceMode.TRAIN_FOCUS);
                    m.focusTrain = "Stone Express";
                    return m;
                }, size -> {
                    final PanelModel m = model(Mk3Settings.FaceMode.TRAIN_FOCUS);
                    m.focusTrain = "Coal Shuttle";
                    return m;
                });
    }

    @Test
    void status() throws IOException {
        final int[][] sizes = {{1, 1}, {2, 1}, {2, 2}, {3, 2}, {4, 3}, {6, 3}};
        sheet("mk3_face_train_status.png",
                "Network status. Left of each pair is the faulted railway; right is a healthy one.",
                sizes, size -> {
                    ClientRailNetwork.accept(RailFixtures.network(), true);
                    return model(Mk3Settings.FaceMode.TRAIN_STATUS);
                }, size -> {
                    ClientRailNetwork.accept(healthy(), true);
                    return model(Mk3Settings.FaceMode.TRAIN_STATUS);
                });
        ClientRailNetwork.accept(RailFixtures.network(), true);
    }

    @Test
    void detailLadder() throws IOException {
        ensureOut();
        final Mk3Settings.FaceMode[] modes = {
                Mk3Settings.FaceMode.TRAIN_BOARD, Mk3Settings.FaceMode.TRAIN_MAP,
                Mk3Settings.FaceMode.TRAIN_ROUTE, Mk3Settings.FaceMode.TRAIN_FOCUS,
                Mk3Settings.FaceMode.TRAIN_STATUS, Mk3Settings.FaceMode.TRAIN_DIAGRAM};
        final Lod[] levels = {Lod.FULL, Lod.REDUCED, Lod.GLYPH};
        final String[] names = {"full", "reduced", "glyph"};
        recordSomeHistory();

        final int cell = 256;
        final BufferedImage sheet = new BufferedImage(60 + levels.length * (cell + 20),
                50 + modes.length * (cell * 2 / 3 + 40), BufferedImage.TYPE_INT_RGB);
        final Graphics2D g = start(sheet,
                "Every railway face at every level of detail, on a 3 x 2 wall.");

        for (int m = 0; m < modes.length; m++) {
            final int y = 46 + m * (cell * 2 / 3 + 40);
            caption(g, modes[m].name().toLowerCase(java.util.Locale.ROOT), 24, y - 6);
            for (int l = 0; l < levels.length; l++) {
                final PanelModel model = configured(modes[m]);
                final BufferedImage img = RailFixtures.render(model, levels[l], 3, 2, SCALE);
                final int x = 24 + l * (cell + 20);
                g.drawImage(img, x, y, cell, cell * 2 / 3, null);
                caption(g, names[l], x, y + cell * 2 / 3 + 14);
            }
        }
        g.dispose();
        PreviewImages.write(sheet, "png", new File(OUT, "mk3_face_train_detail.png"));
    }

    @Test
    void closeUps() throws IOException {
        ensureOut();
        recordSomeHistory();
        final File dir = new File(OUT, "mk3_face_train");
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("could not create " + dir);
        }
        write(dir, "board_4x3", configured(Mk3Settings.FaceMode.TRAIN_BOARD), 4, 3);
        write(dir, "board_2x2", configured(Mk3Settings.FaceMode.TRAIN_BOARD), 2, 2);
        write(dir, "board_1x1", configured(Mk3Settings.FaceMode.TRAIN_BOARD), 1, 1);
        write(dir, "board_6x1", configured(Mk3Settings.FaceMode.TRAIN_BOARD), 6, 1);
        write(dir, "map_4x3", configured(Mk3Settings.FaceMode.TRAIN_MAP), 4, 3);
        write(dir, "map_2x2", configured(Mk3Settings.FaceMode.TRAIN_MAP), 2, 2);
        write(dir, "route_5x3", configured(Mk3Settings.FaceMode.TRAIN_ROUTE), 5, 3);
        write(dir, "route_2x1", configured(Mk3Settings.FaceMode.TRAIN_ROUTE), 2, 1);
        write(dir, "route_1x3", configured(Mk3Settings.FaceMode.TRAIN_ROUTE), 1, 3);
        write(dir, "focus_5x3", configured(Mk3Settings.FaceMode.TRAIN_FOCUS), 5, 3);
        write(dir, "focus_2x2", configured(Mk3Settings.FaceMode.TRAIN_FOCUS), 2, 2);
        write(dir, "status_4x3", configured(Mk3Settings.FaceMode.TRAIN_STATUS), 4, 3);
        write(dir, "diagram_5x3", configured(Mk3Settings.FaceMode.TRAIN_DIAGRAM), 5, 3);
    }

    private static void write(File dir, String name, PanelModel model, int w, int h)
            throws IOException {
        PreviewImages.write(RailFixtures.render(model, Lod.FULL, w, h, SCALE), "png",
                new File(dir, name + ".png"));
    }

    private interface Maker {
        PanelModel make(int[] size);
    }

    private interface RowMaker {
        PanelModel make(int row, int[] size);
    }

    private interface RowName {
        String name(int row);
    }

    private void sheet(String file, String caption, int[][] sizes, Maker left, Maker right)
            throws IOException {
        ensureOut();
        recordSomeHistory();
        final int cell = 236;
        final int perRow = 4;
        final int rows = (sizes.length + perRow - 1) / perRow;
        final int cols = Math.min(sizes.length, perRow) * (right == null ? 1 : 2);
        final BufferedImage sheet = new BufferedImage(
                48 + cols * (cell + 14), 52 + rows * (cell + 40), BufferedImage.TYPE_INT_RGB);
        final Graphics2D g = start(sheet, caption);

        for (int i = 0; i < sizes.length; i++) {
            final int[] size = sizes[i];
            final int row = i / perRow;
            final int col = (i % perRow) * (right == null ? 1 : 2);
            final int y = 48 + row * (cell + 40);
            place(g, RailFixtures.render(left.make(size), Lod.FULL, size[0], size[1], SCALE),
                    24 + col * (cell + 14), y, cell, size);
            if (right != null) {
                place(g, RailFixtures.render(right.make(size), Lod.FULL, size[0], size[1], SCALE),
                        24 + (col + 1) * (cell + 14), y, cell, size);
            }
        }
        g.dispose();
        PreviewImages.write(sheet, "png", new File(OUT, file));
    }

    private void grid(String file, String caption, int[][] sizes, int rowCount, RowMaker maker,
                      RowName rowName) throws IOException {
        ensureOut();
        recordSomeHistory();
        final int cell = 236;
        final BufferedImage sheet = new BufferedImage(
                48 + sizes.length * (cell + 14), 52 + rowCount * (cell + 40),
                BufferedImage.TYPE_INT_RGB);
        final Graphics2D g = start(sheet, caption);
        for (int r = 0; r < rowCount; r++) {
            final int y = 48 + r * (cell + 40);
            caption(g, rowName.name(r), 24, y - 6);
            for (int i = 0; i < sizes.length; i++) {
                final int[] size = sizes[i];
                place(g, RailFixtures.render(maker.make(r, size), Lod.FULL, size[0], size[1], SCALE),
                        24 + i * (cell + 14), y, cell, size);
            }
        }
        g.dispose();
        PreviewImages.write(sheet, "png", new File(OUT, file));
    }

    private static void place(Graphics2D g, BufferedImage img, int x, int y, int cell,
                              int[] size) {
        final float fit = Math.min(cell / (float) img.getWidth(), cell / (float) img.getHeight());
        final int w = Math.round(img.getWidth() * fit);
        final int h = Math.round(img.getHeight() * fit);
        g.drawImage(img, x, y, w, h, null);
        caption(g, size[0] + " x " + size[1] + "  (" + img.getWidth() + " x " + img.getHeight()
                + " px)", x, y + h + 14);
    }

    private static PanelModel model(Mk3Settings.FaceMode mode) {
        final PanelModel m = new PanelModel();
        m.faceMode = mode;
        m.title = "Railway";
        m.status = "LIVE";
        return m;
    }

    private static PanelModel configured(Mk3Settings.FaceMode mode) {
        final PanelModel m = model(mode);
        m.boardStation = "Central *";
        m.diagramLine = "Stone circuit";
        m.focusTrain = "Coal Shuttle";
        m.mapFocus = Mk3Settings.MapFocus.NETWORK;
        return m;
    }

    private static RailSnapshot healthy() {
        final RailSnapshot snap = RailFixtures.network();
        for (final RailSnapshot.TrainState t : snap.trains()) {
            t.derailed = false;
            t.conductorOk = true;
            t.navigationOk = true;
            t.trackOk = true;
            t.waitingForSignal = false;
            t.ticksWaitingForSignal = 0;
            t.blockedByTrain = null;
            t.schedulePaused = false;
            t.fuelTicks = 0;
            if (!t.moving()) {
                t.speed = 0.05f;
            }
        }
        return snap;
    }

    private static void recordSomeHistory() {
        RailHistory.clear();
        ClientRailMap.clear();
        final RailSnapshot snap = RailFixtures.network();
        final RailMap map = RailMap.build(snap, List.of(), true, Set.of());
        for (int step = 0; step < 420; step++) {
            snap.serverTick += 10L;
            for (final RailSnapshot.TrainState t : snap.trains()) {
                if (t.derailed || !t.hasSchedule || t.waitingForSignal) {
                    continue;
                }
                t.distanceToDestination = Math.max(0.0,
                        t.distanceToDestination - Math.abs(t.speed) * 200.0);
                if (t.distanceToDestination <= 0.0) {
                    t.distanceToDestination = t.distanceStartedAt;
                    t.currentEntry = (t.currentEntry + 1) % Math.max(1, t.stops.size());
                    final RailSnapshot.Station next =
                            snap.resolveFilter(t.stops.get(t.currentEntry), t.dimension);
                    if (next != null) {
                        t.destinationStation = next.id;
                        t.destinationName = next.name;
                    }
                }
            }
            map.refresh(snap);
            RailHistory.sample(snap, map);
        }
        ClientRailNetwork.accept(RailFixtures.network(), true);
    }

    private static void ensureOut() throws IOException {
        if (!OUT.exists() && !OUT.mkdirs()) {
            throw new IOException("could not create " + OUT);
        }
    }

    private static Graphics2D start(BufferedImage sheet, String caption) {
        final Graphics2D g = sheet.createGraphics();
        g.setColor(new Color(0x14161B));
        g.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        caption(g, caption, 24, 22);
        return g;
    }

    private static void caption(Graphics2D g, String text, int x, int y) {
        g.setColor(new Color(0x98A0AD));
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        g.drawString(text, x, y);
    }
}
