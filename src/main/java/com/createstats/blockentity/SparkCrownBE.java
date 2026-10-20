package com.createstats.blockentity;

import com.createstats.block.SparkCrownBlock;
import com.createstats.block.SparkCrownStructureBlock;
import com.createstats.chunkloader.CrownConfig;
import com.createstats.chunkloader.CrownRegistry;
import com.createstats.chunkloader.CrownTickets;
import com.createstats.chunkloader.core.CrownBoard;
import com.createstats.chunkloader.core.CrownHold;
import com.createstats.chunkloader.core.CrownLaw;
import com.createstats.chunkloader.core.CrownPicks;
import com.createstats.chunkloader.core.CrownPlan;
import com.createstats.chunkloader.core.CrownRegion;
import com.createstats.chunkloader.core.CrownState;
import com.createstats.chunkloader.core.CrownStuds;
import com.createstats.power.FeBoardPeer;
import com.createstats.power.FeFeeds;
import com.createstats.power.FeGoggle;
import com.createstats.power.FeGridMember;
import com.createstats.power.FeIntakeBudget;
import com.createstats.power.FeLock;
import com.createstats.power.FeManagedLoad;
import com.createstats.power.FeManagedState;
import com.createstats.power.FePriority;
import com.createstats.power.FeRates;
import com.createstats.power.FeStationFeed;
import com.createstats.power.FeStationLoad;
import com.createstats.power.FeStudPanel;
import com.createstats.power.FeWireNetwork;
import com.createstats.power.FeWireTerminal;
import com.createstats.power.PduReason;
import com.createstats.power.WireTier;
import com.createstats.registry.ModBlocks;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class SparkCrownBE extends SmartBlockEntity implements FeGridMember, FeManagedLoad, FeWireTerminal,
        FeStationLoad, IHaveGoggleInformation {
    private static final int HEARTBEAT_TICKS = 40;

    private static final int LOOK_TICKS = 20;
    private static final int TICKING_TICKS = 10;

    private final LongArrayList picks = new LongArrayList();

    private int held;
    private final CrownHold hold = new CrownHold();
    @Nullable
    private UUID owner;
    private String ownerName = "";

    private final BlockPos[] studLink = new BlockPos[2];
    private final WireTier[] studTier = {WireTier.COPPER, WireTier.COPPER};

    private final int[] studKind = {FeFeeds.UNWIRED, FeFeeds.UNWIRED};
    private boolean studKindsRead;

    private int adoptedHome = Integer.MIN_VALUE;

    private final FeIntakeBudget intake = new FeIntakeBudget();
    private final FeManagedState managed = new FeManagedState();
    private int heartbeat;

    {
        managed.setPriority(FePriority.CRITICAL);
    }

    @Nullable
    private FeWireNetwork network;
    private boolean ticked;
    private int sinceHeartbeat;
    private int sinceLook = LOOK_TICKS;
    private int sinceTicking;
    private boolean formDue = true;
    private CrownState.Reason block = CrownState.Reason.NONE;
    private long otherStation;
    private int ownerTotal;
    private int form;
    @Nullable
    private CrownPlan.Cell blockingCell;
    private String blockingId = "";
    private int tickingMask;
    private int measuredDraw;

    private boolean releasing;
    private List<BlockPos> wireList = List.of();
    private int syncCooldown;
    private int numbersCooldown;
    private CrownState lastSent = CrownState.EMPTY;

    private final FeStationFeed[] boardFeeds = new FeStationFeed[2];

    private String lastBoardSent = "";

    private CrownBoard clientBoard = CrownBoard.NONE;

    private CrownState crown = CrownState.EMPTY;

    private final IEnergyStorage energyView = new IEnergyStorage() {
        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            if (level == null || level.isClientSide() || maxReceive <= 0) {
                return 0;
            }
            final CrownLaw law = CrownConfig.law();
            final long capacity = law.storeCapacity(picks.size());
            final long draw = law.totalDraw(held);

            final long now = level.getGameTime();
            final int rate = transferRate();
            final int take = (int) Math.min(Math.min(maxReceive, hold.room(capacity, draw)), intake.remaining(rate, now));
            if (take > 0 && !simulate) {
                hold.accept(take, capacity, draw);
                intake.spend(rate, now, take);
                setChanged();
            }
            return Math.max(0, take);
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            return 0;
        }

        @Override
        public int getEnergyStored() {
            return (int) Math.min(Integer.MAX_VALUE, hold.stored());
        }

        @Override
        public int getMaxEnergyStored() {
            final CrownLaw law = CrownConfig.law();
            final long max = Math.max(law.storeCapacity(picks.size()) + law.totalDraw(held), hold.stored());
            return (int) Math.min(Integer.MAX_VALUE, max);
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

    public SparkCrownBE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
    }

    public IEnergyStorage energyHandle() {
        return energyView;
    }

    public Direction facing() {
        final BlockState s = getBlockState();
        return s.hasProperty(SparkCrownBlock.FACING) ? s.getValue(SparkCrownBlock.FACING) : Direction.SOUTH;
    }

    public Direction backFace() {
        return facing().getOpposite();
    }

    public boolean takesPowerOn(@Nullable Direction side) {
        return side == null || side == backFace();
    }

    public CrownState crown() {
        return crown;
    }

    @Nullable
    public UUID owner() {
        return owner;
    }

    public String ownerName() {
        return ownerName;
    }

    public long[] picks() {
        return picks.toLongArray();
    }

    public long ownChunk() {
        return ChunkPos.asLong(worldPosition.getX() >> 4, worldPosition.getZ() >> 4);
    }

    public boolean isReleasing() {
        return releasing;
    }

    @Override
    public void tick() {
        super.tick();
        if (level != null && level.isClientSide()) {
            com.createstats.chunkloader.client.CrownSounds.tick(this);
            return;
        }
        if (!(level instanceof ServerLevel server)) {
            return;
        }

        readStudKinds();
        if (network == null || network.isDirty()) {
            FeWireNetwork.buildFrom(this);
        }
        if (network != null) {
            network.solveOnce(level);
        }
        final long now = level.getGameTime();

        if (network == null || !network.isPduManaged(now)) {
            setManagedShed(false);
        }
        final CrownLaw law = CrownConfig.law();
        if (!ticked) {
            firstTick(server, law);
            ticked = true;
        }
        if (++sinceLook >= LOOK_TICKS) {
            sinceLook = 0;
            look(server, law);
        }
        final int heldBefore = held;
        final CrownHold.Verdict verdict = hold.step(held, picks.size(), law, block == CrownState.Reason.NONE);
        measuredDraw = heldBefore > 0 && verdict != CrownHold.Verdict.RELEASE_ALL ? law.totalDraw(heldBefore) : 0;
        switch (verdict) {
            case ACQUIRE -> acquireNext(server);
            case RELEASE_ALL -> releaseAll(server);
            default -> {
            }
        }
        if (held != heldBefore) {
            formDue = true;
            publish(server);
        }
        if (held > 0 && ++sinceHeartbeat >= HEARTBEAT_TICKS) {
            sinceHeartbeat = 0;
            for (int i = 0; i < held; i++) {
                CrownTickets.hold(server, worldPosition, picks.getLong(i));
            }
            heartbeat++;
        }
        if (++sinceTicking >= TICKING_TICKS) {
            sinceTicking = 0;
            tickingMask = tickingMask(server);
        }
        if (formDue) {
            formDue = false;
            reshape(server);
        }
        updateLight(law);
        crown = state(law);
        sync();
        setChanged();
    }

    private void firstTick(ServerLevel server, CrownLaw law) {
        final long own = ownChunk();
        final int ox = worldPosition.getX() >> 4;
        final int oz = worldPosition.getZ() >> 4;
        final LongArrayList kept = new LongArrayList();
        kept.add(own);
        for (int i = 0; i < picks.size(); i++) {
            final long c = picks.getLong(i);
            if (c != own && !kept.contains(c) && kept.size() < law.maxPerStation()
                    && CrownRegion.sameRegion(ox, oz, CrownRegion.x(c), CrownRegion.z(c))) {
                kept.add(c);
            }
        }
        final boolean ownWasFirst = !picks.isEmpty() && picks.getLong(0) == own;
        picks.clear();
        picks.addAll(kept);
        if (!ownWasFirst) {
            held = 0;
        }
        held = Math.min(held, picks.size());
        for (int i = 0; i < picks.size(); i++) {
            if (i < held) {
                CrownTickets.hold(server, worldPosition, picks.getLong(i));
            } else {
                CrownTickets.release(server, worldPosition, picks.getLong(i));
            }
        }
        look(server, law);
        sinceLook = 0;
        formDue = true;
    }

    private void look(ServerLevel server, CrownLaw law) {
        while (picks.size() > law.maxPerStation()) {
            final int last = picks.size() - 1;
            if (last < held) {
                CrownTickets.release(server, worldPosition, picks.getLong(last));
                held = last;
            }
            picks.removeLong(last);
            formDue = true;
        }
        otherStation = 0L;
        final CrownRegistry registry = CrownRegistry.get(server);
        final String dimension = CrownRegistry.dimensionOf(server);
        if (owner == null) {
            block = CrownState.Reason.NO_OWNER;
            registry.remove(dimension, worldPosition.asLong());
        } else if (onSable()) {
            block = CrownState.Reason.ON_SABLE;
            registry.remove(dimension, worldPosition.asLong());
        } else {
            final int rx = CrownRegion.regionOf(worldPosition.getX() >> 4);
            final int rz = CrownRegion.regionOf(worldPosition.getZ() >> 4);
            final CrownRegistry.Station holder = registry.holderOf(dimension, rx, rz,
                    p -> CrownRegistry.stationStillAt(server, p));
            if (holder != null && holder.pos() != worldPosition.asLong()) {
                block = CrownState.Reason.REGION_TAKEN;
                otherStation = holder.pos();
            } else {
                block = managed.isForcedOff() ? CrownState.Reason.SWITCHED_OFF : CrownState.Reason.NONE;
                publish(server);
            }
        }
        ownerTotal = registry.ownerTotal(owner);
        formDue = true;
    }

    // ask sable, plot positions lie. sable tax #3
    private boolean onSable() {
        return com.createstats.flight.bridge.SableBridge.subLevelAccessOf(this) != null
                || CrownRegion.isPlotChunk(worldPosition.getX() >> 4, worldPosition.getZ() >> 4);
    }

    private void publish(ServerLevel server) {
        if (owner == null || block == CrownState.Reason.REGION_TAKEN || block == CrownState.Reason.ON_SABLE) {
            return;
        }
        CrownRegistry.get(server).put(new CrownRegistry.Station(CrownRegistry.dimensionOf(server),
                worldPosition.asLong(), owner, CrownRegion.regionOf(worldPosition.getX() >> 4),
                CrownRegion.regionOf(worldPosition.getZ() >> 4), picks.toLongArray(), held));
    }

    private void acquireNext(ServerLevel server) {
        if (held >= picks.size()) {
            return;
        }
        if (CrownTickets.hold(server, worldPosition, picks.getLong(held))) {
            held++;
        }
    }

    private void releaseAll(ServerLevel server) {
        for (int i = 0; i < held; i++) {
            CrownTickets.release(server, worldPosition, picks.getLong(i));
        }
        held = 0;
    }

    private int tickingMask(ServerLevel server) {
        int mask = 0;
        for (int i = 0; i < held && i < 32; i++) {
            final long c = picks.getLong(i);
            if (server.getChunkSource().isPositionTicking(c) && server.isNaturalSpawningAllowed(new ChunkPos(c))) {
                mask |= 1 << i;
            }
        }
        return mask;
    }

    public BlockPos cellPos(CrownPlan.Cell c) {
        final Direction front = facing();
        final Direction right = front.getClockWise();
        return worldPosition.relative(right, c.right()).above(c.up()).relative(front, c.front());
    }

    private Direction worldStep(int[] step) {
        final Direction front = facing();
        if (step[1] != 0) {
            return step[1] < 0 ? Direction.DOWN : Direction.UP;
        }
        if (step[0] != 0) {
            final Direction right = front.getClockWise();
            return step[0] > 0 ? right : right.getOpposite();
        }
        return step[2] > 0 ? front : front.getOpposite();
    }

    private boolean cellFree(ServerLevel server, CrownPlan.Cell c) {
        final BlockPos p = cellPos(c);
        final BlockState there = server.getBlockState(p);
        if (isOwnCell(server, p, there)) {
            return true;
        }
        return there.canBeReplaced() && server.getEntitiesOfClass(LivingEntity.class, new AABB(p)).isEmpty();
    }

    private boolean isOwnCell(ServerLevel server, BlockPos p, BlockState there) {
        return there.getBlock() instanceof SparkCrownStructureBlock
                && worldPosition.equals(SparkCrownStructureBlock.master(server, p, there));
    }

    private void reshape(ServerLevel server) {
        form = CrownPlan.formCount(held, c -> cellFree(server, c));
        final CrownPlan.Cell first = CrownPlan.firstBlocked(held, c -> cellFree(server, c));
        blockingCell = first;
        blockingId = first == null ? "" : whatIsAt(server, cellPos(first));
        final List<CrownPlan.Cell> want = CrownPlan.cellsFor(form);

        final List<CrownPlan.Cell> all = new ArrayList<>(CrownPlan.cellsFor(CrownLaw.MAX_FORM));
        Collections.reverse(all);
        for (CrownPlan.Cell c : all) {
            if (c.isBase() || want.contains(c)) {
                continue;
            }
            final BlockPos p = cellPos(c);
            if (isOwnCell(server, p, server.getBlockState(p))) {
                giveBack(server, p);
            }
        }

        for (CrownPlan.Cell c : want) {
            if (c.isBase()) {
                continue;
            }
            final BlockPos p = cellPos(c);
            final BlockState there = server.getBlockState(p);
            if (isOwnCell(server, p, there)) {
                continue;
            }
            server.setBlock(p, ModBlocks.SPARK_CROWN_STRUCTURE.get().defaultBlockState()
                    .setValue(SparkCrownStructureBlock.FACING, worldStep(CrownPlan.parentStep(c))), 3);
        }
    }

    private void giveBack(ServerLevel server, BlockPos p) {
        releasing = true;
        try {
            server.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
        } finally {
            releasing = false;
        }
    }

    public void giveBackAllCells(ServerLevel server, Direction front) {
        final Direction right = front.getClockWise();
        for (CrownPlan.Cell c : CrownPlan.cellsFor(CrownLaw.MAX_FORM)) {
            if (c.isBase()) {
                continue;
            }
            final BlockPos p = worldPosition.relative(right, c.right()).above(c.up()).relative(front, c.front());
            if (server.getBlockState(p).getBlock() instanceof SparkCrownStructureBlock) {
                giveBack(server, p);
            }
        }
    }

    private static String whatIsAt(ServerLevel server, BlockPos p) {
        final List<LivingEntity> in = server.getEntitiesOfClass(LivingEntity.class, new AABB(p));
        if (!in.isEmpty()) {
            return String.valueOf(BuiltInRegistries.ENTITY_TYPE.getKey(in.get(0).getType()));
        }
        return String.valueOf(BuiltInRegistries.BLOCK.getKey(server.getBlockState(p).getBlock()));
    }

    private void updateLight(CrownLaw law) {
        final BlockState s = getBlockState();
        if (!s.hasProperty(SparkCrownBlock.LIGHT) || level == null) {
            return;
        }
        final int light = law.lightLevel(held);
        if (s.getValue(SparkCrownBlock.LIGHT) != light) {
            level.setBlock(worldPosition, s.setValue(SparkCrownBlock.LIGHT, light), 3);
        }
    }

    public CrownPicks.Verdict togglePick(ServerPlayer player, long chunk) {
        if (!(level instanceof ServerLevel server)) {
            return CrownPicks.Verdict.NO_OWNER;
        }
        final CrownLaw law = CrownConfig.law();
        final CrownRegistry registry = CrownRegistry.get(server);
        final CrownPicks.Verdict v = CrownPicks.toggle(new CrownPicks.Request(owner != null,
                owner != null && owner.equals(player.getUUID()),
                block == CrownState.Reason.NONE || block == CrownState.Reason.SWITCHED_OFF,
                worldPosition.getX() >> 4, worldPosition.getZ() >> 4, CrownRegion.x(chunk), CrownRegion.z(chunk),
                picks.contains(chunk), picks.size(), registry.ownerTotal(owner),
                board(server.getGameTime()).pickCarry(), board(server.getGameTime()).pickShortIn()), law);
        if (v == CrownPicks.Verdict.PICK) {
            final boolean allHeld = held > 0 && held == picks.size();
            picks.add(chunk);
            if (allHeld && CrownTickets.hold(server, worldPosition, chunk)) {
                held++;
            }
        } else if (v == CrownPicks.Verdict.DROP) {
            final int at = picks.indexOf(chunk);
            if (at < held) {
                CrownTickets.release(server, worldPosition, chunk);
                held--;
            }
            picks.removeLong(at);
        }
        if (v.changes()) {
            formDue = true;
            publish(server);
            ownerTotal = registry.ownerTotal(owner);
            crown = state(law);
            syncCooldown = 0;
            sendData();
            setChanged();
        }
        return v;
    }

    public boolean hasPick(long chunk) {
        return picks.contains(chunk);
    }

    public void announce(ServerPlayer player, CrownPicks.Verdict v) {
        final CrownLaw law = CrownConfig.law();
        player.displayClientMessage(v.changes() ? pickMessage(v, law) : refusal(v, law), true);
    }

    public void claimIfUnowned(ServerPlayer player) {
        if (owner == null && level instanceof ServerLevel server
                && !(player instanceof net.neoforged.neoforge.common.util.FakePlayer)) {
            claim(server, player);
        }
    }

    private void claim(ServerLevel server, ServerPlayer player) {
        final CrownLaw law = CrownConfig.law();
        final int total = CrownRegistry.get(server).ownerTotal(player.getUUID());
        if (total + Math.max(1, picks.size()) > law.maxPerPlayer()) {
            player.displayClientMessage(Component.translatable("block.create_stats.spark_crown.player_full",
                    total, law.maxPerPlayer()), true);
            return;
        }
        setOwner(player.getUUID(), player.getGameProfile().getName());
        look(server, law);
        player.displayClientMessage(Component.translatable("block.create_stats.spark_crown.claimed"), true);
    }

    public void setOwner(@Nullable UUID uuid, String name) {
        owner = uuid;
        ownerName = name == null ? "" : name;
        sinceLook = LOOK_TICKS;
        setChanged();
    }

    private Component summary(CrownLaw law) {
        final int draw = law.totalDraw(held);
        return held == picks.size()
                ? Component.translatable("block.create_stats.spark_crown.summary", held, picks.size(), fmt(draw),
                fmt(law.nextPrice(picks.size())))
                : Component.translatable("block.create_stats.spark_crown.summary_dark", held, picks.size(),
                fmt(law.totalDraw(picks.size())));
    }

    private Component pickMessage(CrownPicks.Verdict v, CrownLaw law) {
        final CrownBoard b = board(level == null ? 0L : level.getGameTime());
        if (v != CrownPicks.Verdict.PICK || !b.covers() || !b.onBanks()) {
            return summary(law);
        }
        return b.shortIn() >= 0
                ? Component.translatable("block.create_stats.spark_crown.pick_on_banks_short", summary(law),
                        duration(b.shortIn()))
                : Component.translatable("block.create_stats.spark_crown.pick_on_banks", summary(law));
    }

    private static Component duration(int ticks) {
        final int seconds = Math.max(0, ticks) / 20;
        return seconds >= 60
                ? Component.translatable("block.create_stats.spark_crown.minutes", (seconds + 30) / 60)
                : Component.translatable("block.create_stats.spark_crown.seconds", seconds);
    }

    private Component refusal(CrownPicks.Verdict v, CrownLaw law) {
        return switch (v) {
            case NO_OWNER -> Component.translatable("block.create_stats.spark_crown.refuse.no_owner");
            case NOT_OWNER -> Component.translatable("block.create_stats.spark_crown.refuse.not_owner", ownerName);
            case STATION_DARK_FOR_GOOD -> Component.translatable("block.create_stats.spark_crown.refuse.blocked",
                    reason(block, otherStation));
            case OUTSIDE_REGION -> Component.translatable("block.create_stats.spark_crown.refuse.outside_region");
            case SABLE_PLOT -> Component.translatable("block.create_stats.spark_crown.refuse.sable");
            case OWN_CHUNK -> Component.translatable("block.create_stats.spark_crown.refuse.own_chunk");
            case STATION_FULL -> Component.translatable("block.create_stats.spark_crown.refuse.station_full",
                    law.maxPerStation());
            case PLAYER_FULL -> Component.translatable("block.create_stats.spark_crown.player_full", ownerTotal,
                    law.maxPerPlayer());
            case BOARD_CARRY -> {
                final CrownBoard b = board(level == null ? 0L : level.getGameTime());
                yield Component.translatable("block.create_stats.spark_crown.refuse.board_carry", studNames(b),
                        fmt(b.carried()), picks.size() + 1, fmt(law.totalDraw(picks.size() + 1)));
            }
            case BOARD_SUPPLY -> Component.translatable("block.create_stats.spark_crown.refuse.board_supply",
                    duration(board(level == null ? 0L : level.getGameTime()).shortIn()));
            default -> summary(law);
        };
    }

    private CrownState state(CrownLaw law) {
        final long capacity = law.storeCapacity(picks.size());
        final int studs = (studLink[0] != null ? 1 : 0) | (studLink[1] != null ? 2 : 0);
        final CrownState.Reason reason = block != CrownState.Reason.NONE ? block
                : held > 0 ? CrownState.Reason.NONE : CrownState.Reason.NO_POWER;
        final CrownPlan.Cell cell = blockingCell;

        final int ticking = held >= 32 ? tickingMask : tickingMask & ((1 << held) - 1);
        return new CrownState(held, picks.size(), form, ticking, hold.phase(),
                capacity <= 0 ? 0f : (float) Math.min(1.0, hold.stored() / (double) capacity),
                hold.stored(), capacity, law.totalDraw(held), law.totalDraw(picks.size()),
                (int) Math.min(Integer.MAX_VALUE, hold.lastArrived()),
                law.nextPrice(picks.size()), ownerTotal, law.maxPerStation(), law.maxPerPlayer(), studs,
                studLink[0] != null ? FeRates.wire(studTier[0]) : 0, studLink[1] != null ? FeRates.wire(studTier[1]) : 0,
                heartbeat, law.lightLevel(held), reason, otherStation, cell != null,
                cell == null ? 0 : cell.right(), cell == null ? 0 : cell.up(), cell == null ? 0 : cell.front(),
                blockingId, CrownRegion.regionOf(worldPosition.getX() >> 4),
                CrownRegion.regionOf(worldPosition.getZ() >> 4));
    }

    private void sync() {
        final String boardNow = level == null ? "" : board(level.getGameTime()).discrete();
        final boolean shapeChanged = !discrete(crown).equals(discrete(lastSent)) || !boardNow.equals(lastBoardSent);
        if (shapeChanged) {
            syncCooldown = Math.min(syncCooldown, 2);
            numbersCooldown = 0;
        }
        final int every = crown.phase() == CrownHold.Phase.RIDING ? 4 : 20;
        if (--numbersCooldown <= 0 || shapeChanged) {
            if (--syncCooldown <= 0) {
                syncCooldown = 2;
                numbersCooldown = every;
                lastSent = crown;
                lastBoardSent = boardNow;
                sendData();
            }
        }
    }

    private static CrownState discrete(CrownState s) {
        return new CrownState(s.held(), s.picked(), s.form(), s.tickingMask(), s.phase(), 0f, 0L, 0L, s.drawFePerTick(),
                s.pickedDrawFePerTick(), 0, s.nextPriceFePerTick(), s.ownerTotal(), s.maxPerStation(), s.maxPerPlayer(),
                s.studs(),
                s.studARating(), s.studBRating(), s.heartbeat(), s.light(), s.reason(), s.otherStation(), s.blocked(),
                s.blockRight(), s.blockUp(), s.blockFront(), s.blockingId(), s.regionX(), s.regionZ());
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        final CrownState s = crown;
        tooltip.add(FeGoggle.title(Component.translatable("block.create_stats.spark_crown")));
        if (s.reason() != CrownState.Reason.NONE && s.reason() != CrownState.Reason.NO_POWER) {
            tooltip.add(FeGoggle.bad(tr("goggle.blocked", reason(s.reason(), s.otherStation()).getString())));
        } else if (s.held() == 0) {
            tooltip.add(FeGoggle.bad(tr("goggle.dark", s.picked())));
        } else if (s.phase() == CrownHold.Phase.RIDING) {
            tooltip.add(FeGoggle.warn(tr("goggle.riding", s.held(), s.picked(),
                    String.format(java.util.Locale.ROOT, "%.1f", s.rideThroughSeconds()))));
        } else {
            tooltip.add(FeGoggle.ok(tr("goggle.holding", s.held(), s.picked())));
        }
        tooltip.add(FeGoggle.dim(tr("goggle.owner_total", s.ownerTotal(), s.maxPerPlayer())));
        tooltip.add(FeGoggle.draw(s.drawFePerTick()));

        tooltip.add(FeGoggle.supplyDemand(s.intakeFePerTick(),
                s.held() > 0 ? s.drawFePerTick() : s.pickedDrawFePerTick()));
        if (s.picked() < s.maxPerStation()) {
            tooltip.add(FeGoggle.dim(tr("goggle.next", fmt(s.nextPriceFePerTick()))));
        }
        if (s.held() > 0 && s.rideThroughSeconds() <= 0f) {
            tooltip.add(FeGoggle.warn(tr("goggle.store_none")));
        } else {
            tooltip.add(FeGoggle.dim(tr("goggle.store", String.format(java.util.Locale.ROOT, "%.1f",
                    s.held() > 0 ? s.rideThroughSeconds() : 0f), fmt(s.storedFe()), fmt(s.capacityFe()))));
        }
        if (s.held() == 0 && s.reason() == CrownState.Reason.NO_POWER) {
            tooltip.add(FeGoggle.dim(tr("goggle.comeback")));
        }
        if (s.held() > 0 && s.intakeFePerTick() < s.drawFePerTick() && s.phase() == CrownHold.Phase.RIDING) {
            tooltip.add(FeGoggle.warn(tr("goggle.short", fmt(s.drawFePerTick() - s.intakeFePerTick()))));
        }
        tooltip.add(FeGoggle.dim(tr("goggle.region", s.regionX(), s.regionZ(),
                CrownRegion.firstBlock(s.regionX()), CrownRegion.lastBlock(s.regionX()),
                CrownRegion.firstBlock(s.regionZ()), CrownRegion.lastBlock(s.regionZ()))));
        if (s.blocked()) {
            final BlockPos at = cellPos(new CrownPlan.Cell(s.blockRight(), s.blockUp(), s.blockFront()));
            tooltip.add(FeGoggle.warn(tr("goggle.no_room", nameOf(s.blockingId()), at.getX(), at.getY(), at.getZ())));
        }
        tooltip.add(FeGoggle.dim(tr("goggle.studs", studText(s.studAWired(), s.studARating()),
                studText(s.studBWired(), s.studBRating()))));
        boardGoggles(tooltip, s);
        return true;
    }

    private void boardGoggles(List<Component> tooltip, CrownState s) {
        final CrownBoard b = clientBoard;
        if (!b.present()) {
            return;
        }
        tooltip.add(FeGoggle.dim(tr("goggle.board", boardStud(b.legA(), b.liveA(), b.shareA(), s.studAWired()),
                boardStud(b.legB(), b.liveB(), b.shareB(), s.studBWired()))));
        if ((b.legA() >= 0 && !b.liveA()) || (b.legB() >= 0 && !b.liveB())) {
            tooltip.add(FeGoggle.warn(tr("goggle.board_open")));
        }
        final String carry = tr("goggle.board_carry", fmt(b.carried()), fmt(b.cost()));
        tooltip.add(b.carried() < b.cost() ? FeGoggle.warn(carry) : FeGoggle.dim(carry));
        if (b.reason() != PduReason.RUNNING && b.reason() != PduReason.WIRE_SHORT) {
            tooltip.add(FeGoggle.bad(tr("goggle.board_verdict", b.reason().description())));
        }
        if (b.covers() && s.picked() < s.maxPerStation()
                && (long) s.pickedDrawFePerTick() + s.nextPriceFePerTick() > b.carried()) {
            tooltip.add(FeGoggle.dim(tr("goggle.board_full")));
        }

        if (b.onBanks()) {
            tooltip.add(FeGoggle.warn(b.shortIn() >= 0
                    ? tr("goggle.supply_short", fmt(b.banks()), duration(b.shortIn()).getString())
                    : tr("goggle.supply_banks", fmt(b.banks()))));
        } else {
            tooltip.add(FeGoggle.dim(tr("goggle.supply_ok")));
        }
    }

    private static String boardStud(int leg, boolean live, int share, boolean wired) {
        if (leg < 0) {
            return wired ? tr("goggle.board_stud_off") : tr("goggle.stud_empty");
        }
        return live ? tr("goggle.board_stud", leg + 1, fmt(share)) : tr("goggle.board_stud_open", leg + 1);
    }

    private static Component studNames(CrownBoard b) {
        final String key = "block.create_stats.spark_crown.goggle.";
        if (b.legA() >= 0 && b.legB() >= 0 && b.legA() != b.legB()) {
            return Component.translatable(key + "board_pair", Math.min(b.legA(), b.legB()) + 1,
                    Math.max(b.legA(), b.legB()) + 1);
        }
        return Component.translatable(key + "board_one", Math.max(b.legA(), b.legB()) + 1);
    }

    private static String studText(boolean wired, int rating) {
        return wired ? fmt(rating) + " FE/t" : tr("goggle.stud_empty");
    }

    // components not strings, a dedicated server has no lang files
    private static Component reason(CrownState.Reason reason, long other) {
        final String key = "block.create_stats.spark_crown.reason.";
        return switch (reason) {
            case NO_OWNER -> Component.translatable(key + "no_owner");
            case REGION_TAKEN -> {
                final BlockPos p = BlockPos.of(other);
                yield Component.translatable(key + "region_taken", p.getX(), p.getY(), p.getZ());
            }
            case ON_SABLE -> Component.translatable(key + "on_sable");
            case SWITCHED_OFF -> Component.translatable(key + "switched_off");
            case NO_POWER -> Component.translatable(key + "no_power");
            default -> Component.empty();
        };
    }

    private static String nameOf(String id) {
        final ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null) {
            return id;
        }
        if (BuiltInRegistries.BLOCK.containsKey(rl)) {
            return BuiltInRegistries.BLOCK.get(rl).getName().getString();
        }
        if (BuiltInRegistries.ENTITY_TYPE.containsKey(rl)) {
            return BuiltInRegistries.ENTITY_TYPE.get(rl).getDescription().getString();
        }
        return id;
    }

    private static String tr(String key, Object... args) {
        return Component.translatable("block.create_stats.spark_crown." + key, args).getString();
    }

    private static String fmt(long v) {
        return String.format(java.util.Locale.ROOT, "%,d", v);
    }

    @Override
    public int ratedDrawFePerTick() {
        return block == CrownState.Reason.NONE ? CrownConfig.law().totalDraw(picks.size()) : 0;
    }

    @Override
    public int measuredDrawFePerTick() {
        return measuredDraw;
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
        sinceLook = LOOK_TICKS;
        setChanged();
    }

    @Override
    public void setManagedShed(boolean shed) {
        managed.setShed(shed);
    }

    @Override
    public boolean isManagedShed() {
        return managed.isShed();
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
        final CrownLaw law = CrownConfig.law();
        return (int) Math.min(Integer.MAX_VALUE, 2L * Math.max(law.totalDraw(1), law.totalDraw(picks.size())));
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

    @Override
    public void adoptNetwork(FeWireNetwork network) {
        this.network = network;

        this.adoptedHome = homeSlot();
    }

    @Override
    @Nullable
    public FeWireNetwork network() {
        return network;
    }

    @Override
    public void invalidateNetwork() {
        if (network != null) {
            network.markDirty();
            network = null;
        }
    }

    @Override
    public void acceptBoardFeed(FeStationFeed feed) {
        if (feed == null || level == null || level.isClientSide()) {
            return;
        }
        final long now = level.getGameTime();
        int slot = -1;
        for (int i = 0; i < boardFeeds.length; i++) {
            if (boardFeeds[i] != null && boardFeeds[i].board() == feed.board()) {
                slot = i;
                break;
            }
        }
        for (int i = 0; i < boardFeeds.length && slot < 0; i++) {
            if (boardFeeds[i] == null || !boardFeeds[i].fresh(now)) {
                slot = i;
            }
        }
        if (slot >= 0) {
            boardFeeds[slot] = feed;
        }
    }

    @Override
    @Nullable
    public BlockPos studFarEnd(int stud) {
        return stud == 0 || stud == 1 ? studLink[stud] : null;
    }

    private CrownBoard board(long now) {
        return CrownBoard.of(boardFeeds, now, studLink[0] != null, studLink[1] != null);
    }

    public CrownBoard clientBoard() {
        return clientBoard;
    }

    @Override
    public BlockPos terminalPos() {
        return worldPosition;
    }

    @Override
    public List<BlockPos> links() {
        return wireList;
    }

    @Override
    public WireTier tierOf(BlockPos link) {
        final int stud = studOf(link);
        return stud < 0 ? WireTier.COPPER : studTier[stud];
    }

    public int studOf(BlockPos far) {
        for (int i = 0; i < 2; i++) {
            if (far.equals(studLink[i])) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public int maxLinks() {
        return 2;
    }

    @Override
    public boolean feedsInParallel() {
        return true;
    }

    @Override
    @Nullable
    public BlockPos homeLink() {
        if (!studKindsRead && level != null && !level.isClientSide()) {
            readStudKindsOnly();
        }
        final int slot = homeSlot();
        return slot < 0 ? null : studLink[slot];
    }

    private int homeSlot() {
        return FeFeeds.homeSlot(studKind);
    }

    private void readStudKinds() {
        readStudKindsOnly();
        if (network != null && homeSlot() != adoptedHome) {
            invalidateNetwork();
        }
    }

    private void readStudKindsOnly() {
        if (level == null) {
            return;
        }
        studKindsRead = true;
        for (int i = 0; i < 2; i++) {
            if (studLink[i] == null) {
                studKind[i] = FeFeeds.UNWIRED;
            } else if (level.isLoaded(studLink[i])) {
                studKind[i] = kindAt(studLink[i]);
            }
        }
    }

    private int kindAt(BlockPos far) {
        final net.minecraft.world.level.block.entity.BlockEntity be = level.getBlockEntity(far);
        if (be instanceof FeBoardPeer) {
            return FeFeeds.BOARD;
        }
        if (be instanceof MainBreakerBE || be instanceof UniversalPowerCouplerBE) {
            return FeFeeds.SILENT;
        }
        if (be instanceof DiagnosticJunctionBE || be instanceof FeStudPanel) {
            return FeFeeds.PASS;
        }
        return be instanceof FeWireTerminal ? FeFeeds.DIRECT : FeFeeds.SILENT;
    }

    @Override
    public void addLink(BlockPos far, WireTier tier) {
        if (studOf(far) >= 0) {
            return;
        }
        final int stud = CrownStuds.nearerStud(CrownStuds.turnsFor(facing().getName()),
                far.getX() - worldPosition.getX(), far.getZ() - worldPosition.getZ(),
                studLink[0] == null, studLink[1] == null);
        if (stud < 0) {
            return;
        }
        studLink[stud] = far.immutable();
        studTier[stud] = tier == null ? WireTier.COPPER : tier;
        if (level != null && !level.isClientSide() && level.isLoaded(far)) {
            studKind[stud] = kindAt(far);
        }
        wiringChanged();
    }

    @Override
    public void removeDeadLink(BlockPos far) {
        final int stud = studOf(far);
        if (stud >= 0) {
            studLink[stud] = null;
            studTier[stud] = WireTier.COPPER;
            studKind[stud] = FeFeeds.UNWIRED;
            wiringChanged();
        }
    }

    @Override
    public void dropWiresAndUnlinkAll() {
        if (level == null || level.isClientSide()) {
            return;
        }
        final java.util.Map<WireTier, Integer> refund = new java.util.EnumMap<>(WireTier.class);
        for (int i = 0; i < 2; i++) {
            final BlockPos link = studLink[i];
            if (link == null) {
                continue;
            }
            refund.merge(studTier[i], 1, Integer::sum);
            if (level.getBlockEntity(link) instanceof com.createstats.power.FeStudPanel panel) {
                panel.forgetLinksTo(worldPosition);
            } else if (level.getBlockEntity(link) instanceof FeWireTerminal other && other != this) {
                other.removeDeadLink(worldPosition);
            }
            studLink[i] = null;
            studTier[i] = WireTier.COPPER;
            studKind[i] = FeFeeds.UNWIRED;
        }
        wiringChanged();
        for (java.util.Map.Entry<WireTier, Integer> e : refund.entrySet()) {
            net.minecraft.world.level.block.Block.popResource(level, worldPosition, new net.minecraft.world.item.ItemStack(
                    com.createstats.registry.ModItems.wireItem(e.getKey()), e.getValue()));
        }
    }

    private void wiringChanged() {
        rebuildWireList();
        setChanged();
        invalidateNetwork();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
        }
    }

    private void rebuildWireList() {
        final List<BlockPos> v = new ArrayList<>(2);
        for (BlockPos p : studLink) {
            if (p != null) {
                v.add(p);
            }
        }
        wireList = Collections.unmodifiableList(v);
    }

    @Override
    public void remove() {
        if (level instanceof ServerLevel server) {
            releaseAll(server);
            for (int i = 0; i < picks.size(); i++) {
                CrownTickets.release(server, worldPosition, picks.getLong(i));
            }
            CrownRegistry.get(server).remove(CrownRegistry.dimensionOf(server), worldPosition.asLong());
        }
        invalidateNetwork();
        super.remove();
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putInt("Tier", managed.priorityOrdinal());
        tag.putInt("Lock", managed.lockOrdinal());
        for (int i = 0; i < 2; i++) {
            if (studLink[i] != null) {
                tag.putLong("Stud" + i, studLink[i].asLong());
                tag.putInt("StudTier" + i, studTier[i].ordinal());
            }
        }
        if (owner != null) {
            tag.putUUID("Owner", owner);
        }
        tag.putString("OwnerName", ownerName);
        if (!clientPacket) {
            tag.putLongArray("Picks", picks.toLongArray());
            tag.putInt("Held", held);
            tag.putLong("Store", hold.stored());
            tag.putInt("Phase", hold.phase().ordinal());
            tag.putInt("Heartbeat", heartbeat);
        } else {
            tag.putLongArray("Crown", crown.pack());
            tag.putString("Blocking", crown.blockingId());

            tag.putLongArray("ClientPicks", picks.toLongArray());

            final CrownBoard b = level == null ? CrownBoard.NONE : board(level.getGameTime());
            if (b.present()) {
                tag.putIntArray("Board", b.pack());
            }
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);

        if (tag.contains("Tier")) {
            managed.load(tag.getInt("Tier"), tag.getInt("Lock"));
        }
        for (int i = 0; i < 2; i++) {
            studLink[i] = tag.contains("Stud" + i) ? BlockPos.of(tag.getLong("Stud" + i)) : null;
            studTier[i] = WireTier.byOrdinal(tag.getInt("StudTier" + i));
        }
        rebuildWireList();
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        ownerName = tag.getString("OwnerName");
        if (!clientPacket && tag.contains("Picks")) {
            picks.clear();
            for (long c : tag.getLongArray("Picks")) {
                picks.add(c);
            }
            held = Math.max(0, Math.min(tag.getInt("Held"), picks.size()));
            hold.restore(tag.getLong("Store"), CrownHold.Phase.of(tag.getInt("Phase")));
            heartbeat = tag.getInt("Heartbeat");
        }

        if (tag.contains("Crown")) {
            crown = CrownState.unpack(tag.getLongArray("Crown"), tag.getString("Blocking"));
        }
        if (clientPacket) {
            clientBoard = tag.contains("Board") ? CrownBoard.unpack(tag.getIntArray("Board")) : CrownBoard.NONE;
        }
        if (clientPacket && tag.contains("ClientPicks")) {
            picks.clear();
            for (long c : tag.getLongArray("ClientPicks")) {
                picks.add(c);
            }
        }
    }
}
