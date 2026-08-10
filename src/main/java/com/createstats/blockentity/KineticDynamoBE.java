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

/** [1] */
public class KineticDynamoBE extends KineticBlockEntity {

    private int energy;
    private int lastGeneratedFePerTick;

    /** Extract-only view: the dynamo is a source, nothing can force-feed it. */
    private final IEnergyStorage energyView = new IEnergyStorage() {
        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            return 0;
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            final int amount = Math.min(Math.min(maxExtract, transferRate()), energy);
            if (amount <= 0) {
                return 0;
            }
            if (!simulate) {
                energy -= amount;
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

    /** [2] */
    public int potentialFePerTick() {
        final float speed = Math.abs(getSpeed());
        return speed <= 0.01f ? 0 : Math.max(0, (int) Math.floor(speed * fePerRpm()));
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
        final float speed = Math.abs(getSpeed());
        if (speed > 0.01f) {
            final int generated = Math.max(0, (int) Math.floor(speed * fePerRpm()));
            lastGeneratedFePerTick = generated;
            if (generated > 0 && energy < capacity) {
                energy = Math.min(capacity, energy + generated);
                setChanged();
            }
        } else {
            lastGeneratedFePerTick = 0;
        }
        if (energy > 0) {
            pushToNeighbors();
        }
        syncReadout(lastGeneratedFePerTick * 31 + energy / 64);
    }

    /** Push FE to every non-shaft neighbor that can receive (consumers, connectors, batteries). */
    private void pushToNeighbors() {
        final int rate = transferRate();
        for (Direction direction : Direction.values()) {
            if (direction == Direction.DOWN || energy <= 0) {
                continue; // DOWN is the shaft face.
            }
            final IEnergyStorage target = level.getCapability(Capabilities.EnergyStorage.BLOCK,
                    worldPosition.relative(direction), direction.getOpposite());
            if (target == null || !target.canReceive()) {
                continue;
            }
            final int moved = target.receiveEnergy(Math.min(rate, energy), false);
            if (moved > 0) {
                energy -= moved;
                setChanged();
            }
        }
    }

    @Override
    public float calculateStressApplied() {
        return (float) tier().stressPerRpm();
    }


    /** Last readout actually sent, and how long ago, so a still block costs nothing. */
    private int lastSyncedReadout = Integer.MIN_VALUE;
    private int ticksSinceReadoutSync;

    /** [3] */
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
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        energy = Math.max(0, tag.getInt("Energy"));
        lastGeneratedFePerTick = Math.max(0, tag.getInt("Generated"));
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        super.addToGoggleTooltip(tooltip, isPlayerSneaking);
        tooltip.add(Component.literal("    ").append(
                Component.literal("Kinetic Dynamo").withStyle(ChatFormatting.GRAY)));
        tooltip.add(Component.literal("    ").append(Component
                .literal(String.format(Locale.ROOT, "Generating: %d FE/t", lastGeneratedFePerTick))
                .withStyle(lastGeneratedFePerTick > 0 ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY)));
        tooltip.add(Component.literal("    ").append(Component
                .literal(String.format(Locale.ROOT, "Buffer: %,d / %,d FE", energy, bufferCapacity()))
                .withStyle(ChatFormatting.AQUA)));
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

    /** [4] */
    private double fePerRpm() {
        return tier().fePerRpm();
    }

    /** This dynamo's grade, defaulting to the middle one if it somehow has no dynamo block. */
    public com.createstats.power.DynamoTier tier() {
        return getBlockState().getBlock() instanceof com.createstats.block.KineticDynamoBlock dynamo
                ? dynamo.tier()
                : com.createstats.power.DynamoTier.STANDARD;
    }
}
