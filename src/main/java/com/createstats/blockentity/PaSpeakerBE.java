package com.createstats.blockentity;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.createstats.pa.SpeakerKind;
import com.createstats.power.FeGridMember;
import com.createstats.power.FeIdleLoad;
import com.createstats.power.FeLock;
import com.createstats.power.FeManagedLoad;
import com.createstats.power.FeManagedState;
import com.createstats.power.FePriority;
import com.createstats.power.FeRatedLoad;
import com.createstats.power.FeWireNetwork;
import com.createstats.power.FeWireTerminal;
import com.createstats.power.WireTier;
import com.createstats.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;

// a speaker carries what the computer decided, it extends hearing range not scan range
public class PaSpeakerBE extends BlockEntity implements FeGridMember, FeManagedLoad, FeWireTerminal,
        com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation {
    private static final int SPEAKING_TAIL = 6;

    private static final int LINK_CHECK_TICKS = 20;

    private final SpeakerKind kind;

    private int energy;
    private int lastSyncedEnergy;
    private boolean powered;

    private int ratedDraw;
    private int measuredDraw;
    private final FeManagedState managed = new FeManagedState();
    private final List<BlockPos> links = new ArrayList<>();
    private final Map<Long, WireTier> linkTiers = new HashMap<>();
    @Nullable
    private FeWireNetwork network;

    private int speakingFor;
    private int phase;

    private boolean linkedToBrain;

    private final IEnergyStorage energyView = new IEnergyStorage() {
        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            final int room = Math.min(maxReceive, capacity() - energy);
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
            return capacity();
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

    public PaSpeakerBE(BlockEntityType<?> type, BlockPos pos, BlockState state, SpeakerKind kind) {
        super(type, pos, state);
        this.kind = kind;
    }

    public SpeakerKind kind() {
        return this.kind;
    }

    public int capacity() {
        return this.kind.bufferFe();
    }

    public int storedEnergyFe() {
        return this.energy;
    }

    public boolean isPowered() {
        return this.powered;
    }

    public boolean isSpeaking() {
        return this.speakingFor > 0;
    }

    public void markSpoke(int ticks) {
        final int hold = Math.max(SPEAKING_TAIL, ticks + SPEAKING_TAIL);

        final boolean stale = hold > this.speakingFor;
        this.speakingFor = hold;
        if (stale) {
            this.markDirtyAndSync();
        }
    }

    public void stopSpeaking() {
        if (this.speakingFor > 0) {
            this.speakingFor = 0;
            this.markDirtyAndSync();
        }
    }

    public void serverTick() {
        if (!(this.level instanceof ServerLevel server)) {
            return;
        }
        if (this.network == null || this.network.isDirty()) {
            FeWireNetwork.buildFrom(this);
        }
        if (this.network != null) {
            this.network.solveOnce(server);
        }

        if (this.network == null || !this.network.isPduManaged(server.getGameTime())) {
            this.setManagedShed(false);
        }

        final FeIdleLoad load = FeIdleLoad.of(this.managed.isSuppressed(), this.energy,
                this.kind.idleDrawFe());
        final boolean nowPowered = load.powered();
        this.energy -= load.spend();
        this.ratedDraw = load.rated();
        this.measuredDraw = load.measured();
        if (nowPowered != this.powered) {
            this.powered = nowPowered;

            if (!nowPowered) {
                this.speakingFor = 0;
            }
            this.markDirtyAndSync();
        }
        if (this.speakingFor > 0 && --this.speakingFor == 0) {
            this.markDirtyAndSync();
        }

        final long now = server.getGameTime();

        if ((now + Math.floorMod(this.worldPosition.hashCode(), LINK_CHECK_TICKS)) % LINK_CHECK_TICKS
                == 0) {
            final boolean linked = !com.createstats.pa.PaSpeakerLink.brainsFor(server, this).isEmpty();
            if (linked != this.linkedToBrain) {
                this.linkedToBrain = linked;
                this.markDirtyAndSync();
            }
        }
        if (Math.abs(this.energy - this.lastSyncedEnergy) * 8 >= this.capacity() || now % 200 == 0) {
            this.lastSyncedEnergy = this.energy;
            this.markDirtyAndSync();
        }
    }

    public void clientTick() {
        this.phase++;
        if (this.speakingFor > 0) {
            this.speakingFor--;
        }
    }

    public float statusLevel(float partialTick) {
        if (!this.powered) {
            return 0.0f;
        }
        if (this.speakingFor > 0) {
            final float t = (this.phase + partialTick) * 0.42f;
            return 0.72f + 0.28f * Mth.sin(t);
        }
        return 0.34f;
    }

    public boolean statusIsHealthy() {
        return this.linkedToBrain;
    }

    public float voiceLevel(float partialTick) {
        if (!this.powered || this.speakingFor <= 0) {
            return 0.0f;
        }
        final float remaining = Math.max(0.0f, this.speakingFor - partialTick);

        final float envelope = Math.min(1.0f, remaining / SPEAKING_TAIL);
        final float shimmer = 0.86f + 0.14f * Mth.sin((this.phase + partialTick) * 0.9f);
        return envelope * shimmer;
    }

    @Override
    public long nodeKey() {
        return this.worldPosition.asLong();
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
        return Math.max(32, this.kind.idleDrawFe() * 16);
    }

    @Override
    public IEnergyStorage attachedStorage() {
        return this.energyView;
    }

    @Override
    public boolean isGone(Level owner) {
        return this.isRemoved() || this.getLevel() != owner;
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

    @Nullable
    public FeWireNetwork feNetwork() {
        return this.network;
    }

    @Override
    public void invalidateNetwork() {
        if (this.network != null) {
            this.network.markDirty();
            this.network = null;
        }
    }

    public IEnergyStorage energyHandle() {
        return this.energyView;
    }

    @Override
    public BlockPos terminalPos() {
        return this.worldPosition;
    }

    @Override
    public List<BlockPos> links() {
        return this.links;
    }

    @Override
    public WireTier tierOf(BlockPos link) {
        return this.linkTiers.getOrDefault(link.asLong(), WireTier.COPPER);
    }

    @Override
    public void addLink(BlockPos far, WireTier tier) {
        final BlockPos at = far.immutable();
        this.links.add(at);
        this.linkTiers.put(at.asLong(), tier == null ? WireTier.COPPER : tier);
        this.syncLinks();
    }

    @Override
    public void removeDeadLink(BlockPos far) {
        this.linkTiers.remove(far.asLong());
        if (this.links.remove(far)) {
            this.syncLinks();
        }
    }

    @Override
    public void dropWiresAndUnlinkAll() {
        if (this.level == null || this.level.isClientSide()) {
            return;
        }
        final Map<WireTier, Integer> refund = new EnumMap<>(WireTier.class);
        for (BlockPos link : new ArrayList<>(this.links)) {
            refund.merge(this.tierOf(link), 1, Integer::sum);

            if (this.level.getBlockEntity(link) instanceof com.createstats.power.FeStudPanel panel) {
                panel.forgetLinksTo(this.worldPosition);
            } else if (this.level.getBlockEntity(link) instanceof FeWireTerminal other && other != this) {
                other.removeDeadLink(this.worldPosition);
            }
        }
        this.links.clear();
        this.linkTiers.clear();
        this.syncLinks();
        for (Map.Entry<WireTier, Integer> e : refund.entrySet()) {
            Block.popResource(this.level, this.worldPosition,
                    new ItemStack(ModItems.wireItem(e.getKey()), e.getValue()));
        }
    }

    private void syncLinks() {
        this.setChanged();
        this.invalidateNetwork();
        if (this.level != null && !this.level.isClientSide()) {
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(),
                    Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public FePriority managedPriority() {
        return this.managed.priority();
    }

    @Override
    public void setManagedPriority(FePriority tier) {
        this.managed.setPriority(tier);
        this.setChanged();
    }

    @Override
    public FeLock managedLock() {
        return this.managed.lock();
    }

    @Override
    public void setManagedLock(FeLock lock) {
        this.managed.setLock(lock);
        this.setChanged();
    }

    @Override
    public void setManagedShed(boolean shed) {
        if (this.managed.isShed() != shed) {
            this.managed.setShed(shed);
            this.setChanged();
        }
    }

    @Override
    public boolean isManagedShed() {
        return this.managed.isShed();
    }

    @Override
    public int ratedDrawFePerTick() {
        return this.ratedDraw;
    }

    @Override
    public int measuredDrawFePerTick() {
        return this.measuredDraw;
    }

    @Override
    public boolean addToGoggleTooltip(List<net.minecraft.network.chat.Component> tooltip,
                                      boolean isPlayerSneaking) {
        com.simibubi.create.foundation.utility.CreateLang
                .translate(this.kind == SpeakerKind.MEGAPHONE
                        ? "gui.goggles.pa_megaphone" : "gui.goggles.pa_speaker")
                .forGoggles(tooltip);

        final String state;
        final net.minecraft.ChatFormatting colour;
        if (this.powered) {
            state = "Powered";
            colour = net.minecraft.ChatFormatting.GREEN;
        } else if (this.managed.lock() == FeLock.FORCE_OFF) {
            state = "Switched off at the power panel";
            colour = net.minecraft.ChatFormatting.YELLOW;
        } else if (this.managed.isShed()) {
            state = "Shed by the power panel";
            colour = net.minecraft.ChatFormatting.YELLOW;
        } else {
            state = "No power";
            colour = net.minecraft.ChatFormatting.RED;
        }
        com.simibubi.create.foundation.utility.CreateLang.text(state).style(colour)
                .forGoggles(tooltip, 1);
        com.simibubi.create.foundation.utility.CreateLang
                .text("Heard " + this.kind.range() + " blocks out, "
                        + this.kind.halfHeight() + " up and down")
                .style(net.minecraft.ChatFormatting.GRAY)
                .forGoggles(tooltip, 1);
        com.simibubi.create.foundation.utility.CreateLang
                .text(this.energy + " / " + this.capacity() + " FE")
                .style(net.minecraft.ChatFormatting.GRAY)
                .forGoggles(tooltip, 1);

        com.simibubi.create.foundation.utility.CreateLang
                .text(this.linkedToBrain ? "On a circuit with an Announcement Computer"
                        : "No Announcement Computer on this circuit")
                .style(this.linkedToBrain ? net.minecraft.ChatFormatting.GREEN
                        : net.minecraft.ChatFormatting.YELLOW)
                .forGoggles(tooltip, 1);
        return true;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Energy", this.energy);
        tag.putBoolean("Powered", this.powered);
        tag.putBoolean("Linked", this.linkedToBrain);
        tag.putInt("Priority", this.managed.priorityOrdinal());
        tag.putInt("Lock", this.managed.lockOrdinal());
        final ListTag wires = new ListTag();
        for (BlockPos link : this.links) {
            final CompoundTag one = new CompoundTag();
            one.putLong("P", link.asLong());
            one.putString("T", this.tierOf(link).name());
            wires.add(one);
        }
        tag.put("Links", wires);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.energy = Math.max(0, Math.min(this.capacity(), tag.getInt("Energy")));
        this.powered = tag.getBoolean("Powered");
        this.linkedToBrain = tag.getBoolean("Linked");

        this.speakingFor = tag.getInt("Speaking");
        this.managed.load(tag.getInt("Priority"), tag.getInt("Lock"));
        this.links.clear();
        this.linkTiers.clear();
        final ListTag wires = tag.getList("Links", Tag.TAG_COMPOUND);
        for (int i = 0; i < wires.size(); i++) {
            final CompoundTag one = wires.getCompound(i);
            final BlockPos at = BlockPos.of(one.getLong("P"));
            this.links.add(at);
            try {
                this.linkTiers.put(at.asLong(), WireTier.valueOf(one.getString("T")));
            } catch (IllegalArgumentException e) {
                this.linkTiers.put(at.asLong(), WireTier.COPPER);
            }
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        final CompoundTag tag = super.getUpdateTag(registries);
        this.saveAdditional(tag, registries);
        tag.putInt("Speaking", this.speakingFor);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    public void markDirtyAndSync() {
        this.setChanged();
        if (this.level != null && !this.level.isClientSide()) {
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
        }
    }
}
