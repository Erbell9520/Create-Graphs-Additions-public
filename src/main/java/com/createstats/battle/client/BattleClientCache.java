package com.createstats.battle.client;

import com.createstats.battle.console.DetailView;
import com.createstats.battle.console.JobBoard;
import com.createstats.battle.console.ShapeView;
import com.createstats.battle.console.WatchView;
import com.createstats.battle.core.Projection;
import com.createstats.battle.net.BattleDetailPacket;
import com.createstats.battle.net.BattleShapePacket;
import com.createstats.battle.net.BattleWatchPacket;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class BattleClientCache {
    private static final Map<String, ShapeView> SHAPES = new HashMap<>();
    private static final Map<String, DetailView> DETAILS = new HashMap<>();
    private static final Map<String, WatchView> WATCH = new HashMap<>();

    private static final Map<String, JobBoard> JOBS = new HashMap<>();

    private BattleClientCache() {
    }

    public static JobBoard jobs(String key) {
        return JOBS.computeIfAbsent(key, k -> new JobBoard());
    }

    @Nullable
    public static ShapeView shape(String key) {
        return SHAPES.get(key);
    }

    @Nullable
    public static DetailView detail(String key) {
        return DETAILS.get(key);
    }

    public static WatchView watch(String key) {
        WatchView w = WATCH.get(key);
        return w == null ? WatchView.empty(key) : w;
    }

    public static void clear() {
        SHAPES.clear();
        DETAILS.clear();
        WATCH.clear();
        JOBS.clear();
    }

    static void accept(BattleWatchPacket p) {
        List<WatchView.Contact> contacts = new ArrayList<>(p.contacts().size());
        for (BattleWatchPacket.ContactLine c : p.contacts()) {
            contacts.add(new WatchView.Contact(c.kind(), c.entityId(), c.shipId(), c.name(), c.range(), c.bearing(),
                    c.elevation(), c.closing(), c.hostile(), c.targetsCrew(), c.radius(), c.band()));
        }
        List<WatchView.Gun> guns = new ArrayList<>(p.guns().size());
        for (BattleWatchPacket.GunDetail g : p.guns()) {
            guns.add(new WatchView.Gun(g.id(), g.mount(), g.yawWhere(), g.pitchWhere(), g.yawShare(), g.pitchShare(),
                    g.aim(), g.flight(), g.lead(), g.error(), g.miss(), g.speed(), g.yawRpm(), g.pitchRpm(), g.yawGain(),
                    g.pitchGain(), g.reach()));
        }
        long now = Minecraft.getInstance().level == null ? 0L : Minecraft.getInstance().level.getGameTime();
        WATCH.put(p.key(), new WatchView(p.key(), List.copyOf(contacts), List.copyOf(guns), now));
    }

    static void accept(BattleShapePacket p) {
        int[] f = p.frame();
        if (f.length < 7) return;
        Projection.Frame frame = new Projection.Frame(f[0], f[1], f[2], f[3], f[4], f[5], Math.max(1, f[6]));
        byte[] plan = Projection.unpack2(p.plan(), frame.planW() * frame.planH());
        byte[] profile = Projection.unpack2(p.profile(), frame.profileW() * frame.profileH());
        SHAPES.put(p.key(), new ShapeView(p.key(), p.version(), p.ground(), p.bow(), p.origin(), frame, plan, profile,
                p.props(), p.bridges()));
    }

    static void accept(BattleDetailPacket p) {
        List<DetailView.Place> places = new ArrayList<>(p.places().size());
        for (BattleDetailPacket.PlaceLine l : p.places()) {
            places.add(new DetailView.Place(l.id(), l.name(), l.kind(), l.size(), l.anchor()));
        }
        long now = Minecraft.getInstance().level == null ? 0L : Minecraft.getInstance().level.getGameTime();
        DETAILS.put(p.key(), new DetailView(p.key(), p.lostVersion(), p.lost(), p.ageTicks(), p.causes(), p.burning(),
                List.copyOf(places), now));
    }
}
