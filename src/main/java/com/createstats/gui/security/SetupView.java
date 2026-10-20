package com.createstats.gui.security;

import com.createstats.network.SecurityActionPacket;
import com.createstats.network.SecuritySyncPacket;
import com.createstats.security.LockerCode;
import com.createstats.statsmk3.ui.PanelSurface;
import com.createstats.statsmk3.ui.Rect;
import com.createstats.statsmk3.ui.Scroll;
import com.createstats.statsmk3.ui.Text;
import com.createstats.statsmk3.ui.TextField;
import com.createstats.statsmk3.ui.Tokens;
import net.minecraft.Util;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.List;
import java.util.UUID;

// fail secure is a visible setting, defaulted on
@OnlyIn(Dist.CLIENT)
public class SetupView extends SecurityView {
    private static final long REMOVE_CONFIRM_MS = 4_000L;

    private final TextField houseName;
    private final TextField password;

    private final Scroll scroll = new Scroll();

    private float contentHeight;

    private boolean malformed;

    private UUID removingHousehold;
    private long removeUntil;

    public SetupView(SecurityContext ctx) {
        super(ctx);
        this.houseName = SecurityCards.field(this::commitHouseName, "Name this house");
        this.password = SecurityCards.field(this::commitPassword, "....");
        this.houseName.setVisible(false);
        this.password.setVisible(false);
    }

    @Override
    public String id() {
        return "setup";
    }

    @Override
    public String label() {
        return "Setup";
    }

    @Override
    public String group() {
        return "policy";
    }

    @Override
    public void drawIcon(PanelSurface s, float cx, float cy, int color) {
        SecurityIcons.sliders(s, cx, cy, color);
    }

    @Override
    public void draw(PanelSurface s, float mx, float my) {
        final SecuritySyncPacket view = this.ctx.view();
        final boolean may = this.ctx.mayEditPanel();
        final boolean mayRemove = view.joinable().stream()
                .anyMatch(SecuritySyncPacket.Candidate::removable);

        float y = this.area.y;
        s.text("Setup", this.area.x, y, SecurityTheme.TEXT, Text.SUB);
        s.textRight(Text.track(may ? "OWNER"
                        : mayRemove ? "SERVER ADMIN, REMOVAL ONLY"
                        : "READ ONLY, YOU ARE NOT THE OWNER"),
                this.area.right(), y + 4.0f, SecurityTheme.TEXT_FAINT, SecurityTheme.MICRO);
        y += Text.lineHeight(Text.SUB) + Tokens.S2;

        final Rect pane = new Rect(this.area.x, y, this.area.w, this.area.bottom() - y);
        this.scroll.setMetrics(this.contentHeight, pane.h);
        final boolean bar = this.scroll.scrollable();
        final float paneWidth = pane.w - (bar ? Tokens.S2 : 0.0f);
        final float top = y;
        s.clip(pane);
        pushHitClip(pane);
        y -= this.scroll.value();
        final Rect cards = new Rect(this.area.x, this.area.y, paneWidth, this.area.h);

        Rect card = new Rect(cards.x, y, cards.w, 52.0f);
        s.card(card, Tokens.R_CARD, SecurityTheme.CARD, SecurityTheme.LINE);
        SecurityCards.text(s, card, "When a device loses contact with this household",
                "Each device caches the last roster it was sent, so this only applies once that "
                        + "cache goes stale.");
        final float bw = 72.0f;
        final Rect secure = new Rect(card.right() - bw * 2.0f - Tokens.S1 - Tokens.S3,
                card.centerY() - SecurityTheme.BTN_H * 0.5f, bw, SecurityTheme.BTN_H);
        SecurityTheme.button(s, secure, "Fail secure", view.failSecure(), may && hot(secure, mx, my),
                may);
        final Rect open = new Rect(secure.right() + Tokens.S1, secure.y, bw, SecurityTheme.BTN_H);
        SecurityTheme.button(s, open, "Fail open", !view.failSecure(), may && hot(open, mx, my), may);
        if (may) {
            hit(secure, () -> this.ctx.send(SecurityActionPacket.panel(
                    this.ctx.panel.getBlockPos(), SecurityActionPacket.FAIL_SECURE, 1, "")));
            hit(open, () -> this.ctx.send(SecurityActionPacket.panel(
                    this.ctx.panel.getBlockPos(), SecurityActionPacket.FAIL_SECURE, 0, "")));
        }
        y += card.h + Tokens.S2;

        card = new Rect(cards.x, y, cards.w, 52.0f);
        s.card(card, Tokens.R_CARD, SecurityTheme.CARD, SecurityTheme.LINE);
        SecurityCards.text(s, card, "Proximity opening",
                "How close a permitted player gets before a device acts on its own. Clicking and "
                        + "redstone always work regardless.");
        final Rect minus = new Rect(card.right() - 74.0f, card.centerY() - 9.0f, 18.0f, 18.0f);
        final Rect plus = new Rect(card.right() - 18.0f - Tokens.S3, card.centerY() - 9.0f, 18.0f,
                18.0f);
        SecurityTheme.button(s, minus, "-", false, may && hot(minus, mx, my), may);
        SecurityTheme.button(s, plus, "+", false, may && hot(plus, mx, my), may);
        s.textCenterV(view.proximity() + "m", (minus.right() + plus.x) * 0.5f, card.centerY(),
                SecurityTheme.TEXT, Text.BODY);
        if (may) {
            hit(minus, () -> this.ctx.send(SecurityActionPacket.panel(this.ctx.panel.getBlockPos(),
                    SecurityActionPacket.PROXIMITY, view.proximity() - 1, "")));
            hit(plus, () -> this.ctx.send(SecurityActionPacket.panel(this.ctx.panel.getBlockPos(),
                    SecurityActionPacket.PROXIMITY, view.proximity() + 1, "")));
        }
        y += card.h + Tokens.S2;

        y = SecurityCards.houseName(this, s, this.houseName, cards, y, may, mx, my);

        y = SecurityCards.password(this, s, this.password, cards, y, may, this.malformed, mx, my);
        y = SecurityCards.alwaysAsk(this, s, cards, y, may, mx, my);

        y = SecurityCards.household(this, s, cards, y, mx, my);

        final int transferable = Math.max(0, this.ctx.people().size() - 1);
        card = new Rect(cards.x, y, cards.w, 44.0f + transferable * 20.0f);
        s.card(card, Tokens.R_CARD, SecurityTheme.CARD, SecurityTheme.LINE);
        SecurityCards.text(s, card, "Transfer ownership",
                "Hands the bypass and this view to somebody else. There is exactly one owner.");
        if (!may) {
            finish(s, pane, top, card.bottom(), bar);
            return;
        }
        float ty = card.y + 38.0f;
        for (SecuritySyncPacket.Person p : this.ctx.people()) {
            if (p.isOwner() || ty + 20.0f > card.bottom()) {
                continue;
            }
            final Rect row = new Rect(card.x + Tokens.S3, ty, card.w - Tokens.S3 * 2.0f, 18.0f);
            final boolean hot = hot(row, mx, my);
            s.card(row, Tokens.R_CHIP, hot ? SecurityTheme.RED_WASH : SecurityTheme.CANVAS,
                    hot ? SecurityTheme.RED_EDGE : SecurityTheme.LINE);
            s.textV("Make " + p.name() + " the owner", row.x + Tokens.S2, row.centerY(),
                    hot ? SecurityTheme.RED : SecurityTheme.TEXT_DIM, SecurityTheme.MICRO);
            hit(row, () -> this.ctx.send(SecurityActionPacket.person(this.ctx.panel.getBlockPos(),
                    SecurityActionPacket.TRANSFER, p.id(), 0)));
            ty += 20.0f;
        }
        finish(s, pane, top, card.bottom(), bar);
    }

    private void finish(PanelSurface s, Rect pane, float top, float bottom, boolean bar) {
        this.contentHeight = bottom - top + this.scroll.value();
        popHitClip();
        s.unclip();
        if (bar) {
            final Rect track = new Rect(pane.right() - 3.0f, pane.y, 3.0f, pane.h);
            this.scroll.drawBar(s, track);
        }
    }

    private void commitHouseName(String typed) {
        if (this.ctx.mayEditPanel()) {
            this.ctx.send(SecurityActionPacket.panel(this.ctx.panel.getBlockPos(),
                    SecurityActionPacket.HOUSE_NAME, 0, typed == null ? "" : typed.trim()));
        }
        this.houseName.setVisible(false);
    }

    private void commitPassword(String typed) {
        if (!this.ctx.mayEditPanel()) {
            this.password.setVisible(false);
            return;
        }
        final String clean = typed == null ? "" : typed.trim();
        if (!clean.isEmpty() && !LockerCode.wellFormed(clean)) {
            this.malformed = true;
            this.password.begin("");
            return;
        }
        this.malformed = false;
        this.ctx.send(SecurityActionPacket.panel(this.ctx.panel.getBlockPos(),
                SecurityActionPacket.SET_PASSWORD, 0, clean));
        this.password.setVisible(false);
    }

    protected final TextField houseNameField() {
        return this.houseName;
    }

    protected final TextField passwordField() {
        return this.password;
    }

    protected final boolean malformed() {
        return this.malformed;
    }

    protected final Scroll paneScroll() {
        return this.scroll;
    }

    void beginHouseName(String current) {
        this.password.setVisible(false);
        this.houseName.begin(current);
        this.houseName.setVisible(true);
    }

    void beginPassword() {
        this.houseName.setVisible(false);
        this.malformed = false;
        this.password.begin("");
        this.password.setVisible(true);
    }

    boolean removalArmed(UUID household) {
        if (Util.getMillis() >= this.removeUntil) {
            this.removingHousehold = null;
        }
        return household != null && household.equals(this.removingHousehold);
    }

    void requestHouseholdRemoval(UUID household) {
        if (household == null) {
            return;
        }
        if (removalArmed(household)) {
            this.removingHousehold = null;
            this.removeUntil = 0L;
            this.ctx.send(SecurityActionPacket.person(this.ctx.panel.getBlockPos(),
                    SecurityActionPacket.REMOVE_HOUSEHOLD, household, 0));
            return;
        }
        this.removingHousehold = household;
        this.removeUntil = Util.getMillis() + REMOVE_CONFIRM_MS;
    }

    @Override
    public boolean click(double mx, double my) {
        for (TextField field : fields()) {
            if (field.visible() && field.mouseDown(mx, my, 0)) {
                return true;
            }
        }
        for (TextField field : fields()) {
            if (field.editing()) {
                field.commit();
                return true;
            }
        }
        return super.click(mx, my);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        for (TextField field : fields()) {
            if (field.editing() && field.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        for (TextField field : fields()) {
            if (field.editing() && field.charTyped(c, modifiers)) {
                return true;
            }
        }
        return super.charTyped(c, modifiers);
    }

    @Override
    public boolean scroll(double mx, double my, double amount) {
        return this.scroll.wheel(amount);
    }

    @Override
    public void update(float dt) {
        super.update(dt);
        this.scroll.update(dt);
        for (TextField field : fields()) {
            field.update(dt);
        }
    }

    private List<TextField> fields() {
        return List.of(this.houseName, this.password);
    }

    @Override
    public void drawDock(PanelSurface s, Rect r, float mx, float my) {
        SecurityDock.circuit(s, this.ctx, r);
    }
}
