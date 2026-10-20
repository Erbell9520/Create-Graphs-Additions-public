package com.createstats.client;

import com.createstats.CreateStats;
import com.createstats.mixin.CreativeModeInventoryScreenAccessor;
import com.createstats.registry.CatalogLayout;
import com.createstats.registry.ModCreativeTab;
import com.createstats.registry.ModCreativeTab.CatalogGrid;
import com.createstats.registry.ModCreativeTab.Section;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ContainerScreenEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

import javax.annotation.Nullable;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@EventBusSubscriber(modid = CreateStats.MOD_ID, value = Dist.CLIENT)
public final class CreativeCatalogOverlay {
    public static final int GRID_LEFT = 8;
    public static final int GRID_TOP = 17;
    public static final int GRID_RIGHT = GRID_LEFT + 9 * 18;
    private static final int ROW = 18;

    private static final int SCROLLER_X = 175;
    private static final int SCROLLER_Y = 18;
    private static final int SCROLLER_W = 12;
    private static final int SCROLLER_H = 15;

    private static final int NOTCH_REACH = 2;

    private static final int LABEL_INK = 0xFFF2F2F2;
    private static final int LABEL_FACE = 0xFF1E1F21;
    private static final int LABEL_LIT = 0xFF3A3C40;
    private static final int LABEL_EDGE = 0xFF000000;
    private static final int NOTCH_DARK = 0xFF373737;
    private static final int NOTCH_LIT = 0xFFC6C6C6;

    @Nullable
    private static CatalogGrid grid;
    @Nullable
    private static CreativeModeTab lastSelectedTab;

    private static int rememberedRow;

    private static int swallowRelease = -1;
    private static final Map<CatalogBannerSpec, CatalogBannerClock> CLOCKS = new EnumMap<>(CatalogBannerSpec.class);

    private CreativeCatalogOverlay() {
    }

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (event.getScreen() instanceof CreativeModeInventoryScreen) {
            grid = null;
            lastSelectedTab = null;
            swallowRelease = -1;
        }
    }

    @SubscribeEvent
    public static void onScreenRenderPre(ScreenEvent.Render.Pre event) {
        if (!(event.getScreen() instanceof CreativeModeInventoryScreen screen)) {
            return;
        }
        final CreativeModeTab selected = CreativeModeInventoryScreenAccessor.createStats$getSelectedTab();
        final boolean onOurTab = selected == ModCreativeTab.CREATE_STATS_TAB.get();
        if (onOurTab && selected != lastSelectedTab) {
            apply(screen, rememberedRow);
        } else if (onOurTab && (grid == null || drifted(screen.getMenu().items, grid.stacks()))) {
            apply(screen, rememberedRow);
        }
        lastSelectedTab = selected;
        if (onOurTab && grid != null) {
            rememberedRow = grid.layout().firstRowFor(scroll(screen));
        }
    }

    private static boolean drifted(List<ItemStack> menu, List<ItemStack> ours) {
        if (menu.size() != ours.size()) {
            return true;
        }
        for (int i = 0; i < ours.size(); i++) {
            if (!ours.get(i).isEmpty()) {
                return menu.get(i) != ours.get(i);
            }
        }
        return false;
    }

    private static void apply(CreativeModeInventoryScreen screen, int firstRow) {
        grid = ModCreativeTab.catalogGrid();
        final CreativeModeInventoryScreen.ItemPickerMenu menu = screen.getMenu();
        menu.items.clear();
        menu.items.addAll(grid.stacks());
        scrollToRow(screen, firstRow);
    }

    private static void scrollToRow(CreativeModeInventoryScreen screen, int firstRow) {
        final CatalogLayout layout = grid.layout();
        final int row = Math.max(0, Math.min(firstRow, layout.lastFirstRow()));
        final float offs = layout.scrollFor(row);
        screen.getMenu().scrollTo(offs);
        ((CreativeModeInventoryScreenAccessor) screen).createStats$setScrollOffs(offs);
        rememberedRow = row;
    }

    private static float scroll(CreativeModeInventoryScreen screen) {
        return ((CreativeModeInventoryScreenAccessor) screen).createStats$getScrollOffs();
    }

    @Nullable
    private static CatalogGrid active() {
        if (grid == null || CreativeModeInventoryScreenAccessor.createStats$getSelectedTab()
            != ModCreativeTab.CREATE_STATS_TAB.get()) {
            return null;
        }
        return grid;
    }

    @SubscribeEvent
    public static void onBackground(ContainerScreenEvent.Render.Background event) {
        if (!(event.getContainerScreen() instanceof CreativeModeInventoryScreen screen) || active() == null) {
            return;
        }
        final GuiGraphics g = event.getGuiGraphics();
        final CatalogLayout layout = grid.layout();
        final int left = screen.getGuiLeft();
        final int top = screen.getGuiTop();
        for (int s = 0; s < layout.sectionCount(); s++) {
            final int y = layout.notchY(s);
            if (y < 0) {
                continue;
            }

            final int ny = top + SCROLLER_Y + y;
            for (int x : new int[] {left + SCROLLER_X, left + SCROLLER_X + SCROLLER_W - 3}) {
                g.fill(x, ny, x + 3, ny + 1, NOTCH_DARK);
                g.fill(x, ny + 1, x + 3, ny + 2, NOTCH_LIT);
            }
        }

        final CreativeModeTab tab = ModCreativeTab.CREATE_STATS_TAB.get();
        g.blitSprite(tab.getScrollerSprite(), left + SCROLLER_X, top + SCROLLER_Y + scrollerOffset(screen),
            SCROLLER_W, SCROLLER_H);
    }

    private static int scrollerOffset(CreativeModeInventoryScreen screen) {
        return (int) ((float) CatalogLayout.SCROLL_TRAVEL * scroll(screen));
    }

    private static int notchAt(CreativeModeInventoryScreen screen, double mouseX, double mouseY) {
        final double mx = mouseX - screen.getGuiLeft();
        final double my = mouseY - screen.getGuiTop();
        if (mx < SCROLLER_X || mx >= SCROLLER_X + SCROLLER_W) {
            return -1;
        }
        final int scrollerTop = SCROLLER_Y + scrollerOffset(screen);
        if (my >= scrollerTop && my < scrollerTop + SCROLLER_H) {
            return -1;
        }
        final CatalogLayout layout = grid.layout();
        for (int s = 0; s < layout.sectionCount(); s++) {
            final int y = layout.notchY(s);
            if (y >= 0 && Math.abs(my - (SCROLLER_Y + y + 0.5)) <= NOTCH_REACH + 0.5) {
                return s;
            }
        }
        return -1;
    }

    @SubscribeEvent
    public static void onForeground(ContainerScreenEvent.Render.Foreground event) {
        if (!(event.getContainerScreen() instanceof CreativeModeInventoryScreen screen) || active() == null) {
            return;
        }
        final CatalogLayout layout = grid.layout();
        final int first = layout.firstRowFor(scroll(screen));
        final int hovered = bannerAt(screen, first, event.getMouseX(), event.getMouseY());
        final long now = Util.getMillis();

        for (int s = 0; s < grid.sections().size(); s++) {
            final CatalogBannerSpec spec = CatalogBannerSpec.of(grid.sections().get(s).key());
            if (spec != null) {
                clock(spec).update(s == hovered, now);
            }
        }
        final GuiGraphics g = event.getGuiGraphics();
        for (int r = 0; r < CatalogLayout.VISIBLE_ROWS; r++) {
            final int s = layout.bannerAt(first + r);
            if (s >= 0) {
                drawBanner(g, grid.sections().get(s), GRID_LEFT, GRID_TOP + r * ROW, now);
            }
        }
    }

    private static CatalogBannerClock clock(CatalogBannerSpec spec) {
        return CLOCKS.computeIfAbsent(spec, CatalogBannerClock::new);
    }

    private static int bannerAt(CreativeModeInventoryScreen screen, int firstRow, double mouseX, double mouseY) {
        final double mx = mouseX - screen.getGuiLeft();
        final double my = mouseY - screen.getGuiTop();
        if (mx < GRID_LEFT || mx >= GRID_RIGHT || my < GRID_TOP || my >= GRID_TOP + CatalogLayout.VISIBLE_ROWS * ROW) {
            return -1;
        }
        return grid.layout().bannerAt(firstRow + (int) ((my - GRID_TOP) / ROW));
    }

    private static void drawBanner(GuiGraphics g, Section section, int x, int y, long now) {
        final CatalogBannerSpec spec = CatalogBannerSpec.of(section.key());
        if (spec != null) {
            final ResourceLocation strip = ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, spec.texturePath());
            final int w = CatalogBannerSpec.FRAME_WIDTH;
            final int h = CatalogBannerSpec.FRAME_HEIGHT;
            g.blit(strip, x, y, 0, 0, w, h, w, spec.textureHeight());
            final CatalogBannerClock clock = clock(spec);
            final int live = clock.liveFrame(now);
            if (live > 0 && clock.power() > 0.0F) {
                RenderSystem.enableBlend();
                g.setColor(1.0F, 1.0F, 1.0F, clock.power());
                g.blit(strip, x, y, 0, live * h, w, h, w, spec.textureHeight());
                g.setColor(1.0F, 1.0F, 1.0F, 1.0F);
                RenderSystem.disableBlend();
            }
        }
        label(g, section.title(), x, y);
    }

    private static void label(GuiGraphics g, Component name, int x, int y) {
        final Font font = Minecraft.getInstance().font;
        Component text = name;
        if (font.width(text) > CatalogLayout.LABEL_MAX_TEXT) {
            text = Component.literal(font.plainSubstrByWidth(name.getString(),
                CatalogLayout.LABEL_MAX_TEXT - font.width("...")) + "...");
        }
        final int x0 = x + CatalogLayout.LABEL_X;
        final int y0 = y + 3;
        final int x1 = x0 + font.width(text) + CatalogLayout.LABEL_PAD;
        final int y1 = y + 15;
        g.fill(x0, y0, x1, y1, LABEL_EDGE);
        g.fill(x0 + 1, y0 + 1, x1 - 1, y1 - 1, LABEL_FACE);
        g.fill(x0 + 1, y0 + 1, x1 - 1, y0 + 2, LABEL_LIT);
        g.fill(x0 + 1, y0 + 1, x0 + 2, y1 - 1, LABEL_LIT);
        g.drawString(font, text, x0 + 5, y0 + 2, LABEL_INK, false);
    }

    // cancel the press AND swallow the release, container screens place stacks on release
    @SubscribeEvent
    public static void onMousePressed(ScreenEvent.MouseButtonPressed.Pre event) {
        if (!(event.getScreen() instanceof CreativeModeInventoryScreen screen) || active() == null) {
            return;
        }
        final CatalogLayout layout = grid.layout();
        int section = bannerAt(screen, layout.firstRowFor(scroll(screen)), event.getMouseX(), event.getMouseY());
        if (section < 0) {
            section = notchAt(screen, event.getMouseX(), event.getMouseY());
        }
        if (section >= 0) {
            if (event.getButton() == 0) {
                scrollToRow(screen, layout.jumpTarget(section));
            }
            event.setCanceled(true);
            swallowRelease = event.getButton();
        }
    }

    @SubscribeEvent
    public static void onMouseReleased(ScreenEvent.MouseButtonReleased.Pre event) {
        if (swallowRelease >= 0 && event.getButton() == swallowRelease) {
            swallowRelease = -1;
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRenderPost(ScreenEvent.Render.Post event) {
        if (!(event.getScreen() instanceof CreativeModeInventoryScreen screen) || active() == null) {
            return;
        }
        final int s = notchAt(screen, event.getMouseX(), event.getMouseY());
        if (s < 0) {
            return;
        }
        final Section section = grid.sections().get(s);
        final int count = ModCreativeTab.itemsForSection(section).size();
        event.getGuiGraphics().renderComponentTooltip(Minecraft.getInstance().font, List.of(section.title(),
                Component.translatable("itemGroup.create_stats.count", count).withStyle(ChatFormatting.DARK_GRAY)),
            event.getMouseX(), event.getMouseY());
    }

    @Nullable
    public static Component runningHead() {
        final CatalogGrid active = active();
        if (active == null || !(Minecraft.getInstance().screen instanceof CreativeModeInventoryScreen screen)) {
            return null;
        }
        final int s = active.layout().runningHead(active.layout().firstRowFor(scroll(screen)));
        if (s < 0) {
            return null;
        }
        final Font font = Minecraft.getInstance().font;
        final Component head = active.sections().get(s).title();
        final int titleRight = CatalogLayout.TITLE_X
            + font.width(ModCreativeTab.CREATE_STATS_TAB.get().getDisplayName());
        return titleRight + CatalogLayout.RUNNING_HEAD_GAP + font.width(head) <= CatalogLayout.RUNNING_HEAD_RIGHT
            ? head : null;
    }

    public static int runningHeadColour() {
        return ModCreativeTab.CREATE_STATS_TAB.get().getLabelColor();
    }
}
