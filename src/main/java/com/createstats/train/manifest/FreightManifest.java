package com.createstats.train.manifest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.createmod.catnip.nbt.NBTHelper;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.nbt.Tag;

public class FreightManifest {
    private final List<FreightManifestLine> lines;
    @Nullable
    private UUID ownerId;
    private int version;

    public FreightManifest() {
        this.lines = new ArrayList<>();
        this.version = 0;
    }

    public List<FreightManifestLine> getLines() {
        return this.lines;
    }

    public void setLines(List<FreightManifestLine> lines) {
        this.lines.clear();
        for (FreightManifestLine line : lines) {
            this.lines.add(line.copy());
        }
    }

    public boolean isEmpty() {
        return this.lines.isEmpty();
    }

    @Nullable
    public UUID getOwnerId() {
        return this.ownerId;
    }

    public void setOwnerId(@Nullable UUID ownerId) {
        this.ownerId = ownerId;
    }

    public int getVersion() {
        return this.version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public void bumpVersion() {
        this.version++;
    }

    public FreightManifest copy() {
        FreightManifest copy = new FreightManifest();
        copy.setLines(this.lines);
        copy.ownerId = this.ownerId;
        copy.version = this.version;
        return copy;
    }

    public CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        ListTag lineList = new ListTag();
        for (FreightManifestLine line : this.lines) {
            lineList.add(line.save(registries));
        }
        tag.put("Lines", lineList);
        if (this.ownerId != null) {
            tag.putUUID("Owner", this.ownerId);
        }
        tag.putInt("Version", this.version);
        return tag;
    }

    public static FreightManifest load(CompoundTag tag, HolderLookup.Provider registries) {
        FreightManifest manifest = new FreightManifest();
        NBTHelper.iterateCompoundList(tag.getList("Lines", Tag.TAG_COMPOUND), lineTag -> manifest.lines.add(FreightManifestLine.load(lineTag, registries)));
        if (tag.hasUUID("Owner")) {
            manifest.ownerId = tag.getUUID("Owner");
        }
        manifest.version = tag.getInt("Version");
        return manifest;
    }

    public void encode(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(this.lines.size());
        for (FreightManifestLine line : this.lines) {
            line.encode(buf);
        }
        buf.writeBoolean(this.ownerId != null);
        if (this.ownerId != null) {
            buf.writeUUID(this.ownerId);
        }
        buf.writeVarInt(this.version);
    }

    public static FreightManifest decode(RegistryFriendlyByteBuf buf) {
        FreightManifest manifest = new FreightManifest();
        int size = buf.readVarInt();
        for (int i = 0; i < size; i++) {
            manifest.lines.add(FreightManifestLine.decode(buf));
        }
        if (buf.readBoolean()) {
            manifest.ownerId = buf.readUUID();
        }
        manifest.version = buf.readVarInt();
        return manifest;
    }
}
