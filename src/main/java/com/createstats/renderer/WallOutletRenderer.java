package com.createstats.renderer;

import com.createstats.blockentity.WallOutletBE;
import com.createstats.power.CordPlug;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.List;

// the outlet draws the cord, it owns the link
public class WallOutletRenderer implements BlockEntityRenderer<WallOutletBE> {
    private static final double CORD_RADIUS = 0.0125;

    private static final double PLUG_RADIUS = 3.1 / 16.0 / 2.0;

    private static final double RELIEF_RADIUS = 0.030;

    private static final int[] CORD_COLOUR = {28, 26, 25};

    private static final int[] PLUG_COLOUR = {198, 192, 177};

    private static final int[] LED_COLOUR = {70, 170, 255};
    private static final double LED_RADIUS = 0.02;

    private static final double LED_U = 8.0 / 16.0;
    private static final double LED_V = 12.5 / 16.0;

    public WallOutletRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(WallOutletBE outlet, float partialTicks, PoseStack poseStack,
                       MultiBufferSource buffers, int light, int overlay) {
        final boolean corded = outlet.anyCorded();
        if (!corded && !outlet.isLinked()) {
            return;
        }
        final BlockPos origin = outlet.getBlockPos();
        final VertexConsumer vc = PowerConnectorRenderer.wireBuffer(buffers);
        final Matrix4f mat = poseStack.last().pose();
        if (corded) {
            for (int gang = 0; gang < outlet.gangCount(); gang++) {
                final List<Vec3> route = outlet.cordRoute(gang);
                final BlockPos target = outlet.cordTarget(gang);
                final Direction face = outlet.cordFace(gang);
                if (route.size() < 2 || target == null || face == null) {
                    continue;
                }
                this.renderCord(outlet, origin, route, target, face, vc, mat, light);
            }
        }
        if (outlet.isLinked()) {
            this.drawLed(vc, mat, outlet, origin, light);
        }
    }

    private void drawLed(VertexConsumer vc, Matrix4f mat, WallOutletBE outlet, BlockPos origin, int light) {
        final BlockState state = outlet.getBlockState();
        final Vec3 seated = CordPlug.facePoint(state, outlet.getBlockPos(), LED_U, LED_V,
                CordPlug.PLATE_FACE - 0.005);
        final Vec3 tip = CordPlug.facePoint(state, outlet.getBlockPos(), LED_U, LED_V,
                CordPlug.PLATE_FACE + LED_RADIUS * 1.4);
        tube(vc, mat, origin, seated, tip, LED_RADIUS, LED_COLOUR, light);
    }

    private void renderCord(WallOutletBE outlet, BlockPos origin, List<Vec3> route, BlockPos target,
                            Direction face, VertexConsumer vc, Matrix4f mat, int light) {
        final double[][] lead = new double[route.size()][3];
        for (int i = 0; i < route.size(); i++) {
            lead[i][0] = route.get(i).x - origin.getX();
            lead[i][1] = route.get(i).y - origin.getY();
            lead[i][2] = route.get(i).z - origin.getZ();
        }
        PowerConnectorRenderer.drawTube(vc, mat, lead, light, CORD_RADIUS, CORD_COLOUR);

        this.drawPlug(vc, mat, outlet, origin, route.get(0), light);
        this.drawRelief(vc, mat, origin, target, face, light);
    }

    private void drawPlug(VertexConsumer vc, Matrix4f mat, WallOutletBE outlet, BlockPos origin,
                          Vec3 mouth, int light) {
        final Direction out = CordPlug.outward(outlet.getBlockState());
        final Vec3 normal = Vec3.atLowerCornerOf(out.getNormal());
        final Vec3 seated = mouth.subtract(normal.scale(CordPlug.PLUG_LENGTH + 0.02));
        tube(vc, mat, origin, seated, mouth, PLUG_RADIUS, PLUG_COLOUR, light);

        cap(vc, mat, origin, mouth, normal, PLUG_RADIUS, PLUG_COLOUR, light);

        tube(vc, mat, origin, mouth.subtract(normal.scale(0.01)),
                mouth.add(normal.scale(RELIEF_RADIUS * 1.4)), RELIEF_RADIUS, CORD_COLOUR, light);
    }

    private void drawRelief(VertexConsumer vc, Matrix4f mat, BlockPos origin, BlockPos machine,
                            Direction face, int light) {
        tube(vc, mat, origin, CordPlug.machineSurface(machine, face),
                CordPlug.terminal(machine, face), RELIEF_RADIUS, CORD_COLOUR, light);
    }

    private static void tube(VertexConsumer vc, Matrix4f mat, BlockPos origin, Vec3 from, Vec3 to,
                             double radius, int[] colour, int light) {
        final double[][] p = {
                {from.x - origin.getX(), from.y - origin.getY(), from.z - origin.getZ()},
                {to.x - origin.getX(), to.y - origin.getY(), to.z - origin.getZ()},
        };
        PowerConnectorRenderer.drawTube(vc, mat, p, light, radius, colour);
    }

    private static void cap(VertexConsumer vc, Matrix4f mat, BlockPos origin, Vec3 at, Vec3 outward,
                            double radius, int[] colour, int light) {
        final double[] centre = {at.x - origin.getX(), at.y - origin.getY(), at.z - origin.getZ()};
        final double[] dir = {outward.x, outward.y, outward.z};
        PowerConnectorRenderer.drawCap(vc, mat, centre, dir, radius, colour, light);
    }

    @Override
    public int getViewDistance() {
        return 96;
    }

    @Override
    public boolean shouldRenderOffScreen(WallOutletBE outlet) {
        return outlet.anyCorded();
    }
}
