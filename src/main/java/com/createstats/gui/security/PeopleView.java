package com.createstats.gui.security;

import com.createstats.network.SecurityActionPacket;
import com.createstats.network.SecuritySyncPacket;
import com.createstats.security.Clearance;
import com.createstats.security.SecurityRole;
import com.createstats.statsmk3.ui.PanelSurface;
import com.createstats.statsmk3.ui.Rect;
import com.createstats.statsmk3.ui.Scroll;
import com.createstats.statsmk3.ui.Text;
import com.createstats.statsmk3.ui.TextField;
import com.createstats.statsmk3.ui.Tokens;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.List;

@OnlyIn(Dist.CLIENT)
public final class PeopleView extends SecurityView {
    private final Scroll scroll = new Scroll();

    private final TextField addField;

    public PeopleView(SecurityContext ctx) {
        super(ctx);
        this.addField = SecurityCards.field(this::commitAdd, "Player name");
        this.addField.setVisible(false);
    }

    @Override
    public String id() {
        return "people";
    }

    @Override
    public String label() {
        return "People";
    }

    @Override
    public int badge() {
        return this.ctx.people().size();
    }

    @Override
    public void drawIcon(PanelSurface s, float cx, float cy, int color) {
        SecurityIcons.people(s, cx, cy, color);
    }

    @Override
    public void draw(PanelSurface s, float mx, float my) {
        final List<SecuritySyncPacket.Person> people = this.ctx.people();

        final int deviceCount = this.ctx.gatedDeviceCount();

        float y = this.area.y;
        s.text("People", this.area.x, y, SecurityTheme.TEXT, Text.SUB);
        s.textRight(Text.track("ROLE IS WHAT YOU CHANGE . CLEARANCE IS WHAT YOU OPEN"),
                this.area.right(), y + 4.0f, SecurityTheme.TEXT_FAINT, SecurityTheme.MICRO);
        y += Text.lineHeight(Text.SUB) + Tokens.S2;

        if (this.ctx.mayEditRoster()) {
            y = drawAdd(s, y, mx, my);
        }

        final Rect list = new Rect(this.area.x, y, this.area.w, this.area.bottom() - y);
        final float pitch = SecurityTheme.ROW_H + Tokens.S1;
        this.scroll.setMetrics(people.size() * pitch, list.h);

        s.clip(list);
        final float top = list.y - this.scroll.value();
        for (int i = 0; i < people.size(); i++) {
            final Rect r = new Rect(list.x, top + i * pitch, list.w, SecurityTheme.ROW_H);
            if (r.bottom() < list.y || r.y > list.bottom()) {
                continue;
            }
            drawRow(s, r, people.get(i), deviceCount, mx, my);
        }
        s.unclip();
    }

    private float drawAdd(PanelSurface s, float y, float mx, float my) {
        final Rect card = new Rect(this.area.x, y, this.area.w, 30.0f);
        s.card(card, Tokens.R_CARD, SecurityTheme.CARD, SecurityTheme.LINE);
        s.textV("Add player", card.x + Tokens.S3, card.centerY(), SecurityTheme.TEXT_DIM, Text.BODY);

        final float slotW = 130.0f;
        final Rect slot = new Rect(card.right() - slotW - Tokens.S3, card.centerY() - 9.0f, slotW,
                18.0f);
        if (this.addField.visible()) {
            this.addField.setBounds(slot);
            this.addField.draw(s);
        } else {
            SecurityTheme.button(s, slot, "+ Add by name", false, hot(slot, mx, my), true);
            hit(slot, this::beginAdd);
        }
        y = card.bottom() + Tokens.S1;

        final List<SecuritySyncPacket.Online> candidates = this.ctx.online();
        if (!candidates.isEmpty() && !this.addField.editing()) {
            final Rect row = new Rect(this.area.x, y, this.area.w, SecurityTheme.CHIP_H * 2.0f + 3.0f);
            y = drawOnlineRow(s, row, candidates, mx, my) + Tokens.S2;
        } else {
            y += Tokens.S1;
        }
        return y;
    }

    private void beginAdd() {
        this.addField.begin("");
        this.addField.setVisible(true);
    }

    private void commitAdd(String typed) {
        if (this.ctx.mayEditRoster() && !typed.isEmpty()) {
            this.ctx.addPlayer(typed);
        }
        this.addField.setVisible(false);
    }

    private float drawOnlineRow(PanelSurface s, Rect row, List<SecuritySyncPacket.Online> candidates,
                                float mx, float my) {
        float x = row.x;
        float y = row.y;
        for (SecuritySyncPacket.Online o : candidates) {
            final String label = "+ " + o.name();
            final float w = s.textWidth(label, SecurityTheme.MICRO) + Tokens.S3;
            if (x + w > row.right() && x > row.x) {
                x = row.x;
                y += SecurityTheme.CHIP_H + 3.0f;
            }
            if (y + SecurityTheme.CHIP_H > row.bottom()) {
                break;
            }
            final Rect chip = new Rect(x, y, w, SecurityTheme.CHIP_H);
            final boolean hot = hot(chip, mx, my);
            s.card(chip, Tokens.R_CHIP, hot ? SecurityTheme.CARD_HOT : SecurityTheme.CANVAS,
                    hot ? SecurityTheme.STEEL_EDGE : SecurityTheme.LINE);
            s.textCenterV(label, chip.centerX(), chip.centerY(),
                    hot ? SecurityTheme.STEEL_DEEP : SecurityTheme.TEXT_DIM, SecurityTheme.MICRO);
            hit(chip, () -> this.ctx.addPlayer(o.name()));
            x += w + Tokens.S1;
        }
        return y + SecurityTheme.CHIP_H;
    }

    private void drawRow(PanelSurface s, Rect r, SecuritySyncPacket.Person p, int deviceCount,
                         float mx, float my) {
        final boolean selected = p.id().equals(this.ctx.selectedPerson);
        final boolean hot = hot(r, mx, my);
        SecurityTheme.card(s, r, hot, selected);
        hit(r, () -> this.ctx.selectedPerson = p.id());

        final Rect in = r.inset(Tokens.S2, 0.0f, Tokens.S2, 0.0f);
        float x = in.x;

        final float av = 16.0f;
        SecurityTheme.avatar(s, new Rect(x, r.centerY() - av * 0.5f, av, av), p.name(), p.isOwner());
        x += av + Tokens.S2;

        final String opens = p.opens() + " of " + deviceCount;
        final float opensW = s.textWidth(opens, SecurityTheme.MICRO);
        s.textRightV(opens, in.right(), r.centerY(), SecurityTheme.TEXT_DIM, SecurityTheme.MICRO);
        float right = in.right() - opensW - Tokens.S2;

        final float barW = 48.0f;
        if (in.w > 260.0f) {
            final float frac = deviceCount == 0 ? 0.0f : (float) p.opens() / deviceCount;
            SecurityTheme.bar(s, new Rect(right - barW, r.centerY() - 2.0f, barW, 4.0f), frac,
                    p.isOwner() ? SecurityTheme.STEEL : SecurityTheme.STEEL_EDGE);
            right -= barW + Tokens.S3;
        }

        if (p.isOwner()) {
            final float kw = s.textWidth("BYPASS", SecurityTheme.MICRO) + Tokens.S4;
            final Rect chip = new Rect(right - kw, r.centerY() - SecurityTheme.CHIP_H * 0.5f, kw,
                    SecurityTheme.CHIP_H);
            s.card(chip, Tokens.R_CHIP, SecurityTheme.STEEL, SecurityTheme.STEEL_DEEP);
            SecurityIcons.key(s, chip.x + 7.0f, chip.centerY(), 0xFFFFFFFF);
            s.textV("BYPASS", chip.x + 13.0f, chip.centerY(), 0xFFFFFFFF, SecurityTheme.MICRO);
            right = chip.x - Tokens.S2;
        } else {
            final String label = p.level() + " " + Clearance.plain(p.level());
            final float cw = s.textWidth(label, SecurityTheme.MICRO) + Tokens.S3;
            SecurityTheme.levelChip(s, right - cw, r.centerY(), p.level(),
                    Clearance.plain(p.level()));
            right -= cw + Tokens.S2;
        }

        final SecurityRole role = p.roleOf();
        final String roleLabel = role.name();
        final float rw = s.textWidth(roleLabel, SecurityTheme.MICRO) + Tokens.S3;
        if (right - rw > x + 40.0f) {
            SecurityTheme.chip(s, right - rw, r.centerY(), roleLabel,
                    role == SecurityRole.MEMBER ? SecurityTheme.CANVAS : SecurityTheme.STEEL_WASH,
                    role == SecurityRole.MEMBER ? SecurityTheme.TEXT_FAINT : SecurityTheme.STEEL_DEEP,
                    role == SecurityRole.MEMBER ? SecurityTheme.LINE : SecurityTheme.STEEL_EDGE);
            right -= rw + Tokens.S2;
        }

        s.textV(Text.truncate(s, p.name(), Math.max(20.0f, right - x), Text.BODY), x, r.centerY(),
                SecurityTheme.TEXT, Text.BODY);
    }

    @Override
    public void update(float dt) {
        this.scroll.update(dt);
        this.addField.update(dt);
    }

    @Override
    public boolean scroll(double mx, double my, double amount) {
        return this.scroll.wheel(amount);
    }

    @Override
    public boolean click(double mx, double my) {
        if (this.addField.visible() && this.addField.mouseDown(mx, my, 0)) {
            return true;
        }
        if (this.addField.editing()) {
            this.addField.commit();
            return true;
        }
        return super.click(mx, my);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.addField.editing() && this.addField.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        if (this.addField.editing() && this.addField.charTyped(c, modifiers)) {
            return true;
        }
        return super.charTyped(c, modifiers);
    }

    @Override
    public void drawDock(PanelSurface s, Rect r, float mx, float my) {
        final SecuritySyncPacket.Person p = this.ctx.selectedPersonOr();
        if (p == null) {
            SecurityTheme.head(s, r.x, r.y, "NOBODY ON THE ROSTER");
            return;
        }

        float y = r.y;
        final float av = 26.0f;
        SecurityTheme.avatar(s, new Rect(r.x, y, av, av), p.name(), p.isOwner());
        s.text(Text.truncate(s, p.name(), r.w - av - Tokens.S2, Text.SUB), r.x + av + Tokens.S2,
                y + 2.0f, SecurityTheme.TEXT, Text.SUB);
        s.text(p.roleOf().name(), r.x + av + Tokens.S2, y + 14.0f, SecurityTheme.TEXT_FAINT,
                SecurityTheme.MICRO);
        y += av + Tokens.S4;

        final boolean mayRole = this.ctx.mayEditPanel() && !p.isOwner();
        SecurityTheme.head(s, r.x, y, "ROLE, WHAT THEY MAY CHANGE");
        y += Text.lineHeight(SecurityTheme.MICRO) + 4.0f;
        final float half = (r.w - Tokens.S1) * 0.5f;
        for (int i = 0; i < 2; i++) {
            final SecurityRole role = i == 0 ? SecurityRole.ADMIN : SecurityRole.MEMBER;
            final Rect opt = new Rect(r.x + i * (half + Tokens.S1), y, half, SecurityTheme.OPT_H);
            final boolean on = p.roleOf() == role;
            SecurityTheme.option(s, opt, role.name(), on, mayRole && hot(opt, mx, my));
            if (mayRole) {
                hit(opt, () -> this.ctx.setPersonRole(p.id(), role));
            }
        }
        y += SecurityTheme.OPT_H + Tokens.S3;

        SecurityTheme.head(s, r.x, y, p.isOwner() ? "CLEARANCE, NOT ON THE LADDER"
                : "CLEARANCE, WHAT THEY MAY OPEN");
        y += Text.lineHeight(SecurityTheme.MICRO) + 4.0f;

        if (p.isOwner()) {
            final Rect note = new Rect(r.x, y, r.w, 34.0f);
            s.card(note, Tokens.R_CHIP, SecurityTheme.STEEL_WASH, SecurityTheme.STEEL_EDGE);
            for (String line : Text.wrap(s,
                    "The owner passes every device. That is a check of its own, so it survives every "
                            + "level in the house being changed.", note.w - Tokens.S4, SecurityTheme.MICRO)) {
                s.text(line, note.x + Tokens.S2, y + 5.0f, SecurityTheme.STEEL_DEEP, SecurityTheme.MICRO);
                y += Text.lineHeight(SecurityTheme.MICRO) + 1.0f;
            }
            return;
        }

        final boolean mayLevel = this.ctx.mayEditRoster();
        for (int level = Clearance.MIN; level <= Clearance.MAX; level++) {
            final Rect opt = new Rect(r.x, y, r.w, SecurityTheme.OPT_H);
            final boolean on = p.level() == level;
            s.card(opt, Tokens.R_CHIP, on ? SecurityTheme.ladderBg(level)
                            : mayLevel && hot(opt, mx, my) ? SecurityTheme.CARD_HOT : SecurityTheme.CANVAS,
                    on ? SecurityTheme.ladderEdge(level) : SecurityTheme.LINE);
            final int ink = on ? SecurityTheme.ladderInk(level) : SecurityTheme.TEXT_DIM;
            s.textV(String.valueOf(level), opt.x + Tokens.S2, opt.centerY(), ink, SecurityTheme.MICRO);
            s.textV(Clearance.plain(level), opt.x + Tokens.S2 + 10.0f, opt.centerY(), ink, SecurityTheme.MICRO);
            s.textRightV(devicesAt(level) + " devices", opt.right() - Tokens.S2, opt.centerY(),
                    on ? ink : SecurityTheme.TEXT_FAINT, SecurityTheme.MICRO);
            if (mayLevel) {
                final int chosen = level;
                hit(opt, () -> this.ctx.setPersonLevel(p.id(), chosen));
            }
            y += SecurityTheme.OPT_H + 2.0f;
        }

        if (this.ctx.mayEditRoster() && !p.isOwner()) {
            final Rect remove = new Rect(r.x, r.bottom() - SecurityTheme.BTN_H, r.w,
                    SecurityTheme.BTN_H);
            final boolean hot = hot(remove, mx, my);
            s.card(remove, Tokens.R_CHIP, hot ? SecurityTheme.RED_WASH : SecurityTheme.CARD,
                    SecurityTheme.RED_EDGE);
            s.textCenterV("Remove from roster", remove.centerX(), remove.centerY(),
                    SecurityTheme.RED, SecurityTheme.MICRO);
            hit(remove, () -> this.ctx.send(SecurityActionPacket.person(
                    this.ctx.panel.getBlockPos(), SecurityActionPacket.REMOVE_PLAYER, p.id(), 0)));
        }
    }

    private int devicesAt(int level) {
        int n = 0;
        for (SecuritySyncPacket.Row row : this.ctx.devices()) {
            if (row.required() <= level) {
                n++;
            }
        }
        return n;
    }
}
