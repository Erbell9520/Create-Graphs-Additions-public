package com.createstats.battle.client;

import com.createstats.battle.console.ConsoleCanvas;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

// batch fills per layer, a few thousand draw calls killed the mk3 console fps
final class ConsoleRecorder implements ConsoleCanvas {
    private record Text(String text, int x, int y, int colour, int scale, boolean bold) {
    }

    private static final class Layer {
        final List<int[]> fills = new ArrayList<>();
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
        if (text != null && !text.isEmpty()) current.texts.add(new Text(text, x, y, argb, 1, false));
    }

    @Override
    public void text(String text, int x, int y, int argb, int scale, boolean bold) {
        if (text != null && !text.isEmpty()) current.texts.add(new Text(text, x, y, argb, Math.max(1, scale), bold));
    }

    @Override
    public void layer() {
        current = new Layer();
        layers.add(current);
    }

    void replay(GuiGraphics g, Font font) {
        g.drawManaged(() -> {
            for (Layer layer : layers) {
                for (int[] f : layer.fills) g.fill(f[0], f[1], f[2], f[3], f[4]);
                for (Text t : layer.texts) {
                    if (t.scale() == 1 && !t.bold()) {
                        g.drawString(font, t.text(), t.x(), t.y(), t.colour(), false);
                        continue;
                    }
                    Component line = t.bold() ? Component.literal(t.text()).withStyle(ChatFormatting.BOLD) : Component.literal(t.text());
                    if (t.scale() == 1) {
                        g.drawString(font, line, t.x(), t.y(), t.colour(), false);
                        continue;
                    }
                    g.pose().pushPose();
                    g.pose().translate(t.x(), t.y(), 0);
                    g.pose().scale(t.scale(), t.scale(), 1f);
                    g.drawString(font, line, 0, 0, t.colour(), false);
                    g.pose().popPose();
                }
            }
        });
    }
}
