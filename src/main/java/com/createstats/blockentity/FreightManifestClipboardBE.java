package com.createstats.blockentity;

import com.createstats.antenna.AntennaTier;
import com.createstats.antenna.RangeAttachment;
import com.createstats.train.manifest.FreightManifestPageSummary;
import com.createstats.train.manifest.FreightManifestStationResolver;
import com.createstats.train.manifest.FreightStationLink;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class FreightManifestClipboardBE extends BlockEntity implements RangeAttachment {
    public static final String TAG_STATION_LINK = "StationLink";
    private static final String TAG_PAGE = "Page";

    private static final int REFRESH_IDLE = 40;
    private static final int REFRESH_ACTIVE = 10;

    private FreightStationLink stationLink;
    private AntennaTier antennaTier;
    private FreightManifestPageSummary pageSummary;
    private int refreshCooldown;

    public FreightManifestClipboardBE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        this.stationLink = new FreightStationLink();
        this.antennaTier = AntennaTier.NONE;
        this.pageSummary = FreightManifestPageSummary.BLANK;

        this.refreshCooldown = Math.floorMod(pos.hashCode(), REFRESH_IDLE) + 1;
    }

    public FreightManifestPageSummary getPageSummary() {
        return this.pageSummary;
    }

    public void serverTick() {
        if (--this.refreshCooldown > 0) {
            return;
        }
        this.refreshCooldown = this.pageSummary.isTrainPresent() ? REFRESH_ACTIVE : REFRESH_IDLE;
        FreightManifestPageSummary next = this.readStation();
        if (next.matches(this.pageSummary)) {
            return;
        }
        this.pageSummary = next;
        this.setChanged();
        if (this.level != null) {
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(),
                    Block.UPDATE_CLIENTS);
        }
    }

    private FreightManifestPageSummary readStation() {
        if (!this.stationLink.isLinked() || this.level == null || this.level.getServer() == null) {
            return FreightManifestPageSummary.BLANK;
        }
        ServerLevel stationLevel = FreightManifestStationResolver.resolveStationLevel(
                this.level.getServer(), this.stationLink);
        if (stationLevel == null) {
            return FreightManifestPageSummary.unreachable();
        }
        return FreightManifestStationResolver.capturePageSummary(stationLevel, this.stationLink);
    }

    public FreightStationLink getStationLink() {
        return this.stationLink.copy();
    }

    public boolean isLinked() {
        return this.stationLink.isLinked();
    }

    public void setStationLink(FreightStationLink stationLink) {
        this.stationLink = stationLink.copy();

        this.pageSummary = FreightManifestPageSummary.BLANK;
        this.refreshCooldown = 1;
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
        tag.put(TAG_PAGE, this.pageSummary.save());
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
        this.pageSummary = tag.contains(TAG_PAGE)
                ? FreightManifestPageSummary.load(tag.getCompound(TAG_PAGE))
                : FreightManifestPageSummary.BLANK;
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
