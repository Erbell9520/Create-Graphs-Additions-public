package com.createstats.blockentity;

import com.createstats.block.AirFryerBlock;
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
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
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
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public class AirFryerBE extends BlockEntity implements FeGridMember, FeManagedLoad, FeWireTerminal,
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
        if (running) {
            final int left = readoutTicks();
            final Component text;
            if (!paused) {
                text = Component.translatable("status.create_stats.home.frying",
                        ApplianceStatus.clock(left));
            } else if (drawerOpen) {
                text = Component.translatable("status.create_stats.home.paused_drawer");
            } else if (managed.isSuppressed()) {
                text = Component.translatable("status.create_stats.home.paused_pdu");
            } else {
                text = Component.translatable("status.create_stats.home.paused_power");
            }

            final SmeltingRecipe recipe = recipeFor(raw);
            final int perItem = recipe == null || level == null ? 1
                    : Math.max(1, recipe.getResultItem(level.registryAccess()).getCount());
            final int total = (remainingItems + cooked.getCount() / perItem) * itemTicks;
            return ApplianceStatus.running(text, total, left,
                    Component.translatable("status.create_stats.home.fryer_done"));
        }
        if (isGated() || isManagedShed()
                || (ratedDrawFePerTick() > 0 && measuredDrawFePerTick() <= 0)) {
            return null;
        }
        if (managed.isSuppressed()) {
            return ApplianceStatus.idle(Component.translatable("status.create_stats.home.held_pdu"));
        }
        if (!cooked.isEmpty()) {
            return ApplianceStatus.idle(Component.translatable("status.create_stats.home.done_count",
                    cooked.getCount()));
        }
        if (drawerOpen) {
            return ApplianceStatus.idle(Component.translatable("status.create_stats.home.drawer_open"));
        }
        if (!raw.isEmpty()) {
            return ApplianceStatus.idle(Component.translatable("status.create_stats.home.loaded"));
        }
        return ApplianceStatus.idle(Component.translatable("status.create_stats.home.empty"));
    }

    public static final int BASKET_CAPACITY = 8;

    public static final float FOOD_SPEED = 0.45f;

    public static final float BULK_SPEED = 1.40f;

    public static final int DRAW_COOK = 64;

    public static final int DRAW_IDLE = 4;

    public static final int BUFFER = 6_000;

    public static final int DRAWER_TICKS = 7;

    public static final int PRESS_TICKS = 4;

    public static final int FINISH_FLASH_TICKS = 60;

    private boolean on;
    private boolean drawerOpen;
    private long drawerTime = Long.MIN_VALUE;

    private ItemStack raw = ItemStack.EMPTY;

    private ItemStack cooked = ItemStack.EMPTY;

    private boolean running;
    private boolean paused;

    private long nextItemAt = -1L;

    private int pausedRemaining;

    private int itemTicks;

    private int remainingItems;

    private long finishedAt = Long.MIN_VALUE;
    private long pressTime = Long.MIN_VALUE;
    private long clientPressTime = Long.MIN_VALUE;

    private boolean lastRedstone;

    private int energy;
    private int ratedDraw;
    private int measuredDraw;
    private int lastSyncedEnergy;
    private int syncCooldown;

    private int lastComparator = -1;

    private final FeManagedState managed = new FeManagedState();
    private final List<BlockPos> links = new ArrayList<>();
    private final Map<Long, WireTier> linkTiers = new HashMap<>();
    @Nullable
    private FeWireNetwork network;

    @Nullable
    private RecipeManager.CachedCheck<SingleRecipeInput, SmeltingRecipe> smeltCheck;

    private boolean soundPlaying;

    private float fanAngle;
    private float fanSpeed;

    public AirFryerBE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public Direction backFace() {
        final BlockState state = getBlockState();
        return state.hasProperty(AirFryerBlock.FACING)
                ? state.getValue(AirFryerBlock.FACING).getOpposite()
                : Direction.SOUTH;
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

    private final class BasketHandler implements IItemHandler {
        private final boolean insert;

        private BasketHandler(boolean insert) {
            this.insert = insert;
        }

        @Override
        public int getSlots() {
            return 1;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return insert ? raw : cooked;
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (!insert || stack.isEmpty() || !isValid(0, stack)) {
                return stack;
            }
            final int room = BASKET_CAPACITY - raw.getCount();
            final int taken = Math.min(room, stack.getCount());
            if (taken <= 0) {
                return stack;
            }
            if (!simulate) {
                if (raw.isEmpty()) {
                    raw = stack.copyWithCount(taken);
                } else {
                    raw.grow(taken);
                }
                sync();
            }
            final int left = stack.getCount() - taken;
            return left <= 0 ? ItemStack.EMPTY : stack.copyWithCount(left);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (insert || cooked.isEmpty() || amount <= 0) {
                return ItemStack.EMPTY;
            }
            final int taken = Math.min(amount, cooked.getCount());
            final ItemStack out = cooked.copyWithCount(taken);
            if (!simulate) {
                cooked.shrink(taken);
                if (cooked.isEmpty()) {
                    cooked = ItemStack.EMPTY;
                }
                sync();
            }
            return out;
        }

        @Override
        public int getSlotLimit(int slot) {
            return BASKET_CAPACITY;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return insert && isValid(0, stack);
        }

        private boolean isValid(int slot, ItemStack stack) {
            return canAccept(stack);
        }
    }

    private final IItemHandler inputHandle = new BasketHandler(true);
    private final IItemHandler outputHandle = new BasketHandler(false);

    public static void registerCapabilities(net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK,
                com.createstats.registry.ModBlockEntities.AIR_FRYER.get(),
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

    private RecipeManager.CachedCheck<SingleRecipeInput, SmeltingRecipe> smeltCheck() {
        if (smeltCheck == null) {
            smeltCheck = RecipeManager.createCheck(RecipeType.SMELTING);
        }
        return smeltCheck;
    }

    @Nullable
    private SmeltingRecipe recipeFor(ItemStack stack) {
        if (level == null || stack.isEmpty()) {
            return null;
        }
        final Optional<RecipeHolder<SmeltingRecipe>> found =
                smeltCheck().getRecipeFor(new SingleRecipeInput(stack), level);
        return found.map(RecipeHolder::value).orElse(null);
    }

    public boolean canCook(ItemStack stack) {
        return recipeFor(stack) != null;
    }

    public int cookTicksFor(ItemStack stack) {
        final SmeltingRecipe recipe = recipeFor(stack);
        if (recipe == null || level == null) {
            return -1;
        }
        return cookTicksOf(recipe, recipe.getResultItem(level.registryAccess()));
    }

    public static int cookTicksOf(SmeltingRecipe recipe, ItemStack result) {
        return Math.max(1, Math.round(recipe.getCookingTime() * (isFood(result) ? FOOD_SPEED : BULK_SPEED)));
    }

    public static boolean isFood(ItemStack result) {
        return result.has(DataComponents.FOOD);
    }

    public boolean isOn() {
        return on;
    }

    public boolean isDrawerOpen() {
        return drawerOpen;
    }

    public boolean isRunning() {
        return running;
    }

    public boolean isPaused() {
        return running && paused;
    }

    public boolean isCooking() {
        return running && !paused;
    }

    public ItemStack rawStack() {
        return raw;
    }

    public ItemStack cookedStack() {
        return cooked;
    }

    public boolean basketEmpty() {
        return raw.isEmpty() && cooked.isEmpty();
    }

    public int storedEnergyFe() {
        return energy;
    }

    public int bufferSize() {
        return BUFFER;
    }

    public float drawerSlide(float partialTick) {
        if (level == null || drawerTime == Long.MIN_VALUE) {
            return drawerOpen ? 1.0f : 0.0f;
        }
        final float elapsed = level.getGameTime() - drawerTime + partialTick;
        final float t = Mth.clamp(elapsed / DRAWER_TICKS, 0.0f, 1.0f);

        final float eased = t * t * (3.0f - 2.0f * t);
        return drawerOpen ? eased : 1.0f - eased;
    }

    public boolean isPressed() {
        if (level == null) {
            return false;
        }
        final long shownPressTime = level.isClientSide() ? clientPressTime : pressTime;
        return shownPressTime != Long.MIN_VALUE
                && level.getGameTime() - shownPressTime < PRESS_TICKS;
    }

    public boolean isFlashingDone() {
        return level != null && finishedAt != Long.MIN_VALUE
                && level.getGameTime() - finishedAt < FINISH_FLASH_TICKS;
    }

    public int readoutTicks() {
        if (!on) {
            return -1;
        }
        if (running) {
            final int current = paused
                    ? pausedRemaining
                    : (int) Math.max(0L, nextItemAt - (level == null ? 0L : level.getGameTime()));
            return current + Math.max(0, remainingItems - 1) * itemTicks;
        }
        if (isFlashingDone()) {
            return 0;
        }
        if (raw.isEmpty()) {
            return 0;
        }
        final int per = cookTicksFor(raw);
        return per < 0 ? 0 : per * raw.getCount();
    }

    public boolean colonLit() {
        if (level == null) {
            return true;
        }
        if (isFlashingDone()) {
            return ((level.getGameTime() / 5L) & 1L) == 0L;
        }
        if (!isPaused()) {
            return true;
        }
        return ((level.getGameTime() / 10L) & 1L) == 0L;
    }

    public enum Refusal {
        NONE, OFF, EMPTY, NO_RECIPE, NO_POWER, BASKET_BUSY, WRONG_ITEM, FULL
    }

    public void setOn(boolean value) {
        if (on == value) {
            return;
        }
        on = value;
        if (!on) {
            running = false;
            paused = false;
            remainingItems = 0;
        }
        sync();
    }

    public void setDrawerOpen(boolean value) {
        if (drawerOpen == value) {
            return;
        }
        drawerOpen = value;
        if (level != null) {
            drawerTime = level.getGameTime();
        }

        sync();
    }

    public void markPressed() {
        if (level != null) {
            pressTime = level.getGameTime();
            // sync or nobody sees the button move

            sync();
        }
    }

    public Refusal checkStartable() {
        if (level == null || !on) {
            return Refusal.OFF;
        }
        if (raw.isEmpty()) {
            return Refusal.EMPTY;
        }
        if (cookTicksFor(raw) < 0) {
            return Refusal.NO_RECIPE;
        }
        if (energy < DRAW_COOK) {
            return Refusal.NO_POWER;
        }
        return Refusal.NONE;
    }

    public Refusal start() {
        final Refusal refusal = checkStartable();
        if (refusal != Refusal.NONE) {
            return refusal;
        }
        if (drawerOpen) {
            setDrawerOpen(false);
        }
        itemTicks = cookTicksFor(raw);
        remainingItems = raw.getCount();
        nextItemAt = level.getGameTime() + itemTicks;
        running = true;
        paused = false;
        finishedAt = Long.MIN_VALUE;
        sync();
        return Refusal.NONE;
    }

    public void stop() {
        if (!running) {
            return;
        }
        running = false;
        paused = false;
        remainingItems = 0;
        sync();
    }

    public Refusal checkLoad(ItemStack stack) {
        if (stack.isEmpty()) {
            return Refusal.EMPTY;
        }
        if (!canCook(stack)) {
            return Refusal.NO_RECIPE;
        }
        if (!cooked.isEmpty()) {
            return Refusal.BASKET_BUSY;
        }
        if (!raw.isEmpty() && !ItemStack.isSameItemSameComponents(raw, stack)) {
            return Refusal.WRONG_ITEM;
        }
        if (raw.getCount() >= BASKET_CAPACITY) {
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
        final int taken = Math.min(count, BASKET_CAPACITY - raw.getCount());
        if (taken <= 0) {
            return 0;
        }
        if (raw.isEmpty()) {
            raw = stack.copyWithCount(taken);
        } else {
            raw.grow(taken);
        }
        sync();
        return taken;
    }

    public List<ItemStack> unload() {
        final List<ItemStack> out = new ArrayList<>(2);
        if (!cooked.isEmpty()) {
            out.add(cooked);
            cooked = ItemStack.EMPTY;
        }
        if (!raw.isEmpty()) {
            out.add(raw);
            raw = ItemStack.EMPTY;
        }
        if (!out.isEmpty()) {
            running = false;
            paused = false;
            remainingItems = 0;
            sync();
        }
        return out;
    }

    public List<ItemStack> unloadCooked() {
        if (cooked.isEmpty()) {
            return List.of();
        }
        final List<ItemStack> out = new ArrayList<>(1);
        out.add(cooked);
        cooked = ItemStack.EMPTY;
        sync();
        return out;
    }

    public void adoptRedstone(boolean powered) {
        lastRedstone = powered;
        setChanged();
    }

    public void onRedstone(boolean powered) {
        if (powered == lastRedstone) {
            return;
        }
        lastRedstone = powered;
        setChanged();
        if (powered && !running) {
            start();
        }
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
        advanceCook();
        heartbeat();
    }

    private void payPower() {
        final boolean dead = !on || managed.isSuppressed();
        final boolean wantsHeat = running && !drawerOpen && !dead;
        final int want = dead ? 0 : (wantsHeat ? DRAW_COOK : DRAW_IDLE);

        final boolean ratedDead = !on || managed.isForcedOff();
        ratedDraw = ratedDead ? 0
                : (running && !drawerOpen ? DRAW_COOK : DRAW_IDLE);
        if (want == 0 || energy < want) {
            measuredDraw = 0;
            setPaused(running);
            return;
        }
        energy -= want;
        measuredDraw = want;
        setPaused(running && drawerOpen);
        setChanged();
    }

    private void setPaused(boolean value) {
        if (paused == value || level == null) {
            return;
        }
        if (value) {
            pausedRemaining = (int) Mth.clamp(nextItemAt - level.getGameTime(), 0L, Integer.MAX_VALUE);
        } else {
            nextItemAt = level.getGameTime() + pausedRemaining;
        }
        paused = value;
        sync();
    }

    private void advanceCook() {
        if (!running || paused || level.getGameTime() < nextItemAt) {
            return;
        }
        final SmeltingRecipe recipe = recipeFor(raw);
        if (recipe == null) {
            stop();
            return;
        }
        final ItemStack result = recipe.getResultItem(level.registryAccess());
        raw.shrink(1);
        if (raw.isEmpty()) {
            raw = ItemStack.EMPTY;
        }
        if (cooked.isEmpty()) {
            cooked = result.copy();
        } else if (ItemStack.isSameItemSameComponents(cooked, result)) {
            cooked.grow(result.getCount());
        } else {
            Block.popResource(level, worldPosition.above(), result.copy());
        }
        remainingItems--;
        if (remainingItems <= 0 || raw.isEmpty()) {
            running = false;
            remainingItems = 0;
            finishedAt = level.getGameTime();
            AirFryerBlock.playDing(level, worldPosition);
        } else {
            nextItemAt = level.getGameTime() + itemTicks;
        }
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
        lastSyncedEnergy = energy;
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
            refreshComparator();
        }
    }

    // mc caches analog output, tell it. deadbanded, most syncs don't move the reading
    private void refreshComparator() {
        final int now = cooked.isEmpty() ? 0 : Math.max(1, Math.min(15,
                Math.round(cooked.getCount() * 15.0f / BASKET_CAPACITY)));
        if (now != lastComparator) {
            lastComparator = now;
            level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
        }
    }

    private static final float FAN_SPEED = 24.0f;

    private static final float FAN_EASE = 0.055f;

    public void clientTick() {
        final float target = isCooking() ? FAN_SPEED : 0.0f;
        fanSpeed += (target - fanSpeed) * FAN_EASE;
        if (Math.abs(fanSpeed) < 0.05f) {
            fanSpeed = 0.0f;
        }
        fanAngle = (fanAngle + fanSpeed) % 360.0f;
        if (isCooking()) {
            com.createstats.client.AirFryerSoundInstance.startIfNeeded(this);
        }
    }

    public float fanAngle(float partialTick) {
        return fanAngle + fanSpeed * partialTick;
    }

    public boolean isSoundPlaying() {
        return soundPlaying;
    }

    public void setSoundPlaying(boolean value) {
        soundPlaying = value;
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
        return DRAW_COOK * 4;
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
        for (ItemStack stack : List.of(raw, cooked)) {
            if (!stack.isEmpty()) {
                Block.popResource(level, worldPosition, stack.copy());
            }
        }
        raw = ItemStack.EMPTY;
        cooked = ItemStack.EMPTY;
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
        final Refusal blocking = checkStartable();
        tooltip.add(pad(Component.literal("Air Fryer").withStyle(ChatFormatting.GRAY)));

        step(tooltip, 1, "Mains", on
                        ? String.format(Locale.ROOT, "%,d FE  (%d/t)", energy, ratedDraw)
                        : "switched off",
                on && energy >= DRAW_COOK,
                blocking == Refusal.OFF || blocking == Refusal.NO_POWER);
        step(tooltip, 2, "Drawer", drawerOpen ? "open" : "shut", !drawerOpen, false);
        step(tooltip, 3, "Basket", basketSummary(), !raw.isEmpty(),
                blocking == Refusal.EMPTY || blocking == Refusal.NO_RECIPE);

        tooltip.add(pad(Component.literal(nextAction(blocking)).withStyle(actionColour(blocking))));
        return true;
    }

    private String basketSummary() {
        if (basketEmpty()) {
            return "empty";
        }
        final StringBuilder sb = new StringBuilder();
        if (!raw.isEmpty()) {
            sb.append(raw.getCount()).append(" x ").append(raw.getHoverName().getString());
            final int per = cookTicksFor(raw);
            if (per > 0) {
                sb.append("  (").append(clock(per * raw.getCount())).append(')');
            }
        }
        if (!cooked.isEmpty()) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(cooked.getCount()).append(" x ").append(cooked.getHoverName().getString())
                    .append(" done");
        }
        return sb.toString();
    }

    private String nextAction(Refusal blocking) {
        if (running) {
            final String time = clock(readoutTicks());
            if (paused) {
                return drawerOpen
                        ? "Paused at " + time + " -- shut the drawer"
                        : "Paused at " + time + " -- not enough power";
            }
            return "Cooking, " + time + " to go";
        }
        if (isFlashingDone()) {
            return "Done. Open the drawer.";
        }
        return switch (blocking) {
            case OFF -> "Flip the switch on the top panel";
            case EMPTY -> cooked.isEmpty()
                    ? "Open the drawer and put food in the basket"
                    : "Take the cooked food out";
            case NO_RECIPE -> "Nothing in the basket can be cooked";
            case NO_POWER -> "Wire the back terminal and let it charge";
            default -> "Ready. Press start.";
        };
    }

    private ChatFormatting actionColour(Refusal blocking) {
        if (running) {
            return paused ? ChatFormatting.GOLD : ChatFormatting.AQUA;
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

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean("On", on);
        tag.putBoolean("DrawerOpen", drawerOpen);
        tag.putLong("DrawerTime", drawerTime);
        tag.putBoolean("Running", running);
        tag.putBoolean("Paused", paused);
        tag.putLong("NextItemAt", nextItemAt);
        tag.putInt("PausedRemaining", pausedRemaining);
        tag.putInt("ItemTicks", itemTicks);
        tag.putInt("RemainingItems", remainingItems);
        tag.putLong("FinishedAt", finishedAt);
        tag.putLong("PressTime", pressTime);
        tag.putBoolean("LastRedstone", lastRedstone);
        tag.putInt("Energy", energy);
        tag.putInt("Priority", managed.priorityOrdinal());
        tag.putInt("Lock", managed.lockOrdinal());
        if (!raw.isEmpty()) {
            tag.put("Raw", raw.save(registries));
        }
        if (!cooked.isEmpty()) {
            tag.put("Cooked", cooked.save(registries));
        }
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
        drawerOpen = tag.getBoolean("DrawerOpen");
        drawerTime = tag.contains("DrawerTime") ? tag.getLong("DrawerTime") : Long.MIN_VALUE;
        running = tag.getBoolean("Running");
        paused = tag.getBoolean("Paused");
        nextItemAt = tag.contains("NextItemAt") ? tag.getLong("NextItemAt") : -1L;
        pausedRemaining = tag.getInt("PausedRemaining");
        itemTicks = Math.max(1, tag.getInt("ItemTicks"));
        remainingItems = tag.getInt("RemainingItems");
        finishedAt = tag.contains("FinishedAt") ? tag.getLong("FinishedAt") : Long.MIN_VALUE;
        final long oldPressTime = pressTime;
        pressTime = tag.contains("PressTime") ? tag.getLong("PressTime") : Long.MIN_VALUE;
        if (level != null && level.isClientSide() && pressTime != oldPressTime
                && pressTime != Long.MIN_VALUE
                && Math.abs(level.getGameTime() - pressTime) <= PRESS_TICKS + 20L) {
            clientPressTime = level.getGameTime();
        }
        lastRedstone = tag.getBoolean("LastRedstone");
        energy = tag.getInt("Energy");
        managed.load(tag.getInt("Priority"), tag.getInt("Lock"));
        raw = tag.contains("Raw")
                ? ItemStack.parseOptional(registries, tag.getCompound("Raw")) : ItemStack.EMPTY;
        cooked = tag.contains("Cooked")
                ? ItemStack.parseOptional(registries, tag.getCompound("Cooked")) : ItemStack.EMPTY;
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
