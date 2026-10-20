package com.createstats.book.screen;

import com.createstats.CreateStats;
import com.createstats.book.BookTokens;
import com.createstats.book.content.BackMatter;
import com.createstats.book.content.Book;
import com.createstats.book.content.FaultIndex;
import com.createstats.book.content.Section;
import com.createstats.book.content.Sheet;
import com.createstats.book.content.Symptom;
import com.createstats.book.layout.BookCanvas;
import com.createstats.book.layout.BookGeometry;
import com.createstats.book.layout.BookPages;
import com.createstats.book.layout.BookPainter;
import com.createstats.book.layout.BookText;
import com.createstats.book.layout.Ctx;
import com.createstats.book.layout.IndexPage;
import com.createstats.book.layout.IndexSearch;
import com.createstats.book.layout.IndexState;
import com.createstats.book.layout.Page;
import com.createstats.book.scene.BlockFigures;
import com.createstats.book.specimen.BlockSpecimen;
import com.createstats.book.specimen.ManualSpecimen;
import com.createstats.book.specimen.PowerMeterSpecimen;
import com.createstats.client.CreateStatsGuiScale;
import com.createstats.client.LocalGuiDensity;
import com.createstats.registry.ModCreativeTab;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@OnlyIn(Dist.CLIENT)
public class StudentsManualScreen extends Screen implements LocalGuiDensity {
    private static final int SCRIM = 0xA0101214;

    private static final int MIN_CANVAS_W = 640;
    private static final int MIN_CANVAS_H = 360;

    private static final int SEARCH_MAX = 32;

    private static final int PULSE_TICKS = 24;

    private final String openAt;

    private final Screen parent;

    private BookGeometry geo;
    private Ctx layout;
    private List<Page> pages = List.of();

    private int at = -1;
    private int ribbonPage = -1;

    private final Deque<Integer> turns = new ArrayDeque<>();
    private int turnFrom;
    private int turnTicks;
    private int turnLength;

    private String hover;
    private String pulse;
    private int pulseTicks;
    private List<GuiBookCanvas.Hot> hots = List.of();
    private final Deque<BookMemory.Place> history = new ArrayDeque<>();
    private final Map<String, ManualSpecimen> specimens = new HashMap<>();
    private final Map<Section, List<String>> ponder = new EnumMap<>(Section.class);

    private SearchBox search;
    private String query = "";
    private Symptom symptom;
    private Section sectionFilter;
    private int offset;

    private Section near;
    private String nearQuery;

    private Ctx lastFrame;

    public StudentsManualScreen(String openAt) {
        this(openAt, null);
    }

    public StudentsManualScreen(String openAt, Screen parent) {
        super(Component.translatable("create_stats.book.title"));
        this.openAt = openAt;
        this.parent = parent;
    }

    public static StudentsManualScreen at(String id, Screen from) {
        return new StudentsManualScreen(id, from);
    }

    @Override
    public void onClose() {
        if (parent != null && minecraft != null) {
            minecraft.setScreen(parent);
            return;
        }
        super.onClose();
    }

    @Override
    public int maxEffectiveGuiScale() {
        final Window window = net.minecraft.client.Minecraft.getInstance().getWindow();
        return Math.max(1, Math.min(window.getWidth() / MIN_CANVAS_W, window.getHeight() / MIN_CANVAS_H));
    }

    @Override
    protected void init() {
        BookGrain.ensure();

        com.createstats.book.specimen.WireTensionSpecimen.useGameText();
        final BookMemory.Place keep = at >= 0 && at < pages.size()
                ? new BookMemory.Place(pages.get(at).entryId(), pages.get(at).index()) : null;
        final int scale = Math.max(1, (int) Math.round(minecraft.getWindow().getGuiScale()
                / CreateStatsGuiScale.factor(this)));
        this.geo = BookGeometry.of(width, height, scale);
        this.layout = new Ctx(new MeasureCanvas(font), text(), geo, this::ponderIds);
        this.pages = BookPages.build(layout);
        final BookMemory.Place ribbon = BookMemory.ribbon(player());
        this.ribbonPage = ribbon == null ? -1 : BookPages.find(pages, ribbon.entry(), ribbon.sheet());

        final boolean wasFocused = search != null && search.isFocused();
        this.search = new SearchBox(font);
        search.setMaxLength(SEARCH_MAX);
        search.setBordered(false);
        search.setCanLoseFocus(true);
        search.setValue(query);
        search.setResponder(s -> {
            query = s;
            offset = 0;
            if (!s.equals(nearQuery)) {
                near = null;
            }
        });
        search.visible = false;
        addWidget(search);

        int start;
        boolean focusSearch = wasFocused;
        if (keep != null) {
            start = BookPages.find(pages, keep.entry(), keep.sheet());
        } else if (openAt != null) {
            start = deepLink(openAt);
            focusSearch = !query.isEmpty();
        } else if (ribbonPage >= 0) {
            start = ribbonPage;
        } else {
            start = firstContent();
        }
        this.at = align(Math.max(0, start));
        this.turns.clear();
        if (focusSearch && indexOpen()) {
            search.visible = true;
            setFocused(search);
        }
    }

    private int deepLink(String id) {
        final Sheet sheet = Book.find(id).orElse(null);
        if (sheet != null) {
            final int p = BookPages.find(pages, sheet.id());
            if (p >= 0) {
                return p;
            }
        }
        final String name = nameOf(id);
        final int index = BookPages.find(pages, BackMatter.INDEX_ID);
        if (name != null && index >= 0) {
            final String q = name.length() > SEARCH_MAX ? name.substring(0, SEARCH_MAX) : name;
            nearQuery = q;
            search.setValue(q);
            near = sectionOf(id);
            symptom = null;
            sectionFilter = null;
            return index;
        }
        return ribbonPage >= 0 ? ribbonPage : firstContent();
    }

    private static Section sectionOf(String id) {
        final ResourceLocation rl = ResourceLocation.tryParse(id.contains(":") ? id : "create_stats:" + id);
        final Item item = rl == null ? Items.AIR : BuiltInRegistries.ITEM.get(rl);
        if (item == Items.AIR) {
            return null;
        }
        for (final ModCreativeTab.Section cs : ModCreativeTab.catalogSections()) {
            for (final ItemStack stack : ModCreativeTab.itemsForSection(cs)) {
                if (stack.is(item)) {
                    try {
                        return Section.ofCreativeKey(cs.key());
                    } catch (IllegalArgumentException e) {
                        return null;
                    }
                }
            }
        }
        return null;
    }

    private static String nameOf(String id) {
        final ResourceLocation rl = ResourceLocation.tryParse(id.contains(":") ? id : "create_stats:" + id);
        if (rl == null) {
            return null;
        }
        final Block block = BuiltInRegistries.BLOCK.get(rl);
        if (block != Blocks.AIR) {
            return block.getName().getString();
        }
        final Item item = BuiltInRegistries.ITEM.get(rl);
        return item == Items.AIR ? null : new ItemStack(item).getHoverName().getString();
    }

    private int firstContent() {
        for (final Section s : Section.values()) {
            if (!Book.in(s).isEmpty()) {
                return BookPages.sectionStart(pages, s);
            }
        }
        return 0;
    }

    private int align(int page) {
        return page - page % geo.perView();
    }

    private UUID player() {
        return minecraft != null && minecraft.player != null ? minecraft.player.getUUID() : null;
    }

    private BookText text() {
        return new BookText() {
            @Override
            public String get(String key, Object... args) {
                return I18n.get(key, args);
            }

            @Override
            public String itemName(String registryId) {
                final ResourceLocation id = ResourceLocation.tryParse(registryId);
                final Item item = id == null ? Items.AIR : BuiltInRegistries.ITEM.get(id);
                return item == Items.AIR ? registryId : new ItemStack(item).getHoverName().getString();
            }
        };
    }

    private List<String> ponderIds(Section section) {
        return ponder.computeIfAbsent(section, s -> {
            final List<String> out = new ArrayList<>();
            if (s.creativeKey() == null) {
                return out;
            }
            for (final ModCreativeTab.Section cs : ModCreativeTab.catalogSections()) {
                if (!cs.key().equals(s.creativeKey())) {
                    continue;
                }
                for (final ItemStack stack : ModCreativeTab.itemsForSection(cs)) {
                    if (PonderLink.hasScenes(stack)) {
                        out.add(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
                    }
                }
            }
            return out;
        });
    }

    private ManualSpecimen specimen(String id) {
        return specimens.computeIfAbsent(id, k -> {
            try {
                if ("power_meter".equals(k)) {
                    return PowerMeterSpecimen.create();
                }
                if (com.createstats.book.specimen.BatteryPlates.isPlate(k)) {
                    return com.createstats.book.specimen.BatteryControllerSpecimen.create(k);
                }
                if (com.createstats.book.specimen.RingPlates.isPlate(k)) {
                    return com.createstats.book.specimen.WireTensionSpecimen.create(k);
                }
                if (com.createstats.book.specimen.PduPlates.isPlate(k)) {
                    return com.createstats.book.specimen.PduSpecimen.create(k);
                }
                return BlockFigures.byId(k).<ManualSpecimen>map(BlockSpecimen::new).orElse(null);
            } catch (RuntimeException e) {
                CreateStats.LOGGER.warn("Student's Manual: could not build the {} figure", k, e);
                return null;
            }
        });
    }

    private int shownAt() {
        return turns.isEmpty() ? at : turns.peekLast();
    }

    private boolean indexOpen() {
        final int from = shownAt();
        for (int i = from; i < from + geo.perView() && i < pages.size(); i++) {
            final Page p = pages.get(i);
            if (p.sheet() != null && p.sheet().id().equals(BackMatter.INDEX_ID)) {
                return true;
            }
        }
        return false;
    }

    private IndexState indexState() {
        final boolean focused = search.isFocused() && search.visible;
        final boolean caret = focused && (Util.getMillis() / 300L) % 2L == 0L;
        return new IndexState(query, search.getCursorPosition(), search.anchor, focused, caret, symptom,
                sectionFilter, offset, near);
    }

    private void openIndex() {
        final int page = BookPages.find(pages, BackMatter.INDEX_ID);
        if (page < 0) {
            return;
        }
        go(page, true);
        search.visible = true;
        setFocused(search);
        search.moveCursorToEnd(false);
    }

    private void openFirstResult() {
        if (lastFrame == null) {
            return;
        }
        final IndexSearch.Result r = lastFrame.results();
        if (!r.entries().isEmpty()) {
            jumpTo(r.entries().get(0));
        } else if (!r.sheets().isEmpty()) {
            go(BookPages.find(pages, r.sheets().get(0).id()), true);
        }
    }

    private void jumpTo(FaultIndex.Entry e) {
        final int page = BookPages.byLabel(pages, layout.rowPage.get(e.target()));
        if (page < 0) {
            return;
        }
        pulse = e.target();
        pulseTicks = PULSE_TICKS;
        go(page, true);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, SCRIM);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        if (pages.isEmpty()) {
            return;
        }
        final GuiBookCanvas c = new GuiBookCanvas(g, font, this::specimen);
        final Ctx x = new Ctx(c, layout.text, geo, layout.ponder);
        x.firstPage = layout.firstPage;
        x.controlsPage = layout.controlsPage;
        x.figurePage = layout.figurePage;
        x.figureNumber = layout.figureNumber;
        x.rowPage = layout.rowPage;
        x.sectionPage = layout.sectionPage;
        x.pageH = layout.pageH;
        x.wide = layout.wide;
        x.index = indexState();
        final BookPainter.Turn turn = turning(partialTick);
        if (turn == null) {
            x.hover = hover;
            x.pulse = pulseTicks > 0 && (pulseTicks / 4) % 2 == 0 ? pulse : null;
            x.mouseX = mouseX;
            x.mouseY = mouseY;
        }
        BookPainter.paint(x, pages, at, turn, ribbonPage);
        this.hots = c.hots;
        this.lastFrame = x;
        this.hover = turn == null ? c.hotAt(mouseX, mouseY) : null;
        placeSearch(turn == null);
        if (hover != null && hover.startsWith("tab:")) {
            final Section s = Section.values()[Integer.parseInt(hover.substring(4))];
            final List<Component> tip = new ArrayList<>();
            tip.add(Component.literal(s.number() + "  " + I18n.get(s.nameKey())));
            if (Book.in(s).isEmpty()) {
                tip.add(Component.translatable("create_stats.book.empty.section").withStyle(ChatFormatting.GRAY));
            }
            g.renderComponentTooltip(font, tip, mouseX, mouseY);
        } else if (hover != null && !geo.spread() && (hover.startsWith("sec:") || hover.startsWith("secname:"))) {
            final Section s = Section.valueOf(hover.substring(hover.indexOf(':') + 1));
            g.renderComponentTooltip(font, List.of(Component.literal(s.number() + "  " + I18n.get(s.nameKey()))),
                    mouseX, mouseY);
        }
    }

    private void placeSearch(boolean settled) {
        GuiBookCanvas.Hot field = null;
        if (settled) {
            for (final GuiBookCanvas.Hot h : hots) {
                if (h.id().equals(IndexPage.SEARCH)) {
                    field = h;
                }
            }
        }
        if (field == null) {
            search.visible = indexOpen() && search.isFocused();
            if (!indexOpen() && search.isFocused()) {
                setFocused(null);
            }
            return;
        }
        search.visible = true;
        search.setX(field.x0());
        search.setY(field.y0());
        search.setWidth(field.x1() - field.x0());
        search.setHeight(field.y1() - field.y0());
    }

    private BookPainter.Turn turning(float partialTick) {
        if (turns.isEmpty() || turnLength <= 0) {
            return null;
        }
        final float p = Math.min(1.0f, (turnTicks + partialTick) / turnLength);
        return new BookPainter.Turn(turnFrom, turns.peekFirst(), p);
    }

    @Override
    public void tick() {
        super.tick();
        if (pulseTicks > 0) {
            pulseTicks--;
        }
        if (!turns.isEmpty() && ++turnTicks >= turnLength) {
            at = turns.pollFirst();
            turnFrom = at;
            turnTicks = 0;
        }
    }

    private void go(int page, boolean remember) {
        if (page < 0 || page >= pages.size()) {
            return;
        }

        if (!turns.isEmpty()) {
            at = turns.peekLast();
            turns.clear();
        }
        final int target = align(page);
        if (target == at) {
            return;
        }
        if (remember) {
            history.push(place(at));
        }
        final int step = geo.perView();
        final int sheets = Math.abs(target - at) / step;
        turnFrom = at;
        turnTicks = 0;
        if (sheets <= 1) {
            turnLength = BookTokens.TURN_TICKS;
            turns.add(target);
        } else {
            turnLength = BookTokens.RIFFLE_TICKS;
            final int shown = Math.min(BookTokens.RIFFLE_MAX, sheets);
            int last = at;
            for (int i = 1; i <= shown; i++) {
                final int next = i == shown ? target : align(at + (target - at) * i / shown);
                if (next != last) {
                    turns.add(next);
                    last = next;
                }
            }
        }
        playTurn(sheets > 1 ? 1.15f : 1.0f);
    }

    private void turn(int direction) {
        final int next = (turns.isEmpty() ? at : turns.peekLast()) + direction * geo.perView();
        if (next >= 0 && next < pages.size()) {
            go(next, false);
        }
    }

    private BookMemory.Place place(int page) {
        final Page p = pages.get(Math.max(0, Math.min(pages.size() - 1, page)));
        return new BookMemory.Place(p.entryId(), p.index());
    }

    private void back() {
        if (history.isEmpty()) {
            return;
        }
        final BookMemory.Place p = history.pop();
        final int page = BookPages.find(pages, p.entry(), p.sheet());
        if (page >= 0) {
            go(page, false);
        }
    }

    private void playTurn(float pitch) {
        if (minecraft != null) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, pitch));
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_4) {
            back();
            return true;
        }
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT || !turns.isEmpty()) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        final String id = hotAt(mouseX, mouseY);
        if (IndexPage.SEARCH.equals(id) && search.visible) {
            setFocused(search);
            search.mouseClicked(mouseX, mouseY, button);
            return true;
        }
        if (search.isFocused()) {
            setFocused(null);
        }
        if (id == null) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        if (id.equals("nav:prev")) {
            turn(-1);
        } else if (id.equals("nav:next")) {
            turn(1);
        } else if (id.startsWith("tab:")) {
            final Section s = Section.values()[Integer.parseInt(id.substring(4))];
            go(BookPages.sectionStart(pages, s), true);
        } else if (id.startsWith("toc:") || id.startsWith("chip:")) {
            final int page = BookPages.find(pages, id.substring(id.indexOf(':') + 1));
            go(page, true);
        } else if (id.startsWith("ponder:")) {
            final ResourceLocation rl = ResourceLocation.tryParse(id.substring(7));
            if (rl != null) {
                PonderLink.open(this, new ItemStack(BuiltInRegistries.ITEM.get(rl)));
            }
        } else if (id.startsWith("ctl:")) {
            showControl(id);
        } else if (id.startsWith("sym:")) {
            final Symptom s = Symptom.valueOf(id.substring(4));
            symptom = symptom == s ? null : s;
            offset = 0;
            click();
        } else if (id.startsWith("sec:")) {
            final Section s = Section.valueOf(id.substring(4));
            sectionFilter = sectionFilter == s ? null : s;
            offset = 0;
            click();
        } else if (id.startsWith("hit:") && lastFrame != null) {
            final int i = Integer.parseInt(id.substring(4));
            final List<FaultIndex.Entry> entries = lastFrame.results().entries();
            if (i >= 0 && i < entries.size()) {
                jumpTo(entries.get(i));
            }
        } else if (id.equals("res:prev") || id.equals("res:next")) {
            pageResults(id.equals("res:next") ? 1 : -1);
            click();
        }
        return true;
    }

    private void pageResults(int direction) {
        if (!hasHot(direction > 0 ? "res:next" : "res:prev")) {
            return;
        }
        final int step = lastFrame == null ? 1 : Math.max(1, lastFrame.resultsFitted);
        offset = Math.max(0, offset + direction * step);
    }

    private boolean hasHot(String id) {
        for (final GuiBookCanvas.Hot h : hots) {
            if (h.id().equals(id)) {
                return true;
            }
        }
        return false;
    }

    private void click() {
        if (minecraft != null) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
        }
    }

    private void showControl(String id) {
        pulse = id;
        pulseTicks = 16;
        final String sheetId = Ctx.controlSheet(id);
        final String figure = Ctx.controlFigure(id);
        if (sheetId == null) {
            return;
        }

        final int tablePage = BookPages.byLabel(pages, layout.controlsPage.get(figure));
        final int plotted = BookPages.byLabel(pages, layout.figurePage.get(figure));
        final int figurePage = plotted >= 0 ? plotted : BookPages.find(pages, sheetId);
        final int visibleEnd = at + geo.perView();

        if (tablePage >= 0 && (tablePage < at || tablePage >= visibleEnd)) {
            go(tablePage, true);
        } else if (figurePage >= 0 && (figurePage < at || figurePage >= visibleEnd)) {
            go(figurePage, true);
        }
    }

    private String hotAt(double x, double y) {
        for (int i = hots.size() - 1; i >= 0; i--) {
            if (hots.get(i).contains(x, y)) {
                return hots.get(i).id();
            }
        }
        return null;
    }

    private boolean within(String id, double x, double y) {
        for (final GuiBookCanvas.Hot h : hots) {
            if (h.id().equals(id) && h.contains(x, y)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY != 0.0 && turns.isEmpty()) {
            if (lastFrame != null && within(IndexPage.RESULTS, mouseX, mouseY) && resultsPaged()) {
                pageResults(scrollY < 0 ? 1 : -1);
                return true;
            }
            turn(scrollY < 0 ? 1 : -1);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private boolean resultsPaged() {
        return hasHot("res:prev") || hasHot("res:next");
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (search.isFocused() && search.visible) {
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                openFirstResult();
                return true;
            }
            if (key != GLFW.GLFW_KEY_ESCAPE && key != GLFW.GLFW_KEY_PAGE_UP && key != GLFW.GLFW_KEY_PAGE_DOWN
                    && search.keyPressed(key, scan, modifiers)) {
                return true;
            }
        }
        if (key == GLFW.GLFW_KEY_F && hasControlDown()) {
            openIndex();
            return true;
        }
        switch (key) {
            case GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_PAGE_DOWN -> {
                turn(1);
                return true;
            }
            case GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_PAGE_UP -> {
                turn(-1);
                return true;
            }
            case GLFW.GLFW_KEY_BACKSPACE -> {
                back();
                return true;
            }
            default -> {
                return super.keyPressed(key, scan, modifiers);
            }
        }
    }

    @Override
    public boolean charTyped(char ch, int modifiers) {
        if (indexOpen()) {
            if (!search.isFocused()) {
                search.visible = true;
                setFocused(search);
                search.moveCursorToEnd(false);
            }
            return search.charTyped(ch, modifiers);
        }
        return super.charTyped(ch, modifiers);
    }

    @Override
    public void removed() {
        super.removed();
        if (!pages.isEmpty() && at >= 0) {
            final int page = turns.isEmpty() ? at : turns.peekLast();
            BookMemory.setRibbon(player(), place(page));
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // vanilla keeps the selection end private, and we draw ink on paper anyway
    private static final class SearchBox extends EditBox {
        int anchor;

        SearchBox(Font font) {
            super(font, 0, 0, 100, 18, Component.translatable("create_stats.book.index.head"));
        }

        @Override
        public void setHighlightPos(int position) {
            super.setHighlightPos(position);
            anchor = Math.max(0, Math.min(getValue().length(), position));
        }
    }

    private static final class MeasureCanvas implements BookCanvas {
        private final Font font;

        MeasureCanvas(Font font) {
            this.font = font;
        }

        @Override
        public int width(String s, boolean bold) {
            if (s == null || s.isEmpty()) {
                return 0;
            }
            return bold ? font.width(Component.literal(s).withStyle(ChatFormatting.BOLD)) : font.width(s);
        }

        @Override
        public void fill(int x0, int y0, int x1, int y1, int argb) {
        }

        @Override
        public void text(String s, int x, int y, int argb, boolean bold) {
        }

        @Override
        public void textScaled(String s, int x, int y, int argb, int scale) {
        }

        @Override
        public void item(String registryId, int x, int y) {
        }

        @Override
        public void sprite(String spriteId, int x, int y, int w, int h) {
        }

        @Override
        public void paper(int x0, int y0, int x1, int y1) {
        }

        @Override
        public void vinyl(int x0, int y0, int x1, int y1) {
        }

        @Override
        public void specimen(String specimenId, int x, int y, int num, int den, int mouseX, int mouseY) {
        }

        @Override
        public void hot(String id, int x0, int y0, int x1, int y1) {
        }

        @Override
        public void pushFlip(int pivotX, float scaleX) {
        }

        @Override
        public void popFlip() {
        }

        @Override
        public void raise(int z) {
        }
    }
}
