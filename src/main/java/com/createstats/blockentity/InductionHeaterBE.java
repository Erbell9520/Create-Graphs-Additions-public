package com.createstats.blockentity;

import com.createstats.ModConfiguration;
import com.createstats.item.InductionCoilItem;
import com.createstats.registry.ModBlockEntities;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock.HeatLevel;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CampfireCookingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Optional;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.List;

/** [1] */
public class InductionHeaterBE extends KineticBlockEntity {

    /** RPM floors for each heat band. */
    public static final int RPM_WARM = 64;
    public static final int RPM_KINDLED = 128;
    public static final int RPM_SEETHING = 256;

    /** SU drawn per RPM once the block is doing anything at all (>= RPM_WARM). */
    public static final float STRESS_IMPACT = 32f;

    /** Charge thresholds: floorRpm * 16 s * 20 t/s, so each band takes 16 s at its floor RPM. */
    private static final float CHARGE_WARM = RPM_WARM * 320f;      // 20 480
    private static final float CHARGE_KINDLED = RPM_KINDLED * 320f; // 40 960
    private static final float CHARGE_SEETHING = RPM_SEETHING * 320f; // 81 920

    /** Charge bled per tick when above the current RPM band's cap (full cool-down ~32 s). */
    private static final float CHARGE_DECAY = 128f;

    /** Re-push the network stress only when the coefficient moved (mirrors KineticFlywheelBE). */
    private static final float STRESS_PUSH_EPS = 0.5f;

    /** Cook-rate multipliers per tier: Warm cooks at 1x (normal campfire time), Kindled 2x, Seething ~3.33x. */
    private static final float COOK_RATE_WARM = 1f;             // 1x speed (normal campfire time)
    private static final float COOK_RATE_KINDLED = 1f / 0.5f;   // 2x speed
    private static final float COOK_RATE_SEETHING = 1f / 0.3f;  // ~3.33x speed

    /** Coil wear multiplier at Kindled relative to Seething (0.33x = coil lasts ~3x longer at Kindled). */
    private static final float KINDLED_WEAR_FACTOR = 0.33f;

    private float charge;
    private ItemStack coil = ItemStack.EMPTY;
    private float lastPushedStress = Float.NaN;
    private boolean pushingStress; // re-entrancy guard: updateStressFor re-fires our own onSpeedChanged
    private float coilWearAccumulator;
    private float lastSyncedCharge = -1;
    private int lastSyncedCoilDamage = -1;

    // cooking station state
    /** The single plate stack: raw while cooking, flips to cooked atomically when done. */
    private ItemStack cookStack = ItemStack.EMPTY;
    /** Accumulated progress in base-recipe ticks; advances by the tier's rate multiplier each tick. */
    private float cookProgress;
    /** Base cook time of the current recipe, cached when the stack is inserted. */
    private int cookTime;
    /** True once the current stack has been converted; cleared when the plate is emptied. */
    private boolean cooked;
    private final RecipeManager.CachedCheck<SingleRecipeInput, CampfireCookingRecipe> cookCheck =
        RecipeManager.createCheck(RecipeType.CAMPFIRE_COOKING);

    public InductionHeaterBE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ModBlockEntities.INDUCTION_HEATER.get(),
            (be, side) -> be.plateHandler);
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide) {
            return;
        }

        float rpm = Math.abs(getSpeed());
        float cap = chargeCapFor(rpm);
        if (charge < cap) {
            charge = Math.min(cap, charge + rpm);
        } else if (charge > cap) {
            charge = Math.max(cap, charge - CHARGE_DECAY);
        }

        HeatLevel target = currentHeat();
        BlockState state = getBlockState();
        if (state.getValue(BlazeBurnerBlock.HEAT_LEVEL) != target) {
            level.setBlock(worldPosition, state.setValue(BlazeBurnerBlock.HEAT_LEVEL, target), Block.UPDATE_ALL);
            level.updateNeighbourForOutputSignal(worldPosition, state.getBlock());
        }

        if (target == HeatLevel.SEETHING) {
            wearCoil(1f);
        } else if (target == HeatLevel.KINDLED) {
            wearCoil(KINDLED_WEAR_FACTOR);
        }

        tickCooking(target);

        pushStress(false);

        // [2]
        int coilDamage = coil.isEmpty() ? -1 : InductionCoilItem.getWear(coil);
        if (level.getGameTime() % 10 == 0
                && (Math.abs(charge - lastSyncedCharge) > 256f || coilDamage != lastSyncedCoilDamage)) {
            lastSyncedCharge = charge;
            lastSyncedCoilDamage = coilDamage;
            sendData();
        }
    }

    private float chargeCapFor(float rpm) {
        if (rpm < RPM_WARM) return 0;
        // [3]
        if (!hasCoil()) return CHARGE_WARM;
        if (rpm < RPM_KINDLED) return CHARGE_WARM;
        if (rpm < RPM_SEETHING) return CHARGE_KINDLED;
        return CHARGE_SEETHING;
    }

    /** [4] */
    private HeatLevel currentHeat() {
        if (charge >= CHARGE_SEETHING && hasCoil()) return HeatLevel.SEETHING;
        if (charge >= CHARGE_KINDLED && hasCoil()) return HeatLevel.KINDLED;
        if (charge >= CHARGE_WARM) return HeatLevel.SMOULDERING;
        return HeatLevel.NONE;
    }

    /**
     * Damage the installed coil at a rate that stretches its full durability across the
     * configured lifetime, independent of what that durability number is.
     */
    private void wearCoil(float wearFactor) {
        if (coil.isEmpty()) {
            return;
        }
        int lifetimeTicks = Math.max(20, ModConfiguration.COMMON.inductionCoilLifetimeSeconds.get() * 20);
        coilWearAccumulator += wearFactor * (float) InductionCoilItem.MAX_WEAR / lifetimeTicks;
        if (coilWearAccumulator < 1f) {
            return;
        }
        int wearInc = (int) coilWearAccumulator;
        coilWearAccumulator -= wearInc;
        int newWear = InductionCoilItem.getWear(coil) + wearInc;
        if (newWear >= InductionCoilItem.MAX_WEAR) {
            coil = ItemStack.EMPTY;
            coilWearAccumulator = 0;
            if (level != null) {
                level.playSound(null, worldPosition, SoundEvents.ITEM_BREAK, SoundSource.BLOCKS, 0.8f, 0.8f);
            }
            setChanged();
            sendData();
        } else {
            InductionCoilItem.setWear(coil, newWear);
            setChanged();
        }
    }

    // cooking station

    /** [5] */
    private void tickCooking(HeatLevel heat) {
        if (cookStack.isEmpty() || cooked) {
            return;
        }
        float rate = switch (heat) {
            case SEETHING -> COOK_RATE_SEETHING;
            case FADING, KINDLED -> COOK_RATE_KINDLED;
            case SMOULDERING -> COOK_RATE_WARM;
            default -> 0f;
        };
        if (rate <= 0f) {
            return;
        }
        cookProgress += rate;
        float target = effectiveCookTicks();
        if (cookProgress < target) {
            if (level.getGameTime() % 20 == 0) {
                sendData();
            }
            return;
        }

        SingleRecipeInput input = new SingleRecipeInput(cookStack);
        Optional<RecipeHolder<CampfireCookingRecipe>> recipe = cookCheck.getRecipeFor(input, level);
        if (recipe.isPresent()) {
            ItemStack result = recipe.get().value().assemble(input, level.registryAccess());
            result.setCount(cookStack.getCount());
            cookStack = result;
        }
        cooked = true;
        cookProgress = target;
        // The "order's up" moment: a bell chime and a burst of steam so finishing a batch
        // is audible across the kitchen, since nothing is ejected to draw the eye.
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.playSound(null, worldPosition, SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.BLOCKS, 1.0f, 1.4f);
            serverLevel.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                worldPosition.getX() + 0.5, worldPosition.getY() + 0.9, worldPosition.getZ() + 0.5,
                8, 0.15, 0.05, 0.15, 0.004);
        }
        setChanged();
        sendData();
    }

    /** True when the held stack matches a campfire-cooking recipe (i.e. it is cookable raw food). */
    public boolean isCookableItem(ItemStack stack) {
        if (stack.isEmpty() || level == null) {
            return false;
        }
        return cookCheck.getRecipeFor(new SingleRecipeInput(stack), level).isPresent();
    }

    public boolean hasCookStack() {
        return !cookStack.isEmpty();
    }

    public ItemStack getCookStack() {
        return cookStack;
    }

    public boolean isCookDone() {
        return cooked && !cookStack.isEmpty();
    }

    /** [6] */
    private static float countScale(int count) {
        if (count <= 1) {
            return 1f;
        }
        return 1f + 3.65f * ((float) Math.sqrt(count) - 1f);
    }

    /** Total progress (in base-recipe ticks) the current plate needs, scaled by batch size. */
    private float effectiveCookTicks() {
        return cookTime * countScale(cookStack.getCount());
    }

    public float getCookProgressPercent() {
        if (cookStack.isEmpty() || cookTime <= 0) {
            return 0f;
        }
        return Mth.clamp(cookProgress / effectiveCookTicks() * 100f, 0f, 100f);
    }

    /** [7] */
    public int placeFood(ItemStack offered) {
        if (offered.isEmpty() || level == null) {
            return 0;
        }
        if (cookStack.isEmpty()) {
            Optional<RecipeHolder<CampfireCookingRecipe>> recipe =
                cookCheck.getRecipeFor(new SingleRecipeInput(offered), level);
            if (recipe.isEmpty()) {
                return 0;
            }
            int taken = Math.min(offered.getCount(), Math.min(64, offered.getMaxStackSize()));
            cookStack = offered.copyWithCount(taken);
            cookTime = recipe.get().value().getCookingTime();
            cookProgress = 0;
            cooked = false;
            setChanged();
            sendData();
            return taken;
        }
        if (cooked || !ItemStack.isSameItemSameComponents(cookStack, offered)) {
            return 0;
        }
        int room = Math.min(64, cookStack.getMaxStackSize()) - cookStack.getCount();
        if (room <= 0) {
            return 0;
        }
        int added = Math.min(room, offered.getCount());
        // [8]
        cookStack.grow(added);
        setChanged();
        sendData();
        return added;
    }

    /** Clears the plate and returns whatever was on it - raw mid-cook (progress is lost) or cooked. */
    public ItemStack takeFood() {
        ItemStack removed = cookStack;
        cookStack = ItemStack.EMPTY;
        cookProgress = 0;
        cookTime = 0;
        cooked = false;
        setChanged();
        sendData();
        return removed;
    }

    public void dropFood() {
        if (level != null && !level.isClientSide && !cookStack.isEmpty()) {
            Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), cookStack);
            cookStack = ItemStack.EMPTY;
            cookProgress = 0;
            cookTime = 0;
            cooked = false;
        }
    }

    /** [9] */
    private void pushStress(boolean force) {
        if (level == null || level.isClientSide || !hasNetwork() || pushingStress) {
            return;
        }
        float current = calculateStressApplied();
        if (force || Float.isNaN(lastPushedStress) || Math.abs(current - lastPushedStress) > STRESS_PUSH_EPS) {
            pushingStress = true;
            try {
                getOrCreateNetwork().updateStressFor(this, current);
                lastPushedStress = current;
            } finally {
                pushingStress = false;
            }
        }
    }

    @Override
    public float calculateStressApplied() {
        float impact = Math.abs(getSpeed()) >= RPM_WARM
            ? (float) (double) ModConfiguration.COMMON.inductionHeaterStressImpact.get()
            : 0f;
        this.lastStressApplied = impact;
        return impact;
    }

    @Override
    public void onSpeedChanged(float previousSpeed) {
        super.onSpeedChanged(previousSpeed);
        pushStress(true);
    }

    // coil access

    public boolean isCoilItem(ItemStack stack) {
        return stack.getItem() instanceof InductionCoilItem;
    }

    public boolean hasCoil() {
        return !coil.isEmpty();
    }

    public void insertCoil(ItemStack stack) {
        coil = stack;
        coilWearAccumulator = 0;
        setChanged();
        sendData();
    }

    public ItemStack extractCoil() {
        ItemStack removed = coil;
        coil = ItemStack.EMPTY;
        coilWearAccumulator = 0;
        setChanged();
        sendData();
        return removed;
    }

    public void dropCoil() {
        if (level != null && !level.isClientSide && !coil.isEmpty()) {
            Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), coil);
            coil = ItemStack.EMPTY;
        }
    }

    // telemetry (goggles + display source)

    public HeatLevel getHeat() {
        return getBlockState().getValue(BlazeBurnerBlock.HEAT_LEVEL);
    }

    public float getChargePercent() {
        return Mth.clamp(charge / CHARGE_SEETHING * 100f, 0f, 100f);
    }

    public float getCurrentDrawSu() {
        return lastStressApplied * Math.abs(getSpeed());
    }

    public float getCoilHealthPercent() {
        if (coil.isEmpty()) {
            return 0f;
        }
        return 100f * (InductionCoilItem.MAX_WEAR - InductionCoilItem.getWear(coil)) / InductionCoilItem.MAX_WEAR;
    }

    /** Coarse heat tier for telemetry: 0 idle, 1 warm, 2 kindled, 3 superheated. */
    public int getHeatTierOrdinal() {
        return switch (getHeat()) {
            case SEETHING -> 3;
            case KINDLED, FADING -> 2;
            case SMOULDERING -> 1;
            default -> 0;
        };
    }

    /** [10] */
    public float getCoilSecondsRemaining() {
        if (coil.isEmpty()) {
            return 0f;
        }
        final float wearFactor = switch (getHeat()) {
            case SEETHING -> 1f;
            case KINDLED, FADING -> KINDLED_WEAR_FACTOR;
            default -> 0f;
        };
        if (wearFactor <= 0f) {
            return -1f;
        }
        final int lifetimeSec = Math.max(1, ModConfiguration.COMMON.inductionCoilLifetimeSeconds.get());
        final float healthFrac = (float) (InductionCoilItem.MAX_WEAR - InductionCoilItem.getWear(coil)) / InductionCoilItem.MAX_WEAR;
        return Math.max(0f, healthFrac) * lifetimeSec / wearFactor;
    }

    /** [11] */
    public float getCookSecondsRemaining() {
        if (cookStack.isEmpty() || cooked) {
            return 0f;
        }
        final float rate = switch (getHeat()) {
            case SEETHING -> COOK_RATE_SEETHING;
            case FADING, KINDLED -> COOK_RATE_KINDLED;
            case SMOULDERING -> COOK_RATE_WARM;
            default -> 0f;
        };
        if (rate <= 0f) {
            return -1f;
        }
        final float remainingBase = effectiveCookTicks() - cookProgress;
        if (remainingBase <= 0f) {
            return 0f;
        }
        return remainingBase / rate / 20f;
    }

    // persistence

    @Override
    protected void write(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(compound, registries, clientPacket);
        compound.putFloat("HeatCharge", charge);
        if (!coil.isEmpty()) {
            compound.put("Coil", coil.save(registries));
        }
        if (!cookStack.isEmpty()) {
            compound.put("CookStack", cookStack.save(registries));
            compound.putFloat("CookProgress", cookProgress);
            compound.putInt("CookTime", cookTime);
            compound.putBoolean("Cooked", cooked);
        }
    }

    @Override
    protected void read(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(compound, registries, clientPacket);
        charge = compound.getFloat("HeatCharge");
        coil = compound.contains("Coil")
            ? ItemStack.parseOptional(registries, compound.getCompound("Coil"))
            : ItemStack.EMPTY;
        if (compound.contains("CookStack")) {
            cookStack = ItemStack.parseOptional(registries, compound.getCompound("CookStack"));
            cookProgress = compound.getFloat("CookProgress");
            cookTime = compound.getInt("CookTime");
            cooked = compound.getBoolean("Cooked");
        } else {
            cookStack = ItemStack.EMPTY;
            cookProgress = 0;
            cookTime = 0;
            cooked = false;
        }
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        super.addToGoggleTooltip(tooltip, isPlayerSneaking);
        HeatLevel heat = getHeat();

        tooltip.add(Component.literal(" ").append(
            Component.translatable("block.create_stats.induction_heater.goggle.title")
                .withStyle(ChatFormatting.GRAY)));

        ChatFormatting heatColor = switch (heat) {
            case SEETHING -> ChatFormatting.AQUA;
            case KINDLED, FADING -> ChatFormatting.GOLD;
            case SMOULDERING -> ChatFormatting.RED;
            default -> ChatFormatting.DARK_GRAY;
        };
        tooltip.add(Component.literal("    ").append(
            Component.translatable("block.create_stats.induction_heater.goggle.heat." + heat.getSerializedName())
                .withStyle(heatColor)));

        float chargePct = getChargePercent();
        int filled = Mth.clamp(Math.round(chargePct / 10f), 0, 10);
        StringBuilder bar = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            bar.append(i < filled ? '■' : '□');
        }
        tooltip.add(Component.literal("    ")
            .append(Component.literal(bar.toString()).withStyle(
                chargePct >= 100f ? ChatFormatting.AQUA : chargePct >= 50f ? ChatFormatting.GOLD : ChatFormatting.RED))
            .append(Component.literal(String.format(" %.0f%%", chargePct)).withStyle(ChatFormatting.GRAY)));

        if (hasCoil()) {
            tooltip.add(Component.literal("    ").append(
                Component.translatable("block.create_stats.induction_heater.goggle.coil",
                    String.format("%.0f%%", getCoilHealthPercent()))
                    .withStyle(ChatFormatting.LIGHT_PURPLE)));
        } else {
            tooltip.add(Component.literal("    ").append(
                Component.translatable("block.create_stats.induction_heater.goggle.no_coil")
                    .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC)));
        }

        if (hasCookStack()) {
            if (isCookDone()) {
                tooltip.add(Component.literal("    ").append(
                    Component.translatable("block.create_stats.induction_heater.goggle.cook_done",
                        cookStack.getCount(), cookStack.getHoverName())
                        .withStyle(ChatFormatting.GREEN)));
            } else {
                tooltip.add(Component.literal("    ").append(
                    Component.translatable("block.create_stats.induction_heater.goggle.cooking",
                        cookStack.getCount(), cookStack.getHoverName(),
                        String.format("%.0f%%", getCookProgressPercent()))
                        .withStyle(heat.isAtLeast(HeatLevel.FADING) ? ChatFormatting.GOLD : ChatFormatting.DARK_GRAY)));
            }
        }
        return true;
    }

    /** [12] */
    private final IItemHandler plateHandler = new IItemHandler() {
        private static final int SLOT_COIL = 0;
        private static final int SLOT_FOOD = 1;

        @Override
        public int getSlots() {
            return 2;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            // Coil stays hidden (never steal-able); food is visible only once cooked so
            // extraction logic in hoppers sees exactly what it is allowed to take.
            return slot == SLOT_FOOD && isCookDone() ? cookStack : ItemStack.EMPTY;
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (slot == SLOT_COIL) {
                if (hasCoil() || !isCoilItem(stack)) {
                    return stack;
                }
                if (!simulate) {
                    ItemStack single = stack.copy();
                    insertCoil(single.split(1));
                }
                ItemStack remainder = stack.copy();
                remainder.shrink(1);
                return remainder;
            }
            if (slot == SLOT_FOOD) {
                if (!isCookableItem(stack)) {
                    return stack;
                }
                if (simulate) {
                    // Mirror placeFood's acceptance rules without mutating state.
                    int accepted;
                    if (cookStack.isEmpty()) {
                        accepted = Math.min(stack.getCount(), Math.min(64, stack.getMaxStackSize()));
                    } else if (cooked || !ItemStack.isSameItemSameComponents(cookStack, stack)) {
                        accepted = 0;
                    } else {
                        accepted = Math.min(Math.min(64, cookStack.getMaxStackSize()) - cookStack.getCount(), stack.getCount());
                    }
                    if (accepted <= 0) {
                        return stack;
                    }
                    ItemStack remainder = stack.copy();
                    remainder.shrink(accepted);
                    return remainder;
                }
                int accepted = placeFood(stack);
                if (accepted <= 0) {
                    return stack;
                }
                ItemStack remainder = stack.copy();
                remainder.shrink(accepted);
                return remainder;
            }
            return stack;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (slot != SLOT_FOOD || !isCookDone() || amount <= 0) {
                return ItemStack.EMPTY;
            }
            int taken = Math.min(amount, cookStack.getCount());
            if (simulate) {
                return cookStack.copyWithCount(taken);
            }
            ItemStack out = cookStack.split(taken);
            if (cookStack.isEmpty()) {
                takeFood(); // resets progress/cooked bookkeeping
            } else {
                setChanged();
                sendData();
            }
            return out;
        }

        @Override
        public int getSlotLimit(int slot) {
            return slot == SLOT_COIL ? 1 : 64;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return slot == SLOT_COIL ? isCoilItem(stack) : isCookableItem(stack);
        }
    };
}
