package com.createstats.gui.meter.plate;

public final class PlateInk {
    private PlateInk() {
    }

    public record Ink(int bright, int main, int mid, int dim, int faint, int halo) {
    }

    public static final Ink GREEN = new Ink(0xFFC4FFD2, 0xFF72EC96, 0xFF34A05D, 0xFF1C5433, 0xFF12301F, 0xFF173F29);
    public static final Ink AMBER = new Ink(0xFFFFE4A6, 0xFFF6B64C, 0xFFAC7726, 0xFF533A10, 0xFF2E210C, 0xFF3D2B0D);
    public static final Ink RED = new Ink(0xFFFFC7BD, 0xFFFF6752, 0xFFB3341F, 0xFF58190F, 0xFF2F100B, 0xFF42150E);
    public static final Ink IDLE = new Ink(0xFFE8F3EC, 0xFFB2C5B9, 0xFF667B6F, 0xFF35433B, 0xFF1C2620, 0xFF1B251F);

    public static final int RIM_OUT = 0xFF0B0D0F;
    public static final int RIM_LIT = 0xFF4E565D;
    public static final int RIM_FACE = 0xFF363C42;
    public static final int RIM_DARK = 0xFF202428;

    public static final int PLATE_HI = 0xFFE4E8EB;
    public static final int PLATE_LO = 0xFF7B8288;

    public static final int WEAR = 0x4AFFFFFF;

    public static final int GRIME = 0x2C000000;
    public static final int GRIME_CORNER = 0x2A000000;

    public static final int ENGRAVE = 0xFF2B3035;
    public static final int ENGRAVE_LIT = 0xFFD3D7DA;
    public static final int ENGRAVE_SOFT = 0xFF4F565C;

    public static final int STAMP_FIELD = 0xFFA4AAAF;
    public static final int STAMP_INK = 0xFF5F666C;
    public static final int STAMP_LIT = 0xFFD9DDE0;

    public static final int CUT_DARK = 0xFF5A6167;
    public static final int CUT_LIT = 0xFFE6EAED;
    public static final int CUT_MID = 0xFFA0A7AD;

    public static final int BEZEL = 0xFF05080A;
    public static final int GLASS = 0xFF0B1410;
    public static final int GLASS_DEEP = 0xFF070D0A;
    public static final int GLARE = 0xFF15231B;

    public static final int PEAK_MARK = 0xFF8FD9A8;

    public static final int FAULT_BAND = 0xE6070D0A;

    public static final int LAMP_FLOOR = 0xFF1E1810;

    public static final int BRASS_INK = 0xFF4A3510;

    public static final int RING_TRACK = 0xFF3A3F44;
}
