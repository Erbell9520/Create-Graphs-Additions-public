package com.createstats.chunkloader.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class CrownRenderTypes {
    public static final RenderType MATTER = RenderType.create(
            "create_stats_crown_matter",
            DefaultVertexFormat.BLOCK,
            VertexFormat.Mode.QUADS,
            262_144,
            true,
            false,
            RenderType.CompositeState.builder()
                    .setLightmapState(Shards.LIGHTMAP_ON)
                    .setShaderState(Shards.SOLID)
                    .setTextureState(Shards.BLOCKS)
                    .createCompositeState(true));

    public static final RenderType FLOOR = RenderType.create(
            "create_stats_crown_floor",
            DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS,
            8_192,
            false,
            false,
            RenderType.CompositeState.builder()
                    .setShaderState(Shards.POS_COLOR)
                    .setTransparencyState(Shards.ADDITIVE)
                    .setCullState(Shards.NO_CULL_STATE)
                    .setWriteMaskState(Shards.COLOR_ONLY)
                    .setLayeringState(Shards.OFFSET)
                    .createCompositeState(false));

    private CrownRenderTypes() {
    }

    private static final class Shards extends RenderStateShard {
        static final ShaderStateShard SOLID = RENDERTYPE_SOLID_SHADER;
        static final TextureStateShard BLOCKS = BLOCK_SHEET_MIPPED;
        static final LightmapStateShard LIGHTMAP_ON = LIGHTMAP;
        static final ShaderStateShard POS_COLOR = POSITION_COLOR_SHADER;

        static final TransparencyStateShard ADDITIVE = LIGHTNING_TRANSPARENCY;
        static final CullStateShard NO_CULL_STATE = NO_CULL;
        static final WriteMaskStateShard COLOR_ONLY = COLOR_WRITE;

        static final LayeringStateShard OFFSET = POLYGON_OFFSET_LAYERING;

        private Shards() {
            super(null, null, null);
        }
    }
}
