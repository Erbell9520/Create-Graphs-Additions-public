package com.createstats.gui.pdu;

import com.createstats.blockentity.PowerDistributionUnitBE.BranchDevice;
import com.createstats.gui.pdu.PduTableModel.DeviceRow;
import com.createstats.gui.pdu.PduTableModel.Filter;
import com.createstats.gui.pdu.PduTableModel.GroupRow;
import com.createstats.gui.pdu.PduTableModel.Group;
import com.createstats.gui.pdu.PduTableModel.Row;
import com.createstats.gui.pdu.PduTableModel.Sort;
import com.createstats.gui.pdu.PduTableModel.Status;
import com.createstats.power.FeLock;
import com.createstats.power.FePriority;
import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

public final class PduTable {
    public interface Callback {
        void requestSetTier(BranchDevice device, FePriority tier);

        void requestSetLock(BranchDevice device, FeLock lock);

        void requestSort(Sort column);
    }

    private static final Object EMPTY_TOKEN = new Object();
    private static final int OVERSCAN = 2;

    public static final int HEADER_H = 11;

    private List<Row> rows = List.of();
    private int[] groupOf = new int[0];
    private int[] nextHeaderOf = new int[0];
    private int peakDraw = 1;

    private Object lastToken;
    private int lastSize = -1;
    private Group lastGroup;
    private Sort lastSort;
    private boolean lastReversed;
    private Filter lastFilter;

    private final LinkedHashSet<BlockPos> selection = new LinkedHashSet<>();
    private BlockPos cursorPos;
    private BlockPos anchorPos;
    private boolean focused;

    private boolean headerFocused;

    private int scroll;
    private int contentHeight;
    private float scrollbarAlpha;
    private boolean draggingThumb;
    private int thumbGrabOffset;
    private long lastWheelMs;
    private float wheelAccel = 1.0f;

    private PduRect bounds = new PduRect(0, 0, 0, 0);

    private PduRect area = new PduRect(0, 0, 0, 0);
    private int rowH = 16;
    private PduLoadColumns cols = PduLoadColumns.of(0, 0);
    private Sort sort = Sort.DRAW;
    private boolean reversed;

    public void setData(List<BranchDevice> roster, Group group, Sort sort, boolean reversed,
                        String search, boolean shedOnly) {
        setData(roster, group, sort, reversed, new Filter(search, shedOnly, null, Filter.ANY_LEG));
    }

    public void setData(List<BranchDevice> roster, Group group, Sort sort, boolean reversed,
                        String search, boolean shedOnly, FePriority tierFilter) {
        setData(roster, group, sort, reversed, new Filter(search, shedOnly, tierFilter, Filter.ANY_LEG));
    }

    public void setData(List<BranchDevice> roster, Group group, Sort sort, boolean reversed,
                        Filter filter) {
        this.sort = sort;
        this.reversed = reversed;
        final Object token = roster.isEmpty() ? EMPTY_TOKEN : roster.get(0);
        final boolean rosterChanged = roster.size() != lastSize || token != lastToken;
        final boolean controlsChanged = group != lastGroup || sort != lastSort
                || reversed != lastReversed || !filter.equals(lastFilter);
        if (!rosterChanged && !controlsChanged) {
            return;
        }
        rows = PduTableModel.build(roster, group, sort, reversed, filter);
        rebuildIndices();
        peakDraw = 1;
        for (BranchDevice d : roster) {
            // both figures on one peak or the tick sits left of a bar it's bigger than

            if (!d.isStorage()) {
                peakDraw = Math.max(peakDraw, Math.max(d.draw(), d.cost()));
            }
        }
        pruneSelectionAndCursor();
        lastSize = roster.size();
        lastToken = token;
        lastGroup = group;
        lastSort = sort;
        lastReversed = reversed;
        lastFilter = filter;
    }

    private void rebuildIndices() {
        groupOf = new int[rows.size()];
        nextHeaderOf = new int[rows.size()];
        int lastHeader = -1;
        final List<Integer> headers = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i) instanceof GroupRow) {
                lastHeader = i;
                headers.add(i);
            }
            groupOf[i] = lastHeader;
        }
        for (int k = 0; k < headers.size(); k++) {
            final int h = headers.get(k);
            nextHeaderOf[h] = k + 1 < headers.size() ? headers.get(k + 1) : rows.size();
        }
    }

    private void pruneSelectionAndCursor() {
        selection.removeIf(pos -> rowIndexOf(pos) < 0);
        if (cursorPos != null && rowIndexOf(cursorPos) < 0) {
            cursorPos = null;
        }
        if (anchorPos != null && rowIndexOf(anchorPos) < 0) {
            anchorPos = null;
        }
    }

    private int rowIndexOf(BlockPos pos) {
        if (pos == null) {
            return -1;
        }
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i) instanceof DeviceRow d && d.device().pos().equals(pos)) {
                return i;
            }
        }
        return -1;
    }

    public boolean isFocused() {
        return focused;
    }

    public void setFocused(boolean focused) {
        this.focused = focused;

        if (focused && cursorPos == null) {
            final int first = firstDeviceRow();
            if (first >= 0) {
                cursorPos = ((DeviceRow) rows.get(first)).device().pos();
            }
        }
    }

    public void setHeaderFocused(boolean headerFocused) {
        this.headerFocused = headerFocused;
    }

    public int selectionSize() {
        return selection.size();
    }

    public int selectedLoadCount() {
        int n = 0;
        for (Row r : rows) {
            if (r instanceof DeviceRow d && !d.device().isStorage()
                    && selection.contains(d.device().pos())) {
                n++;
            }
        }
        return n;
    }

    public boolean hasSelection() {
        return !selection.isEmpty();
    }

    public void clearSelection() {
        selection.clear();
        anchorPos = null;
    }

    public void selectOnly(BlockPos pos) {
        selection.clear();
        selection.add(pos);
        anchorPos = pos;
        cursorPos = pos;
    }

    public void selectAllVisible() {
        selection.clear();
        for (Row r : rows) {
            if (r instanceof DeviceRow d) {
                selection.add(d.device().pos());
                cursorPos = d.device().pos();
            }
        }
        anchorPos = cursorPos;
    }

    public boolean isSelected(BlockPos pos) {
        return selection.contains(pos);
    }

    public BranchDevice singleSelected() {
        if (selection.size() != 1) {
            return null;
        }
        final BlockPos only = selection.iterator().next();
        final int i = rowIndexOf(only);
        return i < 0 ? null : ((DeviceRow) rows.get(i)).device();
    }

    public List<BranchDevice> selectedDevices() {
        final List<BranchDevice> out = new ArrayList<>(selection.size());
        for (Row r : rows) {
            if (r instanceof DeviceRow d && selection.contains(d.device().pos())) {
                out.add(d.device());
            }
        }
        return out;
    }

    public List<BranchDevice> selectedLoads() {
        final List<BranchDevice> out = new ArrayList<>(selection.size());
        for (Row r : rows) {
            if (r instanceof DeviceRow d && !d.device().isStorage()
                    && selection.contains(d.device().pos())) {
                out.add(d.device());
            }
        }
        return out;
    }

    public int visibleDeviceCount() {
        return PduTableModel.deviceCount(rows);
    }

    public BranchDevice deviceAt(double mx, double my) {
        if (!area.contains(mx, my) || rows.isEmpty() || overStickyHeader(my)) {
            return null;
        }
        final int i = (int) ((my - area.y() + scroll) / rowH);
        return i >= 0 && i < rows.size() && rows.get(i) instanceof DeviceRow d ? d.device() : null;
    }

    public PduRect bounds() {
        return bounds;
    }

    public PduLoadColumns columns() {
        return cols;
    }

    public int rowCount() {
        return rows.size();
    }

    public PduRect rowRect(int i) {
        return new PduRect(area.x(), area.y() + i * rowH - scroll, area.w() - scrollbarInset(), rowH);
    }

    public BranchDevice deviceOnRow(int i) {
        return i >= 0 && i < rows.size() && rows.get(i) instanceof DeviceRow d ? d.device() : null;
    }

    public void draw(GuiGraphics g, Font font, PduRect outer, int rowHeight, int mouseX, int mouseY,
                     float partialTick) {
        this.bounds = outer;
        this.area = new PduRect(outer.x(), outer.y() + HEADER_H, outer.w(),
                Math.max(0, outer.h() - HEADER_H));
        this.rowH = rowHeight;
        cols = PduLoadColumns.of(area.x(), area.w() - scrollbarInset());
        contentHeight = rows.size() * rowH;
        clampScroll();

        drawColumnHeader(g, font, mouseX, mouseY);

        if (rows.isEmpty()) {
            final String msg = "No devices match";
            g.drawString(font, msg, area.centerX() - font.width(msg) / 2, area.centerY() - 4,
                    PduTheme.TEXT_FAINT, false);
            return;
        }

        g.enableScissor(area.x(), area.y(), area.right(), area.bottom());
        final int topRow = Mth.clamp(scroll / rowH, 0, rows.size() - 1);
        final int first = Math.max(0, topRow - OVERSCAN);
        final int last = Math.min(rows.size() - 1, (scroll + area.h()) / rowH + OVERSCAN);
        for (int i = first; i <= last; i++) {
            final int y = area.y() + i * rowH - scroll;
            drawRow(g, font, i, y, mouseX, mouseY);
        }
        g.disableScissor();

        drawStickyHeader(g, font, topRow);
        drawScrollbar(g, mouseX, mouseY, partialTick);
    }

    private void drawColumnHeader(GuiGraphics g, Font font, int mouseX, int mouseY) {
        final int y = bounds.y();
        g.fill(bounds.x(), y, bounds.right(), y + HEADER_H, PduTheme.BG_DEEP);
        g.fill(bounds.x(), y + HEADER_H - 1, bounds.right(), y + HEADER_H, PduTheme.STROKE);
        final boolean overHeader = mouseY >= y && mouseY < y + HEADER_H
                && mouseX >= bounds.x() && mouseX < bounds.right();
        final Sort hovered = overHeader ? cols.columnAt(mouseX) : null;

        for (Sort column : Sort.values()) {
            if (column == Sort.LEG && !cols.legShown()) {
                continue;
            }
            final int[] box = cols.boundsOf(column);
            final boolean active = column == sort;
            final int colour = active ? PduTheme.TEXT
                    : column == hovered ? PduTheme.TEXT_DIM : PduTheme.TEXT_FAINT;
            final String label = column.label();

            final boolean rightAligned = column == Sort.DRAW || column == Sort.LEG;
            final int arrowW = active ? 7 : 0;
            final int textX = rightAligned
                    ? box[0] + box[1] - font.width(label)
                    : box[0] + arrowW;
            g.drawString(font, trim(font, label, box[1] - arrowW), textX, y + 1, colour, false);
            if (active) {
                final int ax = rightAligned ? textX - arrowW : box[0];
                drawSortArrow(g, ax + 1, y + 4, !reversed, PduTheme.ACCENT);
                g.fill(box[0], y + HEADER_H - 2, box[0] + box[1], y + HEADER_H - 1, PduTheme.ACCENT);
            }
            if (headerFocused && active) {
                PduTheme.outline(g, box[0] - 1, y, box[1] + 2, HEADER_H - 1, PduTheme.STROKE_HOT);
            }
        }

        PduGlyphs.drawLock(g, cols.lockX(), y + 1, FeLock.NONE, false);
    }

    private static void drawSortArrow(GuiGraphics g, int x, int y, boolean down, int colour) {
        if (down) {
            g.fill(x, y, x + 5, y + 1, colour);
            g.fill(x + 1, y + 1, x + 4, y + 2, colour);
            g.fill(x + 2, y + 2, x + 3, y + 3, colour);
        } else {
            g.fill(x + 2, y, x + 3, y + 1, colour);
            g.fill(x + 1, y + 1, x + 4, y + 2, colour);
            g.fill(x, y + 2, x + 5, y + 3, colour);
        }
    }

    private void drawRow(GuiGraphics g, Font font, int i, int y, int mouseX, int mouseY) {
        final int rowRight = area.right() - scrollbarInset();
        if (rows.get(i) instanceof GroupRow header) {
            drawGroupRow(g, font, header, y, rowRight);
        } else {
            drawDeviceRow(g, font, ((DeviceRow) rows.get(i)).device(), y, rowRight, mouseX, mouseY);
        }
    }

    private void drawGroupRow(GuiGraphics g, Font font, GroupRow header, int y, int rowRight) {
        g.fill(area.x(), y, rowRight, y + rowH, PduTheme.BG_DEEP);
        g.fill(area.x(), y + rowH - 1, rowRight, y + rowH, PduTheme.STROKE);
        PduCard.drawHeading(g, font, header.label(), area.x() + 4, y + (rowH - 8) / 2);
        final String meta;
        final int metaColour;
        if (header.storage()) {
            meta = String.format(Locale.ROOT, "%d %s   %d%%   %s%s FE/t",
                    header.total(), header.total() == 1 ? "tank" : "tanks",
                    Math.round(header.fill() * 100.0f),
                    header.totalDraw() > 0 ? "+" : "", compact(header.totalDraw()));
            metaColour = header.totalDraw() > 0 ? PduTheme.OK
                    : header.totalDraw() < 0 ? PduTheme.INFO : PduTheme.TEXT_DIM;
        } else {
            meta = String.format(Locale.ROOT, "%d/%d   %s FE/t",
                    header.served(), header.total(), compact(header.totalDraw()));
            metaColour = header.served() < header.total() ? PduTheme.WARN : PduTheme.TEXT_DIM;
        }
        g.drawString(font, meta, rowRight - 4 - font.width(meta), y + (rowH - 8) / 2, metaColour, false);
    }

    private void drawDeviceRow(GuiGraphics g, Font font, BranchDevice d, int y, int rowRight,
                               int mouseX, int mouseY) {
        final boolean selected = selection.contains(d.pos());
        final boolean isCursor = focused && d.pos().equals(cursorPos);
        final boolean hovered = mouseX >= area.x() && mouseX < rowRight && mouseY >= y && mouseY < y + rowH
                && !overStickyHeader(mouseY);

        if (d.isStorage()) {
            drawStorageRow(g, font, d, y, rowRight, selected, hovered, isCursor);
            return;
        }

        final Status status = Status.of(d);
        g.fill(area.x(), y, rowRight, y + rowH, selected || hovered ? PduTheme.BG_RAISED : PduTheme.BG_PANEL);
        if (status == Status.SHED) {
            g.fill(area.x(), y, rowRight, y + rowH, PduTheme.withAlpha(PduTheme.WARN, 0x16));
        } else if (status == Status.BREAKER) {
            g.fill(area.x(), y, rowRight, y + rowH, PduTheme.withAlpha(PduTheme.CRIT, 0x14));
        }
        if (selected) {
            g.fill(area.x(), y, area.x() + 2, y + rowH, PduTheme.ACCENT);
        }

        final int midText = y + (rowH - 8) / 2;
        final int pipColour = PduGlyphs.drawPip(g, cols.pipX(), y + (rowH - 6) / 2, d);

        final int nameColour = status.isDark() ? PduTheme.TEXT_DIM : PduTheme.TEXT;

        g.fill(cols.nameX(), midText - 1, cols.nameX() + 3, midText + 8, PduLegRow.colour(d.leg()));
        g.drawString(font, trim(font, d.label(), cols.nameW() - 6), cols.nameX() + 6, midText,
                nameColour, false);

        final boolean tierHover = hovered && mouseX >= cols.tierX() && mouseX < cols.tierX() + cols.tierW();
        PduChip.draw(g, font, cols.tierX(), y + (rowH - PduChip.HEIGHT) / 2, tierLabel(font, d),
                PduGlyphs.tierColour(d.priority()), tierHover, false);

        drawDrawColumn(g, font, d, y, midText);
        drawStateColumn(g, font, d, status, midText, pipColour);

        if (cols.legShown()) {
            final String leg = legShortName(d.leg());
            g.drawString(font, leg, cols.legX() + cols.legW() - font.width(leg), midText,
                    PduTheme.TEXT_FAINT, false);
        }

        final boolean lockHover = hovered && mouseX >= cols.lockX() && mouseX < cols.lockX() + 10;
        PduGlyphs.drawLock(g, cols.lockX(), y + (rowH - 8) / 2, d.lock(), lockHover);

        if (isCursor) {
            PduTheme.outline(g, area.x() + 1, y, rowRight - area.x() - 1, rowH, PduTheme.STROKE_HOT);
        }
    }

    private void drawDrawColumn(GuiGraphics g, Font font, BranchDevice d, int y, int midText) {
        final int barY = y + rowH - 3;
        final int w = cols.drawW();
        final int x = cols.drawX();
        final int barW = Math.min(w, Math.round(w * (d.draw() / (float) peakDraw)));
        g.fill(x, barY, x + barW, barY + 1,
                PduTheme.withAlpha(d.shed() ? PduTheme.WARN : PduTheme.INFO, 0x55));
        if (d.cost() != d.draw()) {
            final int costX = x + Math.min(w - 1, Math.round(w * (d.cost() / (float) peakDraw)));
            g.fill(costX, barY - 1, costX + 1, barY + 2, PduTheme.withAlpha(PduTheme.TEXT_DIM, 0xAA));
        }
        final String draw = String.format(Locale.ROOT, "%,d", d.draw());
        final int unitW = font.width(" FE/t");
        g.drawString(font, "FE/t", x + w - font.width("FE/t"), midText, PduTheme.TEXT_FAINT, false);
        g.drawString(font, draw, x + w - unitW - font.width(draw), midText, PduTheme.TEXT_DIM, false);
    }

    // the full sentence never fit. "Shed: gener..." for all five reasons, which exist because they need five different fixes
    private void drawStateColumn(GuiGraphics g, Font font, BranchDevice d, Status status, int midText,
                                 int pipColour) {
        final String shown = cols.stateWordsShown() ? d.reason().shortLabel() : d.reason().shortFlag();
        g.drawString(font, trim(font, shown, cols.stateW()), cols.stateX(), midText,
                status == Status.RUNNING ? PduTheme.TEXT_DIM : pipColour, false);
    }

    private String tierLabel(Font font, BranchDevice d) {
        final String full = d.priority().getDisplayName();
        return PduChip.width(font, full) <= cols.tierW() ? full : d.priority().getShortName();
    }

    private static String legShortName(int leg) {
        return PduLegRow.isBack(leg) ? "Back" : "L" + (leg + 1);
    }

    private void drawStorageRow(GuiGraphics g, Font font, BranchDevice d, int y, int rowRight,
                                boolean selected, boolean hovered, boolean isCursor) {
        g.fill(area.x(), y, rowRight, y + rowH,
                selected || hovered ? PduTheme.BG_RAISED : PduTheme.BG_PANEL);
        if (selected) {
            g.fill(area.x(), y, area.x() + 2, y + rowH, PduTheme.ACCENT);
        }

        final int midText = y + (rowH - 8) / 2;
        final int flow = d.draw();
        final int accent = flow > 0 ? PduTheme.OK : flow < 0 ? PduTheme.INFO : PduTheme.TEXT_DIM;

        PduGlyphs.drawPip(g, cols.pipX(), y + (rowH - 6) / 2, d);

        g.fill(cols.nameX(), midText - 1, cols.nameX() + 3, midText + 8, PduLegRow.colour(d.leg()));
        g.drawString(font, trim(font, d.label(), cols.nameW() - 6), cols.nameX() + 6, midText,
                PduTheme.TEXT, false);

        PduChip.draw(g, font, cols.tierX(), y + (rowH - PduChip.HEIGHT) / 2,
                PduChip.width(font, "STORAGE") <= cols.tierW() ? "STORAGE" : "TANK",
                PduTheme.INFO, false, false);

        final int fillW = Math.round(cols.drawW() * d.fill());
        g.fill(cols.drawX(), y + rowH - 3, cols.drawX() + fillW, y + rowH - 2,
                PduTheme.withAlpha(PduTheme.INFO, 0x55));
        final String value = flow == 0 ? "idle"
                : String.format(Locale.ROOT, "%s%,d", flow > 0 ? "+" : "", flow);
        final int unitW = font.width(" FE/t");
        if (flow != 0) {
            g.drawString(font, "FE/t", cols.drawX() + cols.drawW() - font.width("FE/t"), midText,
                    PduTheme.TEXT_FAINT, false);
            g.drawString(font, value, cols.drawX() + cols.drawW() - unitW - font.width(value), midText,
                    accent, false);
        } else {
            g.drawString(font, value, cols.drawX() + cols.drawW() - font.width(value), midText,
                    PduTheme.TEXT_FAINT, false);
        }

        final String pct = Math.round(d.fill() * 100.0f) + "%";
        g.drawString(font, trim(font, pct, cols.stateW()), cols.stateX(), midText, accent, false);

        if (cols.legShown()) {
            final String leg = legShortName(d.leg());
            g.drawString(font, leg, cols.legX() + cols.legW() - font.width(leg), midText,
                    PduTheme.TEXT_FAINT, false);
        }

        if (isCursor) {
            PduTheme.outline(g, area.x() + 1, y, rowRight - area.x() - 1, rowH, PduTheme.STROKE_HOT);
        }
    }

    private void drawStickyHeader(GuiGraphics g, Font font, int topRow) {
        if (groupOf.length == 0) {
            return;
        }
        final int h = groupOf[topRow];
        if (h < 0 || !(rows.get(h) instanceof GroupRow header)) {
            return;
        }
        drawGroupRow(g, font, header, stickyY(), area.right() - scrollbarInset());
    }

    private int stickyY() {
        if (groupOf.length == 0) {
            return area.y();
        }
        final int topRow = Mth.clamp(scroll / rowH, 0, rows.size() - 1);
        final int h = groupOf[topRow];
        if (h < 0) {
            return area.y();
        }
        final int nextH = nextHeaderOf[h];
        if (nextH < rows.size()) {
            final int nextScreenY = area.y() + nextH * rowH - scroll;
            if (nextScreenY < area.y() + rowH) {
                return nextScreenY - rowH;
            }
        }
        return area.y();
    }

    private boolean overStickyHeader(double my) {
        if (rows.isEmpty() || groupOf.length == 0) {
            return false;
        }
        final int topRow = Mth.clamp(scroll / rowH, 0, rows.size() - 1);
        if (groupOf[topRow] < 0) {
            return false;
        }

        final int y = stickyY();
        return my >= y && my < y + rowH && y + rowH > area.y();
    }

    private int scrollbarInset() {
        return contentHeight > area.h() ? 7 : 0;
    }

    private void drawScrollbar(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (contentHeight <= area.h()) {
            scrollbarAlpha = Ease.ease(scrollbarAlpha, 0.0f, 0.3f, partialTick);
            return;
        }
        final boolean hovered = area.contains(mouseX, mouseY) || draggingThumb;
        scrollbarAlpha = Ease.ease(scrollbarAlpha, hovered ? 1.0f : 0.25f, 0.3f, partialTick);
        final int alpha = Math.round(Mth.clamp(scrollbarAlpha, 0.0f, 1.0f) * 0xFF);
        if (alpha <= 4) {
            return;
        }
        final int trackX = area.right() - 5;
        g.fill(trackX, area.y(), trackX + 3, area.bottom(), PduTheme.withAlpha(PduTheme.BG_PANEL, alpha));
        final int thumbH = thumbHeight();
        final int thumbY = thumbTop();
        g.fill(trackX, thumbY, trackX + 3, thumbY + thumbH,
                PduTheme.withAlpha(draggingThumb ? PduTheme.TEXT : PduTheme.INFO, alpha));
    }

    private int thumbHeight() {
        return Math.max(12, (int) ((long) area.h() * area.h() / contentHeight));
    }

    private int thumbTop() {
        final int maxScroll = Math.max(1, contentHeight - area.h());
        final int travel = Math.max(1, area.h() - thumbHeight());
        return area.y() + Math.round(travel * (scroll / (float) maxScroll));
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double dy) {
        if (!area.contains(mouseX, mouseY) || contentHeight <= area.h()) {
            return false;
        }
        final long now = Util.getMillis();

        wheelAccel = now - lastWheelMs < 140 ? Math.min(wheelAccel + 0.6f, 4.0f) : 1.0f;
        lastWheelMs = now;
        setScroll(scroll - (int) Math.round(dy * rowH * 1.5f * wheelAccel));
        return true;
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button, boolean shift, boolean ctrl,
                                Callback cb) {
        if (!bounds.contains(mouseX, mouseY)) {
            return false;
        }

        if (mouseY < area.y()) {
            if (button == 0) {
                final Sort column = cols.columnAt(mouseX);
                if (column != null) {
                    cb.requestSort(column);
                }
            }
            return true;
        }

        if (button == 0 && contentHeight > area.h() && mouseX >= area.right() - 6) {
            final int thumbY = thumbTop();
            final int thumbH = thumbHeight();
            if (mouseY >= thumbY && mouseY < thumbY + thumbH) {
                draggingThumb = true;
                thumbGrabOffset = (int) (mouseY - thumbY);
            } else {
                setScroll(scroll + (mouseY < thumbY ? -area.h() : area.h()));
            }
            return true;
        }

        final int rowRight = area.right() - scrollbarInset();
        if (mouseX >= rowRight) {
            return true;
        }

        if (overStickyHeader(mouseY)) {
            if (button == 0) {
                final int topRow = Mth.clamp(scroll / rowH, 0, rows.size() - 1);
                selectGroup(groupOf[topRow]);
            }
            return true;
        }
        final int i = (int) ((mouseY - area.y() + scroll) / rowH);
        if (i < 0 || i >= rows.size()) {
            return true;
        }
        if (rows.get(i) instanceof GroupRow) {
            if (button == 0) {
                selectGroup(i);
            }
            return true;
        }
        final BranchDevice d = ((DeviceRow) rows.get(i)).device();
        if (button == 0 || button == 1) {
            if (!d.isStorage()) {
                if (mouseX >= cols.tierX() && mouseX < cols.tierX() + cols.tierW()) {
                    cb.requestSetTier(d, button == 0 ? d.priority().next() : prevTier(d.priority()));
                    return true;
                }
                if (mouseX >= cols.lockX() - 3 && mouseX < cols.lockX() + 13) {
                    cb.requestSetLock(d, button == 0 ? d.lock().next() : d.lock().next().next());
                    return true;
                }
            }
            if (button == 0) {
                selectAt(i, shift, ctrl);
            }
            return true;
        }
        return true;
    }

    public boolean mouseDragged(double mouseX, double mouseY) {
        if (!draggingThumb) {
            return false;
        }
        final int travel = Math.max(1, area.h() - thumbHeight());
        final int maxScroll = Math.max(0, contentHeight - area.h());
        final float at = (float) (mouseY - thumbGrabOffset - area.y()) / travel;
        setScroll(Math.round(at * maxScroll));
        return true;
    }

    public void mouseReleased() {
        draggingThumb = false;
    }

    public boolean keyPressed(int keyCode, Callback cb) {
        if (rows.isEmpty()) {
            return false;
        }
        switch (keyCode) {
            case GLFW.GLFW_KEY_UP -> {
                moveCursor(-1);
                return true;
            }
            case GLFW.GLFW_KEY_DOWN -> {
                moveCursor(1);
                return true;
            }
            case GLFW.GLFW_KEY_PAGE_UP -> {
                moveCursorBy(-Math.max(1, area.h() / rowH - 1));
                return true;
            }
            case GLFW.GLFW_KEY_PAGE_DOWN -> {
                moveCursorBy(Math.max(1, area.h() / rowH - 1));
                return true;
            }
            case GLFW.GLFW_KEY_HOME -> {
                cursorToRow(firstDeviceRow(), true);
                return true;
            }
            case GLFW.GLFW_KEY_END -> {
                cursorToRow(lastDeviceRow(), true);
                return true;
            }
            case GLFW.GLFW_KEY_LEFT -> {
                for (BranchDevice d : keyboardTargets()) {
                    cb.requestSetTier(d, prevTier(d.priority()));
                }
                return true;
            }
            case GLFW.GLFW_KEY_RIGHT -> {
                for (BranchDevice d : keyboardTargets()) {
                    cb.requestSetTier(d, d.priority().next());
                }
                return true;
            }
            case GLFW.GLFW_KEY_L -> {
                for (BranchDevice d : keyboardTargets()) {
                    cb.requestSetLock(d, d.lock().next());
                }
                return true;
            }
            case GLFW.GLFW_KEY_SPACE -> {
                final BranchDevice d = cursorDevice();
                if (d != null) {
                    toggle(d.pos());
                    anchorPos = d.pos();
                }
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    private void selectAt(int rowIndex, boolean shift, boolean ctrl) {
        final BlockPos pos = ((DeviceRow) rows.get(rowIndex)).device().pos();
        if (shift && anchorPos != null) {
            selectRange(rowIndexOf(anchorPos), rowIndex);
        } else if (ctrl) {
            toggle(pos);
            anchorPos = pos;
        } else {
            selection.clear();
            selection.add(pos);
            anchorPos = pos;
        }
        cursorPos = pos;
    }

    private void selectRange(int a, int b) {
        if (a < 0) {
            a = b;
        }
        final int lo = Math.min(a, b);
        final int hi = Math.max(a, b);
        selection.clear();
        for (int i = lo; i <= hi; i++) {
            if (rows.get(i) instanceof DeviceRow d) {
                selection.add(d.device().pos());
            }
        }
    }

    private void selectGroup(int headerIndex) {
        if (headerIndex < 0 || headerIndex >= rows.size()) {
            return;
        }
        selection.clear();
        for (int i = headerIndex + 1; i < rows.size() && rows.get(i) instanceof DeviceRow d; i++) {
            selection.add(d.device().pos());
            anchorPos = d.device().pos();
            cursorPos = d.device().pos();
        }
    }

    private void toggle(BlockPos pos) {
        if (!selection.remove(pos)) {
            selection.add(pos);
        }
    }

    private void moveCursor(int dir) {
        final int cur = cursorPos == null ? -1 : rowIndexOf(cursorPos);
        final int next = dir < 0 ? prevDeviceRow(cur) : nextDeviceRow(cur);
        if (next >= 0) {
            cursorToRow(next, true);
        }
    }

    private void moveCursorBy(int rowsDelta) {
        int cur = cursorPos == null ? firstDeviceRow() : rowIndexOf(cursorPos);
        if (cur < 0) {
            return;
        }
        int target = Mth.clamp(cur + rowsDelta, 0, rows.size() - 1);

        while (target > 0 && target < rows.size() && !(rows.get(target) instanceof DeviceRow)) {
            target += rowsDelta > 0 ? 1 : -1;
        }
        if (target >= 0 && target < rows.size() && rows.get(target) instanceof DeviceRow) {
            cursorToRow(target, true);
        }
    }

    private void cursorToRow(int rowIndex, boolean select) {
        if (rowIndex < 0 || rowIndex >= rows.size() || !(rows.get(rowIndex) instanceof DeviceRow d)) {
            return;
        }
        cursorPos = d.device().pos();
        if (select) {
            selection.clear();
            selection.add(cursorPos);
            anchorPos = cursorPos;
        }
        ensureVisible(rowIndex);
    }

    private void ensureVisible(int rowIndex) {
        final int rowTop = rowIndex * rowH;

        if (rowTop - rowH < scroll) {
            setScroll(rowTop - rowH);
        } else if (rowTop + rowH > scroll + area.h()) {
            setScroll(rowTop + rowH - area.h());
        }
    }

    private BranchDevice cursorDevice() {
        final int i = cursorPos == null ? -1 : rowIndexOf(cursorPos);
        return i < 0 ? null : ((DeviceRow) rows.get(i)).device();
    }

    // filter batteries out, a multi select with a tank sends a tier packet for a tank
    private List<BranchDevice> keyboardTargets() {
        if (selectionSize() > 1) {
            return selectedLoads();
        }
        final BranchDevice cursor = cursorDevice();
        return cursor == null || cursor.isStorage() ? List.of() : List.of(cursor);
    }

    private int firstDeviceRow() {
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i) instanceof DeviceRow) {
                return i;
            }
        }
        return -1;
    }

    private int lastDeviceRow() {
        for (int i = rows.size() - 1; i >= 0; i--) {
            if (rows.get(i) instanceof DeviceRow) {
                return i;
            }
        }
        return -1;
    }

    private int nextDeviceRow(int from) {
        for (int i = from + 1; i < rows.size(); i++) {
            if (rows.get(i) instanceof DeviceRow) {
                return i;
            }
        }
        return from >= 0 ? from : firstDeviceRow();
    }

    private int prevDeviceRow(int from) {
        for (int i = from - 1; i >= 0; i--) {
            if (rows.get(i) instanceof DeviceRow) {
                return i;
            }
        }
        return from >= 0 ? from : lastDeviceRow();
    }

    private static FePriority prevTier(FePriority tier) {
        final FePriority[] values = FePriority.values();
        return values[(tier.ordinal() + values.length - 1) % values.length];
    }

    private void setScroll(int value) {
        scroll = Mth.clamp(value, 0, Math.max(0, contentHeight - area.h()));
    }

    private void clampScroll() {
        scroll = Mth.clamp(scroll, 0, Math.max(0, contentHeight - area.h()));
    }

    private static String compact(int v) {
        if (v >= 1_000_000) {
            return String.format(Locale.ROOT, "%.1fM", v / 1_000_000.0);
        }
        if (v >= 1_000) {
            return String.format(Locale.ROOT, "%.1fk", v / 1_000.0);
        }
        return String.valueOf(v);
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
