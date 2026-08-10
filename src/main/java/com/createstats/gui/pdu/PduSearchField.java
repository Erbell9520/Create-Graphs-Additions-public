package com.createstats.gui.pdu;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.lwjgl.glfw.GLFW;

/** [1] */
public final class PduSearchField {

    public static final int HEIGHT = 14;

    private final String placeholder;
    private final StringBuilder text = new StringBuilder();
    private boolean focused;
    private PduRect bounds = new PduRect(0, 0, 0, 0);
    private PduRect clearBounds = new PduRect(0, 0, 0, 0);

    public PduSearchField(String placeholder) {
        this.placeholder = placeholder;
    }

    public String text() {
        return text.toString();
    }

    public boolean isEmpty() {
        return text.length() == 0;
    }

    public boolean isFocused() {
        return focused;
    }

    public void setFocused(boolean focused) {
        this.focused = focused;
    }

    public void clear() {
        text.setLength(0);
    }

    /**
     * Consumes a typed character, returning whether it was taken, so the screen only swallows
     * keystrokes a focused field actually wants and everything else still reaches the shortcuts.
     */
    public boolean charTyped(char c) {
        if (!focused || c < ' ' || c == 127) {
            return false;
        }
        text.append(c);
        return true;
    }

    /** Handles the editing keys the field owns (backspace). Returns whether the key was consumed. */
    public boolean keyPressed(int keyCode) {
        if (!focused) {
            return false;
        }
        if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            if (text.length() > 0) {
                text.deleteCharAt(text.length() - 1);
            }
            return true;
        }
        return false;
    }

    public void draw(GuiGraphics g, Font font, int x, int y, int w, int mouseX, int mouseY) {
        this.bounds = new PduRect(x, y, w, HEIGHT);
        final boolean hovered = bounds.contains(mouseX, mouseY);
        g.fill(x, y, x + w, y + HEIGHT, focused || hovered ? PduTheme.BG_RAISED : PduTheme.BG_PANEL);
        PduTheme.outline(g, x, y, w, HEIGHT, focused ? PduTheme.STROKE_HOT : PduTheme.STROKE);

        // [2]
        PduTheme.outline(g, x + 4, y + 4, 4, 4, PduTheme.TEXT_FAINT);
        g.fill(x + 7, y + 7, x + 9, y + 9, PduTheme.TEXT_FAINT);

        final int textX = x + 12;
        final boolean showClear = !isEmpty();
        final int textRight = x + w - (showClear ? 12 : 4);
        if (isEmpty()) {
            g.drawString(font, placeholder, textX, y + 3, PduTheme.TEXT_FAINT, false);
        } else {
            g.drawString(font, tail(font, textRight - textX), textX, y + 3, PduTheme.TEXT, false);
        }

        if (showClear) {
            final int clx = x + w - 9;
            clearBounds = new PduRect(clx - 2, y + 1, 10, HEIGHT - 2);
            g.drawString(font, "x", clx, y + 3,
                    clearBounds.contains(mouseX, mouseY) ? PduTheme.TEXT : PduTheme.TEXT_DIM, false);
        } else {
            clearBounds = new PduRect(0, 0, 0, 0);
        }

        if (focused) {
            PduTheme.focusRing(g, x, y, w, HEIGHT);
        }
    }

    /** True if the click landed on the clear button, which empties the field. Test this before {@link #clicked}. */
    public boolean clickedClear(double mx, double my) {
        if (clearBounds.contains(mx, my)) {
            clear();
            return true;
        }
        return false;
    }

    /** True if the click landed anywhere in the field, which the screen turns into focus. */
    public boolean clicked(double mx, double my) {
        return bounds.contains(mx, my);
    }

    /** The visible tail of the text when it overflows the field, so the newest typing stays in view. */
    private String tail(Font font, int maxW) {
        final String s = text.toString();
        if (font.width(s) <= maxW) {
            return s;
        }
        int start = 0;
        while (start < s.length() && font.width(s.substring(start)) > maxW) {
            start++;
        }
        return s.substring(start);
    }
}
