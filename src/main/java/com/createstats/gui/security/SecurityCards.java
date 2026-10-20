package com.createstats.gui.security;

import com.createstats.network.SecurityActionPacket;
import com.createstats.network.SecuritySyncPacket;
import com.createstats.statsmk3.ui.PanelSurface;
import com.createstats.statsmk3.ui.Rect;
import com.createstats.statsmk3.ui.Text;
import com.createstats.statsmk3.ui.TextField;
import com.createstats.statsmk3.ui.Tokens;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.List;

@OnlyIn(Dist.CLIENT)
final class SecurityCards {
    private SecurityCards() {
    }

    static final float CARD_H = 52.0f;

    static TextField field(java.util.function.Consumer<String> onCommit, String placeholder) {
        return new TextField(onCommit)
                .palette(SecurityTheme.CANVAS, SecurityTheme.TEXT, SecurityTheme.TEXT_FAINT,
                        SecurityTheme.LINE, SecurityTheme.STEEL)
                .textScale(Text.BODY)
                .placeholder(placeholder);
    }

    private static final float BODY_BESIDE_CONTROL = 0.62f;

    private static final float BODY_FULL_WIDTH = 0.92f;

    static void text(PanelSurface s, Rect card, String title, String body) {
        text(s, card, title, body, BODY_BESIDE_CONTROL);
    }

    static void text(PanelSurface s, Rect card, String title, String body, float widthFraction) {
        s.text(title, card.x + Tokens.S3, card.y + Tokens.S2, SecurityTheme.TEXT, Text.BODY);
        float y = card.y + Tokens.S2 + 12.0f;
        for (String line : Text.wrap(s, body, card.w * widthFraction, SecurityTheme.MICRO)) {
            s.text(line, card.x + Tokens.S3, y, SecurityTheme.TEXT_FAINT, SecurityTheme.MICRO);
            y += Text.lineHeight(SecurityTheme.MICRO) + 1.0f;
        }
    }

    static float houseName(SetupView view, PanelSurface s, TextField field, Rect area, float y,
                           boolean may, float mx, float my) {
        final Rect card = new Rect(area.x, y, area.w, CARD_H);
        s.card(card, Tokens.R_CARD, SecurityTheme.CARD, SecurityTheme.LINE);
        text(s, card, "House name",
                "Shown on this panel, on every keypad in the house, and on the row a second panel "
                        + "sees when it offers to join.");
        final Rect slot = new Rect(card.right() - 150.0f - Tokens.S3, card.centerY() - 9.0f,
                150.0f, 18.0f);
        if (field.visible()) {
            field.setBounds(slot);
            field.draw(s);
        } else {
            final String current = view.ctx.view().houseRaw();
            SecurityTheme.button(s, slot, current.isEmpty() ? "Set a name" : current, false,
                    may && view.hot(slot, mx, my), may);
            if (may) {
                view.hit(slot, () -> view.beginHouseName(current));
            }
        }
        return card.bottom() + Tokens.S2;
    }

    static float password(SetupView view, PanelSurface s, TextField field, Rect area, float y,
                          boolean may, boolean malformed, float mx, float my) {
        final SecuritySyncPacket sync = view.ctx.view();
        final Rect card = new Rect(area.x, y, area.w, CARD_H);
        s.card(card, Tokens.R_CARD, SecurityTheme.CARD, SecurityTheme.LINE);
        text(s, card, sync.hasPassword() ? "Password, set" : "Password, none",
                malformed ? "Four digits, nothing else. Leave it empty to remove the password."
                        : sync.hasPassword()
                                ? "Asked of anybody below keyholder. Nothing here can read it back, "
                                        + "only replace it."
                                : "This panel opens for anyone who can reach it. Set four digits to "
                                        + "ask everybody below keyholder.");
        final Rect slot = new Rect(card.right() - 96.0f - Tokens.S3, card.centerY() - 9.0f,
                96.0f, 18.0f);
        if (field.visible()) {
            field.setBounds(slot);
            field.draw(s);
        } else {
            SecurityTheme.button(s, slot, sync.hasPassword() ? "Change" : "Set", false,
                    may && view.hot(slot, mx, my), may);
            if (may) {
                view.hit(slot, view::beginPassword);
            }
        }
        if (malformed) {
            s.textRight("four digits", card.right() - Tokens.S3, card.bottom() - 10.0f,
                    SecurityTheme.RED, SecurityTheme.MICRO);
        }
        return card.bottom() + Tokens.S2;
    }

    static float alwaysAsk(SetupView view, PanelSurface s, Rect area, float y, boolean may,
                           float mx, float my) {
        final SecuritySyncPacket sync = view.ctx.view();
        final Rect card = new Rect(area.x, y, area.w, CARD_H);
        s.card(card, Tokens.R_CARD, SecurityTheme.CARD, SecurityTheme.LINE);
        text(s, card, "Always ask for the password",
                sync.hasPassword()
                        ? "Includes you. With this off, you and anyone at keyholder walk straight in."
                        : "Worth nothing until a password is set.");
        final float bw = 60.0f;
        final Rect on = new Rect(card.right() - bw * 2.0f - Tokens.S1 - Tokens.S3,
                card.centerY() - SecurityTheme.BTN_H * 0.5f, bw, SecurityTheme.BTN_H);
        final Rect off = new Rect(on.right() + Tokens.S1, on.y, bw, SecurityTheme.BTN_H);
        SecurityTheme.button(s, on, "Always", sync.alwaysAsk(), may && view.hot(on, mx, my), may);
        SecurityTheme.button(s, off, "By level", !sync.alwaysAsk(), may && view.hot(off, mx, my), may);
        if (may) {
            view.hit(on, () -> view.ctx.send(SecurityActionPacket.panel(
                    view.ctx.panel.getBlockPos(), SecurityActionPacket.SET_ALWAYS_ASK, 1, "")));
            view.hit(off, () -> view.ctx.send(SecurityActionPacket.panel(
                    view.ctx.panel.getBlockPos(), SecurityActionPacket.SET_ALWAYS_ASK, 0, "")));
        }
        return card.bottom() + Tokens.S2;
    }

    static float household(SetupView view, PanelSurface s, Rect area, float y, float mx, float my) {
        final SecuritySyncPacket sync = view.ctx.view();
        final List<SecuritySyncPacket.Candidate> joinable = sync.joinable();
        final Rect card = new Rect(area.x, y, area.w, CARD_H + joinable.size() * 24.0f);
        s.card(card, Tokens.R_CARD, SecurityTheme.CARD, SecurityTheme.LINE);

        final String panels = sync.panels() == 1 ? "1 panel" : sync.panels() + " panels";
        final String asleep = sync.sleeping() == 0 ? "" : ", " + sync.sleeping() + " asleep";

        text(s, card, "This household, " + panels + asleep,
                sync.sleeping() > 0
                        ? "A sleeping panel's wing is not on the device list until its chunk loads."
                        : "Every panel here shares one roster, one log and one lockdown.",
                BODY_FULL_WIDTH);

        float ty = card.y + CARD_H - 8.0f;
        for (SecuritySyncPacket.Candidate c : joinable) {
            if (ty + 20.0f > card.bottom()) {
                break;
            }
            final Rect row = new Rect(card.x + Tokens.S3, ty, card.w - Tokens.S3 * 2.0f, 20.0f);
            final float removeWidth = c.removable() ? 46.0f : 0.0f;
            final Rect join = new Rect(row.x, row.y,
                    row.w - (c.removable() ? removeWidth + Tokens.S1 : 0.0f), row.h);
            final boolean hot = c.joinable() && view.hot(join, mx, my);
            s.card(join, Tokens.R_CHIP, SecurityTheme.CANVAS,
                    hot ? SecurityTheme.STEEL : SecurityTheme.LINE);
            final String name = c.house().isEmpty() ? "an unnamed house" : c.house();
            s.textV((c.joinable() ? "Join " : "Saved ") + name,
                    join.x + Tokens.S2, join.centerY(),
                    hot ? SecurityTheme.TEXT : SecurityTheme.TEXT_DIM, SecurityTheme.MICRO);

            final String place = c.noPanels() ? "saved, no panel" : "on this circuit";
            final String tag = c.locked() && c.joinable() ? place + ", code" : place;
            if (!tag.isEmpty()) {
                s.textRightV(tag, join.right() - Tokens.S2, join.centerY(),
                        SecurityTheme.TEXT_FAINT, SecurityTheme.MICRO);
            }
            if (c.joinable()) {
                view.hit(join, () -> view.ctx.send(SecurityActionPacket.person(
                        view.ctx.panel.getBlockPos(), SecurityActionPacket.JOIN_HOUSEHOLD,
                        c.id(), 0)));
            }
            if (c.removable()) {
                final Rect remove = new Rect(join.right() + Tokens.S1, row.y, removeWidth, row.h);
                final boolean armed = view.removalArmed(c.id());
                final boolean removeHot = view.hot(remove, mx, my);
                s.card(remove, Tokens.R_CHIP,
                        armed || removeHot ? SecurityTheme.RED_WASH : SecurityTheme.CARD,
                        SecurityTheme.RED_EDGE);
                s.textCenterV(armed ? "Sure?" : "Remove", remove.centerX(), remove.centerY(),
                        SecurityTheme.RED, SecurityTheme.MICRO);
                view.hit(remove, () -> view.requestHouseholdRemoval(c.id()));
            }
            ty += 24.0f;
        }
        return card.bottom() + Tokens.S2;
    }
}
