package com.createstats.gui;

import java.util.ArrayList;
import java.util.List;

import com.createstats.blockentity.InductionSmelterBE;
import com.createstats.menu.InductionSmelterMenu;
import com.createstats.network.InductionSmelterActionPacket;
import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

// progress is position, the muffle is the progress bar
public class InductionSmelterScreen extends AbstractContainerScreen<InductionSmelterMenu> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath("create_stats", "textures/gui/induction_smelter.png");

    private static final int PANEL_W = 176;
    private static final int PANEL_H = 192;

    private static final int ENERGY_X = 7;
    private static final int ENERGY_Y = 17;
    private static final int ENERGY_W = 14;
    private static final int ENERGY_H = 48;

    private static final int CHAMBER_X = 48;
    private static final int CHAMBER_Y = 17;
    private static final int CHAMBER_W = 82;
    private static final int CHAMBER_H = 48;

    private static final int HEAT_X = 7;
    private static final int HEAT_Y = 67;
    private static final int HEAT_W = 121;
    private static final int HEAT_H = 10;

    private static final int LANE_X = 132;
    private static final int LANE_Y = 67;
    private static final int LANE_W = 36;
    private static final int LANE_H = 10;

    private static final int BTN_Y = 82;
    private static final int BTN_W = 34;
    private static final int BTN_H = 14;
    private static final int[] BTN_X = {8, 44, 80};

    private static final int READOUT_X = 118;
    private static final int READOUT_Y = 82;
    private static final int READOUT_W = 50;
    private static final int READOUT_H = 14;

    private static final int HOUSING_H = 6;
    private static final int COIL_H = 5;
    private static final int TUNNEL_H = 24;

    private static final int LIP_W = 8;
    private static final int BACK_W = 8;

    private static final int[] SPR_ENERGY = {176, 0, 12, 46};
    private static final int[] SPR_GLOW = {176, 48, 16, 16};
    private static final int[] SPR_LEAF = {194, 48, 8, 12};
    private static final int[] SPR_HEAT = {0, 196, 119, 8};

    private static final int COIL_LIT = 0xFFFFB856;
    private static final int COIL_CORE = 0xFFFFF2D2;
    private static final int HEAT_HI = 0xFFFFC46A;
    private static final int AMBER = 0xFFF0A42C;
    private static final int RED = 0xFFE0463A;
    private static final int GREEN = 0xFF5CD872;
    private static final int ARC_BLUE = 0xFF9CD8FF;
    private static final int INK = 0xFF404040;
    private static final int INK_DIM = 0xFF767676;
    private static final int BRASS = 0xFFCBA04C;
    private static final int BTN_FACE = 0xFF9E9E9E;
    private static final int DARK = 0xFF3A3F47;

    private int flash;
    private float lastProgress;
    private int ticks;

    public InductionSmelterScreen(InductionSmelterMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = PANEL_W;
        this.imageHeight = PANEL_H;
        this.inventoryLabelX = InductionSmelterMenu.INV_X;
        this.inventoryLabelY = InductionSmelterMenu.INV_Y - 11;
        this.titleLabelX = 8;
        this.titleLabelY = 6;
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        ticks++;

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
        renderHeat(g, x, y, partialTick);
        renderControls(g, mouseX, mouseY);
    }

    private void renderEnergy(GuiGraphics g, int x, int y) {
        final int inner = ENERGY_H - 2;
        final int filled = Math.round(inner * menu.charge());
        if (filled > 0) {
            g.blit(TEXTURE, x + ENERGY_X + 1, y + ENERGY_Y + 1 + (inner - filled),
                    SPR_ENERGY[0], SPR_ENERGY[1] + (inner - filled), ENERGY_W - 2, filled);
        }
        if (menu.flag(InductionSmelterMenu.FLAG_STARVED)) {
            g.fill(x + ENERGY_X + 1, y + ENERGY_Y + 1, x + ENERGY_X + ENERGY_W - 1,
                    y + ENERGY_Y + ENERGY_H - 1, 0x40F0A42C);
        }
    }

    private void renderHeat(GuiGraphics g, int x, int y, float partialTick) {
        final int inner = HEAT_W - 2;
        final InductionSmelterBE smelter = menu.smelter();

        final float shown = smelter == null ? menu.heat() : smelter.needleAt(partialTick);
        final int filled = Math.round(inner * Mth.clamp(shown, 0.0f, 1.0f));
        if (filled > 0) {
            g.blit(TEXTURE, x + HEAT_X + 1, y + HEAT_Y + 1, SPR_HEAT[0], SPR_HEAT[1],
                    filled, HEAT_H - 2);
        }

        final float cap = menu.heatCap();
        if (cap < 0.999f) {
            final int cx = x + HEAT_X + 1 + Math.round(inner * cap);
            g.fill(cx, y + HEAT_Y, cx + 1, y + HEAT_Y + HEAT_H, 0xFFCBA04C);
            g.fill(cx + 1, y + HEAT_Y + 1, x + HEAT_X + HEAT_W - 1, y + HEAT_Y + HEAT_H - 1,
                    0x30000000);
        }

        if (filled > 1) {
            final int hx = x + HEAT_X + filled;
            g.fill(hx, y + HEAT_Y + 1, hx + 1, y + HEAT_Y + HEAT_H - 1,
                    menu.flag(InductionSmelterMenu.FLAG_STARVED) ? AMBER : HEAT_HI);
        }
    }

    private void renderChamber(GuiGraphics g, int x, int y, float partialTick) {
        final InductionSmelterBE smelter = menu.smelter();
        final int ix = x + CHAMBER_X + 1;
        final int iy = y + CHAMBER_Y + 1;
        final int iw = CHAMBER_W - 2;

        final int topCoil = iy + HOUSING_H;
        final int bottomCoil = iy + HOUSING_H + COIL_H + TUNNEL_H;
        final int tunnelY = iy + HOUSING_H + COIL_H;

        final boolean starved = menu.flag(InductionSmelterMenu.FLAG_STARVED);
        final boolean working = menu.flag(InductionSmelterMenu.FLAG_WORKING);
        final float glow = smelter == null
                ? (working ? 1.0f : (menu.flag(InductionSmelterMenu.FLAG_HOT) ? 0.45f : 0.0f))
                : smelter.coilGlow(partialTick);
        final float open = smelter == null ? (working ? 1.0f : 0.0f)
                : smelter.shutterOpen(partialTick);
        final float travel = smelter == null ? menu.progress() : smelter.workTravel(partialTick);

        RenderSystem.enableBlend();

        renderCoils(g, ix, iw, topCoil, bottomCoil, glow, starved, partialTick);
        renderShutter(g, ix, tunnelY, open);
        renderPiece(g, smelter, ix, iw, tunnelY, travel, glow, starved);

        if (flash > 0) {
            final int alpha = Math.round(90 * (flash / 12.0f));
            g.fill(ix, tunnelY, ix + iw, tunnelY + TUNNEL_H, (alpha << 24) | 0x00FFF4D0);
        }
        RenderSystem.disableBlend();
    }

    private void renderCoils(GuiGraphics g, int ix, int iw, int topCoil, int bottomCoil,
                             float glow, boolean starved, float partialTick) {
        if (glow <= 0.02f) {
            return;
        }
        final float phase = (ticks + partialTick) * (starved ? 0.55f : 0.22f);
        int index = 0;
        for (int wx = ix + LIP_W + 1; wx < ix + iw - BACK_W - 1; wx += 3, index++) {
            float lit = 0.55f + 0.45f * Mth.sin(phase - index * 0.42f);
            if (starved) {
                if (((ticks / 2 + index * 7) % 5) == 0) {
                    continue;
                }
                lit *= 0.7f;
            }
            final int colour = blend(COIL_LIT, COIL_CORE, lit);
            final int alpha = Math.round(255 * Mth.clamp(glow * (0.5f + 0.5f * lit), 0.0f, 1.0f));
            final int argb = (alpha << 24) | (colour & 0x00FFFFFF);
            for (int top : new int[]{topCoil, bottomCoil}) {
                g.fill(wx, top + 1, wx + 1, top + COIL_H - 1, argb);
            }
        }

        final float haze = glow * (starved ? 0.16f : 0.3f);
        glow(g, ix + iw / 2, topCoil + COIL_H + 2, iw, haze, starved ? AMBER : COIL_LIT);
        glow(g, ix + iw / 2, bottomCoil - 2, iw, haze, starved ? AMBER : COIL_LIT);
    }

    private void renderShutter(GuiGraphics g, int ix, int tunnelY, float open) {
        final int lw = SPR_LEAF[2];
        final int lh = SPR_LEAF[3];
        final int slide = Math.round(open * (lh + 1));
        final int lx = ix + LIP_W;

        final int shown = lh - slide;
        if (shown <= 0) {
            return;
        }
        g.blit(TEXTURE, lx, tunnelY, SPR_LEAF[0], SPR_LEAF[1] + slide, lw, shown);

        for (int row = 0; row < shown; row++) {
            g.blit(TEXTURE, lx, tunnelY + TUNNEL_H - 1 - row,
                    SPR_LEAF[0], SPR_LEAF[1] + slide + row, lw, 1);
        }
    }

    private void renderPiece(GuiGraphics g, InductionSmelterBE smelter, int ix, int iw,
                             int tunnelY, float travel, float glow, boolean starved) {
        if (smelter == null) {
            return;
        }
        final int span0 = ix + LIP_W + 2;
        final int span1 = ix + iw - BACK_W - 18;
        final int py = tunnelY + TUNNEL_H / 2 - 8;

        final ItemStack charge = smelter.item(InductionSmelterBE.SLOT_IN);
        if (charge.isEmpty()) {
            return;
        }
        final int px = Math.round(Mth.lerp(Mth.clamp(travel, 0.0f, 1.0f), span0, span1));
        final float warmth = glow * (0.25f + 0.75f * travel * travel);
        if (warmth > 0.05f) {
            glow(g, px + 8, py + 8, 32, warmth * 0.8f, starved ? AMBER : HEAT_HI);
        }
        g.renderItem(charge, px, py);
        if (warmth > 0.05f) {
            final int wash = Math.round(190 * warmth);
            g.fill(px, py, px + 16, py + 16, (wash << 24) | 0x00E0641E);
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
        final int damper = menu.damper();

        for (int i = 0; i < BTN_X.length; i++) {
            button(g, x + BTN_X[i], y + BTN_Y, BTN_W, BTN_H,
                    Component.translatable("gui.create_stats.induction_smelter.damper_" + i),
                    damper == i, BRASS, hovering(mouseX, mouseY, BTN_X[i], BTN_Y, BTN_W, BTN_H));
        }

        final int draw = menu.reading(InductionSmelterMenu.DATA_DRAW);
        final int rated = menu.reading(InductionSmelterMenu.DATA_RATED);
        final Component text = Component.translatable("gui.create_stats.induction_smelter.fe_per_tick",
                draw > 0 ? draw : rated);
        final int colour = rated <= 0 ? INK_DIM
                : menu.flag(InductionSmelterMenu.FLAG_STARVED) ? AMBER : ARC_BLUE;
        g.drawString(font, text, x + READOUT_X + (READOUT_W - font.width(text)) / 2,
                y + READOUT_Y + 4, colour, false);

        final boolean hot = menu.flag(InductionSmelterMenu.FLAG_HOT);
        final boolean blast = menu.flag(InductionSmelterMenu.FLAG_BLASTING);
        final Component lane = Component.translatable(hot
                ? (blast ? "gui.create_stats.induction_smelter.lane_blast"
                         : "gui.create_stats.induction_smelter.lane_smelt")
                : "gui.create_stats.induction_smelter.lane_cold");
        g.drawString(font, lane, x + LANE_X + (LANE_W - font.width(lane)) / 2, y + LANE_Y + 2,
                hot ? (blast ? HEAT_HI : 0xFFE0864A) : INK_DIM, false);

        final int bank = menu.reading(InductionSmelterMenu.DATA_BANK);
        if (bank > 1) {
            final Component tag = Component.translatable(
                    "gui.create_stats.induction_smelter.bank", bank);
            g.drawString(font, tag, x + 150 - font.width(tag), y + 6, INK_DIM, false);
        }

        final boolean live = menu.reading(InductionSmelterMenu.DATA_RATED) > 0;
        lamp(g, x + PANEL_W - 21, y + 7,
                menu.flag(InductionSmelterMenu.FLAG_CUT) ? RED
                        : menu.flag(InductionSmelterMenu.FLAG_STARVED) ? AMBER
                        : live ? GREEN : DARK);
        lamp(g, x + PANEL_W - 14, y + 7, hot ? (blast ? HEAT_HI : 0xFFE0641E) : DARK);
    }

    private void button(GuiGraphics g, int x, int y, int w, int h, Component label, boolean active,
                        int accent, boolean hovered) {
        final int face = active ? accent : (hovered ? 0xFFB4B4B4 : BTN_FACE);
        g.fill(x, y, x + w, y + h, face);

        final int lit = active ? shade(accent, 1.35f) : 0xFFFFFFFF;
        final int dark = active ? shade(accent, 0.55f) : 0xFF555555;
        g.fill(x, y, x + w - 1, y + 1, lit);
        g.fill(x, y, x + 1, y + h - 1, lit);
        g.fill(x + 1, y + h - 1, x + w, y + h, dark);
        g.fill(x + w - 1, y + 1, x + w, y + h, dark);
        g.drawString(font, label, x + (w - font.width(label)) / 2, y + 3,
                active ? 0xFF201810 : INK, false);
    }

    private void lamp(GuiGraphics g, int x, int y, int colour) {
        g.fill(x, y, x + 4, y + 4, colour);
        g.fill(x, y, x + 2, y + 1, shade(colour, 1.45f));
    }

    private static int shade(int argb, float factor) {
        final int r = Mth.clamp(Math.round(((argb >> 16) & 0xFF) * factor), 0, 255);
        final int gg = Mth.clamp(Math.round(((argb >> 8) & 0xFF) * factor), 0, 255);
        final int b = Mth.clamp(Math.round((argb & 0xFF) * factor), 0, 255);
        return (argb & 0xFF000000) | (r << 16) | (gg << 8) | b;
    }

    private static int blend(int from, int to, float t) {
        final float k = Mth.clamp(t, 0.0f, 1.0f);
        final int r = Math.round(Mth.lerp(k, (from >> 16) & 0xFF, (to >> 16) & 0xFF));
        final int g = Math.round(Mth.lerp(k, (from >> 8) & 0xFF, (to >> 8) & 0xFF));
        final int b = Math.round(Mth.lerp(k, from & 0xFF, to & 0xFF));
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private boolean hovering(int mouseX, int mouseY, int x, int y, int w, int h) {
        final int mx = mouseX - leftPos;
        final int my = mouseY - topPos;
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && menu.smelter() != null) {
            final int mx = (int) mouseX;
            final int my = (int) mouseY;
            for (int i = 0; i < BTN_X.length; i++) {
                if (hovering(mx, my, BTN_X[i], BTN_Y, BTN_W, BTN_H)) {
                    PacketDistributor.sendToServer(new InductionSmelterActionPacket(
                            menu.smelter().getBlockPos(),
                            InductionSmelterActionPacket.ACTION_DAMPER, i));
                    if (minecraft != null) {
                        minecraft.getSoundManager().play(
                                SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
                    }
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void renderCustomTooltips(GuiGraphics g, int mouseX, int mouseY) {
        final List<Component> lines = new ArrayList<>();
        if (hovering(mouseX, mouseY, ENERGY_X, ENERGY_Y, ENERGY_W, ENERGY_H)) {
            lines.add(Component.translatable("gui.create_stats.induction_smelter.buffer",
                    menu.reading(InductionSmelterMenu.DATA_ENERGY), InductionSmelterBE.BUFFER));
            final int intake = menu.reading(InductionSmelterMenu.DATA_INTAKE);
            final int rated = menu.reading(InductionSmelterMenu.DATA_RATED);
            lines.add(Component.translatable("gui.create_stats.induction_smelter.supply",
                            intake, rated)
                    .withStyle(s -> s.withColor(intake < rated ? 0xF0A42C : 0x9CD8FF)));
            final int bank = menu.reading(InductionSmelterMenu.DATA_BANK);
            if (bank > 1) {
                lines.add(Component.translatable("gui.create_stats.induction_smelter.bank_note", bank)
                        .withStyle(s -> s.withColor(0x9E9E9E)));
            }
            if (menu.flag(InductionSmelterMenu.FLAG_STARVED)) {
                lines.add(Component.translatable("gui.create_stats.induction_smelter.starved")
                        .withStyle(s -> s.withColor(0xF0A42C)));
            }
        } else if (hovering(mouseX, mouseY, HEAT_X, HEAT_Y, HEAT_W + LANE_W + 4, HEAT_H)) {
            final int percent = Math.round(menu.heat() * 100.0f);
            lines.add(Component.translatable("gui.create_stats.induction_smelter.muffle", percent));
            lines.add(menu.flag(InductionSmelterMenu.FLAG_HOT)
                    ? Component.translatable(menu.flag(InductionSmelterMenu.FLAG_BLASTING)
                            ? "gui.create_stats.induction_smelter.lane_blast_note"
                            : "gui.create_stats.induction_smelter.lane_smelt_note")
                            .withStyle(s -> s.withColor(0xE0A050))
                    : Component.translatable("gui.create_stats.induction_smelter.too_cold")
                            .withStyle(s -> s.withColor(0x9E9E9E)));
            lines.add(Component.translatable("gui.create_stats.induction_smelter.rate", percent)
                    .withStyle(s -> s.withColor(0xBFC7D1)));
        } else if (hovering(mouseX, mouseY, CHAMBER_X, CHAMBER_Y, CHAMBER_W, CHAMBER_H)) {
            final InductionSmelterBE smelter = menu.smelter();
            final ItemStack result = smelter == null ? ItemStack.EMPTY : smelter.previewResult();
            if (result.isEmpty()) {
                lines.add(Component.translatable("gui.create_stats.induction_smelter.no_charge")
                        .withStyle(s -> s.withColor(0x9E9E9E)));
            } else {
                lines.add(Component.translatable("gui.create_stats.induction_smelter.making",
                        result.getCount(), result.getHoverName()));
                lines.add(Component.translatable("gui.create_stats.induction_smelter.progress",
                        Math.round(menu.progress() * 100.0f)));
            }
            statusLine(lines);
        } else if (hovering(mouseX, mouseY, BTN_X[0], BTN_Y,
                BTN_X[2] + BTN_W - BTN_X[0], BTN_H)) {
            for (int i = 0; i < InductionSmelterBE.DAMPER_PERCENT.length; i++) {
                lines.add(Component.translatable("gui.create_stats.induction_smelter.damper_line_" + i,
                        InductionSmelterBE.DAMPER_PERCENT[i]).withStyle(s -> s.withColor(0xBFC7D1)));
            }
            lines.add(Component.translatable("gui.create_stats.induction_smelter.hold_cost",
                            InductionSmelterBE.DRAW_IDLE, InductionSmelterBE.IDLE_HOLD_TICKS / 20)
                    .withStyle(s -> s.withColor(0x9E9E9E)));
        }
        if (!lines.isEmpty()) {
            g.renderComponentTooltip(font, lines, mouseX, mouseY);
        }
    }

    private void statusLine(List<Component> lines) {
        final String key;
        final int colour;
        if (menu.flag(InductionSmelterMenu.FLAG_CUT)) {
            key = "cut";
            colour = 0xE0463A;
        } else if (menu.flag(InductionSmelterMenu.FLAG_SHED)) {
            key = "shed";
            colour = 0xF0A42C;
        } else if (menu.flag(InductionSmelterMenu.FLAG_BLOCKED)) {
            key = "blocked";
            colour = 0xF0A42C;
        } else if (menu.flag(InductionSmelterMenu.FLAG_NEEDS_BLAST)) {
            key = "needs_blast";
            colour = 0xE0463A;
        } else if (menu.flag(InductionSmelterMenu.FLAG_NO_RECIPE)) {
            key = "no_recipe";
            colour = 0xF0A42C;
        } else if (menu.damper() == InductionSmelterBE.DAMPER_SHUT) {
            key = "damper_shut";
            colour = 0x9E9E9E;
        } else {
            return;
        }
        lines.add(Component.translatable("gui.create_stats.induction_smelter." + key)
                .withStyle(s -> s.withColor(colour)));
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, titleLabelX, titleLabelY, INK, false);
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, INK, false);
    }

    @Override
    protected void renderTooltip(GuiGraphics g, int x, int y) {
        if (hovering(x, y, CHAMBER_X, CHAMBER_Y, CHAMBER_W, CHAMBER_H)
                || hovering(x, y, ENERGY_X, ENERGY_Y, ENERGY_W, ENERGY_H)
                || hovering(x, y, HEAT_X, HEAT_Y, HEAT_W + LANE_W + 4, HEAT_H)) {
            return;
        }
        super.renderTooltip(g, x, y);
    }
}
