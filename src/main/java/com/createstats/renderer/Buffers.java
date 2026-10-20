package com.createstats.renderer;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;

public final class Buffers {
    private Buffers() {
    }

    public static VertexConsumer live(MultiBufferSource buffers, RenderType type) {
        return new Live(buffers, type);
    }

    private record Live(MultiBufferSource buffers, RenderType type) implements VertexConsumer {
        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            return this.buffers.getBuffer(this.type).addVertex(x, y, z);
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            return this.buffers.getBuffer(this.type).setColor(r, g, b, a);
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            return this.buffers.getBuffer(this.type).setUv(u, v);
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            return this.buffers.getBuffer(this.type).setUv1(u, v);
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            return this.buffers.getBuffer(this.type).setUv2(u, v);
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            return this.buffers.getBuffer(this.type).setNormal(x, y, z);
        }
    }
}
