package com.createstats.mixin;

import com.createstats.cinecam.client.AwayCull;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
abstract class LevelRendererCullEventsMixin {
    @Inject(method = "addRecentlyCompiledSection", at = @At("TAIL"))
    private void createstats$compiled(SectionRenderDispatcher.RenderSection section, CallbackInfo ci) {
        AwayCull.compiled(section);
    }

    @Inject(method = "onChunkLoaded", at = @At("TAIL"))
    private void createstats$loaded(ChunkPos pos, CallbackInfo ci) {
        AwayCull.loaded(pos);
    }

    @Inject(method = "needsUpdate", at = @At("TAIL"))
    private void createstats$invalidated(CallbackInfo ci) {
        AwayCull.invalidated();
    }
}
