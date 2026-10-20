package com.createstats.surveyor.client;

import com.createstats.surveyor.RouteTrack;
import com.createstats.surveyor.RouteWords;
import com.createstats.surveyor.core.Pathway;
import com.createstats.surveyor.core.RouteRunner;
import com.createstats.surveyor.core.Stop;
import net.minecraft.client.resources.language.I18n;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class RouteText {
    private RouteText() {
    }

    public static String state(int ordinal) {
        return I18n.get("create_stats.surveyor.state." + RouteRunner.State.byOrdinal(ordinal).key);
    }

    public static String rules(Stop stop) {
        return I18n.get("create_stats.surveyor.stop_prefix") + " " + RouteWords.rules(stop);
    }

    public static List<String> card(RouteTrack t) {
        List<String> out = new ArrayList<>();
        String state = state(t.state());
        out.add(t.name() + "  " + state + (t.reason().isEmpty() ? "" : ": " + t.reason()));
        List<String> second = new ArrayList<>();
        second.add(String.format(Locale.ROOT, "%.1f b/s", Math.abs(t.speed())));
        PathwayClientCache.Entry e = PathwayClientCache.get(t.pathway());
        if (e != null) {
            Pathway p = e.pathway;
            second.add(p.name());
            if (t.nextStop() >= 0 && p.node(t.nextStop()) != null) {
                String eta = t.eta() >= 0 ? " " + I18n.get("create_stats.surveyor.eta", RouteWords.seconds(Math.round(t.eta()))) : "";
                second.add(I18n.get("create_stats.surveyor.next_stop", p.label(t.nextStop())) + eta);
            }
        }
        out.add(String.join("  ", second));
        if (t.waitFraction() >= 0.0f && !t.waitText().isEmpty()) out.add(t.waitText());
        else if (t.cargo() >= 0) out.add(I18n.get("create_stats.surveyor.cargo", t.cargo()));
        if (t.laps() > 0 && t.lastLap() > 0) out.add(I18n.get("create_stats.surveyor.lap", t.laps(), RouteWords.seconds(Math.round(t.lastLap()))));
        return out;
    }
}
