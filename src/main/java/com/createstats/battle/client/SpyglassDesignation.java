package com.createstats.battle.client;

import com.createstats.CreateStats;
import com.createstats.battle.core.BattleReport;
import com.createstats.battle.net.BattleKeyPacket;
import com.createstats.battle.world.SablePlots;
import com.createstats.blockentity.BattleScreenDisplayBE;
import com.createstats.flight.bridge.SableBridge;
import com.createstats.item.PilotHelmetData;
import com.createstats.item.PilotHelmetItem;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.UUID;

@EventBusSubscriber(modid = CreateStats.MOD_ID, value = Dist.CLIENT)
public final class SpyglassDesignation {
    public static final int DWELL = 20;
    public static final double MAX_RANGE = 512;
    public static final double MIN_CONE = 2;
    public static final double FIRE_CONE = 5;
    public static final double HELMET_RANGE = 256;

    private static Candidate dwelling;
    private static int dwellTicks;
    private static boolean taught;

    private SpyglassDesignation() {
    }

    private record Candidate(int entityId, @Nullable UUID ship, Vec3 at, double radius) {
        boolean same(Candidate o) {
            return o != null && entityId == o.entityId && (ship == null ? o.ship == null : ship.equals(o.ship));
        }
    }

    private record Aboard(BattleReport report, @Nullable UUID ship, @Nullable AABB bounds) {
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) {
            reset();
            return;
        }
        while (BattleKeys.FIRE.consumeClick()) send(new BattleKeyPacket(BattleKeyPacket.FIRE, -1, "", false));
        while (BattleKeys.WEAPONS.consumeClick()) send(new BattleKeyPacket(BattleKeyPacket.TOGGLE_WEAPONS, -1, "", false));
        while (BattleKeys.DESIGNATE.consumeClick()) {
            Aboard a = aboard(mc, p);
            Candidate c = candidate(mc, p, a == null ? null : a.ship(), a == null ? null : a.bounds());
            if (c == null) p.displayClientMessage(Component.translatable("battle.create_stats.key.nothing"), true);
            else send(designate(c, false));
        }
        if (mc.screen != null || !p.isScoping()) {
            reset();
            return;
        }
        Aboard a = aboard(mc, p);
        if (a == null) {
            reset();
            return;
        }
        Candidate c = candidate(mc, p, a.ship(), a.bounds());
        if (c == null) {
            reset();
            return;
        }
        if (c.same(dwelling)) {
            dwellTicks++;
        } else {
            dwelling = c;
            dwellTicks = 1;
        }
        if (dwellTicks == DWELL && !isTarget(a.report(), c)) {
            send(designate(c, true));
            if (!taught) {
                taught = true;
                p.displayClientMessage(Component.translatable("battle.create_stats.spyglass.teach"), false);
            }
        }
    }

    @SubscribeEvent
    public static void attack(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isAttack()) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null || !p.isScoping()) return;
        Aboard a = aboard(mc, p);
        if (a == null) return;
        BattleReport.TargetLine t = a.report().target();
        if (!t.present() || t.lostSince() >= 0) return;
        Vec3 at = targetPos(mc, t);
        if (at == null) return;
        Vec3 eye = p.getEyePosition();
        if (angle(p.getViewVector(1f), at.subtract(eye)) > FIRE_CONE) return;
        send(new BattleKeyPacket(BattleKeyPacket.FIRE, -1, "", true));
        event.setCanceled(true);
        event.setSwingHand(false);
    }

    public static void renderRing(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (dwelling == null || dwellTicks <= 0 || mc.player == null || !mc.player.isScoping()) return;
        int cx = g.guiWidth() / 2;
        int cy = g.guiHeight() / 2;
        int n = 16;
        int lit = Math.min(n, dwellTicks * n / DWELL);
        boolean locked = dwellTicks >= DWELL;

        RenderSystem.depthMask(false);
        for (int i = 0; i < n; i++) {
            double a = 2 * Math.PI * i / n;
            int x = cx + (int) Math.round(Math.sin(a) * 14);
            int y = cy - (int) Math.round(Math.cos(a) * 14);
            int colour = locked ? 0xFFFF5B66 : i < lit ? 0xFF78FFC4 : 0x80A0A0A0;
            g.fill(x - 1, y - 1, x + 1, y + 1, colour);
        }
        g.flush();
        RenderSystem.depthMask(true);
    }

    @Nullable
    private static Aboard aboard(Minecraft mc, LocalPlayer p) {
        Object sub = SablePlots.shipContaining(mc.level, p.position(), 1.5);
        UUID ship = sub == null ? null : SableBridge.subLevelId(sub);
        if (ship != null) {
            String key = "s:" + ship;
            for (BattleScreenDisplayBE be : BattleScreenDisplayBE.CLIENT_LOADED) {
                if (!be.isRemoved() && key.equals(be.report().key())) return new Aboard(be.report(), ship, SablePlots.worldBounds(sub));
            }
        }
        for (BattleScreenDisplayBE be : BattleScreenDisplayBE.CLIENT_LOADED) {
            BattleReport r = be.report();
            if (be.isRemoved() || !r.ground()) continue;
            int half = r.groundSize() / 2;
            BlockPos b = be.getBlockPos();
            AABB box = new AABB(b.getX() - half, b.getY() - half, b.getZ() - half, b.getX() + half + 1, b.getY() + half + 1, b.getZ() + half + 1);
            if (box.contains(p.position())) return new Aboard(r, null, box);
        }
        ItemStack helmet = p.getItemBySlot(EquipmentSlot.HEAD);
        if (helmet.getItem() instanceof PilotHelmetItem) {
            PilotHelmetData.LinkedNode link = PilotHelmetItem.getData(helmet).getBattleBridgeLink();
            BlockPos pos = link == null ? null : link.getTargetPos();
            if (pos != null && SableBridge.getBlockEntityOnContraption(mc.level, pos) instanceof BattleScreenDisplayBE be) {
                Vec3 w = SableBridge.worldPointOf(be, Vec3.atCenterOf(pos));
                if (w.distanceTo(p.position()) <= HELMET_RANGE) {
                    UUID s = null;
                    String key = be.report().key();
                    if (key.startsWith("s:")) {
                        try {
                            s = UUID.fromString(key.substring(2));
                        } catch (IllegalArgumentException ignored) {
                        }
                    }
                    return new Aboard(be.report(), s, null);
                }
            }
        }
        return null;
    }

    @Nullable
    private static Candidate candidate(Minecraft mc, LocalPlayer p, @Nullable UUID ownShip, @Nullable AABB ownBounds) {
        Vec3 eye = p.getEyePosition();
        Vec3 look = p.getViewVector(1f);
        Candidate best = null;
        double bestAngle = Double.MAX_VALUE;
        AABB aboard = ownBounds == null ? null : ownBounds.inflate(1.5);
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof LivingEntity le) || e == p || e instanceof Player || !le.isAlive()) continue;
            Vec3 c = e.getBoundingBox().getCenter();
            double d = c.distanceTo(eye);
            if (d > MAX_RANGE || d < 1) continue;
            if (aboard != null && aboard.contains(c)) continue;
            double radius = e.getBbWidth() / 2 + 0.25;
            double cone = Math.max(MIN_CONE, Math.toDegrees(Math.atan(radius / d)));
            double a = angle(look, c.subtract(eye));
            if (a <= cone && a < bestAngle) {
                bestAngle = a;
                best = new Candidate(e.getId(), null, c, radius);
            }
        }
        for (Object sub : SableBridge.listSubLevels(mc.level)) {
            UUID id = SableBridge.subLevelId(sub);
            if (id == null || id.equals(ownShip)) continue;
            Vector3d c = SableBridge.subLevelWorldCenter(sub);
            AABB b = SablePlots.worldBounds(sub);
            if (c == null || b == null) continue;
            Vec3 at = new Vec3(c.x, c.y, c.z);
            if (aboard != null && aboard.intersects(b)) continue;
            double d = at.distanceTo(eye);
            if (d > MAX_RANGE || d < 1) continue;
            double radius = Math.max(b.getXsize(), Math.max(b.getYsize(), b.getZsize())) / 2;
            double cone = Math.max(MIN_CONE, Math.toDegrees(Math.atan(radius / d)));
            double a = angle(look, at.subtract(eye));
            if (a <= cone && a < bestAngle) {
                bestAngle = a;
                best = new Candidate(-1, id, at, radius);
            }
        }
        return best;
    }

    private static boolean isTarget(BattleReport r, Candidate c) {
        BattleReport.TargetLine t = r.target();
        if (!t.present()) return false;
        return c.ship() != null ? c.ship().toString().equals(t.shipId()) : c.entityId() == t.entityId();
    }

    @Nullable
    private static Vec3 targetPos(Minecraft mc, BattleReport.TargetLine t) {
        if (t.kind() == 1) {
            try {
                Object sub = SableBridge.findSubLevelById(mc.level, UUID.fromString(t.shipId()));
                Vector3d c = sub == null ? null : SableBridge.subLevelWorldCenter(sub);
                return c == null ? null : new Vec3(c.x, c.y, c.z);
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
        Entity e = mc.level.getEntity(t.entityId());
        return e == null ? null : e.getBoundingBox().getCenter();
    }

    private static double angle(Vec3 a, Vec3 b) {
        double la = a.length();
        double lb = b.length();
        if (la < 1e-9 || lb < 1e-9) return 180;
        double cos = a.dot(b) / (la * lb);
        return Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, cos))));
    }

    private static BattleKeyPacket designate(Candidate c, boolean spyglass) {
        return new BattleKeyPacket(BattleKeyPacket.DESIGNATE, c.entityId(), c.ship() == null ? "" : c.ship().toString(), spyglass);
    }

    private static void send(BattleKeyPacket packet) {
        PacketDistributor.sendToServer(packet);
    }

    private static void reset() {
        dwelling = null;
        dwellTicks = 0;
    }
}
