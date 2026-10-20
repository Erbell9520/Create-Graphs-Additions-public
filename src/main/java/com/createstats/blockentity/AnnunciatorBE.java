package com.createstats.blockentity;

import com.createstats.alarm.annunciator.AlarmSeverity;
import com.createstats.alarm.annunciator.AnnunciatorEngine;
import com.createstats.alarm.annunciator.AnnunciatorEvent;
import com.createstats.alarm.annunciator.AnnunciatorSettings;
import com.createstats.alarm.annunciator.AnnunciatorState;
import com.createstats.alarm.annunciator.AnnunciatorVariant;
import com.createstats.alarm.annunciator.GridFact;
import com.createstats.alarm.annunciator.RuleBinding;
import com.createstats.alarm.annunciator.RuleFault;
import com.createstats.alarm.annunciator.RuleMeasure;
import com.createstats.alarm.annunciator.RuleStatus;
import com.createstats.alarm.annunciator.ToggleMode;
import com.createstats.alarm.annunciator.WatchRule;
import com.createstats.antenna.AntennaRange;
import com.createstats.antenna.AntennaTier;
import com.createstats.antenna.RangeAttachment;
import com.createstats.block.AnnunciatorBlock;
import com.createstats.data.MetricType;
import com.createstats.data.TimeSeriesStore;
import com.createstats.power.FeGridMember;
import com.createstats.power.FeGridSolver;
import com.createstats.power.FeWireNetwork;
import com.createstats.util.InstrumentSourceHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class AnnunciatorBE extends BlockEntity implements RangeAttachment, com.createstats.power.IFeConsumer,
        com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation {
    private static final int SCAN_INTERVAL = 20;

    public static final int STRIKE_ANIM_TICKS = 10;

    private final com.createstats.power.FeConsumer feConsumer = com.createstats.power.FeConsumer
            .forBlockEntity(this, com.createstats.power.FeProfiles
                    .settings(com.createstats.power.FeProfiles.KEY_STATS_ALARM));

    private final List<WatchRule> rules = new ArrayList<>();
    private Map<String, RuleStatus> statuses = new HashMap<>();
    // faults sit beside the statuses, the engine knows nothing about chunks and reach
    private Map<String, RuleFault> faults = new HashMap<>();

    private final java.util.ArrayDeque<AnnunciatorEvent> log = new java.util.ArrayDeque<>();
    private AnnunciatorSettings settings = new AnnunciatorSettings();
    private ToggleMode toggle = ToggleMode.ARMED;
    private AntennaTier antennaTier = AntennaTier.NONE;
    private UUID ownerUUID;

    private int scanTimer;
    private int hornTimer;
    private boolean ackPending;
    private boolean resetPending;
    private boolean needsSync;
    private int nextRuleId = 1;

    private AlarmSeverity severity = AlarmSeverity.NORMAL;
    private AnnunciatorState deviceState = AnnunciatorState.NORMAL;
    private int culpritIndex = -1;
    private String culpritLabel = "";
    private String culpritValue = "";
    private int faultCount;

    private boolean circuitFound;

    private long lastStrikeTick = Long.MIN_VALUE;

    private long lastAckTick = Long.MIN_VALUE;

    public AnnunciatorBE(BlockEntityType<?> type, BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        super(type, pos, state);
    }

    public com.createstats.power.FeConsumer feConsumer() {
        return this.feConsumer;
    }

    public AnnunciatorVariant variant() {
        return this.getBlockState().getBlock() instanceof AnnunciatorBlock block
                ? block.variant() : AnnunciatorVariant.SENTRY;
    }

    public List<WatchRule> getRules() {
        return this.rules;
    }

    public AnnunciatorSettings getSettings() {
        return this.settings;
    }

    public void setSettings(AnnunciatorSettings settings) {
        this.settings = settings == null ? new AnnunciatorSettings() : settings;
        this.markDirtyAndSync();
    }

    public void setRules(List<WatchRule> incoming) {
        final Map<String, WatchRule> previous = new HashMap<>();
        for (WatchRule rule : this.rules) {
            previous.put(rule.getId(), rule);
        }
        this.rules.clear();
        final java.util.Set<String> seen = new java.util.HashSet<>();
        if (incoming != null) {
            for (WatchRule rule : incoming) {
                if (this.rules.size() >= WatchRule.MAX_RULES) {
                    break;
                }
                this.rememberRuleId(rule.getId());
                final WatchRule resolved = rule.getId().isEmpty() || !seen.add(rule.getId())
                        ? rule.copyWithId(this.freshRuleId(seen)) : rule;
                seen.add(resolved.getId());
                this.rules.add(resolved);
            }
        }
        final Map<String, RuleStatus> kept = new HashMap<>();
        final Map<String, RuleFault> keptFaults = new HashMap<>();
        for (WatchRule rule : this.rules) {
            final RuleStatus was = this.statuses.get(rule.getId());
            final WatchRule before = previous.get(rule.getId());
            if (was == null || before == null || !sameSource(before, rule)) {
                continue;
            }
            kept.put(rule.getId(), was);
            final RuleFault fault = this.faults.get(rule.getId());
            if (fault != null) {
                keptFaults.put(rule.getId(), fault);
            }
        }
        this.statuses = kept;
        this.faults = keptFaults;
        this.markDirtyAndSync();
    }

    private static boolean sameSource(WatchRule before, WatchRule after) {
        return before.getBinding().equals(after.getBinding())
                && before.getMeasure() == after.getMeasure();
    }

    public WatchRule createRule() {
        return new WatchRule(this.freshRuleId(java.util.Set.of()));
    }

    private String freshRuleId(java.util.Set<String> taken) {
        String candidate;
        do {
            candidate = "r" + (this.nextRuleId++);
        } while (taken.contains(candidate) || this.hasRuleId(candidate));
        return candidate;
    }

    private boolean hasRuleId(String id) {
        for (WatchRule rule : this.rules) {
            if (rule.getId().equals(id)) {
                return true;
            }
        }
        return false;
    }

    private void rememberRuleId(String id) {
        if (id == null || id.length() < 2 || id.charAt(0) != 'r') {
            return;
        }
        try {
            this.nextRuleId = Math.max(this.nextRuleId, Integer.parseInt(id.substring(1)) + 1);
        } catch (NumberFormatException ignored) {
        }
    }

    public ToggleMode getToggle() {
        return this.toggle;
    }

    public void cycleToggle() {
        this.toggle = this.toggle.next();
        if (this.toggle.isTest() && this.level != null && !this.level.isClientSide()) {
            this.playHorn();
        }
        this.markDirtyAndSync();
    }

    public void acknowledge() {
        this.ackPending = true;
        if (this.level != null) {
            this.lastAckTick = this.level.getGameTime();
        }
        this.markDirtyAndSync();
    }

    public void reset() {
        this.resetPending = true;
        this.markDirtyAndSync();
    }

    public UUID getOwnerUUID() {
        return this.ownerUUID;
    }

    public void setOwnerUUID(UUID uuid) {
        this.ownerUUID = uuid;
    }

    @Override
    public AntennaTier getAntennaTier() {
        return this.antennaTier;
    }

    @Override
    public void setAntennaTier(AntennaTier tier) {
        final AntennaTier resolved = tier == null ? AntennaTier.NONE : tier;
        if (this.antennaTier == resolved) {
            return;
        }
        this.antennaTier = resolved;
        this.markDirtyAndSync();
    }

    public AlarmSeverity getSeverity() {
        return this.severity;
    }

    public AnnunciatorState getDeviceState() {
        return this.deviceState;
    }

    public int getCulpritIndex() {
        return this.culpritIndex;
    }

    public String getCulpritLabel() {
        return this.culpritLabel;
    }

    public String getCulpritValue() {
        return this.culpritValue;
    }

    public int getFaultCount() {
        return this.faultCount;
    }

    public boolean hasCircuit() {
        return this.circuitFound;
    }

    public long getLastStrikeTick() {
        return this.lastStrikeTick;
    }

    public long getLastAckTick() {
        return this.lastAckTick;
    }

    public RuleStatus statusOf(WatchRule rule) {
        final RuleStatus status = this.statuses.get(rule.getId());
        return status == null ? RuleStatus.initial(rule.getId()) : status;
    }

    // never scanned = NONE, don't invent a reason
    public RuleFault faultOf(WatchRule rule) {
        final RuleFault fault = this.faults.get(rule.getId());
        return fault == null ? RuleFault.NONE : fault;
    }

    public List<AnnunciatorEvent> getLog() {
        return List.copyOf(this.log);
    }

    public void clearLog() {
        if (this.log.isEmpty()) {
            return;
        }
        this.log.clear();
        this.markDirtyAndSync();
    }

    public int effectiveScanRadius() {
        return AntennaRange.cappedScanRadius(this.settings.getScanRadius(), this.antennaTier,
                AnnunciatorSettings.MAX_AERIAL_RADIUS);
    }

    public float dialFraction() {
        if (this.culpritIndex < 0 || this.culpritIndex >= this.rules.size()) {
            return 0.0f;
        }
        final WatchRule rule = this.rules.get(this.culpritIndex);
        final RuleStatus status = this.statusOf(rule);
        final float[] range = com.createstats.display.MetricContract.fixedRange(rule.getMetricType());
        if (range != null && range[1] > range[0]) {
            return clamp01((status.value() - range[0]) / (range[1] - range[0]));
        }
        final float span = Math.abs(rule.getThreshold()) * 1.5f;
        return span <= 0.0001f ? 0.0f : clamp01(Math.abs(status.value()) / span);
    }

    private static float clamp01(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }

    public void tick() {
        final Level level = this.getLevel();
        if (level == null || level.isClientSide()) {
            return;
        }
        if (this.needsSync) {
            this.needsSync = false;
            level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
        }

        final boolean wantsPower = this.deviceState.isOutstanding() || !this.rules.isEmpty();
        final boolean powered = this.feConsumer.serviceTick(wantsPower);
        com.createstats.power.FePowerFx.handleTransition(level, this.worldPosition, this.feConsumer);
        if (!powered) {
            if (this.severity != AlarmSeverity.NORMAL) {
                this.severity = AlarmSeverity.NORMAL;
                this.refreshGlow();
                this.markDirtyAndSync();
            }
            return;
        }

        if (++this.scanTimer < SCAN_INTERVAL) {
            this.tickHorn(level);
            return;
        }
        this.scanTimer = 0;
        if (!this.feConsumer.spendBurst()) {
            return;
        }
        this.scan(level);
        this.tickHorn(level);
    }

    private void scan(Level level) {
        final boolean ack = this.ackPending;
        final boolean reset = this.resetPending;
        this.ackPending = false;
        this.resetPending = false;

        final FeWireNetwork circuit = this.variant().isWired() ? this.findCircuit(level) : null;
        final boolean wasOnCircuit = this.circuitFound;
        this.circuitFound = circuit != null;
        final FeGridSolver.Result grid = circuit == null ? null : circuit.lastResult();

        final Map<String, RuleStatus> before = this.statuses;
        final Map<String, RuleFault> faultsBefore = this.faults;

        final Map<String, RuleFault> freshFaults = new HashMap<>();
        final AnnunciatorEngine.Outcome outcome = AnnunciatorEngine.evaluate(
                this.rules, this.statuses,
                rule -> {
                    final Sampled sampled = this.sample(level, rule, circuit, grid);
                    if (sampled.fault().isFault()) {
                        freshFaults.put(rule.getId(), sampled.fault());
                    }
                    return sampled.reading();
                },
                ack, reset, this.toggle.isMuted(), level.getGameTime());

        final AlarmSeverity wasSeverity = this.severity;
        final AnnunciatorState wasState = this.deviceState;

        this.recordEvents(before, outcome, level.getGameTime());
        this.statuses = AnnunciatorEngine.index(outcome.statuses());
        this.faults = freshFaults;
        this.severity = outcome.severity();
        this.deviceState = outcome.state();
        this.culpritIndex = outcome.culpritIndex();
        this.faultCount = outcome.faultCount();

        final RuleStatus culprit = outcome.culprit();
        if (culprit != null && this.culpritIndex < this.rules.size()) {
            final WatchRule rule = this.rules.get(this.culpritIndex);
            this.culpritLabel = rule.getLabel();
            this.culpritValue = rule.formatValue(culprit.value());
        } else {
            this.culpritLabel = "";
            this.culpritValue = "";
        }

        if (wasSeverity != this.severity) {
            this.refreshGlow();
        }

        if (wasState.isActive() != this.deviceState.isActive()) {
            level.updateNeighborsAt(this.worldPosition, this.getBlockState().getBlock());
        }

        if (this.deviceState == AnnunciatorState.ALARM && wasState != AnnunciatorState.ALARM) {
            this.hornTimer = this.settings.getHornIntervalTicks();
        }

        if (wasSeverity != this.severity || wasState != this.deviceState
                || wasOnCircuit != this.circuitFound
                || !before.equals(this.statuses) || !faultsBefore.equals(this.faults)) {
            this.markDirtyAndSync();
        } else {
            this.setChanged();
        }
    }

    private void recordEvents(@Nullable Map<String, RuleStatus> before,
                              AnnunciatorEngine.Outcome outcome, long now) {
        final List<RuleStatus> after = outcome.statuses();
        for (int i = 0; i < after.size() && i < this.rules.size(); i++) {
            final WatchRule rule = this.rules.get(i);
            if (!rule.isLive()) {
                continue;
            }
            final RuleStatus fresh = after.get(i);
            final RuleStatus was = before == null ? null : before.get(rule.getId());
            final AnnunciatorState wasState = was == null ? AnnunciatorState.NORMAL : was.state();
            final String reading = fresh.available() ? rule.formatValue(fresh.value()) : "";

            final AnnunciatorEvent.Kind kind = AnnunciatorEvent.kindFor(wasState, fresh.state());
            if (kind != null) {
                this.pushEvent(new AnnunciatorEvent(rule.getLabel(), kind,
                        fresh.effectiveSeverity(), reading, now));
            }

            if (was != null && was.available() != fresh.available()) {
                this.pushEvent(new AnnunciatorEvent(rule.getLabel(),
                        fresh.available() ? AnnunciatorEvent.Kind.RESTORED : AnnunciatorEvent.Kind.LOST,
                        fresh.effectiveSeverity(), reading, now));
            }
        }
    }

    private void pushEvent(AnnunciatorEvent event) {
        this.log.addFirst(event);
        while (this.log.size() > AnnunciatorEvent.MAX_EVENTS) {
            this.log.removeLast();
        }
    }

    private void tickHorn(Level level) {
        if (this.deviceState != AnnunciatorState.ALARM || this.toggle.isMuted()) {
            this.hornTimer = 0;
            return;
        }
        if (++this.hornTimer < this.settings.getHornIntervalTicks()) {
            return;
        }
        this.hornTimer = 0;
        this.playHorn();
        this.spawnParticles(level);
        if (this.settings.isHudAlert()) {
            this.sendHudAlert(level);
        }
    }

    @Nullable
    private FeWireNetwork findCircuit(Level level) {
        for (Direction direction : Direction.values()) {
            final BlockEntity neighbour = level.getBlockEntity(this.worldPosition.relative(direction));
            final FeWireNetwork network = FeWireNetwork.liveNetworkOf(neighbour);
            if (network != null) {
                return network;
            }
        }
        return null;
    }

    private record Sampled(AnnunciatorEngine.Reading reading, RuleFault fault) {
        static Sampled ok(float value) {
            return new Sampled(AnnunciatorEngine.Reading.of(value), RuleFault.NONE);
        }

        static Sampled missing(RuleFault fault) {
            return new Sampled(AnnunciatorEngine.Reading.MISSING, fault);
        }
    }

    private Sampled sample(Level level, WatchRule rule,
                           @Nullable FeWireNetwork circuit,
                           @Nullable FeGridSolver.Result grid) {
        final RuleBinding binding = rule.getBinding();
        return switch (binding.kind()) {
            case UNBOUND -> Sampled.missing(RuleFault.UNBOUND);
            case GRID -> this.sampleGrid(rule, grid);
            case PANEL_CHANNEL -> this.samplePanel(level, rule, circuit);
            case MACHINE -> this.sampleMachine(level, rule, circuit);
        };
    }

    private Sampled sampleGrid(WatchRule rule, @Nullable FeGridSolver.Result grid) {
        if (!this.variant().canReadGrid()) {
            return Sampled.missing(RuleFault.NO_GRID_DATA);
        }
        if (grid == null) {
            return Sampled.missing(this.circuitFound ? RuleFault.NO_GRID_DATA : RuleFault.NO_CIRCUIT);
        }
        final GridFact fact = rule.getBinding().gridFact();
        if (fact == null) {
            return Sampled.missing(RuleFault.NO_GRID_DATA);
        }

        if (rule.getMeasure() == RuleMeasure.RATE) {
            return Sampled.missing(RuleFault.NO_HISTORY);
        }
        return Sampled.ok(fact.read(grid));
    }

    private Sampled samplePanel(Level level, WatchRule rule, @Nullable FeWireNetwork circuit) {
        final BlockPos target = rule.getBinding().target();
        if (target == null) {
            return Sampled.missing(RuleFault.UNBOUND);
        }
        final RuleFault reach = this.reachFault(level, target, circuit);
        if (reach.isFault()) {
            return Sampled.missing(reach);
        }
        if (!(level.getBlockEntity(target) instanceof StatsDisplayPanelBE panel)) {
            return Sampled.missing(RuleFault.PANEL_MISSING);
        }
        for (StatsDisplayPanelBE.DataChannel channel : panel.getActiveChannels()) {
            if (!channel.getId().equals(rule.getBinding().key())) {
                continue;
            }
            final TimeSeriesStore store = channel.getStore();
            if (store == null || store.getSampleCount() == 0) {
                return Sampled.missing(RuleFault.NO_SAMPLES);
            }
            return Sampled.ok(rule.getMeasure() == RuleMeasure.RATE
                    ? store.getRateOfChange() : store.getLatestValue());
        }

        return Sampled.missing(panel.getChannel(rule.getBinding().key()) == null
                ? RuleFault.CHANNEL_MISSING : RuleFault.NO_SAMPLES);
    }

    private Sampled sampleMachine(Level level, WatchRule rule, @Nullable FeWireNetwork circuit) {
        final BlockPos target = rule.getBinding().target();
        if (target == null) {
            return Sampled.missing(RuleFault.UNBOUND);
        }
        final RuleFault reach = this.reachFault(level, target, circuit);
        if (reach.isFault()) {
            return Sampled.missing(reach);
        }
        final BlockEntity machine = level.getBlockEntity(target);
        if (machine == null) {
            return Sampled.missing(RuleFault.MACHINE_MISSING);
        }
        final InstrumentSourceHelper.SourceReadout readout;
        try {
            readout = InstrumentSourceHelper.readSource(machine, null);
        } catch (RuntimeException ignored) {
            return Sampled.missing(RuleFault.MACHINE_MISSING);
        }
        final InstrumentSourceHelper.MetricSample sample =
                readout.metrics().get(rule.getBinding().key());
        if (sample == null) {
            return Sampled.missing(RuleFault.METRIC_MISSING);
        }

        if (rule.getMeasure() == RuleMeasure.RATE) {
            return Sampled.missing(RuleFault.NO_HISTORY);
        }
        return Sampled.ok(sample.value());
    }

    private RuleFault reachFault(Level level, BlockPos target, @Nullable FeWireNetwork circuit) {
        if (!level.isLoaded(target)) {
            return RuleFault.UNLOADED;
        }
        if (this.variant().isWired()) {
            if (circuit == null) {
                return RuleFault.NO_CIRCUIT;
            }
            return onCircuit(level, circuit, target) ? RuleFault.NONE : RuleFault.OFF_CIRCUIT;
        }
        final int radius = this.effectiveScanRadius();
        return target.distSqr(this.worldPosition) <= (double) radius * radius
                ? RuleFault.NONE : RuleFault.OUT_OF_RANGE;
    }

    private static boolean onCircuit(Level level, FeWireNetwork circuit, BlockPos target) {
        if (circuit.reachesDevice(target)) {
            return true;
        }
        if (level.getBlockEntity(target) instanceof StatsDisplayPanelBE panel) {
            for (BlockPos member : panel.getMultiblockMembers()) {
                if (circuit.reachesDevice(member)) {
                    return true;
                }
            }
        }
        return false;
    }

    private void playHorn() {
        final Level level = this.getLevel();
        if (level == null || level.isClientSide()
                || this.settings.getHorn() == AnnunciatorSettings.Horn.SILENT) {
            return;
        }
        this.lastStrikeTick = level.getGameTime();
        final SoundEvent sound = this.hornSound();
        final float volume = this.settings.getVolume();
        final float pitch = this.settings.getPitch();
        if (this.settings.getHornAudience() == AnnunciatorSettings.Audience.ALL_PLAYERS) {
            level.playSound(null, this.worldPosition, sound, SoundSource.BLOCKS, volume, pitch);
        } else if (level instanceof ServerLevel serverLevel) {
            final int range = AntennaRange.scale(this.settings.getHearingRange(), this.antennaTier);
            for (ServerPlayer player : this.audience(serverLevel, this.settings.getHornAudience(), range)) {
                player.playNotifySound(sound, SoundSource.BLOCKS, volume, pitch);
            }
        }
        this.markDirtyAndSync();
    }

    private SoundEvent hornSound() {
        return this.settings.getHorn().soundEvent();
    }

    private void spawnParticles(Level level) {
        if (this.settings.getParticle() == AnnunciatorSettings.Particle.NONE
                || this.settings.getParticleCount() <= 0
                || !(level instanceof ServerLevel serverLevel)) {
            return;
        }
        final double x = this.worldPosition.getX() + 0.5;

        final double y = this.worldPosition.getY() + 1.9;
        final double z = this.worldPosition.getZ() + 0.5;
        final int count = this.settings.getParticleCount();
        final ParticleOptions particle = switch (this.settings.getParticle()) {
            case SMOKE -> ParticleTypes.CAMPFIRE_SIGNAL_SMOKE;
            case FLAME -> ParticleTypes.FLAME;
            case SOUL_FIRE -> ParticleTypes.SOUL_FIRE_FLAME;
            case SPARK -> ParticleTypes.ELECTRIC_SPARK;
            case REDSTONE -> new DustParticleOptions(
                    new Vector3f(this.severity.colour() >> 16 & 0xFF, this.severity.colour() >> 8 & 0xFF,
                            this.severity.colour() & 0xFF).div(255.0f), 1.0f);
            case NONE -> null;
        };
        if (particle != null) {
            serverLevel.sendParticles(particle, x, y, z, count, 0.2, 0.15, 0.2, 0.02);
        }
    }

    private void sendHudAlert(Level level) {
        if (!(level instanceof ServerLevel serverLevel) || this.culpritLabel.isEmpty()) {
            return;
        }
        final String base = this.settings.getMessage().isEmpty()
                ? this.culpritLabel : this.settings.getMessage();
        final String colour = this.severity == AlarmSeverity.CRITICAL ? "§c"
                : this.severity == AlarmSeverity.WARNING ? "§e" : "§b";
        final String text = colour + "⚠ " + base + " §7" + this.culpritValue;
        final int range = AntennaRange.scale(this.settings.getHudRange(), this.antennaTier);
        for (ServerPlayer player : this.audience(serverLevel, this.settings.getHudAudience(), range)) {
            player.displayClientMessage(Component.literal(text), true);
        }
    }

    private List<ServerPlayer> audience(ServerLevel level, AnnunciatorSettings.Audience audience, int range) {
        final double x = this.worldPosition.getX() + 0.5;
        final double y = this.worldPosition.getY() + 0.5;
        final double z = this.worldPosition.getZ() + 0.5;
        return switch (audience) {
            case ALL_PLAYERS -> List.copyOf(level.players());
            case NEARBY_ONLY -> level.players().stream()
                    .filter(p -> p.distanceToSqr(x, y, z) <= (double) range * range).toList();
            case OWNER_ONLY -> level.players().stream()
                    .filter(p -> this.ownerUUID != null && p.getUUID().equals(this.ownerUUID)).toList();
            case NOBODY -> List.of();
        };
    }

    private void refreshGlow() {
        final Level level = this.getLevel();
        if (level == null || level.isClientSide()
                || !(this.getBlockState().getBlock() instanceof AnnunciatorBlock)) {
            return;
        }
        final AnnunciatorBlock.Glow want = AnnunciatorBlock.Glow.forSeverity(this.severity);
        if (this.getBlockState().getValue(AnnunciatorBlock.GLOW) != want) {
            level.setBlock(this.worldPosition,
                    this.getBlockState().setValue(AnnunciatorBlock.GLOW, want), 3);
        }
    }

    public int redstoneSignal() {
        return this.settings.isRedstoneEnabled() && this.deviceState.isActive()
                && this.severity.isAlarming() ? 15 : 0;
    }

    public int comparatorSignal() {
        return this.severity.comparatorLevel();
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        tooltip.add(com.createstats.power.FeGoggle.title(Component.translatable(
                this.variant().isWired() ? "block.create_stats.klaxon_alarm"
                        : "block.create_stats.sentry_alarm")));

        switch (this.deviceState) {
            case ALARM -> tooltip.add(com.createstats.power.FeGoggle.bad(
                    "ALARM: " + this.culpritLabel + " " + this.culpritValue));
            case ACKNOWLEDGED -> tooltip.add(com.createstats.power.FeGoggle.warn(
                    "Acknowledged: " + this.culpritLabel + " " + this.culpritValue));
            case RINGBACK -> tooltip.add(com.createstats.power.FeGoggle.warn(
                    "Ringback: " + this.culpritLabel + " cleared itself"));
            case NORMAL -> tooltip.add(this.rules.isEmpty()
                    ? com.createstats.power.FeGoggle.dim("No rules set")
                    : com.createstats.power.FeGoggle.ok("Normal"));
        }

        if (isPlayerSneaking) {
            int armed = 0;
            for (WatchRule rule : this.rules) {
                if (rule.isLive()) {
                    armed++;
                }
            }
            tooltip.add(com.createstats.power.FeGoggle.dim(
                    armed + " of " + this.rules.size() + " rules armed"));
        }

        tooltip.add(com.createstats.power.FeGoggle.dim(this.variant().isWired()
                ? (this.circuitFound ? "On circuit" : "No circuit attached")
                : "Range " + this.effectiveScanRadius() + " blocks"
                        + (this.antennaTier == AntennaTier.NONE ? "" : " (aerial)")));

        if (this.faultCount > 0) {
            tooltip.add(com.createstats.power.FeGoggle.warn(
                    this.faultCount + " rule(s) cannot be read"));

            final RuleFault worst = this.commonestFault();
            if (worst.isFault()) {
                tooltip.add(com.createstats.power.FeGoggle.dim(
                        Component.translatable(worst.translationKey()).getString()));
            }
        }
        if (this.toggle.isMuted()) {
            tooltip.add(com.createstats.power.FeGoggle.warn("Horn MUTED"));
        }
        if (!isPlayerSneaking) {
            tooltip.add(com.createstats.power.FeGoggle.more());
        }
        return true;
    }

    public RuleFault commonestFault() {
        final Map<RuleFault, Integer> tally = new HashMap<>();
        for (RuleFault fault : this.faults.values()) {
            tally.merge(fault, 1, Integer::sum);
        }
        RuleFault best = RuleFault.NONE;
        int bestCount = 0;
        for (Map.Entry<RuleFault, Integer> entry : tally.entrySet()) {
            final boolean better = entry.getValue() > bestCount
                    || (entry.getValue() == bestCount && entry.getKey().isReachProblem()
                            && !best.isReachProblem());
            if (better) {
                best = entry.getKey();
                bestCount = entry.getValue();
            }
        }
        return best;
    }

    private void markDirtyAndSync() {
        this.needsSync = true;
        this.setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        this.feConsumer.save(tag);
        tag.put("Settings", this.settings.save());
        final ListTag ruleList = new ListTag();
        for (WatchRule rule : this.rules) {
            ruleList.add(rule.save());
        }
        tag.put("Rules", ruleList);
        final ListTag statusList = new ListTag();
        for (RuleStatus status : this.statuses.values()) {
            statusList.add(status.save());
        }
        tag.put("Statuses", statusList);

        final ListTag faultList = new ListTag();
        for (Map.Entry<String, RuleFault> entry : this.faults.entrySet()) {
            final CompoundTag faultTag = new CompoundTag();
            faultTag.putString("Rule", entry.getKey());
            faultTag.putString("Fault", entry.getValue().getSerializedName());
            faultList.add(faultTag);
        }

        tag.put("RuleFaults", faultList);

        final ListTag logList = new ListTag();
        for (AnnunciatorEvent event : this.log) {
            logList.add(event.save());
        }
        tag.put("Log", logList);
        tag.putString("Toggle", this.toggle.getSerializedName());
        tag.putString("AntennaTier", this.antennaTier.name());
        tag.putInt("NextRuleId", this.nextRuleId);
        tag.putString("Severity", this.severity.getSerializedName());
        tag.putString("State", this.deviceState.getSerializedName());
        tag.putInt("Culprit", this.culpritIndex);
        tag.putString("CulpritLabel", this.culpritLabel);
        tag.putString("CulpritValue", this.culpritValue);
        tag.putInt("Faults", this.faultCount);
        tag.putBoolean("Circuit", this.circuitFound);
        tag.putLong("LastStrike", this.lastStrikeTick);
        tag.putLong("LastAck", this.lastAckTick);
        if (this.ownerUUID != null) {
            tag.putUUID("Owner", this.ownerUUID);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.feConsumer.load(tag);
        if (tag.contains("Settings")) {
            this.settings = AnnunciatorSettings.load(tag.getCompound("Settings"));
        }
        this.rules.clear();
        final ListTag ruleList = tag.getList("Rules", Tag.TAG_COMPOUND);
        for (int i = 0; i < ruleList.size() && i < WatchRule.MAX_RULES; i++) {
            this.rules.add(WatchRule.load(ruleList.getCompound(i)));
        }
        this.statuses = new HashMap<>();
        final ListTag statusList = tag.getList("Statuses", Tag.TAG_COMPOUND);
        for (int i = 0; i < statusList.size(); i++) {
            final RuleStatus status = RuleStatus.load(statusList.getCompound(i));
            this.statuses.put(status.ruleId(), status);
        }
        this.faults = new HashMap<>();
        final ListTag faultList = tag.getList("RuleFaults", Tag.TAG_COMPOUND);
        for (int i = 0; i < faultList.size(); i++) {
            final CompoundTag faultTag = faultList.getCompound(i);
            final RuleFault fault = RuleFault.byName(faultTag.getString("Fault"));
            if (fault.isFault()) {
                this.faults.put(faultTag.getString("Rule"), fault);
            }
        }
        this.log.clear();
        final ListTag logList = tag.getList("Log", Tag.TAG_COMPOUND);
        for (int i = 0; i < logList.size() && i < AnnunciatorEvent.MAX_EVENTS; i++) {
            this.log.addLast(AnnunciatorEvent.load(logList.getCompound(i)));
        }
        this.toggle = ToggleMode.byName(tag.getString("Toggle"));
        this.antennaTier = AntennaTier.byName(tag.getString("AntennaTier"));
        this.nextRuleId = Math.max(1, tag.getInt("NextRuleId"));
        this.severity = AlarmSeverity.byName(tag.getString("Severity"));
        this.deviceState = AnnunciatorState.byName(tag.getString("State"));
        this.culpritIndex = tag.contains("Culprit") ? tag.getInt("Culprit") : -1;
        this.culpritLabel = tag.getString("CulpritLabel");
        this.culpritValue = tag.getString("CulpritValue");
        this.faultCount = tag.getInt("Faults");
        this.circuitFound = tag.getBoolean("Circuit");
        this.lastStrikeTick = tag.contains("LastStrike") ? tag.getLong("LastStrike") : Long.MIN_VALUE;
        this.lastAckTick = tag.contains("LastAck") ? tag.getLong("LastAck") : Long.MIN_VALUE;
        if (tag.hasUUID("Owner")) {
            this.ownerUUID = tag.getUUID("Owner");
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        final CompoundTag tag = super.getUpdateTag(registries);
        this.saveAdditional(tag, registries);
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket packet,
                             HolderLookup.Provider registries) {
        final CompoundTag tag = packet.getTag();
        if (tag != null) {
            this.handleUpdateTag(tag, registries);
        }
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        this.loadAdditional(tag, registries);
    }
}
