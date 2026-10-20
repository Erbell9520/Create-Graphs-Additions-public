package com.createstats.blockentity;

import com.createstats.block.SolarPanelBlock;
import com.createstats.power.FeGoggle;
import com.createstats.power.FeGridMember;
import com.createstats.power.FeSupply;
import com.createstats.power.FeWireNetwork;
import com.createstats.power.SolarCurve;
import com.createstats.power.SolarTier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

// joined panels share a bonus, a roof beats a checkerboard
public class SolarPanelBE extends BlockEntity implements FeGridMember, FeSupply,
        com.createstats.power.FeForecastingSource,
        com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation {
    public static final int BUFFER = 4_000;

    public static final int OUTFLOW_RATE = 512;

    public static final int RATE_INTERVAL = 10;

    public static final int ARRAY_INTERVAL = 20;

    public static final int MAX_ARRAY = 1_024;

    private int energy;
    private int lastGeneratedFePerTick;
    private int lastOutflowFePerTick;
    private int outflowThisTick;

    private int rate;

    private int skyLight;

    private boolean covered;

    private SolarCurve.Sky sky = SolarCurve.Sky.CLEAR;

    private int arraySize = 1;

    @Nullable
    private FeWireNetwork network;
    @Nullable
    private PanelArray array;
    private long lastRateTick = Long.MIN_VALUE;

    private int lastSyncedReadout = Integer.MIN_VALUE;
    private int ticksSinceReadoutSync;

    private static final class PanelArray {
        private final List<SolarPanelBE> members;
        private final int litCount;
        private final long builtTick;

        PanelArray(List<SolarPanelBE> members, int litCount, long builtTick) {
            this.members = members;
            this.litCount = litCount;
            this.builtTick = builtTick;
        }
    }

    public SolarPanelBE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public SolarTier tier() {
        return getBlockState().getBlock() instanceof SolarPanelBlock panel
                ? panel.tier() : SolarTier.REINFORCED;
    }

    public int storedFe() {
        return energy;
    }

    public int lastGeneratedFePerTick() {
        return lastGeneratedFePerTick;
    }

    public int lastOutflowFePerTick() {
        return lastOutflowFePerTick;
    }

    public int arraySize() {
        return arraySize;
    }

    public int skyLight() {
        return skyLight;
    }

    public SolarCurve.Sky sky() {
        return sky;
    }

    @Override
    public int potentialFePerTick() {
        return SolarCurve.deliverable(rate, OUTFLOW_RATE);
    }

    @Override
    public boolean weatherSensitive() {
        return true;
    }

    public boolean isCovered() {
        return covered;
    }

    @Override
    public int potentialFePerTickIn(int ticks) {
        if (level == null) {
            return potentialFePerTick();
        }

        final boolean clockRuns = level.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT);
        return SolarCurve.deliverable(SolarCurve.fePerTickIn(tier().peakFePerTick(), arraySize,
                level.getDayTime(), ticks, sky, SolarCurve.exposure(skyLight, covered), clockRuns),
                OUTFLOW_RATE);
    }

    public void serverTick() {
        if (level == null || level.isClientSide()) {
            return;
        }

        lastOutflowFePerTick = outflowThisTick;
        outflowThisTick = 0;

        final long now = level.getGameTime();
        if (lastRateTick == Long.MIN_VALUE || now - lastRateTick >= RATE_INTERVAL) {
            lastRateTick = now;
            recomputeRate(now);
        }

        if (energy > BUFFER) {
            energy = BUFFER;
        }
        final int made = Math.max(0, Math.min(rate, BUFFER - energy));
        lastGeneratedFePerTick = made;
        if (made > 0) {
            energy += made;
            setChanged();
        }

        if (network == null || network.isDirty()) {
            FeWireNetwork.buildFrom(this);
        }
        if (network != null) {
            network.solveOnce(level);
        }

        syncReadout(rate * 31 + lastOutflowFePerTick * 7 + energy / 64 + arraySize * 101
                + skyLight * 13 + sky.ordinal() + lastGeneratedFePerTick * 17 + (covered ? 4_099 : 0));
    }

    private void recomputeRate(long now) {
        if (level == null) {
            return;
        }
        skyLight = level.getBrightness(LightLayer.SKY, worldPosition.above());
        covered = coveredByPanel(level, worldPosition);

        sky = level.isThundering() ? SolarCurve.Sky.THUNDER
                : level.isRaining() ? SolarCurve.Sky.RAIN : SolarCurve.Sky.CLEAR;
        arraySize = Math.max(1, array(now).litCount);
        rate = SolarCurve.fePerTick(tier().peakFePerTick(), arraySize, level.getDayTime(), sky,
                SolarCurve.exposure(skyLight, covered));
    }

    private static boolean coveredByPanel(Level level, BlockPos pos) {
        final int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, pos.getX(), pos.getZ());
        final BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos(pos.getX(), pos.getY(),
                pos.getZ());
        for (int y = pos.getY() + 1; y < top; y++) {
            at.setY(y);
            if (level.getBlockState(at).getBlock() instanceof SolarPanelBlock) {
                return true;
            }
        }
        return false;
    }

    private PanelArray array(long now) {
        if (array != null && now - array.builtTick < ARRAY_INTERVAL && !array.members.isEmpty()) {
            return array;
        }
        return rebuildArray(now);
    }

    private PanelArray rebuildArray(long now) {
        final List<SolarPanelBE> found = new ArrayList<>();
        if (level == null) {
            found.add(this);
            array = new PanelArray(found, 1, now);
            return array;
        }
        final Set<BlockPos> seen = new HashSet<>();
        final Deque<SolarPanelBE> queue = new ArrayDeque<>();
        queue.add(this);
        seen.add(worldPosition.immutable());
        found.add(this);
        double exposureSum = 0.0;
        while (!queue.isEmpty() && found.size() < MAX_ARRAY) {
            final SolarPanelBE panel = queue.poll();
            for (Direction direction : Direction.values()) {
                final BlockPos at = panel.getBlockPos().relative(direction).immutable();

                if (!seen.add(at) || !level.isLoaded(at)) {
                    continue;
                }
                if (level.getBlockEntity(at) instanceof SolarPanelBE other && !other.isRemoved()) {
                    found.add(other);
                    queue.add(other);
                    if (found.size() >= MAX_ARRAY) {
                        break;
                    }
                }
            }
        }

        for (SolarPanelBE panel : found) {
            exposureSum += SolarCurve.exposure(
                    level.getBrightness(LightLayer.SKY, panel.getBlockPos().above()),
                    coveredByPanel(level, panel.getBlockPos()));
        }
        final PanelArray shared = new PanelArray(found, SolarCurve.effectiveArraySize(exposureSum),
                now);
        for (SolarPanelBE panel : found) {
            panel.array = shared;
        }
        return shared;
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
                    Block.UPDATE_CLIENTS);
        }
    }

    private final IEnergyStorage energyView = new IEnergyStorage() {
        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            return 0;
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            final int amount = Math.min(Math.min(maxExtract, outflowBudget()), energy);
            if (amount <= 0) {
                return 0;
            }
            if (!simulate) {
                energy -= amount;
                outflowThisTick += amount;
                setChanged();
            }
            return amount;
        }

        @Override
        public int getEnergyStored() {
            return energy;
        }

        @Override
        public int getMaxEnergyStored() {
            return BUFFER;
        }

        @Override
        public boolean canExtract() {
            return true;
        }

        @Override
        public boolean canReceive() {
            return false;
        }
    };

    private int outflowBudget() {
        return Math.max(0, OUTFLOW_RATE - outflowThisTick);
    }

    public IEnergyStorage energyHandle() {
        return energyView;
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
        return OUTFLOW_RATE;
    }

    @Override
    public IEnergyStorage attachedStorage() {
        return energyView;
    }

    @Override
    public boolean isGone(Level owner) {
        return isRemoved() || getLevel() != owner;
    }

    @Override
    public void setLastFlow(int flow) {
    }

    @Override
    public void adoptNetwork(FeWireNetwork adopted) {
        this.network = adopted;
    }

    @Override
    @Nullable
    public FeWireNetwork network() {
        return this.network;
    }

    @Override
    public void invalidateNetwork() {
        if (network != null) {
            network.markDirty();
            network = null;
        }
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        tooltip.add(FeGoggle.title(getBlockState().getBlock().getName().getString()));
        tooltip.add(FeGoggle.output(lastGeneratedFePerTick));
        if (rate > 0 && lastGeneratedFePerTick < rate) {
            tooltip.add(FeGoggle.dim(String.format(Locale.ROOT, "Buffer full, holding %d FE/t", rate)));
        }
        tooltip.add(FeGoggle.amount("Buffer", energy, BUFFER));
        if (covered) {
            tooltip.add(FeGoggle.warn("Shaded by the panel above"));
        } else if (skyLight <= 0) {
            tooltip.add(FeGoggle.warn("No sky above"));
        } else {
            if (skyLight < SolarCurve.FULL_SKY_LIGHT) {
                tooltip.add(FeGoggle.dim(String.format(Locale.ROOT, "Shaded, %d%% of the sky",
                        skyLight * 100 / SolarCurve.FULL_SKY_LIGHT)));
            }

            final boolean night = level != null && SolarCurve.isNight(level.getDayTime());
            if (night) {
                tooltip.add(FeGoggle.dim("Night"));
            } else {
                if (sky != SolarCurve.Sky.CLEAR) {
                    tooltip.add(FeGoggle.dim(String.format(Locale.ROOT, "%s, x%.2f",
                            sky == SolarCurve.Sky.THUNDER ? "Thunderstorm" : "Raining", sky.factor())));
                }
                if (rate <= 0) {
                    tooltip.add(FeGoggle.dim("Too little light to generate"));
                }
            }
        }
        tooltip.add(FeGoggle.dim(String.format(Locale.ROOT, "Array: %d %s, x%.2f",
                arraySize, arraySize == 1 ? "panel" : "panels",
                SolarCurve.arrayMultiplier(arraySize))));
        return true;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Energy", energy);

        tag.putInt("Rate", rate);
        tag.putInt("Generated", lastGeneratedFePerTick);
        tag.putInt("Outflow", lastOutflowFePerTick);
        tag.putInt("SkyLight", skyLight);
        tag.putByte("Sky", (byte) sky.ordinal());
        tag.putInt("ArraySize", arraySize);
        tag.putBoolean("Covered", covered);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        energy = Mth.clamp(tag.getInt("Energy"), 0, BUFFER);
        rate = Math.max(0, tag.getInt("Rate"));
        lastGeneratedFePerTick = Math.max(0, tag.getInt("Generated"));
        lastOutflowFePerTick = Math.max(0, tag.getInt("Outflow"));
        skyLight = Mth.clamp(tag.getInt("SkyLight"), 0, SolarCurve.FULL_SKY_LIGHT);
        final SolarCurve.Sky[] skies = SolarCurve.Sky.values();
        final int ordinal = tag.getByte("Sky");
        sky = ordinal >= 0 && ordinal < skies.length ? skies[ordinal] : SolarCurve.Sky.CLEAR;
        arraySize = Math.max(1, tag.getInt("ArraySize"));
        covered = tag.getBoolean("Covered");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        final CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        loadAdditional(tag, registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        invalidateNetwork();

        if (array != null) {
            for (SolarPanelBE panel : array.members) {
                panel.array = null;
            }
            array = null;
        }
    }
}
