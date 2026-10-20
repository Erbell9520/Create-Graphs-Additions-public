package com.createstats.gui;

import com.createstats.blockentity.PowerLimiterBE;
import com.createstats.gui.pdu.PduCard;
import com.createstats.gui.pdu.PduChip;
import com.createstats.gui.pdu.PduRect;
import com.createstats.gui.pdu.PduRing;
import com.createstats.gui.pdu.PduSegmented;
import com.createstats.gui.pdu.PduTheme;
import com.createstats.network.PowerLimiterConfigPacket;
import com.createstats.power.LimiterLadder;
import com.createstats.power.LimiterMode;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

public class PowerLimiterScreen extends Screen {
    private static final int PANEL_W = 340;
    private static final int PANEL_H = 238;
    private static final int HEADER_H = 30;
    private static final int FOOTER_H = 16;
    private static final int HERO_H = 36;
    private static final int SLIDER_H = 11;

    private static final int MIN_TRACE_H = 26;

    private static final int TRACE_INTERVAL = 10;

    private final PowerLimiterBE limiter;
    private final PduSegmented modeSeg =
            new PduSegmented("Always", "On opens", "On shuts", "Analog");

    private final PduRing trace = new PduRing(360);
    private int traceCounter;

    private PduRect panel = new PduRect(0, 0, 0, 0);
    private PduRect sliderRect = new PduRect(0, 0, 0, 0);
    private PduRect minusRect = new PduRect(0, 0, 0, 0);
    private PduRect plusRect = new PduRect(0, 0, 0, 0);
    private PduRect presetRect = new PduRect(0, 0, 0, 0);
    private PduRect modeRect = new PduRect(0, 0, 0, 0);
    private PduRect traceRect = new PduRect(0, 0, 0, 0);
    private PduRect resetRect = new PduRect(0, 0, 0, 0);
    private final int[] presetX = new int[LimiterLadder.PRESET_NOTCHES.length + 1];

    private static final String[] PRESET_LABELS =
            {"Shut", "Trickle", "Room", "Standard", "Heavy", "Max"};

    // short labels at gui scale 4, dropping presets would lose Shut which is the one you need
    private static final String[] PRESET_LABELS_SHORT = {"0", "256", "4k", "8k", "32k", "Max"};

    private boolean shortPresets;

    private boolean dragging;

    private int pendingNotch = -1;
    private int pendingAge;
    @javax.annotation.Nullable
    private LimiterMode pendingMode;
    private int pendingModeAge;

    public PowerLimiterScreen(PowerLimiterBE limiter) {
        super(Component.translatable("block.create_stats.power_limiter"));
        this.limiter = limiter;
    }

    @Override
    protected void init() {
        final int pw = Math.min(PANEL_W, width - 2 * PduTheme.BASE);
        final int ph = Math.min(PANEL_H, height - 2 * PduTheme.BASE);
        this.panel = new PduRect((width - pw) / 2, (height - ph) / 2, pw, ph);
        this.modeSeg.setSelected(this.mode().ordinal());
        this.layout();
    }

    private void layout() {
        final int inner = PduTheme.BASE;
        final int x = panel.x() + inner;
        final int w = panel.w() - inner * 2;

        int y = panel.y() + HEADER_H + HERO_H + 12;

        final int stepW = Math.max(PduChip.width(font, "-"), PduChip.width(font, "+"));
        this.minusRect = new PduRect(x, y, stepW, SLIDER_H);
        this.plusRect = new PduRect(panel.right() - inner - stepW, y, stepW, SLIDER_H);
        this.sliderRect = new PduRect(minusRect.right() + 6, y,
                Math.max(20, plusRect.x() - minusRect.right() - 12), SLIDER_H);
        y += SLIDER_H + 6;

        this.presetRect = new PduRect(x, y, w, PduChip.HEIGHT);
        this.shortPresets = false;
        this.shortPresets = this.presetRowWidth() > w;
        int cx = x;
        for (int i = 0; i < LimiterLadder.PRESET_NOTCHES.length; i++) {
            presetX[i] = cx;
            cx += PduChip.width(font, presetLabel(i)) + 3;
        }
        presetX[LimiterLadder.PRESET_NOTCHES.length] = cx;
        y += PduChip.HEIGHT + 12;

        this.modeRect = new PduRect(x, y, modeSeg.width(font), 13);

        y += modeRect.h() + 15;

        final int traceBottom = panel.bottom() - FOOTER_H - 2;
        this.traceRect = new PduRect(x, y, w, Math.max(MIN_TRACE_H, traceBottom - y));

        final int resetW = PduChip.width(font, "Reset");
        this.resetRect = new PduRect(panel.right() - inner - resetW,
                panel.bottom() - FOOTER_H + 2, resetW, PduChip.HEIGHT);
    }

    @Override
    public void tick() {
        super.tick();
        if (minecraft == null || minecraft.level == null || limiter.isRemoved()
                || minecraft.level.getBlockEntity(limiter.getBlockPos()) != limiter) {
            onClose();
            return;
        }
        if (++traceCounter % TRACE_INTERVAL == 0) {
            trace.push(limiter.getRateFePerTick());
        }

        if (pendingNotch >= 0 && (limiter.notch() == pendingNotch || ++pendingAge > 40)) {
            pendingNotch = -1;
            pendingAge = 0;
        }
        if (pendingMode != null && (limiter.mode() == pendingMode || ++pendingModeAge > 40)) {
            pendingMode = null;
            pendingModeAge = 0;
            modeSeg.setSelected(limiter.mode().ordinal());
        }
    }

    private int notch() {
        return pendingNotch >= 0 ? pendingNotch : limiter.notch();
    }

    private LimiterMode mode() {
        return pendingMode != null ? pendingMode : limiter.mode();
    }

    private int shownCap() {
        return mode().apply(LimiterLadder.feAt(notch()), limiter.redstoneStrength());
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);

        g.fill(0, 0, width, height, 0xB8000000);
        g.fill(panel.x(), panel.y(), panel.right(), panel.bottom(), PduTheme.BG_DEEP);
        PduTheme.outline(g, panel.x(), panel.y(), panel.w(), panel.h(), PduTheme.STROKE);

        final PowerLimiterBE.Status state = limiter.status();
        final int accent = accentFor(state);

        drawHeader(g, state, accent);
        drawHero(g, state, accent);
        drawSlider(g, mouseX, mouseY);
        drawPresets(g, mouseX, mouseY);
        drawMode(g, mouseX, mouseY);
        drawTrace(g);
        drawFooter(g, mouseX, mouseY);
    }

    private static int accentFor(PowerLimiterBE.Status state) {
        return switch (state) {
            case PASSING -> PduTheme.OK;
            case LIMITING -> PduTheme.WARN;
            case IDLE -> PduTheme.TEXT_DIM;
            default -> PduTheme.CRIT;
        };
    }

    private static String stateLabel(PowerLimiterBE.Status state) {
        return switch (state) {
            case PASSING -> "PASSING";
            case LIMITING -> "LIMITING";
            case IDLE -> "IDLE";
            case SHUT -> "SHUT";
            case LOOPED -> "LOOPED";
            case NO_SUPPLY -> "NO SUPPLY";
            case NO_LOAD -> "NO LOAD";
        };
    }

    private static boolean isFault(PowerLimiterBE.Status state) {
        return state == PowerLimiterBE.Status.LOOPED || state == PowerLimiterBE.Status.NO_SUPPLY
                || state == PowerLimiterBE.Status.NO_LOAD;
    }

    private void drawHeader(GuiGraphics g, PowerLimiterBE.Status state, int accent) {
        final int x = panel.x() + PduTheme.BASE;
        PduCard.drawHeading(g, font, "Power Limiter", x, panel.y() + 6);

        final String label = stateLabel(state);
        final int chipW = PduChip.width(font, label);
        PduChip.draw(g, font, panel.right() - PduTheme.BASE - chipW, panel.y() + 5, label, accent,
                false, false);

        final Component supply = limiter.getSupplyName();
        final Component load = limiter.getLoadName();
        final String subtitle = supply == null && load == null
                ? "Supply into the brass terminal, load off the gland end"
                : name(supply) + "  →  " + name(load);
        g.drawString(font, trim(subtitle, panel.w() - 2 * PduTheme.BASE), x, panel.y() + 18,
                isFault(state) ? PduTheme.WARN : PduTheme.TEXT_DIM, false);
        g.fill(panel.x() + 1, panel.y() + HEADER_H - 2, panel.right() - 1, panel.y() + HEADER_H - 1,
                PduTheme.STROKE);
    }

    private String name(Component c) {
        return c == null ? "nothing" : c.getString();
    }

    private void drawHero(GuiGraphics g, PowerLimiterBE.Status state, int accent) {
        final int x = panel.x() + PduTheme.BASE;
        final int y = panel.y() + HEADER_H + 3;

        if (isFault(state)) {
            g.drawString(font, stateLabel(state), x, y + 4, PduTheme.CRIT, false);
            int line = y + 16;
            for (final FormattedCharSequence row
                    : font.split(Component.literal(faultAdvice(state)), panel.w() - 2 * PduTheme.BASE)) {
                g.drawString(font, row, x, line, PduTheme.TEXT_DIM, false);
                line += 10;
            }
            return;
        }

        final int rate = limiter.getRateFePerTick();
        final int cap = shownCap();

        final String reading = LimiterLadder.compact(rate);
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(2.0f, 2.0f, 1.0f);
        g.drawString(font, reading, 0, 0, accent, false);
        g.pose().popPose();
        final String of = "of " + String.format(Locale.ROOT, "%,d", cap) + " FE/t";
        g.drawString(font, of, x + font.width(reading) * 2 + 6, y + 9, PduTheme.ACCENT, false);

        final float load = cap <= 0 ? 0.0f : Math.min(1.0f, rate / (float) cap);
        final int barY = y + 23;
        final int barW = panel.w() - 2 * PduTheme.BASE;
        g.fill(x, barY, x + barW, barY + 6, PduTheme.BG_PANEL);
        g.fill(x, barY, x + Math.round(barW * load), barY + 6, PduTheme.withAlpha(accent, 0xCC));
        PduTheme.outline(g, x, barY, barW, 6, PduTheme.STROKE);
    }

    private String faultAdvice(PowerLimiterBE.Status state) {
        return switch (state) {
            case LOOPED -> "One circuit reaches both terminals, so nothing crossing it would be limited "
                    + "and the same FE would go round for ever.";
            case NO_SUPPLY -> "Nothing on the " + faceName(limiter.inputFace())
                    + " face can supply it. That end is the brass terminal.";
            default -> "Nothing on the " + faceName(limiter.outputFace())
                    + " face can take power. That end is the gland.";
        };
    }

    private void drawSlider(GuiGraphics g, int mouseX, int mouseY) {
        final int labelY = sliderRect.y() - 12;
        g.drawString(font, "Limit", panel.x() + PduTheme.BASE, labelY, PduTheme.TEXT_DIM, false);
        final String value = LimiterLadder.format(LimiterLadder.feAt(notch()));
        g.drawString(font, Component.literal(value).withStyle(ChatFormatting.BOLD),
                panel.right() - PduTheme.BASE - font.width(value), labelY, PduTheme.ACCENT, false);

        PduChip.draw(g, font, minusRect.x(), minusRect.y(), "-", PduTheme.TEXT_DIM,
                minusRect.contains(mouseX, mouseY), false);
        PduChip.draw(g, font, plusRect.x(), plusRect.y(), "+", PduTheme.TEXT_DIM,
                plusRect.contains(mouseX, mouseY), false);

        final int tx = sliderRect.x();
        final int tw = sliderRect.w();
        final int mid = sliderRect.y() + sliderRect.h() / 2;
        g.fill(tx, mid - 2, tx + tw, mid + 2, PduTheme.BG_PANEL);
        PduTheme.outline(g, tx, mid - 2, tw, 4, PduTheme.STROKE);
        for (int n = 0; n <= LimiterLadder.MAX_NOTCH; n += 6) {
            final int px = tx + Math.round((tw - 1) * LimiterLadder.fractionForNotch(n));
            g.fill(px, mid + 3, px + 1, mid + 6, PduTheme.STROKE_HOT);
        }

        final float fraction = LimiterLadder.fractionForNotch(notch());
        final int fillW = Math.round((tw - 1) * fraction);
        g.fill(tx + 1, mid - 1, tx + 1 + Math.max(0, fillW - 1), mid + 1, PduTheme.ACCENT);

        final int knobX = tx + fillW;
        final boolean hot = dragging || sliderRect.contains(mouseX, mouseY);
        g.fill(knobX - 2, mid - 5, knobX + 3, mid + 5,
                hot ? PduTheme.STROKE_HOT : PduTheme.BG_RAISED);
        PduTheme.outline(g, knobX - 2, mid - 5, 5, 10, PduTheme.ACCENT);
    }

    private void drawPresets(GuiGraphics g, int mouseX, int mouseY) {
        for (int i = 0; i < LimiterLadder.PRESET_NOTCHES.length; i++) {
            final boolean hovered = mouseX >= presetX[i] && mouseX < presetX[i + 1] - 3
                    && presetRect.contains(mouseX, mouseY);
            final boolean active = LimiterLadder.PRESET_NOTCHES[i] == notch();
            PduChip.draw(g, font, presetX[i], presetRect.y(), presetLabel(i),
                    active ? PduTheme.ACCENT : PduTheme.TEXT_DIM, hovered, false);
        }
    }

    private String presetLabel(int index) {
        final String[] labels = this.shortPresets ? PRESET_LABELS_SHORT : PRESET_LABELS;
        return index < labels.length ? labels[index]
                : LimiterLadder.compact(LimiterLadder.feAt(LimiterLadder.PRESET_NOTCHES[index]));
    }

    private int presetRowWidth() {
        int total = -3;
        for (int i = 0; i < LimiterLadder.PRESET_NOTCHES.length; i++) {
            total += PduChip.width(font, this.presetLabel(i)) + 3;
        }
        return total;
    }

    private void drawMode(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, "Redstone", panel.x() + PduTheme.BASE, modeRect.y() - 11,
                PduTheme.TEXT_DIM, false);
        final LimiterMode current = mode();
        if (current != LimiterMode.ALWAYS) {
            final String signal = "signal " + limiter.redstoneStrength() + " of 15";
            g.drawString(font, signal, panel.right() - PduTheme.BASE - font.width(signal),
                    modeRect.y() - 11, limiter.redstoneStrength() > 0 ? PduTheme.ACCENT
                            : PduTheme.TEXT_FAINT, false);
        }
        modeSeg.setSelected(current.ordinal());
        modeSeg.draw(g, font, modeRect.x(), modeRect.y(), modeRect.h(), mouseX, mouseY);
        g.drawString(font, trim(Component.translatable(current.descriptionKey()).getString(),
                        panel.w() - 2 * PduTheme.BASE),
                panel.x() + PduTheme.BASE, modeRect.bottom() + 3, PduTheme.TEXT_FAINT, false);
    }

    private void drawTrace(GuiGraphics g) {
        PduTheme.panel(g, traceRect.x(), traceRect.y(), traceRect.w(), traceRect.h());
        final int cap = shownCap();
        float scale = Math.max(1.0f, cap);
        scale = Math.max(scale, trace.max());

        final int inner = traceRect.h() - 2;
        if (cap > 0 && cap < scale) {
            final int capY = traceRect.bottom() - 1 - Math.round(inner * (cap / scale));
            for (int x = traceRect.x() + 2; x < traceRect.right() - 2; x += 4) {
                g.fill(x, capY, x + 2, capY + 1, PduTheme.withAlpha(PduTheme.ACCENT, 0xAA));
            }
        }

        final int n = trace.count();
        if (n == 0) {
            g.drawString(font, "collecting", traceRect.x() + 4, traceRect.centerY() - 4,
                    PduTheme.TEXT_FAINT, false);
            return;
        }
        final int columns = Math.min(n, traceRect.w() - 4);
        for (int c = 0; c < columns; c++) {
            final float value = trace.at(n - columns + c);
            if (value <= 0.0f) {
                continue;
            }
            final int h = Math.max(1, Math.round(inner * Mth.clamp(value / scale, 0.0f, 1.0f)));
            final int px = traceRect.x() + 2 + c;
            g.fill(px, traceRect.bottom() - 1 - h, px + 1, traceRect.bottom() - 1,
                    cap > 0 && value >= cap ? PduTheme.WARN : PduTheme.OK);
        }
    }

    private void drawFooter(GuiGraphics g, int mouseX, int mouseY) {
        final int y = panel.bottom() - FOOTER_H + 4;
        final int x = panel.x() + PduTheme.BASE;
        g.drawString(font, "Comparator " + limiter.getAnalogOutputSignal() + " of 15", x, y,
                PduTheme.WARN, false);
        final String hint = "arrows or scroll to fine tune";
        final int hintX = x + font.width("Comparator 15 of 15") + 12;
        if (hintX + font.width(hint) < resetRect.x() - 6) {
            g.drawString(font, hint, hintX, y, PduTheme.TEXT_FAINT, false);
        }
        PduChip.draw(g, font, resetRect.x(), resetRect.y(), "Reset", PduTheme.CRIT,
                resetRect.contains(mouseX, mouseY), false);
    }

    private static String faceName(Direction dir) {
        return dir.getName().toLowerCase(Locale.ROOT);
    }

    private String trim(String s, int maxW) {
        if (font.width(s) <= maxW) {
            return s;
        }
        int end = s.length();
        while (end > 0 && font.width(s.substring(0, end)) + font.width("...") > maxW) {
            end--;
        }
        return s.substring(0, end) + "...";
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (sliderRect.contains(mouseX, mouseY)) {
            dragging = true;
            setNotch(notchAt(mouseX));
            return true;
        }
        if (minusRect.contains(mouseX, mouseY)) {
            setNotch(notch() - 1);
            return true;
        }
        if (plusRect.contains(mouseX, mouseY)) {
            setNotch(notch() + 1);
            return true;
        }
        if (presetRect.contains(mouseX, mouseY)) {
            for (int i = 0; i < LimiterLadder.PRESET_NOTCHES.length; i++) {
                if (mouseX >= presetX[i] && mouseX < presetX[i + 1] - 3) {
                    setNotch(LimiterLadder.PRESET_NOTCHES[i]);
                    return true;
                }
            }
        }
        final int seg = modeSeg.segmentAt(mouseX, mouseY);
        if (seg >= 0) {
            setMode(LimiterMode.byOrdinal(seg));
            return true;
        }
        if (resetRect.contains(mouseX, mouseY)) {
            send(PowerLimiterConfigPacket.ACTION_RESET, 0);
            trace.clear();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (dragging) {
            setNotch(notchAt(mouseX));
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        dragging = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double dx, double dy) {
        if (dy != 0.0) {
            setNotch(notch() + (int) Math.signum(dy));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, dx, dy);
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        final int step = Screen.hasShiftDown() ? 6 : 1;
        if (key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_DOWN) {
            setNotch(notch() - step);
            return true;
        }
        if (key == GLFW.GLFW_KEY_RIGHT || key == GLFW.GLFW_KEY_UP) {
            setNotch(notch() + step);
            return true;
        }
        if (key == GLFW.GLFW_KEY_HOME) {
            setNotch(0);
            return true;
        }
        if (key == GLFW.GLFW_KEY_END) {
            setNotch(LimiterLadder.MAX_NOTCH);
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }

    private int notchAt(double mouseX) {
        final double t = (mouseX - sliderRect.x()) / Math.max(1, sliderRect.w() - 1);
        return LimiterLadder.notchForFraction(t);
    }

    private void setNotch(int next) {
        final int clamped = LimiterLadder.clamp(next);
        if (clamped == notch()) {
            return;
        }
        pendingNotch = clamped;
        pendingAge = 0;
        send(PowerLimiterConfigPacket.ACTION_SET_NOTCH, clamped);
    }

    private void setMode(LimiterMode next) {
        if (next == mode()) {
            return;
        }
        pendingMode = next;
        pendingModeAge = 0;
        modeSeg.setSelected(next.ordinal());
        send(PowerLimiterConfigPacket.ACTION_SET_MODE, next.ordinal());
    }

    private void send(int action, int value) {
        PacketDistributor.sendToServer(
                new PowerLimiterConfigPacket(limiter.getBlockPos(), action, value));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
