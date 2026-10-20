package com.createstats.client.ponder.fe;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.createmod.catnip.outliner.Outline;
import net.createmod.catnip.render.PonderRenderTypes;
import net.createmod.catnip.render.SuperRenderTypeBuffer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.joml.Vector4f;

// both face windings, the gui mirror flips Y and culling eats half
final class FeBoxes {
    private FeBoxes() {
    }

    static final ResourceLocation WHITE = ResourceLocation.withDefaultNamespace("textures/misc/white.png");

    static VertexConsumer translucent(SuperRenderTypeBuffer buffer) {
        return buffer.getBuffer(PonderRenderTypes.outlineTranslucent(WHITE, false));
    }

    static VertexConsumer solid(SuperRenderTypeBuffer buffer) {
        return buffer.getBuffer(PonderRenderTypes.outlineSolid());
    }

    static Vector4f rgba(int rgb, float alpha) {
        return new Vector4f(((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f,
                Math.max(0f, Math.min(1f, alpha)));
    }

    static void box(Outline o, PoseStack.Pose pose, VertexConsumer vc, Vector3f a, Vector3f b, Vector4f c) {
        quad(o, pose, vc, c, a.x, a.y, a.z, b.x, a.y, a.z, b.x, a.y, b.z, a.x, a.y, b.z, 0, -1, 0);
        quad(o, pose, vc, c, a.x, b.y, b.z, b.x, b.y, b.z, b.x, b.y, a.z, a.x, b.y, a.z, 0, 1, 0);
        quad(o, pose, vc, c, a.x, a.y, a.z, a.x, b.y, a.z, b.x, b.y, a.z, b.x, a.y, a.z, 0, 0, -1);
        quad(o, pose, vc, c, b.x, a.y, b.z, b.x, b.y, b.z, a.x, b.y, b.z, a.x, a.y, b.z, 0, 0, 1);
        quad(o, pose, vc, c, a.x, a.y, b.z, a.x, b.y, b.z, a.x, b.y, a.z, a.x, a.y, a.z, -1, 0, 0);
        quad(o, pose, vc, c, b.x, a.y, a.z, b.x, b.y, a.z, b.x, b.y, b.z, b.x, a.y, b.z, 1, 0, 0);
    }

    private static void quad(Outline o, PoseStack.Pose pose, VertexConsumer vc, Vector4f c, float x0, float y0,
                             float z0, float x1, float y1, float z1, float x2, float y2, float z2, float x3, float y3,
                             float z3, float nx, float ny, float nz) {
        o.bufferQuad(pose, vc, new Vector3f(x0, y0, z0), new Vector3f(x1, y1, z1), new Vector3f(x2, y2, z2),
                new Vector3f(x3, y3, z3), c, LightTexture.FULL_BRIGHT, new Vector3f(nx, ny, nz));
        o.bufferQuad(pose, vc, new Vector3f(x3, y3, z3), new Vector3f(x2, y2, z2), new Vector3f(x1, y1, z1),
                new Vector3f(x0, y0, z0), c, LightTexture.FULL_BRIGHT, new Vector3f(-nx, -ny, -nz));
    }

    static void line(Outline o, PoseStack ms, VertexConsumer vc, double ax, double ay, double az, double bx, double by,
                     double bz, float w, Vector4f c) {
        o.bufferCuboidLine(ms, vc, net.minecraft.world.phys.Vec3.ZERO, new Vector3d(ax, ay, az), new Vector3d(bx, by, bz),
                w, c, LightTexture.FULL_BRIGHT, false);
    }
}
