package com.createstats.chunkloader.look;

public enum CrownMaterial {
    STEEL_LO("spark_crown/steel_lo"),
    BRASS("spark_crown/brass"),
    BRASS_FRAME("spark_crown/brass_frame"),
    BRASS_LO("spark_crown/brass_lo"),
    RIM("spark_crown/rim"),

    ROSE_0("spark_crown/rose_0"),
    ROSE_1("spark_crown/rose_1"),
    ROSE_2("spark_crown/rose_2"),
    ROSE_3("spark_crown/rose_3"),
    ROSE_4("spark_crown/rose_4"),
    WARN_1("spark_crown/warn_1"),
    WARN_2("spark_crown/warn_2"),
    WARN_3("spark_crown/warn_3"),
    WARN_4("spark_crown/warn_4"),
    SEG_OFF("spark_crown/seg_off"),
    SEG_GREEN("spark_crown/seg_green"),
    SEG_WARN("spark_crown/seg_warn"),
    PILOT_OFF("spark_crown/pilot_off"),
    PILOT_GREEN("spark_crown/pilot_green"),
    PILOT_WARN("spark_crown/pilot_warn"),
    PILOT_RED("spark_crown/pilot_red"),

    DIAL("spark_crown/dial"),
    NEEDLE("spark_crown/needle"),
    NEEDLE_WARN("spark_crown/needle_warn"),
    INSULATOR("advanced_power_connector_insulator");

    public final String path;

    CrownMaterial(String path) {
        this.path = path;
    }

    private static final CrownMaterial[] ROSE = {ROSE_0, ROSE_1, ROSE_2, ROSE_3, ROSE_4};
    private static final CrownMaterial[] WARN = {ROSE_0, WARN_1, WARN_2, WARN_3, WARN_4};

    public static CrownMaterial electrode(int ink, double glow) {
        if (ink == CrownPose.INK_OFF) {
            return ROSE_0;
        }
        final int lv = Math.max(0, Math.min(4, (int) Math.round(glow * 4)));
        return ink == CrownPose.INK_WARN ? WARN[lv] : ROSE[lv];
    }

    public boolean lit() {
        return this != ROSE_0 && name().startsWith("ROSE") || name().startsWith("WARN");
    }
}
