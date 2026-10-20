package com.createstats.gui.pdu;

import com.createstats.blockentity.PowerDistributionUnitBE.BranchDevice;
import com.createstats.power.FeLock;
import com.createstats.power.FePriority;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class PduDrawer {
    public interface Callback {
        void setTier(BranchDevice device, FePriority tier);

        void setLock(BranchDevice device, FeLock lock);

        void locate(BranchDevice device);
    }

    private BranchDevice device;
    private BranchDevice shown;
    private float openFraction;

    private PduRect bounds;
    private final List<int[]> tierHits = new ArrayList<>();
    private final List<int[]> lockHits = new ArrayList<>();
    private int[] locateHit;

    public void setDevice(BranchDevice device) {
        this.device = device;
        if (device != null) {
            this.shown = device;
        }
    }

    public boolean isOpen() {
        return device != null || openFraction > 0.02f;
    }

    public boolean covers(double mx, double my) {
        return bounds != null && bounds.contains(mx, my);
    }

    public void draw(GuiGraphics g, Font font, PduRect content, int mouseX, int mouseY,
                     float partialTick, BlockPos confirmLockOff) {
        draw(g, font, content, mouseX, mouseY, partialTick, confirmLockOff, false);
    }

    public void draw(GuiGraphics g, Font font, PduRect content, int mouseX, int mouseY,
                     float partialTick, BlockPos confirmLockOff, boolean panelOwned) {
        openFraction = Ease.ease(openFraction, device != null ? 1.0f : 0.0f, 0.25f, partialTick);
        tierHits.clear();
        lockHits.clear();
        locateHit = null;
        if (shown == null || openFraction < 0.02f) {
            bounds = null;
            return;
        }
        final int width = Math.min(PduTheme.DRAWER_W, content.w() - 40);
        final int x = content.right() - Math.round(width * openFraction);
        bounds = new PduRect(x, content.y(), width, content.h());

        g.enableScissor(content.x(), content.y(), content.right(), content.bottom());

        g.fill(x - 3, content.y(), x, content.bottom(), 0x30000000);
        g.fill(x, content.y(), content.right(), content.bottom(), PduTheme.BG_PANEL);
        g.fill(x, content.y(), x + 1, content.bottom(), PduTheme.STROKE_HOT);

        final int left = x + PduTheme.PAD;
        final int right = content.right() - PduTheme.PAD;
        int y = content.y() + PduTheme.PAD;

        g.drawString(font, trim(font, shown.label(), right - left), left, y, PduTheme.TEXT, false);
        y += 12;

        final BlockPos p = shown.pos();
        final String pos = p.getX() + ", " + p.getY() + ", " + p.getZ();
        g.drawString(font, trim(font, pos, right - left - 34), left, y, PduTheme.TEXT_DIM, false);
        final String locate = "Locate";
        final int lw = font.width(locate) + 8;
        final int lx = right - lw;
        final boolean lh = mouseX >= lx && mouseX < lx + lw && mouseY >= y - 2 && mouseY < y + 9;
        g.fill(lx, y - 2, lx + lw, y + 9, lh ? PduTheme.BG_RAISED : PduTheme.BG_DEEP);
        PduTheme.outline(g, lx, y - 2, lw, 11, PduTheme.STROKE);
        g.drawString(font, locate, lx + 4, y, PduTheme.INFO, false);
        locateHit = new int[]{lx, y - 2, lx + lw, y + 9};
        y += 14;

        final String legName = PduLegRow.name(shown.leg());
        g.fill(left, y, left + 3, y + 8, PduLegRow.colour(shown.leg()));
        g.drawString(font, trim(font, legName, right - left - 7), left + 7, y, PduTheme.TEXT_DIM, false);
        y += 12;

        // show budget next to draw when they differ, that's why you opened this panel

        g.drawString(font, String.format(Locale.ROOT, "%,d FE/t", shown.draw()), left, y,
                PduTheme.TEXT, false);
        y += 10;
        if (!shown.isStorage() && shown.cost() != shown.draw()) {
            g.drawString(font, trim(font, "costed at " + String.format(Locale.ROOT, "%,d", shown.cost()),
                    right - left), left, y, PduTheme.TEXT_FAINT, false);
            y += 10;
        }
        y += 4;

        g.drawWordWrap(font, Component.literal(shown.reason().description()), left, y,
                right - left, PduTheme.TEXT_DIM);
        y += font.wordWrapHeight(shown.reason().description(), right - left) + 8;

        if (shown.isStorage()) {
            drawStorageBody(g, font, left, right, y);
            g.disableScissor();
            return;
        }

        PduCard.drawHeading(g, font, "Tier", left, y);
        y += 11;
        for (FePriority tier : FePriority.values()) {
            final boolean active = shown.priority() == tier;
            final boolean hover = mouseX >= left && mouseX < right && mouseY >= y && mouseY < y + 13;
            g.fill(left, y, right, y + 13, active || hover ? PduTheme.BG_RAISED : PduTheme.BG_DEEP);
            PduTheme.outline(g, left, y, right - left, 13, active ? PduTheme.ACCENT : PduTheme.STROKE);
            g.fill(left + 3, y + 4, left + 8, y + 9, PduGlyphs.tierColour(tier));
            g.drawString(font, tier.getDisplayName(), left + 12, y + 3,
                    active ? PduTheme.TEXT : PduTheme.TEXT_DIM, false);
            tierHits.add(new int[]{left, y, right, y + 13, tier.ordinal()});
            y += 15;
        }
        y += 4;

        PduCard.drawHeading(g, font, "Lock", left, y);
        y += 11;
        for (FeLock lock : FeLock.values()) {
            final boolean active = shown.lock() == lock;
            final boolean hover = mouseX >= left && mouseX < right && mouseY >= y && mouseY < y + 13;
            final boolean arming = lock == FeLock.FORCE_OFF && confirmLockOff != null
                    && confirmLockOff.equals(shown.pos());
            g.fill(left, y, right, y + 13, active || hover ? PduTheme.BG_RAISED : PduTheme.BG_DEEP);
            PduTheme.outline(g, left, y, right - left, 13,
                    arming ? PduTheme.CRIT : active ? PduTheme.ACCENT : PduTheme.STROKE);
            final String label = arming ? "Sure? Lock off" : lockLabel(lock);
            g.drawString(font, label, left + 6, y + 3,
                    arming ? PduTheme.CRIT : active ? PduTheme.TEXT : PduTheme.TEXT_DIM, false);
            lockHits.add(new int[]{left, y, right, y + 13, lock.ordinal()});
            y += 15;
        }
        if (panelOwned && y + 20 < content.bottom()) {
            y += 4;
            g.fill(left, y, right, y + 1, PduTheme.STROKE);
            y += 4;
            g.drawWordWrap(font, Component.literal(
                    "Settings kept by this panel. Rewiring this machine will not carry them."),
                    left, y, right - left, PduTheme.TEXT_FAINT);
        }
        g.disableScissor();
    }

    private void drawStorageBody(GuiGraphics g, Font font, int left, int right, int y) {
        PduCard.drawHeading(g, font, "Charge", left, y);
        y += 11;
        final String charge = String.format(Locale.ROOT, "%,d / %,d FE", shown.stored(), shown.capacity());
        g.drawString(font, trim(font, charge, right - left), left, y, PduTheme.TEXT, false);
        y += 11;
        PduGauge.drawHorizontal(g, left, y, right - left, 7, shown.fill(), 0.0f, PduTheme.INFO);
        y += 12;
        final int flow = shown.draw();
        final String flowLine = flow == 0 ? "Not moving"
                : String.format(Locale.ROOT, "%s%,d FE/t", flow > 0 ? "+" : "", flow);
        g.drawString(font, flowLine, left, y,
                flow > 0 ? PduTheme.OK : flow < 0 ? PduTheme.INFO : PduTheme.TEXT_DIM, false);
        y += 14;
        g.drawWordWrap(font, Component.literal(
                "A battery has no tier and is never shed, so there is nothing here to set."),
                left, y, right - left, PduTheme.TEXT_FAINT);
    }

    public PduRect bounds() {
        return bounds;
    }

    public PduRect tierRect(FePriority tier) {
        return rectOf(tierHits, tier.ordinal());
    }

    public PduRect lockRect(FeLock lock) {
        return rectOf(lockHits, lock.ordinal());
    }

    public PduRect locateRect() {
        return locateHit == null ? null
                : new PduRect(locateHit[0], locateHit[1], locateHit[2] - locateHit[0], locateHit[3] - locateHit[1]);
    }

    private static PduRect rectOf(List<int[]> hits, int key) {
        for (int[] h : hits) {
            if (h[4] == key) {
                return new PduRect(h[0], h[1], h[2] - h[0], h[3] - h[1]);
            }
        }
        return null;
    }

    public boolean mouseClicked(double mx, double my, int button, Callback cb) {
        if (button != 0 || bounds == null || !bounds.contains(mx, my) || shown == null) {
            return false;
        }
        if (locateHit != null && inside(locateHit, mx, my)) {
            cb.locate(shown);
            return true;
        }
        for (int[] hit : tierHits) {
            if (inside(hit, mx, my)) {
                cb.setTier(shown, FePriority.byOrdinal(hit[4]));
                return true;
            }
        }
        for (int[] hit : lockHits) {
            if (inside(hit, mx, my)) {
                cb.setLock(shown, FeLock.byOrdinal(hit[4]));
                return true;
            }
        }

        return true;
    }

    private static boolean inside(int[] box, double mx, double my) {
        return mx >= box[0] && mx < box[2] && my >= box[1] && my < box[3];
    }

    private static String lockLabel(FeLock lock) {
        return switch (lock) {
            case NONE -> "Auto";
            case FORCE_ON -> "Lock on";
            case FORCE_OFF -> "Lock off";
        };
    }

    private static String trim(Font font, String s, int maxW) {
        if (font.width(s) <= maxW) {
            return s;
        }
        final int ell = font.width("...");
        int end = s.length();
        while (end > 0 && font.width(s.substring(0, end)) + ell > maxW) {
            end--;
        }
        return s.substring(0, end) + "...";
    }
}
