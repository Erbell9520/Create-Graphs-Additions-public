package com.createstats.gui.security;

import com.createstats.network.SecuritySyncPacket;
import com.createstats.power.SecurityDevice;
import com.createstats.security.LockerCode;
import com.createstats.security.LockerMode;
import com.createstats.statsmk3.ui.PanelSurface;
import com.createstats.statsmk3.ui.Rect;
import com.createstats.statsmk3.ui.Text;
import com.createstats.statsmk3.ui.TextField;
import com.createstats.statsmk3.ui.Tokens;
import net.minecraft.core.BlockPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class LockersView extends DevicesView {
    private final TextField code;

    private BlockPos setting;

    private boolean malformed;

    public LockersView(SecurityContext ctx) {
        super(ctx, SecurityDevice.DeviceClass.LOCKER);
        this.code = new TextField(this::commitCode)
                .palette(SecurityTheme.CANVAS, SecurityTheme.TEXT, SecurityTheme.TEXT_FAINT,
                        SecurityTheme.LINE, SecurityTheme.STEEL)
                .textScale(Text.BODY)
                .placeholder("....");
        this.code.setVisible(false);
    }

    @Override
    protected String passLabel() {
        return " can open";
    }

    @Override
    protected boolean usesClearance(SecuritySyncPacket.Row row) {
        return !modeOf(row).personal();
    }

    @Override
    protected String noClearanceLine(SecuritySyncPacket.Row row) {
        final String holder = holderOf(row);
        return "Personal" + (holder.isEmpty() ? "" : ", " + holder + "'s")
                + ". It opens for them alone while it is on this network, and asks everybody else "
                + "for its code. Clearance decides nothing here.";
    }

    @Override
    public void layout(Rect area) {
        super.layout(area);

        cancel();
    }

    @Override
    public void update(float dt) {
        super.update(dt);
        this.code.update(dt);
    }

    @Override
    protected float drawExtras(PanelSurface s, Rect r, SecuritySyncPacket.Row row, float y,
                               float mx, float my) {
        if (this.ctx.houseSealed()) {
            y = note(s, r, y, "AWAY+ is on. The code is the only way into this locker, the owner "
                    + "included. A locker with no code set still runs on clearance, because a box "
                    + "nobody can be asked for a code is a box nobody could open again.");
            y += Tokens.S2;
        }

        y = drawUpgrades(s, r, row, y, mx, my);

        SecurityTheme.head(s, r.x, y, "CODE");
        y += Text.lineHeight(SecurityTheme.MICRO) + 4.0f;

        if (modeOf(row).personal()) {
            toggleRow(s, new Rect(r.x, y, r.w, SecurityTheme.OPT_H), "A code is set", "PERSONAL",
                    true, false, mx, my, () -> {
                    });
            y += SecurityTheme.OPT_H + 4.0f;
            y = note(s, r, y, "Only its holder can set this locker's code, at the block, and only "
                    + "they open it without typing one. This panel cannot write a code onto it: an "
                    + "admin who could would be able to open it a moment later.");
            y += Tokens.S2;
            y = note(s, r, y, "Losing power, or losing this panel, puts it on the code for "
                    + "everybody, its holder included. AWAY+ does not touch a personal locker: "
                    + "sealing the house is the household deciding something, and this is the one "
                    + "box it does not decide about.");
            return y + Tokens.S3;
        }

        final boolean may = this.ctx.mayEditDevices();
        if (row.pos().equals(this.setting)) {
            final Rect field = new Rect(r.x, y, r.w, SecurityTheme.OPT_H);
            this.code.setBounds(field);
            this.code.setVisible(true);
            this.code.draw(s);
            y += SecurityTheme.OPT_H + 4.0f;
            y = note(s, r, y, this.malformed
                    ? "Four digits, nothing else. Leave it empty and press enter to remove the code."
                    : "Type four digits and press enter. Empty removes the code.");
            return y + Tokens.S3;
        }

        final boolean has = hasCode(row);
        toggleRow(s, new Rect(r.x, y, r.w, SecurityTheme.OPT_H),
                has ? "A code is set" : "No code set",
                may ? "CHANGE" : (has ? "SET" : "NONE"),
                has, may, mx, my, () -> begin(row));
        y += SecurityTheme.OPT_H + 4.0f;

        y = note(s, r, y, has
                ? "Anybody who knows the code opens this locker, whatever the roster says. The panel "
                        + "never shows a code back, so a forgotten one is replaced rather than read."
                : "With no code, clearance is the only way in. Set one to let somebody in who is not "
                        + "on the roster.");
        y += Tokens.S2;
        y = note(s, r, y, "Three wrong codes in a row shut that locker's keypad for a minute and "
                + "report tamper here. Losing power, or losing this panel, shuts the locker on the "
                + "spot and puts it on the code for everyone, the owner included, and it keeps "
                + "asking after the power is back until somebody types it. Setting a code here "
                + "clears that too.");
        return y + Tokens.S3;
    }

    private float drawUpgrades(PanelSurface s, Rect r, SecuritySyncPacket.Row row, float y,
                               float mx, float my) {
        SecurityTheme.head(s, r.x, y, "CAPACITY");
        y += Text.lineHeight(SecurityTheme.MICRO) + 4.0f;

        final com.createstats.blockentity.SecurityLockerBE locker = lockerAt(row);
        if (locker == null) {
            y = note(s, r, y, "Out of range from here. Stand nearer this locker to read its "
                    + "capacity.");
            return y + Tokens.S3;
        }
        if (!locker.reportsUpgrades()) {
            y = note(s, r, y, "A personal locker does not report its capacity to the house. Its "
                    + "holder can see it on the box.");
            return y + Tokens.S3;
        }

        final int slotUpgrades = locker.upgradeCount(com.createstats.upgrade.UpgradeKind.SLOTS);
        final int stackUpgrades = locker.upgradeCount(com.createstats.upgrade.UpgradeKind.STACK);
        final int slots = locker.rows() * com.createstats.blockentity.SecurityLockerBE.COLUMNS;

        toggleRow(s, new Rect(r.x, y, r.w, SecurityTheme.OPT_H),
                slots + " slots, " + locker.stackCap() + " to a stack",
                installedBadge(slotUpgrades, stackUpgrades),
                slotUpgrades + stackUpgrades > 0, false, mx, my, () -> {
                });
        y += SecurityTheme.OPT_H + 4.0f;

        if (slotUpgrades + stackUpgrades == 0) {
            y = note(s, r, y, "No upgrades installed. A locker takes three, in the rack on the "
                    + "right of its grid: Slot Upgrades make it taller and Stack Upgrades make "
                    + "each slot deeper.");
        } else {
            y = note(s, r, y, describeRack(slotUpgrades, stackUpgrades)
                    + " Upgrades are installed and removed at the locker itself, behind its lock. "
                    + "One cannot be taken out while the box is too full for the smaller size, "
                    + "which is refused rather than spilled.");
        }
        return y + Tokens.S3;
    }

    private static String installedBadge(int slotUpgrades, int stackUpgrades) {
        return slotUpgrades + stackUpgrades == 0 ? "NONE" : slotUpgrades + " + " + stackUpgrades;
    }

    private static String describeRack(int slotUpgrades, int stackUpgrades) {
        final StringBuilder out = new StringBuilder();
        if (slotUpgrades > 0) {
            out.append(slotUpgrades).append(slotUpgrades == 1 ? " Slot Upgrade" : " Slot Upgrades");
        }
        if (stackUpgrades > 0) {
            if (out.length() > 0) {
                out.append(" and ");
            }
            out.append(stackUpgrades)
                    .append(stackUpgrades == 1 ? " Stack Upgrade" : " Stack Upgrades");
        }
        return out.append(" installed.").toString();
    }

    private boolean hasCode(SecuritySyncPacket.Row row) {
        final com.createstats.blockentity.SecurityLockerBE locker = lockerAt(row);
        return locker != null && locker.hasCode();
    }

    private LockerMode modeOf(SecuritySyncPacket.Row row) {
        final com.createstats.blockentity.SecurityLockerBE locker = lockerAt(row);
        return locker == null ? LockerMode.UNSET : locker.mode();
    }

    private String holderOf(SecuritySyncPacket.Row row) {
        final com.createstats.blockentity.SecurityLockerBE locker = lockerAt(row);
        return locker == null ? "" : locker.holderName();
    }

    @org.jetbrains.annotations.Nullable
    private com.createstats.blockentity.SecurityLockerBE lockerAt(SecuritySyncPacket.Row row) {
        final net.minecraft.client.multiplayer.ClientLevel level =
                net.minecraft.client.Minecraft.getInstance().level;
        return level != null && level.getBlockEntity(row.pos())
                instanceof com.createstats.blockentity.SecurityLockerBE locker ? locker : null;
    }

    private void begin(SecuritySyncPacket.Row row) {
        this.setting = row.pos();
        this.malformed = false;
        this.code.begin("");
        this.code.setVisible(true);
    }

    private void cancel() {
        this.code.cancel();
        this.code.setVisible(false);
        this.setting = null;
        this.malformed = false;
    }

    private void commitCode(String typed) {
        if (this.setting == null || !this.ctx.mayEditDevices()) {
            cancel();
            return;
        }
        final String clean = typed == null ? "" : typed.trim();
        if (!clean.isEmpty() && !LockerCode.wellFormed(clean)) {
            this.malformed = true;
            this.code.begin("");
            return;
        }
        this.ctx.setLockerCode(this.setting, clean);
        cancel();
    }

    @Override
    public boolean click(double mx, double my) {
        if (this.code.visible() && this.code.mouseDown(mx, my, 0)) {
            return true;
        }
        if (this.code.editing()) {
            this.code.commit();
            return true;
        }
        return super.click(mx, my);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return this.code.keyPressed(keyCode, scanCode, modifiers) || super.keyPressed(keyCode,
                scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        return this.code.charTyped(c, modifiers) || super.charTyped(c, modifiers);
    }

    private float note(PanelSurface s, Rect r, float y, String text) {
        for (String line : Text.wrap(s, text, r.w, SecurityTheme.MICRO)) {
            s.text(line, r.x, y, SecurityTheme.TEXT_FAINT, SecurityTheme.MICRO);
            y += Text.lineHeight(SecurityTheme.MICRO) + 1.0f;
        }
        return y;
    }
}
