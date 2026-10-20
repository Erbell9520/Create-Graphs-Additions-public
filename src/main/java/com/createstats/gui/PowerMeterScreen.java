package com.createstats.gui;

import com.createstats.blockentity.PowerMeterBE;
import com.createstats.book.specimen.ManualSpecimen;
import com.createstats.book.specimen.SpecimenAnatomy;
import com.createstats.book.specimen.Specimens;
import com.createstats.gui.meter.MeterHistory;
import com.createstats.gui.meter.plate.GlassFont;
import com.createstats.gui.meter.plate.HoldGesture;
import com.createstats.gui.meter.plate.MeterText;
import com.createstats.gui.meter.plate.PlateGeometry;
import com.createstats.gui.meter.plate.PlateGeometry.Rect;
import com.createstats.gui.meter.plate.PlateHotspots;
import com.createstats.gui.meter.plate.PlateInk;
import com.createstats.gui.meter.plate.PlateInk.Ink;
import com.createstats.gui.meter.plate.RatingPlate;
import com.createstats.gui.meter.plate.RatingPlate.Sprite;
import com.createstats.gui.meter.plate.TraceMath;
import com.createstats.network.PowerMeterActionPacket;
import com.createstats.network.PowerMeterHistoryRequestPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class PowerMeterScreen extends Screen implements ManualSpecimen {
    private static final int SCRIM = 0xB8000000;
    private static final String[] SPAN = {"1m", "5m", "15m"};

    private final PowerMeterBE meter;
    private final MeterHistory history = new MeterHistory();
    private final HoldGesture hold = new HoldGesture();

    private PlateGeometry geo = PlateGeometry.of(853, 533);
    private RatingPlate plate;

    private int range = MeterHistory.RANGE_1M;
    private boolean subscribed;
    private int keepaliveCounter;
    private int ticks;

    private float flow;
    private float flowSpeed;

    private int flash;

    private boolean sealed = true;

    private ItemStack supplyStack = ItemStack.EMPTY;
    private ItemStack loadStack = ItemStack.EMPTY;

    private boolean specimen;

    public PowerMeterScreen(PowerMeterBE meter) {
        super(Component.translatable("block.create_stats.power_meter"));
        this.meter = meter;
    }

    public static PowerMeterScreen specimen(PowerMeterBE fixture, ItemStack supply, ItemStack load, int[] trace) {
        final PowerMeterScreen s = new PowerMeterScreen(fixture);
        s.specimen = true;
        s.minecraft = Minecraft.getInstance();
        s.font = s.minecraft.font;
        s.width = PlateGeometry.MAX_W + 12;
        s.height = PlateGeometry.MAX_H + 12;
        s.layout();
        s.supplyStack = supply;
        s.loadStack = load;
        s.history.replace(trace, trace.length);
        return s;
    }

    @Override
    protected void init() {
        layout();
        refreshNeighbours();
        if (!subscribed) {
            subscribe();
        }
    }

    private void layout() {
        this.geo = PlateGeometry.of(width, height);
        final List<Rect> windows = new ArrayList<>();
        windows.add(geo.rate);
        windows.add(geo.slotIn);
        windows.add(geo.slotOut);
        windows.add(geo.trace);
        windows.add(geo.peak.window());
        windows.add(geo.carried.window());
        windows.add(geo.load.window());
        this.plate = new RatingPlate(font, geo, windows);
        GlassFont.ensure();
    }

    @Override
    public int specimenWidth() {
        return geo.w;
    }

    @Override
    public int specimenHeight() {
        return geo.h;
    }

    @Override
    public void renderSpecimen(GuiGraphics g, int mouseX, int mouseY) {
        drawPlate(g, mouseX, mouseY, 0.0f);
    }

    @Override
    public SpecimenAnatomy anatomy() {
        return Specimens.powerMeter(geo);
    }

    @Override
    public void tick() {
        super.tick();
        if (specimen) {
            return;
        }
        if (minecraft == null || minecraft.level == null || meter.isRemoved()
                || minecraft.level.getBlockEntity(meter.getBlockPos()) != meter) {
            onClose();
            return;
        }
        ticks++;
        if (subscribed && ++keepaliveCounter % 40 == 0) {
            send(PowerMeterHistoryRequestPacket.ACTION_KEEPALIVE);
        }
        if (ticks % 10 == 0) {
            refreshNeighbours();
        }

        final int peak = meter.getPeakFePerTick();
        final float ratio = meter.status() == PowerMeterBE.Status.FLOWING && peak > 0
                ? Math.min(1.0f, meter.getRateFePerTick() / (float) peak) : 0.0f;
        this.flowSpeed = ratio > 0 ? 0.35f + 2.3f * ratio : 0.0f;
        this.flow = (flow + flowSpeed) % 8.0f;
        if (hold.tick()) {
            resetMeter();
        }
        if (flash > 0) {
            flash--;
        }
    }

    private void refreshNeighbours() {
        this.supplyStack = neighbour(meter.inputFace(), meter.getSupplyName() != null);
        this.loadStack = neighbour(meter.outputFace(), meter.getLoadName() != null);
    }

    private ItemStack neighbour(Direction face, boolean present) {
        if (!present || minecraft == null || minecraft.level == null) {
            return ItemStack.EMPTY;
        }
        final Item item = minecraft.level.getBlockState(meter.getBlockPos().relative(face)).getBlock().asItem();
        return item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.fill(0, 0, width, height, SCRIM);
        final int mx = mouseX - geo.x;
        final int my = mouseY - geo.y;

        g.pose().pushPose();
        g.pose().translate(geo.x, geo.y, 0.0f);
        drawPlate(g, mx, my, partialTick);
        g.pose().popPose();

        final List<Component> tip = tooltipAt(mx, my);
        if (tip != null) {
            g.renderComponentTooltip(font, tip, mouseX, mouseY);
        }
    }

    private void drawPlate(GuiGraphics g, int mx, int my, float partialTick) {
        plate.plate(g);
        drawHeader(g);
        drawWiring(g, partialTick);
        drawRate(g);
        drawTrace(g, mx, my);
        drawKeys(g, mx, my);
        drawFoot(g, partialTick);

        if (!supplyStack.isEmpty()) {
            g.renderItem(supplyStack, 7, PlateGeometry.ICON_Y);
        }
        if (!loadStack.isEmpty()) {
            g.renderItem(loadStack, geo.w - 23, PlateGeometry.ICON_Y);
        }
    }

    private PowerMeterBE.Status status() {
        return meter.status();
    }

    private boolean isFault() {
        final PowerMeterBE.Status s = status();
        return s == PowerMeterBE.Status.LOOPED || s == PowerMeterBE.Status.NO_SUPPLY
                || s == PowerMeterBE.Status.NO_LOAD;
    }

    private static Ink inkFor(PowerMeterBE.Status s) {
        return switch (s) {
            case FLOWING -> PlateInk.GREEN;
            case BACKED_UP -> PlateInk.AMBER;
            case IDLE -> PlateInk.IDLE;
            default -> PlateInk.RED;
        };
    }

    private static String stateWord(PowerMeterBE.Status s) {
        return Component.translatable(switch (s) {
            case FLOWING -> "gui.create_stats.power_meter.state.flowing";
            case BACKED_UP -> "gui.create_stats.power_meter.state.backed_up";
            case IDLE -> "gui.create_stats.power_meter.state.idle";
            case LOOPED -> "gui.create_stats.power_meter.state.looped";
            case NO_SUPPLY -> "gui.create_stats.power_meter.state.no_supply";
            case NO_LOAD -> "gui.create_stats.power_meter.state.no_load";
        }).getString();
    }

    private void drawHeader(GuiGraphics g) {
        plate.engrave(g, "POWER METER", 15, PlateGeometry.TITLE_Y);

        final BlockPos p = meter.getBlockPos();
        plate.stampRight(g, "X" + p.getX() + " Y" + p.getY() + " Z" + p.getZ(), geo.w - 16, PlateGeometry.TITLE_Y);
    }

    private void drawWiring(GuiGraphics g, float pt) {
        final int half = (geo.w >> 1) - 6;

        final Component supply = meter.getSupplyName();
        final Component load = meter.getLoadName();
        plate.engrave(g, trim(supply == null ? "nothing" : supply.getString(), half - 27), 27,
                PlateGeometry.NAME_Y, supply == null ? PlateInk.ENGRAVE_SOFT : PlateInk.ENGRAVE);
        plate.engraveRight(g, trim(load == null ? "nothing" : load.getString(), half - 27), geo.w - 27,
                PlateGeometry.NAME_Y, load == null ? PlateInk.ENGRAVE_SOFT : PlateInk.ENGRAVE);
        if (supplyStack.isEmpty()) {
            plate.socket(g, 7, PlateGeometry.ICON_Y);
        }
        if (loadStack.isEmpty()) {
            plate.socket(g, geo.w - 23, PlateGeometry.ICON_Y);
        }

        plate.window(g, geo.slotIn, false);
        plate.window(g, geo.slotOut, false);
        drawFlow(g, pt);
        plate.arrow(g, (geo.slotIn.x() + geo.slotIn.right()) / 2 - 7, geo.lineY - 11);
        plate.arrow(g, (geo.slotOut.x() + geo.slotOut.right()) / 2 - 7, geo.lineY - 11);

        plate.sprite(g, Sprite.TERMINAL, geo.terminal.x(), geo.terminal.y());
        plate.sprite(g, Sprite.GLAND, geo.gland.x(), geo.gland.y());
        drawJewels(g);

        plate.engrave(g, "IN " + meter.inputFace().getName().toUpperCase(Locale.ROOT), 8, geo.faceY);
        plate.engraveRight(g, meter.outputFace().getName().toUpperCase(Locale.ROOT) + " OUT", geo.w - 8,
                geo.faceY, PlateInk.ENGRAVE);
    }

    private void drawJewels(GuiGraphics g) {
        final PowerMeterBE.Status s = status();
        final boolean blink = (ticks / 10) % 2 == 0;
        Sprite in = Sprite.JEWEL_GREEN;
        Sprite out = Sprite.JEWEL_GREEN;
        if (s == PowerMeterBE.Status.NO_SUPPLY) {
            in = Sprite.JEWEL_RED;
        }
        if (s == PowerMeterBE.Status.NO_LOAD) {
            out = Sprite.JEWEL_RED;
        }
        if (s == PowerMeterBE.Status.BACKED_UP) {
            out = Sprite.JEWEL_AMBER;
        }
        if (s == PowerMeterBE.Status.LOOPED) {
            in = blink ? Sprite.JEWEL_RED : Sprite.JEWEL_OFF;
            out = blink ? Sprite.JEWEL_OFF : Sprite.JEWEL_RED;
        }
        plate.sprite(g, in, geo.jewelIn.x(), geo.jewelIn.y());
        plate.sprite(g, out, geo.jewelOut.x(), geo.jewelOut.y());
    }

    private void drawFlow(GuiGraphics g, float pt) {
        final PowerMeterBE.Status s = status();
        final float phase = (flow + flowSpeed * pt) % 8.0f;
        g.drawManaged(() -> {
            for (final Rect slot : new Rect[]{geo.slotIn, geo.slotOut}) {
                final boolean in = slot == geo.slotIn;
                final int x0 = slot.x() + 2;
                final int x1 = slot.right() - 2;
                final int y = slot.y() + 2;
                if (s == PowerMeterBE.Status.FLOWING) {
                    for (int x = x0; x < x1; x++) {
                        final int k = (int) Math.floor(((x - x0 - phase) % 8 + 8) % 8);
                        if (k < 4) {
                            g.fill(x, y + 1, x + 1, y + 2, k == 3 ? PlateInk.GREEN.bright() : PlateInk.GREEN.main());
                            g.fill(x, y, x + 1, y + 1, PlateInk.GREEN.dim());
                            g.fill(x, y + 2, x + 1, y + 3, PlateInk.GREEN.dim());
                        } else {
                            g.fill(x, y + 1, x + 1, y + 2, PlateInk.GREEN.faint());
                        }
                    }
                } else if (s == PowerMeterBE.Status.BACKED_UP && in) {
                    g.fill(x0, y + 1, x1, y + 2, PlateInk.AMBER.mid());
                    g.fill(x0, y, x1, y + 1, PlateInk.AMBER.dim());
                    g.fill(x0, y + 2, x1, y + 3, PlateInk.AMBER.dim());
                } else {
                    g.fill(x0, y + 1, x1, y + 2, PlateInk.IDLE.faint());
                }
            }
        });
    }

    private void drawRate(GuiGraphics g) {
        final Rect r = geo.rate;
        plate.window(g, r, true);
        final PowerMeterBE.Status s = status();
        final Ink ink = inkFor(s);
        final int ix = r.x() + 2;
        final int iw = r.w() - 4;
        if (isFault()) {
            final String word = stateWord(s);
            GlassFont.glow(g, word, ix + (iw - GlassFont.width(word, 2)) / 2, r.y() + 9, ink, 2);
            return;
        }
        GlassFont.glow(g, stateWord(s), r.x() + 6, r.y() + 5, ink, 1);
        final int rate = meter.getRateFePerTick();
        final int[] buf = meter.getLoadBuffer();
        final String right;
        if (s == PowerMeterBE.Status.FLOWING) {
            right = MeterText.compact(rate * 20L) + " FE/s";
        } else if (buf[1] > 0 && buf[0] >= buf[1]) {
            right = "LOAD FULL";
        } else {
            right = s == PowerMeterBE.Status.BACKED_UP ? "LOAD NOT TAKING" : "NOTHING DRAWING";
        }
        GlassFont.draw(g, right, r.right() - 6 - GlassFont.width(right), r.y() + 5, ink.mid(), 1);

        String num = MeterText.group(rate);
        final int unitW = GlassFont.width("FE/t");
        if (GlassFont.width(num, 2) + 5 + unitW > iw - 12) {
            num = MeterText.compact(rate);
        }
        final int nw = GlassFont.width(num, 2);
        final int x = ix + (iw - nw - 5 - unitW) / 2;
        final Ink big = flash > 0 ? new Ink(ink.bright(), ink.bright(), ink.mid(), ink.dim(), ink.faint(), ink.halo()) : ink;
        GlassFont.glow(g, num, x, r.y() + 14, big, 2);
        GlassFont.draw(g, "FE/t", x + nw + 5, r.y() + 21, ink.mid(), 1);
    }

    private void drawTrace(GuiGraphics g, int mx, int my) {
        final Rect t = geo.trace;
        final Rect gl = t.glass();
        plate.window(g, t, true);
        final Ink ink = PlateInk.GREEN;
        final int bottom = gl.bottom() - 1;
        final int midY = gl.y() + gl.h() / 2;
        final int cols = gl.w();
        final int[] chrono = history.chronological();
        final int n = chrono.length;
        final boolean fault = isFault();
        final Ink li = fault ? PlateInk.IDLE : ink;
        final int peak = meter.getPeakFePerTick();
        final int[] colVal = TraceMath.columns(chrono, MeterHistory.SIZE, cols);
        final int axis = TraceMath.axisFor(colVal, peak);
        final int plotH = geo.plotHeight();

        g.drawManaged(() -> {
            for (int q = 1; q < 4; q++) {
                final int gx = gl.x() + Math.round(gl.w() * q / 4.0f);
                for (int y = gl.y() + 1; y < bottom; y += 2) {
                    g.fill(gx, y, gx + 1, y + 1, ink.faint());
                }
            }
            for (int x = gl.x() + 1; x < gl.right() - 1; x += 2) {
                g.fill(x, midY, x + 1, midY + 1, ink.faint());
            }
            if (n == 0) {
                return;
            }
            final int[] rows = new int[cols];
            for (int c = 0; c < cols; c++) {
                rows[c] = colVal[c] == TraceMath.EMPTY ? TraceMath.EMPTY
                        : TraceMath.rowOf(colVal[c], axis, bottom, plotH);
            }

            final int[][] env = TraceMath.envelope(rows, 3);
            for (int c = 0; c < cols; c++) {
                if (rows[c] == TraceMath.EMPTY) {
                    continue;
                }
                final int x = gl.x() + c;
                for (int yy = env[c][0]; yy <= env[c][1]; yy++) {
                    if (BAYER[yy & 3][x & 3] < 5) {
                        g.fill(x, yy, x + 1, yy + 1, li.faint());
                    }
                }
            }
            int prev = TraceMath.EMPTY;
            for (int c = 0; c < cols; c++) {
                final int y = rows[c];
                if (y == TraceMath.EMPTY) {
                    prev = TraceMath.EMPTY;
                    continue;
                }
                final int x = gl.x() + c;

                if (y + 1 <= bottom) {
                    g.fill(x, y + 1, x + 1, y + 2, li.dim());
                }
                // join only on gentle slopes, noisy load shouldn't turn into a comb

                if (prev != TraceMath.EMPTY && Math.abs(prev - y) > 1 && Math.abs(prev - y) <= 4) {
                    g.fill(x, Math.min(prev, y) + 1, x + 1, Math.max(prev, y), li.mid());
                }
                g.fill(x, y, x + 1, y + 1, li.main());
                prev = y;
            }

            if (!fault && rows[cols - 1] != TraceMath.EMPTY) {
                final int hx = gl.x() + cols - 1;
                final int hy = rows[cols - 1];
                for (int y = gl.y(); y < bottom; y += 3) {
                    g.fill(hx, y, hx + 1, y + 1, ink.dim());
                }
                g.fill(hx - 1, hy - 1, hx + 1, hy + 1, ink.bright());
            }

            if (!fault && peak > 0 && peak <= axis) {
                final int py = TraceMath.rowOf(peak, axis, bottom, plotH);
                for (int x = gl.x() + 1; x < gl.right() - 1; x++) {
                    if (((x - gl.x()) >> 1) % 2 == 0) {
                        g.fill(x, py, x + 1, py + 1, PlateInk.PEAK_MARK);
                    }
                }
            }
        });

        if (n == 0) {
            final String base = "GATHERING TRACE";
            final String dots = ".".repeat(1 + (ticks / 10) % 3);
            GlassFont.draw(g, base + dots, gl.x() + (gl.w() - GlassFont.width(base + "...")) / 2, midY - 3, ink.mid(), 1);
        } else {
            if (!fault && peak > 0 && peak <= axis) {
                final int py = TraceMath.rowOf(peak, axis, bottom, plotH);
                plate.label(g, "PEAK", gl.right() - 4 - GlassFont.width("PEAK"), py - 9, PlateInk.PEAK_MARK);
            }

            if (n < 20 && !fault) {
                plate.label(g, "GATHERING TRACE", gl.x() + (gl.w() - GlassFont.width("GATHERING TRACE")) / 2, midY - 3, ink.mid());
            }
            plate.label(g, MeterText.group(axis) + " FE/t", gl.x() + 3, gl.y() + 3, ink.mid());
        }
        plate.label(g, "-" + SPAN[range], gl.x() + 3, bottom - 8, ink.mid());
        plate.label(g, "now", gl.right() - 3 - GlassFont.width("now"), bottom - 8, ink.mid());

        if (fault) {
            drawFaultBand(g, gl);
            return;
        }

        if (n > 0 && gl.contains(mx, my)) {
            final int c = mx - gl.x();
            final int v = colVal[c];
            if (v != TraceMath.EMPTY) {
                g.drawManaged(() -> {
                    for (int y = gl.y(); y <= bottom; y++) {
                        g.fill(mx, y, mx + 1, y + 1, (y & 1) == 1 ? ink.bright() : ink.mid());
                    }
                });
                final int age = TraceMath.ageTicks(c, cols, MeterHistory.SIZE, MeterHistory.CADENCE[range]);
                final String l1 = v == 0 ? "idle" : MeterText.group(v) + " FE/t";
                final String l2 = age == 0 ? "now" : MeterText.duration(age) + " ago";
                final int bw = Math.max(GlassFont.width(l1), GlassFont.width(l2)) + 10;
                final int bx = mx + 4 + bw < gl.right() ? mx + 4 : mx - 4 - bw;
                final int by = gl.y() + 3;
                g.fill(bx, by, bx + bw, by + 23, PlateInk.GLASS_DEEP);
                g.fill(bx, by, bx + bw, by + 1, ink.dim());
                g.fill(bx, by + 22, bx + bw, by + 23, ink.dim());
                g.fill(bx, by, bx + 1, by + 23, ink.dim());
                g.fill(bx + bw - 1, by, bx + bw, by + 23, ink.dim());
                GlassFont.glow(g, l1, bx + 5, by + 4, ink, 1);
                GlassFont.draw(g, l2, bx + 5, by + 13, ink.mid(), 1);
            }
        }
    }

    private void drawFaultBand(GuiGraphics g, Rect gl) {
        final PowerMeterBE.Status s = status();
        final String advice = switch (s) {
            case LOOPED -> "One circuit reaches both terminals, so it would count the same FE forever. Break the loop on one side.";
            case NO_SUPPLY -> "Nothing on the " + meter.inputFace().getName() + " face can supply it. That end is the brass terminal.";
            default -> "Nothing on the " + meter.outputFace().getName() + " face can take power. That end is the gland.";
        };
        final List<String> lines = GlassFont.split(advice, gl.w() - 28);
        final int bh = lines.size() * 10 + 8;
        final int by = gl.y() + (gl.h() - bh) / 2;
        final int x0 = gl.x() + 8;
        final int x1 = gl.right() - 8;
        g.fill(x0, by, x1, by + bh, PlateInk.FAULT_BAND);
        g.fill(x0, by, x1, by + 1, PlateInk.RED.dim());
        g.fill(x0, by + bh - 1, x1, by + bh, PlateInk.RED.dim());
        g.fill(x0, by, x0 + 1, by + bh, PlateInk.RED.dim());
        g.fill(x1 - 1, by, x1, by + bh, PlateInk.RED.dim());
        for (int i = 0; i < lines.size(); i++) {
            if (i == 0) {
                GlassFont.glow(g, lines.get(i), gl.x() + 16, by + 5, PlateInk.RED, 1);
            } else {
                GlassFont.draw(g, lines.get(i), gl.x() + 16, by + 5 + i * 10, PlateInk.RED.main(), 1);
            }
        }
    }

    private void drawKeys(GuiGraphics g, int mx, int my) {
        for (int i = 0; i < 3; i++) {
            final Rect k = geo.keys[i];
            final boolean on = range == i;
            final boolean hover = !on && k.contains(mx, my);
            plate.sprite(g, on ? Sprite.KEY_DOWN : hover ? Sprite.KEY_HOVER : Sprite.KEY_UP, k.x(), k.y());
            final String t = SPAN[i];
            g.drawString(font, t, k.x() + (k.w() - font.width(t)) / 2, k.y() + (on ? 5 : 4), PlateInk.BRASS_INK, false);

            plate.engrave(g, String.valueOf(i + 1), 7, k.y() + 4, PlateInk.ENGRAVE_SOFT);
        }
    }

    private void drawFoot(GuiGraphics g, float pt) {
        final Ink ink = isFault() ? PlateInk.IDLE : PlateInk.GREEN;
        final boolean bright = flash > 0;
        for (final PlateGeometry.Line l : new PlateGeometry.Line[]{geo.peak, geo.carried, geo.comparator, geo.load}) {
            plate.engrave(g, l.legend(), l.legendX(), l.window().y() + 3);
        }
        plate.window(g, geo.peak.window(), false);
        plate.window(g, geo.carried.window(), false);
        plate.window(g, geo.load.window(), false);

        value(g, geo.peak.window(), MeterText.group(meter.getPeakFePerTick()),
                MeterText.compact(meter.getPeakFePerTick()), "FE/t", ink, bright);
        value(g, geo.carried.window(), MeterText.compact(meter.getTotalFe()),
                MeterText.compact(meter.getTotalFe()), "FE", ink, bright);

        // 15 lamps in threes of five, count groups. red glass means fault, so lamps

        final Rect c = geo.comparator.window();
        final int lvl = meter.getAnalogOutputSignal();
        final int pitch = Math.max(3, Math.min(4, (c.w() - 8) / 16));
        final int stripW = 15 * pitch - 1 + 4 + 4;
        plate.cut(g, c.x(), c.y(), Math.min(c.w(), stripW + 4), c.h(), PlateInk.LAMP_FLOOR);
        for (int i = 0; i < 15; i++) {
            final int lx = c.x() + 4 + i * pitch + (i / 5) * 2;
            plate.sprite(g, i < lvl ? Sprite.LAMP_ON : Sprite.LAMP_OFF, lx, c.y() + 4, pitch - 1);
        }

        final Rect r = geo.load.window();
        final int[] buf = meter.getLoadBuffer();
        if (buf[1] > 0) {
            final int pct = Math.round(100.0f * buf[0] / buf[1]);
            final Ink li = pct >= 100 ? PlateInk.AMBER : ink;
            GlassFont.draw(g, String.format(Locale.ROOT, "%4s", pct + "%"), r.x() + 3, r.y() + 3, li.main(), 1);
            final int bx = r.x() + 3 + GlassFont.width("100%") + 5;
            final int bw = r.right() - 5 - bx;
            if (bw > 6) {
                final int fillW = Math.round(bw * Math.min(100, pct) / 100.0f);
                g.drawManaged(() -> {
                    for (int x = bx; x < bx + bw; x++) {
                        g.fill(x, r.y() + 3, x + 1, r.y() + 10, ((x - bx) & 1) == 1 ? li.faint() : li.dim());
                    }
                    g.fill(bx, r.y() + 3, bx + fillW, r.y() + 10, li.mid());
                    g.fill(bx, r.y() + 3, bx + fillW, r.y() + 4, li.main());
                });
            }
        } else {
            GlassFont.draw(g, "no buffer", r.x() + 3, r.y() + 3, PlateInk.IDLE.mid(), 1);
        }

        final int lx = geo.w - 6;
        final int ly = r.y() + 6;
        plate.rule(g, r.right() + 1, ly, lx, ly);
        plate.rule(g, lx, geo.faceY + 3, lx, ly);

        final int[][] bracket = bracket();
        for (final int[] s : bracket) {
            plate.rule(g, s[0], s[1], s[2], s[3]);
        }

        final Rect stud = geo.stud;
        plate.sprite(g, hold.holding() ? Sprite.STUD_DOWN : sealed ? Sprite.STUD : Sprite.STUD_OPEN, stud.x(), stud.y());
        final Rect z = geo.studZone;
        plate.engrave(g, "RESET", z.x() + (z.w() - font.width("RESET")) / 2, stud.y() - 9);
        plate.engrave(g, "HOLD", z.x() + (z.w() - font.width("HOLD")) / 2, stud.y() + 19, PlateInk.ENGRAVE_SOFT);
        if (hold.holding()) {
            holdMoment(g, bracket, hold.progress(pt));
        }
    }

    private void value(GuiGraphics g, Rect r, String full, String compact, String unit, Ink k, boolean bright) {
        final int room = r.w() - 6;
        String text = full;
        if (GlassFont.width(text) + 4 + GlassFont.width(unit) > room) {
            text = compact;
        }
        final boolean showUnit = GlassFont.width(text) + 4 + GlassFont.width(unit) <= room;
        final int w = GlassFont.draw(g, text, r.x() + 3, r.y() + 3, bright ? k.bright() : k.main(), 1);
        if (showUnit) {
            GlassFont.draw(g, unit, r.x() + 3 + w + 4, r.y() + 3, k.mid(), 1);
        }
    }

    private int[][] bracket() {
        final int bx = geo.bracketX;
        return new int[][]{
                {geo.stud.right(), geo.bracketStudY, bx, geo.bracketStudY},
                {bx, geo.bracketStudY, bx, geo.bracketTop},
                {bx, geo.bracketStudY, bx, geo.bracketRow2},
                {bx, geo.bracketRow1, geo.peak.legendX() - 4, geo.bracketRow1},
                {bx, geo.bracketRow2, geo.carried.legendX() - 4, geo.bracketRow2},
        };
    }

    private void holdMoment(GuiGraphics g, int[][] bracket, float p) {
        final int cx = geo.stud.x() + 8;
        final int cy = geo.stud.y() + 8;
        g.drawManaged(() -> {
            for (int y = -10; y <= 10; y++) {
                for (int x = -10; x <= 10; x++) {
                    final double d = Math.sqrt(x * x + y * y);
                    if (d < 9.2 || d > 10.4) {
                        continue;
                    }
                    final double a = (Math.atan2(x, -y) + Math.PI * 2) % (Math.PI * 2);
                    g.fill(cx + x, cy + y, cx + x + 1, cy + y + 1,
                            a / (Math.PI * 2) <= p ? PlateInk.AMBER.main() : PlateInk.RING_TRACK);
                }
            }

            final int d0 = Math.abs(bracket[0][2] - bracket[0][0]);
            final int[] start = {0, d0, d0, d0 + Math.abs(bracket[3][1] - bracket[0][1]), d0 + Math.abs(bracket[2][3] - bracket[2][1])};
            int reach = 0;
            for (int i = 0; i < bracket.length; i++) {
                reach = Math.max(reach, start[i] + Math.abs(bracket[i][2] - bracket[i][0]) + Math.abs(bracket[i][3] - bracket[i][1]));
            }
            final float lit = p * reach;
            for (int i = 0; i < bracket.length; i++) {
                final int[] s = bracket[i];
                final int len = Math.max(Math.abs(s[2] - s[0]), Math.abs(s[3] - s[1]));
                for (int k = 0; k <= len; k++) {
                    if (start[i] + k <= lit) {
                        final int x = s[0] + Integer.signum(s[2] - s[0]) * k;
                        final int y = s[1] + Integer.signum(s[3] - s[1]) * k;
                        g.fill(x, y, x + 1, y + 1, PlateInk.AMBER.main());
                    }
                }
            }
        });
    }

    private List<Component> tooltipAt(int mx, int my) {
        final PlateHotspots hot = new PlateHotspots(geo);
        if (hot.trace().contains(mx, my)) {
            return null;
        }
        for (int i = 0; i < 3; i++) {
            if (hot.key(i).contains(mx, my)) {
                return lines(Component.translatable("gui.create_stats.power_meter.tip.span", spanName(i)).getString(),
                        Component.translatable("gui.create_stats.power_meter.tip.key", i + 1).getString());
            }
        }
        if (hot.stud().contains(mx, my)) {
            return body("gui.create_stats.power_meter.tip.stud", "gui.create_stats.power_meter.tip.stud.body");
        }
        final String in = meter.inputFace().getName();
        final String out = meter.outputFace().getName();
        final Component supply = meter.getSupplyName();
        final Component load = meter.getLoadName();
        if (hot.peak().contains(mx, my)) {
            return body("gui.create_stats.power_meter.tip.peak", "gui.create_stats.power_meter.tip.peak.body");
        }
        if (hot.carried().contains(mx, my)) {
            return body("gui.create_stats.power_meter.tip.carried", "gui.create_stats.power_meter.tip.carried.body");
        }
        if (hot.comparator().contains(mx, my)) {
            final List<Component> tip = body("gui.create_stats.power_meter.tip.comparator",
                    "gui.create_stats.power_meter.tip.comparator.body");
            tip.set(0, Component.translatable("gui.create_stats.power_meter.tip.comparator",
                    meter.getAnalogOutputSignal(), net.minecraft.world.level.redstone.Redstone.SIGNAL_MAX));
            return tip;
        }
        if (hot.load().contains(mx, my)) {
            return body("gui.create_stats.power_meter.tip.load", "gui.create_stats.power_meter.tip.load.body");
        }
        if (hot.rate().contains(mx, my)) {
            return body("gui.create_stats.power_meter.tip.rate", "gui.create_stats.power_meter.tip.rate.body");
        }
        if (hot.terminal().contains(mx, my)) {
            return lines(Component.translatable("gui.create_stats.power_meter.tip.terminal", in).getString(),
                    supply != null
                            ? Component.translatable("gui.create_stats.power_meter.tip.terminal.supply", supply.getString()).getString()
                            : Component.translatable("gui.create_stats.power_meter.tip.terminal.none").getString());
        }
        if (hot.gland().contains(mx, my)) {
            return lines(Component.translatable("gui.create_stats.power_meter.tip.gland", out).getString(),
                    load != null
                            ? Component.translatable("gui.create_stats.power_meter.tip.gland.load", load.getString()).getString()
                            : Component.translatable("gui.create_stats.power_meter.tip.gland.none").getString());
        }
        if (hot.supply().contains(mx, my)) {
            return supply != null
                    ? lines(Component.translatable("gui.create_stats.power_meter.tip.terminal.supply", supply.getString()).getString(),
                            Component.translatable("gui.create_stats.power_meter.tip.supply.on", in).getString())
                    : lines(Component.translatable("gui.create_stats.power_meter.tip.supply.none", in).getString());
        }
        if (hot.loadName().contains(mx, my)) {
            return load != null
                    ? lines(Component.translatable("gui.create_stats.power_meter.tip.gland.load", load.getString()).getString(),
                            Component.translatable("gui.create_stats.power_meter.tip.load.on", out).getString())
                    : lines(Component.translatable("gui.create_stats.power_meter.tip.load.none", out).getString());
        }
        if (hot.stamp().contains(mx, my)) {
            final BlockPos p = meter.getBlockPos();
            return lines(Component.translatable("gui.create_stats.power_meter.tip.position", p.getX(), p.getY(), p.getZ())
                    .getString());
        }
        return null;
    }

    private static String spanName(int range) {
        final int minutes = MeterHistory.SIZE * MeterHistory.CADENCE[range] / (SharedConstants.TICKS_PER_SECOND * 60);
        return Component.translatable(minutes == 1 ? "gui.create_stats.power_meter.tip.span.minute"
                : "gui.create_stats.power_meter.tip.span.minutes", minutes).getString();
    }

    private static List<Component> body(String titleKey, String bodyKey) {
        return lines(Component.translatable(titleKey).getString(),
                Component.translatable(bodyKey).getString().split("\n"));
    }

    private static List<Component> lines(String first, String... rest) {
        final List<Component> out = new ArrayList<>();
        out.add(Component.literal(first));
        for (final String r : rest) {
            out.add(Component.literal(r).withStyle(ChatFormatting.GRAY));
        }
        return out;
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
        final int mx = (int) Math.floor(mouseX) - geo.x;
        final int my = (int) Math.floor(mouseY) - geo.y;
        final PlateHotspots hot = new PlateHotspots(geo);
        for (int i = 0; i < 3; i++) {
            if (hot.key(i).contains(mx, my)) {
                if (i != range) {
                    click(1.25f);
                    selectRange(i);
                }
                return true;
            }
        }
        if (hot.stud().contains(mx, my)) {
            click(0.8f);
            hold.press();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        hold.release();
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (key >= GLFW.GLFW_KEY_1 && key <= GLFW.GLFW_KEY_3) {
            final int i = key - GLFW.GLFW_KEY_1;
            if (i != range) {
                click(1.25f);
                selectRange(i);
            }
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }

    private void click(float pitch) {
        if (minecraft != null) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, pitch));
        }
    }

    private void selectRange(int next) {
        this.range = MeterHistory.clampRange(next);
        subscribe();
    }

    private void resetMeter() {
        PacketDistributor.sendToServer(new PowerMeterActionPacket(meter.getBlockPos(), PowerMeterActionPacket.ACTION_RESET));
        history.clear();
        flash = 8;
        sealed = false;
        if (minecraft != null) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.TRIPWIRE_DETACH, 1.2f, 0.9f));
        }
    }

    public void receiveHistory(BlockPos pos, int packetRange, boolean full, int[] samples) {
        if (!pos.equals(meter.getBlockPos()) || packetRange != range) {
            return;
        }
        if (full) {
            history.replace(samples, samples.length);
        } else if (samples.length > 0) {
            history.record(samples[0]);
        }
    }

    private void subscribe() {
        subscribed = true;
        history.clear();
        send(PowerMeterHistoryRequestPacket.ACTION_SUBSCRIBE);
    }

    private void send(int action) {
        PacketDistributor.sendToServer(new PowerMeterHistoryRequestPacket(meter.getBlockPos(), range, action));
    }

    @Override
    public void onClose() {
        if (specimen) {
            return;
        }
        if (subscribed) {
            send(PowerMeterHistoryRequestPacket.ACTION_CLOSE);
            subscribed = false;
        }
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static final int[][] BAYER = {{0, 8, 2, 10}, {12, 4, 14, 6}, {3, 11, 1, 9}, {15, 7, 13, 5}};
}
