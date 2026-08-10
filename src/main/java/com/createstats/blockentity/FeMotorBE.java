package com.createstats.blockentity;

import com.createstats.block.FeMotorBlock;
import com.createstats.power.FeGridMember;
import com.createstats.power.FeLock;
import com.createstats.power.FeManagedLoad;
import com.createstats.power.FeManagedState;
import com.createstats.power.FePriority;
import com.createstats.power.FeWireNetwork;
import com.createstats.power.FeWireTerminal;
import com.createstats.power.MotorTier;
import com.createstats.power.WireTier;
import com.simibubi.create.content.kinetics.base.GeneratingKineticBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** [1] */
public class FeMotorBE extends GeneratingKineticBlockEntity implements FeGridMember, FeManagedLoad,
        FeWireTerminal, com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation {

    /** A running reserve, so a momentary grid dip does not stall the shaft. */
    private static final int BUFFER = 64000;

    private int energy;

    /** The signed speed this motor is sourcing, or zero. What {@link #getGeneratedSpeed()} returns. */
    private int outputSpeed;
    /** Whether the motor made its rotation this tick: it had the energy, and nothing cut it. */
    private boolean running;
    /** What the motor costs to hold at its rated speed, whether or not the buffer could pay. */
    private int ratedDraw;
    /** What the motor actually spent last tick, which is what the panel shows. */
    private int measuredDraw;

    private final FeManagedState managed = new FeManagedState();
    /** Wires strung to this motor's built-in connector, and each grade, keyed by the far position. */
    private final List<BlockPos> links = new ArrayList<>();
    private final Map<Long, WireTier> linkTiers = new HashMap<>();
    @Nullable
    private FeWireNetwork network;

    /** Receive only: a motor is a load and never a source of FE for anything else. */
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

    public FeMotorBE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    // grade

    private MotorTier tier() {
        return getBlockState().getBlock() instanceof FeMotorBlock motor ? motor.tier() : MotorTier.MK_I;
    }

    private Direction facing() {
        return getBlockState().hasProperty(FeMotorBlock.FACING)
                ? getBlockState().getValue(FeMotorBlock.FACING) : Direction.NORTH;
    }

    // kinetic source contract

    @Override
    public float getGeneratedSpeed() {
        return outputSpeed;
    }

    @Override
    public float calculateAddedStressCapacity() {
        // Create scales this by |generatedSpeed|, so coeff = capacity / rpm delivers the flat rated
        // capacity at the rated speed. Zero while stopped, so a dead motor claims no capacity.
        return running ? tier().stressCapacityCoeff() : 0f;
    }

    // tick

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide()) {
            return;   // the shaft spins from the synced network speed; nothing else to do client-side
        }
        // Wire membership, exactly like the pump: build the network from this node when it is missing
        // or stale, then let the shared solve fill the buffer behind energyHandle().
        if (network == null || network.isDirty()) {
            FeWireNetwork.buildFrom(this);
        }
        if (network != null) {
            network.solveOnce(level);
        }
        // [2]
        if (network == null || !network.isPduManaged(level.getGameTime())) {
            setManagedShed(false);
        }
        updateRunning();
    }

    /** [3] */
    private void updateRunning() {
        final boolean cut = level.hasNeighborSignal(worldPosition) || managed.isSuppressed();
        final int cost = tier().feCostPerTick();
        ratedDraw = cut ? 0 : cost;

        final boolean wantRun = !cut && energy >= cost;
        if (wantRun) {
            energy -= cost;
            measuredDraw = cost;
            setChanged();
        } else {
            measuredDraw = 0;
        }

        if (wantRun != running) {
            running = wantRun;
            applyOutputSpeed();
        }
    }

    /** Recompute the sourced speed for the current running state, and kick the network if it moved. */
    private void applyOutputSpeed() {
        final int desired = running
                ? Math.round(convertToDirection(tier().ratedRpm(), facing())) : 0;
        if (desired != outputSpeed) {
            outputSpeed = desired;
            updateGeneratedRotation();
        }
    }

    // grid membership

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

    /** [4] */
    @Override
    public int transferRate() {
        return tier().feCostPerTick() * 2;
    }

    @Override
    public IEnergyStorage attachedStorage() {
        return energyView;
    }

    @Override
    public boolean isGone(Level owner) {
        return isRemoved() || getLevel() != owner;
    }

    @Override
    public void setLastFlow(int flow) {
    }

    public void adoptNetwork(FeWireNetwork adopted) {
        this.network = adopted;
    }

    @Override
    public void invalidateNetwork() {
        if (network != null) {
            network.markDirty();
            network = null;
        }
    }

    public IEnergyStorage energyHandle() {
        return energyView;
    }

    // built-in connector: a wire terminal, exactly like a Power Connector

    @Override
    public BlockPos terminalPos() {
        return worldPosition;
    }

    @Override
    public List<BlockPos> links() {
        return links;
    }

    @Override
    public WireTier tierOf(BlockPos link) {
        return linkTiers.getOrDefault(link.asLong(), WireTier.COPPER);
    }

    @Override
    public void addLink(BlockPos far, WireTier tier) {
        final BlockPos at = far.immutable();
        links.add(at);
        linkTiers.put(at.asLong(), tier == null ? WireTier.COPPER : tier);
        syncLinks();
    }

    @Override
    public void removeDeadLink(BlockPos far) {
        linkTiers.remove(far.asLong());
        if (links.remove(far)) {
            syncLinks();
        }
    }

    @Override
    public void dropWiresAndUnlinkAll() {
        if (level == null || level.isClientSide()) {
            return;
        }
        final Map<WireTier, Integer> refund = new EnumMap<>(WireTier.class);
        for (BlockPos link : new ArrayList<>(links)) {
            refund.merge(tierOf(link), 1, Integer::sum);
            if (level.getBlockEntity(link) instanceof FeWireTerminal other && other != this) {
                other.removeDeadLink(worldPosition);
            }
        }
        links.clear();
        linkTiers.clear();
        syncLinks();
        for (Map.Entry<WireTier, Integer> e : refund.entrySet()) {
            net.minecraft.world.level.block.Block.popResource(level, worldPosition,
                    new net.minecraft.world.item.ItemStack(
                            com.createstats.registry.ModItems.wireItem(e.getKey()), e.getValue()));
        }
    }

    /** Rebuild the network and push the new link list to the wire renderer on any wiring change. */
    private void syncLinks() {
        setChanged();
        invalidateNetwork();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
        }
    }

    // managed load

    @Override
    public FePriority managedPriority() {
        return managed.priority();
    }

    @Override
    public void setManagedPriority(FePriority tier) {
        managed.setPriority(tier);
        setChanged();
    }

    @Override
    public FeLock managedLock() {
        return managed.lock();
    }

    @Override
    public void setManagedLock(FeLock lock) {
        managed.setLock(lock);
        setChanged();
    }

    @Override
    public void setManagedShed(boolean shed) {
        if (managed.isShed() == shed) {
            return;
        }
        managed.setShed(shed);
        setChanged();
    }

    @Override
    public boolean isManagedShed() {
        return managed.isShed();
    }

    @Override
    public int ratedDrawFePerTick() {
        return ratedDraw;
    }

    @Override
    public int measuredDrawFePerTick() {
        return measuredDraw;
    }

    // goggle overlay

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        super.addToGoggleTooltip(tooltip, isPlayerSneaking);
        final int rpm = Math.abs(outputSpeed);
        tooltip.add(gogglePad(Component.translatable(getBlockState().getBlock().getDescriptionId())
                .withStyle(ChatFormatting.GRAY)));
        tooltip.add(gogglePad(Component.literal(
                rpm > 0 ? String.format(Locale.ROOT, "Running: %d RPM", rpm) : "Idle")
                .withStyle(rpm > 0 ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY)));
        if (measuredDraw > 0) {
            tooltip.add(gogglePad(Component.literal(String.format(Locale.ROOT, "Drawing: %,d FE/t", measuredDraw))
                    .withStyle(ChatFormatting.AQUA)));
        }
        tooltip.add(gogglePad(Component.literal(String.format(Locale.ROOT, "Buffer: %,d / %,d FE", energy, BUFFER))
                .withStyle(ChatFormatting.AQUA)));
        return true;
    }

    private static Component gogglePad(Component line) {
        return Component.literal("    ").append(line);
    }

    // save

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putInt("Energy", energy);
        tag.putBoolean("Running", running);
        tag.putInt("Priority", managed.priorityOrdinal());
        tag.putInt("Lock", managed.lockOrdinal());
        final long[] linkArr = new long[links.size()];
        final byte[] tierArr = new byte[links.size()];
        for (int i = 0; i < links.size(); i++) {
            linkArr[i] = links.get(i).asLong();
            tierArr[i] = (byte) tierOf(links.get(i)).ordinal();
        }
        tag.putLongArray("Links", linkArr);
        tag.putByteArray("LinkTiers", tierArr);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        energy = Mth.clamp(tag.getInt("Energy"), 0, BUFFER);
        running = tag.getBoolean("Running");
        outputSpeed = running ? Math.round(convertToDirection(tier().ratedRpm(), facing())) : 0;
        managed.load(tag.getInt("Priority"), tag.getInt("Lock"));
        links.clear();
        linkTiers.clear();
        final long[] packed = tag.getLongArray("Links");
        final byte[] tiers = tag.getByteArray("LinkTiers");
        for (int i = 0; i < packed.length; i++) {
            final BlockPos p = BlockPos.of(packed[i]);
            links.add(p);
            if (i < tiers.length) {
                linkTiers.put(packed[i], WireTier.byOrdinal(tiers[i]));
            }
        }
    }
}
