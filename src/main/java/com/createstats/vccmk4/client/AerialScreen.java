package com.createstats.vccmk4.client;

import com.createstats.client.LocalGuiDensity;
import com.createstats.vccmk4.console.AerialConsole;
import com.createstats.vccmk4.console.ConsoleHost;
import com.createstats.vccmk4.network.AerialActionPacket;
import com.createstats.vccmk4.network.AerialView;
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

public class AerialScreen extends Screen implements LocalGuiDensity, HelmScreen {
    private static final int KEEPALIVE_TICKS = 40;
    private static AerialConsole.Page lastPage = AerialConsole.Page.FLIGHT;

    private final BlockPos pos;
    private final AerialConsole console;
    private final Vcc4Screen.Recorder recorder = new Vcc4Screen.Recorder();
    private int ticks;

    public AerialScreen(BlockPos pos, AerialView view) {
        super(Component.translatable("gui.create_stats.vcc_mk4_aerial.title"));
        this.pos = pos.immutable();
        this.console = new AerialConsole(new Host(), view, lastPage);
    }

    @Override
    public BlockPos pos() {
        return pos;
    }

    public void accept(AerialView view) {
        console.accept(view);
    }

    @Override
    public boolean passesKeys() {
        return console.passesKeys();
    }

    @Override
    public int maxEffectiveGuiScale() {
        Window window = Minecraft.getInstance().getWindow();
        int own = Math.max(1, (int) Math.round(window.getGuiScale()));
        for (int scale = own; scale > 1; scale--) {
            if (window.getWidth() / scale >= AerialConsole.MIN_W + 2 * AerialConsole.MARGIN
                    && window.getHeight() / scale >= AerialConsole.MIN_H + 2 * AerialConsole.MARGIN) {
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
        console.tick();
        if (++ticks % KEEPALIVE_TICKS == 0) PacketDistributor.sendToServer(AerialActionPacket.of(pos, AerialActionPacket.KEEPALIVE));
    }

    @Override
    public void onClose() {
        PacketDistributor.sendToServer(AerialActionPacket.of(pos, AerialActionPacket.CLOSE));
        lastPage = console.page();
        super.onClose();
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partialTick) {
        super.render(g, mx, my, partialTick);
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
        if (console.keyPressed(keyCode, scanCode)) return true;

        if (minecraft != null && minecraft.options.keyInventory.matches(keyCode, scanCode)) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        console.keyReleased(keyCode, scanCode);
        return super.keyReleased(keyCode, scanCode, modifiers);
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
        public String keyName(int keyId) {
            return Vcc4Keys.name(keyId);
        }

        @Override
        public List<String> keyAlso(int keyId) {
            return Vcc4Keys.alsoBound(keyId);
        }

        @Override
        public boolean keyLeavesSeat(int keyId) {
            return Vcc4Keys.leavesSeat(keyId);
        }

        @Override
        public int resolveKey(int glfwKey, int scanCode) {
            return Vcc4Keys.resolve(glfwKey, scanCode);
        }

        @Override
        public int resolveMouse(int button) {
            return Vcc4Keys.resolveMouse(button);
        }

        @Override
        public void send(int action, int a, int b, long target) {
            PacketDistributor.sendToServer(new AerialActionPacket(pos, action, a, b, target));
        }

        @Override
        public void click() {
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
        }
    }
}
