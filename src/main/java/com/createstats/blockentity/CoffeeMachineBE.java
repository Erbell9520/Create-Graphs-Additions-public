package com.createstats.blockentity;

import com.createstats.block.MugContent;
import com.createstats.power.FeGridMember;
import com.createstats.power.FeLock;
import com.createstats.power.FeManagedLoad;
import com.createstats.power.FeManagedState;
import com.createstats.power.FePriority;
import com.createstats.power.FeWireNetwork;
import com.createstats.power.FeWireTerminal;
import com.createstats.power.WireTier;
import com.createstats.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
import java.util.Map;

// grid member not FeConsumer, creativeEnergy waives consumers and this has its own terminal
public class CoffeeMachineBE extends BlockEntity implements FeGridMember, FeManagedLoad, FeWireTerminal,
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

    public static final int BREW_TICKS = 44;

    private static final int PULL_TICKS = 7;

    private static final int RETURN_TICKS = 10;

    private static final float FILL_PER_BREW = 0.34f;

    private static final float PRESSURE_TICKS = 40.0f;

    private static final float WAND_TICKS = 8.0f;

    private static final float LOCK_TICKS = 6.0f;

    public static final int BEAN_CAPACITY = 64;

    private static final int IDLE_DRAW = 8;

    private static final int BREW_COST = 900;

    private static final int FROTH_COST = 450;

    public static final int FROTH_TICKS = 30;

    private static final int BUFFER = 4000;

    private boolean on;
    private boolean wandOut;
    private boolean locked;
    private int beans;

    private int mug = -1;

    private float fill;

    private boolean milk;

    private long brewStart = -1L;

    private long frothStart = -1L;
    private long switchTime = Long.MIN_VALUE;
    private long wandTime = Long.MIN_VALUE;
    private long lockTime = Long.MIN_VALUE;

    private int energy;
    private int ratedDraw;
    private int measuredDraw;

    private int lastSyncedEnergy;
    private int syncCooldown;

    private final FeManagedState managed = new FeManagedState();
    private final List<BlockPos> links = new ArrayList<>();
    private final Map<Long, WireTier> linkTiers = new HashMap<>();
    @Nullable
    private FeWireNetwork network;

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

    private final IItemHandler itemHandle = new IItemHandler() {
        @Override
        public int getSlots() {
            return 1;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return beans <= 0 ? ItemStack.EMPTY : new ItemStack(Items.COCOA_BEANS, beans);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (!stack.is(Items.COCOA_BEANS)) {
                return stack;
            }
            final int room = Math.min(stack.getCount(), BEAN_CAPACITY - beans);
            if (room <= 0) {
                return stack;
            }
            if (!simulate) {
                beans += room;
                sync();
            }
            return stack.getCount() == room ? ItemStack.EMPTY : stack.copyWithCount(stack.getCount() - room);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return BEAN_CAPACITY;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return stack.is(Items.COCOA_BEANS);
        }
    };

    public CoffeeMachineBE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public IItemHandler itemHandle() {
        return itemHandle;
    }

    public Direction backFace() {
        final BlockState state = getBlockState();
        return state.hasProperty(com.createstats.block.CoffeeMachineBlock.FACING)
                ? state.getValue(com.createstats.block.CoffeeMachineBlock.FACING).getOpposite()
                : Direction.SOUTH;
    }

    public static void registerCapabilities(net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK,
                com.createstats.registry.ModBlockEntities.COFFEE_MACHINE.get(),
                (be, side) -> side == null || side == Direction.UP ? be.itemHandle() : null);
    }

    public boolean isOn() {
        return on;
    }

    public boolean isWandOut() {
        return wandOut;
    }

    public boolean isLocked() {
        return locked;
    }

    public int beans() {
        return beans;
    }

    public int mug() {
        return mug;
    }

    public boolean hasMug() {
        return mug >= 0;
    }

    public int storedEnergyFe() {
        return energy;
    }

    public boolean isBrewing() {
        return brewStart >= 0L;
    }

    public long brewElapsed() {
        if (brewStart < 0L || level == null) {
            return -1L;
        }
        return level.getGameTime() - brewStart;
    }

    public static float leverProgress(float elapsed) {
        if (elapsed < 0.0f || elapsed >= BREW_TICKS) {
            return 0.0f;
        }
        if (elapsed < PULL_TICKS) {
            return elapsed / PULL_TICKS;
        }
        final float remaining = BREW_TICKS - elapsed;
        if (remaining < RETURN_TICKS) {
            return remaining / RETURN_TICKS;
        }
        return 1.0f;
    }

    public static float pourProgress(float elapsed) {
        final float start = PULL_TICKS;
        final float end = BREW_TICKS - RETURN_TICKS;
        return Mth.clamp((elapsed - start) / (end - start), 0.0f, 1.0f);
    }

    public boolean isPouring() {
        final long elapsed = brewElapsed();
        return elapsed >= 0L && leverProgress(elapsed) > 0.85f && pourProgress(elapsed) < 1.0f;
    }

    public float leverSwing(float partialTick) {
        final long elapsed = brewElapsed();
        return elapsed < 0L ? 0.0f : leverProgress(elapsed + partialTick);
    }

    public float displayFill(float partialTick) {
        if (!hasMug()) {
            return 0.0f;
        }
        final long elapsed = brewElapsed();
        if (elapsed < 0L) {
            return fill;
        }
        return Math.min(1.0f, fill + pourProgress(elapsed + partialTick) * FILL_PER_BREW);
    }

    public boolean isFull() {
        return fill >= 0.999f;
    }

    public boolean hasMilk() {
        return milk;
    }

    public boolean isFrothing() {
        return frothStart >= 0L;
    }

    public float frothProgress(float partialTick) {
        if (frothStart < 0L || level == null) {
            return 0.0f;
        }
        return Mth.clamp((level.getGameTime() - frothStart + partialTick) / FROTH_TICKS, 0.0f, 1.0f);
    }

    public MugContent trayContent() {
        return MugContent.of(isFull(), milk);
    }

    public float pressure(float partialTick) {
        return ramp(switchTime, PRESSURE_TICKS, on, partialTick);
    }

    public float wandSwing(float partialTick) {
        return ramp(wandTime, WAND_TICKS, wandOut, partialTick);
    }

    public float lockSwing(float partialTick) {
        return ramp(lockTime, LOCK_TICKS, locked, partialTick);
    }

    private float ramp(long since, float duration, boolean towardOne, float partialTick) {
        if (level == null || since == Long.MIN_VALUE) {
            return towardOne ? 1.0f : 0.0f;
        }
        final float elapsed = level.getGameTime() - since + partialTick;
        final float t = Mth.clamp(elapsed / duration, 0.0f, 1.0f);
        return towardOne ? t : 1.0f - t;
    }

    public void setOn(boolean value) {
        on = value;
        if (level != null) {
            switchTime = level.getGameTime();
        }
        if (!on) {
            // power cut mid stroke leaves the lever floating forever

            brewStart = -1L;
            frothStart = -1L;
            if (wandOut) {
                wandOut = false;
                wandTime = switchTime;
            }
        }
        sync();
    }

    public void setWandOut(boolean value) {
        wandOut = value;
        if (level != null) {
            wandTime = level.getGameTime();
        }
        sync();
    }

    public void setLocked(boolean value) {
        locked = value;
        if (level != null) {
            lockTime = level.getGameTime();
        }
        sync();
    }

    public void setMug(int variant) {
        mug = variant;
        fill = 0.0f;
        milk = false;
        sync();
    }

    public void clearMug() {
        mug = -1;
        fill = 0.0f;
        milk = false;

        frothStart = -1L;
        sync();
    }

    public int addBeans(int count) {
        final int room = Math.min(count, BEAN_CAPACITY - beans);
        if (room > 0) {
            beans += room;
            sync();
        }
        return room;
    }

    public enum Refusal {
        NONE, OFF, UNLOCKED, NO_BEANS, NO_POWER, BUSY, NO_MUG, ALREADY_MILKY
    }

    public Refusal checkFrothable() {
        if (level == null || !on) {
            return Refusal.OFF;
        }
        if (isFrothing()) {
            return Refusal.BUSY;
        }
        if (!hasMug()) {
            return Refusal.NO_MUG;
        }
        if (milk) {
            return Refusal.ALREADY_MILKY;
        }
        if (energy < FROTH_COST) {
            return Refusal.NO_POWER;
        }
        return Refusal.NONE;
    }

    public Refusal startFroth() {
        final Refusal refusal = checkFrothable();
        if (refusal != Refusal.NONE) {
            return refusal;
        }
        energy -= FROTH_COST;
        frothStart = level.getGameTime();
        sync();
        return Refusal.NONE;
    }

    public Refusal checkReady() {
        if (level == null || !on) {
            return Refusal.OFF;
        }
        if (isBrewing()) {
            return Refusal.BUSY;
        }
        if (!locked) {
            return Refusal.UNLOCKED;
        }
        if (beans <= 0) {
            return Refusal.NO_BEANS;
        }
        if (energy < BREW_COST) {
            return Refusal.NO_POWER;
        }
        return Refusal.NONE;
    }

    public Refusal startBrew() {
        final Refusal refusal = checkReady();
        if (refusal != Refusal.NONE) {
            return refusal;
        }
        energy -= BREW_COST;
        beans -= 1;
        brewStart = level.getGameTime();
        sync();
        return Refusal.NONE;
    }

    public int brewCost() {
        return BREW_COST;
    }

    public int bufferSize() {
        return BUFFER;
    }

    public int idleDraw() {
        return IDLE_DRAW;
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
        payIdle();
        finishBrew();
        finishFroth();
        heartbeat();
    }

    private void finishFroth() {
        if (frothStart < 0L || level.getGameTime() - frothStart < FROTH_TICKS) {
            return;
        }
        frothStart = -1L;

        if (hasMug()) {
            milk = true;
        }
        sync();
    }

    // 1/s max, only when moved. per tick sync spam already cost us once
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

    private void payIdle() {
        final boolean cut = managed.isSuppressed();

        ratedDraw = on && !managed.isForcedOff() ? IDLE_DRAW : 0;
        if (!on) {
            measuredDraw = 0;
            return;
        }
        if (cut || energy < IDLE_DRAW) {
            measuredDraw = 0;
            setOn(false);
            return;
        }
        energy -= IDLE_DRAW;
        measuredDraw = IDLE_DRAW;
        setChanged();
    }

    private void finishBrew() {
        if (brewStart < 0L || level.getGameTime() - brewStart < BREW_TICKS) {
            return;
        }
        brewStart = -1L;
        if (hasMug()) {
            fill = Math.min(1.0f, fill + FILL_PER_BREW);
        }
        sync();
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
        return BREW_COST / 4;
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
    public boolean addToGoggleTooltip(List<net.minecraft.network.chat.Component> tooltip,
                                      boolean isPlayerSneaking) {
        final java.util.Locale l = java.util.Locale.ROOT;
        final Refusal blocking = checkReady();

        tooltip.add(pad(net.minecraft.network.chat.Component.literal("Coffee Machine")
                .withStyle(net.minecraft.ChatFormatting.GRAY)));

        step(tooltip, 1, "Power", on
                        ? String.format(l, "%,d FE  (idle %d/t)", energy, IDLE_DRAW)
                        : "switched off",
                on && energy >= BREW_COST, blocking == Refusal.OFF || blocking == Refusal.NO_POWER);
        step(tooltip, 2, "Portafilter", locked ? "locked in" : "loose",
                locked, blocking == Refusal.UNLOCKED);
        step(tooltip, 3, "Beans", String.format(l, "%d / %d", beans, BEAN_CAPACITY),
                beans > 0, blocking == Refusal.NO_BEANS);
        step(tooltip, 4, "Mug", hasMug()
                        ? String.format(l, "%s, %s", mugName(), drinkSummary())
                        : "none on the tray",
                hasMug(), false);

        tooltip.add(pad(net.minecraft.network.chat.Component.literal(nextAction(blocking))
                .withStyle(actionColour(blocking))));
        return true;
    }

    private String mugName() {
        return switch (mug) {
            case 1 -> "Enamel";
            case 2 -> "Steel";
            default -> "Ceramic";
        };
    }

    private String drinkSummary() {
        final MugContent content = trayContent();
        if (content == MugContent.EMPTY) {
            return fill > 0.0f
                    ? String.format(java.util.Locale.ROOT, "coffee %d%%", Math.round(fill * 100.0f))
                    : "empty";
        }
        if (content == MugContent.MILK && fill > 0.0f) {
            return String.format(java.util.Locale.ROOT, "milk, coffee %d%%",
                    Math.round(fill * 100.0f));
        }
        return switch (content) {
            case COFFEE -> "coffee";
            case MILK -> "steamed milk";
            default -> "latte";
        };
    }

    private static void step(List<net.minecraft.network.chat.Component> tooltip, int index, String name,
                             String value, boolean satisfied, boolean blocking) {
        final net.minecraft.ChatFormatting colour = blocking ? net.minecraft.ChatFormatting.GOLD
                : satisfied ? net.minecraft.ChatFormatting.GREEN
                : net.minecraft.ChatFormatting.DARK_GRAY;

        tooltip.add(pad(net.minecraft.network.chat.Component
                .literal(String.format(java.util.Locale.ROOT, "%s %d  %s: %s",
                        blocking ? ">" : " ", index, name, value))
                .withStyle(colour)));
    }

    private String nextAction(Refusal blocking) {
        if (isFrothing()) {
            return String.format(java.util.Locale.ROOT, "Frothing  %d%%",
                    Math.round(frothProgress(0.0f) * 100.0f));
        }
        if (blocking == Refusal.BUSY) {
            final long elapsed = brewElapsed();
            return String.format(java.util.Locale.ROOT, "Brewing  %d%%",
                    Math.round(pourProgress(elapsed) * 100.0f));
        }
        return switch (blocking) {
            case OFF -> "Flip the switch on the front panel";
            case UNLOCKED -> "Twist the portafilter into the group head";
            case NO_BEANS -> "Load Cocoa Beans, by hand or by hopper on top";
            case NO_POWER -> "Wire the back terminal and let it charge";
            default -> hasMug() ? "Ready. Pull the lever." : "Ready, but there is no mug on the tray";
        };
    }

    private net.minecraft.ChatFormatting actionColour(Refusal blocking) {
        if (isFrothing()) {
            return net.minecraft.ChatFormatting.AQUA;
        }
        return switch (blocking) {
            case NONE -> net.minecraft.ChatFormatting.GREEN;
            case BUSY -> net.minecraft.ChatFormatting.AQUA;
            default -> net.minecraft.ChatFormatting.GOLD;
        };
    }

    private static net.minecraft.network.chat.Component pad(net.minecraft.network.chat.Component line) {
        return net.minecraft.network.chat.Component.literal("    ").append(line);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean("On", on);
        tag.putBoolean("WandOut", wandOut);
        tag.putBoolean("Locked", locked);
        tag.putInt("Beans", beans);
        tag.putInt("Mug", mug);
        tag.putFloat("Fill", fill);
        tag.putBoolean("Milk", milk);
        tag.putLong("BrewStart", brewStart);
        tag.putLong("FrothStart", frothStart);
        tag.putLong("SwitchTime", switchTime);
        tag.putLong("WandTime", wandTime);
        tag.putLong("LockTime", lockTime);
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
        on = tag.getBoolean("On");
        wandOut = tag.getBoolean("WandOut");
        locked = tag.getBoolean("Locked");
        beans = tag.getInt("Beans");
        mug = tag.contains("Mug") ? tag.getInt("Mug") : -1;
        fill = tag.getFloat("Fill");
        milk = tag.getBoolean("Milk");
        brewStart = tag.contains("BrewStart") ? tag.getLong("BrewStart") : -1L;
        frothStart = tag.contains("FrothStart") ? tag.getLong("FrothStart") : -1L;
        switchTime = tag.contains("SwitchTime") ? tag.getLong("SwitchTime") : Long.MIN_VALUE;
        wandTime = tag.contains("WandTime") ? tag.getLong("WandTime") : Long.MIN_VALUE;
        lockTime = tag.contains("LockTime") ? tag.getLong("LockTime") : Long.MIN_VALUE;
        energy = tag.getInt("Energy");
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
