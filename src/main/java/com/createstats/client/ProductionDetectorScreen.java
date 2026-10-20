package com.createstats.client;

import com.createstats.blockentity.ProductionDetectorBE;
import com.createstats.blockentity.inventory.InventoryBooks;
import com.createstats.blockentity.inventory.InventoryFace;
import com.createstats.gui.home.HomeKit;
import com.createstats.inventory.core.BinCardLedger;
import com.createstats.inventory.core.ChartHistory;
import com.createstats.inventory.core.ChartPaperLayout;
import com.createstats.inventory.core.EnergyReadout;
import com.createstats.inventory.core.FaceFormat;
import com.createstats.inventory.core.FlowClock;
import com.createstats.inventory.core.MarkLadder;
import com.createstats.inventory.core.SetPointRelay;
import com.createstats.network.DetectorResetPacket;
import com.createstats.network.InventoryDisplaySettingsPacket;
import com.createstats.power.FeConsumer;
import com.createstats.power.FeLock;
import com.createstats.renderer.ProductionDetectorTextureManager;
import com.createstats.renderer.inventory.LampPainter;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@OnlyIn(Dist.CLIENT)
public class ProductionDetectorScreen extends Screen {
    private static final int PW = 328;
    private static final int PH = 216;
    private static final int TAB_STEP = 27;
    private static final int LAMP_W = 33;
    private static final int LAMP_H = 12;
    private static final int LAMP_STEP = 35;
    private static final int ROW_H = 18;
    private static final int SUMMARY_X = 180;
    private static final int GRID_Y = 42;

    private static final int INK = 0xFF404040;
    private static final int GREEN = 0xFF2F7D32;
    private static final int AMBER = 0xFF8A5A00;
    private static final int RED = 0xFFA4241B;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int LIST_BG = 0xFF51493A;
    private static final int LIST_DIM = 0xFFA09A8A;
    private static final int LIST_SUB = 0xFFD8D0C0;
    private static final int LIST_RULE = 0xFF6E6553;
    private static final int LIST_RISE = 0xFF8CE07A;
    private static final int LIST_FALL = 0xFFFFC266;
    private static final int LIST_PROBLEM = 0xFFFFAFA3;
    private static final int BAR_GREEN = 0xFF4FA03C;
    private static final int BAR_AMBER = 0xFFD08A1E;
    private static final int PIP_OK = 0xFF4FC05A;
    private static final int TIP_GREY = 0xA8A8A8;
    private static final int TIP_RISE = 0x8CE07A;
    private static final int TIP_FALL = 0xFFC266;
    private static final int CARD_INK = 0xFF23284A;
    private static final int CARD_HEAD = 0xFF7A2A24;

    private static final ResourceLocation SCROLLER = ResourceLocation.withDefaultNamespace("container/creative_inventory/scroller");
    private static final ResourceLocation SCROLLER_OFF =
            ResourceLocation.withDefaultNamespace("container/creative_inventory/scroller_disabled");

    private static final int HEARTBEAT_TICKS = 100;

    private static final int HOLD_TICKS = 20;

    private static final int PENDING_TICKS = 40;

    private enum Tab {
        CONTENTS,
        WATCH,
        BOOKS
    }

    private record Line(String text, int ink) {
    }

    private record WatchLine(String markKey, ItemStack icon, String name, String count, long low, long high, String unit,
                             double frac, double lowFrac, double highFrac, String state, int stateInk, int pip,
                             int barInk) {
    }

    private record WatchFields(WatchLine line, EditBox low, EditBox high) {
    }

    private final ProductionDetectorBE be;
    private final BlockPos pos;
    private Tab tab = Tab.CONTENTS;
    private int left;
    private int top;
    private int ticks;
    private boolean viewing;
    private int scroll;
    private boolean draggingScroller;
    private int hold = -1;
    private int closedFlash;
    private int localDays;
    private int localDaysUntil;
    private int localLowMode = -1;
    private int localHighMode = -1;
    private int localStall = -1;
    private int localModesUntil;
    private final Map<String, long[]> pendingMarks = new HashMap<>();
    private final List<WatchFields> fields = new ArrayList<>();
    private String layoutSignature = "";
    private Button relayLow;
    private Button relayHigh;
    private Button stallButton;
    private Button dayButton;
    private Button threeDaysButton;
    private Button closeBooks;
    private Button preferButton;
    private Button dieselButton;

    private List<Component> tip;
    private int tipX;
    private int tipY;

    public ProductionDetectorScreen(ProductionDetectorBE be) {
        super(Component.translatable("create_stats.inventory_display.gui.title"));
        this.be = be;
        this.pos = be.getBlockPos();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        this.left = (this.width - PW) / 2;
        this.top = Math.max(30, (this.height - PH) / 2 + 14);
        this.fields.clear();
        this.relayLow = null;
        this.relayHigh = null;
        this.stallButton = null;
        this.dayButton = null;
        this.threeDaysButton = null;
        this.closeBooks = null;
        this.preferButton = null;
        this.dieselButton = null;
        final InventoryFace f = this.be.clientFace();
        this.layoutSignature = signature(f);
        if (notice(f) == null) {
            switch (this.tab) {
                case CONTENTS -> initContents(f);
                case WATCH -> initWatch(f);
                case BOOKS -> initBooks();
            }
        }
        if (!this.viewing) {
            this.viewing = true;
            send(InventoryDisplaySettingsPacket.of(this.pos, InventoryDisplaySettingsPacket.OP_VIEW));
        }
    }

    private String signature(InventoryFace f) {
        final StringBuilder b = new StringBuilder();
        b.append(this.tab).append('|').append(notice(f) == null);
        if (f != null) {
            b.append('|').append(f.page).append('|').append(f.bothSources).append('|').append(f.quantityKey);
            for (InventoryFace.WatchRow r : f.rows) {
                b.append('|').append(r.markKey());
            }
        }
        return b.toString();
    }

    private void initContents(InventoryFace f) {
        if (f == null) {
            return;
        }
        int x = 8;
        final int y = PH - 8 - 16;
        if (f.bothSources) {
            final Component label = f.source == InventoryFace.SOURCE_LINK
                    ? Component.translatable("create_stats.inventory_display.gui.prefer_behind")
                    : Component.translatable("create_stats.inventory_display.gui.prefer_link");
            this.preferButton = addRenderableWidget(Button.builder(label, b -> send(InventoryDisplaySettingsPacket.of(
                    this.pos, InventoryDisplaySettingsPacket.OP_PREFER_BEHIND, f.source == InventoryFace.SOURCE_LINK ? 1 : 0)))
                    .bounds(X(x), Y(y), 154, 16).build());
            x += 158;
        }
        if (f.page == InventoryFace.Page.DIESEL) {
            final Component label = f.dieselMulti
                    ? Component.translatable("create_stats.inventory_display.gui.diesel.multi")
                    : Component.translatable("create_stats.inventory_display.gui.diesel.single");
            this.dieselButton = addRenderableWidget(Button.builder(label, b -> send(InventoryDisplaySettingsPacket.of(
                    this.pos, InventoryDisplaySettingsPacket.OP_DIESEL, f.dieselMulti ? 0 : 1)))
                    .bounds(X(x), Y(y), 154, 16).build());
        }
    }

    private boolean contentsHasButtonRow(InventoryFace f) {
        return f != null && (f.bothSources || f.page == InventoryFace.Page.DIESEL);
    }

    private void initWatch(InventoryFace f) {
        int y = GRID_Y + (emptyCup(f) ? 34 : 0);
        for (WatchLine line : watchLines(f)) {
            final EditBox low = field(150, y + 10, line.low());
            final EditBox high = field(202, y + 10, line.high());
            this.fields.add(new WatchFields(line, low, high));
            y += 34;
        }
        this.relayLow = addRenderableWidget(Button.builder(Component.empty(), b -> cycleRelay(false))
                .bounds(X(8), Y(161), 154, 20).build());
        this.relayHigh = addRenderableWidget(Button.builder(Component.empty(), b -> cycleRelay(true))
                .bounds(X(166), Y(161), 154, 20).build());
        this.stallButton = addRenderableWidget(Button.builder(Component.empty(), b -> cycleStall())
                .bounds(X(196), Y(186), 124, 16).build());
        refreshLabels(f);
    }

    private EditBox field(int x, int y, long value) {
        final EditBox box = new EditBox(this.font, X(x), Y(y), 44, 14, Component.empty());
        box.setMaxLength(9);
        box.setFilter(s -> s.chars().allMatch(Character::isDigit));
        box.setValue(Long.toString(value));
        return addRenderableWidget(box);
    }

    private void initBooks() {
        this.dayButton = addRenderableWidget(Button.builder(Component.translatable("create_stats.inventory_display.gui.day"),
                b -> setDays(1)).bounds(X(8), Y(138), 36, 16).build());
        this.threeDaysButton = addRenderableWidget(Button.builder(
                Component.translatable("create_stats.inventory_display.gui.three_days"), b -> setDays(3))
                .bounds(X(46), Y(138), 44, 16).build());
        this.closeBooks = addRenderableWidget(new HoldButton(X(232), Y(186), 88, 20));
        refreshLabels(this.be.clientFace());
    }

    @Override
    public void tick() {
        super.tick();
        this.ticks++;
        if (this.be.isRemoved()) {
            onClose();
            return;
        }
        if (this.ticks % HEARTBEAT_TICKS == 0) {
            send(InventoryDisplaySettingsPacket.of(this.pos, InventoryDisplaySettingsPacket.OP_VIEW));
        }
        final InventoryFace f = this.be.clientFace();

        for (WatchFields wf : this.fields) {
            if (!wf.low().isFocused() && !wf.high().isFocused()) {
                commit(wf);
            }
        }
        final boolean typing = this.fields.stream().anyMatch(w -> w.low().isFocused() || w.high().isFocused());
        if (!typing && !signature(f).equals(this.layoutSignature)) {
            rebuildWidgets();
            return;
        }
        for (WatchFields wf : this.fields) {
            final WatchLine now = lineFor(f, wf.line().markKey());
            if (now != null) {
                if (!wf.low().isFocused()) {
                    sync(wf.low(), now.low());
                }
                if (!wf.high().isFocused()) {
                    sync(wf.high(), now.high());
                }
            }
        }
        refreshLabels(f);
        if (this.hold >= 0) {
            final long window = Minecraft.getInstance().getWindow().getWindow();
            final boolean down = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
            if (!down || this.closeBooks == null || !this.closeBooks.isHovered()) {
                this.hold = -1;
            } else if (++this.hold >= HOLD_TICKS) {
                this.hold = -1;
                this.closedFlash = 16;
                if (connected(DetectorResetPacket.TYPE)) {
                    PacketDistributor.sendToServer(new DetectorResetPacket(this.pos));
                }
            }
        }
        if (this.closedFlash > 0) {
            this.closedFlash--;
        }
    }

    private void sync(EditBox box, long value) {
        final String s = Long.toString(value);
        if (!box.getValue().equals(s)) {
            box.setValue(s);
        }
    }

    @Override
    public void removed() {
        for (WatchFields wf : this.fields) {
            commit(wf);
        }
        if (this.viewing) {
            this.viewing = false;
            send(InventoryDisplaySettingsPacket.of(this.pos, InventoryDisplaySettingsPacket.OP_UNVIEW));
        }
        super.removed();
    }

    private static boolean connected(CustomPacketPayload.Type<?> type) {
        final ClientPacketListener c = Minecraft.getInstance().getConnection();
        return c != null && c.hasChannel(type);
    }

    private static void send(InventoryDisplaySettingsPacket packet) {
        if (connected(InventoryDisplaySettingsPacket.TYPE)) {
            PacketDistributor.sendToServer(packet);
        }
    }

    private void commit(WatchFields wf) {
        final WatchLine now = lineFor(this.be.clientFace(), wf.line().markKey());
        final WatchLine base = now != null ? now : wf.line();
        final long low = parse(wf.low().getValue(), base.low());
        final long high = parse(wf.high().getValue(), base.high());
        if (low == base.low() && high == base.high()) {
            return;
        }
        this.pendingMarks.put(wf.line().markKey(), new long[]{low, high, this.ticks + PENDING_TICKS});
        send(InventoryDisplaySettingsPacket.marks(this.pos, wf.line().markKey(), low, high));
    }

    private static long parse(String s, long fallback) {
        if (s == null || s.isEmpty()) {
            return fallback;
        }
        try {
            return Math.max(0L, Long.parseLong(s));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private void cycleRelay(boolean high) {
        final InventoryFace f = this.be.clientFace();
        if (f == null) {
            return;
        }
        final SetPointRelay.Mode mode = SetPointRelay.Mode.byOrdinal(high ? highMode(f) : lowMode(f)).next();
        if (high) {
            this.localHighMode = mode.ordinal();
        } else {
            this.localLowMode = mode.ordinal();
        }
        this.localModesUntil = this.ticks + PENDING_TICKS;
        send(new InventoryDisplaySettingsPacket(this.pos, InventoryDisplaySettingsPacket.OP_RELAY, "", high ? 1 : 0,
                mode.ordinal()));
        refreshLabels(f);
    }

    private void cycleStall() {
        final InventoryFace f = this.be.clientFace();
        if (f == null) {
            return;
        }
        final int next = Math.floorMod(stallIndex(f) + 1, 4);
        this.localStall = next;
        this.localModesUntil = this.ticks + PENDING_TICKS;
        send(InventoryDisplaySettingsPacket.of(this.pos, InventoryDisplaySettingsPacket.OP_STALL, next));
        refreshLabels(f);
    }

    private void setDays(int days) {
        this.localDays = days;
        this.localDaysUntil = this.ticks + PENDING_TICKS;
        send(InventoryDisplaySettingsPacket.of(this.pos, InventoryDisplaySettingsPacket.OP_CHART_DAYS, days));
        refreshLabels(this.be.clientFace());
    }

    private int lowMode(InventoryFace f) {
        return this.localLowMode >= 0 && this.ticks < this.localModesUntil ? this.localLowMode : f.lowMode;
    }

    private int highMode(InventoryFace f) {
        return this.localHighMode >= 0 && this.ticks < this.localModesUntil ? this.localHighMode : f.highMode;
    }

    private int stallIndex(InventoryFace f) {
        return this.localStall >= 0 && this.ticks < this.localModesUntil ? this.localStall : f.stallIndex;
    }

    private int days(InventoryFace f) {
        if (this.localDays != 0 && this.ticks < this.localDaysUntil) {
            return this.localDays;
        }
        return f != null && f.chartDays == 1 ? 1 : 3;
    }

    private void refreshLabels(InventoryFace f) {
        if (f == null) {
            return;
        }
        if (this.relayLow != null) {
            this.relayLow.setMessage(switch (SetPointRelay.Mode.byOrdinal(lowMode(f))) {
                case PULSE -> Component.translatable("create_stats.inventory_display.gui.relay.left_pulse");
                case OFF -> Component.translatable("create_stats.inventory_display.gui.relay.left_off");
                default -> Component.translatable("create_stats.inventory_display.gui.relay.left_steady");
            });
        }
        if (this.relayHigh != null) {
            this.relayHigh.setMessage(switch (SetPointRelay.Mode.byOrdinal(highMode(f))) {
                case PULSE -> Component.translatable("create_stats.inventory_display.gui.relay.right_pulse");
                case OFF -> Component.translatable("create_stats.inventory_display.gui.relay.right_off");
                default -> Component.translatable("create_stats.inventory_display.gui.relay.right_steady");
            });
        }
        if (this.stallButton != null) {
            final Component when = switch (stallIndex(f)) {
                case 0 -> Component.translatable("create_stats.inventory_display.gui.stall.30s");
                case 2 -> Component.translatable("create_stats.inventory_display.gui.stall.5m");
                case 3 -> Component.translatable("create_stats.inventory_display.gui.stall.hour");
                default -> Component.translatable("create_stats.inventory_display.gui.stall.1m");
            };
            this.stallButton.setMessage(Component.translatable("create_stats.inventory_display.gui.stall_after", when));
        }
        if (this.dayButton != null) {
            this.dayButton.active = days(f) != 1;
            this.threeDaysButton.active = days(f) != 3;
        }
        if (this.closeBooks != null) {
            this.closeBooks.setMessage(this.hold >= 0
                    ? Component.translatable("create_stats.inventory_display.gui.close_books.hold")
                    : Component.translatable("create_stats.inventory_display.gui.close_books"));
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        for (int i = 0; i < 3; i++) {
            final int x = X(i * TAB_STEP);
            final int y = Y(-28);
            if (mx >= x && mx < x + 26 && my >= y && my < y + 28) {
                final Tab next = Tab.values()[i];
                if (next != this.tab) {
                    for (WatchFields wf : this.fields) {
                        commit(wf);
                    }
                    this.tab = next;
                    this.scroll = 0;
                    rebuildWidgets();
                }
                return true;
            }
        }
        if (this.tab == Tab.CONTENTS && button == 0) {
            final int[] track = scrollTrack(this.be.clientFace());
            if (track != null && inside(mx, my, PW - 20, track[0], 12, track[1])) {
                this.draggingScroller = true;
                scrollTo(my, track);
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (this.draggingScroller) {
            final int[] track = scrollTrack(this.be.clientFace());
            if (track != null) {
                scrollTo(my, track);
            }
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        this.draggingScroller = false;
        this.hold = -1;
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        if (this.tab == Tab.CONTENTS && scrollY != 0.0) {
            final int[] track = scrollTrack(this.be.clientFace());
            if (track != null) {
                this.scroll = Mth.clamp(this.scroll + (scrollY > 0 ? -1 : 1), 0, track[2]);
                return true;
            }
        }
        return super.mouseScrolled(mx, my, scrollX, scrollY);
    }

    private void scrollTo(double my, int[] track) {
        final double t = (my - Y(track[0]) - 8.0) / Math.max(1.0, track[1] - 17.0);
        this.scroll = Mth.clamp((int) Math.round(Mth.clamp(t, 0.0, 1.0) * track[2]), 0, track[2]);
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        for (WatchFields wf : this.fields) {
            if (wf.low().isFocused() || wf.high().isFocused()) {
                if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER || key == GLFW.GLFW_KEY_TAB) {
                    commit(wf);
                    setFocused(null);
                    wf.low().setFocused(false);
                    wf.high().setFocused(false);
                    return true;
                }
                if (key == GLFW.GLFW_KEY_ESCAPE) {
                    final WatchLine now = lineFor(this.be.clientFace(), wf.line().markKey());
                    final WatchLine base = now != null ? now : wf.line();
                    sync(wf.low(), base.low());
                    sync(wf.high(), base.high());
                    setFocused(null);
                    wf.low().setFocused(false);
                    wf.high().setFocused(false);
                    return true;
                }
            }
        }
        return super.keyPressed(key, scan, modifiers);
    }

    // hold for a second. the keyboard can't, a held key isn't a hand on a ledger
    private final class HoldButton extends Button {
        HoldButton(int x, int y, int w, int h) {
            super(x, y, w, h, Component.translatable("create_stats.inventory_display.gui.close_books"), b -> {
            }, DEFAULT_NARRATION);
        }

        @Override
        public void onClick(double mouseX, double mouseY) {
            ProductionDetectorScreen.this.hold = 0;
        }

        @Override
        public void onRelease(double mouseX, double mouseY) {
            ProductionDetectorScreen.this.hold = -1;
        }

        @Override
        public void onPress() {
        }
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float partialTick) {
        renderTransparentBackground(g);
        this.tip = null;
        final InventoryFace f = this.be.clientFace();
        tabs(g, false, mx, my);
        HomeKit.frame(g, this.left, this.top, PW, PH);
        tabs(g, true, mx, my);
        header(g, f, mx, my);
        final List<Line> notice = notice(f);
        if (notice != null) {
            blankPage(g, notice);
            return;
        }
        switch (this.tab) {
            case CONTENTS -> contents(g, f, mx, my);
            case WATCH -> watch(g, f, mx, my);
            case BOOKS -> books(g, f, mx, my);
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partialTick) {
        super.render(g, mx, my, partialTick);
        if (this.hold >= 0 && this.closeBooks != null) {
            fill(g, 234, 202, 234 + Math.round(84.0f * Math.min(this.hold + partialTick, HOLD_TICKS) / HOLD_TICKS), 204,
                    BAR_GREEN);
        }
        if (this.tip != null && !this.tip.isEmpty()) {
            g.renderComponentTooltip(this.font, this.tip, this.tipX, this.tipY);
        }
    }

    private void tooltip(List<Component> lines, int x, int y) {
        this.tip = lines;
        this.tipX = x;
        this.tipY = y;
    }

    private void tabs(GuiGraphics g, boolean selectedPass, int mx, int my) {
        final ItemStack[] icons = {new ItemStack(Items.OAK_PLANKS), new ItemStack(Items.IRON_INGOT), new ItemStack(Items.BOOK)};
        for (int i = 0; i < 3; i++) {
            final boolean selected = Tab.values()[i] == this.tab;
            if (selected != selectedPass) {
                continue;
            }
            final int x = X(i * TAB_STEP);
            final int y = Y(-28);
            g.blitSprite(ResourceLocation.withDefaultNamespace("container/creative_inventory/tab_top_"
                    + (selected ? "selected_" : "unselected_") + (i + 1)), x, y, 26, 32);
            g.renderItem(icons[i], x + 5, y + 9);
            if (!selected && mx >= x && mx < x + 26 && my >= y && my < y + 28) {
                tooltip(List.of(tabName(i)), mx, my);
            }
        }
    }

    private static Component tabName(int i) {
        return switch (i) {
            case 1 -> Component.translatable("create_stats.inventory_display.gui.tab.watch");
            case 2 -> Component.translatable("create_stats.inventory_display.gui.tab.books");
            default -> Component.translatable("create_stats.inventory_display.gui.tab.contents");
        };
    }

    private void header(GuiGraphics g, InventoryFace f, int mx, int my) {
        text(g, this.title.getString(), 8, 6, INK);
        annunciator(g, f, PW - 8 - 4 * LAMP_STEP + 2, 3);
        HomeKit.well(g, X(7), Y(17), 18, 18);
        if (f != null && !f.sourceIcon.isEmpty()) {
            g.renderItem(f.sourceIcon, X(8), Y(18));
        }
        final int powerLeft = power(g, mx, my);
        final int max = Math.max(40, powerLeft - 36);
        if (f == null) {
            return;
        }
        final String name = sourceName(f);
        final String shownName = HomeKit.trim(this.font, name, max);
        text(g, shownName, 30, 19, INK);
        final Line how = how(f);
        final String shownHow = HomeKit.trim(this.font, how.text(), max);
        text(g, shownHow, 30, 28, how.ink());
        if ((!shownName.equals(name) || !shownHow.equals(how.text())) && inside(mx, my, 30, 18, max, 18)) {
            tooltip(List.of(Component.literal(name), Component.literal(how.text()).withColor(TIP_GREY)), mx, my);
        }
    }

    private String sourceName(InventoryFace f) {
        String name = f.title.getString();
        if (f.sizeA > 0 && f.sizeC > 0) {
            name = Component.translatable("create_stats.inventory_display.gui.source.sized", name, f.sizeA, f.sizeB,
                    f.sizeC).getString();
        }
        if (f.source == InventoryFace.SOURCE_LINK) {
            name = Component.translatable("create_stats.inventory_display.gui.source.by_link", name).getString();
        }
        return name;
    }

    private Line how(InventoryFace f) {
        if (f.source != InventoryFace.SOURCE_LINK) {
            return new Line(Component.translatable("create_stats.inventory_display.gui.read_behind").getString(), INK);
        }
        if (f.page == InventoryFace.Page.OUT_OF_REACH) {
            if (f.otherDimension || f.linkDistance < 0) {
                return new Line(Component.translatable("create_stats.inventory_display.gui.read_link_other").getString(), RED);
            }
            return new Line(Component.translatable("create_stats.inventory_display.gui.read_link_far",
                    FaceFormat.grouped(f.linkDistance)).getString(), RED);
        }
        final String distance = FaceFormat.grouped(Math.max(0, f.linkDistance));
        if (f.antennaTier > 0) {
            return new Line(Component.translatable("create_stats.inventory_display.gui.read_link_antenna", distance,
                    antennaName(f.antennaTier), FaceFormat.grouped(f.linkReach)).getString(), INK);
        }
        return new Line(Component.translatable("create_stats.inventory_display.gui.read_link", distance).getString(), INK);
    }

    private static String antennaName(int tier) {
        return switch (tier) {
            case 1 -> Component.translatable("create_stats.inventory_display.gui.antenna.small").getString();
            case 2 -> Component.translatable("create_stats.inventory_display.gui.antenna.advanced").getString();
            default -> Component.translatable("create_stats.inventory_display.gui.antenna.trt").getString();
        };
    }

    private int power(GuiGraphics g, int mx, int my) {
        final FeConsumer fe = this.be.feConsumer();
        final boolean paid = fe.requiresPower();
        final boolean lockedOff = fe.lock() == FeLock.FORCE_OFF;
        final boolean dead = paid && !fe.isPowered();
        final boolean shed = paid && fe.isLoadShed() && !lockedOff;
        final int y = 24;
        int leftEdge;
        if (!paid || lockedOff || (dead && !shed)) {
            final String word = !paid ? Component.translatable("create_stats.inventory_display.gui.power_unlimited").getString()
                    : lockedOff ? Component.translatable("create_stats.inventory_display.gui.power_off").getString()
                    : Component.translatable("create_stats.inventory_display.gui.power_none",
                    FaceFormat.grouped(fe.getEnergyStored())).getString();
            leftEdge = PW - 8 - this.font.width(word);
            text(g, word, leftEdge, y, dead && !lockedOff && paid ? RED : INK);
        } else {
            final float pct = Mth.clamp(fe.fillPct() / 100.0f, 0.0f, 1.0f);
            final String figure = Math.round(pct * 100.0f) + "%";
            textRight(g, figure, PW - 8, y, INK);
            bar(g, PW - 96, y, 60, 7, pct, shed || fe.isBrownout() ? BAR_AMBER : BAR_GREEN, null);
            final String label = shed ? Component.translatable("create_stats.inventory_display.gui.power_shed").getString()
                    : Component.translatable("create_stats.inventory_display.gui.power").getString();
            leftEdge = PW - 100 - this.font.width(label);
            text(g, label, leftEdge, y, shed ? AMBER : INK);
        }
        if (inside(mx, my, leftEdge, y - 2, PW - 8 - leftEdge, 11)) {
            final List<Component> lines = new ArrayList<>();
            lines.add(Component.translatable("create_stats.inventory_display.gui.power"));
            if (paid) {
                lines.add(Component.translatable("create_stats.inventory_display.gui.power.tip.stored",
                        FaceFormat.grouped(fe.getEnergyStored()), FaceFormat.grouped(fe.getMaxEnergyStored())).withColor(TIP_GREY));
                lines.add(Component.translatable("create_stats.inventory_display.gui.power.tip.draw",
                        FaceFormat.grouped(fe.settings().idleDrain()), FaceFormat.grouped(fe.settings().activeDrain()))
                        .withColor(TIP_GREY));
            } else {
                lines.add(Component.translatable("create_stats.inventory_display.gui.power_unlimited").withColor(TIP_GREY));
            }
            tooltip(lines, mx, my);
        }
        return leftEdge;
    }

    private void annunciator(GuiGraphics g, InventoryFace f, int x, int y) {
        final FeConsumer fe = this.be.feConsumer();
        final boolean dead = fe.requiresPower() && !fe.isPowered();
        final boolean flashOn = (this.ticks / 5) % 2 == 0;
        final boolean lampTest = f != null && Minecraft.getInstance().level != null
                && f.lampTestUntil > Minecraft.getInstance().level.getGameTime();
        for (int i = 0; i < 4; i++) {
            final int bit = 1 << i;
            final boolean lit;
            if (dead) {
                lit = false;
            } else if (lampTest || bit == InventoryFace.LAMP_POWER) {
                lit = true;
            } else {
                lit = f != null && f.lit(bit) && (!f.flashing(bit) || flashOn);
            }
            final int col = LampPainter.FILMS[i];
            final int wx = x + i * LAMP_STEP;
            fill(g, wx - 1, y - 1, wx + LAMP_W + 1, y + LAMP_H + 1, 0xFF1A1A1A);
            final int film = lit ? col : lerp(col, 0xFF000000, 0.80);
            fill(g, wx, y, wx + LAMP_W, y + LAMP_H, film);
            fill(g, wx, y, wx + LAMP_W, y + 1, lerp(film, 0xFFFFFFFF, lit ? 0.25 : 0.10));
            final int ink = lit ? lerp(col, 0xFF000000, 0.80) : lerp(lerp(col, 0xFFFFFFFF, 0.35), 0xFF000000, 0.22);
            final String word = lampWord(i);
            g.drawString(this.font, word, X(wx) + (LAMP_W - this.font.width(word)) / 2 + 1, Y(y + 2), ink, false);
        }
    }

    private static String lampWord(int i) {
        return switch (i) {
            case 0 -> Component.translatable("create_stats.inventory_display.lamp.low").getString();
            case 1 -> Component.translatable("create_stats.inventory_display.lamp.full").getString();
            case 2 -> Component.translatable("create_stats.inventory_display.lamp.stall").getString();
            default -> Component.translatable("create_stats.inventory_display.lamp.power").getString();
        };
    }

    private List<Line> notice(InventoryFace f) {
        final FeConsumer fe = this.be.feConsumer();
        if (fe.requiresPower() && !fe.isPowered()) {
            return List.of(
                    new Line(Component.translatable("create_stats.inventory_display.gui.no_power.1").getString(), WHITE),
                    new Line(Component.translatable("create_stats.inventory_display.gui.no_power.2").getString(), LIST_SUB),
                    new Line(Component.translatable("create_stats.inventory_display.gui.no_power.3").getString(), LIST_SUB),
                    new Line(Component.translatable("create_stats.inventory_display.gui.no_power.4").getString(), LIST_SUB),
                    new Line(Component.translatable("create_stats.inventory_display.gui.no_power.5").getString(), WHITE));
        }
        if (f == null || f.page == InventoryFace.Page.BLANK) {
            return List.of();
        }
        return switch (f.page) {
            case NOTHING -> List.of(
                    new Line(Component.translatable("create_stats.inventory_display.gui.nothing.1").getString(), WHITE),
                    new Line(Component.translatable("create_stats.inventory_display.gui.nothing.2").getString(), LIST_SUB),
                    new Line(Component.translatable("create_stats.inventory_display.gui.nothing.3").getString(), LIST_SUB));
            case CANT_READ -> List.of(
                    new Line(Component.translatable("create_stats.inventory_display.gui.cant_read.1",
                            f.title.getString()).getString(), LIST_PROBLEM),
                    new Line(Component.translatable("create_stats.inventory_display.gui.cant_read.2").getString(), LIST_SUB));
            case NOT_LOADED -> {
                final List<Line> lines = new ArrayList<>();
                lines.add(new Line(Component.translatable("create_stats.inventory_display.gui.not_loaded.1").getString(), WHITE));
                lines.add(new Line(Component.translatable("create_stats.inventory_display.gui.not_loaded.2").getString(),
                        LIST_SUB));
                if (f.lastReadSeconds >= 0) {
                    lines.add(new Line(Component.translatable("create_stats.inventory_display.gui.not_loaded.3",
                            clock(f.lastReadSeconds)).getString(), LIST_SUB));
                }
                yield lines;
            }
            case OUT_OF_REACH -> reachLines(f);
            default -> null;
        };
    }

    private List<Line> reachLines(InventoryFace f) {
        final List<Line> lines = new ArrayList<>();
        final int base = Math.max(1, f.baseReach);
        if (!f.otherDimension && f.linkDistance >= 0) {
            lines.add(new Line(Component.translatable("create_stats.inventory_display.gui.reach.1",
                    FaceFormat.grouped(f.linkDistance)).getString(), LIST_PROBLEM));
            lines.add(new Line(Component.translatable("create_stats.inventory_display.gui.reach.2",
                    FaceFormat.grouped(base)).getString(), LIST_SUB));
        }
        lines.add(new Line(Component.translatable("create_stats.inventory_display.gui.reach.3").getString(), LIST_SUB));
        if (!f.otherDimension) {
            lines.add(new Line("  " + Component.translatable("create_stats.inventory_display.gui.reach.small",
                    FaceFormat.grouped(base * 4L)).getString(), WHITE));
            lines.add(new Line("  " + Component.translatable("create_stats.inventory_display.gui.reach.advanced",
                    FaceFormat.grouped(base * 12L)).getString(), WHITE));
        }
        lines.add(new Line("  " + Component.translatable("create_stats.inventory_display.gui.reach.trt",
                FaceFormat.grouped(base * 32L)).getString(), WHITE));
        return lines;
    }

    private void blankPage(GuiGraphics g, List<Line> lines) {
        for (int i = 0; i < 27; i++) {
            HomeKit.slot(g, X(8 + (i % 9) * 18), Y(GRID_Y + (i / 9) * 18));
        }
        listWell(g, 8, 104, PW - 16, PH - 112);
        int y = 112;
        for (Line line : lines) {
            text(g, line.text(), 16, y, line.ink(), true);
            y += 11;
        }
    }

    private boolean itemPage(InventoryFace f) {
        return f.page == InventoryFace.Page.CONTAINER || f.page == InventoryFace.Page.VAULT
                || f.page == InventoryFace.Page.WATCH_ONE || f.page == InventoryFace.Page.WATCH_MANY;
    }

    private boolean tilePage(InventoryFace f) {
        return f.page == InventoryFace.Page.VAULT || f.totalSlots > 54 || (f.slots.isEmpty() && !f.tiles.isEmpty());
    }

    private void contents(GuiGraphics g, InventoryFace f, int mx, int my) {
        if (f.page == InventoryFace.Page.LINK_TEXT) {
            listWell(g, 8, GRID_Y, PW - 16, PH - GRID_Y - 8);
            int y = GRID_Y + 8;
            for (String line : f.lines) {
                text(g, line, 16, y, WHITE, true);
                y += 11;
            }
            return;
        }
        final InventoryBooks books = this.be.clientBooks();
        int gridBottom;
        String hoverKey = null;
        List<Component> docked = null;
        if (itemPage(f) && !tilePage(f)) {
            final int n = Math.min(54, f.slots.size());
            int hoverIdx = -1;
            for (int i = 0; i < n; i++) {
                if (!f.slots.get(i).isEmpty() && inside(mx, my, 8 + (i % 9) * 18, GRID_Y + (i / 9) * 18, 18, 18)) {
                    hoverIdx = i;
                }
            }
            final ItemStack hovered = hoverIdx >= 0 ? f.slots.get(hoverIdx) : ItemStack.EMPTY;
            for (int i = 0; i < n; i++) {
                final int x = 8 + (i % 9) * 18;
                final int y = GRID_Y + (i / 9) * 18;
                HomeKit.slot(g, X(x), Y(y));
                final ItemStack s = f.slots.get(i);
                if (s.isEmpty()) {
                    continue;
                }
                g.renderItem(s, X(x + 1), Y(y + 1));
                g.renderItemDecorations(this.font, s, X(x + 1), Y(y + 1));
                if (!hovered.isEmpty() && ItemStack.isSameItemSameComponents(s, hovered)) {
                    final int a = i == hoverIdx ? 0x80FFFFFF : 0x38FFFFFF;
                    g.fillGradient(RenderType.guiOverlay(), X(x + 1), Y(y + 1), X(x + 17), Y(y + 17), a, a, 0);
                }
            }
            gridBottom = GRID_Y + Math.max(1, (n + 8) / 9) * 18;
            if (!hovered.isEmpty()) {
                final InventoryBooks.Row row = rowFor(books, hovered);
                hoverKey = row != null ? row.key() : null;
                int stacks = 0;
                long here = 0L;
                for (ItemStack s : f.slots) {
                    if (ItemStack.isSameItemSameComponents(s, hovered)) {
                        stacks++;
                        here += s.getCount();
                    }
                }
                docked = itemTip(hovered, row != null ? row.here() : here, stacks, row);
            }
        } else if (itemPage(f)) {
            final boolean more = f.totalTypes > 12;
            final int shown = Math.min(f.tiles.size(), more ? 11 : 12);
            for (int i = 0; i < shown; i++) {
                final int x = 8 + (i % 4) * 40;
                final int y = GRID_Y + (i / 4) * 18;
                final InventoryFace.Tile t = f.tiles.get(i);
                HomeKit.slot(g, X(x), Y(y));
                g.renderItem(t.icon(), X(x + 1), Y(y + 1));
                text(g, FaceFormat.stockCount(t.count()), x + 20, y + 5, INK);
                if (inside(mx, my, x, y, 38, 18)) {
                    final InventoryBooks.Row row = rowFor(books, t.icon());
                    hoverKey = row != null ? row.key() : null;
                    tooltip(itemTip(t.icon(), t.count(), (int) Math.max(1L, (t.count() + 63L) / 64L), row), mx, my);
                }
            }
            if (more) {
                final int x = 8 + 3 * 40;
                final int y = GRID_Y + 2 * 18;
                HomeKit.well(g, X(x), Y(y), 38, 18);
                final String s = "+" + (f.totalTypes - shown);
                g.drawString(this.font, s, X(x + 19) - this.font.width(s) / 2, Y(y + 5), WHITE, true);
            }
            gridBottom = GRID_Y + 3 * 18;
        } else {
            gridBottom = gauge(g, f);
        }
        if (docked == null) {
            summary(g, f);
        }
        table(g, f, books, gridBottom + 6, hoverKey, mx, my);
        if (docked != null) {
            tooltip(docked, X(SUMMARY_X + 4) - 12, Y(46) + 12);
        }
    }

    private int gauge(GuiGraphics g, InventoryFace f) {
        HomeKit.slot(g, X(8), Y(GRID_Y));
        ItemStack icon = f.sourceIcon;
        if ((f.page == InventoryFace.Page.TANK || f.page == InventoryFace.Page.DIESEL) && !f.fluid.isEmpty()) {
            final ItemStack bucket = new ItemStack(f.fluid.getFluid().getBucket());
            if (!bucket.isEmpty()) {
                icon = bucket;
            }
        }
        if (!icon.isEmpty()) {
            g.renderItem(icon, X(9), Y(GRID_Y + 1));
        }
        String big;
        String small;
        double frac = f.fill;
        switch (f.page) {
            case ENERGY -> {
                big = f.energyStored ? FaceFormat.fe(f.energy) : feRate(f.energyIn);
                small = f.energyStored ? Component.translatable("create_stats.inventory_display.gui.of",
                        FaceFormat.fe(f.energyCapacity)).getString() : "";

                frac = f.energyStored ? f.fill : -1.0;
            }
            case BELT -> {
                big = Component.translatable("create_stats.inventory_display.gui.on_belt", f.itemsOnBelt).getString();
                small = "";
                frac = -1.0;
            }
            default -> {
                big = FaceFormat.buckets(f.fluidAmount) + " B";
                small = Component.translatable("create_stats.inventory_display.gui.of",
                        FaceFormat.buckets(f.fluidCapacity) + " B").getString();
            }
        }
        g.drawString(this.font, Component.literal(big).withStyle(ChatFormatting.BOLD), X(30), Y(GRID_Y + 1), INK, false);
        text(g, small, 30, GRID_Y + 11, INK);
        if (frac >= 0.0) {
            bar(g, 8, GRID_Y + 24, 162, 9, frac, BAR_GREEN, null);
        }
        return GRID_Y + 36;
    }

    private String feRate(long perTick) {
        return Component.translatable("create_stats.inventory_display.gui.fe_rate", FaceFormat.grouped(perTick)).getString();
    }

    // hierarchy by weight, not grey
    private void summary(GuiGraphics g, InventoryFace f) {
        final int x0 = SUMMARY_X;
        if (f.page == InventoryFace.Page.BELT) {
            if (f.stalledSeconds >= 0) {
                bold(g, Component.translatable("create_stats.inventory_display.gui.stalled").getString(), x0, 43, RED);
                text(g, Component.translatable("create_stats.inventory_display.gui.stalled_since",
                        clock(f.stalledSeconds)).getString(), x0, 53, INK);
            } else if (f.gatherSeconds >= 0) {
                gathering(g, f, x0, 43);
            } else {
                bold(g, Component.translatable("create_stats.inventory_display.gui.passing").getString(), x0, 43, INK);
                text(g, rate(f.passPerMinute), x0, 53, GREEN);
            }
            return;
        }
        final boolean fluid = f.page == InventoryFace.Page.TANK || f.page == InventoryFace.Page.DIESEL;
        final boolean energy = f.page == InventoryFace.Page.ENERGY;

        final boolean holds = !energy || f.energyStored;
        if (holds) {
            text(g, Component.translatable("create_stats.inventory_display.gui.fill").getString(), x0, 43, INK);
            bar(g, x0 + 24, 42, 90, 9, f.fill, BAR_GREEN, null);
            textRight(g, FaceFormat.percent(f.fill), PW - 8, 43, INK);
        }
        if (f.gatherSeconds >= 0 && holds) {
            gathering(g, f, x0, 58);
            return;
        }
        final int y = holds ? 57 : 43;
        text(g, Component.translatable("create_stats.inventory_display.gui.in").getString(), x0, y, INK);
        text(g, energy ? feRate(f.energyIn) : fluid ? bucketRate(f.inPerMinute) : rate(f.inPerMinute), x0 + 24, y, GREEN);
        text(g, Component.translatable("create_stats.inventory_display.gui.out").getString(), x0, y + 11, INK);
        text(g, energy ? feRate(f.energyOut) : fluid ? bucketRate(f.outPerMinute) : rate(f.outPerMinute), x0 + 24, y + 11,
                AMBER);
        if (!holds || (energy && f.energyClock == EnergyReadout.Clock.NONE.ordinal())) {
            return;
        }
        if (f.clock == FlowClock.Kind.STEADY || f.clock == FlowClock.Kind.NONE) {
            bold(g, Component.translatable("create_stats.inventory_display.gui.steady").getString(), x0, 81, INK);
            text(g, Component.translatable("create_stats.inventory_display.gui.nothing_moving").getString(), x0, 91, INK);
        } else {
            bold(g, clockLine(f.clock, f.clockSeconds), x0, 81, INK);

            text(g, Component.translatable(energy && f.energyClock == EnergyReadout.Clock.QUOTED.ordinal()
                    ? "create_stats.inventory_display.gui.as_forecast"
                    : "create_stats.inventory_display.gui.at_this_rate").getString(), x0, 91, INK);
        }
    }

    private void gathering(GuiGraphics g, InventoryFace f, int x0, int y) {
        bold(g, Component.translatable("create_stats.inventory_display.gui.gathering").getString(), x0, y, INK);
        text(g, Component.translatable("create_stats.inventory_display.gui.rates_in", clock(f.gatherSeconds)).getString(),
                x0, y + 11, INK);
    }

    private void table(GuiGraphics g, InventoryFace f, InventoryBooks books, int ty, String hoverKey, int mx, int my) {
        final int th = PH - ty - 8 - (contentsHasButtonRow(f) ? 20 : 0);
        if (th < 32) {
            return;
        }
        listWell(g, 8, ty, PW - 30, th);
        text(g, Component.translatable("create_stats.inventory_display.gui.col.item").getString(), 30, ty + 3, WHITE, true);
        textRight(g, Component.translatable("create_stats.inventory_display.gui.col.here").getString(), 196, ty + 3, WHITE, true);
        textRight(g, Component.translatable("create_stats.inventory_display.gui.col.in").getString(), 238, ty + 3, WHITE, true);
        textRight(g, Component.translatable("create_stats.inventory_display.gui.col.out").getString(), 272, ty + 3, WHITE, true);
        textRight(g, Component.translatable("create_stats.inventory_display.gui.col.net").getString(), PW - 26, ty + 3, WHITE,
                true);
        fill(g, 10, ty + 12, PW - 24, ty + 13, LIST_RULE);
        final List<InventoryBooks.Row> rows = books != null ? books.rows : List.of();
        final int visible = Math.max(0, (th - 14) / ROW_H);
        final int maxScroll = Math.max(0, rows.size() - visible);
        this.scroll = Mth.clamp(this.scroll, 0, maxScroll);
        for (int n = 0; n < visible && this.scroll + n < rows.size(); n++) {
            final InventoryBooks.Row r = rows.get(this.scroll + n);
            final int yy = ty + 14 + n * ROW_H;
            final boolean rowHot = inside(mx, my, 9, yy - 1, PW - 32, ROW_H);
            if (rowHot || r.key().equals(hoverKey)) {
                fill(g, 9, yy - 1, PW - 23, yy + ROW_H - 1, 0x30FFFFFF);
            }
            if (!r.icon().isEmpty()) {
                g.renderItem(r.icon(), X(10), Y(yy));
            }
            final String name = rowName(r, f);
            final String shown = HomeKit.trim(this.font, name, 112);
            text(g, shown, 30, yy + 4, WHITE, true);
            textRight(g, amount(r.key(), r.here(), f), 196, yy + 4, WHITE, true);
            final long in = Math.round(r.inPerMinute());
            final long out = Math.round(r.outPerMinute());
            final long net = in - out;
            textRight(g, in != 0 ? flow(r.key(), in, f) : "-", 238, yy + 4, in != 0 ? LIST_RISE : LIST_DIM, true);
            textRight(g, out != 0 ? flow(r.key(), out, f) : "-", 272, yy + 4, out != 0 ? LIST_FALL : LIST_DIM, true);
            textRight(g, net > 0 ? "+" + flow(r.key(), net, f) : net < 0 ? "-" + flow(r.key(), -net, f) : "0", PW - 26, yy + 4,
                    net > 0 ? LIST_RISE : net < 0 ? LIST_FALL : LIST_DIM, true);
            if (rowHot && !shown.equals(name)) {
                tooltip(List.of(Component.literal(name)), mx, my);
            }
        }
        HomeKit.well(g, X(PW - 20), Y(ty), 12, th);
        final int sy = ty + 1 + (maxScroll == 0 ? 0 : Math.round((th - 2 - 15) * (this.scroll / (float) maxScroll)));
        g.blitSprite(maxScroll > 0 ? SCROLLER : SCROLLER_OFF, X(PW - 20), Y(sy), 12, 15);
    }

    private int[] scrollTrack(InventoryFace f) {
        if (f == null || notice(f) != null || f.page == InventoryFace.Page.LINK_TEXT) {
            return null;
        }
        final int gridBottom;
        if (itemPage(f) && !tilePage(f)) {
            gridBottom = GRID_Y + Math.max(1, (Math.min(54, f.slots.size()) + 8) / 9) * 18;
        } else if (itemPage(f)) {
            gridBottom = GRID_Y + 3 * 18;
        } else {
            gridBottom = GRID_Y + 36;
        }
        final int ty = gridBottom + 6;
        final int th = PH - ty - 8 - (contentsHasButtonRow(f) ? 20 : 0);
        if (th < 32) {
            return null;
        }
        final InventoryBooks books = this.be.clientBooks();
        final int rows = books != null ? books.rows.size() : 0;
        return new int[]{ty, th, Math.max(0, rows - Math.max(0, (th - 14) / ROW_H))};
    }

    private String rowName(InventoryBooks.Row r, InventoryFace f) {
        if (!r.icon().isEmpty()) {
            return r.icon().getHoverName().getString();
        }
        if (("fluid".equals(r.key()) || "fuel".equals(r.key())) && !f.fluid.isEmpty()) {
            return f.fluid.getHoverName().getString();
        }
        return f.title.getString();
    }

    private String amount(String key, long value, InventoryFace f) {
        if ("fluid".equals(key) || "fuel".equals(key)) {
            return FaceFormat.buckets(value) + " B";
        }
        if ("fe".equals(key)) {
            return FaceFormat.fe(value);
        }
        return FaceFormat.grouped(value);
    }

    private String flow(String key, long perMinute, InventoryFace f) {
        if ("fluid".equals(key) || "fuel".equals(key)) {
            return FaceFormat.buckets(perMinute);
        }
        if ("fe".equals(key)) {
            return FaceFormat.compact(perMinute);
        }
        return FaceFormat.grouped(perMinute);
    }

    private static InventoryBooks.Row rowFor(InventoryBooks books, ItemStack stack) {
        if (books == null || stack.isEmpty()) {
            return null;
        }
        for (InventoryBooks.Row r : books.rows) {
            if (!r.icon().isEmpty() && ItemStack.isSameItemSameComponents(r.icon(), stack)) {
                return r;
            }
        }
        return null;
    }

    private List<Component> itemTip(ItemStack stack, long here, int stacks, InventoryBooks.Row row) {
        final List<Component> lines = new ArrayList<>();
        lines.add(stack.getHoverName());
        lines.add((stacks == 1
                ? Component.translatable("create_stats.inventory_display.gui.tip.here_one", FaceFormat.grouped(here))
                : Component.translatable("create_stats.inventory_display.gui.tip.here", FaceFormat.grouped(here),
                Integer.toString(stacks))).withColor(TIP_GREY));
        final long in = row != null ? Math.round(row.inPerMinute()) : 0L;
        final long out = row != null ? Math.round(row.outPerMinute()) : 0L;
        lines.add(Component.translatable("create_stats.inventory_display.gui.tip.flow", FaceFormat.grouped(in),
                FaceFormat.grouped(out)).withColor(TIP_GREY));
        final long net = in - out;
        if (net > 0) {
            lines.add(Component.translatable("create_stats.inventory_display.gui.tip.rising", FaceFormat.grouped(net))
                    .withColor(TIP_RISE));
        } else if (net < 0) {
            lines.add(Component.translatable("create_stats.inventory_display.gui.tip.falling", "-" + FaceFormat.grouped(-net))
                    .withColor(TIP_FALL));
        } else {
            lines.add(Component.translatable("create_stats.inventory_display.gui.tip.still").withColor(TIP_GREY));
        }
        return lines;
    }

    private boolean emptyCup(InventoryFace f) {
        return f != null && f.rows.isEmpty() && (f.page == InventoryFace.Page.CONTAINER || f.page == InventoryFace.Page.VAULT);
    }

    private List<WatchLine> watchLines(InventoryFace f) {
        final List<WatchLine> out = new ArrayList<>();
        if (f == null) {
            return out;
        }
        for (InventoryFace.WatchRow r : f.rows) {
            final long[] p = pending(r.markKey());
            final long low = p != null ? p[0] : r.low();
            final long high = p != null ? p[1] : r.high();
            final double top = Math.max(Math.max(high * 1.25, r.count() * 1.05), 1.0);
            final boolean below = r.count() <= low;
            final boolean above = r.count() >= high;
            final String state = below ? Component.translatable("create_stats.inventory_display.gui.below_low").getString()
                    : above ? Component.translatable("create_stats.inventory_display.gui.above_high").getString()
                    : f.gatherSeconds >= 0 ? plain(Component.translatable("create_stats.inventory_display.gui.gathering").getString())
                    : clockState(r.clock(), r.clockSeconds());
            out.add(new WatchLine(r.markKey(), r.icon(), r.name().getString(), FaceFormat.grouped(r.count()), low, high,
                    unit(MarkLadder.ITEMS), r.count() / top, low / top, high / top, state, below || above ? AMBER : GREEN,
                    below ? 0xFFFFB040 : above ? 0xFFE6ECF2 : PIP_OK, below || above ? BAR_AMBER : BAR_GREEN));
        }
        if (out.isEmpty() && !f.quantityKey.isEmpty()) {
            final MarkLadder unit = MarkLadder.values()[Math.max(0, Math.min(MarkLadder.values().length - 1, f.markUnit))];
            final long[] p = pending(f.quantityKey);
            final long low = p != null ? p[0] : f.quantityLow;
            final long high = p != null ? p[1] : f.quantityHigh;
            final double value = f.quantityValue;
            final boolean percent = unit == MarkLadder.PERCENT || unit == MarkLadder.ENERGY_PERCENT;
            final double top = percent ? 100.0 : Math.max(Math.max(high * 1.25, value * 1.05), 1.0);
            final boolean below = value <= low;
            final boolean above = value >= high;
            final String count = percent ? Math.round(value) + unit(unit) : FaceFormat.rateFigure(value) + " " + unit(unit);
            final String name = emptyCup(f) ? Component.translatable("create_stats.inventory_display.gui.whole_container").getString()
                    : f.title.getString();

            final boolean noClock = f.page == InventoryFace.Page.ENERGY && f.energyClock == EnergyReadout.Clock.NONE.ordinal();
            final String state = below ? Component.translatable("create_stats.inventory_display.gui.below_low").getString()
                    : above ? Component.translatable("create_stats.inventory_display.gui.above_high").getString()
                    : noClock ? ""
                    : f.gatherSeconds >= 0 ? plain(Component.translatable("create_stats.inventory_display.gui.gathering").getString())
                    : clockState(f.clock, f.clockSeconds);
            out.add(new WatchLine(f.quantityKey, ItemStack.EMPTY, name, count, low, high, unit(unit), value / top, low / top,
                    high / top, state, below || above ? AMBER : GREEN, below ? 0xFFFFB040 : above ? 0xFFE6ECF2 : PIP_OK,
                    below || above ? BAR_AMBER : BAR_GREEN));
        }
        return out;
    }

    private WatchLine lineFor(InventoryFace f, String key) {
        for (WatchLine l : watchLines(f)) {
            if (l.markKey().equals(key)) {
                return l;
            }
        }
        return null;
    }

    private long[] pending(String key) {
        final long[] p = this.pendingMarks.get(key);
        if (p == null) {
            return null;
        }
        if (this.ticks > p[2]) {
            this.pendingMarks.remove(key);
            return null;
        }
        return p;
    }

    private static String unit(MarkLadder unit) {
        return switch (unit) {
            case ITEMS -> Component.translatable("create_stats.inventory_display.value.items").getString();
            case PER_MINUTE -> Component.translatable("create_stats.inventory_display.value.per_min").getString();
            default -> Component.translatable("create_stats.inventory_display.value.percent").getString();
        };
    }

    private static String plain(String s) {
        return s.endsWith(":") ? s.substring(0, s.length() - 1) : s;
    }

    private String clockState(FlowClock.Kind kind, double seconds) {
        if (kind == FlowClock.Kind.STEADY || kind == FlowClock.Kind.NONE) {
            return plain(Component.translatable("create_stats.inventory_display.gui.steady").getString());
        }
        return clockLine(kind, seconds);
    }

    private void watch(GuiGraphics g, InventoryFace f, int mx, int my) {
        int y = GRID_Y;
        if (emptyCup(f)) {
            listWell(g, 8, y, PW - 16, 30);
            text(g, Component.translatable("create_stats.inventory_display.gui.empty_cup.1").getString(), 14, y + 5, WHITE, true);
            text(g, Component.translatable("create_stats.inventory_display.gui.empty_cup.2").getString(), 14, y + 16, LIST_SUB,
                    true);
            y += 34;
        }
        for (WatchFields wf : this.fields) {
            final WatchLine now = lineFor(f, wf.line().markKey());
            final WatchLine l = now != null ? now : wf.line();
            HomeKit.slot(g, X(8), Y(y));
            if (!l.icon().isEmpty()) {
                g.renderItem(l.icon(), X(9), Y(y + 1));
            }
            text(g, HomeKit.trim(this.font, l.name(), 116), 30, y + 1, INK);
            bold(g, l.count(), 30, y + 11, INK);
            text(g, Component.translatable("create_stats.inventory_display.gui.low").getString(), 150, y + 1, INK);
            text(g, Component.translatable("create_stats.inventory_display.gui.high").getString(), 202, y + 1, INK);
            text(g, l.unit(), 250, y + 14, INK);
            pip(g, PW - 14, y + 2, l.pip());
            textRight(g, HomeKit.trim(this.font, l.state(), 74), PW - 22, y + 1, l.stateInk());
            bar(g, 30, y + 27, PW - 38, 4, l.frac(), l.barInk(), new double[]{l.lowFrac(), l.highFrac()});
            y += 34;
        }
        text(g, Component.translatable("create_stats.inventory_display.gui.redstone").getString(), 8, 150, INK);
        final int cy = 188;
        text(g, Component.translatable("create_stats.inventory_display.gui.comparator").getString(), 8, cy + 2, INK);
        final int comparator = f != null ? f.comparator : 0;
        for (int i = 0; i < 15; i++) {
            fill(g, 68 + i * 7, cy + 1, 73 + i * 7, cy + 9, 0xFF3B2410);
            fill(g, 69 + i * 7, cy + 2, 72 + i * 7, cy + 8, i < comparator ? 0xFFF2C04E : 0xFF6A4220);
        }
        text(g, Integer.toString(comparator), 68 + 15 * 7 + 3, cy + 2, INK);
        if (inside(mx, my, 68, cy, 15 * 7, 10)) {
            final List<Component> lines = new ArrayList<>();
            lines.add(Component.translatable("create_stats.inventory_display.gui.comparator").append(" " + comparator));
            for (FormattedText part : this.font.getSplitter().splitLines(
                    Component.translatable("create_stats.inventory_display.gui.comparator.tip"), 180,
                    net.minecraft.network.chat.Style.EMPTY)) {
                lines.add(Component.literal(part.getString()).withColor(TIP_GREY));
            }
            tooltip(lines, mx, my);
        }
    }

    private void books(GuiGraphics g, InventoryFace f, int mx, int my) {
        final int cx = 8;
        final int cy = GRID_Y;
        final int cw = PW - 16;
        final int ch = 92;
        HomeKit.well(g, X(cx), Y(cy), cw, ch);
        final ChartHistory chart = this.be.clientChart();
        final int days = days(f);
        final int scale = f != null ? f.chartScale : 1;
        final ResourceLocation paper = ProductionDetectorTextureManager.screenPaper(chart, cw - 2, ch - 2, days, scale,
                f != null ? f.chartLow : Float.NaN, f != null ? f.chartHigh : Float.NaN);
        g.blit(paper, X(cx + 1), Y(cy + 1), 0, 0, cw - 2, ch - 2, cw - 2, ch - 2);
        if (inside(mx, my, cx + 1, cy + 1, cw - 2, ch - 2) && chart.size() > 0) {
            final int col = mx - X(cx + 1);
            final ChartPaperLayout layout = ChartPaperLayout.of(chart, cw - 2, days);
            if (col >= 0 && col < layout.columns().size()) {
                final ChartPaperLayout.Column c = layout.columns().get(col);
                if (c.sample() >= 0) {
                    fill(g, col + cx + 1, cy + 1, col + cx + 2, cy + ch - 1, 0x80FFFFFF);
                    tooltip(chartReading(chart.reading(c.sample()), f), mx, my);
                }
            }
        }
        final Component pen = f != null && !f.penName.getString().isEmpty()
                ? Component.translatable("create_stats.inventory_display.gui.pen", f.penName.getString())
                : Component.translatable("create_stats.inventory_display.gui.pen_container");
        text(g, HomeKit.trim(this.font, pen.getString(), PW - 104), 96, 142, INK);
        binCard(g, f);
    }

    private List<Component> chartReading(ChartHistory.Reading r, InventoryFace f) {
        final List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("create_stats.inventory_display.gui.chart.tip.when", Integer.toString(r.day()),
                String.format(java.util.Locale.ROOT, "%02d:%02d", r.hour(), r.minute())));
        if (r.clockJumped()) {
            lines.add(Component.translatable("create_stats.inventory_display.gui.chart.tip.skip").withColor(TIP_GREY));
        }
        if (r.gap()) {
            lines.add(Component.translatable("create_stats.inventory_display.gui.chart.tip.gap").withColor(TIP_GREY));
            return lines;
        }
        final String name = f != null && !f.penName.getString().isEmpty() ? f.penName.getString()
                : Component.translatable("create_stats.inventory_display.gui.whole_container").getString();
        lines.add(Component.translatable("create_stats.inventory_display.gui.chart.tip.value", name,
                FaceFormat.grouped(r.value())).withColor(TIP_GREY));
        if (r.hasNet()) {
            if (r.netPerMinute() > 0) {
                lines.add(Component.translatable("create_stats.inventory_display.gui.tip.rising",
                        FaceFormat.grouped(r.netPerMinute())).withColor(TIP_RISE));
            } else if (r.netPerMinute() < 0) {
                lines.add(Component.translatable("create_stats.inventory_display.gui.tip.falling",
                        "-" + FaceFormat.grouped(-r.netPerMinute())).withColor(TIP_FALL));
            } else {
                lines.add(Component.translatable("create_stats.inventory_display.gui.tip.still").withColor(TIP_GREY));
            }
        }
        return lines;
    }

    private void binCard(GuiGraphics g, InventoryFace f) {
        final int bx = 8;
        final int by = 158;
        final int bw = 218;
        final int bh = 50;
        fill(g, bx, by, bx + bw, by + bh, 0xFFF3EFE2);
        fill(g, bx, by, bx + bw, by + 1, 0xFFD9D2BC);
        fill(g, bx, by + bh - 1, bx + bw, by + bh, 0xFFB8B09A);
        fill(g, bx + bw - 1, by, bx + bw, by + bh, 0xFFB8B09A);
        for (int ry = by + 21; ry < by + bh - 1; ry += 10) {
            fill(g, bx + 2, ry, bx + bw - 2, ry + 1, 0xFFB9CBE6);
        }
        fill(g, bx + 2, by + 11, bx + bw - 2, by + 12, 0xFFD2524A);
        text(g, Component.translatable("create_stats.inventory_display.gui.card.day").getString(), bx + 4, by + 2, CARD_HEAD);
        textRight(g, Component.translatable("create_stats.inventory_display.gui.card.received").getString(), bx + 112, by + 2,
                CARD_HEAD);
        textRight(g, Component.translatable("create_stats.inventory_display.gui.card.issued").getString(), bx + 160, by + 2,
                CARD_HEAD);
        textRight(g, Component.translatable("create_stats.inventory_display.gui.card.balance").getString(), bx + 214, by + 2,
                CARD_HEAD);
        final InventoryBooks books = this.be.clientBooks();
        ItemStack penIcon = ItemStack.EMPTY;
        BinCardLedger.Row card = null;
        String key = "";
        if (books != null) {
            card = books.total;
            if (f != null && !f.rows.isEmpty()) {
                final InventoryBooks.Row row = books.row(f.rows.get(0).markKey());
                if (row != null) {
                    card = row.card();
                    key = row.key();
                }
                penIcon = f.rows.get(0).icon();
            }
        }
        if (books != null && card != null) {
            final boolean fluid = f != null && (f.page == InventoryFace.Page.TANK || f.page == InventoryFace.Page.DIESEL);
            final String unitKey = !key.isEmpty() ? key : fluid ? "fluid" : f != null && f.page == InventoryFace.Page.ENERGY ? "fe" : "";
            final String[][] rows = {
                    {Component.translatable("create_stats.inventory_display.gui.card.today", Integer.toString(books.today)).getString(),
                            amount(unitKey, card.todayIn(), f), amount(unitKey, card.todayOut(), f), amount(unitKey, card.balance(), f)},
                    {Component.translatable("create_stats.inventory_display.gui.card.yesterday", Integer.toString(books.today - 1))
                            .getString(), card.hasYesterday() ? amount(unitKey, card.yesterdayIn(), f) : "-",
                            card.hasYesterday() ? amount(unitKey, card.yesterdayOut(), f) : "-",
                            card.hasYesterday() ? amount(unitKey, card.yesterdayClose(), f) : "-"},
                    {Component.translatable("create_stats.inventory_display.gui.card.since", Integer.toString(books.openedDay))
                            .getString(), amount(unitKey, card.received(), f), amount(unitKey, card.issued(), f),
                            amount(unitKey, card.balance(), f)}};
            final int flash = this.closedFlash > 0 && (this.closedFlash / 2) % 2 == 0 ? GREEN : CARD_INK;
            for (int i = 0; i < 3; i++) {
                final int yy = by + 13 + i * 10;
                text(g, rows[i][0], bx + 4, yy, CARD_INK);
                textRight(g, rows[i][1], bx + 112, yy, i == 2 ? flash : CARD_INK);
                textRight(g, rows[i][2], bx + 160, yy, i == 2 ? flash : CARD_INK);
                textRight(g, rows[i][3], bx + 214, yy, CARD_INK);
            }
        }
        HomeKit.slot(g, X(232), Y(158));
        if (!penIcon.isEmpty()) {
            g.renderItem(penIcon, X(233), Y(159));
        }
        text(g, Component.translatable("create_stats.inventory_display.gui.opened").getString(), 254, 159, INK);
        if (books != null) {
            text(g, Component.translatable("create_stats.inventory_display.gui.opened_day",
                    Integer.toString(books.openedDay)).getString(), 254, 168, INK);
        }
        if (this.closeBooks != null && this.closeBooks.isHovered() && this.hold < 0) {
            final List<Component> lines = new ArrayList<>();
            lines.add(Component.translatable("create_stats.inventory_display.gui.close_books"));
            for (FormattedText part : this.font.getSplitter().splitLines(
                    Component.translatable("create_stats.inventory_display.gui.close_books.tip"), 200,
                    net.minecraft.network.chat.Style.EMPTY)) {
                lines.add(Component.literal(part.getString()).withColor(TIP_GREY));
            }
            tooltip(lines, this.closeBooks.getX() + 4, this.closeBooks.getY() + 4);
        }
    }

    private static String clock(double seconds) {
        return FaceFormat.clock(seconds,
                Component.translatable("create_stats.inventory_display.gui.clock.hm").getString(),
                Component.translatable("create_stats.inventory_display.gui.clock.days").getString());
    }

    private static String rate(double perMinute) {
        return Component.translatable("create_stats.inventory_display.gui.rate", FaceFormat.rateFigure(perMinute)).getString();
    }

    private static String bucketRate(double millibucketsPerMinute) {
        return Component.translatable("create_stats.inventory_display.gui.rate",
                FaceFormat.buckets(Math.round(millibucketsPerMinute)) + " B").getString();
    }

    private static String clockLine(FlowClock.Kind kind, double seconds) {
        final String c = clock(seconds);
        return switch (kind) {
            case FULL -> Component.translatable("create_stats.inventory_display.gui.full_in", c).getString();
            case EMPTY -> Component.translatable("create_stats.inventory_display.gui.empty_in", c).getString();
            case LOW -> Component.translatable("create_stats.inventory_display.gui.low_in", c).getString();
            case HIGH -> Component.translatable("create_stats.inventory_display.gui.high_in", c).getString();
            case RUNTIME -> Component.translatable("create_stats.inventory_display.gui.runtime", c).getString();
            default -> plain(Component.translatable("create_stats.inventory_display.gui.steady").getString());
        };
    }

    private int X(int x) {
        return this.left + x;
    }

    private int Y(int y) {
        return this.top + y;
    }

    private boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= X(x) && mx < X(x + w) && my >= Y(y) && my < Y(y + h);
    }

    private void fill(GuiGraphics g, int x0, int y0, int x1, int y1, int c) {
        g.fill(X(x0), Y(y0), X(x1), Y(y1), c);
    }

    private void text(GuiGraphics g, String s, int x, int y, int c) {
        g.drawString(this.font, s, X(x), Y(y), c, false);
    }

    private void text(GuiGraphics g, String s, int x, int y, int c, boolean shadow) {
        g.drawString(this.font, s, X(x), Y(y), c, shadow);
    }

    private void bold(GuiGraphics g, String s, int x, int y, int c) {
        g.drawString(this.font, Component.literal(s).withStyle(ChatFormatting.BOLD), X(x), Y(y), c, false);
    }

    private void textRight(GuiGraphics g, String s, int rx, int y, int c) {
        g.drawString(this.font, s, X(rx) - this.font.width(s), Y(y), c, false);
    }

    private void textRight(GuiGraphics g, String s, int rx, int y, int c, boolean shadow) {
        g.drawString(this.font, s, X(rx) - this.font.width(s), Y(y), c, shadow);
    }

    private void listWell(GuiGraphics g, int x, int y, int w, int h) {
        HomeKit.well(g, X(x), Y(y), w, h);
        g.fill(X(x + 1), Y(y + 1), X(x + w - 1), Y(y + h - 1), LIST_BG);
    }

    private void bar(GuiGraphics g, int x, int y, int w, int h, double frac, int colour, double[] marks) {
        HomeKit.well(g, X(x), Y(y), w, h);
        final int fw = (int) Math.round((w - 2) * Math.max(0.0, Math.min(1.0, frac)));
        if (fw > 0) {
            fill(g, x + 1, y + 1, x + 1 + fw, y + h - 1, colour);
            fill(g, x + 1, y + 1, x + 1 + fw, y + 2, lerp(colour, 0xFFFFFFFF, 0.35));
        }
        if (marks != null) {
            for (double m : marks) {
                final int mx = x + 1 + (int) Math.round((w - 2) * Math.max(0.0, Math.min(1.0, m)));
                fill(g, mx, y - 2, mx + 1, y + h + 2, AMBER);
            }
        }
    }

    private void pip(GuiGraphics g, int x, int y, int colour) {
        fill(g, x + 1, y, x + 4, y + 5, colour);
        fill(g, x, y + 1, x + 5, y + 4, colour);
        fill(g, x + 1, y + 1, x + 2, y + 2, lerp(colour, 0xFFFFFFFF, 0.6));
        fill(g, x + 1, y + 4, x + 4, y + 5, lerp(colour, 0xFF000000, 0.35));
    }

    private static int lerp(int a, int b, double t) {
        final int ar = (a >> 16) & 255;
        final int ag = (a >> 8) & 255;
        final int ab = a & 255;
        final int r = (int) Math.round(ar + (((b >> 16) & 255) - ar) * t);
        final int gg = (int) Math.round(ag + (((b >> 8) & 255) - ag) * t);
        final int bb = (int) Math.round(ab + ((b & 255) - ab) * t);
        return 0xFF000000 | (r << 16) | (gg << 8) | bb;
    }
}
