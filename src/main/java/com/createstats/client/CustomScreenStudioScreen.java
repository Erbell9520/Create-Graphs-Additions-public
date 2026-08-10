package com.createstats.client;

import com.createstats.blockentity.SourceConfig;
import com.createstats.blockentity.StatsDisplayPanelBE;
import com.createstats.display.CustomModuleInstance;
import com.createstats.display.CustomModuleBinding;
import com.createstats.display.CustomScreenLayout;
import com.createstats.display.CustomScreenMigrationCoordinator;
import com.createstats.display.CustomScreenModuleCatalog;
import com.createstats.display.CustomScreenModuleContract;
import com.createstats.display.CustomScreenPresetCatalog;
import com.createstats.display.CustomScreenProfile;
import com.createstats.display.CustomScreenSourceFamilyCatalog;
import com.createstats.display.CustomScreenSourceCapability;
import com.createstats.display.CustomScreenSourceCapabilityCatalog;
import com.createstats.display.CustomScreenModuleType;
import com.createstats.display.DisplayTheme;
import com.createstats.display.SourceFamilyDefinition;
import com.createstats.display.SpecializedHudWidgetCatalog;
import com.createstats.display.StatsDisplaySettings;
import com.createstats.network.ConfigSyncPacket;
import com.createstats.renderer.CustomScreenModuleRenderer;
import com.createstats.renderer.DisplayTextureManager;
import net.minecraft.core.BlockPos;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class CustomScreenStudioScreen extends Screen {
    private static final int MARGIN = 10;
    private static final int HEADER_H = 56;
    private static final int FOOTER_H = 22;
    private static final int GRID_PAD = 14;
    private static final int UNDO_CAP = 50;

    private final StatsDisplayPanelBE panel;
    private final Screen parentScreen;
    private final StatsDisplaySettings draftSettings;
    private CustomScreenLayout draftLayout;

    private DisplayTheme previewThemeOverride;
    private CustomScreenLayout.VariantFamily activeVariant;
    private CustomScreenModuleContract.Domain selectedDomain;
    private RailMode railMode;
    private String selectedSourceId;
    private String selectedModuleId;
    private EditBox profileNameBox;
    private EditBox searchBox;

    private Button saveButton;
    private Button undoButton;
    private Button redoButton;
    private Button snapButton;
    private Button helpButton;
    private Button previewButton;
    private Button presetsButton;
    private Button variantButton;
    private Button newProfileButton;
    private Button copyProfileButton;
    private Button nextProfileButton;
    private Button deleteProfileButton;
    private Button duplicateButton;
    private Button deleteButton;
    private Button visibilityButton;
    private Button useBaseButton;
    private Button styleButton;
    private Button xMinusButton;
    private Button xPlusButton;
    private Button yMinusButton;
    private Button yPlusButton;
    private Button wMinusButton;
    private Button wPlusButton;
    private Button hMinusButton;
    private Button hPlusButton;

    private final Deque<CompoundTag> undoStack = new ArrayDeque<CompoundTag>();
    private final Deque<CompoundTag> redoStack = new ArrayDeque<CompoundTag>();
    private CompoundTag savedSnapshot;

    private boolean snapToGrid;
    private boolean helpMode;
    private boolean presetModalOpen;
    private boolean sourcePickerOpen;
    private boolean slotPickerOpen;
    private boolean draggingModule;
    private boolean resizingModule;
    private String gestureModuleId;
    private int gestureStartMouseX;
    private int gestureStartMouseY;
    private CustomModuleInstance.GridBounds gestureStartBounds;
    private String selectedPresetId;
    private String statusMessage;
    private CompoundTag copiedModuleSnapshot;
    private int presetScrollOffset;
    private int railScrollOffset;
    private int sourcePickerScrollOffset;
    private String sourcePickerField;
    private int slotPickerScrollOffset;
    private String slotPickerField;
    private int presetReplaceX;
    private int presetReplaceY;
    private int presetAddX;
    private int presetAddY;
    private int presetCancelX;
    private int presetCancelY;
    private int sourcePickerCancelX;
    private int sourcePickerCancelY;
    private int slotPickerCancelX;
    private int slotPickerCancelY;

    private int leftX;
    private int leftW;
    private int inspectorX;
    private int inspectorW;
    private int stageX;
    private int stageY;
    private int stageW;
    private int stageH;
    private int gridOriginX;
    private int gridOriginY;
    private int gridCols;
    private int gridRows;
    private int cellSize;
    private boolean compactHeaderTools;

    public CustomScreenStudioScreen(StatsDisplayPanelBE panel, Screen parentScreen) {
        super(Component.literal("Custom Screen Studio"));
        this.panel = panel.getControllerBE();
        this.parentScreen = parentScreen;
        this.draftSettings = StatsDisplaySettings.load(this.panel.getSettings().save());
        this.draftLayout = this.draftSettings.hasCustomScreenLayout()
                ? this.draftSettings.getCustomScreenLayout().copy()
                : CustomScreenModuleCatalog.createStarterLayout();
        if (this.draftLayout == null || this.draftLayout.getModules().isEmpty()) {
            this.draftLayout = CustomScreenModuleCatalog.createStarterLayout();
        }
        this.draftLayout = CustomScreenMigrationCoordinator.normalizeLayout(this.draftLayout, this.panel.getSourceRegistry());
        this.draftSettings.setCustomScreenLayout(this.draftLayout.copy());
        this.activeVariant = CustomScreenLayout.VariantFamily.BASE;
        this.selectedDomain = null;
        this.railMode = RailMode.SOURCES;
        this.selectedSourceId = "";
        this.selectedModuleId = this.draftLayout.getModules().isEmpty() ? null : this.draftLayout.getModules().get(0).getId();
        this.snapToGrid = true;
        this.helpMode = false;
        this.presetModalOpen = false;
        this.sourcePickerOpen = false;
        this.slotPickerOpen = false;
        this.selectedPresetId = null;
        this.statusMessage = "";
        this.copiedModuleSnapshot = null;
        this.presetScrollOffset = 0;
        this.railScrollOffset = 0;
        this.sourcePickerScrollOffset = 0;
        this.sourcePickerField = "";
        this.slotPickerScrollOffset = 0;
        this.slotPickerField = "";
        this.savedSnapshot = this.draftSettings.save();
    }

    @Override
    protected void init() {
        this.computeLayout();
        this.clearWidgets();
        final int topButtonY = MARGIN + 6;
        final int actionButtonY = MARGIN + 32;
        final int rightActionStartX = this.width - MARGIN - 176;
        final int titleReserve = this.headerTitleMaxWidth(false);
        int toolX = MARGIN + 12 + titleReserve + 10;
        final int toolbarEndX = rightActionStartX - 8;
        final int fullToolbarWidth = 426;
        this.compactHeaderTools = toolbarEndX - toolX < fullToolbarWidth;
        if (this.compactHeaderTools) {
            toolX = MARGIN + 12 + this.headerTitleMaxWidth(true) + 8;
        }
        this.saveButton = this.addRenderableWidget(this.studioButton("Save", button -> this.saveAndClose(), this.width - MARGIN - 54, actionButtonY, 54, 18));
        this.addRenderableWidget(this.studioButton("Cancel", button -> this.cancel(), this.width - MARGIN - 114, actionButtonY, 56, 18));
        this.addRenderableWidget(this.studioButton("Reset", button -> this.resetLayout(), this.width - MARGIN - 176, actionButtonY, 56, 18));

        this.undoButton = this.addRenderableWidget(this.studioButton(this.compactHeaderTools ? "U" : "Undo", button -> this.undo(), toolX, topButtonY, this.compactHeaderTools ? 22 : 38, 18));
        toolX += this.compactHeaderTools ? 25 : 42;
        this.redoButton = this.addRenderableWidget(this.studioButton(this.compactHeaderTools ? "R" : "Redo", button -> this.redo(), toolX, topButtonY, this.compactHeaderTools ? 22 : 38, 18));
        toolX += this.compactHeaderTools ? 25 : 42;
        this.snapButton = this.addRenderableWidget(this.studioButton(this.compactHeaderTools ? "Snap" : "Snap On", button -> {
            this.snapToGrid = !this.snapToGrid;
            this.updateButtonStates();
        }, toolX, topButtonY, this.compactHeaderTools ? 36 : 58, 18));
        toolX += this.compactHeaderTools ? 39 : 62;
        this.helpButton = this.addRenderableWidget(this.studioButton(this.compactHeaderTools ? "?" : "Help", button -> {
            this.helpMode = !this.helpMode;
            this.updateButtonStates();
        }, toolX, topButtonY, this.compactHeaderTools ? 22 : 42, 18));
        toolX += this.compactHeaderTools ? 25 : 46;
        this.previewButton = this.addRenderableWidget(this.studioButton(this.compactHeaderTools ? "View" : "Preview", button -> {
            this.previewThemeOverride = this.studioTheme().next();
            this.updateButtonStates();
        }, toolX, topButtonY, this.compactHeaderTools ? 44 : 78, 18));
        toolX += this.compactHeaderTools ? 47 : 82;
        this.presetsButton = this.addRenderableWidget(this.studioButton(this.compactHeaderTools ? "Preset" : "Presets", button -> {
            this.presetModalOpen = true;
            this.presetScrollOffset = 0;
            if (this.selectedPresetId == null && !CustomScreenPresetCatalog.allPresets().isEmpty()) {
                this.selectedPresetId = CustomScreenPresetCatalog.allPresets().get(0).id();
            }
            this.updateButtonStates();
        }, toolX, topButtonY, this.compactHeaderTools ? 44 : 70, 18));
        toolX += this.compactHeaderTools ? 47 : 74;
        this.variantButton = this.addRenderableWidget(this.studioButton(this.compactHeaderTools ? "Var" : "Variant", button -> {
            this.activeVariant = nextVariant(this.activeVariant);
            this.computeLayout();
            this.updateButtonStates();
        }, toolX, topButtonY, this.compactHeaderTools ? 38 : 78, 18));

        if (this.profileNameBox == null) {
            this.profileNameBox = new EditBox(this.font, this.leftX + 10, this.stageY + 18, this.leftW - 20, 18, Component.literal("Profile name"));
            this.profileNameBox.setHint(Component.literal("Profile name"));
            this.profileNameBox.setValue(this.currentProfileName());
        }
        else {
            this.profileNameBox.setX(this.leftX + 10);
            this.profileNameBox.setY(this.stageY + 18);
            this.profileNameBox.setWidth(this.leftW - 20);
        }
        this.addRenderableWidget(this.profileNameBox);

        final int profileButtonY = this.stageY + 42;
        final int profileButtonGap = 3;
        final int profileButtonW = Math.max(24, (this.leftW - 20 - profileButtonGap * 3) / 4);
        this.newProfileButton = this.addRenderableWidget(this.studioButton("New", button -> this.createProfile(), this.leftX + 10, profileButtonY, profileButtonW, 16));
        this.copyProfileButton = this.addRenderableWidget(this.studioButton("Copy", button -> this.duplicateProfile(), this.leftX + 10 + (profileButtonW + profileButtonGap), profileButtonY, profileButtonW, 16));
        this.nextProfileButton = this.addRenderableWidget(this.studioButton("Next", button -> this.switchNextProfile(), this.leftX + 10 + (profileButtonW + profileButtonGap) * 2, profileButtonY, profileButtonW, 16));
        this.deleteProfileButton = this.addRenderableWidget(this.studioButton("Del", button -> this.deleteActiveProfile(), this.leftX + 10 + (profileButtonW + profileButtonGap) * 3, profileButtonY, profileButtonW, 16));

        if (this.searchBox == null) {
            this.searchBox = new EditBox(this.font, this.leftX + 10, this.stageY + 76, this.leftW - 20, 18, Component.literal("Search modules"));
            this.searchBox.setHint(Component.literal("Search modules"));
        }
        else {
            this.searchBox.setX(this.leftX + 10);
            this.searchBox.setY(this.stageY + 76);
            this.searchBox.setWidth(this.leftW - 20);
        }
        this.addRenderableWidget(this.searchBox);

        final int inspectorButtonW = (this.inspectorW - 26) / 2;
        int inspectorY = this.inspectorActionY();
        this.duplicateButton = this.addRenderableWidget(this.studioButton("Duplicate", button -> this.duplicateSelected(), this.inspectorX + 10, inspectorY, inspectorButtonW, 18));
        this.deleteButton = this.addRenderableWidget(this.studioButton("Delete", button -> this.deleteSelected(), this.inspectorX + 14 + inspectorButtonW, inspectorY, inspectorButtonW, 18));
        inspectorY += 22;
        this.visibilityButton = this.addRenderableWidget(this.studioButton("Hide", button -> this.toggleSelectedVisibility(), this.inspectorX + 10, inspectorY, inspectorButtonW, 18));
        this.useBaseButton = this.addRenderableWidget(this.studioButton("Use Base", button -> this.clearSelectedVariantOverride(), this.inspectorX + 14 + inspectorButtonW, inspectorY, inspectorButtonW, 18));
        inspectorY += 28;
        this.styleButton = this.addRenderableWidget(this.studioButton("Style", button -> this.cycleSelectedStyle(), this.inspectorX + 10, inspectorY, this.inspectorW - 20, 18));
        inspectorY += 24;
        this.xMinusButton = this.addRenderableWidget(this.studioButton("X-", button -> this.nudgeSelected(-1, 0, 0, 0), this.inspectorX + 10, inspectorY, inspectorButtonW, 18));
        this.xPlusButton = this.addRenderableWidget(this.studioButton("X+", button -> this.nudgeSelected(1, 0, 0, 0), this.inspectorX + 14 + inspectorButtonW, inspectorY, inspectorButtonW, 18));
        inspectorY += 22;
        this.yMinusButton = this.addRenderableWidget(this.studioButton("Y-", button -> this.nudgeSelected(0, -1, 0, 0), this.inspectorX + 10, inspectorY, inspectorButtonW, 18));
        this.yPlusButton = this.addRenderableWidget(this.studioButton("Y+", button -> this.nudgeSelected(0, 1, 0, 0), this.inspectorX + 14 + inspectorButtonW, inspectorY, inspectorButtonW, 18));
        inspectorY += 22;
        this.wMinusButton = this.addRenderableWidget(this.studioButton("W-", button -> this.nudgeSelected(0, 0, -1, 0), this.inspectorX + 10, inspectorY, inspectorButtonW, 18));
        this.wPlusButton = this.addRenderableWidget(this.studioButton("W+", button -> this.nudgeSelected(0, 0, 1, 0), this.inspectorX + 14 + inspectorButtonW, inspectorY, inspectorButtonW, 18));
        inspectorY += 22;
        this.hMinusButton = this.addRenderableWidget(this.studioButton("H-", button -> this.nudgeSelected(0, 0, 0, -1), this.inspectorX + 10, inspectorY, inspectorButtonW, 18));
        this.hPlusButton = this.addRenderableWidget(this.studioButton("H+", button -> this.nudgeSelected(0, 0, 0, 1), this.inspectorX + 14 + inspectorButtonW, inspectorY, inspectorButtonW, 18));
        this.updateButtonStates();
    }

    @Override
    public void tick() {
        super.tick();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        final DisplayTheme theme = this.studioTheme();
        this.renderStudioBackdrop(graphics, theme);

        this.renderTopBar(graphics, theme);
        this.renderLeftRail(graphics, mouseX, mouseY, theme);
        this.renderStage(graphics, mouseX, mouseY, theme);
        this.renderInspector(graphics, theme);
        this.renderFooter(graphics, theme);

        super.render(graphics, mouseX, mouseY, partialTick);

        if (this.helpMode) {
            this.renderHelpOverlay(graphics, theme);
        }
        if (this.presetModalOpen) {
            this.renderPresetModal(graphics, mouseX, mouseY, theme);
        }
        if (this.sourcePickerOpen) {
            this.renderSourcePickerModal(graphics, mouseX, mouseY, theme);
        }
        if (this.slotPickerOpen) {
            this.renderSlotPickerModal(graphics, mouseX, mouseY, theme);
        }
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.sourcePickerOpen && this.handleSourcePickerModalClick((int) mouseX, (int) mouseY, button)) {
            return true;
        }
        if (this.slotPickerOpen && this.handleSlotPickerModalClick((int) mouseX, (int) mouseY, button)) {
            return true;
        }
        if (this.presetModalOpen && this.handlePresetModalClick((int) mouseX, (int) mouseY, button)) {
            return true;
        }
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (button != 0) {
            return false;
        }
        if (this.handleInspectorClick((int) mouseX, (int) mouseY)) {
            return true;
        }
        if (this.handleLeftRailClick((int) mouseX, (int) mouseY)) {
            return true;
        }
        final ModuleView hit = this.findModuleAt((int) mouseX, (int) mouseY);
        if (hit != null) {
            this.selectedModuleId = hit.module().getId();
            this.updateButtonStates();
            final int handleSize = 8;
            final int handleX = hit.renderX + hit.renderW - handleSize;
            final int handleY = hit.renderY + hit.renderH - handleSize;
            if (mouseX >= handleX && mouseY >= handleY) {
                this.beginGesture(hit, (int) mouseX, (int) mouseY, false, true);
            }
            else {
                this.beginGesture(hit, (int) mouseX, (int) mouseY, true, false);
            }
            return true;
        }
        if (this.isInsideGrid((int) mouseX, (int) mouseY)) {
            this.selectedModuleId = null;
            this.updateButtonStates();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0 && (this.draggingModule || this.resizingModule) && this.gestureModuleId != null && this.gestureStartBounds != null) {
            final int deltaGridX = this.deltaToGrid((int) mouseX - this.gestureStartMouseX);
            final int deltaGridY = this.deltaToGrid((int) mouseY - this.gestureStartMouseY);
            final CustomModuleInstance.GridBounds nextBounds = this.gestureStartBounds.copy();
            final CustomModuleInstance gestureModule = this.draftLayout.findModule(this.gestureModuleId);
            final CustomScreenModuleContract gestureContract = gestureModule != null ? CustomScreenModuleCatalog.getContract(gestureModule.getModuleType()) : null;
            if (this.draggingModule) {
                nextBounds.setX(this.gestureStartBounds.getX() + deltaGridX);
                nextBounds.setY(this.gestureStartBounds.getY() + deltaGridY);
            }
            if (this.resizingModule) {
                nextBounds.setWidth(this.gestureStartBounds.getWidth() + deltaGridX);
                nextBounds.setHeight(this.gestureStartBounds.getHeight() + deltaGridY);
            }
            this.clampBounds(nextBounds, gestureContract);
            this.applyBoundsChange(this.gestureModuleId, nextBounds);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && (this.draggingModule || this.resizingModule)) {
            this.draggingModule = false;
            this.resizingModule = false;
            this.gestureModuleId = null;
            this.gestureStartBounds = null;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (this.presetModalOpen) {
            final int modalH = Math.min(250, this.height - 80);
            final int visibleRows = this.presetVisibleRowCount(modalH);
            final int maxOffset = Math.max(0, CustomScreenPresetCatalog.allPresets().size() - visibleRows);
            this.presetScrollOffset = Math.max(0, Math.min(maxOffset, this.presetScrollOffset + (scrollY < 0.0D ? 1 : -1)));
            return true;
        }
        if (this.sourcePickerOpen) {
            final int modalH = Math.min(280, this.height - 80);
            final int visibleRows = this.sourcePickerVisibleRowCount(modalH);
            final int maxOffset = Math.max(0, this.sourcePickerEntries().size() - visibleRows);
            this.sourcePickerScrollOffset = Math.max(0, Math.min(maxOffset, this.sourcePickerScrollOffset + (scrollY < 0.0D ? 1 : -1)));
            return true;
        }
        if (this.slotPickerOpen) {
            final int modalH = Math.min(280, this.height - 80);
            final int visibleRows = this.slotPickerVisibleRowCount(modalH);
            final int maxOffset = Math.max(0, this.slotPickerEntries().size() - visibleRows);
            this.slotPickerScrollOffset = Math.max(0, Math.min(maxOffset, this.slotPickerScrollOffset + (scrollY < 0.0D ? 1 : -1)));
            return true;
        }
        if (inside((int) mouseX, (int) mouseY, this.leftX, this.stageY, this.leftW, this.stageH)) {
            final int visibleRows = this.railVisibleRows();
            final int maxOffset = Math.max(0, this.railEntryCount() - visibleRows);
            this.railScrollOffset = Math.max(0, Math.min(maxOffset, this.railScrollOffset + (scrollY < 0.0D ? 1 : -1)));
            return maxOffset > 0;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (((this.searchBox != null && this.searchBox.isFocused()) || (this.profileNameBox != null && this.profileNameBox.isFocused()))
                && keyCode != GLFW.GLFW_KEY_ESCAPE) {
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        if (hasControlDown() && keyCode == GLFW.GLFW_KEY_Z) {
            this.undo();
            return true;
        }
        if (hasControlDown() && (keyCode == GLFW.GLFW_KEY_Y || (keyCode == GLFW.GLFW_KEY_Z && hasShiftDown()))) {
            this.redo();
            return true;
        }
        if (hasControlDown() && keyCode == GLFW.GLFW_KEY_D) {
            this.duplicateSelected();
            return true;
        }
        if (hasControlDown() && keyCode == GLFW.GLFW_KEY_C) {
            this.copySelected();
            return true;
        }
        if (hasControlDown() && keyCode == GLFW.GLFW_KEY_V) {
            this.pasteCopied();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_DELETE || keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            this.deleteSelected();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_LEFT) {
            this.nudgeSelected(-1, 0, 0, 0);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_RIGHT) {
            this.nudgeSelected(1, 0, 0, 0);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_UP) {
            this.nudgeSelected(0, -1, 0, 0);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_DOWN) {
            this.nudgeSelected(0, 1, 0, 0);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        this.cancel();
    }

    private void renderTopBar(GuiGraphics graphics, DisplayTheme theme) {
        graphics.fill(MARGIN, MARGIN, this.width - MARGIN, MARGIN + HEADER_H, withAlpha(theme.tabBg, 236));
        graphics.fill(MARGIN, MARGIN, MARGIN + 4, MARGIN + HEADER_H, theme.accent);
        graphics.fill(MARGIN, MARGIN + HEADER_H - 1, this.width - MARGIN, MARGIN + HEADER_H, withAlpha(theme.border, 210));
        final int titleMaxW = this.headerTitleMaxWidth(this.compactHeaderTools);
        this.drawFittedString(graphics, "Custom Screen Studio", MARGIN + 12, MARGIN + 8, titleMaxW, theme.text);
        this.drawFittedString(graphics,
            this.currentProfileName() + "  |  " + (this.hasUnsavedChanges() ? "Unsaved draft" : "Saved draft"),
            MARGIN + 12,
                MARGIN + 20,
            titleMaxW,
                this.hasUnsavedChanges() ? theme.warn : theme.dim);
    }

    private void renderLeftRail(GuiGraphics graphics, int mouseX, int mouseY, DisplayTheme theme) {
        graphics.fill(this.leftX, this.stageY, this.leftX + this.leftW, this.stageY + this.stageH, withAlpha(theme.tabBg, 222));
        drawBorder(graphics, this.leftX, this.stageY, this.leftW, this.stageH, theme.border);
        graphics.fill(this.leftX + 6, this.stageY + 6, this.leftX + this.leftW - 6, this.stageY + 62, withAlpha(theme.graphBg, 180));
        graphics.fill(this.leftX + 6, this.stageY + 6, this.leftX + 10, this.stageY + 62, theme.accent);
        drawBorder(graphics, this.leftX + 6, this.stageY + 6, this.leftW - 12, 56, withAlpha(theme.border, 190));
        graphics.drawString(this.font, "Profiles", this.leftX + 10, this.stageY + 6, theme.text, false);
        graphics.drawString(this.font, this.profilePositionLabel(), this.leftX + this.leftW - 54, this.stageY + 6, theme.dim, false);
        graphics.fill(this.leftX + 8, this.stageY + 64, this.leftX + this.leftW - 8, this.stageY + 65, withAlpha(theme.border, 170));
        graphics.drawString(this.font, "Builder", this.leftX + 10, this.stageY + 66, theme.text, false);
        final int chipY = this.stageY + 102;
        final int chipGap = 3;
        final int chipW = (this.leftW - 20 - chipGap * 2) / 3;
        for (int index = 0; index < RailMode.values().length; ++index) {
            final RailMode mode = RailMode.values()[index];
            final int chipX = this.leftX + 10 + index * (chipW + chipGap);
            final boolean active = mode == this.railMode;
            final int fill = active ? withAlpha(theme.accent, 210) : withAlpha(theme.bg, 120);
            graphics.fill(chipX, chipY, chipX + chipW, chipY + 14, fill);
            drawBorder(graphics, chipX, chipY, chipW, 14, active ? theme.accent : theme.border);
            drawCentered(graphics, mode.labelForWidth(this.font, chipW), chipX, chipY + 3, chipW, active ? 0xFFFFFFFF : theme.dim);
        }
        final String repairSummary = this.layoutHealthSummary();
        final boolean needsRepair = this.needsLayoutRepair();
        if (needsRepair) {
            final int repairY = this.stageY + 120;
            final int repairH = this.repairBannerHeight();
            graphics.fill(this.leftX + 8, repairY, this.leftX + this.leftW - 8, repairY + repairH, withAlpha(theme.warn, 72));
            graphics.fill(this.leftX + 8, repairY, this.leftX + 12, repairY + repairH, theme.warn);
            drawBorder(graphics, this.leftX + 8, repairY, this.leftW - 16, repairH, theme.warn);
            this.drawFittedString(graphics, repairSummary, this.leftX + 16, repairY + 5, this.leftW - 30, theme.warn);
            this.drawFittedString(graphics, "Click to inspect first repair", this.leftX + 16, repairY + 17, this.leftW - 30, theme.dim);
        }
        final int entryY = this.railEntryStartY();
        if (this.railMode == RailMode.SOURCES) {
            this.renderSourceCards(graphics, mouseX, mouseY, theme, entryY);
        }
        else if (this.railMode == RailMode.SECTIONS) {
            this.renderSectionCards(graphics, mouseX, mouseY, theme, entryY);
        }
        else {
            this.renderLibraryCards(graphics, mouseX, mouseY, theme, entryY);
        }
        this.renderRailScrollHints(graphics, theme, entryY);
    }

    private void renderSourceCards(GuiGraphics graphics, int mouseX, int mouseY, DisplayTheme theme, int entryY) {
        final List<SourceSummary> sources = this.filteredSourceSummaries();
        if (sources.isEmpty()) {
            graphics.drawString(this.font, "No connected sources in scope", this.leftX + 10, entryY + 4, theme.dim, false);
            graphics.drawString(this.font, "Use Library for basic atoms.", this.leftX + 10, entryY + 18, theme.dim, false);
            return;
        }
        final int firstIndex = this.railFirstVisibleIndex();
        final int visibleRows = this.railVisibleRows();
        int y = entryY;
        for (int index = firstIndex; index < Math.min(sources.size(), firstIndex + visibleRows); ++index) {
            final SourceSummary summary = sources.get(index);
            final CustomScreenSourceCapability capability = summary.capability();
            final boolean selected = capability.getSourceId().equals(this.selectedSourceId);
            final boolean hovered = inside(mouseX, mouseY, this.leftX + 8, y, this.leftW - 16, 34);
            graphics.fill(this.leftX + 8, y, this.leftX + this.leftW - 8, y + 34, hovered || selected ? withAlpha(theme.tabSel, 218) : withAlpha(theme.bg, 104));
            graphics.fill(this.leftX + 8, y, this.leftX + 12, y + 34, selected ? theme.accent : domainColor(capability.getDomain(), theme));
            drawBorder(graphics, this.leftX + 8, y, this.leftW - 16, 34, hovered || selected ? theme.accent : theme.border);
            this.drawFittedString(graphics, capability.getSourceLabel(), this.leftX + 16, y + 5, this.leftW - 88, theme.text);
            final String familyLabel = familyLabel(capability);
            graphics.drawString(this.font, capability.getDomain().getLabel(), this.leftX + this.leftW - 58, y + 5, theme.dim, false);
            this.drawFittedString(graphics, familyLabel + " / " + capability.getSourceType(), this.leftX + 16, y + 17, this.leftW - 32, theme.dim);
            graphics.drawString(this.font, summary.sectionCount() + " sections", this.leftX + 16, y + 26, capability.isOnline() ? theme.accent : theme.warn, false);
            y += 38;
        }
    }

    private void renderSectionCards(GuiGraphics graphics, int mouseX, int mouseY, DisplayTheme theme, int entryY) {
        final List<CustomScreenSourceCapability> capabilities = this.filteredSectionCapabilities();
        if (capabilities.isEmpty()) {
            graphics.drawString(this.font, "Select a source to see sections", this.leftX + 10, entryY + 4, theme.dim, false);
            graphics.drawString(this.font, "or search all live sections.", this.leftX + 10, entryY + 18, theme.dim, false);
            return;
        }
        final int firstIndex = this.railFirstVisibleIndex();
        final int visibleRows = this.railVisibleRows();
        int y = entryY;
        for (int index = firstIndex; index < Math.min(capabilities.size(), firstIndex + visibleRows); ++index) {
            final CustomScreenSourceCapability capability = capabilities.get(index);
            final boolean hovered = inside(mouseX, mouseY, this.leftX + 8, y, this.leftW - 16, 36);
            graphics.fill(this.leftX + 8, y, this.leftX + this.leftW - 8, y + 36, hovered ? withAlpha(theme.tabSel, 220) : withAlpha(theme.bg, 108));
            graphics.fill(this.leftX + 8, y, this.leftX + 12, y + 36, capability.isHero() ? theme.accent : domainColor(capability.getDomain(), theme));
            drawBorder(graphics, this.leftX + 8, y, this.leftW - 16, 36, hovered ? theme.accent : theme.border);
            final String sectionTier = sectionTierLabel(capability);
            this.drawFittedString(graphics, capability.getSectionLabel(), this.leftX + 16, y + 5, this.leftW - 72, theme.text);
            this.drawFittedString(graphics, sectionTier, this.leftX + this.leftW - 66, y + 5, 50, nativeSection(capability) ? theme.accent : theme.dim);
            this.drawFittedString(graphics, familyLabel(capability) + " / " + capability.getSectionRole().getLabel(), this.leftX + 16, y + 17, this.leftW - 32, theme.dim);
            this.drawFittedString(graphics, capability.getSourceId(), this.leftX + 16, y + 27, this.leftW - 32, capability.isOnline() ? theme.accent : theme.warn);
            y += 40;
        }
    }

    private void renderLibraryCards(GuiGraphics graphics, int mouseX, int mouseY, DisplayTheme theme, int entryY) {
        final List<CustomScreenModuleContract> entries = this.filteredContracts();
        final int firstIndex = this.railFirstVisibleIndex();
        final int visibleRows = this.railVisibleRows();
        int y = entryY;
        for (int index = firstIndex; index < Math.min(entries.size(), firstIndex + visibleRows); ++index) {
            final CustomScreenModuleContract contract = entries.get(index);
            final boolean hovered = mouseX >= this.leftX + 8 && mouseX <= this.leftX + this.leftW - 8
                    && mouseY >= y && mouseY <= y + 18;
            final int fill = hovered ? withAlpha(theme.tabSel, 210) : withAlpha(theme.bg, 96);
            graphics.fill(this.leftX + 8, y, this.leftX + this.leftW - 8, y + 18, fill);
            drawBorder(graphics, this.leftX + 8, y, this.leftW - 16, 18, hovered ? theme.accent : theme.border);
            this.drawFittedString(graphics, contract.getDisplayName(), this.leftX + 14, y + 4, this.leftW - 82, theme.text);
            graphics.drawString(this.font, contract.getDomain().getLabel(), this.leftX + this.leftW - 56, y + 4, theme.dim, false);
            y += 20;
        }
    }

    private void renderRailScrollHints(GuiGraphics graphics, DisplayTheme theme, int entryY) {
        final int visibleRows = this.railVisibleRows();
        final int entryCount = this.railEntryCount();
        final int firstIndex = this.railFirstVisibleIndex();
        if (entryCount <= visibleRows) {
            return;
        }
        final int bottomY = this.railListBottom();
        graphics.fill(this.leftX + 8, entryY - 10, this.leftX + this.leftW - 8, entryY - 9, withAlpha(theme.border, 145));
        graphics.fill(this.leftX + 8, bottomY - 1, this.leftX + this.leftW - 8, bottomY, withAlpha(theme.border, 145));
        if (firstIndex > 0) {
            this.drawFittedString(graphics, "Scroll for earlier sources", this.leftX + 12, entryY - 8, this.leftW - 24, theme.dim);
        }
        if (firstIndex + visibleRows < entryCount) {
            this.drawFittedString(graphics, "Scroll for more", this.leftX + 12, bottomY - 12, this.leftW - 24, theme.dim);
        }
    }

    private int railEntryCount() {
        if (this.railMode == RailMode.SOURCES) {
            return this.filteredSourceSummaries().size();
        }
        if (this.railMode == RailMode.SECTIONS) {
            return this.filteredSectionCapabilities().size();
        }
        return this.filteredContracts().size();
    }

    private int railVisibleRows() {
        return Math.max(1, (this.railListBottom() - this.railEntryStartY()) / this.railRowHeight());
    }

    private int railFirstVisibleIndex() {
        final int maxOffset = Math.max(0, this.railEntryCount() - this.railVisibleRows());
        this.railScrollOffset = Math.max(0, Math.min(maxOffset, this.railScrollOffset));
        return this.railScrollOffset;
    }

    private int railRowHeight() {
        if (this.railMode == RailMode.SOURCES) {
            return 38;
        }
        if (this.railMode == RailMode.SECTIONS) {
            return 40;
        }
        return 20;
    }

    private int railListBottom() {
        return this.stageY + this.stageH - 14;
    }

    private int railEntryStartY() {
        return this.needsLayoutRepair() ? this.stageY + 120 + this.repairBannerHeight() + 8 : this.stageY + 130;
    }

    private void renderStage(GuiGraphics graphics, int mouseX, int mouseY, DisplayTheme theme) {
        graphics.fill(this.stageX, this.stageY, this.stageX + this.stageW, this.stageY + this.stageH, withAlpha(theme.graphBg, 218));
        drawBorder(graphics, this.stageX, this.stageY, this.stageW, this.stageH, theme.border);
        graphics.fill(this.stageX, this.stageY, this.stageX + this.stageW, this.stageY + 22, withAlpha(theme.tabBg, 238));
        graphics.fill(this.stageX, this.stageY + 21, this.stageX + this.stageW, this.stageY + 22, withAlpha(theme.accent, 150));
        graphics.drawString(this.font, "Stage", this.stageX + 10, this.stageY + 7, theme.text, false);
        final String variantLabel = this.activeVariant.getSerializedName() + "  " + this.gridCols + "x" + this.gridRows;
        final int variantW = Math.min(104, Math.max(56, this.font.width(variantLabel) + 4));
        this.drawFittedString(graphics, variantLabel, this.stageX + this.stageW - variantW - 8, this.stageY + 6, variantW, theme.dim);
        final int profileX = this.stageX + 48;
        final int profileMaxW = Math.max(24, this.stageX + this.stageW - variantW - 18 - profileX);
        this.drawFittedString(graphics, this.currentProfileName(), profileX, this.stageY + 7, profileMaxW, theme.dim);

        if (this.snapToGrid || this.helpMode) {
            for (int column = 0; column <= this.gridCols; ++column) {
                final int x = this.gridOriginX + column * this.cellSize;
                graphics.fill(x, this.gridOriginY, x + 1, this.gridOriginY + this.gridRows * this.cellSize, withAlpha(theme.grid, 70));
            }
            for (int row = 0; row <= this.gridRows; ++row) {
                final int y = this.gridOriginY + row * this.cellSize;
                graphics.fill(this.gridOriginX, y, this.gridOriginX + this.gridCols * this.cellSize, y + 1, withAlpha(theme.grid, 70));
            }
        }
        drawBorder(graphics, this.gridOriginX, this.gridOriginY, this.gridCols * this.cellSize, this.gridRows * this.cellSize, withAlpha(theme.border, 180));

        final List<ModuleView> visibleModules = this.resolvedModules();
        for (ModuleView moduleView : visibleModules) {
            if (moduleView.hidden()) {
                this.renderHiddenModuleGhost(graphics, moduleView, theme);
            }
            else {
                CustomScreenModuleRenderer.renderStudio(graphics,
                        this.font,
                        moduleView.renderX,
                        moduleView.renderY,
                        moduleView.renderW,
                        moduleView.renderH,
                        theme,
                        moduleView.module(),
                        this.availableSourceIds(),
                        moduleView.module().getId().equals(this.selectedModuleId));
            }
            this.renderModuleOriginBadge(graphics, moduleView, theme);
            if (moduleView.module().getId().equals(this.selectedModuleId)) {
                graphics.fill(moduleView.renderX + moduleView.renderW - 8,
                        moduleView.renderY + moduleView.renderH - 8,
                        moduleView.renderX + moduleView.renderW - 1,
                        moduleView.renderY + moduleView.renderH - 1,
                        theme.accent);
            }
        }

        if (visibleModules.isEmpty()) {
            drawCentered(graphics, "Add modules from the left rail to start composing.", this.stageX, this.stageY + this.stageH / 2 - 5, this.stageW, theme.dim);
        }
        if (this.helpMode) {
            graphics.drawString(this.font, "Drag modules to move them. Drag the corner square to resize.", this.stageX + 10, this.stageY + this.stageH - 18, theme.dim, false);
        }
    }

    private void renderInspector(GuiGraphics graphics, DisplayTheme theme) {
        this.layoutInspectorWidgets();
        graphics.fill(this.inspectorX, this.stageY, this.inspectorX + this.inspectorW, this.stageY + this.stageH, withAlpha(theme.tabBg, 222));
        drawBorder(graphics, this.inspectorX, this.stageY, this.inspectorW, this.stageH, theme.border);
        graphics.fill(this.inspectorX, this.stageY, this.inspectorX + this.inspectorW, this.stageY + 22, withAlpha(theme.graphBg, 178));
        graphics.fill(this.inspectorX, this.stageY + 21, this.inspectorX + this.inspectorW, this.stageY + 22, withAlpha(theme.accent, 125));
        graphics.drawString(this.font, "Inspector", this.inspectorX + 10, this.stageY + 6, theme.text, false);

        final CustomModuleInstance selectedModule = this.getSelectedModule();
        if (selectedModule == null) {
            graphics.drawString(this.font, "No module selected", this.inspectorX + 10, this.stageY + 34, theme.dim, false);
            this.drawFittedString(graphics, "Click a module on stage or add one from the left rail.", this.inspectorX + 10, this.stageY + 48, this.inspectorW - 20, theme.dim);
            return;
        }
        final ModuleView view = this.findResolvedView(selectedModule.getId());
        final CustomScreenModuleContract contract = CustomScreenModuleCatalog.getContract(selectedModule.getModuleType());
        final CustomScreenModuleRenderer.BindingSummary binding = contract != null
            ? CustomScreenModuleRenderer.summarizeBinding(contract, selectedModule, this.availableSourceIds(), this.panel.getSourceRegistry())
                : new CustomScreenModuleRenderer.BindingSummary(CustomScreenModuleRenderer.BindingStatus.UNRESOLVED, "Missing", "Unknown contract", theme.warn);

        final int selectedTitleColor = view != null && view.hidden() ? theme.warn : theme.text;
        final CompoundTag selectedConfig = selectedModule.getModuleConfig();
        final String sourceId = selectedConfig.getString("sourceId");
        final String ownerScreen = selectedConfig.getString("ownerScreen");
        final String sectionLabel = selectedConfig.getString("sectionLabel");
        this.drawFittedString(graphics, contract != null ? contract.getDisplayName() : selectedModule.getModuleType(), this.inspectorX + 10, this.stageY + 28, this.inspectorW - 20, selectedTitleColor);
        this.drawFittedString(graphics,
            !sourceId.isEmpty() ? "Source: " + sourceId + (ownerScreen.isEmpty() ? "" : " / " + ownerScreen) : selectedModule.getId(),
            this.inspectorX + 10,
            this.stageY + 42,
            this.inspectorW - 20,
            theme.dim);
        this.drawFittedString(graphics, sectionLabel.isEmpty() ? "Binding" : "Binding / " + sectionLabel, this.inspectorX + 10, this.stageY + 56, this.inspectorW - 20, theme.dim);
        graphics.fill(this.inspectorX + 10, this.stageY + 68, this.inspectorX + this.inspectorW - 10, this.stageY + 86, withAlpha(binding.accentColor(), 210));
        drawBorder(graphics, this.inspectorX + 10, this.stageY + 68, this.inspectorW - 20, 18, binding.accentColor());
        this.drawFittedString(graphics, binding.label() + ": " + binding.detail(), this.inspectorX + 16, this.stageY + 74, this.inspectorW - 32, 0xFFFFFFFF);

        final int actionY = this.inspectorActionY();
        final int bindingBottom = actionY - 10;
        final List<RepairEntry> selectedRepairs = this.repairEntriesForModule(selectedModule.getId());
        int bindingRowY = this.stageY + 92;
        if (!selectedRepairs.isEmpty()) {
            bindingRowY = this.renderInspectorRepairBlock(graphics, theme, selectedRepairs, bindingRowY, bindingBottom);
        }
        int hiddenBindings = 0;
        if (contract != null) {
            for (String field : contract.getBindingFields()) {
                if (bindingRowY + 18 > bindingBottom) {
                    hiddenBindings++;
                    continue;
                }
                final CustomModuleBinding fieldBinding = selectedModule.resolveBinding(field);
                final CustomScreenModuleRenderer.BindingSummary fieldSummary = CustomScreenModuleRenderer.summarizeBindingField(fieldBinding, this.availableSourceIds(), this.panel.getSourceRegistry());
                graphics.fill(this.inspectorX + 10, bindingRowY, this.inspectorX + this.inspectorW - 10, bindingRowY + 18, withAlpha(theme.bg, 128));
                drawBorder(graphics, this.inspectorX + 10, bindingRowY, this.inspectorW - 20, 18, fieldSummary.accentColor());
                this.drawFittedString(graphics, this.bindingFieldLabel(field), this.inspectorX + 14, bindingRowY + 5, 68, theme.text);
                this.drawFittedString(graphics, fieldSummary.label() + " " + fieldSummary.detail(), this.inspectorX + 88, bindingRowY + 5, this.inspectorW - 104, theme.dim);
                bindingRowY += 20;
            }
        }
        if (hiddenBindings > 0) {
            this.drawFittedString(graphics, "+" + hiddenBindings + " more bindings; resize the GUI or select picker rows", this.inspectorX + 10, Math.max(this.stageY + 92, actionY - 20), this.inspectorW - 20, theme.warn);
        }

        final int responsiveY = Math.max(bindingRowY + 6, this.stageY + 126);
        if (responsiveY + 24 <= actionY - 4) {
            graphics.drawString(this.font, "Responsive", this.inspectorX + 10, responsiveY, theme.dim, false);
            if (contract != null) {
                this.drawFittedString(graphics,
                        "Min " + contract.getMinAuthoringWidth() + "x" + contract.getMinAuthoringHeight() + "  |  Hero " + contract.getHeroPolicy().name().toLowerCase(Locale.ROOT),
                        this.inspectorX + 10,
                        responsiveY + 12,
                        this.inspectorW - 20,
                        theme.dim);
            }
        }

        final CustomModuleInstance.GridBounds visibleBounds = view != null ? view.bounds() : selectedModule.getBounds();
        final int detailY = actionY + 164;
        if (detailY + 52 <= this.stageY + this.stageH - 6) {
            this.drawFittedString(graphics,
                    "Bounds " + visibleBounds.getX() + "," + visibleBounds.getY() + "  " + visibleBounds.getWidth() + "x" + visibleBounds.getHeight(),
                    this.inspectorX + 10,
                    detailY,
                    this.inspectorW - 20,
                    theme.dim);
            this.drawFittedString(graphics, "Scale: " + selectedModule.getScale().getSerializedName(), this.inspectorX + 10, detailY + 14, this.inspectorW - 20, theme.dim);
            this.drawFittedString(graphics, "Family: " + this.activeVariant.getSerializedName(), this.inspectorX + 10, detailY + 28, this.inspectorW - 20, theme.dim);
            this.drawFittedString(graphics, "Style: " + this.currentStyleLabel() + "  |  " + this.visibilityLabel(), this.inspectorX + 10, detailY + 42, this.inspectorW - 20, theme.dim);
        }
    }

    private void renderFooter(GuiGraphics graphics, DisplayTheme theme) {
        final int footerY = this.height - FOOTER_H;
        final String healthSummary = this.layoutHealthSummary();
        graphics.fill(MARGIN, footerY, this.width - MARGIN, this.height - MARGIN, withAlpha(theme.tabBg, 232));
        graphics.fill(MARGIN, footerY, this.width - MARGIN, footerY + 1, theme.border);
        this.drawFittedString(graphics,
            this.draftLayout.getModules().size() + " modules  |  " + this.activeVariant.getSerializedName() + "  |  " + healthSummary + "  |  " + (this.hasUnsavedChanges() ? "unsaved" : "saved") + (this.statusMessage.isEmpty() ? "" : "  |  " + this.statusMessage),
                MARGIN + 10,
                footerY + 6,
            this.width - MARGIN * 2 - 20,
                theme.dim);
    }

    private void renderPresetModal(GuiGraphics graphics, int mouseX, int mouseY, DisplayTheme theme) {
        graphics.fill(0, 0, this.width, this.height, 0x88000000);
        final int modalW = Math.min(420, this.width - 80);
        final int modalH = Math.min(250, this.height - 80);
        final int x = (this.width - modalW) / 2;
        final int y = (this.height - modalH) / 2;
        graphics.fill(x, y, x + modalW, y + modalH, withAlpha(theme.tabBg, 242));
        drawBorder(graphics, x, y, modalW, modalH, theme.accent);
        graphics.fill(x, y, x + modalW, y + 4, theme.accent);
        graphics.drawString(this.font, "Preset Apply", x + 12, y + 10, theme.text, false);
        this.drawFittedString(graphics, "Focused source: " + (this.activeStudioSourceScope().isEmpty() ? "none" : this.activeStudioSourceScope()), x + 12, y + 24, modalW - 220, theme.dim);
        final String compatibilityMessage = this.selectedPresetCompatibilityMessage();
        if (!compatibilityMessage.isEmpty()) {
            this.drawFittedString(graphics, compatibilityMessage, x + 206, y + 24, modalW - 220, theme.warn);
        }
        final List<CustomScreenPresetCatalog.PresetDefinition> presets = CustomScreenPresetCatalog.allPresets();
        final int visibleRows = this.presetVisibleRowCount(modalH);
        final int firstIndex = this.presetFirstVisibleIndex(modalH);
        int rowY = y + 42;
        for (int index = firstIndex; index < Math.min(presets.size(), firstIndex + visibleRows); ++index) {
            final CustomScreenPresetCatalog.PresetDefinition preset = presets.get(index);
            final boolean selected = preset.id().equals(this.selectedPresetId);
            final boolean compatible = this.presetCompatibleWithFocus(preset);
            final boolean hovered = inside(mouseX, mouseY, x + 10, rowY, modalW - 20, 34);
            graphics.fill(x + 10, rowY, x + modalW - 10, rowY + 34, hovered || selected ? withAlpha(theme.tabSel, 214) : withAlpha(theme.bg, 130));
            drawBorder(graphics, x + 10, rowY, modalW - 20, 34, !compatible ? theme.warn : selected ? theme.accent : theme.border);
            this.drawFittedString(graphics, preset.label(), x + 18, rowY + 6, modalW - 36, compatible ? theme.text : theme.warn);
            this.drawFittedString(graphics, compatible ? preset.description() : "Needs " + this.presetFamilyLabel(preset), x + 18, rowY + 19, modalW - 36, compatible ? theme.dim : theme.warn);
            rowY += 38;
        }
        if (firstIndex > 0) {
            graphics.drawString(this.font, "Scroll up", x + modalW - 76, y + 10, theme.dim, false);
        }
        if (firstIndex + visibleRows < presets.size()) {
            graphics.drawString(this.font, "Scroll down", x + modalW - 88, y + modalH - 42, theme.dim, false);
        }
        this.presetReplaceX = x + 16;
        this.presetReplaceY = y + modalH - 28;
        this.presetAddX = x + 96;
        this.presetAddY = this.presetReplaceY;
        this.presetCancelX = x + 176;
        this.presetCancelY = this.presetReplaceY;
        final boolean canApplyPreset = this.selectedPresetCanApply();
        this.drawModalButton(graphics, this.presetReplaceX, this.presetReplaceY, 68, 16, "Replace", theme, canApplyPreset);
        this.drawModalButton(graphics, this.presetAddX, this.presetAddY, 68, 16, "Add", theme, canApplyPreset);
        this.drawModalButton(graphics, this.presetCancelX, this.presetCancelY, 68, 16, "Cancel", theme, true);
    }

    private void renderSourcePickerModal(GuiGraphics graphics, int mouseX, int mouseY, DisplayTheme theme) {
        graphics.fill(0, 0, this.width, this.height, 0x88000000);
        final int modalW = Math.min(460, this.width - 80);
        final int modalH = Math.min(280, this.height - 80);
        final int x = (this.width - modalW) / 2;
        final int y = (this.height - modalH) / 2;
        graphics.fill(x, y, x + modalW, y + modalH, withAlpha(theme.tabBg, 244));
        drawBorder(graphics, x, y, modalW, modalH, theme.accent);
        graphics.fill(x, y, x + modalW, y + 4, theme.accent);
        graphics.drawString(this.font, "Choose Source", x + 12, y + 10, theme.text, false);
        graphics.drawString(this.font, this.bindingFieldLabel(this.sourcePickerField), x + 12, y + 24, theme.dim, false);

        final List<SourcePickerEntry> entries = this.sourcePickerEntries();
        final int visibleRows = this.sourcePickerVisibleRowCount(modalH);
        final int firstIndex = this.sourcePickerFirstVisibleIndex(modalH);
        final int rowHeight = this.sourcePickerRowHeight();
        int rowY = y + 44;
        for (int index = firstIndex; index < Math.min(entries.size(), firstIndex + visibleRows); ++index) {
            final SourcePickerEntry entry = entries.get(index);
            final boolean hovered = inside(mouseX, mouseY, x + 10, rowY, modalW - 20, rowHeight - 4);
            final int rowColor = hovered ? withAlpha(theme.tabSel, 220) : withAlpha(theme.bg, 128);
            final String statusLabel = entry.compatible() ? "compatible" : entry.stale() ? "missing" : "wrong family";
            graphics.fill(x + 10, rowY, x + modalW - 10, rowY + rowHeight - 4, rowColor);
            drawBorder(graphics, x + 10, rowY, modalW - 20, rowHeight - 4, entry.compatible() ? theme.border : theme.warn);
            this.drawFittedString(graphics, entry.label(), x + 18, rowY + 5, Math.max(40, modalW - 154), entry.stale() ? theme.warn : theme.text);
            this.drawFittedString(graphics, statusLabel, x + modalW - 132, rowY + 5, Math.max(40, Math.min(114, modalW - 36)), entry.compatible() ? theme.accent : theme.warn);
            this.drawFittedString(graphics, entry.sourceId().isEmpty() ? "clear binding" : entry.sourceId() + " / " + entry.sourceType(), x + 18, rowY + 18, modalW - 36, theme.dim);
            if (!entry.compatible()) {
                this.drawFittedString(graphics, entry.compatibilityLabel(), x + 18, rowY + 31, modalW - 36, theme.warn);
            }
            rowY += rowHeight;
        }
        if (firstIndex > 0) {
            graphics.drawString(this.font, "Scroll up", x + modalW - 76, y + 10, theme.dim, false);
        }
        if (firstIndex + visibleRows < entries.size()) {
            graphics.drawString(this.font, "Scroll down", x + modalW - 88, y + modalH - 42, theme.dim, false);
        }
        this.sourcePickerCancelX = x + 16;
        this.sourcePickerCancelY = y + modalH - 28;
        this.drawModalButton(graphics, this.sourcePickerCancelX, this.sourcePickerCancelY, 68, 16, "Cancel", theme, true);
    }

    private void renderSlotPickerModal(GuiGraphics graphics, int mouseX, int mouseY, DisplayTheme theme) {
        graphics.fill(0, 0, this.width, this.height, 0x88000000);
        final int modalW = Math.min(560, this.width - 60);
        final int modalH = Math.min(360, this.height - 60);
        final int x = (this.width - modalW) / 2;
        final int y = (this.height - modalH) / 2;
        graphics.fill(x, y, x + modalW, y + modalH, withAlpha(theme.tabBg, 244));
        drawBorder(graphics, x, y, modalW, modalH, theme.accent);
        graphics.fill(x, y, x + modalW, y + 4, theme.accent);
        graphics.drawString(this.font, "Choose Source Output", x + 12, y + 10, theme.text, false);
        graphics.drawString(this.font, this.bindingFieldLabel(this.slotPickerField), x + 12, y + 24, theme.dim, false);

        final List<SlotPickerEntry> entries = this.slotPickerEntries();
        final int visibleRows = this.slotPickerVisibleRowCount(modalH);
        final int firstIndex = this.slotPickerFirstVisibleIndex(modalH);
        final int rowHeight = this.slotPickerRowHeight();
        int rowY = y + 44;
        for (int index = firstIndex; index < Math.min(entries.size(), firstIndex + visibleRows); ++index) {
            final SlotPickerEntry entry = entries.get(index);
            if (!entry.selectable()) {
                graphics.fill(x + 10, rowY + 12, x + modalW - 10, rowY + 13, theme.border);
                graphics.drawString(this.font, entry.category().toUpperCase(Locale.ROOT), x + 18, rowY + 5, theme.accent, false);
                rowY += rowHeight;
                continue;
            }
            final boolean hovered = inside(mouseX, mouseY, x + 10, rowY, modalW - 20, rowHeight - 4);
            final int borderColor = entry.sourceSpecific() ? theme.accent : theme.border;
            graphics.fill(x + 10, rowY, x + modalW - 10, rowY + rowHeight - 4, hovered ? withAlpha(theme.tabSel, 220) : withAlpha(theme.bg, 150));
            drawBorder(graphics, x + 10, rowY, modalW - 20, rowHeight - 4, borderColor);
            this.drawFittedString(graphics, entry.label(), x + 18, rowY + 5, modalW - 174, theme.text);
            this.drawFittedString(graphics, entry.category(), x + modalW - 146, rowY + 5, 128, theme.accent);
            final String sourceLine = entry.sourceId().isEmpty()
                    ? "Any source / semantic fallback"
                    : entry.sourceLabel() + " / " + entry.sourceType() + " / " + entry.sourceId();
            this.drawFittedString(graphics, sourceLine, x + 18, rowY + 18, modalW - 36, theme.dim);
            this.drawFittedString(graphics, entry.detail(), x + 18, rowY + 31, modalW - 36, theme.dim);
            rowY += rowHeight;
        }
        if (firstIndex > 0) {
            graphics.drawString(this.font, "Scroll up", x + modalW - 76, y + 10, theme.dim, false);
        }
        if (firstIndex + visibleRows < entries.size()) {
            graphics.drawString(this.font, "Scroll down", x + modalW - 88, y + modalH - 42, theme.dim, false);
        }
        this.slotPickerCancelX = x + 16;
        this.slotPickerCancelY = y + modalH - 28;
        this.drawModalButton(graphics, this.slotPickerCancelX, this.slotPickerCancelY, 68, 16, "Cancel", theme, true);
    }

    private void renderHelpOverlay(GuiGraphics graphics, DisplayTheme theme) {
        final int overlayW = 250;
        final int overlayH = 74;
        final int x = this.stageX + this.stageW - overlayW - 10;
        final int y = this.stageY + 28;
        graphics.fill(x, y, x + overlayW, y + overlayH, withAlpha(theme.bg, 232));
        drawBorder(graphics, x, y, overlayW, overlayH, theme.accent);
        graphics.drawString(this.font, "Studio Help", x + 10, y + 8, theme.text, false);
        graphics.drawString(this.font, "Click a left-rail card to add a module.", x + 10, y + 24, theme.dim, false);
        graphics.drawString(this.font, "Drag on stage to move. Drag the corner square to resize.", x + 10, y + 36, theme.dim, false);
        graphics.drawString(this.font, "Ctrl+Z / Ctrl+Y undo and redo. Delete removes a selected module.", x + 10, y + 48, theme.dim, false);
    }

    private boolean handleLeftRailClick(int mouseX, int mouseY) {
        final int chipY = this.stageY + 102;
        final int chipGap = 3;
        final int chipW = (this.leftW - 20 - chipGap * 2) / 3;
        for (int index = 0; index < RailMode.values().length; ++index) {
            final int chipX = this.leftX + 10 + index * (chipW + chipGap);
            if (inside(mouseX, mouseY, chipX, chipY, chipW, 14)) {
                this.railMode = RailMode.values()[index];
                this.railScrollOffset = 0;
                return true;
            }
        }
        final boolean needsRepair = this.needsLayoutRepair();
        if (needsRepair && inside(mouseX, mouseY, this.leftX + 8, this.stageY + 120, this.leftW - 16, this.repairBannerHeight())) {
            final RepairEntry entry = this.firstRepairEntry();
            if (entry != null) {
                this.selectedModuleId = entry.moduleId();
                this.statusMessage = "Repair " + this.bindingFieldLabel(entry.field()) + ": " + entry.label();
                this.updateButtonStates();
            }
            return true;
        }
        int entryY = this.railEntryStartY();
        if (this.railMode == RailMode.SOURCES) {
            final List<SourceSummary> sources = this.filteredSourceSummaries();
            final int firstIndex = this.railFirstVisibleIndex();
            final int visibleRows = this.railVisibleRows();
            for (int index = firstIndex; index < Math.min(sources.size(), firstIndex + visibleRows); ++index) {
                final SourceSummary summary = sources.get(index);
                if (inside(mouseX, mouseY, this.leftX + 8, entryY, this.leftW - 16, 34)) {
                    this.selectedSourceId = summary.capability().getSourceId();
                    this.railMode = RailMode.SECTIONS;
                    this.railScrollOffset = 0;
                    this.statusMessage = "Selected source " + this.selectedSourceId;
                    return true;
                }
                entryY += 38;
            }
        }
        else if (this.railMode == RailMode.SECTIONS) {
            final List<CustomScreenSourceCapability> capabilities = this.filteredSectionCapabilities();
            final int firstIndex = this.railFirstVisibleIndex();
            final int visibleRows = this.railVisibleRows();
            for (int index = firstIndex; index < Math.min(capabilities.size(), firstIndex + visibleRows); ++index) {
                final CustomScreenSourceCapability capability = capabilities.get(index);
                if (inside(mouseX, mouseY, this.leftX + 8, entryY, this.leftW - 16, 36)) {
                    this.addSourceCapability(capability);
                    return true;
                }
                entryY += 40;
            }
        }
        else {
            final List<CustomScreenModuleContract> entries = this.filteredContracts();
            final int firstIndex = this.railFirstVisibleIndex();
            final int visibleRows = this.railVisibleRows();
            for (int index = firstIndex; index < Math.min(entries.size(), firstIndex + visibleRows); ++index) {
                final CustomScreenModuleContract contract = entries.get(index);
                if (inside(mouseX, mouseY, this.leftX + 8, entryY, this.leftW - 16, 18)) {
                    this.addModule(contract);
                    return true;
                }
                entryY += 20;
            }
        }
        return false;
    }

    private boolean handleInspectorClick(int mouseX, int mouseY) {
        final CustomModuleInstance selected = this.getSelectedModule();
        final CustomScreenModuleContract contract = selected != null ? CustomScreenModuleCatalog.getContract(selected.getModuleType()) : null;
        if (selected == null || contract == null) {
            return false;
        }
        final int actionY = this.inspectorActionY();
        final int bindingBottom = actionY - 10;
        final List<RepairEntry> selectedRepairs = this.repairEntriesForModule(selected.getId());
        if (this.handleInspectorRepairBlockClick(mouseX, mouseY, selectedRepairs, this.stageY + 92, bindingBottom)) {
            return true;
        }
        int bindingRowY = this.inspectorRepairBlockBottom(selectedRepairs, this.stageY + 92, bindingBottom);
        for (String field : contract.getBindingFields()) {
            if (bindingRowY + 18 > bindingBottom) {
                return false;
            }
            if (inside(mouseX, mouseY, this.inspectorX + 10, bindingRowY, this.inspectorW - 20, 18)) {
                if (field.endsWith("Source")) {
                    this.openSourcePicker(field);
                }
                else {
                    this.openSlotPicker(field);
                }
                return true;
            }
            bindingRowY += 20;
        }
        return false;
    }

    private void openRepairPicker(RepairEntry repair) {
        if (repair == null || repair.field().isEmpty()) {
            this.statusMessage = "Select the module and remove or replace it";
            return;
        }
        if (repair.field().endsWith("Source")) {
            this.openSourcePicker(repair.field());
        }
        else {
            this.openSlotPicker(repair.field());
        }
    }

    private boolean repairCanClear(RepairEntry repair) {
        return repair != null && !repair.field().isEmpty();
    }

    private void clearRepairBinding(RepairEntry repair) {
        if (!this.repairCanClear(repair)) {
            return;
        }
        final CustomModuleInstance module = this.draftLayout.findModule(repair.moduleId());
        if (module == null) {
            return;
        }
        final CustomModuleBinding binding = module.resolveBinding(repair.field());
        if (binding == null) {
            return;
        }
        this.captureUndo();
        if (repair.field().endsWith("Source")) {
            binding.setSourceId("");
            binding.setSourceTypeHint("");
            binding.setSourcePosHint(Long.MIN_VALUE);
            final CompoundTag config = module.getModuleConfig();
            config.putString("sourceId", "");
            config.putString("sourceType", "");
            config.putString("sourceLabel", "");
            config.putBoolean("sourceOnline", false);
            module.setModuleConfig(config);
        }
        else {
            binding.setSlot("");
            binding.setMetricLabel("");
        }
        binding.normalize();
        module.normalize();
        this.statusMessage = "Cleared " + this.bindingFieldLabel(repair.field());
        this.syncDraftLayout();
    }

    private boolean handlePresetModalClick(int mouseX, int mouseY, int button) {
        if (!this.presetModalOpen || button != 0) {
            return false;
        }
        final int modalW = Math.min(420, this.width - 80);
        final int modalH = Math.min(250, this.height - 80);
        final int x = (this.width - modalW) / 2;
        final int y = (this.height - modalH) / 2;
        final List<CustomScreenPresetCatalog.PresetDefinition> presets = CustomScreenPresetCatalog.allPresets();
        final int visibleRows = this.presetVisibleRowCount(modalH);
        final int firstIndex = this.presetFirstVisibleIndex(modalH);
        int rowY = y + 42;
        for (int index = firstIndex; index < Math.min(presets.size(), firstIndex + visibleRows); ++index) {
            final CustomScreenPresetCatalog.PresetDefinition preset = presets.get(index);
            if (inside(mouseX, mouseY, x + 10, rowY, modalW - 20, 34)) {
                this.selectedPresetId = preset.id();
                return true;
            }
            rowY += 38;
        }
        if (inside(mouseX, mouseY, this.presetReplaceX, this.presetReplaceY, 68, 16) && this.selectedPresetId != null) {
            if (!this.selectedPresetCanApply()) {
                this.statusMessage = this.selectedPresetCompatibilityMessage();
                return true;
            }
            this.applySelectedPreset(CustomScreenPresetCatalog.ApplyMode.REPLACE);
            return true;
        }
        if (inside(mouseX, mouseY, this.presetAddX, this.presetAddY, 68, 16) && this.selectedPresetId != null) {
            if (!this.selectedPresetCanApply()) {
                this.statusMessage = this.selectedPresetCompatibilityMessage();
                return true;
            }
            this.applySelectedPreset(CustomScreenPresetCatalog.ApplyMode.ADD);
            return true;
        }
        if (inside(mouseX, mouseY, this.presetCancelX, this.presetCancelY, 68, 16) || !inside(mouseX, mouseY, x, y, modalW, modalH)) {
            this.presetModalOpen = false;
            this.updateButtonStates();
            return true;
        }
        return false;
    }

    private boolean handleSourcePickerModalClick(int mouseX, int mouseY, int button) {
        if (!this.sourcePickerOpen || button != 0) {
            return false;
        }
        final int modalW = Math.min(460, this.width - 80);
        final int modalH = Math.min(280, this.height - 80);
        final int x = (this.width - modalW) / 2;
        final int y = (this.height - modalH) / 2;
        final List<SourcePickerEntry> entries = this.sourcePickerEntries();
        final int visibleRows = this.sourcePickerVisibleRowCount(modalH);
        final int firstIndex = this.sourcePickerFirstVisibleIndex(modalH);
        final int rowHeight = this.sourcePickerRowHeight();
        int rowY = y + 44;
        for (int index = firstIndex; index < Math.min(entries.size(), firstIndex + visibleRows); ++index) {
            final SourcePickerEntry entry = entries.get(index);
            if (inside(mouseX, mouseY, x + 10, rowY, modalW - 20, rowHeight - 4)) {
                if (!entry.compatible()) {
                    this.statusMessage = entry.stale() ? "Saved source is missing" : "Cannot bind: " + entry.compatibilityLabel();
                    return true;
                }
                this.selectSourceForBinding(entry);
                return true;
            }
            rowY += rowHeight;
        }
        if (inside(mouseX, mouseY, this.sourcePickerCancelX, this.sourcePickerCancelY, 68, 16) || !inside(mouseX, mouseY, x, y, modalW, modalH)) {
            this.sourcePickerOpen = false;
            this.sourcePickerField = "";
            this.updateButtonStates();
            return true;
        }
        return false;
    }

    private boolean handleSlotPickerModalClick(int mouseX, int mouseY, int button) {
        if (!this.slotPickerOpen || button != 0) {
            return false;
        }
        final int modalW = Math.min(560, this.width - 60);
        final int modalH = Math.min(360, this.height - 60);
        final int x = (this.width - modalW) / 2;
        final int y = (this.height - modalH) / 2;
        final List<SlotPickerEntry> entries = this.slotPickerEntries();
        final int visibleRows = this.slotPickerVisibleRowCount(modalH);
        final int firstIndex = this.slotPickerFirstVisibleIndex(modalH);
        final int rowHeight = this.slotPickerRowHeight();
        int rowY = y + 44;
        for (int index = firstIndex; index < Math.min(entries.size(), firstIndex + visibleRows); ++index) {
            final SlotPickerEntry entry = entries.get(index);
            if (entry.selectable() && inside(mouseX, mouseY, x + 10, rowY, modalW - 20, rowHeight - 4)) {
                this.selectSlotForBinding(entry);
                return true;
            }
            rowY += rowHeight;
        }
        if (inside(mouseX, mouseY, this.slotPickerCancelX, this.slotPickerCancelY, 68, 16) || !inside(mouseX, mouseY, x, y, modalW, modalH)) {
            this.slotPickerOpen = false;
            this.slotPickerField = "";
            this.updateButtonStates();
            return true;
        }
        return false;
    }

    private void addModule(CustomScreenModuleContract contract) {
        if (contract == null) {
            return;
        }
        this.captureUndo();
        final int columns = Math.max(1, this.gridCols - contract.getDefaultWidth());
        final int rows = Math.max(1, this.gridRows - contract.getDefaultHeight());
        final int index = this.draftLayout.getModules().size();
        final int gridX = Math.max(0, (index * 2) % columns);
        final int gridY = Math.max(0, ((index * 2) / columns) % rows);
        final CustomModuleInstance module = CustomScreenModuleCatalog.createInstance(contract.getType(), gridX, gridY);
        if (module == null) {
            return;
        }
        this.clampBounds(module.getBounds(), contract);
        this.draftLayout.addModule(module);
        this.selectedModuleId = module.getId();
        if (this.activeVariant == CustomScreenLayout.VariantFamily.COMPACT && contract.getHeroPolicy() != CustomScreenModuleContract.HeroPolicy.NO) {
            this.statusMessage = "Hero scene added; compact view hides it by default.";
        }
        this.syncDraftLayout();
    }

    private void addSourceCapability(CustomScreenSourceCapability capability) {
        if (capability == null) {
            return;
        }
        this.captureUndo();
        final CustomScreenModuleContract contract = CustomScreenModuleCatalog.getContract(capability.getModuleType());
        final int width = contract != null ? contract.getDefaultWidth() : 4;
        final int height = contract != null ? contract.getDefaultHeight() : 3;
        final int columns = Math.max(1, this.gridCols - width);
        final int rows = Math.max(1, this.gridRows - height);
        final int index = this.draftLayout.getModules().size();
        final int gridX = Math.max(0, (index * 2) % columns);
        final int gridY = Math.max(0, ((index * 2) / columns) % rows);
        final CustomModuleInstance module = capability.createModule(gridX, gridY);
        if (module == null) {
            return;
        }
        this.clampBounds(module.getBounds(), contract);
        this.draftLayout.addModule(module);
        this.selectedModuleId = module.getId();
        this.statusMessage = "Added " + capability.getSectionLabel() + " from " + capability.getSourceId();
        this.syncDraftLayout();
    }

    private void renderStudioBackdrop(GuiGraphics graphics, DisplayTheme theme) {
        graphics.fillGradient(0, 0, this.width, this.height, darken(theme.bg, 0.92f), theme.bg);
        graphics.fill(0, 0, this.width, 3, theme.accent);
        graphics.fillGradient(0, this.height / 3, this.width, this.height, withAlpha(theme.graphBg, 0), withAlpha(theme.graphBg, 96));
        graphics.fill(this.stageX - 4, this.stageY - 4, this.stageX + this.stageW + 4, this.stageY + this.stageH + 4, withAlpha(theme.accent, 26));
    }

    private void renderHiddenModuleGhost(GuiGraphics graphics, ModuleView moduleView, DisplayTheme theme) {
        final boolean selected = moduleView.module().getId().equals(this.selectedModuleId);
        final int outline = selected ? theme.warn : withAlpha(theme.border, 210);
        graphics.fill(moduleView.renderX, moduleView.renderY, moduleView.renderX + moduleView.renderW, moduleView.renderY + moduleView.renderH, withAlpha(theme.bg, 82));
        drawBorder(graphics, moduleView.renderX, moduleView.renderY, moduleView.renderW, moduleView.renderH, outline);
        graphics.fill(moduleView.renderX + 3, moduleView.renderY + 3, moduleView.renderX + moduleView.renderW - 3, moduleView.renderY + 4, withAlpha(outline, 150));
        graphics.drawString(this.font, "Hidden", moduleView.renderX + 6, moduleView.renderY + 7, selected ? theme.warn : theme.dim, false);
        final String label = moduleView.contract() != null ? moduleView.contract().getDisplayName() : moduleView.module().getModuleType();
        graphics.drawString(this.font, label, moduleView.renderX + 6, moduleView.renderY + 19, theme.dim, false);
    }

    private void renderModuleOriginBadge(GuiGraphics graphics, ModuleView moduleView, DisplayTheme theme) {
        final CompoundTag config = moduleView.module().getModuleConfig();
        if (!config.contains("sourceId") && !config.contains("sectionLabel")) {
            return;
        }
        final String section = config.getString("sectionLabel").isEmpty() ? moduleView.module().getModuleType() : config.getString("sectionLabel");
        final String source = config.getString("sourceId");
        final String label = source.isEmpty() ? section : section + " / " + source;
        final int badgeW = Math.min(moduleView.renderW - 6, Math.max(36, this.font.width(label) + 8));
        if (badgeW <= 0 || moduleView.renderH < 18) {
            return;
        }
        graphics.fill(moduleView.renderX + 3, moduleView.renderY + 3, moduleView.renderX + 3 + badgeW, moduleView.renderY + 14, withAlpha(theme.bg, 210));
        drawBorder(graphics, moduleView.renderX + 3, moduleView.renderY + 3, badgeW, 11, moduleView.binding().accentColor());
        this.drawFittedString(graphics, label, moduleView.renderX + 7, moduleView.renderY + 5, badgeW - 8, theme.text);
    }

    private void duplicateSelected() {
        final CustomModuleInstance selected = this.getSelectedModule();
        if (selected == null) {
            return;
        }
        this.captureUndo();
        final ModuleView view = this.findResolvedView(selected.getId());
        final CustomModuleInstance copy = selected.copy();
        copy.setId(selected.getModuleType() + "-copy-" + System.nanoTime());
        if (view != null) {
            final CustomModuleInstance.GridBounds bounds = view.bounds().copy();
            bounds.setX(bounds.getX() + 1);
            bounds.setY(bounds.getY() + 1);
            this.clampBounds(bounds);
            copy.setBounds(bounds);
        }
        else {
            final CustomModuleInstance.GridBounds bounds = copy.getBounds().copy();
            bounds.setX(bounds.getX() + 1);
            bounds.setY(bounds.getY() + 1);
            this.clampBounds(bounds);
            copy.setBounds(bounds);
        }
        this.draftLayout.addModule(copy);
        this.selectedModuleId = copy.getId();
        this.statusMessage = "Duplicated module";
        this.syncDraftLayout();
    }

    private void copySelected() {
        final CustomModuleInstance selected = this.getSelectedModule();
        if (selected == null) {
            return;
        }
        this.copiedModuleSnapshot = selected.save();
        this.statusMessage = "Copied " + selected.getId();
        this.updateButtonStates();
    }

    private void pasteCopied() {
        if (this.copiedModuleSnapshot == null) {
            return;
        }
        this.captureUndo();
        final CustomModuleInstance pasted = CustomModuleInstance.load(this.copiedModuleSnapshot);
        pasted.setId(pasted.getModuleType() + "-paste-" + System.nanoTime());
        final CustomModuleInstance.GridBounds bounds = pasted.getBounds().copy();
        bounds.setX(bounds.getX() + 1);
        bounds.setY(bounds.getY() + 1);
        this.clampBounds(bounds, CustomScreenModuleCatalog.getContract(pasted.getModuleType()));
        pasted.setBounds(bounds);
        this.draftLayout.addModule(pasted);
        this.selectedModuleId = pasted.getId();
        this.statusMessage = "Pasted module";
        this.syncDraftLayout();
    }

    private void deleteSelected() {
        if (this.selectedModuleId == null || this.selectedModuleId.isEmpty()) {
            return;
        }
        this.captureUndo();
        this.draftLayout.removeModule(this.selectedModuleId);
        for (CustomScreenLayout.VariantFamily family : CustomScreenLayout.VariantFamily.values()) {
            if (family == CustomScreenLayout.VariantFamily.BASE) {
                continue;
            }
            final CustomScreenLayout.VariantLayout variantLayout = this.draftLayout.getVariant(family);
            if (variantLayout == null) {
                continue;
            }
            variantLayout.removeModuleOverride(this.selectedModuleId);
            variantLayout.removeOmittedModule(this.selectedModuleId);
            if (variantLayout.isEmpty()) {
                this.draftLayout.setVariant(family, null);
            }
        }
        this.selectedModuleId = this.draftLayout.getModules().isEmpty() ? null : this.draftLayout.getModules().get(Math.max(0, this.draftLayout.getModules().size() - 1)).getId();
        this.syncDraftLayout();
    }

    private void toggleSelectedVisibility() {
        final CustomModuleInstance selected = this.getSelectedModule();
        if (selected == null) {
            return;
        }
        final CustomScreenModuleContract contract = CustomScreenModuleCatalog.getContract(selected.getModuleType());
        this.captureUndo();
        if (this.activeVariant == CustomScreenLayout.VariantFamily.BASE) {
            final CustomModuleInstance.VisibilityPolicy policy = selected.getVisibilityPolicy();
            final CustomModuleInstance.VisibilityPolicy.VisibilityMode next = policy.get(CustomScreenLayout.VariantFamily.BASE) == CustomModuleInstance.VisibilityPolicy.VisibilityMode.HIDE
                    ? CustomModuleInstance.VisibilityPolicy.VisibilityMode.SHOW
                    : CustomModuleInstance.VisibilityPolicy.VisibilityMode.HIDE;
            policy.set(CustomScreenLayout.VariantFamily.BASE, next);
            selected.setVisibilityPolicy(policy);
        }
        else {
            final CustomScreenLayout.VariantLayout variantLayout = this.draftLayout.getOrCreateVariant(this.activeVariant);
            final CustomModuleInstance.VariantOverride override = this.copySelectedOverride();
            final CustomModuleInstance.VisibilityPolicy.VisibilityMode current = this.currentVariantVisibility();
            final CustomModuleInstance.VisibilityPolicy.VisibilityMode next = current == CustomModuleInstance.VisibilityPolicy.VisibilityMode.HIDE
                    ? CustomModuleInstance.VisibilityPolicy.VisibilityMode.INHERIT
                    : CustomModuleInstance.VisibilityPolicy.VisibilityMode.HIDE;
            if (next != CustomModuleInstance.VisibilityPolicy.VisibilityMode.HIDE
                    && this.activeVariant == CustomScreenLayout.VariantFamily.COMPACT
                    && contract != null
                    && contract.getHeroPolicy() != CustomScreenModuleContract.HeroPolicy.NO) {
                this.statusMessage = "Hero scenes stay hidden in compact unless a sibling exists.";
                return;
            }
            override.setVisibility(next);
            if (override.isEmpty()) {
                variantLayout.removeModuleOverride(this.selectedModuleId);
            }
            else {
                variantLayout.putModuleOverride(this.selectedModuleId, override);
            }
            if (variantLayout.isEmpty()) {
                this.draftLayout.setVariant(this.activeVariant, null);
            }
        }
        this.statusMessage = "Visibility updated";
        this.syncDraftLayout();
    }

    private void clearSelectedVariantOverride() {
        if (this.activeVariant == CustomScreenLayout.VariantFamily.BASE || this.selectedModuleId == null) {
            return;
        }
        final CustomScreenLayout.VariantLayout variantLayout = this.draftLayout.getVariant(this.activeVariant);
        if (variantLayout == null) {
            return;
        }
        this.captureUndo();
        variantLayout.removeModuleOverride(this.selectedModuleId);
        variantLayout.removeOmittedModule(this.selectedModuleId);
        if (variantLayout.isEmpty()) {
            this.draftLayout.setVariant(this.activeVariant, null);
        }
        this.statusMessage = "Variant override cleared";
        this.syncDraftLayout();
    }

    private void nudgeSelected(int deltaX, int deltaY, int deltaW, int deltaH) {
        final ModuleView selectedView = this.findResolvedView(this.selectedModuleId);
        if (selectedView == null) {
            return;
        }
        this.captureUndo();
        final CustomModuleInstance.GridBounds bounds = selectedView.bounds().copy();
        bounds.setX(bounds.getX() + deltaX);
        bounds.setY(bounds.getY() + deltaY);
        bounds.setWidth(bounds.getWidth() + deltaW);
        bounds.setHeight(bounds.getHeight() + deltaH);
        this.clampBounds(bounds, selectedView.contract());
        this.applyBoundsChange(this.selectedModuleId, bounds);
    }

    private void beginGesture(ModuleView hit, int mouseX, int mouseY, boolean dragging, boolean resizing) {
        this.captureUndo();
        this.draggingModule = dragging;
        this.resizingModule = resizing;
        this.gestureModuleId = hit.module().getId();
        this.gestureStartMouseX = mouseX;
        this.gestureStartMouseY = mouseY;
        this.gestureStartBounds = hit.bounds().copy();
    }

    private void applyBoundsChange(String moduleId, CustomModuleInstance.GridBounds nextBounds) {
        if (moduleId == null || nextBounds == null) {
            return;
        }
        final CustomModuleInstance module = this.draftLayout.findModule(moduleId);
        if (module == null) {
            return;
        }
        this.clampBounds(nextBounds, CustomScreenModuleCatalog.getContract(module.getModuleType()));
        if (this.activeVariant == CustomScreenLayout.VariantFamily.BASE) {
            module.setBounds(nextBounds);
        }
        else {
            final CustomScreenLayout.VariantLayout variantLayout = this.draftLayout.getOrCreateVariant(this.activeVariant);
            final CustomModuleInstance.VariantOverride override = this.copySelectedOverride();
            override.setBounds(nextBounds);
            variantLayout.putModuleOverride(moduleId, override);
        }
        this.statusMessage = "Bounds updated";
        this.syncDraftLayout();
    }

    private void undo() {
        if (this.undoStack.isEmpty()) {
            return;
        }
        this.redoStack.addLast(this.draftLayout.save());
        this.restoreLayout(this.undoStack.removeLast());
    }

    private void redo() {
        if (this.redoStack.isEmpty()) {
            return;
        }
        this.undoStack.addLast(this.draftLayout.save());
        this.restoreLayout(this.redoStack.removeLast());
    }

    private void resetLayout() {
        this.captureUndo();
        this.draftLayout = CustomScreenModuleCatalog.createStarterLayout();
        this.selectedModuleId = this.draftLayout.getModules().isEmpty() ? null : this.draftLayout.getModules().get(0).getId();
        this.activeVariant = CustomScreenLayout.VariantFamily.BASE;
        this.statusMessage = "Reset to starter layout";
        this.syncDraftLayout();
    }

    private void saveAndClose() {
        this.draftLayout.normalize();
        this.draftSettings.saveActiveCustomScreenProfile(this.currentProfileName(), this.draftLayout.copy());
        this.draftSettings.setCustomScreenLayoutEnabled(true);
        PacketDistributor.sendToServer(ConfigSyncPacket.settingsChange(this.panel.getBlockPos(), this.draftSettings));
        this.savedSnapshot = this.draftSettings.save();
        DisplayTextureManager.markDirty(this.panel.getBlockPos());
        this.minecraft.setScreen(this.parentScreen);
    }

    private void cancel() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parentScreen);
        }
    }

    private void captureUndo() {
        this.undoStack.addLast(this.draftLayout.save());
        while (this.undoStack.size() > UNDO_CAP) {
            this.undoStack.removeFirst();
        }
        this.redoStack.clear();
        this.updateButtonStates();
    }

    private void restoreLayout(CompoundTag snapshot) {
        this.draftLayout = CustomScreenLayout.load(snapshot);
        if (this.draftLayout.findModule(this.selectedModuleId) == null) {
            this.selectedModuleId = this.draftLayout.getModules().isEmpty() ? null : this.draftLayout.getModules().get(0).getId();
        }
        this.statusMessage = "History restored";
        this.syncDraftLayout();
    }

    private void syncDraftLayout() {
        this.draftLayout = CustomScreenMigrationCoordinator.normalizeLayout(this.draftLayout, this.panel.getSourceRegistry());
        this.draftSettings.saveActiveCustomScreenProfile(this.currentProfileName(), this.draftLayout.copy());
        this.updateButtonStates();
    }

    private void updateButtonStates() {
        if (this.saveButton != null) {
            this.saveButton.setMessage(Component.literal(this.hasUnsavedChanges() ? "Save*" : "Save"));
        }
        if (this.undoButton != null) {
            this.undoButton.active = !this.undoStack.isEmpty();
        }
        if (this.redoButton != null) {
            this.redoButton.active = !this.redoStack.isEmpty();
        }
        if (this.snapButton != null) {
            this.snapButton.setMessage(Component.literal(this.compactHeaderTools ? (this.snapToGrid ? "Snap" : "Free") : (this.snapToGrid ? "Snap On" : "Snap Off")));
        }
        if (this.helpButton != null) {
            this.helpButton.setMessage(Component.literal(this.compactHeaderTools ? (this.helpMode ? "?*" : "?") : (this.helpMode ? "Help On" : "Help")));
        }
        if (this.previewButton != null) {
            this.previewButton.setMessage(Component.literal(this.compactHeaderTools ? "View" : "Preview " + this.studioTheme().getDisplayName()));
        }
        if (this.variantButton != null) {
            this.variantButton.setMessage(Component.literal(this.compactHeaderTools ? this.activeVariant.getSerializedName() : "Variant " + this.activeVariant.getSerializedName()));
        }
        final boolean hasSelection = this.getSelectedModule() != null;
        final List<Button> selectionButtons = List.of(
                this.duplicateButton,
                this.deleteButton,
                this.visibilityButton,
            this.styleButton,
                this.xMinusButton,
                this.xPlusButton,
                this.yMinusButton,
                this.yPlusButton,
                this.wMinusButton,
                this.wPlusButton,
                this.hMinusButton,
                this.hPlusButton);
        for (Button button : selectionButtons) {
            if (button != null) {
                button.active = hasSelection;
            }
        }
        if (this.visibilityButton != null) {
            this.visibilityButton.setMessage(Component.literal(this.currentVariantVisibility() == CustomModuleInstance.VisibilityPolicy.VisibilityMode.HIDE ? "Show" : "Hide"));
        }
        if (this.useBaseButton != null) {
            this.useBaseButton.active = hasSelection && this.activeVariant != CustomScreenLayout.VariantFamily.BASE && this.hasSelectedVariantOverride();
        }
        if (this.styleButton != null) {
            this.styleButton.setMessage(Component.literal("Style " + this.currentStyleLabel()));
        }
        if (this.presetsButton != null) {
            this.presetsButton.active = !this.presetModalOpen && !this.sourcePickerOpen && !this.slotPickerOpen;
        }
        if (this.nextProfileButton != null) {
            this.nextProfileButton.active = this.draftSettings.getCustomScreenProfileCount() > 1;
        }
        if (this.deleteProfileButton != null) {
            this.deleteProfileButton.active = this.draftSettings.getCustomScreenProfileCount() > 1;
        }
    }

    private void layoutInspectorWidgets() {
        final int inspectorButtonW = (this.inspectorW - 26) / 2;
        int inspectorY = this.inspectorActionY();
        if (this.duplicateButton != null) {
            this.duplicateButton.setX(this.inspectorX + 10);
            this.duplicateButton.setY(inspectorY);
            this.duplicateButton.setWidth(inspectorButtonW);
        }
        if (this.deleteButton != null) {
            this.deleteButton.setX(this.inspectorX + 14 + inspectorButtonW);
            this.deleteButton.setY(inspectorY);
            this.deleteButton.setWidth(inspectorButtonW);
        }
        inspectorY += 22;
        if (this.visibilityButton != null) {
            this.visibilityButton.setX(this.inspectorX + 10);
            this.visibilityButton.setY(inspectorY);
            this.visibilityButton.setWidth(inspectorButtonW);
        }
        if (this.useBaseButton != null) {
            this.useBaseButton.setX(this.inspectorX + 14 + inspectorButtonW);
            this.useBaseButton.setY(inspectorY);
            this.useBaseButton.setWidth(inspectorButtonW);
        }
        inspectorY += 28;
        if (this.styleButton != null) {
            this.styleButton.setX(this.inspectorX + 10);
            this.styleButton.setY(inspectorY);
            this.styleButton.setWidth(this.inspectorW - 20);
        }
        inspectorY += 24;
        this.layoutNudgeButtonRow(this.xMinusButton, this.xPlusButton, inspectorY, inspectorButtonW);
        inspectorY += 22;
        this.layoutNudgeButtonRow(this.yMinusButton, this.yPlusButton, inspectorY, inspectorButtonW);
        inspectorY += 22;
        this.layoutNudgeButtonRow(this.wMinusButton, this.wPlusButton, inspectorY, inspectorButtonW);
        inspectorY += 22;
        this.layoutNudgeButtonRow(this.hMinusButton, this.hPlusButton, inspectorY, inspectorButtonW);
    }

    private void layoutNudgeButtonRow(Button minusButton, Button plusButton, int y, int buttonWidth) {
        if (minusButton != null) {
            minusButton.setX(this.inspectorX + 10);
            minusButton.setY(y);
            minusButton.setWidth(buttonWidth);
        }
        if (plusButton != null) {
            plusButton.setX(this.inspectorX + 14 + buttonWidth);
            plusButton.setY(y);
            plusButton.setWidth(buttonWidth);
        }
    }

    private Button studioButton(String label, Button.OnPress onPress, int x, int y, int width, int height) {
        return new StudioButton(x, y, width, height, Component.literal(label), onPress);
    }

    private int headerTitleMaxWidth(boolean compact) {
        return Math.max(compact ? 84 : 110, Math.min(compact ? 138 : 220, this.width / 5));
    }

    private int repairBannerHeight() {
        return 30;
    }

    private int renderInspectorRepairBlock(GuiGraphics graphics, DisplayTheme theme, List<RepairEntry> repairs, int y, int bottomY) {
        if (repairs == null || repairs.isEmpty()) {
            return y;
        }
        final int endY = this.inspectorRepairBlockBottom(repairs, y, bottomY);
        if (endY <= y + 8) {
            return y;
        }
        graphics.fill(this.inspectorX + 10, y, this.inspectorX + this.inspectorW - 10, endY - 6, withAlpha(theme.warn, 38));
        drawBorder(graphics, this.inspectorX + 10, y, this.inspectorW - 20, endY - y - 6, theme.warn);
        this.drawFittedString(graphics, "Repair needed", this.inspectorX + 16, y + 5, this.inspectorW - 32, theme.warn);
        if (endY - y <= 34) {
            final RepairEntry repair = repairs.get(0);
            this.drawFittedString(graphics, repair.label() + ": " + repair.detail(), this.inspectorX + 16, y + 17, this.inspectorW - 32, theme.dim);
            return endY;
        }
        int rowY = y + 18;
        final int rowCount = this.inspectorRepairVisibleRows(repairs, y, bottomY);
        for (int index = 0; index < rowCount; ++index) {
            final RepairEntry repair = repairs.get(index);
            graphics.fill(this.inspectorX + 14, rowY, this.inspectorX + this.inspectorW - 14, rowY + 16, withAlpha(theme.warn, 54));
            drawBorder(graphics, this.inspectorX + 14, rowY, this.inspectorW - 28, 16, theme.warn);
            this.drawFittedString(graphics, this.bindingFieldLabel(repair.field()), this.inspectorX + 18, rowY + 4, 64, theme.warn);
            this.drawFittedString(graphics, repair.label() + " " + repair.detail(), this.inspectorX + 84, rowY + 4, Math.max(28, this.inspectorW - 154), theme.dim);
            if (this.repairCanClear(repair)) {
                drawBorder(graphics, this.inspectorX + this.inspectorW - 58, rowY + 2, 44, 12, theme.warn);
                drawCentered(graphics, "Clear", this.inspectorX + this.inspectorW - 58, rowY + 5, 44, theme.warn);
            }
            rowY += 18;
        }
        if (repairs.size() > rowCount) {
            this.drawFittedString(graphics, "+" + (repairs.size() - rowCount) + " more repair items", this.inspectorX + 16, rowY + 2, this.inspectorW - 32, theme.warn);
        }
        return endY;
    }

    private boolean handleInspectorRepairBlockClick(int mouseX, int mouseY, List<RepairEntry> repairs, int y, int bottomY) {
        if (repairs == null || repairs.isEmpty()) {
            return false;
        }
        final int endY = this.inspectorRepairBlockBottom(repairs, y, bottomY);
        if (!inside(mouseX, mouseY, this.inspectorX + 10, y, this.inspectorW - 20, Math.max(0, endY - y - 6))) {
            return false;
        }
        final int rowCount = this.inspectorRepairVisibleRows(repairs, y, bottomY);
        if (rowCount <= 0 || endY - y <= 34) {
            this.openRepairPicker(repairs.get(0));
            return true;
        }
        int rowY = y + 18;
        for (int index = 0; index < rowCount; ++index) {
            final RepairEntry repair = repairs.get(index);
            if (inside(mouseX, mouseY, this.inspectorX + 14, rowY, this.inspectorW - 28, 16)) {
                if (this.repairCanClear(repair) && inside(mouseX, mouseY, this.inspectorX + this.inspectorW - 58, rowY + 2, 44, 12)) {
                    this.clearRepairBinding(repair);
                    return true;
                }
                this.openRepairPicker(repair);
                return true;
            }
            rowY += 18;
        }
        this.openRepairPicker(repairs.get(0));
        return true;
    }

    private int inspectorRepairBlockBottom(List<RepairEntry> repairs, int y, int bottomY) {
        if (repairs == null || repairs.isEmpty() || y >= bottomY) {
            return y;
        }
        final int rowCount = this.inspectorRepairVisibleRows(repairs, y, bottomY);
        final int overflowH = repairs.size() > rowCount ? 12 : 0;
        final int desired = y + 18 + rowCount * 18 + overflowH + 8;
        final int compact = Math.min(bottomY, y + 34);
        return rowCount <= 0 ? compact : Math.min(bottomY, desired);
    }

    private int inspectorRepairVisibleRows(List<RepairEntry> repairs, int y, int bottomY) {
        if (repairs == null || repairs.isEmpty()) {
            return 0;
        }
        final int available = bottomY - y - 26;
        if (available < 18) {
            return 0;
        }
        return Math.max(1, Math.min(3, Math.min(repairs.size(), available / 18)));
    }

    private int inspectorActionY() {
        final CustomModuleInstance selected = this.getSelectedModule();
        final CustomScreenModuleContract contract = selected != null ? CustomScreenModuleCatalog.getContract(selected.getModuleType()) : null;
        final int bindingRows = contract != null ? contract.getBindingFields().size() : 0;
        final int desired = this.stageY + 112 + bindingRows * 20;
        final int max = this.stageY + Math.max(152, this.stageH - 166);
        return Math.max(this.stageY + 152, Math.min(desired, max));
    }

    private void computeLayout() {
        this.leftX = MARGIN;
        final int availableW = Math.max(1, this.width - MARGIN * 2);
        this.leftW = Math.max(160, Math.min(246, this.width / 5));
        this.inspectorW = Math.max(176, Math.min(256, this.width / 5));
        // 180 = minimum usable stage width, 28 = combined inner gutters between left rail/stage/inspector (14 + 14).
        int overflow = this.leftW + this.inspectorW + 180 + 28 - availableW;
        if (overflow > 0) {
            final int leftReduction = Math.min(overflow, this.leftW - 142);
            this.leftW -= leftReduction;
            overflow -= leftReduction;
        }
        if (overflow > 0) {
            final int inspectorReduction = Math.min(overflow, this.inspectorW - 154);
            this.inspectorW -= inspectorReduction;
        }
        this.inspectorX = this.width - MARGIN - this.inspectorW;
        this.stageX = this.leftX + this.leftW + 14;
        this.stageY = MARGIN + HEADER_H + 8;
        this.stageW = Math.max(120, this.inspectorX - this.stageX - 14);
        this.stageH = Math.max(140, this.height - this.stageY - FOOTER_H - MARGIN);

        final GridShape shape = GridShape.forVariant(this.activeVariant);
        this.gridCols = shape.columns;
        this.gridRows = shape.rows;
        // [1]
        this.cellSize = Math.max(12, Math.min((this.stageW - GRID_PAD * 2) / this.gridCols, (this.stageH - 32 - GRID_PAD * 2) / this.gridRows));
        final int gridWidth = this.gridCols * this.cellSize;
        final int gridHeight = this.gridRows * this.cellSize;
        this.gridOriginX = this.stageX + (this.stageW - gridWidth) / 2;
        this.gridOriginY = this.stageY + 28 + (this.stageH - 38 - gridHeight) / 2;
    }

    private List<CustomScreenModuleContract> filteredContracts() {
        final String query = this.searchBox != null ? this.searchBox.getValue() : "";
        return CustomScreenModuleCatalog.filteredContracts(query, this.selectedDomain);
    }

    private List<CustomScreenSourceCapability> sourceCapabilities() {
        return CustomScreenSourceCapabilityCatalog.collect(this.panel.getSourceRegistry());
    }

    private List<SourceSummary> filteredSourceSummaries() {
        final String query = this.searchBox != null ? this.searchBox.getValue() : "";
        final LinkedHashMap<String, SourceSummaryBuilder> summaries = new LinkedHashMap<String, SourceSummaryBuilder>();
        for (CustomScreenSourceCapability capability : this.sourceCapabilities()) {
            if (!capability.matches(query)) {
                continue;
            }
            summaries.computeIfAbsent(capability.getSourceId(), key -> new SourceSummaryBuilder(capability)).increment();
        }
        final ArrayList<SourceSummary> result = new ArrayList<SourceSummary>();
        for (SourceSummaryBuilder builder : summaries.values()) {
            result.add(builder.build());
        }
        result.sort(Comparator.comparing((SourceSummary summary) -> familySortKey(summary.capability()))
                .thenComparing(summary -> summary.capability().getSourceLabel())
                .thenComparing(summary -> summary.capability().getSourceId()));
        return result;
    }

    private List<CustomScreenSourceCapability> filteredSectionCapabilities() {
        final String query = this.searchBox != null ? this.searchBox.getValue() : "";
        final ArrayList<CustomScreenSourceCapability> capabilities = new ArrayList<CustomScreenSourceCapability>();
        for (CustomScreenSourceCapability capability : this.sourceCapabilities()) {
            if (!this.selectedSourceId.isEmpty() && !this.selectedSourceId.equals(capability.getSourceId())) {
                continue;
            }
            if (!capability.matches(query)) {
                continue;
            }
            capabilities.add(capability);
        }
        capabilities.sort(Comparator.comparing(CustomScreenStudioScreen::familySortKey)
                .thenComparingInt(CustomScreenStudioScreen::sectionTierOrder)
                .thenComparing(CustomScreenSourceCapability::getSourceLabel)
                .thenComparing(CustomScreenSourceCapability::getSectionLabel));
        return capabilities;
    }

    private static String familyLabel(CustomScreenSourceCapability capability) {
        return CustomScreenSourceFamilyCatalog.familyForSourceType(capability != null ? capability.getSourceType() : "").getDisplayName();
    }

    private static String familySortKey(CustomScreenSourceCapability capability) {
        final SourceFamilyDefinition family = CustomScreenSourceFamilyCatalog.familyForSourceType(capability != null ? capability.getSourceType() : "");
        return family.getGroup().ordinal() + ":" + family.getFamilyId();
    }

    private static boolean nativeSection(CustomScreenSourceCapability capability) {
        if (capability == null) {
            return false;
        }
        return capability.isHero()
                || (!"source_summary".equals(capability.getSectionId()) && !"channel_overview".equals(capability.getSectionId()));
    }

    private static int sectionTierOrder(CustomScreenSourceCapability capability) {
        if (capability == null) {
            return 3;
        }
        if (capability.isHero()) {
            return 0;
        }
        return nativeSection(capability) ? 1 : 2;
    }

    private static String sectionTierLabel(CustomScreenSourceCapability capability) {
        if (capability != null && capability.isHero()) {
            return "Macro";
        }
        return nativeSection(capability) ? "Native" : "Generic";
    }

    private List<ModuleView> resolvedModules() {
        final ArrayList<ModuleView> modules = new ArrayList<ModuleView>();
        final Set<String> availableSourceIds = this.availableSourceIds();
        final CustomScreenLayout.VariantLayout variantLayout = this.activeVariant == CustomScreenLayout.VariantFamily.BASE
                ? null
                : this.draftLayout.getVariant(this.activeVariant);
        for (CustomModuleInstance module : this.draftLayout.getModules()) {
            if (module == null) {
                continue;
            }
            if (variantLayout != null && variantLayout.getOmittedModules().contains(module.getId())) {
                continue;
            }
            final CustomModuleInstance.VariantOverride override = variantLayout != null
                    ? variantLayout.getModuleOverrides().get(module.getId())
                    : null;
            final CustomModuleInstance effectiveModule = module.copy();
            if (override != null && override.getVariant() != null && !override.getVariant().isEmpty()) {
                effectiveModule.setVariant(override.getVariant());
            }
            final CustomModuleInstance.GridBounds bounds = override != null && override.getBounds() != null ? override.getBounds().copy() : effectiveModule.getBounds().copy();
            final CustomModuleInstance.VisibilityPolicy.VisibilityMode visibility = override != null
                    && override.getVisibility() != null
                    && override.getVisibility() != CustomModuleInstance.VisibilityPolicy.VisibilityMode.INHERIT
                    ? override.getVisibility()
                    : effectiveModule.getVisibilityPolicy().get(this.activeVariant);
            final boolean hidden = visibility == CustomModuleInstance.VisibilityPolicy.VisibilityMode.HIDE;
            final int renderX = this.gridOriginX + bounds.getX() * this.cellSize + 1;
            final int renderY = this.gridOriginY + bounds.getY() * this.cellSize + 1;
            final int renderW = Math.max(8, bounds.getWidth() * this.cellSize - 2);
            final int renderH = Math.max(8, bounds.getHeight() * this.cellSize - 2);
            modules.add(new ModuleView(effectiveModule,
                    bounds,
                    CustomScreenModuleCatalog.getContract(effectiveModule.getModuleType()),
                    CustomScreenModuleRenderer.summarizeBinding(CustomScreenModuleCatalog.getContract(effectiveModule.getModuleType()), effectiveModule, availableSourceIds),
                    renderX,
                    renderY,
                    renderW,
                    renderH,
                    hidden,
                    override));
        }
        return modules;
    }

    private int presetVisibleRowCount(int modalH) {
        return Math.max(1, (modalH - 86) / 38);
    }

    private int presetFirstVisibleIndex(int modalH) {
        final int visibleRows = this.presetVisibleRowCount(modalH);
        final int maxOffset = Math.max(0, CustomScreenPresetCatalog.allPresets().size() - visibleRows);
        this.presetScrollOffset = Math.max(0, Math.min(maxOffset, this.presetScrollOffset));
        return this.presetScrollOffset;
    }

    private ModuleView findResolvedView(String moduleId) {
        if (moduleId == null || moduleId.isEmpty()) {
            return null;
        }
        for (ModuleView moduleView : this.resolvedModules()) {
            if (moduleId.equals(moduleView.module().getId())) {
                return moduleView;
            }
        }
        return null;
    }

    private ModuleView findModuleAt(int mouseX, int mouseY) {
        final List<ModuleView> modules = this.resolvedModules();
        for (int index = modules.size() - 1; index >= 0; --index) {
            final ModuleView moduleView = modules.get(index);
            if (inside(mouseX, mouseY, moduleView.renderX, moduleView.renderY, moduleView.renderW, moduleView.renderH)) {
                return moduleView;
            }
        }
        return null;
    }

    private boolean hasUnsavedChanges() {
        if (this.profileNameBox != null && !this.currentProfileName().equals(this.draftSettings.activeCustomScreenProfileName())) {
            return true;
        }
        return !this.savedSnapshot.equals(this.draftSettings.save());
    }

    private DisplayTheme studioTheme() {
        return this.previewThemeOverride != null ? this.previewThemeOverride : this.draftSettings.getDisplayTheme();
    }

    private Set<String> availableSourceIds() {
        return new LinkedHashSet<String>(this.panel.getSourceRegistry().keySet());
    }

    private CustomModuleInstance getSelectedModule() {
        return this.selectedModuleId != null ? this.draftLayout.findModule(this.selectedModuleId) : null;
    }

    private String currentProfileName() {
        final String typedName = this.profileNameBox != null ? this.profileNameBox.getValue().trim() : "";
        if (!typedName.isEmpty()) {
            return typedName;
        }
        return this.draftSettings.activeCustomScreenProfileName();
    }

    private String profilePositionLabel() {
        final List<CustomScreenProfile> profiles = this.draftSettings.getCustomScreenProfiles();
        if (profiles.isEmpty()) {
            return "0/0";
        }
        final String activeId = this.draftSettings.getActiveCustomScreenProfileId();
        int activeIndex = 0;
        for (int index = 0; index < profiles.size(); ++index) {
            if (profiles.get(index).getId().equals(activeId)) {
                activeIndex = index;
                break;
            }
        }
        return (activeIndex + 1) + "/" + profiles.size();
    }

    private void createProfile() {
        final String name = "Custom Layout " + (this.draftSettings.getCustomScreenProfileCount() + 1);
        this.draftLayout = CustomScreenModuleCatalog.createStarterLayout();
        this.draftSettings.createCustomScreenProfile(name, this.draftLayout);
        this.draftLayout = this.draftSettings.getCustomScreenLayout().copy();
        this.selectedModuleId = this.draftLayout.getModules().isEmpty() ? null : this.draftLayout.getModules().get(0).getId();
        this.activeVariant = CustomScreenLayout.VariantFamily.BASE;
        this.undoStack.clear();
        this.redoStack.clear();
        this.setProfileNameBox(name);
        this.statusMessage = "Created profile " + name;
        this.syncDraftLayout();
    }

    private void duplicateProfile() {
        this.draftSettings.saveActiveCustomScreenProfile(this.currentProfileName(), this.draftLayout.copy());
        final String name = this.currentProfileName() + " Copy";
        this.draftSettings.duplicateActiveCustomScreenProfile(name);
        this.draftLayout = this.draftSettings.getCustomScreenLayout().copy();
        this.selectedModuleId = this.draftLayout.getModules().isEmpty() ? null : this.draftLayout.getModules().get(0).getId();
        this.undoStack.clear();
        this.redoStack.clear();
        this.setProfileNameBox(name);
        this.statusMessage = "Duplicated profile";
        this.syncDraftLayout();
    }

    private void switchNextProfile() {
        this.draftSettings.saveActiveCustomScreenProfile(this.currentProfileName(), this.draftLayout.copy());
        if (!this.draftSettings.cycleCustomScreenProfile()) {
            return;
        }
        this.draftLayout = this.draftSettings.getCustomScreenLayout().copy();
        this.selectedModuleId = this.draftLayout.getModules().isEmpty() ? null : this.draftLayout.getModules().get(0).getId();
        this.undoStack.clear();
        this.redoStack.clear();
        this.setProfileNameBox(this.draftSettings.activeCustomScreenProfileName());
        this.statusMessage = "Switched profile";
        this.syncDraftLayout();
    }

    private void deleteActiveProfile() {
        final String activeId = this.draftSettings.getActiveCustomScreenProfileId();
        if (!this.draftSettings.deleteCustomScreenProfile(activeId)) {
            return;
        }
        this.draftLayout = this.draftSettings.getCustomScreenLayout().copy();
        this.selectedModuleId = this.draftLayout.getModules().isEmpty() ? null : this.draftLayout.getModules().get(0).getId();
        this.undoStack.clear();
        this.redoStack.clear();
        this.setProfileNameBox(this.draftSettings.activeCustomScreenProfileName());
        this.statusMessage = "Deleted profile";
        this.syncDraftLayout();
    }

    private void setProfileNameBox(String name) {
        if (this.profileNameBox != null) {
            this.profileNameBox.setValue(name != null ? name : "Custom Layout");
        }
    }

    private CustomModuleInstance.VariantOverride copySelectedOverride() {
        final ModuleView view = this.findResolvedView(this.selectedModuleId);
        return view != null && view.override() != null ? view.override().copy() : new CustomModuleInstance.VariantOverride();
    }

    private boolean hasSelectedVariantOverride() {
        final ModuleView view = this.findResolvedView(this.selectedModuleId);
        return view != null && view.override() != null && !view.override().isEmpty();
    }

    private CustomModuleInstance.VisibilityPolicy.VisibilityMode currentVariantVisibility() {
        final ModuleView view = this.findResolvedView(this.selectedModuleId);
        if (view != null && view.override() != null && view.override().getVisibility() != null
                && view.override().getVisibility() != CustomModuleInstance.VisibilityPolicy.VisibilityMode.INHERIT) {
            return view.override().getVisibility();
        }
        final CustomModuleInstance selected = this.getSelectedModule();
        return selected != null ? selected.getVisibilityPolicy().get(this.activeVariant) : CustomModuleInstance.VisibilityPolicy.VisibilityMode.SHOW;
    }

    private String visibilityLabel() {
        return this.currentVariantVisibility().getSerializedName();
    }

    private String currentStyleLabel() {
        final CustomModuleInstance selected = this.getSelectedModule();
        if (selected == null) {
            return "default";
        }
        if (CustomScreenModuleType.ANALOG_GAUGE.getSerializedName().equals(selected.getModuleType())) {
            final String visualMode = selected.getModuleConfig().getString("visualMode");
            return visualMode.isEmpty() ? "dial" : visualMode;
        }
        final ModuleView view = this.findResolvedView(this.selectedModuleId);
        return view != null && view.module() != null ? view.module().getVariant() : selected.getVariant();
    }

    private int deltaToGrid(int deltaPixels) {
        if (this.cellSize <= 0) {
            return 0;
        }
        return this.snapToGrid ? Math.round(deltaPixels / (float) this.cellSize) : deltaPixels / this.cellSize;
    }

    private void clampBounds(CustomModuleInstance.GridBounds bounds) {
        this.clampBounds(bounds, null);
    }

    private void clampBounds(CustomModuleInstance.GridBounds bounds, CustomScreenModuleContract contract) {
        bounds.setX(Math.max(0, Math.min(this.gridCols - 1, bounds.getX())));
        bounds.setY(Math.max(0, Math.min(this.gridRows - 1, bounds.getY())));
        final int minWidth = contract != null ? contract.getMinAuthoringWidth() : 1;
        final int minHeight = contract != null ? contract.getMinAuthoringHeight() : 1;
        bounds.setWidth(Math.max(Math.min(this.gridCols - bounds.getX(), minWidth), Math.min(this.gridCols - bounds.getX(), bounds.getWidth())));
        bounds.setHeight(Math.max(Math.min(this.gridRows - bounds.getY(), minHeight), Math.min(this.gridRows - bounds.getY(), bounds.getHeight())));
    }

    private boolean isInsideGrid(int mouseX, int mouseY) {
        return inside(mouseX, mouseY, this.gridOriginX, this.gridOriginY, this.gridCols * this.cellSize, this.gridRows * this.cellSize);
    }

    private int selectedDomainIndex() {
        if (this.selectedDomain == null) {
            return 0;
        }
        return switch (this.selectedDomain) {
            case STRUCTURE -> 1;
            case METRICS -> 2;
            case STATUS -> 3;
        };
    }

    private static CustomScreenModuleContract.Domain domainForIndex(int index) {
        return switch (index) {
            case 1 -> CustomScreenModuleContract.Domain.STRUCTURE;
            case 2 -> CustomScreenModuleContract.Domain.METRICS;
            case 3 -> CustomScreenModuleContract.Domain.STATUS;
            default -> null;
        };
    }

    private static String domainLabel(int index) {
        return switch (index) {
            case 1 -> "Structure";
            case 2 -> "Metrics";
            case 3 -> "Status";
            default -> "All";
        };
    }

    private static CustomScreenLayout.VariantFamily nextVariant(CustomScreenLayout.VariantFamily current) {
        return switch (current) {
            case BASE -> CustomScreenLayout.VariantFamily.WIDE;
            case WIDE -> CustomScreenLayout.VariantFamily.SQUARE;
            case SQUARE -> CustomScreenLayout.VariantFamily.TALL;
            case TALL -> CustomScreenLayout.VariantFamily.COMPACT;
            case COMPACT -> CustomScreenLayout.VariantFamily.BASE;
        };
    }

    private static boolean inside(int mouseX, int mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    private void applySelectedPreset(CustomScreenPresetCatalog.ApplyMode mode) {
        final CustomScreenPresetCatalog.PresetDefinition preset = CustomScreenPresetCatalog.find(this.selectedPresetId);
        if (preset == null) {
            return;
        }
        this.captureUndo();
        final CustomScreenPresetCatalog.ApplyResult result = CustomScreenPresetCatalog.apply(
                this.draftLayout,
                preset,
                mode,
                this.activeStudioSourceScope(),
                this.panel.getSourceRegistry());
        this.draftLayout = result.layout();
        this.selectedModuleId = this.draftLayout.getModules().isEmpty() ? null : this.draftLayout.getModules().get(0).getId();
        this.statusMessage = result.summary();
        this.presetModalOpen = false;
        this.syncDraftLayout();
    }

    private void openSourcePicker(String field) {
        this.sourcePickerField = field != null ? field : "";
        this.sourcePickerScrollOffset = 0;
        this.sourcePickerOpen = true;
        this.presetModalOpen = false;
        this.updateButtonStates();
    }

    private void selectSourceForBinding(SourcePickerEntry entry) {
        final CustomModuleInstance selected = this.getSelectedModule();
        if (selected == null || entry == null || this.sourcePickerField.isEmpty()) {
            return;
        }
        final CustomModuleBinding binding = selected.resolveBinding(this.sourcePickerField);
        if (binding == null) {
            return;
        }
        this.captureUndo();
        final String previousSourceId = binding.getSourceId();
        binding.setMode(CustomModuleBinding.Mode.EXPLICIT_SOURCE);
        binding.setSourceId(entry.sourceId());
        if (!entry.sourceType().isEmpty()) {
            binding.setSourceTypeHint(entry.sourceType());
        }
        binding.normalize();
        for (CustomModuleBinding sibling : selected.getBindings()) {
            if (sibling == null || sibling.matchesField(this.sourcePickerField)) {
                continue;
            }
            final boolean inheritsNewSource = previousSourceId.isEmpty()
                    && sibling.getSourceId().isEmpty()
                    && isMetricBinding(sibling.getKey())
                    && !entry.sourceId().isEmpty();
            if ((!previousSourceId.isEmpty() && previousSourceId.equals(sibling.getSourceId())) || inheritsNewSource) {
                sibling.setSourceId(entry.sourceId());
                sibling.setSourceTypeHint(entry.sourceType());
                sibling.normalize();
            }
        }
        final CompoundTag config = selected.getModuleConfig();
        config.putString("sourceId", entry.sourceId());
        config.putString("sourceType", entry.sourceType());
        config.putString("sourceLabel", entry.sourceId().isEmpty() ? "" : entry.label());
        config.putBoolean("sourceOnline", !entry.stale() && !entry.sourceId().isEmpty());
        selected.setModuleConfig(config);
        this.statusMessage = entry.sourceId().isEmpty() ? "Binding cleared" : "Bound to " + entry.sourceId();
        this.sourcePickerOpen = false;
        this.sourcePickerField = "";
        this.syncDraftLayout();
    }

    private List<SourcePickerEntry> sourcePickerEntries() {
        final ArrayList<SourcePickerEntry> entries = new ArrayList<SourcePickerEntry>();
        entries.add(new SourcePickerEntry("", "", "Clear source binding", true, false, "clear"));
        final ArrayList<SourcePickerEntry> liveEntries = new ArrayList<SourcePickerEntry>();
        final CustomModuleInstance selected = this.getSelectedModule();
        final CustomModuleBinding binding = selected != null ? selected.resolveBinding(this.sourcePickerField) : null;
        final String currentSourceId = binding != null ? binding.getSourceId() : "";
        boolean currentSourcePresent = currentSourceId.isEmpty();
        for (Map.Entry<String, SourceConfig> sourceEntry : this.panel.getSourceRegistry().entrySet()) {
            final SourceConfig config = sourceEntry.getValue();
            if (config == null) {
                continue;
            }
            if (sourceEntry.getKey().equals(currentSourceId)) {
                currentSourcePresent = true;
            }
            liveEntries.add(new SourcePickerEntry(sourceEntry.getKey(),
                    config.getSourceType(),
                    config.getDisplayLabel(),
                    compatibleSource(binding, config),
                    false,
                    sourceCompatibilityLabel(binding, config)));
        }
        liveEntries.sort(Comparator.comparingInt(CustomScreenStudioScreen::sourcePickerRank)
                .thenComparing(entry -> sourceFamilySortKey(entry.sourceType()))
                .thenComparing(SourcePickerEntry::label)
                .thenComparing(SourcePickerEntry::sourceId));
        entries.addAll(liveEntries);
        if (!currentSourcePresent) {
            entries.add(new SourcePickerEntry(currentSourceId, binding != null ? binding.getSourceTypeHint() : "", "Missing saved source", false, true, "missing"));
        }
        return entries;
    }

    private static int sourcePickerRank(SourcePickerEntry entry) {
        if (entry == null) {
            return 4;
        }
        if (entry.sourceId().isEmpty()) {
            return 0;
        }
        if (entry.compatible()) {
            return 1;
        }
        if (entry.stale()) {
            return 3;
        }
        return 2;
    }

    private static String sourceFamilySortKey(String sourceType) {
        final SourceFamilyDefinition family = CustomScreenSourceFamilyCatalog.familyForSourceType(sourceType);
        return family.getGroup().ordinal() + ":" + family.getFamilyId();
    }

    private static boolean compatibleSource(CustomModuleBinding binding, SourceConfig config) {
        if (binding == null || config == null || binding.getSourceTypeFilter().isEmpty()) {
            return true;
        }
        return CustomScreenSourceFamilyCatalog.matchesSourceTypeFilter(config, binding.getSourceTypeFilter());
    }

    private static String sourceCompatibilityLabel(CustomModuleBinding binding, SourceConfig config) {
        if (config == null) {
            return "missing";
        }
        if (binding == null || binding.getSourceTypeFilter().isEmpty()) {
            return "any family";
        }
        if (CustomScreenSourceFamilyCatalog.matchesSourceTypeFilter(config, binding.getSourceTypeFilter())) {
            return "compatible";
        }
        final SourceFamilyDefinition expected = CustomScreenSourceFamilyCatalog.familyForSourceType(binding.getSourceTypeFilter());
        final SourceFamilyDefinition actual = CustomScreenSourceFamilyCatalog.familyForSource(config);
        return "needs " + expected.getDisplayName() + ", got " + actual.getDisplayName();
    }

    private static boolean isMetricBinding(String key) {
        return CustomModuleBinding.PRIMARY_METRIC.equals(key)
                || CustomModuleBinding.SECONDARY_METRIC.equals(key)
                || CustomModuleBinding.STATUS_METRIC.equals(key);
    }

    private int sourcePickerVisibleRowCount(int modalH) {
        return Math.max(1, (modalH - 92) / this.sourcePickerRowHeight());
    }

    private int sourcePickerRowHeight() {
        return 44;
    }

    private int sourcePickerFirstVisibleIndex(int modalH) {
        final int visibleRows = this.sourcePickerVisibleRowCount(modalH);
        final int maxOffset = Math.max(0, this.sourcePickerEntries().size() - visibleRows);
        this.sourcePickerScrollOffset = Math.max(0, Math.min(maxOffset, this.sourcePickerScrollOffset));
        return this.sourcePickerScrollOffset;
    }

    private void openSlotPicker(String field) {
        this.slotPickerField = field != null ? field : "";
        this.slotPickerScrollOffset = 0;
        this.slotPickerOpen = true;
        this.presetModalOpen = false;
        this.sourcePickerOpen = false;
        this.updateButtonStates();
    }

    private void selectSlotForBinding(SlotPickerEntry entry) {
        final CustomModuleInstance selected = this.getSelectedModule();
        if (selected == null || entry == null || !entry.selectable() || this.slotPickerField.isEmpty()) {
            return;
        }
        final CustomModuleBinding binding = selected.resolveBinding(this.slotPickerField);
        if (binding == null) {
            return;
        }
        this.captureUndo();
        binding.setMode(CustomModuleBinding.Mode.SEMANTIC_SLOT);
        binding.setSlot(entry.slot());
        binding.setMetricLabel(entry.slot().isEmpty() ? "" : entry.label());
        if (entry.slot().isEmpty()) {
            binding.setSourceId("");
            binding.setSourceTypeHint("");
            binding.setSourceTypeFilter("");
            binding.setSourcePosHint(Long.MIN_VALUE);
        }
        else {
            final CompoundTag selectedConfig = selected.getModuleConfig();
            final String fallbackSourceId = selectedConfig.getString("sourceId");
            final String fallbackSourceType = selectedConfig.getString("sourceType");
            final String entrySourceId = entry.sourceId().isEmpty() ? fallbackSourceId : entry.sourceId();
            final String entrySourceType = entry.sourceType().isEmpty() ? fallbackSourceType : entry.sourceType();
            if (!entrySourceId.isEmpty()) {
                binding.setSourceId(entrySourceId);
                binding.setSourceTypeHint(entrySourceType);
                binding.setSourceTypeFilter(entrySourceType);
                final SourceConfig entrySourceConfig = this.panel.getSourceRegistry().get(entrySourceId);
                if (entrySourceConfig != null && entrySourceConfig.getSourcePos() != null) {
                    binding.setSourcePosHint(entrySourceConfig.getSourcePos().asLong());
                }
            }
        }
        binding.normalize();
        final CompoundTag config = selected.getModuleConfig();
        if (CustomModuleBinding.PRIMARY_METRIC.equals(this.slotPickerField)) {
            config.putString("primaryMetric", entry.slot());
        }
        if (CustomScreenModuleType.ANALOG_GAUGE.getSerializedName().equals(selected.getModuleType())) {
            config.putString("visualMode", CustomScreenSourceCapability.visualModeFor(entry.sourceType().isEmpty() ? config.getString("sourceType") : entry.sourceType(),
                    config.getString("sectionLabel"),
                    entry.slot(),
                    config.getString("sectionRole")));
        }
        selected.setModuleConfig(config);
        this.statusMessage = entry.slot().isEmpty() ? "Semantic slot cleared" : "Bound " + entry.label();
        this.slotPickerOpen = false;
        this.slotPickerField = "";
        this.syncDraftLayout();
    }

    private List<SlotPickerEntry> slotPickerEntries() {
        final ArrayList<SlotPickerEntry> entries = new ArrayList<SlotPickerEntry>();
        entries.add(SlotPickerEntry.header("Binding Action"));
        entries.add(SlotPickerEntry.metric("", "Clear slot", "Binding Action", "", "", "", "Remove this metric binding", false));
        final CustomModuleInstance selected = this.getSelectedModule();
        final CompoundTag config = selected != null ? selected.getModuleConfig() : new CompoundTag();
        final String selectedSourceId = this.selectedMetricSourceId(selected, config);
        final SourceConfig selectedSource = this.panel.getSourceRegistry().get(selectedSourceId);
        final LinkedHashSet<String> seenSelections = new LinkedHashSet<String>();
        seenSelections.add(selectionKey("", ""));
        if (selectedSource != null) {
            final int selectedOutputStart = entries.size();
            this.addLiveSourceMetricEntries(entries, seenSelections, "Selected Source Outputs", selectedSource);
            this.addSlotHeaderIfNeeded(entries, selectedOutputStart, "Selected Source Outputs");

            final int specializedStart = entries.size();
            this.addCatalogMetricEntries(entries, seenSelections, "Specialized Screen Sections", selectedSource);
            this.addSlotHeaderIfNeeded(entries, specializedStart, "Specialized Screen Sections");

            if (this.isDieselEngineSource(selectedSource)) {
                final int reserveStart = entries.size();
                for (SourceConfig sourceConfig : this.panel.getSourceRegistry().values()) {
                    if (this.isDieselFuelReserveSource(sourceConfig)) {
                        this.addLiveSourceMetricEntries(entries, seenSelections, "Linked Fuel Reserve Outputs", sourceConfig);
                    }
                }
                this.addSlotHeaderIfNeeded(entries, reserveStart, "Linked Fuel Reserve Outputs");
            }
        }
        if (selectedSource == null || !isMetricBinding(this.slotPickerField)) {
            final int otherOutputStart = entries.size();
            for (SourceConfig sourceConfig : this.panel.getSourceRegistry().values()) {
                if (sourceConfig == null || sourceConfig.getSourceId().equals(selectedSourceId)) {
                    continue;
                }
                this.addLiveSourceMetricEntries(entries, seenSelections, "Other Connected Outputs", sourceConfig);
            }
            this.addSlotHeaderIfNeeded(entries, otherOutputStart, "Other Connected Outputs");
        }

        final int commonStart = entries.size();
        this.addCommonSlotEntries(entries, seenSelections, selectedSource);
        this.addSlotHeaderIfNeeded(entries, commonStart, "Common Semantic Slots");
        return entries;
    }

    private void addSlotHeaderIfNeeded(ArrayList<SlotPickerEntry> entries, int startIndex, String category) {
        if (entries.size() > startIndex) {
            entries.add(startIndex, SlotPickerEntry.header(category));
        }
    }

    private void addLiveSourceMetricEntries(ArrayList<SlotPickerEntry> entries,
                                            Set<String> seenSelections,
                                            String category,
                                            SourceConfig sourceConfig) {
        if (sourceConfig == null) {
            return;
        }
        final String sourceId = sourceConfig.getSourceId();
        for (StatsDisplayPanelBE.DataChannel channel : this.panel.getChannelsForSource(sourceId)) {
            if (channel == null || channel.getStore().getSampleCount() <= 0) {
                continue;
            }
            final String slot = metricSlotFromChannelId(channel.getId(), sourceId);
            if (slot.isEmpty() || !seenSelections.add(selectionKey(sourceId, slot))) {
                continue;
            }
            entries.add(SlotPickerEntry.metric(slot,
                    channel.getLabel(),
                    category,
                    sourceId,
                    sourceConfig.getSourceType(),
                    sourceConfig.getObservedDisplayLabel(),
                    this.metricPickerDetail(channel, slot, sourceConfig),
                    true));
        }
    }

    private void addCatalogMetricEntries(ArrayList<SlotPickerEntry> entries,
                                         Set<String> seenSelections,
                                         String category,
                                         SourceConfig sourceConfig) {
        if (sourceConfig == null) {
            return;
        }
        final String sourceId = sourceConfig.getSourceId();
        final String sourceType = sourceConfig.getSourceType();
        for (SpecializedHudWidgetCatalog.Definition definition : SpecializedHudWidgetCatalog.definitionsFor(sourceType)) {
            if (seenSelections.add(selectionKey(sourceId, definition.primaryKey()))) {
                entries.add(SlotPickerEntry.metric(definition.primaryKey(),
                        definition.label(),
                        category,
                        sourceId,
                        sourceType,
                        sourceConfig.getObservedDisplayLabel(),
                        "Primary output from " + definition.label(),
                        true));
            }
            for (String key : definition.keys()) {
                if (seenSelections.add(selectionKey(sourceId, key))) {
                    entries.add(SlotPickerEntry.metric(key,
                            bindingLabelForSlot(key),
                            category,
                            sourceId,
                            sourceType,
                            sourceConfig.getObservedDisplayLabel(),
                            "Output key from " + definition.label(),
                            true));
                }
            }
        }
    }

    private void addCommonSlotEntries(ArrayList<SlotPickerEntry> entries,
                                      Set<String> seenSelections,
                                      SourceConfig selectedSource) {
        final String sourceId = selectedSource != null ? selectedSource.getSourceId() : "";
        final String sourceType = selectedSource != null ? selectedSource.getSourceType() : "";
        final String sourceLabel = selectedSource != null ? selectedSource.getObservedDisplayLabel() : "";
        for (String slot : this.slotSuggestionsForField(this.slotPickerField, selectedSource)) {
            if (slot.isEmpty() || !seenSelections.add(selectionKey(sourceId, slot))) {
                continue;
            }
            entries.add(SlotPickerEntry.metric(slot,
                    bindingLabelForSlot(slot),
                    "Common Semantic Slots",
                    sourceId,
                    sourceType,
                    sourceLabel,
                    selectedSource != null ? "Search this source for a matching output" : "Search all connected sources for a matching output",
                    selectedSource != null));
        }
    }

    private String selectedMetricSourceId(CustomModuleInstance selected, CompoundTag config) {
        if (config != null && !config.getString("sourceId").isEmpty()) {
            return config.getString("sourceId");
        }
        if (selected != null) {
            final CustomModuleBinding sourceBinding = selected.resolveBinding(CustomModuleBinding.PRIMARY_SOURCE);
            if (sourceBinding != null && !sourceBinding.getSourceId().isEmpty()) {
                return sourceBinding.getSourceId();
            }
            final CustomModuleBinding metricBinding = selected.resolveBinding(this.slotPickerField);
            if (metricBinding != null && !metricBinding.getSourceId().isEmpty()) {
                return metricBinding.getSourceId();
            }
        }
        return "";
    }

    private String metricPickerDetail(StatsDisplayPanelBE.DataChannel channel, String slot, SourceConfig sourceConfig) {
        final String latestValue = channel.getStore().getSampleCount() > 0
                ? channel.getStore().getMetricType().formatValue(channel.getStore().getLatestValue())
                : "waiting";
        return slot + " / " + channel.getStore().getMetricType().name() + " / " + latestValue + " / " + sourcePositionLabel(sourceConfig);
    }

    private static String metricSlotFromChannelId(String channelId, String sourceId) {
        if (channelId == null || channelId.isEmpty()) {
            return "";
        }
        final String exactPrefix = "src_" + safeString(sourceId) + "_auto_";
        if (!sourceId.isEmpty() && channelId.startsWith(exactPrefix)) {
            return channelId.substring(exactPrefix.length());
        }
        final int autoIndex = channelId.indexOf("_auto_");
        if (autoIndex >= 0 && autoIndex + 6 < channelId.length()) {
            return channelId.substring(autoIndex + 6);
        }
        return channelId;
    }

    private static String sourcePositionLabel(SourceConfig sourceConfig) {
        if (sourceConfig == null) {
            return "no source";
        }
        final BlockPos observedPos = sourceConfig.getObservedTargetPos();
        final BlockPos sourcePos = observedPos != null ? observedPos : sourceConfig.getSourcePos();
        return sourcePos != null ? sourcePos.toShortString() : sourceConfig.getSourceId();
    }

    private static String selectionKey(String sourceId, String slot) {
        return safeString(sourceId) + "\u0000" + safeString(slot);
    }

    private boolean isDieselEngineSource(SourceConfig sourceConfig) {
        return "diesel.engine".equals(CustomScreenSourceFamilyCatalog.familyForSource(sourceConfig).getFamilyId());
    }

    private boolean isDieselFuelReserveSource(SourceConfig sourceConfig) {
        return "diesel.reserve".equals(CustomScreenSourceFamilyCatalog.familyForSource(sourceConfig).getFamilyId());
    }

    private static String safeString(String value) {
        return value != null ? value : "";
    }

    private int slotPickerVisibleRowCount(int modalH) {
        return Math.max(1, (modalH - 92) / this.slotPickerRowHeight());
    }

    private int slotPickerRowHeight() {
        return 48;
    }

    private int slotPickerFirstVisibleIndex(int modalH) {
        final int visibleRows = this.slotPickerVisibleRowCount(modalH);
        final int maxOffset = Math.max(0, this.slotPickerEntries().size() - visibleRows);
        this.slotPickerScrollOffset = Math.max(0, Math.min(maxOffset, this.slotPickerScrollOffset));
        return this.slotPickerScrollOffset;
    }

    private void cycleBindingField(String field) {
        final CustomModuleInstance selected = this.getSelectedModule();
        if (selected == null || field == null || field.isEmpty()) {
            return;
        }
        final CustomModuleBinding binding = selected.resolveBinding(field);
        if (binding == null) {
            return;
        }
        this.captureUndo();
        if (field.endsWith("Source")) {
            final List<String> sourceIds = new ArrayList<String>();
            sourceIds.add("");
            sourceIds.addAll(this.availableSourceIdList());
            final String current = binding.getSourceId();
            final int currentIndex = Math.max(0, sourceIds.indexOf(current));
            final String next = sourceIds.get((currentIndex + 1) % sourceIds.size());
            binding.setMode(CustomModuleBinding.Mode.EXPLICIT_SOURCE);
            binding.setSourceId(next);
            this.statusMessage = next.isEmpty() ? "Binding cleared" : "Bound to " + next;
        }
        else {
            final List<String> slots = this.slotSuggestionsForField(field);
            final String current = binding.getSlot();
            final int currentIndex = Math.max(0, slots.indexOf(current));
            final String next = slots.get((currentIndex + 1) % slots.size());
            binding.setMode(CustomModuleBinding.Mode.SEMANTIC_SLOT);
            binding.setSlot(next);
            binding.setMetricLabel(next.isEmpty() ? "" : bindingLabelForSlot(next));
            this.statusMessage = next.isEmpty() ? "Semantic slot cleared" : "Slot " + next;
        }
        binding.normalize();
        this.syncDraftLayout();
    }

    private void cycleSelectedStyle() {
        final CustomModuleInstance selected = this.getSelectedModule();
        final CustomScreenModuleContract contract = selected != null ? CustomScreenModuleCatalog.getContract(selected.getModuleType()) : null;
        if (selected == null || contract == null || contract.getVariantKeys().isEmpty()) {
            return;
        }
        if (CustomScreenModuleType.ANALOG_GAUGE.getSerializedName().equals(selected.getModuleType())) {
            this.cycleSelectedAnalogMode(selected);
            return;
        }
        this.captureUndo();
        final List<String> keys = contract.getVariantKeys();
        if (this.activeVariant == CustomScreenLayout.VariantFamily.BASE) {
            final String current = selected.getVariant();
            final int index = Math.max(0, keys.indexOf(current));
            selected.setVariant(keys.get((index + 1) % keys.size()));
        }
        else {
            final CustomScreenLayout.VariantLayout variantLayout = this.draftLayout.getOrCreateVariant(this.activeVariant);
            final CustomModuleInstance.VariantOverride override = this.copySelectedOverride();
            final String current = override.getVariant() != null && !override.getVariant().isEmpty() ? override.getVariant() : selected.getVariant();
            final int index = Math.max(0, keys.indexOf(current));
            final String next = keys.get((index + 1) % keys.size());
            override.setVariant(next.equals(selected.getVariant()) ? "" : next);
            if (override.isEmpty()) {
                variantLayout.removeModuleOverride(this.selectedModuleId);
            }
            else {
                variantLayout.putModuleOverride(this.selectedModuleId, override);
            }
            if (variantLayout.isEmpty()) {
                this.draftLayout.setVariant(this.activeVariant, null);
            }
        }
        this.statusMessage = "Style cycled";
        this.syncDraftLayout();
    }

    private void cycleSelectedAnalogMode(CustomModuleInstance selected) {
        this.captureUndo();
        final List<String> modes = this.analogModesForSelectedSource(selected);
        final CompoundTag config = selected.getModuleConfig();
        final String current = config.getString("visualMode").isEmpty() ? "dial" : config.getString("visualMode");
        final int index = Math.max(0, modes.indexOf(current));
        final String next = modes.get((index + 1) % modes.size());
        config.putString("visualMode", next);
        selected.setModuleConfig(config);
        this.statusMessage = "Analog mode " + next;
        this.syncDraftLayout();
    }

    private List<String> availableSourceIdList() {
        return new ArrayList<String>(this.panel.getSourceRegistry().keySet());
    }

    private String bindingFieldLabel(String field) {
        return switch (field) {
            case CustomModuleBinding.PRIMARY_SOURCE -> "Primary src";
            case CustomModuleBinding.SECONDARY_SOURCE -> "Second src";
            case CustomModuleBinding.SUPPORT_SOURCE -> "Support src";
            case CustomModuleBinding.COMPARISON_SOURCE -> "Compare src";
            case CustomModuleBinding.PRIMARY_METRIC -> "Primary slot";
            case CustomModuleBinding.SECONDARY_METRIC -> "Second slot";
            case CustomModuleBinding.STATUS_METRIC -> "Status slot";
            default -> field;
        };
    }

    private List<String> slotSuggestionsForField(String field) {
        return this.slotSuggestionsForField(field, null);
    }

    private List<String> slotSuggestionsForField(String field, SourceConfig selectedSource) {
        final ArrayList<String> slots = new ArrayList<String>();
        slots.add("");
        final String familyId = selectedSource != null ? CustomScreenSourceFamilyCatalog.familyForSource(selectedSource).getFamilyId() : CustomScreenSourceFamilyCatalog.GENERIC_UNKNOWN;
        if (CustomModuleBinding.PRIMARY_METRIC.equals(field)) {
            if (familyId.startsWith("kinetics.")) {
                slots.add("rpm");
                slots.add("stress");
                slots.add("capacity");
                slots.add("throughput");
            }
            else if (familyId.startsWith("energy.")) {
                slots.add("stored_energy");
                slots.add("energy");
                slots.add("capacity");
                slots.add("throughput");
            }
            else if (familyId.startsWith("diesel.") || "fluid.tank".equals(familyId) || "steam.boiler".equals(familyId)) {
                slots.add("fuel");
                slots.add("fluid");
                slots.add("fill");
                slots.add("capacity");
            }
            else if (familyId.startsWith("flight.")) {
                slots.add("velocity");
                slots.add("altitude");
                slots.add("horizon");
                slots.add("heading");
            }
            else {
                slots.add("value");
                slots.add("status");
                slots.add("health");
                slots.add("throughput");
            }
        }
        else if (CustomModuleBinding.SECONDARY_METRIC.equals(field)) {
            if (familyId.startsWith("energy.")) {
                slots.add("capacity");
                slots.add("output");
                slots.add("input");
                slots.add("efficiency");
            }
            else if (familyId.startsWith("kinetics.")) {
                slots.add("capacity");
                slots.add("load");
                slots.add("speed");
                slots.add("stress");
            }
            else if (familyId.startsWith("diesel.") || "fluid.tank".equals(familyId) || "steam.boiler".equals(familyId)) {
                slots.add("reserve");
                slots.add("output");
                slots.add("temperature");
                slots.add("pressure");
            }
            else if (familyId.startsWith("flight.")) {
                slots.add("altitude");
                slots.add("range");
                slots.add("speed");
                slots.add("bearing");
            }
            else {
                slots.add("capacity");
                slots.add("output");
                slots.add("range");
                slots.add("health");
            }
        }
        else if (CustomModuleBinding.STATUS_METRIC.equals(field)) {
            slots.add("status");
            slots.add("state");
            slots.add("alert");
            slots.add("mode");
            slots.add("health");
        }
        return slots;
    }

    private List<String> analogModesForSelectedSource(CustomModuleInstance selected) {
        final CompoundTag config = selected != null ? selected.getModuleConfig() : new CompoundTag();
        final String sourceType = config.getString("sourceType");
        final String familyId = CustomScreenSourceFamilyCatalog.familyForSourceType(sourceType).getFamilyId();
        if (familyId.startsWith("energy.")) {
            return List.of("accumulator", "dial");
        }
        if ("kinetics.stressometer".equals(familyId) || "kinetics.load_balancer".equals(familyId)) {
            return List.of("stress", "rpm", "dial");
        }
        if ("kinetics.speedometer".equals(familyId) || "kinetics.clutch".equals(familyId)) {
            return List.of("rpm", "stress", "dial");
        }
        if (familyId.startsWith("diesel.") || "fluid.tank".equals(familyId)) {
            return List.of("tank", "heat", "dial");
        }
        if ("steam.boiler".equals(familyId)) {
            return List.of("heat", "tank", "dial");
        }
        if ("flight.altitude_sensor".equals(familyId)) {
            return List.of("altitude", "dial");
        }
        if ("flight.velocity_sensor".equals(familyId)) {
            return List.of("rpm", "dial");
        }
        if ("flight.gimbal_sensor".equals(familyId) || "flight.aero_deck".equals(familyId) || "flight.aeroline_director".equals(familyId)) {
            return List.of("horizon", "altitude", "dial");
        }
        return List.of("dial");
    }

    private static String bindingLabelForSlot(String slot) {
        if (slot == null || slot.isEmpty()) {
            return "";
        }
        final String normalized = slot.replace('_', ' ');
        return Character.toUpperCase(normalized.charAt(0)) + normalized.substring(1);
    }

    private void drawModalButton(GuiGraphics graphics, int x, int y, int width, int height, String text, DisplayTheme theme, boolean active) {
        final int fill = active ? withAlpha(theme.accent, 210) : withAlpha(theme.border, 120);
        graphics.fill(x, y, x + width, y + height, fill);
        drawBorder(graphics, x, y, width, height, active ? theme.accent : theme.border);
        drawCentered(graphics, text, x, y + 4, width, active ? 0xFFFFFFFF : theme.dim);
    }

    private String selectedPresetCompatibilityMessage() {
        final CustomScreenPresetCatalog.PresetDefinition preset = CustomScreenPresetCatalog.find(this.selectedPresetId);
        if (preset == null) {
            return "";
        }
        if (preset.supportsAnyFamily()) {
            return "";
        }
        final String focusedSourceId = this.activeStudioSourceScope();
        if (focusedSourceId == null || focusedSourceId.isEmpty()) {
            return "No focus: explicit scene bindings stay unresolved";
        }
        final SourceConfig config = this.panel.getSourceRegistry().get(focusedSourceId);
        if (config == null) {
            return "Focused source is offline";
        }
        final SourceFamilyDefinition family = CustomScreenSourceFamilyCatalog.familyForSource(config);
        if (preset.supportsFamily(family.getFamilyId())) {
            return "";
        }
        return "Needs " + this.presetFamilyLabel(preset) + ", focused " + family.getDisplayName();
    }

    private boolean selectedPresetCanApply() {
        return this.presetCompatibleWithFocus(CustomScreenPresetCatalog.find(this.selectedPresetId));
    }

    private boolean presetCompatibleWithFocus(CustomScreenPresetCatalog.PresetDefinition preset) {
        if (preset == null) {
            return false;
        }
        if (preset.supportsAnyFamily()) {
            return true;
        }
        final String focusedSourceId = this.activeStudioSourceScope();
        if (focusedSourceId == null || focusedSourceId.isEmpty()) {
            return false;
        }
        final SourceConfig config = this.panel.getSourceRegistry().get(focusedSourceId);
        if (config == null) {
            return false;
        }
        final SourceFamilyDefinition family = CustomScreenSourceFamilyCatalog.familyForSource(config);
        return preset.supportsFamily(family.getFamilyId());
    }

    private String presetFamilyLabel(CustomScreenPresetCatalog.PresetDefinition preset) {
        if (preset == null || preset.supportsAnyFamily()) {
            return "any family";
        }
        final ArrayList<String> labels = new ArrayList<String>();
        for (String familyId : preset.supportedSourceFamilies()) {
            final SourceFamilyDefinition definition = CustomScreenSourceFamilyCatalog.definition(familyId);
            labels.add(definition != null ? definition.getDisplayName() : familyId);
        }
        return labels.isEmpty() ? "any family" : String.join(" / ", labels);
    }

    private String layoutHealthSummary() {
        final List<RepairEntry> repairs = this.layoutRepairEntries();
        int wrongFamily = 0;
        int offline = 0;
        int unresolved = 0;
        for (RepairEntry repair : repairs) {
            switch (repair.status()) {
                case INCOMPATIBLE -> wrongFamily++;
                case OFFLINE -> offline++;
                case UNRESOLVED -> unresolved++;
                default -> {
                }
            }
        }
        if (wrongFamily == 0 && offline == 0 && unresolved == 0) {
            return "healthy";
        }
        final ArrayList<String> parts = new ArrayList<String>();
        if (wrongFamily > 0) {
            parts.add(wrongFamily + " wrong family");
        }
        if (offline > 0) {
            parts.add(offline + " offline");
        }
        if (unresolved > 0) {
            parts.add(unresolved + " unresolved");
        }
        return "repair " + String.join(" / ", parts);
    }

    private boolean needsLayoutRepair() {
        return !this.layoutRepairEntries().isEmpty();
    }

    private RepairEntry firstRepairEntry() {
        final List<RepairEntry> repairs = this.layoutRepairEntries();
        return repairs.isEmpty() ? null : repairs.get(0);
    }

    private List<RepairEntry> repairEntriesForModule(String moduleId) {
        if (moduleId == null || moduleId.isEmpty()) {
            return List.of();
        }
        final ArrayList<RepairEntry> matches = new ArrayList<RepairEntry>();
        for (RepairEntry repair : this.layoutRepairEntries()) {
            if (moduleId.equals(repair.moduleId())) {
                matches.add(repair);
            }
        }
        return matches;
    }

    private List<RepairEntry> layoutRepairEntries() {
        final ArrayList<RepairEntry> repairs = new ArrayList<RepairEntry>();
        for (CustomModuleInstance module : this.draftLayout.getModules()) {
            final CustomScreenModuleContract contract = CustomScreenModuleCatalog.getContract(module.getModuleType());
            if (contract == null) {
                repairs.add(new RepairEntry(module.getId(), "", CustomScreenModuleRenderer.BindingStatus.UNRESOLVED, "Missing", module.getModuleType()));
                continue;
            }
            for (String field : contract.getBindingFields()) {
                final CustomScreenModuleRenderer.BindingSummary summary = CustomScreenModuleRenderer.summarizeBindingField(module.resolveBinding(field), this.availableSourceIds(), this.panel.getSourceRegistry());
                if (CustomScreenModuleRenderer.isRepairStatus(summary.status())) {
                    repairs.add(new RepairEntry(module.getId(), field, summary.status(), summary.label(), summary.detail()));
                }
            }
        }
        repairs.sort(Comparator.comparingInt((RepairEntry repair) -> CustomScreenModuleRenderer.repairPriority(repair.status()))
                .thenComparing(RepairEntry::moduleId)
                .thenComparing(RepairEntry::field));
        return repairs;
    }

    private String activeStudioSourceScope() {
        if (this.selectedSourceId != null && !this.selectedSourceId.isEmpty()) {
            return this.selectedSourceId;
        }
        return this.draftSettings.getPreferredSourceId();
    }

    private void drawBorder(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        graphics.fill(x, y, x + width, y + 1, color);
        graphics.fill(x, y + height - 1, x + width, y + height, color);
        graphics.fill(x, y + 1, x + 1, y + height - 1, color);
        graphics.fill(x + width - 1, y + 1, x + width, y + height - 1, color);
    }

    private void drawCentered(GuiGraphics graphics, String text, int x, int y, int width, int color) {
        graphics.drawString(this.font, text, x + Math.max(0, (width - this.font.width(text)) / 2), y, color, false);
    }

    private void drawFittedString(GuiGraphics graphics, String text, int x, int y, int maxWidth, int color) {
        graphics.drawString(this.font, this.fitText(text, maxWidth), x, y, color, false);
    }

    private String fitText(String text, int maxWidth) {
        String value = text != null ? text : "";
        if (this.font.width(value) <= maxWidth) {
            return value;
        }
        while (value.length() > 1 && this.font.width(value + ".") > maxWidth) {
            value = value.substring(0, value.length() - 1);
        }
        return value + ".";
    }

    private static int domainColor(CustomScreenSourceCapability.PaletteDomain domain, DisplayTheme theme) {
        return switch (domain) {
            case FLIGHT -> 0xFF4DABF7;
            case ENGINES -> 0xFFFF922B;
            case BATTLE -> 0xFFFF6B6B;
            case FACTORY -> 0xFF69DB7C;
            case FLUIDS -> 0xFF22B8CF;
            case SENSORS -> 0xFFFFD43B;
            case GENERIC -> theme.accent;
        };
    }

    private static int withAlpha(int color, int alpha) {
        final int clamped = Math.max(0, Math.min(255, alpha));
        return (clamped << 24) | (color & 0x00FFFFFF);
    }

    private static int darken(int color, float factor) {
        final float clamped = Math.max(0.0f, Math.min(1.0f, factor));
        final int r = Math.round(((color >> 16) & 0xFF) * clamped);
        final int g = Math.round(((color >> 8) & 0xFF) * clamped);
        final int b = Math.round((color & 0xFF) * clamped);
        return (color & 0xFF000000) | (r << 16) | (g << 8) | b;
    }

    private record SourceSummary(CustomScreenSourceCapability capability, int sectionCount) {
    }

    private record SourcePickerEntry(String sourceId, String sourceType, String label, boolean compatible, boolean stale, String compatibilityLabel) {
    }

    private record RepairEntry(String moduleId,
                               String field,
                               CustomScreenModuleRenderer.BindingStatus status,
                               String label,
                               String detail) {
    }

    private record SlotPickerEntry(String slot,
                                   String label,
                                   String category,
                                   String sourceId,
                                   String sourceType,
                                   String sourceLabel,
                                   String detail,
                                   boolean sourceSpecific,
                                   boolean selectable) {
        private static SlotPickerEntry header(String category) {
            return new SlotPickerEntry("", category, category, "", "", "", "", false, false);
        }

        private static SlotPickerEntry metric(String slot,
                                              String label,
                                              String category,
                                              String sourceId,
                                              String sourceType,
                                              String sourceLabel,
                                              String detail,
                                              boolean sourceSpecific) {
            return new SlotPickerEntry(safeString(slot),
                    safeString(label),
                    safeString(category),
                    safeString(sourceId),
                    safeString(sourceType),
                    safeString(sourceLabel),
                    safeString(detail),
                    sourceSpecific,
                    true);
        }
    }

    private static final class SourceSummaryBuilder {
        private final CustomScreenSourceCapability capability;
        private int sectionCount;

        private SourceSummaryBuilder(CustomScreenSourceCapability capability) {
            this.capability = capability;
            this.sectionCount = 0;
        }

        private void increment() {
            ++this.sectionCount;
        }

        private SourceSummary build() {
            return new SourceSummary(this.capability, this.sectionCount);
        }
    }

    private enum RailMode {
        SOURCES("Sources", "Src"),
        SECTIONS("Sections", "Sec"),
        LIBRARY("Library", "Lib");

        private final String label;
        private final String shortLabel;

        RailMode(String label, String shortLabel) {
            this.label = label;
            this.shortLabel = shortLabel;
        }

        private String labelForWidth(net.minecraft.client.gui.Font font, int width) {
            return font.width(this.label) + 4 <= width ? this.label : this.shortLabel;
        }
    }

    private record ModuleView(CustomModuleInstance module,
                              CustomModuleInstance.GridBounds bounds,
                              CustomScreenModuleContract contract,
                              CustomScreenModuleRenderer.BindingSummary binding,
                              int renderX,
                              int renderY,
                              int renderW,
                              int renderH,
                              boolean hidden,
                              CustomModuleInstance.VariantOverride override) {
    }

    private class StudioButton extends Button {
        private StudioButton(int x, int y, int width, int height, Component message, OnPress onPress) {
            super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            final DisplayTheme theme = CustomScreenStudioScreen.this.studioTheme();
            final boolean hot = this.isHoveredOrFocused();
            final int fill = !this.active ? withAlpha(theme.bg, 92) : hot ? withAlpha(theme.accent, 78) : withAlpha(theme.bg, 170);
            final int border = !this.active ? withAlpha(theme.border, 110) : hot ? theme.accent : withAlpha(theme.border, 205);
            final int text = !this.active ? theme.dim : hot ? 0xFFFFFFFF : theme.text;
            graphics.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height, fill);
            drawBorder(graphics, this.getX(), this.getY(), this.width, this.height, border);
            if (hot && this.active) {
                graphics.fill(this.getX() + 2, this.getY() + 2, this.getX() + this.width - 2, this.getY() + 3, withAlpha(theme.accent, 120));
            }
            graphics.drawCenteredString(CustomScreenStudioScreen.this.font,
                    this.getMessage(),
                    this.getX() + this.width / 2,
                    this.getY() + Math.max(2, (this.height - CustomScreenStudioScreen.this.font.lineHeight) / 2),
                    text);
        }
    }

    private static final class GridShape {
        private final int columns;
        private final int rows;

        private GridShape(int columns, int rows) {
            this.columns = columns;
            this.rows = rows;
        }

        private static GridShape forVariant(CustomScreenLayout.VariantFamily family) {
            return switch (family) {
                case SQUARE -> new GridShape(14, 14);
                case TALL -> new GridShape(10, 16);
                case COMPACT -> new GridShape(10, 8);
                case BASE, WIDE -> new GridShape(18, 12);
            };
        }
    }
}
