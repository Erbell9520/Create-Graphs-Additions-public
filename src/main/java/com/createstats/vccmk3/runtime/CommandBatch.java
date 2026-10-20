package com.createstats.vccmk3.runtime;

import net.minecraft.core.BlockPos;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

// accumulate then apply, or the second write silently wins
public final class CommandBatch {
    private final Map<BlockPos, Integer> rpm = new LinkedHashMap<>();
    private final Map<BlockPos, Double> angle = new LinkedHashMap<>();
    private final Map<BlockPos, Integer> steer = new LinkedHashMap<>();
    private final Map<Integer, Integer> signals = new LinkedHashMap<>();
    private final Set<BlockPos> reverseAllowed = new LinkedHashSet<>();

    private int routesApplied;
    private int routesUnsupported;

    public void addRpm(BlockPos governor, int delta, boolean allowReverse) {
        if (governor == null) return;
        rpm.merge(governor, delta, Integer::sum);
        if (allowReverse) reverseAllowed.add(governor);
    }

    public void setRpm(BlockPos governor, int value) {
        if (governor == null) return;
        rpm.put(governor, value);

        reverseAllowed.add(governor);
    }

    public void addAngle(BlockPos actuator, double degrees) {
        if (actuator == null) return;
        angle.merge(actuator, degrees, Double::sum);
    }

    public void addSteer(BlockPos wheel, int signal) {
        if (wheel == null) return;
        steer.merge(wheel, signal, Integer::sum);
    }

    public void raiseSignal(int row, int level) {
        signals.merge(row, level, Math::max);
    }

    public void countApplied() {
        routesApplied++;
    }

    public void countUnsupported() {
        routesUnsupported++;
    }

    public Map<BlockPos, Integer> rpmCommands() {
        return rpm;
    }

    public Map<BlockPos, Double> angleCommands() {
        return angle;
    }

    public Map<BlockPos, Integer> steerCommands() {
        return steer;
    }

    public Map<Integer, Integer> signalCommands() {
        return signals;
    }

    public boolean allowsReverse(BlockPos governor) {
        return reverseAllowed.contains(governor);
    }

    public int routesApplied() {
        return routesApplied;
    }

    public int routesUnsupported() {
        return routesUnsupported;
    }

    public boolean isEmpty() {
        return rpm.isEmpty() && angle.isEmpty() && steer.isEmpty() && signals.isEmpty();
    }
}
