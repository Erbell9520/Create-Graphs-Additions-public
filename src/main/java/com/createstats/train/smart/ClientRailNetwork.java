package com.createstats.train.smart;

import com.createstats.network.RailSubscribePacket;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

@OnlyIn(Dist.CLIENT)
public final class ClientRailNetwork {
    private ClientRailNetwork() {
    }

    private static final int RENEW_INTERVAL = 40;

    private static RailSnapshot snapshot = RailSnapshot.EMPTY;
    private static long lastPacketTime;
    private static int renewCountdown;
    private static boolean wantedThisTick;
    private static boolean subscribed;

    // never null
    public static RailSnapshot get() {
        return snapshot;
    }

    public static boolean isLive() {
        return snapshot != RailSnapshot.EMPTY
                && System.currentTimeMillis() - lastPacketTime < 5000L;
    }

    public static void requestUpdates() {
        wantedThisTick = true;
    }

    public static void tick() {
        if (wantedThisTick) {
            if (--renewCountdown <= 0) {
                renewCountdown = RENEW_INTERVAL;
                PacketDistributor.sendToServer(new RailSubscribePacket(true));
                subscribed = true;
            }
        } else if (subscribed) {
            subscribed = false;
            renewCountdown = 0;
            PacketDistributor.sendToServer(new RailSubscribePacket(false));
        }
        wantedThisTick = false;
    }

    public static void accept(RailSnapshot incoming, boolean includesTopology) {
        if (incoming == null) {
            return;
        }
        if (!includesTopology) {
            incoming.adoptTopology(snapshot);
        }
        snapshot = incoming;
        lastPacketTime = System.currentTimeMillis();
    }

    public static void clear() {
        snapshot = RailSnapshot.EMPTY;
        lastPacketTime = 0L;
        renewCountdown = 0;
        subscribed = false;
        wantedThisTick = false;
    }
}
