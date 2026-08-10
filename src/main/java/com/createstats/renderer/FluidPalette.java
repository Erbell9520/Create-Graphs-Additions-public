package com.createstats.renderer;

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidStack;

@OnlyIn(Dist.CLIENT)
public final class FluidPalette {
    private FluidPalette() {}

    private static final Object2IntOpenHashMap<Fluid> CACHE = new Object2IntOpenHashMap<>();

    private static final int FALLBACK_WATER = 0xFF2E8FE0;
    private static final int FALLBACK_LAVA  = 0xFFCF7515;
    private static final int FALLBACK_GENERIC = 0xFF6CC1E5;

    public static int colorFor(FluidStack stack) {
        if (stack == null || stack.isEmpty()) return FALLBACK_GENERIC;
        Fluid fluid = stack.getFluid();
        if (fluid == null) return FALLBACK_GENERIC;
        synchronized (CACHE) {
            int cached = CACHE.getOrDefault(fluid, Integer.MIN_VALUE);
            if (cached != Integer.MIN_VALUE) return cached;
        }
        int color = computeColor(stack, fluid);
        synchronized (CACHE) {
            CACHE.put(fluid, color);
        }
        return color;
    }

    public static int colorForFluidId(String fluidId) {
        if (fluidId == null || fluidId.isEmpty()) return FALLBACK_GENERIC;
        ResourceLocation id;
        try {
            id = ResourceLocation.parse(fluidId);
        } catch (Exception e) {
            return FALLBACK_GENERIC;
        }
        Fluid fluid = net.minecraft.core.registries.BuiltInRegistries.FLUID.get(id);
        if (fluid == null || fluid == Fluids.EMPTY) {
            if ("minecraft:water".equals(fluidId)) return FALLBACK_WATER;
            if ("minecraft:lava".equals(fluidId)) return FALLBACK_LAVA;
            return FALLBACK_GENERIC;
        }
        return colorFor(new FluidStack(fluid, 1));
    }

    private static int computeColor(FluidStack stack, Fluid fluid) {
        // hard fallbacks for vanilla fluids that are texture-driven and would otherwise read as -1
        if (fluid == Fluids.WATER || fluid == Fluids.FLOWING_WATER) return FALLBACK_WATER;
        if (fluid == Fluids.LAVA || fluid == Fluids.FLOWING_LAVA) return FALLBACK_LAVA;

        int sampled = FALLBACK_GENERIC;
        try {
            IClientFluidTypeExtensions ext = IClientFluidTypeExtensions.of(fluid);
            ResourceLocation still = ext.getStillTexture(stack);
            if (still != null) {
                TextureAtlasSprite sprite = Minecraft.getInstance()
                    .getModelManager()
                    .getAtlas(InventoryMenu.BLOCK_ATLAS)
                    .getSprite(still);
                if (sprite != null) {
                    var contents = sprite.contents();
                    int w = contents.width();
                    int h = contents.height();
                    if (w > 0 && h > 0) {
                        // average a 4-pixel cluster at center for better stability
                        long aR=0, aG=0, aB=0; int n=0;
                        int cx = w / 2; int cy = h / 2;
                        for (int dy = -1; dy <= 1; dy++) {
                            for (int dx = -1; dx <= 1; dx++) {
                                int px = Math.max(0, Math.min(w-1, cx+dx));
                                int py = Math.max(0, Math.min(h-1, cy+dy));
                                int rgba = contents.getOriginalImage().getPixelRGBA(px, py);
                                int a = (rgba >>> 24) & 0xFF;
                                if (a < 16) continue;
                                int b = (rgba >>> 16) & 0xFF;
                                int g = (rgba >>>  8) & 0xFF;
                                int r = (rgba       ) & 0xFF;
                                aR += r; aG += g; aB += b; n++;
                            }
                        }
                        if (n > 0) {
                            sampled = 0xFF000000 | ((int)(aR/n) << 16) | ((int)(aG/n) << 8) | (int)(aB/n);
                        }
                    }
                }
            }
            int tint = ext.getTintColor(stack);
            if (tint != -1 && (tint & 0x00FFFFFF) != 0x00FFFFFF) {
                int tr = (tint >> 16) & 0xFF;
                int tg = (tint >>  8) & 0xFF;
                int tb =  tint        & 0xFF;
                int sr = (sampled >> 16) & 0xFF;
                int sg = (sampled >>  8) & 0xFF;
                int sb =  sampled        & 0xFF;
                int rr = (sr * tr) / 255;
                int rg = (sg * tg) / 255;
                int rb = (sb * tb) / 255;
                sampled = 0xFF000000 | (rr << 16) | (rg << 8) | rb;
            }
        } catch (Throwable t) {
            return FALLBACK_GENERIC;
        }
        return sampled;
    }

    /** Resolves the still-fluid atlas sprite for a fluid registry id, or null if unavailable. */
    public static TextureAtlasSprite stillSpriteForFluidId(String fluidId) {
        if (fluidId == null || fluidId.isEmpty() || !fluidId.contains(":")) return null;
        try {
            ResourceLocation id = ResourceLocation.parse(fluidId);
            Fluid fluid = net.minecraft.core.registries.BuiltInRegistries.FLUID.get(id);
            if (fluid == null || fluid == Fluids.EMPTY) return null;
            FluidStack stack = new FluidStack(fluid, 1);
            IClientFluidTypeExtensions ext = IClientFluidTypeExtensions.of(fluid);
            ResourceLocation still = ext.getStillTexture(stack);
            if (still == null) return null;
            return Minecraft.getInstance().getModelManager().getAtlas(InventoryMenu.BLOCK_ATLAS).getSprite(still);
        } catch (Throwable t) {
            return null;
        }
    }

    /** Human-friendly display name for a fluid registry id. */
    public static String displayNameForFluidId(String fluidId) {
        if (fluidId == null || fluidId.isEmpty() || !fluidId.contains(":")) return "None";
        try {
            ResourceLocation id = ResourceLocation.parse(fluidId);
            Fluid fluid = net.minecraft.core.registries.BuiltInRegistries.FLUID.get(id);
            if (fluid != null && fluid != Fluids.EMPTY) {
                String n = new FluidStack(fluid, 1).getHoverName().getString();
                if (n != null && !n.isEmpty()) return n;
            }
            String path = id.getPath();
            return path.isEmpty() ? "Fluid" : Character.toUpperCase(path.charAt(0)) + path.substring(1).replace('_', ' ');
        } catch (Throwable t) {
            return "Fluid";
        }
    }
}
