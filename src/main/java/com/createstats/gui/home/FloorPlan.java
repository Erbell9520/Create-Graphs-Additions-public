package com.createstats.gui.home;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class FloorPlan implements AutoCloseable {
    private static final int RESAMPLE_TICKS = 100;

    public static final int MAX = 128;

    private final ResourceLocation id;
    @Nullable
    private DynamicTexture texture;
    private int texW;
    private int texH;

    @Nullable
    private PlanGrid grid;
    private int x0 = Integer.MIN_VALUE;
    private int z0;
    private int cols;
    private int rows;
    private int floorY;
    private boolean light;
    private long sampledAt = -1;

    private boolean dirty = true;
    private int paintedScale = -1;
    private long paintedLamps;

    public FloorPlan(ResourceLocation id) {
        this.id = id;
    }

    public void ensure(ClientLevel level, int x0, int z0, int cols, int rows, int floorY, boolean lightMode, long now,
                       boolean frozen) {
        if (frozen && grid != null) {
            return;
        }
        cols = Math.max(1, Math.min(MAX, cols));
        rows = Math.max(1, Math.min(MAX, rows));
        final boolean same = x0 == this.x0 && z0 == this.z0 && cols == this.cols && rows == this.rows
                && floorY == this.floorY && lightMode == this.light;
        if (same && sampledAt >= 0 && now - sampledAt < RESAMPLE_TICKS && now >= sampledAt) {
            return;
        }
        this.x0 = x0;
        this.z0 = z0;
        this.cols = cols;
        this.rows = rows;
        this.floorY = floorY;
        this.light = lightMode;
        this.sampledAt = now;
        this.grid = PlanSampler.sample(level, x0, z0, cols, rows, floorY, lightMode);
        this.dirty = true;
        this.generation++;
    }

    public int generation() {
        return generation;
    }

    private int generation;

    public void paint(int scale, List<PlanPainter.Lamp> lamps, long lampsKey) {
        if (grid == null) {
            return;
        }
        if (!dirty && scale == paintedScale && lampsKey == paintedLamps && texture != null) {
            return;
        }
        final PlanPainter.Picture pic = PlanPainter.paint(grid, scale, lamps, light);
        upload(pic);
        paintedScale = scale;
        paintedLamps = lampsKey;
        dirty = false;
    }

    private void upload(PlanPainter.Picture pic) {
        if (texture == null || texW != pic.w() || texH != pic.h()) {
            texW = pic.w();
            texH = pic.h();
            texture = new DynamicTexture(new NativeImage(NativeImage.Format.RGBA, texW, texH, true));
            Minecraft.getInstance().getTextureManager().register(id, texture);
        }
        final NativeImage image = texture.getPixels();
        if (image == null) {
            return;
        }
        final int[] argb = pic.argb();
        for (int y = 0; y < texH; y++) {
            for (int x = 0; x < texW; x++) {
                final int c = argb[y * texW + x];

                image.setPixelRGBA(x, y, (c & 0xFF00FF00) | ((c >> 16) & 0xFF) | ((c & 0xFF) << 16));
            }
        }
        texture.upload();
    }

    public void draw(GuiGraphics g, int px, int py) {
        if (texture == null) {
            return;
        }
        g.blit(id, px, py, 0, 0, texW, texH, texW, texH);
    }

    public boolean spawnable(int x, int z) {
        return light && grid != null && grid.has(x, z, PlanGrid.F_SPAWN);
    }

    public byte kind(int x, int z) {
        return grid == null ? PlanGrid.NONE : grid.kind(x, z);
    }

    public byte sym(int x, int z) {
        return grid == null ? PlanGrid.SYM_NONE : grid.sym(x, z);
    }

    public boolean has(int x, int z, int bits) {
        return grid != null && grid.has(x, z, bits);
    }

    public int light(int x, int z) {
        return light && grid != null ? grid.light(x, z) : 15;
    }

    public boolean isClearFloor(int x, int z) {
        return grid != null && grid.isClearFloor(x, z);
    }

    public int floorY() {
        return grid == null ? Integer.MIN_VALUE : grid.floorY();
    }

    public int firstX() {
        return grid == null ? x0 : grid.x0;
    }

    public int firstZ() {
        return grid == null ? z0 : grid.z0;
    }

    public int cols() {
        return grid == null ? 0 : grid.cols;
    }

    public int rows() {
        return grid == null ? 0 : grid.rows;
    }

    public void invalidate() {
        sampledAt = -1;
    }

    @Override
    public void close() {
        if (texture != null) {
            Minecraft.getInstance().getTextureManager().release(id);
            texture = null;
        }
    }
}
