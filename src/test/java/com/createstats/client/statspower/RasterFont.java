package com.createstats.client.statspower;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Rectangle;

public final class RasterFont extends net.minecraft.client.gui.Font {
    private static final Font AWT = new Font(Font.SANS_SERIF, Font.PLAIN, 9);

    public RasterFont() {
        super(rl -> null, false);
    }

    @Override
    public int width(String text) {
        return widthOf(text);
    }

    public static int widthOf(String text) {
        if (text == null) {
            return 0;
        }
        int total = 0;
        for (int i = 0; i < text.length(); i++) {
            final char c = text.charAt(i);
            if (c == '§' && i + 1 < text.length()) {
                i++;
                continue;
            }
            total += advance(c);
        }
        return total;
    }

    private static int advance(char c) {
        return switch (c) {
            case ' ' -> 4;
            case '!', '.', ',', ':', ';', 'i', '|', '\'' -> 2;
            case 'l', '`' -> 3;
            case '(', ')', '[', ']', '{', '}', 't', 'I', '"', '*' -> 4;
            case 'f', 'k', '<', '>' -> 5;
            case '‹', '›', '·', '—', '…', '±', '▲', '▼' -> 6;
            default -> c < 32 ? 0 : 6;
        };
    }

    static void paint(Graphics2D g, String text, int x, int y, int rgb, int alpha, int[] clip) {
        g.setFont(AWT);
        g.setColor(new Color(rgb | (alpha << 24), true));
        if (clip != null) {
            g.setClip(new Rectangle(clip[0], clip[1],
                    Math.max(0, clip[2] - clip[0]), Math.max(0, clip[3] - clip[1])));
        } else {
            g.setClip(null);
        }
        int cx = x;
        for (int i = 0; i < text.length(); i++) {
            final char c = text.charAt(i);
            if (c == '§' && i + 1 < text.length()) {
                i++;
                continue;
            }
            g.drawString(String.valueOf(c), cx, y + 8);
            cx += advance(c);
        }
        g.setClip(null);
    }
}
