package com.createstats.blockentity;

import com.createstats.block.InductionSmelterBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.BlastingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class InductionSmelterBE extends BlockEntity
        implements com.createstats.power.FeGridMember,
        com.createstats.power.FeManagedLoad,
        com.createstats.power.Gateable,
        com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation,
        net.minecraft.world.MenuProvider {
    public static final int SLOT_IN = 0;

    public static final int SLOT_OUT = 1;
    public static final int SLOT_COUNT = 2;

    public static final float SPEED = 0.85f;

    public static final int DRAW_WORK = 64;

    public static final int DRAW_IDLE = 12;

    public static final int HEAT_MAX = 600;

    public static final int BUFFER = 8_000;

    public static final int MIN_WORK_HEAT = HEAT_MAX / 4;

    public static final int BLAST_HEAT = HEAT_MAX * 4 / 5;

    public static final int IDLE_HOLD_TICKS = (HEAT_MAX * DRAW_WORK) / DRAW_IDLE;

    public static final int[] DAMPER_PERCENT = {0, 70, 100};
    public static final int DAMPER_SHUT = 0;
    public static final int DAMPER_HALF = 1;
    public static final int DAMPER_OPEN = 2;

    private static final int MAX_BANK = 256;

    public static int idleDrawFor(int cap) {
        return Math.max(1, Math.round(DRAW_IDLE * cap / (float) HEAT_MAX));
    }

    private int energy;
    private int intakeAccumulator;
    private int measuredIntake;
    private int measuredDraw;
    private int ratedDraw;

    private final com.createstats.power.FeManagedState managed =
            new com.createstats.power.FeManagedState();

    private int damper = DAMPER_OPEN;

    private int heat;

    private int idleTicks = IDLE_HOLD_TICKS;

    private final NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);

    private float progress;

    // lock the lane at batch start, heat moves mid batch and the finish line moved under it
    private boolean laneBlasting;

    private boolean working;
    private boolean starved;
    private boolean outputBlocked;
    private boolean redstoneCut;

    private int cachedDuration;

    public InductionSmelterBE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    private static final class BankGroup {
        private final List<InductionSmelterBE> members;
        private final long builtTick;

        BankGroup(List<InductionSmelterBE> members, long builtTick) {
            this.members = members;
            this.builtTick = builtTick;
        }
    }

    private BankGroup group;

    private List<InductionSmelterBE> group() {
        final long now = level != null ? level.getGameTime() : 0L;
        if (group != null && group.builtTick == now && !group.members.isEmpty()) {
            return group.members;
        }
        return rebuildGroup(now);
    }

    private List<InductionSmelterBE> rebuildGroup(long now) {
        final List<InductionSmelterBE> found = new ArrayList<>();
        found.add(this);
        if (level != null) {
            final Set<BlockPos> seen = new HashSet<>();
            final Deque<InductionSmelterBE> queue = new ArrayDeque<>();
            seen.add(worldPosition.immutable());
            queue.add(this);
            while (!queue.isEmpty() && found.size() < MAX_BANK) {
                final InductionSmelterBE here = queue.poll();
                for (Direction direction : Direction.values()) {
                    final BlockPos neighbour = here.getBlockPos().relative(direction).immutable();
                    if (seen.contains(neighbour) || !level.isLoaded(neighbour)) {
                        continue;
                    }
                    seen.add(neighbour);
                    if (level.getBlockEntity(neighbour) instanceof InductionSmelterBE other
                            && !other.isRemoved()) {
                        found.add(other);
                        queue.add(other);
                        if (found.size() >= MAX_BANK) {
                            break;
                        }
                    }
                }
            }
        }
        found.sort(Comparator.comparingLong(be -> be.getBlockPos().asLong()));
        final BankGroup shared = new BankGroup(found, now);
        for (InductionSmelterBE member : found) {
            member.group = shared;
        }
        return found;
    }

    public int bankSize() {
        return group().size();
    }

    @org.jetbrains.annotations.Nullable
    private com.createstats.power.FeWireNetwork network;

    private final IEnergyStorage energyView = new IEnergyStorage() {
        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            final int room = Math.min(maxReceive, BUFFER - energy);
            if (room <= 0) {
                return 0;
            }
            if (!simulate) {
                energy += room;
                intakeAccumulator += room;
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

    public IEnergyStorage energyHandle() {
        return energyView;
    }

    public Direction backFace() {
        return getBlockState().getValue(HorizontalDirectionalBlock.FACING).getOpposite();
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
        return 1024;
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

    public void adoptNetwork(com.createstats.power.FeWireNetwork network) {
        this.network = network;
    }

    @Override
    @javax.annotation.Nullable
    public com.createstats.power.FeWireNetwork network() {
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
    public int ratedDrawFePerTick() {
        return ratedDraw;
    }

    @Override
    public int measuredDrawFePerTick() {
        return measuredDraw;
    }

    @Override
    public com.createstats.power.FePriority managedPriority() {
        return managed.priority();
    }

    @Override
    public void setManagedPriority(com.createstats.power.FePriority tier) {
        managed.setPriority(tier);
        setChanged();
    }

    @Override
    public com.createstats.power.FeLock managedLock() {
        return managed.lock();
    }

    @Override
    public void setManagedLock(com.createstats.power.FeLock lock) {
        managed.setLock(lock);
        setChanged();
        refresh();
    }

    @Override
    public void setManagedShed(boolean shed) {
        if (managed.isShed() == shed) {
            return;
        }
        managed.setShed(shed);
        refresh();
    }

    @Override
    public boolean isManagedShed() {
        return managed.isShed();
    }

    public int damper() {
        return damper;
    }

    public int heatCap() {
        return HEAT_MAX * DAMPER_PERCENT[damper] / 100;
    }

    public int cycleDamper() {
        return setDamperTo((damper + 1) % DAMPER_PERCENT.length);
    }

    public boolean setDamper(int wanted) {
        final int clamped = Math.max(0, Math.min(DAMPER_PERCENT.length - 1, wanted));
        if (clamped == damper) {
            return false;
        }
        setDamperTo(clamped);
        return true;
    }

    private int setDamperTo(int detent) {
        damper = detent;
        setChanged();
        refresh();
        sync();
        return damper;
    }

    public int heat() {
        return heat;
    }

    public float heatFraction() {
        return heat / (float) HEAT_MAX;
    }

    public boolean isHot() {
        return heat >= MIN_WORK_HEAT;
    }

    public boolean isWorking() {
        return working;
    }

    public boolean isStarved() {
        return starved;
    }

    public boolean isBlasting() {
        return progress > 0 ? laneBlasting : heat >= BLAST_HEAT;
    }

    public int energyStored() {
        return energy;
    }

    public ItemStack item(int slot) {
        return items.get(slot);
    }

    public float batchFraction() {
        return cachedDuration <= 0 ? 0.0f : Math.min(1.0f, progress / cachedDuration);
    }

    private RecipeManager.CachedCheck<SingleRecipeInput, SmeltingRecipe> smeltCheck;
    private RecipeManager.CachedCheck<SingleRecipeInput, BlastingRecipe> blastCheck;

    private RecipeManager.CachedCheck<SingleRecipeInput, SmeltingRecipe> smeltCheck() {
        if (smeltCheck == null) {
            smeltCheck = RecipeManager.createCheck(RecipeType.SMELTING);
        }
        return smeltCheck;
    }

    private RecipeManager.CachedCheck<SingleRecipeInput, BlastingRecipe> blastCheck() {
        if (blastCheck == null) {
            blastCheck = RecipeManager.createCheck(RecipeType.BLASTING);
        }
        return blastCheck;
    }

    // vanilla smelting/blasting directly, a custom type would buy nothing and cost a JEI category
    @org.jetbrains.annotations.Nullable
    public AbstractCookingRecipe currentRecipe() {
        if (level == null) {
            return null;
        }
        final ItemStack input = items.get(SLOT_IN);
        if (input.isEmpty()) {
            return null;
        }
        final SingleRecipeInput in = new SingleRecipeInput(input);
        if (isBlasting()) {
            final Optional<RecipeHolder<BlastingRecipe>> blast = blastCheck().getRecipeFor(in, level);
            if (blast.isPresent()) {
                return blast.get().value();
            }
            if (progress > 0.0f) {
                return null;
            }
        }
        final Optional<RecipeHolder<SmeltingRecipe>> smelt = smeltCheck().getRecipeFor(in, level);
        return smelt.map(RecipeHolder::value).orElse(null);
    }

    public static int durationOf(AbstractCookingRecipe recipe) {
        return Math.max(1, Math.round(recipe.getCookingTime() * SPEED));
    }

    private boolean needsBlastingLane() {
        if (level == null) {
            return false;
        }
        final ItemStack input = items.get(SLOT_IN);
        if (input.isEmpty() || heatCap() >= BLAST_HEAT) {
            return false;
        }
        final SingleRecipeInput in = new SingleRecipeInput(input);
        return blastCheck().getRecipeFor(in, level).isPresent()
                && smeltCheck().getRecipeFor(in, level).isEmpty();
    }

    public boolean isProcessable(ItemStack stack) {
        if (level == null || stack.isEmpty()) {
            return false;
        }
        final SingleRecipeInput in = new SingleRecipeInput(stack);
        return smeltCheck().getRecipeFor(in, level).isPresent()
                || blastCheck().getRecipeFor(in, level).isPresent();
    }

    private final IItemHandler itemHandle = new IItemHandler() {
        @Override
        public int getSlots() {
            return SLOT_COUNT;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return items.get(slot);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (slot != SLOT_IN || stack.isEmpty() || !isProcessable(stack)) {
                return stack;
            }
            ItemStack left = offer(InductionSmelterBE.this, stack, simulate);
            if (left.isEmpty()) {
                return left;
            }
            for (InductionSmelterBE other : group()) {
                if (other == InductionSmelterBE.this) {
                    continue;
                }
                left = offer(other, left, simulate);
                if (left.isEmpty()) {
                    break;
                }
            }
            return left;
        }

        private ItemStack offer(InductionSmelterBE target, ItemStack stack, boolean simulate) {
            final ItemStack held = target.items.get(SLOT_IN);
            if (!held.isEmpty() && !ItemStack.isSameItemSameComponents(held, stack)) {
                return stack;
            }
            final int room = Math.min(stack.getMaxStackSize(), 64) - held.getCount();
            final int taken = Math.min(room, stack.getCount());
            if (taken <= 0) {
                return stack;
            }
            if (!simulate) {
                if (held.isEmpty()) {
                    target.items.set(SLOT_IN, stack.copyWithCount(taken));
                } else {
                    held.grow(taken);
                }
                target.setChanged();
                target.sync();
            }
            return stack.copyWithCount(stack.getCount() - taken);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (slot != SLOT_OUT || amount <= 0) {
                return ItemStack.EMPTY;
            }
            final ItemStack mine = take(InductionSmelterBE.this, amount, simulate);
            if (!mine.isEmpty()) {
                return mine;
            }
            for (InductionSmelterBE other : group()) {
                if (other == InductionSmelterBE.this) {
                    continue;
                }
                final ItemStack theirs = take(other, amount, simulate);
                if (!theirs.isEmpty()) {
                    return theirs;
                }
            }
            return ItemStack.EMPTY;
        }

        private ItemStack take(InductionSmelterBE target, int amount, boolean simulate) {
            final ItemStack held = target.items.get(SLOT_OUT);
            if (held.isEmpty()) {
                return ItemStack.EMPTY;
            }
            final int taken = Math.min(amount, held.getCount());
            final ItemStack out = held.copyWithCount(taken);
            if (!simulate) {
                held.shrink(taken);
                if (held.isEmpty()) {
                    target.items.set(SLOT_OUT, ItemStack.EMPTY);
                }
                target.setChanged();
                target.sync();
            }
            return out;
        }

        @Override
        public int getSlotLimit(int slot) {
            return 64;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return slot == SLOT_IN && isProcessable(stack);
        }
    };

    public IItemHandler itemHandle() {
        return itemHandle;
    }

    public static void registerCapabilities(
            net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK,
                com.createstats.registry.ModBlockEntities.INDUCTION_SMELTER.get(),
                (be, side) -> be.itemHandle());
    }

    public int offerCharge(ItemStack offered) {
        final ItemStack left = itemHandle.insertItem(SLOT_IN, offered, false);
        return offered.getCount() - left.getCount();
    }

    public ItemStack collectProduct() {
        final ItemStack out = items.get(SLOT_OUT);
        if (out.isEmpty()) {
            return ItemStack.EMPTY;
        }
        items.set(SLOT_OUT, ItemStack.EMPTY);
        setChanged();
        sync();
        return out;
    }

    public void dropContents() {
        if (level == null) {
            return;
        }
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) {
                Block.popResource(level, worldPosition, stack);
            }
        }
        items.clear();
    }

    public void tick() {
        if (level == null || level.isClientSide()) {
            return;
        }
        if (network == null || network.isDirty()) {
            com.createstats.power.FeWireNetwork.buildFrom(this);
        }
        if (network != null) {
            network.solveOnce(level);
        }

        if (network == null || !network.isPduManaged(level.getGameTime())) {
            setManagedShed(false);
        }

        redstoneCut = level.hasNeighborSignal(worldPosition);

        final int cap = heatCap();

        final boolean allowed = !redstoneCut && !gated && !managed.isForcedOff() && cap > 0;
        final boolean live = allowed && !managed.isSuppressed();

        final AbstractCookingRecipe recipe = currentRecipe();
        if (recipe != null) {
            cachedDuration = durationOf(recipe);
            final ItemStack result = recipe.getResultItem(level.registryAccess());
            outputBlocked = roomFor(SLOT_OUT, result) < result.getCount();
        } else {
            cachedDuration = 0;
            outputBlocked = false;
        }

        if (progress <= 0.0f) {
            laneBlasting = recipe instanceof BlastingRecipe;
        }

        final boolean hasJob = live && recipe != null && !outputBlocked;
        idleTicks = hasJob ? 0 : Math.min(IDLE_HOLD_TICKS, idleTicks + 1);

        final boolean wantsHeat = live && (hasJob || idleTicks < IDLE_HOLD_TICKS);
        final boolean warming = wantsHeat && heat < cap;

        final int want;
        if (hasJob || warming) {
            want = DRAW_WORK;
        } else if (wantsHeat) {
            want = idleDrawFor(cap);
        } else {
            want = 0;
        }

        final boolean ratedJob = allowed && recipe != null && !outputBlocked;
        final boolean ratedHeat = allowed && (ratedJob || idleTicks < IDLE_HOLD_TICKS);
        if (ratedJob || (ratedHeat && heat < cap)) {
            ratedDraw = DRAW_WORK;
        } else if (ratedHeat) {
            ratedDraw = idleDrawFor(cap);
        } else {
            ratedDraw = 0;
        }

        final boolean paid = want > 0 && energy >= want;
        if (paid) {
            energy -= want;
            measuredDraw = want;
            setChanged();
        } else {
            measuredDraw = 0;
        }

        if (paid && warming) {
            heat = Math.min(cap, heat + 1);
        } else if (!paid || !wantsHeat || heat > cap) {
            heat = Math.max(0, heat - 1);
        }

        final boolean worked = paid && hasJob && heat >= MIN_WORK_HEAT;
        if (worked) {
            progress += heatFraction();
        }

        measuredIntake = intakeAccumulator;
        intakeAccumulator = 0;

        if (recipe != null && progress >= cachedDuration) {
            complete(recipe);
        } else if (recipe == null && progress > 0.0f) {
            progress = 0.0f;
        }

        final boolean nowStarved = want > 0 && (!paid || measuredIntake < want);
        if (worked != working || nowStarved != starved) {
            working = worked;
            starved = nowStarved;
            refresh();
        }
        syncTelemetryIfDue();
    }

    private void complete(AbstractCookingRecipe recipe) {
        final ItemStack input = items.get(SLOT_IN);
        final ItemStack product = recipe.assemble(new SingleRecipeInput(input), level.registryAccess());
        if (roomFor(SLOT_OUT, product) < product.getCount()) {
            return;
        }
        input.shrink(1);
        if (input.isEmpty()) {
            items.set(SLOT_IN, ItemStack.EMPTY);
        }
        final ItemStack held = items.get(SLOT_OUT);
        if (held.isEmpty()) {
            items.set(SLOT_OUT, product);
        } else {
            held.grow(product.getCount());
        }
        progress = 0.0f;
        setChanged();
        sync();
    }

    private int roomFor(int slot, ItemStack stack) {
        final ItemStack held = items.get(slot);
        final int cap = Math.min(64, stack.getMaxStackSize());
        if (held.isEmpty()) {
            return cap;
        }
        return ItemStack.isSameItemSameComponents(held, stack) ? cap - held.getCount() : 0;
    }

    private void refresh() {
        if (level == null || level.isClientSide()
                || !(getBlockState().getBlock() instanceof InductionSmelterBlock)) {
            return;
        }
        final BlockState state = getBlockState();
        if (state.getValue(InductionSmelterBlock.HOT) == isHot()
                && state.getValue(InductionSmelterBlock.WORKING) == working) {
            sync();
            return;
        }
        level.setBlock(worldPosition, state
                .setValue(InductionSmelterBlock.HOT, isHot())
                .setValue(InductionSmelterBlock.WORKING, working), Block.UPDATE_ALL);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        invalidateNetwork();

        if (group != null) {
            for (InductionSmelterBE member : group.members) {
                member.group = null;
            }
            group = null;
        }
    }

    private void sync() {
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    private int lastSyncedTelemetry = Integer.MIN_VALUE;
    private int ticksSinceTelemetrySync;

    private void syncTelemetryIfDue() {
        ticksSinceTelemetrySync++;
        final int shown = heat * 31 + Math.round(progress) * 7 + measuredDraw;
        final int bucket = Math.max(1, Math.abs(shown) / 24);
        if (Math.abs(shown - lastSyncedTelemetry) < bucket && ticksSinceTelemetrySync < 20) {
            return;
        }
        lastSyncedTelemetry = shown;
        ticksSinceTelemetrySync = 0;
        sync();
    }

    public void touched() {
        if (level != null && !level.isClientSide() && progress > 0.0f && currentRecipe() == null) {
            progress = 0.0f;
        }
        setChanged();
        sync();
    }

    private float shutter;
    private float shutterPrev;
    private float needle;
    private float needlePrev;
    private float glow;
    private float glowPrev;
    private float travel;
    private float travelPrev;
    private float fanAngle;
    private float fanAnglePrev;
    private float fanSpeed;
    private int clientAge;

    public void clientTick() {
        shutterPrev = shutter;
        needlePrev = needle;
        glowPrev = glow;
        travelPrev = travel;
        fanAnglePrev = fanAngle;
        clientAge++;

        final BlockState state = getBlockState();
        if (!(state.getBlock() instanceof InductionSmelterBlock)) {
            return;
        }
        final boolean hot = state.getValue(InductionSmelterBlock.HOT);
        final boolean busy = state.getValue(InductionSmelterBlock.WORKING);

        shutter = approach(shutter, busy || !items.get(SLOT_OUT).isEmpty() ? 1.0f : 0.0f,
                busy ? 0.14f : 0.07f);

        needle = approach(needle, heatFraction(), 0.012f);

        final float wanted = starved ? 0.35f : (busy ? 1.0f : (hot ? 0.45f : 0.0f));
        glow = approach(glow, wanted, starved ? 0.22f : 0.06f);

        final float target = batchFraction();
        travel = busy ? Math.min(1.0f, Math.max(travel, target) + heatFraction() / Math.max(1.0f, cachedDuration))
                : target;
        if (Math.abs(travel - target) > 0.25f) {
            travel = target;
        }

        fanSpeed = approach(fanSpeed, hot ? (1.5f + 9.0f * heatFraction()) : 0.0f, 0.15f);
        fanAngle += fanSpeed;
        if (fanAngle > 3600.0f) {
            fanAngle -= 3600.0f;
            fanAnglePrev -= 3600.0f;
        }
    }

    private static float approach(float value, float target, float rate) {
        final float delta = target - value;
        if (Math.abs(delta) <= rate) {
            return target;
        }
        return value + Math.signum(delta) * rate;
    }

    private static float lerp(float t, float from, float to) {
        return from + (to - from) * t;
    }

    public float shutterOpen(float partialTick) {
        return lerp(partialTick, shutterPrev, shutter);
    }

    public float needleAt(float partialTick) {
        return lerp(partialTick, needlePrev, needle);
    }

    public float coilGlow(float partialTick) {
        return lerp(partialTick, glowPrev, glow);
    }

    public float workTravel(float partialTick) {
        return lerp(partialTick, travelPrev, travel);
    }

    public float fanSpin(float partialTick) {
        return lerp(partialTick, fanAnglePrev, fanAngle);
    }

    public int clientAge() {
        return clientAge;
    }

    private final net.minecraft.world.Container menuContainer = new net.minecraft.world.Container() {
        @Override
        public int getContainerSize() {
            return SLOT_COUNT;
        }

        @Override
        public boolean isEmpty() {
            for (ItemStack stack : items) {
                if (!stack.isEmpty()) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public ItemStack getItem(int slot) {
            return items.get(slot);
        }

        @Override
        public ItemStack removeItem(int slot, int amount) {
            final ItemStack taken = ContainerHelper.removeItem(items, slot, amount);
            if (!taken.isEmpty()) {
                touched();
            }
            return taken;
        }

        @Override
        public ItemStack removeItemNoUpdate(int slot) {
            final ItemStack taken = ContainerHelper.takeItem(items, slot);
            touched();
            return taken;
        }

        @Override
        public void setItem(int slot, ItemStack stack) {
            items.set(slot, stack);
            if (stack.getCount() > getMaxStackSize()) {
                stack.setCount(getMaxStackSize());
            }
            touched();
        }

        @Override
        public void setChanged() {
            touched();
        }

        @Override
        public boolean stillValid(net.minecraft.world.entity.player.Player player) {
            return !isRemoved();
        }

        @Override
        public void clearContent() {
            items.clear();
            touched();
        }
    };

    public net.minecraft.world.Container menuContainer() {
        return menuContainer;
    }

    public ItemStack previewResult() {
        final AbstractCookingRecipe recipe = currentRecipe();
        return recipe == null || level == null
                ? ItemStack.EMPTY : recipe.getResultItem(level.registryAccess());
    }

    private final net.minecraft.world.inventory.ContainerData data =
            new net.minecraft.world.inventory.ContainerData() {
                @Override
                public int get(int index) {
                    return switch (index) {
                        case com.createstats.menu.InductionSmelterMenu.DATA_ENERGY -> energy;
                        case com.createstats.menu.InductionSmelterMenu.DATA_HEAT -> heat;
                        case com.createstats.menu.InductionSmelterMenu.DATA_PROGRESS ->
                                Math.round(progress);
                        case com.createstats.menu.InductionSmelterMenu.DATA_DURATION -> cachedDuration;
                        case com.createstats.menu.InductionSmelterMenu.DATA_DAMPER -> damper;
                        case com.createstats.menu.InductionSmelterMenu.DATA_FLAGS -> flags();
                        case com.createstats.menu.InductionSmelterMenu.DATA_DRAW -> measuredDraw;
                        case com.createstats.menu.InductionSmelterMenu.DATA_RATED -> ratedDraw;
                        case com.createstats.menu.InductionSmelterMenu.DATA_INTAKE -> measuredIntake;
                        case com.createstats.menu.InductionSmelterMenu.DATA_BANK -> bankSize();
                        default -> 0;
                    };
                }

                @Override
                public void set(int index, int value) {
                }

                @Override
                public int getCount() {
                    return com.createstats.menu.InductionSmelterMenu.DATA_SIZE;
                }
            };

    private int flags() {
        int f = 0;
        if (working) {
            f |= com.createstats.menu.InductionSmelterMenu.FLAG_WORKING;
        }
        if (isHot()) {
            f |= com.createstats.menu.InductionSmelterMenu.FLAG_HOT;
        }
        if (starved) {
            f |= com.createstats.menu.InductionSmelterMenu.FLAG_STARVED;
        }
        if (isBlasting()) {
            f |= com.createstats.menu.InductionSmelterMenu.FLAG_BLASTING;
        }
        if (redstoneCut) {
            f |= com.createstats.menu.InductionSmelterMenu.FLAG_CUT;
        }
        if (managed.isSuppressed()) {
            f |= com.createstats.menu.InductionSmelterMenu.FLAG_SHED;
        }
        if (outputBlocked) {
            f |= com.createstats.menu.InductionSmelterMenu.FLAG_BLOCKED;
        }

        if (needsBlastingLane()) {
            f |= com.createstats.menu.InductionSmelterMenu.FLAG_NEEDS_BLAST;
        } else if (!items.get(SLOT_IN).isEmpty() && currentRecipe() == null) {
            f |= com.createstats.menu.InductionSmelterMenu.FLAG_NO_RECIPE;
        }
        return f;
    }

    public net.minecraft.world.inventory.ContainerData dataAccess() {
        return data;
    }

    @Override
    public net.minecraft.network.chat.Component getDisplayName() {
        return net.minecraft.network.chat.Component.translatable("block.create_stats.induction_smelter");
    }

    @Override
    public net.minecraft.world.inventory.AbstractContainerMenu createMenu(
            int containerId, net.minecraft.world.entity.player.Inventory inventory,
            net.minecraft.world.entity.player.Player player) {
        return new com.createstats.menu.InductionSmelterMenu(containerId, inventory, this);
    }

    @Override
    public boolean addToGoggleTooltip(java.util.List<net.minecraft.network.chat.Component> tooltip,
                                      boolean isPlayerSneaking) {
        final java.util.Locale l = java.util.Locale.ROOT;
        tooltip.add(com.createstats.power.FeGoggle.title("Induction Smelter"));
        tooltip.add(com.createstats.power.FeGoggle.loadState(damper == DAMPER_SHUT, true, starved));
        tooltip.add(com.createstats.power.FeGoggle.dim(String.format(l, "Damper %s, %d%% of full",
                switch (damper) {
                    case DAMPER_SHUT -> "shut";
                    case DAMPER_HALF -> "half";
                    default -> "open";
                }, DAMPER_PERCENT[damper])));
        tooltip.add((isHot() ? com.createstats.power.FeGoggle.ok(String.format(l, "Muffle at %d%%, %s",
                Math.round(heatFraction() * 100.0f), isBlasting() ? "blasting" : "smelting"))
                : com.createstats.power.FeGoggle.warn(String.format(l, "Muffle at %d%%, too cold to run",
                Math.round(heatFraction() * 100.0f)))));

        if (ratedDraw > 0 && (isPlayerSneaking || starved || measuredIntake < ratedDraw)) {
            tooltip.add(com.createstats.power.FeGoggle.supplyDemand(measuredIntake, ratedDraw));
        }
        if (outputBlocked) {
            tooltip.add(com.createstats.power.FeGoggle.bad("Output full"));
        } else if (working) {
            tooltip.add(com.createstats.power.FeGoggle.warn(String.format(l, "Smelting, %d%%",
                    Math.round(batchFraction() * 100.0f))));
        } else if (items.get(SLOT_IN).isEmpty()) {
            tooltip.add(com.createstats.power.FeGoggle.dim("Nothing to smelt"));
        } else if (currentRecipe() == null) {
            tooltip.add(needsBlastingLane()
                    ? com.createstats.power.FeGoggle.bad("Damper too low: this needs the blasting lane")
                    : com.createstats.power.FeGoggle.warn("Nothing here smelts this"));
        }

        final int bank = bankSize();
        if (bank > 1) {
            tooltip.add(com.createstats.power.FeGoggle.dim(String.format(l, "Bank of %d", bank)));
        }
        if (isPlayerSneaking && !isHot() && idleTicks >= IDLE_HOLD_TICKS && damper != DAMPER_SHUT) {
            tooltip.add(com.createstats.power.FeGoggle.dim("Idle too long to be worth holding hot"));
        }
        if (!isPlayerSneaking) {
            tooltip.add(com.createstats.power.FeGoggle.more());
        }
        return true;
    }

    @Override
    public void setGated(boolean gated) {
        if (this.gated == gated) {
            return;
        }
        this.gated = gated;
        setChanged();
    }

    @Override
    public boolean isGated() {
        return gated;
    }

    private boolean gated;

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Energy", energy);
        tag.putInt("Damper", damper);
        tag.putBoolean("Gated", gated);
        tag.putInt("Heat", heat);
        tag.putInt("Idle", idleTicks);
        tag.putFloat("Progress", progress);
        tag.putBoolean("Blasting", laneBlasting);
        tag.putBoolean("Working", working);
        tag.putBoolean("Starved", starved);
        tag.putBoolean("Blocked", outputBlocked);
        tag.putInt("Duration", cachedDuration);
        tag.putInt("MDraw", measuredDraw);
        tag.putInt("MIntake", measuredIntake);
        tag.putInt("RDraw", ratedDraw);
        tag.putInt("Tier", managed.priorityOrdinal());
        tag.putInt("Lock", managed.lockOrdinal());
        ContainerHelper.saveAllItems(tag, items, registries);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        energy = Math.max(0, Math.min(BUFFER, tag.getInt("Energy")));
        damper = Math.max(0, Math.min(DAMPER_PERCENT.length - 1,
                tag.contains("Damper") ? tag.getInt("Damper") : DAMPER_OPEN));
        heat = Math.max(0, Math.min(HEAT_MAX, tag.getInt("Heat")));
        gated = tag.getBoolean("Gated");
        idleTicks = Math.max(0, Math.min(IDLE_HOLD_TICKS, tag.getInt("Idle")));
        progress = Math.max(0.0f, tag.getFloat("Progress"));
        laneBlasting = tag.getBoolean("Blasting");
        working = tag.getBoolean("Working");
        starved = tag.getBoolean("Starved");
        outputBlocked = tag.getBoolean("Blocked");
        cachedDuration = Math.max(0, tag.getInt("Duration"));
        measuredDraw = Math.max(0, tag.getInt("MDraw"));
        measuredIntake = Math.max(0, tag.getInt("MIntake"));
        ratedDraw = Math.max(0, tag.getInt("RDraw"));
        managed.load(tag.getInt("Tier"), tag.getInt("Lock"));
        items.clear();
        ContainerHelper.loadAllItems(tag, items, registries);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        final CompoundTag tag = super.getUpdateTag(registries);
        saveAdditional(tag, registries);
        return tag;
    }

    @Override
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener>
            getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }
}
