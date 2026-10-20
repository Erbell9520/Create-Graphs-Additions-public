package com.createstats.blockentity;

import com.createstats.battle.core.BattleSystem;
import com.createstats.battle.core.ComparatorScale;
import com.createstats.battle.core.PackedPos;
import com.createstats.battle.core.SurveyStore;
import com.createstats.battle.world.BlockInfo;
import com.createstats.battle.world.ShipRecord;
import com.createstats.battle.world.ShipRecords;
import com.createstats.flight.bridge.SableBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

public class DamageObserverBE extends BlockEntity {
    public static final int MIN_COVER = 1;
    public static final int MAX_COVER = 8;
    public static final int DEFAULT_COVER = 4;
    public static final int MAX_NAME = 24;

    private String name = "";
    private int cover = DEFAULT_COVER;

    private String legacyWeaponName = "";

    private int permille = -1;
    private long lastLossAt = -1L;
    private boolean attached;
    private String reportsTo = "";
    private int comparator;

    private final SurveyStore alone = new SurveyStore();
    private boolean aloneReady;
    private int lastIntact = -1;

    public DamageObserverBE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public String name() {
        return name;
    }

    public int cover() {
        return cover;
    }

    public int permille() {
        return permille;
    }

    public long lastLossAt() {
        return lastLossAt;
    }

    public boolean attached() {
        return attached;
    }

    public String reportsTo() {
        return reportsTo;
    }

    public int comparatorOutput() {
        return comparator;
    }

    public String legacyWeaponName() {
        return legacyWeaponName;
    }

    public void configure(String newName, int newCover) {
        String n = newName == null ? "" : newName.strip();
        if (n.length() > MAX_NAME) n = n.substring(0, MAX_NAME);
        int c = Math.max(MIN_COVER, Math.min(MAX_COVER, newCover));
        if (n.equals(name) && c == cover) return;
        if (c != cover) aloneReady = false;
        name = n;
        cover = c;
        sync();
    }

    public void serverTick() {
        if (!(level instanceof ServerLevel sl)) return;
        long now = sl.getGameTime();
        if (Math.floorMod(now + worldPosition.hashCode(), 20L) != 0) return;
        ShipRecord r = record(sl);
        boolean wasAttached = attached;
        String wasReports = reportsTo;
        int intact;
        int total;
        if (r != null && r.election().hasMaster()) {
            attached = true;
            reportsTo = r.ground() ? "" : r.shipName();
            r.registerMarker(worldPosition, name, cover, now);
            int[] c = r.cube(worldPosition, cover);
            intact = c[0];
            total = c[1];
            aloneReady = false;
        } else {
            attached = false;
            reportsTo = "";
            int[] c = checkAlone(sl, now);
            intact = c[0];
            total = c[1];
        }
        int newPermille = total == 0 ? -1 : (int) (intact * 1000L / total);
        boolean changed = newPermille != permille || wasAttached != attached || !wasReports.equals(reportsTo);
        if (lastIntact >= 0 && intact < lastIntact && total > 0) {
            lastLossAt = now;
            changed = true;
        }
        lastIntact = intact;
        permille = newPermille;
        int cmp = ComparatorScale.marker(intact, total);
        if (cmp != comparator) {
            comparator = cmp;
            sl.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
        }
        if (changed) sync();
    }

    @Nullable
    private ShipRecord record(ServerLevel sl) {
        Object sub = SableBridge.subLevelAccessOf(this);
        UUID id = sub == null ? null : SableBridge.subLevelId(sub);
        if (id != null) return ShipRecords.loadedShip(sl, id);
        List<ShipRecord> ground = ShipRecords.groundAt(sl, worldPosition);
        return ground.isEmpty() ? null : ground.get(0);
    }

    private int[] checkAlone(ServerLevel sl, long now) {
        if (!aloneReady) {
            alone.clear();
            for (int dx = -cover; dx <= cover; dx++) {
                for (int dy = -cover; dy <= cover; dy++) {
                    for (int dz = -cover; dz <= cover; dz++) {
                        BlockPos p = worldPosition.offset(dx, dy, dz);
                        BlockState st = stateAt(sl, p);
                        if (st == null || !BlockInfo.counts(st, false)) continue;
                        alone.add(PackedPos.pack(dx, dy, dz), BlockInfo.id(st.getBlock()), BattleSystem.HULL);
                    }
                }
            }
            alone.setTaken(now);
            aloneReady = true;
            return new int[]{alone.size(), alone.size()};
        }
        for (int slot = 0; slot < alone.size(); slot++) {
            int packed = alone.packedAt(slot);
            BlockState st = stateAt(sl, worldPosition.offset(PackedPos.dx(packed), PackedPos.dy(packed), PackedPos.dz(packed)));
            if (st == null) continue;
            if (BlockInfo.isGone(st)) {
                alone.markLost(slot, now, SurveyStore.CAUSE_HIT);
            } else if (alone.isLost(slot)) {
                alone.putBack(slot, BlockInfo.id(st.getBlock()), BattleSystem.HULL);
            }
        }
        return new int[]{alone.intactCount(), alone.size()};
    }

    @Nullable
    private static BlockState stateAt(ServerLevel sl, BlockPos p) {
        LevelChunk c = sl.getChunkSource().getChunk(p.getX() >> 4, p.getZ() >> 4, false);
        return c == null ? null : c.getBlockState(p);
    }

    private void sync() {
        setChanged();
        if (level != null && !level.isClientSide) {
            BlockState st = getBlockState();
            level.sendBlockUpdated(worldPosition, st, st, Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("Name", name);
        tag.putByte("Cover", (byte) cover);
        if (!legacyWeaponName.isEmpty()) tag.putString("LegacyWeaponName", legacyWeaponName);
        tag.putBoolean("MigratedV2", true);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("Permille")) {
            name = tag.getString("Name");
            cover = tag.getByte("Cover");
            permille = tag.getInt("Permille");
            lastLossAt = tag.getLong("LastLoss");
            attached = tag.getBoolean("Attached");
            reportsTo = tag.getString("ReportsTo");
            return;
        }
        if (!tag.getBoolean("MigratedV2") && tag.contains("Settings", Tag.TAG_COMPOUND)) {
            migrateV1(tag.getCompound("Settings"));
            return;
        }
        name = tag.getString("Name");
        int c = tag.contains("Cover") ? tag.getByte("Cover") : DEFAULT_COVER;
        cover = Math.max(MIN_COVER, Math.min(MAX_COVER, c));
        legacyWeaponName = tag.getString("LegacyWeaponName");
    }

    private void migrateV1(CompoundTag settings) {
        String n = settings.getString("ObserverName");
        name = n.length() > MAX_NAME ? n.substring(0, MAX_NAME) : n;
        int radius = settings.contains("AreaRadius") ? settings.getInt("AreaRadius") : DEFAULT_COVER;
        cover = Math.max(MIN_COVER, Math.min(MAX_COVER, radius <= 0 ? DEFAULT_COVER : radius));
        if ("WEAPONS".equals(settings.getString("Category"))) legacyWeaponName = name;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putString("Name", name);
        tag.putByte("Cover", (byte) cover);
        tag.putInt("Permille", permille);
        tag.putLong("LastLoss", lastLossAt);
        tag.putBoolean("Attached", attached);
        tag.putString("ReportsTo", reportsTo);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
