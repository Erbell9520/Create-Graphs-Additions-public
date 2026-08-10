package com.createstats.renderer.instrument;

import com.createstats.blockentity.StatsDisplayPanelBE;
import com.createstats.display.DisplayTheme;
import com.createstats.display.StatsDisplaySettings;
import com.createstats.renderer.AnalogGaugeRenderer;
import com.createstats.renderer.SpecializedPanelRenderer;
import com.createstats.renderer.TextureGraphRenderer;
import com.mojang.blaze3d.platform.NativeImage;
import java.util.List;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class InstrumentRenderDispatcher
{
    private InstrumentRenderDispatcher() {
    }
    
    public static void renderAnalogPanel(NativeImage image, StatsDisplayPanelBE be, StatsDisplayPanelBE.DataChannel primaryChannel, int x, int y, int w, int h, DisplayTheme theme, StatsDisplaySettings settings, int pageIdx, int totalPages) {
        if (settings.getInstrumentStyleFamily().isLegacy()) {
            AnalogGaugeRenderer.renderAnalogGauges(image, be, primaryChannel, x, y, w, h, theme, settings);
        }
        else {
            ModernInstrumentTextureRenderer.renderAnalogPanel(image, be, primaryChannel, x, y, w, h, theme, settings, pageIdx, totalPages);
        }
        if (settings.getInstrumentStyleFamily().isLegacy() && totalPages > 1) {
            final int dotsH = Math.max(6, image.getHeight() / 32);
            TextureGraphRenderer.renderPageDots(image, pageIdx, totalPages, x, y + h - dotsH, w, dotsH, theme);
        }
    }
    
    public static boolean renderSpecializedPanel(NativeImage image, StatsDisplayPanelBE be, List<StatsDisplayPanelBE.DataChannel> channels, String preferredSourceId, long renderTick) {
        return SpecializedPanelRenderer.renderSpecializedPanel(image, be, channels, preferredSourceId, renderTick);
    }
}
