package com.createstats.gui.pdu;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

public final class PduSegmented {
    private final String[] options;
    private int selected;
    private boolean focused;

    private final int[] segStarts;
    private int segTop;
    private int segHeight;

    public PduSegmented(String... options) {
        this.options = options.clone();
        this.segStarts = new int[options.length + 1];
    }

    public int selected() {
        return selected;
    }

    public void setSelected(int index) {
        if (index >= 0 && index < options.length) {
            selected = index;
        }
    }

    public int count() {
        return options.length;
    }

    public String option(int index) {
        return options[index];
    }

    public boolean isFocused() {
        return focused;
    }

    public void setFocused(boolean focused) {
        this.focused = focused;
    }

    public int width(Font font) {
        int w = 0;
        for (String o : options) {
            w += segWidth(font, o);
        }
        return w;
    }

    private static int segWidth(Font font, String label) {
        return font.width(label) + 12;
    }

    public void draw(GuiGraphics g, Font font, int x, int y, int height, int mouseX, int mouseY) {
        this.segTop = y;
        this.segHeight = height;
        int cx = x;
        for (int i = 0; i < options.length; i++) {
            segStarts[i] = cx;
            final int w = segWidth(font, options[i]);
            final boolean active = i == selected;
            final boolean hovered = mouseX >= cx && mouseX < cx + w && mouseY >= y && mouseY < y + height;
            g.fill(cx, y, cx + w, y + height,
                    active ? PduTheme.BG_RAISED : hovered ? PduTheme.BG_PANEL : PduTheme.BG_DEEP);
            g.drawString(font, options[i], cx + 6, y + (height - 8) / 2,
                    active ? PduTheme.TEXT : PduTheme.TEXT_DIM, false);

            if (active) {
                g.fill(cx + 4, y + height - 2, cx + w - 4, y + height - 1, PduTheme.ACCENT);
            }
            cx += w;
        }
        segStarts[options.length] = cx;
        PduTheme.outline(g, x, y, cx - x, height, PduTheme.STROKE);
        if (focused) {
            PduTheme.focusRing(g, x, y, cx - x, height);
        }
    }

    public PduRect segmentRect(int i) {
        return new PduRect(segStarts[i], segTop, segStarts[i + 1] - segStarts[i], segHeight);
    }

    public int segmentAt(double mx, double my) {
        if (options.length == 0 || my < segTop || my >= segTop + segHeight) {
            return -1;
        }
        for (int i = 0; i < options.length; i++) {
            if (mx >= segStarts[i] && mx < segStarts[i + 1]) {
                return i;
            }
        }
        return -1;
    }
}
