package com.createstats.cinecam.client;

import com.createstats.mixin.LevelRendererCullAccessor;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SectionOcclusionGraph;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.fml.ModList;

import javax.annotation.Nullable;

public final class AwayCull {
    private static final boolean REPLACED = ModList.get().isLoaded("sodium") || ModList.get().isLoaded("embeddium");

    private static final long WARMUP_NANOS = 1_000_000_000L;

    @Nullable private static volatile AwayCull active;

    private final SectionOcclusionGraph graph = new SectionOcclusionGraph();
    private final ObjectArrayList<SectionRenderDispatcher.RenderSection> visible = new ObjectArrayList<>(10000);
    @Nullable private volatile ViewArea area;
    private double camX = Double.MIN_VALUE, camY = Double.MIN_VALUE, camZ = Double.MIN_VALUE;
    private double rotX = Double.MIN_VALUE, rotY = Double.MIN_VALUE;

    private long emptyFrom = System.nanoTime();

    @Nullable private volatile SectionOcclusionGraph playerGraph;
    @Nullable private ObjectArrayList<SectionRenderDispatcher.RenderSection> playerVisible;
    private double playerX, playerY, playerZ, playerRotX, playerRotY;
    private volatile boolean swapped;

    void begin(LevelRenderer renderer) {
        if (REPLACED) return;
        LevelRendererCullAccessor lr = (LevelRendererCullAccessor) renderer;
        ViewArea now = lr.createstats$viewArea();
        if (now == null) return;
        if (now != area) {
            graph.waitAndReset(now);
            visible.clear();
            area = now;
            camX = camY = camZ = rotX = rotY = Double.MIN_VALUE;
            emptyFrom = System.nanoTime();
        }
        playerGraph = lr.createstats$graph();
        playerVisible = lr.createstats$visible();
        playerX = lr.createstats$prevCamX();
        playerY = lr.createstats$prevCamY();
        playerZ = lr.createstats$prevCamZ();
        playerRotX = lr.createstats$prevCamRotX();
        playerRotY = lr.createstats$prevCamRotY();
        lr.createstats$setGraph(graph);
        lr.createstats$setVisible(visible);
        lr.createstats$setPrevCamX(camX);
        lr.createstats$setPrevCamY(camY);
        lr.createstats$setPrevCamZ(camZ);
        lr.createstats$setPrevCamRotX(rotX);
        lr.createstats$setPrevCamRotY(rotY);
        swapped = true;
    }

    void end(LevelRenderer renderer) {
        if (!swapped) return;
        swapped = false;
        LevelRendererCullAccessor lr = (LevelRendererCullAccessor) renderer;
        camX = lr.createstats$prevCamX();
        camY = lr.createstats$prevCamY();
        camZ = lr.createstats$prevCamZ();
        rotX = lr.createstats$prevCamRotX();
        rotY = lr.createstats$prevCamRotY();
        ViewArea now = lr.createstats$viewArea();
        if (now != area) {
            area = now;
            if (playerGraph != null) playerGraph.waitAndReset(now);
            if (playerVisible != null) playerVisible.clear();
            playerX = playerY = playerZ = playerRotX = playerRotY = Double.MIN_VALUE;
        }
        lr.createstats$setGraph(playerGraph);
        lr.createstats$setVisible(playerVisible);
        lr.createstats$setPrevCamX(playerX);
        lr.createstats$setPrevCamY(playerY);
        lr.createstats$setPrevCamZ(playerZ);
        lr.createstats$setPrevCamRotX(playerRotX);
        lr.createstats$setPrevCamRotY(playerRotY);
        playerGraph = null;
        playerVisible = null;
    }

    boolean warming() {
        return !REPLACED && visible.isEmpty() && System.nanoTime() - emptyFrom < WARMUP_NANOS;
    }

    void open() {
        active = this;
    }

    void close() {
        if (active == this) active = null;
        graph.waitAndReset(null);
        visible.clear();
        area = null;
    }

    @Nullable
    private SectionOcclusionGraph other() {
        if (swapped) return playerGraph;
        return area != null ? graph : null;
    }

    public static void compiled(SectionRenderDispatcher.RenderSection section) {
        AwayCull a = active;
        SectionOcclusionGraph g = a == null || REPLACED ? null : a.other();
        if (g != null) g.onSectionCompiled(section);
    }

    public static void loaded(ChunkPos pos) {
        AwayCull a = active;
        SectionOcclusionGraph g = a == null || REPLACED ? null : a.other();
        if (g != null) g.onChunkLoaded(pos);
    }

    public static void invalidated() {
        AwayCull a = active;
        SectionOcclusionGraph g = a == null || REPLACED ? null : a.other();
        if (g != null) g.invalidate();
    }
}
