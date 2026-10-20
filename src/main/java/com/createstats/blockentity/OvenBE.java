package com.createstats.blockentity;

import com.createstats.baking.OvenThermal;
import com.createstats.block.OvenBlock;
import com.createstats.power.Adjustable;
import com.createstats.power.ApplianceStatus;
import com.createstats.power.FeGridMember;
import com.createstats.power.FeLock;
import com.createstats.power.FeManagedLoad;
import com.createstats.power.FeManagedState;
import com.createstats.power.FePriority;
import com.createstats.power.FeWireNetwork;
import com.createstats.power.FeWireTerminal;
import com.createstats.power.WireTier;
import com.createstats.recipe.BakingRecipe;
import com.createstats.recipe.BakingRecipeInput;
import com.createstats.registry.ModItems;
import com.createstats.registry.ModRecipes;
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
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public class OvenBE extends BlockEntity implements FeGridMember, FeManagedLoad, FeWireTerminal,
        Adjustable, com.createstats.power.Gateable, com.createstats.power.HomeAppliance,
        com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation,
        com.createstats.util.CopyableSettings {
    public static final int SLOT_TIN_IN = 0;
    public static final int SLOT_CAKE = 1;
    public static final int SLOT_TIN_OUT = 2;
    public static final int SLOTS = 3;

    public static final int DRAW_IDLE = 4;

    public static final int DRAW_LAMP = 2;

    public static final int BUFFER = 6_000;

    private static final int DOOR_TICKS = 7;
    private static final int PRESS_TICKS = 4;
    private static final int KNOB_TICKS = 5;

    public static final float BROWNING_POINT = 0.55f;

    private final ItemStack[] slots = {ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY};

    private int setting = 1;
    private int knobFrom = 1;
    private long knobTime = Long.MIN_VALUE;

    private boolean started;
    private boolean doorOpen;
    private long doorTime = Long.MIN_VALUE;
    private boolean lampOn;

    private int progress;
    private int bake;

    private final OvenThermal thermal = new OvenThermal();

    private int pressed = -1;
    private long pressTime = Long.MIN_VALUE;
    private long clientPressTime = Long.MIN_VALUE;

    private boolean lastRedstone;

    private boolean gated;

    private int energy;

    private long fedAt = Long.MIN_VALUE;
    private int ratedDraw;
    private int measuredDraw;
    private int lastComparator = -1;

    private boolean clientWired;
    private boolean clientSupply;

    private boolean syncedWired;
    private boolean syncedSupply;

    private final FeManagedState managed = new FeManagedState();
    private final List<BlockPos> links = new ArrayList<>();
    private final Map<Long, WireTier> linkTiers = new HashMap<>();
    @Nullable
    private FeWireNetwork network;

    public OvenBE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    private Direction facing() {
        final BlockState state = getBlockState();
        return state.hasProperty(OvenBlock.FACING)
                ? state.getValue(OvenBlock.FACING) : Direction.NORTH;
    }

    public Direction backFace() {
        return facing().getOpposite();
    }

    // the door face, nothing wires to it. three places spelled this backFace().getOpposite(), one typo from wiring through the door
    public Direction frontFace() {
        return facing();
    }

    public enum Refusal {
        NONE, EMPTY, OFF, DOOR_OPEN, COLD, FULL, WRONG_ITEM, NO_POWER, SHED, GATED, BUSY;

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public boolean isStarted() {
        return started;
    }

    public boolean isDoorOpen() {
        return doorOpen;
    }

    public boolean isLampOn() {
        return lampOn;
    }

    public int setting() {
        return setting;
    }

    public int setpoint() {
        return OvenThermal.setpoint(setting);
    }

    public int setpointCelsius() {
        return OvenThermal.setpointCelsius(setting);
    }

    public int celsius() {
        return thermal.celsius();
    }

    public ItemStack slot(int index) {
        return slots[Mth.clamp(index, 0, SLOTS - 1)];
    }

    public ItemStack loaded() {
        return slots[SLOT_TIN_IN];
    }

    public boolean isCavityLit() {
        return lampOn || isHeating();
    }

    public boolean isHeating() {
        return measuredDraw > standbyDraw() && started;
    }

    private int standbyDraw() {
        return DRAW_IDLE + (lampOn ? DRAW_LAMP : 0);
    }

    public float bakeFraction() {
        return bake <= 0 ? 0.0f : Mth.clamp(progress / (float) bake, 0.0f, 1.0f);
    }

    public int remainingTicks() {
        return bake <= 0 || slots[SLOT_TIN_IN].isEmpty() ? -1 : Math.max(0, bake - progress);
    }

    public int preheatTicks() {
        final int ticks = OvenThermal.preheatTicks(thermal.heat(), setpoint());
        return Math.max(0, ticks);
    }

    public Optional<BakingRecipe> recipeFor(ItemStack stack) {
        if (level == null || stack.isEmpty()) {
            return Optional.empty();
        }
        return level.getRecipeManager()
                .getRecipeFor(ModRecipes.BAKING_TYPE.get(), new BakingRecipeInput(stack), level)
                .map(holder -> holder.value());
    }

    public boolean bakeable(ItemStack stack) {
        return recipeFor(stack).isPresent();
    }

    public int cycleSetting() {
        knobFrom = setting;
        setting = (setting + 1) % OvenThermal.settings();
        knobTime = level == null ? 0L : level.getGameTime();

        if (bake > 0 && !slots[SLOT_TIN_IN].isEmpty()) {
            final float done = bakeFraction();
            bake = bakeTicksFor(slots[SLOT_TIN_IN]);
            progress = Math.round(done * bake);
        }
        sync();
        return setting;
    }

    public boolean toggleStart() {
        started = !started;
        sync();
        return started;
    }

    public boolean toggleLamp() {
        lampOn = !lampOn;
        sync();
        return lampOn;
    }

    public void setDoorOpen(boolean value) {
        if (doorOpen == value) {
            return;
        }
        doorOpen = value;
        doorTime = level == null ? 0L : level.getGameTime();
        refreshState();
        sync();
    }

    public void markPressed(int index) {
        pressed = index;
        pressTime = level == null ? 0L : level.getGameTime();
        sync();
    }

    public int pressedButton() {
        if (level == null || pressed < 0) {
            return -1;
        }
        final long at = level.isClientSide() ? clientPressTime : pressTime;
        return at != Long.MIN_VALUE && level.getGameTime() - at < PRESS_TICKS ? pressed : -1;
    }

    public Refusal checkLoad(ItemStack stack) {
        if (!doorOpen) {
            return Refusal.DOOR_OPEN;
        }
        if (!slots[SLOT_TIN_IN].isEmpty()) {
            return Refusal.FULL;
        }
        if (!bakeable(stack)) {
            return Refusal.WRONG_ITEM;
        }
        return Refusal.NONE;
    }

    public int load(ItemStack held) {
        if (checkLoad(held) != Refusal.NONE) {
            return 0;
        }
        slots[SLOT_TIN_IN] = held.copyWithCount(1);
        progress = 0;
        bake = bakeTicksFor(slots[SLOT_TIN_IN]);
        sync();
        refreshComparator();
        return 1;
    }

    public List<ItemStack> unloadFinished() {
        final List<ItemStack> out = new ArrayList<>(2);
        for (int slot : new int[]{SLOT_CAKE, SLOT_TIN_OUT}) {
            if (!slots[slot].isEmpty()) {
                out.add(slots[slot]);
                slots[slot] = ItemStack.EMPTY;
            }
        }
        if (!out.isEmpty()) {
            sync();
            refreshComparator();
        }
        return out;
    }

    public ItemStack unloadRaw() {
        final ItemStack out = slots[SLOT_TIN_IN];
        if (out.isEmpty()) {
            return ItemStack.EMPTY;
        }
        slots[SLOT_TIN_IN] = ItemStack.EMPTY;
        progress = 0;
        bake = 0;
        sync();
        refreshComparator();
        return out;
    }

    public boolean hasFinished() {
        return !slots[SLOT_CAKE].isEmpty() || !slots[SLOT_TIN_OUT].isEmpty();
    }

    public void dropContents() {
        if (level == null || level.isClientSide()) {
            return;
        }
        for (int i = 0; i < SLOTS; i++) {
            if (!slots[i].isEmpty()) {
                Block.popResource(level, worldPosition, slots[i].copy());
                slots[i] = ItemStack.EMPTY;
            }
        }
    }

    private int bakeTicksFor(ItemStack stack) {
        return recipeFor(stack)
                .map(recipe -> OvenThermal.bakeTicks(recipe.bakeTime(), setting))
                .orElse(0);
    }

    public void serverTick() {
        if (level == null || level.isClientSide()) {
            return;
        }
        if (network == null || network.isDirty()) {
            FeWireNetwork.buildFrom(this);
        }
        if (network != null) {
            network.solveOnce(level);
        }

        if (network == null || !network.isPduManaged(level.getGameTime())) {
            setManagedShed(false);
        }
        payPower();
        advanceBake();
        heartbeat();
    }

    private void payPower() {
        final Refusal why = checkRun();

        final boolean dead = why == Refusal.SHED || why == Refusal.GATED;
        final int standby = dead ? 0 : standbyDraw();
        final int elements = dead || !started ? 0 : thermal.elementDraw(setpoint());
        final int want = standby + elements;
        ratedDraw = want;

        final int spent = Math.min(energy, want);
        if (spent > 0) {
            energy -= spent;
            setChanged();
        }
        measuredDraw = spent;
        thermal.step(Math.max(0, spent - standby), doorOpen ? OvenThermal.OPEN_DOOR_LOSS : 1);
        refreshState();
    }

    public Refusal checkRun() {
        if (managed.isSuppressed()) {
            return Refusal.SHED;
        }
        if (gated) {
            return Refusal.GATED;
        }
        if (!started) {
            return Refusal.OFF;
        }
        if (energy < standbyDraw()) {
            return Refusal.NO_POWER;
        }
        if (doorOpen) {
            return Refusal.DOOR_OPEN;
        }
        if (slots[SLOT_TIN_IN].isEmpty()) {
            return Refusal.EMPTY;
        }
        if (hasFinished()) {
            return Refusal.FULL;
        }
        if (!thermal.atTemperature(setpoint())) {
            return Refusal.COLD;
        }
        return Refusal.NONE;
    }

    private void advanceBake() {
        if (checkRun() != Refusal.NONE) {
            return;
        }
        if (bake <= 0) {
            bake = bakeTicksFor(slots[SLOT_TIN_IN]);
            if (bake <= 0) {
                return;
            }
        }
        progress++;
        if (progress < bake) {
            if (progress % Math.max(1, bake / 16) == 0) {
                sync();
            }
            return;
        }
        finishBake();
    }

    private void finishBake() {
        final Optional<BakingRecipe> recipe = recipeFor(slots[SLOT_TIN_IN]);
        if (recipe.isEmpty()) {
            return;
        }
        slots[SLOT_CAKE] = recipe.get().bake();
        slots[SLOT_TIN_OUT] = recipe.get().emptied();
        slots[SLOT_TIN_IN] = ItemStack.EMPTY;
        progress = 0;
        bake = 0;
        if (level != null) {
            OvenBlock.playDing(level, worldPosition);
        }
        sync();
        refreshComparator();
    }

    public boolean hasSupply() {
        if (energy > 0) {
            return true;
        }
        return level != null && level.isClientSide() ? clientSupply : gridHasSupply();
    }

    private boolean gridHasSupply() {
        return network != null && network.lastResult().available() > 0;
    }

    public boolean isWired() {
        return level != null && level.isClientSide() ? clientWired : onGrid();
    }

    private boolean onGrid() {
        return !links.isEmpty() || (network != null && network.size() > 1) || isFed();
    }

    private boolean isFed() {
        return level != null && fedAt != Long.MIN_VALUE && level.getGameTime() - fedAt <= 40L;
    }

    public boolean isStalled() {
        if (!started || slots[SLOT_TIN_IN].isEmpty()) {
            return false;
        }
        final Refusal why = checkRun();
        return why == Refusal.NO_POWER || why == Refusal.SHED || why == Refusal.GATED
                || why == Refusal.DOOR_OPEN;
    }

    private void refreshState() {
        if (level == null || level.isClientSide()) {
            return;
        }
        final BlockState state = getBlockState();
        BlockState wanted = state;
        if (state.hasProperty(OvenBlock.LIT) && state.getValue(OvenBlock.LIT) != isCavityLit()) {
            wanted = wanted.setValue(OvenBlock.LIT, isCavityLit());
        }
        if (state.hasProperty(OvenBlock.OPEN) && state.getValue(OvenBlock.OPEN) != doorOpen) {
            wanted = wanted.setValue(OvenBlock.OPEN, doorOpen);
        }
        if (wanted != state) {
            level.setBlock(worldPosition, wanted, Block.UPDATE_ALL);
        }
    }

    private void heartbeat() {
        if (level == null || (level.getGameTime() % 10L) != 0L) {
            return;
        }

        final boolean wiredNow = onGrid();
        final boolean supplyNow = gridHasSupply();
        if (started || thermal.excess() > 0 || measuredDraw > 0
                || wiredNow != syncedWired || supplyNow != syncedSupply) {
            syncedWired = wiredNow;
            syncedSupply = supplyNow;
            sync();
        }
    }

    public float doorSwing(float partialTick) {
        if (level == null) {
            return doorOpen ? 1.0f : 0.0f;
        }
        final float target = doorOpen ? 1.0f : 0.0f;
        if (doorTime == Long.MIN_VALUE) {
            return target;
        }
        final float elapsed = (level.getGameTime() - doorTime) + partialTick;
        if (elapsed >= DOOR_TICKS) {
            return target;
        }
        final float t = Mth.clamp(elapsed / DOOR_TICKS, 0.0f, 1.0f);

        final float smooth = t * t * (3.0f - 2.0f * t);
        return doorOpen ? smooth : 1.0f - smooth;
    }

    public float knobPosition(float partialTick) {
        if (level == null || knobTime == Long.MIN_VALUE) {
            return setting;
        }
        final float elapsed = (level.getGameTime() - knobTime) + partialTick;
        if (elapsed >= KNOB_TICKS) {
            return setting;
        }
        final float t = Mth.clamp(elapsed / KNOB_TICKS, 0.0f, 1.0f);
        final float smooth = t * t * (3.0f - 2.0f * t);

        final int span = OvenThermal.settings();
        float delta = setting - knobFrom;
        if (delta > span / 2.0f) {
            delta -= span;
        } else if (delta < -span / 2.0f) {
            delta += span;
        }
        return knobFrom + delta * smooth;
    }

    private final IEnergyStorage energyView = new IEnergyStorage() {
        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            final int room = Math.min(maxReceive, BUFFER - energy);
            if (room <= 0) {
                return 0;
            }
            if (!simulate) {
                energy += room;
                if (level != null && !level.isClientSide()) {
                    fedAt = level.getGameTime();
                }
                setChanged();
            }
            return room;
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
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

    private final class SlotHandler implements IItemHandler {
        private final boolean insert;

        private SlotHandler(boolean insert) {
            this.insert = insert;
        }

        @Override
        public int getSlots() {
            return SLOTS;
        }

        @Override
        public @NotNull ItemStack getStackInSlot(int slot) {
            return slot >= 0 && slot < SLOTS ? slots[slot] : ItemStack.EMPTY;
        }

        @Override
        public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
            if (!insert || slot != SLOT_TIN_IN || stack.isEmpty()
                    || !slots[SLOT_TIN_IN].isEmpty() || !bakeable(stack)) {
                return stack;
            }
            if (!simulate) {
                slots[SLOT_TIN_IN] = stack.copyWithCount(1);
                progress = 0;
                bake = bakeTicksFor(slots[SLOT_TIN_IN]);
                sync();
                refreshComparator();
            }
            return stack.copyWithCount(stack.getCount() - 1);
        }

        @Override
        public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (insert || amount <= 0 || (slot != SLOT_CAKE && slot != SLOT_TIN_OUT)
                    || slots[slot].isEmpty()) {
                return ItemStack.EMPTY;
            }
            final int taken = Math.min(amount, slots[slot].getCount());
            final ItemStack out = slots[slot].copyWithCount(taken);
            if (!simulate) {
                slots[slot].shrink(taken);
                if (slots[slot].isEmpty()) {
                    slots[slot] = ItemStack.EMPTY;
                }
                sync();
                refreshComparator();
            }
            return out;
        }

        @Override
        public int getSlotLimit(int slot) {
            return slot == SLOT_TIN_IN ? 1 : 64;
        }

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            return insert && slot == SLOT_TIN_IN && bakeable(stack);
        }
    }

    private final IItemHandler inputHandle = new SlotHandler(true);
    private final IItemHandler outputHandle = new SlotHandler(false);

    // top takes tins, bottom gives cakes. a bottom that accepted would feed the cake straight back
    public static void registerCapabilities(
            net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK,
                com.createstats.registry.ModBlockEntities.OVEN.get(),
                (be, side) -> {
                    if (side == Direction.UP) {
                        return be.inputHandle;
                    }
                    if (side == Direction.DOWN) {
                        return be.outputHandle;
                    }
                    return null;
                });
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
        return OvenThermal.DRAW_ELEMENT * 4;
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

    public IEnergyStorage energyHandle() {
        return energyView;
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
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(),
                    Block.UPDATE_CLIENTS);
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
        sync();
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

    public void onRedstone(boolean powered) {
        if (powered == lastRedstone) {
            return;
        }
        lastRedstone = powered;
        if (powered) {
            started = !started;
            sync();
        }
        setChanged();
    }

    @Override
    public void setGated(boolean value) {
        if (gated == value) {
            return;
        }
        gated = value;
        setChanged();
        sync();
    }

    @Override
    public boolean isGated() {
        return gated;
    }

    @Override
    public com.createstats.power.HomeAppliance.Category applianceCategory() {
        return com.createstats.power.HomeAppliance.Category.KITCHEN;
    }

    @Override
    @Nullable
    public ApplianceStatus applianceStatus() {
        final int left = remainingTicks();
        if (left >= 0 && (started || progress > 0)) {
            final Component text = switch (checkRun()) {
                case COLD -> Component.translatable("status.create_stats.home.preheating",
                        ApplianceStatus.clock(preheatTicks()));
                case DOOR_OPEN -> Component.translatable("status.create_stats.home.paused_door");
                case NO_POWER -> Component.translatable("status.create_stats.home.paused_power");
                case SHED -> Component.translatable("status.create_stats.home.paused_pdu");
                case GATED -> Component.translatable("status.create_stats.home.paused_off");
                case OFF -> Component.translatable("status.create_stats.home.stopped",
                        ApplianceStatus.clock(left));
                case FULL -> Component.translatable("status.create_stats.home.paused_full");
                default -> Component.translatable("status.create_stats.home.baking",
                        ApplianceStatus.clock(left));
            };
            return ApplianceStatus.running(text, bake, left,
                    Component.translatable("status.create_stats.home.oven_done"));
        }
        if (isGated() || isManagedShed()
                || (ratedDrawFePerTick() > 0 && measuredDrawFePerTick() <= 0)) {
            return null;
        }
        if (managed.isSuppressed()) {
            return ApplianceStatus.idle(Component.translatable("status.create_stats.home.held_pdu"));
        }
        if (hasFinished()) {
            return ApplianceStatus.idle(slots[SLOT_CAKE].isEmpty()
                    ? Component.translatable("status.create_stats.home.oven_tin")
                    : Component.translatable("status.create_stats.home.oven_cake"));
        }
        if (!slots[SLOT_TIN_IN].isEmpty()) {
            return ApplianceStatus.idle(Component.translatable("status.create_stats.home.loaded"));
        }
        if (doorOpen) {
            return ApplianceStatus.idle(Component.translatable("status.create_stats.home.door_open"));
        }
        if (started) {
            return ApplianceStatus.idle(thermal.atTemperature(setpoint())
                    ? Component.translatable("status.create_stats.home.oven_hot", setpointCelsius())
                    : Component.translatable("status.create_stats.home.preheating",
                            ApplianceStatus.clock(preheatTicks())));
        }
        return ApplianceStatus.idle(Component.translatable("status.create_stats.home.idle"));
    }

    @Override
    public int getMode() {
        return setting;
    }

    @Override
    public void setMode(int mode) {
        final int wanted = Mth.clamp(mode, 0, OvenThermal.settings() - 1);
        if (wanted == setting) {
            return;
        }
        knobFrom = setting;
        setting = wanted;
        knobTime = level == null ? 0L : level.getGameTime();
        if (bake > 0 && !slots[SLOT_TIN_IN].isEmpty()) {
            final float done = bakeFraction();
            bake = bakeTicksFor(slots[SLOT_TIN_IN]);
            progress = Math.round(done * bake);
        }
        sync();
    }

    @Override
    public List<Component> modeLabels() {
        final List<Component> out = new ArrayList<>(OvenThermal.settings());
        for (int i = 0; i < OvenThermal.settings(); i++) {
            out.add(labelFor(i));
        }
        return out;
    }

    @Override
    public String settingsKey() {
        return "create_stats:oven";
    }

    @Override
    public void writeSettings(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("Setting", setting);
    }

    @Override
    public boolean applySettings(CompoundTag tag, HolderLookup.Provider registries,
                                 @Nullable net.minecraft.world.entity.player.Player player, boolean simulate) {
        if (!tag.contains("Setting")) {
            return false;
        }
        if (!simulate) {
            setMode(tag.getInt("Setting"));
        }
        return true;
    }

    @Override
    public void writeFreshDefaults(CompoundTag tag) {
        tag.putInt("Priority", com.createstats.power.FePriority.DEFAULT.ordinal());
    }

    public static Component labelFor(int setting) {
        return Component.literal(OvenThermal.setpointCelsius(setting) + " C");
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        final Refusal blocking = checkRun();
        tooltip.add(pad(Component.literal("Oven").withStyle(ChatFormatting.GRAY)));

        step(tooltip, 1, "Mains", mainsSummary(), energy >= standbyDraw(),
                blocking == Refusal.NO_POWER || blocking == Refusal.SHED
                        || blocking == Refusal.GATED);
        step(tooltip, 2, "Cavity", cavitySummary(), thermal.atTemperature(setpoint()),
                blocking == Refusal.COLD || blocking == Refusal.DOOR_OPEN);
        step(tooltip, 3, "Shelf", shelfSummary(), !slots[SLOT_TIN_IN].isEmpty(),
                blocking == Refusal.EMPTY || blocking == Refusal.FULL);
        step(tooltip, 4, "Bake", bakeSummary(), blocking == Refusal.NONE, false);

        tooltip.add(pad(Component.literal(nextAction(blocking)).withStyle(actionColour(blocking))));
        return true;
    }

    private String mainsSummary() {
        if (!isWired()) {
            return "not wired";
        }
        if (energy <= 0 && !hasSupply()) {
            return "wired, grid is empty";
        }
        return String.format(Locale.ROOT, "%,d/%,d FE, %d of %d/t",
                energy, BUFFER, measuredDraw, ratedDraw);
    }

    private String cavitySummary() {
        final int now = thermal.celsius();
        final int target = setpointCelsius();
        if (doorOpen) {
            return String.format(Locale.ROOT, "%d C, door open", now);
        }
        if (thermal.atTemperature(setpoint())) {
            return String.format(Locale.ROOT, "%d C, ready", now);
        }
        if (!started) {
            return String.format(Locale.ROOT, "%d C, cooling", now);
        }
        final int left = preheatTicks();
        return String.format(Locale.ROOT, "%d of %d C, %s", now, target, clock(left));
    }

    private String shelfSummary() {
        final StringBuilder sb = new StringBuilder();
        if (slots[SLOT_TIN_IN].isEmpty()) {
            sb.append("empty");
        } else {
            sb.append(slots[SLOT_TIN_IN].getHoverName().getString());
        }
        if (hasFinished()) {
            sb.append(slots[SLOT_CAKE].isEmpty() ? ", tin to take" : ", cake to take");
        }
        return sb.toString();
    }

    private String bakeSummary() {
        final int left = remainingTicks();
        if (left < 0) {
            return String.format(Locale.ROOT, "%d C setting", setpointCelsius());
        }
        return String.format(Locale.ROOT, "%d%% at %d C, %s left",
                Math.round(bakeFraction() * 100.0f), setpointCelsius(), clock(left));
    }

    private String nextAction(Refusal blocking) {
        if (!isWired() && blocking != Refusal.SHED && blocking != Refusal.GATED) {
            return "Wire the back or a flank, or plug a cord";
        }
        return switch (blocking) {
            case NONE -> "Baking, " + clock(remainingTicks()) + " to go";
            case SHED -> "Held: shed by the PDU";
            case GATED -> "Held: switched off at a panel";

            case NO_POWER -> "Held: waiting for charge";
            case OFF -> hasFinished() ? "Take the cake out" : "Press start";
            case DOOR_OPEN -> "Shut the door";
            case EMPTY -> hasFinished()
                    ? "Take the cake out"
                    : "Put a filled Cake Mold on the rack";
            case FULL -> "Take the finished cake out first";
            case COLD -> "Preheating, " + clock(preheatTicks()) + " to go";
            default -> "Held.";
        };
    }

    private ChatFormatting actionColour(Refusal blocking) {
        if (blocking == Refusal.NONE) {
            return ChatFormatting.AQUA;
        }

        if (!isWired() && blocking != Refusal.SHED && blocking != Refusal.GATED) {
            return ChatFormatting.GOLD;
        }
        if (isStalled()) {
            return ChatFormatting.GOLD;
        }
        return blocking == Refusal.OFF || blocking == Refusal.EMPTY
                ? ChatFormatting.DARK_GRAY : ChatFormatting.GREEN;
    }

    public static String clock(int ticks) {
        final int seconds = (Math.max(0, ticks) + 19) / 20;
        return String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
    }

    private static void step(List<Component> tooltip, int index, String name, String value,
                             boolean satisfied, boolean blocking) {
        final ChatFormatting colour = blocking ? ChatFormatting.GOLD
                : satisfied ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY;
        tooltip.add(pad(Component.literal(String.format(Locale.ROOT, "%s %d  %s: %s",
                blocking ? ">" : " ", index, name, value)).withStyle(colour)));
    }

    private static Component pad(Component line) {
        return Component.literal("    ").append(line);
    }

    public int comparatorSignal() {
        if (hasFinished()) {
            return 15;
        }
        if (bake <= 0 || slots[SLOT_TIN_IN].isEmpty()) {
            return 0;
        }
        return Mth.clamp(1 + Math.round(bakeFraction() * 13.0f), 1, 14);
    }

    private void refreshComparator() {
        if (level == null || level.isClientSide()) {
            return;
        }
        final int now = comparatorSignal();
        if (now != lastComparator) {
            lastComparator = now;
            level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
        }
    }

    private void sync() {
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(),
                    Block.UPDATE_ALL);
            refreshComparator();
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        for (int i = 0; i < SLOTS; i++) {
            if (!slots[i].isEmpty()) {
                tag.put("Slot" + i, slots[i].save(registries));
            }
        }
        tag.putInt("Setting", setting);
        tag.putInt("KnobFrom", knobFrom);
        tag.putLong("KnobTime", knobTime);
        tag.putBoolean("Started", started);
        tag.putBoolean("DoorOpen", doorOpen);
        tag.putLong("DoorTime", doorTime);
        tag.putBoolean("Lamp", lampOn);
        tag.putInt("Progress", progress);
        tag.putInt("Bake", bake);
        tag.putInt("Heat", thermal.heat());
        tag.putInt("Pressed", pressed);
        tag.putLong("PressTime", pressTime);
        tag.putBoolean("LastRedstone", lastRedstone);
        tag.putBoolean("Gated", gated);
        tag.putInt("Energy", energy);

        tag.putInt("RatedDraw", ratedDraw);
        tag.putInt("MeasuredDraw", measuredDraw);

        tag.putBoolean("Wired", onGrid());
        tag.putBoolean("Supply", gridHasSupply());
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
        for (int i = 0; i < SLOTS; i++) {
            slots[i] = tag.contains("Slot" + i)
                    ? ItemStack.parseOptional(registries, tag.getCompound("Slot" + i))
                    : ItemStack.EMPTY;
        }
        setting = Mth.clamp(tag.getInt("Setting"), 0, OvenThermal.settings() - 1);
        knobFrom = Mth.clamp(tag.getInt("KnobFrom"), 0, OvenThermal.settings() - 1);
        knobTime = tag.contains("KnobTime") ? tag.getLong("KnobTime") : Long.MIN_VALUE;
        started = tag.getBoolean("Started");
        doorOpen = tag.getBoolean("DoorOpen");
        doorTime = tag.contains("DoorTime") ? tag.getLong("DoorTime") : Long.MIN_VALUE;
        lampOn = tag.getBoolean("Lamp");
        progress = Math.max(0, tag.getInt("Progress"));
        bake = Math.max(0, tag.getInt("Bake"));
        thermal.setHeat(tag.contains("Heat") ? tag.getInt("Heat") : OvenThermal.AMBIENT);
        lastRedstone = tag.getBoolean("LastRedstone");
        gated = tag.getBoolean("Gated");
        energy = Mth.clamp(tag.getInt("Energy"), 0, BUFFER);
        ratedDraw = Math.max(0, tag.getInt("RatedDraw"));
        measuredDraw = Math.max(0, tag.getInt("MeasuredDraw"));
        clientWired = tag.getBoolean("Wired");
        clientSupply = tag.getBoolean("Supply");
        managed.load(tag.getInt("Priority"), tag.getInt("Lock"));
        final long wasPressTime = pressTime;
        pressed = tag.contains("Pressed") ? tag.getInt("Pressed") : -1;
        pressTime = tag.contains("PressTime") ? tag.getLong("PressTime") : Long.MIN_VALUE;

        if (level != null && level.isClientSide() && pressTime != wasPressTime
                && pressTime != Long.MIN_VALUE
                && Math.abs(level.getGameTime() - pressTime) <= PRESS_TICKS + 20L) {
            clientPressTime = level.getGameTime();
        }
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
    }
}
