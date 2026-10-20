package com.createstats.pducabinet.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
final class CabinetRenderTypes {
    static final ResourceLocation GLASS=ResourceLocation.fromNamespaceAndPath("create_stats","textures/block/pdu_cabinet/glass.png");

    static final RenderType PANE=glass("create_stats_cabinet_glass_pane",Shards.TRANSLUCENT);

    static final RenderType GLINT=glass("create_stats_cabinet_glass_glint",Shards.ADDED);

    private CabinetRenderTypes() {}

    private static RenderType glass(String name,RenderStateShard.TransparencyStateShard blend) {
        return RenderType.create(name,DefaultVertexFormat.NEW_ENTITY,VertexFormat.Mode.QUADS,256,false,true,
                RenderType.CompositeState.builder()
                        .setShaderState(Shards.ENTITY_TRANSLUCENT)
                        .setTextureState(new RenderStateShard.TextureStateShard(GLASS,false,false))
                        .setTransparencyState(blend)
                        .setCullState(Shards.NO_CULL_STATE)
                        .setLightmapState(Shards.LIGHTMAP_ON)
                        .setOverlayState(Shards.OVERLAY_ON)
                        .setWriteMaskState(Shards.COLOR_ONLY)
                        .createCompositeState(false));
    }

    private static final class Shards extends RenderStateShard {
        static final ShaderStateShard ENTITY_TRANSLUCENT=RENDERTYPE_ENTITY_TRANSLUCENT_SHADER;
        static final TransparencyStateShard TRANSLUCENT=TRANSLUCENT_TRANSPARENCY;
        static final TransparencyStateShard ADDED=LIGHTNING_TRANSPARENCY;
        static final CullStateShard NO_CULL_STATE=NO_CULL;
        static final LightmapStateShard LIGHTMAP_ON=LIGHTMAP;
        static final OverlayStateShard OVERLAY_ON=OVERLAY;
        static final WriteMaskStateShard COLOR_ONLY=COLOR_WRITE;

        private Shards() { super(null,null,null); }
    }
}
