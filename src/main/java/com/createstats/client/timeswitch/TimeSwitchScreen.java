package com.createstats.client.timeswitch;

import com.createstats.blockentity.BlastShutterBE;
import com.createstats.blockentity.SwitchBE;
import com.createstats.network.TimeSwitchEditPacket;
import com.createstats.timeswitch.TimeSwitchProgram;
import com.createstats.timeswitch.TimeSwitchProgram.Preset;
import com.createstats.timeswitch.TimeSwitchText;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

@OnlyIn(Dist.CLIENT)
public class TimeSwitchScreen extends Screen {
    private static final int OUTLINE = 0xFF000000;
    private static final int HIGHLIGHT = 0xFFFFFFFF;
    private static final int FACE = 0xFFC6C6C6;
    private static final int FACE_HOT = 0xFFD8D8D8;
    private static final int SHADOW = 0xFF555555;
    private static final int LABEL = 0xFF404040;

    private static final int PAD = TimeSwitchRingLayout.PAD;

    private static final int HUB_RADIUS = 22;

    private static final Preset[] SLOTS = {Preset.DUSK_TO_DAWN, Preset.DAYTIME, Preset.CLEAR, Preset.INVERT};

    private final BlockPos pos;

    private int hovered = -1;
    private boolean done;
    private TimeSwitchRingLayout layout = TimeSwitchRingLayout.of(320, 240, 24);

    public TimeSwitchScreen(BlockPos pos) {
        super(Component.translatable("block.create_stats.time_switch"));
        this.pos = pos.immutable();
    }

    @Override
    protected void init() {
        layout = TimeSwitchRingLayout.of(width, height,
                font.width(Component.translatable("create_stats.time_switch.preset.keep")));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void tick() {
        final Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || !(mc.level.getBlockEntity(pos) instanceof SwitchBE)
                || mc.player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 64.0) {
            onClose();
            return;
        }

        if (!useHeld(mc)) {
            commit();
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        final Minecraft mc = Minecraft.getInstance();
        if (!(mc.level != null && mc.level.getBlockEntity(pos) instanceof SwitchBE be)) {
            return;
        }
        final int cx = width / 2;
        final int cy = height / 2;
        hovered = pick(mouseX - cx, mouseY - cy);
        final int current = TimeSwitchView.shownPins(be);
        final int nowHour = TimeSwitchProgram.hourOf(mc.level.getDayTime());
        if (hovered >= 0) {
            TimeSwitchView.preview(pos, result(SLOTS[hovered], current));
        } else {
            TimeSwitchView.clearPreview(pos);
        }

        final int[][] rects = layout.rects(width, height);
        for (int k = 0; k < SLOTS.length; k++) {
            final int[] r = rects[k];
            panel(g, r[0], r[1], r[2], r[3], k == hovered);
            g.drawString(font, TimeSwitchText.preset(SLOTS[k]), r[0] + PAD, r[1] + PAD, LABEL, false);
            TimeSwitchStrip.draw(g, font, r[0] + PAD, r[1] + PAD + 14, result(SLOTS[k], current), current, nowHour,
                    true);
        }

        final int[] hub = rects[SLOTS.length];
        panel(g, hub[0], hub[1], hub[2], hub[3], hovered == -1);
        g.drawString(font, Component.translatable("create_stats.time_switch.preset.keep"), hub[0] + PAD, hub[1] + PAD,
                LABEL, false);

        final java.util.List<net.minecraft.util.FormattedCharSequence> hint =
                font.split(Component.translatable("create_stats.time_switch.ring.hint"), Math.max(40, width - 16));
        int hintY = height - 12 - (hint.size() - 1) * 10;
        for (net.minecraft.util.FormattedCharSequence line : hint) {
            g.drawCenteredString(font, line, cx, hintY, HIGHLIGHT);
            hintY += 10;
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    private static int result(Preset preset, int current) {
        return TimeSwitchProgram.preset(preset, current, BlastShutterBE.DUSK, BlastShutterBE.DAWN);
    }

    private int pick(int dx, int dy) {
        if (dx * dx + dy * dy <= HUB_RADIUS * HUB_RADIUS) {
            return -1;
        }
        final double ax = dx / (double) layout.rx();
        final double ay = dy / (double) layout.ry();
        if (Math.abs(ay) >= Math.abs(ax)) {
            return ay < 0 ? 0 : 2;
        }
        return ax > 0 ? 1 : 3;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            commit();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (Minecraft.getInstance().options.keyUse.matchesMouse(button)) {
            commit();
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (Minecraft.getInstance().options.keyUse.matches(keyCode, scanCode)) {
            commit();
            return true;
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public void removed() {
        TimeSwitchView.clearPreview(pos);
        TimeSwitchInput.ringClosed();
        super.removed();
    }

    private void commit() {
        if (done) {
            return;
        }
        done = true;
        if (hovered >= 0) {
            PacketDistributor.sendToServer(TimeSwitchEditPacket.preset(pos, SLOTS[hovered]));
        } else {
            Minecraft.getInstance().getSoundManager().play(
                    SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 0.7f, 0.3f));
        }
        onClose();
    }

    private static boolean useHeld(Minecraft mc) {
        final InputConstants.Key key = mc.options.keyUse.getKey();
        final long window = mc.getWindow().getWindow();
        if (key.getType() == InputConstants.Type.MOUSE) {
            return GLFW.glfwGetMouseButton(window, key.getValue()) == GLFW.GLFW_PRESS;
        }
        return InputConstants.isKeyDown(window, key.getValue());
    }

    private static void panel(GuiGraphics g, int x, int y, int w, int h, boolean hot) {
        g.fill(x, y, x + w, y + h, hot ? HIGHLIGHT : OUTLINE);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, hot ? FACE_HOT : FACE);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, HIGHLIGHT);
        g.fill(x + 1, y + 1, x + 2, y + h - 1, HIGHLIGHT);
        g.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, SHADOW);
        g.fill(x + w - 2, y + 1, x + w - 1, y + h - 1, SHADOW);
    }
}
