package com.createstats.gui.security;

import com.createstats.network.SecuritySyncPacket;
import com.createstats.power.SecurityDevice;
import com.createstats.security.CoilStatus;
import com.createstats.security.CoilWords;
import com.createstats.statsmk3.ui.PanelSurface;
import com.createstats.statsmk3.ui.Rect;
import com.createstats.statsmk3.ui.Text;
import com.createstats.statsmk3.ui.Tokens;
import com.createstats.stormcoil.CoilRules;
import com.createstats.stormcoil.CoilRules.HouseState;
import com.createstats.stormcoil.CoilRules.Rule;
import com.createstats.stormcoil.CoilStage;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@OnlyIn(Dist.CLIENT)
public final class CoilsView extends DevicesView {
    private static final float PENDING_SECONDS = 2.0f;

    private static final String K = "gui.create_stats.security.coils.";

    private static final String[] ROWS = {"hostile", "transform", "strangers", "members"};

    private static final class Pending {
        final Rule rule;
        float age;

        Pending(Rule rule) {
            this.rule = rule;
        }
    }

    private final Map<String, Pending> pending = new HashMap<>();
    private int seenSyncs = -1;

    public CoilsView(SecurityContext ctx) {
        super(ctx, SecurityDevice.DeviceClass.COIL);
    }

    private static String tr(String key, Object... args) {
        return Component.translatable(key, args).getString();
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
    protected String passLabel() {
        return tr(K + "can_adjust");
    }

    @Override
    protected String rowNote(SecuritySyncPacket.Row row) {
        final SecuritySyncPacket.CoilRow c = this.ctx.coil(row.pos());
        return c == null ? row.bearing() : tr(K + "row.stage", c.stage()) + "  .  " + row.bearing();
    }

    @Override
    protected Rect stateChip(PanelSurface s, float rightX, float centerY, SecuritySyncPacket.Row row) {
        final SecuritySyncPacket.CoilRow c = this.ctx.coil(row.pos());
        if (row.stateOf() == SecurityDevice.DeviceState.OFFLINE || c == null) {
            return super.stateChip(s, rightX, centerY, row);
        }
        final CoilStatus st = c.statusOf();
        final String label = CoilWords.status(st).getString().toUpperCase(Locale.ROOT);
        if (st.isFault()) {
            return SecurityTheme.chipRight(s, rightX, centerY, label, SecurityTheme.RED_WASH, SecurityTheme.RED,
                    SecurityTheme.RED_EDGE);
        }
        if (st.onGuard()) {
            return SecurityTheme.chipRight(s, rightX, centerY, label, SecurityTheme.GREEN_WASH,
                    SecurityTheme.GREEN_DEEP, SecurityTheme.GREEN_EDGE);
        }
        if (st == CoilStatus.STOOD_DOWN || st == CoilStatus.HELD) {
            return SecurityTheme.chipRight(s, rightX, centerY, label, SecurityTheme.AMBER_WASH,
                    SecurityTheme.AMBER_INK, SecurityTheme.AMBER_EDGE);
        }
        return SecurityTheme.chipRight(s, rightX, centerY, label, SecurityTheme.CANVAS, SecurityTheme.TEXT_DIM,
                SecurityTheme.LINE);
    }

    @Override
    protected int outlineOf(SecuritySyncPacket.Row row) {
        final SecuritySyncPacket.CoilRow c = this.ctx.coil(row.pos());
        if (c == null || row.stateOf() == SecurityDevice.DeviceState.OFFLINE) {
            return super.outlineOf(row);
        }
        final CoilStatus st = c.statusOf();
        return st.isFault() ? SecurityTheme.RED : st.onGuard() ? SecurityTheme.GREEN
                : st == CoilStatus.STOOD_DOWN || st == CoilStatus.HELD ? SecurityTheme.AMBER : SecurityTheme.STEEL;
    }

    @Override
    public void update(float dt) {
        super.update(dt);
        final boolean fresh = this.ctx.syncCount() != this.seenSyncs;
        this.seenSyncs = this.ctx.syncCount();
        for (Iterator<Map.Entry<String, Pending>> it = this.pending.entrySet().iterator(); it.hasNext(); ) {
            final Map.Entry<String, Pending> e = it.next();
            final Pending p = e.getValue();
            p.age += dt;
            if (p.age > PENDING_SECONDS || (fresh && landed(e.getKey(), p))) {
                it.remove();
            }
        }
    }

    private boolean landed(String key, Pending p) {
        final int hash = key.indexOf('#');
        final BlockPos pos = BlockPos.of(Long.parseLong(key.substring(0, hash)));
        final SecuritySyncPacket.CoilRow c = this.ctx.coil(pos);
        return c != null && c.grid().rule(HouseState.byOrdinal(Integer.parseInt(key.substring(hash + 1))))
                .equals(p.rule);
    }

    private static String key(SecuritySyncPacket.CoilRow c, HouseState state) {
        return c.pos().asLong() + "#" + state.ordinal();
    }

    private Rule shown(SecuritySyncPacket.CoilRow c, HouseState state) {
        final Pending p = this.pending.get(key(c, state));
        return p != null ? p.rule : c.grid().rule(state);
    }

    private void ask(SecuritySyncPacket.CoilRow c, HouseState state, Rule rule) {
        this.pending.put(key(c, state), new Pending(rule));
        this.ctx.setCoilRule(c.pos(), state, rule);
    }

    @Override
    protected float drawExtras(PanelSurface s, Rect r, SecuritySyncPacket.Row row, float y, float mx, float my) {
        final SecuritySyncPacket.CoilRow c = this.ctx.coil(row.pos());
        if (c == null) {
            return y;
        }
        y = drawStatus(s, r, c, y);
        y = drawGuard(s, r, c, y, mx, my);
        y = drawRules(s, r, c, y, mx, my);
        return drawAtBlock(s, r, row, c, y, mx, my);
    }

    private static int stateInk(HouseState s) {
        return switch (s) {
            case HOME -> SecurityTheme.GREEN_DEEP;
            case AWAY -> SecurityTheme.RED;
            case CUSTOM -> SecurityTheme.STEEL_DEEP;
            case AWAY_PLUS -> SecurityTheme.SEAL_DEEP;
            case LOCKDOWN -> SecurityTheme.SEAL;
        };
    }

    private static int stateWash(HouseState s) {
        return switch (s) {
            case HOME -> SecurityTheme.GREEN_WASH;
            case AWAY, LOCKDOWN -> SecurityTheme.RED_WASH;
            case CUSTOM -> SecurityTheme.STEEL_WASH;
            case AWAY_PLUS -> SecurityTheme.SEAL_WASH;
        };
    }

    private static int stateEdge(HouseState s) {
        return switch (s) {
            case HOME -> SecurityTheme.GREEN_EDGE;
            case AWAY, LOCKDOWN -> SecurityTheme.RED_EDGE;
            case CUSTOM -> SecurityTheme.STEEL_EDGE;
            case AWAY_PLUS -> SecurityTheme.SEAL_EDGE;
        };
    }

    private float drawStatus(PanelSurface s, Rect r, SecuritySyncPacket.CoilRow c, float y) {
        SecurityTheme.head(s, r.x, y, tr(K + "head.now"));
        y += Text.lineHeight(SecurityTheme.MICRO) + 4.0f;
        final CoilStatus st = c.statusOf();
        final HouseState now = this.ctx.houseState();
        final Rule rule = c.grid().rule(now);
        final String head = CoilWords.status(st).getString();
        final String sub = switch (st) {
            case ON_GUARD, FIGHTING -> tr(K + "sub." + st.key(), CoilWords.rule(rule));
            case NO_TARGETS -> tr(K + "sub.no_targets", CoilWords.state(now));
            case BLOCKED -> tr(K + "sub.blocked", c.blockedBy());
            default -> tr(K + "sub." + st.key());
        };
        final boolean fault = st.isFault();
        final boolean guard = st.onGuard();
        final boolean standby = st.standby();
        final int ink = fault ? SecurityTheme.RED : guard ? SecurityTheme.GREEN_DEEP
                : standby ? SecurityTheme.AMBER_INK : SecurityTheme.TEXT_DIM;

        final float lh = 11.0f;
        final int rows = c.loaded() ? 4 : 0;

        final float tx = r.x + 28.0f;
        final List<String> subLines = Text.wrap(s, sub, r.right() - Tokens.S2 - tx, SecurityTheme.MICRO);
        final int subCount = Math.min(2, subLines.size());
        final float extra = subCount > 1 ? 7.0f : 0.0f;
        final Rect card = new Rect(r.x, y, r.w, 32.0f + extra + (rows > 0 ? rows * lh + 5.0f : 0.0f));
        s.card(card, Tokens.R_CHIP, SecurityTheme.CARD, fault ? SecurityTheme.RED_EDGE : SecurityTheme.LINE);

        final float lx = card.x + 14.0f;
        final float ly = card.y + 15.0f;
        final int lamp = fault ? SecurityTheme.RED : guard ? SecurityTheme.GREEN : standby ? SecurityTheme.AMBER
                : SecurityTheme.OFF;
        if (guard || fault) {
            s.circle(lx, ly, 8.5f, fault ? SecurityTheme.RED_WASH : SecurityTheme.GREEN_WASH);
        }
        s.circle(lx, ly, 5.5f, lamp);
        if (guard || fault || standby) {
            s.circle(lx - 1.6f, ly - 1.8f, 1.4f, 0x99FFFFFF);
        } else {
            s.circle(lx, ly, 4.5f, SecurityTheme.CANVAS);
        }
        final Rect chip = SecurityTheme.chipRight(s, card.right() - Tokens.S2, card.y + 10.0f,
                CoilWords.state(now).getString().toUpperCase(Locale.ROOT), stateWash(now), stateInk(now),
                stateEdge(now));
        s.text(Text.truncate(s, head, chip.x - Tokens.S1 - tx, Text.BODY), tx, card.y + 6.0f, ink, Text.BODY);
        for (int i = 0; i < subCount; i++) {
            s.text(subLines.get(i), tx, card.y + 17.0f + i * 7.0f, SecurityTheme.TEXT_FAINT, SecurityTheme.MICRO);
        }
        if (rows > 0) {
            s.hairlineH(card.x + 1.0f, card.y + 28.0f + extra, card.w - 2.0f, SecurityTheme.LINE);
            final String[] legends = {tr(K + "legend.stage"), tr(K + "legend.jars"), tr(K + "legend.bank"),
                    tr(K + "legend.last")};
            final String[] values = {
                    tr(K + "value.stage", c.stage(), CoilStage.values().length),
                    tr(K + "value.jars", c.volleys(), c.volleysMax()),
                    tr(K + "value.bank", c.strikes(), c.strikesMax()),
                    c.sinceArc() < 0 ? tr(K + "last.never") : tr(K + "last.what", c.lastArc(), ago(c.sinceArc()))};
            float legendW = 0.0f;
            for (String l : legends) {
                legendW = Math.max(legendW, s.textWidth(l, SecurityTheme.MICRO));
            }
            final float vx = card.x + Tokens.S2 + legendW + Tokens.S2;
            float ry = card.y + 32.0f + extra;
            for (int i = 0; i < 4; i++) {
                s.text(legends[i], card.x + Tokens.S2, ry + 1.0f, SecurityTheme.TEXT_FAINT, SecurityTheme.MICRO);
                float x = vx;
                if (i == 1 || i == 2) {
                    final int held = i == 1 ? c.volleys() : c.strikes();
                    final int max = i == 1 ? c.volleysMax() : c.strikesMax();
                    final float w = i == 1 ? 5.0f : 3.0f;
                    for (int p = 0; p < max; p++) {
                        s.roundedRect(new Rect(x, ry + 1.0f, w, 6.0f), 1.0f,
                                p < held ? SecurityTheme.STEEL : SecurityTheme.LINE);
                        x += w + 1.0f;
                    }
                    x += Tokens.S1;
                }
                s.text(Text.truncate(s, values[i], card.right() - Tokens.S2 - x, SecurityTheme.MICRO), x, ry + 1.0f,
                        SecurityTheme.TEXT_DIM, SecurityTheme.MICRO);
                ry += lh;
            }
        }
        y = card.bottom() + 4.0f;
        if (!c.governed() && c.loaded()) {
            y = note(s, r, y, tr(K + "joining"), SecurityTheme.AMBER_INK) + 2.0f;
        }
        return y + Tokens.S3 - 4.0f;
    }

    private float drawGuard(PanelSurface s, Rect r, SecuritySyncPacket.CoilRow c, float y, float mx, float my) {
        final boolean on = c.on();
        toggleRow(s, new Rect(r.x, y, r.w, SecurityTheme.OPT_H), tr(K + (on ? "on" : "off")),
                tr(K + (on ? "on.foot" : "off.foot")), on, this.ctx.mayEditDevices(), mx, my,
                () -> this.ctx.setCoilOn(c.pos(), !on));
        return y + SecurityTheme.OPT_H + Tokens.S3;
    }

    private float drawRules(PanelSurface s, Rect r, SecuritySyncPacket.CoilRow c, float y, float mx, float my) {
        SecurityTheme.head(s, r.x, y, tr(K + "head.rules"));
        y += Text.lineHeight(SecurityTheme.MICRO) + 4.0f;
        final boolean may = this.ctx.mayEditDevices();
        final boolean pvp = this.ctx.pvp();
        final HouseState now = this.ctx.houseState();
        final HouseState[] states = HouseState.all();

        float labelW = 0.0f;
        for (String k : ROWS) {
            labelW = Math.max(labelW, s.textWidth(tr(K + "row." + k), SecurityTheme.MICRO));
        }
        labelW += Tokens.S2;
        final float colW = (r.w - labelW) / states.length;
        final String[] heads = new String[states.length];
        float headW = 0.0f;
        for (int i = 0; i < states.length; i++) {
            heads[i] = CoilWords.state(states[i]).getString().toUpperCase(Locale.ROOT);
            headW = Math.max(headW, s.textWidth(heads[i], SecurityTheme.MICRO));
        }
        final boolean stagger = colW < headW + 4.0f;
        final float lineH = Text.lineHeight(SecurityTheme.MICRO) + 1.0f;
        final float headH = stagger ? lineH * 2.0f + 3.0f : lineH + 3.0f;
        final float rowH = 16.0f;
        final float gap = 2.0f;
        final float gridH = headH + ROWS.length * (rowH + gap);

        final int at = now.ordinal();
        s.card(new Rect(r.x + labelW + at * colW + 1.0f, y - 1.0f, colW - 2.0f, gridH + 1.0f), Tokens.R_CHIP,
                stateWash(now), stateEdge(now));
        for (int i = 0; i < states.length; i++) {
            final float cx = r.x + labelW + i * colW + colW * 0.5f;
            final float w = s.textWidth(heads[i], SecurityTheme.MICRO);
            final float x = Math.max(r.x + labelW, Math.min(r.right() - w, cx - w * 0.5f));
            final int line = stagger ? i % 2 : 0;
            s.text(heads[i], x, y + 2.0f + line * lineH, i == at ? stateInk(states[i]) : SecurityTheme.TEXT_FAINT,
                    SecurityTheme.MICRO);
            if (stagger && line == 0) {
                s.rect(cx - 0.5f, y + 2.0f + lineH - 1.0f, 1.0f, lineH + 1.0f,
                        i == at ? stateEdge(states[i]) : SecurityTheme.LINE);
            }
        }

        float ry = y + headH;
        for (int ri = 0; ri < ROWS.length; ri++) {
            final String k = ROWS[ri];
            final boolean dead = ri >= 2 && !pvp;
            s.textV(tr(K + "row." + k), r.x, ry + rowH * 0.5f, dead ? SecurityTheme.OFF : SecurityTheme.TEXT_DIM,
                    SecurityTheme.MICRO);
            for (int i = 0; i < states.length; i++) {
                final HouseState state = states[i];
                final Rule rule = shown(c, state);
                final float cx = r.x + labelW + i * colW + colW * 0.5f;
                final float cy = ry + rowH * 0.5f;
                if ("members".equals(k)) {
                    drawMembersCell(s, c, state, rule, cx, cy, colW, may, dead, mx, my);
                } else {
                    drawCheckCell(s, c, state, rule, ri, cx, cy, may, dead, mx, my);
                }
            }
            ry += rowH + gap;
        }
        y = ry + 2.0f;
        if (!pvp) {
            y = note(s, r, y, tr(K + "note.pvp"), SecurityTheme.AMBER_INK) + 2.0f;
        }
        if (c.following()) {
            y = note(s, r, y, tr(K + "note.following"), SecurityTheme.TEXT_FAINT) + 2.0f;
        }
        if (!may) {
            y = note(s, r, y, tr(K + "note.member"), SecurityTheme.TEXT_FAINT) + 2.0f;
        }
        y = note(s, r, y, tr(K + "note.members"), SecurityTheme.TEXT_FAINT) + 2.0f;
        y = note(s, r, y, tr(K + "note.never"), SecurityTheme.TEXT_FAINT);
        return y + Tokens.S3;
    }

    private void drawCheckCell(PanelSurface s, SecuritySyncPacket.CoilRow c, HouseState state, Rule rule, int row,
                               float cx, float cy, boolean may, boolean dead, float mx, float my) {
        final boolean on = row == 0 ? rule.hostile() : row == 1 ? rule.transform() : rule.strangers();
        final Rect cell = new Rect(cx - 6.0f, cy - 6.0f, 12.0f, 12.0f);
        final boolean hot = may && hot(cell, mx, my);
        s.card(cell, Tokens.R_CHIP,
                on ? (dead ? SecurityTheme.OFF : SecurityTheme.STEEL) : hot ? SecurityTheme.CARD_HOT : SecurityTheme.CARD,
                on ? (dead ? SecurityTheme.OFF : SecurityTheme.STEEL_DEEP) : hot ? SecurityTheme.STEEL_EDGE
                        : SecurityTheme.OFF);
        if (on) {
            s.line(cell.x + 3.0f, cell.y + 6.2f, cell.x + 5.2f, cell.y + 8.4f, 1.4f, 0xFFFFFFFF);
            s.line(cell.x + 5.2f, cell.y + 8.4f, cell.x + 9.2f, cell.y + 3.6f, 1.4f, 0xFFFFFFFF);
        }
        if (may) {
            final Rule next = row == 0 ? rule.withHostile(!on) : row == 1 ? rule.withTransform(!on)
                    : rule.withStrangers(!on);
            hit(cell, () -> ask(c, state, next));
        }
    }

    private void drawMembersCell(PanelSurface s, SecuritySyncPacket.CoilRow c, HouseState state, Rule rule, float cx,
                                 float cy, float colW, boolean may, boolean dead, float mx, float my) {
        final int below = rule.membersBelow();
        final boolean on = below != CoilRules.NO_MEMBERS;
        final String label = on ? tr(K + "members.below", below) : tr(K + "members.none");
        final float w = Math.min(colW - 4.0f, 20.0f);
        final Rect cell = new Rect(cx - w * 0.5f, cy - 6.0f, w, 12.0f);
        final boolean hot = may && hot(cell, mx, my);
        s.card(cell, Tokens.R_CHIP,
                on ? (dead ? SecurityTheme.OFF : SecurityTheme.STEEL) : hot ? SecurityTheme.CARD_HOT : SecurityTheme.CARD,
                on ? (dead ? SecurityTheme.OFF : SecurityTheme.STEEL_DEEP) : hot ? SecurityTheme.STEEL_EDGE
                        : dead ? SecurityTheme.LINE : SecurityTheme.OFF);
        s.textCenterV(label, cell.centerX(), cell.centerY() + 0.5f,
                on ? 0xFFFFFFFF : dead ? SecurityTheme.OFF : SecurityTheme.TEXT_FAINT, SecurityTheme.MICRO);
        if (may) {
            hit(cell, () -> ask(c, state, rule.withMembersBelow(CoilRules.nextBelow(below))));
        }
    }

    private float drawAtBlock(PanelSurface s, Rect r, SecuritySyncPacket.Row row, SecuritySyncPacket.CoilRow c,
                              float y, float mx, float my) {
        SecurityTheme.head(s, r.x, y, tr(K + "head.block"));
        y += Text.lineHeight(SecurityTheme.MICRO) + 4.0f;
        final boolean may = this.ctx.mayEditDevices();
        final boolean locked = c.locked();
        toggleRow(s, new Rect(r.x, y, r.w, SecurityTheme.OPT_H), tr(K + (locked ? "lock" : "unlock")),
                tr(K + (locked ? "lock.foot" : "unlock.foot")), locked, may, mx, my,
                () -> this.ctx.setCoilLocked(c.pos(), !locked));
        y += SecurityTheme.OPT_H + 2.0f;
        final boolean hears = c.hears();
        toggleRow(s, new Rect(r.x, y, r.w, SecurityTheme.OPT_H), tr(K + (hears ? "hears" : "deaf")),
                tr(K + (hears ? "hears.foot" : "deaf.foot")), !hears, may, mx, my,
                () -> this.ctx.setCoilRedstone(c.pos(), !hears));
        y += SecurityTheme.OPT_H + 4.0f;
        y = note(s, r, y, tr(K + (locked ? "note.locked" : "note.open")), SecurityTheme.TEXT_FAINT);
        y = note(s, r, y + 2.0f, tr(K + "note.removal"), SecurityTheme.TEXT_FAINT) + Tokens.S3;
        if (!locked) {
            y = drawLadder(s, r, row, y, mx, my);
        }
        return y;
    }

    private static String ago(int ticks) {
        if (ticks < 20) {
            return tr(K + "ago.now");
        }
        if (ticks < 1200) {
            return tr(K + "ago.seconds", ticks / 20);
        }
        if (ticks < 72000) {
            return tr(K + "ago.minutes", ticks / 1200);
        }
        return tr(K + "ago.hours", ticks / 72000);
    }

    private float note(PanelSurface s, Rect r, float y, String text, int ink) {
        for (String line : Text.wrap(s, text, r.w, SecurityTheme.MICRO)) {
            s.text(line, r.x, y, ink, SecurityTheme.MICRO);
            y += Text.lineHeight(SecurityTheme.MICRO) + 1.0f;
        }
        return y;
    }
}
