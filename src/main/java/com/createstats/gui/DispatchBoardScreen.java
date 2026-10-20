package com.createstats.gui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import com.createstats.dispatch.BoardHeaderState;
import com.createstats.dispatch.DispatchRowEntry;
import com.createstats.dispatch.DispatchRowState;
import com.createstats.network.FreightManifestDeliveryOrderActionPacket;
import com.createstats.network.RequestDispatchBoardStatePacket;
import com.createstats.train.manifest.DeliveryOrderAction;
import com.createstats.train.manifest.DeliveryOrderBoardView;
import com.createstats.train.manifest.DeliveryOrderHistoryEntry;
import com.createstats.train.manifest.DeliveryOrderHistoryType;
import com.createstats.train.manifest.DeliveryOrderLineView;
import com.createstats.train.manifest.DeliveryOrderResetMode;
import com.createstats.train.manifest.FreightManifest;
import com.createstats.train.manifest.FreightManifestLogic;
import com.createstats.train.manifest.FreightManifestResourceKey;
import com.createstats.train.manifest.FreightStationCargoView;
import com.createstats.train.manifest.FreightStationLink;
import com.createstats.train.manifest.ManifestCargoKind;
import com.createstats.train.manifest.ManifestOperation;
import com.createstats.train.manifest.StationTrainRoster;
import com.createstats.train.manifest.TrainSummarySnapshot;
import com.createstats.train.manifest.TrainVisitRecord;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

// lit panel on a wall, iron and brass. the clipboard is paper
public class DispatchBoardScreen extends Screen {
    private static final int ROW_H = 30;

    private static final int ACTIONS_W = 124;
    private static final int CARGO_ROW_H = 17;
    private static final int MAX_BOTTOM_ROWS = 4;

    private static final int MIN_ORDER_ROWS = 2;
    private static final int POLL_INTERVAL_TICKS = 5;

    private enum Page {
        LIVE("Live board"),
        UPCOMING("Upcoming trains"),
        HISTORY("History");

        private final String label;

        Page(String label) {
            this.label = label;
        }

        Page next() {
            return values()[(this.ordinal() + 1) % values().length];
        }
    }

    private enum Filter {
        OPEN("Needs work"),
        DONE("Finished"),
        ALL("All orders");

        private final String label;

        Filter(String label) {
            this.label = label;
        }

        Filter next() {
            return values()[(this.ordinal() + 1) % values().length];
        }
    }

    private final BlockPos blockPos;
    private final FreightStationLink stationLink;
    private final boolean canEdit;
    private FreightManifest manifest;
    private FreightStationCargoView cargoView;
    private DeliveryOrderBoardView deliveryBoard;
    private StationTrainRoster stationRoster;

    private BoardHeaderState headerState;
    private List<DispatchRowEntry> allRows = new ArrayList<>();
    private List<DispatchRowEntry> rows = new ArrayList<>();
    private List<CargoEntry> cargoEntries = new ArrayList<>();
    private int openCount;
    private int doneCount;
    private UUID nextLineId;

    private int panelLeft, panelTop, panelWidth, panelHeight;
    private int faceLeft, faceTop, faceRight, faceBottom;
    private int innerLeft, innerRight;
    private int headerTop, headerBottom;
    private int statusTop, statusBottom;
    private int rowsTop, rowsBottom;
    private int cargoLeft, cargoRight, cargoTop, cargoBottom;
    private int histLeft, histRight, histTop, histBottom;
    private int footerTop;
    private int pageTabY;
    private final int[] pageTabX = new int[Page.values().length];
    private final int[] pageTabW = new int[Page.values().length];
    private int visibleRows = 6;

    private int bottomRows = MAX_BOTTOM_ROWS;
    private boolean compact;
    private final int[] chipX = new int[Filter.values().length];
    private final int[] chipW = new int[Filter.values().length];
    private int chipY;

    private Filter filter = Filter.OPEN;
    private int selected = -1;
    private int dispatchScroll;
    private int cargoScroll;
    private int historyScroll;
    private int visitScroll;
    private int upcomingScroll;
    private int upcomingCargoScroll;
    private int upcomingSelected;
    private int pollCooldown;
    private InkButton hideDoneButton;
    private InkButton showHiddenButton;
    private InkButton resetDoneButton;
    private Page page = Page.LIVE;

    private float frameTime;
    private float frameDt;
    private float lastFrameTime = Float.NaN;
    private float animScrollPx;
    private float bannerProgress;
    private float refreshFlash;
    private float selectionY = Float.NaN;
    private int dataSignature = Integer.MIN_VALUE;
    private final Map<UUID, Float> rowProgressAnim = new HashMap<>();
    private final Map<UUID, Float> rowHoverAnim = new HashMap<>();
    private final Map<UUID, UiFx.RateTracker> rates = new HashMap<>();
    private final Map<String, UiFx.Rolling> counters = new HashMap<>();

    public DispatchBoardScreen(BlockPos blockPos, FreightStationLink stationLink, FreightManifest manifest,
                               FreightStationCargoView cargoView, DeliveryOrderBoardView deliveryBoard,
                               StationTrainRoster stationRoster, boolean canEdit) {
        super(Component.literal("Dispatch Board"));
        this.blockPos = blockPos;
        this.stationLink = stationLink.copy();
        this.manifest = manifest.copy();
        this.canEdit = canEdit;
        this.cargoView = cargoView == null ? FreightStationCargoView.unavailable("Waiting for cargo data...") : cargoView;
        this.deliveryBoard = deliveryBoard == null ? DeliveryOrderBoardView.empty(0L, "Waiting for delivery data...") : deliveryBoard;
        this.stationRoster = stationRoster == null ? StationTrainRoster.empty(0L) : stationRoster.copy();
        this.rebuildDerivedState();
        this.dataSignature = this.computeDataSignature();
        this.bannerProgress = this.targetBannerProgress();
        for (DispatchRowEntry row : this.rows) {
            this.rowProgressAnim.put(row.getLineView().getLineId(), row.getProgress());
        }
    }

    @Override
    protected void init() {
        super.init();
        this.clearWidgets();
        this.panelWidth = Math.min(this.width - 32, 660);
        this.panelHeight = Math.min(this.height - 32, 500);
        this.panelLeft = (this.width - this.panelWidth) / 2;
        this.panelTop = (this.height - this.panelHeight) / 2;
        this.layout();
        this.addFooterButtons();
    }

    @Override
    protected void rebuildWidgets() {
        this.init();
    }

    @Override
    public void tick() {
        super.tick();
        if (this.stationLink.isLinked() && --this.pollCooldown <= 0) {
            this.pollCooldown = POLL_INTERVAL_TICKS;
            PacketDistributor.sendToServer(new RequestDispatchBoardStatePacket(this.blockPos));
        }
    }

    private void layout() {
        this.faceLeft = this.panelLeft + 4;
        this.faceTop = this.panelTop + 4;
        this.faceRight = this.panelLeft + this.panelWidth - 4;
        this.faceBottom = this.panelTop + this.panelHeight - 4;
        this.innerLeft = this.faceLeft + 10;
        this.innerRight = this.faceRight - 10;

        this.headerTop = this.faceTop + 5;
        this.headerBottom = this.headerTop + 22;
        this.pageTabY = this.headerBottom + 5;
        int tabX = this.innerLeft;
        for (Page value : Page.values()) {
            int i = value.ordinal();
            int countW = value == Page.UPCOMING
                    ? this.stationRoster.getAssociatedTrainCount()
                    : value == Page.HISTORY ? this.deliveryBoard.getHistoryEntries().size() : -1;
            String label = countW >= 0 ? value.label + " " + countW : value.label;
            this.pageTabW[i] = Math.max(74, this.font.width(label) + 14);
            this.pageTabX[i] = tabX;
            tabX += this.pageTabW[i] + 4;
        }
        this.statusTop = this.pageTabY + 17;
        this.statusBottom = this.statusTop + 56;

        this.footerTop = this.faceBottom - 8 - 20;
        this.chipY = this.statusBottom + 8;
        this.rowsTop = this.chipY + 13;

        int forLists = this.footerTop - 9 - 12 - this.rowsTop;
        this.bottomRows = Math.max(0, Math.min(MAX_BOTTOM_ROWS,
            (forLists - MIN_ORDER_ROWS * ROW_H) / CARGO_ROW_H));
        int ordersLimit;
        if (this.bottomRows > 0) {
            this.cargoBottom = this.footerTop - 9;
            this.cargoTop = this.cargoBottom - this.bottomRows * CARGO_ROW_H;
            ordersLimit = this.cargoTop - 12;
        } else {
            this.cargoBottom = this.footerTop - 8;
            this.cargoTop = this.cargoBottom;
            ordersLimit = this.footerTop - 8;
        }
        int midX = this.innerLeft + (this.innerRight - this.innerLeft) / 2 - 4;
        this.cargoLeft = this.innerLeft;
        this.cargoRight = midX;
        this.histLeft = midX + 8;
        this.histRight = this.innerRight;
        this.histTop = this.cargoTop;
        this.histBottom = this.cargoBottom;

        this.visibleRows = Math.max(1, (ordersLimit - this.rowsTop) / ROW_H);
        this.rowsBottom = this.rowsTop + this.visibleRows * ROW_H;

        this.compact = false;
        for (int attempt = 0; attempt < 2; attempt++) {
            int x = this.innerRight;
            for (int i = Filter.values().length - 1; i >= 0; i--) {
                String label = this.chipLabel(Filter.values()[i]);
                int w = UiFx.chipWidth(this.font, label);
                x -= w;
                this.chipX[i] = x;
                this.chipW[i] = w;
                x -= 4;
            }
            if (x >= this.innerLeft + this.font.width("ORDERS") + 12 || this.compact) {
                break;
            }
            this.compact = true;
        }
    }

    private void addFooterButtons() {
        int y = this.footerTop;
        int x = this.innerLeft;
        this.hideDoneButton = null;
        this.showHiddenButton = null;
        this.resetDoneButton = null;
        if (this.canEdit && this.page == Page.LIVE) {
            this.hideDoneButton = this.addRenderableWidget(InkButton.of(x, y, 84, 20, "Hide Done",
                () -> this.sendBulk(DeliveryOrderAction.HIDE_COMPLETED)));
            x += 88;
            this.showHiddenButton = this.addRenderableWidget(InkButton.of(x, y, 92, 20, "Show Hidden",
                () -> this.sendBulk(DeliveryOrderAction.SHOW_HIDDEN)));
            x += 96;
            this.resetDoneButton = this.addRenderableWidget(InkButton.of(x, y, 88, 20, "Reset Done",
                () -> this.sendBulk(DeliveryOrderAction.RESET_COMPLETED)));
        }
        this.addRenderableWidget(InkButton.primary(this.innerRight - 76, y, 76, 20, "Close", this::onClose));
        this.updateFooterState();
    }

    private void updateFooterState() {
        if (this.hideDoneButton != null) {
            this.hideDoneButton.active = this.countState(DispatchRowState.COMPLETE) > 0;
        }
        if (this.showHiddenButton != null) {
            this.showHiddenButton.active = this.hasRevealableHiddenOrders();
        }
        if (this.resetDoneButton != null) {
            this.resetDoneButton.active = this.doneCount > 0;
        }
    }

    private void sendBulk(DeliveryOrderAction action) {
        this.playClack(1.0f);
        PacketDistributor.sendToServer(new FreightManifestDeliveryOrderActionPacket(this.blockPos, null, action));
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.frameTime = this.animationTime(partialTick);
        this.frameDt = Float.isNaN(this.lastFrameTime) ? 0f
            : Math.max(0f, Math.min(4f, this.frameTime - this.lastFrameTime));
        this.lastFrameTime = this.frameTime;
        this.updateAnimations(this.frameDt);

        this.renderBackdrop(g);
        this.renderChassis(g);
        this.renderHeaderRail(g);
        this.renderPageTabs(g, mouseX, mouseY);
        switch (this.page) {
            case LIVE -> {
                this.renderBerthStrip(g);
                this.renderFilterRow(g, mouseX, mouseY);
                this.renderOrders(g, mouseX, mouseY);
                this.renderCargoLedger(g, mouseX, mouseY);
                this.renderEvents(g, mouseX, mouseY);
            }
            case UPCOMING -> this.renderUpcomingPage(g, mouseX, mouseY);
            case HISTORY -> this.renderHistoryPage(g, mouseX, mouseY);
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.level.getBlockEntity(this.blockPos)
                instanceof com.createstats.blockentity.DispatchBoardBE be) {
            com.createstats.power.client.FeStatusWidget.draw(g, this.font, this.width - 130, 8, 120,
                be.getControllerBE().feConsumer());
        }

        super.render(g, mouseX, mouseY, partialTick);
        this.renderTooltips(g, mouseX, mouseY);
    }

    private void renderPageTabs(GuiGraphics g, int mouseX, int mouseY) {
        for (Page value : Page.values()) {
            int i = value.ordinal();
            int x = this.pageTabX[i];
            int w = this.pageTabW[i];
            boolean selected = this.page == value;
            boolean hovered = mouseX >= x && mouseX < x + w
                    && mouseY >= this.pageTabY && mouseY < this.pageTabY + 13;
            int fill = selected ? UiFx.BRASS : (hovered ? 0x33FFFFFF : 0x1AFFFFFF);
            int ink = selected ? UiFx.IRON_LO : UiFx.LIT_DIM;
            String label = value.label;
            if (value == Page.UPCOMING) {
                label += " " + this.stationRoster.getAssociatedTrainCount();
            } else if (value == Page.HISTORY) {
                label += " " + this.deliveryBoard.getHistoryEntries().size();
            }
            UiFx.chip(g, this.font, x, this.pageTabY, label, ink, fill);
        }
        UiFx.hairline(g, this.pageTabX[Page.values().length - 1]
                + this.pageTabW[Page.values().length - 1] + 6, this.pageTabY + 6,
                Math.max(0, this.innerRight - (this.pageTabX[Page.values().length - 1]
                        + this.pageTabW[Page.values().length - 1] + 6)), UiFx.FACE_RULE);
    }

    private float animationTime(float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            return com.createstats.client.AnimationClock.wrapped(mc.level, partialTick);
        }
        return (System.currentTimeMillis() % 100000L) / 50.0f;
    }

    private void updateAnimations(float dt) {
        int sig = this.computeDataSignature();
        if (sig != this.dataSignature) {
            this.dataSignature = sig;
            this.refreshFlash = 1.0f;
        }
        this.refreshFlash = Math.max(0f, this.refreshFlash - dt * 0.10f);
        this.animScrollPx = UiFx.approach(this.animScrollPx, this.dispatchScroll * (float) ROW_H, dt, 0.55f);
        this.bannerProgress = UiFx.approach(this.bannerProgress, this.targetBannerProgress(), dt, 0.16f);
        for (DispatchRowEntry row : this.rows) {
            UUID id = row.getLineView().getLineId();
            float current = this.rowProgressAnim.getOrDefault(id, row.getProgress());
            this.rowProgressAnim.put(id, UiFx.approach(current, row.getProgress(), dt, 0.22f));
        }
        float selectionTarget = this.selected >= 0 && this.selected < this.rows.size()
            ? this.rowTopFor(this.selected) : Float.NaN;
        if (Float.isNaN(selectionTarget)) {
            this.selectionY = Float.NaN;
        } else if (Float.isNaN(this.selectionY)) {
            this.selectionY = selectionTarget;
        } else {
            this.selectionY = UiFx.approach(this.selectionY, selectionTarget, dt, 0.5f);
        }
    }

    private float roll(String key, float target, float speed) {
        return this.counters.computeIfAbsent(key, k -> new UiFx.Rolling()).update(target, this.frameDt, speed);
    }

    private void renderBackdrop(GuiGraphics g) {
        g.fillGradient(0, 0, this.width, this.height, 0xE60A0C10, 0xF2141017);
        int spacing = 30;
        int drift = (int) (this.frameTime * 0.35f) % spacing;
        for (int x = -drift; x < this.width; x += spacing) {
            g.fill(x, 0, x + 1, this.height, 0x10B58C3E);
        }
        UiFx.softShadow(g, this.panelLeft, this.panelTop, this.panelWidth, this.panelHeight);
    }

    private void renderChassis(GuiGraphics g) {
        UiFx.raised(g, this.panelLeft, this.panelTop, this.panelWidth, this.panelHeight, UiFx.IRON);
        UiFx.outline(g, this.panelLeft, this.panelTop, this.panelWidth, this.panelHeight, UiFx.FACE_EDGE);
        UiFx.sunken(g, this.faceLeft, this.faceTop, this.faceRight - this.faceLeft,
            this.faceBottom - this.faceTop, UiFx.FACE);

        int[] bx = {this.panelLeft + 3, this.panelLeft + this.panelWidth - 5};
        int[] by = {this.panelTop + 3, this.panelTop + this.panelHeight - 5};
        for (int x : bx) {
            for (int y : by) {
                g.fill(x, y, x + 2, y + 2, UiFx.BRASS);
                g.fill(x, y, x + 1, y + 1, UiFx.BRASS_HI);
            }
        }
    }

    private void renderHeaderRail(GuiGraphics g) {
        int h = this.headerBottom - this.headerTop;
        UiFx.raised(g, this.faceLeft + 1, this.headerTop, this.faceRight - this.faceLeft - 2, h,
            UiFx.FACE_HI);
        UiFx.hairline(g, this.faceLeft + 1, this.headerBottom - 1, this.faceRight - this.faceLeft - 2,
            UiFx.FACE_RULE);

        String title = "DISPATCH";
        int plateW = this.font.width(title) + 14;
        int plateX = this.innerLeft;
        int plateY = this.headerTop + 4;
        UiFx.raised(g, plateX, plateY, plateW, 14, UiFx.BRASS);
        UiFx.outline(g, plateX, plateY, plateW, 14, UiFx.BRASS_LO);
        g.drawString(this.font, title, plateX + 7, plateY + 3, UiFx.IRON_LO, false);

        int textX = plateX + plateW + 9;
        String station = this.stationLink.isLinked() ? this.stationLink.getStationName() : "No station linked";
        int rightGuard = this.innerRight - 66;
        g.drawString(this.font, this.trim(station, rightGuard - textX - 6), textX, this.headerTop + 7,
            this.stationLink.isLinked() ? UiFx.LIT : UiFx.LIT_FAINT, false);

        int pipX = this.innerRight - 5;
        boolean live = this.stationLink.isLinked();
        UiFx.pulseDot(g, pipX, this.headerTop + 11, 2,
            live ? UiFx.lerpColor(UiFx.LIT_LIVE, 0xFFFFFFFF, this.refreshFlash * 0.7f)
                 : UiFx.LIT_FAINT,
            this.frameTime, live ? 0.42f : 0.12f);
        if (!this.canEdit) {
            String ro = "READ ONLY";
            int w = UiFx.chipWidth(this.font, ro);
            UiFx.chip(g, this.font, pipX - 9 - w, this.headerTop + 5, ro,
                UiFx.LIT_DIM, 0x33000000);
        }
    }

    private void renderBerthStrip(GuiGraphics g) {
        int left = this.innerLeft;
        int right = this.innerRight;
        int top = this.statusTop;
        int h = this.statusBottom - this.statusTop;
        int state = this.statusColor();
        boolean moving = this.isMoving();
        boolean attention = this.headerState == BoardHeaderState.ERROR
            || this.headerState == BoardHeaderState.WAITING_ON_CARGO;

        UiFx.sunken(g, left, top, right - left, h, UiFx.FACE_LO);

        float pulse = moving || attention ? 0.55f + 0.45f * UiFx.pulse01(this.frameTime, 0.30f) : 0.9f;
        g.fill(left + 1, top + 1, left + 5, top + h - 1, UiFx.lerpColor(UiFx.FACE_LO, state, pulse));

        int textLeft = left + 13;
        int gaugeW = Math.min(224, (right - left) * 2 / 5);
        int gaugeLeft = right - gaugeW - 8;

        this.drawScaled(g, this.statusHeadline(), textLeft, top + 7, state, 2);
        g.drawString(this.font, this.trim(this.statusDetail(), gaugeLeft - textLeft - 10), textLeft, top + 26,
            UiFx.LIT_DIM, false);

        boolean showGauge = this.headerState != BoardHeaderState.UNLINKED && this.deliveryBoard.getTotalOrders() > 0;
        if (showGauge) {
            int pct = Math.round(this.bannerProgress * 100f);
            String pctText = pct + "%";
            this.drawScaled(g, pctText, gaugeLeft, top + 5, state, 2);

            int done = this.deliveryBoard.getCompletedOrders();
            String counts = done + " of " + this.deliveryBoard.getTotalOrders() + " orders";

            this.drawRight(g, counts, gaugeLeft + gaugeW, top + 9, UiFx.LIT_DIM);
            UiFx.liveProgressBar(g, gaugeLeft, top + 24, gaugeW, 10, this.bannerProgress,
                state, UiFx.FACE_EDGE, UiFx.lerpColor(UiFx.FACE_LO, state, 0.45f),
                moving, this.frameTime);
        } else if (this.headerState == BoardHeaderState.READY) {
            this.drawScaled(g, "CLEAR", gaugeLeft, top + 14, state, 2);
        }

        this.renderFlowLane(g, textLeft, top + h - 15, right - 9, 11, state, moving);
    }

    private void renderFlowLane(GuiGraphics g, int left, int top, int right, int h, int state, boolean moving) {
        UiFx.sunken(g, left, top, right - left, h, UiFx.FACE_EDGE);
        g.drawString(this.font, "YARD", left + 4, top + 2, UiFx.LIT_FAINT, false);
        String trainLabel = this.cargoView.isTrainPresent() && !this.cargoView.getTrainName().isEmpty()
            ? this.cargoView.getTrainName() : "TRAIN";
        int trainW = this.font.width(trainLabel);
        g.drawString(this.font, this.trim(trainLabel, 120), right - Math.min(trainW, 120) - 4, top + 2,
            this.cargoView.isTrainPresent() ? UiFx.LIT_DIM : UiFx.LIT_FAINT, false);

        int laneLeft = left + this.font.width("YARD") + 10;
        int laneRight = right - Math.min(trainW, 120) - 10;
        int direction = this.flowDirection();
        if (moving && direction != 0 && laneRight > laneLeft + 10) {
            int hue = direction > 0 ? UiFx.LIT_TO_TRAIN : UiFx.LIT_TO_STATION;
            UiFx.marchingChevrons(g, laneLeft, top + 1, laneRight - laneLeft, h - 2, hue,
                this.frameTime, direction);
            return;
        }
        String word = switch (this.headerState) {
            case UNLINKED -> "OFFLINE";
            case IDLE -> "BERTH EMPTY";
            case ARRIVING -> "INBOUND";
            case PRESENT -> "DOCKED";
            case READY -> "CLEARED TO DEPART";
            case WAITING_ON_CARGO -> "NO TRANSFER";
            case ERROR -> "CHECK ORDERS";
            default -> "IDLE";
        };
        if (laneRight > laneLeft) {
            g.drawString(this.font, word, (laneLeft + laneRight) / 2 - this.font.width(word) / 2, top + 2,
                UiFx.lerpColor(UiFx.LIT_FAINT, state, 0.5f), false);
        }
    }

    private void renderFilterRow(GuiGraphics g, int mouseX, int mouseY) {
        UiFx.sectionLabelLit(g, this.font, "Work queue", this.innerLeft, this.chipY + 2,
            this.chipX[0] - 8);
        for (Filter value : Filter.values()) {
            int i = value.ordinal();
            boolean on = this.filter == value;
            boolean hot = mouseX >= this.chipX[i] && mouseX < this.chipX[i] + this.chipW[i]
                && mouseY >= this.chipY && mouseY < this.chipY + 11;
            int fill = on ? UiFx.BRASS : (hot ? 0x33FFFFFF : 0x1AFFFFFF);
            int ink = on ? UiFx.IRON_LO : UiFx.LIT_DIM;
            UiFx.chip(g, this.font, this.chipX[i], this.chipY, this.chipLabel(value), ink, fill);
        }
    }

    private String chipLabel(Filter value) {
        if (this.compact) {
            return value.label;
        }
        return switch (value) {
            case OPEN -> "Needs work " + this.openCount;
            case DONE -> "Finished " + this.doneCount;
            case ALL -> "All orders " + this.allRows.size();
        };
    }

    private void renderOrders(GuiGraphics g, int mouseX, int mouseY) {
        int left = this.innerLeft;
        int right = this.innerRight;
        int h = this.rowsBottom - this.rowsTop;
        UiFx.sunken(g, left, this.rowsTop, right - left, h, UiFx.FACE_LO);

        if (this.rows.isEmpty()) {
            this.renderEmptyOrders(g, left, right);
            return;
        }

        boolean mouseInside = mouseX >= left && mouseX < right && mouseY >= this.rowsTop && mouseY < this.rowsBottom;
        int first = (int) Math.floor(this.animScrollPx / ROW_H);
        long boardTick = this.deliveryBoard.getCurrentTick();

        g.enableScissor(left + 1, this.rowsTop + 1, right - 1, this.rowsBottom - 1);

        // row fill = mouse, bar = you. keyboard nav needs the difference

        if (!Float.isNaN(this.selectionY)) {
            int sy = Math.round(this.selectionY);
            g.fill(left + 1, sy, right - 1, sy + ROW_H - 1, 0x22B58C3E);
            g.fill(left + 1, sy, left + 2, sy + ROW_H - 1, UiFx.BRASS);
        }

        for (int i = -1; i <= this.visibleRows; i++) {
            int index = first + i;
            if (index < 0 || index >= this.rows.size()) {
                continue;
            }
            int rowTop = this.rowTopFor(index);
            if (rowTop + ROW_H < this.rowsTop || rowTop > this.rowsBottom) {
                continue;
            }
            boolean hovered = mouseInside && mouseY >= rowTop && mouseY < rowTop + ROW_H;
            this.renderOrderRow(g, this.rows.get(index), index, rowTop, left, right, hovered, mouseX, mouseY, boardTick);
        }
        g.disableScissor();

        this.renderScrollbar(g, right - 4, this.rowsTop + 2, this.rowsBottom - 2,
            this.rows.size(), this.visibleRows, this.animScrollPx, ROW_H);
    }

    private void renderEmptyOrders(GuiGraphics g, int left, int right) {
        int cy = this.rowsTop + (this.rowsBottom - this.rowsTop) / 2 - 14;
        String head;
        String hint;
        if (!this.stationLink.isLinked()) {
            head = "Not linked";
            hint = "Shift-click this board with a Freight Manifest Clipboard bound to a station.";
        } else if (this.allRows.isEmpty()) {
            head = "No orders";
            hint = "Write a manifest on the clipboard; the orders on it appear here.";
        } else if (this.filter == Filter.OPEN) {
            head = "Nothing outstanding";
            hint = "Every order at this station is complete. Switch to Done or All to review them.";
        } else {
            head = "Nothing here";
            hint = "No order matches this filter.";
        }
        int hw = this.font.width(head);
        g.drawString(this.font, head, (left + right) / 2 - hw / 2, cy, UiFx.LIT_DIM, false);
        String wrapped = this.trim(hint, right - left - 32);
        g.drawString(this.font, wrapped, (left + right) / 2 - this.font.width(wrapped) / 2, cy + 13,
            UiFx.LIT_FAINT, false);
    }

    private void renderOrderRow(GuiGraphics g, DispatchRowEntry entry, int index, int rowTop,
                                int left, int right, boolean hovered, int mouseX, int mouseY, long boardTick) {
        DeliveryOrderLineView line = entry.getLineView();
        UUID id = line.getLineId();
        DispatchRowState rowState = entry.getState();
        boolean hidden = rowState == DispatchRowState.COMPLETE_HIDDEN;
        int status = this.rowStatusColor(rowState);
        int direction = line.getOperation() == ManifestOperation.UNLOAD
            ? UiFx.LIT_TO_STATION : UiFx.LIT_TO_TRAIN;

        float hover = UiFx.approach(this.rowHoverAnim.getOrDefault(id, 0f), hovered ? 1f : 0f,
            this.frameDt, 0.4f);
        this.rowHoverAnim.put(id, hover);

        if (index % 2 == 1) {
            g.fill(left + 1, rowTop, right - 1, rowTop + ROW_H - 1, UiFx.FACE_ROW);
        }
        if (hover > 0.01f) {
            g.fill(left + 1, rowTop, right - 1, rowTop + ROW_H - 1,
                UiFx.multiplyAlpha(0x40FFFFFF, hover));
        }
        UiFx.hairline(g, left + 1, rowTop + ROW_H - 1, right - left - 2, 0x1AFFFFFF);

        boolean live = rowState == DispatchRowState.IN_PROGRESS || rowState == DispatchRowState.TRACKING;
        int edge = live
            ? UiFx.lerpColor(status, 0xFFFFFFFF, 0.3f * UiFx.pulse01(this.frameTime + index * 7f, 0.34f))
            : status;
        g.fill(left + 2, rowTop + 3, left + 5, rowTop + ROW_H - 4, hidden ? UiFx.LIT_FAINT : edge);
        UiFx.triangle(g, left + 8, rowTop + 6, 4,
            line.getOperation() == ManifestOperation.UNLOAD ? -1 : 1,
            hidden ? UiFx.LIT_FAINT : direction);

        if (!line.getDisplayStack().isEmpty()) {
            g.renderItem(line.getDisplayStack(), left + 18, rowTop + 6);
        }

        int actionsLeft = this.canEdit ? ACTIONS_W : 46;
        actionsLeft = right - actionsLeft;
        int nameX = left + 38;

        String workState = null;
        int workStateInk = status;
        if (id.equals(this.nextLineId)) {
            workState = "NEXT";
            workStateInk = UiFx.LIT_LIVE;
        } else if (line.isDismissed()) {
            workState = "ARCHIVED";
            workStateInk = UiFx.LIT_FAINT;
        } else if (line.isComplete()) {
            workState = "DONE";
            workStateInk = UiFx.LIT_OK;
        }
        if (workState != null) {
            int stateW = UiFx.chipWidth(this.font, workState);
            UiFx.chip(g, this.font, nameX, rowTop + 3, workState,
                    workStateInk == UiFx.LIT_FAINT ? UiFx.LIT_DIM : UiFx.FACE_LO,
                    workStateInk);
            nameX += stateW + 4;
        }

        String badge = line.getResetMode().getBadgeLabel(line.getResetIntervalValue());
        int badgeW = UiFx.chipWidth(this.font, badge);
        int badgeX = actionsLeft - badgeW - 6;
        int nameLimit = badgeX - nameX - 6;

        UiFx.RateTracker rate = this.rates.get(id);
        boolean feeding = rate != null && rate.isMoving();
        if (rate != null && !line.isComplete() && line.getTargetAmount() > 0) {
            long eta = rate.estimate(line.getTargetAmount() - line.getDeliveredAmount());
            if (eta > 0L) {
                String label = "~" + FreightManifestLogic.formatTicks(eta) + " left";
                int labelW = this.font.width(label);

                if (nameLimit - labelW - 8 > 40) {
                    g.drawString(this.font, label, badgeX - labelW - 8, rowTop + 5,
                        UiFx.LIT_FAINT, false);
                    nameLimit -= labelW + 8;
                }
            }
        }
        g.drawString(this.font, this.trim(line.getDisplayName(), nameLimit), nameX, rowTop + 5,
            hidden ? UiFx.LIT_FAINT : UiFx.LIT, false);
        UiFx.chip(g, this.font, badgeX, rowTop + 3, badge, UiFx.LIT_DIM, 0x26FFFFFF);

        if (line.getTargetAmount() <= 0) {
            g.drawString(this.font, "No amount set - finish this line on the clipboard", nameX, rowTop + 17,
                UiFx.LIT_BAD, false);
        } else {
            float progress = this.rowProgressAnim.getOrDefault(id, entry.getProgress());
            String figures = FreightManifestLogic.formatAmount(entry.getCargoKind(), line.getDeliveredAmount())
                + " / " + FreightManifestLogic.formatAmount(entry.getCargoKind(), line.getTargetAmount());
            int figuresW = this.font.width(figures);
            int gaugeRight = actionsLeft - figuresW - 12;
            if (gaugeRight > nameX + 12) {
                this.gaugeOnFace(g, nameX, rowTop + 18, gaugeRight - nameX, 6, progress,
                    hidden ? UiFx.LIT_FAINT : status, feeding);
            }
            g.drawString(this.font, figures, actionsLeft - figuresW - 6, rowTop + 17,
                hidden ? UiFx.LIT_FAINT : UiFx.LIT_DIM, false);
        }

        if (this.canEdit) {
            boolean complete = line.isComplete();
            this.rowButton(g, right - ACTIONS_W, rowTop + 4, 62, complete ? "Reset" : "Mark done", true, mouseX, mouseY);
            boolean archived = this.isOneShotArchive(line);
            boolean toggleable = !archived && (complete || line.isDismissed());
            String toggleLabel = archived ? "Archived" : line.isDismissed() ? "Show" : "Hide done";
            this.rowButton(g, right - 56, rowTop + 4, 48, toggleLabel,
                toggleable, mouseX, mouseY);
        } else if (line.getTargetAmount() > 0) {
            float progress = this.rowProgressAnim.getOrDefault(id, entry.getProgress());
            String pct = Math.round(this.roll("pct" + id, progress * 100f, 0.3f)) + "%";
            g.drawString(this.font, pct, right - 8 - this.font.width(pct), rowTop + 10,
                line.isComplete() ? UiFx.LIT_OK : UiFx.LIT_DIM, false);
        }
    }

    private void gaugeOnFace(GuiGraphics g, int x, int y, int w, int h, float value, int fill, boolean active) {
        UiFx.sunken(g, x, y, w, h, UiFx.FACE_EDGE);
        int fillW = Math.round((w - 2) * UiFx.clamp01(value));
        if (fillW <= 0) {
            return;
        }
        int fx = x + 1;
        int fy = y + 1;
        int fh = Math.max(1, h - 2);
        g.fill(fx, fy, fx + fillW, fy + fh, fill);
        g.fill(fx, fy, fx + fillW, fy + 1, UiFx.brighten(fill, 0.35f));
        if (active && fh > 1) {
            g.enableScissor(fx, fy, fx + fillW, fy + fh);
            int period = 10;
            int phase = (int) ((this.frameTime * 1.1f) % period);
            int sheen = UiFx.withAlpha(0xFFFFFFFF, 52);
            for (int sx = fx - fh - period + phase; sx < fx + fillW + fh; sx += period) {
                for (int row = 0; row < fh; row++) {
                    g.fill(sx + row, fy + row, sx + row + 2, fy + row + 1, sheen);
                }
            }
            g.disableScissor();
        }
    }

    private void rowButton(GuiGraphics g, int x, int y, int w, String label, boolean active, int mouseX, int mouseY) {
        int h = 14;
        boolean hovered = active && mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
        int fill = !active ? 0x14FFFFFF : (hovered ? 0x4DB58C3E : 0x26FFFFFF);
        g.fill(x, y, x + w, y + h, fill);
        UiFx.outline(g, x, y, w, h, active
            ? (hovered ? UiFx.BRASS : 0x33FFFFFF) : 0x1AFFFFFF);
        int ink = active ? (hovered ? UiFx.BRASS_HI : UiFx.LIT_DIM) : UiFx.LIT_FAINT;
        g.drawString(this.font, label, x + (w - this.font.width(label)) / 2, y + 3, ink, false);
    }

    private void renderCargoLedger(GuiGraphics g, int mouseX, int mouseY) {
        if (this.bottomRows <= 0) {
            return;
        }
        int outX = this.cargoRight - 8;
        int inX = outX - 40;
        int heldX = inX - 40;

        UiFx.sectionLabelLit(g, this.font, "Cargo", this.cargoLeft, this.cargoTop - 11,
            heldX - this.font.width("aboard") - 6);
        this.drawRight(g, "aboard", heldX, this.cargoTop - 11, UiFx.LIT_FAINT);
        this.drawRight(g, "off", inX, this.cargoTop - 11, UiFx.LIT_FAINT);
        this.drawRight(g, "on", outX, this.cargoTop - 11, UiFx.LIT_FAINT);
        int w = this.cargoRight - this.cargoLeft;
        UiFx.sunken(g, this.cargoLeft, this.cargoTop, w, this.cargoBottom - this.cargoTop,
            UiFx.FACE_LO);

        if (this.cargoEntries.isEmpty()) {
            g.drawString(this.font, this.cargoView.isTrainPresent() ? "Train is empty." : "No train in the berth.",
                this.cargoLeft + 8, this.cargoTop + 6, UiFx.LIT_FAINT, false);
            return;
        }

        for (int i = 0; i < this.bottomRows; i++) {
            int index = this.cargoScroll + i;
            if (index >= this.cargoEntries.size()) {
                break;
            }
            int rowTop = this.cargoTop + i * CARGO_ROW_H;
            boolean hovered = mouseX >= this.cargoLeft && mouseX < this.cargoRight
                && mouseY >= rowTop && mouseY < rowTop + CARGO_ROW_H;
            if (hovered) {
                g.fill(this.cargoLeft + 1, rowTop, this.cargoRight - 1, rowTop + CARGO_ROW_H - 1, 0x33FFFFFF);
            } else if (index % 2 == 1) {
                g.fill(this.cargoLeft + 1, rowTop, this.cargoRight - 1, rowTop + CARGO_ROW_H - 1, UiFx.FACE_ROW);
            }

            CargoEntry entry = this.cargoEntries.get(index);
            if (entry.hasGoal) {
                g.fill(this.cargoLeft + 2, rowTop + 3, this.cargoLeft + 4, rowTop + CARGO_ROW_H - 4,
                    entry.unloadGoal ? UiFx.LIT_TO_STATION : UiFx.LIT_TO_TRAIN);
            }
            if (!entry.icon.isEmpty()) {
                g.renderItem(entry.icon, this.cargoLeft + 7, rowTop + 1);
            }
            g.drawString(this.font, this.trim(entry.name, heldX - this.cargoLeft - 32), this.cargoLeft + 26,
                rowTop + 5, entry.hasGoal ? UiFx.LIT : UiFx.LIT_DIM, false);
            this.drawRight(g, entry.compact(entry.held), heldX, rowTop + 5, UiFx.LIT_DIM);
            this.drawRight(g, entry.toStation > 0 ? "-" + entry.compact(entry.toStation) : "·",
                inX, rowTop + 5, entry.toStation > 0 ? UiFx.LIT_TO_STATION : UiFx.LIT_FAINT);
            this.drawRight(g, entry.toTrain > 0 ? "+" + entry.compact(entry.toTrain) : "·",
                outX, rowTop + 5, entry.toTrain > 0 ? UiFx.LIT_TO_TRAIN : UiFx.LIT_FAINT);
        }
        this.renderScrollbar(g, this.cargoRight - 4, this.cargoTop + 2, this.cargoBottom - 2,
            this.cargoEntries.size(), this.bottomRows, this.cargoScroll * (float) CARGO_ROW_H, CARGO_ROW_H);
    }

    private void renderEvents(GuiGraphics g, int mouseX, int mouseY) {
        if (this.bottomRows <= 0) {
            return;
        }
        UiFx.sectionLabelLit(g, this.font, "Recent", this.histLeft, this.histTop - 11, this.histRight);
        UiFx.sunken(g, this.histLeft, this.histTop, this.histRight - this.histLeft,
            this.histBottom - this.histTop, UiFx.FACE_LO);

        List<DeliveryOrderHistoryEntry> history = this.deliveryBoard.getHistoryEntries();
        if (history.isEmpty()) {
            g.drawString(this.font, "Nothing has happened yet.", this.histLeft + 8, this.histTop + 6,
                UiFx.LIT_FAINT, false);
            return;
        }

        for (int i = 0; i < this.bottomRows; i++) {
            int index = this.historyScroll + i;
            if (index >= history.size()) {
                break;
            }
            int rowTop = this.histTop + i * CARGO_ROW_H;
            boolean hovered = mouseX >= this.histLeft && mouseX < this.histRight
                && mouseY >= rowTop && mouseY < rowTop + CARGO_ROW_H;
            if (hovered) {
                g.fill(this.histLeft + 1, rowTop, this.histRight - 1, rowTop + CARGO_ROW_H - 1, 0x33FFFFFF);
            } else if (index % 2 == 1) {
                g.fill(this.histLeft + 1, rowTop, this.histRight - 1, rowTop + CARGO_ROW_H - 1, UiFx.FACE_ROW);
            }

            DeliveryOrderHistoryEntry entry = history.get(index);
            int hue = this.historyColor(entry.getType());
            g.fill(this.histLeft + 2, rowTop + 3, this.histLeft + 4, rowTop + CARGO_ROW_H - 4, hue);
            String age = FreightManifestLogic.formatTicks(
                Math.max(0L, this.deliveryBoard.getCurrentTick() - entry.getTick()));
            this.drawRight(g, age, this.histRight - 6, rowTop + 5, UiFx.LIT_FAINT);
            int textWidth = Math.max(40, this.histRight - this.histLeft - this.font.width(age) - 22);
            g.drawString(this.font, this.trim(entry.getTitle() + ": " + entry.getDetail(), textWidth),
                this.histLeft + 9, rowTop + 5, UiFx.LIT_DIM, false);
        }
        this.renderScrollbar(g, this.histRight - 4, this.histTop + 2, this.histBottom - 2,
            history.size(), this.bottomRows, this.historyScroll * (float) CARGO_ROW_H, CARGO_ROW_H);
    }

    private void renderUpcomingPage(GuiGraphics g, int mouseX, int mouseY) {
        int top = this.statusTop;
        int bottom = this.footerTop - 9;
        int split = this.innerLeft + Math.max(214, (this.innerRight - this.innerLeft) * 2 / 5);
        int listRight = split - 5;
        int detailLeft = split + 5;
        UiFx.sunken(g, this.innerLeft, top, listRight - this.innerLeft, bottom - top, UiFx.FACE_LO);
        UiFx.sunken(g, detailLeft, top, this.innerRight - detailLeft, bottom - top, UiFx.FACE_LO);

        List<TrainSummarySnapshot> trains = this.rosterTrains();
        int inbound = 0;
        for (TrainSummarySnapshot train : trains) {
            if (train.isInboundToStation()) {
                inbound++;
            }
        }
        UiFx.sectionLabelLit(g, this.font, "Scheduled trains", this.innerLeft + 8, top + 7, listRight - 8);
        this.drawRight(g, inbound > 0 ? inbound + " inbound" : trains.size() + " linked",
                listRight - 8, top + 7, inbound > 0 ? UiFx.LIT_LIVE : UiFx.LIT_FAINT);
        int rowsY = top + 23;
        UiFx.hairline(g, this.innerLeft + 1, rowsY - 3, listRight - this.innerLeft - 2, UiFx.FACE_RULE);

        if (trains.isEmpty()) {
            g.drawString(this.font, "No scheduled trains found.", this.innerLeft + 10, rowsY + 12,
                    UiFx.LIT_DIM, false);
            g.drawString(this.font, this.trim("A train appears here when its schedule includes this station.",
                    listRight - this.innerLeft - 20), this.innerLeft + 10, rowsY + 25,
                    UiFx.LIT_FAINT, false);
            this.renderUpcomingCargoEmpty(g, detailLeft, top, bottom,
                    "Select a scheduled train to inspect its cargo.");
            return;
        }

        this.upcomingSelected = Math.max(0, Math.min(this.upcomingSelected, trains.size() - 1));
        int rowH = 38;
        int visible = Math.max(1, (bottom - rowsY - 2) / rowH);
        this.upcomingScroll = Math.max(0, Math.min(this.upcomingScroll,
                Math.max(0, trains.size() - visible)));
        g.enableScissor(this.innerLeft + 1, rowsY, listRight - 1, bottom - 1);
        for (int i = 0; i < visible; i++) {
            int index = this.upcomingScroll + i;
            if (index >= trains.size()) {
                break;
            }
            TrainSummarySnapshot train = trains.get(index);
            int y = rowsY + i * rowH;
            boolean selected = index == this.upcomingSelected;
            boolean hovered = mouseX >= this.innerLeft && mouseX < listRight
                    && mouseY >= y && mouseY < y + rowH;
            if (selected || hovered) {
                g.fill(this.innerLeft + 1, y, listRight - 1, y + rowH - 1,
                        selected ? 0x33B58C3E : 0x22FFFFFF);
            } else if (index % 2 == 1) {
                g.fill(this.innerLeft + 1, y, listRight - 1, y + rowH - 1, UiFx.FACE_ROW);
            }
            int hue = train.isStoppedAtStation() ? UiFx.LIT_OK
                    : train.isInboundToStation() ? UiFx.LIT_LIVE
                    : train.isWaitingForSignal() ? UiFx.LIT_WARN : UiFx.LIT_DIM;
            g.fill(this.innerLeft + 3, y + 4, this.innerLeft + 6, y + rowH - 5, hue);
            String state = train.isStoppedAtStation() ? "DOCKED"
                    : train.isInboundToStation() ? "NEXT"
                    : train.isWaitingForSignal() ? "SIGNAL" : "SCHEDULED";
            int stateW = UiFx.chipWidth(this.font, state);
            UiFx.chip(g, this.font, listRight - stateW - 8, y + 5, state, UiFx.FACE_LO, hue);
            g.drawString(this.font, this.trim(train.getTrainName(),
                    listRight - stateW - this.innerLeft - 48), this.innerLeft + 12, y + 6,
                    selected ? UiFx.LIT : UiFx.LIT_DIM, false);
            String destination = train.getNextDestination().isEmpty()
                    ? "No live destination" : "Next: " + train.getNextDestination();
            g.drawString(this.font, this.trim(destination, listRight - this.innerLeft - 26),
                    this.innerLeft + 12, y + 19, UiFx.LIT_FAINT, false);
            String facts = train.getCarriageCount() + " carriages | "
                    + train.getCargoSnapshot().getAmounts().size() + " cargo types";
            int chargeW = this.drawChargeChip(g, train, listRight - 8, y + 27);
            g.drawString(this.font, this.trim(facts, listRight - this.innerLeft - 26 - chargeW),
                    this.innerLeft + 12, y + 29, UiFx.LIT_FAINT, false);
        }
        g.disableScissor();
        this.renderScrollbar(g, listRight - 4, rowsY + 2, bottom - 2,
                trains.size(), visible, this.upcomingScroll * (float) rowH, rowH);
        this.renderUpcomingCargo(g, trains.get(this.upcomingSelected), detailLeft, top, bottom, mouseX, mouseY);
    }

    private int drawChargeChip(GuiGraphics g, TrainSummarySnapshot train, int right, int y) {
        com.createstats.train.power.TrainChargeReading charge = train.getCharge();
        if (charge == null || !charge.hasStorage()) {
            return 0;
        }
        long now = this.clientGameTime();
        String label = "FE " + charge.percent() + "%" + (charge.isLive(now) ? "" : " " + charge.ageLabel(now));
        int w = UiFx.chipWidth(this.font, label);
        UiFx.chip(g, this.font, right - w, y, label, UiFx.FACE_LO,
                0xFF000000 | com.createstats.power.PortableFeColors.fillOnDark(charge.fraction()));
        return w + 4;
    }

    private long clientGameTime() {
        return this.minecraft != null && this.minecraft.level != null ? this.minecraft.level.getGameTime() : 0L;
    }

    private void renderUpcomingCargo(GuiGraphics g, TrainSummarySnapshot train, int left, int top,
                                     int bottom, int mouseX, int mouseY) {
        int right = this.innerRight;
        int hue = train.isInboundToStation() ? UiFx.LIT_LIVE : UiFx.LIT_DIM;
        UiFx.sectionLabelLit(g, this.font, "Aboard " + train.getTrainName(), left + 8, top + 7, right - 8);
        String state = train.isInboundToStation() ? "NEXT TO THIS STATION" : "SCHEDULED FOR THIS STATION";
        g.drawString(this.font, this.trim(state, right - left - 16), left + 8, top + 20, hue, false);
        String route = train.getNextDestination().isEmpty()
                ? "Live destination is not available" : "Current destination: " + train.getNextDestination();
        g.drawString(this.font, this.trim(route, right - left - 16), left + 8, top + 32,
                UiFx.LIT_FAINT, false);
        g.drawString(this.font, train.getCarriageCount() + " carriages | owner " + train.getOwnerName(),
                left + 8, top + 44, UiFx.LIT_FAINT, false);
        com.createstats.train.power.TrainChargeReading charge = train.getCharge();
        if (charge != null && charge.hasStorage()) {
            this.drawRight(g, "FE " + charge.percent() + "% | " + charge.ageLabel(this.clientGameTime()),
                    right - 8, top + 20, 0xFF000000 | com.createstats.power.PortableFeColors.fillOnDark(charge.fraction()));
        }

        int rowsY = top + 62;
        UiFx.hairline(g, left + 1, rowsY - 5, right - left - 2, UiFx.FACE_RULE);
        List<CargoEntry> cargo = this.buildSnapshotCargoEntries(train);
        if (cargo.isEmpty()) {
            this.renderUpcomingCargoEmpty(g, left, rowsY - 8, bottom, "This train is empty.");
            return;
        }
        int rowH = 22;
        int visible = Math.max(1, (bottom - rowsY - 2) / rowH);
        this.upcomingCargoScroll = Math.max(0, Math.min(this.upcomingCargoScroll,
                Math.max(0, cargo.size() - visible)));
        g.enableScissor(left + 1, rowsY, right - 1, bottom - 1);
        for (int i = 0; i < visible; i++) {
            int index = this.upcomingCargoScroll + i;
            if (index >= cargo.size()) {
                break;
            }
            CargoEntry entry = cargo.get(index);
            int y = rowsY + i * rowH;
            if (index % 2 == 1) {
                g.fill(left + 1, y, right - 1, y + rowH - 1, UiFx.FACE_ROW);
            }
            if (!entry.icon.isEmpty()) {
                g.renderItem(entry.icon, left + 7, y + 3);
            }
            g.drawString(this.font, this.trim(entry.name, right - left - 92), left + 28, y + 7,
                    UiFx.LIT, false);
            this.drawRight(g, entry.compact(entry.held), right - 8, y + 7, UiFx.LIT_LIVE);
        }
        g.disableScissor();
        this.renderScrollbar(g, right - 4, rowsY + 2, bottom - 2,
                cargo.size(), visible, this.upcomingCargoScroll * (float) rowH, rowH);
    }

    private void renderUpcomingCargoEmpty(GuiGraphics g, int left, int top, int bottom, String text) {
        int right = this.innerRight;
        int y = top + Math.max(20, (bottom - top) / 2);
        String line = this.trim(text, right - left - 24);
        g.drawString(this.font, line, left + (right - left - this.font.width(line)) / 2,
                y, UiFx.LIT_FAINT, false);
    }

    private void renderHistoryPage(GuiGraphics g, int mouseX, int mouseY) {
        int top = this.statusTop;
        int bottom = this.footerTop - 9;
        int split = this.innerLeft + (this.innerRight - this.innerLeft) * 3 / 5;
        int eventsRight = split - 5;
        int visitsLeft = split + 5;
        UiFx.sunken(g, this.innerLeft, top, eventsRight - this.innerLeft, bottom - top, UiFx.FACE_LO);
        UiFx.sunken(g, visitsLeft, top, this.innerRight - visitsLeft, bottom - top, UiFx.FACE_LO);

        List<DeliveryOrderHistoryEntry> events = this.deliveryBoard.getHistoryEntries();
        UiFx.sectionLabelLit(g, this.font, "Order receipts", this.innerLeft + 8, top + 7, eventsRight - 8);
        this.drawRight(g, events.size() + " events", eventsRight - 8, top + 7, UiFx.LIT_FAINT);
        int rowsY = top + 23;
        UiFx.hairline(g, this.innerLeft + 1, rowsY - 3, eventsRight - this.innerLeft - 2, UiFx.FACE_RULE);
        int rowH = 36;
        int visible = Math.max(1, (bottom - rowsY - 2) / rowH);
        this.historyScroll = Math.max(0, Math.min(this.historyScroll,
                Math.max(0, events.size() - visible)));
        if (events.isEmpty()) {
            g.drawString(this.font, "No completed or reset orders yet.", this.innerLeft + 10,
                    rowsY + 12, UiFx.LIT_FAINT, false);
        } else {
            g.enableScissor(this.innerLeft + 1, rowsY, eventsRight - 1, bottom - 1);
            for (int i = 0; i < visible; i++) {
                int index = this.historyScroll + i;
                if (index >= events.size()) {
                    break;
                }
                DeliveryOrderHistoryEntry entry = events.get(index);
                int y = rowsY + i * rowH;
                if (index % 2 == 1) {
                    g.fill(this.innerLeft + 1, y, eventsRight - 1, y + rowH - 1, UiFx.FACE_ROW);
                }
                int hue = this.historyColor(entry.getType());
                g.fill(this.innerLeft + 3, y + 4, this.innerLeft + 6, y + rowH - 5, hue);
                String age = FreightManifestLogic.formatTicks(Math.max(0L,
                        this.deliveryBoard.getCurrentTick() - entry.getTick())) + " ago";
                this.drawRight(g, age, eventsRight - 8, y + 6, UiFx.LIT_FAINT);
                g.drawString(this.font, this.trim(entry.getTitle(),
                        eventsRight - this.innerLeft - this.font.width(age) - 34),
                        this.innerLeft + 12, y + 6, hue, false);
                g.drawString(this.font, this.trim(entry.getDetail(), eventsRight - this.innerLeft - 28),
                        this.innerLeft + 12, y + 20, UiFx.LIT_DIM, false);
            }
            g.disableScissor();
        }
        this.renderScrollbar(g, eventsRight - 4, rowsY + 2, bottom - 2,
                events.size(), visible, this.historyScroll * (float) rowH, rowH);

        List<TrainVisitRecord> visits = this.stationRoster.getRecentVisits();
        UiFx.sectionLabelLit(g, this.font, "Train visits", visitsLeft + 8, top + 7, this.innerRight - 8);
        this.drawRight(g, visits.size() + " visits", this.innerRight - 8, top + 7, UiFx.LIT_FAINT);
        int visitVisible = Math.max(1, (bottom - rowsY - 2) / rowH);
        this.visitScroll = Math.max(0, Math.min(this.visitScroll,
                Math.max(0, visits.size() - visitVisible)));
        if (visits.isEmpty()) {
            g.drawString(this.font, "No train visits recorded.", visitsLeft + 10,
                    rowsY + 12, UiFx.LIT_FAINT, false);
        } else {
            g.enableScissor(visitsLeft + 1, rowsY, this.innerRight - 1, bottom - 1);
            for (int i = 0; i < visitVisible; i++) {
                int index = this.visitScroll + i;
                if (index >= visits.size()) {
                    break;
                }
                TrainVisitRecord visit = visits.get(index);
                int y = rowsY + i * rowH;
                if (index % 2 == 1) {
                    g.fill(visitsLeft + 1, y, this.innerRight - 1, y + rowH - 1, UiFx.FACE_ROW);
                }
                int hue = visit.isStillPresent() ? UiFx.LIT_OK : UiFx.LIT_DIM;
                g.fill(visitsLeft + 3, y + 4, visitsLeft + 6, y + rowH - 5, hue);
                g.drawString(this.font, this.trim(visit.getTrainName(), this.innerRight - visitsLeft - 24),
                        visitsLeft + 12, y + 6, hue, false);
                String detail;
                if (visit.isStillPresent()) {
                    detail = "Docked for " + FreightManifestLogic.formatTicks(Math.max(0L,
                            this.deliveryBoard.getCurrentTick() - visit.getArrivalTick()));
                } else {
                    detail = "Stayed " + FreightManifestLogic.formatTicks(Math.max(0L,
                            visit.getDepartureTick() - visit.getArrivalTick()));
                }
                g.drawString(this.font, detail, visitsLeft + 12, y + 20, UiFx.LIT_FAINT, false);
            }
            g.disableScissor();
        }
        this.renderScrollbar(g, this.innerRight - 4, rowsY + 2, bottom - 2,
                visits.size(), visitVisible, this.visitScroll * (float) rowH, rowH);
    }

    private List<TrainSummarySnapshot> rosterTrains() {
        List<TrainSummarySnapshot> trains = new ArrayList<>();
        if (this.stationRoster.getPresentTrain() != null) {
            trains.add(this.stationRoster.getPresentTrain());
        }
        trains.addAll(this.stationRoster.getAssociatedTrains());
        return trains;
    }

    private List<CargoEntry> buildSnapshotCargoEntries(TrainSummarySnapshot train) {
        List<CargoEntry> entries = new ArrayList<>();
        for (Map.Entry<FreightManifestResourceKey, Integer> entry
                : train.getCargoSnapshot().getAmounts().entrySet()) {
            entries.add(CargoEntry.of(entry.getKey(), entry.getValue(), 0, 0, 0, 0));
        }
        entries.sort(Comparator.comparing((CargoEntry entry) -> entry.kind.ordinal())
                .thenComparing(entry -> entry.name));
        return entries;
    }

    private void renderScrollbar(GuiGraphics g, int x, int top, int bottom, int count, int visible,
                                 float scrollPx, int rowHeight) {
        if (count <= visible) {
            return;
        }
        int trackH = bottom - top;
        g.fill(x, top, x + 3, bottom, 0x40000000);
        int thumbH = Math.max(12, Math.round(trackH * (visible / (float) count)));
        float maxScroll = Math.max(1f, (count - visible) * (float) rowHeight);
        int thumbTop = top + Math.round((trackH - thumbH) * UiFx.clamp01(scrollPx / maxScroll));
        g.fill(x, thumbTop, x + 3, thumbTop + thumbH, UiFx.BRASS);
        g.fill(x, thumbTop, x + 3, thumbTop + 1, UiFx.BRASS_HI);
    }

    private void renderTooltips(GuiGraphics g, int mouseX, int mouseY) {
        if (this.page != Page.LIVE) {
            return;
        }
        if (this.inside(mouseX, mouseY, this.innerLeft, this.rowsTop, this.innerRight, this.rowsBottom)) {
            int index = this.rowIndexAt(mouseY);
            if (index >= 0 && index < this.rows.size()) {
                DeliveryOrderLineView line = this.rows.get(index).getLineView();
                int rowTop = this.rowTopFor(index);
                if (this.canEdit && mouseY >= rowTop + 4 && mouseY < rowTop + 18) {
                    if (mouseX >= this.innerRight - ACTIONS_W && mouseX < this.innerRight - ACTIONS_W + 62) {
                        g.renderTooltip(this.font, List.of(
                            Component.literal(line.isComplete() ? "Reset this order cycle" : "Mark complete for this cycle"),
                            Component.literal(line.isComplete()
                                ? "Clears progress and reopens the line."
                                : "Use when the order was satisfied outside live tracking.").withStyle(ChatFormatting.GRAY),
                            Component.literal("Enter").withStyle(ChatFormatting.DARK_GRAY)
                        ), Optional.empty(), mouseX, mouseY);
                        return;
                    }
                    if (!this.isOneShotArchive(line) && (line.isComplete() || line.isDismissed())
                        && mouseX >= this.innerRight - 56 && mouseX < this.innerRight - 8) {
                        g.renderTooltip(this.font, List.of(
                            Component.literal(line.isDismissed() ? "Show this order again" : "Hide this completed order"),
                            Component.literal("Tidies the board for this cycle only.").withStyle(ChatFormatting.GRAY),
                            Component.literal("Delete").withStyle(ChatFormatting.DARK_GRAY)
                        ), Optional.empty(), mouseX, mouseY);
                        return;
                    }
                }
                g.renderTooltip(this.font, this.orderTooltip(this.rows.get(index)), Optional.empty(), mouseX, mouseY);
                return;
            }
        }
        if (this.inside(mouseX, mouseY, this.cargoLeft, this.cargoTop, this.cargoRight, this.cargoBottom)) {
            int index = this.cargoScroll + (mouseY - this.cargoTop) / CARGO_ROW_H;
            if (index >= 0 && index < this.cargoEntries.size()) {
                CargoEntry entry = this.cargoEntries.get(index);
                List<Component> tooltip = new ArrayList<>();
                tooltip.add(Component.literal(entry.name));
                tooltip.add(Component.literal("Aboard: " + entry.compact(entry.held)).withStyle(ChatFormatting.GRAY));
                if (entry.toStation > 0) {
                    tooltip.add(Component.literal("Unloaded to yard: " + entry.compact(entry.toStation))
                        .withColor(UiFx.LIT_TO_STATION & 0xFFFFFF));
                }
                if (entry.toTrain > 0) {
                    tooltip.add(Component.literal("Loaded to train: " + entry.compact(entry.toTrain))
                        .withColor(UiFx.LIT_TO_TRAIN & 0xFFFFFF));
                }
                if (entry.hasGoal) {
                    tooltip.add(Component.literal(entry.unloadGoal
                        ? "The manifest asks for this to come off."
                        : "The manifest asks for this to go on.").withStyle(ChatFormatting.DARK_GRAY));
                }
                g.renderTooltip(this.font, tooltip, Optional.empty(), mouseX, mouseY);
            }
        }
    }

    private List<Component> orderTooltip(DispatchRowEntry entry) {
        DeliveryOrderLineView line = entry.getLineView();
        List<Component> tooltip = new ArrayList<>();
        tooltip.add(Component.literal(line.getDisplayName()));
        tooltip.add(Component.literal(entry.getState().getLabel())
            .withColor(this.rowStatusColor(entry.getState()) & 0xFFFFFF)
            .append(Component.literal("  ").append(Component.literal(
                line.getOperation() == ManifestOperation.UNLOAD ? "to the yard" : "to the train")
                .withColor((line.getOperation() == ManifestOperation.UNLOAD
                    ? UiFx.LIT_TO_STATION : UiFx.LIT_TO_TRAIN) & 0xFFFFFF))));
        tooltip.add(Component.literal(line.getStatusText()).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("Progress: "
            + FreightManifestLogic.formatAmount(entry.getCargoKind(), line.getDeliveredAmount()) + " / "
            + FreightManifestLogic.formatAmount(entry.getCargoKind(), line.getTargetAmount())
            + "  (" + Math.round(entry.getProgress() * 100f) + "%)").withStyle(ChatFormatting.GRAY));
        if (line.getFreightAmount() > 0) {
            tooltip.add(Component.literal("Aboard the train: "
                + FreightManifestLogic.formatAmount(entry.getCargoKind(), line.getFreightAmount()))
                .withStyle(ChatFormatting.AQUA));
        }
        tooltip.add(Component.literal("Cycle: " + line.getResetMode().getEditorLabel(line.getResetIntervalValue()))
            .withStyle(ChatFormatting.DARK_GRAY));
        if (line.getResetMode() == DeliveryOrderResetMode.ONCE) {
            tooltip.add(Component.literal("Retires itself from the manifest once fulfilled.")
                .withStyle(ChatFormatting.DARK_GRAY));
        } else if (line.getTicksUntilReset() > 0L) {
            tooltip.add(Component.literal("Next reset in " + FreightManifestLogic.formatTicks(line.getTicksUntilReset()))
                .withStyle(ChatFormatting.DARK_GRAY));
        }
        return tooltip;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        this.layout();
        for (Page value : Page.values()) {
            int i = value.ordinal();
            if (mouseX >= this.pageTabX[i] && mouseX < this.pageTabX[i] + this.pageTabW[i]
                    && mouseY >= this.pageTabY && mouseY < this.pageTabY + 13) {
                this.setPage(value);
                return true;
            }
        }
        if (this.page == Page.UPCOMING) {
            int split = this.innerLeft + Math.max(214, (this.innerRight - this.innerLeft) * 2 / 5);
            int rowsY = this.statusTop + 23;
            int rowH = 38;
            if (mouseX >= this.innerLeft && mouseX < split - 5
                    && mouseY >= rowsY && mouseY < this.footerTop - 9) {
                int index = this.upcomingScroll + (int) ((mouseY - rowsY) / rowH);
                if (index >= 0 && index < this.rosterTrains().size()) {
                    this.upcomingSelected = index;
                    this.upcomingCargoScroll = 0;
                    this.playClack(1.1f);
                }
                return true;
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }
        if (this.page == Page.HISTORY) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        for (Filter value : Filter.values()) {
            int i = value.ordinal();
            if (mouseX >= this.chipX[i] && mouseX < this.chipX[i] + this.chipW[i]
                && mouseY >= this.chipY && mouseY < this.chipY + 11) {
                this.setFilter(value);
                return true;
            }
        }
        if (this.inside((int) mouseX, (int) mouseY, this.innerLeft, this.rowsTop, this.innerRight, this.rowsBottom)) {
            int index = this.rowIndexAt(mouseY);
            if (index < 0 || index >= this.rows.size()) {
                return true;
            }
            this.selected = index;
            if (!this.canEdit) {
                return true;
            }
            DeliveryOrderLineView line = this.rows.get(index).getLineView();
            int rowTop = this.rowTopFor(index);
            if (mouseY >= rowTop + 4 && mouseY < rowTop + 18) {
                if (mouseX >= this.innerRight - ACTIONS_W && mouseX < this.innerRight - ACTIONS_W + 62) {
                    this.sendLine(line, line.isComplete()
                        ? DeliveryOrderAction.RESET_CYCLE : DeliveryOrderAction.MARK_COMPLETE);
                    return true;
                }
                if (!this.isOneShotArchive(line) && (line.isComplete() || line.isDismissed())
                    && mouseX >= this.innerRight - 56 && mouseX < this.innerRight - 8) {
                    this.sendLine(line, DeliveryOrderAction.TOGGLE_DISMISSED);
                    return true;
                }
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        this.layout();
        int step = (int) Math.signum(scrollY);
        if (this.page == Page.UPCOMING) {
            int split = this.innerLeft + Math.max(214, (this.innerRight - this.innerLeft) * 2 / 5);
            int bottom = this.footerTop - 9;
            int rowsY = this.statusTop + 23;
            if (mouseX < split) {
                int visible = Math.max(1, (bottom - rowsY - 2) / 38);
                int max = Math.max(0, this.rosterTrains().size() - visible);
                this.upcomingScroll = Math.max(0, Math.min(max, this.upcomingScroll - step));
            } else {
                List<TrainSummarySnapshot> trains = this.rosterTrains();
                int size = trains.isEmpty() ? 0
                        : this.buildSnapshotCargoEntries(trains.get(Math.max(0,
                            Math.min(this.upcomingSelected, trains.size() - 1)))).size();
                int visible = Math.max(1, (bottom - (this.statusTop + 62) - 2) / 22);
                this.upcomingCargoScroll = Math.max(0,
                        Math.min(Math.max(0, size - visible), this.upcomingCargoScroll - step));
            }
            return true;
        }
        if (this.page == Page.HISTORY) {
            int split = this.innerLeft + (this.innerRight - this.innerLeft) * 3 / 5;
            int visible = Math.max(1, (this.footerTop - 9 - (this.statusTop + 23) - 2) / 36);
            if (mouseX < split) {
                this.historyScroll = Math.max(0, Math.min(
                        Math.max(0, this.deliveryBoard.getHistoryEntries().size() - visible),
                        this.historyScroll - step));
            } else {
                this.visitScroll = Math.max(0, Math.min(
                        Math.max(0, this.stationRoster.getRecentVisits().size() - visible),
                        this.visitScroll - step));
            }
            return true;
        }
        if (this.inside((int) mouseX, (int) mouseY, this.innerLeft, this.rowsTop, this.innerRight, this.rowsBottom)) {
            int max = Math.max(0, this.rows.size() - this.visibleRows);
            if (max > 0) {
                this.dispatchScroll = Math.max(0, Math.min(max, this.dispatchScroll - step));
                return true;
            }
        }
        if (this.inside((int) mouseX, (int) mouseY, this.cargoLeft, this.cargoTop, this.cargoRight, this.cargoBottom)) {
            int max = Math.max(0, this.cargoEntries.size() - this.bottomRows);
            if (max > 0) {
                this.cargoScroll = Math.max(0, Math.min(max, this.cargoScroll - step));
                return true;
            }
        }
        if (this.inside((int) mouseX, (int) mouseY, this.histLeft, this.histTop, this.histRight, this.histBottom)) {
            int max = Math.max(0, this.deliveryBoard.getHistoryEntries().size() - this.bottomRows);
            if (max > 0) {
                this.historyScroll = Math.max(0, Math.min(max, this.historyScroll - step));
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_TAB) {
            this.setPage(this.page.next());
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_LEFT) {
            this.setPage(Page.values()[(this.page.ordinal() + Page.values().length - 1) % Page.values().length]);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_RIGHT) {
            this.setPage(this.page.next());
            return true;
        }
        if (this.page == Page.UPCOMING && (keyCode == GLFW.GLFW_KEY_DOWN || keyCode == GLFW.GLFW_KEY_UP)) {
            List<TrainSummarySnapshot> trains = this.rosterTrains();
            if (!trains.isEmpty()) {
                int delta = keyCode == GLFW.GLFW_KEY_DOWN ? 1 : -1;
                this.upcomingSelected = Math.max(0, Math.min(trains.size() - 1,
                        this.upcomingSelected + delta));
                this.upcomingCargoScroll = 0;
            }
            return true;
        }
        if (this.page != Page.LIVE) {
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        switch (keyCode) {
            case GLFW.GLFW_KEY_DOWN -> {
                this.moveSelection(1);
                return true;
            }
            case GLFW.GLFW_KEY_UP -> {
                this.moveSelection(-1);
                return true;
            }
            case GLFW.GLFW_KEY_F -> {
                this.setFilter(this.filter.next());
                return true;
            }
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                if (this.canEdit && this.selected >= 0 && this.selected < this.rows.size()) {
                    DeliveryOrderLineView line = this.rows.get(this.selected).getLineView();
                    this.sendLine(line, line.isComplete()
                        ? DeliveryOrderAction.RESET_CYCLE : DeliveryOrderAction.MARK_COMPLETE);
                    return true;
                }
            }
            case GLFW.GLFW_KEY_DELETE -> {
                if (this.canEdit && this.selected >= 0 && this.selected < this.rows.size()) {
                    DeliveryOrderLineView line = this.rows.get(this.selected).getLineView();
                    if (!this.isOneShotArchive(line) && (line.isComplete() || line.isDismissed())) {
                        this.sendLine(line, DeliveryOrderAction.TOGGLE_DISMISSED);
                        return true;
                    }
                }
            }
            default -> {
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void moveSelection(int delta) {
        if (this.rows.isEmpty()) {
            return;
        }
        this.selected = this.selected < 0 ? 0
            : Math.max(0, Math.min(this.rows.size() - 1, this.selected + delta));

        if (this.selected < this.dispatchScroll) {
            this.dispatchScroll = this.selected;
        } else if (this.selected >= this.dispatchScroll + this.visibleRows) {
            this.dispatchScroll = this.selected - this.visibleRows + 1;
        }
        this.playClack(1.15f);
    }

    private void setFilter(Filter value) {
        if (this.filter == value) {
            return;
        }
        this.filter = value;
        this.selected = -1;
        this.dispatchScroll = 0;
        this.animScrollPx = 0f;
        this.applyFilter();
        this.playClack(0.9f);
        this.layout();
    }

    private void setPage(Page value) {
        if (this.page == value) {
            return;
        }
        this.page = value;
        this.playClack(0.95f + value.ordinal() * 0.08f);
        this.rebuildWidgets();
    }

    private void sendLine(DeliveryOrderLineView line, DeliveryOrderAction action) {
        this.playClack(action == DeliveryOrderAction.RESET_CYCLE ? 0.85f : 1.05f);
        PacketDistributor.sendToServer(
            new FreightManifestDeliveryOrderActionPacket(this.blockPos, line.getLineId(), action));
    }

    private void playClack(float pitch) {
        Minecraft.getInstance().getSoundManager()
            .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, pitch));
    }

    public boolean isForBoard(BlockPos pos) {
        return this.blockPos.equals(pos);
    }

    public void updateLiveState(FreightManifest manifest, FreightStationCargoView cargoView,
                               DeliveryOrderBoardView deliveryBoard, StationTrainRoster stationRoster) {
        final List<TrainSummarySnapshot> before = this.rosterTrains();
        final UUID selectedTrain = this.upcomingSelected >= 0 && this.upcomingSelected < before.size()
                ? before.get(this.upcomingSelected).getTrainId() : null;
        this.manifest = manifest.copy();
        this.cargoView = cargoView.copy();
        this.deliveryBoard = deliveryBoard;
        this.stationRoster = stationRoster.copy();
        if (selectedTrain != null) {
            final List<TrainSummarySnapshot> after = this.rosterTrains();
            int found = -1;
            for (int i = 0; i < after.size(); i++) {
                if (selectedTrain.equals(after.get(i).getTrainId())) {
                    found = i;
                    break;
                }
            }
            if (found >= 0) {
                this.upcomingSelected = found;
            } else {
                this.upcomingCargoScroll = 0;
            }
        }
        this.rebuildDerivedState();

        this.layout();
        this.clampScroll();
        this.updateFooterState();
    }

    private void rebuildDerivedState() {
        this.headerState = BoardHeaderState.derive(this.stationLink, this.deliveryBoard);

        boolean trainPresent = this.deliveryBoard.isTrainPresent();
        this.allRows = new ArrayList<>();
        this.openCount = 0;
        this.doneCount = 0;
        this.nextLineId = null;
        for (int i = 0; i < this.deliveryBoard.getLineViews().size(); i++) {
            DeliveryOrderLineView line = this.deliveryBoard.getLineViews().get(i);
            this.allRows.add(new DispatchRowEntry(line, DispatchRowState.derive(line, trainPresent), i));
            if (line.isComplete()) {
                this.doneCount++;
            } else {
                this.openCount++;
                if (!line.isDismissed() && this.nextLineId == null) {
                    this.nextLineId = line.getLineId();
                }
            }
        }
        this.applyFilter();
        this.cargoEntries = this.buildCargoEntries();

        long tick = this.deliveryBoard.getCurrentTick();
        Set<UUID> live = new HashSet<>();
        for (DispatchRowEntry row : this.allRows) {
            UUID id = row.getLineView().getLineId();
            live.add(id);
            this.rates.computeIfAbsent(id, key -> new UiFx.RateTracker())
                .sample(tick, row.getDeliveredAmount());
        }
        this.rates.keySet().retainAll(live);
        this.rowProgressAnim.keySet().retainAll(live);
        this.rowHoverAnim.keySet().retainAll(live);
    }

    private void applyFilter() {
        this.rows = new ArrayList<>();
        for (DispatchRowEntry row : this.allRows) {
            boolean complete = row.getLineView().isComplete();
            boolean keep = switch (this.filter) {
                case OPEN -> !complete;
                case DONE -> complete;
                case ALL -> true;
            };
            if (keep) {
                this.rows.add(row);
            }
        }

        if (this.selected >= this.rows.size()) {
            this.selected = this.rows.isEmpty() ? -1 : this.rows.size() - 1;
        }
    }

    private void clampScroll() {
        this.dispatchScroll = Math.max(0, Math.min(Math.max(0, this.rows.size() - this.visibleRows), this.dispatchScroll));
        this.cargoScroll = Math.max(0, Math.min(Math.max(0, this.cargoEntries.size() - this.bottomRows), this.cargoScroll));
        this.historyScroll = Math.max(0, Math.min(
            Math.max(0, this.deliveryBoard.getHistoryEntries().size() - this.bottomRows), this.historyScroll));
        this.visitScroll = Math.max(0, Math.min(
            Math.max(0, this.stationRoster.getRecentVisits().size() - 1), this.visitScroll));
        this.upcomingScroll = Math.max(0, Math.min(
            Math.max(0, this.rosterTrains().size() - 1), this.upcomingScroll));
        if (!this.rosterTrains().isEmpty()) {
            this.upcomingSelected = Math.max(0,
                Math.min(this.rosterTrains().size() - 1, this.upcomingSelected));
        } else {
            this.upcomingSelected = 0;
        }
    }

    private List<CargoEntry> buildCargoEntries() {
        Map<FreightManifestResourceKey, Integer> expectedOff =
            FreightManifestLogic.getExpectedAmounts(this.manifest, ManifestOperation.UNLOAD);
        Map<FreightManifestResourceKey, Integer> expectedOn =
            FreightManifestLogic.getExpectedAmounts(this.manifest, ManifestOperation.LOAD);

        Set<FreightManifestResourceKey> keys = new java.util.LinkedHashSet<>();
        keys.addAll(this.cargoView.getFreightTotals().getAmounts().keySet());
        keys.addAll(this.cargoView.getMovedToStation().getAmounts().keySet());
        keys.addAll(this.cargoView.getMovedToTrain().getAmounts().keySet());
        keys.addAll(expectedOff.keySet());
        keys.addAll(expectedOn.keySet());

        List<CargoEntry> entries = new ArrayList<>();
        for (FreightManifestResourceKey key : keys) {
            entries.add(CargoEntry.of(key,
                this.cargoView.getFreightTotals().getAmount(key),
                this.cargoView.getMovedToStation().getAmount(key),
                this.cargoView.getMovedToTrain().getAmount(key),
                expectedOff.getOrDefault(key, 0),
                expectedOn.getOrDefault(key, 0)));
        }
        entries.sort(Comparator.comparing((CargoEntry e) -> !e.hasGoal)
            .thenComparing(e -> e.kind.ordinal())
            .thenComparing(e -> e.name));
        return entries;
    }

    private int computeDataSignature() {
        int sig = this.headerState == null ? 0 : this.headerState.ordinal();
        sig = sig * 31 + this.deliveryBoard.getCompletedOrders();
        sig = sig * 31 + this.deliveryBoard.getTotalOrders();
        sig = sig * 31 + this.deliveryBoard.getDismissedOrders();
        sig = sig * 31 + (this.deliveryBoard.isTrainPresent() ? 1 : 0);
        long delivered = 0L;
        for (DispatchRowEntry row : this.allRows) {
            delivered += row.getDeliveredAmount();
        }
        return sig * 31 + (int) (delivered & 0x7FFFFFFFL);
    }

    private float targetBannerProgress() {
        long delivered = 0L;
        long target = 0L;
        for (DispatchRowEntry row : this.allRows) {
            delivered += Math.max(0, row.getDeliveredAmount());
            target += Math.max(0, row.getTargetAmount());
        }
        if (target > 0L) {
            return UiFx.clamp01(delivered / (float) target);
        }
        int total = this.deliveryBoard.getTotalOrders();
        int done = this.deliveryBoard.getCompletedOrders();
        return total > 0 ? UiFx.clamp01(done / (float) total) : 0f;
    }

    private int rowTopFor(int index) {
        return Math.round(this.rowsTop - this.animScrollPx + index * ROW_H);
    }

    private int rowIndexAt(double mouseY) {
        if (mouseY < this.rowsTop || mouseY >= this.rowsBottom) {
            return -1;
        }
        return (int) Math.floor((mouseY - this.rowsTop + this.animScrollPx) / ROW_H);
    }

    private boolean inside(int x, int y, int left, int top, int right, int bottom) {
        return x >= left && x < right && y >= top && y < bottom;
    }

    private boolean isOneShotArchive(DeliveryOrderLineView line) {
        return line.isComplete() && line.getResetMode() == DeliveryOrderResetMode.ONCE;
    }

    private int countState(DispatchRowState target) {
        int count = 0;
        for (DispatchRowEntry row : this.allRows) {
            if (row.getState() == target) {
                count++;
            }
        }
        return count;
    }

    private boolean hasRevealableHiddenOrders() {
        for (DispatchRowEntry row : this.allRows) {
            DeliveryOrderLineView line = row.getLineView();
            if (line.isDismissed() && !this.isOneShotArchive(line)) {
                return true;
            }
        }
        return false;
    }

    private int rowStatusColor(DispatchRowState state) {
        return switch (state) {
            case COMPLETE -> UiFx.LIT_OK;
            case COMPLETE_HIDDEN -> UiFx.LIT_FAINT;
            case IN_PROGRESS -> UiFx.LIT_WARN;
            case TRACKING -> UiFx.LIT_LIVE;
            case INVALID, NEEDS_SETUP -> UiFx.LIT_BAD;
            case WAITING_NEXT_CYCLE -> UiFx.BRASS;
            default -> UiFx.LIT_IDLE;
        };
    }

    private int statusColor() {
        return switch (this.headerState) {
            case READY -> UiFx.LIT_OK;
            case LOADING, UNLOADING -> UiFx.LIT_WARN;
            case ARRIVING, PRESENT -> UiFx.LIT_LIVE;
            case WAITING_ON_CARGO -> UiFx.LIT_WARN;
            case ERROR -> UiFx.LIT_BAD;
            default -> UiFx.LIT_IDLE;
        };
    }

    private String statusHeadline() {
        return switch (this.headerState) {
            case UNLINKED -> "NOT LINKED";
            case IDLE -> "AWAITING TRAIN";
            case ARRIVING -> "TRAIN ARRIVING";
            case PRESENT -> "TRAIN DOCKED";
            case LOADING -> "LOADING TRAIN";
            case UNLOADING -> "UNLOADING TRAIN";
            case WAITING_ON_CARGO -> "WAITING ON CARGO";
            case READY -> "DISPATCH COMPLETE";
            case ERROR -> "ATTENTION NEEDED";
        };
    }

    private String statusDetail() {
        int remaining = Math.max(0, this.deliveryBoard.getTotalOrders()
            - this.deliveryBoard.getCompletedOrders() - this.deliveryBoard.getDismissedOrders());
        int active = this.countState(DispatchRowState.IN_PROGRESS);
        int broken = this.countState(DispatchRowState.INVALID) + this.countState(DispatchRowState.NEEDS_SETUP);
        return switch (this.headerState) {
            case UNLINKED -> "Shift-click with a bound clipboard to attach a station";
            case IDLE -> remaining > 0
                ? remaining + " order" + plural(remaining) + " staged for the next train"
                : "No dispatch orders configured yet";
            case ARRIVING -> "Inbound train detected, preparing the berth";
            case PRESENT -> this.carriageLine("Train standing at the berth");
            case LOADING -> Math.max(1, active) + " order" + plural(Math.max(1, active)) + " moving cargo onto the train";
            case UNLOADING -> Math.max(1, active) + " order" + plural(Math.max(1, active)) + " moving cargo into the yard";
            case WAITING_ON_CARGO -> "Train docked, but nothing is moving yet";
            case READY -> "All orders complete, the train is free to depart";
            case ERROR -> broken > 0 ? broken + " order" + plural(broken) + " need setup" : "Some orders need attention";
        };
    }

    private String carriageLine(String prefix) {
        int carriages = this.cargoView.getCarriageCount();
        return carriages > 0 ? prefix + " · " + carriages + " carriage" + plural(carriages) : prefix;
    }

    private boolean isMoving() {
        for (UiFx.RateTracker tracker : this.rates.values()) {
            if (tracker.isMoving()) {
                return true;
            }
        }
        return false;
    }

    private int flowDirection() {
        boolean toTrain = false;
        boolean toStation = false;
        for (DispatchRowEntry row : this.allRows) {
            UiFx.RateTracker tracker = this.rates.get(row.getLineView().getLineId());
            if (tracker == null || !tracker.isMoving()) {
                continue;
            }
            if (row.getOperation() == ManifestOperation.UNLOAD) {
                toStation = true;
            } else {
                toTrain = true;
            }
        }
        if (toTrain == toStation) {
            return 0;
        }
        return toTrain ? 1 : -1;
    }

    private int historyColor(DeliveryOrderHistoryType type) {
        return switch (type) {
            case COMPLETED, MANUAL_COMPLETE -> UiFx.LIT_OK;
            case SHOWN -> UiFx.LIT_DIM;
            case HIDDEN -> UiFx.LIT_FAINT;
            case AUTO_RESET, MANUAL_RESET -> UiFx.BRASS;
        };
    }

    private static String plural(int n) {
        return n == 1 ? "" : "s";
    }

    private void drawScaled(GuiGraphics g, String text, int x, int y, int color, int scale) {
        g.pose().pushPose();
        g.pose().scale(scale, scale, 1.0f);
        g.drawString(this.font, text, x / scale, y / scale, color, false);
        g.pose().popPose();
    }

    private void drawRight(GuiGraphics g, String text, int right, int y, int color) {
        g.drawString(this.font, text, right - this.font.width(text), y, color, false);
    }

    private String trim(String text, int width) {
        if (width <= 0) {
            return "";
        }
        if (this.font.width(text) <= width) {
            return text;
        }
        int max = Math.max(0, width - this.font.width("…"));
        return this.font.plainSubstrByWidth(text, max) + "…";
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static final class CargoEntry {
        private final ManifestCargoKind kind;
        private final String name;
        private final int held;
        private final int toStation;
        private final int toTrain;
        private final boolean hasGoal;
        private final boolean unloadGoal;
        private final ItemStack icon;

        private CargoEntry(ManifestCargoKind kind, String name, int held, int toStation, int toTrain,
                           int expectedOff, int expectedOn, ItemStack icon) {
            this.kind = kind;
            this.name = name;
            this.held = held;
            this.toStation = toStation;
            this.toTrain = toTrain;
            this.hasGoal = expectedOff > 0 || expectedOn > 0;
            this.unloadGoal = expectedOff > 0;
            this.icon = icon;
        }

        String compact(int amount) {
            if (this.kind == ManifestCargoKind.FLUID && amount >= 1000) {
                return amount % 1000 == 0 ? (amount / 1000) + "B"
                    : String.format(Locale.ROOT, "%.1fB", amount / 1000.0f);
            }
            if (amount >= 100000) {
                return (amount / 1000) + "k";
            }
            return FreightManifestLogic.formatAmount(this.kind, amount);
        }

        static CargoEntry of(FreightManifestResourceKey key, int held, int toStation, int toTrain,
                             int expectedOff, int expectedOn) {
            if (key.getCargoKind() == ManifestCargoKind.ITEM) {
                var item = BuiltInRegistries.ITEM.getOptional(key.getResourceId()).orElse(null);
                ItemStack icon = item == null ? ItemStack.EMPTY : new ItemStack(item);
                String name = icon.isEmpty() ? key.getResourceId().toString() : icon.getHoverName().getString();
                return new CargoEntry(ManifestCargoKind.ITEM, name, held, toStation, toTrain,
                    expectedOff, expectedOn, icon);
            }
            Fluid fluid = BuiltInRegistries.FLUID.getOptional(key.getResourceId()).orElse(null);
            ItemStack icon = ItemStack.EMPTY;
            String name = key.getResourceId().toString();
            if (fluid != null) {
                FluidStack stack = new FluidStack(fluid, 1000);
                icon = FluidUtil.getFilledBucket(stack);
                name = stack.getDisplayName().getString();
            }
            return new CargoEntry(ManifestCargoKind.FLUID, name, held, toStation, toTrain,
                expectedOff, expectedOn, icon);
        }
    }
}
