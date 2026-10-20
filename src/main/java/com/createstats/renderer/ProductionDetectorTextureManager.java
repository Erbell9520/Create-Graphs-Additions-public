package com.createstats.renderer;

import com.createstats.CreateStats;
import com.createstats.blockentity.inventory.InventoryFace;
import com.createstats.inventory.core.ChartHistory;
import com.createstats.renderer.inventory.FacePainter;
import com.createstats.renderer.inventory.GlassCanvas;
import com.createstats.renderer.inventory.InventoryRenderTypes;
import com.createstats.renderer.inventory.LampPainter;
import com.createstats.renderer.inventory.MipChain;
import com.createstats.renderer.inventory.MipTexture;
import com.createstats.renderer.inventory.PaperPainter;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

@OnlyIn(Dist.CLIENT)
public final class ProductionDetectorTextureManager {
    public static final int GLASS_W = FacePainter.W;
    public static final int GLASS_H = FacePainter.H * 2;
    private static final int GLASS_LEVELS = 4;
    public static final int PAPER_W = 138;
    public static final int PAPER_H = 70;
    private static final int PAPER_TEX_W = 144;
    private static final int PAPER_TEX_H = 72;
    private static final int PAPER_LEVELS = 3;

    public static final float PAPER_U1 = PAPER_W / (float) PAPER_TEX_W;
    public static final float PAPER_V1 = PAPER_H / (float) PAPER_TEX_H;

    private static final long IDLE_MS = 30_000L;
    private static final long BAKE_WINDOW_NS = 4_000_000L;
    private static final int BAKES_PER_WINDOW = 3;

    private static final Map<Long, Entry> ENTRIES = new HashMap<>();
    private static final ResourceLocation LAMPS_ID = ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID,
            "inventory_display/lamps");
    private static final ResourceLocation SCREEN_PAPER_ID = ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID,
            "inventory_display/screen_paper");
    @Nullable
    private static MipTexture lamps;
    @Nullable
    private static RenderType lampsType;
    @Nullable
    private static DynamicTexture screenPaper;
    private static int screenPaperW;
    private static int screenPaperH;
    private static int screenPaperHash = Integer.MIN_VALUE;
    private static long lastSweep;
    private static long bakeWindowStart;
    private static int bakesInWindow;

    private ProductionDetectorTextureManager() {
    }

    public enum Mode {
        DEAD,
        WARMING,
        LIVE
    }

    public static final class Entry {
        final Level level;
        final MipTexture glass;
        final ResourceLocation glassId;
        final RenderType glassType;
        final MipTexture paper;
        final ResourceLocation paperId;
        final RenderType paperType;
        @Nullable
        InventoryFace face;
        @Nullable
        Mode mode;
        boolean painted;
        int paperHash = Integer.MIN_VALUE;
        FacePainter.Layers layers;
        long lastUse;

        Entry(Level level, long key) {
            this.level = level;
            final String id = Long.toUnsignedString(key, 36);
            this.glass = new MipTexture(GLASS_W, GLASS_H, GLASS_LEVELS);
            this.paper = new MipTexture(PAPER_TEX_W, PAPER_TEX_H, PAPER_LEVELS);
            this.glassId = ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, "inventory_display/glass_" + id);
            this.paperId = ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, "inventory_display/paper_" + id);
            Minecraft.getInstance().getTextureManager().register(this.glassId, this.glass);
            Minecraft.getInstance().getTextureManager().register(this.paperId, this.paper);
            this.glassType = InventoryRenderTypes.surface(this.glassId, true);
            this.paperType = InventoryRenderTypes.surface(this.paperId, true);
        }

        void close() {
            Minecraft.getInstance().getTextureManager().release(this.glassId);
            Minecraft.getInstance().getTextureManager().release(this.paperId);
        }

        public RenderType glassType() {
            return this.glassType;
        }

        public RenderType paperType() {
            return this.paperType;
        }

        public boolean painted() {
            return this.painted;
        }

        public FacePainter.Layers layers() {
            return this.layers;
        }
    }

    public static Entry glass(BlockPos pos, Level level, @Nullable InventoryFace face, Mode mode) {
        sweep();
        final long key = pos.asLong();
        Entry e = ENTRIES.get(key);
        if (e != null && e.level != level) {
            e.close();
            ENTRIES.remove(key);
            e = null;
        }
        if (e == null) {
            e = new Entry(level, key);
            ENTRIES.put(key, e);
        }
        e.lastUse = System.currentTimeMillis();
        final boolean stale = !e.painted || e.face != face || e.mode != mode;
        if (stale && (!e.painted || mayBake())) {
            // dead = honest blank, no stale numbers
            final FacePainter.Layers layers = mode == Mode.DEAD || face == null ? FacePainter.blank()
                    : mode == Mode.WARMING ? FacePainter.warming(face)
                    : face.page == InventoryFace.Page.BLANK ? FacePainter.blank() : FacePainter.paint(face);
            final int[] px = new int[GLASS_W * GLASS_H];
            System.arraycopy(layers.base.px, 0, px, 0, layers.base.px.length);
            System.arraycopy(layers.over.px, 0, px, layers.base.px.length, layers.over.px.length);
            e.glass.upload(px);
            e.layers = layers;
            e.face = face;
            e.mode = mode;
            e.painted = true;
        }
        return e;
    }

    private static boolean mayBake() {
        final long now = System.nanoTime();
        if (now - bakeWindowStart > BAKE_WINDOW_NS) {
            bakeWindowStart = now;
            bakesInWindow = 0;
        }
        if (bakesInWindow >= BAKES_PER_WINDOW) {
            return false;
        }
        bakesInWindow++;
        return true;
    }

    public static RenderType paper(Entry e, ChartHistory chart, int scale, float low, float high) {
        final int hash = 31 * (31 * (31 * Long.hashCode(chart.lastPeriod()) + chart.size()) + scale)
                + Float.hashCode(low) * 7 + Float.hashCode(high) * 13;
        if (hash != e.paperHash) {
            e.paperHash = hash;
            final GlassCanvas c = PaperPainter.paint(chart, PAPER_W, PAPER_H, 1, scale, low, high);
            e.paper.upload(MipChain.pad(c.px, PAPER_W, PAPER_H, PAPER_TEX_W, PAPER_TEX_H));
        }
        return e.paperType;
    }

    public static RenderType lamps() {
        if (lamps == null || lampsType == null) {
            lamps = new MipTexture(LampPainter.TEX_W, LampPainter.TEX_H, LampPainter.MAX_LEVEL);
            lamps.upload(LampPainter.paint());
            Minecraft.getInstance().getTextureManager().register(LAMPS_ID, lamps);
            lampsType = InventoryRenderTypes.surface(LAMPS_ID, true);
        }
        return lampsType;
    }

    public static ResourceLocation screenPaper(ChartHistory chart, int width, int height, int days, int scale, float low,
                                               float high) {
        if (screenPaper == null || screenPaperW != width || screenPaperH != height) {
            if (screenPaper != null) {
                Minecraft.getInstance().getTextureManager().release(SCREEN_PAPER_ID);
            }
            screenPaper = new DynamicTexture(new NativeImage(NativeImage.Format.RGBA, width, height, true));
            screenPaperW = width;
            screenPaperH = height;
            screenPaperHash = Integer.MIN_VALUE;
            Minecraft.getInstance().getTextureManager().register(SCREEN_PAPER_ID, screenPaper);
        }
        final int hash = 31 * (31 * (31 * (31 * Long.hashCode(chart.lastPeriod()) + chart.size()) + scale) + days)
                + Float.hashCode(low) * 7 + Float.hashCode(high) * 13;
        if (hash != screenPaperHash) {
            screenPaperHash = hash;
            final GlassCanvas c = PaperPainter.paint(chart, width, height, days, scale, low, high);
            final NativeImage image = screenPaper.getPixels();
            if (image != null) {
                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        image.setPixelRGBA(x, y, BitmapFont.argbToAbgr(c.px[y * width + x]));
                    }
                }
                screenPaper.upload();
            }
        }
        return SCREEN_PAPER_ID;
    }

    private static void sweep() {
        final long now = System.currentTimeMillis();
        if (now - lastSweep < 5_000L) {
            return;
        }
        lastSweep = now;
        final Iterator<Map.Entry<Long, Entry>> it = ENTRIES.entrySet().iterator();
        while (it.hasNext()) {
            final Entry e = it.next().getValue();
            if (now - e.lastUse > IDLE_MS) {
                e.close();
                it.remove();
            }
        }
    }

    public static void cleanup(BlockPos pos) {
        final Entry e = ENTRIES.remove(pos.asLong());
        if (e != null) {
            e.close();
        }
    }

    public static void cleanupAll() {
        for (Entry e : ENTRIES.values()) {
            e.close();
        }
        ENTRIES.clear();
        if (screenPaper != null) {
            Minecraft.getInstance().getTextureManager().release(SCREEN_PAPER_ID);
            screenPaper = null;
        }
    }

    @EventBusSubscriber(modid = CreateStats.MOD_ID, value = Dist.CLIENT)
    public static final class Events {
        private Events() {
        }

        @SubscribeEvent
        public static void unload(LevelEvent.Unload event) {
            if (event.getLevel().isClientSide()) {
                cleanupAll();
            }
        }
    }
}
