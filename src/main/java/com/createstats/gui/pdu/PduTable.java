package com.createstats.gui.pdu;

import com.createstats.blockentity.PowerDistributionUnitBE.BranchDevice;
import com.createstats.gui.pdu.PduTableModel.DeviceRow;
import com.createstats.gui.pdu.PduTableModel.GroupRow;
import com.createstats.gui.pdu.PduTableModel.Group;
import com.createstats.gui.pdu.PduTableModel.Row;
import com.createstats.gui.pdu.PduTableModel.Sort;
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

/** [1] */
public final class PduTable {

    /** The screen fields these so it can apply the confirmation guard and send the validated packets. */
    public interface Callback {
        void requestSetTier(BranchDevice device, FePriority tier);

        void requestSetLock(BranchDevice device, FeLock lock);
    }

    /** Sentinel for "roster is empty", so the rebuild check never dereferences a missing first element. */
    private static final Object EMPTY_TOKEN = new Object();
    private static final int OVERSCAN = 2;

    private List<Row> rows = List.of();
    private int[] groupOf = new int[0];
    private int[] nextHeaderOf = new int[0];
    private int peakDraw = 1;

    // Rebuild detection. The first device's identity plus the size catches a sync (which recreates
    // every record), and the control fields catch a search, group or sort change.
    private Object lastToken;
    private int lastSize = -1;
    private Group lastGroup;
    private Sort lastSort;
    private boolean lastReversed;
    private String lastSearch = "\u0000";
    private boolean lastShedOnly;
    private FePriority lastTierFilter;

    private final LinkedHashSet<BlockPos> selection = new LinkedHashSet<>();
    private BlockPos cursorPos;
    private BlockPos anchorPos;
    private boolean focused;

    private int scroll;
    private int contentHeight;
    private float scrollbarAlpha;
    private boolean draggingThumb;
    private int thumbGrabOffset;
    private long lastWheelMs;
    private float wheelAccel = 1.0f;

    private PduRect area = new PduRect(0, 0, 0, 0);
    private int rowH = 16;
    // Column geometry, recomputed each frame into fields so no record is allocated in the hot path.
    private int pipX;
    private int nameX;
    private int nameW;
    private int tierX;
    private int tierW;
    private int drawColX;
    private int drawColW;
    private int reasonX;
    private int reasonW;
    private int lockX;

    // data

    /** Rebuilds the model only when the roster or the controls actually change. */
    public void setData(List<BranchDevice> roster, Group group, Sort sort, boolean reversed,
                        String search, boolean shedOnly) {
        setData(roster, group, sort, reversed, search, shedOnly, null);
    }

    /** Rebuilds the model only when the roster or the controls actually change. */
    public void setData(List<BranchDevice> roster, Group group, Sort sort, boolean reversed,
                        String search, boolean shedOnly, FePriority tierFilter) {
        final Object token = roster.isEmpty() ? EMPTY_TOKEN : roster.get(0);
        final boolean rosterChanged = roster.size() != lastSize || token != lastToken;
        final boolean controlsChanged = group != lastGroup || sort != lastSort
                || reversed != lastReversed || !search.equals(lastSearch) || shedOnly != lastShedOnly
                || tierFilter != lastTierFilter;
        if (!rosterChanged && !controlsChanged) {
            return;
        }
        rows = PduTableModel.build(roster, group, sort, reversed, search, shedOnly, tierFilter);
        rebuildIndices();
        peakDraw = 1;
        for (BranchDevice d : roster) {
            peakDraw = Math.max(peakDraw, d.draw());
        }
        pruneSelectionAndCursor();
        lastSize = roster.size();
        lastToken = token;
        lastGroup = group;
        lastSort = sort;
        lastReversed = reversed;
        lastSearch = search;
        lastShedOnly = shedOnly;
        lastTierFilter = tierFilter;
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

    // queries

    public boolean isFocused() {
        return focused;
    }

    public void setFocused(boolean focused) {
        this.focused = focused;
        // Give the keyboard something to land on the moment the table takes focus.
        if (focused && cursorPos == null) {
            final int first = firstDeviceRow();
            if (first >= 0) {
                cursorPos = ((DeviceRow) rows.get(first)).device().pos();
            }
        }
    }

    public int selectionSize() {
        return selection.size();
    }

    public boolean hasSelection() {
        return !selection.isEmpty();
    }

    public void clearSelection() {
        selection.clear();
        anchorPos = null;
    }

    /** Selects exactly this device, for a cross highlight click from the wiring diagram. */
    public void selectOnly(BlockPos pos) {
        selection.clear();
        selection.add(pos);
        anchorPos = pos;
        cursorPos = pos;
    }

    /** Selects every device currently in the table, after filter and search, for a keyboard bulk edit. */
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

    /** Whether a position is in the selection, so the diagram can highlight what the table has selected. */
    public boolean isSelected(BlockPos pos) {
        return selection.contains(pos);
    }

    /** The single selected device, or null unless exactly one is selected. Drives the inspector. */
    public BranchDevice singleSelected() {
        if (selection.size() != 1) {
            return null;
        }
        final BlockPos only = selection.iterator().next();
        final int i = rowIndexOf(only);
        return i < 0 ? null : ((DeviceRow) rows.get(i)).device();
    }

    /** Every selected device, in row order, for the bulk bar. Allocated only when a selection exists. */
    public List<BranchDevice> selectedDevices() {
        final List<BranchDevice> out = new ArrayList<>(selection.size());
        for (Row r : rows) {
            if (r instanceof DeviceRow d && selection.contains(d.device().pos())) {
                out.add(d.device());
            }
        }
        return out;
    }

    /** The device under the cursor, or null, for the hover tooltip the screen draws over everything. */
    public BranchDevice deviceAt(double mx, double my) {
        if (!area.contains(mx, my) || rows.isEmpty()) {
            return null;
        }
        final int i = (int) ((my - area.y() + scroll) / rowH);
        return i >= 0 && i < rows.size() && rows.get(i) instanceof DeviceRow d ? d.device() : null;
    }

    // draw

    public void draw(GuiGraphics g, Font font, PduRect bounds, int rowHeight, int mouseX, int mouseY,
                     float partialTick) {
        this.area = bounds;
        this.rowH = rowHeight;
        computeColumns();
        contentHeight = rows.size() * rowH;
        clampScroll();

        if (rows.isEmpty()) {
            final String msg = "No devices match";
            g.drawString(font, msg, bounds.centerX() - font.width(msg) / 2, bounds.centerY() - 4,
                    PduTheme.TEXT_FAINT, false);
            return;
        }

        g.enableScissor(bounds.x(), bounds.y(), bounds.right(), bounds.bottom());
        final int topRow = Mth.clamp(scroll / rowH, 0, rows.size() - 1);
        final int first = Math.max(0, topRow - OVERSCAN);
        final int last = Math.min(rows.size() - 1, (scroll + bounds.h()) / rowH + OVERSCAN);
        for (int i = first; i <= last; i++) {
            final int y = bounds.y() + i * rowH - scroll;
            drawRow(g, font, i, y, mouseX, mouseY);
        }
        g.disableScissor();

        drawStickyHeader(g, font, topRow);
        drawScrollbar(g, mouseX, mouseY, partialTick);
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
        final String meta = String.format(Locale.ROOT, "%d/%d   %s FE/t",
                header.served(), header.total(), compact(header.totalDraw()));
        g.drawString(font, meta, rowRight - 4 - font.width(meta), y + (rowH - 8) / 2,
                header.served() < header.total() ? PduTheme.WARN : PduTheme.TEXT_DIM, false);
    }

    private void drawDeviceRow(GuiGraphics g, Font font, BranchDevice d, int y, int rowRight,
                               int mouseX, int mouseY) {
        final boolean selected = selection.contains(d.pos());
        final boolean isCursor = focused && d.pos().equals(cursorPos);
        final boolean hovered = mouseX >= area.x() && mouseX < rowRight && mouseY >= y && mouseY < y + rowH;

        g.fill(area.x(), y, rowRight, y + rowH, selected || hovered ? PduTheme.BG_RAISED : PduTheme.BG_PANEL);
        if (d.shed() && d.lock() != FeLock.FORCE_OFF) {
            g.fill(area.x(), y, rowRight, y + rowH, PduTheme.withAlpha(PduTheme.WARN, 0x16));
        }
        if (selected) {
            g.fill(area.x(), y, area.x() + 2, y + rowH, PduTheme.ACCENT);
        }

        final int midText = y + (rowH - 8) / 2;
        final int pipColour = PduGlyphs.drawPip(g, pipX, y + (rowH - 6) / 2, d);

        final int nameColour = d.shed() || d.lock() == FeLock.FORCE_OFF ? PduTheme.TEXT_DIM : PduTheme.TEXT;
        // [2]
        g.fill(nameX, midText - 1, nameX + 3, midText + 8, PduLegRow.colour(d.leg()));
        g.drawString(font, trim(font, d.label(), nameW - 6), nameX + 6, midText, nameColour, false);

        // Tier chip, clickable to cycle.
        final String tierLabel = d.priority().getDisplayName();
        final boolean tierHover = hovered && mouseX >= tierX && mouseX < tierX + tierW;
        PduChip.draw(g, font, tierX, y + (rowH - PduChip.HEIGHT) / 2, tierLabel,
                PduGlyphs.tierColour(d.priority()), tierHover, false);

        // Draw, right aligned, with a proportional bar behind it.
        final int barW = Math.min(drawColW, Math.round(drawColW * (d.draw() / (float) peakDraw)));
        g.fill(drawColX, y + rowH - 3, drawColX + barW, y + rowH - 2,
                PduTheme.withAlpha(d.shed() ? PduTheme.WARN : PduTheme.INFO, 0x55));
        final String draw = String.format(Locale.ROOT, "%,d", d.draw());
        final int unitW = font.width(" FE/t");
        g.drawString(font, "FE/t", drawColX + drawColW - font.width("FE/t"), midText, PduTheme.TEXT_FAINT, false);
        g.drawString(font, draw, drawColX + drawColW - unitW - font.width(draw), midText, PduTheme.TEXT_DIM, false);

        // Full reason text, ellipsised, coloured to match the pip.
        g.drawString(font, trim(font, d.reason().description(), reasonW), reasonX, midText, pipColour, false);

        // Lock icon button.
        final boolean lockHover = hovered && mouseX >= lockX && mouseX < lockX + 10;
        PduGlyphs.drawLock(g, lockX, y + (rowH - 8) / 2, d.lock(), lockHover);

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
        int stickyY = area.y();
        final int nextH = nextHeaderOf[h];
        if (nextH < rows.size()) {
            // The next group's header shoves this one up as it arrives, the classic sticky push.
            final int nextScreenY = area.y() + nextH * rowH - scroll;
            if (nextScreenY < area.y() + rowH) {
                stickyY = nextScreenY - rowH;
            }
        }
        drawGroupRow(g, font, header, stickyY, area.right() - scrollbarInset());
    }

    // scrollbar

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

    // input

    public boolean mouseScrolled(double mouseX, double mouseY, double dy) {
        if (!area.contains(mouseX, mouseY) || contentHeight <= area.h()) {
            return false;
        }
        final long now = Util.getMillis();
        // Acceleration: a fast flick scrolls further per notch than a slow deliberate one.
        wheelAccel = now - lastWheelMs < 140 ? Math.min(wheelAccel + 0.6f, 4.0f) : 1.0f;
        lastWheelMs = now;
        setScroll(scroll - (int) Math.round(dy * rowH * 1.5f * wheelAccel));
        return true;
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button, boolean shift, boolean ctrl,
                                Callback cb) {
        if (!area.contains(mouseX, mouseY)) {
            return false;
        }
        // Scrollbar first: it overlaps the list's right edge.
        if (button == 0 && contentHeight > area.h() && mouseX >= area.right() - 6) {
            final int thumbY = thumbTop();
            final int thumbH = thumbHeight();
            if (mouseY >= thumbY && mouseY < thumbY + thumbH) {
                draggingThumb = true;
                thumbGrabOffset = (int) (mouseY - thumbY);
            } else {
                // Track click pages toward the cursor.
                setScroll(scroll + (mouseY < thumbY ? -area.h() : area.h()));
            }
            return true;
        }

        final int rowRight = area.right() - scrollbarInset();
        if (mouseX >= rowRight) {
            return true;
        }
        final int i = (int) ((mouseY - area.y() + scroll) / rowH);
        if (i < 0 || i >= rows.size()) {
            return true;
        }
        if (rows.get(i) instanceof GroupRow) {
            // A header click selects that whole group, the fastest way to a bulk action.
            if (button == 0) {
                selectGroup(i);
            }
            return true;
        }
        final BranchDevice d = ((DeviceRow) rows.get(i)).device();
        if (button == 0 || button == 1) {
            // [3]
            if (mouseX >= tierX && mouseX < tierX + tierW) {
                cb.requestSetTier(d, button == 0 ? d.priority().next() : prevTier(d.priority()));
                return true;
            }
            if (mouseX >= lockX - 3 && mouseX < lockX + 13) {
                cb.requestSetLock(d, button == 0 ? d.lock().next() : d.lock().next().next());
                return true;
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

    // selection

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
        // Land on a device row, not a header.
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
        // Keep one row of clearance at the top so the sticky header never covers the cursor row.
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

    /** [4] */
    private List<BranchDevice> keyboardTargets() {
        if (selectionSize() > 1) {
            return selectedDevices();
        }
        final BranchDevice c = cursorDevice();
        return c == null ? List.of() : List.of(c);
    }

    // row index helpers

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

    // layout

    private void computeColumns() {
        final int pad = 4;
        final int pipW = 10;
        final int lockW = 12;
        tierW = 52;
        drawColW = 74;
        final int gap = 6;
        pipX = area.x() + pad;
        final int fixed = pipW + tierW + drawColW + lockW + gap * 4;
        final int flex = Math.max(40, area.w() - 2 * pad - fixed);
        nameW = Math.max(40, Math.round(flex * 0.44f));
        reasonW = Math.max(24, flex - nameW);
        nameX = pipX + pipW;
        tierX = nameX + nameW + gap;
        drawColX = tierX + tierW + gap;
        reasonX = drawColX + drawColW + gap;
        lockX = reasonX + reasonW + gap;
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
