package com.createstats.vccmk4;

import com.createstats.flight.bridge.SableBridge;
import com.createstats.vccmk4.core.JetKind;
import com.createstats.vccmk4.core.JetSupply;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.joml.Vector3d;

import javax.annotation.Nullable;
import java.util.UUID;

// the aerial found the thrusters and listed them as "not flown yet". this opens the path
public final class Vcc4DirectJets {
    public record Reading(JetKind kind, double force, Vector3d direction, double ceiling, double clear, JetSupply supply,
                          boolean lit) {
    }

    public enum Result {
        DRIVEN,

        HELD,

        GONE
    }

    private Vcc4DirectJets() {
    }

    @Nullable
    public static Reading read(@Nullable BlockEntity be) {
        Vcc4Jets.Reading r = Vcc4Jets.read(be);
        if (r == null) return null;
        return new Reading(r.kind(), r.force(), new Vector3d(r.direction()), r.ceiling(), r.clear(), r.supply(), r.lit());
    }

    public static Result command(ServerLevel level, BlockPos pos, UUID owner, double command) {
        BlockEntity be = SableBridge.getBlockEntityOnContraption(level, pos);
        Vcc4Jets.Type type = Vcc4Jets.of(be);
        if (type == null || be == null || be.isRemoved()) return Result.GONE;
        if (!Vcc4ThrustLeases.renew(level, pos, owner, be, type)) return Result.HELD;
        return type.command(level, be, Math.max(0.0, Math.min(1.0, command)), null) ? Result.DRIVEN : Result.GONE;
    }

    public static boolean heldByAnother(ServerLevel level, BlockPos pos, UUID owner) {
        return Vcc4ThrustLeases.heldByAnother(level, pos, owner);
    }

    public static void releaseAll(ServerLevel level, UUID owner) {
        Vcc4ThrustLeases.releaseAll(level, owner);
    }
}
