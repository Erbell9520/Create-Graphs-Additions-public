package com.createstats.client.hud;

import com.createstats.flight.FlightMode;
import com.createstats.flight.SensorRequirement;
import com.createstats.flight.network.FccGuiSnapshot;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/** [1] */
public final class HudWarnings {

    private HudWarnings() {}

    public enum Severity {
        INFO(0xFF6EA8FF),
        CAUTION(0xFFFFC24B),
        WARNING(0xFFFF8C3B),
        ALARM(0xFFFF4D4D);

        public final int color;

        Severity(int color) {
            this.color = color;
        }
    }

    public record Entry(Severity severity, String text) {}

    /** Collect craft-state warnings from the snapshot, most severe first. Never returns null. */
    public static List<Entry> collect(@Nullable FccGuiSnapshot snapshot) {
        List<Entry> list = new ArrayList<>();
        if (snapshot == null) {
            return list;
        }
        int flags = snapshot.readinessFlags();
        if ((flags & FccGuiSnapshot.READY_NO_BINDINGS) != 0) list.add(new Entry(Severity.WARNING, "NO BINDINGS"));
        if ((flags & FccGuiSnapshot.READY_BMATRIX_INVALID) != 0) list.add(new Entry(Severity.WARNING, "CTRL MATRIX"));
        if ((flags & FccGuiSnapshot.READY_ZERO_YAW_AUTHORITY) != 0) list.add(new Entry(Severity.CAUTION, "NO YAW AUTH"));
        if ((flags & FccGuiSnapshot.READY_UNKNOWN_SPIN) != 0) list.add(new Entry(Severity.CAUTION, "SPIN UNKNOWN"));

        var avail = snapshot.availableSensors();
        if (!avail.contains(SensorRequirement.GIMBAL)) list.add(new Entry(Severity.WARNING, "NO GIMBAL"));
        if (!avail.contains(SensorRequirement.ALTITUDE)) list.add(new Entry(Severity.CAUTION, "NO ALT SENS"));
        if (!avail.contains(SensorRequirement.VELOCITY)) list.add(new Entry(Severity.CAUTION, "NO VEL SENS"));

        String warning = snapshot.lastWarning();
        if (warning != null && !warning.isBlank()) {
            list.add(new Entry(Severity.WARNING, trim(warning, 24)));
        }

        // [2]
        if (!snapshot.fePowered()) {
            list.add(new Entry(Severity.ALARM, "NO POWER"));
        } else if (snapshot.feBufferPct() < com.createstats.power.FeConsumer.LOW_POWER_PCT) {
            list.add(new Entry(Severity.CAUTION, "LOW POWER " + Math.round(snapshot.feBufferPct()) + "%"));
        }

        if (snapshot.mode() == FlightMode.OFF) {
            list.add(new Entry(Severity.INFO, "DISARMED"));
        }

        // Most severe first; stable for equal severity so ordering stays deterministic frame to frame.
        list.sort((a, b) -> b.severity().ordinal() - a.severity().ordinal());
        return list;
    }

    public static Severity topSeverity(List<Entry> entries) {
        Severity top = Severity.INFO;
        for (Entry e : entries) {
            if (e.severity().ordinal() > top.ordinal()) top = e.severity();
        }
        return top;
    }

    /** [3] */
    public static int render(GuiGraphics g, Font font, int x, int y, List<Entry> entries, int maxRows) {
        int shown = Math.min(entries.size(), Math.max(0, maxRows));
        int rowY = y;
        for (int i = 0; i < shown; i++) {
            Entry e = entries.get(i);
            g.fill(x, rowY + 1, x + 5, rowY + 6, e.severity().color);
            g.drawString(font, e.text(), x + 9, rowY, e.severity().color, true);
            rowY += 10;
        }
        if (entries.size() > shown) {
            g.drawString(font, "+" + (entries.size() - shown) + " more", x + 9, rowY, 0xFFB0B8C0, true);
            rowY += 10;
        }
        return rowY;
    }

    private static String trim(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}
