package com.createstats.rs;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.createstats.blockentity.IAutoDataReceiver;
import com.createstats.blockentity.SourceConfig;
import com.createstats.blockentity.StatsDisplayPanelBE;
import com.createstats.compat.RefinedStorageCompat;
import com.createstats.compat.RefinedStorageCompatHelper;
import com.createstats.data.MetricType;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

public final class RsLinkReader {
    private static final int CHANNELS_PER_NETWORK = 12;

    private RsLinkReader() {
    }

    public static boolean emit(StatsDisplayPanelBE panel, BlockPos sourcePos,
                               @Nullable BlockEntity sourceBE) {
        if (!RefinedStorageCompat.isAvailable()
                || !RefinedStorageCompatHelper.isNetworkBlockEntity(sourceBE)) {
            return false;
        }
        final RefinedStorageCompatHelper.NetworkRead read = RefinedStorageCompatHelper.read(sourceBE);
        final String type = RefinedStorageCompatHelper.SOURCE_TYPE;
        final SourceConfig config = panel.registerOrUpdateSource(sourcePos, type);
        final long gameTick = panel.getLevel() != null ? panel.getLevel().getGameTime() : 0L;

        final RsNetworkState state = panel.getOrCreateRsNetwork(config.getSourceId());
        state.ingest(read, gameTick);

        emitScalars(panel, sourcePos, type, read);
        emitItems(panel, sourcePos, type, config, read);
        return true;
    }

    public static boolean emit(IAutoDataReceiver receiver, BlockPos sourcePos,
                               @Nullable BlockEntity sourceBE) {
        if (!RefinedStorageCompat.isAvailable()
                || !RefinedStorageCompatHelper.isNetworkBlockEntity(sourceBE)) {
            return false;
        }
        final RefinedStorageCompatHelper.NetworkRead read = RefinedStorageCompatHelper.read(sourceBE);
        emitScalars(receiver, sourcePos, RefinedStorageCompatHelper.SOURCE_TYPE, read);
        return true;
    }

    private static void emitScalars(IAutoDataReceiver receiver, BlockPos sourcePos, String type,
                                    RefinedStorageCompatHelper.NetworkRead read) {
        receiver.setSourceType(type);
        receiver.onAutoData(sourcePos, type, RsChannels.ONLINE, "Network Online",
                read.online() ? 1.0f : 0.0f, MetricType.BOOLEAN_FLAG);
        receiver.onAutoData(sourcePos, type, RsChannels.STORED, "Items Stored",
                clampToFloat(read.itemsStored()), MetricType.ITEM_COUNT);
        receiver.onAutoData(sourcePos, type, RsChannels.TYPES, "Item Types",
                read.itemTypes(), MetricType.ITEM_COUNT);
        receiver.onAutoData(sourcePos, type, RsChannels.DISKS, "Storage Sources",
                read.storageCount(), MetricType.ITEM_COUNT);
        receiver.onAutoData(sourcePos, type, RsChannels.PATTERNS, "Autocrafting Patterns",
                read.patternCount(), MetricType.ITEM_COUNT);

        if (read.hasCapacity()) {
            receiver.onAutoData(sourcePos, type, RsChannels.CAPACITY, "Item Capacity",
                    clampToFloat(read.itemCapacity()), MetricType.ITEM_COUNT);
            receiver.onAutoData(sourcePos, type, RsChannels.FILL, "Network Fill %",
                    read.fillPercent(), MetricType.FILL_LEVEL);
        }
        if (read.fluidsStored() > 0L || read.fluidTypes() > 0) {
            receiver.onAutoData(sourcePos, type, RsChannels.FLUID, "Fluids Stored (mB)",
                    clampToFloat(read.fluidsStored()), MetricType.FLUID_MB);
            receiver.onAutoData(sourcePos, type, RsChannels.FLUID_TYPES, "Fluid Types",
                    read.fluidTypes(), MetricType.ITEM_COUNT);
        }
        if (read.energyCapacity() > 0L) {
            receiver.onAutoData(sourcePos, type, RsChannels.ENERGY, "Network Energy (FE)",
                    clampToFloat(read.energyStored()), MetricType.ENERGY_FE);
            receiver.onAutoData(sourcePos, type, RsChannels.ENERGY_PERCENT, "Network Energy %",
                    read.energyPercent(), MetricType.FILL_LEVEL);
        }
    }

    private static void emitItems(StatsDisplayPanelBE panel, BlockPos sourcePos, String type,
                                  SourceConfig config,
                                  RefinedStorageCompatHelper.NetworkRead read) {
        final Set<String> emitted = new LinkedHashSet<>();
        int written = 0;
        for (final RefinedStorageCompatHelper.Entry entry : read.items()) {
            if (written++ >= CHANNELS_PER_NETWORK) {
                break;
            }
            final String metricId = RsChannels.itemMetricId(entry.id());
            emitted.add(metricId);
            panel.onAutoData(sourcePos, type, metricId, entry.displayName(),
                    clampToFloat(entry.amount()), MetricType.ITEM_COUNT);
        }
        zeroDeparted(panel, sourcePos, type, config, emitted);
    }

    private static void zeroDeparted(StatsDisplayPanelBE panel, BlockPos sourcePos, String type,
                                     SourceConfig config, Set<String> emitted) {
        final String prefix = "src_" + config.getSourceId() + "_auto_";
        final List<StatsDisplayPanelBE.DataChannel> channels =
                new ArrayList<>(panel.getChannelsForSource(config.getSourceId()));
        for (final StatsDisplayPanelBE.DataChannel channel : channels) {
            final String id = channel.getId();
            if (id == null || !id.startsWith(prefix)) {
                continue;
            }
            final String metricId = id.substring(prefix.length());
            if (!metricId.startsWith(RsChannels.ITEM_PREFIX) || emitted.contains(metricId)) {
                continue;
            }
            if (channel.getStore().getLatestValue() <= 0.0f) {
                continue;
            }
            panel.onAutoData(sourcePos, type, metricId, channel.getLabel(), 0.0f,
                    MetricType.ITEM_COUNT);
        }
    }

    private static float clampToFloat(long value) {
        return (float) Math.max(0L, Math.min(value, 1_000_000_000_000L));
    }
}
