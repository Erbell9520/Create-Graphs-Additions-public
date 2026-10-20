package com.createstats.item;

import com.createstats.flight.bridge.HingeConstraintBridge;
import com.createstats.flight.hinge.HingeJoint;
import com.createstats.flight.hinge.HingeJointManager;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

public class HingeItem extends Item {
    private record Pending(UUID subId, Vec3 hit, boolean doorAxis) {}

    private record Pick(UUID subId, Vec3 hit) {}

    private static final Map<UUID, Pending> PENDING = new HashMap<>();

    private static final double PICK_REACH = 96.0;
    private static final double MAX_PICK_PERP = 24.0;
    private static final double MAX_LINK_DISTANCE = 8.0;

    public HingeItem(Item.Properties properties) {
        super(properties);
    }

    public static void clearPending(UUID playerId) {
        PENDING.remove(playerId);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide() || !(level instanceof ServerLevel serverLevel)) {
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        }
        if (!HingeConstraintBridge.isAvailable()) {
            msg(player, Component.translatable("message.create_stats.hinge.unavailable"), ChatFormatting.RED);
            return InteractionResultHolder.success(stack);
        }

        boolean sneaking = player.isShiftKeyDown();
        HingeJointManager manager = HingeJointManager.get(serverLevel);

        if (sneaking) {
            Pick removeTarget = pickByAnchor(serverLevel, player);
            if (removeTarget != null) {
                int removed = manager.removeJointsInvolving(removeTarget.subId);
                if (removed > 0) {
                    PENDING.remove(player.getUUID());
                    feedbackUnlink(serverLevel, removeTarget.hit);
                    msg(player, Component.translatable("message.create_stats.hinge.removed", removed,
                        Component.translatable(removed > 1 ? "message.create_stats.hinge.unit.plural" : "message.create_stats.hinge.unit.singular")), ChatFormatting.YELLOW);
                    return InteractionResultHolder.success(stack);
                }
            }
        }

        Pick pick = pick(serverLevel, player);

        if (pick == null) {
            if (sneaking && PENDING.remove(player.getUUID()) != null) {
                msg(player, Component.translatable("message.create_stats.hinge.anchor_cleared"), ChatFormatting.YELLOW);
            } else {
                msg(player, Component.translatable("message.create_stats.hinge.aim"), ChatFormatting.GRAY);
            }
            return InteractionResultHolder.success(stack);
        }

        boolean doorAxis = !sneaking;
        Pending pending = PENDING.get(player.getUUID());

        if (pending == null) {
            PENDING.put(player.getUUID(), new Pending(pick.subId, pick.hit, doorAxis));
            feedbackPick(serverLevel, pick.hit);
            msg(player, Component.translatable("message.create_stats.hinge.anchor_a",
                Component.translatable(doorAxis ? "create_stats.hinge.axis.door" : "create_stats.hinge.axis.fold")), ChatFormatting.AQUA);
            return InteractionResultHolder.success(stack);
        }

        if (pending.subId.equals(pick.subId)) {
            msg(player, Component.translatable("message.create_stats.hinge.same"), ChatFormatting.RED);
            return InteractionResultHolder.success(stack);
        }
        double gap = pending.hit.distanceTo(pick.hit);
        if (gap > MAX_LINK_DISTANCE) {
            msg(player, Component.translatable("message.create_stats.hinge.too_far", String.format("%.1f", gap)), ChatFormatting.RED);
            return InteractionResultHolder.success(stack);
        }

        boolean created = createHinge(serverLevel, pending, pick);
        PENDING.remove(player.getUUID());
        if (created) {
            feedbackLink(serverLevel, midpoint(pending.hit, pick.hit));
            msg(player, Component.translatable("message.create_stats.hinge.created",
                Component.translatable(pending.doorAxis ? "create_stats.hinge.axis.door" : "create_stats.hinge.axis.fold")), ChatFormatting.GOLD);
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
        } else {
            msg(player, Component.translatable("message.create_stats.hinge.failed"), ChatFormatting.RED);
        }
        return InteractionResultHolder.success(stack);
    }

    private static boolean createHinge(ServerLevel level, Pending a, Pick b) {
        Object subA = HingeConstraintBridge.subLevelById(level, a.subId);
        Object subB = HingeConstraintBridge.subLevelById(level, b.subId);
        if (subA == null || subB == null) {
            return false;
        }
        // replace, never stack two hinges on one pair. they fight and thrash the ships

        HingeJointManager manager = HingeJointManager.get(level);
        HingeJoint existing = manager.findJointBetween(a.subId, b.subId);
        if (existing != null) {
            manager.removeJoint(existing.jointId);
        }

        Vec3 seam = midpoint(a.hit, b.hit);
        Vector3d seamV = new Vector3d(seam.x, seam.y, seam.z);

        Vector3d axisWorld;
        if (a.doorAxis) {
            axisWorld = new Vector3d(0, 1, 0);
        } else {
            Vec3 pull = b.hit.subtract(a.hit);
            Vec3 pullH = new Vec3(pull.x, 0, pull.z);
            if (pullH.lengthSqr() < 1.0e-6) {
                pullH = new Vec3(1, 0, 0);
            }
            Vec3 ax = new Vec3(0, 1, 0).cross(pullH).normalize();
            axisWorld = new Vector3d(ax.x, ax.y, ax.z);
        }

        Vector3d pivotLocalA = HingeConstraintBridge.worldToLocal(subA, seamV);
        Vector3d pivotLocalB = HingeConstraintBridge.worldToLocal(subB, seamV);
        Vector3d axisLocalA = HingeConstraintBridge.worldAxisToLocal(subA, axisWorld);
        Vector3d axisLocalB = HingeConstraintBridge.worldAxisToLocal(subB, axisWorld);
        if (pivotLocalA == null || pivotLocalB == null || axisLocalA == null || axisLocalB == null) {
            return false;
        }

        Object handle = HingeConstraintBridge.createRotary(level, subA, subB,
            pivotLocalA, pivotLocalB, axisLocalA, axisLocalB);
        if (handle == null) {
            return false;
        }
        HingeJoint joint = new HingeJoint(UUID.randomUUID(), a.subId, b.subId,
            pivotLocalA, pivotLocalB, axisLocalA, axisLocalB);
        joint.handle = handle;
        manager.addJoint(joint);
        return true;
    }

    @Nullable
    private static Pick pick(ServerLevel level, Player player) {
        Pick precise = pickPreciseEdge(level, player);
        return precise != null ? precise : pickByAnchor(level, player);
    }

    @Nullable
    private static Pick pickPreciseEdge(ServerLevel level, Player player) {
        Vec3 realEye = player.getEyePosition(1.0f);
        Object bestSub = null;
        Vec3 bestWorld = null;
        double bestDist = Double.MAX_VALUE;
        for (Object sub : HingeConstraintBridge.allServerSubLevels(level)) {
            Vector3d com = HingeConstraintBridge.worldAnchor(sub);
            if (com == null || realEye.distanceTo(new Vec3(com.x, com.y, com.z)) > PICK_REACH + 64.0) {
                continue;
            }
            final Vec3[] hitWorld = new Vec3[1];
            HingeConstraintBridge.withEntityLocal(sub, player, () -> {
                Vec3 e = player.getEyePosition(1.0f);
                Vec3 end = e.add(player.getViewVector(1.0f).scale(PICK_REACH));
                BlockHitResult h = level.clip(
                    new ClipContext(e, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
                if (h.getType() == HitResult.Type.BLOCK) {
                    Vec3 lp = h.getLocation();
                    Vector3d w = HingeConstraintBridge.localToWorld(sub, new Vector3d(lp.x, lp.y, lp.z));
                    if (w != null && Double.isFinite(w.x) && Double.isFinite(w.y) && Double.isFinite(w.z)) {
                        hitWorld[0] = new Vec3(w.x, w.y, w.z);
                    }
                }
            });
            if (hitWorld[0] == null) {
                continue;
            }
            double dist = realEye.distanceTo(hitWorld[0]);

            if (dist <= PICK_REACH + 4.0 && dist < bestDist) {
                bestDist = dist;
                bestSub = sub;
                bestWorld = hitWorld[0];
            }
        }
        if (bestSub == null) {
            return null;
        }
        UUID id = HingeConstraintBridge.subLevelId(bestSub);
        return id == null ? null : new Pick(id, bestWorld);
    }

    @Nullable
    private static Pick pickByAnchor(ServerLevel level, Player player) {
        Vec3 eye = player.getEyePosition(1.0f);
        Vec3 dir = player.getViewVector(1.0f).normalize();
        Object best = null;
        double bestPerp = Double.MAX_VALUE;
        Vec3 bestHit = null;
        for (Object sub : HingeConstraintBridge.allServerSubLevels(level)) {
            Vector3d a = HingeConstraintBridge.worldAnchor(sub);
            if (a == null) {
                continue;
            }
            Vec3 anchor = new Vec3(a.x, a.y, a.z);
            double t = anchor.subtract(eye).dot(dir);
            if (t < 0 || t > PICK_REACH) {
                continue;
            }
            Vec3 closest = eye.add(dir.scale(t));
            double perp = closest.distanceTo(anchor);
            if (perp < bestPerp && perp <= MAX_PICK_PERP) {
                bestPerp = perp;
                best = sub;
                bestHit = closest;
            }
        }
        if (best == null) {
            return null;
        }
        UUID id = HingeConstraintBridge.subLevelId(best);
        return id == null ? null : new Pick(id, bestHit);
    }

    private static void feedbackPick(ServerLevel level, Vec3 at) {
        level.playSound(null, at.x, at.y, at.z, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.PLAYERS, 0.7f, 1.4f);
        level.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 6, 0.1, 0.1, 0.1, 0.01);
    }

    private static void feedbackLink(ServerLevel level, Vec3 at) {
        level.playSound(null, at.x, at.y, at.z, SoundEvents.IRON_DOOR_CLOSE, SoundSource.PLAYERS, 0.8f, 0.9f);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.4f, 1.6f);
        level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 16, 0.2, 0.2, 0.2, 0.1);
    }

    private static void feedbackUnlink(ServerLevel level, Vec3 at) {
        level.playSound(null, at.x, at.y, at.z, SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.PLAYERS, 0.8f, 0.8f);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.IRON_DOOR_OPEN, SoundSource.PLAYERS, 0.6f, 1.1f);
        level.sendParticles(ParticleTypes.SMOKE, at.x, at.y, at.z, 12, 0.2, 0.2, 0.2, 0.02);
    }

    private static Vec3 midpoint(Vec3 a, Vec3 b) {
        return new Vec3((a.x + b.x) * 0.5, (a.y + b.y) * 0.5, (a.z + b.z) * 0.5);
    }

    private static void msg(Player player, Component text, ChatFormatting color) {
        player.displayClientMessage(text.copy().withStyle(color), true);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.create_stats.hinge.tooltip.line1").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.create_stats.hinge.tooltip.line2").withStyle(ChatFormatting.DARK_GRAY));
    }
}
