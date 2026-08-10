package com.createstats.blockentity;

import com.createstats.antenna.AntennaTier;
import com.createstats.antenna.RangeAttachment;
import com.createstats.train.manifest.FreightStationLink;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class FreightManifestClipboardBE extends BlockEntity implements RangeAttachment {

    public static final String TAG_STATION_LINK = "StationLink";

    private FreightStationLink stationLink;
    private AntennaTier antennaTier;

    public FreightManifestClipboardBE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        this.stationLink = new FreightStationLink();
        this.antennaTier = AntennaTier.NONE;
    }

    public FreightStationLink getStationLink() {
        return this.stationLink.copy();
    }

    public boolean isLinked() {
        return this.stationLink.isLinked();
    }

    public void setStationLink(FreightStationLink stationLink) {
        this.stationLink = stationLink.copy();
        this.notifyUpdate();
    }

    @Override
    public AntennaTier getAntennaTier() {
        return this.antennaTier;
    }

    @Override
    public void setAntennaTier(AntennaTier tier) {
        AntennaTier resolvedTier = tier == null ? AntennaTier.NONE : tier;
        if (this.antennaTier == resolvedTier) {
            return;
        }
        this.antennaTier = resolvedTier;
        this.notifyUpdate();
    }

    private void notifyUpdate() {
        this.setChanged();
        if (this.level != null) {
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (this.stationLink.isLinked()) {
            tag.put(TAG_STATION_LINK, this.stationLink.save());
        }
        tag.putString("AntennaTier", this.antennaTier.name());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains(TAG_STATION_LINK)) {
            this.stationLink = FreightStationLink.load(tag.getCompound(TAG_STATION_LINK));
        } else {
            this.stationLink = new FreightStationLink();
        }
        this.antennaTier = AntennaTier.byName(tag.getString("AntennaTier"));
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        this.saveAdditional(tag, registries);
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt, HolderLookup.Provider registries) {
        CompoundTag tag = pkt.getTag();
        if (tag != null) {
            this.loadAdditional(tag, registries);
        }
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        this.loadAdditional(tag, registries);
    }
}
