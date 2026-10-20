package com.createstats.gui.security;

import com.createstats.network.SecuritySyncPacket;
import com.createstats.security.Clearance;
import com.createstats.statsmk3.ui.PanelSurface;
import com.createstats.statsmk3.ui.Rect;
import com.createstats.statsmk3.ui.Scroll;
import com.createstats.statsmk3.ui.Text;
import com.createstats.statsmk3.ui.Tokens;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.List;

@OnlyIn(Dist.CLIENT)
public final class AccessView extends SecurityView {
    private static final float NAME_W = 132.0f;
    private static final float CELL_H = 18.0f;
    private static final float PITCH = CELL_H + 3.0f;
    private static final float HEAD_H = 30.0f;

    private static final float MIN_CELL_W = 14.0f;

    private final Scroll scroll = new Scroll();

    public AccessView(SecurityContext ctx) {
        super(ctx);
    }

    @Override
    public String id() {
        return "access";
    }

    @Override
    public String label() {
        return "Access";
    }

    @Override
    public String group() {
        return "policy";
    }

    @Override
    public void drawIcon(PanelSurface s, float cx, float cy, int color) {
        SecurityIcons.grid(s, cx, cy, color);
    }

    @Override
    public void draw(PanelSurface s, float mx, float my) {
        final List<SecuritySyncPacket.Row> devices = gated();
        final int hidden = this.ctx.devices().size() - devices.size();
        final List<SecuritySyncPacket.Person> people = this.ctx.people();

        float y = this.area.y;
        s.text("Access", this.area.x, y, SecurityTheme.TEXT, Text.SUB);

        s.textRight(Text.track(hidden == 0
                        ? "DERIVED, NOT STORED . A CELL PASSES WHEN CLEARANCE MEETS THE LEVEL"
                        : "DERIVED, NOT STORED . " + hidden + " UNGATED "
                                + (hidden == 1 ? "DEVICE IS" : "DEVICES ARE") + " NOT LISTED"),
                this.area.right(), y + 4.0f, SecurityTheme.TEXT_FAINT, SecurityTheme.MICRO);
        y += Text.lineHeight(Text.SUB) + Tokens.S2;

        final Rect board = new Rect(this.area.x, y, this.area.w, this.area.bottom() - y);
        s.card(board, Tokens.R_CARD, SecurityTheme.CARD, SecurityTheme.LINE);

        if (devices.isEmpty() || people.isEmpty()) {
            s.textCenterV(devices.isEmpty()
                            ? hidden > 0 ? "Only hazard sensors here, and nothing grants access to one"
                                    : "No devices to grant access to"
                            : "Nobody on the roster yet",
                    board.centerX(), board.centerY(), SecurityTheme.TEXT_FAINT, Text.BODY);
            return;
        }

        final Rect inner = board.inset(Tokens.S3);
        final float gridW = inner.w - NAME_W;
        final float cellW = gridW / people.size();

        if (cellW < MIN_CELL_W) {
            drawTooNarrow(s, inner, people.size());
            return;
        }

        drawHeader(s, inner, people, cellW);

        final Rect body = new Rect(inner.x, inner.y + HEAD_H, inner.w,
                inner.h - HEAD_H);
        this.scroll.setMetrics(devices.size() * PITCH, body.h);

        s.clip(body);
        final float top = body.y - this.scroll.value();
        for (int i = 0; i < devices.size(); i++) {
            final float rowY = top + i * PITCH;
            if (rowY + CELL_H < body.y || rowY > body.bottom()) {
                continue;
            }
            drawRow(s, inner.x, rowY, devices.get(i), people, cellW, mx, my);
        }
        s.unclip();
    }

    private void drawHeader(PanelSurface s, Rect inner, List<SecuritySyncPacket.Person> people,
                            float cellW) {
        s.textV(Text.track("DEVICE"), inner.x, inner.y + HEAD_H * 0.5f, SecurityTheme.TEXT_FAINT,
                SecurityTheme.MICRO);
        for (int c = 0; c < people.size(); c++) {
            final SecuritySyncPacket.Person p = people.get(c);
            final float cx = inner.x + NAME_W + c * cellW + cellW * 0.5f;

            if (cellW > 44.0f) {
                s.textCenterV(Text.truncate(s, p.name(), cellW - 2.0f, SecurityTheme.MICRO), cx,
                        inner.y + 6.0f, SecurityTheme.TEXT_DIM, SecurityTheme.MICRO);
            } else {
                SecurityTheme.avatar(s, new Rect(cx - 6.0f, inner.y + 1.0f, 12.0f, 12.0f),
                        p.name(), p.isOwner());
            }

            final String badge = p.isOwner() ? "K" : String.valueOf(p.level());
            final Rect chip = new Rect(cx - 7.0f, inner.y + 16.0f, 14.0f, 11.0f);
            s.roundedRect(chip, Tokens.R_CHIP, p.isOwner() ? SecurityTheme.STEEL
                    : SecurityTheme.ladderBg(p.level()));
            s.textCenterV(badge, chip.centerX(), chip.centerY(),
                    p.isOwner() ? 0xFFFFFFFF : SecurityTheme.ladderInk(p.level()), SecurityTheme.MICRO);
        }
        s.hairlineH(inner.x, inner.y + HEAD_H - 2.0f, inner.w, SecurityTheme.LINE);
    }

    private void drawRow(PanelSurface s, float x, float y, SecuritySyncPacket.Row device,
                         List<SecuritySyncPacket.Person> people, float cellW, float mx, float my) {
        final float centerY = y + CELL_H * 0.5f;

        final Rect chip = new Rect(x + NAME_W - 18.0f, centerY - 5.5f, 14.0f, 11.0f);
        s.roundedRect(chip, Tokens.R_CHIP, SecurityTheme.ladderBg(device.required()));
        s.textCenterV(String.valueOf(device.required()), chip.centerX(), chip.centerY(),
                SecurityTheme.ladderInk(device.required()), SecurityTheme.MICRO);
        s.textV(Text.truncate(s, device.name(), NAME_W - 24.0f, SecurityTheme.MICRO), x, centerY,
                SecurityTheme.TEXT, SecurityTheme.MICRO);

        for (int c = 0; c < people.size(); c++) {
            final SecuritySyncPacket.Person p = people.get(c);
            final Rect cell = new Rect(x + NAME_W + c * cellW + 1.0f, y, cellW - 2.0f, CELL_H);
            final boolean picked = device.pos().equals(this.ctx.cellDevice)
                    && p.id().equals(this.ctx.cellPerson);

            if (p.isOwner()) {
                s.roundedRect(cell, Tokens.R_CHIP, SecurityTheme.STEEL);
                SecurityIcons.key(s, cell.centerX() - 1.0f, cell.centerY(), 0xFFFFFFFF);
            } else if (this.ctx.passes(p, device)) {
                s.card(cell, Tokens.R_CHIP, SecurityTheme.STEEL_WASH, SecurityTheme.STEEL_EDGE);
            } else {
                SecurityTheme.hatch(s, cell, SecurityTheme.RED_WASH, 0x40D0553F);
            }
            if (picked) {
                s.roundedRectOutline(cell, Tokens.R_CHIP, 1.4f, SecurityTheme.STEEL_DEEP);
            } else if (hot(cell, mx, my)) {
                s.roundedRectOutline(cell, Tokens.R_CHIP, 1.0f, SecurityTheme.STEEL_EDGE);
            }
            hit(cell, () -> {
                this.ctx.cellDevice = device.pos();
                this.ctx.cellPerson = p.id();
                this.ctx.selectedDevice = device.pos();
                this.ctx.selectedPerson = p.id();
            });
        }
    }

    private List<SecuritySyncPacket.Row> gated() {
        final List<SecuritySyncPacket.Row> out = new java.util.ArrayList<>();
        for (SecuritySyncPacket.Row row : this.ctx.devices()) {
            if (row.clearanceGated()) {
                out.add(row);
            }
        }
        return out;
    }

    private void drawTooNarrow(PanelSurface s, Rect inner, int people) {
        float y = inner.centerY() - 18.0f;
        s.textCenterV("Too many people for a grid this wide", inner.centerX(), y,
                SecurityTheme.TEXT_DIM, Text.BODY);
        y += 16.0f;
        for (String line : Text.wrap(s, people + " on the roster. Widen the window, or use the "
                        + "People view, where each person's own row carries the same answer one "
                        + "slice at a time.",
                Math.min(360.0f, inner.w - Tokens.S4), SecurityTheme.MICRO)) {
            s.textCenterV(line, inner.centerX(), y, SecurityTheme.TEXT_FAINT, SecurityTheme.MICRO);
            y += Text.lineHeight(SecurityTheme.MICRO) + 2.0f;
        }
    }

    @Override
    public void update(float dt) {
        this.scroll.update(dt);
    }

    @Override
    public boolean scroll(double mx, double my, double amount) {
        return this.scroll.wheel(amount);
    }

    @Override
    public void drawDock(PanelSurface s, Rect r, float mx, float my) {
        float y = r.y;
        SecurityTheme.head(s, r.x, y, "SELECTED CELL");
        y += Text.lineHeight(SecurityTheme.MICRO) + 4.0f;

        final SecuritySyncPacket.Row device = this.ctx.cellDevice == null ? null
                : this.ctx.device(this.ctx.cellDevice);
        final SecuritySyncPacket.Person person = this.ctx.cellPerson == null ? null
                : this.ctx.person(this.ctx.cellPerson);

        if (device == null || person == null) {
            final Rect note = new Rect(r.x, y, r.w, 46.0f);
            s.card(note, Tokens.R_CHIP, SecurityTheme.CANVAS, SecurityTheme.LINE);
            float ny = note.y + 6.0f;
            for (String line : Text.wrap(s, "Click a cell. Every one is a single comparison, so the "
                    + "panel can always say why it reads the way it does.",
                    note.w - Tokens.S4, SecurityTheme.MICRO)) {
                s.text(line, note.x + Tokens.S2, ny, SecurityTheme.TEXT_FAINT, SecurityTheme.MICRO);
                ny += Text.lineHeight(SecurityTheme.MICRO) + 1.0f;
            }
            return;
        }

        final boolean owner = person.isOwner();
        final boolean passes = this.ctx.passes(person, device);
        final int bg = owner ? SecurityTheme.STEEL_WASH
                : passes ? SecurityTheme.GREEN_WASH : SecurityTheme.RED_WASH;
        final int edge = owner ? SecurityTheme.STEEL_EDGE
                : passes ? SecurityTheme.GREEN_EDGE : SecurityTheme.RED_EDGE;
        final int ink = owner ? SecurityTheme.STEEL_DEEP
                : passes ? SecurityTheme.GREEN_DEEP : SecurityTheme.RED;

        final boolean sensor = device.isSensor();
        final String verdict = owner ? "Owner bypass"
                : sensor ? passes ? "Walks past unseen" : "Trips it"
                : passes ? "Passes" : "Denied";
        final String explain = owner
                ? person.name() + " passes " + device.name() + " because the owner passes every "
                        + "device. That check runs before the ladder, so lowering levels never "
                        + "changes it."
                : person.name() + " holds " + person.level() + " "
                        + Clearance.plain(person.level()) + " and " + device.name() + " requires "
                        + device.required() + " " + Clearance.plain(device.required()) + ".";

        final List<String> lines = Text.wrap(s, explain, r.w - Tokens.S4, SecurityTheme.MICRO);
        final float noteH = 18.0f + lines.size() * (Text.lineHeight(SecurityTheme.MICRO) + 1.0f);
        final Rect note = new Rect(r.x, y, r.w, noteH);
        s.card(note, Tokens.R_CHIP, bg, edge);
        s.text(verdict, note.x + Tokens.S2, note.y + 5.0f, ink, Text.BODY);
        float ny = note.y + 17.0f;
        for (String line : lines) {
            s.text(line, note.x + Tokens.S2, ny, ink, SecurityTheme.MICRO);
            ny += Text.lineHeight(SecurityTheme.MICRO) + 1.0f;
        }
        y += noteH + Tokens.S3;

        if (owner || passes || !this.ctx.mayEditRoster()) {
            return;
        }

        SecurityTheme.head(s, r.x, y, "TWO WAYS TO FIX IT");
        y += Text.lineHeight(SecurityTheme.MICRO) + 4.0f;

        final Rect raise = new Rect(r.x, y, r.w, 24.0f);
        final boolean raiseHot = hot(raise, mx, my);
        s.card(raise, Tokens.R_CHIP, raiseHot ? SecurityTheme.CARD_HOT : SecurityTheme.CANVAS,
                SecurityTheme.STEEL_EDGE);
        s.textV(Text.truncate(s, "Raise " + person.name() + " to " + device.required() + " "
                        + Clearance.plain(device.required()), raise.w - Tokens.S4, SecurityTheme.MICRO),
                raise.x + Tokens.S2, raise.centerY(), SecurityTheme.STEEL_DEEP, SecurityTheme.MICRO);
        hit(raise, () -> this.ctx.setPersonLevel(person.id(), device.required()));
        y += 26.0f;

        final boolean mayDevice = this.ctx.mayEditDevices();
        final Rect lower = new Rect(r.x, y, r.w, 24.0f);
        final boolean lowerHot = mayDevice && hot(lower, mx, my);
        s.card(lower, Tokens.R_CHIP, lowerHot ? SecurityTheme.CARD_HOT : SecurityTheme.CANVAS,
                mayDevice ? SecurityTheme.STEEL_EDGE : SecurityTheme.LINE);
        s.textV(Text.truncate(s, "Lower " + device.name() + " to " + person.level() + " "
                        + Clearance.plain(person.level()), lower.w - Tokens.S4, SecurityTheme.MICRO),
                lower.x + Tokens.S2, lower.centerY(),
                mayDevice ? SecurityTheme.STEEL_DEEP : SecurityTheme.TEXT_FAINT, SecurityTheme.MICRO);
        if (mayDevice) {
            hit(lower, () -> this.ctx.setLevel(device.pos(), person.level()));
        }
        y += 26.0f + Tokens.S2;

        for (String line : Text.wrap(s, "Raising the person opens every device at that level. "
                        + "Lowering the device opens it to everyone at or above the new one.",
                r.w, SecurityTheme.MICRO)) {
            s.text(line, r.x, y, SecurityTheme.TEXT_FAINT, SecurityTheme.MICRO);
            y += Text.lineHeight(SecurityTheme.MICRO) + 1.0f;
        }
    }
}
