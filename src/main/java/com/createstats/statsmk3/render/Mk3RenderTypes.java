package com.createstats.statsmk3.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

// entityTranslucentEmissive sorts quads by distance on upload and wrecks layer order. own type
@OnlyIn(Dist.CLIENT)
public final class Mk3RenderTypes {
    public static final RenderType PANEL = RenderType.create(
            "create_stats_mk3_panel",
            DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS,
            2048,
            false,
            false,
            RenderType.CompositeState.builder()
                    .setShaderState(Shards.POS_COLOR)
                    .setTransparencyState(Shards.TRANSLUCENT)
                    .setCullState(Shards.NO_CULL_STATE)
                    .setWriteMaskState(Shards.COLOR_ONLY)
                    .createCompositeState(false));

    private Mk3RenderTypes() {
    }

    private static final class Shards extends RenderStateShard {
        static final ShaderStateShard POS_COLOR = POSITION_COLOR_SHADER;
        static final TransparencyStateShard TRANSLUCENT = TRANSLUCENT_TRANSPARENCY;
        static final CullStateShard NO_CULL_STATE = NO_CULL;
        static final WriteMaskStateShard COLOR_ONLY = COLOR_WRITE;

        private Shards() {
            super(null, null, null);
        }
    }
}
