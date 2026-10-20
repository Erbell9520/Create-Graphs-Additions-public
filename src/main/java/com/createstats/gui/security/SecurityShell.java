package com.createstats.gui.security;

import com.createstats.power.SecurityDevice;
import com.createstats.security.SecurityRole;
import com.createstats.statsmk3.ui.Easing;
import com.createstats.statsmk3.ui.Motion;
import com.createstats.statsmk3.ui.PanelSurface;
import com.createstats.statsmk3.ui.Rect;
import com.createstats.statsmk3.ui.Scroll;
import com.createstats.statsmk3.ui.Spring;
import com.createstats.statsmk3.ui.Text;
import com.createstats.statsmk3.ui.Tokens;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;

@OnlyIn(Dist.CLIENT)
public final class SecurityShell {
    private final SecurityContext ctx;
    private final List<SecurityView> views = new ArrayList<>();
    private SecurityLayout box = SecurityLayout.of(0.0f, 0.0f);
    private int active;

    private final Spring indicator = Spring.ui(0.0f);

    private float entered = 1.0f;

    private final Scroll railScroll = new Scroll();

    private final WizardView wizard;

    public SecurityShell(SecurityContext ctx) {
        this.ctx = ctx;
        this.wizard = new WizardView(ctx);
        rebuildViews();
    }

    private boolean setupPending() {
        return !this.ctx.view().configured();
    }

    private SecurityView focus() {
        return setupPending() ? this.wizard : active();
    }

    public SecurityContext context() {
        return this.ctx;
    }

    public SecurityLayout box() {
        return this.box;
    }

    private void rebuildViews() {
        this.views.clear();
        this.views.add(new OverviewView(this.ctx));
        this.views.add(new PeopleView(this.ctx));
        for (SecurityDevice.DeviceClass cls : SecurityDevice.DeviceClass.all()) {
            this.views.add(switch (cls) {
                case DOOR -> new DoorsView(this.ctx);
                case SENSOR -> new SensorsView(this.ctx);
                case LOCKER -> new LockersView(this.ctx);
                case SHUTTER -> new ShuttersView(this.ctx);
                case REPELLER -> new RepellersView(this.ctx);
                case RELAY -> new RelaysView(this.ctx);
                case COIL -> new CoilsView(this.ctx);
            });
        }
        this.views.add(new AccessView(this.ctx));
        this.views.add(new LogView(this.ctx));
        this.views.add(new SetupView(this.ctx));
    }

    public SecurityView active() {
        return this.views.get(Math.max(0, Math.min(this.active, this.views.size() - 1)));
    }

    public void layout(float width, float height) {
        this.box = SecurityLayout.of(width, height, this.views.size(), groupCount());
        this.railScroll.setMetrics(this.box.railContent, this.box.railContent - this.box.railScrollMax());
        final Rect area = this.box.viewArea(this.box.hasDock());
        for (SecurityView view : this.views) {
            view.layout(view.usesDock() ? area : this.box.viewArea(false));
        }
        this.active = Math.min(this.active, this.views.size() - 1);
        this.indicator.jump(this.active);
        this.wizard.layout(this.box.viewArea(false));
    }

    public void update(float dt) {
        this.indicator.step(dt);
        this.railScroll.update(dt);
        this.entered = Math.min(1.0f, this.entered + dt / Motion.VIEW_TRANSITION);

        for (SecurityView view : this.views) {
            view.update(dt);
        }
        this.wizard.update(dt);
    }

    private void select(int index) {
        if (index == this.active || index < 0 || index >= this.views.size()) {
            return;
        }
        this.active = index;
        this.indicator.set(index);
        this.entered = 0.0f;
        active().onEnter();
    }

    public void draw(PanelSurface s, float mx, float my) {
        for (SecurityView view : this.views) {
            view.beginFrame();
        }
        this.wizard.beginFrame();

        final Rect root = this.box.root;
        s.shadow(root.inset(-6.0f), Tokens.R_PANEL + 6.0f, 22.0f, 0xB0000000);
        s.roundedRect(root, Tokens.R_PANEL, SecurityTheme.CANVAS);

        drawTitlebar(s, mx, my);

        if (setupPending()) {
            s.clip(this.box.wideWork);
            this.wizard.draw(s, mx, my);
            s.unclip();
            return;
        }

        drawRail(s, mx, my);

        final float t = Motion.reducedMotion() ? 1.0f : Easing.expoOut(this.entered);
        final float rise = (1.0f - t) * Motion.VIEW_RISE;

        s.pushPose();
        s.translate(0.0f, rise);
        s.setAlpha(t);
        s.clip(this.box.hasDock() ? this.box.work : this.box.wideWork);
        active().draw(s, mx, my - rise);
        s.unclip();
        s.setAlpha(1.0f);
        s.popPose();

        if (this.box.hasDock() && active().usesDock()) {
            s.hairlineV(this.box.dock.x, this.box.dock.y, this.box.dock.h, SecurityTheme.LINE);
            s.rect(this.box.dock, SecurityTheme.CARD);
            s.pushPose();
            s.translate(0.0f, rise);
            s.setAlpha(t);
            s.clip(this.box.dock);
            active().drawDock(s, this.box.dockInner(), mx, my - rise);
            s.unclip();
            s.setAlpha(1.0f);
            s.popPose();
        }
    }

    private void drawTitlebar(PanelSurface s, float mx, float my) {
        final Rect bar = this.box.titlebar();
        s.rect(bar, SecurityTheme.CARD);
        s.hairlineH(bar.x, bar.bottom() - 1.0f, bar.w, SecurityTheme.LINE);

        float x = bar.x + Tokens.S4;
        SecurityIcons.shield(s, x + 6.0f, bar.centerY(), SecurityTheme.STEEL);
        x += 18.0f;

        final String house = this.ctx.view().house();
        s.textV(house.isEmpty() ? "Security" : house, x, bar.centerY(), SecurityTheme.TEXT,
                Text.BODY);
        x += s.textWidth(house.isEmpty() ? "Security" : house, Text.BODY) + Tokens.S3;

        final SecurityRole role = this.ctx.role();
        if (role != SecurityRole.MEMBER) {
            SecurityTheme.chip(s, x, bar.centerY(), role.name(), SecurityTheme.STEEL_WASH,
                    SecurityTheme.STEEL_DEEP, SecurityTheme.STEEL_EDGE);
        }

        final boolean dark = !this.ctx.view().awake();
        final boolean lockdown = this.ctx.lockedDown();
        final boolean released = this.ctx.released();
        final boolean hazard = this.ctx.hazard();
        final boolean fault = this.ctx.hasFault();
        final String state = dark ? "NO SUPPLY"
                : lockdown ? "LOCKDOWN"
                : released ? "FIRE"
                : hazard ? "HAZARD"
                : fault ? "ATTENTION" : "SECURE";
        final boolean warn = released || hazard;
        final boolean bad = lockdown || fault;
        SecurityTheme.chipRight(s, bar.right() - Tokens.S4, bar.centerY(), state,
                dark ? SecurityTheme.CANVAS
                        : bad ? SecurityTheme.RED_WASH
                        : warn ? SecurityTheme.AMBER_WASH : SecurityTheme.GREEN_WASH,
                dark ? SecurityTheme.TEXT_FAINT
                        : bad ? SecurityTheme.RED
                        : warn ? SecurityTheme.AMBER_INK : SecurityTheme.GREEN_DEEP,
                dark ? SecurityTheme.LINE
                        : bad ? SecurityTheme.RED_EDGE
                        : warn ? SecurityTheme.AMBER_EDGE : SecurityTheme.GREEN_EDGE);
    }

    private void drawRail(PanelSurface s, float mx, float my) {
        s.hairlineV(this.box.rail.right() - 1.0f, this.box.rail.y, this.box.rail.h,
                SecurityTheme.LINE);
        final Rect viewport = this.box.railViewport();
        final boolean scrolls = this.box.railScrolls;
        if (scrolls) {
            s.clip(viewport);
        }

        final float slot = this.indicator.value();
        final int lower = (int) Math.floor(slot);
        final float frac = slot - lower;
        final Rect a = railRect(lower);
        final Rect b = railRect(Math.min(lower + 1, this.views.size() - 1));
        final Rect pill = new Rect(a.x, Easing.lerp(a.y, b.y, frac), a.w, a.h);
        s.card(pill, Tokens.R_CARD, SecurityTheme.CARD, SecurityTheme.STEEL_EDGE);

        for (int i = 0; i < this.views.size(); i++) {
            final SecurityView view = this.views.get(i);
            final Rect r = railRect(i);

            if (i == 0 || !this.views.get(i - 1).group().equals(view.group())) {
                final Rect cap = new Rect(r.x, r.y - this.box.railGroupH, r.w, this.box.railGroupH);
                if (this.box.railLabels) {
                    s.textV(Text.track(view.group().toUpperCase(java.util.Locale.ROOT)),
                            cap.x + Tokens.S2, cap.bottom() - 6.0f, SecurityTheme.OFF, SecurityTheme.MICRO);
                }
            }

            final boolean on = i == this.active;
            final boolean hot = r.contains(mx, my) && (!scrolls || viewport.contains(mx, my));
            if (hot && !on) {
                s.roundedRect(r, Tokens.R_CARD, SecurityTheme.CARD_HOT);
            }
            final int ink = on ? SecurityTheme.STEEL_DEEP
                    : hot ? SecurityTheme.TEXT : SecurityTheme.TEXT_DIM;

            final float iconX = this.box.railLabels ? r.x + Tokens.S3 + 7.0f : r.centerX();
            view.drawIcon(s, iconX, r.centerY(), ink);

            if (this.box.railLabels) {
                s.textV(view.label(), r.x + Tokens.S3 + 20.0f, r.centerY(), ink, SecurityTheme.MICRO);
                final int badge = view.badge();
                if (badge >= 0) {
                    s.textRightV(String.valueOf(badge), r.right() - Tokens.S3, r.centerY(),
                            badge == 0 ? SecurityTheme.OFF : SecurityTheme.TEXT_FAINT, SecurityTheme.MICRO);
                }
            }
        }
        if (scrolls) {
            s.unclip();
            final Rect track = new Rect(this.box.rail.right() - 5.0f, viewport.y + 2.0f, 2.0f,
                    viewport.h - 4.0f);
            s.roundedRect(track, 1.0f, SecurityTheme.LINE);
            final float span = Math.max(1.0f, this.railScroll.max());
            final float thumbH = Math.max(18.0f, track.h * (track.h / (track.h + span)));
            final float ty = track.y + (this.railScroll.value() / span) * (track.h - thumbH);
            s.roundedRect(new Rect(track.x, ty, track.w, thumbH), 1.0f, SecurityTheme.OFF);
        }
    }

    private int groupCount() {
        int groups = 0;
        for (int i = 0; i < this.views.size(); i++) {
            if (i == 0 || !this.views.get(i - 1).group().equals(this.views.get(i).group())) {
                groups++;
            }
        }
        return groups;
    }

    private Rect railRect(int index) {
        int groups = 0;
        for (int i = 0; i <= index && i < this.views.size(); i++) {
            if (i == 0 || !this.views.get(i - 1).group().equals(this.views.get(i).group())) {
                groups++;
            }
        }
        final Rect r = this.box.railItem(index, groups);

        return this.box.railScrolls ? new Rect(r.x, r.y - this.railScroll.value(), r.w, r.h) : r;
    }

    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) {
            return false;
        }

        if (setupPending()) {
            return this.wizard.click(mx, my);
        }
        final Rect viewport = this.box.railViewport();
        for (int i = 0; i < this.views.size(); i++) {
            if (railRect(i).contains(mx, my) && (!this.box.railScrolls || viewport.contains(mx, my))) {
                select(i);
                return true;
            }
        }
        return active().click(mx, my);
    }

    public boolean mouseScrolled(double mx, double my, double amount) {
        if (!setupPending() && this.box.railScrolls && this.box.rail.contains(mx, my)) {
            return this.railScroll.wheel(amount);
        }
        return focus().scroll(mx, my, amount);
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return focus().keyPressed(keyCode, scanCode, modifiers);
    }

    public boolean charTyped(char c, int modifiers) {
        return focus().charTyped(c, modifiers);
    }
}
