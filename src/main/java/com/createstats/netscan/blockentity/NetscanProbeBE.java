package com.createstats.netscan.blockentity;

import javax.annotation.Nullable;

import com.createstats.netscan.NetscanEngine;
import com.createstats.netscan.NetscanHost;
import com.createstats.netscan.ScannerForm;
import com.createstats.netscan.ScannerTier;
import com.createstats.netscan.block.NetscanProbeBlock;
import com.createstats.power.FeConsumer;
import com.createstats.power.FeProfiles;
import com.createstats.power.IFeConsumer;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

// clamped to a chest vs stopped drivetrain are different problems, say which
public class NetscanProbeBE extends BlockEntity implements NetscanHost, IFeConsumer,
        IHaveGoggleInformation {
    private final NetscanEngine engine = new NetscanEngine(this);
    private FeConsumer feConsumer;
    private boolean powered;

    public NetscanProbeBE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public FeConsumer feConsumer() {
        if (this.feConsumer == null) {
            this.feConsumer = FeConsumer.forBlockEntity(this,
                    FeProfiles.settings(scannerTier().feProfileKey()));
        }
        return this.feConsumer;
    }

    @Override
    public void onFePowerLost() {
        this.engine.setGraph(com.createstats.netscan.scan.ScanGraph.EMPTY);
    }

    @Override
    public ScannerTier scannerTier() {
        return getBlockState().getBlock() instanceof NetscanProbeBlock block
                ? block.tier() : ScannerTier.FIELD;
    }

    @Override
    public ScannerForm scannerForm() {
        return ScannerForm.CLAMP;
    }

    @Override
    public NetscanEngine scanEngine() {
        return this.engine;
    }

    @Override
    public BlockPos scanSeed() {
        if (this.level == null) {
            return null;
        }
        final BlockState state = getBlockState();
        if (!(state.getBlock() instanceof NetscanProbeBlock)) {
            return null;
        }
        final BlockPos target =
                this.worldPosition.relative(state.getValue(NetscanProbeBlock.FACING).getOpposite());
        if (!this.level.isLoaded(target)) {
            return null;
        }
        return this.level.getBlockEntity(target) instanceof KineticBlockEntity ? target : null;
    }

    @Override
    public boolean scanPowered() {
        return this.powered;
    }

    @Override
    public net.minecraft.world.level.Level scanLevel() {
        return this.level;
    }

    @Override
    public BlockPos scanPos() {
        return this.worldPosition;
    }

    public void tick() {
        if (!(this.level instanceof ServerLevel server)) {
            return;
        }

        this.powered = feConsumer().serviceTick(scanSeed() != null);
        com.createstats.power.FePowerFx.handleTransition(this.level, this.worldPosition, feConsumer());

        if (this.engine.serverTick(server, server.getGameTime())) {
            setChanged();
            this.level.sendBlockUpdated(this.worldPosition, getBlockState(), getBlockState(), 2);
        }
        updateLit();
    }

    private void updateLit() {
        final BlockState state = getBlockState();
        if (!(state.getBlock() instanceof NetscanProbeBlock)) {
            return;
        }
        final boolean lit = this.powered && !this.engine.graph().isEmpty();
        if (state.getValue(NetscanProbeBlock.LIT) != lit) {
            this.level.setBlock(this.worldPosition, state.setValue(NetscanProbeBlock.LIT, lit), 3);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        feConsumer().save(tag);
        this.engine.save(tag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        feConsumer().load(tag);
        this.engine.load(tag);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    @Nullable
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public boolean addToGoggleTooltip(java.util.List<Component> tooltip, boolean isPlayerSneaking) {
        return com.createstats.netscan.NetscanGoggle.append(tooltip, this);
    }
}
