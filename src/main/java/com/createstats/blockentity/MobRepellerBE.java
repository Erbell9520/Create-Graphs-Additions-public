package com.createstats.blockentity;

import com.createstats.power.FeGoggle;
import com.createstats.power.FeGridMember;
import com.createstats.power.FeLock;
import com.createstats.power.FeManagedLoad;
import com.createstats.power.FeManagedState;
import com.createstats.power.FePriority;
import com.createstats.power.FeWireNetwork;
import com.createstats.power.SecurityDevice;
import com.createstats.registry.ModSounds;
import com.createstats.repeller.FrequencyTable;
import com.createstats.repeller.IntakeLedger;
import com.createstats.repeller.RepellerDraw;
import com.createstats.repeller.RepellerFrequencies;
import com.createstats.repeller.RepellerIndex;
import com.createstats.repeller.RepellerShape;
import com.createstats.repeller.RepellerStations;
import com.createstats.repeller.SpawnIndex;
import com.createstats.repeller.TransmitMeter;
import com.createstats.security.Clearance;
import com.createstats.security.PanelSweep;
import com.createstats.security.RepellerGate;
import com.createstats.security.SecurityLink;
import com.createstats.security.SecurityLog;
import com.createstats.security.SecurityRoster;
import com.createstats.tuner.TunerBand;
import com.createstats.tuner.TunerHost;
import com.createstats.tuner.TunerRange;
import com.createstats.tuner.TunerStation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public class MobRepellerBE extends SmartBlockEntity implements FeGridMember, FeManagedLoad, TunerHost,
        SecurityDevice, com.createstats.security.GovernedDevice,
        com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation,
        com.createstats.util.CopyableSettings {
    public enum State {
        NO_STATION,

        OFF,

        RUNNING,

        SHORT,

        DISABLED,

        SHED,

        PANEL_OFF;

        public static State of(int i) {
            final State[] all = values();
            return i >= 0 && i < all.length ? all[i] : NO_STATION;
        }

        public boolean transmitting() {
            return this == RUNNING || this == SHORT;
        }
    }

    public static final TunerRange RANGE = new TunerRange(RepellerDraw.MIN_RADIUS, RepellerDraw.MAX_RADIUS,
            RepellerDraw.RADIUS_STEP, RepellerDraw::drawFor);

    public static final TunerBand BAND = new TunerBand(FrequencyTable.MIN_TENTHS, FrequencyTable.MAX_TENTHS, 10, 100, 10,
            "kHz");

    private static final int POWER_SOUND_GAP = 30;

    private static final int SURVEY_TTL = 100;

    private static final int POLICY_TICKS = 40;

    private static final int TUNE_LOG_SETTLE = 60;

    private static final int REFUSAL_LOG_GAP = 20;

    private int energy;
    private String station = "";
    private int radius = RepellerDraw.DEFAULT_RADIUS;
    private final AtomicLong repelled = new AtomicLong();
    private final FeManagedState managed = new FeManagedState();
    private final TransmitMeter meter = new TransmitMeter();
    private final IntakeLedger intake = new IntakeLedger();
    @Nullable
    private FeWireNetwork network;

    private int ratedDraw;
    private int measuredDraw;
    private double intakeSmoothed;
    @Nullable
    private EntityType<?> resolvedType;
    private String resolvedFor = "\u0000";

    @Nullable
    private EntityType<?> indexedType;
    private double indexedRadius;
    private final int[] perSecond = new int[60];
    private long lastRepelledSample;
    @Nullable
    private List<TunerStation> survey;
    private long surveyAt = Long.MIN_VALUE;
    private boolean syncDue;

    private boolean ticked;
    private long lastPowerSound = Long.MIN_VALUE / 2;
    private int syncCooldown;
    private int numbersCooldown;

    @Nullable
    private BlockPos panelPos;

    @Nullable
    private java.util.UUID panelHousehold;

    @Nullable
    private BlockPos offeredPanel;

    @Nullable
    private SecurityRoster roster;
    private int required = Clearance.DEFAULT_REQUIRED;

    private int sincePolicy = POLICY_TICKS;
    @Nullable
    private ServerPlayer tunedBy;
    private long tunedAt;
    @Nullable
    private java.util.UUID refusedWho;
    private long refusedAt = Long.MIN_VALUE / 2;

    private boolean panelOff;

    private boolean locked;

    private boolean deaf;

    private boolean governed;

    private State state = State.NO_STATION;
    private int stationTenths;
    private float effective;
    private float power;
    private int repelledMinute;
    private boolean onCircuit;
    private int intakeShown;

    public float needle;

    public float hornTilt;

    public float scopePhase;

    public float scopeAmp;

    public float rangeKnob;

    private final IEnergyStorage energyView = new IEnergyStorage() {
        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            final int room = Math.min(maxReceive, RepellerDraw.BUFFER - energy);
            if (room <= 0) {
                return 0;
            }
            if (!simulate) {
                energy += room;
                if (level != null) {
                    intake.record(level.getGameTime(), room);
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
            return RepellerDraw.BUFFER;
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

    public MobRepellerBE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
    }

    public IEnergyStorage energyHandle() {
        return energyView;
    }

    public Direction facing() {
        final BlockState s = getBlockState();
        return s.hasProperty(HorizontalDirectionalBlock.FACING) ? s.getValue(HorizontalDirectionalBlock.FACING)
                : Direction.NORTH;
    }

    public boolean takesPowerOn(@Nullable Direction side) {
        return side == null || side == Direction.DOWN || (side.getAxis().isHorizontal() && side != facing());
    }

    public State state() {
        return state;
    }

    public String station() {
        return station;
    }

    public int radius() {
        return radius;
    }

    public float effectiveRadius() {
        return effective;
    }

    public float powerFraction() {
        return power;
    }

    public int energyStored() {
        return energy;
    }

    public int stationTenths() {
        return stationTenths;
    }

    public int repelledLastMinute() {
        return repelledMinute;
    }

    public boolean column() {
        return type() == EntityType.PHANTOM;
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null) {
            return;
        }
        if (level.isClientSide()) {
            clientTick();
            return;
        }
        if (network == null || network.isDirty()) {
            FeWireNetwork.buildFrom(this);
        }
        if (network != null) {
            network.solveOnce(level);
        }
        final long now = level.getGameTime();
        if (++sincePolicy >= POLICY_TICKS) {
            sincePolicy = 0;
            findPolicy();
        }
        flushTuneLog(now);

        if (network == null || !network.isPduManaged(now)) {
            setManagedShed(false);
        }

        final boolean redstone = !deaf && level.hasNeighborSignal(worldPosition);
        final EntityType<?> type = type();
        final int full = RepellerDraw.drawFor(radius);
        ratedDraw = type == null || panelOff || redstone || managed.isForcedOff() ? 0 : full;

        final State before = state;
        int spent = 0;
        if (type == null) {
            state = State.NO_STATION;
            meter.reset();
        } else if (panelOff) {
            state = State.PANEL_OFF;
            meter.reset();
        } else if (redstone) {
            state = State.DISABLED;
            meter.reset();
        } else if (managed.isSuppressed()) {
            state = State.SHED;
            meter.reset();
        } else {
            spent = Math.min(full, energy);
            energy -= spent;
            meter.tick(radius, spent);
            final double r = meter.publishedRadius();

            state = r <= 0.0 ? State.OFF : r < radius && spent < full ? State.SHORT : State.RUNNING;
        }
        if (ticked && state.transmitting() != before.transmitting() && now - lastPowerSound >= POWER_SOUND_GAP) {
            lastPowerSound = now;
            level.playSound(null, worldPosition, state.transmitting() ? ModSounds.MOB_REPELLER_POWER_UP.get()
                    : ModSounds.MOB_REPELLER_POWER_DOWN.get(), SoundSource.BLOCKS, 0.6f, 1.0f);
        }
        ticked = true;
        measuredDraw = spent;
        intakeSmoothed += (intake.completedBefore(now) - intakeSmoothed) * TransmitMeter.ALPHA;
        onCircuit = network != null && network.size() > 1;

        final float newEffective = (float) meter.publishedRadius();
        final float newPower = Math.round(meter.powerFraction(radius) * 32.0) / 32.0f;
        final int newTenths = type == null ? 0 : RepellerFrequencies.tenthsOf(type);
        if (state != before || newEffective != effective || newTenths != stationTenths) {
            syncDue = true;
            syncCooldown = 0;
        } else if (newPower != power) {
            syncDue = true;
        }
        effective = newEffective;
        power = newPower;
        stationTenths = newTenths;
        updateIndex(type);
        rollRepelled(now);
        intakeShown = (int) Math.round(intakeSmoothed);

        if (--numbersCooldown <= 0) {
            numbersCooldown = 20;
            syncDue = true;
        }
        if (syncDue && --syncCooldown <= 0) {
            syncDue = false;
            syncCooldown = 2;
            sendData();
        }
        setChanged();
    }

    @Nullable
    private EntityType<?> type() {
        if (!station.equals(resolvedFor)) {
            resolvedFor = station;
            resolvedType = RepellerFrequencies.typeOf(station);
        }
        return resolvedType;
    }

    private void updateIndex(@Nullable EntityType<?> type) {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        final double r = type == null ? 0.0 : meter.publishedRadius();
        if (type == indexedType && r == indexedRadius) {
            return;
        }
        indexedType = type;
        indexedRadius = r;
        if (type == null || r <= 0.0) {
            RepellerIndex.remove(server, worldPosition.asLong());
            return;
        }
        RepellerIndex.put(server, new SpawnIndex.Entry<>(worldPosition.asLong(), type,
                type == EntityType.PHANTOM ? RepellerShape.COLUMN : RepellerShape.SPHERE,
                worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5, r, repelled));
    }

    private void rollRepelled(long now) {
        if (now % 20 != 0) {
            return;
        }
        final long total = repelled.get();
        System.arraycopy(perSecond, 0, perSecond, 1, perSecond.length - 1);
        perSecond[0] = (int) Math.max(0, Math.min(Integer.MAX_VALUE, total - lastRepelledSample));
        lastRepelledSample = total;
        int sum = 0;
        for (int v : perSecond) {
            sum += v;
        }
        repelledMinute = sum;
    }

    private void clientTick() {
        final float targetNeedle = stationTenths <= 0 ? 0.0f
                : (stationTenths - FrequencyTable.MIN_TENTHS) / (float) (FrequencyTable.MAX_TENTHS - FrequencyTable.MIN_TENTHS);
        needle += (targetNeedle - needle) * 0.18f;
        final float targetRange = (radius - RepellerDraw.MIN_RADIUS) / (float) (RepellerDraw.MAX_RADIUS - RepellerDraw.MIN_RADIUS);
        rangeKnob += (targetRange - rangeKnob) * 0.18f;
        final float targetTilt = column() ? 58.0f : 0.0f;
        hornTilt += (targetTilt - hornTilt) * 0.08f;
        final float targetAmp = state.transmitting() ? power : 0.0f;
        scopeAmp += (targetAmp - scopeAmp) * 0.15f;
        if (state.transmitting() || state == State.NO_STATION) {
            scopePhase = (scopePhase + 0.9f) % 360.0f;
        }
    }

    @Override
    public TunerRange tunerRange() {
        return RANGE;
    }

    @Override
    public TunerBand dialBand() {
        return BAND;
    }

    @Override
    public Component rangeLabel(@Nullable TunerStation station, int range) {
        return Component.translatable(station != null && station.column()
                ? "block.create_stats.mob_repeller.tuner.column" : "block.create_stats.mob_repeller.tuner.sphere", range);
    }

    @Override
    @Nullable
    public Component tunerNote(@Nullable TunerStation station, int range) {
        if (onCircuit || station == null || RepellerDraw.drawFor(range) <= WallOutletBE.RATED_FE_PER_TICK) {
            return null;
        }
        return Component.translatable("block.create_stats.mob_repeller.tuner.cord",
                (int) Math.floor(RepellerDraw.radiusPaidBy(WallOutletBE.RATED_FE_PER_TICK)));
    }

    @Override
    public String tunedStation() {
        return station;
    }

    @Override
    public int tunedRange() {
        return radius;
    }

    @Override
    public List<TunerStation> surveyStations(ServerPlayer player) {
        if (!(level instanceof ServerLevel server)) {
            return List.of();
        }
        final long now = server.getGameTime();
        if (survey == null || now - surveyAt > SURVEY_TTL) {
            survey = RepellerStations.survey(server, worldPosition, station);
            surveyAt = now;
        }
        return survey;
    }

    @Override
    public void applyTuning(ServerPlayer player, String wanted, int range) {
        if (level == null || level.isClientSide()) {
            return;
        }
        final RepellerGate gate = gateFor(player);
        if (gate != RepellerGate.ALLOW) {
            refuse(player, gate);
            return;
        }
        if (retune(wanted, range) && panelPos != null) {
            tunedBy = player;
            tunedAt = level.getGameTime();
        }
    }

    public boolean retuneFromPanel(String wanted, int range) {
        return level != null && !level.isClientSide() && retune(wanted, range);
    }

    public String tuningLine() {
        final EntityType<?> type = type();
        if (type == null) {
            return "tuned off";
        }
        final String mob = BuiltInRegistries.ENTITY_TYPE.getKey(type).getPath().replace('_', ' ');
        return "tuned to " + mob + ", " + radius + (column() ? " block column" : " blocks");
    }

    private boolean retune(@Nullable String wanted, int range) {
        final String id = wanted == null ? "" : wanted;
        if (!id.isEmpty() && RepellerFrequencies.typeOf(id) == null) {
            return false;
        }
        final int r = RepellerDraw.clampRadius(range);
        final boolean retuned = !id.equals(station);
        if (!retuned && r == radius) {
            return false;
        }
        station = id;
        radius = r;
        survey = null;

        level.playSound(null, worldPosition, ModSounds.MOB_REPELLER_ENGAGE.get(), SoundSource.BLOCKS,
                retuned ? 0.7f : 0.45f, retuned ? 1.0f : 1.15f);
        setChanged();
        syncDue = true;
        syncCooldown = 0;
        return true;
    }

    @Override
    @Nullable
    public Component tunerLock() {
        return locked ? Component.translatable("block.create_stats.mob_repeller.locked") : null;
    }

    @Override
    public String settingsKey() {
        return "create_stats:mob_repeller";
    }

    @Override
    public void writeSettings(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putString("Station", station);
        tag.putInt("Radius", radius);
    }

    @Override
    public boolean applySettings(CompoundTag tag, HolderLookup.Provider registries,
                                 @Nullable net.minecraft.world.entity.player.Player player, boolean simulate) {
        if (!tag.contains("Station") || !tag.contains("Radius")) {
            return false;
        }
        final String wanted = tag.getString("Station");
        if (!wanted.isEmpty() && RepellerFrequencies.typeOf(wanted) == null) {
            return false;
        }
        if (simulate) {
            return !locked;
        }
        if (!(player instanceof ServerPlayer server)) {
            return false;
        }
        final RepellerGate gate = gateFor(server);
        if (gate != RepellerGate.ALLOW) {
            refuse(server, gate);
            return false;
        }
        applyTuning(server, wanted, tag.getInt("Radius"));
        return true;
    }

    @Override
    public void writeFreshDefaults(CompoundTag tag) {
        tag.putInt("Tier", FePriority.DEFAULT.ordinal());
    }

    @Override
    public void writeSafe(CompoundTag tag, HolderLookup.Provider registries) {
        super.writeSafe(tag, registries);
        writeFreshDefaults(tag);
        writeSettings(tag, registries);
    }

    @Override
    public DeviceClass deviceClass() {
        return DeviceClass.REPELLER;
    }

    @Override
    public DeviceState deviceState() {
        return switch (state) {
            case RUNNING -> DeviceState.ACTIVE;
            case SHORT -> DeviceState.DAMAGED;
            case OFF, SHED -> DeviceState.UNPOWERED;
            default -> DeviceState.IDLE;
        };
    }

    @Override
    @Nullable
    public BlockPos governingPanel() {
        return panelPos;
    }

    public boolean panelOff() {
        return panelOff;
    }

    public boolean locked() {
        return locked;
    }

    public boolean hearsRedstone() {
        return !deaf;
    }

    public boolean governed() {
        return governed;
    }

    public void repoll() {
        sincePolicy = POLICY_TICKS;
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
    @Nullable
    public java.util.UUID governingHousehold() {
        return panelHousehold;
    }

    @Override
    public void offerPanel(BlockPos panel) {
        if (panelPos == null && !panel.equals(offeredPanel)) {
            offeredPanel = panel.immutable();
            sincePolicy = POLICY_TICKS;
        }
    }

    private void findPolicy() {
        if (level == null) {
            return;
        }
        final SecurityLink.Contact<HomeSecurityPanelBE> contact = SecurityLink.resolve(panel(),
                panelPos != null, panelPos != null && level.isLoaded(panelPos),
                network != null || offeredPanel != null, this::searchPanels);
        if (contact.state() == SecurityLink.UNKNOWN) {
            return;
        }
        if (contact.state().isLost()) {
            loseLink();
            return;
        }
        final HomeSecurityPanelBE found = contact.panel();
        if (found == null) {
            return;
        }
        final boolean was = governed;
        final boolean wasOff = panelOff;
        final boolean wasLocked = locked;
        final boolean wasDeaf = deaf;
        panelPos = found.getBlockPos();
        panelHousehold = com.createstats.security.SecurityIdentity.householdOf(found);
        offeredPanel = null;
        governed = true;
        roster = found.rosterSnapshot();
        required = found.requiredLevel(worldPosition);
        panelOff = !found.repellerOn(worldPosition);
        locked = found.repellerLocked(worldPosition);
        deaf = !found.repellerHearsRedstone(worldPosition);
        if (!was || wasOff != panelOff || wasLocked != locked || wasDeaf != deaf) {
            setChanged();
            syncDue = true;
            syncCooldown = 0;
        }
    }

    private void loseLink() {
        final boolean had = governed || panelPos != null || panelOff || locked || deaf;
        panelPos = null;
        offeredPanel = null;
        roster = null;
        required = Clearance.DEFAULT_REQUIRED;
        governed = false;
        panelOff = false;
        locked = false;
        deaf = false;
        if (had) {
            setChanged();
            syncDue = true;
            syncCooldown = 0;
        }
    }

    @Nullable
    private HomeSecurityPanelBE searchPanels() {
        final HomeSecurityPanelBE wired = PanelSweep.nearest(level, network, worldPosition, worldPosition,
                panelHousehold);
        return wired != null ? wired : hintedPanel();
    }

    @Nullable
    private HomeSecurityPanelBE hintedPanel() {
        if (level == null || offeredPanel == null || !level.isLoaded(offeredPanel)) {
            return null;
        }
        return com.createstats.security.SecurityIdentity.offered(level, offeredPanel, worldPosition,
                panelHousehold);
    }

    @Nullable
    public HomeSecurityPanelBE panel() {
        if (level == null || panelPos == null || !level.isLoaded(panelPos)) {
            return null;
        }
        return level.getBlockEntity(panelPos) instanceof HomeSecurityPanelBE p
                && com.createstats.security.SecurityIdentity.accepts(level, panelHousehold, worldPosition, p)
                ? p : null;
    }

    public boolean clears(net.minecraft.world.entity.player.Player player) {
        return roster != null && roster.passes(player.getUUID(), required);
    }

    private RepellerGate gateFor(ServerPlayer player) {
        return RepellerGate.decide(panelPos != null, locked, roster != null, clears(player));
    }

    private void refuse(ServerPlayer player, RepellerGate gate) {
        player.displayClientMessage(switch (gate) {
            case LOCKED -> Component.translatable("block.create_stats.mob_repeller.locked");
            case NO_CLEARANCE -> Component.translatable("block.create_stats.mob_repeller.no_clearance",
                    required, Clearance.label(required));
            default -> Component.translatable("block.create_stats.mob_repeller.out_of_contact");
        }, true);
        final long now = level == null ? 0L : level.getGameTime();
        if (player.getUUID().equals(refusedWho) && now - refusedAt < REFUSAL_LOG_GAP) {
            return;
        }
        refusedWho = player.getUUID();
        refusedAt = now;
        final HomeSecurityPanelBE p = panel();
        if (p != null) {
            p.recordDevice(player, worldPosition, gate == RepellerGate.LOCKED ? "tried to retune it: locked"
                    : "tried to retune it: needs " + required + " " + Clearance.plain(required),
                    SecurityLog.Verdict.DENIED);
        }
    }

    private void flushTuneLog(long now) {
        if (tunedBy == null || now - tunedAt < TUNE_LOG_SETTLE) {
            return;
        }
        final HomeSecurityPanelBE p = panel();
        if (p != null) {
            p.recordDevice(tunedBy, worldPosition, tuningLine() + " at the block", SecurityLog.Verdict.CHANGED);
        }
        tunedBy = null;
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        tooltip.add(FeGoggle.title(Component.translatable("block.create_stats.mob_repeller")));
        final EntityType<?> type = type();
        final String mob = type == null ? "" : type.getDescription().getString();
        final String khz = stationTenths > 0 ? FrequencyTable.format(stationTenths) : "";
        tooltip.add(switch (state) {
            case RUNNING -> FeGoggle.ok(tr("goggle.running", mob, khz));
            case SHORT -> FeGoggle.warn(tr("goggle.short", mob, khz));
            case OFF -> FeGoggle.bad(tr("goggle.no_power", mob, khz));
            case DISABLED -> FeGoggle.warn(tr("goggle.redstone"));
            case SHED -> FeGoggle.warn(tr("goggle.shed"));
            case PANEL_OFF -> FeGoggle.warn(tr("goggle.panel_off"));
            default -> FeGoggle.warn(tr("goggle.no_station"));
        });
        if (type != null) {
            final boolean col = type == EntityType.PHANTOM;
            tooltip.add(FeGoggle.dim(tr(col ? "goggle.column" : "goggle.sphere", radius,
                    String.format(java.util.Locale.ROOT, "%.1f", effective))));
            if (state == State.SHORT) {
                tooltip.add(onCircuit ? FeGoggle.warn(tr("goggle.why_supply"))
                        : FeGoggle.warn(tr("goggle.why_cord", WallOutletBE.RATED_FE_PER_TICK)));
            }
            tooltip.add(FeGoggle.supplyDemand(intakeShown, RepellerDraw.drawFor(radius)));
        }
        tooltip.add(FeGoggle.draw(measuredDraw));
        tooltip.add(FeGoggle.stored(energy, RepellerDraw.BUFFER));
        if (type != null) {
            tooltip.add(FeGoggle.dim(tr("goggle.repelled", repelledMinute)));
        }
        tooltip.add(FeGoggle.dim(tr("goggle.spawners")));
        if (governed) {
            tooltip.add(FeGoggle.dim(tr(locked ? "goggle.locked" : "goggle.governed")));
            if (deaf) {
                tooltip.add(FeGoggle.dim(tr("goggle.deaf")));
            }
        }
        return true;
    }

    private static String tr(String key, Object... args) {
        return Component.translatable("block.create_stats.mob_repeller." + key, args).getString();
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
        managed.setShed(shed);
    }

    @Override
    public boolean isManagedShed() {
        return managed.isShed();
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
        return RepellerDraw.TRANSFER_RATE;
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

    @Override
    public void adoptNetwork(FeWireNetwork network) {
        this.network = network;
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
    }

    @Override
    public void remove() {
        invalidateNetwork();
        super.remove();
    }

    @Override
    public void invalidate() {
        if (level instanceof ServerLevel server) {
            RepellerIndex.remove(server, worldPosition.asLong());
        }
        indexedType = null;
        indexedRadius = 0.0;
        super.invalidate();
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putInt("Energy", energy);
        tag.putString("Station", station);
        tag.putInt("Radius", radius);
        tag.putInt("Tier", managed.priorityOrdinal());
        tag.putInt("Lock", managed.lockOrdinal());
        tag.putLong("Repelled", repelled.get());
        tag.putDouble("Smoothed", meter.smoothedFePerTick());
        tag.putDouble("Published", meter.publishedRadius());
        if (panelPos != null && !clientPacket) {
            tag.putLong("Panel", panelPos.asLong());
        }
        if (panelHousehold != null && !clientPacket) {
            tag.putUUID("PanelHousehold", panelHousehold);
        }
        tag.putBoolean("PanelOff", panelOff);
        tag.putBoolean("Locked", locked);
        tag.putBoolean("Deaf", deaf);
        tag.putBoolean("Governed", governed);
        if (clientPacket) {
            tag.putInt("State", state.ordinal());
            tag.putInt("Tenths", stationTenths);
            tag.putFloat("Effective", effective);
            tag.putFloat("Power", power);
            tag.putInt("Draw", measuredDraw);
            tag.putInt("Getting", intakeShown);
            tag.putInt("Minute", repelledMinute);
            tag.putBoolean("OnCircuit", onCircuit);
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        energy = Mth.clamp(tag.getInt("Energy"), 0, RepellerDraw.BUFFER);
        station = tag.getString("Station");
        radius = tag.contains("Radius") ? RepellerDraw.clampRadius(tag.getInt("Radius")) : RepellerDraw.DEFAULT_RADIUS;
        managed.load(tag.getInt("Tier"), tag.getInt("Lock"));
        repelled.set(Math.max(0L, tag.getLong("Repelled")));
        if (!clientPacket) {
            meter.restore(tag.getDouble("Smoothed"), tag.getDouble("Published"));
            lastRepelledSample = repelled.get();
            panelPos = tag.contains("Panel") ? BlockPos.of(tag.getLong("Panel")) : null;
            panelHousehold = tag.hasUUID("PanelHousehold") ? tag.getUUID("PanelHousehold") : null;
        }

        panelOff = tag.getBoolean("PanelOff");
        locked = tag.getBoolean("Locked");
        deaf = tag.getBoolean("Deaf");
        governed = tag.getBoolean("Governed");
        // read when present not only from packets, so ponder can set any state

        if (clientPacket || tag.contains("State")) {
            state = State.of(tag.getInt("State"));
            stationTenths = tag.getInt("Tenths");
            effective = tag.getFloat("Effective");
            power = tag.getFloat("Power");
            measuredDraw = tag.getInt("Draw");
            intakeShown = tag.getInt("Getting");
            repelledMinute = tag.getInt("Minute");
            onCircuit = tag.getBoolean("OnCircuit");
        }
    }
}
