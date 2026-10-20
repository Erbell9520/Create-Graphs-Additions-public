package com.createstats.power;

import java.util.ArrayList;
import java.util.List;

public final class BatteryControllerPlan {
    private BatteryControllerPlan() {
    }

    public static final int RECOVER_MARGIN_PCT = 5;

    public enum Reason {
        SERVED("Served"),

        PARTIAL("Partial"),

        OFF("Breaker off"),

        FLOOR("Below floor"),

        RATIONED("Rationed"),

        NO_SURPLUS("No surplus"),

        IDLE("Idle"),

        SHED("Shed"),

        RECHARGE("Recharging reserve"),

        UNWIRED("Not wired");

        private final String label;

        Reason(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }

        public boolean isWithheld() {
            return this == FLOOR || this == SHED || this == NO_SURPLUS || this == RECHARGE;
        }
    }

    public record Output(boolean wired, boolean enabled, BatteryOutputMode mode, int demand, int ceiling,
                         int limit, int floorPct, FePriority priority, boolean wasHeld,
                         boolean managed, int criticalDemand) {
        public Output(boolean wired, boolean enabled, BatteryOutputMode mode, int demand, int ceiling,
                      int limit, int floorPct, FePriority priority, boolean wasHeld) {
            this(wired, enabled, mode, demand, ceiling, limit, floorPct, priority, wasHeld, false, 0);
        }

        public Output {
            demand = Math.max(0, demand);
            ceiling = Math.max(0, ceiling);
            limit = Math.max(0, limit);
            floorPct = Math.max(0, Math.min(100, floorPct));
            criticalDemand = Math.max(0, criticalDemand);
            mode = mode == null ? BatteryOutputMode.DIRECT : mode;
            priority = priority == null ? FePriority.NORMAL : priority;
        }
    }

    public record Grant(int granted, Reason reason, boolean held) {
    }

    public record Plan(List<Grant> grants, int totalGranted, int fromGeneration, int fromReserve) {
    }

    public static Plan plan(List<Output> outputs, int generation, int spendable, int reservePct) {
        return plan(outputs, generation, spendable, reservePct, 0);
    }

    public static Plan plan(List<Output> outputs, int generation, int spendable, int reservePct,
                            int chargeClaim) {
        final int count = outputs == null ? 0 : outputs.size();
        final List<Grant> grants = new ArrayList<>(count);
        if (count == 0) {
            return new Plan(grants, 0, 0, 0);
        }

        final int[] want = new int[count];
        final Reason[] reason = new Reason[count];
        final boolean[] held = new boolean[count];
        for (int i = 0; i < count; i++) {
            final Output out = outputs.get(i);
            if (!out.wired()) {
                reason[i] = Reason.UNWIRED;
                continue;
            }
            if (!out.enabled()) {
                reason[i] = Reason.OFF;
                continue;
            }

            final int gate = out.wasHeld() ? out.floorPct() + RECOVER_MARGIN_PCT : out.floorPct();
            if (out.floorPct() > 0 && reservePct < gate) {
                if (out.managed()) {
                    if (out.criticalDemand() <= 0) {
                        reason[i] = Reason.FLOOR;
                        held[i] = true;
                        continue;
                    }

                    int rationed = Math.min(out.criticalDemand(), out.ceiling());
                    if (out.mode() == BatteryOutputMode.LIMIT) {
                        rationed = Math.min(rationed, out.limit());
                    }
                    if (rationed <= 0) {
                        reason[i] = Reason.FLOOR;
                        held[i] = true;
                        continue;
                    }
                    want[i] = rationed;
                    held[i] = true;
                    continue;
                }
                reason[i] = Reason.FLOOR;
                held[i] = true;
                continue;
            }
            int ask = out.demand();
            if (out.mode() == BatteryOutputMode.LIMIT) {
                ask = Math.min(ask, out.limit());
            }
            ask = Math.min(ask, out.ceiling());
            if (ask <= 0) {
                reason[i] = out.demand() <= 0 ? Reason.IDLE
                        : out.ceiling() <= 0 ? Reason.UNWIRED : Reason.OFF;
                continue;
            }
            want[i] = ask;
        }

        int pool = Math.max(0, spendable);
        int surplusPool = Math.max(0, Math.min(generation - Math.max(0, chargeClaim), pool));
        final int[] given = new int[count];

        for (FePriority tier : FePriority.values()) {
            final List<Integer> members = new ArrayList<>();
            final int[] ask = new int[count];
            int tierWant = 0;
            for (int i = 0; i < count; i++) {
                final int portion = portionAt(tier, outputs.get(i), want[i], given[i]);
                if (portion > 0) {
                    members.add(i);
                    ask[i] = portion;
                    tierWant = FeRates.plus(tierWant, portion);
                }
            }
            if (members.isEmpty()) {
                continue;
            }

            if (tierWant <= poolFor(members, outputs, pool, surplusPool)) {
                for (int index : members) {
                    final int take = ask[index];
                    given[index] += take;
                    pool -= take;
                    if (outputs.get(index).mode() == BatteryOutputMode.SURPLUS) {
                        surplusPool -= take;
                    } else {
                        surplusPool = Math.max(0, Math.min(surplusPool - take, pool));
                    }
                }
                continue;
            }

            final int[] got = new int[count];
            final int share = Math.max(1, pool / members.size());
            for (int index : members) {
                final int budget = budgetFor(outputs.get(index), pool, surplusPool);
                final int take = Math.min(Math.min(share, ask[index]), budget);
                if (take <= 0) {
                    continue;
                }
                got[index] += take;
                given[index] += take;
                pool -= take;
                if (outputs.get(index).mode() == BatteryOutputMode.SURPLUS) {
                    surplusPool -= take;
                } else {
                    surplusPool = Math.max(0, Math.min(surplusPool - take, pool));
                }
            }
            for (int index : members) {
                if (pool <= 0) {
                    break;
                }
                final int budget = budgetFor(outputs.get(index), pool, surplusPool);
                final int take = Math.min(ask[index] - got[index], budget);
                if (take <= 0) {
                    continue;
                }
                got[index] += take;
                given[index] += take;
                pool -= take;
                if (outputs.get(index).mode() == BatteryOutputMode.SURPLUS) {
                    surplusPool -= take;
                } else {
                    surplusPool = Math.max(0, Math.min(surplusPool - take, pool));
                }
            }
        }

        final boolean[] blamedOnRecharge = new boolean[count];
        if (chargeClaim > 0) {
            int unattributed = chargeClaim;
            for (FePriority tier : FePriority.values()) {
                for (int i = 0; i < count && unattributed > 0; i++) {
                    final int missing = want[i] - given[i];
                    if (missing <= 0 || reason[i] != null || outputs.get(i).priority() != tier) {
                        continue;
                    }
                    blamedOnRecharge[i] = true;
                    unattributed -= missing;
                }
                if (unattributed <= 0) {
                    break;
                }
            }
        }

        int total = 0;
        for (int i = 0; i < count; i++) {
            total += given[i];
            if (reason[i] == null) {
                if (held[i] && given[i] > 0) {
                    reason[i] = Reason.RATIONED;
                } else if (given[i] >= want[i] && want[i] > 0) {
                    reason[i] = Reason.SERVED;
                } else if (blamedOnRecharge[i]) {
                    reason[i] = Reason.RECHARGE;
                } else if (given[i] > 0) {
                    reason[i] = Reason.PARTIAL;
                } else if (outputs.get(i).mode() == BatteryOutputMode.SURPLUS) {
                    reason[i] = Reason.NO_SURPLUS;
                } else {
                    reason[i] = Reason.SHED;
                }
            }
            grants.add(new Grant(given[i], reason[i], held[i]));
        }

        final int fromGeneration = Math.min(total, Math.max(0, generation));
        return new Plan(grants, total, fromGeneration, total - fromGeneration);
    }

    private static int budgetFor(Output out, int pool, int surplusPool) {
        return out.mode() == BatteryOutputMode.SURPLUS ? Math.max(0, Math.min(pool, surplusPool))
                : Math.max(0, pool);
    }

    private static int poolFor(List<Integer> members, List<Output> outputs, int pool, int surplusPool) {
        boolean surplus = false;
        boolean other = false;
        for (int index : members) {
            if (outputs.get(index).mode() == BatteryOutputMode.SURPLUS) {
                surplus = true;
            } else {
                other = true;
            }
        }
        if (!surplus) {
            return pool;
        }

        return other ? -1 : Math.min(pool, surplusPool);
    }

    private static int portionAt(FePriority tier, Output out, int want, int given) {
        if (want <= 0) {
            return 0;
        }
        if (tier == FePriority.CRITICAL) {
            if (out.priority() == FePriority.CRITICAL) {
                return want;
            }
            return out.managed() ? Math.min(out.criticalDemand(), want) : 0;
        }
        return out.priority() == tier ? Math.max(0, want - given) : 0;
    }

    static int askOf(Output out) {
        int ask = out.demand();
        if (out.mode() == BatteryOutputMode.LIMIT) {
            ask = Math.min(ask, out.limit());
        }
        return Math.min(ask, out.ceiling());
    }

    public static int criticalWant(List<Output> outputs) {
        int want = 0;
        if (outputs == null) {
            return 0;
        }
        for (Output out : outputs) {
            if (!out.wired() || !out.enabled()) {
                continue;
            }
            final int ask = askOf(out);
            if (out.managed() && out.criticalDemand() > 0) {
                want = FeRates.plus(want, Math.min(out.criticalDemand(), ask));
            } else if (out.priority() == FePriority.CRITICAL) {
                want = FeRates.plus(want, ask);
            }
        }
        return want;
    }

    public static int wiredCeiling(List<Output> outputs) {
        int total = 0;
        if (outputs == null) {
            return 0;
        }
        for (Output out : outputs) {
            if (!out.wired() || !out.enabled()) {
                continue;
            }
            int cap = out.ceiling();
            if (out.mode() == BatteryOutputMode.LIMIT) {
                cap = Math.min(cap, out.limit());
            }
            total = FeRates.plus(total, cap);
        }
        return total;
    }

    public static int boardCap(Output out, Grant grant, int generationFree, int otherLoads) {
        if (out == null || grant == null || !out.wired() || !out.enabled()) {
            return 0;
        }
        if (grant.granted() <= 0 && grant.reason().isWithheld()) {
            return 0;
        }
        int cap = out.ceiling();
        if (out.mode() == BatteryOutputMode.LIMIT) {
            cap = Math.min(cap, out.limit());
        }
        if (grant.reason() == Reason.RATIONED) {
            cap = Math.min(cap, out.criticalDemand());
        }
        if (out.mode() == BatteryOutputMode.SURPLUS) {
            cap = Math.min(cap, Math.max(0, generationFree));
        }
        return Math.max(0, cap - Math.max(0, otherLoads));
    }
}
