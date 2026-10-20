package com.createstats.surveyor.client;

import com.createstats.registry.ModSounds;
import com.createstats.vccmk4.client.PathwayBoardScreen;

import com.createstats.surveyor.PathwayOps;
import com.createstats.surveyor.SurveyorStaffItem;
import com.createstats.surveyor.core.PathNode;
import com.createstats.surveyor.core.PathKind;
import com.createstats.surveyor.core.Pathway;
import com.createstats.surveyor.core.SegmentCheck;
import com.createstats.surveyor.core.StrokeSimplify;
import com.createstats.surveyor.network.RouteActionPacket;
import com.createstats.surveyor.network.RoutePickedPacket;
import com.createstats.surveyor.network.SurveyorEditPacket;
import com.createstats.surveyor.network.SurveyorViewPacket;
import com.simibubi.create.AllItems;
import com.simibubi.create.AllKeys;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// vanilla asks the block before the item, so a chest eats the click
@EventBusSubscriber(modid = "create_stats", value = Dist.CLIENT)
public final class StaffInput {
    static final int CLICK_TICKS = 8;
    static final double CLICK_TRAVEL = 1.5;
    static final double DRAG_START = 0.35;
    static final int PING_TICKS = 20;
    static final int UNDO_OFFER_TICKS = 100;

    static final int LOOK_GRACE = 30;

    private enum Gesture {
        NONE, STROKE, PRESS_NODE, DRAG_NODE, PRESS_HANDLE, DRAG_HANDLE, BRUSH
    }

    private static List<String> undoNames = List.of();
    private static List<String> redoNames = List.of();

    public record Move(UUID pathway, int fromIndex, int toIndex, Vec3 anchor, Vec3 offset) {
    }

    @Nullable private static Move move;

    @Nullable private static UUID stretchPathway;
    private static int stretchNode = -1;

    private static StaffMode mode = StaffMode.SURVEY;
    @Nullable private static UUID selected;
    private static StaffPicking.Pick hover = StaffPicking.Pick.NONE;
    private static Gesture gesture = Gesture.NONE;
    private static int gestureTicks;
    private static boolean gestureSneak;
    private static final List<double[]> stroke = new ArrayList<>();

    private static double strokeTravel;
    @Nullable private static UUID gPathway;
    private static long gVersion;
    private static int gNode = -1;
    @Nullable private static Vec3 gStart;

    @Nullable private static Vec3 gPressGround;

    private static boolean gFromNode;
    @Nullable private static Vec3 dragTo;

    @Nullable private static UUID linkVehicle;
    private static String linkName = "";
    @Nullable private static Vec3 linkAt;

    @Nullable private static UUID pinned;
    private static long undoOfferedAt = Long.MIN_VALUE;
    private static int pingTicks;

    private static boolean toldLooking;
    private static boolean toldBoard;
    private static int notLookingTicks;
    private static long modeChangedAt = Long.MIN_VALUE;

    private static boolean attackLatched;
    private static boolean useLatched;

    private StaffInput() {
    }

    public static StaffMode mode() {
        return mode;
    }

    public static void setMode(StaffMode m) {
        if (m == mode) return;
        mode = m;
        cancelGesture();
        Minecraft mc = Minecraft.getInstance();
        modeChangedAt = mc.level == null ? 0 : mc.level.getGameTime();
        sound(ModSounds.SURVEYOR_STAFF_MODE.get(), 1.0f, 0.45f);
    }

    private static void sound(SoundEvent event, float pitch, float volume) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(event, pitch, volume));
    }

    public static long modeChangedAt() {
        return modeChangedAt;
    }

    @Nullable
    public static UUID selected() {
        return selected;
    }

    public static void select(@Nullable UUID id) {
        selected = id;
    }

    public static StaffPicking.Pick hover() {
        return hover;
    }

    @Nullable
    public static UUID linkVehicle() {
        return linkVehicle;
    }

    public static String linkName() {
        return linkName;
    }

    @Nullable
    public static Vec3 linkAt() {
        return linkAt;
    }

    @Nullable
    public static UUID pinned() {
        return pinned;
    }

    public static void pin(@Nullable UUID console) {
        pinned = console;
    }

    public static boolean undoOffered() {
        Minecraft mc = Minecraft.getInstance();

        if (mc.level == null || undoOfferedAt == Long.MIN_VALUE) return false;
        long since = mc.level.getGameTime() - undoOfferedAt;
        return since >= 0 && since < UNDO_OFFER_TICKS;
    }

    public record Preview(List<double[]> stroke, @Nullable UUID pathway, int node, @Nullable Vec3 dragTo, boolean insert) {
    }

    public static Preview preview() {
        return new Preview(List.copyOf(stroke), gPathway, gNode, dragTo,
                gesture == Gesture.DRAG_HANDLE);
    }

    @Nullable
    public static Vec3 paintingFrom() {
        if (gesture != Gesture.STROKE || stroke.isEmpty() || isClick(strokeTravel, gestureTicks)) return null;
        double[] a = stroke.get(0);
        return new Vec3(a[0], a[1], a[2]);
    }

    static boolean isClick(double travel, int ticks) {
        return travel < CLICK_TRAVEL || (ticks < CLICK_TICKS && travel < CLICK_TRAVEL * 2);
    }

    public static boolean holding(@Nullable Player p) {
        return p != null && p.getMainHandItem().getItem() instanceof SurveyorStaffItem;
    }

    public static void history(List<String> undo, List<String> redo) {
        undoNames = List.copyOf(undo);
        redoNames = List.copyOf(redo);
    }

    public static List<String> undoNames() {
        return undoNames;
    }

    public static List<String> redoNames() {
        return redoNames;
    }

    @Nullable
    public static Move move() {
        return move;
    }

    public static List<double[]> brush() {
        return gesture == Gesture.BRUSH ? List.copyOf(stroke) : List.of();
    }

    public static int stretchMark(UUID pathway) {
        return pathway != null && pathway.equals(stretchPathway) ? stretchNode : -1;
    }

    public static boolean goggles(@Nullable Player p) {
        return p != null && AllItems.GOGGLES.isIn(p.getItemBySlot(EquipmentSlot.HEAD));
    }

    public static double checkRadius() {
        if (selected != null) {
            for (PathwayClientCache.Vehicle v : PathwayClientCache.vehiclesOn(selected)) {
                if (v.now != null && v.now.minRadius() > 0.5f) return v.now.minRadius();
            }
        }
        return SegmentCheck.DEFAULT_RADIUS;
    }

    public static double checkHalfWidth() {
        if (selected != null) {
            for (PathwayClientCache.Vehicle v : PathwayClientCache.vehiclesOn(selected)) {
                if (v.now != null && v.now.halfWidth() > 0.4f) return v.now.halfWidth();
            }
        }
        return 1.5;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.level == null) {
            cancelGesture();
            return;
        }
        if (!mc.options.keyAttack.isDown()) attackLatched = false;
        if (!mc.options.keyUse.isDown()) useLatched = false;
        boolean holding = holding(player);
        boolean board = PathwayBoardScreen.isOpen();
        boolean looking = holding || board || (goggles(player) && SurveyorClientOptions.gogglesShow())
                || SurveyorClientOptions.alwaysShow() || RouteSight.wantsPathways(player);
        if (looking) {
            notLookingTicks = 0;
            if (--pingTicks <= 0 || !toldLooking || board != toldBoard) {
                pingTicks = PING_TICKS;
                toldLooking = true;
                toldBoard = board;
                PacketDistributor.sendToServer(new SurveyorViewPacket(true, board));
            }
        } else if (toldLooking && ++notLookingTicks >= LOOK_GRACE) {
            toldLooking = false;
            toldBoard = false;
            PacketDistributor.sendToServer(new SurveyorViewPacket(false, false));
        }
        if (!holding || mc.screen != null) {
            if (gesture != Gesture.NONE) finishGesture(mc, mc.screen == null);
            hover = StaffPicking.Pick.NONE;

            if (!holding) move = null;
            return;
        }
        hover = StaffPicking.pick(mc, 1.0f, mode == StaffMode.EDIT, mode == StaffMode.SURVEY || mode == StaffMode.EDIT ? null : null,
                mode == StaffMode.WATCH || mode == StaffMode.LINK);
        if (move != null) followMove(mc);
        if (gesture != Gesture.NONE) {
            gestureTicks++;
            if (!mc.options.keyUse.isDown()) finishGesture(mc, true);
            else updateGesture(mc);
        }
    }

    @SubscribeEvent
    public static void onInteraction(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (!holding(player) || event.getHand() != net.minecraft.world.InteractionHand.MAIN_HAND) return;
        if (event.isPickBlock()) {
            event.setCanceled(true);
            event.setSwingHand(false);
            middleClick(mc, player);
            return;
        }
        if (event.isAttack()) {
            event.setCanceled(true);
            event.setSwingHand(false);
            if (!attackLatched) {
                attackLatched = true;
                leftClick(mc, player);
            }
            return;
        }
        if (!event.isUseItem()) return;
        if (gesture != Gesture.NONE || useLatched) {
            event.setCanceled(true);
            event.setSwingHand(false);
            return;
        }
        if (rightClick(mc, player)) {
            useLatched = true;
            event.setCanceled(true);
            event.setSwingHand(true);
        }
    }

    private static boolean rightClick(Minecraft mc, Player player) {
        boolean sneak = player.isShiftKeyDown();
        if (move != null) {
            placeMove(mc);
            return true;
        }
        StaffPicking.Pick pick = StaffPicking.pick(mc, 1.0f, mode == StaffMode.EDIT, null,
                mode == StaffMode.WATCH || mode == StaffMode.LINK);
        boolean air = mc.hitResult == null || mc.hitResult.getType() == HitResult.Type.MISS;

        if (air && (pick.kind() == StaffPicking.Kind.NONE || (sneak && pick.kind() == StaffPicking.Kind.GROUND))) {
            PathwayBoardScreen.open(selected, null, null);
            return true;
        }
        switch (mode) {
            case SURVEY -> {
                if (pick.kind() != StaffPicking.Kind.NODE) return false;
                PathwayClientCache.Entry e = PathwayClientCache.get(pick.pathway());
                if (e == null) return true;
                Pathway p = e.pathway;
                if (!p.id().equals(selected)) {
                    select(p.id());
                    tell(mc, Component.translatable("create_stats.surveyor.selected", p.name()));
                    return true;
                }
                int index = p.indexOf(pick.nodeId());
                if (index == 0 && p.nodes().size() >= 3 && p.kind() != PathKind.LOOP) {
                    send(SurveyorEditPacket.of(SurveyorEditPacket.CLOSE_LOOP, p.id(), p.version()));
                    sound(ModSounds.SURVEYOR_STAFF_LOOP.get(), 1.0f, 0.5f);
                    return true;
                }
                // start the stroke at the node itself, vanilla sent it to the ground behind the stake

                beginStroke(pick.point(), sneak);
                gFromNode = true;
                return true;
            }
            case EDIT -> {
                if (mc.options.keySprint.isDown()) {
                    PathwayClientCache.Entry target = PathwayClientCache.get(pick.pathway() != null ? pick.pathway() : selected);
                    if (target == null) {
                        tell(mc, Component.translatable("create_stats.surveyor.brush.no_line"));
                        return true;
                    }
                    select(target.pathway.id());
                    beginGesture(Gesture.BRUSH, sneak);
                    gPathway = target.pathway.id();
                    gVersion = target.pathway.version();
                    Vec3 g = StaffPicking.groundAhead(mc, 1.0f);
                    if (g != null) stroke.add(new double[]{g.x, g.y, g.z});
                    return true;
                }
                if (pick.kind() == StaffPicking.Kind.NODE || pick.kind() == StaffPicking.Kind.HANDLE) {
                    PathwayClientCache.Entry e = PathwayClientCache.get(pick.pathway());
                    if (e == null) return true;
                    select(e.pathway.id());
                    beginGesture(pick.kind() == StaffPicking.Kind.NODE ? Gesture.PRESS_NODE : Gesture.PRESS_HANDLE, sneak);
                    gPathway = e.pathway.id();
                    gVersion = e.pathway.version();
                    gNode = pick.nodeId();
                    gStart = pick.point();
                    gPressGround = StaffPicking.groundAhead(mc, 1.0f);

                    dragTo = pick.kind() == StaffPicking.Kind.HANDLE ? pick.point().subtract(0.0, 0.35, 0.0) : null;
                    return true;
                }
                if (pick.kind() == StaffPicking.Kind.SEGMENT) {
                    select(pick.pathway());
                    return true;
                }
                return false;
            }
            case WATCH -> {
                if (pick.kind() == StaffPicking.Kind.VEHICLE && pick.vehicle() != null) {
                    if (pick.vehicle().equals(pinned)) {
                        PathwayBoardScreen.open(pick.pathway(), pick.vehicle(), null);
                    } else {
                        pinned = pick.vehicle();
                    }
                    return true;
                }
                if (pick.kind() == StaffPicking.Kind.NODE || pick.kind() == StaffPicking.Kind.SEGMENT) {
                    select(pick.pathway());
                    return true;
                }
                return false;
            }
            case LINK -> {
                if ((pick.kind() == StaffPicking.Kind.NODE || pick.kind() == StaffPicking.Kind.SEGMENT) && linkVehicle != null) {
                    PacketDistributor.sendToServer(new RouteActionPacket(RouteActionPacket.ASSIGN, linkVehicle, 0L, pick.pathway(), 0));
                    sound(ModSounds.SURVEYOR_STAFF_LINK.get(), 1.0f, 0.5f);
                    linkVehicle = null;
                    linkAt = null;
                    return true;
                }
                if (pick.kind() == StaffPicking.Kind.VEHICLE && pick.vehicle() != null) {
                    if (sneak) PacketDistributor.sendToServer(RouteActionPacket.to(RouteActionPacket.UNASSIGN, pick.vehicle()));
                    else pickedLocally(pick.vehicle(), pick.point());
                    return true;
                }

                if (mc.hitResult instanceof BlockHitResult bh && bh.getType() == HitResult.Type.BLOCK) {
                    PacketDistributor.sendToServer(RouteActionPacket.at(RouteActionPacket.PICK, bh.getBlockPos().asLong(), sneak ? 1 : 0));
                    return true;
                }
                return false;
            }
        }
        return false;
    }

    public static void useOnBlock(UseOnContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (mode != StaffMode.SURVEY || gesture != Gesture.NONE) return;
        Vec3 g = StaffPicking.groundFrom(mc, new BlockHitResult(ctx.getClickLocation(), ctx.getClickedFace(), ctx.getClickedPos(), ctx.isInside()));
        if (g != null) beginStroke(g, ctx.getPlayer() != null && ctx.getPlayer().isShiftKeyDown());
    }

    public static void useAir(Player player) {
        Minecraft mc = Minecraft.getInstance();
        if (mode != StaffMode.SURVEY || gesture != Gesture.NONE) return;
        Vec3 g = StaffPicking.groundAhead(mc, 1.0f);
        if (g != null) beginStroke(g, player.isShiftKeyDown());
    }

    private static void beginStroke(Vec3 at, boolean sneak) {
        beginGesture(Gesture.STROKE, sneak);
        stroke.add(new double[]{at.x, at.y, at.z});
        PathwayClientCache.Entry e = PathwayClientCache.get(selected);
        gPathway = e == null ? null : e.pathway.id();
        gVersion = e == null ? 0L : e.pathway.version();
        gStart = at;
    }

    private static void beginGesture(Gesture g, boolean sneak) {
        gesture = g;
        gestureTicks = 0;
        gestureSneak = sneak;
        gFromNode = false;
        stroke.clear();
        strokeTravel = 0.0;
        dragTo = null;
    }

    private static void updateGesture(Minecraft mc) {
        switch (gesture) {
            case STROKE, BRUSH -> {
                Vec3 g = StaffPicking.groundAhead(mc, 1.0f);
                if (g == null || stroke.size() >= 4000) return;
                double[] last = stroke.isEmpty() ? null : stroke.get(stroke.size() - 1);
                double d2 = last == null ? 1.0 : (g.x - last[0]) * (g.x - last[0]) + (g.z - last[2]) * (g.z - last[2]);

                if (d2 >= (gesture == Gesture.BRUSH ? 0.25 : 0.0625)) {
                    stroke.add(new double[]{g.x, g.y, g.z});
                    if (last != null) strokeTravel += Math.sqrt(d2);
                }
            }
            case PRESS_NODE, PRESS_HANDLE -> {
                Vec3 g = StaffPicking.groundAhead(mc, 1.0f);
                if (g != null && (gPressGround == null || g.distanceTo(gPressGround) > DRAG_START)) {
                    gesture = gesture == Gesture.PRESS_NODE ? Gesture.DRAG_NODE : Gesture.DRAG_HANDLE;
                    dragTo = snapped(mc, g, gesture == Gesture.DRAG_NODE ? gPathway : null, gNode);
                }
            }
            case DRAG_NODE, DRAG_HANDLE -> {
                Vec3 g = StaffPicking.groundAhead(mc, 1.0f);
                if (g != null) dragTo = snapped(mc, g, gesture == Gesture.DRAG_NODE ? gPathway : null, gNode);
            }
            default -> {
            }
        }
    }

    private static Vec3 snapped(Minecraft mc, Vec3 at, @Nullable UUID pathway, int node) {
        Vec3 look = mc.player == null ? new Vec3(0, 0, 1) : mc.player.getViewVector(1.0f);
        return StaffSnap.apply(mc.level, at, look, pathway, node);
    }

    private static void finishGesture(Minecraft mc, boolean commit) {
        Gesture g = gesture;
        gesture = Gesture.NONE;
        if (!commit) {
            cancelGesture();
            return;
        }
        switch (g) {
            case STROKE -> finishStroke(mc);
            case BRUSH -> finishBrush(mc);
            case PRESS_NODE -> {
                if (gestureSneak) {
                    GhostRun.start(gPathway, gNode);
                } else if (gPathway != null) {
                    NodeCardScreen.open(gPathway, gNode);
                }
            }
            case DRAG_NODE -> {
                if (dragTo != null && gPathway != null) {
                    send(SurveyorEditPacket.at(SurveyorEditPacket.MOVE_NODE, gPathway, currentVersion(gPathway), gNode, dragTo.x, dragTo.y, dragTo.z));
                    sound(ModSounds.SURVEYOR_STAFF_STAKE.get(), 1.1f, 0.45f);
                }
            }
            case PRESS_HANDLE, DRAG_HANDLE -> {
                if (dragTo != null && gPathway != null) {
                    send(SurveyorEditPacket.at(SurveyorEditPacket.INSERT_AFTER, gPathway, currentVersion(gPathway), gNode, dragTo.x, dragTo.y, dragTo.z));
                    sound(ModSounds.SURVEYOR_STAFF_STAKE.get(), 1.1f, 0.45f);
                }
            }
            default -> {
            }
        }
        cancelGesture();
    }

    private static void finishStroke(Minecraft mc) {
        if (stroke.isEmpty()) return;
        double travel = 0.0;
        for (int i = 1; i < stroke.size(); i++) {
            travel += Math.hypot(stroke.get(i)[0] - stroke.get(i - 1)[0], stroke.get(i)[2] - stroke.get(i - 1)[2]);
        }
        double[] at = stroke.get(0);
        PathwayClientCache.Entry e = PathwayClientCache.get(selected);
        UUID id = e == null ? SurveyorEditPacket.NONE : e.pathway.id();
        long version = e == null ? 0L : e.pathway.version();
        if (isClick(travel, gestureTicks)) {
            if (gFromNode) return;
            Vec3 s = snapped(mc, new Vec3(at[0], at[1], at[2]), null, -1);
            at = new double[]{s.x, s.y, s.z};
            if (e == null) {
                send(SurveyorEditPacket.at(SurveyorEditPacket.CREATE, SurveyorEditPacket.NONE, 0L, -1, at[0], at[1], at[2]));
            } else if (gestureSneak) {
                send(SurveyorEditPacket.at(SurveyorEditPacket.END_ONE_WAY, id, version, -1, at[0], at[1], at[2]));
                select(null);
            } else {
                send(SurveyorEditPacket.at(SurveyorEditPacket.APPEND, id, version, -1, at[0], at[1], at[2]));
            }
            sound(ModSounds.SURVEYOR_STAFF_STAKE.get(), 1.0f, 0.5f);
            return;
        }

        List<double[]> kept = StrokeSimplify.simplify(stroke, StrokeSimplify.EPSILON, StrokeSimplify.MIN_SPACING);
        int backwards = 0;
        if (e != null && !e.pathway.nodes().isEmpty()) {
            PathNode first = e.pathway.nodes().get(0);
            PathNode last = e.pathway.nodes().get(e.pathway.nodes().size() - 1);
            double toFirst = first.distanceSq(at[0], at[1], at[2]);
            double toLast = last.distanceSq(at[0], at[1], at[2]);
            if (toFirst < 4.0 && toFirst < toLast && e.pathway.nodes().size() > 1) backwards = 1;

            if (backwards == 1 ? toFirst < 4.0 : toLast < 4.0) kept.remove(0);
        }
        if (kept.isEmpty()) return;
        send(new SurveyorEditPacket(SurveyorEditPacket.APPEND_STROKE, id, version, -1, 0, 0, 0, backwards, "",
                PathwayOps.encodePoints(kept)));
        sound(ModSounds.SURVEYOR_STAFF_CHALK.get(), 1.0f, 0.5f);
    }

    private static void finishBrush(Minecraft mc) {
        if (stroke.isEmpty() || gPathway == null) return;
        List<double[]> pts = new ArrayList<>(stroke);

        while (pts.size() > 200) {
            List<double[]> half = new ArrayList<>(pts.size() / 2 + 1);
            for (int i = 0; i < pts.size(); i += 2) half.add(pts.get(i));
            pts = half;
        }
        send(new SurveyorEditPacket(SurveyorEditPacket.SMOOTH, gPathway, currentVersion(gPathway), -1,
                com.createstats.surveyor.core.PathEdits.BRUSH_RADIUS, 0, 0, 0, "", PathwayOps.encodePoints(pts)));
        sound(ModSounds.SURVEYOR_STAFF_CHALK.get(), 0.8f, 0.4f);
    }

    private static void middleClick(Minecraft mc, Player player) {
        if (player.isShiftKeyDown()) {
            send(SurveyorEditPacket.of(SurveyorEditPacket.REDO, SurveyorEditPacket.NONE, 0L));
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f, 0.3f));
            return;
        }
        if (move != null) return;
        StaffPicking.Pick pick = StaffPicking.pick(mc, 1.0f, false, null, false);
        if ((pick.kind() != StaffPicking.Kind.NODE && pick.kind() != StaffPicking.Kind.SEGMENT) || pick.pathway() == null) return;
        PathwayClientCache.Entry e = PathwayClientCache.get(pick.pathway());
        if (e == null || e.pathway.node(pick.nodeId()) == null) return;
        select(e.pathway.id());
        Pathway p = e.pathway;
        List<com.createstats.vccmk4.console.NodeRadial.Page> pages = com.createstats.vccmk4.console.NodeRadial.forNode(
                StaffRadialScreen.HOST, p, pick.nodeId(), SurveyorClientOptions.snap().key, stretchMark(p.id()));
        int page = pick.kind() == StaffPicking.Kind.NODE ? com.createstats.vccmk4.console.NodeRadial.STOP_PAGE
                : com.createstats.vccmk4.console.NodeRadial.LINE_PAGE;
        mc.setScreen(new StaffRadialScreen(new com.createstats.vccmk4.console.NodeRadial(p.label(pick.nodeId()), p.name(),
                pages, page), p.id(), pick.nodeId()));
    }

    static void radial(String id, UUID pathway, int nodeId) {
        Minecraft mc = Minecraft.getInstance();
        PathwayClientCache.Entry e = PathwayClientCache.get(pathway);
        if (e == null || e.pathway.node(nodeId) == null) return;
        Pathway p = e.pathway;
        PathNode n = p.node(nodeId);
        long v = p.version();
        int colon = id.indexOf(':');
        String what = colon < 0 ? id : id.substring(0, colon);
        String arg = colon < 0 ? "" : id.substring(colon + 1);
        switch (what) {
            case "stop" -> {
                int seconds = Integer.parseInt(arg);
                if (seconds <= 0) {
                    send(SurveyorEditPacket.bytes(SurveyorEditPacket.SET_STOP, p.id(), v, nodeId, new byte[0]));
                } else {
                    com.createstats.surveyor.core.Stop base = n.stop() == null ? com.createstats.surveyor.core.Stop.DEFAULT : n.stop();
                    com.createstats.surveyor.core.Stop s = base.withRules(List.of(List.of(new com.createstats.surveyor.core.StopCondition.Wait(seconds))));
                    send(SurveyorEditPacket.bytes(SurveyorEditPacket.SET_STOP, p.id(), v, nodeId,
                            com.createstats.surveyor.core.PathwayCodec.encodeStop(s)));
                }
                sound(ModSounds.SURVEYOR_STAFF_STAKE.get(), 1.25f, 0.4f);
            }
            case "card" -> NodeCardScreen.open(p.id(), nodeId);
            case "limit" -> send(SurveyorEditPacket.value(SurveyorEditPacket.SET_SPEED, p.id(), v, nodeId, Integer.parseInt(arg)));
            case "corner" -> {
                for (com.createstats.surveyor.core.Corner c : com.createstats.surveyor.core.Corner.values()) {
                    if (c.key.equals(arg)) send(SurveyorEditPacket.arg(SurveyorEditPacket.SET_CORNER, p.id(), v, nodeId, c.ordinal()));
                }
            }
            case "split" -> send(SurveyorEditPacket.arg(SurveyorEditPacket.SPLIT, p.id(), v, nodeId, 0));
            case "join" -> send(SurveyorEditPacket.arg(SurveyorEditPacket.JOIN, p.id(), v, nodeId, 0));
            case "junction" -> send(SurveyorEditPacket.arg(SurveyorEditPacket.MAKE_JUNCTION, p.id(), v, nodeId, 0));
            case "delete" -> {
                send(SurveyorEditPacket.arg(SurveyorEditPacket.DELETE_NODE, p.id(), v, nodeId, 0));
                undoOfferedAt = mc.level == null ? 0 : mc.level.getGameTime();
                sound(ModSounds.SURVEYOR_STAFF_DELETE.get(), 1.0f, 0.5f);
            }
            case "reverse" -> send(SurveyorEditPacket.of(SurveyorEditPacket.REVERSE, p.id(), v));
            case "duplicate" -> send(SurveyorEditPacket.of(SurveyorEditPacket.DUPLICATE, p.id(), v));
            case "simplify" -> send(SurveyorEditPacket.of(SurveyorEditPacket.SIMPLIFY, p.id(), v));
            case "fit" -> send(SurveyorEditPacket.of(SurveyorEditPacket.FAIR, p.id(), v));
            case "move_line" -> armMove(mc, p, 0, p.nodes().size() - 1, n);
            case "move_from" -> {
                stretchPathway = p.id();
                stretchNode = nodeId;
                tell(mc, Component.translatable("create_stats.surveyor.move.marked", p.label(nodeId), p.name()));
            }
            case "move_to" -> {
                int from = p.indexOf(stretchNode);
                int to = p.indexOf(nodeId);
                stretchPathway = null;
                stretchNode = -1;
                if (from < 0 || to < 0) return;
                if (!p.closed() && to < from) {
                    int t = from;
                    from = to;
                    to = t;
                }
                armMove(mc, p, from, to, n);
            }
            case "snap" -> {
                SurveyorClientOptions.snap(SurveyorClientOptions.Snap.byKey(arg));
                tell(mc, Component.translatable("create_stats.surveyor.snap.now",
                        Component.translatable("create_stats.surveyor.radial.snap." + arg)));
            }
            default -> {
            }
        }
    }

    private static void armMove(Minecraft mc, Pathway p, int from, int to, PathNode anchor) {
        if (p.nodes().isEmpty()) return;
        move = new Move(p.id(), from, to, new Vec3(anchor.x(), anchor.y(), anchor.z()), Vec3.ZERO);
        boolean whole = from == 0 && to == p.nodes().size() - 1;
        tell(mc, Component.translatable(whole ? "create_stats.surveyor.move.armed_line" : "create_stats.surveyor.move.armed_stretch",
                p.name(), p.label(p.nodes().get(from).id()), p.label(p.nodes().get(to).id())));
    }

    private static void followMove(Minecraft mc) {
        PathwayClientCache.Entry e = PathwayClientCache.get(move.pathway());
        if (e == null || move.toIndex() >= e.pathway.nodes().size()) {
            move = null;
            return;
        }
        Vec3 g = StaffPicking.groundAhead(mc, 1.0f);
        if (g == null) return;
        Vec3 off = g.subtract(move.anchor());
        if (SurveyorClientOptions.snap() == SurveyorClientOptions.Snap.GRID) {
            off = new Vec3(Math.round(off.x), Math.round(off.y), Math.round(off.z));
        }
        double len = Math.hypot(off.x, off.z);
        if (len > PathwayOps.MAX_MOVE) off = off.scale(PathwayOps.MAX_MOVE / len);
        move = new Move(move.pathway(), move.fromIndex(), move.toIndex(), move.anchor(), off);
    }

    private static void placeMove(Minecraft mc) {
        Move m = move;
        move = null;
        PathwayClientCache.Entry e = PathwayClientCache.get(m.pathway());
        if (e == null || m.offset().lengthSqr() < 1.0e-4) return;
        Pathway p = e.pathway;
        boolean whole = m.fromIndex() == 0 && m.toIndex() == p.nodes().size() - 1;
        int fromId = whole ? -1 : p.nodes().get(m.fromIndex()).id();
        int toId = whole ? 0 : p.nodes().get(m.toIndex()).id();
        send(new SurveyorEditPacket(SurveyorEditPacket.MOVE_LINE, p.id(), p.version(), fromId, m.offset().x, m.offset().y,
                m.offset().z, toId, "", null));
        sound(ModSounds.SURVEYOR_STAFF_STAKE.get(), 0.9f, 0.5f);
    }

    private static void leftClick(Minecraft mc, Player player) {
        if (move != null) {
            move = null;
            tell(mc, Component.translatable("create_stats.surveyor.move.let_go"));
            return;
        }
        if (player.isShiftKeyDown()) {
            send(SurveyorEditPacket.of(SurveyorEditPacket.UNDO, SurveyorEditPacket.NONE, 0L));
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 0.8f, 0.3f));
            return;
        }
        StaffPicking.Pick pick = StaffPicking.pick(mc, 1.0f, false, null, false);
        if (pick.kind() != StaffPicking.Kind.NODE) return;
        PathwayClientCache.Entry e = PathwayClientCache.get(pick.pathway());
        if (e == null) return;
        send(SurveyorEditPacket.arg(SurveyorEditPacket.DELETE_NODE, e.pathway.id(), e.pathway.version(), pick.nodeId(), 0));
        undoOfferedAt = mc.level == null ? 0 : mc.level.getGameTime();
        sound(ModSounds.SURVEYOR_STAFF_DELETE.get(), 1.0f, 0.5f);
    }

    @SubscribeEvent
    public static void onScroll(InputEvent.MouseScrollingEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (!holding(mc.player) || mc.screen != null || event.getScrollDeltaY() == 0.0) return;
        int dir = event.getScrollDeltaY() > 0 ? 1 : -1;
        if (AllKeys.TOOL_MENU.isPressed()) {
            event.setCanceled(true);
            setMode(mode.next(-dir));
            return;
        }
        if (mode == StaffMode.EDIT && hover.kind() == StaffPicking.Kind.NODE && hover.pathway() != null) {
            event.setCanceled(true);
            PathwayClientCache.Entry e = PathwayClientCache.get(hover.pathway());
            if (e == null) return;
            boolean corner = mc.player.isShiftKeyDown();

            boolean stop = mc.options.keySprint.isDown();
            int op = stop ? SurveyorEditPacket.STEP_STOP : (corner ? SurveyorEditPacket.STEP_CORNER : SurveyorEditPacket.STEP_SPEED);
            send(SurveyorEditPacket.arg(op, e.pathway.id(), e.pathway.version(), hover.nodeId(), dir));
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_HAT.value(), dir > 0 ? 1.3f : 1.0f, 0.35f));
        }
    }

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getLevel().isClientSide && holding(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        PathwayClientCache.clear();
        cancelGesture();
        selected = null;
        linkVehicle = null;
        pinned = null;
        toldLooking = false;
        toldBoard = false;
        notLookingTicks = 0;
        attackLatched = false;
        useLatched = false;
        undoOfferedAt = Long.MIN_VALUE;
        modeChangedAt = Long.MIN_VALUE;
        move = null;
        stretchPathway = null;
        stretchNode = -1;
        undoNames = List.of();
        redoNames = List.of();
    }

    public static void picked(RoutePickedPacket p) {
        if (p.console().getMostSignificantBits() == 0 && p.console().getLeastSignificantBits() == 0) {
            linkVehicle = null;
            linkAt = null;
            return;
        }
        linkVehicle = p.console();
        linkName = p.name();
        linkAt = new Vec3(p.x(), p.y(), p.z());
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.CROSSBOW_LOADING_MIDDLE.value(), 1.5f, 0.5f));
    }

    private static void pickedLocally(UUID console, Vec3 at) {
        PathwayClientCache.Vehicle v = PathwayClientCache.vehicle(console);
        linkVehicle = console;
        linkName = v != null && v.now != null ? v.now.name() : "";
        linkAt = at;
    }

    private static long currentVersion(UUID pathway) {
        PathwayClientCache.Entry e = PathwayClientCache.get(pathway);
        return e == null ? gVersion : e.pathway.version();
    }

    private static void cancelGesture() {
        gesture = Gesture.NONE;
        stroke.clear();
        strokeTravel = 0.0;
        gPathway = null;
        gNode = -1;
        gStart = null;
        gPressGround = null;
        gFromNode = false;
        dragTo = null;
    }

    static void send(SurveyorEditPacket p) {
        PacketDistributor.sendToServer(p);
    }

    private static void tell(Minecraft mc, Component c) {
        if (mc.player != null) mc.player.displayClientMessage(c, true);
    }
}
