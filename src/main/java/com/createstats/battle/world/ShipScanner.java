package com.createstats.battle.world;

import com.createstats.battle.core.BattleReport;
import com.createstats.battle.core.BattleSystem;
import com.createstats.battle.core.BowGuess;
import com.createstats.battle.core.PackedPos;
import com.createstats.battle.core.ShipFrame;
import com.createstats.battle.core.SurveyStore;
import com.createstats.flight.bridge.SableBridge;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntIterator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

final class ShipScanner {
    static final byte CAUSE_REFIT = 100;
    static final byte CAUSE_ADD = 101;

    static final int SURVEY_BUDGET = 16_384;
    static final int LOSS_BUDGET = 4_096;
    static final int QUEUE_BUDGET = 4_096;
    static final int GROWTH_BUDGET = 16_384;
    static final long GROWTH_EVERY = 1200L;
    static final long FIRE_MEMORY = 40L;
    private static final Direction[] DIRS = Direction.values();

    private ShipScanner() {
    }

    record Area(List<LevelChunk> chunks, @Nullable Object subLevel, @Nullable AABB bounds) {
    }

    static void tick(ShipRecord r, ServerLevel level, BlockPos master, net.minecraft.core.Direction masterFacing, long now) {
        Area area = area(r, level);
        if (area == null) return;
        r.bridgeDy = master.getY() - r.origin().getY();
        if (now % 20 == 0) {
            r.playerAboard = playerAboard(level, area);
            if (area.subLevel() != null) {
                String name = SableBridge.subLevelName(area.subLevel());
                if (!name.equals(r.shipName)) {
                    r.shipName = name;
                    r.changed();
                }
            }
        }
        r.expirePending(now);

        if (r.survey == null && (r.surveyWanted || r.store.isEmpty())) {
            r.survey = new SurveyJob(r, area, master, masterFacing);
            r.surveyWanted = false;
            r.growth = null;
            r.changed();
        }
        if (r.survey != null) {
            if (r.survey.step(level, SURVEY_BUDGET)) {
                r.survey.install(level, now);
                r.survey = null;
                r.nextGrowthAt = now + GROWTH_EVERY;
            } else if (now % 10 == 0) {
                r.changed();
            }
            return;
        }

        ChunkCache cache = new ChunkCache();
        processQueued(r, level, cache, now);
        for (int i = 0; i < LOSS_BUDGET && i < r.store.size(); i++) {
            int slot = r.store.nextSlot();
            if (slot < 0) break;
            check(r, level, slot, now, cache);
        }
        r.checkSeparation(now);
        if (now % 20 == 0) fireScan(r, level, area, now);

        if (r.growth == null && now >= r.nextGrowthAt) r.growth = new GrowthJob(r, area);
        if (r.growth != null && r.growth.step(level, GROWTH_BUDGET)) {
            if (r.newBlocks != r.growth.count) {
                r.newBlocks = r.growth.count;
                r.changed();
            }
            r.growth = null;
            r.nextGrowthAt = now + GROWTH_EVERY;
        }
        r.nameDirty();
        tellCrew(r, level, area, now);
        r.expireMarkers(now);
    }

    // master only, three bridges still means one message per hit
    private static void tellCrew(ShipRecord r, ServerLevel level, Area area, long now) {
        List<ShipRecord.HitAcc> closed = r.closedHits(now);
        if (closed.isEmpty() || (r.options & BattleReport.OPT_CHAT) == 0 || area.bounds() == null) return;
        List<ServerPlayer> crew = level.getEntitiesOfClass(ServerPlayer.class, area.bounds().inflate(1.5), p -> !p.isSpectator());
        if (crew.isEmpty()) return;
        for (ShipRecord.HitAcc h : closed) {
            Component line = BattleText.tr("chat.hit", BattleText.place(h.place), h.count);
            for (ServerPlayer p : crew) p.sendSystemMessage(line);
        }
    }

    @Nullable
    static Area area(ShipRecord r, ServerLevel level) {
        if (!r.ground()) {
            Object sub = r.key().ship() == null ? null : SableBridge.findSubLevelById(level, r.key().ship());
            if (sub == null) return null;
            return new Area(SablePlots.plotChunks(sub), sub, SablePlots.worldBounds(sub));
        }
        BlockPos o = r.origin();
        int half = r.groundSize / 2;
        List<LevelChunk> chunks = new ArrayList<>();
        for (int cx = (o.getX() - half) >> 4; cx <= (o.getX() + half) >> 4; cx++) {
            for (int cz = (o.getZ() - half) >> 4; cz <= (o.getZ() + half) >> 4; cz++) {
                LevelChunk c = level.getChunkSource().getChunk(cx, cz, false);
                if (c != null) chunks.add(c);
            }
        }
        AABB box = new AABB(o.getX() - half, o.getY() - half, o.getZ() - half, o.getX() + half + 1, o.getY() + half + 1, o.getZ() + half + 1);
        return new Area(chunks, null, box);
    }

    private static boolean playerAboard(ServerLevel level, Area area) {
        if (area.bounds() == null) return false;
        AABB box = area.bounds().inflate(1.5);
        for (Player p : level.getEntitiesOfClass(Player.class, box)) {
            if (!p.isSpectator()) return true;
        }
        return false;
    }

    private static void processQueued(ShipRecord r, ServerLevel level, ChunkCache cache, long now) {
        int n = 0;
        IntIterator it = r.queued.iterator();
        while (it.hasNext() && n++ < QUEUE_BUDGET) {
            int packed = it.nextInt();
            it.remove();
            int slot = r.store.slotOf(packed);
            if (slot >= 0) {
                check(r, level, slot, now, cache);
                continue;
            }
            ShipRecord.Pending pd = r.takePending(packed, now);
            if (pd == null || pd.cause() != CAUSE_ADD) continue;
            BlockPos p = r.unpack(packed);
            LevelChunk chunk = cache.get(level, p);
            if (chunk == null) continue;
            BlockState st = chunk.getBlockState(p);
            if (!BlockInfo.counts(st, r.ground()) || (r.ground() && !r.inGroundBox(p))) continue;
            String id = BlockInfo.id(st.getBlock());
            int pal = r.store.paletteIndexOf(id);
            BattleSystem sys = pal >= 0 ? r.store.paletteSystem(pal) : BlockInfo.classify(level, p, st, chunk);
            if (r.store.add(packed, id, sys) >= 0) {
                r.refit(pd.who(), 1, now);
                r.shapeChanged();
            }
        }
    }

    static void check(ShipRecord r, ServerLevel level, int slot, long now, ChunkCache cache) {
        int packed = r.store.packedAt(slot);
        BlockPos p = r.unpack(packed);
        LevelChunk chunk = cache.get(level, p);
        if (chunk == null) return;
        BlockState st = chunk.getBlockState(p);
        if (BlockInfo.isGone(st)) {
            if (r.store.isLost(slot)) {
                r.takePending(packed, now);
                return;
            }
            ShipRecord.Pending pd = r.takePending(packed, now);
            String blockId = r.store.blockAt(slot);
            if (pd != null && pd.cause() == CAUSE_REFIT) {
                r.store.remove(slot);
                r.refit(pd.who(), 1, now);
                r.shapeChanged();
                return;
            }
            byte cause;
            int group = 0;
            if (pd != null && pd.cause() != CAUSE_ADD) {
                cause = pd.cause();
                group = pd.group();
            } else if (BlockInfo.isFire(st) || now - r.fireTouched.getOrDefault(packed, Long.MIN_VALUE / 2) <= FIRE_MEMORY) {
                cause = SurveyStore.CAUSE_FIRE;
            } else {
                cause = SurveyStore.CAUSE_HIT;
            }
            r.loss(slot, cause, now, group, blockId);
            return;
        }
        r.takePending(packed, now);
        Block b = st.getBlock();
        boolean same = b == r.paletteBlock(r.store.paletteAt(slot));
        if (same && !r.store.isLost(slot)) return;
        String id = BlockInfo.id(b);
        int pal = r.store.paletteIndexOf(id);
        BattleSystem sys = pal >= 0 ? r.store.paletteSystem(pal) : BlockInfo.classify(level, p, st, chunk);
        if (r.store.isLost(slot)) {
            r.putBack(slot, id, sys);
        } else {
            r.store.adopt(slot, id, sys);
            r.changed();
        }
    }

    private static void fireScan(ShipRecord r, ServerLevel level, Area area, long now) {
        boolean any = false;
        int firstCell = -1;
        for (LevelChunk chunk : area.chunks()) {
            LevelChunkSection[] sections = chunk.getSections();
            for (int i = 0; i < sections.length; i++) {
                LevelChunkSection s = sections[i];
                if (s == null || s.hasOnlyAir() || !s.maybeHas(st -> st.is(BlockTags.FIRE))) continue;
                int baseX = chunk.getPos().getMinBlockX();
                int baseY = chunk.getSectionYFromSectionIndex(i) << 4;
                int baseZ = chunk.getPos().getMinBlockZ();
                for (int y = 0; y < 16; y++) {
                    for (int z = 0; z < 16; z++) {
                        for (int x = 0; x < 16; x++) {
                            if (!s.getBlockState(x, y, z).is(BlockTags.FIRE)) continue;
                            BlockPos fp = new BlockPos(baseX + x, baseY + y, baseZ + z);
                            if (r.ground() && !r.inGroundBox(fp)) continue;
                            for (int d = -1; d < DIRS.length; d++) {
                                BlockPos q = d < 0 ? fp : fp.relative(DIRS[d]);
                                int packed = r.pack(q);
                                if (packed < 0 || r.store.slotOf(packed) < 0) continue;
                                r.fireTouched.put(packed, now);
                                any = true;
                                if (firstCell < 0) firstCell = packed;
                            }
                        }
                    }
                }
            }
        }
        r.fireTouched.int2LongEntrySet().removeIf(e -> now - e.getLongValue() > FIRE_MEMORY * 5);
        String place = any ? r.namer().name(new int[]{firstCell}, com.createstats.battle.core.Places.KIND_DAMAGE) : "";
        if (any != r.fireAboard || !place.equals(r.firePlace)) {
            r.fireAboard = any;
            r.firePlace = place;
            r.changed();
        }
        if (any && now - r.lastFireTone >= 200) {
            r.lastFireTone = now;
            r.fireToneSeq++;
        }
    }

    abstract static class SectionWalk {
        final ShipRecord record;
        final List<LevelChunk> chunks;
        int chunkIndex;
        int sectionIndex;
        int sectionsDone;
        final int sectionsTotal;

        SectionWalk(ShipRecord record, Area area) {
            this.record = record;
            this.chunks = area.chunks();
            int total = 0;
            for (LevelChunk c : chunks) {
                for (LevelChunkSection s : c.getSections()) {
                    if (s != null && !s.hasOnlyAir()) total++;
                }
            }
            this.sectionsTotal = total;
        }

        boolean step(ServerLevel level, int budget) {
            int used = 0;
            while (chunkIndex < chunks.size()) {
                LevelChunk chunk = chunks.get(chunkIndex);
                LevelChunkSection[] sections = chunk.getSections();
                if (sectionIndex >= sections.length) {
                    chunkIndex++;
                    sectionIndex = 0;
                    continue;
                }
                if (used >= budget) return false;
                LevelChunkSection s = sections[sectionIndex];
                int index = sectionIndex++;
                if (s == null || s.hasOnlyAir()) continue;
                sectionsDone++;
                used += 4096;
                int baseX = chunk.getPos().getMinBlockX();
                int baseY = chunk.getSectionYFromSectionIndex(index) << 4;
                int baseZ = chunk.getPos().getMinBlockZ();
                for (int y = 0; y < 16; y++) {
                    for (int z = 0; z < 16; z++) {
                        for (int x = 0; x < 16; x++) {
                            BlockState st = s.getBlockState(x, y, z);
                            if (st.isAir()) continue;
                            visit(level, chunk, baseX + x, baseY + y, baseZ + z, st);
                        }
                    }
                }
            }
            return true;
        }

        abstract void visit(ServerLevel level, LevelChunk chunk, int x, int y, int z, BlockState st);

        int permille() {
            return sectionsTotal == 0 ? 0 : (int) Math.min(999L, sectionsDone * 1000L / sectionsTotal);
        }
    }

    static final class SurveyJob extends SectionWalk {
        private final BlockPos master;
        private final Direction masterFacing;
        private final IntArrayList packed = new IntArrayList();
        private final IntArrayList blockIndex = new IntArrayList();
        private final Map<Block, Integer> blocks = new IdentityHashMap<>();
        private final List<Block> blockList = new ArrayList<>();
        private final List<BlockPos> samples = new ArrayList<>();
        private final List<BowGuess.Thrust> thrusts = new ArrayList<>();
        private final IntArrayList propellers = new IntArrayList();
        private boolean outOfRange;

        SurveyJob(ShipRecord record, Area area, BlockPos master, Direction masterFacing) {
            super(record, area);
            this.master = master;
            this.masterFacing = masterFacing;
        }

        @Override
        void visit(ServerLevel level, LevelChunk chunk, int x, int y, int z, BlockState st) {
            BlockPos p = new BlockPos(x, y, z);
            if (record.ground() && !record.inGroundBox(p)) return;
            if (!BlockInfo.counts(st, record.ground())) return;
            int pk = record.pack(p);
            if (pk < 0) {
                outOfRange = true;
                return;
            }
            Block b = st.getBlock();
            Integer bi = blocks.get(b);
            if (bi == null) {
                bi = blockList.size();
                blocks.put(b, bi);
                blockList.add(b);
                samples.add(p);
            }
            packed.add(pk);
            blockIndex.add(bi.intValue());
            if (!record.ground() && st.hasBlockEntity()) {
                BlockEntity be = chunk.getBlockEntity(p);
                if (be != null && SableBridge.matchesAnyPropeller(be)) {
                    propellers.add(pk);
                    SableBridge.PropData prop = SableBridge.readProp(be);
                    if (prop != null && prop.thrustDirLocal() != null) {
                        Vector3d d = prop.thrustDirLocal();
                        double sign = prop.thrustSigned() < 0 ? -1.0 : 1.0;
                        double w = prop.sailPower() > 0 ? prop.sailPower() : 1.0;
                        thrusts.add(new BowGuess.Thrust(d.x * sign, d.y * sign, d.z * sign, w));
                    }
                }
            }
        }

        void install(ServerLevel level, long now) {
            ShipRecord r = record;
            int n = packed.size();
            Integer[] order = new Integer[n];
            for (int i = 0; i < n; i++) order[i] = i;
            boolean capped = outOfRange;
            if (n > SurveyStore.CAP) {
                int mp = r.pack(master);
                int mx = mp < 0 ? 0 : PackedPos.dx(mp);
                int my = mp < 0 ? 0 : PackedPos.dy(mp);
                int mz = mp < 0 ? 0 : PackedPos.dz(mp);
                java.util.Arrays.sort(order, Comparator.comparingLong(i -> {
                    int q = packed.getInt(i);
                    long dx = PackedPos.dx(q) - mx;
                    long dy = PackedPos.dy(q) - my;
                    long dz = PackedPos.dz(q) - mz;
                    return dx * dx + dy * dy + dz * dz;
                }));
                capped = true;
            }
            BattleSystem[] systems = new BattleSystem[blockList.size()];
            for (int i = 0; i < blockList.size(); i++) {
                BlockPos sp = samples.get(i);
                LevelChunk c = level.getChunkSource().getChunk(sp.getX() >> 4, sp.getZ() >> 4, false);
                BlockState st = c != null ? c.getBlockState(sp) : blockList.get(i).defaultBlockState();
                systems[i] = BlockInfo.classify(level, sp, st, c);
            }
            r.store.clear();
            r.places.clear();
            r.bannerCells.clear();
            r.fireTouched.clear();
            r.separationWindow.clear();
            r.pending.clear();
            r.queued.clear();
            r.resetPaletteBlocks();
            for (int k = 0; k < Math.min(n, SurveyStore.CAP); k++) {
                int i = order[k];
                int bi = blockIndex.getInt(i);
                r.store.add(packed.getInt(i), BlockInfo.id(blockList.get(bi)), systems[bi]);
            }
            r.store.setTaken(now);
            r.capped = capped;
            r.newBlocks = 0;

            ShipFrame f = r.frame();
            List<Integer> props = new ArrayList<>();
            for (int i = 0; i < propellers.size(); i++) {
                if (r.store.slotOf(propellers.getInt(i)) >= 0) props.add(propellers.getInt(i));
            }
            if (!r.bowDecided && !r.ground()) {
                int guess = BowGuess.guess(thrusts);
                if (guess >= 0) {
                    r.bow = guess;
                    r.bowSource = BattleReport.BOW_THRUST;
                } else {
                    Direction toBow = masterFacing.getOpposite();
                    r.bow = ShipFrame.quarterOf(toBow.getStepX(), toBow.getStepZ());
                    r.bowSource = BattleReport.BOW_PLACER;
                }
                r.bowDecided = true;
                f = r.frame();
            }
            final ShipFrame frame = f;
            props.sort(Comparator.<Integer>comparingInt(p -> -frame.fore(PackedPos.dx(p), PackedPos.dz(p)))
                    .thenComparingInt(p -> frame.stbd(PackedPos.dx(p), PackedPos.dz(p))));
            r.props.clear();
            for (int i = 0; i < props.size(); i++) r.props.add(new ShipRecord.Prop(i + 1, props.get(i)));
            r.shapeChanged();
            r.lostChanged();
        }
    }

    static final class GrowthJob extends SectionWalk {
        int count;

        GrowthJob(ShipRecord record, Area area) {
            super(record, area);
        }

        @Override
        void visit(ServerLevel level, LevelChunk chunk, int x, int y, int z, BlockState st) {
            if (!BlockInfo.counts(st, record.ground())) return;
            BlockPos p = new BlockPos(x, y, z);
            if (record.ground() && !record.inGroundBox(p)) return;
            int pk = record.pack(p);
            if (pk >= 0 && record.store.slotOf(pk) < 0) count++;
        }
    }

    static final class ChunkCache {
        private long key = Long.MIN_VALUE;
        @Nullable
        private LevelChunk chunk;

        @Nullable
        LevelChunk get(ServerLevel level, BlockPos p) {
            long k = (((long) (p.getX() >> 4)) << 32) ^ ((p.getZ() >> 4) & 0xFFFFFFFFL);
            if (k != key) {
                key = k;
                chunk = level.getChunkSource().getChunk(p.getX() >> 4, p.getZ() >> 4, false);
            }
            return chunk;
        }
    }
}
