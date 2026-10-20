package com.createstats.gui;

import com.createstats.security.LockerCode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;

@OnlyIn(Dist.CLIENT)
public abstract class KeypadScreen extends Screen {
    protected static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            "create_stats", "textures/gui/security_locker_keypad.png");

    protected static final int PANEL_W = 132;
    protected static final int PANEL_H = 176;

    private static final int KEY = 20;
    private static final int KEY_GAP = 4;
    private static final int GRID_X = (PANEL_W - (KEY * 3 + KEY_GAP * 2)) / 2;
    private static final int GRID_Y = 58;

    private static final int SPR_KEY_X = 0;
    private static final int SPR_KEY_Y = PANEL_H;
    private static final int SPR_HOT_Y = PANEL_H + KEY;
    private static final int SPR_DOWN_Y = PANEL_H + KEY * 2;

    protected static final int INK = 0x2A2E31;
    protected static final int INK_DIM = 0x6E7774;
    protected static final int INK_BAD = 0xB4361F;
    private static final int PIP_ON = 0xC8A44C;
    private static final int PIP_OFF = 0x3A4043;

    protected final BlockPos pos;
    protected final StringBuilder typed = new StringBuilder(LockerCode.DIGITS);

    private int left;
    private int top;

    @Nullable
    protected LockerCode.Result verdict;
    protected int lockoutSeconds;

    protected int shake;

    private int sinceOpen;

    protected float openSeconds(float partialTick) {
        return (this.sinceOpen + partialTick) / 20.0f;
    }

    private int pressed = -1;

    protected KeypadScreen(BlockPos pos, Component title) {
        super(title);
        this.pos = pos;
    }

    public BlockPos target() {
        return this.pos;
    }

    protected abstract void submit(String digits);

    protected abstract Component restingLine();

    protected Component headline() {
        return this.title;
    }

    protected int panelW() {
        return PANEL_W;
    }

    protected int panelH() {
        return PANEL_H;
    }

    protected int gridX() {
        return GRID_X;
    }

    protected int gridY() {
        return GRID_Y;
    }

    protected int headlineY() {
        return 8;
    }

    protected int pipsY() {
        return 30;
    }

    protected ResourceLocation sheet() {
        return TEXTURE;
    }

    protected int sheetW() {
        return 256;
    }

    protected int sheetH() {
        return 256;
    }

    protected void drawBody(GuiGraphics g, int x, int y, float partialTick) {
    }

    protected void acceptVerdict(@Nullable LockerCode.Result result, int lockoutSeconds) {
        this.verdict = result;
        this.lockoutSeconds = lockoutSeconds;
        this.typed.setLength(0);
        if (result != null && result != LockerCode.Result.OPENED) {
            this.shake = 10;
        }
    }

    @Override
    protected void init() {
        this.left = (this.width - panelW()) / 2;
        this.top = (this.height - panelH()) / 2;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        this.sinceOpen++;
        if (this.shake > 0) {
            this.shake--;
        }
        if (this.lockoutSeconds > 0 && this.minecraft != null
                && this.minecraft.level != null && this.minecraft.level.getGameTime() % 20L == 0L) {
            this.lockoutSeconds--;
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);

        final int jitter = this.shake > 0 ? ((this.shake % 4) < 2 ? 2 : -2) : 0;
        final int x = this.left + jitter;
        final int y = this.top;
        g.blit(sheet(), x, y, 0.0f, 0.0f, panelW(), panelH(), sheetW(), sheetH());
        g.drawCenteredString(this.font, headline(), x + panelW() / 2, y + headlineY(), INK);
        drawPips(g, x, y);
        drawBody(g, x, y, partialTick);
        drawKeys(g, x, y, mouseX, mouseY);
        drawStatus(g, x, y);
    }

    private void drawPips(GuiGraphics g, int x, int y) {
        final int w = 12;
        final int gap = 6;
        final int total = LockerCode.DIGITS * w + (LockerCode.DIGITS - 1) * gap;
        int px = x + (panelW() - total) / 2;
        for (int i = 0; i < LockerCode.DIGITS; i++) {
            final boolean on = i < this.typed.length();
            g.fill(px, y + pipsY(), px + w, y + pipsY() + 12, 0xFF000000 | (on ? PIP_ON : PIP_OFF));
            px += w + gap;
        }
    }

    private void drawKeys(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        for (int index = 0; index < 12; index++) {
            final int col = index % 3;
            final int row = index / 3;
            final int kx = x + gridX() + col * (KEY + KEY_GAP);
            final int ky = y + gridY() + row * (KEY + KEY_GAP);
            final boolean hot = mouseX >= kx && mouseX < kx + KEY && mouseY >= ky && mouseY < ky + KEY;
            final int sprite = this.pressed == index ? SPR_DOWN_Y : hot ? SPR_HOT_Y : SPR_KEY_Y;

            g.blit(sheet(), kx, ky, (float) SPR_KEY_X, (float) (sprite - PANEL_H + panelH()),
                    KEY, KEY, sheetW(), sheetH());
            g.drawCenteredString(this.font, labelOf(index), kx + KEY / 2, ky + (KEY - 8) / 2,
                    locked() ? INK_DIM : INK);
        }
    }

    private static String labelOf(int index) {
        return switch (index) {
            case 9 -> "C";
            case 10 -> "0";
            case 11 -> "E";
            default -> String.valueOf(index + 1);
        };
    }

    private void drawStatus(GuiGraphics g, int x, int y) {
        final Component line;
        int colour = INK_DIM;
        if (locked()) {
            line = Component.translatable("gui.create_stats.locker.keypad_locked",
                    Math.max(1, this.lockoutSeconds));
            colour = INK_BAD;
        } else if (this.verdict == LockerCode.Result.WRONG) {
            line = Component.translatable("gui.create_stats.locker.keypad_wrong");
            colour = INK_BAD;
        } else if (this.verdict == LockerCode.Result.NO_CODE) {
            line = Component.translatable("gui.create_stats.locker.keypad_no_code");
            colour = INK_BAD;
        } else {
            line = restingLine();
        }
        g.drawCenteredString(this.font, line, x + panelW() / 2, y + panelH() - 16, colour);
    }

    protected boolean locked() {
        return this.lockoutSeconds > 0
                || this.verdict == LockerCode.Result.TAMPER
                || this.verdict == LockerCode.Result.LOCKED_OUT;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && !locked()) {
            final int index = keyAt(mouseX, mouseY);
            if (index >= 0) {
                this.pressed = index;
                press(index);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        this.pressed = -1;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private int keyAt(double mouseX, double mouseY) {
        for (int index = 0; index < 12; index++) {
            final int col = index % 3;
            final int row = index / 3;
            final int kx = this.left + gridX() + col * (KEY + KEY_GAP);
            final int ky = this.top + gridY() + row * (KEY + KEY_GAP);
            if (mouseX >= kx && mouseX < kx + KEY && mouseY >= ky && mouseY < ky + KEY) {
                return index;
            }
        }
        return -1;
    }

    private void press(int index) {
        click();
        if (index == 9) {
            this.typed.setLength(0);
            this.verdict = null;
            return;
        }
        if (index == 11) {
            send();
            return;
        }
        typeDigit(index == 10 ? '0' : (char) ('1' + index));
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (locked()) {
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        if (keyCode >= 320 && keyCode <= 329) {
            typeDigit((char) ('0' + (keyCode - 320)));
            return true;
        }
        if (keyCode >= 48 && keyCode <= 57) {
            typeDigit((char) ('0' + (keyCode - 48)));
            return true;
        }
        if (keyCode == 259) {
            if (this.typed.length() > 0) {
                this.typed.setLength(this.typed.length() - 1);
                click();
            }
            return true;
        }
        if (keyCode == 257 || keyCode == 335) {
            send();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void typeDigit(char digit) {
        if (this.typed.length() >= LockerCode.DIGITS) {
            return;
        }
        this.typed.append(digit);
        this.verdict = null;
        click();

        if (this.typed.length() == LockerCode.DIGITS) {
            send();
        }
    }

    private void send() {
        if (this.typed.length() != LockerCode.DIGITS) {
            this.shake = 8;
            return;
        }
        final String digits = this.typed.toString();

        this.typed.setLength(0);
        submit(digits);
    }

    protected void click() {
        final Minecraft mc = Minecraft.getInstance();
        if (mc != null) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
        }
    }
}
