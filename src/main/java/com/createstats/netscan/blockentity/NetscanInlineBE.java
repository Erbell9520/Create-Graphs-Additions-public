package com.createstats.netscan.blockentity;

import java.util.List;

import com.createstats.netscan.NetscanEngine;
import com.createstats.netscan.NetscanHost;
import com.createstats.netscan.ScannerForm;
import com.createstats.netscan.ScannerTier;
import com.createstats.netscan.block.NetscanInlineBlock;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class NetscanInlineBE extends KineticBlockEntity implements NetscanHost {
    private static final int COAST_TICKS = 200;

    private final NetscanEngine engine = new NetscanEngine(this);
    private int coastTicks;

    public NetscanInlineBE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
    }

    @Override
    public ScannerTier scannerTier() {
        return getBlockState().getBlock() instanceof NetscanInlineBlock block
                ? block.tier() : ScannerTier.FIELD;
    }

    @Override
    public ScannerForm scannerForm() {
        return ScannerForm.INLINE;
    }

    @Override
    public NetscanEngine scanEngine() {
        return this.engine;
    }

    @Override
    public BlockPos scanSeed() {
        return this.worldPosition;
    }

    // keep reading through overstress, create's stressometer does too. going dark on the failure you came to diagnose is useless
    @Override
    public boolean scanPowered() {
        return Math.abs(getSpeed()) > 0.0f || isOverStressed() || this.coastTicks > 0;
    }

    @Override
    public net.minecraft.world.level.Level scanLevel() {
        return this.level;
    }

    @Override
    public BlockPos scanPos() {
        return this.worldPosition;
    }

    @Override
    public float calculateStressApplied() {
        final float impact = (float) scannerTier().stressPerRpm();
        this.lastStressApplied = impact;
        return impact;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level == null || this.level.isClientSide() || !(this.level instanceof ServerLevel server)) {
            return;
        }
        if (Math.abs(getSpeed()) > 0.0f) {
            this.coastTicks = COAST_TICKS;
        } else if (this.coastTicks > 0) {
            this.coastTicks--;
        }
        if (this.engine.serverTick(server, server.getGameTime())) {
            setChanged();
            sendData();
        }
        updateLit();
    }

    private void updateLit() {
        final BlockState state = getBlockState();
        if (!(state.getBlock() instanceof NetscanInlineBlock)) {
            return;
        }
        final boolean lit = scanPowered() && !this.engine.graph().isEmpty();
        if (state.getValue(NetscanInlineBlock.LIT) != lit) {
            this.level.setBlock(this.worldPosition, state.setValue(NetscanInlineBlock.LIT, lit), 3);
        }
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        this.engine.save(tag);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        this.engine.load(tag);
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        final boolean added = super.addToGoggleTooltip(tooltip, isPlayerSneaking);
        return com.createstats.netscan.NetscanGoggle.append(tooltip, this) || added;
    }
}
