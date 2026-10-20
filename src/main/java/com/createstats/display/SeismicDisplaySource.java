package com.createstats.display;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.createstats.blockentity.SeismicLinks;
import com.createstats.blockentity.SeismicScannerBE;
import com.createstats.blockentity.StatsDisplayPanelBE;
import com.createstats.seismic.archive.ArchiveBlow;
import com.createstats.seismic.archive.SeismicArchive;
import com.createstats.seismic.archive.SeismicArchiveData;
import com.createstats.seismic.archive.SurveyChannels;
import com.createstats.seismic.archive.SurveyText;
import com.simibubi.create.api.behaviour.display.DisplaySource;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import com.simibubi.create.content.redstone.displayLink.target.DisplayTargetStats;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;

public class SeismicDisplaySource extends DisplaySource implements StatsLinkSource {
    private final Map<String, Cached> cache = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Cached> eldest) {
            return size() > 64;
        }
    };

    private record Cached(long revision, long at, List<ArchiveBlow> blows, Map<String, SurveyChannels.Figures> figures) {
    }

    @Override
    public List<MutableComponent> provideText(DisplayLinkContext context, DisplayTargetStats stats) {
        if (context.getTargetBlockEntity() instanceof StatsDisplayPanelBE) {
            return EMPTY;
        }
        if (!(context.level() instanceof ServerLevel level)) {
            return EMPTY;
        }
        final SeismicScannerBE rig = SeismicLinks.rigAt(level, context.getSourcePos());
        if (rig == null) {
            return EMPTY;
        }
        final SeismicArchive archive = SeismicArchiveData.get(level).archive();
        final long key = rig.getBlockPos().asLong();
        final BlockPos at = context.getTargetPos();
        final long now = level.getGameTime();
        final String cacheKey = key + "@" + at.asLong();
        final long revision = archive.revisionOf(key);
        Cached c;
        synchronized (this.cache) {
            c = this.cache.get(cacheKey);
            final boolean arriving = c != null && SurveyChannels.hot(c.blows(), now) && now - c.at() >= 20L;
            if (c == null || c.revision() != revision || arriving) {
                final List<ArchiveBlow> blows = archive.snapshot(List.of(key), Long.MIN_VALUE);
                c = new Cached(revision, now, blows, SurveyChannels.compute(blows, at.getX() + 0.5, at.getZ() + 0.5,
                        now));
                this.cache.put(cacheKey, c);
            }
        }
        final List<MutableComponent> out = new ArrayList<>();
        for (final String line : SurveyText.signLines(c.figures(), c.blows(), rig.target(), at.getX() + 0.5,
                at.getZ() + 0.5, Math.max(1, stats.maxRows()), stats.maxColumns(), now)) {
            out.add(Component.literal(line));
        }
        return out;
    }

    @Override
    public int getPassiveRefreshTicks() {
        return 20;
    }

    @Override
    public boolean shouldPassiveReset() {
        return true;
    }

    @Override
    protected String getTranslationKey() {
        return "seismic_scanner";
    }
}
