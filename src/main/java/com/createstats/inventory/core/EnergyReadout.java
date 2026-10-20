package com.createstats.inventory.core;

import com.createstats.power.ReserveProjection;

import java.util.Map;
import java.util.function.Supplier;

// conduit charge is not reserve
public final class EnergyReadout {
    private EnergyReadout() {
    }

    public enum Shape {
        BANK,
        CONTROLLER,
        CONNECTOR,
        BOARD,
        METER,
        DEVICE
    }

    public enum Count {
        NONE,
        BLOCKS,
        WIRES
    }

    public enum Clock {
        OWN,

        QUOTED,

        NONE
    }

    public record Outlook(boolean managed, int shortInTicks, int fullInTicks) {
        public static final Outlook UNMANAGED = new Outlook(false, -1, -1);

        public static Outlook managed(int shortInTicks, int fullInTicks) {
            return new Outlook(true, shortInTicks, fullInTicks);
        }

        public static Outlook ofForecast(ReserveProjection.Result forecast) {
            if (forecast == null || forecast.trace().length == 0) {
                return managed(-1, -1);
            }
            final ReserveProjection.Milestone full = forecast.first(ReserveProjection.Kind.FULL);
            return managed(forecast.ticksToShort(), full == null ? -1 : Math.max(1, full.tick()));
        }
    }

    public record Quote(Clock clock, boolean full, int ticks) {
        public static final Quote OWN = new Quote(Clock.OWN, false, 0);
        public static final Quote NONE = new Quote(Clock.NONE, false, 0);
    }

    public record Figures(boolean hasStored, long stored, long capacity, boolean hasFlows, long inPerTick,
                          long outPerTick, Count countKind, long count, Quote quote) {
        public long netPerTick() {
            return inPerTick - outPerTick;
        }

        public double fill() {
            return capacity > 0L ? Math.max(0.0, Math.min(1.0, stored / (double) capacity)) : 0.0;
        }

        Figures with(Quote q) {
            return new Figures(hasStored, stored, capacity, hasFlows, inPerTick, outPerTick, countKind, count, q);
        }
    }

    public record Reserve(boolean present, long stored, long capacity, long inPerTick, long outPerTick) {
        public static final Reserve NONE = new Reserve(false, 0L, 0L, 0L, 0L);
    }

    public static Figures read(Shape shape, Map<String, Float> metrics, Reserve reserve) {
        return read(shape, metrics, reserve, Outlook.UNMANAGED);
    }

    public static Figures read(Shape shape, Map<String, Float> metrics, Reserve reserve, Outlook outlook) {
        final Reserve r = reserve == null ? Reserve.NONE : reserve;
        final Figures f = switch (shape) {
            case CONNECTOR -> new Figures(r.present() && r.capacity() > 0L, r.stored(), r.capacity(), true,
                    r.inPerTick(), r.outPerTick(), has(metrics, "connector_count") ? Count.WIRES : Count.NONE,
                    whole(metrics, "connector_count"), Quote.NONE);
            case BOARD -> new Figures(r.present() && r.capacity() > 0L, r.stored(), r.capacity(),
                    has(metrics, "energy_input_rate") || has(metrics, "energy_output_rate"),
                    whole(metrics, "energy_input_rate"), whole(metrics, "energy_output_rate"), Count.NONE, 0L,
                    Quote.NONE);
            case METER -> new Figures(false, 0L, 0L, has(metrics, "meter_rate") || has(metrics, "energy_input_rate"),
                    first(metrics, "meter_rate", "energy_input_rate"), first(metrics, "meter_rate", "energy_output_rate"),
                    Count.NONE, 0L, Quote.NONE);
            case BANK, CONTROLLER, DEVICE -> stored(shape, metrics);
        };
        return f.hasStored() ? f.with(quote(shape, outlook)) : f;
    }

    public static Quote quote(Shape shape, Outlook outlook) {
        final Outlook o = outlook == null ? Outlook.UNMANAGED : outlook;
        if (!o.managed() && shape != Shape.CONTROLLER && shape != Shape.BOARD) {
            return Quote.OWN;
        }

        if (o.shortInTicks() == 0) {
            return Quote.NONE;
        }
        if (o.shortInTicks() > 0) {
            return new Quote(Clock.QUOTED, false, o.shortInTicks());
        }
        return o.fullInTicks() > 0 ? new Quote(Clock.QUOTED, true, o.fullInTicks()) : Quote.NONE;
    }

    public static FlowClock.Projection clock(Quote quote, Supplier<FlowClock.Projection> own) {
        return switch (quote == null ? Clock.NONE : quote.clock()) {
            case OWN -> own.get();
            case QUOTED -> new FlowClock.Projection(quote.full() ? FlowClock.Kind.FULL : FlowClock.Kind.RUNTIME,
                    quote.ticks() / 20.0);
            case NONE -> FlowClock.Projection.NONE;
        };
    }

    private static Figures stored(Shape shape, Map<String, Float> metrics) {
        final boolean hasStored = has(metrics, "energy_stored");
        final long stored = whole(metrics, "energy_stored");
        final long capacity = has(metrics, "energy_capacity") ? whole(metrics, "energy_capacity") : stored;
        final boolean hasIn = has(metrics, "energy_input_rate");
        final boolean hasOut = has(metrics, "energy_output_rate");
        final boolean hasNet = has(metrics, "energy_net_rate");
        long in = whole(metrics, "energy_input_rate");
        long out = whole(metrics, "energy_output_rate");
        if (hasNet && !(hasIn && hasOut)) {
            final long net = whole(metrics, "energy_net_rate");
            if (!hasIn && !hasOut) {
                in = Math.max(0L, net);
                out = Math.max(0L, -net);
            } else if (!hasIn) {
                in = Math.max(0L, net + out);
            } else {
                out = Math.max(0L, in - net);
            }
        }
        Count kind = Count.NONE;
        long count = 0L;
        if (shape == Shape.BANK) {
            if (has(metrics, "bank_blocks")) {
                kind = Count.BLOCKS;
                count = whole(metrics, "bank_blocks");
            } else if (has(metrics, "accumulator_size")) {
                kind = Count.BLOCKS;
                count = whole(metrics, "accumulator_size");
            }
        }
        return new Figures(hasStored && capacity > 0L, stored, capacity, hasIn || hasOut || hasNet, in, out, kind,
                count, Quote.NONE);
    }

    private static boolean has(Map<String, Float> m, String key) {
        final Float v = m.get(key);
        return v != null && Float.isFinite(v);
    }

    private static long whole(Map<String, Float> m, String key) {
        final Float v = m.get(key);
        return v == null || !Float.isFinite(v) ? 0L : Math.round((double) v);
    }

    private static long first(Map<String, Float> m, String a, String b) {
        return has(m, a) ? whole(m, a) : whole(m, b);
    }
}
