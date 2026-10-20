package com.createstats.blockentity;

import com.createstats.block.SecurityRelayBlock;
import com.createstats.power.BoreLinks;
import com.createstats.power.FeGridMember;
import com.createstats.power.FeLock;
import com.createstats.power.FeManagedLoad;
import com.createstats.power.FeManagedState;
import com.createstats.power.FePriority;
import com.createstats.power.FeWireNetwork;
import com.createstats.power.SecurityDevice;
import com.createstats.security.PanelAccess;
import com.createstats.security.RelayDevice;
import com.createstats.security.SecurityEndpoint;
import com.createstats.security.SecurityHousehold;
import com.createstats.security.SecurityHouseholds;
import com.createstats.security.SecurityIdentity;
import com.createstats.security.SecurityLink;
import com.createstats.security.SecuritySegment;
import com.createstats.security.Truth;
import com.createstats.security.relay.RelayBehavior;
import com.createstats.security.relay.RelayCodec;
import com.createstats.security.relay.RelayCondition;
import com.createstats.security.relay.RelayConfig;
import com.createstats.security.relay.RelayContact;
import com.createstats.security.relay.RelayPower;
import com.createstats.security.relay.RelayReading;
import com.createstats.security.relay.RelayRules;
import com.createstats.security.relay.RelayState;
import com.createstats.security.relay.RelaySubscription;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// adapter only, every rule is in RelayRules
public class SecurityRelayBE extends BlockEntity implements FeGridMember, FeManagedLoad, SecurityDevice,
        RelayDevice, BoreLinks.Socket, com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation {
    private static final int POLICY_TICKS = 40;

    public static final double REACH_SQ = 64.0;

    private int energy;
    @Nullable
    private FeWireNetwork network;
    private final FeManagedState managed = new FeManagedState();
    private int ratedDraw;
    private int measuredDraw;
    private final List<BlockPos> bores = new ArrayList<>(1);

    private int sincePolicy = POLICY_TICKS;

    @Nullable
    private UUID relayId;

    @Nullable
    private UUID placer;
    private String placerName = "";

    @Nullable
    private BlockPos panelPos;

    @Nullable
    private UUID claimed;

    @Nullable
    private BlockPos offeredPanel;

    private RelayContact contact = RelayContact.UNBOUND;

    private RelayConfig config = RelayConfig.DEFAULT;
    private RelayState received = RelayState.seed(0);
    private RelayReading reading = RelayReading.NONE;
    private long revision;

    private long seenTrigger = RelayState.NEVER;

    private int output;

    private boolean powered;

    private long outputChangedAt = Long.MIN_VALUE;
    private long pendingChangedAt = Long.MIN_VALUE;
    private long resetPressedAt = Long.MIN_VALUE;

    private int pulseLeft;

    private boolean syncDue = true;

    private final IEnergyStorage energyView = new IEnergyStorage() {
        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            final int room = Math.min(Math.min(maxReceive, RelayPower.INTAKE), RelayPower.BUFFER - energy);
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
            return RelayPower.BUFFER;
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

    public SecurityRelayBE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);

        managed.setPriority(FePriority.CRITICAL);
    }

    public IEnergyStorage energyHandle() {
        return energyView;
    }

    public static int drawPerTick() {
        return RelayPower.RATED;
    }

    public void tick() {
        if (level == null || level.isClientSide()) {
            return;
        }
        final long now = level.getGameTime();
        if (relayId == null) {
            relayId = UUID.randomUUID();
            setChanged();
        }
        if (network == null || network.isDirty()) {
            if (BoreLinks.prune(level, this)) {
                return;
            }
            FeWireNetwork.buildFrom(this);
        }
        if (network != null) {
            network.solveOnce(level);
        }
        if (network == null || !network.isPduManaged(now)) {
            setManagedShed(false);
        }
        ratedDraw = RelayPower.rated(managed.isForcedOff());

        if (++sincePolicy >= POLICY_TICKS) {
            sincePolicy = 0;
            findPolicy(now);
        }

        if (contact == RelayContact.LINKED) {
            final RelaySubscription sub = subscription();
            if (sub == null) {
                sincePolicy = POLICY_TICKS;
                contact = RelayContact.LOST;
            } else {
                final SecurityHousehold h = household();
                if (h != null) {
                    h.advance(now);
                }
                receive(sub, now);
            }
        }

        final RelayRules.Output want = RelayRules.signal(config, received, reading.truth(), true, contact, now,
                seenTrigger);
        final RelayPower.Tick paid = RelayPower.step(energy, managed.isSuppressed(), want.level() > 0);
        energy = paid.energyAfter();
        measuredDraw = paid.paid();
        final boolean nowPowered = paid.logicRuns();
        final int next = paid.coilRuns() ? want.level() : 0;
        if (paid.coilRuns() && want.level() > 0) {
            seenTrigger = want.seenTrigger();
        }
        if (nowPowered != powered) {
            powered = nowPowered;
            syncDue = true;
        }
        if (next != output) {
            output = next;
            outputChangedAt = now;
            setChanged();
            level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
            syncDue = true;
        }
        final int left = pulseLeft(now);
        if (left != pulseLeft) {
            pulseLeft = left;
            syncDue = true;
        }
        if (paid.paid() > 0) {
            setChanged();
        }
        if (syncDue) {
            syncDue = false;
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    private void receive(RelaySubscription sub, long now) {
        final boolean wasPending = received.pending();
        final boolean changed = !sub.config().equals(config) || sub.revision() != revision
                || RelayRules.savedFieldsDiffer(received, sub.state())
                || sub.reading().truth() != reading.truth();
        config = sub.config();
        revision = sub.revision();
        received = sub.state();
        reading = sub.reading();
        if (received.pending() != wasPending) {
            pendingChangedAt = now;
        }
        if (changed) {
            setChanged();
            syncDue = true;
        }
    }

    private int pulseLeft(long now) {
        if (config.behavior() != RelayBehavior.PULSE || received.pulseUntil() == RelayState.NEVER
                || now >= received.pulseUntil()) {
            return 0;
        }
        return (int) Math.min(RelayConfig.MAX_PULSE, received.pulseUntil() - now);
    }

    private void findPolicy(long now) {
        final SecurityLink.Contact<HomeSecurityPanelBE> found = SecurityLink.resolve(panel(),
                panelPos != null, panelPos != null && level.isLoaded(panelPos),
                network != null || offeredPanel != null, this::searchCircuit);
        final RelayContact was = contact;
        switch (found.state()) {
            case UNKNOWN -> contact = claimed != null ? RelayContact.UNKNOWN
                    : (contact == RelayContact.CANDIDATE ? RelayContact.CANDIDATE : RelayContact.UNBOUND);
            case LOST -> {
                panelPos = null;
                contact = claimed != null ? RelayContact.LOST : RelayContact.UNBOUND;
            }
            case LINKED -> link(found.panel(), now);
        }
        if (contact != was) {
            setChanged();
            syncDue = true;
        }
    }

    private void link(@Nullable HomeSecurityPanelBE panel, long now) {
        if (panel == null) {
            return;
        }
        panelPos = panel.getBlockPos();
        offeredPanel = null;
        final SecurityHousehold h = panel.household();
        if (h == null) {
            return;
        }
        final SecurityEndpoint here = SecurityIdentity.at(level, worldPosition);

        RelaySubscription sub = h.relayStandingAt(relayId, here, now);
        if (sub != null && !sub.endpoint().equals(here)) {
            relayId = UUID.randomUUID();
            claimed = null;
            sub = null;
        }
        if (sub == null && placer != null && h.roster().roleOf(placer).mayEditDevices()) {
            sub = h.claimRelay(relayId, here, placer, placerName, now);
        }
        if (sub == null) {
            claimed = null;
            contact = RelayContact.CANDIDATE;
            return;
        }
        claimed = h.id();
        contact = RelayContact.LINKED;
        receive(sub, now);
    }

    @Nullable
    private HomeSecurityPanelBE searchCircuit() {
        final HomeSecurityPanelBE wired = com.createstats.security.PanelSweep.nearest(level, network,
                worldPosition, worldPosition, claimed);
        return wired != null ? wired
                : SecurityIdentity.offered(level, offeredPanel, worldPosition, claimed);
    }

    @Nullable
    public HomeSecurityPanelBE panel() {
        if (level == null || panelPos == null || !level.isLoaded(panelPos)) {
            return null;
        }
        return level.getBlockEntity(panelPos) instanceof HomeSecurityPanelBE panel
                && SecurityIdentity.accepts(level, claimed, worldPosition, panel) ? panel : null;
    }

    @Nullable
    public SecurityHousehold household() {
        if (claimed == null) {
            return null;
        }
        final SecurityHouseholds store = SecurityHouseholds.of(level);
        return store == null ? null : store.find(claimed);
    }

    @Nullable
    public SecurityHousehold candidateHousehold() {
        final HomeSecurityPanelBE p = panelPos == null || level == null || !level.isLoaded(panelPos) ? null
                : level.getBlockEntity(panelPos) instanceof HomeSecurityPanelBE found ? found : null;
        return p == null ? null : p.household();
    }

    @Nullable
    private RelaySubscription subscription() {
        final SecurityHousehold h = household();
        return h == null || relayId == null ? null : h.relay(relayId);
    }

    @Nullable
    private SecurityHousehold admissionHousehold() {
        return contact == RelayContact.CANDIDATE ? candidateHousehold() : household();
    }

    public boolean admits(ServerPlayer player) {
        if (player == null || level == null) {
            return false;
        }
        final SecurityHousehold h = admissionHousehold();
        if (h == null) {
            return true;
        }
        final UUID id = player.getUUID();
        return PanelAccess.demand(h.hasPassword(), h.alwaysAsk(), h.roster().isOwner(id), h.roster().levelOf(id))
                == PanelAccess.Entry.OPEN || h.admitted(id, level.getGameTime());
    }

    public void refreshAdmission(ServerPlayer player) {
        final SecurityHousehold h = admissionHousehold();
        if (h != null && player != null && level != null) {
            h.refreshAdmission(player.getUUID(), level.getGameTime());
        }
    }

    public void askPassword(ServerPlayer player) {
        final SecurityHousehold h = admissionHousehold();
        if (h == null || player == null) {
            return;
        }
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                com.createstats.network.OpenSecurityPanelPacket.askAtRelay(worldPosition, h.house()));
    }

    public void offerPassword(ServerPlayer player, String digits) {
        final SecurityHousehold h = admissionHousehold();
        if (player == null || level == null) {
            return;
        }
        if (h == null) {
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                    com.createstats.network.OpenSecurityPanelPacket.shut(worldPosition));
            return;
        }
        final long now = level.getGameTime();
        final com.createstats.security.LockerCode.Result result = h.offerPassword(digits, now);
        if (result == com.createstats.security.LockerCode.Result.OPENED) {
            h.admit(player.getUUID(), now);
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                    com.createstats.network.SecurityRelaySyncPacket.open(this, player));
            return;
        }
        if (result == com.createstats.security.LockerCode.Result.TAMPER) {
            final String cause = player.getGameProfile().getName() + " failed the house code at a Security Relay";
            final HomeSecurityPanelBE p = admissionPanel();
            if (p != null) {
                p.tripped(worldPosition, com.createstats.security.SensorKind.TAMPER, cause);
            } else {
                h.log().add(now, "relay", "Security Relay", cause,
                        com.createstats.security.SecurityLog.Verdict.FAULT);
            }
        }
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                com.createstats.network.OpenSecurityPanelPacket.verdict(worldPosition, result,
                        (h.passwordLockout(now) + 19) / 20, h.house(),
                        com.createstats.network.OpenSecurityPanelPacket.RELAY));
    }

    @Nullable
    private HomeSecurityPanelBE admissionPanel() {
        if (level == null || panelPos == null || !level.isLoaded(panelPos)) {
            return null;
        }
        final SecurityHousehold h = admissionHousehold();
        return h != null && level.getBlockEntity(panelPos) instanceof HomeSecurityPanelBE p
                && h.id().equals(p.householdId()) ? p : null;
    }

    public RelayRules.ResetVerdict pressReset(ServerPlayer player) {
        if (level == null) {
            return RelayRules.ResetVerdict.NO_CONTACT;
        }
        resetPressedAt = level.getGameTime();
        syncDue = true;
        final SecurityHousehold h = household();
        if (h == null || relayId == null) {
            return RelayRules.ResetVerdict.NO_CONTACT;
        }
        final RelayRules.ResetVerdict verdict = h.resetRelay(relayId, player.getUUID(),
                player.getGameProfile().getName(), contact == RelayContact.LINKED, level.getGameTime());
        if (verdict.done()) {
            final RelaySubscription sub = subscription();
            if (sub != null) {
                receive(sub, level.getGameTime());
            }
        }
        return verdict;
    }

    public RelayRules.ConfigVerdict configure(ServerPlayer player, RelayConfig wanted, long expectedRevision) {
        final SecurityHousehold h = household();
        if (h == null || relayId == null || level == null || contact != RelayContact.LINKED) {
            return RelayRules.ConfigVerdict.FORBIDDEN;
        }
        final RelayRules.ConfigVerdict verdict = h.configureRelay(relayId, wanted, expectedRevision,
                player.getUUID(), player.getGameProfile().getName(), level.getGameTime());
        final RelaySubscription sub = subscription();
        if (sub != null) {
            receive(sub, level.getGameTime());
        }
        return verdict;
    }

    public boolean accept(ServerPlayer player) {
        final SecurityHousehold h = candidateHousehold();
        if (h == null || relayId == null || level == null || contact != RelayContact.CANDIDATE) {
            return false;
        }
        final RelaySubscription sub = h.claimRelay(relayId, SecurityIdentity.at(level, worldPosition),
                player.getUUID(), player.getGameProfile().getName(), level.getGameTime());
        if (sub == null) {
            return false;
        }
        sincePolicy = POLICY_TICKS;
        return true;
    }

    public void setPlacer(@Nullable Player player) {
        if (player != null) {
            placer = player.getUUID();
            placerName = player.getGameProfile().getName();
            setChanged();
        }
    }

    public static void tell(Player player, RelayRules.ResetVerdict verdict) {
        player.displayClientMessage(Component.translatable("message.create_stats.security_relay.reset."
                + verdict.name().toLowerCase(java.util.Locale.ROOT))
                .withStyle(verdict.done() ? ChatFormatting.GREEN : ChatFormatting.RED), true);
    }

    @Override
    @Nullable
    public UUID relayId() {
        return relayId;
    }

    @Override
    public RelayContact contact() {
        return contact;
    }

    @Override
    public boolean powered() {
        return powered;
    }

    @Override
    public int output() {
        return output;
    }

    @Override
    @Nullable
    public UUID placer() {
        return placer;
    }

    public RelayConfig config() {
        return config;
    }

    @Nullable
    public RelayCondition condition() {
        return config.condition();
    }

    public boolean pending() {
        return received.pending();
    }

    public Truth reading() {
        return reading.truth();
    }

    public RelayReading readingDetail() {
        return reading;
    }

    public long revision() {
        return revision;
    }

    public long lastTriggerAt() {
        return received.lastTriggerAt();
    }

    public int pulseLeft() {
        return pulseLeft;
    }

    public long outputChangedAt() {
        return outputChangedAt;
    }

    public long pendingChangedAt() {
        return pendingChangedAt;
    }

    public long resetPressedAt() {
        return resetPressedAt;
    }

    public int energy() {
        return energy;
    }

    @Override
    public void repoll() {
        sincePolicy = POLICY_TICKS;
    }

    @Override
    public void offerPanel(BlockPos panel) {
        if (panelPos == null && panel != null && !panel.equals(offeredPanel)) {
            offeredPanel = panel.immutable();
            sincePolicy = POLICY_TICKS;
        }
    }

    @Override
    public void releasePanel(BlockPos panel) {
        if (panel == null) {
            return;
        }
        if (panel.equals(offeredPanel)) {
            offeredPanel = null;
        }
        if (panel.equals(panelPos)) {
            panelPos = null;
            sincePolicy = POLICY_TICKS;
        }
    }

    @Override
    public DeviceClass deviceClass() {
        return DeviceClass.RELAY;
    }

    @Override
    public DeviceState deviceState() {
        if (!powered) {
            return DeviceState.UNPOWERED;
        }
        return output > 0 ? DeviceState.ACTIVE : DeviceState.IDLE;
    }

    @Override
    @Nullable
    public BlockPos governingPanel() {
        return claimed == null ? null : panelPos;
    }

    @Override
    @Nullable
    public UUID governingHousehold() {
        return claimed;
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
        return RelayPower.INTAKE;
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
        return network;
    }

    @Override
    public void invalidateNetwork() {
        if (network != null) {
            network.markDirty();
            network = null;
        }
        sincePolicy = POLICY_TICKS;
    }

    @Override
    public BlockPos borePosition() {
        return worldPosition;
    }

    @Override
    public List<BlockPos> bores() {
        return bores;
    }

    @Override
    public boolean addBore(BlockPos far) {
        if (bores.contains(far)) {
            return false;
        }
        bores.add(far.immutable());
        return true;
    }

    @Override
    public boolean removeBore(BlockPos far) {
        return bores.remove(far);
    }

    @Override
    public void invalidate(Level owner) {
        setChanged();
        invalidateNetwork();
        if (owner != null && !owner.isClientSide()) {
            owner.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
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
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        tooltip.add(com.createstats.power.FeGoggle.title("Security Relay"));
        final RelayCondition c = config.condition();
        if (!powered) {
            tooltip.add(com.createstats.power.FeGoggle.bad("No power, output 0"));
        } else if (contact == RelayContact.UNBOUND) {
            tooltip.add(com.createstats.power.FeGoggle.warn("No household on this circuit"));
        } else if (contact == RelayContact.CANDIDATE) {
            tooltip.add(com.createstats.power.FeGoggle.warn("Waiting to be accepted at the panel"));
        } else if (contact == RelayContact.UNKNOWN) {
            tooltip.add(com.createstats.power.FeGoggle.warn("Panel asleep, holding what it had"));
        } else if (contact == RelayContact.LOST) {
            tooltip.add(com.createstats.power.FeGoggle.bad("Household lost, holding what it had"));
        } else if (output > 0) {
            tooltip.add(com.createstats.power.FeGoggle.ok("Output 15"));
        } else {
            tooltip.add(com.createstats.power.FeGoggle.dim("Output 0"));
        }
        if (contact.bound()) {
            tooltip.add(com.createstats.power.FeGoggle.dim((c == null ? "Unsupported condition"
                    : Component.translatable("relay.create_stats.condition." + c.langKey()).getString())
                    + ", " + Component.translatable("relay.create_stats.behavior." + config.behavior().key())
                    .getString()));
            if (received.pending()) {
                tooltip.add(com.createstats.power.FeGoggle.warn("Holding an incident, reset to clear"));
            }
        }
        tooltip.add(com.createstats.power.FeGoggle.stored(energy, RelayPower.BUFFER));
        tooltip.add(com.createstats.power.FeGoggle.draw(measuredDraw));
        return true;
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        invalidateNetwork();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Energy", energy);
        tag.putInt("Tier", managed.priorityOrdinal());
        tag.putInt("Lock", managed.lockOrdinal());
        if (relayId != null) {
            tag.putUUID("RelayId", relayId);
        }
        if (placer != null) {
            tag.putUUID("Placer", placer);
            tag.putString("PlacerName", placerName);
        }
        if (panelPos != null) {
            tag.putLong("Panel", panelPos.asLong());
        }
        if (claimed != null) {
            tag.putUUID("PanelHousehold", claimed);
        }
        // same codec as the household so they can't drift

        if (relayId != null) {
            final RelaySubscription copy = new RelaySubscription(relayId,
                    SecurityEndpoint.legacy(worldPosition.asLong()), config, revision, received, 0L, null);
            tag.put("Received", RelayCodec.save(copy));
        }
        tag.putLong("SeenTrigger", seenTrigger);
        tag.putInt("Output", output);
        BoreLinks.save(tag, bores);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        energy = Math.max(0, Math.min(RelayPower.BUFFER, tag.getInt("Energy")));
        if (tag.contains("Tier")) {
            managed.load(tag.getInt("Tier"), tag.getInt("Lock"));
        }
        relayId = tag.hasUUID("RelayId") ? tag.getUUID("RelayId") : null;
        placer = tag.hasUUID("Placer") ? tag.getUUID("Placer") : null;
        placerName = tag.getString("PlacerName");
        panelPos = tag.contains("Panel") ? BlockPos.of(tag.getLong("Panel")) : null;
        claimed = tag.hasUUID("PanelHousehold") ? tag.getUUID("PanelHousehold") : null;
        final RelaySubscription copy = tag.contains("Received") ? RelayCodec.load(tag.getCompound("Received"))
                : null;
        if (copy != null && copy.supported()) {
            config = copy.config();
            revision = copy.revision();
            received = copy.state();
        }
        reading = RelayReading.NONE;
        seenTrigger = tag.contains("SeenTrigger") ? tag.getLong("SeenTrigger") : RelayState.NEVER;
        output = Math.max(0, Math.min(15, tag.getInt("Output")));

        contact = claimed != null ? RelayContact.UNKNOWN : RelayContact.UNBOUND;
        bores.clear();
        bores.addAll(BoreLinks.load(tag));
        sincePolicy = POLICY_TICKS;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        final CompoundTag tag = new CompoundTag();
        tag.putInt("Out", output);
        tag.putInt("Contact", contact.ordinal());
        tag.putBoolean("Powered", powered);
        tag.putString("Cond", config.conditionId());
        tag.putString("Beh", config.behavior().key());
        tag.putInt("PulseT", config.pulseTicks());
        tag.putString("ModeKey", config.mode());
        tag.putBoolean("Pend", received.pending());
        tag.putInt("PulseLeft", pulseLeft);
        tag.putInt("Truth", reading.truth().ordinal());
        tag.putLong("OutAt", outputChangedAt);
        tag.putLong("PendAt", pendingChangedAt);
        tag.putLong("PressAt", resetPressedAt);
        tag.putInt("Energy", energy);
        tag.putInt("Draw", measuredDraw);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        output = tag.getInt("Out");
        contact = RelayContact.byOrdinal(tag.getInt("Contact"));
        powered = tag.getBoolean("Powered");
        final RelayBehavior b = RelayBehavior.byKey(tag.getString("Beh"));
        config = new RelayConfig(tag.getString("Cond"), b == null ? RelayBehavior.FOLLOW : b,
                Math.max(RelayConfig.MIN_PULSE, Math.min(RelayConfig.MAX_PULSE, tag.getInt("PulseT"))),
                tag.getString("ModeKey"), null);
        received = received.withPending(tag.getBoolean("Pend"));
        pulseLeft = tag.getInt("PulseLeft");
        reading = RelayReading.of(Truth.byOrdinal(tag.getInt("Truth")));
        outputChangedAt = tag.getLong("OutAt");
        pendingChangedAt = tag.getLong("PendAt");
        resetPressedAt = tag.getLong("PressAt");
        energy = tag.getInt("Energy");
        measuredDraw = tag.getInt("Draw");
    }

    @Override
    public void onDataPacket(net.minecraft.network.Connection net,
                             net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket packet,
                             HolderLookup.Provider registries) {
        final CompoundTag tag = packet.getTag();
        if (tag != null) {
            handleUpdateTag(tag, registries);
        }
    }

    @Override
    public net.minecraft.network.protocol.Packet<
            net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    public int signalTowards() {
        return output;
    }

    public BlockPos outputPos() {
        return worldPosition.relative(getBlockState().getValue(SecurityRelayBlock.FACING).getOpposite());
    }

    public SecurityEndpoint endpoint() {
        return SecurityIdentity.at(level, SecuritySegment.policyAnchor(level, worldPosition));
    }
}
