package com.createstats.gui;

import com.createstats.network.LockerModePacket;
import com.createstats.security.LockerMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

@OnlyIn(Dist.CLIENT)
public class LockerModeScreen extends Screen {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            "create_stats", "textures/gui/security_locker_mode.png");

    private static final int PANEL_W = 176;
    private static final int PANEL_H = 132;
    private static final int CARD_W = 160;
    private static final int CARD_H = 40;
    private static final int CARD_X = 8;
    private static final int CARD_Y0 = 24;
    private static final int CARD_Y1 = 66;

    private static final int SPR_CARD_Y = PANEL_H;
    private static final int SPR_HOT_Y = PANEL_H + CARD_H;
    private static final int SPR_DOWN_Y = PANEL_H + CARD_H * 2;

    private static final int INK = 0x2A2E31;
    private static final int INK_DIM = 0x6E7774;
    private static final int BRASS = 0xD0A655;

    private final BlockPos pos;

    private final LockerMode current;

    private int left;
    private int top;

    private int pressed = -1;

    public LockerModeScreen(BlockPos pos, LockerMode current) {
        super(Component.translatable("gui.create_stats.locker.mode"));
        this.pos = pos;
        this.current = current;
    }

    public BlockPos target() {
        return this.pos;
    }

    @Override
    protected void init() {
        this.left = (this.width - PANEL_W) / 2;
        this.top = (this.height - PANEL_H) / 2;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        final int x = this.left;
        final int y = this.top;
        g.blit(TEXTURE, x, y, 0, 0, PANEL_W, PANEL_H);

        g.drawCenteredString(this.font, this.title, x + PANEL_W / 2, y + 8, BRASS);

        card(g, 0, CARD_Y0, mouseX, mouseY, LockerMode.PERSONAL,
                "gui.create_stats.locker.mode_personal",
                "gui.create_stats.locker.mode_personal_note");
        card(g, 1, CARD_Y1, mouseX, mouseY, LockerMode.GENERAL,
                "gui.create_stats.locker.mode_general",
                "gui.create_stats.locker.mode_general_note");

        g.drawCenteredString(this.font,
                Component.translatable("gui.create_stats.locker.mode_foot"),
                x + PANEL_W / 2, y + PANEL_H - 19, INK_DIM);
    }

    private void card(GuiGraphics g, int index, int cardY, int mouseX, int mouseY, LockerMode mode,
                      String name, String note) {
        final int x = this.left + CARD_X;
        final int y = this.top + cardY;
        final boolean hot = mouseX >= x && mouseX < x + CARD_W && mouseY >= y && mouseY < y + CARD_H;
        final int sprite = this.pressed == index ? SPR_DOWN_Y : hot ? SPR_HOT_Y : SPR_CARD_Y;
        g.blit(TEXTURE, x, y, 0, sprite, CARD_W, CARD_H);

        g.drawString(this.font, Component.translatable(name), x + 8, y + 5, INK, false);
        if (mode == this.current) {
            final Component here = Component.translatable("gui.create_stats.locker.mode_current");
            g.drawString(this.font, here, x + CARD_W - 8 - this.font.width(here), y + 5, BRASS,
                    false);
        }

        final List<FormattedCharSequence> lines =
                this.font.split(Component.translatable(note), CARD_W - 16);
        int ty = y + 17;
        for (FormattedCharSequence line : lines) {
            g.drawString(this.font, line, x + 8, ty, INK_DIM, false);
            ty += 10;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            final int index = cardAt(mouseX, mouseY);
            if (index >= 0) {
                this.pressed = index;
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        final int down = this.pressed;
        this.pressed = -1;
        if (button == 0 && down >= 0 && cardAt(mouseX, mouseY) == down) {
            choose(down);
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private int cardAt(double mouseX, double mouseY) {
        for (int index = 0; index < 2; index++) {
            final int x = this.left + CARD_X;
            final int y = this.top + (index == 0 ? CARD_Y0 : CARD_Y1);
            if (mouseX >= x && mouseX < x + CARD_W && mouseY >= y && mouseY < y + CARD_H) {
                return index;
            }
        }
        return -1;
    }

    private void choose(int index) {
        click();
        if (index == 0) {
            Minecraft.getInstance().setScreen(new LockerKeypadScreen(this.pos, true));
            return;
        }
        PacketDistributor.sendToServer(LockerModePacket.general(this.pos));

        onClose();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 49 || keyCode == 321) {
            choose(0);
            return true;
        }
        if (keyCode == 50 || keyCode == 322) {
            choose(1);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void click() {
        final Minecraft mc = Minecraft.getInstance();
        if (mc != null) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
        }
    }
}
