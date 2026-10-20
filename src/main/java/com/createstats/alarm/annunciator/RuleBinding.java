package com.createstats.alarm.annunciator;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;

public record RuleBinding(Kind kind, @Nullable BlockPos target, String key) {
    public static final RuleBinding UNBOUND = new RuleBinding(Kind.UNBOUND, null, "");

    public enum Kind {
        UNBOUND("unbound"),

        PANEL_CHANNEL("panel"),

        GRID("grid"),

        MACHINE("machine");

        private final String serializedName;

        Kind(String serializedName) {
            this.serializedName = serializedName;
        }

        public String getSerializedName() {
            return this.serializedName;
        }

        public static Kind byName(String name) {
            for (Kind kind : values()) {
                if (kind.serializedName.equalsIgnoreCase(name) || kind.name().equalsIgnoreCase(name)) {
                    return kind;
                }
            }
            return UNBOUND;
        }
    }

    public static RuleBinding panelChannel(BlockPos panel, String channelId) {
        return new RuleBinding(Kind.PANEL_CHANNEL, panel.immutable(), channelId);
    }

    public static RuleBinding grid(GridFact fact) {
        return new RuleBinding(Kind.GRID, null, fact.getSerializedName());
    }

    public static RuleBinding machine(BlockPos machine, String metricKey) {
        return new RuleBinding(Kind.MACHINE, machine.immutable(), metricKey);
    }

    public boolean isBound() {
        return this.kind != Kind.UNBOUND;
    }

    @Nullable
    public GridFact gridFact() {
        return this.kind == Kind.GRID ? GridFact.byName(this.key) : null;
    }

    public String shortLabel() {
        return switch (this.kind) {
            case UNBOUND -> "----";
            case GRID -> {
                final GridFact fact = this.gridFact();
                yield fact == null ? "GRID" : fact.displayName();
            }
            case PANEL_CHANNEL -> this.key.isEmpty() ? "CHAN" : this.key;
            case MACHINE -> this.key.isEmpty() ? "MACH" : this.key;
        };
    }

    public CompoundTag save() {
        final CompoundTag tag = new CompoundTag();
        tag.putString("Kind", this.kind.getSerializedName());
        tag.putString("Key", this.key);
        if (this.target != null) {
            tag.putLong("Target", this.target.asLong());
        }
        return tag;
    }

    public static RuleBinding load(CompoundTag tag) {
        final Kind kind = Kind.byName(tag.getString("Kind"));
        final BlockPos target = tag.contains("Target") ? BlockPos.of(tag.getLong("Target")) : null;
        return new RuleBinding(kind, target, tag.getString("Key"));
    }
}
