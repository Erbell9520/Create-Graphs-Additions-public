package com.createstats.guard;

import com.createstats.CreateStats;
import com.createstats.flight.bridge.ForeignHandles;
import com.createstats.vccmk4.core.FaultRule;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

public final class BlockFault {
    static final double TELL_RADIUS = 64.0;

    static final double GIVE_RADIUS = 16.0;

    private static final Map<BlockEntity, FaultRule> RULES = Collections.synchronizedMap(new WeakHashMap<>());

    private static final Set<BlockEntity> GONE = Collections.newSetFromMap(Collections.synchronizedMap(new WeakHashMap<>()));

    private BlockFault() {
    }

    public static void tick(String name, BlockEntity be, Runnable tick) {
        if (be.isRemoved() || GONE.contains(be)) return;
        if (!(be.getLevel() instanceof ServerLevel server)) {
            tick.run();
            return;
        }
        FaultRule rule = RULES.computeIfAbsent(be, k -> new FaultRule());
        long t0 = System.nanoTime();
        try {
            tick.run();
        } catch (Throwable e) {
            if (ForeignHandles.fatal(e)) throw e;
            leave(server, name, be, e, null);
            return;
        }
        long took = System.nanoTime() - t0;
        if (rule.overBudget(took)) {
            leave(server, name, be, null, "its tick held the server for " + FaultRule.seconds(took)
                    + (rule.slowRun() > 1 ? ", " + rule.slowRun() + " ticks in a row" : ""));
        }
    }

    public static boolean isTicking(BlockEntity be) {
        if (be == null || be.isRemoved() || GONE.contains(be) || !(be.getLevel() instanceof ServerLevel)) return false;
        synchronized (RULES) {
            return RULES.containsKey(be);
        }
    }

    static List<BlockEntity> loaded() {
        List<BlockEntity> out = new ArrayList<>();
        synchronized (RULES) {
            for (BlockEntity be : RULES.keySet()) {
                if (isTicking(be)) out.add(be);
            }
        }
        return out;
    }

    private static void leave(ServerLevel server, String name, BlockEntity be, @Nullable Throwable error,
                              @Nullable String reason) {
        if (!GONE.add(be)) return;

        try {
            depart(server, name, be, error, reason);
        } catch (Throwable t) {
            if (ForeignHandles.fatal(t)) throw t;
            CreateStats.LOGGER.error("[Guard] the {} at {} is stopped, but its removal failed", name, be.getBlockPos(), t);
        }
    }

    private static void depart(ServerLevel server, String name, BlockEntity be, @Nullable Throwable error,
                               @Nullable String reason) {
        BlockPos pos = be.getBlockPos().immutable();
        BlockState state = be.getBlockState();
        Vec3 at = Vec3.atCenterOf(pos);
        String why = error != null ? FaultRule.summary(error) : reason == null ? "removed" : reason;
        if (error != null) {
            CreateStats.LOGGER.error("[Guard] {} at {} failed and removes itself to keep the server up: {}",
                    name, pos, why, error);
        } else {
            CreateStats.LOGGER.warn("[Guard] {} at {} removes itself: {}", name, pos, why);
        }
        tell(server, name, at, error != null, why);

        server.getServer().tell(new TickTask(server.getServer().getTickCount(),
                () -> remove(server, name, be, pos, state, at)));
    }

    private static void tell(ServerLevel server, String name, Vec3 at, boolean failed, String why) {
        MutableComponent line = Component.literal("[Create: S&P] " + name + (failed
                ? " hit an error and removed itself so the server stays up. Error: " + why
                : " removed itself: " + why)).withStyle(ChatFormatting.RED);
        for (ServerPlayer p : server.players()) {
            if (p.position().distanceTo(at) <= TELL_RADIUS) p.sendSystemMessage(line);
        }
    }

    private static void remove(ServerLevel server, String name, BlockEntity be, BlockPos pos, BlockState state, Vec3 at) {
        try {
            if (server.getBlockEntity(pos) != be) return;
            if (!server.removeBlock(pos, false)) return;
        } catch (Throwable e) {
            if (ForeignHandles.fatal(e)) throw e;
            CreateStats.LOGGER.error("[Guard] could not remove the {} at {}; it stays stopped for this session", name, pos, e);
            return;
        }

        try {
            ItemStack stack = new ItemStack(state.getBlock().asItem());
            if (stack.isEmpty()) return;
            ServerPlayer to = nearest(server, at);
            if (to != null) {
                if (!to.getInventory().add(stack)) to.drop(stack, false);
                return;
            }
            ItemEntity item = new ItemEntity(server, at.x, at.y + 0.5, at.z, stack);
            item.setDefaultPickUpDelay();
            server.addFreshEntity(item);
        } catch (Throwable e) {
            if (ForeignHandles.fatal(e)) throw e;
            CreateStats.LOGGER.error("[Guard] the {} at {} is gone, but its item could not be handed back", name, pos, e);
        }
    }

    @Nullable
    private static ServerPlayer nearest(ServerLevel server, Vec3 at) {
        ServerPlayer best = null;
        double bestDist = GIVE_RADIUS;
        for (ServerPlayer p : server.players()) {
            if (p.isSpectator()) continue;
            double d = p.position().distanceTo(at);
            if (d <= bestDist) {
                bestDist = d;
                best = p;
            }
        }
        return best;
    }
}
