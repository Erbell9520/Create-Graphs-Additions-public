package com.createstats.blockentity;

import com.createstats.block.BiofuelBurnerBlock;
import com.createstats.power.BiofuelFuel;
import com.createstats.power.BiofuelValue;
import com.createstats.power.FeGoggle;
import com.createstats.power.FeGridMember;
import com.createstats.power.FeSupply;
import com.createstats.power.FeWireNetwork;
import com.createstats.power.FeWireTerminal;
import com.createstats.power.WireTier;
import com.createstats.registry.ModItems;
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
import net.neoforged.neoforge.capabilities.Capabilities;
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

// one rate, fuel only changes how long. satisfactory style
public class BiofuelBurnerBE extends BlockEntity implements FeGridMember, FeWireTerminal, FeSupply,
        com.createstats.power.FeForecastingSource,
        com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation {
    public static final int BUFFER = 16_000;

    public static final int OUTFLOW_RATE = 1_024;

    public static final int FUEL_CAPACITY = 64;

    private ItemStack fuel = ItemStack.EMPTY;

    private int burnLeft;
    private int burnTotal;

    private int energy;
    private int lastGeneratedFePerTick;
    private int lastOutflowFePerTick;

    private int outflowThisTick;

    private final com.createstats.power.BurnerFuelOutlook feed = new com.createstats.power.BurnerFuelOutlook();

    private final List<BlockPos> links = new ArrayList<>();
    private final Map<Long, WireTier> linkTiers = new HashMap<>();
    @Nullable
    private FeWireNetwork network;

    private int lastComparator = -1;
    private int lastSyncedReadout = Integer.MIN_VALUE;
    private int ticksSinceReadoutSync;

    public BiofuelBurnerBE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    private Direction facing() {
        final BlockState state = getBlockState();
        return state.hasProperty(BiofuelBurnerBlock.FACING)
                ? state.getValue(BiofuelBurnerBlock.FACING) : Direction.NORTH;
    }

    public Direction frontFace() {
        return facing();
    }

    public Direction backFace() {
        return facing().getOpposite();
    }

    public boolean isBurning() {
        return burnLeft > 0;
    }

    public int burnTicksLeft() {
        return burnLeft;
    }

    public float burnFraction() {
        return burnTotal <= 0 ? 0.0f : Mth.clamp(burnLeft / (float) burnTotal, 0.0f, 1.0f);
    }

    public ItemStack fuelStack() {
        return fuel;
    }

    public int storedFe() {
        return energy;
    }

    public int lastGeneratedFePerTick() {
        return lastGeneratedFePerTick;
    }

    public int lastOutflowFePerTick() {
        return lastOutflowFePerTick;
    }

    @Override
    public int potentialFePerTick() {
        return burnLeft > 0 || !fuel.isEmpty() ? BiofuelValue.OUTPUT_FE_PER_TICK : 0;
    }

    // forever while a hopper keeps it fed. controllers forecast 30 min now, the old answer was wrong past 50s
    @Override
    public int potentialFePerTickIn(int ticks) {
        return com.createstats.power.BurnerFuelOutlook.outputIn(potentialFePerTick(), fuelTicksOnHand(),
                feed.fed(), ticks);
    }

    @Override
    public boolean burnsFuel() {
        return true;
    }

    public long fuelTicksOnHand() {
        return com.createstats.power.BurnerFuelOutlook.ticksOnHand(burnLeft, fuel.isEmpty() ? 0 : fuel.getCount(),
                fuel.isEmpty() ? 0 : BiofuelFuel.burnTicksOf(fuel));
    }

    public boolean isFed() {
        return feed.fed();
    }

    public void serverTick() {
        if (level == null || level.isClientSide()) {
            return;
        }

        lastOutflowFePerTick = outflowThisTick;
        outflowThisTick = 0;

        if (energy > BUFFER) {
            energy = BUFFER;
        }
        final int room = BUFFER - energy;
        if (burnLeft <= 0 && room > 0 && !fuel.isEmpty()) {
            burnTotal = BiofuelFuel.burnTicksOf(fuel);
            burnLeft = burnTotal;
            fuel.shrink(1);
            feed.onTake();
            if (fuel.isEmpty()) {
                fuel = ItemStack.EMPTY;
            }
            setChanged();
        }

        final int made = burnLeft > 0 ? BiofuelValue.tickOutput(room) : 0;
        lastGeneratedFePerTick = made;
        if (made > 0) {
            energy += made;
            burnLeft--;
            setChanged();
        }

        if (network == null || network.isDirty()) {
            FeWireNetwork.buildFrom(this);
        }
        if (network != null) {
            network.solveOnce(level);
        }

        if (energy > 0) {
            pushToNeighbours();
        }

        final boolean lit = burnLeft > 0;
        if (getBlockState().hasProperty(BiofuelBurnerBlock.LIT)
                && getBlockState().getValue(BiofuelBurnerBlock.LIT) != lit) {
            level.setBlock(worldPosition, getBlockState().setValue(BiofuelBurnerBlock.LIT, lit),
                    Block.UPDATE_ALL);
        }
        refreshComparator();
        syncReadout(made * 31 + lastOutflowFePerTick * 7 + energy / 64 + fuel.getCount() * 101
                + (lit ? 1 : 0));
    }

    private void pushToNeighbours() {
        if (level == null) {
            return;
        }
        for (final Direction direction : Direction.values()) {
            if (energy <= 0) {
                return;
            }
            if (direction == frontFace()) {
                continue;
            }
            final int budget = Math.min(outflowBudget(), energy);
            if (budget <= 0) {
                return;
            }
            final BlockPos at = worldPosition.relative(direction);
            if (level.getBlockEntity(at) instanceof FeGridMember) {
                continue;
            }
            final IEnergyStorage target = level.getCapability(Capabilities.EnergyStorage.BLOCK, at,
                    direction.getOpposite());
            if (target == null || !target.canReceive()) {
                continue;
            }
            final int moved = target.receiveEnergy(budget, false);
            if (moved > 0) {
                energy -= moved;
                recordOutflow(moved);
                setChanged();
            }
        }
    }

    private int outflowBudget() {
        return Math.max(0, OUTFLOW_RATE - outflowThisTick);
    }

    private void recordOutflow(int amount) {
        if (amount > 0) {
            outflowThisTick += amount;
        }
    }

    private void syncReadout(int shown) {
        ticksSinceReadoutSync++;
        final int bucket = Math.max(1, Math.abs(shown) / 32);
        if (Math.abs(shown - lastSyncedReadout) < bucket && ticksSinceReadoutSync < 20) {
            return;
        }
        lastSyncedReadout = shown;
        ticksSinceReadoutSync = 0;
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(),
                    Block.UPDATE_CLIENTS);
        }
    }

    public int comparatorSignal() {
        if (fuel.isEmpty()) {
            return isBurning() ? 1 : 0;
        }
        return Mth.clamp(1 + fuel.getCount() * 14 / FUEL_CAPACITY, 1, 15);
    }

    private void refreshComparator() {
        final int now = comparatorSignal();
        if (now != lastComparator) {
            lastComparator = now;
            if (level != null) {
                level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
            }
        }
    }

    public boolean canAccept(ItemStack stack) {
        if (!BiofuelFuel.accepts(stack)) {
            return false;
        }
        return fuel.isEmpty() || (ItemStack.isSameItemSameComponents(fuel, stack)
                && fuel.getCount() < FUEL_CAPACITY);
    }

    public ItemStack insertFuel(ItemStack stack, boolean simulate) {
        final ItemStack left = insert(stack, simulate);
        if (!simulate && left.getCount() < stack.getCount()) {
            feed.onHand();
        }
        return left;
    }

    private ItemStack insert(ItemStack stack, boolean simulate) {
        if (!canAccept(stack)) {
            return stack;
        }
        final int room = FUEL_CAPACITY - (fuel.isEmpty() ? 0 : fuel.getCount());
        final int taken = Math.min(room, stack.getCount());
        if (taken <= 0) {
            return stack;
        }
        if (!simulate) {
            if (fuel.isEmpty()) {
                fuel = stack.copyWithCount(taken);
            } else {
                fuel.grow(taken);
            }
            setChanged();
            refreshComparator();
            sync();
        }
        return stack.copyWithCount(stack.getCount() - taken);
    }

    public ItemStack takeFuel() {
        final ItemStack out = fuel;
        fuel = ItemStack.EMPTY;
        setChanged();
        refreshComparator();
        sync();
        return out;
    }

    private void sync() {
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(),
                    Block.UPDATE_CLIENTS);
        }
    }

    private final IEnergyStorage energyView = new IEnergyStorage() {
        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            return 0;
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            final int amount = Math.min(Math.min(maxExtract, outflowBudget()), energy);
            if (amount <= 0) {
                return 0;
            }
            if (!simulate) {
                energy -= amount;
                recordOutflow(amount);
                setChanged();
            }
            return amount;
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
            return true;
        }

        @Override
        public boolean canReceive() {
            return false;
        }
    };

    private final IItemHandler fuelHandle = new IItemHandler() {
        @Override
        public int getSlots() {
            return 1;
        }

        @Override
        public @NotNull ItemStack getStackInSlot(int slot) {
            return slot == 0 ? fuel : ItemStack.EMPTY;
        }

        @Override
        public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
            if (slot != 0) {
                return stack;
            }
            final ItemStack left = insert(stack, simulate);
            if (!simulate && left.getCount() < stack.getCount()) {
                feed.onFeed();
            }
            return left;
        }

        @Override
        public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
            return ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return FUEL_CAPACITY;
        }

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            return slot == 0 && BiofuelFuel.accepts(stack);
        }
    };

    public IEnergyStorage energyHandle() {
        return energyView;
    }

    public static void registerCapabilities(
            net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK,
                com.createstats.registry.ModBlockEntities.BIOFUEL_BURNER.get(),
                (be, side) -> be.fuelHandle);
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
        return OUTFLOW_RATE;
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
        for (final BlockPos link : new ArrayList<>(links)) {
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
        for (final Map.Entry<WireTier, Integer> e : refund.entrySet()) {
            Block.popResource(level, worldPosition,
                    new ItemStack(ModItems.wireItem(e.getKey()), e.getValue()));
        }
    }

    private void syncLinks() {
        setChanged();
        invalidateNetwork();
        sync();
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        tooltip.add(FeGoggle.title("Biofuel Burner"));
        tooltip.add(FeGoggle.output(lastGeneratedFePerTick));
        if (burnLeft > 0 && lastGeneratedFePerTick < BiofuelValue.OUTPUT_FE_PER_TICK) {
            tooltip.add(FeGoggle.dim("Buffer full, holding the fire"));
        }
        tooltip.add(FeGoggle.amount("Buffer", energy, BUFFER));
        if (burnLeft > 0) {
            tooltip.add(FeGoggle.dim(String.format(Locale.ROOT, "Burning, %s left", clock(burnLeft))));
        } else if (fuel.isEmpty()) {
            tooltip.add(FeGoggle.warn("Out of fuel"));
        } else {
            tooltip.add(FeGoggle.dim("Ready to light"));
        }
        if (!fuel.isEmpty()) {
            tooltip.add(FeGoggle.dim(String.format(Locale.ROOT, "Hopper: %d x %s, %s of burn",
                    fuel.getCount(), fuel.getHoverName().getString(),
                    clock(fuel.getCount() * BiofuelFuel.burnTicksOf(fuel)))));
        }

        if (feed.fed() && potentialFePerTick() > 0) {
            tooltip.add(FeGoggle.dim("Auto-fed: forecasts keep it burning"));
        }
        return true;
    }

    public static String clock(int ticks) {
        final int seconds = Math.max(0, ticks) / 20;
        return seconds >= 60
                ? String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60)
                : String.format(Locale.ROOT, "%ds", seconds);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!fuel.isEmpty()) {
            tag.put("Fuel", fuel.save(registries));
        }
        tag.putInt("BurnLeft", burnLeft);
        tag.putInt("BurnTotal", burnTotal);
        tag.putInt("Energy", energy);

        tag.putInt("Generated", lastGeneratedFePerTick);
        tag.putInt("Outflow", lastOutflowFePerTick);

        tag.putBoolean("Fed", feed.fed());
        tag.putBoolean("FeedWait", feed.waiting());
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
        fuel = tag.contains("Fuel")
                ? ItemStack.parseOptional(registries, tag.getCompound("Fuel")) : ItemStack.EMPTY;
        burnLeft = Math.max(0, tag.getInt("BurnLeft"));
        burnTotal = Math.max(0, tag.getInt("BurnTotal"));
        energy = Mth.clamp(tag.getInt("Energy"), 0, BUFFER);
        lastGeneratedFePerTick = Math.max(0, tag.getInt("Generated"));
        lastOutflowFePerTick = Math.max(0, tag.getInt("Outflow"));
        feed.load(tag.getBoolean("Fed"), tag.getBoolean("FeedWait"));
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
