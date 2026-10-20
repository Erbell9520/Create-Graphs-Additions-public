package com.createstats.blockentity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

public class StatusTowerLightSettings {
    private static final float MIN_THRESHOLD = 0.0f;
    private static final float MAX_THRESHOLD = 1_000_000.0f;

    private SourceMode sourceMode;
    private Metric metric;
    private LogicMode logicMode;
    private ThresholdDirection thresholdDirection;
    private float warningThreshold;
    private float criticalThreshold;
    private boolean binaryInvert;
    private Lens lens;
    private AlertStyle alertStyle;
    private Sounder sounder;

    public StatusTowerLightSettings() {
        this.sourceMode = SourceMode.LOCAL;
        this.metric = Metric.AUTO;
        this.logicMode = LogicMode.BINARY;
        this.thresholdDirection = ThresholdDirection.HIGHER_IS_WORSE;
        this.warningThreshold = 75.0f;
        this.criticalThreshold = 90.0f;
        this.binaryInvert = false;
        this.lens = Lens.AUTO;
        this.alertStyle = AlertStyle.CLASSIC;
        this.sounder = Sounder.OFF;
    }

    public Lens getLens() {
        return this.lens;
    }

    public void setLens(Lens lens) {
        this.lens = lens;
    }

    public AlertStyle getAlertStyle() {
        return this.alertStyle;
    }

    public void setAlertStyle(AlertStyle alertStyle) {
        this.alertStyle = alertStyle;
    }

    public Sounder getSounder() {
        return this.sounder;
    }

    public void setSounder(Sounder sounder) {
        this.sounder = sounder;
    }

    public SourceMode getSourceMode() {
        return this.sourceMode;
    }

    public void setSourceMode(SourceMode sourceMode) {
        this.sourceMode = sourceMode;
    }

    public void cycleSourceMode() {
        this.sourceMode = this.sourceMode.next();
    }

    public Metric getMetric() {
        return this.metric;
    }

    public void setMetric(Metric metric) {
        this.metric = metric;
        this.normalizeLogicMode();
    }

    public void cycleMetric(SourceKind sourceKind) {
        final Metric[] options = this.getMetricOptions(sourceKind);
        int index = 0;
        for (int i = 0; i < options.length; i++) {
            if (options[i] == this.metric) {
                index = i;
                break;
            }
        }
        this.metric = options[(index + 1) % options.length];
        this.normalizeLogicMode();
    }

    public void ensureMetricSupported(SourceKind sourceKind) {
        if (!this.isMetricSupported(this.metric, sourceKind)) {
            this.metric = Metric.AUTO;
        }
        this.normalizeLogicMode();
    }

    public LogicMode getLogicMode() {
        return this.logicMode;
    }

    public boolean isThresholdMode() {
        return this.logicMode == LogicMode.THRESHOLD;
    }

    public void setLogicMode(LogicMode logicMode) {
        this.logicMode = logicMode;
    }

    public void cycleLogicMode() {
        this.logicMode = this.logicMode.next();
    }

    public boolean usesAutoLogicSelection() {
        return this.metric == Metric.AUTO;
    }

    public boolean usesBinaryLogic() {
        return this.metric.isBinaryMetric() || (this.metric == Metric.AUTO && this.logicMode == LogicMode.BINARY);
    }

    public boolean usesThresholdLogic() {
        return !this.usesBinaryLogic();
    }

    public ThresholdDirection getThresholdDirection() {
        return this.thresholdDirection;
    }

    public void setThresholdDirection(ThresholdDirection thresholdDirection) {
        this.thresholdDirection = thresholdDirection;
        this.normalizeThresholdOrder();
    }

    public void cycleThresholdDirection() {
        this.thresholdDirection = this.thresholdDirection.next();
        this.normalizeThresholdOrder();
    }

    public float getWarningThreshold() {
        return this.warningThreshold;
    }

    public void setWarningThreshold(float warningThreshold) {
        this.warningThreshold = clampThreshold(warningThreshold);
        this.normalizeThresholdOrder();
    }

    public float getCriticalThreshold() {
        return this.criticalThreshold;
    }

    public void setCriticalThreshold(float criticalThreshold) {
        this.criticalThreshold = clampThreshold(criticalThreshold);
        this.normalizeThresholdOrder();
    }

    public boolean isBinaryInvert() {
        return this.binaryInvert;
    }

    public void setBinaryInvert(boolean binaryInvert) {
        this.binaryInvert = binaryInvert;
    }

    public StatusTowerLightBE.SignalState thresholdSeverity(float value) {
        if (this.thresholdDirection == ThresholdDirection.HIGHER_IS_WORSE) {
            if (value >= this.criticalThreshold) {
                return StatusTowerLightBE.SignalState.CRITICAL;
            }
            return value >= this.warningThreshold
                    ? StatusTowerLightBE.SignalState.WARNING : StatusTowerLightBE.SignalState.NORMAL;
        }
        if (value <= this.criticalThreshold) {
            return StatusTowerLightBE.SignalState.CRITICAL;
        }
        return value <= this.warningThreshold
                ? StatusTowerLightBE.SignalState.WARNING : StatusTowerLightBE.SignalState.NORMAL;
    }

    public void setThresholds(float warning, float critical) {
        this.warningThreshold = clampThreshold(warning);
        this.criticalThreshold = clampThreshold(critical);
        this.normalizeThresholdOrder();
    }

    private static float clampThreshold(float value) {
        return Math.max(MIN_THRESHOLD, Math.min(MAX_THRESHOLD, value));
    }

    private void normalizeLogicMode() {
        if (this.metric == Metric.AUTO) {
            return;
        }
        this.logicMode = this.metric.isBinaryMetric() ? LogicMode.BINARY : LogicMode.THRESHOLD;
    }

    private void normalizeThresholdOrder() {
        if (this.thresholdDirection == ThresholdDirection.HIGHER_IS_WORSE && this.warningThreshold > this.criticalThreshold) {
            final float swap = this.warningThreshold;
            this.warningThreshold = this.criticalThreshold;
            this.criticalThreshold = swap;
        }
        if (this.thresholdDirection == ThresholdDirection.LOWER_IS_WORSE && this.warningThreshold < this.criticalThreshold) {
            final float swap = this.warningThreshold;
            this.warningThreshold = this.criticalThreshold;
            this.criticalThreshold = swap;
        }
    }

    public CompoundTag save() {
        final CompoundTag tag = new CompoundTag();
        tag.putString("SourceMode", this.sourceMode.name());
        tag.putString("Metric", this.metric.name());
        tag.putString("LogicMode", this.logicMode.name());
        tag.putString("ThresholdDirection", this.thresholdDirection.name());
        tag.putFloat("WarningThreshold", this.warningThreshold);
        tag.putFloat("CriticalThreshold", this.criticalThreshold);
        tag.putBoolean("BinaryInvert", this.binaryInvert);
        tag.putString("Lens", this.lens.name());
        tag.putString("AlertStyle", this.alertStyle.name());
        tag.putString("Sounder", this.sounder.name());
        return tag;
    }

    public static StatusTowerLightSettings load(CompoundTag tag) {
        final StatusTowerLightSettings settings = new StatusTowerLightSettings();
        try {
            settings.sourceMode = SourceMode.valueOf(tag.getString("SourceMode"));
        } catch (Exception ignored) {
        }
        try {
            settings.metric = Metric.valueOf(tag.getString("Metric"));
        } catch (Exception ignored) {
        }
        try {
            settings.logicMode = LogicMode.valueOf(tag.getString("LogicMode"));
        } catch (Exception ignored) {
        }
        try {
            settings.thresholdDirection = ThresholdDirection.valueOf(tag.getString("ThresholdDirection"));
        } catch (Exception ignored) {
        }
        if (tag.contains("WarningThreshold")) {
            settings.warningThreshold = clampThreshold(tag.getFloat("WarningThreshold"));
        }
        if (tag.contains("CriticalThreshold")) {
            settings.criticalThreshold = clampThreshold(tag.getFloat("CriticalThreshold"));
        }
        if (tag.contains("BinaryInvert")) {
            settings.binaryInvert = tag.getBoolean("BinaryInvert");
        }
        try {
            settings.lens = Lens.valueOf(tag.getString("Lens"));
        } catch (Exception ignored) {
        }
        try {
            settings.alertStyle = AlertStyle.valueOf(tag.getString("AlertStyle"));
        } catch (Exception ignored) {
        }
        try {
            settings.sounder = Sounder.valueOf(tag.getString("Sounder"));
        } catch (Exception ignored) {
        }
        settings.normalizeLogicMode();
        settings.normalizeThresholdOrder();
        return settings;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeNbt(this.save());
    }

    public static StatusTowerLightSettings decode(FriendlyByteBuf buf) {
        final CompoundTag tag = buf.readNbt();
        return tag != null ? load(tag) : new StatusTowerLightSettings();
    }

    public enum SourceMode {
        LOCAL("Local"),
        LINKED("Linked");

        private final String displayName;

        SourceMode(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return this.displayName;
        }

        public SourceMode next() {
            final SourceMode[] values = values();
            return values[(this.ordinal() + 1) % values.length];
        }
    }

    public enum LogicMode {
        THRESHOLD("Threshold"),
        BINARY("Binary");

        private final String displayName;

        LogicMode(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return this.displayName;
        }

        public LogicMode next() {
            final LogicMode[] values = values();
            return values[(this.ordinal() + 1) % values.length];
        }
    }

    public enum ThresholdDirection {
        HIGHER_IS_WORSE("Higher = worse"),
        LOWER_IS_WORSE("Lower = worse");

        private final String displayName;

        ThresholdDirection(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return this.displayName;
        }

        public ThresholdDirection next() {
            final ThresholdDirection[] values = values();
            return values[(this.ordinal() + 1) % values.length];
        }
    }

    public enum Metric {
        AUTO("Auto", false),
        RUNNING("Running", true),
        OVERSTRESSED("Overstressed", true),
        RPM("RPM", false),
        STRESS_PERCENT("Stress %", false),
        SPARE_CAPACITY("Spare Capacity", false),
        FILL_LEVEL("Fill %", false),
        ITEM_COUNT("Stock / Count", false),
        ITEMS_PER_MIN("Items/min", false),
        FLUID_MB("Fluid Amount", false),
        ENERGY_FILL("Energy %", false),
        ENERGY_STORED("Stored FE", false),
        ENERGY_RATE("FE/t", false),
        HEAT_LEVEL("Heat", false),
        BOOLEAN("Signal", true);

        private final String displayName;
        private final boolean binaryMetric;

        Metric(String displayName, boolean binaryMetric) {
            this.displayName = displayName;
            this.binaryMetric = binaryMetric;
        }

        public String getDisplayName() {
            return this.displayName;
        }

        public boolean isBinaryMetric() {
            return this.binaryMetric;
        }
    }

    public boolean isMetricSupported(Metric metric, SourceKind sourceKind) {
        for (Metric option : this.getMetricOptions(sourceKind)) {
            if (option == metric) {
                return true;
            }
        }
        return false;
    }

    public Metric[] getMetricOptions(SourceKind sourceKind) {
        return switch (sourceKind) {
            case KINETIC_STRESS -> new Metric[] { Metric.AUTO, Metric.STRESS_PERCENT, Metric.SPARE_CAPACITY, Metric.RPM, Metric.RUNNING, Metric.OVERSTRESSED };
            case KINETIC_SPEED -> new Metric[] { Metric.AUTO, Metric.RPM, Metric.RUNNING, Metric.OVERSTRESSED };
            case ITEM_STORAGE -> new Metric[] { Metric.AUTO, Metric.FILL_LEVEL, Metric.ITEM_COUNT, Metric.BOOLEAN };
            case FLUID_STORAGE -> new Metric[] { Metric.AUTO, Metric.FILL_LEVEL, Metric.FLUID_MB, Metric.HEAT_LEVEL, Metric.BOOLEAN };
            case ENERGY_STORAGE -> new Metric[] { Metric.AUTO, Metric.ENERGY_FILL, Metric.ENERGY_STORED, Metric.ENERGY_RATE, Metric.BOOLEAN };
            case PRODUCTION -> new Metric[] { Metric.AUTO, Metric.ITEMS_PER_MIN, Metric.ITEM_COUNT };
            case SIGNAL -> new Metric[] { Metric.AUTO, Metric.BOOLEAN };
            case NO_SOURCE, AMBIGUOUS, UNKNOWN -> new Metric[] { Metric.AUTO };
        };
    }

    public enum Lens {
        AUTO("Auto (traffic)", 0),
        RED("Red", 0xF2402C),
        AMBER("Amber", 0xFFA61E),
        GREEN("Green", 0x4ADE5A),
        BLUE("Blue", 0x4A8CF0),
        WHITE("White", 0xF2F4F8),
        CYAN("Cyan", 0x3FD8D0);

        public static final int AUTO_NORMAL = 0x4ADE5A;
        public static final int AUTO_WARNING = 0xFFA61E;
        public static final int AUTO_CRITICAL = 0xF2402C;

        private final String displayName;
        private final int rgb;

        Lens(String displayName, int rgb) {
            this.displayName = displayName;
            this.rgb = rgb;
        }

        public String getDisplayName() {
            return this.displayName;
        }

        public int rgb() {
            return this.rgb;
        }

        public boolean isAuto() {
            return this == AUTO;
        }

        public Lens next() {
            final Lens[] values = values();
            return values[(this.ordinal() + 1) % values.length];
        }
    }

    public enum AlertStyle {
        CLASSIC("Classic"),

        CALM("Calm"),

        URGENT("Urgent"),

        BEACON("Beacon"),

        SOLID("Solid");

        private final String displayName;

        AlertStyle(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return this.displayName;
        }

        public SignalPattern patternFor(StatusTowerLightBE.SignalState state) {
            if (this == SOLID) {
                return state == StatusTowerLightBE.SignalState.OFF
                        ? SignalPattern.OFF : SignalPattern.STEADY;
            }
            return switch (state) {
                case OFF -> SignalPattern.OFF;
                case NORMAL -> SignalPattern.STEADY;
                // no data always stutters. must not look like good news

                case NO_DATA -> SignalPattern.FAULT;
                case WARNING -> switch (this) {
                    case CLASSIC -> SignalPattern.BLINK;
                    case CALM, BEACON -> SignalPattern.PULSE;
                    case URGENT -> SignalPattern.FLASH;
                    case SOLID -> SignalPattern.STEADY;
                };
                case CRITICAL -> switch (this) {
                    case CLASSIC -> SignalPattern.FLASH;
                    case CALM -> SignalPattern.BLINK;
                    case URGENT -> SignalPattern.STROBE;
                    case BEACON -> SignalPattern.ROTATE;
                    case SOLID -> SignalPattern.STEADY;
                };
            };
        }
    }

    public enum Sounder {
        OFF("Off"),
        CRITICAL("On critical"),
        WARNING("On warning");

        private final String displayName;

        Sounder(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return this.displayName;
        }

        public boolean soundsFor(StatusTowerLightBE.SignalState state) {
            return switch (this) {
                case OFF -> false;
                case CRITICAL -> state == StatusTowerLightBE.SignalState.CRITICAL;
                case WARNING -> state == StatusTowerLightBE.SignalState.CRITICAL
                        || state == StatusTowerLightBE.SignalState.WARNING;
            };
        }

        public Sounder next() {
            final Sounder[] values = values();
            return values[(this.ordinal() + 1) % values.length];
        }
    }

    public enum SourceKind {
        NO_SOURCE("No source"),
        AMBIGUOUS("Ambiguous"),
        KINETIC_STRESS("Kinetic (stress)"),
        KINETIC_SPEED("Kinetic (speed)"),
        ITEM_STORAGE("Item storage"),
        FLUID_STORAGE("Fluid storage"),
        ENERGY_STORAGE("Energy storage"),
        PRODUCTION("Throughput"),
        SIGNAL("Signal"),
        UNKNOWN("Unknown");

        private final String displayName;

        SourceKind(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return this.displayName;
        }
    }
}
