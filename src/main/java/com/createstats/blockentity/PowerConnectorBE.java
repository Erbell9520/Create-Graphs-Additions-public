package com.createstats.blockentity;

import com.createstats.ModConfiguration;
import com.createstats.block.PowerConnectorBlock;
import com.createstats.power.ConnectorReadout;
import com.createstats.power.FeWireNetwork;
import com.createstats.power.WireTension;
import com.createstats.power.WireTier;
import com.createstats.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class PowerConnectorBE extends BlockEntity implements com.createstats.power.FeGridMember,
        com.createstats.power.FeWireTerminal,
        com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation {
    private final List<BlockPos> links = new ArrayList<>();

    private final java.util.Map<Long, WireTier> linkTiers = new java.util.HashMap<>();

    private final java.util.Map<Long, WireTension> linkTension = new java.util.HashMap<>();
    private int energy;

    @Nullable
    private FeWireNetwork network;

    private int lastFlow;

    private int cableFeed;

    private int carried;

    private int blockFlow;

    private boolean servesBlock = true;

    private int branchCeiling = com.createstats.power.FeGridSolver.NO_CEILING;

    private final IEnergyStorage energyView = new IEnergyStorage() {
        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            final int limit = Math.min(maxReceive, transferRate());
            final int amount = Math.max(0, Math.min(limit, bufferCapacity() - energy));
            if (amount > 0 && !simulate) {
                energy += amount;
                setChanged();
            }
            if (amount >= limit || network == null) {
                return amount;
            }

            return amount + network.takeFromExternal(nodeKey(), limit - amount, simulate, level);
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            final int limit = Math.min(maxExtract, transferRate());
            final int amount = Math.max(0, Math.min(limit, energy));
            if (amount > 0 && !simulate) {
                energy -= amount;
                setChanged();
            }
            if (amount >= limit || network == null) {
                return com.createstats.power.FeWireNetwork.noteSeamTransfer(network, level, simulate,
                        amount, nodeKey());
            }
            return com.createstats.power.FeWireNetwork.noteSeamTransfer(network, level, simulate,
                    amount + network.giveToExternal(nodeKey(), limit - amount, simulate, level),
                    nodeKey());
        }

        @Override
        public int getEnergyStored() {
            return energy;
        }

        @Override
        public int getMaxEnergyStored() {
            return bufferCapacity();
        }

        @Override
        public boolean canExtract() {
            return true;
        }

        @Override
        public boolean canReceive() {
            return true;
        }
    };

    public PowerConnectorBE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public IEnergyStorage energyHandle() {
        return energyView;
    }

    public List<BlockPos> links() {
        return links;
    }

    public WireTier tierOf(BlockPos link) {
        return linkTiers.getOrDefault(link.asLong(), WireTier.COPPER);
    }

    public WireTension tensionOf(BlockPos link) {
        final WireTension set = linkTension.get(link.asLong());
        return set == null ? WireTension.STANDARD : set;
    }

    public void setTension(BlockPos link, WireTension tension) {
        if (tension == linkTension.get(link.asLong())) {
            return;
        }
        linkTension.put(link.asLong(), tension);
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
        }
    }

    public static WireTension tensionBetween(@Nullable net.minecraft.world.level.BlockGetter level,
                                             BlockPos a, BlockPos b) {
        if (level == null) {
            return WireTension.STANDARD;
        }
        if (level.getBlockEntity(a) instanceof PowerConnectorBE near) {
            final WireTension set = near.linkTension.get(b.asLong());
            if (set != null) {
                return set;
            }
        }
        if (level.getBlockEntity(b) instanceof PowerConnectorBE far) {
            final WireTension set = far.linkTension.get(a.asLong());
            if (set != null) {
                return set;
            }
        }
        return WireTension.STANDARD;
    }

    public List<WireTier> spanTiers() {
        final List<WireTier> spans = new ArrayList<>(links.size());
        for (BlockPos link : links) {
            spans.add(tierOf(link));
        }
        return spans;
    }

    public void measureCableFeed() {
        if (level == null || level.isClientSide()) {
            return;
        }
        int sum = 0;
        for (Direction dir : Direction.values()) {
            final BlockPos at = worldPosition.relative(dir);
            if (!level.isLoaded(at)) {
                continue;
            }
            final BlockEntity be = level.getBlockEntity(at);
            final boolean conducts = be instanceof CableBE
                    || be instanceof IndoorCableBE
                    || (be instanceof SwitchBE sw && sw.isClosed())
                    || (be instanceof DiagnosticJunctionBE junction
                        && junction.isCableSplice() && !junction.isCutOff());
            if (conducts) {
                sum = com.createstats.power.FeRates.plus(sum,
                        Math.max(0, ((com.createstats.power.FeGridMember) be).transferRate()));
            }
        }
        if (sum != cableFeed) {
            cableFeed = sum;
            setChanged();
        }
    }

    public int storedEnergy() {
        return energy;
    }

    public void tick() {
        if (level == null || level.isClientSide()) {
            return;
        }
        if (energy > bufferCapacity()) {
            energy = bufferCapacity();
        }
        if (network == null || network.isDirty()) {
            FeWireNetwork.buildFrom(this);
        }
        if (network != null) {
            network.solveOnce(level);
        }
        refreshReadout();
        syncReadoutIfDue();
    }

    // server side now. the client copy of six machines never synced draw, fryer at full tilt read "Idle"
    private void refreshReadout() {
        final BlockEntity attached = level.getBlockEntity(worldPosition.relative(mountedOn()));
        servesBlock = attachedStorage() != null;
        int flow = com.createstats.power.FeGoggle.deviceFlowFePerTick(attached);
        if (flow == 0 && !com.createstats.power.FeGoggle.readsDeviceFlow(attached)) {
            flow = lastFlow;
        }
        blockFlow = servesBlock ? flow : 0;
        final long now = level.getGameTime();
        final int through = network == null ? com.createstats.power.FeFlowGraph.UNKNOWN
                : network.flowThrough(nodeKey(), now);

        carried = through == com.createstats.power.FeFlowGraph.UNKNOWN
                ? Math.abs(blockFlow) : Math.max(0, through);
        branchCeiling = network == null ? com.createstats.power.FeGridSolver.NO_CEILING
                : network.supplyCeiling(level);
    }

    public void setLastFlow(int flow) {
        this.lastFlow = flow;
    }

    @Override
    public boolean isGone(net.minecraft.world.level.Level owner) {
        return isRemoved() || getLevel() != owner;
    }

    @Override
    public int lastFlow() {
        return lastFlow;
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        readout().appendTo(tooltip, isPlayerSneaking);
        return true;
    }

    public ConnectorReadout readout() {
        final com.createstats.power.ConnectorTier tier = connectorTier();
        return new ConnectorReadout(tier.getDisplayName(), tier, baseTransferRate(), spanTiers(), cableFeed,
                carried, blockFlow, servesBlock, branchCeiling, bridgeCount(), sendOnlyBridgeCount(),
                energy, bufferCapacity());
    }

    private int sendOnlyBridgeCount() {
        if (level == null) {
            return 0;
        }
        int oneWay = 0;
        for (BlockPos link : links) {
            if (com.createstats.power.NewAgeFeed.isSendOnlyBridge(level, link)) {
                oneWay++;
            }
        }
        return oneWay;
    }

    private int bridgeCount() {
        if (level == null) {
            return 0;
        }
        int bridges = 0;
        for (BlockPos link : links) {
            if (com.createstats.power.ForeignTerminalNode.isBridgeTarget(level, link)) {
                bridges++;
            }
        }
        return bridges;
    }

    @Override
    public long nodeKey() {
        return worldPosition.asLong();
    }

    @Override
    @Nullable
    public IEnergyStorage attachedStorage() {
        if (level == null) {
            return null;
        }
        final Direction towards = mountedOn();
        return level.getCapability(Capabilities.EnergyStorage.BLOCK,
                worldPosition.relative(towards), towards.getOpposite());
    }

    public Direction mountedOn() {
        return (getBlockState().hasProperty(PowerConnectorBlock.FACING)
                ? getBlockState().getValue(PowerConnectorBlock.FACING)
                : Direction.UP).getOpposite();
    }

    public void adoptNetwork(FeWireNetwork network) {
        this.network = network;
    }

    @Nullable
    public FeWireNetwork network() {
        return network;
    }

    public void setStoredEnergy(int amount) {
        final int clamped = Math.max(0, Math.min(amount, bufferCapacity()));
        if (clamped != energy) {
            energy = clamped;
            setChanged();
        }
    }

    public void removeDeadLink(BlockPos link) {
        linkTiers.remove(link.asLong());
        linkTension.remove(link.asLong());
        if (links.remove(link)) {
            syncLinks();
        }
    }

    @Override
    public void invalidateNetwork() {
        if (network != null) {
            network.markDirty();
        }
        network = null;
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        invalidateNetwork();
    }

    public static void linkPair(PowerConnectorBE a, PowerConnectorBE b, WireTier tier) {
        a.links.add(b.worldPosition.immutable());
        b.links.add(a.worldPosition.immutable());
        a.linkTiers.put(b.worldPosition.asLong(), tier);
        b.linkTiers.put(a.worldPosition.asLong(), tier);
        a.syncLinks();
        b.syncLinks();
    }

    @Override
    public BlockPos terminalPos() {
        return worldPosition;
    }

    @Override
    public void addLink(BlockPos far, WireTier tier) {
        final BlockPos at = far.immutable();
        links.add(at);
        linkTiers.put(at.asLong(), tier == null ? WireTier.COPPER : tier);
        syncLinks();
    }

    public void dropWiresAndUnlinkAll() {
        if (level == null || level.isClientSide()) {
            return;
        }

        final java.util.Map<WireTier, Integer> refund = new java.util.EnumMap<>(WireTier.class);
        for (BlockPos link : new ArrayList<>(links)) {
            refund.merge(tierOf(link), 1, Integer::sum);
            final BlockEntity far = level.getBlockEntity(link);

            if (far instanceof com.createstats.power.FeStudPanel panel) {
                panel.forgetLinksTo(worldPosition);
            } else if (far instanceof com.createstats.power.FeWireTerminal other && other != this) {
                other.removeDeadLink(worldPosition);
            }
        }
        links.clear();
        linkTiers.clear();
        linkTension.clear();
        syncLinks();
        for (java.util.Map.Entry<WireTier, Integer> e : refund.entrySet()) {
            Block.popResource(level, worldPosition,
                    new ItemStack(ModItems.wireItem(e.getKey()), e.getValue()));
        }
    }

    private void syncLinks() {
        setChanged();

        invalidateNetwork();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
        }
    }

    private int syncedCarried = Integer.MIN_VALUE;
    private int syncedBlockFlow = Integer.MIN_VALUE;
    private int syncedShape = Integer.MIN_VALUE;
    private int ticksSinceReadoutSync;

    private void syncReadoutIfDue() {
        ticksSinceReadoutSync++;
        final int shape = java.util.Objects.hash(servesBlock, branchCeiling, cableFeed, transferRate());
        if (!moved(carried, syncedCarried) && !moved(blockFlow, syncedBlockFlow)
                && shape == syncedShape && ticksSinceReadoutSync < 20) {
            return;
        }
        syncedCarried = carried;
        syncedBlockFlow = blockFlow;
        syncedShape = shape;
        ticksSinceReadoutSync = 0;
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(),
                    net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
        }
    }

    private static boolean moved(int now, int sent) {
        return Math.abs((long) now - sent) >= Math.max(1, Math.abs(now) / 32);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Energy", energy);

        tag.putInt("LastFlow", lastFlow);

        tag.putInt("CableFeed", cableFeed);
        tag.putInt("Carried", carried);
        tag.putInt("BlockFlow", blockFlow);
        tag.putBoolean("ServesBlock", servesBlock);
        tag.putInt("BranchCeiling", branchCeiling);
        final byte[] tiers = new byte[links.size()];
        for (int i = 0; i < links.size(); i++) {
            tiers[i] = (byte) tierOf(links.get(i)).ordinal();
        }
        tag.putByteArray("LinkTiers", tiers);

        final byte[] tension = new byte[links.size()];
        for (int i = 0; i < links.size(); i++) {
            final WireTension set = linkTension.get(links.get(i).asLong());
            tension[i] = (byte) (set == null ? -1 : set.id());
        }
        tag.putByteArray("LinkTension", tension);
        final long[] longs = new long[links.size()];
        for (int i = 0; i < links.size(); i++) {
            longs[i] = links.get(i).asLong();
        }
        tag.putLongArray("Links", longs);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        energy = Math.max(0, tag.getInt("Energy"));
        if (tag.contains("LastFlow")) {
            lastFlow = tag.getInt("LastFlow");
        }
        cableFeed = Math.max(0, tag.getInt("CableFeed"));
        carried = Math.max(0, tag.getInt("Carried"));
        blockFlow = tag.getInt("BlockFlow");

        servesBlock = !tag.contains("ServesBlock") || tag.getBoolean("ServesBlock");
        branchCeiling = tag.contains("BranchCeiling") ? tag.getInt("BranchCeiling")
                : com.createstats.power.FeGridSolver.NO_CEILING;
        links.clear();
        linkTiers.clear();
        linkTension.clear();

        final byte[] tiers = tag.getByteArray("LinkTiers");
        final byte[] tension = tag.getByteArray("LinkTension");
        final long[] packedLinks = tag.getLongArray("Links");
        for (int i = 0; i < packedLinks.length; i++) {
            final BlockPos pos = BlockPos.of(packedLinks[i]);
            links.add(pos);
            if (i < tiers.length) {
                linkTiers.put(packedLinks[i], WireTier.byOrdinal(tiers[i]));
            }
            if (i < tension.length && tension[i] >= 0) {
                linkTension.put(packedLinks[i], WireTension.byId(tension[i]));
            }
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        final CompoundTag tag = super.getUpdateTag(registries);
        saveAdditional(tag, registries);
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        loadAdditional(tag, registries);
    }

    // wires add up like parallel conductors, capped at the connector's own rating
    @Override
    public int transferRate() {
        return com.createstats.power.FeRates.connectorThroughput(baseTransferRate(), connectorTier(),
                spanTiers(), cableFeed);
    }

    public com.createstats.power.ConnectorTier connectorTier() {
        return getBlockState().getBlock() instanceof PowerConnectorBlock block
                ? block.tier() : com.createstats.power.ConnectorTier.POWER;
    }

    public static int baseTransferRate() {
        try {
            return ModConfiguration.COMMON.connectorTransferRate.get();
        } catch (RuntimeException ignored) {
            return 8192;
        }
    }

    @Override
    public int bufferCapacity() {
        try {
            return ModConfiguration.COMMON.connectorBufferCapacity.get();
        } catch (RuntimeException ignored) {
            return DEFAULT_BUFFER;
        }
    }

    public static final int DEFAULT_BUFFER = 16_000;

    public static int wireRange() {
        try {
            return ModConfiguration.COMMON.connectorWireRange.get();
        } catch (RuntimeException ignored) {
            return 32;
        }
    }

    @Override
    public int maxLinks() {
        return connectorTier().maxLinks();
    }
}
