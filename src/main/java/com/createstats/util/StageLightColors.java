package com.createstats.util;

import net.minecraft.world.item.DyeColor;

public final class StageLightColors {
    private StageLightColors() {
    }

    public static int rgb(DyeColor dye) {
        return switch (dye) {
            case WHITE -> 0xF9FFFE;
            case ORANGE -> 0xF9801D;
            case MAGENTA -> 0xC74EBD;
            case LIGHT_BLUE -> 0x3AB3DA;
            case YELLOW -> 0xFED83D;
            case LIME -> 0x80C71F;
            case PINK -> 0xF38BAA;
            case GRAY -> 0x474F52;
            case LIGHT_GRAY -> 0x9D9D97;
            case CYAN -> 0x169C9C;
            case PURPLE -> 0x8932B8;
            case BLUE -> 0x3C44AA;
            case BROWN -> 0x835432;
            case GREEN -> 0x5E7C16;
            case RED -> 0xB02E26;
            case BLACK -> 0x1D1D21;
        };
    }

    public static DyeColor fromRgb(int rgb) {
        final int masked = rgb & 0xFFFFFF;
        for (DyeColor dye : DyeColor.values()) {
            if (rgb(dye) == masked) {
                return dye;
            }
        }
        return null;
    }
}
