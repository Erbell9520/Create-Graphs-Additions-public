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

/** [1] */
public final class PduDrawer {

    /** The screen fields these so tier and lock go through the same validated, confirmed path as the table. */
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

    /** null closes the drawer, sliding it out while it still shows the device it had. */
    public void setDevice(BranchDevice device) {
        this.device = device;
        if (device != null) {
            this.shown = device;
        }
    }

    public boolean isOpen() {
        return device != null || openFraction > 0.02f;
    }

    /** Whether the drawer is covering this point, so the screen does not draw a row tooltip under it. */
    public boolean covers(double mx, double my) {
        return bounds != null && bounds.contains(mx, my);
    }

    public void draw(GuiGraphics g, Font font, PduRect content, int mouseX, int mouseY,
                     float partialTick, BlockPos confirmLockOff) {
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
        // A soft shadow and a hard edge, so the drawer reads as floating over the table under it.
        g.fill(x - 3, content.y(), x, content.bottom(), 0x30000000);
        g.fill(x, content.y(), content.right(), content.bottom(), PduTheme.BG_PANEL);
        g.fill(x, content.y(), x + 1, content.bottom(), PduTheme.STROKE_HOT);

        final int left = x + PduTheme.PAD;
        final int right = content.right() - PduTheme.PAD;
        int y = content.y() + PduTheme.PAD;

        // Name.
        g.drawString(font, trim(font, shown.label(), right - left), left, y, PduTheme.TEXT, false);
        y += 12;

        // Position with a locate action.
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

        // Live draw.
        g.drawString(font, String.format(Locale.ROOT, "%,d FE/t", shown.draw()), left, y, PduTheme.TEXT, false);
        y += 14;

        // Reason, wrapped.
        g.drawWordWrap(font, Component.literal(shown.reason().description()), left, y,
                right - left, PduTheme.TEXT_DIM);
        y += font.wordWrapHeight(shown.reason().description(), right - left) + 8;

        // Tier buttons, five of them explicit.
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

        // Lock buttons, three of them explicit, with the inline confirm on locking a critical off.
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
        g.disableScissor();
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
        // Swallow clicks that land on the drawer but not a control, so they do not fall through to the
        // table underneath and change the selection out from under the inspector.
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
