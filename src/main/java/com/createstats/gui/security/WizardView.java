package com.createstats.gui.security;

import com.createstats.statsmk3.ui.PanelSurface;
import com.createstats.statsmk3.ui.Rect;
import com.createstats.statsmk3.ui.Scroll;
import com.createstats.statsmk3.ui.Text;
import com.createstats.statsmk3.ui.Tokens;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

// extends SetupView so there's one implementation of the settings
@OnlyIn(Dist.CLIENT)
public final class WizardView extends SetupView {
    private float contentHeight;

    public WizardView(SecurityContext ctx) {
        super(ctx);
    }

    @Override
    public String id() {
        return "wizard";
    }

    @Override
    public String label() {
        return "Set up";
    }

    @Override
    public boolean usesDock() {
        return false;
    }

    @Override
    public void draw(PanelSurface s, float mx, float my) {
        final boolean may = this.ctx.mayEditPanel();

        float y = this.area.y;
        s.text("Set up this panel", this.area.x, y, SecurityTheme.TEXT, Text.SUB);
        y += Text.lineHeight(Text.SUB) + 2.0f;
        for (String line : Text.wrap(s, may
                ? "Four questions, and every one of them can be changed later on the Setup tab."
                : "This panel has not been set up, and its owner is the one who does it.",
                this.area.w, SecurityTheme.MICRO)) {
            s.text(line, this.area.x, y, SecurityTheme.TEXT_FAINT, SecurityTheme.MICRO);
            y += Text.lineHeight(SecurityTheme.MICRO) + 1.0f;
        }
        y += Tokens.S2;

        final Rect done = may ? SecurityLayout.wizardDone(this.area) : Rect.EMPTY;
        final float paneBottom = may ? done.y - Tokens.S2 : this.area.bottom();
        final Rect pane = new Rect(this.area.x, y, this.area.w, Math.max(0.0f, paneBottom - y));
        final Scroll scroll = paneScroll();
        scroll.setMetrics(this.contentHeight, pane.h);
        final boolean bar = scroll.scrollable();
        final Rect cards = new Rect(this.area.x, this.area.y,
                this.area.w - (bar ? Tokens.S2 : 0.0f), this.area.h);
        final float top = y;
        s.clip(pane);
        pushHitClip(pane);
        y -= scroll.value();

        y = SecurityCards.household(this, s, cards, y, mx, my);
        y = SecurityCards.houseName(this, s, houseNameField(), cards, y, may, mx, my);
        y = SecurityCards.password(this, s, passwordField(), cards, y, may, malformed(), mx, my);
        y = SecurityCards.alwaysAsk(this, s, cards, y, may, mx, my);

        this.contentHeight = y - Tokens.S2 - top + scroll.value();
        popHitClip();
        s.unclip();
        if (bar) {
            scroll.drawBar(s, new Rect(pane.right() - 3.0f, pane.y, 3.0f, pane.h));
        }

        if (!may) {
            return;
        }

        SecurityTheme.button(s, done, "Done", true, hot(done, mx, my), true);
        hit(done, this::finish);
    }

    private void finish() {
        this.ctx.send(com.createstats.network.SecurityActionPacket.panel(
                this.ctx.panel.getBlockPos(),
                com.createstats.network.SecurityActionPacket.FINISH_SETUP, 0, ""));
    }
}
