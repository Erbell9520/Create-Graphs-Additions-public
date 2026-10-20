package com.createstats.battle.world;

import com.createstats.battle.core.GunGraph;
import com.createstats.battle.core.GunState;
import com.createstats.battle.net.BattleKeyPacket;
import com.createstats.battle.net.BattleWatchPacket;
import com.createstats.blockentity.BattleScreenDisplayBE;
import com.createstats.flight.bridge.SableBridge;
import com.createstats.item.PilotHelmetData;
import com.createstats.item.PilotHelmetItem;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class BattleCrew {
    public static final int KEYS_PER_SECOND = 4;

    public static final double HELMET_RANGE = 256;

    private static final Map<UUID, long[]> RATE = new HashMap<>();

    private BattleCrew() {
    }

    public static void handleKey(ServerPlayer player, BattleKeyPacket k) {
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        long[] rate = RATE.computeIfAbsent(player.getUUID(), u -> new long[]{now, 0});
        if (now - rate[0] >= 20) {
            rate[0] = now;
            rate[1] = 0;
        }
        if (++rate[1] > KEYS_PER_SECOND) return;
        ShipRecord r = recordFor(player);
        if (r == null) {
            player.displayClientMessage(BattleText.tr("key.not_aboard"), true);
            return;
        }
        String by = player.getGameProfile().getName();
        switch (k.action()) {
            case BattleKeyPacket.FIRE -> {
                if (!r.election().hasMaster()) {
                    player.displayClientMessage(BattleText.tr("status.no_power"), true);
                    return;
                }
                r.requestSalvo(player, by);
            }
            case BattleKeyPacket.TOGGLE_WEAPONS -> {
                if (r.redstoneIn == 1) {
                    player.displayClientMessage(BattleText.tr("weapons.by_redstone"), true);
                    return;
                }
                r.setWeapons(!r.weaponsFree, by, now);
                player.displayClientMessage(BattleText.tr(r.weaponsFree ? "status.weapons_free" : "weapons.safe"), true);
            }
            case BattleKeyPacket.DESIGNATE -> {
                int kind = k.shipId() == null || k.shipId().isEmpty() ? TargetFinder.KIND_ENTITY : TargetFinder.KIND_SHIP;
                designate(r, level, player, kind, k.entityId(), k.shipId(), false, now);
            }
            default -> {
            }
        }
    }

    @Nullable
    public static ShipRecord recordFor(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        Object sub = SablePlots.shipContaining(level, player.position(), 1.5);
        if (sub != null) {
            UUID id = SableBridge.subLevelId(sub);
            ShipRecord r = id == null ? null : ShipRecords.loadedShip(level, id);
            if (r != null) return r;
        }
        List<ShipRecord> ground = ShipRecords.groundAt(level, player.blockPosition());
        if (!ground.isEmpty()) return ground.get(0);
        ItemStack helmet = player.getItemBySlot(EquipmentSlot.HEAD);
        if (helmet.getItem() instanceof PilotHelmetItem) {
            PilotHelmetData.LinkedNode link = PilotHelmetItem.getData(helmet).getBattleBridgeLink();
            BlockPos pos = link == null ? null : link.getTargetPos();
            if (pos != null) {
                BlockEntity be = SableBridge.getBlockEntityOnContraption(level, pos);
                if (be instanceof BattleScreenDisplayBE bridge) {
                    Vec3 w = SableBridge.worldPointOf(be, Vec3.atCenterOf(pos));
                    if (w.distanceTo(player.position()) <= HELMET_RANGE) return bridge.record();
                }
            }
        }
        return null;
    }

    public static boolean designate(ShipRecord r, ServerLevel level, ServerPlayer by, int kind, int entityId, String shipId,
                                    boolean allowPlayer, long now) {
        double maxRange = TargetFinder.radius(r) * 2;
        Vec3 bridge = r.bridgeAt == Long.MIN_VALUE ? by.position() : new Vec3(r.bridgeLast[0], r.bridgeLast[1], r.bridgeLast[2]);
        UUID id;
        String name;
        if (kind == TargetFinder.KIND_SHIP) {
            try {
                id = UUID.fromString(shipId);
            } catch (IllegalArgumentException e) {
                return false;
            }
            if (r.ownBodies.contains(id)) return refuse(by, "designate.own");
            Object sub = SableBridge.findSubLevelById(level, id);
            if (sub == null) return false;
            String nm = SableBridge.subLevelName(sub);
            name = nm == null || nm.isEmpty() ? "#battle.create_stats.ship" : nm;
        } else {
            Entity e = level.getEntity(entityId);
            if (!(e instanceof LivingEntity le) || !le.isAlive() || e.isSpectator()) return false;
            boolean player = e instanceof Player;
            if (player && !allowPlayer) return refuse(by, "designate.no_players");
            kind = player ? TargetFinder.KIND_PLAYER : TargetFinder.KIND_ENTITY;
            ShipScanner.Area area = ShipScanner.area(r, level);
            AABB own = area == null ? null : area.bounds();
            if (own != null && own.inflate(1.5).contains(e.getBoundingBox().getCenter())) return refuse(by, "designate.own");
            if (e.position().distanceTo(bridge) > maxRange) return refuse(by, "designate.too_far");
            id = e.getUUID();
            name = TargetFinder.nameOf(e);
        }
        if (!r.designate(kind, id, name, by.getGameProfile().getName(), now)) return false;
        by.playNotifySound(SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.PLAYERS, 0.6f, 1.5f);
        r.designationCaller = by;
        r.designationAt = now;
        return true;
    }

    private static boolean refuse(ServerPlayer player, String key) {
        player.displayClientMessage(BattleText.tr(key), true);
        return false;
    }

    static MutableComponent designated(ShipRecord r) {
        TargetFinder.Target t = r.target;
        Component name = t == null ? Component.empty() : BattleText.name(t.name);
        List<String> tracking = new ArrayList<>();
        MutableComponent rest = Component.empty();
        int others = 0;
        for (GunRecord g : r.guns) {
            if (g.mode == GunState.MODE_HOLD) continue;
            switch (g.state) {
                case SLEWING, READY, SAFE, FIRING, BLOCKED, LOADING, EMPTY, OFF_BORE -> tracking.add(g.label());
                default -> {
                    if (others++ > 0) rest.append("; ");
                    rest.append(BattleText.tr("salvo.item", g.label(), BattleText.tr("gun.word." + g.state.key())));
                }
            }
        }
        if (tracking.isEmpty() && others == 0) return BattleText.tr("designated", name);
        MutableComponent list = Component.empty();
        if (!tracking.isEmpty()) list.append(BattleText.tr("designated.tracking", String.join(", ", tracking)));
        if (others > 0) {
            if (!tracking.isEmpty()) list.append("; ");
            list.append(rest);
        }
        return BattleText.tr("designated.guns", name, list);
    }

    static BattleWatchPacket watchPacket(ShipRecord r, long now) {
        List<BattleWatchPacket.ContactLine> contacts = new ArrayList<>();
        for (TargetFinder.Contact c : r.contacts) {
            contacts.add(new BattleWatchPacket.ContactLine(c.kind(), c.entityId(), c.shipId(), c.name(), (float) c.range(),
                    (float) c.bearing(), (float) c.elevation(), (float) c.closing(), c.hostile(), c.targetsCrew(),
                    (float) c.radius(), c.band()));
        }
        List<BattleWatchPacket.GunDetail> guns = new ArrayList<>();
        for (GunRecord g : r.guns) {
            String yawWhere = g.shared ? "=" : g.yawConflict ? "!" : where(r, g.yawGov);
            String pitchWhere = g.shared ? "=" : g.pitchConflict ? "!" : where(r, g.pitchGov);
            float flight = g.answer == null ? -1f : (float) g.answer.flightTicks();
            float lead = g.answer == null ? 0f : (float) g.answer.leadDeg();
            guns.add(new BattleWatchPacket.GunDetail(g.id, g.mountType, yawWhere, pitchWhere, g.yawLeader, g.pitchLeader,
                    g.aimStatus(now), flight, lead, (float) g.aimError, (float) g.miss, (float) g.v0, g.yawRpm, g.pitchRpm,
                    (float) g.yawGainShown(), (float) g.pitchGainShown(),
                    (float) g.maxRange));
        }
        return new BattleWatchPacket(r.key().text(), contacts, guns);
    }

    private static String where(ShipRecord r, long gov) {
        if (gov == GunGraph.NONE) return "";
        int packed = r.pack(BlockPos.of(gov));
        return packed < 0 ? "?" : r.namer().geometry(packed);
    }
}
