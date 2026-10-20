package com.createstats.client.tuner;

import com.createstats.network.TunerSurveyPacket;
import com.createstats.tuner.TunerStation;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class TunerSurveys {
    private static final long FRESH_MS = 4_000L;

    @Nullable
    private static BlockPos pos;
    @Nullable
    private static List<TunerStation> stations;
    private static long receivedAt;
    @Nullable
    private static BlockPos askedFor;
    private static long askedAt;

    private TunerSurveys() {
    }

    public static void request(BlockPos at) {
        if (due(at)) {
            PacketDistributor.sendToServer(new TunerSurveyPacket(at.immutable()));
        }
    }

    // panel asks through its own packet, the tuner's is arm's reach only
    public static boolean due(BlockPos at) {
        final long now = Util.getMillis();
        if (at.equals(pos) && stations != null && now - receivedAt < FRESH_MS) {
            return false;
        }
        if (at.equals(askedFor) && now - askedAt < FRESH_MS) {
            return false;
        }
        askedFor = at.immutable();
        askedAt = now;
        return true;
    }

    public static void receive(BlockPos at, List<TunerStation> list) {
        pos = at.immutable();
        stations = list;
        receivedAt = Util.getMillis();
        if (at.equals(askedFor)) {
            askedFor = null;
        }
    }

    @Nullable
    public static List<TunerStation> of(BlockPos at) {
        return at.equals(pos) ? stations : null;
    }

    @Nullable
    public static TunerStation find(BlockPos at, String id) {
        final List<TunerStation> list = of(at);
        if (list == null || id.isEmpty()) {
            return null;
        }
        for (TunerStation s : list) {
            if (s.id().equals(id)) {
                return s;
            }
        }
        return null;
    }
}
