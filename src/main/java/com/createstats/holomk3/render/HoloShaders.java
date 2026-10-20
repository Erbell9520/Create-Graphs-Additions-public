package com.createstats.holomk3.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;

import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;

import com.createstats.CreateStats;

@OnlyIn(Dist.CLIENT)
public final class HoloShaders {
    private static final ResourceLocation ADDITIVE_ID =
            ResourceLocation.fromNamespaceAndPath("create_stats", "holo_additive");

    private static ShaderInstance additive;
    private static boolean failed;

    private static float time;
    private static float scan = 0.55f;
    private static float dissolve = 1.0f;

    private HoloShaders() {
    }

    public static void register(RegisterShadersEvent event) {
        try {
            event.registerShader(
                    new ShaderInstance(event.getResourceProvider(), ADDITIVE_ID,
                            DefaultVertexFormat.POSITION_COLOR),
                    shader -> additive = shader);
        } catch (Exception ex) {
            failed = true;
            additive = null;
            CreateStats.LOGGER.warn(
                    "[create_stats] Holographic Projector Mk III core shader did not build; "
                            + "falling back to the geometry only path. {}", ex.toString());
        }
    }

    public static boolean available() {
        return !failed && additive != null;
    }

    public static ShaderInstance activeOrFallback() {
        final ShaderInstance shader = additive;
        if (shader == null) {
            return GameRenderer.getPositionColorShader();
        }
        set(shader, "HoloTime", time);
        set(shader, "HoloScan", scan);
        set(shader, "HoloDissolve", dissolve);
        return shader;
    }

    private static void set(ShaderInstance shader, String name, float value) {
        final com.mojang.blaze3d.shaders.Uniform uniform = shader.getUniform(name);
        if (uniform != null) {
            uniform.set(value);
        }
    }

    public static void frame(float seconds, float scanDensity, float materialise) {
        time = seconds;
        scan = scanDensity;
        dissolve = materialise < 0.0f ? 0.0f : Math.min(materialise, 1.0f);
    }
}
