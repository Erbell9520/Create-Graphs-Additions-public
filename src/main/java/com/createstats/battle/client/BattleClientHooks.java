package com.createstats.battle.client;

import com.createstats.battle.net.BattleDetailPacket;
import com.createstats.battle.net.BattleShapePacket;
import com.createstats.battle.net.BattleWatchPacket;

public final class BattleClientHooks {
    private BattleClientHooks() {
    }

    public static void shape(BattleShapePacket packet) {
        BattleClientCache.accept(packet);
    }

    public static void detail(BattleDetailPacket packet) {
        BattleClientCache.accept(packet);
    }

    public static void watch(BattleWatchPacket packet) {
        BattleClientCache.accept(packet);
    }
}
