package com.createstats.vccmk4.console;

import com.createstats.surveyor.core.Pathway;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

public interface BoardHost extends ConsoleHost {
    record Line(Pathway pathway, String owner, int vehicles, int warn, String warning, boolean mine, boolean editable) {
    }

    record Vehicle(UUID console, UUID pathway, String name, int state, String reason, double x, double z, float heading,
                   float speed, float s, int nextStop, float eta, float waitFraction, String waitText, int laps, float lastLap,
                   int cargo) {
    }

    record LogLine(long time, UUID pathway, String text) {
    }

    record Health(int level, double from, double to, String why, int fix, int node, double at) {
    }

    default List<Health> health(UUID pathway) {
        return List.of();
    }

    record Terrain(int x0, int z0, int cell, int w, int h, int[] argb) {
    }

    default List<Terrain> terrain() {
        return List.of();
    }

    default boolean healthKnown(UUID pathway) {
        return false;
    }

    record Trail(UUID console, String name, long[] time, float[] s, int[] laps) {
    }

    default List<Trail> trails(UUID pathway) {
        return List.of();
    }

    List<Line> pathways();

    List<Vehicle> vehicles();

    List<LogLine> log();

    long now();

    @Nullable
    double[] you();

    double groundY(double x, double z, double fallback);

    String heldItem();

    String heldFluid();

    String itemName(String id);

    void sendEdit(int op, UUID pathway, long version, int node, double x, double y, double z, int a, String text, byte[] data);

    void sendOrder(int op, UUID console, UUID pathway, int a);

    void useWithStaff(UUID pathway);

    void startNew();

    void askText(String current, int maxLength, Consumer<String> done);

    void pin(UUID console);
}
