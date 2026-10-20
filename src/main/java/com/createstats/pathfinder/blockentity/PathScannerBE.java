package com.createstats.pathfinder.blockentity;

import com.createstats.CreateStats;
import com.createstats.flight.bridge.SableBridge;
import com.createstats.pathfinder.block.PathScannerBlock;
import com.createstats.pathfinder.menu.PathScannerMenu;
import com.createstats.pathfinder.model.ChunkScanCacheEntry;
import com.createstats.pathfinder.model.HazardFlags;
import com.createstats.pathfinder.model.ScanRegion;
import com.createstats.pathfinder.model.UnknownPolicy;
import com.createstats.pathfinder.settings.PathScanSettings;
import com.createstats.pathfinder.util.DrivabilityScanner;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.joml.Quaterniond;
import org.joml.Vector3d;

public class PathScannerBE extends BlockEntity implements MenuProvider, IHaveGoggleInformation, com.createstats.power.IFeConsumer,
        com.createstats.util.CopyableSettings {
    private final com.createstats.power.FeConsumer feConsumer = com.createstats.power.FeConsumer
            .forBlockEntity(this, com.createstats.power.FeProfiles.settings(com.createstats.power.FeProfiles.KEY_PATH_SCANNER));

    public com.createstats.power.FeConsumer feConsumer() {
        return feConsumer;
    }
    public static final int SCHEMA_VERSION = 1;
    private static final int CHUNK_CELL_COUNT = 16 * 16;
    public static final int MAX_QUEUE_CHUNKS = Math.max(1024, PathScanSettings.MAX_SCAN_CHUNKS);
    private static final int MAX_CACHE_ENTRIES = Math.max(256, PathScanSettings.MAX_SCAN_CHUNKS);
    private static final int MAX_PERSISTED_CACHE_ENTRIES = 16;
    private static final int CLIENT_SYNC_INTERVAL_TICKS = 10;
    private static final int VEHICLE_SCAN_MIN_CHUNKS_PER_TICK = 5;
    public static final int HAZARD_UNKNOWN_CHUNK = HazardFlags.UNKNOWN_CHUNK;

    private UUID scannerId;
    private PathScanSettings settings;
    private final ArrayDeque<Long> scanQueue;
    private final Set<Long> queuedChunks;
    private final LinkedHashMap<Long, ChunkScanCacheEntry> chunkCache;
    @Nullable
    private ScanRegion currentRegion;
    private long lastScannerChunkKey;
    private Direction lastFacing;
    private int lastYawBucket = Integer.MIN_VALUE;
    private boolean manualRescanRequested;
    private long lastClientSyncTick;
    private int expectedChunkCount;
    private int loadedChunkCount;
    private int unknownChunkCount;
    private int totalPassableCells;
    private int totalMarginalCells;
    private int totalBlockedCells;
    private long lastScanTick;
    private int lastProcessedChunks;
    private int lastEffectiveChunkBudget;
    private long lastDebugLogTick = Long.MIN_VALUE;
    private boolean remotePaused;
    @Nullable
    private BlockPos externalScanOrigin;
    private float externalScanYawDeg = 180f;
    private int externalForwardChunks = PathScanSettings.DEFAULT_FORWARD_CHUNKS;
    private int externalSideChunks = PathScanSettings.DEFAULT_SIDE_CHUNKS;
    private long externalScanFrameTick = Long.MIN_VALUE;

    public PathScannerBE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        this.scannerId = UUID.randomUUID();
        this.settings = PathScanSettings.defaults();
        this.scanQueue = new ArrayDeque<>();
        this.queuedChunks = new HashSet<>();
        this.chunkCache = new LinkedHashMap<>(64, 0.75f, true);
        this.lastScannerChunkKey = Long.MIN_VALUE;
        this.lastFacing = Direction.NORTH;
    }

    public UUID scannerId() {
        return scannerId;
    }

    public PathScanSettings settings() {
        return settings;
    }

    public void setSettings(PathScanSettings settings) {
        this.settings = settings == null ? PathScanSettings.defaults() : settings;
        this.requestManualRescan();
        this.markDirtyAndSync();
    }

    @Override
    public String settingsKey() {
        return "create_stats:path_scanner";
    }

    @Override
    public void writeSettings(CompoundTag tag, HolderLookup.Provider registries) {
        tag.put("Settings", settings.save());
    }

    @Override
    public boolean applySettings(CompoundTag tag, HolderLookup.Provider registries, @Nullable Player player,
                                 boolean simulate) {
        if (!tag.contains("Settings")) {
            return false;
        }
        // a held button pastes every tick, don't rescan on repeats
        if (!simulate && !alreadyHolds(tag, registries)) {
            setSettings(PathScanSettings.load(tag.getCompound("Settings")));
        }
        return true;
    }

    public void requestManualRescan() {
        this.manualRescanRequested = true;
    }

    public int queueDepth() {
        return scanQueue.size();
    }

    public int expectedChunkCount() {
        return expectedChunkCount;
    }

    public int loadedChunkCount() {
        return loadedChunkCount;
    }

    public int unknownChunkCount() {
        return unknownChunkCount;
    }

    public int totalPassableCells() {
        return totalPassableCells;
    }

    public int totalMarginalCells() {
        return totalMarginalCells;
    }

    public int totalBlockedCells() {
        return totalBlockedCells;
    }

    public long lastScanTick() {
        return lastScanTick;
    }

    public int lastProcessedChunks() {
        return lastProcessedChunks;
    }

    public BlockPos debugScanOrigin() {
        return currentRegion == null ? this.worldPosition : currentRegion.origin();
    }

    public String debugFacingLabel() {
        if (currentRegion != null) {
            return String.format(java.util.Locale.ROOT, "yaw %.0f", currentRegion.yawDeg());
        }
        return lastFacing == null ? "unknown" : lastFacing.getName();
    }

    public void setVehicleScanFrame(BlockPos origin, float yawDeg, long gameTime) {
        this.setVehicleScanFrame(origin, yawDeg, gameTime, PathScanSettings.DEFAULT_FORWARD_CHUNKS,
            PathScanSettings.DEFAULT_SIDE_CHUNKS);
    }

    public void setVehicleScanFrame(BlockPos origin, float yawDeg, long gameTime, int minForwardChunks, int minSideChunks) {
        if (origin == null) return;
        this.externalScanOrigin = origin.immutable();
        this.externalScanYawDeg = normalizeYaw(yawDeg);
        this.externalForwardChunks = clamp(minForwardChunks, 1, PathScanSettings.MAX_FORWARD_CHUNKS);
        this.externalSideChunks = clamp(minSideChunks, 0, PathScanSettings.MAX_SIDE_CHUNKS);
        this.externalScanFrameTick = gameTime;
    }

    public String debugLine() {
        BlockPos origin = debugScanOrigin();
        String frame = hasActiveVehicleScanFrame() ? "vehicle" : "scanner";
        String paused = remotePaused ? " paused" : "";
        int forwardChunks = currentRegion == null ? settings.forwardChunks() : currentRegion.forwardChunks();
        int sideChunks = currentRegion == null ? settings.sideChunks() : currentRegion.sideChunks();
        boolean allowTwoBlock = currentRegion == null ? settings.allowTwoBlockPaths() : settings.allowTwoBlockPaths() || hasActiveVehicleScanFrame();
        UnknownPolicy unknownPolicy = currentRegion == null ? settings.unknownPolicy() : currentRegion.unknownPolicy();
        return String.format(java.util.Locale.ROOT,
            "frame=%s%s origin=%d,%d,%d facing=%s scan=%dx%d twoStep=%s unknown=%s stride=%d budget=%d queue=%d loaded=%d/%d unknown=%d cells[p=%d m=%d b=%d] lastTick=%d lastChunks=%d",
            frame, paused, origin.getX(), origin.getY(), origin.getZ(), debugFacingLabel(), forwardChunks, sideChunks,
            allowTwoBlock, unknownPolicy, activeChunkStride(), Math.max(settings.chunksPerTick(), lastEffectiveChunkBudget), queueDepth(), loadedChunkCount, Math.max(1, expectedChunkCount),
            unknownChunkCount, totalPassableCells, totalMarginalCells, totalBlockedCells, lastScanTick, lastProcessedChunks);
    }

    public boolean isRemotePaused() {
        return remotePaused;
    }

    public void setRemotePaused(boolean paused) {
        if (this.remotePaused == paused) return;
        this.remotePaused = paused;
        if (paused) {
            this.scanQueue.clear();
            this.queuedChunks.clear();
            this.lastProcessedChunks = 0;
        } else {
            this.requestManualRescan();
        }
        this.markDirtyAndSync();
    }

    @Nullable
    public ScanRegion currentRegion() {
        return currentRegion;
    }

    public boolean hasLoadedCenterCorridor(int sideRadius) {
        if (this.currentRegion == null) return false;
        int safeSideRadius = Math.max(0, Math.min(sideRadius, this.currentRegion.sideChunks()));
        Direction facing = facingForYaw(this.currentRegion.yawDeg());
        int baseChunkX = this.currentRegion.origin().getX() >> 4;
        int baseChunkZ = this.currentRegion.origin().getZ() >> 4;
        int forwardX = facing.getStepX();
        int forwardZ = facing.getStepZ();
        int rightX = -forwardZ;
        int rightZ = forwardX;
        for (int forward = 0; forward < this.currentRegion.forwardChunks(); forward++) {
            for (int side = -safeSideRadius; side <= safeSideRadius; side++) {
                long key = chunkKey(baseChunkX + forwardX * forward + rightX * side,
                    baseChunkZ + forwardZ * forward + rightZ * side);
                ChunkScanCacheEntry entry = this.chunkCache.get(key);
                if (entry == null || !entry.loaded()) return false;
            }
        }
        return true;
    }

    @Nullable
    public ChunkScanCacheEntry getCacheEntry(int chunkX, int chunkZ) {
        return chunkCache.get(chunkKey(chunkX, chunkZ));
    }

    public List<ChunkScanCacheEntry> recentCacheEntries(int maxEntries) {
        int limit = Math.max(0, maxEntries);
        if (limit == 0 || this.chunkCache.isEmpty()) return List.of();
        List<ChunkScanCacheEntry> entries = new ArrayList<>(this.chunkCache.values());
        int from = Math.max(0, entries.size() - limit);
        return List.copyOf(entries.subList(from, entries.size()));
    }

    public void tick() {
        if (!(this.level instanceof ServerLevel serverLevel)) {
            return;
        }

        final boolean fePowered = this.feConsumer.serviceTick(this.scanQueue.size() > 0);
        com.createstats.power.FePowerFx.handleTransition(this.level, this.worldPosition, this.feConsumer);
        if (!fePowered) {
            this.lastProcessedChunks = 0;
            return;
        }
        if (this.remotePaused) {
            this.lastProcessedChunks = 0;
            return;
        }
        ScanFrame scanFrame = this.currentScanFrame(serverLevel);
        PathScanSettings activeSettings = this.activeScanSettings(serverLevel);
        BlockPos scanOrigin = scanFrame.origin();
        Direction facing = scanFrame.facing();
        long scannerChunkKey = chunkKey(scanOrigin.getX() >> 4, scanOrigin.getZ() >> 4);
        int yawBucket = yawBucket(scanFrame.yawDeg());
        boolean regionChanged = this.currentRegion == null
            || this.lastScannerChunkKey != scannerChunkKey
            || this.lastFacing != facing
            || this.lastYawBucket != yawBucket
            || this.currentRegion.forwardChunks() != activeSettings.forwardChunks()
            || this.currentRegion.sideChunks() != activeSettings.sideChunks()
            || this.manualRescanRequested;

        if (regionChanged && this.feConsumer.spendBurst()) {
            this.enqueueCurrentRegion(serverLevel, this.manualRescanRequested || this.currentRegion == null,
                true, scanOrigin, facing, scanFrame.yawDeg(), activeSettings);
            this.lastScannerChunkKey = scannerChunkKey;
            this.lastFacing = facing;
            this.lastYawBucket = yawBucket;
            this.manualRescanRequested = false;
        }
        this.processQueue(serverLevel);
        this.logDebug(serverLevel, regionChanged, scanOrigin, facing);
    }

    private void logDebug(ServerLevel serverLevel, boolean regionChanged, BlockPos scanOrigin, Direction facing) {
        if (!pathfinderDebugLoggingEnabled()) return;
        boolean active = regionChanged || this.lastProcessedChunks > 0 || this.scanQueue.size() > 0;
        if (!active && this.lastDebugLogTick != Long.MIN_VALUE && serverLevel.getGameTime() - this.lastDebugLogTick < 80L) {
            return;
        }
        if (!regionChanged && this.lastDebugLogTick != Long.MIN_VALUE && serverLevel.getGameTime() - this.lastDebugLogTick < 40L) {
            return;
        }
        this.lastDebugLogTick = serverLevel.getGameTime();
        CreateStats.LOGGER.info("[PFDEBUG/SCANNER] pos={} worldOrigin={} facing={} regionChanged={} {}",
            this.worldPosition, scanOrigin, facing, regionChanged, this.debugLine());
    }

    private static boolean pathfinderDebugLoggingEnabled() {
        return false;
    }

    private void enqueueCurrentRegion(ServerLevel serverLevel, boolean forceRescan, boolean clearPendingQueue,
                                      BlockPos scanOrigin, Direction facing, float yawDeg, PathScanSettings activeSettings) {
        this.currentRegion = ScanRegion.fromSettings(scanOrigin, yawDeg, activeSettings);
        this.expectedChunkCount = activeSettings.expectedChunkCount();
        if (forceRescan || clearPendingQueue) {
            this.scanQueue.clear();
            this.queuedChunks.clear();
        }

        int baseChunkX = scanOrigin.getX() >> 4;
        int baseChunkZ = scanOrigin.getZ() >> 4;
        int forwardX = facing.getStepX();
        int forwardZ = facing.getStepZ();
        int rightX = -forwardZ;
        int rightZ = forwardX;
        List<Long> primary = new ArrayList<>();
        List<Long> secondary = new ArrayList<>();

        for (int sideDistance = 0; sideDistance <= activeSettings.sideChunks(); sideDistance++) {
            for (int forward = 0; forward < activeSettings.forwardChunks(); forward++) {
                if (sideDistance == 0) {
                    addScanKey(primary, secondary, baseChunkX, baseChunkZ, forwardX, forwardZ, rightX, rightZ, forward, 0);
                } else {
                    addScanKey(primary, secondary, baseChunkX, baseChunkZ, forwardX, forwardZ, rightX, rightZ, forward, -sideDistance);
                    addScanKey(primary, secondary, baseChunkX, baseChunkZ, forwardX, forwardZ, rightX, rightZ, forward, sideDistance);
                }
            }
            for (int forward = -1; forward >= -PathScanSettings.BACKWARD_CHUNKS; forward--) {
                if (sideDistance == 0) {
                    addScanKey(primary, secondary, baseChunkX, baseChunkZ, forwardX, forwardZ, rightX, rightZ, forward, 0);
                } else {
                    addScanKey(primary, secondary, baseChunkX, baseChunkZ, forwardX, forwardZ, rightX, rightZ, forward, -sideDistance);
                    addScanKey(primary, secondary, baseChunkX, baseChunkZ, forwardX, forwardZ, rightX, rightZ, forward, sideDistance);
                }
            }
        }

        for (long key : primary) enqueueChunk(serverLevel, key, forceRescan);
        for (long key : secondary) enqueueChunk(serverLevel, key, forceRescan);
        this.recalculateCoverageStats();
        this.markDirtyAndSync();
    }

    private void addScanKey(List<Long> primary, List<Long> secondary, int baseChunkX, int baseChunkZ,
                            int forwardX, int forwardZ, int rightX, int rightZ, int forward, int side) {
        int chunkX = baseChunkX + forwardX * forward + rightX * side;
        int chunkZ = baseChunkZ + forwardZ * forward + rightZ * side;
        long key = chunkKey(chunkX, chunkZ);
        boolean primaryStride = this.activeChunkStride() == 1 || ((Math.abs(forward) + Math.abs(side)) % 2 == 0);
        if (primaryStride) primary.add(key); else secondary.add(key);
    }

    private void enqueueChunk(ServerLevel serverLevel, long key, boolean forceRescan) {
        if (this.scanQueue.size() >= MAX_QUEUE_CHUNKS || this.queuedChunks.contains(key)) {
            return;
        }
        ChunkScanCacheEntry existing = this.chunkCache.get(key);
        if (!forceRescan && existing != null && serverLevel.getGameTime() - existing.lastScannedTick() < this.settings.cacheTtlTicks()) {
            return;
        }
        this.scanQueue.addLast(key);
        this.queuedChunks.add(key);
    }

    private void processQueue(ServerLevel serverLevel) {
        ServerLevel scanLevel = this.scanLevel(serverLevel);
        this.lastProcessedChunks = 0;
        int requestedBudget = this.settings.chunksPerTick();
        if (this.hasRecentVehicleScanFrame(serverLevel)) {
            requestedBudget = Math.max(requestedBudget, VEHICLE_SCAN_MIN_CHUNKS_PER_TICK);
        }
        int budget = Math.max(1, Math.min(PathScanSettings.MAX_CHUNKS_PER_TICK, requestedBudget));
        this.lastEffectiveChunkBudget = budget;
        while (this.lastProcessedChunks < budget && !this.scanQueue.isEmpty()) {
            long key = this.scanQueue.removeFirst();
            this.queuedChunks.remove(key);
            ChunkScanCacheEntry entry = this.scanChunk(serverLevel, scanLevel, keyChunkX(key), keyChunkZ(key));
            this.putCacheEntry(key, entry);
            this.lastProcessedChunks++;
            this.lastScanTick = serverLevel.getGameTime();
        }
        if (this.lastProcessedChunks > 0) {
            this.recalculateCoverageStats();
            if (serverLevel.getGameTime() - this.lastClientSyncTick >= CLIENT_SYNC_INTERVAL_TICKS) {
                this.markDirtyAndSync();
            } else {
                this.setChanged();
            }
        }
    }

    private ServerLevel scanLevel(ServerLevel fallback) {
        ServerLevel resolved = SableBridge.outerServerLevelOf(this);
        return resolved == null ? fallback : resolved;
    }

    private ChunkScanCacheEntry scanChunk(ServerLevel serverLevel, ServerLevel scanLevel, int chunkX, int chunkZ) {
        ScanFrame scanFrame = this.currentScanFrame(serverLevel);
        ScanRegion region = this.currentRegion == null
            ? ScanRegion.fromSettings(scanFrame.origin(), scanFrame.yawDeg(), this.settings)
            : this.currentRegion;
        PathScanSettings scanSettings = scanSettingsForRegion(serverLevel, region);
        return DrivabilityScanner.scanChunk(scanLevel, chunkX, chunkZ, region, scanSettings);
    }

    private PathScanSettings scanSettingsForRegion(ServerLevel serverLevel, ScanRegion region) {
        return new PathScanSettings(
            region.forwardChunks(),
            region.sideChunks(),
            region.chunkStride(),
            this.settings.chunksPerTick(),
            region.verticalWindow(),
            this.settings.allowTwoBlockPaths(),
            region.unknownPolicy(),
            this.settings.riskPreset(),
            this.settings.cacheTtlTicks());
    }

    private void putCacheEntry(long key, ChunkScanCacheEntry entry) {
        this.chunkCache.put(key, entry);
        while (this.chunkCache.size() > MAX_CACHE_ENTRIES) {
            Long firstKey = this.chunkCache.keySet().iterator().next();
            this.chunkCache.remove(firstKey);
        }
    }

    private void recalculateCoverageStats() {
        int loaded = 0;
        int unknown = 0;
        int passable = 0;
        int marginal = 0;
        int blocked = 0;
        List<ChunkScanCacheEntry> entries = new ArrayList<>();
        UnknownPolicy activeUnknownPolicy = this.currentRegion == null ? this.settings.unknownPolicy() : this.currentRegion.unknownPolicy();
        if (this.currentRegion != null) {
            for (long key : regionChunkKeys(this.currentRegion)) {
                ChunkScanCacheEntry entry = this.chunkCache.get(key);
                if (entry == null) {
                    unknown++;
                    if (activeUnknownPolicy == UnknownPolicy.BLOCK) {
                        blocked += 16 * 16;
                    } else {
                        marginal += 16 * 16;
                    }
                    continue;
                }
                entries.add(entry);
            }
        } else {
            entries.addAll(this.chunkCache.values());
        }
        for (ChunkScanCacheEntry entry : entries) {
            if (entry.loaded()) {
                loaded++;
            } else {
                unknown++;
                if (activeUnknownPolicy == UnknownPolicy.BLOCK) {
                    blocked += 16 * 16;
                } else {
                    marginal += 16 * 16;
                }
            }
            passable += entry.passableCount();
            marginal += entry.marginalCount();
            blocked += entry.blockedCount();
        }
        this.loadedChunkCount = loaded;
        this.unknownChunkCount = unknown;
        this.totalPassableCells = passable;
        this.totalMarginalCells = marginal;
        this.totalBlockedCells = blocked;
    }

    private static List<Long> regionChunkKeys(ScanRegion region) {
        List<Long> keys = new ArrayList<>(Math.max(1, region.forwardChunks() * (region.sideChunks() * 2 + 1)));
        Direction facing = facingForYaw(region.yawDeg());
        int baseChunkX = region.origin().getX() >> 4;
        int baseChunkZ = region.origin().getZ() >> 4;
        int forwardX = facing.getStepX();
        int forwardZ = facing.getStepZ();
        int rightX = -forwardZ;
        int rightZ = forwardX;
        for (int forward = 0; forward < region.forwardChunks(); forward++) {
            for (int side = -region.sideChunks(); side <= region.sideChunks(); side++) {
                keys.add(chunkKey(baseChunkX + forwardX * forward + rightX * side,
                    baseChunkZ + forwardZ * forward + rightZ * side));
            }
        }
        return keys;
    }

    public boolean isPlayerInInteractionRange(Player player) {
        return com.createstats.flight.bridge.SableBridge.isPlayerInInteractionRangeFor(this, player);
    }

    private Vector3d interactionWorldCenter() {
        Object subLevel = SableBridge.subLevelAccessOf(this);
        if (subLevel != null) {
            Vector3d world = SableBridge.localToWorld(subLevel, new Vector3d(
                this.worldPosition.getX() + 0.5,
                this.worldPosition.getY() + 0.5,
                this.worldPosition.getZ() + 0.5));
            if (world != null && Double.isFinite(world.x) && Double.isFinite(world.y) && Double.isFinite(world.z)) {
                return world;
            }
        }
        return new Vector3d(this.worldPosition.getX() + 0.5, this.worldPosition.getY() + 0.5,
            this.worldPosition.getZ() + 0.5);
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        tooltip.add(Component.literal("  ").append(
            Component.literal("Path Scanner").withStyle(ChatFormatting.GRAY)));

        int loaded = loadedChunkCount;
        int expected = Math.max(1, expectedChunkCount);
        int coveragePct = Math.round(loaded * 100f / expected);
        tooltip.add(Component.literal("    ").append(
            Component.literal(String.format(java.util.Locale.ROOT,
                "Chunks: %d / %d  (%d %%)", loaded, expected, coveragePct))
                .withStyle(coveragePct >= 90 ? ChatFormatting.GREEN : ChatFormatting.YELLOW)));

        int passable = totalPassableCells;
        int marginal = totalMarginalCells;
        int blocked  = totalBlockedCells;
        int totalCells = Math.max(1, passable + marginal + blocked);
        int passablePct = passable * 100 / totalCells;
        tooltip.add(Component.literal("    ").append(
            Component.literal(String.format(java.util.Locale.ROOT, "Passable: %d  (%d %%)", passable, passablePct))
                .withStyle(ChatFormatting.GREEN)));
        tooltip.add(Component.literal("    ").append(
            Component.literal(String.format(java.util.Locale.ROOT, "Marginal: %d", marginal))
                .withStyle(ChatFormatting.YELLOW)));
        tooltip.add(Component.literal("    ").append(
            Component.literal(String.format(java.util.Locale.ROOT, "Blocked:  %d", blocked))
                .withStyle(ChatFormatting.RED)));
        return true;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.create_stats.path_scanner");
    }

    @Override
    @Nullable
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new PathScannerMenu(containerId, playerInventory, this);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        this.feConsumer.save(tag);
        tag.putInt("Version", SCHEMA_VERSION);
        tag.putUUID("ScannerId", scannerId);
        tag.put("Settings", settings.save());
        tag.putInt("ExpectedChunks", expectedChunkCount);
        tag.putInt("LoadedChunks", loadedChunkCount);
        tag.putInt("UnknownChunks", unknownChunkCount);
        tag.putInt("PassableCells", totalPassableCells);
        tag.putInt("MarginalCells", totalMarginalCells);
        tag.putInt("BlockedCells", totalBlockedCells);
        tag.putLong("LastScanTick", lastScanTick);
        tag.putInt("EffectiveBudget", lastEffectiveChunkBudget);
        tag.putBoolean("RemotePaused", remotePaused);
        if (currentRegion != null) {
            tag.put("CurrentRegion", currentRegion.save());
        }
        this.writeCacheEntries(tag, this.chunkCache.values(), MAX_PERSISTED_CACHE_ENTRIES);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.feConsumer.load(tag);
        this.scannerId = tag.hasUUID("ScannerId") ? tag.getUUID("ScannerId") : UUID.randomUUID();
        this.settings = tag.contains("Settings") ? PathScanSettings.load(tag.getCompound("Settings")) : PathScanSettings.defaults();
        this.expectedChunkCount = tag.getInt("ExpectedChunks");
        this.loadedChunkCount = tag.getInt("LoadedChunks");
        this.unknownChunkCount = tag.getInt("UnknownChunks");
        this.totalPassableCells = tag.getInt("PassableCells");
        this.totalMarginalCells = tag.getInt("MarginalCells");
        this.totalBlockedCells = tag.getInt("BlockedCells");
        this.lastScanTick = tag.getLong("LastScanTick");
        this.lastEffectiveChunkBudget = Math.max(0, tag.getInt("EffectiveBudget"));
        this.remotePaused = tag.getBoolean("RemotePaused");
        this.currentRegion = tag.contains("CurrentRegion", Tag.TAG_COMPOUND)
            ? ScanRegion.load(tag.getCompound("CurrentRegion")) : null;
        this.chunkCache.clear();
        boolean allowMetadataOnly = this.level != null && this.level.isClientSide;
        if (tag.contains("Cache", Tag.TAG_LIST)) {
            ListTag cacheList = tag.getList("Cache", Tag.TAG_COMPOUND);
            for (int i = 0; i < cacheList.size(); i++) {
                ChunkScanCacheEntry entry = ChunkScanCacheEntry.load(cacheList.getCompound(i));
                if (!allowMetadataOnly && entry.compressedCellPayload().length < CHUNK_CELL_COUNT) continue;
                this.chunkCache.put(chunkKey(entry.chunkX(), entry.chunkZ()), entry);
            }
        }
        this.scanQueue.clear();
        this.queuedChunks.clear();
        this.recalculateCoverageStats();
    }

    private void writeCacheEntries(CompoundTag tag, Iterable<ChunkScanCacheEntry> entries, int maxEntries) {
        ListTag cacheList = new ListTag();
        int saved = 0;
        for (ChunkScanCacheEntry entry : entries) {
            cacheList.add(metadataOnlyEntry(entry).save());
            if (++saved >= maxEntries) break;
        }
        tag.put("Cache", cacheList);
    }

    private Iterable<ChunkScanCacheEntry> clientSyncCacheEntries() {
        if (this.currentRegion == null) {
            return this.chunkCache.values();
        }
        List<ChunkScanCacheEntry> entries = new ArrayList<>();
        for (long key : regionChunkKeys(this.currentRegion)) {
            ChunkScanCacheEntry entry = this.chunkCache.get(key);
            if (entry != null) {
                entries.add(entry);
            }
        }
        return entries;
    }

    private static ChunkScanCacheEntry metadataOnlyEntry(ChunkScanCacheEntry entry) {
        return new ChunkScanCacheEntry(entry.chunkX(), entry.chunkZ(),
            entry.lastScannedTick(), entry.dirtySinceTick(), entry.loaded(), entry.summaryHash(),
            new byte[0], new byte[0], entry.passableCount(), entry.marginalCount(), entry.blockedCount(), entry.hazardFlags());
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        this.saveAdditional(tag, registries);
        this.writeCacheEntries(tag, this.clientSyncCacheEntries(), MAX_CACHE_ENTRIES);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        this.loadAdditional(tag, registries);
    }

    @Override
    @Nullable
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt, HolderLookup.Provider registries) {
        CompoundTag tag = pkt.getTag();
        if (tag != null) {
            this.loadAdditional(tag, registries);
        }
    }

    public void markDirtyAndSync() {
        this.setChanged();
        Level level = this.level;
        if (level instanceof ServerLevel serverLevel) {
            this.lastClientSyncTick = serverLevel.getGameTime();
            serverLevel.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
        }
    }

    private Direction getFacing() {
        BlockState state = this.getBlockState();
        return state.hasProperty(PathScannerBlock.FACING) ? state.getValue(PathScannerBlock.FACING) : Direction.NORTH;
    }

    private BlockPos currentWorldScanOrigin() {
        Object subLevel = SableBridge.subLevelAccessOf(this);
        if (subLevel != null) {
            Vector3d world = SableBridge.localToWorld(subLevel, new Vector3d(
                this.worldPosition.getX() + 0.5,
                this.worldPosition.getY() + 0.5,
                this.worldPosition.getZ() + 0.5));
            if (world != null && Double.isFinite(world.x) && Double.isFinite(world.y) && Double.isFinite(world.z)) {
                return BlockPos.containing(world.x, world.y, world.z);
            }
        }
        return this.worldPosition;
    }

    private Direction getWorldFacing() {
        Direction localFacing = this.getFacing();
        Object subLevel = SableBridge.subLevelAccessOf(this);
        Quaterniond bodyToWorld = SableBridge.bodyOrientation(subLevel);
        if (bodyToWorld == null) {
            return localFacing;
        }
        Vector3d worldForward = bodyToWorld.transform(new Vector3d(localFacing.getStepX(), 0.0, localFacing.getStepZ()));
        if (!Double.isFinite(worldForward.x) || !Double.isFinite(worldForward.z)) {
            return localFacing;
        }
        if (Math.abs(worldForward.x) > Math.abs(worldForward.z)) {
            return worldForward.x >= 0.0 ? Direction.EAST : Direction.WEST;
        }
        return worldForward.z >= 0.0 ? Direction.SOUTH : Direction.NORTH;
    }

    private ScanFrame currentScanFrame(ServerLevel serverLevel) {
        if (this.hasRecentVehicleScanFrame(serverLevel)) {
            float yaw = normalizeYaw(externalScanYawDeg);
            return new ScanFrame(externalScanOrigin, facingForYaw(yaw), yaw);
        }
        Direction facing = this.getWorldFacing();
        return new ScanFrame(this.currentWorldScanOrigin(), facing, yawForFacing(facing));
    }

    private boolean hasRecentVehicleScanFrame(ServerLevel serverLevel) {
        return externalScanOrigin != null && externalScanFrameTick != Long.MIN_VALUE
            && serverLevel.getGameTime() - externalScanFrameTick <= 60L;
    }

    private PathScanSettings activeScanSettings(ServerLevel serverLevel) {
        if (!this.hasRecentVehicleScanFrame(serverLevel)) return this.settings;
        return new PathScanSettings(
            this.externalForwardChunks,
            this.externalSideChunks,
            this.settings.chunkStride(),
            this.settings.chunksPerTick(),
            this.settings.verticalWindow(),
            this.settings.allowTwoBlockPaths(),
            UnknownPolicy.BLOCK,
            this.settings.riskPreset(),
            this.settings.cacheTtlTicks());
    }

    private int activeChunkStride() {
        return currentRegion == null ? settings.chunkStride() : currentRegion.chunkStride();
    }

    public boolean hasActiveVehicleScanFrame() {
        Level level = this.level;
        if (level instanceof ServerLevel serverLevel) {
            return this.hasRecentVehicleScanFrame(serverLevel);
        }
        return externalScanOrigin != null;
    }

    private static int yawBucket(float yaw) {
        return Math.floorMod(Math.round(normalizeYaw(yaw) / 15.0f), 24);
    }

    private static Direction facingForYaw(float yaw) {
        float normalized = normalizeYaw(yaw);
        if (normalized > 45f && normalized <= 135f) return Direction.WEST;
        if (normalized < -45f && normalized >= -135f) return Direction.EAST;
        if (normalized > 135f || normalized <= -135f) return Direction.NORTH;
        return Direction.SOUTH;
    }

    private static float yawForFacing(Direction facing) {
        return switch (facing) {
            case SOUTH -> 0f;
            case WEST -> 90f;
            case EAST -> -90f;
            default -> 180f;
        };
    }

    private static float normalizeYaw(float yaw) {
        float normalized = yaw % 360f;
        if (normalized < -180f) normalized += 360f;
        if (normalized > 180f) normalized -= 360f;
        return normalized;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private record ScanFrame(BlockPos origin, Direction facing, float yawDeg) {
    }

    private static long chunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX & 0xffffffffL) | (((long) chunkZ & 0xffffffffL) << 32);
    }

    private static int keyChunkX(long key) {
        return (int) (key & 0xffffffffL);
    }

    private static int keyChunkZ(long key) {
        return (int) (key >> 32);
    }
}
