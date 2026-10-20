package com.createstats.blockentity;

import com.createstats.block.ToasterBlock;
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
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;
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

// two slices one lever
public class ToasterBE extends BlockEntity implements FeGridMember, FeManagedLoad, FeWireTerminal,
        Adjustable, com.createstats.power.Gateable, com.createstats.power.HomeAppliance,
        com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation,
        com.createstats.util.CopyableSettings {
    public static final int SLOTS = 2;

    public static final int STAGES = 6;

    public static final int DETENTS = 7;

    public static final int DRAW_TOASTING = 80;

    public static final int DRAW_BAGEL = DRAW_TOASTING / 2;

    public static final int DRAW_IDLE = 2;

    public static final int BUFFER = 4_000;

    private static final int CYCLE_BASE = 50;
    private static final int CYCLE_STEP = 16;

    private static final int DOWN_TICKS = 5;
    private static final int POP_TICKS = 16;
    private static final int DIAL_TICKS = 6;
    private static final int TRAY_TICKS = 8;
    private static final int PRESS_TICKS = 4;

    private static final double SPRING_DAMP = 3.6;
    private static final double SPRING_RATE = 7.2;

    private static final float HOP = 1.0f;
    private static final float TILT = 7.0f;

    private static final float HEAT_UP = 0.10f;
    private static final float HEAT_DOWN = 0.035f;

    private final ItemStack[] slots = {ItemStack.EMPTY, ItemStack.EMPTY};
    private final int[] stage = new int[SLOTS];

    private final boolean[] done = new boolean[SLOTS];

    private int detent = 3;
    private int dialFrom = 3;
    private long dialTime = Long.MIN_VALUE;

    private boolean bagel;
    private boolean down;
    private long leverTime = Long.MIN_VALUE;
    private int progress;
    private int cycle;

    private boolean heating;

    private boolean trayOpen;
    private long trayTime = Long.MIN_VALUE;

    private int pressed = -1;
    private long pressTime = Long.MIN_VALUE;
    private long clientPressTime = Long.MIN_VALUE;

    private boolean lastRedstone;

    private boolean gated;

    private int energy;

    private long fedAt = Long.MIN_VALUE;
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

    private float heat;
    private float heatPrev;

    public ToasterBE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    private Direction facing() {
        final BlockState state = getBlockState();
        return state.hasProperty(ToasterBlock.FACING)
                ? state.getValue(ToasterBlock.FACING) : Direction.NORTH;
    }

    public Direction backFace() {
        return facing().getOpposite();
    }

    public enum Refusal {
        NONE, EMPTY, IDLE, RUNNING, TRAY_OUT, SLOT_FULL, WRONG_ITEM, SLOT_EMPTY, NO_POWER, SHED,
        GATED;

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public boolean isDown() {
        return down;
    }

    public boolean isHeating() {
        return heating;
    }

    public boolean isBagel() {
        return bagel;
    }

    public boolean isTrayOpen() {
        return trayOpen;
    }

    public int detent() {
        return detent;
    }

    public ItemStack slot(int index) {
        return slots[Mth.clamp(index, 0, SLOTS - 1)];
    }

    public int sliceStage(int index) {
        return stage[Mth.clamp(index, 0, SLOTS - 1)];
    }

    public boolean loaded() {
        return !slots[0].isEmpty() || !slots[1].isEmpty();
    }

    public int finishedCount() {
        int n = 0;
        for (int i = 0; i < SLOTS; i++) {
            if (done[i] && !slots[i].isEmpty()) {
                n++;
            }
        }
        return n;
    }

    public int targetStage() {
        return 1 + Math.round(detent * 4.0f / (DETENTS - 1));
    }

    public int cycleTicks() {
        return CYCLE_BASE + detent * CYCLE_STEP;
    }

    public int toastingDraw() {
        return bagel ? DRAW_BAGEL : DRAW_TOASTING;
    }

    public int remainingTicks() {
        return down ? Math.max(0, cycle - progress) : -1;
    }

    public Refusal pushDown() {
        if (down) {
            return Refusal.RUNNING;
        }
        if (trayOpen) {
            return Refusal.TRAY_OUT;
        }
        if (!loaded()) {
            return Refusal.EMPTY;
        }
        down = true;
        progress = 0;
        cycle = cycleTicks();
        leverTime = level == null ? 0L : level.getGameTime();
        for (int i = 0; i < SLOTS; i++) {
            done[i] = false;
        }
        sync();
        return Refusal.NONE;
    }

    public void pop() {
        if (!down) {
            return;
        }
        down = false;
        setHeating(false);
        leverTime = level == null ? 0L : level.getGameTime();
        for (int i = 0; i < SLOTS; i++) {
            if (!slots[i].isEmpty() && stage[i] > 0) {
                slots[i] = toast(stage[i]);
                done[i] = true;
            }
        }
        progress = 0;
        sync();
    }

    public static ItemStack toast(int browning) {
        final ItemStack out = new ItemStack(ModItems.TOAST.get());
        out.set(DataComponents.CUSTOM_MODEL_DATA,
                new CustomModelData(Mth.clamp(browning, 1, STAGES - 1)));
        return out;
    }

    public static int stageOf(ItemStack stack) {
        if (!stack.is(ModItems.TOAST.get())) {
            return 0;
        }
        final CustomModelData data = stack.get(DataComponents.CUSTOM_MODEL_DATA);
        return data == null ? 1 : Mth.clamp(data.value(), 0, STAGES - 1);
    }

    public static boolean toastable(ItemStack stack) {
        return stack.is(ModItems.BREAD_SLICE.get()) || stack.is(ModItems.TOAST.get());
    }

    public Refusal checkLoad(int index, ItemStack stack) {
        if (down) {
            return Refusal.RUNNING;
        }
        if (!slots[index].isEmpty()) {
            return Refusal.SLOT_FULL;
        }
        if (!toastable(stack)) {
            return Refusal.WRONG_ITEM;
        }
        return Refusal.NONE;
    }

    public int load(int index, ItemStack stack) {
        int taken = 0;
        for (final int slot : new int[]{index, index ^ 1}) {
            if (taken >= stack.getCount() || !slots[slot].isEmpty()) {
                continue;
            }
            final ItemStack one = stack.copyWithCount(1);
            slots[slot] = one;
            stage[slot] = stageOf(one);
            done[slot] = false;
            taken++;
        }
        if (taken > 0) {
            sync();
        }
        return taken;
    }

    public ItemStack unload(int index) {
        if (down || slots[index].isEmpty()) {
            return ItemStack.EMPTY;
        }
        final ItemStack out = slots[index];
        slots[index] = ItemStack.EMPTY;
        stage[index] = 0;
        done[index] = false;
        sync();
        return out;
    }

    public List<ItemStack> dropContents() {
        final List<ItemStack> out = new ArrayList<>(SLOTS);
        for (int i = 0; i < SLOTS; i++) {
            if (!slots[i].isEmpty()) {
                out.add(slots[i]);
                slots[i] = ItemStack.EMPTY;
                stage[i] = 0;
                done[i] = false;
            }
        }
        return out;
    }

    public boolean turnDial(boolean forward) {
        dialFrom = detent;
        detent = Math.floorMod(detent + (forward ? 1 : -1), DETENTS);
        dialTime = level == null ? 0L : level.getGameTime();
        if (down) {
            final int target = targetStage();
            cycle = cycleTicks();
            progress = Math.min(progress, cycle);

            if (loaded() && everyLoadedSliceIsAtLeast(target)) {
                pop();
                return true;
            }
        }
        sync();
        return false;
    }

    private boolean everyLoadedSliceIsAtLeast(int target) {
        for (int i = 0; i < SLOTS; i++) {
            if (!slots[i].isEmpty() && stage[i] < target) {
                return false;
            }
        }
        return true;
    }

    public void toggleBagel() {
        bagel = !bagel;
        sync();
    }

    @Override
    public String settingsKey() {
        return "create_stats:toaster";
    }

    @Override
    public void writeSettings(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("Detent", detent);
        tag.putBoolean("Bagel", bagel);
    }

    @Override
    public boolean applySettings(CompoundTag tag, HolderLookup.Provider registries,
                                 @Nullable net.minecraft.world.entity.player.Player player, boolean simulate) {
        if (!tag.contains("Detent") || !tag.contains("Bagel")) {
            return false;
        }
        if (simulate) {
            return true;
        }
        setMode(tag.getInt("Detent"));
        if (tag.getBoolean("Bagel") != bagel) {
            toggleBagel();
        }
        return true;
    }

    @Override
    public void writeFreshDefaults(CompoundTag tag) {
        tag.putInt("Priority", com.createstats.power.FePriority.DEFAULT.ordinal());
    }

    public void setTrayOpen(boolean open) {
        if (trayOpen == open) {
            return;
        }
        trayOpen = open;
        trayTime = level == null ? 0L : level.getGameTime();
        sync();
    }

    public void markPressed(int button) {
        if (level == null) {
            return;
        }
        pressed = button;
        pressTime = level.getGameTime();
        sync();
    }

    public int pressedButton() {
        if (level == null || pressed < 0) {
            return -1;
        }
        final long shown = level.isClientSide() ? clientPressTime : pressTime;
        return shown != Long.MIN_VALUE && level.getGameTime() - shown < PRESS_TICKS ? pressed : -1;
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
        if (powered && !down) {
            pushDown();
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
        advanceToasting();
        heartbeat();
    }

    private void payPower() {
        final Refusal why = checkRun();

        final boolean dead = why == Refusal.SHED || why == Refusal.GATED;
        final boolean wantsHeat = why == Refusal.NONE;
        final int want = dead ? 0 : (wantsHeat ? toastingDraw() : DRAW_IDLE);
        ratedDraw = want;
        if (want == 0 || energy < want) {
            measuredDraw = 0;
            setHeating(false);
            return;
        }
        energy -= want;
        measuredDraw = want;
        setHeating(wantsHeat);
        setChanged();
    }

    public Refusal checkRun() {
        if (managed.isSuppressed()) {
            return Refusal.SHED;
        }
        if (gated) {
            return Refusal.GATED;
        }
        if (!down) {
            return Refusal.IDLE;
        }
        if (energy < toastingDraw()) {
            return Refusal.NO_POWER;
        }
        return Refusal.NONE;
    }

    // no readout of its own, so the dial tells you if there's mains
    public boolean hasSupply() {
        return energy > 0 || (network != null && network.lastResult().available() > 0);
    }

    public boolean isWired() {
        return !links.isEmpty() || (network != null && network.size() > 1) || isFed();
    }

    private boolean isFed() {
        return level != null && fedAt != Long.MIN_VALUE && level.getGameTime() - fedAt <= 40L;
    }

    public boolean isStalled() {
        if (!down) {
            return false;
        }
        final Refusal why = checkRun();
        return why == Refusal.NO_POWER || why == Refusal.SHED || why == Refusal.GATED;
    }

    private void setHeating(boolean value) {
        if (heating == value) {
            return;
        }
        heating = value;
        setLit(value);
        sync();
    }

    private void advanceToasting() {
        if (!heating) {
            return;
        }
        progress++;
        final int target = targetStage();
        final int now = Math.min(target, Math.round(target * progress / (float) Math.max(1, cycle)));
        boolean changed = false;
        for (int i = 0; i < SLOTS; i++) {
            if (!slots[i].isEmpty() && now > stage[i]) {
                stage[i] = now;
                changed = true;
            }
        }
        if (progress >= cycle) {
            ToasterBlock.playPop(level, worldPosition);
            pop();
            return;
        }
        if (changed) {
            sync();
        }
    }

    public void clientTick() {
        heatPrev = heat;
        final float target = heating ? 1.0f : 0.0f;
        heat += (target - heat) * (target > heat ? HEAT_UP : HEAT_DOWN);
        if (Math.abs(target - heat) < 0.004f) {
            heat = target;
        }
    }

    private void heartbeat() {
        if (--syncCooldown > 0) {
            return;
        }
        syncCooldown = 20;
        if (Math.abs(energy - lastSyncedEnergy) * 16 >= BUFFER) {
            lastSyncedEnergy = energy;
            sync();
        }
    }

    public float carriage(float partialTick) {
        if (level == null || leverTime == Long.MIN_VALUE) {
            return down ? 1.0f : 0.0f;
        }
        final float u = level.getGameTime() - leverTime + partialTick;
        if (down) {
            final float t = Mth.clamp(u / DOWN_TICKS, 0.0f, 1.0f);
            return t * t * (3.0f - 2.0f * t);
        }
        if (u >= POP_TICKS || u < 0.0f) {
            return 0.0f;
        }
        final double p = u / POP_TICKS;
        return (float) (Math.exp(-SPRING_DAMP * p) * Math.cos(SPRING_RATE * p));
    }

    public float sliceHop(float partialTick) {
        if (down || level == null || leverTime == Long.MIN_VALUE) {
            return 0.0f;
        }
        final float p = (level.getGameTime() - leverTime + partialTick) / POP_TICKS;
        if (p < 0.0f || p >= 0.5f) {
            return 0.0f;
        }
        final float q = p / 0.5f;
        return HOP * 4.0f * q * (1.0f - q);
    }

    public float sliceTilt(int index, float partialTick) {
        if (down || level == null || leverTime == Long.MIN_VALUE) {
            return 0.0f;
        }
        final float p = (level.getGameTime() - leverTime + partialTick) / POP_TICKS;
        if (p < 0.0f || p >= 1.0f) {
            return 0.0f;
        }
        return (float) (TILT * Math.sin(p * Math.PI * 2.0) * (1.0 - p)) * (index == 0 ? 1.0f : -1.0f);
    }

    public float heat(float partialTick) {
        return Mth.lerp(partialTick, heatPrev, heat);
    }

    public int elementStage(float partialTick) {
        final float h = heat(partialTick);
        return h < 0.08f ? 0 : h < 0.42f ? 1 : h < 0.78f ? 2 : 3;
    }

    public boolean elementLit(int index) {
        return !bagel || index == 1 || index == 2;
    }

    public float dialAngle(float partialTick, float sweep, int steps) {
        final float to = detentAngle(detent, sweep, steps);
        if (level == null || dialTime == Long.MIN_VALUE) {
            return to;
        }
        final float from = detentAngle(dialFrom, sweep, steps);
        float t = Mth.clamp((level.getGameTime() - dialTime + partialTick) / DIAL_TICKS, 0.0f, 1.0f);
        t = t * t * (3.0f - 2.0f * t);
        return from + (to - from) * t;
    }

    private static float detentAngle(int step, float sweep, int steps) {
        return -sweep / 2.0f + step * (sweep / (steps - 1));
    }

    public float trayOut(float partialTick) {
        if (level == null || trayTime == Long.MIN_VALUE) {
            return trayOpen ? 1.0f : 0.0f;
        }
        final float t = Mth.clamp((level.getGameTime() - trayTime + partialTick) / TRAY_TICKS,
                0.0f, 1.0f);
        final float eased = t * t * (3.0f - 2.0f * t);
        return trayOpen ? eased : 1.0f - eased;
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
            if (!insert || down || stack.isEmpty() || !toastable(stack)
                    || slot < 0 || slot >= SLOTS || !slots[slot].isEmpty()) {
                return stack;
            }
            if (!simulate) {
                slots[slot] = stack.copyWithCount(1);
                stage[slot] = stageOf(stack);
                done[slot] = false;
                sync();
                refreshComparator();
            }
            return stack.copyWithCount(stack.getCount() - 1);
        }

        @Override
        public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (insert || down || amount <= 0 || slot < 0 || slot >= SLOTS
                    || slots[slot].isEmpty() || !done[slot]) {
                return ItemStack.EMPTY;
            }
            final ItemStack out = slots[slot].copy();
            if (!simulate) {
                slots[slot] = ItemStack.EMPTY;
                stage[slot] = 0;
                done[slot] = false;
                sync();
                refreshComparator();
            }
            return out;
        }

        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            return insert && toastable(stack);
        }
    }

    private final IItemHandler inputHandle = new SlotHandler(true);
    private final IItemHandler outputHandle = new SlotHandler(false);

    public static void registerCapabilities(
            net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK,
                com.createstats.registry.ModBlockEntities.TOASTER.get(),
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
        return DRAW_TOASTING * 4;
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

    // suppression, not the lever. mapping a remote onto a spring latch pops toast across the house
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
        if (down) {
            final int left = remainingTicks();
            final Component text;
            if (heating) {
                text = Component.translatable("status.create_stats.home.toasting",
                        ApplianceStatus.clock(left));
            } else {
                text = switch (checkRun()) {
                    case SHED -> Component.translatable("status.create_stats.home.paused_pdu");
                    case GATED -> Component.translatable("status.create_stats.home.paused_off");
                    case NO_POWER -> Component.translatable("status.create_stats.home.paused_power");
                    default -> Component.translatable("status.create_stats.home.toasting",
                            ApplianceStatus.clock(left));
                };
            }
            return ApplianceStatus.running(text, cycle, left,
                    Component.translatable("status.create_stats.home.toaster_done"));
        }
        if (isGated() || isManagedShed()
                || (ratedDrawFePerTick() > 0 && measuredDrawFePerTick() <= 0)) {
            return null;
        }
        if (managed.isSuppressed()) {
            return ApplianceStatus.idle(Component.translatable("status.create_stats.home.held_pdu"));
        }
        if (finishedCount() > 0) {
            return ApplianceStatus.idle(Component.translatable("status.create_stats.home.toast_ready"));
        }
        if (trayOpen) {
            return ApplianceStatus.idle(Component.translatable("status.create_stats.home.tray_out"));
        }
        if (loaded()) {
            return ApplianceStatus.idle(Component.translatable("status.create_stats.home.loaded"));
        }
        return ApplianceStatus.idle(Component.translatable("status.create_stats.home.empty"));
    }

    @Override
    public int getMode() {
        return detent;
    }

    @Override
    public void setMode(int mode) {
        final int wanted = Mth.clamp(mode, 0, DETENTS - 1);
        if (wanted == detent) {
            return;
        }
        dialFrom = detent;
        detent = wanted;
        dialTime = level == null ? 0L : level.getGameTime();
        if (down) {
            cycle = cycleTicks();
            progress = Math.min(progress, cycle);
        }
        sync();
    }

    @Override
    public List<Component> modeLabels() {
        final List<Component> out = new ArrayList<>(DETENTS);
        for (int i = 0; i < DETENTS; i++) {
            out.add(labelFor(i));
        }
        return out;
    }

    public static Component labelFor(int detent) {
        final int step = Mth.clamp(detent, 0, DETENTS - 1);
        final int shade = 1 + Math.round(step * 4.0f / (DETENTS - 1));
        return Component.translatable("mode.create_stats.toaster.detent", step + 1,
                Component.translatable("mode.create_stats.toaster.stage_" + shade));
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        final Refusal blocking = checkRun();
        tooltip.add(pad(Component.literal("Toaster").withStyle(ChatFormatting.GRAY)));

        step(tooltip, 1, "Mains", mainsSummary(), energy >= toastingDraw(),
                blocking == Refusal.NO_POWER || blocking == Refusal.SHED
                        || blocking == Refusal.GATED);
        step(tooltip, 2, "Slots", slotSummary(), loaded(),
                blocking == Refusal.IDLE && !loaded());
        step(tooltip, 3, "Browning", String.format(Locale.ROOT, "%d of %d, %.1fs%s",
                        detent + 1, DETENTS, cycleTicks() / 20.0f, bagel ? ", bagel" : ""),
                true, false);

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

    private String slotSummary() {
        if (!loaded()) {
            return "empty";
        }
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < SLOTS; i++) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(slots[i].isEmpty() ? "empty"
                    : done[i] ? "done" : shadeName(stage[i]));
        }
        return sb.toString();
    }

    private static String shadeName(int shade) {
        return switch (shade) {
            case 0 -> "raw";
            case 1 -> "warm";
            case 2 -> "light";
            case 3 -> "golden";
            case 4 -> "dark";
            default -> "very dark";
        };
    }

    private String nextAction(Refusal blocking) {
        if (heating) {
            return "Toasting, " + clock(remainingTicks()) + " to go";
        }
        if (down) {
            return switch (blocking) {
                case NO_POWER -> isWired()
                        ? "Held: waiting for charge"
                        : "Held: nothing wired to the studs on the back";
                case SHED -> "Held: shed by the Power Distribution Unit";
                case GATED -> "Held: switched off from a panel";
                default -> "Held.";
            };
        }
        if (finishedCount() > 0) {
            return "Take the toast out";
        }
        return loaded() ? "Push the lever" : "Drop bread in a slot";
    }

    private ChatFormatting actionColour(Refusal blocking) {
        if (heating) {
            return ChatFormatting.AQUA;
        }
        if (isStalled()) {
            return ChatFormatting.GOLD;
        }
        return blocking == Refusal.IDLE && !loaded()
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

    private void setLit(boolean value) {
        if (level == null || level.isClientSide()) {
            return;
        }
        final BlockState state = getBlockState();
        if (state.hasProperty(ToasterBlock.LIT) && state.getValue(ToasterBlock.LIT) != value) {
            level.setBlock(worldPosition, state.setValue(ToasterBlock.LIT, value), Block.UPDATE_ALL);
        }
    }

    public int comparatorSignal() {
        final int finished = finishedCount();
        return finished <= 0 ? 0 : Math.max(1, Math.round(finished * 15.0f / SLOTS));
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
        lastSyncedEnergy = energy;
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(),
                    Block.UPDATE_ALL);
            refreshComparator();
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        final ListTag list = new ListTag();
        for (int i = 0; i < SLOTS; i++) {
            final CompoundTag slot = new CompoundTag();
            slot.putInt("Slot", i);
            slot.putInt("Stage", stage[i]);
            slot.putBoolean("Done", done[i]);
            if (!slots[i].isEmpty()) {
                slot.put("Item", slots[i].save(registries));
            }
            list.add(slot);
        }
        tag.put("Slots", list);
        tag.putInt("Detent", detent);
        tag.putInt("DialFrom", dialFrom);
        tag.putLong("DialTime", dialTime);
        tag.putBoolean("Bagel", bagel);
        tag.putBoolean("Down", down);
        tag.putBoolean("Heating", heating);
        tag.putLong("LeverTime", leverTime);
        tag.putInt("Progress", progress);
        tag.putInt("Cycle", cycle);
        tag.putBoolean("TrayOpen", trayOpen);
        tag.putLong("TrayTime", trayTime);
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
        for (int i = 0; i < SLOTS; i++) {
            slots[i] = ItemStack.EMPTY;
            stage[i] = 0;
            done[i] = false;
        }
        final ListTag list = tag.getList("Slots", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            final CompoundTag slot = list.getCompound(i);
            final int index = Mth.clamp(slot.getInt("Slot"), 0, SLOTS - 1);
            stage[index] = Mth.clamp(slot.getInt("Stage"), 0, STAGES - 1);
            done[index] = slot.getBoolean("Done");
            slots[index] = slot.contains("Item")
                    ? ItemStack.parseOptional(registries, slot.getCompound("Item"))
                    : ItemStack.EMPTY;
        }
        detent = Mth.clamp(tag.getInt("Detent"), 0, DETENTS - 1);
        dialFrom = Mth.clamp(tag.getInt("DialFrom"), 0, DETENTS - 1);
        dialTime = tag.contains("DialTime") ? tag.getLong("DialTime") : Long.MIN_VALUE;
        bagel = tag.getBoolean("Bagel");
        down = tag.getBoolean("Down");
        heating = tag.getBoolean("Heating");
        leverTime = tag.contains("LeverTime") ? tag.getLong("LeverTime") : Long.MIN_VALUE;
        progress = Math.max(0, tag.getInt("Progress"));
        cycle = Math.max(1, tag.getInt("Cycle"));
        trayOpen = tag.getBoolean("TrayOpen");
        trayTime = tag.contains("TrayTime") ? tag.getLong("TrayTime") : Long.MIN_VALUE;
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
