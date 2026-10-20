package com.createstats.battle.world;

import com.createstats.battle.core.BattleReport;
import com.createstats.battle.core.BattleSystem;
import com.createstats.battle.core.BellRules;
import com.createstats.battle.core.ComparatorScale;
import com.createstats.battle.core.GunArc;
import com.createstats.battle.core.GunState;
import com.createstats.battle.core.MasterElection;
import com.createstats.battle.core.PackedPos;
import com.createstats.battle.core.PlaceName;
import com.createstats.battle.core.PlaceNamer;
import com.createstats.battle.core.Places;
import com.createstats.battle.core.RefitRules;
import com.createstats.battle.core.ShipFrame;
import com.createstats.battle.core.SurveyStore;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntLinkedOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class ShipRecord extends SavedData {
    public static final int FORMAT = 1;
    public static final int MAX_LOG = 32;
    public static final int MAX_MARKERS = 32;

    public static final long HIT_WINDOW = 20L;

    public static final long PENDING_TICKS = 60L;

    public static final byte LOG_REFIT = 1;
    public static final byte LOG_SAFE_NEW_MASTER = 2;
    public static final byte LOG_SAFE_LOAD = 3;
    public static final byte LOG_SAFE_POWER = 4;
    public static final byte LOG_ACCEPT_PLACE = 5;
    public static final byte LOG_ACCEPT_SHAPE = 6;
    public static final byte LOG_ACCEPT_REFIT = 7;
    public static final byte LOG_BOW = 8;
    public static final byte LOG_CARRIED = 9;
    public static final byte LOG_BROKE_AWAY = 10;
    public static final byte LOG_DESIGNATE = 11;
    public static final byte LOG_TARGET_DOWN = 12;
    public static final byte LOG_TARGET_GONE = 13;
    public static final byte LOG_TARGET_LOST = 14;
    public static final byte LOG_TARGET_DROPPED = 15;
    public static final byte LOG_CLEAR_TARGET = 16;
    public static final byte LOG_WEAPONS = 17;
    public static final byte LOG_SALVO = 18;
    public static final byte LOG_GUN_DOWN = 19;
    public static final byte LOG_MODE = 20;

    public static final int MAX_GUNS = 32;
    // stops sending to a crashed client
    public static final long WATCH_TICKS = 200L;

    private RecordKey key;
    private long origin;
    final SurveyStore store = new SurveyStore();
    final Places places = new Places();
    int bow;
    int bowSource = BattleReport.BOW_PLACER;
    boolean bowDecided;
    int groundSize = 33;
    int options = BattleReport.OPT_BELL | BattleReport.OPT_LIGHTS;
    int redstoneIn;
    int comparatorMode = ComparatorScale.MODE_INTEGRITY;
    UUID controlId = UUID.randomUUID();
    long seen;
    int shapeVersion;
    int lostVersion;
    long lastHitAt = -1L;
    long lastBlastAt = -1L;
    final List<HitAcc> hits = new ArrayList<>();
    final List<LogEntry> log = new ArrayList<>();
    String refitBy = "";
    int refitCount;
    long refitAt = -1L;
    int newBlocks = -1;
    final IntArrayList bannerCells = new IntArrayList();
    boolean capped;
    final List<Prop> props = new ArrayList<>();
    boolean lightsCleared;
    String shipName = "";
    int bridgeDy;
    boolean weaponsFree;

    final List<GunRecord> guns = new ArrayList<>();

    @Nullable TargetFinder.Target target;

    final IntOpenHashSet firing = new IntOpenHashSet();

    final MasterElection election = new MasterElection();

    boolean freshlyLoaded;
    final BellRules bell = new BellRules();

    int ringSeq;
    int fireToneSeq;
    long lastFireTone = Long.MIN_VALUE / 2;
    long lastWorkTick = Long.MIN_VALUE;
    int reportVersion;

    final Int2ObjectOpenHashMap<Pending> pending = new Int2ObjectOpenHashMap<>();

    final IntLinkedOpenHashSet queued = new IntLinkedOpenHashSet();

    final Map<Long, MarkerInfo> markers = new LinkedHashMap<>();

    final Int2LongOpenHashMap fireTouched = new Int2LongOpenHashMap();
    boolean fireAboard;
    String firePlace = "";

    boolean playerAboard;
    int blastSeq;
    @Nullable ShipScanner.SurveyJob survey;
    @Nullable ShipScanner.GrowthJob growth;
    long nextGrowthAt;
    @Nullable ShipShape shape;
    int shapeBuiltFor = -1;
    boolean surveyWanted;

    final Map<Integer, int[]> separationWindow = new HashMap<>();

    final Set<UUID> ownBodies = new HashSet<>();

    final Map<String, double[]> contactHistory = new HashMap<>();
    List<TargetFinder.Contact> contacts = List.of();
    BattleReport.TargetLine targetLine = BattleReport.TargetLine.NONE;

    boolean firingCleared;
    boolean salvoWanted;
    @Nullable net.minecraft.server.level.ServerPlayer salvoCaller;
    String salvoBy = "";
    long salvoAt = -1L;
    String salvo = "";

    @Nullable net.minecraft.server.level.ServerPlayer designationCaller;
    long designationAt;
    long nextGunScan;
    boolean gunScanWanted = true;

    int targetDownSeq;
    int gunDownSeq;
    int designateSeq;
    long bridgeAt = Long.MIN_VALUE;
    final double[] bridgeLast = new double[3];
    final double[] bridgeVel = new double[3];
    private int[] refBounds;
    private int refBoundsFor = -1;

    boolean contactsDue;

    final Map<UUID, Long> watchers = new HashMap<>();

    private net.minecraft.world.level.block.Block[] paletteBlocks = new net.minecraft.world.level.block.Block[0];

    @Nullable
    net.minecraft.world.level.block.Block paletteBlock(int index) {
        if (index >= paletteBlocks.length) {
            paletteBlocks = java.util.Arrays.copyOf(paletteBlocks, Math.max(index + 1, store.paletteSize()));
        }
        net.minecraft.world.level.block.Block b = paletteBlocks[index];
        if (b == null) {
            b = BlockInfo.block(store.paletteId(index));
            paletteBlocks[index] = b;
        }
        return b;
    }

    void resetPaletteBlocks() {
        paletteBlocks = new net.minecraft.world.level.block.Block[0];
    }

    public record Prop(int number, int packed) {
    }

    public record MarkerInfo(String name, int cover, long seen) {
    }

    record Pending(byte cause, long expires, String who, int group) {
    }

    public record LogEntry(long t, byte kind, int a, int b, String s) {
    }

    static final class HitAcc {
        long at;
        String place;
        int count;
        final Map<String, Integer> blocks = new HashMap<>();
        long lastAdd;
        int group;

        boolean told;

        String what() {
            String best = "";
            int n = 0;
            for (Map.Entry<String, Integer> e : blocks.entrySet()) {
                if (e.getValue() > n) {
                    n = e.getValue();
                    best = e.getKey();
                }
            }
            return n * 2 >= count ? best : "";
        }
    }

    ShipRecord(RecordKey key, long origin) {
        this.key = key;
        this.origin = origin;
    }

    public static SavedData.Factory<ShipRecord> factory(RecordKey key, BlockPos origin) {
        return new SavedData.Factory<>(() -> new ShipRecord(key, origin.asLong()), ShipRecord::load, null);
    }

    public RecordKey key() {
        return key;
    }

    public BlockPos origin() {
        return BlockPos.of(origin);
    }

    public boolean ground() {
        return key.ground();
    }

    public ShipFrame frame() {
        return ground() ? ShipFrame.ground() : new ShipFrame(bow);
    }

    public UUID controlId() {
        return controlId;
    }

    public MasterElection election() {
        return election;
    }

    public SurveyStore store() {
        return store;
    }

    public Places places() {
        return places;
    }

    public int pack(BlockPos p) {
        int dx = p.getX() - BlockPos.getX(origin);
        int dy = p.getY() - BlockPos.getY(origin);
        int dz = p.getZ() - BlockPos.getZ(origin);
        return PackedPos.fits(dx, dy, dz) ? PackedPos.pack(dx, dy, dz) : -1;
    }

    public int pack(int x, int y, int z) {
        int dx = x - BlockPos.getX(origin);
        int dy = y - BlockPos.getY(origin);
        int dz = z - BlockPos.getZ(origin);
        return PackedPos.fits(dx, dy, dz) ? PackedPos.pack(dx, dy, dz) : -1;
    }

    boolean intactAt(int x, int y, int z) {
        int packed = pack(x, y, z);
        if (packed < 0) return false;
        int slot = store.slotOf(packed);
        return slot >= 0 && !store.isLost(slot);
    }

    @Nullable
    int[] refBounds() {
        if (store.isEmpty()) return null;
        if (refBounds == null || refBoundsFor != shapeVersion) {
            int[] b = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
            int ox = BlockPos.getX(origin);
            int oy = BlockPos.getY(origin);
            int oz = BlockPos.getZ(origin);
            for (int slot = 0; slot < store.size(); slot++) {
                int packed = store.packedAt(slot);
                int x = ox + PackedPos.dx(packed);
                int y = oy + PackedPos.dy(packed);
                int z = oz + PackedPos.dz(packed);
                if (x < b[0]) b[0] = x;
                if (y < b[1]) b[1] = y;
                if (z < b[2]) b[2] = z;
                if (x > b[3]) b[3] = x;
                if (y > b[4]) b[4] = y;
                if (z > b[5]) b[5] = z;
            }
            refBounds = b;
            refBoundsFor = shapeVersion;
        }
        return refBounds;
    }

    public BlockPos unpack(int packed) {
        return new BlockPos(BlockPos.getX(origin) + PackedPos.dx(packed), BlockPos.getY(origin) + PackedPos.dy(packed),
                BlockPos.getZ(origin) + PackedPos.dz(packed));
    }

    public boolean inGroundBox(BlockPos p) {
        int half = groundSize / 2;
        return Math.abs(p.getX() - BlockPos.getX(origin)) <= half && Math.abs(p.getY() - BlockPos.getY(origin)) <= half
                && Math.abs(p.getZ() - BlockPos.getZ(origin)) <= half;
    }

    void changed() {
        reportVersion++;
        setDirty();
    }

    void lostChanged() {
        lostVersion++;
        changed();
    }

    void shapeChanged() {
        shapeVersion++;
        shape = null;
        changed();
    }

    public int reportVersion() {
        return reportVersion;
    }

    public int shapeVersion() {
        return shapeVersion;
    }

    public int lostVersion() {
        return lostVersion;
    }

    public void touch(long now) {
        if (seen != now) {
            seen = now;
            setDirty();
        }
    }

    public void onBlast(BlockPos p, int group, long now) {
        int packed = pack(p);
        if (packed < 0 || store.slotOf(packed) < 0) return;
        pending.put(packed, new Pending(SurveyStore.CAUSE_BLAST, now + PENDING_TICKS, "", group));
        queued.add(packed);
        lastBlastAt = now;
    }

    public int nextBlastGroup() {
        return ++blastSeq;
    }

    public void onPlayerBreak(BlockPos p, String who, long now) {
        int packed = pack(p);
        if (packed < 0 || store.slotOf(packed) < 0) return;
        byte cause = RefitRules.isRefit(now, lastHitAt) ? ShipScanner.CAUSE_REFIT : SurveyStore.CAUSE_PLAYER;
        pending.put(packed, new Pending(cause, now + PENDING_TICKS, who == null ? "" : who, 0));
        queued.add(packed);
    }

    public void onPlace(BlockPos p, @Nullable String player, long now) {
        int packed = pack(p);
        if (packed < 0) return;
        if (store.slotOf(packed) < 0) {
            if (player == null) return;
            pending.put(packed, new Pending(ShipScanner.CAUSE_ADD, now + PENDING_TICKS, player, 0));
        }
        queued.add(packed);
    }

    public void onSplit(List<BlockPos> positions, long now) {
        int group = nextBlastGroup();
        for (BlockPos p : positions) {
            int packed = pack(p);
            if (packed < 0 || store.slotOf(packed) < 0) continue;
            pending.put(packed, new Pending(SurveyStore.CAUSE_SEPARATED, now + PENDING_TICKS, "", group));
            queued.add(packed);
        }
    }

    @Nullable
    Pending takePending(int packed, long now) {
        Pending p = pending.remove(packed);
        return p == null || p.expires() < now ? null : p;
    }

    void expirePending(long now) {
        if (pending.isEmpty()) return;
        pending.int2ObjectEntrySet().removeIf(e -> e.getValue().expires() < now);
    }

    void loss(int slot, byte cause, long now, int group, String blockId) {
        int packed = store.packedAt(slot);
        boolean peace = RefitRules.isRefit(now, lastBlastAt);
        if (!store.markLost(slot, now, cause)) return;
        byte kind = cause == SurveyStore.CAUSE_SEPARATED ? Places.KIND_BROKE_AWAY : Places.KIND_DAMAGE;
        Places.Place place = places.add(packed, kind);

        if (cause == SurveyStore.CAUSE_HIT && peace && playerAboard) bannerCells.add(packed);
        if (cause == SurveyStore.CAUSE_BLAST || cause == SurveyStore.CAUSE_HIT || cause == SurveyStore.CAUSE_SEPARATED) {
            boolean firstOfHit = countHit(place, cause == SurveyStore.CAUSE_HIT ? -place.id() : group, now, blockId);
            lastHitAt = now;
            if (firstOfHit && cause != SurveyStore.CAUSE_SEPARATED && (options & BattleReport.OPT_BELL) != 0) bell.hit(now);
        }
        if (cause == SurveyStore.CAUSE_HIT) {
            int[] w = separationWindow.computeIfAbsent(place.id(), k -> new int[]{0, (int) now});
            w[0]++;
        }
        lostChanged();
    }

    void putBack(int slot, String blockId, BattleSystem system) {
        places.remove(store.packedAt(slot));
        store.putBack(slot, blockId, system);
        bannerCells.rem(store.packedAt(slot));
        lostChanged();
    }

    private boolean countHit(Places.Place place, int group, long now, String blockId) {
        for (HitAcc h : hits) {
            if (h.group == group && now - h.lastAdd <= HIT_WINDOW) {
                h.count++;
                h.blocks.merge(blockId, 1, Integer::sum);
                h.lastAdd = now;
                return false;
            }
        }
        HitAcc h = new HitAcc();
        h.at = now;
        h.place = place.name().isEmpty() ? "" : place.name();
        h.count = 1;
        h.blocks.put(blockId, 1);
        h.lastAdd = now;
        h.group = group;
        hits.add(0, h);
        while (hits.size() > BattleReport.MAX_HITS) hits.remove(hits.size() - 1);
        return true;
    }

    // only hand out closed hits, otherwise it says "1 block lost" for a shell that took 20
    List<HitAcc> closedHits(long now) {
        List<HitAcc> out = new ArrayList<>(2);
        for (HitAcc h : hits) {
            if (!h.told && now - h.lastAdd > HIT_WINDOW) {
                h.told = true;
                out.add(h);
            }
        }
        return out;
    }

    void nameDirty() {
        places.nameDirty(namer());
        for (HitAcc h : hits) {
            if (!h.place.isEmpty()) continue;
            for (Places.Place p : places.open()) {
                if (h.group == -p.id() || !p.name().isEmpty()) {
                    h.place = p.name();
                    break;
                }
            }
        }
    }

    PlaceNamer namer() {
        ShipShape s = shape();
        List<PlaceNamer.Marker> ms = new ArrayList<>();
        for (Map.Entry<Long, MarkerInfo> e : markers.entrySet()) {
            int packed = pack(BlockPos.of(e.getKey()));
            if (packed < 0) continue;
            ms.add(new PlaceNamer.Marker(e.getValue().name(), PackedPos.dx(packed), PackedPos.dy(packed), PackedPos.dz(packed), e.getValue().cover()));
        }
        List<PlaceNamer.Part> ps = new ArrayList<>();
        for (Prop p : props) ps.add(new PlaceNamer.Part(p.number(), new int[]{p.packed()}));
        return new PlaceNamer(ground(), frame(), s.minFore, s.maxFore, s.minStbd, s.maxStbd, bridgeDy, ms, List.of(), ps,
                c -> {
                    int slot = store.slotOf(c);
                    return slot >= 0 && store.systemAt(slot) == BattleSystem.LIFT;
                });
    }

    void checkSeparation(long now) {
        if (separationWindow.isEmpty()) return;
        Iterator<Map.Entry<Integer, int[]>> it = separationWindow.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, int[]> e = it.next();
            int[] w = e.getValue();
            if (now - w[1] < HIT_WINDOW * 2) continue;
            it.remove();
            boolean blastNearby = lastBlastAt >= 0 && now - lastBlastAt < HIT_WINDOW * 3;
            if (w[0] < 64 || blastNearby) continue;
            Places.Place p = places.byId(e.getKey());
            if (p == null) continue;
            int[] cells = places.accept(p.id());
            for (int c : cells) {
                int slot = store.slotOf(c);
                if (slot < 0) continue;
                places.add(c, Places.KIND_BROKE_AWAY);
                bannerCells.rem(c);
            }
            log(now, LOG_BROKE_AWAY, cells.length, 0, "");
            lostChanged();
        }
    }

    public boolean acceptPlace(int id, long now) {
        int[] cells = places.accept(id);
        if (cells.length == 0) return false;
        for (int c : cells) {
            store.removePacked(c);
            bannerCells.rem(c);
        }
        log(now, LOG_ACCEPT_PLACE, id, cells.length, "");
        shapeChanged();
        lostChanged();
        return true;
    }

    public void acceptBanner(long now, boolean accept) {
        if (accept) {
            int n = 0;
            for (int i = 0; i < bannerCells.size(); i++) {
                int c = bannerCells.getInt(i);
                places.remove(c);
                if (store.removePacked(c)) n++;
            }
            if (n > 0) {
                refitBy = "";
                refitCount = n;
                refitAt = now;
                log(now, LOG_ACCEPT_REFIT, n, 0, "");
                shapeChanged();
            }
        }
        bannerCells.clear();
        lostChanged();
    }

    public void acceptShape(long now) {
        surveyWanted = true;
        log(now, LOG_ACCEPT_SHAPE, 0, 0, "");
        changed();
    }

    public void turnBow(long now) {
        if (ground()) return;
        bow = (bow + 1) & 3;
        bowSource = BattleReport.BOW_MANUAL;
        bowDecided = true;
        log(now, LOG_BOW, bow, 0, "");
        places.clearNames();
        shapeChanged();
    }

    public void setGroundSize(int size) {
        int s = Math.max(17, Math.min(129, size | 1));
        if (s == groundSize) return;
        groundSize = s;
        surveyWanted = true;
        changed();
    }

    public void setOption(int option, int value) {
        switch (option) {
            case 0 -> options = value != 0 ? options | BattleReport.OPT_BELL : options & ~BattleReport.OPT_BELL;
            case 1 -> options = value != 0 ? options | BattleReport.OPT_LIGHTS : options & ~BattleReport.OPT_LIGHTS;
            case 2 -> options = value != 0 ? options | BattleReport.OPT_CHAT : options & ~BattleReport.OPT_CHAT;
            case 3 -> redstoneIn = Math.max(0, Math.min(1, value));
            case 4 -> comparatorMode = Math.max(0, Math.min(2, value));
            default -> {
                return;
            }
        }
        if ((options & BattleReport.OPT_BELL) == 0) bell.cancel();
        changed();
    }

    void refit(String who, int n, long now) {
        if (n <= 0) return;
        if (now - refitAt < 200 && refitBy.equals(who)) {
            refitCount += n;
        } else {
            refitCount = n;
        }
        refitBy = who;
        refitAt = now;
        log(now, LOG_REFIT, n, 0, who);
        changed();
    }

    void log(long now, byte kind, int a, int b, String s) {
        log.add(new LogEntry(now, kind, a, b, s == null ? "" : s));
        while (log.size() > MAX_LOG) log.remove(0);
        setDirty();
    }

    public List<LogEntry> log() {
        return List.copyOf(log);
    }

    public void onHandover(long now, byte why) {
        weaponsFree = false;
        firingCleared = false;
        log(now, why, 0, 0, "");
        changed();
    }

    public List<GunRecord> guns() {
        return guns;
    }

    int nextGunId() {
        int id = 1;
        boolean taken = true;
        while (taken) {
            taken = false;
            for (GunRecord g : guns) {
                if (g.id == id) {
                    taken = true;
                    id++;
                    break;
                }
            }
        }
        return id;
    }

    @Nullable
    GunRecord gun(int id) {
        for (GunRecord g : guns) if (g.id == id) return g;
        return null;
    }

    @Nullable
    public TargetFinder.Target target() {
        return target;
    }

    public boolean designate(int kind, UUID id, String name, String by, long now) {
        if (target != null && target.kind == kind && target.id.equals(id)) return false;
        target = new TargetFinder.Target(kind, id, name, by, now);
        designateSeq++;
        for (GunRecord g : guns) {
            g.answer = null;
            if (g.yaw != null) g.yaw.reset();
            if (g.pitch != null) g.pitch.reset();
        }
        log(now, LOG_DESIGNATE, kind, 0, name + "\u0001" + by);
        setDirty();
        changed();
        return true;
    }

    public void clearTarget(String by, long now) {
        if (target == null) return;
        log(now, LOG_CLEAR_TARGET, 0, 0, target.name + "\u0001" + by);
        target = null;
        setDirty();
        changed();
    }

    public void setWeapons(boolean free, String by, long now) {
        if (weaponsFree == free) return;
        weaponsFree = free;
        log(now, LOG_WEAPONS, free ? 1 : 0, 0, by);
        changed();
    }

    public void setGunMode(int gunId, int mode, long now) {
        int m = Math.max(GunState.MODE_HOLD, Math.min(GunState.MODE_FIRE, mode));
        boolean any = false;
        for (GunRecord g : guns) {
            if ((gunId < 0 || g.id == gunId) && g.mode != m) {
                g.mode = m;
                any = true;
            }
        }
        if (!any) return;
        log(now, LOG_MODE, gunId, m, "");
        setDirty();
        changed();
    }

    public void renameGun(int gunId, String name) {
        GunRecord g = gun(gunId);
        if (g == null) return;
        String n = name == null ? "" : name.strip();
        if (n.length() > 24) n = n.substring(0, 24);
        if (n.equals(g.name)) return;
        g.name = n;
        setDirty();
        changed();
    }

    public void setGunArc(int gunId, int packed) {
        GunRecord g = gun(gunId);
        if (g == null) return;
        GunArc next = g.arc.with(GunArc.packedField(packed), GunArc.packedValue(packed));
        if (next.equals(g.arc)) return;
        g.arc = next;
        setDirty();
        changed();
    }

    public void relearnAim(int gunId) {
        for (GunRecord g : guns) if (gunId < 0 || g.id == gunId) g.relearn();
        gunScanWanted = true;
        setDirty();
        changed();
    }

    public void requestSalvo(@Nullable net.minecraft.server.level.ServerPlayer caller, String by) {
        salvoWanted = true;
        salvoCaller = caller;
        salvoBy = by == null ? "" : by;
    }

    public void watch(UUID player, boolean open, long now) {
        if (open) watchers.put(player, now);
        else watchers.remove(player);
    }

    boolean watched(long now) {
        watchers.values().removeIf(t -> now - t > WATCH_TICKS);
        return !watchers.isEmpty();
    }

    public boolean fireControl(net.minecraft.server.level.ServerLevel level, BlockPos bridge, net.minecraft.world.phys.Vec3 world,
                               boolean powered, long now) {
        return FireControl.tick(this, level, bridge, world, powered, now);
    }

    public void letGoAll(net.minecraft.server.level.ServerLevel level) {
        FireControl.letGoAll(this, level);
    }

    public boolean designateFromConsole(net.minecraft.server.level.ServerLevel level, net.minecraft.server.level.ServerPlayer by,
                                        int kind, int entityId, String shipId, long now) {
        return BattleCrew.designate(this, level, by, kind, entityId, shipId, kind == TargetFinder.KIND_PLAYER, now);
    }

    public void sendWatch(net.minecraft.server.level.ServerLevel level, long now) {
        if (!watched(now)) return;
        com.createstats.battle.net.BattleWatchPacket packet = BattleCrew.watchPacket(this, now);
        for (UUID id : List.copyOf(watchers.keySet())) {
            net.minecraft.server.level.ServerPlayer p = level.getServer().getPlayerList().getPlayer(id);
            if (p == null) {
                watchers.remove(id);
                continue;
            }
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(p, packet);
        }
    }

    public int redstoneIn() {
        return redstoneIn;
    }

    public boolean weaponsFree() {
        return weaponsFree;
    }

    public boolean watching(UUID player) {
        return watchers.containsKey(player);
    }

    public void wantContacts() {
        contactsDue = true;
    }

    public int targetDownSeq() {
        return targetDownSeq;
    }

    public int gunDownSeq() {
        return gunDownSeq;
    }

    public int designateSeq() {
        return designateSeq;
    }

    public void registerMarker(BlockPos pos, String name, int cover, long now) {
        MarkerInfo old = markers.get(pos.asLong());
        if (old == null && markers.size() >= MAX_MARKERS) return;
        markers.put(pos.asLong(), new MarkerInfo(name, cover, now));
        if (old == null || !old.name().equals(name) || old.cover() != cover) {
            places.clearNames();
            changed();
        }
    }

    void expireMarkers(long now) {
        if (markers.values().removeIf(m -> now - m.seen() > 100)) changed();
    }

    public int[] cube(BlockPos centre, int cover) {
        int intact = 0;
        int total = 0;
        for (int dx = -cover; dx <= cover; dx++) {
            for (int dy = -cover; dy <= cover; dy++) {
                for (int dz = -cover; dz <= cover; dz++) {
                    int packed = pack(centre.offset(dx, dy, dz));
                    if (packed < 0) continue;
                    int slot = store.slotOf(packed);
                    if (slot < 0) continue;
                    total++;
                    if (!store.isLost(slot)) intact++;
                }
            }
        }
        return new int[]{intact, total};
    }

    public ShipShape shape() {
        if (shape == null || shapeBuiltFor != shapeVersion) {
            shape = ShipShape.build(this);
            shapeBuiltFor = shapeVersion;
        }
        return shape;
    }

    public BattleReport report(boolean powered, boolean master, long now) {
        List<BattleReport.SystemLine> systems = new ArrayList<>();
        for (BattleSystem s : BattleSystem.values()) {
            if (s == BattleSystem.PROPULSION) {
                int total = props.size();
                int intact = 0;
                for (Prop p : props) {
                    int slot = store.slotOf(p.packed());
                    if (slot >= 0 && !store.isLost(slot)) intact++;
                }
                systems.add(new BattleReport.SystemLine(intact, total));
            } else {
                systems.add(new BattleReport.SystemLine(store.total(s) - store.lost(s), store.total(s)));
            }
        }
        List<BattleReport.HitLine> hitLines = new ArrayList<>();
        for (HitAcc h : hits) {
            hitLines.add(new BattleReport.HitLine(h.at, h.place, h.count, h.count == 1 || !h.what().isEmpty()
                    ? BlockInfo.descriptionId(h.what()) : ""));
        }
        List<BattleReport.SectionLine> sections = new ArrayList<>();
        PlaceNamer where = markers.isEmpty() ? null : namer();
        for (Map.Entry<Long, MarkerInfo> e : markers.entrySet()) {
            if (sections.size() >= BattleReport.MAX_SECTIONS) break;
            BlockPos pos = BlockPos.of(e.getKey());
            int[] c = cube(pos, e.getValue().cover());
            int permille = c[1] == 0 ? -1 : (int) (c[0] * 1000L / c[1]);
            int packed = pack(pos);

            String place = packed < 0 || where == null ? "" : where.geometry(packed);
            sections.add(new BattleReport.SectionLine(e.getKey(), e.getValue().name(), permille, place));
        }
        List<String> missing = new ArrayList<>();
        if (store.lostCount() > 0) {
            boolean[] seenPalette = new boolean[store.palette().size()];
            for (int slot = 0; slot < store.size() && missing.size() < BattleReport.MAX_MISSING; slot++) {
                if (!store.isLost(slot)) continue;
                int p = store.paletteAt(slot);
                if (seenPalette[p]) continue;
                seenPalette[p] = true;
                missing.add(store.blockAt(slot));
            }
        }
        ShipShape s = shape();
        int surveyPermille = survey != null ? survey.permille() : (store.isEmpty() && surveyWanted ? 0 : -1);
        if (surveyPermille < 0 && store.isEmpty()) surveyPermille = 0;
        List<BattleReport.GunLine> gunLines = new ArrayList<>();
        for (GunRecord g : guns) {
            if (gunLines.size() >= BattleReport.MAX_GUNS) break;
            GunState st = powered || g.state == GunState.DOWN || g.state == GunState.HOLD ? g.state : GunState.NO_POWER;
            gunLines.add(new BattleReport.GunLine(g.id, g.label(), st.ordinal(), g.mode, g.kind, g.load, g.train, g.elev,
                    g.axis, g.argA, g.argB, g.argS, g.arc, g.mount()));
        }
        return new BattleReport(key.text(), ground(), shipName, powered, master, now, surveyPermille, store.taken(),
                store.size(), store.lostCount(), places.count(), newBlocks, bannerCells.size(), capped, bow, bowSource,
                groundSize, options, redstoneIn, comparatorMode, systems, hitLines, lastHitAt,
                fireAboard ? firePlace : "", sections, missing, shapeVersion, lostVersion, s.face(this), refitBy,
                refitCount, refitAt, weaponsFree, redstoneIn == 1, target == null ? BattleReport.TargetLine.NONE : targetLine,
                gunLines, guns.size(), salvoAt, salvo);
    }

    public int gunsReady() {
        int n = 0;
        for (GunRecord g : guns) if (g.state.ready()) n++;
        return n;
    }

    public int comparator(long now) {
        long age = lastHitAt < 0 ? -1L : now - lastHitAt;
        return ComparatorScale.bridge(comparatorMode, store.lostCount(), store.size(), age, gunsReady());
    }

    public boolean claimWork(long now) {
        if (lastWorkTick == now) return false;
        lastWorkTick = now;
        return true;
    }

    public void work(net.minecraft.server.level.ServerLevel level, BlockPos master, net.minecraft.core.Direction facing, long now) {
        ShipScanner.tick(this, level, master, facing, now);
        if (bell.ringDue(now)) ringSeq++;
        if (!lightsCleared) {
            ShipScanner.Area area = ShipScanner.area(this, level);
            if (area != null) {
                for (net.minecraft.world.level.chunk.LevelChunk c : area.chunks()) {
                    for (net.minecraft.world.level.block.entity.BlockEntity be : c.getBlockEntities().values()) {
                        if (be instanceof com.createstats.blockentity.StageLightBE light && light.hasBattleTarget()) {
                            light.clearBattleTarget();
                        }
                    }
                }
                lightsCleared = true;
                setDirty();
            }
        }
    }

    public int ringSeq() {
        return ringSeq;
    }

    public long lastHitAt() {
        return lastHitAt;
    }

    public String shipName() {
        return shipName;
    }

    public int fireToneSeq() {
        return fireToneSeq;
    }

    public boolean freshlyLoaded() {
        return freshlyLoaded;
    }

    public void clearFreshlyLoaded() {
        freshlyLoaded = false;
    }

    public com.createstats.battle.net.BattleShapePacket shapePacket() {
        ShipShape s = shape();
        com.createstats.battle.core.Projection.Frame f = s.frame;
        int[] frameInts = {f.minFore(), f.minStbd(), f.maxUp(), f.length(), f.beam(), f.height(), f.k()};
        int[] propPairs = new int[props.size() * 2];
        for (int i = 0; i < props.size(); i++) {
            propPairs[i * 2] = props.get(i).number();
            propPairs[i * 2 + 1] = props.get(i).packed();
        }
        long[] positions = election.positions();
        IntArrayList bridges = new IntArrayList();
        for (long p : positions) {
            int packed = pack(BlockPos.of(p));
            if (packed >= 0) bridges.add(packed);
        }
        return new com.createstats.battle.net.BattleShapePacket(key.text(), shapeVersion, ground(), bow, origin, frameInts,
                com.createstats.battle.core.Projection.pack2(s.plan), com.createstats.battle.core.Projection.pack2(s.profile),
                propPairs, bridges.toIntArray());
    }

    public com.createstats.battle.net.BattleDetailPacket detailPacket(BlockPos near, long now) {
        int[] lostAll = store.lostPositions();
        int np = pack(near);
        final int cx = np < 0 ? 0 : PackedPos.dx(np);
        final int cy = np < 0 ? 0 : PackedPos.dy(np);
        final int cz = np < 0 ? 0 : PackedPos.dz(np);
        Integer[] order = new Integer[lostAll.length];
        for (int i = 0; i < order.length; i++) order[i] = i;
        if (lostAll.length > com.createstats.battle.net.BattleDetailPacket.MAX_LOST) {
            java.util.Arrays.sort(order, java.util.Comparator.comparingLong(i -> {
                int q = lostAll[i];
                long dx = PackedPos.dx(q) - cx;
                long dy = PackedPos.dy(q) - cy;
                long dz = PackedPos.dz(q) - cz;
                return dx * dx + dy * dy + dz * dz;
            }));
        }
        int n = Math.min(lostAll.length, com.createstats.battle.net.BattleDetailPacket.MAX_LOST);
        int[] lost = new int[n];
        int[] ages = new int[n];
        byte[] causes = new byte[n];
        for (int k = 0; k < n; k++) {
            int packed = lostAll[order[k]];
            int slot = store.slotOf(packed);
            lost[k] = packed;
            ages[k] = slot < 0 ? Integer.MAX_VALUE : (int) Math.min(Integer.MAX_VALUE, Math.max(0L, now - store.lostAt(slot)));
            causes[k] = slot < 0 ? SurveyStore.CAUSE_HIT : store.causeAt(slot);
        }
        IntArrayList burning = new IntArrayList();
        for (it.unimi.dsi.fastutil.ints.Int2LongMap.Entry e : fireTouched.int2LongEntrySet()) {
            if (now - e.getLongValue() <= ShipScanner.FIRE_MEMORY && burning.size() < 1024) burning.add(e.getIntKey());
        }
        List<com.createstats.battle.net.BattleDetailPacket.PlaceLine> lines = new ArrayList<>();
        for (Places.Place p : places.open()) {
            int[] cells = p.cells();
            lines.add(new com.createstats.battle.net.BattleDetailPacket.PlaceLine(p.id(), p.name(), p.kind(), p.size(),
                    cells.length == 0 ? 0 : cells[0]));
        }
        return new com.createstats.battle.net.BattleDetailPacket(key.text(), lostVersion, lost, ages, causes,
                burning.toIntArray(), lines);
    }

    public void carryFrom(ShipRecord old, int quarterTurns, long now) {
        options = old.options;
        redstoneIn = old.redstoneIn;
        comparatorMode = old.comparatorMode;
        groundSize = old.groundSize;
        if (!old.ground() && old.bowDecided) {
            bow = (old.bow + quarterTurns) & 3;
            bowSource = old.bowSource;
            bowDecided = true;
        }
        log(now, LOG_CARRIED, quarterTurns, 0, old.key.text());
        changed();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("Version", FORMAT);
        tag.put("Key", key.save());
        tag.putLong("Origin", origin);
        tag.putByte("Bow", (byte) bow);
        tag.putByte("BowSource", (byte) bowSource);
        tag.putBoolean("BowDecided", bowDecided);

        CompoundTag ref = new CompoundTag();
        int n = store.size();
        int[] pos = new int[n];
        int[] pal = new int[(n + 1) / 2];
        for (int i = 0; i < n; i++) {
            pos[i] = store.packedAt(i);
            int p = store.paletteAt(i) & 0xFFFF;
            pal[i >> 1] |= (i & 1) == 0 ? p : p << 16;
        }
        ref.putIntArray("Pos", pos);
        ref.putIntArray("Pal", pal);
        ListTag palette = new ListTag();
        List<String> ids = store.palette();
        byte[] sys = new byte[ids.size()];
        for (int i = 0; i < ids.size(); i++) {
            palette.add(StringTag.valueOf(ids.get(i)));
            sys[i] = (byte) store.paletteSystem(i).ordinal();
        }
        ref.put("Palette", palette);
        ref.putByteArray("PalSys", sys);
        ref.putLong("Taken", store.taken());
        tag.put("Ref", ref);

        CompoundTag lost = new CompoundTag();
        IntArrayList lostPos = new IntArrayList();
        List<Long> lostTime = new ArrayList<>();
        ByteList causes = new ByteList();
        for (int i = 0; i < n; i++) {
            if (!store.isLost(i)) continue;
            lostPos.add(store.packedAt(i));
            lostTime.add(store.lostAt(i));
            causes.add(store.causeAt(i));
        }
        lost.putIntArray("Pos", lostPos.toIntArray());
        lost.put("Time", new LongArrayTag(lostTime));
        lost.putByteArray("Cause", causes.toArray());
        tag.put("Lost", lost);

        ListTag placeList = new ListTag();
        for (Places.Place p : places.open()) {
            CompoundTag pt = new CompoundTag();
            pt.putInt("Id", p.id());
            pt.putString("Name", p.name());
            pt.putByte("Kind", p.kind());
            pt.put("Cells", new IntArrayTag(p.cells()));
            placeList.add(pt);
        }
        tag.put("Places", placeList);
        tag.putInt("NextPlace", places.nextId());

        ListTag propList = new ListTag();
        for (Prop p : props) {
            CompoundTag pt = new CompoundTag();
            pt.putInt("Pos", p.packed());
            pt.putInt("Id", p.number());
            propList.add(pt);
        }
        tag.put("Props", propList);
        tag.putUUID("ControlId", controlId);

        CompoundTag opts = new CompoundTag();
        opts.putBoolean("Bell", (options & BattleReport.OPT_BELL) != 0);
        opts.putBoolean("Lights", (options & BattleReport.OPT_LIGHTS) != 0);
        opts.putBoolean("Chat", (options & BattleReport.OPT_CHAT) != 0);
        opts.putByte("RsIn", (byte) redstoneIn);
        opts.putByte("Cmp", (byte) comparatorMode);
        opts.putByte("Ground", (byte) groundSize);
        tag.put("Opts", opts);

        ListTag logList = new ListTag();
        for (LogEntry e : log) {
            CompoundTag lt = new CompoundTag();
            lt.putLong("T", e.t());
            lt.putByte("K", e.kind());
            lt.putInt("A", e.a());
            lt.putInt("B", e.b());
            lt.putString("S", e.s());
            logList.add(lt);
        }
        tag.put("Log", logList);
        tag.putLong("Seen", seen);
        tag.putInt("ShapeV", shapeVersion);
        tag.putInt("LostV", lostVersion);
        tag.putLong("LastHit", lastHitAt);
        tag.putLong("LastBlast", lastBlastAt);

        ListTag hitList = new ListTag();
        for (HitAcc h : hits) {
            CompoundTag ht = new CompoundTag();
            ht.putLong("T", h.at);
            ht.putString("Place", h.place);
            ht.putInt("N", h.count);
            ht.putString("What", h.what());
            hitList.add(ht);
        }
        tag.put("Hits", hitList);
        CompoundTag refit = new CompoundTag();
        refit.putString("By", refitBy);
        refit.putInt("N", refitCount);
        refit.putLong("T", refitAt);
        tag.put("Refit", refit);
        tag.putInt("NewBlocks", newBlocks);
        tag.putIntArray("Banner", bannerCells.toIntArray());
        tag.putBoolean("Capped", capped);
        ListTag gunList = new ListTag();
        for (GunRecord g : guns) gunList.add(g.save());
        tag.put("Guns", gunList);
        if (target != null) {
            CompoundTag tt = new CompoundTag();
            tt.putByte("Kind", (byte) target.kind);
            tt.putUUID("Id", target.id);
            tt.putString("Name", target.name);
            tt.putString("By", target.by);
            tt.putLong("At", target.at);
            tt.putLong("LostSince", target.lostSince);
            tag.put("Target", tt);
        }
        tag.putIntArray("Firing", firing.toIntArray());
        tag.putBoolean("LightsCleared", lightsCleared);
        tag.putString("Name", shipName);
        tag.putInt("BridgeDy", bridgeDy);
        tag.putByte("Weapons", (byte) (weaponsFree ? 1 : 0));
        return tag;
    }

    public static ShipRecord load(CompoundTag tag, HolderLookup.Provider registries) {
        RecordKey key = RecordKey.load(tag.getCompound("Key"));
        if (key == null) key = RecordKey.ground(net.minecraft.world.level.Level.OVERWORLD, BlockPos.ZERO);
        ShipRecord r = new ShipRecord(key, tag.getLong("Origin"));
        r.bow = tag.getByte("Bow") & 3;
        r.bowSource = tag.getByte("BowSource");
        r.bowDecided = tag.getBoolean("BowDecided");

        CompoundTag ref = tag.getCompound("Ref");
        ListTag palette = ref.getList("Palette", Tag.TAG_STRING);
        byte[] sys = ref.getByteArray("PalSys");
        for (int i = 0; i < palette.size(); i++) {
            r.store.paletteOf(palette.getString(i), BattleSystem.byOrdinal(i < sys.length ? sys[i] : 0));
        }
        int[] pos = ref.getIntArray("Pos");
        int[] pal = ref.getIntArray("Pal");
        CompoundTag lost = tag.getCompound("Lost");
        int[] lostPos = lost.getIntArray("Pos");
        long[] lostTime = lost.getLongArray("Time");
        byte[] causes = lost.getByteArray("Cause");
        Map<Integer, Integer> lostIndex = new HashMap<>();
        for (int i = 0; i < lostPos.length; i++) lostIndex.put(lostPos[i], i);
        for (int i = 0; i < pos.length; i++) {
            int word = (i >> 1) < pal.length ? pal[i >> 1] : 0;
            int p = (i & 1) == 0 ? word & 0xFFFF : (word >>> 16) & 0xFFFF;
            Integer li = lostIndex.get(pos[i]);
            long t = li == null || li >= lostTime.length ? SurveyStore.INTACT : lostTime[li];
            byte c = li == null || li >= causes.length ? SurveyStore.CAUSE_HIT : causes[li];
            r.store.load(pos[i], p, t, c);
        }
        r.store.setTaken(ref.contains("Taken") ? ref.getLong("Taken") : -1L);

        ListTag placeList = tag.getList("Places", Tag.TAG_COMPOUND);
        for (int i = 0; i < placeList.size(); i++) {
            CompoundTag pt = placeList.getCompound(i);
            r.places.load(pt.getInt("Id"), pt.getByte("Kind"), pt.getString("Name"), pt.getIntArray("Cells"));
        }
        if (r.places.count() > 0) r.places.setNextId(Math.max(r.places.nextId(), tag.getInt("NextPlace")));

        ListTag propList = tag.getList("Props", Tag.TAG_COMPOUND);
        for (int i = 0; i < propList.size(); i++) {
            CompoundTag pt = propList.getCompound(i);
            r.props.add(new Prop(pt.getInt("Id"), pt.getInt("Pos")));
        }
        if (tag.hasUUID("ControlId")) r.controlId = tag.getUUID("ControlId");

        CompoundTag opts = tag.getCompound("Opts");
        r.options = (opts.getBoolean("Bell") ? BattleReport.OPT_BELL : 0) | (opts.getBoolean("Lights") ? BattleReport.OPT_LIGHTS : 0)
                | (opts.getBoolean("Chat") ? BattleReport.OPT_CHAT : 0);
        r.redstoneIn = opts.getByte("RsIn");
        r.comparatorMode = opts.getByte("Cmp");
        int ground = opts.getByte("Ground") & 0xFF;
        r.groundSize = ground >= 17 ? ground : 33;

        ListTag logList = tag.getList("Log", Tag.TAG_COMPOUND);
        for (int i = 0; i < logList.size(); i++) {
            CompoundTag lt = logList.getCompound(i);
            r.log.add(new LogEntry(lt.getLong("T"), lt.getByte("K"), lt.getInt("A"), lt.getInt("B"), lt.getString("S")));
        }
        r.seen = tag.getLong("Seen");
        r.shapeVersion = tag.getInt("ShapeV");
        r.lostVersion = tag.getInt("LostV");
        r.lastHitAt = tag.contains("LastHit") ? tag.getLong("LastHit") : -1L;
        r.lastBlastAt = tag.contains("LastBlast") ? tag.getLong("LastBlast") : -1L;
        ListTag hitList = tag.getList("Hits", Tag.TAG_COMPOUND);
        for (int i = 0; i < hitList.size() && i < BattleReport.MAX_HITS; i++) {
            CompoundTag ht = hitList.getCompound(i);
            HitAcc h = new HitAcc();
            h.at = ht.getLong("T");
            h.place = ht.getString("Place");
            h.count = ht.getInt("N");
            String what = ht.getString("What");
            if (!what.isEmpty()) h.blocks.put(what, h.count);
            h.lastAdd = h.at;
            h.group = Integer.MIN_VALUE + i;
            h.told = true;
            r.hits.add(h);
        }
        CompoundTag refit = tag.getCompound("Refit");
        r.refitBy = refit.getString("By");
        r.refitCount = refit.getInt("N");
        r.refitAt = refit.contains("T") ? refit.getLong("T") : -1L;
        r.newBlocks = tag.contains("NewBlocks") ? tag.getInt("NewBlocks") : -1;
        for (int c : tag.getIntArray("Banner")) r.bannerCells.add(c);
        r.capped = tag.getBoolean("Capped");
        ListTag gunList = tag.getList("Guns", Tag.TAG_COMPOUND);
        for (int i = 0; i < gunList.size() && r.guns.size() < MAX_GUNS; i++) r.guns.add(GunRecord.load(gunList.getCompound(i)));
        if (tag.contains("Target", Tag.TAG_COMPOUND)) {
            CompoundTag tt = tag.getCompound("Target");
            if (tt.hasUUID("Id")) {
                r.target = new TargetFinder.Target(tt.getByte("Kind"), tt.getUUID("Id"), tt.getString("Name"),
                        tt.getString("By"), tt.getLong("At"));
                r.target.lostSince = tt.getLong("LostSince");
            }
        }
        for (int id : tag.getIntArray("Firing")) r.firing.add(id);
        r.lightsCleared = tag.getBoolean("LightsCleared");
        r.shipName = tag.getString("Name");
        r.bridgeDy = tag.getInt("BridgeDy");

        r.weaponsFree = false;
        r.freshlyLoaded = true;
        return r;
    }

    private static final class ByteList {
        private byte[] data = new byte[64];
        private int size;

        void add(byte b) {
            if (size == data.length) data = java.util.Arrays.copyOf(data, size * 2);
            data[size++] = b;
        }

        byte[] toArray() {
            return java.util.Arrays.copyOf(data, size);
        }
    }
}
