package com.createstats.surveyor.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

// polygon offset, the ribbon is exactly on the ground which is the worst case for z fighting
@OnlyIn(Dist.CLIENT)
public final class SurveyorRenderTypes {
    public static final RenderType RIBBON = RenderType.create(
            "create_stats_pathway_ribbon",
            DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS,
            8192,
            false,
            false,
            RenderType.CompositeState.builder()
                    .setShaderState(Shards.POS_COLOR)
                    .setLayeringState(Shards.OFFSET)
                    .setTransparencyState(Shards.TRANSLUCENT)
                    .setCullState(Shards.NO_CULL)
                    .setWriteMaskState(Shards.COLOR_ONLY)
                    .createCompositeState(false));

    public static final RenderType PARTS = RenderType.create(
            "create_stats_pathway_parts",
            DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS,
            4096,
            false,
            false,
            RenderType.CompositeState.builder()
                    .setShaderState(Shards.POS_COLOR)
                    .setTransparencyState(Shards.TRANSLUCENT)
                    .setCullState(Shards.NO_CULL)
                    .createCompositeState(false));

    public static final RenderType PAINT = RenderType.create(
            "create_stats_pathway_paint",
            DefaultVertexFormat.POSITION_COLOR_LIGHTMAP,
            VertexFormat.Mode.QUADS,
            16384,
            false,
            false,
            RenderType.CompositeState.builder()
                    .setShaderState(Shards.POS_COLOR_LIGHTMAP)
                    .setLightmapState(Shards.LIGHTMAP)
                    .setLayeringState(Shards.OFFSET)
                    .setTransparencyState(Shards.TRANSLUCENT)
                    .setCullState(Shards.NO_CULL)
                    .setWriteMaskState(Shards.COLOR_ONLY)
                    .createCompositeState(false));

    public static final RenderType THINGS = RenderType.create(
            "create_stats_pathway_things",
            DefaultVertexFormat.POSITION_COLOR_LIGHTMAP,
            VertexFormat.Mode.QUADS,
            8192,
            false,
            false,
            RenderType.CompositeState.builder()
                    .setShaderState(Shards.POS_COLOR_LIGHTMAP)
                    .setLightmapState(Shards.LIGHTMAP)
                    .setTransparencyState(Shards.TRANSLUCENT)
                    .setCullState(Shards.NO_CULL)
                    .createCompositeState(false));

    private SurveyorRenderTypes() {
    }

    private static final class Shards extends RenderStateShard {
        static final ShaderStateShard POS_COLOR = RenderStateShard.POSITION_COLOR_SHADER;
        static final ShaderStateShard POS_COLOR_LIGHTMAP = RenderStateShard.POSITION_COLOR_LIGHTMAP_SHADER;
        static final LightmapStateShard LIGHTMAP = RenderStateShard.LIGHTMAP;
        static final LayeringStateShard OFFSET = RenderStateShard.POLYGON_OFFSET_LAYERING;
        static final TransparencyStateShard TRANSLUCENT = RenderStateShard.TRANSLUCENT_TRANSPARENCY;
        static final CullStateShard NO_CULL = RenderStateShard.NO_CULL;
        static final WriteMaskStateShard COLOR_ONLY = RenderStateShard.COLOR_WRITE;

        private Shards() {
            super("create_stats_pathway_shards", () -> {
            }, () -> {
            });
        }
    }
}
