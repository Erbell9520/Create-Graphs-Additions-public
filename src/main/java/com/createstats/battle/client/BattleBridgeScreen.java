package com.createstats.battle.client;

import com.createstats.battle.console.BridgeConsole;
import com.createstats.battle.console.ConsoleHost;
import com.createstats.battle.console.DetailView;
import com.createstats.battle.console.ShapeView;
import com.createstats.battle.core.BattleReport;
import com.createstats.battle.net.BattleBridgeActionPacket;
import com.createstats.blockentity.BattleScreenDisplayBE;
import com.createstats.client.LocalGuiDensity;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

public class BattleBridgeScreen extends Screen implements LocalGuiDensity {
    private static BridgeConsole.Page lastPage = BridgeConsole.Page.BOARD;

    private final BattleScreenDisplayBE be;
    private final BlockPos pos;
    private final BridgeConsole console;
    private final ConsoleRecorder recorder = new ConsoleRecorder();
    private int shapeAsked = Integer.MIN_VALUE;
    private int detailAsked = Integer.MIN_VALUE;
    private long detailAskedAt = Long.MIN_VALUE / 2;
    private long watchSentAt;

    public BattleBridgeScreen(BattleScreenDisplayBE be) {
        super(Component.translatable("battle.create_stats.title"));
        this.be = be;
        this.pos = be.getBlockPos().immutable();
        BattleReport r = be.report();
        this.console = new BridgeConsole(new Host(), r, BattleClientCache.shape(r.key()), BattleClientCache.detail(r.key()),
                lastPage, clock());
        this.console.jobs(BattleClientCache.jobs(r.key()));
    }

    private static long clock() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null ? 0L : mc.level.getGameTime();
    }

    @Override
    protected void init() {
        super.init();
        send(BattleBridgeActionPacket.WATCH, 1, 0);
        watchSentAt = clock();
        refresh();
    }

    @Override
    public int maxEffectiveGuiScale() {
        Window window = Minecraft.getInstance().getWindow();
        int own = Math.max(1, (int) Math.round(window.getGuiScale()));
        for (int scale = own; scale > 1; scale--) {
            if (window.getWidth() / scale >= BridgeConsole.MIN_W + 2 * BridgeConsole.MARGIN
                    && window.getHeight() / scale >= BridgeConsole.MIN_H + 2 * BridgeConsole.MARGIN) {
                return scale;
            }
        }
        return 1;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        if (be.isRemoved()) {
            onClose();
            return;
        }

        if (clock() - watchSentAt >= 100) {
            send(BattleBridgeActionPacket.WATCH, 1, 0);
            watchSentAt = clock();
        }
        refresh();
    }

    private void refresh() {
        BattleReport r = be.report();
        long now = clock();
        ShapeView shape = BattleClientCache.shape(r.key());
        DetailView detail = BattleClientCache.detail(r.key());
        if (r.hasReference() && (shape == null || shape.version() != r.shapeVersion()) && shapeAsked != r.shapeVersion()) {
            shapeAsked = r.shapeVersion();
            send(BattleBridgeActionPacket.REQUEST_SHAPE, shape == null ? -1 : shape.version(), 0);
        }
        boolean detailStale = detail == null || detail.lostVersion() != r.lostVersion();
        if (r.hasReference() && detailStale && (detailAsked != r.lostVersion() || now - detailAskedAt > 40)) {
            detailAsked = r.lostVersion();
            detailAskedAt = now;
            send(BattleBridgeActionPacket.REQUEST_DETAIL, detail == null ? -1 : detail.lostVersion(), 0);
        }
        console.accept(r, shape, detail, now);
        console.watch(BattleClientCache.watch(r.key()));
    }

    @Override
    public void onClose() {
        send(BattleBridgeActionPacket.WATCH, 0, 0);
        lastPage = console.page();
        super.onClose();
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partialTick) {
        super.render(g, mx, my, partialTick);
        console.accept(be.report(), BattleClientCache.shape(be.report().key()), BattleClientCache.detail(be.report().key()), clock());
        console.watch(BattleClientCache.watch(be.report().key()));
        recorder.begin();
        console.render(recorder, width, height, mx, my);
        recorder.replay(g, font);
        List<String> tip = console.tooltip();
        if (!tip.isEmpty()) {
            List<Component> lines = new ArrayList<>(tip.size());
            for (String line : tip) lines.add(Component.literal(line));
            g.renderComponentTooltip(font, lines, mx, my);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (console.mouseClicked(mx, my, button)) return true;
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        return console.mouseScrolled(mx, my, scrollY) || super.mouseScrolled(mx, my, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (console.editing() && console.keyPressed(keyCode)) return true;
        if (minecraft != null && minecraft.options.keyInventory.matches(keyCode, scanCode)) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char ch, int modifiers) {
        if (console.charTyped(ch)) return true;
        return super.charTyped(ch, modifiers);
    }

    private void send(int action, int a, int b) {
        PacketDistributor.sendToServer(BattleBridgeActionPacket.of(pos, action, a, b));
    }

    private final class Host implements ConsoleHost {
        @Override
        public int width(String text) {
            return font.width(text);
        }

        @Override
        public String tr(String key, Object... args) {
            return I18n.get(key, args);
        }

        @Override
        public String name(String idOrName) {
            if (idOrName == null || idOrName.isEmpty()) return "";
            return I18n.exists(idOrName) ? I18n.get(idOrName) : idOrName;
        }

        @Override
        public void send(int action, int a, int b, String s) {
            PacketDistributor.sendToServer(new BattleBridgeActionPacket(pos, action, a, b, s == null ? "" : s));
        }

        @Override
        public void click() {
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
        }
    }
}
