package com.createstats.blockentity;

import com.createstats.block.WallOutletBlock;
import com.createstats.power.ApplianceStatus;
import com.createstats.power.BoreLinks;
import com.createstats.power.CordPlug;
import com.createstats.power.CordRoute;
import com.createstats.power.FeConsumer;
import com.createstats.power.FeGoggle;
import com.createstats.power.FeProfiles;
import com.createstats.util.InstrumentSourceHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;

import java.util.ArrayList;
import java.util.List;

// the bore is the only connection that goes through matter
public class WallOutletBE extends BlockEntity implements BoreLinks.Socket,
        com.createstats.power.IFeConsumer, com.createstats.power.Gateable,
        com.createstats.power.HomeAppliance, com.createstats.power.PlugHost,
        com.createstats.power.FeRatedLoad,
        com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation {
    @Override
    public com.createstats.power.HomeAppliance.Category applianceCategory() {
        return com.createstats.power.HomeAppliance.Category.SOCKETS;
    }

    @Override
    @org.jetbrains.annotations.Nullable
    public ApplianceStatus applianceStatus() {
        if (this.gated) {
            return null;
        }
        if (this.feConsumer.isSuppressed()) {
            return ApplianceStatus.idle(Component.translatable("status.create_stats.home.held_pdu"));
        }
        if (!this.isLive()) {
            return ApplianceStatus.idle(Component.translatable("status.create_stats.home.no_supply"));
        }
        final int plugged = this.level == null ? 0 : this.pluggedAppliances(this.level).size();
        if (plugged == 1) {
            return ApplianceStatus.idle(Component.translatable("status.create_stats.home.feeding_one"));
        }
        if (plugged > 1) {
            return ApplianceStatus.idle(Component.translatable("status.create_stats.home.feeding_many",
                    plugged));
        }
        if (this.served > 0) {
            return ApplianceStatus.idle(Component.translatable("status.create_stats.home.feeding_fe",
                    this.served));
        }
        return ApplianceStatus.idle(Component.translatable("status.create_stats.home.live"));
    }

    public static final int RATED_FE_PER_TICK = 512;

    private final FeConsumer feConsumer = FeConsumer.forBlockEntity(this,
            FeProfiles.settings(FeProfiles.KEY_WALL_OUTLET));

    private final List<BlockPos> bores = new ArrayList<>(1);

    private static final class Cord {
        BlockPos target;
        Direction face;

        List<Vec3> route = List.of();

        int reviewTicks = CORD_REVIEW_TICKS;
    }

    private final Cord[] cords;

    private int served;

    private float servedAverage;

    private int demand;

    private int[] cordDemand;

    private boolean gated;

    private boolean linked;

    public WallOutletBE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        this.cords = new Cord[gangOffsetsFor(state).length];
        this.cordDemand = new int[this.cords.length];
    }

    public FeConsumer feConsumer() {
        return this.feConsumer;
    }

    public int servedFePerTick() {
        return this.served;
    }

    // the socket costs what's plugged in. idle drain 0, charging a hole in the wall for existing is dumb
    @Override
    public int ratedDrawFePerTick() {
        return Math.min(RATED_FE_PER_TICK, Math.max(0, this.demand));
    }

    @Override
    public int measuredDrawFePerTick() {
        return Math.max(0, this.served);
    }

    public boolean isLive() {
        return !this.gated && (this.feConsumer.isCreativePowered() || this.feConsumer.getEnergyStored() > 0);
    }

    @Override
    public void setGated(boolean gated) {
        if (this.gated == gated) {
            return;
        }
        this.gated = gated;
        this.setChanged();
    }

    @Override
    public boolean isGated() {
        return this.gated;
    }

    public void setLinked(boolean linked) {
        if (this.linked == linked) {
            return;
        }
        this.linked = linked;
        this.setChanged();
        if (this.level != null && !this.level.isClientSide()) {
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 2);
        }
    }

    public boolean isLinked() {
        return this.linked;
    }

    public void tick() {
        if (this.level == null || this.level.isClientSide()) {
            return;
        }

        if (!this.gated) {
            com.createstats.power.FePowerFx.chargeFromNeighbors(this.level, this.worldPosition, this.feConsumer);
        }
        for (int gang = 0; gang < this.cords.length; gang++) {
            final Cord cord = this.cords[gang];
            if (cord != null && --cord.reviewTicks <= 0) {
                cord.reviewTicks = CORD_REVIEW_TICKS;
                this.reviewCord(gang);
            }
        }

        final boolean off = this.gated || this.feConsumer.isSuppressed();
        this.served = off ? 0 : this.pushToNeighbours();

        if (this.gated || this.feConsumer.lock() == com.createstats.power.FeLock.FORCE_OFF) {
            this.demand = 0;
            java.util.Arrays.fill(this.cordDemand, 0);
        } else {
            this.refreshDemand();
        }
        this.servedAverage += (this.served - this.servedAverage) * 0.25f;
        this.updateLamp();
        this.syncLoadIfDue();
    }

    private int lastSyncedServed = Integer.MIN_VALUE;
    private int lastSyncedDemand = Integer.MIN_VALUE;
    private int ticksSinceLoadSync;

    private void syncLoadIfDue() {
        this.ticksSinceLoadSync++;
        final int servedBucket = Math.max(1, Math.abs(this.served) / 16);
        final int demandBucket = Math.max(1, Math.abs(this.demand) / 16);
        if (Math.abs(this.served - this.lastSyncedServed) < servedBucket
                && Math.abs(this.demand - this.lastSyncedDemand) < demandBucket
                && this.ticksSinceLoadSync < 20) {
            return;
        }
        this.lastSyncedServed = this.served;
        this.lastSyncedDemand = this.demand;
        this.ticksSinceLoadSync = 0;
        this.setChanged();
        this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 2);
    }

    private int demandOf(BlockPos at, Direction face) {
        final BlockEntity be = this.level.getBlockEntity(at);

        if (be instanceof com.createstats.power.FeRatedLoad own) {
            return Math.max(0, own.ratedDrawFePerTick());
        }
        int rated = com.createstats.compat.CreateAdditionCompatHelper.consumptionFePerTick(be);
        if (rated < 0) {
            rated = com.createstats.compat.NewAgeCompatHelper.consumptionFePerTick(be);
        }
        if (rated >= 0) {
            return rated;
        }
        final IEnergyStorage sink = this.level.getCapability(Capabilities.EnergyStorage.BLOCK, at, face);
        return sink == null ? 0 : sink.receiveEnergy(RATED_FE_PER_TICK, true);
    }

    private void refreshDemand() {
        int total = 0;
        for (Direction direction : Direction.values()) {
            final BlockPos at = this.worldPosition.relative(direction);
            if (this.refuses(at)) {
                continue;
            }
            total += this.demandOf(at, direction.getOpposite());
        }
        for (int gang = 0; gang < this.cords.length; gang++) {
            final Cord cord = this.cords[gang];
            final int need = cord != null && this.level.isLoaded(cord.target)
                    ? this.demandOf(cord.target, cord.face) : 0;
            this.cordDemand[gang] = need;
            total += need;
        }
        this.demand = total;
    }

    private int pushToNeighbours() {
        final int budget = this.feConsumer.isCreativePowered() ? RATED_FE_PER_TICK
                : Math.min(RATED_FE_PER_TICK, this.feConsumer.getEnergyStored());
        if (budget <= 0) {
            return 0;
        }
        final List<IEnergyStorage> takers = new ArrayList<>(7);
        for (Direction direction : Direction.values()) {
            final BlockPos at = this.worldPosition.relative(direction);
            if (this.refuses(at)) {
                continue;
            }
            add(takers, this.level.getCapability(
                    Capabilities.EnergyStorage.BLOCK, at, direction.getOpposite()));
        }

        for (Cord cord : this.cords) {
            if (cord != null && this.level.isLoaded(cord.target)) {
                add(takers, this.level.getCapability(Capabilities.EnergyStorage.BLOCK,
                        cord.target, cord.face));
            }
        }
        if (takers.isEmpty()) {
            return 0;
        }
        int left = budget;
        int total = 0;

        for (int pass = 0; pass < 2 && left > 0; pass++) {
            final int share = Math.max(1, left / takers.size());
            for (IEnergyStorage taker : takers) {
                if (left <= 0) {
                    break;
                }
                final int moved = taker.receiveEnergy(Math.min(share, left), false);
                left -= moved;
                total += moved;
            }
        }
        if (total > 0) {
            this.feConsumer.drawDown(total);
            this.setChanged();
        }
        return total;
    }

    private static void add(List<IEnergyStorage> takers, @org.jetbrains.annotations.Nullable IEnergyStorage sink) {
        if (sink != null && sink.canReceive() && sink.receiveEnergy(1, true) > 0) {
            takers.add(sink);
        }
    }

    public static final int CORD_REVIEW_TICKS = 40;

    public int gangCount() {
        return this.cords.length;
    }

    @org.jetbrains.annotations.Nullable
    public BlockPos cordTarget(int gang) {
        final Cord cord = this.cords[gang];
        return cord == null ? null : cord.target;
    }

    public List<Vec3> cordRoute(int gang) {
        final Cord cord = this.cords[gang];
        return cord == null ? List.of() : cord.route;
    }

    @org.jetbrains.annotations.Nullable
    public Direction cordFace(int gang) {
        final Cord cord = this.cords[gang];
        return cord == null ? null : cord.face;
    }

    public boolean anyCorded() {
        for (Cord cord : this.cords) {
            if (cord != null) {
                return true;
            }
        }
        return false;
    }

    @Override
    public List<BlockPos> pluggedAppliances(Level level) {
        final List<BlockPos> found = new ArrayList<>(this.cords.length + 6);
        for (Direction direction : Direction.values()) {
            final BlockPos at = this.worldPosition.relative(direction);
            if (this.refuses(at) || !level.isLoaded(at)) {
                continue;
            }
            if (level.getBlockEntity(at) instanceof com.createstats.power.HomeAppliance) {
                found.add(at);
            }
        }
        for (Cord cord : this.cords) {
            if (cord != null && level.isLoaded(cord.target)
                    && level.getBlockEntity(cord.target) instanceof com.createstats.power.HomeAppliance
                    && !found.contains(cord.target)) {
                found.add(cord.target);
            }
        }
        return found;
    }

    public double[] gangOffsets() {
        return gangOffsetsFor(this.getBlockState());
    }

    private static double[] gangOffsetsFor(BlockState state) {
        return state.getBlock() == com.createstats.registry.ModBlocks.DUPLEX_POWER_OUTLET.get()
                ? new double[] {4.0 / 16.0, 12.0 / 16.0} : new double[] {8.0 / 16.0};
    }

    private int nearestGang(Vec3 towards) {
        final double[] bores = this.gangOffsets();
        int best = 0;
        double bestDist = Double.MAX_VALUE;
        for (int i = 0; i < bores.length; i++) {
            final Vec3 mouth = CordPlug.socketMouth(this.getBlockState(), this.worldPosition,
                    new double[] {bores[i]}, null);
            final double d = mouth.distanceToSqr(towards);
            if (d < bestDist) {
                bestDist = d;
                best = i;
            }
        }
        return best;
    }

    private int pickGangFor(BlockPos machine) {
        if (this.cords.length == 1) {
            return 0;
        }
        final int nearest = this.nearestGang(Vec3.atCenterOf(machine));
        if (this.cords[nearest] == null) {
            return nearest;
        }
        final int other = 1 - nearest;
        return this.cords[other] == null ? other : -1;
    }

    @org.jetbrains.annotations.Nullable
    public String plugIn(BlockPos machine) {
        if (this.level == null) {
            return "no world";
        }
        if (machine.equals(this.worldPosition)) {
            return "A socket cannot be plugged into itself.";
        }
        final int gang = this.pickGangFor(machine);
        if (gang < 0) {
            return "Both sockets already have a cord. Unplug one first.";
        }
        final Cord cord = new Cord();
        final String refusal = this.settle(cord, gang, machine);
        if (refusal != null) {
            return refusal;
        }
        this.cords[gang] = cord;
        this.syncCord();
        return null;
    }

    public boolean unplug(Vec3 near) {
        final int gang = this.occupiedGangNearest(near);
        if (gang < 0) {
            return false;
        }
        this.cords[gang] = null;
        this.syncCord();
        return true;
    }

    private int occupiedGangNearest(Vec3 near) {
        int only = -1;
        int occupied = 0;
        for (int i = 0; i < this.cords.length; i++) {
            if (this.cords[i] != null) {
                only = i;
                occupied++;
            }
        }
        if (occupied == 0) {
            return -1;
        }
        return occupied == 1 ? only : this.nearestGang(near);
    }

    @org.jetbrains.annotations.Nullable
    private String settle(Cord cord, int gang, BlockPos machine) {
        final double[] bores = this.gangOffsets();
        final Vec3 mouth = CordPlug.socketMouth(this.getBlockState(), this.worldPosition,
                new double[] {bores[gang]}, null);
        final Direction face = CordPlug.machineFace(this.level, machine, mouth);
        if (face == null) {
            return "Nothing on that block will take a plug.";
        }
        final Vec3 terminal = CordPlug.terminal(machine, face);
        if (mouth.distanceTo(terminal) > CordRoute.MAX_REACH) {
            return "Too far for a cord. Move it closer, or run cable.";
        }
        final List<Vec3> route = CordRoute.plan(this.level, this.worldPosition, machine, mouth, terminal);
        if (route == null) {
            return "No way to lay a cord between those two.";
        }
        cord.target = machine.immutable();
        cord.face = face;
        cord.route = route;
        cord.reviewTicks = CORD_REVIEW_TICKS;
        return null;
    }

    private void reviewCord(int gang) {
        final Cord cord = this.cords[gang];
        if (cord == null || !this.level.isLoaded(cord.target)) {
            return;
        }
        if (cord.face == null || this.level.getCapability(Capabilities.EnergyStorage.BLOCK,
                cord.target, cord.face) == null) {
            this.cords[gang] = null;
            this.syncCord();
            return;
        }
        final CordRoute.Solid world = CordRoute.worldAround(this.level, this.worldPosition, cord.target);
        for (Vec3 point : cord.route) {
            if (world.at(point.x, point.y, point.z) != null) {
                if (this.settle(cord, gang, cord.target) != null) {
                    this.cords[gang] = null;
                }
                this.syncCord();
                return;
            }
        }
    }

    private void syncCord() {
        this.setChanged();
        if (this.level != null && !this.level.isClientSide()) {
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
        }
    }

    private boolean refuses(BlockPos at) {
        final BlockEntity neighbour = this.level.getBlockEntity(at);
        return neighbour instanceof WallOutletBE
                || neighbour instanceof DiagnosticJunctionBE
                || InstrumentSourceHelper.isGridWiring(neighbour);
    }

    private void updateLamp() {
        final WallOutletBlock.Lamp want = !this.isLive() ? WallOutletBlock.Lamp.DEAD
                : this.servedAverage >= RATED_FE_PER_TICK * 0.95f ? WallOutletBlock.Lamp.LOADED
                : WallOutletBlock.Lamp.LIVE;
        final BlockState state = this.getBlockState();
        if (state.hasProperty(WallOutletBlock.LAMP) && state.getValue(WallOutletBlock.LAMP) != want) {
            this.level.setBlock(this.worldPosition, state.setValue(WallOutletBlock.LAMP, want), 3);
        }
    }

    @Override
    public BlockPos borePosition() {
        return this.worldPosition;
    }

    @Override
    public List<BlockPos> bores() {
        return this.bores;
    }

    @Override
    public boolean addBore(BlockPos far) {
        if (this.bores.contains(far)) {
            return false;
        }
        this.bores.add(far.immutable());
        return true;
    }

    @Override
    public boolean removeBore(BlockPos far) {
        return this.bores.remove(far);
    }

    @Override
    public void invalidate(Level owner) {
        this.setChanged();
        if (owner != null && !owner.isClientSide()) {
            owner.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 2);
        }
    }

    public boolean isBored() {
        return !this.bores.isEmpty();
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        tooltip.add(FeGoggle.title(this.getBlockState().getBlock().getName()));
        if (this.gated) {
            tooltip.add(FeGoggle.warn("Gated by a switch"));
            return true;
        }
        if (!this.isLive()) {
            tooltip.add(FeGoggle.bad(this.isBored() ? "Bored, but the circuit is dead" : "No supply"));
            if (!this.isBored()) {
                tooltip.add(FeGoggle.dim("Bore through the wall to a cable behind it"));
            }
            return true;
        }
        tooltip.add(this.served > 0 ? FeGoggle.ok("Feeding") : FeGoggle.ok("Live, nothing drawing"));
        if (this.linked) {
            tooltip.add(FeGoggle.dim("Paired to a switch"));
        }
        for (int gang = 0; gang < this.cords.length; gang++) {
            final Cord cord = this.cords[gang];
            if (cord == null) {
                continue;
            }
            final String name = "Corded to "
                    + this.level.getBlockState(cord.target).getBlock().getName().getString();
            final int need = this.cordDemand[gang];
            tooltip.add(FeGoggle.dim(need > 0
                    ? name + String.format(java.util.Locale.ROOT, " (%,d FE/t)", need) : name));
        }
        tooltip.add(FeGoggle.supplyDemand(this.served, this.demand));
        if (isPlayerSneaking) {
            tooltip.add(FeGoggle.stored(this.feConsumer.getEnergyStored(),
                    this.feConsumer.getMaxEnergyStored()));
            tooltip.add(FeGoggle.dim(this.isBored()
                    ? "Fed through a bore" : "Fed by wiring against the plate"));
        } else {
            tooltip.add(FeGoggle.more());
        }
        return true;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        this.feConsumer.save(tag);
        BoreLinks.save(tag, this.bores);
        tag.putBoolean("Gated", this.gated);
        tag.putBoolean("Linked", this.linked);

        tag.putInt("Served", this.served);
        tag.putInt("Demand", this.demand);

        final ListTag cordsTag = new ListTag();
        for (int gang = 0; gang < this.cords.length; gang++) {
            final Cord cord = this.cords[gang];
            if (cord == null) {
                continue;
            }
            final CompoundTag entry = new CompoundTag();
            entry.putInt("Gang", gang);
            entry.putLong("CordAt", cord.target.asLong());
            entry.putInt("CordFace", cord.face.get3DDataValue());
            entry.putInt("Demand", this.cordDemand[gang]);
            final ListTag route = new ListTag();
            for (Vec3 point : cord.route) {
                final CompoundTag at = new CompoundTag();
                at.putDouble("X", point.x);
                at.putDouble("Y", point.y);
                at.putDouble("Z", point.z);
                route.add(at);
            }
            entry.put("Route", route);
            cordsTag.add(entry);
        }
        tag.put("Cords", cordsTag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.feConsumer.load(tag);
        this.bores.clear();
        this.bores.addAll(BoreLinks.load(tag));
        this.gated = tag.getBoolean("Gated");
        this.linked = tag.getBoolean("Linked");
        this.served = tag.getInt("Served");
        this.demand = tag.getInt("Demand");
        java.util.Arrays.fill(this.cords, null);
        java.util.Arrays.fill(this.cordDemand, 0);
        final ListTag cordsTag = tag.getList("Cords", net.minecraft.nbt.Tag.TAG_COMPOUND);
        for (int i = 0; i < cordsTag.size(); i++) {
            final CompoundTag entry = cordsTag.getCompound(i);
            final int gang = entry.getInt("Gang");
            if (gang < 0 || gang >= this.cords.length) {
                continue;
            }
            final Cord cord = new Cord();
            cord.target = BlockPos.of(entry.getLong("CordAt"));
            cord.face = Direction.from3DDataValue(entry.getInt("CordFace"));
            this.cordDemand[gang] = entry.getInt("Demand");
            final ListTag route = entry.getList("Route", net.minecraft.nbt.Tag.TAG_COMPOUND);
            final List<Vec3> points = new ArrayList<>(route.size());
            for (int j = 0; j < route.size(); j++) {
                final CompoundTag at = route.getCompound(j);
                points.add(new Vec3(at.getDouble("X"), at.getDouble("Y"), at.getDouble("Z")));
            }
            cord.route = List.copyOf(points);
            this.cords[gang] = cord;
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        final CompoundTag tag = new CompoundTag();
        this.saveAdditional(tag, registries);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(Connection connection, ClientboundBlockEntityDataPacket packet,
                             HolderLookup.Provider registries) {
        final CompoundTag tag = packet.getTag();
        if (tag != null) {
            this.loadAdditional(tag, registries);
        }
    }

    @Override
    public void setRemoved() {
        if (this.level != null && !this.level.isClientSide()) {
            BoreLinks.severAll(this.level, this);
        }
        super.setRemoved();
    }
}
