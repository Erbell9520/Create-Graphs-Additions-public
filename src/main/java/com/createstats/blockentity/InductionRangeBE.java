package com.createstats.blockentity;

import com.createstats.block.InductionRangeBlock;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock.HeatLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.BlastingRecipe;
import net.minecraft.world.item.crafting.CampfireCookingRecipe;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.Optional;

/** [1] */
public class InductionRangeBE extends BlockEntity
        implements com.createstats.power.FeGridMember,
        com.createstats.power.FeManagedLoad,
        com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation {

    /** [2] */
    public static final int DRAW_HIGH = 256;
    public static final int DRAW_MEDIUM = DRAW_HIGH / 4;
    public static final int DRAW_LOW = DRAW_HIGH / 16;

    /** [3] */
    private static final int BUFFER = 2000;

    /** Campfire speed, then double, then a bit over triple. Matches the Induction Heater's curve. */
    private static final float COOK_RATE_LOW = 1.0f;
    private static final float COOK_RATE_MEDIUM = 2.0f;
    private static final float COOK_RATE_HIGH = 10.0f / 3.0f;
    private static final int COOK_TICKS = 600;
    /** One batch. Eight is what fits on the trivet without the pile hiding the block. */
    public static final int STACK_LIMIT = 8;

    private int energy;
    /** FE that arrived since the last tick, and the settled per-tick figure it feeds. */
    private int intakeAccumulator;
    private int measuredIntake;
    /** What the element actually spent last tick, which is the setting unless starved. */
    private int measuredDraw;
    /** [4] */
    private int ratedDraw;
    /** Tier and lock as set from the panel. See {@link com.createstats.power.FeManagedLoad}. */
    private final com.createstats.power.FeManagedState managed = new com.createstats.power.FeManagedState();
    /** What the player asked for: 0 off, 1 low, 2 medium, 3 high. Persisted. */
    private int setting;
    /** The setting it is allowed to hold, which is {@link #setting} unless redstone or the panel says no. */
    private int allowedSetting;
    /** What it is actually managing, which is the setting unless the supply fell short. */
    private HeatLevel achieved = HeatLevel.NONE;

    private ItemStack cookStack = ItemStack.EMPTY;
    private float cookProgress;
    private boolean cooked;
    private RecipeManager.CachedCheck<SingleRecipeInput, CampfireCookingRecipe> cookCheck;
    private RecipeManager.CachedCheck<SingleRecipeInput, SmeltingRecipe> smeltCheck;
    private RecipeManager.CachedCheck<SingleRecipeInput, BlastingRecipe> blastCheck;

    /** [5] */
    private final IItemHandler itemHandle = new IItemHandler() {
        @Override
        public int getSlots() {
            return 1;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return cookStack;
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (stack.isEmpty() || cooked || !isProcessable(stack)) {
                return stack;
            }
            if (!cookStack.isEmpty() && !ItemStack.isSameItemSameComponents(cookStack, stack)) {
                return stack;
            }
            final int room = STACK_LIMIT - cookStack.getCount();
            final int taken = Math.min(room, stack.getCount());
            if (taken <= 0) {
                return stack;
            }
            if (!simulate) {
                placeFood(stack.copyWithCount(taken));
            }
            return stack.copyWithCount(stack.getCount() - taken);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (!cooked || cookStack.isEmpty()) {
                return ItemStack.EMPTY;   // never pull food off the heat before it is done
            }
            final int taken = Math.min(amount, cookStack.getCount());
            final ItemStack out = cookStack.copyWithCount(taken);
            if (!simulate) {
                cookStack.shrink(taken);
                if (cookStack.isEmpty()) {
                    cookStack = ItemStack.EMPTY;
                    cookProgress = 0.0f;
                    cooked = false;
                }
                setChanged();
                sync();
            }
            return out;
        }

        @Override
        public int getSlotLimit(int slot) {
            return STACK_LIMIT;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return isProcessable(stack);
        }
    };

    public IItemHandler itemHandle() {
        return itemHandle;
    }

    public static void registerCapabilities(
            net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK,
                com.createstats.registry.ModBlockEntities.INDUCTION_RANGE.get(),
                (be, side) -> be.itemHandle());
    }

    /** Receive only: a range is a load and never a source for anything else. */
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

    public InductionRangeBE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    // grid membership, and the bank

    /** [6] */
    @org.jetbrains.annotations.Nullable
    private com.createstats.power.FeWireNetwork network;

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

    /** Generous, so a whole bank refills within a tick or two of supply returning. */
    @Override
    public int transferRate() {
        return 512;
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

    /** Forces rediscovery, for when a unit joins or leaves a bank. */
    public void invalidateNetwork() {
        if (network != null) {
            network.markDirty();
            network = null;
        }
    }

    public IEnergyStorage energyHandle() {
        return energyView;
    }

    // the dial

    /** 0 off, 1 low, 2 medium, 3 high. */
    public int setting() {
        return setting;
    }

    /** [7] */
    public HeatLevel requested() {
        return heatFor(allowedSetting);
    }

    public HeatLevel achieved() {
        return achieved;
    }

    public int draw() {
        return drawFor(setting);
    }

    /**
     * The steady cost of the setting this element is allowed to hold, which is zero while redstone is
     * cutting it. This is what the Power Distribution Unit budgets against.
     */
    @Override
    public int ratedDrawFePerTick() {
        return ratedDraw;
    }

    /** What the element actually spent last tick, which is what the panel shows. */
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
        refresh();   // a force-off goes cold now, rather than after the buffer runs down
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

    /** True when the supply cannot hold the setting, so the block is running cooler than asked. */
    public boolean isStarved() {
        return achieved.ordinal() < requested().ordinal();
    }

    /** Advances the dial one step and wraps. Called from a bare right click. */
    public void cycleSetting() {
        setting = (setting + 1) % 4;
        setChanged();
        refresh();
    }

    public static HeatLevel heatFor(int setting) {
        return switch (setting) {
            case 1 -> HeatLevel.SMOULDERING;
            case 2 -> HeatLevel.KINDLED;
            case 3 -> HeatLevel.SEETHING;
            default -> HeatLevel.NONE;
        };
    }

    public static int drawFor(int setting) {
        return switch (setting) {
            case 1 -> DRAW_LOW;
            case 2 -> DRAW_MEDIUM;
            case 3 -> DRAW_HIGH;
            default -> 0;
        };
    }

    // tick

    public void tick() {
        if (level == null || level.isClientSide()) {
            return;
        }
        // [8]
        if (network == null || network.isDirty()) {
            com.createstats.power.FeWireNetwork.buildFrom(this);
        }
        if (network != null) {
            network.solveOnce(level);
        }
        // [9]
        if (network == null || !network.isPduManaged(level.getGameTime())) {
            setManagedShed(false);
        }
        // [10]
        final boolean cut = level.hasNeighborSignal(worldPosition) || managed.isSuppressed();
        final int wanted = cut ? 0 : setting;
        allowedSetting = wanted;
        // [11]
        ratedDraw = drawFor(wanted);

        // [12]
        int level0 = wanted;
        while (level0 > 0 && energy < drawFor(level0)) {
            level0--;
        }
        measuredDraw = level0 > 0 ? drawFor(level0) : 0;
        if (level0 > 0) {
            energy -= measuredDraw;
            setChanged();
        }
        // Sampled once a tick rather than smoothed: the reading is meant to answer "is my supply
        // keeping up", and an averaged number hides exactly the dips that question is about.
        measuredIntake = intakeAccumulator;
        intakeAccumulator = 0;
        final HeatLevel now = heatFor(level0);
        if (now != achieved) {
            achieved = now;
            refresh();
        }
        tickCooking(now);
    }

    /** Writes the achieved heat and the starved flag into the blockstate, and nothing else. */
    private void refresh() {
        if (level == null || level.isClientSide() || !(getBlockState().getBlock() instanceof InductionRangeBlock)) {
            return;
        }
        final BlockState state = getBlockState();
        final boolean starved = isStarved();
        if (state.getValue(BlazeBurnerBlock.HEAT_LEVEL) == achieved
                && state.getValue(InductionRangeBlock.STARVED) == starved) {
            return;
        }
        level.setBlock(worldPosition, state
                .setValue(BlazeBurnerBlock.HEAT_LEVEL, achieved)
                .setValue(InductionRangeBlock.STARVED, starved), Block.UPDATE_ALL);
    }

    // cooking

    private void tickCooking(HeatLevel heat) {
        if (cookStack.isEmpty() || cooked || level == null) {
            return;
        }
        final float rate = switch (heat) {
            case SEETHING -> COOK_RATE_HIGH;
            case FADING, KINDLED -> COOK_RATE_MEDIUM;
            case SMOULDERING -> COOK_RATE_LOW;
            default -> 0.0f;
        };
        if (rate <= 0.0f) {
            return;
        }
        cookProgress += rate;
        if (cookProgress < COOK_TICKS) {
            return;
        }
        final ItemStack result = resultFor(cookStack, heat);
        if (result == null) {
            cookProgress = 0.0f;   // the dial dropped below what this item needs; wait, do not burn it
            return;
        }
        result.setCount(cookStack.getCount());
        cookStack = result;
        cooked = true;
        cookProgress = COOK_TICKS;
        if (level instanceof ServerLevel server) {
            server.playSound(null, worldPosition, SoundEvents.NOTE_BLOCK_BELL.value(),
                    SoundSource.BLOCKS, 1.0f, 1.4f);
            server.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                    worldPosition.getX() + 0.5, worldPosition.getY() + 0.85, worldPosition.getZ() + 0.5,
                    8, 0.15, 0.05, 0.15, 0.004);
        }
        setChanged();
        sync();
    }

    /** [13] */
    @javax.annotation.Nullable
    private ItemStack resultFor(ItemStack input, HeatLevel heat) {
        if (level == null || input.isEmpty() || heat == HeatLevel.NONE) {
            return null;
        }
        final SingleRecipeInput in = new SingleRecipeInput(input);
        if (heat == HeatLevel.SEETHING) {
            final Optional<RecipeHolder<BlastingRecipe>> blast = blastCheck().getRecipeFor(in, level);
            if (blast.isPresent()) {
                return assemble(blast.get().value(), in);
            }
        }
        if (heat.isAtLeast(HeatLevel.KINDLED)) {
            final Optional<RecipeHolder<SmeltingRecipe>> smelt = smeltCheck().getRecipeFor(in, level);
            if (smelt.isPresent()) {
                return assemble(smelt.get().value(), in);
            }
        }
        final Optional<RecipeHolder<CampfireCookingRecipe>> cook = cookCheck().getRecipeFor(in, level);
        return cook.map(r -> assemble(r.value(), in)).orElse(null);
    }

    private ItemStack assemble(AbstractCookingRecipe recipe, SingleRecipeInput in) {
        return recipe.assemble(in, level.registryAccess());
    }

    private RecipeManager.CachedCheck<SingleRecipeInput, CampfireCookingRecipe> cookCheck() {
        if (cookCheck == null) {
            cookCheck = RecipeManager.createCheck(RecipeType.CAMPFIRE_COOKING);
        }
        return cookCheck;
    }

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

    /** True when anything this hob can ever do would process the item, whatever it is set to now. */
    public boolean isProcessable(ItemStack stack) {
        if (level == null || stack.isEmpty()) {
            return false;
        }
        final SingleRecipeInput in = new SingleRecipeInput(stack);
        return cookCheck().getRecipeFor(in, level).isPresent()
                || smeltCheck().getRecipeFor(in, level).isPresent()
                || blastCheck().getRecipeFor(in, level).isPresent();
    }

    /** Kept for the block's hand interaction, which should accept anything the hob might process. */
    public boolean isCookable(ItemStack stack) {
        return isProcessable(stack);
    }

    public boolean hasFood() {
        return !cookStack.isEmpty();
    }

    public boolean isCookDone() {
        return cooked;
    }

    public ItemStack displayedFood() {
        return cookStack;
    }

    /** Progress toward done, for the renderer and the goggle overlay. */
    public float cookFraction() {
        return cookStack.isEmpty() ? 0.0f : Math.min(1.0f, cookProgress / COOK_TICKS);
    }

    /** Accepts up to a stack of one cookable item. Returns how many were taken. */
    public int placeFood(ItemStack offered) {
        if (!isCookable(offered)) {
            return 0;
        }
        if (!cookStack.isEmpty() && (cooked || !ItemStack.isSameItemSameComponents(cookStack, offered))) {
            return 0;
        }
        final int room = STACK_LIMIT - cookStack.getCount();
        final int taken = Math.min(room, offered.getCount());
        if (taken <= 0) {
            return 0;
        }
        if (cookStack.isEmpty()) {
            cookStack = offered.copyWithCount(taken);
            cookProgress = 0.0f;
            cooked = false;
        } else {
            cookStack.grow(taken);
        }
        setChanged();
        sync();
        return taken;
    }

    /** Hands back whatever is on the hob, cooked or not. Pulling early loses the progress. */
    public ItemStack takeFood() {
        final ItemStack out = cookStack;
        cookStack = ItemStack.EMPTY;
        cookProgress = 0.0f;
        cooked = false;
        setChanged();
        sync();
        return out;
    }

    public void dropFood() {
        if (level != null && !cookStack.isEmpty()) {
            Block.popResource(level, worldPosition, cookStack);
            cookStack = ItemStack.EMPTY;
        }
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

    /** [14] */
    @Override
    public boolean addToGoggleTooltip(java.util.List<net.minecraft.network.chat.Component> tooltip,
                                      boolean isPlayerSneaking) {
        final java.util.Locale l = java.util.Locale.ROOT;
        tooltip.add(pad(net.minecraft.network.chat.Component.literal("Induction Range")
                .withStyle(net.minecraft.ChatFormatting.GRAY)));

        final HeatLevel want = requested();
        final String setting = switch (setting()) {
            case 1 -> "Low";
            case 2 -> "Medium";
            case 3 -> "High";
            default -> "Off";
        };
        final boolean starved = isStarved();
        tooltip.add(pad(net.minecraft.network.chat.Component.literal(
                starved ? "Set to " + setting + ", holding " + name(achieved) + " only"
                        : "Set to " + setting)
                .withStyle(starved ? net.minecraft.ChatFormatting.GOLD
                        : net.minecraft.ChatFormatting.GRAY)));

        tooltip.add(pad(net.minecraft.network.chat.Component.literal(measuredDraw > 0
                ? String.format(l, "Drawing: %,d FE/t", measuredDraw) : "Idle")
                .withStyle(measuredDraw > 0 ? net.minecraft.ChatFormatting.GREEN
                        : net.minecraft.ChatFormatting.DARK_GRAY)));
        tooltip.add(pad(net.minecraft.network.chat.Component.literal(
                String.format(l, "Supply: %,d FE/t", measuredIntake))
                .withStyle(measuredIntake < measuredDraw ? net.minecraft.ChatFormatting.GOLD
                        : net.minecraft.ChatFormatting.AQUA)));
        tooltip.add(pad(net.minecraft.network.chat.Component.literal(
                String.format(l, "Buffer: %,d / %,d FE", energy, BUFFER))
                .withStyle(net.minecraft.ChatFormatting.AQUA)));

        if (!cookStack.isEmpty()) {
            tooltip.add(pad(net.minecraft.network.chat.Component.literal(cooked
                    ? "Batch ready: " + cookStack.getCount()
                    : String.format(l, "Cooking %d, %d%%", cookStack.getCount(),
                            Math.round(cookFraction() * 100.0f)))
                    .withStyle(cooked ? net.minecraft.ChatFormatting.GREEN
                            : net.minecraft.ChatFormatting.YELLOW)));
        }
        return true;
    }

    private static net.minecraft.network.chat.Component pad(
            net.minecraft.network.chat.Component line) {
        return net.minecraft.network.chat.Component.literal("    ").append(line);
    }

    private static String name(HeatLevel heat) {
        return switch (heat) {
            case SMOULDERING -> "Low";
            case FADING, KINDLED -> "Medium";
            case SEETHING -> "High";
            default -> "nothing";
        };
    }

    // persistence

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Energy", energy);
        tag.putInt("Setting", setting);
        tag.putInt("Tier", managed.priorityOrdinal());
        tag.putInt("Lock", managed.lockOrdinal());
        tag.putInt("Achieved", achieved.ordinal());
        tag.putFloat("Cook", cookProgress);
        tag.putBoolean("Cooked", cooked);
        if (!cookStack.isEmpty()) {
            tag.put("Food", cookStack.save(registries));
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        energy = Math.max(0, Math.min(BUFFER, tag.getInt("Energy")));
        setting = Math.max(0, Math.min(3, tag.getInt("Setting")));
        allowedSetting = setting;   // the first tick re-derives it from redstone and the panel
        managed.load(tag.getInt("Tier"), tag.getInt("Lock"));
        final HeatLevel[] levels = HeatLevel.values();
        achieved = levels[Math.max(0, Math.min(levels.length - 1, tag.getInt("Achieved")))];
        cookProgress = tag.getFloat("Cook");
        cooked = tag.getBoolean("Cooked");
        cookStack = tag.contains("Food")
                ? ItemStack.parseOptional(registries, tag.getCompound("Food"))
                : ItemStack.EMPTY;
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
