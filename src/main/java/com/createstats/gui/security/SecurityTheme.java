package com.createstats.gui.security;

import com.createstats.statsmk3.ui.PanelSurface;
import com.createstats.statsmk3.ui.Rect;
import com.createstats.statsmk3.ui.Text;
import com.createstats.statsmk3.ui.Tokens;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class SecurityTheme {
    private SecurityTheme() {
    }

    public static final int CANVAS = 0xFFF6FAF7;
    public static final int CARD = 0xFFFFFFFF;
    public static final int CARD_HOT = 0xFFF0F6F2;
    public static final int LINE = 0xFFE2EAE5;
    public static final int TEXT = 0xFF0F1A14;
    public static final int TEXT_DIM = 0xFF5B6B62;

    public static final int TEXT_FAINT = 0xFF67766D;
    public static final int OFF = 0xFFC3CEC7;

    public static final float MICRO = 2.0f / 3.0f;

    public static final int GREEN = 0xFF18A957;
    public static final int GREEN_DEEP = 0xFF0E7A3E;
    public static final int GREEN_WASH = 0xFFE7F6EC;
    public static final int GREEN_EDGE = 0xFFBFE4CD;

    public static final int AMBER = 0xFFE0A03A;
    public static final int AMBER_INK = 0xFF9A6B1E;
    public static final int AMBER_WASH = 0xFFFDF4EC;
    public static final int AMBER_EDGE = 0xFFF0DCC4;

    public static final int RED = 0xFFD0553F;
    public static final int RED_WASH = 0xFFFBEAE6;
    public static final int RED_EDGE = 0xFFF0C6BC;

    public static final int SEAL = 0xFF8E2F26;
    public static final int SEAL_DEEP = 0xFF6E241D;
    public static final int SEAL_WASH = 0xFFF6E5E2;
    public static final int SEAL_EDGE = 0xFFDCB3AB;

    public static int rowChip(int accent) {
        return (accent & 0x00FFFFFF) | 0x22000000;
    }

    public static final int STEEL = 0xFF3A6EA0;

    public static int outlineColor(com.createstats.network.SecuritySyncPacket.Row row) {
        final com.createstats.power.SecurityDevice.DeviceState state = row.stateOf();
        if (row.isSensor()) {
            return switch (state) {
                case UNPOWERED, BREACHED, OFFLINE, ACTIVE -> RED;
                case DAMAGED -> AMBER;
                default -> row.isHazard() ? AMBER : row.armed() ? GREEN : OFF;
            };
        }
        if (row.isRepeller() && state == com.createstats.power.SecurityDevice.DeviceState.UNPOWERED) {
            return RED;
        }
        if (row.isCoil() && (state == com.createstats.power.SecurityDevice.DeviceState.UNPOWERED
                || state == com.createstats.power.SecurityDevice.DeviceState.DAMAGED)) {
            return RED;
        }
        return switch (state) {
            case ACTIVE -> GREEN;
            case DAMAGED -> AMBER;
            case BREACHED, OFFLINE -> RED;
            case UNPOWERED -> OFF;
            default -> STEEL;
        };
    }
    public static final int STEEL_DEEP = 0xFF2C5580;
    public static final int STEEL_WASH = 0xFFE8EFF6;
    public static final int STEEL_EDGE = 0xFFA9C3DA;

    private static final int[] LADDER_BG = {0, 0xFFF2F6FA, 0xFFEBF1F8, 0xFFE8EFF6, 0xFFDCE8F3, 0xFF3A6EA0};
    private static final int[] LADDER_INK = {0, 0xFF6C8FAE, 0xFF5580AB, 0xFF3A6EA0, 0xFF2C5580, 0xFFFFFFFF};
    private static final int[] LADDER_EDGE = {0, 0xFFDCE7F1, 0xFFCFDFED, 0xFFBFD4E7, 0xFFA9C3DA, 0xFF2C5580};

    public static int ladderBg(int level) {
        return LADDER_BG[clamp(level)];
    }

    public static int ladderInk(int level) {
        return LADDER_INK[clamp(level)];
    }

    public static int ladderEdge(int level) {
        return LADDER_EDGE[clamp(level)];
    }

    private static int clamp(int level) {
        return level < 1 ? 1 : level > 5 ? 5 : level;
    }

    public static final float ROW_H = 26.0f;
    public static final float HEADER_H = 16.0f;
    public static final float CHIP_H = 15.0f;
    public static final float BTN_H = 18.0f;
    public static final float OPT_H = 20.0f;

    public static void card(PanelSurface s, Rect r, boolean hot, boolean selected) {
        s.card(r, Tokens.R_CARD, hot ? CARD_HOT : CARD, selected ? STEEL_EDGE : LINE);
    }

    public static Rect chip(PanelSurface s, float x, float centerY, String label, int bg, int ink,
                            int edge) {
        final float w = s.textWidth(label, SecurityTheme.MICRO) + Tokens.S3;
        final Rect r = new Rect(x, centerY - CHIP_H * 0.5f, w, CHIP_H);
        s.card(r, Tokens.R_CHIP, bg, edge);
        s.textCenterV(label, r.centerX(), r.centerY(), ink, SecurityTheme.MICRO);
        return r;
    }

    public static Rect levelChip(PanelSurface s, float x, float centerY, int level, String name) {
        return chip(s, x, centerY, level + " " + name, ladderBg(level), ladderInk(level),
                ladderEdge(level));
    }

    public static Rect chipRight(PanelSurface s, float rightX, float centerY, String label, int bg,
                                 int ink, int edge) {
        final float w = s.textWidth(label, SecurityTheme.MICRO) + Tokens.S3;
        return chip(s, rightX - w, centerY, label, bg, ink, edge);
    }

    public static void button(PanelSurface s, Rect r, String label, boolean primary, boolean hot,
                              boolean enabled) {
        final int bg = !enabled ? CANVAS : primary ? (hot ? STEEL_DEEP : STEEL) : hot ? CARD_HOT : CARD;
        final int edge = !enabled ? LINE : primary ? STEEL_DEEP : OFF;
        final int ink = !enabled ? TEXT_FAINT : primary ? 0xFFFFFFFF : TEXT_DIM;
        s.card(r, Tokens.R_CHIP, bg, edge);
        s.textCenterV(label, r.centerX(), r.centerY(), ink, SecurityTheme.MICRO);
    }

    public static void option(PanelSurface s, Rect r, String label, boolean on, boolean hot) {
        s.card(r, Tokens.R_CHIP, on ? STEEL_WASH : hot ? CARD_HOT : CANVAS,
                on ? STEEL_EDGE : LINE);
        s.textCenterV(label, r.centerX(), r.centerY(), on ? STEEL_DEEP : TEXT_DIM, SecurityTheme.MICRO);
    }

    public static void head(PanelSurface s, float x, float y, String label) {
        s.text(Text.track(label), x, y, TEXT_FAINT, SecurityTheme.MICRO);
    }

    public static void bar(PanelSurface s, Rect r, float fraction, int fill) {
        s.roundedRect(r, r.h * 0.5f, LINE);
        final float w = Math.max(0.0f, Math.min(1.0f, fraction)) * r.w;
        if (w > 0.5f) {
            s.roundedRect(new Rect(r.x, r.y, Math.max(r.h, w), r.h), r.h * 0.5f, fill);
        }
    }

    public static void hatch(PanelSurface s, Rect r, int ground, int stripe) {
        s.roundedRect(r, Tokens.R_CHIP, ground);

        s.clip(r);
        final float pitch = 6.0f;
        for (float o = -r.h; o < r.w + r.h; o += pitch) {
            s.line(r.x + o, r.bottom(), r.x + o + r.h, r.y, 1.6f, stripe);
        }
        s.unclip();
    }

    public static void toggle(PanelSurface s, Rect r, boolean on, boolean hot) {
        s.roundedRect(r, r.h * 0.5f, on ? GREEN : OFF);
        if (hot) {
            s.roundedRect(r, r.h * 0.5f, on ? 0x22FFFFFF : 0x22000000);
        }
        final float knob = r.h - 2.0f;
        final float kx = on ? r.right() - knob - 1.0f : r.x + 1.0f;
        s.roundedRect(new Rect(kx, r.y + 1.0f, knob, knob), knob * 0.5f, CARD);
    }

    public static void avatar(PanelSurface s, Rect r, String name, boolean owner) {
        s.roundedRect(r, Tokens.R_CHIP, owner ? STEEL : CARD_HOT);
        final String initial = name == null || name.isEmpty()
                ? "?" : name.substring(0, 1).toUpperCase(java.util.Locale.ROOT);
        s.textCenterV(initial, r.centerX(), r.centerY(), owner ? 0xFFFFFFFF : TEXT_DIM, Text.BODY);
    }
}
