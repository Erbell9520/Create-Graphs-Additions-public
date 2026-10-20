package com.createstats.statsmk3.page;

import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class PeerFocusMemory {
    private static final int LIMIT = 64;

    private static final Map<String, String> LAST = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
            return size() > LIMIT;
        }
    };

    private PeerFocusMemory() {
    }

    private static String key(BlockPos panel, String pageId) {
        return (panel == null ? "?" : panel.asLong()) + "/" + (pageId == null ? "" : pageId);
    }

    public static void remember(BlockPos panel, String pageId, String sourceId) {
        final String k = key(panel, pageId);
        if (sourceId == null || sourceId.isEmpty()) {
            LAST.remove(k);
        } else {
            LAST.put(k, sourceId);
        }
    }

    public static String recall(BlockPos panel, String pageId) {
        final String v = LAST.get(key(panel, pageId));
        return v == null ? "" : v;
    }

    public static void clear() {
        LAST.clear();
    }
}
