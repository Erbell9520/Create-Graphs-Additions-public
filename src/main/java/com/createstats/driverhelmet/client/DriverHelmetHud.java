package com.createstats.driverhelmet.client;

import com.createstats.CreateStats;
import com.createstats.driverhelmet.DashLine;
import com.createstats.driverhelmet.DriverHelmetItem;
import com.createstats.driverhelmet.DriverHelmets;
import com.createstats.driverhelmet.DriverTelemetryPacket;
import com.createstats.driverhelmet.HelmetGlowPacket;
import com.createstats.driverhelmet.HelmetVisor;
import com.createstats.driverhelmet.HudBoot;
import com.createstats.driverhelmet.HudLayout;
import com.createstats.driverhelmet.HudTheme;
import com.createstats.driverhelmet.LedBar;
import com.createstats.driverhelmet.LedFont;
import com.createstats.driverhelmet.VisorModes;
import com.createstats.driverhelmet.VisorMotion;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.List;
import java.util.Locale;
import javax.annotation.Nullable;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public final class DriverHelmetHud {
    static final ResourceLocation FRAME =
            ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, "textures/gui/driver_helmet_visor.png");
    private static final int FRAME_W = 512;
    private static final int FRAME_H = 320;
    private static final int TINT = 0x12183E;
    private static final int LIP = 0x1E2028;
    private static final int LIP_TAB = 0xFFB23A;
    private static final int IRIDIUM = 0x7FA4FF;

    private static final long TOAST_NANOS = 1_600_000_000L;

    private static final int IMPACT_TICKS = 80;

    private static final double WARN_SLIP = LedBar.WARN_SLIP;

    private static final double EASE_SECONDS = 0.08;

    private static final int CRAWL_WINDOW = HudLayout.DASH_WIDTH - 13;

    private static final int TRAIL = 20;
    private static final float[] TRAIL_LAT = new float[TRAIL];
    private static final float[] TRAIL_LON = new float[TRAIL];
    private static int trailSize;
    private static int trailHead;
    private static DriverTelemetryPacket lastSampled;
    private static DriverTelemetryPacket previous;
    private static long sampledNanos;
    private static long themeToastNanos = Long.MIN_VALUE;
    private static float barYEased = Float.NaN;
    private static long barNanos;

    private DriverHelmetHud() {
    }

    static void themeChanged() {
        themeToastNanos = System.nanoTime();
    }

    private static ItemStack helmet() {
        final Minecraft mc = Minecraft.getInstance();
        return mc.player == null ? ItemStack.EMPTY : DriverHelmets.worn(mc.player);
    }

    private static boolean firstPerson() {
        return Minecraft.getInstance().options.getCameraType().isFirstPerson();
    }

    private static ItemStack hudHelmet() {
        final ItemStack helmet = helmet();
        if (helmet.isEmpty()) {
            return ItemStack.EMPTY;
        }
        return VisorModes.hudInView(DriverHelmetItem.visor(helmet).open(), firstPerson(),
                Minecraft.getInstance().player.isPassenger()) ? helmet : ItemStack.EMPTY;
    }

    static boolean showing() {
        return !hudHelmet().isEmpty();
    }

    public static void renderVisor(GuiGraphics g, DeltaTracker delta) {
        final ItemStack helmet = helmet();
        if (helmet.isEmpty() || !VisorModes.glassInView(firstPerson())) {
            return;
        }
        final double angle = HelmetView.angle(DriverHelmetItem.visor(helmet));
        final double down = 1.0 - angle / VisorMotion.OPEN_MAX;
        final int w = g.guiWidth();
        final int h = g.guiHeight();

        final int lip = (int) Math.round(down * (h + 16)) - 10;
        final float frame = (float) Math.max(0.0, Math.min(1.0, down * 1.2));

        if (lip + 4 <= 0 && frame < 1.0f / 255.0f) {
            return;
        }

        // depthMask false or Jade's tooltip vanishes behind us. vanilla's pumpkin does the same

        RenderSystem.depthMask(false);
        try {
            g.fill(0, 0, w, Math.max(0, Math.min(h, lip)), argb(0.20, TINT));

            RenderSystem.enableBlend();
            g.setColor(1.0f, 1.0f, 1.0f, frame);
            g.blit(FRAME, 0, 0, w, h, 0.0f, 0.0f, FRAME_W, FRAME_H, FRAME_W, FRAME_H);
            g.setColor(1.0f, 1.0f, 1.0f, 1.0f);

            if (lip > -8 && lip < h + 8) {
                g.fill(0, lip - 6, w, lip - 5, argb(0.43, IRIDIUM));
                g.fill(0, lip - 5, w, lip, argb(0.94, LIP));
                g.fill(0, lip, w, lip + 1, argb(0.86, 0x000000));
                g.fill(w / 2 - 7, lip - 2, w / 2 + 8, lip + 4, argb(1.0, LIP_TAB));
            }
        } finally {
            g.flush();
            RenderSystem.depthMask(true);
        }
    }

    private record View(HelmetVisor visor, HudTheme theme, HudBoot.Phase phase, double into, double fade,
                        String crawl, @Nullable DashLine dash, @Nullable DriverTelemetryPacket tel,
                        LedBar.Warning warning) {
    }

    private static View view(HelmetVisor visor) {
        final Minecraft mc = Minecraft.getInstance();
        final DashLine dash = DashFeed.current();
        final DriverTelemetryPacket tel = DriverTelemetryPacket.current();
        final HudSession.Boot boot = HudSession.boot(visor);
        final String name = mc.player == null ? "" : mc.player.getGameProfile().getName();
        final String crawl = "WELCOME " + name.toUpperCase(Locale.ROOT);
        final double speed = tel != null ? tel.speed() : dash == null ? 0.0 : DashFeed.smoothKmh() / 3.6;
        final boolean welcome = boot.welcome() && HudBoot.welcomeAllowed(speed);
        final HudBoot.Phase phase = HudBoot.phase(boot.seconds(), welcome,
                HudBoot.welcomeLength(LedFont.width(crawl, 2, 1, 1), CRAWL_WINDOW));
        return new View(visor, HudTheme.of(visor.theme()), phase, HudBoot.into(boot.seconds(), phase),
                HudBoot.fade(boot.seconds()), crawl, dash, tel, warning(dash, tel));
    }

    private static LedBar.Warning warning(@Nullable DashLine dash, @Nullable DriverTelemetryPacket tel) {
        if (dash == null || tel == null) {
            return LedBar.Warning.NONE;
        }
        boolean spin = false;
        boolean lock = false;
        for (DriverTelemetryPacket.Wheel w : tel.wheels()) {
            if (!w.driven() || !w.contact() || Float.isNaN(w.slip())) {
                continue;
            }
            spin |= w.slip() > WARN_SLIP && Math.abs(dash.throttle()) > 0.05;
            lock |= w.slip() < -WARN_SLIP && dash.braking();
        }
        return lock ? LedBar.Warning.LOCK : spin ? LedBar.Warning.SPIN : LedBar.Warning.NONE;
    }

    private static LedBar.Frame leds(View v) {
        return switch (v.phase()) {
            case DARK -> LedBar.uniform(LedBar.OFF, 0.0f);
            case SELF_TEST -> LedBar.selfTest(v.into());
            case WELCOME -> LedBar.sweep(v.into());
            case LIVE -> LedBar.live(v.dash() == null ? 0.0 : v.dash().throttle(), v.warning(), HelmetView.now() / 20.0);
        };
    }

    static LedBar.Frame currentLeds(HelmetVisor visor) {
        return leds(view(visor));
    }

    static int[] glowReading(HelmetVisor visor) {
        final View v = view(visor);
        final int throttle = v.phase() == HudBoot.Phase.LIVE && v.dash() != null
                ? (int) Math.round(v.dash().throttle() * 100.0) : 0;
        int state = switch (v.warning()) {
            case SPIN -> HelmetGlowPacket.SPIN;
            case LOCK -> HelmetGlowPacket.LOCK;
            case NONE -> 0;
        };
        if (v.phase() == HudBoot.Phase.WELCOME) {
            state |= HelmetGlowPacket.WELCOME;
        }
        return new int[]{throttle, state};
    }

    public static void renderHud(GuiGraphics g, DeltaTracker delta) {
        final ItemStack helmet = hudHelmet();
        if (helmet.isEmpty()) {
            return;
        }
        final View v = view(DriverHelmetItem.visor(helmet));
        if (v.phase() == HudBoot.Phase.DARK) {
            return;
        }
        final Minecraft mc = Minecraft.getInstance();
        final Font font = mc.font;
        final HudLayout at = HudLayout.of(g.guiWidth(), g.guiHeight());
        sample(v.tel());
        final int barY = barY(at);

        RenderSystem.depthMask(false);
        try {
            if (v.fade() < 1.0) {
                RenderSystem.enableBlend();
                g.setColor(1.0f, 1.0f, 1.0f, (float) v.fade());
            }
            throttleBar(g, font, at, barY, v);
            dashPlate(g, font, at, v);
            if (at.wheels) {
                wheelsPlate(g, font, at, v.tel(), v.theme());
            }
            if (at.g && !(mc.screen instanceof ChatScreen)) {
                gPlate(g, font, at, v.tel(), v.visor(), v.theme());
            }
            if (System.nanoTime() - themeToastNanos < TOAST_NANOS) {
                toast(g, font, at, barY, v.theme());
            }
        } finally {
            g.flush();
            g.setColor(1.0f, 1.0f, 1.0f, 1.0f);
            RenderSystem.depthMask(true);
        }
    }

    private static int barY(HudLayout at) {
        final int x0 = at.barX - 6;
        final int x1 = at.barX + HudLayout.BAR_WIDTH + 5;
        final int target = at.barYBelow(TopClearance.blockedBottom(x0, x1, at.maxBarY + HudLayout.TOP_PLATE_HEIGHT));
        final long now = System.nanoTime();
        if (Float.isNaN(barYEased) || now - barNanos > 500_000_000L) {
            barYEased = target;
        } else {
            final double dt = (now - barNanos) / 1.0e9;
            barYEased += (float) ((target - barYEased) * (1.0 - Math.exp(-dt / EASE_SECONDS)));
        }
        barNanos = now;
        return Math.round(barYEased);
    }

    private static void sample(@Nullable DriverTelemetryPacket tel) {
        if (tel == null) {
            trailSize = 0;
            lastSampled = null;
            previous = null;
            return;
        }
        if (tel == lastSampled) {
            return;
        }
        previous = lastSampled;
        lastSampled = tel;
        sampledNanos = System.nanoTime();
        TRAIL_LAT[trailHead] = tel.latG();
        TRAIL_LON[trailHead] = tel.lonG();
        trailHead = (trailHead + 1) % TRAIL;
        trailSize = Math.min(TRAIL, trailSize + 1);
    }

    private static int roleColour(int role, int index, HudTheme t) {
        return switch (role) {
            case LedBar.LOW -> t.shiftLow;
            case LedBar.MID -> t.shiftMid;
            case LedBar.HIGH -> t.shiftHigh;
            case LedBar.BAD -> t.bad;
            case LedBar.COOL -> t.cool;
            default -> roleColour(LedBar.segment(index), index, t);
        };
    }

    private static void throttleBar(GuiGraphics g, Font font, HudLayout at, int by, View v) {
        final HudTheme t = v.theme();
        final int bx = at.barX;
        plate(g, t, bx - 6, by - 4, bx + HudLayout.BAR_WIDTH + 5, by - 4 + HudLayout.TOP_PLATE_HEIGHT - 4);
        final LedBar.Frame leds = leds(v);
        final double ghost = v.phase() == HudBoot.Phase.SELF_TEST ? 0.06 : 0.20;
        final boolean reverse = v.phase() == HudBoot.Phase.LIVE && v.dash() != null && v.dash().throttle() < 0;
        for (int i = 0; i < HudLayout.LEDS; i++) {
            final int x = bx + i * (HudLayout.LED + HudLayout.LED_GAP);
            final int role = leds.role()[i];
            final float level = leds.level()[i];
            if (role == LedBar.OFF || level <= 0.0f) {
                final int unlit = reverse ? t.bad : roleColour(LedBar.segment(i), i, t);
                g.fill(x, by, x + HudLayout.LED, by + HudLayout.LED, argb(ghost, unlit));
                continue;
            }
            final int colour = roleColour(role, i, t);
            g.fill(x - 1, by - 1, x + HudLayout.LED + 1, by + HudLayout.LED + 1, argb(0.35 * level, colour));
            g.fill(x, by, x + HudLayout.LED, by + HudLayout.LED, argb(Math.max(0.2, level), colour));
        }

        final int ty = by + HudLayout.LED + 5;
        final DashLine dash = v.dash();
        final DriverTelemetryPacket tel = v.tel();
        final String line;
        final int colour;
        boolean scroll = false;
        if (v.phase() == HudBoot.Phase.SELF_TEST) {
            line = "System check";
            colour = argb(0.80, t.text);
        } else if (v.phase() == HudBoot.Phase.WELCOME) {
            line = "All systems OK";
            colour = argb(0.92, t.ok);
        } else if (tel != null && tel.impactAge() >= 0 && tel.impactAge() < IMPACT_TICKS) {
            line = String.format(Locale.ROOT, "IMPACT %.1f g", tel.impactG());
            colour = argb(1.0, t.bad);
        } else if (tel != null && tel.airNow() > 0.0f) {
            line = String.format(Locale.ROOT, "AIRBORNE %.1f s", tel.airNow());
            colour = argb(1.0, t.warn);
        } else if (dash == null) {
            line = "No VCC link";
            colour = argb(0.62, t.text);
        } else if (!dash.problem().isEmpty()) {
            line = "! " + dash.problem();
            colour = argb(1.0, t.warn);
            scroll = true;
        } else if (!dash.extras().isEmpty()) {
            line = dash.extras();
            colour = argb(0.86, t.text);
            scroll = font.width(line) > HudLayout.BAR_WIDTH;
        } else {
            line = "VCC Mk IV";
            colour = argb(0.92, t.ok);
        }
        final int width = font.width(line);
        g.enableScissor(bx, ty, bx + HudLayout.BAR_WIDTH, ty + 9);
        if (scroll) {
            final int period = width + HudLayout.BAR_WIDTH + 24;
            final int offset = (int) (HelmetView.now() * 1.2) % period;
            g.drawString(font, line, bx + HudLayout.BAR_WIDTH - offset, ty, colour, false);
        } else {
            g.drawString(font, line, bx + (HudLayout.BAR_WIDTH - width) / 2, ty, colour, false);
        }
        g.disableScissor();
    }

    private static void toast(GuiGraphics g, Font font, HudLayout at, int barY, HudTheme t) {
        final Component name = Component.translatable("hud.create_stats.driver_helmet.theme",
                Component.translatable("hud.create_stats.driver_helmet.theme." + t.id));
        final int w = font.width(name) + 12;
        final int x = at.barX + HudLayout.BAR_WIDTH / 2 - w / 2;
        final int y = barY - 4 + HudLayout.TOP_PLATE_HEIGHT;
        plate(g, t, x, y, x + w, y + 13);
        g.drawString(font, name, x + 6, y + 3, argb(1.0, t.primary), false);
    }

    private static void dashPlate(GuiGraphics g, Font font, HudLayout at, View v) {
        final HudTheme t = v.theme();
        final DashLine dash = v.dash();
        final boolean test = v.phase() == HudBoot.Phase.SELF_TEST;
        plate(g, t, at.dashX0, at.dashY0, at.dashX1, at.dashY1);
        final int left = at.dashX0 + 7;
        final int right = at.dashX1 - 6;
        final int top = at.dashY0 + 6;

        final String[] legends = {"BRK", "BST", "HLD", "NV"};
        final boolean[] on = {dash != null && dash.braking(), dash != null && dash.boosting(),
                dash != null && dash.holding(), v.visor().nightVision()};
        final int[] colours = {t.bad, t.cool, t.warn, t.ok};
        int lx = left;
        for (int i = 0; i < legends.length; i++) {
            final int w = LedFont.width(legends[i], 1, 0, 1) + 6;
            if (on[i] || test) {
                g.fill(lx, top - 2, lx + w, top + 9, argb(0.94, colours[i]));
                led(g, legends[i], lx + 3, top, argb(1.0, t.plate), 1, 0, 1, 0.0f);
            } else {
                outline(g, lx, top - 2, lx + w, top + 9, argb(0.28, t.text));
                led(g, legends[i], lx + 3, top, argb(0.34, t.text), 1, 0, 1, 0.0f);
            }
            lx += w + 2;
        }

        final int ry = top + 15;
        if (v.phase() == HudBoot.Phase.WELCOME) {
            final int x = left + CRAWL_WINDOW - (int) Math.round(v.into() * HudBoot.CRAWL);
            g.enableScissor(left - 3, ry - 3, right + 1, ry + 23);
            led(g, v.crawl(), x, ry, argb(1.0, t.primary), 2, 1, 1, 0.0f);
            g.disableScissor();
        } else {
            final char gear = test ? '8' : dash == null ? '-' : dash.gear();
            final int gearColour = gear == 'B' ? t.bad : gear == 'H' ? t.cool : t.primary;
            final int gw = LedFont.width("D", 2, 1, 1);
            outline(g, left - 3, ry - 3, left + gw + 3, ry + 23, argb(0.60, gearColour));
            led(g, String.valueOf(gear), left, ry, argb(1.0, gearColour), 2, 1, 1, 0.08f);
            final String speed = test ? "888" : dash == null ? "---"
                    : String.format(Locale.ROOT, "%03d", Math.min(999, Math.round(Math.abs(DashFeed.smoothKmh()))));
            final String unit = "km/h";
            final int ux = right - font.width(unit);
            final int sx = ux - 5 - LedFont.width(speed, 2, 1, 1);
            led(g, speed, sx, ry, argb(1.0, t.primary), 2, 1, 1, 0.08f);
            g.drawString(font, unit, ux, ry + 7, argb(0.84, t.text), false);
        }

        final int sy = ry + 29;
        g.drawString(font, "Steer", left, sy - 3, argb(0.80, t.text), false);
        final int barW = 58;
        final int bx = right - barW;
        g.fill(bx, sy, bx + barW + 1, sy + 2, argb(0.31, t.text));
        g.fill(bx + barW / 2, sy - 3, bx + barW / 2 + 1, sy + 5, argb(0.66, t.text));
        if (dash != null && !test) {
            final int mx = bx + barW / 2 + (int) Math.round(dash.steer() * (barW / 2 - 2));
            g.fill(mx - 1, sy - 3, mx + 2, sy + 5, argb(1.0, t.primary));
        }
    }

    private static void wheelsPlate(GuiGraphics g, Font font, HudLayout at, @Nullable DriverTelemetryPacket tel,
                                    HudTheme t) {
        final int x0 = at.dashX0;
        final int x1 = at.dashX1;
        final int y0 = at.wheelsY0;
        plate(g, t, x0, y0, x1, at.wheelsY1);
        g.drawString(font, "Wheels", x0 + 6, y0 + 4, argb(0.62, t.text), false);
        final List<DriverTelemetryPacket.Wheel> wheels = tel == null ? List.of() : tel.wheels();
        final int cx = (x0 + x1) / 2;
        final int cy = y0 + 34;
        if (wheels.isEmpty()) {
            final String none = tel == null ? "No telemetry" : "No wheels found";
            g.drawString(font, none, cx - font.width(none) / 2, cy - 4, argb(0.55, t.text), false);
            return;
        }
        g.drawString(font, wheels.size() + "", x1 - 6 - font.width(wheels.size() + ""), y0 + 4, argb(0.62, t.text), false);

        float minLat = Float.MAX_VALUE, maxLat = -Float.MAX_VALUE, minLon = Float.MAX_VALUE, maxLon = -Float.MAX_VALUE;
        for (DriverTelemetryPacket.Wheel w : wheels) {
            minLat = Math.min(minLat, w.lat());
            maxLat = Math.max(maxLat, w.lat());
            minLon = Math.min(minLon, w.lon());
            maxLon = Math.max(maxLon, w.lon());
        }
        final float latC = (minLat + maxLat) / 2.0f;
        final float lonC = (minLon + maxLon) / 2.0f;

        final float scale = Math.min(12.0f, Math.min(80.0f / Math.max(1.0f, maxLat - minLat),
                28.0f / Math.max(1.0f, maxLon - minLon)));
        final int bw = Math.round((maxLat - minLat) * scale) / 2 + 6;
        final int bh = Math.round((maxLon - minLon) * scale) / 2 + 7;
        outline(g, cx - bw, cy - bh, cx + bw + 1, cy + bh + 1, argb(0.22, t.text));
        g.fill(cx, cy - bh - 3, cx + 1, cy - bh - 1, argb(0.45, t.text));
        g.fill(cx - 1, cy - bh - 2, cx + 2, cy - bh - 1, argb(0.45, t.text));

        double applied = 0.0;
        double actual = 0.0;
        int driven = 0;
        int actualCount = 0;
        double worstSlip = 0.0;
        int slipCount = 0;
        double leastGrip = Double.NaN;
        for (DriverTelemetryPacket.Wheel w : wheels) {
            final int x = cx + Math.round((w.lat() - latC) * scale);
            final int y = cy - Math.round((w.lon() - lonC) * scale);
            if (!Float.isNaN(w.grip()) && (Double.isNaN(leastGrip) || w.grip() < leastGrip)) {
                leastGrip = w.grip();
            }
            if (w.driven()) {
                applied += Math.abs(w.applied());
                driven++;
                if (!Float.isNaN(w.actual())) {
                    actual += w.actual();
                    actualCount++;
                }
            }
            if (!w.contact()) {
                outline(g, x - 3, y - 5, x + 4, y + 6, argb(0.45, t.text));
                continue;
            }
            final int colour;
            final double alpha;
            if (!w.driven() || Float.isNaN(w.slip())) {
                colour = t.text;
                alpha = 0.55;
            } else {
                final double s = Math.abs(w.slip());
                colour = s < 0.10 ? t.ok : s < WARN_SLIP ? t.warn : t.bad;
                alpha = Float.isNaN(w.grip()) ? 1.0 : 0.45 + 0.55 * Math.min(1.0, w.grip());
                slipCount++;
                if (Math.abs(w.slip()) > Math.abs(worstSlip)) {
                    worstSlip = w.slip();
                }
            }

            g.fill(x - 3, y - 4, x + 4, y + 5, argb(alpha, colour));
            g.fill(x - 2, y - 5, x + 3, y + 6, argb(alpha, colour));

            final int side = w.lat() >= latC ? x + 5 : x - 5;
            final int fill = (int) Math.round(Math.max(0.0, Math.min(1.0, w.load())) * 11);
            g.fill(side, y - 5, side + 1, y + 6, argb(0.16, t.text));
            if (fill > 0) {
                g.fill(side, y + 6 - fill, side + 1, y + 6, argb(0.70, t.text));
            }
        }

        final int r1 = y0 + 61;
        final int r2 = y0 + 71;
        final int mid = (x0 + x1) / 2;
        final double s = Math.abs(worstSlip);
        cell(g, font, "App", driven == 0 ? "--" : String.format(Locale.ROOT, "%.0f", applied / driven),
                x0 + 6, mid - 3, r1, t.primary, t);
        cell(g, font, "Act", actualCount == 0 ? "--" : String.format(Locale.ROOT, "%.0f", actual / actualCount),
                mid + 3, x1 - 6, r1, t.primary, t);
        cell(g, font, "Slip", slipCount == 0 ? "--" : String.format(Locale.ROOT, "%+.0f%%", worstSlip * 100.0),
                x0 + 6, mid - 3, r2, s < 0.10 ? t.ok : s < WARN_SLIP ? t.warn : t.bad, t);
        cell(g, font, "Grip", Double.isNaN(leastGrip) ? "--"
                        : String.format(Locale.ROOT, "%.0f%%", Math.min(1.0, leastGrip) * 100.0), mid + 3, x1 - 6, r2,
                Double.isNaN(leastGrip) || leastGrip >= 0.8 ? t.primary : leastGrip >= 0.5 ? t.warn : t.bad, t);
    }

    private static void cell(GuiGraphics g, Font font, String name, String val, int x0, int x1, int y, int rgb,
                             HudTheme t) {
        label(g, font, name, x0, y, t);
        g.drawString(font, val, x1 - font.width(val), y, argb(1.0, rgb), false);
    }

    private static void gPlate(GuiGraphics g, Font font, HudLayout at, @Nullable DriverTelemetryPacket tel,
                               HelmetVisor visor, HudTheme t) {
        final int x0 = at.gX0;
        final int x1 = at.gX1;
        final int y0 = at.gY0;
        plate(g, t, x0, y0, x1, at.gY1);
        g.drawString(font, "G", x0 + 6, y0 + 4, argb(0.62, t.text), false);
        final int cx = (x0 + x1) / 2;
        final int cy = y0 + 45;
        final int r = 30;
        ring(g, cx, cy, r, 48, argb(0.22, t.text));
        ring(g, cx, cy, r / 2, 28, argb(0.16, t.text));
        for (int i = -r; i <= r; i += 4) {
            g.fill(cx + i, cy, cx + i + 1, cy + 1, argb(0.12, t.text));
            g.fill(cx, cy + i, cx + 1, cy + i + 1, argb(0.12, t.text));
        }
        double lat = 0.0;
        double lon = 0.0;
        if (tel != null) {
            for (int i = 0; i < trailSize; i++) {
                final int idx = (trailHead - trailSize + i + TRAIL) % TRAIL;
                final int px = cx + (int) Math.round(clamp(TRAIL_LAT[idx], 1.3) * r);
                final int py = cy - (int) Math.round(clamp(TRAIL_LON[idx], 1.3) * r);
                g.fill(px, py, px + 2, py + 2, argb(0.08 + 0.4 * (i + 1) / (double) trailSize, t.cool));
            }
            final double k = previous == null ? 1.0
                    : Math.min(1.0, (System.nanoTime() - sampledNanos) / 250_000_000.0);
            lat = previous == null ? tel.latG() : previous.latG() + (tel.latG() - previous.latG()) * k;
            lon = previous == null ? tel.lonG() : previous.lonG() + (tel.lonG() - previous.lonG()) * k;
            final int px = cx + (int) Math.round(clamp(lat, 1.3) * r);
            final int py = cy - (int) Math.round(clamp(lon, 1.3) * r);
            g.fill(px - 2, py - 2, px + 3, py + 3, argb(0.35, t.primary));
            g.fill(px - 1, py - 1, px + 2, py + 2, argb(1.0, t.primary));
            final String total = String.format(Locale.ROOT, "%.2fg", Math.hypot(lat, lon));
            g.drawString(font, total, x1 - 6 - font.width(total), y0 + 4, argb(1.0, t.primary), false);
        }

        row(g, font, "Lateral", tel == null ? "--" : String.format(Locale.ROOT, "%+.2f g", lat), x0, x1, y0 + 81, t);
        row(g, font, "Long", tel == null ? "--" : String.format(Locale.ROOT, "%+.2f g", lon), x0, x1, y0 + 90, t);
        row(g, font, "Vert", tel == null ? "--" : String.format(Locale.ROOT, "%.2f g", tel.vertG()), x0, x1, y0 + 99, t);
        row(g, font, "Peak 10s", tel == null ? "--" : String.format(Locale.ROOT, "%.2f g", tel.peakG()), x0, x1,
                y0 + 108, t);
        final double odo = tel == null ? visor.odometer() : tel.odometer();
        row(g, font, "Odo", km(odo), x0, x1, y0 + 120, t);
        row(g, font, "Trip", tel == null ? "--" : km(tel.trip()), x0, x1, y0 + 129, t);
        row(g, font, "Time", tel == null ? "--" : clock(tel.tripTicks()), x0, x1, y0 + 138, t);
        row(g, font, "Top", tel == null ? "--" : String.format(Locale.ROOT, "%.0f km/h", tel.topSpeed() * 3.6),
                x0, x1, y0 + 147, t);
        final int mid = (x0 + x1) / 2;
        cell(g, font, "Pitch", tel == null ? "--" : String.format(Locale.ROOT, "%+.0f°", tel.pitch()),
                x0 + 6, mid - 3, y0 + 159, t.primary, t);
        cell(g, font, "Roll", tel == null ? "--" : String.format(Locale.ROOT, "%+.0f°", tel.roll()),
                mid + 3, x1 - 6, y0 + 159, t.primary, t);
        final boolean flying = tel != null && tel.airNow() > 0.0f;
        final String air = tel == null ? "--" : flying ? String.format(Locale.ROOT, "%.1fs", tel.airNow())
                : tel.lastAir() > 0.0f ? String.format(Locale.ROOT, "%.1fs", tel.lastAir()) : "--";
        cell(g, font, "Air", air, x0 + 6, mid - 3, y0 + 171, flying ? t.warn : t.primary, t);
        cell(g, font, "Hit", tel == null || tel.tripImpact() <= 0.0f ? "--"
                : String.format(Locale.ROOT, "%.1fg", tel.tripImpact()), mid + 3, x1 - 6, y0 + 171, t.primary, t);
    }

    private static String km(double metres) {
        final double km = metres / 1000.0;
        return String.format(Locale.ROOT, km < 10.0 ? "%.2f km" : km < 1000.0 ? "%.1f km" : "%.0f km", km);
    }

    private static String clock(int ticks) {
        final int s = ticks / 20;
        return s >= 3600 ? String.format(Locale.ROOT, "%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60)
                : String.format(Locale.ROOT, "%02d:%02d", s / 60, s % 60);
    }

    private static void plate(GuiGraphics g, HudTheme t, int x0, int y0, int x1, int y1) {
        g.fill(x0, y0, x1, y1, argb(t.plateAlpha, t.plate));
        g.fill(x0 + 1, y0, x1 - 1, y0 + 1, argb(0.67, t.stripe));
    }

    private static int label(GuiGraphics g, Font font, String text, int x, int y, HudTheme t) {
        g.drawString(font, text, x, y, argb(0.60, t.text), false);
        return x + font.width(text);
    }

    private static void row(GuiGraphics g, Font font, String name, String val, int x0, int x1, int y, HudTheme t) {
        label(g, font, name, x0 + 6, y, t);
        g.drawString(font, val, x1 - 6 - font.width(val), y, argb(1.0, t.primary), false);
    }

    private static void ring(GuiGraphics g, int cx, int cy, int r, int dots, int argb) {
        for (int i = 0; i < dots; i++) {
            final double a = 2.0 * Math.PI * i / dots;
            final int x = cx + (int) Math.round(Math.cos(a) * r);
            final int y = cy + (int) Math.round(Math.sin(a) * r);
            g.fill(x, y, x + 1, y + 1, argb);
        }
    }

    private static void outline(GuiGraphics g, int x0, int y0, int x1, int y1, int argb) {
        g.fill(x0, y0, x1, y0 + 1, argb);
        g.fill(x0, y1 - 1, x1, y1, argb);
        g.fill(x0, y0 + 1, x0 + 1, y1 - 1, argb);
        g.fill(x1 - 1, y0 + 1, x1, y1 - 1, argb);
    }

    private static void led(GuiGraphics g, String text, int x, int y, int argb, int dot, int gap, int spacing,
                            float ghost) {
        final int step = dot + gap;
        final int ghostArgb = ((int) (((argb >>> 24) & 0xFF) * ghost) << 24) | (argb & 0xFFFFFF);
        int cx = x;
        for (int i = 0; i < text.length(); i++) {
            final char c = text.charAt(i);
            for (int r = 0; r < LedFont.ROWS; r++) {
                for (int col = 0; col < LedFont.COLS; col++) {
                    final int px = cx + col * step;
                    final int py = y + r * step;
                    if (LedFont.lit(c, col, r)) {
                        g.fill(px, py, px + dot, py + dot, argb);
                    } else if (ghost > 0.0f) {
                        g.fill(px, py, px + dot, py + dot, ghostArgb);
                    }
                }
            }
            cx += LedFont.COLS * step - gap + spacing * dot;
        }
    }

    private static double clamp(double v, double limit) {
        return Math.max(-limit, Math.min(limit, v));
    }

    private static int argb(double alpha, int rgb) {
        return ((int) Math.round(Math.max(0.0, Math.min(1.0, alpha)) * 255) << 24) | (rgb & 0xFFFFFF);
    }
}
