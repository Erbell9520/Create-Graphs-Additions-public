package com.createstats.gui.home;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

// vanilla's own pixels, checked against generic_54.png
public final class HomeKit {
    private HomeKit() {
    }

    public static final int OUTLINE = 0xFF000000;
    public static final int HILITE = 0xFFFFFFFF;
    public static final int FACE = 0xFFC6C6C6;
    public static final int RECESS = 0xFF8B8B8B;
    public static final int SHADOW = 0xFF555555;
    public static final int SLOT_DARK = 0xFF373737;

    public static final int TITLE = 0xFF404040;

    public static final int SOFT = 0xFF606060;

    public static final int OK_INK = 0xFF1F5A1F;
    public static final int WARN_INK = 0xFF7A4A00;
    public static final int BAD_INK = 0xFF8A1A1A;

    public static final int WELL_OK = 0xFF0E3A0E;
    public static final int WELL_OFF = 0xFF262626;
    public static final int WELL_WARN = 0xFF4A2800;
    public static final int WELL_BAD = 0xFF5A0A0A;

    public static final int FACE_SOFT = 0xFF555555;

    public static final int BAND = 0xFF7B7B7B;
    public static final int BAND_PICKED = 0xFF9C9C9C;
    public static final int BAND_EDGE = 0xFF5E5E5E;

    public static final int SELECTED = 0xFFA2A2A2;

    public static final int LAMP_EDGE = 0xFFE6994A;
    public static final int LAMP_MID = 0xFFF2BD74;
    public static final int LAMP_CORE = 0xFFF6DAB4;

    public static final int FILL_BAR = 0xFF6666FF;
    public static final int XP_GREEN = 0xFF80FF20;

    public static ResourceLocation sprite(String name) {
        return ResourceLocation.withDefaultNamespace(name);
    }

    public static final ResourceLocation SLOT = sprite("container/slot");
    public static final ResourceLocation BUTTON = sprite("widget/button");
    public static final ResourceLocation BUTTON_HOT = sprite("widget/button_highlighted");
    public static final ResourceLocation BUTTON_OFF = sprite("widget/button_disabled");
    public static final ResourceLocation SLIDER = sprite("widget/slider");
    public static final ResourceLocation SLIDER_HOT = sprite("widget/slider_highlighted");
    public static final ResourceLocation HANDLE = sprite("widget/slider_handle");
    public static final ResourceLocation HANDLE_HOT = sprite("widget/slider_handle_highlighted");
    public static final ResourceLocation CHECK = sprite("widget/checkbox");
    public static final ResourceLocation CHECK_ON = sprite("widget/checkbox_selected");
    public static final ResourceLocation CHECK_HOT = sprite("widget/checkbox_highlighted");
    public static final ResourceLocation CHECK_ON_HOT = sprite("widget/checkbox_selected_highlighted");
    public static final ResourceLocation SCROLLER = sprite("widget/scroller");
    public static final ResourceLocation HOTBAR = sprite("hud/hotbar");
    public static final ResourceLocation HOTBAR_SELECTION = sprite("hud/hotbar_selection");
    public static final ResourceLocation TAB = sprite("recipe_book/tab");
    public static final ResourceLocation TAB_SELECTED = sprite("recipe_book/tab_selected");
    public static final ResourceLocation MAP_BACKGROUND =
            ResourceLocation.withDefaultNamespace("textures/map/map_background.png");

    public static ResourceLocation decoration(String name) {
        return ResourceLocation.withDefaultNamespace("textures/map/decorations/" + name + ".png");
    }

    public static void frame(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x + 3, y + 3, x + w - 3, y + h - 3, FACE);
        g.fill(x + 2, y, x + w - 3, y + 1, OUTLINE);
        g.fill(x + 3, y + h - 1, x + w - 2, y + h, OUTLINE);
        g.fill(x, y + 2, x + 1, y + h - 3, OUTLINE);
        g.fill(x + w - 1, y + 3, x + w, y + h - 2, OUTLINE);
        g.fill(x + 1, y + 1, x + 2, y + 2, OUTLINE);
        g.fill(x + w - 3, y + 1, x + w - 2, y + 2, OUTLINE);
        g.fill(x + w - 2, y + 2, x + w - 1, y + 3, OUTLINE);
        g.fill(x + w - 2, y + h - 2, x + w - 1, y + h - 1, OUTLINE);
        g.fill(x + 1, y + h - 3, x + 2, y + h - 2, OUTLINE);
        g.fill(x + 2, y + h - 2, x + 3, y + h - 1, OUTLINE);
        g.fill(x + 2, y + 1, x + w - 3, y + 3, HILITE);
        g.fill(x + 1, y + 2, x + 3, y + h - 3, HILITE);
        g.fill(x + 3, y + 3, x + 4, y + 4, HILITE);
        g.fill(x + w - 3, y + 3, x + w - 1, y + h - 2, SHADOW);
        g.fill(x + 3, y + h - 3, x + w - 2, y + h - 1, SHADOW);
        g.fill(x + w - 4, y + h - 4, x + w - 3, y + h - 3, SHADOW);
        g.fill(x + w - 3, y + 2, x + w - 2, y + 3, FACE);
        g.fill(x + 2, y + h - 3, x + 3, y + h - 2, FACE);
    }

    public static void well(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, RECESS);
        g.fill(x, y, x + w - 1, y + 1, SLOT_DARK);
        g.fill(x, y, x + 1, y + h - 1, SLOT_DARK);
        g.fill(x + 1, y + h - 1, x + w, y + h, HILITE);
        g.fill(x + w - 1, y + 1, x + w, y + h, HILITE);
    }

    public static void slot(GuiGraphics g, int x, int y) {
        g.blitSprite(SLOT, x, y, 18, 18);
    }

    public static void glow(GuiGraphics g, int x, int y) {
        g.fill(x + 1, y + 1, x + 17, y + 17, LAMP_EDGE);
        g.fill(x + 2, y + 2, x + 16, y + 16, LAMP_MID);
        g.fill(x + 4, y + 4, x + 14, y + 14, LAMP_CORE);
    }

    public static void slotHighlight(GuiGraphics g, int x, int y) {
        g.fillGradient(net.minecraft.client.renderer.RenderType.guiOverlay(), x + 1, y + 1, x + 17, y + 17,
                0x80FFFFFF, 0x80FFFFFF, 0);
    }

    public static void outline(GuiGraphics g, int x, int y, int w, int h, int c) {
        g.fill(x, y, x + w, y + 1, c);
        g.fill(x, y + h - 1, x + w, y + h, c);
        g.fill(x, y, x + 1, y + h, c);
        g.fill(x + w - 1, y, x + w, y + h, c);
    }

    public static void groove(GuiGraphics g, int x0, int x1, int y) {
        g.fill(x0, y, x1, y + 1, RECESS);
        g.fill(x0, y + 1, x1, y + 2, HILITE);
    }

    public static void button(GuiGraphics g, Font font, Component label, int x, int y, int w, int h, boolean hot,
                              boolean active) {
        g.blitSprite(!active ? BUTTON_OFF : hot ? BUTTON_HOT : BUTTON, x, y, w, h);
        final int colour = active ? 0xFFFFFFFF : 0xFFA0A0A0;
        final String text = trim(font, label.getString(), w - 6);
        g.drawCenteredString(font, text, x + w / 2, y + (h - 8) / 2, colour);
    }

    public static void slider(GuiGraphics g, Font font, Component label, int x, int y, int w, int h, float frac,
                              boolean hot) {
        g.blitSprite(hot ? SLIDER_HOT : SLIDER, x, y, w, h);
        final int hx = x + Math.round(Math.max(0f, Math.min(1f, frac)) * (w - 8));
        g.blitSprite(hot ? HANDLE_HOT : HANDLE, hx, y, 8, h);
        g.drawCenteredString(font, trim(font, label.getString(), w - 6), x + w / 2, y + (h - 8) / 2, 0xFFFFFFFF);
    }

    public static void checkbox(GuiGraphics g, Font font, Component label, int x, int y, boolean on, boolean hot,
                                int ink) {
        g.blitSprite(on ? (hot ? CHECK_ON_HOT : CHECK_ON) : (hot ? CHECK_HOT : CHECK), x, y, 17, 17);
        g.drawString(font, label, x + 21, y + 5, ink, false);
    }

    public static void xpText(GuiGraphics g, Font font, String s, int x, int y) {
        g.drawString(font, s, x + 1, y, 0xFF000000, false);
        g.drawString(font, s, x - 1, y, 0xFF000000, false);
        g.drawString(font, s, x, y + 1, 0xFF000000, false);
        g.drawString(font, s, x, y - 1, 0xFF000000, false);
        g.drawString(font, s, x, y, XP_GREEN, false);
    }

    public static void labelBox(GuiGraphics g, Font font, String s, int x, int y) {
        final int w = font.width(s);
        g.fill(x - 1, y - 1, x + w + 1, y + 9, 0xCC000000);
        g.drawString(font, s, x, y, 0xFFFFFFFF, false);
    }

    public static final int LABEL_ICON = 10;

    public static void labelBoxIcon(GuiGraphics g, Font font, ItemStack icon, String s, int x, int y) {
        final int w = (s.isEmpty() ? 8 : LABEL_ICON + font.width(s));
        g.fill(x - 1, y - 1, x + w + 1, y + 9, 0xCC000000);
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(0.5f, 0.5f, 1f);
        g.renderItem(icon, 0, 0);
        g.pose().popPose();
        if (!s.isEmpty()) {
            g.drawString(font, s, x + LABEL_ICON, y, 0xFFFFFFFF, false);
        }
    }

    public static void dot(GuiGraphics g, int x, int y, int colour) {
        g.fill(x, y, x + 3, y + 3, 0xFF202020);
        g.fill(x + 1, y + 1, x + 2, y + 2, colour);
    }

    public static String trim(Font font, String s, int max) {
        if (font.width(s) <= max) {
            return s;
        }
        return font.plainSubstrByWidth(s, Math.max(0, max - font.width("..."))) + "...";
    }

    private static final String[] BULB = {"..###..", ".#YYY#.", "#YWYYY#", "#YYYYY#", ".#YYY#.", "..#B#..", "..###.."};
    private static final String[] BOX = {"#######", "#hhhLL#", "#hCCLL#", "#hCCCC#", "#hCCCC#", "#hCCCC#", "#######"};
    private static final String[] SOCKET = {".#####.", "#FFFFF#", "#FHFHF#", "#FFFFF#", "#FFFFF#", ".#####."};
    private static final String[] CROSS = {"#...#", ".#.#.", "..#..", ".#.#.", "#...#"};

    public enum Mark { BULB, SOCKET, BOX }

    public static void mark(GuiGraphics g, Mark mark, int cx, int cy, boolean lit, int led, boolean ghost) {
        final int a = ghost ? 0x70000000 : 0xFF000000;
        switch (mark) {
            case BULB -> {
                final boolean starved = !lit && (led & 0xFFFFFF) == 0xFFAA00;
                pix(g, BULB, cx - 3, cy - 3, '#', a | 0x1A1A1A,
                        'Y', a | (lit ? 0xFFDF4F : starved ? 0xC8872E : 0x8A8A8A),
                        'W', a | (lit ? 0xFFFFFF : starved ? 0xE8B868 : 0xB8B8B8), 'B', a | 0x6E6E6E);
            }
            case SOCKET -> pix(g, SOCKET, cx - 3, cy - 3, '#', a | 0x1A1A1A, 'F', a | (led & 0xFFFFFF),
                    'H', a | 0x2A2A2A, '.', 0);
            case BOX -> pix(g, BOX, cx - 3, cy - 3, '#', a | 0x101010, 'C', a | 0x4A4A4A, 'h', a | 0x7A7A7A,
                    'L', a | (led & 0xFFFFFF));
        }
    }

    public static void spawnCross(GuiGraphics g, int x, int y) {
        pix(g, CROSS, x, y, '#', 0xFFFF5555);
    }

    private static final String[] SPOT = {"..###..", ".#...#.", "#..#..#", "#.###.#", "#..#..#", ".#...#.", "..###.."};

    public static void lampSpot(GuiGraphics g, int cx, int cy) {
        pix(g, SPOT, cx - 3, cy - 3, '#', 0xFFFFD84A);
    }

    private static final String[] DISC9 = {"...###...", "..#YYY#..", ".#YYYYY#.", "#YYYWYYY#", "#YYYYYYY#", "#YYYYYYY#",
            ".#YYYYY#.", "..#YYY#..", "...###..."};
    private static final String[] DISC7 = {"..###..", ".#YYY#.", "#YYWYY#", "#YYYYY#", "#YYYYY#", ".#YYY#.", "..###.."};
    private static final String[] DISC5 = {".###.", "#YYY#", "#YWY#", "#YYY#", ".###."};

    public static final int LAMP_WARM = 0xFFDF4F;

    public enum Lamp { LIT, OFF, STARVED }

    private static int rim(Lamp l) {
        return switch (l) {
            case LIT -> 0xFF9A6A10;
            case STARVED -> 0xFF8A5A10;
            case OFF -> 0xFF303030;
        };
    }

    private static int core(Lamp l) {
        return switch (l) {
            case LIT -> 0xFFFFDF4F;
            case STARVED -> 0xFFE8B868;
            case OFF -> 0xFFE6E6E6;
        };
    }

    public static void lampDisc(GuiGraphics g, int cx, int cy, Lamp state, boolean small) {
        lampDisc(g, cx, cy, state, small ? 5 : 7, LAMP_WARM);
    }

    public static void lampDisc(GuiGraphics g, int cx, int cy, Lamp state, int size, int tint) {
        final String[] art = size >= 9 ? DISC9 : size >= 7 ? DISC7 : DISC5;
        final int half = art.length / 2;
        final int core = state == Lamp.LIT ? 0xFF000000 | tint : core(state);
        final int shine = state == Lamp.LIT ? 0xFF000000 | PlanPainter.mix(tint, 0xFFFFFF, 0.55f) : core;
        pix(g, art, cx - half, cy - half, '#', rim(state), 'Y', core, 'W', shine);
    }

    public static int discSize(int scale, boolean small) {
        return scale >= 10 ? (small ? 7 : 9) : (small ? 5 : 7);
    }

    private static final String[] NORTH = {"..#..", ".###.", "#...#", "##..#", "#.#.#", "#..##", "#...#"};

    public static void northMark(GuiGraphics g, int x, int y) {
        pix(g, NORTH, x, y, '#', 0xFF5A4A32);
    }

    public static void viewCone(GuiGraphics g, int length) {
        for (int k = 1; k <= length; k++) {
            final int half = Math.max(1, k * 2 / 3);
            g.fill(-half, -4 - k, half + 1, -3 - k, 0x38FFFFFF);
        }
    }

    public static void lampBar(GuiGraphics g, int x0, int y0, int x1, int y1, Lamp state) {
        g.fill(x0, y0, x1, y1, rim(state));
        g.fill(x0 + 1, y0 + 1, x1 - 1, y1 - 1, core(state));
        if (state == Lamp.LIT) {
            if (x1 - x0 >= y1 - y0) {
                g.fill(x0 + 2, y0 + 1, x1 - 2, y0 + 2, 0xFFFFFFE8);
            } else {
                g.fill(x0 + 1, y0 + 2, x0 + 2, y1 - 2, 0xFFFFFFE8);
            }
        }
    }

    private static final String[] BED = {"RRRW", "RRRW", "B..B"};
    private static final String[] DOOR = {"DDD", "DKD", "DDD", "DDD"};
    private static final String[] SUN = {".YY.", "YYYY", "YYYY", ".YY."};

    public enum Badge { BEDTIME, AWAY, HOME }

    public static void badge(GuiGraphics g, Badge badge, int x, int y) {
        final String[] art = switch (badge) {
            case BEDTIME -> BED;
            case AWAY -> DOOR;
            case HOME -> SUN;
        };
        final int w = art[0].length();
        g.fill(x - 1, y - 1, x + w + 1, y + art.length + 1, 0xFF1A1A1A);
        pix(g, art, x, y, 'R', 0xFFB02E26, 'W', 0xFFF0F0F0, 'B', 0xFF5A3A1A, 'D', 0xFF8F6A3A, 'K', 0xFFFFE066,
                'Y', 0xFFFFD84A, '.', 0xFF1A1A1A);
    }

    public static void lampSwitch(GuiGraphics g, int x, int y, boolean lit, boolean hot) {
        slot(g, x, y);
        g.blit(lit ? LAMP_ON : LAMP_OFF, x + 1, y + 1, 0, 0, 16, 16, 16, 16);
        if (hot) {
            slotHighlight(g, x, y);
        }
    }

    public static final ResourceLocation LAMP_ON = ResourceLocation.withDefaultNamespace(
            "textures/block/redstone_lamp_on.png");
    public static final ResourceLocation LAMP_OFF = ResourceLocation.withDefaultNamespace(
            "textures/block/redstone_lamp.png");

    private static void pix(GuiGraphics g, String[] art, int x, int y, Object... palette) {
        for (int j = 0; j < art.length; j++) {
            final String row = art[j];
            for (int i = 0; i < row.length(); i++) {
                final char ch = row.charAt(i);
                for (int p = 0; p + 1 < palette.length; p += 2) {
                    if ((Character) palette[p] == ch) {
                        final int c = (Integer) palette[p + 1];
                        if ((c >>> 24) != 0) {
                            g.fill(x + i, y + j, x + i + 1, y + j + 1, c);
                        }
                        break;
                    }
                }
            }
        }
    }

    public static void parchment(GuiGraphics g, int x, int y, int w, int h) {
        final int b = 4;
        final int t = 64;
        blitPart(g, x, y, b, b, 0, 0);
        blitPart(g, x + w - b, y, b, b, t - b, 0);
        blitPart(g, x, y + h - b, b, b, 0, t - b);
        blitPart(g, x + w - b, y + h - b, b, b, t - b, t - b);
        tile(g, x + b, y, w - 2 * b, b, b, 0, t - 2 * b, b);
        tile(g, x + b, y + h - b, w - 2 * b, b, b, t - b, t - 2 * b, b);
        tile(g, x, y + b, b, h - 2 * b, 0, b, b, t - 2 * b);
        tile(g, x + w - b, y + b, b, h - 2 * b, t - b, b, b, t - 2 * b);
        tile(g, x + b, y + b, w - 2 * b, h - 2 * b, b, b, t - 2 * b, t - 2 * b);
    }

    private static void blitPart(GuiGraphics g, int x, int y, int w, int h, int u, int v) {
        g.blit(MAP_BACKGROUND, x, y, u, v, w, h, 64, 64);
    }

    private static void tile(GuiGraphics g, int x, int y, int w, int h, int u, int v, int tw, int th) {
        for (int yy = 0; yy < h; yy += th) {
            for (int xx = 0; xx < w; xx += tw) {
                blitPart(g, x + xx, y + yy, Math.min(tw, w - xx), Math.min(th, h - yy), u, v);
            }
        }
    }
}
