package com.createstats.gui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;

public class FreightManifestClipboardScreen extends Screen {

    private static Tab LAST_OPENED_TAB = Tab.MANIFEST;

    private enum Tab {
        MANIFEST("Manifest"),
        DELIVERY_ORDERS("Orders"),
        TOTAL_CARGO("Cargo");

        private final String label;

        Tab(String label) {
            this.label = label;
        }

        public String getLabel() {
            return this.label;
        }
    }

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
    private static final int PAPER_RULE = 0x18A88F66;
    private static final int TAB_WIDTH = 72;
    private static final int TAB_HEIGHT = 24;
    private static final int MANIFEST_ROW_HEIGHT = 20;
    private static final int VISIBLE_MANIFEST_ROWS = 8;
    private static final int ORDER_ROW_HEIGHT = 28;
    private static final int VISIBLE_ORDER_ROWS = 9;
    private static final int CARGO_ROW_HEIGHT = 20;
    private static final int VISIBLE_CARGO_ROWS = 10;
    private static final int EXPANDED_HISTORY_ROW_HEIGHT = 38;
    private static final int VISIBLE_EXPANDED_HISTORY_ROWS = 6;

    private final BlockPos blockPos;
    private final FreightStationLink stationLink;
    private final FreightManifest manifest;
    private final boolean canEdit;
    private final List<FreightManifestLine> lines;
    private final List<CargoRow> cargoRows;
    private FreightStationCargoView cargoView;
    private DeliveryOrderBoardView deliveryBoard;

    private int panelLeft;
    private int panelTop;
    private int panelWidth;
    private int panelHeight;
    private int selectedIndex;
    private int lineScroll;
    private int orderScroll;
    private int cargoScroll;
    private int historyScroll;
    private boolean historyExpanded;
    private boolean dirty;
    private boolean confirmClose;
    private EditBox amountInput;
    private EditBox intervalInput;
    private Tab currentTab;
    private int cargoRefreshCooldown;

    // Animation state (frame-rate independent smoothing)
    private float frameTime;
    private float frameDt;
    private float lastFrameTime = Float.NaN;
    private float refreshFlash;
    private int dataSignature = Integer.MIN_VALUE;
    private final Map<UUID, Float> orderProgressAnim = new HashMap<>();

    public FreightManifestClipboardScreen(BlockPos blockPos, FreightStationLink stationLink, FreightManifest manifest,
                                          FreightStationCargoView cargoView, DeliveryOrderBoardView deliveryBoard,
                                          boolean canEdit) {
        super(Component.literal("Freight Manifest Clipboard"));
        this.blockPos = blockPos;
        this.stationLink = stationLink.copy();
        this.manifest = manifest.copy();
        this.canEdit = canEdit;
        this.lines = new ArrayList<>();
        for (FreightManifestLine line : manifest.getLines()) {
            this.lines.add(line.copy());
        }
        this.cargoRows = new ArrayList<>();
        this.cargoView = FreightStationCargoView.unavailable("Waiting for cargo data...");
        this.deliveryBoard = deliveryBoard == null ? DeliveryOrderBoardView.empty(0L, "Waiting for delivery data...") : deliveryBoard;
        this.selectedIndex = this.lines.isEmpty() ? -1 : 0;
        this.lineScroll = 0;
        this.orderScroll = 0;
        this.cargoScroll = 0;
        this.historyScroll = 0;
        this.historyExpanded = false;
        this.dirty = false;
        this.confirmClose = false;
        this.currentTab = LAST_OPENED_TAB;
        this.cargoRefreshCooldown = 0;
        this.updateLiveState(cargoView, this.deliveryBoard);
    }

    @Override
    protected void init() {
        super.init();
        this.clearWidgets();
        this.panelWidth = Math.min(this.width - 40, 632);
        this.panelHeight = Math.min(this.height - 40, 461);
        this.panelLeft = (this.width - this.panelWidth) / 2;
        this.panelTop = (this.height - this.panelHeight) / 2;

        this.addTabButtons();
        switch (this.currentTab) {
            case MANIFEST -> this.initManifestWidgets();
            case DELIVERY_ORDERS -> this.initDeliveryWidgets();
            case TOTAL_CARGO -> this.initCargoWidgets();
        }
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

    private void addTabButtons() {
        int tabX = this.panelLeft - TAB_WIDTH + 6;
        int tabY = this.panelTop + 56;
        for (int i = 0; i < Tab.values().length; i++) {
            Tab tab = Tab.values()[i];
            Button tabButton = Button.builder(Component.literal(tab.getLabel()), b -> {
                this.applyEditorInputs();
                this.currentTab = tab;
                LAST_OPENED_TAB = tab;
                this.rebuildWidgets();
            }).bounds(tabX, tabY + i * (TAB_HEIGHT + 4), TAB_WIDTH, TAB_HEIGHT).build();
            tabButton.active = this.currentTab != tab;
            this.addRenderableWidget(tabButton);
        }
    }

    private void initManifestWidgets() {
        int controlsY = this.getManifestControlsY();
        int editorLeft = this.getManifestEditorLeft();
        int editorTop = this.getManifestTop();
        int editorRight = this.panelLeft + this.panelWidth - 18;

        Button addLineButton = Button.builder(Component.literal("+ Line"), b -> this.addLine())
            .bounds(this.panelLeft + 18, controlsY, 56, 20).build();
        addLineButton.active = this.canEdit && this.lines.size() < FreightManifestLogic.MAX_LINES;
        this.addRenderableWidget(addLineButton);

        Button removeLineButton = Button.builder(Component.literal("- Line"), b -> this.removeSelectedLine())
            .bounds(this.panelLeft + 80, controlsY, 58, 20).build();
        removeLineButton.active = this.canEdit && this.hasSelection();
        this.addRenderableWidget(removeLineButton);

        Button duplicateLineButton = Button.builder(Component.literal("Copy"), b -> this.duplicateSelectedLine())
            .bounds(this.panelLeft + 144, controlsY, 60, 20).build();
        duplicateLineButton.active = this.canEdit && this.hasSelection() && this.lines.size() < FreightManifestLogic.MAX_LINES;
        this.addRenderableWidget(duplicateLineButton);

        Button operationButton = Button.builder(Component.literal(this.getOperationButtonLabel()), b -> {
            FreightManifestLine selected = this.getSelectedLine();
            if (selected != null) {
                selected.setOperation(selected.getOperation().cycle());
                this.refreshCargoRows();
                this.markDirty();
                this.rebuildWidgets();
            }
        }).bounds(editorLeft + 70, editorTop + 72, 92, 20).build();
        operationButton.active = this.canEdit && this.hasSelection();
        this.addRenderableWidget(operationButton);

        Button clearButton = Button.builder(Component.literal("Clear"), b -> {
            FreightManifestLine selected = this.getSelectedLine();
            if (selected != null) {
                selected.clearSelection();
                this.refreshCargoRows();
                this.markDirty();
                this.rebuildWidgets();
            }
        }).bounds(editorRight - 66, editorTop + 72, 58, 20).build();
        clearButton.active = this.canEdit && this.hasSelection();
        this.addRenderableWidget(clearButton);

        Button resetModeButton = Button.builder(Component.literal(this.getResetModeButtonLabel()), b -> {
            FreightManifestLine selected = this.getSelectedLine();
            if (selected != null) {
                selected.setResetMode(selected.getResetMode().cycle());
                if (selected.getResetMode().isIntervalBased() && selected.getResetIntervalValue() <= 0) {
                    selected.setResetIntervalValue(1);
                }
                this.markDirty();
                this.rebuildWidgets();
            }
        }).bounds(editorLeft + 70, editorTop + 96, 118, 20).build();
        resetModeButton.active = this.canEdit && this.hasSelection();
        this.addRenderableWidget(resetModeButton);

        this.intervalInput = new EditBox(this.font, editorLeft + 196, editorTop + 97, 40, 18, Component.literal("Interval"));
        this.intervalInput.setFilter(text -> text.isEmpty() || text.matches("[0-9]+"));
        this.intervalInput.setMaxLength(4);
        this.intervalInput.setEditable(this.canEdit && this.hasSelection() && this.getSelectedLine() != null
            && this.getSelectedLine().getResetMode().isIntervalBased());
        this.intervalInput.setResponder(text -> this.onIntervalInputChanged());
        this.syncIntervalInputFromSelection();
        this.addRenderableWidget(this.intervalInput);

        this.amountInput = new EditBox(this.font, editorLeft + 70, editorTop + 120, 134, 18, Component.literal("Amount"));
        this.amountInput.setFilter(text -> text.isEmpty() || text.matches("[0-9]+"));
        this.amountInput.setMaxLength(9);
        this.amountInput.setEditable(this.canEdit && this.hasSelection());
        this.amountInput.setResponder(text -> this.onAmountInputChanged());
        this.syncAmountInputFromSelection();
        this.addRenderableWidget(this.amountInput);

        Button applyButton = Button.builder(Component.literal("Apply"), b -> this.triggerSave())
            .bounds(this.panelLeft + this.panelWidth - 184, this.panelTop + this.panelHeight - 26, 80, 20).build();
        applyButton.active = this.canEdit && this.stationLink.isLinked() && this.getManifestValidation().isValid();
        this.addRenderableWidget(applyButton);

        this.addRenderableWidget(Button.builder(Component.literal(this.getCloseButtonLabel()), b -> this.onClose())
            .bounds(this.panelLeft + this.panelWidth - 94, this.panelTop + this.panelHeight - 26, 80, 20).build());
    }

    private void initDeliveryWidgets() {
        Button hideDoneButton = Button.builder(Component.literal("Hide Done"), b ->
            PacketDistributor.sendToServer(new FreightManifestDeliveryOrderActionPacket(this.blockPos, null, DeliveryOrderAction.HIDE_COMPLETED)))
            .bounds(this.panelLeft + 18, this.panelTop + this.panelHeight - 26, 88, 20).build();
        hideDoneButton.active = this.hasCompletedVisibleOrders();
        this.addRenderableWidget(hideDoneButton);

        Button showHiddenButton = Button.builder(Component.literal("Show Hidden"), b ->
            PacketDistributor.sendToServer(new FreightManifestDeliveryOrderActionPacket(this.blockPos, null, DeliveryOrderAction.SHOW_HIDDEN)))
            .bounds(this.panelLeft + 112, this.panelTop + this.panelHeight - 26, 96, 20).build();
        showHiddenButton.active = this.hasHiddenOrders();
        this.addRenderableWidget(showHiddenButton);

        Button resetDoneButton = Button.builder(Component.literal("Reset Done"), b ->
            PacketDistributor.sendToServer(new FreightManifestDeliveryOrderActionPacket(this.blockPos, null, DeliveryOrderAction.RESET_COMPLETED)))
            .bounds(this.panelLeft + 214, this.panelTop + this.panelHeight - 26, 92, 20).build();
        resetDoneButton.active = this.hasCompletedOrders();
        this.addRenderableWidget(resetDoneButton);

        this.addRenderableWidget(Button.builder(Component.literal(this.getCloseButtonLabel()), b -> this.onClose())
            .bounds(this.panelLeft + this.panelWidth - 94, this.panelTop + this.panelHeight - 26, 80, 20).build());
    }

    private void initCargoWidgets() {
        this.addRenderableWidget(Button.builder(Component.literal(this.getCloseButtonLabel()), b -> this.onClose())
            .bounds(this.panelLeft + this.panelWidth - 94, this.panelTop + this.panelHeight - 26, 80, 20).build());
    }

    private int getManifestTop() {
        return this.panelTop + 88;
    }

    private int getManifestListLeft() {
        return this.panelLeft + 18;
    }

    private int getManifestListRight() {
        int extraWidth = Math.max(0, this.panelWidth - 486);
        return this.getManifestListLeft() + 188 + extraWidth / 4;
    }

    private int getManifestEditorLeft() {
        return this.getManifestListRight() + 10;
    }

    private int getManifestControlsY() {
        return this.getManifestTop() + VISIBLE_MANIFEST_ROWS * MANIFEST_ROW_HEIGHT + 10;
    }

    private int getInventoryTop() {
        return this.panelTop + this.panelHeight - 124;
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
            if (row.isDismissed()) {
                return true;
            }
        }
        return false;
    }

    private void playUiClick(float pitch) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, pitch));
    }

    private boolean hasSelection() {
        return this.selectedIndex >= 0 && this.selectedIndex < this.lines.size();
    }

    private FreightManifestLine getSelectedLine() {
        return this.hasSelection() ? this.lines.get(this.selectedIndex) : null;
    }

    private String getOperationButtonLabel() {
        FreightManifestLine selected = this.getSelectedLine();
        return selected == null ? "Operation" : selected.getOperation().getDisplayName();
    }

    private String getResetModeButtonLabel() {
        FreightManifestLine selected = this.getSelectedLine();
        return selected == null ? "Reset Mode" : selected.getResetMode().getBadgeLabel(selected.getResetIntervalValue());
    }

    private void addLine() {
        this.applyEditorInputs();
        this.lines.add(new FreightManifestLine());
        this.selectedIndex = this.lines.size() - 1;
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
        if (selected == null || this.amountInput == null) {
            return;
        }
        String value = this.amountInput.getValue();
        if (value.isEmpty()) {
            return;
        }
        try {
            int amount = Math.max(1, Integer.parseInt(value));
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
        if (selected == null || this.intervalInput == null || !selected.getResetMode().isIntervalBased()) {
            return;
        }
        String value = this.intervalInput.getValue();
        if (value.isEmpty()) {
            return;
        }
        try {
            int interval = Math.max(1, Integer.parseInt(value));
            if (selected.getResetIntervalValue() != interval) {
                selected.setResetIntervalValue(interval);
                this.markDirty();
            }
        } catch (NumberFormatException ignored) {
        }
    }

    private void triggerSave() {
        this.applyEditorInputs();
        if (Minecraft.getInstance().player == null) {
            return;
        }
        if (!this.stationLink.isLinked()) {
            Minecraft.getInstance().player.displayClientMessage(
                Component.literal("Link the clipboard to a station before saving.").withStyle(ChatFormatting.RED), false);
            return;
        }
        FreightManifestValidationResult validation = this.getManifestValidation();
        if (!validation.isValid()) {
            Minecraft.getInstance().player.displayClientMessage(
                Component.literal(validation.getMessage()).withStyle(ChatFormatting.RED), false);
            return;
        }
        PacketDistributor.sendToServer(new SaveFreightManifestPacket(this.blockPos, this.manifest.getVersion(), this.lines));
        this.dirty = false;
        this.confirmClose = false;
        this.rebuildWidgets();
    }

    @Override
    public void onClose() {
        if (this.dirty && this.canEdit) {
            if (this.confirmClose) {
                // Player confirmed: discard unsaved changes and close
                super.onClose();
                return;
            }
            // First close attempt with unsaved changes, warn and require a second press
            this.confirmClose = true;
            if (Minecraft.getInstance().player != null) {
                Minecraft.getInstance().player.displayClientMessage(
                    Component.literal("You have unsaved changes. Use Apply to save, or press Escape/Close again to discard and exit.")
                        .withStyle(ChatFormatting.YELLOW), false);
            }
            this.rebuildWidgets();
        } else {
            super.onClose();
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.frameTime = this.animationTime(partialTick);
        this.frameDt = Float.isNaN(this.lastFrameTime) ? 0f : Math.max(0f, Math.min(4f, this.frameTime - this.lastFrameTime));
        this.lastFrameTime = this.frameTime;
        this.updateAnimations(this.frameDt);

        this.renderBackdrop(g, this.frameTime);
        this.renderPaperFrame(g);
        this.drawPaperCenteredText(g, "Freight Manifest Clipboard", this.width / 2, this.panelTop + 10, ACCENT);
        this.drawPaperCenteredText(g, "station cargo worksheet", this.width / 2, this.panelTop + 22, DIM);
        // Live data indicator dot near the title.
        int liveBase = this.stationLink.isLinked() ? GREEN : DIM;
        int liveColor = DispatchUiFx.lerpColor(liveBase, 0xFFFFFFFF, this.refreshFlash * 0.7f);
        DispatchUiFx.pulseDot(g, this.panelLeft + 16, this.panelTop + 15, 2, liveColor, this.frameTime, this.stationLink.isLinked() ? 0.30f : 0.12f);
        if (!this.canEdit) {
            this.drawPaperText(g, "Read only", this.panelLeft + this.panelWidth - 64, this.panelTop + 9, DIM);
        }

        this.renderStationInfo(g);
        if (this.dirty) {
            this.renderStatusChip(g, this.panelLeft + 14, this.panelTop + this.panelHeight - 40, "Unsaved changes", ACCENT);
        }
        switch (this.currentTab) {
            case MANIFEST -> this.renderManifestTab(g, mouseX, mouseY);
            case DELIVERY_ORDERS -> this.renderDeliveryOrdersTab(g, mouseX, mouseY);
            case TOTAL_CARGO -> this.renderCargoTab(g, mouseX, mouseY);
        }

        super.render(g, mouseX, mouseY, partialTick);
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

        for (DeliveryOrderLineView row : this.deliveryBoard.getLineViews()) {
            float target = row.getTargetAmount() > 0
                    ? DispatchUiFx.clamp01(row.getDeliveredAmount() / (float) row.getTargetAmount())
                    : 0f;
            float cur = this.orderProgressAnim.getOrDefault(row.getLineId(), target);
            this.orderProgressAnim.put(row.getLineId(), DispatchUiFx.approach(cur, target, dt, 0.22f));
        }
        if (!this.orderProgressAnim.isEmpty()) {
            Set<UUID> live = new java.util.HashSet<>();
            for (DeliveryOrderLineView row : this.deliveryBoard.getLineViews()) {
                live.add(row.getLineId());
            }
            this.orderProgressAnim.keySet().retainAll(live);
        }
    }

    private int computeDataSignature() {
        int sig = this.deliveryBoard.getTotalOrders();
        sig = sig * 31 + this.deliveryBoard.getCompletedOrders();
        sig = sig * 31 + (this.deliveryBoard.isTrainPresent() ? 1 : 0);
        long delivered = 0L;
        for (DeliveryOrderLineView row : this.deliveryBoard.getLineViews()) {
            delivered += row.getDeliveredAmount();
        }
        sig = sig * 31 + (int) (delivered & 0x7FFFFFFF);
        return sig;
    }

    private void renderBackdrop(GuiGraphics g, float time) {
        g.fillGradient(0, 0, this.width, this.height, 0xE6121016, 0xF21A1310);
        int spacing = 30;
        int drift = (int) (time * 0.35f) % spacing;
        for (int x = -drift; x < this.width; x += spacing) {
            g.fill(x, 0, x + 1, this.height, 0x14C9A24A);
        }
        for (int y = drift - spacing; y < this.height; y += spacing) {
            g.fill(0, y, this.width, y + 1, 0x0AC9A24A);
        }
        g.fill(this.panelLeft + 6, this.panelTop + 8, this.panelLeft + this.panelWidth + 6, this.panelTop + this.panelHeight + 8, 0x66000000);
    }

    private void renderStationInfo(GuiGraphics g) {
        int x1 = this.panelLeft + 14;
        int y1 = this.panelTop + 26;
        int x2 = this.panelLeft + this.panelWidth - 14;
        int y2 = y1 + 48;
        this.renderPaperSection(g, x1, y1, x2, y2);
        this.drawPaperText(g, "Station: " + (this.stationLink.isLinked() ? this.stationLink.getStationName() : "Unlinked"), x1 + 8, y1 + 8, TEXT);
        String dimText = this.stationLink.getStationDimension() == null ? "unknown" : this.stationLink.getStationDimension().toString();
        this.drawPaperText(g, this.trimToWidth("Dimension: " + dimText, x2 - x1 - 16), x1 + 8, y1 + 20, DIM);
        this.drawPaperText(g, this.trimToWidth(this.getManifestSummaryText(), x2 - x1 - 16), x1 + 8, y1 + 32, this.getManifestSummaryColor());
    }

    private void renderManifestTab(GuiGraphics g, int mouseX, int mouseY) {
        this.renderLineList(g, mouseX, mouseY);
        this.renderEditor(g);
        this.renderInventory(g, mouseX, mouseY);
    }

    private void renderLineList(GuiGraphics g, int mouseX, int mouseY) {
        int listLeft = this.getManifestListLeft();
        int listTop = this.getManifestTop();
        int listRight = this.getManifestListRight();
        int listBottom = listTop + VISIBLE_MANIFEST_ROWS * MANIFEST_ROW_HEIGHT;
        this.renderPaperSection(g, listLeft, listTop, listRight, listBottom);
        this.drawPaperText(g, "Manifest Lines", listLeft + 4, listTop - 10, TEXT);
        String readySummary = this.getConfiguredLineCount() + "/" + this.lines.size() + " ready";
        this.drawPaperText(g, readySummary, listRight - this.font.width(readySummary) - 4, listTop - 10, this.getManifestSummaryColor());

        Set<java.util.UUID> duplicateIds = this.getDuplicateLineIds();
        for (int row = 0; row < VISIBLE_MANIFEST_ROWS; row++) {
            int lineIndex = this.lineScroll + row;
            int rowTop = listTop + row * MANIFEST_ROW_HEIGHT;
            int rowBottom = rowTop + MANIFEST_ROW_HEIGHT - 1;
            boolean hovered = mouseX >= listLeft && mouseX < listRight && mouseY >= rowTop && mouseY < rowBottom;
            int bg = hovered ? SLOT_HOVER : 0x22000000;
            if (lineIndex == this.selectedIndex) {
                bg = SLOT_SELECTED;
            }
            g.fill(listLeft + 1, rowTop + 1, listRight - 1, rowBottom, bg);
            if (lineIndex >= this.lines.size()) {
                continue;
            }
            FreightManifestLine line = this.lines.get(lineIndex);
            boolean configured = this.isLineConfigured(line);
            boolean duplicate = duplicateIds.contains(line.getLineId());
            int markerColor = duplicate ? ACCENT : (configured ? GREEN : RED);
            int nameColor = duplicate ? ACCENT : (configured ? TEXT : DIM);
            g.fill(listLeft + 2, rowTop + 2, listLeft + 4, rowBottom - 1, markerColor);
            if (!line.getDisplayStack().isEmpty()) {
                g.renderItem(line.getDisplayStack(), listLeft + 4, rowTop + 2);
            }
            String name = line.hasSelection() ? line.getDisplayName() : "Click inventory to assign";
            this.drawPaperText(g, this.trimToWidth(name, Math.max(96, listRight - listLeft - 92)), listLeft + 24, rowTop + 3, nameColor);
            String detail = configured
                ? (line.getOperation().getDisplayName() + " | " + line.getResetMode().getBadgeLabel(line.getResetIntervalValue()))
                : (line.hasSelection() ? "Set amount" : "Pick cargo");
            int detailColor = duplicate ? ACCENT : (configured ? this.getOperationColor(line.getOperation()) : DIM);
            this.drawPaperText(g, detail, listLeft + 24, rowTop + 12, detailColor);
            String amount = line.getAmount() + (line.getCargoKind() == ManifestCargoKind.FLUID ? "mB" : "");
            this.drawPaperText(g, amount, listRight - 6 - this.font.width(amount), rowTop + 8, configured ? TEXT : DIM);
        }
    }

    private void renderEditor(GuiGraphics g) {
        int x1 = this.getManifestEditorLeft();
        int y1 = this.getManifestTop();
        int x2 = this.panelLeft + this.panelWidth - 18;
        int y2 = this.getManifestControlsY() - 12;
        this.renderPaperSection(g, x1, y1, x2, y2);
        this.drawPaperText(g, "Line Editor", x1 + 8, y1 + 8, TEXT);

        FreightManifestLine selected = this.getSelectedLine();
        if (selected == null) {
            this.drawPaperText(g, "Select a line from the list.", x1 + 8, y1 + 28, DIM);
            this.drawPaperText(g, "Then click an item or filled bucket", x1 + 8, y1 + 42, DIM);
            this.drawPaperText(g, "in your inventory to assign it.", x1 + 8, y1 + 56, DIM);
            return;
        }

        if (!selected.getDisplayStack().isEmpty()) {
            g.renderItem(selected.getDisplayStack(), x1 + 8, y1 + 26);
        }
        String resource = selected.hasSelection() ? selected.getDisplayName() : "Click inventory item to set";
        this.drawPaperText(g, this.trimToWidth("Resource: " + resource, x2 - x1 - 44), x1 + 30, y1 + 28, TEXT);
        this.drawPaperText(g, "Kind: " + selected.getCargoKind().getDisplayName(), x1 + 8, y1 + 54, DIM);
        this.drawPaperText(g, "Status: " + this.getSelectedLineStatusText(selected), x1 + 124, y1 + 54, this.getSelectedLineStatusColor(selected));
        this.drawPaperText(g, "Operation:", x1 + 8, y1 + 78, DIM);
        this.drawPaperText(g, "Reset:", x1 + 8, y1 + 102, DIM);
        this.drawPaperText(g, "Amount:", x1 + 8, y1 + 126, DIM);
        if (selected.getResetMode().isIntervalBased()) {
            String unit = selected.getResetMode() == DeliveryOrderResetMode.EVERY_X_MINUTES ? "min" : "hour";
            this.drawPaperText(g, unit, x1 + 242, y1 + 104, DIM);
        }
        this.drawPaperText(g, selected.getCargoKind().getUnitLabel(), x2 - 36, y1 + 128, DIM);
    }

    private void renderInventory(GuiGraphics g, int mouseX, int mouseY) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        Inventory inventory = minecraft.player.getInventory();
        int left = this.panelLeft + 18;
        int top = this.getInventoryTop();
        this.drawPaperText(g, "Inventory Selection", left, top - 10, TEXT);
        this.drawPaperText(g, "Click an item or filled bucket to assign the selected line", left + 100, top - 10, DIM);

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int slot = 9 + row * 9 + col;
                this.renderInventorySlot(g, inventory.getItem(slot), left + col * 18, top + row * 18, mouseX, mouseY);
            }
        }
        int hotbarTop = top + 58;
        for (int col = 0; col < 9; col++) {
            this.renderInventorySlot(g, inventory.getItem(col), left + col * 18, hotbarTop, mouseX, mouseY);
        }
    }

    private void renderInventorySlot(GuiGraphics g, ItemStack stack, int x, int y, int mouseX, int mouseY) {
        boolean hovered = mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16;
        g.fill(x - 1, y - 1, x + 17, y + 17, hovered ? SLOT_HOVER : SLOT_BG);
        g.fill(x - 1, y - 1, x + 17, y, BORDER);
        g.fill(x - 1, y + 16, x + 17, y + 17, BORDER);
        g.fill(x - 1, y - 1, x, y + 17, BORDER);
        g.fill(x + 16, y - 1, x + 17, y + 17, BORDER);
        if (!stack.isEmpty()) {
            g.renderItem(stack, x, y);
            g.renderItemDecorations(this.font, stack, x, y);
        }
    }

    private void renderDeliveryOrdersTab(GuiGraphics g, int mouseX, int mouseY) {
        int summaryLeft = this.panelLeft + 18;
        int summaryTop = this.panelTop + 88;
        int summaryRight = this.panelLeft + this.panelWidth - 18;
        int summaryBottom = summaryTop + 52;
        this.renderPaperSection(g, summaryLeft, summaryTop, summaryRight, summaryBottom);
        this.drawPaperText(g, "Live Delivery Orders", summaryLeft + 8, summaryTop + 8, TEXT);
        if (this.deliveryBoard.isTrainPresent()) {
            this.drawPaperText(g, "Current Train: " + this.deliveryBoard.getTrainName(), summaryLeft + 8, summaryTop + 22, DIM);
        } else {
            this.drawPaperText(g, "Current Train: none", summaryLeft + 8, summaryTop + 22, DIM);
        }
        int summaryColor = this.deliveryBoard.getTotalOrders() > 0 && this.deliveryBoard.getCompletedOrders() >= this.deliveryBoard.getTotalOrders()
            ? GREEN
            : (this.deliveryBoard.getTotalOrders() > 0 ? ACCENT : DIM);
        this.drawPaperText(g, this.trimToWidth(this.deliveryBoard.getSummaryStatus(), summaryRight - summaryLeft - 16),
            summaryLeft + 8, summaryTop + 36, summaryColor);

        int listTop = summaryBottom + 10;
        int listBottom = listTop + VISIBLE_ORDER_ROWS * ORDER_ROW_HEIGHT;
        this.renderPaperSection(g, summaryLeft, listTop, summaryRight, listBottom);
        this.drawPaperText(g, "Saved station board", summaryLeft + 4, listTop - 10, TEXT);
        String hint = "Manifest edits apply after Apply";
        this.drawPaperText(g, hint, summaryRight - this.font.width(hint) - 4, listTop - 10, DIM);

        List<DeliveryOrderLineView> rows = this.deliveryBoard.getLineViews();
        if (rows.isEmpty()) {
            this.drawPaperText(g, "No delivery orders configured yet.", summaryLeft + 8, listTop + 10, DIM);
            return;
        }

        for (int rowIndex = 0; rowIndex < VISIBLE_ORDER_ROWS; rowIndex++) {
            int orderIndex = this.orderScroll + rowIndex;
            int rowTop = listTop + rowIndex * ORDER_ROW_HEIGHT;
            int rowBottom = rowTop + ORDER_ROW_HEIGHT - 1;
            boolean hovered = mouseX >= summaryLeft && mouseX < summaryRight && mouseY >= rowTop && mouseY < rowBottom;
            g.fill(summaryLeft + 1, rowTop + 1, summaryRight - 1, rowBottom, hovered ? SLOT_HOVER : 0x22000000);
            if (orderIndex >= rows.size()) {
                continue;
            }
            DeliveryOrderLineView row = rows.get(orderIndex);
            this.renderDeliveryOrderRow(g, row, summaryLeft, summaryRight, rowTop, rowBottom, mouseX, mouseY);
        }
        this.renderScrollIndicators(g, summaryRight - 10, listTop + 4, listBottom - 12,
            this.orderScroll > 0, this.orderScroll + VISIBLE_ORDER_ROWS < rows.size());
        List<Component> tooltip = this.getDeliveryOrderHoverTooltip(mouseX, mouseY, summaryLeft, summaryRight, listTop, rows);
        if (tooltip != null) {
            g.renderTooltip(this.font, tooltip, java.util.Optional.empty(), mouseX, mouseY);
        }
    }

    private void renderDeliveryOrderRow(GuiGraphics g, DeliveryOrderLineView row, int left, int right, int rowTop, int rowBottom,
                                        int mouseX, int mouseY) {
        int markerColor = row.isDismissed() ? DIM : (row.isComplete() ? GREEN : (row.getDeliveredAmount() > 0 ? ACCENT : RED));
        g.fill(left + 2, rowTop + 3, left + 5, rowBottom - 2, markerColor);
        if (!row.getDisplayStack().isEmpty()) {
            g.renderItem(row.getDisplayStack(), left + 8, rowTop + 5);
        }

        String primaryLabel = row.getDisplayName();
        String modeBadge = "[" + row.getResetMode().getBadgeLabel(row.getResetIntervalValue()) + "]";
        int actionWidth = row.isComplete() ? 40 : 38;
        int dismissWidth = 36;
        int actionX = right - 10 - dismissWidth - 6 - actionWidth;
        int dismissX = right - 10 - dismissWidth;
        int badgeX = actionX - 8 - this.font.width(modeBadge);
        int textColor = row.isDismissed() ? DIM : TEXT;
        this.drawPaperText(g, this.trimToWidth(primaryLabel, badgeX - (left + 28) - 4), left + 28, rowTop + 5, textColor);
        this.drawPaperText(g, modeBadge, badgeX, rowTop + 5, DIM);

        String operation = row.getOperation() == ManifestOperation.UNLOAD ? "Unload" : "Load";
        String secondary = operation + " " + FreightManifestLogic.formatAmount(row.getCargoKind(), row.getDeliveredAmount())
            + " / " + FreightManifestLogic.formatAmount(row.getCargoKind(), row.getTargetAmount());
        if (row.getFreightAmount() > 0) {
            secondary += " | On train " + FreightManifestLogic.formatAmount(row.getCargoKind(), row.getFreightAmount());
        }
        if (row.getTicksUntilReset() > 0L) {
            secondary += " | Reset " + FreightManifestLogic.formatTicks(row.getTicksUntilReset());
        }
        secondary = this.trimToWidth(secondary + " | " + row.getStatusText(), actionX - (left + 28) - 4);
        this.drawPaperText(g, secondary, left + 28, rowTop + 16, row.isDismissed() ? DIM : ACCENT);

        // Animated progress strip along the bottom of the row.
        if (row.getTargetAmount() > 0) {
            float ap = this.orderProgressAnim.getOrDefault(row.getLineId(),
                    DispatchUiFx.clamp01(row.getDeliveredAmount() / (float) row.getTargetAmount()));
            int barColor = row.isDismissed() ? DIM : (row.isComplete() ? GREEN : (row.getDeliveredAmount() > 0 ? ACCENT : RED));
            int barRight = badgeX - 8;
            boolean activeRow = !row.isComplete() && !row.isDismissed() && row.getDeliveredAmount() > 0;
            if (barRight > left + 28 + 8) {
                DispatchUiFx.liveProgressBar(g, left + 28, rowTop + 24, barRight - (left + 28), 3,
                        ap, barColor, 0xFF14110C, 0x88705438, activeRow, this.frameTime);
            }
        }

        String actionLabel = row.isComplete() ? "Reset" : "Done";
        this.renderMiniButton(g, actionX, rowTop + 5, actionWidth, actionLabel, true, mouseX, mouseY);
        boolean dismissActive = row.isComplete() || row.isDismissed();
        this.renderMiniButton(g, dismissX, rowTop + 5, dismissWidth, row.isDismissed() ? "Show" : "Hide", dismissActive, mouseX, mouseY);
    }

    private void renderPaperFrame(GuiGraphics g) {
        g.fill(this.panelLeft, this.panelTop, this.panelLeft + this.panelWidth, this.panelTop + this.panelHeight, PANEL_BG);
        g.fill(this.panelLeft + 2, this.panelTop + 2, this.panelLeft + this.panelWidth - 2, this.panelTop + this.panelHeight - 2, PANEL_INNER);
        g.fill(this.panelLeft, this.panelTop, this.panelLeft + this.panelWidth, this.panelTop + 2, BORDER);
        g.fill(this.panelLeft, this.panelTop + this.panelHeight - 2, this.panelLeft + this.panelWidth, this.panelTop + this.panelHeight, BORDER);
        g.fill(this.panelLeft, this.panelTop, this.panelLeft + 2, this.panelTop + this.panelHeight, BORDER);
        g.fill(this.panelLeft + this.panelWidth - 2, this.panelTop, this.panelLeft + this.panelWidth, this.panelTop + this.panelHeight, BORDER);
        g.fill(this.panelLeft + 8, this.panelTop + 10, this.panelLeft + 14, this.panelTop + this.panelHeight - 10, 0x55B08B57);
        for (int y = this.panelTop + 22; y < this.panelTop + this.panelHeight - 12; y += 34) {
            g.fill(this.panelLeft + 8, y, this.panelLeft + 14, y + 5, BRASS);
            g.fill(this.panelLeft + 9, y + 1, this.panelLeft + 13, y + 4, PANEL_BG);
        }
        int clipLeft = this.width / 2 - 36;
        g.fill(clipLeft, this.panelTop - 6, clipLeft + 72, this.panelTop + 8, BRASS);
        g.fill(clipLeft + 6, this.panelTop - 3, clipLeft + 66, this.panelTop + 5, 0xFFE0BE84);
        g.fill(clipLeft + 10, this.panelTop - 1, clipLeft + 14, this.panelTop + 3, BORDER);
        g.fill(clipLeft + 58, this.panelTop - 1, clipLeft + 62, this.panelTop + 3, BORDER);
    }

    private void renderPaperSection(GuiGraphics g, int left, int top, int right, int bottom) {
        g.fill(left, top, right, bottom, SLOT_BG);
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

    private void drawPaperCenteredText(GuiGraphics g, String text, int centerX, int y, int color) {
        this.drawPaperText(g, text, centerX - this.font.width(text) / 2, y, color);
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

    private void renderStatusChip(GuiGraphics g, int x, int y, String label, int color) {
        int width = this.font.width(label) + 10;
        g.fill(x, y, x + width, y + 14, 0xE8F1E5C6);
        g.fill(x, y, x + width, y + 1, color);
        g.fill(x, y + 13, x + width, y + 14, BORDER);
        g.fill(x, y, x + 1, y + 14, BORDER);
        g.fill(x + width - 1, y, x + width, y + 14, BORDER);
        this.drawPaperText(g, label, x + 5, y + 3, this.paperInk(color));
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

    private List<Component> getDeliveryOrderHoverTooltip(int mouseX, int mouseY, int left, int right, int listTop,
                                                         List<DeliveryOrderLineView> rows) {
        if (mouseX < left || mouseX >= right || mouseY < listTop) {
            return null;
        }
        int listBottom = listTop + VISIBLE_ORDER_ROWS * ORDER_ROW_HEIGHT;
        if (mouseY >= listBottom) {
            return null;
        }
        int rowIndex = this.orderScroll + (mouseY - listTop) / ORDER_ROW_HEIGHT;
        if (rowIndex < 0 || rowIndex >= rows.size()) {
            return null;
        }
        DeliveryOrderLineView row = rows.get(rowIndex);
        int rowTop = listTop + (rowIndex - this.orderScroll) * ORDER_ROW_HEIGHT;
        int actionWidth = row.isComplete() ? 40 : 38;
        int dismissWidth = 36;
        int actionX = right - 10 - dismissWidth - 6 - actionWidth;
        int dismissX = right - 10 - dismissWidth;
        if (mouseX >= actionX && mouseX < actionX + actionWidth && mouseY >= rowTop + 5 && mouseY < rowTop + 21) {
            return List.of(
                Component.literal(row.isComplete() ? "Reset this order cycle" : "Mark complete for this cycle"),
                Component.literal(row.isComplete() ? "Clears progress and reopens the line." : "Use when this order was satisfied outside live tracking.").withStyle(net.minecraft.ChatFormatting.GRAY));
        }
        boolean dismissActive = row.isComplete() || row.isDismissed();
        if (dismissActive && mouseX >= dismissX && mouseX < dismissX + dismissWidth && mouseY >= rowTop + 5 && mouseY < rowTop + 21) {
            return List.of(
                Component.literal(row.isDismissed() ? "Show hidden order again" : "Hide this completed order"),
                Component.literal(row.isDismissed() ? "Bring the line back into the visible list." : "Keeps the board tidy for this cycle only.").withStyle(net.minecraft.ChatFormatting.GRAY));
        }
        return null;
    }

    private void renderCargoTab(GuiGraphics g, int mouseX, int mouseY) {
        int summaryLeft = this.panelLeft + 18;
        int summaryTop = this.panelTop + 88;
        int summaryRight = this.panelLeft + this.panelWidth - 18;
        int summaryBottom = summaryTop + 52;
        this.renderPaperSection(g, summaryLeft, summaryTop, summaryRight, summaryBottom);

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
        int previewColor = this.getPreviewStatusColor(preview);
        if (this.cargoView.isTrainPresent()) {
            this.drawPaperText(g, "Current Train: " + this.cargoView.getTrainName(), summaryLeft + 8, summaryTop + 8, TEXT);
            this.drawPaperText(g,
                "Carriages: " + this.cargoView.getCarriageCount() + " | Tracked Items: " + itemTypes + " | Tracked Fluids: " + fluidTypes,
                summaryLeft + 8, summaryTop + 22, DIM);
        } else {
            this.drawPaperText(g, "Current Train: none", summaryLeft + 8, summaryTop + 8, TEXT);
            this.drawPaperText(g,
                "Working preview only | Tracked Items: " + itemTypes + " | Tracked Fluids: " + fluidTypes,
                summaryLeft + 8, summaryTop + 22, DIM);
        }
        this.drawPaperText(g, this.trimToWidth("Preview: " + preview.getStatusMessage(), summaryRight - summaryLeft - 16),
            summaryLeft + 8, summaryTop + 36, previewColor);

        int listTop = summaryBottom + 10;
        CargoRow hoveredRow = null;
        if (this.historyExpanded) {
            int listBottom = listTop + VISIBLE_EXPANDED_HISTORY_ROWS * EXPANDED_HISTORY_ROW_HEIGHT;
            this.renderPaperSection(g, summaryLeft, listTop, summaryRight, listBottom);
            this.drawPaperText(g, "Recent Delivery History [Click to collapse]", summaryLeft + 4, listTop - 10, TEXT);
            hoveredRow = null;
            this.renderExpandedHistoryPanel(g, mouseX, mouseY, summaryLeft, summaryRight, listTop);
            this.renderScrollIndicators(g, summaryRight - 10, listTop + 4, listBottom - 12,
                this.historyScroll > 0, this.historyScroll + VISIBLE_EXPANDED_HISTORY_ROWS < this.deliveryBoard.getHistoryEntries().size());
        } else {
            int splitWidth = summaryRight - summaryLeft;
            int cargoPanelRight = summaryLeft + splitWidth * 11 / 20;
            int historyPanelLeft = cargoPanelRight + 8;
            int listBottom = listTop + VISIBLE_CARGO_ROWS * CARGO_ROW_HEIGHT;
            this.renderPaperSection(g, summaryLeft, listTop, cargoPanelRight, listBottom);
            this.renderPaperSection(g, historyPanelLeft, listTop, summaryRight, listBottom);
            this.drawPaperText(g, "Manifest & Cargo Overview", summaryLeft + 4, listTop - 10, TEXT);
            this.drawPaperText(g, "Recent Delivery History [Click to expand]", historyPanelLeft + 4, listTop - 10, TEXT);
            int cargoWidth = cargoPanelRight - summaryLeft;
            int freightRight = summaryLeft + cargoWidth * 12 / 20;
            int toStationRight = summaryLeft + cargoWidth * 16 / 20;
            int toTrainRight = cargoPanelRight - 8;
            this.drawPaperText(g, "In Freight", freightRight - this.font.width("In Freight"), listTop - 10, DIM);
            this.drawPaperText(g, "To Station", toStationRight - this.font.width("To Station"), listTop - 10, GREEN);
            this.drawPaperText(g, "To Train", toTrainRight - this.font.width("To Train"), listTop - 10, RED);

            if (this.cargoRows.isEmpty()) {
                this.drawPaperText(g, "No manifest targets or live cargo to show yet.", summaryLeft + 8, listTop + 10, DIM);
            }

            hoveredRow = this.renderCargoRows(g, mouseX, mouseY, summaryLeft, cargoPanelRight, listTop, freightRight, toStationRight, toTrainRight);
            this.renderHistoryPanel(g, mouseX, mouseY, historyPanelLeft, summaryRight, listTop);
            this.renderScrollIndicators(g, cargoPanelRight - 10, listTop + 4, listBottom - 12,
                this.cargoScroll > 0, this.cargoScroll + VISIBLE_CARGO_ROWS < this.cargoRows.size());
            this.renderScrollIndicators(g, summaryRight - 10, listTop + 4, listBottom - 12,
                this.historyScroll > 0, this.historyScroll + VISIBLE_CARGO_ROWS < this.deliveryBoard.getHistoryEntries().size());
        }

        if (hoveredRow != null && hoveredRow.hasManifestGoal()) {
            List<Component> tooltip = new ArrayList<>();
            tooltip.add(Component.literal(hoveredRow.name));
            if (hoveredRow.expectedToStation > 0) {
                tooltip.add(Component.literal("Unload target: " + hoveredRow.amountText(Math.min(hoveredRow.toStationAmount, hoveredRow.expectedToStation))
                    + " / " + hoveredRow.amountText(hoveredRow.expectedToStation)));
            }
            if (hoveredRow.expectedToTrain > 0) {
                tooltip.add(Component.literal("Load target: " + hoveredRow.amountText(Math.min(hoveredRow.toTrainAmount, hoveredRow.expectedToTrain))
                    + " / " + hoveredRow.amountText(hoveredRow.expectedToTrain)));
            }
            g.renderTooltip(this.font, tooltip, java.util.Optional.empty(), mouseX, mouseY);
        } else {
            List<Component> historyHint = this.getHistoryHintTooltip(mouseX, mouseY, summaryLeft, summaryRight, listTop);
            if (historyHint != null) {
                g.renderTooltip(this.font, historyHint, java.util.Optional.empty(), mouseX, mouseY);
            }
        }
    }

    private List<Component> getHistoryHintTooltip(int mouseX, int mouseY, int left, int right, int listTop) {
        if (this.deliveryBoard.getHistoryEntries().isEmpty()) {
            return null;
        }
        if (this.historyExpanded) {
            int listBottom = listTop + VISIBLE_EXPANDED_HISTORY_ROWS * EXPANDED_HISTORY_ROW_HEIGHT;
            if (mouseX >= left && mouseX < right && mouseY >= listTop && mouseY < listBottom) {
                return List.of(
                    Component.literal("Click to collapse history"),
                    Component.literal("Return to the split cargo + history view.").withStyle(net.minecraft.ChatFormatting.GRAY));
            }
            return null;
        }
        int historyLeft = left + (right - left) * 11 / 20 + 8;
        int listBottom = listTop + VISIBLE_CARGO_ROWS * CARGO_ROW_HEIGHT;
        if (mouseX >= historyLeft && mouseX < right && mouseY >= listTop && mouseY < listBottom) {
            return List.of(
                Component.literal("Click to expand history"),
                Component.literal("Open a larger recent-delivery timeline.").withStyle(net.minecraft.ChatFormatting.GRAY));
        }
        return null;
    }

    private CargoRow renderCargoRows(GuiGraphics g, int mouseX, int mouseY, int left, int right, int listTop,
                                     int freightRight, int toStationRight, int toTrainRight) {
        CargoRow hoveredRow = null;
        for (int rowIndex = 0; rowIndex < VISIBLE_CARGO_ROWS; rowIndex++) {
            int cargoIndex = this.cargoScroll + rowIndex;
            int rowTop = listTop + rowIndex * CARGO_ROW_HEIGHT;
            int rowBottom = rowTop + CARGO_ROW_HEIGHT - 1;
            boolean hovered = mouseX >= left && mouseX < right && mouseY >= rowTop && mouseY < rowBottom;
            g.fill(left + 1, rowTop + 1, right - 1, rowBottom, hovered ? SLOT_HOVER : 0x22000000);
            if (cargoIndex >= this.cargoRows.size()) {
                continue;
            }
            CargoRow row = this.cargoRows.get(cargoIndex);
            if (hovered) {
                hoveredRow = row;
            }
            this.renderCargoGoalMarker(g, left + 3, rowTop + 3, row, rowBottom - rowTop - 4);
            if (!row.icon.isEmpty()) {
                g.renderItem(row.icon, left + 8, rowTop + 2);
            }
            this.drawPaperText(g, this.trimToWidth(row.name, freightRight - left - 48), left + 28, rowTop + 6, row.hasManifestGoal() ? TEXT : DIM);
            String freightText = row.amountText(row.freightAmount);
            String toStationText = row.amountText(row.toStationAmount);
            String toTrainText = row.amountText(row.toTrainAmount);
            this.drawPaperText(g, freightText, freightRight - this.font.width(freightText), rowTop + 6, DIM);
            this.drawPaperText(g, toStationText, toStationRight - this.font.width(toStationText), rowTop + 6, GREEN);
            this.drawPaperText(g, toTrainText, toTrainRight - this.font.width(toTrainText), rowTop + 6, RED);
        }
        return hoveredRow;
    }

    private void renderHistoryPanel(GuiGraphics g, int mouseX, int mouseY, int left, int right, int listTop) {
        List<DeliveryOrderHistoryEntry> historyEntries = this.deliveryBoard.getHistoryEntries();
        if (historyEntries.isEmpty()) {
            this.drawPaperText(g, "No delivery history yet.", left + 8, listTop + 10, DIM);
            return;
        }
        for (int rowIndex = 0; rowIndex < VISIBLE_CARGO_ROWS; rowIndex++) {
            int historyIndex = this.historyScroll + rowIndex;
            int rowTop = listTop + rowIndex * CARGO_ROW_HEIGHT;
            int rowBottom = rowTop + CARGO_ROW_HEIGHT - 1;
            boolean hovered = mouseX >= left && mouseX < right && mouseY >= rowTop && mouseY < rowBottom;
            g.fill(left + 1, rowTop + 1, right - 1, rowBottom, hovered ? SLOT_HOVER : 0x22000000);
            if (historyIndex >= historyEntries.size()) {
                continue;
            }
            DeliveryOrderHistoryEntry entry = historyEntries.get(historyIndex);
            int statusColor = this.getHistoryColor(entry.getType());
            g.fill(left + 3, rowTop + 4, left + 6, rowBottom - 3, statusColor);
            String age = FreightManifestLogic.formatTicks(Math.max(0L, this.deliveryBoard.getCurrentTick() - entry.getTick()));
            this.drawPaperText(g, age, right - this.font.width(age) - 6, rowTop + 6, DIM);
            String detail = entry.getTitle() + ": " + entry.getDetail();
            this.drawPaperText(g, this.trimToWidth(detail, Math.max(40, right - left - this.font.width(age) - 18)), left + 10, rowTop + 6, this.paperInk(statusColor));
        }
    }

    private void renderExpandedHistoryPanel(GuiGraphics g, int mouseX, int mouseY, int left, int right, int listTop) {
        List<DeliveryOrderHistoryEntry> historyEntries = this.deliveryBoard.getHistoryEntries();
        if (historyEntries.isEmpty()) {
            this.drawPaperText(g, "No delivery history yet.", left + 8, listTop + 10, DIM);
            return;
        }
        for (int rowIndex = 0; rowIndex < VISIBLE_EXPANDED_HISTORY_ROWS; rowIndex++) {
            int historyIndex = this.historyScroll + rowIndex;
            int rowTop = listTop + rowIndex * EXPANDED_HISTORY_ROW_HEIGHT;
            int rowBottom = rowTop + EXPANDED_HISTORY_ROW_HEIGHT - 1;
            boolean hovered = mouseX >= left && mouseX < right && mouseY >= rowTop && mouseY < rowBottom;
            g.fill(left + 1, rowTop + 1, right - 1, rowBottom, hovered ? SLOT_HOVER : 0x22000000);
            if (historyIndex >= historyEntries.size()) {
                continue;
            }
            DeliveryOrderHistoryEntry entry = historyEntries.get(historyIndex);
            int statusColor = this.getHistoryColor(entry.getType());
            g.fill(left + 4, rowTop + 5, left + 8, rowBottom - 5, statusColor);
            String age = FreightManifestLogic.formatTicks(Math.max(0L, this.deliveryBoard.getCurrentTick() - entry.getTick()));
            this.drawPaperText(g, age, right - this.font.width(age) - 8, rowTop + 6, DIM);
            this.drawPaperText(g, this.trimToWidth(entry.getTitle(), Math.max(60, right - left - this.font.width(age) - 28)),
                left + 14, rowTop + 6, this.paperInk(statusColor));
            this.drawPaperText(g, this.trimToWidth(entry.getDetail(), Math.max(60, right - left - 22)),
                left + 14, rowTop + 20, TEXT);
        }
    }

    private int getHistoryColor(DeliveryOrderHistoryType type) {
        return switch (type) {
            case COMPLETED, MANUAL_COMPLETE, SHOWN -> GREEN;
            case HIDDEN -> DIM;
            case AUTO_RESET, MANUAL_RESET -> ACCENT;
        };
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return switch (this.currentTab) {
            case MANIFEST -> this.handleLineClick(mouseX, mouseY) || this.handleInventoryClick(mouseX, mouseY) || super.mouseClicked(mouseX, mouseY, button);
            case DELIVERY_ORDERS -> this.handleDeliveryOrderClick(mouseX, mouseY) || super.mouseClicked(mouseX, mouseY, button);
            case TOTAL_CARGO -> this.handleCargoClick(mouseX, mouseY) || super.mouseClicked(mouseX, mouseY, button);
        };
    }

    private boolean handleLineClick(double mouseX, double mouseY) {
        int listLeft = this.getManifestListLeft();
        int listTop = this.getManifestTop();
        int listRight = this.getManifestListRight();
        int listBottom = listTop + VISIBLE_MANIFEST_ROWS * MANIFEST_ROW_HEIGHT;
        if (mouseX < listLeft || mouseX >= listRight || mouseY < listTop || mouseY >= listBottom) {
            return false;
        }
        int row = (int) ((mouseY - listTop) / MANIFEST_ROW_HEIGHT);
        int lineIndex = this.lineScroll + row;
        if (lineIndex >= 0 && lineIndex < this.lines.size()) {
            this.applyEditorInputs();
            this.selectedIndex = lineIndex;
            this.rebuildWidgets();
        }
        return true;
    }

    private boolean handleDeliveryOrderClick(double mouseX, double mouseY) {
        int listLeft = this.panelLeft + 18;
        int listTop = this.panelTop + 150;
        int listRight = this.panelLeft + this.panelWidth - 18;
        int listBottom = listTop + VISIBLE_ORDER_ROWS * ORDER_ROW_HEIGHT;
        if (mouseX < listLeft || mouseX >= listRight || mouseY < listTop || mouseY >= listBottom) {
            return false;
        }
        int rowIndex = this.orderScroll + (int) ((mouseY - listTop) / ORDER_ROW_HEIGHT);
        List<DeliveryOrderLineView> rows = this.deliveryBoard.getLineViews();
        if (rowIndex < 0 || rowIndex >= rows.size()) {
            return true;
        }
        DeliveryOrderLineView row = rows.get(rowIndex);
        int rowTop = listTop + (rowIndex - this.orderScroll) * ORDER_ROW_HEIGHT;
        int actionWidth = row.isComplete() ? 40 : 38;
        int dismissWidth = 36;
        int actionX = listRight - 10 - dismissWidth - 6 - actionWidth;
        int dismissX = listRight - 10 - dismissWidth;
        if (mouseX >= actionX && mouseX < actionX + actionWidth && mouseY >= rowTop + 5 && mouseY < rowTop + 21) {
            DeliveryOrderAction action = row.isComplete() ? DeliveryOrderAction.RESET_CYCLE : DeliveryOrderAction.MARK_COMPLETE;
            this.playUiClick(row.isComplete() ? 0.9f : 1.1f);
            PacketDistributor.sendToServer(new FreightManifestDeliveryOrderActionPacket(this.blockPos, row.getLineId(), action));
            return true;
        }
        if ((row.isComplete() || row.isDismissed())
            && mouseX >= dismissX && mouseX < dismissX + dismissWidth && mouseY >= rowTop + 5 && mouseY < rowTop + 21) {
            this.playUiClick(row.isDismissed() ? 1.0f : 0.95f);
            PacketDistributor.sendToServer(new FreightManifestDeliveryOrderActionPacket(this.blockPos, row.getLineId(), DeliveryOrderAction.TOGGLE_DISMISSED));
            return true;
        }
        return true;
    }

    private boolean handleCargoClick(double mouseX, double mouseY) {
        if (this.deliveryBoard.getHistoryEntries().isEmpty()) {
            return false;
        }
        int listLeft = this.panelLeft + 18;
        int listTop = this.panelTop + 150;
        int listRight = this.panelLeft + this.panelWidth - 18;
        if (this.historyExpanded) {
            int listBottom = listTop + VISIBLE_EXPANDED_HISTORY_ROWS * EXPANDED_HISTORY_ROW_HEIGHT;
            if (mouseX >= listLeft && mouseX < listRight && mouseY >= listTop && mouseY < listBottom) {
                this.historyExpanded = false;
                this.clampHistoryScroll();
                this.playUiClick(0.95f);
                return true;
            }
            return false;
        }
        int splitWidth = listRight - listLeft;
        int historyLeft = listLeft + splitWidth * 11 / 20 + 8;
        int listBottom = listTop + VISIBLE_CARGO_ROWS * CARGO_ROW_HEIGHT;
        if (mouseX >= historyLeft && mouseX < listRight && mouseY >= listTop && mouseY < listBottom) {
            this.historyExpanded = true;
            this.clampHistoryScroll();
            this.playUiClick(1.05f);
            return true;
        }
        return false;
    }

    private boolean handleInventoryClick(double mouseX, double mouseY) {
        if (!this.canEdit || !this.hasSelection()) {
            return false;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return false;
        }

        int slot = this.getInventorySlotAt(mouseX, mouseY);
        if (slot < 0) {
            return false;
        }
        ItemStack stack = minecraft.player.getInventory().getItem(slot);
        if (stack.isEmpty()) {
            return true;
        }

        this.applyEditorInputs();
        FreightManifestLine selected = this.getSelectedLine();
        ItemStack displayStack = stack.copy();
        displayStack.setCount(1);

        FluidStack fluid = FluidUtil.getFluidContained(displayStack).orElse(FluidStack.EMPTY);
        if (!fluid.isEmpty()) {
            ResourceLocation fluidId = BuiltInRegistries.FLUID.getKey(fluid.getFluid());
            if (fluidId != null) {
                selected.setSelection(ManifestCargoKind.FLUID, fluidId, displayStack);
            }
        } else {
            ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(displayStack.getItem());
            if (itemId != null) {
                selected.setSelection(ManifestCargoKind.ITEM, itemId, displayStack);
            }
        }

        this.refreshCargoRows();
        this.markDirty();
        this.rebuildWidgets();
        return true;
    }

    private int getInventorySlotAt(double mouseX, double mouseY) {
        int left = this.panelLeft + 18;
        int top = this.getInventoryTop();
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int x = left + col * 18;
                int y = top + row * 18;
                if (mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16) {
                    return 9 + row * 9 + col;
                }
            }
        }
        int hotbarTop = top + 58;
        for (int col = 0; col < 9; col++) {
            int x = left + col * 18;
            if (mouseX >= x && mouseX < x + 16 && mouseY >= hotbarTop && mouseY < hotbarTop + 16) {
                return col;
            }
        }
        return -1;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        switch (this.currentTab) {
            case MANIFEST -> {
                int maxScroll = Math.max(0, this.lines.size() - VISIBLE_MANIFEST_ROWS);
                int listLeft = this.getManifestListLeft();
                int listTop = this.getManifestTop();
                int listRight = this.getManifestListRight();
                int listBottom = listTop + VISIBLE_MANIFEST_ROWS * MANIFEST_ROW_HEIGHT;
                if (mouseX >= listLeft && mouseX < listRight && mouseY >= listTop && mouseY < listBottom && maxScroll > 0) {
                    this.lineScroll = Math.max(0, Math.min(maxScroll, this.lineScroll - (int) Math.signum(scrollY)));
                    return true;
                }
            }
            case DELIVERY_ORDERS -> {
                int maxScroll = Math.max(0, this.deliveryBoard.getLineViews().size() - VISIBLE_ORDER_ROWS);
                int listLeft = this.panelLeft + 18;
                int listTop = this.panelTop + 150;
                int listRight = this.panelLeft + this.panelWidth - 18;
                int listBottom = listTop + VISIBLE_ORDER_ROWS * ORDER_ROW_HEIGHT;
                if (mouseX >= listLeft && mouseX < listRight && mouseY >= listTop && mouseY < listBottom && maxScroll > 0) {
                    this.orderScroll = Math.max(0, Math.min(maxScroll, this.orderScroll - (int) Math.signum(scrollY)));
                    return true;
                }
            }
            case TOTAL_CARGO -> {
                int listLeft = this.panelLeft + 18;
                int listTop = this.panelTop + 150;
                int listRight = this.panelLeft + this.panelWidth - 18;
                if (this.historyExpanded) {
                    int historyMaxScroll = Math.max(0, this.deliveryBoard.getHistoryEntries().size() - VISIBLE_EXPANDED_HISTORY_ROWS);
                    int listBottom = listTop + VISIBLE_EXPANDED_HISTORY_ROWS * EXPANDED_HISTORY_ROW_HEIGHT;
                    if (mouseX >= listLeft && mouseX < listRight && mouseY >= listTop && mouseY < listBottom && historyMaxScroll > 0) {
                        this.historyScroll = Math.max(0, Math.min(historyMaxScroll, this.historyScroll - (int) Math.signum(scrollY)));
                        return true;
                    }
                } else {
                    int cargoMaxScroll = Math.max(0, this.cargoRows.size() - VISIBLE_CARGO_ROWS);
                    int historyMaxScroll = Math.max(0, this.deliveryBoard.getHistoryEntries().size() - VISIBLE_CARGO_ROWS);
                    int splitWidth = listRight - listLeft;
                    int cargoRight = listLeft + splitWidth * 11 / 20;
                    int historyLeft = cargoRight + 8;
                    int listBottom = listTop + VISIBLE_CARGO_ROWS * CARGO_ROW_HEIGHT;
                    if (mouseY >= listTop && mouseY < listBottom) {
                        if (mouseX >= listLeft && mouseX < cargoRight && cargoMaxScroll > 0) {
                            this.cargoScroll = Math.max(0, Math.min(cargoMaxScroll, this.cargoScroll - (int) Math.signum(scrollY)));
                            return true;
                        }
                        if (mouseX >= historyLeft && mouseX < listRight && historyMaxScroll > 0) {
                            this.historyScroll = Math.max(0, Math.min(historyMaxScroll, this.historyScroll - (int) Math.signum(scrollY)));
                            return true;
                        }
                    }
                }
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private void ensureSelectionVisible() {
        if (!this.hasSelection()) {
            this.lineScroll = 0;
            return;
        }
        if (this.selectedIndex < this.lineScroll) {
            this.lineScroll = this.selectedIndex;
        } else if (this.selectedIndex >= this.lineScroll + VISIBLE_MANIFEST_ROWS) {
            this.lineScroll = this.selectedIndex - VISIBLE_MANIFEST_ROWS + 1;
        }
    }

    public boolean isForClipboard(BlockPos pos) {
        return this.blockPos.equals(pos);
    }

    public void updateLiveState(FreightStationCargoView cargoView, DeliveryOrderBoardView deliveryBoard) {
        this.cargoView = cargoView.copy();
        this.deliveryBoard = deliveryBoard;
        this.refreshCargoRows();
        int maxOrderScroll = Math.max(0, this.deliveryBoard.getLineViews().size() - VISIBLE_ORDER_ROWS);
        this.orderScroll = Math.max(0, Math.min(maxOrderScroll, this.orderScroll));
        if (this.deliveryBoard.getHistoryEntries().isEmpty()) {
            this.historyExpanded = false;
        }
        this.clampHistoryScroll();
    }

    private void markDirty() {
        this.dirty = true;
        this.confirmClose = false;
    }

    private String getCloseButtonLabel() {
        return this.confirmClose ? "Discard?" : "Close";
    }

    private void clampHistoryScroll() {
        int visibleRows = this.historyExpanded ? VISIBLE_EXPANDED_HISTORY_ROWS : VISIBLE_CARGO_ROWS;
        int maxHistoryScroll = Math.max(0, this.deliveryBoard.getHistoryEntries().size() - visibleRows);
        this.historyScroll = Math.max(0, Math.min(maxHistoryScroll, this.historyScroll));
    }

    private List<CargoRow> buildCargoRows(FreightStationCargoView cargoView) {
        List<CargoRow> rows = new ArrayList<>();
        Map<FreightManifestResourceKey, Integer> expectedToStation = this.buildExpectedAmounts(ManifestOperation.UNLOAD);
        Map<FreightManifestResourceKey, Integer> expectedToTrain = this.buildExpectedAmounts(ManifestOperation.LOAD);
        List<FreightManifestResourceKey> keys = new ArrayList<>();
        keys.addAll(cargoView.getFreightTotals().getAmounts().keySet());
        for (FreightManifestResourceKey key : cargoView.getMovedToStation().getAmounts().keySet()) {
            if (!keys.contains(key)) {
                keys.add(key);
            }
        }
        for (FreightManifestResourceKey key : cargoView.getMovedToTrain().getAmounts().keySet()) {
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
                cargoView.getFreightTotals().getAmount(key),
                cargoView.getMovedToStation().getAmount(key),
                cargoView.getMovedToTrain().getAmount(key),
                expectedToStation.getOrDefault(key, 0),
                expectedToTrain.getOrDefault(key, 0)));
        }
        rows.sort(Comparator.comparing((CargoRow row) -> !row.hasManifestGoal()).thenComparing(row -> row.kind.ordinal()).thenComparing(row -> row.name));
        return rows;
    }

    private void refreshCargoRows() {
        this.cargoRows.clear();
        this.cargoRows.addAll(this.buildCargoRows(this.cargoView));
        int maxScroll = Math.max(0, this.cargoRows.size() - VISIBLE_CARGO_ROWS);
        this.cargoScroll = Math.max(0, Math.min(maxScroll, this.cargoScroll));
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

    private Set<java.util.UUID> getDuplicateLineIds() {
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
        Set<java.util.UUID> duplicates = this.getDuplicateLineIds();
        if (!duplicates.isEmpty()) {
            return duplicates.size() + " lines share the same cargo and direction.";
        }
        return this.getConfiguredLineCount() + "/" + this.lines.size() + " lines ready to apply";
    }

    private int getManifestSummaryColor() {
        FreightManifestValidationResult validation = this.getManifestValidation();
        if (!this.stationLink.isLinked() || this.lines.isEmpty()) {
            return DIM;
        }
        if (!validation.isValid()) {
            return RED;
        }
        return this.getDuplicateLineIds().isEmpty() ? GREEN : ACCENT;
    }

    private boolean isLineConfigured(FreightManifestLine line) {
        return line != null && line.hasSelection() && line.getAmount() > 0
            && (!line.getResetMode().isIntervalBased() || line.getResetIntervalValue() > 0);
    }

    private String getSelectedLineStatusText(FreightManifestLine line) {
        if (line == null) {
            return "No line selected";
        }
        if (!line.hasSelection()) {
            return "Missing resource";
        }
        if (line.getAmount() <= 0) {
            return "Missing amount";
        }
        if (line.getResetMode().isIntervalBased() && line.getResetIntervalValue() <= 0) {
            return "Missing interval";
        }
        if (this.getDuplicateLineIds().contains(line.getLineId())) {
            return "Duplicate target line";
        }
        return "Ready";
    }

    private int getSelectedLineStatusColor(FreightManifestLine line) {
        if (line == null) {
            return DIM;
        }
        if (this.getDuplicateLineIds().contains(line.getLineId())) {
            return ACCENT;
        }
        return this.isLineConfigured(line) ? GREEN : RED;
    }

    private int getOperationColor(ManifestOperation operation) {
        return operation == ManifestOperation.UNLOAD ? GREEN : RED;
    }

    private FreightManifestEvaluationResult getPreviewEvaluation() {
        FreightManifestValidationResult validation = this.getManifestValidation();
        if (!validation.isValid()) {
            return FreightManifestEvaluationResult.waiting(validation.getMessage());
        }
        return FreightManifestLogic.previewEvaluation(this.buildWorkingManifest(), this.cargoView);
    }

    private int getPreviewStatusColor(FreightManifestEvaluationResult evaluation) {
        FreightManifestValidationResult validation = this.getManifestValidation();
        if (!validation.isValid()) {
            return RED;
        }
        if (this.lines.isEmpty()) {
            return DIM;
        }
        if (evaluation.isComplete()) {
            return GREEN;
        }
        return this.cargoView.isTrainPresent() ? ACCENT : DIM;
    }

    private void renderCargoGoalMarker(GuiGraphics g, int x, int y, CargoRow row, int height) {
        if (!row.hasManifestGoal()) {
            return;
        }
        int width = 3;
        if (row.expectedToStation > 0 && row.expectedToTrain > 0) {
            int split = Math.max(1, height / 2);
            g.fill(x, y, x + width, y + split, GREEN);
            g.fill(x, y + split, x + width, y + height, RED);
            return;
        }
        g.fill(x, y, x + width, y + height, row.expectedToStation > 0 ? GREEN : RED);
    }

    private String trimToWidth(String text, int width) {
        if (this.font.width(text) <= width) {
            return text;
        }
        String ellipsis = "...";
        int maxWidth = Math.max(0, width - this.font.width(ellipsis));
        return this.font.plainSubstrByWidth(text, maxWidth) + ellipsis;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static class CargoRow {
        private final ManifestCargoKind kind;
        private final String name;
        private final int freightAmount;
        private final int toStationAmount;
        private final int toTrainAmount;
        private final int expectedToStation;
        private final int expectedToTrain;
        private final ItemStack icon;

        private CargoRow(ManifestCargoKind kind, String name, int freightAmount, int toStationAmount, int toTrainAmount,
                         int expectedToStation, int expectedToTrain, ItemStack icon) {
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
            return this.kind == ManifestCargoKind.FLUID ? (amount + "mB") : Integer.toString(amount);
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
