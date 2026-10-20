package com.createstats.mixin;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SectionOcclusionGraph;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LevelRenderer.class)
public interface LevelRendererCullAccessor {
    @Accessor("sectionOcclusionGraph")
    SectionOcclusionGraph createstats$graph();

    @Mutable
    @Accessor("sectionOcclusionGraph")
    void createstats$setGraph(SectionOcclusionGraph graph);

    @Accessor("visibleSections")
    ObjectArrayList<SectionRenderDispatcher.RenderSection> createstats$visible();

    @Mutable
    @Accessor("visibleSections")
    void createstats$setVisible(ObjectArrayList<SectionRenderDispatcher.RenderSection> visible);

    @Accessor("viewArea")
    ViewArea createstats$viewArea();

    @Accessor("prevCamX")
    double createstats$prevCamX();

    @Accessor("prevCamX")
    void createstats$setPrevCamX(double v);

    @Accessor("prevCamY")
    double createstats$prevCamY();

    @Accessor("prevCamY")
    void createstats$setPrevCamY(double v);

    @Accessor("prevCamZ")
    double createstats$prevCamZ();

    @Accessor("prevCamZ")
    void createstats$setPrevCamZ(double v);

    @Accessor("prevCamRotX")
    double createstats$prevCamRotX();

    @Accessor("prevCamRotX")
    void createstats$setPrevCamRotX(double v);

    @Accessor("prevCamRotY")
    double createstats$prevCamRotY();

    @Accessor("prevCamRotY")
    void createstats$setPrevCamRotY(double v);
}
