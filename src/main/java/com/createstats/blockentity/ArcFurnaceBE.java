package com.createstats.blockentity;

import com.createstats.block.ArcFurnaceBlock;
import com.createstats.recipe.ArcRecipe;
import com.createstats.recipe.ArcRecipeInput;
import com.createstats.registry.ModRecipes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.Optional;

// the one load that cares how steady your grid is
public class ArcFurnaceBE extends BlockEntity
        implements com.createstats.power.FeGridMember,
        com.createstats.power.FeManagedLoad,
        com.createstats.power.Gateable,
        net.minecraft.world.MenuProvider,
        com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation {
    public static final int SLOT_A = 0;

    public static final int SLOT_B = 1;

    public static final int SLOT_OUT = 2;

    public static final int SLOT_SLAG = 3;
    public static final int SLOT_COUNT = 4;

    public static final int MIN_TAP = 1;
    public static final int MAX_TAP = 3;

    // ~30 ticks at tap II, 14 at tap III. tap III with no storage SHOULD look marginal
    public static final int BUFFER = 16_000;

    private static final int RESTRIKE_TICKS = 20;

    private static final float CLEAN_GRADE = 0.99f;

    private static final float DIRTY_GRADE = 0.75f;

    public static final int SLAG_LIMIT = 64;

    public static int drawFor(int tap) {
        return 128 * tap * tap;
    }

    private int energy;
    private int intakeAccumulator;
    private int measuredIntake;
    private int measuredDraw;
    private int ratedDraw;

    private final com.createstats.power.FeManagedState managed =
            new com.createstats.power.FeManagedState();

    private boolean running;

    private int tap = 2;

    private final NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);

    private float progress;

    private int restrike;

    private int workTicks;
    private int stallTicks;

    private boolean arcStruck;
    private boolean starved;

    private int cachedDuration;
    private boolean outputBlocked;
    private boolean redstoneCut;

    public ArcFurnaceBE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
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
        return 4096;
    }

    @Override
    public IEnergyStorage attachedStorage() {
        return energyView;
    }

    @Override
    public boolean isGone(net.minecraft.world.level.Level owner) {
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

    public boolean isRunning() {
        return running;
    }

    public int tap() {
        return tap;
    }

    public int draw() {
        return drawFor(tap);
    }

    public boolean isArcStruck() {
        return arcStruck;
    }

    public boolean isStarved() {
        return starved;
    }

    public float batchFraction() {
        final ArcRecipe recipe = currentRecipe();
        return recipe == null ? 0.0f : Math.min(1.0f, progress / recipe.duration());
    }

    public float arcGrade() {
        final int total = workTicks + stallTicks;
        return total == 0 ? 1.0f : (float) workTicks / total;
    }

    public boolean isSlagFull() {
        return items.get(SLOT_SLAG).getCount() >= SLAG_LIMIT;
    }

    public ItemStack item(int slot) {
        return items.get(slot);
    }

    public boolean isCharge(ItemStack stack) {
        if (level == null || stack.isEmpty()) {
            return false;
        }
        for (RecipeHolder<ArcRecipe> holder
                : level.getRecipeManager().getAllRecipesFor(ModRecipes.ARC_TYPE.get())) {
            if (holder.value().acceptsAsCharge(stack)) {
                return true;
            }
        }
        return false;
    }

    @org.jetbrains.annotations.Nullable
    private ArcRecipe currentRecipe() {
        if (level == null) {
            return null;
        }
        final ArcRecipeInput in = new ArcRecipeInput(items.get(SLOT_A), items.get(SLOT_B));
        if (in.a().isEmpty() || in.b().isEmpty()) {
            return null;
        }
        final Optional<RecipeHolder<ArcRecipe>> found =
                level.getRecipeManager().getRecipeFor(ModRecipes.ARC_TYPE.get(), in, level);
        return found.map(RecipeHolder::value).orElse(null);
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
            if (stack.isEmpty() || slot > SLOT_B || !isCharge(stack)) {
                return stack;
            }
            final ItemStack held = items.get(slot);
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
                    items.set(slot, stack.copyWithCount(taken));
                } else {
                    held.grow(taken);
                }
                setChanged();
                sync();
            }
            return stack.copyWithCount(stack.getCount() - taken);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (slot < SLOT_OUT) {
                return ItemStack.EMPTY;
            }
            final ItemStack held = items.get(slot);
            if (held.isEmpty()) {
                return ItemStack.EMPTY;
            }
            final int taken = Math.min(amount, held.getCount());
            final ItemStack out = held.copyWithCount(taken);
            if (!simulate) {
                held.shrink(taken);
                if (held.isEmpty()) {
                    items.set(slot, ItemStack.EMPTY);
                }
                setChanged();
                sync();
            }
            return out;
        }

        @Override
        public int getSlotLimit(int slot) {
            return 64;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return slot <= SLOT_B && isCharge(stack);
        }
    };

    public IItemHandler itemHandle() {
        return itemHandle;
    }

    public static void registerCapabilities(
            net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK,
                com.createstats.registry.ModBlockEntities.ARC_FURNACE.get(),
                (be, side) -> be.itemHandle());
    }

    public int offerCharge(ItemStack offered) {
        if (!isCharge(offered)) {
            return 0;
        }
        for (int slot = SLOT_A; slot <= SLOT_B; slot++) {
            final ItemStack held = items.get(slot);
            if (held.isEmpty() || ItemStack.isSameItemSameComponents(held, offered)) {
                final ItemStack left = itemHandle.insertItem(slot, offered, false);
                return offered.getCount() - left.getCount();
            }
        }
        return 0;
    }

    public java.util.List<ItemStack> collectProduct() {
        final java.util.List<ItemStack> out = new java.util.ArrayList<>(2);
        for (int slot : new int[]{SLOT_OUT, SLOT_SLAG}) {
            if (!items.get(slot).isEmpty()) {
                out.add(items.get(slot));
                items.set(slot, ItemStack.EMPTY);
            }
        }
        if (!out.isEmpty()) {
            setChanged();
            sync();
        }
        return out;
    }

    public java.util.List<ItemStack> ejectCharge() {
        final java.util.List<ItemStack> out = new java.util.ArrayList<>(2);
        for (int slot : new int[]{SLOT_A, SLOT_B}) {
            if (!items.get(slot).isEmpty()) {
                out.add(items.get(slot));
                items.set(slot, ItemStack.EMPTY);
            }
        }
        if (!out.isEmpty()) {
            breakArc();
            progress = 0.0f;
            workTicks = 0;
            stallTicks = 0;
            setChanged();
            sync();
        }
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
        // auto shed lapses with no panel, the player lock doesn't

        if (network == null || !network.isPduManaged(level.getGameTime())) {
            setManagedShed(false);
        }

        redstoneCut = level.hasNeighborSignal(worldPosition);

        final boolean allowed = running && !redstoneCut && !gated && !managed.isForcedOff();
        final boolean live = allowed && !managed.isSuppressed();

        final ArcRecipe recipe = currentRecipe();

        outputBlocked = recipe != null
                && roomFor(SLOT_OUT, recipe.result()) < recipe.result().getCount();
        cachedDuration = recipe == null ? 0 : recipe.duration();
        final boolean holdOff = recipe == null
                || outputBlocked
                || (progress <= 0.0f && isSlagFull());

        final boolean wants = live && !holdOff;

        ratedDraw = allowed && !holdOff ? drawFor(tap) : 0;

        boolean progressed = false;
        if (wants) {
            final int cost = drawFor(tap);
            if (energy >= cost) {
                energy -= cost;
                measuredDraw = cost;
                if (restrike > 0) {
                    restrike--;
                    countStall();
                } else {
                    progress += tap;
                    workTicks++;
                    progressed = true;
                }
            } else {
                measuredDraw = 0;
                if (restrike <= 0) {
                    restrike = RESTRIKE_TICKS;
                }
                countStall();
            }
            setChanged();
        } else {
            measuredDraw = 0;
            if (!live) {
                breakArc();
            }
        }

        measuredIntake = intakeAccumulator;
        intakeAccumulator = 0;

        if (recipe != null && progress >= recipe.duration()) {
            complete(recipe);
        }

        final boolean nowStarved = wants && (measuredDraw == 0 || measuredIntake < drawFor(tap));
        if (progressed != arcStruck || nowStarved != starved) {
            arcStruck = progressed;
            starved = nowStarved;
            refresh();
        }
        syncTelemetryIfDue();
    }

    private void countStall() {
        if (workTicks > 0) {
            stallTicks++;
        }
    }

    private void breakArc() {
        arcStruck = false;
        restrike = RESTRIKE_TICKS;
    }

    private void complete(ArcRecipe recipe) {
        final ArcRecipeInput in = new ArcRecipeInput(items.get(SLOT_A), items.get(SLOT_B));
        final int[] cost = recipe.costs(in);
        if (cost == null) {
            resetBatch();
            return;
        }
        final float grade = arcGrade();
        final ItemStack product = recipe.assemble(in, level.registryAccess());
        final int base = product.getCount();
        if (tap == MIN_TAP && grade >= CLEAN_GRADE) {
            product.grow(1);
        }
        final int room = roomFor(SLOT_OUT, product);
        if (room < base) {
            return;
        }
        if (room < product.getCount()) {
            product.setCount(room);
        }
        items.get(SLOT_A).shrink(cost[0]);
        items.get(SLOT_B).shrink(cost[1]);
        if (items.get(SLOT_A).isEmpty()) {
            items.set(SLOT_A, ItemStack.EMPTY);
        }
        if (items.get(SLOT_B).isEmpty()) {
            items.set(SLOT_B, ItemStack.EMPTY);
        }
        put(SLOT_OUT, product);
        if (grade < DIRTY_GRADE) {
            final ItemStack slag = new ItemStack(com.createstats.registry.ModItems.SLAG.get(),
                    tap == MAX_TAP ? 2 : 1);
            final int slagRoom = roomFor(SLOT_SLAG, slag);
            if (slagRoom > 0) {
                slag.setCount(Math.min(slagRoom, slag.getCount()));
                put(SLOT_SLAG, slag);
            }
        }
        resetBatch();
        if (level instanceof ServerLevel server) {
            server.playSound(null, worldPosition, SoundEvents.ANVIL_LAND, SoundSource.BLOCKS,
                    0.35f, grade >= CLEAN_GRADE ? 1.5f : 1.1f);
            server.sendParticles(ParticleTypes.LAVA,
                    worldPosition.getX() + 0.5, worldPosition.getY() + 0.7, worldPosition.getZ() + 0.5,
                    3, 0.2, 0.05, 0.2, 0.0);
        }
        setChanged();
        sync();
    }

    private void resetBatch() {
        progress = 0.0f;
        workTicks = 0;
        stallTicks = 0;
    }

    private int roomFor(int slot, ItemStack stack) {
        final ItemStack held = items.get(slot);
        final int cap = Math.min(64, stack.getMaxStackSize());
        if (held.isEmpty()) {
            return cap;
        }
        return ItemStack.isSameItemSameComponents(held, stack) ? cap - held.getCount() : 0;
    }

    private void put(int slot, ItemStack stack) {
        final ItemStack held = items.get(slot);
        if (held.isEmpty()) {
            items.set(slot, stack);
        } else {
            held.grow(stack.getCount());
        }
    }

    private void refresh() {
        if (level == null || level.isClientSide()
                || !(getBlockState().getBlock() instanceof ArcFurnaceBlock)) {
            return;
        }
        final BlockState state = getBlockState();
        if (state.getValue(ArcFurnaceBlock.RUNNING) == running
                && state.getValue(ArcFurnaceBlock.ARC) == arcStruck
                && state.getValue(ArcFurnaceBlock.STARVED) == starved) {
            sync();
            return;
        }
        level.setBlock(worldPosition, state
                .setValue(ArcFurnaceBlock.RUNNING, running)
                .setValue(ArcFurnaceBlock.ARC, arcStruck)
                .setValue(ArcFurnaceBlock.STARVED, starved), Block.UPDATE_ALL);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        invalidateNetwork();
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
        final int shown = measuredDraw * 31 + measuredIntake;
        final int bucket = Math.max(1, Math.abs(shown) / 16);
        if (Math.abs(shown - lastSyncedTelemetry) < bucket && ticksSinceTelemetrySync < 20) {
            return;
        }
        lastSyncedTelemetry = shown;
        ticksSinceTelemetrySync = 0;
        sync();
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

    private void touched() {
        if (level != null && !level.isClientSide() && progress > 0.0f && currentRecipe() == null) {
            resetBatch();
            breakArc();
        }
        setChanged();
        sync();
    }

    private final net.minecraft.world.inventory.ContainerData data =
            new net.minecraft.world.inventory.ContainerData() {
                @Override
                public int get(int index) {
                    return switch (index) {
                        case com.createstats.menu.ArcFurnaceMenu.DATA_ENERGY -> energy;
                        case com.createstats.menu.ArcFurnaceMenu.DATA_PROGRESS -> Math.round(progress);
                        case com.createstats.menu.ArcFurnaceMenu.DATA_DURATION -> cachedDuration;
                        case com.createstats.menu.ArcFurnaceMenu.DATA_TAP -> tap;
                        case com.createstats.menu.ArcFurnaceMenu.DATA_FLAGS -> flags();
                        case com.createstats.menu.ArcFurnaceMenu.DATA_DRAW -> measuredDraw;
                        case com.createstats.menu.ArcFurnaceMenu.DATA_INTAKE -> measuredIntake;
                        case com.createstats.menu.ArcFurnaceMenu.DATA_GRADE ->
                                Math.round(arcGrade() * 100.0f);
                        default -> 0;
                    };
                }

                @Override
                public void set(int index, int value) {
                }

                @Override
                public int getCount() {
                    return com.createstats.menu.ArcFurnaceMenu.DATA_SIZE;
                }
            };

    private int flags() {
        int f = 0;
        if (running) {
            f |= com.createstats.menu.ArcFurnaceMenu.FLAG_RUNNING;
        }
        if (arcStruck) {
            f |= com.createstats.menu.ArcFurnaceMenu.FLAG_ARC;
        }
        if (starved) {
            f |= com.createstats.menu.ArcFurnaceMenu.FLAG_STARVED;
        }
        if (isSlagFull()) {
            f |= com.createstats.menu.ArcFurnaceMenu.FLAG_SLAG_FULL;
        }
        if (redstoneCut) {
            f |= com.createstats.menu.ArcFurnaceMenu.FLAG_CUT;
        }
        if (managed.isSuppressed()) {
            f |= com.createstats.menu.ArcFurnaceMenu.FLAG_SHED;
        }
        if (outputBlocked) {
            f |= com.createstats.menu.ArcFurnaceMenu.FLAG_BLOCKED;
        }
        return f;
    }

    public net.minecraft.world.inventory.ContainerData dataAccess() {
        return data;
    }

    @Override
    public net.minecraft.network.chat.Component getDisplayName() {
        return net.minecraft.network.chat.Component.translatable("block.create_stats.arc_furnace");
    }

    @Override
    public net.minecraft.world.inventory.AbstractContainerMenu createMenu(
            int containerId, net.minecraft.world.entity.player.Inventory inventory,
            net.minecraft.world.entity.player.Player player) {
        return new com.createstats.menu.ArcFurnaceMenu(containerId, inventory, this);
    }

    public void setTap(int wanted) {
        final int clamped = Math.max(MIN_TAP, Math.min(MAX_TAP, wanted));
        if (clamped == tap) {
            return;
        }
        tap = clamped;
        setChanged();
        refresh();
    }

    private boolean hatchLatched;

    public boolean isHatchLatched() {
        return hatchLatched;
    }

    public boolean toggleHatch() {
        hatchLatched = !hatchLatched;
        setChanged();
        sync();
        return hatchLatched;
    }

    public void setRunning(boolean on) {
        if (running == on) {
            return;
        }
        running = on;
        if (!running) {
            breakArc();
        }
        setChanged();
        refresh();
    }

    private ItemStack previewResult = ItemStack.EMPTY;
    private ItemStack previewA = ItemStack.EMPTY;
    private ItemStack previewB = ItemStack.EMPTY;

    private boolean previewValid;

    public ItemStack previewResult() {
        if (level == null) {
            return ItemStack.EMPTY;
        }
        final ItemStack a = items.get(SLOT_A);
        final ItemStack b = items.get(SLOT_B);

        if (previewValid && ItemStack.isSameItemSameComponents(a, previewA)
                && ItemStack.isSameItemSameComponents(b, previewB)) {
            return previewResult;
        }
        previewValid = true;
        previewA = a.copy();
        previewB = b.copy();
        final ArcRecipe recipe = currentRecipe();
        previewResult = recipe == null
                ? ItemStack.EMPTY
                : recipe.assemble(new ArcRecipeInput(a, b), level.registryAccess());
        return previewResult;
    }

    public int recipeDuration() {
        if (level != null && level.isClientSide()) {
            final ArcRecipe recipe = currentRecipe();
            return recipe == null ? 0 : recipe.duration();
        }
        return cachedDuration;
    }

    public int energyStored() {
        return energy;
    }

    private float lift = 1.0f;
    private float liftPrev = 1.0f;
    private float hatchOpen;
    private float hatchOpenPrev;
    private float fanAngle;
    private float fanAnglePrev;
    private float fanSpeed;
    private float heat;
    private float heatPrev;
    private float drawerOut;
    private float drawerOutPrev;
    private int clientAge;

    public void clientTick() {
        liftPrev = lift;
        hatchOpenPrev = hatchOpen;
        fanAnglePrev = fanAngle;
        heatPrev = heat;
        drawerOutPrev = drawerOut;
        clientAge++;

        final BlockState state = getBlockState();
        if (!(state.getBlock() instanceof ArcFurnaceBlock)) {
            return;
        }
        final boolean on = state.getValue(ArcFurnaceBlock.RUNNING);
        final boolean arc = on && state.getValue(ArcFurnaceBlock.ARC);
        final boolean sick = on && state.getValue(ArcFurnaceBlock.STARVED);

        lift = approach(lift, arc ? 0.0f : (on ? 0.45f : 1.0f), arc ? 0.11f : 0.045f);

        hatchOpen = approach(hatchOpen, hatchLatched ? 1.0f : 0.0f, hatchLatched ? 0.13f : 0.10f);

        heat = approach(heat, arc ? (sick ? 0.55f : 1.0f) : (on ? 0.24f : 0.0f), arc ? 0.09f : 0.010f);

        drawerOut = approach(drawerOut,
                Math.min(1.0f, items.get(SLOT_SLAG).getCount() / (float) SLAG_LIMIT), 0.035f);

        fanSpeed = approach(fanSpeed, on ? (2.4f + 2.6f * tap) : 0.0f, 0.12f);
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

    public float electrodeLift(float partialTick) {
        return lerp(partialTick, liftPrev, lift);
    }

    public float hatchSwing(float partialTick) {
        return lerp(partialTick, hatchOpenPrev, hatchOpen);
    }

    public float fanSpin(float partialTick) {
        return lerp(partialTick, fanAnglePrev, fanAngle);
    }

    public float glowHeat(float partialTick) {
        return lerp(partialTick, heatPrev, heat);
    }

    public float drawerSlide(float partialTick) {
        return lerp(partialTick, drawerOutPrev, drawerOut);
    }

    public int clientAge() {
        return clientAge;
    }

    @Override
    public boolean addToGoggleTooltip(java.util.List<net.minecraft.network.chat.Component> tooltip,
                                      boolean isPlayerSneaking) {
        final java.util.Locale l = java.util.Locale.ROOT;
        tooltip.add(com.createstats.power.FeGoggle.title("Electric Arc Furnace"));
        tooltip.add(com.createstats.power.FeGoggle.loadState(!running, true, starved));
        tooltip.add(com.createstats.power.FeGoggle.dim(String.format(l, "Tap %s, %d FE/t",
                "I".repeat(tap), drawFor(tap))));

        if (running && (isPlayerSneaking || starved || measuredIntake < ratedDraw)) {
            tooltip.add(com.createstats.power.FeGoggle.supplyDemand(measuredIntake, ratedDraw));
        }
        if (currentRecipe() == null) {
            tooltip.add(com.createstats.power.FeGoggle.dim("No charge"));
        } else if (arcStruck || restrike > 0) {
            tooltip.add(com.createstats.power.FeGoggle.warn(String.format(l, "Smelting, %d%%",
                    Math.round(batchFraction() * 100.0f))));

            final int grade = Math.round(arcGrade() * 100.0f);
            if (grade < 99) {
                tooltip.add(com.createstats.power.FeGoggle.bad(String.format(l, "Arc held %d%% of the run",
                        grade)));
            } else if (isPlayerSneaking) {
                tooltip.add(com.createstats.power.FeGoggle.ok("Arc held, clean run"));
            }
        }
        if (isSlagFull()) {
            tooltip.add(com.createstats.power.FeGoggle.bad("Slag hopper full, will not restart"));
        } else if (!items.get(SLOT_SLAG).isEmpty()) {
            tooltip.add(com.createstats.power.FeGoggle.warn(
                    "Slag: " + items.get(SLOT_SLAG).getCount()));
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
        tag.putBoolean("Running", running);
        tag.putBoolean("Gated", gated);
        tag.putBoolean("Hatch", hatchLatched);
        tag.putInt("Tap", tap);
        tag.putInt("Tier", managed.priorityOrdinal());
        tag.putInt("Lock", managed.lockOrdinal());
        tag.putFloat("Progress", progress);
        tag.putInt("Restrike", restrike);
        tag.putInt("Work", workTicks);
        tag.putInt("Stall", stallTicks);
        tag.putBoolean("Arc", arcStruck);
        tag.putBoolean("Starved", starved);
        tag.putInt("MDraw", measuredDraw);
        tag.putInt("MIntake", measuredIntake);
        tag.putInt("RDraw", ratedDraw);
        ContainerHelper.saveAllItems(tag, items, registries);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        energy = Math.max(0, Math.min(BUFFER, tag.getInt("Energy")));
        running = tag.getBoolean("Running");
        gated = tag.getBoolean("Gated");
        hatchLatched = tag.getBoolean("Hatch");
        tap = Math.max(MIN_TAP, Math.min(MAX_TAP, tag.contains("Tap") ? tag.getInt("Tap") : 2));
        managed.load(tag.getInt("Tier"), tag.getInt("Lock"));
        progress = Math.max(0.0f, tag.getFloat("Progress"));
        restrike = Math.max(0, tag.getInt("Restrike"));
        workTicks = Math.max(0, tag.getInt("Work"));
        stallTicks = Math.max(0, tag.getInt("Stall"));
        arcStruck = tag.getBoolean("Arc");
        starved = tag.getBoolean("Starved");
        measuredDraw = Math.max(0, tag.getInt("MDraw"));
        measuredIntake = Math.max(0, tag.getInt("MIntake"));
        ratedDraw = Math.max(0, tag.getInt("RDraw"));
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
