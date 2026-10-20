package com.createstats.statsmk3.page;

import java.util.ArrayList;
import java.util.List;

import com.createstats.blockentity.StatsDisplayPanelBE;
import com.createstats.data.TimeSeriesStore;
import com.createstats.data.WindowedTrend;
import com.createstats.rs.RsChannels;
import com.createstats.rs.RsNetworkState;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class RsReadings {
    public static final String FAMILY = "storage.refined_storage";

    private RsReadings() {
    }

    public record Board(String label, boolean online, RsNetworkState state, List<Stock> stock) {
        public static final Board NONE =
                new Board("Storage Network", false, null, List.of());

        public boolean hasState() {
            return this.state != null;
        }
    }

    public record Stock(String itemId,
                        ItemStack stack,
                        String name,
                        long amount,
                        RsNetworkState.Row row,
                        TimeSeriesStore store) {
        public long delta(WindowedTrend.Window window) {
            return this.row == null ? 0L : this.row.deltaFor(window);
        }

        public float spread(WindowedTrend.Window window) {
            return this.row == null ? 0.0f : this.row.spreadFor(window);
        }

        public int coverageSeconds(WindowedTrend.Window window) {
            return this.row == null ? 0 : this.row.coverageFor(window);
        }

        public float relativeSpread(WindowedTrend.Window window) {
            return this.amount <= 0L ? 0.0f : spread(window) / this.amount;
        }

        public float perMinute(WindowedTrend.Window window) {
            final int seconds = coverageSeconds(window);
            return seconds <= 0 ? 0.0f : delta(window) * 60.0f / seconds;
        }

        public float[] series(WindowedTrend.Window window) {
            return this.row == null ? new float[0] : this.row.seriesFor(window);
        }

        public boolean hasSeries() {
            return this.row != null && this.row.hasSeries();
        }
    }

    public static List<Board> boardsOf(StatsDisplayPanelBE panel) {
        final List<Board> boards = new ArrayList<>();
        if (panel == null) {
            return boards;
        }
        for (final SourceProbe.Unit unit : SourceProbe.byFamily(panel, FAMILY)) {
            final String sourceId = unit.config() == null ? "" : unit.config().getSourceId();
            final RsNetworkState state = panel.getRsNetwork(sourceId);
            final String user = unit.config() == null ? "" : unit.config().getUserLabel();

            final boolean online = state != null ? state.online() : unit.flag(RsChannels.ONLINE);
            boards.add(new Board(user == null || user.isEmpty() ? "Storage Network" : user,
                    online, state, stockOf(unit, state)));
        }
        return boards;
    }

    public static boolean any(StatsDisplayPanelBE panel) {
        return !boardsOf(panel).isEmpty();
    }

    private static List<Stock> stockOf(SourceProbe.Unit unit, RsNetworkState state) {
        final List<Stock> stock = new ArrayList<>();
        if (state == null) {
            return stock;
        }
        for (final RsNetworkState.Row row : state.rows()) {
            final ItemStack stack = stackOf(row.itemId());
            stock.add(new Stock(row.itemId(), stack,
                    stack == null ? prettyId(row.itemId()) : stack.getHoverName().getString(),
                    row.amount(), row,
                    unit == null ? null : unit.store(RsChannels.itemMetricId(row.itemId()))));
        }
        return stock;
    }

    public static Stock hero(List<Stock> stock, String focusItemId) {
        if (stock.isEmpty()) {
            return null;
        }
        if (focusItemId != null && !focusItemId.isEmpty()) {
            for (final Stock s : stock) {
                if (s.itemId().equals(focusItemId)) {
                    return s;
                }
            }
        }
        return stock.get(0);
    }

    // null not EMPTY, a pack change leaves rows behind and they should say "removed"
    @org.jetbrains.annotations.Nullable
    public static ItemStack stackOf(String itemId) {
        if (itemId == null || itemId.isEmpty()) {
            return null;
        }
        final ItemStack cached = STACK_CACHE.get(itemId);
        if (cached != null) {
            return cached == MISSING ? null : cached;
        }
        ItemStack resolved = null;
        try {
            final ResourceLocation key = ResourceLocation.tryParse(itemId);
            final Item item = key == null
                    ? null : BuiltInRegistries.ITEM.getOptional(key).orElse(null);
            resolved = item == null ? null : new ItemStack(item);
        } catch (RuntimeException | LinkageError noRegistry) {
            resolved = null;
        }
        try {
            STACK_CACHE.put(itemId, resolved == null ? MISSING : resolved);
        } catch (RuntimeException | LinkageError ignored) {
        }
        return resolved;
    }

    private static final java.util.Map<String, ItemStack> STACK_CACHE =
            new java.util.concurrent.ConcurrentHashMap<>();

    private static final ItemStack MISSING = missingSentinel();

    @org.jetbrains.annotations.Nullable
    private static ItemStack missingSentinel() {
        try {
            return ItemStack.EMPTY;
        } catch (RuntimeException | LinkageError noRegistry) {
            return null;
        }
    }

    public static String prettyId(String itemId) {
        final int colon = itemId.indexOf(':');
        final String path = colon < 0 ? itemId : itemId.substring(colon + 1);
        final StringBuilder sb = new StringBuilder(path.length());
        boolean upper = true;
        for (int i = 0; i < path.length(); i++) {
            final char c = path.charAt(i) == '_' ? ' ' : path.charAt(i);
            sb.append(upper ? Character.toUpperCase(c) : c);
            upper = c == ' ';
        }
        return sb.toString();
    }

    public static float[] seriesFor(Stock stock, WindowedTrend.Window window) {
        final float[] tiered = stock.series(window);
        if (tiered.length > 1) {
            return tiered;
        }
        final TimeSeriesStore store = stock.store();
        if (store == null || store.getSampleCount() < 2) {
            return new float[0];
        }
        return store.getDataForTimeScale(Math.min(window.spanSeconds(), 180));
    }

    public static int coveredSeconds(Stock stock, WindowedTrend.Window window) {
        if (stock.series(window).length > 1) {
            return stock.coverageSeconds(window);
        }
        final TimeSeriesStore store = stock.store();
        if (store == null || store.getSampleCount() < 2) {
            return 0;
        }
        return Math.min(store.getDurationSeconds(), Math.min(window.spanSeconds(), 180));
    }
}
