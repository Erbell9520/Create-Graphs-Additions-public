package com.createstats.client;

import com.createstats.blockentity.ProductionDetectorBE;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.core.Direction;
import java.util.List;

@OnlyIn(Dist.CLIENT)
public class ProductionDetectorScreen extends Screen
{
    private static final int GUI_VISIBLE_ITEM_ROWS = 8;
    private static final float ENERGY_FLOW_EPSILON = 0.01f;
    private final ProductionDetectorBE detector;
    private final int bg;
    private final int text;
    private final int grid;
    private final int accent;
    private final int dim;
    private final int border;
    private int panelLeft;
    private int panelTop;
    private int panelWidth;
    private int panelHeight;
    private int itemScrollOffset;

    public ProductionDetectorScreen(ProductionDetectorBE detector) {
        super(Component.translatable("block.create_stats.production_detector"));
        this.detector = detector;
        this.bg = 0xFF121719;
        this.text = 0xFFF5E5C5;
        this.grid = 0xFF234A4C;
        this.accent = 0xFF42E3D4;
        this.dim = 0xFFB8A37F;
        this.border = 0xFFC69B4A;
    }

    protected void init() {
        this.panelWidth = Math.min(430, Math.max(1, this.width - 24));
        this.panelHeight = Math.min(290, Math.max(1, this.height - 24));
        this.panelLeft = (this.width - this.panelWidth) / 2;
        this.panelTop = (this.height - this.panelHeight) / 2;
        this.itemScrollOffset = 0;

        this.addRenderableWidget(net.minecraft.client.gui.components.Button.builder(
            Component.literal("Manual"),
            b -> net.minecraft.client.Minecraft.getInstance().setScreen(new com.createstats.gui.FactoryManualScreen("production_detector")))
            .bounds(this.panelLeft + 112, this.panelTop + 5, 58, 16).build());

        // Source mode toggle buttons (Round 3)
        final int btnY = this.panelTop + 50;
        this.addRenderableWidget(net.minecraft.client.gui.components.Button.builder(
            Component.literal("Direct"),
            b -> this.sendSourceMode(ProductionDetectorBE.SourceMode.DIRECT))
            .bounds(this.panelLeft + 10, btnY, 64, 16).build());
        this.addRenderableWidget(net.minecraft.client.gui.components.Button.builder(
            Component.literal("Bridge"),
            b -> this.sendSourceMode(ProductionDetectorBE.SourceMode.BRIDGE))
            .bounds(this.panelLeft + 78, btnY, 64, 16).build());
        if (this.shouldShowDieselStructureButtons()) {
            final int dieselX = this.panelLeft + this.panelWidth - 146;
            this.addRenderableWidget(net.minecraft.client.gui.components.Button.builder(
                Component.literal("Single"),
                b -> this.sendDieselStructureMode(false))
                .bounds(dieselX, btnY, 68, 16).build());
            this.addRenderableWidget(net.minecraft.client.gui.components.Button.builder(
                Component.literal("Multi"),
                b -> this.sendDieselStructureMode(true))
                .bounds(dieselX + 72, btnY, 68, 16).build());
        }
    }

    private void sendSourceMode(ProductionDetectorBE.SourceMode mode) {
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(
            new com.createstats.network.ProductionDetectorSourceModePacket(this.detector.getBlockPos(), mode));
    }

    private void sendDieselStructureMode(boolean multi) {
        this.detector.setDieselMultiStructureMode(multi);
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(
            new com.createstats.network.ProductionDetectorDieselModePacket(this.detector.getBlockPos(), multi));
    }

    private void renderSourceModeHighlight(GuiGraphics g) {
        final int btnY = this.panelTop + 50;
        final boolean direct = this.detector.getSourceMode() == ProductionDetectorBE.SourceMode.DIRECT;
        // Highlight the active button with a thin accent bar above it.
        if (direct) {
            g.fill(this.panelLeft + 10, btnY - 2, this.panelLeft + 74, btnY - 1, this.accent);
        } else {
            g.fill(this.panelLeft + 78, btnY - 2, this.panelLeft + 142, btnY - 1, this.accent);
        }
        if (!this.shouldShowDieselStructureButtons()) {
            return;
        }
        final int dieselX = this.panelLeft + this.panelWidth - 146;
        if (this.detector.isDieselMultiStructureMode()) {
            g.fill(dieselX + 72, btnY - 2, dieselX + 140, btnY - 1, 0xFFE6A342);
        } else {
            g.fill(dieselX, btnY - 2, dieselX + 68, btnY - 1, 0xFFE6A342);
        }
    }

    private void renderBridgeStatus(GuiGraphics g, int x, int y) {
        final ProductionDetectorBE.SourceMode src = this.detector.getSourceMode();
        if (src == ProductionDetectorBE.SourceMode.DIRECT) {
            g.fill(x, y, x + 8, y + 8, 0xFF6E6457);
            g.drawString(this.font, "Direct mode", x + 14, y, this.dim);
            return;
        }
        final ProductionDetectorBE.BridgeStatus bs = this.detector.getBridgeStatus();
        int color;
        String label;
        switch (bs) {
            case OK -> { color = 0xFF55C273; label = "Bridged"; }
            case OUT_OF_RANGE -> { color = 0xFFE0A23A; label = "Out of range"; }
            case UNLOADED -> { color = 0xFFE0A23A; label = "Source unloaded"; }
            case NO_BRIDGE_FOUND -> { color = 0xFFD05050; label = "No DL/Observer pointing here"; }
            case NO_CAPABILITY -> { color = 0xFFD05050; label = "Source has no readable data"; }
            case CYCLE -> { color = 0xFFD05050; label = "Display Link cycle"; }
            case INVALID_LINK -> { color = 0xFFD05050; label = "Invalid link"; }
            default -> { color = 0xFF6E6457; label = "Searching for bridge..."; }
        }
        g.fill(x, y, x + 8, y + 8, color);
        String txt = label;
        if (bs == ProductionDetectorBE.BridgeStatus.OK) {
            final String name = this.detector.getBridgeResolvedName();
            if (name != null && !name.isEmpty()) txt = label + " → " + name;
        }
        final int reserveRight = this.shouldShowDieselStructureButtons() ? 150 : 0;
        final int maxW = this.panelLeft + this.panelWidth - 10 - reserveRight - (x + 14);
        g.drawString(this.font, this.truncate(txt, maxW), x + 14, y, this.text);
    }

    private boolean shouldShowDieselStructureButtons() {
        final ProductionDetectorBE.DieselSnapshot snapshot = this.detector.getDieselSnapshot();
        return snapshot != null && snapshot.sourceType().contains("Engine");
    }

    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderTransparentBackground(g);
        // FE Dependent Mode panel in the free screen corner (self-gating).
        com.createstats.power.client.FeStatusWidget.draw(g, this.font, this.width - 130, 8, 120, this.detector.feConsumer());
        g.fill(this.panelLeft, this.panelTop, this.panelLeft + this.panelWidth, this.panelTop + this.panelHeight, this.bg);
        this.border(g, this.panelLeft, this.panelTop, this.panelWidth, this.panelHeight, this.border);
        this.renderPanelPattern(g, this.panelLeft + 6, this.panelTop + 6, this.panelWidth - 12, this.panelHeight - 12);
        g.fill(this.panelLeft + 4, this.panelTop + 4, this.panelLeft + this.panelWidth - 4, this.panelTop + 6, 0xFF6D5122);
        g.drawString(this.font, "Inventory Display", this.panelLeft + 10, this.panelTop + 8, this.border);

        final String targetName = this.detector.getMonitoredTargetName();
        final String status = this.detector.getTargetStatusSummary();
        final Direction facing = (Direction)this.detector.getBlockState().getValue(com.createstats.block.ProductionDetectorBlock.FACING);
        final String facingName = facing.getName().substring(0, 1).toUpperCase() + facing.getName().substring(1);
        final String coordStr = String.format("(%d, %d, %d)", this.detector.getBlockPos().getX(), this.detector.getBlockPos().getY(), this.detector.getBlockPos().getZ());
        final int coordW = this.font.width(coordStr);
        g.drawString(this.font, coordStr, this.panelLeft + this.panelWidth - coordW - 10, this.panelTop + 8, this.dim);

        g.fill(this.panelLeft + 6, this.panelTop + 26, this.panelLeft + this.panelWidth - 6, this.panelTop + 27, this.border);
        this.renderStatusPill(g, this.panelLeft + 10, this.panelTop + 32);
        g.drawString(this.font, this.truncate(status, this.panelWidth - 140), this.panelLeft + 92, this.panelTop + 35, this.dim);

        final int summaryY = this.panelTop + 76;
        final int gap = 6;
        final int cardW = (this.panelWidth - 20 - gap * 2) / 3;
        final ProductionDetectorBE.EnergySnapshot energySnapshot = this.detector.getEnergySnapshot();
        final ProductionDetectorBE.DieselSnapshot dieselSnapshot = this.detector.getDieselSnapshot();
        if (this.detector.getDisplayMode() == ProductionDetectorBE.DisplayMode.DIESEL && dieselSnapshot != null) {
            this.renderSummaryCard(g, this.panelLeft + 10, summaryY, cardW, "Fuel", String.format("%.0f%%", dieselSnapshot.fillPercent()));
            this.renderSummaryCard(g, this.panelLeft + 10 + cardW + gap, summaryY, cardW, "Output", this.formatDieselOutput(dieselSnapshot));
            this.renderSummaryCard(g, this.panelLeft + 10 + (cardW + gap) * 2, summaryY, cardW, "Plant", this.truncate(dieselSnapshot.sourceType(), cardW - 8));
        } else if (this.detector.getDisplayMode() == ProductionDetectorBE.DisplayMode.ENERGY && energySnapshot != null) {
            this.renderSummaryCard(g, this.panelLeft + 10, summaryY, cardW, "Charge", String.format("%.0f%%", energySnapshot.fillPercent()));
            this.renderSummaryCard(g, this.panelLeft + 10 + cardW + gap, summaryY, cardW, "Stored", this.formatEnergy(energySnapshot.stored()));
            this.renderSummaryCard(g, this.panelLeft + 10 + (cardW + gap) * 2, summaryY, cardW, "Net", this.formatEnergyRate(energySnapshot.netRate()));
        } else {
            this.renderSummaryCard(g, this.panelLeft + 10, summaryY, cardW, "Total", String.format("%,d", this.detector.getTotalItemsSeen()));
            this.renderSummaryCard(g, this.panelLeft + 10 + cardW + gap, summaryY, cardW, "Types", String.valueOf(this.detector.getTrackedItemTypeCount()));
            this.renderSummaryCard(g, this.panelLeft + 10 + (cardW + gap) * 2, summaryY, cardW, "Target", this.truncate(targetName, cardW - 8));
        }

        // Round 3: source mode highlight + bridge status pill
        this.renderSourceModeHighlight(g);
        this.renderBridgeStatus(g, this.panelLeft + 150, this.panelTop + 52);

        final int bodyY = this.panelTop + 120;
        final int bodyH = this.panelHeight - 130;
        switch (this.detector.getDisplayMode()) {
            case FLUID -> this.renderFluidMode(g, this.panelLeft + 10, bodyY, this.panelWidth - 20, bodyH);
            case ENERGY -> this.renderEnergyMode(g, this.panelLeft + 10, bodyY, this.panelWidth - 20, bodyH);
            case DIESEL -> this.renderDieselMode(g, this.panelLeft + 10, bodyY, this.panelWidth - 20, bodyH);
            case DISPLAY_LINK_FEED -> this.renderDisplayLinkMode(g, this.panelLeft + 10, bodyY, this.panelWidth - 20, bodyH);
            default -> {
                final int listW = (int)((this.panelWidth - 26) * 0.64f);
                this.renderTopItems(g, this.panelLeft + 10, bodyY, listW, bodyH);
                this.renderDetails(g, this.panelLeft + 16 + listW, bodyY, this.panelWidth - listW - 26, bodyH, targetName, facingName);
            }
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    private void renderFluidMode(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, 0xCC062022);
        this.border(g, x, y, w, h, this.grid);
        g.drawString(this.font, "Fluid Contents", x + 6, y + 6, this.accent);
        final List<ProductionDetectorBE.FluidEntry> fluids = this.detector.getFluidEntriesSnapshot();
        final List<ProductionDetectorBE.SyntheticContentEntry> synthetic = this.detector.getSyntheticContentSnapshot();
        ProductionDetectorBE.BoilerSnapshot bs = this.detector.getBoilerSnapshot();
        boolean hasFluid = !fluids.isEmpty();
        boolean hasSynthetic = !synthetic.isEmpty();
        boolean hasBoiler = bs != null && bs.active();
        if (!hasFluid && !hasSynthetic && !hasBoiler) {
            g.drawCenteredString(this.font, "No fluid detected", x + w / 2, y + h / 2 - 4, 0xFFD6A84F);
            return;
        }
        String name; long amount; long capacity; String fluidId;
        if (hasFluid) {
            ProductionDetectorBE.FluidEntry primary = fluids.get(0);
            name = primary.displayName(); amount = primary.amount(); capacity = primary.capacity();
            fluidId = primary.fluidId();
        } else if (hasSynthetic) {
            ProductionDetectorBE.SyntheticContentEntry s0 = synthetic.get(0);
            name = s0.label(); amount = s0.amount(); capacity = s0.capacity();
            fluidId = "synthetic:" + s0.id();
        } else {
            name = "Boiler"; amount = 0; capacity = 0; fluidId = "minecraft:water";
        }
        int capX = x + w / 4;
        int capW = w / 2;
        int capY = y + 28;
        int capH = h - 90;
        g.fill(capX, capY, capX + capW, capY + capH, 0xFF050B0E);
        this.border(g, capX, capY, capW, capH, this.border);
        if (capacity > 0) {
            float ratio = Math.min(1f, amount / (float) capacity);
            long ms = net.minecraft.Util.getMillis();
            int fillTop = capY + capH - (int)(capH * ratio);
            // Round 3: per-fluid color from FluidPalette (alpha forced to 0xCC for translucent fill)
            int argb = com.createstats.renderer.FluidPalette.colorForFluidId(fluidId);
            int fillColor = 0xCC000000 | (argb & 0x00FFFFFF);
            for (int col = 0; col < capW - 4; col++) {
                float wave = (float) Math.sin((ms / 80.0) + col * 0.18) * 1.6f;
                int top = (int)(fillTop + wave);
                g.fill(capX + 2 + col, top, capX + 3 + col, capY + capH - 2, fillColor);
            }
            int pct = Math.round(100f * amount / capacity);
            g.drawCenteredString(this.font, String.format("%,d / %,d mB", amount, capacity), x + w / 2, capY + capH + 6, this.accent);
            g.drawCenteredString(this.font, pct + "%", x + w / 2, capY + capH + 18, 0xFF58D575);
        }
        g.drawCenteredString(this.font, name, x + w / 2, capY - 14, this.text);
        if (hasBoiler) {
            int by = y + h - 30;
            g.fill(x + 6, by, x + w - 6, by + 24, 0xCC1A2628);
            this.border(g, x + 6, by, w - 12, 24, this.grid);
            g.drawString(this.font, "Heat " + bs.activeHeat() + "  Engines " + bs.engines() + "  Whistles " + bs.whistles(), x + 12, by + 4, 0xFFD6A84F);
            g.drawString(this.font, "Water " + String.format("%,d mB/t", bs.waterSupply()), x + 12, by + 14, this.accent);
        }
    }

    private void renderDisplayLinkMode(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, 0xCC062022);
        this.border(g, x, y, w, h, this.grid);
        g.drawString(this.font, "Display Link Feed", x + 6, y + 6, this.accent);
        final List<ProductionDetectorBE.DisplayLinkLine> lines = this.detector.getDisplayLinkLinesSnapshot();
        if (lines.isEmpty()) {
            g.drawCenteredString(this.font, "Waiting for data...", x + w / 2, y + h / 2 - 4, 0xFFD6A84F);
            return;
        }
        int rowY = y + 24;
        for (ProductionDetectorBE.DisplayLinkLine line : lines) {
            g.drawString(this.font, this.truncate(line.plainText(), w - 12), x + 6, rowY, this.text);
            rowY += 12;
            if (rowY > y + h - 6) break;
        }
    }

    private void renderEnergyMode(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, 0xCC062022);
        this.border(g, x, y, w, h, this.grid);
        g.drawString(this.font, "Energy Circuit", x + 6, y + 6, 0xFFE6A342);
        final ProductionDetectorBE.EnergySnapshot snapshot = this.detector.getEnergySnapshot();
        if (snapshot == null) {
            g.drawCenteredString(this.font, "No energy detected", x + w / 2, y + h / 2 - 4, 0xFFD6A84F);
            return;
        }

        g.drawString(this.font, this.truncate(snapshot.sourceType(), w - 120), x + 6, y + 19, this.dim);
        g.drawString(this.font, String.format("%.1f%%", snapshot.fillPercent()), x + w - this.font.width(String.format("%.1f%%", snapshot.fillPercent())) - 8, y + 19, this.energyColor(snapshot));

        final int batteryX = x + 18;
        final int batteryY = y + 38;
        final int batteryW = w - 36;
        final int batteryH = Math.max(34, Math.min(48, h / 3));
        this.drawEnergyBatteryGui(g, batteryX, batteryY, batteryW, batteryH, snapshot);
        g.drawCenteredString(this.font, this.formatEnergy(snapshot.stored()) + " / " + this.formatEnergy(snapshot.capacity()), x + w / 2, batteryY + batteryH + 6, this.accent);

        final int railY = batteryY + batteryH + 34;
        final int nodeW = Math.max(58, w / 5);
        final int nodeH = 36;
        final int coreW = Math.max(70, w / 4);
        final int leftNodeX = x + 10;
        final int coreX = x + w / 2 - coreW / 2;
        final int rightNodeX = x + w - nodeW - 10;
        this.drawEnergyNodeGui(g, leftNodeX, railY - nodeH / 2, nodeW, nodeH, "IN", this.formatEnergyRate(snapshot.inputRate()), 0xFF58D575);
        this.drawEnergyNodeGui(g, coreX, railY - nodeH / 2 - 4, coreW, nodeH + 8, "NET", this.formatEnergyRate(snapshot.netRate()), this.energyColor(snapshot));
        this.drawEnergyNodeGui(g, rightNodeX, railY - nodeH / 2, nodeW, nodeH, "OUT", this.formatEnergyRate(snapshot.outputRate()), 0xFFD6A84F);
        this.drawEnergyFlowGui(g, leftNodeX + nodeW, coreX, railY, true, this.hasInputFlow(snapshot), 0xFF58D575);
        this.drawEnergyFlowGui(g, coreX + coreW, rightNodeX, railY, true, this.hasOutputFlow(snapshot), 0xFFD6A84F);

        final int stripY = y + h - 28;
        this.drawEnergyConnectorStripGui(g, x + 10, stripY, w - 20, 20, snapshot);
    }

    private void renderDieselMode(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, 0xCC062022);
        this.border(g, x, y, w, h, this.grid);
        g.drawString(this.font, "Diesel Plant", x + 6, y + 6, 0xFFE6A342);
        final ProductionDetectorBE.DieselSnapshot snapshot = this.detector.getDieselSnapshot();
        if (snapshot == null) {
            g.drawCenteredString(this.font, "No diesel plant detected", x + w / 2, y + h / 2 - 4, 0xFFD6A84F);
            return;
        }

        final int stateColor = this.dieselColor(snapshot);
        g.drawString(this.font, this.truncate(snapshot.sourceType(), w - 130), x + 6, y + 19, this.dim);
        final String state = this.dieselStateLabel(snapshot);
        g.drawString(this.font, state, x + w - this.font.width(state) - 8, y + 19, stateColor);

        if ("Diesel Distillation Tower".equals(snapshot.sourceType())) {
            this.renderDistillationTowerMode(g, x, y, w, h, snapshot, state, stateColor);
            return;
        }
        if ("Oil Pumpjack".equals(snapshot.sourceType())) {
            this.renderPumpjackMode(g, x, y, w, h, snapshot, state, stateColor);
            return;
        }
        if (snapshot.sourceType().contains("Engine")) {
            this.renderDieselEngineMode(g, x, y, w, h, snapshot, state, stateColor);
            return;
        }

        final int gaugeX = x + 18;
        final int gaugeY = y + 38;
        final int gaugeW = w - 36;
        final int gaugeH = Math.max(30, Math.min(44, h / 3));
        this.drawDieselFuelGaugeGui(g, gaugeX, gaugeY, gaugeW, gaugeH, snapshot);
        g.drawCenteredString(this.font, this.formatFluidAmount(snapshot.fluidAmount()) + " / " + this.formatFluidAmount(snapshot.fluidCapacity()), x + w / 2, gaugeY + gaugeH + 6, this.accent);

        final int railY = gaugeY + gaugeH + 34;
        final int nodeW = Math.max(58, w / 5);
        final int nodeH = 36;
        final int coreW = Math.max(78, w / 4);
        final int leftNodeX = x + 10;
        final int coreX = x + w / 2 - coreW / 2;
        final int rightNodeX = x + w - nodeW - 10;
        this.drawDieselNodeGui(g, leftNodeX, railY - nodeH / 2, nodeW, nodeH, this.dieselInputLabel(snapshot), this.dieselInputValue(snapshot), this.dieselFillColor(snapshot));
        this.drawDieselNodeGui(g, coreX, railY - nodeH / 2 - 4, coreW, nodeH + 8, this.dieselCoreLabel(snapshot), state, stateColor);
        this.drawDieselNodeGui(g, rightNodeX, railY - nodeH / 2, nodeW, nodeH, this.dieselOutputLabel(snapshot), this.dieselOutputValue(snapshot), this.accent);
        this.drawEnergyFlowGui(g, leftNodeX + nodeW, coreX, railY, true, snapshot.fillPercent() > 0.0f || snapshot.oilRemaining() > 0.0f, this.dieselFillColor(snapshot));
        this.drawEnergyFlowGui(g, coreX + coreW, rightNodeX, railY, true, snapshot.hasActivity(), this.accent);

        final int stripY = y + h - 28;
        g.fill(x + 10, stripY, x + w - 10, stripY + 20, 0xCC081A1D);
        this.border(g, x + 10, stripY, w - 20, 20, this.grid);
        g.drawString(this.font, "Stress " + this.formatCompact(snapshot.generatedStress()) + " SU", x + 16, stripY + 6, snapshot.generatedStress() > 0.0f ? 0xFF58D575 : this.dim);
        final String right = snapshot.sourceType().contains("Group")
            ? "OK " + Math.round(snapshot.okEngines()) + "  Stall " + Math.round(snapshot.stallingEngines()) + "  Off " + Math.round(snapshot.offEngines())
            : "Heat " + this.formatCompact(snapshot.heatLevel()) + "  Blocks " + Math.round(snapshot.engineCount());
        g.drawString(this.font, right, x + w - this.font.width(right) - 16, stripY + 6, this.dim);
    }

    private void renderDistillationTowerMode(GuiGraphics g, int x, int y, int w, int h, ProductionDetectorBE.DieselSnapshot snapshot, String state, int stateColor) {
        final int top = y + 36;
        final int bottom = y + h - 31;
        final int columnW = Math.max(54, w / 6);
        final int columnX = x + w / 2 - columnW / 2;
        final int columnY = top + 4;
        final int columnH = Math.max(76, bottom - columnY);

        g.fill(columnX, columnY, columnX + columnW, columnY + columnH, 0xFF071B1D);
        this.border(g, columnX, columnY, columnW, columnH, this.border);
        final int fillH = Math.round((columnH - 8) * Math.max(0.0f, Math.min(1.0f, snapshot.fillPercent() / 100.0f)));
        if (fillH > 0) {
            g.fill(columnX + 4, columnY + columnH - 4 - fillH, columnX + columnW - 4, columnY + columnH - 4, 0xCC9D6525);
            g.fill(columnX + 4, columnY + columnH - 4 - fillH, columnX + columnW - 4, columnY + columnH - 2 - fillH, 0xFFFFD27A);
        }
        for (int tray = 1; tray < 6; tray++) {
            final int trayY = columnY + tray * columnH / 6;
            g.fill(columnX + 7, trayY, columnX + columnW - 7, trayY + 2, tray % 2 == 0 ? 0xFF6D5122 : 0xFF42E3D4);
        }
        if (snapshot.processing()) {
            final long tick = net.minecraft.Util.getMillis() / 50L;
            final int span = Math.max(12, columnH - 18);
            for (int bubble = 0; bubble < 6; bubble++) {
                final int bubbleY = columnY + columnH - 12 - Math.floorMod((int)(tick * 2L + bubble * 9), span);
                final int bubbleX = columnX + 12 + (bubble % 3) * Math.max(8, (columnW - 24) / 3);
                g.fill(bubbleX, bubbleY, bubbleX + 4, bubbleY + 4, stateColor);
            }
        }

        final int nodeW = Math.max(70, w / 5);
        final int feedX = x + 14;
        final int crudeY = columnY + 18;
        final int heatY = columnY + columnH - 40;
        this.drawDieselNodeGui(g, feedX, crudeY, nodeW, 34, "CRUDE FEED", String.format("%.0f%% full", snapshot.fillPercent()), this.dieselFillColor(snapshot));
        this.drawDieselNodeGui(g, feedX, heatY, nodeW, 34, "BOILER HEAT", this.formatCompact(snapshot.heatLevel()), snapshot.heatLevel() > 0.0f ? 0xFFD6A84F : this.dim);
        this.drawDieselPipeGui(g, feedX + nodeW, columnX, crudeY + 17, snapshot.fillPercent() > 0.0f, this.dieselFillColor(snapshot), true);
        this.drawDieselPipeGui(g, feedX + nodeW, columnX, heatY + 17, snapshot.heatLevel() > 0.0f, 0xFFD6A84F, true);

        final int outX = x + w - nodeW - 14;
        final String[] outputs = {"GASOLINE", "DIESEL"};
        for (int idx = 0; idx < outputs.length; idx++) {
            final int outY = columnY + 22 + idx * Math.max(42, (columnH - 42) / 2);
            this.drawDieselPipeGui(g, columnX + columnW, outX, outY + 15, snapshot.processing(), this.accent, true);
            this.drawDieselNodeGui(g, outX, outY, nodeW, 30, outputs[idx], snapshot.processing() ? "FLOW" : "HOLD", snapshot.processing() ? this.accent : this.dim);
        }

        final int statusY = y + h - 28;
        g.fill(x + 10, statusY, x + w - 10, statusY + 20, 0xCC081A1D);
        this.border(g, x + 10, statusY, w - 20, 20, this.grid);
        g.drawString(this.font, "Fill " + String.format("%.0f%%", snapshot.fillPercent()) + "  Process " + String.format("%.0f%%", snapshot.processPercent()), x + 16, statusY + 6, stateColor);
        final String right = "Heat " + this.formatCompact(snapshot.heatLevel()) + "  Tanks " + Math.round(snapshot.engineCount());
        g.drawString(this.font, right, x + w - this.font.width(right) - 16, statusY + 6, this.dim);
    }

    private void renderPumpjackMode(GuiGraphics g, int x, int y, int w, int h, ProductionDetectorBE.DieselSnapshot snapshot, String state, int stateColor) {
        final int top = y + 36;
        final int groundY = y + h - 58;
        g.fill(x + 10, groundY, x + w - 10, groundY + 4, 0xFF6D5122);
        g.fill(x + 10, groundY + 4, x + w - 10, groundY + 34, 0xFF1A1410);
        for (int soil = x + 18; soil < x + w - 18; soil += 16) {
            g.fill(soil, groundY + 12, soil + 8, groundY + 14, 0xFF3D2A1B);
        }

        final int pivotX = x + w / 2;
        final int pivotY = top + 54;
        g.fill(pivotX - 4, pivotY, pivotX + 4, groundY, 0xFF6D5122);
        g.fill(pivotX - 24, pivotY + 10, pivotX + 24, pivotY + 16, this.border);
        final int bob = snapshot.processing() ? Math.floorMod((int)(net.minecraft.Util.getMillis() / 50L), 28) - 14 : 0;
        this.drawSlopedBeam(g, pivotX - 88, pivotY + bob / 3, pivotX + 78, pivotY - bob / 4, this.border);
        g.fill(pivotX + 66, pivotY - 4 - bob / 4, pivotX + 82, pivotY + 21 - bob / 4, 0xFFD6A84F);
        g.fill(pivotX - 96, pivotY + bob / 3 + 8, pivotX - 80, pivotY + bob / 3 + 32, this.accent);
        g.fill(pivotX - 90, pivotY + bob / 3 + 32, pivotX - 85, groundY + 8, this.accent);
        g.fill(pivotX - 96, groundY + 4, pivotX - 79, groundY + 13, this.accent);

        final int depositW = Math.max(100, w / 3);
        final int depositX = x + w / 2 - depositW / 2;
        final int depositY = groundY + 17;
        g.fill(depositX, depositY, depositX + depositW, depositY + 14, 0xFF090D0F);
        final int depositFill = Math.round((depositW - 8) * Math.max(0.05f, Math.min(1.0f, snapshot.oilRemaining() / Math.max(1.0f, snapshot.oilRemaining() + 20000.0f))));
        g.fill(depositX + 4, depositY + 3, depositX + 4 + depositFill, depositY + 11, 0xCC9D6525);
        this.border(g, depositX, depositY, depositW, 14, 0xFF6D5122);

        final int nodeW = Math.max(78, w / 5);
        this.drawDieselNodeGui(g, x + 14, top + 8, nodeW, 36, "OIL FIELD", this.formatFluidAmount(snapshot.oilRemaining()), snapshot.oilRemaining() > 0.0f ? 0xFFD6A84F : this.dim);
        this.drawDieselNodeGui(g, x + w / 2 - nodeW / 2, top + 8, nodeW, 36, "PUMP", state, stateColor);
        this.drawDieselNodeGui(g, x + w - nodeW - 14, top + 8, nodeW, 36, "PIPE", this.formatCompact(snapshot.pipeLength()) + " blocks", snapshot.validFuel() ? 0xFF58D575 : 0xFFD6A84F);
        this.drawDieselPipeGui(g, x + 14 + nodeW, x + w / 2 - nodeW / 2, top + 26, snapshot.oilRemaining() > 0.0f, 0xFFD6A84F, true);
        this.drawDieselPipeGui(g, x + w / 2 + nodeW / 2, x + w - nodeW - 14, top + 26, snapshot.processing(), this.accent, true);

        final int statusY = y + h - 28;
        g.fill(x + 10, statusY, x + w - 10, statusY + 20, 0xCC081A1D);
        this.border(g, x + 10, statusY, w - 20, 20, this.grid);
        g.drawString(this.font, "Deposit " + this.formatFluidAmount(snapshot.oilRemaining()), x + 16, statusY + 6, snapshot.oilRemaining() > 0.0f ? 0xFFD6A84F : this.dim);
        final String right = (snapshot.validFuel() ? "Pipe valid" : "Pipe invalid") + "  Length " + this.formatCompact(snapshot.pipeLength());
        g.drawString(this.font, right, x + w - this.font.width(right) - 16, statusY + 6, snapshot.validFuel() ? 0xFF58D575 : 0xFFD6A84F);
    }

    private void renderDieselEngineMode(GuiGraphics g, int x, int y, int w, int h, ProductionDetectorBE.DieselSnapshot snapshot, String state, int stateColor) {
        final int top = y + 36;
        final int statusH = 32;
        final int bottom = y + h - statusH - 9;
        final int tankX = x + 14;
        final int tankY = top + 8;
        final int tankW = Math.max(54, w / 7);
        final int tankH = Math.max(64, bottom - tankY - 8);
        g.fill(tankX, tankY, tankX + tankW, tankY + tankH, 0xFF050B0E);
        this.border(g, tankX, tankY, tankW, tankH, this.border);
        final int tankFill = Math.round((tankH - 10) * Math.max(0.0f, Math.min(1.0f, snapshot.fillPercent() / 100.0f)));
        if (tankFill > 0) {
            g.fill(tankX + 5, tankY + tankH - 5 - tankFill, tankX + tankW - 5, tankY + tankH - 5, 0xCC9D6525);
            g.fill(tankX + 5, tankY + tankH - 5 - tankFill, tankX + tankW - 5, tankY + tankH - 3 - tankFill, 0xFFFFD27A);
        }
        g.drawCenteredString(this.font, "FUEL", tankX + tankW / 2, tankY + 7, this.dim);
        g.drawCenteredString(this.font, String.format("%.0f%%", snapshot.fillPercent()), tankX + tankW / 2, tankY + tankH - 15, this.dieselFillColor(snapshot));

        final int engineX = tankX + tankW + 28;
        final int statsW = Math.max(94, w / 4);
        final int engineW = Math.max(112, x + w - engineX - statsW - 28);
        final int engineY = top + 18;
        final int engineH = Math.max(64, bottom - engineY - 8);
        g.fill(engineX, engineY, engineX + engineW, engineY + engineH, 0xDD081A1D);
        this.border(g, engineX, engineY, engineW, engineH, stateColor);
        g.drawCenteredString(this.font, snapshot.sourceType().contains("Group") ? "ENGINE GROUP" : "DIESEL ENGINE", engineX + engineW / 2, engineY + 7, this.dim);

        final double time = net.minecraft.Util.getMillis() / 180.0;
        final int cylinderCount = Math.max(2, Math.min(6, Math.round(snapshot.engineCount())));
        final int bayW = Math.max(10, (engineW - 24) / cylinderCount);
        final int crankY = engineY + engineH - 24;
        g.fill(engineX + 10, crankY - 2, engineX + engineW - 10, crankY + 2, stateColor);
        for (int cyl = 0; cyl < cylinderCount; cyl++) {
            final int cx = engineX + 12 + cyl * bayW + bayW / 2;
            final int pistonTravel = Math.max(8, engineH / 4);
            final int pistonTop = engineY + 29 + (snapshot.running() ? (int)Math.round((Math.sin(time + cyl * 1.15) + 1.0) * pistonTravel * 0.5) : pistonTravel / 2);
            g.fill(cx - 7, engineY + 23, cx + 8, crankY - 5, 0xFF102D30);
            this.border(g, cx - 7, engineY + 23, 15, crankY - engineY - 28, this.grid);
            g.fill(cx - 5, pistonTop, cx + 6, pistonTop + 8, snapshot.running() ? 0xFFE6A342 : this.dim);
            this.drawSlopedBeam(g, cx, pistonTop + 8, cx + (cyl % 2 == 0 ? 8 : -8), crankY, 0xFFB8A37F);
            final int sparkColor = snapshot.running() && Math.sin(time + cyl * 1.15) > 0.55 ? 0xFFFFD27A : 0xFF274548;
            g.fill(cx - 2, engineY + 17, cx + 3, engineY + 21, sparkColor);
        }
        final int flywheelX = engineX + engineW - 18;
        final int flywheelY = crankY;
        for (int spoke = 0; spoke < 6; spoke++) {
            final double angle = time * (snapshot.running() ? 0.9 : 0.08) + spoke * Math.PI / 3.0;
            final int sx = flywheelX + (int)Math.round(Math.cos(angle) * 14.0);
            final int sy = flywheelY + (int)Math.round(Math.sin(angle) * 14.0);
            this.drawSlopedBeam(g, flywheelX, flywheelY, sx, sy, stateColor);
        }
        g.fill(flywheelX - 3, flywheelY - 3, flywheelX + 4, flywheelY + 4, stateColor);

        this.drawDieselPipeGui(g, tankX + tankW, engineX, tankY + tankH / 2, snapshot.fillPercent() > 0.0f, this.dieselFillColor(snapshot), true);
        final int statsX = x + w - statsW - 14;
        this.drawDieselPipeGui(g, engineX + engineW, statsX, crankY, snapshot.running(), this.accent, true);
        final int cardH = 24;
        this.drawDieselNodeGui(g, statsX, top + 6, statsW, cardH, "RPM", String.format("%.0f", snapshot.generatedSpeed()), this.accent);
        this.drawDieselNodeGui(g, statsX, top + 34, statsW, cardH, "PRODUCED", this.formatCompact(snapshot.generatedStress()) + " SU", snapshot.generatedStress() > 0.0f ? 0xFF58D575 : this.dim);
        this.drawDieselNodeGui(g, statsX, top + 62, statsW, cardH, "LOAD", this.formatCompact(snapshot.networkStress()) + " SU", snapshot.networkStress() > 0.0f ? 0xFFD6A84F : this.dim);
        final String groupLine = snapshot.sourceType().contains("Group")
            ? "OK " + Math.round(snapshot.okEngines()) + "  ST " + Math.round(snapshot.stallingEngines()) + "  OFF " + Math.round(snapshot.offEngines())
            : "ENGINES " + Math.round(snapshot.engineCount());

        final int statusY = y + h - statusH - 4;
        g.fill(x + 10, statusY, x + w - 10, statusY + statusH, 0xCC081A1D);
        this.border(g, x + 10, statusY, w - 20, statusH, this.grid);
        final String mode = snapshot.sourceType().contains("Group") || snapshot.engineCount() > 1.0f ? "Multi" : "Single";
        g.drawString(this.font, this.truncate(mode + " engine feed  " + groupLine, w - 32), x + 16, statusY + 5, stateColor);
        final String right = "Produced " + this.formatCompact(snapshot.generatedStress()) + " SU  Load " + this.formatCompact(snapshot.networkStress()) + " SU";
        g.drawString(this.font, this.truncate(right, w - 32), x + 16, statusY + 18, this.dim);
    }

    private void drawDieselPipeGui(GuiGraphics g, int startX, int endX, int y, boolean active, int color, boolean toRight) {
        final int left = Math.min(startX, endX);
        final int right = Math.max(startX, endX);
        final int width = Math.max(1, right - left);
        g.fill(left, y - 1, right, y + 1, active ? 0xFF6D5122 : 0x66425355);
        if (!active) return;
        final int span = Math.max(8, width - 2);
        final long tick = net.minecraft.Util.getMillis() / 50L;
        final int phase = Math.floorMod((int)(tick * 4L), span);
        final int pulseX = toRight ? left + 1 + phase : right - 2 - phase;
        g.fill(pulseX - 2, y - 3, pulseX + 3, y + 4, color);
    }

    private void drawSlopedBeam(GuiGraphics g, int x1, int y1, int x2, int y2, int color) {
        final int steps = Math.max(1, Math.abs(x2 - x1));
        for (int step = 0; step <= steps; step++) {
            final float ratio = step / (float)steps;
            final int px = Math.round(x1 + (x2 - x1) * ratio);
            final int py = Math.round(y1 + (y2 - y1) * ratio);
            g.fill(px, py, px + 3, py + 3, color);
        }
    }

    private void drawDieselFuelGaugeGui(GuiGraphics g, int x, int y, int w, int h, ProductionDetectorBE.DieselSnapshot snapshot) {
        g.fill(x, y, x + w, y + h, 0xFF050B0E);
        this.border(g, x, y, w, h, this.border);
        final int innerX = x + 5;
        final int innerY = y + 5;
        final int innerW = Math.max(1, w - 10);
        final int innerH = Math.max(1, h - 10);
        final int fillW = Math.round(innerW * Math.max(0.0f, Math.min(1.0f, snapshot.fillPercent() / 100.0f)));
        if (fillW > 0) {
            g.fill(innerX, innerY, innerX + fillW, innerY + innerH, 0xCCB47A2B);
            g.fill(innerX, innerY, innerX + fillW, innerY + 2, 0xFFFFD27A);
        }
        final int progressW = Math.round(innerW * Math.max(0.0f, Math.min(1.0f, snapshot.processPercent() / 100.0f)));
        if (progressW > 0) {
            g.fill(innerX, innerY + innerH - 3, innerX + progressW, innerY + innerH, 0xFF58D575);
        }
        if (snapshot.hasActivity()) {
            final int span = Math.max(6, innerW - 4);
            final long tick = net.minecraft.Util.getMillis() / 50L;
            for (int pulse = 0; pulse < 4; pulse++) {
                final int phase = Math.floorMod((int)(tick * 3L + pulse * span / 4), span);
                final int pulseX = innerX + 2 + phase;
                g.fill(pulseX - 2, y + h / 2 - 2, pulseX + 3, y + h / 2 + 3, this.dieselColor(snapshot));
            }
        }
        g.drawCenteredString(this.font, String.format("%.1f%%", snapshot.fillPercent()), x + w / 2, y + h / 2 - this.font.lineHeight / 2, this.text);
    }

    private void drawDieselNodeGui(GuiGraphics g, int x, int y, int w, int h, String label, String value, int color) {
        g.fill(x, y, x + w, y + h, 0xDD0B2E30);
        this.border(g, x, y, w, h, color);
        g.drawCenteredString(this.font, label, x + w / 2, y + 5, this.dim);
        g.drawCenteredString(this.font, this.truncate(value, w - 8), x + w / 2, y + Math.max(14, h - 12), color);
    }

    private void drawEnergyBatteryGui(GuiGraphics g, int x, int y, int w, int h, ProductionDetectorBE.EnergySnapshot snapshot) {
        g.fill(x, y, x + w, y + h, 0xFF050B0E);
        this.border(g, x, y, w, h, this.border);
        final int terminalW = Math.max(5, w / 36);
        g.fill(x + w, y + h / 3, x + w + terminalW, y + h / 3 + Math.max(8, h / 3), this.border);
        final int innerX = x + 5;
        final int innerY = y + 5;
        final int innerW = Math.max(1, w - 10);
        final int innerH = Math.max(1, h - 10);
        final int fillW = Math.round(innerW * Math.max(0.0f, Math.min(1.0f, snapshot.fillPercent() / 100.0f)));
        if (fillW > 0) {
            g.fill(innerX, innerY, innerX + fillW, innerY + innerH, 0xCC3FA86D);
            g.fill(innerX, innerY, innerX + fillW, innerY + 2, 0xFFEAFBD6);
        }
        for (int segment = 1; segment < 5; segment++) {
            final int segmentX = innerX + segment * innerW / 5;
            g.fill(segmentX, innerY, segmentX + 1, innerY + innerH, 0xFF6D5122);
        }
        if (this.hasEnergyFlow(snapshot)) {
            final int span = Math.max(6, innerW - 4);
            final long tick = net.minecraft.Util.getMillis() / 50L;
            for (int pulse = 0; pulse < 4; pulse++) {
                final int phase = Math.floorMod((int)(tick * 3L + pulse * span / 4), span);
                final int pulseX = this.isDischarging(snapshot) ? innerX + innerW - 3 - phase : innerX + 2 + phase;
                g.fill(pulseX - 2, y + h / 2 - 2, pulseX + 3, y + h / 2 + 3, this.energyColor(snapshot));
            }
        }
    }

    private void drawEnergyNodeGui(GuiGraphics g, int x, int y, int w, int h, String label, String value, int color) {
        g.fill(x, y, x + w, y + h, 0xDD0B2E30);
        this.border(g, x, y, w, h, color);
        g.drawCenteredString(this.font, label, x + w / 2, y + 5, this.dim);
        g.drawCenteredString(this.font, this.truncate(value, w - 8), x + w / 2, y + h - 14, color);
    }

    private void drawEnergyFlowGui(GuiGraphics g, int startX, int endX, int y, boolean toRight, boolean active, int color) {
        final int left = Math.min(startX, endX);
        final int right = Math.max(startX, endX);
        final int width = Math.max(1, right - left);
        g.fill(left, y - 1, right, y + 1, active ? 0xFF6D5122 : 0x66425355);
        if (!active) return;
        final int span = Math.max(8, width - 2);
        final long tick = net.minecraft.Util.getMillis() / 50L;
        for (int pulse = 0; pulse < 4; pulse++) {
            final int phase = Math.floorMod((int)(tick * 4L + pulse * span / 4), span);
            final int pulseX = toRight ? left + 1 + phase : right - 2 - phase;
            g.fill(pulseX - 2, y - 3, pulseX + 3, y + 4, color);
        }
    }

    private void drawEnergyConnectorStripGui(GuiGraphics g, int x, int y, int w, int h, ProductionDetectorBE.EnergySnapshot snapshot) {
        g.fill(x, y, x + w, y + h, 0xCC081A1D);
        this.border(g, x, y, w, h, this.grid);
        g.drawString(this.font, "Connectors " + Math.round(snapshot.connectors()), x + 6, y + 6, snapshot.connectors() > 0.5f ? 0xFF58D575 : this.dim);
        final String blocks = "Blocks " + Math.round(snapshot.blocks());
        g.drawString(this.font, blocks, x + w - this.font.width(blocks) - 6, y + 6, this.dim);
        final int startX = x + w / 2 - 34;
        final int activeNodes = Math.max(0, Math.min(8, Math.round(snapshot.connectors())));
        for (int node = 0; node < 8; node++) {
            final int color = node < activeNodes ? 0xFF66A8FF : 0xFF243336;
            g.fill(startX + node * 9, y + h - 6, startX + node * 9 + 5, y + h - 3, color);
        }
    }

    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
    }

    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        if (this.detector.getDisplayMode() != ProductionDetectorBE.DisplayMode.FLUID
            && this.detector.getDisplayMode() != ProductionDetectorBE.DisplayMode.ENERGY
                && this.detector.getDisplayMode() != ProductionDetectorBE.DisplayMode.DIESEL
                && this.detector.getDisplayMode() != ProductionDetectorBE.DisplayMode.DISPLAY_LINK_FEED
                && this.isMouseOverItemList(mx, my)) {
            final int maxScroll = Math.max(0, this.detector.getTopItemEntries(Integer.MAX_VALUE).size() - GUI_VISIBLE_ITEM_ROWS);
            if (maxScroll > 0 && scrollY != 0.0) {
                this.itemScrollOffset -= (int) Math.signum(scrollY);
                if (this.itemScrollOffset < 0) this.itemScrollOffset = 0;
                if (this.itemScrollOffset > maxScroll) this.itemScrollOffset = maxScroll;
                return true;
            }
        }
        return super.mouseScrolled(mx, my, scrollX, scrollY);
    }

    private void border(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }

    private void renderPanelPattern(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, 0xAA062022);
        for (int px = x + 12; px < x + w - 8; px += 18) {
            g.fill(px, y + 4, px + 1, y + h - 4, 0x33234A4C);
        }
        for (int py = y + 10; py < y + h - 8; py += 18) {
            g.fill(x + 4, py, x + w - 4, py + 1, 0x33234A4C);
        }
    }

    private void renderTopItems(GuiGraphics g, int listX, int startY, int listW, int listH) {
        final List<ProductionDetectorBE.ItemCountEntry> entries = this.detector.getTopItemEntries(Integer.MAX_VALUE);
        g.fill(listX, startY, listX + listW, startY + listH, 0xCC062022);
        this.border(g, listX, startY, listW, listH, this.grid);
        g.drawString(this.font, "Stored items", listX, startY - 14, this.border);
        g.drawString(this.font, "Count", listX + listW - this.font.width("Count"), startY - 14, this.accent);
        if (entries.isEmpty()) {
            final String empty = this.detector.getTargetState() == ProductionDetectorBE.TargetState.NO_TARGET ? "No target in range" : "No items in target";
            g.drawCenteredString(this.font, empty, listX + listW / 2, startY + listH / 2 - 4, 0xFFD6A84F);
            return;
        }
        final int maxScroll = Math.max(0, entries.size() - GUI_VISIBLE_ITEM_ROWS);
        if (this.itemScrollOffset > maxScroll) this.itemScrollOffset = maxScroll;
        final int topCount = Math.max(1, entries.get(0).count());
        final int visibleRows = Math.min(GUI_VISIBLE_ITEM_ROWS, entries.size());
        final int rowH = Math.max(14, Math.min(20, (listH - 12) / GUI_VISIBLE_ITEM_ROWS));
        final int rowInnerH = Math.max(12, rowH - 3);
        for (int i = 0; i < visibleRows; ++i) {
            final int entryIndex = i + this.itemScrollOffset;
            final ProductionDetectorBE.ItemCountEntry entry = entries.get(entryIndex);
            final boolean topEntry = entryIndex == 0;
            final int rowY = startY + 6 + i * rowH;
            final int rowColor = topEntry ? 0x663E331D : ((i % 2 == 0) ? 0x66153335 : 0x44112628);
            g.fill(listX + 6, rowY, listX + listW - 6, rowY + rowInnerH, rowColor);
            this.border(g, listX + 6, rowY, listW - 12, rowInnerH, topEntry ? this.border : this.grid);
            final int barW = Math.max(2, (int)((listW - 4) * (entry.count() / (float)topCount)));
            g.fill(listX + 8, rowY + rowInnerH - 3, listX + listW - 8, rowY + rowInnerH - 2, 0x66234A4C);
            g.fill(listX + 8, rowY + rowInnerH - 3, listX + 8 + Math.min(barW, listW - 16), rowY + rowInnerH - 1, topEntry ? 0xFF58D575 : this.accent);
            g.drawString(this.font, (entryIndex + 1) + ".", listX + 11, rowY + 4, this.dim);
            g.drawString(this.font, this.truncate(entry.displayName(), listW - 104), listX + 32, rowY + 4, this.text);
            final String count = String.format("%,d", entry.count());
            g.drawString(this.font, count, listX + listW - this.font.width(count) - 12, rowY + 4, topEntry ? this.accent : this.border);
        }
    }

    private boolean isMouseOverItemList(double mx, double my) {
        final int bodyY = this.panelTop + 120;
        final int bodyH = this.panelHeight - 130;
        final int listW = (int)((this.panelWidth - 26) * 0.64f);
        final int listX = this.panelLeft + 10;
        return mx >= listX && mx <= listX + listW && my >= bodyY && my <= bodyY + bodyH;
    }

    private void renderSummaryCard(GuiGraphics g, int x, int y, int w, String label, String value) {
        g.fill(x, y, x + w, y + 32, 0xCC062022);
        this.border(g, x, y, w, 32, this.grid);
        g.drawString(this.font, label, x + 5, y + 5, this.dim);
        g.drawString(this.font, this.truncate(value, w - 10), x + 5, y + 18, this.text);
    }

    private void renderStatusPill(GuiGraphics g, int x, int y) {
        final String label = switch (this.detector.getTargetState()) {
            case MONITORING -> "Monitoring";
            case IDLE -> "Empty";
            case BLOCKED -> "Blocked";
            case NO_TARGET -> "No Target";
        };
        final int color = switch (this.detector.getTargetState()) {
            case MONITORING -> this.accent;
            case IDLE -> this.dim;
            case BLOCKED -> 0xFFD05858;
            case NO_TARGET -> this.grid;
        };
        final int w = this.font.width(label) + 12;
        g.fill(x, y, x + w, y + 15, 0x66101010);
        this.border(g, x, y, w, 15, color);
        g.drawString(this.font, label, x + 6, y + 4, color);
    }

    private void renderDetails(GuiGraphics g, int x, int y, int w, int h, String targetName, String facingName) {
        g.fill(x, y, x + w, y + h, 0xCC062022);
        this.border(g, x, y, w, h, this.grid);
        g.drawString(this.font, "Details", x + 6, y + 6, this.accent);
        int lineY = y + 24;
        lineY = this.detailLine(g, x, lineY, w, "Target", targetName);
        lineY = this.detailLine(g, x, lineY, w, "Facing", facingName);
        lineY = this.detailLine(g, x, lineY, w, "Range", this.detector.getDetectionRange() + " block" + (this.detector.getDetectionRange() == 1 ? "" : "s"));
        this.detailLine(g, x, lineY, w, "State", this.detector.getTargetState().name().toLowerCase());
    }

    private int detailLine(GuiGraphics g, int x, int y, int w, String label, String value) {
        g.drawString(this.font, label, x + 6, y, this.dim);
        g.drawString(this.font, this.truncate(value, w - 12), x + 6, y + 10, this.text);
        return y + 26;
    }

    private int energyColor(ProductionDetectorBE.EnergySnapshot snapshot) {
        if (this.isDischarging(snapshot)) return 0xFFD6A84F;
        if (this.isCharging(snapshot)) return 0xFF58D575;
        if (this.hasEnergyFlow(snapshot)) return 0xFF66A8FF;
        if (snapshot.fillPercent() <= 10.0f) return 0xFFD05858;
        return 0xFFE6A342;
    }

    private int dieselColor(ProductionDetectorBE.DieselSnapshot snapshot) {
        if (snapshot.redstoneDisabled() || !snapshot.validFuel()) return 0xFFD05858;
        if (snapshot.hasActivity()) return 0xFF58D575;
        if (snapshot.fillPercent() > 0.0f || snapshot.oilRemaining() > 0.0f) return 0xFFE6A342;
        return this.dim;
    }

    private int dieselFillColor(ProductionDetectorBE.DieselSnapshot snapshot) {
        if (snapshot.fillPercent() <= 0.0f && snapshot.oilRemaining() <= 0.0f) return this.dim;
        if (snapshot.fillPercent() > 0.0f && snapshot.fillPercent() < 20.0f) return 0xFFD6A84F;
        return 0xFFB47A2B;
    }

    private String dieselStateLabel(ProductionDetectorBE.DieselSnapshot snapshot) {
        if (snapshot.sourceType().contains("Group")) {
            if (snapshot.offEngines() >= snapshot.engineCount()) return "OFF";
            if (snapshot.stallingEngines() > 0.0f) return "STALLING";
            if (snapshot.okEngines() > 0.0f) return "OK";
        }
        if (snapshot.redstoneDisabled()) return "STOPPED";
        if (!snapshot.validFuel()) return "NO FUEL";
        if (snapshot.running()) return "RUNNING";
        if (snapshot.processing()) return "PROCESS";
        if (snapshot.heatLevel() > 0.0f) return "HEAT";
        return "IDLE";
    }

    private String dieselInputLabel(ProductionDetectorBE.DieselSnapshot snapshot) {
        return "Oil Pumpjack".equals(snapshot.sourceType()) ? "DEPOSIT" : "FUEL";
    }

    private String dieselInputValue(ProductionDetectorBE.DieselSnapshot snapshot) {
        if (snapshot.oilRemaining() > 0.0f) return this.formatFluidAmount(snapshot.oilRemaining());
        return String.format("%.0f%%", snapshot.fillPercent());
    }

    private String dieselCoreLabel(ProductionDetectorBE.DieselSnapshot snapshot) {
        if (snapshot.sourceType().contains("Engine")) return "ENGINE";
        if ("Diesel Distillation Tower".equals(snapshot.sourceType())) return "TOWER";
        if ("Diesel Burner".equals(snapshot.sourceType())) return "BURNER";
        if ("Oil Barrel".equals(snapshot.sourceType())) return "BARREL";
        return "PUMP";
    }

    private String dieselOutputLabel(ProductionDetectorBE.DieselSnapshot snapshot) {
        if (snapshot.sourceType().contains("Engine")) return "SHAFT";
        if ("Diesel Burner".equals(snapshot.sourceType())) return "HEAT";
        return "OUTPUT";
    }

    private String dieselOutputValue(ProductionDetectorBE.DieselSnapshot snapshot) {
        if (snapshot.sourceType().contains("Engine")) return this.formatDieselOutput(snapshot);
        if ("Diesel Burner".equals(snapshot.sourceType())) return this.formatCompact(snapshot.heatLevel());
        return snapshot.processing() ? "FLOW" : "HOLD";
    }

    private String formatDieselOutput(ProductionDetectorBE.DieselSnapshot snapshot) {
        if (Math.abs(snapshot.generatedSpeed()) > 0.01f) return String.format("%.0f RPM", snapshot.generatedSpeed());
        if (snapshot.heatLevel() > 0.0f) return "Heat " + this.formatCompact(snapshot.heatLevel());
        if (snapshot.oilRemaining() > 0.0f) return this.formatFluidAmount(snapshot.oilRemaining());
        return snapshot.hasActivity() ? "Active" : "Idle";
    }

    private boolean hasInputFlow(ProductionDetectorBE.EnergySnapshot snapshot) {
        return snapshot.inputRate() > ENERGY_FLOW_EPSILON || snapshot.netRate() > ENERGY_FLOW_EPSILON;
    }

    private boolean hasOutputFlow(ProductionDetectorBE.EnergySnapshot snapshot) {
        return snapshot.outputRate() > ENERGY_FLOW_EPSILON || snapshot.netRate() < -ENERGY_FLOW_EPSILON;
    }

    private boolean hasEnergyFlow(ProductionDetectorBE.EnergySnapshot snapshot) {
        return this.hasInputFlow(snapshot) || this.hasOutputFlow(snapshot);
    }

    private boolean isCharging(ProductionDetectorBE.EnergySnapshot snapshot) {
        return this.hasInputFlow(snapshot) && (!this.hasOutputFlow(snapshot) || snapshot.inputRate() > snapshot.outputRate() + ENERGY_FLOW_EPSILON || snapshot.netRate() > ENERGY_FLOW_EPSILON);
    }

    private boolean isDischarging(ProductionDetectorBE.EnergySnapshot snapshot) {
        return this.hasOutputFlow(snapshot) && (!this.hasInputFlow(snapshot) || snapshot.outputRate() > snapshot.inputRate() + ENERGY_FLOW_EPSILON || snapshot.netRate() < -ENERGY_FLOW_EPSILON);
    }

    private String formatEnergy(float value) {
        final float abs = Math.abs(value);
        if (abs >= 1_000_000.0f) return String.format("%.1fM FE", value / 1_000_000.0f);
        if (abs >= 10_000.0f) return String.format("%.1fk FE", value / 1_000.0f);
        return String.format("%.0f FE", value);
    }

    private String formatEnergyRate(float value) {
        final String sign = value > 0.01f ? "+" : "";
        final float abs = Math.abs(value);
        if (abs >= 1_000_000.0f) return sign + String.format("%.1fM FE/t", value / 1_000_000.0f);
        if (abs >= 10_000.0f) return sign + String.format("%.1fk FE/t", value / 1_000.0f);
        return sign + String.format("%.0f FE/t", value);
    }

    private String formatFluidAmount(float value) {
        return this.formatCompact(value) + " mB";
    }

    private String formatCompact(float value) {
        final float abs = Math.abs(value);
        if (abs >= 1_000_000.0f) return String.format("%.1fM", value / 1_000_000.0f);
        if (abs >= 10_000.0f) return String.format("%.1fk", value / 1_000.0f);
        return String.format("%.0f", value);
    }

    private String truncate(String value, int maxWidth) {
        if (this.font.width(value) <= maxWidth) {
            return value;
        }
        while (value.length() > 3 && this.font.width(value + "..") > maxWidth) {
            value = value.substring(0, value.length() - 1);
        }
        return value + "..";
    }
}
