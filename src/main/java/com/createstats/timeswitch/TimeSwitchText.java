package com.createstats.timeswitch;

import com.createstats.timeswitch.TimeSwitchProgram.Mode;
import com.createstats.timeswitch.TimeSwitchProgram.Preset;
import net.minecraft.network.chat.Component;

import java.util.Locale;

public final class TimeSwitchText {
    private TimeSwitchText() {
    }

    public static Component program(int pins) {
        return program(pins, 3);
    }

    // twelve single hours as ranges ran off the screen
    public static Component program(int pins, int maxRuns) {
        if ((pins & TimeSwitchProgram.ALL) == TimeSwitchProgram.NONE) {
            return Component.translatable("create_stats.time_switch.program.none");
        }
        if ((pins & TimeSwitchProgram.ALL) == TimeSwitchProgram.ALL) {
            return Component.translatable("create_stats.time_switch.program.all");
        }
        final int runs = TimeSwitchProgram.ranges(pins).size();
        if (runs > maxRuns) {
            return Component.translatable("create_stats.time_switch.program.many", TimeSwitchProgram.count(pins), runs);
        }
        return Component.translatable("create_stats.time_switch.program", ranges(pins));
    }

    public static String ranges(int pins) {
        return TimeSwitchProgram.describe(pins,
                Component.translatable("create_stats.time_switch.range_to").getString(),
                Component.translatable("create_stats.time_switch.range_joiner").getString());
    }

    public static Component why(boolean closed, int pins, Mode mode, long dayTime) {
        return switch (mode) {
            case ON -> Component.translatable("create_stats.time_switch.held_on");
            case OFF -> Component.translatable("create_stats.time_switch.held_off");
            case AUTO -> {
                final String pin = TimeSwitchProgram.clock(TimeSwitchProgram.hourOf(dayTime));
                yield closed ? Component.translatable("create_stats.time_switch.pin_in", pin)
                        : Component.translatable("create_stats.time_switch.pin_out", pin);
            }
        };
    }

    public static Component next(boolean closed, int pins, Mode mode, long dayTime) {
        final int hour = TimeSwitchProgram.nextChange(dayTime, pins, mode);
        if (hour < 0) {
            return null;
        }
        return Component.translatable(closed ? "create_stats.time_switch.opens_at" : "create_stats.time_switch.closes_at",
                TimeSwitchProgram.clock(hour));
    }

    public static String now(long dayTime) {
        final long tick = TimeSwitchProgram.tickOfDay(dayTime);
        final int minute = (int) ((tick % TimeSwitchProgram.HOUR_TICKS) * 60L / TimeSwitchProgram.HOUR_TICKS);
        return String.format(Locale.ROOT, "%02d:%02d", TimeSwitchProgram.hourOf(dayTime), minute);
    }

    public static Component pin(int hour, boolean in) {
        return Component.translatable(in ? "create_stats.time_switch.hover.pin_on" : "create_stats.time_switch.hover.pin_off",
                TimeSwitchProgram.clock(hour), TimeSwitchProgram.clock((hour + 1) % TimeSwitchProgram.HOURS));
    }

    public static Component detent(Mode mode, boolean current) {
        final Component name = switch (mode) {
            case AUTO -> Component.translatable("create_stats.time_switch.hover.mode_auto");
            case ON -> Component.translatable("create_stats.time_switch.hover.mode_on");
            case OFF -> Component.translatable("create_stats.time_switch.hover.mode_off");
        };
        return current ? Component.translatable("create_stats.time_switch.hover.mode_set", name) : name;
    }

    public static Component mode(Mode mode) {
        return switch (mode) {
            case AUTO -> Component.translatable("create_stats.time_switch.mode.auto");
            case ON -> Component.translatable("create_stats.time_switch.mode.on");
            case OFF -> Component.translatable("create_stats.time_switch.mode.off");
        };
    }

    public static Component preset(Preset preset) {
        return switch (preset) {
            case DUSK_TO_DAWN -> Component.translatable("create_stats.time_switch.preset.dusk_to_dawn");
            case DAYTIME -> Component.translatable("create_stats.time_switch.preset.daytime");
            case CLEAR -> Component.translatable("create_stats.time_switch.preset.clear");
            case INVERT -> Component.translatable("create_stats.time_switch.preset.invert");
        };
    }

    public static Component status(boolean closed, int pins, Mode mode, long dayTime) {
        final Component next = next(closed, pins, mode, dayTime);
        return Component.translatable(next == null ? "create_stats.time_switch.status"
                        : "create_stats.time_switch.status_next",
                mode(mode), program(pins), next == null ? Component.empty() : next);
    }
}
