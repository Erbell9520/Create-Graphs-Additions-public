package com.createstats.battle.core;

public enum BattleSystem {
    HULL("hull", Unit.BLOCKS),
    LIFT("lift", Unit.BLOCKS),
    PROPULSION("propulsion", Unit.PROPELLERS),
    POWER("power", Unit.BLOCKS),
    GUNS("guns", Unit.BLOCKS),
    CONTROL("control", Unit.BLOCKS),
    CARGO("cargo", Unit.BLOCKS);

    public enum Unit {
        BLOCKS, PROPELLERS
    }

    private static final BattleSystem[] VALUES = values();

    private final String key;
    private final Unit unit;

    BattleSystem(String key, Unit unit) {
        this.key = key;
        this.unit = unit;
    }

    public String key() {
        return key;
    }

    public Unit unit() {
        return unit;
    }

    public static BattleSystem byOrdinal(int ordinal) {
        return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : HULL;
    }
}
