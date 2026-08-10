package com.createstats.gui;

import com.createstats.blockentity.PowerDistributionUnitBE;
import com.createstats.blockentity.PowerDistributionUnitBE.BranchDevice;
import com.createstats.gui.pdu.PduCard;
import com.createstats.gui.pdu.PduChart;
import com.createstats.gui.pdu.PduDiagram;
import com.createstats.gui.pdu.PduHistory;
import com.createstats.gui.pdu.PduChip;
import com.createstats.gui.pdu.Ease;
import com.createstats.gui.pdu.PduBalanceBar;
import com.createstats.gui.pdu.PduGauge;
import com.createstats.gui.pdu.PduGlyphs;
import com.createstats.gui.pdu.PduRing;
import com.createstats.gui.pdu.PduSparkline;
import com.createstats.gui.pdu.PduStatCard;
import com.createstats.gui.pdu.PduStatusLine;
import com.createstats.gui.pdu.PduDrawer;
import com.createstats.gui.pdu.PduIcons;
import com.createstats.gui.pdu.PduLegRow;
import com.createstats.gui.pdu.PduRail;
import com.createstats.gui.pdu.PduRect;
import com.createstats.gui.pdu.PduSearchField;
import com.createstats.gui.pdu.PduSegmented;
import com.createstats.gui.pdu.PduTable;
import com.createstats.gui.pdu.PduTableModel;
import com.createstats.gui.pdu.PduTheme;
import com.createstats.gui.pdu.PduToast;
import com.createstats.network.PduActionPacket;
import com.createstats.network.PduHistoryRequestPacket;
import com.createstats.network.PduSetPacket;
import com.createstats.power.FeGridSolver;
import com.createstats.power.FeLock;
import com.createstats.power.FePriority;
import com.createstats.power.PduMode;
import com.createstats.power.PduReason;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** [1] */
public class PowerDistributionUnitScreen extends Screen implements PduTable.Callback, PduDrawer.Callback {

    private enum View {
        OVERVIEW(PduIcons.OVERVIEW, "Overview"),
        LOADS(PduIcons.LOADS, "Loads"),
        LIGHTING(PduIcons.LIGHTING, "Lighting"),
        WIRING(PduIcons.WIRING, "Wiring"),
        FLOW(PduIcons.FLOW, "Flow"),
        LEGS(PduIcons.LEGS, "Legs"),
        HISTORY(PduIcons.HISTORY, "History");

        final String icon;
        final String title;

        View(String icon, String title) {
            this.icon = icon;
            this.title = title;
        }
    }

    /** The Tab focus ring, in order. The table holds focus by default because it is the content. */
    private enum Focus {
        SEARCH, GROUP, SORT, SHED, TABLE
    }

    private static final int RAIL_W = 26;
    private static final int FOOTER_H = 16;
    private static final int TOOLBAR_H = 20;
    private static final int LIGHTING_HEADER_H = 24;
    private static final long CONFIRM_MS = 2000L;

    private final PowerDistributionUnitBE pdu;
    private final PduRail rail;
    private final PduTable table = new PduTable();
    private final PduDrawer drawer = new PduDrawer();
    private final PduDiagram diagram = new PduDiagram();
    private final PduSearchField search = new PduSearchField("Search devices");
    private final PduSegmented groupSeg = new PduSegmented("Tier", "Type", "Status");
    private final PduSegmented sortSeg = new PduSegmented("Draw", "Name", "Status");
    private boolean sortReversed;
    private boolean shedOnly;
    private Focus focus = Focus.TABLE;

    private PduTheme.Breakpoint breakpoint = PduTheme.Breakpoint.NORMAL;
    private PduRect panel = new PduRect(0, 0, 0, 0);
    private PduRect railRect = new PduRect(0, 0, 0, 0);
    private PduRect headerRect = new PduRect(0, 0, 0, 0);
    private PduRect contentRect = new PduRect(0, 0, 0, 0);
    private PduRect footerRect = new PduRect(0, 0, 0, 0);
    private PduRect toolbarRect = new PduRect(0, 0, 0, 0);
    private PduRect tableRect = new PduRect(0, 0, 0, 0);
    // FLOW: the 3D camera, and the pane it lives in so drag and scroll can hit test against it.
    private final com.createstats.gui.pdu.PduScene3D.Camera flowCamera =
            new com.createstats.gui.pdu.PduScene3D.Camera();
    private PduRect flowSceneRect = new PduRect(0, 0, 0, 0);
    private PduRect shedRect = new PduRect(0, 0, 0, 0);
    private int rowH = 16;
    private int headerH = 28;

    private View view = View.OVERVIEW;

    // [2]
    private List<BranchDevice> lightingCache = List.of();
    private Object lightingToken;
    private int lightingSize = -1;
    private static final Object EMPTY_TOKEN = new Object();

    // Inline confirmation: locking a Critical device off, single or in a bulk selection, arms for two
    // seconds and commits on a repeat, rather than a heavy modal.
    private BlockPos confirmLockOffPos;
    private long confirmLockOffUntil;
    private boolean confirmBulkLockOff;
    private long confirmBulkUntil;

    // A transient stack of confirmations, bottom right of the panel.
    private final PduToast toasts = new PduToast();
    /** Auto assign overwrites every manual tier, so it arms and commits on a repeat like the lock off. */
    private long confirmAssignUntil;

    // OVERVIEW: a client side rolling buffer sampled once per tick while the screen is open, so the
    // sparklines have data before the server history buffer of a later phase exists.
    private final PduRing supplyRing = new PduRing(60);
    private final PduRing demandRing = new PduRing(60);
    // Reused each frame so the tier band computation allocates nothing in the OVERVIEW render.
    private final int[] tierDraw = new int[FePriority.values().length];
    private final int[] tierServed = new int[FePriority.values().length];
    // Eased copies of the tier bands and the buffer fill and chart scale, so those bars glide on a
    // sync rather than stepping. Numbers still snap; only their bars move.
    private final float[] shownTierDraw = new float[FePriority.values().length];
    private final float[] shownTierServed = new float[FePriority.values().length];
    private final int[] bandDraw = new int[FePriority.values().length];
    private final int[] bandServed = new int[FePriority.values().length];
    private float shownFill = -1.0f;
    private float shownChartMax = -1.0f;
    private final List<int[]> lightingButtons = new ArrayList<>();
    // Reused each frame for the rail hover tooltip, so render() allocates nothing on the hot path.
    private final List<Component> railTips = new ArrayList<>(1);
    // The roll call rows' hit boxes and the active tier filter a click through sets.
    private final List<int[]> rollCallHits = new ArrayList<>();
    private FePriority tierFilter;
    private PduRect filterChipBounds = new PduRect(0, 0, 0, 0);
    // The one line status, derived once per frame and shared by the footer and the OVERVIEW card.
    private String statusText = "";
    private int statusColour = PduTheme.TEXT_DIM;

    // [3]
    private final PduHistory clientHistory = new PduHistory();
    private int historyRange;
    private boolean historySubscribed;
    private int historyKeepalive;
    private int scrubIndex = -1;
    private final PduRect[] rangeButtons = {
            new PduRect(0, 0, 0, 0), new PduRect(0, 0, 0, 0), new PduRect(0, 0, 0, 0)};

    private final List<int[]> bulkHits = new ArrayList<>();
    private PduRect modePillBounds = new PduRect(0, 0, 0, 0);
    private PduRect assignBounds = new PduRect(0, 0, 0, 0);

    public PowerDistributionUnitScreen(PowerDistributionUnitBE pdu) {
        super(Component.translatable("block.create_stats.power_distribution_unit"));
        this.pdu = pdu;
        final PduRail.Item[] items = new PduRail.Item[View.values().length];
        for (View v : View.values()) {
            items[v.ordinal()] = new PduRail.Item(v.icon, v.title);
        }
        this.rail = new PduRail(items);
        this.rail.setActive(view.ordinal());
    }

    @Override
    protected void init() {
        breakpoint = PduTheme.currentFor(width, height);
        final int pw = Math.min(breakpoint.idealW, width - 2 * PduTheme.BASE);
        final int ph = Math.min(breakpoint.idealH, height - 2 * PduTheme.BASE);
        this.panel = new PduRect((width - pw) / 2, (height - ph) / 2, pw, ph);
        this.rowH = breakpoint.rowH;
        this.headerH = breakpoint == PduTheme.Breakpoint.COMPACT ? 22 : 28;
        layout();
        table.setFocused(true);
    }

    private void layout() {
        final int rw = Math.min(RAIL_W, panel.w() / 4);
        this.railRect = new PduRect(panel.x(), panel.y(), rw, panel.h());
        final int cx = panel.x() + rw;
        final int cw = panel.w() - rw;
        this.headerRect = new PduRect(cx, panel.y(), cw, headerH);
        this.footerRect = new PduRect(cx, panel.bottom() - FOOTER_H, cw, FOOTER_H);
        this.contentRect = new PduRect(cx, panel.y() + headerH, cw,
                Math.max(0, panel.h() - headerH - FOOTER_H));
    }

    @Override
    public void tick() {
        super.tick();
        if (minecraft == null || minecraft.level == null || pdu.isRemoved()
                || minecraft.level.getBlockEntity(pdu.getBlockPos()) != pdu) {
            onClose();
            return;
        }
        // [4]
        supplyRing.push(pdu.mode() == PduMode.AUTO ? pdu.generationFePerTick() : pdu.measuredInputFePerTick());
        demandRing.push(pdu.branchDemandFePerTick());

        // keep the history subscription alive while watching, so the server timeout only fires once
        // we've actually stopped looking
        if (historySubscribed && view == View.HISTORY && ++historyKeepalive % 40 == 0) {
            sendHistoryRequest(PduHistoryRequestPacket.ACTION_KEEPALIVE);
        }
    }

    /** 1.21 blurs the world behind a Screen by default, which fogs an instrument panel. */
    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        expireTransients();

        final FeGridSolver.BranchResult branch = pdu.lastBranchResult();
        refreshStatus(branch);
        railTips.clear();

        g.fill(0, 0, width, height, 0xB8000000);
        g.fill(panel.x(), panel.y(), panel.right(), panel.bottom(), PduTheme.BG_DEEP);
        PduTheme.outline(g, panel.x(), panel.y(), panel.w(), panel.h(), PduTheme.STROKE);

        rail.draw(g, railRect, mouseX, mouseY, partialTick, railTips);
        drawHeader(g, headerRect, branch, mouseX, mouseY);
        drawContent(g, mouseX, mouseY, partialTick);
        drawFooter(g, footerRect);
        toasts.draw(g, font, panel, partialTick);

        // Tooltips last, over everything. A hovered device row explains itself, unless the inspector
        // is covering that row, in which case the inspector already shows more than a tooltip could.
        if (isTableView()) {
            final BranchDevice hovered = table.deviceAt(mouseX, mouseY);
            if (hovered != null && !drawer.covers(mouseX, mouseY)) {
                g.renderComponentTooltip(font, deviceTooltip(hovered), mouseX, mouseY);
                return;
            }
        }
        if (view == View.WIRING) {
            final BranchDevice hovered = deviceAtPos(diagram.nodeAt(mouseX, mouseY));
            if (hovered != null) {
                g.renderComponentTooltip(font, deviceTooltip(hovered), mouseX, mouseY);
                return;
            }
        }
        if (!railTips.isEmpty()) {
            g.renderComponentTooltip(font, railTips, mouseX, mouseY);
        }
    }

    private void expireTransients() {
        final long now = Util.getMillis();
        if (confirmLockOffPos != null && now > confirmLockOffUntil) {
            confirmLockOffPos = null;
        }
        if (confirmBulkLockOff && now > confirmBulkUntil) {
            confirmBulkLockOff = false;
        }
    }

    // header

    private void drawHeader(GuiGraphics g, PduRect r, FeGridSolver.BranchResult branch, int mouseX, int mouseY) {
        g.fill(r.x(), r.y(), r.right(), r.bottom(), PduTheme.BG_PANEL);
        g.fill(r.x(), r.bottom() - 1, r.right(), r.bottom(), PduTheme.STROKE);
        final int textY = r.y() + (r.h() - 8) / 2;
        final String title = breakpoint == PduTheme.Breakpoint.COMPACT
                ? "POWER DISTRIBUTION" : "POWER DISTRIBUTION UNIT";
        PduCard.drawHeading(g, font, title, r.x() + PduTheme.PAD, textY);

        final PduMode mode = pdu.mode();
        final int pillY = r.y() + (r.h() - PduChip.HEIGHT) / 2;
        final String modeLabel = mode.getDisplayName().toUpperCase(Locale.ROOT);
        final int modeW = PduChip.width(font, modeLabel);
        final int modeX = r.right() - PduTheme.PAD - modeW;
        PduChip.draw(g, font, modeX, pillY, modeLabel, modeColour(mode),
                modePillBounds.contains(mouseX, mouseY), false);
        modePillBounds = new PduRect(modeX, pillY, modeW, PduChip.HEIGHT);

        final boolean assignArmed = Util.getMillis() < confirmAssignUntil;
        final String assignLabel = assignArmed ? "SURE?" : "ASSIGN";
        final int assignW = PduChip.width(font, assignLabel);
        final int assignX = modeX - PduTheme.GAP - assignW;
        PduChip.draw(g, font, assignX, pillY, assignLabel, assignArmed ? PduTheme.CRIT : PduTheme.ACCENT,
                assignBounds.contains(mouseX, mouseY), false);
        assignBounds = new PduRect(assignX, pillY, assignW, PduChip.HEIGHT);

        final int total = branch.served() + branch.shed();
        final int pct = Math.round(pdu.fillFraction() * 100.0f);
        final String totals = String.format(Locale.ROOT, "%d/%d served   %d%% buffer",
                branch.served(), total, pct);
        final int totalsX = assignX - PduTheme.GAP - font.width(totals);
        if (totalsX > r.x() + PduTheme.PAD + font.width(title) + PduTheme.GAP) {
            g.drawString(font, totals, totalsX, textY,
                    branch.shed() > 0 ? PduTheme.WARN : PduTheme.TEXT_DIM, false);
        }
    }

    private static int modeColour(PduMode m) {
        return switch (m) {
            case AUTO -> PduTheme.OK;
            case MANUAL -> PduTheme.INFO;
            case OFF -> PduTheme.TEXT_DIM;
        };
    }

    // footer

    private void drawFooter(GuiGraphics g, PduRect r) {
        g.fill(r.x(), r.y(), r.right(), r.bottom(), PduTheme.BG_PANEL);
        g.fill(r.x(), r.y(), r.right(), r.y() + 1, PduTheme.STROKE);
        final int textY = r.y() + (r.h() - 8) / 2;

        final String status = trimToWidth(statusText, r.w() - 2 * PduTheme.PAD);
        g.drawString(font, status, r.x() + PduTheme.PAD, textY, statusColour, false);

        final String hints = breakpoint == PduTheme.Breakpoint.COMPACT
                ? "1-5  /  M  Esc"
                : "1-5 Views   / Search   Tab Focus   M Mode   Esc Close";
        final int hintsX = r.right() - PduTheme.PAD - font.width(hints);
        if (hintsX > r.x() + PduTheme.PAD + font.width(status) + PduTheme.GAP) {
            g.drawString(font, hints, hintsX, textY, PduTheme.TEXT_FAINT, false);
        }
    }

    // content

    private void drawContent(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        switch (view) {
            case OVERVIEW -> drawOverview(g, mouseX, mouseY, partialTick);
            case LOADS -> drawTableView(g, pdu.roster(), mouseX, mouseY, partialTick);
            case LIGHTING -> drawTableView(g, lightingRoster(), mouseX, mouseY, partialTick);
            case HISTORY -> drawHistory(g, mouseX, mouseY, partialTick);
            case WIRING -> drawWiring(g, mouseX, mouseY);
            case FLOW -> drawFlow(g, mouseX, mouseY, partialTick);
            case LEGS -> drawLegs(g, mouseX, mouseY, partialTick);
            default -> drawPlaceholder(g, contentRect, view);
        }
    }

    private void drawTableView(GuiGraphics g, List<BranchDevice> roster, int mouseX, int mouseY, float partialTick) {
        table.setData(roster, group(), sort(), sortReversed, search.text(), shedOnly, tierFilter);

        int top = contentRect.y();
        if (view == View.LIGHTING) {
            drawLightingHeader(g, new PduRect(contentRect.x(), top, contentRect.w(), LIGHTING_HEADER_H),
                    roster, mouseX, mouseY);
            top += LIGHTING_HEADER_H + 2;
        }
        this.toolbarRect = new PduRect(contentRect.x(), top, contentRect.w(), TOOLBAR_H);
        final int belowY = top + TOOLBAR_H;
        PduRect below = new PduRect(contentRect.x(), belowY, contentRect.w(), contentRect.bottom() - belowY);

        if (table.selectionSize() >= 2) {
            drawBulkBar(g, toolbarRect, mouseX, mouseY);
        } else {
            drawToolbar(g, toolbarRect, mouseX, mouseY);
        }

        // [5]
        if (roster.isEmpty()) {
            this.tableRect = below;
            drawTableEmptyState(g, below);
            drawer.setDevice(null);
            return;
        }
        if (pdu.mode() == PduMode.OFF) {
            below = drawOffBanner(g, below);
        }
        this.tableRect = below;
        table.draw(g, font, below, rowH, mouseX, mouseY, partialTick);
        drawer.setDevice(table.selectionSize() == 1 ? table.singleSelected() : null);
        drawer.draw(g, font, below, mouseX, mouseY, partialTick, confirmLockOffPos);
    }

    private void drawLightingHeader(GuiGraphics g, PduRect r, List<BranchDevice> lights, int mouseX, int mouseY) {
        lightingButtons.clear();
        PduTheme.panel(g, r.x(), r.y(), r.w(), r.h());
        int lit = 0;
        int totalDraw = 0;
        for (BranchDevice d : lights) {
            if (!d.shed()) {
                lit++;
            }
            totalDraw += d.draw();
        }
        final int textY = r.y() + (r.h() - 8) / 2;
        g.drawString(font, lit + " lit,  " + (lights.size() - lit) + " dark   " + fmt(totalDraw) + " FE/t",
                r.x() + PduTheme.PAD, textY, PduTheme.TEXT, false);

        // Master tier control on the right: five tier buttons that set every tierable light at once.
        int x = r.right() - PduTheme.PAD;
        final int by = r.y() + (r.h() - 12) / 2;
        for (int i = FePriority.values().length - 1; i >= 0; i--) {
            final FePriority tier = FePriority.values()[i];
            final int bw = 16;
            x -= bw;
            final boolean hov = mouseX >= x && mouseX < x + bw && mouseY >= by && mouseY < by + 12;
            g.fill(x, by, x + bw, by + 12, hov ? PduTheme.BG_PANEL : PduTheme.BG_DEEP);
            PduTheme.outline(g, x, by, bw, 12, PduTheme.STROKE);
            g.fill(x + 2, by + 2, x + bw - 2, by + 4, PduGlyphs.tierColour(tier));
            final String ch = tier.getDisplayName().substring(0, 1);
            g.drawString(font, ch, x + (bw - font.width(ch)) / 2, by + 4, PduTheme.TEXT, false);
            lightingButtons.add(new int[]{x, by, x + bw, by + 12, tier.ordinal()});
            x -= 2;
        }
        final String setAll = "Set all";
        x -= font.width(setAll) + 4;
        g.drawString(font, setAll, x, textY, PduTheme.TEXT_DIM, false);
    }

    private void drawTableEmptyState(GuiGraphics g, PduRect area) {
        final String line1;
        final String line2;
        if (view == View.LIGHTING) {
            line1 = "No lights on this branch";
            line2 = "Lamps and stage lights show here";
        } else if (!pdu.hasAnyOutput()) {
            line1 = "Nothing is wired to this panel";
            line2 = "Copper Wire from a stud to a machine, or cable on the back boss";
        } else {
            // Wiring but nothing on it, which is exactly the state you are in halfway through running
            // a leg. Counting the wires rather than the conduit is what makes that legible.
            line1 = "Branch found, " + pdu.wiresOnStuds()
                    + (pdu.wiresOnStuds() == 1 ? " wire on the studs" : " wires on the studs");
            line2 = "No machines on it yet";
        }
        g.drawString(font, line1, area.centerX() - font.width(line1) / 2, area.centerY() - 8, PduTheme.TEXT_DIM, false);
        g.drawString(font, line2, area.centerX() - font.width(line2) / 2, area.centerY() + 4, PduTheme.TEXT_FAINT, false);
    }

    private PduRect drawOffBanner(GuiGraphics g, PduRect area) {
        final int h = 14;
        g.fill(area.x(), area.y(), area.right(), area.y() + h, PduTheme.withAlpha(PduTheme.WARN, 0x22));
        PduTheme.outline(g, area.x(), area.y(), area.w(), h, PduTheme.withAlpha(PduTheme.WARN, 0x66));
        g.drawString(font, trimToWidth(
                "Distribution off. Tiers and locks are ignored; every device shares the branch evenly.",
                area.w() - 12), area.x() + 6, area.y() + 3, PduTheme.WARN, false);
        return new PduRect(area.x(), area.y() + h + 2, area.w(), area.h() - h - 2);
    }

    private void sendAllLighting(int tierOrdinal) {
        PacketDistributor.sendToServer(new PduSetPacket(pdu.getBlockPos(),
                PduSetPacket.ACTION_ALL_LIGHTING, tierOrdinal, List.of()));
        toast("Set all lighting to " + FePriority.byOrdinal(tierOrdinal).getDisplayName(), PduToast.Severity.OK);
    }

    private void drawToolbar(GuiGraphics g, PduRect r, int mouseX, int mouseY) {
        g.fill(r.x(), r.y(), r.right(), r.bottom(), PduTheme.BG_DEEP);
        final int cy = r.y() + (r.h() - 14) / 2;
        int x = r.right() - PduTheme.PAD;

        final String shedLabel = "Shed";
        final int shedW = font.width(shedLabel) + 12;
        x -= shedW;
        final int shedX = x;
        final boolean shedHover = mouseX >= shedX && mouseX < shedX + shedW && mouseY >= cy && mouseY < cy + 14;
        g.fill(shedX, cy, shedX + shedW, cy + 14,
                shedOnly ? PduTheme.BG_RAISED : shedHover ? PduTheme.BG_PANEL : PduTheme.BG_DEEP);
        PduTheme.outline(g, shedX, cy, shedW, 14, focus == Focus.SHED ? PduTheme.STROKE_HOT : PduTheme.STROKE);
        g.drawString(font, shedLabel, shedX + 6, cy + 3, shedOnly ? PduTheme.WARN : PduTheme.TEXT_DIM, false);
        shedRect = new PduRect(shedX, cy, shedW, 14);
        x -= PduTheme.GAP;

        // Sort direction glyph, so the reverse state reads without a second control.
        x -= 8;
        drawTriangle(g, x + 1, cy + 5, !sortReversed, PduTheme.TEXT_DIM);
        x -= 2;

        final int sortW = sortSeg.width(font);
        x -= sortW;
        sortSeg.setFocused(focus == Focus.SORT);
        sortSeg.draw(g, font, x, cy, 14, mouseX, mouseY);
        x -= PduTheme.GAP;

        final int groupW = groupSeg.width(font);
        x -= groupW;
        groupSeg.setFocused(focus == Focus.GROUP);
        groupSeg.draw(g, font, x, cy, 14, mouseX, mouseY);
        x -= PduTheme.GAP;

        // Tier filter chip, when a roll call click through is active, at the far left; the search
        // fills the space after it and its clear cross removes the filter.
        int searchLeft = r.x() + PduTheme.PAD;
        if (tierFilter != null) {
            final String chip = tierFilter.getDisplayName() + "  x";
            final int chipW = font.width(chip) + 10;
            final boolean chipHover = mouseX >= searchLeft && mouseX < searchLeft + chipW
                    && mouseY >= cy && mouseY < cy + 14;
            g.fill(searchLeft, cy, searchLeft + chipW, cy + 14, chipHover ? PduTheme.BG_RAISED : PduTheme.BG_PANEL);
            PduTheme.outline(g, searchLeft, cy, chipW, 14, PduGlyphs.tierColour(tierFilter));
            g.drawString(font, chip, searchLeft + 5, cy + 3, PduGlyphs.tierColour(tierFilter), false);
            filterChipBounds = new PduRect(searchLeft, cy, chipW, 14);
            searchLeft += chipW + PduTheme.GAP;
        } else {
            filterChipBounds = new PduRect(0, 0, 0, 0);
        }
        final int searchW = Math.max(56, x - searchLeft);
        search.setFocused(focus == Focus.SEARCH);
        search.draw(g, font, searchLeft, cy, searchW, mouseX, mouseY);
    }

    private void drawBulkBar(GuiGraphics g, PduRect r, int mouseX, int mouseY) {
        bulkHits.clear();
        g.fill(r.x(), r.y(), r.right(), r.bottom(), PduTheme.BG_RAISED);
        PduTheme.outline(g, r.x(), r.y(), r.w(), r.h(), PduTheme.ACCENT);
        final int cy = r.y() + (r.h() - 14) / 2;
        final String label = table.selectionSize() + " selected";
        g.drawString(font, label, r.x() + PduTheme.PAD, cy + 3, PduTheme.TEXT, false);
        int x = r.x() + PduTheme.PAD + font.width(label) + 10;

        for (FePriority tier : FePriority.values()) {
            final int bw = 16;
            final boolean hov = mouseX >= x && mouseX < x + bw && mouseY >= cy && mouseY < cy + 14;
            g.fill(x, cy, x + bw, cy + 14, hov ? PduTheme.BG_PANEL : PduTheme.BG_DEEP);
            PduTheme.outline(g, x, cy, bw, 14, PduTheme.STROKE);
            g.fill(x + 2, cy + 2, x + bw - 2, cy + 4, com.createstats.gui.pdu.PduGlyphs.tierColour(tier));
            final String ch = tier.getDisplayName().substring(0, 1);
            g.drawString(font, ch, x + (bw - font.width(ch)) / 2, cy + 5, PduTheme.TEXT, false);
            bulkHits.add(new int[]{x, cy, x + bw, cy + 14, tier.ordinal()});
            x += bw + 2;
        }
        x += 6;
        x = bulkButton(g, x, cy, confirmBulkLockOff ? "Sure?" : "Lock off", mouseX, mouseY, 100, confirmBulkLockOff);
        x = bulkButton(g, x, cy, "Unlock", mouseX, mouseY, 101, false);
        bulkButton(g, x, cy, "Clear", mouseX, mouseY, 102, false);
    }

    private int bulkButton(GuiGraphics g, int x, int cy, String label, int mouseX, int mouseY, int code, boolean arming) {
        final int bw = font.width(label) + 10;
        final boolean hov = mouseX >= x && mouseX < x + bw && mouseY >= cy && mouseY < cy + 14;
        g.fill(x, cy, x + bw, cy + 14, hov ? PduTheme.BG_PANEL : PduTheme.BG_DEEP);
        PduTheme.outline(g, x, cy, bw, 14, arming ? PduTheme.CRIT : PduTheme.STROKE);
        g.drawString(font, label, x + 5, cy + 3, arming ? PduTheme.CRIT : PduTheme.TEXT, false);
        bulkHits.add(new int[]{x, cy, x + bw, cy + 14, code});
        return x + bw + 4;
    }

    private void drawTriangle(GuiGraphics g, int x, int y, boolean up, int colour) {
        if (up) {
            g.fill(x + 2, y, x + 3, y + 1, colour);
            g.fill(x + 1, y + 1, x + 4, y + 2, colour);
            g.fill(x, y + 2, x + 5, y + 3, colour);
        } else {
            g.fill(x, y, x + 5, y + 1, colour);
            g.fill(x + 1, y + 1, x + 4, y + 2, colour);
            g.fill(x + 2, y + 2, x + 3, y + 3, colour);
        }
    }

    // OVERVIEW

    private void drawOverview(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        rollCallHits.clear();
        computeTierBands();
        final PduRect area = contentRect.inset(PduTheme.GAP);
        final int gap = PduTheme.GAP;
        final boolean isAuto = pdu.mode() == PduMode.AUTO;
        final boolean compact = breakpoint == PduTheme.Breakpoint.COMPACT;
        final FeGridSolver.BranchResult branch = pdu.lastBranchResult();

        final int cardH = compact ? 52 : 62;
        final int cardW = (area.w() - 2 * gap) / 3;
        final PduRect supplyCard = new PduRect(area.x(), area.y(), cardW, cardH);
        final PduRect demandCard = new PduRect(area.x() + cardW + gap, area.y(), cardW, cardH);
        final PduRect bufferCard = new PduRect(demandCard.right() + gap, area.y(),
                area.right() - (demandCard.right() + gap), cardH);

        final int measured = pdu.measuredInputFePerTick();
        final int generation = pdu.generationFePerTick();
        // [6]
        final boolean dark = pdu.storedEnergy() <= 0 && measured <= 0;
        final int supplyValue = isAuto ? generation : measured;
        final String supplySub = isAuto
                ? PduStatusLine.formatSupplySubtitle(measured, pdu.fillFraction()) : "measured intake";
        PduStatCard.draw(g, font, supplyCard, "Supply", fmt(supplyValue), "FE/t", supplySub, supplyRing,
                dark ? PduTheme.CRIT : PduTheme.INFO);

        final int total = branch.served() + branch.shed();
        final int demand = isAuto ? pdu.branchDemandFePerTick() : rosterTotalDraw();
        final String demandSub = branch.served() + " of " + total + " devices";
        PduStatCard.draw(g, font, demandCard, "Demand", fmt(demand), "FE/t", demandSub,
                isAuto ? demandRing : null, PduTheme.TEXT);

        drawBufferCard(g, bufferCard, isAuto, partialTick);

        final int balanceH = compact ? 38 : 44;
        final PduRect balance = new PduRect(area.x(), supplyCard.bottom() + gap, area.w(), balanceH);
        // Ease the bands toward their real per tier values so they glide on a sync rather than stepping.
        for (int i = 0; i < tierDraw.length; i++) {
            shownTierDraw[i] = Ease.ease(shownTierDraw[i], tierDraw[i], 0.18f, partialTick);
            shownTierServed[i] = Ease.ease(shownTierServed[i], tierServed[i], 0.18f, partialTick);
            bandDraw[i] = Math.round(shownTierDraw[i]);
            bandServed[i] = Math.round(shownTierServed[i]);
        }
        PduBalanceBar.draw(g, font, balance, bandDraw, bandServed, pdu.roster().size());

        final int row3Y = balance.bottom() + gap;
        final int row3H = Math.max(30, area.bottom() - row3Y);
        final int statusW = Math.round((area.w() - gap) * 0.56f);
        final PduRect statusCard = new PduRect(area.x(), row3Y, statusW, row3H);
        final PduRect rollCard = new PduRect(area.x() + statusW + gap, row3Y,
                area.right() - (area.x() + statusW + gap), row3H);
        drawStatusCard(g, statusCard);
        drawRollCall(g, rollCard, mouseX, mouseY);
    }

    /** [7] */
    private void drawBufferCard(GuiGraphics g, PduRect bounds, boolean isAuto, float partialTick) {
        final PduRect c = PduCard.draw(g, font, bounds, "Buffer");
        final float fill = pdu.fillFraction();
        shownFill = easeFrom(shownFill, fill, partialTick);
        final int pct = Math.round(fill * 100.0f);
        final int fillColour = fill < pdu.reserveFloorFraction() ? PduTheme.WARN
                : fill < 0.15f ? PduTheme.CRIT : PduTheme.INFO;
        final Component pctComp = Component.literal(pct + "%").withStyle(ChatFormatting.BOLD);
        g.drawString(font, pctComp, c.x(), c.y(), PduTheme.TEXT, false);
        final String cap = fmt(pdu.storedEnergy()) + " / " + fmt(pdu.capacity()) + " FE";
        g.drawString(font, trimToWidth(cap, c.w() - font.width(pctComp) - 6),
                c.x() + font.width(pctComp) + 6, c.y(), PduTheme.TEXT_DIM, false);
        PduGauge.drawHorizontal(g, c.x(), c.y() + 13, c.w(), 9, shownFill, pdu.reserveFloorFraction(), fillColour);
        final String sub = isAuto
                ? "Reserve " + PduStatusLine.formatEndurance(pdu.autoEnduranceTicks(), pdu.isSurplus(), fill)
                : "Floor at " + Math.round(pdu.reserveFloorFraction() * 100) + "%";
        g.drawString(font, trimToWidth(sub, c.w()), c.x(), c.bottom() - 8, PduTheme.TEXT_DIM, false);
    }

    private void drawStatusCard(GuiGraphics g, PduRect bounds) {
        final PduRect c = PduCard.draw(g, font, bounds, "Status");
        g.drawWordWrap(font, Component.literal(statusText), c.x(), c.y(), c.w(), statusColour);
    }

    private void drawRollCall(GuiGraphics g, PduRect bounds, int mouseX, int mouseY) {
        final PduRect c = PduCard.draw(g, font, bounds, "Tier roll call");
        final PowerDistributionUnitBE.TierRollCall roll = pdu.tierRollCall();
        final int rh = Math.max(9, c.h() / FePriority.values().length);
        for (FePriority tier : FePriority.values()) {
            final int i = tier.ordinal();
            final int y = c.y() + i * rh;
            final boolean hover = mouseX >= c.x() && mouseX < c.right() && mouseY >= y && mouseY < y + rh;
            if (hover) {
                g.fill(c.x() - 2, y, c.right() + 2, y + rh - 1, PduTheme.BG_RAISED);
            }
            final int served = roll.served()[i];
            final int totalCount = roll.total()[i];
            final int tierColour = PduGlyphs.tierColour(tier);
            g.fill(c.x(), y + 2, c.x() + 3, y + rh - 3, tierColour);
            g.drawString(font, tier.getDisplayName(), c.x() + 7, y + (rh - 8) / 2,
                    totalCount == 0 ? PduTheme.TEXT_FAINT : PduTheme.TEXT, false);
            final String rc = served + "/" + totalCount + "   " + fmt(tierDraw[i]);
            g.drawString(font, rc, c.right() - font.width(rc), y + (rh - 8) / 2,
                    served < totalCount ? PduTheme.WARN : PduTheme.TEXT_DIM, false);
            rollCallHits.add(new int[]{c.x(), y, c.right(), y + rh, i});
        }
    }

    private void computeTierBands() {
        java.util.Arrays.fill(tierDraw, 0);
        java.util.Arrays.fill(tierServed, 0);
        for (BranchDevice d : pdu.roster()) {
            if (d.lock() == FeLock.FORCE_OFF) {
                continue;
            }
            final int t = d.priority().ordinal();
            tierDraw[t] += d.draw();
            if (!d.shed()) {
                tierServed[t] += d.draw();
            }
        }
    }

    private int rosterTotalDraw() {
        int t = 0;
        for (int d : tierDraw) {
            t += d;
        }
        return t;
    }

    private void refreshStatus(FeGridSolver.BranchResult branch) {
        final PduMode mode = pdu.mode();
        final boolean isAuto = mode == PduMode.AUTO;
        final boolean hasBranch = !pdu.roster().isEmpty();
        final boolean dark = pdu.storedEnergy() <= 0 && pdu.measuredInputFePerTick() <= 0;
        final int shed = branch.shed();
        // [8]
        final int spare = isAuto ? Math.max(0, pdu.generationFePerTick() - pdu.branchDemandFePerTick()) : 0;
        final boolean surplus = isAuto && pdu.isSurplus();
        final boolean throughputLimited = isAuto && pdu.isThroughputLimited();
        final int endurance = isAuto ? pdu.autoEnduranceTicks() : Integer.MAX_VALUE;
        final int floorPct = Math.round(pdu.reserveFloorFraction() * 100.0f);
        statusText = PduStatusLine.derive(mode, hasBranch, dark, surplus, throughputLimited, shed,
                spare, endurance, pdu.fillFraction(), floorPct);
        statusColour = !hasBranch || dark ? PduTheme.CRIT : shed > 0 ? PduTheme.WARN : PduTheme.OK;
    }

    private static String fmt(int v) {
        return String.format(Locale.ROOT, "%,d", v);
    }

    /** Eases a shown value toward its target, snapping the first frame so it does not sweep up from zero. */
    private static float easeFrom(float shown, float target, float partialTick) {
        return shown < 0.0f ? target : Ease.ease(shown, target, 0.18f, partialTick);
    }

    private String trimToWidth(String s, int maxW) {
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

    // HISTORY

    private void drawHistory(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        final PduRect area = contentRect.inset(PduTheme.GAP);
        final int legendH = 12;
        final PduRect chartBounds = new PduRect(area.x(), area.y() + legendH + 2, area.w(),
                Math.max(20, area.h() - legendH - 2));

        // [9]
        scrubIndex = PduChart.sampleAt(chartBounds, clientHistory, mouseX, mouseY);
        final int shown = scrubIndex >= 0 ? scrubIndex : clientHistory.count() - 1;
        final int supplyVal = shown >= 0 ? clientHistory.supplyAt(shown) : 0;
        final int demandVal = shown >= 0 ? clientHistory.demandAt(shown) : 0;

        int lx = area.x();
        g.fill(lx, area.y() + 2, lx + 8, area.y() + 8, PduTheme.withAlpha(PduTheme.OK, 0xC0));
        final String supplyLabel = "Supply " + fmt(supplyVal);
        g.drawString(font, supplyLabel, lx + 11, area.y() + 2, PduTheme.TEXT, false);
        lx += 11 + font.width(supplyLabel) + 12;
        g.fill(lx, area.y() + 3, lx + 8, area.y() + 5, PduTheme.WARN);
        final String demandLabel = "Demand " + fmt(demandVal);
        g.drawString(font, demandLabel, lx + 11, area.y() + 2, PduTheme.TEXT, false);
        if (scrubIndex >= 0) {
            g.drawString(font, "scrubbing", lx + 11 + font.width(demandLabel) + 10, area.y() + 2,
                    PduTheme.TEXT_FAINT, false);
        }

        final String[] labels = {"1m", "5m", "15m"};
        int bx = area.right();
        for (int i = labels.length - 1; i >= 0; i--) {
            final int bw = font.width(labels[i]) + 10;
            bx -= bw;
            rangeButtons[i] = new PduRect(bx, area.y(), bw, 12);
            final boolean active = i == historyRange;
            final boolean hover = rangeButtons[i].contains(mouseX, mouseY);
            g.fill(bx, area.y(), bx + bw, area.y() + 12,
                    active ? PduTheme.BG_RAISED : hover ? PduTheme.BG_PANEL : PduTheme.BG_DEEP);
            PduTheme.outline(g, bx, area.y(), bw, 12, active ? PduTheme.ACCENT : PduTheme.STROKE);
            g.drawString(font, labels[i], bx + 5, area.y() + 2, active ? PduTheme.TEXT : PduTheme.TEXT_DIM, false);
            bx -= 4;
        }

        int realMax = 1;
        for (int i = 0; i < clientHistory.count(); i++) {
            realMax = Math.max(realMax, Math.max(clientHistory.supplyAt(i), clientHistory.demandAt(i)));
        }
        // Ease the vertical scale so a new peak grows the axis smoothly instead of snapping every sync.
        shownChartMax = easeFrom(shownChartMax, realMax, partialTick);
        PduChart.draw(g, font, chartBounds, clientHistory, scrubIndex, Math.max(1, Math.round(shownChartMax)));
    }

    /** Receives a full buffer or a delta, applied only if it is for this block and the watched range. */
    public void receiveHistory(BlockPos pos, int range, boolean full, short[] supply, short[] demand, byte[] shed) {
        if (!pos.equals(pdu.getBlockPos()) || range != historyRange) {
            return;
        }
        if (full) {
            clientHistory.replace(supply, demand, shed, supply.length);
        } else if (supply.length > 0) {
            clientHistory.pushRaw(supply[0], demand[0], shed[0]);
        }
    }

    private void subscribeHistory() {
        historySubscribed = true;
        clientHistory.clear();
        sendHistoryRequest(PduHistoryRequestPacket.ACTION_SUBSCRIBE);
    }

    private void closeHistory() {
        if (historySubscribed) {
            sendHistoryRequest(PduHistoryRequestPacket.ACTION_CLOSE);
            historySubscribed = false;
        }
    }

    private void sendHistoryRequest(int action) {
        PacketDistributor.sendToServer(new PduHistoryRequestPacket(pdu.getBlockPos(), historyRange, action));
    }

    // WIRING

    private void drawWiring(GuiGraphics g, int mouseX, int mouseY) {
        final PduRect area = contentRect.inset(PduTheme.GAP);
        final int cardH = 44;
        final PduRect diagramBounds = new PduRect(area.x(), area.y(), area.w(),
                Math.max(40, area.h() - cardH - PduTheme.GAP));
        final PduRect statsCard = new PduRect(area.x(), diagramBounds.bottom() + PduTheme.GAP, area.w(), cardH);

        // A pos to device map for state colouring, small and only built while the WIRING view is open.
        final Map<BlockPos, BranchDevice> byPos = new HashMap<>();
        for (BranchDevice d : pdu.roster()) {
            byPos.put(d.pos(), d);
        }
        diagram.draw(g, font, diagramBounds, pdu.topology(), byPos, table::isSelected,
                pdu.topologyTruncated(), mouseX, mouseY);
        drawWiringStats(g, statsCard);
    }

    private void drawWiringStats(GuiGraphics g, PduRect bounds) {
        final PduRect c = PduCard.draw(g, font, bounds, "Wiring");
        final PowerDistributionUnitBE.BranchWiring w = pdu.wiring();
        // [10]
        final int wires = pdu.wiresOnStuds();
        g.drawString(font, trimToWidth(wires + (wires == 1 ? " wire, " : " wires, ")
                        + w.segments() + (w.segments() == 1 ? " conductor" : " conductors"), c.w()),
                c.x(), c.y(), PduTheme.TEXT, false);
        g.drawString(font, trimToWidth(fmt(w.buffered()) + " / " + fmt(w.capacity()) + " FE buffered", c.w()),
                c.x(), c.y() + 11, PduTheme.TEXT_DIM, false);
        final boolean limited = pdu.isThroughputLimited();
        final String ceiling = "Ceiling " + fmt(pdu.branchCeilingFePerTick()) + " FE/t"
                + (limited ? "  at the limit" : "");
        g.drawString(font, trimToWidth(ceiling, c.w()), c.x(), c.bottom() - 8,
                limited ? PduTheme.CRIT : PduTheme.TEXT_DIM, false);
    }

    // LEGS

    /** Breaker hit boxes, rebuilt each frame: {x0, y0, x1, y1, leg}. */
    private final java.util.List<int[]> legHits = new ArrayList<>();

    /** [11] */
    private void drawLegs(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        final PduRect area = contentRect.inset(PduTheme.GAP);
        legHits.clear();
        final int rows = PowerDistributionUnitBE.LEGS;
        // [12]
        final int rowH = Math.max(16, Math.min(40, (area.h() - 2) / rows));
        int y = area.y();
        for (int leg = 0; leg < rows; leg++) {
            drawLegRow(g, new PduRect(area.x(), y, area.w(), rowH - 2), leg, mouseX, mouseY, partialTick);
            y += rowH;
        }
    }

    private void drawLegRow(GuiGraphics g, PduRect r, int leg, int mouseX, int mouseY, float partialTick) {
        final boolean wired = pdu.isLegWired(leg);
        final int rating = pdu.legRating(leg);
        final int demand = pdu.legShownDemand(leg);
        final int devices = pdu.legDeviceCount(leg);
        final PduLegRow.State state = PduLegRow.stateOf(
                wired, pdu.isLegClosed(leg), pdu.isLegTripped(leg), pdu.isLegOverCapacity(leg));
        final int colour = PduLegRow.colour(leg);

        PduTheme.panel(g, r.x(), r.y(), r.w(), r.h());
        // [13]
        g.fill(r.x(), r.y(), r.x() + 3, r.bottom(),
                wired ? colour : PduTheme.withAlpha(colour, 0x40));

        final int textX = r.x() + 10;
        final int nameW = 46;
        g.drawString(font, PduLegRow.name(leg), textX, r.y() + 4,
                wired ? PduTheme.TEXT : PduTheme.TEXT_FAINT, false);

        // State word, coloured only as a second channel: the word itself already says it.
        final int stateColour = state.isAlarm() ? PduTheme.CRIT
                : state == PduLegRow.State.UNWIRED ? PduTheme.TEXT_FAINT
                : state == PduLegRow.State.OPEN ? PduTheme.WARN : PduTheme.OK;
        g.drawString(font, state.label(), textX + nameW, r.y() + 4, stateColour, false);

        if (r.h() >= 24) {
            g.drawString(font, trimToWidth(
                            PduLegRow.detail(state, demand, rating, devices, PduLegRow.isBack(leg)),
                            r.w() - 96),
                    textX, r.y() + 15, PduTheme.TEXT_DIM, false);
        }

        // Load bar, only where there is a conductor to be a fraction of.
        if (wired && r.h() >= 30) {
            final float target = PduLegRow.loadFraction(demand, rating);
            shownLegLoad[leg] = Ease.ease(shownLegLoad[leg], target, 0.18f, partialTick);
            final int barY = r.bottom() - 7;
            final int barW = r.w() - 20 - 44;
            g.fill(textX, barY, textX + barW, barY + 4, PduTheme.BG_DEEP);
            final int fill = Math.round(barW * shownLegLoad[leg]);
            g.fill(textX, barY, textX + fill, barY + 4,
                    state.isAlarm() ? PduTheme.CRIT : colour);
        }

        // The breaker. Always the opposite of what the leg is doing, like a real one.
        if (wired || state != PduLegRow.State.UNWIRED) {
            final String label = PduLegRow.buttonLabel(state);
            final int bw = 42;
            final int bx = r.right() - bw - 5;
            final int by = r.y() + (r.h() - 14) / 2;
            final boolean hot = mouseX >= bx && mouseX <= bx + bw && mouseY >= by && mouseY <= by + 14;
            g.fill(bx, by, bx + bw, by + 14, hot ? PduTheme.BG_RAISED : PduTheme.BG_DEEP);
            PduTheme.outline(g, bx, by, bw, 14, hot ? PduTheme.STROKE_HOT : PduTheme.STROKE);
            g.drawString(font, label, bx + (bw - font.width(label)) / 2, by + 3,
                    state == PduLegRow.State.TRIPPED ? PduTheme.CRIT : PduTheme.TEXT, false);
            legHits.add(new int[]{bx, by, bx + bw, by + 14, leg});
        }
    }

    /** Eased load bars, so a leg filling up glides rather than stepping on each sync. */
    private final float[] shownLegLoad = new float[PowerDistributionUnitBE.LEGS];

    private boolean clickLegs(double mouseX, double mouseY) {
        for (int[] hit : legHits) {
            if (mouseX >= hit[0] && mouseX <= hit[2] && mouseY >= hit[1] && mouseY <= hit[3]) {
                final int leg = hit[4];
                final PduLegRow.State state = PduLegRow.stateOf(pdu.isLegWired(leg),
                        pdu.isLegClosed(leg), pdu.isLegTripped(leg), pdu.isLegOverCapacity(leg));
                sendSet(List.of(), com.createstats.network.PduSetPacket.ACTION_SET_BREAKER,
                        com.createstats.network.PduSetPacket.breakerValue(
                                leg, PduLegRow.nextClosedState(state)));
                toast(PduLegRow.name(leg) + " breaker "
                        + (PduLegRow.nextClosedState(state) ? "closed" : "opened"),
                        PduToast.Severity.INFO);
                return true;
            }
        }
        return false;
    }

    // FLOW
    /** [14] */
    private void drawFlow(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        final PduRect area = contentRect.inset(PduTheme.GAP);
        final boolean narrow = breakpoint == PduTheme.Breakpoint.COMPACT;
        final PduRect sceneBounds;
        final PduRect workingBounds;
        if (narrow) {
            // Two columns in 336 px leaves neither wide enough to read, so the working goes underneath.
            final int workingH = Math.max(64, Math.round(area.h() * 0.40f));
            sceneBounds = new PduRect(area.x(), area.y(), area.w(), area.h() - workingH - PduTheme.GAP);
            workingBounds = new PduRect(area.x(), sceneBounds.bottom() + PduTheme.GAP, area.w(), workingH);
        } else {
            final int workingW = Math.max(148, Math.round(area.w() * 0.36f));
            sceneBounds = new PduRect(area.x(), area.y(), area.w() - workingW - PduTheme.GAP, area.h());
            workingBounds = new PduRect(sceneBounds.right() + PduTheme.GAP, area.y(), workingW, area.h());
        }
        this.flowSceneRect = sceneBounds;
        drawFlowScene(g, sceneBounds, partialTick);
        drawFlowWorking(g, workingBounds);
    }

    private void drawFlowScene(GuiGraphics g, PduRect bounds, float partialTick) {
        PduTheme.panel(g, bounds.x(), bounds.y(), bounds.w(), bounds.h());
        final PduRect inner = bounds.inset(PduTheme.BASE);
        // One hint line, under the scene rather than over it, so nothing competes with the model.
        final PduRect viewport = new PduRect(inner.x(), inner.y(), inner.w(), inner.h() - 10);

        final List<com.createstats.gui.pdu.PduTopology.BranchNode> topology = pdu.topology();
        if (topology.isEmpty() || !pdu.hasAnyOutput() || minecraft == null || minecraft.level == null) {
            g.drawString(font, "Nothing is wired to this panel.", inner.x(),
                    inner.y() + inner.h() / 2 - 4, PduTheme.TEXT_FAINT, false);
            return;
        }
        final List<BlockPos> positions = new ArrayList<>(topology.size() + 1);
        for (com.createstats.gui.pdu.PduTopology.BranchNode n : topology) {
            positions.add(n.pos());
        }
        // [15]
        if (!positions.contains(pdu.getBlockPos())) {
            positions.add(pdu.getBlockPos());
        }
        final Map<BlockPos, BranchDevice> byPos = new HashMap<>(pdu.roster().size() * 2);
        for (BranchDevice d : pdu.roster()) {
            byPos.put(d.pos(), d);
        }
        com.createstats.gui.pdu.PduScene3D.render(g, viewport.x(), viewport.y(), viewport.w(),
                viewport.h(), minecraft.level, positions, byPos, pdu.getBlockPos(), flowCamera,
                minecraft.level.getGameTime(), partialTick);

        final String hint = flowCamera.userTookOver
                ? "Drag to turn, scroll to zoom, R to reset" : "Drag to turn, scroll to zoom";
        g.drawString(font, hint, inner.x(), inner.bottom() - 8, PduTheme.TEXT_FAINT, false);
        final int shedCount = pdu.lastBranchResult().shed();
        if (shedCount > 0) {
            final String tally = shedCount + " shed, marked red";
            g.drawString(font, tally, inner.right() - font.width(tally), inner.bottom() - 8,
                    PduTheme.CRIT, false);
        }
    }

    /** The derivation: every number the decision rested on, and the arithmetic between them. */
    private void drawFlowWorking(GuiGraphics g, PduRect bounds) {
        final PduRect c = PduCard.draw(g, font, bounds, "How this was decided");
        final FeGridSolver.BranchResult branch = pdu.lastBranchResult();
        final List<com.createstats.gui.pdu.PduDerivation.Row> rows =
                com.createstats.gui.pdu.PduDerivation.derive(new com.createstats.gui.pdu.PduDerivation.Inputs(
                        // [16]
                        pdu.mode(), pdu.hasAnyOutput(), pdu.generationFePerTick(),
                        pdu.measuredInputFePerTick(), pdu.branchCeilingFePerTick(), pdu.budgetFePerTick(),
                        pdu.branchDemandFePerTick(), pdu.servedDemandFePerTick(), branch.served(),
                        branch.shed(), pdu.storedEnergy(), pdu.capacity(), pdu.reserveFloorFraction(),
                        pdu.isSurplus(), pdu.isThroughputLimited()));

        // [17]
        final int available = (c.bottom() - c.y()) / 10;
        final List<com.createstats.gui.pdu.PduDerivation.Row> shown =
                com.createstats.gui.pdu.PduDerivation.lineCount(rows) <= available
                        ? rows : com.createstats.gui.pdu.PduDerivation.terse(rows);

        int y = c.y();
        for (com.createstats.gui.pdu.PduDerivation.Row row : shown) {
            if (y > c.bottom() - 8) {
                break;
            }
            y = drawDerivationRow(g, c, row, y);
        }
    }

    /** One line of the working. Returns the next free y. */
    private int drawDerivationRow(GuiGraphics g, PduRect c, com.createstats.gui.pdu.PduDerivation.Row row,
                                  int y) {
        switch (row.kind()) {
            case HEADING -> {
                y += 4;
                PduCard.drawHeading(g, font, row.label(), c.x(), y);
                g.fill(c.x(), y + 10, c.right(), y + 11, PduTheme.STROKE);
                return y + 13;
            }
            case VERDICT -> {
                y += 2;
                for (String line : wrap(row.label(), c.w())) {
                    g.drawString(font, line, c.x(), y, PduTheme.TEXT, false);
                    y += 9;
                }
                for (String line : wrap(row.note(), c.w())) {
                    g.drawString(font, line, c.x(), y, PduTheme.TEXT_DIM, false);
                    y += 9;
                }
                return y + 2;
            }
            default -> {
                final boolean term = row.kind() == com.createstats.gui.pdu.PduDerivation.Kind.TERM;
                final boolean result = row.kind() == com.createstats.gui.pdu.PduDerivation.Kind.RESULT;
                final int labelX = c.x() + (term ? 8 : 0);
                final int labelColour = result ? PduTheme.TEXT : term ? PduTheme.TEXT_FAINT : PduTheme.TEXT_DIM;
                final int valueColour = result ? PduTheme.INFO : PduTheme.TEXT;
                final int valueW = font.width(row.value());
                g.drawString(font, trimToWidth(row.label(), c.w() - valueW - 6), labelX, y, labelColour, false);
                g.drawString(font, row.value(), c.right() - valueW, y, valueColour, false);
                y += 9;
                if (!row.note().isEmpty() && !term) {
                    g.drawString(font, trimToWidth(row.note(), c.w()), c.x() + 8, y, PduTheme.TEXT_FAINT, false);
                    y += 9;
                }
                return y + 1;
            }
        }
    }

    /** Greedy word wrap to a pixel width, for the two prose lines in the working. */
    private List<String> wrap(String text, int width) {
        final List<String> out = new ArrayList<>(3);
        if (text == null || text.isEmpty()) {
            return out;
        }
        final StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            final String candidate = line.isEmpty() ? word : line + " " + word;
            if (font.width(candidate) > width && !line.isEmpty()) {
                out.add(line.toString());
                line.setLength(0);
                line.append(word);
            } else {
                line.setLength(0);
                line.append(candidate);
            }
        }
        if (!line.isEmpty()) {
            out.add(line.toString());
        }
        return out;
    }

    private BranchDevice deviceAtPos(BlockPos pos) {
        if (pos == null) {
            return null;
        }
        for (BranchDevice d : pdu.roster()) {
            if (d.pos().equals(pos)) {
                return d;
            }
        }
        return null;
    }

    private void drawPlaceholder(GuiGraphics g, PduRect area, View v) {
        final int cx = area.centerX();
        final int cy = area.centerY();
        PduIcons.draw(g, v.icon, cx - 8, cy - 18, PduTheme.TEXT_FAINT);
        final String title = v.title.toUpperCase(Locale.ROOT);
        PduCard.drawHeading(g, font, title, cx - (font.width(title) + title.length()) / 2, cy);
        final String sub = "Coming online in a later phase";
        g.drawString(font, sub, cx - font.width(sub) / 2, cy + 12, PduTheme.TEXT_FAINT, false);
    }

    private List<BranchDevice> lightingRoster() {
        final List<BranchDevice> roster = pdu.roster();
        final Object token = roster.isEmpty() ? EMPTY_TOKEN : roster.get(0);
        if (token != lightingToken || roster.size() != lightingSize) {
            final List<BranchDevice> out = new ArrayList<>();
            for (BranchDevice d : roster) {
                if (d.lighting()) {
                    out.add(d);
                }
            }
            lightingCache = out;
            lightingToken = token;
            lightingSize = roster.size();
        }
        return lightingCache;
    }

    private PduTableModel.Group group() {
        return PduTableModel.Group.values()[groupSeg.selected()];
    }

    private PduTableModel.Sort sort() {
        return PduTableModel.Sort.values()[sortSeg.selected()];
    }

    private List<Component> deviceTooltip(BranchDevice device) {
        final List<Component> lines = new ArrayList<>(5);
        lines.add(Component.literal(device.label()).withStyle(ChatFormatting.WHITE));
        lines.add(Component.literal(String.format(Locale.ROOT, "%s tier,  %,d FE/t",
                device.priority().getDisplayName(), device.draw())).withStyle(ChatFormatting.GRAY));
        final PduReason reason = device.reason();
        final ChatFormatting reasonColour = switch (reason) {
            case RUNNING -> ChatFormatting.GREEN;
            case LOCKED_ON -> ChatFormatting.AQUA;
            case LOCKED_OFF -> ChatFormatting.DARK_GRAY;
            case AWAITING_DWELL -> ChatFormatting.YELLOW;
            default -> ChatFormatting.GOLD;
        };
        lines.add(Component.literal(reason.description()).withStyle(reasonColour));
        lines.add(Component.literal(""));
        lines.add(Component.literal("Click to select    Left / Right sets tier    L locks")
                .withStyle(ChatFormatting.DARK_GRAY));
        return lines;
    }

    // table and drawer callbacks

    @Override
    public void requestSetTier(BranchDevice device, FePriority tier) {
        applyTier(device, tier);
    }

    @Override
    public void requestSetLock(BranchDevice device, FeLock lock) {
        applyLock(device, lock);
    }

    @Override
    public void setTier(BranchDevice device, FePriority tier) {
        applyTier(device, tier);
    }

    @Override
    public void setLock(BranchDevice device, FeLock lock) {
        applyLock(device, lock);
    }

    @Override
    public void locate(BranchDevice device) {
        if (minecraft != null) {
            final BlockPos p = device.pos();
            minecraft.keyboardHandler.setClipboard(p.getX() + " " + p.getY() + " " + p.getZ());
            toast("Copied " + device.label() + " position", PduToast.Severity.INFO);
        }
    }

    private void applyTier(BranchDevice device, FePriority tier) {
        sendSet(List.of(device.pos()), PduSetPacket.ACTION_PRIORITY, tier.ordinal());
    }

    /** [18] */
    private void applyLock(BranchDevice device, FeLock target) {
        if (target == FeLock.FORCE_OFF && device.priority() == FePriority.CRITICAL) {
            final long now = Util.getMillis();
            if (device.pos().equals(confirmLockOffPos) && now < confirmLockOffUntil) {
                confirmLockOffPos = null;
                sendSet(List.of(device.pos()), PduSetPacket.ACTION_LOCK, target.ordinal());
            } else {
                confirmLockOffPos = device.pos();
                confirmLockOffUntil = now + CONFIRM_MS;
                toast("Lock a Critical device off? Do it again to confirm", PduToast.Severity.WARN);
            }
            return;
        }
        confirmLockOffPos = null;
        sendSet(List.of(device.pos()), PduSetPacket.ACTION_LOCK, target.ordinal());
    }

    private void bulkSetTier(int tierOrdinal) {
        sendSet(positions(table.selectedDevices()), PduSetPacket.ACTION_PRIORITY, tierOrdinal);
    }

    private void bulkLockOff() {
        final List<BranchDevice> selected = table.selectedDevices();
        boolean anyCritical = false;
        for (BranchDevice d : selected) {
            if (d.priority() == FePriority.CRITICAL) {
                anyCritical = true;
                break;
            }
        }
        if (anyCritical) {
            final long now = Util.getMillis();
            if (confirmBulkLockOff && now < confirmBulkUntil) {
                confirmBulkLockOff = false;
                sendSet(positions(selected), PduSetPacket.ACTION_LOCK, FeLock.FORCE_OFF.ordinal());
            } else {
                confirmBulkLockOff = true;
                confirmBulkUntil = now + CONFIRM_MS;
                toast("Lock off a selection with a Critical device? Do it again to confirm", PduToast.Severity.WARN);
            }
            return;
        }
        sendSet(positions(selected), PduSetPacket.ACTION_LOCK, FeLock.FORCE_OFF.ordinal());
    }

    private void bulkUnlock() {
        sendSet(positions(table.selectedDevices()), PduSetPacket.ACTION_LOCK, FeLock.NONE.ordinal());
    }

    private static List<BlockPos> positions(List<BranchDevice> devices) {
        final List<BlockPos> out = new ArrayList<>(devices.size());
        for (BranchDevice d : devices) {
            out.add(d.pos());
        }
        return out;
    }

    private void sendSet(List<BlockPos> targets, int action, int value) {
        PacketDistributor.sendToServer(new PduSetPacket(pdu.getBlockPos(), action, value, targets));
    }

    private void toast(String message, PduToast.Severity severity) {
        toasts.push(message, severity);
    }

    /** Auto assign overwrites every manual tier, so it arms for two seconds and commits on a repeat. */
    private void requestAssign() {
        final long now = Util.getMillis();
        if (now < confirmAssignUntil) {
            confirmAssignUntil = 0;
            send(pdu.getBlockPos(), PduActionPacket.ACTION_AUTO);
            toast("Assigned default tiers to every device", PduToast.Severity.OK);
        } else {
            confirmAssignUntil = now + CONFIRM_MS;
            toast("Assign default tiers to every device? Do it again to confirm", PduToast.Severity.WARN);
        }
    }

    // input

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double dx, double dy) {
        if (isTableView() && table.mouseScrolled(mouseX, mouseY, dy)) {
            return true;
        }
        if (view == View.FLOW && flowSceneRect.contains((int) mouseX, (int) mouseY) && dy != 0) {
            flowCamera.zoomBy(dy);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, dx, dy);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        final int railItem = rail.itemAt(mouseX, mouseY);
        if (button == 0 && railItem >= 0) {
            setView(View.values()[railItem]);
            return true;
        }
        if (view == View.LEGS && button == 0 && clickLegs(mouseX, mouseY)) {
            return true;
        }
        if (view == View.FLOW && button == 0 && flowSceneRect.contains((int) mouseX, (int) mouseY)) {
            flowCamera.beginDrag(mouseX, mouseY);
            return true;
        }
        if (button == 0 && modePillBounds.contains(mouseX, mouseY)) {
            send(pdu.getBlockPos(), PduActionPacket.ACTION_CYCLE_MODE);
            return true;
        }
        if (button == 0 && assignBounds.contains(mouseX, mouseY)) {
            requestAssign();
            return true;
        }
        if (view == View.OVERVIEW && button == 0) {
            for (int[] hit : rollCallHits) {
                if (mouseX >= hit[0] && mouseX < hit[2] && mouseY >= hit[1] && mouseY < hit[3]) {
                    setTierFilter(FePriority.byOrdinal(hit[4]));
                    return true;
                }
            }
        }
        if (view == View.HISTORY && button == 0) {
            for (int i = 0; i < rangeButtons.length; i++) {
                if (rangeButtons[i].contains(mouseX, mouseY)) {
                    if (historyRange != i) {
                        historyRange = i;
                        subscribeHistory();
                    }
                    return true;
                }
            }
        }
        if (view == View.WIRING && button == 0) {
            final BlockPos node = diagram.nodeAt(mouseX, mouseY);
            if (node != null) {
                final BranchDevice device = deviceAtPos(node);
                if (device != null) {
                    table.selectOnly(device.pos());
                }
                return true;
            }
        }
        if (view == View.LIGHTING && button == 0) {
            for (int[] b : lightingButtons) {
                if (mouseX >= b[0] && mouseX < b[2] && mouseY >= b[1] && mouseY < b[3]) {
                    sendAllLighting(b[4]);
                    return true;
                }
            }
        }
        if (isTableView()) {
            // Drawer first, then the toolbar or bulk bar, then the table underneath.
            if (drawer.mouseClicked(mouseX, mouseY, button, this)) {
                return true;
            }
            if (table.selectionSize() >= 2) {
                if (bulkClicked(mouseX, mouseY, button)) {
                    return true;
                }
            } else if (toolbarClicked(mouseX, mouseY, button)) {
                return true;
            }
            final boolean shift = hasShiftDown();
            final boolean ctrl = hasControlDown();
            if (table.mouseClicked(mouseX, mouseY, button, shift, ctrl, this)) {
                focus = Focus.TABLE;
                updateFocusFlags();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean toolbarClicked(double mx, double my, int button) {
        if (button != 0 || !toolbarRect.contains(mx, my)) {
            return false;
        }
        if (filterChipBounds.contains(mx, my)) {
            tierFilter = null;
            return true;
        }
        if (search.clickedClear(mx, my)) {
            return true;
        }
        if (search.clicked(mx, my)) {
            setFocus(Focus.SEARCH);
            return true;
        }
        final int gi = groupSeg.segmentAt(mx, my);
        if (gi >= 0) {
            groupSeg.setSelected(gi);
            setFocus(Focus.GROUP);
            return true;
        }
        final int si = sortSeg.segmentAt(mx, my);
        if (si >= 0) {
            if (si == sortSeg.selected()) {
                sortReversed = !sortReversed;
            } else {
                sortSeg.setSelected(si);
                sortReversed = false;
            }
            setFocus(Focus.SORT);
            return true;
        }
        if (shedRect.contains(mx, my)) {
            shedOnly = !shedOnly;
            setFocus(Focus.SHED);
            return true;
        }
        return false;
    }

    private boolean bulkClicked(double mx, double my, int button) {
        if (button != 0) {
            return false;
        }
        for (int[] hit : bulkHits) {
            if (mx >= hit[0] && mx < hit[2] && my >= hit[1] && my < hit[3]) {
                final int code = hit[4];
                switch (code) {
                    case 100 -> bulkLockOff();
                    case 101 -> bulkUnlock();
                    case 102 -> table.clearSelection();
                    default -> bulkSetTier(code);
                }
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (isTableView() && table.mouseDragged(mouseX, mouseY)) {
            return true;
        }
        if (view == View.FLOW && flowCamera.dragging) {
            flowCamera.drag(mouseX, mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        table.mouseReleased();
        flowCamera.endDrag();
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            // Unwind: search, then the tier filter, then the selection, then close.
            if (!search.isEmpty()) {
                search.clear();
                return true;
            }
            if (tierFilter != null) {
                tierFilter = null;
                return true;
            }
            if (table.hasSelection()) {
                table.clearSelection();
                return true;
            }
            onClose();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_TAB) {
            cycleFocus(hasShiftDown());
            return true;
        }
        // A focused search box owns the keyboard, so digits and letters type instead of firing shortcuts.
        if (focus == Focus.SEARCH) {
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                setFocus(Focus.TABLE);
                return true;
            }
            search.keyPressed(keyCode);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_SLASH) {
            setFocus(Focus.SEARCH);
            return true;
        }
        // Bounded by the enum rather than a hardcoded key, so adding a view cannot leave its number
        // key dead or, worse, index past the end of the array.
        if (keyCode >= GLFW.GLFW_KEY_1
                && keyCode < GLFW.GLFW_KEY_1 + Math.min(9, View.values().length)) {
            setView(View.values()[keyCode - GLFW.GLFW_KEY_1]);
            return true;
        }
        // R resets the 3D camera and hands the scene back to its idle spin. Only on the Flow tab, so
        // the key stays free everywhere else.
        if (keyCode == GLFW.GLFW_KEY_R && view == View.FLOW) {
            flowCamera.reset();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_M) {
            send(pdu.getBlockPos(), PduActionPacket.ACTION_CYCLE_MODE);
            return true;
        }
        // [19]
        if (keyCode == GLFW.GLFW_KEY_A && hasControlDown() && focus == Focus.TABLE && isTableView()) {
            table.selectAllVisible();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_A) {
            requestAssign();
            return true;
        }
        if (focus == Focus.GROUP && horizontalStep(keyCode) != 0) {
            groupSeg.setSelected(Mth.clamp(groupSeg.selected() + horizontalStep(keyCode), 0, groupSeg.count() - 1));
            return true;
        }
        if (focus == Focus.SORT) {
            if (horizontalStep(keyCode) != 0) {
                sortSeg.setSelected(Mth.clamp(sortSeg.selected() + horizontalStep(keyCode), 0, sortSeg.count() - 1));
                sortReversed = false;
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_SPACE) {
                sortReversed = !sortReversed;
                return true;
            }
        }
        if (focus == Focus.SHED && (keyCode == GLFW.GLFW_KEY_SPACE || keyCode == GLFW.GLFW_KEY_ENTER)) {
            shedOnly = !shedOnly;
            return true;
        }
        // On the history view Left/Right walk the range, so the whole view is keyboard reachable.
        if (view == View.HISTORY && horizontalStep(keyCode) != 0) {
            final int next = Mth.clamp(historyRange + horizontalStep(keyCode), 0, 2);
            if (next != historyRange) {
                historyRange = next;
                subscribeHistory();
            }
            return true;
        }
        if (focus == Focus.TABLE && isTableView() && table.keyPressed(keyCode, this)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private static int horizontalStep(int keyCode) {
        if (keyCode == GLFW.GLFW_KEY_LEFT) {
            return -1;
        }
        if (keyCode == GLFW.GLFW_KEY_RIGHT) {
            return 1;
        }
        return 0;
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        if (focus == Focus.SEARCH && search.charTyped(c)) {
            return true;
        }
        return super.charTyped(c, modifiers);
    }

    private void cycleFocus(boolean backward) {
        final Focus[] order = Focus.values();
        setFocus(order[(focus.ordinal() + (backward ? order.length - 1 : 1)) % order.length]);
    }

    private void setFocus(Focus next) {
        this.focus = next;
        updateFocusFlags();
    }

    private void updateFocusFlags() {
        search.setFocused(focus == Focus.SEARCH);
        groupSeg.setFocused(focus == Focus.GROUP);
        sortSeg.setFocused(focus == Focus.SORT);
        table.setFocused(focus == Focus.TABLE);
    }

    private boolean isTableView() {
        return view == View.LOADS || view == View.LIGHTING;
    }

    private void setView(View next) {
        if (view != next) {
            if (view == View.HISTORY) {
                closeHistory();
            }
            view = next;
            rail.setActive(next.ordinal());
            if (isTableView()) {
                setFocus(Focus.TABLE);
            }
            if (next == View.HISTORY) {
                subscribeHistory();
            }
        }
    }

    /** A tier roll call click through: narrow LOADS to that tier and go there. */
    private void setTierFilter(FePriority tier) {
        tierFilter = tier;
        setView(View.LOADS);
        setFocus(Focus.TABLE);
    }

    private void send(BlockPos target, int action) {
        PacketDistributor.sendToServer(new PduActionPacket(pdu.getBlockPos(), target, action));
    }

    @Override
    public void onClose() {
        closeHistory();
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
