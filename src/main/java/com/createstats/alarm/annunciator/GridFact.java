package com.createstats.alarm.annunciator;

import com.createstats.data.MetricType;
import com.createstats.power.FeGridSolver;

// the wired klaxon reads the circuit it's spliced into, free, any distance
public enum GridFact {
    BANK_CHARGE("bank_charge", "Bank Charge", MetricType.FILL_LEVEL) {
        @Override
        public float read(FeGridSolver.Result result) {
            return percent(result.reserve(), result.reserveCapacity());
        }
    },

    BANK_STORED("bank_stored", "Bank Stored", MetricType.ENERGY_FE) {
        @Override
        public float read(FeGridSolver.Result result) {
            return result.reserve();
        }
    },

    BANK_CAPACITY("bank_capacity", "Bank Capacity", MetricType.ENERGY_FE) {
        @Override
        public float read(FeGridSolver.Result result) {
            return result.reserveCapacity();
        }
    },

    BANK_FLOW("bank_flow", "Bank Flow", MetricType.ENERGY_FE_PER_TICK) {
        @Override
        public float read(FeGridSolver.Result result) {
            return result.reserveNet();
        }
    },

    ENDURANCE("endurance", "Endurance", MetricType.ENDURANCE_TICKS) {
        @Override
        public float read(FeGridSolver.Result result) {
            final int ticks = result.enduranceTicks();
            return ticks == Integer.MAX_VALUE ? 0.0f : ticks / 20.0f;
        }
    },

    DEMAND("demand", "Demand", MetricType.ENERGY_FE_PER_TICK) {
        @Override
        public float read(FeGridSolver.Result result) {
            return result.demand();
        }
    },

    DELIVERED("delivered", "Delivered", MetricType.ENERGY_FE_PER_TICK) {
        @Override
        public float read(FeGridSolver.Result result) {
            return result.delivered();
        }
    },

    SHORTFALL("shortfall", "Shortfall", MetricType.ENERGY_FE_PER_TICK) {
        @Override
        public float read(FeGridSolver.Result result) {
            return result.shortfall();
        }
    },

    SATISFACTION("satisfaction", "Satisfaction", MetricType.FILL_LEVEL) {
        @Override
        public float read(FeGridSolver.Result result) {
            return result.demand() <= 0 ? 100.0f : percent(result.delivered(), result.demand());
        }
    },

    SHED("shed", "Load Shed", MetricType.ENERGY_FE_PER_TICK) {
        @Override
        public float read(FeGridSolver.Result result) {
            return result.shed();
        }
    },

    GENERATED("generated", "Generated", MetricType.ENERGY_FE_PER_TICK) {
        @Override
        public float read(FeGridSolver.Result result) {
            return result.generated();
        }
    },

    WIRE_CHARGE("wire_charge", "Wire Buffer", MetricType.FILL_LEVEL) {
        @Override
        public float read(FeGridSolver.Result result) {
            return percent(result.stored(), result.capacity());
        }
    };

    private final String serializedName;
    private final String displayName;
    private final MetricType metricType;

    GridFact(String serializedName, String displayName, MetricType metricType) {
        this.serializedName = serializedName;
        this.displayName = displayName;
        this.metricType = metricType;
    }

    public abstract float read(FeGridSolver.Result result);

    public String getSerializedName() {
        return this.serializedName;
    }

    public String displayName() {
        return this.displayName;
    }

    public MetricType metricType() {
        return this.metricType;
    }

    private static float percent(int part, int whole) {
        return whole <= 0 ? 0.0f : Math.max(0.0f, Math.min(100.0f, part * 100.0f / whole));
    }

    public GridFact next() {
        final GridFact[] values = values();
        return values[(this.ordinal() + 1) % values.length];
    }

    public static GridFact byName(String name) {
        for (GridFact fact : values()) {
            if (fact.serializedName.equalsIgnoreCase(name) || fact.name().equalsIgnoreCase(name)) {
                return fact;
            }
        }
        return BANK_CHARGE;
    }
}
