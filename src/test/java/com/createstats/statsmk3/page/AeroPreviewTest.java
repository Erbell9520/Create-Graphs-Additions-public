package com.createstats.statsmk3.page;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.createstats.aerial.AerialAlerts;
import com.createstats.aerial.AerialFixtures;
import com.createstats.aerial.AerialFixtures.Scene;
import com.createstats.aerial.AerialFrame;
import com.createstats.aerial.AerialRealistic;
import com.createstats.aerial.AerialSnapshot;
import com.createstats.aerial.AirframeMath;
import com.createstats.aerial.AirframeReadout;
import com.createstats.statsmk3.GlyphSurface;
import com.createstats.statsmk3.Mk3Settings;
import com.createstats.statsmk3.paint.Lod;
import com.createstats.statsmk3.paint.PanelModel;
import com.createstats.statsmk3.paint.PanelPainter;
import com.createstats.statsmk3.ui.Rect;
import com.createstats.statsmk3.ui.Shapes;
import com.createstats.statsmk3.view.StudioLayout;
import com.createstats.util.PreviewImages;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "AERO_PREVIEW", matches = ".+")
class AeroPreviewTest {
    private static final File OUT = new File("mk3_aero_preview");
    private static final int CAPTION = 0x98A0AD;
    private static final AeroSkin K = AeroSkin.DECK;

    static Rect pageArea(int screenW, int screenH) {
        final Rect v = StudioLayout.of(screenW, screenH).viewArea(false);
        return new Rect(0.0f, 0.0f, Math.round(v.w), Math.round(v.h));
    }

    static AerialFrame frame(AerialSnapshot s, boolean stale) {
        final AirframeReadout r = AirframeMath.read(s);
        final double age = stale ? 6.0 : 0.0;
        return new AerialFrame(r, AerialAlerts.evaluate(r, List.of(), age), age);
    }

    static AerialFrame ringing(AerialSnapshot s, double rungAgo) {
        final AirframeReadout r = AirframeMath.read(s);
        return new AerialFrame(r, AerialAlerts.evaluate(r, List.of(), 0.0), 0.0, rungAgo);
    }

    private static BufferedImage page(AerialFrame f, Rect area, int tab) {
        final GlyphSurface s = new GlyphSurface((int) area.w, (int) area.h, K.ground() & 0xFFFFFF);
        Shapes.rect(s, area, K.ground());
        AeroView.paintPage(s, area, f, tab, K);
        return s.finish();
    }

    private static BufferedImage face(AerialFrame f, Mk3Settings.FaceMode mode, int w, int h) {
        final Rect rect = AeroArtTest.wall(w, h, Mk3Settings.DEFAULT_FACE_SCALE);
        final PanelModel m = AeroArtTest.model(mode, f, true);
        final GlyphSurface s = new GlyphSurface(Math.round(rect.w), Math.round(rect.h), 0x000000);
        PanelPainter.paint(s, new Rect(0.0f, 0.0f, rect.w, rect.h), m, Lod.FULL);
        return s.finish();
    }

    @Test
    void theFlightDeckSheets() throws IOException {
        final int v = nextVersion();
        final AerialFrame owner = frame(AerialRealistic.scene(Scene.CRUISE), false);
        final AerialFrame kestrel = frame(AerialFixtures.full(Scene.CRUISE), false);

        final Rect mid = pageArea(853, 480);
        final Rect small = pageArea(640, 360);
        tabs(String.format(Locale.ROOT, "tabs_realistic_v%02d.png", v), "Every tab: the owner's ship, as the Aerial sends it",
                owner, mid);
        tabs(String.format(Locale.ROOT, "tabs_full_v%02d.png", v), "Every tab: the Kestrel, every section full", kestrel, mid);
        tabs(String.format(Locale.ROOT, "tabs_default_v%02d.png", v),
                "Every tab at the default size (1080p, GUI scale 3): the owner's ship", owner, small);

        final Mk3Settings.FaceMode[] modes = {Mk3Settings.FaceMode.AERO_DECK, Mk3Settings.FaceMode.AERO_BUDGET,
                Mk3Settings.FaceMode.AERO_TELEGRAPH, Mk3Settings.FaceMode.AERO_MAP, Mk3Settings.FaceMode.AERO_VOYAGE,
                Mk3Settings.FaceMode.AERO_SCOPE};
        final String[] modeNames = {"deck", "lift", "telegraph", "map", "voyage", "scope"};
        final int[][] walls = {{1, 1}, {3, 1}, {1, 2}, {2, 2}, {4, 3}};
        final List<BufferedImage> faces = new ArrayList<>();
        final List<String> faceCaptions = new ArrayList<>();
        for (int m = 0; m < modes.length; m++) {
            for (final int[] wh : walls) {
                faces.add(face(kestrel, modes[m], wh[0], wh[1]));
                faceCaptions.add(String.format(Locale.ROOT, "%s %dx%d", modeNames[m], wh[0], wh[1]));
            }
        }
        write(String.format(Locale.ROOT, "faces_v%02d.png", v), "Every face, cruising: the Kestrel", faces, faceCaptions,
                walls.length);

        final AerialSnapshot ownerMoored = AerialRealistic.scene(Scene.MOORED);
        final AerialFrame[] states = {owner, frame(AerialRealistic.scene(Scene.SINKING), false),
                frame(AerialFixtures.full(Scene.SINKING), false), frame(ownerMoored, false),
                frame(AerialFixtures.anchored(AerialRealistic.scene(Scene.CRUISE)), false),
                frame(AerialFixtures.weighing(ownerMoored), false), frame(AerialRealistic.scene(Scene.CRUISE), true),
                ringing(AerialRealistic.scene(Scene.CRUISE), 0.3)};
        final String[] stateNames = {"owner, cruise", "owner, sinking (cautions)", "Kestrel, sinking (a warning)",
                "owner, moored", "owner, anchored, nobody aboard", "owner, weighing off", "owner, no feed",
                "owner, the telegraph just rung"};
        final List<BufferedImage> deck = new ArrayList<>();
        final List<String> deckCaptions = new ArrayList<>();
        for (int i = 0; i < states.length; i++) {
            deck.add(face(states[i], Mk3Settings.FaceMode.AERO_DECK, 2, 2));
            deckCaptions.add(stateNames[i] + ", 2x2");
            deck.add(face(states[i], Mk3Settings.FaceMode.AERO_DECK, 4, 3));
            deckCaptions.add(stateNames[i] + ", 4x3");
        }
        write(String.format(Locale.ROOT, "states_v%02d.png", v), "The deck face in every state the ship can be in", deck,
                deckCaptions, 4);
    }

    private static void tabs(String name, String title, AerialFrame f, Rect area) throws IOException {
        final List<BufferedImage> images = new ArrayList<>();
        final List<String> captions = new ArrayList<>();
        for (int tab = 0; tab < AeroBridge.TABS.length; tab++) {
            images.add(page(f, area, tab));
            captions.add(String.format(Locale.ROOT, "%s (page %dx%d)", AeroBridge.TABS[tab], (int) area.w, (int) area.h));
        }
        write(name, title, images, captions, 2);
    }

    static int nextVersion() {
        int max = 0;
        final File[] files = OUT.listFiles();
        if (files != null) {
            final Pattern p = Pattern.compile("_v(\\d+)\\.png$");
            for (final File f : files) {
                final Matcher m = p.matcher(f.getName());
                if (m.find()) {
                    max = Math.max(max, Integer.parseInt(m.group(1)));
                }
            }
        }
        return max + 1;
    }

    private static void write(String name, String title, List<BufferedImage> images, List<String> captions, int cols)
            throws IOException {
        if (!OUT.exists() && !OUT.mkdirs()) {
            throw new IOException("could not create " + OUT);
        }
        final File file = new File(OUT, name);
        if (file.exists()) {
            throw new IOException(file + " exists: iterations are never overwritten");
        }
        final int pad = 16;
        final int cap = 18;
        final int head = 30;
        final int[] colW = new int[cols];
        final int rows = (images.size() + cols - 1) / cols;
        final int[] rowH = new int[rows];
        for (int i = 0; i < images.size(); i++) {
            colW[i % cols] = Math.max(colW[i % cols], images.get(i).getWidth());
            rowH[i / cols] = Math.max(rowH[i / cols], images.get(i).getHeight());
        }
        int w = pad;
        for (final int c : colW) {
            w += c + pad;
        }
        int h = head + pad;
        for (final int r : rowH) {
            h += r + cap + pad;
        }
        final BufferedImage sheet = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        final Graphics2D g = sheet.createGraphics();
        g.setColor(new Color(0x0E0F13));
        g.fillRect(0, 0, w, h);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 15));
        g.setColor(new Color(0xECEEF2));
        g.drawString(title, pad, 22);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        int y = head + pad;
        for (int row = 0; row < rows; row++) {
            int x = pad;
            for (int col = 0; col < cols; col++) {
                final int i = row * cols + col;
                if (i < images.size()) {
                    g.setColor(new Color(CAPTION));
                    g.drawString(captions.get(i), x, y + 12);
                    g.drawImage(images.get(i), x, y + cap, null);
                }
                x += colW[col] + pad;
            }
            y += rowH[row] + cap + pad;
        }
        g.dispose();
        PreviewImages.write(sheet, "png", file);
    }
}
