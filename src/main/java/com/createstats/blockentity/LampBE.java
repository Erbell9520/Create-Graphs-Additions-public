package com.createstats.blockentity;

import com.createstats.block.LampBlock;
import com.createstats.power.ApplianceStatus;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;

// not FeConsumer, creativeEnergy would light every lamp with nothing wired. defeats the point
public class LampBE extends BlockEntity
        implements com.createstats.power.FeGridMember, com.createstats.power.FeManagedLoad,
        com.createstats.power.Gateable, com.createstats.power.Adjustable,
        com.createstats.power.HomeAppliance,
        com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation,
        com.createstats.util.CopyableSettings {
    private static final int BUFFER = 100;

    private static final int DRAIN = 1;

    private int energy;

    private boolean gated;

    private int ratedDraw;

    private int measuredDraw;

    private final com.createstats.power.FeManagedState managed = new com.createstats.power.FeManagedState();

    private final IEnergyStorage energyView = new IEnergyStorage() {
        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            final int room = Math.min(maxReceive, BUFFER - energy);
            if (room <= 0) {
                return 0;
            }
            if (!simulate) {
                energy += room;
                setChanged();
            }
            return room;
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            return 0;
        }

        @Override
        public int getEnergyStored() {
            return energy;
        }

        @Override
        public int getMaxEnergyStored() {
            return BUFFER;
        }

        @Override
        public boolean canExtract() {
            return false;
        }

        @Override
        public boolean canReceive() {
            return true;
        }
    };

    public LampBE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public IEnergyStorage energyHandle() {
        return energyView;
    }

    @Override
    public void setGated(boolean gated) {
        if (this.gated == gated) {
            return;
        }
        this.gated = gated;
        setChanged();
        refresh();
    }

    @Override
    public boolean isGated() {
        return gated;
    }

    @Override
    public int getMode() {
        final BlockState state = getBlockState();
        return state.hasProperty(LampBlock.DIM) ? state.getValue(LampBlock.DIM)
                : LampBlock.DIM_FRACTION.length - 1;
    }

    @Override
    public void setMode(int mode) {
        if (level == null || level.isClientSide()) {
            return;
        }
        final BlockState state = getBlockState();
        if (!state.hasProperty(LampBlock.DIM)) {
            return;
        }
        final int next = Math.max(0, Math.min(LampBlock.DIM_FRACTION.length - 1, mode));
        if (next == state.getValue(LampBlock.DIM)) {
            return;
        }

        level.setBlock(worldPosition, state.setValue(LampBlock.DIM, next), Block.UPDATE_ALL);
    }

    @Override
    public java.util.List<net.minecraft.network.chat.Component> modeLabels() {
        final java.util.List<net.minecraft.network.chat.Component> out =
                new java.util.ArrayList<>(LampBlock.DIM_FRACTION.length);
        for (float fraction : LampBlock.DIM_FRACTION) {
            out.add(net.minecraft.network.chat.Component.literal(Math.round(fraction * 100) + "%"));
        }
        return out;
    }

    @Override
    public String settingsKey() {
        return "create_stats:lamp";
    }

    @Override
    public boolean hasCopyableSettings() {
        return getBlockState().hasProperty(LampBlock.DIM);
    }

    @Override
    public void writeSettings(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("Dim", getMode());
    }

    @Override
    public boolean applySettings(CompoundTag tag, HolderLookup.Provider registries,
                                 @org.jetbrains.annotations.Nullable net.minecraft.world.entity.player.Player player,
                                 boolean simulate) {
        if (!tag.contains("Dim") || !getBlockState().hasProperty(LampBlock.DIM)) {
            return false;
        }
        if (!simulate) {
            setMode(tag.getInt("Dim"));
        }
        return true;
    }

    @Override
    public void writeFreshDefaults(CompoundTag tag) {
        tag.putInt("Tier", com.createstats.power.FePriority.DEFAULT.ordinal());
    }

    @Override
    public void writeSafe(CompoundTag tag, HolderLookup.Provider registries) {
        writeFreshDefaults(tag);
    }

    @Override
    public com.createstats.power.HomeAppliance.Category applianceCategory() {
        return com.createstats.power.HomeAppliance.Category.LIGHTING;
    }

    @Override
    @org.jetbrains.annotations.Nullable
    public ApplianceStatus applianceStatus() {
        if (isLit()) {
            final int percent = Math.round(LampBlock.dimFraction(getBlockState()) * 100.0f);
            return ApplianceStatus.idle(percent >= 100
                    ? Component.translatable("status.create_stats.home.lamp_lit")
                    : Component.translatable("status.create_stats.home.lamp_dimmed", percent));
        }
        if (gated || isManagedShed()) {
            return null;
        }
        if (managed.isSuppressed()) {
            return ApplianceStatus.idle(Component.translatable("status.create_stats.home.held_pdu"));
        }
        if (level != null && level.hasNeighborSignal(worldPosition)) {
            return ApplianceStatus.idle(Component.translatable("status.create_stats.home.held_redstone"));
        }
        return null;
    }

    public int drawFePerTick() {
        return DRAIN;
    }

    public static int drawPerTick() {
        return DRAIN;
    }

    @Override
    public int ratedDrawFePerTick() {
        return ratedDraw;
    }

    @Override
    public int measuredDrawFePerTick() {
        return measuredDraw;
    }

    @Override
    public com.createstats.power.FePriority managedPriority() {
        return managed.priority();
    }

    @Override
    public void setManagedPriority(com.createstats.power.FePriority tier) {
        managed.setPriority(tier);
        setChanged();
    }

    @Override
    public com.createstats.power.FeLock managedLock() {
        return managed.lock();
    }

    @Override
    public void setManagedLock(com.createstats.power.FeLock lock) {
        managed.setLock(lock);
        setChanged();
        refresh();
    }

    @Override
    public void setManagedShed(boolean shed) {
        if (managed.isShed() == shed) {
            return;
        }
        managed.setShed(shed);
        refresh();
    }

    @Override
    public boolean isManagedShed() {
        return managed.isShed();
    }

    public boolean isLit() {
        return getBlockState().hasProperty(LampBlock.LIT) && getBlockState().getValue(LampBlock.LIT);
    }

    @Override
    public boolean addToGoggleTooltip(java.util.List<net.minecraft.network.chat.Component> tooltip,
                                      boolean isPlayerSneaking) {
        tooltip.add(com.createstats.power.FeGoggle.title("Lamp"));
        final boolean lit = isLit();
        if (gated) {
            tooltip.add(com.createstats.power.FeGoggle.dim("Gated by a linked switch"));
        } else if (lit) {
            final int percent = Math.round(LampBlock.dimFraction(getBlockState()) * 100);
            tooltip.add(com.createstats.power.FeGoggle.ok(
                    percent >= 100 ? "Lit" : "Lit, dimmed to " + percent + "%"));
        } else if (managed.isSuppressed()) {
            tooltip.add(com.createstats.power.FeGoggle.warn("Off at the panel"));
        } else {
            tooltip.add(com.createstats.power.FeGoggle.bad("No power"));
        }
        tooltip.add(com.createstats.power.FeGoggle.draw(lit ? drawFePerTick() : 0));
        return true;
    }

    @org.jetbrains.annotations.Nullable
    private com.createstats.power.FeWireNetwork network;

    @Override
    public long nodeKey() {
        return worldPosition.asLong();
    }

    @Override
    public int storedEnergy() {
        return 0;
    }

    @Override
    public void setStoredEnergy(int amount) {
    }

    @Override
    public int bufferCapacity() {
        return 0;
    }

    @Override
    public int transferRate() {
        return 64;
    }

    @Override
    public IEnergyStorage attachedStorage() {
        return energyView;
    }

    @Override
    public boolean isGone(net.minecraft.world.level.Level owner) {
        return isRemoved() || getLevel() != owner;
    }

    @Override
    public void setLastFlow(int flow) {
    }

    public void adoptNetwork(com.createstats.power.FeWireNetwork network) {
        this.network = network;
    }

    @Override
    @javax.annotation.Nullable
    public com.createstats.power.FeWireNetwork network() {
        return this.network;
    }

    public void invalidateNetwork() {
        if (network != null) {
            network.markDirty();
            network = null;
        }
    }

    public void tick() {
        if (level == null || level.isClientSide()) {
            return;
        }
        if (network == null || network.isDirty()) {
            com.createstats.power.FeWireNetwork.buildFrom(this);
        }
        if (network != null) {
            network.solveOnce(level);
        }

        if (network == null || !network.isPduManaged(level.getGameTime())) {
            setManagedShed(false);
        }

        final boolean wantsToRun = !gated && !managed.isSuppressed()
                && !level.hasNeighborSignal(worldPosition);

        ratedDraw = !gated && !managed.isForcedOff() && !level.hasNeighborSignal(worldPosition)
                ? DRAIN : 0;
        if (wantsToRun && energy > 0) {
            energy = Math.max(0, energy - DRAIN);
            measuredDraw = DRAIN;
            setChanged();
        } else {
            measuredDraw = 0;
        }
        refresh();
    }

    private void refresh() {
        if (level == null || level.isClientSide()) {
            return;
        }
        final BlockState state = getBlockState();
        if (!(state.getBlock() instanceof LampBlock)) {
            return;
        }
        final boolean lit = energy > 0 && !gated && !managed.isSuppressed()
                && !level.hasNeighborSignal(worldPosition);
        if (lit != state.getValue(LampBlock.LIT)) {
            level.setBlock(worldPosition, state.setValue(LampBlock.LIT, lit), Block.UPDATE_ALL);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Energy", energy);
        tag.putBoolean("Gated", gated);
        tag.putInt("Tier", managed.priorityOrdinal());
        tag.putInt("Lock", managed.lockOrdinal());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        energy = Math.max(0, Math.min(BUFFER, tag.getInt("Energy")));
        gated = tag.getBoolean("Gated");
        managed.load(tag.getInt("Tier"), tag.getInt("Lock"));
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        invalidateNetwork();
    }
}
