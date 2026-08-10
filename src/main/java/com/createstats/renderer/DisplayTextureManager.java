package com.createstats.renderer;

import com.createstats.CreateStats;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HashSet;
import java.util.List;
import java.util.Comparator;
import com.createstats.dashboard.DashboardRenderer;
import com.createstats.dashboard.DashboardLayoutEngine;
import com.createstats.renderer.instrument.InstrumentRenderDispatcher;
import java.util.ArrayList;
import com.createstats.display.StatsDisplaySettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import com.mojang.blaze3d.platform.NativeImage;
import java.util.Iterator;
import net.minecraft.resources.ResourceLocation;
import com.createstats.blockentity.SourceConfig;
import com.createstats.blockentity.StatsDisplayPanelBE;
import net.minecraft.core.BlockPos;
import java.util.Map;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class DisplayTextureManager
{
    private static final Map<BlockPos, TextureEntry> textures;
    private static final int DEFAULT_UPDATE_INTERVAL = 5;
    private static final int DEFAULT_RESOLUTION = 256;
    private static final int MAX_IDLE_TICKS = 200;
    private static int cleanupCounter;
    private static final int CLEANUP_INTERVAL = 100;
    private static final long SOURCE_IDLE_TIMEOUT = 600L; // Imported from StatsDisplayPanelBE
    
    /**
     * Represents the operational state of a Stats Display Panel.
     */
    public enum PanelState {
        UNCONFIGURED,      // No sources registered, no setup needed
        SETUP_REQUIRED,    // StatsDisplayLink needs output type selection
        AWAITING_DATA,     // Sources registered but no data received yet
        BOOTING,           // First data just arrived, boot animation active
        LIVE,              // Normal operation with active data flow
        STALE,             // Data stream interrupted/timed out
        ERROR;             // Exceptional state (reserved for future use)
        
        public boolean shouldAnimate() {
            return this == BOOTING || this == LIVE || this == SETUP_REQUIRED;
        }
        
        public boolean isHealthy() {
            return this == LIVE || this == BOOTING;
        }
    }
    
    private DisplayTextureManager() {
    }
    
    public static ResourceLocation getOrUpdateTexture(StatsDisplayPanelBE be, float partialTick) {
        final BlockPos pos = be.getBlockPos();
        TextureEntry entry = DisplayTextureManager.textures.get(pos);
        if (entry == null) {
            entry = createTextureEntry(be);
            if (entry == null) {
                return null;
            }
            DisplayTextureManager.textures.put(pos, entry);
        }
        entry.lastAccessTick = getGameTime();
        final long currentTick = getGameTime();
        if (currentTick - entry.lastUpdateTick >= 5L || entry.needsRedraw) {
            updateTexture(entry, be);
            entry.lastUpdateTick = currentTick;
            entry.needsRedraw = false;
        }
        if (++DisplayTextureManager.cleanupCounter >= 100) {
            DisplayTextureManager.cleanupCounter = 0;
            cleanupStaleTextures();
        }
        return entry.textureLocation;
    }
    
    public static void markDirty(BlockPos pos) {
        final TextureEntry entry = DisplayTextureManager.textures.get(pos);
        if (entry != null) {
            entry.needsRedraw = true;
        }
    }
    
    public static void disposeTexture(BlockPos pos) {
        final TextureEntry entry = DisplayTextureManager.textures.remove(pos);
        if (entry != null) {
            disposeEntry(entry);
        }
        NeedlePhysicsRegistry.remove(pos);
    }
    
    public static void disposeAll() {
        for (TextureEntry entry : DisplayTextureManager.textures.values()) {
            disposeEntry(entry);
        }
        DisplayTextureManager.textures.clear();
        NeedlePhysicsRegistry.clear();
        TopologyRenderer.disposeAll();
    }
    
    private static TextureEntry createTextureEntry(StatsDisplayPanelBE be) {
        final int mbW = be.getMultiblockWidth();
        final int mbH = be.getMultiblockHeight();
        final int resolution = 256;
        int texW = resolution * mbW;
        int texH = resolution * mbH;
        texW = Math.min(texW, 1024);
        texH = Math.min(texH, 1024);
        try {
            final NativeImage image = new NativeImage(texW, texH, false);
            final DynamicTexture dynamicTexture = new DynamicTexture(image);
            final BlockPos pos = be.getBlockPos();
            final String texName = "create_stats/panel_" + pos.getX() + "_" + pos.getY() + "_" + pos.getZ();
            final ResourceLocation location = Minecraft.getInstance().getTextureManager().register(texName, dynamicTexture);
            final TextureEntry entry = new TextureEntry();
            entry.image = image;
            entry.dynamicTexture = dynamicTexture;
            entry.textureLocation = location;
            entry.lastUpdateTick = 0L;
            entry.lastAccessTick = getGameTime();
            entry.needsRedraw = true;
            entry.texWidth = texW;
            entry.texHeight = texH;
            updateTexture(entry, be);
            return entry;
        }
        catch (Exception e) {
            return null;
        }
    }
    
    private static void updateTexture(TextureEntry entry, StatsDisplayPanelBE be) {
        final List<StatsDisplayPanelBE.DataChannel> allChannels = be.getActiveChannels();
        
        // === STATE DETECTION & TRANSITION TRACKING ===
        final long currentTick = getGameTime();
        final PanelState newState = detectPanelState(be, entry, currentTick);
        
        // Handle state transitions with hysteresis to prevent flickering
        if (entry.currentState != newState) {
            // Require state to persist for 3 update cycles (3 * 5 ticks = 15 ticks)
            if (entry.stateStabilityCounter < 3) {
                entry.stateStabilityCounter++;
            } else {
                // State is stable, commit transition
                entry.previousState = entry.currentState;
                entry.currentState = newState;
                entry.stateTransitionTick = currentTick;
                entry.stateStabilityCounter = 0;
                entry.bootAnimationProgress = 0; // Reset animations on state change
                entry.needsRedraw = true; // Force immediate redraw
            }
        } else {
            // State unchanged, reset stability counter
            entry.stateStabilityCounter = 0;
        }
        
        // Update boot animation progress (0-100 over 2 seconds = 40 ticks)
        if (entry.currentState == PanelState.BOOTING) {
            final long ticksInBootState = currentTick - entry.stateTransitionTick;
            entry.bootAnimationProgress = Math.min(100, (int)((ticksInBootState * 100) / 40));
            
            // Force redraw during boot animation to update sweep
            if (entry.bootAnimationProgress < 100) {
                entry.needsRedraw = true;
            }
        }
        // === END STATE TRACKING ===
        
        final StatsDisplaySettings settings = be.getSettings();
        final StatsDisplaySettings.DisplayMode mode = settings.getDisplayMode();
        final StatsDisplaySettings.GraphType gType = settings.getGraphType();
        if (settings.getGraphType() == StatsDisplaySettings.GraphType.TOPOLOGY) {
            TopologyRenderer.setCurrentBE(be);
        }
        final List<StatsDisplayPanelBE.DataChannel> channels = collectVisibleChannels(be, allChannels, settings);
        final String preferredSourceId = resolvePreferredSourceId(be, settings, channels);
        final boolean dedicatedView = gType == StatsDisplaySettings.GraphType.TRAIN_MAP
                || gType == StatsDisplaySettings.GraphType.EFFICIENCY
                || gType == StatsDisplaySettings.GraphType.TOPOLOGY;
        if (settings.isCustomScreenLayoutEnabled() && CustomScreenPanelRenderer.renderCustomLayout(entry.image, be, channels)) {
            PanelAnimationRenderer.applyStateVisuals(entry.image, entry.currentState,
                entry.bootAnimationProgress, currentTick, settings.getDisplayTheme());
            entry.dynamicTexture.upload();
            return;
        }
        if (mode == StatsDisplaySettings.DisplayMode.SPECIALIZED
                && !dedicatedView
                && InstrumentRenderDispatcher.renderSpecializedPanel(entry.image, be, channels, preferredSourceId, getGameTime())) {
            if (settings.getGraphType() == StatsDisplaySettings.GraphType.TOPOLOGY) {
                TopologyRenderer.clearCurrentBE();
            }
            // Apply state animations before upload
            PanelAnimationRenderer.applyStateVisuals(entry.image, entry.currentState, 
                                                     entry.bootAnimationProgress, currentTick, settings.getDisplayTheme());
            entry.dynamicTexture.upload();
            return;
        }
        if (settings.getGraphType() == StatsDisplaySettings.GraphType.ANALOG_GAUGE) {
            final long ticksSinceLastUpdate = getGameTime() - entry.lastUpdateTick;
            final int steps = (int)Math.min(Math.max(1L, ticksSinceLastUpdate), 20L);
            for (int i = 0; i < channels.size(); ++i) {
                final NeedlePhysics needle = NeedlePhysicsRegistry.getOrCreate(be.getBlockPos(), i);
                final float normalized = computeNormalizedForNeedle(channels.get(i), settings);
                needle.setTarget(normalized * 4.712389f);
                for (int t = 0; t < steps; ++t) {
                    needle.tick();
                }
            }
        }
        List<StatsDisplayPanelBE.DataChannel> pageChannels = buildPagedChannels(be, channels, preferredSourceId);
        if (gType == StatsDisplaySettings.GraphType.ANALOG_GAUGE) {
            pageChannels = buildAnalogPages(channels, preferredSourceId);
        }
        else if (gType == StatsDisplaySettings.GraphType.CAPACITY) {
            pageChannels = buildCapacityPages(channels, preferredSourceId);
        }
        final int totalPages = Math.max(1, pageChannels.size());
        int currentPage = 0;
        final boolean specialView = dedicatedView || gType == StatsDisplaySettings.GraphType.ANALOG_GAUGE;
        if (!specialView && DashboardLayoutEngine.shouldActivate(be, channels)) {
            DashboardRenderer.renderDashboard(entry.image, be, channels);
            // Apply state animations before upload
            PanelAnimationRenderer.applyStateVisuals(entry.image, entry.currentState, 
                                                     entry.bootAnimationProgress, currentTick, settings.getDisplayTheme());
            entry.dynamicTexture.upload();
            return;
        }
        final int cycleInterval = settings.getCycleInterval();
        if (totalPages > 1 && cycleInterval > 0 && be.getLevel() != null) {
            final long gameTime = be.getLevel().getGameTime();
            currentPage = (int)(gameTime / cycleInterval % totalPages);
        }
        final StatsDisplayPanelBE.DataChannel currentChannel = pageChannels.isEmpty() ? null : pageChannels.get(currentPage);
        final int channelIdx = currentPage;
        TextureGraphRenderer.renderPage(entry.image, be, currentChannel, channelIdx, currentPage, totalPages);
        if (settings.getGraphType() == StatsDisplaySettings.GraphType.TOPOLOGY) {
            TopologyRenderer.clearCurrentBE();
        }
        
        // === APPLY STATE ANIMATIONS ===
        PanelAnimationRenderer.applyStateVisuals(entry.image, entry.currentState, 
                                                 entry.bootAnimationProgress, currentTick, settings.getDisplayTheme());
        // === END ANIMATIONS ===
        
        entry.dynamicTexture.upload();
    }
    
    private static void cleanupStaleTextures() {
        final long currentTick = getGameTime();
        final Iterator<Map.Entry<BlockPos, TextureEntry>> it = DisplayTextureManager.textures.entrySet().iterator();
        while (it.hasNext()) {
            final Map.Entry<BlockPos, TextureEntry> mapEntry = it.next();
            final TextureEntry entry = mapEntry.getValue();
            if (currentTick - entry.lastAccessTick > 200L) {
                disposeEntry(entry);
                it.remove();
            }
        }
    }
    
    private static void disposeEntry(TextureEntry entry) {
        try {
            if (entry.dynamicTexture != null) {
                entry.dynamicTexture.close();
            }
        }
        catch (RuntimeException ex) {
            CreateStats.LOGGER.warn("Failed to dispose display texture", ex);
        }
    }
    
    private static long getGameTime() {
        if (Minecraft.getInstance().level != null) {
            return Minecraft.getInstance().level.getGameTime();
        }
        return System.currentTimeMillis() / 50L;
    }
    
    private static float computeNormalizedForNeedle(StatsDisplayPanelBE.DataChannel channel, StatsDisplaySettings settings) {
        final float value = channel.getStore().getLatestValue();
        if (Float.isNaN(value) || Float.isInfinite(value)) {
            return 0.5f;
        }
        float min = settings.getAnalogGaugeMin();
        float max = settings.getAnalogGaugeMax();
        if (min >= max) {
            final float[] fixedRange = com.createstats.display.MetricContract.fixedRange(channel.getStore().getMetricType());
            if (fixedRange != null) {
                min = fixedRange[0];
                max = fixedRange[1];
            } else {
                min = channel.getStore().getMin();
                max = channel.getStore().getMax();
                float range = max - min;
                if (range < 0.001f) {
                    range = Math.max(1.0f, Math.abs(min) * 0.1f);
                }
                min = ((channel.getStore().getMin() >= 0.0f) ? 0.0f : (min - range * 0.1f));
                max += range * 0.1f;
            }
        }
        float range = max - min;
        if (range < 0.001f) {
            return 0.5f;
        }
        return Math.max(0.0f, Math.min(1.0f, (value - min) / range));
    }

    private static List<StatsDisplayPanelBE.DataChannel> collectVisibleChannels(StatsDisplayPanelBE be, List<StatsDisplayPanelBE.DataChannel> allChannels, StatsDisplaySettings settings) {
        final ArrayList<StatsDisplayPanelBE.DataChannel> visible = new ArrayList<StatsDisplayPanelBE.DataChannel>();
        for (StatsDisplayPanelBE.DataChannel channel : allChannels) {
            if (!settings.isChannelHidden(channel.getId())) {
                visible.add(channel);
            }
        }
        visible.sort(displayOrder(be));
        return visible;
    }

    private static List<StatsDisplayPanelBE.DataChannel> buildPagedChannels(StatsDisplayPanelBE be, List<StatsDisplayPanelBE.DataChannel> channels, String preferredSourceId) {
        final ArrayList<StatsDisplayPanelBE.DataChannel> pages = new ArrayList<StatsDisplayPanelBE.DataChannel>();
        for (StatsDisplayPanelBE.DataChannel channel : channels) {
            if (preferredSourceId != null && !preferredSourceId.equals(extractSourceId(channel.getId()))) {
                continue;
            }
            pages.add(channel);
        }
        if (pages.isEmpty() && preferredSourceId == null) {
            pages.addAll(channels);
        }
        pages.sort(displayOrder(be));
        return pages;
    }

    private static Comparator<StatsDisplayPanelBE.DataChannel> displayOrder(StatsDisplayPanelBE be) {
        final Map<String, SourceConfig> sourceRegistry = be.getSourceRegistry();
        return Comparator.comparing((StatsDisplayPanelBE.DataChannel channel) -> sourceSortLabel(sourceRegistry, extractSourceId(channel.getId())), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(channel -> channel.getLabel(), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(StatsDisplayPanelBE.DataChannel::getId, String.CASE_INSENSITIVE_ORDER);
    }

    private static String sourceSortLabel(Map<String, SourceConfig> sourceRegistry, String sourceId) {
        final SourceConfig config = sourceRegistry.get(sourceId);
        if (config != null) {
            return config.getObservedDisplayLabel();
        }
        return sourceId;
    }

    private static List<StatsDisplayPanelBE.DataChannel> buildAnalogPages(List<StatsDisplayPanelBE.DataChannel> channels, String preferredSourceId) {
        final ArrayList<StatsDisplayPanelBE.DataChannel> pages = new ArrayList<StatsDisplayPanelBE.DataChannel>();
        final HashSet<String> seenSources = new HashSet<String>();
        for (StatsDisplayPanelBE.DataChannel channel : channels) {
            final String sourceId = extractSourceId(channel.getId());
            if (preferredSourceId != null && !preferredSourceId.equals(sourceId)) {
                continue;
            }
            if (seenSources.add(sourceId)) {
                pages.add(channel);
            }
        }
        return pages;
    }

    private static List<StatsDisplayPanelBE.DataChannel> buildCapacityPages(List<StatsDisplayPanelBE.DataChannel> channels, String preferredSourceId) {
        final ArrayList<StatsDisplayPanelBE.DataChannel> pages = new ArrayList<StatsDisplayPanelBE.DataChannel>();
        final HashSet<String> seenSources = new HashSet<String>();
        for (StatsDisplayPanelBE.DataChannel channel : channels) {
            final String sourceId = extractSourceId(channel.getId());
            if (preferredSourceId != null && !preferredSourceId.equals(sourceId)) {
                continue;
            }
            if (!seenSources.add(sourceId)) {
                continue;
            }
            final StatsDisplayPanelBE.DataChannel representative = selectCapacityRepresentative(channels, sourceId);
            if (representative != null) {
                pages.add(representative);
            }
        }
        return pages;
    }

    private static StatsDisplayPanelBE.DataChannel selectCapacityRepresentative(List<StatsDisplayPanelBE.DataChannel> channels, String sourceId) {
        final String[] preferredMetrics = { "stress_pct", "stress_su", "tank_fill", "fluid_amount", "fill_pct", "stock_level" };
        for (String metric : preferredMetrics) {
            for (StatsDisplayPanelBE.DataChannel channel : channels) {
                if (sourceId.equals(extractSourceId(channel.getId())) && channel.getId().contains(metric)) {
                    return channel;
                }
            }
        }
        return null;
    }

    private static String resolvePreferredSourceId(StatsDisplayPanelBE be, StatsDisplaySettings settings, List<StatsDisplayPanelBE.DataChannel> channels) {
        final String preferred = settings.getPreferredSourceId();
        if (preferred == null || preferred.isEmpty()) {
            return null;
        }
        for (StatsDisplayPanelBE.DataChannel channel : channels) {
            if (preferred.equals(extractSourceId(channel.getId()))) {
                return preferred;
            }
        }
        if (SpecializedPanelRenderer.canRenderSourceWithoutChannels(be, preferred)) {
            return preferred;
        }
        return null;
    }

    private static String extractSourceId(String channelId) {
        if (!channelId.startsWith("src_")) {
            return "legacy";
        }
        final String rest = channelId.substring(4);
        int idx = rest.indexOf("_auto_");
        if (idx > 0) {
            return rest.substring(0, idx);
        }
        idx = rest.indexOf("_line");
        if (idx > 0) {
            return rest.substring(0, idx);
        }
        return rest;
    }
    
    /**
     * Determine the current operational state of the panel based on data source status.
     * Uses existing SOURCE_IDLE_TIMEOUT and considers channel/source health.
     */
    private static PanelState detectPanelState(StatsDisplayPanelBE be, TextureEntry entry, long currentTick) {
        // Priority 1: Setup required state (user action needed)
        if (be.isStatsDisplayLinkOptInRequired()) {
            return PanelState.SETUP_REQUIRED;
        }
        
        // Get all channels with data
        final List<StatsDisplayPanelBE.DataChannel> channels = be.getActiveChannels();
        final java.util.LinkedHashMap<String, SourceConfig> sourceRegistry = be.getSourceRegistry();
        
        // Priority 2: Unconfigured (no sources ever registered)
        if (channels.isEmpty() && sourceRegistry.isEmpty()) {
            return PanelState.UNCONFIGURED;
        }
        
        // Priority 3: Awaiting data (sources exist but no channel data yet)
        if (channels.isEmpty() && !sourceRegistry.isEmpty()) {
            return PanelState.AWAITING_DATA;
        }
        
        // Analyze channel/source health for BOOTING/LIVE/STALE detection
        long newestLastDataTick = 0L;
        long newestFirstConnectTick = 0L;
        int totalSampleCount = 0;
        boolean hasActiveSource = false;
        
        for (StatsDisplayPanelBE.DataChannel ch : channels) {
            totalSampleCount += ch.getStore().getSampleCount();
            
            // Extract source ID from channel ID (format: "src_sourceId_metric")
            final String channelId = ch.getId();
            final String sourceId = extractSourceId(channelId);
            final SourceConfig src = sourceRegistry.get(sourceId);
            if (src != null) {
                newestLastDataTick = Math.max(newestLastDataTick, src.getLastDataTick());
                newestFirstConnectTick = Math.max(newestFirstConnectTick, src.getFirstConnectTick());
                hasActiveSource = hasActiveSource || src.isActive();
            }
        }
        
        // No valid source data found (shouldn't happen, but defensive)
        if (newestLastDataTick <= 0L) {
            return PanelState.AWAITING_DATA;
        }
        
        final long ticksSinceLastData = currentTick - newestLastDataTick;
        final long ticksSinceFirstConnect = currentTick - newestFirstConnectTick;
        
        // Priority 4: BOOTING detection
        // Criteria: First data arrived recently (within 100 ticks) AND low sample count (< 20 samples)
        if (ticksSinceFirstConnect >= 0 && ticksSinceFirstConnect < 100 && totalSampleCount < 20) {
            return PanelState.BOOTING;
        }
        
        // Priority 5: STALE detection (data stream interrupted)
        if (!hasActiveSource && ticksSinceLastData > SOURCE_IDLE_TIMEOUT) {
            return PanelState.STALE;
        }
        
        // Priority 6: LIVE (healthy operation)
        return PanelState.LIVE;
    }
    
    static {
        textures = new HashMap<BlockPos, TextureEntry>();
        DisplayTextureManager.cleanupCounter = 0;
    }
    
    private static class TextureEntry
    {
        NativeImage image;
        DynamicTexture dynamicTexture;
        ResourceLocation textureLocation;
        long lastUpdateTick;
        long lastAccessTick;
        boolean needsRedraw;
        int texWidth;
        int texHeight;
        
        // Animation state tracking
        PanelState currentState = PanelState.UNCONFIGURED;
        PanelState previousState = PanelState.UNCONFIGURED;
        long stateTransitionTick = 0L;
        int bootAnimationProgress = 0; // 0-100
        int stateStabilityCounter = 0; // Hysteresis to prevent flickering
    }
}
