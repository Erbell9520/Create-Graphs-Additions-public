package com.createstats.blockentity;

import com.createstats.block.BulkWasherBlock;
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
import com.createstats.registry.ModItems;
import com.simibubi.create.AllRecipeTypes;
import com.simibubi.create.content.kinetics.fan.processing.SplashingRecipe;
import net.minecraft.ChatFormatting;
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
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
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

// the opposite trade to a fan wash tower: one block, no water source
public class BulkWasherBE extends BlockEntity implements FeGridMember, FeManagedLoad, FeWireTerminal,
        Adjustable, com.createstats.power.Gateable, com.createstats.power.HomeAppliance,
        com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation,
        com.createstats.util.CopyableSettings {
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
        return com.createstats.power.HomeAppliance.Category.LAUNDRY;
    }

    @Override
    @Nullable
    public ApplianceStatus applianceStatus() {
        if (!pending.isEmpty()) {
            final Component washing = Component.translatable("status.create_stats.home.washing",
                    modeLabels().get(mode.ordinal()), ApplianceStatus.clock(readoutTicks()));
            final Component text;
            if (running) {
                text = washing;
            } else {
                text = switch (checkRun()) {
                    case OFF -> Component.translatable("status.create_stats.home.paused_off");
                    case NOT_STARTED -> Component.translatable("status.create_stats.home.loaded");
                    case CUT -> Component.translatable("status.create_stats.home.paused_redstone");
                    case SHED -> Component.translatable("status.create_stats.home.paused_pdu");
                    case DOOR_OPEN -> Component.translatable("status.create_stats.home.paused_door");
                    case NO_WATER -> Component.translatable("status.create_stats.home.paused_water");
                    case NO_POWER -> Component.translatable("status.create_stats.home.paused_power");
                    default -> washing;
                };
            }
            return ApplianceStatus.running(text, 0, 0,
                    Component.translatable("status.create_stats.home.washer_done"));
        }
        if (isGated() || isManagedShed()
                || (ratedDrawFePerTick() > 0 && measuredDrawFePerTick() <= 0)) {
            return null;
        }
        if (managed.isSuppressed()) {
            return ApplianceStatus.idle(Component.translatable("status.create_stats.home.held_pdu"));
        }
        if (redstoneCut) {
            return ApplianceStatus.idle(Component.translatable("status.create_stats.home.held_redstone"));
        }
        if (!washed.isEmpty()) {
            return ApplianceStatus.idle(Component.translatable("status.create_stats.home.done_count",
                    washedCount()));
        }
        if (doorOpen) {
            return ApplianceStatus.idle(Component.translatable("status.create_stats.home.door_open"));
        }
        return ApplianceStatus.idle(Component.translatable("status.create_stats.home.empty"));
    }

    public static final int DRUM_CAPACITY = 256;

    public static final int WATER_PER_ITEM = 20;

    public static final int TANK_CAPACITY = 3_000;

    public static final int DRAW_IDLE = 4;

    public static final int BUFFER = 24_000;

    public static final int DOOR_TICKS = 9;

    public static final int PRESS_TICKS = 4;

    private static final int DRUM_SYNC_TICKS = 4;

    public enum WashMode {
        ECO(16, 200, 8),

        HALF(32, 140, 46),

        FULL(64, 100, 256);

        public final int batch;
        public final int cycleTicks;
        public final int drawFe;

        WashMode(int batch, int cycleTicks, int drawFe) {
            this.batch = batch;
            this.cycleTicks = cycleTicks;
            this.drawFe = drawFe;
        }

        public float itemsPerSecond() {
            return batch * 20.0f / cycleTicks;
        }

        public int fePerItem() {
            return Math.round(drawFe * (float) cycleTicks / batch);
        }

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }

        public WashMode next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    private boolean on;
    private WashMode mode = WashMode.HALF;

    private boolean doorOpen;
    private long doorTime = Long.MIN_VALUE;

    private final List<ItemStack> pending = new ArrayList<>();

    private final List<ItemStack> washed = new ArrayList<>();

    private int water;

    private int progress;

    private boolean running;
    private boolean redstoneCut;

    private boolean started;

    private int pressedButton = -1;
    private long pressTime = Long.MIN_VALUE;
    private long clientPressTime = Long.MIN_VALUE;

    private int energy;
    private int ratedDraw;
    private int measuredDraw;
    private int lastSyncedEnergy;
    private int syncCooldown;
    private int lastComparator = -1;

    // sync per item = 13 full drum packets a second per machine. no
    private boolean drumDirty;
    private int drumSyncCooldown;

    private final FeManagedState managed = new FeManagedState();
    private final List<BlockPos> links = new ArrayList<>();
    private final Map<Long, WireTier> linkTiers = new HashMap<>();
    @Nullable
    private FeWireNetwork network;

    @Nullable
    private RecipeManager.CachedCheck<SingleRecipeInput, SplashingRecipe> splashCheck;

    private float drumAngle;
    private float drumSpeed;

    public BulkWasherBE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    private Direction facing() {
        final BlockState state = getBlockState();
        return state.hasProperty(BulkWasherBlock.FACING)
                ? state.getValue(BulkWasherBlock.FACING) : Direction.NORTH;
    }

    public Direction backFace() {
        return facing().getOpposite();
    }

    public Direction inputFace() {
        return facing().getClockWise();
    }

    public Direction outputFace() {
        return facing().getCounterClockWise();
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

    private final IFluidHandler tankView = new IFluidHandler() {
        @Override
        public int getTanks() {
            return 1;
        }

        @Override
        public @NotNull FluidStack getFluidInTank(int tank) {
            return water <= 0 ? FluidStack.EMPTY : new FluidStack(Fluids.WATER, water);
        }

        @Override
        public int getTankCapacity(int tank) {
            return TANK_CAPACITY;
        }

        @Override
        public boolean isFluidValid(int tank, @NotNull FluidStack stack) {
            return stack.getFluid() == Fluids.WATER;
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

    private final class DrumHandler implements IItemHandler {
        private final boolean insert;

        private DrumHandler(boolean insert) {
            this.insert = insert;
        }

        @Override
        public int getSlots() {
            return 1;
        }

        @Override
        public @NotNull ItemStack getStackInSlot(int slot) {
            final List<ItemStack> side = insert ? pending : washed;
            return side.isEmpty() ? ItemStack.EMPTY : side.get(0);
        }

        @Override
        public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
            if (!insert || stack.isEmpty() || !canAccept(stack)) {
                return stack;
            }
            final int taken = Math.min(drumRoom(), stack.getCount());
            if (taken <= 0) {
                return stack;
            }
            if (!simulate) {
                addTo(pending, stack.copyWithCount(taken));
                sync();
            }
            final int left = stack.getCount() - taken;
            return left <= 0 ? ItemStack.EMPTY : stack.copyWithCount(left);
        }

        @Override
        public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (insert || washed.isEmpty() || amount <= 0) {
                return ItemStack.EMPTY;
            }
            final ItemStack head = washed.get(0);
            final int taken = Math.min(amount, head.getCount());
            final ItemStack out = head.copyWithCount(taken);
            if (!simulate) {
                head.shrink(taken);
                if (head.isEmpty()) {
                    washed.remove(0);
                }
                sync();
            }
            return out;
        }

        @Override
        public int getSlotLimit(int slot) {
            return DRUM_CAPACITY;
        }

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            return insert && canAccept(stack);
        }
    }

    private final IItemHandler inputHandle = new DrumHandler(true);
    private final IItemHandler outputHandle = new DrumHandler(false);

    public static void registerCapabilities(
            net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK,
                com.createstats.registry.ModBlockEntities.BULK_WASHER.get(),
                (be, side) -> {
                    if (side == null) {
                        return null;
                    }

                    if (side == be.inputFace() || side == be.backFace() || side == Direction.UP) {
                        return be.inputHandle;
                    }
                    if (side == be.outputFace() || side == Direction.DOWN) {
                        return be.outputHandle;
                    }
                    return null;
                });
        event.registerBlockEntity(net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK,
                com.createstats.registry.ModBlockEntities.BULK_WASHER.get(),

                (be, side) -> side == null || side == be.backFace() || side == Direction.UP
                        ? be.tankView : null);
    }

    private RecipeManager.CachedCheck<SingleRecipeInput, SplashingRecipe> splashCheck() {
        if (splashCheck == null) {
            final RecipeType<SplashingRecipe> type =
                    AllRecipeTypes.SPLASHING.<SingleRecipeInput, SplashingRecipe>getType();
            splashCheck = RecipeManager.createCheck(type);
        }
        return splashCheck;
    }

    @Nullable
    private SplashingRecipe recipeFor(ItemStack stack) {
        if (level == null || stack.isEmpty()) {
            return null;
        }
        final Optional<RecipeHolder<SplashingRecipe>> found =
                splashCheck().getRecipeFor(new SingleRecipeInput(stack), level);
        return found.map(RecipeHolder::value).orElse(null);
    }

    public boolean canWash(ItemStack stack) {
        return recipeFor(stack) != null;
    }

    private static int countOf(List<ItemStack> side) {
        int total = 0;
        for (final ItemStack stack : side) {
            total += stack.getCount();
        }
        return total;
    }

    public int pendingCount() {
        return countOf(pending);
    }

    public int washedCount() {
        return countOf(washed);
    }

    public int drumCount() {
        return pendingCount() + washedCount();
    }

    public int drumRoom() {
        return Math.max(0, DRUM_CAPACITY - drumCount());
    }

    private static void addTo(List<ItemStack> side, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        int left = stack.getCount();
        for (final ItemStack held : side) {
            if (!ItemStack.isSameItemSameComponents(held, stack)) {
                continue;
            }
            final int room = held.getMaxStackSize() - held.getCount();
            if (room <= 0) {
                continue;
            }
            final int moved = Math.min(room, left);
            held.grow(moved);
            left -= moved;
            if (left <= 0) {
                return;
            }
        }
        while (left > 0) {
            final int chunk = Math.min(left, stack.getMaxStackSize());
            side.add(stack.copyWithCount(chunk));
            left -= chunk;
        }
    }

    public boolean isOn() {
        return on;
    }

    public WashMode mode() {
        return mode;
    }

    public boolean isDoorOpen() {
        return doorOpen;
    }

    public boolean isRunning() {
        return running;
    }

    public int waterMb() {
        return water;
    }

    public float waterFraction() {
        return Mth.clamp(water / (float) TANK_CAPACITY, 0.0f, 1.0f);
    }

    public int storedEnergyFe() {
        return energy;
    }

    public int bufferSize() {
        return BUFFER;
    }

    public List<ItemStack> pendingStacks() {
        return pending;
    }

    public List<ItemStack> washedStacks() {
        return washed;
    }

    public float doorSwing(float partialTick) {
        if (level == null || doorTime == Long.MIN_VALUE) {
            return doorOpen ? 1.0f : 0.0f;
        }
        final float elapsed = level.getGameTime() - doorTime + partialTick;
        final float t = Mth.clamp(elapsed / DOOR_TICKS, 0.0f, 1.0f);

        final float eased = t * t * (3.0f - 2.0f * t);
        return doorOpen ? eased : 1.0f - eased;
    }

    public int readoutTicks() {
        if (!on) {
            return -1;
        }
        final int waiting = pendingCount();
        if (waiting <= 0) {
            return 0;
        }
        final long ticks = ((long) waiting * mode.cycleTicks - progress + mode.batch - 1)
                / mode.batch;
        return (int) Mth.clamp(ticks, 0L, Integer.MAX_VALUE);
    }

    public boolean colonLit() {
        if (level == null) {
            return true;
        }
        if (running || pendingCount() <= 0) {
            return true;
        }
        return ((level.getGameTime() / 10L) & 1L) == 0L;
    }

    public enum Refusal {
        NONE, OFF, NOT_STARTED, CUT, SHED, EMPTY, NO_RECIPE, NO_POWER, NO_WATER, FULL, DOOR_OPEN,
        DOOR_LOCKED;

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public Refusal checkRun() {
        return checkRun(false);
    }

    private Refusal ratedRun() {
        return checkRun(true);
    }

    private Refusal checkRun(boolean ignoreAutoShed) {
        if (!on) {
            return Refusal.OFF;
        }
        if (!started) {
            return Refusal.NOT_STARTED;
        }
        if (redstoneCut) {
            return Refusal.CUT;
        }
        if (managed.isForcedOff() || (!ignoreAutoShed && managed.isSuppressed())) {
            return Refusal.SHED;
        }
        if (doorOpen) {
            return Refusal.DOOR_OPEN;
        }
        if (pending.isEmpty()) {
            return Refusal.EMPTY;
        }
        if (water < WATER_PER_ITEM) {
            return Refusal.NO_WATER;
        }
        if (energy < mode.drawFe) {
            return Refusal.NO_POWER;
        }
        return Refusal.NONE;
    }

    public boolean isBlocked() {
        final Refusal why = checkRun();
        return why == Refusal.NO_WATER || why == Refusal.NO_POWER || why == Refusal.SHED
                || (why == Refusal.DOOR_OPEN && !pending.isEmpty());
    }

    public boolean isStarted() {
        return started;
    }

    public boolean toggleStart() {
        started = !started;
        if (!started) {
            setRunning(false);
        }
        sync();
        return started;
    }

    public void markPressed(int button) {
        if (level == null) {
            return;
        }
        pressedButton = button;
        pressTime = level.getGameTime();

        sync();
    }

    public int pressedButton() {
        if (level == null || pressedButton < 0) {
            return -1;
        }
        final long shown = level.isClientSide() ? clientPressTime : pressTime;
        return shown != Long.MIN_VALUE && level.getGameTime() - shown < PRESS_TICKS
                ? pressedButton : -1;
    }

    public void setOn(boolean value) {
        if (on == value) {
            return;
        }
        on = value;
        if (!on) {
            setRunning(false);
        }
        sync();
    }

    public WashMode cycleMode() {
        setMode(mode.next().ordinal());
        return mode;
    }

    @Override
    public String settingsKey() {
        return "create_stats:bulk_washer";
    }

    @Override
    public void writeSettings(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("Mode", mode.ordinal());
    }

    @Override
    public boolean applySettings(CompoundTag tag, HolderLookup.Provider registries,
                                 @Nullable net.minecraft.world.entity.player.Player player, boolean simulate) {
        if (!tag.contains("Mode")) {
            return false;
        }
        if (!simulate) {
            setMode(tag.getInt("Mode"));
        }
        return true;
    }

    @Override
    public void writeFreshDefaults(CompoundTag tag) {
        tag.putInt("Priority", com.createstats.power.FePriority.DEFAULT.ordinal());
    }

    public boolean canOpenDoor() {
        return !running;
    }

    public void setDoorOpen(boolean value) {
        if (doorOpen == value) {
            return;
        }
        doorOpen = value;
        if (level != null) {
            doorTime = level.getGameTime();
        }

        sync();
    }

    public Refusal checkLoad(ItemStack stack) {
        if (stack.isEmpty()) {
            return Refusal.EMPTY;
        }
        if (!canWash(stack)) {
            return Refusal.NO_RECIPE;
        }
        if (drumRoom() <= 0) {
            return Refusal.FULL;
        }
        return Refusal.NONE;
    }

    public boolean canAccept(ItemStack stack) {
        return checkLoad(stack) == Refusal.NONE;
    }

    public int load(ItemStack stack, int count) {
        if (!canAccept(stack)) {
            return 0;
        }
        final int taken = Math.min(count, drumRoom());
        if (taken <= 0) {
            return 0;
        }
        addTo(pending, stack.copyWithCount(taken));
        sync();
        return taken;
    }

    public List<ItemStack> unloadWashed() {
        if (washed.isEmpty()) {
            return List.of();
        }
        final List<ItemStack> out = new ArrayList<>(washed);
        washed.clear();
        sync();
        return out;
    }

    public List<ItemStack> unloadAll() {
        final List<ItemStack> out = new ArrayList<>(washed.size() + pending.size());
        out.addAll(washed);
        out.addAll(pending);
        washed.clear();
        pending.clear();
        if (!out.isEmpty()) {
            setRunning(false);
            sync();
        }
        return out;
    }

    public int addWater(int amount, boolean execute) {
        final int taken = Math.min(Math.max(0, amount), TANK_CAPACITY - water);
        if (taken > 0 && execute) {
            water += taken;
            sync();
        }
        return taken;
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

        redstoneCut = level.hasNeighborSignal(worldPosition);
        payPower();
        advanceWash();
        flushDrum();
        heartbeat();
    }

    private void payPower() {
        final Refusal why = checkRun();

        final boolean dead = why == Refusal.OFF || why == Refusal.CUT || why == Refusal.SHED;
        final boolean wantsDrum = why == Refusal.NONE;
        final int want = dead ? 0 : (wantsDrum ? mode.drawFe : DRAW_IDLE);

        final Refusal rated = ratedRun();
        ratedDraw = rated == Refusal.OFF || rated == Refusal.CUT || rated == Refusal.SHED ? 0
                : rated == Refusal.NONE || rated == Refusal.NO_POWER ? mode.drawFe : DRAW_IDLE;
        if (want == 0 || energy < want) {
            measuredDraw = 0;
            setRunning(false);
            return;
        }
        energy -= want;
        measuredDraw = want;
        setRunning(wantsDrum);
        setChanged();
    }

    private void setRunning(boolean value) {
        if (running == value) {
            return;
        }
        running = value;
        sync();
    }

    private void advanceWash() {
        if (!running) {
            return;
        }
        progress += mode.batch;
        while (progress >= mode.cycleTicks) {
            if (water < WATER_PER_ITEM || pending.isEmpty()) {
                progress = Math.min(progress, mode.cycleTicks - 1);
                setRunning(false);
                return;
            }
            progress -= mode.cycleTicks;
            washOne();
        }
        setChanged();
    }

    private void washOne() {
        final ItemStack head = pending.get(0);
        final ItemStack one = head.copyWithCount(1);
        head.shrink(1);
        if (head.isEmpty()) {
            pending.remove(0);
        }
        water -= WATER_PER_ITEM;

        final SplashingRecipe recipe = recipeFor(one);
        if (recipe == null) {
            addTo(washed, one);
            syncDrumSoon();
            return;
        }
        for (final ItemStack result : recipe.rollResults(level.getRandom())) {
            if (!result.isEmpty()) {
                addTo(washed, result);
            }
        }
        syncDrumSoon();
    }

    private void syncDrumSoon() {
        drumDirty = true;
        setChanged();
        if (level != null && !level.isClientSide()) {
            refreshComparator();
        }
    }

    private void flushDrum() {
        if (!drumDirty) {
            drumSyncCooldown = 0;
            return;
        }
        if (--drumSyncCooldown > 0) {
            return;
        }
        drumSyncCooldown = DRUM_SYNC_TICKS;
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

    private void sync() {
        setChanged();
        drumDirty = false;
        lastSyncedEnergy = energy;
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
            refreshComparator();
        }
    }

    private void refreshComparator() {
        final int count = drumCount();
        final int now = count <= 0 ? 0
                : Math.max(1, Math.min(15, Math.round(count * 15.0f / DRUM_CAPACITY)));
        if (now != lastComparator) {
            lastComparator = now;
            level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
        }
    }

    private static final float[] DRUM_SPEED = {5.0f, 9.0f, 15.0f};

    private static final float DRUM_EASE = 0.04f;

    public void clientTick() {
        final float target = running ? DRUM_SPEED[mode.ordinal()] : 0.0f;
        drumSpeed += (target - drumSpeed) * DRUM_EASE;
        if (Math.abs(drumSpeed) < 0.02f) {
            drumSpeed = 0.0f;
        }
        drumAngle = (drumAngle + drumSpeed) % 360.0f;
    }

    public float drumAngle(float partialTick) {
        return drumAngle + drumSpeed * partialTick;
    }

    @Override
    public int getMode() {
        return mode.ordinal();
    }

    @Override
    public void setMode(int index) {
        final WashMode[] all = WashMode.values();
        final WashMode next = all[Mth.clamp(index, 0, all.length - 1)];
        if (next == mode) {
            return;
        }
        mode = next;

        sync();
    }

    @Override
    public List<Component> modeLabels() {
        final List<Component> out = new ArrayList<>(WashMode.values().length);
        for (final WashMode m : WashMode.values()) {
            out.add(Component.translatable("mode.create_stats.bulk_washer." + m.key()));
        }
        return out;
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
        return WashMode.FULL.drawFe * 4;
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

    public IFluidHandler fluidHandle() {
        return tankView;
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

    public void dropContents() {
        if (level == null || level.isClientSide()) {
            return;
        }
        for (final ItemStack stack : washed) {
            Block.popResource(level, worldPosition, stack.copy());
        }
        for (final ItemStack stack : pending) {
            Block.popResource(level, worldPosition, stack.copy());
        }
        washed.clear();
        pending.clear();
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
        final Refusal blocking = checkRun();
        tooltip.add(pad(Component.literal("Bulk Washer").withStyle(ChatFormatting.GRAY)));

        step(tooltip, 1, "Mains", on
                        ? String.format(Locale.ROOT, "%,d FE  (%d/t)", energy, ratedDraw)
                        : "switched off",
                on && energy >= mode.drawFe,
                blocking == Refusal.OFF || blocking == Refusal.NO_POWER
                        || blocking == Refusal.CUT || blocking == Refusal.SHED
                        || blocking == Refusal.NOT_STARTED);
        step(tooltip, 2, "Water", String.format(Locale.ROOT, "%,d / %,d mB  (%d items)",
                        water, TANK_CAPACITY, water / WATER_PER_ITEM),
                water >= WATER_PER_ITEM, blocking == Refusal.NO_WATER);
        step(tooltip, 3, "Drum", drumSummary(), !pending.isEmpty(),
                blocking == Refusal.EMPTY || blocking == Refusal.DOOR_OPEN);
        step(tooltip, 4, "Programme", String.format(Locale.ROOT, "%s  (%.1f/s, %d FE each)",
                        mode.name().charAt(0) + mode.name().substring(1).toLowerCase(Locale.ROOT),
                        mode.itemsPerSecond(), mode.fePerItem()),
                true, false);

        tooltip.add(pad(Component.literal(nextAction(blocking)).withStyle(actionColour(blocking))));
        return true;
    }

    private String drumSummary() {
        if (pending.isEmpty() && washed.isEmpty()) {
            return "empty";
        }
        final StringBuilder sb = new StringBuilder();
        sb.append(drumCount()).append(" / ").append(DRUM_CAPACITY);
        if (!pending.isEmpty()) {
            sb.append(", ").append(pendingCount()).append(" waiting");
        }
        if (!washed.isEmpty()) {
            sb.append(", ").append(washedCount()).append(" washed");
        }
        return sb.toString();
    }

    private String nextAction(Refusal blocking) {
        if (running) {
            return "Washing, " + clock(readoutTicks()) + " to go";
        }
        return switch (blocking) {
            case OFF -> "Press the power button";
            case NOT_STARTED -> pending.isEmpty()
                    ? "Load the drum, then press start"
                    : "Press start";
            case CUT -> "Held by a redstone signal";
            case SHED -> "Shed by the Power Distribution Unit";
            case DOOR_OPEN -> pending.isEmpty() ? "Load the drum and shut the door" : "Shut the door";
            case EMPTY -> washed.isEmpty()
                    ? "Load the drum with something washable"
                    : "Take the washed items out";
            case NO_WATER -> "Fill the tank: a bucket, or a pipe into the back";
            case NO_POWER -> "Wire the back terminal and let it charge";
            default -> "Ready.";
        };
    }

    private ChatFormatting actionColour(Refusal blocking) {
        if (running) {
            return ChatFormatting.AQUA;
        }
        if (blocking == Refusal.EMPTY && washed.isEmpty()) {
            return ChatFormatting.DARK_GRAY;
        }
        return blocking == Refusal.NONE ? ChatFormatting.GREEN : ChatFormatting.GOLD;
    }

    public static String clock(int ticks) {
        final int seconds = Math.max(0, ticks) / 20;
        return String.format(Locale.ROOT, "%02d:%02d", Math.min(99, seconds / 60), seconds % 60);
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

    private static ListTag saveSide(List<ItemStack> side, HolderLookup.Provider registries) {
        final ListTag list = new ListTag();
        for (final ItemStack stack : side) {
            if (!stack.isEmpty()) {
                list.add(stack.save(registries));
            }
        }
        return list;
    }

    private static void loadSide(List<ItemStack> side, ListTag list,
                                 HolderLookup.Provider registries) {
        side.clear();
        for (int i = 0; i < list.size(); i++) {
            final ItemStack stack = ItemStack.parseOptional(registries, list.getCompound(i));
            if (!stack.isEmpty()) {
                side.add(stack);
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean("On", on);
        tag.putInt("Mode", mode.ordinal());
        tag.putBoolean("DoorOpen", doorOpen);
        tag.putLong("DoorTime", doorTime);
        tag.putBoolean("Started", started);
        tag.putBoolean("Running", running);
        tag.putInt("PressedButton", pressedButton);
        tag.putLong("PressTime", pressTime);
        tag.putBoolean("RedstoneCut", redstoneCut);
        tag.putInt("Progress", progress);
        tag.putInt("Water", water);
        tag.putInt("Energy", energy);
        tag.putInt("Priority", managed.priorityOrdinal());
        tag.putInt("Lock", managed.lockOrdinal());
        tag.put("Pending", saveSide(pending, registries));
        tag.put("Washed", saveSide(washed, registries));
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
        on = tag.getBoolean("On");
        final WashMode[] modes = WashMode.values();
        mode = modes[Mth.clamp(tag.getInt("Mode"), 0, modes.length - 1)];
        doorOpen = tag.getBoolean("DoorOpen");
        doorTime = tag.contains("DoorTime") ? tag.getLong("DoorTime") : Long.MIN_VALUE;
        started = tag.getBoolean("Started");
        running = tag.getBoolean("Running");
        pressedButton = tag.contains("PressedButton") ? tag.getInt("PressedButton") : -1;
        final long wasPressTime = pressTime;
        pressTime = tag.contains("PressTime") ? tag.getLong("PressTime") : Long.MIN_VALUE;
        // only replay a press stamped within ~1s, chunk load shouldn't replay old ones

        if (level != null && level.isClientSide() && pressTime != wasPressTime
                && pressTime != Long.MIN_VALUE
                && Math.abs(level.getGameTime() - pressTime) <= PRESS_TICKS + 20L) {
            clientPressTime = level.getGameTime();
        }
        redstoneCut = tag.getBoolean("RedstoneCut");
        progress = Math.max(0, tag.getInt("Progress"));
        water = Mth.clamp(tag.getInt("Water"), 0, TANK_CAPACITY);
        energy = Mth.clamp(tag.getInt("Energy"), 0, BUFFER);
        managed.load(tag.getInt("Priority"), tag.getInt("Lock"));
        loadSide(pending, tag.getList("Pending", Tag.TAG_COMPOUND), registries);
        loadSide(washed, tag.getList("Washed", Tag.TAG_COMPOUND), registries);
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
