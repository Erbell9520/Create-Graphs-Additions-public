package com.createstats.gui.security;

import com.createstats.network.SecuritySyncPacket;
import com.createstats.power.SecurityDevice;
import com.createstats.security.ModeEffects;
import com.createstats.security.SecurityMode;
import com.createstats.security.SecurityRole;
import com.createstats.statsmk3.ui.Easing;
import com.createstats.statsmk3.ui.Motion;
import com.createstats.statsmk3.ui.PanelSurface;
import com.createstats.statsmk3.ui.Rect;
import com.createstats.statsmk3.ui.Spring;
import com.createstats.statsmk3.ui.Text;
import com.createstats.statsmk3.ui.Tokens;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;

@OnlyIn(Dist.CLIENT)
public final class OverviewView extends SecurityView {
    private boolean effectsOpen = true;

    private SecurityMode previewing;

    private SecurityMode shownMode;
    private SecurityMode fromMode;

    private float sinceMode = 1.0f;

    private float sinceOpen = 1.0f;

    private final Spring openAmount = Spring.ui(1.0f);

    public OverviewView(SecurityContext ctx) {
        super(ctx);
    }

    // every view gets updated every frame, that missing line is why nothing scrolled once
    @Override
    public void update(float dt) {
        this.openAmount.step(dt);
        this.sinceMode = Math.min(4.0f, this.sinceMode + dt);
        this.sinceOpen = Math.min(4.0f, this.sinceOpen + dt);
    }

    @Override
    public String id() {
        return "overview";
    }

    @Override
    public String label() {
        return "Overview";
    }

    @Override
    public void drawIcon(PanelSurface s, float cx, float cy, int color) {
        SecurityIcons.house(s, cx, cy, color);
    }

    @Override
    public void draw(PanelSurface s, float mx, float my) {
        pushHitClip(this.area);
        try {
            drawBody(s, mx, my);
        } finally {
            popHitClip();
        }
    }

    private void drawBody(PanelSurface s, float mx, float my) {
        this.previewing = null;
        final SecuritySyncPacket view = this.ctx.view();
        final List<SecuritySyncPacket.Row> devices = this.ctx.devices();

        int breached = 0;
        int offline = 0;
        for (SecuritySyncPacket.Row row : devices) {
            if (row.stateOf() == SecurityDevice.DeviceState.BREACHED) {
                breached++;
            } else if (row.stateOf() == SecurityDevice.DeviceState.OFFLINE) {
                offline++;
            }
        }

        int blind = 0;
        for (SecuritySyncPacket.Row row : devices) {
            if (SecurityContext.blind(row)) {
                blind++;
            }
        }
        final boolean lockdown = this.ctx.lockedDown();
        final boolean fault = breached > 0 || offline > 0 || blind > 0;

        float y = this.area.y;
        s.text("Overview", this.area.x, y, SecurityTheme.TEXT, Text.SUB);
        s.textRight(Text.track(view.awake() ? "SCANNING" : "NO SUPPLY, SHOWING LAST SCAN"),
                this.area.right(), y + 4.0f, SecurityTheme.TEXT_FAINT, SecurityTheme.MICRO);
        y += Text.lineHeight(Text.SUB) + Tokens.S2;

        final Rect banner = new Rect(this.area.x, y, this.area.w, 46.0f);

        final boolean hazard = this.ctx.hazard();
        s.roundedRect(banner, Tokens.R_CARD,
                lockdown ? SecurityTheme.RED
                        : this.ctx.released() ? SecurityTheme.AMBER
                        : hazard ? SecurityTheme.AMBER
                        : fault ? SecurityTheme.RED
                        : this.ctx.houseSealed() ? SecurityTheme.SEAL : SecurityTheme.GREEN);
        SecurityIcons.shield(s, banner.x + 22.0f, banner.centerY(), 0xFFFFFFFF);
        final boolean shut = this.ctx.houseSealed();
        final String headline = lockdown ? "LOCKDOWN"
                : this.ctx.released() ? "FIRE RELEASE"
                : hazard ? "HAZARD"
                : fault ? "ATTENTION"

                : shut ? "AWAY+" : "SECURE";
        s.text(headline, banner.x + 40.0f, banner.y + 9.0f, 0xFFFFFFFF, Text.SUB);
        final String reason;
        if (lockdown) {
            final String cause = this.ctx.lockdownCause();
            reason = (cause == null || cause.isBlank() ? "A sensor tripped" : "Tripped by " + cause)
                    + (hasEntrance()
                            ? ". Every door is sealed except the entrance."
                            : ". Every door is sealed and none is the entrance.");
        } else if (this.ctx.released()) {
            reason = "Fire detected. Doors are open to everyone except the entrance.";
        } else if (hazard) {
            final String cause = this.ctx.hazardCause();
            reason = (cause == null || cause.isBlank() ? "A hazard sensor tripped"
                    : "A hazard sensor found " + cause)
                    + ". Doors are unaffected, this is a warning.";
        } else if (fault) {
            reason = faultLine(breached, offline, blind);
        } else if (shut) {
            reason = "The house is shut. Every door is owner only and opens on a click, "
                    + "and the password stands it down.";
        } else {
            reason = devices.isEmpty()
                    ? "Nothing found on this circuit yet"
                    : "Every device is holding its clearance";
        }
        s.text(Text.truncate(s, reason, banner.w - 48.0f, SecurityTheme.MICRO), banner.x + 40.0f,
                banner.y + 26.0f, 0xCCFFFFFF, SecurityTheme.MICRO);

        if (lockdown && !shut && this.ctx.mayEditPanel()) {
            final Rect clear = new Rect(banner.right() - 70.0f, banner.centerY() - 9.0f, 62.0f,
                    SecurityTheme.BTN_H);
            SecurityTheme.button(s, clear, "Clear", true, hot(clear, mx, my), true);
            hit(clear, this.ctx::clearLockdown);
        }
        y += banner.h + Tokens.S3;
        y = drawNotices(s, y);

        y = drawModes(s, y, mx, my);
        y = drawEffects(s, y, mx, my);
        y = drawShutterMaster(s, y, mx, my);
        y = drawRepellerMaster(s, y, mx, my);
        y = drawCoilMaster(s, y, mx, my);

        final float cardW = (this.area.w - Tokens.S2 * 3.0f) / 4.0f;
        final float cardH = 48.0f;
        stat(s, new Rect(this.area.x, y, cardW, cardH), "DOORS",
                this.ctx.devicesOf(SecurityDevice.DeviceClass.DOOR).size(),
                breached > 0 ? breached + " breached" : "all holding", breached > 0);
        stat(s, new Rect(this.area.x + cardW + Tokens.S2, y, cardW, cardH), "PEOPLE",
                this.ctx.people().size(), rosterFoot(), false);
        final int sensors = this.ctx.devicesOf(SecurityDevice.DeviceClass.SENSOR).size();
        stat(s, new Rect(this.area.x + (cardW + Tokens.S2) * 2.0f, y, cardW, cardH), "SENSORS",
                sensors,
                blind > 0 ? blind + " blind, no power"
                        : sensors == 0 ? "none on this circuit"
                        : watchingCount() + " of " + sensors + " watching",
                blind > 0);

        stat(s, new Rect(this.area.x + (cardW + Tokens.S2) * 3.0f, y, cardW, cardH), "DRAW",
                view.awake() ? 1 : 0, "FE/t at this panel", false);
        y += cardH + Tokens.S3;

        final float rest = this.area.bottom() - y;
        if (rest < 24.0f) {
            return;
        }
        final Rect activity = new Rect(this.area.x, y, this.area.w, rest);
        s.card(activity, Tokens.R_CARD, SecurityTheme.CARD, SecurityTheme.LINE);
        final Rect inner = activity.inset(Tokens.S3);
        SecurityTheme.head(s, inner.x, inner.y, "RECENT ACTIVITY");
        float ly = inner.y + Text.lineHeight(SecurityTheme.MICRO) + 4.0f;

        final List<SecuritySyncPacket.Line> log = this.ctx.log();
        if (log.isEmpty()) {
            if (ly + 12.0f < inner.bottom()) {
                s.textV("Nothing yet. Access decisions and edits are recorded here.", inner.x,
                        ly + 6.0f, SecurityTheme.TEXT_FAINT, SecurityTheme.MICRO);
            }
            return;
        }
        for (int i = 0; i < log.size() && ly + 12.0f < inner.bottom(); i++) {
            LogView.drawLine(s, this.ctx, new Rect(inner.x, ly, inner.w, 12.0f), log.get(i), false);
            ly += 12.0f;
        }
    }

    private float drawNotices(PanelSurface s, float y) {
        final List<String> lines = new ArrayList<>();
        if (this.ctx.hiddenRows() > 0) {
            lines.add(net.minecraft.network.chat.Component.translatable(
                    "gui.create_stats.security.relays.notice.hidden", this.ctx.hiddenRows()).getString());
        }
        if (this.ctx.legacyUnresolved() > 0) {
            lines.add(net.minecraft.network.chat.Component.translatable(
                    "gui.create_stats.security.relays.notice.legacy", this.ctx.legacyUnresolved()).getString());
        }
        if (lines.isEmpty()) {
            return y;
        }
        final List<String> wrapped = new ArrayList<>();
        for (String line : lines) {
            wrapped.addAll(Text.wrap(s, line, this.area.w - Tokens.S3 * 2.0f, SecurityTheme.MICRO));
        }
        final float lh = Text.lineHeight(SecurityTheme.MICRO) + 2.0f;
        final float h = Tokens.S2 * 2.0f + wrapped.size() * lh - 2.0f;
        final Rect card = new Rect(this.area.x, y, this.area.w, h);
        s.card(card, Tokens.R_CHIP, SecurityTheme.AMBER_WASH, SecurityTheme.AMBER_EDGE);
        float ly = card.y + Tokens.S2;
        for (String line : wrapped) {
            s.text(line, card.x + Tokens.S3, ly, SecurityTheme.AMBER_INK, SecurityTheme.MICRO);
            ly += lh;
        }
        return y + h + Tokens.S3;
    }

    private float drawModes(PanelSurface s, float y, float mx, float my) {
        SecurityTheme.head(s, this.area.x, y, "MODE");
        y += Text.lineHeight(SecurityTheme.MICRO) + 4.0f;

        final SecurityMode current = this.ctx.mode();
        final boolean may = this.ctx.mayEditPanel();
        final SecurityMode[] modes = SecurityMode.all();
        final float gap = Tokens.S2;
        final float w = (this.area.w - gap * (modes.length - 1)) / modes.length;
        final float h = 30.0f;

        for (int i = 0; i < modes.length; i++) {
            final SecurityMode m = modes[i];
            final Rect r = new Rect(this.area.x + i * (w + gap), y, w, h);
            final boolean on = m == current;
            final boolean allowed = may && (!m.sealsHouse()
                    || (this.ctx.isOwner() && this.ctx.hasPassword()));
            final boolean warm = allowed && hot(r, mx, my);
            s.card(r, Tokens.R_CHIP,
                    on ? modeWash(m) : warm ? SecurityTheme.CARD_HOT : SecurityTheme.CANVAS,
                    on ? modeEdge(m) : SecurityTheme.LINE);
            final int ink = on ? modeInk(m) : allowed ? SecurityTheme.TEXT_DIM : SecurityTheme.OFF;
            s.textCenter(modeName(m), r.centerX(), r.y + 5.0f, ink, Text.BODY);
            s.textCenter(Text.truncate(s, footFor(m), r.w - Tokens.S2, SecurityTheme.MICRO),
                    r.centerX(), r.y + 17.0f,
                    on ? ink : SecurityTheme.TEXT_FAINT, SecurityTheme.MICRO);
            if (allowed && !on) {
                if (warm) {
                    this.previewing = m;
                }
                hit(r, () -> this.ctx.setMode(m));
            }
        }
        y += h + Tokens.S2;

        if (!this.ctx.devicesOf(SecurityDevice.DeviceClass.DOOR).isEmpty() && !hasEntrance()) {
            final Rect warn = new Rect(this.area.x, y, this.area.w, 24.0f);
            s.card(warn, Tokens.R_CHIP, SecurityTheme.AMBER_WASH, SecurityTheme.AMBER_EDGE);
            s.textV(Text.truncate(s, "No door is the entrance. A lockdown would seal every one of "
                            + "them, against you as well. Pick one on the Doors tab.",
                    warn.w - Tokens.S4, SecurityTheme.MICRO),
                    warn.x + Tokens.S2, warn.centerY(), SecurityTheme.AMBER_INK, SecurityTheme.MICRO);
            y += warn.h + Tokens.S1;
        }
        return y + Tokens.S3;
    }

    private static final float EFFECT_HEAD_H = 12.0f;

    private static final float EFFECT_GAP = 3.0f;

    private static final float EFFECT_ROW_H = 9.5f;

    private static final float EFFECT_GLYPH = 0.58f;

    private static final float EFFECT_GLYPH_X = 11.0f;
    private static final float EFFECT_LABEL_X = 19.0f;

    private static final float EFFECT_VALUE_X = 58.0f;

    private static final float EFFECT_NOTE_H = 10.0f;

    private float drawEffects(PanelSurface s, float y, float mx, float my) {
        final SecurityMode shown = this.previewing != null ? this.previewing : this.ctx.mode();
        final boolean previewing = this.previewing != null;

        if (shown != this.shownMode) {
            this.fromMode = this.shownMode == null ? shown : this.shownMode;
            this.shownMode = shown;
            this.sinceMode = 0.0f;
        }
        final ModeEffects.House house = houseFacts();
        final List<ModeEffects.Row> rows = ModeEffects.rows(shown, house);
        int changes = 0;
        for (ModeEffects.Row row : rows) {
            if (row.changed()) {
                changes++;
            }
        }

        final float open = Easing.clamp01(this.openAmount.value());
        final float fullBody = EFFECT_GAP + rows.size() * EFFECT_ROW_H + EFFECT_NOTE_H;
        final float bodyH = fullBody * open;
        final Rect card = new Rect(this.area.x, y, this.area.w,
                Tokens.S2 + EFFECT_HEAD_H + bodyH + EFFECT_GAP);

        final boolean live = this.ctx.view().awake();

        final float t = Motion.reducedMotion() ? 1.0f
                : Easing.easeOut(Easing.clamp01(this.sinceMode / Motion.VIEW_TRANSITION));
        final SecurityMode from = this.fromMode == null ? shown : this.fromMode;
        final int wash = live ? Tokens.mix(modeWash(from), modeWash(shown), t) : SecurityTheme.CANVAS;
        final int edge = live ? Tokens.mix(modeEdge(from), modeEdge(shown), t) : SecurityTheme.LINE;
        final int ink = live ? Tokens.mix(modeInk(from), modeInk(shown), t) : SecurityTheme.OFF;
        s.card(card, Tokens.R_CARD, wash, edge);

        final Rect inner = new Rect(card.x + Tokens.S3, card.y + Tokens.S2,
                card.w - Tokens.S3 * 2.0f, card.h - Tokens.S2 - EFFECT_GAP);
        final Rect header = new Rect(inner.x, inner.y, inner.w, EFFECT_HEAD_H);
        final float cy = header.centerY();

        final Rect grab = header.inset(-4.0f, -2.0f, -4.0f, -2.0f);
        if (hot(grab, mx, my)) {
            s.roundedRect(grab, Tokens.R_CHIP, 0x22FFFFFF);
        }

        s.roundedRect(new Rect(inner.x, cy - 2.5f, 5.0f, 5.0f), 1.5f, ink);
        final String name = Text.track(modeName(shown));
        s.textV(name, inner.x + 9.0f, cy, live ? ink : SecurityTheme.OFF, SecurityTheme.MICRO);
        final float postureX = inner.x + 9.0f + s.textWidth(name, SecurityTheme.MICRO) + Tokens.S2;

        final String count = Text.track(previewing
                ? "preview " + changes + "/" + rows.size()
                : changes + "/" + rows.size() + " changed");
        final float caretX = inner.right() - 5.0f;
        final float countW = s.textWidth(count, SecurityTheme.MICRO);
        final Rect countR = new Rect(caretX - 10.0f - countW - Tokens.S1, cy - 4.5f,
                countW + Tokens.S2, 9.0f);
        if (live) {
            s.roundedRect(countR, Tokens.R_CHIP, SecurityTheme.rowChip(ink));
        }
        s.textCenterV(count, countR.centerX(), cy, live ? ink : SecurityTheme.OFF,
                SecurityTheme.MICRO);

        s.textV(Text.truncate(s, ModeEffects.posture(shown, house),
                        Math.max(0.0f, countR.x - Tokens.S2 - postureX), SecurityTheme.MICRO),
                postureX, cy, live ? SecurityTheme.TEXT_DIM : SecurityTheme.OFF,
                SecurityTheme.MICRO);

        drawCaret(s, caretX, cy, open, live ? ink : SecurityTheme.OFF);
        hit(grab, () -> {
            this.effectsOpen = !this.effectsOpen;
            this.openAmount.set(this.effectsOpen ? 1.0f : 0.0f);

            this.sinceOpen = 0.0f;
        });

        if (bodyH <= 0.5f) {
            return card.bottom() + Tokens.S3;
        }

        final Rect body = new Rect(inner.x, header.bottom() + EFFECT_GAP, inner.w,
                Math.max(0.0f, bodyH - EFFECT_GAP));
        s.clip(new Rect(body.x - 2.0f, header.bottom(), body.w + 4.0f, bodyH));
        s.hairlineH(inner.x, header.bottom() + 1.0f, inner.w, edge);

        final float clock = Math.min(this.sinceMode, this.sinceOpen);
        float ry = body.y;
        for (int i = 0; i < rows.size(); i++) {
            final ModeEffects.Row row = rows.get(i);
            final float in = Motion.reducedMotion() ? 1.0f : Motion.stagger(clock, i);
            final Rect r = new Rect(body.x, ry + (1.0f - in) * 4.0f, body.w, EFFECT_ROW_H);
            final boolean lit = live && row.changed();
            if (lit) {
                s.roundedRect(new Rect(r.x, r.y + 0.5f, r.w * in, EFFECT_ROW_H - 1.0f),
                        Tokens.R_CHIP, SecurityTheme.rowChip(ink));

                final float bh = (EFFECT_ROW_H - 1.0f) * in;
                s.roundedRect(new Rect(r.x, r.centerY() - bh * 0.5f, 2.0f, bh), 1.0f,
                        Tokens.alpha(ink, in));
            }
            final int glyphInk = !live ? SecurityTheme.OFF
                    : Tokens.alpha(lit ? ink : SecurityTheme.TEXT_FAINT, in);
            final int labelInk = !live ? SecurityTheme.OFF
                    : Tokens.alpha(lit ? SecurityTheme.TEXT_DIM : SecurityTheme.TEXT_FAINT, in);
            final int valueInk = !live ? SecurityTheme.OFF
                    : Tokens.alpha(lit ? SecurityTheme.TEXT : SecurityTheme.TEXT_FAINT, in);
            axisGlyph(s, row.axis(), r.x + EFFECT_GLYPH_X, r.centerY(), glyphInk);
            s.textV(row.axis().label(), r.x + EFFECT_LABEL_X, r.centerY(), labelInk,
                    SecurityTheme.MICRO);
            s.textV(Text.truncate(s, row.phrase(), r.w - EFFECT_VALUE_X, SecurityTheme.MICRO),
                    r.x + EFFECT_VALUE_X, r.centerY(), valueInk, SecurityTheme.MICRO);
            ry += EFFECT_ROW_H;
        }

        final float noteIn = Motion.reducedMotion() ? 1.0f : Motion.stagger(clock, rows.size());
        s.textV(Text.truncate(s, ModeEffects.HAZARD_NOTE, body.w, SecurityTheme.MICRO),
                body.x, ry + EFFECT_NOTE_H * 0.5f + (1.0f - noteIn) * 4.0f,
                Tokens.alpha(live ? SecurityTheme.TEXT_FAINT : SecurityTheme.OFF, noteIn),
                SecurityTheme.MICRO);
        s.unclip();
        return card.bottom() + Tokens.S3;
    }

    private ModeEffects.House houseFacts() {
        final List<SecuritySyncPacket.Row> shutters =
                this.ctx.devicesOf(SecurityDevice.DeviceClass.SHUTTER);
        int commanded = 0;
        for (SecuritySyncPacket.Row row : shutters) {
            if (commandedBank(row)) {
                commanded++;
            }
        }
        int blind = 0;
        for (SecuritySyncPacket.Row row : this.ctx.devices()) {
            if (SecurityContext.blind(row)) {
                blind++;
            }
        }
        final int sensors = this.ctx.devicesOf(SecurityDevice.DeviceClass.SENSOR).size();
        return new ModeEffects.House(this.ctx.lockedDown(), this.ctx.lockdownCause(),
                shutters.size(), commanded,
                this.ctx.devicesOf(SecurityDevice.DeviceClass.LOCKER).size(),
                sensors, watchingCount(), blind);
    }

    private boolean commandedBank(SecuritySyncPacket.Row row) {
        final net.minecraft.client.multiplayer.ClientLevel level =
                net.minecraft.client.Minecraft.getInstance().level;
        return level != null
                && level.getBlockEntity(row.pos())
                        instanceof com.createstats.blockentity.BlastShutterBE shutter
                && shutter.commandedFlag();
    }

    private static void axisGlyph(PanelSurface s, ModeEffects.Axis axis, float cx, float cy,
                                  int color) {
        final SecurityIcons.Glyph glyph = switch (axis) {
            case DOORS -> SecurityIcons::door;
            case AUTO_OPEN -> SecurityIcons::approach;
            case SENSORS -> SecurityIcons::sensor;
            case IF_TRIPPED -> SecurityIcons::bell;
            case SHUTTERS -> SecurityIcons::shutter;
            case LOCKERS -> SecurityIcons::locker;
            case SWITCHING -> SecurityIcons::key;
        };
        SecurityIcons.small(s, glyph, cx, cy, EFFECT_GLYPH, color);
    }

    private static void drawCaret(PanelSurface s, float cx, float cy, float open, int color) {
        final double a = Math.toRadians(-90.0 + 90.0 * open);
        final float ca = (float) Math.cos(a);
        final float sa = (float) Math.sin(a);

        final float[] xs = {-3.0f, 0.0f, 3.0f};
        final float[] ys = {-1.5f, 1.5f, -1.5f};
        for (int i = 0; i < 2; i++) {
            s.line(cx + xs[i] * ca - ys[i] * sa, cy + xs[i] * sa + ys[i] * ca,
                    cx + xs[i + 1] * ca - ys[i + 1] * sa, cy + xs[i + 1] * sa + ys[i + 1] * ca,
                    1.2f, color);
        }
    }

    private float drawRepellerMaster(PanelSurface s, float y, float mx, float my) {
        final List<SecuritySyncPacket.Row> repellers =
                this.ctx.devicesOf(SecurityDevice.DeviceClass.REPELLER);
        if (repellers.isEmpty()) {
            return y;
        }
        int sending = 0;
        for (SecuritySyncPacket.Row row : repellers) {
            final SecurityDevice.DeviceState st = row.stateOf();
            if (st == SecurityDevice.DeviceState.ACTIVE || st == SecurityDevice.DeviceState.DAMAGED) {
                sending++;
            }
        }
        final boolean may = this.ctx.mayEditPanel();
        final float bw = 58.0f;
        final Rect allOff = new Rect(this.area.right() - bw, y, bw, SecurityTheme.BTN_H);
        final Rect allOn = new Rect(allOff.x - Tokens.S1 - bw, y, bw, SecurityTheme.BTN_H);
        SecurityTheme.button(s, allOn, "All on", false, hot(allOn, mx, my), may);
        SecurityTheme.button(s, allOff, "All off", false, hot(allOff, mx, my), may);
        if (may) {
            hit(allOn, () -> this.ctx.switchAllRepellers(true));
            hit(allOff, () -> this.ctx.switchAllRepellers(false));
        }
        SecurityIcons.small(s, SecurityIcons::repeller, this.area.x + 5.0f, allOn.centerY(), EFFECT_GLYPH,
                SecurityTheme.TEXT_DIM);
        s.textV(Text.truncate(s, "Repellers: " + sending + " of " + repellers.size() + " sending",
                        allOn.x - this.area.x - 14.0f - Tokens.S2, SecurityTheme.MICRO),
                this.area.x + 14.0f, allOn.centerY(), SecurityTheme.TEXT_DIM, SecurityTheme.MICRO);
        return y + SecurityTheme.BTN_H + Tokens.S3;
    }

    private float drawCoilMaster(PanelSurface s, float y, float mx, float my) {
        final List<SecuritySyncPacket.Row> coils = this.ctx.devicesOf(SecurityDevice.DeviceClass.COIL);
        if (coils.isEmpty()) {
            return y;
        }
        int guarding = 0;
        for (SecuritySyncPacket.Row row : coils) {
            if (row.stateOf() == SecurityDevice.DeviceState.ACTIVE) {
                guarding++;
            }
        }
        final boolean may = this.ctx.mayEditPanel();
        final float bw = 58.0f;
        final Rect allOff = new Rect(this.area.right() - bw, y, bw, SecurityTheme.BTN_H);
        final Rect allOn = new Rect(allOff.x - Tokens.S1 - bw, y, bw, SecurityTheme.BTN_H);
        SecurityTheme.button(s, allOn, net.minecraft.network.chat.Component.translatable(
                "gui.create_stats.security.coils.all_on").getString(), false, hot(allOn, mx, my), may);
        SecurityTheme.button(s, allOff, net.minecraft.network.chat.Component.translatable(
                "gui.create_stats.security.coils.all_off").getString(), false, hot(allOff, mx, my), may);
        if (may) {
            hit(allOn, () -> this.ctx.switchAllCoils(true));
            hit(allOff, () -> this.ctx.switchAllCoils(false));
        }
        SecurityIcons.small(s, SecurityIcons::coil, this.area.x + 5.0f, allOn.centerY(), EFFECT_GLYPH,
                SecurityTheme.TEXT_DIM);
        s.textV(Text.truncate(s, net.minecraft.network.chat.Component.translatable(
                                "gui.create_stats.security.coils.overview", guarding, coils.size()).getString(),
                        allOn.x - this.area.x - 14.0f - Tokens.S2, SecurityTheme.MICRO),
                this.area.x + 14.0f, allOn.centerY(), SecurityTheme.TEXT_DIM, SecurityTheme.MICRO);
        return y + SecurityTheme.BTN_H + Tokens.S3;
    }

    private float drawShutterMaster(PanelSurface s, float y, float mx, float my) {
        final List<SecuritySyncPacket.Row> shutters =
                this.ctx.devicesOf(SecurityDevice.DeviceClass.SHUTTER);
        if (shutters.isEmpty()) {
            return y;
        }
        int sealed = 0;
        for (SecuritySyncPacket.Row row : shutters) {
            if (row.stateOf() == SecurityDevice.DeviceState.ACTIVE) {
                sealed++;
            }
        }
        SecurityTheme.head(s, this.area.x, y, "SHUTTERS");
        y += Text.lineHeight(SecurityTheme.MICRO) + 4.0f;

        final boolean may = this.ctx.mayEditPanel();
        final float gap = Tokens.S2;
        final float w = (this.area.w - gap) * 0.5f;
        final Rect sealAll = new Rect(this.area.x, y, w, SecurityTheme.BTN_H);
        final Rect openAll = new Rect(this.area.x + w + gap, y, w, SecurityTheme.BTN_H);
        SecurityTheme.button(s, sealAll, "Seal every shutter", false, hot(sealAll, mx, my), may);
        SecurityTheme.button(s, openAll, "Open every shutter", false, hot(openAll, mx, my), may);
        if (may) {
            hit(sealAll, () -> this.ctx.sealAllShutters(true));
            hit(openAll, () -> this.ctx.sealAllShutters(false));
        }
        y += SecurityTheme.BTN_H + 3.0f;

        int commanded = 0;
        for (SecuritySyncPacket.Row row : shutters) {
            if (commandedBank(row)) {
                commanded++;
            }
        }
        s.text(this.ctx.houseSealed()
                        ? "AWAY+ is holding all " + shutters.size() + " shut. " + commanded
                                + " of them stay shut when the mode stands down."
                        : sealed + " of " + shutters.size()
                                + (shutters.size() == 1 ? " bank" : " banks")
                                + " sealed. Sealing here is a choice, not a lockdown.",
                this.area.x, y, SecurityTheme.TEXT_FAINT, SecurityTheme.MICRO);
        return y + Text.lineHeight(SecurityTheme.MICRO) + Tokens.S3;
    }

    private boolean hasEntrance() {
        for (SecuritySyncPacket.Row row : this.ctx.devices()) {
            if (row.entrance()) {
                return true;
            }
        }
        return false;
    }

    private String footFor(SecurityMode m) {
        if (!m.sealsHouse() || !this.ctx.mayEditPanel()) {
            return modeFoot(m);
        }
        if (!this.ctx.hasPassword()) {
            return "needs a panel password";
        }
        if (!this.ctx.isOwner()) {
            return "owner only";
        }
        return modeFoot(m);
    }

    private static String modeName(SecurityMode m) {
        return m.sealsHouse() ? "AWAY+" : m.plain();
    }

    private static String modeFoot(SecurityMode m) {
        return switch (m) {
            case HOME -> "sensors log only";
            case AWAY -> "a trip seals the house";
            case CUSTOM -> "armed per sensor";
            case AWAY_PLUS -> "owner leaves by code";
        };
    }

    private static int modeWash(SecurityMode m) {
        return switch (m) {
            case HOME -> SecurityTheme.GREEN_WASH;
            case AWAY -> SecurityTheme.RED_WASH;
            case CUSTOM -> SecurityTheme.STEEL_WASH;
            case AWAY_PLUS -> SecurityTheme.SEAL_WASH;
        };
    }

    private static int modeInk(SecurityMode m) {
        return switch (m) {
            case HOME -> SecurityTheme.GREEN_DEEP;
            case AWAY -> SecurityTheme.RED;
            case CUSTOM -> SecurityTheme.STEEL_DEEP;
            case AWAY_PLUS -> SecurityTheme.SEAL_DEEP;
        };
    }

    private static int modeEdge(SecurityMode m) {
        return switch (m) {
            case HOME -> SecurityTheme.GREEN_EDGE;
            case AWAY -> SecurityTheme.RED_EDGE;
            case CUSTOM -> SecurityTheme.STEEL_EDGE;
            case AWAY_PLUS -> SecurityTheme.SEAL_EDGE;
        };
    }

    private int watchingCount() {
        int n = 0;
        for (SecuritySyncPacket.Row row : this.ctx.devices()) {
            if (row.isSensor() && row.armed()
                    && row.stateOf() != SecurityDevice.DeviceState.UNPOWERED) {
                n++;
            }
        }
        return n;
    }

    private static String faultLine(int breached, int offline, int blind) {
        final StringBuilder sb = new StringBuilder();
        if (breached > 0) {
            sb.append(breached).append(breached == 1 ? " device breached" : " devices breached");
        }
        if (offline > 0) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(offline).append(" out of contact");
        }
        if (blind > 0) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(blind).append(blind == 1 ? " sensor has no power" : " sensors have no power");
        }
        return sb.toString();
    }

    private String rosterFoot() {
        int owners = 0;
        int admins = 0;
        for (SecuritySyncPacket.Person p : this.ctx.people()) {
            if (p.roleOf() == SecurityRole.OWNER) {
                owners++;
            } else if (p.roleOf() == SecurityRole.ADMIN) {
                admins++;
            }
        }
        return owners + " owner, " + admins + (admins == 1 ? " admin" : " admins");
    }

    private void stat(PanelSurface s, Rect r, String label, int value, String foot, boolean warn) {
        s.card(r, Tokens.R_CARD, SecurityTheme.CARD, SecurityTheme.LINE);
        SecurityTheme.head(s, r.x + Tokens.S2, r.y + Tokens.S2, label);
        s.text(String.valueOf(value), r.x + Tokens.S2, r.y + 17.0f,
                value == 0 ? SecurityTheme.OFF : SecurityTheme.TEXT, 2.4f);
        s.text(Text.truncate(s, foot, r.w - Tokens.S4, SecurityTheme.MICRO), r.x + Tokens.S2,
                r.bottom() - 11.0f, warn ? SecurityTheme.RED : SecurityTheme.TEXT_FAINT,
                SecurityTheme.MICRO);
    }

    @Override
    public void drawDock(PanelSurface s, Rect r, float mx, float my) {
        SecurityDock.circuit(s, this.ctx, r);
    }
}
