package com.createstats.pa;

import com.createstats.alarm.annunciator.AlarmSeverity;
import com.createstats.alarm.annunciator.RuleComparator;
import net.minecraft.nbt.CompoundTag;

// three disciplines, one setting
public final class PaSubscription {
    public enum Discipline {
        ON_DEMAND("on_demand", "On Demand"),

        ON_EVENT("on_event", "On Change"),

        PERIODIC("periodic", "Bulletin");

        private final String serializedName;
        private final String displayName;

        Discipline(String serializedName, String displayName) {
            this.serializedName = serializedName;
            this.displayName = displayName;
        }

        public String getSerializedName() {
            return this.serializedName;
        }

        public String displayName() {
            return this.displayName;
        }
    }

    public enum Verbosity {
        TERSE("terse", "Terse"),

        NORMAL("normal", "Normal"),

        FULL("full", "Full");

        private final String serializedName;
        private final String displayName;

        Verbosity(String serializedName, String displayName) {
            this.serializedName = serializedName;
            this.displayName = displayName;
        }

        public String getSerializedName() {
            return this.serializedName;
        }

        public String displayName() {
            return this.displayName;
        }
    }

    private PaBinding binding;
    private Discipline discipline = Discipline.ON_EVENT;
    private Verbosity verbosity = Verbosity.NORMAL;
    private AlarmSeverity severity = AlarmSeverity.NOTICE;
    private RuleComparator comparator = RuleComparator.ABOVE;
    private float threshold = 90.0f;
    private float deadband = 5.0f;
    private boolean enabled = true;
    private boolean followSource = true;

    private boolean tripped;

    private boolean available = true;

    private float followedThreshold;
    private boolean followedAbove = true;
    private boolean hasFollowed;

    public PaSubscription(PaBinding binding) {
        this.binding = binding;
    }

    public PaBinding binding() {
        return this.binding;
    }

    public String key() {
        return this.binding.key();
    }

    public Discipline discipline() {
        return this.discipline;
    }

    public void setDiscipline(Discipline value) {
        this.discipline = value == null ? Discipline.ON_EVENT : value;
    }

    public Verbosity verbosity() {
        return this.verbosity;
    }

    public void setVerbosity(Verbosity value) {
        this.verbosity = value == null ? Verbosity.NORMAL : value;
    }

    public AlarmSeverity severity() {
        return this.severity;
    }

    public void setSeverity(AlarmSeverity value) {
        this.severity = value == null ? AlarmSeverity.NOTICE : value;
    }

    public RuleComparator comparator() {
        return this.comparator;
    }

    public void setComparator(RuleComparator value) {
        this.comparator = value == null ? RuleComparator.ABOVE : value;
    }

    public float threshold() {
        return this.threshold;
    }

    public void setThreshold(float value) {
        this.threshold = value;
    }

    public float deadband() {
        return this.deadband;
    }

    public void setDeadband(float value) {
        this.deadband = Math.max(0.0f, value);
    }

    public boolean enabled() {
        return this.enabled;
    }

    public void setEnabled(boolean value) {
        this.enabled = value;
    }

    public boolean followSource() {
        return this.followSource;
    }

    public void setFollowSource(boolean value) {
        this.followSource = value;
    }

    public void observeSource(float panelThreshold, boolean above) {
        this.followedThreshold = panelThreshold;
        this.followedAbove = above;
        this.hasFollowed = true;
    }

    public void clearObservedSource() {
        this.hasFollowed = false;
    }

    public float effectiveThreshold() {
        return this.followSource && this.hasFollowed ? this.followedThreshold : this.threshold;
    }

    public RuleComparator effectiveComparator() {
        if (!this.followSource || !this.hasFollowed) {
            return this.comparator;
        }
        return this.followedAbove ? RuleComparator.ABOVE : RuleComparator.BELOW;
    }

    public boolean isFollowing() {
        return this.followSource && this.hasFollowed;
    }

    public boolean tripped() {
        return this.tripped;
    }

    public boolean isAvailable() {
        return this.available;
    }

    public boolean updateAvailability(boolean readable) {
        final boolean changed = readable != this.available;
        this.available = readable;
        return changed;
    }

    public boolean updateDeviceTrip(boolean alarming) {
        final boolean changed = alarming != this.tripped;
        this.tripped = alarming;
        return changed;
    }

    public boolean updateTrip(float value) {
        final RuleComparator test = this.effectiveComparator();
        final float limit = this.effectiveThreshold();
        final boolean now = this.tripped
                ? test.holds(value, limit, this.deadband)
                : test.trips(value, limit, this.deadband);
        final boolean changed = now != this.tripped;
        this.tripped = now;
        return changed;
    }

    public CompoundTag save() {
        final CompoundTag tag = new CompoundTag();
        tag.put("B", this.binding.save());
        tag.putString("D", this.discipline.getSerializedName());
        tag.putString("V", this.verbosity.getSerializedName());
        tag.putString("S", this.severity.getSerializedName());
        tag.putString("C", this.comparator.getSerializedName());
        tag.putFloat("T", this.threshold);
        tag.putFloat("H", this.deadband);
        tag.putBoolean("E", this.enabled);
        tag.putBoolean("F", this.followSource);
        tag.putBoolean("Tr", this.tripped);
        return tag;
    }

    public static PaSubscription load(CompoundTag tag) {
        final PaSubscription sub = new PaSubscription(PaBinding.load(tag.getCompound("B")));
        sub.discipline = disciplineByName(tag.getString("D"));
        sub.verbosity = verbosityByName(tag.getString("V"));

        sub.severity = AlarmSeverity.byName(tag.getString("S"));
        sub.comparator = comparatorByName(tag.getString("C"));
        sub.threshold = tag.getFloat("T");
        sub.deadband = Math.max(0.0f, tag.getFloat("H"));

        sub.enabled = !tag.contains("E") || tag.getBoolean("E");

        sub.followSource = tag.contains("F") && tag.getBoolean("F");
        sub.tripped = tag.getBoolean("Tr");
        return sub;
    }

    public static Discipline disciplineByName(String name) {
        for (Discipline value : Discipline.values()) {
            if (value.getSerializedName().equals(name)) {
                return value;
            }
        }
        return Discipline.ON_EVENT;
    }

    public static Verbosity verbosityByName(String name) {
        for (Verbosity value : Verbosity.values()) {
            if (value.getSerializedName().equals(name)) {
                return value;
            }
        }
        return Verbosity.NORMAL;
    }

    private static RuleComparator comparatorByName(String name) {
        for (RuleComparator value : RuleComparator.values()) {
            if (value.getSerializedName().equals(name)) {
                return value;
            }
        }
        return RuleComparator.ABOVE;
    }
}
