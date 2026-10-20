package com.createstats.renderer;

import com.createstats.blockentity.VacuumCollectorBE;
import com.createstats.renderer.VacuumMesh.Tile;
import com.createstats.vacuum.CollectorButton;
import com.createstats.vacuum.VacuumFrame;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public class VacuumCollectorRenderer implements BlockEntityRenderer<VacuumCollectorBE> {
    public static final ResourceLocation PARTS =
            ResourceLocation.fromNamespaceAndPath("create_stats", "textures/block/vacuum_parts.png");

    private static final double CX = 8.0;
    private static final double CZ = 9.0;

    private static final double SCREEN_FRONT = 0.05;

    private static final double SPROCKET_Y = 2.0;

    private static final double[][] SPROCKETS = {{15.25, 15.75, 15.75, 16.0}, {0.25, 0.75, 0.0, 0.25}};

    public VacuumCollectorRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(VacuumCollectorBE be, float partial, PoseStack ps, MultiBufferSource buffers, int light,
                       int overlay) {
        // filters never drew on this block until this call. create only draws them from its own renderer

        FilteringRenderer.renderOnBlockEntity(be, partial, ps, buffers, light, overlay);
        final VacuumCollectorBE.State state = be.state();
        ps.pushPose();
        ps.translate(0.5, 0.0, 0.5);
        ps.mulPose(Axis.YP.rotationDegrees(VacuumFrame.yRotation(be.facing())));
        ps.translate(-0.5, 0.0, -0.5);
        ps.scale(1.0f / 16.0f, 1.0f / 16.0f, 1.0f / 16.0f);

        final VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(PARTS));

        ps.pushPose();
        ps.translate(CX, 0, CZ);
        ps.mulPose(Axis.YP.rotationDegrees(be.fanAngle + be.fanSpeed * partial));
        ps.translate(-CX, 0, -CZ);
        VacuumMesh.disc(vc, ps.last(), 'y', CX, CZ, 3.0, 15.45, 1, 16, Tile.FAN, VacuumMesh.WHITE, light, overlay);
        ps.popPose();

        for (double[] x : SPROCKETS) {
            ps.pushPose();
            ps.translate(0, SPROCKET_Y, CZ);
            ps.mulPose(Axis.XP.rotationDegrees(be.sprocketAngle));
            ps.translate(0, -SPROCKET_Y, -CZ);
            VacuumMesh.prism(vc, ps.last(), 'x', CZ, SPROCKET_Y, 0.95, x[0], x[1], 16, Tile.BRASS_PLATE,
                    Tile.BRASS_PLATE, true, true, 0, 0, VacuumMesh.WHITE, light, overlay);
            for (int tooth = 0; tooth < 8; tooth++) {
                ps.pushPose();
                ps.translate(0, SPROCKET_Y, CZ);
                ps.mulPose(Axis.XP.rotationDegrees(tooth * 45.0f));
                ps.translate(0, -SPROCKET_Y, -CZ);
                VacuumMesh.box(vc, ps.last(), x[0], SPROCKET_Y + 0.85, CZ - 0.28, x[1], SPROCKET_Y + 1.4, CZ + 0.28,
                        Tile.BRASS_PLATE, light, overlay);
                ps.popPose();
            }
            VacuumMesh.prism(vc, ps.last(), 'x', CZ, SPROCKET_Y, 0.45, x[2], x[3], 8, Tile.IRON_DARK, Tile.IRON_DARK,
                    true, true, 0, 0, VacuumMesh.WHITE, light, overlay);
            ps.popPose();
        }

        ps.pushPose();
        ps.translate(5.0, 0, 3.0);
        ps.mulPose(Axis.YP.rotationDegrees(be.damperOpen));
        ps.translate(-5.0, 0, -3.0);
        VacuumMesh.box(vc, ps.last(), 3.15, 7.9, 2.9, 6.85, 11.6, 3.1, Tile.STEEL_DARK, light, overlay);
        VacuumMesh.box(vc, ps.last(), 4.9, 7.8, 2.85, 5.1, 11.7, 3.15, Tile.IRON_DARK, light, overlay);
        VacuumMesh.box(vc, ps.last(), 5.0, 12.15, 2.75, 7.4, 12.65, 3.25, Tile.BRASS_PLATE, light, overlay);
        VacuumMesh.box(vc, ps.last(), 4.7, 12.05, 2.7, 5.3, 12.85, 3.3, Tile.IRON_DARK, light, overlay);
        ps.popPose();

        if (be.screened()) {
            final double z0 = SCREEN_FRONT;
            final double z1 = SCREEN_FRONT + 0.3;
            VacuumMesh.box(vc, ps.last(), 3.3, 7.75, z0, 3.65, 11.75, z1, Tile.BRASS_PLATE, light, overlay);
            VacuumMesh.box(vc, ps.last(), 6.35, 7.75, z0, 6.7, 11.75, z1, Tile.BRASS_PLATE, light, overlay);
            VacuumMesh.box(vc, ps.last(), 3.65, 7.75, z0, 6.35, 8.1, z1, Tile.BRASS_PLATE, light, overlay);
            VacuumMesh.box(vc, ps.last(), 3.65, 11.4, z0, 6.35, 11.75, z1, Tile.BRASS_PLATE, light, overlay);
            VacuumMesh.box(vc, ps.last(), 3.5, 11.75, z0 + 0.05, 4.5, 13.1, z1 - 0.05, Tile.BRASS_PLATE, light, overlay);
            VacuumMesh.box(vc, ps.last(), 3.35, 12.85, z0, 4.65, 13.35, z1, Tile.IRON_DARK, light, overlay);
        }

        final double lift = switch (state) {
            case IDLE -> 1.25;
            case PULL -> 2.75;
            default -> 0.0;
        };

        final double base = 7.75;
        VacuumMesh.box(vc, ps.last(), 13.35, 4.15, 10.2, 13.95, base - lift, 10.8, Tile.FLUID, light, overlay);
        VacuumMesh.box(vc, ps.last(), 13.35, 4.15, 12.2, 13.95, base + lift, 12.8, Tile.FLUID, light, overlay);
        VacuumMesh.box(vc, ps.last(), 13.35, 4.15, 10.2, 13.95, 4.6, 12.8, Tile.FLUID, light, overlay);

        final float xp = be.xpCharge();
        if (xp > 0.02f && state != VacuumCollectorBE.State.OFF && state != VacuumCollectorBE.State.SWITCHED_OFF) {
            VacuumMesh.prism(vc, ps.last(), 'y', CX, CZ, 2.5, 13.05, 13.05 + 0.85 * xp, 16, Tile.XP, Tile.XP,
                    false, true, 0, 0, VacuumMesh.WHITE, LightTexture.FULL_BRIGHT, overlay);
        }

        final Tile lamp = switch (state) {
            case PULL -> Tile.LAMP_GREEN;
            case IDLE, FULL, DISABLED -> Tile.LAMP_AMBER;
            default -> Tile.LAMP_OFF;
        };
        VacuumMesh.box(vc, ps.last(), 4.0, 1.0, CollectorButton.lensFront(be.switchedOn()), 6.0, 3.0, 0.5, lamp,
                VacuumMesh.WHITE, lamp == Tile.LAMP_OFF ? light : LightTexture.FULL_BRIGHT, overlay);

        drawCatch(be, partial, ps, buffers, light, overlay);

        final VertexConsumer glass = buffers.getBuffer(RenderType.entityTranslucent(PARTS));
        final double[][] steps = {{2.0, 3.5, 4.75}, {3.25, 4.75, 6.0}, {4.5, 6.0, 7.0}};
        double prev = 0.0;
        for (double[] s : steps) {
            VacuumMesh.prism(glass, ps.last(), 'y', CX, CZ, s[0], s[1], s[2], 16, Tile.GLASS, Tile.GLASS,
                    prev == 0.0, false, 0, 0, VacuumMesh.WHITE, light, overlay);
            if (prev > 0) {
                VacuumMesh.prism(glass, ps.last(), 'y', CX, CZ, s[0], s[1], s[1] + 0.001, 16, null, Tile.GLASS,
                        true, false, prev, 0, VacuumMesh.WHITE, light, overlay);
            }
            prev = s[0];
        }
        VacuumMesh.prism(glass, ps.last(), 'y', CX, CZ, 5.0, 7.5, 12.0, 16, Tile.GLASS, null, false, false, 0, 0,
                VacuumMesh.WHITE, light, overlay);
        VacuumMesh.prism(glass, ps.last(), 'y', CX, CZ, 3.0, 13.0, 14.0, 16, Tile.GLASS, null, false, false, 0, 0,
                VacuumMesh.WHITE, light, overlay);
        for (double z : new double[] {10.5, 12.5}) {
            VacuumMesh.box(glass, ps.last(), 13.15, 4.75, z - 0.5, 14.15, 11.0, z + 0.5, Tile.GLASS, light, overlay);
        }
        VacuumMesh.box(glass, ps.last(), 13.15, 4.0, 10.0, 14.15, 4.75, 13.0, Tile.GLASS, light, overlay);

        ps.popPose();
    }

    private static void drawCatch(VacuumCollectorBE be, float partial, PoseStack ps, MultiBufferSource buffers,
                                  int light, int overlay) {
        final var inv = be.catchInventory();
        final java.util.List<ItemStack> shown = new java.util.ArrayList<>();
        for (int i = 0; i < inv.getSlots(); i++) {
            final ItemStack s = inv.getStackInSlot(i);
            if (!s.isEmpty()) {
                shown.add(s);
                if (s.getCount() >= 32) {
                    shown.add(s);
                }
            }
        }
        if (shown.isEmpty()) {
            return;
        }
        final VacuumCollectorBE.State state = be.state();
        final boolean spinning = state == VacuumCollectorBE.State.PULL || state == VacuumCollectorBE.State.IDLE;
        final double c0 = 3.5;
        final double b0 = 7.5;
        final double b1 = 12.0;
        final int n = shown.size();
        for (int i = 0; i < n; i++) {
            final double x;
            final double y;
            final double z;
            final float yaw;
            final float tilt;
            if (spinning) {
                final double h = b1 - 0.9 - (i / (double) Math.max(1, n - 1)) * (b1 - 0.9 - (c0 + 1.2));
                final double r = h > b0 ? 3.4 : 0.7 + (h - c0) * 1.0;
                final double ang = Math.toRadians(i * 137.5 + be.swirlAngle * (h > b0 ? 1.0 : 0.6));
                x = CX + r * Math.cos(ang);
                y = h;
                z = CZ + r * Math.sin(ang);
                yaw = (float) -Math.toDegrees(ang);
                tilt = 20 + (i * 37) % 50;
            } else {
                final int layer = i / 4;
                final double ang = Math.toRadians(i * 97.0 + layer * 40);
                final double r = Math.min(3.5, 0.4 + layer * 0.9 + (i % 2) * 0.5);
                x = CX + r * Math.cos(ang);
                y = c0 + 0.9 + layer * 1.2;
                z = CZ + r * Math.sin(ang);
                yaw = (i * 53) % 180;
                tilt = 70 + (i * 23) % 30;
            }
            ps.pushPose();
            ps.translate(x, y, z);
            ps.mulPose(Axis.YP.rotationDegrees(yaw));
            ps.mulPose(Axis.XP.rotationDegrees(tilt));
            ps.scale(3.5f, 3.5f, 3.5f);
            Minecraft.getInstance().getItemRenderer().renderStatic(shown.get(i), ItemDisplayContext.FIXED, light,
                    overlay, ps, buffers, be.getLevel(), i);
            ps.popPose();
        }
    }
}
