package com.createstats.topology;

import java.util.Locale;

public enum NodeType
{
    SHAFT(5, false),
    COGWHEEL(5, false),
    LARGE_COGWHEEL(5, false),
    GEARBOX(5, false),
    GEARSHIFT(5, false),
    SPEED_CONTROLLER(7, false),
    BELT(5, false),
    CHAIN_DRIVE(5, false),
    MECHANICAL_PRESS(7, true),
    MECHANICAL_MIXER(7, true),
    MECHANICAL_DRILL(7, true),
    MECHANICAL_SAW(7, true),
    DEPLOYER(7, true),
    ENCASED_FAN(7, true),
    MILLSTONE(7, true),
    CRUSHING_WHEEL(7, true),
    WATER_WHEEL(7, true),
    WINDMILL(7, true),
    HAND_CRANK(7, true),
    STEAM_ENGINE(7, true),
    CREATIVE_MOTOR(7, true),
    FLYWHEEL(7, true),
    GAUGE(7, true),
    OTHER(5, true);

    private final int iconSize;
    private final boolean showLabel;

    private NodeType(int iconSize, boolean showLabel) {
        this.iconSize = iconSize;
        this.showLabel = showLabel;
    }

    public int getIconSize() {
        return this.iconSize;
    }

    public boolean showLabel() {
        return this.showLabel;
    }

    public boolean isTransferOrgan() {
        return switch (this) {
            case SHAFT, COGWHEEL, LARGE_COGWHEEL, GEARBOX, GEARSHIFT, SPEED_CONTROLLER, BELT, CHAIN_DRIVE, FLYWHEEL -> true;
            default -> false;
        };
    }

    public boolean isSourceType() {
        return switch (this) {
            case WATER_WHEEL, WINDMILL, HAND_CRANK, STEAM_ENGINE, CREATIVE_MOTOR -> true;
            default -> false;
        };
    }

    public boolean isObserverLike() {
        return this == GAUGE;
    }

    public boolean isMachineLike() {
        return switch (this) {
            case MECHANICAL_PRESS, MECHANICAL_MIXER, MECHANICAL_DRILL, MECHANICAL_SAW, DEPLOYER, ENCASED_FAN, MILLSTONE, CRUSHING_WHEEL -> true;
            default -> false;
        };
    }

    public static NodeType fromClassName(String className) {
        if (className.contains("CreativeMotor")) {
            return NodeType.CREATIVE_MOTOR;
        }
        if (className.contains("PoweredShaft")) {
            return NodeType.STEAM_ENGINE;
        }
        if (className.contains("WaterWheel")) {
            return NodeType.WATER_WHEEL;
        }
        if (className.contains("WindmillBearing")) {
            return NodeType.WINDMILL;
        }
        if (className.contains("HandCrank")) {
            return NodeType.HAND_CRANK;
        }
        if (className.contains("SteamEngine")) {
            return NodeType.STEAM_ENGINE;
        }
        if (className.contains("MechanicalPress")) {
            return NodeType.MECHANICAL_PRESS;
        }
        if (className.contains("MechanicalMixer")) {
            return NodeType.MECHANICAL_MIXER;
        }
        if (className.contains("Drill")) {
            return NodeType.MECHANICAL_DRILL;
        }
        if (className.contains("Saw")) {
            return NodeType.MECHANICAL_SAW;
        }
        if (className.contains("Deployer")) {
            return NodeType.DEPLOYER;
        }
        if (className.contains("EncasedFan")) {
            return NodeType.ENCASED_FAN;
        }
        if (className.contains("Millstone")) {
            return NodeType.MILLSTONE;
        }
        if (className.contains("CrushingWheel")) {
            return NodeType.CRUSHING_WHEEL;
        }
        if (className.contains("Flywheel")) {
            return NodeType.FLYWHEEL;
        }
        if (className.contains("SpeedController")) {
            return NodeType.SPEED_CONTROLLER;
        }
        if (className.contains("Gearshift")) {
            return NodeType.GEARSHIFT;
        }
        if (className.contains("Gearbox")) {
            return NodeType.GEARBOX;
        }
        if (className.contains("LargeCog")) {
            return NodeType.LARGE_COGWHEEL;
        }
        if (className.contains("Cog")) {
            return NodeType.COGWHEEL;
        }
        if (className.contains("Belt")) {
            return NodeType.BELT;
        }
        if (className.contains("ChainDrive")) {
            return NodeType.CHAIN_DRIVE;
        }
        if (className.contains("Gauge") || className.contains("Stressometer") || className.contains("Speedometer")) {
            return NodeType.GAUGE;
        }
        if (className.contains("Shaft")) {
            return NodeType.SHAFT;
        }
        return NodeType.OTHER;
    }

    public static NodeType fromIdentity(String blockName, String blockClassName, String blockEntityClassName) {
        final String identity = ((blockName == null) ? "" : blockName) + " " + ((blockClassName == null) ? "" : blockClassName) + " " + ((blockEntityClassName == null) ? "" : blockEntityClassName);
        final String normalized = identity.toLowerCase(Locale.ROOT);
        if (normalized.contains("creative motor")) {
            return NodeType.CREATIVE_MOTOR;
        }
        if (normalized.contains("water wheel")) {
            return NodeType.WATER_WHEEL;
        }
        if (normalized.contains("windmill bearing") || normalized.contains("windmill")) {
            return NodeType.WINDMILL;
        }
        if (normalized.contains("hand crank")) {
            return NodeType.HAND_CRANK;
        }
        if (normalized.contains("steam engine")) {
            return NodeType.STEAM_ENGINE;
        }
        if (normalized.contains("powered shaft")) {
            return NodeType.STEAM_ENGINE;
        }
        if (normalized.contains("mechanical press")) {
            return NodeType.MECHANICAL_PRESS;
        }
        if (normalized.contains("mechanical mixer")) {
            return NodeType.MECHANICAL_MIXER;
        }
        if (normalized.contains("mechanical drill") || normalized.contains(" drill")) {
            return NodeType.MECHANICAL_DRILL;
        }
        if (normalized.contains("mechanical saw") || normalized.contains(" saw")) {
            return NodeType.MECHANICAL_SAW;
        }
        if (normalized.contains("deployer")) {
            return NodeType.DEPLOYER;
        }
        if (normalized.contains("encased fan")) {
            return NodeType.ENCASED_FAN;
        }
        if (normalized.contains("millstone")) {
            return NodeType.MILLSTONE;
        }
        if (normalized.contains("crushing wheel")) {
            return NodeType.CRUSHING_WHEEL;
        }
        if (normalized.contains("stress gauge") || normalized.contains("speed gauge") || normalized.contains("stressometer") || normalized.contains("speedometer") || normalized.contains(" gauge")) {
            return NodeType.GAUGE;
        }
        if (normalized.contains("rotation speed controller") || normalized.contains("speed controller")) {
            return NodeType.SPEED_CONTROLLER;
        }
        if (normalized.contains("gearshift") || normalized.contains("gear shift")) {
            return NodeType.GEARSHIFT;
        }
        if (normalized.contains("gearbox")) {
            return NodeType.GEARBOX;
        }
        if (normalized.contains("large cogwheel") || normalized.contains("large cog wheel")) {
            return NodeType.LARGE_COGWHEEL;
        }
        if (normalized.contains("cogwheel") || normalized.contains("cog wheel")) {
            return NodeType.COGWHEEL;
        }
        if (normalized.contains("chain drive")) {
            return NodeType.CHAIN_DRIVE;
        }
        if (normalized.contains("belt")) {
            return NodeType.BELT;
        }
        if (normalized.contains("flywheel")) {
            return NodeType.FLYWHEEL;
        }
        if (normalized.contains("shaft")) {
            return NodeType.SHAFT;
        }
        return fromClassName(blockEntityClassName == null ? "" : blockEntityClassName);
    }

    public static String displayNameFromClassName(String className) {
        final String name = className.replace("BlockEntity", "").replace("TileEntity", "").replace("BE", "");
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < name.length(); ++i) {
            final char c = name.charAt(i);
            if (i > 0 && Character.isUpperCase(c) && Character.isLowerCase(name.charAt(i - 1))) {
                sb.append(' ');
            }
            sb.append(c);
        }
        final String result = sb.toString().trim();
        return result.isEmpty() ? "Machine" : result;
    }
}
