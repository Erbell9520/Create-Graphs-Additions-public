package com.createstats.pa;

import java.util.List;

import com.createstats.alarm.annunciator.AlarmSeverity;
import com.createstats.data.MetricType;
import com.createstats.pa.PaSubscription.Verbosity;

public final class Verbalizer {
    private Verbalizer() {
    }

    public enum Trend {
        RISING, FALLING, STEADY;

        public String clip() {
            return switch (this) {
                case RISING -> Lexicon.RISING;
                case FALLING -> Lexicon.FALLING;
                case STEADY -> Lexicon.STEADY;
            };
        }
    }

    public record Reading(String subjectClip, DeviceId id, String measureClip, double value,
                          MetricType type, Trend trend, boolean available) {
    }

    public enum Phase {
        RAISED,

        CLEARED,

        RESTORED,

        ROUTINE
    }

    public static String urgency(AlarmSeverity severity) {
        if (severity == null) {
            return "";
        }
        return switch (severity) {
            case CRITICAL -> Lexicon.ALERT;
            case WARNING -> Lexicon.WARNING;
            case NOTICE -> Lexicon.CAUTION;
            case NORMAL -> "";
        };
    }

    public static Utterance say(Reading reading, Verbosity verbosity, AlarmSeverity severity) {
        return say(reading, verbosity, severity, Phase.ROUTINE);
    }

    public static Utterance say(Reading reading, Verbosity verbosity, AlarmSeverity severity,
                                Phase phase) {
        final boolean goodNews = phase == Phase.CLEARED || phase == Phase.RESTORED;
        final Utterance.Builder b = new Utterance.Builder()
                .severity(goodNews ? AlarmSeverity.NORMAL : severity)
                .dedupe(reading.subjectClip() + "/" + reading.id().number() + "/"
                        + reading.measureClip() + "/" + phase.name());

        if (phase == Phase.CLEARED) {
            b.say(Lexicon.ALL_CLEAR);
        } else if (phase != Phase.RESTORED) {
            b.say(urgency(severity));
        }

        if (phase == Phase.RESTORED) {
            subjectAndId(b, reading);
            b.say(reading.measureClip());
            return b.say(Lexicon.RESTORED).build();
        }

        if (!reading.available()) {
            subjectAndId(b, reading);
            return b.say(Lexicon.OFFLINE).build();
        }

        if (verbosity != Verbosity.TERSE || phase == Phase.CLEARED) {
            subjectAndId(b, reading);
            b.say(reading.measureClip());
        }

        final double spoken = PaVocabulary.convertForSpeech(reading.type(), reading.value());
        b.quantity(spoken, PaVocabulary.unit(reading.type()));

        if (phase == Phase.CLEARED) {
            return b.say(Lexicon.NOMINAL).build();
        }

        if (verbosity == Verbosity.FULL) {
            if (reading.trend() != Trend.STEADY) {
                b.say(reading.trend().clip());
            }
            final String state = stateWord(reading);
            if (!state.isEmpty()) {
                b.say(state);
            }
        }
        return b.build();
    }

    private static void subjectAndId(Utterance.Builder b, Reading reading) {
        b.say(reading.subjectClip());
        if (reading.id().isPresent()) {
            b.say(Lexicon.UNIT);
            b.say(Lexicon.number(reading.id().number()));
        }
    }

    private static String stateWord(Reading reading) {
        final MetricType type = reading.type();
        if (type != MetricType.FILL_LEVEL && type != MetricType.STRESS_PERCENT
                && type != MetricType.EFFICIENCY_SCORE) {
            return "";
        }
        final double v = reading.value();
        if (v <= 2.0) {
            return Lexicon.EMPTY;
        }
        if (v >= 99.0) {
            return Lexicon.FULL;
        }
        if (v >= 90.0) {
            return Lexicon.HIGH;
        }
        if (v <= 10.0) {
            return Lexicon.LOW;
        }
        return "";
    }

    public static Utterance bulletin(List<Reading> readings, Verbosity verbosity, int maxFacts) {
        final Utterance.Builder b = new Utterance.Builder().dedupe("bulletin");
        b.say(Lexicon.REPORT);

        if (readings.isEmpty()) {
            return b.say(Lexicon.NO_FAULTS).build();
        }

        AlarmSeverity worst = AlarmSeverity.NORMAL;
        final int spoken = Math.min(Math.max(1, maxFacts), readings.size());
        for (int i = 0; i < spoken; i++) {
            final Reading reading = readings.get(i);
            final Utterance one = say(reading, verbosity, AlarmSeverity.NORMAL);
            b.say(one.clips());
        }
        for (Reading reading : readings) {
            worst = AlarmSeverity.worst(worst, reading.available()
                    ? AlarmSeverity.NORMAL : AlarmSeverity.NOTICE);
        }

        final int rest = readings.size() - spoken;
        if (rest > 0) {
            b.say(Lexicon.AND);
            b.say(Lexicon.number(rest));
            b.say(Lexicon.OTHERS, Lexicon.OUTSTANDING);
        }
        return b.severity(worst).say(Lexicon.END_OF_REPORT).build();
    }
}
