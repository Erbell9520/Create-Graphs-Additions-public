package com.createstats.renderer.inventory;

// mid ink is fills only, as text it was 3:1
public enum FaceInks {
    GREEN(0xFFC4FFD2, 0xFF72EC96, 0xFF8DB59A, 0xFF34A05D, 0xFF1C5433, 0xFF12301F, 0xFF173F29),
    AMBER(0xFFFFE4A6, 0xFFF6B64C, 0xFFC9A877, 0xFFAC7726, 0xFF533A10, 0xFF2E210C, 0xFF3D2B0D),
    RED(0xFFFFC7BD, 0xFFFF6752, 0xFFD99A90, 0xFFB3341F, 0xFF58190F, 0xFF2F100B, 0xFF42150E),
    IDLE(0xFFE8F3EC, 0xFFB2C5B9, 0xFFA3B3A9, 0xFF667B6F, 0xFF35433B, 0xFF1C2620, 0xFF1B251F);

    public final int bright;
    public final int main;
    public final int label;
    public final int mid;
    public final int dim;
    public final int faint;
    public final int halo;

    FaceInks(int bright, int main, int label, int mid, int dim, int faint, int halo) {
        this.bright = bright;
        this.main = main;
        this.label = label;
        this.mid = mid;
        this.dim = dim;
        this.faint = faint;
        this.halo = halo;
    }

    public static final int GLASS = 0xFF0B1410;
    public static final int GLASS_DOT = 0xFF101D16;
    public static final int GLASS_DEEP = 0xFF070D0A;

    public static final int GLARE = 0x1615231B;
}
