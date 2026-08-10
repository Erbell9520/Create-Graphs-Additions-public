package com.createstats.client;

import com.createstats.blockentity.CalculatorBE;
import com.createstats.data.MetricType;
import com.createstats.display.StatsDisplaySettings;
import com.createstats.gui.widgets.GraphWidget;
import com.createstats.network.CalculatorStatePacket;
import net.neoforged.neoforge.network.PacketDistributor;
import com.createstats.renderer.CalculatorTextureManager;
import com.createstats.util.ExpressionEvaluator;
import com.createstats.util.ExpressionEvaluator.EvaluationException;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class CalculatorScreen extends Screen
{
    private static final int OUTER_MARGIN = 14;
    private static final int CONTENT_PAD = 14;
    private static final int GUTTER = 10;
    private static final int COLOR_FRAME = 0xF20F141B;
    private static final int COLOR_SURFACE = 0xFF151B24;
    private static final int COLOR_TOP_BAR = 0xFF1B2430;
    private static final int COLOR_GLASS = 0xCC1E2733;
    private static final int COLOR_GLASS_DARK = 0xE8141A22;
    private static final int COLOR_BORDER = 0xFF3B4A5A;
    private static final int COLOR_TEXT = 0xFFE7EEF7;
    private static final int COLOR_MUTED = 0xFF94A3B8;
    private static final int COLOR_ACCENT = 0xFF65D6E8;
    private static final int COLOR_AMBER = 0xFFE0B85A;
    private static final int COLOR_ERROR = 0xFFFF6B6B;
    private final CalculatorBE calculator;
    private EditBox expressionBox;
    private EditBox minXBox;
    private EditBox maxXBox;
    private GraphWidget plotWidget;
    private float[] plotData;
    private int plotSamples;
    private int leftPos;
    private int topPos;
    private int panelWidth;
    private int panelHeight;
    private int historyWidth;
    private int keypadWidth;
    private int mainX;
    private int mainWidth;
    private int keypadX;
    private int contentTop;
    private int contentBottom;
    private int graphY;
    private int graphHeight;
    private int pinnedListY;
    private int historyListY;
    private int screenTicks;
    private int resultPulseTicks;
    private int graphPulseTicks;
    private int inputPulseTicks;
    
    public CalculatorScreen(CalculatorBE calculator) {
        super(Component.translatable("gui.create_stats.calculator.title"));
        this.calculator = calculator.getControllerBE();
        this.plotData = new float[0];
        this.plotSamples = 160;
    }
    
    protected void init() {
        super.init();
        this.computeLayout();
        final int actionY = this.contentTop + 66;
        final int plotRowY = actionY + 32;
        final boolean showFormulaPresets = this.mainWidth >= 520;
        this.graphY = plotRowY + (showFormulaPresets ? 62 : 34);
        this.graphHeight = Math.max(96, this.contentBottom - this.graphY);
        final int rightActionY = this.contentTop + 6;
        final int scientificY = rightActionY + 30;
        final int keypadY = this.calculator.isScientificMode() ? scientificY + 72 : scientificY + 4;
        this.expressionBox = new EditBox(this.font, this.mainX, this.contentTop + 20, this.mainWidth, 20, Component.translatable("gui.create_stats.calculator.expression"));
        this.expressionBox.setMaxLength(256);
        this.expressionBox.setValue(this.calculator.getExpression());
        this.expressionBox.setResponder(value -> {
            this.calculator.setExpression(value);
            CalculatorTextureManager.markDirty(this.calculator.getBlockPos());
        });
        this.addRenderableWidget(this.expressionBox);
        this.addRenderableWidget(this.button(Component.translatable("gui.create_stats.calculator.done"), this.leftPos + this.panelWidth - 76, this.topPos + 10, 62, 18, b -> this.onClose()));
        this.addRenderableWidget(this.button(this.modeLabel(), this.keypadX, rightActionY, this.keypadWidth, 20, b -> {
            this.saveEditingState();
            this.calculator.setScientificMode(!this.calculator.isScientificMode());
            this.syncToServer();
            this.rebuildScreen();
        }));
        this.addRenderableWidget(this.button(Component.translatable("gui.create_stats.calculator.evaluate"), this.mainX, actionY, 92, 20, b -> this.evaluateExpression()));
        this.addRenderableWidget(this.button(Component.translatable("gui.create_stats.calculator.clear"), this.mainX + 100, actionY, 82, 20, b -> {
            this.calculator.clearEntry();
            this.expressionBox.setValue("");
            this.plotData = new float[0];
            this.syncToServer();
            this.plotWidget.setData(this.plotData);
        }));
        this.addRenderableWidget(this.button(Component.translatable("gui.create_stats.calculator.clear_history"), this.leftPos + CONTENT_PAD + 8, this.contentBottom - 24, this.historyWidth - 16, 18, b -> {
            this.calculator.clearHistory();
            this.syncToServer();
        }));
        this.addRenderableWidget(this.button(Component.translatable("gui.create_stats.calculator.pin_formula"), this.leftPos + CONTENT_PAD + 8, this.contentTop + 24, this.historyWidth - 16, 18, b -> this.pinCurrentFormula()));
        if (this.calculator.isScientificMode()) {
            this.addFunctionGrid(scientificY);
        }
        final int rangeBoxX = this.mainX + 44;
        final int rangeBoxWidth = 88;
        this.minXBox = new EditBox(this.font, rangeBoxX, plotRowY, rangeBoxWidth, 20, Component.translatable("gui.create_stats.calculator.min_x"));
        this.minXBox.setMaxLength(16);
        this.minXBox.setFilter(s -> s.isEmpty() || s.matches("-?[0-9]*\\.?[0-9]*"));
        this.minXBox.setValue(this.formatRangeValue(this.calculator.getPlotMinX()));
        this.addRenderableWidget(this.minXBox);
        this.maxXBox = new EditBox(this.font, rangeBoxX + 96, plotRowY, rangeBoxWidth, 20, Component.translatable("gui.create_stats.calculator.max_x"));
        this.maxXBox.setMaxLength(16);
        this.maxXBox.setFilter(s -> s.isEmpty() || s.matches("-?[0-9]*\\.?[0-9]*"));
        this.maxXBox.setValue(this.formatRangeValue(this.calculator.getPlotMaxX()));
        this.addRenderableWidget(this.maxXBox);
        this.addRenderableWidget(this.button(Component.translatable("gui.create_stats.calculator.plot"), rangeBoxX + 196, plotRowY, 72, 20, b -> this.plotExpression()));
        this.addRenderableWidget(this.button(Component.translatable("gui.create_stats.calculator.ans"), this.mainX + 190, actionY, 62, 20, b -> this.insertAnswer()));
        if (this.mainWidth >= 360) {
            this.addRenderableWidget(this.button(Component.translatable("gui.create_stats.calculator.samples", this.plotSamples), this.mainX + 260, actionY, 96, 20, b -> this.cyclePlotSamples()));
        }
        if (this.mainWidth >= 560) {
            this.addRenderableWidget(this.button(Component.literal("-10..10"), rangeBoxX + 278, plotRowY, 64, 20, b -> this.applyPlotRange(-10.0D, 10.0D)));
            this.addRenderableWidget(this.button(Component.literal("0..100"), rangeBoxX + 350, plotRowY, 62, 20, b -> this.applyPlotRange(0.0D, 100.0D)));
            this.addRenderableWidget(this.button(Component.literal("-pi..pi"), rangeBoxX + 420, plotRowY, 68, 20, b -> this.applyPlotRange(-Math.PI, Math.PI)));
        }
        if (showFormulaPresets) {
            final int presetY = plotRowY + 26;
            this.addRenderableWidget(this.button(Component.literal("sin(x)"), this.mainX, presetY, 66, 20, b -> this.applyFormulaPreset("sin(x)")));
            this.addRenderableWidget(this.button(Component.literal("x^2"), this.mainX + 72, presetY, 58, 20, b -> this.applyFormulaPreset("x^2")));
            this.addRenderableWidget(this.button(Component.literal("abs(x)"), this.mainX + 136, presetY, 66, 20, b -> this.applyFormulaPreset("abs(x)")));
            this.addRenderableWidget(this.button(Component.literal("sqrt(abs(x))"), this.mainX + 208, presetY, 104, 20, b -> this.applyFormulaPreset("sqrt(abs(x))")));
        }
        this.plotWidget = new GraphWidget(this.mainX, this.graphY, this.mainWidth, this.graphHeight);
        final StatsDisplaySettings plotSettings = new StatsDisplaySettings();
        plotSettings.setGraphType(StatsDisplaySettings.GraphType.LINE_CHART);
        plotSettings.setShowGrid(true);
        plotSettings.setShowAxisLabels(false);
        plotSettings.setShowMinMaxAvg(false);
        this.plotWidget.setSettings(plotSettings);
        this.plotWidget.setMetricType(MetricType.GENERIC);
        this.addRenderableWidget(this.plotWidget);
        this.plotWidget.setData(this.plotData);
        if (this.plotData.length == 0 && !this.calculator.getExpression().isEmpty()) {
            this.tryRestorePlot();
        }
        this.addKeypadRow(this.keypadX, keypadY, this.keypadWidth, new String[] { "C", "(", ")", "DEL" });
        this.addKeypadRow(this.keypadX, keypadY + 26, this.keypadWidth, new String[] { "7", "8", "9", "/" });
        this.addKeypadRow(this.keypadX, keypadY + 52, this.keypadWidth, new String[] { "4", "5", "6", "*" });
        this.addKeypadRow(this.keypadX, keypadY + 78, this.keypadWidth, new String[] { "1", "2", "3", "-" });
        this.addKeypadRow(this.keypadX, keypadY + 104, this.keypadWidth, new String[] { "0", ".", "=", "+" });
    }

    private void computeLayout() {
        this.panelWidth = Math.max(520, this.width - OUTER_MARGIN * 2);
        this.panelHeight = Math.max(332, this.height - OUTER_MARGIN * 2);
        if (this.panelWidth > this.width - 8) {
            this.panelWidth = Math.max(1, this.width - 8);
        }
        if (this.panelHeight > this.height - 8) {
            this.panelHeight = Math.max(1, this.height - 8);
        }
        this.leftPos = (this.width - this.panelWidth) / 2;
        this.topPos = (this.height - this.panelHeight) / 2;
        this.contentTop = this.topPos + 42;
        this.contentBottom = this.topPos + this.panelHeight - CONTENT_PAD;
        this.historyWidth = this.clamp(this.panelWidth / 5, 116, 156);
        this.keypadWidth = this.clamp(this.panelWidth / 4, 172, 228);
        this.mainX = this.leftPos + CONTENT_PAD + this.historyWidth + GUTTER;
        this.keypadX = this.leftPos + this.panelWidth - CONTENT_PAD - this.keypadWidth;
        this.mainWidth = this.keypadX - this.mainX - GUTTER;
        if (this.mainWidth < 260) {
            this.historyWidth = 112;
            this.keypadWidth = 168;
            this.mainX = this.leftPos + CONTENT_PAD + this.historyWidth + GUTTER;
            this.keypadX = this.leftPos + this.panelWidth - CONTENT_PAD - this.keypadWidth;
            this.mainWidth = Math.max(220, this.keypadX - this.mainX - GUTTER);
        }
        this.pinnedListY = this.contentTop + 58;
        this.historyListY = this.pinnedListY + 74;
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
    
    private void rebuildScreen() {
        this.clearWidgets();
        this.init();
    }
    
    private void addFunctionGrid(int y) {
        final String[][] functions = {
            { "sin", "sin(" }, { "cos", "cos(" }, { "tan", "tan(" },
            { "log", "log(" }, { "ln", "ln(" }, { "sqrt", "sqrt(" },
            { "x", "x" }, { "^", "^" }, { "pi", "pi" }
        };
        final int columns = 3;
        final int gap = 5;
        final int buttonWidth = (this.keypadWidth - gap * (columns - 1)) / columns;
        for (int i = 0; i < functions.length; ++i) {
            final String label = functions[i][0];
            final String token = functions[i][1];
            final int col = i % columns;
            final int row = i / columns;
            this.addRenderableWidget(this.button(Component.literal(label), this.keypadX + col * (buttonWidth + gap), y + row * 22, buttonWidth, 18, b -> this.insertToken(token)));
        }
    }
    
    private void addKeypadRow(int x, int y, int width, String[] tokens) {
        final int gap = 6;
        final int buttonWidth = (width - gap * (tokens.length - 1)) / tokens.length;
        for (int i = 0; i < tokens.length; ++i) {
            final String token = tokens[i];
            this.addRenderableWidget(this.button(Component.literal(token), x + i * (buttonWidth + gap), y, buttonWidth, 22, b -> this.handleKeypadToken(token)));
        }
    }

    private Button button(Component label, int x, int y, int width, int height, Button.OnPress onPress) {
        return new CalculatorButton(x, y, width, height, label, onPress);
    }

    private void handleKeypadToken(String token) {
        if ("=".equals(token)) {
            this.evaluateExpression();
        }
        else if ("C".equals(token)) {
            this.calculator.clearEntry();
            this.expressionBox.setValue("");
            this.plotData = new float[0];
            this.plotWidget.setData(this.plotData);
            this.inputPulseTicks = 8;
        }
        else if ("DEL".equals(token)) {
            this.expressionBox.deleteChars(-1);
            this.calculator.setExpression(this.expressionBox.getValue());
            this.inputPulseTicks = 8;
        }
        else {
            this.insertToken(token);
        }
    }
    
    private Component modeLabel() {
        return Component.translatable(this.calculator.isScientificMode()
                ? "gui.create_stats.calculator.mode.scientific"
                : "gui.create_stats.calculator.mode.simple");
    }
    
    private void insertToken(String token) {
        this.expressionBox.setFocused(true);
        this.expressionBox.insertText(token);
        this.calculator.setExpression(this.expressionBox.getValue());
        this.inputPulseTicks = 8;
    }

    private void insertAnswer() {
        final String answer = this.calculator.getLastResultText();
        if (answer == null || answer.isBlank()) {
            return;
        }
        this.insertToken(answer);
    }

    private void pinCurrentFormula() {
        this.saveEditingState();
        if (this.calculator.pinFormula(this.expressionBox.getValue())) {
            this.syncToServer();
            this.inputPulseTicks = 10;
        }
    }

    private void restoreHistoryEntry(String entry) {
        final int separator = entry.indexOf(" = ");
        final String expression = separator >= 0 ? entry.substring(0, separator) : entry;
        this.expressionBox.setValue(expression);
        this.expressionBox.setCursorPosition(expression.length());
        this.calculator.setExpression(expression);
        this.plotData = new float[0];
        this.plotWidget.setData(this.plotData);
        this.inputPulseTicks = 8;
    }
    
    private void saveEditingState() {
        this.calculator.setExpression(this.expressionBox.getValue());
        final Double min = this.parseRange(this.minXBox.getValue());
        final Double max = this.parseRange(this.maxXBox.getValue());
        if (min != null && max != null && max > min) {
            this.calculator.setPlotRange(min, max);
        }
    }
    
    private void evaluateExpression() {
        this.saveEditingState();
        this.calculator.evaluateExpression();
        this.syncToServer();
        this.resultPulseTicks = 16;
    }
    
    private void plotExpression() {
        this.saveEditingState();
        final Double min = this.parseRange(this.minXBox.getValue());
        final Double max = this.parseRange(this.maxXBox.getValue());
        if (min == null || max == null || max <= min) {
            this.calculator.setErrorMessage(Component.translatable("gui.create_stats.calculator.error.plot_range").getString());
            this.plotData = new float[0];
            this.plotWidget.setData(this.plotData);
            this.syncToServer();
            this.resultPulseTicks = 16;
            return;
        }
        this.calculator.setPlotRange(min, max);
        try {
            this.plotData = this.sampleCurrentPlot(min, max);
            this.plotWidget.setData(this.plotData);
            if (this.plotData.length >= 2) {
                this.calculator.setErrorMessage("");
                this.graphPulseTicks = 22;
            }
            else {
                this.calculator.setErrorMessage("Try a wider x range or an x-based formula");
                this.resultPulseTicks = 16;
            }
            this.syncToServer();
        }
        catch (EvaluationException e) {
            this.calculator.setErrorMessage(e.getMessage());
            this.plotData = new float[0];
            this.plotWidget.setData(this.plotData);
            this.syncToServer();
            this.resultPulseTicks = 16;
        }
    }
    
    private void tryRestorePlot() {
        try {
            this.plotData = this.sampleCurrentPlot(this.calculator.getPlotMinX(), this.calculator.getPlotMaxX());
            this.plotWidget.setData(this.plotData);
            if (this.plotData.length >= 2) {
                this.calculator.setErrorMessage("");
                this.syncToServer();
            }
        }
        catch (EvaluationException ex) {
            this.plotData = new float[0];
            this.plotWidget.setData(this.plotData);
        }
    }
    
    private Double parseRange(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return Double.parseDouble(text);
        }
        catch (NumberFormatException ex) {
            return null;
        }
    }
    
    private String formatRangeValue(double value) {
        try {
            return ExpressionEvaluator.format(value);
        }
        catch (EvaluationException ex) {
            return Double.toString(value);
        }
    }

    private void applyPlotRange(double minX, double maxX) {
        this.minXBox.setValue(this.formatRangeValue(minX));
        this.maxXBox.setValue(this.formatRangeValue(maxX));
        this.calculator.setPlotRange(minX, maxX);
        this.plotExpression();
    }

    private void applyFormulaPreset(String expression) {
        this.expressionBox.setValue(expression);
        this.expressionBox.setCursorPosition(expression.length());
        this.calculator.setExpression(expression);
        this.plotExpression();
        this.rebuildScreen();
    }

    private float[] sampleCurrentPlot(double min, double max) throws EvaluationException {
        final String expression = this.calculator.getExpression();
        final boolean scientificPlot = this.calculator.isScientificMode() || requiresScientificPlotMode(expression);
        if (scientificPlot && !this.calculator.isScientificMode()) {
            this.calculator.setScientificMode(true);
        }
        return ExpressionEvaluator.samplePlot(expression, scientificPlot, min, max, this.plotSamples);
    }

    static boolean requiresScientificPlotMode(String expression) {
        if (expression == null || expression.isBlank()) {
            return false;
        }
        final String lower = expression.toLowerCase();
        if (lower.indexOf('^') >= 0) {
            return true;
        }
        final String[] scientificIdentifiers = {
            "x", "pi", "e", "sin", "cos", "tan", "asin", "acos", "atan", "sqrt", "log", "ln", "abs", "exp", "floor", "ceil", "round", "pow", "min", "max"
        };
        for (String identifier : scientificIdentifiers) {
            if (containsIdentifier(lower, identifier)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsIdentifier(String text, String identifier) {
        int index = text.indexOf(identifier);
        while (index >= 0) {
            final int before = index - 1;
            final int after = index + identifier.length();
            final boolean leftBoundary = before < 0 || !isIdentifierPart(text.charAt(before));
            final boolean rightBoundary = after >= text.length() || !isIdentifierPart(text.charAt(after));
            if (leftBoundary && rightBoundary) {
                return true;
            }
            index = text.indexOf(identifier, index + 1);
        }
        return false;
    }

    private static boolean isIdentifierPart(char ch) {
        return (ch >= 'a' && ch <= 'z') || (ch >= '0' && ch <= '9') || ch == '_';
    }

    private void cyclePlotSamples() {
        if (this.plotSamples < 192) {
            this.plotSamples = 192;
        }
        else if (this.plotSamples < 320) {
            this.plotSamples = 320;
        }
        else {
            this.plotSamples = 96;
        }
        this.saveEditingState();
        if (!this.calculator.getExpression().isBlank()) {
            this.plotExpression();
        }
        this.rebuildScreen();
    }
    
    private void syncToServer() {
        PacketDistributor.sendToServer(new CalculatorStatePacket(this.calculator.getBlockPos(), this.calculator.saveCalculatorState()));
        CalculatorTextureManager.markDirty(this.calculator.getBlockPos());
    }
    
    public void onClose() {
        this.saveEditingState();
        this.syncToServer();
        super.onClose();
    }
    
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 257 || keyCode == 335) {
            this.evaluateExpression();
            return true;
        }
        final boolean handled = super.keyPressed(keyCode, scanCode, modifiers);
        if (handled && this.expressionBox != null && this.expressionBox.isFocused()) {
            this.calculator.setExpression(this.expressionBox.getValue());
        }
        return handled;
    }

    public boolean charTyped(char codePoint, int modifiers) {
        final boolean handled = super.charTyped(codePoint, modifiers);
        if (handled && this.expressionBox != null && this.expressionBox.isFocused()) {
            this.calculator.setExpression(this.expressionBox.getValue());
        }
        return handled;
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && this.tryRestoreHistoryAt(mouseX, mouseY)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    public void tick() {
        super.tick();
        ++this.screenTicks;
        if (this.resultPulseTicks > 0) {
            --this.resultPulseTicks;
        }
        if (this.graphPulseTicks > 0) {
            --this.graphPulseTicks;
        }
        if (this.inputPulseTicks > 0) {
            --this.inputPulseTicks;
        }
    }
    
    public boolean isPauseScreen() {
        return false;
    }
    
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderTransparentBackground(graphics);
        final int actionY = this.contentTop + 66;
        final int plotRowY = actionY + 32;
        graphics.fill(this.leftPos, this.topPos, this.leftPos + this.panelWidth, this.topPos + this.panelHeight, COLOR_FRAME);
        graphics.fill(this.leftPos + 1, this.topPos + 1, this.leftPos + this.panelWidth - 1, this.topPos + this.panelHeight - 1, COLOR_SURFACE);
        graphics.fill(this.leftPos + 1, this.topPos + 1, this.leftPos + this.panelWidth - 1, this.topPos + 36, COLOR_TOP_BAR);
        this.drawPanel(graphics, this.leftPos + CONTENT_PAD, this.contentTop, this.historyWidth, this.contentBottom - this.contentTop, COLOR_GLASS, COLOR_BORDER);
        this.drawPanel(graphics, this.mainX - 8, this.contentTop, this.mainWidth + 16, this.contentBottom - this.contentTop, COLOR_GLASS, COLOR_BORDER);
        this.drawPanel(graphics, this.keypadX - 8, this.contentTop, this.keypadWidth + 16, this.contentBottom - this.contentTop, COLOR_GLASS, COLOR_BORDER);
        this.drawPanel(graphics, this.mainX, this.contentTop + 20, this.mainWidth, 20, COLOR_GLASS_DARK, COLOR_BORDER);
        this.drawPanel(graphics, this.mainX, this.contentTop + 46, this.mainWidth, 22, COLOR_GLASS_DARK, COLOR_BORDER);
        this.drawPanel(graphics, this.mainX, this.graphY, this.mainWidth, this.graphHeight, COLOR_GLASS_DARK, COLOR_BORDER);
        graphics.fill(this.mainX + 1, this.graphY + 1, this.mainX + this.mainWidth - 1, this.graphY + 2, COLOR_ACCENT);
        this.renderInputPulse(graphics, partialTick);
        graphics.drawString(this.font, Component.translatable("gui.create_stats.calculator.title"), this.leftPos + CONTENT_PAD, this.topPos + 9, COLOR_TEXT, false);
        graphics.drawString(this.font, Component.translatable(this.calculator.isScientificMode()
                ? "gui.create_stats.calculator.subtitle.scientific"
                : "gui.create_stats.calculator.subtitle.simple"), this.leftPos + CONTENT_PAD, this.topPos + 22, COLOR_MUTED, false);
        graphics.drawString(this.font, Component.translatable("gui.create_stats.calculator.history"), this.leftPos + CONTENT_PAD + 8, this.contentTop + 10, COLOR_MUTED, false);
        graphics.drawString(this.font, Component.translatable("gui.create_stats.calculator.expression"), this.mainX, this.contentTop + 8, COLOR_MUTED, false);
        graphics.drawString(this.font, Component.translatable("gui.create_stats.calculator.result"), this.mainX, this.contentTop + 36, COLOR_MUTED, false);
        if (this.calculator.isScientificMode()) {
            graphics.drawString(this.font, Component.translatable("gui.create_stats.calculator.functions"), this.keypadX, this.contentTop + 26, COLOR_MUTED, false);
        }
        graphics.drawString(this.font, Component.translatable("gui.create_stats.calculator.range"), this.mainX, plotRowY + 6, COLOR_MUTED, false);
        if (this.mainWidth >= 520) {
            graphics.drawString(this.font, Component.translatable("gui.create_stats.calculator.formula_presets"), this.mainX, plotRowY + 18, COLOR_MUTED, false);
        }
        graphics.drawString(this.font, Component.translatable("gui.create_stats.calculator.keypad"), this.keypadX, this.keypadLabelY(), COLOR_MUTED, false);
        super.render(graphics, mouseX, mouseY, partialTick);
        this.renderResultPulse(graphics, partialTick);
        final String resultLine = this.resultLine();
        graphics.drawString(this.font, this.truncateText(resultLine, this.mainWidth - 16), this.mainX + 8, this.contentTop + 53, this.calculator.getErrorMessage().isEmpty() ? COLOR_ACCENT : COLOR_ERROR, false);
        if (this.plotData.length == 0) {
            this.renderPlotEmptyState(graphics, this.mainX, this.graphY, this.mainWidth, this.graphHeight);
        }
        this.renderGraphPulse(graphics, partialTick);
        this.renderGraphStats(graphics);
        this.renderGraphTrace(graphics, mouseX, mouseY);
        this.renderHistory(graphics);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }
    
    private void renderHistory(GuiGraphics graphics) {
        final List<String> pinned = this.calculator.getPinnedFormulas();
        graphics.drawString(this.font, Component.translatable("gui.create_stats.calculator.pinned"), this.leftPos + CONTENT_PAD + 10, this.pinnedListY - 12, COLOR_MUTED, false);
        if (pinned.isEmpty()) {
            graphics.drawString(this.font, Component.translatable("gui.create_stats.calculator.pinned_hint_1"), this.leftPos + CONTENT_PAD + 10, this.pinnedListY, COLOR_MUTED, false);
            graphics.drawString(this.font, Component.translatable("gui.create_stats.calculator.pinned_hint_2"), this.leftPos + CONTENT_PAD + 10, this.pinnedListY + 13, COLOR_MUTED, false);
        }
        else {
            final int pinnedRows = Math.min(4, pinned.size());
            for (int i = 0; i < pinnedRows; ++i) {
                final int y = this.pinnedListY + i * 13;
                graphics.drawString(this.font, this.truncateText(pinned.get(i), this.historyWidth - 20), this.leftPos + CONTENT_PAD + 10, y, (i == 0) ? COLOR_AMBER : COLOR_TEXT, false);
            }
        }
        graphics.drawString(this.font, Component.translatable("gui.create_stats.calculator.history"), this.leftPos + CONTENT_PAD + 10, this.historyListY - 12, COLOR_MUTED, false);
        final List<String> history = this.calculator.getHistory();
        if (history.isEmpty()) {
            graphics.drawString(this.font, Component.translatable("gui.create_stats.calculator.no_history"), this.leftPos + CONTENT_PAD + 10, this.historyListY, COLOR_MUTED, false);
            graphics.drawString(this.font, Component.translatable("gui.create_stats.calculator.history_hint_1"), this.leftPos + CONTENT_PAD + 10, this.historyListY + 14, COLOR_MUTED, false);
            graphics.drawString(this.font, Component.translatable("gui.create_stats.calculator.history_hint_2"), this.leftPos + CONTENT_PAD + 10, this.historyListY + 28, COLOR_MUTED, false);
            return;
        }
        int y = this.historyListY;
        final int maxRows = Math.max(1, (this.contentBottom - this.historyListY - 32) / 13);
        for (int i = 0; i < history.size() && i < maxRows; ++i) {
            final String line = this.truncateText(history.get(i), this.historyWidth - 20);
            graphics.drawString(this.font, line, this.leftPos + CONTENT_PAD + 10, y, (i == 0) ? COLOR_TEXT : COLOR_MUTED, false);
            y += 13;
        }
    }
    
    private void renderPlotEmptyState(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, COLOR_GLASS_DARK);
        final String line1;
        final String line2;
        if (this.calculator.getExpression().isBlank()) {
            line1 = Component.translatable("gui.create_stats.calculator.plot_empty.enter_formula").getString();
            line2 = Component.translatable("gui.create_stats.calculator.plot_empty.press_plot").getString();
        }
        else if (!this.calculator.getErrorMessage().isEmpty()) {
            line1 = Component.translatable("gui.create_stats.calculator.plot_empty.unavailable").getString();
            line2 = this.truncateText(this.calculator.getErrorMessage(), 180);
        }
        else {
            line1 = Component.translatable("gui.create_stats.calculator.plot_empty.constants").getString();
            line2 = Component.translatable(this.calculator.isScientificMode()
                    ? "gui.create_stats.calculator.plot_empty.use_x"
                    : "gui.create_stats.calculator.plot_empty.enable_scientific").getString();
        }
        final int centerX = x + width / 2;
        final int centerY = y + height / 2;
        graphics.drawCenteredString(this.font, line1, centerX, centerY - 8, COLOR_MUTED);
        graphics.drawCenteredString(this.font, this.truncateText(line2, width - 20), centerX, centerY + 6, COLOR_MUTED);
    }

    private void renderInputPulse(GuiGraphics graphics, float partialTick) {
        final float pulse = this.pulse(this.inputPulseTicks, partialTick, 10.0F);
        if (pulse <= 0.0F) {
            return;
        }
        final int color = withAlpha(COLOR_AMBER, (int)(80.0F * pulse));
        this.drawGlowBorder(graphics, this.keypadX - 8, this.contentTop, this.keypadWidth + 16, this.contentBottom - this.contentTop, color);
        this.drawGlowBorder(graphics, this.mainX, this.contentTop + 20, this.mainWidth, 20, color);
    }

    private void renderResultPulse(GuiGraphics graphics, float partialTick) {
        final float pulse = this.pulse(this.resultPulseTicks, partialTick, 16.0F);
        if (pulse <= 0.0F) {
            return;
        }
        final int color = this.calculator.getErrorMessage().isEmpty() ? COLOR_ACCENT : COLOR_ERROR;
        graphics.fill(this.mainX + 1, this.contentTop + 47, this.mainX + this.mainWidth - 1, this.contentTop + 67, withAlpha(color, (int)(70.0F * pulse)));
        this.drawGlowBorder(graphics, this.mainX, this.contentTop + 46, this.mainWidth, 22, withAlpha(color, (int)(120.0F * pulse)));
    }

    private void renderGraphPulse(GuiGraphics graphics, float partialTick) {
        final float pulse = this.pulse(this.graphPulseTicks, partialTick, 22.0F);
        if (pulse <= 0.0F) {
            return;
        }
        this.drawGlowBorder(graphics, this.mainX, this.graphY, this.mainWidth, this.graphHeight, withAlpha(COLOR_ACCENT, (int)(135.0F * pulse)));
        graphics.fill(this.mainX + 2, this.graphY + 2, this.mainX + this.mainWidth - 2, this.graphY + 4, withAlpha(COLOR_ACCENT, (int)(55.0F * pulse)));
    }

    private void renderGraphStats(GuiGraphics graphics) {
        if (this.plotData.length < 2) {
            return;
        }
        final float min = this.minPlotValue();
        final float max = this.maxPlotValue();
        final float avg = this.avgPlotValue();
        final int x = this.mainX + this.mainWidth - 118;
        final int y = this.graphY + 10;
        graphics.fill(x - 6, y - 5, x + 112, y + 35, withAlpha(COLOR_SURFACE, 205));
        this.drawGlowBorder(graphics, x - 6, y - 5, 118, 40, withAlpha(COLOR_BORDER, 150));
        graphics.drawString(this.font, Component.translatable("gui.create_stats.calculator.plot_max", this.formatPlotValue(max)), x, y, COLOR_TEXT, false);
        graphics.drawString(this.font, Component.translatable("gui.create_stats.calculator.plot_avg", this.formatPlotValue(avg)), x, y + 11, COLOR_AMBER, false);
        graphics.drawString(this.font, Component.translatable("gui.create_stats.calculator.plot_min", this.formatPlotValue(min)), x, y + 22, COLOR_MUTED, false);
    }

    private void renderGraphTrace(GuiGraphics graphics, int mouseX, int mouseY) {
        if (this.plotData.length < 2) {
            return;
        }
        final int innerX = this.mainX + 4;
        final int innerY = this.graphY + 4;
        final int innerW = Math.max(1, this.mainWidth - 8);
        final int innerH = Math.max(1, this.graphHeight - 8);
        if (mouseX < innerX || mouseX > innerX + innerW || mouseY < innerY || mouseY > innerY + innerH) {
            return;
        }
        final float ratio = Math.max(0.0F, Math.min(1.0F, (mouseX - innerX) / (float)innerW));
        final int index = Math.max(0, Math.min(this.plotData.length - 1, Math.round(ratio * (this.plotData.length - 1))));
        final float min = this.minPlotValue();
        final float max = this.maxPlotValue();
        final float range = Math.max(0.001F, max - min);
        final int traceX = innerX + Math.round(index / (float)(this.plotData.length - 1) * innerW);
        final int traceY = innerY + innerH - Math.round((this.plotData[index] - min) / range * innerH);
        final double xValue = this.calculator.getPlotMinX() + (this.calculator.getPlotMaxX() - this.calculator.getPlotMinX()) * index / (double)(this.plotData.length - 1);
        graphics.fill(traceX, innerY, traceX + 1, innerY + innerH, withAlpha(COLOR_ACCENT, 130));
        graphics.fill(traceX - 2, traceY - 2, traceX + 3, traceY + 3, COLOR_ACCENT);
        final String readout = "x " + this.formatPlotValue((float)xValue) + "   y " + this.formatPlotValue(this.plotData[index]);
        final int boxW = this.font.width(readout) + 14;
        final int boxX = Math.min(Math.max(traceX + 8, innerX + 4), innerX + innerW - boxW - 4);
        final int boxY = Math.max(innerY + 8, traceY - 24);
        graphics.fill(boxX, boxY, boxX + boxW, boxY + 18, withAlpha(COLOR_SURFACE, 225));
        this.drawGlowBorder(graphics, boxX, boxY, boxW, 18, withAlpha(COLOR_ACCENT, 150));
        graphics.drawString(this.font, readout, boxX + 7, boxY + 5, COLOR_TEXT, false);
    }

    private float minPlotValue() {
        float min = Float.MAX_VALUE;
        for (float value : this.plotData) {
            min = Math.min(min, value);
        }
        return min;
    }

    private float maxPlotValue() {
        float max = -Float.MAX_VALUE;
        for (float value : this.plotData) {
            max = Math.max(max, value);
        }
        return max;
    }

    private float avgPlotValue() {
        float sum = 0.0F;
        for (float value : this.plotData) {
            sum += value;
        }
        return this.plotData.length == 0 ? 0.0F : sum / this.plotData.length;
    }

    private String formatPlotValue(float value) {
        if (Math.abs(value) >= 100.0F) {
            return String.format(Locale.ROOT, "%.0f", value);
        }
        if (Math.abs(value) >= 10.0F) {
            return String.format(Locale.ROOT, "%.1f", value);
        }
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private float pulse(int ticks, float partialTick, float maxTicks) {
        if (ticks <= 0) {
            return 0.0F;
        }
        return Math.max(0.0F, Math.min(1.0F, (ticks - partialTick) / maxTicks));
    }

    private void drawGlowBorder(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        graphics.fill(x, y, x + width, y + 1, color);
        graphics.fill(x, y + height - 1, x + width, y + height, color);
        graphics.fill(x, y, x + 1, y + height, color);
        graphics.fill(x + width - 1, y, x + width, y + height, color);
    }

    private static int withAlpha(int color, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (color & 0x00FFFFFF);
    }
    
    private void drawPanel(GuiGraphics graphics, int x, int y, int w, int h, int fill, int border) {
        graphics.fill(x, y, x + w, y + h, fill);
        graphics.fill(x, y, x + w, y + 1, border);
        graphics.fill(x, y + h - 1, x + w, y + h, border);
        graphics.fill(x, y, x + 1, y + h, border);
        graphics.fill(x + w - 1, y, x + w, y + h, border);
    }
    
    private String truncateText(String text, int maxWidth) {
        if (this.font.width(text) <= maxWidth) {
            return text;
        }
        return this.font.plainSubstrByWidth(text, Math.max(0, maxWidth - this.font.width(".."))) + "..";
    }
    
    private boolean tryRestoreHistoryAt(double mouseX, double mouseY) {
        final int x0 = this.leftPos + CONTENT_PAD + 8;
        final int x1 = x0 + this.historyWidth - 16;
        if (mouseX < x0 || mouseX > x1) {
            return false;
        }
        final List<String> pinned = this.calculator.getPinnedFormulas();
        if (mouseY >= this.pinnedListY && mouseY < this.pinnedListY + 52) {
            final int pinnedIndex = (int)((mouseY - this.pinnedListY) / 13.0D);
            if (pinnedIndex >= 0 && pinnedIndex < pinned.size() && pinnedIndex < 4) {
                this.restoreHistoryEntry(pinned.get(pinnedIndex));
                return true;
            }
            return false;
        }
        if (mouseY < this.historyListY || mouseY > this.contentBottom - 32) {
            return false;
        }
        final List<String> history = this.calculator.getHistory();
        final int index = (int)((mouseY - this.historyListY) / 13.0D);
        final int maxRows = Math.max(1, (this.contentBottom - this.historyListY - 32) / 13);
        if (index < 0 || index >= history.size() || index >= maxRows) {
            return false;
        }
        this.restoreHistoryEntry(history.get(index));
        return true;
    }

    private String resultLine() {
        if (!this.calculator.getErrorMessage().isEmpty()) {
            return this.calculator.getErrorMessage();
        }
        if (!this.calculator.getLastResultText().isEmpty()) {
            return "= " + this.calculator.getLastResultText();
        }
        return Component.translatable("gui.create_stats.calculator.ready").getString();
    }

    private int keypadLabelY() {
        return this.contentTop + (this.calculator.isScientificMode() ? 104 : 32);
    }

    private static class CalculatorButton extends Button {
        private CalculatorButton(int x, int y, int width, int height, Component message, OnPress onPress) {
            super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            final boolean hot = this.isHoveredOrFocused();
            final int fill = hot ? withAlpha(COLOR_ACCENT, 72) : withAlpha(COLOR_GLASS_DARK, 230);
            final int border = hot ? COLOR_ACCENT : COLOR_BORDER;
            final int text = this.active ? COLOR_TEXT : COLOR_MUTED;
            graphics.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height, fill);
            graphics.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + 1, border);
            graphics.fill(this.getX(), this.getY() + this.height - 1, this.getX() + this.width, this.getY() + this.height, border);
            graphics.fill(this.getX(), this.getY(), this.getX() + 1, this.getY() + this.height, border);
            graphics.fill(this.getX() + this.width - 1, this.getY(), this.getX() + this.width, this.getY() + this.height, border);
            if (hot) {
                graphics.fill(this.getX() + 2, this.getY() + 2, this.getX() + this.width - 2, this.getY() + 3, withAlpha(COLOR_ACCENT, 95));
            }
            final net.minecraft.client.gui.Font font = net.minecraft.client.Minecraft.getInstance().font;
            graphics.drawCenteredString(font, this.getMessage(), this.getX() + this.width / 2, this.getY() + (this.height - 8) / 2, text);
        }
    }
}
