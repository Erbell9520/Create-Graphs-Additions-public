package com.createstats.flight.client;

import com.createstats.flight.FlightMode;
import com.createstats.flight.NoseAxis;
import com.createstats.flight.SensorRequirement;
import com.createstats.flight.workbench.FccDiagramProfile;
import com.createstats.flight.network.FccGuiSnapshot;
import com.createstats.flight.network.FccGuiSnapshotPacket;
import com.createstats.flight.network.FccTelemetryPacket;
import com.createstats.flight.screen.FlightControlComputerScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.Objects;

/**
 * Client cache of the latest FCC GUI snapshot/telemetry, keyed by containerId.
 * Drops packets whose containerId does not match the currently open FCC screen.
 */
public final class FccClientState {

    // [1]
    private static final double MAX_HELMET_EXTRAPOLATION_SECONDS = 0.35D;
    private static final double MAX_HELMET_STALE_SECONDS = 0.35D;

    private static volatile int cachedContainerId = -1;
    private static volatile FccGuiSnapshot lastSnapshot = null;
    private static volatile FccTelemetryPacket lastTelemetry = null;
    private static volatile long lastTelemetryNanos = Long.MIN_VALUE;
    private static volatile double distanceTravelledMeters = 0.0;
    @Nullable
    private static volatile BlockPos pinnedControllerPos = null;
    private static volatile FccGuiSnapshot pinnedSnapshot = null;
    private static volatile FccTelemetryPacket pinnedTelemetry = null;
    private static volatile long pinnedTelemetryNanos = Long.MIN_VALUE;
    private static volatile double pinnedDistanceTravelledMeters = 0.0;
    @Nullable
    private static volatile BlockPos helmetControllerPos = null;
    private static volatile FccGuiSnapshot helmetSnapshot = null;
    private static volatile FccTelemetryPacket previousHelmetTelemetry = null;
    private static volatile long previousHelmetTelemetryNanos = Long.MIN_VALUE;
    private static volatile FccTelemetryPacket helmetTelemetry = null;
    private static volatile long helmetTelemetryNanos = Long.MIN_VALUE;
    private static volatile double helmetDistanceTravelledMeters = 0.0;

    private FccClientState() {}

    public static void applySnapshot(FccGuiSnapshotPacket pkt) {
        Integer openId = openContainerId();
        FccGuiSnapshot snapshot = pkt.snapshot();
        boolean forPinned = pinnedControllerPos != null && snapshot.fccPos().equals(pinnedControllerPos);
        boolean forHelmet = helmetControllerPos != null && snapshot.fccPos().equals(helmetControllerPos);
        boolean forOpen = openId != null && openId == pkt.containerId();
        if (!forOpen && !forPinned && !forHelmet && openId != null) return;
        if (forOpen || (openId == null && !forPinned && !forHelmet)) {
            cachedContainerId = pkt.containerId();
            lastSnapshot = snapshot;
        }
        if (forPinned) {
            pinnedSnapshot = snapshot;
        }
        if (forHelmet) {
            helmetSnapshot = snapshot;
        }
        FlightControlComputerScreen open = openScreen();
        if (open != null && open.getMenu().containerId == pkt.containerId()) {
            open.onSnapshot(snapshot);
        }
    }

    public static void applyTelemetry(FccTelemetryPacket pkt) {
        Integer openId = openContainerId();
        BlockPos controllerPos = BlockPos.of(pkt.controllerPosLong());
        boolean forOpen = openId != null && openId == pkt.containerId();
        boolean forPinned = pinnedControllerPos != null && controllerPos.equals(pinnedControllerPos);
        boolean forHelmet = helmetControllerPos != null && controllerPos.equals(helmetControllerPos);
        if (!forOpen && !forPinned && !forHelmet) return;
        long now = System.nanoTime();
        if (forOpen) {
            distanceTravelledMeters = accumulateDistance(distanceTravelledMeters, lastTelemetry, lastTelemetryNanos, pkt, now);
            lastTelemetryNanos = now;
            cachedContainerId = pkt.containerId();
            lastTelemetry = pkt;
        }
        if (forPinned) {
            pinnedDistanceTravelledMeters = accumulateDistance(pinnedDistanceTravelledMeters, pinnedTelemetry,
                pinnedTelemetryNanos, pkt, now);
            pinnedTelemetryNanos = now;
            pinnedTelemetry = pkt;
        }
        if (forHelmet) {
            if (sameTelemetryStream(helmetTelemetry, pkt)) {
                previousHelmetTelemetry = helmetTelemetry;
                previousHelmetTelemetryNanos = helmetTelemetryNanos;
            } else {
                previousHelmetTelemetry = null;
                previousHelmetTelemetryNanos = Long.MIN_VALUE;
            }
            helmetDistanceTravelledMeters = accumulateDistance(helmetDistanceTravelledMeters, helmetTelemetry,
                helmetTelemetryNanos, pkt, now);
            helmetTelemetryNanos = now;
            helmetTelemetry = pkt;
        }
        FlightControlComputerScreen open = openScreen();
        if (open != null && open.getMenu().containerId == pkt.containerId()) {
            open.onTelemetry(pkt);
        }
    }

    public static void pinHud(@Nullable BlockPos controllerPos) {
        if (!Objects.equals(pinnedControllerPos, controllerPos)) {
            pinnedSnapshot = null;
            pinnedTelemetry = null;
            pinnedTelemetryNanos = Long.MIN_VALUE;
            pinnedDistanceTravelledMeters = 0.0;
        }
        pinnedControllerPos = controllerPos;
    }

    public static boolean isHudPinned(@Nullable BlockPos controllerPos) {
        return controllerPos != null && controllerPos.equals(pinnedControllerPos);
    }

    public static void pinHelmetHud(@Nullable BlockPos controllerPos) {
        if (!Objects.equals(helmetControllerPos, controllerPos)) {
            helmetSnapshot = null;
            previousHelmetTelemetry = null;
            previousHelmetTelemetryNanos = Long.MIN_VALUE;
            helmetTelemetry = null;
            helmetTelemetryNanos = Long.MIN_VALUE;
            helmetDistanceTravelledMeters = 0.0;
        }
        helmetControllerPos = controllerPos;
    }

    @Nullable
    public static FccTelemetryPacket pinnedTelemetry() {
        if (pinnedControllerPos == null || pinnedTelemetry == null) {
            return null;
        }
        return BlockPos.of(pinnedTelemetry.controllerPosLong()).equals(pinnedControllerPos) ? pinnedTelemetry : null;
    }

    @Nullable
    public static FccTelemetryPacket helmetTelemetry() {
        if (helmetControllerPos == null || helmetTelemetry == null) {
            return null;
        }
        return BlockPos.of(helmetTelemetry.controllerPosLong()).equals(helmetControllerPos) ? helmetTelemetry : null;
    }

    /** [2] */
    public static double helmetTelemetryAgeSeconds() {
        FccTelemetryPacket latest = helmetTelemetry;
        long latestNanos = helmetTelemetryNanos;
        if (helmetControllerPos == null || latest == null || latestNanos == Long.MIN_VALUE
            || !BlockPos.of(latest.controllerPosLong()).equals(helmetControllerPos)) {
            return Double.POSITIVE_INFINITY;
        }
        return Math.max(0.0D, (System.nanoTime() - latestNanos) / 1_000_000_000.0D);
    }

    @Nullable
    public static HelmetTelemetryFrame helmetTelemetryFrame() {
        if (helmetControllerPos == null) {
            return null;
        }
        FccTelemetryPacket latest = helmetTelemetry;
        if (latest == null || !BlockPos.of(latest.controllerPosLong()).equals(helmetControllerPos)) {
            return null;
        }
        FccTelemetryPacket previous = previousHelmetTelemetry;
        long latestNanos = helmetTelemetryNanos;
        long previousNanos = previousHelmetTelemetryNanos;
        double ageSeconds = Math.max(0.0D, (System.nanoTime() - latestNanos) / 1_000_000_000.0D);
        if (previous == null || previousNanos == Long.MIN_VALUE || !sameTelemetryStream(previous, latest)) {
            return HelmetTelemetryFrame.fromLatest(latest);
        }
        double sampleSeconds = Math.max(1.0E-3D, (latestNanos - previousNanos) / 1_000_000_000.0D);
        double extrapolationSeconds = ageSeconds > MAX_HELMET_STALE_SECONDS
            ? 0.0D
            : Math.min(ageSeconds, Math.min(MAX_HELMET_EXTRAPOLATION_SECONDS, sampleSeconds));
        float headingRate = Mth.wrapDegrees(latest.headingDeg() - previous.headingDeg()) / (float) sampleSeconds;
        float pitchRate = (latest.pitchDeg() - previous.pitchDeg()) / (float) sampleSeconds;
        float rollRate = (latest.rollDeg() - previous.rollDeg()) / (float) sampleSeconds;
        float velXRate = (latest.velX() - previous.velX()) / (float) sampleSeconds;
        float velYRate = (latest.velY() - previous.velY()) / (float) sampleSeconds;
        float velZRate = (latest.velZ() - previous.velZ()) / (float) sampleSeconds;
        return new HelmetTelemetryFrame(
            latest,
            wrapTelemetryDegrees(latest.headingDeg() + headingRate * (float) extrapolationSeconds),
            latest.pitchDeg() + pitchRate * (float) extrapolationSeconds,
            latest.rollDeg() + rollRate * (float) extrapolationSeconds,
            latest.altitude() + latest.climbRate() * extrapolationSeconds,
            latest.climbRate(),
            latest.velX() + velXRate * (float) extrapolationSeconds,
            latest.velY() + velYRate * (float) extrapolationSeconds,
            latest.velZ() + velZRate * (float) extrapolationSeconds);
    }

    public static boolean acceptsTelemetryFor(BlockPos controllerPos) {
        return pinnedControllerPos != null && pinnedControllerPos.equals(controllerPos);
    }

    /** Snapshot for the given container, or null if cache is for a different one. */
    public static FccGuiSnapshot snapshotFor(int containerId) {
        return (cachedContainerId == containerId) ? lastSnapshot : null;
    }

    /**
     * Snapshot lookup for pinned HUD rendering.
     * Falls back to the latest snapshot when HUD is pinned and ids drift
     * (e.g. menu close/open transitions).
     */
    @Nullable
    public static FccGuiSnapshot snapshotForPinned(int containerId) {
        if (pinnedControllerPos == null) return null;
        if (pinnedSnapshot != null && pinnedSnapshot.fccPos().equals(pinnedControllerPos)) return pinnedSnapshot;
        if (cachedContainerId == containerId && lastSnapshot != null && lastSnapshot.fccPos().equals(pinnedControllerPos)) {
            return lastSnapshot;
        }
        return lastSnapshot != null && lastSnapshot.fccPos().equals(pinnedControllerPos) ? lastSnapshot : null;
    }

    @Nullable
    public static FccGuiSnapshot helmetSnapshot() {
        if (helmetControllerPos == null) return null;
        if (helmetSnapshot != null && helmetSnapshot.fccPos().equals(helmetControllerPos)) return helmetSnapshot;
        return lastSnapshot != null && lastSnapshot.fccPos().equals(helmetControllerPos) ? lastSnapshot : null;
    }

    public static FccTelemetryPacket telemetryFor(int containerId) {
        return (cachedContainerId == containerId) ? lastTelemetry : null;
    }

    public static void clear() {
        cachedContainerId = -1;
        lastSnapshot = null;
        lastTelemetry = null;
        lastTelemetryNanos = Long.MIN_VALUE;
        distanceTravelledMeters = 0.0;
        pinnedControllerPos = null;
        pinnedSnapshot = null;
        pinnedTelemetry = null;
        pinnedTelemetryNanos = Long.MIN_VALUE;
        pinnedDistanceTravelledMeters = 0.0;
        helmetControllerPos = null;
        helmetSnapshot = null;
        previousHelmetTelemetry = null;
        previousHelmetTelemetryNanos = Long.MIN_VALUE;
        helmetTelemetry = null;
        helmetTelemetryNanos = Long.MIN_VALUE;
        helmetDistanceTravelledMeters = 0.0;
    }

    private static Integer openContainerId() {
        FlightControlComputerScreen open = openScreen();
        return open != null ? open.getMenu().containerId : null;
    }

    private static FlightControlComputerScreen openScreen() {
        Minecraft mc = Minecraft.getInstance();
        return (mc != null && mc.screen instanceof FlightControlComputerScreen s) ? s : null;
    }

    public static FccGuiSnapshot empty() {
        return new FccGuiSnapshot(
            net.minecraft.core.BlockPos.ZERO, FlightMode.OFF, NoseAxis.POS_Z, FccDiagramProfile.BALLOON_ZEPPELIN,
            0, 0, 0, EnumSet.noneOf(SensorRequirement.class), false, 0, 0, 0, 0, 0, "",
            null, null, null, null, 0,
            0, 0, 0, "", FccGuiSnapshot.READY_NO_BINDINGS, 0,
            0, 0, 0, 0,
            java.util.List.of(), java.util.List.of(), java.util.List.of(),
            java.util.List.of(), java.util.List.of(),
            java.util.List.of(), java.util.List.of(),
            0, 0, 0,
            0, 0, 0, 2,
                null, "", "No Pathfinder linked", false, 0f, 0, 0, 0.0, 0, java.util.List.of(),
                true,
                false, false, 0.0, 0.0, 0, 8.0f, Double.NaN, Double.NaN, Double.NaN, "", 1.0f,
                true, 100.0f);
    }

    public static double distanceTravelledMeters(int containerId) {
        if (cachedContainerId == containerId) return distanceTravelledMeters;
        if (pinnedTelemetry != null && pinnedTelemetry.containerId() == containerId) return pinnedDistanceTravelledMeters;
        return 0.0;
    }

    private static double accumulateDistance(double current, @Nullable FccTelemetryPacket previous,
                                             long previousNanos, FccTelemetryPacket next, long nowNanos) {
        if (previous == null || previousNanos == Long.MIN_VALUE) return current;
        if (previous.containerId() != next.containerId() || previous.controllerPosLong() != next.controllerPosLong()) {
            return current;
        }
        double dt = Math.max(0.0, (nowNanos - previousNanos) / 1_000_000_000.0);
        double speed = Math.sqrt(next.velX() * next.velX() + next.velY() * next.velY() + next.velZ() * next.velZ());
        return current + speed * dt;
    }

    private static boolean sameTelemetryStream(@Nullable FccTelemetryPacket left, @Nullable FccTelemetryPacket right) {
        return left != null && right != null
            && left.containerId() == right.containerId()
            && left.controllerPosLong() == right.controllerPosLong();
    }

    private static float wrapTelemetryDegrees(float degrees) {
        return Mth.wrapDegrees(degrees);
    }

    public record HelmetTelemetryFrame(
        FccTelemetryPacket packet,
        float headingDeg,
        float pitchDeg,
        float rollDeg,
        double altitude,
        float climbRate,
        float velX,
        float velY,
        float velZ
    ) {
        private static HelmetTelemetryFrame fromLatest(FccTelemetryPacket packet) {
            return new HelmetTelemetryFrame(
                packet,
                packet.headingDeg(),
                packet.pitchDeg(),
                packet.rollDeg(),
                packet.altitude(),
                packet.climbRate(),
                packet.velX(),
                packet.velY(),
                packet.velZ());
        }
    }
}
