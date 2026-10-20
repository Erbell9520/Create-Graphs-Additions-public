package com.createstats.pa;

public enum SpeakerKind {
    SPEAKER("speaker", 64, 4),

    MEGAPHONE("megaphone", 32, 6);

    public static final int HALF_HEIGHT = 8;

    private final String serializedName;
    private final int range;
    private final int idleDraw;

    SpeakerKind(String serializedName, int range, int idleDraw) {
        this.serializedName = serializedName;
        this.range = range;
        this.idleDraw = idleDraw;
    }

    public String getSerializedName() {
        return this.serializedName;
    }

    public int range() {
        return this.range;
    }

    public int halfHeight() {
        return HALF_HEIGHT;
    }

    public int idleDrawFe() {
        return this.idleDraw;
    }

    public int bufferFe() {
        return this.idleDraw * 400;
    }

    public static SpeakerKind byName(String name) {
        for (SpeakerKind value : values()) {
            if (value.serializedName.equals(name)) {
                return value;
            }
        }
        return SPEAKER;
    }
}
