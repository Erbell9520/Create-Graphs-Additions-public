package com.createstats.gui;

import com.createstats.manual.FactoryManualContents;
import com.createstats.manual.FactoryManualPage;
import com.createstats.manual.ManualPlayerNotesStorage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

public class FactoryManualScreen extends Screen {
    private static final int MAX_PANEL_WIDTH = 550;
    private static final int MAX_PANEL_HEIGHT = 340;
    private static final int MIN_PANEL_WIDTH = 420;
    private static final int MIN_PANEL_HEIGHT = 270;
    private static final int INNER_MARGIN = 14;
    private static final int COLUMN_GAP = 12;
    private static final int TOC_ROW_HEIGHT = 16;
    private static final int PAGE_TURN_ZONE = 20;
    private static final int PANEL_BG = 0xFFE9D9B7;
    private static final int PANEL_INNER = 0xFFF3E8CC;
    private static final int BORDER = 0xFF705438;
    private static final int HEADER_BG = 0xFFE2D0AA;
    private static final int BRASS = 0xFFBF9150;
    private static final int TEXT = 0xFF342718;
    private static final int DIM = 0xFF7B6951;
    private static final int PAPER_RULE = 0x18A88F66;
    private static final int HOVER_FILL = 0x3ABF9150;
    private static final int PAGE_TURN_HINT = 0x885A452D;
    private static final int INVENTORY_DISPLAY_SHEETS = 8;
    private static final int FCC_SHEETS = 9;
    private static final int STATS_PANEL_SHEETS = 5;
    private static final int DATA_DIAL_SHEETS = 5;
    private static int lastSelectedPage;
    private static int lastSubpage;
    private static int lastTocScroll;

    private final List<FactoryManualPage> pages;
    private final List<TocRow> tocRows;
    private final int[] pageRowLookup;
    private int selectedPage;
    private int selectedSubpage;
    private int tocScroll;
    private List<List<FormattedCharSequence>> subpages;
    private List<FormattedCharSequence> titleLines;
    private boolean titleTrimmed;
    private ManualSection currentSection;
    private int panelLeft;
    private int panelTop;
    private int panelWidth;
    private int panelHeight;
    private int tocLeft;
    private int tocTop;
    private int tocWidth;
    private int tocHeight;
    private int tocListTop;
    private int tocVisibleRows;
    private int articleLeft;
    private int articleTop;
    private int articleWidth;
    private int articleHeight;
    private int articleBodyLeft;
    private int articleBodyTop;
    private int articleBodyWidth;
    private int articleBodyHeight;
    private int bodyLineAdvance;
    private PaperButton tocUpButton;
    private PaperButton tocDownButton;
    private PaperButton prevSubpageButton;
    private PaperButton nextSubpageButton;
    private PaperButton savePlayerNotesButton;
    private EditBox playerNotesEditor;
    private String playerNotesDraft;
    private final String initialPageId;

    public FactoryManualScreen() {
        this(null);
    }

    public FactoryManualScreen(String initialPageId) {
        super(Component.translatable("item.create_stats.factory_manual"));
        this.initialPageId = initialPageId;
        this.pages = FactoryManualContents.pages();
        this.tocRows = new ArrayList<>();
        this.pageRowLookup = new int[this.pages.size()];
        this.selectedPage = 0;
        this.selectedSubpage = 0;
        this.tocScroll = 0;
        this.subpages = List.of(Collections.emptyList());
        this.titleLines = Collections.emptyList();
        this.titleTrimmed = false;
        this.currentSection = ManualSection.BASICS;
        this.playerNotesDraft = ManualPlayerNotesStorage.get();
        this.buildTocRows();
    }

    @Override
    protected void init() {
        super.init();
        this.clearWidgets();
        this.computeLayout();
        if (this.initialPageId != null) {
            int initialIndex = this.findPageIndex(this.initialPageId);
            this.selectedPage = initialIndex >= 0 ? initialIndex : Mth.clamp(lastSelectedPage, 0, Math.max(0, this.pages.size() - 1));
            this.selectedSubpage = 0;
        } else {
            this.selectedPage = Mth.clamp(lastSelectedPage, 0, Math.max(0, this.pages.size() - 1));
            this.selectedSubpage = Math.max(0, lastSubpage);
        }
        this.tocScroll = Mth.clamp(lastTocScroll, 0, this.maxTocScroll());
        this.ensureSelectedVisible();
        this.addNavigationButtons();
        this.rebuildSubpages(true);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        this.renderTransparentBackground(guiGraphics);
        this.renderPaperFrame(guiGraphics);
        this.renderContents(guiGraphics, mouseX, mouseY);
        this.renderCurrentPage(guiGraphics, mouseX, mouseY);
        super.render(guiGraphics, mouseX, mouseY, partialTicks);
        this.renderTooltips(guiGraphics, mouseX, mouseY);
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        int clickedIndex = this.getTocIndexAt(mouseX, mouseY);
        if (clickedIndex >= 0) {
            this.selectPage(clickedIndex);
            return true;
        }
        if (this.isFlightControlComputerGuidePage() && this.isInsideArticle(mouseX, mouseY) && this.handleFccGuideClick(mouseX, mouseY)) {
            return true;
        }
        if (this.isFigurePage() && this.isInsideArticle(mouseX, mouseY) && this.handleFigureGuideClick(mouseX, mouseY)) {
            return true;
        }
        if (this.isStatsPanelGuidePage() && this.isInsideArticle(mouseX, mouseY) && this.handleFigureGuideClick(mouseX, mouseY)) {
            return true;
        }
        if (this.isDataDialGuidePage() && this.isInsideArticle(mouseX, mouseY) && this.handleFigureGuideClick(mouseX, mouseY)) {
            return true;
        }
        if (this.isInsideArticle(mouseX, mouseY)) {
            if (mouseX <= this.articleLeft + PAGE_TURN_ZONE && this.advanceReading(-1)) {
                return true;
            }
            if (mouseX >= this.articleLeft + this.articleWidth - PAGE_TURN_ZONE && this.advanceReading(1)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (this.isInsideContents(mouseX, mouseY) && this.maxTocScroll() > 0) {
            this.scrollContents(scrollY > 0.0D ? -1 : 1);
            return true;
        }
        if (this.isInsideArticle(mouseX, mouseY)) {
            return this.advanceReading(scrollY > 0.0D ? -1 : 1) || super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (super.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (this.isPlayerNotesPage() && this.playerNotesEditor != null && this.playerNotesEditor.isFocused()) {
            return true;
        }
        return switch (keyCode) {
            case GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_PAGE_UP -> this.advanceReading(-1);
            case GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_PAGE_DOWN, GLFW.GLFW_KEY_SPACE -> this.advanceReading(1);
            case GLFW.GLFW_KEY_UP -> this.changePage(-1);
            case GLFW.GLFW_KEY_DOWN -> this.changePage(1);
            case GLFW.GLFW_KEY_HOME -> this.selectPage(0);
            case GLFW.GLFW_KEY_END -> this.selectPage(this.pages.size() - 1);
            default -> false;
        };
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void removed() {
        lastSelectedPage = this.selectedPage;
        lastSubpage = this.selectedSubpage;
        lastTocScroll = this.tocScroll;
        this.capturePlayerNotesDraft();
        if (!this.playerNotesDraft.equals(ManualPlayerNotesStorage.get())) {
            ManualPlayerNotesStorage.set(this.playerNotesDraft);
        }
        super.removed();
    }

    private void computeLayout() {
        int widthLimit = Math.max(260, this.width - 20);
        int heightLimit = Math.max(190, this.height - 16);
        this.panelWidth = Math.min(MAX_PANEL_WIDTH, Math.max(MIN_PANEL_WIDTH, widthLimit));
        this.panelHeight = Math.min(MAX_PANEL_HEIGHT, Math.max(MIN_PANEL_HEIGHT, heightLimit));
        this.panelWidth = Math.min(this.panelWidth, this.width - 10);
        this.panelHeight = Math.min(this.panelHeight, this.height - 10);
        this.panelLeft = (this.width - this.panelWidth) / 2;
        this.panelTop = Math.max(6, (this.height - this.panelHeight) / 2 - 4);

        int sectionTop = this.panelTop + 30;
        int sectionBottom = this.panelTop + this.panelHeight - 32;
        this.tocLeft = this.panelLeft + INNER_MARGIN;
        this.tocTop = sectionTop;
        this.tocHeight = sectionBottom - sectionTop;
        this.tocWidth = Mth.clamp(this.panelWidth / 3, 132, 152);
        this.articleLeft = this.tocLeft + this.tocWidth + COLUMN_GAP;
        this.articleTop = sectionTop;
        this.articleWidth = this.panelLeft + this.panelWidth - INNER_MARGIN - this.articleLeft;
        this.articleHeight = sectionBottom - sectionTop;
        this.tocListTop = this.tocTop + 24;
        this.tocVisibleRows = Math.max(1, (this.tocHeight - 34) / TOC_ROW_HEIGHT);
    }

    private void addNavigationButtons() {
        int tocButtonY = this.tocTop + 4;
        int buttonStep = Math.max(1, this.tocVisibleRows - 2);
        this.tocUpButton = this.addRenderableWidget(new PaperButton(
                this.tocLeft + this.tocWidth - 46, tocButtonY, 20, 16,
                Component.literal("▲"), b -> this.scrollContents(-buttonStep), PaperButtonStyle.ACCENT));
        this.tocDownButton = this.addRenderableWidget(new PaperButton(
                this.tocLeft + this.tocWidth - 24, tocButtonY, 20, 16,
                Component.literal("▼"), b -> this.scrollContents(buttonStep), PaperButtonStyle.ACCENT));

        int footerY = this.panelTop + this.panelHeight - 24;
        this.addRenderableWidget(new PaperButton(
                this.panelLeft + INNER_MARGIN, footerY, 66, 18,
                Component.translatable("gui.create_stats.factory_manual.done"), b -> this.onClose(), PaperButtonStyle.NEUTRAL));
        this.prevSubpageButton = this.addRenderableWidget(new PaperButton(
                this.panelLeft + this.panelWidth - 124, footerY, 54, 18,
                Component.translatable("gui.create_stats.factory_manual.previous"), b -> this.advanceReading(-1), PaperButtonStyle.ACCENT));
        this.nextSubpageButton = this.addRenderableWidget(new PaperButton(
                this.panelLeft + this.panelWidth - 64, footerY, 54, 18,
                Component.translatable("gui.create_stats.factory_manual.next"), b -> this.advanceReading(1), PaperButtonStyle.ACCENT));

        this.savePlayerNotesButton = this.addRenderableWidget(new PaperButton(
            this.panelLeft + INNER_MARGIN + 72, footerY, 92, 18,
            Component.translatable("gui.create_stats.factory_manual.player_notes.save"), b -> this.savePlayerNotes(), PaperButtonStyle.ACCENT));

        this.playerNotesEditor = this.addRenderableWidget(new EditBox(this.font,
            this.articleBodyLeft, this.articleBodyTop,
            this.articleBodyWidth, Math.max(18, this.articleBodyHeight - 24),
            Component.translatable("gui.create_stats.factory_manual.player_notes.placeholder")));
        this.playerNotesEditor.setMaxLength(4096);
        this.playerNotesEditor.setValue(this.playerNotesDraft);
        this.playerNotesEditor.setResponder(text -> this.playerNotesDraft = text);
        this.playerNotesEditor.setBordered(false);
        this.playerNotesEditor.setVisible(false);
        this.playerNotesEditor.setCanLoseFocus(true);
    }

    private void renderPaperFrame(GuiGraphics guiGraphics) {
        guiGraphics.fill(this.panelLeft, this.panelTop, this.panelLeft + this.panelWidth, this.panelTop + this.panelHeight, PANEL_BG);
        guiGraphics.fill(this.panelLeft + 2, this.panelTop + 2, this.panelLeft + this.panelWidth - 2, this.panelTop + this.panelHeight - 2, PANEL_INNER);
        drawOutline(guiGraphics, this.panelLeft, this.panelTop, this.panelWidth, this.panelHeight, BORDER);

        guiGraphics.fill(this.panelLeft + 8, this.panelTop + 10, this.panelLeft + 14, this.panelTop + this.panelHeight - 10, 0x55B08B57);
        for (int y = this.panelTop + 22; y < this.panelTop + this.panelHeight - 12; y += 34) {
            guiGraphics.fill(this.panelLeft + 8, y, this.panelLeft + 14, y + 5, BRASS);
            guiGraphics.fill(this.panelLeft + 9, y + 1, this.panelLeft + 13, y + 4, PANEL_BG);
        }

        int plateWidth = 118;
        int plateLeft = this.width / 2 - plateWidth / 2;
        guiGraphics.fill(plateLeft, this.panelTop - 6, plateLeft + plateWidth, this.panelTop + 8, BRASS);
        guiGraphics.fill(plateLeft + 8, this.panelTop - 3, plateLeft + plateWidth - 8, this.panelTop + 5, 0xFFE0BE84);
        guiGraphics.fill(plateLeft + 12, this.panelTop - 1, plateLeft + 16, this.panelTop + 3, BORDER);
        guiGraphics.fill(plateLeft + plateWidth - 16, this.panelTop - 1, plateLeft + plateWidth - 12, this.panelTop + 3, BORDER);

        this.renderPaperSection(guiGraphics, this.tocLeft, this.tocTop, this.tocLeft + this.tocWidth, this.tocTop + this.tocHeight, PANEL_INNER);
        this.renderPaperSection(guiGraphics, this.articleLeft, this.articleTop, this.articleLeft + this.articleWidth, this.articleTop + this.articleHeight, PANEL_INNER);

        this.drawPaperCenteredText(guiGraphics, this.title.getString(), this.width / 2, this.panelTop - 1, TEXT);
        this.drawPaperText(guiGraphics, Component.translatable("gui.create_stats.factory_manual.subtitle").getString(),
                this.panelLeft + 22, this.panelTop + 10, DIM);
    }

    private void renderContents(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int textWidth = this.tocWidth - 68;
        this.drawPaperText(guiGraphics, Component.translatable("gui.create_stats.factory_manual.contents").getString(),
                this.tocLeft + 8, this.tocTop + 8, TEXT);
        String countLabel = Component.translatable("gui.create_stats.factory_manual.entry_count", this.pages.size()).getString();
        this.drawPaperText(guiGraphics, countLabel,
                this.tocLeft + this.tocWidth - 8 - this.font.width(countLabel), this.tocTop + 8, DIM);

        for (int i = 0; i < this.tocVisibleRows; i++) {
            int rowIndex = this.tocScroll + i;
            if (rowIndex >= this.tocRows.size()) {
                break;
            }
            TocRow tocRow = this.tocRows.get(rowIndex);
            int rowY = this.tocListTop + i * TOC_ROW_HEIGHT;
            if (tocRow.isHeading()) {
                this.renderSectionHeading(guiGraphics, tocRow.section(), rowY);
                continue;
            }

            FactoryManualPage page = this.pages.get(tocRow.pageIndex());
            boolean selected = tocRow.pageIndex() == this.selectedPage;
            boolean hovered = tocRow.pageIndex() == this.getTocIndexAt(mouseX, mouseY);
            int fill = selected ? this.withAlpha(tocRow.section().tintColor(), 72) : (hovered ? HOVER_FILL : 0);
            if (fill != 0) {
                guiGraphics.fill(this.tocLeft + 4, rowY - 1, this.tocLeft + this.tocWidth - 4, rowY + TOC_ROW_HEIGHT - 1, fill);
            }
            if (selected) {
                guiGraphics.fill(this.tocLeft + 4, rowY - 1, this.tocLeft + 7, rowY + TOC_ROW_HEIGHT - 1, tocRow.section().tintColor());
            }

            ItemStack icon = page.iconStack();
            guiGraphics.renderItem(icon, this.tocLeft + 9, rowY);

            String indexLabel = Integer.toString(tocRow.pageIndex() + 1);
            int indexX = this.tocLeft + this.tocWidth - 8 - this.font.width(indexLabel);
            this.drawPaperText(guiGraphics, indexLabel, indexX, rowY + 4, DIM);
            this.drawPaperText(guiGraphics, this.trim(page.title().getString(), textWidth),
                    this.tocLeft + 28, rowY + 4, selected ? TEXT : 0xFF473527);
        }
    }

    private void renderCurrentPage(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        FactoryManualPage page = this.pages.get(this.selectedPage);
        int headerBottom = this.articleBodyTop - 8;
        guiGraphics.fill(this.articleLeft + 1, this.articleTop + 1, this.articleLeft + this.articleWidth - 1, headerBottom, HEADER_BG);
        guiGraphics.fill(this.articleLeft + 8, headerBottom, this.articleLeft + this.articleWidth - 8, headerBottom + 1, BORDER);

        this.renderStatusChip(guiGraphics, this.articleLeft + 10, this.articleTop + 8,
                this.currentSection.title().getString(), this.currentSection.tintColor());
        String pageLabel = Component.translatable("gui.create_stats.factory_manual.page", this.selectedPage + 1, this.pages.size()).getString();
        this.drawPaperText(guiGraphics, pageLabel,
                this.articleLeft + this.articleWidth - 10 - this.font.width(pageLabel), this.articleTop + 11, DIM);

        ItemStack icon = page.iconStack();
        guiGraphics.renderItem(icon, this.articleLeft + 10, this.articleTop + 28);
        for (int i = 0; i < this.titleLines.size(); i++) {
            guiGraphics.drawString(this.font, this.titleLines.get(i), this.articleLeft + 34,
                    this.articleTop + 27 + i * (this.font.lineHeight + 1), TEXT, false);
        }

        for (int y = this.articleBodyTop + this.font.lineHeight + 2; y < this.articleBodyTop + this.articleBodyHeight; y += this.bodyLineAdvance) {
            guiGraphics.fill(this.articleBodyLeft, y, this.articleBodyLeft + this.articleBodyWidth, y + 1, PAPER_RULE);
        }

        if (this.isPlayerNotesPage()) {
            String helper = Component.translatable("gui.create_stats.factory_manual.player_notes.edit_hint").getString();
            this.drawPaperText(guiGraphics, helper, this.articleBodyLeft, this.articleBodyTop - 10, DIM);
        } else if (this.isInventoryDisplayGuidePage()) {
            this.renderInventoryDisplayGuide(guiGraphics);
        } else if (this.isFlightControlComputerGuidePage()) {
            this.renderFccGuide(guiGraphics);
        } else if (this.isStatsPanelGuidePage()) {
            this.renderStatsPanelGuide(guiGraphics);
        } else if (this.isDataDialGuidePage()) {
            this.renderDataDialGuide(guiGraphics);
        } else if (this.isFigurePage()) {
            this.renderFigureGuide(guiGraphics);
        } else {
            List<FormattedCharSequence> lines = this.subpages.get(this.selectedSubpage);
            for (int i = 0; i < lines.size(); i++) {
                guiGraphics.drawString(this.font, lines.get(i), this.articleBodyLeft,
                        this.articleBodyTop + i * this.bodyLineAdvance, TEXT, false);
            }
        }

        String sheetLabel = Component.translatable("gui.create_stats.factory_manual.subpage", this.selectedSubpage + 1, this.subpages.size()).getString();
        this.drawPaperText(guiGraphics, sheetLabel,
                this.articleLeft + this.articleWidth - 10 - this.font.width(sheetLabel),
                this.articleTop + this.articleHeight - 16, DIM);

        String shortcutLabel = this.trim(Component.translatable("gui.create_stats.factory_manual.shortcuts").getString(),
                Math.max(96, this.panelWidth - 196));
        this.drawPaperCenteredText(guiGraphics, shortcutLabel, this.width / 2, this.panelTop + this.panelHeight - 20, DIM);
        this.updatePlayerNotesWidgetState();
        this.renderPageTurnHints(guiGraphics, mouseX, mouseY);
    }

    private void renderTooltips(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int tocIndex = this.getTocIndexAt(mouseX, mouseY);
        if (tocIndex >= 0) {
            FactoryManualPage hoveredPage = this.pages.get(tocIndex);
            int titleWidth = this.tocWidth - 68;
            if (this.font.width(hoveredPage.title()) > titleWidth) {
                guiGraphics.renderTooltip(this.font,
                        List.of(hoveredPage.title().copy(), ManualSection.forPage(hoveredPage.id()).title().copy().withStyle(ChatFormatting.GRAY)),
                        Optional.empty(), mouseX, mouseY);
                return;
            }
        }
        if (this.titleTrimmed && this.isHoveringTitle(mouseX, mouseY)) {
            FactoryManualPage page = this.pages.get(this.selectedPage);
            guiGraphics.renderTooltip(this.font,
                    List.of(page.title().copy(), this.currentSection.title().copy().withStyle(ChatFormatting.GRAY)),
                    Optional.empty(), mouseX, mouseY);
        }
    }

    private void renderSectionHeading(GuiGraphics guiGraphics, ManualSection section, int rowY) {
        int labelX = this.tocLeft + 8;
        int labelY = rowY + 4;
        String label = section.title().getString();
        this.drawPaperText(guiGraphics, label, labelX, labelY, this.paperInk(section.tintColor()));
        int lineStart = labelX + this.font.width(label) + 6;
        guiGraphics.fill(lineStart, rowY + 8, this.tocLeft + this.tocWidth - 8, rowY + 9, this.withAlpha(section.tintColor(), 140));
    }

    private void renderPageTurnHints(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int hintY = this.articleBodyTop + this.articleBodyHeight / 2;
        boolean hoverPrev = mouseX >= this.articleLeft && mouseX <= this.articleLeft + PAGE_TURN_ZONE && this.isInsideArticle(mouseX, mouseY);
        boolean hoverNext = mouseX >= this.articleLeft + this.articleWidth - PAGE_TURN_ZONE
                && mouseX <= this.articleLeft + this.articleWidth && this.isInsideArticle(mouseX, mouseY);
        if (this.canAdvanceReading(-1)) {
            this.drawPaperText(guiGraphics, "◀", this.articleLeft + 6, hintY, hoverPrev ? TEXT : PAGE_TURN_HINT);
        }
        if (this.canAdvanceReading(1)) {
            this.drawPaperText(guiGraphics, "▶", this.articleLeft + this.articleWidth - 12, hintY, hoverNext ? TEXT : PAGE_TURN_HINT);
        }
    }

    private void renderInventoryDisplayGuide(GuiGraphics guiGraphics) {
        int x = this.articleBodyLeft;
        int y = this.articleBodyTop;
        int w = this.articleBodyWidth;
        int h = this.articleBodyHeight;
        guiGraphics.fill(x, y, x + w, y + h, 0x55DCC79B);
        drawOutline(guiGraphics, x, y, w, h, 0x88705438);
        switch (this.selectedSubpage) {
            case 0 -> this.renderInventoryLessonAim(guiGraphics, x, y, w, h);
            case 1 -> this.renderInventoryLessonSources(guiGraphics, x, y, w, h);
            case 2 -> this.renderInventoryLessonModePriority(guiGraphics, x, y, w, h);
            case 3 -> this.renderInventoryLessonDirectBridge(guiGraphics, x, y, w, h);
            case 4 -> this.renderInventoryLessonItems(guiGraphics, x, y, w, h);
            case 5 -> this.renderInventoryLessonFluidEnergy(guiGraphics, x, y, w, h);
            case 6 -> this.renderInventoryLessonDiesel(guiGraphics, x, y, w, h);
            default -> this.renderInventoryLessonTroubleshoot(guiGraphics, x, y, w, h);
        }
    }

    private void renderInventoryLessonAim(GuiGraphics guiGraphics, int x, int y, int w, int h) {
        this.drawLessonTitle(guiGraphics, x, y, "1. Point the screen at one target");
        int sourceX = x + 10;
        int displayX = x + w / 2 - 20;
        int panelX = x + w - 66;
        int midY = y + h / 2 - 18;
        this.drawSourceBlock(guiGraphics, sourceX, midY, "Chest", 0xFFE2B46E);
        this.drawInventoryDisplayIcon(guiGraphics, displayX, midY - 3, true);
        this.drawMiniPanel(guiGraphics, panelX, midY - 5, "Panel", "view", 0xFF7E9352);
        this.drawAnimatedArrow(guiGraphics, sourceX + 42, displayX - 3, midY + 18, 0xFF4A7C8F, true);
        this.drawAnimatedArrow(guiGraphics, displayX + 45, panelX - 4, midY + 18, 0xFFB9843E, true);
        this.drawLessonChip(guiGraphics, x + 10, y + h - 32, "Place", 0xFFB9843E);
        this.drawLessonChip(guiGraphics, x + 62, y + h - 32, "Face", 0xFF7E9352);
        this.drawLessonChip(guiGraphics, x + 108, y + h - 32, "Open", 0xFF4A7C8F);
    }

    private void renderInventoryLessonSources(GuiGraphics guiGraphics, int x, int y, int w, int h) {
        this.drawLessonTitle(guiGraphics, x, y, "2. What it can read");
        String[] labels = {"Items", "Belts", "Fluids", "Boiler", "Energy", "Link", "Diesel"};
        int[] colors = {0xFFE2B46E, 0xFF9B7C4F, 0xFF4A8F8B, 0xFFA26B3B, 0xFF5E8F55, 0xFF8B6C96, 0xFFC47A35};
        int cols = 4;
        int tileW = Math.max(42, (w - 18) / cols);
        int top = y + 24;
        for (int i = 0; i < labels.length; i++) {
            int tx = x + 8 + (i % cols) * tileW;
            int ty = top + (i / cols) * 36;
            this.drawSourceTile(guiGraphics, tx, ty, tileW - 6, 28, labels[i], colors[i], i);
        }
        int displayX = x + w - 56;
        int displayY = y + h - 46;
        this.drawInventoryDisplayIcon(guiGraphics, displayX, displayY, true);
        this.drawAnimatedArrow(guiGraphics, x + 24, displayX - 2, displayY + 18, 0xFF4A7C8F, true);
        this.drawTinyText(guiGraphics, this.trim("One target in, best page out", w - 18), x + 8, y + h - 15, DIM);
    }

    private void renderInventoryLessonModePriority(GuiGraphics guiGraphics, int x, int y, int w, int h) {
        this.drawLessonTitle(guiGraphics, x, y, "3. It chooses the clearest page");
        String[] modes = {"Diesel plant", "Energy", "Items", "Fluids", "Link text"};
        int[] colors = {0xFFC47A35, 0xFF5E8F55, 0xFFE2B46E, 0xFF4A8F8B, 0xFF8B6C96};
        int rowH = Math.max(17, (h - 38) / modes.length);
        int pulse = Math.floorMod((int)(net.minecraft.Util.getMillis() / 420L), modes.length);
        for (int i = 0; i < modes.length; i++) {
            int rowY = y + 24 + i * rowH;
            int color = i == pulse ? colors[i] : 0xFF9D8B68;
            guiGraphics.fill(x + 14, rowY, x + w - 18, rowY + rowH - 3, i == pulse ? 0x55BF9150 : 0x22A88F66);
            drawOutline(guiGraphics, x + 14, rowY, w - 32, rowH - 3, color);
            this.drawPaperText(guiGraphics, Integer.toString(i + 1), x + 22, rowY + 4, color);
            this.drawPaperText(guiGraphics, modes[i], x + 42, rowY + 4, TEXT);
            if (i == pulse) {
                this.drawLessonChip(guiGraphics, x + w - 76, rowY + 2, "shown", color);
            }
        }
    }

    private void renderInventoryLessonDirectBridge(GuiGraphics guiGraphics, int x, int y, int w, int h) {
        this.drawLessonTitle(guiGraphics, x, y, "4. Direct nearby, Bridge remote");
        int rowOne = y + 40;
        int rowTwo = y + 90;
        this.drawTinyText(guiGraphics, "Direct", x + 12, rowOne - 12, TEXT);
        this.drawSourceBlock(guiGraphics, x + 14, rowOne, "Target", 0xFFE2B46E);
        this.drawInventoryDisplayIcon(guiGraphics, x + w - 58, rowOne - 2, true);
        this.drawAnimatedArrow(guiGraphics, x + 58, x + w - 62, rowOne + 18, 0xFF7E9352, true);
        this.drawTinyText(guiGraphics, "Bridge", x + 12, rowTwo - 12, TEXT);
        this.drawSourceBlock(guiGraphics, x + 14, rowTwo, "Remote", 0xFF8B6C96);
        this.drawMiniPanel(guiGraphics, x + w / 2 - 27, rowTwo, "Link", "points", 0xFF4A7C8F);
        this.drawInventoryDisplayIcon(guiGraphics, x + w - 58, rowTwo - 2, true);
        this.drawAnimatedArrow(guiGraphics, x + 58, x + w / 2 - 30, rowTwo + 18, 0xFF8B6C96, true);
        this.drawAnimatedArrow(guiGraphics, x + w / 2 + 28, x + w - 62, rowTwo + 18, 0xFF4A7C8F, true);
    }

    private void renderInventoryLessonItems(GuiGraphics guiGraphics, int x, int y, int w, int h) {
        this.drawLessonTitle(guiGraphics, x, y, "5. Items and belts become counts");
        int beltY = y + 58;
        guiGraphics.fill(x + 12, beltY, x + w - 12, beltY + 18, 0xFF6D5122);
        for (int px = x + 18; px < x + w - 20; px += 18) {
            guiGraphics.fill(px, beltY + 3, px + 10, beltY + 15, 0xFF3C3024);
        }
        int moving = Math.floorMod((int)(net.minecraft.Util.getMillis() / 80L), Math.max(1, w - 62));
        guiGraphics.fill(x + 22 + moving, beltY - 8, x + 34 + moving, beltY + 4, 0xFFE2B46E);
        guiGraphics.fill(x + 22 + moving, beltY - 8, x + 34 + moving, beltY - 6, 0xFFFFE0A2);
        this.drawMiniPanel(guiGraphics, x + w - 70, y + 28, "Top item", "x64", 0xFFE2B46E);
        this.drawMiniPanel(guiGraphics, x + w - 70, y + 78, "Types", "3", 0xFF7E9352);
        this.drawAnimatedArrow(guiGraphics, x + 30, x + w - 75, beltY + 9, 0xFFB9843E, true);
        this.drawTinyText(guiGraphics, this.trim("Chests, vaults and item handlers use this idea.", w - 20), x + 10, y + h - 16, DIM);
    }

    private void renderInventoryLessonFluidEnergy(GuiGraphics guiGraphics, int x, int y, int w, int h) {
        this.drawLessonTitle(guiGraphics, x, y, "6. Fluids and energy show flow");
        int tankX = x + 18;
        int tankY = y + 34;
        int tankH = h - 58;
        guiGraphics.fill(tankX, tankY, tankX + 44, tankY + tankH, 0xFFE8D7AE);
        drawOutline(guiGraphics, tankX, tankY, 44, tankH, 0xFF4A8F8B);
        int fill = Math.round((tankH - 8) * (0.55f + 0.12f * Mth.sin(net.minecraft.Util.getMillis() / 350.0f)));
        guiGraphics.fill(tankX + 4, tankY + tankH - 4 - fill, tankX + 40, tankY + tankH - 4, 0xAA4A8F8B);
        this.drawTinyText(guiGraphics, "mB", tankX + 15, tankY + tankH / 2, TEXT);
        int batteryX = x + w - 98;
        int batteryY = y + 52;
        guiGraphics.fill(batteryX, batteryY, batteryX + 72, batteryY + 34, 0xFFE8D7AE);
        drawOutline(guiGraphics, batteryX, batteryY, 72, 34, 0xFF5E8F55);
        guiGraphics.fill(batteryX + 72, batteryY + 10, batteryX + 78, batteryY + 24, 0xFF5E8F55);
        int charge = Math.floorMod((int)(net.minecraft.Util.getMillis() / 24L), 60);
        guiGraphics.fill(batteryX + 5, batteryY + 5, batteryX + 5 + charge, batteryY + 29, 0xAA5E8F55);
        this.drawAnimatedArrow(guiGraphics, tankX + 52, batteryX - 6, y + h / 2, 0xFF4A7C8F, true);
        this.drawTinyText(guiGraphics, "Tank: amount / capacity", x + 12, y + h - 26, DIM);
        this.drawTinyText(guiGraphics, "Battery: input - output = net", x + 12, y + h - 14, DIM);
    }

    private void renderInventoryLessonDiesel(GuiGraphics guiGraphics, int x, int y, int w, int h) {
        this.drawLessonTitle(guiGraphics, x, y, "7. Diesel engines become a machine diagram");
        int tankX = x + 12;
        int tankY = y + 42;
        guiGraphics.fill(tankX, tankY, tankX + 30, y + h - 28, 0xFFE8D7AE);
        drawOutline(guiGraphics, tankX, tankY, 30, h - 70, 0xFFC47A35);
        guiGraphics.fill(tankX + 4, y + h - 58, tankX + 26, y + h - 34, 0xAAC47A35);
        int engineX = x + 58;
        int engineY = y + 38;
        int engineW = Math.max(80, w - 126);
        int engineH = h - 66;
        guiGraphics.fill(engineX, engineY, engineX + engineW, engineY + engineH, 0xFFE2D0AA);
        drawOutline(guiGraphics, engineX, engineY, engineW, engineH, 0xFFA26B3B);
        double time = net.minecraft.Util.getMillis() / 170.0;
        for (int piston = 0; piston < 4; piston++) {
            int px = engineX + 14 + piston * Math.max(13, (engineW - 28) / 4);
            int py = engineY + 28 + (int)Math.round((Math.sin(time + piston) + 1.0) * 8.0);
            guiGraphics.fill(px, engineY + 18, px + 9, engineY + engineH - 13, 0x88705438);
            guiGraphics.fill(px + 1, py, px + 8, py + 8, 0xFFC47A35);
        }
        this.drawAnimatedArrow(guiGraphics, tankX + 32, engineX - 2, y + h / 2, 0xFFC47A35, true);
        this.drawMiniPanel(guiGraphics, x + w - 60, y + 38, "RPM", "speed", 0xFF4A7C8F);
        this.drawMiniPanel(guiGraphics, x + w - 60, y + 78, "SU", "power", 0xFF7E9352);
        this.drawTinyText(guiGraphics, this.trim("Single reads one controller. Multi reads the plant.", w - 20), x + 10, y + h - 16, DIM);
    }

    private void renderInventoryLessonTroubleshoot(GuiGraphics guiGraphics, int x, int y, int w, int h) {
        this.drawLessonTitle(guiGraphics, x, y, "8. Blank? Follow the lights");
        String[] checks = {"Face target", "Bridge status", "Readable data"};
        String[] fixes = {"screen points at block", "link or observer points here", "items, fluids, energy or diesel"};
        int[] colors = {0xFF7E9352, 0xFFB9843E, 0xFF4A7C8F};
        int cardH = Math.max(28, (h - 46) / 3);
        int active = Math.floorMod((int)(net.minecraft.Util.getMillis() / 520L), checks.length);
        for (int i = 0; i < checks.length; i++) {
            int cy = y + 28 + i * cardH;
            guiGraphics.fill(x + 12, cy, x + w - 12, cy + cardH - 6, i == active ? 0x55BF9150 : 0x22A88F66);
            drawOutline(guiGraphics, x + 12, cy, w - 24, cardH - 6, colors[i]);
            guiGraphics.fill(x + 20, cy + 8, x + 30, cy + 18, colors[i]);
            this.drawPaperText(guiGraphics, checks[i], x + 38, cy + 5, TEXT);
            this.drawTinyText(guiGraphics, this.trim(fixes[i], Math.max(20, w - 58)), x + 38, cy + 17, DIM);
        }
    }

    private void drawLessonTitle(GuiGraphics guiGraphics, int x, int y, String title) {
        guiGraphics.fill(x + 6, y + 6, x + this.articleBodyWidth - 6, y + 20, 0x66E2D0AA);
        this.drawPaperText(guiGraphics, this.trim(title, this.articleBodyWidth - 18), x + 10, y + 9, TEXT);
    }

    private void drawSourceBlock(GuiGraphics guiGraphics, int x, int y, String label, int color) {
        guiGraphics.fill(x, y, x + 42, y + 36, 0xFFE8D7AE);
        drawOutline(guiGraphics, x, y, 42, 36, color);
        guiGraphics.fill(x + 6, y + 7, x + 36, y + 23, 0x55A88F66);
        this.drawTinyText(guiGraphics, this.trim(label, 36), x + 5, y + 25, TEXT);
    }

    private void drawInventoryDisplayIcon(GuiGraphics guiGraphics, int x, int y, boolean glow) {
        guiGraphics.fill(x + 5, y + 8, x + 39, y + 35, 0xFFE8D7AE);
        drawOutline(guiGraphics, x + 5, y + 8, 34, 27, 0xFF705438);
        guiGraphics.fill(x + 11, y + 2, x + 35, y + 18, glow ? 0xFF4A8F8B : 0xFF8A7A63);
        drawOutline(guiGraphics, x + 11, y + 2, 24, 16, 0xFF234A4C);
        guiGraphics.fill(x + 14, y + 5, x + 32, y + 8, 0xCCF3E8CC);
        guiGraphics.fill(x + 14, y + 11, x + 28, y + 13, 0xCCF3E8CC);
    }

    private void drawMiniPanel(GuiGraphics guiGraphics, int x, int y, String top, String bottom, int color) {
        guiGraphics.fill(x, y, x + 54, y + 34, 0xFFE8D7AE);
        drawOutline(guiGraphics, x, y, 54, 34, color);
        this.drawTinyText(guiGraphics, this.trim(top, 46), x + 5, y + 6, TEXT);
        this.drawTinyText(guiGraphics, this.trim(bottom, 46), x + 5, y + 19, this.paperInk(color));
    }

    private void drawSourceTile(GuiGraphics guiGraphics, int x, int y, int w, int h, String label, int color, int offset) {
        boolean lit = Math.floorMod((int)(net.minecraft.Util.getMillis() / 360L) + offset, 7) == 0;
        guiGraphics.fill(x, y, x + w, y + h, lit ? 0x66BF9150 : 0x22A88F66);
        drawOutline(guiGraphics, x, y, w, h, color);
        guiGraphics.fill(x + 5, y + 6, x + 15, y + 16, color);
        this.drawTinyText(guiGraphics, this.trim(label, Math.max(12, w - 24)), x + 19, y + 9, TEXT);
    }

    private void drawLessonChip(GuiGraphics guiGraphics, int x, int y, String label, int color) {
        int width = this.font.width(label) + 10;
        guiGraphics.fill(x, y, x + width, y + 14, 0xE8F1E5C6);
        drawOutline(guiGraphics, x, y, width, 14, color);
        this.drawTinyText(guiGraphics, label, x + 5, y + 3, this.paperInk(color));
    }

    private void drawAnimatedArrow(GuiGraphics guiGraphics, int startX, int endX, int y, int color, boolean toRight) {
        int left = Math.min(startX, endX);
        int right = Math.max(startX, endX);
        guiGraphics.fill(left, y, right, y + 2, 0x88705438);
        int span = Math.max(6, right - left - 6);
        int phase = Math.floorMod((int)(net.minecraft.Util.getMillis() / 40L), span);
        int pulseX = toRight ? left + 3 + phase : right - 3 - phase;
        guiGraphics.fill(pulseX - 2, y - 2, pulseX + 3, y + 5, color);
        if (toRight) {
            guiGraphics.fill(right - 4, y - 3, right, y + 5, color);
        } else {
            guiGraphics.fill(left, y - 3, left + 4, y + 5, color);
        }
    }

    private void drawTinyText(GuiGraphics guiGraphics, String text, int x, int y, int color) {
        guiGraphics.drawString(this.font, text, x, y, color, false);
    }

    private boolean isInventoryDisplayGuidePage() {
        if (this.selectedPage < 0 || this.selectedPage >= this.pages.size()) {
            return false;
        }
        return "production_detector".equals(this.pages.get(this.selectedPage).id());
    }

    // bespoke lesson: Stats Display Panel. Each sheet pairs a distinct diagram with a plain, friendly paragraph explanation (no truncation, fully wrapped). Navigation via pip strip.

    private boolean isStatsPanelGuidePage() {
        if (this.selectedPage < 0 || this.selectedPage >= this.pages.size()) {
            return false;
        }
        return "stats_display_panel".equals(this.pages.get(this.selectedPage).id());
    }

    private void renderStatsPanelGuide(GuiGraphics guiGraphics) {
        int x = this.articleBodyLeft;
        int y = this.articleBodyTop;
        int w = this.articleBodyWidth;
        int h = this.articleBodyHeight;
        guiGraphics.fill(x, y, x + w, y + h, 0x55DCC79B);
        drawOutline(guiGraphics, x, y, w, h, 0x88705438);

        int titleH = 18;
        int footer = 18; // pip strip
        int contentTop = y + titleH + 2;
        int contentBottom = y + h - footer;
        switch (this.selectedSubpage) {
            case 0 -> this.renderStatsPanelWhat(guiGraphics, x, w, contentTop, contentBottom);
            case 1 -> this.renderStatsPanelOpen(guiGraphics, x, w, contentTop, contentBottom);
            case 2 -> this.renderStatsPanelSwitch(guiGraphics, x, w, contentTop, contentBottom);
            case 3 -> this.renderStatsPanelBigger(guiGraphics, x, w, contentTop, contentBottom);
            default -> this.renderStatsPanelCopy(guiGraphics, x, w, contentTop, contentBottom);
        }
        this.renderFigureSheetPips(guiGraphics, x, y, w, h);
    }

    private void renderStatsPanelWhat(GuiGraphics guiGraphics, int x, int w, int top, int bottom) {
        this.drawLessonTitle(guiGraphics, x, top - 18, "1. A screen for your factory");
        int cardTop = this.drawStatsParagraphCard(guiGraphics, x, w, top, bottom, 0xFF2F5F73,
                "The Stats Display Panel is a screen you hang on a wall. It watches your factory and "
                        + "shows the numbers as a moving graph, so you can see how things are going with one glance.");
        int dx = x + 8;
        int dw = w - 16;
        int dTop = top + 4;
        int dBottom = cardTop - 6;
        // A brick wall with a mounted panel drawing a live line graph.
        for (int by = dTop; by < dBottom; by += 8) {
            for (int bx = dx; bx < dx + dw; bx += 16) {
                int off = ((by - dTop) / 8) % 2 == 0 ? 0 : 8;
                drawOutline(guiGraphics, bx + off - 8, by, 16, 8, 0x33705438);
            }
        }
        int pw = Math.min(dw - 20, 120);
        int ph = Math.min(dBottom - dTop - 8, 60);
        int px = x + w / 2 - pw / 2;
        int py = dTop + ((dBottom - dTop) - ph) / 2;
        guiGraphics.fill(px - 3, py - 3, px + pw + 3, py + ph + 3, 0xFF705438);
        guiGraphics.fill(px, py, px + pw, py + ph, 0xFF1B2B2E);
        drawOutline(guiGraphics, px, py, pw, ph, 0xFF4A8F8B);
        // animated graph line
        int prevX = px + 4;
        int prevY = py + ph / 2;
        for (int i = 0; i <= pw - 8; i += 4) {
            double t = (net.minecraft.Util.getMillis() / 320.0) + i * 0.12;
            int gy = py + ph / 2 - (int) Math.round(Math.sin(t) * (ph * 0.30));
            int gx = px + 4 + i;
            guiGraphics.fill(Math.min(prevX, gx), Math.min(prevY, gy), Math.max(prevX, gx) + 1,
                    Math.max(prevY, gy) + 1, 0x00000000);
            guiGraphics.fill(gx, gy, gx + 2, gy + 2, 0xFF8FE6C4);
            prevX = gx;
            prevY = gy;
        }
        for (int gy = py + 6; gy < py + ph; gy += 12) {
            guiGraphics.fill(px + 3, gy, px + pw - 3, gy + 1, 0x224A8F8B);
        }
    }

    private void renderStatsPanelOpen(GuiGraphics guiGraphics, int x, int w, int top, int bottom) {
        this.drawLessonTitle(guiGraphics, x, top - 18, "2. Right-click to open it");
        int cardTop = this.drawStatsParagraphCard(guiGraphics, x, w, top, bottom, 0xFF7E9352,
                "Right-click the panel and a bigger screen pops open. This is where you read the details "
                        + "and choose what the panel should keep an eye on.");
        int dTop = top + 4;
        int dBottom = cardTop - 6;
        int midY = (dTop + dBottom) / 2;
        // small wall panel on the left
        int sw = 34;
        int sh = 26;
        int sx = x + 16;
        int sy = midY - sh / 2;
        guiGraphics.fill(sx - 2, sy - 2, sx + sw + 2, sy + sh + 2, 0xFF705438);
        guiGraphics.fill(sx, sy, sx + sw, sy + sh, 0xFF1B2B2E);
        drawOutline(guiGraphics, sx, sy, sw, sh, 0xFF4A8F8B);
        this.drawTinyText(guiGraphics, "panel", sx + 4, sy + sh + 3, DIM);
        // animated click pulse on the panel
        int pulse = (int) Math.floorMod(net.minecraft.Util.getMillis() / 90L, 30L);
        int ring = pulse < 15 ? pulse : 0;
        if (ring > 0) {
            drawOutline(guiGraphics, sx + sw / 2 - ring, sy + sh / 2 - ring, ring * 2, ring * 2, 0x88FFE0A2);
        }
        this.drawTinyText(guiGraphics, "right-click", sx - 2, sy - 14, 0xFF8B641F);
        // arrow to a big opened dashboard on the right
        int bw = Math.min(w - sw - 60, 110);
        int bh = Math.min(dBottom - dTop - 6, 66);
        int bx = x + w - bw - 12;
        int by = midY - bh / 2;
        this.drawAnimatedArrow(guiGraphics, sx + sw + 4, bx - 4, midY, 0xFF7E9352, true);
        guiGraphics.fill(bx - 3, by - 3, bx + bw + 3, by + bh + 3, 0xFF705438);
        guiGraphics.fill(bx, by, bx + bw, by + bh, 0xFFE8D7AE);
        drawOutline(guiGraphics, bx, by, bw, bh, 0xFF2F5F73);
        guiGraphics.fill(bx + 4, by + 4, bx + bw - 4, by + 14, 0x552F5F73);
        this.drawTinyText(guiGraphics, "Dashboard", bx + 7, by + 5, TEXT);
        // little bars in the dashboard
        for (int i = 0; i < 4; i++) {
            int barH = 6 + (i * 7) % (bh - 26);
            int barX = bx + 8 + i * ((bw - 16) / 4);
            guiGraphics.fill(barX, by + bh - 6 - barH, barX + 8, by + bh - 6, 0xFF7E9352);
        }
    }

    private void renderStatsPanelSwitch(GuiGraphics guiGraphics, int x, int w, int top, int bottom) {
        this.drawLessonTitle(guiGraphics, x, top - 18, "3. Sneak-click to switch the look");
        int cardTop = this.drawStatsParagraphCard(guiGraphics, x, w, top, bottom, 0xFF8B6C96,
                "Sneak and right-click to flip the panel to its next look. The same numbers can show as a "
                        + "line graph, a round gauge, or a simple list. Pick whatever is easiest to read.");
        int dTop = top + 4;
        int dBottom = cardTop - 6;
        String[] modes = {"Graph", "Gauge", "List"};
        int[] colors = {0xFF4A8F8B, 0xFF8B641F, 0xFF7E9352};
        int active = (int) Math.floorMod(net.minecraft.Util.getMillis() / 1100L, 3L);
        int cellW = (w - 32) / 3;
        int cellH = Math.min(dBottom - dTop - 6, 58);
        int cy = (dTop + dBottom) / 2 - cellH / 2;
        for (int i = 0; i < 3; i++) {
            int cx = x + 12 + i * cellW;
            boolean on = i == active;
            guiGraphics.fill(cx, cy, cx + cellW - 8, cy + cellH, on ? 0xFF1B2B2E : 0xFF2A2A2A);
            drawOutline(guiGraphics, cx, cy, cellW - 8, cellH, on ? colors[i] : 0xFF6A5A44);
            int iconColor = on ? colors[i] : 0xFF6A5A44;
            int iw = cellW - 24;
            int ix = cx + 8;
            if (i == 0) {
                int prevY = cy + cellH / 2;
                for (int s = 0; s <= iw; s += 4) {
                    int gy = cy + cellH / 2 - (int) Math.round(Math.sin(s * 0.3) * (cellH * 0.22));
                    guiGraphics.fill(ix + s, gy, ix + s + 2, gy + 2, iconColor);
                    prevY = gy;
                }
            } else if (i == 1) {
                int gcx = cx + (cellW - 8) / 2;
                int gcy = cy + cellH - 12;
                drawOutline(guiGraphics, gcx - iw / 2, cy + 8, iw, cellH - 18, iconColor);
                double ang = on ? (-0.6 + 1.2 * (Math.sin(net.minecraft.Util.getMillis() / 300.0) * 0.5 + 0.5)) : 0.2;
                int nx = gcx + (int) Math.round(Math.cos(Math.PI - ang) * iw * 0.4);
                int ny = gcy - (int) Math.round(Math.sin(Math.PI - ang) * iw * 0.4);
                guiGraphics.fill(Math.min(gcx, nx), Math.min(gcy, ny), Math.max(gcx, nx) + 1, Math.max(gcy, ny) + 1, iconColor);
            } else {
                for (int r = 0; r < 4; r++) {
                    guiGraphics.fill(ix, cy + 10 + r * 9, ix + iw, cy + 12 + r * 9, iconColor);
                }
            }
            this.drawTinyText(guiGraphics, modes[i], cx + 4, cy + cellH - 11, on ? TEXT : DIM);
        }
        this.drawTinyText(guiGraphics, "sneak + right-click cycles", x + 12, dTop - 2, 0xFF8B6C96);
    }

    private void renderStatsPanelBigger(GuiGraphics guiGraphics, int x, int w, int top, int bottom) {
        this.drawLessonTitle(guiGraphics, x, top - 18, "4. Join panels into a big screen");
        int cardTop = this.drawStatsParagraphCard(guiGraphics, x, w, top, bottom, 0xFFB9843E,
                "Place matching panels side by side, all facing the same way, and they join into one giant "
                        + "screen run by a single controller. Hold Shift while placing to auto-fill the neighbours fast.");
        int dTop = top + 4;
        int dBottom = cardTop - 6;
        int cols = 3;
        int rows = 2;
        int gap = 3;
        int gridW = Math.min(w - 80, 132);
        int gridH = Math.min(dBottom - dTop - 6, 60);
        int cellW = (gridW - (cols - 1) * gap) / cols;
        int cellH = (gridH - (rows - 1) * gap) / rows;
        int gx0 = x + 14;
        int gy0 = (dTop + dBottom) / 2 - gridH / 2;
        int sweep = (int) Math.floorMod(net.minecraft.Util.getMillis() / 360L, (long) (cols * rows + 2));
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                int idx = r * cols + c;
                int gx = gx0 + c * (cellW + gap);
                int gy = gy0 + r * (cellH + gap);
                boolean lit = idx < sweep;
                boolean controller = c == 0 && r == 0;
                guiGraphics.fill(gx, gy, gx + cellW, gy + cellH, lit ? 0xFF1B2B2E : 0xFF3A3A36);
                drawOutline(guiGraphics, gx, gy, cellW, cellH, controller ? 0xFFE0A23A : (lit ? 0xFF4A8F8B : 0xFF6A5A44));
                if (lit) {
                    guiGraphics.fill(gx + 3, gy + 3, gx + cellW - 3, gy + 5, 0x884A8F8B);
                }
            }
        }
        // one big merged graph overlay when fully lit
        if (sweep >= cols * rows) {
            int prevY = gy0 + gridH / 2;
            for (int s = 0; s <= gridW; s += 4) {
                int yy = gy0 + gridH / 2 - (int) Math.round(Math.sin(s * 0.16 + net.minecraft.Util.getMillis() / 240.0) * (gridH * 0.30));
                guiGraphics.fill(gx0 + s, yy, gx0 + s + 2, yy + 2, 0xFF8FE6C4);
                prevY = yy;
            }
        }
        // controller + shift labels to the right
        int lx = gx0 + gridW + 12;
        guiGraphics.fill(lx, gy0 + 2, lx + 10, gy0 + 12, 0xFFE0A23A);
        this.drawTinyText(guiGraphics, "controller", lx + 14, gy0 + 3, TEXT);
        this.drawTinyText(guiGraphics, "Shift =", lx, gy0 + 24, 0xFFB9843E);
        this.drawTinyText(guiGraphics, "auto-fill", lx, gy0 + 35, 0xFFB9843E);
    }

    private void renderStatsPanelCopy(GuiGraphics guiGraphics, int x, int w, int top, int bottom) {
        this.drawLessonTitle(guiGraphics, x, top - 18, "5. Copy settings with the tool");
        int cardTop = this.drawStatsParagraphCard(guiGraphics, x, w, top, bottom, 0xFF8B641F,
                "Use a Stats Configurator to copy one panel's settings and paste them onto another. Set up "
                        + "one panel just right, then give every other panel the same look in seconds.");
        int dTop = top + 4;
        int dBottom = cardTop - 6;
        int midY = (dTop + dBottom) / 2;
        int pw = 34;
        int ph = 26;
        int leftX = x + 16;
        int rightX = x + w - pw - 16;
        int sy = midY - ph / 2;
        // source panel (configured)
        guiGraphics.fill(leftX, sy, leftX + pw, sy + ph, 0xFF1B2B2E);
        drawOutline(guiGraphics, leftX, sy, pw, ph, 0xFF4A8F8B);
        guiGraphics.fill(leftX + 4, sy + 4, leftX + pw - 4, sy + 8, 0x884A8F8B);
        guiGraphics.fill(leftX + 4, sy + 12, leftX + pw - 10, sy + 16, 0x884A8F8B);
        this.drawTinyText(guiGraphics, "set up", leftX, sy + ph + 3, DIM);
        // target panel (blank)
        guiGraphics.fill(rightX, sy, rightX + pw, sy + ph, 0xFF2A2A2A);
        drawOutline(guiGraphics, rightX, sy, pw, ph, 0xFF6A5A44);
        this.drawTinyText(guiGraphics, "blank", rightX, sy + ph + 3, DIM);
        // tool travelling between them carrying the settings
        int span = (rightX) - (leftX + pw) - 18;
        int phase = (int) Math.floorMod(net.minecraft.Util.getMillis() / 24L, Math.max(1L, (long) span * 2));
        int travel = phase < span ? phase : (span * 2 - phase);
        int tx = leftX + pw + 8 + travel;
        guiGraphics.fill(tx, midY - 5, tx + 12, midY + 5, 0xFFB97F2E);
        drawOutline(guiGraphics, tx, midY - 5, 12, 10, 0xFF6A4A1E);
        guiGraphics.fill(tx + 3, midY - 2, tx + 9, midY + 2, 0xFFF3E8CC);
        this.drawTinyText(guiGraphics, "Configurator", x + w / 2 - this.font.width("Configurator") / 2, dTop - 2, 0xFF8B641F);
    }

    /** [1] */
    private int drawStatsParagraphCard(GuiGraphics guiGraphics, int x, int w, int top, int bottom,
                                       int accent, String text) {
        int inner = w - 24;
        List<String> lines = this.wrapPlain(text, inner);
        int lineH = this.font.lineHeight + 2;
        int cardH = lines.size() * lineH + 12;
        int cardTop = bottom - cardH;
        int minDiagram = 46;
        if (cardTop < top + minDiagram) {
            cardTop = top + minDiagram;
        }
        guiGraphics.fill(x + 6, cardTop, x + w - 6, bottom, 0xF2F1E5C6);
        drawOutline(guiGraphics, x + 6, cardTop, w - 12, bottom - cardTop, 0x66705438);
        guiGraphics.fill(x + 6, cardTop, x + 9, bottom, accent);
        int ty = cardTop + 6;
        for (String line : lines) {
            guiGraphics.drawString(this.font, line, x + 14, ty, TEXT, false);
            ty += lineH;
        }
        return cardTop;
    }

    /** Word-wraps text to fit maxWidth, never dropping content. */
    private List<String> wrapPlain(String text, int maxWidth) {
        List<String> out = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return out;
        }
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            String candidate = line.length() == 0 ? word : line + " " + word;
            if (this.font.width(candidate) > maxWidth && line.length() > 0) {
                out.add(line.toString());
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(candidate);
            }
        }
        if (line.length() > 0) {
            out.add(line.toString());
        }
        return out;
    }

    // bespoke lesson: Data Dial. A round gauge whose needle and comparator follow one reading.

    private boolean isDataDialGuidePage() {
        if (this.selectedPage < 0 || this.selectedPage >= this.pages.size()) {
            return false;
        }
        return "data_dial".equals(this.pages.get(this.selectedPage).id());
    }

    private void renderDataDialGuide(GuiGraphics guiGraphics) {
        int x = this.articleBodyLeft;
        int y = this.articleBodyTop;
        int w = this.articleBodyWidth;
        int h = this.articleBodyHeight;
        guiGraphics.fill(x, y, x + w, y + h, 0x55DCC79B);
        drawOutline(guiGraphics, x, y, w, h, 0x88705438);
        int titleH = 18;
        int footer = 18;
        int contentTop = y + titleH + 2;
        int contentBottom = y + h - footer;
        switch (this.selectedSubpage) {
            case 0 -> this.renderDialWhat(guiGraphics, x, w, contentTop, contentBottom);
            case 1 -> this.renderDialLocal(guiGraphics, x, w, contentTop, contentBottom);
            case 2 -> this.renderDialLinked(guiGraphics, x, w, contentTop, contentBottom);
            case 3 -> this.renderDialMetric(guiGraphics, x, w, contentTop, contentBottom);
            default -> this.renderDialRedstone(guiGraphics, x, w, contentTop, contentBottom);
        }
        this.renderFigureSheetPips(guiGraphics, x, y, w, h);
    }

    private void renderDialWhat(GuiGraphics guiGraphics, int x, int w, int top, int bottom) {
        this.drawLessonTitle(guiGraphics, x, top - 18, "1. A gauge for one reading");
        int cardTop = this.drawStatsParagraphCard(guiGraphics, x, w, top, bottom, 0xFF4A7C8F,
                "The Data Dial is a round gauge you hang on a wall. Its needle swings to show one live "
                        + "reading from your machines, so you can check a single number at a glance.");
        int cx = x + w / 2;
        int r = Math.min((cardTop - top) / 2 - 4, 28);
        int cy = top + (cardTop - top) / 2;
        float frac = (float) (Math.sin(net.minecraft.Util.getMillis() / 600.0) * 0.5 + 0.5);
        this.drawDialFace(guiGraphics, cx, cy, r, 0xFF4A7C8F);
        this.drawDialNeedle(guiGraphics, cx, cy, frac, r - 4, 0xFFA8492B);
        this.drawTinyText(guiGraphics, "live", cx - this.font.width("live") / 2, cy + r - 10, DIM);
    }

    private void renderDialLocal(GuiGraphics guiGraphics, int x, int w, int top, int bottom) {
        this.drawLessonTitle(guiGraphics, x, top - 18, "2. Local: reads the block behind");
        int cardTop = this.drawStatsParagraphCard(guiGraphics, x, w, top, bottom, 0xFF7E9352,
                "Hang the dial flat against a sturdy block and it reads the machine right behind it. No "
                        + "wiring needed - the block it touches is the one it measures.");
        int midY = top + (cardTop - top) / 2;
        int bx = x + 16;
        int bw = 30;
        guiGraphics.fill(bx, midY - 16, bx + bw, midY + 16, 0xFFB58A4B);
        drawOutline(guiGraphics, bx, midY - 16, bw, 32, 0xFF6A4A1E);
        this.drawTinyText(guiGraphics, "machine", bx - 2, midY + 20, DIM);
        int r = 22;
        int cx = bx + bw + 14 + r;
        this.drawAnimatedArrow(guiGraphics, bx + bw + 4, cx - r - 2, midY, 0xFF7E9352, false);
        this.drawDialFace(guiGraphics, cx, midY, r, 0xFF7E9352);
        this.drawDialNeedle(guiGraphics, cx, midY, 0.7f, r - 4, 0xFFA8492B);
        this.drawTinyText(guiGraphics, "touching = reading", cx - 30, midY + r + 4, TEXT);
    }

    private void renderDialLinked(GuiGraphics guiGraphics, int x, int w, int top, int bottom) {
        this.drawLessonTitle(guiGraphics, x, top - 18, "3. Linked: follow a Display Link");
        int cardTop = this.drawStatsParagraphCard(guiGraphics, x, w, top, bottom, 0xFF66507F,
                "Sneak and right-click to switch the dial to Linked mode. Now it follows a value sent in "
                        + "from a Display Link far away, instead of the block behind it.");
        int midY = top + (cardTop - top) / 2;
        int lx = x + 16;
        guiGraphics.fill(lx, midY - 12, lx + 26, midY + 12, 0xFF6E5790);
        drawOutline(guiGraphics, lx, midY - 12, 26, 24, 0xFF3C2F52);
        this.drawTinyText(guiGraphics, "Display", lx - 2, midY + 16, DIM);
        this.drawTinyText(guiGraphics, "Link", lx + 4, midY + 26, DIM);
        int r = 22;
        int cx = x + w - r - 18;
        // dashed beam
        int beamStart = lx + 28;
        int beamEnd = cx - r - 2;
        int dash = (int) Math.floorMod(net.minecraft.Util.getMillis() / 60L, 8L);
        for (int px = beamStart + dash; px < beamEnd; px += 8) {
            guiGraphics.fill(px, midY - 1, Math.min(px + 4, beamEnd), midY + 1, 0xFF8B6C96);
        }
        this.drawDialFace(guiGraphics, cx, midY, r, 0xFF66507F);
        this.drawDialNeedle(guiGraphics, cx, midY, 0.4f, r - 4, 0xFFA8492B);
        this.drawTinyText(guiGraphics, "sneak-click swaps mode", x + 14, cardTop - 12, 0xFF66507F);
    }

    private void renderDialMetric(GuiGraphics guiGraphics, int x, int w, int top, int bottom) {
        this.drawLessonTitle(guiGraphics, x, top - 18, "4. Right-click to pick the reading");
        int cardTop = this.drawStatsParagraphCard(guiGraphics, x, w, top, bottom, 0xFF2F5F73,
                "Right-click the dial to cycle which number it shows - like rotation speed, stress, or "
                        + "another metric. The needle jumps to whatever reading you choose.");
        int midY = top + (cardTop - top) / 2;
        int r = Math.min((cardTop - top) / 2 - 4, 26);
        int cx = x + 40 + r;
        String[] metrics = {"RPM", "Stress", "Items"};
        float[] fracs = {0.8f, 0.45f, 0.2f};
        int[] cols = {0xFF4A7C8F, 0xFF8B641F, 0xFF7E9352};
        int active = (int) Math.floorMod(net.minecraft.Util.getMillis() / 1100L, 3L);
        this.drawDialFace(guiGraphics, cx, midY, r, cols[active]);
        this.drawDialNeedle(guiGraphics, cx, midY, fracs[active], r - 4, 0xFFA8492B);
        // metric list on the right, active highlighted
        int listX = cx + r + 16;
        for (int i = 0; i < metrics.length; i++) {
            int ly = midY - 22 + i * 16;
            boolean on = i == active;
            if (on) {
                guiGraphics.fill(listX - 3, ly - 2, listX + 60, ly + 11, 0x55BF9150);
            }
            guiGraphics.fill(listX, ly + 1, listX + 8, ly + 9, cols[i]);
            this.drawTinyText(guiGraphics, metrics[i], listX + 12, ly + 1, on ? TEXT : DIM);
        }
    }

    private void renderDialRedstone(GuiGraphics guiGraphics, int x, int w, int top, int bottom) {
        this.drawLessonTitle(guiGraphics, x, top - 18, "5. It also sends a redstone signal");
        int cardTop = this.drawStatsParagraphCard(guiGraphics, x, w, top, bottom, 0xFFA8492B,
                "The needle and a comparator both follow the same reading. So the dial is a gauge you can "
                        + "see and an analog redstone source at the same time - higher reading, stronger signal.");
        int midY = top + (cardTop - top) / 2;
        int r = 22;
        int cx = x + 36 + r;
        float frac = (float) (Math.sin(net.minecraft.Util.getMillis() / 700.0) * 0.5 + 0.5);
        this.drawDialFace(guiGraphics, cx, midY, r, 0xFFA8492B);
        this.drawDialNeedle(guiGraphics, cx, midY, frac, r - 4, 0xFF342718);
        // redstone strength bars to the right
        int barX = cx + r + 18;
        int bars = 1 + Math.round(frac * 14);
        for (int i = 0; i < 15; i++) {
            int by = midY + 22 - i * 3;
            boolean on = i < bars;
            guiGraphics.fill(barX, by, barX + 40, by + 2, on ? 0xFFC0392B : 0x33705438);
        }
        this.drawTinyText(guiGraphics, "signal: " + bars + "/15", barX, midY - 24, TEXT);
    }

    private void drawDialFace(GuiGraphics guiGraphics, int cx, int cy, int r, int accent) {
        for (int dy = -r; dy <= r; dy++) {
            int half = (int) Math.round(Math.sqrt((double) r * r - dy * dy));
            guiGraphics.fill(cx - half, cy + dy, cx + half, cy + dy + 1, 0xFFE8D7AE);
        }
        for (double a = 0; a < Math.PI * 2; a += 0.16) {
            int rx = cx + (int) Math.round(Math.cos(a) * r);
            int ry = cy + (int) Math.round(Math.sin(a) * r);
            guiGraphics.fill(rx - 1, ry - 1, rx + 1, ry + 1, accent);
        }
        // top arc tick marks
        for (int t = 0; t <= 6; t++) {
            double a = Math.PI - t * (Math.PI / 6.0);
            int tx = cx + (int) Math.round(Math.cos(a) * (r - 3));
            int ty = cy - (int) Math.round(Math.sin(a) * (r - 3));
            guiGraphics.fill(tx - 1, ty - 1, tx + 1, ty + 1, 0x88705438);
        }
    }

    private void drawDialNeedle(GuiGraphics guiGraphics, int cx, int cy, float frac, int len, int color) {
        double a = Math.PI - Mth.clamp(frac, 0f, 1f) * Math.PI;
        for (int i = 0; i <= len; i++) {
            int nx = cx + (int) Math.round(Math.cos(a) * i);
            int ny = cy - (int) Math.round(Math.sin(a) * i);
            guiGraphics.fill(nx - 1, ny - 1, nx + 1, ny + 1, color);
        }
        guiGraphics.fill(cx - 2, cy - 2, cx + 3, cy + 3, 0xFF342718);
    }

    /** A glowing dot travelling along a segment to show a signal flowing from A to B. */
    private void drawFlowDot(GuiGraphics guiGraphics, int x1, int y1, int x2, int y2, int color, long period, float offset) {
        float t = ((net.minecraft.Util.getMillis() % period) / (float) period + offset) % 1f;
        int px = Math.round(x1 + (x2 - x1) * t);
        int py = Math.round(y1 + (y2 - y1) * t);
        guiGraphics.fill(px - 2, py - 2, px + 3, py + 3, color);
        guiGraphics.fill(px - 1, py - 1, px + 2, py + 2, 0xFFFFF2C8);
    }

    /** A small spinning fan whose blades rotate at a speed set by {@code rpm} (0..1). */
    private void drawSpinFan(GuiGraphics guiGraphics, int cx, int cy, int r, float rpm, int color) {
        for (int dy = -r; dy <= r; dy++) {
            int half = (int) Math.round(Math.sqrt((double) r * r - dy * dy));
            guiGraphics.fill(cx - half, cy + dy, cx + half, cy + dy + 1, 0xFF21302F);
        }
        double base = net.minecraft.Util.getMillis() / 1000.0 * (0.6 + rpm * 6.0);
        for (int b = 0; b < 3; b++) {
            double a = base + b * (Math.PI * 2 / 3);
            int ex = cx + (int) Math.round(Math.cos(a) * (r - 1));
            int ey = cy + (int) Math.round(Math.sin(a) * (r - 1));
            for (int i = 0; i <= r - 1; i++) {
                int bx = cx + (int) Math.round(Math.cos(a) * i);
                int by = cy + (int) Math.round(Math.sin(a) * i);
                guiGraphics.fill(bx - 1, by - 1, bx + 1, by + 1, color);
            }
        }
        guiGraphics.fill(cx - 1, cy - 1, cx + 2, cy + 2, 0xFFFFF2C8);
    }


    private void renderFccGuide(GuiGraphics guiGraphics) {
        int x = this.articleBodyLeft;
        int y = this.articleBodyTop;
        int w = this.articleBodyWidth;
        int h = this.articleBodyHeight;
        guiGraphics.fill(x, y, x + w, y + h, 0x4F8DB0B8);
        drawOutline(guiGraphics, x, y, w, h, 0x88705438);
        int titleH = 18;
        int footer = 18;
        int top = y + titleH + 2;
        int bottom = y + h - footer;
        switch (this.selectedSubpage) {
            case 0 -> this.renderFccHub(guiGraphics, x, w, top, bottom);
            case 1 -> this.renderFccSetup(guiGraphics, x, w, top, bottom);
            case 2 -> this.renderFccLayout(guiGraphics, x, w, top, bottom);
            case 3 -> this.renderFccControl(guiGraphics, x, w, top, bottom);
            case 4 -> this.renderFccAirship(guiGraphics, x, w, top, bottom);
            case 5 -> this.renderFccGround(guiGraphics, x, w, top, bottom);
            case 6 -> this.renderFccAuto(guiGraphics, x, w, top, bottom);
            case 7 -> this.renderFccPower(guiGraphics, x, w, top, bottom);
            default -> this.renderFccTrouble(guiGraphics, x, w, top, bottom);
        }
        this.renderFccSheetPips(guiGraphics, x, y, w, h);
    }

    private static final String[] FCC_HUB_LABELS = {"Setup", "Layout", "Control", "Operate", "Power"};
    private static final int[] FCC_HUB_SHEETS = {1, 2, 3, 4, 7};
    private static final int[] FCC_HUB_COLORS = {0xFF8B641F, 0xFF3E6B69, 0xFF8A5425, 0xFF2F5F73, 0xFF66507F};

    private void renderFccHub(GuiGraphics guiGraphics, int x, int w, int top, int bottom) {
        this.drawLessonTitle(guiGraphics, x, top - 18, "1. The VCC is your vehicle's brain");
        int cardTop = this.drawStatsParagraphCard(guiGraphics, x, w, top, bottom, 0xFF2F5F73,
                "The Visual Vehicle Control Center (VCC) is your vehicle's brain. Teach it your machines "
                        + "once, then steer everything from five stations: Setup, Layout, Control, Operate and Power.");
        int chipsY = top + 2;
        int chipW = (w - 16) / FCC_HUB_LABELS.length;
        int boxW = 54;
        int boxX = x + w / 2 - boxW / 2;
        int boxY = chipsY + 30;
        int boxH = Math.min(38, Math.max(20, cardTop - 6 - boxY));
        int vccCx = boxX + boxW / 2;
        int active = Math.floorMod((int) (net.minecraft.Util.getMillis() / 650L), FCC_HUB_LABELS.length);
        for (int i = 0; i < FCC_HUB_LABELS.length; i++) {
            int chipX = x + 8 + i * chipW;
            int chipCx = chipX + (chipW - 4) / 2;
            guiGraphics.fill(chipCx, chipsY + 25, chipCx + 1, boxY, 0x66705438);
            // a thought pulse races up from the VCC brain to the station it is currently driving
            if (i == active) {
                this.drawFlowDot(guiGraphics, vccCx, boxY, chipCx, chipsY + 25, FCC_HUB_COLORS[i], 650L, 0f);
            }
        }
        this.drawFccBox(guiGraphics, boxX, boxY, boxW, boxH, "VCC", "brain", 0xFF2F5F73);
        for (int i = 0; i < FCC_HUB_LABELS.length; i++) {
            int chipX = x + 8 + i * chipW;
            this.drawFccStationChip(guiGraphics, chipX, chipsY, chipW - 4, FCC_HUB_LABELS[i], FCC_HUB_COLORS[i], i == active);
            this.drawTinyText(guiGraphics, "p" + (FCC_HUB_SHEETS[i] + 1), chipX + 4, chipsY + 14, this.paperInk(FCC_HUB_COLORS[i]));
        }
        this.drawTinyText(guiGraphics, "tap a station", boxX + boxW + 6, boxY + 4, DIM);
    }

    private void renderFccSetup(GuiGraphics guiGraphics, int x, int w, int top, int bottom) {
        this.drawLessonTitle(guiGraphics, x, top - 18, "2. Setup: teach it your machines");
        int cardTop = this.drawStatsParagraphCard(guiGraphics, x, w, top, bottom, 0xFF8B641F,
                "First, link your engines and steering with cards, give each device a clear name, and sort "
                        + "them into groups like left and right. Pick a vehicle template, run a gentle test, and you are ready.");
        int midY = top + (cardTop - top) / 2;
        int bw = Math.min(52, (w - 60) / 3);
        int bh = 32;
        int by = midY - bh / 2;
        int x1 = x + 10;
        int x2 = x + w / 2 - bw / 2;
        int x3 = x + w - bw - 10;
        // a card token walks Cards -> Devices -> Groups; the box it just reached glows
        float trip = (net.minecraft.Util.getMillis() % 2400L) / 2400f;
        int stage = trip < 0.5f ? 0 : 1;
        this.drawFccBox(guiGraphics, x1, by, bw, bh, "Cards", "link", 0xFF8B641F);
        this.drawFccBox(guiGraphics, x2, by, bw, bh, "Devices", "name", 0xFF3E6B69);
        this.drawFccBox(guiGraphics, x3, by, bw, bh, "Groups", "roles", 0xFF4C6F32);
        this.drawAnimatedArrow(guiGraphics, x1 + bw + 2, x2 - 2, midY, 0xFF8B641F, true);
        this.drawAnimatedArrow(guiGraphics, x2 + bw + 2, x3 - 2, midY, 0xFF4C6F32, true);
        if (stage == 0) {
            guiGraphics.fill(x2 - 2, by - 2, x2 + bw + 2, by + bh + 2, 0x333E6B69);
            this.drawFlowDot(guiGraphics, x1 + bw, midY, x2, midY, 0xFF8B641F, 1200L, 0f);
        } else {
            guiGraphics.fill(x3 - 2, by - 2, x3 + bw + 2, by + bh + 2, 0x334C6F32);
            this.drawFlowDot(guiGraphics, x2 + bw, midY, x3, midY, 0xFF4C6F32, 1200L, 0f);
        }
    }

    private void renderFccLayout(GuiGraphics guiGraphics, int x, int w, int top, int bottom) {
        this.drawLessonTitle(guiGraphics, x, top - 18, "3. Layout: map where things are");
        int cardTop = this.drawStatsParagraphCard(guiGraphics, x, w, top, bottom, 0xFF4C6F32,
                "Tell the VCC where each part sits - front, back, left, right or lift. These spots become "
                        + "names, so later you can say push from the back without hunting for the right engine.");
        int dTop = top + 6;
        int dBottom = cardTop - 8;
        int cw = Math.min(72, w - 90);
        int chh = Math.min(dBottom - dTop - 18, 44);
        int craftX = x + (w - cw) / 2;
        int craftY = dTop + ((dBottom - dTop) - chh) / 2;
        this.drawFccCraft(guiGraphics, craftX, craftY, cw, chh, false);
        int pulse = Math.floorMod((int) (net.minecraft.Util.getMillis() / 360L), 5);
        this.drawFccZone(guiGraphics, craftX + cw / 2 - 18, craftY - 16, "Front", 0xFF2F6F88, pulse == 0);
        this.drawFccZone(guiGraphics, craftX + cw / 2 - 18, craftY + chh + 1, "Back", 0xFF9B6427, pulse == 1);
        this.drawFccZone(guiGraphics, craftX - 38, craftY + chh / 2 - 7, "Left", 0xFF2F6F88, pulse == 2);
        this.drawFccZone(guiGraphics, craftX + cw + 2, craftY + chh / 2 - 7, "Right", 0xFF9B6427, pulse == 3);
        this.drawFccSignalPulse(guiGraphics, craftX + cw / 2, craftY + chh / 2, 0xFF4C6F32);
    }

    private void renderFccControl(GuiGraphics guiGraphics, int x, int w, int top, int bottom) {
        this.drawLessonTitle(guiGraphics, x, top - 18, "4. Control: wire levers to engines");
        int cardTop = this.drawStatsParagraphCard(guiGraphics, x, w, top, bottom, 0xFF8A5425,
                "A control links a lever or wheel to a group of engines. Slide the strength to set how hard "
                        + "each one pushes. Pull the thrust lever and every routed engine responds together.");
        int dTop = top + 4;
        int dBottom = cardTop - 6;
        int bw = Math.min(54, (w - 40) / 3);
        int bh = Math.min((dBottom - dTop - 8) / 2, 28);
        int leftX = x + 8;
        int midX = x + w / 2 - bw / 2;
        int rightX = x + w - bw - 8;
        int row1 = dTop + 2;
        int row2 = dBottom - bh;
        // thrust demand swings up and down so the whole chain visibly reacts to it
        float thrust = (float) (0.5 + 0.5 * Math.sin(net.minecraft.Util.getMillis() / 900.0));
        this.drawFccBox(guiGraphics, leftX, row1, bw, bh, "Thrust", "lever", 0xFF2F5F73);
        // a little lever handle slides with the thrust demand
        int leverTrack = bw - 16;
        int leverX = leftX + 8 + Math.round(leverTrack * thrust);
        guiGraphics.fill(leftX + 8, row1 + bh - 7, leftX + bw - 8, row1 + bh - 5, 0x66342718);
        guiGraphics.fill(leverX - 2, row1 + bh - 10, leverX + 3, row1 + bh - 3, 0xFFFFE08A);
        this.drawFccBox(guiGraphics, leftX, row2, bw, bh, "Yaw", "wheel", 0xFF8A5425);
        int scaleY = (row1 + row2 + bh) / 2;
        this.drawFccBox(guiGraphics, midX, scaleY - bh / 2, bw, bh, "Route", "scale", 0xFF8B641F);
        // route-scale knob position follows the thrust demand
        int knobTrack = bw - 16;
        int knobX = midX + 8 + Math.round(knobTrack * thrust);
        guiGraphics.fill(midX + 8, scaleY + bh / 2 - 6, midX + bw - 8, scaleY + bh / 2 - 4, 0x66705438);
        guiGraphics.fill(knobX - 2, scaleY + bh / 2 - 9, knobX + 3, scaleY + bh / 2 - 1, 0xFFB9843E);
        this.drawFccBox(guiGraphics, rightX, row1, bw, bh, "Left", "RPM", 0xFF2F6F88);
        this.drawFccBox(guiGraphics, rightX, row2, bw, bh, "Right", "RPM", 0xFF9B6427);
        // the two engines spin at a speed set by the thrust demand
        this.drawSpinFan(guiGraphics, rightX + bw - 12, row1 + bh / 2, 7, thrust, 0xFF62D5FF);
        this.drawSpinFan(guiGraphics, rightX + bw - 12, row2 + bh / 2, 7, thrust, 0xFFFFB454);
        this.drawAnimatedArrow(guiGraphics, leftX + bw + 2, midX - 2, scaleY, 0xFF2F5F73, true);
        this.drawAnimatedArrow(guiGraphics, midX + bw + 2, rightX - 2, scaleY, 0xFF2F6F88, true);
        // signal beads carry the thrust demand from lever -> scale -> engines
        this.drawFlowDot(guiGraphics, leftX + bw, scaleY, midX, scaleY, 0xFF2F5F73, 900L, 0f);
        this.drawFlowDot(guiGraphics, midX + bw, scaleY, rightX, scaleY, 0xFF2F6F88, 900L, 0.2f);
    }

    private void renderFccAirship(GuiGraphics guiGraphics, int x, int w, int top, int bottom) {
        this.drawLessonTitle(guiGraphics, x, top - 18, "5. Operate: airship helm");
        int cardTop = this.drawStatsParagraphCard(guiGraphics, x, w, top, bottom, 0xFF4A7C8F,
                "For balloons and airships the helm gives thrust to move, yaw to turn, and lift to climb or "
                        + "drop. The propellers spin to match whatever you ask for.");
        int dTop = top + 6;
        int dBottom = cardTop - 6;
        int cw = Math.min(64, w - 100);
        int chh = Math.min(dBottom - dTop - 16, 40);
        int craftX = x + 24;
        int craftY = dTop + ((dBottom - dTop) - chh) / 2;
        this.drawFccCraft(guiGraphics, craftX, craftY, cw, chh, true);
        this.drawFccProp(guiGraphics, craftX + cw / 2 - 9, craftY - 12, 0xFF62D5FF);
        this.drawFccProp(guiGraphics, craftX + cw / 2 - 9, craftY + chh - 2, 0xFFFFB454);
        this.drawFccProp(guiGraphics, craftX - 14, craftY + chh / 2 - 10, 0xFF39C8FF);
        this.drawMiniPanel(guiGraphics, x + w - 60, dTop + 2, "Thrust", "move", 0xFF4A7C8F);
        this.drawMiniPanel(guiGraphics, x + w - 60, dTop + 40, "Lift", "climb", 0xFF7E9352);
    }

    private void renderFccGround(GuiGraphics guiGraphics, int x, int w, int top, int bottom) {
        this.drawLessonTitle(guiGraphics, x, top - 18, "6. Operate: ground vehicle helm");
        int cardTop = this.drawStatsParagraphCard(guiGraphics, x, w, top, bottom, 0xFF7E9352,
                "For cars and tanks the helm becomes a throttle for speed and a wheel for steering. Press "
                        + "forward and the wheels spin; turn the wheel and it leans into the corner.");
        int dTop = top + 4;
        int dBottom = cardTop - 6;
        int midY = (dTop + dBottom) / 2;
        int carW = Math.min(96, w - 90);
        int carX = x + 16;
        int carY = midY - 12;
        guiGraphics.fill(carX, carY, carX + carW, carY + 22, 0xFFE2D0AA);
        drawOutline(guiGraphics, carX, carY, carW, 22, 0xFF6C8F8A);
        this.drawWheel(guiGraphics, carX + 16, carY + 22, 0xFF342718);
        this.drawWheel(guiGraphics, carX + carW - 16, carY + 22, 0xFF342718);
        // spin marks on each wheel so the car clearly looks like it is rolling
        double wa = net.minecraft.Util.getMillis() / 120.0;
        for (int wheel = 0; wheel < 2; wheel++) {
            int wcx = wheel == 0 ? carX + 16 : carX + carW - 16;
            int wmx = wcx + (int) Math.round(Math.cos(wa) * 3);
            int wmy = carY + 27 + (int) Math.round(Math.sin(wa) * 3);
            guiGraphics.fill(wmx - 1, wmy - 1, wmx + 1, wmy + 1, 0xFFB9843E);
        }
        // ground dashes sweep backwards to show forward speed
        int groundY = carY + 34;
        int sweep = Math.floorMod((int) (net.minecraft.Util.getMillis() / 30L), 16);
        for (int dx = carX - 16 - sweep; dx < carX + carW + 16; dx += 16) {
            guiGraphics.fill(dx, groundY, dx + 8, groundY + 2, 0x66705438);
        }
        guiGraphics.fill(carX + 12 + sweep / 2, carY + 8, carX + 18 + sweep / 2, carY + 12, 0xFF4A7C8F);
        this.drawMiniPanel(guiGraphics, x + w - 62, dTop + 2, "Throttle", "speed", 0xFF4A7C8F);
        this.drawMiniPanel(guiGraphics, x + w - 62, dTop + 40, "Steer", "wheel", 0xFFA26B3B);
    }

    private void renderFccAuto(GuiGraphics guiGraphics, int x, int w, int top, int bottom) {
        this.drawLessonTitle(guiGraphics, x, top - 18, "7. Auto Mode drives for you");
        int cardTop = this.drawStatsParagraphCard(guiGraphics, x, w, top, bottom, 0xFF66507F,
                "Switch on Auto Mode and the VCC moves the normal controls by itself - following a route, "
                        + "approaching a stop, announcing it, even reversing. You just watch it fly.");
        int dTop = top + 4;
        int dBottom = cardTop - 6;
        int mapW = Math.min(w / 3, 86);
        int mapH = Math.min(dBottom - dTop - 4, 60);
        this.drawFccRouteMap(guiGraphics, x + 8, dTop, mapW, mapH);
        int midY = dTop + mapH / 2;
        int bw = 56;
        int vccX = x + 8 + mapW + 14;
        this.drawFccBox(guiGraphics, vccX, midY - 16, bw, 32, "VCC", "assist", 0xFF2F5F73);
        int rx = x + w - bw - 8;
        this.drawFccBox(guiGraphics, rx, dTop + 2, bw, 26, "Thrust", "auto", 0xFF4C6F32);
        this.drawFccBox(guiGraphics, rx, dBottom - 26, bw, 26, "Yaw", "auto", 0xFF8A5425);
        this.drawAnimatedArrow(guiGraphics, x + 8 + mapW + 2, vccX - 2, midY, 0xFF66507F, true);
        this.drawAnimatedArrow(guiGraphics, vccX + bw + 2, rx - 2, dTop + 15, 0xFF4C6F32, true);
        this.drawAnimatedArrow(guiGraphics, vccX + bw + 2, rx - 2, dBottom - 13, 0xFF8A5425, true);
        // the map feeds the VCC, which then writes both auto controls by itself
        this.drawFlowDot(guiGraphics, x + 8 + mapW, midY, vccX, midY, 0xFF66507F, 1100L, 0f);
        this.drawFlowDot(guiGraphics, vccX + bw, midY, rx, dTop + 15, 0xFF4C6F32, 1100L, 0.3f);
        this.drawFlowDot(guiGraphics, vccX + bw, midY, rx, dBottom - 13, 0xFF8A5425, 1100L, 0.6f);
    }

    private void renderFccPower(GuiGraphics guiGraphics, int x, int w, int top, int bottom) {
        this.drawLessonTitle(guiGraphics, x, top - 18, "8. Power tools for experts");
        int cardTop = this.drawStatsParagraphCard(guiGraphics, x, w, top, bottom, 0xFF8B6C96,
                "The Power station adds extras: link a controller, override with an E-Manual, and nudge the "
                        + "vehicle along a safe test rail while you read live telemetry. Handy when fine-tuning.");
        int dTop = top + 4;
        int dBottom = cardTop - 6;
        int bw = Math.min(58, (w - 40) / 3);
        int bh = 30;
        int by = dTop + 4;
        int x1 = x + 8;
        int x2 = x + w / 2 - bw / 2;
        int x3 = x + w - bw - 8;
        this.drawFccBox(guiGraphics, x1, by, bw, bh, "Linked", "control", 0xFF8B6C96);
        this.drawFccBox(guiGraphics, x2, by, bw, bh, "E-Manual", "override", 0xFF7E9352);
        this.drawFccBox(guiGraphics, x3, by, bw, bh, "Test", "rail", 0xFFA26B3B);
        int railY = Math.min(by + bh + 12, dBottom - 6);
        guiGraphics.fill(x + 16, railY, x + w - 16, railY + 6, 0x88705438);
        int span = Math.max(1, w - 44);
        int bead = Math.floorMod((int) (net.minecraft.Util.getMillis() / 45L), span);
        guiGraphics.fill(x + 20 + bead, railY - 4, x + 28 + bead, railY + 10, 0xFFB9843E);
    }

    private void renderFccTrouble(GuiGraphics guiGraphics, int x, int w, int top, int bottom) {
        this.drawLessonTitle(guiGraphics, x, top - 18, "9. Nothing happens? Check the chain");
        int cardTop = this.drawStatsParagraphCard(guiGraphics, x, w, top, bottom, 0xFFA8492B,
                "If a control does nothing, follow the chain: is the device linked, is it in a group, does a "
                        + "control route to it, and is the right vehicle profile picked? Fix the first broken link.");
        int dTop = top + 4;
        int dBottom = cardTop - 6;
        String[] checks = {"Device linked?", "In a group?", "Control routed?", "Right profile?"};
        int[] colors = {0xFFE05A47, 0xFFB9843E, 0xFFA26B3B, 0xFF4A7C8F};
        int rowH = Math.max(12, (dBottom - dTop) / checks.length);
        int active = Math.floorMod((int) (net.minecraft.Util.getMillis() / 600L), checks.length);
        for (int i = 0; i < checks.length; i++) {
            int cy = dTop + i * rowH;
            boolean on = i <= active;
            guiGraphics.fill(x + 12, cy, x + w - 12, cy + rowH - 3, on ? 0x55BF9150 : 0x18A88F66);
            drawOutline(guiGraphics, x + 12, cy, w - 24, rowH - 3, colors[i]);
            guiGraphics.fill(x + 18, cy + 3, x + 26, cy + rowH - 6, on ? colors[i] : 0x66705438);
            this.drawTinyText(guiGraphics, (i + 1) + ". " + checks[i], x + 32, cy + 3, TEXT);
        }
    }

    private void renderFccSheetPips(GuiGraphics guiGraphics, int x, int y, int w, int h) {
        int pipY = y + h - 18;
        int pipW = Math.max(10, (w - 20) / FCC_SHEETS);
        guiGraphics.fill(x + 6, pipY - 3, x + w - 6, pipY + 14, 0xE8F1E5C6);
        drawOutline(guiGraphics, x + 6, pipY - 3, w - 12, 17, 0x66705438);
        for (int i = 0; i < FCC_SHEETS; i++) {
            int px = x + 10 + i * pipW;
            int color = i == this.selectedSubpage ? 0xFF2F5F73 : 0xFF7B6951;
            guiGraphics.fill(px, pipY, px + pipW - 3, pipY + 10, i == this.selectedSubpage ? 0x775A8EA0 : 0x18A88F66);
            drawOutline(guiGraphics, px, pipY, pipW - 3, 10, color);
            this.drawTinyText(guiGraphics, Integer.toString(i + 1), px + Math.max(2, (pipW - 8) / 2), pipY + 1, TEXT);
        }
    }

    private boolean handleFccGuideClick(double mouseX, double mouseY) {
        int x = this.articleBodyLeft;
        int y = this.articleBodyTop;
        int w = this.articleBodyWidth;
        int h = this.articleBodyHeight;
        int pipY = y + h - 18;
        int pipW = Math.max(10, (w - 20) / FCC_SHEETS);
        if (mouseY >= pipY - 3 && mouseY <= pipY + 14) {
            for (int i = 0; i < FCC_SHEETS; i++) {
                int px = x + 10 + i * pipW;
                if (mouseX >= px && mouseX <= px + pipW - 3) {
                    this.selectedSubpage = i;
                    this.updateButtons();
                    return true;
                }
            }
        }
        if (this.selectedSubpage != 0) {
            return false;
        }
        int chipCount = FCC_HUB_LABELS.length;
        int chipW = (w - 16) / chipCount;
        int chipsY = y + 20 + 2;
        for (int i = 0; i < chipCount; i++) {
            int chipX = x + 8 + i * chipW;
            if (mouseX >= chipX && mouseX <= chipX + chipW - 4 && mouseY >= chipsY && mouseY <= chipsY + 25) {
                this.selectedSubpage = FCC_HUB_SHEETS[i];
                this.updateButtons();
                return true;
            }
        }
        return false;
    }

    private void drawFccBox(GuiGraphics guiGraphics, int x, int y, int w, int h, String top, String bottom, int color) {
        guiGraphics.fill(x, y, x + w, y + h, 0xFFE8D7AE);
        drawOutline(guiGraphics, x, y, w, h, color);
        this.drawTinyText(guiGraphics, this.trim(top, Math.max(8, w - 8)), x + 4, y + 6, TEXT);
        this.drawTinyText(guiGraphics, this.trim(bottom, Math.max(8, w - 8)), x + 4, y + h - 12, this.diagramInk(color));
    }

    private void drawFccStationChip(GuiGraphics guiGraphics, int x, int y, int w, String label, int color, boolean active) {
        guiGraphics.fill(x, y, x + w, y + 25, active ? 0x55BF9150 : 0x22A88F66);
        drawOutline(guiGraphics, x, y, w, 25, color);
        this.drawTinyText(guiGraphics, this.trim(label, Math.max(8, w - 8)), x + 4, y + 5, TEXT);
    }

    private void drawFccCraft(GuiGraphics guiGraphics, int x, int y, int w, int h, boolean airship) {
        int body = airship ? 0xFFE2D0AA : 0xFFD8C395;
        guiGraphics.fill(x + w / 4, y, x + w * 3 / 4, y + h, body);
        guiGraphics.fill(x, y + h / 3, x + w, y + h * 2 / 3, body);
        drawOutline(guiGraphics, x + w / 4, y, w / 2, h, 0xFF705438);
        drawOutline(guiGraphics, x, y + h / 3, w, h / 3, 0xFF705438);
        guiGraphics.fill(x + w / 2 - 5, y + h / 2 - 5, x + w / 2 + 6, y + h / 2 + 6, 0xFF4A7C8F);
    }

    private void drawFccZone(GuiGraphics guiGraphics, int x, int y, String label, int color, boolean active) {
        guiGraphics.fill(x, y, x + 36, y + 15, active ? 0x88BF9150 : 0x33A88F66);
        drawOutline(guiGraphics, x, y, 36, 15, color);
        this.drawTinyText(guiGraphics, this.trim(label, 30), x + 3, y + 4, this.diagramInk(color));
    }

    private void drawFccSignalPulse(GuiGraphics guiGraphics, int centerX, int centerY, int color) {
        int radius = 8 + Math.floorMod((int)(net.minecraft.Util.getMillis() / 120L), 14);
        guiGraphics.fill(centerX - 2, centerY - radius, centerX + 3, centerY - radius + 3, color);
        guiGraphics.fill(centerX - 2, centerY + radius - 2, centerX + 3, centerY + radius + 1, color);
        guiGraphics.fill(centerX - radius, centerY - 2, centerX - radius + 3, centerY + 3, color);
        guiGraphics.fill(centerX + radius - 2, centerY - 2, centerX + radius + 1, centerY + 3, color);
    }

    private void drawFccRouteScale(GuiGraphics guiGraphics, int x, int y, int width, int color) {
        guiGraphics.fill(x, y, x + width, y + 3, 0x88705438);
        int knobTravel = Math.max(1, width - 8);
        int knob = Math.floorMod((int)(net.minecraft.Util.getMillis() / 70L), knobTravel);
        guiGraphics.fill(x + knob, y - 3, x + knob + 8, y + 7, color);
        guiGraphics.fill(x + knob + 2, y - 1, x + knob + 6, y + 5, 0xFFE8D7AE);
    }

    private void drawFccRouteMap(GuiGraphics guiGraphics, int x, int y, int w, int h) {
        guiGraphics.fill(x, y, x + w, y + h, 0xE8E2D0AA);
        drawOutline(guiGraphics, x, y, w, h, 0xFF66507F);
        for (int gx = x + 8; gx < x + w - 4; gx += 14) {
            guiGraphics.fill(gx, y + 5, gx + 1, y + h - 5, 0x24A88F66);
        }
        for (int gy = y + 8; gy < y + h - 4; gy += 14) {
            guiGraphics.fill(x + 5, gy, x + w - 5, gy + 1, 0x24A88F66);
        }
        int startX = x + 14;
        int startY = y + h - 14;
        int targetX = x + w - 16;
        int targetY = y + 16;
        this.drawAnimatedArrow(guiGraphics, startX, targetX, startY - 8, 0xFF66507F, true);
        guiGraphics.fill(startX - 4, startY - 4, startX + 5, startY + 5, 0xFF4C6F32);
        guiGraphics.fill(targetX - 5, targetY - 5, targetX + 6, targetY + 6, 0xFF8A5425);
        int travel = Math.max(1, w - 36);
        int phase = Math.floorMod((int)(net.minecraft.Util.getMillis() / 55L), travel);
        int craftX = startX + phase;
        int craftY = startY - 8 - Math.round(phase * (startY - targetY - 8) / (float) travel);
        guiGraphics.fill(craftX - 3, craftY - 3, craftX + 4, craftY + 4, 0xFF2F5F73);
        this.drawTinyText(guiGraphics, "Nav", x + 6, y + 6, TEXT);
    }

    private int diagramInk(int color) {
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        r = Math.max(40, Math.round(r * 0.42f));
        g = Math.max(32, Math.round(g * 0.42f));
        b = Math.max(24, Math.round(b * 0.42f));
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private void drawFccProp(GuiGraphics guiGraphics, int x, int y, int color) {
        int phase = Math.floorMod((int)(net.minecraft.Util.getMillis() / 90L), 12);
        guiGraphics.fill(x + 5, y, x + 13, y + 22, 0x55705438);
        guiGraphics.fill(x, y + 7 + phase / 4, x + 18, y + 12 + phase / 4, color);
        guiGraphics.fill(x + 7 + phase / 5, y + 2, x + 12 + phase / 5, y + 20, color);
    }

    private void drawWheel(GuiGraphics guiGraphics, int x, int y, int color) {
        guiGraphics.fill(x - 7, y - 7, x + 8, y + 8, color);
        guiGraphics.fill(x - 4, y - 4, x + 5, y + 5, 0xFFE8D7AE);
        int phase = Math.floorMod((int)(net.minecraft.Util.getMillis() / 80L), 8);
        guiGraphics.fill(x - 6 + phase / 2, y - 1, x + 6 - phase / 2, y + 2, 0xFF4A7C8F);
    }

    private int visualGuideSheetCount() {
        if (this.isInventoryDisplayGuidePage()) {
            return INVENTORY_DISPLAY_SHEETS;
        }
        if (this.isFlightControlComputerGuidePage()) {
            return FCC_SHEETS;
        }
        if (this.isStatsPanelGuidePage()) {
            return STATS_PANEL_SHEETS;
        }
        if (this.isDataDialGuidePage()) {
            return DATA_DIAL_SHEETS;
        }
        if (this.selectedPage >= 0 && this.selectedPage < this.pages.size()) {
            int figs = com.createstats.manual.figure.ManualFigures.sheetCount(this.pages.get(this.selectedPage).id());
            if (figs > 0) {
                return figs;
            }
        }
        return 0;
    }

    private boolean isFigurePage() {
        if (this.selectedPage < 0 || this.selectedPage >= this.pages.size()) {
            return false;
        }
        return com.createstats.manual.figure.ManualFigures.has(this.pages.get(this.selectedPage).id());
    }

    private void renderFigureGuide(GuiGraphics guiGraphics) {
        String pageId = this.pages.get(this.selectedPage).id();
        com.createstats.manual.figure.FigureSheet sheet =
                com.createstats.manual.figure.ManualFigures.sheet(pageId, this.selectedSubpage);
        if (sheet == null) {
            return;
        }
        int x = this.articleBodyLeft;
        int y = this.articleBodyTop;
        int w = this.articleBodyWidth;
        int h = this.articleBodyHeight;
        com.createstats.manual.figure.ManualFigureRenderer.render(
                guiGraphics, this.font, x, y, w, h, sheet, net.minecraft.Util.getMillis());
        this.renderFigureSheetPips(guiGraphics, x, y, w, h);
    }

    private void renderFigureSheetPips(GuiGraphics guiGraphics, int x, int y, int w, int h) {
        int count = this.subpages.size();
        if (count <= 1) {
            return;
        }
        int pipY = y + h - 16;
        int pipW = Math.max(10, (w - 20) / count);
        guiGraphics.fill(x + 6, pipY - 3, x + w - 6, pipY + 14, 0xE8F1E5C6);
        drawOutline(guiGraphics, x + 6, pipY - 3, w - 12, 17, 0x66705438);
        for (int i = 0; i < count; i++) {
            int px = x + 10 + i * pipW;
            int color = i == this.selectedSubpage ? 0xFF2F5F73 : 0xFF7B6951;
            guiGraphics.fill(px, pipY, px + pipW - 3, pipY + 10, i == this.selectedSubpage ? 0x775A8EA0 : 0x18A88F66);
            drawOutline(guiGraphics, px, pipY, pipW - 3, 10, color);
            this.drawTinyText(guiGraphics, Integer.toString(i + 1), px + Math.max(2, (pipW - 8) / 2), pipY + 1, TEXT);
        }
    }

    private boolean handleFigureGuideClick(double mouseX, double mouseY) {
        int x = this.articleBodyLeft;
        int y = this.articleBodyTop;
        int w = this.articleBodyWidth;
        int h = this.articleBodyHeight;
        int count = this.subpages.size();
        if (count <= 1) {
            return false;
        }
        int pipY = y + h - 16;
        int pipW = Math.max(10, (w - 20) / count);
        if (mouseY >= pipY - 3 && mouseY <= pipY + 14) {
            for (int i = 0; i < count; i++) {
                int px = x + 10 + i * pipW;
                if (mouseX >= px && mouseX <= px + pipW - 3) {
                    this.selectedSubpage = i;
                    this.updateButtons();
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isFlightControlComputerGuidePage() {
        if (this.selectedPage < 0 || this.selectedPage >= this.pages.size()) {
            return false;
        }
        return "flight_control_computer".equals(this.pages.get(this.selectedPage).id());
    }

    private int findPageIndex(String pageId) {
        for (int index = 0; index < this.pages.size(); index++) {
            if (this.pages.get(index).id().equals(pageId)) {
                return index;
            }
        }
        return -1;
    }

    private boolean selectPage(int index) {
        int clamped = Mth.clamp(index, 0, Math.max(0, this.pages.size() - 1));
        if (clamped == this.selectedPage && this.selectedSubpage == 0) {
            return false;
        }
        this.selectedPage = clamped;
        this.selectedSubpage = 0;
        this.ensureSelectedVisible();
        this.capturePlayerNotesDraft();
        this.rebuildSubpages(false);
        return true;
    }

    private boolean changePage(int delta) {
        int target = Mth.clamp(this.selectedPage + delta, 0, Math.max(0, this.pages.size() - 1));
        if (target == this.selectedPage) {
            return false;
        }
        this.selectedPage = target;
        this.selectedSubpage = 0;
        this.ensureSelectedVisible();
        this.capturePlayerNotesDraft();
        this.rebuildSubpages(false);
        return true;
    }

    private boolean advanceReading(int delta) {
        if (delta == 0) {
            return false;
        }
        int nextSubpage = this.selectedSubpage + delta;
        if (nextSubpage >= 0 && nextSubpage < this.subpages.size()) {
            this.selectedSubpage = nextSubpage;
            this.updateButtons();
            return true;
        }

        int pageStep = delta > 0 ? 1 : -1;
        int nextPage = this.selectedPage + pageStep;
        if (nextPage < 0 || nextPage >= this.pages.size()) {
            return false;
        }

        this.selectedPage = nextPage;
        this.ensureSelectedVisible();
        this.capturePlayerNotesDraft();
        this.rebuildSubpages(false);
        if (delta < 0) {
            this.selectedSubpage = this.subpages.size() - 1;
            this.updateButtons();
        }
        return true;
    }

    private void scrollContents(int delta) {
        this.tocScroll = Mth.clamp(this.tocScroll + delta, 0, this.maxTocScroll());
        this.updateButtons();
    }

    private void ensureSelectedVisible() {
        int selectedRow = this.pageRowLookup[this.selectedPage];
        if (selectedRow < this.tocScroll) {
            this.tocScroll = selectedRow;
        } else if (selectedRow >= this.tocScroll + this.tocVisibleRows) {
            this.tocScroll = selectedRow - this.tocVisibleRows + 1;
        }
        if (selectedRow > 0 && this.tocRows.get(selectedRow - 1).isHeading() && this.tocScroll == selectedRow) {
            this.tocScroll = selectedRow - 1;
        }
        this.tocScroll = Mth.clamp(this.tocScroll, 0, this.maxTocScroll());
    }

    private void rebuildSubpages(boolean preserveSubpage) {
        FactoryManualPage page = this.pages.get(this.selectedPage);
        this.currentSection = ManualSection.forPage(page.id());
        this.titleLines = this.font.split(page.title(), this.articleWidth - 48);
        this.titleTrimmed = this.titleLines.size() > 2;
        if (this.titleTrimmed) {
            this.titleLines = new ArrayList<>(this.titleLines.subList(0, 2));
        }
        this.articleBodyLeft = this.articleLeft + 10;
        this.articleBodyWidth = this.articleWidth - 20;
        this.bodyLineAdvance = this.font.lineHeight + 2;
        int titleHeight = Math.max(16, this.titleLines.size() * (this.font.lineHeight + 1));
        this.articleBodyTop = this.articleTop + 28 + titleHeight + 18;
        this.articleBodyHeight = Math.max(this.bodyLineAdvance, this.articleTop + this.articleHeight - 24 - this.articleBodyTop);
        int linesPerSubpage = Math.max(1, this.articleBodyHeight / this.bodyLineAdvance);

        List<List<FormattedCharSequence>> rebuilt = new ArrayList<>();
        int visualGuideSheets = this.visualGuideSheetCount();
        if (this.isPlayerNotesPage() || visualGuideSheets > 0) {
            rebuilt.add(Collections.emptyList());
            for (int sheet = 1; sheet < visualGuideSheets; sheet++) {
                rebuilt.add(Collections.emptyList());
            }
        } else {
            List<FormattedCharSequence> wrapped = this.font.split(page.body(), this.articleBodyWidth);
            if (wrapped.isEmpty()) {
                rebuilt.add(Collections.emptyList());
            } else {
                for (int i = 0; i < wrapped.size(); i += linesPerSubpage) {
                    rebuilt.add(new ArrayList<>(wrapped.subList(i, Math.min(i + linesPerSubpage, wrapped.size()))));
                }
            }
        }
        this.subpages = rebuilt;
        if (preserveSubpage) {
            this.selectedSubpage = Mth.clamp(this.selectedSubpage, 0, Math.max(0, this.subpages.size() - 1));
        } else {
            this.selectedSubpage = 0;
        }
        this.updateButtons();
    }

    private void updateButtons() {
        if (this.tocUpButton != null) {
            this.tocUpButton.active = this.tocScroll > 0;
        }
        if (this.tocDownButton != null) {
            this.tocDownButton.active = this.tocScroll < this.maxTocScroll();
        }
        if (this.prevSubpageButton != null) {
            this.prevSubpageButton.active = this.canAdvanceReading(-1);
        }
        if (this.nextSubpageButton != null) {
            this.nextSubpageButton.active = this.canAdvanceReading(1);
        }
        this.updatePlayerNotesWidgetState();
    }

    private boolean canAdvanceReading(int delta) {
        if (delta < 0) {
            return this.selectedSubpage > 0 || this.selectedPage > 0;
        }
        if (delta > 0) {
            return this.selectedSubpage < this.subpages.size() - 1 || this.selectedPage < this.pages.size() - 1;
        }
        return false;
    }

    private int getTocIndexAt(double mouseX, double mouseY) {
        if (!this.isInsideContents(mouseX, mouseY) || mouseY < this.tocListTop) {
            return -1;
        }
        int row = this.tocScroll + (int) ((mouseY - this.tocListTop) / TOC_ROW_HEIGHT);
        if (row < 0 || row >= this.tocRows.size()) {
            return -1;
        }
        TocRow tocRow = this.tocRows.get(row);
        return tocRow.isHeading() ? -1 : tocRow.pageIndex();
    }

    private boolean isInsideContents(double mouseX, double mouseY) {
        return mouseX >= this.tocLeft && mouseX <= this.tocLeft + this.tocWidth
                && mouseY >= this.tocTop && mouseY <= this.tocTop + this.tocHeight;
    }

    private boolean isInsideArticle(double mouseX, double mouseY) {
        return mouseX >= this.articleLeft && mouseX <= this.articleLeft + this.articleWidth
                && mouseY >= this.articleTop && mouseY <= this.articleTop + this.articleHeight;
    }

    private boolean isHoveringTitle(int mouseX, int mouseY) {
        int titleTop = this.articleTop + 27;
        int titleBottom = titleTop + this.titleLines.size() * (this.font.lineHeight + 1);
        return mouseX >= this.articleLeft + 34 && mouseX <= this.articleLeft + this.articleWidth - 10
                && mouseY >= titleTop && mouseY <= titleBottom;
    }

    private int maxTocScroll() {
        return Math.max(0, this.tocRows.size() - this.tocVisibleRows);
    }

    private void buildTocRows() {
        this.tocRows.clear();
        ManualSection current = null;
        for (int i = 0; i < this.pages.size(); i++) {
            ManualSection section = ManualSection.forPage(this.pages.get(i).id());
            if (section != current) {
                this.tocRows.add(TocRow.heading(section));
                current = section;
            }
            this.pageRowLookup[i] = this.tocRows.size();
            this.tocRows.add(TocRow.page(i, section));
        }
    }

    private String trim(String text, int maxWidth) {
        if (this.font.width(text) <= maxWidth) {
            return text;
        }
        return this.font.plainSubstrByWidth(text, Math.max(0, maxWidth - this.font.width("…"))) + "…";
    }

    private void renderPaperSection(GuiGraphics guiGraphics, int left, int top, int right, int bottom, int fillColor) {
        guiGraphics.fill(left, top, right, bottom, fillColor);
        drawOutline(guiGraphics, left, top, right - left, bottom - top, BORDER);
    }

    private void drawPaperText(GuiGraphics guiGraphics, String text, int x, int y, int color) {
        if (this.shouldOutlinePaperText(color)) {
            int outline = 0xFF24160A;
            guiGraphics.drawString(this.font, text, x - 1, y, outline, false);
            guiGraphics.drawString(this.font, text, x + 1, y, outline, false);
            guiGraphics.drawString(this.font, text, x, y - 1, outline, false);
            guiGraphics.drawString(this.font, text, x, y + 1, outline, false);
        }
        guiGraphics.drawString(this.font, text, x, y, color, false);
    }

    private void drawPaperCenteredText(GuiGraphics guiGraphics, String text, int centerX, int y, int color) {
        this.drawPaperText(guiGraphics, text, centerX - this.font.width(text) / 2, y, color);
    }

    private void renderStatusChip(GuiGraphics guiGraphics, int x, int y, String label, int color) {
        int width = this.font.width(label) + 10;
        guiGraphics.fill(x, y, x + width, y + 14, 0xE8F1E5C6);
        guiGraphics.fill(x, y, x + width, y + 1, color);
        guiGraphics.fill(x, y + 13, x + width, y + 14, BORDER);
        guiGraphics.fill(x, y, x + 1, y + 14, BORDER);
        guiGraphics.fill(x + width - 1, y, x + width, y + 14, BORDER);
        this.drawPaperText(guiGraphics, label, x + 5, y + 3, this.paperInk(color));
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
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        int max = Math.max(r, Math.max(g, b));
        if (max <= 0) {
            return 0xFFECDDBA;
        }
        float scale = 232.0f / max;
        r = Math.min(255, Math.round(r * scale));
        g = Math.min(255, Math.round(g * scale));
        b = Math.min(255, Math.round(b * scale));
        r = Math.min(255, Math.round(r * 0.72f + 255 * 0.28f));
        g = Math.min(255, Math.round(g * 0.72f + 255 * 0.28f));
        b = Math.min(255, Math.round(b * 0.72f + 255 * 0.28f));
        int floor = 104;
        r = Math.max(floor, r);
        g = Math.max(floor, g);
        b = Math.max(floor, b);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private int withAlpha(int color, int alpha) {
        return (alpha << 24) | (color & 0x00FFFFFF);
    }

    private boolean isPlayerNotesPage() {
        if (this.selectedPage < 0 || this.selectedPage >= this.pages.size()) {
            return false;
        }
        return "player_notes".equals(this.pages.get(this.selectedPage).id());
    }

    private void capturePlayerNotesDraft() {
        if (this.playerNotesEditor != null && this.playerNotesEditor.isVisible()) {
            this.playerNotesDraft = this.playerNotesEditor.getValue();
        }
    }

    private void updatePlayerNotesWidgetState() {
        boolean active = this.isPlayerNotesPage() && this.playerNotesEditor != null && this.savePlayerNotesButton != null;
        if (this.playerNotesEditor != null) {
            this.playerNotesEditor.setVisible(active);
            this.playerNotesEditor.active = active;
            if (active) {
                this.playerNotesEditor.setX(this.articleBodyLeft);
                this.playerNotesEditor.setY(this.articleBodyTop + 6);
                this.playerNotesEditor.setWidth(this.articleBodyWidth);
                if (!this.playerNotesEditor.getValue().equals(this.playerNotesDraft)) {
                    this.playerNotesEditor.setValue(this.playerNotesDraft);
                }
            }
        }
        if (this.savePlayerNotesButton != null) {
            this.savePlayerNotesButton.visible = active;
            this.savePlayerNotesButton.active = active;
        }
    }

    private void savePlayerNotes() {
        if (this.playerNotesEditor != null) {
            this.playerNotesDraft = this.playerNotesEditor.getValue();
        }
        ManualPlayerNotesStorage.set(this.playerNotesDraft);
    }

    private static void drawOutline(GuiGraphics guiGraphics, int x, int y, int width, int height, int color) {
        guiGraphics.fill(x, y, x + width, y + 1, color);
        guiGraphics.fill(x, y + height - 1, x + width, y + height, color);
        guiGraphics.fill(x, y, x + 1, y + height, color);
        guiGraphics.fill(x + width - 1, y, x + width, y + height, color);
    }

    private record TocRow(int pageIndex, ManualSection section) {
        private static TocRow heading(ManualSection section) {
            return new TocRow(-1, section);
        }

        private static TocRow page(int pageIndex, ManualSection section) {
            return new TocRow(pageIndex, section);
        }

        private boolean isHeading() {
            return this.pageIndex < 0;
        }
    }

    private enum ManualSection {
        BASICS("gui.create_stats.factory_manual.section.basics", 0xFFB9843E),
        TOOLS("gui.create_stats.factory_manual.section.tools", 0xFF9B7C4F),
        AERONAUTICS("gui.create_stats.factory_manual.section.aeronautics", 0xFF6C8F8A),
        INSTRUMENTS("gui.create_stats.factory_manual.section.instruments", 0xFF7E9352),
        CONTROL("gui.create_stats.factory_manual.section.control", 0xFFA26B3B),
        LOGISTICS("gui.create_stats.factory_manual.section.logistics", 0xFF8B6C96),
        FLIGHT_CONTROL("gui.create_stats.factory_manual.section.flight_control", 0xFF4A7C8F),
        PERSONAL_REFLECTION("gui.create_stats.factory_manual.section.personal_reflection", 0xFF9B8A6B),
        PLAYER_NOTES("gui.create_stats.factory_manual.section.player_notes", 0xFF7B8E8B);

        private final String titleKey;
        private final int tintColor;

        ManualSection(String titleKey, int tintColor) {
            this.titleKey = titleKey;
            this.tintColor = tintColor;
        }

        private Component title() {
            return Component.translatable(this.titleKey);
        }

        private int tintColor() {
            return this.tintColor;
        }

        private static ManualSection forPage(String id) {
            return switch (id) {
                case "intro" -> BASICS;
                case "stats_configurator", "stats_handheld", "memory_cog", "signal_antennas" -> TOOLS;
                case "aero_stats_link", "aero_source_matrix", "contraption_info", "aero_flight_deck", "aeroline_director", "hot_air_burner_telemetry" -> AERONAUTICS;
                case "factory_logic_controller", "machine_selection_card", "safety_clutch_link_card", "safety_clutch", "auto_transmission_clutch", "control_clutch", "load_balancer", "smart_gearshift", "overflow_gate", "kinetic_flywheel" -> CONTROL;
                case "freight_manifest_clipboard", "dispatch_board" -> LOGISTICS;
                case "rpm_governor", "flight_control_computer", "damage_observer", "battle_screen_display",
                    "path_scanner", "pathfinder_computer" -> FLIGHT_CONTROL;
                case "personal_reflection" -> PERSONAL_REFLECTION;
                case "player_notes" -> PLAYER_NOTES;
                default -> INSTRUMENTS;
            };
        }
    }

    private enum PaperButtonStyle {
        ACCENT(0xFFE8D7AE, 0xFFF0D39B, BRASS, TEXT),
        NEUTRAL(0xFFE3D6B8, 0xFFEADDC1, BORDER, TEXT);

        private final int backgroundColor;
        private final int hoverBackgroundColor;
        private final int borderColor;
        private final int textColor;

        PaperButtonStyle(int backgroundColor, int hoverBackgroundColor, int borderColor, int textColor) {
            this.backgroundColor = backgroundColor;
            this.hoverBackgroundColor = hoverBackgroundColor;
            this.borderColor = borderColor;
            this.textColor = textColor;
        }
    }

    private static class PaperButton extends Button {
        private final PaperButtonStyle style;

        private PaperButton(int x, int y, int width, int height, Component message, OnPress onPress, PaperButtonStyle style) {
            super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
            this.style = style;
        }

        @Override
        protected void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            Minecraft minecraft = Minecraft.getInstance();
            int backgroundColor = this.active
                    ? (this.isHoveredOrFocused() ? this.style.hoverBackgroundColor : this.style.backgroundColor)
                    : 0xFFD4C8AB;
            int borderColor = this.active ? this.style.borderColor : 0xFF8A7A63;
            int textColor = this.active ? this.style.textColor : DIM;
            guiGraphics.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height, backgroundColor);
            drawOutline(guiGraphics, this.getX(), this.getY(), this.width, this.height, borderColor);
            guiGraphics.fill(this.getX() + 1, this.getY() + 1, this.getX() + this.width - 1, this.getY() + 2, 0x22FFFFFF);
            guiGraphics.drawCenteredString(minecraft.font, this.getMessage(),
                    this.getX() + this.width / 2, this.getY() + (this.height - 8) / 2, textColor);
        }
    }
}
