package com.createstats.aviatorhelmet.client;

import com.createstats.CreateStats;
import com.createstats.aviatorhelmet.AerialHudPacket;
import com.createstats.aviatorhelmet.AviatorGoggles;
import com.createstats.aviatorhelmet.AviatorHelmetItem;
import com.createstats.aviatorhelmet.AviatorHelmets;
import com.createstats.aviatorhelmet.AviatorHudLayout;
import com.createstats.aviatorhelmet.AviatorModes;
import com.createstats.aviatorhelmet.GoggleMotion;
import com.createstats.aviatorhelmet.HudTone;
import com.createstats.aviatorhelmet.LampWarmth;
import com.createstats.driverhelmet.client.TopClearance;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.annotation.Nullable;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public final class AviatorHud {
    static final ResourceLocation LENSES =
            ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, "textures/gui/aviator_helmet_lenses.png");

    private static final int LENSES_W = 512;
    private static final int LENSES_H = 400;
    private static final double LENSES_SCREEN_H = 320.0;

    private static final int RIM = 0x120C06;
    private static final int BRASS = 0x966834;
    private static final int RIM_ALPHA = 215;

    private static final long TOAST_NANOS = 1_600_000_000L;
    private static long toneToastNanos = Long.MIN_VALUE / 2;
    private static final AviatorHudState STATE = new AviatorHudState();

    private static double compassTop = 14.0;

    private AviatorHud() {
    }

    static void toneChanged() {
        toneToastNanos = System.nanoTime();
    }

    private static ItemStack helmet() {
        final Minecraft mc = Minecraft.getInstance();
        return mc.player == null ? ItemStack.EMPTY : AviatorHelmets.worn(mc.player);
    }

    private static boolean firstPerson() {
        return Minecraft.getInstance().options.getCameraType().isFirstPerson();
    }

    public static void renderLenses(GuiGraphics g, DeltaTracker delta) {
        final ItemStack helmet = helmet();
        if (helmet.isEmpty() || !AviatorModes.lensesInView(firstPerson())) {
            return;
        }
        final AviatorGoggles goggles = AviatorHelmetItem.goggles(helmet);
        final double seconds = GoggleView.secondsInto(goggles);
        final double cover = GoggleMotion.lensCover(goggles.down(), seconds);
        if (cover <= 0.002) {
            return;
        }
        final HudTone tone = HudTone.of(goggles.tone());
        final int w = g.guiWidth();
        final int h = g.guiHeight();

        final boolean seated = cover >= 0.999;
        final double shift = (1.0 - cover) * h * 1.05;
        final int edge = seated ? h : (int) Math.round(h * 1.241 - shift);
        if (edge <= 0) {
            return;
        }
        final double tint = cover * cover * (0.7 + 0.3 * LampWarmth.warm(goggles.down(), seconds));

        RenderSystem.depthMask(false);
        try {
            final int tintArgb = ((int) Math.round(((tone.tint >>> 24) & 0xFF) * tint) << 24) | (tone.tint & 0xFFFFFF);
            if ((tintArgb >>> 24) > 0) {
                g.fill(0, 0, w, Math.min(h, edge), tintArgb);
            }
            RenderSystem.enableBlend();
            g.enableScissor(0, 0, w, Math.min(h, edge));
            final int fieldH = (int) Math.round(h * LENSES_H / LENSES_SCREEN_H);
            g.blit(LENSES, 0, (int) Math.round(-shift), w, fieldH, 0.0f, 0.0f, LENSES_W, LENSES_H, LENSES_W, LENSES_H);
            g.disableScissor();
            if (!seated && edge < h + 6) {
                g.fill(0, edge - 5, w, edge - 1, (RIM_ALPHA << 24) | RIM);
                g.fill(0, edge - 1, w, edge + 1, (RIM_ALPHA << 24) | BRASS);
            }
        } finally {
            g.flush();
            RenderSystem.depthMask(true);
        }
    }

    public static void renderHud(GuiGraphics g, DeltaTracker delta) {
        final Minecraft mc = Minecraft.getInstance();
        final ItemStack helmet = helmet();
        if (helmet.isEmpty() || mc.player == null) {
            return;
        }
        final AviatorGoggles goggles = AviatorHelmetItem.goggles(helmet);
        if (!AviatorModes.hudInView(goggles.down(), firstPerson(), mc.player.isPassenger())) {
            return;
        }
        final double seconds = GoggleView.secondsInto(goggles);
        if (!LampWarmth.lit(goggles.down(), seconds)) {
            return;
        }
        final Font font = mc.font;
        final int w = g.guiWidth();
        final int h = g.guiHeight();
        final AerialHudPacket p = AerialHudPacket.current();
        if (p != null) {
            STATE.update(p, System.nanoTime());
        }
        final AviatorHudLayout base = AviatorHudLayout.of(w, h);
        final int[] span = base.compassSpan();
        final int blocked = TopClearance.blockedBottom(span[0], span[1], h / 3);
        final double wanted = Math.max(14.0, blocked + 4.0);
        compassTop += (wanted - compassTop) * 0.35;
        final AviatorHudLayout at = AviatorHudLayout.of(w, h, (int) Math.round(compassTop));

        final HudPen pen = new HudPen(g, font, HudTone.of(goggles.tone()));
        pen.warm = LampWarmth.warm(goggles.down(), seconds);
        pen.level = LampWarmth.level(goggles.down(), seconds);
        final List<String> banner = p == null ? List.of() : balanced(font, p.banner(), at.bannerWidth);

        RenderSystem.depthMask(false);
        try {
            RenderSystem.enableBlend();
            for (int pass = 0; pass < 2; pass++) {
                pen.ghost(pass == 0);
                if (p == null) {
                    pen.glow = AviatorHudState.STEADY;
                    reticle(pen, at, 0.0, 0.0, false);
                } else {
                    draw(pen, at, p, banner);
                }
                if (System.nanoTime() - toneToastNanos < TOAST_NANOS) {
                    pen.glow = AviatorHudState.ACTIVE;
                    final HudTone t = HudTone.of(goggles.tone());
                    final String name = Component.translatable("hud.create_stats.aviator_helmet.tone." + t.id).getString();
                    pen.text(Component.translatable("hud.create_stats.aviator_helmet.tone", name).getString()
                            .toUpperCase(Locale.ROOT), at.reticleX, at.reticleY - 24, HudPen.Align.CENTRE);
                }
            }
        } finally {
            g.flush();
            RenderSystem.depthMask(true);
        }
    }

    private static void draw(HudPen pen, AviatorHudLayout at, AerialHudPacket p, List<String> banner) {
        pen.glow = STATE.compassGlow;
        compass(pen, at, p);
        pen.glow = STATE.altGlow;
        altimeter(pen, at, p);
        pen.glow = STATE.telegraphGlow;
        telegraph(pen, at, p);
        pen.glow = STATE.attitudeGlow;
        reticle(pen, at, STATE.roll, STATE.pitch, true);
        pen.glow = AviatorHudState.STEADY;
        pen.text(p.mode(), at.modeX, at.modeY, HudPen.Align.LEFT);
        if (!p.sub().isEmpty()) {
            pen.text(p.sub(), at.modeX, at.modeY + 10, HudPen.Align.LEFT, pen.core(), true);
        }
        if (!banner.isEmpty()) {
            pen.glow = p.alert() ? 0.8 + 0.2 * Math.sin(System.nanoTime() / 1.0e9 * 2.0 * Math.PI) : AviatorHudState.ACTIVE;
            final int colour = p.alert() ? pen.alert() : pen.core();
            final int top = at.bannerTop(banner.size());
            for (int i = 0; i < banner.size(); i++) {
                pen.text(banner.get(i), at.width / 2.0, top + AviatorHudLayout.LINE * i, HudPen.Align.CENTRE, colour, false);
            }
        }
    }

    private static void compass(HudPen pen, AviatorHudLayout at, AerialHudPacket p) {
        final double heading = Double.isFinite(STATE.heading) ? STATE.heading : p.heading();
        final int cx = at.compassX;
        final int half = at.compassHalf;
        final int y = at.compassTop;
        final int px = AviatorHudLayout.COMPASS_PX_PER_DEG;
        pen.line(cx - half, y + 14, cx + half, y + 14);
        final int from = (int) Math.floor(heading) - 60;
        for (int deg = from; deg <= from + 121; deg++) {
            final int x = (int) Math.round(cx + (deg - heading) * px);
            if (Math.abs(x - cx) > half) {
                continue;
            }
            final int d = Math.floorMod(deg, 360);
            if (d % 10 == 0) {
                pen.thin(x, y + 14, x, y + 9);
                if (d % 30 == 0) {
                    pen.text(cardinal(d), x, y, HudPen.Align.CENTRE);
                }
            } else if (d % 5 == 0) {
                pen.thin(x, y + 14, x, y + 12);
            }
        }

        pen.line(cx, y + 16, cx, y + 22);
        pen.polyline(true, cx - 3, y + 22, cx, y + 19, cx + 3, y + 22);
        if (!Float.isNaN(p.heldHeading())) {
            final int x = at(cx, heading, p.heldHeading(), px);
            if (Math.abs(x - cx) <= half) {
                pen.polyline(true, x - 3, y + 15, x, y + 18, x + 3, y + 15);
            }
        }
        if (!Float.isNaN(p.track())) {
            final int x = at(cx, heading, p.track(), px);
            if (Math.abs(x - cx) <= half) {
                pen.polyline(false, x, y + 15, x + 2, y + 17, x, y + 19, x - 2, y + 17, x, y + 15);
            }
        }
        if (p.windSpeed() > 0.3f && !Float.isNaN(p.windFrom())) {
            final double rx = cx - half - 16;
            final double ry = y + 8;
            final double rr = 7.0;
            final double[] ring = new double[26];
            for (int i = 0; i < 13; i++) {
                final double a = Math.toRadians(i * 30.0);
                ring[2 * i] = rx + rr * Math.cos(a);
                ring[2 * i + 1] = ry + rr * Math.sin(a);
            }
            pen.polyline(false, ring);
            final double toward = Math.toRadians(AviatorHudState.normalise(p.windFrom() + 180.0 - heading));
            final double dx = Math.sin(toward);
            final double dy = -Math.cos(toward);
            pen.line(rx - dx * 5, ry - dy * 5, rx + dx * 5, ry + dy * 5);
            final double tx = rx + dx * 5;
            final double ty = ry + dy * 5;
            pen.polyline(true, tx - dx * 3 - dy * 2, ty - dy * 3 + dx * 2, tx, ty, tx - dx * 3 + dy * 2, ty - dy * 3 - dx * 2);
            pen.text(String.format(Locale.ROOT, "%.1f", p.windSpeed()), rx, ry + rr + 3, HudPen.Align.CENTRE, pen.core(), true);
        }
        if (!Float.isNaN(p.targetBearing())) {
            final int x = Math.max(cx - half, Math.min(cx + half, at(cx, heading, p.targetBearing(), px)));
            pen.line(x, y - 2, x, y + 13);
            pen.poly(x + 1, y - 2, x + 7, y + 1, x + 1, y + 4);
            if (!p.targetLabel().isEmpty()) {
                pen.text(p.targetLabel(), cx, y + 26, HudPen.Align.CENTRE);
            }
        }
    }

    private static int at(int cx, double heading, double bearing, int px) {
        return (int) Math.round(cx + (AviatorHudState.normalise(bearing - heading + 540.0) - 180.0) * px);
    }

    private static String cardinal(int d) {
        return switch (d) {
            case 0 -> "N";
            case 90 -> "E";
            case 180 -> "S";
            case 270 -> "W";
            default -> Integer.toString(d / 10);
        };
    }

    private static void altimeter(HudPen pen, AviatorHudLayout at, AerialHudPacket p) {
        final double alt = Double.isFinite(STATE.altitude) ? STATE.altitude : p.altitude();
        final int x = at.tapeX;
        final int mid = at.tapeMid;
        final int top = mid - at.tapeHalf;
        final int bot = mid + at.tapeHalf;
        final int px = AviatorHudLayout.TAPE_PX_PER_BLOCK;
        pen.line(x, top, x, bot);
        final int reach = at.tapeHalf / px + 2;
        final int base = (int) Math.floor(alt);
        for (int b = base - reach; b <= base + reach; b++) {
            final int yy = (int) (mid - (b - alt) * px);
            if (yy < top || yy > bot) {
                continue;
            }
            if (b % 10 == 0) {
                pen.thin(x, yy, x + 6, yy);
                if (Math.abs(yy - mid) > 10) {
                    pen.text(Integer.toString(b), x - 3, yy - 4, HudPen.Align.RIGHT);
                }
            } else if (b % 2 == 0) {
                pen.thin(x, yy, x + 3, yy);
            }
        }
        if (!Float.isNaN(p.ceiling())) {
            final int yy = (int) (mid - (p.ceiling() - alt) * px);
            if (yy >= top && yy <= bot) {
                pen.line(x - 8, yy, x + 12, yy);
                pen.line(x - 8, yy + 2, x + 12, yy + 2);
                pen.text("CEIL", x + 14, yy - 3, HudPen.Align.LEFT);
            }
        }
        if (!Float.isNaN(p.terrain())) {
            final int yy = (int) (mid - (p.terrain() - alt) * px);
            if (yy >= top && yy <= bot) {
                pen.line(x - 8, yy, x + 12, yy);
                for (int k = -8; k < 12; k += 3) {
                    pen.thin(x + k, yy + 1, x + k + 3, yy + 5);
                }
            }
        }
        if (!Float.isNaN(p.heldAltitude())) {
            final double held = p.heldAltitude();
            final int yy = Math.max(top, Math.min(bot, (int) (mid - (held - alt) * px)));
            pen.polyline(true, x + 8, yy - 3, x + 12, yy, x + 8, yy + 3);
            final boolean onGround = !Float.isNaN(p.terrain()) && Math.abs(held - p.terrain()) < 3.0;
            if (Math.abs(held - alt) >= 1.0 && !onGround) {
                pen.text(Long.toString(Math.round(held)), x + 14, yy - 4, HudPen.Align.LEFT);
            }
        }
        drum(pen, alt, x - 2, mid);
        if (!Float.isNaN(p.ground())) {
            final boolean low = p.ground() < 10.0f;
            final double glow = pen.glow;
            if (low) {
                pen.glow = AviatorHudState.ACTIVE;
            }
            pen.text(String.format(Locale.ROOT, low ? "GND %.1f" : "GND %.0f", p.ground()), x, bot + 6,
                    HudPen.Align.CENTRE, pen.core(), !low);
            pen.glow = glow;
        }

        final int sx = at.statoscopeX;
        final int sh = AviatorHudLayout.STATOSCOPE_HALF;
        pen.thin(sx, mid - sh, sx, mid + sh);
        for (int v : new int[] {-4, -2, -1, 0, 1, 2, 4}) {
            final int ty = mid - v * 8;
            pen.thin(sx - (v == 0 ? 4 : 2), ty, sx, ty);
        }
        final double vy = STATE.climb;
        final int hh = (int) Math.max(-sh, Math.min(sh, -vy * 8.0));
        pen.poly(sx + 2, mid + hh, sx + 6, mid + hh - 3, sx + 6, mid + hh + 3);
        pen.text(String.format(Locale.ROOT, "%+.1f", vy), sx + 9, mid + hh - 4, HudPen.Align.LEFT);
        pen.text("RISE", sx - 8, mid - sh - 12, HudPen.Align.LEFT, pen.core(), true);
    }

    private static void drum(HudPen pen, double alt, int right, int mid) {
        final int whole = (int) Math.floor(alt);
        final double frac = alt - whole;
        final String s = Integer.toString(whole);
        final int w = pen.font().width(Integer.toString(whole + 1)) + 6;
        final int x0 = right - w;
        pen.rect(x0, mid - 6, right, mid + 6);
        final String head = s.substring(0, s.length() - 1);
        int tx = x0 + 3;
        if (!head.isEmpty()) {
            tx += pen.text(head, tx, mid - 4, HudPen.Align.LEFT);
        }
        final String cur = s.substring(s.length() - 1);
        final String next = Integer.toString(Math.abs((whole + 1) % 10));
        final int off = (int) Math.round(frac * 9.0);
        pen.clip(x0 + 1, mid - 5, right, mid + 6);
        pen.text(cur, tx, mid - 4 - off, HudPen.Align.LEFT);
        pen.text(next, tx, mid + 5 - off, HudPen.Align.LEFT);
        pen.unclip();
    }

    private static void telegraph(HudPen pen, AviatorHudLayout at, AerialHudPacket p) {
        final int cx = at.telegraphX;
        final int cy = at.telegraphY;
        final int r = AviatorHudLayout.TELEGRAPH_R;
        final double[] arc = new double[62];
        for (int i = 0; i <= 30; i++) {
            final double a = Math.toRadians(i * 6.0);
            arc[2 * i] = cx + r * Math.cos(a);
            arc[2 * i + 1] = cy - r * Math.sin(a);
        }
        pen.polyline(true, arc);
        pen.thin(cx - r - 2, cy, cx + r + 2, cy);
        for (double pos : new double[] {-1.0, -0.5, -0.25, 0.0, 0.25, 0.5, 1.0}) {
            final double a = Math.toRadians(90.0 - pos * 90.0);
            pen.thin(cx + (r - 5) * Math.cos(a), cy - (r - 5) * Math.sin(a), cx + r * Math.cos(a), cy - r * Math.sin(a));
        }
        pen.text("AST", cx - r - 2, cy + 3, HudPen.Align.LEFT);
        pen.text("AHD", cx + r + 2, cy + 3, HudPen.Align.RIGHT);
        double a = Math.toRadians(90.0 - STATE.order * 90.0);
        pen.line(cx, cy, cx + (r - 3) * Math.cos(a), cy - (r - 3) * Math.sin(a));
        a = Math.toRadians(90.0 - STATE.engine * 90.0);
        pen.line(cx + 6 * Math.cos(a), cy - 6 * Math.sin(a), cx + (r - 14) * Math.cos(a), cy - (r - 14) * Math.sin(a),
                pen.core(), true, 2);
        pen.text(orderWords(p.order()), cx, cy + 14, HudPen.Align.CENTRE);
        pen.text(String.format(Locale.ROOT, "%.1f b/s", STATE.speed), cx, cy + 26, HudPen.Align.CENTRE);
        pen.text(String.format(Locale.ROOT, "FULL %.1f", p.full()), cx, cy + 36, HudPen.Align.CENTRE, pen.core(), true);
        if (Double.isFinite(STATE.lift)) {
            final int y = cy + 56;
            pen.text(String.format(Locale.ROOT, "LIFT %d%%", Math.round(STATE.lift * 100.0)), cx, y - 10, HudPen.Align.CENTRE);
            pen.thin(cx - 30, y, cx + 30, y);
            pen.line(cx - 30, y, cx - 30 + (int) (60 * Math.max(0.0, Math.min(1.0, STATE.lift))), y);
        }
    }

    static String orderWords(double order) {
        final double[] notches = {1.0, 0.5, 0.25, 0.0, -0.25, -0.5, -1.0};
        final String[] words = {"FULL AHEAD", "HALF AHEAD", "SLOW AHEAD", "STOP", "SLOW ASTERN", "HALF ASTERN", "FULL ASTERN"};
        for (int i = 0; i < notches.length; i++) {
            if (Math.abs(order - notches[i]) < 0.01) {
                return words[i];
            }
        }
        return (order > 0.0 ? "AHEAD " : "ASTERN ") + Math.round(Math.abs(order) * 100.0) + "%";
    }

    private static void reticle(HudPen pen, AviatorHudLayout at, double roll, double pitch, boolean instruments) {
        final int cx = at.reticleX;
        final int cy = at.reticleY;
        final double[] ring = new double[50];
        for (int i = 0; i < 25; i++) {
            final double a = Math.toRadians(i * 15.0);
            ring[2 * i] = cx + 9 * Math.cos(a);
            ring[2 * i + 1] = cy + 9 * Math.sin(a);
        }
        pen.polyline(true, ring);
        final double rr = Math.toRadians(Double.isFinite(roll) ? roll : 0.0);
        for (int s = -1; s <= 1; s += 2) {
            pen.line(cx + s * 14 * Math.cos(rr), cy + s * 14 * Math.sin(rr), cx + s * 26 * Math.cos(rr), cy + s * 26 * Math.sin(rr));
        }
        if (!instruments) {
            return;
        }

        final int iy = cy + 36;
        final double[] tube = new double[54];
        for (int i = 0; i < 27; i++) {
            final int dx = -26 + 2 * i;
            tube[2 * i] = cx + dx;
            tube[2 * i + 1] = iy + Math.round(Math.pow(dx / 26.0, 2) * 5.0);
        }
        pen.polyline(false, tube);
        for (int deg : new int[] {-10, -5, 0, 5, 10}) {
            final int dx = (int) (deg * 2.4);
            final long ty = iy + Math.round(Math.pow(dx / 26.0, 2) * 5.0);
            pen.thin(cx + dx, ty - (deg == 0 ? 3 : 2), cx + dx, ty - 1);
        }
        final int bx = (int) Math.max(-24, Math.min(24, (Double.isFinite(pitch) ? pitch : 0.0) * 2.4));
        final long by = iy + Math.round(Math.pow(bx / 26.0, 2) * 5.0) - 2;
        pen.poly(cx + bx - 1, by - 1, cx + bx + 1, by - 1, cx + bx + 1, by + 1, cx + bx - 1, by + 1);
    }

    static List<String> balanced(Font font, @Nullable String s, int maxWidth) {
        if (s == null || s.isBlank()) {
            return List.of();
        }
        final List<String> first = split(font, s, maxWidth);
        final int n = first.size();
        if (n <= 1) {
            return List.of(s);
        }
        int width = font.width(s) / n + 24;
        while (split(font, s, width).size() > n && width < maxWidth) {
            width += 8;
        }
        final List<String> out = split(font, s, Math.min(width, maxWidth));
        return out.size() > n ? first : out;
    }

    private static List<String> split(Font font, String s, int width) {
        final List<String> out = new ArrayList<>();
        for (FormattedText t : font.getSplitter().splitLines(s, width, Style.EMPTY)) {
            out.add(t.getString());
        }
        return out;
    }
}
