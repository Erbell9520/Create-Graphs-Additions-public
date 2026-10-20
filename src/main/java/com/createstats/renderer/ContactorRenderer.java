package com.createstats.renderer;

import com.createstats.block.ContactorBlock;
import com.createstats.block.MountedPart;
import com.createstats.blockentity.ContactorBE;
import com.createstats.client.ModPartialModels;
import com.createstats.contactor.ContactorFrame;
import com.createstats.contactor.ContactorGeometry;
import com.createstats.contactor.ContactorLogic;
import com.createstats.contactor.ContactorMotion;
import com.createstats.contactor.ContactorState;
import com.createstats.contactor.ContactorStuds;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class ContactorRenderer implements BlockEntityRenderer<ContactorBE> {
    private static final float PX = 1.0f / 16.0f;

    private static final int PILOT_LIT = 0xFFFF4A33;
    private static final int PILOT_DARK = 0xFF4E2724;

    private static final int PILOT_STRIKE = 0xFFFFC9B8;

    public ContactorRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(ContactorBE be, float partialTick, PoseStack ps, MultiBufferSource buffers, int light,
                       int overlay) {
        final BlockState state = be.getBlockState();
        final Level level = be.getLevel();
        if (!(state.getBlock() instanceof ContactorBlock) || level == null) {
            return;
        }
        final Direction facing = state.getValue(ContactorBlock.FACING);
        final int spin = ContactorBlock.spin(state);
        final boolean nc = state.getValue(ContactorBlock.NORMALLY_CLOSED);

        drawStudWires(be, facing, spin, ps, buffers, light);

        final ContactorMotion.Unit motion = be.motion();
        final int broke = motion.advance(System.nanoTime(), state.getValue(ContactorBlock.LEFT),
                state.getValue(ContactorBlock.RIGHT),
                ContactorBlock.coil(level, be.getBlockPos(), state, ContactorState.LEFT_SIDE),
                ContactorBlock.coil(level, be.getBlockPos(), state, ContactorState.RIGHT_SIDE), nc, be.loads());
        if (broke != 0) {
            throwSparks(level, be.getBlockPos(), facing, spin, nc, broke, motion);
        }

        ps.pushPose();
        orient(ps, facing, spin);
        final var solid = buffers.getBuffer(RenderType.solid());
        final var cutout = buffers.getBuffer(RenderType.cutout());

        moved(part(ModPartialModels.CONTACTOR_CARRIER, state), 0.0, -motion.left().drive(), 0.0)
                .light(light).renderInto(ps, solid);
        moved(part(ModPartialModels.CONTACTOR_CARRIER, state), ContactorGeometry.UNIT_PITCH,
                -motion.right().drive(), 0.0).light(light).renderInto(ps, solid);

        final double mean = motion.meanDrive();
        moved(part(ModPartialModels.CONTACTOR_SLIDER, state), 0.0, -mean, 0.0).light(light).renderInto(ps, solid);
        final SuperByteBuffer bar = moved(part(ModPartialModels.CONTACTOR_EVENER, state), 0.0, -mean, 0.0);
        turnedAboutView(bar, ContactorGeometry.BAR_PIVOT_X, ContactorGeometry.BAR_PIVOT_Y,
                ContactorGeometry.BAR_PIVOT_Z, (float) motion.tilt()).light(light).renderInto(ps, solid);

        for (int pole = 0; pole < ContactorLogic.POLES; pole++) {
            final PartialModel model = pole == ContactorLogic.MIDDLE
                    ? (nc ? ModPartialModels.CONTACTOR_BRIDGE_NC_MIDDLE : ModPartialModels.CONTACTOR_BRIDGE_NO_MIDDLE)
                    : (nc ? ModPartialModels.CONTACTOR_BRIDGE_NC : ModPartialModels.CONTACTOR_BRIDGE_NO);
            moved(part(model, state), poleOffset(pole), -motion.bridgeDrop(pole), 0.0)
                    .light(light).renderInto(ps, solid);
        }

        for (int pole = 0; pole < ContactorLogic.POLES; pole++) {
            final SuperByteBuffer flag = moved(part(ModPartialModels.CONTACTOR_FLAG, state), poleOffset(pole), 0.0, 0.0);
            flippedAboutLongAxis(flag, ContactorGeometry.POLE_X[ContactorLogic.LEFT], ContactorGeometry.FLAG_PIVOT_Y,
                    ContactorGeometry.FLAG_PIVOT_Z, (float) motion.flagTurn(pole)).light(light).renderInto(ps, cutout);
        }

        for (int side = 0; side < 2; side++) {
            final double glow = motion.pilot(side);
            final SuperByteBuffer lens = moved(part(ModPartialModels.CONTACTOR_PILOT, state),
                    side == 0 ? 0.0 : 16.0 - ContactorGeometry.PILOT_X0 - ContactorGeometry.PILOT_X1, 0.0, 0.0);
            if (glow <= 0.02) {
                lens.color(PILOT_DARK).light(light).renderInto(ps, solid);
            } else {
                final int lit = mixArgb(PILOT_DARK, PILOT_LIT, (float) Math.min(1.0, glow));
                lens.color(glow > 1.0 ? mixArgb(lit, PILOT_STRIKE, (float) ((glow - 1.0) / 0.35)) : lit)
                        .light(LightTexture.FULL_BRIGHT).renderInto(ps, solid);
            }
        }

        for (int stud = 0; stud < ContactorLogic.STUDS; stud++) {
            if (be.studLinks(stud).isEmpty()) {
                continue;
            }
            final double up = ContactorLogic.isLine(stud) ? 0.0 : ContactorGeometry.LOAD_Y - ContactorGeometry.LINE_Y;
            moved(part(ModPartialModels.CONTACTOR_RING, state), poleOffset(ContactorLogic.poleOf(stud)), up, 0.0)
                    .light(light).renderInto(ps, solid);
        }

        final var glowing = buffers.getBuffer(RenderType.translucent());
        for (int pole = 0; pole < ContactorLogic.POLES; pole++) {
            final double glow = motion.arcGlow(pole);
            if (glow <= 0.0) {
                continue;
            }
            final double gap = nc ? -(ContactorGeometry.ARM_Y1 - ContactorGeometry.ARM_Y0) - 0.35 : 0.0;
            final int alpha = Mth.clamp((int) Math.round(255 * glow), 0, 255);
            moved(part(ModPartialModels.CONTACTOR_ARC, state), poleOffset(pole), gap + motion.arcRise(pole), 0.0)
                    .color((alpha << 24) | 0xFFFFFF).light(LightTexture.FULL_BRIGHT).renderInto(ps, glowing);
        }
        ps.popPose();
    }

    private static SuperByteBuffer part(PartialModel model, BlockState state) {
        return CachedBuffers.partial(model, state);
    }

    private static double poleOffset(int pole) {
        return ContactorGeometry.POLE_X[pole] - ContactorGeometry.POLE_X[ContactorLogic.LEFT];
    }

    private static SuperByteBuffer moved(SuperByteBuffer part, double dx, double dy, double dz) {
        return part.translate((float) (-dx * PX), (float) (dz * PX), (float) (dy * PX));
    }

    private static SuperByteBuffer turnedAboutView(SuperByteBuffer part, double x, double y, double z, float degrees) {
        final float px = (float) ((16.0 - x) * PX);
        final float py = (float) (z * PX);
        final float pz = (float) (y * PX);
        return part.translate(px, py, pz).rotate((float) Math.toRadians(degrees), Direction.UP)
                .translate(-px, -py, -pz);
    }

    private static SuperByteBuffer flippedAboutLongAxis(SuperByteBuffer part, double x, double y, double z,
                                                        float degrees) {
        final float px = (float) ((16.0 - x) * PX);
        final float py = (float) (z * PX);
        final float pz = (float) (y * PX);
        return part.translate(px, py, pz).rotate((float) Math.toRadians(degrees), Direction.EAST)
                .translate(-px, -py, -pz);
    }

    private static void orient(PoseStack ps, Direction facing, int spin) {
        final float[] rot = MountedPart.rotationDegrees(facing);
        ps.translate(0.5, 0.5, 0.5);
        if (rot[0] != 0.0f) {
            ps.mulPose(Axis.YP.rotationDegrees(rot[0]));
        }
        if (rot[1] != 0.0f) {
            ps.mulPose(Axis.XP.rotationDegrees(rot[1]));
        }
        if (spin != 0) {
            ps.mulPose(Axis.YP.rotationDegrees(MountedPart.spinDegrees(spin)));
        }
        ps.translate(-0.5, -0.5, -0.5);
    }

    private static void throwSparks(Level level, BlockPos pos, Direction facing, int spin, boolean nc, int broke,
                                    ContactorMotion.Unit motion) {
        final double gapY = ContactorGeometry.gapY(nc);
        for (int pole = 0; pole < ContactorLogic.POLES; pole++) {
            if ((broke & (1 << pole)) == 0) {
                continue;
            }
            final int load = (int) Math.round(motion.brokeLoad(pole));
            final int sparks = ContactorLogic.sparksPerPole(load);
            final double speed = ContactorLogic.sparkSpeed(load);
            final double[] at = ContactorFrame.toBlock(facing, spin, ContactorGeometry.POLE_X[pole], gapY,
                    ContactorGeometry.CONTACT_Z);
            for (int i = 0; i < sparks; i++) {
                level.addParticle(ParticleTypes.ELECTRIC_SPARK, pos.getX() + at[0], pos.getY() + at[1],
                        pos.getZ() + at[2], (level.random.nextDouble() - 0.5) * speed * 2.0,
                        (level.random.nextDouble() - 0.2) * speed * 2.0, (level.random.nextDouble() - 0.5) * speed * 2.0);
            }
            final int puffs = ContactorLogic.chutePuffs(load);
            if (puffs > 0) {
                final double[] vent = ContactorFrame.toBlock(facing, spin, ContactorGeometry.POLE_X[pole],
                        ContactorGeometry.HOOD_Y1, ContactorGeometry.VENT_Z);
                final Vec3 out = Vec3.atLowerCornerOf(ContactorFrame.topOf(facing, spin).getNormal()).scale(0.02);
                for (int i = 0; i < puffs; i++) {
                    level.addParticle(ParticleTypes.SMOKE, pos.getX() + vent[0], pos.getY() + vent[1],
                            pos.getZ() + vent[2], out.x, out.y + 0.01, out.z);
                }
            }
        }
    }

    private static void drawStudWires(ContactorBE be, Direction facing, int spin, PoseStack ps,
                                      MultiBufferSource buffers, int light) {
        if (be.wiresOnStuds() <= 0 || be.getLevel() == null) {
            return;
        }
        final com.mojang.blaze3d.vertex.VertexConsumer vc = PowerConnectorRenderer.wireBuffer(buffers);
        final org.joml.Matrix4f mat = ps.last().pose();
        final BlockPos origin = be.getBlockPos();
        for (int stud = 0; stud < ContactorLogic.STUDS; stud++) {
            final java.util.List<ContactorBE.StudLink> links = be.studLinks(stud);
            if (links.isEmpty()) {
                continue;
            }
            final Vec3 from = ContactorStuds.anchor(facing, spin, stud);
            for (ContactorBE.StudLink link : links) {
                final Vec3 to = PowerConnectorRenderer.attachPoint(be.getLevel(), link.to(), origin);
                PowerConnectorRenderer.drawCable(vc, mat, be.getLevel(), origin, link.to(),
                        (float) from.x, (float) from.y, (float) from.z,
                        (float) (link.to().getX() - origin.getX() + to.x),
                        (float) (link.to().getY() - origin.getY() + to.y),
                        (float) (link.to().getZ() - origin.getZ() + to.z),
                        light, link.tier());
            }
        }
    }

    private static int mixArgb(int from, int to, float t) {
        final float k = Mth.clamp(t, 0.0f, 1.0f);
        final int a = Mth.lerpInt(k, (from >>> 24) & 0xFF, (to >>> 24) & 0xFF);
        final int r = Mth.lerpInt(k, (from >> 16) & 0xFF, (to >> 16) & 0xFF);
        final int g = Mth.lerpInt(k, (from >> 8) & 0xFF, (to >> 8) & 0xFF);
        final int b = Mth.lerpInt(k, from & 0xFF, to & 0xFF);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    @Override
    public AABB getRenderBoundingBox(ContactorBE be) {
        if (be.wiresOnStuds() <= 0) {
            return new AABB(be.getBlockPos());
        }
        final PowerConnectorRenderer.WireBounds bounds = new PowerConnectorRenderer.WireBounds(be.getBlockPos());
        for (int stud = 0; stud < ContactorLogic.STUDS; stud++) {
            for (ContactorBE.StudLink link : be.studLinks(stud)) {
                bounds.include(link.to());
            }
        }
        return bounds.build();
    }

    // unconditional, the check runs once at section compile
    @Override
    public boolean shouldRenderOffScreen(ContactorBE be) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return com.createstats.ModConfiguration.cappedViewDistance(
                64 + com.createstats.blockentity.PowerConnectorBE.wireRange());
    }
}
