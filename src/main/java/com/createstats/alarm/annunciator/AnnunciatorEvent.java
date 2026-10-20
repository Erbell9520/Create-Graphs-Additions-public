package com.createstats.alarm.annunciator;

import net.minecraft.nbt.CompoundTag;

public record AnnunciatorEvent(String label, Kind kind, AlarmSeverity severity, String reading,
                               long tick) {
    public static final int MAX_EVENTS = 12;

    private static final int MAX_LABEL = 24;

    public enum Kind {
        TRIP("trip"),

        ACK("ack"),

        RINGBACK("ringback"),

        CLEAR("clear"),

        LOST("lost"),

        RESTORED("restored");

        private final String serializedName;

        Kind(String serializedName) {
            this.serializedName = serializedName;
        }

        public String getSerializedName() {
            return this.serializedName;
        }

        public boolean isAcquisition() {
            return this == LOST || this == RESTORED;
        }

        public static Kind byName(String name) {
            for (Kind kind : values()) {
                if (kind.serializedName.equalsIgnoreCase(name) || kind.name().equalsIgnoreCase(name)) {
                    return kind;
                }
            }
            return TRIP;
        }
    }

    public AnnunciatorEvent(String label, Kind kind, AlarmSeverity severity, String reading, long tick) {
        this.label = clip(label);
        this.kind = kind == null ? Kind.TRIP : kind;
        this.severity = severity == null ? AlarmSeverity.NORMAL : severity;
        this.reading = reading == null ? "" : reading;
        this.tick = tick;
    }

    private static String clip(String text) {
        if (text == null || text.isEmpty()) {
            return "?";
        }
        return text.length() <= MAX_LABEL ? text : text.substring(0, MAX_LABEL);
    }

    public static Kind kindFor(AnnunciatorState was, AnnunciatorState now) {
        if (was == now) {
            return null;
        }
        return switch (now) {
            case ALARM -> Kind.TRIP;
            case ACKNOWLEDGED -> Kind.ACK;
            case RINGBACK -> Kind.RINGBACK;
            case NORMAL -> was.isOutstanding() ? Kind.CLEAR : null;
        };
    }

    public CompoundTag save() {
        final CompoundTag tag = new CompoundTag();
        tag.putString("L", this.label);
        tag.putString("K", this.kind.getSerializedName());
        tag.putString("S", this.severity.getSerializedName());
        tag.putString("V", this.reading);
        tag.putLong("T", this.tick);
        return tag;
    }

    public static AnnunciatorEvent load(CompoundTag tag) {
        return new AnnunciatorEvent(
                tag.getString("L"),
                Kind.byName(tag.getString("K")),
                AlarmSeverity.byName(tag.getString("S")),
                tag.getString("V"),
                tag.getLong("T"));
    }
}
