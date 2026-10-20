package com.createstats.blockentity;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class MiniDisplaySettings {
    private SourceMode sourceMode;
    private StorageDetailMode storageDetailMode;
    private AccentColor accentColor;
    private boolean lit;
    private boolean showLabels;
    private ItemStack filterA;
    private ItemStack filterB;

    public MiniDisplaySettings() {
        this.sourceMode = SourceMode.LOCAL;
        this.storageDetailMode = StorageDetailMode.TOTAL_ONLY;
        this.accentColor = AccentColor.BRASS;
        this.lit = true;
        this.showLabels = true;
        this.filterA = ItemStack.EMPTY;
        this.filterB = ItemStack.EMPTY;
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

    public StorageDetailMode getStorageDetailMode() {
        return this.storageDetailMode;
    }

    public void setStorageDetailMode(StorageDetailMode storageDetailMode) {
        this.storageDetailMode = storageDetailMode;
    }

    public void cycleStorageDetailMode() {
        this.storageDetailMode = this.storageDetailMode.next();
    }

    public AccentColor getAccentColor() {
        return this.accentColor;
    }

    public void setAccentColor(AccentColor accentColor) {
        this.accentColor = accentColor;
    }

    public void cycleAccentColor() {
        this.accentColor = this.accentColor.next();
    }

    public boolean isLit() {
        return this.lit;
    }

    public void setLit(boolean lit) {
        this.lit = lit;
    }

    public boolean isShowLabels() {
        return this.showLabels;
    }

    public void setShowLabels(boolean showLabels) {
        this.showLabels = showLabels;
    }

    public ItemStack getFilterA() {
        return this.filterA;
    }

    public ItemStack getFilterB() {
        return this.filterB;
    }

    public void setFilter(int index, ItemStack stack) {
        ItemStack filter = ItemStack.EMPTY;
        if (!stack.isEmpty()) {
            filter = stack.copy();
            filter.setCount(1);
        }
        if (index == 0) {
            this.filterA = filter;
        } else {
            this.filterB = filter;
        }
    }

    public void clearFilter(int index) {
        this.setFilter(index, ItemStack.EMPTY);
    }

    public MiniDisplaySettings copy() {
        final MiniDisplaySettings copy = new MiniDisplaySettings();
        copy.sourceMode = this.sourceMode;
        copy.storageDetailMode = this.storageDetailMode;
        copy.accentColor = this.accentColor;
        copy.lit = this.lit;
        copy.showLabels = this.showLabels;
        copy.filterA = this.filterA.copy();
        copy.filterB = this.filterB.copy();
        return copy;
    }

    public List<ItemStack> getTrackedFilterItems() {
        final List<ItemStack> tracked = new ArrayList<>(2);
        if (!this.filterA.isEmpty()) {
            tracked.add(this.filterA.copy());
        }
        if (!this.filterB.isEmpty()) {
            tracked.add(this.filterB.copy());
        }
        return tracked;
    }

    public CompoundTag save(HolderLookup.Provider registries) {
        final CompoundTag tag = new CompoundTag();
        tag.putString("SourceMode", this.sourceMode.name());
        tag.putString("StorageDetailMode", this.storageDetailMode.name());
        tag.putString("AccentColor", this.accentColor.name());
        tag.putBoolean("Lit", this.lit);
        tag.putBoolean("ShowLabels", this.showLabels);
        if (!this.filterA.isEmpty()) {
            tag.put("FilterA", this.filterA.saveOptional(registries));
        }
        if (!this.filterB.isEmpty()) {
            tag.put("FilterB", this.filterB.saveOptional(registries));
        }
        return tag;
    }

    public static MiniDisplaySettings load(CompoundTag tag, HolderLookup.Provider registries) {
        final MiniDisplaySettings settings = new MiniDisplaySettings();
        try {
            settings.sourceMode = SourceMode.valueOf(tag.getString("SourceMode"));
        } catch (Exception ignored) {
        }
        try {
            settings.storageDetailMode = StorageDetailMode.valueOf(tag.getString("StorageDetailMode"));
        } catch (Exception ignored) {
        }
        try {
            settings.accentColor = AccentColor.valueOf(tag.getString("AccentColor"));
        } catch (Exception ignored) {
        }
        if (tag.contains("Lit")) {
            settings.lit = tag.getBoolean("Lit");
        }
        if (tag.contains("ShowLabels")) {
            settings.showLabels = tag.getBoolean("ShowLabels");
        }
        settings.filterA = tag.contains("FilterA") ? ItemStack.parseOptional(registries, tag.getCompound("FilterA")) : ItemStack.EMPTY;
        settings.filterB = tag.contains("FilterB") ? ItemStack.parseOptional(registries, tag.getCompound("FilterB")) : ItemStack.EMPTY;
        return settings;
    }

    public void encode(RegistryFriendlyByteBuf buf) {
        buf.writeNbt(this.save(buf.registryAccess()));
    }

    public static MiniDisplaySettings decode(RegistryFriendlyByteBuf buf) {
        final CompoundTag tag = buf.readNbt();
        return tag != null ? load(tag, buf.registryAccess()) : new MiniDisplaySettings();
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

    public enum StorageDetailMode {
        TOTAL_ONLY("Total only"),
        SELECTED_ITEMS("Selected 2 items");

        private final String displayName;

        StorageDetailMode(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return this.displayName;
        }

        public StorageDetailMode next() {
            final StorageDetailMode[] values = values();
            return values[(this.ordinal() + 1) % values.length];
        }
    }

    public enum AccentColor {
        BRASS("Brass", 0xFFC78B2A),
        VERDANT("Verdant", 0xFF2FB36D),
        SKY("Sky", 0xFF5AA5D6),
        AMBER("Amber", 0xFFE3A43B),
        CRIMSON("Crimson", 0xFFD9594C),
        VIOLET("Violet", 0xFF8A67E8);

        private final String displayName;
        private final int color;

        AccentColor(String displayName, int color) {
            this.displayName = displayName;
            this.color = color;
        }

        public String getDisplayName() {
            return this.displayName;
        }

        public int getColor() {
            return this.color;
        }

        public AccentColor next() {
            final AccentColor[] values = values();
            return values[(this.ordinal() + 1) % values.length];
        }
    }
}
