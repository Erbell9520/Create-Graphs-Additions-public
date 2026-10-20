package com.createstats.blockentity;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.createstats.alarm.annunciator.AlarmSeverity;
import com.createstats.alarm.annunciator.AnnunciatorEvent;
import com.createstats.data.MetricType;
import com.createstats.network.PaSpeakPacket;
import com.createstats.pa.Announcer;
import com.createstats.pa.DeviceId;
import com.createstats.pa.DeviceIdRegistry;
import com.createstats.pa.Lexicon;
import com.createstats.pa.PaBinding;
import com.createstats.pa.PaAudioZone;
import com.createstats.pa.PaCatalog;
import com.createstats.pa.PaImport;
import com.createstats.pa.PaReach;
import com.createstats.pa.PaSettings;
import com.createstats.pa.PaSubscription;
import com.createstats.pa.PaVocabulary;
import com.createstats.pa.Utterance;
import com.createstats.pa.Verbalizer;
import com.createstats.power.FeGridMember;
import com.createstats.power.FeLock;
import com.createstats.power.FeManagedLoad;
import com.createstats.power.FeManagedState;
import com.createstats.power.FePriority;
import com.createstats.power.FeWireNetwork;
import com.createstats.power.FeWireTerminal;
import com.createstats.power.WireTier;
import com.createstats.registry.ModItems;
import com.createstats.util.InstrumentSourceHelper.MetricSample;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

public class AnnouncementComputerBE extends BlockEntity implements FeGridMember, FeManagedLoad,
        FeWireTerminal, com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation {
    private static final int SCAN_INTERVAL = 100;

    private static final int POLL_INTERVAL = 20;

    private static final int HISTORY = 16;

    private static final int MAX_LOG = 24;

    private static final int BRAIN_HALF_HEIGHT = 12;

    private static final float FAN_TURNS_PER_TICK = 0.055f;

    private static final int IDLE_DRAW = 6;

    private static final int SCAN_COST = 40;

    private static final int SPEAK_COST_PER_CLIP = 30;

    private static final int BUFFER = 8000;

    private int energy;
    private int lastSyncedEnergy;
    private boolean powered = true;
    private int ratedDraw = IDLE_DRAW;
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

    private final PaSettings settings = new PaSettings();
    private final Map<String, PaSubscription> subscriptions = new LinkedHashMap<>();

    private final Announcer announcer = new Announcer();
    private final Map<String, float[]> history = new LinkedHashMap<>();
    private final Map<String, Integer> historyAt = new LinkedHashMap<>();
    private List<PaCatalog.Row> catalog = List.of();
    private long nextBulletinAt;

    private AlarmSeverity worst = AlarmSeverity.NORMAL;
    private boolean speaking;
    private int queued;

    private boolean held;

    private boolean starved;
    private int watchedCount;
    private int unreadableCount;

    private int importable;

    private int speakers;
    private final List<String> spokenLog = new ArrayList<>();

    private float fanAngle;
    private float lastFanAngle;
    private float fanSpin = 1.0f;
    private int phase;

    public AnnouncementComputerBE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public PaSettings settings() {
        return this.settings;
    }

    public List<PaCatalog.Row> catalog() {
        return this.catalog;
    }

    public Map<String, PaSubscription> subscriptions() {
        return this.subscriptions;
    }

    public AlarmSeverity worstSeverity() {
        return this.worst;
    }

    public boolean isSpeaking() {
        return this.speaking;
    }

    public int queuedCount() {
        return this.queued;
    }

    public boolean isHeld() {
        return this.held;
    }

    public boolean isStarved() {
        return this.starved;
    }

    public Utterance onAir() {
        return this.announcer.speaking();
    }

    public int onAirWord() {
        return this.level == null ? -1 : this.announcer.speakingWordIndex(this.level.getGameTime());
    }

    public List<Announcer.Waiting> pendingQueue() {
        return this.level == null ? List.of() : this.announcer.peekQueue(this.level.getGameTime());
    }

    public int watchedCount() {
        return this.watchedCount;
    }

    public int unreadableCount() {
        return this.unreadableCount;
    }

    public int importableCount() {
        return this.importable;
    }

    public List<String> spokenLog() {
        return List.copyOf(this.spokenLog);
    }

    public int storedEnergyFe() {
        return this.energy;
    }

    public int energyCapacityFe() {
        return BUFFER;
    }

    public boolean isPowered() {
        return this.powered;
    }

    public int ratedDrawFe() {
        return this.ratedDraw;
    }

    public void serverTick() {
        if (!(this.level instanceof ServerLevel server)) {
            return;
        }
        final long now = server.getGameTime();

        if (this.network == null || this.network.isDirty()) {
            FeWireNetwork.buildFrom(this);
        }
        if (this.network != null) {
            this.network.solveOnce(server);
        }

        // going dark is a real state. it does NOT ack anymore, that cleared the queue

        if (!this.drawPower(now)) {
            if (this.powered) {
                this.powered = false;
                this.markDirtyAndSync();
            }

            this.announcer.tick(now, this.speechGate());
            this.syncVoiceState(now);
            return;
        }
        if (!this.powered) {
            this.powered = true;
            this.markDirtyAndSync();
        }

        if (now % SCAN_INTERVAL == 0 && this.energy >= SCAN_COST) {
            this.energy -= SCAN_COST;
            this.catalog = PaCatalog.build(server, this.worldPosition, this.settings.scanRadius(),
                    PaReach.circuitPositions(this));

            final int offer = PaImport.newCount(PaImport.plan(this, this.catalog));
            final int relays = com.createstats.pa.PaSpeakerLink.speakersFor(server, this).size();
            if (offer != this.importable || relays != this.speakers) {
                this.importable = offer;
                this.speakers = relays;
                this.markDirtyAndSync();
            }
        }
        if (now % POLL_INTERVAL == 0) {
            this.evaluate(server, now);
        }

        if (this.nextBulletinAt == 0) {
            this.nextBulletinAt = now + this.settings.bulletinIntervalTicks();
        }
        if (this.settings.bulletinEnabled() && now >= this.nextBulletinAt) {
            this.nextBulletinAt = now + this.settings.bulletinIntervalTicks();

            final List<Verbalizer.Reading> readings = this.bulletinReadings(server);
            if (!readings.isEmpty()) {
                this.announcer.offer(Verbalizer.bulletin(readings, this.settings.bulletinVerbosity(),
                        this.settings.bulletinFacts()), now);
            }
        }

        final Utterance starting = this.announcer.tick(now, this.speechGate());
        if (starting != null) {
            this.energy -= starting.clips().size() * SPEAK_COST_PER_CLIP;
            this.speak(server, starting, true);
        }

        this.syncVoiceState(now);
    }

    private Announcer.Gate speechGate() {
        return new Announcer.Gate() {
            @Override
            public boolean open() {
                return AnnouncementComputerBE.this.powered
                        && !AnnouncementComputerBE.this.settings.muted();
            }

            @Override
            public boolean affords(Utterance utterance) {
                return AnnouncementComputerBE.this.energy
                        >= utterance.clips().size() * SPEAK_COST_PER_CLIP;
            }
        };
    }

    private void syncVoiceState(long now) {
        final boolean nowSpeaking = this.announcer.isSpeaking(now);
        final int nowQueued = this.announcer.queued();
        final boolean nowHeld = this.announcer.isHeld();
        final boolean nowStarved = this.announcer.isStarved();
        if (nowSpeaking != this.speaking || nowQueued != this.queued || nowHeld != this.held
                || nowStarved != this.starved) {
            this.speaking = nowSpeaking;
            this.queued = nowQueued;
            this.held = nowHeld;
            this.starved = nowStarved;
            this.markDirtyAndSync();
        }
    }

    // edges only. "warning 94%" three times a minute forever is not an alarm, it's a neighbour
    private void evaluate(ServerLevel level, long now) {
        AlarmSeverity worstNow = AlarmSeverity.NORMAL;
        int watched = 0;
        int unreadable = 0;

        for (PaSubscription sub : this.subscriptions.values()) {
            if (!sub.enabled()) {
                continue;
            }
            watched++;
            final boolean volunteers = sub.discipline() == PaSubscription.Discipline.ON_EVENT;
            if (sub.binding().kind() == PaBinding.Kind.DEVICE) {
                worstNow = AlarmSeverity.worst(worstNow,
                        this.pollDevice(level, sub, now, volunteers));
                continue;
            }

            this.refreshFollowed(level, sub);

            final MetricSample sample = PaCatalog.sample(level, sub.binding());
            if (sample == null) {
                unreadable++;
                sub.updateTrip(Float.NaN);

                if (sub.updateAvailability(false) && volunteers) {
                    this.offerReading(level, sub, 0.0f, MetricType.GENERIC, false,
                            Verbalizer.Phase.RAISED, now);
                }
                continue;
            }
            if (sub.updateAvailability(true) && volunteers) {
                this.offerReading(level, sub, sample.value(), sample.type(), true,
                        Verbalizer.Phase.RESTORED, now);
            }
            this.push(sub.key(), sample.value());
            if (sub.updateTrip(sample.value())) {
                worstNow = AlarmSeverity.worst(worstNow, sub.tripped() ? sub.severity() : AlarmSeverity.NORMAL);
                if (volunteers) {
                    this.offerReading(level, sub, sample.value(), sample.type(), true,
                            sub.tripped() ? Verbalizer.Phase.RAISED : Verbalizer.Phase.CLEARED, now);
                }
            } else if (sub.tripped()) {
                worstNow = AlarmSeverity.worst(worstNow, sub.severity());
            }
        }

        if (worstNow != this.worst || watched != this.watchedCount || unreadable != this.unreadableCount) {
            this.worst = worstNow;
            this.watchedCount = watched;
            this.unreadableCount = unreadable;
            this.markDirtyAndSync();
        }
    }

    private void refreshFollowed(ServerLevel level, PaSubscription sub) {
        if (!sub.followSource() || sub.binding().kind() != PaBinding.Kind.CHANNEL) {
            return;
        }
        final StatsDisplayPanelBE.DataChannel channel = PaCatalog.channelOf(level, sub.binding());
        if (channel != null && channel.isAlertEnabled()) {
            sub.observeSource(channel.getAlertThreshold(), channel.isAlertAbove());
        } else {
            sub.clearObservedSource();
        }
    }

    // both alarm types answer here. stats alarm got forgotten once
    private AlarmSeverity pollDevice(ServerLevel level, PaSubscription sub, long now,
                                     boolean volunteers) {
        final BlockEntity be = level.isLoaded(sub.binding().pos())
                ? level.getBlockEntity(sub.binding().pos()) : null;
        if (be instanceof StatsAlarmBE alarm) {
            return this.pollAlarm(level, alarm, sub, now, volunteers);
        }
        if (!(be instanceof AnnunciatorBE device)) {
            return AlarmSeverity.NORMAL;
        }
        final AlarmSeverity severity = device.getSeverity();
        final DeviceId id = DeviceIdRegistry.get(level).idFor(sub.binding().pos(),
                device.getBlockState());

        final boolean alarming = severity.isAlarming();
        final boolean edge = sub.updateDeviceTrip(alarming);
        if (!alarming) {
            if (edge && volunteers) {
                this.announcer.offer(new Utterance.Builder()
                        .severity(AlarmSeverity.NORMAL)
                        .dedupe("device/" + id.number() + "/clear")
                        .say(Lexicon.ALL_CLEAR)
                        .say(Lexicon.ALARM)
                        .say(id.isPresent() ? Lexicon.UNIT : "")
                        .say(id.isPresent() ? Lexicon.number(id.number()) : List.of())
                        .say(Lexicon.NOMINAL)
                        .build(), now);
            }
            return AlarmSeverity.NORMAL;
        }
        if (volunteers) {
            this.announcer.offer(deviceAlarm(id, severity), now);
        }
        return severity;
    }

    private static Utterance deviceAlarm(DeviceId id, AlarmSeverity severity) {
        return new Utterance.Builder()
                .severity(severity)
                .dedupe("device/" + id.number() + "/" + severity.getSerializedName())
                .say(Verbalizer.urgency(severity))
                .say(Lexicon.ALARM)
                .say(id.isPresent() ? Lexicon.UNIT : "")
                .say(id.isPresent() ? Lexicon.number(id.number()) : List.of())
                .say(severity == AlarmSeverity.CRITICAL ? Lexicon.CRITICAL : Lexicon.HIGH)
                .build();
    }

    private AlarmSeverity pollAlarm(ServerLevel level, StatsAlarmBE alarm, PaSubscription sub,
                                    long now, boolean volunteers) {
        final DeviceId id = DeviceIdRegistry.get(level).idFor(sub.binding().pos(),
                alarm.getBlockState());
        final boolean ringing = alarm.isActive();
        final boolean edge = sub.updateDeviceTrip(ringing);
        if (!ringing) {
            if (edge && volunteers) {
                this.announcer.offer(new Utterance.Builder()
                        .severity(AlarmSeverity.NORMAL)
                        .dedupe("alarm/" + id.number() + "/clear")
                        .say(Lexicon.ALL_CLEAR)
                        .say(Lexicon.ALARM)
                        .say(id.isPresent() ? Lexicon.UNIT : "")
                        .say(id.isPresent() ? Lexicon.number(id.number()) : List.of())
                        .build(), now);
            }
            return AlarmSeverity.NORMAL;
        }
        if (volunteers) {
            this.announcer.offer(new Utterance.Builder()
                    .severity(AlarmSeverity.WARNING)
                    .dedupe("alarm/" + id.number())
                    .say(Verbalizer.urgency(AlarmSeverity.WARNING))
                    .say(Lexicon.ALARM)
                    .say(id.isPresent() ? Lexicon.UNIT : "")
                    .say(id.isPresent() ? Lexicon.number(id.number()) : List.of())
                    .build(), now);
        }
        return AlarmSeverity.WARNING;
    }

    private void offerReading(ServerLevel level, PaSubscription sub, float value, MetricType type,
                              boolean available, Verbalizer.Phase phase, long now) {
        final Utterance u = Verbalizer.say(this.readingFor(level, sub, value, type, available),
                sub.verbosity(), sub.tripped() ? sub.severity() : AlarmSeverity.NORMAL, phase);
        this.announcer.offer(u, now);
    }

    public Verbalizer.Reading readingFor(ServerLevel level, PaSubscription sub) {
        final MetricSample sample = PaCatalog.sample(level, sub.binding());
        return this.readingFor(level, sub, sample == null ? 0.0f : sample.value(),
                sample == null ? MetricType.GENERIC : sample.type(), sample != null);
    }

    private Verbalizer.Reading readingFor(ServerLevel level, PaSubscription sub, float value,
                                          MetricType type, boolean available) {
        final BlockPos pos = PaCatalog.subjectPos(level, sub.binding());
        final BlockState state = level.isLoaded(pos) ? level.getBlockState(pos) : null;
        final DeviceId id = state == null ? DeviceId.NONE
                : DeviceIdRegistry.get(level).idFor(pos, state);
        final String subject = state == null ? Lexicon.MACHINE
                : PaVocabulary.subjectForBlock(PaCatalog.registryPath(state), PaCatalog.kindAt(level, pos));
        final String measure = sub.binding().kind() == PaBinding.Kind.CHANNEL
                ? PaVocabulary.measureForChannel(sub.binding().metricId(), type)
                : PaVocabulary.measure(sub.binding().metricId());
        return new Verbalizer.Reading(subject, id, measure, value, type, this.trend(sub.key()),
                available);
    }

    private List<Verbalizer.Reading> bulletinReadings(ServerLevel level) {
        record Line(Verbalizer.Reading reading, boolean tripped) {
        }
        final List<Line> lines = new ArrayList<>();
        for (PaSubscription sub : this.subscriptions.values()) {
            if (!sub.enabled() || sub.discipline() == PaSubscription.Discipline.ON_DEMAND) {
                continue;
            }
            final boolean periodic = sub.discipline() == PaSubscription.Discipline.PERIODIC;
            if (!periodic && !sub.tripped()) {
                continue;
            }

            if (sub.binding().kind() == PaBinding.Kind.DEVICE) {
                continue;
            }
            final MetricSample sample = PaCatalog.sample(level, sub.binding());
            lines.add(new Line(this.readingFor(level, sub, sample == null ? 0.0f : sample.value(),
                    sample == null ? MetricType.GENERIC : sample.type(), sample != null),
                    sub.tripped()));
        }
        lines.sort((a, b) -> {
            if (a.tripped() != b.tripped()) {
                return a.tripped() ? -1 : 1;
            }
            return Double.compare(b.reading().value(), a.reading().value());
        });
        final List<Verbalizer.Reading> out = new ArrayList<>(lines.size());
        for (Line line : lines) {
            out.add(line.reading());
        }
        return out;
    }

    public void sayOnDemandReport() {
        if (!(this.level instanceof ServerLevel server)) {
            return;
        }
        final long now = server.getGameTime();
        final List<Verbalizer.Reading> readings = new ArrayList<>();
        for (PaSubscription sub : this.subscriptions.values()) {
            if (!sub.enabled() || sub.discipline() != PaSubscription.Discipline.ON_DEMAND
                    || sub.binding().kind() == PaBinding.Kind.DEVICE) {
                continue;
            }
            final MetricSample sample = PaCatalog.sample(server, sub.binding());
            readings.add(this.readingFor(server, sub, sample == null ? 0.0f : sample.value(),
                    sample == null ? MetricType.GENERIC : sample.type(), sample != null));
        }
        if (readings.isEmpty()) {
            this.announcer.offer(Verbalizer.bulletin(List.of(), this.settings.bulletinVerbosity(),
                    this.settings.bulletinFacts()), now);
            return;
        }
        readings.sort((a, b) -> Double.compare(b.value(), a.value()));
        this.announcer.offer(Verbalizer.bulletin(readings, this.settings.bulletinVerbosity(),
                Math.max(this.settings.bulletinFacts(), readings.size())), now);
    }

    public int onDemandCount() {
        int count = 0;
        for (PaSubscription sub : this.subscriptions.values()) {
            if (sub.enabled() && sub.discipline() == PaSubscription.Discipline.ON_DEMAND) {
                count++;
            }
        }
        return count;
    }

    private void push(String key, float value) {
        final float[] ring = this.history.computeIfAbsent(key, k -> {
            final float[] fresh = new float[HISTORY];
            java.util.Arrays.fill(fresh, Float.NaN);
            return fresh;
        });
        final int at = this.historyAt.getOrDefault(key, 0);
        ring[at % HISTORY] = value;
        this.historyAt.put(key, at + 1);
    }

    private Verbalizer.Trend trend(String key) {
        final float[] ring = this.history.get(key);
        if (ring == null) {
            return Verbalizer.Trend.STEADY;
        }
        final int count = Math.min(HISTORY, this.historyAt.getOrDefault(key, 0));
        if (count < 4) {
            return Verbalizer.Trend.STEADY;
        }
        final int at = this.historyAt.get(key);
        final float newest = ring[(at - 1 + HISTORY * 2) % HISTORY];
        final float oldest = ring[(at - count + HISTORY * 2) % HISTORY];
        if (Float.isNaN(newest) || Float.isNaN(oldest)) {
            return Verbalizer.Trend.STEADY;
        }
        final float span = Math.max(1.0f, Math.abs(oldest));
        final float change = (newest - oldest) / span;

        if (change > 0.05f) {
            return Verbalizer.Trend.RISING;
        }
        return change < -0.05f ? Verbalizer.Trend.FALLING : Verbalizer.Trend.STEADY;
    }

    private void speak(ServerLevel level, Utterance utterance, boolean stopFirst) {
        final List<com.createstats.blockentity.PaSpeakerBE> relays =
                com.createstats.pa.PaSpeakerLink.speakersFor(level, this);

        final int lit = utterance.estimatedTicks();
        for (com.createstats.blockentity.PaSpeakerBE speaker : relays) {
            speaker.markSpoke(lit);
        }
        final List<Heard> listeners = this.audience(level, relays);
        for (Heard heard : listeners) {
            PacketDistributor.sendToPlayer(heard.player(),
                    PaSpeakPacket.of(heard.source(), utterance, stopFirst, this.settings.volume(),
                            this.settings.pitch(), heard.range()));
        }

        com.createstats.DebugLogging.pa(
                "PA {} speaking {} clips for {} ticks: {} relays lit, {} listeners",
                this.worldPosition, utterance.clips().size(), lit, relays.size(), listeners.size());
        this.spokenLog.add(0, String.join(" ", utterance.clips()));
        while (this.spokenLog.size() > MAX_LOG) {
            this.spokenLog.remove(this.spokenLog.size() - 1);
        }
        this.markDirtyAndSync();
    }

    private List<Heard> audience(ServerLevel level,
                                 List<com.createstats.blockentity.PaSpeakerBE> speakers) {
        final List<Heard> out = new ArrayList<>();
        final boolean everyone = this.settings.audience() == PaSettings.Audience.EVERYONE;
        for (ServerPlayer player : level.players()) {
            final Heard heard = this.sourceFor(player, speakers, everyone);
            if (heard == null) {
                continue;
            }
            out.add(heard);
        }
        return out;
    }

    @Nullable
    private Heard sourceFor(ServerPlayer player, List<com.createstats.blockentity.PaSpeakerBE> speakers,
                            boolean everyone) {
        com.createstats.blockentity.PaSpeakerBE bestSpeaker = null;
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;

        if (PaAudioZone.covers(this.worldPosition, player, this.settings.hearingRange(),
                BRAIN_HALF_HEIGHT)) {
            best = this.worldPosition;
            bestDist = this.worldPosition.distSqr(player.blockPosition());
        }
        for (com.createstats.blockentity.PaSpeakerBE speaker : speakers) {
            if (!PaAudioZone.covers(speaker.getBlockPos(), player.blockPosition(), speaker.kind())) {
                continue;
            }
            final double d = speaker.getBlockPos().distSqr(player.blockPosition());
            if (d < bestDist) {
                bestDist = d;
                best = speaker.getBlockPos();
                bestSpeaker = speaker;
            }
        }
        if (best != null) {
            return new Heard(player, best, bestSpeaker, rangeOf(bestSpeaker));
        }
        if (!everyone) {
            return null;
        }

        BlockPos fallback = this.worldPosition;
        double fallbackDist = this.worldPosition.distSqr(player.blockPosition());
        com.createstats.blockentity.PaSpeakerBE fallbackSpeaker = null;
        for (com.createstats.blockentity.PaSpeakerBE speaker : speakers) {
            final double d = speaker.getBlockPos().distSqr(player.blockPosition());
            if (d < fallbackDist) {
                fallbackDist = d;
                fallback = speaker.getBlockPos();
                fallbackSpeaker = speaker;
            }
        }

        final double voiceRange = com.createstats.registry.ModSounds.PA_VOICE_RANGE;
        if (fallbackDist > voiceRange * voiceRange) {
            return new Heard(player, player.blockPosition(), null, this.settings.hearingRange());
        }
        return new Heard(player, fallback, fallbackSpeaker, rangeOf(fallbackSpeaker));
    }

    private int rangeOf(@Nullable com.createstats.blockentity.PaSpeakerBE speaker) {
        return speaker == null ? this.settings.hearingRange() : speaker.kind().range();
    }

    private record Heard(ServerPlayer player, BlockPos source,
                         @Nullable com.createstats.blockentity.PaSpeakerBE speaker, int range) {
    }

    public int speakerCount() {
        return this.speakers;
    }

    public void acknowledge() {
        if (this.level instanceof ServerLevel server) {
            this.announcer.acknowledge(server.getGameTime());
            final List<com.createstats.blockentity.PaSpeakerBE> relays =
                    com.createstats.pa.PaSpeakerLink.speakersFor(server, this);

            for (com.createstats.blockentity.PaSpeakerBE speaker : relays) {
                speaker.stopSpeaking();
            }
            for (Heard heard : this.audience(server, relays)) {
                PacketDistributor.sendToPlayer(heard.player(),
                        PaSpeakPacket.silence(heard.source()));
            }
            this.speaking = false;
            this.queued = 0;
            this.held = false;
            this.starved = false;
            this.markDirtyAndSync();
        }
    }

    public void sayNow(Utterance utterance) {
        if (this.level instanceof ServerLevel server && !utterance.isEmpty()) {
            this.speak(server, utterance, true);
        }
    }

    public void addSubscription(PaSubscription sub) {
        this.subscriptions.put(sub.key(), sub);
        this.markDirtyAndSync();
    }

    public void removeSubscription(String key) {
        if (this.subscriptions.remove(key) != null) {
            this.history.remove(key);
            this.historyAt.remove(key);
            this.markDirtyAndSync();
        }
    }

    private boolean drawPower(long now) {
        this.ratedDraw = IDLE_DRAW + SCAN_COST / SCAN_INTERVAL
                + (this.subscriptions.size() * SPEAK_COST_PER_CLIP) / 60;
        if (this.energy < IDLE_DRAW) {
            return false;
        }
        this.energy -= IDLE_DRAW;

        if (Math.abs(this.energy - this.lastSyncedEnergy) * 16 >= BUFFER || now % 200 == 0) {
            this.lastSyncedEnergy = this.energy;
            this.markDirtyAndSync();
        }
        return true;
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
        return Math.max(64, SPEAK_COST_PER_CLIP * 8);
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

    public Direction backFace() {
        final BlockState state = this.getBlockState();
        return state.hasProperty(com.createstats.block.AnnouncementComputerBlock.FACING)
                ? state.getValue(com.createstats.block.AnnouncementComputerBlock.FACING).getOpposite()
                : Direction.SOUTH;
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
        if (this.managed.isShed() == shed) {
            return;
        }
        this.managed.setShed(shed);
        this.setChanged();
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
        return this.powered ? IDLE_DRAW : 0;
    }

    @Override
    public boolean addToGoggleTooltip(List<net.minecraft.network.chat.Component> tooltip,
                                      boolean isPlayerSneaking) {
        com.simibubi.create.foundation.utility.CreateLang.translate("gui.goggles.pa_brain")
                .forGoggles(tooltip);
        com.simibubi.create.foundation.utility.CreateLang.text(
                        this.powered ? "Powered" : "No power")
                .style(this.powered ? net.minecraft.ChatFormatting.GREEN
                        : net.minecraft.ChatFormatting.RED)
                .forGoggles(tooltip, 1);
        com.simibubi.create.foundation.utility.CreateLang.text(
                        this.energy + " / " + BUFFER + " FE")
                .style(net.minecraft.ChatFormatting.GRAY)
                .forGoggles(tooltip, 1);
        com.simibubi.create.foundation.utility.CreateLang.text(
                        this.subscriptions.size() + " announced, " + this.ratedDraw + " FE/t")
                .style(net.minecraft.ChatFormatting.GRAY)
                .forGoggles(tooltip, 1);
        com.simibubi.create.foundation.utility.CreateLang.text(
                        this.speakers == 0 ? "No speakers on this circuit"
                                : this.speakers + (this.speakers == 1 ? " speaker relaying"
                                        : " speakers relaying"))
                .style(this.speakers > 0 ? net.minecraft.ChatFormatting.GREEN
                        : net.minecraft.ChatFormatting.GRAY)
                .forGoggles(tooltip, 1);

        if (this.held || this.starved) {
            com.simibubi.create.foundation.utility.CreateLang.text(
                            this.starved ? "Holding " + this.queued + ": not enough FE for a sentence"
                                    : "Muted, holding " + this.queued)
                    .style(net.minecraft.ChatFormatting.YELLOW)
                    .forGoggles(tooltip, 1);
        } else if (this.speaking) {
            com.simibubi.create.foundation.utility.CreateLang.text("Speaking")
                    .style(net.minecraft.ChatFormatting.GREEN)
                    .forGoggles(tooltip, 1);
        }
        if (this.importable > 0) {
            com.simibubi.create.foundation.utility.CreateLang.text(
                            this.importable + " nearby alarms not announced yet")
                    .style(net.minecraft.ChatFormatting.AQUA)
                    .forGoggles(tooltip, 1);
        }
        return true;
    }

    public void clientTick() {
        this.phase++;
        this.lastFanAngle = this.fanAngle;

        if (!this.powered) {
            this.fanSpin = Math.max(0.0f, this.fanSpin - 0.02f);
        } else {
            this.fanSpin = Math.min(1.0f, this.fanSpin + 0.05f);
        }
        this.fanAngle += FAN_TURNS_PER_TICK * 360.0f * this.fanSpin;
        if (this.fanAngle >= 360.0f) {
            this.fanAngle -= 360.0f;
            this.lastFanAngle -= 360.0f;
        }
    }

    public float fanAngle(float partialTick) {
        return Mth.lerp(partialTick, this.lastFanAngle, this.fanAngle);
    }

    public float pilotGlow(float partialTick) {
        if (!this.powered) {
            return 0.0f;
        }
        final float t = (this.phase + partialTick) / 20.0f;
        return 0.45f + 0.55f * (0.5f + 0.5f * Mth.sin(t * 0.9f));
    }

    public float barLevel(int index, float partialTick) {
        if (!this.powered) {
            return 0.0f;
        }
        final float t = (this.phase + partialTick) / 20.0f;
        final float[] rate = {0.37f, 0.61f, 0.23f};
        final int i = Mth.clamp(index, 0, 2);
        final boolean live = this.speaking;
        final float bias = live ? 0.55f : 0.12f;
        final float swing = live ? 0.42f : 0.06f;
        return Mth.clamp(bias + swing * Mth.sin(t * rate[i] * Mth.TWO_PI + i * 1.7f), 0.04f, 1.0f);
    }

    public int litLens() {
        if (!this.powered) {
            return -1;
        }
        return switch (this.worst) {
            case NOTICE -> 0;
            case WARNING -> 1;
            case CRITICAL -> 2;
            case NORMAL -> -1;
        };
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Settings", this.settings.save());
        final ListTag subs = new ListTag();
        for (PaSubscription sub : this.subscriptions.values()) {
            subs.add(sub.save());
        }
        tag.put("Subs", subs);
        tag.putInt("Energy", this.energy);
        tag.putBoolean("Powered", this.powered);
        tag.putInt("Rated", this.ratedDraw);
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
        this.settings.load(tag.getCompound("Settings"));
        this.subscriptions.clear();
        final ListTag subs = tag.getList("Subs", Tag.TAG_COMPOUND);
        for (int i = 0; i < subs.size(); i++) {
            final PaSubscription sub = PaSubscription.load(subs.getCompound(i));
            this.subscriptions.put(sub.key(), sub);
        }
        this.energy = Math.max(0, Math.min(BUFFER, tag.getInt("Energy")));
        this.powered = !tag.contains("Powered") || tag.getBoolean("Powered");
        this.ratedDraw = Math.max(IDLE_DRAW, tag.getInt("Rated"));
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
        if (tag.contains("Worst")) {
            this.worst = AlarmSeverity.byName(tag.getString("Worst"));
            this.speaking = tag.getBoolean("Speaking");
            this.queued = tag.getInt("Queued");

            this.held = tag.getBoolean("Held");
            this.starved = tag.getBoolean("Starved");
            this.watchedCount = tag.getInt("Watched");
            this.unreadableCount = tag.getInt("Unreadable");
            this.importable = tag.getInt("Importable");
            this.speakers = tag.getInt("Speakers");
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        final CompoundTag tag = super.getUpdateTag(registries);
        this.saveAdditional(tag, registries);
        tag.putString("Worst", this.worst.getSerializedName());
        tag.putBoolean("Speaking", this.speaking);
        tag.putInt("Queued", this.queued);
        tag.putBoolean("Held", this.held);
        tag.putBoolean("Starved", this.starved);
        tag.putInt("Watched", this.watchedCount);
        tag.putInt("Unreadable", this.unreadableCount);
        tag.putInt("Importable", this.importable);
        tag.putInt("Speakers", this.speakers);
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
