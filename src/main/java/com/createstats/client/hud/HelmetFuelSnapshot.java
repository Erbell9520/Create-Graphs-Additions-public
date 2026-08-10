package com.createstats.client.hud;

import com.createstats.blockentity.StatsDisplayPanelBE;
import com.createstats.data.MetricType;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Reads fuel-reserve channels from a linked {@link StatsDisplayPanelBE} on the
 * client (panels are vanilla-synced to nearby players), aggregating them into
 * a tidy snapshot for the OTOMOBIL helmet HUD.
 */
@OnlyIn(Dist.CLIENT)
public final class HelmetFuelSnapshot {

    public enum FuelKind {
        DIESEL("Diesel", 0xFFFFC34D),
        GASOLINE("Gasoline", 0xFFFF8B47),
        BIODIESEL("Biodiesel", 0xFF7BE26B),
        ETHANOL("Ethanol", 0xFF6FE9FF),
        PLANT_OIL("Plant Oil", 0xFFB9E26B),
        FUEL("Fuel", 0xFFFFD27A),
        UNKNOWN("Reserve", 0xFFBFC5D2);

        public final String displayName;
        public final int color;

        FuelKind(String displayName, int color) {
            this.displayName = displayName;
            this.color = color;
        }
    }

    public static final class Entry {
        public final String label;
        public final float amountMb;
        public final FuelKind kind;

        public Entry(String label, float amountMb, FuelKind kind) {
            this.label = label;
            this.amountMb = amountMb;
            this.kind = kind;
        }
    }

    public static final class Snapshot {
        public final boolean linked;
        public final boolean panelPresent;
        public final List<Entry> entries;
        public final float totalMb;
        public final float totalCapacityMb;
        public final FuelKind dominantKind;

        Snapshot(boolean linked, boolean panelPresent, List<Entry> entries,
                 float totalMb, float totalCapacityMb, FuelKind dominantKind) {
            this.linked = linked;
            this.panelPresent = panelPresent;
            this.entries = entries;
            this.totalMb = totalMb;
            this.totalCapacityMb = totalCapacityMb;
            this.dominantKind = dominantKind;
        }

        public boolean hasFuel() {
            return panelPresent && !entries.isEmpty();
        }

        /** 0..1 normalized fill ratio, or 0 if capacity unknown. */
        public float fillPct() {
            return totalCapacityMb > 0.5f
                ? Math.min(1.0f, totalMb / totalCapacityMb)
                : 0.0f;
        }
    }

    private static final Snapshot EMPTY_UNLINKED =
        new Snapshot(false, false, List.of(), 0.0f, 0.0f, FuelKind.UNKNOWN);
    private static final Snapshot EMPTY_LINKED_NO_PANEL =
        new Snapshot(true, false, List.of(), 0.0f, 0.0f, FuelKind.UNKNOWN);

    /** [1] */
    private static final long READ_INTERVAL_NANOS = 200_000_000L;
    private static long cachedReadNanos = Long.MIN_VALUE;
    @Nullable
    private static BlockPos cachedPos = null;
    private static Snapshot cachedSnapshot = EMPTY_UNLINKED;

    private HelmetFuelSnapshot() {
    }

    public static Snapshot read(@Nullable BlockPos linkedPanelPos) {
        long now = System.nanoTime();
        if (java.util.Objects.equals(linkedPanelPos, cachedPos)
            && cachedReadNanos != Long.MIN_VALUE
            && now - cachedReadNanos < READ_INTERVAL_NANOS) {
            return cachedSnapshot;
        }
        Snapshot fresh = readUncached(linkedPanelPos);
        cachedPos = linkedPanelPos == null ? null : linkedPanelPos.immutable();
        cachedReadNanos = now;
        cachedSnapshot = fresh;
        return fresh;
    }

    private static Snapshot readUncached(@Nullable BlockPos linkedPanelPos) {
        if (linkedPanelPos == null) return EMPTY_UNLINKED;
        Minecraft mc = Minecraft.getInstance();
        Level level = mc.level;
        if (level == null) return EMPTY_LINKED_NO_PANEL;
        if (!level.isLoaded(linkedPanelPos)) return EMPTY_LINKED_NO_PANEL;
        BlockEntity be = level.getBlockEntity(linkedPanelPos);
        if (!(be instanceof StatsDisplayPanelBE panel) || panel.isRemoved()) {
            return EMPTY_LINKED_NO_PANEL;
        }

        // [2]
        java.util.LinkedHashMap<String, Float> contentBySource = new java.util.LinkedHashMap<>();
        java.util.HashMap<String, String> labelBySource = new java.util.HashMap<>();
        java.util.HashMap<String, FuelKind> kindBySource = new java.util.HashMap<>();
        java.util.HashMap<String, Float> capacityBySource = new java.util.HashMap<>();

        for (StatsDisplayPanelBE.DataChannel channel : panel.getActiveChannels()) {
            if (channel == null) continue;
            if (channel.getStore().getMetricType() != MetricType.FLUID_MB) continue;
            String id = channel.getId();
            String label = channel.getLabel();
            float amount = channel.getStore().getLatestValue();
            if (!Float.isFinite(amount) || amount < 0.0f) continue;
            String source = sourceKey(id, label);
            boolean isCapacity = looksLikeCapacityChannel(id) || looksLikeCapacityChannel(label);
            boolean isMeta     = looksLikeCapacityOrMeta(id) || looksLikeCapacityOrMeta(label);
            if (isCapacity) {
                Float prev = capacityBySource.get(source);
                if (prev == null || amount > prev) capacityBySource.put(source, amount);
                continue;
            }
            if (isMeta) continue; // "Reserve Fuel %", "Mismatch", "Reserve Links" etc.
            if (amount <= 0.5f) continue;
            // Keep only the largest content reading per source: the engine fuel tank and its
            // reserve mirror describe the same fluid, so we must not add them together.
            Float prev = contentBySource.get(source);
            if (prev == null || amount > prev) {
                contentBySource.put(source, amount);
                labelBySource.put(source, label != null && !label.isEmpty() ? label : id);
                kindBySource.put(source, classify(id, label));
            }
        }

        List<Entry> entries = new ArrayList<>();
        float total = 0.0f;
        int[] kindWeights = new int[FuelKind.values().length];
        for (java.util.Map.Entry<String, Float> bucket : contentBySource.entrySet()) {
            float amt = bucket.getValue();
            FuelKind kind = kindBySource.getOrDefault(bucket.getKey(), FuelKind.UNKNOWN);
            entries.add(new Entry(labelBySource.getOrDefault(bucket.getKey(), bucket.getKey()), amt, kind));
            total += amt;
            kindWeights[kind.ordinal()] += Math.round(amt);
        }

        // Capacity sums across distinct sources (one MAX'd value each).
        float totalCapacity = 0.0f;
        for (Float v : capacityBySource.values()) totalCapacity += v;

        // Sort by amount descending.
        entries.sort((a, b) -> Float.compare(b.amountMb, a.amountMb));

        FuelKind dominant = FuelKind.UNKNOWN;
        int bestWeight = -1;
        for (FuelKind k : FuelKind.values()) {
            int w = kindWeights[k.ordinal()];
            if (k == FuelKind.UNKNOWN) continue;
            if (w > bestWeight) {
                bestWeight = w;
                dominant = k;
            }
        }
        if (bestWeight <= 0) dominant = FuelKind.UNKNOWN;

        return new Snapshot(true, true, entries, total, totalCapacity, dominant);
    }

    private static boolean matchesFuelText(String text) {
        // Retained for potential future "strict fuel-only" mode; currently unused
        // because the helmet trusts any FLUID_MB content channel on the linked panel.
        if (text == null) return false;
        String lower = text.toLowerCase(Locale.ROOT);
        return lower.contains("reserve")
            || lower.contains("diesel")
            || lower.contains("gasoline")
            || lower.contains("biodiesel")
            || lower.contains("ethanol")
            || lower.contains("plantoil")
            || lower.contains("plant oil")
            || lower.contains("fuel_")
            || lower.contains("fuel ")
            || lower.contains("fuel-")
            || lower.endsWith("fuel");
    }

    /** [3] */
    private static boolean looksLikeCapacityOrMeta(String text) {
        if (text == null) return false;
        String lower = text.toLowerCase(Locale.ROOT);
        return lower.contains("capacity")
            || lower.contains("fill %")
            || lower.contains("fill%")
            || lower.contains("fuel %")
            || lower.contains("fuel%")
            || lower.contains("mismatch")
            || lower.contains("link")
            || lower.endsWith("%");
    }

    /** Narrower test: only "capacity"-named channels carry tank size in mB. */
    private static boolean looksLikeCapacityChannel(String text) {
        if (text == null) return false;
        return text.toLowerCase(Locale.ROOT).contains("capacity");
    }

    /** [4] */
    private static String sourceKey(@Nullable String id, @Nullable String label) {
        if (id != null && id.startsWith("src_")) {
            int next = id.indexOf('_', 4);
            return next > 4 ? id.substring(0, next) : id;
        }
        if (id != null && !id.isEmpty()) return id;
        return label != null && !label.isEmpty() ? label : "?";
    }

    private static FuelKind classify(String id, String label) {
        String text = ((id != null ? id : "") + " " + (label != null ? label : "")).toLowerCase(Locale.ROOT);
        if (text.contains("biodiesel")) return FuelKind.BIODIESEL;
        if (text.contains("diesel")) return FuelKind.DIESEL;
        if (text.contains("gasoline")) return FuelKind.GASOLINE;
        if (text.contains("ethanol")) return FuelKind.ETHANOL;
        if (text.contains("plant oil") || text.contains("plantoil")) return FuelKind.PLANT_OIL;
        if (text.contains("fuel")) return FuelKind.FUEL;
        return FuelKind.UNKNOWN;
    }
}
