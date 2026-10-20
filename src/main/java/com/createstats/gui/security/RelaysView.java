package com.createstats.gui.security;

import com.createstats.network.SecurityRelayActionPacket;
import com.createstats.network.SecuritySyncPacket;
import com.createstats.power.SecurityDevice;
import com.createstats.security.SecurityKinds;
import com.createstats.security.SecurityMode;
import com.createstats.security.SensorKind;
import com.createstats.security.Truth;
import com.createstats.security.relay.RelayBehavior;
import com.createstats.security.relay.RelayCondition;
import com.createstats.security.relay.RelayConditions;
import com.createstats.security.relay.RelayConfig;
import com.createstats.security.relay.RelayContact;
import com.createstats.statsmk3.ui.PanelSurface;
import com.createstats.statsmk3.ui.Rect;
import com.createstats.statsmk3.ui.Text;
import com.createstats.statsmk3.ui.Tokens;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.UnaryOperator;

@OnlyIn(Dist.CLIENT)
public final class RelaysView extends DevicesView {
    private static final float STATION_H = 14.0f;

    private static final float CARD_ROW_H = 11.0f;

    private static final int MAX_CARRY = 20;
    private static final String K = "gui.create_stats.security.relays.";

    private final Map<BlockPos, Edits> edits = new HashMap<>();
    private int syncSeen = -1;

    private float sinceSync;

    public RelaysView(SecurityContext ctx) {
        super(ctx, SecurityDevice.DeviceClass.RELAY);
    }

    static String tr(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }

    @Override
    public void update(float dt) {
        super.update(dt);
        this.sinceSync += dt;
        for (Edits e : this.edits.values()) {
            e.update(dt);
        }
        noticeSync();
    }

    private void noticeSync() {
        if (this.ctx.syncCount() == this.syncSeen) {
            return;
        }
        this.syncSeen = this.ctx.syncCount();
        this.sinceSync = 0.0f;
        for (SecuritySyncPacket.RelayRow rr : this.ctx.relays()) {
            final Edits e = this.edits.get(rr.pos());
            if (e != null) {
                e.onSync(rr.revision(), configOf(rr), rr.scoped() ? rr.scope() : null, sender(rr.pos()));
            }
        }
    }

    private Edits.Sender sender(BlockPos relay) {
        return (config, scope, revision) -> this.ctx.configureRelay(relay, config, scope, revision);
    }

    private int carried() {
        return Math.min(MAX_CARRY, (int) (this.sinceSync * 20.0f));
    }

    @Override
    protected boolean usesClearance(SecuritySyncPacket.Row row) {
        return false;
    }

    @Override
    protected String noClearanceLine(SecuritySyncPacket.Row row) {
        return null;
    }

    @Override
    protected String rowNote(SecuritySyncPacket.Row row) {
        final SecuritySyncPacket.RelayRow rr = this.ctx.relay(row.pos());
        if (rr == null) {
            return row.bearing();
        }
        if (!rr.subscribed()) {
            return tr(K + "row.waiting") + "  .  " + row.bearing();
        }
        return conditionName(rr.condition()) + ", " + behaviorName(rr.behavior()) + "  .  " + row.bearing();
    }

    @Override
    protected Rect stateChip(PanelSurface s, float rightX, float centerY, SecuritySyncPacket.Row row) {
        final SecuritySyncPacket.RelayRow rr = this.ctx.relay(row.pos());
        final SecurityDevice.DeviceState state = row.stateOf();
        final Rect chip;
        if (rr != null && !rr.subscribed() && state != SecurityDevice.DeviceState.OFFLINE) {
            chip = SecurityTheme.chipRight(s, rightX, centerY, tr(K + "chip.waiting"),
                    SecurityTheme.AMBER_WASH, SecurityTheme.AMBER_INK, SecurityTheme.AMBER_EDGE);
        } else {
            final String label = this.deviceClass.stateLabel(state).getString().toUpperCase(Locale.ROOT);
            chip = switch (state) {
                case ACTIVE -> SecurityTheme.chipRight(s, rightX, centerY, label,
                        SecurityTheme.GREEN_WASH, SecurityTheme.GREEN_DEEP, SecurityTheme.GREEN_EDGE);
                case UNPOWERED -> SecurityTheme.chipRight(s, rightX, centerY, label,
                        SecurityTheme.AMBER_WASH, SecurityTheme.AMBER_INK, SecurityTheme.AMBER_EDGE);
                case OFFLINE, BREACHED -> SecurityTheme.chipRight(s, rightX, centerY, label,
                        SecurityTheme.RED_WASH, SecurityTheme.RED, SecurityTheme.RED_EDGE);
                default -> SecurityTheme.chipRight(s, rightX, centerY, label,
                        SecurityTheme.CANVAS, SecurityTheme.TEXT_DIM, SecurityTheme.LINE);
            };
        }

        final String held = tr(K + "held");
        final float heldW = s.textWidth(held, SecurityTheme.MICRO) + Tokens.S3;
        if (rr != null && rr.subscribed() && rr.pending()
                && chip.x - Tokens.S1 - heldW >= this.area.x + 120.0f) {
            final Rect h = SecurityTheme.chipRight(s, chip.x - Tokens.S1, centerY, held,
                    SecurityTheme.RED_WASH, SecurityTheme.RED, SecurityTheme.RED_EDGE);
            return new Rect(h.x, chip.y, chip.right() - h.x, chip.h);
        }
        return chip;
    }

    @Override
    protected int outlineOf(SecuritySyncPacket.Row row) {
        final SecuritySyncPacket.RelayRow rr = this.ctx.relay(row.pos());
        if (rr != null && rr.subscribed() && rr.pending()) {
            return SecurityTheme.RED;
        }
        if (rr != null && !rr.subscribed() && row.stateOf() != SecurityDevice.DeviceState.OFFLINE) {
            return SecurityTheme.AMBER;
        }
        return switch (row.stateOf()) {
            case ACTIVE -> SecurityTheme.GREEN;
            case UNPOWERED, DAMAGED -> SecurityTheme.AMBER;
            case OFFLINE, BREACHED -> SecurityTheme.RED;
            default -> SecurityTheme.STEEL;
        };
    }

    @Override
    protected float drawExtras(PanelSurface s, Rect r, SecuritySyncPacket.Row row, float y, float mx,
                               float my) {
        noticeSync();
        final SecuritySyncPacket.RelayRow rr = this.ctx.relay(row.pos());
        if (rr == null) {
            return y;
        }
        y = drawStatus(s, r, rr, y, mx, my);
        if (!rr.subscribed()) {
            return drawAccept(s, r, rr, y, mx, my);
        }
        y = drawSettings(s, r, rr, y, mx, my);
        return drawRetire(s, r, rr, y, mx, my);
    }

    private record Line(String legend, String value, int ink, @Nullable String note, int noteInk,
                        float bar) {
    }

    private float drawStatus(PanelSurface s, Rect r, SecuritySyncPacket.RelayRow rr, float y, float mx,
                             float my) {
        SecurityTheme.head(s, r.x, y, tr(K + "head.now"));
        y += Text.lineHeight(SecurityTheme.MICRO) + 4.0f;
        final RelayCondition c = RelayConditions.byId(rr.condition());
        final boolean unloaded = rr.contact() < 0;
        final RelayContact contact = unloaded ? null : RelayContact.byOrdinal(rr.contact());
        final int carry = carried();
        final int pulseLeft = Math.max(0, rr.pulseLeft() - carry);
        final int since = rr.sinceTrigger() < 0 ? -1 : rr.sinceTrigger() + carry;

        final String head;
        final int ink;
        final int lamp;
        if (!rr.subscribed()) {
            head = "waiting";
            ink = SecurityTheme.AMBER_INK;
            lamp = 0;
        } else if (unloaded) {
            head = "unloaded";
            ink = SecurityTheme.TEXT_DIM;
            lamp = -1;
        } else if (contact == RelayContact.UNBOUND) {
            head = "unbound";
            ink = SecurityTheme.TEXT_DIM;
            lamp = 0;
        } else if (!rr.powered()) {
            head = "no_power";
            ink = SecurityTheme.AMBER_INK;
            lamp = -2;
        } else if (rr.output() > 0) {
            head = "on";
            ink = SecurityTheme.GREEN_DEEP;
            lamp = 1;
        } else {
            head = "off";
            ink = SecurityTheme.TEXT;
            lamp = 0;
        }

        final List<Line> lines = new ArrayList<>();
        final String contactKey = unloaded ? "unloaded" : contact.name().toLowerCase(Locale.ROOT);
        lines.add(new Line(tr(K + "legend.contact"), tr(K + "contact." + contactKey),
                contact == RelayContact.LINKED ? SecurityTheme.TEXT_DIM
                        : contact == RelayContact.LOST ? SecurityTheme.RED : SecurityTheme.AMBER_INK,
                null, 0, -1.0f));
        if (rr.subscribed()) {
            final String reads;
            int readInk = SecurityTheme.TEXT_DIM;
            if (c == null || c.isPoint()) {
                reads = tr(K + "reads.event");
            } else {
                final Truth t = Truth.byOrdinal(rr.truth());
                reads = tr(K + "reads." + t.name().toLowerCase(Locale.ROOT));
                readInk = t == Truth.TRUE ? SecurityTheme.TEXT
                        : t == Truth.UNKNOWN ? SecurityTheme.AMBER_INK : SecurityTheme.TEXT_DIM;
            }

            final String seen = rr.sources() > 0
                    ? tr(K + "reads.seen", rr.sources() - rr.unavailable(), rr.sources()) : null;
            lines.add(new Line(tr(K + "legend.reads"), reads, readInk, seen,
                    rr.unavailable() > 0 ? SecurityTheme.AMBER_INK : SecurityTheme.TEXT_FAINT, -1.0f));
            if (pulseLeft > 0) {
                lines.add(new Line(tr(K + "legend.pulse"), tr(K + "pulse.left", pulseLeft),
                        SecurityTheme.GREEN_DEEP, null, 0, pulseLeft / (float) Math.max(1, rr.pulse())));
            }
            lines.add(new Line(tr(K + "legend.last"), ago(since), SecurityTheme.TEXT_DIM, null, 0, -1.0f));
        }

        final Rect card = new Rect(r.x, y, r.w, 32.0f + lines.size() * CARD_ROW_H + 5.0f);
        final boolean held = rr.subscribed() && rr.pending();
        s.card(card, Tokens.R_CHIP, SecurityTheme.CARD, held ? SecurityTheme.RED_EDGE : SecurityTheme.LINE);

        final float lx = card.x + 14.0f;
        final float ly = card.y + 15.0f;
        switch (lamp) {
            case 1 -> {
                s.circle(lx, ly, 8.5f, SecurityTheme.GREEN_WASH);
                s.circle(lx, ly, 5.5f, SecurityTheme.GREEN);
                s.circle(lx - 1.6f, ly - 1.8f, 1.4f, 0x99FFFFFF);
            }
            case -2 -> {
                s.circle(lx, ly, 5.5f, SecurityTheme.AMBER_EDGE);
                s.circle(lx, ly, 4.5f, SecurityTheme.CANVAS);
                s.line(lx - 3.0f, ly + 3.0f, lx + 3.0f, ly - 3.0f, 1.25f, SecurityTheme.AMBER);
            }
            case -1 -> {
                s.circle(lx, ly, 5.5f, SecurityTheme.LINE);
                s.circle(lx, ly, 4.5f, SecurityTheme.CARD);
                s.textCenterV("?", lx + 0.5f, ly + 0.5f, SecurityTheme.OFF, SecurityTheme.MICRO);
            }
            default -> {
                s.circle(lx, ly, 5.5f, SecurityTheme.OFF);
                s.circle(lx, ly, 4.5f, SecurityTheme.CANVAS);
            }
        }
        final float tx = card.x + 28.0f;
        float right = card.right() - Tokens.S2;
        if (held) {
            final Rect chip = SecurityTheme.chipRight(s, right, card.y + 10.0f, tr(K + "held"),
                    SecurityTheme.RED_WASH, SecurityTheme.RED, SecurityTheme.RED_EDGE);
            right = chip.x - Tokens.S1;
        }
        s.text(Text.truncate(s, tr(K + "output." + head), right - tx, Text.BODY), tx, card.y + 6.0f, ink,
                Text.BODY);
        s.text(Text.truncate(s, tr(K + "output.sub." + head), card.right() - Tokens.S2 - tx,
                SecurityTheme.MICRO), tx, card.y + 17.0f, SecurityTheme.TEXT_FAINT, SecurityTheme.MICRO);
        s.hairlineH(card.x + 1.0f, card.y + 28.0f, card.w - 2.0f, SecurityTheme.LINE);

        float legendW = 0.0f;
        for (Line l : lines) {
            legendW = Math.max(legendW, s.textWidth(l.legend(), SecurityTheme.MICRO));
        }
        final float vx = card.x + Tokens.S2 + legendW + Tokens.S2;
        float ry = card.y + 32.0f;
        for (Line l : lines) {
            s.text(l.legend(), card.x + Tokens.S2, ry + 1.0f, SecurityTheme.TEXT_FAINT, SecurityTheme.MICRO);
            float vw = card.right() - Tokens.S2 - vx;
            if (l.note() != null) {
                s.textRight(l.note(), card.right() - Tokens.S2, ry + 1.0f, l.noteInk(), SecurityTheme.MICRO);
                vw -= s.textWidth(l.note(), SecurityTheme.MICRO) + Tokens.S2;
            }
            final String value = Text.truncate(s, l.value(), vw, SecurityTheme.MICRO);
            s.text(value, vx, ry + 1.0f, l.ink(), SecurityTheme.MICRO);
            if (l.bar() >= 0.0f) {
                final float bx = vx + s.textWidth(value, SecurityTheme.MICRO) + Tokens.S2;
                if (card.right() - Tokens.S2 - bx > 12.0f) {
                    SecurityTheme.bar(s, new Rect(bx, ry + 2.0f, card.right() - Tokens.S2 - bx, 4.0f), l.bar(),
                            SecurityTheme.GREEN);
                }
            }
            ry += CARD_ROW_H;
        }
        y = card.bottom() + 4.0f;

        if (held) {
            final String why = resetBlocked(rr, c);
            final Rect btn = new Rect(r.x, y, r.w, SecurityTheme.BTN_H);
            SecurityTheme.button(s, btn, tr(K + "reset"), true, why == null && hot(btn, mx, my), why == null);
            if (why == null) {
                hit(btn, () -> this.ctx.relayAction(rr.pos(), SecurityRelayActionPacket.RESET));
            }
            y += SecurityTheme.BTN_H + 4.0f;
            if (why != null) {
                y = wrapNote(s, r, y, tr(why), SecurityTheme.TEXT_FAINT);
            }
        }
        return y + Tokens.S3;
    }

    @Nullable
    private String resetBlocked(SecuritySyncPacket.RelayRow rr, @Nullable RelayCondition c) {
        if (!this.ctx.mayEditDevices()) {
            return K + "reset.why.forbidden";
        }
        if (RelayContact.byOrdinal(rr.contact()) != RelayContact.LINKED || rr.contact() < 0) {
            return K + "reset.why.contact";
        }
        if (c != null && !c.isPoint()) {
            final Truth t = Truth.byOrdinal(rr.truth());
            if (t == Truth.UNKNOWN) {
                return K + "reset.why.unknown";
            }
            if (t == Truth.TRUE) {
                return K + "reset.why.active";
            }
        }
        return null;
    }

    private float drawAccept(PanelSurface s, Rect r, SecuritySyncPacket.RelayRow rr, float y, float mx,
                             float my) {
        SecurityTheme.head(s, r.x, y, tr(K + "head.accept"));
        y += Text.lineHeight(SecurityTheme.MICRO) + 4.0f;
        y = wrapNote(s, r, y, tr(K + "accept.line"), SecurityTheme.TEXT_DIM) + 4.0f;

        final String why = !this.ctx.mayEditDevices() ? K + "accept.why.forbidden"
                : rr.contact() < 0 ? K + "accept.why.unloaded" : null;
        final boolean may = why == null && RelayContact.byOrdinal(rr.contact()) == RelayContact.CANDIDATE;
        final Rect btn = new Rect(r.x, y, r.w, SecurityTheme.BTN_H);
        SecurityTheme.button(s, btn, tr(K + "accept"), true, may && hot(btn, mx, my), may);
        if (may) {
            hit(btn, () -> this.ctx.relayAction(rr.pos(), SecurityRelayActionPacket.ACCEPT));
        }
        y += SecurityTheme.BTN_H + 4.0f;
        if (why != null) {
            y = wrapNote(s, r, y, tr(why), SecurityTheme.TEXT_FAINT);
        }
        return y + Tokens.S3;
    }

    private float drawSettings(PanelSurface s, Rect r, SecuritySyncPacket.RelayRow rr, float y, float mx,
                               float my) {
        final boolean may = this.ctx.mayEditDevices();
        final boolean live = may && !rr.pending();
        final Edits e = this.edits.computeIfAbsent(rr.pos(), p -> new Edits());
        final RelayConfig current = configOf(rr);
        final RelayCondition cond = RelayConditions.byId(rr.condition());

        if (!live) {
            final boolean heldOnly = may && rr.pending();
            y = wrapNote(s, r, y, tr(heldOnly ? K + "locked.held" : K + "locked.member"),
                    heldOnly ? SecurityTheme.RED : SecurityTheme.TEXT_FAINT) + Tokens.S2;
        }

        y = head(s, r, y, "watches", e);
        final List<RelayCondition> all = RelayConditions.all();
        for (RelayCondition c : all) {
            y = conditionRow(s, r, rr, e, c, c.id().equals(rr.condition()), live, y, mx, my);
        }
        if (cond == null) {
            y = conditionRow(s, r, rr, e, null, true, false, y, mx, my);
        }
        y += Tokens.S3 - 2.0f;

        y = head(s, r, y, "answers", e);
        final RelayBehavior[] behaviors = RelayBehavior.all();
        final float gap = 2.0f;
        final float bw = (r.w - gap * (behaviors.length - 1)) / behaviors.length;
        for (int i = 0; i < behaviors.length; i++) {
            final RelayBehavior b = behaviors[i];
            final Rect opt = new Rect(r.x + i * (bw + gap), y, bw, SecurityTheme.OPT_H);
            final String label = behaviorName(b.key());
            final boolean offered = cond != null && cond.offers(b);
            final boolean on = b.key().equals(rr.behavior());
            if (!offered) {
                s.card(opt, Tokens.R_CHIP, SecurityTheme.CANVAS, SecurityTheme.LINE);
                s.textCenterV(label, opt.centerX(), opt.centerY(), SecurityTheme.OFF, SecurityTheme.MICRO);
                final float lw = s.textWidth(label, SecurityTheme.MICRO);
                s.hairlineH(opt.centerX() - lw * 0.5f - 1.0f, opt.centerY(), lw + 2.0f, SecurityTheme.OFF);
                continue;
            }
            option(s, opt, label, on, live, mx, my);
            if (pending(e, "answers:" + b.key())) {
                s.roundedRectOutline(opt, Tokens.R_CHIP, 1.0f, SecurityTheme.STEEL);
            }
            if (live && !on) {
                hit(opt, () -> askConfig(rr, e, "answers:" + b.key(), w -> w.withBehavior(b)));
            }
        }
        y += SecurityTheme.OPT_H + 4.0f;
        final RelayBehavior chosen = RelayBehavior.byKey(rr.behavior());
        if (chosen != null) {
            y = wrapNote(s, r, y, tr("relay.create_stats.behavior." + chosen.key() + ".blurb"),
                    SecurityTheme.TEXT_DIM);
        }
        if (cond != null && cond.behaviors().size() < behaviors.length) {
            y = wrapNote(s, r, y + 1.0f, tr(cond.isPoint() ? K + "behavior.why.point" : K + "behavior.why.supervision"),
                    SecurityTheme.TEXT_FAINT);
        }
        y += Tokens.S3;

        if (chosen == RelayBehavior.PULSE) {
            y = drawPulse(s, r, rr, e, current, live, y, mx, my);
        }

        if (cond != null && cond.needsMode()) {
            y = head(s, r, y, "mode", e);
            final SecurityMode[] modes = SecurityMode.all();
            final float mw = (r.w - gap * (modes.length - 1)) / modes.length;
            for (int i = 0; i < modes.length; i++) {
                final SecurityMode m = modes[i];
                final Rect opt = new Rect(r.x + i * (mw + gap), y, mw, SecurityTheme.OPT_H);
                final boolean on = m.key().equals(rr.mode());
                option(s, opt, tr("relay.create_stats.mode." + m.key()), on, live, mx, my);
                if (pending(e, "mode:" + m.key())) {
                    s.roundedRectOutline(opt, Tokens.R_CHIP, 1.0f, SecurityTheme.STEEL);
                }
                if (live && !on) {
                    hit(opt, () -> askConfig(rr, e, "mode:" + m.key(), w -> w.withMode(m.key())));
                }
            }
            y += SecurityTheme.OPT_H + 4.0f;
            final SecurityMode picked = SecurityMode.byKey(rr.mode());
            if (picked != null) {
                y = wrapNote(s, r, y, tr("securitymode.create_stats." + picked.key() + ".blurb"),
                        SecurityTheme.TEXT_FAINT);
            }
            y += Tokens.S3;
        }

        if (cond != null && cond.narrowable()) {
            y = drawScope(s, r, rr, e, cond, live, y, mx, my);
        }
        return y;
    }

    private float head(PanelSurface s, Rect r, float y, String section, Edits e) {
        SecurityTheme.head(s, r.x, y, tr(K + "head." + section));
        if (e.busy() && e.asked() != null && e.asked().startsWith(section)) {
            s.textRight(tr(K + "sending"), r.right(), y, SecurityTheme.STEEL, SecurityTheme.MICRO);
        }
        return y + Text.lineHeight(SecurityTheme.MICRO) + 4.0f;
    }

    private static boolean pending(Edits e, String key) {
        return e.busy() && key.equals(e.asked());
    }

    private void option(PanelSurface s, Rect opt, String label, boolean on, boolean live, float mx, float my) {
        if (live || on) {
            SecurityTheme.option(s, opt, label, on, live && !on && hot(opt, mx, my));
        } else {
            s.card(opt, Tokens.R_CHIP, SecurityTheme.CANVAS, SecurityTheme.LINE);
            s.textCenterV(label, opt.centerX(), opt.centerY(), SecurityTheme.OFF, SecurityTheme.MICRO);
        }
    }

    private float conditionRow(PanelSurface s, Rect r, SecuritySyncPacket.RelayRow rr, Edits e,
                               @Nullable RelayCondition c, boolean on, boolean live, float y, float mx,
                               float my) {
        final String key = c == null ? "unsupported" : c.langKey();
        final String name = tr("relay.create_stats.condition." + key);
        final String blurbKey = "relay.create_stats.condition." + key + ".blurb";
        final String blurb = Component.translatable(blurbKey).getString();
        final boolean hasBlurb = !blurb.equals(blurbKey);
        final List<String> wrapped = on && hasBlurb
                ? Text.wrap(s, blurb, r.w - 20.0f - Tokens.S2, SecurityTheme.MICRO) : List.of();
        final float h = wrapped.isEmpty() ? STATION_H : STATION_H + wrapped.size() * 7.0f + 2.0f;
        final Rect box = new Rect(r.x, y, r.w, h);
        final boolean asked = c != null && pending(e, "watches:" + c.id());
        final boolean warm = live && !on && c != null && hot(box, mx, my);
        s.card(box, Tokens.R_CHIP,
                on ? SecurityTheme.STEEL_WASH : warm ? SecurityTheme.CARD_HOT : SecurityTheme.CANVAS,
                asked ? SecurityTheme.STEEL : on ? SecurityTheme.STEEL_EDGE : SecurityTheme.LINE);
        final int ink = on ? SecurityTheme.STEEL_DEEP : live ? SecurityTheme.TEXT_DIM : SecurityTheme.OFF;
        final float cy = box.y + STATION_H * 0.5f;
        SecurityIcons.small(s, glyph(c), box.x + 10.0f, cy, 0.6f,
                on ? SecurityTheme.STEEL_DEEP : live ? SecurityTheme.TEXT_FAINT : SecurityTheme.OFF);
        s.textV(name, box.x + 20.0f, cy, ink, SecurityTheme.MICRO);
        if (!on && hasBlurb) {
            final float bx = box.x + 20.0f + s.textWidth(name, SecurityTheme.MICRO) + 6.0f;
            s.textV(Text.truncate(s, blurb, box.right() - Tokens.S2 - bx, SecurityTheme.MICRO), bx, cy,
                    live ? SecurityTheme.TEXT_FAINT : SecurityTheme.OFF, SecurityTheme.MICRO);
        }
        float wy = box.y + STATION_H - 2.0f;
        for (String line : wrapped) {
            s.text(line, box.x + 20.0f, wy, SecurityTheme.STEEL, SecurityTheme.MICRO);
            wy += 7.0f;
        }
        if (live && !on && c != null) {
            hit(box, () -> askConfig(rr, e, "watches:" + c.id(), w -> w.withCondition(c.id())));
        }
        return y + h + 2.0f;
    }

    private static SecurityIcons.Glyph glyph(@Nullable RelayCondition c) {
        if (c == null) {
            return SecurityIcons::sensor;
        }
        return switch (c.langKey()) {
            case "lockdown" -> SecurityIcons::bell;
            case "tamper" -> SecurityIcons::tamper;
            case "fire" -> SecurityIcons::fire;
            case "water" -> SecurityIcons::water;
            case "denied" -> SecurityIcons::door;
            case "breached" -> SecurityIcons::shutter;
            case "mode" -> SecurityIcons::house;
            case "healthy" -> SecurityIcons::shield;
            default -> SecurityIcons::sensor;
        };
    }

    private float drawPulse(PanelSurface s, Rect r, SecuritySyncPacket.RelayRow rr, Edits e,
                            RelayConfig current, boolean live, float y, float mx, float my) {
        y = head(s, r, y, "pulse", e);
        final int base = e.base(current).pulseTicks();
        final int[] steps = {-10, -1, 1, 10};
        final float sw = 22.0f;
        final float g2 = 2.0f;
        final float mid = r.w - (sw + g2) * steps.length;
        final String ticks = tr(K + "pulse.ticks", rr.pulse());
        final String full = ticks + "  .  " + tr(K + "pulse.seconds", seconds(rr.pulse()));
        final String centre = s.textWidth(full, SecurityTheme.MICRO) <= mid - 4.0f ? full : ticks;
        for (int i = 0; i < steps.length; i++) {
            final int d = steps[i];
            final float x = i < 2 ? r.x + i * (sw + g2) : r.right() - (steps.length - i) * (sw + g2) + g2;
            final Rect btn = new Rect(x, y, sw, SecurityTheme.BTN_H);
            final int target = clampPulse(base + d);
            final boolean can = live && target != base;
            SecurityTheme.button(s, btn, (d > 0 ? "+" : "-") + Math.abs(d), false, can && hot(btn, mx, my), can);
            if (can) {
                hit(btn, () -> askConfig(rr, e, "pulse", w -> w.withPulse(clampPulse(w.pulseTicks() + d))));
            }
        }
        s.textCenterV(centre, r.x + (sw + g2) * 2.0f + mid * 0.5f - 1.0f, y + SecurityTheme.BTN_H * 0.5f,
                SecurityTheme.TEXT, SecurityTheme.MICRO);
        y += SecurityTheme.BTN_H + 3.0f;

        if (pending(e, "pulse") && base != rr.pulse()) {
            s.textCenter("-> " + tr(K + "pulse.ticks", base), r.centerX(), y, SecurityTheme.STEEL,
                    SecurityTheme.MICRO);
            y += Text.lineHeight(SecurityTheme.MICRO) + 1.0f;
        }
        return y + Tokens.S3;
    }

    private static int clampPulse(int ticks) {
        return Math.max(RelayConfig.MIN_PULSE, Math.min(RelayConfig.MAX_PULSE, ticks));
    }

    public static String seconds(int ticks) {
        return BigDecimal.valueOf(ticks).divide(BigDecimal.valueOf(20)).stripTrailingZeros().toPlainString();
    }

    private float drawScope(PanelSurface s, Rect r, SecuritySyncPacket.RelayRow rr, Edits e, RelayCondition cond,
                            boolean live, float y, float mx, float my) {
        y = head(s, r, y, "scope", e);
        final List<SecuritySyncPacket.Row> eligible = new ArrayList<>();
        for (SecuritySyncPacket.Row d : this.ctx.devices()) {
            final String kind = kindOf(d);
            if (kind != null && cond.acceptsDevice(kind)) {
                eligible.add(d);
            }
        }
        y = scopeRow(s, r, rr, e, tr(K + "scope.house"), tr(K + "scope.house.foot", eligible.size()),
                !rr.scoped(), SecurityIcons::house, null, "scope:house", live, y, mx, my);
        boolean listed = false;
        for (SecuritySyncPacket.Row d : eligible) {
            final boolean on = rr.scoped() && d.pos().equals(rr.scope());
            listed |= on;
            y = scopeRow(s, r, rr, e, d.name(), d.bearing(), on, (ss, cx, cy, c) -> deviceGlyph(ss, d, cx, cy, c),
                    d.pos(), "scope:" + d.pos().asLong(), live, y, mx, my);
        }
        if (rr.scoped() && !listed) {
            final BlockPos at = rr.scope();
            y = scopeRow(s, r, rr, e, tr(K + "scope.elsewhere"), at.getX() + " " + at.getY() + " " + at.getZ(),
                    true, null, at, "scope:elsewhere", false, y, mx, my);
        }
        if (eligible.isEmpty()) {
            y = wrapNote(s, r, y + 1.0f, tr(K + "scope.none"), SecurityTheme.TEXT_FAINT);
        }
        return y + Tokens.S3 - 2.0f;
    }

    private float scopeRow(PanelSurface s, Rect r, SecuritySyncPacket.RelayRow rr, Edits e, String label,
                           String foot, boolean on, @Nullable SecurityIcons.Glyph icon, @Nullable BlockPos target,
                           String key, boolean live, float y, float mx, float my) {
        final Rect box = new Rect(r.x, y, r.w, STATION_H);
        final boolean warm = live && !on && hot(box, mx, my);
        s.card(box, Tokens.R_CHIP,
                on ? SecurityTheme.STEEL_WASH : warm ? SecurityTheme.CARD_HOT : SecurityTheme.CANVAS,
                pending(e, key) ? SecurityTheme.STEEL : on ? SecurityTheme.STEEL_EDGE : SecurityTheme.LINE);
        final int ink = on ? SecurityTheme.STEEL_DEEP : live ? SecurityTheme.TEXT_DIM : SecurityTheme.OFF;
        if (icon != null) {
            SecurityIcons.small(s, icon, box.x + 10.0f, box.centerY(), 0.6f,
                    on ? SecurityTheme.STEEL_DEEP : live ? SecurityTheme.TEXT_FAINT : SecurityTheme.OFF);
        }
        final float fw = s.textWidth(foot, SecurityTheme.MICRO);
        s.textV(Text.truncate(s, label, box.w - 20.0f - fw - Tokens.S2 * 2.0f, SecurityTheme.MICRO),
                box.x + (icon != null ? 20.0f : Tokens.S2), box.centerY(), ink, SecurityTheme.MICRO);
        s.textRightV(foot, box.right() - Tokens.S2, box.centerY(), on ? ink : SecurityTheme.TEXT_FAINT,
                SecurityTheme.MICRO);
        if (live && !on) {
            hit(box, () -> askScope(rr, e, key, target));
        }
        return y + STATION_H + 2.0f;
    }

    private static void deviceGlyph(PanelSurface s, SecuritySyncPacket.Row d, float cx, float cy, int c) {
        switch (d.classOf()) {
            case DOOR -> SecurityIcons.door(s, cx, cy, c);
            case SENSOR -> SecurityIcons.sensor(s, cx, cy, c, d.sensorKindOf());
            case LOCKER -> SecurityIcons.locker(s, cx, cy, c);
            case SHUTTER -> SecurityIcons.shutter(s, cx, cy, c);
            case REPELLER -> SecurityIcons.repeller(s, cx, cy, c);
            case RELAY -> SecurityIcons.relay(s, cx, cy, c);
            case COIL -> SecurityIcons.coil(s, cx, cy, c);
        }
    }

    @Nullable
    static String kindOf(SecuritySyncPacket.Row row) {
        return switch (row.classOf()) {
            case DOOR -> SecurityKinds.DOOR;
            case LOCKER -> SecurityKinds.LOCKER;
            case SHUTTER -> SecurityKinds.SHUTTER;
            case REPELLER -> SecurityKinds.REPELLER;
            case RELAY -> SecurityKinds.RELAY;
            case COIL -> SecurityKinds.COIL;
            case SENSOR -> {
                final SensorKind k = row.sensorKindOf();
                yield k == null ? null : SecurityKinds.sensor(k);
            }
        };
    }

    private float drawRetire(PanelSurface s, Rect r, SecuritySyncPacket.RelayRow rr, float y, float mx,
                             float my) {
        SecurityTheme.head(s, r.x, y, tr(K + "head.retire"));
        y += Text.lineHeight(SecurityTheme.MICRO) + 4.0f;
        final boolean may = this.ctx.mayEditDevices();
        // owner only while an incident is held, retiring ends it unresolved

        final boolean ownerOnly = rr.pending() && !this.ctx.isOwner();
        final boolean enabled = may && !ownerOnly;
        final boolean armed = armedFor("retire", rr.pos());
        final Rect btn = new Rect(r.x, y, r.w, SecurityTheme.BTN_H);
        danger(s, btn, tr(armed ? K + "retire.confirm" : K + "retire"), armed, enabled && hot(btn, mx, my), enabled);
        if (enabled) {
            hit(btn, () -> {
                if (armedFor("retire", rr.pos())) {
                    disarm();
                    this.ctx.relayAction(rr.pos(), SecurityRelayActionPacket.RETIRE);
                } else {
                    arm("retire", rr.pos());
                }
            });
        }
        y += SecurityTheme.BTN_H + 4.0f;
        y = wrapNote(s, r, y, tr(ownerOnly && may ? K + "retire.why.held" : K + "retire.line"),
                SecurityTheme.TEXT_FAINT);
        return y + Tokens.S3;
    }

    private void askConfig(SecuritySyncPacket.RelayRow rr, Edits e, String asked, UnaryOperator<RelayConfig> change) {
        RelayConfig wanted = change.apply(e.base(configOf(rr)));
        BlockPos scope = e.baseScope(rr.scoped() ? rr.scope() : null);
        final RelayCondition c = wanted.condition();
        if (c != null && !c.offers(wanted.behavior())) {
            for (RelayBehavior b : RelayBehavior.all()) {
                if (c.offers(b)) {
                    wanted = wanted.withBehavior(b);
                    break;
                }
            }
        }
        if (c != null && c.needsMode() && SecurityMode.byKey(wanted.mode()) == null) {
            wanted = wanted.withMode(SecurityMode.AWAY.key());
        }
        if (scope != null && (c == null || !c.narrowable() || !scopeStillFits(c, scope))) {
            scope = null;
        }
        e.ask(rr.revision(), wanted, scope, asked, sender(rr.pos()));
    }

    private void askScope(SecuritySyncPacket.RelayRow rr, Edits e, String asked, @Nullable BlockPos target) {
        e.ask(rr.revision(), e.base(configOf(rr)), target, asked, sender(rr.pos()));
    }

    private boolean scopeStillFits(RelayCondition c, BlockPos pos) {
        final SecuritySyncPacket.Row d = this.ctx.device(pos);
        final String kind = d == null ? null : kindOf(d);
        return d == null || (kind != null && c.acceptsDevice(kind));
    }

    static RelayConfig configOf(SecuritySyncPacket.RelayRow rr) {
        final RelayBehavior b = RelayBehavior.byKey(rr.behavior());
        return new RelayConfig(rr.condition(), b == null ? RelayBehavior.FOLLOW : b,
                rr.pulse() <= 0 ? RelayConfig.DEFAULT_PULSE : rr.pulse(), rr.mode(), null);
    }

    public static String conditionName(String id) {
        final RelayCondition c = RelayConditions.byId(id);
        return tr("relay.create_stats.condition." + (c == null ? "unsupported" : c.langKey()));
    }

    public static String behaviorName(String key) {
        final RelayBehavior b = RelayBehavior.byKey(key);
        return b == null ? key : tr("relay.create_stats.behavior." + b.key());
    }

    static String ago(int ticks) {
        if (ticks < 0) {
            return tr(K + "last.never");
        }
        if (ticks < 20) {
            return tr(K + "last.now");
        }
        if (ticks < 1200) {
            return tr(K + "last.seconds", ticks / 20);
        }
        if (ticks < 72000) {
            return tr(K + "last.minutes", ticks / 1200);
        }
        return tr(K + "last.hours", ticks / 72000);
    }

    private static float wrapNote(PanelSurface s, Rect r, float y, String text, int ink) {
        for (String line : Text.wrap(s, text, r.w, SecurityTheme.MICRO)) {
            s.text(line, r.x, y, ink, SecurityTheme.MICRO);
            y += Text.lineHeight(SecurityTheme.MICRO) + 1.0f;
        }
        return y;
    }

    // one in flight, latest queued. two quick presses shared a revision and the second came back STALE
    public static final class Edits {
        @FunctionalInterface
        public interface Sender {
            void send(RelayConfig config, @Nullable BlockPos scope, long revision);
        }

        private static final float ANSWER_SECONDS = 2.0f;
        private static final float GIVE_UP_SECONDS = 4.0f;

        private boolean sent;
        private RelayConfig sentConfig;
        private BlockPos sentScope;
        private long sentRevision;
        private float sentAge;
        private int sentSyncs;

        private boolean queued;
        private RelayConfig queuedConfig;
        private BlockPos queuedScope;

        private String asked;

        public boolean busy() {
            return this.sent || this.queued;
        }

        @Nullable
        public String asked() {
            return this.asked;
        }

        public RelayConfig base(RelayConfig current) {
            return this.queued ? this.queuedConfig : this.sent ? this.sentConfig : current;
        }

        @Nullable
        public BlockPos baseScope(@Nullable BlockPos current) {
            return this.queued ? this.queuedScope : this.sent ? this.sentScope : current;
        }

        public void ask(long revision, RelayConfig wanted, @Nullable BlockPos scope, String asked, Sender out) {
            this.asked = asked;
            if (this.sent) {
                this.queued = true;
                this.queuedConfig = wanted;
                this.queuedScope = scope;
                return;
            }
            send(wanted, scope, revision, out);
        }

        private void send(RelayConfig wanted, @Nullable BlockPos scope, long revision, Sender out) {
            this.sent = true;
            this.sentConfig = wanted;
            this.sentScope = scope;
            this.sentRevision = revision;
            this.sentAge = 0.0f;
            this.sentSyncs = 0;
            out.send(wanted, scope, revision);
        }

        public void onSync(long revision, RelayConfig current, @Nullable BlockPos currentScope, Sender out) {
            if (!this.sent) {
                return;
            }
            this.sentSyncs++;
            if (revision == this.sentRevision && this.sentSyncs < 2 && this.sentAge < ANSWER_SECONDS) {
                return;
            }
            this.sent = false;
            final boolean had = this.queued;
            this.queued = false;
            if (had && !same(this.queuedConfig, this.queuedScope, current, currentScope)) {
                send(this.queuedConfig, this.queuedScope, revision, out);
            } else {
                this.asked = null;
            }
        }

        public void update(float dt) {
            if (this.sent && (this.sentAge += dt) > GIVE_UP_SECONDS) {
                this.sent = false;
                this.queued = false;
                this.asked = null;
            }
        }

        private static boolean same(RelayConfig a, @Nullable BlockPos aScope, RelayConfig b,
                                    @Nullable BlockPos bScope) {
            return a.conditionId().equals(b.conditionId()) && a.behavior() == b.behavior()
                    && a.pulseTicks() == b.pulseTicks() && a.mode().equals(b.mode())
                    && (aScope == null ? bScope == null : aScope.equals(bScope));
        }
    }
}
