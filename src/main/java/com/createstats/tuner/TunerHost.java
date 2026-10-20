package com.createstats.tuner;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface TunerHost {
    TunerRange tunerRange();

    @Nullable
    default TunerBand dialBand() {
        return null;
    }

    default Component rangeLabel(@Nullable TunerStation station, int range) {
        return Component.translatable("create_stats.tuner.range", range);
    }

    default Component costLabel(int range, boolean untuned) {
        return Component.translatable("create_stats.tuner.cost", untuned ? 0 : tunerRange().costAt(range));
    }

    default Component stationLine(TunerStation station, int range, boolean compact) {
        return Component.literal(station.subLabel());
    }

    default boolean tunesAt(net.minecraft.core.BlockPos clicked, net.minecraft.world.phys.Vec3 hit) {
        return true;
    }

    @org.jetbrains.annotations.Nullable
    static net.minecraft.core.BlockPos hostPos(net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof TunerHost) {
            return pos;
        }
        final net.minecraft.world.level.block.state.BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof Part part) {
            final net.minecraft.core.BlockPos at = part.tunerHostPos(state, pos);
            return at != null && level.getBlockEntity(at) instanceof TunerHost ? at : null;
        }
        return null;
    }

    interface Part {
        @org.jetbrains.annotations.Nullable
        net.minecraft.core.BlockPos tunerHostPos(net.minecraft.world.level.block.state.BlockState state,
                                                 net.minecraft.core.BlockPos pos);
    }

    @Nullable
    default Component tunerNote(@Nullable TunerStation station, int range) {
        return null;
    }

    // courtesy only, the host does the actual refusing
    @Nullable
    default Component tunerLock() {
        return null;
    }

    String tunedStation();

    int tunedRange();

    List<TunerStation> surveyStations(ServerPlayer player);

    void applyTuning(ServerPlayer player, String station, int range);
}
