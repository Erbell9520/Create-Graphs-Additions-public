package com.createstats.gui.pdu;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.List;

/** [1] */
public final class PduRail {

    /** One rail entry: which glyph to draw, and the tooltip that names its view. */
    public record Item(String iconId, String tooltip) {
    }

    private final Item[] items;
    private int active;
    private final int[] itemTops;
    private int itemH = 22;
    private PduRect bounds = new PduRect(0, 0, 0, 0);
    /** Eased top of the active item, so the accent bar glides between views. */
    private float indicatorY = -1.0f;

    public PduRail(Item... items) {
        this.items = items.clone();
        this.itemTops = new int[items.length];
    }

    public int active() {
        return active;
    }

    public void setActive(int index) {
        if (index >= 0 && index < items.length) {
            active = index;
        }
    }

    public int count() {
        return items.length;
    }

    public void draw(GuiGraphics g, PduRect rail, int mouseX, int mouseY, float partialTick,
                     List<Component> tooltipOut) {
        this.bounds = rail;
        g.fill(rail.x(), rail.y(), rail.right(), rail.bottom(), PduTheme.BG_DEEP);
        // A hairline divides the rail from the content, so the two read as separate columns.
        g.fill(rail.right() - 1, rail.y(), rail.right(), rail.bottom(), PduTheme.STROKE);

        // Size items to the rail so five always fit, clamped so they never get cramped or vast.
        this.itemH = Mth.clamp((rail.h() - 2 * PduTheme.BASE) / Math.max(1, items.length), 18, 26);
        final int iconSize = itemH >= 22 ? 16 : 14;
        int y = rail.y() + PduTheme.BASE;

        for (int i = 0; i < items.length; i++) {
            itemTops[i] = y;
            final boolean act = i == active;
            final boolean hovered = mouseX >= rail.x() && mouseX < rail.right()
                    && mouseY >= y && mouseY < y + itemH;
            if (hovered && !act) {
                g.fill(rail.x(), y, rail.right(), y + itemH, PduTheme.BG_PANEL);
            }
            final int color = act || hovered ? PduTheme.TEXT : PduTheme.TEXT_DIM;
            final int ix = rail.x() + (rail.w() - iconSize) / 2;
            final int iy = y + (itemH - iconSize) / 2;
            PduIcons.draw(g, items[i].iconId(), ix, iy, color);
            if (hovered && tooltipOut != null) {
                tooltipOut.add(Component.literal(items[i].tooltip()));
            }
            y += itemH;
        }

        // The accent bar on the active item's inner edge, eased into place.
        final float targetY = itemTops[active];
        indicatorY = indicatorY < 0.0f ? targetY : Ease.ease(indicatorY, targetY, 0.30f, partialTick);
        final int barY = Math.round(indicatorY);
        g.fill(rail.x(), barY + 2, rail.x() + 2, barY + itemH - 2, PduTheme.ACCENT);
    }

    /** The item under the cursor, or {@code -1}. */
    public int itemAt(double mx, double my) {
        if (!bounds.contains(mx, my)) {
            return -1;
        }
        for (int i = 0; i < items.length; i++) {
            if (my >= itemTops[i] && my < itemTops[i] + itemH) {
                return i;
            }
        }
        return -1;
    }
}
