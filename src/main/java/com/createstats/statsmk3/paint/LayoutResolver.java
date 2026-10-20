package com.createstats.statsmk3.paint;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.createstats.statsmk3.layout.Mk3Layout;
import com.createstats.statsmk3.layout.Tile;
import com.createstats.statsmk3.ui.Rect;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class LayoutResolver {
    private LayoutResolver() {
    }

    public static Rect rectOf(Tile tile, Mk3Layout layout, Rect body, float gap) {
        if (tile == null || layout == null || body.isEmpty()) {
            return Rect.EMPTY;
        }
        final int cols = Math.max(1, layout.cols());
        final int rows = Math.max(1, layout.rows());
        final float cw = (body.w - gap * (cols - 1)) / cols;
        final float ch = (body.h - gap * (rows - 1)) / rows;
        if (cw <= 0.0f || ch <= 0.0f) {
            return Rect.EMPTY;
        }
        return new Rect(
                body.x + tile.gx * (cw + gap),
                body.y + tile.gy * (ch + gap),
                tile.gw * cw + (tile.gw - 1) * gap,
                tile.gh * ch + (tile.gh - 1) * gap);
    }

    public static int[] cellAt(Mk3Layout layout, Rect body, float gap, double px, double py) {
        if (layout == null || body.isEmpty() || !body.contains(px, py)) {
            return null;
        }
        final int cols = Math.max(1, layout.cols());
        final int rows = Math.max(1, layout.rows());
        final float cw = (body.w - gap * (cols - 1)) / cols;
        final float ch = (body.h - gap * (rows - 1)) / rows;
        if (cw <= 0.0f || ch <= 0.0f) {
            return null;
        }
        final int cx = (int) Math.floor((px - body.x) / (cw + gap));
        final int cy = (int) Math.floor((py - body.y) / (ch + gap));
        if (cx < 0 || cy < 0 || cx >= cols || cy >= rows) {
            return null;
        }
        return new int[]{cx, cy};
    }

    public static int[] nearestCell(Mk3Layout layout, Rect body, float gap, double px, double py) {
        final int cols = Math.max(1, layout.cols());
        final int rows = Math.max(1, layout.rows());
        final float cw = (body.w - gap * (cols - 1)) / cols;
        final float ch = (body.h - gap * (rows - 1)) / rows;
        if (cw <= 0.0f || ch <= 0.0f) {
            return new int[]{0, 0};
        }
        final int cx = (int) Math.round((px - body.x) / (cw + gap));
        final int cy = (int) Math.round((py - body.y) / (ch + gap));
        return new int[]{clamp(cx, 0, cols - 1), clamp(cy, 0, rows - 1)};
    }

    public static void bind(PanelModel model) {
        model.slots.clear();
        final Mk3Layout layout = model.layout;
        if (layout == null || layout.isEmpty()) {
            return;
        }
        final List<ChannelView> channels = model.channels;

        final Set<String> pinned = new HashSet<>();
        for (final Tile t : layout.tiles()) {
            if (t.isBound()) {
                pinned.add(t.channelId);
            }
        }

        int cursor = 0;
        for (final Tile tile : layout.tiles()) {
            if (tile.isBound()) {
                final ChannelView found = findChannel(channels, tile.channelId);
                model.slots.add(new PanelModel.Slot(tile, found, found == null));
                continue;
            }
            ChannelView pick = null;
            if (!channels.isEmpty()) {
                for (int step = 0; step < channels.size(); step++) {
                    final ChannelView candidate = channels.get((cursor + step) % channels.size());
                    if (!pinned.contains(candidate.id)) {
                        pick = candidate;
                        cursor = (cursor + step + 1) % channels.size();
                        break;
                    }
                }
                if (pick == null) {
                    pick = channels.get(cursor % channels.size());
                    cursor++;
                }
            }
            model.slots.add(new PanelModel.Slot(tile, pick, false));
        }
    }

    private static ChannelView findChannel(List<ChannelView> channels, String id) {
        for (final ChannelView ch : channels) {
            if (ch.id.equals(id)) {
                return ch;
            }
        }
        return null;
    }

    // largest tile wins, ties go to the earlier one so it's stable
    public static PanelModel.Slot primary(PanelModel model) {
        PanelModel.Slot best = null;
        int bestCells = -1;
        for (final PanelModel.Slot slot : model.slots) {
            if (slot.channel == null) {
                continue;
            }
            final int cells = slot.tile.cells();
            if (cells > bestCells) {
                bestCells = cells;
                best = slot;
            }
        }
        if (best == null && !model.slots.isEmpty()) {
            best = model.slots.get(0);
        }
        return best;
    }

    private static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : Math.min(v, hi);
    }
}
