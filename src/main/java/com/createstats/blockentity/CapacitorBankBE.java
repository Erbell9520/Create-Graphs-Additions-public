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

public class CapacitorBankBE extends BlockEntity implements com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation {
    public static final int LED_SEGMENTS = 8;

    private int energy;

    private static final int SYNC_INTERVAL_TICKS = 4;

    private int syncedCapacity = 1024;
    private int lastSyncedEnergy = -1;
    private int ticksSinceSync = SYNC_INTERVAL_TICKS;

    private int chargeIn;
    private int chargeOut;

    private float chargeInFiltered;
    private float chargeOutFiltered;

    private int inAccumulator;
    private int outAccumulator;

    private int lastSyncedIn = -1;
    private int lastSyncedOut = -1;

    private int lastLedLevel = -1;

    private int reserveRank = com.createstats.power.FeReserve.DEFAULT_RANK;

    @Nullable
    private BankGroup group;

    private static final class BankGroup {
        private final List<CapacitorBankBE> members;
        private long builtTick;

        BankGroup(List<CapacitorBankBE> members, long builtTick) {
            this.members = members;
            this.builtTick = builtTick;
        }
    }

    private final class BankEnergyView implements IEnergyStorage, com.createstats.power.FeReserve {
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

        @Override
        public int reserveRank() {
            return groupReserveRank();
        }

        @Override
        public long reservePoolKey() {
            final List<CapacitorBankBE> members = group();
            return members.isEmpty() ? worldPosition.asLong() : members.get(0).getBlockPos().asLong();
        }
    }

    private final IEnergyStorage energyView = new BankEnergyView();

    public CapacitorBankBE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public IEnergyStorage energyHandle() {
        return energyView;
    }

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

    public int groupReserveRank() {
        int lowest = com.createstats.power.FeReserve.MAX_RANK;
        for (CapacitorBankBE bank : group()) {
            lowest = Math.min(lowest, com.createstats.power.FeReserve.clamp(bank.reserveRank));
        }
        return lowest;
    }

    public int ownReserveRank() {
        return com.createstats.power.FeReserve.clamp(reserveRank);
    }

    public void setReserveRank(int rank) {
        final int clamped = com.createstats.power.FeReserve.clamp(rank);
        for (CapacitorBankBE bank : group()) {
            if (bank.reserveRank != clamped) {
                bank.reserveRank = clamped;
                bank.setChanged();
                if (level != null && !level.isClientSide()) {
                    level.sendBlockUpdated(bank.getBlockPos(), bank.getBlockState(), bank.getBlockState(), 2);
                }
            }
        }
    }

    // wraps like FePriority#next, one control reaches every value
    public int cycleReserveRank() {
        final int next = groupReserveRank() >= com.createstats.power.FeReserve.MAX_RANK
                ? com.createstats.power.FeReserve.MIN_RANK : groupReserveRank() + 1;
        setReserveRank(next);
        return next;
    }

    public int cycleReserveRankBack() {
        final int next = groupReserveRank() <= com.createstats.power.FeReserve.MIN_RANK
                ? com.createstats.power.FeReserve.MAX_RANK : groupReserveRank() - 1;
        setReserveRank(next);
        return next;
    }

    private List<CapacitorBankBE> group() {
        final long now = level != null ? level.getGameTime() : 0L;
        if (group != null && group.builtTick == now && !group.members.isEmpty()) {
            return group.members;
        }
        return rebuildGroup(now);
    }

    private List<CapacitorBankBE> rebuildGroup(long now) {
        final List<CapacitorBankBE> found = new ArrayList<>();
        if (level == null) {
            found.add(this);
            group = new BankGroup(found, now);
            return found;
        }

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

        final List<List<BlockPos>> banks = FeBankMerge.partition(
                new ArrayList<>(cluster.keySet()), maxFootprint(), maxHeight());

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

    // only the controller draws the face, the gauge spans the whole build
    public boolean isRenderController() {
        final List<CapacitorBankBE> members = group();
        return !members.isEmpty() && members.get(0) == this;
    }

    public BlockPos structureMin() {
        BlockPos min = worldPosition;
        for (CapacitorBankBE bank : group()) {
            final BlockPos pos = bank.getBlockPos();
            min = new BlockPos(Math.min(min.getX(), pos.getX()),
                    Math.min(min.getY(), pos.getY()), Math.min(min.getZ(), pos.getZ()));
        }
        return min;
    }

    public BlockPos structureMax() {
        BlockPos max = worldPosition;
        for (CapacitorBankBE bank : group()) {
            final BlockPos pos = bank.getBlockPos();
            max = new BlockPos(Math.max(max.getX(), pos.getX()),
                    Math.max(max.getY(), pos.getY()), Math.max(max.getZ(), pos.getZ()));
        }
        return max;
    }

    public boolean isSolidBox() {
        final BlockPos min = structureMin();
        final BlockPos max = structureMax();
        final int volume = (max.getX() - min.getX() + 1)
                * (max.getY() - min.getY() + 1)
                * (max.getZ() - min.getZ() + 1);
        return volume == group().size();
    }

    public float groupFillFraction() {
        final int capacity = groupCapacity();
        return capacity <= 0 ? 0.0f : Math.max(0.0f, Math.min(1.0f, groupEnergy() / (float) capacity));
    }

    private int indexInGroup() {
        final List<CapacitorBankBE> members = group();
        for (int i = 0; i < members.size(); i++) {
            if (members.get(i) == this) {
                return i;
            }
        }
        return 0;
    }

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

    private int distribute(int amount, boolean simulate) {
        final List<CapacitorBankBE> members = group();
        final int[] energies = readEnergies(members);
        final int[] capacities = FeBankMerge.shareCapacity(
                FeBankMerge.capacity(members.size(), blockCapacity(), mergeExponent()), members.size());
        final int placed = com.createstats.power.FeStackFill.fillBottomUp(energies, capacities, amount);
        if (!simulate && placed > 0) {
            writeEnergies(members, energies);
            this.inAccumulator += placed;
        }
        return placed;
    }

    private int drain(int amount, boolean simulate) {
        final List<CapacitorBankBE> members = group();
        final int[] energies = readEnergies(members);
        final int taken = com.createstats.power.FeStackFill.drainTopDown(energies, amount);
        if (!simulate && taken > 0) {
            writeEnergies(members, energies);
            this.outAccumulator += taken;
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

    public void tick() {
        if (level == null || level.isClientSide()) {
            return;
        }
        final int capacity = ownCapacity();
        if (energy > capacity) {
            energy = capacity;
            setChanged();
        }

        if (isRenderController()) {
            int rawIn = 0;
            int rawOut = 0;
            for (CapacitorBankBE bank : group()) {
                rawIn += bank.inAccumulator;
                rawOut += bank.outAccumulator;
                bank.inAccumulator = 0;
                bank.outAccumulator = 0;
            }

            chargeInFiltered = chargeInFiltered * 0.75f + rawIn * 0.25f;
            chargeOutFiltered = chargeOutFiltered * 0.75f + rawOut * 0.25f;
            chargeIn = roundUpAwayFromZero(chargeInFiltered);
            chargeOut = roundUpAwayFromZero(chargeOutFiltered);
        }

        final int bucket = Math.max(1, capacity / 32);
        final boolean chargeMoved = Math.abs(energy - lastSyncedEnergy) >= bucket
                || (energy == 0) != (lastSyncedEnergy == 0)
                || (energy >= capacity) != (lastSyncedEnergy >= capacity)

                || (isRenderController()
                        && (rateMoved(chargeIn, lastSyncedIn) || rateMoved(chargeOut, lastSyncedOut)));
        if (ticksSinceSync < SYNC_INTERVAL_TICKS) {
            ticksSinceSync++;
        }
        if (syncedCapacity != capacity || (chargeMoved && ticksSinceSync >= SYNC_INTERVAL_TICKS)) {
            syncedCapacity = capacity;
            lastSyncedEnergy = energy;
            lastSyncedIn = chargeIn;
            lastSyncedOut = chargeOut;
            ticksSinceSync = 0;
            setChanged();

            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
        }
        final BlockState state = getBlockState();
        if (!state.hasProperty(CapacitorBankBlock.LEVEL) || !state.hasProperty(CapacitorBankBlock.SEGMENT)) {
            return;
        }

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

    public int ledLevel() {
        final int capacity = ownCapacity();
        if (energy <= 0 || capacity <= 0) {
            return 0;
        }
        final int scaled = (int) ((long) energy * LED_SEGMENTS / capacity);
        return Math.max(1, Math.min(LED_SEGMENTS, scaled));
    }

    private int lastSyncedReadout = Integer.MIN_VALUE;
    private int ticksSinceReadoutSync;

    private int managedSettlePct = -1;
    private int managedFullTicks = -1;
    private long managedUntil = Long.MIN_VALUE;

    private boolean syncedManaged;
    private int syncedManagedSettle = -1;
    private int syncedManagedFull = -1;

    public void acceptManagerOutlook(int settlePct, int fullInTicks, long untilTick) {
        if (level == null || level.isClientSide()) {
            return;
        }
        final List<CapacitorBankBE> members = group();
        for (CapacitorBankBE bank : members.isEmpty() ? List.of(this) : members) {
            bank.managedSettlePct = settlePct;
            bank.managedFullTicks = fullInTicks;
            bank.managedUntil = untilTick;
        }
    }

    public boolean isManaged() {
        if (level != null && level.isClientSide()) {
            return syncedManaged;
        }
        return level != null && level.getGameTime() <= managedUntil;
    }

    public int managedSettlePct() {
        if (level != null && level.isClientSide()) {
            return syncedManagedSettle;
        }
        return isManaged() ? managedSettlePct : -1;
    }

    public int managedFullInTicks() {
        if (level != null && level.isClientSide()) {
            return syncedManagedFull;
        }
        return isManaged() ? managedFullTicks : -1;
    }

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

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Energy", energy);
        tag.putInt("Cap", syncedCapacity);
        tag.putInt("InRate", chargeIn);
        tag.putInt("OutRate", chargeOut);

        tag.putInt("Rate", chargeIn - chargeOut);
        tag.putInt("Rank", reserveRank);

        tag.putBoolean("Mgr", isManaged());
        tag.putInt("MgrSet", managedSettlePct());
        tag.putInt("MgrFull", managedFullInTicks());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        energy = Math.max(0, tag.getInt("Energy"));
        if (tag.contains("Cap")) {
            syncedCapacity = Math.max(1, tag.getInt("Cap"));
        }
        if (tag.contains("InRate") || tag.contains("OutRate")) {
            chargeIn = Math.max(0, tag.getInt("InRate"));
            chargeOut = Math.max(0, tag.getInt("OutRate"));
        } else {
            final int net = tag.getInt("Rate");
            chargeIn = Math.max(0, net);
            chargeOut = Math.max(0, -net);
        }
        chargeInFiltered = chargeIn;
        chargeOutFiltered = chargeOut;

        reserveRank = tag.contains("Rank")
                ? com.createstats.power.FeReserve.clamp(tag.getInt("Rank"))
                : com.createstats.power.FeReserve.DEFAULT_RANK;
        syncedManaged = tag.getBoolean("Mgr");
        syncedManagedSettle = tag.contains("MgrSet") ? tag.getInt("MgrSet") : -1;
        syncedManagedFull = tag.contains("MgrFull") ? tag.getInt("MgrFull") : -1;
    }

    public int chargeFePerTick() {
        final List<CapacitorBankBE> members = group();
        return members.isEmpty() ? chargeIn : members.get(0).chargeIn;
    }

    public int dischargeFePerTick() {
        final List<CapacitorBankBE> members = group();
        return members.isEmpty() ? chargeOut : members.get(0).chargeOut;
    }

    public int chargeRatePerTick() {
        return chargeFePerTick() - dischargeFePerTick();
    }

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

    public int runtimeTicks() {
        final int rate = chargeRatePerTick();
        if (rate >= 0) {
            return -1;
        }
        final int stored = groupEnergy();
        return stored <= 0 ? 0 : stored / -rate;
    }

    private static int roundUpAwayFromZero(float v) {
        if (v > 0.0f) {
            return (int) Math.ceil(v - 0.001f);
        }
        return v < 0.0f ? (int) Math.floor(v + 0.001f) : 0;
    }

    private static boolean rateMoved(int now, int synced) {
        if (now == synced) {
            return false;
        }

        if ((now == 0) != (synced == 0)) {
            return true;
        }
        return Math.abs(now - synced) >= Math.max(1, Math.abs(now) / 16);
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

        group = null;
    }

    public net.minecraft.world.phys.AABB getRenderBoundingBox() {
        final BlockPos min = structureMin();
        final BlockPos max = structureMax();
        return new net.minecraft.world.phys.AABB(
                min.getX(), min.getY(), min.getZ(),
                max.getX() + 1.0, max.getY() + 1.0, max.getZ() + 1.0);
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        final int stored = groupEnergy();
        final int capacity = groupCapacity();
        final int stacked = groupSize();

        final int in = chargeFePerTick();
        final int out = dischargeFePerTick();

        tooltip.add(com.createstats.power.FeGoggle.title("Capacitor Bank"));
        tooltip.add(com.createstats.power.FeGoggle.stored(stored, capacity));
        tooltip.add(com.createstats.power.FeGoggle.chargeFlow(in - out));

        if (in > 0 && out > 0) {
            tooltip.add(com.createstats.power.FeGoggle.dim(String.format(Locale.ROOT,
                    "In %,d FE/t, out %,d FE/t", in, out)));
        }
        if (isManaged()) {
            final int fullIn = managedFullInTicks();
            final int settle = managedSettlePct();
            tooltip.add(com.createstats.power.FeGoggle.dim(fullIn > 0
                    ? "Managed by a Battery Controller: full in ~" + com.createstats.power.FeGoggle.time(fullIn)
                    : settle >= 0 ? "Managed by a Battery Controller: settles near " + settle + "%"
                    : "Managed by a Battery Controller"));
        } else {
            final int endTicks = enduranceTicks();
            if (endTicks > 0) {
                tooltip.add(com.createstats.power.FeGoggle.dim((in > out ? "Full in ~" : "Empty in ~")
                        + com.createstats.power.FeGoggle.time(endTicks)));
            }
        }

        if (isPlayerSneaking && stacked > 1) {
            tooltip.add(com.createstats.power.FeGoggle.dim(
                    String.format(Locale.ROOT, "Pooled across %d blocks, fills bottom up", stacked)));
        }

        final int rank = groupReserveRank();
        tooltip.add(Component.literal("    ").append(Component
                .literal(com.createstats.power.FeReserve.describe(rank))
                .withStyle(rank == com.createstats.power.FeReserve.MIN_RANK
                        ? ChatFormatting.GOLD : ChatFormatting.GRAY)));
        if (isPlayerSneaking) {
            tooltip.add(Component.literal("    ").append(Component
                    .literal(com.createstats.train.power.CarriedReadout.active()
                            ? "Carried: rank is set on the ground"
                            : "Wrench to change rank")
                    .withStyle(ChatFormatting.DARK_GRAY)));
        } else {
            tooltip.add(com.createstats.power.FeGoggle.more());
        }
        return true;
    }

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
            return 32768;
        }
    }
}
