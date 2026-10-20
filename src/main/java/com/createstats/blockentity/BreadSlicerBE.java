package com.createstats.blockentity;

import com.createstats.block.BreadSlicerBlock;
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
import com.createstats.recipe.SlicingRecipe;
import com.createstats.recipe.SlicingRecipeInput;
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
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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

// one blade, the bread moves
public class BreadSlicerBE extends BlockEntity implements FeGridMember, FeManagedLoad,
        FeWireTerminal, Adjustable, com.createstats.power.Gateable,
        com.createstats.power.HomeAppliance,
        com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation {
    public static final int[] STROKES = {4, 6, 8};
    public static final int DEFAULT_NOTCH = 1;

    public static final int PASS_TICKS = 160;

    public static final int DRAW_BASE = 20;

    public static final int DRAW_IDLE = 2;

    public static final int BUFFER = 8_000;

    public static final int OUTPUT_CAP = 32;

    private static final int HOOD_TICKS = 9;
    private static final int PRESS_TICKS = 4;
    private static final int LEVER_TICKS = 5;

    public static final float APPROACH = 0.52f;

    public static final float CUT_SPAN = 4.00f;
    public static final float TRAVEL = APPROACH + CUT_SPAN;

    public static final float RISE_START = 0.40f;
    public static final float RISE_END = 0.78f;

    public enum Refusal {
        NONE,

        SHED,

        GATED,

        HOOD,

        EMPTY,

        FULL,

        NO_POWER,

        IDLE
    }

    private ItemStack loaf = ItemStack.EMPTY;
    private ItemStack output = ItemStack.EMPTY;

    private int notch = DEFAULT_NOTCH;
    private long leverTime = Long.MIN_VALUE;

    private boolean hoodOpen;
    private long hoodTime = Long.MIN_VALUE;

    private boolean running;
    private int progress;

    private int soundedStrokes;

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

    private final FeManagedState managed = new FeManagedState();
    private final List<BlockPos> links = new ArrayList<>();
    private final Map<Long, WireTier> linkTiers = new HashMap<>();

    @Nullable
    private FeWireNetwork network;

    public BreadSlicerBE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    private Direction facing() {
        final BlockState state = getBlockState();
        return state.hasProperty(BreadSlicerBlock.FACING)
                ? state.getValue(BreadSlicerBlock.FACING) : Direction.NORTH;
    }

    public Direction backFace() {
        return facing().getOpposite();
    }

    public boolean isHoodOpen() {
        return hoodOpen;
    }

    public boolean isRunning() {
        return running;
    }

    public int notch() {
        return notch;
    }

    public int strokes() {
        return STROKES[Mth.clamp(notch, 0, STROKES.length - 1)];
    }

    public ItemStack loaf() {
        return loaf;
    }

    public ItemStack output() {
        return output;
    }

    public int progress() {
        return progress;
    }

    public int remainingTicks() {
        return running ? Math.max(0, PASS_TICKS - progress) : 0;
    }

    public int draw() {
        return DRAW_BASE << Mth.clamp(notch, 0, STROKES.length - 1);
    }

    public int drawPerSlice() {
        return Math.round(draw() * PASS_TICKS / (float) strokes());
    }

    public Optional<SlicingRecipe> recipeFor(ItemStack stack) {
        if (level == null || stack.isEmpty()) {
            return Optional.empty();
        }
        return level.getRecipeManager()
                .getRecipeFor(ModRecipes.SLICING_TYPE.get(), new SlicingRecipeInput(stack), level)
                .map(holder -> holder.value());
    }

    public boolean sliceable(ItemStack stack) {
        return recipeFor(stack).isPresent();
    }

    // lid up and stopped only. a loaf into a running slicer is a hand into a running slicer
    public ItemStack loadFromHand(ItemStack held) {
        if (held.isEmpty() || running || !hoodOpen || !loaf.isEmpty() || !sliceable(held)) {
            return held;
        }
        loaf = held.copyWithCount(1);
        sync();
        return held.copyWithCount(held.getCount() - 1);
    }

    public ItemStack unloadDeck() {
        if (loaf.isEmpty()) {
            return ItemStack.EMPTY;
        }
        final ItemStack out = loaf;
        loaf = ItemStack.EMPTY;

        stop();
        sync();
        return out;
    }

    public ItemStack collect() {
        if (output.isEmpty()) {
            return ItemStack.EMPTY;
        }
        final ItemStack out = output;
        output = ItemStack.EMPTY;
        sync();
        return out;
    }

    public void setHoodOpen(boolean open) {
        if (hoodOpen == open || level == null) {
            return;
        }
        hoodOpen = open;
        hoodTime = level.getGameTime();
        if (!level.isClientSide()) {
            level.playSound(null, worldPosition,
                    open ? SoundEvents.IRON_TRAPDOOR_OPEN : SoundEvents.IRON_TRAPDOOR_CLOSE,
                    SoundSource.BLOCKS, 0.42f, open ? 1.35f : 1.25f);
            sync();
        }
    }

    public Refusal start() {
        markPressed(0);
        if (running) {
            return Refusal.NONE;
        }
        final Refusal why = checkRun();
        if (why != Refusal.NONE && why != Refusal.IDLE) {
            return why;
        }
        running = true;
        soundedStrokes = 0;
        if (level != null && !level.isClientSide()) {
            level.playSound(null, worldPosition, SoundEvents.STONE_BUTTON_CLICK_ON,
                    SoundSource.BLOCKS, 0.5f, 0.9f);
        }
        sync();
        return Refusal.NONE;
    }

    public void stop() {
        markPressed(1);
        if (!running) {
            return;
        }
        running = false;
        if (level != null && !level.isClientSide()) {
            level.playSound(null, worldPosition, SoundEvents.STONE_BUTTON_CLICK_OFF,
                    SoundSource.BLOCKS, 0.5f, 0.8f);
        }
        sync();
    }

    public boolean nudgeLever() {
        if (running || level == null) {
            return false;
        }
        notch = (notch + 1) % STROKES.length;
        leverTime = level.getGameTime();
        if (!level.isClientSide()) {
            level.playSound(null, worldPosition, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS,
                    0.4f, 1.1f);
        }
        sync();
        return true;
    }

    public void markPressed(int button) {
        if (level == null) {
            return;
        }
        pressed = button;
        pressTime = level.getGameTime();
        if (!level.isClientSide()) {
            sync();
        } else {
            clientPressTime = level.getGameTime();
        }
    }

    public int pressedButton() {
        if (level == null || pressed < 0) {
            return -1;
        }
        final long shown = level.isClientSide() ? clientPressTime : pressTime;
        return shown != Long.MIN_VALUE && level.getGameTime() - shown < PRESS_TICKS ? pressed : -1;
    }

    public void onRedstone(boolean powered) {
        if (level == null || level.isClientSide()) {
            return;
        }
        if (powered && !lastRedstone) {
            start();
        }
        if (lastRedstone != powered) {
            lastRedstone = powered;
            setChanged();
        }
    }

    public void adoptRedstone(boolean powered) {
        lastRedstone = powered;
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
        advance();
        heartbeat();
    }

    private void payPower() {
        final Refusal why = checkRun();

        final boolean dead = why == Refusal.SHED || why == Refusal.GATED;
        final boolean cutting = running && why == Refusal.NONE;
        final int want = dead ? 0 : (cutting ? draw() : DRAW_IDLE);

        final Refusal rated = ratedRun();
        ratedDraw = rated == Refusal.SHED || rated == Refusal.GATED ? 0
                : rated == Refusal.NONE || rated == Refusal.NO_POWER ? draw() : DRAW_IDLE;
        if (want == 0 || energy < want) {
            measuredDraw = 0;
            setLit(false);
            return;
        }
        energy -= want;
        measuredDraw = want;
        setLit(cutting);
        setChanged();
    }

    public Refusal checkRun() {
        return checkRun(false);
    }

    private Refusal ratedRun() {
        return checkRun(true);
    }

    private Refusal checkRun(boolean ignoreAutoShed) {
        if (managed.isForcedOff() || (!ignoreAutoShed && managed.isSuppressed())) {
            return Refusal.SHED;
        }
        if (gated) {
            return Refusal.GATED;
        }
        if (hoodOpen) {
            return Refusal.HOOD;
        }
        if (loaf.isEmpty() || !sliceable(loaf)) {
            return Refusal.EMPTY;
        }
        if (!hasRoomForYield()) {
            return Refusal.FULL;
        }
        if (energy < draw()) {
            return Refusal.NO_POWER;
        }
        return running ? Refusal.NONE : Refusal.IDLE;
    }

    private boolean hasRoomForYield() {
        final Optional<SlicingRecipe> recipe = recipeFor(loaf);
        if (recipe.isEmpty()) {
            return false;
        }
        final ItemStack out = recipe.get().yield(strokes());
        if (output.isEmpty()) {
            return out.getCount() <= OUTPUT_CAP;
        }
        return ItemStack.isSameItemSameComponents(output, out)
                && output.getCount() + out.getCount() <= OUTPUT_CAP;
    }

    private void advance() {
        if (!running) {
            return;
        }
        if (checkRun() != Refusal.NONE) {
            return;
        }
        progress++;
        announceStroke();
        if (progress < PASS_TICKS) {
            setChanged();
            return;
        }
        finishPass();
    }

    private void announceStroke() {
        final int done = strokesCut();
        if (done <= soundedStrokes || level == null) {
            return;
        }
        soundedStrokes = done;
        level.playSound(null, worldPosition, SoundEvents.SHEEP_SHEAR, SoundSource.BLOCKS,
                0.22f, 1.45f + (done % 3) * 0.06f);
    }

    private void finishPass() {
        final Optional<SlicingRecipe> recipe = recipeFor(loaf);
        running = false;
        progress = 0;
        soundedStrokes = 0;
        if (recipe.isPresent()) {
            final ItemStack out = recipe.get().yield(strokes());
            if (output.isEmpty()) {
                output = out;
            } else {
                output.grow(out.getCount());
            }
        }
        loaf = ItemStack.EMPTY;
        if (level != null) {
            level.playSound(null, worldPosition, SoundEvents.NOTE_BLOCK_HAT.value(),
                    SoundSource.BLOCKS, 0.45f, 1.2f);
        }
        sync();
    }

    private void heartbeat() {
        if (level == null || !running) {
            return;
        }
        if ((level.getGameTime() & 0x1F) == 0L) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(),
                    Block.UPDATE_CLIENTS);
        }
    }

    public float feed(float partialTick) {
        if (!running) {
            return progress <= 0 ? 0.0f : Mth.clamp(progress / (float) PASS_TICKS, 0.0f, 1.0f);
        }
        return Mth.clamp((progress + partialTick) / PASS_TICKS, 0.0f, 1.0f);
    }

    public float strokePhase(float partialTick) {
        final float u = (feed(partialTick) * TRAVEL - APPROACH) / CUT_SPAN * strokes();
        return u - Mth.floor(u);
    }

    public float cutFraction(float partialTick) {
        return Mth.clamp((feed(partialTick) * TRAVEL - APPROACH) / CUT_SPAN, 0.0f, 1.0f);
    }

    public float fedPitches(float partialTick) {
        final int count = strokes();
        final float s = cutFraction(partialTick) * count;
        final int done = Mth.floor(s);
        if (done >= count) {
            return count;
        }
        final float phase = s - done;
        return done + (phase >= RISE_START ? 1.0f : ease(phase / RISE_START));
    }

    public float travel(float partialTick) {
        final float straight = feed(partialTick) * TRAVEL;
        if (straight <= APPROACH) {
            return straight;
        }
        return APPROACH + fedPitches(partialTick) / strokes() * CUT_SPAN;
    }

    public int strokesCut(float partialTick) {
        final int count = strokes();
        final float s = cutFraction(partialTick) * count;
        final int done = Mth.floor(s);
        if (done >= count) {
            return count;
        }
        return s - done >= RISE_END ? done + 1 : done;
    }

    public int strokesCut() {
        return strokesCut(0.0f);
    }

    private static float ease(float t) {
        return (1.0f - Mth.cos(Mth.clamp(t, 0.0f, 1.0f) * Mth.PI)) * 0.5f;
    }

    public float hood(float partialTick) {
        if (level == null || hoodTime == Long.MIN_VALUE) {
            return hoodOpen ? 1.0f : 0.0f;
        }
        final float t = Mth.clamp((level.getGameTime() - hoodTime + partialTick) / HOOD_TICKS,
                0.0f, 1.0f);
        return hoodOpen ? t : 1.0f - t;
    }

    public float leverPosition(float partialTick) {
        if (level == null || leverTime == Long.MIN_VALUE) {
            return notch;
        }
        final float t = Mth.clamp((level.getGameTime() - leverTime + partialTick) / LEVER_TICKS,
                0.0f, 1.0f);
        final int from = (notch + STROKES.length - 1) % STROKES.length;

        return from + (notch - from) * t;
    }

    public boolean isCutting() {
        return running && checkRun() == Refusal.NONE;
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

    private final class DeckHandler implements IItemHandler {
        private final boolean insert;

        private DeckHandler(boolean insert) {
            this.insert = insert;
        }

        @Override
        public int getSlots() {
            return 2;
        }

        @Override
        public @NotNull ItemStack getStackInSlot(int slot) {
            return slot == 0 ? loaf : slot == 1 ? output : ItemStack.EMPTY;
        }

        @Override
        public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
            if (!insert || slot != 0 || running || hoodOpen || stack.isEmpty()
                    || !loaf.isEmpty() || !sliceable(stack)) {
                return stack;
            }
            if (!simulate) {
                loaf = stack.copyWithCount(1);
                sync();
            }
            return stack.copyWithCount(stack.getCount() - 1);
        }

        @Override
        public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (insert || slot != 1 || amount <= 0 || running || output.isEmpty()) {
                return ItemStack.EMPTY;
            }
            final int taken = Math.min(amount, output.getCount());
            final ItemStack out = output.copyWithCount(taken);
            if (!simulate) {
                output.shrink(taken);
                if (output.isEmpty()) {
                    output = ItemStack.EMPTY;
                }
                sync();
            }
            return out;
        }

        @Override
        public int getSlotLimit(int slot) {
            return slot == 0 ? 1 : OUTPUT_CAP;
        }

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            return insert && slot == 0 && sliceable(stack);
        }
    }

    private final IItemHandler inputHandle = new DeckHandler(true);
    private final IItemHandler outputHandle = new DeckHandler(false);

    public static void registerCapabilities(
            net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK,
                com.createstats.registry.ModBlockEntities.BREAD_SLICER.get(),
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
        return (DRAW_BASE << (STROKES.length - 1)) * 4;
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
    public void setGated(boolean value) {
        if (gated == value) {
            return;
        }
        gated = value;
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
        if (running || (progress > 0 && !loaf.isEmpty())) {
            final int left = Math.max(0, PASS_TICKS - progress);
            final Component text;
            if (!running) {
                text = Component.translatable("status.create_stats.home.stopped",
                        ApplianceStatus.clock(left));
            } else {
                text = switch (checkRun()) {
                    case HOOD -> Component.translatable("status.create_stats.home.paused_lid");
                    case NO_POWER -> Component.translatable("status.create_stats.home.paused_power");
                    case SHED -> Component.translatable("status.create_stats.home.paused_pdu");
                    case GATED -> Component.translatable("status.create_stats.home.paused_off");
                    case FULL -> Component.translatable("status.create_stats.home.paused_full");
                    default -> Component.translatable("status.create_stats.home.slicing",
                            ApplianceStatus.clock(left));
                };
            }
            return ApplianceStatus.running(text, PASS_TICKS, left,
                    Component.translatable("status.create_stats.home.slicer_done"));
        }
        if (isGated() || isManagedShed()
                || (ratedDrawFePerTick() > 0 && measuredDrawFePerTick() <= 0)) {
            return null;
        }
        if (managed.isSuppressed()) {
            return ApplianceStatus.idle(Component.translatable("status.create_stats.home.held_pdu"));
        }
        if (!output.isEmpty()) {
            return ApplianceStatus.idle(Component.translatable("status.create_stats.home.done_count",
                    output.getCount()));
        }
        if (hoodOpen) {
            return ApplianceStatus.idle(Component.translatable("status.create_stats.home.lid_open"));
        }
        if (!loaf.isEmpty()) {
            return ApplianceStatus.idle(Component.translatable("status.create_stats.home.loaded"));
        }
        return ApplianceStatus.idle(Component.translatable("status.create_stats.home.empty"));
    }

    @Override
    public int getMode() {
        return notch;
    }

    @Override
    public void setMode(int mode) {
        final int wanted = Mth.clamp(mode, 0, STROKES.length - 1);
        if (wanted == notch || running) {
            return;
        }
        notch = wanted;
        if (level != null) {
            leverTime = level.getGameTime();
        }
        sync();
    }

    @Override
    public List<Component> modeLabels() {
        final List<Component> out = new ArrayList<>(STROKES.length);
        for (int i = 0; i < STROKES.length; i++) {
            out.add(Component.translatable("create_stats.bread_slicer.notch." + i));
        }
        return out;
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        final Refusal blocking = checkRun();
        tooltip.add(pad(Component.literal("Bread Slicer").withStyle(ChatFormatting.GRAY)));

        step(tooltip, 1, "Mains", mainsSummary(), energy >= draw(),
                blocking == Refusal.NO_POWER || blocking == Refusal.SHED
                        || blocking == Refusal.GATED);
        step(tooltip, 2, "Deck", deckSummary(), !loaf.isEmpty(),
                blocking == Refusal.EMPTY || blocking == Refusal.HOOD || blocking == Refusal.FULL);
        step(tooltip, 3, "Thickness", String.format(Locale.ROOT, "%d slices, %d FE/t, %,d FE a slice",
                        strokes(), draw(), drawPerSlice()), true, false);

        tooltip.add(pad(Component.literal(nextAction(blocking)).withStyle(actionColour(blocking))));
        return true;
    }

    private String mainsSummary() {
        if (!isWired()) {
            return "not wired: cable or wire to any side, or a cord from a socket";
        }
        if (energy <= 0 && !hasSupply()) {
            return "wired, but the grid has nothing to give";
        }
        return String.format(Locale.ROOT, "%,d / %,d FE  (%d/t)", energy, BUFFER, ratedDraw);
    }

    private String deckSummary() {
        final StringBuilder sb = new StringBuilder();
        sb.append(loaf.isEmpty() ? "empty" : loaf.getHoverName().getString());
        if (!output.isEmpty()) {
            sb.append(", ").append(output.getCount()).append(" waiting");
        }
        if (hoodOpen) {
            sb.append(", lid up");
        }
        return sb.toString();
    }

    private String nextAction(Refusal blocking) {
        if (isCutting()) {
            return String.format(Locale.ROOT, "Slicing, %d of %d, %s to go",
                    strokesCut(), strokes(), clock(remainingTicks()));
        }
        if (running) {
            return switch (blocking) {
                case HOOD -> "Held: close the lid";
                case NO_POWER -> isWired() ? "Held: waiting for charge"
                        : "Held: nothing wired to the studs on the back";
                case SHED -> "Held: shed by the Power Distribution Unit";
                case GATED -> "Held: switched off from a panel";
                case FULL -> "Held: take the slices off the delivery end";
                default -> "Held.";
            };
        }
        return switch (blocking) {
            case HOOD -> loaf.isEmpty() ? "Put a loaf on the deck" : "Close the lid";
            case EMPTY -> "Open the lid and put a loaf in";
            case FULL -> "Take the slices off the delivery end";
            case NO_POWER, SHED, GATED -> "Not powered";
            default -> "Press the green button";
        };
    }

    private ChatFormatting actionColour(Refusal blocking) {
        if (isCutting()) {
            return ChatFormatting.AQUA;
        }
        if (running) {
            return ChatFormatting.GOLD;
        }
        return blocking == Refusal.IDLE ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY;
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

    public boolean hasSupply() {
        return energy > 0 || (network != null && network.lastResult().available() > 0);
    }

    public boolean isWired() {
        return !links.isEmpty() || (network != null && network.size() > 1) || isFed();
    }

    private boolean isFed() {
        return level != null && fedAt != Long.MIN_VALUE && level.getGameTime() - fedAt <= 40L;
    }

    private void setLit(boolean value) {
        if (level == null || level.isClientSide()) {
            return;
        }
        final BlockState state = getBlockState();
        if (state.hasProperty(BreadSlicerBlock.LIT) && state.getValue(BreadSlicerBlock.LIT) != value) {
            level.setBlock(worldPosition, state.setValue(BreadSlicerBlock.LIT, value),
                    Block.UPDATE_ALL);
        }
    }

    public int comparatorSignal() {
        if (output.isEmpty()) {
            return 0;
        }
        return Math.max(1, Math.round(output.getCount() * 15.0f / OUTPUT_CAP));
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

    public List<ItemStack> dropContents() {
        final List<ItemStack> out = new ArrayList<>(2);
        if (!loaf.isEmpty()) {
            out.add(loaf.copy());
        }
        if (!output.isEmpty()) {
            out.add(output.copy());
        }
        return out;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!loaf.isEmpty()) {
            tag.put("Loaf", loaf.save(registries));
        }
        if (!output.isEmpty()) {
            tag.put("Output", output.save(registries));
        }
        tag.putInt("Notch", notch);
        tag.putLong("LeverTime", leverTime);
        tag.putBoolean("HoodOpen", hoodOpen);
        tag.putLong("HoodTime", hoodTime);
        tag.putBoolean("Running", running);
        tag.putInt("Progress", progress);
        tag.putInt("Sounded", soundedStrokes);
        tag.putInt("Pressed", pressed);
        tag.putLong("PressTime", pressTime);
        tag.putBoolean("LastRedstone", lastRedstone);
        tag.putBoolean("Gated", gated);
        tag.putInt("Energy", energy);
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
        loaf = tag.contains("Loaf")
                ? ItemStack.parseOptional(registries, tag.getCompound("Loaf")) : ItemStack.EMPTY;
        output = tag.contains("Output")
                ? ItemStack.parseOptional(registries, tag.getCompound("Output")) : ItemStack.EMPTY;
        notch = Mth.clamp(tag.getInt("Notch"), 0, STROKES.length - 1);
        leverTime = tag.contains("LeverTime") ? tag.getLong("LeverTime") : Long.MIN_VALUE;
        hoodOpen = tag.getBoolean("HoodOpen");
        hoodTime = tag.contains("HoodTime") ? tag.getLong("HoodTime") : Long.MIN_VALUE;
        running = tag.getBoolean("Running");
        progress = Mth.clamp(tag.getInt("Progress"), 0, PASS_TICKS);
        soundedStrokes = Math.max(0, tag.getInt("Sounded"));
        lastRedstone = tag.getBoolean("LastRedstone");
        gated = tag.getBoolean("Gated");
        energy = Mth.clamp(tag.getInt("Energy"), 0, BUFFER);
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
