package com.createstats.blockentity;

import com.createstats.block.DamageObserverBlock;
import com.createstats.battle.BattleLightSnapshot;
import com.createstats.compat.CreateBigCannonsCompat;
import com.createstats.compat.CreateBigCannonsReflectionAdapter;
import com.createstats.flight.bridge.SableBridge;
import com.createstats.flight.blockentity.RpmGovernorBE;
import com.createstats.util.InventoryLookupHelper;
import com.createstats.util.ItemHandlerMetrics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import org.joml.Vector3d;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public class DamageObserverBE extends BlockEntity implements com.createstats.power.IFeConsumer {

    /** FE Dependent Mode buffer. Receive-only; exposed as a block capability via FeCapabilities. */
    private final com.createstats.power.FeConsumer feConsumer = com.createstats.power.FeConsumer
            .forBlockEntity(this, com.createstats.power.FeProfiles.settings(com.createstats.power.FeProfiles.KEY_DAMAGE_OBSERVER));

    public com.createstats.power.FeConsumer feConsumer() {
        return feConsumer;
    }

    private static final int SCAN_INTERVAL_TICKS = 10;

    private DamageObserverSettings settings;
    @Nullable private UUID governorControlUuid;
    private final Set<Long> baselineSolidBlocks = new HashSet<>();
    private final Set<Long> structurePresetBlocks = new HashSet<>();
    private final Map<Long, CreateBigCannonsCompat.WeaponPart> baselineWeaponParts = new HashMap<>();
    private boolean baselineCalibrated;
    private boolean structurePresetSaved;
    private int structurePresetBlockCount;
    private long lastScanTick = Long.MIN_VALUE;
    private int watchedBlocks;
    private int baselineBlocks;
    private int presentBaselineBlocks;
    private int missingBlocks;
    private int newBlocks;
    private int cargoContainers;
    private long cargoItems;
    private long cargoItemCapacity;
    private long cargoFluidAmount;
    private long cargoFluidCapacity;
    private long cargoEnergyStored;
    private long cargoEnergyCapacity;
    private boolean weaponDetected;
    private String weaponKind = "NONE";
    private String weaponReadiness = "OFFLINE";
    private String weaponLoadedState = "NOT_APPLICABLE";
    private String weaponMaterial = "Unknown";
    private String weaponDisplayName = "";
    private int weaponBlocks;
    private int weaponBaselineBlocks;
    private int weaponPresentBlocks;
    private int weaponMissingBlocks;
    private int weaponBarrelBlocks;
    private float weaponAutocannonProjectileSpeed;
    private int weaponChamberBlocks;
    private int weaponBreechBlocks;
    private int weaponMuzzleBlocks;
    private int weaponMountBlocks;
    private int weaponLoaderBlocks;
    private int weaponRecoilBlocks;
    private int weaponAmmoContainers;
    private int weaponAmmoRounds;
    private int weaponAmmoCapacity;
    private int weaponTracerRounds;
    private boolean weaponAmmoCreative;
    private int weaponFireRateRpm;
    private boolean weaponCanFireKnown;
    private boolean weaponCanFire;
    private int weaponProjectileBlocks;
    private int weaponPropellantBlocks;
    private int weaponBarrelPayloadBlocks;
    private int weaponChamberPayloadBlocks;
    private int weaponBreechPayloadBlocks;
    private int weaponUnknownPayloadBlocks;
    private int weaponInspectedPayloadBlocks;
    private int weaponEmptyPayloadBlocks;
    private int weaponMissingBarrelBlocks;
    private int weaponMissingBreechBlocks;
    private int weaponMissingMountBlocks;
    private int weaponMissingLoaderBlocks;
    private final List<BlockPos> weaponGovernorPositions = new ArrayList<>();
    private final List<BattleLightSnapshot.Entry> detectedLights = new ArrayList<>();
    private int lastMetricsHash;

    public DamageObserverBE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        this.settings = new DamageObserverSettings();
    }

    public DamageObserverSettings getSettings() {
        return this.settings;
    }

    public UUID getOrCreateGovernorControlUuid() {
        if (this.governorControlUuid == null) {
            this.governorControlUuid = UUID.randomUUID();
            this.setChanged();
        }
        return this.governorControlUuid;
    }

    public List<BlockPos> getWeaponGovernorPositions() {
        return List.copyOf(this.weaponGovernorPositions);
    }

    /** Stage Lights found in the watched volume (any category), for the Battle Screen lighting panel. */
    public List<BattleLightSnapshot.Entry> getDetectedLights() {
        return List.copyOf(this.detectedLights);
    }

    public void setSettings(DamageObserverSettings settings) {
        boolean geometryChanged = this.settings.getWatchMode() != settings.getWatchMode()
                || this.settings.getAreaRadius() != settings.getAreaRadius();
        boolean categoryChanged = this.settings.getCategory() != settings.getCategory();
        this.settings = settings.copy();
        if (geometryChanged) {
            this.resetBaseline();
            this.clearStructurePreset();
        } else if (categoryChanged) {
            this.rebuildBaselineWeaponParts(this.baselineSolidBlocks);
            this.clearWeaponScan();
            this.lastMetricsHash = 0;
        }
        this.setChanged();
        if (this.level != null) {
            this.forceScanObservedVolume();
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
        }
    }

    public void forceScanObservedVolume() {
        if (this.level == null || this.level.isClientSide()) {
            return;
        }
        this.lastScanTick = this.level.getGameTime();
        this.scanObservedVolume();
    }

    public void forceScanObservedVolumeIfDue(int minIntervalTicks) {
        if (this.level == null || this.level.isClientSide()) {
            return;
        }
        long tick = this.level.getGameTime();
        int interval = Math.max(0, minIntervalTicks);
        if (interval > 0 && this.lastScanTick != Long.MIN_VALUE && tick - this.lastScanTick < interval) {
            return;
        }
        this.lastScanTick = tick;
        this.scanObservedVolume();
    }

    public void serverTick() {
        if (this.level == null || this.level.isClientSide()) {
            return;
        }
        // FE Dependent Mode gate: a dead observer stops sweeping; each sweep also pays a burst.
        final boolean fePowered = this.feConsumer.serviceTick(true);
        com.createstats.power.FePowerFx.handleTransition(this.level, this.worldPosition, this.feConsumer);
        if (!fePowered) {
            return;
        }
        long tick = this.level.getGameTime();
        if (tick - this.lastScanTick < SCAN_INTERVAL_TICKS) {
            return;
        }
        if (!this.feConsumer.spendBurst()) {
            return;
        }
        this.lastScanTick = tick;
        this.scanObservedVolume();
    }

    public Direction getWatchDirection() {
        BlockState state = this.getBlockState();
        if (state.getBlock() instanceof DamageObserverBlock) {
            return state.getValue(DamageObserverBlock.FACING).getOpposite();
        }
        return Direction.NORTH;
    }

    public Direction getBehindDirection() {
        return this.getWatchDirection().getOpposite();
    }

    public BlockPos getObservedAnchorPos() {
        if (this.settings.getWatchMode() == DamageObserverSettings.WatchMode.BEHIND_BOX) {
            int radius = this.settings.getAreaRadius();
            return this.worldPosition.relative(this.getBehindDirection(), radius + 1).above(radius);
        }
        return this.worldPosition.relative(this.getWatchDirection());
    }

    public int getObservedDiameter() {
        return this.settings.getWatchMode() == DamageObserverSettings.WatchMode.LOCAL_AREA
                || this.settings.getWatchMode() == DamageObserverSettings.WatchMode.BEHIND_BOX
                ? this.settings.getAreaRadius() * 2 + 1
                : 1;
    }

    public BlockPos getObservedMinPos() {
        if (this.settings.getWatchMode() == DamageObserverSettings.WatchMode.BEHIND_BOX) {
            return this.getBehindBoxCorner(true);
        }
        BlockPos anchor = this.getObservedAnchorPos();
        int radius = this.settings.getWatchMode() == DamageObserverSettings.WatchMode.LOCAL_AREA ? this.settings.getAreaRadius() : 0;
        return anchor.offset(-radius, -radius, -radius);
    }

    public BlockPos getObservedMaxPos() {
        if (this.settings.getWatchMode() == DamageObserverSettings.WatchMode.BEHIND_BOX) {
            return this.getBehindBoxCorner(false);
        }
        BlockPos anchor = this.getObservedAnchorPos();
        int radius = this.settings.getWatchMode() == DamageObserverSettings.WatchMode.LOCAL_AREA ? this.settings.getAreaRadius() : 0;
        return anchor.offset(radius, radius, radius);
    }

    private BlockPos getBehindBoxCorner(boolean min) {
        int radius = this.settings.getAreaRadius();
        int diameter = radius * 2 + 1;
        Direction back = this.getBehindDirection();
        Direction side = back.getClockWise();
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (int sideOffset : new int[]{-radius, radius}) {
            for (int depthOffset : new int[]{1, diameter}) {
                for (int yOffset : new int[]{0, diameter - 1}) {
                    BlockPos pos = this.worldPosition.relative(side, sideOffset).relative(back, depthOffset).above(yOffset);
                    minX = Math.min(minX, pos.getX());
                    minY = Math.min(minY, pos.getY());
                    minZ = Math.min(minZ, pos.getZ());
                    maxX = Math.max(maxX, pos.getX());
                    maxY = Math.max(maxY, pos.getY());
                    maxZ = Math.max(maxZ, pos.getZ());
                }
            }
        }
        return min ? new BlockPos(minX, minY, minZ) : new BlockPos(maxX, maxY, maxZ);
    }

    public int getWatchedBlocks() {
        return this.watchedBlocks;
    }

    public int getBaselineBlocks() {
        return this.baselineBlocks;
    }

    public int getPresentBaselineBlocks() {
        return this.presentBaselineBlocks;
    }

    public int getMissingBlocks() {
        return this.missingBlocks;
    }

    public int getNewBlocks() {
        return this.newBlocks;
    }

    public int getCargoContainers() {
        return this.cargoContainers;
    }

    public long getCargoItems() {
        return this.cargoItems;
    }

    public long getCargoItemCapacity() {
        return this.cargoItemCapacity;
    }

    public long getCargoFluidAmount() {
        return this.cargoFluidAmount;
    }

    public long getCargoFluidCapacity() {
        return this.cargoFluidCapacity;
    }

    public long getCargoEnergyStored() {
        return this.cargoEnergyStored;
    }

    public long getCargoEnergyCapacity() {
        return this.cargoEnergyCapacity;
    }

    public boolean isWeaponDetected() {
        return this.weaponDetected;
    }

    public String getWeaponKind() {
        return this.weaponKind;
    }

    public String getWeaponReadiness() {
        return this.weaponReadiness;
    }

    public String getWeaponLoadedState() {
        return this.weaponLoadedState;
    }

    public String getWeaponMaterial() {
        return this.weaponMaterial;
    }

    public String getWeaponDisplayName() {
        return this.weaponDisplayName;
    }

    public int getWeaponBlocks() {
        return this.weaponBlocks;
    }

    public int getWeaponBaselineBlocks() {
        return this.weaponBaselineBlocks;
    }

    public int getWeaponPresentBlocks() {
        return this.weaponPresentBlocks;
    }

    public int getWeaponMissingBlocks() {
        return this.weaponMissingBlocks;
    }

    public int getWeaponBarrelBlocks() {
        return this.weaponBarrelBlocks;
    }

    public float getAutocannonProjectileSpeed() {
        return this.weaponAutocannonProjectileSpeed;
    }

    public int getWeaponChamberBlocks() {
        return this.weaponChamberBlocks;
    }

    public int getWeaponBreechBlocks() {
        return this.weaponBreechBlocks;
    }

    public int getWeaponMuzzleBlocks() {
        return this.weaponMuzzleBlocks;
    }

    public int getWeaponMountBlocks() {
        return this.weaponMountBlocks;
    }

    public int getWeaponLoaderBlocks() {
        return this.weaponLoaderBlocks;
    }

    public int getWeaponRecoilBlocks() {
        return this.weaponRecoilBlocks;
    }

    public int getWeaponAmmoContainers() {
        return this.weaponAmmoContainers;
    }

    public int getWeaponAmmoRounds() {
        return this.weaponAmmoRounds;
    }

    public int getWeaponAmmoCapacity() {
        return this.weaponAmmoCapacity;
    }

    public int getWeaponTracerRounds() {
        return this.weaponTracerRounds;
    }

    public boolean isWeaponAmmoCreative() {
        return this.weaponAmmoCreative;
    }

    public int getWeaponFireRateRpm() {
        return this.weaponFireRateRpm;
    }

    public boolean isWeaponCanFireKnown() {
        return this.weaponCanFireKnown;
    }

    public boolean canWeaponFire() {
        return this.weaponCanFire;
    }

    public int getWeaponProjectileBlocks() {
        return this.weaponProjectileBlocks;
    }

    public int getWeaponPropellantBlocks() {
        return this.weaponPropellantBlocks;
    }

    public int getWeaponBarrelPayloadBlocks() {
        return this.weaponBarrelPayloadBlocks;
    }

    public int getWeaponChamberPayloadBlocks() {
        return this.weaponChamberPayloadBlocks;
    }

    public int getWeaponBreechPayloadBlocks() {
        return this.weaponBreechPayloadBlocks;
    }

    public int getWeaponUnknownPayloadBlocks() {
        return this.weaponUnknownPayloadBlocks;
    }

    public int getWeaponInspectedPayloadBlocks() {
        return this.weaponInspectedPayloadBlocks;
    }

    public int getWeaponEmptyPayloadBlocks() {
        return this.weaponEmptyPayloadBlocks;
    }

    public int getWeaponMissingBarrelBlocks() {
        return this.weaponMissingBarrelBlocks;
    }

    public int getWeaponMissingBreechBlocks() {
        return this.weaponMissingBreechBlocks;
    }

    public int getWeaponMissingMountBlocks() {
        return this.weaponMissingMountBlocks;
    }

    public int getWeaponMissingLoaderBlocks() {
        return this.weaponMissingLoaderBlocks;
    }

    public boolean hasStructurePreset() {
        return this.structurePresetSaved;
    }

    public int getStructurePresetBlockCount() {
        return this.structurePresetBlockCount;
    }

    public double getHealthPercent() {
        if (!this.baselineCalibrated || this.baselineBlocks <= 0) {
            return 100.0;
        }
        return Math.max(0.0, Math.min(100.0, this.presentBaselineBlocks * 100.0 / this.baselineBlocks));
    }

    public void recalibrateBaselineToCurrentStructure() {
        if (this.level == null || this.level.isClientSide()) {
            return;
        }
        this.applyBaseline(this.captureCurrentSolidBlocks());
        this.forceScanObservedVolume();
        this.syncScanState();
    }

    public void saveCurrentStructurePreset() {
        if (this.level == null || this.level.isClientSide()) {
            return;
        }
        Set<Long> currentSolidBlocks = this.captureCurrentSolidBlocks();
        this.structurePresetBlocks.clear();
        this.structurePresetBlocks.addAll(currentSolidBlocks);
        this.structurePresetSaved = true;
        this.structurePresetBlockCount = this.structurePresetBlocks.size();
        this.applyBaseline(currentSolidBlocks);
        this.forceScanObservedVolume();
        this.syncScanState();
    }

    private Set<Long> captureCurrentSolidBlocks() {
        Set<Long> currentSolidBlocks = new HashSet<>();
        if (this.level == null) {
            return currentSolidBlocks;
        }
        BlockPos min = this.getObservedMinPos();
        BlockPos max = this.getObservedMaxPos();
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            if (!this.level.isLoaded(pos)) {
                continue;
            }
            BlockState state = this.level.getBlockState(pos);
            if (!state.isAir()) {
                currentSolidBlocks.add(pos.asLong());
            }
        }
        return currentSolidBlocks;
    }

    /** [1] */
    public com.createstats.battle.DamageObserverVoxelMap buildVoxelMap() {
        if (this.level == null) {
            return null;
        }
        BlockPos min = this.getObservedMinPos();
        BlockPos max = this.getObservedMaxPos();
        int dx = max.getX() - min.getX() + 1;
        int dy = max.getY() - min.getY() + 1;
        int dz = max.getZ() - min.getZ() + 1;
        // Hard cap on volume to keep payload + render cost bounded.
        if ((long) dx * dy * dz > 80_000L) {
            return null;
        }
        com.createstats.battle.DamageObserverVoxelMap.Builder builder =
                new com.createstats.battle.DamageObserverVoxelMap.Builder(this.worldPosition.immutable(), min.immutable(), dx, dy, dz)
                        .name(this.settings.getObserverName())
                        .category(this.settings.getCategory() == null ? "" : this.settings.getCategory().name());

        // Mark baseline cells.
        for (long key : this.baselineSolidBlocks) {
            BlockPos p = BlockPos.of(key);
            int lx = p.getX() - min.getX();
            int ly = p.getY() - min.getY();
            int lz = p.getZ() - min.getZ();
            builder.markBaseline(lx, ly, lz);
        }

        // [2]
        boolean weaponsCategory = this.settings.getCategory() == DamageObserverSettings.ObserverCategory.WEAPONS;
        java.util.Map<BlockPos, BlockState> assemblyModel = new java.util.LinkedHashMap<>();
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            if (!this.level.isLoaded(pos)) {
                continue;
            }
            BlockState state = this.level.getBlockState(pos);
            if (state.isAir()) {
                continue;
            }
            int lx = pos.getX() - min.getX();
            int ly = pos.getY() - min.getY();
            int lz = pos.getZ() - min.getZ();
            builder.setBlock(lx, ly, lz, state);
            if (weaponsCategory) {
                if (CreateBigCannonsCompat.isCreateBigCannonsBlock(state)) {
                    assemblyModel.put(pos.immutable(), state);
                }
                if (CreateBigCannonsCompat.weaponKind(state) == CreateBigCannonsCompat.WeaponKind.MOUNT) {
                    for (java.util.Map.Entry<BlockPos, BlockState> cannonEntry
                            : CreateBigCannonsReflectionAdapter.collectAssembledCannonWorldBlocks(this.level.getBlockEntity(pos)).entrySet()) {
                        assemblyModel.putIfAbsent(cannonEntry.getKey(), cannonEntry.getValue());
                    }
                }
            }
        }
        if (weaponsCategory) {
            for (BlockPos governorPos : this.weaponGovernorPositions) {
                if (this.level.isLoaded(governorPos)) {
                    BlockState governorState = this.level.getBlockState(governorPos);
                    if (!governorState.isAir()) {
                        assemblyModel.put(governorPos.immutable(), governorState);
                    }
                }
            }
        }

        com.createstats.battle.DamageObserverVoxelMap result = builder.build(this.level.getGameTime());
        if (!assemblyModel.isEmpty()) {
            result.setCannonModel(assemblyModel);
        }
        return result;
    }

    private void applyBaseline(Set<Long> solidBlocks) {
        this.baselineSolidBlocks.clear();
        this.baselineSolidBlocks.addAll(solidBlocks);
        this.rebuildBaselineWeaponParts(solidBlocks);
        this.baselineCalibrated = true;
        this.baselineBlocks = this.baselineSolidBlocks.size();
        this.presentBaselineBlocks = this.baselineBlocks;
        this.missingBlocks = 0;
        this.newBlocks = 0;
        this.lastMetricsHash = 0;
    }

    private void rebuildBaselineWeaponParts(Set<Long> solidBlocks) {
        this.baselineWeaponParts.clear();
        if (this.level == null || this.settings.getCategory() != DamageObserverSettings.ObserverCategory.WEAPONS) {
            return;
        }
        WeaponScan weapon = new WeaponScan();
        for (Long key : solidBlocks) {
            BlockPos pos = BlockPos.of(key);
            if (!this.level.isLoaded(pos)) {
                continue;
            }
            BlockState state = this.level.getBlockState(pos);
            if (!state.isAir()) {
                weapon.scanBlock(pos, key, state, this.level.getBlockEntity(pos));
            }
        }
        this.inferAutocannonAttachments(weapon);
        this.baselineWeaponParts.putAll(weapon.partsByPos);
    }

    private void scanObservedVolume() {
        BlockPos min = this.getObservedMinPos();
        BlockPos max = this.getObservedMaxPos();
        Set<Long> currentSolid = new HashSet<>();
        int watched = 0;
        int present = 0;
        int added = 0;
        CargoScan cargo = new CargoScan();
        boolean scanWeapons = this.settings.getCategory() == DamageObserverSettings.ObserverCategory.WEAPONS;
        // [3]
        boolean scanCargo = this.settings.getCategory() == DamageObserverSettings.ObserverCategory.CARGO;
        WeaponScan weapon = new WeaponScan();
        List<BlockPos> detectedGovernors = new ArrayList<>();
        List<BattleLightSnapshot.Entry> lightsFound = new ArrayList<>();

        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            if (!this.level.isLoaded(pos)) {
                continue;
            }
            watched++;
            BlockState state = this.level.getBlockState(pos);
            BlockEntity blockEntity = this.level.getBlockEntity(pos);
            boolean solid = !state.isAir();
            long key = pos.asLong();
            if (solid) {
                currentSolid.add(key);
                if (scanWeapons) {
                    weapon.scanBlock(pos, key, state, blockEntity);
                    if (blockEntity instanceof RpmGovernorBE) {
                        detectedGovernors.add(pos.immutable());
                    }
                }
                if (this.baselineSolidBlocks.contains(key)) {
                    present++;
                } else if (this.baselineCalibrated) {
                    added++;
                }
            }
            if (blockEntity != null) {
                if (scanCargo) {
                    this.scanCargo(blockEntity, cargo);
                }
                if (blockEntity instanceof StageLightBE light) {
                    lightsFound.add(new BattleLightSnapshot.Entry(key, light.isLit(), light.getPan(), light.getTilt(),
                            light.getRange(), light.getBrightness(), light.getFocus(), light.getTint(),
                            light.isBeamVisible(), light.isVolumetric(), light.getTrackingRange(),
                            light.getBeamWidth(), light.getLightSpread(), light.hasBattleTarget(),
                            light.getMaxRange(), light.getPeakRange(), light.getBattleTargetName(),
                            light.getEffect(), light.getEffectSpeed()));
                }
            }
        }

        if (!this.baselineCalibrated) {
            this.baselineSolidBlocks.clear();
            this.baselineSolidBlocks.addAll(currentSolid);
            this.baselineCalibrated = true;
            present = currentSolid.size();
            added = 0;
            if (scanWeapons) {
                this.baselineWeaponParts.clear();
                this.baselineWeaponParts.putAll(weapon.partsByPos);
            }
        }
        if (scanWeapons && this.settings.getWatchMode() == DamageObserverSettings.WatchMode.SINGLE_BLOCK) {
            this.inferSingleBlockWeaponChain(weapon);
        }
        if (scanWeapons) {
            this.inferAutocannonAttachments(weapon);
        }

        this.watchedBlocks = watched;
        this.baselineBlocks = this.baselineSolidBlocks.size();
        this.presentBaselineBlocks = present;
        this.missingBlocks = Math.max(0, this.baselineBlocks - present);
        this.newBlocks = added;
        this.cargoContainers = cargo.containers;
        this.cargoItems = cargo.items;
        this.cargoItemCapacity = cargo.itemCapacity;
        this.cargoFluidAmount = cargo.fluidAmount;
        this.cargoFluidCapacity = cargo.fluidCapacity;
        this.cargoEnergyStored = cargo.energyStored;
        this.cargoEnergyCapacity = cargo.energyCapacity;
        this.detectedLights.clear();
        this.detectedLights.addAll(lightsFound);
        this.applyWeaponScan(scanWeapons, weapon);
        this.applyWeaponGovernors(scanWeapons, detectedGovernors);

        int hash = this.metricsHash();
        if (hash != this.lastMetricsHash) {
            this.lastMetricsHash = hash;
            this.syncScanState();
        }
    }

    private void applyWeaponGovernors(boolean scanWeapons, List<BlockPos> detectedGovernors) {
        if (!scanWeapons) {
            this.weaponGovernorPositions.clear();
            return;
        }
        // [4]
        if (this.weaponGovernorPositions.size() == detectedGovernors.size()
                && this.weaponGovernorPositions.containsAll(detectedGovernors)) {
            return;
        }
        sortGovernorPositions(this.level, detectedGovernors);
        this.weaponGovernorPositions.clear();
        this.weaponGovernorPositions.addAll(detectedGovernors);
        this.setChanged();
    }

    static void sortGovernorPositions(@Nullable Level level, List<BlockPos> governors) {
        governors.sort((first, second) -> {
            GovernorSortKey firstKey = governorSortKey(level, first);
            GovernorSortKey secondKey = governorSortKey(level, second);

            int frame = Integer.compare(firstKey.frameId(), secondKey.frameId());
            if (frame != 0) return frame;

            int x = Double.compare(firstKey.sortX(), secondKey.sortX());
            if (x != 0) return x;

            int y = Double.compare(firstKey.sortY(), secondKey.sortY());
            if (y != 0) return y;

            int z = Double.compare(firstKey.sortZ(), secondKey.sortZ());
            if (z != 0) return z;

            int worldX = Integer.compare(first.getX(), second.getX());
            if (worldX != 0) return worldX;

            int worldY = Integer.compare(first.getY(), second.getY());
            if (worldY != 0) return worldY;

            return Integer.compare(first.getZ(), second.getZ());
        });
    }

    private static GovernorSortKey governorSortKey(@Nullable Level level, BlockPos pos) {
        double sortX = pos.getX();
        double sortY = pos.getY();
        double sortZ = pos.getZ();
        int frameId = 0;
        if (level != null) {
            BlockEntity blockEntity = SableBridge.getBlockEntityOnContraption(level, pos);
            if (blockEntity == null && level.isLoaded(pos)) {
                blockEntity = level.getBlockEntity(pos);
            }
            Object subLevel = SableBridge.subLevelAccessOf(blockEntity);
            if (subLevel != null) {
                Vector3d local = SableBridge.worldToLocal(subLevel, new Vector3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5));
                if (local != null
                        && Double.isFinite(local.x)
                        && Double.isFinite(local.y)
                        && Double.isFinite(local.z)) {
                    frameId = System.identityHashCode(subLevel);
                    sortX = local.x;
                    sortY = local.y;
                    sortZ = local.z;
                }
            }
        }
        return new GovernorSortKey(frameId, sortX, sortY, sortZ);
    }

    private record GovernorSortKey(int frameId, double sortX, double sortY, double sortZ) {
    }

    private void applyWeaponScan(boolean scanWeapons, WeaponScan weapon) {
        if (!scanWeapons) {
            this.clearWeaponScan();
            return;
        }
        weapon.applyBaseline(this.baselineWeaponParts);
        this.weaponDetected = weapon.weaponBlocks > 0;
        this.weaponKind = weapon.kind.name();
        this.weaponMaterial = weapon.material;
        this.weaponDisplayName = weapon.displayName;
        this.weaponBlocks = weapon.weaponBlocks;
        this.weaponBaselineBlocks = weapon.baselineWeaponBlocks;
        this.weaponPresentBlocks = weapon.presentWeaponBlocks;
        this.weaponMissingBlocks = weapon.missingWeaponBlocks;
        this.weaponBarrelBlocks = weapon.count(CreateBigCannonsCompat.WeaponPart.BARREL);
        this.weaponAutocannonProjectileSpeed = weapon.autocannonBallisticsKnown
                ? weapon.autocannonBaseSpeed + weapon.autocannonSpeedPerBarrel
                        * Math.min(Math.max(0, this.weaponBarrelBlocks), weapon.autocannonMaxSpeedIncreases)
                : 0.0f;
        this.weaponChamberBlocks = weapon.count(CreateBigCannonsCompat.WeaponPart.CHAMBER);
        this.weaponBreechBlocks = weapon.count(CreateBigCannonsCompat.WeaponPart.BREECH);
        this.weaponMuzzleBlocks = weapon.count(CreateBigCannonsCompat.WeaponPart.MUZZLE);
        this.weaponMountBlocks = weapon.count(CreateBigCannonsCompat.WeaponPart.MOUNT);
        this.weaponLoaderBlocks = weapon.count(CreateBigCannonsCompat.WeaponPart.LOADER)
                + weapon.count(CreateBigCannonsCompat.WeaponPart.RAM_HEAD)
                + weapon.count(CreateBigCannonsCompat.WeaponPart.WORM_HEAD);
        this.weaponRecoilBlocks = weapon.count(CreateBigCannonsCompat.WeaponPart.RECOIL);
        this.weaponAmmoContainers = weapon.count(CreateBigCannonsCompat.WeaponPart.AMMO_CONTAINER);
        this.weaponAmmoRounds = weapon.ammoRounds;
        this.weaponAmmoCapacity = weapon.ammoCapacity;
        this.weaponTracerRounds = weapon.tracerRounds;
        this.weaponAmmoCreative = weapon.ammoCreative;
        this.weaponFireRateRpm = weapon.fireRateRpm;
        this.weaponCanFireKnown = weapon.canFireKnown;
        this.weaponCanFire = weapon.canFire;
        this.weaponProjectileBlocks = weapon.count(CreateBigCannonsCompat.WeaponPart.PROJECTILE) + weapon.projectilePayloadBlocks;
        this.weaponPropellantBlocks = weapon.count(CreateBigCannonsCompat.WeaponPart.PROPELLANT) + weapon.propellantPayloadBlocks;
        this.weaponBarrelPayloadBlocks = weapon.barrelPayloadBlocks;
        this.weaponChamberPayloadBlocks = weapon.chamberPayloadBlocks;
        this.weaponBreechPayloadBlocks = weapon.breechPayloadBlocks;
        this.weaponUnknownPayloadBlocks = weapon.unknownPayloadBlocks;
        this.weaponInspectedPayloadBlocks = weapon.inspectedPayloads;
        this.weaponEmptyPayloadBlocks = weapon.emptyPayloads;
        this.weaponMissingBarrelBlocks = weapon.missingCount(CreateBigCannonsCompat.WeaponPart.BARREL)
                + weapon.missingCount(CreateBigCannonsCompat.WeaponPart.CHAMBER)
                + weapon.missingCount(CreateBigCannonsCompat.WeaponPart.MUZZLE);
        this.weaponMissingBreechBlocks = weapon.missingCount(CreateBigCannonsCompat.WeaponPart.BREECH);
        this.weaponMissingMountBlocks = weapon.missingCount(CreateBigCannonsCompat.WeaponPart.MOUNT);
        this.weaponMissingLoaderBlocks = weapon.missingCount(CreateBigCannonsCompat.WeaponPart.LOADER)
                + weapon.missingCount(CreateBigCannonsCompat.WeaponPart.RAM_HEAD)
                + weapon.missingCount(CreateBigCannonsCompat.WeaponPart.WORM_HEAD);
        this.weaponLoadedState = weapon.loadedState();
        this.weaponReadiness = weapon.readiness();
    }

    private void inferSingleBlockWeaponChain(WeaponScan weapon) {
        if (this.level == null) {
            return;
        }
        BlockPos anchor = this.getObservedAnchorPos();
        if (!this.level.isLoaded(anchor)) {
            return;
        }
        BlockState anchorState = this.level.getBlockState(anchor);
        if (!CreateBigCannonsCompat.isLikelyWeaponBlock(anchorState)) {
            return;
        }
        Direction axis = CreateBigCannonsCompat.cannonAxisDirection(anchorState, this.getWatchDirection());
        this.walkInferredWeaponChain(weapon, anchor, axis);
        this.walkInferredWeaponChain(weapon, anchor, axis.getOpposite());
    }

    private void walkInferredWeaponChain(WeaponScan weapon, BlockPos anchor, Direction direction) {
        for (int step = 1; step <= 16; step++) {
            BlockPos pos = anchor.relative(direction, step);
            if (!this.level.isLoaded(pos)) {
                break;
            }
            BlockState state = this.level.getBlockState(pos);
            if (!CreateBigCannonsCompat.isLikelyWeaponBlock(state)) {
                break;
            }
            weapon.scanBlock(pos, pos.asLong(), state, this.level.getBlockEntity(pos));
        }
    }

    private void inferAutocannonAttachments(WeaponScan weapon) {
        if (this.level == null || weapon.kind != CreateBigCannonsCompat.WeaponKind.AUTOCANNON || weapon.worldWeaponSeeds.isEmpty()) {
            return;
        }
        Set<BlockPos> frontier = new HashSet<>(weapon.worldWeaponSeeds);
        for (int depth = 0; depth < 2 && !frontier.isEmpty(); depth++) {
            Set<BlockPos> next = new HashSet<>();
            for (BlockPos seed : frontier) {
                for (Direction direction : Direction.values()) {
                    BlockPos pos = seed.relative(direction);
                    if (!weapon.markWorldProbe(pos) || !this.level.isLoaded(pos)) {
                        continue;
                    }
                    BlockState state = this.level.getBlockState(pos);
                    if (!CreateBigCannonsCompat.isLikelyWeaponBlock(state)) {
                        continue;
                    }
                    weapon.scanBlock(pos, pos.asLong(), state, this.level.getBlockEntity(pos), false);
                    next.add(pos.immutable());
                }
            }
            frontier = next;
        }
    }

    private void clearWeaponScan() {
        this.weaponDetected = false;
        this.weaponKind = "NONE";
        this.weaponReadiness = "OFFLINE";
        this.weaponLoadedState = "NOT_APPLICABLE";
        this.weaponMaterial = "Unknown";
        this.weaponDisplayName = "";
        this.weaponBlocks = 0;
        this.weaponBaselineBlocks = 0;
        this.weaponPresentBlocks = 0;
        this.weaponMissingBlocks = 0;
        this.weaponBarrelBlocks = 0;
        this.weaponAutocannonProjectileSpeed = 0.0f;
        this.weaponChamberBlocks = 0;
        this.weaponBreechBlocks = 0;
        this.weaponMuzzleBlocks = 0;
        this.weaponMountBlocks = 0;
        this.weaponLoaderBlocks = 0;
        this.weaponRecoilBlocks = 0;
        this.weaponAmmoContainers = 0;
        this.weaponAmmoRounds = 0;
        this.weaponAmmoCapacity = 0;
        this.weaponTracerRounds = 0;
        this.weaponAmmoCreative = false;
        this.weaponFireRateRpm = 0;
        this.weaponCanFireKnown = false;
        this.weaponCanFire = false;
        this.weaponProjectileBlocks = 0;
        this.weaponPropellantBlocks = 0;
        this.weaponBarrelPayloadBlocks = 0;
        this.weaponChamberPayloadBlocks = 0;
        this.weaponBreechPayloadBlocks = 0;
        this.weaponUnknownPayloadBlocks = 0;
        this.weaponInspectedPayloadBlocks = 0;
        this.weaponEmptyPayloadBlocks = 0;
        this.weaponMissingBarrelBlocks = 0;
        this.weaponMissingBreechBlocks = 0;
        this.weaponMissingMountBlocks = 0;
        this.weaponMissingLoaderBlocks = 0;
        this.weaponGovernorPositions.clear();
    }

    private void scanCargo(BlockEntity blockEntity, CargoScan cargo) {
        IItemHandler itemHandler = InventoryLookupHelper.resolveItemHandler(this.level, blockEntity.getBlockPos(), blockEntity, null);
        boolean countedContainer = false;
        if (itemHandler != null) {
            ItemHandlerMetrics.Readout readout = ItemHandlerMetrics.read(itemHandler);
            cargo.items += readout.totalItems();
            cargo.itemCapacity += readout.totalCapacity();
            countedContainer = true;
        }
        IFluidHandler fluidHandler = this.level.getCapability(Capabilities.FluidHandler.BLOCK, blockEntity.getBlockPos(), null);
        if (fluidHandler != null) {
            for (int tank = 0; tank < fluidHandler.getTanks(); tank++) {
                cargo.fluidAmount += fluidHandler.getFluidInTank(tank).getAmount();
                cargo.fluidCapacity += Math.max(0, fluidHandler.getTankCapacity(tank));
            }
            countedContainer = true;
        }
        IEnergyStorage energyStorage = this.level.getCapability(Capabilities.EnergyStorage.BLOCK, blockEntity.getBlockPos(), null);
        if (energyStorage != null) {
            cargo.energyStored += Math.max(0, energyStorage.getEnergyStored());
            cargo.energyCapacity += Math.max(0, energyStorage.getMaxEnergyStored());
            countedContainer = true;
        }
        if (countedContainer) {
            cargo.containers++;
        }
    }

    private int metricsHash() {
        return Objects.hash(
                this.baselineCalibrated,
                this.watchedBlocks,
                this.baselineBlocks,
                this.presentBaselineBlocks,
                this.missingBlocks,
                this.newBlocks,
                this.cargoContainers,
                this.cargoItems,
                this.cargoItemCapacity,
                this.cargoFluidAmount,
                this.cargoFluidCapacity,
                this.cargoEnergyStored,
                this.cargoEnergyCapacity,
                this.weaponDetected,
                this.weaponKind,
                this.weaponReadiness,
                this.weaponLoadedState,
                this.weaponMaterial,
                this.weaponDisplayName,
                this.weaponBlocks,
                this.weaponBaselineBlocks,
                this.weaponPresentBlocks,
                this.weaponMissingBlocks,
                this.weaponBarrelBlocks,
                this.weaponChamberBlocks,
                this.weaponBreechBlocks,
                this.weaponMuzzleBlocks,
                this.weaponMountBlocks,
                this.weaponLoaderBlocks,
                this.weaponRecoilBlocks,
                this.weaponAmmoContainers,
                this.weaponAmmoRounds,
                this.weaponAmmoCapacity,
                this.weaponTracerRounds,
                this.weaponAmmoCreative,
                this.weaponFireRateRpm,
                this.weaponCanFireKnown,
                this.weaponCanFire,
                this.weaponProjectileBlocks,
                this.weaponPropellantBlocks,
                this.weaponBarrelPayloadBlocks,
                this.weaponChamberPayloadBlocks,
                this.weaponBreechPayloadBlocks,
                this.weaponUnknownPayloadBlocks,
                this.weaponInspectedPayloadBlocks,
                this.weaponEmptyPayloadBlocks,
                this.weaponMissingBarrelBlocks,
                this.weaponMissingBreechBlocks,
                this.weaponMissingMountBlocks,
                this.weaponMissingLoaderBlocks,
                this.weaponGovernorPositions);
    }

    private void resetBaseline() {
        this.baselineSolidBlocks.clear();
            this.baselineWeaponParts.clear();
        this.baselineCalibrated = false;
        this.watchedBlocks = 0;
        this.baselineBlocks = 0;
        this.presentBaselineBlocks = 0;
        this.missingBlocks = 0;
        this.newBlocks = 0;
        this.cargoContainers = 0;
        this.cargoItems = 0L;
        this.cargoItemCapacity = 0L;
        this.cargoFluidAmount = 0L;
        this.cargoFluidCapacity = 0L;
        this.cargoEnergyStored = 0L;
        this.cargoEnergyCapacity = 0L;
        this.clearWeaponScan();
        this.lastMetricsHash = 0;
    }

    private void clearStructurePreset() {
        this.structurePresetBlocks.clear();
        this.structurePresetSaved = false;
        this.structurePresetBlockCount = 0;
    }

    private void syncScanState() {
        this.setChanged();
        if (this.level != null) {
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        this.feConsumer.save(tag);
        tag.put("Settings", this.settings.save());
        if (this.governorControlUuid != null) {
            tag.putUUID("GovernorControlUuid", this.governorControlUuid);
        }
        this.saveScanState(tag, true);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.feConsumer.load(tag);
        if (tag.contains("Settings", Tag.TAG_COMPOUND)) {
            this.settings = DamageObserverSettings.load(tag.getCompound("Settings"));
        } else {
            this.settings = new DamageObserverSettings();
        }
        this.governorControlUuid = tag.hasUUID("GovernorControlUuid") ? tag.getUUID("GovernorControlUuid") : null;
        this.loadScanState(tag, true);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.put("Settings", this.settings.save());
        this.saveScanState(tag, false);
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(Connection connection, ClientboundBlockEntityDataPacket packet, HolderLookup.Provider registries) {
        CompoundTag tag = packet.getTag();
        if (tag != null && tag.contains("Settings", Tag.TAG_COMPOUND)) {
            this.settings = DamageObserverSettings.load(tag.getCompound("Settings"));
            this.loadScanState(tag, false);
        }
    }

    private void saveScanState(CompoundTag tag, boolean includeBaseline) {
        tag.putBoolean("BaselineCalibrated", this.baselineCalibrated);
        tag.putInt("WatchedBlocks", this.watchedBlocks);
        tag.putInt("BaselineBlocks", this.baselineBlocks);
        tag.putInt("PresentBaselineBlocks", this.presentBaselineBlocks);
        tag.putInt("MissingBlocks", this.missingBlocks);
        tag.putInt("NewBlocks", this.newBlocks);
        tag.putInt("CargoContainers", this.cargoContainers);
        tag.putLong("CargoItems", this.cargoItems);
        tag.putLong("CargoItemCapacity", this.cargoItemCapacity);
        tag.putLong("CargoFluidAmount", this.cargoFluidAmount);
        tag.putLong("CargoFluidCapacity", this.cargoFluidCapacity);
        tag.putLong("CargoEnergyStored", this.cargoEnergyStored);
        tag.putLong("CargoEnergyCapacity", this.cargoEnergyCapacity);
        tag.putBoolean("WeaponDetected", this.weaponDetected);
        tag.putString("WeaponKind", this.weaponKind);
        tag.putString("WeaponReadiness", this.weaponReadiness);
        tag.putString("WeaponLoadedState", this.weaponLoadedState);
        tag.putString("WeaponMaterial", this.weaponMaterial);
        tag.putString("WeaponDisplayName", this.weaponDisplayName);
        tag.putInt("WeaponBlocks", this.weaponBlocks);
        tag.putInt("WeaponBaselineBlocks", this.weaponBaselineBlocks);
        tag.putInt("WeaponPresentBlocks", this.weaponPresentBlocks);
        tag.putInt("WeaponMissingBlocks", this.weaponMissingBlocks);
        tag.putInt("WeaponBarrelBlocks", this.weaponBarrelBlocks);
        tag.putFloat("WeaponAutocannonProjectileSpeed", this.weaponAutocannonProjectileSpeed);
        tag.putInt("WeaponChamberBlocks", this.weaponChamberBlocks);
        tag.putInt("WeaponBreechBlocks", this.weaponBreechBlocks);
        tag.putInt("WeaponMuzzleBlocks", this.weaponMuzzleBlocks);
        tag.putInt("WeaponMountBlocks", this.weaponMountBlocks);
        tag.putInt("WeaponLoaderBlocks", this.weaponLoaderBlocks);
        tag.putInt("WeaponRecoilBlocks", this.weaponRecoilBlocks);
        tag.putInt("WeaponAmmoContainers", this.weaponAmmoContainers);
        tag.putInt("WeaponAmmoRounds", this.weaponAmmoRounds);
        tag.putInt("WeaponAmmoCapacity", this.weaponAmmoCapacity);
        tag.putInt("WeaponTracerRounds", this.weaponTracerRounds);
        tag.putBoolean("WeaponAmmoCreative", this.weaponAmmoCreative);
        tag.putInt("WeaponFireRateRpm", this.weaponFireRateRpm);
        tag.putBoolean("WeaponCanFireKnown", this.weaponCanFireKnown);
        tag.putBoolean("WeaponCanFire", this.weaponCanFire);
        tag.putInt("WeaponProjectileBlocks", this.weaponProjectileBlocks);
        tag.putInt("WeaponPropellantBlocks", this.weaponPropellantBlocks);
        tag.putInt("WeaponBarrelPayloadBlocks", this.weaponBarrelPayloadBlocks);
        tag.putInt("WeaponChamberPayloadBlocks", this.weaponChamberPayloadBlocks);
        tag.putInt("WeaponBreechPayloadBlocks", this.weaponBreechPayloadBlocks);
        tag.putInt("WeaponUnknownPayloadBlocks", this.weaponUnknownPayloadBlocks);
        tag.putInt("WeaponInspectedPayloadBlocks", this.weaponInspectedPayloadBlocks);
        tag.putInt("WeaponEmptyPayloadBlocks", this.weaponEmptyPayloadBlocks);
        tag.putInt("WeaponMissingBarrelBlocks", this.weaponMissingBarrelBlocks);
        tag.putInt("WeaponMissingBreechBlocks", this.weaponMissingBreechBlocks);
        tag.putInt("WeaponMissingMountBlocks", this.weaponMissingMountBlocks);
        tag.putInt("WeaponMissingLoaderBlocks", this.weaponMissingLoaderBlocks);
        ListTag governorTags = new ListTag();
        for (BlockPos governorPos : this.weaponGovernorPositions) {
            CompoundTag governorTag = new CompoundTag();
            governorTag.putLong("Pos", governorPos.asLong());
            governorTags.add(governorTag);
        }
        tag.put("WeaponGovernors", governorTags);
        tag.putBoolean("StructurePresetSaved", this.structurePresetSaved);
        tag.putInt("StructurePresetBlockCount", this.structurePresetBlockCount);
        if (includeBaseline) {
            long[] baseline = new long[this.baselineSolidBlocks.size()];
            int index = 0;
            for (Long pos : this.baselineSolidBlocks) {
                baseline[index++] = pos;
            }
            tag.putLongArray("BaselineSolidBlocks", baseline);
            long[] preset = new long[this.structurePresetBlocks.size()];
            index = 0;
            for (Long pos : this.structurePresetBlocks) {
                preset[index++] = pos;
            }
            tag.putLongArray("StructurePresetSolidBlocks", preset);
            ListTag weaponParts = new ListTag();
            for (Map.Entry<Long, CreateBigCannonsCompat.WeaponPart> partEntry : this.baselineWeaponParts.entrySet()) {
                CompoundTag partTag = new CompoundTag();
                partTag.putLong("Pos", partEntry.getKey());
                partTag.putString("Part", partEntry.getValue().name());
                weaponParts.add(partTag);
            }
            tag.put("BaselineWeaponParts", weaponParts);
        }
    }

    private void loadScanState(CompoundTag tag, boolean includeBaseline) {
        this.baselineCalibrated = tag.getBoolean("BaselineCalibrated");
        this.watchedBlocks = tag.getInt("WatchedBlocks");
        this.baselineBlocks = tag.getInt("BaselineBlocks");
        this.presentBaselineBlocks = tag.getInt("PresentBaselineBlocks");
        this.missingBlocks = tag.getInt("MissingBlocks");
        this.newBlocks = tag.getInt("NewBlocks");
        this.cargoContainers = tag.getInt("CargoContainers");
        this.cargoItems = tag.getLong("CargoItems");
        this.cargoItemCapacity = tag.getLong("CargoItemCapacity");
        this.cargoFluidAmount = tag.getLong("CargoFluidAmount");
        this.cargoFluidCapacity = tag.getLong("CargoFluidCapacity");
        this.cargoEnergyStored = tag.getLong("CargoEnergyStored");
        this.cargoEnergyCapacity = tag.getLong("CargoEnergyCapacity");
        this.weaponDetected = tag.getBoolean("WeaponDetected");
        this.weaponKind = tag.contains("WeaponKind") ? tag.getString("WeaponKind") : "NONE";
        this.weaponReadiness = tag.contains("WeaponReadiness") ? tag.getString("WeaponReadiness") : "OFFLINE";
        this.weaponLoadedState = tag.contains("WeaponLoadedState") ? tag.getString("WeaponLoadedState") : "NOT_APPLICABLE";
        this.weaponMaterial = tag.contains("WeaponMaterial") ? tag.getString("WeaponMaterial") : "Unknown";
        this.weaponDisplayName = tag.contains("WeaponDisplayName") ? tag.getString("WeaponDisplayName") : "";
        this.weaponBlocks = tag.getInt("WeaponBlocks");
        this.weaponBaselineBlocks = tag.getInt("WeaponBaselineBlocks");
        this.weaponPresentBlocks = tag.getInt("WeaponPresentBlocks");
        this.weaponMissingBlocks = tag.getInt("WeaponMissingBlocks");
        this.weaponBarrelBlocks = tag.getInt("WeaponBarrelBlocks");
        this.weaponAutocannonProjectileSpeed = tag.getFloat("WeaponAutocannonProjectileSpeed");
        this.weaponChamberBlocks = tag.getInt("WeaponChamberBlocks");
        this.weaponBreechBlocks = tag.getInt("WeaponBreechBlocks");
        this.weaponMuzzleBlocks = tag.getInt("WeaponMuzzleBlocks");
        this.weaponMountBlocks = tag.getInt("WeaponMountBlocks");
        this.weaponLoaderBlocks = tag.getInt("WeaponLoaderBlocks");
        this.weaponRecoilBlocks = tag.getInt("WeaponRecoilBlocks");
        this.weaponAmmoContainers = tag.getInt("WeaponAmmoContainers");
        this.weaponAmmoRounds = tag.getInt("WeaponAmmoRounds");
        this.weaponAmmoCapacity = tag.getInt("WeaponAmmoCapacity");
        this.weaponTracerRounds = tag.getInt("WeaponTracerRounds");
        this.weaponAmmoCreative = tag.getBoolean("WeaponAmmoCreative");
        this.weaponFireRateRpm = tag.getInt("WeaponFireRateRpm");
        this.weaponCanFireKnown = tag.getBoolean("WeaponCanFireKnown");
        this.weaponCanFire = tag.getBoolean("WeaponCanFire");
        this.weaponProjectileBlocks = tag.getInt("WeaponProjectileBlocks");
        this.weaponPropellantBlocks = tag.getInt("WeaponPropellantBlocks");
        this.weaponBarrelPayloadBlocks = tag.getInt("WeaponBarrelPayloadBlocks");
        this.weaponChamberPayloadBlocks = tag.getInt("WeaponChamberPayloadBlocks");
        this.weaponBreechPayloadBlocks = tag.getInt("WeaponBreechPayloadBlocks");
        this.weaponUnknownPayloadBlocks = tag.getInt("WeaponUnknownPayloadBlocks");
        this.weaponInspectedPayloadBlocks = tag.getInt("WeaponInspectedPayloadBlocks");
        this.weaponEmptyPayloadBlocks = tag.getInt("WeaponEmptyPayloadBlocks");
        this.weaponMissingBarrelBlocks = tag.getInt("WeaponMissingBarrelBlocks");
        this.weaponMissingBreechBlocks = tag.getInt("WeaponMissingBreechBlocks");
        this.weaponMissingMountBlocks = tag.getInt("WeaponMissingMountBlocks");
        this.weaponMissingLoaderBlocks = tag.getInt("WeaponMissingLoaderBlocks");
        this.weaponGovernorPositions.clear();
        if (tag.contains("WeaponGovernors", Tag.TAG_LIST)) {
            ListTag governorTags = tag.getList("WeaponGovernors", Tag.TAG_COMPOUND);
            for (int index = 0; index < governorTags.size(); index++) {
                this.weaponGovernorPositions.add(BlockPos.of(governorTags.getCompound(index).getLong("Pos")));
            }
        }
        this.structurePresetSaved = tag.getBoolean("StructurePresetSaved");
        this.structurePresetBlockCount = tag.getInt("StructurePresetBlockCount");
        if (includeBaseline && tag.contains("BaselineSolidBlocks", Tag.TAG_LONG_ARRAY)) {
            this.baselineSolidBlocks.clear();
            for (long pos : tag.getLongArray("BaselineSolidBlocks")) {
                this.baselineSolidBlocks.add(pos);
            }
        }
        if (includeBaseline && tag.contains("BaselineWeaponParts", Tag.TAG_LIST)) {
            this.baselineWeaponParts.clear();
            ListTag weaponParts = tag.getList("BaselineWeaponParts", Tag.TAG_COMPOUND);
            for (int index = 0; index < weaponParts.size(); index++) {
                CompoundTag partTag = weaponParts.getCompound(index);
                this.baselineWeaponParts.put(partTag.getLong("Pos"), safeWeaponPart(partTag.getString("Part")));
            }
        }
        if (includeBaseline && tag.contains("StructurePresetSolidBlocks", Tag.TAG_LONG_ARRAY)) {
            this.structurePresetBlocks.clear();
            for (long pos : tag.getLongArray("StructurePresetSolidBlocks")) {
                this.structurePresetBlocks.add(pos);
            }
            this.structurePresetBlockCount = this.structurePresetBlocks.size();
            this.structurePresetSaved = this.structurePresetSaved || !this.structurePresetBlocks.isEmpty();
        }
        this.lastMetricsHash = this.metricsHash();
    }

    private static CreateBigCannonsCompat.WeaponPart safeWeaponPart(String value) {
        try {
            return CreateBigCannonsCompat.WeaponPart.valueOf(value);
        } catch (Exception ignored) {
            return CreateBigCannonsCompat.WeaponPart.UNKNOWN;
        }
    }

    private static final class WeaponScan {
        private final Set<Long> weaponPositions = new HashSet<>();
        private final Set<BlockPos> worldWeaponSeeds = new HashSet<>();
        private final Set<Long> worldProbePositions = new HashSet<>();
        private final Map<Long, CreateBigCannonsCompat.WeaponPart> partsByPos = new HashMap<>();
        private final EnumMap<CreateBigCannonsCompat.WeaponPart, Integer> counts = new EnumMap<>(CreateBigCannonsCompat.WeaponPart.class);
        private final EnumMap<CreateBigCannonsCompat.WeaponPart, Integer> missingCounts = new EnumMap<>(CreateBigCannonsCompat.WeaponPart.class);
        private CreateBigCannonsCompat.WeaponKind kind = CreateBigCannonsCompat.WeaponKind.NONE;
        private String material = "Unknown";
        private String displayName = "";
        private int weaponBlocks;
        private int baselineWeaponBlocks;
        private int presentWeaponBlocks;
        private int missingWeaponBlocks;
        private int projectilePayloadBlocks;
        private int propellantPayloadBlocks;
        private int barrelPayloadBlocks;
        private int chamberPayloadBlocks;
        private int breechPayloadBlocks;
        private int unknownPayloadBlocks;
        private int ammoRounds;
        private int ammoCapacity;
        private int tracerRounds;
        private boolean ammoCreative;
        private int fireRateRpm;
        private boolean canFireKnown;
        private boolean canFire;
        private int loadingBlocks;
        private int inspectedPayloads;
        private int emptyPayloads;
        private boolean breechOpenKnown;
        private boolean breechOpen;

        private boolean autocannonBallisticsKnown;
        private float autocannonBaseSpeed;
        private float autocannonSpeedPerBarrel;
        private int autocannonMaxSpeedIncreases;

        private void scanBlock(@Nullable BlockPos worldPos, long key, BlockState state, @Nullable BlockEntity blockEntity) {
            this.scanBlock(worldPos, key, state, blockEntity, true);
        }

        private void scanBlock(@Nullable BlockPos worldPos, long key, BlockState state, @Nullable BlockEntity blockEntity, boolean inspectMountedCannon) {
            CreateBigCannonsCompat.WeaponPart part = CreateBigCannonsCompat.classifyPart(state);
            CreateBigCannonsCompat.WeaponKind detectedKind = CreateBigCannonsCompat.weaponKind(state);
            if (part == CreateBigCannonsCompat.WeaponPart.UNKNOWN && detectedKind == CreateBigCannonsCompat.WeaponKind.NONE) {
                return;
            }
            if (!this.weaponPositions.add(key)) {
                return;
            }
            if (worldPos != null) {
                this.worldWeaponSeeds.add(worldPos.immutable());
                this.worldProbePositions.add(worldPos.asLong());
            }
            this.weaponBlocks++;
            if (part != CreateBigCannonsCompat.WeaponPart.UNKNOWN) {
                this.partsByPos.put(key, part);
                this.counts.merge(part, 1, Integer::sum);
            }
            this.kind = mergeKind(this.kind, detectedKind);
            if ("Unknown".equals(this.material)) {
                this.material = CreateBigCannonsCompat.materialHint(state);
            }
            if (this.displayName.isEmpty()) {
                this.displayName = CreateBigCannonsCompat.displayName(state);
            }
            if (blockEntity != null) {
                this.applyAutocannonControl(CreateBigCannonsReflectionAdapter.inspectAutocannonControl(blockEntity));
            }
            if (!this.autocannonBallisticsKnown) {
                CreateBigCannonsReflectionAdapter.AutocannonBallisticsInspection ballistics =
                        CreateBigCannonsReflectionAdapter.inspectAutocannonBallistics(state, 0);
                if (ballistics.available()) {
                    this.autocannonBallisticsKnown = true;
                    this.autocannonBaseSpeed = ballistics.baseSpeed();
                    this.autocannonSpeedPerBarrel = ballistics.speedIncreasePerBarrel();
                    this.autocannonMaxSpeedIncreases = ballistics.maxSpeedIncreases();
                }
            }
            if (part == CreateBigCannonsCompat.WeaponPart.AMMO_CONTAINER && blockEntity != null) {
                this.scanAmmoContainer(blockEntity);
            }
            this.applyInspection(part, CreateBigCannonsReflectionAdapter.inspect(blockEntity, state));
            if (inspectMountedCannon && part == CreateBigCannonsCompat.WeaponPart.MOUNT) {
                for (CreateBigCannonsReflectionAdapter.VirtualBlockInspection virtualBlock : CreateBigCannonsReflectionAdapter.inspectMountedCannon(blockEntity, key)) {
                    this.scanBlock(null, virtualBlock.key(), virtualBlock.state(), virtualBlock.blockEntity(), false);
                }
            }
        }

        private boolean markWorldProbe(BlockPos pos) {
            return this.worldProbePositions.add(pos.asLong());
        }

        private void scanAmmoContainer(BlockEntity blockEntity) {
            CreateBigCannonsReflectionAdapter.AmmoContainerInspection inspection = CreateBigCannonsReflectionAdapter.inspectAmmoContainer(blockEntity);
            int rounds = inspection.totalRounds();
            int capacity = inspection.totalCapacity();
            if (blockEntity.getLevel() != null) {
                IItemHandler itemHandler = InventoryLookupHelper.resolveItemHandler(blockEntity.getLevel(), blockEntity.getBlockPos(), blockEntity, null);
                if (itemHandler != null) {
                    ItemHandlerMetrics.Readout readout = ItemHandlerMetrics.read(itemHandler);
                    rounds = Math.max(rounds, readout.totalItems());
                    capacity = Math.max(capacity, readout.totalCapacity());
                }
            }
            this.ammoRounds += Math.max(0, rounds);
            this.ammoCapacity += Math.max(0, capacity);
            this.tracerRounds += Math.max(0, inspection.tracerRounds());
            this.ammoCreative |= inspection.creative();
        }

        private void applyAutocannonControl(CreateBigCannonsReflectionAdapter.AutocannonControlInspection inspection) {
            this.fireRateRpm = Math.max(this.fireRateRpm, inspection.fireRateRpm());
            if (inspection.canFireKnown()) {
                this.canFireKnown = true;
                this.canFire |= inspection.canFire();
            }
        }

        private void applyInspection(CreateBigCannonsCompat.WeaponPart part, CreateBigCannonsReflectionAdapter.LoadInspection inspection) {
            this.projectilePayloadBlocks += inspection.projectileBlocks();
            this.propellantPayloadBlocks += inspection.propellantBlocks();
            int payloadBlocks = inspection.projectileBlocks() + inspection.propellantBlocks();
            if (payloadBlocks > 0) {
                switch (part) {
                    case BARREL, MUZZLE -> this.barrelPayloadBlocks += payloadBlocks;
                    case CHAMBER -> this.chamberPayloadBlocks += payloadBlocks;
                    case BREECH -> this.breechPayloadBlocks += payloadBlocks;
                    default -> this.unknownPayloadBlocks += payloadBlocks;
                }
            }
            this.loadingBlocks += inspection.loadingBlocks();
            this.inspectedPayloads += inspection.inspectedPayloads();
            this.emptyPayloads += inspection.emptyPayloads();
            if (inspection.breechOpenKnown()) {
                this.breechOpenKnown = true;
                this.breechOpen = inspection.breechOpen();
            }
        }

        private void applyBaseline(Map<Long, CreateBigCannonsCompat.WeaponPart> baselineParts) {
            this.baselineWeaponBlocks = baselineParts.size();
            this.presentWeaponBlocks = 0;
            this.missingWeaponBlocks = 0;
            this.missingCounts.clear();
            for (Map.Entry<Long, CreateBigCannonsCompat.WeaponPart> entry : baselineParts.entrySet()) {
                if (this.partsByPos.containsKey(entry.getKey())) {
                    this.presentWeaponBlocks++;
                    continue;
                }
                this.missingWeaponBlocks++;
                this.missingCounts.merge(entry.getValue(), 1, Integer::sum);
            }
        }

        private int count(CreateBigCannonsCompat.WeaponPart part) {
            return this.counts.getOrDefault(part, 0);
        }

        private int missingCount(CreateBigCannonsCompat.WeaponPart part) {
            return this.missingCounts.getOrDefault(part, 0);
        }

        private String loadedState() {
            if (this.weaponBlocks <= 0) {
                return "NOT_APPLICABLE";
            }
            boolean autocannon = this.kind == CreateBigCannonsCompat.WeaponKind.AUTOCANNON;
            int projectiles = this.count(CreateBigCannonsCompat.WeaponPart.PROJECTILE) + this.projectilePayloadBlocks;
            int propellant = this.count(CreateBigCannonsCompat.WeaponPart.PROPELLANT) + this.propellantPayloadBlocks;
            int payloadBlocks = projectiles + propellant;
            // A big cannon is combat ready only with BOTH a projectile and a propellant charge seated.
            // Autocannons feed from an item magazine instead, so their block payload is never required.
            boolean combatReady = autocannon || (projectiles > 0 && propellant > 0);
            if (this.loadingBlocks > 0) {
                // The loader is physically stroking right now ("moving"). If a complete munition set is
                // already seated, surface that instead of hiding it behind a transient LOADING flag.
                return combatReady ? "LOADED" : "LOADING";
            }
            if (payloadBlocks > 0) {
                // Something is loaded. A charge with no shot (or a shot with no charge) is a partial load.
                return combatReady ? "LOADED" : "PARTIAL";
            }
            if (this.inspectedPayloads > 0 && this.emptyPayloads > 0) {
                return "EMPTY";
            }
            return "UNKNOWN";
        }

        private String readiness() {
            if (this.weaponBlocks <= 0) {
                return "OFFLINE";
            }
            String loadedState = this.loadedState();
            if ("LOADING".equals(loadedState)) {
                return "LOADING";
            }
            if (this.missingWeaponBlocks > 0 || this.hasMissingCriticalPart()) {
                return "DAMAGED";
            }
            if (this.breechOpenKnown && this.breechOpen && !"LOADED".equals(loadedState)) {
                return "UNKNOWN";
            }
            return "READY";
        }

        private boolean hasMissingCriticalPart() {
            return this.missingCount(CreateBigCannonsCompat.WeaponPart.BARREL) > 0
                    || this.missingCount(CreateBigCannonsCompat.WeaponPart.CHAMBER) > 0
                    || this.missingCount(CreateBigCannonsCompat.WeaponPart.MUZZLE) > 0
                    || this.missingCount(CreateBigCannonsCompat.WeaponPart.BREECH) > 0
                    || this.missingCount(CreateBigCannonsCompat.WeaponPart.MOUNT) > 0;
        }

        private static CreateBigCannonsCompat.WeaponKind mergeKind(CreateBigCannonsCompat.WeaponKind current, CreateBigCannonsCompat.WeaponKind next) {
            if (next == CreateBigCannonsCompat.WeaponKind.NONE || current == next) {
                return current;
            }
            if (current == CreateBigCannonsCompat.WeaponKind.NONE) {
                return next;
            }
            if (current == CreateBigCannonsCompat.WeaponKind.AUTOCANNON || next == CreateBigCannonsCompat.WeaponKind.AUTOCANNON) {
                return CreateBigCannonsCompat.WeaponKind.AUTOCANNON;
            }
            if (current == CreateBigCannonsCompat.WeaponKind.BIG_CANNON || next == CreateBigCannonsCompat.WeaponKind.BIG_CANNON) {
                return CreateBigCannonsCompat.WeaponKind.BIG_CANNON;
            }
            return current;
        }
    }

    private static final class CargoScan {
        private int containers;
        private long items;
        private long itemCapacity;
        private long fluidAmount;
        private long fluidCapacity;
        private long energyStored;
        private long energyCapacity;
    }
}
