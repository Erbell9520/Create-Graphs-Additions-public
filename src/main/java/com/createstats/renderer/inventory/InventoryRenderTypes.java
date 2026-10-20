package com.createstats.renderer.inventory;

import com.createstats.CreateStats;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class InventoryRenderTypes {
    private InventoryRenderTypes() {
    }

    public static final ResourceLocation BLANK = ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID,
            "textures/special/blank.png");

    public static RenderType surface(ResourceLocation texture, boolean mipmap) {
        return RenderType.create("create_stats_inventory_surface", DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP,
                VertexFormat.Mode.QUADS, 1536, false, false,
                RenderType.CompositeState.builder()
                        .setShaderState(Shards.TEXT)
                        .setTextureState(new RenderStateShard.TextureStateShard(texture, false, mipmap))
                        .setTransparencyState(Shards.TRANSLUCENT)
                        .setLightmapState(Shards.LIGHTMAP_ON)
                        .setCullState(Shards.CULL_OFF)
                        .createCompositeState(false));
    }

    public static final RenderType ATLAS = surface(InventoryMenu.BLOCK_ATLAS, true);

    public static final RenderType FLAT = surface(BLANK, false);

    private static final class Shards extends RenderStateShard {
        static final ShaderStateShard TEXT = RENDERTYPE_TEXT_SHADER;
        static final TransparencyStateShard TRANSLUCENT = TRANSLUCENT_TRANSPARENCY;
        static final LightmapStateShard LIGHTMAP_ON = LIGHTMAP;
        static final CullStateShard CULL_OFF = NO_CULL;

        private Shards() {
            super(null, null, null);
        }
    }
}
