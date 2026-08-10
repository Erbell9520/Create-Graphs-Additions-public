package com.createstats.blockentity;

import com.createstats.ModConfiguration;
import com.createstats.block.PowerConnectorBlock;
import com.createstats.power.FeWireNetwork;
import com.createstats.power.WireTier;
import com.createstats.registry.ModBlocks;
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
import java.util.Locale;

/** [1] */
public class PowerConnectorBE extends BlockEntity implements com.createstats.power.FeGridMember,
        com.createstats.power.FeWireTerminal,
        com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation {

    private final List<BlockPos> links = new ArrayList<>();
    /** [2] */
    private final java.util.Map<Long, WireTier> linkTiers = new java.util.HashMap<>();
    private int energy;
    /** The connected component this node belongs to. Rebuilt lazily whenever it is null or dirty. */
    @Nullable
    private FeWireNetwork network;

    /** [3] */
    private int lastFlow;
    /** Minimum ticks between two flow syncs even when the value has not changed, as drift insurance. */
    private static final int FLOW_SYNC_HEARTBEAT_TICKS = 20;
    private int lastSyncedFlow = Integer.MIN_VALUE;
    private int ticksSinceFlowSync = FLOW_SYNC_HEARTBEAT_TICKS;

    private final IEnergyStorage energyView = new IEnergyStorage() {
        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            final int amount = Math.min(Math.min(maxReceive, transferRate()), bufferCapacity() - energy);
            if (amount <= 0) {
                return 0;
            }
            if (!simulate) {
                energy += amount;
                setChanged();
            }
            return amount;
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            final int amount = Math.min(Math.min(maxExtract, transferRate()), energy);
            if (amount <= 0) {
                return 0;
            }
            if (!simulate) {
                energy -= amount;
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

    /** The grade of the wire running to {@code link}, or {@link WireTier#COPPER} if untiered. */
    public WireTier tierOf(BlockPos link) {
        return linkTiers.getOrDefault(link.asLong(), WireTier.COPPER);
    }

    /** The best wire attached here, which is what this node's throughput is allowed to reach. */
    public WireTier bestWireTier() {
        WireTier best = WireTier.COPPER;
        for (WireTier tier : linkTiers.values()) {
            if (tier.multiplier() > best.multiplier()) {
                best = tier;
            }
        }
        return best;
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
            // Rebuilds the whole connected component and hands every member the same instance, so
            // this costs one flood fill per topology change, not one per node per tick.
            FeWireNetwork.buildFrom(this);
        }
        if (network != null) {
            // Whichever member ticks first this tick runs the grid solve; the rest fall through.
            network.solveOnce(level);
        }
        // [4]
        syncReadout(lastFlow * 31 + energy / 8);
    }

    // grid seam (used by FeWireNetwork)

    /** Set by the grid solve (or the PDU takeover) every tick it runs. Not called from anywhere else. */
    public void setLastFlow(int flow) {
        this.lastFlow = flow;
    }

    @Override
    public boolean isGone(net.minecraft.world.level.Level owner) {
        return isRemoved() || getLevel() != owner;
    }

    /**
     * Signed FE/t between the grid and the attached block, as of the last solve. Positive feeds the
     * attached block, negative draws from it, zero is idle.
     */
    public int lastFlow() {
        return lastFlow;
    }

    /** [5] */
    private void syncFlowIfDue() {
        ticksSinceFlowSync++;
        if (lastFlow == lastSyncedFlow && ticksSinceFlowSync < FLOW_SYNC_HEARTBEAT_TICKS) {
            return;
        }
        lastSyncedFlow = lastFlow;
        ticksSinceFlowSync = 0;
        setChanged();
        if (level != null && !level.isClientSide()) {
            // Flag 2: clients only, matching syncLinks(). A flow change is not a redstone event.
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
        }
    }

    /** Goggle readout: what is passing between the grid and whatever this connector is mounted on. */
    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        tooltip.add(Component.literal("    ").append(
                Component.literal("Power Connector").withStyle(ChatFormatting.GRAY)));
        final String flowLine;
        final ChatFormatting flowColor;
        if (lastFlow > 0) {
            flowLine = String.format(Locale.ROOT, "Feeding attached block: %,d FE/t", lastFlow);
            flowColor = ChatFormatting.GREEN;
        } else if (lastFlow < 0) {
            flowLine = String.format(Locale.ROOT, "Drawing from attached block: %,d FE/t", -lastFlow);
            flowColor = ChatFormatting.AQUA;
        } else {
            flowLine = "Idle";
            flowColor = ChatFormatting.DARK_GRAY;
        }
        tooltip.add(Component.literal("    ").append(Component.literal(flowLine).withStyle(flowColor)));
        tooltip.add(Component.literal("    ").append(Component
                .literal(String.format(Locale.ROOT, "Buffer: %,d / %,d FE", energy, bufferCapacity()))
                .withStyle(ChatFormatting.AQUA)));
        return true;
    }

    @Override
    public long nodeKey() {
        return worldPosition.asLong();
    }

    /** The block this connector is mounted on, as an energy handler, or null if it has none. */
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

    /** [6] */
    public Direction mountedOn() {
        return (getBlockState().hasProperty(PowerConnectorBlock.FACING)
                ? getBlockState().getValue(PowerConnectorBlock.FACING)
                : Direction.UP).getOpposite();
    }

    /** Adopt the network this node was found to belong to. Called only by {@link FeWireNetwork}. */
    public void adoptNetwork(FeWireNetwork network) {
        this.network = network;
    }

    /** The grid this node is part of, if it has been built yet. */
    @Nullable
    public FeWireNetwork network() {
        return network;
    }

    /** Set by the grid solve when it settles the pool back across node buffers. */
    public void setStoredEnergy(int amount) {
        final int clamped = Math.max(0, Math.min(amount, bufferCapacity()));
        if (clamped != energy) {
            energy = clamped;
            setChanged();
        }
    }

    /** Drops one link that has been proven to point at something that is no longer a connector. */
    public void removeDeadLink(BlockPos link) {
        linkTiers.remove(link.asLong());
        if (links.remove(link)) {
            syncLinks();
        }
    }

    /** Forces this node's network to rebuild on the next tick. */
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

    // wire link management (driven by CopperWireItem)

    /** [7] */
    public void addStudLink(BlockPos panel, WireTier tier) {
        final BlockPos at = panel.immutable();
        links.add(at);
        linkTiers.put(at.asLong(), tier == null ? WireTier.COPPER : tier);
        syncLinks();
    }

    /** Forgets the wire to a panel. The panel refunds it, or has already been broken. */
    public void removeStudLink(BlockPos panel) {
        linkTiers.remove(panel.asLong());
        if (links.remove(panel)) {
            syncLinks();
        }
    }

    /** Links both ends with a wire of the given grade. Caller has validated range and capacity. */
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

    /** One side of a link, so a wire to a machine's built-in connector is stored the same way. */
    @Override
    public void addLink(BlockPos far, WireTier tier) {
        final BlockPos at = far.immutable();
        links.add(at);
        linkTiers.put(at.asLong(), tier == null ? WireTier.COPPER : tier);
        syncLinks();
    }

    /** Removes every wire on this node (both sides) and drops one Copper Wire per removed link. */
    public void dropWiresAndUnlinkAll() {
        if (level == null || level.isClientSide()) {
            return;
        }
        // Counted per grade, so a mixed node returns exactly what was spent on it rather than a
        // handful of copper regardless of what the player actually strung up.
        final java.util.Map<WireTier, Integer> refund = new java.util.EnumMap<>(WireTier.class);
        for (BlockPos link : new ArrayList<>(links)) {
            refund.merge(tierOf(link), 1, Integer::sum);
            if (level.getBlockEntity(link) instanceof PowerConnectorBE other) {
                other.links.remove(worldPosition);
                other.linkTiers.remove(worldPosition.asLong());
                other.syncLinks();
            } else if (level.getBlockEntity(link)
                    instanceof PowerDistributionUnitBE panel) {
                // The panel's leg loses this wire too. Forgetting rather than dropping, because the
                // refund below already returns it: dropping at both ends would mint a second wire.
                panel.forgetLinksTo(worldPosition);
            }
        }
        links.clear();
        linkTiers.clear();
        syncLinks();
        for (java.util.Map.Entry<WireTier, Integer> e : refund.entrySet()) {
            Block.popResource(level, worldPosition,
                    new ItemStack(ModItems.wireItem(e.getKey()), e.getValue()));
        }
    }

    private void syncLinks() {
        setChanged();
        // Topology changed, so the connected component this node belongs to is no longer valid.
        invalidateNetwork();
        if (level != null && !level.isClientSide()) {
            // Flag 2: clients only. The wire renderer needs the new link list; the neighbours do
            // not need a redstone update out of a wiring change.
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
        }
    }

    // persistence + client sync


    /** Last readout actually sent, and how long ago, so a still block costs nothing. */
    private int lastSyncedReadout = Integer.MIN_VALUE;
    private int ticksSinceReadoutSync;

    /** [8] */
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
                    net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Energy", energy);
        // [9]
        tag.putInt("LastFlow", lastFlow);
        final byte[] tiers = new byte[links.size()];
        for (int i = 0; i < links.size(); i++) {
            tiers[i] = (byte) tierOf(links.get(i)).ordinal();
        }
        tag.putByteArray("LinkTiers", tiers);
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
        links.clear();
        linkTiers.clear();
        // Written alongside Links and in the same order. A world saved before wire tiers existed has
        // no LinkTiers at all, and every link correctly falls back to plain copper.
        final byte[] tiers = tag.getByteArray("LinkTiers");
        final long[] packedLinks = tag.getLongArray("Links");
        for (int i = 0; i < packedLinks.length; i++) {
            final BlockPos pos = BlockPos.of(packedLinks[i]);
            links.add(pos);
            if (i < tiers.length) {
                linkTiers.put(packedLinks[i], WireTier.byOrdinal(tiers[i]));
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

    // config

    /** [10] */
    @Override
    public int transferRate() {
        final int base = baseTransferRate();
        final int connectorTier = getBlockState().getBlock() == ModBlocks.ADVANCED_POWER_CONNECTOR.get()
                ? advancedMultiplier() : 1;
        // A connector with nothing attached is rated at its own grade; there is no wire to cap it.
        final int wireTier = links.isEmpty() ? connectorTier : bestWireTier().multiplier();
        return base * Math.min(connectorTier, wireTier);
    }

    /** The base rate one plain connector on plain copper moves. Every tier is a multiple of this. */
    public static int baseTransferRate() {
        try {
            return ModConfiguration.COMMON.connectorTransferRate.get();
        } catch (RuntimeException ignored) {
            return 8192;
        }
    }

    /** Throughput multiplier of the Advanced Power Connector over the plain one. */
    public static int advancedMultiplier() {
        try {
            return ModConfiguration.COMMON.connectorAdvancedMultiplier.get();
        } catch (RuntimeException ignored) {
            return 3;
        }
    }

    @Override
    public int bufferCapacity() {
        try {
            return ModConfiguration.COMMON.connectorBufferCapacity.get();
        } catch (RuntimeException ignored) {
            return 4096;
        }
    }

    public static int wireRange() {
        try {
            return ModConfiguration.COMMON.connectorWireRange.get();
        } catch (RuntimeException ignored) {
            return 32;
        }
    }

    public static int maxLinks() {
        try {
            return ModConfiguration.COMMON.connectorMaxLinks.get();
        } catch (RuntimeException ignored) {
            return 4;
        }
    }
}
