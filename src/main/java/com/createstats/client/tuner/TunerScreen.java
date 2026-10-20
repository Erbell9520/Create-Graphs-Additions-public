package com.createstats.client.tuner;

import com.createstats.network.TunerApplyPacket;
import com.createstats.tuner.TunerBand;
import com.createstats.tuner.TunerDial;
import com.createstats.tuner.TunerHost;
import com.createstats.tuner.TunerRange;
import com.createstats.tuner.TunerStation;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class TunerScreen extends Screen {
    private static final int OUTLINE = 0xFF000000;
    private static final int HIGHLIGHT = 0xFFFFFFFF;
    private static final int FACE = 0xFFC6C6C6;
    private static final int FACE_HOT = 0xFFD8D8D8;
    private static final int RECESSED = 0xFF8B8B8B;
    private static final int SLOT_DARK = 0xFF373737;
    private static final int SHADOW = 0xFF555555;
    private static final int LABEL = 0xFF404040;
    private static final int WARN = 0xFFAA0000;
    private static final int NEEDLE = 0xFFD0301C;

    static final int CHIP_W = 22;
    static final int CHIP_H = 24;

    static final int CHIP_PITCH = 25;

    static final int RING_STEP = 26;
    static final int HUB_W = 120;
    static final int HUB_H = 48;

    private static final int PICK_HUB = -1;
    private static final int PICK_OFF = -2;
    private static final int PICK_NONE = -3;

    private final BlockPos pos;
    private final String originalStation;
    private final int originalRange;
    private String picked;
    private int range;
    private boolean done;

    int cx;
    int cy;
    int rx;
    int ry;

    private List<TunerStation> visible = List.of();
    private TunerDial.Chip[] chips = new TunerDial.Chip[0];
    @Nullable
    private List<TunerStation> laidOutFrom;
    private int laidOutRange = Integer.MIN_VALUE;
    private String laidOutPick = "";
    private int hovered = PICK_NONE;

    public TunerScreen(BlockPos pos, TunerHost host) {
        super(Component.translatable("create_stats.tuner.title"));
        this.pos = pos.immutable();
        this.originalStation = host.tunedStation();
        this.originalRange = host.tunerRange().clamp(host.tunedRange());
        this.picked = originalStation;
        this.range = originalRange;
    }

    @Override
    protected void init() {
        cx = width / 2;
        cy = height / 2 - 6;
        ry = Math.max(56, Math.min(104, height / 2 - 42));
        rx = Math.max(ry, Math.min((int) (ry * 1.6), width / 2 - 34));
        laidOutFrom = null;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
    }

    @Nullable
    private TunerHost host() {
        final Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return null;
        }
        final BlockEntity be = mc.level.getBlockEntity(pos);
        return be instanceof TunerHost h ? h : null;
    }

    @Override
    public void tick() {
        final Minecraft mc = Minecraft.getInstance();
        final TunerHost host = host();
        if (mc.player == null || host == null
                || mc.player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 64.0) {
            onClose();
            return;
        }
        TunerSurveys.request(pos);

        if (!useHeld(mc)) {
            commit();
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        final TunerHost host = host();
        if (scrollY == 0.0 || host == null) {
            return false;
        }
        stepRange(host.tunerRange(), scrollY > 0 ? 1 : -1, false);
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        final TunerHost host = host();
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && host != null) {
            final int at = pick((int) mouseX, (int) mouseY);
            if (at == PICK_HUB) {
                stepRange(host.tunerRange(), 1, true);
            } else {
                choose(at);
            }
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
        if (done) {
            TunerPreviews.release(this);
        } else {
            TunerPreviews.release(this, TunerSurveys.find(pos, originalStation), originalRange);
        }
        TunerHold.ringClosed();
        super.removed();
    }

    private void stepRange(TunerRange r, int direction, boolean cycle) {
        final int next = cycle ? r.cycle(range) : r.step(range, direction);
        if (next == range) {
            TunerSounds.stop();
            return;
        }
        range = next;
        TunerSounds.detent(r.notchOf(range), r.notches());
    }

    private void choose(int at) {
        if (at == PICK_OFF) {
            if (!picked.isEmpty()) {
                picked = "";
                TunerSounds.stop();
            }
            return;
        }
        if (at < 0 || at >= visible.size()) {
            return;
        }
        final TunerStation s = visible.get(at);
        if (!s.id().equals(picked)) {
            picked = s.id();
            TunerSounds.sweep(fractionOf(s, at));
        }
    }

    private void commit() {
        if (done) {
            return;
        }
        done = true;
        if (!picked.equals(originalStation) || range != originalRange) {
            PacketDistributor.sendToServer(new TunerApplyPacket(pos, picked, range));
        }
        onClose();
    }

    private void layout(TunerHost host) {
        final List<TunerStation> survey = TunerSurveys.of(pos);
        if (survey == laidOutFrom && range == laidOutRange && picked.equals(laidOutPick)) {
            return;
        }
        laidOutFrom = survey;
        laidOutRange = range;
        laidOutPick = picked;
        final List<TunerStation> next = new ArrayList<>();
        if (survey != null) {
            for (TunerStation s : survey) {
                if (s.shownAt(range, originalStation) || s.id().equals(picked)) {
                    next.add(s);
                }
            }
        }
        final TunerBand band = host.dialBand();
        if (band != null) {
            next.sort(Comparator.comparingInt(TunerStation::dial));
        }
        visible = List.copyOf(next);
        final double[] fractions = new double[visible.size()];
        for (int i = 0; i < fractions.length; i++) {
            fractions[i] = fractionOf(visible.get(i), i);
        }
        final double gapOuter = CHIP_PITCH / (double) ry;
        final double gapInner = CHIP_PITCH / (double) Math.max(20, ry - RING_STEP);
        chips = TunerDial.place(fractions, gapOuter, gapInner);
    }

    private double fractionOf(TunerStation s, int index) {
        final TunerHost host = host();
        final TunerBand band = host == null ? null : host.dialBand();
        if (band != null) {
            return band.fraction(s.dial());
        }
        return visible.size() <= 1 ? 0.5 : index / (double) (visible.size() - 1);
    }

    int chipX(int i) {
        final int r = chips[i].ring() == 0 ? rx : rx - RING_STEP;
        return (int) Math.round(cx + Math.cos(chips[i].angle()) * r);
    }

    int chipY(int i) {
        final int r = chips[i].ring() == 0 ? ry : ry - RING_STEP;
        return (int) Math.round(cy + Math.sin(chips[i].angle()) * r);
    }

    int offX() {
        return cx;
    }

    int offY() {
        return cy + ry;
    }

    private boolean anyInner() {
        for (TunerDial.Chip c : chips) {
            if (c.ring() == 1) {
                return true;
            }
        }
        return false;
    }

    private int pick(int mx, int my) {
        if (Math.abs(mx - cx) <= HUB_W / 2 && Math.abs(my - cy) <= HUB_H / 2) {
            return PICK_HUB;
        }
        if (Math.abs(mx - offX()) <= CHIP_W / 2 && Math.abs(my - offY()) <= CHIP_H / 2) {
            return PICK_OFF;
        }
        for (int i = 0; i < chips.length; i++) {
            if (Math.abs(mx - chipX(i)) <= CHIP_W / 2 && Math.abs(my - chipY(i)) <= CHIP_H / 2) {
                return i;
            }
        }
        final double nx = (mx - cx) / (double) rx;
        final double ny = (my - cy) / (double) ry;
        final double d = Math.hypot(nx, ny);
        if (d < 0.45) {
            return PICK_NONE;
        }
        final double at = Math.atan2(ny, nx);

        final double bottom = Math.abs(TunerDial.wrapAngle(at - Math.PI / 2));
        if (Math.min(bottom, Math.PI * 2 - bottom) < (Math.PI * 2 - TunerDial.SWEEP) / 2 - 0.04 && d > 0.6) {
            return PICK_OFF;
        }
        final double innerD = (ry - RING_STEP) / (double) ry;
        final int ring = anyInner() && d < (1.0 + innerD) / 2 ? 1 : 0;
        final int i = TunerDial.pick(chips, ring, at, CHIP_PITCH * 1.5 / ry);
        return i >= 0 ? i : PICK_NONE;
    }

    // ---------- Drawing ----------

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        final TunerHost host = host();
        if (host == null) {
            return;
        }
        layout(host);
        hovered = pick(mouseX, mouseY);
        if (hovered >= 0 || hovered == PICK_OFF) {
            choose(hovered);
            layout(host);
        }
        final TunerStation station = pickedStation();
        TunerPreviews.show(this, pos, station, range, originalRange);

        drawTrack(g);
        final TunerBand band = host.dialBand();
        if (band != null) {
            drawScale(g, band);
        }
        int pickedIndex = -1;
        for (int i = 0; i < visible.size(); i++) {
            if (visible.get(i).id().equals(picked)) {
                pickedIndex = i;
            }
        }
        if (pickedIndex >= 0) {
            needle(g, chipX(pickedIndex), chipY(pickedIndex));
        } else if (picked.isEmpty()) {
            needle(g, offX(), offY());
        }
        for (int i = 0; i < visible.size(); i++) {
            final TunerStation s = visible.get(i);
            drawChip(g, chipX(i), chipY(i), s.icon(), s.colour(), s.id().equals(picked), hovered == i);
        }
        drawOff(g, picked.isEmpty(), hovered == PICK_OFF);
        drawHub(g, host, station, hovered == PICK_HUB);

        final Component hint1 = Component.translatable("create_stats.tuner.hint_pick");
        final Component hint2 = Component.translatable("create_stats.tuner.hint_apply");
        g.drawCenteredString(font, hint1, cx, height - 22, HIGHLIGHT);
        g.drawCenteredString(font, hint2, cx, height - 12, 0xFFAAAAAA);
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Nullable
    private TunerStation pickedStation() {
        for (TunerStation s : visible) {
            if (s.id().equals(picked)) {
                return s;
            }
        }
        return TunerSurveys.find(pos, picked);
    }

    private void drawTrack(GuiGraphics g) {
        final double step = 4.0 / rx;
        for (double a = TunerDial.START; a <= TunerDial.START + TunerDial.SWEEP + 1e-9; a += step) {
            dot(g, (int) Math.round(cx + Math.cos(a) * rx), (int) Math.round(cy + Math.sin(a) * ry), HIGHLIGHT);
        }
    }

    private void drawScale(GuiGraphics g, TunerBand band) {
        for (int v : band.ticks(band.minor())) {
            final boolean major = v % band.major() == 0;
            final double a = TunerDial.angleOf(band.fraction(v));
            final int from = 15;
            final int to = major ? 19 : 16;
            for (int k = from; k <= to; k++) {
                dot(g, (int) Math.round(cx + Math.cos(a) * (rx + k)), (int) Math.round(cy + Math.sin(a) * (ry + k)),
                        major ? HIGHLIGHT : 0xFFBBBBBB);
            }
            if (major) {
                final String label = band.label(v);
                final int lx = (int) Math.round(cx + Math.cos(a) * (rx + 27));
                final int ly = (int) Math.round(cy + Math.sin(a) * (ry + 27));
                g.drawString(font, label, lx - font.width(label) / 2, ly - 4, HIGHLIGHT, true);
            }
        }
        final double end = TunerDial.START + TunerDial.SWEEP;
        final int ux = (int) Math.round(cx + Math.cos(end) * (rx + 27));
        final int uy = (int) Math.round(cy + Math.sin(end) * (ry + 27));
        g.drawString(font, band.unit(), ux - font.width(band.unit()) / 2, uy - 4, 0xFFBBBBBB, true);
    }

    private void needle(GuiGraphics g, int tx, int ty) {
        final double dx = tx - cx;
        final double dy = ty - cy;
        final double len = Math.hypot(dx, dy);
        if (len < 1.0) {
            return;
        }

        final double tHub = Math.min(dx == 0 ? Double.MAX_VALUE : (HUB_W / 2.0 + 2) / Math.abs(dx),
                dy == 0 ? Double.MAX_VALUE : (HUB_H / 2.0 + 2) / Math.abs(dy));
        final double t0 = Math.min(1.0, tHub);
        final double t1 = Math.max(t0, 1.0 - 13.0 / len);
        final int steps = (int) Math.ceil((t1 - t0) * len);
        for (int i = 0; i <= steps; i++) {
            final double t = t0 + (t1 - t0) * i / Math.max(1, steps);
            final int x = (int) Math.round(cx + dx * t);
            final int y = (int) Math.round(cy + dy * t);
            g.fill(x + 1, y + 1, x + 2, y + 2, OUTLINE);
            g.fill(x, y, x + 1, y + 1, NEEDLE);
        }
    }

    private void drawChip(GuiGraphics g, int x, int y, String icon, int colour, boolean on, boolean hot) {
        final int x0 = x - CHIP_W / 2;
        final int y0 = y - CHIP_H / 2;
        bevel(g, x0, y0, CHIP_W, CHIP_H, on);
        slot(g, x0 + 2, y0 + 2);
        TunerIcons.draw(g, icon, x0 + 3, y0 + 3, 16);
        if (hot) {
            g.fill(x0 + 3, y0 + 3, x0 + 19, y0 + 19, 0x80FFFFFF);
        }
        g.fill(x0 + 2, y0 + 20, x0 + 20, y0 + 22, 0xFF000000 | (colour & 0xFFFFFF));
    }

    private void drawOff(GuiGraphics g, boolean on, boolean hot) {
        final int x0 = offX() - CHIP_W / 2;
        final int y0 = offY() - CHIP_H / 2;
        bevel(g, x0, y0, CHIP_W, CHIP_H, on);
        slot(g, x0 + 2, y0 + 2);
        final String off = font.plainSubstrByWidth(Component.translatable("create_stats.tuner.off").getString(), 18);
        g.drawString(font, off, x0 + 11 - font.width(off) / 2, y0 + 7, on ? LABEL : 0xFF303030, false);
        if (hot) {
            g.fill(x0 + 3, y0 + 3, x0 + 19, y0 + 19, 0x80FFFFFF);
        }
        g.fill(x0 + 2, y0 + 20, x0 + 20, y0 + 22, SHADOW);
    }

    private void drawHub(GuiGraphics g, TunerHost host, @Nullable TunerStation station, boolean hot) {
        final int x = cx - HUB_W / 2;
        final int y = cy - HUB_H / 2;
        bevel(g, x, y, HUB_W, HUB_H, hot);
        slot(g, x + 4, y + 4);
        final List<TunerStation> survey = TunerSurveys.of(pos);
        final Component name;
        final Component sub;
        if (station != null) {
            TunerIcons.draw(g, station.icon(), x + 5, y + 5, 16);
            name = Component.translatable(station.labelKey());
            sub = host.stationLine(station, range, true);
        } else if (!picked.isEmpty()) {
            name = Component.literal(picked);
            sub = Component.translatable("create_stats.tuner.listening");
        } else {
            name = Component.translatable("create_stats.tuner.no_station");
            sub = Component.translatable("create_stats.tuner.draws_nothing");
        }
        final int textW = HUB_W - 30;
        g.drawString(font, font.plainSubstrByWidth(name.getString(), textW), x + 26, y + 5, LABEL, false);
        g.drawString(font, font.plainSubstrByWidth(sub.getString(), textW), x + 26, y + 15, SHADOW, false);

        final TunerRange r = host.tunerRange();
        // untuned draws 0, so the cost says 0

        final boolean untuned = station == null && picked.isEmpty();
        final String cost = host.costLabel(range, untuned).getString();
        final String what = font.plainSubstrByWidth(host.rangeLabel(station, range).getString(),
                HUB_W - 12 - font.width(cost));
        g.drawString(font, what, x + 5, y + 26, LABEL, false);
        g.drawString(font, cost, x + HUB_W - 5 - font.width(cost), y + 26, LABEL, false);

        final Component note = note(host, station, survey);
        if (note != null) {
            g.drawString(font, font.plainSubstrByWidth(note.getString(), HUB_W - 10), x + 5, y + 37, WARN, false);
        } else {
            notches(g, x + 5, y + 38, r);
        }
    }

    @Nullable
    private Component note(TunerHost host, @Nullable TunerStation station, @Nullable List<TunerStation> survey) {
        if (survey == null) {
            return Component.translatable("create_stats.tuner.listening");
        }
        if (survey.isEmpty() || (survey.size() == 1 && survey.get(0).minRange() <= 0)) {
            return Component.translatable("create_stats.tuner.nothing_spawns", host.tunerRange().max());
        }
        if (station != null && station.minRange() <= 0) {
            return Component.translatable("create_stats.tuner.not_found");
        }
        if (station != null && station.minRange() > range) {
            return Component.translatable("create_stats.tuner.out_of_reach", station.minRange());
        }
        return host.tunerNote(station, range);
    }

    private void notches(GuiGraphics g, int x, int y, TunerRange r) {
        final int n = r.notches();
        final int pitch = Math.max(3, Math.min(7, (HUB_W - 10) / Math.max(1, n)));
        final int on = r.notchOf(range);
        for (int i = 0; i < n; i++) {
            final int px = x + i * pitch;
            final boolean lit = i <= on;
            g.fill(px, y, px + pitch - 1, y + 4, i == on ? OUTLINE : SHADOW);
            g.fill(px + 1, y + 1, px + pitch - 2, y + 3, lit ? HIGHLIGHT : RECESSED);
        }
    }

    private static void bevel(GuiGraphics g, int x, int y, int w, int h, boolean hot) {
        g.fill(x, y, x + w, y + h, hot ? HIGHLIGHT : OUTLINE);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, hot ? FACE_HOT : FACE);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, HIGHLIGHT);
        g.fill(x + 1, y + 1, x + 2, y + h - 1, HIGHLIGHT);
        g.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, SHADOW);
        g.fill(x + w - 2, y + 1, x + w - 1, y + h - 1, SHADOW);
    }

    private static void slot(GuiGraphics g, int x, int y) {
        g.fill(x, y, x + 18, y + 18, SLOT_DARK);
        g.fill(x + 1, y + 1, x + 18, y + 18, HIGHLIGHT);
        g.fill(x + 1, y + 1, x + 17, y + 17, RECESSED);
    }

    private static void dot(GuiGraphics g, int x, int y, int colour) {
        g.fill(x + 1, y + 1, x + 2, y + 2, OUTLINE);
        g.fill(x, y, x + 1, y + 1, colour);
    }

    private static boolean useHeld(Minecraft mc) {
        final InputConstants.Key key = mc.options.keyUse.getKey();
        final long window = mc.getWindow().getWindow();
        if (key.getType() == InputConstants.Type.MOUSE) {
            return GLFW.glfwGetMouseButton(window, key.getValue()) == GLFW.GLFW_PRESS;
        }
        return InputConstants.isKeyDown(window, key.getValue());
    }
}
