package com.createstats.gui.pdu;

import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;

/** [1] */
public final class PduToast {

    public enum Severity {
        INFO, WARN, OK
    }

    private static final class Entry {
        final String text;
        final Severity severity;
        final long bornMs;
        float shownAlpha;

        Entry(String text, Severity severity, long bornMs) {
            this.text = text;
            this.severity = severity;
            this.bornMs = bornMs;
        }
    }

    private static final int MAX = 3;
    private static final long LIFE_MS = 2400L;
    private static final long FADE_MS = 400L;

    private final Deque<Entry> entries = new ArrayDeque<>();

    public void push(String text, Severity severity) {
        entries.addLast(new Entry(text, severity, Util.getMillis()));
        while (entries.size() > MAX) {
            entries.removeFirst();
        }
    }

    public void draw(GuiGraphics g, Font font, PduRect panel, float partialTick) {
        final long now = Util.getMillis();
        entries.removeIf(e -> now - e.bornMs > LIFE_MS + FADE_MS);
        if (entries.isEmpty()) {
            return;
        }
        int y = panel.bottom() - 8;
        final Iterator<Entry> it = entries.descendingIterator();
        while (it.hasNext()) {
            final Entry e = it.next();
            final long age = now - e.bornMs;
            e.shownAlpha = Ease.ease(e.shownAlpha, age > LIFE_MS ? 0.0f : 1.0f, 0.35f, partialTick);
            final float a = Mth.clamp(e.shownAlpha, 0.0f, 1.0f);
            if (a < 0.02f) {
                continue;
            }
            final int w = font.width(e.text) + 16;
            final int h = 14;
            final int slide = Math.round((1.0f - a) * 10.0f);
            final int x = panel.right() - 8 - w + slide;
            y -= h;
            final int alpha = Math.round(a * 255.0f);
            final int tint = switch (e.severity) {
                case WARN -> PduTheme.WARN;
                case OK -> PduTheme.OK;
                default -> PduTheme.INFO;
            };
            g.fill(x, y, x + w, y + h, PduTheme.withAlpha(PduTheme.BG_RAISED, Math.round(a * 232.0f)));
            PduTheme.outline(g, x, y, w, h, PduTheme.withAlpha(tint, alpha));
            g.fill(x, y, x + 2, y + h, PduTheme.withAlpha(tint, alpha));
            g.drawString(font, e.text, x + 6, y + 3, PduTheme.withAlpha(PduTheme.TEXT, alpha), false);
            y -= 4;
        }
    }
}
