package com.createstats.gui;

import java.util.ArrayList;
import java.util.List;

import com.createstats.blockentity.ArcFurnaceBE;
import com.createstats.menu.ArcFurnaceMenu;
import com.createstats.network.ArcFurnaceActionPacket;
import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

// it's a furnace first, electrical bits bolted on
public class ArcFurnaceScreen extends AbstractContainerScreen<ArcFurnaceMenu> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath("create_stats", "textures/gui/arc_furnace.png");

    private static final int PANEL_W = 176;
    private static final int PANEL_H = 192;

    private static final int ENERGY_X = 7;
    private static final int ENERGY_Y = 17;
    private static final int ENERGY_W = 16;
    private static final int ENERGY_H = 62;

    private static final int CHAMBER_X = 52;
    private static final int CHAMBER_Y = 16;
    private static final int CHAMBER_W = 68;
    private static final int CHAMBER_H = 64;

    private static final int ARROW_X = 124;
    private static final int ARROW_Y = 40;
    private static final int ARROW_W = 22;
    private static final int ARROW_H = 16;

    private static final int BTN_POWER_X = 8;
    private static final int BTN_POWER_Y = 84;
    private static final int BTN_POWER_W = 34;
    private static final int BTN_H = 14;
    private static final int BTN_TAP_Y = 84;
    private static final int BTN_TAP_W = 20;
    private static final int[] BTN_TAP_X = {46, 68, 90};

    private static final int READOUT_X = 114;
    private static final int READOUT_Y = 84;
    private static final int READOUT_W = 54;

    private static final int[] SPR_ENERGY = {176, 0, 14, 60};
    private static final int[] SPR_ARROW = {176, 62, 22, 16};
    private static final int[] SPR_GLOW = {176, 80, 16, 16};
    private static final int[] SPR_POOL = {176, 98, 32, 12};
    private static final int[] SPR_ELECTRODE = {210, 0, 6, 44};

    private static final int[] CHAMBER_ELECTRODES = {12, 30, 48};

    private static final int ARC_WHITE = 0xFFECF5FF;
    private static final int ARC_BLUE = 0xFF92C8FF;
    private static final int HEAT = 0xFFE0641E;
    private static final int HEAT_HI = 0xFFFFC46A;
    private static final int AMBER = 0xFFF0A42C;
    private static final int RED = 0xFFE0463A;
    private static final int GREEN = 0xFF5CD872;
    private static final int INK = 0xFF404040;
    private static final int INK_DIM = 0xFF6E6E6E;
    private static final int COPPER = 0xFFC46E34;
    private static final int BRASS = 0xFFCBA04C;
    private static final int BTN_FACE = 0xFF9E9E9E;

    private final RandomSource jitter = RandomSource.create();

    private float lift = 1.0f;

    private int flash;
    private float lastProgress;
    private int ticks;

    public ArcFurnaceScreen(ArcFurnaceMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = PANEL_W;
        this.imageHeight = PANEL_H;
        this.inventoryLabelX = ArcFurnaceMenu.INV_X;
        this.inventoryLabelY = ArcFurnaceMenu.INV_Y - 11;
        this.titleLabelX = 8;
        this.titleLabelY = 6;
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        ticks++;
        final boolean arc = menu.flag(ArcFurnaceMenu.FLAG_ARC);
        final boolean on = menu.flag(ArcFurnaceMenu.FLAG_RUNNING);
        final float target = arc ? 0.0f : (on ? 0.45f : 1.0f);
        lift += Mth.clamp(target - lift, -0.11f, 0.11f);

        final float progress = menu.progress();
        if (lastProgress > 0.55f && progress < 0.05f) {
            flash = 12;
        }
        lastProgress = progress;
        if (flash > 0) {
            flash--;
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderCustomTooltips(g, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        final int x = leftPos;
        final int y = topPos;
        g.blit(TEXTURE, x, y, 0, 0, PANEL_W, PANEL_H);

        renderEnergy(g, x, y);
        renderChamber(g, x, y, partialTick);
        renderArrow(g, x, y);
        renderControls(g, mouseX, mouseY);
    }

    private void renderEnergy(GuiGraphics g, int x, int y) {
        final int inner = ENERGY_H - 2;
        final int filled = Math.round(inner * menu.charge());
        if (filled <= 0) {
            return;
        }

        g.blit(TEXTURE, x + ENERGY_X + 1, y + ENERGY_Y + 1 + (inner - filled),
                SPR_ENERGY[0], SPR_ENERGY[1] + (inner - filled), SPR_ENERGY[2], filled);

        if (menu.flag(ArcFurnaceMenu.FLAG_STARVED)) {
            g.fill(x + ENERGY_X + 1, y + ENERGY_Y + 1, x + ENERGY_X + ENERGY_W - 1,
                    y + ENERGY_Y + ENERGY_H - 1, 0x40F0A42C);
        }
    }

    private void renderArrow(GuiGraphics g, int x, int y) {
        final int width = Math.round(ARROW_W * menu.progress());
        if (width > 0) {
            g.blit(TEXTURE, x + ARROW_X, y + ARROW_Y, SPR_ARROW[0], SPR_ARROW[1], width, ARROW_H);
        }
        if (flash > 0) {
            final int alpha = Math.round(255 * (flash / 12.0f));
            g.fill(x + ARROW_X, y + ARROW_Y + 5, x + ARROW_X + ARROW_W, y + ARROW_Y + 11,
                    (alpha << 24) | 0x00FFF4D0);
        }
    }

    private void renderChamber(GuiGraphics g, int x, int y, float partialTick) {
        final int ix = x + CHAMBER_X + 1;
        final int iy = y + CHAMBER_Y + 1;
        final int iw = CHAMBER_W - 2;
        final int ih = CHAMBER_H - 2;
        final boolean arc = menu.flag(ArcFurnaceMenu.FLAG_ARC);
        final boolean starved = menu.flag(ArcFurnaceMenu.FLAG_STARVED);
        final float progress = menu.progress();
        final float heat = arc ? (starved ? 0.55f : 1.0f) : (menu.flag(ArcFurnaceMenu.FLAG_RUNNING)
                ? 0.25f : 0.0f);

        final int floorY = iy + ih - 6;
        final int tipY = iy + Math.round(Mth.lerp(lift, 30.0f, 8.0f));

        RenderSystem.enableBlend();

        if (heat > 0.02f) {
            final float pulse = 0.86f + 0.14f * Mth.sin((ticks + partialTick) * 0.24f);
            final int poolAlpha = Math.round(255 * Math.min(1.0f, heat * pulse));
            g.setColor(1.0f, starved ? 0.62f : 1.0f, starved ? 0.42f : 1.0f, poolAlpha / 255.0f);
            g.blit(TEXTURE, ix + iw / 2 - 16, floorY - 2, SPR_POOL[0], SPR_POOL[1],
                    SPR_POOL[2], SPR_POOL[3]);
            g.setColor(1.0f, 1.0f, 1.0f, 1.0f);
            glow(g, ix + iw / 2, floorY + 2, 52, heat * 0.55f * pulse, starved ? AMBER : HEAT);
        }

        final ArcFurnaceBE furnace = menu.furnace();
        final ItemStack charge = furnace == null ? ItemStack.EMPTY : furnace.item(ArcFurnaceBE.SLOT_A);
        if (!charge.isEmpty()) {
            final int itemY = Math.round(Mth.lerp(progress, tipY + 8.0f, floorY - 6.0f));
            final int itemX = ix + iw / 2 - 8;
            if (heat > 0.05f) {
                glow(g, itemX + 8, itemY + 8, 30, heat * (0.35f + 0.45f * progress), HEAT_HI);
            }
            g.renderItem(charge, itemX, itemY);
            if (heat > 0.05f) {
                final int wash = Math.round(200 * heat * (0.25f + 0.75f * progress * progress));
                g.fill(itemX, itemY, itemX + 16, itemY + 16, (wash << 24) | (HEAT & 0x00FFFFFF));
            }
        }

        final int columnH = tipY - iy;
        for (int offx : CHAMBER_ELECTRODES) {
            if (columnH > 0) {
                g.blit(TEXTURE, ix + offx, iy, SPR_ELECTRODE[0], SPR_ELECTRODE[1],
                        SPR_ELECTRODE[2], Math.min(columnH, SPR_ELECTRODE[3]));
            }
        }

        if (arc) {
            drawArc(g, ix, tipY, floorY, starved);
        }

        if (heat > 0.4f) {
            final int count = starved ? 3 : 6;
            for (int i = 0; i < count; i++) {
                final int seed = (ticks / 3 + i * 37) & 0xFF;
                jitter.setSeed(seed * 2654435761L + i);
                final int sx = ix + 6 + jitter.nextInt(iw - 12);
                final int life = (ticks + i * 11) % 34;
                final int sy = floorY - life;
                if (sy < iy + 3) {
                    continue;
                }
                final int alpha = Math.round(200 * (1.0f - life / 34.0f));
                g.fill(sx, sy, sx + 1, sy + 1, (alpha << 24) | ((starved ? AMBER : HEAT_HI) & 0xFFFFFF));
            }
        }

        g.fill(ix, iy + ih - 2, ix + iw, iy + ih, 0x50000000);
        RenderSystem.disableBlend();
    }

    private void drawArc(GuiGraphics g, int ix, int tipY, int floorY, boolean starved) {
        final int core = starved ? AMBER : ARC_WHITE;
        final int halo = starved ? RED : ARC_BLUE;
        for (int e = 0; e < CHAMBER_ELECTRODES.length; e++) {
            final int baseX = ix + CHAMBER_ELECTRODES[e] + 3;
            jitter.setSeed((ticks / 2) * 31L + e * 977L);
            if (starved && jitter.nextInt(3) == 0) {
                continue;
            }
            int px = baseX;
            for (int yy = tipY; yy < floorY; yy += 3) {
                final int nx = baseX + jitter.nextInt(5) - 2;
                line(g, px, yy, nx, Math.min(yy + 3, floorY), halo, 3);
                line(g, px, yy, nx, Math.min(yy + 3, floorY), core, 1);
                px = nx;
            }
            glow(g, px, floorY, 16, starved ? 0.5f : 0.9f, halo);
        }
    }

    private void line(GuiGraphics g, int x0, int y0, int x1, int y1, int colour, int width) {
        final int steps = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
        final int alpha = width > 1 ? 0x55 : 0xFF;
        final int argb = (alpha << 24) | (colour & 0x00FFFFFF);
        for (int i = 0; i <= steps; i++) {
            final float t = steps == 0 ? 0.0f : i / (float) steps;
            final int px = Math.round(Mth.lerp(t, x0, x1));
            final int py = Math.round(Mth.lerp(t, y0, y1));
            g.fill(px - width / 2, py, px - width / 2 + width, py + 1, argb);
        }
    }

    private void glow(GuiGraphics g, int cx, int cy, int size, float strength, int colour) {
        if (strength <= 0.01f) {
            return;
        }
        g.setColor(((colour >> 16) & 0xFF) / 255.0f, ((colour >> 8) & 0xFF) / 255.0f,
                (colour & 0xFF) / 255.0f, Math.min(1.0f, strength));
        RenderSystem.enableBlend();
        g.blit(TEXTURE, cx - size / 2, cy - size / 2, size, size,
                SPR_GLOW[0], SPR_GLOW[1], SPR_GLOW[2], SPR_GLOW[3], 256, 256);
        g.setColor(1.0f, 1.0f, 1.0f, 1.0f);
    }

    private void renderControls(GuiGraphics g, int mouseX, int mouseY) {
        final int x = leftPos;
        final int y = topPos;
        final boolean on = menu.flag(ArcFurnaceMenu.FLAG_RUNNING);

        button(g, x + BTN_POWER_X, y + BTN_POWER_Y, BTN_POWER_W, BTN_H,
                on ? Component.translatable("gui.create_stats.arc_furnace.on")
                        : Component.translatable("gui.create_stats.arc_furnace.off"),
                on, COPPER, hovering(mouseX, mouseY, BTN_POWER_X, BTN_POWER_Y, BTN_POWER_W, BTN_H));

        final int tap = menu.tap();
        for (int i = 0; i < BTN_TAP_X.length; i++) {
            button(g, x + BTN_TAP_X[i], y + BTN_TAP_Y, BTN_TAP_W, BTN_H,
                    Component.literal("I".repeat(i + 1)), tap == i + 1, BRASS,
                    hovering(mouseX, mouseY, BTN_TAP_X[i], BTN_TAP_Y, BTN_TAP_W, BTN_H));
        }

        final int draw = menu.reading(ArcFurnaceMenu.DATA_DRAW);
        final int rated = ArcFurnaceBE.drawFor(tap);
        final Component text = Component.translatable("gui.create_stats.arc_furnace.fe_per_tick",
                draw > 0 ? draw : rated);
        final int colour = !on ? INK_DIM
                : menu.flag(ArcFurnaceMenu.FLAG_STARVED) ? AMBER
                : menu.flag(ArcFurnaceMenu.FLAG_ARC) ? ARC_BLUE : 0xFF9CD8FF;
        g.drawString(font, text, x + READOUT_X + (READOUT_W - font.width(text)) / 2,
                y + READOUT_Y + 4, colour, false);

        lamp(g, x + PANEL_W - 20, y + 8, on ? (menu.flag(ArcFurnaceMenu.FLAG_STARVED) ? AMBER : GREEN)
                : 0xFF3A3F47);
        lamp(g, x + PANEL_W - 13, y + 8, menu.flag(ArcFurnaceMenu.FLAG_ARC)
                ? (menu.flag(ArcFurnaceMenu.FLAG_STARVED) ? RED : ARC_WHITE) : 0xFF3A3F47);
    }

    private void button(GuiGraphics g, int x, int y, int w, int h, Component label, boolean active,
                        int accent, boolean hovered) {
        final int face = active ? accent : (hovered ? 0xFFB4B4B4 : BTN_FACE);
        g.fill(x, y, x + w, y + h, face);

        final int lit = active ? shade(accent, 0.55f) : 0xFFFFFFFF;
        final int dark = active ? shade(accent, 1.35f) : 0xFF555555;
        g.fill(x, y, x + w - 1, y + 1, lit);
        g.fill(x, y, x + 1, y + h - 1, lit);
        g.fill(x + 1, y + h - 1, x + w, y + h, dark);
        g.fill(x + w - 1, y + 1, x + w, y + h, dark);
        g.drawString(font, label, x + (w - font.width(label)) / 2, y + 3,
                active ? 0xFF201810 : INK, false);
    }

    private void lamp(GuiGraphics g, int x, int y, int colour) {
        g.fill(x - 1, y - 1, x + 5, y + 5, 0xFF20242A);
        g.fill(x, y, x + 4, y + 4, colour);
        g.fill(x, y, x + 2, y + 1, shade(colour, 1.45f));
    }

    private static int shade(int argb, float factor) {
        final int r = Mth.clamp(Math.round(((argb >> 16) & 0xFF) * factor), 0, 255);
        final int gg = Mth.clamp(Math.round(((argb >> 8) & 0xFF) * factor), 0, 255);
        final int b = Mth.clamp(Math.round((argb & 0xFF) * factor), 0, 255);
        return (argb & 0xFF000000) | (r << 16) | (gg << 8) | b;
    }

    private boolean hovering(int mouseX, int mouseY, int x, int y, int w, int h) {
        final int mx = mouseX - leftPos;
        final int my = mouseY - topPos;
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && menu.furnace() != null) {
            final int mx = (int) mouseX;
            final int my = (int) mouseY;
            if (hovering(mx, my, BTN_POWER_X, BTN_POWER_Y, BTN_POWER_W, BTN_H)) {
                send(ArcFurnaceActionPacket.ACTION_RUNNING,
                        menu.flag(ArcFurnaceMenu.FLAG_RUNNING) ? 0 : 1);
                return true;
            }
            for (int i = 0; i < BTN_TAP_X.length; i++) {
                if (hovering(mx, my, BTN_TAP_X[i], BTN_TAP_Y, BTN_TAP_W, BTN_H)) {
                    send(ArcFurnaceActionPacket.ACTION_TAP, i + 1);
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void send(int action, int value) {
        PacketDistributor.sendToServer(
                new ArcFurnaceActionPacket(menu.furnace().getBlockPos(), action, value));
        if (minecraft != null) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
        }
    }

    private void renderCustomTooltips(GuiGraphics g, int mouseX, int mouseY) {
        final List<Component> lines = new ArrayList<>();
        if (hovering(mouseX, mouseY, ENERGY_X, ENERGY_Y, ENERGY_W, ENERGY_H)) {
            lines.add(Component.translatable("gui.create_stats.arc_furnace.buffer",
                    menu.reading(ArcFurnaceMenu.DATA_ENERGY), ArcFurnaceBE.BUFFER));
            final int intake = menu.reading(ArcFurnaceMenu.DATA_INTAKE);
            final int draw = menu.reading(ArcFurnaceMenu.DATA_DRAW);
            lines.add(Component.translatable("gui.create_stats.arc_furnace.supply", intake, draw)
                    .withStyle(s -> s.withColor(intake < draw ? 0xF0A42C : 0x9CD8FF)));
            if (menu.flag(ArcFurnaceMenu.FLAG_STARVED)) {
                lines.add(Component.translatable("gui.create_stats.arc_furnace.starved")
                        .withStyle(s -> s.withColor(0xF0A42C)));
            }
        } else if (hovering(mouseX, mouseY, CHAMBER_X, CHAMBER_Y, CHAMBER_W, CHAMBER_H)) {
            final ArcFurnaceBE furnace = menu.furnace();
            final ItemStack result = furnace == null ? ItemStack.EMPTY : furnace.previewResult();
            if (result.isEmpty()) {
                lines.add(Component.translatable("gui.create_stats.arc_furnace.no_charge")
                        .withStyle(s -> s.withColor(0x9E9E9E)));
            } else {
                lines.add(Component.translatable("gui.create_stats.arc_furnace.making",
                        result.getCount(), result.getHoverName()));
                lines.add(Component.translatable("gui.create_stats.arc_furnace.progress",
                        Math.round(menu.progress() * 100.0f)));
                final int grade = menu.reading(ArcFurnaceMenu.DATA_GRADE);
                lines.add(grade >= 99
                        ? Component.translatable("gui.create_stats.arc_furnace.clean")
                                .withStyle(s -> s.withColor(0x5CD872))
                        : Component.translatable("gui.create_stats.arc_furnace.held", grade)
                                .withStyle(s -> s.withColor(grade >= 75 ? 0xF0A42C : 0xE0463A)));
            }
            statusLine(lines);
        } else if (hovering(mouseX, mouseY, BTN_TAP_X[0], BTN_TAP_Y,
                BTN_TAP_X[2] + BTN_TAP_W - BTN_TAP_X[0], BTN_H)) {
            for (int i = 1; i <= ArcFurnaceBE.MAX_TAP; i++) {
                lines.add(Component.translatable("gui.create_stats.arc_furnace.tap_line",
                        "I".repeat(i), ArcFurnaceBE.drawFor(i), i)
                        .withStyle(s -> s.withColor(0xBFC7D1)));
            }
        }
        if (!lines.isEmpty()) {
            g.renderComponentTooltip(font, lines, mouseX, mouseY);
        }
    }

    private void statusLine(List<Component> lines) {
        if (menu.flag(ArcFurnaceMenu.FLAG_CUT)) {
            lines.add(Component.translatable("gui.create_stats.arc_furnace.cut")
                    .withStyle(s -> s.withColor(0xE0463A)));
        } else if (menu.flag(ArcFurnaceMenu.FLAG_SHED)) {
            lines.add(Component.translatable("gui.create_stats.arc_furnace.shed")
                    .withStyle(s -> s.withColor(0xF0A42C)));
        } else if (menu.flag(ArcFurnaceMenu.FLAG_BLOCKED)) {
            lines.add(Component.translatable("gui.create_stats.arc_furnace.blocked")
                    .withStyle(s -> s.withColor(0xF0A42C)));
        } else if (menu.flag(ArcFurnaceMenu.FLAG_SLAG_FULL)) {
            lines.add(Component.translatable("gui.create_stats.arc_furnace.slag_full")
                    .withStyle(s -> s.withColor(0xF0A42C)));
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, titleLabelX, titleLabelY, INK, false);
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, INK, false);
    }

    @Override
    protected void renderTooltip(GuiGraphics g, int x, int y) {
        if (hovering(x, y, CHAMBER_X, CHAMBER_Y, CHAMBER_W, CHAMBER_H)
                || hovering(x, y, ENERGY_X, ENERGY_Y, ENERGY_W, ENERGY_H)) {
            return;
        }
        super.renderTooltip(g, x, y);
    }
}
