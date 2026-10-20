package com.createstats.blockentity;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.createstats.CreateStats;
import com.createstats.data.MetricType;
import com.createstats.network.SeismicPanelPacket;
import com.createstats.network.SeismicScenePacket;
import com.createstats.seismic.SeismicCatalog;
import com.createstats.seismic.SeismicCharge;
import com.createstats.seismic.archive.ArchiveBlow;
import com.createstats.seismic.archive.SceneBuilder;
import com.createstats.seismic.archive.SceneCodec;
import com.createstats.seismic.archive.SeismicArchive;
import com.createstats.seismic.archive.SeismicArchiveData;
import com.createstats.seismic.archive.SurveyChannels;
import com.createstats.seismic.archive.SurveyFinds;
import com.createstats.seismic.archive.SurveyScene;
import com.createstats.statsmk3.Mk3PanelBlockEntity;
import com.createstats.statsmk3.Mk3Settings;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

public final class SeismicLinks {
    public static final String SOURCE_TYPE = "Seismic Survey";

    public static final long HEARTBEAT_TIMEOUT = 200L;

    public static final long REMOVED_DELAY = 40L;

    public static final int MAX_KNOWN = 32;

    static final long WATCH_TICKS = 600L;

    static final long BUILD_GAP = 20L;
    static final long CHANNEL_TICKS = 20L;

    static final long WARM_TICKS = 400L;
    static final long RIG_TICKS = 10L;

    public static final long NEW_TICKS = SurveyChannels.NEW_TICKS;

    static final long REQUEST_GAP = 40L;

    public static final int OP_REQUEST = 0;
    public static final int OP_FORGET = 1;
    public static final int OP_CLEAR = 2;
    public static final int OP_UNCLEAR = 3;

    public static final int OP_REQUEST_PAGE = 4;

    public static final int OP_WORK = 5;
    public static final int OP_UNWORK = 6;

    static final int MAX_WORKED = 256;

    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
        final Thread t = new Thread(r, "Create Stats seismic scenes");
        t.setDaemon(true);
        t.setPriority(Thread.NORM_PRIORITY - 1);
        return t;
    });

    public record RigState(int state, int chargePct, int radius, String target, boolean coupled) {
    }

    private record Watch(long tick, boolean page) {
    }

    private record Built(long stamp, SurveyScene scene, byte[] full, byte[] face) {
    }

    private final StatsDisplayPanelBE panel;

    private final Map<BlockPos, Long> linked = new LinkedHashMap<>();

    private final LinkedHashSet<Long> known = new LinkedHashSet<>();

    private final Map<Long, Integer> numbers = new HashMap<>();
    private int nextNumber = 1;
    private long since = Long.MIN_VALUE;
    private final Map<Long, RigState> rigStates = new LinkedHashMap<>();

    private final LinkedHashMap<Long, Long> worked = new LinkedHashMap<>();

    private final Map<UUID, Watch> watchers = new HashMap<>();
    private long builtStamp = Long.MIN_VALUE;
    private long lastBuild = Long.MIN_VALUE;
    private Future<Built> building;
    private byte[] sceneBytes;
    private byte[] faceBytes;
    private long faceSpec;
    private Future<Map<String, SurveyChannels.Figures>> counting;
    private long countedStamp = Long.MIN_VALUE;
    private long lastCount = Long.MIN_VALUE;
    private Map<String, SurveyChannels.Figures> figures = Map.of();
    private int blowCount;
    private long newestBlow = Long.MIN_VALUE;
    private long lastChannels = Long.MIN_VALUE;
    private long lastRigRead = Long.MIN_VALUE;

    private SurveyScene scene = SurveyScene.EMPTY;
    private long sceneStamp = Long.MIN_VALUE;
    private boolean sceneFull;
    private long wireStamp = Long.MIN_VALUE;
    private long lastRequest = Long.MIN_VALUE;

    SeismicLinks(StatsDisplayPanelBE panel) {
        this.panel = panel;
    }

    public static SeismicScannerBE rigAt(Level level, BlockPos pos) {
        if (level == null || pos == null || !level.isLoaded(pos)) {
            return null;
        }
        if (level.getBlockEntity(pos) instanceof SeismicScannerBE rig) {
            return rig;
        }
        final BlockPos below = pos.below();
        return level.isLoaded(below) && level.getBlockEntity(below) instanceof SeismicScannerBE rig ? rig : null;
    }

    public void heartbeat(BlockPos rig, long tick) {
        final BlockPos pos = rig.immutable();
        final boolean fresh = !this.linked.containsKey(pos);
        this.linked.put(pos, tick);
        final long key = pos.asLong();
        if (fresh || !this.known.contains(key)) {
            this.known.remove(key);
            this.known.add(key);
            if (!this.numbers.containsKey(key)) {
                this.numbers.put(key, this.nextNumber++);
            }
            trimKnown();
            this.panel.markSeismicLinksChanged();
        }
    }

    public boolean remove(BlockPos source) {
        BlockPos hit = this.linked.containsKey(source) ? source : this.linked.containsKey(source.below()) ? source.below()
                : null;
        if (hit == null) {
            return false;
        }
        this.linked.remove(hit);
        this.panel.markSeismicLinksChanged();
        return true;
    }

    private void trimKnown() {
        final Iterator<Long> it = this.known.iterator();
        while (this.known.size() > MAX_KNOWN && it.hasNext()) {
            final long k = it.next();
            if (!this.linked.containsKey(BlockPos.of(k))) {
                it.remove();
                this.rigStates.remove(k);
                this.numbers.remove(k);
            }
        }
    }

    public boolean hasRigs() {
        return !this.known.isEmpty();
    }

    public boolean hasLinks() {
        return !this.linked.isEmpty();
    }

    public List<Long> knownRigs() {
        return List.copyOf(this.known);
    }

    public boolean isLinked(long rigKey) {
        return this.linked.containsKey(BlockPos.of(rigKey));
    }

    public long since() {
        return this.since;
    }

    public Map<Long, RigState> rigStates() {
        return java.util.Collections.unmodifiableMap(this.rigStates);
    }

    public Map<Long, Long> worked() {
        return java.util.Collections.unmodifiableMap(this.worked);
    }

    public Map<Long, Integer> rigNumbers() {
        return java.util.Collections.unmodifiableMap(this.numbers);
    }

    public void serverTick(ServerLevel level, long tick) {
        if (this.known.isEmpty() && this.linked.isEmpty()) {
            return;
        }
        boolean changed = false;
        final Iterator<Map.Entry<BlockPos, Long>> it = this.linked.entrySet().iterator();
        while (it.hasNext()) {
            final Map.Entry<BlockPos, Long> e = it.next();
            final long age = tick - e.getValue();
            final boolean loaded = level.isLoaded(e.getKey());
            final boolean missing = loaded && !(level.getBlockEntity(e.getKey()) instanceof SeismicScannerBE);
            if (age > HEARTBEAT_TIMEOUT || (missing && age > REMOVED_DELAY) || age < 0L) {
                it.remove();
                this.rigStates.remove(e.getKey().asLong());
                changed = true;
            }
        }
        if (tick - this.lastRigRead >= RIG_TICKS || tick < this.lastRigRead) {
            this.lastRigRead = tick;
            changed |= readRigs(level);
        }
        if (changed) {
            this.panel.markSeismicLinksChanged();
        }
        final SeismicArchiveData data = SeismicArchiveData.get(level);
        final SeismicArchive archive = data.archive();

        archive.closeStale(tick);
        data.touched();
        collect(level, tick);
        final long spec = faceSpec();
        final long stamp = stamp(archive) ^ spec;
        this.watchers.values().removeIf(w -> tick - w.tick() > WATCH_TICKS || w.tick() > tick);
        if (!this.watchers.isEmpty() && stamp != this.builtStamp && this.building == null
                && (this.lastBuild == Long.MIN_VALUE || tick - this.lastBuild >= BUILD_GAP || tick < this.lastBuild)) {
            startBuild(archive, tick, stamp, spec);
        }
        if (this.counting == null && !this.known.isEmpty() && countDue(archive, tick, stamp)) {
            startCount(archive, tick, stamp);
        }
        if (!this.known.isEmpty() && (this.lastChannels == Long.MIN_VALUE || tick - this.lastChannels >= CHANNEL_TICKS
                || tick < this.lastChannels)) {
            this.lastChannels = tick;
            publish(tick);
        }
    }

    long stamp(SeismicArchive archive) {
        long h = 0xCBF29CE484222325L;
        for (final long key : this.known) {
            h = (h ^ key) * 0x100000001B3L;
            h = (h ^ archive.revisionOf(key)) * 0x100000001B3L;
            h = (h ^ (this.linked.containsKey(BlockPos.of(key)) ? 1L : 2L)) * 0x100000001B3L;
        }
        h = (h ^ this.since) * 0x100000001B3L;
        return h == Long.MIN_VALUE ? 0L : h;
    }

    private long faceSpec() {
        final Mk3Settings st = this.panel instanceof Mk3PanelBlockEntity mk3 ? mk3.mk3Settings() : null;
        if (st == null || !st.faceMode.isSurvey()) {
            return 0L;
        }
        long h = 0x9E3779B97F4A7C15L ^ st.faceMode.ordinal();
        h = h * 31 + st.survey.hidden.hashCode();
        h = h * 31 + Long.hashCode(st.survey.pin);
        return h == 0L ? 1L : h;
    }

    private static int faceWhat(Mk3Settings.FaceMode mode) {
        return switch (mode) {
            case SURVEY_FINDS -> SurveyScene.FACE_BOARD;
            case SURVEY_ECHO -> SurveyScene.FACE_ECHO;
            default -> SurveyScene.FACE_SCAN;
        };
    }

    private void startBuild(SeismicArchive archive, long tick, long stamp, long spec) {
        final List<ArchiveBlow> blows = archive.snapshot(this.known, this.since);
        final List<Long> knownKeys = List.copyOf(this.known);
        final List<Long> linkedKeys = new ArrayList<>();
        for (final BlockPos p : this.linked.keySet()) {
            linkedKeys.add(p.asLong());
        }
        final BlockPos at = this.panel.getBlockPos();
        final long since = this.since;
        final Mk3Settings st = this.panel instanceof Mk3PanelBlockEntity mk3 ? mk3.mk3Settings() : null;
        final boolean face = spec != 0L && st != null;
        final int what = face ? faceWhat(st.faceMode) : 0;
        final Set<String> hidden = face ? hiddenOf(st.survey.hidden) : Set.of();
        final long pin = face ? st.survey.pin : Long.MIN_VALUE;
        this.lastBuild = tick;
        this.building = WORKER.submit(() -> {
            final SurveyScene s = SceneBuilder.build(blows, knownKeys, linkedKeys, since, at.getX(), at.getY(), at.getZ(),
                    SceneBuilder.DEFAULT_BUDGET, tick, stamp);
            final byte[] full = SceneCodec.encode(s);
            final byte[] faceBytes = face ? SceneCodec.encode(s.faceView(what, hidden, pin, at.getX() + 0.5,
                    at.getZ() + 0.5)) : null;
            return new Built(stamp, s, full, faceBytes);
        });
    }

    private static Set<String> hiddenOf(String csv) {
        final Set<String> out = new HashSet<>();
        if (csv != null) {
            for (final String t : csv.split(",")) {
                if (!t.isBlank()) {
                    out.add(t.trim());
                }
            }
        }
        return out;
    }

    private boolean countDue(SeismicArchive archive, long tick, long stamp) {
        if (stamp != this.countedStamp) {
            return this.lastCount == Long.MIN_VALUE || tick - this.lastCount >= CHANNEL_TICKS || tick < this.lastCount;
        }

        final long gap = this.newestBlow != Long.MIN_VALUE && tick - this.newestBlow <= 300L ? CHANNEL_TICKS
                : this.newestBlow != Long.MIN_VALUE && tick - this.newestBlow <= NEW_TICKS + 300L ? WARM_TICKS : -1L;
        return gap > 0 && (tick - this.lastCount >= gap || tick < this.lastCount);
    }

    private void startCount(SeismicArchive archive, long tick, long stamp) {
        final List<ArchiveBlow> blows = archive.snapshot(this.known, this.since);
        final BlockPos at = this.panel.getBlockPos();
        this.lastCount = tick;
        this.countedStamp = stamp;
        this.blowCount = blows.size();
        long newest = Long.MIN_VALUE;
        for (final ArchiveBlow b : blows) {
            newest = Math.max(newest, b.at);
        }
        this.newestBlow = newest;
        this.counting = WORKER.submit(() -> SurveyChannels.compute(blows, at.getX() + 0.5, at.getZ() + 0.5, tick));
    }

    private void collect(ServerLevel level, long tick) {
        if (this.building != null && this.building.isDone()) {
            final Future<Built> f = this.building;
            this.building = null;
            try {
                final Built b = f.get();
                this.builtStamp = b.stamp();
                this.sceneBytes = b.full();
                this.faceBytes = b.face();
                this.panel.markSeismicLinksChanged();
                sendAll(level, tick);
            } catch (Exception e) {
                CreateStats.LOGGER.warn("[Seismic] building a panel's scene failed at {}: {}", this.panel.getBlockPos(),
                        e.toString());
                this.sceneBytes = null;
                this.faceBytes = null;
            }
        }
        if (this.counting != null && this.counting.isDone()) {
            final Future<Map<String, SurveyChannels.Figures>> f = this.counting;
            this.counting = null;
            try {
                this.figures = f.get();
            } catch (Exception e) {
                CreateStats.LOGGER.debug("[Seismic] a panel's channel pass failed: {}", e.toString());
            }
        }
    }

    private void sendAll(ServerLevel level, long tick) {
        final BlockPos at = this.panel.getBlockPos();
        for (final Map.Entry<UUID, Watch> e : this.watchers.entrySet()) {
            final ServerPlayer p = level.getServer().getPlayerList().getPlayer(e.getKey());
            if (p != null && p.serverLevel() == level) {
                send(p, e.getValue().page());
            }
        }
    }

    private void send(ServerPlayer p, boolean page) {
        final BlockPos at = this.panel.getBlockPos();
        if (page && this.sceneBytes != null && this.panel.isPlayerInInteractionRange(p)) {
            PacketDistributor.sendToPlayer(p, new SeismicScenePacket(at, this.builtStamp, true, this.sceneBytes));
        } else if (this.faceBytes != null) {
            PacketDistributor.sendToPlayer(p, new SeismicScenePacket(at, this.builtStamp, false, this.faceBytes));
        }
    }

    public void onRequest(ServerPlayer player, long has, boolean page) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        final boolean near = this.panel.isPlayerInInteractionRange(player);
        if (page && !near) {
            return;
        }
        if (!page && faceSpec() == 0L) {
            return;
        }
        final long tick = level.getGameTime();
        final Watch was = this.watchers.put(player.getUUID(), new Watch(tick, page));
        if (this.sceneBytes == null && this.faceBytes == null) {
            return;
        }
        if (has != this.builtStamp || (page && (was == null || !was.page()))) {
            send(player, page);
        }
    }

    public void command(ServerLevel level, int op, long arg) {
        switch (op) {
            case OP_FORGET -> {
                if (this.known.remove(arg)) {
                    this.linked.remove(BlockPos.of(arg));
                    this.rigStates.remove(arg);
                    this.numbers.remove(arg);
                    this.panel.markSeismicLinksChanged();
                }
            }
            case OP_CLEAR -> {
                this.since = level.getGameTime();
                this.panel.markSeismicLinksChanged();
            }
            case OP_UNCLEAR -> {
                this.since = Long.MIN_VALUE;
                this.panel.markSeismicLinksChanged();
            }
            case OP_WORK -> {
                this.worked.remove(arg);
                this.worked.put(arg, level.getGameTime());
                while (this.worked.size() > MAX_WORKED) {
                    this.worked.remove(this.worked.keySet().iterator().next());
                }
                this.panel.markSeismicLinksChanged();
            }
            case OP_UNWORK -> {
                if (this.worked.remove(arg) != null) {
                    this.panel.markSeismicLinksChanged();
                }
            }
            default -> {
            }
        }
    }

    private boolean readRigs(Level level) {
        boolean changed = false;
        for (final BlockPos p : this.linked.keySet()) {
            final SeismicScannerBE rig = level.isLoaded(p) && level.getBlockEntity(p) instanceof SeismicScannerBE r ? r : null;
            if (rig == null) {
                continue;
            }
            final int burst = SeismicCharge.burstFor(rig.radius());
            final int pct = burst <= 0 ? 0 : (int) Math.min(100L, Math.round(100.0 * rig.stored() / burst));
            final RigState now = new RigState(rig.state().ordinal(), pct / 5 * 5, rig.radius(), rig.target(),
                    rig.coupled());
            if (!now.equals(this.rigStates.put(p.asLong(), now))) {
                changed = true;
            }
        }
        return changed;
    }

    private void publish(long tick) {
        for (final SurveyChannels.Figures f : this.figures.values()) {
            final String id = channelId(f.target());
            final String name = SurveyFinds.station(f.target());
            if (!Double.isNaN(f.nearest())) {
                final String noun = SurveyFinds.noun(f.target(), f.nearestKind());
                put(id + "_nearest", "Nearest " + noun + " along the ground (m)", Math.round(f.nearest()));
                put(id + "_y", "Nearest " + noun + ", Y", f.nearestY());
            }
            put(id + "_blocks", name + " blocks known", f.blocks());
            put(id + "_new", name + " blocks heard (10 min)", f.fresh());
            put(id + "_heard", name + " blocks heard (5 s)", f.heard());
        }
        put("seismic_blows", "Seismic blows", this.blowCount);
        if (this.newestBlow != Long.MIN_VALUE) {
            put("seismic_since_blow", "Since the last blow (s)", Math.max(0L, tick - this.newestBlow) / 20L);
        }
    }

    public static String channelId(String target) {
        if (SeismicCatalog.VOIDS.equals(target)) {
            return "seismic_voids";
        }
        if (SeismicCatalog.LIQUIDS.equals(target)) {
            return "seismic_liquids";
        }
        return "seismic_" + SeismicCatalog.orePath(target).replace('/', '_').toLowerCase(Locale.ROOT);
    }

    private void put(String id, String label, double value) {
        this.panel.onAutoData(this.panel.getBlockPos(), SOURCE_TYPE, id, label, (float) value, MetricType.GENERIC);
    }

    public void save(CompoundTag tag, boolean wire) {
        final CompoundTag t = new CompoundTag();
        final ListTag links = new ListTag();
        for (final Map.Entry<BlockPos, Long> e : this.linked.entrySet()) {
            final CompoundTag l = new CompoundTag();
            l.putLong("Pos", e.getKey().asLong());
            l.putLong("Beat", e.getValue());
            links.add(l);
        }
        t.put("Linked", links);
        final long[] keys = this.known.stream().mapToLong(Long::longValue).toArray();
        t.put("Known", new LongArrayTag(keys));
        final int[] nums = new int[keys.length];
        for (int i = 0; i < keys.length; i++) {
            nums[i] = this.numbers.getOrDefault(keys[i], 0);
        }
        t.put("Numbers", new IntArrayTag(nums));
        t.putInt("NextNumber", this.nextNumber);
        t.putLong("Since", this.since);
        final long[] marks = new long[this.worked.size() * 2];
        int w = 0;
        for (final Map.Entry<Long, Long> e : this.worked.entrySet()) {
            marks[w++] = e.getKey();
            marks[w++] = e.getValue();
        }
        t.put("Worked", new LongArrayTag(marks));
        if (wire) {
            t.putLong("Stamp", this.builtStamp);
            final ListTag rigs = new ListTag();
            for (final Map.Entry<Long, RigState> e : this.rigStates.entrySet()) {
                final CompoundTag r = new CompoundTag();
                r.putLong("Pos", e.getKey());
                r.putInt("State", e.getValue().state());
                r.putInt("Charge", e.getValue().chargePct());
                r.putInt("Radius", e.getValue().radius());
                r.putString("Target", e.getValue().target());
                r.putBoolean("Coupled", e.getValue().coupled());
                rigs.add(r);
            }
            t.put("Rigs", rigs);
        }
        tag.put("SeismicLinks", t);
    }

    public void load(CompoundTag tag, boolean client) {
        if (!tag.contains("SeismicLinks", Tag.TAG_COMPOUND)) {
            if (client) {
                this.linked.clear();
                this.known.clear();
                this.numbers.clear();
                this.rigStates.clear();
                this.scene = SurveyScene.EMPTY;
                this.sceneStamp = Long.MIN_VALUE;
                this.wireStamp = Long.MIN_VALUE;
                this.sceneFull = false;
            }
            return;
        }
        final CompoundTag t = tag.getCompound("SeismicLinks");
        final Map<BlockPos, Long> previous = new LinkedHashMap<>(this.linked);
        this.linked.clear();
        final ListTag links = t.getList("Linked", Tag.TAG_COMPOUND);
        for (int i = 0; i < links.size() && i < MAX_KNOWN; i++) {
            final CompoundTag l = links.getCompound(i);
            final BlockPos pos = BlockPos.of(l.getLong("Pos"));
            this.linked.put(pos, l.contains("Beat") ? l.getLong("Beat") : previous.getOrDefault(pos, 0L));
        }
        this.known.clear();
        this.numbers.clear();
        final long[] keys = t.getLongArray("Known");
        final int[] nums = t.getIntArray("Numbers");
        int highest = 0;
        for (int i = 0; i < keys.length && this.known.size() < MAX_KNOWN; i++) {
            this.known.add(keys[i]);

            final int n = i < nums.length && nums[i] > 0 ? nums[i] : i + 1;
            this.numbers.put(keys[i], n);
            highest = Math.max(highest, n);
        }
        this.nextNumber = Math.max(highest + 1, t.contains("NextNumber") ? t.getInt("NextNumber") : 1);
        this.since = t.contains("Since") ? t.getLong("Since") : Long.MIN_VALUE;
        this.worked.clear();
        final long[] marks = t.getLongArray("Worked");
        for (int i = 0; i + 1 < marks.length && this.worked.size() < MAX_WORKED; i += 2) {
            this.worked.put(marks[i], marks[i + 1]);
        }
        if (client) {
            this.wireStamp = t.contains("Stamp") ? t.getLong("Stamp") : Long.MIN_VALUE;
            this.rigStates.clear();
            final ListTag rigs = t.getList("Rigs", Tag.TAG_COMPOUND);
            for (int i = 0; i < rigs.size() && i < MAX_KNOWN; i++) {
                final CompoundTag r = rigs.getCompound(i);
                final String target = r.getString("Target");
                this.rigStates.put(r.getLong("Pos"), new RigState(r.getInt("State"),
                        Math.max(0, Math.min(100, r.getInt("Charge"))), r.getInt("Radius"),
                        target.length() > 128 ? "" : target, r.getBoolean("Coupled")));
            }
            if (this.known.isEmpty()) {
                this.scene = SurveyScene.EMPTY;
                this.sceneStamp = Long.MIN_VALUE;
                this.sceneFull = false;
            }
        }
    }

    public SurveyScene scene() {
        return this.scene;
    }

    public boolean received() {
        return this.sceneStamp != Long.MIN_VALUE;
    }

    public boolean behind() {
        return this.wireStamp != this.sceneStamp;
    }

    public void requestIfNeeded(long tick) {
        requestIfNeeded(tick, false);
    }

    public void requestIfNeeded(long tick, boolean page) {
        if (this.known.isEmpty()) {
            return;
        }
        final boolean behind = behind() || (page && !this.sceneFull);
        final long since = tick - this.lastRequest;
        if (this.lastRequest != Long.MIN_VALUE && since >= 0 && since < (behind ? REQUEST_GAP : WATCH_TICKS / 3)) {
            return;
        }
        this.lastRequest = tick;
        PacketDistributor.sendToServer(new SeismicPanelPacket(this.panel.getBlockPos(),
                page ? OP_REQUEST_PAGE : OP_REQUEST, page && !this.sceneFull ? Long.MIN_VALUE : this.sceneStamp));
    }

    public void acceptScene(long stamp, boolean full, byte[] data) {
        try {
            this.scene = SceneCodec.decode(data);
            this.sceneStamp = stamp;
            this.sceneFull = full;
            if (this.wireStamp == Long.MIN_VALUE) {
                this.wireStamp = stamp;
            }
        } catch (IOException e) {
            CreateStats.LOGGER.debug("[Seismic] dropped a scene packet: {}", e.getMessage());
        }
    }
}
