package com.createstats.battle.console;

import java.util.List;

public record WatchView(String key, List<Contact> contacts, List<Gun> guns, long receivedAt) {
    public static final int KIND_ENTITY = 0;
    public static final int KIND_SHIP = 1;
    public static final int KIND_PLAYER = 2;

    public record Contact(int kind, int entityId, String shipId, String name, float range, float bearing,
                          float elevation, float closing, boolean hostile, boolean targetsCrew, float radius, int band) {
    }

    public record Gun(int id, int mount, String yawWhere, String pitchWhere, int yawShare, int pitchShare, int aim,
                      float flight, float lead, float error, float miss, float speed, int yawRpm, int pitchRpm,
                      float yawGain, float pitchGain, float reach) {
    }

    public Gun gun(int id) {
        for (Gun g : guns) if (g.id() == id) return g;
        return null;
    }

    public static WatchView empty(String key) {
        return new WatchView(key, List.of(), List.of(), 0L);
    }
}
