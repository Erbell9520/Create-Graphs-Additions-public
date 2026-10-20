package com.createstats.power;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntUnaryOperator;

public final class SupplyForecast {
    private SupplyForecast() {
    }

    public static final int SAMPLE_TICKS = 300;

    public static final int FOLD_TICKS = 1_200;

    public enum Kind {
        SUN,

        FUEL,

        STEADY
    }

    public enum What {
        SUN_UP,

        SUN_DOWN,

        FUEL_OUT
    }

    public record Change(int tick, What what, int fePerTick) {
    }

    public record Result(int[] total, List<Change> changes) {
        public static final Result NONE = new Result(new int[0], List.of());

        public Result {
            total = total == null ? new int[0] : total.clone();
            changes = changes == null ? List.of() : List.copyOf(changes);
        }

        public boolean isPresent() {
            return total.length > 0;
        }

        public int at(int ticks) {
            if (total.length == 0) {
                return 0;
            }
            final int t = Math.max(0, ticks);
            final int i = t / SAMPLE_TICKS;
            if (i >= total.length - 1) {
                return total[total.length - 1];
            }
            final int into = t - i * SAMPLE_TICKS;
            return (int) (total[i] + (total[i + 1] - (long) total[i]) * into / SAMPLE_TICKS);
        }

        public Change first(What what) {
            for (Change c : changes) {
                if (c.what() == what) {
                    return c;
                }
            }
            return null;
        }
    }

    public static int deliverable(int flow, int ceiling, int sourcesNow, int sourcesThen) {
        final int carried = Math.max(0, flow);
        final int now = Math.max(0, sourcesNow);
        final int then = Math.max(0, sourcesThen);
        if (then <= now) {
            return FeForecastRelay.through(carried, now, then);
        }
        final int room = Math.max(0, ceiling - carried);
        return carried + Math.min(room, then - now);
    }

    public static final class Builder {
        private final int samples;
        private final int[] flow;
        private final int[] ceiling;
        private final int[][] byPort;
        private final int[] sun;
        private final int[] fuel;

        public Builder(int ports, int horizon) {
            this.samples = Math.max(1, Math.max(0, horizon) / SAMPLE_TICKS + 1);
            this.flow = new int[Math.max(0, ports)];
            this.ceiling = new int[flow.length];
            this.byPort = new int[flow.length][samples];
            this.sun = new int[samples];
            this.fuel = new int[samples];
        }

        public Builder port(int port, int carried, int rating) {
            if (port >= 0 && port < flow.length) {
                flow[port] = Math.max(0, carried);
                ceiling[port] = Math.max(0, rating);
            }
            return this;
        }

        public Builder add(int port, Kind kind, IntUnaryOperator fePerTickIn) {
            if (port < 0 || port >= flow.length || fePerTickIn == null) {
                return this;
            }
            final int[] row = byPort[port];
            for (int k = 0; k < samples; k++) {
                final int v = Math.max(0, fePerTickIn.applyAsInt(k * SAMPLE_TICKS));
                row[k] = FeRates.plus(row[k], v);
                if (kind == Kind.SUN) {
                    sun[k] = FeRates.plus(sun[k], v);
                } else if (kind == Kind.FUEL) {
                    fuel[k] = FeRates.plus(fuel[k], v);
                }
            }
            return this;
        }

        public Result build() {
            final int[] total = new int[samples];
            for (int k = 0; k < samples; k++) {
                int sum = 0;
                for (int p = 0; p < flow.length; p++) {
                    sum = FeRates.plus(sum, deliverable(flow[p], ceiling[p], byPort[p][0], byPort[p][k]));
                }
                total[k] = sum;
            }
            return new Result(total, changes());
        }

        private List<Change> changes() {
            final List<Change> out = new ArrayList<>();
            Change lastFuel = null;
            for (int k = 1; k < samples; k++) {
                final int tick = k * SAMPLE_TICKS;
                if (sun[k - 1] <= 0 && sun[k] > 0) {
                    int peak = 0;
                    for (int j = k; j < samples && sun[j] > 0; j++) {
                        peak = Math.max(peak, sun[j]);
                    }
                    out.add(new Change(tick, What.SUN_UP, peak));
                } else if (sun[k - 1] > 0 && sun[k] <= 0) {
                    int peak = 0;
                    for (int j = k - 1; j >= 0 && sun[j] > 0; j--) {
                        peak = Math.max(peak, sun[j]);
                    }
                    out.add(new Change(tick, What.SUN_DOWN, peak));
                }
                final int lost = fuel[k - 1] - fuel[k];
                if (lost > 0) {
                    if (lastFuel != null && tick - lastFuel.tick() < FOLD_TICKS) {
                        out.remove(lastFuel);
                        lastFuel = new Change(lastFuel.tick(), What.FUEL_OUT, FeRates.plus(lastFuel.fePerTick(), lost));
                    } else {
                        lastFuel = new Change(tick, What.FUEL_OUT, lost);
                    }
                    out.add(lastFuel);
                }
            }
            out.sort(java.util.Comparator.comparingInt(Change::tick));
            return out;
        }
    }
}
