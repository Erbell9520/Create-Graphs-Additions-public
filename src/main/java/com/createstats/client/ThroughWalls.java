package com.createstats.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.createmod.ponder.enums.PonderSpecialTextures;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import org.lwjgl.opengl.GL11;

public final class ThroughWalls {
    private ThroughWalls() {
    }

    public static RenderType type() {
        return Gpu.XRAY;
    }

    public static MultiBufferSource.BufferSource newBuffers() {
        return MultiBufferSource.immediate(new ByteBufferBuilder(1 << 16));
    }

    public static void always(Runnable draw) {
        RenderSystem.depthFunc(GL11.GL_ALWAYS);
        try {
            draw.run();
        } finally {
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
        }
    }

    private static final class Gpu {
        static final RenderType XRAY = RenderType.create("create_stats_through_walls", DefaultVertexFormat.NEW_ENTITY,
                VertexFormat.Mode.QUADS, 256, false, false,
                RenderType.CompositeState.builder()
                        .setShaderState(Shards.ENTITY_TRANSLUCENT)
                        .setTextureState(new RenderStateShard.TextureStateShard(
                                PonderSpecialTextures.BLANK.getLocation(), false, false))
                        .setTransparencyState(Shards.TRANSLUCENT)
                        .setDepthTestState(Shards.ALWAYS)
                        .setLayeringState(Shards.EVERY_FRAGMENT)
                        .setWriteMaskState(Shards.COLOUR_ONLY)
                        .setCullState(Shards.CULLED)
                        .setLightmapState(Shards.LIT)
                        .setOverlayState(Shards.OVERLAID)
                        .createCompositeState(false));

        private Gpu() {
        }
    }

    private static final class Shards extends RenderStateShard {
        static final ShaderStateShard ENTITY_TRANSLUCENT = RENDERTYPE_ENTITY_TRANSLUCENT_SHADER;
        static final TransparencyStateShard TRANSLUCENT = TRANSLUCENT_TRANSPARENCY;
        static final DepthTestStateShard ALWAYS = NO_DEPTH_TEST;

        static final LayeringStateShard EVERY_FRAGMENT = new LayeringStateShard("create_stats_every_fragment",
                () -> RenderSystem.depthFunc(GL11.GL_ALWAYS), () -> RenderSystem.depthFunc(GL11.GL_LEQUAL));
        static final WriteMaskStateShard COLOUR_ONLY = COLOR_WRITE;
        static final CullStateShard CULLED = CULL;
        static final LightmapStateShard LIT = LIGHTMAP;
        static final OverlayStateShard OVERLAID = OVERLAY;

        private Shards() {
            super(null, null, null);
        }
    }
}
