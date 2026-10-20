package com.createstats.pa;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.createstats.alarm.annunciator.AlarmSeverity;
import com.createstats.alarm.annunciator.RuleComparator;
import com.createstats.blockentity.AnnouncementComputerBE;
import com.createstats.data.MetricType;
import com.createstats.display.MetricContract;
import com.createstats.pa.PaSubscription.Discipline;
import com.createstats.pa.PaSubscription.Verbosity;
import net.minecraft.server.level.ServerLevel;

// adopt the alarms you already set
public final class PaImport {
    private PaImport() {
    }

    public record Proposal(PaBinding binding, String title, String origin, AlarmSeverity severity,
                           float threshold, boolean above, MetricType type, boolean follow,
                           boolean present) {
    }

    public static List<Proposal> plan(ServerLevel level, AnnouncementComputerBE brain) {
        return plan(brain, PaCatalog.build(level, brain.getBlockPos(),
                brain.settings().scanRadius(), PaReach.circuitPositions(brain)));
    }

    public static List<Proposal> plan(AnnouncementComputerBE brain, List<PaCatalog.Row> catalog) {
        final List<Proposal> out = new ArrayList<>();
        final Map<String, PaSubscription> existing = brain.subscriptions();
        for (PaCatalog.Row row : catalog) {
            if (!row.bindable() || !row.has(PaCatalog.FLAG_ARMED)) {
                continue;
            }
            final PaBinding binding = PaConsoleRequests.bindingFromKey(row.key());
            if (binding == null) {
                continue;
            }
            final boolean inherited = row.has(PaCatalog.FLAG_INHERITED);
            out.add(new Proposal(binding, row.title(),
                    inherited ? "its own rules" : "alert set on the display",
                    inherited ? AlarmSeverity.WARNING : severityFor(row),
                    row.threshold(), row.has(PaCatalog.FLAG_ABOVE), row.type(), !inherited,
                    existing.containsKey(row.key())));
        }
        return out;
    }

    public static int newCount(List<Proposal> plan) {
        int count = 0;
        for (Proposal proposal : plan) {
            if (!proposal.present()) {
                count++;
            }
        }
        return count;
    }

    public static int apply(AnnouncementComputerBE brain, List<Proposal> plan) {
        int added = 0;
        for (Proposal proposal : plan) {
            if (proposal.present() || brain.subscriptions().containsKey(proposal.binding().key())) {
                continue;
            }
            brain.addSubscription(subscriptionFor(proposal));
            added++;
        }
        return added;
    }

    public static PaSubscription subscriptionFor(Proposal proposal) {
        final PaSubscription sub = new PaSubscription(proposal.binding());
        sub.setSeverity(proposal.severity());
        sub.setDiscipline(Discipline.ON_EVENT);
        sub.setVerbosity(Verbosity.NORMAL);
        sub.setComparator(proposal.above() ? RuleComparator.ABOVE : RuleComparator.BELOW);
        sub.setThreshold(proposal.threshold());
        sub.setDeadband(deadbandFor(proposal.type(), proposal.threshold()));
        sub.setFollowSource(proposal.follow());
        return sub;
    }

    public static float deadbandFor(MetricType type, float threshold) {
        final float[] range = MetricContract.fixedRange(type == null ? MetricType.GENERIC : type);
        if (range != null) {
            return Math.abs(range[1] - range[0]) * 0.02f;
        }
        return Math.max(0.01f, Math.abs(threshold) * 0.02f);
    }

    private static AlarmSeverity severityFor(PaCatalog.Row row) {
        return row.has(PaCatalog.FLAG_TRIPPED) ? AlarmSeverity.WARNING : AlarmSeverity.NOTICE;
    }
}
