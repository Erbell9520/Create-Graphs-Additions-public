package com.createstats.vccmk4.route;

import com.createstats.vccmk4.core.DriveModel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.joml.Vector3d;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

public interface RouteHost {
    default boolean returnReady() { return true; }

    default boolean present() { return true; }

    default double[] extents() { return null; }

    default com.createstats.surveyor.core.StopRules.Cargo frozenCargo() { return null; }

    default RouteSensors.Obstacle unseenObstacle() { return RouteSensors.Obstacle.NONE; }

    ServerLevel routeLevel();

    UUID consoleId();

    BlockPos consolePos();

    BlockEntity consoleEntity();

    @Nullable
    Object craft();

    @Nullable
    UUID craftId();

    @Nullable
    Object serverSubLevel();

    Vector3d referenceLocal();

    Vector3d noseLocal();

    Vector3d rightLocal();

    double kappaPerStick(double lockDeg);

    double lockAt(double speed);

    boolean skidSteer();

    String cannotFollow();

    boolean jointSteered();

    double halfTrack();

    double measuredSpeed();

    boolean calibrated();

    void armCalibration();

    DriveModel.Output appliedOutput();

    int maxRpm();

    String vehicleName();

    List<UUID> ownBodies();

    List<BlockEntity> cargoBlocks();

    boolean seatedDriver();

    void routeChanged();

    default boolean docked() {
        return false;
    }

    default double wheelDrive() {
        return Double.NaN;
    }

    default void wake() {
    }
}
