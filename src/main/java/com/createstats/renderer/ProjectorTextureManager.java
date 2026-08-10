package com.createstats.renderer;

import com.createstats.CreateStats;
import java.util.HashMap;
import com.createstats.display.StatsDisplaySettings;
import java.util.List;
import com.createstats.display.DisplayTheme;
import com.createstats.display.HologramSettings;
import java.util.ArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import com.mojang.blaze3d.platform.NativeImage;
import java.util.Iterator;
import net.minecraft.resources.ResourceLocation;
import com.createstats.blockentity.HolographicProjectorBE;
import net.minecraft.core.BlockPos;
import java.util.Map;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class ProjectorTextureManager
{
    private static final Map<BlockPos, TextureEntry> textures;
    private static final int UPDATE_INTERVAL = 5;
    private static final int TEX_WIDTH = 512;
    private static final int TEX_HEIGHT = 384;
    private static final int MAX_IDLE_TICKS = 200;
    private static int cleanupCounter;
    private static final int CLEANUP_INTERVAL = 100;
    
    private ProjectorTextureManager() {
    }
    
    public static ResourceLocation getOrUpdateTexture(HolographicProjectorBE be) {
        final BlockPos pos = be.getBlockPos();
        TextureEntry entry = ProjectorTextureManager.textures.get(pos);
        if (entry == null) {
            entry = createEntry(pos);
            if (entry == null) {
                return null;
            }
            ProjectorTextureManager.textures.put(pos, entry);
        }
        final long currentTick = getGameTime();
        entry.lastAccessTick = currentTick;
        if (currentTick - entry.lastUpdateTick >= 5L || entry.needsRedraw) {
            updateTexture(entry, be);
            entry.lastUpdateTick = currentTick;
            entry.needsRedraw = false;
        }
        if (++ProjectorTextureManager.cleanupCounter >= 100) {
            ProjectorTextureManager.cleanupCounter = 0;
            cleanupStale();
        }
        return entry.location;
    }
    
    public static void markDirty(BlockPos pos) {
        final TextureEntry entry = ProjectorTextureManager.textures.get(pos);
        if (entry != null) {
            entry.needsRedraw = true;
        }
    }
    
    public static void cleanup(BlockPos pos) {
        final TextureEntry entry = ProjectorTextureManager.textures.remove(pos);
        if (entry != null) {
            disposeEntry(entry);
        }
    }
    
    public static void cleanupAll() {
        for (TextureEntry e : ProjectorTextureManager.textures.values()) {
            disposeEntry(e);
        }
        ProjectorTextureManager.textures.clear();
    }
    
    private static TextureEntry createEntry(BlockPos pos) {
        NativeImage image = null;
        try {
            image = new NativeImage(512, 384, false);
            final DynamicTexture dynTex = new DynamicTexture(image);
            final String name = "create_stats/projector_" + pos.getX() + "_" + pos.getY() + "_" + pos.getZ();
            final ResourceLocation loc = Minecraft.getInstance().getTextureManager().register(name, dynTex);
            final TextureEntry entry = new TextureEntry();
            entry.image = image;
            entry.dynTex = dynTex;
            entry.location = loc;
            entry.lastUpdateTick = 0L;
            entry.lastAccessTick = getGameTime();
            entry.needsRedraw = true;
            return entry;
        }
        catch (Exception e) {
            CreateStats.LOGGER.warn("Failed to create projector texture for {}", pos, e);
            if (image != null) {
                image.close();
            }
            return null;
        }
    }
    
    private static void updateTexture(TextureEntry entry, HolographicProjectorBE be) {
        final HologramSettings settings = be.getHologramSettings();
        final DisplayTheme theme = settings.getDisplayTheme();
        final List<HolographicProjectorBE.DataChannel> allChannels = be.getActiveChannels();
        final List<HolographicProjectorBE.DataChannel> channels = new ArrayList<HolographicProjectorBE.DataChannel>();
        for (HolographicProjectorBE.DataChannel ch : allChannels) {
            if (!settings.getHiddenChannelIds().contains(ch.getId())) {
                channels.add(ch);
            }
        }
        if (channels.isEmpty()) {
            TextureGraphRenderer.fillRect(entry.image, 0, 0, 512, 384, theme.graphBg);
            entry.dynTex.upload();
            return;
        }
        final int totalPages = channels.size();
        int currentPage = 0;
        final int cycleInterval = settings.getCycleInterval();
        if (totalPages > 1 && cycleInterval > 0 && be.getLevel() != null) {
            final long gameTime = be.getLevel().getGameTime();
            currentPage = (int)(gameTime / cycleInterval % totalPages);
        }
        currentPage = Math.min(currentPage, totalPages - 1);
        final HolographicProjectorBE.DataChannel currentChannel = channels.get(currentPage);
        // channelIdx must reflect the channel's position in the unfiltered list so that color
        // assignment (CHANNEL_COLORS[channelIdx % len]) stays stable even when channels are hidden.
        int channelIdx = currentPage;
        for (int i = 0; i < allChannels.size(); i++) {
            if (allChannels.get(i).getId().equals(currentChannel.getId())) {
                channelIdx = i;
                break;
            }
        }
        final StatsDisplaySettings renderSettings = buildRenderSettings(settings);
        TextureGraphRenderer.renderProjectorPage(entry.image, currentChannel, channelIdx, currentPage, totalPages, theme, renderSettings);
        entry.dynTex.upload();
    }
    
    private static StatsDisplaySettings buildRenderSettings(HologramSettings hs) {
        final StatsDisplaySettings s = new StatsDisplaySettings();
        s.setGraphType(mapDisplayModeToGraphType(hs.getDisplayMode()));
        s.setTimeRange(hs.getTimeRange());
        s.setShowGrid(hs.isShowGrid());
        s.setShowAxisLabels(hs.isShowLabels());
        s.setDisplayTheme(hs.getDisplayTheme());
        return s;
    }

    private static StatsDisplaySettings.GraphType mapDisplayModeToGraphType(HologramDisplayMode mode) {
        return switch (mode) {
            case BAR_3D    -> StatsDisplaySettings.GraphType.BAR_CHART;
            case GAUGE_3D  -> StatsDisplaySettings.GraphType.GAUGE;
            default        -> StatsDisplaySettings.GraphType.LINE_CHART;
        };
    }
    
    private static void cleanupStale() {
        final long currentTick = getGameTime();
        final Iterator<Map.Entry<BlockPos, TextureEntry>> it = ProjectorTextureManager.textures.entrySet().iterator();
        while (it.hasNext()) {
            final Map.Entry<BlockPos, TextureEntry> mapEntry = it.next();
            if (currentTick - mapEntry.getValue().lastAccessTick > 200L) {
                disposeEntry(mapEntry.getValue());
                it.remove();
            }
        }
    }
    
    private static void disposeEntry(TextureEntry entry) {
        try {
            if (entry.dynTex != null) {
                entry.dynTex.close();
            }
        }
        catch (RuntimeException ex) {
            CreateStats.LOGGER.warn("Failed to dispose projector texture", ex);
        }
    }
    
    private static long getGameTime() {
        if (Minecraft.getInstance().level != null) {
            return Minecraft.getInstance().level.getGameTime();
        }
        return System.currentTimeMillis() / 50L;
    }
    
    static {
        textures = new HashMap<BlockPos, TextureEntry>();
        ProjectorTextureManager.cleanupCounter = 0;
    }
    
    private static class TextureEntry
    {
        NativeImage image;
        DynamicTexture dynTex;
        ResourceLocation location;
        long lastUpdateTick;
        long lastAccessTick;
        boolean needsRedraw;
    }
}
