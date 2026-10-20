package com.createstats.client.timeswitch;

import com.createstats.timeswitch.TimeSwitchProgram;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class TimeSwitchStrip {
    public static final int CELL = 5;

    public static final int WIDTH = TimeSwitchProgram.HOURS * CELL + 3;

    public static final int HEIGHT = 13;

    public static final int HEIGHT_LABELLED = HEIGHT + 10;

    public static final int VERMILION = 0xFFE24B2A;
    private static final int OFF_CELL = 0xFF5E5E5E;
    private static final int SLOT_DARK = 0xFF373737;
    private static final int SLOT_LIGHT = 0xFFFFFFFF;
    private static final int SLOT_FILL = 0xFF8B8B8B;
    private static final int LABEL = 0xFF404040;
    private static final int MARK = 0xFF000000;

    private static final int NIGHT = 0xFF1F2B4E;
    private static final int DAWN = 0xFFE89A5A;
    private static final int DAY = 0xFF86BCDE;
    private static final int DUSK = 0xFFB06C6C;

    private TimeSwitchStrip() {
    }

    static int sky(int hour) {
        if (hour == 5) {
            return DAWN;
        }
        if (hour == 18) {
            return DUSK;
        }
        return hour >= 6 && hour < 18 ? DAY : NIGHT;
    }

    public static void draw(GuiGraphics g, Font font, int x, int y, int pins, int was, int nowHour, boolean labels) {
        g.fill(x, y, x + WIDTH, y + HEIGHT, SLOT_FILL);
        g.fill(x, y, x + WIDTH - 1, y + 1, SLOT_DARK);
        g.fill(x, y, x + 1, y + HEIGHT - 1, SLOT_DARK);
        g.fill(x + 1, y + HEIGHT - 1, x + WIDTH, y + HEIGHT, SLOT_LIGHT);
        g.fill(x + WIDTH - 1, y + 1, x + WIDTH, y + HEIGHT, SLOT_LIGHT);
        for (int h = 0; h < TimeSwitchProgram.HOURS; h++) {
            final int cx = x + 2 + h * CELL;
            final boolean in = TimeSwitchProgram.isIn(pins, h);
            final boolean changed = was >= 0 && TimeSwitchProgram.isIn(was, h) != in;

            g.fill(cx, y + 3, cx + CELL - 1, y + 8, in ? VERMILION : OFF_CELL);
            g.fill(cx, y + 9, cx + CELL - 1, y + 11, sky(h));
            if (changed) {
                g.fill(cx, y + 2, cx + CELL - 1, y + 3, MARK);
                g.fill(cx, y + 8, cx + CELL - 1, y + 9, MARK);
            }
        }
        if (nowHour >= 0) {
            final int nx = x + 2 + nowHour * CELL + (CELL - 1) / 2;
            g.fill(nx - 1, y - 2, nx + 2, y - 1, MARK);
            g.fill(nx, y - 1, nx + 1, y + 1, MARK);
        }
        if (labels) {
            for (int h = 0; h <= TimeSwitchProgram.HOURS; h += 6) {
                final String s = Integer.toString(h);
                final int lx = x + 2 + h * CELL - (h == TimeSwitchProgram.HOURS ? 1 : 0) - font.width(s) / 2;
                g.drawString(font, s, lx, y + HEIGHT + 1, LABEL, false);
            }
        }
    }
}
