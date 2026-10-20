package com.createstats.battle.client;

import com.createstats.battle.console.ConsoleHost;
import com.createstats.battle.console.MarkerConsole;
import com.createstats.battle.net.SectionMarkerPacket;
import com.createstats.blockentity.DamageObserverBE;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

// escape saves too. if you typed a name you meant it
public class SectionMarkerScreen extends Screen {
    private final DamageObserverBE be;
    private final MarkerConsole console;
    private final ConsoleRecorder recorder = new ConsoleRecorder();
    private boolean sent;

    public SectionMarkerScreen(DamageObserverBE be) {
        super(Component.translatable("battle.create_stats.marker.title"));
        this.be = be;
        this.console = new MarkerConsole(new Host(), be.name(), be.cover(), be.permille(), be.attached(), be.reportsTo());
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
        console.accept(be.permille(), be.attached(), be.reportsTo());
        console.tick();
        if (console.done()) onClose();
    }

    @Override
    public void onClose() {
        if (!sent) {
            sent = true;
            PacketDistributor.sendToServer(new SectionMarkerPacket(be.getBlockPos(), console.name(), console.cover()));
        }
        super.onClose();
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
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
        if (console.mouseClicked(mx, my, button)) {
            if (console.done()) onClose();
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean charTyped(char ch, int modifiers) {
        return console.charTyped(ch) || super.charTyped(ch, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (console.keyPressed(keyCode)) {
            if (console.done()) onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
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
            return idOrName == null ? "" : I18n.exists(idOrName) ? I18n.get(idOrName) : idOrName;
        }

        @Override
        public void send(int action, int a, int b, String s) {
        }

        @Override
        public void click() {
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
        }
    }
}
