package com.createstats.book.screen;

import com.createstats.book.layout.BookCanvas;
import com.createstats.book.specimen.ManualSpecimen;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

@OnlyIn(Dist.CLIENT)
final class GuiBookCanvas implements BookCanvas {
    private static final float RAISE = 200.0f;

    private static final float LEAF = 420.0f;

    record Hot(String id, int x0, int y0, int x1, int y1) {
        boolean contains(double x, double y) {
            return x >= x0 && x < x1 && y >= y0 && y < y1;
        }
    }

    private final GuiGraphics g;
    private final Font font;
    private final Function<String, ManualSpecimen> specimens;
    private final Map<String, ItemStack> stacks = new HashMap<>();
    final List<Hot> hots = new ArrayList<>();
    private int flips;
    private float raised;

    GuiBookCanvas(GuiGraphics g, Font font, Function<String, ManualSpecimen> specimens) {
        this.g = g;
        this.font = font;
        this.specimens = specimens;
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
        if ((argb >>> 24) == 0 || x1 <= x0 || y1 <= y0) {
            return;
        }
        g.fill(x0, y0, x1, y1, argb);
    }

    @Override
    public void text(String s, int x, int y, int argb, boolean bold) {
        if (s == null || s.isEmpty()) {
            return;
        }
        if (bold) {
            g.drawString(font, Component.literal(s).withStyle(ChatFormatting.BOLD), x, y, argb, false);
        } else {
            g.drawString(font, s, x, y, argb, false);
        }
    }

    @Override
    public void shadowText(String s, int x, int y, int argb) {
        g.drawString(font, s, x, y, argb, true);
    }

    @Override
    public void textScaled(String s, int x, int y, int argb, int scale) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0.0f);
        g.pose().scale(scale, scale, 1.0f);
        g.drawString(font, s, 0, 0, argb, false);
        g.pose().popPose();
    }

    @Override
    public void item(String registryId, int x, int y) {
        final ItemStack stack = stacks.computeIfAbsent(registryId, GuiBookCanvas::stack);
        if (!stack.isEmpty()) {
            g.renderItem(stack, x, y);
        }
    }

    private static ItemStack stack(String registryId) {
        final ResourceLocation id = ResourceLocation.tryParse(registryId);
        if (id == null) {
            return ItemStack.EMPTY;
        }
        final Item item = BuiltInRegistries.ITEM.get(id);
        return item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
    }

    @Override
    public void sprite(String spriteId, int x, int y, int w, int h) {
        g.blitSprite(ResourceLocation.withDefaultNamespace(spriteId), x, y, w, h);
    }

    @Override
    public void paper(int x0, int y0, int x1, int y1) {
        BookGrain.tile(g, BookGrain.PAPER, x0, y0, x1, y1);
    }

    @Override
    public void vinyl(int x0, int y0, int x1, int y1) {
        BookGrain.tile(g, BookGrain.VINYL, x0, y0, x1, y1);
    }

    @Override
    public void specimen(String specimenId, int x, int y, int num, int den, int mouseX, int mouseY) {
        final ManualSpecimen s = specimens.apply(specimenId);
        if (s == null) {
            return;
        }
        final float k = num / (float) den;
        final int w = (int) Math.ceil(s.specimenWidth() * k);
        final int h = (int) Math.ceil(s.specimenHeight() * k);
        final boolean inside = flips == 0 && mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
        final int mx = inside ? (int) Math.floor((mouseX - x) / k) : -10000;
        final int my = inside ? (int) Math.floor((mouseY - y) / k) : -10000;
        g.pose().pushPose();
        g.pose().translate(x, y, 0.0f);
        g.pose().scale(k, k, 1.0f);
        if (s.clipsItsDrawing()) {
            // vanilla's scissor lands at the window corner, hence PosedGraphics
            final GuiGraphics posed = PosedGraphics.over(g);
            s.renderSpecimen(posed, mx, my);
            posed.flush();
        } else {
            s.renderSpecimen(g, mx, my);
        }
        g.pose().popPose();
    }

    @Override
    public void hot(String id, int x0, int y0, int x1, int y1) {
        if (flips == 0) {
            hots.add(new Hot(id, x0, y0, x1, y1));
        }
    }

    @Override
    public void pushFlip(int pivotX, float scaleX) {
        flips++;
        g.pose().pushPose();
        g.pose().translate(pivotX, 0.0f, LEAF);
        g.pose().scale(scaleX, 1.0f, 1.0f);
        g.pose().translate(-pivotX, 0.0f, 0.0f);
    }

    @Override
    public void popFlip() {
        g.pose().popPose();
        flips--;
    }

    @Override
    public void raise(int z) {
        final float want = z * RAISE;
        if (want != raised) {
            g.pose().translate(0.0f, 0.0f, want - raised);
            raised = want;
        }
    }

    @Override
    public void line(int x0, int y0, int x1, int y1, int argb) {
        g.drawManaged(() -> BookCanvas.super.line(x0, y0, x1, y1, argb));
    }

    @Override
    public void dots(int x0, int x1, int y, int argb, int step) {
        g.drawManaged(() -> BookCanvas.super.dots(x0, x1, y, argb, step));
    }

    String hotAt(double x, double y) {
        for (int i = hots.size() - 1; i >= 0; i--) {
            if (hots.get(i).contains(x, y)) {
                return hots.get(i).id();
            }
        }
        return null;
    }
}
