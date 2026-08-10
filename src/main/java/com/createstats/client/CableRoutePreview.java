package com.createstats.client;

import com.createstats.block.CableBlock;
import com.createstats.item.CableItem;
import com.createstats.power.CablePlacement;
import com.createstats.power.CableRoute;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.util.List;

/** [1] */
@EventBusSubscriber(modid = "create_stats", value = Dist.CLIENT)
public final class CableRoutePreview {

    private CableRoutePreview() {
    }

    private static final int COL_OK = 0x6BE4FF;
    private static final int COL_SHORT = 0xFFBA3C;
    /** How far the player can aim when picking the far end of a run. */
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
        draw(event.getPoseStack(), route, cable, mc, have >= needed ? COL_OK : COL_SHORT, have);
    }

    /** [2] */
    private record Pending(int steps, int cost, int have, String grade) {
    }

    @org.jetbrains.annotations.Nullable
    private static Pending pending;

    /** Drops the readout. Called whenever the ghost is not drawn, from every early return above. */
    public static void clear() {
        pending = null;
    }

    /** [3] */
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

    /** The cable stack in either hand, or empty. */
    private static ItemStack heldCable(Player player) {
        for (net.minecraft.world.InteractionHand hand : net.minecraft.world.InteractionHand.values()) {
            final ItemStack stack = player.getItemInHand(hand);
            if (stack.getItem() instanceof CableItem) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    /** Where the player is looking, as a cable step, or null if they are not aimed at a surface. */
    private static CableRoute.Step aimedStep(LocalPlayer player) {
        final Vec3 eye = player.getEyePosition();
        final Vec3 look = player.getViewVector(1.0f);
        final HitResult hit = player.level().clip(new ClipContext(
                eye, eye.add(look.scale(REACH)),
                ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        if (!(hit instanceof BlockHitResult block) || hit.getType() != HitResult.Type.BLOCK) {
            return null;
        }
        // Same snapping the placement uses, or the hologram would show a run ending somewhere the
        // server then declines to build.
        return CablePlacement.endpointFor(player.level(), block.getBlockPos(), block.getDirection(),
                block.getLocation());
    }

    /** [4] */
    private static void draw(PoseStack poseStack, List<CableRoute.Step> route, CableBlock cable,
                             Minecraft mc, int colour, int budget) {
        final Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        final float r = ((colour >> 16) & 0xFF) / 255.0f;
        final float g = ((colour >> 8) & 0xFF) / 255.0f;
        final float b = (colour & 0xFF) / 255.0f;

        poseStack.pushPose();
        poseStack.translate(-cam.x, -cam.y, -cam.z);
        final Matrix4f mat = poseStack.last().pose();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(net.minecraft.client.renderer.GameRenderer::getPositionColorShader);

        final BufferBuilder buffer = Tesselator.getInstance()
                .begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        int drawn = 0;
        for (CableRoute.Step step : route) {
            // Past the budget the ghost fades hard, so the player sees exactly where they run out.
            final float alpha = drawn < budget ? 0.55f : 0.14f;
            slab(buffer, mat, step.pos(), step.face(), r, g, b, alpha);
            drawn++;
        }
        BufferUploader.drawWithShader(buffer.buildOrThrow());

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        poseStack.popPose();
    }

    /** A thin quad lying on one face of a block position, inset slightly to avoid z-fighting. */
    private static void slab(BufferBuilder buffer, Matrix4f mat, BlockPos pos, Direction face,
                             float r, float g, float b, float a) {
        final float e = 0.03f;   // inset from the block edges
        final float o = 0.015f;  // stand-off from the surface
        final float x0 = pos.getX() + e;
        final float y0 = pos.getY() + e;
        final float z0 = pos.getZ() + e;
        final float x1 = pos.getX() + 1 - e;
        final float y1 = pos.getY() + 1 - e;
        final float z1 = pos.getZ() + 1 - e;
        switch (face) {
            case DOWN -> quad(buffer, mat, x0, y0 + o, z0, x1, y0 + o, z0, x1, y0 + o, z1, x0, y0 + o, z1, r, g, b, a);
            case UP -> quad(buffer, mat, x0, y1 - o, z1, x1, y1 - o, z1, x1, y1 - o, z0, x0, y1 - o, z0, r, g, b, a);
            case NORTH -> quad(buffer, mat, x0, y0, z0 + o, x0, y1, z0 + o, x1, y1, z0 + o, x1, y0, z0 + o, r, g, b, a);
            case SOUTH -> quad(buffer, mat, x1, y0, z1 - o, x1, y1, z1 - o, x0, y1, z1 - o, x0, y0, z1 - o, r, g, b, a);
            case WEST -> quad(buffer, mat, x0 + o, y0, z1, x0 + o, y1, z1, x0 + o, y1, z0, x0 + o, y0, z0, r, g, b, a);
            case EAST -> quad(buffer, mat, x1 - o, y0, z0, x1 - o, y1, z0, x1 - o, y1, z1, x1 - o, y0, z1, r, g, b, a);
        }
    }

    private static void quad(BufferBuilder buffer, Matrix4f mat,
                             float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz,
                             float r, float g, float b, float a) {
        buffer.addVertex(mat, ax, ay, az).setColor(r, g, b, a);
        buffer.addVertex(mat, bx, by, bz).setColor(r, g, b, a);
        buffer.addVertex(mat, cx, cy, cz).setColor(r, g, b, a);
        buffer.addVertex(mat, dx, dy, dz).setColor(r, g, b, a);
    }
}
