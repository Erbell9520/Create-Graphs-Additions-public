package com.createstats.blockentity;

import com.createstats.battle.BattleObserverSnapshot;
import com.createstats.battle.core.BattleReport;
import com.createstats.battle.net.BattleBridgeActionPacket;
import com.createstats.battle.world.BattleSounds;
import com.createstats.battle.world.DamageEvents;
import com.createstats.battle.world.LegacySnapshots;
import com.createstats.battle.world.RecordKey;
import com.createstats.battle.world.ShipRecord;
import com.createstats.battle.world.ShipRecords;
import com.createstats.block.BattleScreenDisplayBlock;
import com.createstats.flight.bridge.SableBridge;
import com.createstats.power.FeConsumer;
import com.createstats.power.FePowerFx;
import com.createstats.power.FeProfiles;
import com.createstats.power.IFeConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class BattleScreenDisplayBE extends BlockEntity implements IFeConsumer {
    private static final int ACTIONS_PER_SECOND = 20;

    public static final java.util.Set<BattleScreenDisplayBE> CLIENT_LOADED = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private final FeConsumer feConsumer = FeConsumer.forBlockEntity(this, FeProfiles.settings(FeProfiles.KEY_BATTLE_SCREEN));

    @Nullable
    private RecordKey recordKey;
    private long selfPos = Long.MIN_VALUE;
    private int selfFacing = -1;
    private final List<ItemStack> returned = new ArrayList<>();

    @Nullable
    private ShipRecord record;
    @Nullable
    private RecordKey keyCache;
    private long keyCheckedAt = Long.MIN_VALUE;
    private int builtFor = Integer.MIN_VALUE;
    private boolean builtPowered;
    private boolean builtMaster;
    private long lastSync = Long.MIN_VALUE / 2;
    private int ringSeen = -1;
    private int fireSeen = -1;
    private int comparator;
    private boolean wasPowered;
    private boolean wasMaster;

    private boolean activeDraw;
    private int lastSignal;
    private int targetDownSeen = -1;
    private int gunDownSeen = -1;
    private final Map<UUID, long[]> actionRate = new HashMap<>();

    private BattleReport report = BattleReport.empty("", true, false);

    public BattleScreenDisplayBE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public FeConsumer feConsumer() {
        return feConsumer;
    }

    public BattleReport report() {
        return report;
    }

    @Nullable
    public ShipRecord record() {
        return record;
    }

    public int comparatorOutput() {
        return comparator;
    }

    public void serverTick() {
        if (!(level instanceof ServerLevel sl)) return;
        long now = sl.getGameTime();
        DamageEvents.ensureSplitListener();

        boolean powered = feConsumer.serviceTick(activeDraw);
        FePowerFx.handleTransition(level, worldPosition, feConsumer);
        BlockState state = getBlockState();
        if (state.hasProperty(BattleScreenDisplayBlock.ACTIVE) && state.getValue(BattleScreenDisplayBlock.ACTIVE) != powered) {
            level.setBlock(worldPosition, state.setValue(BattleScreenDisplayBlock.ACTIVE, powered), Block.UPDATE_ALL);
            state = getBlockState();
        }

        RecordKey key = currentKey(sl, now);
        if (record == null || !key.equals(recordKey)) attach(sl, key, now);
        ShipRecord r = record;
        if (r == null) return;
        if (now % 100 == 0) r.touch(now);

        long self = worldPosition.asLong();
        boolean lostPower = wasPowered && !powered;
        if (powered) r.election().checkIn(self, now);
        else r.election().leave(self);
        wasPowered = powered;
        if (r.election().update(now)) {
            byte why = lostPower ? ShipRecord.LOG_SAFE_POWER
                    : r.freshlyLoaded() ? ShipRecord.LOG_SAFE_LOAD : ShipRecord.LOG_SAFE_NEW_MASTER;
            r.clearFreshlyLoaded();
            r.onHandover(now, why);
        }
        boolean master = r.election().isMaster(self);
        activeDraw = false;
        if (master && r.claimWork(now)) {
            r.work(sl, worldPosition, facing(), now);
            Vec3 world = SableBridge.worldPointOf(this, Vec3.atCenterOf(worldPosition));
            activeDraw = r.fireControl(sl, worldPosition, world, powered, now);
            if (now % 10 == 0) r.sendWatch(sl, now);
        }

        if (wasMaster && !master) r.letGoAll(sl);
        wasMaster = master;
        redstone(sl, r, now);

        if (powered) {
            if (ringSeen != r.ringSeq()) {
                if (ringSeen >= 0) BattleSounds.bell(level, worldPosition);
                ringSeen = r.ringSeq();
            }
            if (fireSeen != r.fireToneSeq()) {
                if (fireSeen >= 0) BattleSounds.fire(level, worldPosition);
                fireSeen = r.fireToneSeq();
            }
            if (targetDownSeen != r.targetDownSeq()) {
                if (targetDownSeen >= 0) BattleSounds.targetDown(level, worldPosition);
                targetDownSeen = r.targetDownSeq();
            }
            if (gunDownSeen != r.gunDownSeq()) {
                if (gunDownSeen >= 0) BattleSounds.gunDown(level, worldPosition);
                gunDownSeen = r.gunDownSeq();
            }
        }

        boolean stale = r.reportVersion() != builtFor || powered != builtPowered || master != builtMaster;
        if (stale && (now - lastSync >= 5 || r.lastHitAt() == now)) {
            report = r.report(powered, master, now);
            builtFor = r.reportVersion();
            builtPowered = powered;
            builtMaster = master;
            lastSync = now;
            setChanged();
            sl.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
        }

        if (now % 10 == 0) {
            int c = r.comparator(now);
            if (c != comparator) {
                comparator = c;
                level.updateNeighbourForOutputSignal(worldPosition, state.getBlock());
            }
        }
    }

    private void redstone(ServerLevel sl, ShipRecord r, long now) {
        int signal = sl.getBestNeighborSignal(worldPosition);
        if (r.redstoneIn() == 0) {
            if (signal > 0 && lastSignal == 0) r.requestSalvo(null, "redstone");
        } else if ((signal > 0) != r.weaponsFree()) {
            r.setWeapons(signal > 0, "redstone", now);
        }
        lastSignal = signal;
    }

    private RecordKey currentKey(ServerLevel sl, long now) {
        if (keyCache != null && now - keyCheckedAt < 20) return keyCache;
        keyCheckedAt = now;
        Object sub = SableBridge.subLevelAccessOf(this);
        UUID id = sub == null ? null : SableBridge.subLevelId(sub);
        keyCache = id != null ? RecordKey.ship(id, sl.dimension()) : RecordKey.ground(sl.dimension(), worldPosition);
        return keyCache;
    }

    private void attach(ServerLevel sl, RecordKey key, long now) {
        ShipRecord old = recordKey != null && !recordKey.equals(key) ? ShipRecords.existing(sl, recordKey) : null;
        if (record != null) record.election().leave(worldPosition.asLong());
        record = ShipRecords.getOrCreate(sl, key, worldPosition);
        if (old != null) {
            old.election().leave(selfPos);
            if (record.store().isEmpty() && record.log().isEmpty()) {
                int q = selfFacing < 0 ? 0 : Math.floorMod(facing().get2DDataValue() - selfFacing, 4);
                record.carryFrom(old, q, now);
            }
        }
        recordKey = key;
        selfPos = worldPosition.asLong();
        selfFacing = facing().get2DDataValue();
        builtFor = Integer.MIN_VALUE;
        setChanged();
    }

    private Direction facing() {
        BlockState state = getBlockState();
        return state.hasProperty(BattleScreenDisplayBlock.FACING) ? state.getValue(BattleScreenDisplayBlock.FACING) : Direction.NORTH;
    }

    public void handleAction(ServerPlayer player, BattleBridgeActionPacket packet) {
        if (!(level instanceof ServerLevel sl) || record == null) return;
        if (!SableBridge.isPlayerInInteractionRangeFor(this, player)) return;
        long now = sl.getGameTime();
        long[] rate = actionRate.computeIfAbsent(player.getUUID(), k -> new long[]{now, 0});
        if (now - rate[0] >= 20) {
            rate[0] = now;
            rate[1] = 0;
        }
        if (++rate[1] > ACTIONS_PER_SECOND) return;
        ShipRecord r = record;
        switch (packet.action()) {
            case BattleBridgeActionPacket.WATCH -> {
                boolean open = packet.a() == 1;
                if (open && !r.watching(player.getUUID())) {
                    giveReturned(player);
                    r.wantContacts();
                }
                r.watch(player.getUUID(), open, now);
            }
            case BattleBridgeActionPacket.DESIGNATE -> r.designateFromConsole(sl, player, Math.max(0, Math.min(2, packet.a())),
                    packet.b(), packet.s(), now);
            case BattleBridgeActionPacket.CLEAR_TARGET -> r.clearTarget(player.getGameProfile().getName(), now);
            case BattleBridgeActionPacket.SET_MODE -> r.setGunMode(packet.a(), packet.b(), now);
            case BattleBridgeActionPacket.SET_WEAPONS -> {
                if (r.redstoneIn() != 1) r.setWeapons(packet.a() == 1, player.getGameProfile().getName(), now);
            }
            case BattleBridgeActionPacket.FIRE_READY -> r.requestSalvo(player, player.getGameProfile().getName());
            case BattleBridgeActionPacket.RENAME_GUN -> r.renameGun(packet.a(), packet.s());
            case BattleBridgeActionPacket.RELEARN_AIM -> r.relearnAim(packet.a());
            case BattleBridgeActionPacket.SET_ARC -> r.setGunArc(packet.a(), packet.b());
            case BattleBridgeActionPacket.REQUEST_SHAPE -> PacketDistributor.sendToPlayer(player, r.shapePacket());
            case BattleBridgeActionPacket.REQUEST_DETAIL -> PacketDistributor.sendToPlayer(player, r.detailPacket(worldPosition, now));
            case BattleBridgeActionPacket.ACCEPT_SHAPE -> r.acceptShape(now);
            case BattleBridgeActionPacket.ACCEPT_PLACE -> r.acceptPlace(packet.a(), now);
            case BattleBridgeActionPacket.ACCEPT_REFIT -> r.acceptBanner(now, packet.a() == 1);
            case BattleBridgeActionPacket.TURN_BOW -> r.turnBow(now);
            case BattleBridgeActionPacket.SET_GROUND_SIZE -> r.setGroundSize(packet.a());
            case BattleBridgeActionPacket.SET_OPTION -> r.setOption(packet.a(), packet.b());
            default -> {
            }
        }
    }

    private void giveReturned(ServerPlayer player) {
        if (returned.isEmpty()) return;
        int count = 0;
        for (ItemStack stack : returned) {
            count += stack.getCount();
            ItemStack copy = stack.copy();
            if (!player.getInventory().add(copy) && !copy.isEmpty()) player.drop(copy, false);
        }
        returned.clear();
        setChanged();
        player.sendSystemMessage(Component.translatable("battle.create_stats.frequencies_back", count));
    }

    public List<BattleObserverSnapshot> getBattleFeedSnapshots() {
        long now = level == null ? 0L : level.getGameTime();
        String dim = level == null ? "" : level.dimension().location().toString();
        return LegacySnapshots.from(report, worldPosition, dim, now);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && level.isClientSide) CLIENT_LOADED.add(this);
    }

    @Override
    public void setRemoved() {
        leave();
        CLIENT_LOADED.remove(this);
        super.setRemoved();
    }

    @Override
    public void onChunkUnloaded() {
        leave();
        CLIENT_LOADED.remove(this);
        super.onChunkUnloaded();
    }

    private void leave() {
        if (record == null || !(level instanceof ServerLevel sl)) return;

        if (record.election().isMaster(worldPosition.asLong())) record.letGoAll(sl);
        record.election().leave(worldPosition.asLong());
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        feConsumer.save(tag);
        if (recordKey != null) tag.put("RecordKey", recordKey.save());
        tag.putLong("SelfPos", selfPos);
        tag.putByte("SelfFacing", (byte) selfFacing);
        tag.putBoolean("MigratedV2", true);
        if (!returned.isEmpty()) {
            ListTag list = new ListTag();
            for (ItemStack s : returned) {
                if (!s.isEmpty()) list.add(s.save(registries));
            }
            tag.put("Returned", list);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("Report", Tag.TAG_BYTE_ARRAY)) {
            BattleReport decoded = BattleReport.decode(tag.getByteArray("Report"));
            if (decoded != null) report = decoded;
            return;
        }
        feConsumer.load(tag);
        recordKey = RecordKey.load(tag.getCompound("RecordKey"));
        selfPos = tag.contains("SelfPos") ? tag.getLong("SelfPos") : Long.MIN_VALUE;
        selfFacing = tag.contains("SelfFacing") ? tag.getByte("SelfFacing") : -1;
        returned.clear();
        ListTag list = tag.getList("Returned", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            ItemStack s = ItemStack.parseOptional(registries, list.getCompound(i));
            if (!s.isEmpty()) returned.add(s);
        }
        if (!tag.getBoolean("MigratedV2")) migrateV1(tag, registries);
    }

    private void migrateV1(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag configs = tag.getList("LinkedControllerConfigs", Tag.TAG_COMPOUND);
        for (int i = 0; i < configs.size(); i++) {
            CompoundTag inv = configs.getCompound(i).getCompound("Inventory");
            if (inv.isEmpty()) continue;
            ItemStackHandler handler = new ItemStackHandler();
            try {
                handler.deserializeNBT(registries, inv);
            } catch (RuntimeException e) {
                continue;
            }
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                ItemStack s = handler.getStackInSlot(slot);
                if (!s.isEmpty()) returned.add(s.copy());
            }
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putByteArray("Report", report.encode());
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
