package com.createstats.statsmk3.ui;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.createstats.statsmk3.RasterSurface;
import com.createstats.util.PreviewImages;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextFieldTest {
    private static final int ENTER = 257;
    private static final int ESCAPE = 256;
    private static final int BACKSPACE = 259;
    private static final int DELETE = 261;
    private static final int LEFT = 263;
    private static final int RIGHT = 262;
    private static final int HOME = 268;
    private static final int END = 269;

    private static void type(TextField f, String s) {
        for (final char c : s.toCharArray()) {
            f.charTyped(c, 0);
        }
    }

    @Test
    void typesCommitsAndReports() {
        final List<String> sent = new ArrayList<>();
        final TextField f = new TextField(sent::add);
        f.begin("");
        type(f, "Kiln line");
        assertEquals("Kiln line", f.text());
        assertTrue(f.editing());

        f.keyPressed(ENTER, 0, 0);
        assertFalse(f.editing());
        assertEquals(List.of("Kiln line"), sent);
    }

    @Test
    void escapeCancelsWithoutSending() {
        final List<String> sent = new ArrayList<>();
        final TextField f = new TextField(sent::add);
        f.begin("Kiln line");
        type(f, "!!!");
        f.keyPressed(ESCAPE, 0, 0);
        assertFalse(f.editing());
        assertTrue(sent.isEmpty());
    }

    @Test
    void caretMovesAndEdits() {
        final TextField f = new TextField(s -> { });
        f.begin("Kiln");
        f.keyPressed(HOME, 0, 0);
        type(f, "The ");
        assertEquals("The Kiln", f.text());

        f.keyPressed(END, 0, 0);
        f.keyPressed(BACKSPACE, 0, 0);
        assertEquals("The Kil", f.text());

        f.keyPressed(LEFT, 0, 0);
        f.keyPressed(LEFT, 0, 0);
        f.keyPressed(DELETE, 0, 0);
        assertEquals("The Kl", f.text());

        f.keyPressed(RIGHT, 0, 0);
        type(f, "?");
        assertEquals("The Kl?", f.text());
    }

    @Test
    void blankCommitsAsBlank() {
        final List<String> sent = new ArrayList<>();
        final TextField f = new TextField(sent::add);
        f.begin("Kiln line");
        for (int i = 0; i < 40; i++) {
            f.keyPressed(BACKSPACE, 0, 0);
        }
        f.keyPressed(ENTER, 0, 0);
        assertEquals(List.of(""), sent);
    }

    @Test
    void refusesFormattingCodesAndControlCharacters() {
        final TextField f = new TextField(s -> { });
        f.begin("");
        type(f, "a§cb");
        f.charTyped('\n', 0);
        assertEquals("acb", f.text());
    }

    @Test
    void stopsAtTheLengthCap() {
        final TextField f = new TextField(s -> { });
        f.begin("");
        type(f, "x".repeat(TextField.MAX_LENGTH + 20));
        assertEquals(TextField.MAX_LENGTH, f.text().length());
    }

    @Test
    void anIdleFieldConsumesNothing() {
        final TextField f = new TextField(s -> { });
        assertFalse(f.editing());
        assertFalse(f.keyPressed(ENTER, 0, 0));
        assertFalse(f.charTyped('x', 0));
        assertEquals("", f.text());
    }

    @Test
    void rendersItsStates() throws IOException {
        final File out = new File("fixture_preview");
        if (!out.exists() && !out.mkdirs()) {
            throw new IOException("could not create " + out);
        }
        final int w = 460;
        final int rowH = 46;
        final String[] captions = {
            "idle, empty: the placeholder",
            "editing: caret at the end",
            "editing: caret moved into the word",
            "editing: name longer than the box, scrolled to the caret",
        };
        final BufferedImage sheet = new BufferedImage(w + 40, captions.length * (rowH + 26) + 20,
                BufferedImage.TYPE_INT_RGB);
        final Graphics2D g = sheet.createGraphics();
        g.setColor(new Color(0x07080B));
        g.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());

        for (int i = 0; i < captions.length; i++) {
            final RasterSurface s = new RasterSurface(w, rowH, 0x111318);
            final TextField f = new TextField(v -> { }).placeholder("Name this machine");
            f.setBounds(new Rect(12.0f, 12.0f, w - 24.0f, 22.0f));
            switch (i) {
                case 1 -> f.begin("Kiln line");
                case 2 -> {
                    f.begin("Kiln line");
                    for (int k = 0; k < 5; k++) {
                        f.keyPressed(LEFT, 0, 0);
                    }
                }
                case 3 -> f.begin("Northwest andesite line, upper deck");
                default -> {
                }
            }
            f.update(0.0f);
            f.draw(s);
            final int y = 20 + i * (rowH + 26);
            g.drawImage(s.finish(), 20, y, null);
            g.setColor(new Color(0x98A0AD));
            g.setFont(new Font("SansSerif", Font.PLAIN, 11));
            g.drawString(captions[i], 20, y - 5);
        }
        g.dispose();
        PreviewImages.write(sheet, "png", new File(out, "mk3_text_field.png"));
    }

    @Test
    void beginningWithAnOverlongNameClampsIt() {
        final TextField f = new TextField(s -> { });
        f.begin("y".repeat(200));
        assertEquals(TextField.MAX_LENGTH, f.text().length());
    }
}
