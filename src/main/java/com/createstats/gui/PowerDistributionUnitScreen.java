package com.createstats.gui;

import com.createstats.blockentity.PowerDistributionUnitBE;
import com.createstats.blockentity.PowerDistributionUnitBE.BranchDevice;
import com.createstats.gui.pdu.PduCard;
import com.createstats.gui.pdu.PduChart;
import com.createstats.gui.pdu.PduDayChart;
import com.createstats.gui.pdu.PduDayOutlook;
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

public class PowerDistributionUnitScreen extends Screen implements PduTable.Callback, PduDrawer.Callback,
        com.createstats.book.specimen.ManualSpecimen {
    private enum View {
        OVERVIEW(PduIcons.OVERVIEW, "Overview"),
        LOADS(PduIcons.LOADS, "Loads"),
        LIGHTING(PduIcons.LIGHTING, "Lighting"),
        WIRING(PduIcons.WIRING, "Wiring"),
        FLOW(PduIcons.FLOW, "Flow"),
        LEGS(PduIcons.LEGS, "Legs"),
        HISTORY(PduIcons.HISTORY, "History"),

        CHAIN(PduIcons.CHAIN, "In / Out");

        final String icon;
        final String title;

        View(String icon, String title) {
            this.icon = icon;
            this.title = title;
        }
    }

    private enum Focus {
        SEARCH, GROUP, SORT, SHED, TABLE
    }

    private static final int RAIL_W = 26;
    private static final int FOOTER_H = 16;

    private static final int TOOLBAR_H = 20;
    private static final int TOOLBAR_H_STACKED = 32;
    private static final int LIGHTING_HEADER_H = 24;
    private static final long CONFIRM_MS = 2000L;

    private final PowerDistributionUnitBE pdu;
    private final PduRail rail;
    private final PduTable table = new PduTable();
    private final PduDrawer drawer = new PduDrawer();
    private final PduDiagram diagram = new PduDiagram();
    private final PduSearchField search = new PduSearchField("Search devices");
    private final PduSegmented groupSeg = new PduSegmented("Tier", "Type", "Status", "Leg");

    private PduTableModel.Sort sortKey = PduTableModel.Sort.DRAW;
    private boolean sortReversed;
    private boolean shedOnly;

    private int legFilter = PduTableModel.Filter.ANY_LEG;
    private Focus focus = Focus.TABLE;

    private PduTheme.Breakpoint breakpoint = PduTheme.Breakpoint.NORMAL;
    private PduRect panel = new PduRect(0, 0, 0, 0);
    private PduRect railRect = new PduRect(0, 0, 0, 0);
    private PduRect headerRect = new PduRect(0, 0, 0, 0);
    private PduRect contentRect = new PduRect(0, 0, 0, 0);
    private PduRect footerRect = new PduRect(0, 0, 0, 0);
    private PduRect toolbarRect = new PduRect(0, 0, 0, 0);
    private PduRect tableRect = new PduRect(0, 0, 0, 0);

    private final com.createstats.gui.pdu.PduScene3D.Camera flowCamera =
            new com.createstats.gui.pdu.PduScene3D.Camera();
    private PduRect flowSceneRect = new PduRect(0, 0, 0, 0);
    private PduRect shedRect = new PduRect(0, 0, 0, 0);
    private int rowH = 16;
    private int headerH = 28;

    private View view = View.OVERVIEW;

    private List<BranchDevice> lightingCache = List.of();
    private Object lightingToken;
    private int lightingSize = -1;
    private static final Object EMPTY_TOKEN = new Object();

    private BlockPos confirmLockOffPos;
    private long confirmLockOffUntil;
    private boolean confirmBulkLockOff;
    private long confirmBulkUntil;

    private final PduToast toasts = new PduToast();

    private long confirmAssignUntil;

    private final PduRing supplyRing = new PduRing(60);
    private final PduRing demandRing = new PduRing(60);

    private final int[] tierDraw = new int[FePriority.values().length];
    private final int[] tierServed = new int[FePriority.values().length];

    private final float[] shownTierDraw = new float[FePriority.values().length];
    private final float[] shownTierServed = new float[FePriority.values().length];
    private final int[] bandDraw = new int[FePriority.values().length];
    private final int[] bandServed = new int[FePriority.values().length];
    private float shownFill = -1.0f;
    private float shownChartMax = -1.0f;
    private final List<int[]> lightingButtons = new ArrayList<>();

    private final List<Component> railTips = new ArrayList<>(1);

    private final List<int[]> rollCallHits = new ArrayList<>();
    private FePriority tierFilter;
    private PduRect filterChipBounds = new PduRect(0, 0, 0, 0);
    private PduRect legChipBounds = new PduRect(0, 0, 0, 0);

    private String statusText = "";
    private int statusColour = PduTheme.TEXT_DIM;

    private final PduHistory clientHistory = new PduHistory();
    private int historyRange;
    private boolean historySubscribed;
    private int historyKeepalive;
    private int scrubIndex = -1;
    private final PduRect[] rangeButtons = {
            new PduRect(0, 0, 0, 0), new PduRect(0, 0, 0, 0), new PduRect(0, 0, 0, 0),
            new PduRect(0, 0, 0, 0)};

    private static final int DAY_RANGE = 3;
    private int dayHoverStep = -1;

    private final List<int[]> bulkHits = new ArrayList<>();
    private PduRect modePillBounds = new PduRect(0, 0, 0, 0);
    private PduRect assignBounds = new PduRect(0, 0, 0, 0);

    private boolean specimen;

    private java.util.function.Supplier<com.createstats.book.specimen.SpecimenAnatomy> specimenAnatomy;

    private Map<String, PduRect> notes;

    private SpecimenScene specimenScene;

    public interface SpecimenScene {
        void draw(GuiGraphics g, int x, int y, int w, int h, List<BlockPos> positions,
                  Map<BlockPos, BranchDevice> devices, BlockPos board,
                  com.createstats.gui.pdu.PduScene3D.Camera camera);
    }

    public static PowerDistributionUnitScreen specimen(PowerDistributionUnitBE fixture, int view, int w, int h,
            @org.jetbrains.annotations.Nullable net.minecraft.client.Minecraft mc, net.minecraft.client.gui.Font font,
            @org.jetbrains.annotations.Nullable BlockPos selected, boolean selectAll,
            java.util.function.Supplier<com.createstats.book.specimen.SpecimenAnatomy> anatomy, int historyRange,
            @org.jetbrains.annotations.Nullable SpecimenScene scene) {
        final PowerDistributionUnitScreen s = new PowerDistributionUnitScreen(fixture);
        s.specimen = true;
        s.specimenAnatomy = anatomy;
        s.specimenScene = scene;
        s.historyRange = Mth.clamp(historyRange, 0, DAY_RANGE);
        s.minecraft = mc;
        s.font = font;
        s.width = w;
        s.height = h;

        s.breakpoint = PduTheme.currentFor(w + 4 * PduTheme.BASE, h + 4 * PduTheme.BASE);
        s.panel = new PduRect(0, 0, w, h);
        s.rowH = s.breakpoint.rowH;
        s.headerH = s.breakpoint == PduTheme.Breakpoint.COMPACT ? 22 : 28;
        s.layout();
        s.table.setFocused(true);
        s.setView(View.values()[Mth.clamp(view, 0, View.values().length - 1)]);
        if (selected != null) {
            s.table.selectOnly(selected);
        }
        if (selectAll && s.isTableView()) {
            s.table.setData(s.view == View.LIGHTING ? s.lightingRoster() : fixture.roster(), s.group(), s.sortKey,
                    s.sortReversed, s.filter());
            s.table.selectAllVisible();
        }

        for (int i = 0; i < 60; i++) {
            s.supplyRing.push(fixture.mode() == PduMode.AUTO
                    ? fixture.generationFePerTick() : fixture.measuredInputFePerTick());
            s.demandRing.push(fixture.branchDemandFePerTick());
        }

        if (s.historyRange != DAY_RANGE) {
            final int supply = fixture.mode() == PduMode.AUTO
                    ? fixture.generationFePerTick() : fixture.measuredInputFePerTick();
            int asked = 0;
            if (fixture.mode() == PduMode.AUTO) {
                asked = fixture.branchDemandFePerTick();
            } else {
                for (BranchDevice d : fixture.roster()) {
                    if (d.lock() != FeLock.FORCE_OFF && !d.isStorage()) {
                        asked += d.cost();
                    }
                }
            }
            final int demand = asked + fixture.exportDemandFePerTick() + fixture.upkeepFePerTick();
            for (int i = 0; i < s.clientHistory.capacity(); i++) {
                s.clientHistory.record(supply, demand, fixture.lastBranchResult().shed());
            }
        }
        return s;
    }

    public static List<String> viewTitles() {
        final List<String> out = new ArrayList<>(View.values().length);
        for (View v : View.values()) {
            out.add(v.title);
        }
        return out;
    }

    @Override
    public int specimenWidth() {
        return panel.w();
    }

    @Override
    public int specimenHeight() {
        return panel.h();
    }

    @Override
    public void renderSpecimen(GuiGraphics g, int mouseX, int mouseY) {
        Ease.still(() -> drawPanel(g, mouseX, mouseY, 1.0f));
    }

    @Override
    public com.createstats.book.specimen.SpecimenAnatomy anatomy() {
        return specimenAnatomy.get();
    }

    @Override
    public boolean clipsItsDrawing() {
        return true;
    }

    public Map<String, PduRect> measureSpecimen(GuiGraphics g) {
        notes = new java.util.LinkedHashMap<>();
        try {
            renderSpecimen(g, -10_000, -10_000);
            return specimenParts();
        } finally {
            notes = null;
        }
    }

    private void note(String id, PduRect r) {
        if (notes != null && r != null) {
            notes.put(id, r);
        }
    }

    public Map<String, PduRect> specimenParts() {
        final Map<String, PduRect> out = new java.util.LinkedHashMap<>();
        out.put("panel", panel);
        out.put("rail", railRect);
        for (int i = 0; i < View.values().length; i++) {
            out.put("rail." + i, rail.itemRect(i));
        }
        out.put("header", headerRect);
        out.put("mode", modePillBounds);
        out.put("assign", assignBounds);
        out.put("content", contentRect);
        out.put("footer", footerRect);
        for (int[] h : rollCallHits) {
            out.put("roll." + h[4], new PduRect(h[0], h[1], h[2] - h[0], h[3] - h[1]));
        }
        for (int[] h : lightingButtons) {
            out.put("lighting.tier." + h[4], new PduRect(h[0], h[1], h[2] - h[0], h[3] - h[1]));
        }
        if (isTableView() && table.selectionSize() >= 2) {
            for (int[] h : bulkHits) {
                final String name = switch (h[4]) {
                    case 100 -> "bulk.lockoff";
                    case 101 -> "bulk.auto";
                    case 102 -> "bulk.clear";
                    case 103 -> "bulk.lockon";
                    default -> "bulk.tier." + h[4];
                };
                out.put(name, new PduRect(h[0], h[1], h[2] - h[0], h[3] - h[1]));
            }
        }
        if (isTableView()) {
            out.put("toolbar", toolbarRect);
            if (table.selectionSize() < 2) {
                out.put("search", search.bounds());
                for (int i = 0; i < groupSeg.count(); i++) {
                    out.put("group." + i, groupSeg.segmentRect(i));
                }
                out.put("shed", shedRect);
            }
            out.put("table", table.bounds());
            final com.createstats.gui.pdu.PduLoadColumns cols = table.columns();
            final int headY = table.bounds().y();
            for (PduTableModel.Sort column : PduTableModel.Sort.values()) {
                if (column == PduTableModel.Sort.LEG && !cols.legShown()) {
                    continue;
                }
                final int[] box = cols.boundsOf(column);
                out.put("head." + column.name().toLowerCase(Locale.ROOT),
                        new PduRect(box[0], headY, box[1], PduTable.HEADER_H));
            }
            out.put("head.lock", new PduRect(cols.lockX(), headY, cols.lockW(), PduTable.HEADER_H));
            for (int i = 0; i < table.rowCount(); i++) {
                final BranchDevice d = table.deviceOnRow(i);
                out.put(d == null ? "grouprow." + i : "row." + d.pos().asLong(), table.rowRect(i));
            }
            if (drawer.bounds() != null) {
                out.put("drawer", drawer.bounds());
                for (FePriority tier : FePriority.values()) {
                    final PduRect r = drawer.tierRect(tier);
                    if (r != null) {
                        out.put("drawer.tier." + tier.ordinal(), r);
                    }
                }
                for (FeLock lock : FeLock.values()) {
                    final PduRect r = drawer.lockRect(lock);
                    if (r != null) {
                        out.put("drawer.lock." + lock.ordinal(), r);
                    }
                }
                if (drawer.locateRect() != null) {
                    out.put("drawer.locate", drawer.locateRect());
                }
            }
        }
        if (view == View.WIRING) {
            for (Map.Entry<BlockPos, PduRect> e : diagram.nodeRects().entrySet()) {
                out.put("node." + e.getKey().asLong(), e.getValue());
            }
        }
        if (view == View.LEGS) {
            putHits(out, "leg.row.", legRowHits);
            putHits(out, "leg.breaker.", legHits);
            putHits(out, "leg.rank.", legRankHits);
            putHits(out, "leg.board.", legBoardHits);
        }
        if (view == View.HISTORY) {
            for (int i = 0; i < rangeButtons.length; i++) {
                out.put("range." + i, rangeButtons[i]);
            }
        }
        if (view == View.CHAIN) {
            out.put("chain.toggle", chainToggle);
        }
        if (notes != null) {
            out.putAll(notes);
        }
        return out;
    }

    private static void putHits(Map<String, PduRect> out, String prefix, List<int[]> hits) {
        for (int[] h : hits) {
            out.put(prefix + h[4], new PduRect(h[0], h[1], h[2] - h[0], h[3] - h[1]));
        }
    }

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
        if (specimen) {
            return;
        }
        if (minecraft == null || minecraft.level == null || pdu.isRemoved()
                || minecraft.level.getBlockEntity(pdu.getBlockPos()) != pdu) {
            onClose();
            return;
        }

        supplyRing.push(pdu.mode() == PduMode.AUTO ? pdu.generationFePerTick() : pdu.measuredInputFePerTick());
        demandRing.push(pdu.branchDemandFePerTick());

        if (historySubscribed && view == View.HISTORY && ++historyKeepalive % 40 == 0) {
            sendHistoryRequest(PduHistoryRequestPacket.ACTION_KEEPALIVE);
        }
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        expireTransients();

        g.fill(0, 0, width, height, 0xB8000000);
        drawPanel(g, mouseX, mouseY, partialTick);
        toasts.draw(g, font, panel, partialTick);

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
        if (view == View.CHAIN && !inOutTips.isEmpty()) {
            g.renderComponentTooltip(font, inOutTips, mouseX, mouseY);
            return;
        }
        if (view == View.HISTORY && historyRange == DAY_RANGE && dayHoverStep >= 0) {
            final List<Component> tips = new ArrayList<>(5);
            final List<String> lines = pdu.dayOutlook().hoverLines(dayHoverStep);
            for (int i = 0; i < lines.size(); i++) {
                tips.add(Component.literal(lines.get(i))
                        .withStyle(i == 0 ? ChatFormatting.WHITE : ChatFormatting.GRAY));
            }
            g.renderComponentTooltip(font, tips, mouseX, mouseY);
            return;
        }
        if (!railTips.isEmpty()) {
            g.renderComponentTooltip(font, railTips, mouseX, mouseY);
        }
    }

    private void drawPanel(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        final FeGridSolver.BranchResult branch = pdu.lastBranchResult();
        refreshStatus(branch);
        railTips.clear();

        g.fill(panel.x(), panel.y(), panel.right(), panel.bottom(), PduTheme.BG_DEEP);
        PduTheme.outline(g, panel.x(), panel.y(), panel.w(), panel.h(), PduTheme.STROKE);

        rail.draw(g, railRect, mouseX, mouseY, partialTick, railTips);
        drawHeader(g, headerRect, branch, mouseX, mouseY);
        drawContent(g, mouseX, mouseY, partialTick);
        drawFooter(g, footerRect);
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
            note("totals", new PduRect(totalsX, textY, font.width(totals), 8));
        }
    }

    private static int modeColour(PduMode m) {
        return switch (m) {
            case AUTO -> PduTheme.OK;
            case MANUAL -> PduTheme.INFO;
            case OFF -> PduTheme.TEXT_DIM;
        };
    }

    private void drawFooter(GuiGraphics g, PduRect r) {
        g.fill(r.x(), r.y(), r.right(), r.bottom(), PduTheme.BG_PANEL);
        g.fill(r.x(), r.y(), r.right(), r.y() + 1, PduTheme.STROKE);
        final int textY = r.y() + (r.h() - 8) / 2;

        final String status = trimToWidth(statusText, r.w() - 2 * PduTheme.PAD);
        g.drawString(font, status, r.x() + PduTheme.PAD, textY, statusColour, false);
        note("footer.status", new PduRect(r.x() + PduTheme.PAD, textY, font.width(status), 8));

        final String keys = "1-" + Math.min(9, View.values().length);
        final String hints = breakpoint == PduTheme.Breakpoint.COMPACT
                ? keys + "  /  M  Esc"
                : keys + " Views   / Search   Tab Focus   M Mode   Esc Close";
        final int hintsX = r.right() - PduTheme.PAD - font.width(hints);
        if (hintsX > r.x() + PduTheme.PAD + font.width(status) + PduTheme.GAP) {
            g.drawString(font, hints, hintsX, textY, PduTheme.TEXT_FAINT, false);
            note("footer.keys", new PduRect(hintsX, textY, font.width(hints), 8));
        }
    }

    private void drawContent(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        switch (view) {
            case OVERVIEW -> drawOverview(g, mouseX, mouseY, partialTick);
            case LOADS -> drawTableView(g, pdu.roster(), mouseX, mouseY, partialTick);
            case LIGHTING -> drawTableView(g, lightingRoster(), mouseX, mouseY, partialTick);
            case HISTORY -> drawHistory(g, mouseX, mouseY, partialTick);
            case WIRING -> drawWiring(g, mouseX, mouseY);
            case FLOW -> drawFlow(g, mouseX, mouseY, partialTick);
            case LEGS -> drawLegs(g, mouseX, mouseY, partialTick);
            case CHAIN -> drawInOut(g, mouseX, mouseY, partialTick);
            default -> drawPlaceholder(g, contentRect, view);
        }
    }

    private void drawTableView(GuiGraphics g, List<BranchDevice> roster, int mouseX, int mouseY, float partialTick) {
        table.setData(roster, group(), sortKey, sortReversed, filter());

        int top = contentRect.y();
        if (view == View.LIGHTING) {
            drawLightingHeader(g, new PduRect(contentRect.x(), top, contentRect.w(), LIGHTING_HEADER_H),
                    roster, mouseX, mouseY);
            top += LIGHTING_HEADER_H + 2;
        }
        this.toolbarRect = new PduRect(contentRect.x(), top, contentRect.w(), toolbarHeight());
        final int belowY = top + toolbarHeight();
        PduRect below = new PduRect(contentRect.x(), belowY, contentRect.w(), contentRect.bottom() - belowY);

        if (!isFocusReachable(focus)) {
            setFocus(Focus.TABLE);
        }
        if (table.selectionSize() >= 2) {
            drawBulkBar(g, toolbarRect, mouseX, mouseY);
        } else {
            drawToolbar(g, toolbarRect, mouseX, mouseY);
        }

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
        table.setHeaderFocused(focus == Focus.SORT);
        table.draw(g, font, below, rowH, mouseX, mouseY, partialTick);
        final BranchDevice single = table.selectionSize() == 1 ? table.singleSelected() : null;
        drawer.setDevice(single);
        drawer.draw(g, font, below, mouseX, mouseY, partialTick, confirmLockOffPos,
                single != null && isPanelOwned(single));
    }

    private boolean isPanelOwned(BranchDevice device) {
        if (minecraft == null || minecraft.level == null || device.isStorage()) {
            return false;
        }
        final net.minecraft.world.level.block.entity.BlockEntity be =
                minecraft.level.getBlockEntity(device.pos());
        return be != null && !(be instanceof com.createstats.power.IFeConsumer)
                && !(be instanceof com.createstats.power.FeManagedLoad);
    }

    private void drawLightingHeader(GuiGraphics g, PduRect r, List<BranchDevice> lights, int mouseX, int mouseY) {
        lightingButtons.clear();
        PduTheme.panel(g, r.x(), r.y(), r.w(), r.h());
        note("lighting.header", r);
        int lit = 0;
        int totalDraw = 0;
        for (BranchDevice d : lights) {
            if (!d.isDark()) {
                lit++;
            }
            totalDraw += d.draw();
        }
        final int textY = r.y() + (r.h() - 8) / 2;
        g.drawString(font, lit + " lit,  " + (lights.size() - lit) + " dark   " + fmt(totalDraw) + " FE/t",
                r.x() + PduTheme.PAD, textY, PduTheme.TEXT, false);

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
        note("lighting.setall", new PduRect(x, textY, font.width(setAll), 8));
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

    private int toolbarHeight() {
        return breakpoint == PduTheme.Breakpoint.COMPACT ? TOOLBAR_H_STACKED : TOOLBAR_H;
    }

    private void drawToolbar(GuiGraphics g, PduRect r, int mouseX, int mouseY) {
        g.fill(r.x(), r.y(), r.right(), r.bottom(), PduTheme.BG_DEEP);
        final boolean stacked = breakpoint == PduTheme.Breakpoint.COMPACT;

        final int controlsY = stacked ? r.y() + 17 : r.y() + (r.h() - 14) / 2;
        final int searchY = stacked ? r.y() + 2 : controlsY;
        final int cy = controlsY;
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

        final int groupW = groupSeg.width(font);
        x -= groupW;
        groupSeg.setFocused(focus == Focus.GROUP);
        groupSeg.draw(g, font, x, cy, 14, mouseX, mouseY);
        x -= PduTheme.GAP;

        // show how much of the branch you're looking at, a forgotten search looks like a small branch

        int searchLeft = r.x() + PduTheme.PAD;
        if (tierFilter != null) {
            searchLeft += drawFilterChip(g, searchLeft, searchY, tierFilter.getDisplayName(),
                    PduGlyphs.tierColour(tierFilter), mouseX, mouseY, true) + PduTheme.GAP;
        } else {
            filterChipBounds = new PduRect(0, 0, 0, 0);
        }
        if (legFilter != PduTableModel.Filter.ANY_LEG) {
            searchLeft += drawFilterChip(g, searchLeft, searchY, PduLegRow.name(legFilter),
                    PduLegRow.colour(legFilter), mouseX, mouseY, false) + PduTheme.GAP;
        } else {
            legChipBounds = new PduRect(0, 0, 0, 0);
        }

        final String count = table.visibleDeviceCount() + " of " + loadRosterCount();
        final int countW = font.width(count) + PduTheme.GAP;
        if (!filter().isEmpty() && (stacked || x - countW - searchLeft >= 70)) {
            x -= font.width(count);
            g.drawString(font, count, x, cy + 3, PduTheme.TEXT_FAINT, false);
            x -= PduTheme.GAP;
        }

        final int searchRight = stacked ? r.right() - PduTheme.PAD : x;
        final int searchW = searchRight - searchLeft;
        search.setFocused(focus == Focus.SEARCH);
        if (searchW >= 40) {
            search.draw(g, font, searchLeft, searchY, searchW, mouseX, mouseY);
        }
    }

    private int drawFilterChip(GuiGraphics g, int x, int cy, String label, int colour,
                               int mouseX, int mouseY, boolean tier) {
        final String text = label + "  x";
        final int w = font.width(text) + 10;
        final boolean hover = mouseX >= x && mouseX < x + w && mouseY >= cy && mouseY < cy + 14;
        g.fill(x, cy, x + w, cy + 14, hover ? PduTheme.BG_RAISED : PduTheme.BG_PANEL);
        PduTheme.outline(g, x, cy, w, 14, colour);
        g.drawString(font, text, x + 5, cy + 3, colour, false);
        final PduRect bounds = new PduRect(x, cy, w, 14);
        if (tier) {
            filterChipBounds = bounds;
        } else {
            legChipBounds = bounds;
        }
        return w;
    }

    private PduTableModel.Filter filter() {
        return new PduTableModel.Filter(search.text(), shedOnly, tierFilter, legFilter);
    }

    private int loadRosterCount() {
        return view == View.LIGHTING ? lightingRoster().size() : pdu.roster().size();
    }

    private void drawBulkBar(GuiGraphics g, PduRect r, int mouseX, int mouseY) {
        bulkHits.clear();
        g.fill(r.x(), r.y(), r.right(), r.bottom(), PduTheme.BG_RAISED);
        PduTheme.outline(g, r.x(), r.y(), r.w(), r.h(), PduTheme.ACCENT);
        final int cy = r.y() + (r.h() - 14) / 2;

        final int selected = table.selectionSize();
        final int loads = table.selectedLoadCount();
        final String label = loads == selected
                ? selected + " selected"
                : loads + " of " + selected + " settable";
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

        x = bulkButton(g, x, cy, "Lock on", mouseX, mouseY, 103, false);
        x = bulkButton(g, x, cy, "Auto", mouseX, mouseY, 101, false);
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
        note("card.supply", supplyCard);
        note("card.demand", demandCard);
        note("card.buffer", bufferCard);

        final int measured = pdu.measuredInputFePerTick();
        final int generation = pdu.generationFePerTick();

        final boolean dark = pdu.storedEnergy() <= 0 && measured <= 0;
        final int supplyValue = isAuto ? generation : measured;
        final String supplySub = isAuto
                ? PduStatusLine.formatSupplySubtitle(measured, pdu.fillFraction()) : "measured intake";
        PduStatCard.draw(g, font, supplyCard, "Supply", fmt(supplyValue), "FE/t", supplySub, supplyRing,
                dark ? PduTheme.CRIT : PduTheme.INFO);

        final int total = branch.served() + branch.shed();

        final int exported = pdu.exportDemandFePerTick();

        final int upkeep = pdu.upkeepFePerTick();
        final int demand = (isAuto ? pdu.branchDemandFePerTick() : rosterTotalDraw())
                + exported + upkeep;
        final StringBuilder demandSubText =
                new StringBuilder(branch.served() + " of " + total + " devices");
        if (exported > 0) {
            demandSubText.append(" + ").append(fmt(exported)).append(" export");
        }
        if (upkeep > 0) {
            demandSubText.append(" + ").append(fmt(upkeep)).append(" panel");
        }
        final String demandSub = demandSubText.toString();
        PduStatCard.draw(g, font, demandCard, "Demand", fmt(demand), "FE/t", demandSub,
                isAuto ? demandRing : null, exported > 0 ? PduTheme.INFO : PduTheme.TEXT);

        drawBufferCard(g, bufferCard, isAuto, partialTick);

        final int balanceH = compact ? 38 : 44;
        final PduRect balance = new PduRect(area.x(), supplyCard.bottom() + gap, area.w(), balanceH);
        note("balance", balance);

        for (int i = 0; i < tierDraw.length; i++) {
            shownTierDraw[i] = Ease.ease(shownTierDraw[i], tierDraw[i], 0.18f, partialTick);
            shownTierServed[i] = Ease.ease(shownTierServed[i], tierServed[i], 0.18f, partialTick);
            bandDraw[i] = Math.round(shownTierDraw[i]);
            bandServed[i] = Math.round(shownTierServed[i]);
        }
        PduBalanceBar.draw(g, font, balance, bandDraw, bandServed, loadRosterSize());

        final int row3Y = balance.bottom() + gap;
        final int row3H = Math.max(30, area.bottom() - row3Y);
        final int statusW = Math.round((area.w() - gap) * 0.56f);
        final PduRect statusCard = new PduRect(area.x(), row3Y, statusW, row3H);
        final PduRect rollCard = new PduRect(area.x() + statusW + gap, row3Y,
                area.right() - (area.x() + statusW + gap), row3H);
        note("card.status", statusCard);
        note("card.roll", rollCard);
        drawStatusCard(g, statusCard);
        drawRollCall(g, rollCard, mouseX, mouseY);
    }

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
        note("gauge", new PduRect(c.x(), c.y() + 13, c.w(), 9));

        final String sub;
        final int subColour;
        if (!isAuto) {
            sub = "Floor at " + Math.round(pdu.reserveFloorFraction() * 100) + "%";
            subColour = PduTheme.TEXT_DIM;
        } else if (pdu.isReserveLinked()) {
            final int soc = pdu.linkedReserveSocPct();
            final com.createstats.power.FeReserveReport.Stance stance = pdu.linkedStance();
            final int controllers = Math.max(1, pdu.reserveLinkCount());
            sub = (controllers == 1 ? "Controller " : controllers + " controllers ") + soc + "%  "
                    + fmt(pdu.linkedReserveStored()) + " FE  "
                    + (stance == null ? "" : stance.label());
            subColour = stance == null ? PduTheme.TEXT_DIM : switch (stance) {
                case SURPLUS -> PduTheme.OK;
                case NOMINAL -> PduTheme.INFO;
                case CONSERVE -> PduTheme.WARN;
                case CRITICAL -> PduTheme.CRIT;
            };
        } else if (pdu.reserveTankCount() > 1) {
            // charge first, that's what protection keys on. tank count colouring kept one-full-one-empty builds orange forever

            final int banksPct = Math.round(pdu.reserveFillFraction() * 100.0f);
            sub = "Banks " + banksPct + "%  " + pdu.reserveDepth() + "/" + pdu.reserveTankCount() + "  "
                    + PduStatusLine.formatEndurance(pdu.autoEnduranceTicks(), pdu.isSurplus(), fill);
            subColour = pdu.isCoreProtected() ? PduTheme.CRIT
                    : banksPct >= 40 ? PduTheme.TEXT_DIM : PduTheme.WARN;
        } else if (pdu.reserveTankCount() == 1) {
            final int banksPct = Math.round(pdu.reserveFillFraction() * 100.0f);
            sub = "Bank " + banksPct + "%  " + fmt(pdu.reserveStored()) + " FE  "
                    + PduStatusLine.formatEndurance(pdu.autoEnduranceTicks(), pdu.isSurplus(), fill);
            subColour = pdu.isCoreProtected() ? PduTheme.CRIT
                    : banksPct >= 40 ? PduTheme.TEXT_DIM : PduTheme.WARN;
        } else {
            sub = "Reserve " + PduStatusLine.formatEndurance(pdu.autoEnduranceTicks(), pdu.isSurplus(), fill);
            subColour = PduTheme.TEXT_DIM;
        }
        g.drawString(font, trimToWidth(sub, c.w()), c.x(), c.bottom() - 8, subColour, false);
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
            if (d.isStorage() || d.reason() == PduReason.LOCKED_OFF) {
                continue;
            }
            // band by cost, not this tick's draw. the bar and the card disagreed in the same frame

            final int t = d.priority().ordinal();
            tierDraw[t] += d.cost();
            if (!d.isDark()) {
                tierServed[t] += d.cost();
            }
        }
    }

    private int loadRosterSize() {
        int n = 0;
        for (BranchDevice d : pdu.roster()) {
            if (!d.isStorage()) {
                n++;
            }
        }
        return n;
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
        // minus the export. it said 2,329 spare, the real number was 262

        final int spare = isAuto ? Math.max(0, pdu.generationFePerTick()
                - pdu.branchDemandFePerTick() - pdu.exportDemandFePerTick()
                - pdu.upkeepFePerTick()) : 0;
        final boolean surplus = isAuto && pdu.isSurplus();
        final boolean throughputLimited = isAuto && pdu.isThroughputLimited();
        final int endurance = isAuto ? pdu.autoEnduranceTicks() : Integer.MAX_VALUE;
        final int floorPct = Math.round(pdu.reserveFloorFraction() * 100.0f);
        final int strayLeg = pdu.firstStrayControllerLeg();
        final int strayStud = pdu.firstStrayPanelStudLeg();
        statusText = PduStatusLine.derive(mode, hasBranch, dark, surplus, throughputLimited, shed,
                spare, endurance, pdu.fillFraction(), floorPct, pdu.foreignFeedMisplaced(), strayLeg,
                isAuto ? pdu.exportDemandFePerTick() : 0, strayStud);
        statusColour = pdu.foreignFeedMisplaced() || strayLeg >= 0 || strayStud >= 0 || !hasBranch || dark
                ? PduTheme.CRIT
                : shed > 0 ? PduTheme.WARN : PduTheme.OK;
    }

    private static String fmt(int v) {
        return String.format(Locale.ROOT, "%,d", v);
    }

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

    private void drawHistory(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        final PduRect area = contentRect.inset(PduTheme.GAP);
        final int legendH = 12;
        if (historyRange == DAY_RANGE) {
            drawRangeButtons(g, area, mouseX, mouseY);
            drawDay(g, area, legendH, mouseX, mouseY);
            return;
        }
        dayHoverStep = -1;
        final PduRect chartBounds = new PduRect(area.x(), area.y() + legendH + 2, area.w(),
                Math.max(20, area.h() - legendH - 2));

        scrubIndex = PduChart.sampleAt(chartBounds, clientHistory, mouseX, mouseY);
        final int shown = scrubIndex >= 0 ? scrubIndex : clientHistory.count() - 1;
        final int supplyVal = shown >= 0 ? clientHistory.supplyAt(shown) : 0;
        final int demandVal = shown >= 0 ? clientHistory.demandAt(shown) : 0;

        int lx = area.x();
        g.fill(lx, area.y() + 2, lx + 8, area.y() + 8, PduTheme.withAlpha(PduTheme.OK, 0xC0));
        note("history.supply", new PduRect(lx, area.y() + 2, 8, 6));
        final String supplyLabel = "Supply " + fmt(supplyVal);
        g.drawString(font, supplyLabel, lx + 11, area.y() + 2, PduTheme.TEXT, false);
        lx += 11 + font.width(supplyLabel) + 12;
        g.fill(lx, area.y() + 3, lx + 8, area.y() + 5, PduTheme.WARN);
        note("history.demand", new PduRect(lx, area.y() + 3, 8, 2));
        final String demandLabel = "Demand " + fmt(demandVal);
        g.drawString(font, demandLabel, lx + 11, area.y() + 2, PduTheme.TEXT, false);
        if (scrubIndex >= 0) {
            g.drawString(font, "scrubbing", lx + 11 + font.width(demandLabel) + 10, area.y() + 2,
                    PduTheme.TEXT_FAINT, false);
        }

        drawRangeButtons(g, area, mouseX, mouseY);

        int realMax = 1;
        for (int i = 0; i < clientHistory.count(); i++) {
            realMax = Math.max(realMax, Math.max(clientHistory.supplyAt(i), clientHistory.demandAt(i)));
        }

        shownChartMax = easeFrom(shownChartMax, realMax, partialTick);
        PduChart.draw(g, font, chartBounds, clientHistory, scrubIndex, Math.max(1, Math.round(shownChartMax)));
        if (notes != null) {
            final PduRect plot = PduChart.plotBounds(chartBounds);
            note("history.plot", plot);
            note("history.rail", new PduRect(plot.x(), plot.bottom() + 2, plot.w(), PduChart.RAIL_H));

            final int max = Math.max(1, Math.round(shownChartMax));
            final int top = PduChart.valueY(plot, supplyVal, max);
            note("history.area", new PduRect(plot.x(), top, plot.w(), Math.max(1, plot.bottom() - 1 - top)));
            note("history.line", new PduRect(plot.x(), PduChart.valueY(plot, demandVal, max), plot.w(), 1));
        }
    }

    private void drawRangeButtons(GuiGraphics g, PduRect area, int mouseX, int mouseY) {
        final String[] labels = {"1m", "5m", "15m", "Day"};
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

            if (i == DAY_RANGE) {
                bx -= 4;
            }
        }
    }

    private void drawDay(GuiGraphics g, PduRect area, int legendH, int mouseX, int mouseY) {
        final PduDayOutlook day = pdu.dayOutlook();
        final boolean narrow = area.w() < 320;

        final int legendRight = rangeButtons[0].x() - 8;
        int lx = area.x();
        lx = dayLegend(g, lx, area.y(), legendRight, PduTheme.withAlpha(PduTheme.OK, 0xC0), false, "Supply");
        if (day.criticalFePerTick() > 0) {
            lx = dayLegend(g, lx, area.y(), legendRight, PduTheme.CRIT, true, "Critical");
        }
        if (day.anyRainCost()) {
            lx = dayLegend(g, lx, area.y(), legendRight, PduTheme.withAlpha(PduTheme.INFO, 0xA0), false, "Rain");
        }
        if (day.anyCold()) {
            dayLegend(g, lx, area.y(), legendRight, PduTheme.BG_RAISED, false, narrow ? "Unlearned" : "Not learned");
        }

        final String hold = day.holdLine(false);

        final int footH = !day.hasCurve() ? 0 : hold == null ? 11 : 21;
        final PduRect chart = new PduRect(area.x(), area.y() + legendH + 2, area.w(),
                Math.max(30, area.h() - legendH - 2 - footH));
        dayHoverStep = PduDayChart.stepAt(chart, day, mouseX, mouseY);
        PduDayChart.draw(g, font, chart, day, dayHoverStep);
        if (!day.hasCurve()) {
            return;
        }
        final int fy = chart.bottom() + 2;
        final String summary = fitText(day.summary(false), day.summary(true), area.w());
        g.drawString(font, summary, area.x(), fy, day.shortfallFe() > 0 ? PduTheme.WARN : PduTheme.TEXT_DIM, false);
        if (hold != null) {
            g.drawString(font, fitText(hold, day.holdLine(true), area.w()), area.x(), fy + 10,
                    PduTheme.TEXT_FAINT, false);
        }
    }

    private int dayLegend(GuiGraphics g, int x, int y, int right, int colour, boolean line, String label) {
        final int w = 11 + font.width(label);
        if (x + w > right) {
            return x;
        }
        if (line) {
            g.fill(x, y + 4, x + 3, y + 5, colour);
            g.fill(x + 5, y + 4, x + 8, y + 5, colour);
        } else {
            g.fill(x, y + 2, x + 8, y + 8, colour);
        }
        g.drawString(font, label, x + 11, y + 2, PduTheme.TEXT, false);
        return x + w + 10;
    }

    private String fitText(String full, String shortForm, int width) {
        return full == null ? shortForm : font.width(full) <= width ? full : shortForm;
    }

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
        if (historyRange == DAY_RANGE) {
            closeHistory();
            return;
        }
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

    private void drawWiring(GuiGraphics g, int mouseX, int mouseY) {
        final PduRect area = contentRect.inset(PduTheme.GAP);

        int extraLines = 0;
        if (pdu.branchStorageSummary().tanks() > 0) {
            extraLines++;
        }
        if (pdu.branchSourceCount() > 0) {
            extraLines++;
        }
        final int cardH = 44 + extraLines * 11;
        final PduRect diagramBounds = new PduRect(area.x(), area.y(), area.w(),
                Math.max(40, area.h() - cardH - PduTheme.GAP));
        final PduRect statsCard = new PduRect(area.x(), diagramBounds.bottom() + PduTheme.GAP, area.w(), cardH);
        note("diagram", diagramBounds);
        note("card.wiring", statsCard);

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
        note("wiring.lines", c);
        final PowerDistributionUnitBE.BranchWiring w = pdu.wiring();

        final int wires = pdu.wiresOnStuds();
        g.drawString(font, trimToWidth(wires + (wires == 1 ? " wire, " : " wires, ")
                        + w.segments() + (w.segments() == 1 ? " conductor" : " conductors"), c.w()),
                c.x(), c.y(), PduTheme.TEXT, false);
        g.drawString(font, trimToWidth(fmt(w.buffered()) + " / " + fmt(w.capacity()) + " FE buffered", c.w()),
                c.x(), c.y() + 11, PduTheme.TEXT_DIM, false);

        int line = c.y() + 22;
        final PowerDistributionUnitBE.BranchStorage store = pdu.branchStorageSummary();
        if (store.tanks() > 0) {
            final String flow = store.chargeRate() == 0 ? ""
                    : "  " + (store.chargeRate() > 0 ? "+" : "-") + fmt(Math.abs(store.chargeRate())) + " FE/t";
            g.drawString(font, trimToWidth(store.tanks() + (store.tanks() == 1 ? " tank " : " tanks ")
                            + fmt(store.stored()) + " / " + fmt(store.capacity()) + " FE" + flow, c.w()),
                    c.x(), line,
                    store.chargeRate() > 0 ? PduTheme.OK
                            : store.chargeRate() < 0 ? PduTheme.INFO : PduTheme.TEXT_DIM, false);
            line += 11;
        }

        if (pdu.branchSourceCount() > 0) {
            final int feeds = pdu.branchSourceCount();
            g.drawString(font, trimToWidth(feeds + (feeds == 1 ? " feed on a leg, " : " feeds on legs, ")
                            + fmt(pdu.branchSourceInputFePerTick()) + " FE/t in", c.w()),
                    c.x(), line,
                    pdu.branchSourceInputFePerTick() > 0 ? PduTheme.OK : PduTheme.TEXT_DIM, false);
        }
        final boolean limited = pdu.isThroughputLimited();
        final String ceiling = "Ceiling " + fmt(pdu.branchCeilingFePerTick()) + " FE/t"
                + (limited ? "  at the limit" : "");
        g.drawString(font, trimToWidth(ceiling, c.w()), c.x(), c.bottom() - 8,
                limited ? PduTheme.CRIT : PduTheme.TEXT_DIM, false);
    }

    private final java.util.List<int[]> legHits = new ArrayList<>();

    private void drawLegs(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        final PduRect area = contentRect.inset(PduTheme.GAP);
        legHits.clear();
        legRowHits.clear();
        legRankHits.clear();
        legBoardHits.clear();
        final int rows = PowerDistributionUnitBE.LEGS;

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
        legRowHits.add(new int[]{r.x(), r.y(), r.right(), r.bottom(), leg});

        PduTheme.panel(g, r.x(), r.y(), r.w(), r.h());

        g.fill(r.x(), r.y(), r.x() + 3, r.bottom(),
                wired ? colour : PduTheme.withAlpha(colour, 0x40));
        if (notes != null) {
            note("leg.tab." + leg, new PduRect(r.x(), r.y(), 3, r.h()));
        }

        final int textX = r.x() + 10;
        final int nameW = 46;
        g.drawString(font, PduLegRow.name(leg), textX, r.y() + 4,
                wired ? PduTheme.TEXT : PduTheme.TEXT_FAINT, false);

        final int stateColour = state.isAlarm() ? PduTheme.CRIT
                : state == PduLegRow.State.UNWIRED ? PduTheme.TEXT_FAINT
                : state == PduLegRow.State.OPEN ? PduTheme.WARN : PduTheme.OK;
        g.drawString(font, state.label(), textX + nameW, r.y() + 4, stateColour, false);

        final PowerDistributionUnitBE.BranchDevice boardRow = pdu.legBoardRow(leg);
        if (r.h() >= 24) {
            g.drawString(font, trimToWidth(
                            PduLegRow.detail(state, demand, rating, devices, PduLegRow.isBack(leg),
                                    boardRow != null, pdu.isExportHeld()),
                            r.w() - 96),
                    textX, r.y() + 15, PduTheme.TEXT_DIM, false);
        }

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

            int chipX = bx - PduTheme.GAP;
            final int chipY = r.y() + (r.h() - PduChip.HEIGHT) / 2;
            if (r.w() >= 210) {
                if (boardRow != null) {
                    final String boardLabel = "BOARD " + boardRow.priority().getShortName();
                    chipX -= PduChip.width(font, boardLabel);
                    final boolean hotBoard = mouseX >= chipX && mouseX <= chipX + PduChip.width(font, boardLabel)
                            && mouseY >= chipY && mouseY <= chipY + PduChip.HEIGHT;
                    PduChip.draw(g, font, chipX, chipY, boardLabel,
                            PduGlyphs.tierColour(boardRow.priority()), hotBoard, false);
                    legBoardHits.add(new int[]{chipX, chipY,
                            chipX + PduChip.width(font, boardLabel), chipY + PduChip.HEIGHT, leg});
                    chipX -= PduTheme.GAP;
                }
                final FePriority legTier = pdu.legPriority(leg);
                final String legLabel = "LEG " + legTier.getShortName();
                chipX -= PduChip.width(font, legLabel);
                final boolean hotLeg = mouseX >= chipX && mouseX <= chipX + PduChip.width(font, legLabel)
                        && mouseY >= chipY && mouseY <= chipY + PduChip.HEIGHT;
                PduChip.draw(g, font, chipX, chipY, legLabel, PduGlyphs.tierColour(legTier),
                        hotLeg, false);
                legRankHits.add(new int[]{chipX, chipY,
                        chipX + PduChip.width(font, legLabel), chipY + PduChip.HEIGHT, leg});
            }
        }
    }

    private final java.util.List<int[]> legRankHits = new ArrayList<>();

    private final java.util.List<int[]> legBoardHits = new ArrayList<>();

    private final float[] shownLegLoad = new float[PowerDistributionUnitBE.LEGS];

    private final java.util.List<int[]> legRowHits = new ArrayList<>();

    private boolean clickLegs(double mouseX, double mouseY) {
        for (int[] hit : legRankHits) {
            if (mouseX >= hit[0] && mouseX <= hit[2] && mouseY >= hit[1] && mouseY <= hit[3]) {
                final int leg = hit[4];
                PacketDistributor.sendToServer(
                        com.createstats.network.PduActionPacket.cycleLegPriority(pdu.getBlockPos(), leg));
                toast(PduLegRow.name(leg) + " rank: " + pdu.legPriority(leg).next().getShortName()
                        + " (orders legs within a tier)", PduToast.Severity.INFO);
                return true;
            }
        }
        for (int[] hit : legBoardHits) {
            if (mouseX >= hit[0] && mouseX <= hit[2] && mouseY >= hit[1] && mouseY <= hit[3]) {
                final PowerDistributionUnitBE.BranchDevice board = pdu.legBoardRow(hit[4]);
                if (board == null) {
                    return true;
                }
                final FePriority next = board.priority().next();
                sendSet(List.of(board.pos()), PduSetPacket.ACTION_PRIORITY, next.ordinal());
                toast(board.name() + " tier: " + next.getShortName(), PduToast.Severity.INFO);
                return true;
            }
        }
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

        for (int[] hit : legRowHits) {
            if (mouseX >= hit[0] && mouseX <= hit[2] && mouseY >= hit[1] && mouseY <= hit[3]) {
                setLegFilter(hit[4]);
                return true;
            }
        }
        return false;
    }

    private void drawFlow(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        final PduRect area = contentRect.inset(PduTheme.GAP);
        final boolean narrow = breakpoint == PduTheme.Breakpoint.COMPACT;
        final PduRect sceneBounds;
        final PduRect workingBounds;
        if (narrow) {
            final int workingH = Math.max(64, Math.round(area.h() * 0.40f));
            sceneBounds = new PduRect(area.x(), area.y(), area.w(), area.h() - workingH - PduTheme.GAP);
            workingBounds = new PduRect(area.x(), sceneBounds.bottom() + PduTheme.GAP, area.w(), workingH);
        } else {
            final int workingW = Math.max(148, Math.round(area.w() * 0.36f));
            sceneBounds = new PduRect(area.x(), area.y(), area.w() - workingW - PduTheme.GAP, area.h());
            workingBounds = new PduRect(sceneBounds.right() + PduTheme.GAP, area.y(), workingW, area.h());
        }
        this.flowSceneRect = sceneBounds;
        note("flow.scene", sceneBounds);
        note("flow.working", workingBounds);
        drawFlowScene(g, sceneBounds, partialTick);
        drawFlowWorking(g, workingBounds);
    }

    private void drawFlowScene(GuiGraphics g, PduRect bounds, float partialTick) {
        PduTheme.panel(g, bounds.x(), bounds.y(), bounds.w(), bounds.h());
        final PduRect inner = bounds.inset(PduTheme.BASE);

        final PduRect viewport = new PduRect(inner.x(), inner.y(), inner.w(), inner.h() - 10);

        final List<com.createstats.gui.pdu.PduTopology.BranchNode> topology = pdu.topology();

        final boolean hasWorld = specimen || (minecraft != null && minecraft.level != null);
        if (topology.isEmpty() || !pdu.hasAnyOutput() || !hasWorld) {
            g.drawString(font, "Nothing is wired to this panel.", inner.x(),
                    inner.y() + inner.h() / 2 - 4, PduTheme.TEXT_FAINT, false);
            return;
        }
        final List<BlockPos> positions = new ArrayList<>(topology.size() + 1);
        for (com.createstats.gui.pdu.PduTopology.BranchNode n : topology) {
            positions.add(n.pos());
        }

        if (!positions.contains(pdu.getBlockPos())) {
            positions.add(pdu.getBlockPos());
        }
        final Map<BlockPos, BranchDevice> byPos = new HashMap<>(pdu.roster().size() * 2);
        for (BranchDevice d : pdu.roster()) {
            byPos.put(d.pos(), d);
        }
        if (specimen) {
            note("flow.viewport", viewport);
            if (specimenScene != null) {
                specimenScene.draw(g, viewport.x(), viewport.y(), viewport.w(), viewport.h(), positions, byPos,
                        pdu.getBlockPos(), flowCamera);
            }
        } else {
            com.createstats.gui.pdu.PduScene3D.render(g, viewport.x(), viewport.y(), viewport.w(),
                    viewport.h(), minecraft.level, positions, byPos, pdu.getBlockPos(), flowCamera,
                    minecraft.level.getGameTime(), partialTick);
        }

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

    private void drawFlowWorking(GuiGraphics g, PduRect bounds) {
        final PduRect c = PduCard.draw(g, font, bounds, "How this was decided");
        final FeGridSolver.BranchResult branch = pdu.lastBranchResult();
        final List<com.createstats.gui.pdu.PduDerivation.Row> rows =
                com.createstats.gui.pdu.PduDerivation.derive(new com.createstats.gui.pdu.PduDerivation.Inputs(

                        pdu.mode(), pdu.hasAnyOutput(), pdu.generationFePerTick(),
                        pdu.measuredInputFePerTick(), pdu.branchCeilingFePerTick(), pdu.budgetFePerTick(),
                        pdu.branchDemandFePerTick(), pdu.servedDemandFePerTick(), branch.served(),
                        branch.shed(), pdu.storedEnergy(), pdu.capacity(), pdu.reserveFloorFraction(),
                        pdu.isSurplus(), pdu.isThroughputLimited(), reserveLink(),
                        pdu.exportDemandFePerTick(), pdu.upkeepFePerTick()));

        final int available = (c.bottom() - c.y()) / 10;
        final List<com.createstats.gui.pdu.PduDerivation.Row> shown =
                com.createstats.gui.pdu.PduDerivation.fit(rows, available);

        int y = c.y();
        for (com.createstats.gui.pdu.PduDerivation.Row row : shown) {
            if (y > c.bottom() - 8) {
                break;
            }
            y = drawDerivationRow(g, c, row, y);
        }
    }

    private final List<Component> inOutTips = new ArrayList<>(6);

    private final float[] shownFlow = new float[com.createstats.gui.pdu.PduFlowModel.Kind.values().length];

    private final int[] flowTarget = new int[com.createstats.gui.pdu.PduFlowModel.Kind.values().length];
    private float shownBusFill = -1.0f;

    private final float[] shownLadder = {-1.0f, -1.0f, -1.0f, -1.0f};

    private final int[] shedByReason = new int[PduReason.values().length];

    private boolean chainDetails;
    private PduRect chainToggle = new PduRect(0, 0, 0, 0);

    private void drawInOut(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        inOutTips.clear();
        final PduRect area = contentRect.inset(PduTheme.GAP);
        final String toggle = chainDetails ? "Show the flow" : "Chain details";
        final int tw = font.width(toggle) + 10;
        chainToggle = new PduRect(area.right() - tw, area.y(), tw, 12);
        final boolean hot = chainToggle.contains(mouseX, mouseY);
        g.fill(chainToggle.x(), chainToggle.y(), chainToggle.right(), chainToggle.bottom(),
                hot ? PduTheme.BG_RAISED : PduTheme.BG_PANEL);
        PduTheme.outline(g, chainToggle.x(), chainToggle.y(), chainToggle.w(), chainToggle.h(),
                hot ? PduTheme.STROKE_HOT : PduTheme.STROKE);
        g.drawString(font, toggle, chainToggle.x() + 5, chainToggle.y() + 2, PduTheme.TEXT, false);
        final String sub = chainDetails ? "Upstream, this board, downstream"
                : "Where the energy comes from, where it goes, and why";
        g.drawString(font, trimToWidth(sub, area.w() - tw - 8), area.x(), area.y() + 2,
                PduTheme.TEXT_DIM, false);
        final PduRect body = new PduRect(area.x(), area.y() + 16, area.w(), Math.max(0, area.h() - 16));
        if (chainDetails) {
            drawChain(g, body, partialTick);
            return;
        }

        computeTierBands();
        java.util.Arrays.fill(shedByReason, 0);
        int shedCount = 0;
        for (BranchDevice d : pdu.roster()) {
            if (d.isStorage() || !d.reason().isShed()) {
                continue;
            }
            shedByReason[d.reason().ordinal()] += Math.max(0, d.cost());
            shedCount++;
        }

        final float riverShare = switch (breakpoint) {
            case COMPACT -> 0.55f;
            case NORMAL -> 0.60f;
            case LARGE, XL -> 0.66f;
        };
        final int riverH = Math.min(Math.max(90, Math.round(body.h() * riverShare)),
                Math.max(40, body.h() - 64));
        final PduRect river = new PduRect(body.x(), body.y(), body.w(), riverH);
        final PduRect ladder = new PduRect(body.x(), river.bottom() + PduTheme.GAP, body.w(),
                Math.max(0, body.bottom() - river.bottom() - PduTheme.GAP));
        drawRiver(g, river, mouseX, mouseY, partialTick);
        if (ladder.h() > 20) {
            drawLadder(g, ladder, mouseX, mouseY, shedCount, partialTick);
        }
    }

    private static int flowColour(com.createstats.gui.pdu.PduFlowModel.Kind kind) {
        final FePriority tier = kind.tier();
        if (tier != null) {
            return PduGlyphs.tierColour(tier);
        }
        return switch (kind) {
            case SOLAR -> 0xFFE3B75A;
            case GENERATORS -> PduTheme.ACCENT;
            case CONTROLLER -> PduTheme.INFO;
            case UNPRICED -> 0xFF9AA6B2;
            case SOURCE_BUFFERS -> 0xFF8FA37A;
            case LEG_SOURCES -> 0xFFB9824F;
            case FROM_BUFFER -> 0xFF6FB6D8;
            case EXPORT -> 0xFF8E7CC3;
            case CHARGING -> PduTheme.INFO;
            case UPKEEP -> PduTheme.TEXT_FAINT;
            case SPARE -> PduTheme.withAlpha(PduTheme.OK, 0xB0);
            default -> PduTheme.TEXT_DIM;
        };
    }

    private static int lighten(int argb) {
        final int r = (argb >> 16) & 0xFF;
        final int gg = (argb >> 8) & 0xFF;
        final int b = argb & 0xFF;
        return 0xFF000000 | ((r + (255 - r) / 2) << 16) | ((gg + (255 - gg) / 2) << 8) | (b + (255 - b) / 2);
    }

    private void drawRiver(GuiGraphics g, PduRect r, int mx, int my, float partialTick) {
        PduTheme.panel(g, r.x(), r.y(), r.w(), r.h());
        note("inout.river", r);
        final PduRect in = r.inset(6);
        final com.createstats.gui.pdu.PduFlowModel.Flow flow = com.createstats.gui.pdu.PduFlowModel.flow(
                new com.createstats.gui.pdu.PduFlowModel.Inputs(pdu.supplyBreakdown(), tierServed,
                        pdu.backExportFePerTick(), Math.max(0, pdu.branchStorageSummary().chargeRate()),
                        pdu.upkeepFePerTick()));

        final com.createstats.gui.pdu.PduFlowModel.Kind[] kinds = com.createstats.gui.pdu.PduFlowModel.Kind.values();
        final int[] target = new int[kinds.length];
        for (com.createstats.gui.pdu.PduFlowModel.Node n : flow.in()) {
            target[n.kind().ordinal()] = n.fe();
        }
        for (com.createstats.gui.pdu.PduFlowModel.Node n : flow.out()) {
            target[n.kind().ordinal()] = n.fe();
        }
        for (int i = 0; i < kinds.length; i++) {
            shownFlow[i] = Ease.ease(shownFlow[i], target[i], 0.18f, partialTick);
        }
        System.arraycopy(target, 0, flowTarget, 0, target.length);
        final int colTop = r.inset(6).y() + 12;
        final int colH = Math.max(20, r.inset(6).h() - 12);
        int shedTotal = 0;
        for (int fe : shedByReason) {
            shedTotal += fe;
        }
        final int shedRoom = shedTotal > 0 ? 18 : 0;

        final List<com.createstats.gui.pdu.PduFlowModel.Node> ins = com.createstats.gui.pdu.PduFlowModel.fit(
                easedNodes(true), com.createstats.gui.pdu.PduFlowModel.capacity(colH, 4, 12), true);
        final List<com.createstats.gui.pdu.PduFlowModel.Node> outs = com.createstats.gui.pdu.PduFlowModel.fit(
                easedNodes(false), com.createstats.gui.pdu.PduFlowModel.capacity(colH - shedRoom, 4, 12), false);
        int inSum = 0;
        for (com.createstats.gui.pdu.PduFlowModel.Node n : ins) {
            inSum += n.fe();
        }
        int outSum = 0;
        for (com.createstats.gui.pdu.PduFlowModel.Node n : outs) {
            outSum += n.fe();
        }
        final int total = Math.max(inSum, outSum);

        final int colW = Math.max(64, Math.min(150, Math.round(in.w() * 0.22f)));
        final int busW = Math.max(18, Math.min(44, Math.round(in.w() * 0.07f)));
        final int leftX = in.x();
        final int rightX = in.right() - colW;
        final int busX = in.centerX() - busW / 2;
        final int top = colTop;
        final int h = colH;

        PduCard.drawHeading(g, font, "In", leftX, in.y());
        PduCard.drawHeading(g, font, "Out", rightX, in.y());
        final int pct = Math.round(pdu.fillFraction() * 100.0f);
        final String busHead = "Board " + pct + "%";
        g.drawString(font, busHead, in.centerX() - font.width(busHead) / 2, in.y(), PduTheme.TEXT, false);

        if (total <= 0) {
            drawBus(g, busX, top, busW, h, mx, my, partialTick);
            final String quiet = "Nothing is flowing through this board.";
            g.drawString(font, quiet, in.centerX() - font.width(quiet) / 2, top + h / 2 - 4,
                    PduTheme.TEXT_FAINT, false);
            return;
        }

        final int shedFe = shedTotal;
        final int shedReserve = shedRoom;
        final int[] ly = com.createstats.gui.pdu.PduFlowModel.layout(ins, top, h, 4, 12);
        final int[] ry = com.createstats.gui.pdu.PduFlowModel.layout(outs, top, h - shedReserve, 4, 12);
        final int[] lb = com.createstats.gui.pdu.PduFlowModel.busSlots(ins, total, top, h);
        final int[] rb = com.createstats.gui.pdu.PduFlowModel.busSlots(outs, total, top, h);
        final long millis = Util.getMillis();
        final boolean still = Ease.reduceMotion();

        for (int i = 0; i < ins.size(); i++) {
            final com.createstats.gui.pdu.PduFlowModel.Node n = ins.get(i);
            ribbon(g, leftX + colW, ly[i * 2], ly[i * 2 + 1], busX, lb[i * 2], lb[i * 2 + 1],
                    flowColour(n.kind()), liveFe(n, target), millis, still);
        }
        for (int i = 0; i < outs.size(); i++) {
            final com.createstats.gui.pdu.PduFlowModel.Node n = outs.get(i);
            ribbon(g, busX + busW, rb[i * 2], rb[i * 2 + 1], rightX, ry[i * 2], ry[i * 2 + 1],
                    flowColour(n.kind()), liveFe(n, target), millis, still);
        }
        drawBus(g, busX, top, busW, h, mx, my, partialTick);
        for (int i = 0; i < ins.size(); i++) {
            flowBox(g, ins.get(i), liveFe(ins.get(i), target), leftX, ly[i * 2], colW,
                    ly[i * 2 + 1] - ly[i * 2], true, mx, my);
        }
        for (int i = 0; i < outs.size(); i++) {
            flowBox(g, outs.get(i), liveFe(outs.get(i), target), rightX, ry[i * 2], colW,
                    ry[i * 2 + 1] - ry[i * 2], false, mx, my);
        }
        if (shedFe > 0) {
            drawShedGhost(g, rightX, top + h - shedReserve + 4, colW, shedReserve - 4, busX + busW,
                    top + h - 2, shedFe, millis, still, mx, my);
        }
    }

    private static int liveFe(com.createstats.gui.pdu.PduFlowModel.Node n, int[] target) {
        final com.createstats.gui.pdu.PduFlowModel.Kind k = n.kind();
        return k == com.createstats.gui.pdu.PduFlowModel.Kind.OTHER_IN
                || k == com.createstats.gui.pdu.PduFlowModel.Kind.OTHER_OUT ? n.fe() : target[k.ordinal()];
    }

    private List<com.createstats.gui.pdu.PduFlowModel.Node> easedNodes(boolean sources) {
        final List<com.createstats.gui.pdu.PduFlowModel.Node> nodes = new ArrayList<>(8);
        for (com.createstats.gui.pdu.PduFlowModel.Kind kind : com.createstats.gui.pdu.PduFlowModel.Kind.values()) {
            if (kind.isSource() != sources) {
                continue;
            }
            final int fe = Math.round(shownFlow[kind.ordinal()]);

            if (fe > 0 && flowTarget[kind.ordinal()] > 0) {
                nodes.add(new com.createstats.gui.pdu.PduFlowModel.Node(kind, fe));
            }
        }
        return nodes;
    }

    private void ribbon(GuiGraphics g, int x0, int a0, int a1, int x1, int b0, int b1, int colour,
                        int fe, long millis, boolean still) {
        final int span = x1 - x0;
        if (span <= 2) {
            return;
        }
        final int band = PduTheme.withAlpha(colour, 0x5C);
        final int edge = PduTheme.withAlpha(colour, 0x9C);
        for (int x = x0; x < x1; x += 2) {
            final float s = com.createstats.gui.pdu.PduFlowModel.smooth((x - x0) / (float) span);
            final int t = Math.round(a0 + (b0 - a0) * s);
            int b = Math.round(a1 + (b1 - a1) * s);
            if (b <= t) {
                b = t + 1;
            }
            final int xr = Math.min(x + 2, x1);
            g.fill(x, t, xr, b, band);
            g.fill(x, t, xr, t + 1, edge);
        }
        if (still || fe <= 0) {
            return;
        }
        final float spacing = 16.0f;
        final float phase = com.createstats.gui.pdu.PduFlowModel.particlePhase(millis, fe, spacing);
        final int dot = lighten(colour);
        final float c0 = (a0 + a1) / 2.0f;
        final float c1 = (b0 + b1) / 2.0f;
        final int thick = Math.min(a1 - a0, b1 - b0);
        for (float fx = x0 + phase; fx < x1 - 1; fx += spacing) {
            final float s = com.createstats.gui.pdu.PduFlowModel.smooth((fx - x0) / span);
            final int cy = Math.round(c0 + (c1 - c0) * s);
            final int px = Math.round(fx);
            if (thick >= 4) {
                g.fill(px - 1, cy - 1, px + 2, cy + 1, dot);
            } else {
                g.fill(px, cy, px + 1, cy + 1, dot);
            }
        }
    }

    private void drawBus(GuiGraphics g, int x, int y, int w, int h, int mx, int my, float partialTick) {
        g.fill(x, y, x + w, y + h, PduTheme.BG_DEEP);
        note("inout.bus", new PduRect(x, y, w, h));
        PduTheme.outline(g, x, y, w, h, PduTheme.STROKE_HOT);
        final float fill = pdu.fillFraction();
        shownBusFill = easeFrom(shownBusFill, fill, partialTick);
        final int inner = h - 4;
        final int fillH = Math.round(inner * Math.max(0.0f, Math.min(1.0f, shownBusFill)));
        final int colour = fill < pdu.reserveFloorFraction() ? PduTheme.WARN : PduTheme.INFO;
        g.fill(x + 2, y + 2 + inner - fillH, x + w - 2, y + 2 + inner, PduTheme.withAlpha(colour, 0x88));
        final int floorY = y + 2 + inner - Math.round(inner * pdu.reserveFloorFraction());
        g.fill(x, floorY, x + w, floorY + 1, PduTheme.TEXT_DIM);
        final int held = pdu.effectiveFloorPctShown();
        final boolean holding = pdu.holdTargetFe() > 0 && held > Math.round(pdu.reserveFloorFraction() * 100.0f);
        if (holding) {
            final int heldY = y + 2 + inner - Math.round(inner * held / 100.0f);
            g.fill(x - 2, heldY, x + w + 2, heldY + 1, PduTheme.WARN);
        }
        if (mx >= x && mx < x + w && my >= y && my < y + h) {
            inOutTips.add(Component.literal("This board's buffer").withStyle(ChatFormatting.WHITE));
            inOutTips.add(Component.literal(Math.round(fill * 100.0f) + "%  " + fmt(pdu.storedEnergy())
                    + " / " + fmt(pdu.capacity()) + " FE").withStyle(ChatFormatting.GRAY));
            inOutTips.add(Component.literal("Reserve floor " + Math.round(pdu.reserveFloorFraction() * 100.0f)
                    + "%: below it only Critical draws").withStyle(ChatFormatting.DARK_GRAY));
            if (holding) {
                inOutTips.add(Component.literal("Holding " + held + "% back for a board upstream ("
                        + fmt(pdu.holdTargetFe()) + " FE)").withStyle(ChatFormatting.GOLD));
            }
        }
    }

    private void flowBox(GuiGraphics g, com.createstats.gui.pdu.PduFlowModel.Node node, int liveFe,
                         int x, int y, int w, int h, boolean source, int mx, int my) {
        if (h <= 0) {
            return;
        }
        if (notes != null) {
            note("inout.box." + node.kind().name().toLowerCase(Locale.ROOT), new PduRect(x, y, w, h));
        }
        final int colour = flowColour(node.kind());
        final boolean hover = mx >= x && mx < x + w && my >= y && my < y + h;
        g.fill(x, y, x + w, y + h, hover ? PduTheme.STROKE : PduTheme.BG_RAISED);
        if (source) {
            g.fill(x + w - 3, y, x + w, y + h, colour);
        } else {
            g.fill(x, y, x + 3, y + h, colour);
        }
        final int tx = source ? x + 3 : x + 6;
        final int textW = w - 9;
        final String label = flowLabel(node.kind());
        final String value = fmt(liveFe) + " FE/t";
        if (h >= 20) {
            g.drawString(font, trimToWidth(label, textW), tx, y + (h - 18) / 2, PduTheme.TEXT, false);
            g.drawString(font, trimToWidth(value, textW), tx, y + (h - 18) / 2 + 10, PduTheme.TEXT_DIM, false);
        } else if (h >= 9) {
            final int vw = font.width(value);
            if (vw + 20 < textW) {
                g.drawString(font, trimToWidth(label, textW - vw - 4), tx, y + (h - 8) / 2, PduTheme.TEXT, false);
                g.drawString(font, value, tx + textW - vw, y + (h - 8) / 2, PduTheme.TEXT_DIM, false);
            } else {
                g.drawString(font, trimToWidth(label, textW), tx, y + (h - 8) / 2, PduTheme.TEXT, false);
            }
        }
        if (hover) {
            inOutTips.add(Component.literal(label + ": " + fmt(liveFe) + " FE/t").withStyle(ChatFormatting.WHITE));
            for (String line : flowExplanation(node.kind(), liveFe)) {
                inOutTips.add(Component.literal(line).withStyle(ChatFormatting.GRAY));
            }
        }
    }

    private String flowLabel(com.createstats.gui.pdu.PduFlowModel.Kind kind) {
        if (kind == com.createstats.gui.pdu.PduFlowModel.Kind.SPARE) {
            return pdu.fillFraction() >= 0.999f ? "Spare, idle" : "Spare, to buffer";
        }
        if (kind == com.createstats.gui.pdu.PduFlowModel.Kind.CONTROLLER && pdu.reserveLinkCount() > 1) {
            return pdu.reserveLinkCount() + " controllers";
        }
        if (kind == com.createstats.gui.pdu.PduFlowModel.Kind.UNPRICED && pdu.upstreamPanelCount() > 0) {
            return "Board upstream";
        }
        return kind.label();
    }

    private List<String> flowExplanation(com.createstats.gui.pdu.PduFlowModel.Kind kind, int fe) {
        final List<String> lines = new ArrayList<>(3);
        switch (kind) {
            case SOLAR -> {
                lines.add("Solar Panels on the feed, at what they make now");
                final int drop = pdu.forecastDropFePerTick();
                if (drop > 0) {
                    lines.add("Forecast: down " + fmt(drop) + " FE/t within the minute");
                    lines.add("The admit budget already plans without it");
                }
            }
            case GENERATORS -> lines.add("Dynamos, burners and alternators the board can price");
            case CONTROLLER -> lines.add("A Battery Controller upstream, beyond what the feed's own generators make");
            case UNPRICED -> {
                if (pdu.upstreamPanelCount() > 0) {
                    lines.add("Fed through a leg of another Power Distribution Unit");
                } else {
                    lines.add("Arriving from sources nothing can price: the observed peak");
                }
            }
            case SOURCE_BUFFERS -> {
                lines.add("Charge the feed's own generators banked earlier, arriving on top");
                lines.add("of what they make now: a dynamo spinning down, a roof emptying");
                lines.add("Not a source: it stops when those buffers are empty");
            }
            case LEG_SOURCES -> lines.add("Generators wired to the legs, counted by what they make");
            case FROM_BUFFER -> lines.add("The branch takes more than the supply; the rest comes out of the buffer");
            case EXPORT -> lines.add("Leaving through the back boss for a board or controller below");
            case CHARGING -> lines.add("Filling banks wired to the legs");
            case UPKEEP -> lines.add("The panel's own running cost, paid before anything else");
            case SPARE -> lines.add(pdu.fillFraction() >= 0.999f
                    ? "Supply not spent: the buffer is full, so it idles"
                    : "Supply not spent: it tops the buffer up");
            case OTHER_IN, OTHER_OUT -> lines.add("Smaller flows, merged so every box stays readable."
                    + " A bigger window shows each one");
            default -> {
                final FePriority tier = kind.tier();
                if (tier != null) {
                    final int i = tier.ordinal();
                    lines.add(fmt(tierServed[i]) + " of " + fmt(tierDraw[i]) + " FE/t served");
                    if (tierDraw[i] > tierServed[i]) {
                        lines.add(fmt(tierDraw[i] - tierServed[i]) + " FE/t of this tier is shed; see below");
                    }
                }
            }
        }
        return lines;
    }

    private void drawShedGhost(GuiGraphics g, int x, int y, int w, int h, int fromX, int fromY, int shedFe,
                               long millis, boolean still, int mx, int my) {
        if (h <= 2) {
            return;
        }
        note("inout.shed", new PduRect(x, y, w, h));
        final int phase = still ? 0 : (int) ((millis / 80L) % 6L);
        for (int dx = x - phase; dx < x + w; dx += 6) {
            final int a = Math.max(x, dx);
            final int b = Math.min(x + w, dx + 3);
            if (b > a) {
                g.fill(a, y, b, y + 1, PduTheme.CRIT);
                g.fill(a, y + h - 1, b, y + h, PduTheme.CRIT);
            }
        }
        g.fill(x, y, x + 1, y + h, PduTheme.CRIT);
        g.fill(x + w - 1, y, x + w, y + h, PduTheme.CRIT);

        for (int dx = fromX; dx < x - 2; dx += 5) {
            g.fill(dx, fromY, Math.min(dx + 2, x - 2), fromY + 1, PduTheme.withAlpha(PduTheme.CRIT, 0x90));
        }
        final String text = "Shed " + fmt(shedFe) + " FE/t";
        g.drawString(font, trimToWidth(text, w - 6), x + 4, y + (h - 8) / 2, PduTheme.CRIT, false);
        if (mx >= x && mx < x + w && my >= y && my < y + h) {
            inOutTips.add(Component.literal("Wanted, and not being sent: " + fmt(shedFe) + " FE/t")
                    .withStyle(ChatFormatting.RED));
            inOutTips.add(Component.literal("Why is on the ladder below").withStyle(ChatFormatting.GRAY));
        }
    }

    private void drawLadder(GuiGraphics g, PduRect bounds, int mx, int my, int shedCount, float partialTick) {
        final PduRect c = PduCard.draw(g, font, bounds, "Why: what the decision was made on");
        note("inout.ladder", bounds);
        final PduMode mode = pdu.mode();
        final com.createstats.gui.pdu.PduFlowModel.Budgets b = pdu.decisionBudgets();
        final int gen = pdu.generationFePerTick();
        final String verdict = com.createstats.gui.pdu.PduFlowModel.verdict(mode, b, gen, shedByReason, shedCount);
        if (mode != PduMode.AUTO) {
            int y = c.y();
            for (String line : wrap(verdict, c.w())) {
                g.drawString(font, line, c.x(), y, PduTheme.TEXT, false);
                y += 10;
            }
            g.drawString(font, trimToWidth("Switch to Active Auto to see a budget.", c.w()), c.x(), y + 2,
                    PduTheme.TEXT_FAINT, false);

            final com.createstats.power.FeBoardDeal.Answer deal = pdu.dealShown();
            if (deal.isPresent() && y + 14 <= c.bottom() - 8) {
                g.drawString(font, trimToWidth(dealSentence(deal), c.w()), c.x(), y + 14,
                        PduTheme.TEXT_DIM, false);
            }
            return;
        }
        int demand = 0;
        for (int fe : tierDraw) {
            demand += fe;
        }
        shownLadder[0] = easeFrom(shownLadder[0], gen, partialTick);
        shownLadder[1] = easeFrom(shownLadder[1], b.admit(), partialTick);
        shownLadder[2] = easeFrom(shownLadder[2], b.run(), partialTick);
        shownLadder[3] = easeFrom(shownLadder[3], b.critical(), partialTick);
        final int scale = com.createstats.gui.pdu.PduFlowModel.ladderScale(b, gen, demand);
        final int labelW = 44;
        final int barX = c.x() + labelW;
        final int barW = Math.max(20, c.w() - labelW);

        final int barA = c.h() >= 110 ? 14 : c.h() >= 80 ? 12 : 9;
        final int barB = barA + 2;
        final int rowA = c.y();
        final int rowB = rowA + barA + 6;
        final boolean still = Ease.reduceMotion();
        final long millis = Util.getMillis();

        g.drawString(font, "Supply", c.x(), rowA + (barA - 8) / 2, PduTheme.TEXT_DIM, false);
        g.fill(barX, rowA, barX + barW, rowA + barA, PduTheme.BG_DEEP);
        note("inout.ladder.supply", new PduRect(barX, rowA, barW, barA));
        final int genPx = com.createstats.gui.pdu.PduFlowModel.ladderX(Math.round(shownLadder[0]), scale, barX, barW);
        g.fill(barX, rowA, genPx, rowA + barA, PduTheme.withAlpha(PduTheme.OK, 0x90));
        if (b.standing() > 0) {
            final int stPx = com.createstats.gui.pdu.PduFlowModel.ladderX(b.standing(), scale, barX, barW);
            hatch(g, barX, rowA, stPx, rowA + barA, PduTheme.TEXT_FAINT, 0);
        }
        if (b.forecastDrop() > 0) {
            final int planPx = com.createstats.gui.pdu.PduFlowModel.ladderX(Math.max(0, gen - b.forecastDrop()),
                    scale, barX, barW);
            hatch(g, planPx, rowA, genPx, rowA + barA, PduTheme.WARN, still ? 0 : (int) ((millis / 120L) % 4L));
        }

        if (b.ride() > 0) {
            final int from = Math.max(0, gen - b.standing());
            final int a = com.createstats.gui.pdu.PduFlowModel.ladderX(from, scale, barX, barW);
            final int z = com.createstats.gui.pdu.PduFlowModel.ladderX(from + b.ride(), scale, barX, barW);
            g.fill(a, rowA + barA + 1, z, rowA + barA + 4, PduTheme.withAlpha(PduTheme.INFO, 0xC0));
            if (mx >= a && mx < z && my >= rowA + barA && my < rowA + barA + 5) {
                inOutTips.add(Component.literal("Reserve ride: " + fmt(b.ride()) + " FE/t")
                        .withStyle(ChatFormatting.WHITE));
                inOutTips.add(Component.literal("What the buffer and banks add to the run budget")
                        .withStyle(ChatFormatting.GRAY));
            }
        }
        if (mx >= barX && mx < genPx && my >= rowA && my < rowA + barA) {
            inOutTips.add(Component.literal("Generation: " + fmt(gen) + " FE/t").withStyle(ChatFormatting.WHITE));
            if (b.standing() > 0) {
                inOutTips.add(Component.literal("Less " + fmt(b.standing())
                        + " FE/t of export and upkeep, paid first (grey)").withStyle(ChatFormatting.GRAY));
            }
            if (b.forecastDrop() > 0) {
                inOutTips.add(Component.literal("Less " + fmt(b.forecastDrop())
                        + " FE/t about to go at dusk (amber): admit plans without it").withStyle(ChatFormatting.GOLD));
            }
        }

        g.drawString(font, "Demand", c.x(), rowB + (barB - 8) / 2, PduTheme.TEXT_DIM, false);
        g.fill(barX, rowB, barX + barW, rowB + barB, PduTheme.BG_DEEP);
        note("inout.ladder.demand", new PduRect(barX, rowB, barW, barB));
        final List<com.createstats.gui.pdu.PduFlowModel.TierSpan> spans =
                com.createstats.gui.pdu.PduFlowModel.tierSpans(tierDraw, tierServed);
        for (com.createstats.gui.pdu.PduFlowModel.TierSpan span : spans) {
            final int colour = PduGlyphs.tierColour(span.tier());
            final int s0 = com.createstats.gui.pdu.PduFlowModel.ladderX(span.start(), scale, barX, barW);
            final int s1 = com.createstats.gui.pdu.PduFlowModel.ladderX(span.servedEnd(), scale, barX, barW);
            final int s2 = com.createstats.gui.pdu.PduFlowModel.ladderX(span.end(), scale, barX, barW);
            g.fill(s0, rowB, s1, rowB + barB, PduTheme.withAlpha(colour, 0xC0));
            if (s2 > s1) {
                hatch(g, s1, rowB, s2, rowB + barB, colour, still ? 0 : (int) ((millis / 160L) % 4L));
                g.fill(s1, rowB, s2, rowB + 1, PduTheme.CRIT);
                g.fill(s1, rowB + barB - 1, s2, rowB + barB, PduTheme.CRIT);
            }
            if (s2 > s0) {
                g.fill(s2 - 1, rowB, s2, rowB + barB, PduTheme.BG_DEEP);
            }
            final String name = span.tier().getDisplayName();
            if (barB >= 11 && s2 - s0 >= font.width(name) + 6) {
                g.drawString(font, name, s0 + 3, rowB + (barB - 8) / 2, PduTheme.TEXT, false);
            }
            if (mx >= s0 && mx < s2 && my >= rowB && my < rowB + barB) {
                inOutTips.add(Component.literal(span.tier().getDisplayName() + ": wants " + fmt(span.demand())
                        + " FE/t").withStyle(ChatFormatting.WHITE));
                inOutTips.add(Component.literal("Served " + fmt(span.servedEnd() - span.start()) + ", shed "
                        + fmt(span.shed())).withStyle(ChatFormatting.GRAY));
                inOutTips.add(Component.literal("Starts at " + fmt(span.start())
                        + " FE/t: every tier above it is admitted first").withStyle(ChatFormatting.DARK_GRAY));
            }
        }

        final int markEnd = rowB + barB + 2;
        ladderMarker(g, Math.round(shownLadder[1]), scale, barX, barW, rowA - 2, markEnd, PduTheme.ACCENT,
                "Admit", "A shed device must fit under this to come back", mx, my);
        ladderMarker(g, Math.round(shownLadder[2]), scale, barX, barW, rowA - 2, markEnd, PduTheme.TEXT,
                "Run", "A running device keeps drawing while it fits under this", mx, my);
        if (b.critical() > b.run()) {
            ladderMarker(g, Math.round(shownLadder[3]), scale, barX, barW, rowA - 2, markEnd,
                    PduGlyphs.tierColour(FePriority.CRITICAL), "Critical",
                    "Critical may ride the reserve down to here", mx, my);
        }
        if (b.ceiling() > 0 && b.ceiling() < scale) {
            ladderMarker(g, b.ceiling(), scale, barX, barW, rowA - 2, markEnd, PduTheme.TEXT_FAINT,
                    "Wire", "The legs' combined rating: nothing passes it", mx, my);
        }

        int y = markEnd + 4;
        final int verdictColour = shedCount > 0 || b.belowFloor() ? PduTheme.WARN : PduTheme.OK;
        for (String line : wrap(verdict, c.w())) {
            if (y > c.bottom() - 8) {
                return;
            }
            g.drawString(font, line, c.x(), y, verdictColour, false);
            y += 10;
        }
        if (y <= c.bottom() - 8) {
            int lx = c.x();
            lx = legendItem(g, lx, y, PduTheme.withAlpha(PduTheme.OK, 0x90), "Gen " + fmt(gen), c.right());
            if (b.forecastDrop() > 0) {
                lx = legendItem(g, lx, y, PduTheme.WARN, "Falling " + fmt(b.forecastDrop()), c.right());
            }
            lx = legendItem(g, lx, y, PduTheme.ACCENT, "Admit " + fmt(b.admit()), c.right());
            lx = legendItem(g, lx, y, PduTheme.TEXT, "Run " + fmt(b.run()), c.right());
            if (b.critical() > b.run()) {
                lx = legendItem(g, lx, y, PduGlyphs.tierColour(FePriority.CRITICAL),
                        "Crit " + fmt(b.critical()), c.right());
            }
            legendItem(g, lx, y, PduTheme.TEXT_FAINT, "Wire " + fmt(b.ceiling()), c.right());
            y += 12;
        }
        y = drawDeal(g, c, y, scale, barX, barW, barA, mx, my);
        final List<String> notes = new ArrayList<>(3);
        if (b.linked() && !pdu.dealShown().isPresent()) {
            notes.add("Budgets come from the Battery Controller upstream and its band.");
        }
        final int shortfall = pdu.outlookShortfallFe();
        if (shortfall > 0) {
            notes.add("Night outlook: " + fmt(shortfall) + " FE short before supply is back; asking the boards on its legs to hold it.");
        }
        if (pdu.holdTargetFe() > 0) {
            notes.add("Holding " + fmt(pdu.holdTargetFe()) + " FE back for a board upstream.");
        }
        if (b.wireLimited()) {
            notes.add("The legs are the limit, not the supply.");
        }
        for (String note : notes) {
            for (String line : wrap(note, c.w())) {
                if (y > c.bottom() - 8) {
                    return;
                }
                g.drawString(font, line, c.x(), y, PduTheme.TEXT_DIM, false);
                y += 10;
            }
        }
    }

    private int drawDeal(GuiGraphics g, PduRect c, int y, int scale, int barX, int barW, int barH,
                         int mx, int my) {
        final com.createstats.power.FeBoardDeal.Answer deal = pdu.dealShown();
        if (!deal.isPresent()) {
            return y;
        }
        final int h = Math.max(7, barH - 4);
        if (y + h * 2 + 4 + 10 > c.bottom()) {
            if (y <= c.bottom() - 8) {
                g.drawString(font, trimToWidth(dealSentence(deal), c.w()), c.x(), y,
                        deal.shortfall() > 0 ? PduTheme.WARN : PduTheme.TEXT_DIM, false);
                y += 10;
            }
            return y;
        }
        final com.createstats.power.FeBoardDeal.Request asked = deal.asked();
        y += 2;
        dealBar(g, c, "Asked", y, h, scale, barX, barW, asked.critical(), asked.running(), asked.restore(),
                true, mx, my, deal);
        y += h + 3;
        dealBar(g, c, "Given", y, h, scale, barX, barW, deal.critical(), deal.running(), deal.restore(),
                false, mx, my, deal);
        y += h + 4;
        if (y <= c.bottom() - 8) {
            g.drawString(font, trimToWidth(dealSentence(deal), c.w()), c.x(), y,
                    deal.shortfall() > 0 ? PduTheme.WARN : PduTheme.OK, false);
            y += 10;
        }
        return y;
    }

    private void dealBar(GuiGraphics g, PduRect c, String label, int y, int h, int scale, int barX, int barW,
                         int critical, int running, int restore, boolean asked, int mx, int my,
                         com.createstats.power.FeBoardDeal.Answer deal) {
        g.drawString(font, label, c.x(), y + (h - 8) / 2, PduTheme.TEXT_DIM, false);
        g.fill(barX, y, barX + barW, y + h, PduTheme.BG_DEEP);
        final int critColour = PduGlyphs.tierColour(FePriority.CRITICAL);
        final int x0 = barX;
        final int x1 = com.createstats.gui.pdu.PduFlowModel.ladderX(critical, scale, barX, barW);
        final int x2 = com.createstats.gui.pdu.PduFlowModel.ladderX(critical + running, scale, barX, barW);
        final int x3 = com.createstats.gui.pdu.PduFlowModel.ladderX(critical + running + restore, scale, barX, barW);
        g.fill(x0, y, x1, y + h, PduTheme.withAlpha(critColour, 0xC0));
        g.fill(x1, y, x2, y + h, PduTheme.withAlpha(PduTheme.INFO, 0xB0));
        if (asked) {
            hatch(g, x2, y, x3, y + h, PduTheme.ACCENT, 0);
        } else {
            g.fill(x2, y, x3, y + h, PduTheme.withAlpha(PduTheme.ACCENT, 0xB0));
        }
        final String total = fmt(critical + running + restore);
        final int tx = Math.min(barX + barW - font.width(total) - 2, x3 + 3);
        g.drawString(font, total, tx, y + (h - 8) / 2, PduTheme.TEXT, false);
        if (mx >= barX && mx < barX + barW && my >= y && my < y + h) {
            inOutTips.add(Component.literal((asked ? "Asked " : "Given ") + (deal.own() ? "its own banks" : "the controller")
                    + ": " + fmt(critical + running + restore) + " FE/t").withStyle(ChatFormatting.WHITE));
            inOutTips.add(Component.literal("Critical " + fmt(critical) + ", running " + fmt(running)
                    + ", restore " + fmt(restore)).withStyle(ChatFormatting.GRAY));
            inOutTips.add(Component.literal(asked
                    ? "Costs, not draws: shedding moves a device from running to restore"
                    : "The board admits against this and nothing else").withStyle(ChatFormatting.DARK_GRAY));
            if (!asked && deal.spare() > 0) {
                inOutTips.add(Component.literal("Spare beyond this: " + fmt(deal.spare())
                        + " FE/t, the headroom restores are judged on").withStyle(ChatFormatting.DARK_GRAY));
            }
        }
    }

    private static String dealSentence(com.createstats.power.FeBoardDeal.Answer deal) {
        final String who = deal.own() ? "Own banks (no controller)" : "Battery Controller";
        final String soc = deal.socPct() >= 0 ? ", banks " + deal.socPct() + "%" : "";
        final String state = who + ": " + deal.stance().label() + soc;
        if (deal.shortfall() <= 0) {
            return state + ". Granted in full.";
        }
        return state + ". Short " + fmt(deal.shortfall()) + " FE/t: " + deal.reason().sentence()
                + (deal.own() ? ". A controller lets you set this band." : ".");
    }

    private void ladderMarker(GuiGraphics g, int value, int scale, int barX, int barW, int y0, int y1, int colour,
                              String name, String meaning, int mx, int my) {
        final int x = Math.min(barX + barW - 2, com.createstats.gui.pdu.PduFlowModel.ladderX(value, scale, barX, barW));
        g.fill(x, y0, x + 2, y1, colour);
        g.fill(x - 1, y0, x + 3, y0 + 2, colour);
        if (notes != null) {
            note("inout.marker." + name.toLowerCase(Locale.ROOT), new PduRect(x - 1, y0, 4, y1 - y0));
        }
        if (mx >= x - 2 && mx < x + 4 && my >= y0 && my < y1) {
            inOutTips.add(Component.literal(name + " budget: " + fmt(value) + " FE/t").withStyle(ChatFormatting.WHITE));
            inOutTips.add(Component.literal(meaning).withStyle(ChatFormatting.GRAY));
            if (value >= scale) {
                inOutTips.add(Component.literal("Off the end of the scale").withStyle(ChatFormatting.DARK_GRAY));
            }
        }
    }

    private int legendItem(GuiGraphics g, int x, int y, int colour, String text, int right) {
        final int w = 8 + font.width(text) + 8;
        if (x + w - 8 > right) {
            return x;
        }
        g.fill(x, y + 1, x + 5, y + 6, colour);
        g.drawString(font, text, x + 8, y, PduTheme.TEXT_DIM, false);
        return x + w;
    }

    private static void hatch(GuiGraphics g, int x0, int y0, int x1, int y1, int colour, int phase) {
        if (x1 <= x0) {
            return;
        }
        final int line = PduTheme.withAlpha(colour, 0xB0);
        for (int x = x0 + (phase % 4); x < x1; x += 4) {
            g.fill(x, y0, Math.min(x + 1, x1), y1, line);
        }
    }

    private void drawChain(GuiGraphics g, PduRect area, float partialTick) {
        final int gaugeH = 30;
        drawChainGauge(g, new PduRect(area.x(), area.y(), area.w(), gaugeH), partialTick);

        final PduRect card = PduCard.draw(g, font,
                new PduRect(area.x(), area.y() + gaugeH + PduTheme.GAP, area.w(),
                        Math.max(20, area.bottom() - area.y() - gaugeH - PduTheme.GAP)),
                "Upstream, here, downstream");
        final List<com.createstats.gui.pdu.PduDerivation.Row> rows =
                com.createstats.gui.pdu.PduChain.rows(chainUpstream(), chainBoard(), chainDownstream());

        final int available = (card.bottom() - card.y()) / 10;
        final List<com.createstats.gui.pdu.PduDerivation.Row> shown =
                com.createstats.gui.pdu.PduDerivation.fit(rows, available);
        int y = card.y();
        for (com.createstats.gui.pdu.PduDerivation.Row row : shown) {
            if (y > card.bottom() - 8) {
                break;
            }
            y = drawDerivationRow(g, card, row, y);
        }
    }

    private float shownChainFill = -1.0f;

    private void drawChainGauge(GuiGraphics g, PduRect r, float partialTick) {
        PduTheme.panel(g, r.x(), r.y(), r.w(), r.h());
        final boolean linked = pdu.isReserveLinked();
        final int stored = linked ? pdu.linkedReserveStored() : pdu.storedEnergy();
        final int capacity = linked ? pdu.linkedReserveCapacity() : pdu.capacity();
        final float fill = capacity <= 0 ? 0.0f : Math.min(1.0f, stored / (float) capacity);
        shownChainFill = shownChainFill < 0.0f ? fill
                : Ease.ease(shownChainFill, fill, 0.18f, partialTick);

        final com.createstats.power.FeReserveReport.Stance stance = pdu.linkedStance();
        final int colour = !linked ? PduTheme.INFO : stance == null ? PduTheme.INFO : switch (stance) {
            case SURPLUS -> PduTheme.OK;
            case NOMINAL -> PduTheme.INFO;
            case CONSERVE -> PduTheme.WARN;
            case CRITICAL -> PduTheme.CRIT;
        };
        final String title = linked
                ? (pdu.reserveLinkCount() > 1 ? pdu.reserveLinkCount() + " controllers upstream"
                        : "Battery Controller upstream")
                : "This board's own buffer";
        final String value = Math.round(fill * 100.0f) + "%   " + fmt(stored) + " / " + fmt(capacity)
                + " FE";
        g.drawString(font, title, r.x() + 5, r.y() + 4, PduTheme.TEXT, false);
        g.drawString(font, value, r.right() - 5 - font.width(value), r.y() + 4,
                PduTheme.TEXT_DIM, false);

        final float notch = linked ? pdu.linkedBandFloorPct() / 100.0f : pdu.reserveFloorFraction();
        PduGauge.drawHorizontal(g, r.x() + 5, r.y() + 16, r.w() - 10, 9, shownChainFill, notch, colour);
    }

    private com.createstats.gui.pdu.PduChain.Upstream chainUpstream() {
        final com.createstats.power.FeReserveReport.Stance stance = pdu.linkedStance();
        final boolean linked = pdu.isReserveLinked() && stance != null;
        return new com.createstats.gui.pdu.PduChain.Upstream(linked,
                Math.max(1, pdu.reserveLinkCount()), pdu.linkedReserveSocPct(),
                pdu.linkedReserveStored(), pdu.linkedReserveCapacity(),
                linked ? stance.label() : "", linked ? stance.blurb() : "",
                linked ? pdu.linkedGeneration() : 0, pdu.linkedSustainedDraw(),
                pdu.linkedCriticalDraw(), pdu.linkedChargeClaim(),
                pdu.linkedBandFloorPct(), pdu.linkedBandCeilingPct(),
                pdu.reserveTankCount(), pdu.reserveStored(), pdu.reserveCapacity(),
                pdu.upstreamSourceCount(), pdu.foreignFeedMisplaced(),
                pdu.upstreamPanelCount(), pdu.upstreamPanelFeed(),

                pdu.generationFePerTick());
    }

    private com.createstats.gui.pdu.PduChain.Board chainBoard() {
        final FeGridSolver.BranchResult branch = pdu.lastBranchResult();
        return new com.createstats.gui.pdu.PduChain.Board(pdu.measuredInputFePerTick(),
                pdu.storedEnergy(), pdu.capacity(), Math.round(pdu.reserveFloorFraction() * 100.0f),
                pdu.budgetFePerTick(), pdu.branchDemandFePerTick(), branch.served(), branch.shed(),
                pdu.isShedFromUpstream(), pdu.upstreamLock() == FeLock.FORCE_OFF);
    }

    private com.createstats.gui.pdu.PduChain.Downstream chainDownstream() {
        final List<com.createstats.gui.pdu.PduChain.Panel> panels = new ArrayList<>(2);
        if (minecraft != null && minecraft.level != null) {
            for (BranchDevice d : pdu.roster()) {
                if (d.isStorage() || !minecraft.level.getBlockState(d.pos())
                        .is(com.createstats.registry.ModBlocks.POWER_DISTRIBUTION_UNIT.get())) {
                    continue;
                }
                panels.add(new com.createstats.gui.pdu.PduChain.Panel(
                        com.createstats.power.PduDeviceIds.label(d.name(), d.id(), false), d.leg(),
                        d.draw(), d.priority().getDisplayName(), d.reason().description()));
            }
        }
        return new com.createstats.gui.pdu.PduChain.Downstream(panels,
                pdu.downstreamControllerCount(), pdu.downstreamControllerDraw(),
                pdu.backExportFePerTick(), pdu.firstStrayControllerLeg());
    }

    @org.jetbrains.annotations.Nullable
    private com.createstats.gui.pdu.PduDerivation.Link reserveLink() {
        final com.createstats.power.FeReserveReport.Stance stance = pdu.linkedStance();
        if (!pdu.isReserveLinked() || stance == null) {
            return null;
        }
        return new com.createstats.gui.pdu.PduDerivation.Link(pdu.linkedReserveSocPct(),
                stance.label(), stance.blurb(), stance.isRationing(), pdu.linkedSustainedDraw(),
                pdu.linkedBandFloorPct(), pdu.linkedBandCeilingPct(), pdu.linkedChargeClaim(),
                Math.max(1, pdu.reserveLinkCount()));
    }

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

                if (!row.note().isEmpty()) {
                    final int noteX = c.x() + (term ? 16 : 8);
                    g.drawString(font, trimToWidth(row.note(), c.right() - noteX), noteX, y,
                            PduTheme.TEXT_FAINT, false);
                    y += 9;
                }
                return y + 1;
            }
        }
    }

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

    private List<Component> deviceTooltip(BranchDevice device) {
        final List<Component> lines = new ArrayList<>(6);
        lines.add(Component.literal(device.label()).withStyle(ChatFormatting.WHITE));
        if (device.isStorage()) {
            lines.add(Component.literal(String.format(Locale.ROOT, "%s,  %,d / %,d FE",
                    PduLegRow.name(device.leg()), device.stored(), device.capacity()))
                    .withStyle(ChatFormatting.GRAY));
            lines.add(Component.literal(device.reason().description()).withStyle(ChatFormatting.AQUA));
            lines.add(Component.literal(""));
            lines.add(Component.literal("A battery has no tier and is never shed")
                    .withStyle(ChatFormatting.DARK_GRAY));
            return lines;
        }
        lines.add(Component.literal(String.format(Locale.ROOT, "%s tier,  %s,  %,d FE/t",
                device.priority().getDisplayName(), PduLegRow.name(device.leg()), device.draw()))
                .withStyle(ChatFormatting.GRAY));

        if (device.cost() != device.draw()) {
            lines.add(Component.literal(String.format(Locale.ROOT, "budgeted %,d FE/t", device.cost()))
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        final PduReason reason = device.reason();
        final ChatFormatting reasonColour = switch (reason) {
            case RUNNING -> ChatFormatting.GREEN;
            case LOCKED_ON -> ChatFormatting.AQUA;
            case LOCKED_OFF -> ChatFormatting.DARK_GRAY;
            case BREAKER_OPEN -> ChatFormatting.RED;
            case AWAITING_DWELL -> ChatFormatting.YELLOW;
            default -> ChatFormatting.GOLD;
        };
        lines.add(Component.literal(reason.description()).withStyle(reasonColour));
        lines.add(Component.literal(""));
        lines.add(Component.literal("Click to select    Left / Right sets tier    L locks")
                .withStyle(ChatFormatting.DARK_GRAY));
        return lines;
    }

    @Override
    public void requestSetTier(BranchDevice device, FePriority tier) {
        applyTier(device, tier);
    }

    @Override
    public void requestSetLock(BranchDevice device, FeLock lock) {
        applyLock(device, lock);
    }

    @Override
    public void requestSort(PduTableModel.Sort column) {
        if (column == sortKey) {
            sortReversed = !sortReversed;
        } else {
            sortKey = column;
            sortReversed = false;
        }
        setFocus(Focus.SORT);
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
        sendSet(positions(table.selectedLoads()), PduSetPacket.ACTION_PRIORITY, tierOrdinal);
    }

    private void bulkLockOff() {
        final List<BranchDevice> selected = table.selectedLoads();
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

    private void bulkSetLock(FeLock lock) {
        sendSet(positions(table.selectedLoads()), PduSetPacket.ACTION_LOCK, lock.ordinal());
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
        if (view == View.CHAIN && button == 0 && chainToggle.contains(mouseX, mouseY)) {
            chainDetails = !chainDetails;
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
        if (legChipBounds.contains(mx, my)) {
            legFilter = PduTableModel.Filter.ANY_LEG;
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
                    case 101 -> bulkSetLock(FeLock.NONE);
                    case 102 -> table.clearSelection();
                    case 103 -> bulkSetLock(FeLock.FORCE_ON);
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
            if (!search.isEmpty()) {
                search.clear();
                return true;
            }
            if (tierFilter != null) {
                tierFilter = null;
                return true;
            }
            if (legFilter != PduTableModel.Filter.ANY_LEG) {
                legFilter = PduTableModel.Filter.ANY_LEG;
                return true;
            }
            if (shedOnly) {
                shedOnly = false;
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

        if (keyCode >= GLFW.GLFW_KEY_1
                && keyCode < GLFW.GLFW_KEY_1 + Math.min(9, View.values().length)) {
            setView(View.values()[keyCode - GLFW.GLFW_KEY_1]);
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_R && view == View.FLOW) {
            flowCamera.reset();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_M) {
            send(pdu.getBlockPos(), PduActionPacket.ACTION_CYCLE_MODE);
            return true;
        }

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
                final PduTableModel.Sort[] columns = PduTableModel.Sort.values();
                sortKey = columns[Mth.clamp(sortKey.ordinal() + horizontalStep(keyCode),
                        0, columns.length - 1)];
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

        if (view == View.HISTORY && horizontalStep(keyCode) != 0) {
            final int next = Mth.clamp(historyRange + horizontalStep(keyCode), 0, DAY_RANGE);
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
        Focus next = focus;
        for (int i = 0; i < order.length; i++) {
            next = order[(next.ordinal() + (backward ? order.length - 1 : 1)) % order.length];
            if (isFocusReachable(next)) {
                break;
            }
        }
        setFocus(next);
    }

    private boolean isFocusReachable(Focus stop) {
        if (stop == Focus.TABLE) {
            return true;
        }
        if (!isTableView()) {
            return false;
        }

        return stop == Focus.SORT || table.selectionSize() < 2;
    }

    private void setFocus(Focus next) {
        this.focus = next;
        updateFocusFlags();
    }

    private void updateFocusFlags() {
        search.setFocused(focus == Focus.SEARCH);
        groupSeg.setFocused(focus == Focus.GROUP);
        table.setHeaderFocused(focus == Focus.SORT);
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
            if (next == View.HISTORY && !specimen) {
                subscribeHistory();
            }
        }
    }

    private void setTierFilter(FePriority tier) {
        tierFilter = tier;
        setView(View.LOADS);
        setFocus(Focus.TABLE);
    }

    private void setLegFilter(int leg) {
        legFilter = legFilter == leg ? PduTableModel.Filter.ANY_LEG : leg;
        setView(View.LOADS);
        setFocus(Focus.TABLE);
        if (legFilter != PduTableModel.Filter.ANY_LEG) {
            toast("Showing " + PduLegRow.name(leg) + " only", PduToast.Severity.INFO);
        }
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
