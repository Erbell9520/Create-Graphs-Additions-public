package com.createstats.power;

import com.createstats.ModConfiguration;

public final class FeProfiles {
    public static final String KEY_FCC = "fcc";
    public static final String KEY_VCC_MK2 = "vccMk2";
    public static final String KEY_PATHFINDER_COMPUTER = "pathfinderComputer";
    public static final String KEY_PATH_SCANNER = "pathScanner";
    public static final String KEY_BATTLE_SCREEN = "battleScreen";
    public static final String KEY_HOLOGRAPHIC_PROJECTOR = "holographicProjector";
    public static final String KEY_FACTORY_LOGIC = "factoryLogicController";
    public static final String KEY_STAGE_LIGHT = "stageLight";
    public static final String KEY_STATUS_TOWER_LIGHT = "statusTowerLight";
    public static final String KEY_STATS_DISPLAY_PANEL = "statsDisplayPanel";
    public static final String KEY_STATS_ALARM = "statsAlarm";
    public static final String KEY_DISPATCH_BOARD = "dispatchBoard";
    public static final String KEY_PRODUCTION_DETECTOR = "productionDetector";
    public static final String KEY_WALL_OUTLET = "wallOutlet";
    public static final String KEY_CINE_CAMERA = "cineCamera";

    public static final String KEY_TOPOLOGY_SCANNER_FIELD = "topologyScannerField";
    public static final String KEY_TOPOLOGY_SCANNER_ADVANCED = "topologyScannerAdvanced";
    public static final String KEY_TOPOLOGY_SCANNER_INDUSTRIAL = "topologyScannerIndustrial";

    private FeProfiles() {
    }

    public static boolean creativeEnergy() {
        try {
            return ModConfiguration.COMMON.creativeEnergy.get();
        } catch (RuntimeException ignored) {
            return true;
        }
    }

    public static boolean powerRequired() {
        return !creativeEnergy();
    }

    public static boolean showLoginReminder() {
        try {
            return ModConfiguration.COMMON.feShowLoginReminder.get();
        } catch (RuntimeException ignored) {
            return true;
        }
    }

    public static int rebootThresholdPct() {
        try {
            return ModConfiguration.COMMON.feRebootThresholdPct.get();
        } catch (RuntimeException ignored) {
            return 25;
        }
    }

    public static FeConsumer.Settings settings(String key) {
        return new FeConsumer.Settings() {
            @Override
            public boolean enabled() {
                return powerRequired();
            }

            @Override
            public int capacity() {
                final ModConfiguration.FePowerValues values = values(key);
                return values != null ? safeGet(values.bufferCapacity, 100000) : 100000;
            }

            @Override
            public int idleDrain() {
                final ModConfiguration.FePowerValues values = values(key);
                return values != null ? safeGet(values.idleDrain, 0) : 0;
            }

            @Override
            public int activeDrain() {
                final ModConfiguration.FePowerValues values = values(key);
                return values != null ? safeGet(values.activeDrain, 0) : 0;
            }

            @Override
            public int burstCost() {
                final ModConfiguration.FePowerValues values = values(key);
                return values != null ? safeGet(values.burstCost, 0) : 0;
            }
        };
    }

    // capacity scales with blocks, drain doesn't
    public static FeConsumer.Settings pooled(String key, java.util.function.IntSupplier unitCount) {
        final FeConsumer.Settings base = settings(key);
        return new FeConsumer.Settings() {
            @Override
            public boolean enabled() {
                return base.enabled();
            }

            @Override
            public int capacity() {
                return saturate((long) base.capacity() * Math.max(1, unitCount.getAsInt()));
            }

            @Override
            public int idleDrain() {
                return base.idleDrain();
            }

            @Override
            public int activeDrain() {
                return base.activeDrain();
            }

            @Override
            public int burstCost() {
                return base.burstCost();
            }
        };
    }

    private static int saturate(long value) {
        return (int) Math.max(0L, Math.min(Integer.MAX_VALUE, value));
    }

    private static ModConfiguration.FePowerValues values(String key) {
        try {
            return ModConfiguration.COMMON.fePowerProfiles.get(key);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static int safeGet(net.neoforged.neoforge.common.ModConfigSpec.IntValue value, int fallback) {
        try {
            return value.get();
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }
}
