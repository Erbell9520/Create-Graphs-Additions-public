package com.createstats.blockentity;

import com.createstats.ModConfiguration;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;

import java.util.List;
import java.util.Locale;

public class KineticDynamoBE extends KineticBlockEntity
        implements com.createstats.power.FeSupply, com.createstats.power.FeForecastingSource {
    private int energy;

    private int lastGeneratedFePerTick;

    private int lastOutflowFePerTick;

    private int outflowThisTick;

    private int outflowBudget() {
        return Math.max(0, transferRate() - outflowThisTick);
    }

    private void recordOutflow(int amount) {
        if (amount > 0) {
            outflowThisTick += amount;
        }
    }

    private final IEnergyStorage energyView = new IEnergyStorage() {
        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            return 0;
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            final int amount = Math.min(Math.min(maxExtract, outflowBudget()), energy);
            if (amount <= 0) {
                return 0;
            }
            if (!simulate) {
                energy -= amount;
                recordOutflow(amount);
                setChanged();
            }
            return amount;
        }

        @Override
        public int getEnergyStored() {
            return energy;
        }

        @Override
        public int getMaxEnergyStored() {
            return bufferCapacity();
        }

        @Override
        public boolean canExtract() {
            return true;
        }

        @Override
        public boolean canReceive() {
            return false;
        }
    };

    public KineticDynamoBE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public IEnergyStorage energyHandle() {
        return energyView;
    }

    public int storedEnergy() {
        return energy;
    }

    public int lastGeneratedFePerTick() {
        return lastGeneratedFePerTick;
    }

    public int lastOutflowFePerTick() {
        return lastOutflowFePerTick;
    }

    @Override
    public int potentialFePerTick() {
        final float speed = Math.abs(getSpeed());
        return speed <= 0.01f ? 0 : Math.max(0, (int) Math.floor(speed * fePerRpm()));
    }

    @Override
    public int potentialFePerTickIn(int ticks) {
        return potentialFePerTick();
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide()) {
            return;
        }
        final int capacity = bufferCapacity();
        if (energy > capacity) {
            energy = capacity;
        }

        lastOutflowFePerTick = outflowThisTick;
        outflowThisTick = 0;

        final int potential = potentialFePerTick();
        final int generated = Math.min(potential, Math.max(0, capacity - energy));
        lastGeneratedFePerTick = generated;
        if (generated > 0) {
            energy += generated;
            setChanged();
        }
        if (energy > 0) {
            pushToNeighbors();
        }
        syncReadout(lastGeneratedFePerTick * 31 + lastOutflowFePerTick * 7 + energy / 64);
    }

    private void pushToNeighbors() {
        for (Direction direction : Direction.values()) {
            if (direction == Direction.DOWN || energy <= 0) {
                continue;
            }
            final int budget = Math.min(outflowBudget(), energy);
            if (budget <= 0) {
                return;
            }
            final IEnergyStorage target = level.getCapability(Capabilities.EnergyStorage.BLOCK,
                    worldPosition.relative(direction), direction.getOpposite());
            if (target == null || !target.canReceive()) {
                continue;
            }
            final int moved = target.receiveEnergy(budget, false);
            if (moved > 0) {
                energy -= moved;
                recordOutflow(moved);
                setChanged();
            }
        }
    }

    @Override
    public float calculateStressApplied() {
        final float impact = (float) tier().stressPerRpm();
        this.lastStressApplied = impact;
        return impact;
    }

    private int lastSyncedReadout = Integer.MIN_VALUE;
    private int ticksSinceReadoutSync;

    private void syncReadout(int shown) {
        ticksSinceReadoutSync++;
        final int bucket = Math.max(1, Math.abs(shown) / 32);
        if (Math.abs(shown - lastSyncedReadout) < bucket && ticksSinceReadoutSync < 20) {
            return;
        }
        lastSyncedReadout = shown;
        ticksSinceReadoutSync = 0;
        sendData();
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putInt("Energy", energy);
        tag.putInt("Generated", lastGeneratedFePerTick);
        tag.putInt("Outflow", lastOutflowFePerTick);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        energy = Math.max(0, tag.getInt("Energy"));
        lastGeneratedFePerTick = Math.max(0, tag.getInt("Generated"));
        lastOutflowFePerTick = Math.max(0, tag.getInt("Outflow"));
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        super.addToGoggleTooltip(tooltip, isPlayerSneaking);
        tooltip.add(com.createstats.power.FeGoggle.title("Kinetic Dynamo"));
        final int potential = potentialFePerTick();
        tooltip.add(com.createstats.power.FeGoggle.output(lastGeneratedFePerTick));
        if (potential > 0 && lastGeneratedFePerTick < potential) {
            tooltip.add(com.createstats.power.FeGoggle.dim(String.format(Locale.ROOT,
                    "Capable of %,d FE/t, grid not drawing the rest", potential)));
        }
        tooltip.add(com.createstats.power.FeGoggle.amount("Buffer", energy, bufferCapacity()));
        return true;
    }

    private static int bufferCapacity() {
        try {
            return ModConfiguration.COMMON.dynamoBufferCapacity.get();
        } catch (RuntimeException ignored) {
            return 16000;
        }
    }

    private static int transferRate() {
        try {
            return ModConfiguration.COMMON.dynamoTransferRate.get();
        } catch (RuntimeException ignored) {
            return 1024;
        }
    }

    private double fePerRpm() {
        return tier().fePerRpm();
    }

    public com.createstats.power.DynamoTier tier() {
        return getBlockState().getBlock() instanceof com.createstats.block.KineticDynamoBlock dynamo
                ? dynamo.tier()
                : com.createstats.power.DynamoTier.STANDARD;
    }
}
