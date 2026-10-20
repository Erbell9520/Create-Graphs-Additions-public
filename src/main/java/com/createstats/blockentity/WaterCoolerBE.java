package com.createstats.blockentity;

import com.createstats.block.MugContent;
import com.createstats.block.WaterCoolerBlock;
import com.createstats.power.ApplianceStatus;
import com.createstats.power.FeGridMember;
import com.createstats.power.FeLock;
import com.createstats.power.FeManagedLoad;
import com.createstats.power.FeManagedState;
import com.createstats.power.FePriority;
import com.createstats.power.FeWireNetwork;
import com.createstats.power.FeWireTerminal;
import com.createstats.power.WireTier;
import com.createstats.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// one tank three taps, the difference is a chiller and a carbonator
public class WaterCoolerBE extends BlockEntity implements FeGridMember, FeManagedLoad, FeWireTerminal,
        com.createstats.power.Gateable, com.createstats.power.HomeAppliance,
        com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation {
    @Override
    public void setGated(boolean gated) {
        setOn(!gated);
    }

    @Override
    public boolean isGated() {
        return !isOn();
    }

    @Override
    public com.createstats.power.HomeAppliance.Category applianceCategory() {
        return com.createstats.power.HomeAppliance.Category.KITCHEN;
    }

    @Override
    @Nullable
    public ApplianceStatus applianceStatus() {
        if (isGated() || isManagedShed()
                || (ratedDrawFePerTick() > 0 && measuredDrawFePerTick() <= 0)) {
            return null;
        }
        if (water < SERVE_MB) {
            return ApplianceStatus.idle(Component.translatable("status.create_stats.home.tank_empty"));
        }
        if (isPouring()) {
            return ApplianceStatus.idle(Component.translatable("status.create_stats.home.pouring"));
        }
        if (!isCold()) {
            return ApplianceStatus.idle(Component.translatable("status.create_stats.home.chilling",
                    Math.round(temperature * 100.0f)));
        }
        return ApplianceStatus.idle(isCarbonatorReady()
                ? Component.translatable("status.create_stats.home.cold")
                : Component.translatable("status.create_stats.home.cold_co2"));
    }

    public static final int TANK_CAPACITY = 4000;

    public static final int SERVE_MB = 250;
    public static final int BUCKET_MB = 1000;
    public static final int BOTTLE_MB = 250;

    public static final int POUR_TICKS = 30;

    private static final int IDLE_DRAW = 4;

    private static final int CHILL_DRAW = 48;

    private static final int CO2_CHARGE_RATE = 8;

    private static final int CO2_FULL = 600;

    private static final int BUFFER = 3000;

    private static final float CHILL_RATE = 1.0f / 1200.0f;

    private static final float WARM_RATE = 1.0f / 9600.0f;

    private static final float CHILL_THRESHOLD = 0.75f;

    private static final float CHILL_RESUME = 0.92f;

    private boolean on = true;
    private int water;
    private float temperature;
    private int co2;
    private int energy;

    private int mug = -1;

    private MugContent trayContent = MugContent.EMPTY;

    private long pourStart = -1L;
    @Nullable
    private WaterCoolerBlock.Tap pouring;
    private long switchTime = Long.MIN_VALUE;

    private long glugTime = Long.MIN_VALUE;

    private boolean chilling;
    private int ratedDraw;
    private int measuredDraw;
    private int lastSyncedEnergy;
    private int syncCooldown;

    private int lastSyncedTempBucket = -1;
    private boolean lastSyncedCo2Ready;

    private final FeManagedState managed = new FeManagedState();
    @Nullable
    private FeWireNetwork network;
    private final List<BlockPos> links = new ArrayList<>();
    private final Map<Long, WireTier> linkTiers = new HashMap<>();

    private final IEnergyStorage energyView = new IEnergyStorage() {
        @Override
        public int receiveEnergy(int max, boolean simulate) {
            final int taken = Math.min(max, BUFFER - energy);
            if (!simulate && taken > 0) {
                energy += taken;
                setChanged();
            }
            return taken;
        }

        @Override
        public int extractEnergy(int max, boolean simulate) {
            return 0;
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
            return false;
        }

        @Override
        public boolean canReceive() {
            return true;
        }
    };

    // fill only. drainable = it's a tank and the chiller chases the temperature forever
    private final IFluidHandler tankView = new IFluidHandler() {
        @Override
        public int getTanks() {
            return 1;
        }

        @Override
        public @NotNull FluidStack getFluidInTank(int tank) {
            return water <= 0 ? FluidStack.EMPTY
                    : new FluidStack(net.minecraft.world.level.material.Fluids.WATER, water);
        }

        @Override
        public int getTankCapacity(int tank) {
            return TANK_CAPACITY;
        }

        @Override
        public boolean isFluidValid(int tank, @NotNull FluidStack stack) {
            return stack.getFluid() == net.minecraft.world.level.material.Fluids.WATER;
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            if (!isFluidValid(0, resource)) {
                return 0;
            }
            return addWater(resource.getAmount(), action.execute());
        }

        @Override
        public @NotNull FluidStack drain(FluidStack resource, FluidAction action) {
            return FluidStack.EMPTY;
        }

        @Override
        public @NotNull FluidStack drain(int maxDrain, FluidAction action) {
            return FluidStack.EMPTY;
        }
    };

    public WaterCoolerBE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public enum Refusal {
        NONE, OFF, NO_POWER, NO_WATER, NOT_COLD, NO_CO2, NO_MUG, MUG_FULL, TANK_FULL, BUSY;

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public Refusal check(WaterCoolerBlock.Tap tap) {
        if (pourStart >= 0L) {
            return Refusal.BUSY;
        }
        // mug first, that's the step you can act on

        if (!hasMug()) {
            return Refusal.NO_MUG;
        }
        if (trayContent.isDrinkable()) {
            return Refusal.MUG_FULL;
        }
        return checkMachine(tap);
    }

    public Refusal checkMachine(WaterCoolerBlock.Tap tap) {
        if (water < SERVE_MB) {
            return Refusal.NO_WATER;
        }

        if (tap == WaterCoolerBlock.Tap.SPRING) {
            return Refusal.NONE;
        }
        if (!on) {
            return Refusal.OFF;
        }
        if (energy < IDLE_DRAW) {
            return Refusal.NO_POWER;
        }
        if (temperature < CHILL_THRESHOLD) {
            return Refusal.NOT_COLD;
        }
        if (tap == WaterCoolerBlock.Tap.SPARKLING && co2 < CO2_FULL) {
            return Refusal.NO_CO2;
        }
        return Refusal.NONE;
    }

    public Refusal startPour(WaterCoolerBlock.Tap tap) {
        final Refusal refusal = check(tap);
        if (refusal != Refusal.NONE) {
            return refusal;
        }

        water -= SERVE_MB;
        if (tap == WaterCoolerBlock.Tap.SPARKLING) {
            co2 = 0;
        }
        pourStart = level.getGameTime();
        pouring = tap;
        glugTime = pourStart;
        sync();
        return Refusal.NONE;
    }

    public Refusal drawOff(int amount) {
        if (water < amount) {
            return Refusal.NO_WATER;
        }
        water -= amount;
        glugTime = level.getGameTime();
        sync();
        return Refusal.NONE;
    }

    public int addWater(int amount, boolean execute) {
        final int taken = Math.min(amount, TANK_CAPACITY - water);
        if (taken <= 0 || !execute) {
            return Math.max(0, taken);
        }
        final int before = water;
        water += taken;
        temperature = water <= 0 ? 0.0f : temperature * (before / (float) water);
        if (temperature < CHILL_RESUME) {
            chilling = true;
        }
        sync();
        return taken;
    }

    public void serverTick() {
        if (level == null) {
            return;
        }
        payIdle();
        runChiller();
        chargeCarbonator();
        driftWarm();
        finishPour();
        heartbeat();
    }

    private void payIdle() {
        final boolean cut = managed.isSuppressed();
        if (!on || cut) {
            ratedDraw = 0;
            measuredDraw = 0;
            if (cut && on) {
                setOn(false);
            }
            return;
        }
        if (energy < IDLE_DRAW) {
            measuredDraw = 0;
            setOn(false);
            return;
        }
        energy -= IDLE_DRAW;
        measuredDraw = IDLE_DRAW;
        ratedDraw = IDLE_DRAW + (chilling ? CHILL_DRAW : 0);
    }

    private void runChiller() {
        if (!on || water <= 0) {
            chilling = false;
            return;
        }
        if (temperature >= 1.0f) {
            chilling = false;
        } else if (temperature < CHILL_RESUME) {
            chilling = true;
        }
        if (!chilling || energy < CHILL_DRAW) {
            return;
        }
        energy -= CHILL_DRAW;
        measuredDraw += CHILL_DRAW;
        temperature = Math.min(1.0f, temperature + CHILL_RATE);
        syncTemperatureBucket();
    }

    private void chargeCarbonator() {
        if (!on || co2 >= CO2_FULL || energy < CO2_CHARGE_RATE) {
            return;
        }
        energy -= CO2_CHARGE_RATE;
        measuredDraw += CO2_CHARGE_RATE;
        co2 = Math.min(CO2_FULL, co2 + CO2_CHARGE_RATE);
        if (isCarbonatorReady() != lastSyncedCo2Ready) {
            lastSyncedCo2Ready = isCarbonatorReady();
            sync();
        }
    }

    private void driftWarm() {
        if (temperature <= 0.0f) {
            return;
        }
        temperature = Math.max(0.0f, temperature - WARM_RATE);
        syncTemperatureBucket();
    }

    private void syncTemperatureBucket() {
        final int bucket = Math.round(temperature * 8.0f);
        if (bucket != lastSyncedTempBucket) {
            lastSyncedTempBucket = bucket;
            sync();
        }
    }

    private void finishPour() {
        if (pourStart < 0L) {
            return;
        }
        final long elapsed = level.getGameTime() - pourStart;

        if (elapsed > 0 && elapsed % 10 == 0) {
            glugTime = level.getGameTime();
            level.playSound(null, worldPosition, net.minecraft.sounds.SoundEvents.BUBBLE_COLUMN_BUBBLE_POP,
                    net.minecraft.sounds.SoundSource.BLOCKS, 0.35f, 0.7f + (elapsed % 3) * 0.12f);
        }
        if (elapsed < POUR_TICKS) {
            return;
        }
        if (hasMug() && pouring != null) {
            trayContent = pouring.content();
        }
        pourStart = -1L;
        pouring = null;
        sync();
    }

    private void heartbeat() {
        if (--syncCooldown > 0) {
            return;
        }
        syncCooldown = 20;
        if (Math.abs(energy - lastSyncedEnergy) * 32 >= BUFFER) {
            lastSyncedEnergy = energy;
            sync();
        }
    }

    public float bottleFill(float partialTick) {
        float level = water / (float) TANK_CAPACITY;
        if (pourStart >= 0L) {
            level += (SERVE_MB / (float) TANK_CAPACITY) * (1.0f - pourProgress(partialTick));
        }
        return Mth.clamp(level, 0.0f, 1.0f);
    }

    public float pourProgress(float partialTick) {
        if (pourStart < 0L || level == null) {
            return 0.0f;
        }
        final float elapsed = level.getGameTime() - pourStart + partialTick;
        return Mth.clamp(elapsed / POUR_TICKS, 0.0f, 1.0f);
    }

    public boolean isPouring() {
        return pourStart >= 0L;
    }

    @Nullable
    public WaterCoolerBlock.Tap pouringTap() {
        return pouring;
    }

    public float mugFill(float partialTick) {
        if (trayContent.isDrinkable()) {
            return 1.0f;
        }
        return isPouring() ? pourProgress(partialTick) : 0.0f;
    }

    public float switchSwing(float partialTick) {
        if (level == null || switchTime == Long.MIN_VALUE) {
            return on ? 1.0f : 0.0f;
        }
        final float elapsed = level.getGameTime() - switchTime + partialTick;
        final float t = Mth.clamp(elapsed / 4.0f, 0.0f, 1.0f);
        return on ? t : 1.0f - t;
    }

    public float glugPhase(float partialTick) {
        if (level == null || glugTime == Long.MIN_VALUE) {
            return 1.0f;
        }
        final float elapsed = level.getGameTime() - glugTime + partialTick;
        return Mth.clamp(elapsed / 10.0f, 0.0f, 1.0f);
    }

    public boolean isOn() {
        return on;
    }

    public boolean isChilling() {
        return chilling;
    }

    public boolean isCold() {
        return temperature >= CHILL_THRESHOLD;
    }

    public boolean isCarbonatorReady() {
        return co2 >= CO2_FULL;
    }

    public float temperature() {
        return temperature;
    }

    public int waterMb() {
        return water;
    }

    public boolean hasPower() {
        return energy >= IDLE_DRAW;
    }

    public void setOn(boolean value) {
        if (on == value) {
            return;
        }
        on = value;
        switchTime = level == null ? Long.MIN_VALUE : level.getGameTime();
        if (!on) {
            chilling = false;
        }
        sync();
    }

    public boolean hasMug() {
        return mug >= 0;
    }

    public int mug() {
        return Math.max(0, mug);
    }

    public MugContent trayContent() {
        return trayContent;
    }

    public void setMug(int variant) {
        mug = variant;
        trayContent = MugContent.EMPTY;
        sync();
    }

    public void clearMug() {
        mug = -1;
        trayContent = MugContent.EMPTY;
        pourStart = -1L;
        pouring = null;
        sync();
    }

    public Component statusLine() {
        final String text = String.format(Locale.ROOT, "%d%% water  %s  %s",
                Math.round(water * 100.0f / TANK_CAPACITY),
                isCold() ? "chilled" : chilling ? String.format(Locale.ROOT, "cooling %d%%",
                        Math.round(temperature * 100.0f)) : "not cold",
                isCarbonatorReady() ? "CO2 ready" : "CO2 charging");
        return Component.literal(text).withStyle(isCold() ? ChatFormatting.AQUA : ChatFormatting.GRAY);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean("On", on);
        tag.putInt("Water", water);
        tag.putFloat("Temperature", temperature);
        tag.putInt("Co2", co2);
        tag.putInt("Energy", energy);
        tag.putInt("Mug", mug);
        tag.putInt("Tray", trayContent.ordinal());
        tag.putLong("PourStart", pourStart);
        tag.putInt("Pouring", pouring == null ? -1 : pouring.ordinal());
        tag.putBoolean("Chilling", chilling);
        tag.putInt("RatedDraw", ratedDraw);
        tag.putInt("MeasuredDraw", measuredDraw);
        tag.putInt("Priority", managed.priorityOrdinal());
        tag.putInt("Lock", managed.lockOrdinal());
        final long[] linkArr = new long[links.size()];
        final byte[] tierArr = new byte[links.size()];
        for (int i = 0; i < links.size(); i++) {
            linkArr[i] = links.get(i).asLong();
            tierArr[i] = (byte) tierOf(links.get(i)).ordinal();
        }
        tag.putLongArray("Links", linkArr);
        tag.putByteArray("LinkTiers", tierArr);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        on = !tag.contains("On") || tag.getBoolean("On");
        water = tag.getInt("Water");
        temperature = tag.getFloat("Temperature");
        co2 = tag.getInt("Co2");
        energy = tag.getInt("Energy");
        mug = tag.contains("Mug") ? tag.getInt("Mug") : -1;
        trayContent = MugContent.byOrdinal(tag.getInt("Tray"));
        pourStart = tag.contains("PourStart") ? tag.getLong("PourStart") : -1L;
        final int tapOrdinal = tag.contains("Pouring") ? tag.getInt("Pouring") : -1;
        pouring = tapOrdinal < 0 || tapOrdinal >= WaterCoolerBlock.Tap.values().length
                ? null : WaterCoolerBlock.Tap.values()[tapOrdinal];
        chilling = tag.getBoolean("Chilling");
        ratedDraw = tag.getInt("RatedDraw");
        measuredDraw = tag.getInt("MeasuredDraw");
        managed.load(tag.getInt("Priority"), tag.getInt("Lock"));
        links.clear();
        linkTiers.clear();
        final long[] linkArr = tag.getLongArray("Links");
        final byte[] tierArr = tag.getByteArray("LinkTiers");
        final WireTier[] tiers = WireTier.values();
        for (int i = 0; i < linkArr.length; i++) {
            final BlockPos p = BlockPos.of(linkArr[i]);
            links.add(p);
            if (i < tierArr.length && tierArr[i] >= 0 && tierArr[i] < tiers.length) {
                linkTiers.put(p.asLong(), tiers[tierArr[i]]);
            }
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        final CompoundTag tag = super.getUpdateTag(registries);
        saveAdditional(tag, registries);
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    private void sync() {
        setChanged();
        lastSyncedEnergy = energy;
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }
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
        return CHILL_DRAW * 4;
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

    public void adoptNetwork(FeWireNetwork adopted) {
        this.network = adopted;
    }

    @Override
    @javax.annotation.Nullable
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

    public IEnergyStorage energyHandle() {
        return energyView;
    }

    public IFluidHandler fluidHandle() {
        return tankView;
    }

    public Direction backFace() {
        return WaterCoolerBlock.wireFace(getBlockState());
    }

    @Override
    public BlockPos terminalPos() {
        return worldPosition;
    }

    @Override
    public List<BlockPos> links() {
        return links;
    }

    @Override
    public WireTier tierOf(BlockPos link) {
        return linkTiers.getOrDefault(link.asLong(), WireTier.COPPER);
    }

    @Override
    public void addLink(BlockPos far, WireTier tier) {
        final BlockPos at = far.immutable();
        links.add(at);
        linkTiers.put(at.asLong(), tier == null ? WireTier.COPPER : tier);
        syncLinks();
    }

    @Override
    public void removeDeadLink(BlockPos far) {
        linkTiers.remove(far.asLong());
        if (links.remove(far)) {
            syncLinks();
        }
    }

    @Override
    public void dropWiresAndUnlinkAll() {
        if (level == null || level.isClientSide()) {
            return;
        }
        final Map<WireTier, Integer> refund = new EnumMap<>(WireTier.class);
        for (BlockPos link : new ArrayList<>(links)) {
            refund.merge(tierOf(link), 1, Integer::sum);

            if (level.getBlockEntity(link) instanceof com.createstats.power.FeStudPanel panel) {
                panel.forgetLinksTo(worldPosition);
            } else if (level.getBlockEntity(link) instanceof FeWireTerminal other && other != this) {
                other.removeDeadLink(worldPosition);
            }
        }
        links.clear();
        linkTiers.clear();
        syncLinks();
        for (Map.Entry<WireTier, Integer> e : refund.entrySet()) {
            Block.popResource(level, worldPosition,
                    new ItemStack(ModItems.wireItem(e.getKey()), e.getValue()));
        }
    }

    private void syncLinks() {
        setChanged();
        invalidateNetwork();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
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
        setChanged();
    }

    @Override
    public void setManagedShed(boolean shed) {
        if (managed.isShed() == shed) {
            return;
        }
        managed.setShed(shed);
        setChanged();
    }

    @Override
    public boolean isManagedShed() {
        return managed.isShed();
    }

    @Override
    public int ratedDrawFePerTick() {
        return ratedDraw;
    }

    @Override
    public int measuredDrawFePerTick() {
        return measuredDraw;
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        final Locale l = Locale.ROOT;
        tooltip.add(pad(Component.literal("Water Cooler").withStyle(ChatFormatting.GRAY)));

        line(tooltip, "Tank", String.format(l, "%,d / %,d mB  (%d servings)",
                water, TANK_CAPACITY, water / SERVE_MB), water >= SERVE_MB);
        line(tooltip, "Chiller", !on ? "switched off"
                        : chilling ? String.format(l, "running, %d%%  (%d FE/t)",
                                Math.round(temperature * 100.0f), CHILL_DRAW)
                        : String.format(l, "holding at %d%%", Math.round(temperature * 100.0f)),
                isCold());
        line(tooltip, "Carbonator", isCarbonatorReady() ? "charged"
                : String.format(l, "charging, %d%%", Math.round(co2 * 100.0f / CO2_FULL)),
                isCarbonatorReady());
        line(tooltip, "Power", on ? String.format(l, "%,d FE  (idle %d/t)", energy, IDLE_DRAW)
                : "switched off", on && energy >= IDLE_DRAW);
        line(tooltip, "Mug", hasMug()
                        ? String.format(l, "%s, %s", mugName(),
                                trayContent.isDrinkable() ? drinkName(trayContent) : "empty")
                        : "none on the grille",
                hasMug());

        for (WaterCoolerBlock.Tap tap : WaterCoolerBlock.Tap.values()) {
            final Refusal refusal = checkMachine(tap);
            tooltip.add(pad(Component.literal("  " + tapName(tap) + ": ")
                    .withStyle(ChatFormatting.DARK_GRAY)
                    .append(Component.literal(refusal == Refusal.NONE ? "ready" : reason(refusal))
                            .withStyle(refusal == Refusal.NONE
                                    ? ChatFormatting.GREEN : ChatFormatting.GOLD))));
        }
        return true;
    }

    private static String tapName(WaterCoolerBlock.Tap tap) {
        return switch (tap) {
            case SPRING -> "Spring";
            case CHILLED -> "Chilled";
            case SPARKLING -> "Sparkling";
        };
    }

    private static String drinkName(MugContent content) {
        return switch (content) {
            case SPRING_WATER -> "spring water";
            case CHILLED_WATER -> "chilled water";
            case SPARKLING_WATER -> "sparkling water";
            default -> content.getSerializedName();
        };
    }

    private static String reason(Refusal refusal) {
        return switch (refusal) {
            case OFF -> "switched off";
            case NO_POWER -> "no power";
            case NO_WATER -> "tank empty";
            case NOT_COLD -> "not cold yet";
            case NO_CO2 -> "carbonator charging";
            case NO_MUG -> "no mug";
            case MUG_FULL -> "mug already full";
            case BUSY -> "pouring";
            default -> "not ready";
        };
    }

    private String mugName() {
        return switch (mug) {
            case 1 -> "Enamel";
            case 2 -> "Steel";
            default -> "Ceramic";
        };
    }

    private static void line(List<Component> tooltip, String label, String value, boolean good) {
        tooltip.add(pad(Component.literal(label + ": ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(value)
                        .withStyle(good ? ChatFormatting.WHITE : ChatFormatting.GOLD))));
    }

    private static Component pad(Component body) {
        return Component.literal("    ").append(body);
    }
}
