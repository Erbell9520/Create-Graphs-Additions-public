package com.createstats.blockentity;

import com.createstats.ModConfiguration;
import com.createstats.block.CapacitorBankBlock;
import com.createstats.power.FeBankMerge;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** [1] */
public class CapacitorBankBE extends BlockEntity implements com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation {

    /** Number of LED segments on the block face, and the top of the blockstate level property. */
    public static final int LED_SEGMENTS = 8;

    private int energy;
    /** Minimum ticks between charge syncs, so a fast-charging bank cannot flood the network. */
    private static final int SYNC_INTERVAL_TICKS = 4;

    /** This block's share of its bank's capacity, mirrored to the client so the face reads right. */
    private int syncedCapacity = 1024;
    private int lastSyncedEnergy = -1;
    private int ticksSinceSync = SYNC_INTERVAL_TICKS;
    /** Smoothed FE per tick across the whole bank. Positive charging, negative discharging. */
    private int chargeRate;
    private int lastGroupEnergy = -1;
    private int lastSyncedRate = Integer.MIN_VALUE;
    /** Last LED level written to the blockstate, for the anti-strobe deadband in {@link #tick()}. */
    private int lastLedLevel = -1;
    /** Shared with every member of the pool; rebuilt lazily when stale. */
    @Nullable
    private BankGroup group;

    /** [2] */
    private static final class BankGroup {
        private final List<CapacitorBankBE> members;
        private long builtTick;

        BankGroup(List<CapacitorBankBE> members, long builtTick) {
            this.members = members;
            this.builtTick = builtTick;
        }
    }

    private final IEnergyStorage energyView = new IEnergyStorage() {
        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            return distribute(Math.min(maxReceive, transferRate()), simulate);
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            return drain(Math.min(maxExtract, transferRate()), simulate);
        }

        @Override
        public int getEnergyStored() {
            return groupEnergy();
        }

        @Override
        public int getMaxEnergyStored() {
            return groupCapacity();
        }

        @Override
        public boolean canExtract() {
            return true;
        }

        @Override
        public boolean canReceive() {
            return true;
        }
    };

    public CapacitorBankBE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public IEnergyStorage energyHandle() {
        return energyView;
    }

    /** This block's own charge, which is what drives its LEDs. */
    public int storedEnergy() {
        return energy;
    }

    public int groupEnergy() {
        int total = 0;
        for (CapacitorBankBE bank : group()) {
            total += bank.energy;
        }
        return total;
    }

    /** [3] */
    public int groupCapacity() {
        if (level != null && level.isClientSide()) {
            int total = 0;
            for (CapacitorBankBE bank : group()) {
                total += bank.syncedCapacity;
            }
            return total;
        }
        return computedGroupCapacity();
    }

    private int computedGroupCapacity() {
        return FeBankMerge.capacity(group().size(), blockCapacity(), mergeExponent());
    }

    /** This block's share of the bank's capacity, which is what its own charge is measured against. */
    public int ownCapacity() {
        if (level != null && level.isClientSide()) {
            return syncedCapacity;
        }
        final int[] shares = FeBankMerge.shareCapacity(computedGroupCapacity(), group().size());
        final int index = indexInGroup();
        return index < shares.length ? shares[index] : blockCapacity();
    }

    public int groupSize() {
        return group().size();
    }

    // pooling

    private List<CapacitorBankBE> group() {
        final long now = level != null ? level.getGameTime() : 0L;
        if (group != null && group.builtTick == now && !group.members.isEmpty()) {
            return group.members;
        }
        return rebuildGroup(now);
    }

    /** [4] */
    private List<CapacitorBankBE> rebuildGroup(long now) {
        final List<CapacitorBankBE> found = new ArrayList<>();
        if (level == null) {
            found.add(this);
            group = new BankGroup(found, now);
            return found;
        }

        // 1. The entire connected cluster, which every member sees identically.
        final Map<BlockPos, CapacitorBankBE> cluster = new HashMap<>();
        final Deque<CapacitorBankBE> queue = new ArrayDeque<>();
        queue.add(this);
        cluster.put(worldPosition.immutable(), this);
        while (!queue.isEmpty()) {
            final CapacitorBankBE bank = queue.poll();
            for (Direction direction : Direction.values()) {
                final BlockPos neighbour = bank.getBlockPos().relative(direction).immutable();
                if (!cluster.containsKey(neighbour) && level.isLoaded(neighbour)
                        && level.getBlockEntity(neighbour) instanceof CapacitorBankBE other
                        && !other.isRemoved()) {
                    cluster.put(neighbour, other);
                    queue.add(other);
                }
            }
        }

        // 2. Split it into banks bounded by the configured footprint and height.
        final List<List<BlockPos>> banks = FeBankMerge.partition(
                new ArrayList<>(cluster.keySet()), maxFootprint(), maxHeight());

        // 3. Hand every bank to its own members, and keep the one containing this block.
        List<CapacitorBankBE> mine = null;
        for (List<BlockPos> bank : banks) {
            final List<CapacitorBankBE> members = new ArrayList<>(bank.size());
            for (BlockPos pos : bank) {
                final CapacitorBankBE member = cluster.get(pos);
                if (member != null) {
                    members.add(member);
                }
            }
            if (members.isEmpty()) {
                continue;
            }
            final BankGroup shared = new BankGroup(members, now);
            for (CapacitorBankBE member : members) {
                member.group = shared;
            }
            if (bank.contains(worldPosition)) {
                mine = members;
            }
        }
        if (mine == null) {
            mine = new ArrayList<>();
            mine.add(this);
            group = new BankGroup(mine, now);
        }
        return mine;
    }

    /** [5] */
    public boolean isRenderController() {
        final List<CapacitorBankBE> members = group();
        return !members.isEmpty() && members.get(0) == this;
    }

    /** Lowest corner of the merged structure, in world space. */
    public BlockPos structureMin() {
        BlockPos min = worldPosition;
        for (CapacitorBankBE bank : group()) {
            final BlockPos pos = bank.getBlockPos();
            min = new BlockPos(Math.min(min.getX(), pos.getX()),
                    Math.min(min.getY(), pos.getY()), Math.min(min.getZ(), pos.getZ()));
        }
        return min;
    }

    /** Highest corner of the merged structure, in world space (inclusive). */
    public BlockPos structureMax() {
        BlockPos max = worldPosition;
        for (CapacitorBankBE bank : group()) {
            final BlockPos pos = bank.getBlockPos();
            max = new BlockPos(Math.max(max.getX(), pos.getX()),
                    Math.max(max.getY(), pos.getY()), Math.max(max.getZ(), pos.getZ()));
        }
        return max;
    }

    /** [6] */
    public boolean isSolidBox() {
        final BlockPos min = structureMin();
        final BlockPos max = structureMax();
        final int volume = (max.getX() - min.getX() + 1)
                * (max.getY() - min.getY() + 1)
                * (max.getZ() - min.getZ() + 1);
        return volume == group().size();
    }

    /** Charge of the whole merged bank, 0..1. What the face gauge and its colour are driven from. */
    public float groupFillFraction() {
        final int capacity = groupCapacity();
        return capacity <= 0 ? 0.0f : Math.max(0.0f, Math.min(1.0f, groupEnergy() / (float) capacity));
    }

    /** This block's index within its bank, bottom up. Drives which share of capacity it holds. */
    private int indexInGroup() {
        final List<CapacitorBankBE> members = group();
        for (int i = 0; i < members.size(); i++) {
            if (members.get(i) == this) {
                return i;
            }
        }
        return 0;
    }

    /** [7] */
    public com.createstats.block.CapacitorBankBlock.Segment segment() {
        if (level == null) {
            return com.createstats.block.CapacitorBankBlock.Segment.SINGLE;
        }
        final List<CapacitorBankBE> members = group();
        boolean above = false;
        boolean below = false;
        for (CapacitorBankBE member : members) {
            final BlockPos pos = member.getBlockPos();
            if (pos.getX() != worldPosition.getX() || pos.getZ() != worldPosition.getZ()) {
                continue;
            }
            if (pos.getY() == worldPosition.getY() + 1) {
                above = true;
            } else if (pos.getY() == worldPosition.getY() - 1) {
                below = true;
            }
        }
        if (above && below) {
            return com.createstats.block.CapacitorBankBlock.Segment.MIDDLE;
        }
        if (above) {
            return com.createstats.block.CapacitorBankBlock.Segment.BOTTOM;
        }
        if (below) {
            return com.createstats.block.CapacitorBankBlock.Segment.TOP;
        }
        return com.createstats.block.CapacitorBankBlock.Segment.SINGLE;
    }

    /** Fills the pool from the bottom block up. Returns FE actually stored. */
    private int distribute(int amount, boolean simulate) {
        final List<CapacitorBankBE> members = group();
        final int[] energies = readEnergies(members);
        final int[] capacities = FeBankMerge.shareCapacity(
                FeBankMerge.capacity(members.size(), blockCapacity(), mergeExponent()), members.size());
        final int placed = com.createstats.power.FeStackFill.fillBottomUp(energies, capacities, amount);
        if (!simulate && placed > 0) {
            writeEnergies(members, energies);
        }
        return placed;
    }

    /** Empties the pool from the top block down. Returns FE actually removed. */
    private int drain(int amount, boolean simulate) {
        final List<CapacitorBankBE> members = group();
        final int[] energies = readEnergies(members);
        final int taken = com.createstats.power.FeStackFill.drainTopDown(energies, amount);
        if (!simulate && taken > 0) {
            writeEnergies(members, energies);
        }
        return taken;
    }

    private static int[] readEnergies(List<CapacitorBankBE> members) {
        final int[] energies = new int[members.size()];
        for (int i = 0; i < energies.length; i++) {
            energies[i] = members.get(i).energy;
        }
        return energies;
    }

    private static void writeEnergies(List<CapacitorBankBE> members, int[] energies) {
        for (int i = 0; i < energies.length; i++) {
            final CapacitorBankBE bank = members.get(i);
            if (bank.energy != energies[i]) {
                bank.energy = energies[i];
                bank.setChanged();
            }
        }
    }

    // tick: drive the LEDs

    /** [8] */
    /** [9] */
    public void tick() {
        if (level == null || level.isClientSide()) {
            return;
        }
        final int capacity = ownCapacity();
        if (energy > capacity) {
            energy = capacity;
            setChanged();
        }
        // [10]
        if (isRenderController()) {
            final int nowEnergy = groupEnergy();
            if (lastGroupEnergy >= 0) {
                // Light smoothing: the raw per-tick delta is spiky because the grid solve delivers
                // in bursts, and a readout that flickers is worse than one that lags slightly.
                chargeRate = Math.round(chargeRate * 0.75f + (nowEnergy - lastGroupEnergy) * 0.25f);
            }
            lastGroupEnergy = nowEnergy;
        }

        // [11]
        final int bucket = Math.max(1, capacity / 32);
        final boolean chargeMoved = Math.abs(energy - lastSyncedEnergy) >= bucket
                || (energy == 0) != (lastSyncedEnergy == 0)
                || (energy >= capacity) != (lastSyncedEnergy >= capacity)
                // The face shows a live FE/t, so a bank sitting at a steady charge still has to
                // push updates when the throughput through it changes.
                || (isRenderController() && chargeRate != lastSyncedRate);
        if (ticksSinceSync < SYNC_INTERVAL_TICKS) {
            ticksSinceSync++;
        }
        if (syncedCapacity != capacity || (chargeMoved && ticksSinceSync >= SYNC_INTERVAL_TICKS)) {
            syncedCapacity = capacity;
            lastSyncedEnergy = energy;
            lastSyncedRate = chargeRate;
            ticksSinceSync = 0;
            setChanged();
            // Flag 2: clients only. Neighbours have no reason to re-evaluate redstone because a
            // battery ticked up.
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
        }
        final BlockState state = getBlockState();
        if (!state.hasProperty(CapacitorBankBlock.LEVEL) || !state.hasProperty(CapacitorBankBlock.SEGMENT)) {
            return;
        }
        // [12]
        final float scaled = groupFillFraction() * LED_SEGMENTS;
        int lit = Mth.clamp(Math.round(scaled), 0, LED_SEGMENTS);
        if (lastLedLevel >= 0 && Math.abs(scaled - lastLedLevel) < 0.65f) {
            lit = lastLedLevel;
        }
        lastLedLevel = lit;
        final CapacitorBankBlock.Segment segment = segment();
        if (state.getValue(CapacitorBankBlock.LEVEL) != lit || state.getValue(CapacitorBankBlock.SEGMENT) != segment) {
            level.setBlock(worldPosition, state
                    .setValue(CapacitorBankBlock.LEVEL, lit)
                    .setValue(CapacitorBankBlock.SEGMENT, segment), 3);
        }
        syncReadout(energy / 64);
    }

    /** LED segments lit for this block, 0 to 8. Any charge at all lights at least one. */
    public int ledLevel() {
        final int capacity = ownCapacity();
        if (energy <= 0 || capacity <= 0) {
            return 0;
        }
        final int scaled = (int) ((long) energy * LED_SEGMENTS / capacity);
        return Math.max(1, Math.min(LED_SEGMENTS, scaled));
    }


    /** Last readout actually sent, and how long ago, so a still block costs nothing. */
    private int lastSyncedReadout = Integer.MIN_VALUE;
    private int ticksSinceReadoutSync;

    /** [13] */
    private void syncReadout(int shown) {
        ticksSinceReadoutSync++;
        final int bucket = Math.max(1, Math.abs(shown) / 32);
        if (Math.abs(shown - lastSyncedReadout) < bucket && ticksSinceReadoutSync < 20) {
            return;
        }
        lastSyncedReadout = shown;
        ticksSinceReadoutSync = 0;
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(),
                    net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
        }
    }

    // persistence

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Energy", energy);
        tag.putInt("Cap", syncedCapacity);
        tag.putInt("Rate", chargeRate);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        energy = Math.max(0, tag.getInt("Energy"));
        if (tag.contains("Cap")) {
            syncedCapacity = Math.max(1, tag.getInt("Cap"));
        }
        chargeRate = tag.getInt("Rate");
    }

    /** [14] */
    public int chargeRatePerTick() {
        final List<CapacitorBankBE> members = group();
        return members.isEmpty() ? chargeRate : members.get(0).chargeRate;
    }

    /**
     * Ticks until the bank is full (while charging) or empty (while discharging), or -1 when it is
     * holding steady and neither answer means anything.
     */
    public int enduranceTicks() {
        final int rate = chargeRatePerTick();
        if (rate > 0) {
            final int headroom = groupCapacity() - groupEnergy();
            return headroom <= 0 ? -1 : headroom / rate;
        }
        if (rate < 0) {
            final int stored = groupEnergy();
            return stored <= 0 ? -1 : stored / -rate;
        }
        return -1;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        final CompoundTag tag = super.getUpdateTag(registries);
        saveAdditional(tag, registries);
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        loadAdditional(tag, registries);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        // Force the rest of the pool to re-flood rather than keep a reference to a dead block.
        group = null;
    }

    /**
     * The controller draws the whole structure's face, so its render box has to cover every member
     * or the readout vanishes as soon as the controller block itself leaves the frustum.
     */
    public net.minecraft.world.phys.AABB getRenderBoundingBox() {
        final BlockPos min = structureMin();
        final BlockPos max = structureMax();
        return new net.minecraft.world.phys.AABB(
                min.getX(), min.getY(), min.getZ(),
                max.getX() + 1.0, max.getY() + 1.0, max.getZ() + 1.0);
    }

    /** [15] */
    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        final int stored = groupEnergy();
        final int capacity = groupCapacity();
        final int stacked = groupSize();
        final float pct = capacity > 0 ? stored * 100.0f / capacity : 0.0f;

        tooltip.add(Component.literal("    ").append(
                Component.literal("Capacitor Bank").withStyle(ChatFormatting.GRAY)));
        tooltip.add(Component.literal("    ").append(Component
                .literal(String.format(Locale.ROOT, "Charge: %,d / %,d FE (%.0f%%)", stored, capacity, pct))
                .withStyle(pct < 20.0f ? ChatFormatting.RED : pct < 60.0f ? ChatFormatting.YELLOW : ChatFormatting.GREEN)));
        if (stacked > 1) {
            tooltip.add(Component.literal("    ").append(Component
                    .literal(String.format(Locale.ROOT, "Pooled across %d blocks, fills bottom up", stacked))
                    .withStyle(ChatFormatting.DARK_AQUA)));
        }
        return true;
    }

    // config

    public static int blockCapacity() {
        try {
            return ModConfiguration.COMMON.capacitorBankCapacity.get();
        } catch (RuntimeException ignored) {
            return 1024000;
        }
    }

    public static double mergeExponent() {
        try {
            return ModConfiguration.COMMON.capacitorBankExponent.get();
        } catch (RuntimeException ignored) {
            return 1.1;
        }
    }

    public static int maxFootprint() {
        try {
            return ModConfiguration.COMMON.capacitorBankMaxFootprint.get();
        } catch (RuntimeException ignored) {
            return 3;
        }
    }

    public static int maxHeight() {
        try {
            return ModConfiguration.COMMON.capacitorBankMaxHeight.get();
        } catch (RuntimeException ignored) {
            return 6;
        }
    }

    public static int transferRate() {
        try {
            return ModConfiguration.COMMON.capacitorBankTransferRate.get();
        } catch (RuntimeException ignored) {
            return 8192;
        }
    }
}
