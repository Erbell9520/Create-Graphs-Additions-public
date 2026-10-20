package com.createstats.vccmk4;

import com.createstats.flight.bridge.SableBridge;
import com.createstats.vccmk4.core.SeatRule;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

import javax.annotation.Nullable;

public final class SeatReach {
    static final double SEAT_LIFT = 0.25;

    static final double SAME_GRID = 64.0;

    private SeatReach() {
    }

    @Nullable
    public static int[] offset(Player player, BlockPos console, @Nullable Object craft) {
        Entity seat = player.getVehicle();
        Vec3 at = seat != null ? seat.position() : player.position();
        Vector3d p = new Vector3d(at.x, at.y, at.z);
        boolean onGrid = Math.abs(p.x - console.getX()) < SAME_GRID && Math.abs(p.y - console.getY()) < SAME_GRID
                && Math.abs(p.z - console.getZ()) < SAME_GRID;
        if (!onGrid && craft != null) {
            Vector3d local = SableBridge.worldToLocal(craft, p);
            if (local == null || !Double.isFinite(local.x) || !Double.isFinite(local.y) || !Double.isFinite(local.z)) {
                return null;
            }
            p = local;
        }
        if (!Double.isFinite(p.x) || !Double.isFinite(p.y) || !Double.isFinite(p.z)) return null;
        return new int[] {Mth.floor(p.x) - console.getX(), Mth.floor(p.y + SEAT_LIFT) - console.getY(),
                Mth.floor(p.z) - console.getZ()};
    }
}
