package com.createstats.seismic.archive;

import com.createstats.CreateStats;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.io.IOException;

public final class SeismicArchiveData extends SavedData {
    static final String FILE = CreateStats.MOD_ID + "_seismic_archive";

    private final SeismicArchive archive;
    private long savedRevision = Long.MIN_VALUE;

    private SeismicArchiveData(SeismicArchive archive) {
        this.archive = archive;
    }

    public static SeismicArchiveData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(() -> new SeismicArchiveData(new SeismicArchive()), SeismicArchiveData::load),
                FILE);
    }

    public SeismicArchive archive() {
        return archive;
    }

    public void touched() {
        if (archive.revision() != savedRevision) {
            savedRevision = archive.revision();
            setDirty();
        }
    }

    private static SeismicArchiveData load(CompoundTag tag, HolderLookup.Provider registries) {
        if (!tag.contains("Archive", Tag.TAG_BYTE_ARRAY)) {
            return new SeismicArchiveData(new SeismicArchive());
        }
        try {
            return new SeismicArchiveData(ArchiveCodec.decode(tag.getByteArray("Archive")));
        } catch (IOException e) {
            CreateStats.LOGGER.warn("[Seismic] the survey archive could not be read and starts empty: {}", e.toString());
            return new SeismicArchiveData(new SeismicArchive());
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putByteArray("Archive", ArchiveCodec.encode(archive));
        return tag;
    }
}
