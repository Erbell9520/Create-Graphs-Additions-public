package com.createstats.battle.client;

import com.createstats.battle.core.BattleReport;
import com.createstats.battle.core.HeaderStatus;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.jetbrains.annotations.Nullable;

// pilot can't sit in a console so the one second reads go on the helmet
public final class HelmetBattleStrip {
    public static final int WIDTH = 180;
    public static final int HEIGHT = 44;
    public static final int MAX_LAMPS = 8;

    private static final int HUD_DIM = 0xC07FE2C8;
    private static final int HUD_TEXT = 0xFFE8FFF4;
    private static final int RED = 0xFFFF5B66;
    private static final int AMBER = 0xFFFFC965;
    private static final int GREEN = 0xFF78FFC4;

    private HelmetBattleStrip() {
    }

    public static void render(GuiGraphics g, Font font, int x, int y, @Nullable BattleReport r, long clock) {
        int gap = 6;
        int cell = (WIDTH - gap * 2) / 3;
        if (r == null) {
            cell(g, font, x, y, cell, "HULL", "--", HUD_DIM);
            cell(g, font, x + cell + gap, y, cell, "STATUS", "STBY", HUD_DIM);
            cell(g, font, x + (cell + gap) * 2, y, cell, "ARMS", "--", HUD_DIM);
            return;
        }
        int permille = r.integrityPermille();
        String hull = r.hasReference() ? (permille / 10) + "%" : "--";
        int hullColour = !r.hasReference() ? HUD_DIM : permille >= 1000 ? HUD_TEXT : permille >= 900 ? AMBER : RED;
        HeaderStatus.Status s = r.status(clock);
        boolean blinkOff = s.kind().blinks() && (clock / 10) % 2 == 1;
        cell(g, font, x, y, cell, "HULL", hull, hullColour);
        cell(g, font, x + cell + gap, y, cell, "STATUS", shortStatus(s), blinkOff ? HUD_DIM : colour(s.kind().lamp()));
        cell(g, font, x + (cell + gap) * 2, y, cell, "ARMS", r.weaponsFree() ? "FREE" : "SAFE", r.weaponsFree() ? AMBER : HUD_TEXT);
        targetRow(g, font, x, y + 30, r);
    }

    private static void targetRow(GuiGraphics g, Font font, int x, int y, BattleReport r) {
        g.fill(x, y, x + WIDTH, y + 12, 0x22040A10);
        int lamps = Math.min(MAX_LAMPS, r.guns().size());
        int lampsW = lamps * 7;
        int textMax = WIDTH - 8 - lampsW - (lampsW > 0 ? 6 : 0);
        BattleReport.TargetLine t = r.target();
        String text;
        int colour;
        if (!t.present()) {
            text = "TGT --";
            colour = HUD_DIM;
        } else if (t.lostSince() >= 0) {
            text = "TGT " + name(t.name()).toUpperCase(java.util.Locale.ROOT) + " LOST";
            colour = AMBER;
        } else {
            text = "TGT " + targetLabel(t).toUpperCase(java.util.Locale.ROOT) + " "
                    + String.format(java.util.Locale.ROOT, "%03d", Math.floorMod(Math.round(t.bearing()), 360));
            colour = RED;
        }
        while (!text.isEmpty() && font.width(text) > textMax) text = text.substring(0, text.length() - 1);
        g.drawString(font, text, x + 4, y + 2, colour, false);
        int lx = x + WIDTH - 4 - lampsW;
        for (int i = 0; i < lamps; i++) {
            BattleReport.GunLine gun = r.guns().get(i);
            g.fill(lx + i * 7, y + 3, lx + i * 7 + 5, y + 8, 0xC0000000);
            g.fill(lx + i * 7 + 1, y + 4, lx + i * 7 + 4, y + 7, colour(gun.gunState().lamp()));
        }
    }

    public static String targetLabel(BattleReport.TargetLine t) {
        return name(t.name()) + " " + Math.round(t.range()) + "m";
    }

    static String name(String name) {
        if (name == null || name.isEmpty()) return "?";
        return name.charAt(0) == '#' ? net.minecraft.client.resources.language.I18n.get(name.substring(1)) : name;
    }

    public static String statusLine(@Nullable BattleReport r, long clock) {
        if (r == null) return "BATTLE STBY";
        HeaderStatus.Status s = r.status(clock);
        return switch (s.kind()) {
            case NO_POWER -> "BRIDGE NO POWER";
            case FIRE -> "FIRE ABOARD";
            case UNDER_FIRE -> "UNDER FIRE " + s.number() + "S";
            case WEAPONS_FREE -> "WEAPONS FREE";
            case SURVEYING -> "SURVEYING " + s.number() + "%";
            case ALL_CLEAR -> r.placeCount() > 0 ? r.placeCount() + " TO REPAIR" : "ALL CLEAR";
        };
    }

    public static int statusColour(@Nullable BattleReport r, long clock) {
        return r == null ? HUD_DIM : colour(r.status(clock).kind().lamp());
    }

    private static String shortStatus(HeaderStatus.Status s) {
        return switch (s.kind()) {
            case NO_POWER -> "NO PWR";
            case FIRE -> "FIRE";
            case UNDER_FIRE -> "HIT " + s.number() + "S";
            case WEAPONS_FREE -> "FREE";
            case SURVEYING -> "SURVEY";
            case ALL_CLEAR -> "CLEAR";
        };
    }

    private static int colour(HeaderStatus.Lamp lamp) {
        return switch (lamp) {
            case RED -> RED;
            case AMBER -> AMBER;
            case GREEN -> GREEN;
            case GREY -> HUD_DIM;
        };
    }

    private static void cell(GuiGraphics g, Font font, int x, int y, int width, String label, String value, int valueColour) {
        g.fill(x, y, x + width, y + HEIGHT, 0x22040A10);
        g.fill(x, y, x + width, y + 1, 0x343A4C54);
        g.fill(x, y + HEIGHT - 1, x + width, y + HEIGHT, 0x22000000);
        g.drawString(font, label, x + 4, y + 3, HUD_DIM, false);
        String v = value;
        while (!v.isEmpty() && font.width(v) > width - 8) v = v.substring(0, v.length() - 1);
        g.drawString(font, v, x + 4, y + 15, valueColour, false);
    }
}
