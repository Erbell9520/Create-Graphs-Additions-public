package com.createstats.gui.meter.plate;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;

@OnlyIn(Dist.CLIENT)
public final class RatingPlate {
    public static final ResourceLocation PLATE_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("create_stats", "textures/gui/power_meter/plate.png");
    public static final ResourceLocation ATLAS =
            ResourceLocation.fromNamespaceAndPath("create_stats", "textures/gui/power_meter/atlas.png");
    private static final int PLATE_TEX_W = 352;
    private static final int PLATE_TEX_H = 220;
    private static final int ATLAS_W = 128;
    private static final int ATLAS_H = 64;

    public enum Sprite {
        SCREW(0, 0, 5, 5),
        JEWEL_OFF(8, 0, 5, 5),
        JEWEL_GREEN(14, 0, 5, 5),
        JEWEL_AMBER(20, 0, 5, 5),
        JEWEL_RED(26, 0, 5, 5),
        TERMINAL(0, 8, 14, 14),
        GLAND(16, 8, 16, 14),
        KEY_UP(0, 24, 22, 16),
        KEY_DOWN(24, 24, 22, 16),
        KEY_HOVER(48, 24, 22, 16),
        STUD(0, 42, 16, 16),
        STUD_DOWN(18, 42, 16, 16),
        STUD_OPEN(36, 42, 16, 16),
        GLASS(64, 0, 24, 24),
        LAMP_OFF(90, 0, 3, 5),
        LAMP_ON(94, 0, 3, 5);

        final int u;
        final int v;
        public final int w;
        public final int h;

        Sprite(int u, int v, int w, int h) {
            this.u = u;
            this.v = v;
            this.w = w;
            this.h = h;
        }
    }

    private final Font font;
    private final PlateGeometry geo;

    private final int[] wear;
    private final List<int[]> grime = new ArrayList<>();

    public RatingPlate(Font font, PlateGeometry geo, List<PlateGeometry.Rect> windows) {
        this.font = font;
        this.geo = geo;
        final List<Integer> w = new ArrayList<>();
        for (int x = 4; x < geo.w - 4; x++) {
            if (vnoise(x, 4, 13) < 0.28f) {
                w.add(x);
                w.add(4);
            }
            if (vnoise(x, geo.h - 5, 17) < 0.22f) {
                w.add(x);
                w.add(geo.h - 5);
            }
        }
        for (int y = 5; y < geo.h - 5; y++) {
            if (vnoise(4, y, 19) < 0.24f) {
                w.add(4);
                w.add(y);
            }
        }
        this.wear = w.stream().mapToInt(Integer::intValue).toArray();
        for (final PlateGeometry.Rect r : windows) {
            grime.add(grimeRing(r));
        }
    }

    static float vnoise(int x, int y, int seed) {
        int h = x * 374761393 + y * 668265263 + seed * (int) 2246822519L;
        h = (h ^ (h >>> 13)) * 1274126177;
        return ((h ^ (h >>> 16)) & 0xFFFF) / 65535.0f;
    }

    private static int[] grimeRing(PlateGeometry.Rect r) {
        final List<Integer> pts = new ArrayList<>();
        for (int i = -1; i <= r.w(); i++) {
            for (final int yy : new int[]{r.y() - 1, r.bottom()}) {
                if (vnoise(r.x() + i, yy, 31) < 0.34f) {
                    pts.add(r.x() + i);
                    pts.add(yy);
                }
            }
        }
        for (int j = 0; j < r.h(); j++) {
            for (final int xx : new int[]{r.x() - 1, r.right()}) {
                if (vnoise(xx, r.y() + j, 37) < 0.34f) {
                    pts.add(xx);
                    pts.add(r.y() + j);
                }
            }
        }
        return pts.stream().mapToInt(Integer::intValue).toArray();
    }

    private static void px(GuiGraphics g, int x, int y, int c) {
        g.fill(x, y, x + 1, y + 1, c);
    }

    public void plate(GuiGraphics g) {
        final int pw = geo.w;
        final int ph = geo.h;
        g.fill(0, 0, pw, ph, PlateInk.RIM_OUT);
        g.fill(1, 1, pw - 1, ph - 1, PlateInk.RIM_FACE);
        g.fill(1, 1, pw - 1, 2, PlateInk.RIM_LIT);
        g.fill(1, 1, 2, ph - 1, PlateInk.RIM_LIT);
        g.fill(1, ph - 2, pw - 1, ph - 1, PlateInk.RIM_DARK);
        g.fill(pw - 2, 1, pw - 1, ph - 1, PlateInk.RIM_DARK);

        g.blit(PLATE_TEXTURE, 3, 3, 0.0f, 0.0f, pw - 6, ph - 6, PLATE_TEX_W, PLATE_TEX_H);
        g.drawManaged(() -> {
            g.fill(3, 3, pw - 3, 4, PlateInk.PLATE_HI);
            g.fill(3, 3, 4, ph - 3, PlateInk.PLATE_HI);
            g.fill(3, ph - 4, pw - 3, ph - 3, PlateInk.PLATE_LO);
            g.fill(pw - 4, 3, pw - 3, ph - 3, PlateInk.PLATE_LO);
            for (int i = 0; i < wear.length; i += 2) {
                px(g, wear[i], wear[i + 1], PlateInk.WEAR);
            }
            for (final int[] ring : grime) {
                for (int i = 0; i < ring.length; i += 2) {
                    px(g, ring[i], ring[i + 1], PlateInk.GRIME);
                }
            }
        });
        sprite(g, Sprite.SCREW, 6, 6);
        sprite(g, Sprite.SCREW, pw - 11, 6);
        sprite(g, Sprite.SCREW, 6, ph - 11);
        sprite(g, Sprite.SCREW, pw - 11, ph - 11);
    }

    public void sprite(GuiGraphics g, Sprite s, int x, int y) {
        g.blit(ATLAS, x, y, s.u, s.v, s.w, s.h, ATLAS_W, ATLAS_H);
    }

    public void sprite(GuiGraphics g, Sprite s, int x, int y, int w) {
        g.blit(ATLAS, x, y, s.u, s.v, Math.min(w, s.w), s.h, ATLAS_W, ATLAS_H);
    }

    public int engrave(GuiGraphics g, String text, int x, int y, int ink) {
        g.drawString(font, text, x + 1, y + 1, PlateInk.ENGRAVE_LIT, false);
        g.drawString(font, text, x, y, ink, false);
        return font.width(text);
    }

    public int engrave(GuiGraphics g, String text, int x, int y) {
        return engrave(g, text, x, y, PlateInk.ENGRAVE);
    }

    public void engraveRight(GuiGraphics g, String text, int right, int y, int ink) {
        engrave(g, text, right - font.width(text), y, ink);
    }

    public void stampRight(GuiGraphics g, String text, int right, int y) {
        final int w = font.width(text);
        final int x = right - w;
        cut(g, x - 3, y - 2, w + 5, 11, PlateInk.STAMP_FIELD);
        g.drawString(font, text, x + 1, y + 1, PlateInk.STAMP_LIT, false);
        g.drawString(font, text, x, y, PlateInk.STAMP_INK, false);
    }

    public void rule(GuiGraphics g, int x1, int y1, int x2, int y2) {
        if (y1 == y2) {
            g.fill(Math.min(x1, x2), y1 + 1, Math.max(x1, x2) + 1, y1 + 2, PlateInk.ENGRAVE_LIT);
            g.fill(Math.min(x1, x2), y1, Math.max(x1, x2) + 1, y1 + 1, PlateInk.ENGRAVE);
        } else {
            g.fill(x1 + 1, Math.min(y1, y2), x1 + 2, Math.max(y1, y2) + 1, PlateInk.ENGRAVE_LIT);
            g.fill(x1, Math.min(y1, y2), x1 + 1, Math.max(y1, y2) + 1, PlateInk.ENGRAVE);
        }
    }

    public void arrow(GuiGraphics g, int x, int y) {
        g.fill(x, y + 2, x + 10, y + 3, PlateInk.ENGRAVE);
        g.fill(x, y + 3, x + 10, y + 4, PlateInk.ENGRAVE_LIT);
        g.fill(x + 10, y, x + 11, y + 5, PlateInk.ENGRAVE);
        g.fill(x + 11, y + 1, x + 12, y + 4, PlateInk.ENGRAVE);
        px(g, x + 12, y + 2, PlateInk.ENGRAVE);
        px(g, x + 11, y + 4, PlateInk.ENGRAVE_LIT);
        px(g, x + 12, y + 3, PlateInk.ENGRAVE_LIT);
        px(g, x + 13, y + 2, PlateInk.ENGRAVE_LIT);
    }

    public void socket(GuiGraphics g, int x, int y) {
        g.drawManaged(() -> {
            for (int i = 0; i < 16; i += 2) {
                px(g, x + i, y, PlateInk.ENGRAVE_SOFT);
                px(g, x + i, y + 15, PlateInk.ENGRAVE_SOFT);
                px(g, x, y + i, PlateInk.ENGRAVE_SOFT);
                px(g, x + 15, y + i, PlateInk.ENGRAVE_SOFT);
            }
        });
    }

    public void cut(GuiGraphics g, int x, int y, int w, int h, int floor) {
        g.fill(x, y, x + w, y + 1, PlateInk.CUT_DARK);
        g.fill(x, y, x + 1, y + h, PlateInk.CUT_DARK);
        g.fill(x, y + h - 1, x + w, y + h, PlateInk.CUT_LIT);
        g.fill(x + w - 1, y + 1, x + w, y + h, PlateInk.CUT_LIT);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, floor);
    }

    public void window(GuiGraphics g, PlateGeometry.Rect r, boolean glare) {
        final int x = r.x();
        final int y = r.y();
        final int w = r.w();
        final int h = r.h();
        px(g, x - 1, y - 1, PlateInk.GRIME_CORNER);
        px(g, x + w, y + h, PlateInk.GRIME_CORNER);
        g.fill(x, y, x + w, y + 1, PlateInk.CUT_DARK);
        g.fill(x, y, x + 1, y + h, PlateInk.CUT_DARK);
        g.fill(x, y + h - 1, x + w, y + h, PlateInk.CUT_LIT);
        g.fill(x + w - 1, y + 1, x + w, y + h, PlateInk.CUT_LIT);
        px(g, x, y + h - 1, PlateInk.CUT_MID);
        px(g, x + w - 1, y, PlateInk.CUT_MID);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, PlateInk.BEZEL);
        final Sprite t = Sprite.GLASS;
        for (int yy = 0; yy < h - 4; yy += t.h) {
            for (int xx = 0; xx < w - 4; xx += t.w) {
                g.blit(ATLAS, x + 2 + xx, y + 2 + yy, t.u, t.v, Math.min(t.w, w - 4 - xx),
                        Math.min(t.h, h - 4 - yy), ATLAS_W, ATLAS_H);
            }
        }
        if (glare) {
            g.drawManaged(() -> {
                for (int i = 0; i < 9; i++) {
                    px(g, x + 4 + i, y + 2 + i / 2, PlateInk.GLARE);
                }
                for (int i = 0; i < 5; i++) {
                    px(g, x + 4 + i, y + 5 + i / 2, PlateInk.GLARE);
                }
            });
        }
    }

    public void label(GuiGraphics g, String text, int x, int y, int colour) {
        g.fill(x - 2, y - 1, x + GlassFont.width(text) + 2, y + 8, PlateInk.GLASS);
        GlassFont.draw(g, text, x, y, colour, 1);
    }

    public Font font() {
        return font;
    }
}
