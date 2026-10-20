package com.createstats.vccmk4.client;

import com.createstats.client.LocalGuiDensity;
import com.createstats.surveyor.network.RouteActionPacket;
import com.createstats.vccmk4.console.ConsoleCanvas;
import com.createstats.vccmk4.console.ConsoleHost;
import com.createstats.vccmk4.console.Vcc4Console;
import com.createstats.vccmk4.network.Vcc4ActionPacket;
import com.createstats.vccmk4.network.Vcc4Snapshot;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Vcc4Screen extends Screen implements LocalGuiDensity, HelmScreen {
    private static final int KEEPALIVE_TICKS = 40;
    private static Vcc4Console.Page lastPage = Vcc4Console.Page.BOARD;

    private final BlockPos pos;
    private final Vcc4Console console;
    private final Recorder recorder = new Recorder();
    private int ticks;

    public Vcc4Screen(BlockPos pos, Vcc4Snapshot snapshot) {
        super(Component.translatable("gui.create_stats.vcc_mk4.title"));
        this.pos = pos.immutable();
        this.console = new Vcc4Console(new Host(), snapshot, lastPage);
    }

    @Override
    public BlockPos pos() {
        return pos;
    }

    public void accept(Vcc4Snapshot snapshot) {
        console.accept(snapshot);
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
            if (window.getWidth() / scale >= Vcc4Console.MIN_W + 2 * Vcc4Console.MARGIN
                    && window.getHeight() / scale >= Vcc4Console.MIN_H + 2 * Vcc4Console.MARGIN) {
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
        if (++ticks % KEEPALIVE_TICKS == 0) {
            PacketDistributor.sendToServer(Vcc4ActionPacket.of(pos, Vcc4ActionPacket.KEEPALIVE));
        }
    }

    @Override
    public void onClose() {
        PacketDistributor.sendToServer(Vcc4ActionPacket.of(pos, Vcc4ActionPacket.CLOSE));
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
            PacketDistributor.sendToServer(new Vcc4ActionPacket(pos, action, a, b, target));
        }

        @Override
        public void sendOrder(int op, UUID console, UUID pathway, int a) {
            PacketDistributor.sendToServer(new RouteActionPacket(op, console, pos.asLong(), pathway, a));
        }

        @Override
        public void click() {
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
        }
    }

    static final class Recorder implements ConsoleCanvas {
        private record Text(String text, int x, int y, int colour) {
        }

        private record Quad(float[] xy, int colour) {
        }

        private static final class Layer {
            final List<int[]> fills = new ArrayList<>();
            final List<Quad> quads = new ArrayList<>();
            final List<Text> texts = new ArrayList<>();
        }

        private final List<Layer> layers = new ArrayList<>();
        private Layer current;

        void begin() {
            layers.clear();
            current = new Layer();
            layers.add(current);
        }

        @Override
        public void fill(int x0, int y0, int x1, int y1, int argb) {
            if (x1 > x0 && y1 > y0) current.fills.add(new int[]{x0, y0, x1, y1, argb});
        }

        @Override
        public void text(String text, int x, int y, int argb) {
            if (text != null && !text.isEmpty()) current.texts.add(new Text(text, x, y, argb));
        }

        @Override
        public void quad(float x0, float y0, float x1, float y1, float x2, float y2, float x3, float y3, int argb) {
            current.quads.add(new Quad(new float[]{x0, y0, x1, y1, x2, y2, x3, y3}, argb));
        }

        @Override
        public void layer() {
            current = new Layer();
            layers.add(current);
        }

        void replay(GuiGraphics g, net.minecraft.client.gui.Font font) {
            g.drawManaged(() -> {
                for (Layer layer : layers) {
                    for (int[] f : layer.fills) g.fill(f[0], f[1], f[2], f[3], f[4]);
                    if (!layer.quads.isEmpty()) {
                        VertexConsumer vc = g.bufferSource().getBuffer(RenderType.gui());
                        Matrix4f m = g.pose().last().pose();
                        for (Quad quad : layer.quads) {
                            float[] q = quad.xy();

                            vc.addVertex(m, q[0], q[1], 0).setColor(quad.colour());
                            vc.addVertex(m, q[6], q[7], 0).setColor(quad.colour());
                            vc.addVertex(m, q[4], q[5], 0).setColor(quad.colour());
                            vc.addVertex(m, q[2], q[3], 0).setColor(quad.colour());
                        }
                    }
                    for (Text t : layer.texts) g.drawString(font, t.text(), t.x(), t.y(), t.colour(), false);
                }
            });
        }
    }
}
