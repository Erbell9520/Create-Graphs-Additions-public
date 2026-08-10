package com.createstats.gui;

import com.createstats.data.MemoryCogData;
import com.createstats.data.MemoryCogData.SnapshotMetadata;
import com.createstats.data.MetricType;
import com.createstats.data.TimeSeriesStore;
import com.createstats.display.StatsDisplaySettings;
import com.createstats.gui.widgets.GraphWidget;
import com.createstats.item.HandheldLinkData;
import com.createstats.item.HandheldLinkData.TargetType;
import com.createstats.item.StatsHandheldItem;
import com.createstats.network.HandheldStatePacket;
import com.createstats.network.RequestHandheldStatePacket;
import com.createstats.train.manifest.DeliveryOrderBoardView;
import com.createstats.train.manifest.DeliveryOrderLineView;
import com.createstats.train.manifest.FreightManifestResourceKey;
import com.createstats.train.manifest.FreightStationCargoView;
import com.createstats.train.manifest.ManifestCargoKind;
import com.createstats.train.manifest.TrainCargoSnapshot;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.network.PacketDistributor;

public class StatsHandheldScreen extends Screen {

    private static final int PANEL_WIDTH = 200;
    private static final int PANEL_HEIGHT = 318;
    private static final int SCREEN_INSET_X = 12;
    private static final int SCREEN_INSET_TOP = 18;
    private static final int SCREEN_INSET_BOTTOM = 44;
    private static final int SCREEN_WIDTH = PANEL_WIDTH - SCREEN_INSET_X * 2;
    private static final int SCREEN_HEIGHT = PANEL_HEIGHT - SCREEN_INSET_TOP - SCREEN_INSET_BOTTOM;
    private static final int CONTENT_PADDING = 8;
    private static final int HERO_CARD_TOP = 48;
    private static final int HERO_CARD_HEIGHT = 34;
    private static final int GRAPH_TOP = 92;
    private static final int GRAPH_HEIGHT = 108;
    private static final int FOOTER_TOP = GRAPH_TOP + GRAPH_HEIGHT + 8;
    private static final int CARD_GAP = 8;

    private static final int DEVICE_BODY = 0xFF24190F;
    private static final int DEVICE_RIM = 0xFF5E4325;
    private static final int SCREEN_GLASS = 0xFF16100A;
    private static final int PANEL_BG = 0xFFE5D1AA;
    private static final int PANEL_INNER = 0xFFF2E7C7;
    private static final int BORDER = 0xFF705438;
    private static final int ACCENT = 0xFF94622A;
    private static final int TEXT = 0xFF342718;
    private static final int DIM = 0xFF7B6951;
    private static final int GOOD = 0xFF90D878;
    private static final int WARN = 0xFFE0B868;
    private static final int BAD = 0xFFE08A70;
    private static final int WAKE_ANIMATION_TICKS = 18;
    private static final int SIGNAL_PULSE_TICKS = 16;
    private static final int COG_READ_PULSE_TICKS = 22;
    private static final int GRAPH_FADE_TICKS = 14;
    private static final int PULSE_PERIOD_TICKS = 36;
    private static final StatsDisplaySettings.GraphType[] HANDHELD_GRAPH_TYPES = {
        StatsDisplaySettings.GraphType.LINE_CHART,
        StatsDisplaySettings.GraphType.BAR_CHART,
        StatsDisplaySettings.GraphType.GAUGE,
        StatsDisplaySettings.GraphType.NUMERIC
    };

    private final InteractionHand hand;
    private final StatsDisplaySettings settings = new StatsDisplaySettings();
    private GraphWidget graphWidget;
    private Button graphTypeButton;
    private Button colorsButton;
    private Button timeRangeButton;
    private HandheldLinkData activeLink = new HandheldLinkData();
    private HandheldStatePacket remoteState;
    private TimeSeriesStore localStore;
    private SnapshotMetadata localMetadata = SnapshotMetadata.empty();
    private TimeSeriesStore staleGraphStore;
    private String staleMetricLabel = "";
    private FreightStationCargoView staleCargoView;
    private DeliveryOrderBoardView staleDeliveryBoard;
    private int refreshCooldown;
    private String activeLinkKey = "";
    private int animationTicks;
    private int wakeTicks;
    private int signalPulseTicks;
    private int cogReadPulseTicks;
    private int graphFadeTicks;
    private String lastRemoteSignature = "";
    private String lastLocalSnapshotKey = "";
    private String lastGraphVisualKey = "";

    public StatsHandheldScreen(InteractionHand hand) {
        super(Component.translatable("gui.create_stats.handheld.title"));
        this.hand = hand;
    }

    @Override
    protected void init() {
        super.init();
        int graphX = this.screenLeft() + CONTENT_PADDING;
        int graphY = this.screenTop() + GRAPH_TOP;
        this.graphWidget = new GraphWidget(graphX, graphY, SCREEN_WIDTH - CONTENT_PADDING * 2, GRAPH_HEIGHT);
        this.graphWidget.setSettings(this.settings);
        this.addRenderableWidget(this.graphWidget);

        int buttonGap = 4;
        int buttonWidth = (SCREEN_WIDTH - buttonGap * 3) / 4;
        int buttonY = this.panelTop() + PANEL_HEIGHT - 28;
        int buttonX = this.screenLeft();

        this.graphTypeButton = this.addRenderableWidget(Button.builder(Component.literal("Line"), button -> {
            this.settings.setGraphType(this.nextHandheldGraphType(this.settings.getGraphType()));
            this.updateGraphState();
            this.syncButtonLabels();
        }).bounds(buttonX, buttonY, buttonWidth, 20).build());
        this.timeRangeButton = this.addRenderableWidget(Button.builder(Component.literal("5m"), button -> {
            this.settings.cycleTimeRange();
            this.updateGraphState();
            this.syncButtonLabels();
        }).bounds(buttonX + buttonWidth + buttonGap, buttonY, buttonWidth, 20).build());
        this.colorsButton = this.addRenderableWidget(Button.builder(Component.literal("Theme"), button -> {
            this.settings.cycleColorScheme();
            this.updateGraphState();
        }).bounds(buttonX + (buttonWidth + buttonGap) * 2, buttonY, buttonWidth, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Close"), button -> this.onClose())
            .bounds(buttonX + (buttonWidth + buttonGap) * 3, buttonY, buttonWidth, 20).build());

        this.syncButtonLabels();
        this.updateGraphState();
        this.animationTicks = 0;
        this.wakeTicks = 0;
        this.signalPulseTicks = 0;
        this.cogReadPulseTicks = 0;
        this.graphFadeTicks = GRAPH_FADE_TICKS;
    }

    @Override
    public void tick() {
        super.tick();
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player == null) {
            return;
        }
        this.animationTicks++;
        if (this.wakeTicks < WAKE_ANIMATION_TICKS) {
            this.wakeTicks++;
        }
        if (this.signalPulseTicks > 0) {
            this.signalPulseTicks--;
        }
        if (this.cogReadPulseTicks > 0) {
            this.cogReadPulseTicks--;
        }
        if (this.graphFadeTicks > 0) {
            this.graphFadeTicks--;
        }

        ItemStack handheld = player.getItemInHand(this.hand);
        if (!handheld.is(com.createstats.registry.ModItems.STATS_HANDHELD.get())) {
            this.onClose();
            return;
        }

        HandheldLinkData newLink = StatsHandheldItem.getLinkData(handheld);
        String newLinkKey = this.buildLinkKey(newLink);
        if (!newLinkKey.equals(this.activeLinkKey)) {
            this.activeLinkKey = newLinkKey;
            this.remoteState = null;
            this.staleGraphStore = null;
            this.staleMetricLabel = "";
            this.staleCargoView = null;
            this.staleDeliveryBoard = null;
            this.lastRemoteSignature = "";
            this.lastGraphVisualKey = "";
            this.graphFadeTicks = GRAPH_FADE_TICKS;
        }
        this.activeLink = newLink;
        this.updateLocalSnapshot(player);

        if (this.activeLink.isLinked()) {
            if (--this.refreshCooldown <= 0) {
                this.refreshCooldown = 5;
                PacketDistributor.sendToServer(new RequestHandheldStatePacket(this.hand));
            }
        } else {
            this.remoteState = null;
            this.refreshCooldown = 0;
        }

        this.updateGraphState();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderTransparentBackground(graphics);

        int left = this.panelLeft();
        int top = this.panelTop();
        int screenLeft = this.screenLeft();
        int screenTop = this.screenTop();

        this.renderDeviceChrome(graphics, left, top, screenLeft, screenTop);
        this.renderStatusBar(graphics, screenLeft, screenTop);
        this.renderConnectionBadge(graphics, screenLeft, screenTop);

        if (this.isDispatchView()) {
            this.renderDispatchView(graphics, screenLeft, screenTop);
        } else if (this.isGraphVisible()) {
            this.renderHeroCard(graphics, screenLeft, screenTop);
        } else {
            this.renderEmptyView(graphics, screenLeft, screenTop);
        }

        super.render(graphics, mouseX, mouseY, partialTick);

        if (this.isGraphVisible()) {
            this.renderGraphFooter(graphics, screenLeft, screenTop);
        }

        this.renderGraphVisualOverlays(graphics, screenLeft, screenTop);

        if (this.isShowingStaleSnapshot()) {
            graphics.drawCenteredString(this.font, "Last good snapshot", screenLeft + SCREEN_WIDTH / 2, screenTop + SCREEN_HEIGHT - 14, WARN);
        }
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    public void updateRemoteState(HandheldStatePacket packet) {
        if (packet.getHand() != this.hand) {
            return;
        }
        String signature = this.buildRemoteSignature(packet);
        if (!signature.equals(this.lastRemoteSignature)) {
            this.lastRemoteSignature = signature;
            this.signalPulseTicks = SIGNAL_PULSE_TICKS;
            this.graphFadeTicks = GRAPH_FADE_TICKS;
        }
        this.remoteState = packet;
        if (packet.getStore() != null) {
            this.staleGraphStore = packet.getStore();
            this.staleMetricLabel = packet.getMetricLabel();
        }
        if (packet.getCargoView() != null) {
            this.staleCargoView = packet.getCargoView();
        }
        if (packet.getDeliveryBoard() != null) {
            this.staleDeliveryBoard = packet.getDeliveryBoard();
        }
        this.updateGraphState();
    }

    private void updateLocalSnapshot(Player player) {
        ItemStack cog = StatsHandheldItem.getReadableCog(player, this.hand);
        if (cog.isEmpty() || !MemoryCogData.hasData(cog)) {
            this.localStore = null;
            this.localMetadata = SnapshotMetadata.empty();
            this.lastLocalSnapshotKey = "";
            return;
        }
        this.localStore = MemoryCogData.readFromStack(cog);
        this.localMetadata = MemoryCogData.getMetadata(cog);
        String snapshotKey = this.buildLocalSnapshotKey(this.localStore, this.localMetadata);
        if (!snapshotKey.isEmpty() && !snapshotKey.equals(this.lastLocalSnapshotKey)) {
            this.cogReadPulseTicks = COG_READ_PULSE_TICKS;
            this.graphFadeTicks = GRAPH_FADE_TICKS;
        }
        this.lastLocalSnapshotKey = snapshotKey;
    }

    private void updateGraphState() {
        if (this.graphWidget == null) {
            return;
        }
        TimeSeriesStore store = this.getActiveGraphStore();
        boolean visible = this.isGraphVisible();
        if (!this.isHandheldGraphType(this.settings.getGraphType())) {
            this.settings.setGraphType(StatsDisplaySettings.GraphType.LINE_CHART);
            this.syncButtonLabels();
        }
        this.graphWidget.visible = visible;
        this.graphWidget.active = visible;
        this.graphWidget.setSettings(this.settings);
        this.graphWidget.setMetricType(store != null ? store.getMetricType() : MetricType.GENERIC);
        this.graphWidget.setData(store != null ? store.getDataForTimeScale(this.settings.getTimeRange().getSeconds()) : new float[0]);
        String graphKey = this.buildGraphVisualKey(store, visible);
        if (!graphKey.equals(this.lastGraphVisualKey)) {
            this.lastGraphVisualKey = graphKey;
            this.graphFadeTicks = GRAPH_FADE_TICKS;
        }
        if (this.graphTypeButton != null) {
            this.graphTypeButton.active = visible;
            this.colorsButton.active = visible;
            this.timeRangeButton.active = visible;
        }
    }

    private void syncButtonLabels() {
        if (this.graphTypeButton != null) {
            this.graphTypeButton.setMessage(Component.literal(this.shortGraphTypeLabel()));
        }
        if (this.timeRangeButton != null) {
            this.timeRangeButton.setMessage(Component.literal(this.shortTimeRangeLabel()));
        }
    }

    private void renderDeviceChrome(GuiGraphics graphics, int left, int top, int screenLeft, int screenTop) {
        graphics.fill(left + 2, top, left + PANEL_WIDTH - 2, top + PANEL_HEIGHT, DEVICE_BODY);
        graphics.fill(left, top + 2, left + PANEL_WIDTH, top + PANEL_HEIGHT - 2, DEVICE_BODY);
        graphics.fill(left + 4, top + 4, left + PANEL_WIDTH - 4, top + PANEL_HEIGHT - 4, DEVICE_RIM);
        graphics.fill(screenLeft - 2, screenTop - 2, screenLeft + SCREEN_WIDTH + 2, screenTop + SCREEN_HEIGHT + 2, BORDER);
        graphics.fill(screenLeft, screenTop, screenLeft + SCREEN_WIDTH, screenTop + SCREEN_HEIGHT, SCREEN_GLASS);
        graphics.fill(screenLeft + 2, screenTop + 2, screenLeft + SCREEN_WIDTH - 2, screenTop + SCREEN_HEIGHT - 2, PANEL_BG);
        graphics.fill(screenLeft + 5, screenTop + 5, screenLeft + SCREEN_WIDTH - 5, screenTop + SCREEN_HEIGHT - 5, PANEL_INNER);
        graphics.fill(left + PANEL_WIDTH / 2 - 16, top + 8, left + PANEL_WIDTH / 2 + 16, top + 10, 0x66342718);
        graphics.fill(screenLeft + 8, screenTop + 18, screenLeft + SCREEN_WIDTH - 8, screenTop + 19, 0x3324170F);
        if (this.wakeTicks < WAKE_ANIMATION_TICKS) {
            float progress = this.wakeTicks / (float) WAKE_ANIMATION_TICKS;
            int shadeAlpha = Math.max(0, (int) ((1.0f - progress) * 120.0f));
            int glowAlpha = Math.max(0, (int) ((1.0f - progress) * 96.0f));
            graphics.fill(screenLeft + 2, screenTop + 2, screenLeft + SCREEN_WIDTH - 2, screenTop + SCREEN_HEIGHT - 2, this.withAlpha(0xFF000000, shadeAlpha));
            this.drawInsetBorder(graphics, screenLeft - 3, screenTop - 3, SCREEN_WIDTH + 6, SCREEN_HEIGHT + 6, this.withAlpha(ACCENT, glowAlpha));
        }
        if (this.cogReadPulseTicks > 0) {
            int alpha = (int) (this.pulseProgress(this.cogReadPulseTicks, COG_READ_PULSE_TICKS) * 96.0f);
            this.drawInsetBorder(graphics, screenLeft + 3, screenTop + 3, SCREEN_WIDTH - 6, SCREEN_HEIGHT - 6, this.withAlpha(GOOD, alpha));
        }
    }

    private void renderStatusBar(GuiGraphics graphics, int screenLeft, int screenTop) {
        String targetName = this.activeLink.isLinked() ? this.activeLink.getTargetName() : this.getLocalHeaderLabel();
        graphics.drawString(this.font, this.getModeLabel(), screenLeft + 8, screenTop + 8, DIM, false);
        graphics.drawCenteredString(this.font, this.trim(targetName, SCREEN_WIDTH - 74), screenLeft + SCREEN_WIDTH / 2, screenTop + 8, TEXT);
        this.drawSignalBars(graphics, screenLeft + SCREEN_WIDTH - 28, screenTop + 7, this.getSignalBars(), this.activeLink.isLinked());
        if (this.signalPulseTicks > 0) {
            int alpha = (int) (this.pulseProgress(this.signalPulseTicks, SIGNAL_PULSE_TICKS) * 130.0f);
            graphics.fill(screenLeft + SCREEN_WIDTH - 31, screenTop + 5, screenLeft + SCREEN_WIDTH - 4, screenTop + 21, this.withAlpha(GOOD, alpha));
        }
    }

    private void renderConnectionBadge(GuiGraphics graphics, int screenLeft, int screenTop) {
        int x1 = screenLeft + 8;
        int x2 = screenLeft + SCREEN_WIDTH - 8;
        int y1 = screenTop + 24;
        int y2 = y1 + 18;
        int statusColor = this.getStatusColor();
        graphics.fill(x1, y1, x2, y2, this.withAlpha(statusColor, 0x18));
        if (this.isLinking()) {
            int alpha = 0x18 + (int)(this.pulseWave() * 54.0f);
            graphics.fill(x1 + 2, y1 + 2, x2 - 2, y2 - 2, this.withAlpha(statusColor, alpha));
        }
        graphics.fill(x1, y1, x2, y1 + 2, statusColor);
        graphics.drawCenteredString(this.font, this.trim(this.getAnimatedBadgeLabel(), x2 - x1 - 10), screenLeft + SCREEN_WIDTH / 2, y1 + 6, statusColor);
    }

    private void renderHeroCard(GuiGraphics graphics, int screenLeft, int screenTop) {
        int x = screenLeft + CONTENT_PADDING;
        int y = screenTop + HERO_CARD_TOP;
        int width = SCREEN_WIDTH - CONTENT_PADDING * 2;
        int height = HERO_CARD_HEIGHT;
        String latestValue = this.getLatestValueText();
        graphics.fill(x, y, x + width, y + height, 0x1F6B5336);
        if (this.signalPulseTicks > 0) {
            int alpha = (int) (this.pulseProgress(this.signalPulseTicks, SIGNAL_PULSE_TICKS) * 54.0f);
            graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, this.withAlpha(GOOD, alpha));
        }
        if (this.cogReadPulseTicks > 0 && !this.activeLink.isLinked()) {
            int alpha = (int) (this.pulseProgress(this.cogReadPulseTicks, COG_READ_PULSE_TICKS) * 62.0f);
            graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, this.withAlpha(ACCENT, alpha));
        }
        graphics.fill(x, y, x + width, y + 1, BORDER);
        graphics.fill(x, y + height - 1, x + width, y + height, BORDER);
        graphics.drawString(this.font, this.trim(this.getMetricLabel(), width - 12), x + 6, y + 5, ACCENT, false);
        graphics.drawString(this.font, latestValue, x + width - 6 - this.font.width(latestValue), y + 5, TEXT, false);
        graphics.drawString(this.font, this.trim(this.getHeroSubtitle(), width - 12), x + 6, y + 19, DIM, false);
    }

    private void renderGraphFooter(GuiGraphics graphics, int screenLeft, int screenTop) {
        TimeSeriesStore store = this.getActiveGraphStore();
        if (store == null || store.getSampleCount() == 0) {
            return;
        }

        float[] visible = store.getDataForTimeScale(this.settings.getTimeRange().getSeconds());
        if (visible.length == 0) {
            return;
        }

        float min = Float.MAX_VALUE;
        float max = -Float.MAX_VALUE;
        float sum = 0.0f;
        for (float value : visible) {
            min = Math.min(min, value);
            max = Math.max(max, value);
            sum += value;
        }

        int gap = 4;
        int pillWidth = (SCREEN_WIDTH - CONTENT_PADDING * 2 - gap * 2) / 3;
        int y = screenTop + FOOTER_TOP;
        int x = screenLeft + CONTENT_PADDING;
        this.renderStatPill(graphics, x, y, pillWidth, "MIN", min);
        this.renderStatPill(graphics, x + pillWidth + gap, y, pillWidth, "AVG", sum / visible.length);
        this.renderStatPill(graphics, x + (pillWidth + gap) * 2, y, pillWidth, "MAX", max);
    }

    private void renderStatPill(GuiGraphics graphics, int x, int y, int width, String label, float value) {
        graphics.fill(x, y, x + width, y + 24, 0x1A6B5336);
        graphics.fill(x, y, x + width, y + 1, BORDER);
        graphics.fill(x, y + 23, x + width, y + 24, BORDER);
        graphics.drawCenteredString(this.font, label, x + width / 2, y + 4, DIM);
        graphics.drawCenteredString(this.font, this.formatValue(value), x + width / 2, y + 13, TEXT);
    }

    private void renderEmptyView(GuiGraphics graphics, int screenLeft, int screenTop) {
        int x = screenLeft + CONTENT_PADDING;
        int y = screenTop + 92;
        int width = SCREEN_WIDTH - CONTENT_PADDING * 2;
        int height = 84;
        graphics.fill(x, y, x + width, y + height, 0x1A6B5336);
        graphics.fill(x, y, x + width, y + 1, BORDER);
        graphics.fill(x, y + height - 1, x + width, y + height, BORDER);
        graphics.drawCenteredString(this.font, "No local snapshot", x + width / 2, y + 20, ACCENT);
        graphics.drawCenteredString(this.font, "Hold a Memory Cog in your", x + width / 2, y + 38, TEXT);
        graphics.drawCenteredString(this.font, "other hand or link a target.", x + width / 2, y + 50, TEXT);
    }

    private void renderDispatchView(GuiGraphics graphics, int screenLeft, int screenTop) {
        FreightStationCargoView cargoView = this.remoteState != null && this.remoteState.getCargoView() != null ? this.remoteState.getCargoView() : this.staleCargoView;
        DeliveryOrderBoardView deliveryBoard = this.remoteState != null && this.remoteState.getDeliveryBoard() != null ? this.remoteState.getDeliveryBoard() : this.staleDeliveryBoard;

        int x = screenLeft + CONTENT_PADDING;
        int width = SCREEN_WIDTH - CONTENT_PADDING * 2;
        int cargoY = screenTop + 52;
        int cargoHeight = 88;
        int ordersY = cargoY + cargoHeight + CARD_GAP;
        int ordersHeight = 102;

        this.renderDispatchCard(graphics, x, cargoY, width, cargoHeight, "Cargo feed");
        if (cargoView == null) {
            graphics.drawString(this.font, "Waiting for cargo snapshot.", x + 8, cargoY + 20, DIM, false);
        } else {
            graphics.drawString(this.font, this.trim(cargoView.getStatusMessage(), width - 16), x + 8, cargoY + 20, TEXT, false);
            String trainLine = cargoView.isTrainPresent()
                ? "Train: " + cargoView.getTrainName() + " (" + cargoView.getCarriageCount() + " cars)"
                : "No train present";
            graphics.drawString(this.font, this.trim(trainLine, width - 16), x + 8, cargoY + 32, DIM, false);
            this.renderTopCargoEntries(graphics, cargoView.getFreightTotals(), x + 8, cargoY + 48, width - 16);
        }

        this.renderDispatchCard(graphics, x, ordersY, width, ordersHeight, "Orders");
        if (deliveryBoard == null) {
            graphics.drawString(this.font, "Waiting for delivery snapshot.", x + 8, ordersY + 20, DIM, false);
        } else {
            String summary = String.format(Locale.ROOT, "%d total | %d done | %d dismissed",
                deliveryBoard.getTotalOrders(), deliveryBoard.getCompletedOrders(), deliveryBoard.getDismissedOrders());
            graphics.drawString(this.font, this.trim(summary, width - 16), x + 8, ordersY + 20, TEXT, false);
            graphics.drawString(this.font, this.trim(deliveryBoard.getSummaryStatus(), width - 16), x + 8, ordersY + 32, DIM, false);
            this.renderOrderPreview(graphics, deliveryBoard.getLineViews(), x + 8, ordersY + 48, width - 16);
        }
    }

    private void renderDispatchCard(GuiGraphics graphics, int x, int y, int width, int height, String title) {
        graphics.fill(x, y, x + width, y + height, 0x186B5336);
        graphics.fill(x, y, x + width, y + 1, BORDER);
        graphics.fill(x, y + height - 1, x + width, y + height, BORDER);
        graphics.fill(x, y, x + 1, y + height, BORDER);
        graphics.fill(x + width - 1, y, x + width, y + height, BORDER);
        graphics.drawString(this.font, title, x + 8, y + 6, ACCENT, false);
    }

    private void renderTopCargoEntries(GuiGraphics graphics, TrainCargoSnapshot snapshot, int x, int y, int width) {
        List<Map.Entry<FreightManifestResourceKey, Integer>> entries = new ArrayList<>(snapshot.getAmounts().entrySet());
        entries.sort(Map.Entry.<FreightManifestResourceKey, Integer>comparingByValue().reversed());
        if (entries.isEmpty()) {
            graphics.drawString(this.font, "No tracked cargo.", x, y, DIM, false);
            return;
        }
        for (int index = 0; index < Math.min(3, entries.size()); ++index) {
            Map.Entry<FreightManifestResourceKey, Integer> entry = entries.get(index);
            String line = this.formatCargoName(entry.getKey()) + ": " + entry.getValue();
            graphics.drawString(this.font, this.trim(line, width), x, y + index * 12, TEXT, false);
        }
    }

    private void renderOrderPreview(GuiGraphics graphics, List<DeliveryOrderLineView> lines, int x, int y, int width) {
        if (lines.isEmpty()) {
            graphics.drawString(this.font, "No active delivery rows.", x, y, DIM, false);
            return;
        }
        for (int index = 0; index < Math.min(3, lines.size()); ++index) {
            DeliveryOrderLineView line = lines.get(index);
            int rowY = y + index * 16;
            String title = line.getDisplayName() + " " + line.getDeliveredAmount() + "/" + line.getTargetAmount();
            graphics.drawString(this.font, this.trim(title, width), x, rowY, line.isComplete() ? GOOD : TEXT, false);
            int barX = x;
            int barY = rowY + 10;
            int barWidth = width;
            graphics.fill(barX, barY, barX + barWidth, barY + 3, 0x44342718);
            int targetAmount = Math.max(1, line.getTargetAmount());
            int fillWidth = Math.max(0, Math.min(barWidth, (int) Math.round((line.getDeliveredAmount() / (double) targetAmount) * barWidth)));
            graphics.fill(barX, barY, barX + fillWidth, barY + 3, line.isComplete() ? GOOD : ACCENT);
        }
    }

    private String getLocalHeaderLabel() {
        if (this.localMetadata.hasSourceDetails()) {
            return this.localMetadata.sourceLabel().isEmpty() ? this.localMetadata.sourceType() : this.localMetadata.sourceLabel();
        }
        return "Memory Cog";
    }

    private String getModeLabel() {
        if (!this.activeLink.isLinked()) {
            return "Memory";
        }
        return switch (this.activeLink.getTargetType()) {
            case STATS_DISPLAY -> "Display";
            case STATS_RECORDER -> "Recorder";
            case DISPATCH_BOARD -> "Dispatch";
            default -> "Handheld";
        };
    }

    private String getStatusLabel() {
        if (!this.activeLink.isLinked()) {
            return this.localStore != null ? "Local snapshot ready" : "No local snapshot";
        }
        if (this.remoteState == null) {
            return "Requesting remote state...";
        }
        return switch (this.remoteState.getConnectionState()) {
            case LIVE -> "Connected";
            case NO_DATA -> "Connected, waiting for data";
            case OUT_OF_RANGE -> "Out of range";
            case WRONG_DIMENSION -> "Wrong dimension";
            case MISSING -> "Link target missing";
        };
    }

    private String getBadgeLabel() {
        if (!this.activeLink.isLinked()) {
            return this.localStore != null ? "LOCAL SNAPSHOT READY" : "INSERT MEMORY COG";
        }
        if (this.remoteState == null) {
            return "LINKING...";
        }
        return this.getStatusLabel().toUpperCase(Locale.ROOT);
    }

    private String getAnimatedBadgeLabel() {
        if (!this.isLinking()) {
            return this.getBadgeLabel();
        }
        int dots = (this.animationTicks / 8) % 4;
        return switch (dots) {
            case 1 -> "LINKING.";
            case 2 -> "LINKING..";
            case 3 -> "LINKING...";
            default -> "LINKING";
        };
    }

    private boolean isLinking() {
        return this.activeLink.isLinked() && this.remoteState == null;
    }

    private String getStatusDetail() {
        if (!this.activeLink.isLinked()) {
            if (this.localMetadata.hasSourceDetails()) {
                return this.localMetadata.sourceLabel().isEmpty() ? this.localMetadata.sourceType() : this.localMetadata.sourceLabel();
            }
            return "Hold a Memory Cog in the other hand for playback.";
        }
        if (this.remoteState == null) {
            return this.activeLink.getTargetName();
        }
        String detail = this.remoteState.getStatusText();
        if (detail == null || detail.isEmpty()) {
            detail = this.activeLink.getTargetName();
        }
        if (this.isShowingStaleSnapshot()) {
            detail = "Using last good snapshot";
        }
        return detail;
    }

    private int getStatusColor() {
        if (!this.activeLink.isLinked()) {
            return this.localStore != null ? GOOD : WARN;
        }
        if (this.remoteState == null) {
            return WARN;
        }
        return switch (this.remoteState.getConnectionState()) {
            case LIVE -> GOOD;
            case NO_DATA -> WARN;
            case OUT_OF_RANGE, WRONG_DIMENSION, MISSING -> BAD;
        };
    }

    private boolean isGraphVisible() {
        return !this.isDispatchView() && (this.activeLink.isLinked() || this.localStore != null);
    }

    private boolean isDispatchView() {
        return this.activeLink.isLinked() && this.activeLink.getTargetType() == TargetType.DISPATCH_BOARD;
    }

    private int getSignalBars() {
        return this.remoteState != null ? this.remoteState.getSignalBars() : 0;
    }

    private TimeSeriesStore getActiveGraphStore() {
        if (!this.activeLink.isLinked()) {
            return this.localStore;
        }
        if (this.isDispatchView()) {
            return null;
        }
        if (this.remoteState != null && this.remoteState.getStore() != null) {
            return this.remoteState.getStore();
        }
        return this.staleGraphStore;
    }

    private String getMetricLabel() {
        if (!this.activeLink.isLinked()) {
            return this.getLocalHeaderLabel();
        }
        if (this.remoteState != null && !this.remoteState.getMetricLabel().isEmpty()) {
            return this.remoteState.getMetricLabel();
        }
        if (!this.staleMetricLabel.isEmpty()) {
            return this.staleMetricLabel;
        }
        return this.activeLink.getTargetName();
    }

    private String getLatestValueText() {
        TimeSeriesStore store = this.getActiveGraphStore();
        if (store == null) {
            return "--";
        }
        float[] visible = store.getDataForTimeScale(this.settings.getTimeRange().getSeconds());
        if (visible.length == 0) {
            return "--";
        }
        float latest = visible[visible.length - 1];
        String unit = store.getMetricType().getUnit();
        return unit == null || unit.isEmpty() ? this.formatValue(latest) : this.formatValue(latest) + " " + unit;
    }

    private String getHeroSubtitle() {
        TimeSeriesStore store = this.getActiveGraphStore();
        if (store == null || store.getSampleCount() == 0) {
            return this.getStatusDetail();
        }
        String subtitle = this.getStatusDetail();
        if (subtitle == null || subtitle.isEmpty()) {
            subtitle = String.format(Locale.ROOT, "%d samples", store.getSampleCount());
        }
        return subtitle;
    }

    private boolean isShowingStaleSnapshot() {
        if (!this.activeLink.isLinked() || this.remoteState == null) {
            return false;
        }
        return this.remoteState.getConnectionState() != HandheldStatePacket.ConnectionState.LIVE
            && (this.staleGraphStore != null || this.staleCargoView != null || this.staleDeliveryBoard != null);
    }

    private void drawSignalBars(GuiGraphics graphics, int x, int y, int bars, boolean linked) {
        int activeColor = this.signalPulseTicks > 0 ? ACCENT : (linked ? GOOD : DIM);
        for (int index = 0; index < 4; ++index) {
            int height = 3 + index * 3;
            int color = index < bars ? activeColor : 0x55342718;
            graphics.fill(x + index * 5, y + 12 - height, x + index * 5 + 3, y + 12, color);
        }
    }

    private void renderGraphVisualOverlays(GuiGraphics graphics, int screenLeft, int screenTop) {
        if (!this.isGraphVisible() || this.graphWidget == null || !this.graphWidget.visible) {
            return;
        }
        int graphX = screenLeft + CONTENT_PADDING;
        int graphY = screenTop + GRAPH_TOP;
        int graphWidth = SCREEN_WIDTH - CONTENT_PADDING * 2;
        int graphHeight = GRAPH_HEIGHT;
        if (this.graphFadeTicks > 0) {
            int alpha = Math.max(0, (int) (this.graphFadeTicks / (float) GRAPH_FADE_TICKS * 72.0f));
            graphics.fill(graphX + 1, graphY + 1, graphX + graphWidth - 1, graphY + graphHeight - 1, this.withAlpha(PANEL_INNER, alpha));
        }
        if (this.isShowingStaleSnapshot()) {
            int alpha = 0x18 + (int)(this.pulseWave() * 28.0f);
            graphics.fill(graphX + 1, graphY + 1, graphX + graphWidth - 1, graphY + graphHeight - 1, this.withAlpha(WARN, alpha));
            this.drawInsetBorder(graphics, graphX, graphY, graphWidth, graphHeight, this.withAlpha(WARN, 0xB0));
            graphics.fill(graphX + 4, graphY + 4, graphX + graphWidth - 4, graphY + 5, WARN);
        }
        if (this.cogReadPulseTicks > 0 && !this.activeLink.isLinked()) {
            int alpha = (int) (this.pulseProgress(this.cogReadPulseTicks, COG_READ_PULSE_TICKS) * 110.0f);
            this.drawInsetBorder(graphics, graphX + 2, graphY + 2, graphWidth - 4, graphHeight - 4, this.withAlpha(ACCENT, alpha));
        }
    }

    private void drawInsetBorder(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        graphics.fill(x, y, x + width, y + 1, color);
        graphics.fill(x, y + height - 1, x + width, y + height, color);
        graphics.fill(x, y, x + 1, y + height, color);
        graphics.fill(x + width - 1, y, x + width, y + height, color);
    }

    private float pulseProgress(int remainingTicks, int totalTicks) {
        if (totalTicks <= 0) {
            return 0.0f;
        }
        return Math.max(0.0f, Math.min(1.0f, remainingTicks / (float) totalTicks));
    }

    private float pulseWave() {
        return (float)(Math.sin((this.animationTicks % PULSE_PERIOD_TICKS) * Math.PI * 2.0 / PULSE_PERIOD_TICKS) * 0.5 + 0.5);
    }

    private String trim(String text, int width) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        return this.font.plainSubstrByWidth(text, Math.max(10, width));
    }

    private int panelLeft() {
        return (this.width - PANEL_WIDTH) / 2;
    }

    private int panelTop() {
        return (this.height - PANEL_HEIGHT) / 2;
    }

    private int screenLeft() {
        return this.panelLeft() + SCREEN_INSET_X;
    }

    private int screenTop() {
        return this.panelTop() + SCREEN_INSET_TOP;
    }

    private String formatCargoName(FreightManifestResourceKey key) {
        ResourceLocation resourceId = key.getResourceId();
        if (key.getCargoKind() == ManifestCargoKind.ITEM && BuiltInRegistries.ITEM.containsKey(resourceId)) {
            return BuiltInRegistries.ITEM.get(resourceId).getDescription().getString();
        }
        if (key.getCargoKind() == ManifestCargoKind.FLUID && BuiltInRegistries.FLUID.containsKey(resourceId)) {
            Fluid fluid = BuiltInRegistries.FLUID.get(resourceId);
            return fluid.getFluidType().getDescription().getString();
        }
        return resourceId.getPath().replace('_', ' ');
    }

    private String buildLinkKey(HandheldLinkData link) {
        if (!link.isLinked() || link.getTargetPos() == null || link.getDimensionId() == null) {
            return "";
        }
        return link.getTargetType().name() + "|" + link.getDimensionId() + "|" + link.getTargetPos().asLong();
    }

    private String buildRemoteSignature(HandheldStatePacket packet) {
        TimeSeriesStore store = packet.getStore();
        String storeKey = store == null ? "no-store" : store.getMetricType().name();
        return packet.getConnectionState().name() + "|" + packet.getTargetName() + "|" + packet.getMetricLabel() + "|" + packet.getStatusText() + "|" + packet.getSignalBars() + "|" + storeKey;
    }

    private String buildLocalSnapshotKey(TimeSeriesStore store, SnapshotMetadata metadata) {
        if (store == null || store.getSampleCount() == 0) {
            return "";
        }
        String sourceKey = metadata == null ? "" : metadata.sourceLabel() + "|" + metadata.sourceType() + "|" + metadata.dimensionId() + "|" + metadata.capturedGameTime();
        return store.getMetricType().name() + "|" + store.getSampleCount() + "|" + store.getLatestValue() + "|" + sourceKey;
    }

    private String buildGraphVisualKey(TimeSeriesStore store, boolean visible) {
        if (!visible || store == null || store.getSampleCount() == 0) {
            return visible ? "visible-empty" : "hidden";
        }
        String source = this.activeLink.isLinked() ? this.activeLinkKey : this.lastLocalSnapshotKey;
        String connection = this.remoteState != null ? this.remoteState.getConnectionState().name() : "local";
        return source + "|" + connection + "|" + store.getMetricType().name() + "|" + this.settings.getTimeRange().name() + "|" + this.settings.getGraphType().name();
    }

    private boolean isHandheldGraphType(StatsDisplaySettings.GraphType graphType) {
        for (StatsDisplaySettings.GraphType supported : HANDHELD_GRAPH_TYPES) {
            if (supported == graphType) {
                return true;
            }
        }
        return false;
    }

    private StatsDisplaySettings.GraphType nextHandheldGraphType(StatsDisplaySettings.GraphType current) {
        int currentIndex = -1;
        for (int index = 0; index < HANDHELD_GRAPH_TYPES.length; ++index) {
            if (HANDHELD_GRAPH_TYPES[index] == current) {
                currentIndex = index;
                break;
            }
        }
        return HANDHELD_GRAPH_TYPES[(currentIndex + 1) % HANDHELD_GRAPH_TYPES.length];
    }

    private String shortGraphTypeLabel() {
        return switch (this.settings.getGraphType()) {
            case LINE_CHART -> "Line";
            case BAR_CHART -> "Bars";
            case GAUGE -> "Gauge";
            case NUMERIC -> "Num";
            case CAPACITY -> "Cap";
            case PRODUCTION -> "Prod";
            case ANALOG_GAUGE -> "Dial";
            case EFFICIENCY -> "Eff";
            case TOPOLOGY -> "Map";
            case TRAIN_MAP -> "Train";
            case INVENTORY_STATUS -> "Inv";
            case FACTORY_STATUS -> "Factory";
        };
    }

    private String shortTimeRangeLabel() {
        return switch (this.settings.getTimeRange()) {
            case ONE_MIN -> "1m";
            case FIVE_MIN -> "5m";
            case TEN_MIN -> "10m";
            case FULL -> "All";
        };
    }

    private String formatValue(float value) {
        if (Float.isNaN(value) || Float.isInfinite(value)) {
            return "--";
        }
        float abs = Math.abs(value);
        if (abs >= 1000.0f) {
            return String.format(Locale.ROOT, "%.0f", value);
        }
        if (abs >= 100.0f) {
            return String.format(Locale.ROOT, "%.1f", value);
        }
        if (abs >= 10.0f) {
            return String.format(Locale.ROOT, "%.1f", value);
        }
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private int withAlpha(int color, int alpha) {
        return (alpha << 24) | (color & 0x00FFFFFF);
    }
}
