package com.createstats.support;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.createstats.support.SupportPolicy.Level;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

@OnlyIn(Dist.CLIENT)
public class SupportTerminalScreen extends Screen {
    private enum Tab {
        DIAGNOSTICS("Diagnostics"),
        ITEMS("Items"),
        WORLD("World");

        private final String label;

        Tab(String label) {
            this.label = label;
        }
    }

    private static final int PANEL_MAX_WIDTH = 480;
    private static final int PANEL_MAX_HEIGHT = 264;
    private static final int PADDING = 8;
    private static final int HEADER_HEIGHT = 42;
    private static final int FOOTER_HEIGHT = 14;
    private static final int LINE_HEIGHT = 9;

    private static final int COL_BACKDROP = 0xE8101418;
    private static final int COL_PANE = 0xFF0B0E11;
    private static final int COL_BORDER = 0xFF2B3540;
    private static final int COL_ACCENT = 0xFFE0A040;
    private static final int COL_TEXT = 0xFFD8DEE4;
    private static final int COL_DIM = 0xFF7A858F;
    private static final int COL_OK = 0xFF7CC47C;
    private static final int COL_LOCKED = 0xFFD07060;

    private static final int CELL = 18;

    private Tab tab = Tab.DIAGNOSTICS;

    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;

    private String reportTitle = "No report loaded";
    private List<String> reportLines = new ArrayList<>();

    private List<FormattedCharSequence> wrapped = new ArrayList<>();
    private int wrappedForWidth = -1;

    private double diagnosticsScroll;
    private double itemsScroll;

    private EditBox search;
    private List<Item> matches = new ArrayList<>();

    private boolean helloSent;

    public SupportTerminalScreen() {
        super(Component.literal("create_stats support terminal"));
    }

    public static void acceptReport(String title, List<String> lines) {
        if (Minecraft.getInstance().screen instanceof SupportTerminalScreen terminal) {
            terminal.reportTitle = title;
            terminal.reportLines = new ArrayList<>(lines);
            terminal.wrappedForWidth = -1;
            terminal.diagnosticsScroll = 0.0;
        }
    }

    public static void acceptExportSaved(int lines, String fileName) {
        if (Minecraft.getInstance().screen instanceof SupportTerminalScreen terminal) {
            terminal.reportTitle = "Export saved: " + fileName + " (" + lines + " lines)";
        }
    }

    void statusChanged() {
        this.rebuildWidgets();
    }

    private static boolean tier1() {
        return SupportClientState.level().atLeast(Level.TIER1);
    }

    private static boolean tier2() {
        return SupportClientState.level().atLeast(Level.TIER2);
    }

    @Override
    protected void init() {
        if (this.minecraft == null || !SupportClientState.canOpen(this.minecraft.player)) {
            this.onClose();
            return;
        }
        if (!this.helloSent) {
            this.helloSent = true;
            SupportClientState.markHelloSent();
            PacketDistributor.sendToServer(new SupportHelloPacket());
        }

        this.panelWidth = Math.min(PANEL_MAX_WIDTH, this.width - 2 * PADDING);
        this.panelHeight = Math.min(PANEL_MAX_HEIGHT, this.height - 2 * PADDING);
        this.panelX = (this.width - this.panelWidth) / 2;
        this.panelY = (this.height - this.panelHeight) / 2;
        this.wrappedForWidth = -1;

        int tabX = this.panelX + PADDING;
        int tabY = this.panelY + 20;
        for (Tab value : Tab.values()) {
            int tabWidth = Math.max(58, this.font.width(value.label) + 14);
            Button button = Button.builder(Component.literal(value.label), b -> {
                this.tab = value;
                this.rebuildWidgets();
            }).bounds(tabX, tabY, tabWidth, 16).build();
            button.active = this.tab != value;
            this.addRenderableWidget(button);
            tabX += tabWidth + 4;
        }

        switch (this.tab) {
            case DIAGNOSTICS -> initDiagnostics();
            case ITEMS -> initItems();
            case WORLD -> initWorld();
        }
    }

    private void initDiagnostics() {
        int x = this.panelX + PADDING;
        int y = this.panelY + HEADER_HEIGHT - 2;
        x = addAction(x, y, "Full", () -> request(SupportRequestPacket.SECTION_FULL));
        x = addAction(x, y, "Look", () -> request(SupportRequestPacket.SECTION_LOOK));
        x = addAction(x, y, "Nearby", () -> request(SupportRequestPacket.SECTION_NEARBY));
        x = addAction(x, y, "Config", () -> request(SupportRequestPacket.SECTION_CONFIG));
        x = addAction(x, y, "Logs", tier1(), () -> request(SupportRequestPacket.SECTION_LOGS));
        x = addAction(x, y, "Export", tier1(), () -> request(SupportRequestPacket.SECTION_EXPORT));
        addAction(x, y, "Copy", this::copyReport);
    }

    private void initItems() {
        int x = this.panelX + PADDING;
        int y = this.panelY + HEADER_HEIGHT - 2;

        String previous = this.search == null ? "" : this.search.getValue();
        this.search = new EditBox(this.font, x, y, this.panelWidth - 2 * PADDING, 16,
            Component.literal("Search items"));
        this.search.setHint(Component.literal("search items and blocks..."));
        this.search.setMaxLength(64);
        this.search.setValue(previous);
        this.search.setResponder(value -> {
            this.itemsScroll = 0.0;
            this.refreshMatches(value);
        });
        this.addRenderableWidget(this.search);
        this.refreshMatches(previous);
    }

    private void initWorld() {
        int x = this.panelX + PADDING;
        int y = this.panelY + HEADER_HEIGHT - 2;
        boolean live = tier2();
        x = addAction(x, y, "Survival", live, () -> act(SupportActionPacket.of(
            SupportActionPacket.VERB_GAMEMODE, "survival")));
        x = addAction(x, y, "Creative", live, () -> act(SupportActionPacket.of(
            SupportActionPacket.VERB_GAMEMODE, "creative")));
        x = addAction(x, y, "Spectator", live, () -> act(SupportActionPacket.of(
            SupportActionPacket.VERB_GAMEMODE, "spectator")));
        addAction(x, y, "Toggle fly", live, () -> act(SupportActionPacket.of(SupportActionPacket.VERB_FLY)));

        int secondRow = y + 20;
        x = this.panelX + PADDING;
        x = addAction(x, secondRow, "Heal", live, () -> act(SupportActionPacket.of(SupportActionPacket.VERB_HEAL)));
        x = addAction(x, secondRow, "Place test rig", live, () -> act(SupportActionPacket.of(
            SupportActionPacket.VERB_RIG, SupportTestRig.RIG_KINETIC)));
        x = addAction(x, secondRow, "Clear rig", live, () -> act(SupportActionPacket.of(
            SupportActionPacket.VERB_RIG, SupportTestRig.RIG_CLEAR)));

        addAction(x, secondRow, "End session", () -> act(SupportActionPacket.of(SupportActionPacket.VERB_END)));
    }

    private int addAction(int x, int y, String label, Runnable action) {
        return addAction(x, y, label, true, action);
    }

    private int addAction(int x, int y, String label, boolean active, Runnable action) {
        int buttonWidth = this.font.width(label) + 12;
        Button button = Button.builder(Component.literal(label), b -> action.run())
            .bounds(x, y, buttonWidth, 16).build();
        button.active = active;
        this.addRenderableWidget(button);
        return x + buttonWidth + 4;
    }

    private void request(String section) {
        if (SupportRequestPacket.SECTION_EXPORT.equals(section)) {
            SupportClientState.markExportRequested();
            this.reportTitle = "Export requested, saving to this machine...";
        }
        PacketDistributor.sendToServer(new SupportRequestPacket(section));
    }

    private void act(SupportActionPacket packet) {
        PacketDistributor.sendToServer(packet);
    }

    private void copyReport() {
        if (this.minecraft == null || this.reportLines.isEmpty()) {
            return;
        }
        this.minecraft.keyboardHandler.setClipboard(String.join("\n", this.reportLines));
        this.reportTitle = "Copied to clipboard: " + this.reportTitle;
    }

    private void refreshMatches(String query) {
        String needle = query.toLowerCase(Locale.ROOT).trim();
        List<Item> found = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            if (item == net.minecraft.world.item.Items.AIR) {
                continue;
            }
            if (needle.isEmpty()) {
                found.add(item);
                continue;
            }
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
            if (id.toString().toLowerCase(Locale.ROOT).contains(needle)
                || item.getDescription().getString().toLowerCase(Locale.ROOT).contains(needle)) {
                found.add(item);
            }
        }
        this.matches = found;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        graphics.fill(this.panelX, this.panelY, this.panelX + this.panelWidth,
            this.panelY + this.panelHeight, COL_BACKDROP);
        drawBorder(graphics, this.panelX, this.panelY, this.panelWidth, this.panelHeight, COL_BORDER);

        graphics.drawString(this.font, "create_stats support terminal",
            this.panelX + PADDING, this.panelY + 7, COL_ACCENT, false);

        String status = statusLine();
        int titleEnd = this.panelX + PADDING + this.font.width("create_stats support terminal") + 12;
        int room = this.panelX + this.panelWidth - PADDING - titleEnd;
        if (this.font.width(status) > room) {
            status = this.font.plainSubstrByWidth(status, room - this.font.width("...")) + "...";
        }
        graphics.drawString(this.font, status, this.panelX + this.panelWidth - PADDING - this.font.width(status),
            this.panelY + 7, tier2() ? COL_OK : COL_LOCKED, false);

        switch (this.tab) {
            case DIAGNOSTICS -> renderDiagnostics(graphics);
            case ITEMS -> renderItems(graphics, mouseX, mouseY);
            case WORLD -> renderWorld(graphics);
        }
    }

    private static String statusLine() {
        Level level = SupportClientState.level();
        if (level == Level.NONE) {
            return "waiting for server...";
        }
        String line = level + " (" + SupportClientState.basis().label() + ")";
        int seconds = SupportClientState.grantSecondsLeft();
        if (seconds >= 0) {
            line += ", grant " + Math.max(1, seconds / 60) + " min";
        }
        return line;
    }

    private void renderDiagnostics(GuiGraphics graphics) {
        int paneX = this.panelX + PADDING;
        int paneY = this.panelY + HEADER_HEIGHT + 18;
        int paneWidth = this.panelWidth - 2 * PADDING;
        int paneHeight = this.panelY + this.panelHeight - FOOTER_HEIGHT - paneY;

        graphics.drawString(this.font, this.reportTitle, paneX, paneY - 12, COL_TEXT, false);
        graphics.fill(paneX, paneY, paneX + paneWidth, paneY + paneHeight, COL_PANE);
        drawBorder(graphics, paneX, paneY, paneWidth, paneHeight, COL_BORDER);

        rewrapIfNeeded(paneWidth - 6);
        int visibleLines = paneHeight / LINE_HEIGHT;
        this.diagnosticsScroll = clampScroll(this.diagnosticsScroll, this.wrapped.size(), visibleLines);

        graphics.enableScissor(paneX + 1, paneY + 1, paneX + paneWidth - 1, paneY + paneHeight - 1);
        int first = (int) this.diagnosticsScroll;
        for (int i = 0; i < visibleLines && first + i < this.wrapped.size(); i++) {
            graphics.drawString(this.font, this.wrapped.get(first + i),
                paneX + 3, paneY + 2 + i * LINE_HEIGHT, COL_TEXT, false);
        }
        graphics.disableScissor();

        if (this.wrapped.isEmpty()) {
            graphics.drawString(this.font, "Pick a section above to pull server state.",
                paneX + 4, paneY + 4, COL_DIM, false);
        }
        footer(graphics, this.wrapped.size() + " lines - scroll to read - Export writes the full report to disk");
    }

    private void renderItems(GuiGraphics graphics, int mouseX, int mouseY) {
        int gridX = this.panelX + PADDING;
        int gridY = this.panelY + HEADER_HEIGHT + 20;
        int gridWidth = this.panelWidth - 2 * PADDING;
        int gridHeight = this.panelY + this.panelHeight - FOOTER_HEIGHT - gridY;
        int columns = Math.max(1, gridWidth / CELL);
        int rows = Math.max(1, gridHeight / CELL);

        graphics.fill(gridX, gridY, gridX + gridWidth, gridY + gridHeight, COL_PANE);
        drawBorder(graphics, gridX, gridY, gridWidth, gridHeight, COL_BORDER);

        int totalRows = (this.matches.size() + columns - 1) / columns;
        this.itemsScroll = clampScroll(this.itemsScroll, totalRows, rows);

        ItemStack hovered = ItemStack.EMPTY;
        graphics.enableScissor(gridX + 1, gridY + 1, gridX + gridWidth - 1, gridY + gridHeight - 1);
        int firstRow = (int) this.itemsScroll;
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                int index = (firstRow + row) * columns + column;
                if (index < 0 || index >= this.matches.size()) {
                    continue;
                }
                int cellX = gridX + 1 + column * CELL;
                int cellY = gridY + 1 + row * CELL;
                ItemStack stack = new ItemStack(this.matches.get(index));
                boolean over = mouseX >= cellX && mouseX < cellX + CELL
                    && mouseY >= cellY && mouseY < cellY + CELL;
                if (over) {
                    graphics.fill(cellX, cellY, cellX + CELL, cellY + CELL, 0x40FFFFFF);
                    hovered = stack;
                }
                graphics.renderItem(stack, cellX + 1, cellY + 1);
            }
        }
        graphics.disableScissor();

        if (!hovered.isEmpty()) {
            graphics.renderTooltip(this.font, hovered, mouseX, mouseY);
        }
        footer(graphics, tier2()
            ? this.matches.size() + " items - click for one, shift-click for a full stack"
            : "Tier 2 locked: " + SupportClientState.tier2Note());
    }

    private void renderWorld(GuiGraphics graphics) {
        int x = this.panelX + PADDING;
        int y = this.panelY + HEADER_HEIGHT + 42;
        int width = this.panelWidth - 2 * PADDING;
        String[] lines = {
            "The test rig places a driven kinetic network two blocks ahead of you.",
            "Clear removes only the blocks this session's rigs placed.",
            "Every action is written to the server log, unless Tier 2 logging is off.",
            "End session puts back your gamemode and flight and clears the rig. On borrowed",
            "powers (key or grant) that also happens by itself when you log out.",
        };
        for (int i = 0; i < lines.length; i++) {
            graphics.drawString(this.font, lines[i], x, y + i * 11 + (i >= 2 ? 5 : 0), COL_DIM, false);
        }
        if (!tier2()) {
            String note = "Tier 2 locked: " + SupportClientState.tier2Note();
            List<FormattedCharSequence> wrappedNote = this.font.split(Component.literal(note), width);
            int noteY = y + lines.length * 11 + 14;
            for (int i = 0; i < wrappedNote.size(); i++) {
                graphics.drawString(this.font, wrappedNote.get(i), x, noteY + i * LINE_HEIGHT, COL_LOCKED, false);
            }
        }
        footer(graphics, "World actions require Tier 2");
    }

    private void footer(GuiGraphics graphics, String text) {
        graphics.drawString(this.font, text, this.panelX + PADDING,
            this.panelY + this.panelHeight - FOOTER_HEIGHT + 3, COL_DIM, false);
    }

    private void drawBorder(GuiGraphics graphics, int x, int y, int width, int height, int colour) {
        graphics.fill(x, y, x + width, y + 1, colour);
        graphics.fill(x, y + height - 1, x + width, y + height, colour);
        graphics.fill(x, y, x + 1, y + height, colour);
        graphics.fill(x + width - 1, y, x + width, y + height, colour);
    }

    private void rewrapIfNeeded(int width) {
        if (this.wrappedForWidth == width) {
            return;
        }
        List<FormattedCharSequence> lines = new ArrayList<>();
        for (String line : this.reportLines) {
            if (line.isEmpty()) {
                lines.add(FormattedCharSequence.EMPTY);
            } else {
                lines.addAll(this.font.split(Component.literal(line), width));
            }
        }
        this.wrapped = lines;
        this.wrappedForWidth = width;
    }

    private static double clampScroll(double scroll, int total, int visible) {
        return Mth.clamp(scroll, 0.0, Math.max(0, total - visible));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (this.tab == Tab.DIAGNOSTICS) {
            this.diagnosticsScroll -= scrollY * 3.0;
            return true;
        }
        if (this.tab == Tab.ITEMS) {
            this.itemsScroll -= scrollY;
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.tab == Tab.ITEMS && button == 0 && this.handleItemClick(mouseX, mouseY)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean handleItemClick(double mouseX, double mouseY) {
        if (!tier2()) {
            return false;
        }
        int gridX = this.panelX + PADDING;
        int gridY = this.panelY + HEADER_HEIGHT + 20;
        int gridWidth = this.panelWidth - 2 * PADDING;
        int gridHeight = this.panelY + this.panelHeight - FOOTER_HEIGHT - gridY;
        if (mouseX < gridX || mouseX >= gridX + gridWidth || mouseY < gridY || mouseY >= gridY + gridHeight) {
            return false;
        }

        int columns = Math.max(1, gridWidth / CELL);
        int column = (int) ((mouseX - gridX - 1) / CELL);
        int row = (int) ((mouseY - gridY - 1) / CELL);
        if (column < 0 || column >= columns || row < 0) {
            return false;
        }

        int index = ((int) this.itemsScroll + row) * columns + column;
        if (index < 0 || index >= this.matches.size()) {
            return false;
        }

        ResourceLocation id = BuiltInRegistries.ITEM.getKey(this.matches.get(index));
        int count = hasShiftDown() ? 64 : 1;
        act(SupportActionPacket.give(id.toString(), count));
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0x66000000);
    }

    @Override
    public Component getTitle() {
        return Component.literal("create_stats support").withStyle(ChatFormatting.GOLD);
    }
}
