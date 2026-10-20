package com.createstats.client;

import com.createstats.blockentity.SourceConfig;
import com.createstats.blockentity.StatsDisplayPanelBE;
import com.createstats.display.DisplayTheme;
import com.createstats.display.PanelHudSettings;
import com.createstats.display.SpecializedHudWidgetCatalog;
import com.createstats.display.StressometerHudState;
import com.createstats.network.HudLayoutConfigPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

@OnlyIn(Dist.CLIENT)
public class StressometerHudSettingsScreen extends Screen {
    private static final String AERO_FLIGHT_DECK_SOURCE_ID = "aero_flight_deck";
    private static final String AEROLINE_DIRECTOR_SOURCE_ID = "aeroline_director";
    private static final int HANDLE_SIZE = 10;
    private static final int RAIL_BRIGHT = 0xFFE8D3A0;
    private static final int RAIL_MID = 0xFFC1A86B;
    private static final int RAIL_SHADOW = 0xFF74603A;
    private static final int PANEL_LIFT = 0x28FFFFFF;
    private static final int PANEL_SHADE = 0x0A000000;
    private static final int NOISE_A = 0x02D8CAA4;
    private static final int NOISE_B = 0x024B3B25;
    private static final int GRID_MINOR = 0x08C8A870;
    private static final int GRID_MAJOR = 0x12C8A870;

    private final StatsDisplayPanelBE panel;
    private final Screen parentScreen;
    private final String focusedSourceId;
    private final String focusedSourceType;
    private final LinkedHashSet<String> scopedSourceIds;
    private PanelHudSettings draftHudSettings;
    private StressometerHudState draftState;
    private Button enableButton;
    private Button sourceButton;
    private final ArrayList<Button> refreshButtons = new ArrayList<Button>();
    private int leftX;
    private int leftW;
    private int rightX;
    private int rightW;
    private int canvasX;
    private int canvasY;
    private int canvasW;
    private int canvasH;
    private int viewportX;
    private int viewportY;
    private int viewportW;
    private int viewportH;
    private int topBarH;
    private int bottomBarH;
    private int presetsY;
    private int sourceWidgetsY;
    private int addedWidgetsY;
    private int categoryPage;
    private int sourcePage;

    private String selectedCategoryId = "";

    private StressometerHudState pendingResetConfirm;
    private int resetModalConfirmX, resetModalConfirmY, resetModalCancelX, resetModalCancelY, resetModalBtnW, resetModalBtnH;
    private int BG;
    private int TEXT;
    private int DIM;
    private int ACCENT;
    private int BORDER;
    private boolean dragging;
    private boolean dragArmed;
    private static final int DRAG_THRESHOLD_SQ = 9;
    private boolean resizing;
    private int dragOffsetX;
    private int dragOffsetY;
    private StressometerHudState.Bounds.ResolvedBounds resizeStartBounds;
    private int dragStartMouseX;
    private int dragStartMouseY;
    private long tickCount;
    private String lastAddedWidgetId;
    private long lastAddedTick = -100L;
    private long lastSourceCycleTick = -100L;

    private final Deque<UndoEntry> undoStack = new ArrayDeque<>();
    private final Deque<UndoEntry> redoStack = new ArrayDeque<>();
    private static final int UNDO_CAP = 50;

    private CompoundTag lastCommittedSnapshot;

    private String pendingActionLabel = "Edit";

    private CompoundTag pendingGestureSnapshot;
    private String pendingGestureLabel = "";

    private boolean snapToGrid;

    private boolean helpMode;

    private CompoundTag savedSnapshot;

    private boolean pendingDiscardConfirm;
    private int discardModalConfirmX, discardModalConfirmY, discardModalCancelX, discardModalCancelY, discardModalBtnW, discardModalBtnH;

    private String statusMessage = "";
    private long statusMessageExpiryTick;

    private DragHandle activeHandle = DragHandle.NONE;
    private int dragStartBoundsX, dragStartBoundsY, dragStartBoundsW, dragStartBoundsH;

    private BrowserDomain selectedDomain = BrowserDomain.ALL;
    private String browserQuery = "";
    private EditBox browserSearchBox;
    private final ArrayDeque<String> recentlyUsedWidgetIds = new ArrayDeque<>();
    private static final int RECENT_CAP = 8;

    private StressometerHudState.LayoutPreset pendingPresetConfirm;
    private int presetModalApplyX, presetModalApplyY, presetModalAddX, presetModalAddY, presetModalCancelX, presetModalCancelY;

    private int chipDuplicateX = -1, chipDuplicateY, chipDeleteX = -1, chipDeleteY, chipVariantX = -1, chipVariantY, chipBtnW, chipBtnH;

    private final int[] domainChipX = new int[BrowserDomain.values().length];
    private int domainChipY, domainChipW, domainChipH;

    private DisplayTheme previewThemeOverride;

    private boolean variantMenuOpen;

    public StressometerHudSettingsScreen(StatsDisplayPanelBE panel, Screen parentScreen) {
        this(panel, parentScreen, "", "", List.of());
    }

    public StressometerHudSettingsScreen(StatsDisplayPanelBE panel, Screen parentScreen, String focusedSourceId, String focusedSourceType, List<String> scopedSourceIds) {
        super(Component.literal("HUD Studio"));
        this.panel = panel.getControllerBE();
        this.parentScreen = parentScreen;
        this.focusedSourceId = focusedSourceId != null ? focusedSourceId : "";
        this.focusedSourceType = focusedSourceType != null ? focusedSourceType : "";
        this.scopedSourceIds = new LinkedHashSet<String>();
        if (scopedSourceIds != null) {
            this.scopedSourceIds.addAll(scopedSourceIds);
        }
        this.draftHudSettings = this.panel.getSettings().getPanelHudSettings().copy();
        this.draftState = this.draftHudSettings.getStressometerHud();
        this.draftState.normalize(this.panel.getSourceRegistry());
        this.removeWidgetsOutsideStudioScope();
    }

    @Override
    protected void init() {
        this.refreshButtons.clear();
        final DisplayTheme theme = this.studioTheme();
        this.BG = theme.bg;
        this.TEXT = theme.text;
        this.DIM = theme.dim;
        this.ACCENT = theme.accent;
        this.BORDER = theme.border;

        final int margin = 10;
        this.topBarH = 34;
        this.bottomBarH = 26;
        final boolean compactScreen = this.width < 720;
        this.leftW = compactScreen ? 168 : Math.min(236, Math.max(196, this.width / 4));
        this.rightW = compactScreen ? 190 : Math.min(248, Math.max(216, this.width / 4));
        this.leftX = margin;
        this.rightX = this.width - margin - this.rightW;
        this.canvasX = this.leftX + this.leftW + margin;
        this.canvasY = margin + this.topBarH;
        this.canvasW = Math.max(1, this.rightX - margin - this.canvasX);
        this.canvasH = Math.max(1, this.height - this.canvasY - this.bottomBarH - margin);
        this.updateViewport();

        addTooltip(this.addButton(this.width - margin - 56, margin + 7, 56, 20, "Save", this::saveAndClose), "Save changes and close the studio.");
        addTooltip(this.addButton(this.width - margin - 118, margin + 7, 58, 20, "Cancel", this::cancel), "Discard changes and close the studio.");
        addTooltip(this.addButton(this.width - margin - 186, margin + 7, 64, 20, "Reset", this::requestReset), "Reset HUD layout to defaults.");

        final int toolY = margin + 7;
        int toolX = 200;
        final Button undoBtn = this.addButton(toolX, toolY, 26, 20, "↶", this::undo);
        addTooltip(undoBtn, "Undo (Ctrl+Z)");
        undoBtn.active = !this.undoStack.isEmpty();
        toolX += 28;
        final Button redoBtn = this.addButton(toolX, toolY, 26, 20, "↷", this::redo);
        addTooltip(redoBtn, "Redo (Ctrl+Y / Ctrl+Shift+Z)");
        redoBtn.active = !this.redoStack.isEmpty();
        toolX += 28;
        final Button snapBtn = this.addButton(toolX, toolY, 60, 20, this.snapToGrid ? "Snap: On" : "Snap: Off", () -> {
            this.snapToGrid = !this.snapToGrid;
            this.setStatus(this.snapToGrid ? "Snap-to-grid: on" : "Snap-to-grid: off");
            this.rebuildStudio();
        });
        addTooltip(snapBtn, "Toggle 8-pixel snap on drag/resize/nudge (G).");
        toolX += 62;
        final Button helpBtn = this.addButton(toolX, toolY, 22, 20, "?", () -> {
            this.helpMode = !this.helpMode;
            this.setStatus(this.helpMode ? "Help mode: on" : "Help mode: off");
        });
        addTooltip(helpBtn, "Toggle help overlay.");
        toolX += 24;

        final Button themeBtn = this.addButton(toolX, toolY, 92, 20,
                "Preview: " + this.studioTheme().getDisplayName(), () -> {
                    final DisplayTheme curr = this.studioTheme();
                    this.previewThemeOverride = curr.next();
                    this.setStatus("Preview theme: " + this.previewThemeOverride.getDisplayName());
                    this.rebuildStudio();
                });
        addTooltip(themeBtn, "Cycle studio chrome preview through available themes (does not save).");

        final int searchY = this.canvasY + 22;
        if (this.browserSearchBox == null) {
            this.browserSearchBox = new EditBox(this.font, this.leftX + 6, searchY, this.leftW - 12, 14, Component.literal("Search widgets"));
            this.browserSearchBox.setHint(Component.literal("Search widgets…"));
        } else {
            this.browserSearchBox.setX(this.leftX + 6);
            this.browserSearchBox.setY(searchY);
            this.browserSearchBox.setWidth(this.leftW - 12);
        }
        this.browserSearchBox.setValue(this.browserQuery == null ? "" : this.browserQuery);
        this.browserSearchBox.setResponder(s -> {
            this.browserQuery = s == null ? "" : s;
        });
        this.addRenderableWidget(this.browserSearchBox);

        this.domainChipY = searchY + 18;
        this.domainChipH = 14;
        final BrowserDomain[] domains = BrowserDomain.values();
        final int chipGap = 2;
        final int chipAreaW = this.leftW - 12;
        this.domainChipW = Math.max(28, (chipAreaW - chipGap * (domains.length - 1)) / domains.length);
        for (int i = 0; i < domains.length; ++i) {
            this.domainChipX[i] = this.leftX + 6 + i * (this.domainChipW + chipGap);
        }

        int y = this.domainChipY + this.domainChipH + 8;
        this.enableButton = this.addButton(this.leftX + 10, y, this.leftW - 20, 20, "", () -> {
            this.setNextActionLabel(this.draftState.isEnabled() ? "Disable HUD" : "Enable HUD");
            this.draftState.setEnabled(!this.draftState.isEnabled());
            this.rebuildStudio();
        });
        addTooltip(this.enableButton, "Toggle whether this HUD overlay renders in-game.");
        y += 26;
        if (this.hasTopologyControls()) {
            this.sourceButton = this.addButton(this.leftX + 10, y, this.leftW - 20, 20, "", () -> {
                this.setNextActionLabel("Cycle source");
                this.cycleSourceBinding();
                this.rebuildStudio();
            });
            y += 46;
        } else {
            this.sourceButton = null;
            y += 52;
        }
        this.presetsY = y;
        this.addPresetButton(this.leftX + 10, y, "Full", StressometerHudState.LayoutPreset.FULL_PANEL);
        this.addPresetButton(this.leftX + 10 + (this.leftW - 24) / 2, y, "Compact", StressometerHudState.LayoutPreset.COMPACT_ROW);
        y += 24;
        this.addPresetButton(this.leftX + 10, y, "Cards", StressometerHudState.LayoutPreset.CARD_STACK);
        this.addPresetButton(this.leftX + 10 + (this.leftW - 24) / 2, y, "Mini", StressometerHudState.LayoutPreset.MINIMAL);
        y += 24;
        this.addPresetButton(this.leftX + 10, y, "Factory", StressometerHudState.LayoutPreset.FACTORY_FLOOR);
        this.addPresetButton(this.leftX + 10 + (this.leftW - 24) / 2, y, "Ship", StressometerHudState.LayoutPreset.SHIP_BRIDGE);
        y += 24;
        this.addPresetButton(this.leftX + 10, y, "Train", StressometerHudState.LayoutPreset.TRAIN_DISPATCH);
        this.addPresetButton(this.leftX + 10 + (this.leftW - 24) / 2, y, "Pilot", StressometerHudState.LayoutPreset.PILOT_HUD);

        y = this.addWidgetsStartY();
        final List<StressometerHudState.SourceCategory> categories = this.getVisibleCategories();

        if (!categories.isEmpty()) {
            int restored = -1;
            if (!this.selectedCategoryId.isEmpty()) {
                for (int i = 0; i < categories.size(); ++i) {
                    if (categories.get(i).name().equals(this.selectedCategoryId)) {
                        restored = i;
                        break;
                    }
                }
            }
            this.categoryPage = restored >= 0 ? restored : Math.max(0, Math.min(this.categoryPage, categories.size() - 1));
            this.selectedCategoryId = categories.get(this.categoryPage).name();
        } else {
            this.categoryPage = 0;
        }
        final StressometerHudState.SourceCategory currentCategory = categories.isEmpty()
                ? null
                : categories.get(this.categoryPage);
        if (categories.size() > 1) {
            final int categoryCount = categories.size();
            final List<StressometerHudState.SourceCategory> categoriesRef = categories;
            this.addButton(this.leftX + 10, y, (this.leftW - 24) / 2, 18, "‹ Groups", () -> {
                this.categoryPage = Math.max(0, this.categoryPage - 1);
                this.selectedCategoryId = categoriesRef.get(this.categoryPage).name();
                this.sourcePage = 0;
                this.rebuildStudio();
            });
            this.addButton(this.leftX + 10 + (this.leftW - 24) / 2, y, (this.leftW - 24) / 2, 18, "Groups ›", () -> {
                this.categoryPage = Math.min(categoryCount - 1, this.categoryPage + 1);
                this.selectedCategoryId = categoriesRef.get(this.categoryPage).name();
                this.sourcePage = 0;
                this.rebuildStudio();
            });
            y += 40;
        } else if (!categories.isEmpty()) {
            y += 18;
        }
        this.sourceWidgetsY = y;
        final List<StudioButton> categoryButtons = this.buildCategoryButtons(currentCategory);
        final int sourceButtonLimitY = this.canvasY + this.canvasH - 126;
        int visibleSourceRows = Math.max(1, (sourceButtonLimitY - y - 22) / 30);
        int sourcePageCount = Math.max(1, (categoryButtons.size() + visibleSourceRows - 1) / visibleSourceRows);
        this.sourcePage = Math.max(0, Math.min(this.sourcePage, sourcePageCount - 1));
        if (sourcePageCount > 1) {
            final int pageCountForButtons = sourcePageCount;
            this.addButton(this.leftX + 10, y, (this.leftW - 24) / 2, 18, "‹ Page", () -> {
                this.sourcePage = Math.max(0, this.sourcePage - 1);
                this.rebuildStudio();
            });
            this.addButton(this.leftX + 10 + (this.leftW - 24) / 2, y, (this.leftW - 24) / 2, 18, "Page ›", () -> {
                this.sourcePage = Math.min(pageCountForButtons - 1, this.sourcePage + 1);
                this.rebuildStudio();
            });
            y += 22;
            visibleSourceRows = Math.max(1, (sourceButtonLimitY - y) / 30);
            sourcePageCount = Math.max(1, (categoryButtons.size() + visibleSourceRows - 1) / visibleSourceRows);
            this.sourcePage = Math.max(0, Math.min(this.sourcePage, sourcePageCount - 1));
        }
        final int sourceStart = this.sourcePage * visibleSourceRows;
        final int sourceEnd = Math.min(categoryButtons.size(), sourceStart + visibleSourceRows);
        for (int i = sourceStart; i < sourceEnd; ++i) {
            final StudioButton entry = categoryButtons.get(i);
            if (entry.secondaryLabel().isEmpty()) {
                this.addButton(this.leftX + 10, y, this.leftW - 20, 20, this.truncateLabel(entry.primaryLabel(), this.leftW - 28), entry.action());
                y += 24;
            } else {
                this.addSourceWidgetButton(this.leftX + 10, y, this.leftW - 20, 26, entry, entry.action());
                y += 30;
            }
        }
        this.addedWidgetsY = y + 14;

        final StressometerHudState.PartWidget selectedWidget = this.draftState.getSelectedWidget();
        y = this.inspectorActionStartY(selectedWidget);
        if (selectedWidget != null) {
            final int halfBtnW = (this.rightW - 22) / 2;
            this.addButton(this.rightX + 10, y, halfBtnW, 18, "Duplicate", () -> {
                this.setNextActionLabel("Duplicate");
                this.draftState.duplicateWidget(selectedWidget.getId(), 8, 8, this.editWidth(), this.editHeight());
                this.rebuildStudio();
                this.setStatus("Duplicated");
            });
            this.addButton(this.rightX + 10 + halfBtnW + 2, y, this.rightW - 22 - halfBtnW, 18, "Remove", () -> {
                this.setNextActionLabel("Remove widget");
                this.draftState.removeWidget(selectedWidget.getId());
                this.rebuildStudio();
                this.setStatus("Widget removed");
            });
            y = inspectorNextHeader(y);
            this.addButton(this.rightX + 10, y, this.rightW - 20, 18, selectedWidget.hasBackground() ? "[x] Background" : "[ ] Background", () -> {
                selectedWidget.setBackground(!selectedWidget.hasBackground());
                this.rebuildStudio();
            });
            y = inspectorNextSection(y);
            this.addButton(this.rightX + 10, y, this.rightW - 20, 18, selectedWidget.hasTextShadow() ? "[x] Text shadow" : "[ ] Text shadow", () -> {
                selectedWidget.setTextShadow(!selectedWidget.hasTextShadow());
                this.rebuildStudio();
            });
            y = inspectorNextBlock(y);

            final float wScale = selectedWidget.getTextScale();
            final String wScaleLabel = wScale > 0.01f ? String.format(Locale.ROOT, "Font: %.1fx", wScale) : "Font: (global)";
            this.addButton(this.rightX + 10, y, this.rightW - 42, 18, wScaleLabel, () -> {});
            this.addButton(this.rightX + this.rightW - 40, y, 18, 18, "-", () -> {
                selectedWidget.setTextScale(selectedWidget.getTextScale() > 0.01f ? selectedWidget.getTextScale() - 0.1f : this.draftState.getGlobalTextScale() - 0.1f);
                this.rebuildStudio();
            });
            this.addButton(this.rightX + this.rightW - 20, y, 18, 18, "+", () -> {
                selectedWidget.setTextScale(selectedWidget.getTextScale() > 0.01f ? selectedWidget.getTextScale() + 0.1f : this.draftState.getGlobalTextScale() + 0.1f);
                this.rebuildStudio();
            });
            y = inspectorNextRow(y);

            final int wPad = selectedWidget.getPaddingOffset();
            this.addButton(this.rightX + 10, y, this.rightW - 42, 18, "Padding: " + (wPad == 0 ? "(global)" : (wPad > 0 ? "+" : "") + wPad), () -> {});
            this.addButton(this.rightX + this.rightW - 40, y, 18, 18, "-", () -> {
                selectedWidget.setPaddingOffset(selectedWidget.getPaddingOffset() - 1);
                this.rebuildStudio();
            });
            this.addButton(this.rightX + this.rightW - 20, y, 18, 18, "+", () -> {
                selectedWidget.setPaddingOffset(selectedWidget.getPaddingOffset() + 1);
                this.rebuildStudio();
            });

            final java.util.List<String> variants = availableVariantsFor(selectedWidget);
            if (variants.size() > 1) {
                y = inspectorNextRow(y);
                final String currentVariant = selectedWidget.getRenderVariant();
                final String shown = currentVariant == null || currentVariant.isEmpty() ? "classic" : currentVariant;
                final String pretty = Character.toUpperCase(shown.charAt(0)) + shown.substring(1);
                this.addButton(this.rightX + 10, y, this.rightW - 20, 18, "Variant: " + pretty + " ›", () -> {
                    final int idx = Math.max(0, variants.indexOf(shown));
                    final String next = variants.get((idx + 1) % variants.size());
                    selectedWidget.setRenderVariant("classic".equals(next) ? "" : next);
                    this.rebuildStudio();
                });
            }
        }

        final int globalCtrlY = this.canvasY + this.canvasH - 64;
        final float gScale = this.draftState.getGlobalTextScale();
        final int gPad = this.draftState.getGlobalPaddingOffset();
        this.addRenderableWidget(new GlobalFloatSlider(this.rightX + 10, globalCtrlY, this.rightW - 20, 18,
                String.format(Locale.ROOT, "Font Scale: %.2fx", gScale),
                (gScale - 0.5f) / 1.0f, 0.5f, 1.5f,
                v -> {
                    this.draftState.setGlobalTextScale(Math.round(v * 20f) / 20f);
                    this.rebuildStudio();
                }));
        final int globalPadY = globalCtrlY + 22;
        this.addRenderableWidget(new GlobalIntSlider(this.rightX + 10, globalPadY, this.rightW - 20, 18,
                "Padding: " + (gPad >= 0 ? "+" : "") + gPad + " px",
                (gPad - (-4)) / 12.0,
                -4, 8,
                v -> {
                    this.draftState.setGlobalPaddingOffset(v);
                    this.rebuildStudio();
                }));

        this.refreshLabels();

        this.captureBaselineSnapshot();
    }

    private static final int INSPECTOR_ROW_H = 18;
    private static final int INSPECTOR_ROW_GAP = 4;
    private static final int INSPECTOR_SECTION_GAP = 6;
    private static final int INSPECTOR_BLOCK_GAP = 8;
    private static final int INSPECTOR_HEADER_GAP = 10;

    private static int inspectorNextRow(int y) { return y + INSPECTOR_ROW_H + INSPECTOR_ROW_GAP; }
    private static int inspectorNextSection(int y) { return y + INSPECTOR_ROW_H + INSPECTOR_SECTION_GAP; }
    private static int inspectorNextBlock(int y) { return y + INSPECTOR_ROW_H + INSPECTOR_BLOCK_GAP; }
    private static int inspectorNextHeader(int y) { return y + INSPECTOR_ROW_H + INSPECTOR_HEADER_GAP; }

    public void applyPlacementDraft(StressometerHudState updatedState) {
        this.draftState = (updatedState != null) ? updatedState.copy() : this.draftState;
        this.draftHudSettings.setStressometerHud(this.draftState);
        this.draftState = this.draftHudSettings.getStressometerHud();
        this.refreshLabels();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, this.width, this.height, 0xC8241A0E);
        g.fill(0, 0, this.width, this.height, 0x0AD8CAA4);
        this.drawDraftingPanel(g, 8, 8, this.width - 16, this.topBarH, this.BG, false);
        this.drawBrassRailH(g, 9, 8 + this.topBarH - 5, this.width - 18, 4);
        g.drawString(this.font, "HUD Studio", 18, 18, this.ACCENT);

        this.drawPanelShadow(g, this.leftX, this.canvasY, this.leftW, this.canvasH);
        this.drawDraftingPanel(g, this.leftX, this.canvasY, this.leftW, this.canvasH, this.BG, true);
        this.drawSectionHeader(g, "Elements", this.leftX + 8, this.canvasY + 8, this.leftW);
        this.drawDomainChips(g);
        this.drawLeftPanelLabels(g);
        this.drawBrowserEmptyState(g);
        this.drawAddedWidgets(g);

        this.drawPanelShadow(g, this.canvasX, this.canvasY, this.canvasW, this.canvasH);
        this.drawDraftingPanel(g, this.canvasX, this.canvasY, this.canvasW, this.canvasH, 0xA8000000, false);
        this.drawCanvas(g, mouseX, mouseY);

        this.drawPanelShadow(g, this.rightX, this.canvasY, this.rightW, this.canvasH);
        this.drawDraftingPanel(g, this.rightX, this.canvasY, this.rightW, this.canvasH, this.BG, true);
        this.drawSectionHeader(g, "Inspector", this.rightX + 8, this.canvasY + 8, this.rightW);
        this.drawInspector(g);

        this.drawDraftingPanel(g, 8, this.height - this.bottomBarH - 4, this.width - 16, this.bottomBarH - 4, 0xA67A6443, false);
        this.drawBrassRailH(g, 9, this.height - this.bottomBarH - 5, this.width - 18, 3);
        final String tip = this.isCanvasEditable()
                ? (this.snapToGrid ? "Snap-to-grid ON (G to toggle). Ctrl+Z undo, Ctrl+Y redo. Drag handles to resize from any side."
                                   : "Click to select, drag to move, drag handles to resize. G snap-to-grid · Ctrl+Z/Y undo/redo.")
                : "Window is too small for safe placement editing. Enlarge the game window to drag/resize.";
        g.drawString(this.font, tip, 14, this.height - 23, this.DIM);

        if (!this.statusMessage.isEmpty() && this.tickCount < this.statusMessageExpiryTick) {
            final int sw = this.font.width(this.statusMessage);
            g.drawString(this.font, this.statusMessage, this.width - 18 - sw, this.height - 23, 0xFFE9DBB4);
        }
        super.render(g, mouseX, mouseY, partialTick);

        if (this.helpMode) {
            this.drawHelpOverlay(g);
        }

        if (this.isDirty()) {
            final int sx = this.width - 10 - 56 + 56 - 6;
            final int sy = 10 + 7 + 2;
            g.fill(sx, sy, sx + 4, sy + 4, 0xFFFFAA33);
            g.fill(sx - 1, sy - 1, sx + 5, sy, 0x66000000);
        }
        if (this.pendingResetConfirm != null) {
            this.drawResetModal(g, mouseX, mouseY);
        }
        if (this.pendingDiscardConfirm) {
            this.drawDiscardModal(g, mouseX, mouseY);
        }
    }

    private void drawDiscardModal(GuiGraphics g, int mouseX, int mouseY) {
        g.fill(0, 0, this.width, this.height, 0xB0000000);
        final int cardW = Math.min(360, this.width - 80);
        final int cardH = 120;
        final int cardX = (this.width - cardW) / 2;
        final int cardY = (this.height - cardH) / 2;
        this.drawPanelShadow(g, cardX, cardY, cardW, cardH);
        this.drawDraftingPanel(g, cardX, cardY, cardW, cardH, this.BG, true);
        g.drawString(this.font, "Unsaved changes", cardX + 14, cardY + 14, this.ACCENT);
        g.drawString(this.font, "Discard your edits and close the studio?", cardX + 14, cardY + 36, this.TEXT);
        g.drawString(this.font, "Use Cancel to keep editing.", cardX + 14, cardY + 48, this.DIM);
        this.resetModalBtnW = 120;
        this.resetModalBtnH = 20;
        this.resetModalCancelX = cardX + 14;
        this.resetModalCancelY = cardY + cardH - this.resetModalBtnH - 12;
        this.resetModalConfirmX = cardX + cardW - this.resetModalBtnW - 14;
        this.resetModalConfirmY = this.resetModalCancelY;
        drawModalButton(g, this.resetModalCancelX, this.resetModalCancelY, this.resetModalBtnW, this.resetModalBtnH, "Keep editing", mouseX, mouseY, 0xFF3A2E1E);
        drawModalButton(g, this.resetModalConfirmX, this.resetModalConfirmY, this.resetModalBtnW, this.resetModalBtnH, "Discard changes", mouseX, mouseY, 0xFF7A2A1A);
    }

    private void drawHelpOverlay(GuiGraphics g) {
        g.fill(0, 0, this.width, this.height, 0x80000000);
        this.drawHelpLabel(g, 200, 8, 200, 30, "Toolbar: Undo / Redo / Snap / Help");
        this.drawHelpLabel(g, this.leftX, this.canvasY, this.leftW, 30, "Browser, pick widgets to add");
        this.drawHelpLabel(g, this.canvasX, this.canvasY, this.canvasW, 30, "Canvas, drag, resize handles, Ctrl+Z to undo");
        this.drawHelpLabel(g, this.rightX, this.canvasY, this.rightW, 30, "Inspector, tweak the selected widget");
        this.drawHelpLabel(g, 8, this.height - this.bottomBarH - 4, this.width - 16, this.bottomBarH - 4, "Status / shortcut hints");
        g.drawCenteredString(this.font, "Press ? again to close help", this.width / 2, this.height - this.bottomBarH - 18, 0xFFE9DBB4);
    }

    private void drawHelpLabel(GuiGraphics g, int x, int y, int w, int h, String label) {
        g.fill(x, y, x + w, y + h, 0x66D8CAA4);
        g.fill(x, y, x + w, y + 1, 0xFFD8CAA4);
        g.fill(x, y + h - 1, x + w, y + h, 0xFFD8CAA4);
        g.fill(x, y, x + 1, y + h, 0xFFD8CAA4);
        g.fill(x + w - 1, y, x + w, y + h, 0xFFD8CAA4);
        g.drawString(this.font, label, x + 6, y + (h - 8) / 2, 0xFF1A0E04);
    }

    private void drawResetModal(GuiGraphics g, int mouseX, int mouseY) {
        g.fill(0, 0, this.width, this.height, 0xB0000000);
        final int cardW = Math.min(360, this.width - 80);
        final int cardH = 120;
        final int cardX = (this.width - cardW) / 2;
        final int cardY = (this.height - cardH) / 2;
        this.drawPanelShadow(g, cardX, cardY, cardW, cardH);
        this.drawDraftingPanel(g, cardX, cardY, cardW, cardH, this.BG, true);
        g.drawString(this.font, "Reset would clear this layout", cardX + 14, cardY + 14, this.ACCENT);
        g.drawString(this.font, "No source-aware default widgets are", cardX + 14, cardY + 36, this.TEXT);
        g.drawString(this.font, "available for the current scope.", cardX + 14, cardY + 48, this.TEXT);

        this.resetModalBtnW = 120;
        this.resetModalBtnH = 20;
        this.resetModalCancelX = cardX + 14;
        this.resetModalCancelY = cardY + cardH - this.resetModalBtnH - 12;
        this.resetModalConfirmX = cardX + cardW - this.resetModalBtnW - 14;
        this.resetModalConfirmY = this.resetModalCancelY;
        drawModalButton(g, this.resetModalCancelX, this.resetModalCancelY, this.resetModalBtnW, this.resetModalBtnH, "Cancel", mouseX, mouseY, 0xFF3A2E1E);
        drawModalButton(g, this.resetModalConfirmX, this.resetModalConfirmY, this.resetModalBtnW, this.resetModalBtnH, "Reset anyway", mouseX, mouseY, 0xFF7A2A1A);
    }

    private void drawModalButton(GuiGraphics g, int x, int y, int w, int h, String label, int mouseX, int mouseY, int baseColor) {
        final boolean hover = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
        g.fill(x, y, x + w, y + h, hover ? 0xFFAA8055 : baseColor);
        g.fill(x, y, x + w, y + 1, 0xFFD8CAA4);
        g.fill(x, y + h - 1, x + w, y + h, 0xFF1A0E04);
        final int tx = x + (w - this.font.width(label)) / 2;
        g.drawString(this.font, label, tx, y + (h - 8) / 2, 0xFFE9DBB4);
    }

    private boolean isInsideResetModalButton(double mouseX, double mouseY, int btnX, int btnY) {
        return mouseX >= btnX && mouseX < btnX + this.resetModalBtnW
                && mouseY >= btnY && mouseY < btnY + this.resetModalBtnH;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.pendingResetConfirm != null) {
            if (button == 0) {
                if (this.isInsideResetModalButton(mouseX, mouseY, this.resetModalConfirmX, this.resetModalConfirmY)) {
                    this.applyReset(this.pendingResetConfirm);
                } else if (this.isInsideResetModalButton(mouseX, mouseY, this.resetModalCancelX, this.resetModalCancelY)) {
                    this.cancelReset();
                }
            }
            return true;
        }

        if (this.pendingDiscardConfirm) {
            if (button == 0) {
                if (this.isInsideResetModalButton(mouseX, mouseY, this.resetModalConfirmX, this.resetModalConfirmY)) {
                    this.pendingDiscardConfirm = false;
                    Minecraft.getInstance().setScreen(this.parentScreen);
                } else if (this.isInsideResetModalButton(mouseX, mouseY, this.resetModalCancelX, this.resetModalCancelY)) {
                    this.pendingDiscardConfirm = false;
                }
            }
            return true;
        }
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (button != 0) {
            return false;
        }

        final BrowserDomain hitDomain = this.hitDomainChip(mouseX, mouseY);
        if (hitDomain != null && hitDomain != this.selectedDomain) {
            this.selectedDomain = hitDomain;
            this.categoryPage = 0;
            this.sourcePage = 0;
            this.setStatus("Domain: " + hitDomain.label);
            this.rebuildStudio();
            return true;
        }

        final StressometerHudState.PartWidget chipSel = this.draftState.getSelectedWidget();
        if (chipSel != null) {
            final String chipHit = this.hitQuickActionChip(mouseX, mouseY);
            if (chipHit != null) {
                switch (chipHit) {
                    case "duplicate" -> {
                        this.setNextActionLabel("Duplicate");
                        this.draftState.duplicateWidget(chipSel.getId(), 8, 8, this.editWidth(), this.editHeight());
                        this.rebuildStudio();
                        this.setStatus("Duplicated");
                    }
                    case "delete" -> {
                        this.setNextActionLabel("Remove widget");
                        this.draftState.removeWidget(chipSel.getId());
                        this.rebuildStudio();
                        this.setStatus("Widget removed");
                    }
                    case "variant" -> {
                        final java.util.List<String> variants = availableVariantsFor(chipSel);
                        if (variants.size() > 1) {
                            final String currentVariant = chipSel.getRenderVariant();
                            final String shown = currentVariant == null || currentVariant.isEmpty() ? "classic" : currentVariant;
                            final int idx = Math.max(0, variants.indexOf(shown));
                            final String next = variants.get((idx + 1) % variants.size());
                            chipSel.setRenderVariant("classic".equals(next) ? "" : next);
                            this.setNextActionLabel("Variant change");
                            this.rebuildStudio();
                            this.setStatus("Variant: " + next);
                        }
                    }
                }
                return true;
            }
        }
        if (!this.isCanvasEditable()) {
            return false;
        }
        final int localX = this.toScreenX((int) mouseX - this.viewportX);
        final int localY = this.toScreenY((int) mouseY - this.viewportY);
        final StressometerHudState.PartWidget selectedWidget = this.draftState.getSelectedWidget();
        final StressometerHudState.Bounds.ResolvedBounds selectedBounds = selectedWidget != null ? selectedWidget.resolveBounds(this.editWidth(), this.editHeight(), this.draftState) : null;

        if (selectedWidget != null && selectedBounds != null) {
            final DragHandle h = this.hitHandle(localX, localY, selectedBounds);
            if (h != DragHandle.NONE) {
                this.activeHandle = h;
                this.resizing = true;
                this.resizeStartBounds = selectedBounds;
                this.dragStartMouseX = localX;
                this.dragStartMouseY = localY;
                this.dragStartBoundsX = selectedBounds.x();
                this.dragStartBoundsY = selectedBounds.y();
                this.dragStartBoundsW = selectedBounds.width();
                this.dragStartBoundsH = selectedBounds.height();
                this.beginUndoGesture("Resize");
                return true;
            }
        }
        for (int i = this.draftState.getWidgets().size() - 1; i >= 0; --i) {
            final StressometerHudState.PartWidget widget = this.draftState.getWidgets().get(i);
            final StressometerHudState.Bounds.ResolvedBounds bounds = widget.resolveBounds(this.editWidth(), this.editHeight(), this.draftState);
            if (this.isInsideBounds(localX, localY, bounds)) {
                this.draftState.setSelectedWidgetId(widget.getId());

                this.dragArmed = true;
                this.dragging = false;
                this.dragOffsetX = localX - bounds.x();
                this.dragOffsetY = localY - bounds.y();
                this.dragStartMouseX = localX;
                this.dragStartMouseY = localY;
                this.dragStartBoundsX = bounds.x();
                this.dragStartBoundsY = bounds.y();
                this.dragStartBoundsW = bounds.width();
                this.dragStartBoundsH = bounds.height();
                this.beginUndoGesture("Move");
                this.rebuildStudio();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.pendingResetConfirm != null || this.pendingDiscardConfirm) {
            return true;
        }
        final int localX = this.toScreenX((int) mouseX - this.viewportX);
        final int localY = this.toScreenY((int) mouseY - this.viewportY);
        if (!this.isCanvasEditable()) {
            return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
        }
        // 3px before a drag counts
        if (this.dragArmed && !this.dragging) {
            final int dx = localX - this.dragStartMouseX;
            final int dy = localY - this.dragStartMouseY;
            if (dx * dx + dy * dy >= DRAG_THRESHOLD_SQ) {
                this.dragging = true;
                this.dragArmed = false;
            } else {
                return true;
            }
        }
        if (this.dragging) {
            final StressometerHudState.PartWidget widget = this.draftState.getSelectedWidget();
            if (widget == null) {
                return false;
            }
            final StressometerHudState.Bounds.ResolvedBounds bounds = widget.resolveBounds(this.editWidth(), this.editHeight(), this.draftState);

            final int newX = this.snap(this.dragStartBoundsX + (localX - this.dragStartMouseX));
            final int newY = this.snap(this.dragStartBoundsY + (localY - this.dragStartMouseY));
            widget.setBoundsFromPixels(newX, newY, bounds.width(), bounds.height(), this.editWidth(), this.editHeight());
            return true;
        }
        if (this.resizing && this.resizeStartBounds != null) {
            this.applyHandleResize(this.activeHandle, localX, localY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (this.pendingResetConfirm != null || this.pendingDiscardConfirm) {
            return true;
        }
        final boolean wasInteracting = this.dragging || this.resizing;
        this.dragging = false;
        this.dragArmed = false;
        this.resizing = false;
        this.resizeStartBounds = null;
        this.activeHandle = DragHandle.NONE;
        if (wasInteracting) {
            this.commitUndoGesture();
        } else {
            this.cancelUndoGesture();
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.pendingResetConfirm != null) {
            if (keyCode == 256) {
                this.cancelReset();
            } else if (keyCode == 257 || keyCode == 335) {
                this.applyReset(this.pendingResetConfirm);
            }
            return true;
        }
        if (this.pendingDiscardConfirm) {
            if (keyCode == 256) {
                this.pendingDiscardConfirm = false;
            } else if (keyCode == 257 || keyCode == 335) {
                this.pendingDiscardConfirm = false;
                Minecraft.getInstance().setScreen(this.parentScreen);
            }
            return true;
        }

        final boolean ctrlMod = (modifiers & 2) != 0;
        final boolean shiftMod = (modifiers & 1) != 0;
        if (ctrlMod && keyCode == 90) {
            if (shiftMod) this.redo(); else this.undo();
            return true;
        }
        if (ctrlMod && keyCode == 89) {
            this.redo();
            return true;
        }

        if (keyCode == 256) {
            if (this.draftState.getSelectedWidgetId() != null && !this.draftState.getSelectedWidgetId().isEmpty()) {
                this.draftState.setSelectedWidgetId("");
                this.rebuildStudio();
                return true;
            }
            this.cancel();
            return true;
        }

        if (this.isCanvasEditable()) {
            final StressometerHudState.PartWidget sel = this.draftState.getSelectedWidget();
            if (sel != null) {
                final boolean ctrl = ctrlMod;
                final boolean shift = shiftMod;
                final int step = shift ? 8 : 1;
                int dx = 0, dy = 0;
                if (keyCode == 263) dx = -step;
                else if (keyCode == 262) dx = step;
                else if (keyCode == 265) dy = -step;
                else if (keyCode == 264) dy = step;
                if (dx != 0 || dy != 0) {
                    this.beginUndoGesture(shift ? "Nudge ×8" : "Nudge");
                    final StressometerHudState.Bounds.ResolvedBounds b = sel.resolveBounds(this.editWidth(), this.editHeight(), this.draftState);
                    sel.setBoundsFromPixels(b.x() + dx, b.y() + dy, b.width(), b.height(), this.editWidth(), this.editHeight());
                    this.commitUndoGesture();
                    this.rebuildStudio();
                    return true;
                }
                if (keyCode == 261 || keyCode == 259) {
                    this.setNextActionLabel("Remove widget");
                    this.draftState.removeWidget(sel.getId());
                    this.rebuildStudio();
                    this.setStatus("Widget removed");
                    return true;
                }
                if (ctrl && keyCode == 68) {
                    this.setNextActionLabel("Duplicate");
                    this.draftState.duplicateWidget(sel.getId(), 8, 8, this.editWidth(), this.editHeight());
                    this.rebuildStudio();
                    this.setStatus("Duplicated");
                    return true;
                }
            }

            if (keyCode == 71 && !ctrlMod) {
                this.snapToGrid = !this.snapToGrid;
                this.setStatus(this.snapToGrid ? "Snap-to-grid: on" : "Snap-to-grid: off");
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        this.cancel();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        this.tickCount++;
    }

    private static void addTooltip(Button b, String text) {
        if (b != null && text != null && !text.isEmpty()) {
            b.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(text)));
        }
    }

    private Button addButton(int x, int y, int w, int h, String label, Runnable action) {
        return this.addRenderableWidget(new StudioChromeButton(x, y, w, h, Component.literal(label), b -> action.run()));
    }

    private static java.util.List<String> availableVariantsFor(StressometerHudState.PartWidget widget) {
        if (widget == null) return java.util.List.of();

        if (widget.getPart() == StressometerHudState.Part.LOAD_GAUGE) {
            final String src = widget.getSourceType();
            if (src == null || src.isEmpty() || "Stressometer".equals(src)) {
                return java.util.List.of("classic", "reactor");
            }
        }

        if (("Stockpile Switch".equals(widget.getSourceType()))
                && (widget.getPart() == StressometerHudState.Part.STOCKPILE_GAUGE
                 || widget.getPart() == StressometerHudState.Part.STOCKPILE_OUTPUT)) {
            return java.util.List.of("classic", "lamp");
        }

        if (widget.getPart() == StressometerHudState.Part.FLUID_LEVEL
         || widget.getPart() == StressometerHudState.Part.BOILER_WATER
         || widget.getPart() == StressometerHudState.Part.ENGINE_FUEL) {
            return java.util.List.of("classic", "droplet");
        }

        if (widget.getPart() == StressometerHudState.Part.BOILER_HEAT) {
            return java.util.List.of("classic", "flame");
        }

        if (widget.getPart() == StressometerHudState.Part.VELOCITY_AXIS
         || widget.getPart() == StressometerHudState.Part.AERO_DRIVE) {
            return java.util.List.of("classic", "streak");
        }

        if (widget.getPart() == StressometerHudState.Part.INBOUND_TRAINS) {
            return java.util.List.of("classic", "marquee");
        }

        if (widget.getPart() == StressometerHudState.Part.AERO_HORIZON) {
            return java.util.List.of("classic", "attitude_ladder");
        }

        if (widget.getPart() == StressometerHudState.Part.PRODUCTION_RATE) {
            return java.util.List.of("classic", "pulse");
        }

        if (widget.getPart() == StressometerHudState.Part.FACTORY_SHORTFALL) {
            return java.util.List.of("classic", "bottleneck");
        }

        if (widget.getPart() == StressometerHudState.Part.AERO_ORIENTATION) {
            return java.util.List.of("classic", "compass");
        }

        if (widget.getPart() == StressometerHudState.Part.DOCKING_PAIR) {
            return java.util.List.of("classic", "proximity");
        }

        if (widget.getPart() == StressometerHudState.Part.TRAIN_STATION_STATUS) {
            return java.util.List.of("classic", "signalbox");
        }
        return java.util.List.of();
    }

    private Button addSourceWidgetButton(int x, int y, int w, int h, StudioButton entry, Runnable action) {
        return this.addRenderableWidget(new SourceWidgetRowButton(x, y, w, h, entry.primaryLabel(), entry.secondaryLabel(), b -> action.run()));
    }

    private void addPresetButton(int x, int y, String label, StressometerHudState.LayoutPreset preset) {
        final int w = (this.leftW - 24) / 2;
        final Button button = this.addButton(x, y, w, 20, label, () -> {
            this.applySourceAwarePreset(preset);
            this.rebuildStudio();
        });
        button.active = this.activePresetSource() != null;
    }

    private void applySourceAwarePreset(StressometerHudState.LayoutPreset preset) {
        final SourceConfig source = this.activePresetSource();
        if (source == null) {
            return;
        }
        final List<SpecializedHudWidgetCatalog.Definition> definitions = SpecializedHudWidgetCatalog.definitionsFor(source.getSourceType());
        if (definitions.isEmpty()) {
            return;
        }
        if ("Stressometer".equals(source.getSourceType())) {
            this.draftState.bindToSource(source);
        }
        final int wanted = Math.min(definitions.size(), switch (preset) {
            case FULL_PANEL, FACTORY_FLOOR, SHIP_BRIDGE, PILOT_HUD -> definitions.size();
            case CARD_STACK, TRAIN_DISPATCH -> Math.min(6, definitions.size());
            case COMPACT_ROW -> Math.min(4, definitions.size());
            case MINIMAL -> Math.min(2, definitions.size());
        });
        final ArrayList<StressometerHudState.PartWidget> widgets = new ArrayList<StressometerHudState.PartWidget>();
        for (int i = 0; i < wanted; ++i) {
            final SpecializedHudWidgetCatalog.Definition definition = definitions.get(i);
            final StressometerHudState.PartWidget widget = StressometerHudState.PartWidget.createSpecialized(source, definition.part(), definition, i);

            applyCuratedVariant(widget, preset);
            this.placePresetWidget(widget, preset, i, wanted);
            widgets.add(widget);
        }
        this.draftState.replaceWidgets(widgets);
        if (!widgets.isEmpty()) {
            this.recordAddedWidget(widgets.get(0).getId());
        }
    }

    private static void applyCuratedVariant(StressometerHudState.PartWidget widget, StressometerHudState.LayoutPreset preset) {
        if (widget == null || preset == null) return;
        final StressometerHudState.Part part = widget.getPart();
        final String variant = switch (preset) {
            case FACTORY_FLOOR -> switch (part) {
                case PRODUCTION_RATE -> "pulse";
                case FACTORY_SHORTFALL -> "bottleneck";
                case LOAD_GAUGE -> "reactor";
                case BOILER_HEAT -> "flame";
                case BOILER_WATER, ENGINE_FUEL, FLUID_LEVEL -> "droplet";
                default -> null;
            };
            case SHIP_BRIDGE, PILOT_HUD -> switch (part) {
                case AERO_HORIZON -> "attitude_ladder";
                case AERO_ORIENTATION -> "compass";
                case DOCKING_PAIR -> "proximity";
                case VELOCITY_AXIS, AERO_DRIVE -> "streak";
                default -> null;
            };
            case TRAIN_DISPATCH -> switch (part) {
                case TRAIN_STATION_STATUS -> "signalbox";
                case INBOUND_TRAINS -> "marquee";
                default -> null;
            };
            default -> null;
        };
        if (variant != null) {
            widget.setRenderVariant(variant);
        }
    }

    private SourceConfig activePresetSource() {
        final List<SourceConfig> sources = this.getHudSources();
        if (sources.isEmpty()) {
            return null;
        }
        final List<StressometerHudState.SourceCategory> categories = this.getVisibleCategories();
        final StressometerHudState.SourceCategory category = categories.isEmpty()
                ? StressometerHudState.SourceCategory.forSourceType(sources.get(0).getSourceType())
                : categories.get(Math.max(0, Math.min(this.categoryPage, categories.size() - 1)));
        final StressometerHudState.PartWidget selected = this.draftState.getSelectedWidget();
        if (selected != null && !selected.getSourceId().isEmpty()) {
            final SourceConfig selectedSource = this.panel.getSourceRegistry().get(selected.getSourceId());
            if (selectedSource != null
                    && this.isSourceInScope(selectedSource)
                    && StressometerHudState.SourceCategory.forSourceType(selectedSource.getSourceType()) == category) {
                return selectedSource;
            }
        }
        for (SourceConfig source : sources) {
            if (StressometerHudState.SourceCategory.forSourceType(source.getSourceType()) == category) {
                return source;
            }
        }
        return sources.get(0);
    }

    private void placePresetWidget(StressometerHudState.PartWidget widget, StressometerHudState.LayoutPreset preset, int index, int total) {
        final int canvas = 1000;
        final int gap = switch (preset) {
            case FULL_PANEL, FACTORY_FLOOR, SHIP_BRIDGE, PILOT_HUD -> 24;
            case CARD_STACK, TRAIN_DISPATCH -> 26;
            case COMPACT_ROW, MINIMAL -> 18;
        };
        final int cols = switch (preset) {
            case FULL_PANEL, FACTORY_FLOOR, SHIP_BRIDGE, PILOT_HUD -> total <= 3 ? 1 : 2;
            case CARD_STACK, TRAIN_DISPATCH -> total <= 2 ? 1 : 2;
            case COMPACT_ROW -> Math.max(1, Math.min(4, total));
            case MINIMAL -> Math.max(1, Math.min(2, total));
        };
        final int rows = Math.max(1, (total + cols - 1) / cols);
        final int col = index % cols;
        final int row = index / cols;
        final int xMargin = preset == StressometerHudState.LayoutPreset.COMPACT_ROW ? 25 : 40;
        final int yMargin = preset == StressometerHudState.LayoutPreset.COMPACT_ROW ? 34 : 36;
        final int usableW = canvas - xMargin * 2 - gap * (cols - 1);
        final int usableH = canvas - yMargin * 2 - gap * (rows - 1);
        final int w = Math.max(widget.getPart().defaultWidth(), usableW / cols);
        final int h = switch (preset) {
            case FULL_PANEL, FACTORY_FLOOR, SHIP_BRIDGE, PILOT_HUD -> Math.max(widget.getPart().defaultHeight(), Math.min(185, usableH / rows));
            case CARD_STACK, TRAIN_DISPATCH -> Math.max(widget.getPart().defaultHeight(), Math.min(150, usableH / rows));
            case COMPACT_ROW -> Math.max(widget.getPart().minHeight(), Math.min(82, usableH / rows));
            case MINIMAL -> Math.max(widget.getPart().minHeight(), Math.min(88, usableH / rows));
        };
        final int x = xMargin + col * (w + gap);
        final int y = yMargin + row * (h + gap);
        widget.setBoundsFromPixels(x, y, w, h, canvas, canvas);
    }

    private void rebuildStudio() {
        this.maybeCaptureUndoFromRebuild();
        this.clearWidgets();
        this.init();
    }

    // empty reset asks first instead of wiping the canvas
    private void requestReset() {
        final StressometerHudState prepared = this.buildSourceAwareReset();
        this.removeWidgetsOutsideStudioScope(prepared);
        if (prepared.getWidgets().isEmpty()) {
            this.dragging = false;
            this.dragArmed = false;
            this.resizing = false;
            this.pendingResetConfirm = prepared;
            return;
        }
        this.applyReset(prepared);
    }

    private StressometerHudState buildSourceAwareReset() {
        final boolean enabled = this.draftState.isEnabled();
        final StressometerHudState.SourceBindingMode bindingMode = this.draftState.getBindingMode();
        final String sourceType = this.draftState.getBoundSourceType();
        final long sourcePos = this.draftState.getBoundSourcePos();
        final StressometerHudState reset = new StressometerHudState();
        reset.setEnabled(enabled);
        reset.setBindingMode(bindingMode);
        reset.setBoundSourceType(sourceType);
        reset.setBoundSourcePos(sourcePos);
        if (this.isCanvasEditable()) {
            reset.resetBoundsToDefault(this.editWidth(), this.editHeight());
        } else {
            reset.setBounds(this.draftState.getBounds());
        }

        final boolean hasStressometer = !this.getStressometerSources().isEmpty();
        if (!hasStressometer) {
            reset.clearWidgets();
        }

        int seeded = reset.getWidgets().size();
        for (SourceConfig source : this.getHudSources()) {
            if (seeded >= 4) break;
            if (source == null) continue;
            if (hasStressometer && this.isStressometerSource(source)) continue;
            if (reset.addSourceCard(source) != null) {
                seeded++;
            }
        }

        if (!reset.getWidgets().isEmpty()) {
            reset.setSelectedWidgetId(reset.getWidgets().get(0).getId());
        } else {
            reset.setSelectedWidgetId("");
        }
        return reset;
    }

    private boolean isStressometerSource(SourceConfig source) {
        if (source == null) return false;
        for (SourceConfig s : this.getStressometerSources()) {
            if (s == source) return true;
            if (s != null && s.getSourceId() != null && s.getSourceId().equals(source.getSourceId())) {
                return true;
            }
        }
        return false;
    }

    private void applyReset(StressometerHudState prepared) {
        this.draftState = prepared;
        this.draftHudSettings.setStressometerHud(this.draftState);
        this.removeWidgetsOutsideStudioScope();
        this.refreshLabels();
        this.pendingResetConfirm = null;
        this.rebuildStudio();
    }

    private void cancelReset() {
        this.pendingResetConfirm = null;
    }

    private void removeWidgetsOutsideStudioScope() {
        this.removeWidgetsOutsideStudioScope(this.draftState);
    }

    private void removeWidgetsOutsideStudioScope(StressometerHudState state) {
        if (state == null || !this.hasStudioScope()) {
            return;
        }
        final ArrayList<String> removeIds = new ArrayList<String>();
        for (StressometerHudState.PartWidget widget : state.getWidgets()) {
            if (!this.isWidgetInStudioScope(widget)) {
                removeIds.add(widget.getId());
            }
        }
        for (String widgetId : removeIds) {
            state.removeWidget(widgetId);
        }
        if (state.getWidgets().isEmpty()) {
            state.clearWidgets();
        }
    }

    private void addToggleButton(int x, int y, String label, BooleanGetter getter, BooleanSetter setter) {
        final Button button = this.addRenderableWidget(Button.builder(Component.literal(""), b -> {
            setter.set(!getter.get());
            this.refreshLabels();
        }).bounds(x, y, this.rightW - 16, 16).build());
        this.refreshButtons.add(button);
        button.setMessage(Component.literal(this.toggleLabel(label, getter.get())));
    }

    private void refreshLabels() {
        if (this.enableButton != null) {
            this.enableButton.setMessage(Component.literal(this.draftState.isEnabled() ? "HUD: Enabled" : "HUD: Disabled"));
        }
        if (this.sourceButton != null) {
            this.sourceButton.setMessage(Component.literal(this.buildSourceLabel()));
            this.sourceButton.active = !this.getStressometerSources().isEmpty();
        }
        for (Button button : this.refreshButtons) {
            final String plain = button.getMessage().getString();
            final String label = plain.length() > 4 ? plain.substring(4) : plain;
            button.setMessage(Component.literal(this.refreshToggleLabel(label)));
        }
    }

    private String refreshToggleLabel(String label) {
        return switch (label) {
            case "Header" -> this.toggleLabel(label, this.draftState.isHeaderVisible());
            case "Source label" -> this.toggleLabel(label, this.draftState.isNetworkNameVisible());
            case "Load bar" -> this.toggleLabel(label, this.draftState.isLoadBarVisible());
            case "Stress used" -> this.toggleLabel(label, this.draftState.isStressUsedVisible());
            case "Capacity" -> this.toggleLabel(label, this.draftState.isStressCapacityVisible());
            case "Load percent" -> this.toggleLabel(label, this.draftState.isStressPercentVisible());
            case "Status" -> this.toggleLabel(label, this.draftState.isStatusVisible());
            case "Overstress alert" -> this.toggleLabel(label, this.draftState.isWarningVisible());
            case "RPM" -> this.toggleLabel(label, this.draftState.isRpmVisible());
            case "Remaining SU" -> this.toggleLabel(label, this.draftState.isRemainingStressVisible());
            case "Background" -> this.toggleLabel(label, this.draftState.isBackgroundVisible());
            case "Text shadow" -> this.toggleLabel(label, this.draftState.hasTextShadow());
            case "Compact" -> this.toggleLabel(label, this.draftState.isCompact());
            default -> label;
        };
    }

    private String toggleLabel(String label, boolean enabled) {
        return (enabled ? "[x] " : "[ ] ") + label;
    }

    private void drawLeftPanelLabels(GuiGraphics g) {
        if (!this.hasTopologyControls()) {
            // don't repeat it below, that was a duplicate line

            g.drawString(this.font, this.truncateLabel(this.scopeSummary(), this.leftW - 20), this.leftX + 10, this.canvasY + 56, this.DIM);
        }

        this.drawSectionHeader(g, "Presets", this.leftX + 10, this.presetsY - 16, this.leftW);
        this.drawSectionHeader(g, "Widget groups", this.leftX + 10, this.addWidgetsStartY() - 18, this.leftW);
        if (this.sourceWidgetsY > 0) {
            this.drawSectionHeader(g, this.currentCategoryLabel(), this.leftX + 10, this.sourceWidgetsY - 16, this.leftW);
        }
    }

    private int addWidgetsStartY() {
        return (this.presetsY > 0 ? this.presetsY : this.canvasY + 104) + 124;
    }

    private void drawBrowserEmptyState(GuiGraphics g) {
        final List<StressometerHudState.SourceCategory> categories = this.getVisibleCategories();
        final int y = Math.max(this.sourceWidgetsY + 2, this.addWidgetsStartY() + 8);
        if (categories.isEmpty()) {
            g.drawString(this.font, "No selected HUD source", this.leftX + 12, y, this.TEXT);
            g.drawString(this.font, this.truncateLabel("Select sources on the Display Panel home page.", this.leftW - 24), this.leftX + 12, y + 13, this.DIM);
            return;
        }
        final StressometerHudState.SourceCategory category = categories.get(Math.max(0, Math.min(this.categoryPage, categories.size() - 1)));
        if (!this.buildCategoryButtons(category).isEmpty()) {
            return;
        }
        g.drawString(this.font, this.truncateLabel("No widgets for " + category.label(), this.leftW - 24), this.leftX + 12, y, this.TEXT);
        g.drawString(this.font, "Try another bound source group.", this.leftX + 12, y + 13, this.DIM);
    }

    private void drawAddedWidgets(GuiGraphics g) {
        int y = this.addedWidgetsY > 0 ? this.addedWidgetsY : this.addWidgetsStartY() + StressometerHudState.Part.values().length * 22 + 16;
        if (y > this.canvasY + this.canvasH - 20) {
            return;
        }
        g.drawString(this.font, "On canvas", this.leftX + 10, y, this.DIM);
        y += 14;
        for (StressometerHudState.PartWidget widget : this.draftState.getWidgets()) {
            if (y + 16 > this.canvasY + this.canvasH - 8) {
                g.drawString(this.font, "...", this.leftX + 14, y, this.DIM);
                break;
            }
            final boolean selected = widget.getId().equals(this.draftState.getSelectedWidgetId());
            final int selectedAlpha = 0x30 + Math.round(this.pulse01(40.0f, 0.0f) * 0x28);
            final String context = this.widgetSourceContext(widget);
            final int rowH = context.isEmpty() ? 15 : 25;
            g.fill(this.leftX + 10, y, this.leftX + this.leftW - 10, y + rowH, selected ? this.withAlpha(RAIL_BRIGHT, selectedAlpha) : 0x18000000);
            if (widget.getId().equals(this.lastAddedWidgetId)) {
                final long age = this.tickCount - this.lastAddedTick;
                if (age >= 0L && age < 14L) {
                    final float fade = 1.0f - age / 14.0f;
                    g.fill(this.leftX + 10, y, this.leftX + this.leftW - 10, y + rowH, this.withAlpha(RAIL_BRIGHT, Math.max(0, Math.round(0x88 * fade))));
                }
            }
            this.border(g, this.leftX + 10, y, this.leftW - 20, rowH, selected ? RAIL_MID : 0x2F6C694D);
            g.drawString(this.font, this.truncateLabel(this.widgetPrimaryName(widget), this.leftW - 30), this.leftX + 14, y + 2, selected ? RAIL_BRIGHT : this.DIM);
            if (!context.isEmpty() && y + 27 <= this.canvasY + this.canvasH - 8) {
                g.drawString(this.font, this.truncateLabel(context, this.leftW - 30), this.leftX + 14, y + 12, this.DIM);
                y += 28;
            } else {
                y += 18;
            }
        }
    }

    private void drawInspector(GuiGraphics g) {
        final StressometerHudState.PartWidget selected = this.draftState.getSelectedWidget();
        if (selected == null) {
            g.drawString(this.font, "No widget selected", this.rightX + 10, this.canvasY + 28, this.DIM);
            g.drawString(this.font, "Add one from the left browser.", this.rightX + 10, this.canvasY + 42, this.DIM);
            return;
        }
        final int left = this.rightX + 10;
        g.drawString(this.font, this.truncateLabel(this.widgetDisplayName(selected), this.rightW - 20), left, this.canvasY + 26, this.TEXT);
        g.drawString(this.font, "Group", left, this.canvasY + 44, this.DIM);
        g.drawString(this.font, selected.getSourceCategory().label(), left + 42, this.canvasY + 44, this.TEXT);
        int infoY = this.canvasY + 62;
        if (!selected.getSourceType().isEmpty()) {
            g.drawString(this.font, "Source", left, infoY, this.DIM);
            g.drawString(this.font, this.truncateLabel(selected.getSourceType(), this.rightW - 70), left + 42, infoY, this.TEXT);
            infoY += 14;
        }
        g.drawString(this.font, "Size", left, infoY, this.DIM);
        g.drawString(this.font, "Min " + selected.getPart().minWidth() + " x " + selected.getPart().minHeight(), left + 42, infoY, this.TEXT);
        final int actionY = this.inspectorActionStartY(selected);
        g.fill(left, actionY - 8, this.rightX + this.rightW - 10, actionY - 7, this.withAlpha(RAIL_MID, 90));
        g.drawString(this.font, "Actions", left, actionY - 18, this.DIM);
        g.drawString(this.font, "Appearance", left, actionY + 34, this.DIM);
        g.drawString(this.font, "Style", left, actionY + 82, this.DIM);

        g.fill(left, actionY + 130, this.rightX + this.rightW - 10, actionY + 131, this.withAlpha(RAIL_MID, 70));
        g.drawString(this.font, "Canvas", left, actionY + 138, this.DIM);
        g.drawString(this.font, "Drag to place. Pull the lower-right corner to resize.", left, actionY + 152, this.DIM);

        final int globalLabelY = this.canvasY + this.canvasH - 76;
        g.fill(left, globalLabelY - 2, this.rightX + this.rightW - 10, globalLabelY - 1, this.withAlpha(RAIL_MID, 70));
        g.drawString(this.font, "Global style", left, globalLabelY + 5, this.DIM);
    }

    private void drawCanvas(GuiGraphics g, int mouseX, int mouseY) {
        this.drawSectionHeader(g, "Screen Preview", this.canvasX + 8, this.canvasY + 8, this.canvasW);
        g.drawString(this.font, "Preview matches overlay proportions", this.canvasX + 92, this.canvasY + 8, this.DIM);
        final int minorGrid = this.withAlpha(GRID_MINOR, 0x14 + Math.round(this.pulse01(96.0f, 0.0f) * 0x06));
        final int majorGrid = this.withAlpha(GRID_MAJOR, 0x30 + Math.round(this.pulse01(120.0f, 0.35f) * 0x0A));
        for (int gx = this.canvasX + 16; gx < this.canvasX + this.canvasW - 8; gx += 12) {
            g.fill(gx, this.canvasY + 28, gx + 1, this.canvasY + this.canvasH - 8, minorGrid);
        }
        for (int gy = this.canvasY + 32; gy < this.canvasY + this.canvasH - 8; gy += 12) {
            g.fill(this.canvasX + 8, gy, this.canvasX + this.canvasW - 8, gy + 1, minorGrid);
        }
        for (int gx = this.canvasX + 16; gx < this.canvasX + this.canvasW - 8; gx += 48) {
            g.fill(gx, this.canvasY + 28, gx + 1, this.canvasY + this.canvasH - 8, majorGrid);
        }
        for (int gy = this.canvasY + 32; gy < this.canvasY + this.canvasH - 8; gy += 48) {
            g.fill(this.canvasX + 8, gy, this.canvasX + this.canvasW - 8, gy + 1, majorGrid);
        }

        g.fill(this.viewportX, this.viewportY, this.viewportX + this.viewportW, this.viewportY + this.viewportH, 0x77090B13);
        this.drawViewportFrame(g);

        final boolean editable = this.isCanvasEditable();
        if (this.viewportW > 0 && this.viewportH > 0) {
            final float pScale = this.previewScale();
            g.enableScissor(this.viewportX, this.viewportY, this.viewportX + this.viewportW, this.viewportY + this.viewportH);
            g.pose().pushPose();
            g.pose().translate(this.viewportX, this.viewportY, 0);
            g.pose().scale(pScale, pScale, 1.0f);
            StressometerHudWidgetRenderer.renderLayoutPreview(g, this.panel, this.draftState, 0, 0, this.editWidth(), this.editHeight(), this.getPreviewTick());
            g.pose().popPose();
            g.disableScissor();
        }
        if (!editable) {
            g.drawCenteredString(this.font, "Enlarge window to edit", this.viewportX + this.viewportW / 2, this.viewportY + this.viewportH / 2 - 5, this.TEXT);
            return;
        }

        final int localX = this.toScreenX(mouseX - this.viewportX);
        final int localY = this.toScreenY(mouseY - this.viewportY);

        final boolean noWidgets = this.draftState.getWidgets().isEmpty();
        final StressometerHudState.PartWidget selectedWidget = this.draftState.getSelectedWidget();
        if (selectedWidget == null) {
            final int cxC = this.viewportX + this.viewportW / 2;
            final int cyC = this.viewportY + this.viewportH / 2;
            if (noWidgets) {
                g.drawCenteredString(this.font, "Empty HUD layout", cxC, cyC - 14, this.TEXT);
                g.drawCenteredString(this.font, "Pick a widget on the left to add it.", cxC, cyC - 2, this.DIM);
                g.drawCenteredString(this.font, "Then drag, resize, or change its variant.", cxC, cyC + 9, this.DIM);
            } else {
                g.drawCenteredString(this.font, "Click a widget to select", cxC, cyC - 5, this.TEXT);
            }
            return;
        }
        final StressometerHudState.Bounds.ResolvedBounds bounds = selectedWidget.resolveBounds(this.editWidth(), this.editHeight(), this.draftState);
        final int vpBX = this.viewportX + this.toViewportX(bounds.x());
        final int vpBY = this.viewportY + this.toViewportX(bounds.y());
        final int vpBW = this.toViewportD(bounds.width());
        final int vpBH = this.toViewportD(bounds.height());
        final int outlineBase = this.isInsideBounds(localX, localY, bounds) ? 0x0033E0A0 : 0x00D6B84A;
        final int outline = this.withAlpha(outlineBase, 0x90 + Math.round(this.pulse01(40.0f, 0.15f) * 0x6F));
        this.border(g, vpBX - 1, vpBY - 1, vpBW + 2, vpBH + 2, outline);

        final DragHandle hovered = this.hitHandle(localX, localY, bounds);
        this.drawHandle(g, vpBX, vpBY, hovered == DragHandle.NW);
        this.drawHandle(g, vpBX + vpBW - 6, vpBY, hovered == DragHandle.NE);
        this.drawHandle(g, vpBX, vpBY + vpBH - 6, hovered == DragHandle.SW);
        this.drawHandle(g, vpBX + vpBW - 6, vpBY + vpBH - 6, hovered == DragHandle.SE);
        this.drawHandle(g, vpBX + vpBW / 2 - 3, vpBY, hovered == DragHandle.N);
        this.drawHandle(g, vpBX + vpBW / 2 - 3, vpBY + vpBH - 6, hovered == DragHandle.S);
        this.drawHandle(g, vpBX, vpBY + vpBH / 2 - 3, hovered == DragHandle.W);
        this.drawHandle(g, vpBX + vpBW - 6, vpBY + vpBH / 2 - 3, hovered == DragHandle.E);

        this.drawQuickActionChip(g, mouseX, mouseY, vpBX, vpBY, vpBW, vpBH, selectedWidget);
    }

    private void drawHandle(GuiGraphics g, int x, int y, boolean hovered) {
        final int fill = hovered
                ? this.withAlpha(0x00FFFFFF, 0xC0 + Math.round(this.pulse01(20.0f, 0.0f) * 0x3F))
                : 0xCC33E0A0;
        g.fill(x, y, x + 6, y + 6, fill);
        g.fill(x, y, x + 6, y + 1, 0x66000000);
        g.fill(x, y + 5, x + 6, y + 6, 0x66000000);
    }

    private void saveAndClose() {
        this.draftHudSettings.setStressometerHud(this.draftState);
        this.panel.getSettings().setPanelHudSettings(this.draftHudSettings);
        this.panel.setSettings(this.panel.getSettings());
        PacketDistributor.sendToServer(new HudLayoutConfigPacket(this.panel.getBlockPos(), this.draftHudSettings.save()));
        if (this.draftState.isEnabled()) {
            StressometerHudOverlay.pin(this.panel);
        } else if (StressometerHudOverlay.isPinned(this.panel)) {
            StressometerHudOverlay.clear();
        }

        this.savedSnapshot = this.canonicalSnapshot();
        Minecraft.getInstance().setScreen(this.parentScreen);
    }

    private void cancel() {
        if (this.isDirty() && !this.pendingDiscardConfirm) {
            this.pendingResetConfirm = null;
            this.pendingDiscardConfirm = true;
            this.dragging = false;
            this.dragArmed = false;
            this.resizing = false;
            this.cancelUndoGesture();
            return;
        }
        Minecraft.getInstance().setScreen(this.parentScreen);
    }

    private void cycleSourceBinding() {
        final List<SourceConfig> sources = this.getStressometerSources();
        if (sources.isEmpty()) {
            this.draftState.setAutoBinding();
            return;
        }
        if (this.draftState.getBindingMode() == StressometerHudState.SourceBindingMode.AUTO) {
            this.draftState.bindToSource(sources.get(0));
            this.lastSourceCycleTick = this.tickCount;
            return;
        }
        final int currentIndex = this.findSelectedSourceIndex(sources);
        if (currentIndex < 0 || currentIndex >= sources.size() - 1) {
            this.draftState.setAutoBinding();
            this.lastSourceCycleTick = this.tickCount;
            return;
        }
        this.draftState.bindToSource(sources.get(currentIndex + 1));
        this.lastSourceCycleTick = this.tickCount;
    }

    private int findSelectedSourceIndex(List<SourceConfig> sources) {
        final String selectedSourceId = this.draftState.resolvePreferredSourceId(this.panel.getSourceRegistry());
        if (selectedSourceId == null || selectedSourceId.isEmpty()) {
            return -1;
        }
        for (int i = 0; i < sources.size(); ++i) {
            final SourceConfig source = sources.get(i);
            if (source != null && source.getSourceId().equals(selectedSourceId)) {
                return i;
            }
        }
        return -1;
    }

    private List<SourceConfig> getStressometerSources() {
        final ArrayList<SourceConfig> sources = new ArrayList<SourceConfig>();
        for (SourceConfig source : this.getHudSources()) {
            if (source != null && "Stressometer".equals(source.getSourceType())) {
                sources.add(source);
            }
        }
        sources.sort(Comparator.comparing(SourceConfig::getDisplayLabel, String.CASE_INSENSITIVE_ORDER));
        return sources;
    }

    private List<SourceConfig> getHudSources() {
        final ArrayList<SourceConfig> allSources = new ArrayList<SourceConfig>();
        for (SourceConfig source : this.panel.getSourceRegistry().values()) {
            if (source != null && SpecializedHudWidgetCatalog.isSupportedSourceType(source.getSourceType())) {
                allSources.add(source);
            }
        }
        final ArrayList<SourceConfig> scopedSources = new ArrayList<SourceConfig>();
        if (this.hasStudioScope()) {
            for (SourceConfig source : allSources) {
                if (this.isSourceInScope(source)) {
                    scopedSources.add(source);
                }
            }
        }
        final ArrayList<SourceConfig> sources = this.hasStudioScope() ? scopedSources : allSources;
        this.addAeroCompositeSources(sources);
        sources.sort(Comparator
                .comparingInt(this::sourceScopeRank)
                .thenComparing((SourceConfig source) -> !StressometerHudDataResolver.hasSourceWidgetData(this.panel, source))
                .thenComparing(source -> StressometerHudState.SourceCategory.forSourceType(source.getSourceType()).label(), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(SourceConfig::getSourceType, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(SourceConfig::getDisplayLabel, String.CASE_INSENSITIVE_ORDER));
        return sources;
    }

    private void addAeroCompositeSources(ArrayList<SourceConfig> sources) {
        if (!this.hasScopedAeroFeeds()) {
            return;
        }
        if (sources.stream().noneMatch(source -> AERO_FLIGHT_DECK_SOURCE_ID.equals(source.getSourceId()))) {
            final SourceConfig deck = new SourceConfig(AERO_FLIGHT_DECK_SOURCE_ID, this.panel.getBlockPos() != null ? this.panel.getBlockPos() : BlockPos.ZERO, "Aero Flight Deck");
            deck.setUserLabel("Aero Flight Deck");
            sources.add(deck);
        }
        if (sources.stream().noneMatch(source -> AEROLINE_DIRECTOR_SOURCE_ID.equals(source.getSourceId()))) {
            final SourceConfig director = new SourceConfig(AEROLINE_DIRECTOR_SOURCE_ID, this.panel.getBlockPos() != null ? this.panel.getBlockPos() : BlockPos.ZERO, "Aeroline Director");
            director.setUserLabel("Aeroline Director");
            sources.add(director);
        }
    }

    private boolean hasScopedAeroFeeds() {
        for (SourceConfig source : this.panel.getSourceRegistry().values()) {
            if (source == null || !isAeroFeedSourceType(source.getSourceType())) {
                continue;
            }
            if (!this.hasStudioScope() || this.scopedSourceIds.contains(source.getSourceId())) {
                return true;
            }
        }
        return false;
    }

    private List<StressometerHudState.SourceCategory> getVisibleCategories() {
        final LinkedHashSet<StressometerHudState.SourceCategory> categories = new LinkedHashSet<StressometerHudState.SourceCategory>();
        for (SourceConfig source : this.getHudSources()) {
            categories.add(StressometerHudState.SourceCategory.forSourceType(source.getSourceType()));
        }
        for (StressometerHudState.PartWidget widget : this.draftState.getWidgets()) {
            if (this.isWidgetInStudioScope(widget)) {
                categories.add(widget.getSourceType().isEmpty() && widget.isStressometerPart()
                        ? StressometerHudState.SourceCategory.STRESSOMETER
                        : widget.getSourceCategory());
            }
        }
        if (this.hasTrainCapability() || this.hasBoundTrainSource() || this.draftState.getWidget("train_info") != null) {
            categories.add(StressometerHudState.SourceCategory.TRAINS);
        }
        if (this.draftState.getWidget("network_grade") != null || this.draftState.getWidget("network_efficiency") != null) {
            categories.add(StressometerHudState.SourceCategory.EFFICIENCY);
        }

        final ArrayList<StressometerHudState.SourceCategory> filtered = new ArrayList<>();
        for (StressometerHudState.SourceCategory c : categories) {
            if (this.domainAllows(c)) filtered.add(c);
        }
        return filtered;
    }

    private List<CatalogEntry> getCatalogEntries() {
        final ArrayList<CatalogEntry> entries = new ArrayList<CatalogEntry>();
        for (SourceConfig source : this.getHudSources()) {
            for (SpecializedHudWidgetCatalog.Definition definition : SpecializedHudWidgetCatalog.definitionsFor(source.getSourceType())) {
                entries.add(new CatalogEntry(source, definition));
            }
        }
        return entries;
    }

    private List<StudioButton> buildCategoryButtons(StressometerHudState.SourceCategory category) {
        final ArrayList<StudioButton> buttons = new ArrayList<StudioButton>();
        if (category == null) {
            return buttons;
        }
        if (category == StressometerHudState.SourceCategory.STRESSOMETER && this.hasTopologyControls()) {
            final String sourceContext = this.topologyWidgetSourceLabel();
            for (StressometerHudState.Part part : StressometerHudState.Part.values()) {
                if (!isStressometerCatalogPart(part)) {
                    continue;
                }
                final boolean added = this.draftState.hasPart(part);
                final boolean selected = part.id().equals(this.draftState.getSelectedWidgetId());
                final StressometerHudState.Part selectedPart = part;
                final boolean shouldFlash = !added;
                final String actionLabel = selected ? "→ " : (added ? "Select " : "+ ");
                buttons.add(new StudioButton(actionLabel + part.label(), sourceContext, () -> {
                    this.draftState.addWidget(selectedPart);
                    if (shouldFlash) {
                        this.recordAddedWidget(selectedPart.id());
                    }
                    this.recordRecentlyUsed(selectedPart.id());
                    this.rebuildStudio();
                }));
            }
        }
        if (category == StressometerHudState.SourceCategory.TRAINS) {
            final boolean added = this.draftState.getWidget("train_info") != null;
            buttons.add(new StudioButton(added ? "Select Next Train" : "+ Next Train", "Panel train map", () -> {
                this.draftState.addTrainInfoWidget();
                if (!added) {
                    this.recordAddedWidget("train_info");
                }
                this.rebuildStudio();
            }));
        }
        if (category == StressometerHudState.SourceCategory.EFFICIENCY) {
            final boolean gradeAdded = this.draftState.getWidget("network_grade") != null;
            buttons.add(new StudioButton(gradeAdded ? "Select Network Grade" : "+ Network Grade", "Panel utility", () -> {
                this.draftState.addGradeWidget();
                if (!gradeAdded) {
                    this.recordAddedWidget("network_grade");
                }
                this.rebuildStudio();
            }));
            final boolean efficiencyAdded = this.draftState.getWidget("network_efficiency") != null;
            buttons.add(new StudioButton(efficiencyAdded ? "Select Network Efficiency" : "+ Network Efficiency", "Panel utility", () -> {
                this.draftState.addUtilityWidget("network_efficiency", StressometerHudState.Part.NETWORK_EFFICIENCY);
                if (!efficiencyAdded) {
                    this.recordAddedWidget("network_efficiency");
                }
                this.rebuildStudio();
            }));
        }
        for (CatalogEntry entry : this.getCatalogEntries()) {
            if (StressometerHudState.SourceCategory.forSourceType(entry.source().getSourceType()) != category) {
                continue;
            }
            if (category == StressometerHudState.SourceCategory.STRESSOMETER && "Stressometer".equals(entry.source().getSourceType())) {
                continue;
            }
            final boolean added = this.draftState.hasSpecializedWidget(entry.source(), entry.definition().part());
            buttons.add(new StudioButton((added ? "Select " : "+ ") + entry.definition().label(), this.sourceButtonSubtitle(entry.source()), () -> {
                this.draftState.addSpecializedWidget(entry.source(), entry.definition().part());
                final String wid = StressometerHudState.PartWidget.specializedWidgetId(entry.source(), entry.definition().part());
                if (!added) {
                    this.recordAddedWidget(wid);
                }
                this.recordRecentlyUsed(wid);
                this.rebuildStudio();
            }));
        }

        if (this.browserQuery != null && !this.browserQuery.isEmpty()) {
            final ArrayList<StudioButton> filtered = new ArrayList<>();
            for (StudioButton b : buttons) {
                if (this.queryMatches(b.primaryLabel(), b.secondaryLabel())) {
                    filtered.add(b);
                }
            }
            return filtered;
        }
        return buttons;
    }

    private String sourceButtonSubtitle(SourceConfig source) {
        final String suffix = StressometerHudDataResolver.hasSourceWidgetData(this.panel, source) ? "" : " · no data";
        return this.sourceScopePrefix(source) + source.getSourceType() + " · " + this.truncateLabel(source.getDisplayLabel(), Math.max(48, this.leftW - 72)) + suffix;
    }

    private boolean hasBoundTrainSource() {
        for (SourceConfig source : this.getHudSources()) {
            if (StressometerHudState.SourceCategory.forSourceType(source.getSourceType()) == StressometerHudState.SourceCategory.TRAINS) {
                return true;
            }
        }
        return false;
    }

    private boolean hasTrainCapability() {
        return this.panel.hasTrainConnection()
                || this.panel.hasTrainStationLinks()
                || (this.panel.getTrainMapData() != null && !this.panel.getTrainMapData().getTrains().isEmpty());
    }

    private static boolean isStressometerCatalogPart(StressometerHudState.Part part) {
        return part.isStressometerWidget();
    }

    private String widgetDisplayName(StressometerHudState.PartWidget widget) {
        final String context = this.widgetSourceContext(widget);
        final String name = this.widgetPrimaryName(widget);
        return context.isEmpty() ? name : context + " · " + name;
    }

    private String widgetPrimaryName(StressometerHudState.PartWidget widget) {
        final String widgetLabel = widget.getMetricLabel().isEmpty() ? widget.getPart().label() : widget.getMetricLabel();
        if (widget.getPart() == StressometerHudState.Part.SOURCE_CARD) {
            return widget.getSourceType().isEmpty() ? "Source card" : widget.getSourceType();
        }
        return widgetLabel;
    }

    private String widgetSourceContext(StressometerHudState.PartWidget widget) {
        if (widget == null) {
            return "";
        }
        if (!widget.getSourceId().isEmpty()) {
            final SourceConfig source = this.panel.getSourceRegistry().get(widget.getSourceId());
            if (source != null) {
                return source.getSourceType() + " · " + this.truncateLabel(source.getDisplayLabel(), Math.max(48, this.leftW - 74));
            }
        }
        if (!widget.getSourceType().isEmpty()) {
            return widget.getSourceType();
        }
        if (widget.isStressometerPart() && !this.getStressometerSources().isEmpty()) {
            return this.topologyWidgetSourceLabel();
        }
        return "";
    }

    private String currentCategoryLabel() {
        final List<StressometerHudState.SourceCategory> categories = this.getVisibleCategories();
        if (categories.isEmpty()) {
            return "No bound source";
        }
        final int index = Math.max(0, Math.min(this.categoryPage, categories.size() - 1));
        return categories.get(index).label();
    }

    private record CatalogEntry(SourceConfig source, SpecializedHudWidgetCatalog.Definition definition) {
    }

    private record StudioButton(String primaryLabel, String secondaryLabel, Runnable action) {
    }

    private boolean hasStudioScope() {
        return !this.focusedSourceId.isEmpty() || !this.scopedSourceIds.isEmpty() || !this.focusedSourceType.isEmpty();
    }

    private boolean isSourceInScope(SourceConfig source) {
        if (source == null) {
            return false;
        }
        if (isAeroCompositeSource(source)) {
            return this.hasScopedAeroFeeds();
        }
        return this.scopedSourceIds.contains(source.getSourceId());
    }

    private boolean isWidgetInStudioScope(StressometerHudState.PartWidget widget) {
        if (widget == null) {
            return false;
        }
        if (widget.getSourceId().isEmpty()) {
            return widget.isStressometerPart() && !this.getStressometerSources().isEmpty();
        }
        if (isAeroCompositeSourceId(widget.getSourceId()) || isAeroCompositeSourceType(widget.getSourceType())) {
            return this.hasScopedAeroFeeds();
        }
        final SourceConfig source = this.panel.getSourceRegistry().get(widget.getSourceId());
        return source != null && this.isSourceInScope(source);
    }

    private int sourceScopeRank(SourceConfig source) {
        if (source == null) {
            return 3;
        }
        if (isAeroCompositeSource(source)) {
            return 0;
        }
        if (source.getSourceId().equals(this.focusedSourceId) && this.scopedSourceIds.contains(source.getSourceId())) {
            return 0;
        }
        if (this.scopedSourceIds.contains(source.getSourceId())) {
            return 1;
        }
        return 2;
    }

    private String sourceScopePrefix(SourceConfig source) {
        if (source == null) {
            return "";
        }
        if (isAeroCompositeSource(source)) {
            return "Composite · ";
        }
        if (source.getSourceId().equals(this.focusedSourceId) && this.scopedSourceIds.contains(source.getSourceId())) {
            return "Bound focus · ";
        }
        if (this.scopedSourceIds.contains(source.getSourceId())) {
            return "Bound · ";
        }
        return "";
    }

    private static boolean isAeroCompositeSource(SourceConfig source) {
        return source != null && (isAeroCompositeSourceId(source.getSourceId()) || isAeroCompositeSourceType(source.getSourceType()));
    }

    private static boolean isAeroCompositeSourceId(String sourceId) {
        return AERO_FLIGHT_DECK_SOURCE_ID.equals(sourceId) || AEROLINE_DIRECTOR_SOURCE_ID.equals(sourceId);
    }

    private static boolean isAeroCompositeSourceType(String sourceType) {
        return "Aero Flight Deck".equals(sourceType) || "Aeroline Director".equals(sourceType);
    }

    private static boolean isAeroFeedSourceType(String sourceType) {
        return "Portable Engine".equals(sourceType)
                || "Diesel Engine".equals(sourceType)
                || "Modular Diesel Engine".equals(sourceType)
                || "Huge Diesel Engine".equals(sourceType)
                || "Diesel Engine Group".equals(sourceType)
                || "Velocity Sensor".equals(sourceType)
                || "Gimbal Sensor".equals(sourceType)
                || "Altitude Sensor".equals(sourceType)
                || "Docking Connector".equals(sourceType)
                || "Steering Wheel".equals(sourceType)
                || "Laser Sensor".equals(sourceType)
                || "Optical Sensor".equals(sourceType)
                || "Navigation Table".equals(sourceType)
                || "Linked Typewriter".equals(sourceType)
                || "Hot Air Burner".equals(sourceType)
                || "Steam Vent".equals(sourceType)
                || "Contraption Info".equals(sourceType);
    }

    private boolean hasTopologyControls() {
        return !this.getStressometerSources().isEmpty();
    }

    private String studioSubtitle() {
        if (!this.focusedSourceId.isEmpty()) {
            final SourceConfig focused = this.panel.getSourceRegistry().get(this.focusedSourceId);
            if (focused != null && this.scopedSourceIds.contains(this.focusedSourceId)) {
                return this.truncateLabel("Bound focus: " + focused.getDisplayLabel(), Math.max(40, this.width - 220));
            }
        }
        if (this.hasStudioScope()) {
            return this.scopedSourceIds.isEmpty() ? "No bound source selected" : "Bound-source layout editor";
        }
        return "Drafting-table layout editor";
    }

    private String scopeSummary() {
        if (!this.hasStudioScope()) {
            return "Scope: all linked source families";
        }
        final int count = this.scopedSourceIds.size();
        if (count <= 0) {
            return "Scope: no bound sources";
        }
        return "Scope: " + count + " bound sources";
    }

    private int inspectorActionStartY(StressometerHudState.PartWidget selectedWidget) {
        return this.canvasY + (selectedWidget != null && !selectedWidget.getSourceType().isEmpty() ? 100 : 86);
    }

    private String buildSourceLabel() {
        final List<SourceConfig> sources = this.getStressometerSources();
        if (sources.isEmpty()) {
            return "Topology: None";
        }
        if (this.draftState.getBindingMode() == StressometerHudState.SourceBindingMode.AUTO) {
            if (sources.size() == 1) {
                return this.truncateLabel("Topology: Auto · " + sources.get(0).getDisplayLabel(), this.leftW - 20);
            }
            return "Topology: Auto";
        }
        final String selectedSourceId = this.draftState.resolvePreferredSourceId(this.panel.getSourceRegistry());
        if (selectedSourceId == null) {
            return "Topology: Missing";
        }
        final SourceConfig source = this.panel.getSourceRegistry().get(selectedSourceId);
        if (source == null) {
            return "Topology: Missing";
        }
        return this.truncateLabel("Topology: " + source.getDisplayLabel(), this.leftW - 20);
    }

    private String topologyWidgetSourceLabel() {
        final List<SourceConfig> sources = this.getStressometerSources();
        if (sources.isEmpty()) {
            return "No topology source";
        }
        if (this.draftState.getBindingMode() == StressometerHudState.SourceBindingMode.AUTO) {
            if (sources.size() == 1) {
                return sources.get(0).getDisplayLabel();
            }
            return "Auto topology";
        }
        final String selectedSourceId = this.draftState.resolvePreferredSourceId(this.panel.getSourceRegistry());
        if (selectedSourceId == null) {
            return "Missing topology";
        }
        final SourceConfig source = this.panel.getSourceRegistry().get(selectedSourceId);
        if (source == null) {
            return "Missing topology";
        }
        return source.getDisplayLabel();
    }

    private void updateViewport() {
        final int areaX = this.canvasX + 10;
        final int areaY = this.canvasY + 28;
        final int areaW = Math.max(1, this.canvasW - 20);
        final int areaH = Math.max(1, this.canvasH - 38);
        final int screenW = Math.max(1, this.minecraft.getWindow().getGuiScaledWidth());
        final int screenH = Math.max(1, this.minecraft.getWindow().getGuiScaledHeight());
        int fittedW = areaW;
        int fittedH = Math.max(1, Math.round(fittedW * screenH / (float) screenW));
        if (fittedH > areaH) {
            fittedH = areaH;
            fittedW = Math.max(1, Math.round(fittedH * screenW / (float) screenH));
        }
        this.viewportW = fittedW;
        this.viewportH = fittedH;
        this.viewportX = areaX + (areaW - fittedW) / 2;
        this.viewportY = areaY + (areaH - fittedH) / 2;
    }

    private int editWidth() {
        return Math.max(1, this.minecraft.getWindow().getGuiScaledWidth());
    }

    private int editHeight() {
        return Math.max(1, this.minecraft.getWindow().getGuiScaledHeight());
    }

    private float previewScale() {
        return this.viewportW > 0 ? (float) this.viewportW / this.editWidth() : 1.0f;
    }

    private int toScreenX(int viewportLocalX) {
        final float s = this.previewScale();
        return s > 0 ? Math.round(viewportLocalX / s) : viewportLocalX;
    }

    private int toScreenY(int viewportLocalY) {
        final float s = this.previewScale();
        return s > 0 ? Math.round(viewportLocalY / s) : viewportLocalY;
    }

    private int toViewportX(int screenX) {
        return Math.round(screenX * this.previewScale());
    }

    private int toViewportD(int screenD) {
        return Math.max(1, Math.round(screenD * this.previewScale()));
    }

    private boolean isCanvasEditable() {
        return this.viewportW >= 120 && this.viewportH >= 80;
    }

    private long getPreviewTick() {
        return (this.panel.getLevel() != null) ? this.panel.getLevel().getGameTime() : 0L;
    }

    private boolean isInsideBounds(int mouseX, int mouseY, StressometerHudState.Bounds.ResolvedBounds bounds) {
        return mouseX >= bounds.x() && mouseX < bounds.x() + bounds.width()
                && mouseY >= bounds.y() && mouseY < bounds.y() + bounds.height();
    }

    private boolean isInsideResizeHandle(int mouseX, int mouseY, StressometerHudState.Bounds.ResolvedBounds bounds) {
        return mouseX >= bounds.x() + bounds.width() - HANDLE_SIZE
                && mouseX < bounds.x() + bounds.width() + 2
                && mouseY >= bounds.y() + bounds.height() - HANDLE_SIZE
                && mouseY < bounds.y() + bounds.height() + 2;
    }

    private String truncateLabel(String value, int maxWidth) {
        if (this.font.width(value) <= maxWidth) {
            return value;
        }
        final String ellipsis = "..";
        String result = value;
        while (result.length() > 1 && this.font.width(result + ellipsis) > maxWidth) {
            result = result.substring(0, result.length() - 1);
        }
        return result + ellipsis;
    }

    private void drawPanel(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + h, color);
        this.border(g, x, y, w, h, this.BORDER);
    }

    private void drawPanelShadow(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x + 2, y + h, x + w + 2, y + h + 2, 0x55000000);
        g.fill(x + w, y + 2, x + w + 2, y + h, 0x55000000);
    }

    private void brassBorder(GuiGraphics g, int x, int y, int w, int h) {
        this.border(g, x, y, w, h, RAIL_SHADOW);
        if (w <= 2 || h <= 2) {
            return;
        }
        this.border(g, x + 1, y + 1, w - 2, h - 2, RAIL_MID);
        g.fill(x + 1, y + 1, x + 2, y + 2, RAIL_BRIGHT);
        g.fill(x + w - 2, y + h - 2, x + w - 1, y + h - 1, RAIL_SHADOW);
    }

    private void drawDraftingPanel(GuiGraphics g, int x, int y, int w, int h, int baseColor, boolean withNoise) {
        g.fill(x, y, x + w, y + h, this.withAlpha(baseColor & 0x00FFFFFF, 0xE8));
        g.fill(x + 1, y + 1, x + w - 1, y + 2, PANEL_LIFT);
        g.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, PANEL_SHADE);
        if (withNoise && w > 6 && h > 6) {
            this.drawNoiseTexture(g, x + 2, y + 2, w - 4, h - 4);
        }
        final int headerHeight = Math.min(5, Math.max(3, h / 10));
        g.fill(x + 2, y + 2, x + w - 2, y + 2 + headerHeight, 0x0E000000);
        this.drawBrassRailH(g, x + 2, y + 2, Math.max(2, w - 4), headerHeight);
        this.brassBorder(g, x, y, w, h);
    }

    private void drawNoiseTexture(GuiGraphics g, int x, int y, int w, int h) {
        for (int ny = y; ny < y + h; ny += 4) {
            for (int nx = x; nx < x + w; nx += 4) {
                final int hash = (nx * 2531011 + ny * 214013) & 0xFF;
                if (hash < 20) {
                    g.fill(nx, ny, nx + 1, ny + 1, hash < 16 ? NOISE_A : NOISE_B);
                }
            }
        }
    }

    private void drawBrassRailH(GuiGraphics g, int x, int y, int w, int thickness) {
        if (w <= 0 || thickness <= 0) {
            return;
        }
        g.fill(x, y, x + w, y + thickness, RAIL_MID);
        g.fill(x, y, x + w, y + 1, RAIL_BRIGHT);
        g.fill(x, y + thickness - 1, x + w, y + thickness, RAIL_SHADOW);
        final int shimmerOffset = (int) ((this.tickCount % 180L) * (w + 24L) / 180L) - 12;
        final int shimmerX0 = Math.max(x, x + shimmerOffset);
        final int shimmerX1 = Math.min(x + w, shimmerX0 + 14);
        if (shimmerX1 > shimmerX0) {
            g.fill(shimmerX0, y + 1, shimmerX1, y + Math.max(2, thickness - 1), this.withAlpha(RAIL_BRIGHT, 0x18 + Math.round(this.pulse01(180.0f, 0.0f) * 0x0A)));
        }
        if (thickness < 3) {
            return;
        }
        for (int rx = x + 12; rx < x + w - 12; rx += 32) {
            g.fill(rx - 1, y + thickness / 2 - 1, rx + 2, y + thickness / 2 + 2, RAIL_BRIGHT);
            g.fill(rx, y + thickness / 2, rx + 1, y + thickness / 2 + 1, RAIL_SHADOW);
        }
    }

    private void drawSectionHeader(GuiGraphics g, String text, int x, int y, int panelW) {
        final int plateX = x - 6;
        final int plateW = Math.max(24, panelW - 14);
        g.fill(plateX, y + 9, plateX + plateW, y + 10, RAIL_SHADOW);
        g.fill(plateX, y + 8, plateX + plateW, y + 9, RAIL_MID);
        g.fill(x - 2, y + 1, x - 1, y + 8, RAIL_BRIGHT);
        g.drawString(this.font, text, x, y, this.DIM);
    }

    private void drawViewportFrame(GuiGraphics g) {
        g.fill(this.viewportX - 2, this.viewportY - 2, this.viewportX + this.viewportW + 2, this.viewportY + this.viewportH + 2, 0x33000000);
        this.brassBorder(g, this.viewportX - 1, this.viewportY - 1, this.viewportW + 2, this.viewportH + 2);
        final int len = 6 + (this.pulse01(120.0f, 0.1f) > 0.86f ? 1 : 0);
        g.fill(this.viewportX, this.viewportY, this.viewportX + len, this.viewportY + 1, RAIL_BRIGHT);
        g.fill(this.viewportX, this.viewportY, this.viewportX + 1, this.viewportY + len, RAIL_BRIGHT);
        g.fill(this.viewportX + this.viewportW - len, this.viewportY, this.viewportX + this.viewportW, this.viewportY + 1, RAIL_BRIGHT);
        g.fill(this.viewportX + this.viewportW - 1, this.viewportY, this.viewportX + this.viewportW, this.viewportY + len, RAIL_BRIGHT);
        g.fill(this.viewportX, this.viewportY + this.viewportH - 1, this.viewportX + len, this.viewportY + this.viewportH, RAIL_BRIGHT);
        g.fill(this.viewportX, this.viewportY + this.viewportH - len, this.viewportX + 1, this.viewportY + this.viewportH, RAIL_BRIGHT);
        g.fill(this.viewportX + this.viewportW - len, this.viewportY + this.viewportH - 1, this.viewportX + this.viewportW, this.viewportY + this.viewportH, RAIL_BRIGHT);
        g.fill(this.viewportX + this.viewportW - 1, this.viewportY + this.viewportH - len, this.viewportX + this.viewportW, this.viewportY + this.viewportH, RAIL_BRIGHT);
    }

    private void border(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }

    private void recordAddedWidget(String widgetId) {
        this.lastAddedWidgetId = widgetId;
        this.lastAddedTick = this.tickCount;
    }

    private float pulse01(float periodTicks, float phase) {
        return 0.5f + 0.5f * (float) Math.sin((this.tickCount + phase * periodTicks) * (Math.PI * 2.0 / periodTicks));
    }

    private int withAlpha(int color, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (color & 0x00FFFFFF);
    }

    private interface BooleanGetter {
        boolean get();
    }

    private interface BooleanSetter {
        void set(boolean value);
    }

    private class SourceWidgetRowButton extends Button {
        private final String primaryLabel;
        private final String secondaryLabel;

        private SourceWidgetRowButton(int x, int y, int width, int height, String primaryLabel, String secondaryLabel, OnPress onPress) {
            super(x, y, width, height, Component.literal(primaryLabel), onPress, DEFAULT_NARRATION);
            this.primaryLabel = primaryLabel != null ? primaryLabel : "";
            this.secondaryLabel = secondaryLabel != null ? secondaryLabel : "";
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            final Minecraft minecraft = Minecraft.getInstance();
            final boolean hovered = this.isHoveredOrFocused();
            final int background = this.active ? (hovered ? 0xD0241A0D : 0xC81A1209) : 0x9A171009;
            final int primary = this.active ? (hovered ? StressometerHudSettingsScreen.this.TEXT : RAIL_BRIGHT) : StressometerHudSettingsScreen.this.withAlpha(StressometerHudSettingsScreen.this.DIM, 0xA0);
            final int secondary = this.active ? StressometerHudSettingsScreen.this.DIM : StressometerHudSettingsScreen.this.withAlpha(StressometerHudSettingsScreen.this.DIM, 0x88);
            final int border = hovered ? RAIL_BRIGHT : RAIL_MID;

            g.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height, background);
            StressometerHudSettingsScreen.this.border(g, this.getX(), this.getY(), this.width, this.height, this.active ? border : RAIL_SHADOW);
            g.fill(this.getX() + 1, this.getY() + 1, this.getX() + this.width - 1, this.getY() + 2, hovered ? 0x28FFFFFF : 0x16FFFFFF);
            g.drawString(minecraft.font, StressometerHudSettingsScreen.this.truncateLabel(this.primaryLabel, this.width - 10), this.getX() + 5, this.getY() + 4, primary);
            g.drawString(minecraft.font, StressometerHudSettingsScreen.this.truncateLabel(this.secondaryLabel, this.width - 10), this.getX() + 5, this.getY() + 15, secondary);
        }
    }

    private class StudioChromeButton extends Button {
        private StudioChromeButton(int x, int y, int width, int height, Component message, OnPress onPress) {
            super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            final Minecraft minecraft = Minecraft.getInstance();
            final boolean hovered = this.isHoveredOrFocused();
            final boolean flashing = this == StressometerHudSettingsScreen.this.sourceButton
                    && StressometerHudSettingsScreen.this.tickCount - StressometerHudSettingsScreen.this.lastSourceCycleTick < 12L;
            final int background = this.active ? (hovered ? 0xD0241A0D : 0xC81A1209) : 0x9A171009;
            final int text = this.active ? (hovered ? StressometerHudSettingsScreen.this.TEXT : StressometerHudSettingsScreen.this.DIM) : StressometerHudSettingsScreen.this.withAlpha(StressometerHudSettingsScreen.this.DIM, 0xA0);
            final int border = hovered ? RAIL_BRIGHT : RAIL_MID;

            g.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height, background);
            StressometerHudSettingsScreen.this.border(g, this.getX(), this.getY(), this.width, this.height, this.active ? border : RAIL_SHADOW);
            g.fill(this.getX() + 1, this.getY() + 1, this.getX() + this.width - 1, this.getY() + 2, hovered ? 0x28FFFFFF : 0x16FFFFFF);
            g.fill(this.getX() + 1, this.getY() + this.height - 2, this.getX() + this.width - 1, this.getY() + this.height - 1, hovered ? 0x36000000 : 0x24000000);
            if (hovered && this.width > 6 && this.height > 6) {
                g.fill(this.getX() + 2, this.getY() + 2, this.getX() + this.width - 2, this.getY() + 3, 0x12FFFFFF);
                g.fill(this.getX() + 2, this.getY() + this.height - 3, this.getX() + this.width - 2, this.getY() + this.height - 2, 0x22000000);
            }
            if (flashing) {
                final long age = StressometerHudSettingsScreen.this.tickCount - StressometerHudSettingsScreen.this.lastSourceCycleTick;
                final float fade = 1.0f - age / 12.0f;
                final int flash = StressometerHudSettingsScreen.this.withAlpha(RAIL_BRIGHT, Math.max(0, Math.round(0x90 * fade)));
                g.fill(this.getX() + 1, this.getY() + this.height - 3, this.getX() + this.width - 1, this.getY() + this.height - 1, flash);
            }

            final String label = StressometerHudSettingsScreen.this.truncateLabel(this.getMessage().getString(), this.width - 10);
            g.drawCenteredString(minecraft.font, label, this.getX() + this.width / 2, this.getY() + (this.height - minecraft.font.lineHeight) / 2, text);
        }
    }

    private final class GlobalFloatSlider extends AbstractSliderButton {
        private final float min;
        private final float max;
        private final java.util.function.Consumer<Float> onChange;
        GlobalFloatSlider(int x, int y, int w, int h, String label, double initial, float min, float max, java.util.function.Consumer<Float> onChange) {
            super(x, y, w, h, Component.literal(label), Math.max(0.0, Math.min(1.0, initial)));
            this.min = min;
            this.max = max;
            this.onChange = onChange;
        }
        @Override
        protected void updateMessage() {
            final float v = (float) (min + (max - min) * this.value);
            this.setMessage(Component.literal(String.format(Locale.ROOT, "Font Scale: %.2fx", v)));
        }
        @Override
        protected void applyValue() {
            final float v = (float) (min + (max - min) * this.value);
            this.onChange.accept(v);
        }
    }

    private final class GlobalIntSlider extends AbstractSliderButton {
        private final int min;
        private final int max;
        private final java.util.function.IntConsumer onChange;
        GlobalIntSlider(int x, int y, int w, int h, String label, double initial, int min, int max, java.util.function.IntConsumer onChange) {
            super(x, y, w, h, Component.literal(label), Math.max(0.0, Math.min(1.0, initial)));
            this.min = min;
            this.max = max;
            this.onChange = onChange;
        }
        @Override
        protected void updateMessage() {
            final int v = (int) Math.round(min + (max - min) * this.value);
            this.setMessage(Component.literal("Padding: " + (v >= 0 ? "+" : "") + v + " px"));
        }
        @Override
        protected void applyValue() {
            final int v = (int) Math.round(min + (max - min) * this.value);
            this.onChange.accept(v);
        }
    }

    private static final class UndoEntry {
        final String label;
        final CompoundTag snapshot;
        UndoEntry(String label, CompoundTag snapshot) {
            this.label = (label != null && !label.isEmpty()) ? label : "Edit";
            this.snapshot = snapshot;
        }
    }

    private enum DragHandle {
        NONE, MOVE, NW, N, NE, E, SE, S, SW, W
    }

    private enum BrowserDomain {
        ALL("All"),
        FACTORY("Factory"),
        SHIP("Ship"),
        TRAIN("Train"),
        GENERAL("General"),
        ADVANCED("Advanced");
        final String label;
        BrowserDomain(String label) { this.label = label; }
        boolean includes(StressometerHudState.SourceCategory cat) {
            if (this == ALL || this == ADVANCED) return true;
            if (cat == null) return false;
            final String n = cat.name();
            return switch (this) {
                case FACTORY -> n.equals("STRESSOMETER") || n.equals("STORAGE_INVENTORY") || n.equals("FACTORY_KINETIC")
                        || n.equals("ROTATION_FLOW") || n.equals("STOCKPILE") || n.equals("CONTENT_OBSERVER")
                        || n.equals("THRESHOLD_SWITCH") || n.equals("SMART_OBSERVER") || n.equals("SPEED_GAUGE");
                case SHIP -> n.equals("AERO") || n.equals("AERO_FLIGHT_DECK") || n.equals("AEROLINE_DIRECTOR")
                        || n.equals("AEROLINE") || n.contains("AERO") || n.contains("SHIP");
                case TRAIN -> n.contains("TRAIN") || n.contains("STATION") || n.contains("FREIGHT");
                case GENERAL -> n.equals("STRESSOMETER") || n.equals("SPEED_GAUGE") || n.equals("FACTORY_GAUGE");
                default -> true;
            };
        }
    }

    private CompoundTag canonicalSnapshot() {
        final CompoundTag tag = this.draftState.save();
        tag.remove("SelectedWidgetId");
        return tag;
    }

    private void captureBaselineSnapshot() {
        this.lastCommittedSnapshot = this.canonicalSnapshot();
        if (this.savedSnapshot == null) {
            this.savedSnapshot = this.lastCommittedSnapshot.copy();
        }
    }

    private void maybeCaptureUndoFromRebuild() {
        if (this.lastCommittedSnapshot == null) {
            this.lastCommittedSnapshot = this.canonicalSnapshot();
            return;
        }

        if (this.pendingGestureSnapshot != null) {
            this.lastCommittedSnapshot = this.canonicalSnapshot();
            return;
        }
        final CompoundTag current = this.canonicalSnapshot();
        if (!current.equals(this.lastCommittedSnapshot)) {
            this.pushUndoEntry(this.pendingActionLabel, this.lastCommittedSnapshot);
            this.lastCommittedSnapshot = current;
        }
        this.pendingActionLabel = "Edit";
    }

    private void pushUndoEntry(String label, CompoundTag snapshot) {
        if (snapshot == null) return;
        this.undoStack.push(new UndoEntry(label, snapshot));
        while (this.undoStack.size() > UNDO_CAP) {
            this.undoStack.pollLast();
        }
        this.redoStack.clear();
    }

    private void undo() {
        if (this.undoStack.isEmpty()) {
            this.setStatus("Nothing to undo");
            return;
        }
        final UndoEntry entry = this.undoStack.pop();
        final CompoundTag currentBeforeRestore = this.canonicalSnapshot();
        this.redoStack.push(new UndoEntry(entry.label, currentBeforeRestore));
        this.applySnapshot(entry.snapshot);
        this.setStatus("Undid: " + entry.label);
    }

    private void redo() {
        if (this.redoStack.isEmpty()) {
            this.setStatus("Nothing to redo");
            return;
        }
        final UndoEntry entry = this.redoStack.pop();
        final CompoundTag currentBeforeRestore = this.canonicalSnapshot();
        this.undoStack.push(new UndoEntry(entry.label, currentBeforeRestore));
        this.applySnapshot(entry.snapshot);
        this.setStatus("Redid: " + entry.label);
    }

    private void applySnapshot(CompoundTag snapshot) {
        if (snapshot == null) return;
        final String selBefore = this.draftState.getSelectedWidgetId();
        this.draftState = StressometerHudState.load(snapshot);
        this.draftHudSettings.setStressometerHud(this.draftState);
        if (selBefore != null && !selBefore.isEmpty()) {
            if (this.draftState.getWidget(selBefore) != null) {
                this.draftState.setSelectedWidgetId(selBefore);
            } else {
                this.setStatus("Widget removed");
            }
        }
        this.lastCommittedSnapshot = this.canonicalSnapshot();
        this.pendingActionLabel = "Edit";
        this.pendingGestureSnapshot = null;
        this.activeHandle = DragHandle.NONE;
        this.dragging = false;
        this.dragArmed = false;
        this.resizing = false;
        this.clearWidgets();
        this.init();
    }

    private void beginUndoGesture(String label) {
        this.pendingGestureLabel = (label != null) ? label : "Edit";
        this.pendingGestureSnapshot = this.canonicalSnapshot();
    }

    private void commitUndoGesture() {
        if (this.pendingGestureSnapshot == null) {
            return;
        }
        final CompoundTag now = this.canonicalSnapshot();
        if (!now.equals(this.pendingGestureSnapshot)) {
            this.pushUndoEntry(this.pendingGestureLabel, this.pendingGestureSnapshot);
            this.lastCommittedSnapshot = now;
        }
        this.pendingGestureSnapshot = null;
        this.pendingGestureLabel = "";
    }

    private void cancelUndoGesture() {
        this.pendingGestureSnapshot = null;
        this.pendingGestureLabel = "";
    }

    private void setStatus(String message) {
        this.statusMessage = (message != null) ? message : "";
        this.statusMessageExpiryTick = this.tickCount + 60L;
    }

    private void setNextActionLabel(String label) {
        if (label != null && !label.isEmpty()) {
            this.pendingActionLabel = label;
        }
    }

    private boolean isDirty() {
        if (this.savedSnapshot == null) return false;
        return !this.canonicalSnapshot().equals(this.savedSnapshot);
    }

    private int snap(int v) {
        if (!this.snapToGrid) return v;
        final int g = 8;
        return Math.round(v / (float) g) * g;
    }

    private DragHandle hitHandle(int localX, int localY, StressometerHudState.Bounds.ResolvedBounds b) {
        final int hit = 14;
        final int x0 = b.x(), y0 = b.y(), x1 = b.x() + b.width(), y1 = b.y() + b.height();

        if (localX >= x0 - hit / 2 && localX <= x0 + hit / 2 && localY >= y0 - hit / 2 && localY <= y0 + hit / 2) return DragHandle.NW;
        if (localX >= x1 - hit / 2 && localX <= x1 + hit / 2 && localY >= y0 - hit / 2 && localY <= y0 + hit / 2) return DragHandle.NE;
        if (localX >= x0 - hit / 2 && localX <= x0 + hit / 2 && localY >= y1 - hit / 2 && localY <= y1 + hit / 2) return DragHandle.SW;
        if (localX >= x1 - hit / 2 && localX <= x1 + hit / 2 && localY >= y1 - hit / 2 && localY <= y1 + hit / 2) return DragHandle.SE;

        if (localX >= x0 + hit / 2 && localX <= x1 - hit / 2 && localY >= y0 - hit / 2 && localY <= y0 + hit / 2) return DragHandle.N;
        if (localX >= x0 + hit / 2 && localX <= x1 - hit / 2 && localY >= y1 - hit / 2 && localY <= y1 + hit / 2) return DragHandle.S;
        if (localY >= y0 + hit / 2 && localY <= y1 - hit / 2 && localX >= x0 - hit / 2 && localX <= x0 + hit / 2) return DragHandle.W;
        if (localY >= y0 + hit / 2 && localY <= y1 - hit / 2 && localX >= x1 - hit / 2 && localX <= x1 + hit / 2) return DragHandle.E;
        return DragHandle.NONE;
    }

    private void applyHandleResize(DragHandle h, int localX, int localY) {
        final StressometerHudState.PartWidget widget = this.draftState.getSelectedWidget();
        if (widget == null) return;
        int x = this.dragStartBoundsX, y = this.dragStartBoundsY;
        int w = this.dragStartBoundsW, hgt = this.dragStartBoundsH;
        final int dx = localX - this.dragStartMouseX;
        final int dy = localY - this.dragStartMouseY;
        final int minW = Math.max(8, widget.getPart().minWidth());
        final int minH = Math.max(8, widget.getPart().minHeight());
        switch (h) {
            case N -> { y = this.snap(this.dragStartBoundsY + dy); hgt = this.dragStartBoundsH - (y - this.dragStartBoundsY); }
            case S -> { hgt = this.snap(this.dragStartBoundsH + dy); }
            case W -> { x = this.snap(this.dragStartBoundsX + dx); w = this.dragStartBoundsW - (x - this.dragStartBoundsX); }
            case E -> { w = this.snap(this.dragStartBoundsW + dx); }
            case NW -> {
                x = this.snap(this.dragStartBoundsX + dx); w = this.dragStartBoundsW - (x - this.dragStartBoundsX);
                y = this.snap(this.dragStartBoundsY + dy); hgt = this.dragStartBoundsH - (y - this.dragStartBoundsY);
            }
            case NE -> {
                w = this.snap(this.dragStartBoundsW + dx);
                y = this.snap(this.dragStartBoundsY + dy); hgt = this.dragStartBoundsH - (y - this.dragStartBoundsY);
            }
            case SW -> {
                x = this.snap(this.dragStartBoundsX + dx); w = this.dragStartBoundsW - (x - this.dragStartBoundsX);
                hgt = this.snap(this.dragStartBoundsH + dy);
            }
            case SE -> { w = this.snap(this.dragStartBoundsW + dx); hgt = this.snap(this.dragStartBoundsH + dy); }
            default -> { return; }
        }
        if (w < minW) {
            if (h == DragHandle.W || h == DragHandle.NW || h == DragHandle.SW) {
                x = this.dragStartBoundsX + this.dragStartBoundsW - minW;
            }
            w = minW;
        }
        if (hgt < minH) {
            if (h == DragHandle.N || h == DragHandle.NW || h == DragHandle.NE) {
                y = this.dragStartBoundsY + this.dragStartBoundsH - minH;
            }
            hgt = minH;
        }
        widget.setBoundsFromPixels(x, y, w, hgt, this.editWidth(), this.editHeight());
    }

    private DisplayTheme studioTheme() {
        return this.previewThemeOverride != null ? this.previewThemeOverride : this.panel.getSettings().getDisplayTheme();
    }

    private void recordRecentlyUsed(String widgetId) {
        if (widgetId == null || widgetId.isEmpty()) return;
        this.recentlyUsedWidgetIds.remove(widgetId);
        this.recentlyUsedWidgetIds.addFirst(widgetId);
        while (this.recentlyUsedWidgetIds.size() > RECENT_CAP) {
            this.recentlyUsedWidgetIds.pollLast();
        }
    }

    private boolean domainAllows(StressometerHudState.SourceCategory cat) {
        return this.selectedDomain == null || this.selectedDomain.includes(cat);
    }

    private boolean queryMatches(String... haystack) {
        if (this.browserQuery == null || this.browserQuery.isEmpty()) return true;
        final String q = this.browserQuery.toLowerCase(Locale.ROOT);
        for (String s : haystack) {
            if (s != null && s.toLowerCase(Locale.ROOT).contains(q)) return true;
        }
        return false;
    }

    private void drawDomainChips(GuiGraphics g) {
        if (this.domainChipW <= 0) return;
        final BrowserDomain[] domains = BrowserDomain.values();
        for (int i = 0; i < domains.length; ++i) {
            final BrowserDomain d = domains[i];
            final boolean sel = this.selectedDomain == d;
            final int x = this.domainChipX[i];
            final int y = this.domainChipY;
            final int w = this.domainChipW;
            final int h = this.domainChipH;
            final int bg = sel ? this.studioTheme().tabSel : this.studioTheme().tabBg;
            g.fill(x, y, x + w, y + h, bg);
            this.border(g, x, y, w, h, sel ? this.studioTheme().selectionOutline() : this.studioTheme().chromeDivider());
            final String shortLabel = w >= 36 ? d.label : d.label.substring(0, Math.min(3, d.label.length()));
            final int tx = x + Math.max(2, (w - this.font.width(shortLabel)) / 2);
            g.drawString(this.font, shortLabel, tx, y + 3, sel ? this.TEXT : this.DIM);
        }
    }

    private BrowserDomain hitDomainChip(double mouseX, double mouseY) {
        if (mouseY < this.domainChipY || mouseY >= this.domainChipY + this.domainChipH) return null;
        final BrowserDomain[] domains = BrowserDomain.values();
        for (int i = 0; i < domains.length; ++i) {
            final int x = this.domainChipX[i];
            if (mouseX >= x && mouseX < x + this.domainChipW) return domains[i];
        }
        return null;
    }

    private void drawQuickActionChip(GuiGraphics g, int mouseX, int mouseY,
                                      int vpBX, int vpBY, int vpBW, int vpBH,
                                      StressometerHudState.PartWidget selectedWidget) {
        if (selectedWidget == null || this.dragging || this.resizing) {
            this.chipDuplicateX = -1; this.chipDeleteX = -1; this.chipVariantX = -1;
            return;
        }
        final java.util.List<String> variants = availableVariantsFor(selectedWidget);
        final boolean hasVariants = variants.size() > 1;
        this.chipBtnW = 16;
        this.chipBtnH = 16;
        final int padding = 4;
        final int chipCount = hasVariants ? 3 : 2;
        final int chipsW = chipCount * this.chipBtnW + (chipCount - 1) * 2 + padding * 2;
        final int chipsH = this.chipBtnH + padding * 2;

        int cx = vpBX + vpBW + 6;
        int cy = vpBY;
        if (cx + chipsW > this.viewportX + this.viewportW - 2) {
            cx = vpBX - chipsW - 6;
        }
        if (cx < this.viewportX + 2) {
            cx = Math.min(this.viewportX + this.viewportW - chipsW - 2, Math.max(this.viewportX + 2, vpBX));
            cy = Math.max(this.viewportY + 2, vpBY - chipsH - 4);
        }
        cy = Math.max(this.viewportY + 2, Math.min(this.viewportY + this.viewportH - chipsH - 2, cy));

        final DisplayTheme th = this.studioTheme();
        g.fill(cx, cy, cx + chipsW, cy + chipsH, 0xCC1A1209);
        this.border(g, cx, cy, chipsW, chipsH, th.chromeDivider());
        int bx = cx + padding;
        final int by = cy + padding;
        this.chipDuplicateX = bx; this.chipDuplicateY = by;
        this.drawChipButton(g, bx, by, "D", th, mouseX, mouseY);
        bx += this.chipBtnW + 2;
        this.chipDeleteX = bx; this.chipDeleteY = by;
        this.drawChipButton(g, bx, by, "✕", th, mouseX, mouseY);
        if (hasVariants) {
            bx += this.chipBtnW + 2;
            this.chipVariantX = bx; this.chipVariantY = by;
            this.drawChipButton(g, bx, by, "▾", th, mouseX, mouseY);
        } else {
            this.chipVariantX = -1;
        }
    }

    private void drawChipButton(GuiGraphics g, int x, int y, String label, DisplayTheme th, int mouseX, int mouseY) {
        final boolean hover = mouseX >= x && mouseX < x + this.chipBtnW && mouseY >= y && mouseY < y + this.chipBtnH;
        g.fill(x, y, x + this.chipBtnW, y + this.chipBtnH, hover ? th.tabHover : th.tabBg);
        this.border(g, x, y, this.chipBtnW, this.chipBtnH, hover ? th.selectionOutline() : th.chromeDivider());
        g.drawString(this.font, label, x + (this.chipBtnW - this.font.width(label)) / 2, y + 4, hover ? th.text : th.dim);
    }

    private String hitQuickActionChip(double mouseX, double mouseY) {
        if (this.chipDuplicateX >= 0 && mouseX >= this.chipDuplicateX && mouseX < this.chipDuplicateX + this.chipBtnW
                && mouseY >= this.chipDuplicateY && mouseY < this.chipDuplicateY + this.chipBtnH) return "duplicate";
        if (this.chipDeleteX >= 0 && mouseX >= this.chipDeleteX && mouseX < this.chipDeleteX + this.chipBtnW
                && mouseY >= this.chipDeleteY && mouseY < this.chipDeleteY + this.chipBtnH) return "delete";
        if (this.chipVariantX >= 0 && mouseX >= this.chipVariantX && mouseX < this.chipVariantX + this.chipBtnW
                && mouseY >= this.chipVariantY && mouseY < this.chipVariantY + this.chipBtnH) return "variant";
        return null;
    }
}
