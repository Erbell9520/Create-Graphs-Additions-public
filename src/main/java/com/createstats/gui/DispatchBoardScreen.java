package com.createstats.gui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.createstats.dispatch.BoardHeaderState;
import com.createstats.dispatch.DispatchRowEntry;
import com.createstats.dispatch.DispatchRowState;
import com.createstats.network.FreightManifestDeliveryOrderActionPacket;
import com.createstats.network.RequestDispatchBoardStatePacket;
import net.neoforged.neoforge.network.PacketDistributor;
import com.createstats.train.manifest.DeliveryOrderAction;
import com.createstats.train.manifest.DeliveryOrderBoardView;
import com.createstats.train.manifest.DeliveryOrderHistoryEntry;
import com.createstats.train.manifest.DeliveryOrderHistoryType;
import com.createstats.train.manifest.DeliveryOrderLineView;
import com.createstats.train.manifest.FreightManifest;
import com.createstats.train.manifest.FreightManifestLogic;
import com.createstats.train.manifest.FreightManifestResourceKey;
import com.createstats.train.manifest.FreightStationCargoView;
import com.createstats.train.manifest.FreightStationLink;
import com.createstats.train.manifest.ManifestCargoKind;
import com.createstats.train.manifest.ManifestOperation;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
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
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;

/** [1] */
public class DispatchBoardScreen extends Screen {

    // Colors
    private static final int PANEL_BG = 0xFFE9D9B7;
    private static final int PANEL_INNER = 0xFFF3E8CC;
    private static final int BORDER = 0xFF705438;
    private static final int ACCENT = 0xFF94622A;
    private static final int BRASS = 0xFFBF9150;
    private static final int TEXT = 0xFF342718;
    private static final int DIM = 0xFF7B6951;
    private static final int GREEN = 0xFF9AEF9E;
    private static final int RED = 0xFFFFB08F;
    private static final int SLOT_BG = 0xFFF0E5CA;
    private static final int SLOT_HOVER = 0x66D9C08F;
    private static final int SLOT_SELECTED = 0x88D8B46A;
    private static final int HEADER_BG = 0xFFE2D0AA;
    private static final int PAPER_RULE = 0x18A88F66;

    // Premium backdrop + electronic status banner palette (dark, to contrast the paper sheet)
    private static final int BACKDROP_TOP = 0xE6121016;
    private static final int BACKDROP_BOTTOM = 0xF21A1310;
    private static final int BACKDROP_GRID = 0x14C9A24A;
    private static final int PANEL_SHADOW = 0x66000000;
    private static final int BANNER_BG = 0xFF1B1F26;
    private static final int BANNER_BG_DEEP = 0xFF13161D;
    private static final int BANNER_TEXT = 0xFFF1E6C9;
    private static final int BANNER_DIM = 0xFFA59A82;
    private static final int BANNER_TRACK = 0xFF0D0F14;

    // Layout constants
    private static final int DISPATCH_ROW_HEIGHT = 28;
    private static final int CARGO_ROW_HEIGHT = 18;
    private static final int VISIBLE_CARGO_ROWS = 4;
    private static final int HISTORY_ROW_HEIGHT = 18;
    private static final int VISIBLE_HISTORY_ROWS = 4;

    // Core data
    private final BlockPos blockPos;
    private final FreightStationLink stationLink;
    private final boolean canEdit;
    private FreightManifest manifest;
    private FreightStationCargoView cargoView;
    private DeliveryOrderBoardView deliveryBoard;

    // Derived view state (rebuilt on every data update)
    private BoardHeaderState headerState;
    private List<DispatchRowEntry> dispatchRows;
    private List<CargoEntry> cargoEntries;

    // Scroll and layout
    private int panelLeft, panelTop, panelWidth, panelHeight;
    private int dispatchScroll;
    private int cargoScroll;
    private int historyScroll;
    private int refreshCooldown;
    private int visibleDispatchRows = 9;

    // Computed region bounds (set in init, used for click/scroll hit testing)
    private int bannerTop, bannerBottom;
    private int rowsTop, rowsBottom, rowsLeft, rowsRight;
    private int cargoLeft, cargoRight, cargoTop, cargoBottom;
    private int histLeft, histRight, histTop, histBottom;

    // Animation state (frame-rate independent smoothing)
    private float frameTime;
    private float frameDt;
    private float lastFrameTime = Float.NaN;
    private float animScrollPx;
    private float bannerProgress;
    private float refreshFlash;
    private int dataSignature = Integer.MIN_VALUE;
    private final Map<UUID, Float> rowProgressAnim = new HashMap<>();
    private final Map<UUID, Float> rowHoverAnim = new HashMap<>();

    public DispatchBoardScreen(BlockPos blockPos, FreightStationLink stationLink, FreightManifest manifest,
                               FreightStationCargoView cargoView, DeliveryOrderBoardView deliveryBoard,
                               boolean canEdit) {
        super(Component.literal("Dispatch Board"));
        this.blockPos = blockPos;
        this.stationLink = stationLink.copy();
        this.manifest = manifest.copy();
        this.canEdit = canEdit;
        this.cargoView = cargoView == null ? FreightStationCargoView.unavailable("Waiting for cargo data...") : cargoView;
        this.deliveryBoard = deliveryBoard == null ? DeliveryOrderBoardView.empty(0L, "Waiting for delivery data...") : deliveryBoard;
        this.refreshCooldown = 0;
        this.rebuildDerivedState();
        this.dataSignature = this.computeDataSignature();
        this.bannerProgress = this.targetBannerProgress();
        this.seedRowProgressAnim();
    }

    // lifecycle

    @Override
    protected void init() {
        super.init();
        this.clearWidgets();
        this.panelWidth = Math.min(this.width - 40, 632);
        this.panelHeight = Math.min(this.height - 40, 480);
        this.panelLeft = (this.width - this.panelWidth) / 2;
        this.panelTop = (this.height - this.panelHeight) / 2;
        this.computeRegionBounds();
        this.addActionButtons();
    }

    @Override
    protected void rebuildWidgets() {
        this.init();
    }

    @Override
    public void tick() {
        super.tick();
        if (this.stationLink.isLinked() && --this.refreshCooldown <= 0) {
            this.refreshCooldown = 5;
            PacketDistributor.sendToServer(new RequestDispatchBoardStatePacket(this.blockPos));
        }
    }

    private void computeRegionBounds() {
        int margin = 14;
        int innerLeft = this.panelLeft + margin;
        int innerRight = this.panelLeft + this.panelWidth - margin;

        // Activity banner sits under the slim title line.
        this.bannerTop = this.panelTop + 24;
        this.bannerBottom = this.bannerTop + 56;

        // Action buttons live on the bottom edge; bottom panels sit just above them.
        int buttonsY = this.panelTop + this.panelHeight - 28;
        int bottomBottom = buttonsY - 10;
        int bottomTop = bottomBottom - VISIBLE_CARGO_ROWS * CARGO_ROW_HEIGHT;
        int midX = innerLeft + (innerRight - innerLeft) / 2 - 4;

        this.cargoLeft = innerLeft;
        this.cargoRight = midX;
        this.cargoTop = bottomTop;
        this.cargoBottom = bottomBottom;

        this.histLeft = midX + 8;
        this.histRight = innerRight;
        this.histTop = bottomTop;
        this.histBottom = bottomBottom;

        // Dispatch rows fill everything between the banner and the bottom panels.
        this.rowsLeft = innerLeft;
        this.rowsRight = innerRight;
        this.rowsTop = this.bannerBottom + 16;
        int available = bottomTop - 16 - this.rowsTop;
        this.visibleDispatchRows = Math.max(3, available / DISPATCH_ROW_HEIGHT);
        this.rowsBottom = this.rowsTop + this.visibleDispatchRows * DISPATCH_ROW_HEIGHT;
    }

    private void addActionButtons() {
        int buttonsY = this.panelTop + this.panelHeight - 28;

        if (this.canEdit) {
            Button hideDone = Button.builder(Component.literal("Hide Done"), b ->
                PacketDistributor.sendToServer(new FreightManifestDeliveryOrderActionPacket(this.blockPos, null, DeliveryOrderAction.HIDE_COMPLETED)))
                .bounds(this.panelLeft + 14, buttonsY, 82, 20).build();
            hideDone.active = this.hasCompletedVisibleOrders();
            this.addRenderableWidget(hideDone);

            Button showHidden = Button.builder(Component.literal("Show Hidden"), b ->
                PacketDistributor.sendToServer(new FreightManifestDeliveryOrderActionPacket(this.blockPos, null, DeliveryOrderAction.SHOW_HIDDEN)))
                .bounds(this.panelLeft + 102, buttonsY, 90, 20).build();
            showHidden.active = this.hasHiddenOrders();
            this.addRenderableWidget(showHidden);

            Button resetDone = Button.builder(Component.literal("Reset Done"), b ->
                PacketDistributor.sendToServer(new FreightManifestDeliveryOrderActionPacket(this.blockPos, null, DeliveryOrderAction.RESET_COMPLETED)))
                .bounds(this.panelLeft + 198, buttonsY, 86, 20).build();
            resetDone.active = this.hasCompletedOrders();
            this.addRenderableWidget(resetDone);
        }

        this.addRenderableWidget(Button.builder(Component.literal("Close"), b -> this.onClose())
            .bounds(this.panelLeft + this.panelWidth - 94, buttonsY, 80, 20).build());
    }

    // rendering

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.frameTime = this.animationTime(partialTick);
        this.frameDt = Float.isNaN(this.lastFrameTime) ? 0f : Math.max(0f, Math.min(4f, this.frameTime - this.lastFrameTime));
        this.lastFrameTime = this.frameTime;
        this.updateAnimations(this.frameDt);

        this.renderBackdrop(g, this.frameTime);
        // FE Dependent Mode panel in the free screen corner (self-gating). Resolves the live
        // controller BE from the client level (only the controller holds the drained buffer).
        if (net.minecraft.client.Minecraft.getInstance().level != null
                && net.minecraft.client.Minecraft.getInstance().level.getBlockEntity(this.blockPos)
                        instanceof com.createstats.blockentity.DispatchBoardBE dispatchBoardBE) {
            com.createstats.power.client.FeStatusWidget.draw(g, this.font, this.width - 130, 8, 120,
                    dispatchBoardBE.getControllerBE().feConsumer());
        }
        this.renderPaperFrame(g);

        this.renderTitle(g);
        this.renderActivityBanner(g, this.frameTime);
        this.renderDispatchRows(g, mouseX, mouseY);
        this.renderCargoSummary(g, mouseX, mouseY);
        this.renderHistoryStrip(g, mouseX, mouseY);

        // Tooltips last (on top of everything)
        super.render(g, mouseX, mouseY, partialTick);
        this.renderTooltips(g, mouseX, mouseY);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
    }

    private float animationTime(float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            return mc.level.getGameTime() + partialTick;
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

        this.animScrollPx = DispatchUiFx.approach(this.animScrollPx,
                this.dispatchScroll * (float) DISPATCH_ROW_HEIGHT, dt, 0.55f);
        this.bannerProgress = DispatchUiFx.approach(this.bannerProgress, this.targetBannerProgress(), dt, 0.16f);

        for (DispatchRowEntry entry : this.dispatchRows) {
            UUID id = entry.getLineView().getLineId();
            float cur = this.rowProgressAnim.getOrDefault(id, entry.getProgress());
            this.rowProgressAnim.put(id, DispatchUiFx.approach(cur, entry.getProgress(), dt, 0.22f));
        }
    }

    private void renderBackdrop(GuiGraphics g, float time) {
        g.fillGradient(0, 0, this.width, this.height, BACKDROP_TOP, BACKDROP_BOTTOM);
        int spacing = 30;
        int drift = (int) (time * 0.35f) % spacing;
        for (int x = -drift; x < this.width; x += spacing) {
            g.fill(x, 0, x + 1, this.height, BACKDROP_GRID);
        }
        for (int y = drift - spacing; y < this.height; y += spacing) {
            g.fill(0, y, this.width, y + 1, BACKDROP_GRID & 0x0FFFFFFF);
        }
        // Drop shadow under the paper sheet for depth.
        g.fill(this.panelLeft + 6, this.panelTop + 8, this.panelLeft + this.panelWidth + 6, this.panelTop + this.panelHeight + 8, PANEL_SHADOW);
    }

    private void renderTitle(GuiGraphics g) {
        int x1 = this.panelLeft + 16;
        int y1 = this.panelTop + 9;
        this.drawPaperText(g, "Dispatch Board", x1, y1, ACCENT);
        this.drawPaperText(g, "yard engineering docket", x1 + this.font.width("Dispatch Board") + 8, y1, DIM);
        if (!this.canEdit) {
            String ro = "Read only";
            this.drawPaperText(g, ro, this.panelLeft + this.panelWidth - 16 - this.font.width(ro), y1, DIM);
        }
    }

    /** [2] */
    private void renderActivityBanner(GuiGraphics g, float time) {
        int left = this.panelLeft + 14;
        int right = this.panelLeft + this.panelWidth - 14;
        int top = this.bannerTop;
        int bottom = this.bannerBottom;
        int state = this.statusArgb();
        boolean active = this.headerState == BoardHeaderState.LOADING
                || this.headerState == BoardHeaderState.UNLOADING
                || this.headerState == BoardHeaderState.ARRIVING;
        boolean attention = this.headerState == BoardHeaderState.ERROR
                || this.headerState == BoardHeaderState.WAITING_ON_CARGO;

        // Plate + frame.
        g.fill(left, top, right, bottom, BANNER_BG_DEEP);
        g.fill(left + 1, top + 1, right - 1, bottom - 1, BANNER_BG);
        DispatchUiFx.outline(g, left, top, right - left, bottom - top, DispatchUiFx.lerpColor(BANNER_BG, state, 0.5f));

        // Animated left state bar.
        float barPulse = active || attention ? (0.55f + 0.45f * DispatchUiFx.pulse01(time, 0.30f)) : 0.85f;
        int barColor = DispatchUiFx.lerpColor(BANNER_BG, state, barPulse);
        g.fill(left + 1, top + 1, left + 6, bottom - 1, barColor);

        int textLeft = left + 14;
        int rightZoneW = Math.min(232, (right - left) * 2 / 5);
        int rightLeft = right - rightZoneW - 6;

        // Headline (scaled) + live dot.
        this.drawScaledString(g, this.statusHeadline(), textLeft, top + 7, DispatchUiFx.lerpColor(BANNER_TEXT, state, 0.65f), 2);
        int dotColor = DispatchUiFx.lerpColor(state, 0xFFFFFFFF, this.refreshFlash * 0.7f);
        DispatchUiFx.pulseDot(g, right - 12, top + 12, 2, dotColor, time, active ? 0.5f : 0.22f);

        // Detail line.
        String detail = this.statusDetail();
        this.drawPaperText(g, this.trimToWidth(detail, rightLeft - textLeft - 8), textLeft, top + 27, BANNER_DIM);

        // Right zone: percentage + animated aggregate bar.
        boolean showProgress = this.headerState != BoardHeaderState.UNLINKED && this.deliveryBoard.getTotalOrders() > 0;
        if (showProgress) {
            int pct = Math.round(this.bannerProgress * 100f);
            String pctText = pct + "%";
            this.drawScaledString(g, pctText, rightLeft, top + 5, DispatchUiFx.lerpColor(BANNER_TEXT, state, 0.7f), 2);
            String counts = this.deliveryBoard.getCompletedOrders() + "/" + this.deliveryBoard.getTotalOrders() + " done";
            this.drawPaperText(g, counts, rightLeft + this.font.width(pctText) * 2 + 8, top + 9, BANNER_DIM);
            DispatchUiFx.liveProgressBar(g, rightLeft, top + 24, rightZoneW, 11,
                    this.bannerProgress, state, BANNER_TRACK, DispatchUiFx.lerpColor(BANNER_BG, state, 0.4f), active, time);
        } else if (this.headerState == BoardHeaderState.READY) {
            this.drawScaledString(g, "READY", rightLeft, top + 14, state, 2);
        }

        // Cargo-flow lane: yard <-> train with directional chevrons.
        this.renderFlowLane(g, textLeft, bottom - 13, right - 8, 10, state, active, time);
    }

    private void renderFlowLane(GuiGraphics g, int left, int top, int right, int h, int state, boolean active, float time) {
        int laneLeft = left + 30;
        int laneRight = right - 34;
        g.fill(left, top, right, top + h, BANNER_TRACK);
        DispatchUiFx.outline(g, left, top, right - left, h, DispatchUiFx.lerpColor(BANNER_BG, state, 0.3f));

        // End labels.
        this.drawPaperText(g, "YARD", left + 3, top + 1, BANNER_DIM);
        String trainLbl = "TRAIN";
        this.drawPaperText(g, trainLbl, right - this.font.width(trainLbl) - 3, top + 1, BANNER_DIM);

        int dir = switch (this.headerState) {
            case LOADING, ARRIVING -> 1;
            case UNLOADING -> -1;
            default -> 0;
        };
        if (dir != 0 && active && laneRight > laneLeft + 8) {
            DispatchUiFx.marchingChevrons(g, laneLeft, top + 1, laneRight - laneLeft, h - 2, DispatchUiFx.lerpColor(state, 0xFFFFFFFF, 0.1f), time, dir);
        } else {
            String word = switch (this.headerState) {
                case PRESENT -> "DOCKED";
                case READY -> "COMPLETE";
                case WAITING_ON_CARGO -> "NO TRANSFER";
                case ARRIVING -> "INBOUND";
                case UNLINKED -> "OFFLINE";
                default -> "IDLE";
            };
            int wc = DispatchUiFx.lerpColor(BANNER_DIM, state, 0.4f);
            this.drawPaperText(g, word, (laneLeft + laneRight) / 2 - this.font.width(word) / 2, top + 1, wc);
        }
    }

    private void drawScaledString(GuiGraphics g, String text, int x, int y, int color, int scale) {
        g.pose().pushPose();
        g.pose().scale(scale, scale, 1.0f);
        g.drawString(this.font, text, x / scale, y / scale, color, false);
        g.pose().popPose();
    }

    private int statusArgb() {
        return 0xFF000000 | this.headerState.getIndicatorColor();
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
        int err = this.countState(DispatchRowState.INVALID) + this.countState(DispatchRowState.NEEDS_SETUP);
        return switch (this.headerState) {
            case UNLINKED -> "Bind a clipboard to a train station to begin";
            case IDLE -> remaining > 0 ? remaining + " order" + plural(remaining) + " staged for next train" : "No dispatch orders configured yet";
            case ARRIVING -> "Inbound train detected, preparing berth";
            case PRESENT -> "Train standing by at the berth";
            case LOADING -> Math.max(1, active) + " order" + plural(Math.max(1, active)) + " moving cargo to the train";
            case UNLOADING -> Math.max(1, active) + " order" + plural(Math.max(1, active)) + " moving cargo to the yard";
            case WAITING_ON_CARGO -> "Train docked, but no cargo is moving yet";
            case READY -> "All orders complete, train ready to depart";
            case ERROR -> (err > 0 ? err + " order" + plural(err) + " need setup" : "Some orders need attention");
        };
    }

    private static String plural(int n) {
        return n == 1 ? "" : "s";
    }

    private int countState(DispatchRowState target) {
        int n = 0;
        for (DispatchRowEntry e : this.dispatchRows) {
            if (e.getState() == target) {
                n++;
            }
        }
        return n;
    }

    private int computeDataSignature() {
        int sig = this.headerState.ordinal() * 31 + this.deliveryBoard.getCompletedOrders();
        sig = sig * 31 + this.deliveryBoard.getTotalOrders();
        sig = sig * 31 + this.deliveryBoard.getDismissedOrders();
        sig = sig * 31 + (this.deliveryBoard.isTrainPresent() ? 1 : 0);
        long delivered = 0L;
        for (DispatchRowEntry e : this.dispatchRows) {
            delivered += e.getDeliveredAmount();
        }
        sig = sig * 31 + (int) (delivered & 0x7FFFFFFF);
        return sig;
    }

    private float targetBannerProgress() {
        long delivered = 0L;
        long target = 0L;
        for (DispatchRowEntry e : this.dispatchRows) {
            if (e.getState() == DispatchRowState.COMPLETE_HIDDEN) {
                continue;
            }
            delivered += Math.max(0, e.getDeliveredAmount());
            target += Math.max(0, e.getTargetAmount());
        }
        if (target <= 0) {
            int total = this.deliveryBoard.getTotalOrders();
            int done = this.deliveryBoard.getCompletedOrders() + this.deliveryBoard.getDismissedOrders();
            return total > 0 ? DispatchUiFx.clamp01(done / (float) total) : 0f;
        }
        return DispatchUiFx.clamp01(delivered / (float) target);
    }

    private void seedRowProgressAnim() {
        this.rowProgressAnim.clear();
        for (DispatchRowEntry e : this.dispatchRows) {
            this.rowProgressAnim.put(e.getLineView().getLineId(), e.getProgress());
        }
    }

    /** Visual top of a dispatch row for the given absolute index, honoring smooth scroll. */
    private int rowTopFor(int idx) {
        return Math.round(this.rowsTop - this.animScrollPx + idx * DISPATCH_ROW_HEIGHT);
    }

    /** Absolute dispatch-row index under the given mouse Y, or -1 if outside the rows region. */
    private int rowIndexAt(double mouseY) {
        if (mouseY < this.rowsTop || mouseY >= this.rowsBottom) {
            return -1;
        }
        return (int) Math.floor((mouseY - this.rowsTop + this.animScrollPx) / DISPATCH_ROW_HEIGHT);
    }

    private void renderDispatchRows(GuiGraphics g, int mouseX, int mouseY) {
        // Section label + live count
        String rowCountHint = this.dispatchRows.size() + (this.dispatchRows.size() == 1 ? " order" : " orders");
        this.drawPaperText(g, "Dispatch Orders", this.rowsLeft + 4, this.rowsTop - 10, TEXT);
        this.drawPaperText(g, rowCountHint, this.rowsRight - this.font.width(rowCountHint) - 4, this.rowsTop - 10, DIM);

        // Background
        this.renderPaperSection(g, this.rowsLeft, this.rowsTop, this.rowsRight, this.rowsBottom, SLOT_BG);

        if (this.dispatchRows.isEmpty()) {
            this.drawPaperText(g, "No dispatch orders. Use a clipboard to create a manifest.", this.rowsLeft + 8, this.rowsTop + 12, DIM);
            return;
        }

        boolean mouseInRows = mouseX >= this.rowsLeft && mouseX < this.rowsRight && mouseY >= this.rowsTop && mouseY < this.rowsBottom;
        int first = (int) Math.floor(this.animScrollPx / DISPATCH_ROW_HEIGHT);

        g.enableScissor(this.rowsLeft + 1, this.rowsTop + 1, this.rowsRight - 1, this.rowsBottom);
        for (int i = -1; i <= this.visibleDispatchRows; i++) {
            int idx = first + i;
            if (idx < 0 || idx >= this.dispatchRows.size()) {
                continue;
            }
            int rowTop = this.rowTopFor(idx);
            int rowBottom = rowTop + DISPATCH_ROW_HEIGHT - 1;
            if (rowBottom < this.rowsTop || rowTop > this.rowsBottom) {
                continue;
            }

            DispatchRowEntry entry = this.dispatchRows.get(idx);
            DeliveryOrderLineView line = entry.getLineView();
            UUID id = line.getLineId();
            DispatchRowState rowState = entry.getState();
            boolean isDimmed = rowState == DispatchRowState.COMPLETE_HIDDEN;
            boolean activeRow = rowState == DispatchRowState.IN_PROGRESS;
            boolean trackingRow = rowState == DispatchRowState.TRACKING;

            // Hover fade.
            boolean hovered = mouseInRows && mouseY >= rowTop && mouseY < rowBottom;
            float hv = DispatchUiFx.approach(this.rowHoverAnim.getOrDefault(id, 0f), hovered ? 1f : 0f, this.frameDt, 0.4f);
            this.rowHoverAnim.put(id, hv);

            // Row background (alternating + hover wash).
            g.fill(this.rowsLeft + 1, rowTop + 1, this.rowsRight - 1, rowBottom, (idx % 2 == 0) ? 0x18000000 : 0x10000000);
            if (hv > 0.01f) {
                g.fill(this.rowsLeft + 1, rowTop + 1, this.rowsRight - 1, rowBottom, DispatchUiFx.withAlpha(0xD9C08F, (int) (0x5A * hv)));
            }
            g.fill(this.rowsLeft + 1, rowBottom, this.rowsRight - 1, rowBottom + 1, PAPER_RULE);

            // Animated state marker bar (pulses while active/tracking).
            int stateColor = 0xFF000000 | entry.getStateColor();
            int markCol = stateColor;
            if (activeRow || trackingRow) {
                markCol = DispatchUiFx.lerpColor(stateColor, 0xFFFFFFFF, 0.30f * DispatchUiFx.pulse01(this.frameTime + idx * 7f, 0.34f));
            }
            g.fill(this.rowsLeft + 2, rowTop + 3, this.rowsLeft + 5, rowBottom - 2, markCol);

            // Cargo icon.
            if (!entry.getDisplayStack().isEmpty()) {
                g.renderItem(entry.getDisplayStack(), this.rowsLeft + 8, rowTop + 5);
            }

            // State badge + name.
            String stateBadge = entry.getState().getLabel();
            int badgeWidth = this.font.width(stateBadge) + 6;
            int badgeX = this.rowsLeft + 28;
            int stateInk = this.paperInk(stateColor);
            g.fill(badgeX, rowTop + 4, badgeX + badgeWidth, rowTop + 15, this.withAlpha(stateInk, 0x22));
            this.drawPaperText(g, stateBadge, badgeX + 3, rowTop + 5, stateInk);

            int actionAreaStart = this.canEdit ? this.rowsRight - 90 : this.rowsRight - 40;
            int nameX = badgeX + badgeWidth + 4;
            this.drawPaperText(g, this.trimToWidth(entry.getDisplayName(), actionAreaStart - nameX - 4), nameX, rowTop + 5, isDimmed ? DIM : TEXT);

            // Detail line: operation + amounts + reset + on-train.
            String operation = entry.getOperation() == ManifestOperation.UNLOAD ? "Unload" : "Load";
            String amounts = FreightManifestLogic.formatAmount(entry.getCargoKind(), entry.getDeliveredAmount())
                + " / " + FreightManifestLogic.formatAmount(entry.getCargoKind(), entry.getTargetAmount());
            String resetBadge = "[" + entry.getResetBadge() + "]";
            String secondLine = operation + " " + amounts + " " + resetBadge;
            if (line.getFreightAmount() > 0) {
                secondLine += " | On train: " + FreightManifestLogic.formatAmount(entry.getCargoKind(), line.getFreightAmount());
            }
            int opColor = entry.getOperation() == ManifestOperation.UNLOAD ? GREEN : RED;
            this.drawPaperText(g, this.trimToWidth(secondLine, actionAreaStart - (this.rowsLeft + 28) - 4),
                this.rowsLeft + 28, rowTop + 16, isDimmed ? DIM : opColor);

            // Animated progress strip along the bottom of the row.
            float ap = this.rowProgressAnim.getOrDefault(id, entry.getProgress());
            int barLeft = this.rowsLeft + 8;
            int barRight = this.canEdit ? actionAreaStart - 6 : this.rowsRight - 34;
            if (entry.getTargetAmount() > 0 && barRight > barLeft + 6) {
                int barColor = switch (rowState) {
                    case COMPLETE -> GREEN;
                    case IN_PROGRESS -> ACCENT;
                    case TRACKING -> 0xFF55CCFF;
                    case INVALID, NEEDS_SETUP -> RED;
                    default -> DIM;
                };
                DispatchUiFx.liveProgressBar(g, barLeft, rowTop + 24, barRight - barLeft, 3,
                    ap, barColor, BANNER_TRACK, this.withAlpha(BORDER, 0x88), activeRow, this.frameTime);
            }

            // Right side: percentage (read-only) or operator buttons (editable).
            if (this.canEdit) {
                int actionWidth = line.isComplete() ? 40 : 38;
                int dismissWidth = 36;
                int actionX = this.rowsRight - 10 - dismissWidth - 4 - actionWidth;
                int dismissX = this.rowsRight - 10 - dismissWidth;
                String actionLabel = line.isComplete() ? "Reset" : "Done";
                this.renderMiniButton(g, actionX, rowTop + 5, actionWidth, actionLabel, true, mouseX, mouseY);
                boolean dismissActive = line.isComplete() || line.isDismissed();
                this.renderMiniButton(g, dismissX, rowTop + 5, dismissWidth, line.isDismissed() ? "Show" : "Hide", dismissActive, mouseX, mouseY);
            } else if (entry.getTargetAmount() > 0) {
                String pct = Math.round(ap * 100f) + "%";
                int pctColor = rowState == DispatchRowState.COMPLETE ? GREEN : (activeRow ? ACCENT : DIM);
                this.drawPaperText(g, pct, this.rowsRight - 8 - this.font.width(pct), rowTop + 9, pctColor);
            }
        }
        g.disableScissor();

        // Scrollbar.
        if (this.dispatchRows.size() > this.visibleDispatchRows) {
            int sbX = this.rowsRight - 4;
            int trackTop = this.rowsTop + 2;
            int trackBottom = this.rowsBottom - 2;
            int trackH = trackBottom - trackTop;
            g.fill(sbX, trackTop, sbX + 3, trackBottom, 0x33000000);
            float frac = (float) this.visibleDispatchRows / this.dispatchRows.size();
            int thumbH = Math.max(14, (int) (trackH * frac));
            int maxScrollPx = Math.max(1, (this.dispatchRows.size() - this.visibleDispatchRows) * DISPATCH_ROW_HEIGHT);
            float scrollFrac = DispatchUiFx.clamp01(this.animScrollPx / maxScrollPx);
            int thumbTop = trackTop + (int) ((trackH - thumbH) * scrollFrac);
            g.fill(sbX, thumbTop, sbX + 3, thumbTop + thumbH, ACCENT);
            g.fill(sbX, thumbTop, sbX + 3, thumbTop + 1, DispatchUiFx.brighten(ACCENT, 0.4f));
        }
    }

    private void renderCargoSummary(GuiGraphics g, int mouseX, int mouseY) {
        this.drawPaperText(g, "Cargo Summary", this.cargoLeft + 4, this.cargoTop - 10, TEXT);
        this.renderPaperSection(g, this.cargoLeft, this.cargoTop, this.cargoRight, this.cargoBottom, SLOT_BG);

        if (this.cargoEntries.isEmpty()) {
            this.drawPaperText(g, "No cargo data.", this.cargoLeft + 8, this.cargoTop + 8, DIM);
            return;
        }

        for (int i = 0; i < VISIBLE_CARGO_ROWS; i++) {
            int idx = this.cargoScroll + i;
            int rowTop = this.cargoTop + i * CARGO_ROW_HEIGHT;
            int rowBottom = rowTop + CARGO_ROW_HEIGHT - 1;
            boolean hovered = mouseX >= this.cargoLeft && mouseX < this.cargoRight && mouseY >= rowTop && mouseY < rowBottom;
            g.fill(this.cargoLeft + 1, rowTop + 1, this.cargoRight - 1, rowBottom, hovered ? SLOT_HOVER : 0x22000000);

            if (idx >= this.cargoEntries.size()) continue;

            CargoEntry entry = this.cargoEntries.get(idx);

            // Goal marker
            int markerColor = entry.hasManifestGoal ? (entry.isUnloadGoal ? GREEN : RED) : DIM;
            g.fill(this.cargoLeft + 2, rowTop + 3, this.cargoLeft + 5, rowBottom - 2, markerColor);

            if (!entry.icon.isEmpty()) {
                g.renderItem(entry.icon, this.cargoLeft + 6, rowTop + 1);
            }
            this.drawPaperText(g, this.trimToWidth(entry.name, this.cargoRight - this.cargoLeft - 90),
                this.cargoLeft + 24, rowTop + 5, entry.hasManifestGoal ? TEXT : DIM);

            String amountText = entry.amountText(entry.freightAmount);
            this.drawPaperText(g, amountText, this.cargoRight - this.font.width(amountText) - 6, rowTop + 5, DIM);
        }

        this.renderScrollIndicators(g, this.cargoRight - 10, this.cargoTop + 4, this.cargoBottom - 12,
            this.cargoScroll > 0, this.cargoScroll + VISIBLE_CARGO_ROWS < this.cargoEntries.size());
    }

    private void renderHistoryStrip(GuiGraphics g, int mouseX, int mouseY) {
        this.drawPaperText(g, "Recent Events", this.histLeft + 4, this.histTop - 10, TEXT);
        this.renderPaperSection(g, this.histLeft, this.histTop, this.histRight, this.histBottom, SLOT_BG);

        List<DeliveryOrderHistoryEntry> historyEntries = this.deliveryBoard.getHistoryEntries();
        if (historyEntries.isEmpty()) {
            this.drawPaperText(g, "No events yet.", this.histLeft + 8, this.histTop + 8, DIM);
            return;
        }

        for (int i = 0; i < VISIBLE_HISTORY_ROWS; i++) {
            int idx = this.historyScroll + i;
            int rowTop = this.histTop + i * HISTORY_ROW_HEIGHT;
            int rowBottom = rowTop + HISTORY_ROW_HEIGHT - 1;
            boolean hovered = mouseX >= this.histLeft && mouseX < this.histRight && mouseY >= rowTop && mouseY < rowBottom;
            g.fill(this.histLeft + 1, rowTop + 1, this.histRight - 1, rowBottom, hovered ? SLOT_HOVER : 0x22000000);

            if (idx >= historyEntries.size()) continue;

            DeliveryOrderHistoryEntry entry = historyEntries.get(idx);
            int statusColor = this.getHistoryColor(entry.getType());
            g.fill(this.histLeft + 3, rowTop + 4, this.histLeft + 6, rowBottom - 3, statusColor);
            String age = FreightManifestLogic.formatTicks(Math.max(0L, this.deliveryBoard.getCurrentTick() - entry.getTick()));
            this.drawPaperText(g, age, this.histRight - this.font.width(age) - 6, rowTop + 5, DIM);
            String detail = entry.getTitle() + ": " + entry.getDetail();
            int maxTextWidth = Math.max(40, this.histRight - this.histLeft - this.font.width(age) - 18);
            this.drawPaperText(g, this.trimToWidth(detail, maxTextWidth), this.histLeft + 10, rowTop + 5, this.paperInk(statusColor));
        }

        this.renderScrollIndicators(g, this.histRight - 10, this.histTop + 4, this.histBottom - 12,
            this.historyScroll > 0, this.historyScroll + VISIBLE_HISTORY_ROWS < historyEntries.size());
    }

    private void renderTooltips(GuiGraphics g, int mouseX, int mouseY) {
        // Dispatch row tooltips: operator buttons when hovered, else full order detail.
        if (mouseX >= this.rowsLeft && mouseX < this.rowsRight
                && mouseY >= this.rowsTop && mouseY < this.rowsBottom) {
            int rowIdx = this.rowIndexAt(mouseY);
            if (rowIdx >= 0 && rowIdx < this.dispatchRows.size()) {
                DispatchRowEntry entry = this.dispatchRows.get(rowIdx);
                DeliveryOrderLineView line = entry.getLineView();
                int rowTop = this.rowTopFor(rowIdx);
                boolean shownButtonTip = false;
                if (this.canEdit) {
                    int actionWidth = line.isComplete() ? 40 : 38;
                    int dismissWidth = 36;
                    int actionX = this.rowsRight - 10 - dismissWidth - 4 - actionWidth;
                    int dismissX = this.rowsRight - 10 - dismissWidth;

                    if (mouseX >= actionX && mouseX < actionX + actionWidth && mouseY >= rowTop + 5 && mouseY < rowTop + 21) {
                        g.renderTooltip(this.font, List.of(
                            Component.literal(line.isComplete() ? "Reset this order cycle" : "Mark complete for this cycle"),
                            Component.literal(line.isComplete() ? "Clears progress and reopens the line." : "Use when this order was satisfied outside live tracking.").withStyle(ChatFormatting.GRAY)
                        ), java.util.Optional.empty(), mouseX, mouseY);
                        shownButtonTip = true;
                    }

                    boolean dismissActive = line.isComplete() || line.isDismissed();
                    if (!shownButtonTip && dismissActive && mouseX >= dismissX && mouseX < dismissX + dismissWidth && mouseY >= rowTop + 5 && mouseY < rowTop + 21) {
                        g.renderTooltip(this.font, List.of(
                            Component.literal(line.isDismissed() ? "Show hidden order again" : "Hide this completed order"),
                            Component.literal(line.isDismissed() ? "Bring the line back into the visible list." : "Keeps the board tidy for this cycle only.").withStyle(ChatFormatting.GRAY)
                        ), java.util.Optional.empty(), mouseX, mouseY);
                        shownButtonTip = true;
                    }
                }
                if (!shownButtonTip) {
                    g.renderTooltip(this.font, this.buildRowTooltip(entry), java.util.Optional.empty(), mouseX, mouseY);
                }
            }
        }

        // Cargo hover tooltip
        if (mouseX >= this.cargoLeft && mouseX < this.cargoRight && mouseY >= this.cargoTop && mouseY < this.cargoBottom) {
            int idx = this.cargoScroll + (mouseY - this.cargoTop) / CARGO_ROW_HEIGHT;
            if (idx >= 0 && idx < this.cargoEntries.size()) {
                CargoEntry entry = this.cargoEntries.get(idx);
                List<Component> tooltip = new ArrayList<>();
                tooltip.add(Component.literal(entry.name));
                tooltip.add(Component.literal("In freight: " + entry.amountText(entry.freightAmount)).withStyle(ChatFormatting.GRAY));
                if (entry.toStationAmount > 0) {
                    tooltip.add(Component.literal("Unloaded: " + entry.amountText(entry.toStationAmount)).withStyle(ChatFormatting.GREEN));
                }
                if (entry.toTrainAmount > 0) {
                    tooltip.add(Component.literal("Loaded: " + entry.amountText(entry.toTrainAmount)).withStyle(ChatFormatting.RED));
                }
                g.renderTooltip(this.font, tooltip, java.util.Optional.empty(), mouseX, mouseY);
            }
        }
    }

    private List<Component> buildRowTooltip(DispatchRowEntry entry) {
        DeliveryOrderLineView line = entry.getLineView();
        List<Component> t = new ArrayList<>();
        t.add(Component.literal(entry.getDisplayName()));
        t.add(Component.literal(entry.getState().getLabel()).withColor(entry.getStateColor())
            .append(Component.literal("  ").append(Component.literal(
                entry.getOperation() == ManifestOperation.UNLOAD ? "Unload" : "Load").withStyle(ChatFormatting.GRAY))));
        t.add(Component.literal("Progress: "
            + FreightManifestLogic.formatAmount(entry.getCargoKind(), entry.getDeliveredAmount()) + " / "
            + FreightManifestLogic.formatAmount(entry.getCargoKind(), entry.getTargetAmount())
            + "  (" + Math.round(entry.getProgress() * 100f) + "%)").withStyle(ChatFormatting.GRAY));
        if (line.getFreightAmount() > 0) {
            t.add(Component.literal("On train: "
                + FreightManifestLogic.formatAmount(entry.getCargoKind(), line.getFreightAmount())).withStyle(ChatFormatting.AQUA));
        }
        t.add(Component.literal("Reset: " + entry.getResetBadge()).withStyle(ChatFormatting.DARK_GRAY));
        if (line.getTicksUntilReset() > 0L) {
            t.add(Component.literal("Next reset in " + FreightManifestLogic.formatTicks(line.getTicksUntilReset())).withStyle(ChatFormatting.DARK_GRAY));
        }
        return t;
    }

    // shared rendering helpers
    private void renderPaperFrame(GuiGraphics g) {
        g.fill(this.panelLeft, this.panelTop, this.panelLeft + this.panelWidth, this.panelTop + this.panelHeight, PANEL_BG);
        g.fill(this.panelLeft + 2, this.panelTop + 2, this.panelLeft + this.panelWidth - 2, this.panelTop + this.panelHeight - 2, PANEL_INNER);
        g.fill(this.panelLeft, this.panelTop, this.panelLeft + this.panelWidth, this.panelTop + 2, BORDER);
        g.fill(this.panelLeft, this.panelTop + this.panelHeight - 2, this.panelLeft + this.panelWidth, this.panelTop + this.panelHeight, BORDER);
        g.fill(this.panelLeft, this.panelTop, this.panelLeft + 2, this.panelTop + this.panelHeight, BORDER);
        g.fill(this.panelLeft + this.panelWidth - 2, this.panelTop, this.panelLeft + this.panelWidth, this.panelTop + this.panelHeight, BORDER);
        int titlePlateLeft = this.width / 2 - 56;
        g.fill(titlePlateLeft, this.panelTop - 5, titlePlateLeft + 112, this.panelTop + 9, BRASS);
        g.fill(titlePlateLeft + 8, this.panelTop - 2, titlePlateLeft + 104, this.panelTop + 5, 0xFFE0BE84);
        g.fill(titlePlateLeft + 12, this.panelTop, titlePlateLeft + 16, this.panelTop + 4, BORDER);
        g.fill(titlePlateLeft + 96, this.panelTop, titlePlateLeft + 100, this.panelTop + 4, BORDER);
    }

    private void renderPaperSection(GuiGraphics g, int left, int top, int right, int bottom, int fillColor) {
        g.fill(left, top, right, bottom, fillColor);
        g.fill(left, top, right, top + 1, BORDER);
        g.fill(left, bottom - 1, right, bottom, BORDER);
        g.fill(left, top, left + 1, bottom, BORDER);
        g.fill(right - 1, top, right, bottom, BORDER);
        for (int y = top + 13; y < bottom - 2; y += 12) {
            g.fill(left + 1, y, right - 1, y + 1, PAPER_RULE);
        }
    }

    private void drawPaperText(GuiGraphics g, String text, int x, int y, int color) {
        if (this.shouldOutlinePaperText(color)) {
            int outline = 0xFF24160A;
            g.drawString(this.font, text, x - 1, y, outline, false);
            g.drawString(this.font, text, x + 1, y, outline, false);
            g.drawString(this.font, text, x, y - 1, outline, false);
            g.drawString(this.font, text, x, y + 1, outline, false);
        }
        g.drawString(this.font, text, x, y, color, false);
    }

    private boolean shouldOutlinePaperText(int color) {
        int rgb = color & 0x00FFFFFF;
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        int luminance = (299 * r + 587 * g + 114 * b) / 1000;
        return luminance >= 150;
    }

    private int paperInk(int color) {
        int rgb = color & 0x00FFFFFF;
        int r = (rgb >> 16) & 0xFF;
        int gr = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        int max = Math.max(r, Math.max(gr, b));
        if (max <= 0) {
            return 0xFFECDDBA;
        }
        float scale = 232.0f / max;
        r = Math.min(255, Math.round(r * scale));
        gr = Math.min(255, Math.round(gr * scale));
        b = Math.min(255, Math.round(b * scale));
        r = Math.min(255, Math.round(r * 0.72f + 255 * 0.28f));
        gr = Math.min(255, Math.round(gr * 0.72f + 255 * 0.28f));
        b = Math.min(255, Math.round(b * 0.72f + 255 * 0.28f));
        int floor = 104;
        r = Math.max(floor, r);
        gr = Math.max(floor, gr);
        b = Math.max(floor, b);
        return 0xFF000000 | (r << 16) | (gr << 8) | b;
    }

    private int withAlpha(int color, int alpha) {
        return (alpha << 24) | (color & 0x00FFFFFF);
    }

    private void renderMiniButton(GuiGraphics g, int x, int y, int width, String label, boolean active, int mouseX, int mouseY) {
        int height = 16;
        boolean hovered = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        int fill = !active ? 0xFFD9CCAD : (hovered ? 0xFFF0D39B : 0xFFE8D7AE);
        int border = active ? ACCENT : DIM;
        g.fill(x, y, x + width, y + height, fill);
        g.fill(x, y, x + width, y + 1, border);
        g.fill(x, y + height - 1, x + width, y + height, border);
        g.fill(x, y, x + 1, y + height, border);
        g.fill(x + width - 1, y, x + width, y + height, border);
        int textWidth = this.font.width(label);
        this.drawPaperText(g, label, x + (width - textWidth) / 2, y + 4, active ? TEXT : DIM);
    }

    private void renderScrollIndicators(GuiGraphics g, int x, int topY, int bottomY, boolean showUp, boolean showDown) {
        if (showUp) {
            this.drawPaperText(g, "\u25b2", x, topY, ACCENT);
        }
        if (showDown) {
            this.drawPaperText(g, "\u25bc", x, bottomY, ACCENT);
        }
    }

    // click handling

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.handleDispatchRowClick(mouseX, mouseY)) return true;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean handleDispatchRowClick(double mouseX, double mouseY) {
        if (!this.canEdit) return false;
        if (mouseX < this.rowsLeft || mouseX >= this.rowsRight || mouseY < this.rowsTop || mouseY >= this.rowsBottom) return false;

        int idx = this.rowIndexAt(mouseY);
        if (idx < 0 || idx >= this.dispatchRows.size()) return true;

        DeliveryOrderLineView line = this.dispatchRows.get(idx).getLineView();
        int rowTop = this.rowTopFor(idx);
        int actionWidth = line.isComplete() ? 40 : 38;
        int dismissWidth = 36;
        int actionX = this.rowsRight - 10 - dismissWidth - 4 - actionWidth;
        int dismissX = this.rowsRight - 10 - dismissWidth;

        if (mouseX >= actionX && mouseX < actionX + actionWidth && mouseY >= rowTop + 5 && mouseY < rowTop + 21) {
            DeliveryOrderAction action = line.isComplete() ? DeliveryOrderAction.RESET_CYCLE : DeliveryOrderAction.MARK_COMPLETE;
            this.playUiClick(line.isComplete() ? 0.9f : 1.1f);
            PacketDistributor.sendToServer(new FreightManifestDeliveryOrderActionPacket(this.blockPos, line.getLineId(), action));
            return true;
        }

        if ((line.isComplete() || line.isDismissed())
            && mouseX >= dismissX && mouseX < dismissX + dismissWidth && mouseY >= rowTop + 5 && mouseY < rowTop + 21) {
            this.playUiClick(line.isDismissed() ? 1.0f : 0.95f);
            PacketDistributor.sendToServer(new FreightManifestDeliveryOrderActionPacket(this.blockPos, line.getLineId(), DeliveryOrderAction.TOGGLE_DISMISSED));
            return true;
        }

        return true;
    }

    // scroll handling

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        // Dispatch rows
        if (mouseX >= this.rowsLeft && mouseX < this.rowsRight && mouseY >= this.rowsTop && mouseY < this.rowsBottom) {
            int maxScroll = Math.max(0, this.dispatchRows.size() - this.visibleDispatchRows);
            if (maxScroll > 0) {
                this.dispatchScroll = Math.max(0, Math.min(maxScroll, this.dispatchScroll - (int) Math.signum(scrollY)));
                return true;
            }
        }
        // Cargo summary
        if (mouseX >= this.cargoLeft && mouseX < this.cargoRight && mouseY >= this.cargoTop && mouseY < this.cargoBottom) {
            int maxScroll = Math.max(0, this.cargoEntries.size() - VISIBLE_CARGO_ROWS);
            if (maxScroll > 0) {
                this.cargoScroll = Math.max(0, Math.min(maxScroll, this.cargoScroll - (int) Math.signum(scrollY)));
                return true;
            }
        }
        // History
        if (mouseX >= this.histLeft && mouseX < this.histRight && mouseY >= this.histTop && mouseY < this.histBottom) {
            int maxScroll = Math.max(0, this.deliveryBoard.getHistoryEntries().size() - VISIBLE_HISTORY_ROWS);
            if (maxScroll > 0) {
                this.historyScroll = Math.max(0, Math.min(maxScroll, this.historyScroll - (int) Math.signum(scrollY)));
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    // data helpers

    public boolean isForBoard(BlockPos pos) {
        return this.blockPos.equals(pos);
    }

    public void updateLiveState(FreightManifest manifest, FreightStationCargoView cargoView, DeliveryOrderBoardView deliveryBoard) {
        this.manifest = manifest.copy();
        this.cargoView = cargoView.copy();
        this.deliveryBoard = deliveryBoard;
        this.rebuildDerivedState();
        this.clampScrollPositions();
        this.rebuildWidgets();
    }

    private void rebuildDerivedState() {
        this.headerState = BoardHeaderState.derive(this.stationLink, this.deliveryBoard);

        // Build dispatch rows with pre-derived state
        boolean trainPresent = this.deliveryBoard.isTrainPresent();
        this.dispatchRows = new ArrayList<>();
        for (int i = 0; i < this.deliveryBoard.getLineViews().size(); i++) {
            DeliveryOrderLineView line = this.deliveryBoard.getLineViews().get(i);
            DispatchRowState rowState = DispatchRowState.derive(line, trainPresent);
            this.dispatchRows.add(new DispatchRowEntry(line, rowState, i));
        }

        // Build cargo entries
        this.cargoEntries = this.buildCargoEntries();

        // Prune animation maps to current line ids to avoid unbounded growth.
        if (!this.rowProgressAnim.isEmpty() || !this.rowHoverAnim.isEmpty()) {
            java.util.Set<UUID> live = new java.util.HashSet<>();
            for (DispatchRowEntry e : this.dispatchRows) {
                live.add(e.getLineView().getLineId());
            }
            this.rowProgressAnim.keySet().retainAll(live);
            this.rowHoverAnim.keySet().retainAll(live);
        }
    }

    private void clampScrollPositions() {
        int maxDispatch = Math.max(0, this.dispatchRows.size() - this.visibleDispatchRows);
        this.dispatchScroll = Math.max(0, Math.min(maxDispatch, this.dispatchScroll));
        int maxCargo = Math.max(0, this.cargoEntries.size() - VISIBLE_CARGO_ROWS);
        this.cargoScroll = Math.max(0, Math.min(maxCargo, this.cargoScroll));
        int maxHistory = Math.max(0, this.deliveryBoard.getHistoryEntries().size() - VISIBLE_HISTORY_ROWS);
        this.historyScroll = Math.max(0, Math.min(maxHistory, this.historyScroll));
    }

    private boolean hasCompletedOrders() {
        for (DispatchRowEntry row : this.dispatchRows) {
            if (row.getState() == DispatchRowState.COMPLETE || row.getState() == DispatchRowState.COMPLETE_HIDDEN) return true;
        }
        return false;
    }

    private boolean hasCompletedVisibleOrders() {
        for (DispatchRowEntry row : this.dispatchRows) {
            if (row.getState() == DispatchRowState.COMPLETE) return true;
        }
        return false;
    }

    private boolean hasHiddenOrders() {
        for (DispatchRowEntry row : this.dispatchRows) {
            if (row.getState() == DispatchRowState.COMPLETE_HIDDEN) return true;
        }
        return false;
    }

    private int getHistoryColor(DeliveryOrderHistoryType type) {
        return switch (type) {
            case COMPLETED, MANUAL_COMPLETE, SHOWN -> GREEN;
            case HIDDEN -> DIM;
            case AUTO_RESET, MANUAL_RESET -> ACCENT;
        };
    }

    private List<CargoEntry> buildCargoEntries() {
        List<CargoEntry> entries = new ArrayList<>();
        Map<FreightManifestResourceKey, Integer> expectedToStation =
            FreightManifestLogic.getExpectedAmounts(this.manifest, ManifestOperation.UNLOAD);
        Map<FreightManifestResourceKey, Integer> expectedToTrain =
            FreightManifestLogic.getExpectedAmounts(this.manifest, ManifestOperation.LOAD);

        // Collect all known keys
        List<FreightManifestResourceKey> keys = new ArrayList<>();
        keys.addAll(this.cargoView.getFreightTotals().getAmounts().keySet());
        for (FreightManifestResourceKey key : this.cargoView.getMovedToStation().getAmounts().keySet()) {
            if (!keys.contains(key)) keys.add(key);
        }
        for (FreightManifestResourceKey key : this.cargoView.getMovedToTrain().getAmounts().keySet()) {
            if (!keys.contains(key)) keys.add(key);
        }
        for (FreightManifestResourceKey key : expectedToStation.keySet()) {
            if (!keys.contains(key)) keys.add(key);
        }
        for (FreightManifestResourceKey key : expectedToTrain.keySet()) {
            if (!keys.contains(key)) keys.add(key);
        }

        for (FreightManifestResourceKey key : keys) {
            entries.add(CargoEntry.fromData(key,
                this.cargoView.getFreightTotals().getAmount(key),
                this.cargoView.getMovedToStation().getAmount(key),
                this.cargoView.getMovedToTrain().getAmount(key),
                expectedToStation.getOrDefault(key, 0),
                expectedToTrain.getOrDefault(key, 0)));
        }
        entries.sort(Comparator.comparing((CargoEntry e) -> !e.hasManifestGoal)
            .thenComparing(e -> e.kind.ordinal())
            .thenComparing(e -> e.name));
        return entries;
    }

    private void playUiClick(float pitch) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, pitch));
    }

    private String trimToWidth(String text, int width) {
        if (this.font.width(text) <= width) return text;
        String ellipsis = "...";
        int maxWidth = Math.max(0, width - this.font.width(ellipsis));
        return this.font.plainSubstrByWidth(text, maxWidth) + ellipsis;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // cargo data model

    private static class CargoEntry {
        final ManifestCargoKind kind;
        final String name;
        final int freightAmount;
        final int toStationAmount;
        final int toTrainAmount;
        final boolean hasManifestGoal;
        final boolean isUnloadGoal;
        final ItemStack icon;

        CargoEntry(ManifestCargoKind kind, String name, int freightAmount, int toStationAmount, int toTrainAmount,
                   int expectedToStation, int expectedToTrain, ItemStack icon) {
            this.kind = kind;
            this.name = name;
            this.freightAmount = freightAmount;
            this.toStationAmount = toStationAmount;
            this.toTrainAmount = toTrainAmount;
            this.hasManifestGoal = expectedToStation > 0 || expectedToTrain > 0;
            this.isUnloadGoal = expectedToStation > 0;
            this.icon = icon;
        }

        String amountText(int amount) {
            return FreightManifestLogic.formatAmount(this.kind, amount);
        }

        static CargoEntry fromData(FreightManifestResourceKey key,
                                   int freightAmount, int toStationAmount, int toTrainAmount,
                                   int expectedToStation, int expectedToTrain) {
            if (key.getCargoKind() == ManifestCargoKind.ITEM) {
                var item = BuiltInRegistries.ITEM.getOptional(key.getResourceId()).orElse(null);
                ItemStack icon = item == null ? ItemStack.EMPTY : new ItemStack(item);
                String name = icon.isEmpty() ? key.getResourceId().toString() : icon.getHoverName().getString();
                return new CargoEntry(ManifestCargoKind.ITEM, name, freightAmount, toStationAmount, toTrainAmount,
                    expectedToStation, expectedToTrain, icon);
            }
            Fluid fluid = BuiltInRegistries.FLUID.getOptional(key.getResourceId()).orElse(null);
            ItemStack icon = ItemStack.EMPTY;
            String name = key.getResourceId().toString();
            if (fluid != null) {
                FluidStack stack = new FluidStack(fluid, 1000);
                icon = FluidUtil.getFilledBucket(stack);
                name = stack.getDisplayName().getString();
            }
            return new CargoEntry(ManifestCargoKind.FLUID, name, freightAmount, toStationAmount, toTrainAmount,
                expectedToStation, expectedToTrain, icon);
        }
    }
}
