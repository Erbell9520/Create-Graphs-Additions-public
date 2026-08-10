package com.createstats.gui.pdu;

import com.createstats.blockentity.PowerDistributionUnitBE.BranchDevice;
import com.createstats.gui.pdu.PduTopology.BranchNode;
import com.createstats.gui.pdu.PduTopology.Kind;
import com.createstats.power.FeLock;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/** [1] */
public final class PduDiagram {

    private final List<int[]> hitBoxes = new ArrayList<>();
    private final List<BlockPos> hitPositions = new ArrayList<>();

    public void draw(GuiGraphics g, Font font, PduRect bounds, List<BranchNode> nodes,
                     Map<BlockPos, BranchDevice> deviceByPos, Predicate<BlockPos> isSelected,
                     boolean truncated, int mouseX, int mouseY) {
        hitBoxes.clear();
        hitPositions.clear();
        PduTheme.panel(g, bounds.x(), bounds.y(), bounds.w(), bounds.h());
        final PduRect area = bounds.inset(PduTheme.PAD);
        if (nodes.isEmpty()) {
            final String msg = "Nothing wired to this panel";
            g.drawString(font, msg, area.centerX() - font.width(msg) / 2, area.centerY() - 4,
                    PduTheme.TEXT_FAINT, false);
            return;
        }

        final int n = nodes.size();
        final boolean[] meaningful = new boolean[n];
        for (int i = 0; i < n; i++) {
            final Kind k = nodes.get(i).kindEnum();
            meaningful[i] = k == Kind.PDU || k == Kind.SWITCH || k == Kind.DEVICE;
        }

        // [2]
        final int[] logicalParent = new int[n];
        final boolean[] thick = new boolean[n];
        final int[] depth = new int[n];
        for (int i = 0; i < n; i++) {
            if (!meaningful[i]) {
                logicalParent[i] = -1;
                continue;
            }
            int p = nodes.get(i).parentIndex();
            boolean tray = false;
            while (p >= 0 && !meaningful[p]) {
                // [3]
                if (nodes.get(p).kindEnum() == Kind.TRAY || nodes.get(p).kindEnum() == Kind.DUCT) {
                    tray = true;
                }
                p = nodes.get(p).parentIndex();
            }
            logicalParent[i] = p;
            thick[i] = tray;
            depth[i] = p < 0 ? 0 : depth[p] + 1;
        }

        int maxDepth = 0;
        for (int i = 0; i < n; i++) {
            if (meaningful[i]) {
                maxDepth = Math.max(maxDepth, depth[i]);
            }
        }
        final int[] countAtDepth = new int[maxDepth + 1];
        for (int i = 0; i < n; i++) {
            if (meaningful[i]) {
                countAtDepth[depth[i]]++;
            }
        }
        final int levelH = Math.max(18, Math.min(40, maxDepth == 0 ? area.h() : area.h() / (maxDepth + 1)));
        final int[] cx = new int[n];
        final int[] cy = new int[n];
        final int[] placed = new int[maxDepth + 1];
        for (int i = 0; i < n; i++) {
            if (!meaningful[i]) {
                continue;
            }
            final int d = depth[i];
            final int k = Math.max(1, countAtDepth[d]);
            final int j = placed[d]++;
            cx[i] = area.x() + Math.round((j + 0.5f) / k * area.w());
            cy[i] = area.y() + d * levelH + levelH / 2;
        }

        for (int i = 0; i < n; i++) {
            if (!meaningful[i] || logicalParent[i] < 0) {
                continue;
            }
            final BranchNode node = nodes.get(i);
            final boolean dashed = node.kindEnum() == Kind.DEVICE && isShed(deviceByPos.get(node.pos()));
            edge(g, cx[logicalParent[i]], cy[logicalParent[i]], cx[i], cy[i], thick[i], dashed);
        }

        for (int i = 0; i < n; i++) {
            if (meaningful[i]) {
                drawNode(g, font, nodes.get(i), cx[i], cy[i], deviceByPos, isSelected);
            }
        }

        if (truncated) {
            final String note = "diagram truncated at " + PduTopology.MAX_NODES + " nodes";
            g.drawString(font, note, area.x(), area.bottom() - 8, PduTheme.WARN, false);
        }
    }

    private void drawNode(GuiGraphics g, Font font, BranchNode node, int cx, int cy,
                          Map<BlockPos, BranchDevice> deviceByPos, Predicate<BlockPos> isSelected) {
        final Kind kind = node.kindEnum();
        int x;
        int y;
        int w;
        int h;
        if (kind == Kind.PDU) {
            final String label = "PDU";
            w = font.width(label) + 8;
            h = 12;
            x = cx - w / 2;
            y = cy - h / 2;
            g.fill(x, y, x + w, y + h, PduTheme.BG_RAISED);
            PduTheme.outline(g, x, y, w, h, PduTheme.ACCENT);
            g.drawString(font, label, x + 4, y + 2, PduTheme.TEXT, false);
        } else if (kind == Kind.SWITCH) {
            final int colour = node.open() ? PduTheme.CRIT : PduTheme.INFO;
            w = 14;
            h = 12;
            x = cx - w / 2;
            y = cy - h / 2;
            if (node.open()) {
                // A lifted lever with a gap: the break drawn honestly.
                g.fill(cx - 6, cy, cx - 2, cy + 2, colour);
                g.fill(cx + 2, cy, cx + 6, cy + 2, colour);
                g.fill(cx - 3, cy - 5, cx - 1, cy + 1, colour);
            } else {
                g.fill(cx - 6, cy, cx + 6, cy + 2, colour);
            }
        } else {
            final BranchDevice d = deviceByPos.get(node.pos());
            final int colour = deviceColour(d);
            w = 12;
            h = 9;
            x = cx - w / 2;
            y = cy - h / 2;
            g.fill(x, y, x + w, y + h, PduTheme.BG_RAISED);
            PduTheme.outline(g, x, y, w, h, colour);
            g.fill(cx - 2, cy - 2, cx + 2, cy + 2, colour);
        }

        if (isSelected.test(node.pos())) {
            PduTheme.focusRing(g, x, y, w, h);
        }
        hitBoxes.add(new int[]{x - 1, y - 1, x + w + 1, y + h + 1});
        hitPositions.add(node.pos());
    }

    /** The device (or switch) under the cursor, for a click or a tooltip. */
    public BlockPos nodeAt(double mx, double my) {
        for (int i = 0; i < hitBoxes.size(); i++) {
            final int[] b = hitBoxes.get(i);
            if (mx >= b[0] && mx < b[2] && my >= b[1] && my < b[3]) {
                return hitPositions.get(i);
            }
        }
        return null;
    }

    private static boolean isShed(BranchDevice d) {
        return d != null && d.shed();
    }

    private static int deviceColour(BranchDevice d) {
        if (d == null) {
            return PduTheme.TEXT_DIM;
        }
        return switch (PduTableModel.Status.of(d)) {
            case OFF -> PduTheme.TEXT_FAINT;
            case HOLDING, SHED -> PduTheme.WARN;
            case RUNNING -> d.lock() == FeLock.FORCE_ON ? PduTheme.INFO : PduTheme.OK;
        };
    }

    private static void edge(GuiGraphics g, int x0, int y0, int x1, int y1, boolean thick, boolean dashed) {
        int dx = Math.abs(x1 - x0);
        int dy = Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int err = dx - dy;
        int step = 0;
        final int colour = PduTheme.withAlpha(PduTheme.TEXT_DIM, 0xC8);
        while (true) {
            if (!dashed || step / 3 % 2 == 0) {
                g.fill(x0, y0, x0 + 1, y0 + 1, colour);
                if (thick) {
                    g.fill(x0 + 1, y0, x0 + 2, y0 + 1, colour);
                }
            }
            if (x0 == x1 && y0 == y1) {
                break;
            }
            final int e2 = 2 * err;
            if (e2 > -dy) {
                err -= dy;
                x0 += sx;
            }
            if (e2 < dx) {
                err += dx;
                y0 += sy;
            }
            step++;
        }
    }
}
