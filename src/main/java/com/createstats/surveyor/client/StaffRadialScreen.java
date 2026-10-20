package com.createstats.surveyor.client;

import com.createstats.vccmk4.console.ConsoleCanvas;
import com.createstats.vccmk4.console.ConsoleHost;
import com.createstats.vccmk4.console.NodeRadial;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.UUID;

public final class StaffRadialScreen extends Screen {
    private final NodeRadial radial;
    private final UUID pathway;
    private final int node;
    private int ticksOpen;

    StaffRadialScreen(NodeRadial radial, UUID pathway, int node) {
        super(Component.translatable("create_stats.surveyor.radial.title"));
        this.radial = radial;
        this.pathway = pathway;
        this.node = node;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        ticksOpen++;
        PathwayClientCache.Entry e = PathwayClientCache.get(pathway);

        if (e == null || e.pathway.node(node) == null) onClose();
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partial) {
        // dim ourselves, blur would hide the node you're editing
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        float fade = Math.min(1f, (ticksOpen + partial) / NodeRadial.FADE_TICKS);
        radial.render(new Canvas(g, font), HOST, width, height, mouseX, mouseY, fade);
        g.flush();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        radial.hover((int) mouseX - width / 2, (int) mouseY - height / 2);
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            choose();
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            onClose();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE && ticksOpen >= 3) {
            radial.hover((int) mouseX - width / 2, (int) mouseY - height / 2);
            if (radial.chosen() != null) {
                choose();
                return true;
            }
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY == 0.0) return false;
        if (radial.scroll(scrollY < 0 ? 1 : -1)) {
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.6f, 0.15f));
        }
        return true;
    }

    private void choose() {
        NodeRadial.Item it = radial.chosen();
        if (it == null) return;
        onClose();
        StaffInput.radial(it.id(), pathway, node);
    }

    private record Canvas(GuiGraphics g, Font font) implements ConsoleCanvas {
        @Override
        public void fill(int x0, int y0, int x1, int y1, int argb) {
            g.fill(x0, y0, x1, y1, argb);
        }

        @Override
        public void text(String text, int x, int y, int argb) {
            g.drawString(font, text, x, y, argb, false);
        }

        @Override
        public void quad(float x0, float y0, float x1, float y1, float x2, float y2, float x3, float y3, int argb) {
            VertexConsumer vc = g.bufferSource().getBuffer(RenderType.gui());
            Matrix4f m = g.pose().last().pose();
            vc.addVertex(m, x0, y0, 0).setColor(argb);
            vc.addVertex(m, x3, y3, 0).setColor(argb);
            vc.addVertex(m, x2, y2, 0).setColor(argb);
            vc.addVertex(m, x1, y1, 0).setColor(argb);
        }

        @Override
        public void layer() {
            g.flush();
        }
    }

    static final ConsoleHost HOST = new ConsoleHost() {
        @Override
        public int width(String text) {
            return Minecraft.getInstance().font.width(text);
        }

        @Override
        public String tr(String key, Object... args) {
            return I18n.get(key, args);
        }

        @Override
        public String name(String idOrName) {
            return I18n.exists(idOrName) ? I18n.get(idOrName) : idOrName;
        }

        @Override
        public String keyName(int keyId) {
            return "";
        }

        @Override
        public List<String> keyAlso(int keyId) {
            return List.of();
        }

        @Override
        public boolean keyLeavesSeat(int keyId) {
            return false;
        }

        @Override
        public int resolveKey(int glfwKey, int scanCode) {
            return glfwKey;
        }

        @Override
        public int resolveMouse(int button) {
            return 0;
        }

        @Override
        public void send(int action, int a, int b, long target) {
        }

        @Override
        public void click() {
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f, 0.25f));
        }
    };
}
