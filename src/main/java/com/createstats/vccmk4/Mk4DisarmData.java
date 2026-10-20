package com.createstats.vccmk4;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

public final class Mk4DisarmData extends SavedData {
    private static final String DATA_NAME = "create_stats_vcc4_disarm";

    private boolean disarmed;

    public Mk4DisarmData() {
    }

    public static Mk4DisarmData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
            new SavedData.Factory<>(Mk4DisarmData::new, Mk4DisarmData::load), DATA_NAME);
    }

    public static boolean disarmed(ServerLevel level) {
        MinecraftServer server = level.getServer();
        return server != null && get(server).disarmed;
    }

    public boolean isDisarmed() {
        return disarmed;
    }

    public void setDisarmed(boolean on) {
        if (disarmed == on) return;
        disarmed = on;
        setDirty();
    }

    private static Mk4DisarmData load(CompoundTag tag, HolderLookup.Provider registries) {
        Mk4DisarmData data = new Mk4DisarmData();
        data.disarmed = tag.getBoolean("Disarmed");
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putBoolean("Disarmed", disarmed);
        return tag;
    }
}
