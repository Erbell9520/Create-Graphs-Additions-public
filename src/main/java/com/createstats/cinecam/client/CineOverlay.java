package com.createstats.cinecam.client;

import com.createstats.cinecam.CineCameraBE;
import com.createstats.cinecam.core.CineHead;
import com.createstats.cinecam.core.CineMode;
import com.createstats.cinecam.core.FilmFormat;
import com.createstats.cinecam.core.FocusPull;
import com.createstats.cinecam.core.PhotoPrint;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.Items;

import java.util.Locale;

// the real kinotheodolite burned az/el, frame count and time onto every frame. same four here, live
public final class CineOverlay {
    private static final int INK = 0xFFE8E4DA;
    private static final int DIM = 0xFF9A968E;
    private static final int OK = 0xFF6FE36F;
    private static final int WARN = 0xFFE4B763;
    private static final int TALLY_ON = 0xFFE03030;
    private static final int TALLY_OFF = 0xFF502828;
    private static final int NOTCH_OFF = 0x5A9A968E;
    private static final int AF_MARK = 0xB0E8E4DA;
    private static final int BACKING = 0x90000000;
    private static final int REC = 0xFFFF5A5A;

    private static final long SAVED_SHOWN_MS = 6000;

    private CineOverlay() {
    }

    public static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        CineCameraBE cam = CineView.cameraBE();
        if (cam != null && CineView.inView()) {
            view(g, mc, cam);
        } else if (cam != null && CineView.away()) {
            awayBadge(g, mc, cam);
        }

        float fade = CineView.fadeAlpha();
        if (fade > 0.004f) g.fill(0, 0, g.guiWidth(), g.guiHeight(), Math.round(fade * 255f) << 24);
    }

    private static void view(GuiGraphics g, Minecraft mc, CineCameraBE cam) {
        Font font = mc.font;
        int w = g.guiWidth();
        int h = g.guiHeight();
        CineMode mode = cam.cineMode();
        double band = letterbox(g, mc);
        if (mode == CineMode.FILM) {
            if (CineView.filmHud) filmHud(g, font, cam, w, h, band);
            return;
        }
        if (mode == CineMode.PHOTO) {
            photoFrame(g, font, w, h, mc, cam);
        } else {
            vignette(g, w, h);
            reticle(g, w, h, mode.colour());
        }
        double[] d = CineView.sight(cam);
        String az = String.format(Locale.ROOT, "AZ %05.1f°", CineHead.bearing(d[0], d[2]));
        String el = String.format(Locale.ROOT, "EL %+05.1f°", CineHead.elevation(d[0], d[1], d[2]));
        g.drawString(font, az + "   " + el, 8, 8, INK, true);
        String fr = String.format(Locale.ROOT, "FR %06d", cam.frames());
        g.drawString(font, fr, w - 8 - font.width(fr), 8, INK, true);
        if (CineView.lockedAtMs >= 0) {
            String t = sinceLock();
            g.drawString(font, t, w - 8 - font.width(t), 19, INK, true);
        }
        if (cam.feConsumer().isBrownout()) {
            Component low = Component.translatable("create_stats.cine_camera.low_power");
            g.drawString(font, low, w - 8 - font.width(low), 30, WARN, true);
        }
        if (CineView.zoom > 1.05 || CineView.autoHolding) {
            String z = zoomLabel();
            g.drawString(font, z, w - 8 - font.width(z), h / 2 - 4, DIM, true);
        }

        int y = h - 58;
        g.drawString(font, targetLine(cam), 8, y, INK, true);
        y += 11;
        if (cam.status() == CineCameraBE.TRACKING) {
            g.drawString(font, rangeLine(cam), 8, y, INK, true);

            String sp = String.format(Locale.ROOT, "SPD ~%.1f b/s   CLB %+.1f   HDG %03.0f", cam.speed(), cam.climb(), cam.heading());
            g.drawString(font, sp, 8, y + 11, INK, true);
        } else if (cam.runSeconds() > 0f) {
            MutableComponent run = Component.translatable("create_stats.cine_camera.last_run",
                    cam.runName().isEmpty() ? "?" : cam.runName(),
                    String.format(Locale.ROOT, "%.0f", cam.runSeconds()),
                    String.format(Locale.ROOT, "%.0f", cam.runDistance()),
                    String.format(Locale.ROOT, "%.1f", cam.runTopSpeed()),
                    String.format(Locale.ROOT, "%.0f", cam.runTopHeight()));
            g.drawString(font, run, 8, y, DIM, true);
        }
        modeStrip(g, font, w, h, mode);
        Component keys = Component.translatable("create_stats.cine_camera.keys");
        g.drawString(font, keys, (w - font.width(keys)) / 2, h - 12, DIM, true);
    }

    private static String sinceLock() {
        double s = (System.currentTimeMillis() - CineView.lockedAtMs) / 1000.0;
        return String.format(Locale.ROOT, "T+%02d:%04.1f", (int) (s / 60), s % 60);
    }

    private static double letterbox(GuiGraphics g, Minecraft mc) {
        int ww = mc.getWindow().getWidth();
        int wh = mc.getWindow().getHeight();
        int px = (int) Math.round(CineView.band * Math.max(0.0, (wh - ww / CineView.aspect) / 2.0));
        if (px <= 0) return 0.0;
        double scale = mc.getWindow().getGuiScale();
        g.pose().pushPose();
        g.pose().scale((float) (1.0 / scale), (float) (1.0 / scale), 1f);
        g.fill(0, 0, ww, px, 0xFF000000);
        g.fill(0, wh - px, ww, wh, 0xFF000000);
        g.pose().popPose();
        return px / scale;
    }

    private static void filmHud(GuiGraphics g, Font font, CineCameraBE cam, int w, int h, double band) {
        int top = band >= 12 ? (int) ((band - 8) / 2) : 4;
        if (band < 12) g.fill(0, 0, w, 16, BACKING);
        int x = 8;
        CineRecorder.State state = CineRecorder.state();
        long ms = System.currentTimeMillis();
        int lamp = switch (state) {
            case RECORDING -> (ms / 500) % 2 == 0 ? TALLY_ON : TALLY_OFF;
            case STARTING, SAVING -> WARN;
            default -> TALLY_OFF;
        };
        g.fill(x, top + 1, x + 5, top + 6, lamp);
        x += 9;
        Component label = switch (state) {
            case RECORDING -> Component.literal("REC " + filmTime(CineRecorder.filmedMs())).withColor(REC);
            case STARTING -> Component.translatable("create_stats.cine_camera.film.starting").withColor(WARN);
            case SAVING -> Component.translatable("create_stats.cine_camera.film.saving").withColor(WARN);
            default -> Component.literal("STBY").withColor(DIM);
        };
        g.drawString(font, label, x, top, INK, true);
        x += font.width(label) + 12;
        Component speed = speedTag(cam);
        if (speed != null) {
            g.drawString(font, speed, x, top, CineMode.FILM.colour(), true);
            x += font.width(speed) + 12;
        }
        if (cam.feConsumer().isBrownout()) {
            Component low = Component.translatable("create_stats.cine_camera.low_power");
            g.drawString(font, low, x, top, WARN, true);
            x += font.width(low) + 12;
        }
        Component film = Component.translatable("create_stats.cine_camera.mode.film");
        g.drawString(font, film, x, top, CineMode.FILM.colour(), true);
        x += font.width(film) + 10;
        String fr = String.format(Locale.ROOT, "FR %06d", cam.frames());
        g.drawString(font, fr, x, top, INK, true);
        x += font.width(fr) + 10;
        if (CineView.lockedAtMs >= 0) g.drawString(font, sinceLock(), x, top, INK, true);
        int right = w - 8;
        FilmFormat.Shape taking = CineRecorder.shape();
        String shape = (taking != null ? taking : cam.filmShape()).label();
        right -= font.width(shape);
        boolean denied = CineView.aspectDeniedAtMs >= 0 && ms - CineView.aspectDeniedAtMs < 1000;
        g.drawString(font, shape, right, top, denied ? WARN : DIM, true);
        String zoom = zoomLabel();
        right -= 10 + font.width(zoom);
        g.drawString(font, zoom, right, top, CineView.zoom > 1.05 || CineView.autoHolding ? INK : DIM, true);
        String format = formatLabel(cam);
        right -= 10 + font.width(format);
        g.drawString(font, format, right, top, state == CineRecorder.State.RECORDING ? INK : DIM, true);

        int rows = 19;
        int y1;
        if (band >= rows + 4) {
            y1 = (int) (h - band + (band - rows) / 2);
        } else {
            y1 = h - 4 - rows;
            g.fill(0, y1 - 4, w, h, BACKING);
        }
        g.drawString(font, targetLine(cam), 8, y1, INK, true);
        blurReadout(g, font, w, y1);
        String saved = CineRecorder.savedName;
        if (saved != null && state == CineRecorder.State.IDLE && ms - CineRecorder.savedAtMs < SAVED_SHOWN_MS) {
            Component s = Component.translatable("create_stats.cine_camera.film.saved_hud", saved);
            g.drawString(font, s, (w - font.width(s)) / 2, y1, OK, true);
        }
        Component keys = Component.translatable("create_stats.cine_camera.film_keys",
                Minecraft.getInstance().options.keyInventory.getTranslatedKeyMessage());
        if (font.width(keys) <= w - 16) {
            g.drawString(font, keys, (w - font.width(keys)) / 2, y1 + 11, DIM, true);
        } else {
            String[] parts = keys.getString().split(" {3}");
            int half = (parts.length + 1) / 2;
            String a = String.join("   ", java.util.Arrays.copyOfRange(parts, 0, half));
            String b = String.join("   ", java.util.Arrays.copyOfRange(parts, half, parts.length));
            g.drawString(font, a, (w - font.width(a)) / 2, y1 + 11, DIM, true);
            g.drawString(font, b, (w - font.width(b)) / 2, y1 + 22, DIM, true);
        }
        if (FocusPull.on(CineView.tStop)) focusMarks(g, w, h);
    }

    private static void awayBadge(GuiGraphics g, Minecraft mc, CineCameraBE cam) {
        Font font = mc.font;
        long ms = System.currentTimeMillis();
        CineRecorder.State state = CineRecorder.state();
        Component label = switch (state) {
            case RECORDING -> Component.literal("REC " + filmTime(CineRecorder.filmedMs())).withColor(REC);
            case STARTING -> Component.translatable("create_stats.cine_camera.film.starting").withColor(WARN);
            case SAVING -> Component.translatable("create_stats.cine_camera.film.saving").withColor(WARN);
            default -> Component.literal("STBY").withColor(DIM);
        };
        net.minecraft.world.phys.Vec3 t = cam.trunnionWorld();
        int blocks = t == null || mc.player == null ? 0 : (int) Math.round(mc.player.getEyePosition().distanceTo(t));

        boolean far = blocks > CineCameraBE.AWAY_RANGE * 0.75;
        Component where = Component.translatable("create_stats.cine_camera.film.away_badge", blocks).withColor(far ? WARN : DIM);
        Component stop = Component.translatable("create_stats.cine_camera.film.remote_stop",
                CineClientSetup.RECORD.getTranslatedKeyMessage()).withColor(DIM);
        String format = formatLabel(cam);
        Component speed = speedTag(cam);
        int x = 8;
        int y = 8;
        int w = 9 + font.width(label) + 8 + (speed == null ? 0 : font.width(speed) + 8) + font.width(format) + 8
                + font.width(where) + 8 + font.width(stop);
        g.fill(x - 4, y - 4, x + w + 4, y + 12, BACKING);
        int lamp = state == CineRecorder.State.RECORDING ? ((ms / 500) % 2 == 0 ? TALLY_ON : TALLY_OFF) : WARN;
        g.fill(x, y + 1, x + 5, y + 6, lamp);
        int at = x + 9;
        g.drawString(font, label, at, y, INK, true);
        at += font.width(label) + 8;
        if (speed != null) {
            g.drawString(font, speed, at, y, CineMode.FILM.colour(), true);
            at += font.width(speed) + 8;
        }
        g.drawString(font, format, at, y, INK, true);
        at += font.width(format) + 8;
        g.drawString(font, where, at, y, DIM, true);
        at += font.width(where) + 8;
        g.drawString(font, stop, at, y, DIM, true);
    }

    private static String zoomLabel() {
        String z = String.format(Locale.ROOT, "x%.1f", CineView.zoom);
        return CineView.autoHolding ? "AUTO " + z : z;
    }

    @javax.annotation.Nullable
    private static Component speedTag(CineCameraBE cam) {
        FilmFormat.Speed taking = CineRecorder.speed();
        FilmFormat.Speed v = taking != null ? taking : cam.filmSpeed();
        if (v == FilmFormat.Speed.NORMAL) return null;
        return Component.translatable(v.slow() ? "create_stats.cine_camera.hud.slow" : "create_stats.cine_camera.hud.lapse",
                v.label());
    }

    private static String formatLabel(CineCameraBE cam) {
        int[] size = CineRecorder.size();
        int fps = CineRecorder.fps();
        if (size == null) {
            Minecraft mc = Minecraft.getInstance();
            size = FilmFormat.size(cam.filmResolution(), cam.filmShape().aspect(),
                    mc.getMainRenderTarget().width, mc.getMainRenderTarget().height);
            fps = cam.filmRate();
        }
        return FilmFormat.label(size[0], size[1]) + " " + fps + " FPS";
    }

    private static void blurReadout(GuiGraphics g, Font font, int w, int y) {
        double t = CineView.tStop;
        int rung = FocusPull.rung(t);
        boolean changed = CineView.irisAtMs >= 0 && System.currentTimeMillis() - CineView.irisAtMs < CineView.IRIS_SHOWN_MS;
        Component label = rung == 0 ? Component.translatable("create_stats.cine_camera.blur_off")
                : Component.translatable("create_stats.cine_camera.blur_on", stop(FocusPull.clampStop(t)));
        int colour = changed ? CineMode.FILM.colour() : rung == 0 ? DIM : INK;
        int lx = w - 8 - font.width(label);
        g.drawString(font, label, lx, y, colour, true);
        int nx = lx - 6 - (FocusPull.LADDER.length - 1) * 3 + 1;
        for (int i = 1; i < FocusPull.LADDER.length; i++) {
            g.fill(nx, y + 1, nx + 2, y + 7, i <= rung ? colour : NOTCH_OFF);
            nx += 3;
        }
    }

    private static String filmTime(long ms) {
        long s = ms / 1000;
        return s >= 3600 ? String.format(Locale.ROOT, "%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60)
                : String.format(Locale.ROOT, "%02d:%02d", s / 60, s % 60);
    }

    private static String stop(double t) {
        return t == Math.rint(t) ? Integer.toString((int) t) : String.format(Locale.ROOT, "%.1f", t);
    }

    private static void focusMarks(GuiGraphics g, int w, int h) {
        int cx = w / 2;
        int cy = h / 2;
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sy = -1; sy <= 1; sy += 2) {
                int x0 = sx < 0 ? cx - 12 : cx + 11;
                int y0 = sy < 0 ? cy - 8 : cy + 7;
                int hx = sx < 0 ? x0 : x0 - 3;
                int vy = sy < 0 ? y0 : y0 - 3;
                g.fill(hx, y0, hx + 4, y0 + 1, AF_MARK);
                g.fill(x0, vy, x0 + 1, vy + 4, AF_MARK);
            }
        }
    }

    private static Component targetLine(CineCameraBE cam) {
        MutableComponent status = switch (cam.status()) {
            case CineCameraBE.TRACKING -> Component.translatable("create_stats.cine_camera.status.locked").withColor(OK);
            case CineCameraBE.LOST -> Component.translatable("create_stats.cine_camera.status.seeking").withColor(WARN);
            default -> Component.translatable("create_stats.cine_camera.status.none").withColor(DIM);
        };
        if (cam.targetKind() == CineCameraBE.KIND_NONE) return status;
        return Component.literal(cam.targetName().isEmpty() ? "?" : cam.targetName()).append("  ").append(status);
    }

    private static Component rangeLine(CineCameraBE cam) {
        return Component.literal(String.format(Locale.ROOT, "RNG %.0f ±%.0f   HGT %.1f", cam.range(),
                Math.max(1f, cam.rangeSigma()), cam.height()));
    }

    private static void modeStrip(GuiGraphics g, Font font, int w, int h, CineMode active) {
        int x = w - 8;
        CineMode[] all = CineMode.values();
        for (int i = all.length - 1; i >= 0; i--) {
            Component c = Component.literal((i + 1) + " ").append(Component.translatable("create_stats.cine_camera.mode." + all[i].key()));
            int cw = font.width(c);
            x -= cw;
            g.drawString(font, c, x, h - 26, all[i] == active ? all[i].colour() : DIM, true);
            x -= 8;
        }
    }

    private static void vignette(GuiGraphics g, int w, int h) {
        int edge = Math.max(12, h / 12);
        g.fillGradient(0, 0, w, edge, 0x90000000, 0x00000000);
        g.fillGradient(0, h - edge, w, h, 0x00000000, 0x90000000);
    }

    private static void reticle(GuiGraphics g, int w, int h, int colour) {
        int cx = w / 2;
        int cy = h / 2;
        int c = (colour & 0x00FFFFFF) | 0xE0000000;
        g.fill(cx - 12, cy, cx - 3, cy + 1, c);
        g.fill(cx + 4, cy, cx + 13, cy + 1, c);
        g.fill(cx, cy - 12, cx + 1, cy - 3, c);
        g.fill(cx, cy + 4, cx + 1, cy + 13, c);

        for (int k = 2; k <= 5; k++) {
            g.fill(cx - 10 * k, cy - 1, cx - 10 * k + 1, cy + 2, c & 0x80FFFFFF);
            g.fill(cx + 10 * k, cy - 1, cx + 10 * k + 1, cy + 2, c & 0x80FFFFFF);
        }
    }

    private static void photoFrame(GuiGraphics g, Font font, int w, int h, Minecraft mc, CineCameraBE cam) {
        int side = Math.min(w, h) - 16;
        int x0 = (w - side) / 2;
        int y0 = (h - side) / 2;
        int shade = 0x70000000;
        g.fill(0, 0, x0, h, shade);
        g.fill(x0 + side, 0, w, h, shade);
        int arm = side / 10;
        int c = 0xE0FFFFFF;
        for (int[] k : new int[][]{{x0, y0, 1, 1}, {x0 + side, y0, -1, 1}, {x0, y0 + side, 1, -1}, {x0 + side, y0 + side, -1, -1}}) {
            int x = k[0];
            int y = k[1];
            g.fill(Math.min(x, x + k[2] * arm), y - (k[3] < 0 ? 1 : 0), Math.max(x, x + k[2] * arm), y + (k[3] > 0 ? 1 : 0), c);
            g.fill(x - (k[2] < 0 ? 1 : 0), Math.min(y, y + k[3] * arm), x + (k[2] > 0 ? 1 : 0), Math.max(y, y + k[3] * arm), c);
        }

        int maps = mc.player == null ? 0 : mc.player.getInventory().countItem(Items.MAP);
        PhotoPrint.Target target = cam.photoTarget();
        int need = PhotoPrint.mapsFor(cam.photoSide());
        Component hint = switch (target) {
            case FILE -> Component.translatable("create_stats.cine_camera.photo.hint_file");
            case MAP -> Component.translatable("create_stats.cine_camera.photo.hint", need, maps);
            case BOTH -> Component.translatable("create_stats.cine_camera.photo.hint_both", need, maps);
        };
        boolean short_ = target.maps() && maps < need && (mc.player == null || !mc.player.getAbilities().instabuild);
        g.drawString(font, hint, (w - font.width(hint)) / 2, y0 + side - 14, short_ ? WARN : DIM, true);
        if (CineView.flash > 0f) {
            int a = (int) (CineView.flash * 200) << 24;
            g.fill(0, 0, w, h, a | 0xFFFFFF);
        }
    }
}
