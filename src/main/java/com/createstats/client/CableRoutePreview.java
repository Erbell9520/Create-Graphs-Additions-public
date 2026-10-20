package com.createstats.client;

import com.createstats.block.CableBlock;
import com.createstats.item.CableItem;
import com.createstats.power.CablePlacement;
import com.createstats.power.CableRoute;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.List;

// route on the client, it's only asking solid or empty. a packet per pixel of aim, no
@EventBusSubscriber(modid = "create_stats", value = Dist.CLIENT)
public final class CableRoutePreview {
    private CableRoutePreview() {
    }

    private static final int COL_OK = 0x6BE4FF;
    private static final int COL_SHORT = 0xFFBA3C;

    private static final double REACH = 24.0;

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        final Minecraft mc = Minecraft.getInstance();
        final LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            return;
        }
        final ItemStack held = heldCable(player);
        final CableRoute.Step anchor = held.isEmpty() ? null : CablePlacement.anchorOf(player);
        final CableRoute.Step target = anchor == null ? null : aimedStep(player);
        if (target == null || target.equals(anchor)) {
            clear();
            return;
        }
        final List<CableRoute.Step> route = CablePlacement.route(mc.level, anchor, target);
        if (route.isEmpty()) {
            clear();
            return;
        }
        final CableBlock cable = (CableBlock) ((CableItem) held.getItem()).getBlock();
        final int needed = CablePlacement.cost(mc.level, route, cable);
        final int have = player.getAbilities().instabuild ? needed : held.getCount();
        pending = new Pending(route.size(), needed, have, cable.tier().getDisplayName());
        draw(event.getPoseStack(), route, have >= needed ? COL_OK : COL_SHORT, have);
    }

    private record Pending(int steps, int cost, int have, String grade) {
    }

    @org.jetbrains.annotations.Nullable
    private static Pending pending;

    public static void clear() {
        pending = null;
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        clear();
        if (event.getPlayer() != null) {
            CablePlacement.acceptFromServer(event.getPlayer().getUUID(), null);
        }
    }

    public static void renderHud(net.minecraft.client.gui.GuiGraphics graphics) {
        final Pending run = pending;
        final Minecraft mc = Minecraft.getInstance();
        if (run == null || mc.player == null || mc.options.hideGui) {
            return;
        }
        final boolean affordable = run.have() >= run.cost();
        final String line = run.steps() + (run.steps() == 1 ? " block, " : " blocks, ")
                + run.cost() + " " + run.grade() + (affordable ? "" : ", you have " + run.have());
        final int x = graphics.guiWidth() / 2 - mc.font.width(line) / 2;
        final int y = graphics.guiHeight() / 2 + 12;
        graphics.drawString(mc.font, line, x, y, affordable ? 0xFF6BE4FF : 0xFFFFBA3C, true);
    }

    private static ItemStack heldCable(Player player) {
        for (net.minecraft.world.InteractionHand hand : net.minecraft.world.InteractionHand.values()) {
            final ItemStack stack = player.getItemInHand(hand);
            if (stack.getItem() instanceof CableItem) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    private static CableRoute.Step aimedStep(LocalPlayer player) {
        final Vec3 eye = player.getEyePosition();
        final Vec3 look = player.getViewVector(1.0f);
        final HitResult hit = player.level().clip(new ClipContext(
                eye, eye.add(look.scale(REACH)),
                ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        if (!(hit instanceof BlockHitResult block) || hit.getType() != HitResult.Type.BLOCK) {
            return null;
        }

        return CablePlacement.endpointFor(player.level(), block.getBlockPos(), block.getDirection(),
                block.getLocation());
    }

    private static void draw(PoseStack poseStack, List<CableRoute.Step> route, int colour, int budget) {
        final int rgb = colour & 0xFFFFFF;
        final List<FaceSlab.Tinted> faces = new java.util.ArrayList<>(route.size());
        int drawn = 0;
        for (CableRoute.Step step : route) {
            final int alpha = drawn < budget ? 140 : 36;
            faces.add(new FaceSlab.Tinted(step.pos(), step.face(), (alpha << 24) | rgb));
            drawn++;
        }
        FaceSlab.draw(poseStack, faces);
    }
}
