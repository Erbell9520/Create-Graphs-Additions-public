package com.createstats.pa;

import java.util.ArrayList;
import java.util.List;

import com.createstats.blockentity.AnnouncementComputerBE;
import com.createstats.blockentity.StatsDisplayPanelBE.DataChannel;
import com.createstats.data.MetricType;
import com.createstats.network.PaActionPacket;
import com.createstats.network.PaSnapshotPacket;
import com.createstats.pa.PaSettings.Audience;
import com.createstats.pa.PaSubscription.Discipline;
import com.createstats.pa.PaSubscription.Verbosity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

public final class PaConsoleRequests {
    private PaConsoleRequests() {
    }

    public static void open(ServerPlayer player, AnnouncementComputerBE brain) {
        send(player, brain);
    }

    public static void send(ServerPlayer player, AnnouncementComputerBE brain) {
        PacketDistributor.sendToPlayer(player, new PaSnapshotPacket(snapshot(brain)));
    }

    public static PaSnapshot snapshot(AnnouncementComputerBE brain) {
        final List<PaSnapshot.Row> rows = new ArrayList<>();
        final List<PaSnapshot.SpeakerRow> speakers = new ArrayList<>();
        int importable = 0;
        int circuit = 0;
        if (brain.getLevel() instanceof ServerLevel level) {
            final java.util.Set<Long> reached = PaReach.circuitPositions(brain);
            circuit = reached.size();
            final List<PaCatalog.Row> catalog = PaCatalog.build(level, brain.getBlockPos(),
                    brain.settings().scanRadius(), reached);
            for (PaCatalog.Row row : catalog) {
                rows.add(PaSnapshot.Row.of(row));
            }
            importable = PaImport.newCount(PaImport.plan(brain, catalog));
            speakers.addAll(speakerRoster(level, brain, reached));
        }

        final List<PaSnapshot.Sub> subs = new ArrayList<>();
        for (PaSubscription sub : brain.subscriptions().values()) {
            subs.add(new PaSnapshot.Sub(sub.key(), sub.discipline().getSerializedName(),
                    sub.verbosity().getSerializedName(), sub.severity().getSerializedName(),
                    sub.effectiveComparator().getSerializedName(), sub.effectiveThreshold(),
                    sub.deadband(), sub.enabled(), sub.tripped(), sub.isFollowing()));
        }

        return new PaSnapshot(brain.getBlockPos(), brain.settings().copy(), rows, subs,
                brain.spokenLog(), brain.worstSeverity().ordinal(), brain.isSpeaking(),
                brain.queuedCount(), brain.storedEnergyFe(), brain.energyCapacityFe(),
                brain.ratedDrawFe(), brain.isPowered(), importable, circuit, speakers,
                voiceOf(brain));
    }

    // every speaker including dark ones, the dark one IS the answer to "why can't I hear it"
    private static List<PaSnapshot.SpeakerRow> speakerRoster(ServerLevel level,
                                                             AnnouncementComputerBE brain,
                                                             java.util.Set<Long> circuit) {
        final List<PaSnapshot.SpeakerRow> out = new ArrayList<>();
        for (Long packed : circuit) {
            final BlockPos pos = BlockPos.of(packed);
            if (!level.isLoaded(pos)) {
                continue;
            }
            if (!(level.getBlockEntity(pos) instanceof com.createstats.blockentity.PaSpeakerBE be)) {
                continue;
            }
            out.add(new PaSnapshot.SpeakerRow(pos.immutable(), be.kind().getSerializedName(),
                    be.isPowered(), be.isSpeaking(),
                    (int) Math.round(Math.sqrt(brain.getBlockPos().distSqr(pos))),
                    be.kind().range()));
        }

        out.sort((a, b) -> Integer.compare(a.distance(), b.distance()));
        return out;
    }

    private static PaSnapshot.Voice voiceOf(AnnouncementComputerBE brain) {
        final List<PaSnapshot.Waiting> queue = new ArrayList<>();
        for (Announcer.Waiting waiting : brain.pendingQueue()) {
            queue.add(new PaSnapshot.Waiting(waiting.utterance().severity().ordinal(),
                    waiting.utterance().clips(), waiting.waitedTicks()));
        }
        return new PaSnapshot.Voice(brain.onAir().clips(), brain.onAirWord(), queue,
                brain.isHeld(), brain.isStarved(), brain.onDemandCount());
    }

    public static void apply(ServerPlayer player, AnnouncementComputerBE brain, PaActionPacket pkt) {
        final PaSettings settings = brain.settings();
        boolean resend = true;

        final boolean readOnly = "refresh".equals(pkt.verb()) || "test".equals(pkt.verb())
                || "ask".equals(pkt.verb());

        switch (pkt.verb()) {
            case "subscribe" -> subscribe(brain, pkt.key());
            case "unsubscribe" -> brain.removeSubscription(pkt.key());
            case "enabled" -> withSub(brain, pkt.key(), s -> s.setEnabled(pkt.number() > 0.5f));
            case "discipline" -> withSub(brain, pkt.key(),
                    s -> s.setDiscipline(PaSubscription.disciplineByName(pkt.value())));
            case "verbosity" -> withSub(brain, pkt.key(),
                    s -> s.setVerbosity(PaSubscription.verbosityByName(pkt.value())));
            case "severity" -> withSub(brain, pkt.key(),
                    s -> s.setSeverity(com.createstats.alarm.annunciator.AlarmSeverity.byName(pkt.value())));
            case "threshold" -> withSub(brain, pkt.key(), s -> {
                s.setFollowSource(false);
                s.setThreshold(pkt.number());
            });
            case "deadband" -> withSub(brain, pkt.key(), s -> s.setDeadband(pkt.number()));
            case "follow" -> withSub(brain, pkt.key(), s -> s.setFollowSource(pkt.number() > 0.5f));

            case "adopt_all" -> {
                if (brain.getLevel() instanceof ServerLevel level) {
                    PaImport.apply(brain, PaImport.plan(level, brain));
                }
            }
            case "adopt" -> adoptOne(brain, pkt.key());

            case "scan" -> settings.setScanRadius((int) pkt.number());
            case "hearing" -> settings.setHearingRange((int) pkt.number());
            case "audience" -> settings.setAudience(Audience.byName(pkt.value()));
            case "volume" -> settings.setVolume(pkt.number());
            case "pitch" -> settings.setPitch(pkt.number());
            case "bulletin_on" -> settings.setBulletinEnabled(pkt.number() > 0.5f);
            case "bulletin_every" -> settings.setBulletinIntervalTicks((int) pkt.number());
            case "bulletin_facts" -> settings.setBulletinFacts((int) pkt.number());
            case "bulletin_detail" -> settings.setBulletinVerbosity(
                    PaSubscription.verbosityByName(pkt.value()));
            case "mute" -> settings.setMuted(pkt.number() > 0.5f);

            case "silence" -> brain.acknowledge();
            case "test" -> {
                brain.sayNow(previewFor(brain, pkt.key()));
                resend = false;
            }

            case "ask" -> brain.sayOnDemandReport();
            case "refresh" -> { }
            default -> resend = false;
        }

        if (!readOnly) {
            brain.markDirtyAndSync();
        }
        if (resend) {
            send(player, brain);
        }
    }

    private static void subscribe(AnnouncementComputerBE brain, String key) {
        final PaBinding binding = bindingFromKey(key);
        if (binding == null || brain.subscriptions().containsKey(key)
                || !(brain.getLevel() instanceof ServerLevel level)) {
            return;
        }
        final DataChannel channel = PaCatalog.channelOf(level, binding);
        if (channel != null && channel.isAlertEnabled()) {
            brain.addSubscription(PaImport.subscriptionFor(new PaImport.Proposal(binding,
                    channel.getId(), "alert set on the display",
                    com.createstats.alarm.annunciator.AlarmSeverity.NOTICE,
                    channel.getAlertThreshold(), channel.isAlertAbove(),
                    channel.getStore() == null ? MetricType.GENERIC
                            : channel.getStore().getMetricType(),
                    true, false)));
            return;
        }

        final PaSubscription sub = new PaSubscription(binding);
        sub.setFollowSource(false);
        final var sample = PaCatalog.sample(level, binding);
        if (sample != null) {
            sub.setThreshold(suggestThreshold(sample.type(), sample.value()));
            sub.setDeadband(PaImport.deadbandFor(sample.type(), sub.threshold()));
        }
        brain.addSubscription(sub);
    }

    private static void adoptOne(AnnouncementComputerBE brain, String key) {
        if (!(brain.getLevel() instanceof ServerLevel level)) {
            return;
        }
        for (PaImport.Proposal proposal : PaImport.plan(level, brain)) {
            if (proposal.binding().key().equals(key) && !proposal.present()) {
                brain.addSubscription(PaImport.subscriptionFor(proposal));
                return;
            }
        }
        subscribe(brain, key);
    }

    private static void withSub(AnnouncementComputerBE brain, String key,
                                java.util.function.Consumer<PaSubscription> action) {
        final PaSubscription sub = brain.subscriptions().get(key);
        if (sub != null) {
            action.accept(sub);
        }
    }

    public static float suggestThreshold(MetricType type, float current) {
        if (type == MetricType.FILL_LEVEL || type == MetricType.STRESS_PERCENT
                || type == MetricType.EFFICIENCY_SCORE) {
            return Math.max(50.0f, Math.min(95.0f, Math.max(current + 10.0f, 80.0f)));
        }
        return current <= 0.0f ? 1.0f : current * 1.25f;
    }

    public static Utterance previewFor(AnnouncementComputerBE brain, String key) {
        final PaSubscription sub = brain.subscriptions().get(key);
        if (sub == null || !(brain.getLevel() instanceof ServerLevel level)) {
            return Utterance.NOTHING;
        }
        if (sub.binding().kind() == PaBinding.Kind.DEVICE) {
            return new Utterance.Builder().say(Lexicon.CAUTION, Lexicon.ALARM, Lexicon.HIGH).build();
        }
        return Verbalizer.say(brain.readingFor(level, sub), sub.verbosity(), sub.severity());
    }

    public static PaBinding bindingFromKey(String key) {
        try {
            final char kind = key.charAt(0);
            final int at = key.indexOf('@');
            final int colon = key.indexOf(':', at);
            final long packed = Long.parseLong(colon < 0 ? key.substring(at + 1)
                    : key.substring(at + 1, colon));
            final BlockPos pos = BlockPos.of(packed);
            return switch (kind) {
                case 'D' -> PaBinding.device(pos);
                case 'C' -> colon < 0 ? null : PaBinding.channel(pos, key.substring(colon + 1));
                case 'M' -> colon < 0 ? null : PaBinding.metric(pos, key.substring(colon + 1));
                default -> null;
            };
        } catch (RuntimeException e) {
            return null;
        }
    }

    public static List<String> previewClips(PaSnapshot.Row row, PaSnapshot.Sub sub) {
        if (row == null) {
            return List.of(Lexicon.REPORT, Lexicon.NO_FAULTS);
        }
        if (row.has(PaCatalog.FLAG_INHERITED)) {
            return List.of(Lexicon.CAUTION, Lexicon.ALARM, Lexicon.HIGH);
        }
        final Verbosity verbosity = sub == null ? Verbosity.NORMAL
                : PaSubscription.verbosityByName(sub.verbosity());
        final var severity = sub == null ? com.createstats.alarm.annunciator.AlarmSeverity.NOTICE
                : com.createstats.alarm.annunciator.AlarmSeverity.byName(sub.severity());
        final Verbalizer.Reading reading = new Verbalizer.Reading(row.subjectClip(),
                new DeviceId("x", row.idNumber()), row.measureClip(), row.value(), row.type(),
                Verbalizer.Trend.STEADY, true);
        return Verbalizer.say(reading, verbosity, severity).clips();
    }

    public static String disciplineLabel(PaSnapshot.Sub sub) {
        if (sub == null) {
            return "--";
        }
        final Discipline d = PaSubscription.disciplineByName(sub.discipline());
        return d.displayName();
    }

    public static String disciplineTag(PaSnapshot.Sub sub) {
        if (sub == null) {
            return "";
        }
        return switch (PaSubscription.disciplineByName(sub.discipline())) {
            case ON_EVENT -> "CHG";
            case PERIODIC -> "RPT";
            case ON_DEMAND -> "ASK";
        };
    }
}
