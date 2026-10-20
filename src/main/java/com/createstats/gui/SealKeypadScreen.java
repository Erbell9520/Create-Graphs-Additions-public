package com.createstats.gui;

import com.createstats.network.OpenSecurityPanelPacket;
import com.createstats.statsmk3.ui.Easing;
import com.createstats.statsmk3.ui.Motion;
import com.createstats.statsmk3.ui.Tokens;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

// this keypad may explain itself, only the owner ever sees it
@OnlyIn(Dist.CLIENT)
public final class SealKeypadScreen extends PanelKeypadScreen {
    private static final ResourceLocation SHEET = ResourceLocation.fromNamespaceAndPath(
            "create_stats", "textures/gui/security_seal_keypad.png");

    private static final int SEAL_W = 176;
    private static final int SEAL_H = 212;
    private static final int SEAL_GRID_Y = 94;
    private static final int SEAL_BODY_Y = 54;
    private static final int SEAL_GRID_X = (SEAL_W - (20 * 3 + 4 * 2)) / 2;

    private static final int SEAL_INK = 0x6E241D;

    private static final int SEAL_PANEL_RGB = 0xC0B4B2;

    private static final float BAND_WIPE = 0.22f;

    public SealKeypadScreen(BlockPos pos) {
        super(pos);
    }

    @Override
    protected ResourceLocation sheet() {
        return SHEET;
    }

    @Override
    protected int sheetH() {
        return 320;
    }

    @Override
    protected int panelW() {
        return SEAL_W;
    }

    @Override
    protected int panelH() {
        return SEAL_H;
    }

    @Override
    protected int gridX() {
        return SEAL_GRID_X;
    }

    @Override
    protected int gridY() {
        return SEAL_GRID_Y;
    }

    @Override
    protected int headlineY() {
        return 11;
    }

    @Override
    protected int pipsY() {
        return 31;
    }

    @Override
    protected void init() {
        super.init();

        if (this.minecraft != null) {
            this.minecraft.getSoundManager().play(
                    SimpleSoundInstance.forUI(SoundEvents.IRON_DOOR_CLOSE, 0.8f, 0.7f));
        }
    }

    @Override
    protected void drawBody(GuiGraphics g, int x, int y, float partialTick) {
        final float t = openSeconds(partialTick);

        final float wipe = Motion.reducedMotion() ? 1.0f
                : Easing.easeOut(Easing.clamp01(t / BAND_WIPE));
        final int revealed = (int) (SEAL_W * wipe);
        if (revealed < SEAL_W - 2) {
            g.fill(x + Math.max(2, revealed), y + 3, x + SEAL_W - 2, y + 7,
                    0xFF000000 | SEAL_PANEL_RGB);
        }

        final String[] lines = arming()
                ? new String[]{
                        "Doors: owner only, on a click",
                        "Shutters shut, lockers ask",
                        "Only this code stands it down"}
                : new String[]{
                        "Doors go back to clearance",
                        "Shutters go back as you set them",
                        "Lockers stop asking for the code"};
        int ly = y + SEAL_BODY_Y + 4;
        for (int i = 0; i < lines.length; i++) {
            final float lit = Motion.reducedMotion() ? 1.0f
                    : Motion.stagger(Math.max(0.0f, t - BAND_WIPE), i);
            if (lit > 0.0f) {
                g.drawString(this.font, lines[i], x + 12, ly,
                        Tokens.alpha(0xFF000000 | SEAL_INK, lit), false);
            }
            ly += 10;
        }
    }

    @Override
    protected Component restingLine() {
        return Component.translatable(arming()
                ? "gui.create_stats.security.keypad_seal_arm"
                : "gui.create_stats.security.keypad_seal_stand_down");
    }

    private boolean arming() {
        return purpose() == OpenSecurityPanelPacket.SEAL_ARM;
    }
}
