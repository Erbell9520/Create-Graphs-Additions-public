package com.createstats.gui;

import com.createstats.blockentity.MiniDisplayBE;
import com.createstats.blockentity.MiniDisplaySettings;
import com.createstats.display.BattleCompactTargetHandler;
import com.createstats.network.MiniDisplayConfigPacket;
import net.neoforged.neoforge.network.PacketDistributor;
import com.createstats.renderer.instrument.InstrumentRenderPrimitives;
import com.createstats.util.InstrumentSourceHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public class MiniDisplayScreen extends Screen {

    private static final int PANEL_BG = 0xF0181818;
    private static final int BORDER = 0xFF444444;
    private static final int TEXT = 0xFFDDCCAA;
    private static final int DIM = 0xFF888079;
    private static final int STATUS_BG = 0xAA11161C;
    private static final int ROW_HEIGHT = 22;

    private final MiniDisplayBE miniDisplay;
    private final Screen returnScreen;
    private final MiniDisplaySettings settings;

    private int panelLeft;
    private int panelTop;
    private int panelWidth;
    private int panelHeight;
    private int statusTop;
    private int buttonY;

    public MiniDisplayScreen(MiniDisplayBE miniDisplay) {
        this(miniDisplay, null);
    }

    public MiniDisplayScreen(MiniDisplayBE miniDisplay, Screen returnScreen) {
        super(Component.literal("Mini Display"));
        this.miniDisplay = miniDisplay;
        this.returnScreen = returnScreen;
        this.settings = miniDisplay.getSettings().copy();
    }

    @Override
    protected void init() {
        super.init();
        this.clearWidgets();

        final InstrumentSourceHelper.SourceKind previewKind = this.miniDisplay.getPreviewSourceKind(this.settings.getSourceMode());
        final boolean itemStoragePreview = previewKind == InstrumentSourceHelper.SourceKind.ITEM_STORAGE;

        this.panelWidth = 296;
        final boolean selectedItems = itemStoragePreview
                && this.settings.getStorageDetailMode() == MiniDisplaySettings.StorageDetailMode.SELECTED_ITEMS;
        this.panelHeight = selectedItems ? 332 : 276;
        this.panelLeft = (this.width - this.panelWidth) / 2;
        this.panelTop = (this.height - this.panelHeight) / 2;
        this.statusTop = this.panelTop + (selectedItems ? 244 : 188);
        this.buttonY = this.statusTop + 58;

        int y = this.panelTop + 90;

        this.addRenderableWidget(Button.builder(
                Component.literal("Source: " + this.settings.getSourceMode().getDisplayName()),
                b -> {
                    this.settings.cycleSourceMode();
                    this.rebuildWidgets();
                }).bounds(this.panelLeft + 12, y, this.panelWidth - 24, 20).build());
        y += ROW_HEIGHT;

        final String storageLabel = itemStoragePreview
                ? "Storage View: " + this.settings.getStorageDetailMode().getDisplayName()
                : "Storage View: item only";
        final Button storageView = Button.builder(
                Component.literal(storageLabel),
                b -> {
                    if (itemStoragePreview) {
                        this.settings.cycleStorageDetailMode();
                        this.rebuildWidgets();
                    }
                }).bounds(this.panelLeft + 12, y, this.panelWidth - 24, 20).build();
        storageView.active = itemStoragePreview;
        this.addRenderableWidget(storageView);
        y += ROW_HEIGHT;

        this.addRenderableWidget(Button.builder(
                Component.literal("Accent: " + this.settings.getAccentColor().getDisplayName()),
                b -> {
                    this.settings.cycleAccentColor();
                    this.rebuildWidgets();
                }).bounds(this.panelLeft + 12, y, this.panelWidth - 24, 20).build());
        y += ROW_HEIGHT;

        this.addRenderableWidget(Button.builder(
                Component.literal(this.settings.isLit() ? "Lit Face: ON" : "Lit Face: OFF"),
                b -> {
                    this.settings.setLit(!this.settings.isLit());
                    this.rebuildWidgets();
                }).bounds(this.panelLeft + 12, y, (this.panelWidth - 28) / 2, 20).build());
        this.addRenderableWidget(Button.builder(
                Component.literal(this.settings.isShowLabels() ? "Labels: ON" : "Labels: OFF"),
                b -> {
                    this.settings.setShowLabels(!this.settings.isShowLabels());
                    this.rebuildWidgets();
                }).bounds(this.panelLeft + 16 + (this.panelWidth - 28) / 2, y, (this.panelWidth - 28) / 2, 20).build());
        y += ROW_HEIGHT + 2;

        if (selectedItems) {
            this.addFilterRow(y, 0);
            y += 26;
            this.addFilterRow(y, 1);
            y += 30;
        }

        final int buttonWidth = 86;
        this.addRenderableWidget(Button.builder(Component.literal("Apply"), b -> this.applyAndClose())
                .bounds(this.panelLeft + this.panelWidth / 2 - buttonWidth - 4, this.buttonY, buttonWidth, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> this.onClose())
                .bounds(this.panelLeft + this.panelWidth / 2 + 4, this.buttonY, buttonWidth, 20).build());
    }

    private void addFilterRow(int y, int index) {
        final ItemStack filter = index == 0 ? this.settings.getFilterA() : this.settings.getFilterB();
        final String label = filter.isEmpty()
                ? "Filter " + (index + 1) + ": none"
                : "Filter " + (index + 1) + ": " + this.trimToWidth(filter.getHoverName().getString(), 110);
        final int labelWidth = 144;
        final Button useHeld = Button.builder(Component.literal("Use Held"), b -> {
            final ItemStack held = this.getHeldItemForFilter();
            if (!held.isEmpty()) {
                this.settings.setFilter(index, held);
                this.rebuildWidgets();
            }
        }).bounds(this.panelLeft + 12 + labelWidth + 4, y, 74, 20).build();
        useHeld.active = !this.getHeldItemForFilter().isEmpty();
        final Button clear = Button.builder(Component.literal("Clear"), b -> {
            this.settings.clearFilter(index);
            this.rebuildWidgets();
        }).bounds(this.panelLeft + this.panelWidth - 62, y, 50, 20).build();
        clear.active = !filter.isEmpty();

        this.addRenderableWidget(Button.builder(Component.literal(label), b -> {
        }).bounds(this.panelLeft + 12, y, labelWidth, 20).build()).active = false;
        this.addRenderableWidget(useHeld);
        this.addRenderableWidget(clear);
    }

    @Override
    protected void rebuildWidgets() {
        this.init();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private ItemStack getHeldItemForFilter() {
        if (Minecraft.getInstance().player == null) {
            return ItemStack.EMPTY;
        }
        final ItemStack mainHand = Minecraft.getInstance().player.getMainHandItem();
        if (!mainHand.isEmpty()) {
            return mainHand;
        }
        final ItemStack offhand = Minecraft.getInstance().player.getOffhandItem();
        return offhand.isEmpty() ? ItemStack.EMPTY : offhand;
    }

    private void applyAndClose() {
        PacketDistributor.sendToServer(new MiniDisplayConfigPacket(this.miniDisplay.getBlockPos(), this.settings));
        // Do not apply settings on the client here; the server will validate the packet
        // and sync the accepted state back via the normal block-entity update path.
        this.onClose();
    }

    @Override
    public void onClose() {
        if (this.returnScreen != null) {
            Minecraft.getInstance().setScreen(this.returnScreen);
            return;
        }
        super.onClose();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderTransparentBackground(g);

        final InstrumentSourceHelper.SourceKind sourceKind = this.miniDisplay.getPreviewSourceKind(this.settings.getSourceMode());
        final boolean battlePreview = this.isBattlePreview();
        final boolean itemStoragePreview = sourceKind == InstrumentSourceHelper.SourceKind.ITEM_STORAGE;
        final boolean previewLinked = this.settings.getSourceMode() == MiniDisplaySettings.SourceMode.LINKED;
        final boolean liveLinked = this.miniDisplay.getSettings().getSourceMode() == MiniDisplaySettings.SourceMode.LINKED;

        g.fill(this.panelLeft, this.panelTop, this.panelLeft + this.panelWidth, this.panelTop + this.panelHeight, PANEL_BG);
        g.fill(this.panelLeft, this.panelTop, this.panelLeft + this.panelWidth, this.panelTop + 1, BORDER);
        g.fill(this.panelLeft, this.panelTop + this.panelHeight - 1, this.panelLeft + this.panelWidth, this.panelTop + this.panelHeight, BORDER);
        g.fill(this.panelLeft, this.panelTop, this.panelLeft + 1, this.panelTop + this.panelHeight, BORDER);
        g.fill(this.panelLeft + this.panelWidth - 1, this.panelTop, this.panelLeft + this.panelWidth, this.panelTop + this.panelHeight, BORDER);

        final int accent = this.settings.getAccentColor().getColor();
        g.drawCenteredString(this.font, "Mini Display", this.width / 2, this.panelTop + 8, accent);
        g.drawCenteredString(this.font, "Wall readout for compact machine and battle stats", this.width / 2, this.panelTop + 20, TEXT);

        final String previewSource = this.miniDisplay.getPreviewSourceSummary(this.settings.getSourceMode());
        final int sourceBoxTop = this.panelTop + 32;
        g.fill(this.panelLeft + 12, sourceBoxTop, this.panelLeft + this.panelWidth - 12, sourceBoxTop + 50, STATUS_BG);
        g.drawString(this.font, "Detected Class: " + (battlePreview ? "Battle Relay" : sourceKind.getDisplayName()), this.panelLeft + 18, sourceBoxTop + 7, TEXT, false);
        g.drawString(this.font, this.trimToWidth("Source: " + previewSource, this.panelWidth - 40), this.panelLeft + 18, sourceBoxTop + 19, DIM, false);
        g.drawString(this.font, this.trimToWidth("Preview Layout: " + this.getPreviewLayoutSummary(sourceKind, battlePreview), this.panelWidth - 40), this.panelLeft + 18, sourceBoxTop + 31, DIM, false);
        final String previewModeLine = previewLinked
                ? "Mode: Linked relay"
                : "Mode: Local readout";
        g.drawString(this.font, this.trimToWidth(previewModeLine, this.panelWidth - 40),
                this.panelLeft + 18, sourceBoxTop + 41, DIM, false);

        g.fill(this.panelLeft + 12, this.statusTop, this.panelLeft + this.panelWidth - 12, this.statusTop + 50, STATUS_BG);
        g.drawString(this.font, this.trimToWidth("Live: " + this.miniDisplay.getLiveSummary(), this.panelWidth - 40),
                this.panelLeft + 18, this.statusTop + 7, DIM, false);
        g.drawString(this.font, this.trimToWidth("Face: " + (this.settings.isLit() ? "lit" : "dim")
                        + " • " + (this.settings.isShowLabels() ? "labels" : "minimal")
                        + " • " + this.settings.getAccentColor().getDisplayName(),
                this.panelWidth - 40), this.panelLeft + 18, this.statusTop + 19, DIM, false);
        final String liveFootprintLine = liveLinked
                ? "Footprint: " + this.miniDisplay.getFootprintSummary() + " • Relay status: " + this.miniDisplay.getRelaySummary()
                : "Footprint: " + this.miniDisplay.getFootprintSummary();
        g.drawString(this.font, this.trimToWidth(liveFootprintLine, this.panelWidth - 40),
                this.panelLeft + 18, this.statusTop + 31, DIM, false);
        if (!itemStoragePreview) {
            g.drawString(this.font, this.trimToWidth("Storage filters only apply to item storage sources", this.panelWidth - 40),
                    this.panelLeft + 18, this.statusTop + 41, DIM, false);
        }

        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
    }

    private boolean isBattlePreview() {
        return BattleCompactTargetHandler.isBattleSource(this.miniDisplay.getPreviewSourceSummary(this.settings.getSourceMode()));
    }

    private String getPreviewLayoutSummary(InstrumentSourceHelper.SourceKind sourceKind, boolean battlePreview) {
        if (battlePreview) {
            return "hull bar + alert plate + compact observer summary";
        }
        return switch (sourceKind) {
            case ITEM_STORAGE -> this.settings.getStorageDetailMode() == MiniDisplaySettings.StorageDetailMode.SELECTED_ITEMS
                    ? "fill bar + total items + two tracked rows"
                    : "fill bar + total items";
            case FLUID_STORAGE -> "fill bar + fluid amount";
            case ENERGY_STORAGE -> "charge bar + stored FE";
            case POWER_GRID -> "charge bar + grid buffer";
            case BOILER -> "boiler gauge + heat readout";
            case KINETIC_STRESS -> "stress bar + SU bar";
            case KINETIC_SPEED -> "speed plate";
            case NO_SOURCE, UNKNOWN -> "waiting for compatible source";
        };
    }

    private String trimToWidth(String text, int maxWidth) {
        final String trimmed = this.font.plainSubstrByWidth(text, maxWidth);
        if (trimmed.length() == text.length()) {
            return text;
        }
        return InstrumentRenderPrimitives.truncate(trimmed, Math.max(4, trimmed.length()));
    }
}
