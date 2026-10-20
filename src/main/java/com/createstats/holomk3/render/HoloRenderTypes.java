package com.createstats.holomk3.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

// additive, order independent, no sort, no transparency bugs
@OnlyIn(Dist.CLIENT)
public final class HoloRenderTypes {
    public static final RenderType ADDITIVE = RenderType.create(
            "create_stats_holo_additive",
            DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS,
            4096,
            false,
            false,
            RenderType.CompositeState.builder()
                    .setShaderState(Shards.POS_COLOR)
                    .setTransparencyState(Shards.ADDITIVE)
                    .setCullState(Shards.NO_CULL_STATE)
                    .setWriteMaskState(Shards.COLOR_ONLY)
                    .createCompositeState(false));

    public static final RenderType ADDITIVE_SHADED = RenderType.create(
            "create_stats_holo_additive_shaded",
            DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS,
            4096,
            false,
            false,
            RenderType.CompositeState.builder()
                    .setShaderState(Shards.HOLO)
                    .setTransparencyState(Shards.ADDITIVE)
                    .setCullState(Shards.NO_CULL_STATE)
                    .setWriteMaskState(Shards.COLOR_ONLY)
                    .createCompositeState(false));

    public static final RenderType SKIN = RenderType.create(
            "create_stats_holo_skin",
            DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS,
            2048,
            false,
            true,
            RenderType.CompositeState.builder()
                    .setShaderState(Shards.POS_COLOR)
                    .setTransparencyState(Shards.TRANSLUCENT)
                    .setCullState(Shards.NO_CULL_STATE)
                    .createCompositeState(false));

    private HoloRenderTypes() {
    }

    public static RenderType additive(boolean shaderPath) {
        return shaderPath && HoloShaders.available() ? ADDITIVE_SHADED : ADDITIVE;
    }

    private static final class Shards extends RenderStateShard {
        static final ShaderStateShard POS_COLOR = POSITION_COLOR_SHADER;

        static final ShaderStateShard HOLO = new ShaderStateShard(HoloShaders::activeOrFallback);

        static final TransparencyStateShard ADDITIVE = LIGHTNING_TRANSPARENCY;
        static final TransparencyStateShard TRANSLUCENT = TRANSLUCENT_TRANSPARENCY;
        static final CullStateShard NO_CULL_STATE = NO_CULL;
        static final WriteMaskStateShard COLOR_ONLY = COLOR_WRITE;

        private Shards() {
            super(null, null, null);
        }
    }
}
