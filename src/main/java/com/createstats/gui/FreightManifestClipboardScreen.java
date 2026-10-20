package com.createstats.gui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import com.createstats.network.FreightManifestDeliveryOrderActionPacket;
import com.createstats.network.RequestFreightManifestClipboardStatePacket;
import com.createstats.network.SaveFreightManifestPacket;
import net.neoforged.neoforge.network.PacketDistributor;
import com.createstats.train.manifest.DeliveryOrderAction;
import com.createstats.train.manifest.DeliveryOrderBoardView;
import com.createstats.train.manifest.DeliveryOrderHistoryEntry;
import com.createstats.train.manifest.DeliveryOrderHistoryType;
import com.createstats.train.manifest.DeliveryOrderLineView;
import com.createstats.train.manifest.DeliveryOrderResetMode;
import com.createstats.train.manifest.FreightManifest;
import com.createstats.train.manifest.FreightManifestEvaluationResult;
import com.createstats.train.manifest.FreightManifestLine;
import com.createstats.train.manifest.FreightManifestLogic;
import com.createstats.train.manifest.FreightManifestResourceKey;
import com.createstats.train.manifest.FreightManifestValidationResult;
import com.createstats.train.manifest.FreightStationCargoView;
import com.createstats.train.manifest.FreightStationLink;
import com.createstats.train.manifest.ManifestCargoKind;
import com.createstats.train.manifest.ManifestOperation;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import org.lwjgl.glfw.GLFW;

// three tabs, three questions
public class FreightManifestClipboardScreen extends Screen {
    private static Tab LAST_OPENED_TAB = Tab.MANIFEST;

    private enum Tab {
        MANIFEST("Manifest"),
        ORDERS("Orders"),
        CARGO("Cargo");

        private final String label;

        Tab(String label) {
            this.label = label;
        }

        String label() {
            return this.label;
        }
    }

    private static final int ROW_LINE = 30;
    private static final int ROW_ORDER = 32;
    private static final int ROW_CARGO = 20;
    private static final int ROW_HISTORY = 22;
    private static final int ROW_HISTORY_BIG = 34;

    private static final int DRAWER_SHUT = 40;
    private static final int DRAWER_OPEN = 100;
    private static final int SLOT = 18;

    private final BlockPos blockPos;
    private final FreightStationLink stationLink;
    private final FreightManifest manifest;
    private final boolean canEdit;
    private final List<FreightManifestLine> lines;
    private final List<CargoRow> cargoRows;
    private FreightStationCargoView cargoView;
    private DeliveryOrderBoardView deliveryBoard;

    private int selectedIndex;
    private int lineScroll;
    private int orderScroll;
    private int cargoScroll;
    private int historyScroll;
    private boolean historyExpanded;
    private boolean pickerOpen;
    private final com.createstats.train.manifest.ManifestDraftState draftState;
    private boolean confirmClose;
    private EditBox amountInput;
    private EditBox intervalInput;
    private Tab currentTab;
    private int cargoRefreshCooldown;

    private float frameTime;
    private float frameDt;
    private float lastFrameTime = Float.NaN;
    private float refreshFlash;
    private float saveFlash;
    private float tabFade = 1f;
    private float drawerAnim;
    private float selectionY = Float.NaN;
    private int dataSignature = Integer.MIN_VALUE;
    private final Map<UUID, Float> orderProgressAnim = new HashMap<>();
    private final Map<UUID, Float> orderCompleteFlash = new HashMap<>();
    private final Map<String, UiFx.Rolling> counters = new HashMap<>();

    private final Map<UUID, UiFx.RateTracker> rates = new HashMap<>();

    private ResourceLocation linkedHighlight;

    private int pL;
    private int pT;
    private int pW;
    private int pH;
    private int pageL;
    private int pageT;
    private int pageR;
    private int pageB;
    private int gutterX;
    private int contentL;
    private int contentR;
    private int headerT;
    private int headerB;
    private int tabsT;
    private int tabsB;
    private int bodyT;
    private int bodyB;
    private int footerT;
    private int listL;
    private int listR;
    private int listTop;
    private int listBottom;
    private int listRows;
    private int editorL;
    private int editorR;
    private int editorTop;
    private int editorBottom;
    private int drawerTop;
    private int edX;
    private int edW;
    private int edHeadY;
    private int edDirY;
    private int edAmountY;
    private int edResetY;
    private int edIntervalY;
    private int edActionsY;

    private boolean editorCompact;
    private int summaryTop;
    private int summaryBottom;
    private int rowsTop;
    private int rowsBottom;
    private int cargoSplitX;
    private int historyL;

    public FreightManifestClipboardScreen(BlockPos blockPos, FreightStationLink stationLink, FreightManifest manifest,
                                          FreightStationCargoView cargoView, DeliveryOrderBoardView deliveryBoard,
                                          boolean canEdit) {
        super(Component.literal("Freight Manifest"));
        this.blockPos = blockPos;
        this.stationLink = stationLink.copy();
        this.manifest = manifest.copy();
        this.draftState = new com.createstats.train.manifest.ManifestDraftState(manifest.getVersion());
        this.canEdit = canEdit;
        this.lines = new ArrayList<>();
        for (FreightManifestLine line : manifest.getLines()) {
            this.lines.add(line.copy());
        }
        this.cargoRows = new ArrayList<>();
        this.cargoView = FreightStationCargoView.unavailable("Waiting for cargo data...");
        this.deliveryBoard = deliveryBoard == null
                ? DeliveryOrderBoardView.empty(0L, "Waiting for delivery data...") : deliveryBoard;
        this.selectedIndex = this.lines.isEmpty() ? -1 : 0;
        this.currentTab = LAST_OPENED_TAB;

        this.pickerOpen = this.selectedIndex >= 0 && !this.lines.get(this.selectedIndex).hasSelection();
        this.drawerAnim = this.pickerOpen ? 1f : 0f;
        this.updateLiveState(manifest, cargoView, this.deliveryBoard, false, false);
    }

    private void layout() {
        this.pW = Math.max(440, Math.min(this.width - 24, 668));
        this.pH = Math.max(280, Math.min(this.height - 24, 486));
        this.pL = (this.width - this.pW) / 2;
        this.pT = (this.height - this.pH) / 2;

        this.pageL = this.pL + 5;
        this.pageT = this.pT + 5;
        this.pageR = this.pL + this.pW - 5;
        this.pageB = this.pT + this.pH - 5;
        this.gutterX = this.pageL + 20;
        this.contentL = this.gutterX + 10;
        this.contentR = this.pageR - 10;

        this.headerT = this.pageT + 9;
        this.headerB = this.headerT + 44;
        this.tabsT = this.headerB + 6;
        this.tabsB = this.tabsT + 19;
        this.footerT = this.pageB - 26;
        this.bodyT = this.tabsB;
        this.bodyB = this.footerT - 6;

        int drawerH = Math.round(UiFx.lerp(DRAWER_SHUT, DRAWER_OPEN, UiFx.easeInOut(this.drawerAnim)));
        this.drawerTop = this.bodyB - drawerH;

        int bodyW = this.contentR - this.contentL;
        this.listL = this.contentL;
        this.listR = this.listL + Math.max(184, (int) ((bodyW - 10) * 0.44f));
        this.editorL = this.listR + 10;
        this.editorR = this.contentR;
        this.listTop = this.bodyT + 6;
        this.listBottom = this.drawerTop - 8;
        this.editorTop = this.listTop;
        this.editorBottom = this.listBottom;
        this.listRows = Math.max(1, (this.listBottom - (this.listTop + 42)) / ROW_LINE);

        this.layoutEditor(this.bodyB
                - (this.pickerOpen ? DRAWER_OPEN : DRAWER_SHUT) - 8);

        this.summaryTop = this.bodyT + 6;
        this.summaryBottom = this.summaryTop + (this.currentTab == Tab.ORDERS ? 50 : 46);
        this.rowsTop = this.summaryBottom + 8;
        this.rowsBottom = this.bodyB;
        this.cargoSplitX = this.contentL + (bodyW * 11 / 20);
        this.historyL = this.cargoSplitX + 8;
    }

    private void layoutEditor(int settledBottom) {
        this.edX = this.editorL + 10;
        this.edW = this.editorR - this.editorL - 20;

        int avail = settledBottom - this.editorTop;
        this.editorCompact = avail < 190;
        int gap = avail >= 236 ? 14 : (avail >= 200 ? 8 : 3);
        int y = this.editorTop + 9;
        this.edHeadY = y;
        y += 32 + gap / 2;
        this.edDirY = y + 10;
        y += 30 + gap;
        this.edAmountY = y + 10;
        y += 30 + gap;
        this.edResetY = y + 10;
        y += 30 + gap;
        this.edIntervalY = y;
        y += 20 + gap / 2;
        this.edActionsY = Math.min(y, settledBottom - 26);
    }

    @Override
    protected void init() {
        super.init();
        this.clearWidgets();
        this.layout();

        switch (this.currentTab) {
            case MANIFEST -> this.initManifestWidgets();
            case ORDERS -> this.initOrderWidgets();
            case CARGO -> {
            }
        }

        InkButton apply = InkButton.primary(this.contentR - 178, this.footerT + 3, 84, 19, "Publish", this::triggerSave);
        apply.active = this.canEdit && this.stationLink.isLinked() && this.getManifestValidation().isValid();
        apply.setTooltip(Tooltip.create(Component.literal(apply.active
                ? "Publish this manifest to the station  (Ctrl+S)"
                : (this.stationLink.isLinked() ? this.getManifestValidation().getMessage()
                        : "Link this clipboard to a station first"))));
        this.addRenderableWidget(apply);
        this.addRenderableWidget(InkButton.of(this.contentR - 88, this.footerT + 3, 84, 19,
                this.confirmClose ? "Discard?" : "Close", this::onClose));
    }

    private void initManifestWidgets() {
        int by = this.listTop + 21;
        InkButton add = InkButton.quiet(this.listL + 8, by, 48, 14, "+ Line", this::addLine);
        add.active = this.canEdit && this.lines.size() < FreightManifestLogic.MAX_LINES;
        add.setTooltip(Tooltip.create(Component.literal(add.active ? "Add a line  (Ctrl+N)"
                : "A manifest holds at most " + FreightManifestLogic.MAX_LINES + " lines")));
        this.addRenderableWidget(add);
        InkButton copy = InkButton.quiet(this.listL + 60, by, 39, 14, "Copy", this::duplicateSelectedLine);
        copy.active = this.canEdit && this.hasSelection() && this.lines.size() < FreightManifestLogic.MAX_LINES;
        copy.setTooltip(Tooltip.create(Component.literal(copy.active
                ? "Duplicate the selected line  (Ctrl+D)" : "Select a line to duplicate")));
        this.addRenderableWidget(copy);
        InkButton remove = InkButton.quiet(this.listL + 103, by, 52, 14, "Remove", this::removeSelectedLine);
        remove.active = this.canEdit && this.hasSelection();
        remove.setTooltip(Tooltip.create(Component.literal(remove.active
                ? "Remove the selected line  (Delete)" : "Select a line to remove")));
        this.addRenderableWidget(remove);

        if (this.editorCompact) {
            return;
        }

        FreightManifestLine selected = this.getSelectedLine();
        boolean interval = selected != null && selected.getResetMode().isIntervalBased();

        this.intervalInput = new EditBox(this.font, this.edX + 42, this.edIntervalY, 40, 16, Component.literal("Interval"));
        this.intervalInput.setBordered(false);
        this.intervalInput.setTextColor(UiFx.INK);
        this.intervalInput.setTextColorUneditable(UiFx.INK_FAINT);
        this.intervalInput.setFilter(text -> text.isEmpty() || text.matches("[0-9]+"));
        this.intervalInput.setMaxLength(4);
        this.intervalInput.setEditable(this.canEdit && interval);
        this.intervalInput.visible = interval;
        this.intervalInput.setResponder(text -> this.onIntervalInputChanged());
        this.syncIntervalInputFromSelection();
        this.addRenderableWidget(this.intervalInput);

        this.amountInput = new EditBox(this.font, this.edX + 24, this.edAmountY + 4, this.edW - 108, 16,
                Component.literal("Amount"));
        this.amountInput.setBordered(false);
        this.amountInput.setTextColor(UiFx.INK);
        this.amountInput.setTextColorUneditable(UiFx.INK_FAINT);
        this.amountInput.setFilter(text -> text.isEmpty() || text.matches("[0-9]+"));
        this.amountInput.setMaxLength(9);
        this.amountInput.setEditable(this.canEdit && this.hasSelection());
        this.amountInput.setResponder(text -> this.onAmountInputChanged());
        this.syncAmountInputFromSelection();
        this.addRenderableWidget(this.amountInput);

        InkButton chooseCargo = InkButton.of(this.edX, this.edActionsY, 104, 19, "Choose cargo", () -> {
            this.pickerOpen = true;
            this.playPaper(SoundEvents.BOOK_PAGE_TURN, 1.25f);
        });
        chooseCargo.active = this.canEdit && this.hasSelection();
        chooseCargo.setTooltip(Tooltip.create(Component.literal("Choose a stack from your inventory below")));
        this.addRenderableWidget(chooseCargo);
    }

    private void initOrderWidgets() {
        int by = this.summaryTop + 26;
        int right = this.contentR - 8;
        InkButton reset = InkButton.of(right - 84, by, 84, 18, "Reset done", () ->
                PacketDistributor.sendToServer(new FreightManifestDeliveryOrderActionPacket(
                        this.blockPos, null, DeliveryOrderAction.RESET_COMPLETED)));
        reset.active = this.hasCompletedOrders();
        this.addRenderableWidget(reset);

        InkButton show = InkButton.of(right - 174, by, 86, 18, "Show hidden", () ->
                PacketDistributor.sendToServer(new FreightManifestDeliveryOrderActionPacket(
                        this.blockPos, null, DeliveryOrderAction.SHOW_HIDDEN)));
        show.active = this.hasHiddenOrders();
        this.addRenderableWidget(show);

        InkButton hide = InkButton.of(right - 256, by, 78, 18, "Hide done", () ->
                PacketDistributor.sendToServer(new FreightManifestDeliveryOrderActionPacket(
                        this.blockPos, null, DeliveryOrderAction.HIDE_COMPLETED)));
        hide.active = this.hasCompletedVisibleOrders();
        this.addRenderableWidget(hide);
    }

    @Override
    protected void rebuildWidgets() {
        this.init();
    }

    @Override
    public void tick() {
        super.tick();
        if (this.stationLink.isLinked() && --this.cargoRefreshCooldown <= 0) {
            this.cargoRefreshCooldown = 5;
            PacketDistributor.sendToServer(new RequestFreightManifestClipboardStatePacket(this.blockPos));
        }
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
        this.layout();

        this.renderDesk(g);
        this.renderBoard(g);
        this.renderHeader(g, mouseX, mouseY);
        this.renderTabs(g, mouseX, mouseY);

        int bodyAlpha = Math.round(255 * UiFx.easeOut(this.tabFade));
        List<Component> tooltip = null;
        g.pose().pushPose();
        g.pose().translate(0f, (1f - UiFx.easeOut(this.tabFade)) * -4f, 0f);
        switch (this.currentTab) {
            case MANIFEST -> tooltip = this.renderManifestTab(g, mouseX, mouseY);
            case ORDERS -> tooltip = this.renderOrdersTab(g, mouseX, mouseY);
            case CARGO -> tooltip = this.renderCargoTab(g, mouseX, mouseY);
        }
        if (bodyAlpha < 250) {
            g.fill(this.contentL - 4, this.bodyT, this.contentR + 4, this.bodyB,
                    UiFx.withAlpha(UiFx.PAGE, 255 - bodyAlpha));
        }
        g.pose().popPose();

        this.renderFooter(g);
        super.render(g, mouseX, mouseY, partialTick);
        if (tooltip != null) {
            g.renderTooltip(this.font, tooltip, Optional.empty(), mouseX, mouseY);
        }
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
        this.saveFlash = Math.max(0f, this.saveFlash - dt * 0.045f);
        this.tabFade = UiFx.approach(this.tabFade, 1f, dt, 0.42f);
        this.drawerAnim = UiFx.approach(this.drawerAnim, this.pickerOpen ? 1f : 0f, dt, 0.40f);

        for (DeliveryOrderLineView row : this.deliveryBoard.getLineViews()) {
            float target = row.getTargetAmount() > 0
                    ? UiFx.clamp01(row.getDeliveredAmount() / (float) row.getTargetAmount())
                    : 0f;
            float cur = this.orderProgressAnim.getOrDefault(row.getLineId(), target);
            boolean wasShort = cur < 0.999f;
            float next = UiFx.approach(cur, target, dt, 0.22f);
            this.orderProgressAnim.put(row.getLineId(), next);

            if (wasShort && target >= 0.999f && next >= 0.985f) {
                this.orderCompleteFlash.put(row.getLineId(), 1.0f);
            }
        }
        if (!this.orderProgressAnim.isEmpty()) {
            Set<UUID> live = new java.util.HashSet<>();
            for (DeliveryOrderLineView row : this.deliveryBoard.getLineViews()) {
                live.add(row.getLineId());
            }
            this.orderProgressAnim.keySet().retainAll(live);
            this.orderCompleteFlash.keySet().retainAll(live);
        }
        this.orderCompleteFlash.replaceAll((id, v) -> Math.max(0f, v - dt * 0.05f));
    }

    private int computeDataSignature() {
        int sig = this.deliveryBoard.getTotalOrders();
        sig = sig * 31 + this.deliveryBoard.getCompletedOrders();
        sig = sig * 31 + (this.deliveryBoard.isTrainPresent() ? 1 : 0);
        long delivered = 0L;
        for (DeliveryOrderLineView row : this.deliveryBoard.getLineViews()) {
            delivered += row.getDeliveredAmount();
        }
        return sig * 31 + (int) (delivered & 0x7FFFFFFF);
    }

    private float roll(String key, float target, float speed) {
        return this.counters.computeIfAbsent(key, k -> new UiFx.Rolling()).update(target, this.frameDt, speed);
    }

    private void renderDesk(GuiGraphics g) {
        g.fillGradient(0, 0, this.width, this.height, 0xE60E1014, 0xF2171310);
        int spacing = 34;
        int drift = (int) (this.frameTime * 0.28f) % spacing;
        for (int x = -drift; x < this.width; x += spacing) {
            g.fill(x, 0, x + 1, this.height, 0x10C9A24A);
        }
        for (int y = drift - spacing; y < this.height; y += spacing) {
            g.fill(0, y, this.width, y + 1, 0x08C9A24A);
        }
        int vignette = 48;
        for (int i = 0; i < vignette; i++) {
            int a = (int) (0x40 * (1f - i / (float) vignette));
            int c = UiFx.withAlpha(0xFF000000, a / 6);
            g.fill(0, i, this.width, i + 1, c);
            g.fill(0, this.height - i - 1, this.width, this.height - i, c);
        }
    }

    private void renderBoard(GuiGraphics g) {
        UiFx.softShadow(g, this.pL, this.pT, this.pW, this.pH);
        g.fill(this.pL, this.pT, this.pL + this.pW, this.pT + this.pH, UiFx.IRON);
        UiFx.outline(g, this.pL, this.pT, this.pW, this.pH, UiFx.IRON_LO);
        UiFx.outline(g, this.pL + 1, this.pT + 1, this.pW - 2, this.pH - 2, UiFx.IRON_HI);
        UiFx.outline(g, this.pL + 3, this.pT + 3, this.pW - 6, this.pH - 6, UiFx.BRASS_LO);

        g.fill(this.pageL, this.pageT, this.pageR, this.pageB, UiFx.PAGE);

        g.fill(this.pageL, this.pageT, this.pageR, this.pageT + 1, UiFx.PAGE_HI);
        g.fill(this.pageL, this.pageT, this.pageL + 1, this.pageB, UiFx.PAGE_HI);
        g.fill(this.pageL, this.pageB - 1, this.pageR, this.pageB, UiFx.PAGE_LO);
        g.fill(this.pageR - 1, this.pageT, this.pageR, this.pageB, UiFx.PAGE_LO);
        g.fill(this.gutterX, this.pageT + 2, this.gutterX + 1, this.pageB - 2, UiFx.MARGIN_RED);

        int clipW = Math.min(132, this.pW / 3);
        int clipX = this.pL + (this.pW - clipW) / 2;
        g.fill(clipX, this.pT - 5, clipX + clipW, this.pT + 8, UiFx.BRASS_LO);
        g.fill(clipX + 1, this.pT - 4, clipX + clipW - 1, this.pT + 6, UiFx.BRASS);
        g.fill(clipX + 1, this.pT - 4, clipX + clipW - 1, this.pT - 2, UiFx.BRASS_HI);
        for (int i = clipX + 8; i < clipX + clipW - 8; i += 4) {
            g.fill(i, this.pT + 1, i + 2, this.pT + 3, UiFx.BRASS_LO);
        }
    }

    private void renderHeader(GuiGraphics g, int mouseX, int mouseY) {
        boolean linked = this.stationLink.isLinked();
        int pipBase = linked ? UiFx.OK : UiFx.BAD;
        int pip = UiFx.lerpColor(pipBase, 0xFFFFFFFF, this.refreshFlash * 0.55f);
        UiFx.pulseDot(g, this.pageL + 10, this.headerT + 5, 2, pip, this.frameTime, linked ? 0.30f : 0.10f);

        for (int y = this.headerT + 16; y < this.pageB - 8; y += 8) {
            g.fill(this.pageL + 9, y, this.pageL + 12, y + 1, 0x14000000);
        }

        String station = linked ? this.stationLink.getStationName() : "No station linked";
        int gaugeW = Math.min(184, (this.contentR - this.contentL) / 3);
        int gaugeX = this.contentR - gaugeW;
        this.text(g, this.trim(station, gaugeX - this.contentL - 12), this.contentL, this.headerT, UiFx.INK);

        String dim = this.stationLink.getStationDimension() == null
                ? "dimension unknown" : this.stationLink.getStationDimension().toString();
        int metaX = this.contentL;
        this.text(g, this.trim(dim, gaugeX - this.contentL - 80), metaX, this.headerT + 12, UiFx.INK_DIM);
        if (!this.canEdit) {
            UiFx.chip(g, this.font, gaugeX - 74, this.headerT + 10, "READ ONLY",
                    UiFx.PAGE_HI, UiFx.INK_DIM);
        }

        this.renderBerth(g, this.contentL, this.headerT + 26, gaugeX - 12 - this.contentL, 16);
        this.renderOrderGauge(g, gaugeX, this.headerT, gaugeW);
        UiFx.hairline(g, this.contentL, this.headerB - 2, this.contentR - this.contentL, UiFx.RULE);
    }

    private void renderBerth(GuiGraphics g, int x, int y, int w, int h) {
        if (w < 40) {
            return;
        }
        boolean present = this.deliveryBoard.isTrainPresent() || this.cargoView.isTrainPresent();
        UiFx.sunken(g, x, y, w, h, present ? UiFx.PAGE_SUNK : UiFx.PAGE_LO);

        int openLoad = 0;
        int openUnload = 0;
        for (DeliveryOrderLineView row : this.deliveryBoard.getLineViews()) {
            if (row.isComplete() || row.isDismissed()) {
                continue;
            }
            if (row.getOperation() == ManifestOperation.LOAD) {
                openLoad++;
            } else {
                openUnload++;
            }
        }
        int flow = openLoad == 0 && openUnload == 0 ? 0 : (openLoad >= openUnload ? 1 : -1);

        String label;
        int ink;
        if (present) {
            String name = this.deliveryBoard.isTrainPresent()
                    ? this.deliveryBoard.getTrainName() : this.cargoView.getTrainName();
            label = name + (this.cargoView.getCarriageCount() > 0
                    ? "  \u00b7  " + this.cargoView.getCarriageCount() + " carriages" : "");
            ink = UiFx.INK;
        } else {
            label = "Berth empty  \u00b7  waiting for a train";
            ink = UiFx.INK_FAINT;
        }
        label = this.trim(label, w - 76);
        String tail = flow == 0 ? (present ? "idle" : "") : (flow > 0 ? "loading" : "unloading");
        int tailW = tail.isEmpty() ? 0 : this.font.width(tail);

        if (present && flow != 0) {
            int hue = flow > 0 ? UiFx.TO_TRAIN : UiFx.TO_STATION;
            g.fill(x + 1, y + 1, x + w - 1, y + h - 1, UiFx.withAlpha(hue, 20));
            int zoneL = x + 8 + this.font.width(label) + 10;
            int zoneR = x + w - 8 - tailW - 10;
            if (zoneR - zoneL > 26) {
                UiFx.marchingChevrons(g, zoneL, y + 4, zoneR - zoneL, h - 8,
                        UiFx.withAlpha(hue, 130), this.frameTime, flow);
            }
        }

        this.text(g, label, x + 6, y + 4, ink);
        if (!tail.isEmpty()) {
            this.textRight(g, tail, x + w - 6, y + 4,
                    flow == 0 ? UiFx.INK_FAINT
                            : (flow > 0 ? UiFx.TO_TRAIN : UiFx.TO_STATION));
        }
    }

    private void renderOrderGauge(GuiGraphics g, int x, int y, int w) {
        int total = this.deliveryBoard.getTotalOrders();
        int done = this.deliveryBoard.getCompletedOrders();
        UiFx.sectionLabel(g, this.font, "Orders", x, y, x + w);

        float shownDone = this.roll("orders.done", done, 0.20f);
        String headline = total == 0 ? "nothing saved yet"
                : Math.round(shownDone) + " / " + total + " complete";
        this.text(g, headline, x, y + 12, total > 0 && done >= total ? UiFx.OK : UiFx.INK);

        float value = total == 0 ? 0f : UiFx.clamp01(shownDone / total);
        int fill = total == 0 ? UiFx.PAGE_LO
                : (done >= total ? UiFx.OK : (done > 0 ? UiFx.WARN : UiFx.INK_FAINT));
        UiFx.gauge(g, x, y + 24, w, 6, value, fill, this.deliveryBoard.isTrainPresent() && done < total,
                this.frameTime);

        int hidden = this.deliveryBoard.getDismissedOrders();
        int open = Math.max(0, total - done);
        String sub = open + " open" + (hidden > 0 ? "  ·  " + hidden + " hidden" : "");
        this.text(g, sub, x, y + 33, UiFx.INK_FAINT);
    }

    private int tabWidth() {
        return Math.min(128, (this.contentR - this.contentL - 8) / 3);
    }

    private void renderTabs(GuiGraphics g, int mouseX, int mouseY) {
        int tw = this.tabWidth();
        Tab[] tabs = Tab.values();
        for (int i = 0; i < tabs.length; i++) {
            Tab tab = tabs[i];
            int x = this.contentL + i * (tw + 4);
            boolean active = tab == this.currentTab;
            boolean hovered = mouseX >= x && mouseX < x + tw && mouseY >= this.tabsT && mouseY < this.tabsB;

            int face = active ? UiFx.PAGE_HI
                    : UiFx.lerpColor(UiFx.PAGE_LO, UiFx.PAGE, hovered ? 0.7f : 0f);
            g.fill(x, this.tabsT, x + tw, this.tabsB + (active ? 1 : 0), face);
            g.fill(x, this.tabsT, x + tw, this.tabsT + 2, active ? UiFx.BRASS : UiFx.PAGE_SUNK);
            g.fill(x, this.tabsT, x + 1, this.tabsB, UiFx.RULE);
            g.fill(x + tw - 1, this.tabsT, x + tw, this.tabsB, UiFx.RULE);
            if (!active) {
                g.fill(x, this.tabsB - 1, x + tw, this.tabsB, UiFx.RULE);
            }

            String badge = this.tabBadge(tab);
            int badgeW = badge.isEmpty() ? 0 : UiFx.chipWidth(this.font, badge);
            this.text(g, this.trim(tab.label(), tw - badgeW - 16), x + 8, this.tabsT + 6,
                    active ? UiFx.INK : UiFx.INK_DIM);
            if (!badge.isEmpty()) {
                UiFx.chip(g, this.font, x + tw - badgeW - 6, this.tabsT + 4, badge,
                        UiFx.PAGE_HI, this.tabBadgeColor(tab));
            }
        }

        g.fill(this.contentL, this.tabsB, this.contentR, this.tabsB + 1, UiFx.RULE);
    }

    private String tabBadge(Tab tab) {
        return switch (tab) {
            case MANIFEST -> this.lines.isEmpty() ? "" : this.getConfiguredLineCount() + "/" + this.lines.size();
            case ORDERS -> this.deliveryBoard.getTotalOrders() == 0 ? ""
                    : this.deliveryBoard.getCompletedOrders() + "/" + this.deliveryBoard.getTotalOrders();
            case CARGO -> this.cargoRows.isEmpty() ? "" : Integer.toString(this.cargoRows.size());
        };
    }

    private int tabBadgeColor(Tab tab) {
        return switch (tab) {
            case MANIFEST -> this.lines.isEmpty() ? UiFx.INK_FAINT
                    : (this.getConfiguredLineCount() == this.lines.size() ? UiFx.OK : UiFx.WARN);
            case ORDERS -> this.deliveryBoard.getTotalOrders() > 0
                    && this.deliveryBoard.getCompletedOrders() >= this.deliveryBoard.getTotalOrders()
                    ? UiFx.OK : UiFx.INK_DIM;
            case CARGO -> UiFx.INK_DIM;
        };
    }

    private void renderFooter(GuiGraphics g) {
        UiFx.hairline(g, this.contentL, this.footerT, this.contentR - this.contentL, UiFx.RULE);
        String status;
        int ink;
        if (this.draftState.pending()) {
            status = "Waiting for the station to confirm the save.";
            ink = UiFx.INK_DIM;
        } else if (this.saveFlash > 0f) {
            status = "Manifest saved to the station.";
            ink = UiFx.lerpColor(UiFx.INK_DIM, UiFx.OK, UiFx.easeOut(this.saveFlash));
        } else if (this.draftState.dirty()) {
            status = this.confirmClose ? "Unsaved changes: press Close again to discard."
                    : "Draft changes: Publish makes this station's active manifest.";
            ink = UiFx.WARN;
        } else {
            status = this.getManifestSummaryText();
            ink = this.getManifestSummaryColor();
        }
        if (this.draftState.dirty() || this.saveFlash > 0f) {
            int pulse = this.draftState.dirty() && this.saveFlash <= 0f
                    ? Math.round(UiFx.pulse01(this.frameTime, 0.16f) * 90f + 40f) : 200;
            g.fill(this.contentL, this.footerT + 8, this.contentL + 3, this.footerT + 17,
                    UiFx.withAlpha(ink, pulse));
        }
        this.text(g, this.trim(status, this.contentR - this.contentL - 190), this.contentL + 8, this.footerT + 9, ink);
    }

    private List<Component> renderManifestTab(GuiGraphics g, int mouseX, int mouseY) {
        this.linkedHighlight = null;
        List<Component> tooltip = this.renderLineList(g, mouseX, mouseY);
        this.renderEditor(g, mouseX, mouseY);
        List<Component> pickerTip = this.renderPicker(g, mouseX, mouseY);
        return tooltip != null ? tooltip : pickerTip;
    }

    private List<Component> renderLineList(GuiGraphics g, int mouseX, int mouseY) {
        int w = this.listR - this.listL;
        int h = this.listBottom - this.listTop;
        UiFx.plate(g, this.listL, this.listTop, w, h, UiFx.PAGE_HI, UiFx.RULE);
        int ready = this.getConfiguredLineCount();
        UiFx.sectionLabel(g, this.font, "Manifest lines", this.listL + 8, this.listTop + 6, this.listR - 8);
        String count = ready + " of " + this.lines.size() + " ready";
        this.textRight(g, count, this.listR - 8, this.listTop + 6,
                this.lines.isEmpty() ? UiFx.INK_FAINT
                        : (ready == this.lines.size() ? UiFx.OK : UiFx.WARN));

        int rowsY = this.listTop + 42;
        UiFx.hairline(g, this.listL + 1, rowsY - 2, w - 2, UiFx.RULE);
        if (this.lines.isEmpty()) {
            this.text(g, "No cargo moves are drafted yet.", this.listL + 10, rowsY + 10, UiFx.INK_DIM);
            this.text(g, "Add a line, then choose cargo below.", this.listL + 10, rowsY + 22, UiFx.INK_FAINT);
            return null;
        }

        this.lineScroll = Math.max(0, Math.min(this.lineScroll,
                Math.max(0, this.lines.size() - this.listRows)));
        Set<UUID> duplicates = this.getDuplicateLineIds();
        List<Component> tooltip = null;
        g.enableScissor(this.listL + 1, rowsY, this.listR - 1, this.listBottom - 1);
        for (int row = 0; row < this.listRows; row++) {
            int index = this.lineScroll + row;
            if (index >= this.lines.size()) {
                break;
            }
            int top = rowsY + row * ROW_LINE;
            boolean hovered = mouseX >= this.listL && mouseX < this.listR && mouseY >= top && mouseY < top + ROW_LINE;
            FreightManifestLine line = this.lines.get(index);
            boolean configured = this.isLineConfigured(line);
            boolean duplicate = duplicates.contains(line.getLineId());

            if (index == this.selectedIndex) {
                g.fill(this.listL + 1, top, this.listR - 1, top + ROW_LINE, 0x22B58C3E);
            } else if (hovered) {
                g.fill(this.listL + 1, top, this.listR - 1, top + ROW_LINE, 0x14000000);
            }
            if (row > 0) {
                UiFx.hairline(g, this.listL + 6, top, w - 12, UiFx.RULE);
            }

            int statusInk = duplicate ? UiFx.WARN : (configured ? UiFx.OK : UiFx.INK_FAINT);

            g.fill(this.listL + 5, top + 4, this.listL + 8, top + ROW_LINE - 4, statusInk);
            if (!line.getDisplayStack().isEmpty()) {
                g.renderItem(line.getDisplayStack(), this.listL + 12, top + 4);
            }

            int textX = this.listL + 32;
            String amount = FreightManifestLogic.formatAmount(line.getCargoKind(), line.getAmount());
            int amountW = this.font.width(amount);
            String name = line.hasSelection() ? line.getDisplayName() : "Choose cargo";
            this.text(g, this.trim(name, this.listR - 12 - amountW - textX - 4), textX, top + 4,
                    line.hasSelection() ? UiFx.INK : UiFx.INK_FAINT);
            this.textRight(g, amount, this.listR - 8, top + 4,
                    configured ? UiFx.INK : UiFx.INK_FAINT);

            int dirColor = this.directionColor(line.getOperation());
            UiFx.triangle(g, textX, top + 19, 2, line.getOperation() == ManifestOperation.LOAD ? 1 : -1, dirColor);
            String route = line.getOperation() == ManifestOperation.LOAD ? "STATION → TRAIN" : "TRAIN → STATION";
            String repeat = line.getResetMode().getBadgeLabel(line.getResetIntervalValue());
            int repeatW = UiFx.chipWidth(this.font, repeat);
            if (!configured) {
                route = "PICK CARGO TO FINISH THIS LINE";
            } else if (duplicate) {
                route = "DUPLICATE ROUTE";
            }
            this.text(g, this.trim(route, this.listR - textX - repeatW - 20), textX + 7, top + 17,
                    configured && !duplicate ? dirColor : statusInk);
            UiFx.chip(g, this.font, this.listR - repeatW - 8, top + 15, repeat,
                    UiFx.PAGE_HI, UiFx.INK_DIM);

            if (hovered && line.hasSelection()) {
                this.linkedHighlight = line.getResourceId();
            }
            if (hovered && duplicate) {
                tooltip = List.of(Component.literal("Duplicate line"),
                        Component.literal("Another line already moves this cargo the same way; only one of them will run.")
                                .withStyle(ChatFormatting.GRAY));
            }
        }

        if (this.selectedIndex >= this.lineScroll && this.selectedIndex < this.lineScroll + this.listRows) {
            float target = rowsY + (this.selectedIndex - this.lineScroll) * ROW_LINE;
            this.selectionY = Float.isNaN(this.selectionY) ? target
                    : UiFx.approach(this.selectionY, target, this.frameDt, 0.55f);
            int sy = Math.round(this.selectionY);
            g.fill(this.listL + 1, sy + 2, this.listL + 3, sy + ROW_LINE - 2, UiFx.BRASS);
        } else {
            this.selectionY = Float.NaN;
        }
        g.disableScissor();

        this.renderScrollHint(g, this.listR - 5, rowsY, this.listBottom,
                this.lineScroll > 0, this.lineScroll + this.listRows < this.lines.size());
        return tooltip;
    }

    private void renderEditor(GuiGraphics g, int mouseX, int mouseY) {
        int w = this.editorR - this.editorL;
        UiFx.plate(g, this.editorL, this.editorTop, w, this.editorBottom - this.editorTop,
                UiFx.PAGE_HI, UiFx.RULE);

        FreightManifestLine line = this.getSelectedLine();
        if (line == null) {
            this.text(g, "No line selected", this.edX, this.edHeadY + 6, UiFx.INK_DIM);
            this.text(g, "Add a line, or pick one from the list", this.edX, this.edHeadY + 20, UiFx.INK_FAINT);
            this.text(g, "on the left, to edit it here.", this.edX, this.edHeadY + 32, UiFx.INK_FAINT);
            return;
        }

        if (this.editorCompact) {
            if (!line.getDisplayStack().isEmpty()) {
                g.renderItem(line.getDisplayStack(), this.edX, this.edHeadY);
            } else {
                UiFx.sunken(g, this.edX, this.edHeadY, 16, 16, UiFx.PAGE_SUNK);
            }
            String name = line.hasSelection() ? line.getDisplayName() : "No cargo chosen";
            this.text(g, this.trim(name, this.edW - 24), this.edX + 22, this.edHeadY + 1,
                    line.hasSelection() ? UiFx.INK : UiFx.INK_FAINT);
            this.text(g, this.trim("Small view: choose cargo below. Use a taller window to edit this line.",
                            this.edW - 24), this.edX + 22, this.edHeadY + 13, UiFx.INK_FAINT);
            return;
        }

        if (!line.getDisplayStack().isEmpty()) {
            g.renderItem(line.getDisplayStack(), this.edX, this.edHeadY);
        } else {
            UiFx.sunken(g, this.edX, this.edHeadY, 16, 16, UiFx.PAGE_SUNK);
        }
        String name = line.hasSelection() ? line.getDisplayName() : "No cargo assigned";
        this.text(g, this.trim(name, this.edW - 24), this.edX + 22, this.edHeadY + 1,
                line.hasSelection() ? UiFx.INK : UiFx.INK_FAINT);
        String status = this.getSelectedLineStatusText(line);
        int statusInk = this.getSelectedLineStatusColor(line);
        this.text(g, line.getCargoKind().getDisplayName(), this.edX + 22, this.edHeadY + 13, UiFx.INK_FAINT);
        UiFx.chip(g, this.font, this.edX + this.edW - UiFx.chipWidth(this.font, status),
                this.edHeadY + 11, status, UiFx.PAGE_HI, statusInk);

        UiFx.sectionLabel(g, this.font, "Move cargo", this.edX, this.edDirY - 11, this.edX + this.edW);
        int dirSel = line.getOperation() == ManifestOperation.UNLOAD ? 0 : 1;
        this.segmented(g, this.edX, this.edDirY, this.edW, 20,
                new String[]{"Train → station", "Station → train"}, dirSel, mouseX, mouseY,
                dirSel == 0 ? UiFx.TO_STATION : UiFx.TO_TRAIN, this.canEdit);

        UiFx.sectionLabel(g, this.font, "Move each cycle", this.edX, this.edAmountY - 11, this.edX + this.edW);
        UiFx.sunken(g, this.edX + 22, this.edAmountY, this.edW - 104, 20, UiFx.PAGE);
        this.stepper(g, this.edX, this.edAmountY, "−", mouseX, mouseY);
        this.stepper(g, this.edX + this.edW - 82, this.edAmountY, "+", mouseX, mouseY);
        this.textRight(g, line.getCargoKind().getUnitLabel(), this.edX + this.edW - 86, this.edAmountY + 6,
                UiFx.INK_FAINT);
        int[] presets = this.amountPresets(line.getCargoKind());
        for (int i = 0; i < presets.length; i++) {
            int px = this.edX + this.edW - 60 + i * 20;
            boolean hot = mouseX >= px && mouseX < px + 18 && mouseY >= this.edAmountY && mouseY < this.edAmountY + 20;
            UiFx.plate(g, px, this.edAmountY, 18, 20,
                    hot ? UiFx.PAGE : UiFx.PAGE_HI,
                    hot ? UiFx.BRASS : UiFx.RULE);
            String label = this.shortNumber(presets[i]);
            this.text(g, label, px + (18 - this.font.width(label)) / 2, this.edAmountY + 6, UiFx.INK_DIM);
        }

        UiFx.sectionLabel(g, this.font, "Repeat this order", this.edX, this.edResetY - 11, this.edX + this.edW);
        DeliveryOrderResetMode[] modes = DeliveryOrderResetMode.values();
        String[] modeLabels = new String[modes.length];
        for (int i = 0; i < modes.length; i++) {
            modeLabels[i] = switch (modes[i]) {
                case EVERY_ARRIVAL -> "Arrival";
                case ONCE -> "Once";
                case MINECRAFT_DAY -> "Day";
                case EVERY_X_MINUTES -> "Min";
                case EVERY_X_HOURS -> "Hour";
            };
        }
        this.segmented(g, this.edX, this.edResetY, this.edW, 20, modeLabels, line.getResetMode().ordinal(),
                mouseX, mouseY, UiFx.BRASS, this.canEdit);

        if (line.getResetMode().isIntervalBased()) {
            this.text(g, "every", this.edX, this.edIntervalY + 4, UiFx.INK_DIM);
            UiFx.sunken(g, this.edX + 36, this.edIntervalY - 2, 52, 18, UiFx.PAGE);
            this.text(g, line.getResetMode() == DeliveryOrderResetMode.EVERY_X_MINUTES ? "minutes" : "hours",
                    this.edX + 94, this.edIntervalY + 4, UiFx.INK_DIM);
        } else {
            this.text(g, this.resetExplanation(line.getResetMode()), this.edX, this.edIntervalY + 4,
                    UiFx.INK_FAINT);
        }
    }

    private String resetExplanation(DeliveryOrderResetMode mode) {
        return switch (mode) {
            case EVERY_ARRIVAL -> "Reopens whenever a train arrives.";
            case ONCE -> "Runs once, then moves to History.";
            case MINECRAFT_DAY -> "Reopens at the start of each day.";
            default -> "";
        };
    }

    private int[] amountPresets(ManifestCargoKind kind) {
        return kind == ManifestCargoKind.FLUID ? new int[]{1000, 8000, 64000} : new int[]{1, 16, 64};
    }

    private List<Component> renderPicker(GuiGraphics g, int mouseX, int mouseY) {
        int w = this.contentR - this.contentL;
        int h = this.bodyB - this.drawerTop;
        UiFx.plate(g, this.contentL, this.drawerTop, w, h, UiFx.PAGE_HI, UiFx.RULE);

        FreightManifestLine line = this.getSelectedLine();
        String hint;
        if (!this.canEdit) {
            hint = "This manifest is read only.";
        } else if (line == null) {
            hint = "Add or select a line, then choose a stack to describe that cargo.";
        } else if (this.pickerOpen && line.hasSelection()) {
            hint = "Choose a replacement, or Shift-click to add the next matching line.";
        } else if (this.pickerOpen) {
            hint = "Start here: choose the stack this line should move.";
        } else if (line.hasSelection()) {
            hint = "Current cargo stays unchanged. Space opens your full inventory.";
        } else {
            hint = "Choose cargo from the hotbar, or press Space for your inventory.";
        }
        UiFx.sectionLabel(g, this.font, "Inventory cargo", this.contentL + 8, this.drawerTop + 5,
                this.contentL + 102);
        this.text(g, this.trim(hint, w - 144), this.contentL + 116, this.drawerTop + 5, UiFx.INK_FAINT);

        int tx = this.contentR - 22;
        int ty = this.drawerTop + 3;
        boolean hot = mouseX >= tx && mouseX < tx + 18 && mouseY >= ty && mouseY < ty + 13;
        UiFx.plate(g, tx, ty, 18, 13, hot ? UiFx.PAGE : UiFx.PAGE_HI,
                hot ? UiFx.BRASS : UiFx.RULE);
        UiFx.triangle(g, tx + 6, ty + 5, 3, this.pickerOpen ? -2 : 2, UiFx.INK_DIM);

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return null;
        }
        int gridLeft = this.pickerGridLeft();
        int hotbarY = this.bodyB - SLOT - 4;
        Inventory inv = mc.player.getInventory();
        List<Component> tooltip = null;

        g.enableScissor(this.contentL + 1, this.drawerTop + 15, this.contentR - 1, this.bodyB - 1);

        for (int row = 0; row < 3; row++) {
            int y = hotbarY - 6 - (3 - row) * SLOT;
            if (y < this.drawerTop + 15) {
                continue;
            }
            for (int col = 0; col < 9; col++) {
                ItemStack stack = inv.getItem(9 + row * 9 + col);
                if (this.slot(g, stack, gridLeft + col * SLOT, y, mouseX, mouseY) && !stack.isEmpty()) {
                    tooltip = this.getTooltipFromItem(mc, stack);
                }
            }
        }
        if (hotbarY >= this.drawerTop + 15) {
            for (int col = 0; col < 9; col++) {
                ItemStack stack = inv.getItem(col);
                if (this.slot(g, stack, gridLeft + col * SLOT, hotbarY, mouseX, mouseY) && !stack.isEmpty()) {
                    tooltip = this.getTooltipFromItem(mc, stack);
                }
            }
        }
        g.disableScissor();
        return tooltip;
    }

    private int pickerGridLeft() {
        return this.contentL + Math.max(8, (this.contentR - this.contentL - 9 * SLOT) / 2);
    }

    private boolean slot(GuiGraphics g, ItemStack stack, int x, int y, int mouseX, int mouseY) {
        boolean hovered = mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16;
        UiFx.sunken(g, x - 1, y - 1, 18, 18, hovered ? UiFx.PAGE : UiFx.PAGE_SUNK);
        if (!stack.isEmpty()) {
            g.renderItem(stack, x, y);
            g.renderItemDecorations(this.font, stack, x, y);

            if (this.isOnManifest(stack)) {
                g.fill(x + 13, y - 1, x + 17, y + 1, UiFx.BRASS);
                g.fill(x + 15, y - 1, x + 17, y + 3, UiFx.BRASS);
            }
            if (this.linkedHighlight != null && this.linkedHighlight.equals(resourceOf(stack))) {
                int pulse = Math.round(140 + 90 * UiFx.pulse01(this.frameTime, 0.30f));
                g.fill(x - 1, y - 1, x + 17, y + 17, UiFx.withAlpha(UiFx.BRASS, 46));
                UiFx.outline(g, x - 2, y - 2, 20, 20,
                        UiFx.withAlpha(UiFx.BRASS, pulse));
            }
        }
        if (hovered) {
            UiFx.outline(g, x - 1, y - 1, 18, 18, UiFx.BRASS);
        }
        return hovered;
    }

    private static ResourceLocation resourceOf(ItemStack stack) {
        FluidStack fluid = FluidUtil.getFluidContained(stack).orElse(FluidStack.EMPTY);
        return fluid.isEmpty()
                ? BuiltInRegistries.ITEM.getKey(stack.getItem())
                : BuiltInRegistries.FLUID.getKey(fluid.getFluid());
    }

    private boolean isOnManifest(ItemStack stack) {
        ResourceLocation id = resourceOf(stack);
        if (id == null) {
            return false;
        }
        for (FreightManifestLine line : this.lines) {
            if (line.hasSelection() && id.equals(line.getResourceId())) {
                return true;
            }
        }
        return false;
    }

    private List<Component> renderOrdersTab(GuiGraphics g, int mouseX, int mouseY) {
        int w = this.contentR - this.contentL;
        UiFx.plate(g, this.contentL, this.summaryTop, w, this.summaryBottom - this.summaryTop,
                UiFx.PAGE_HI, UiFx.RULE);
        UiFx.sectionLabel(g, this.font, "Saved station board", this.contentL + 8, this.summaryTop + 6,
                this.contentR - 270);
        int total = this.deliveryBoard.getTotalOrders();
        int done = this.deliveryBoard.getCompletedOrders();
        int archived = this.deliveryBoard.getDismissedOrders();
        float shown = this.roll("board.done", done, 0.20f);
        String head = total == 0 ? "No orders saved: finish the draft, then press Publish."
                : Math.max(0, total - archived) + " active, " + archived + " archived";
        this.text(g, head, this.contentL + 8, this.summaryTop + 18,
                total > 0 && done >= total ? UiFx.OK : UiFx.INK);
        if (total > 0) {
            UiFx.gauge(g, this.contentL + 8, this.summaryTop + 29, Math.min(260, w - 280), 6,
                    UiFx.clamp01(shown / total),
                    done >= total ? UiFx.OK : UiFx.WARN,
                    this.deliveryBoard.isTrainPresent() && done < total, this.frameTime);
        }
        this.text(g, this.trim(this.deliveryBoard.getSummaryStatus(), w - 24), this.contentL + 8,
                this.summaryBottom - 12, UiFx.INK_FAINT);

        List<DeliveryOrderLineView> rows = this.activeOrderRows();
        int listH = this.rowsBottom - this.rowsTop;
        UiFx.plate(g, this.contentL, this.rowsTop, w, listH, UiFx.PAGE_HI, UiFx.RULE);
        if (rows.isEmpty()) {
            String title = total > 0 ? "No active orders." : "No delivery orders yet.";
            String detail = total > 0
                    ? "Completed receipts remain available on the Cargo history."
                    : "Orders appear here once a manifest is applied to the station.";
            this.text(g, title, this.contentL + 10, this.rowsTop + 12, UiFx.INK_DIM);
            this.text(g, detail, this.contentL + 10, this.rowsTop + 24, UiFx.INK_FAINT);
            return null;
        }

        int visible = Math.max(1, (listH - 4) / ROW_ORDER);
        this.orderScroll = Math.max(0, Math.min(this.orderScroll, Math.max(0, rows.size() - visible)));
        List<Component> tooltip = null;
        g.enableScissor(this.contentL + 1, this.rowsTop + 1, this.contentR - 1, this.rowsBottom - 1);
        for (int i = 0; i < visible; i++) {
            int index = this.orderScroll + i;
            if (index >= rows.size()) {
                break;
            }
            int top = this.rowsTop + 2 + i * ROW_ORDER;
            List<Component> rowTip = this.renderOrderRow(g, rows.get(index), top, mouseX, mouseY, i > 0);
            if (rowTip != null) {
                tooltip = rowTip;
            }
        }
        g.disableScissor();
        this.renderScrollHint(g, this.contentR - 5, this.rowsTop, this.rowsBottom,
                this.orderScroll > 0, this.orderScroll + visible < rows.size());
        return tooltip;
    }

    private List<Component> renderOrderRow(GuiGraphics g, DeliveryOrderLineView row, int top,
                                           int mouseX, int mouseY, boolean divider) {
        int left = this.contentL;
        int right = this.contentR;
        boolean hovered = mouseX >= left && mouseX < right && mouseY >= top && mouseY < top + ROW_ORDER;
        if (hovered) {
            g.fill(left + 1, top, right - 1, top + ROW_ORDER, 0x12000000);
        }
        if (divider) {
            UiFx.hairline(g, left + 8, top, right - left - 16, UiFx.RULE);
        }

        float flash = this.orderCompleteFlash.getOrDefault(row.getLineId(), 0f);
        if (flash > 0f) {
            g.fill(left + 1, top, right - 1, top + ROW_ORDER,
                    UiFx.withAlpha(UiFx.OK, Math.round(UiFx.easeOut(flash) * 46f)));
        }

        int statusInk = row.isDismissed() ? UiFx.INK_FAINT
                : (row.isComplete() ? UiFx.OK
                : (row.getDeliveredAmount() > 0 ? UiFx.WARN : UiFx.INK_DIM));
        g.fill(left + 5, top + 5, left + 8, top + ROW_ORDER - 5, statusInk);
        if (!row.getDisplayStack().isEmpty()) {
            g.renderItem(row.getDisplayStack(), left + 12, top + 4);
        }

        int actionW = 46;
        int dismissW = 40;
        int dismissX = right - 8 - dismissW;
        int actionX = dismissX - 5 - actionW;
        int textX = left + 32;
        int ink = row.isDismissed() ? UiFx.INK_FAINT : UiFx.INK;

        int dirColor = this.directionColor(row.getOperation());
        UiFx.triangle(g, textX, top + 4, 3, row.getOperation() == ManifestOperation.LOAD ? 1 : -1, dirColor);
        String badge = row.getResetMode().getBadgeLabel(row.getResetIntervalValue());
        int badgeX = actionX - UiFx.chipWidth(this.font, badge) - 8;
        UiFx.chip(g, this.font, badgeX, top + 2,
                badge, UiFx.PAGE_HI, UiFx.INK_DIM);
        int nameX = textX + 8;
        String stateLabel = row.isComplete() ? "DONE" : this.isNextActiveOrder(row) ? "NEXT" : null;
        if (stateLabel != null) {
            int stateInk = row.isComplete() ? UiFx.OK : UiFx.BRASS;
            int stateW = UiFx.chipWidth(this.font, stateLabel);
            UiFx.chip(g, this.font, nameX, top + 2, stateLabel, UiFx.PAGE_HI, stateInk);
            nameX += stateW + 4;
        }
        this.text(g, this.trim(row.getDisplayName(), badgeX - nameX - 5), nameX, top + 3, ink);

        String figures = FreightManifestLogic.formatAmount(row.getCargoKind(), row.getDeliveredAmount())
                + " / " + FreightManifestLogic.formatAmount(row.getCargoKind(), row.getTargetAmount());
        int figuresW = this.font.width(figures);
        int barRight = actionX - 12 - figuresW - 6;
        if (row.getTargetAmount() > 0 && barRight > textX + 40) {
            float value = this.orderProgressAnim.getOrDefault(row.getLineId(),
                    UiFx.clamp01(row.getDeliveredAmount() / (float) row.getTargetAmount()));
            boolean active = !row.isComplete() && !row.isDismissed() && this.deliveryBoard.isTrainPresent();
            UiFx.gauge(g, textX, top + 15, barRight - textX, 6, value,
                    row.isDismissed() ? UiFx.INK_FAINT
                            : (row.isComplete() ? UiFx.OK : dirColor), active, this.frameTime);
            this.text(g, figures, barRight + 6, top + 14, ink);
        }

        StringBuilder meta = new StringBuilder(row.getStatusText());
        if (row.getFreightAmount() > 0) {
            meta.append("  ·  on train ")
                    .append(FreightManifestLogic.formatAmount(row.getCargoKind(), row.getFreightAmount()));
        }
        if (row.getTicksUntilReset() > 0L) {
            meta.append("  ·  resets in ").append(FreightManifestLogic.formatTicks(row.getTicksUntilReset()));
        }
        if (!row.isComplete() && !row.isDismissed()) {
            UiFx.RateTracker tracker = this.rates.get(row.getLineId());
            long eta = tracker == null ? -1L
                    : tracker.estimate(row.getTargetAmount() - row.getDeliveredAmount());
            if (eta > 0L) {
                meta.append("  ·  ~").append(FreightManifestLogic.formatTicks(eta)).append(" left");
            }
        }
        this.text(g, this.trim(meta.toString(), actionX - textX - 8), textX, top + 24, UiFx.INK_FAINT);

        this.miniButton(g, actionX, top + 7, actionW, row.isComplete() ? "Reset" : "Done", true, mouseX, mouseY);
        boolean dismissActive = row.isComplete() || row.isDismissed();
        this.miniButton(g, dismissX, top + 7, dismissW, row.isDismissed() ? "Show" : "Hide",
                dismissActive, mouseX, mouseY);

        if (mouseY >= top + 7 && mouseY < top + 23) {
            if (mouseX >= actionX && mouseX < actionX + actionW) {
                return List.of(
                        Component.literal(row.isComplete() ? "Reset this order cycle" : "Mark complete for this cycle"),
                        Component.literal(row.isComplete()
                                ? "Clears progress and reopens the line."
                                : "Use when this order was satisfied outside live tracking.")
                                .withStyle(ChatFormatting.GRAY));
            }
            if (dismissActive && mouseX >= dismissX && mouseX < dismissX + dismissW) {
                return List.of(
                        Component.literal(row.isDismissed() ? "Show hidden order again" : "Hide this completed order"),
                        Component.literal(row.isDismissed()
                                ? "Bring the line back into the visible list."
                                : "Keeps the board tidy for this cycle only.").withStyle(ChatFormatting.GRAY));
            }
        }
        return null;
    }

    private List<Component> renderCargoTab(GuiGraphics g, int mouseX, int mouseY) {
        int w = this.contentR - this.contentL;
        UiFx.plate(g, this.contentL, this.summaryTop, w, this.summaryBottom - this.summaryTop,
                UiFx.PAGE_HI, UiFx.RULE);

        int itemTypes = 0;
        int fluidTypes = 0;
        for (CargoRow row : this.cargoRows) {
            if (row.kind == ManifestCargoKind.ITEM) {
                itemTypes++;
            } else {
                fluidTypes++;
            }
        }
        FreightManifestEvaluationResult preview = this.getPreviewEvaluation();
        UiFx.sectionLabel(g, this.font, "Live cargo", this.contentL + 8, this.summaryTop + 6, this.contentR - 8);
        String head = this.cargoView.isTrainPresent()
                ? this.cargoView.getTrainName() + "  ·  " + this.cargoView.getCarriageCount() + " carriages"
                : "No train in the berth — showing the manifest's own targets";
        this.text(g, this.trim(head, w - 24), this.contentL + 8, this.summaryTop + 18,
                this.cargoView.isTrainPresent() ? UiFx.INK : UiFx.INK_DIM);
        this.text(g, this.trim(itemTypes + " item types  ·  " + fluidTypes + " fluid types  ·  "
                        + preview.getStatusMessage(), w - 24),
                this.contentL + 8, this.summaryBottom - 12, this.getPreviewStatusColor(preview));

        if (this.historyExpanded) {
            return this.renderHistory(g, mouseX, mouseY, this.contentL, this.contentR, true);
        }
        List<Component> tip = this.renderCargoTable(g, mouseX, mouseY, this.contentL, this.cargoSplitX);
        List<Component> historyTip = this.renderHistory(g, mouseX, mouseY, this.historyL, this.contentR, false);
        return tip != null ? tip : historyTip;
    }

    private List<Component> renderCargoTable(GuiGraphics g, int mouseX, int mouseY, int left, int right) {
        int w = right - left;
        int h = this.rowsBottom - this.rowsTop;
        UiFx.plate(g, left, this.rowsTop, w, h, UiFx.PAGE_HI, UiFx.RULE);

        int onTrainX = left + w * 13 / 20;
        int toStationX = left + w * 17 / 20;
        int toTrainX = right - 8;
        UiFx.sectionLabel(g, this.font, "Resource", left + 8, this.rowsTop + 6, onTrainX - 60);
        this.textRight(g, "HOLD", onTrainX, this.rowsTop + 6, UiFx.INK_FAINT);
        UiFx.triangle(g, toStationX - 4, this.rowsTop + 7, 2, -1, UiFx.TO_STATION);
        UiFx.triangle(g, toTrainX - 4, this.rowsTop + 7, 2, 1, UiFx.TO_TRAIN);

        int rowsY = this.rowsTop + 20;
        UiFx.hairline(g, left + 1, rowsY - 2, w - 2, UiFx.RULE);
        if (this.cargoRows.isEmpty()) {
            this.text(g, "Nothing tracked yet.", left + 10, rowsY + 10, UiFx.INK_DIM);
            return null;
        }

        int visible = Math.max(1, (this.rowsBottom - rowsY - 2) / ROW_CARGO);
        this.cargoScroll = Math.max(0, Math.min(this.cargoScroll,
                Math.max(0, this.cargoRows.size() - visible)));
        CargoRow hoveredRow = null;
        g.enableScissor(left + 1, rowsY, right - 1, this.rowsBottom - 1);
        for (int i = 0; i < visible; i++) {
            int index = this.cargoScroll + i;
            if (index >= this.cargoRows.size()) {
                break;
            }
            CargoRow row = this.cargoRows.get(index);
            int top = rowsY + i * ROW_CARGO;
            boolean hovered = mouseX >= left && mouseX < right && mouseY >= top && mouseY < top + ROW_CARGO;
            if (hovered) {
                g.fill(left + 1, top, right - 1, top + ROW_CARGO, 0x12000000);
                hoveredRow = row;
            }
            if (i > 0) {
                UiFx.hairline(g, left + 8, top, w - 16, UiFx.RULE);
            }

            if (row.hasManifestGoal()) {
                int y0 = top + 4;
                int y1 = top + ROW_CARGO - 4;
                if (row.expectedToStation > 0 && row.expectedToTrain > 0) {
                    int mid = (y0 + y1) / 2;
                    g.fill(left + 5, y0, left + 8, mid, UiFx.TO_STATION);
                    g.fill(left + 5, mid, left + 8, y1, UiFx.TO_TRAIN);
                } else {
                    g.fill(left + 5, y0, left + 8, y1,
                            row.expectedToStation > 0 ? UiFx.TO_STATION : UiFx.TO_TRAIN);
                }
            }
            if (!row.icon.isEmpty()) {
                g.renderItem(row.icon, left + 12, top + 1);
            }
            this.text(g, this.trim(row.name, onTrainX - left - 76), left + 32, top + 4,
                    row.hasManifestGoal() ? UiFx.INK : UiFx.INK_DIM);
            this.textRight(g, row.amountText(row.freightAmount), onTrainX, top + 4, UiFx.INK_DIM);
            this.textRight(g, row.amountText(row.toStationAmount), toStationX, top + 4,
                    row.toStationAmount > 0 ? UiFx.TO_STATION : UiFx.INK_FAINT);
            this.textRight(g, row.amountText(row.toTrainAmount), toTrainX, top + 4,
                    row.toTrainAmount > 0 ? UiFx.TO_TRAIN : UiFx.INK_FAINT);

            if (row.hasManifestGoal()) {
                int goal = row.expectedToStation + row.expectedToTrain;
                int moved = Math.min(row.toStationAmount, row.expectedToStation)
                        + Math.min(row.toTrainAmount, row.expectedToTrain);
                float value = goal <= 0 ? 0f : UiFx.clamp01(moved / (float) goal);
                int barW = onTrainX - 8 - (left + 32);
                if (barW > 12) {
                    UiFx.sunken(g, left + 32, top + 14, barW, 3, UiFx.PAGE_SUNK);
                    g.fill(left + 33, top + 15, left + 33 + Math.round((barW - 2) * value), top + 16,
                            value >= 1f ? UiFx.OK : UiFx.WARN);
                }
            }
        }
        g.disableScissor();
        this.renderScrollHint(g, right - 5, rowsY, this.rowsBottom,
                this.cargoScroll > 0, this.cargoScroll + visible < this.cargoRows.size());

        if (hoveredRow != null && hoveredRow.hasManifestGoal()) {
            List<Component> tooltip = new ArrayList<>();
            tooltip.add(Component.literal(hoveredRow.name));
            if (hoveredRow.expectedToStation > 0) {
                tooltip.add(Component.literal("Unload target: "
                        + hoveredRow.amountText(Math.min(hoveredRow.toStationAmount, hoveredRow.expectedToStation))
                        + " / " + hoveredRow.amountText(hoveredRow.expectedToStation))
                        .withStyle(ChatFormatting.GRAY));
            }
            if (hoveredRow.expectedToTrain > 0) {
                tooltip.add(Component.literal("Load target: "
                        + hoveredRow.amountText(Math.min(hoveredRow.toTrainAmount, hoveredRow.expectedToTrain))
                        + " / " + hoveredRow.amountText(hoveredRow.expectedToTrain))
                        .withStyle(ChatFormatting.GRAY));
            }
            return tooltip;
        }
        return null;
    }

    private List<Component> renderHistory(GuiGraphics g, int mouseX, int mouseY, int left, int right, boolean expanded) {
        int w = right - left;
        int h = this.rowsBottom - this.rowsTop;
        UiFx.plate(g, left, this.rowsTop, w, h, UiFx.PAGE_HI, UiFx.RULE);
        UiFx.sectionLabel(g, this.font, "History", left + 8, this.rowsTop + 6, right - 26);

        int tx = right - 22;
        int ty = this.rowsTop + 3;
        boolean hot = mouseX >= tx && mouseX < tx + 18 && mouseY >= ty && mouseY < ty + 13;
        UiFx.plate(g, tx, ty, 18, 13, hot ? UiFx.PAGE : UiFx.PAGE_HI,
                hot ? UiFx.BRASS : UiFx.RULE);
        this.text(g, expanded ? "–" : "+", tx + 7, ty + 3, UiFx.INK_DIM);

        List<DeliveryOrderHistoryEntry> entries = this.deliveryBoard.getHistoryEntries();
        int rowsY = this.rowsTop + 20;
        UiFx.hairline(g, left + 1, rowsY - 2, w - 2, UiFx.RULE);
        if (entries.isEmpty()) {
            this.text(g, "Nothing has happened yet.", left + 10, rowsY + 10, UiFx.INK_FAINT);
            return hot ? List.of(Component.literal(expanded ? "Collapse history" : "Expand history")) : null;
        }

        int rowH = expanded ? ROW_HISTORY_BIG : ROW_HISTORY;
        int visible = Math.max(1, (this.rowsBottom - rowsY - 2) / rowH);
        this.historyScroll = Math.max(0, Math.min(this.historyScroll, Math.max(0, entries.size() - visible)));
        int spineX = left + 14;
        g.enableScissor(left + 1, rowsY, right - 1, this.rowsBottom - 1);
        g.fill(spineX, rowsY, spineX + 1, this.rowsBottom - 2, UiFx.RULE);
        for (int i = 0; i < visible; i++) {
            int index = this.historyScroll + i;
            if (index >= entries.size()) {
                break;
            }
            DeliveryOrderHistoryEntry entry = entries.get(index);
            int top = rowsY + i * rowH;
            int color = this.historyColor(entry.getType());
            int cy = top + 6;
            g.fill(spineX - 2, cy - 2, spineX + 3, cy + 3, UiFx.PAGE_HI);
            g.fill(spineX - 1, cy - 1, spineX + 2, cy + 2, color);
            String age = FreightManifestLogic.formatTicks(
                    Math.max(0L, this.deliveryBoard.getCurrentTick() - entry.getTick()));
            this.textRight(g, age, right - 8, top + 2, UiFx.INK_FAINT);
            int textW = right - 8 - this.font.width(age) - 8 - (spineX + 8);
            if (expanded) {
                this.text(g, this.trim(entry.getTitle(), textW), spineX + 8, top + 2, color);
                this.text(g, this.trim(entry.getDetail(), right - 8 - (spineX + 8)), spineX + 8, top + 14,
                        UiFx.INK_DIM);
            } else {
                this.text(g, this.trim(entry.getTitle(), textW), spineX + 8, top + 2, color);
                this.text(g, this.trim(entry.getDetail(), right - 8 - (spineX + 8)), spineX + 8, top + 12,
                        UiFx.INK_FAINT);
            }
        }
        g.disableScissor();
        this.renderScrollHint(g, right - 5, rowsY, this.rowsBottom,
                this.historyScroll > 0, this.historyScroll + visible < entries.size());
        return hot ? List.of(Component.literal(expanded ? "Collapse history" : "Expand history")) : null;
    }

    private int historyColor(DeliveryOrderHistoryType type) {
        return switch (type) {
            case COMPLETED, MANUAL_COMPLETE, SHOWN -> UiFx.OK;
            case HIDDEN -> UiFx.INK_FAINT;
            case AUTO_RESET, MANUAL_RESET -> UiFx.WARN;
        };
    }

    private void text(GuiGraphics g, String s, int x, int y, int color) {
        g.drawString(this.font, s, x, y, color, false);
    }

    private void textRight(GuiGraphics g, String s, int xRight, int y, int color) {
        g.drawString(this.font, s, xRight - this.font.width(s), y, color, false);
    }

    private String trim(String text, int width) {
        if (width <= 0) {
            return "";
        }
        if (this.font.width(text) <= width) {
            return text;
        }
        return this.font.plainSubstrByWidth(text, Math.max(0, width - this.font.width("…"))) + "…";
    }

    private String shortNumber(int value) {
        if (value >= 1000 && value % 1000 == 0) {
            return (value / 1000) + "k";
        }
        return Integer.toString(value);
    }

    private void segmented(GuiGraphics g, int x, int y, int w, int h, String[] labels, int selected,
                           int mouseX, int mouseY, int accent, boolean enabled) {
        UiFx.sunken(g, x, y, w, h, UiFx.PAGE_SUNK);
        int n = labels.length;
        for (int i = 0; i < n; i++) {
            int cx = x + 1 + (w - 2) * i / n;
            int cw = x + 1 + (w - 2) * (i + 1) / n - cx;
            boolean on = i == selected;
            boolean hot = enabled && mouseX >= cx && mouseX < cx + cw && mouseY >= y && mouseY < y + h;
            if (on) {
                UiFx.raised(g, cx, y + 1, cw, h - 2, UiFx.PAGE_HI);
                g.fill(cx, y + 1, cx + cw, y + 3, accent);
            } else if (hot) {
                g.fill(cx, y + 1, cx + cw, y + h - 1, 0x1AFFFFFF);
            }
            if (i > 0 && !on) {
                g.fill(cx, y + 3, cx + 1, y + h - 3, UiFx.RULE);
            }
            String label = this.trim(labels[i], cw - 6);
            this.text(g, label, cx + (cw - this.font.width(label)) / 2, y + (h - 8) / 2,
                    on ? UiFx.INK : UiFx.INK_DIM);
        }
        if (!enabled) {
            UiFx.hatch(g, x + 1, y + 1, w - 2, h - 2, 0x18000000, 4);
        }
    }

    private int segmentAt(int x, int y, int w, int h, int count, double mouseX, double mouseY) {
        if (mouseY < y || mouseY >= y + h || mouseX < x || mouseX >= x + w) {
            return -1;
        }
        for (int i = 0; i < count; i++) {
            int cx = x + 1 + (w - 2) * i / count;
            int cw = x + 1 + (w - 2) * (i + 1) / count - cx;
            if (mouseX >= cx && mouseX < cx + cw) {
                return i;
            }
        }
        return -1;
    }

    private void stepper(GuiGraphics g, int x, int y, String glyph, int mouseX, int mouseY) {
        boolean hot = mouseX >= x && mouseX < x + 20 && mouseY >= y && mouseY < y + 20;
        UiFx.plate(g, x, y, 20, 20, hot ? UiFx.PAGE : UiFx.PAGE_HI,
                hot ? UiFx.BRASS : UiFx.RULE);
        this.text(g, glyph, x + (20 - this.font.width(glyph)) / 2, y + 6, UiFx.INK);
    }

    private void miniButton(GuiGraphics g, int x, int y, int w, String label, boolean active, int mouseX, int mouseY) {
        int h = 16;
        boolean hot = active && mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
        UiFx.plate(g, x, y, w, h,
                active ? (hot ? UiFx.PAGE : UiFx.PAGE_HI) : UiFx.PAGE_LO,
                active ? (hot ? UiFx.BRASS : UiFx.RULE) : UiFx.RULE);
        this.text(g, label, x + (w - this.font.width(label)) / 2, y + 4,
                active ? UiFx.INK : UiFx.INK_FAINT);
    }

    private void renderScrollHint(GuiGraphics g, int x, int topY, int bottomY, boolean up, boolean down) {
        float breathe = 0.55f + 0.45f * UiFx.pulse01(this.frameTime, 0.14f);
        if (up) {
            UiFx.triangle(g, x, topY + 3, 2, 2,
                    UiFx.multiplyAlpha(UiFx.INK_DIM, breathe));
        }
        if (down) {
            UiFx.triangle(g, x, bottomY - 7, 2, -2,
                    UiFx.multiplyAlpha(UiFx.INK_DIM, breathe));
        }
    }

    private int directionColor(ManifestOperation operation) {
        return operation == ManifestOperation.LOAD ? UiFx.TO_TRAIN : UiFx.TO_STATION;
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        boolean typing = this.getFocused() instanceof EditBox box && box.isFocused();
        boolean ctrl = Screen.hasControlDown();

        if (ctrl && key == GLFW.GLFW_KEY_S) {
            this.triggerSave();
            return true;
        }
        if (typing && key == GLFW.GLFW_KEY_ENTER) {
            this.applyEditorInputs();
            this.setFocused(null);
            return true;
        }
        if (typing) {
            return super.keyPressed(key, scan, modifiers);
        }

        if (ctrl && this.canEdit) {
            switch (key) {
                case GLFW.GLFW_KEY_N -> {
                    if (this.currentTab == Tab.MANIFEST && this.lines.size() < FreightManifestLogic.MAX_LINES) {
                        this.addLine();
                    }
                    return true;
                }
                case GLFW.GLFW_KEY_D -> {
                    if (this.currentTab == Tab.MANIFEST && this.hasSelection()
                            && this.lines.size() < FreightManifestLogic.MAX_LINES) {
                        this.duplicateSelectedLine();
                    }
                    return true;
                }
                default -> {
                }
            }
        }

        switch (key) {
            case GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_RIGHT -> {
                this.cycleTab(key == GLFW.GLFW_KEY_RIGHT ? 1 : -1);
                return true;
            }
            case GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_DOWN -> {
                if (this.moveSelection(key == GLFW.GLFW_KEY_DOWN ? 1 : -1)) {
                    return true;
                }
            }
            case GLFW.GLFW_KEY_SPACE -> {
                // only without focus, space presses the focused widget

                if (this.currentTab == Tab.MANIFEST && this.getFocused() == null) {
                    this.pickerOpen = !this.pickerOpen;
                    this.playPaper(SoundEvents.BOOK_PAGE_TURN, this.pickerOpen ? 1.25f : 0.85f);
                    return true;
                }
            }
            case GLFW.GLFW_KEY_DELETE -> {
                if (this.currentTab == Tab.MANIFEST && this.canEdit && this.hasSelection()) {
                    this.removeSelectedLine();
                    return true;
                }
            }
            case GLFW.GLFW_KEY_EQUAL, GLFW.GLFW_KEY_KP_ADD -> {
                if (this.adjustSelectedAmount(1)) {
                    return true;
                }
            }
            case GLFW.GLFW_KEY_MINUS, GLFW.GLFW_KEY_KP_SUBTRACT -> {
                if (this.adjustSelectedAmount(-1)) {
                    return true;
                }
            }
            default -> {
            }
        }
        return super.keyPressed(key, scan, modifiers);
    }

    private void cycleTab(int delta) {
        Tab[] tabs = Tab.values();
        Tab next = tabs[Math.floorMod(this.currentTab.ordinal() + delta, tabs.length)];
        if (next == this.currentTab) {
            return;
        }
        this.applyEditorInputs();
        this.currentTab = next;
        LAST_OPENED_TAB = next;
        this.tabFade = 0f;
        this.playPaper(SoundEvents.BOOK_PAGE_TURN, 1.0f);
        this.rebuildWidgets();
    }

    private boolean moveSelection(int delta) {
        if (this.currentTab != Tab.MANIFEST) {
            switch (this.currentTab) {
                case ORDERS -> this.orderScroll = Math.max(0, this.orderScroll + delta);
                case CARGO -> {
                    if (this.historyExpanded) {
                        this.historyScroll = Math.max(0, this.historyScroll + delta);
                    } else {
                        this.cargoScroll = Math.max(0, this.cargoScroll + delta);
                    }
                }
                default -> {
                }
            }
            return true;
        }
        if (this.lines.isEmpty()) {
            return false;
        }
        this.applyEditorInputs();
        int next = Math.max(0, Math.min(this.lines.size() - 1, this.selectedIndex + delta));
        if (next == this.selectedIndex) {
            return true;
        }
        this.selectedIndex = next;
        this.ensureSelectionVisible();
        this.playPaper(SoundEvents.UI_LOOM_SELECT_PATTERN, 0.95f);
        this.rebuildWidgets();
        return true;
    }

    private boolean adjustSelectedAmount(int direction) {
        FreightManifestLine line = this.getSelectedLine();
        if (line == null || !this.canEdit || this.currentTab != Tab.MANIFEST) {
            return false;
        }
        int step = line.getCargoKind() == ManifestCargoKind.FLUID ? 100 : 1;
        if (Screen.hasShiftDown()) {
            step *= 10;
        }
        this.setAmount(line, line.getAmount() + step * direction);
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        this.layout();
        if (this.handleTabClick(mouseX, mouseY)) {
            return true;
        }
        boolean handled = switch (this.currentTab) {
            case MANIFEST -> this.handleManifestClick(mouseX, mouseY);
            case ORDERS -> this.handleOrderClick(mouseX, mouseY);
            case CARGO -> this.handleCargoClick(mouseX, mouseY);
        };
        return handled || super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean handleTabClick(double mouseX, double mouseY) {
        if (mouseY < this.tabsT || mouseY >= this.tabsB) {
            return false;
        }
        int tw = this.tabWidth();
        Tab[] tabs = Tab.values();
        for (int i = 0; i < tabs.length; i++) {
            int x = this.contentL + i * (tw + 4);
            if (mouseX >= x && mouseX < x + tw) {
                if (tabs[i] != this.currentTab) {
                    this.applyEditorInputs();
                    this.currentTab = tabs[i];
                    LAST_OPENED_TAB = tabs[i];
                    this.tabFade = 0f;
                    this.playPaper(SoundEvents.BOOK_PAGE_TURN, 1.0f);
                    this.rebuildWidgets();
                }
                return true;
            }
        }
        return false;
    }

    private boolean handleManifestClick(double mouseX, double mouseY) {
        int tx = this.contentR - 22;
        int ty = this.drawerTop + 3;
        if (mouseX >= tx && mouseX < tx + 18 && mouseY >= ty && mouseY < ty + 13) {
            this.pickerOpen = !this.pickerOpen;
            this.playPaper(SoundEvents.BOOK_PAGE_TURN, this.pickerOpen ? 1.25f : 0.85f);
            return true;
        }

        int rowsY = this.listTop + 42;
        if (mouseX >= this.listL && mouseX < this.listR && mouseY >= rowsY && mouseY < this.listBottom) {
            int index = this.lineScroll + (int) ((mouseY - rowsY) / ROW_LINE);
            if (index >= 0 && index < this.lines.size() && index != this.selectedIndex) {
                this.applyEditorInputs();
                this.selectedIndex = index;
                this.playUiClick(1.0f);
                this.rebuildWidgets();
            }
            return true;
        }

        FreightManifestLine line = this.getSelectedLine();
        if (line != null && this.canEdit && !this.editorCompact) {
            int dir = this.segmentAt(this.edX, this.edDirY, this.edW, 20, 2, mouseX, mouseY);
            if (dir >= 0) {
                ManifestOperation op = dir == 0 ? ManifestOperation.UNLOAD : ManifestOperation.LOAD;
                if (op != line.getOperation()) {
                    line.setOperation(op);
                    this.refreshCargoRows();
                    this.markDirty();
                    this.playUiClick(1.05f);
                    this.rebuildWidgets();
                }
                return true;
            }

            DeliveryOrderResetMode[] modes = DeliveryOrderResetMode.values();
            int mode = this.segmentAt(this.edX, this.edResetY, this.edW, 20, modes.length, mouseX, mouseY);
            if (mode >= 0) {
                if (modes[mode] != line.getResetMode()) {
                    line.setResetMode(modes[mode]);
                    if (modes[mode].isIntervalBased() && line.getResetIntervalValue() <= 0) {
                        line.setResetIntervalValue(1);
                    }
                    this.markDirty();
                    this.playUiClick(1.05f);
                    this.rebuildWidgets();
                }
                return true;
            }

            if (mouseY >= this.edAmountY && mouseY < this.edAmountY + 20) {
                int step = line.getCargoKind() == ManifestCargoKind.FLUID ? 100 : 1;
                if (Screen.hasShiftDown()) {
                    step *= 10;
                }
                if (mouseX >= this.edX && mouseX < this.edX + 20) {
                    this.setAmount(line, line.getAmount() - step);
                    return true;
                }
                if (mouseX >= this.edX + this.edW - 82 && mouseX < this.edX + this.edW - 62) {
                    this.setAmount(line, line.getAmount() + step);
                    return true;
                }
                int[] presets = this.amountPresets(line.getCargoKind());
                for (int i = 0; i < presets.length; i++) {
                    int px = this.edX + this.edW - 60 + i * 20;
                    if (mouseX >= px && mouseX < px + 18) {
                        this.setAmount(line, presets[i]);
                        return true;
                    }
                }
            }
        }

        return this.handlePickerClick(mouseX, mouseY);
    }

    private void setAmount(FreightManifestLine line, int amount) {
        int clamped = Math.max(1, amount);
        if (line.getAmount() != clamped) {
            line.setAmount(clamped);
            this.syncAmountInputFromSelection();
            this.refreshCargoRows();
            this.markDirty();
        }
        this.playUiClick(1.15f);
    }

    private boolean handlePickerClick(double mouseX, double mouseY) {
        if (mouseY < this.drawerTop || mouseY >= this.bodyB) {
            return false;
        }
        if (!this.canEdit || !this.hasSelection()) {
            return true;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return true;
        }
        int slot = this.pickerSlotAt(mouseX, mouseY);
        if (slot < 0) {
            return true;
        }
        ItemStack stack = mc.player.getInventory().getItem(slot);
        if (stack.isEmpty()) {
            return true;
        }

        this.applyEditorInputs();
        // shift-click writes a new line, so you can type a manifest by shift clicking down your inventory

        FreightManifestLine current = this.getSelectedLine();
        if (Screen.hasShiftDown() && current != null && current.hasSelection()
                && this.lines.size() < FreightManifestLogic.MAX_LINES) {
            FreightManifestLine added = new FreightManifestLine();
            added.setOperation(current.getOperation());
            added.setResetMode(current.getResetMode());
            added.setResetIntervalValue(current.getResetIntervalValue());
            this.lines.add(added);
            this.selectedIndex = this.lines.size() - 1;
            this.ensureSelectionVisible();
        }
        FreightManifestLine selected = this.getSelectedLine();
        ItemStack display = stack.copy();
        display.setCount(1);

        FluidStack fluid = FluidUtil.getFluidContained(display).orElse(FluidStack.EMPTY);
        if (!fluid.isEmpty()) {
            ResourceLocation fluidId = BuiltInRegistries.FLUID.getKey(fluid.getFluid());
            if (fluidId != null) {
                selected.setSelection(ManifestCargoKind.FLUID, fluidId, display);
            }
        } else {
            ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(display.getItem());
            if (itemId != null) {
                selected.setSelection(ManifestCargoKind.ITEM, itemId, display);
            }
        }
        this.playPaper(SoundEvents.UI_LOOM_SELECT_PATTERN, 1.15f);
        this.refreshCargoRows();
        this.markDirty();
        this.rebuildWidgets();
        return true;
    }

    private int pickerSlotAt(double mouseX, double mouseY) {
        int gridLeft = this.pickerGridLeft();
        int hotbarY = this.bodyB - SLOT - 4;
        int col = (int) ((mouseX - gridLeft) / SLOT);
        if (col < 0 || col > 8 || mouseX < gridLeft) {
            return -1;
        }
        if (hotbarY >= this.drawerTop + 15 && mouseY >= hotbarY && mouseY < hotbarY + 16) {
            return col;
        }

        for (int row = 0; row < 3; row++) {
            int y = hotbarY - 6 - (3 - row) * SLOT;
            if (y < this.drawerTop + 15) {
                continue;
            }
            if (mouseY >= y && mouseY < y + 16) {
                return 9 + row * 9 + col;
            }
        }
        return -1;
    }

    private boolean handleOrderClick(double mouseX, double mouseY) {
        List<DeliveryOrderLineView> rows = this.activeOrderRows();
        if (rows.isEmpty() || mouseX < this.contentL || mouseX >= this.contentR
                || mouseY < this.rowsTop + 2 || mouseY >= this.rowsBottom) {
            return false;
        }
        int i = (int) ((mouseY - (this.rowsTop + 2)) / ROW_ORDER);
        int index = this.orderScroll + i;
        if (index < 0 || index >= rows.size()) {
            return true;
        }
        DeliveryOrderLineView row = rows.get(index);
        int top = this.rowsTop + 2 + i * ROW_ORDER;
        int actionW = 46;
        int dismissW = 40;
        int dismissX = this.contentR - 8 - dismissW;
        int actionX = dismissX - 5 - actionW;
        if (mouseY < top + 7 || mouseY >= top + 23) {
            return true;
        }
        if (mouseX >= actionX && mouseX < actionX + actionW) {
            DeliveryOrderAction action = row.isComplete()
                    ? DeliveryOrderAction.RESET_CYCLE : DeliveryOrderAction.MARK_COMPLETE;
            this.playUiClick(row.isComplete() ? 0.9f : 1.1f);
            PacketDistributor.sendToServer(
                    new FreightManifestDeliveryOrderActionPacket(this.blockPos, row.getLineId(), action));
            return true;
        }
        if ((row.isComplete() || row.isDismissed()) && mouseX >= dismissX && mouseX < dismissX + dismissW) {
            this.playUiClick(row.isDismissed() ? 1.0f : 0.95f);
            PacketDistributor.sendToServer(new FreightManifestDeliveryOrderActionPacket(
                    this.blockPos, row.getLineId(), DeliveryOrderAction.TOGGLE_DISMISSED));
            return true;
        }
        return true;
    }

    private boolean handleCargoClick(double mouseX, double mouseY) {
        int tx = this.contentR - 22;
        int ty = this.rowsTop + 3;
        if (mouseX >= tx && mouseX < tx + 18 && mouseY >= ty && mouseY < ty + 13) {
            this.historyExpanded = !this.historyExpanded;
            this.clampHistoryScroll();
            this.playUiClick(this.historyExpanded ? 1.05f : 0.95f);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        this.layout();
        int step = (int) Math.signum(scrollY);
        if (step == 0) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        switch (this.currentTab) {
            case MANIFEST -> {
                int rowsY = this.listTop + 42;
                if (mouseX >= this.listL && mouseX < this.listR && mouseY >= rowsY && mouseY < this.listBottom) {
                    int max = Math.max(0, this.lines.size() - this.listRows);
                    this.lineScroll = Math.max(0, Math.min(max, this.lineScroll - step));
                    return true;
                }
            }
            case ORDERS -> {
                if (mouseY >= this.rowsTop && mouseY < this.rowsBottom
                        && mouseX >= this.contentL && mouseX < this.contentR) {
                    int visible = Math.max(1, (this.rowsBottom - this.rowsTop - 4) / ROW_ORDER);
                    int max = Math.max(0, this.activeOrderRows().size() - visible);
                    this.orderScroll = Math.max(0, Math.min(max, this.orderScroll - step));
                    return true;
                }
            }
            case CARGO -> {
                if (mouseY < this.rowsTop || mouseY >= this.rowsBottom) {
                    break;
                }
                int rowH = this.historyExpanded ? ROW_HISTORY_BIG : ROW_HISTORY;
                boolean overHistory = this.historyExpanded
                        || (mouseX >= this.historyL && mouseX < this.contentR);
                if (overHistory) {
                    int visible = Math.max(1, (this.rowsBottom - this.rowsTop - 22) / rowH);
                    int max = Math.max(0, this.deliveryBoard.getHistoryEntries().size() - visible);
                    this.historyScroll = Math.max(0, Math.min(max, this.historyScroll - step));
                    return true;
                }
                if (mouseX >= this.contentL && mouseX < this.cargoSplitX) {
                    int visible = Math.max(1, (this.rowsBottom - this.rowsTop - 22) / ROW_CARGO);
                    int max = Math.max(0, this.cargoRows.size() - visible);
                    this.cargoScroll = Math.max(0, Math.min(max, this.cargoScroll - step));
                    return true;
                }
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private void playUiClick(float pitch) {
        Minecraft.getInstance().getSoundManager()
                .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, pitch));
    }

    private void playPaper(SoundEvent event, float pitch) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(event, pitch));
    }

    private boolean hasSelection() {
        return this.selectedIndex >= 0 && this.selectedIndex < this.lines.size();
    }

    private FreightManifestLine getSelectedLine() {
        return this.hasSelection() ? this.lines.get(this.selectedIndex) : null;
    }

    private void addLine() {
        this.applyEditorInputs();
        this.lines.add(new FreightManifestLine());
        this.selectedIndex = this.lines.size() - 1;
        this.pickerOpen = true;
        this.ensureSelectionVisible();
        this.refreshCargoRows();
        this.markDirty();
        this.rebuildWidgets();
    }

    private void removeSelectedLine() {
        if (!this.hasSelection()) {
            return;
        }
        this.applyEditorInputs();
        this.lines.remove(this.selectedIndex);
        if (this.lines.isEmpty()) {
            this.selectedIndex = -1;
        } else if (this.selectedIndex >= this.lines.size()) {
            this.selectedIndex = this.lines.size() - 1;
        }
        this.ensureSelectionVisible();
        this.refreshCargoRows();
        this.markDirty();
        this.rebuildWidgets();
    }

    private void duplicateSelectedLine() {
        if (!this.hasSelection()) {
            return;
        }
        this.applyEditorInputs();
        this.lines.add(this.selectedIndex + 1, this.getSelectedLine().duplicate());
        this.selectedIndex = Math.min(this.lines.size() - 1, this.selectedIndex + 1);
        this.ensureSelectionVisible();
        this.refreshCargoRows();
        this.markDirty();
        this.rebuildWidgets();
    }

    private void syncAmountInputFromSelection() {
        if (this.amountInput == null) {
            return;
        }
        FreightManifestLine selected = this.getSelectedLine();
        this.amountInput.setValue(selected == null ? "" : Integer.toString(Math.max(1, selected.getAmount())));
    }

    private void syncIntervalInputFromSelection() {
        if (this.intervalInput == null) {
            return;
        }
        FreightManifestLine selected = this.getSelectedLine();
        if (selected == null || !selected.getResetMode().isIntervalBased()) {
            this.intervalInput.setValue("");
            return;
        }
        this.intervalInput.setValue(Integer.toString(Math.max(1, selected.getResetIntervalValue())));
    }

    private void applyEditorInputs() {
        this.applyAmountInput();
        this.applyIntervalInput();
    }

    private void applyAmountInput() {
        FreightManifestLine selected = this.getSelectedLine();
        if (selected == null || this.amountInput == null) {
            return;
        }
        try {
            int amount = Math.max(1, Integer.parseInt(this.amountInput.getValue()));
            if (selected.getAmount() != amount) {
                selected.setAmount(amount);
                this.refreshCargoRows();
                this.markDirty();
            }
        } catch (NumberFormatException ignored) {
        }
    }

    private void applyIntervalInput() {
        FreightManifestLine selected = this.getSelectedLine();
        if (selected == null || this.intervalInput == null || !selected.getResetMode().isIntervalBased()) {
            return;
        }
        try {
            int interval = Math.max(1, Integer.parseInt(this.intervalInput.getValue()));
            if (selected.getResetIntervalValue() != interval) {
                selected.setResetIntervalValue(interval);
                this.markDirty();
            }
        } catch (NumberFormatException ignored) {
        }
    }

    private void onAmountInputChanged() {
        FreightManifestLine selected = this.getSelectedLine();
        if (selected == null || this.amountInput == null || this.amountInput.getValue().isEmpty()) {
            return;
        }
        try {
            int amount = Math.max(1, Integer.parseInt(this.amountInput.getValue()));
            if (selected.getAmount() != amount) {
                selected.setAmount(amount);
                this.refreshCargoRows();
                this.markDirty();
            }
        } catch (NumberFormatException ignored) {
        }
    }

    private void onIntervalInputChanged() {
        FreightManifestLine selected = this.getSelectedLine();
        if (selected == null || this.intervalInput == null
                || !selected.getResetMode().isIntervalBased() || this.intervalInput.getValue().isEmpty()) {
            return;
        }
        try {
            int interval = Math.max(1, Integer.parseInt(this.intervalInput.getValue()));
            if (selected.getResetIntervalValue() != interval) {
                selected.setResetIntervalValue(interval);
                this.markDirty();
            }
        } catch (NumberFormatException ignored) {
        }
    }

    private void triggerSave() {
        this.applyEditorInputs();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        if (!this.stationLink.isLinked()) {
            mc.player.displayClientMessage(
                    Component.literal("Link the clipboard to a station before saving.")
                            .withStyle(ChatFormatting.RED), false);
            return;
        }
        FreightManifestValidationResult validation = this.getManifestValidation();
        if (!validation.isValid()) {
            mc.player.displayClientMessage(
                    Component.literal(validation.getMessage()).withStyle(ChatFormatting.RED), false);
            return;
        }
        if (!this.draftState.beginSave()) {
            return;
        }
        PacketDistributor.sendToServer(
                new SaveFreightManifestPacket(this.blockPos, this.draftState.version(), this.lines));
        this.confirmClose = false;
        this.rebuildWidgets();
    }

    @Override
    public void onClose() {
        if ((this.draftState.dirty() || this.draftState.pending()) && this.canEdit && !this.confirmClose) {
            this.confirmClose = true;
            this.rebuildWidgets();
            return;
        }
        super.onClose();
    }

    private void markDirty() {
        this.draftState.edit();
        this.saveFlash = 0f;
        this.confirmClose = false;
    }

    private void ensureSelectionVisible() {
        if (!this.hasSelection()) {
            this.lineScroll = 0;
            return;
        }
        if (this.selectedIndex < this.lineScroll) {
            this.lineScroll = this.selectedIndex;
        } else if (this.selectedIndex >= this.lineScroll + this.listRows) {
            this.lineScroll = this.selectedIndex - this.listRows + 1;
        }
    }

    public boolean isForClipboard(BlockPos pos) {
        return this.blockPos.equals(pos);
    }

    public void updateLiveState(FreightManifest manifest, FreightStationCargoView cargoView,
            DeliveryOrderBoardView deliveryBoard, boolean saveReply, boolean saveAccepted) {
        boolean wasPending = this.draftState.pending();
        if (this.draftState.receive(manifest.getVersion(), saveReply, saveAccepted)) {
            this.manifest.setLines(manifest.getLines());
            this.manifest.setOwnerId(manifest.getOwnerId());
            this.manifest.setVersion(manifest.getVersion());
            this.lines.clear();
            for (FreightManifestLine line : manifest.getLines()) {
                this.lines.add(line.copy());
            }
            this.selectedIndex = this.lines.isEmpty() ? -1
                : Math.max(0, Math.min(this.selectedIndex, this.lines.size() - 1));

            this.rebuildWidgets();
        }
        if (saveReply && saveAccepted && wasPending) {
            this.saveFlash = this.draftState.dirty() ? 0f : 1.0f;
            this.playPaper(SoundEvents.UI_LOOM_TAKE_RESULT, 1.0f);
        }
        this.cargoView = cargoView.copy();
        this.deliveryBoard = deliveryBoard;
        this.sampleRates();
        this.refreshCargoRows();
        if (this.deliveryBoard.getHistoryEntries().isEmpty()) {
            this.historyExpanded = false;
        }
        this.orderScroll = Math.max(0, Math.min(
                Math.max(0, this.activeOrderRows().size() - 1), this.orderScroll));
        this.clampHistoryScroll();
    }

    // sample per packet, not per frame. fake intervals give rates that change with fps
    private void sampleRates() {
        long tick = this.deliveryBoard.getCurrentTick();
        Set<UUID> live = new java.util.HashSet<>();
        for (DeliveryOrderLineView row : this.deliveryBoard.getLineViews()) {
            live.add(row.getLineId());
            this.rates.computeIfAbsent(row.getLineId(), id -> new UiFx.RateTracker())
                    .sample(tick, row.getDeliveredAmount());
        }
        this.rates.keySet().retainAll(live);
    }

    private void clampHistoryScroll() {
        int max = Math.max(0, this.deliveryBoard.getHistoryEntries().size() - 1);
        this.historyScroll = Math.max(0, Math.min(max, this.historyScroll));
    }

    private boolean hasCompletedOrders() {
        for (DeliveryOrderLineView row : this.deliveryBoard.getLineViews()) {
            if (row.isComplete()) {
                return true;
            }
        }
        return false;
    }

    private boolean hasCompletedVisibleOrders() {
        for (DeliveryOrderLineView row : this.deliveryBoard.getLineViews()) {
            if (row.isComplete() && !row.isDismissed()) {
                return true;
            }
        }
        return false;
    }

    private boolean hasHiddenOrders() {
        for (DeliveryOrderLineView row : this.deliveryBoard.getLineViews()) {
            if (row.isDismissed()
                    && !(row.isComplete() && row.getResetMode() == DeliveryOrderResetMode.ONCE)) {
                return true;
            }
        }
        return false;
    }

    private List<DeliveryOrderLineView> activeOrderRows() {
        List<DeliveryOrderLineView> active = new ArrayList<>();
        for (DeliveryOrderLineView row : this.deliveryBoard.getLineViews()) {
            if (!row.isDismissed()) {
                active.add(row);
            }
        }
        return active;
    }

    private boolean isNextActiveOrder(DeliveryOrderLineView candidate) {
        for (DeliveryOrderLineView row : this.deliveryBoard.getLineViews()) {
            if (!row.isDismissed() && !row.isComplete()) {
                return row.getLineId().equals(candidate.getLineId());
            }
        }
        return false;
    }

    private List<CargoRow> buildCargoRows(FreightStationCargoView view) {
        List<CargoRow> rows = new ArrayList<>();
        Map<FreightManifestResourceKey, Integer> expectedToStation = this.buildExpectedAmounts(ManifestOperation.UNLOAD);
        Map<FreightManifestResourceKey, Integer> expectedToTrain = this.buildExpectedAmounts(ManifestOperation.LOAD);
        List<FreightManifestResourceKey> keys = new ArrayList<>(view.getFreightTotals().getAmounts().keySet());
        for (FreightManifestResourceKey key : view.getMovedToStation().getAmounts().keySet()) {
            if (!keys.contains(key)) {
                keys.add(key);
            }
        }
        for (FreightManifestResourceKey key : view.getMovedToTrain().getAmounts().keySet()) {
            if (!keys.contains(key)) {
                keys.add(key);
            }
        }
        for (FreightManifestResourceKey key : expectedToStation.keySet()) {
            if (!keys.contains(key)) {
                keys.add(key);
            }
        }
        for (FreightManifestResourceKey key : expectedToTrain.keySet()) {
            if (!keys.contains(key)) {
                keys.add(key);
            }
        }
        for (FreightManifestResourceKey key : keys) {
            rows.add(CargoRow.fromSnapshots(key,
                    view.getFreightTotals().getAmount(key),
                    view.getMovedToStation().getAmount(key),
                    view.getMovedToTrain().getAmount(key),
                    expectedToStation.getOrDefault(key, 0),
                    expectedToTrain.getOrDefault(key, 0)));
        }
        rows.sort(Comparator.comparing((CargoRow row) -> !row.hasManifestGoal())
                .thenComparing(row -> row.kind.ordinal())
                .thenComparing(row -> row.name));
        return rows;
    }

    private void refreshCargoRows() {
        this.cargoRows.clear();
        this.cargoRows.addAll(this.buildCargoRows(this.cargoView));
        this.cargoScroll = Math.max(0, Math.min(Math.max(0, this.cargoRows.size() - 1), this.cargoScroll));
    }

    private FreightManifest buildWorkingManifest() {
        FreightManifest working = new FreightManifest();
        working.setLines(this.lines);
        working.setVersion(this.manifest.getVersion());
        return working;
    }

    private Map<FreightManifestResourceKey, Integer> buildExpectedAmounts(ManifestOperation operation) {
        return FreightManifestLogic.getExpectedAmounts(this.buildWorkingManifest(), operation);
    }

    private FreightManifestValidationResult getManifestValidation() {
        return FreightManifestLogic.validate(this.lines);
    }

    private Set<UUID> getDuplicateLineIds() {
        return new LinkedHashSet<>(FreightManifestLogic.findDuplicateLineIds(this.lines));
    }

    private int getConfiguredLineCount() {
        int count = 0;
        for (FreightManifestLine line : this.lines) {
            if (this.isLineConfigured(line)) {
                count++;
            }
        }
        return count;
    }

    private String getManifestSummaryText() {
        FreightManifestValidationResult validation = this.getManifestValidation();
        if (!this.stationLink.isLinked()) {
            return "Link this clipboard to a station to manage cargo.";
        }
        if (this.lines.isEmpty()) {
            return "No manifest lines yet. Add a line to start the checklist.";
        }
        if (!validation.isValid()) {
            return validation.getMessage();
        }
        Set<UUID> duplicates = this.getDuplicateLineIds();
        if (!duplicates.isEmpty()) {
            return duplicates.size() + " lines share the same cargo and direction.";
        }
        return this.getConfiguredLineCount() + "/" + this.lines.size() + " lines ready to apply";
    }

    private int getManifestSummaryColor() {
        if (!this.stationLink.isLinked() || this.lines.isEmpty()) {
            return UiFx.INK_DIM;
        }
        if (!this.getManifestValidation().isValid()) {
            return UiFx.BAD;
        }
        return this.getDuplicateLineIds().isEmpty() ? UiFx.INK_DIM : UiFx.WARN;
    }

    private boolean isLineConfigured(FreightManifestLine line) {
        return line != null && line.hasSelection() && line.getAmount() > 0
                && (!line.getResetMode().isIntervalBased() || line.getResetIntervalValue() > 0);
    }

    private String getSelectedLineStatusText(FreightManifestLine line) {
        if (line == null) {
            return "No line";
        }
        if (!line.hasSelection()) {
            return "No cargo";
        }
        if (line.getAmount() <= 0) {
            return "No amount";
        }
        if (line.getResetMode().isIntervalBased() && line.getResetIntervalValue() <= 0) {
            return "No interval";
        }
        if (this.getDuplicateLineIds().contains(line.getLineId())) {
            return "Duplicate";
        }
        return "Ready";
    }

    private int getSelectedLineStatusColor(FreightManifestLine line) {
        if (line == null) {
            return UiFx.INK_FAINT;
        }
        if (this.getDuplicateLineIds().contains(line.getLineId())) {
            return UiFx.WARN;
        }
        return this.isLineConfigured(line) ? UiFx.OK : UiFx.BAD;
    }

    private FreightManifestEvaluationResult getPreviewEvaluation() {
        FreightManifestValidationResult validation = this.getManifestValidation();
        if (!validation.isValid()) {
            return FreightManifestEvaluationResult.waiting(validation.getMessage());
        }
        return FreightManifestLogic.previewEvaluation(this.buildWorkingManifest(), this.cargoView);
    }

    private int getPreviewStatusColor(FreightManifestEvaluationResult evaluation) {
        if (!this.getManifestValidation().isValid()) {
            return UiFx.BAD;
        }
        if (this.lines.isEmpty()) {
            return UiFx.INK_FAINT;
        }
        if (evaluation.isComplete()) {
            return UiFx.OK;
        }
        return this.cargoView.isTrainPresent() ? UiFx.WARN : UiFx.INK_FAINT;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static final class CargoRow {
        private final ManifestCargoKind kind;
        private final String name;
        private final int freightAmount;
        private final int toStationAmount;
        private final int toTrainAmount;
        private final int expectedToStation;
        private final int expectedToTrain;
        private final ItemStack icon;

        private CargoRow(ManifestCargoKind kind, String name, int freightAmount, int toStationAmount,
                         int toTrainAmount, int expectedToStation, int expectedToTrain, ItemStack icon) {
            this.kind = kind;
            this.name = name;
            this.freightAmount = freightAmount;
            this.toStationAmount = toStationAmount;
            this.toTrainAmount = toTrainAmount;
            this.expectedToStation = expectedToStation;
            this.expectedToTrain = expectedToTrain;
            this.icon = icon;
        }

        private String amountText(int amount) {
            return FreightManifestLogic.formatAmount(this.kind, amount);
        }

        private boolean hasManifestGoal() {
            return this.expectedToStation > 0 || this.expectedToTrain > 0;
        }

        private static CargoRow fromSnapshots(FreightManifestResourceKey key, int freightAmount, int toStationAmount,
                                              int toTrainAmount, int expectedToStation, int expectedToTrain) {
            if (key.getCargoKind() == ManifestCargoKind.ITEM) {
                var item = BuiltInRegistries.ITEM.getOptional(key.getResourceId()).orElse(null);
                ItemStack icon = item == null ? ItemStack.EMPTY : new ItemStack(item);
                String name = icon.isEmpty() ? key.getResourceId().toString() : icon.getHoverName().getString();
                return new CargoRow(ManifestCargoKind.ITEM, name, freightAmount, toStationAmount, toTrainAmount,
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
            return new CargoRow(ManifestCargoKind.FLUID, name, freightAmount, toStationAmount, toTrainAmount,
                    expectedToStation, expectedToTrain, icon);
        }
    }
}
