package com.createstats.vccmk4;

import com.createstats.CreateStats;
import com.createstats.flight.bridge.SableBridge;
import com.createstats.vccmk4.air.AerialBE;
import com.createstats.vccmk4.core.FaultRule;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.event.level.BlockEvent;
import org.joml.Vector3d;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

// a failing console takes itself off the ship instead of taking the server down
public final class ConsoleFault {
    static final double DESTROY_REACH = 8.0;

    static final double TELL_RADIUS = 64.0;

    static final double GIVE_RADIUS = 16.0;
    static final int RECENT_MAX = 5;

    private static final Map<BlockEntity, FaultRule> RULES = Collections.synchronizedMap(new WeakHashMap<>());

    private static final Set<BlockEntity> GONE = Collections.newSetFromMap(Collections.synchronizedMap(new WeakHashMap<>()));

    private static final Deque<String> RECENT = new ArrayDeque<>();

    private ConsoleFault() {
    }

    public static void tick(BlockEntity be, Runnable tick) {
        if (be.isRemoved() || GONE.contains(be)) return;
        if (!(be.getLevel() instanceof ServerLevel server)) {
            clientTick(be, tick);
            return;
        }
        FaultRule rule = RULES.computeIfAbsent(be, k -> new FaultRule());
        if (Mk4DisarmData.disarmed(server)) {
            leave(server, be, null, "VCC Mk IV consoles are disarmed on this server (/vcc4 arm allows them again)", null);
            return;
        }
        long t0 = System.nanoTime();
        try {
            tick.run();
        } catch (RuntimeException | LinkageError | StackOverflowError e) {
            leave(server, be, e, null, null);
            return;
        }
        long took = System.nanoTime() - t0;
        if (rule.overBudget(took)) {
            leave(server, be, null, "its tick held the server for " + FaultRule.seconds(took)
                    + (rule.slowRun() > 1 ? ", " + rule.slowRun() + " ticks in a row" : ""), null);
        }
    }

    private static void clientTick(BlockEntity be, Runnable tick) {
        try {
            tick.run();
        } catch (RuntimeException | LinkageError | StackOverflowError e) {
            GONE.add(be);
            CreateStats.LOGGER.error("[VCC Mk IV] client tick of the console at {} failed; it stops ticking on this client: {}",
                    be.getBlockPos(), FaultRule.summary(e), e);
        }
    }

    static List<BlockEntity> loaded() {
        List<BlockEntity> out = new ArrayList<>();
        synchronized (RULES) {
            for (BlockEntity be : RULES.keySet()) {
                if (be != null && !be.isRemoved() && !GONE.contains(be) && be.getLevel() instanceof ServerLevel) out.add(be);
            }
        }
        return out;
    }

    public static boolean isTicking(BlockEntity be) {
        return be != null && loaded().contains(be);
    }

    static List<String> recent() {
        synchronized (RECENT) {
            return new ArrayList<>(RECENT);
        }
    }

    static void leave(ServerLevel server, BlockEntity be, @Nullable Throwable error, @Nullable String reason,
                      @Nullable ServerPlayer giveTo) {
        if (!GONE.add(be)) return;
        BlockPos pos = be.getBlockPos().immutable();
        BlockState state = be.getBlockState();
        Vec3 world = worldPos(be);
        String why = error != null ? FaultRule.summary(error) : reason == null ? "removed" : reason;
        String where = String.format(java.util.Locale.ROOT, "%.0f %.0f %.0f", world.x, world.y, world.z);
        if (error != null) {
            CreateStats.LOGGER.error("[VCC Mk IV] {} at {} (world {}) failed and takes itself off its ship to keep the server up: {}",
                    nameOf(be), pos, where, why, error);
        } else {
            CreateStats.LOGGER.warn("[VCC Mk IV] {} at {} (world {}) takes itself off its ship: {}", nameOf(be), pos, where, why);
        }
        synchronized (RECENT) {
            RECENT.addLast(nameOf(be) + " at " + where + ": " + why);
            while (RECENT.size() > RECENT_MAX) RECENT.removeFirst();
        }
        if (giveTo == null) tell(server, world, state, error != null, why);
        server.getServer().execute(() -> remove(server, be, pos, state, world, giveTo));
    }

    private static void tell(ServerLevel server, Vec3 world, BlockState state, boolean failed, String why) {
        MutableComponent line = Component.literal("[VCC Mk IV] ").withStyle(ChatFormatting.RED)
                .append(state.getBlock().getName().copy().withStyle(ChatFormatting.RED))
                .append(Component.literal(failed
                        ? " hit an error and removed itself so the server stays up. Error: " + why
                        : " removed itself: " + why).withStyle(ChatFormatting.RED));
        for (ServerPlayer p : server.players()) {
            if (p.position().distanceTo(world) <= TELL_RADIUS) p.sendSystemMessage(line);
        }
    }

    private static void remove(ServerLevel server, BlockEntity be, BlockPos pos, BlockState state, Vec3 world,
                               @Nullable ServerPlayer giveTo) {
        try {
            if (server.getBlockEntity(pos) != be) return;
            if (!server.removeBlock(pos, false)) return;
        } catch (RuntimeException | LinkageError e) {
            CreateStats.LOGGER.error("[VCC Mk IV] could not remove the console at {}; it stays stopped for this session", pos, e);
            return;
        }
        ItemStack stack = new ItemStack(state.getBlock().asItem());
        if (stack.isEmpty()) return;
        ServerPlayer to = giveTo != null ? giveTo : nearest(server, world);
        if (to != null) {
            if (!to.getInventory().add(stack)) to.drop(stack, false);
            return;
        }
        ItemEntity item = new ItemEntity(server, world.x, world.y + 0.5, world.z, stack);
        item.setDefaultPickUpDelay();
        server.addFreshEntity(item);
    }

    @Nullable
    private static ServerPlayer nearest(ServerLevel server, Vec3 world) {
        ServerPlayer best = null;
        double bestDist = GIVE_RADIUS;
        for (ServerPlayer p : server.players()) {
            if (p.isSpectator()) continue;
            double d = p.position().distanceTo(world);
            if (d <= bestDist) {
                bestDist = d;
                best = p;
            }
        }
        return best;
    }

    static int[] destroyNear(ServerPlayer player) {
        int sent = 0;
        int refused = 0;
        if (!(player.level() instanceof ServerLevel server)) return new int[] {0, 0};
        for (BlockEntity be : loaded()) {
            if (be.getLevel() != server) continue;
            if (player.position().distanceTo(worldPos(be)) > DESTROY_REACH) continue;
            if (player.isSpectator() || !player.mayBuild()) {
                refused++;
                continue;
            }
            BlockEvent.BreakEvent event = CommonHooks.fireBlockBreak(server, player.gameMode.getGameModeForPlayer(), player,
                    be.getBlockPos(), be.getBlockState());
            if (event.isCanceled()) {
                refused++;
                continue;
            }
            leave(server, be, null, "sent away by " + player.getGameProfile().getName() + " (/vcc4 destroy)", player);
            sent++;
        }
        return new int[] {sent, refused};
    }

    static int destroyAll(String by) {
        int sent = 0;
        for (BlockEntity be : loaded()) {
            if (!(be.getLevel() instanceof ServerLevel server)) continue;
            leave(server, be, null, "sent away by " + by + " (/vcc4 destroy)", null);
            sent++;
        }
        return sent;
    }

    static String nameOf(BlockEntity be) {
        return be instanceof AerialBE ? "VCC Mk IV Aerial" : "VCC Mk IV";
    }

    static Vec3 worldPos(BlockEntity be) {
        BlockPos pos = be.getBlockPos();
        try {
            Object craft = SableBridge.subLevelAccessOf(be);
            Vector3d w = craft == null ? null : SableBridge.localToWorld(craft, Vcc4Scanner.centre(pos));
            if (w != null && Double.isFinite(w.x) && Double.isFinite(w.y) && Double.isFinite(w.z)) return new Vec3(w.x, w.y, w.z);
        } catch (RuntimeException | LinkageError e) {
        }
        return Vec3.atCenterOf(pos);
    }
}
