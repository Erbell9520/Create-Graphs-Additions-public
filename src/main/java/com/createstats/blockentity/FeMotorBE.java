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
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
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

public class FeMotorBE extends GeneratingKineticBlockEntity implements FeGridMember, FeManagedLoad,
        FeWireTerminal, com.createstats.power.Gateable, com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation {
    private static final int BUFFER = 64000;

    private int energy;

    private int outputSpeed;

    private boolean running;

    private int ratedDraw;

    private int measuredDraw;

    private final FeManagedState managed = new FeManagedState();

    private final List<BlockPos> links = new ArrayList<>();
    private final Map<Long, WireTier> linkTiers = new HashMap<>();
    @Nullable
    private FeWireNetwork network;
    @Nullable
    private ScrollValueBehaviour rpmDial;

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

    private MotorTier tier() {
        return getBlockState().getBlock() instanceof FeMotorBlock motor ? motor.tier() : MotorTier.MK_I;
    }

    private Direction facing() {
        return getBlockState().hasProperty(FeMotorBlock.FACING)
                ? getBlockState().getValue(FeMotorBlock.FACING) : Direction.NORTH;
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);

        rpmDial = new ScrollValueBehaviour(
                Component.translatable("block.create_stats.fe_motor.rpm"), this,
                new com.createstats.block.FeMotorValueBoxTransform());
        rpmDial.between(0, tier().ratedRpm());
        rpmDial.withFormatter(v -> v + " RPM");
        rpmDial.withCallback(v -> onDialChanged());
        behaviours.add(rpmDial);
    }

    private int targetRpm() {
        return rpmDial == null ? 0 : Mth.clamp(rpmDial.getValue(), 0, tier().ratedRpm());
    }

    private void onDialChanged() {
        setChanged();
        if (level != null && !level.isClientSide()) {
            updateRunning();
        }
    }

    @Override
    public float getGeneratedSpeed() {
        return outputSpeed;
    }

    @Override
    public float calculateAddedStressCapacity() {
        final float coeff = running ? tier().stressCapacityCoeff() : 0f;
        this.lastCapacityProvided = coeff;
        return coeff;
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide()) {
            return;
        }

        if (network == null || network.isDirty()) {
            FeWireNetwork.buildFrom(this);
        }
        if (network != null) {
            network.solveOnce(level);
        }

        if (network == null || !network.isPduManaged(level.getGameTime())) {
            setManagedShed(false);
        }
        updateRunning();
        syncDrawIfDue();
    }

    private void updateRunning() {
        final boolean cut = level.hasNeighborSignal(worldPosition) || gated || managed.isSuppressed();
        final int wantRpm = cut ? 0 : targetRpm();
        final int cost = tier().feCostForRpm(wantRpm);

        final boolean ratedCut = level.hasNeighborSignal(worldPosition) || gated || managed.isForcedOff();
        ratedDraw = tier().feCostForRpm(ratedCut ? 0 : targetRpm());

        final boolean wantRun = wantRpm > 0 && energy >= cost;
        if (wantRun) {
            energy -= cost;
            measuredDraw = cost;
            setChanged();
        } else {
            measuredDraw = 0;
        }

        running = wantRun;
        applyOutputSpeed(wantRun ? wantRpm : 0);
    }

    private void applyOutputSpeed(int magnitude) {
        final int desired = magnitude == 0 ? 0 : Math.round(convertToDirection(magnitude, facing()));
        if (desired != outputSpeed) {
            outputSpeed = desired;
            updateGeneratedRotation();
        }
    }

    private int lastSyncedDraw = Integer.MIN_VALUE;
    private int ticksSinceDrawSync;

    private void syncDrawIfDue() {
        ticksSinceDrawSync++;
        final int shown = measuredDraw * 31 + ratedDraw;
        final int bucket = Math.max(1, Math.abs(shown) / 16);
        if (Math.abs(shown - lastSyncedDraw) < bucket && ticksSinceDrawSync < 20) {
            return;
        }
        lastSyncedDraw = shown;
        ticksSinceDrawSync = 0;
        sendData();
    }

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
    @javax.annotation.Nullable
    public FeWireNetwork network() {
        return this.network;
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

            if (level.getBlockEntity(link) instanceof com.createstats.power.FeStudPanel panel) {
                panel.forgetLinksTo(worldPosition);
            } else if (level.getBlockEntity(link) instanceof FeWireTerminal other && other != this) {
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

    private void syncLinks() {
        setChanged();
        invalidateNetwork();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
        }
    }

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

    public int tierMaxDrawFePerTick() {
        return tier().feCostPerTick();
    }

    @Override
    public int measuredDrawFePerTick() {
        return measuredDraw;
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        super.addToGoggleTooltip(tooltip, isPlayerSneaking);
        final int rpm = Math.abs(outputSpeed);
        tooltip.add(com.createstats.power.FeGoggle.title(
                Component.translatable(getBlockState().getBlock().getDescriptionId())));
        final int needs = ratedDrawFePerTick();
        final boolean off = needs <= 0;

        if (managed.isSuppressed()) {
            tooltip.add(com.createstats.power.FeGoggle.warn("Shed at the panel"));
        } else {
            tooltip.add(com.createstats.power.FeGoggle.loadState(off, running, false));
        }
        tooltip.add(com.createstats.power.FeGoggle.dim(
                rpm > 0 ? String.format(Locale.ROOT, "Running: %d RPM", rpm) : "Idle"));
        final int dialed = targetRpm();
        if (dialed != rpm) {
            tooltip.add(com.createstats.power.FeGoggle.dim(
                    String.format(Locale.ROOT, "Dial: %d RPM", dialed)));
        }
        if (!off) {
            tooltip.add(com.createstats.power.FeGoggle.supplyDemand(measuredDraw, needs));
        }
        tooltip.add(com.createstats.power.FeGoggle.amount("Buffer", energy, bufferCapacity()));
        return true;
    }

    private static Component gogglePad(Component line) {
        return Component.literal("    ").append(line);
    }

    @Override
    public void setGated(boolean gated) {
        if (this.gated == gated) {
            return;
        }
        this.gated = gated;
        setChanged();
    }

    @Override
    public boolean isGated() {
        return gated;
    }

    private boolean gated;

    @Override
    public void writeSafe(CompoundTag tag, HolderLookup.Provider registries) {
        super.writeSafe(tag, registries);
        tag.putInt("Priority", FePriority.DEFAULT.ordinal());
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putInt("Energy", energy);
        tag.putBoolean("Running", running);
        tag.putBoolean("Gated", gated);
        tag.putInt("MDraw", measuredDraw);
        tag.putInt("RDraw", ratedDraw);
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
        gated = tag.getBoolean("Gated");
        measuredDraw = Math.max(0, tag.getInt("MDraw"));
        ratedDraw = Math.max(0, tag.getInt("RDraw"));
        outputSpeed = running ? Math.round(convertToDirection(targetRpm(), facing())) : 0;
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
