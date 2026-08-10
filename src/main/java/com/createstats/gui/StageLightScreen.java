package com.createstats.gui;

import com.createstats.blockentity.StageLightBE;
import com.createstats.network.StageLightConfigPacket;
import com.createstats.util.StageLightColors;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.DyeColor;
import net.neoforged.neoforge.network.PacketDistributor;

/** [1] */
public class StageLightScreen extends Screen {

    private static final int PANEL_BG = 0xF0121821;
    private static final int PANEL_DEEP = 0xFF0B141C;
    private static final int BORDER = 0xFF315064;
    private static final int BORDER_DIM = 0xFF223241;
    private static final int TEXT = 0xFFE7F3F5;
    private static final int TEXT_DIM = 0xFF8FA9B4;
    private static final int CYAN = 0xFF4AD9FF;
    private static final int GREEN = 0xFF62E8A3;
    private static final int AMBER = 0xFFFFC15E;
    private static final int TRACK = 0xFF09121A;
    private static final int SHADOW = 0x8A000000;

    private final StageLightBE be;

    private int pan;
    private int tilt;
    private int range;
    private int brightness;
    private int focus;
    private int beamWidth;
    private int lightSpread;
    private int tint;
    private boolean beamVisible;
    private boolean volumetric;
    private boolean lit;
    private int effect;
    private int effectSpeed;

    private int panelLeft;
    private int panelTop;
    private static final int LEFT_COL = 280;
    private final int panelWidth = 508;
    private final int panelHeight = 430;

    /** Colour-filter swatches: index 0 = None, 1..16 = the dye colours. */
    private static final int SWATCH_COUNT = 17;
    private static final int SWATCH_SIZE = 13;
    private static final int SWATCH_STEP = 14;
    private int swatchX;
    private int swatchY;

    private final Slider[] sliders = new Slider[8];
    private Slider dragging;
    private Button onOffButton;
    private Button coneButton;
    private Button fogButton;
    private Button effectButton;

    public StageLightScreen(StageLightBE be) {
        super(Component.translatable("block.create_stats.stage_light"));
        this.be = be;
        this.pan = be.getPan();
        this.tilt = be.getTilt();
        this.range = be.getRange();
        this.brightness = be.getBrightness();
        this.focus = be.getFocus();
        this.beamWidth = be.getBeamWidth();
        this.lightSpread = be.getLightSpread();
        this.tint = be.getTint();
        this.beamVisible = be.isBeamVisible();
        this.volumetric = be.isVolumetric();
        this.lit = be.isLit();
        this.effect = be.getEffect();
        this.effectSpeed = be.getEffectSpeed();
    }

    @Override
    protected void init() {
        super.init();
        this.clearWidgets();
        this.panelLeft = (this.width - this.panelWidth) / 2;
        this.panelTop = (this.height - this.panelHeight) / 2;

        int x = this.panelLeft + 16;
        int w = LEFT_COL - 32;
        int y = this.panelTop + 44;
        int rowH = 34;

        this.sliders[0] = new Slider(Component.translatable("block.create_stats.stage_light.pan").getString(), x, y, w, -StageLightBE.MAX_TILT_DEG, StageLightBE.MAX_TILT_DEG,
                () -> this.pan, v -> this.pan = v, v -> (v > 0 ? "+" : "") + v + "\u00B0");
        y += rowH;
        this.sliders[1] = new Slider(Component.translatable("block.create_stats.stage_light.tilt").getString(), x, y, w, -StageLightBE.MAX_TILT_DEG, StageLightBE.MAX_TILT_DEG,
                () -> this.tilt, v -> this.tilt = v, v -> (v > 0 ? "+" : "") + v + "\u00B0");
        y += rowH;
        this.sliders[2] = new Slider(Component.translatable("gui.create_stats.stage_light.slider.range").getString(), x, y, w, StageLightBE.MIN_RANGE, this.be.getMaxRange(),
                () -> this.range, v -> this.range = v, v -> Component.translatable("gui.create_stats.stage_light.value.blocks", v).getString());
        y += rowH;
        this.sliders[3] = new Slider(Component.translatable("gui.create_stats.stage_light.slider.brightness").getString(), x, y, w, StageLightBE.MIN_BRIGHTNESS, StageLightBE.MAX_BRIGHTNESS,
                () -> this.brightness, v -> this.brightness = v, v -> v + " / 15");
        y += rowH;
        this.sliders[4] = new Slider(Component.translatable("gui.create_stats.stage_light.slider.focus").getString(), x, y, w, StageLightBE.MIN_FOCUS, StageLightBE.MAX_FOCUS,
                () -> this.focus, v -> this.focus = v,
                v -> Component.translatable(v <= 3 ? "gui.create_stats.stage_light.value.tight"
                        : v >= 8 ? "gui.create_stats.stage_light.value.flood"
                        : "gui.create_stats.stage_light.value.beam", v).getString());
        y += rowH;
        this.sliders[5] = new Slider(Component.translatable("gui.create_stats.stage_light.slider.beam_width").getString(), x, y, w, StageLightBE.MIN_WIDTH, StageLightBE.MAX_WIDTH,
                () -> this.beamWidth, v -> this.beamWidth = v, v -> Component.translatable("gui.create_stats.stage_light.value.blocks_cone", v).getString());
        y += rowH;
        this.sliders[6] = new Slider(Component.translatable("gui.create_stats.stage_light.slider.light_spread").getString(), x, y, w, StageLightBE.MIN_WIDTH, StageLightBE.MAX_WIDTH,
                () -> this.lightSpread, v -> this.lightSpread = v, v -> Component.translatable("gui.create_stats.stage_light.value.blocks_light", v).getString());
        y += rowH;
        this.sliders[7] = new Slider(Component.translatable("gui.create_stats.stage_light.slider.effect_speed").getString(), x, y, w, StageLightBE.MIN_EFFECT_SPEED, StageLightBE.MAX_EFFECT_SPEED,
                () -> this.effectSpeed, v -> this.effectSpeed = v, v -> v + " / 10");

        this.swatchX = x;
        this.swatchY = this.panelTop + 322;

        int toggleY = this.panelTop + 348;
        this.coneButton = this.addRenderableWidget(Button.builder(this.coneLabel(), b -> {
            this.beamVisible = !this.beamVisible;
            b.setMessage(this.coneLabel());
            this.sendUpdate();
        }).bounds(this.panelLeft + 16, toggleY, 120, 20).build());
        this.fogButton = this.addRenderableWidget(Button.builder(this.fogLabel(), b -> {
            this.volumetric = !this.volumetric;
            b.setMessage(this.fogLabel());
            this.sendUpdate();
        }).bounds(this.panelLeft + LEFT_COL - 16 - 120, toggleY, 120, 20).build());
        this.effectButton = this.addRenderableWidget(Button.builder(this.effectLabel(), b -> {
            this.effect = (this.effect + 1) % StageLightBE.EFFECT_COUNT;
            b.setMessage(this.effectLabel());
            this.sendUpdate();
        }).bounds(this.panelLeft + 16, toggleY + 24, LEFT_COL - 32, 20).build());

        int buttonY = this.panelTop + this.panelHeight - 28;
        this.onOffButton = this.addRenderableWidget(Button.builder(this.onOffLabel(), b -> {
            this.lit = !this.lit;
            b.setMessage(this.onOffLabel());
            this.sendUpdate();
        }).bounds(this.panelLeft + 16, buttonY, 120, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("gui.create_stats.stage_light.close"), b -> this.onClose())
                .bounds(this.panelLeft + LEFT_COL - 16 - 80, buttonY, 80, 20).build());
    }

    private Component onOffLabel() {
        return Component.translatable(this.lit
                ? "gui.create_stats.stage_light.power.on"
                : "gui.create_stats.stage_light.power.off");
    }

    private Component coneLabel() {
        return Component.translatable(this.beamVisible
                ? "gui.create_stats.stage_light.cone.on"
                : "gui.create_stats.stage_light.cone.off");
    }

    private Component fogLabel() {
        return Component.translatable(this.volumetric
                ? "gui.create_stats.stage_light.fog.on"
                : "gui.create_stats.stage_light.fog.off");
    }

    private Component effectLabel() {
        return Component.translatable("gui.create_stats.stage_light.effect", effectName(this.effect));
    }

    static String effectName(int effect) {
        return Component.translatable(switch (effect) {
            case StageLightBE.EFFECT_STROBE -> "gui.create_stats.stage_light.effect.strobe";
            case StageLightBE.EFFECT_PULSE -> "gui.create_stats.stage_light.effect.pulse";
            case StageLightBE.EFFECT_RAINBOW -> "gui.create_stats.stage_light.effect.rainbow";
            default -> "gui.create_stats.stage_light.effect.none";
        }).getString();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // Empty: avoids the vanilla blurred background on a plain Screen.
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g, mouseX, mouseY, partialTick);
        // FE Dependent Mode panel in the free screen corner (self-gating).
        com.createstats.power.client.FeStatusWidget.draw(g, this.font, this.width - 130, 8, 120, this.be.feConsumer());
        g.fill(this.panelLeft + 5, this.panelTop + 6, this.panelLeft + this.panelWidth + 5, this.panelTop + this.panelHeight + 6, SHADOW);
        g.fill(this.panelLeft, this.panelTop, this.panelLeft + this.panelWidth, this.panelTop + this.panelHeight, PANEL_BG);
        drawBorder(g, this.panelLeft, this.panelTop, this.panelWidth, this.panelHeight, BORDER);
        g.fill(this.panelLeft + 1, this.panelTop + 1, this.panelLeft + this.panelWidth - 1, this.panelTop + 3, CYAN);

        final boolean mk2 = this.be instanceof com.createstats.blockentity.StageLightMk2BE;
        g.drawString(this.font, Component.translatable(mk2 ? "block.create_stats.stage_light_mk2" : "block.create_stats.stage_light"),
                this.panelLeft + 16, this.panelTop + 12, TEXT, false);
        if (mk2) {
            final com.createstats.blockentity.StageLightMk2BE m = (com.createstats.blockentity.StageLightMk2BE) this.be;
            final Component upgrade = Component.translatable(m.hasBeaconUpgrade()
                    ? "gui.create_stats.stage_light.upgrade.installed"
                    : "gui.create_stats.stage_light.upgrade.hint");
            g.drawString(this.font, upgrade, this.panelLeft + 16, this.panelTop + 24, m.hasBeaconUpgrade() ? GREEN : TEXT_DIM, false);
        } else {
            g.drawString(this.font, Component.translatable("gui.create_stats.stage_light.subtitle"), this.panelLeft + 16, this.panelTop + 24, TEXT_DIM, false);
        }

        for (Slider s : this.sliders) {
            s.render(g, mouseX, mouseY);
        }

        // Range rule hint under the Range slider.
        Slider rangeSlider = this.sliders[2];
        Component hint = this.range <= this.be.getPeakRange()
                ? Component.translatable("gui.create_stats.stage_light.range_hint.full", this.be.getPeakRange())
                : Component.translatable("gui.create_stats.stage_light.range_hint.beyond", this.be.getPeakRange());
        g.drawString(this.font, hint, rangeSlider.x, rangeSlider.y + 24, AMBER, false);

        // Beam colour filter swatches (None + 16 dye colours).
        g.drawString(this.font, Component.translatable("gui.create_stats.stage_light.beam_filter"), this.swatchX, this.swatchY - 12, TEXT_DIM, false);
        for (int i = 0; i < SWATCH_COUNT; i++) {
            int sx = this.swatchX + i * SWATCH_STEP;
            int x2 = sx + SWATCH_SIZE;
            int y2 = this.swatchY + SWATCH_SIZE;
            boolean selected = this.tint == swatchTint(i);
            if (i == 0) {
                g.fill(sx, this.swatchY, x2, y2, 0xFF1B232C);
                g.drawString(this.font, "\u00D7", sx + 3, this.swatchY + 3, 0xFFB7C4CC, false);
            } else {
                g.fill(sx, this.swatchY, x2, y2, 0xFF000000 | swatchTint(i));
            }
            g.renderOutline(sx, this.swatchY, SWATCH_SIZE, SWATCH_SIZE, selected ? 0xFFFFFFFF : 0xFF0A1219);
        }

        // Live beam projection diagram (right column), reads the live slider values.
        this.drawBeamDiagram(g, this.panelLeft + LEFT_COL + 12, this.panelTop + 44, this.panelWidth - LEFT_COL - 24, this.panelHeight - 60);

        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (mouseY >= this.swatchY && mouseY < this.swatchY + SWATCH_SIZE) {
                for (int i = 0; i < SWATCH_COUNT; i++) {
                    int sx = this.swatchX + i * SWATCH_STEP;
                    if (mouseX >= sx && mouseX < sx + SWATCH_SIZE) {
                        this.tint = swatchTint(i);
                        this.sendUpdate();
                        return true;
                    }
                }
            }
            for (Slider s : this.sliders) {
                if (s.contains(mouseX, mouseY)) {
                    this.dragging = s;
                    s.setFromMouse(mouseX);
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /** RGB a swatch represents: index 0 = no filter, else the matching dye colour. */
    private static int swatchTint(int index) {
        return index == 0 ? StageLightBE.NO_TINT : StageLightColors.rgb(DyeColor.values()[index - 1]);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (button == 0 && this.dragging != null) {
            this.dragging.setFromMouse(mouseX);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && this.dragging != null) {
            this.dragging = null;
            this.sendUpdate();
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        for (Slider s : this.sliders) {
            if (s.contains(mouseX, mouseY)) {
                s.nudge((int) Math.signum(scrollY));
                this.sendUpdate();
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private void sendUpdate() {
        PacketDistributor.sendToServer(new StageLightConfigPacket(
                this.be.getBlockPos(), this.pan, this.tilt, this.range, this.brightness, this.focus, this.tint,
                this.beamVisible, this.volumetric, this.lit, this.beamWidth, this.lightSpread,
                this.effect, this.effectSpeed));
    }

    @Override
    public void onClose() {
        this.sendUpdate();
        Minecraft.getInstance().setScreen(null);
    }

    private static void drawBorder(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }

    /** [2] */
    private void drawBeamDiagram(GuiGraphics g, int x, int y, int w, int h) {
        float time = (System.currentTimeMillis() % 100000L) / 50.0f;
        g.fill(x, y, x + w, y + h, PANEL_DEEP);
        for (int gx = x + 28; gx < x + w; gx += 34) {
            g.fill(gx, y, gx + 1, y + h, 0x0A4AD9FF);
        }
        int sy = y + (int) ((time * 0.8f) % Math.max(1, h));
        g.fill(x, sy, x + w, sy + 1, 0x124AD9FF);
        drawBorder(g, x, y, w, h, BORDER_DIM);
        g.fill(x, y, x + w, y + 16, 0xFF0E1722);
        g.drawString(this.font, Component.translatable("gui.create_stats.stage_light.beam_projection"), x + 8, y + 5, TEXT_DIM, false);
        String st = Component.translatable(this.lit
                ? "gui.create_stats.stage_light.state.live"
                : "gui.create_stats.stage_light.state.off").getString();
        g.drawString(this.font, st, x + w - 8 - this.font.width(st), y + 5, this.lit ? GREEN : 0xFF566D78, false);

        // Live effect preview: the diagram strobes/pulses/hue-sweeps with the current settings.
        long previewTime = System.currentTimeMillis() / 50L;
        int rgb = StageLightBE.effectTint(this.effect, this.effectSpeed, this.tint & 0xFFFFFF, previewTime, 0.0f);
        int beamCol = rgb == 0xFFFFFF ? 0xFFE8B0 : rgb;
        float lf = this.lit ? 1.0f : 0.28f;
        float fx = this.lit ? Math.max(0.08f, StageLightBE.effectBrightnessFactor(this.effect, this.effectSpeed, previewTime, 0.0f)) : 1.0f;
        float bright = Mth.clamp(this.brightness / 15.0f, 0.12f, 1.0f) * lf * fx;

        int ox = x + w / 2;
        int oy = y + 44;
        int areaLeft = x + 10;
        int areaRight = x + w - 10;
        int areaBottom = y + h - 26;

        g.fill(ox - 22, y + 24, ox + 22, y + 29, 0xFF16232E);
        drawBorder(g, ox - 22, y + 24, 44, 5, BORDER);
        g.fill(ox - 13, y + 28, ox - 10, oy, BORDER);
        g.fill(ox + 10, y + 28, ox + 13, oy, BORDER);

        int tiltDeg = Mth.clamp(this.tilt, -58, 58);
        double slant = Math.tan(Math.toRadians(tiltDeg * 0.7));
        double halfDeg = 4.0 + this.focus * 2.4;
        double tan = Math.tan(Math.toRadians(halfDeg));
        double maxHalf = w * 0.26;
        double capScale = maxHalf / 16.0;
        double coneCap = this.beamWidth * capScale;
        double poolCap = this.lightSpread * capScale;

        int len = Math.max(18, (int) ((areaBottom - oy) * Mth.clamp(this.range / 80.0f, 0.3f, 1.0f)));
        int ex = ox + (int) (len * slant);
        int ey = oy + len;

        int steps = 26;
        int halo = argb((int) (70 * bright), beamCol);
        int core = argb((int) (150 * bright), beamCol);
        for (int i = 1; i <= steps; i++) {
            double a = (double) i / steps;
            int cy = oy + (int) (len * a);
            int cx = ox + (int) (len * a * slant);
            double hw = Math.min(len * a * tan, coneCap);
            if (hw < 0.6) {
                continue;
            }
            int l = Math.max(areaLeft, cx - (int) hw);
            int r = Math.min(areaRight, cx + (int) hw);
            if (r > l) {
                g.fill(l, cy, r, cy + 1, halo);
            }
            int hwc = (int) (hw * 0.5);
            int lc = Math.max(areaLeft, cx - hwc);
            int rc = Math.min(areaRight, cx + hwc);
            if (rc > lc) {
                g.fill(lc, cy, rc, cy + 1, core);
            }
        }
        drawDiagLine(g, ox, oy, Math.max(areaLeft, Math.min(areaRight, ex)), ey, argb((int) (210 * bright), beamCol));

        g.fill(ox - 7, oy - 6, ox + 7, oy + 6, 0xFF222F3A);
        drawBorder(g, ox - 7, oy - 6, 14, 12, this.lit ? (0xFF000000 | beamCol) : BORDER);
        g.fill(ox - 3, oy - 2, ox + 3, oy + 4, this.lit ? (0xFF000000 | beamCol) : 0xFF2A3A46);

        double poolHalf = Math.min(len * tan, poolCap);
        int rp = Math.max(3, (int) poolHalf);
        int ry2 = Math.max(2, rp * 2 / 5);
        int pcx = Math.max(areaLeft + rp, Math.min(areaRight - rp, ex));
        int pcy = Math.min(ey, areaBottom - ry2);
        diagRing(g, pcx, pcy, rp, ry2, argb((int) (120 * bright), beamCol));
        diagRing(g, pcx, pcy, Math.max(2, rp * 3 / 5), Math.max(1, ry2 * 3 / 5), argb((int) (80 * bright), beamCol));

        if (this.volumetric && this.lit) {
            for (int k = 0; k < 6; k++) {
                double a = (time * 0.012 + k / 6.0) % 1.0;
                int cy = oy + (int) (len * a);
                int cx = ox + (int) (len * a * slant);
                double hwc = Math.min(len * a * tan, coneCap);
                int jx = (int) (Math.sin(time * 0.1 + k * 1.7) * hwc * 0.6);
                int mx = Math.max(areaLeft, Math.min(areaRight, cx + jx));
                g.fill(mx, cy, mx + 1, cy + 1, argb((int) (160 * bright * (1.0 - a)), 0xFFFFFF));
            }
        }

        int cxp = x + w - 22;
        int cyp = y + 34;
        int cr = 12;
        g.fill(cxp - cr - 2, cyp - cr - 2, cxp + cr + 3, cyp + cr + 13, 0xCC0A141C);
        diagRing(g, cxp, cyp, cr, cr, BORDER);
        double pr = Math.toRadians(this.pan);
        drawDiagLine(g, cxp, cyp, cxp + (int) (Math.sin(pr) * cr), cyp - (int) (Math.cos(pr) * cr), this.lit ? CYAN : TEXT_DIM);
        g.fill(cxp - 1, cyp - 1, cxp + 1, cyp + 1, TEXT);
        String panLabel = Component.translatable("gui.create_stats.stage_light.pan_compass").getString();
        g.drawString(this.font, panLabel, cxp - this.font.width(panLabel) / 2, cyp + cr + 2, TEXT_DIM, false);

        int capY = y + h - 22;
        String l1 = Component.translatable("gui.create_stats.stage_light.caption.line1", this.tilt, this.range, this.focus).getString();
        String l2 = Component.translatable("gui.create_stats.stage_light.caption.line2", this.beamWidth, this.lightSpread).getString();
        g.drawString(this.font, l1, x + 8, capY, TEXT_DIM, false);
        g.drawString(this.font, l2, x + 8, capY + 10, 0xFF566D78, false);
    }

    private static int argb(int alpha, int rgb) {
        int a = Math.max(0, Math.min(255, alpha));
        return (a << 24) | (rgb & 0xFFFFFF);
    }

    private void diagRing(GuiGraphics g, int cx, int cy, int rx, int ry, int color) {
        if (rx <= 0 || ry <= 0) {
            return;
        }
        for (int d = 0; d < 360; d += 6) {
            double rad = Math.toRadians(d);
            int px = cx + (int) Math.round(Math.cos(rad) * rx);
            int py = cy + (int) Math.round(Math.sin(rad) * ry);
            g.fill(px, py, px + 1, py + 1, color);
        }
    }

    private void drawDiagLine(GuiGraphics g, int x0, int y0, int x1, int y1, int color) {
        int dx = Math.abs(x1 - x0);
        int dy = -Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int err = dx + dy;
        int x = x0;
        int y = y0;
        while (true) {
            g.fill(x, y, x + 1, y + 1, color);
            if (x == x1 && y == y1) {
                break;
            }
            int e2 = 2 * err;
            if (e2 >= dy) {
                err += dy;
                x += sx;
            }
            if (e2 <= dx) {
                err += dx;
                y += sy;
            }
        }
    }

    /** A simple labelled horizontal slider. */
    private final class Slider {
        private final String label;
        final int x;
        final int y;
        private final int w;
        private final int min;
        private final int max;
        private final java.util.function.IntSupplier getter;
        private final java.util.function.IntConsumer setter;
        private final java.util.function.IntFunction<String> formatter;
        private static final int TRACK_Y_OFFSET = 11;
        private static final int H = 18;

        Slider(String label, int x, int y, int w, int min, int max,
               java.util.function.IntSupplier getter, java.util.function.IntConsumer setter,
               java.util.function.IntFunction<String> formatter) {
            this.label = label;
            this.x = x;
            this.y = y;
            this.w = w;
            this.min = min;
            this.max = max;
            this.getter = getter;
            this.setter = setter;
            this.formatter = formatter;
        }

        boolean contains(double mx, double my) {
            return mx >= this.x && mx < this.x + this.w && my >= this.y + TRACK_Y_OFFSET - 4 && my < this.y + TRACK_Y_OFFSET + 8;
        }

        void setFromMouse(double mx) {
            float t = Mth.clamp((float) ((mx - this.x) / Math.max(1, this.w)), 0.0f, 1.0f);
            this.setter.accept(Math.round(Mth.lerp(t, this.min, this.max)));
        }

        void nudge(int dir) {
            this.setter.accept(Mth.clamp(this.getter.getAsInt() + dir, this.min, this.max));
        }

        void render(GuiGraphics g, int mouseX, int mouseY) {
            int value = this.getter.getAsInt();
            float t = (value - this.min) / (float) Math.max(1, this.max - this.min);
            int trackY = this.y + TRACK_Y_OFFSET;
            boolean hover = contains(mouseX, mouseY);

            g.drawString(StageLightScreen.this.font, this.label, this.x, this.y, TEXT, false);
            String valStr = this.formatter.apply(value);
            g.drawString(StageLightScreen.this.font, valStr,
                    this.x + this.w - StageLightScreen.this.font.width(valStr), this.y, CYAN, false);

            g.fill(this.x, trackY, this.x + this.w, trackY + 4, TRACK);
            drawBorder(g, this.x, trackY - 2, this.w, 8, hover ? CYAN : BORDER_DIM);
            int fill = Math.round(this.w * t);
            if (fill > 0) {
                g.fill(this.x, trackY, this.x + fill, trackY + 4, GREEN);
            }
            int knobX = this.x + fill;
            g.fill(knobX - 3, trackY - 4, knobX + 4, trackY + 8, PANEL_DEEP);
            drawBorder(g, knobX - 3, trackY - 4, 7, 12, hover ? GREEN : TEXT);
        }
    }
}
