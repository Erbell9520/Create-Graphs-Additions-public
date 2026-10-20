package com.createstats.train.manifest;

import com.createstats.CreateStats;
import com.createstats.registry.ModBlocks;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.schedule.condition.ScheduleWaitCondition;
import com.simibubi.create.content.trains.station.GlobalStation;
import net.createmod.catnip.data.Pair;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class CargoManifestMetCondition extends ScheduleWaitCondition {
    private static final String STATUS_KEY = "create_stats_manifest_status";

    @Override
    public Pair<ItemStack, Component> getSummary() {
        return Pair.of(ModBlocks.FREIGHT_MANIFEST_CLIPBOARD.asStack(), Component.translatable("create_stats.schedule.condition.manifest_met"));
    }

    @Override
    public boolean tickCompletion(Level level, Train train, CompoundTag context) {
        GlobalStation currentStation = train.getCurrentStation();
        if (currentStation == null) {
            this.updateStatus(context, "Waiting for station data...");
            return false;
        }

        ResourceKey<Level> stationDimension = currentStation.getBlockEntityDimension();
        MinecraftServer server = level.getServer();
        if (stationDimension == null || server == null) {
            this.updateStatus(context, "Station dimension is unavailable.");
            return false;
        }

        ServerLevel stationLevel = server.getLevel(stationDimension);
        if (stationLevel == null) {
            this.updateStatus(context, "Station dimension is not loaded.");
            return false;
        }

        try {
            FreightManifestSavedData savedData = FreightManifestSavedData.get(stationLevel);
            FreightManifest manifest = savedData.getManifest(currentStation.getId());
            if (manifest == null || manifest.isEmpty()) {
                return true;
            }

            FreightManifestEvaluationResult result = FreightManifestLogic.evaluateStation(savedData,
                currentStation.getId(), train, stationLevel.getGameTime(), context);
            this.updateStatus(context, result.getStatusMessage());
            return result.isComplete();
        } catch (LinkageError error) {
            this.updateStatus(context, "Manifest system unavailable: " + error.getClass().getSimpleName());
            return false;
        }
    }

    private void updateStatus(CompoundTag context, String message) {
        if (!message.equals(context.getString(STATUS_KEY))) {
            context.putString(STATUS_KEY, message);
            this.requestStatusToUpdate(context);
        }
    }

    @Override
    protected void writeAdditional(net.minecraft.core.HolderLookup.Provider registries, CompoundTag tag) {
    }

    @Override
    protected void readAdditional(net.minecraft.core.HolderLookup.Provider registries, CompoundTag tag) {
    }

    @Override
    public ResourceLocation getId() {
        return ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, "manifest_met");
    }

    @Override
    public MutableComponent getWaitingStatus(Level level, Train train, CompoundTag tag) {
        if (!tag.contains(STATUS_KEY)) {
            return Component.literal("Waiting for cargo manifest...");
        }
        return Component.literal(tag.getString(STATUS_KEY));
    }
}
