package com.createstats.vccmk2;

import com.createstats.flight.item.GovernorCardItem;
import com.createstats.flight.item.PropellerCardItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class VccMk2Screen extends AbstractContainerScreen<VccMk2Menu> {
    private static final int BAR_H = 26;
    private static final int NAV_W = 152;
    private static final int PAD = 22;

    private static final int BG = 0xFF0A0E13;
    private static final int BAR = 0xFF11161D;
    private static final int NAVBG = 0xFF0C1117;
    private static final int CARD = 0xFF161C25;
    private static final int CARD_SEL = 0xFF24290F;
    private static final int SLOTBG = 0xFF05080C;
    private static final int BAND_A = 0xFF12171F;
    private static final int BAND_B = 0xFF0D1219;
    private static final int GOLD = 0xFFF0CD78;
    private static final int GOLD_DK = 0xFF8A6E3C;
    private static final int CYAN = 0xFF78E6CD;
    private static final int AMBER = 0xFFF5C064;
    private static final int GREEN = 0xFF7BD68A;
    private static final int RED = 0xFFE07070;
    private static final int TXT = 0xFFE2E7ED;
    private static final int DIM = 0xFF8A95A0;
    private static final int GREY = 0xFF39414C;

    private enum Station {
        HANGAR("Hangar", "craft setup"),
        BIND("Bind", "hardware"),
        MIXER("Mixer", "roles & mixing"),
        COCKPIT("Cockpit", "fly & test"),
        AUTO("Auto", "autopilot"),
        ADVANCED("Advanced", "power user");

        final String label;
        final String sub;

        Station(String label, String sub) {
            this.label = label;
            this.sub = sub;
        }
    }

    private enum MixView {
        TOP, BOTTOM, FRONT, REAR, LEFT, RIGHT
    }

    private enum CtlType {
        LEVER, LEVER_C, WHEEL, STICK, SHIFTER
    }

    private record Ctl(String id, String label, CtlType type, int x, int y, int w, int h, String[] detents) {
    }

    private static final String[] AXES = {"Throttle", "Vertical", "Yaw", "Pitch", "Roll"};

    private Station active = Station.HANGAR;
    private boolean proposalDismissed;
    private VccMk2CraftProfile lastSeenProfile;

    private MixView mixView = MixView.TOP;
    private int mixSelRow = -1;
    private int mixPreviewAxis = -1;

    private float armAnim;
    private float modeAnim;
    private float stabAnim;
    private float aux0Anim;
    private float aux1Anim;
    private boolean aux0On;
    private boolean aux1On;
    private VccMk2CraftProfile auxProfile;

    private final Map<String, Float> ctlVal = new HashMap<>();
    private final Map<String, Float> ctlAnim = new HashMap<>();
    private Ctl dragCtl;
    // once touched, the client owns the value. no drag fight with the server
    private final Set<String> ctlOwned = new HashSet<>();

    private static float dashSpeedMps;
    private static float dashRpm;
    private static float dashThrottle;
    private static float dashBrake;
    private static float dashAccelG;
    private static float dashLatG;
    private static double dashTripM;
    private static double dashTopKmh;
    private static float dashPrevSpeedMps;
    private static float dashPrevHeading;
    private static long dashLastNanos;

    private static final int[] AUTO_APPROACH = {100, 150, 200, 300, 500, 800};
    private static final int[] AUTO_ANNOUNCE = {50, 100, 200, 250, 500, 1000};
    private static final double[] AUTO_REVERSE = {2, 3, 4, 5, 7, 10};
    private static final int[] AUTO_EVADE = {12, 18, 24, 30, 36};
    private static final String[] AUTO_SRC = {"OFF", "FALLBACK", "NAV TABLE", "PATHFINDER"};
    private int autoSource;
    private float autoCruise = 0.4f;
    private int autoApproach = 2;
    private int autoAnnounce = 2;
    private int autoReverse = 2;
    private int autoEvade = 2;
    private boolean autoPfOpen = true;
    private boolean autoDragCruise;

    private int bindTypeMenuRow = -1;

    private List<Component> pendingTip;

    private int activeFreqTarget = -1;
    private boolean bombSafety;
    private boolean bayOpen;
    private long dropFlash;

    public VccMk2Screen(VccMk2Menu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.titleLabelY = -9999;
        this.inventoryLabelY = -9999;
    }

    @Override
    protected void init() {
        super.init();
        this.imageWidth = this.width;
        this.imageHeight = this.height;
        this.leftPos = 0;
        this.topPos = 0;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private int stageX() {
        return NAV_W;
    }

    private int stageY() {
        return BAR_H;
    }

    private int stageW() {
        return this.width - NAV_W;
    }

    private int stageH() {
        return this.height - BAR_H;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);

        com.createstats.power.client.FeStatusWidget.draw(g, font,
                leftPos + imageWidth + 6, topPos + 6, 110, menu.blockEntity().feConsumer());
        if (pendingTip != null && !pendingTip.isEmpty()) {
            g.renderComponentTooltip(font, pendingTip, mouseX, mouseY);
        }
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBg(g, partialTick, mouseX, mouseY);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        pendingTip = null;
        menu.setBindActive(active == Station.BIND);
        g.fill(0, 0, this.width, this.height, BG);
        drawStatusBar(g);
        drawNav(g, mouseX, mouseY);
        switch (active) {
            case HANGAR -> drawHangar(g, mouseX, mouseY);
            case BIND -> drawBind(g, mouseX, mouseY);
            case MIXER -> drawMixer(g, mouseX, mouseY);
            case COCKPIT -> drawCockpit(g, mouseX, mouseY);
            case AUTO -> drawAuto(g, mouseX, mouseY);
            case ADVANCED -> drawAdvanced(g, mouseX, mouseY);
            default -> drawPlaceholder(g);
        }
    }

    private void drawStatusBar(GuiGraphics g) {
        g.fill(0, 0, this.width, BAR_H, BAR);
        g.fill(0, BAR_H - 1, this.width, BAR_H, GOLD_DK);
        g.drawString(font, "VCC Mk II", 12, 9, GOLD, false);
        VccMk2BlockEntity be = menu.blockEntity();
        String name = be != null && be.profileChosen() ? be.profile().label() : "Console";
        String tag = "\u25b8 " + name;
        g.drawString(font, tag, 92, 9, DIM, false);
        boolean armed = be != null && be.armed();
        boolean auto = be != null && be.autoMode();
        boolean assisted = be == null || be.assisted();
        boolean canArm = be != null && be.profileChosen() && be.boundPropellerCount() > 0
                && be.boundGovernorCount() > 0 && be.assignedEngineCount() > 0;
        String state = armed ? "LIVE" : canArm ? "READY" : "SETUP";
        int stateCol = armed ? GREEN : canArm ? CYAN : AMBER;
        badge(g, 96 + font.width(tag) + 12, 6, state, stateCol);
        int x = this.width - 12;
        x -= badgeRight(g, x, 7, auto ? "AUTO" : "MANUAL", auto ? GREEN : CYAN) + 6;
        x -= badgeRight(g, x, 7, armed ? "ARMED" : "STANDBY", armed ? GREEN : AMBER) + 6;
        badgeRight(g, x, 7, assisted ? "ASSIST" : "DIRECT", assisted ? CYAN : AMBER);
    }

    private void drawNav(GuiGraphics g, int mouseX, int mouseY) {
        g.fill(0, BAR_H, NAV_W, this.height, NAVBG);
        g.fill(NAV_W - 1, BAR_H, NAV_W, this.height, GOLD_DK);
        VccMk2BlockEntity be = menu.blockEntity();
        boolean pc = be != null && be.profileChosen();
        int propN = be != null ? be.boundPropellerCount() : 0;
        int govN = be != null ? be.boundGovernorCount() : 0;
        int roleN = be != null ? be.assignedEngineCount() : 0;
        boolean armed = be != null && be.armed();
        boolean auto = be != null && be.autoMode();
        boolean canArm = pc && propN > 0 && govN > 0 && roleN > 0;
        Station[] all = Station.values();
        for (int i = 0; i < all.length; i++) {
            int y = navItemY(i);
            boolean on = all[i] == active;
            boolean hover = inRect(mouseX, mouseY, 8, y, NAV_W - 18, 36);
            if (on) {
                g.fill(8, y, NAV_W - 10, y + 36, CARD_SEL);
                g.renderOutline(8, y, NAV_W - 18, 36, GOLD);
            } else if (hover) {
                g.fill(8, y, NAV_W - 10, y + 36, CARD);
            }
            String stext;
            int scol;
            switch (all[i]) {
                case HANGAR -> {
                    stext = pc ? be.profile().label() : "choose craft";
                    scol = pc ? GREEN : AMBER;
                }
                case BIND -> {
                    if (propN > 0 && govN > 0) { stext = be.boundRowCount() + " engines"; scol = GREEN; }
                    else if (propN > 0 || govN > 0) { stext = "incomplete"; scol = AMBER; }
                    else { stext = "no cards"; scol = DIM; }
                }
                case MIXER -> {
                    if (roleN > 0) { stext = roleN + " roled"; scol = GREEN; }
                    else if (propN > 0) { stext = "needs roles"; scol = AMBER; }
                    else { stext = "bind first"; scol = DIM; }
                }
                case COCKPIT -> {
                    if (armed) { stext = "ARMED"; scol = GREEN; }
                    else if (canArm) { stext = "ready"; scol = CYAN; }
                    else { stext = "not ready"; scol = DIM; }
                }
                case AUTO -> {
                    stext = auto ? "engaged" : "off";
                    scol = auto ? GREEN : DIM;
                }
                default -> { stext = all[i].sub; scol = DIM; }
            }
            g.drawString(font, all[i].label, 20, y + 7, on ? GOLD : TXT, false);
            g.drawString(font, stext, 20, y + 20, scol, false);
            g.fill(NAV_W - 21, y + 13, NAV_W - 15, y + 19, scol);
            tip(mouseX, mouseY, 8, y, NAV_W - 18, 36, all[i].label, navTip(all[i]));
        }
    }

    private static String[] navTip(Station s) {
        return switch (s) {
            case HANGAR -> new String[]{"Pick your craft type and check readiness."};
            case BIND -> new String[]{"Link propeller and governor cards to engines.", "AUTO pulls cards, MANUAL places exact ones."};
            case MIXER -> new String[]{"Give each engine a role and test every axis.", "Arrows show how each engine pushes the craft."};
            case COCKPIT -> new String[]{"Fly the craft, arm and work the controls."};
            case AUTO -> new String[]{"Autopilot source and cruise tuning."};
            case ADVANCED -> new String[]{"Input channel frequencies and payload."};
        };
    }

    private int navItemY(int i) {
        return BAR_H + 14 + i * 44;
    }

    private int[] profileCardRect(int i) {
        int sx = stageX() + PAD;
        int sy = stageY() + 56;
        int leftW = (int) ((stageW() - 2 * PAD) * 0.56);
        int gap = 12;
        int cw = (leftW - gap) / 2;
        int ch = Math.min(70, (stageH() - 150) / 4);
        int col = i % 2;
        int row = i / 2;
        return new int[]{sx + col * (cw + gap), sy + row * (ch + gap), cw, ch};
    }

    private void drawHangar(GuiGraphics g, int mouseX, int mouseY) {
        VccMk2BlockEntity be = menu.blockEntity();
        int sx = stageX() + PAD;
        int sy = stageY() + PAD;
        g.drawString(font, "Hangar  \u00b7  Craft Setup", sx, sy - 6, TXT, false);
        g.drawString(font, "Pick what you built. The computer plans the rest.", sx, sy + 8, DIM, false);

        double t = (System.currentTimeMillis() % 1000000L) / 1000.0;
        VccMk2CraftProfile[] profiles = VccMk2CraftProfile.values();
        VccMk2CraftProfile chosen = be != null && be.profileChosen() ? be.profile() : null;
        for (int i = 0; i < profiles.length; i++) {
            int[] r = profileCardRect(i);
            boolean sel = profiles[i] == chosen;
            boolean hover = inRect(mouseX, mouseY, r[0], r[1], r[2], r[3]);
            g.fill(r[0], r[1], r[0] + r[2], r[1] + r[3], sel ? CARD_SEL : CARD);
            g.renderOutline(r[0], r[1], r[2], r[3], sel || hover ? GOLD : GOLD_DK);
            int icon = Math.min(r[3] - 14, 46);
            int ix = r[0] + 10;
            int iy = r[1] + (r[3] - icon) / 2;
            g.fill(ix, iy, ix + icon, iy + icon, 0xFF0B0F14);
            g.renderOutline(ix, iy, icon, icon, sel ? GOLD : GOLD_DK);
            VccMk2CockpitArt.drawProfileIcon(g, profiles[i], ix + 3, iy + 3, icon - 6, icon - 6, t,
                    sel || hover, sel ? GOLD : (hover ? CYAN : DIM));
            int tx = ix + icon + 12;
            int tcy = r[1] + r[3] / 2;
            g.drawString(font, profiles[i].label(), tx, tcy - 9, sel ? GOLD : TXT, false);
            g.drawString(font, profiles[i].sub(), tx, tcy + 2, DIM, false);
            tip(mouseX, mouseY, r[0], r[1], r[2], r[3], profiles[i].label() + "  (" + profiles[i].sub() + ")",
                    planLine(profiles[i]), whyLine(profiles[i]));
        }

        int rx = stageX() + PAD + (int) ((stageW() - 2 * PAD) * 0.56) + 24;
        int rw = stageX() + stageW() - PAD - rx;
        int ry = stageY() + 50;
        panel(g, rx, ry, rw, 150);
        g.drawString(font, "READINESS", rx + 12, ry + 10, GOLD, false);
        int propN = be != null ? be.boundPropellerCount() : 0;
        int govN = be != null ? be.boundGovernorCount() : 0;
        boolean onCraft = be != null && be.isOnContraption();
        readyRow(g, rx + 12, ry + 32, chosen != null, chosen != null ? "Profile: " + chosen.label() : "Pick a craft profile");
        readyRow(g, rx + 12, ry + 52, propN > 0, propN + " propeller card" + (propN == 1 ? "" : "s") + " bound");
        readyRow(g, rx + 12, ry + 72, govN > 0, govN + " governor card" + (govN == 1 ? "" : "s") + " bound");
        readyRow(g, rx + 12, ry + 92, onCraft, onCraft ? "On a contraption" : "Place on an assembled craft");
        boolean canArm = chosen != null && propN > 0 && govN > 0;
        readyRow(g, rx + 12, ry + 112, canArm, canArm ? "Ready to arm" : "Bind hardware to arm");

        int sy2 = ry + 162;
        panel(g, rx, sy2, rw, 92);
        g.drawString(font, "SENSORS", rx + 12, sy2 + 10, GOLD, false);
        g.drawString(font, "Aim a Display Link from sensor to VCC", rx + 64, sy2 + 11, DIM, false);
        for (int i = 0; i < VccMk2BlockEntity.SENSOR_COUNT; i++) {
            drawHangarSensorCell(g, be, i, mouseX, mouseY);
        }

        boolean armed = be != null && be.armed();
        boolean auto = be != null && be.autoMode();
        int by = sy2 + 104;
        int half = (rw - 12) / 2;
        button(g, rx, by, half, 26, armed ? "DISARM" : "ARM OUTPUT", armed ? RED : GOLD, mouseX, mouseY);
        button(g, rx + half + 12, by, half, 26, auto ? "MODE: AUTO" : "MODE: MANUAL", auto ? GREEN : CYAN, mouseX, mouseY);
        tip(mouseX, mouseY, rx, by, half, 26, armed ? "Disarm" : "Arm Output",
                armed ? "Outputs are live, click to make safe." : "Go live so controls drive the craft.");
        tip(mouseX, mouseY, rx + half + 12, by, half, 26, auto ? "Auto Mode" : "Manual Mode",
                auto ? "Autopilot is driving." : "You are flying by hand.");

        int py = stageY() + stageH() - 86;
        if (chosen != null) {
            if (chosen != lastSeenProfile) {
                lastSeenProfile = chosen;
                proposalDismissed = false;
            }
            if (!proposalDismissed) {
                drawProposal(g, chosen, py, mouseX, mouseY);
            } else {
                g.drawString(font, "Plan accepted. Next: open Bind and bind your cards.", sx, py + 30, DIM, false);
            }
        } else {
            g.drawString(font, "No profile yet. Pick one above to see the plan.", sx, py + 30, DIM, false);
        }
    }

    private void drawProposal(GuiGraphics g, VccMk2CraftProfile p, int y, int mouseX, int mouseY) {
        int x = stageX() + PAD;
        int w = stageW() - 2 * PAD;
        int h = 72;
        g.fill(x, y, x + w, y + h, 0xFF12211B);
        g.renderOutline(x, y, w, h, GREEN);
        g.drawString(font, "PLAN", x + 12, y + 10, GREEN, false);
        g.drawString(font, planLine(p), x + 12, y + 30, TXT, false);
        g.drawString(font, "Why: " + whyLine(p), x + 12, y + 48, DIM, false);
        button(g, x + w - 250, y + 38, 110, 24, "ACCEPT", GREEN, mouseX, mouseY);
        button(g, x + w - 132, y + 38, 120, 24, "CHANGE", DIM, mouseX, mouseY);
    }

    private static String planLine(VccMk2CraftProfile p) {
        return switch (p) {
            case AIRSHIP -> "Lift props for Vertical, main props for Throttle, differential for Yaw.";
            case MULTIROTOR -> "Mix all rotors for Throttle / Pitch / Roll / Yaw, stabilized hover.";
            case PLANE -> "Props for Throttle; map fins as elevator / rudder / ailerons.";
            case VTOL -> "Blend rotor hover with vectored forward thrust for transition.";
            case OTOMOBIL -> "Drive the wheels and steer the front axle.";
            case TANK -> "Throttle plus differential track speed for turning.";
            case BOAT -> "Thrust forward, yaw by differential or rudder.";
            case CUSTOM -> "Nothing assumed; you assign every role in the Mixer.";
        };
    }

    private static String whyLine(VccMk2CraftProfile p) {
        return p.isGround()
                ? "Ground vehicle: control is wheel drive + steering, not free-body thrust."
                : "Roles come from each actuator's position and thrust direction around the center of mass.";
    }

    private void readyRow(GuiGraphics g, int x, int y, boolean ok, String text) {
        g.drawString(font, ok ? "\u2714" : "\u26a0", x, y, ok ? GREEN : AMBER, false);
        g.drawString(font, text, x + 16, y, ok ? TXT : DIM, false);
    }

    private static final String[] SENSOR_NAMES = {"Gimbal", "Altitude", "Velocity", "Nav Table"};
    private static final String[] SENSOR_ABBR = {"GMB", "ALT", "VEL", "NAV"};
    private static final String[] SENSOR_UNLOCKS = {
            "Unlocks stabilized attitude (self-leveling).",
            "Unlocks altitude hold and the ALT readout.",
            "Unlocks true ground speed on the gauges.",
            "Unlocks Auto heading and waypoint guidance."
    };

    private int[] hangarSensorRect(int i) {
        int rx = stageX() + PAD + (int) ((stageW() - 2 * PAD) * 0.56) + 24;
        int rw = stageX() + stageW() - PAD - rx;
        int ry = stageY() + 50;
        int sy2 = ry + 162;
        int gap = 8;
        int cellW = (rw - 24 - gap) / 2;
        int col = i % 2;
        int row = i / 2;
        int x = rx + 12 + col * (cellW + gap);
        int y = sy2 + 26 + row * 30;
        return new int[]{x, y, cellW, 28};
    }

    private void drawHangarSensorCell(GuiGraphics g, VccMk2BlockEntity be, int i, int mouseX, int mouseY) {
        int[] r = hangarSensorRect(i);
        int state = be != null ? be.sensorState(i) : VccMk2BlockEntity.SENSOR_NONE;
        boolean manual = be != null && be.sensorManualPos(i) != null;
        BlockPos eff = be != null ? be.sensorEffectivePos(i) : null;
        boolean connected = state == VccMk2BlockEntity.SENSOR_CONNECTED;
        boolean hover = inRect(mouseX, mouseY, r[0], r[1], r[2], r[3]);
        int lamp = connected ? GREEN : state == VccMk2BlockEntity.SENSOR_LOST ? RED : GREY;
        g.fill(r[0], r[1], r[0] + r[2], r[1] + r[3], hover && manual ? CARD : 0xFF11161D);
        g.renderOutline(r[0], r[1], r[2], r[3], hover && manual ? lamp : GOLD_DK);
        g.fill(r[0] + 6, r[1] + 6, r[0] + 12, r[1] + 12, lamp);
        g.renderOutline(r[0] + 6, r[1] + 6, 6, 6, dim(lamp, 0.6));
        g.drawString(font, SENSOR_NAMES[i], r[0] + 18, r[1] + 3, connected ? TXT : DIM, false);
        String st;
        int stCol;
        if (connected) {
            st = (manual ? "LINK " : "AUTO ") + (eff != null ? relText(be, eff) : "");
            stCol = GREEN;
        } else if (state == VccMk2BlockEntity.SENSOR_LOST) {
            st = "LOST";
            stCol = RED;
        } else {
            st = "none";
            stCol = GREY;
        }
        g.drawString(font, st, r[0] + 18, r[1] + 14, stCol, false);
        tip(mouseX, mouseY, r[0], r[1], r[2], r[3], SENSOR_NAMES[i] + " sensor",
                SENSOR_UNLOCKS[i],
                manual ? "Bound by Display Link. Click to clear." : "Aim a Display Link from the sensor to this VCC.");
    }

    private void drawCockpitSensorStrip(GuiGraphics g, VccMk2BlockEntity be, int mouseX, int mouseY) {
        int chipW = 42;
        int gap = 6;
        int n = VccMk2BlockEntity.SENSOR_COUNT;
        int totalW = n * chipW + (n - 1) * gap;
        int x0 = stageX() + stageW() - PAD - totalW;
        int y = stageY() + 10;
        for (int i = 0; i < n; i++) {
            int x = x0 + i * (chipW + gap);
            int state = be != null ? be.sensorState(i) : VccMk2BlockEntity.SENSOR_NONE;
            boolean connected = state == VccMk2BlockEntity.SENSOR_CONNECTED;
            int lamp = connected ? GREEN : state == VccMk2BlockEntity.SENSOR_LOST ? RED : GREY;
            g.fill(x, y, x + chipW, y + 14, 0xFF11161D);
            g.renderOutline(x, y, chipW, 14, connected ? dim(lamp, 0.7) : GOLD_DK);
            g.fill(x + 4, y + 5, x + 9, y + 10, lamp);
            g.drawString(font, SENSOR_ABBR[i], x + 13, y + 3, connected ? TXT : DIM, false);
            tip(mouseX, mouseY, x, y, chipW, 14, SENSOR_NAMES[i] + " sensor",
                    connected ? "Connected." : state == VccMk2BlockEntity.SENSOR_LOST
                            ? "Bound but offline." : "Not linked. Bind in the Hangar.",
                    SENSOR_UNLOCKS[i]);
        }
    }

    private int bindDockTop() {
        return VccMk2Menu.DOCK_TOP;
    }

    private int bindFooterY() {
        return VccMk2Menu.TABLE_TOP + VccMk2BlockEntity.CARD_ROWS * VccMk2Menu.ROW_H + 2;
    }

    private int[] bindTableGeom() {
        int sx = stageX() + PAD;
        int sw = stageW() - 2 * PAD;
        return new int[]{sx, sw, VccMk2Menu.TABLE_TOP, VccMk2Menu.ROW_H};
    }

    private int bindRowItemY(int row) {
        return VccMk2Menu.rowItemY(row);
    }

    private int bindColPart() {
        return VccMk2Menu.COL_PART;
    }

    private int bindColOut() {
        return VccMk2Menu.COL_OUT;
    }

    private int bindColType() {
        return VccMk2Menu.COL_TYPE;
    }

    private int bindColRole() {
        return VccMk2Menu.COL_ROLE;
    }

    private int[] partSlotXY(int row) {
        return VccMk2Menu.partSlotXY(row);
    }

    private int[] outSlotXY(int row, int which) {
        return VccMk2Menu.outSlotXY(row, which);
    }

    private int[] bindTypeRect(int row) {
        return new int[]{VccMk2Menu.COL_TYPE, VccMk2Menu.rowItemY(row) - 1, 70, 18};
    }

    private int[] dockSlotXY(int j) {
        return VccMk2Menu.dockSlotXY(j);
    }

    private void drawBind(GuiGraphics g, int mouseX, int mouseY) {
        VccMk2BlockEntity be = menu.blockEntity();
        int[] geom = bindTableGeom();
        int sx = geom[0];
        int sw = geom[1];
        int tableTop = geom[2];
        int rowH = geom[3];
        int right = sx + sw;

        g.drawString(font, "Bind  \u00b7  Hardware", sx, stageY() + 12, TXT, false);
        int bound = be != null ? be.boundRowCount() : 0;
        badge(g, right - 100, stageY() + 10, bound + " / 8 ENGINES", bound > 0 ? GREEN : DIM);
        g.drawString(font, "Drag linked cards from your inventory. Switch a row to REDSTONE to drive a burner or jet by frequency.",
                sx, stageY() + 28, DIM, false);

        int dotCol = right - 18;
        int headY = tableTop - 12;
        g.drawString(font, "#", sx + 6, headY, DIM, false);
        g.drawString(font, "PART", bindColPart(), headY, GOLD, false);
        g.drawString(font, "OUTPUT", bindColOut(), headY, GOLD, false);
        g.drawString(font, "TYPE", bindColType(), headY, GOLD, false);
        g.drawString(font, "ROLES", bindColRole(), headY, GOLD, false);
        g.drawString(font, "OK", dotCol - 4, headY, GOLD, false);
        g.fill(sx, headY + 11, right, headY + 12, GOLD_DK);

        for (int row = 0; row < VccMk2BlockEntity.CARD_ROWS; row++) {
            int bandTop = tableTop + row * rowH;
            g.fill(sx, bandTop, right, bandTop + rowH - 2, (row % 2 == 0) ? BAND_A : BAND_B);
            int itemY = bindRowItemY(row);
            g.drawString(font, Integer.toString(row + 1), sx + 6, itemY + 4, DIM, false);
            drawBindRow(g, be, row, mouseX, mouseY, dotCol);
        }

        int fy = bindFooterY();
        int[] counts = inventoryCardCounts();
        g.drawString(font, counts[0] + " propeller, " + counts[1] + " governor cards in inventory",
                sx, fy + 3, DIM, false);
        button(g, right - 226, fy, 108, 14, "BIND ALL", GREEN, mouseX, mouseY);
        tip(mouseX, mouseY, right - 226, fy, 108, 14, "Bind All", "Auto-fill empty kinetic rows from your inventory.");
        button(g, right - 112, fy, 108, 14, "CLEAR ALL", RED, mouseX, mouseY);
        tip(mouseX, mouseY, right - 112, fy, 108, 14, "Clear All", "Return every card and frequency to you.");
        drawBindDock(g, sx, right, mouseX, mouseY);
    }

    private void drawBindRow(GuiGraphics g, VccMk2BlockEntity be, int row, int mouseX, int mouseY, int dotCol) {
        boolean redstone = be != null && be.isRedstoneEngine(row);
        int itemY = bindRowItemY(row);

        boolean partCard = be != null && !be.cards().getStackInSlot(VccMk2BlockEntity.propSlot(row)).isEmpty();
        boolean partLinked = be != null && be.linkedPos(VccMk2BlockEntity.propSlot(row), PropellerCardItem.TAG_KEY) != null;
        int[] ps = partSlotXY(row);
        slotBack(g, ps[0], ps[1], partLinked ? GREEN : partCard ? AMBER : GOLD_DK, !partCard);
        if (!partCard) {
            tip(mouseX, mouseY, ps[0] - 1, ps[1] - 1, 18, 18, "Part (engine " + (row + 1) + ")",
                    "Drag a propeller card here. Right-click the part in-world first to link its position.");
        }

        boolean outOk;
        if (redstone) {
            outOk = be.hasEngineFreq(row);
            for (int which = 0; which < 2; which++) {
                int[] fs = outSlotXY(row, which);
                boolean has = !be.engineFreqStack(row, which).isEmpty();
                slotBack(g, fs[0], fs[1], has ? CYAN : GOLD_DK, !has);
                if (!has) {
                    tip(mouseX, mouseY, fs[0] - 1, fs[1] - 1, 18, 18, "Frequency " + (which + 1),
                            "Drag any item here to set the redstone frequency the VCC transmits on.");
                }
            }
            int barX = outSlotXY(row, 1)[0] + 18;
            drawMockSignal(g, barX, itemY, redstone && outOk);
        } else {
            boolean govCard = be != null && !be.cards().getStackInSlot(VccMk2BlockEntity.govSlot(row)).isEmpty();
            boolean govLinked = be != null && be.hasGovernor(row);
            outOk = govLinked;
            int[] gs = outSlotXY(row, 0);
            slotBack(g, gs[0], gs[1], govLinked ? GREEN : govCard ? AMBER : GOLD_DK, !govCard);
            if (!govCard) {
                tip(mouseX, mouseY, gs[0] - 1, gs[1] - 1, 18, 18, "Governor (engine " + (row + 1) + ")",
                        "Drag a governor card here so the VCC can drive this engine's RPM.");
            }
        }

        int[] tr = bindTypeRect(row);
        boolean th = inRect(mouseX, mouseY, tr[0], tr[1], tr[2], tr[3]);
        int tcol = redstone ? CYAN : GOLD;
        g.fill(tr[0], tr[1], tr[0] + tr[2], tr[1] + tr[3], th ? CARD : 0xFF11161D);
        g.renderOutline(tr[0], tr[1], tr[2], tr[3], tcol);
        String tl = redstone ? "REDSTONE" : "KINETIC";
        g.drawString(font, tl, tr[0] + (tr[2] - font.width(tl)) / 2, tr[1] + 5, tcol, false);
        tip(mouseX, mouseY, tr[0], tr[1], tr[2], tr[3], "Output type",
                redstone ? "Redstone: transmits 0-15 on a frequency. Click for kinetic."
                        : "Kinetic: drives a governor's RPM. Click for redstone.");

        drawBindRoles(g, be, row, bindColRole(), itemY + 4);

        int dot = (partLinked && outOk) ? GREEN : (partLinked || outOk) ? AMBER : GREY;
        g.fill(dotCol, itemY + 4, dotCol + 8, itemY + 12, dot);
    }

    private void slotBack(GuiGraphics g, int x, int y, int border, boolean showPlus) {
        g.fill(x - 1, y - 1, x + 17, y + 17, SLOTBG);
        g.renderOutline(x - 1, y - 1, 18, 18, border);
        if (showPlus) {
            g.drawString(font, "+", x + 5, y + 4, GREY, false);
        }
    }

    private void drawMockSignal(GuiGraphics g, int x, int y, boolean live) {
        int w = 16;
        int h = 6;
        int by = y + 5;
        g.fill(x, by, x + w, by + h, 0xFF05080C);
        g.renderOutline(x, by, w, h, GOLD_DK);
        double t = (System.currentTimeMillis() % 1000000L) / 1000.0;
        int lvl = live ? (int) (8 + 6 * (0.5 + 0.5 * Math.sin(t * 1.3 + x))) : 0;
        int fill = (int) ((w - 2) * (lvl / 15.0));
        if (fill > 0) {
            g.fill(x + 1, by + 1, x + 1 + fill, by + h - 1, live ? CYAN : GREY);
        }
    }

    private void drawBindDock(GuiGraphics g, int sx, int right, int mouseX, int mouseY) {
        int top = bindDockTop();
        g.fill(sx, top - 4, right, top - 3, GOLD_DK);
        g.drawString(font, "YOUR INVENTORY", sx, top, GOLD, false);
        for (int j = 0; j < 36; j++) {
            int[] xy = dockSlotXY(j);
            g.fill(xy[0] - 1, xy[1] - 1, xy[0] + 17, xy[1] + 17, SLOTBG);
            g.renderOutline(xy[0] - 1, xy[1] - 1, 18, 18, GOLD_DK);
        }
    }

    private void drawBindRoles(GuiGraphics g, VccMk2BlockEntity be, int row, int x, int y) {
        if (be == null) {
            return;
        }
        int mask = be.rowRoleMask(row);
        if (mask == 0) {
            g.drawString(font, "-", x, y, GREY, false);
            return;
        }
        int px = x;
        for (VccMk2Role r : VccMk2Role.ASSIGNABLE) {
            if ((mask & r.bit()) != 0) {
                g.fill(px, y - 1, px + 6, y + 6, r.color());
                g.renderOutline(px, y - 1, 6, 7, dim(r.color(), 0.6));
                px += 9;
            }
        }
        if (!be.hasOutput(row)) {
            g.drawString(font, "!", px + 2, y, RED, false);
        }
    }

    private static String relText(VccMk2BlockEntity be, BlockPos pos) {
        BlockPos o = be.getBlockPos();
        int dx = pos.getX() - o.getX();
        int dy = pos.getY() - o.getY();
        int dz = pos.getZ() - o.getZ();
        if (Math.abs(dx) > 999 || Math.abs(dy) > 999 || Math.abs(dz) > 999) {
            return "";
        }
        return "(" + sign(dx) + ", " + sign(dy) + ", " + sign(dz) + ")";
    }

    private static String sign(int v) {
        return (v > 0 ? "+" : "") + v;
    }

    private int[] inventoryCardCounts() {
        int prop = 0;
        int gov = 0;
        Player p = Minecraft.getInstance().player;
        if (p != null) {
            for (ItemStack s : p.getInventory().items) {
                if (s.getItem() instanceof PropellerCardItem) {
                    prop += s.getCount();
                } else if (s.getItem() instanceof GovernorCardItem) {
                    gov += s.getCount();
                }
            }
        }
        return new int[]{prop, gov};
    }

    private int[] mixSchematicRect() {
        int x = stageX() + PAD;
        int y = stageY() + 64;
        int w = (int) ((stageW() - 2 * PAD) * 0.60);
        int h = stageH() - 64 - PAD;
        return new int[]{x, y, w, h};
    }

    private int[] mixSideRect() {
        int[] s = mixSchematicRect();
        int x = s[0] + s[2] + 20;
        int w = stageX() + stageW() - PAD - x;
        return new int[]{x, s[1], w, s[3]};
    }

    private int[] mixAxesRect() {
        int[] s = mixSideRect();
        int h = 116;
        return new int[]{s[0], s[1] + s[3] - h, s[2], h};
    }

    private int[] mixViewBtnRect(int i) {
        int[] s = mixSchematicRect();
        int gap = 6;
        int bw = (s[2] - gap * 5) / 6;
        return new int[]{s[0] + i * (bw + gap), stageY() + 40, bw, 18};
    }

    private int[] mixAxisBtnRect(int i) {
        int[] a = mixAxesRect();
        int gap = 6;
        int bw = (a[2] - 24 - gap * (AXES.length - 1)) / AXES.length;
        return new int[]{a[0] + 12 + i * (bw + gap), a[1] + 28, bw, 22};
    }

    private int[] mixRoleBtnRect(int i) {
        int[] s = mixSideRect();
        int gap = 8;
        int bw = (s[2] - 24 - gap) / 2;
        int bh = 22;
        return new int[]{s[0] + 12 + (i % 2) * (bw + gap), s[1] + 104 + (i / 2) * (bh + gap), bw, bh};
    }

    private int[] mixAutoBtnRect() {
        int[] s = mixSideRect();
        int[] a = mixAxesRect();
        return new int[]{s[0] + 12, a[1] - 12 - 26, s[2] - 24, 26};
    }

    private double[] mixProjectLocal(double right, double fwd, double y) {
        return switch (mixView) {
            case TOP -> new double[]{right, fwd};
            case BOTTOM -> new double[]{-right, fwd};
            case FRONT -> new double[]{right, y};
            case REAR -> new double[]{-right, y};
            case LEFT -> new double[]{fwd, y};
            case RIGHT -> new double[]{-fwd, y};
        };
    }

    private double[] mixHV(VccMk2BlockEntity be, double dx, double dy, double dz) {
        Direction nose = be.noseDirection();
        double fx = nose.getStepX();
        double fz = nose.getStepZ();
        double right = dx * (-fz) + dz * fx;
        double fwd = dx * fx + dz * fz;
        return mixProjectLocal(right, fwd, dy);
    }

    private double[] mixScaleCenter(int[] rect) {
        VccMk2BlockEntity be = menu.blockEntity();
        double maxExtent = 1.0;
        if (be != null) {
            BlockPos o = be.getBlockPos();
            for (int row = 0; row < VccMk2BlockEntity.CARD_ROWS; row++) {
                BlockPos p = be.linkedPos(VccMk2BlockEntity.propSlot(row), PropellerCardItem.TAG_KEY);
                if (p == null) {
                    continue;
                }
                double[] hv = mixHV(be, p.getX() - o.getX(), p.getY() - o.getY(), p.getZ() - o.getZ());
                maxExtent = Math.max(maxExtent, Math.max(Math.abs(hv[0]), Math.abs(hv[1])));
            }
        }
        double usable = Math.min(rect[2], rect[3]) / 2.0 - 40;
        double scale = Math.max(6.0, Math.min(usable / maxExtent, 46.0));
        return new double[]{scale, rect[0] + rect[2] / 2.0, rect[1] + rect[3] / 2.0};
    }

    private int[][] mixNodePositions(int[] rect) {
        int[][] out = new int[VccMk2BlockEntity.CARD_ROWS][];
        VccMk2BlockEntity be = menu.blockEntity();
        if (be == null) {
            return out;
        }
        double[] sc = mixScaleCenter(rect);
        BlockPos o = be.getBlockPos();
        for (int row = 0; row < VccMk2BlockEntity.CARD_ROWS; row++) {
            BlockPos p = be.linkedPos(VccMk2BlockEntity.propSlot(row), PropellerCardItem.TAG_KEY);
            if (p == null) {
                continue;
            }
            double[] hv = mixHV(be, p.getX() - o.getX(), p.getY() - o.getY(), p.getZ() - o.getZ());
            int px = (int) Math.round(sc[1] + hv[0] * sc[0]);
            int py = (int) Math.round(sc[2] - hv[1] * sc[0]);
            for (int prev = 0; prev < row; prev++) {
                if (out[prev] == null) {
                    continue;
                }
                int it = 0;
                while (Math.abs(out[prev][0] - px) < 16 && Math.abs(out[prev][1] - py) < 16 && it < 8) {
                    px += 14;
                    if (px > rect[0] + rect[2] - 20) {
                        px -= 28;
                        py += 14;
                    }
                    it++;
                }
            }
            out[row] = new int[]{px, py};
        }
        return out;
    }

    private void drawMixer(GuiGraphics g, int mouseX, int mouseY) {
        VccMk2BlockEntity be = menu.blockEntity();
        int sx = stageX() + PAD;
        g.drawString(font, "Mixer  \u00b7  Roles & Mixing", sx, stageY() + 12, TXT, false);
        int propN = be != null ? be.boundPropellerCount() : 0;
        g.drawString(font, propN > 0
                        ? "Every engine gets a role. Auto-assign first, then tap any node to fine-tune."
                        : "Bind propeller cards in the Bind station, then come back to assign roles.",
                sx, stageY() + 26, DIM, false);

        MixView[] mv = MixView.values();
        String[] views = {"Top", "Bottom", "Front", "Rear", "Left", "Right"};
        for (int i = 0; i < 6; i++) {
            int[] r = mixViewBtnRect(i);
            boolean on = mv[i] == mixView;
            boolean hover = inRect(mouseX, mouseY, r[0], r[1], r[2], r[3]);
            g.fill(r[0], r[1], r[0] + r[2], r[1] + r[3], on ? CARD_SEL : (hover ? CARD : 0xFF11161D));
            g.renderOutline(r[0], r[1], r[2], r[3], on ? GOLD : GOLD_DK);
            g.drawString(font, views[i], r[0] + (r[2] - font.width(views[i])) / 2, r[1] + 5, on ? GOLD : DIM, false);
        }

        int[] sch = mixSchematicRect();
        g.fill(sch[0], sch[1], sch[0] + sch[2], sch[1] + sch[3], 0xFF080C11);
        g.renderOutline(sch[0], sch[1], sch[2], sch[3], GOLD_DK);
        drawMixGrid(g, sch, be);

        if (propN == 0) {
            String m = "No engines bound yet.";
            String m2 = "Open Bind and link your propeller cards.";
            g.drawString(font, m, sch[0] + (sch[2] - font.width(m)) / 2, sch[1] + sch[3] / 2 - 12, DIM, false);
            g.drawString(font, m2, sch[0] + (sch[2] - font.width(m2)) / 2, sch[1] + sch[3] / 2 + 2, GREY, false);
        } else {
            drawMixCraftBody(g, sch, be);
            drawMixNodes(g, sch, be, mouseX, mouseY);
        }
        drawMixNose(g, sch, be, mouseX, mouseY);

        int[] side = mixSideRect();
        boolean haveSel = mixSelRow >= 0 && be != null
                && be.linkedPos(VccMk2BlockEntity.propSlot(mixSelRow), PropellerCardItem.TAG_KEY) != null;
        if (haveSel) {
            drawMixInspector(g, side, be, mouseX, mouseY);
        } else {
            mixSelRow = -1;
            drawMixLegend(g, side, be, mouseX, mouseY);
        }
        drawMixAxes(g, side, mouseX, mouseY);
    }

    private void drawMixGrid(GuiGraphics g, int[] sch, VccMk2BlockEntity be) {
        int gridCol = 0xFF121821;
        for (int x = sch[0]; x <= sch[0] + sch[2]; x += 24) {
            g.fill(x, sch[1], x + 1, sch[1] + sch[3], gridCol);
        }
        for (int y = sch[1]; y <= sch[1] + sch[3]; y += 24) {
            g.fill(sch[0], y, sch[0] + sch[2], y + 1, gridCol);
        }
        String viewName = switch (mixView) {
            case TOP -> "TOP VIEW";
            case BOTTOM -> "BOTTOM VIEW";
            case FRONT -> "FRONT VIEW";
            case REAR -> "REAR VIEW";
            case LEFT -> "LEFT VIEW";
            default -> "RIGHT VIEW";
        };
        g.drawString(font, viewName, sch[0] + 8, sch[1] + sch[3] - 14, GREY, false);
    }

    private void drawMixCraftBody(GuiGraphics g, int[] sch, VccMk2BlockEntity be) {
        int[][] pos = mixNodePositions(sch);
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int count = 0;
        for (int[] p : pos) {
            if (p == null) {
                continue;
            }
            count++;
            minX = Math.min(minX, p[0]);
            maxX = Math.max(maxX, p[0]);
            minY = Math.min(minY, p[1]);
            maxY = Math.max(maxY, p[1]);
        }
        if (count == 0) {
            return;
        }
        int pad = 22;
        int hx0 = Math.max(sch[0] + 6, minX - pad);
        int hy0 = Math.max(sch[1] + 26, minY - pad);
        int hx1 = Math.min(sch[0] + sch[2] - 6, maxX + pad);
        int hy1 = Math.min(sch[1] + sch[3] - 18, maxY + pad);
        int hullCol = 0x1AF0CD78;
        int bcx = (hx0 + hx1) / 2;
        int bcy = (hy0 + hy1) / 2;

        double[] f = mixProjectLocal(0, 1, 0);
        int fdx = (int) Math.signum(f[0]);
        int fdy = (int) Math.signum(-f[1]);
        int len = 22;
        if (fdy < 0) {
            drawWedge(g, bcx, hy0, (hx1 - hx0) / 2, len, 0, -1, hullCol);
        } else if (fdy > 0) {
            drawWedge(g, bcx, hy1, (hx1 - hx0) / 2, len, 0, 1, hullCol);
        } else if (fdx > 0) {
            drawWedge(g, hx1, bcy, (hy1 - hy0) / 2, len, 1, 0, hullCol);
        } else if (fdx < 0) {
            drawWedge(g, hx0, bcy, (hy1 - hy0) / 2, len, -1, 0, hullCol);
        }
        g.fill(hx0, hy0, hx1, hy1, hullCol);
        g.renderOutline(hx0, hy0, hx1 - hx0, hy1 - hy0, GOLD_DK);

        if (fdx == 0 && fdy == 0) {
            String tag = mixView == MixView.FRONT ? "nose toward you" : "tail toward you";
            g.drawString(font, tag, bcx - font.width(tag) / 2, hy0 - 12, GOLD, false);
        } else {
            int nx = bcx + fdx * ((hx1 - hx0) / 2 + len + 6);
            int ny = bcy + fdy * ((hy1 - hy0) / 2 + len + 6);
            g.drawString(font, "NOSE", nx - font.width("NOSE") / 2, ny - 4, GOLD, false);
        }

        double[] sc = mixScaleCenter(sch);
        int ox = (int) Math.round(sc[1]);
        int oy = (int) Math.round(sc[2]);
        g.fill(ox - 5, oy, ox + 5, oy + 1, GOLD);
        g.fill(ox, oy - 5, ox + 1, oy + 5, GOLD);
        g.drawString(font, "CoM", ox + 7, oy - 11, GOLD_DK, false);
    }

    private void drawWedge(GuiGraphics g, int baseX, int baseY, int half, int len, int dx, int dy, int color) {
        if (half < 2 || len < 1) {
            return;
        }
        for (int i = 0; i < len; i++) {
            int h = (int) (half * (1 - i / (double) len));
            if (h < 1) {
                continue;
            }
            if (dy != 0) {
                int yy = baseY + dy * i;
                g.fill(baseX - h, yy, baseX + h, yy + 1, color);
            } else {
                int xx = baseX + dx * i;
                g.fill(xx, baseY - h, xx + 1, baseY + h, color);
            }
        }
    }

    private int[] mixNoseBtnRect() {
        int[] s = mixSchematicRect();
        return new int[]{s[0] + s[2] - 122, s[1] + 8, 114, 18};
    }

    private void drawMixNose(GuiGraphics g, int[] sch, VccMk2BlockEntity be, int mouseX, int mouseY) {
        int[] r = mixNoseBtnRect();
        boolean hover = inRect(mouseX, mouseY, r[0], r[1], r[2], r[3]);
        g.fill(r[0], r[1], r[0] + r[2], r[1] + r[3], hover ? CARD : 0xE60C1117);
        g.renderOutline(r[0], r[1], r[2], r[3], hover ? GOLD : GOLD_DK);
        String nm = be != null ? noseName(be.noseDirection()) : "-";
        g.drawString(font, "Nose " + nm, r[0] + 8, r[1] + 5, GOLD, false);
        g.drawString(font, "\u21bb", r[0] + r[2] - 13, r[1] + 5, hover ? GOLD : DIM, false);
    }

    private static String noseName(Direction d) {
        return switch (d) {
            case NORTH -> "N (-Z)";
            case SOUTH -> "S (+Z)";
            case EAST -> "E (+X)";
            case WEST -> "W (-X)";
            default -> d.getName();
        };
    }

    private void drawMixNodes(GuiGraphics g, int[] sch, VccMk2BlockEntity be, int mouseX, int mouseY) {
        int[][] pos = mixNodePositions(sch);
        double t = System.currentTimeMillis() / 180.0;
        for (int row = 0; row < VccMk2BlockEntity.CARD_ROWS; row++) {
            if (pos[row] == null) {
                continue;
            }
            int px = pos[row][0];
            int py = pos[row][1];
            int mask = be.rowRoleMask(row);
            int base = primaryColor(mask);
            boolean resp = mixPreviewAxis >= 0 && axisRowResponds(be, row, mixPreviewAxis);
            if (resp) {
                int pulse = (int) (5 * (0.5 + 0.5 * Math.sin(t)));
                g.renderOutline(px - 12 - pulse, py - 12 - pulse, 24 + pulse * 2, 24 + pulse * 2, CYAN);
                drawMixPush(g, be, row, px, py);
            }
            g.fill(px - 9, py - 9, px + 9, py + 9, dim(base, 0.34));
            g.renderOutline(px - 9, py - 9, 18, 18, base);
            String lbl = Integer.toString(row + 1);
            g.drawString(font, lbl, px - font.width(lbl) / 2 + 1, py - 3, 0xFF0A0E13, false);
            if (mask == 0) {
                g.drawString(font, "-", px + 11, py - 3, GREY, false);
            } else {
                int pipY = py - 9;
                for (VccMk2Role r : VccMk2Role.ASSIGNABLE) {
                    if ((mask & r.bit()) != 0) {
                        g.fill(px + 11, pipY, px + 17, pipY + 5, r.color());
                        pipY += 6;
                    }
                }
            }
            if (mask != 0 && !be.hasOutput(row)) {
                g.drawString(font, "!", px - 2, py - 20, RED, false);
                g.renderOutline(px - 10, py - 10, 20, 20, RED);
            }
            if (row == mixSelRow) {
                g.renderOutline(px - 11, py - 11, 22, 22, GOLD);
                g.renderOutline(px - 12, py - 12, 24, 24, GOLD);
            } else if (inRect(mouseX, mouseY, px - 9, py - 9, 18, 18)) {
                g.renderOutline(px - 11, py - 11, 22, 22, TXT);
            }
            tip(mouseX, mouseY, px - 9, py - 9, 18, 18, "Engine " + (row + 1),
                    roleSummary(mask), whyRow(mask));
        }
    }

    private static int primaryColor(int mask) {
        for (VccMk2Role r : VccMk2Role.PRIORITY) {
            if ((mask & r.bit()) != 0) {
                return r.color();
            }
        }
        return 0xFF4A525C;
    }

    private static boolean axisRowResponds(VccMk2BlockEntity be, int row, int axis) {
        for (VccMk2Role r : VccMk2Role.ASSIGNABLE) {
            if (be.hasRole(row, r) && axisResponds(axis, r)) {
                return true;
            }
        }
        return false;
    }

    // presentation only
    private double[] mixAxisPushLocal(VccMk2BlockEntity be, int row, int axis) {
        if (be == null || !axisRowResponds(be, row, axis)) {
            return null;
        }
        BlockPos p = be.linkedPos(VccMk2BlockEntity.propSlot(row), PropellerCardItem.TAG_KEY);
        if (p == null) {
            return null;
        }
        BlockPos o = be.getBlockPos();
        Direction nose = be.noseDirection();
        double dx = p.getX() - o.getX();
        double dy = p.getY() - o.getY();
        double dz = p.getZ() - o.getZ();
        double right = dx * (-nose.getStepZ()) + dz * nose.getStepX();
        double fwd = dx * nose.getStepX() + dz * nose.getStepZ();
        return switch (axis) {
            case 0 -> new double[]{0, 1, 0};
            case 1 -> new double[]{0, 0, 1};
            case 2 -> new double[]{-fwd, right, 0};
            case 3 -> new double[]{0, 0, Math.signum(fwd)};
            case 4 -> new double[]{0, 0, -Math.signum(right)};
            default -> null;
        };
    }

    private double[] mixViewNormalLocal() {
        return switch (mixView) {
            case TOP -> new double[]{0, 0, 1};
            case BOTTOM -> new double[]{0, 0, -1};
            case FRONT -> new double[]{0, 1, 0};
            case REAR -> new double[]{0, -1, 0};
            case LEFT -> new double[]{-1, 0, 0};
            case RIGHT -> new double[]{1, 0, 0};
        };
    }

    private void drawMixPush(GuiGraphics g, VccMk2BlockEntity be, int row, int px, int py) {
        double[] push = mixAxisPushLocal(be, row, mixPreviewAxis);
        if (push == null) {
            return;
        }
        double[] hv = mixProjectLocal(push[0], push[1], push[2]);
        double mag = Math.hypot(hv[0], hv[1]);
        double ts = System.currentTimeMillis() / 1000.0;
        if (mag > 0.05) {
            drawAxisArrow(g, px, py, hv[0] / mag, -hv[1] / mag, ts);
        } else {
            double[] nrm = mixViewNormalLocal();
            double outDot = push[0] * nrm[0] + push[1] * nrm[1] + push[2] * nrm[2];
            if (Math.abs(outDot) > 0.05) {
                drawMixOutOfPlane(g, px, py, outDot > 0, ts);
            }
        }
    }

    private void drawAxisArrow(GuiGraphics g, int px, int py, double ux, double uy, double t) {
        int len = 15;
        int sx = px + (int) Math.round(ux * 11);
        int sy = py + (int) Math.round(uy * 11);
        int tx = px + (int) Math.round(ux * (11 + len));
        int ty = py + (int) Math.round(uy * (11 + len));
        VccMk2CockpitArt.lineT(g, sx, sy, tx, ty, dim(CYAN, 0.85));
        double ang = Math.atan2(uy, ux);
        for (int s = -1; s <= 1; s += 2) {
            double a = ang + Math.PI + s * 0.5;
            int hx = tx + (int) Math.round(Math.cos(a) * 5);
            int hy = ty + (int) Math.round(Math.sin(a) * 5);
            VccMk2CockpitArt.lineT(g, tx, ty, hx, hy, CYAN);
        }
        double ph = (t * 1.6) % 1.0;
        int fx = sx + (int) Math.round(ux * ph * len);
        int fy = sy + (int) Math.round(uy * ph * len);
        g.fill(fx - 1, fy - 1, fx + 2, fy + 2, 0xFFFFFFFF);
    }

    private void drawMixOutOfPlane(GuiGraphics g, int px, int py, boolean toward, double t) {
        double ph = (t * 1.2) % 1.0;
        int r = toward ? 4 + (int) (ph * 9) : 13 - (int) (ph * 9);
        int alpha = (int) (210 * (1 - ph));
        VccMk2CockpitArt.ring(g, px, py, Math.max(2, r), (alpha << 24) | (CYAN & 0xFFFFFF), 18);
    }

    private double[] mixRespondCentroid(VccMk2BlockEntity be, int axis) {
        if (be == null) {
            return new double[]{0, 0, 0, 1};
        }
        Direction nose = be.noseDirection();
        BlockPos o = be.getBlockPos();
        double sumR = 0;
        double sumF = 0;
        double maxExt = 1;
        int n = 0;
        for (int row = 0; row < VccMk2BlockEntity.CARD_ROWS; row++) {
            if (!axisRowResponds(be, row, axis)) {
                continue;
            }
            BlockPos p = be.linkedPos(VccMk2BlockEntity.propSlot(row), PropellerCardItem.TAG_KEY);
            if (p == null) {
                continue;
            }
            double dx = p.getX() - o.getX();
            double dz = p.getZ() - o.getZ();
            double right = dx * (-nose.getStepZ()) + dz * nose.getStepX();
            double fwd = dx * nose.getStepX() + dz * nose.getStepZ();
            sumR += right;
            sumF += fwd;
            n++;
            maxExt = Math.max(maxExt, Math.max(Math.abs(right), Math.abs(fwd)));
        }
        if (n == 0) {
            return new double[]{0, 0, 0, 1};
        }
        return new double[]{sumF / n, sumR / n, n, maxExt};
    }

    private String mixBalanceText(VccMk2BlockEntity be, int axis) {
        double[] c = mixRespondCentroid(be, axis);
        if (c[2] == 0) {
            return "No engines drive " + AXES[axis] + " yet.";
        }
        int fp = (int) Math.round(c[0] / c[3] * 100);
        int rp = (int) Math.round(c[1] / c[3] * 100);
        int n = (int) c[2];
        if (axis == 1) {
            if (Math.abs(fp) < 8 && Math.abs(rp) < 8) {
                return "Lift balanced about center, steady hover.";
            }
            if (Math.abs(fp) >= Math.abs(rp)) {
                return "Lift " + Math.abs(fp) + "% " + (fp > 0 ? "forward, nose may rise." : "rear, tail may rise.");
            }
            return "Lift " + Math.abs(rp) + "% " + (rp > 0 ? "right, may bank right." : "left, may bank left.");
        }
        if (axis == 0) {
            if (Math.abs(rp) < 8) {
                return n + (n == 1 ? " engine pushes" : " engines push") + " forward, tracking true.";
            }
            return "Thrust " + Math.abs(rp) + "% " + (rp > 0 ? "right, may veer right." : "left, may veer left.");
        }
        return n + (n == 1 ? " engine drives " : " engines drive ") + AXES[axis] + ".";
    }

    private boolean mixBalanceWarn(VccMk2BlockEntity be, int axis) {
        double[] c = mixRespondCentroid(be, axis);
        if (c[2] == 0) {
            return true;
        }
        if (axis == 0 || axis == 1) {
            int fp = (int) Math.round(c[0] / c[3] * 100);
            int rp = (int) Math.round(c[1] / c[3] * 100);
            return Math.abs(fp) >= 8 || Math.abs(rp) >= 8;
        }
        return false;
    }

    private void drawMixLegend(GuiGraphics g, int[] side, VccMk2BlockEntity be, int mouseX, int mouseY) {
        int x = side[0];
        int y = side[1];
        int w = side[2];
        int[] axes = mixAxesRect();
        int panelH = axes[1] - y - 12;
        panel(g, x, y, w, panelH);
        g.drawString(font, "ROLES", x + 12, y + 10, GOLD, false);
        int ry = y + 30;
        for (VccMk2Role r : VccMk2Role.ASSIGNABLE) {
            int cnt = be != null ? be.roleCount(r) : 0;
            g.fill(x + 12, ry, x + 24, ry + 12, dim(r.color(), 0.30));
            g.renderOutline(x + 12, ry, 12, 12, r.color());
            g.drawString(font, r.label(), x + 32, ry + 2, cnt > 0 ? TXT : DIM, false);
            if (cnt > 0) {
                g.drawString(font, "x" + cnt, x + w - 30, ry + 2, r.color(), false);
            }
            ry += 18;
        }
        int[] ab = mixAutoBtnRect();
        g.drawString(font, be != null && be.profile().isGround()
                        ? "Seeds Drive on the wheels and Steer on the front axle."
                        : "Reads each engine's thrust direction to seed Lift / Thrust.",
                x + 12, ab[1] - 14, DIM, false);
        button(g, ab[0], ab[1], ab[2], ab[3], "AUTO-ASSIGN ROLES", GOLD, mouseX, mouseY);
        tip(mouseX, mouseY, ab[0], ab[1], ab[2], ab[3], "Auto-assign Roles",
                "Reads geometry to seed Lift, Thrust, Drive and Steer.");
    }

    private void drawMixInspector(GuiGraphics g, int[] side, VccMk2BlockEntity be, int mouseX, int mouseY) {
        int x = side[0];
        int y = side[1];
        int w = side[2];
        int[] axes = mixAxesRect();
        panel(g, x, y, w, axes[1] - y - 12);
        int row = mixSelRow;
        int mask = be.rowRoleMask(row);
        g.drawString(font, "ENGINE " + (row + 1), x + 12, y + 10, GOLD, false);
        boolean bh = inRect(mouseX, mouseY, x + w - 58, y + 8, 46, 14);
        g.renderOutline(x + w - 58, y + 8, 46, 14, bh ? TXT : GOLD_DK);
        g.drawString(font, "BACK", x + w - 50, y + 11, bh ? TXT : DIM, false);

        BlockPos p = be.linkedPos(VccMk2BlockEntity.propSlot(row), PropellerCardItem.TAG_KEY);
        String posTxt = p != null ? relText(be, p) : "";
        g.drawString(font, "Position " + posTxt + " from console", x + 12, y + 30, DIM, false);
        g.drawString(font, "Now: " + roleSummary(mask), x + 12, y + 46, mask == 0 ? DIM : TXT, false);
        g.drawString(font, whyRow(mask), x + 12, y + 62, DIM, false);
        if (mask != 0 && !be.hasOutput(row)) {
            g.drawString(font, "\u26a0 No output driver. Bind a governor or a frequency.", x + 12, y + 76, RED, false);
        }

        g.drawString(font, "ROLES  (tap to toggle, combine freely)", x + 12, y + 88, GOLD, false);
        VccMk2Role[] pick = VccMk2Role.ASSIGNABLE;
        for (int i = 0; i < pick.length; i++) {
            int[] r = mixRoleBtnRect(i);
            boolean on = (mask & pick[i].bit()) != 0;
            boolean hover = inRect(mouseX, mouseY, r[0], r[1], r[2], r[3]);
            g.fill(r[0], r[1], r[0] + r[2], r[1] + r[3], on ? dim(pick[i].color(), 0.34) : (hover ? CARD : 0xFF11161D));
            g.renderOutline(r[0], r[1], r[2], r[3], on ? pick[i].color() : GOLD_DK);
            g.fill(r[0] + 6, r[1] + 7, r[0] + 14, r[1] + 15, pick[i].color());
            g.drawString(font, pick[i].label(), r[0] + 20, r[1] + 7, on ? pick[i].color() : TXT, false);
            if (on) {
                g.drawString(font, "\u2713", r[0] + r[2] - 12, r[1] + 7, pick[i].color(), false);
            }
            tip(mouseX, mouseY, r[0], r[1], r[2], r[3], pick[i].label() + " role", roleTip(pick[i]));
        }
    }

    private static String roleSummary(int mask) {
        if (mask == 0) {
            return "Idle (unused)";
        }
        StringBuilder sb = new StringBuilder();
        for (VccMk2Role r : VccMk2Role.ASSIGNABLE) {
            if ((mask & r.bit()) != 0) {
                if (sb.length() > 0) {
                    sb.append(" + ");
                }
                sb.append(r.label());
            }
        }
        return sb.toString();
    }

    private static String whyRow(int mask) {
        if (mask == 0) {
            return "Not used. Tap roles below, or run Auto-assign.";
        }
        boolean drive = (mask & VccMk2Role.DRIVE.bit()) != 0;
        boolean steer = (mask & VccMk2Role.STEER.bit()) != 0;
        if (drive && steer) {
            return "This wheel both drives and steers.";
        }
        if ((mask & VccMk2Role.LIFT.bit()) != 0) {
            return "Lifts the craft; trims pitch & roll by its position.";
        }
        if ((mask & VccMk2Role.THRUST.bit()) != 0) {
            return "Pushes the craft forward (throttle).";
        }
        if (steer) {
            return "Steers the craft.";
        }
        if (drive) {
            return "Drives the wheels.";
        }
        return "Drives the selected control axes.";
    }

    private static String roleTip(VccMk2Role r) {
        return switch (r) {
            case LIFT -> "Holds the craft up, trims pitch and roll.";
            case THRUST -> "Pushes forward for throttle.";
            case YAW -> "Turns the craft by differential or vectoring.";
            case PITCH -> "Noses up and down.";
            case ROLL -> "Banks left and right.";
            case DRIVE -> "Powers the wheels forward and back.";
            case STEER -> "Steers the wheels left and right.";
            default -> "Idle, not used.";
        };
    }

    private void drawMixAxes(GuiGraphics g, int[] side, int mouseX, int mouseY) {
        int[] a = mixAxesRect();
        panel(g, a[0], a[1], a[2], a[3]);
        g.drawString(font, "AXIS PREVIEW", a[0] + 12, a[1] + 10, GOLD, false);
        for (int i = 0; i < AXES.length; i++) {
            int[] r = mixAxisBtnRect(i);
            boolean on = mixPreviewAxis == i;
            boolean hover = inRect(mouseX, mouseY, r[0], r[1], r[2], r[3]);
            g.fill(r[0], r[1], r[0] + r[2], r[1] + r[3], on ? CARD_SEL : (hover ? CARD : 0xFF11161D));
            g.renderOutline(r[0], r[1], r[2], r[3], on ? CYAN : GOLD_DK);
            g.drawString(font, AXES[i], r[0] + (r[2] - font.width(AXES[i])) / 2, r[1] + 7, on ? CYAN : DIM, false);
            tip(mouseX, mouseY, r[0], r[1], r[2], r[3], "Test " + AXES[i],
                    "Highlights responding engines and shows push arrows.");
        }
        if (mixPreviewAxis >= 0) {
            VccMk2BlockEntity be = menu.blockEntity();
            g.drawString(font, "Arrows show each engine's push.", a[0] + 12, a[1] + a[3] - 30, DIM, false);
            g.drawString(font, mixBalanceText(be, mixPreviewAxis), a[0] + 12, a[1] + a[3] - 16,
                    mixBalanceWarn(be, mixPreviewAxis) ? AMBER : GREEN, false);
        } else {
            g.drawString(font, "Tap an axis to preview which engines respond.",
                    a[0] + 12, a[1] + a[3] - 16, DIM, false);
        }
    }

    private static boolean axisResponds(int axis, VccMk2Role role) {
        return switch (axis) {
            case 0 -> role == VccMk2Role.THRUST || role == VccMk2Role.DRIVE;
            case 1 -> role == VccMk2Role.LIFT;
            case 2 -> role == VccMk2Role.YAW || role == VccMk2Role.STEER
                    || role == VccMk2Role.THRUST || role == VccMk2Role.LIFT;
            case 3 -> role == VccMk2Role.PITCH || role == VccMk2Role.LIFT;
            case 4 -> role == VccMk2Role.ROLL || role == VccMk2Role.LIFT;
            default -> false;
        };
    }

    private static int dim(int argb, double f) {
        int a = (argb >>> 24) & 0xFF;
        int r = (int) (((argb >> 16) & 0xFF) * f);
        int gg = (int) (((argb >> 8) & 0xFF) * f);
        int b = (int) ((argb & 0xFF) * f);
        return (a << 24) | (r << 16) | (gg << 8) | b;
    }

    private int controlBandH() {
        VccMk2BlockEntity be = menu.blockEntity();
        boolean ground = be != null && be.profileChosen() && be.profile().isGround();
        int base = Math.max(94, Math.min(150, (int) (stageH() * 0.32)));
        return ground ? base + 44 : base;
    }

    private int[] controlBandRect() {
        int h = controlBandH();
        int y = stageY() + stageH() - PAD - h;
        return new int[]{stageX() + PAD, y, stageW() - 2 * PAD, h};
    }

    private int[] cockpitPanel() {
        int panelW = 250;
        int panelX = stageX() + stageW() - PAD - panelW;
        int top = stageY() + 40;
        int bottom = controlBandRect()[1] - 8;
        return new int[]{panelX, top, panelW, bottom - top};
    }

    private int[] cockpitSwitchRect(int i) {
        int[] p = cockpitPanel();
        int x = p[0] + 10;
        int w = p[2] - 20;
        int yTop = p[1] + 10;
        if (i == 0) {
            return new int[]{x, yTop, w, 60};
        }
        return new int[]{x, yTop + 68 + (i - 1) * 56, w, 48};
    }

    private int[] cockpitInstrRect() {
        int[] p = cockpitPanel();
        int ix = stageX() + PAD;
        int iy = stageY() + 40;
        return new int[]{ix, iy, p[0] - 18 - ix, (p[1] + p[3]) - iy};
    }

    private void drawCockpit(GuiGraphics g, int mouseX, int mouseY) {
        VccMk2BlockEntity be = menu.blockEntity();
        boolean armed = be != null && be.armed();
        boolean auto = be != null && be.autoMode();
        boolean assisted = be == null || be.assisted();
        boolean profileSet = be != null && be.profileChosen();
        VccMk2CraftProfile chosen = profileSet ? be.profile() : VccMk2CraftProfile.CUSTOM;
        boolean ground = chosen.isGround();
        int propN = be != null ? be.boundPropellerCount() : 0;
        int govN = be != null ? be.boundGovernorCount() : 0;
        int roleN = be != null ? be.assignedEngineCount() : 0;
        int missGov = be != null ? be.enginesMissingOutput() : 0;
        boolean onCraft = be != null && be.isOnContraption();
        boolean canArm = profileSet && propN > 0 && govN > 0 && roleN > 0;

        if (chosen != auxProfile) {
            auxProfile = chosen;
        }
        aux0On = be != null && be.aux(0);

        aux1On = ground ? (be != null && be.handbrake()) : (be != null && be.aux(1));
        armAnim += ((armed ? 1f : 0f) - armAnim) * 0.3f;
        modeAnim += ((auto ? 1f : 0f) - modeAnim) * 0.3f;
        stabAnim += ((assisted ? 1f : 0f) - stabAnim) * 0.3f;
        aux0Anim += ((aux0On ? 1f : 0f) - aux0Anim) * 0.3f;
        aux1Anim += ((aux1On ? 1f : 0f) - aux1Anim) * 0.3f;

        double t = (System.currentTimeMillis() % 1000000L) / 1000.0;

        boolean live = armed && be != null;
        double pitch = live ? be.telemPitch() : Math.sin(t * 0.55) * 7 * 0.22;
        double roll = live ? be.telemRoll() : Math.sin(t * 0.42 + 1) * 11 * 0.22;
        double heading = live ? be.telemHeading() : 30;
        double speed = live ? be.telemSpeed() : 0;
        double rpm = live ? be.telemRpm() : 0;
        double alt = live ? be.telemAltitude() : (be != null ? be.getBlockPos().getY() : 64);

        int sx = stageX() + PAD;
        String deckName = ground ? " Cockpit" : chosen == VccMk2CraftProfile.BOAT ? " Helm" : " Flight Deck";
        g.drawString(font, "Cockpit  \u00b7  " + chosen.label() + deckName, sx, stageY() + 12, TXT, false);
        String sub = !armed ? "Standby. Flip ARM when the pre-flight is green."
                : auto ? (ground ? "ARMED. AUTO self-drive engaged."
                : "ARMED. AUTO cruise: holding speed & heading, you fly levitation.")
                : (ground ? "ARMED. Driving, outputs are live." : "ARMED. Manual flight, outputs are live.");
        g.drawString(font, sub, sx, stageY() + 26, armed ? (auto ? GREEN : CYAN) : DIM, false);
        drawCockpitSensorStrip(g, be, mouseX, mouseY);

        int[] in = cockpitInstrRect();
        g.fill(in[0], in[1], in[0] + in[2], in[1] + in[3], 0xFF0B1016);
        g.renderOutline(in[0], in[1], in[2], in[3], GOLD_DK);
        if (ground) {
            drawCarDashboard(g, in, be, armed, t, speed, rpm, heading, pitch, roll, mouseX, mouseY);
        } else {
            drawAirCluster(g, in, be, chosen, armed, t, heading, pitch, roll, alt, profileSet, missGov, mouseX, mouseY);
        }

        int[] p = cockpitPanel();
        panel(g, p[0], p[1], p[2], p[3]);
        drawSwitchPanel(g, p, chosen, be, armed, auto, assisted, canArm, mouseX, mouseY);

        int ry = p[1] + p[3] - 88;
        g.fill(p[0] + 8, ry - 8, p[0] + p[2] - 8, ry - 7, GOLD_DK);
        g.drawString(font, "PRE-FLIGHT", p[0] + 10, ry, GOLD, false);
        ry += 14;
        int cx = p[0] + 10;
        cockpitCheck(g, cx, ry, profileSet, "Craft");
        cockpitCheck(g, cx + 80, ry, propN > 0 && govN > 0, "Engines");
        cockpitCheck(g, cx + 168, ry, roleN > 0 && missGov == 0, "Roles");
        ry += 16;
        cockpitCheck(g, cx, ry, onCraft, "Assembled");
        cockpitCheck(g, cx + 100, ry, canArm, "Ready");
        ry += 18;
        String big = armed ? "LIVE" : canArm ? "READY TO ARM" : "SETUP INCOMPLETE";
        int bigCol = armed ? GREEN : canArm ? CYAN : AMBER;
        g.fill(p[0] + 8, ry, p[0] + p[2] - 8, ry + 16, dim(bigCol, 0.18));
        g.renderOutline(p[0] + 8, ry, p[2] - 16, 16, bigCol);
        g.drawString(font, big, p[0] + (p[2] - font.width(big)) / 2, ry + 4, bigCol, false);

        drawControlConsole(g, controlBandRect(), chosen, mouseX, mouseY);
    }

    private void drawAirCluster(GuiGraphics g, int[] in, VccMk2BlockEntity be, VccMk2CraftProfile chosen,
                                boolean armed, double t, double heading, double pitch, double roll, double alt,
                                boolean profileSet, int missGov, int mouseX, int mouseY) {
        int ix = in[0];
        int iy = in[1];
        int iw = in[2];
        int ih = in[3];
        int rp = Math.min(Math.min(iw / 4, ih / 3), 74);
        int cxP = ix + rp + 18;
        int cyP = iy + rp + 22;
        VccMk2CockpitArt.drawAttitude(g, font, cxP, cyP, rp, pitch, roll, armed);
        tip(mouseX, mouseY, cxP - rp, cyP - rp, rp * 2, rp * 2, "Attitude Indicator",
                "Pitch and roll against the horizon.");
        int cgx = ix + iw / 2 + 8;
        int cgw = ix + iw - cgx - 6;
        int cgh = (int) (ih * 0.52);
        VccMk2CockpitArt.drawCraftGraphic(g, font, chosen, cgx, iy + 6, cgw, cgh, t);
        tip(mouseX, mouseY, cgx, iy + 6, cgw, cgh, chosen.label() + " schematic",
                "Live silhouette with moving control surfaces.");
        int gy = iy + (int) (ih * 0.58);
        int rs = Math.min(Math.min(iw / 9, (iy + ih - gy) / 2 - 14), 40);
        int cxS = ix + rs + 16;
        int cyS = gy + rs + 14;
        VccMk2CockpitArt.drawDial(g, font, cxS, cyS, rs, alt, 256, "ALT", "m", armed ? GOLD : DIM);
        tip(mouseX, mouseY, cxS - rs, cyS - rs, rs * 2, rs * 2, "Altitude", "Height above the world floor.");
        int tapeX = cxS + rs + 22;
        VccMk2CockpitArt.drawHeadingTape(g, font, tapeX, gy + 6, ix + iw - tapeX - 8, 36, heading);
        tip(mouseX, mouseY, tapeX, gy + 6, ix + iw - tapeX - 8, 36, "Heading", "Compass heading the nose points.");
        boolean[] act = activeAxes(profileSet ? chosen : null);
        int axY = iy + ih - 14;
        g.drawString(font, "AXIS", ix + 6, axY, GOLD, false);
        int axX = ix + 6 + font.width("AXIS") + 8;
        for (int i = 0; i < AXES.length; i++) {
            if (!act[i]) {
                continue;
            }
            boolean ok = !respondingEngines(be, i).isEmpty();
            String tok = AXES[i];
            g.fill(axX, axY + 1, axX + 5, axY + 6, ok ? GREEN : AMBER);
            g.drawString(font, tok, axX + 9, axY, ok ? DIM : AMBER, false);
            axX += 9 + font.width(tok) + 14;
        }
        if (missGov > 0) {
            g.drawString(font, "\u26a0 " + missGov + " no output", ix + iw - 120, axY, RED, false);
        }
    }

    private void drawCarDashboard(GuiGraphics g, int[] in, VccMk2BlockEntity be, boolean armed, double t,
                                  double speedKmh, double rpm, double heading, double pitch, double roll,
                                  int mouseX, int mouseY) {
        int ix = in[0];
        int iy = in[1];
        int iw = in[2];
        int ih = in[3];

        long now = System.nanoTime();
        float dt = dashLastNanos == 0L ? 1f / 60f : Math.min(0.25f, (now - dashLastNanos) / 1.0e9f);
        dashLastNanos = now;
        float targetMps = (float) (speedKmh / 3.6);
        dashSpeedMps += (targetMps - dashSpeedMps) * Math.min(1f, dt * 6f);
        dashRpm += ((float) rpm - dashRpm) * Math.min(1f, dt * 6f);
        float thr = be != null ? Math.max(0f, be.axisDemand(VccMk2BlockEntity.AXIS_THROTTLE)) : 0f;
        float brk = be != null ? Math.max(be.axisDemand(VccMk2BlockEntity.AXIS_BRAKE), be.handbrake() ? 1f : 0f) : 0f;
        dashThrottle += (thr - dashThrottle) * Math.min(1f, dt * 8f);
        dashBrake += (brk - dashBrake) * Math.min(1f, dt * 8f);
        if (armed) {
            dashTripM += Math.abs(dashSpeedMps) * dt;
            float aG = ((dashSpeedMps - dashPrevSpeedMps) / Math.max(1e-3f, dt)) / 9.81f;
            if (Math.abs(aG) < 25f) {
                dashAccelG += (aG - dashAccelG) * Math.min(1f, dt * 3f);
            }
            float dh = (float) wrapDeg180(heading - dashPrevHeading);
            float latG = (float) (Math.toRadians(dh) / Math.max(1e-3f, dt) * dashSpeedMps / 9.81);
            if (Math.abs(latG) < 25f) {
                dashLatG += (latG - dashLatG) * Math.min(1f, dt * 3f);
            }
            if (dashSpeedMps * 3.6f > dashTopKmh) {
                dashTopKmh = dashSpeedMps * 3.6f;
            }
        } else {
            dashAccelG += (0f - dashAccelG) * Math.min(1f, dt * 3f);
            dashLatG += (0f - dashLatG) * Math.min(1f, dt * 3f);
        }
        dashPrevSpeedMps = dashSpeedMps;
        dashPrevHeading = (float) heading;
        float showKmh = dashSpeedMps * 3.6f;

        int pad = 10;
        int topH = (int) (ih * 0.56);
        int rSpeed = Math.min(Math.min(iw / 6, topH / 2 - 12), 64);
        if (rSpeed < 26) {
            rSpeed = 26;
        }
        int spCx = ix + pad + rSpeed;
        int spCy = iy + 16 + rSpeed;
        VccMk2CockpitArt.drawCarGauge(g, font, spCx, spCy, rSpeed, dashSpeedMps, 40, 0.85, "SPEED", "m/s",
                armed ? CYAN : DIM, armed);
        String kmh = String.format("%.0f km/h", showKmh);
        g.drawString(font, kmh, spCx - font.width(kmh) / 2, spCy + rSpeed - 2, DIM, false);
        tip(mouseX, mouseY, spCx - rSpeed, spCy - rSpeed, rSpeed * 2, rSpeed * 2, "Speedometer",
                "Live ground speed from the craft's velocity.");

        int rTach = (int) (rSpeed * 0.82);
        int tcCx = spCx + rSpeed + rTach + 18;
        int tcCy = spCy;
        VccMk2CockpitArt.drawCarGauge(g, font, tcCx, tcCy, rTach, dashRpm, 2000, 0.80, "RPM", "",
                armed ? GOLD : DIM, armed);
        tip(mouseX, mouseY, tcCx - rTach, tcCy - rTach, rTach * 2, rTach * 2, "Tachometer",
                "Average drive RPM across the governors.");

        int gsX = tcCx + rTach + 14;
        int gsW = 28;
        int gsTop = iy + 14;
        int gsH = Math.min(rSpeed * 2, topH - 8);
        if (gsX + gsW <= ix + iw - 4) {
            VccMk2CockpitArt.drawGearStack(g, font, gsX, gsTop, gsW, gsH, be != null ? be.gear() : 0, armed);
            tip(mouseX, mouseY, gsX, gsTop, gsW, gsH, "Gear", "Park / Reverse / Neutral / Drive.");
        }

        int boxW = Math.min(70, (ix + iw - (gsX + gsW) - 16) / 2);
        if (boxW >= 44) {
            int boxH = Math.min(topH - 6, 58);
            int gMx = ix + iw - pad - boxW;
            VccMk2CockpitArt.drawGMeter(g, font, gMx, iy + 14, boxW, boxH, dashAccelG, dashLatG);
            tip(mouseX, mouseY, gMx, iy + 14, boxW, boxH, "G-force",
                    "Longitudinal (accel / brake) and lateral (cornering) g.");
            VccMk2CockpitArt.drawInclinometer(g, font, gMx - boxW - 8, iy + 14, boxW, boxH, pitch, roll, armed);
            tip(mouseX, mouseY, gMx - boxW - 8, iy + 14, boxW, boxH, "Inclinometer",
                    "Body pitch and roll on slopes.");
        }

        int botY = iy + topH + 4;
        int botH = iy + ih - botY - 4;
        if (botH >= 44) {
            int wdW = Math.min((int) (iw * 0.42), iw - 220);
            if (wdW < 90) {
                wdW = Math.min(iw / 2, 150);
            }
            drawWheelDiagram(g, be, ix + pad, botY, wdW, botH, armed, mouseX, mouseY);

            int barW = 12;
            int barsX = ix + iw - pad - barW;
            VccMk2CockpitArt.drawPedalBar(g, font, barsX, botY, barW, botH, dashThrottle, "T", GREEN);
            tip(mouseX, mouseY, barsX, botY, barW, botH, "Throttle", "Current throttle demand.");
            VccMk2CockpitArt.drawPedalBar(g, font, barsX - barW - 4, botY, barW, botH, dashBrake, "B", RED);
            tip(mouseX, mouseY, barsX - barW - 4, botY, barW, botH, "Brake", "Brake + handbrake demand.");

            int rx2 = ix + pad + wdW + 12;
            int chipRight = barsX - barW - 12;
            drawCarStatusChips(g, be, rx2, botY, chipRight);
            int colY = botY + 34;
            if (chipRight - rx2 > 60) {
                VccMk2CockpitArt.drawFuelBar(g, font, rx2, colY, chipRight - rx2,
                        be != null ? be.fuelAmount() : 0f, be != null ? be.fuelCapacity() : 0f, armed);
                tip(mouseX, mouseY, rx2, colY, chipRight - rx2, 18, "Fuel",
                        "Total diesel summed across the craft's diesel engines.");
                colY += 22;
            }
            drawCarTripStats(g, rx2, colY);
        }
    }

    private void drawCarStatusChips(GuiGraphics g, VccMk2BlockEntity be, int x, int y, int rightX) {
        int dm = be != null ? be.driveMode() : 0;
        String drive = dm == 0 ? "RWD" : dm == 1 ? "FWD" : "4x4";
        int sm = be != null ? be.steerMode() : 0;
        String steer = sm == 0 ? "FRONT" : sm == 1 ? "4WS" : "CRAB";
        int df = be != null ? be.diffMode() : 0;
        String diff = df == 0 ? "OPEN" : df == 1 ? "REAR" : "FULL";
        boolean inv = be != null && be.steerInvert();
        boolean hb = be != null && be.handbrake();
        boolean lights = be != null && be.aux(0);
        String[] labels = {"DRIVE " + drive, "STEER " + steer, "DIFF " + diff,
                "INV " + (inv ? "ON" : "OFF"), "HBRAKE " + (hb ? "SET" : "OFF"), "LIGHTS " + (lights ? "ON" : "OFF")};
        int[] cols = {CYAN, GOLD, AMBER, inv ? AMBER : GREY, hb ? RED : GREY, lights ? AMBER : GREY};
        int cx = x;
        int cy = y;
        for (int i = 0; i < labels.length; i++) {
            int w = font.width(labels[i]) + 12;
            if (cx + w > rightX) {
                cx = x;
                cy += 16;
            }
            g.fill(cx, cy, cx + w, cy + 13, 0xFF11161D);
            g.renderOutline(cx, cy, w, 13, dim(cols[i], 0.7));
            g.drawString(font, labels[i], cx + 6, cy + 3, cols[i], false);
            cx += w + 6;
        }
    }

    private void drawCarTripStats(GuiGraphics g, int x, int y) {
        String trip = dashTripM < 1000 ? String.format("TRIP  %.0f m", dashTripM)
                : String.format("TRIP  %.2f km", dashTripM / 1000.0);
        String top = String.format("TOP  %.1f m/s", dashTopKmh / 3.6);
        g.drawString(font, trip, x, y, TXT, false);
        g.drawString(font, top, x, y + 11, GOLD, false);
    }

    private void drawWheelDiagram(GuiGraphics g, VccMk2BlockEntity be, int x, int y, int w, int h,
                                  boolean armed, int mouseX, int mouseY) {
        g.fill(x, y, x + w, y + h, 0xFF080C11);
        g.renderOutline(x, y, w, h, GOLD_DK);
        g.drawString(font, "WHEELS  \u00b7  top view", x + 4, y + 3, GOLD, false);
        if (be == null) {
            return;
        }
        BlockPos o = be.getBlockPos();
        Direction nose = be.noseDirection();
        List<float[]> wheels = new ArrayList<>();
        double maxExt = 1;
        for (int r = 0; r < VccMk2BlockEntity.CARD_ROWS; r++) {
            BlockPos p = be.linkedPos(VccMk2BlockEntity.propSlot(r), PropellerCardItem.TAG_KEY);
            if (p == null) {
                continue;
            }
            double dx = p.getX() - o.getX();
            double dz = p.getZ() - o.getZ();
            double right = dx * (-nose.getStepZ()) + dz * nose.getStepX();
            double fwd = dx * nose.getStepX() + dz * nose.getStepZ();
            boolean drive = be.hasRole(r, VccMk2Role.DRIVE);
            boolean steer = be.hasRole(r, VccMk2Role.STEER);
            wheels.add(new float[]{(float) right, (float) fwd, drive ? 1 : 0, steer ? 1 : 0, r});
            maxExt = Math.max(maxExt, Math.max(Math.abs(right), Math.abs(fwd)));
        }
        if (wheels.isEmpty()) {
            String m = "No wheels bound";
            g.drawString(font, m, x + (w - font.width(m)) / 2, y + h / 2 - 4, DIM, false);
            g.drawString(font, "Tag DRIVE / STEER in Mixer", x + (w - font.width("Tag DRIVE / STEER in Mixer")) / 2,
                    y + h / 2 + 8, GREY, false);
            return;
        }
        int cx = x + w / 2;
        int cy = y + h / 2 + 6;
        double scale = Math.min((w / 2.0 - 16) / maxExt, (h / 2.0 - 18) / maxExt);
        scale = Math.max(4, Math.min(scale, 22));
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (float[] wl : wheels) {
            int px = cx + (int) Math.round(wl[0] * scale);
            int py = cy - (int) Math.round(wl[1] * scale);
            minX = Math.min(minX, px);
            maxX = Math.max(maxX, px);
            minY = Math.min(minY, py);
            maxY = Math.max(maxY, py);
        }
        g.fill(minX - 8, minY - 8, maxX + 8, maxY + 8, 0x1AF0CD78);
        g.renderOutline(minX - 8, minY - 8, (maxX + 8) - (minX - 8), (maxY + 8) - (minY - 8), GOLD_DK);
        g.drawString(font, "front", cx - font.width("front") / 2, Math.max(y + 12, minY - 18), DIM, false);
        float yawDemand = be.axisDemand(VccMk2BlockEntity.AXIS_YAW);
        for (float[] wl : wheels) {
            int px = cx + (int) Math.round(wl[0] * scale);
            int py = cy - (int) Math.round(wl[1] * scale);
            boolean drive = wl[2] > 0.5f;
            boolean steer = wl[3] > 0.5f;
            int row = (int) wl[4];
            int cmd = be.wheelCmdRpm(row);
            int act = be.wheelActRpm(row);
            double ang = 0;
            if (steer && armed) {
                ang = Math.toRadians(yawDemand * (be.steerInvert() ? -28 : 28));
            }
            int dxw = (int) Math.round(Math.sin(ang) * 6);
            int dyw = (int) Math.round(Math.cos(ang) * 6);
            int col;
            if (drive && armed && cmd != 0) {
                col = cmd > 0 ? GREEN : AMBER;
            } else {
                col = drive ? dim(GREEN, 0.6) : (steer ? GOLD : DIM);
            }
            VccMk2CockpitArt.lineT(g, px - dxw, py - dyw, px + dxw, py + dyw, col);
            g.fill(px - 2, py - 2, px + 3, py + 3, col);
            if (drive && armed && Math.abs(cmd) > 30 && Math.abs(act) < Math.abs(cmd) * 0.4) {
                g.renderOutline(px - 4, py - 4, 8, 8, RED);
            } else if (drive && armed && cmd != 0) {
                g.renderOutline(px - 4, py - 4, 8, 8, dim(col, 0.6));
            }
            if (drive && armed && cmd != 0) {
                String s = Integer.toString(cmd);
                g.drawString(font, s, px - font.width(s) / 2, py + 6, col, false);
            }
        }
        g.drawString(font, "green drive  gold steer", x + 4, y + h - 10, DIM, false);
    }

    private static double wrapDeg180(double d) {
        d = ((d % 360) + 360) % 360;
        return d > 180 ? d - 360 : d;
    }

    private String[] switchIds(VccMk2CraftProfile p) {
        if (p != null && p.isGround()) {
            return new String[]{"arm", "handbrake", "horn", "lights"};
        }
        return new String[]{"arm", "mode", "stab", "gear", "lights"};
    }

    private void drawSwitchPanel(GuiGraphics g, int[] p, VccMk2CraftProfile chosen, VccMk2BlockEntity be,
                                 boolean armed, boolean auto, boolean assisted, boolean canArm,
                                 int mouseX, int mouseY) {
        String[] ids = switchIds(chosen);
        for (int i = 0; i < ids.length; i++) {
            int[] s = cockpitSwitchRect(i);
            if (s[1] + s[3] > p[1] + p[3] - 92) {
                break;
            }
            boolean hover = inRect(mouseX, mouseY, s[0], s[1], s[2], s[3]);
            switch (ids[i]) {
                case "arm" -> {
                    VccMk2CockpitArt.drawToggle(g, font, s[0], s[1], s[2], s[3], "MASTER ARM",
                            armed, armAnim, armed ? GREEN : GOLD, "ARMED", "STANDBY", hover, true);
                    tip(mouseX, mouseY, s[0], s[1], s[2], s[3], "Master Arm",
                            armed ? "Outputs are LIVE. Click to disarm." : "Outputs are safe. Click to arm.",
                            canArm ? "Pre-flight is green." : "Finish Hangar, Bind and Mixer first.");
                }
                case "mode" -> {
                    VccMk2CockpitArt.drawToggle(g, font, s[0], s[1], s[2], s[3], "FLIGHT MODE",
                            auto, modeAnim, GREEN, "AUTO", "MANUAL", hover, armed);
                    tip(mouseX, mouseY, s[0], s[1], s[2], s[3], "Flight Mode",
                            auto ? "AUTO: autopilot drives." : "MANUAL: you fly by hand.",
                            armed ? "Click to toggle." : "Arm first.");
                }
                case "stab" -> {
                    VccMk2CockpitArt.drawToggle(g, font, s[0], s[1], s[2], s[3], "STABILIZATION",
                            assisted, stabAnim, CYAN, "ASSIST", "DIRECT", hover, true);
                    tip(mouseX, mouseY, s[0], s[1], s[2], s[3], "Stabilization",
                            assisted ? "ASSIST: gimbal self-levels." : "DIRECT: raw axes.",
                            "Full assist needs a Gimbal sensor.");
                }
                case "lights" -> {
                    boolean on = be != null && be.aux(0);
                    VccMk2CockpitArt.drawToggle(g, font, s[0], s[1], s[2], s[3], "LIGHTS",
                            on, aux0Anim, AMBER, "ON", "OFF", hover, true);
                    tip(mouseX, mouseY, s[0], s[1], s[2], s[3], "Lights",
                            "Transmits 0/15 on the lights frequency below. Put a matching link on your lamp.");
                    drawLightsLink(g, s, be, mouseX, mouseY);
                }
                case "handbrake" -> {
                    boolean on = be != null && be.handbrake();
                    VccMk2CockpitArt.drawToggle(g, font, s[0], s[1], s[2], s[3], "HANDBRAKE",
                            on, aux1Anim, RED, "SET", "OFF", hover, true);
                    tip(mouseX, mouseY, s[0], s[1], s[2], s[3], "Handbrake",
                            "Forces full brake and holds the car at a stop.");
                }
                case "horn" -> {
                    boolean on = be != null && be.aux(1);
                    VccMk2CockpitArt.drawToggle(g, font, s[0], s[1], s[2], s[3], "HORN",
                            on, aux1Anim, AMBER, "ON", "OFF", hover, true);
                    tip(mouseX, mouseY, s[0], s[1], s[2], s[3], "Horn",
                            "Aux redstone output you can wire to anything.");
                }
                case "gear" -> {
                    boolean on = be != null && be.aux(1);
                    VccMk2CockpitArt.drawToggle(g, font, s[0], s[1], s[2], s[3], "GEAR",
                            on, aux1Anim, AMBER, "DOWN", "UP", hover, true);
                    tip(mouseX, mouseY, s[0], s[1], s[2], s[3], "Landing gear",
                            "Aux redstone output for retractable gear.");
                }
                default -> {
                }
            }
        }
    }

    private boolean handleSwitchPanelClick(double mx, double my, VccMk2CraftProfile chosen, VccMk2BlockEntity be) {
        int[] p = cockpitPanel();
        String[] ids = switchIds(chosen);
        for (int i = 0; i < ids.length; i++) {
            int[] s = cockpitSwitchRect(i);
            if (s[1] + s[3] > p[1] + p[3] - 92) {
                break;
            }
            if (!inRect(mx, my, s[0], s[1], s[2], s[3])) {
                continue;
            }
            switch (ids[i]) {
                case "arm" -> send(VccMk2ActionPacket.TOGGLE_ARM, 0);
                case "mode" -> {
                    if (be != null && be.armed()) {
                        send(VccMk2ActionPacket.TOGGLE_MODE, 0);
                    }
                }
                case "stab" -> send(VccMk2ActionPacket.TOGGLE_STAB, 0);
                case "lights" -> {
                    if (handleLightsLinkClick(mx, my, s, be)) {
                        return true;
                    }
                    send(VccMk2ActionPacket.SET_AUX, (be != null && be.aux(0)) ? 0 : 1);
                }
                case "handbrake" -> send(VccMk2ActionPacket.SET_HANDBRAKE, (be != null && be.handbrake()) ? 0 : 1);
                case "horn", "gear" -> send(VccMk2ActionPacket.SET_AUX, 2 | ((be != null && be.aux(1)) ? 0 : 1));
                default -> {
                    return false;
                }
            }
            click();
            return true;
        }
        return false;
    }

    private int[] lightsSlotRect(int[] sw, int which) {
        int sz = 16;
        int y = sw[1] + sw[3] + 4;
        int totalW = sz * 2 + 4;
        int x0 = sw[0] + (sw[2] - totalW) / 2;
        return new int[]{x0 + which * (sz + 4), y, sz, sz};
    }

    private void drawLightsLink(GuiGraphics g, int[] sw, VccMk2BlockEntity be, int mouseX, int mouseY) {
        for (int which = 0; which < 2; which++) {
            int[] r = lightsSlotRect(sw, which);
            boolean hov = inRect(mouseX, mouseY, r[0], r[1], r[2], r[3]);
            ItemStack fs = be != null ? be.lightsFreqStack(which) : ItemStack.EMPTY;
            g.fill(r[0], r[1], r[0] + r[2], r[1] + r[3], 0xFF05080C);
            g.renderOutline(r[0], r[1], r[2], r[3], hov ? CYAN : GOLD_DK);
            if (!fs.isEmpty()) {
                g.renderItem(fs, r[0], r[1]);
            } else {
                g.drawString(font, "+", r[0] + 5, r[1] + 4, GREY, false);
            }
            tip(mouseX, mouseY, r[0], r[1], r[2], r[3], "Lights frequency " + (which + 1),
                    fs.isEmpty() ? "Click to pull a frequency item from your inventory." : "Click to return it.");
        }
    }

    private boolean handleLightsLinkClick(double mx, double my, int[] sw, VccMk2BlockEntity be) {
        for (int which = 0; which < 2; which++) {
            int[] r = lightsSlotRect(sw, which);
            if (!inRect(mx, my, r[0], r[1], r[2], r[3])) {
                continue;
            }
            if (be != null && !be.lightsFreqStack(which).isEmpty()) {
                send(VccMk2ActionPacket.LIGHTS_FREQ_RETURN, which);
            } else {
                int idx = firstInventoryItemIndex();
                if (idx >= 0) {
                    send(VccMk2ActionPacket.LIGHTS_FREQ_ASSIGN, which * 64 + idx);
                }
            }
            click();
            return true;
        }
        return false;
    }

    private int firstInventoryItemIndex() {
        Player pl = Minecraft.getInstance().player;
        if (pl == null) {
            return -1;
        }
        for (int i = 0; i < pl.getInventory().items.size(); i++) {
            if (!pl.getInventory().items.get(i).isEmpty()) {
                return i;
            }
        }
        return -1;
    }

    private static String[][] controlDefs(VccMk2CraftProfile p) {
        VccMk2CraftProfile q = p == null ? VccMk2CraftProfile.CUSTOM : p;
        return switch (q) {
            case PLANE -> new String[][]{{"thr", "THROTTLE", "LEVER"}, {"yoke", "YOKE", "WHEEL"},
                    {"rud", "RUDDER", "LEVER_C"}, {"flap", "FLAPS", "SHIFTER", "UP|1|2|FULL"},
                    {"gear", "GEAR", "SHIFTER", "UP|DN"}};
            case MULTIROTOR -> new String[][]{{"lev", "LEVITATION", "LEVER"}, {"cyc", "CYCLIC", "STICK"},
                    {"yaw", "YAW", "LEVER_C"}};
            case VTOL -> new String[][]{{"thr", "THROTTLE", "LEVER"}, {"lev", "LEVITATION", "LEVER_C"},
                    {"tilt", "TILT", "LEVER"}, {"yaw", "YAW", "LEVER_C"}};
            case AIRSHIP -> new String[][]{{"thr", "THROTTLE", "LEVER"}, {"lev", "LEVITATION", "LEVER_C"},
                    {"rud", "RUDDER", "WHEEL"}, {"bal", "BALLAST", "LEVER"}};
            case OTOMOBIL -> new String[][]{{"whl", "STEERING", "WHEEL"}, {"thr", "THROTTLE", "LEVER"},
                    {"brk", "BRAKE", "LEVER"}, {"gear", "GEAR", "SHIFTER", "P|R|N|D"}};
            case TANK -> new String[][]{{"lt", "LEFT TRK", "LEVER_C"}, {"rt", "RIGHT TRK", "LEVER_C"},
                    {"tur", "TURRET", "WHEEL"}};
            case BOAT -> new String[][]{{"thr", "THROTTLE", "LEVER"}, {"helm", "HELM", "WHEEL"},
                    {"rud", "RUDDER", "LEVER_C"}};
            default -> new String[][]{{"thr", "THROTTLE", "LEVER"}, {"yaw", "YAW", "WHEEL"},
                    {"vert", "VERTICAL", "LEVER_C"}};
        };
    }

    private List<Ctl> cockpitControls(VccMk2CraftProfile p) {
        int[] band = controlBandRect();
        String[][] defs = controlDefs(p);
        int n = defs.length;
        int gap = 10;
        int inner = band[2] - 16;
        int cw = (inner - gap * (n - 1)) / n;
        boolean ground = p != null && p.isGround();
        int cy = band[1] + (ground ? 54 : 16);
        int ch = band[3] - (ground ? 60 : 22);
        List<Ctl> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            int cx = band[0] + 8 + i * (cw + gap);
            CtlType type = CtlType.valueOf(defs[i][2]);
            String[] det = defs[i].length > 3 ? defs[i][3].split("\\|") : null;
            if ("steer".equals(defs[i][0])) {
                det = availableSteerDetents();
            }
            out.add(new Ctl(defs[i][0], defs[i][1], type, cx, cy, cw, ch, det));
        }
        return out;
    }

    private String[] availableSteerDetents() {
        VccMk2BlockEntity be = menu.blockEntity();
        int axles = be != null ? be.steerableAxleCount() : 0;
        return axles >= 2 ? new String[]{"FRONT", "4WS", "CRAB"} : new String[]{"FRONT"};
    }

    private void drawControlConsole(GuiGraphics g, int[] band, VccMk2CraftProfile p, int mouseX, int mouseY) {
        panel(g, band[0], band[1], band[2], band[3]);
        VccMk2BlockEntity be = menu.blockEntity();
        boolean armed = be != null && be.armed();
        boolean auto = be != null && be.autoMode();
        boolean groundAuto = armed && auto && p != null && p.isGround();
        boolean ground = p != null && p.isGround();
        String hdr = (armed && auto)
                ? (groundAuto ? "VEHICLE CONTROLS  \u00b7  AUTO self-drive"
                : "FLIGHT CONTROLS  \u00b7  AUTO cruise, you still fly levitation")
                : (ground ? "VEHICLE CONTROLS  \u00b7  drag to operate" : "FLIGHT CONTROLS  \u00b7  drag to operate");
        g.drawString(font, hdr, band[0] + 8, band[1] + 4, (armed && auto) ? GREEN : GOLD, false);
        if (ground) {
            drawVehicleSelectors(g, band, be, mouseX, mouseY);
        }
        double t = (System.currentTimeMillis() % 1000000L) / 1000.0;
        for (Ctl c : cockpitControls(p)) {
            ensureDefault(c);
            seedHeldControl(c, be);
            boolean managed = ctlAuto(p, c.id(), armed, auto);
            boolean hover = !managed && inRect(mouseX, mouseY, c.x(), c.y(), c.w(), c.h());
            int col = managed ? GREY : (c.type() == CtlType.WHEEL ? GOLD : CYAN);
            float v = managed ? autoDemo(c.id(), t) : ease(c.id());
            switch (c.type()) {
                case LEVER -> VccMk2CockpitArt.drawLever(g, font, c.x(), c.y(), c.w(), c.h(), c.label(),
                        v, false, col, hover);
                case LEVER_C -> VccMk2CockpitArt.drawLever(g, font, c.x(), c.y(), c.w(), c.h(), c.label(),
                        v, true, col, hover);
                case WHEEL -> VccMk2CockpitArt.drawWheel(g, font, c.x() + c.w() / 2, c.y() + c.h() / 2 - 2,
                        Math.min(c.w(), c.h()) / 2 - 16, c.label(), v, col, hover);
                case STICK -> VccMk2CockpitArt.drawStick(g, font, c.x(), c.y(), c.w(), c.h(), c.label(),
                        ease(c.id()), ease(c.id() + "_y"), col, hover);
                case SHIFTER -> {
                    int step;
                    if (c.id().equals("gear")) {
                        step = be != null ? be.gear() : 0;
                    } else if (c.id().equals("steer")) {
                        step = be != null ? Math.min(be.steerMode(), c.detents().length - 1) : 0;
                    } else {
                        step = Math.round(getVal(c.id()) * (c.detents().length - 1));
                    }
                    VccMk2CockpitArt.drawShifter(g, font, c.x(), c.y(), c.w(), c.h(), c.label(),
                            step, c.detents(), managed ? GREY : AMBER, hover);
                }
            }
            if (managed) {
                g.drawString(font, "AUTO", c.x() + (c.w() - font.width("AUTO")) / 2, c.y() + c.h() / 2 - 4, GREEN, false);
            }
            tip(mouseX, mouseY, c.x(), c.y(), c.w(), c.h(), c.label(),
                    managed ? "Managed by Autopilot right now." : ctlTipBody(c));
        }
    }

    private int[] vehicleSelRect(int i, int[] band) {
        int n = 5;
        int gap = 6;
        int w = (band[2] - 16 - gap * (n - 1)) / n;
        int x = band[0] + 8 + i * (w + gap);
        return new int[]{x, band[1] + 14, w, 34};
    }

    private void drawVehicleSelectors(GuiGraphics g, int[] band, VccMk2BlockEntity be, int mouseX, int mouseY) {
        String[] driveV = {"RWD", "FWD", "4x4"};
        String[] diffV = {"OPEN", "REAR", "FULL"};
        String[] steerV = {"FRONT", "4WS", "CRAB"};
        int dm = be != null ? be.driveMode() : VccMk2BlockEntity.DRIVE_4X4;
        boolean mid = be == null || be.midAxleDrive();
        int sm = be != null ? be.steerMode() : 0;
        int axles = be != null ? be.steerableAxleCount() : 0;
        int df = be != null ? be.diffMode() : 0;
        boolean inv = be != null && be.steerInvert();

        int[] r0 = vehicleSelRect(0, band);
        VccMk2CockpitArt.drawRotary(g, font, r0[0], r0[1], r0[2], r0[3], "DRIVE", Math.min(dm, 2), driveV, CYAN,
                inRect(mouseX, mouseY, r0[0], r0[1], r0[2], r0[3]), true);
        tip(mouseX, mouseY, r0[0], r0[1], r0[2], r0[3], "Drive mode", "Which axles get power: rear, front, or all wheel.");
        int[] r1 = vehicleSelRect(1, band);
        VccMk2CockpitArt.drawRotary(g, font, r1[0], r1[1], r1[2], r1[3], "MID AXLE", mid ? 0 : 1,
                new String[]{"DRIVE", "FREE"}, mid ? GREEN : DIM, inRect(mouseX, mouseY, r1[0], r1[1], r1[2], r1[3]), true);
        tip(mouseX, mouseY, r1[0], r1[1], r1[2], r1[3], "Mid axle", "Whether the middle axle is powered on a 6+ wheeler.");
        int[] r2 = vehicleSelRect(2, band);
        VccMk2CockpitArt.drawRotary(g, font, r2[0], r2[1], r2[2], r2[3], "STEER",
                axles >= 2 ? Math.min(sm, 2) : 0, axles >= 2 ? steerV : new String[]{"FRONT"},
                axles >= 2 ? GOLD : GREY, inRect(mouseX, mouseY, r2[0], r2[1], r2[2], r2[3]), axles >= 2);
        tip(mouseX, mouseY, r2[0], r2[1], r2[2], r2[3], "Steer mode",
                axles >= 2 ? "Front only, all-wheel (tighter), or crab (sideways)." : "Tag 2+ axles as Steer to unlock 4WS / Crab.");
        int[] r3 = vehicleSelRect(3, band);
        VccMk2CockpitArt.drawRotary(g, font, r3[0], r3[1], r3[2], r3[3], "DIFF", Math.min(df, 2), diffV, AMBER,
                inRect(mouseX, mouseY, r3[0], r3[1], r3[2], r3[3]), true);
        tip(mouseX, mouseY, r3[0], r3[1], r3[2], r3[3], "Differential", "Open lets the inside wheel slow on turns; lock keeps them equal.");
        int[] r4 = vehicleSelRect(4, band);
        VccMk2CockpitArt.drawRotary(g, font, r4[0], r4[1], r4[2], r4[3], "INVERT", inv ? 1 : 0,
                new String[]{"OFF", "ON"}, inv ? AMBER : DIM, inRect(mouseX, mouseY, r4[0], r4[1], r4[2], r4[3]), true);
        tip(mouseX, mouseY, r4[0], r4[1], r4[2], r4[3], "Steering invert", "Flip the steering direction.");
    }

    private void vehicleSel(GuiGraphics g, int[] r, String label, String value, int color,
                            int mouseX, int mouseY, String tipTitle, String tipBody) {
        boolean hover = inRect(mouseX, mouseY, r[0], r[1], r[2], r[3]);
        g.fill(r[0], r[1], r[0] + r[2], r[1] + r[3], hover ? CARD : 0xFF11161D);
        g.renderOutline(r[0], r[1], r[2], r[3], hover ? color : GOLD_DK);
        g.drawString(font, label, r[0] + 4, r[1] + 2, DIM, false);
        g.drawString(font, value, r[0] + 4, r[1] + 10, color, false);
        tip(mouseX, mouseY, r[0], r[1], r[2], r[3], tipTitle, tipBody);
    }

    private boolean handleVehicleSelClick(double mx, double my, int[] band, VccMk2BlockEntity be) {
        if (be == null) {
            return false;
        }
        if (inRect(mx, my, vehicleSelRect(0, band)[0], vehicleSelRect(0, band)[1], vehicleSelRect(0, band)[2], vehicleSelRect(0, band)[3])) {
            send(VccMk2ActionPacket.SET_DRIVE_MODE, (be.driveMode() + 1) % 3);
            click();
            return true;
        }
        if (inRect(mx, my, vehicleSelRect(1, band)[0], vehicleSelRect(1, band)[1], vehicleSelRect(1, band)[2], vehicleSelRect(1, band)[3])) {
            send(VccMk2ActionPacket.SET_MID_AXLE, be.midAxleDrive() ? 0 : 1);
            click();
            return true;
        }
        if (inRect(mx, my, vehicleSelRect(2, band)[0], vehicleSelRect(2, band)[1], vehicleSelRect(2, band)[2], vehicleSelRect(2, band)[3])) {
            int modes = be.steerableAxleCount() >= 2 ? 3 : 1;
            send(VccMk2ActionPacket.SET_STEER_MODE, (be.steerMode() + 1) % modes);
            click();
            return true;
        }
        if (inRect(mx, my, vehicleSelRect(3, band)[0], vehicleSelRect(3, band)[1], vehicleSelRect(3, band)[2], vehicleSelRect(3, band)[3])) {
            send(VccMk2ActionPacket.SET_DIFF, (be.diffMode() + 1) % 3);
            click();
            return true;
        }
        if (inRect(mx, my, vehicleSelRect(4, band)[0], vehicleSelRect(4, band)[1], vehicleSelRect(4, band)[2], vehicleSelRect(4, band)[3])) {
            send(VccMk2ActionPacket.SET_STEER_INVERT, be.steerInvert() ? 0 : 1);
            click();
            return true;
        }
        return false;
    }

    private static boolean ctlAuto(VccMk2CraftProfile p, String id, boolean armed, boolean auto) {
        if (!armed || !auto) {
            return false;
        }
        if (p != null && p.isGround()) {
            return true;
        }
        return id.equals("thr") || id.equals("yaw") || id.equals("rud") || id.equals("helm");
    }

    private static float autoDemo(String id, double t) {
        if (id.equals("thr")) {
            return (float) (0.55 + 0.05 * Math.sin(t * 0.7));
        }
        return (float) (0.5 + 0.12 * Math.sin(t * 0.5));
    }

    private static String ctlTipBody(Ctl c) {
        return switch (c.type()) {
            case LEVER -> "Drag up and down, 0 to 100%.";
            case LEVER_C -> "Drag up and down, center is neutral.";
            case WHEEL -> "Drag left and right to turn.";
            case STICK -> "Drag in any direction to command.";
            case SHIFTER -> "Click to cycle: " + String.join(" / ", c.detents());
        };
    }

    private void ensureDefault(Ctl c) {
        float def = switch (c.type()) {
            case LEVER, SHIFTER -> 0f;
            case LEVER_C, WHEEL, STICK -> 0.5f;
        };
        ctlVal.putIfAbsent(c.id(), def);
        if (c.type() == CtlType.STICK) {
            ctlVal.putIfAbsent(c.id() + "_y", 0.5f);
        }
    }

    private void seedHeldControl(Ctl c, VccMk2BlockEntity be) {
        if (be == null || ctlOwned.contains(c.id())) {
            return;
        }
        if (dragCtl != null && dragCtl.id().equals(c.id())) {
            return;
        }
        Integer axis = axisForControl(c.id());
        if (axis == null) {
            return;
        }
        float held = be.axisDemand(axis);
        float ui = switch (c.type()) {
            case LEVER, SHIFTER -> Math.max(0f, Math.min(1f, held));
            case LEVER_C, WHEEL, STICK -> held * 0.5f + 0.5f;
        };
        ctlVal.put(c.id(), ui);
    }

    private float ease(String id) {
        float target = ctlVal.getOrDefault(id, 0f);
        float a = ctlAnim.getOrDefault(id, target);
        a += (target - a) * 0.3f;
        ctlAnim.put(id, a);
        return a;
    }

    private float getVal(String id) {
        return ctlVal.getOrDefault(id, 0f);
    }

    private void setVal(String id, float v) {
        ctlVal.put(id, Math.max(0f, Math.min(1f, v)));
    }

    private void updateCtl(Ctl c, double mx, double my) {
        ctlOwned.add(c.id());
        switch (c.type()) {
            case LEVER, LEVER_C -> {
                int slotY = c.y() + VccMk2CockpitArt.LEVER_SLOT_TOP;
                int slotH = c.h() - VccMk2CockpitArt.LEVER_SLOT_TOP - VccMk2CockpitArt.LEVER_SLOT_BOT;
                int travel = Math.max(1, slotH - VccMk2CockpitArt.LEVER_HANDLE);
                double hy = my - VccMk2CockpitArt.LEVER_HANDLE / 2.0;
                setVal(c.id(), (float) (1 - (hy - slotY) / travel));
            }
            case WHEEL -> {
                int cx = c.x() + c.w() / 2;
                int r = Math.max(1, Math.min(c.w(), c.h()) / 2 - 16);
                setVal(c.id(), (float) ((mx - (cx - r)) / (2.0 * r)));
            }
            case STICK -> {
                int gx = c.x() + 8;
                int gy = c.y() + 16;
                int gw = Math.max(9, c.w() - 16);
                int gh = Math.max(9, c.h() - 28);
                setVal(c.id(), (float) ((mx - gx) / (gw - 8)));
                setVal(c.id() + "_y", (float) ((my - gy) / (gh - 8)));
            }
            default -> {
            }
        }
        sendAxis(c);
    }

    private void sendAxis(Ctl c) {
        Integer axis = axisForControl(c.id());
        if (axis == null) {
            return;
        }
        float v = Math.max(-1f, Math.min(1f, signedAxisValue(c)));
        int q = Math.round((v + 1f) * 1000f);
        send(VccMk2ActionPacket.SET_AXIS, (axis << 12) | (q & 0xFFF));
    }

    private static Integer axisForControl(String id) {
        return switch (id) {
            case "thr" -> VccMk2BlockEntity.AXIS_THROTTLE;
            case "whl", "yaw", "rud", "helm" -> VccMk2BlockEntity.AXIS_YAW;
            case "lev", "vert" -> VccMk2BlockEntity.AXIS_VERTICAL;
            case "brk" -> VccMk2BlockEntity.AXIS_BRAKE;
            default -> null;
        };
    }

    private float signedAxisValue(Ctl c) {
        float v = ctlVal.getOrDefault(c.id(), 0f);
        return switch (c.type()) {
            case LEVER, SHIFTER -> v;
            case LEVER_C, WHEEL, STICK -> (v - 0.5f) * 2f;
        };
    }

    private void cockpitCheck(GuiGraphics g, int x, int y, boolean ok, String label) {
        g.fill(x, y + 1, x + 6, y + 7, ok ? GREEN : GREY);
        g.drawString(font, label, x + 10, y, ok ? TXT : DIM, false);
    }

    private static String[] auxLabels(VccMk2CraftProfile p) {
        if (p == null) {
            return new String[]{"AUX 1", "AUX 2"};
        }
        return switch (p) {
            case AIRSHIP -> new String[]{"BALLAST", "LIGHTS"};
            case MULTIROTOR -> new String[]{"ALT HOLD", "LIGHTS"};
            case PLANE -> new String[]{"GEAR", "FLAPS"};
            case VTOL -> new String[]{"HOVER", "GEAR"};
            case OTOMOBIL -> new String[]{"LIGHTS", "HANDBRAKE"};
            case TANK -> new String[]{"LIGHTS", "SAFETY"};
            case BOAT -> new String[]{"ANCHOR", "LIGHTS"};
            case CUSTOM -> new String[]{"AUX 1", "AUX 2"};
        };
    }

    private static boolean[] activeAxes(VccMk2CraftProfile p) {
        if (p == null) {
            return new boolean[]{true, true, true, true, true};
        }
        return switch (p) {
            case AIRSHIP -> new boolean[]{true, true, true, false, false};
            case MULTIROTOR, VTOL -> new boolean[]{true, true, true, true, true};
            case PLANE -> new boolean[]{true, false, true, true, true};
            case OTOMOBIL, TANK, BOAT -> new boolean[]{true, false, true, false, false};
            case CUSTOM -> new boolean[]{true, true, true, true, true};
        };
    }

    private static String respondingEngines(VccMk2BlockEntity be, int axis) {
        if (be == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int row = 0; row < VccMk2BlockEntity.CARD_ROWS; row++) {
            if (be.linkedPos(VccMk2BlockEntity.propSlot(row), PropellerCardItem.TAG_KEY) != null
                    && axisRowResponds(be, row, axis)) {
                if (sb.length() > 0) {
                    sb.append(", ");
                }
                sb.append(row + 1);
            }
        }
        return sb.toString();
    }

    private boolean handleCockpitClick(double mouseX, double mouseY) {
        VccMk2BlockEntity be = menu.blockEntity();
        VccMk2CraftProfile prof = be != null && be.profileChosen() ? be.profile() : VccMk2CraftProfile.CUSTOM;
        if (handleSwitchPanelClick(mouseX, mouseY, prof, be)) {
            return true;
        }
        boolean armedNow = be != null && be.armed();
        boolean autoNow = be != null && be.autoMode();
        if (prof.isGround() && handleVehicleSelClick(mouseX, mouseY, controlBandRect(), be)) {
            return true;
        }
        for (Ctl c : cockpitControls(prof)) {
            if (inRect(mouseX, mouseY, c.x(), c.y(), c.w(), c.h())) {
                if (ctlAuto(prof, c.id(), armedNow, autoNow)) {
                    click();
                    return true;
                }
                if (c.type() == CtlType.SHIFTER) {
                    if (c.id().equals("gear")) {
                        int next = be != null ? (be.gear() + 1) % 4 : 0;
                        send(VccMk2ActionPacket.SET_GEAR, next);
                    } else if (c.id().equals("steer")) {
                        int modes = (be != null && be.steerableAxleCount() >= 2) ? 3 : 1;
                        int next = be != null ? (be.steerMode() + 1) % modes : 0;
                        send(VccMk2ActionPacket.SET_STEER_MODE, next);
                    } else {
                        int n = c.detents().length;
                        int step = (Math.round(getVal(c.id()) * (n - 1)) + 1) % n;
                        setVal(c.id(), n <= 1 ? 0f : (float) step / (n - 1));
                    }
                } else {
                    dragCtl = c;
                    updateCtl(c, mouseX, mouseY);
                }
                click();
                return true;
            }
        }
        return false;
    }

    private int[] autoLeftPanel() {
        int sx = stageX() + PAD;
        int y = stageY() + 74;
        return new int[]{sx, y, 248, stageY() + stageH() - PAD - y};
    }

    private int[] autoRightArea() {
        int[] l = autoLeftPanel();
        int x = l[0] + l[2] + 18;
        return new int[]{x, l[1], stageX() + stageW() - PAD - x, l[3]};
    }

    private int[] autoMapRect() {
        int[] r = autoRightArea();
        return new int[]{r[0], r[1], r[2], (int) (r[3] * 0.58)};
    }

    private int[] autoChipRect(int i) {
        int sx = stageX() + PAD;
        int gap = 8;
        int w = Math.min(150, (stageW() - 2 * PAD - 3 * gap) / 4);
        return new int[]{sx + i * (w + gap), stageY() + 48, w, 20};
    }

    private int[] autoRowRect(int idx) {
        int[] lp = autoLeftPanel();
        return new int[]{lp[0] + 10, lp[1] + 34 + idx * 32, lp[2] - 20, 26};
    }

    private List<String> autoSettings(VccMk2CraftProfile p) {
        List<String> s = new ArrayList<>();
        s.add("cruise");
        s.add("approach");
        s.add("announce");
        s.add("reverse");
        if (p != null && p.isGround()) {
            s.add("evade");
            s.add("pf");
        }
        s.add("center");
        return s;
    }

    private void drawAuto(GuiGraphics g, int mouseX, int mouseY) {
        VccMk2BlockEntity be = menu.blockEntity();
        VccMk2CraftProfile chosen = be != null && be.profileChosen() ? be.profile() : VccMk2CraftProfile.CUSTOM;
        boolean air = !chosen.isGround() && chosen != VccMk2CraftProfile.BOAT && chosen != VccMk2CraftProfile.CUSTOM;
        boolean engaged = autoSource != 0;
        int sx = stageX() + PAD;
        g.drawString(font, "Autopilot  \u00b7  " + chosen.label(), sx, stageY() + 12, TXT, false);
        String sub = chosen.isGround() ? "Full self-drive. Follows the route, steers and brakes for you."
                : air ? "Cruise. Holds thrust + heading. You always keep levitation."
                : "Cruise. Holds thrust + heading to the target.";
        g.drawString(font, sub, sx, stageY() + 26, engaged ? GREEN : DIM, false);

        for (int i = 0; i < 4; i++) {
            int[] r = autoChipRect(i);
            boolean avail = !(i == 3 && air);
            boolean on = autoSource == i;
            boolean hover = avail && inRect(mouseX, mouseY, r[0], r[1], r[2], r[3]);
            int col = !avail ? GREY : i == 3 ? CYAN : i == 2 ? GOLD : i == 1 ? GREEN : DIM;
            g.fill(r[0], r[1], r[0] + r[2], r[1] + r[3], on ? dim(col, 0.3) : (hover ? CARD : 0xFF11161D));
            g.renderOutline(r[0], r[1], r[2], r[3], on ? col : (avail ? GOLD_DK : 0xFF2A2F36));
            g.drawString(font, AUTO_SRC[i], r[0] + (r[2] - font.width(AUTO_SRC[i])) / 2, r[1] + 6,
                    !avail ? GREY : on ? col : TXT, false);
            tip(mouseX, mouseY, r[0], r[1], r[2], r[3], AUTO_SRC[i], autoSrcTip(i));
        }

        int[] lp = autoLeftPanel();
        panel(g, lp[0], lp[1], lp[2], lp[3]);
        g.drawString(font, "SETTINGS", lp[0] + 10, lp[1] + 10, GOLD, false);
        List<String> set = autoSettings(chosen);
        for (int idx = 0; idx < set.size(); idx++) {
            int[] r = autoRowRect(idx);
            boolean hover = inRect(mouseX, mouseY, r[0], r[1], r[2], r[3]);
            drawAutoRow(g, set.get(idx), r, hover);
            tip(mouseX, mouseY, r[0], r[1], r[2], r[3], title(set.get(idx)), autoSettingTip(set.get(idx)));
        }

        int[] mr = autoMapRect();
        double t = (System.currentTimeMillis() % 1000000L) / 1000.0;
        if (air) {
            double thrust = engaged ? 0.4 + 0.5 * autoCruise : 0;
            double yaw = engaged ? Math.sin(t * 0.5) * 0.5 : 0;
            VccMk2CockpitArt.drawThrustAssist(g, font, mr[0], mr[1], mr[2], mr[3], t, thrust, yaw, engaged);
        } else {
            VccMk2CockpitArt.drawRouteMap(g, font, mr[0], mr[1], mr[2], mr[3], t, engaged);
        }

        int[] ra = autoRightArea();
        int by = mr[1] + mr[3] + 10;
        panel(g, ra[0], by, ra[2], ra[1] + ra[3] - by);
        g.drawString(font, "STATUS", ra[0] + 10, by + 8, GOLD, false);
        double dist = engaged ? 640 - (t * 22) % 640 : 0;
        int appr = AUTO_APPROACH[autoApproach];
        double spd = engaged ? (1 + autoCruise * 31) * (0.85 + 0.15 * Math.sin(t * 1.3)) : 0;
        String phase = !engaged ? "OFF" : dist < 14 ? "ARRIVAL" : dist < appr ? "APPROACH" : "CRUISE";
        String eta = engaged && spd > 0.2 ? fmtEta(dist / spd) : "--:--";
        int syy = by + 26;
        g.drawString(font, "Phase", ra[0] + 10, syy, DIM, false);
        g.drawString(font, phase, ra[0] + 64, syy, engaged ? (phase.equals("CRUISE") ? GREEN : AMBER) : DIM, false);
        g.drawString(font, "Dist", ra[0] + 10, syy + 14, DIM, false);
        g.drawString(font, engaged ? (int) dist + " m" : "--", ra[0] + 64, syy + 14, TXT, false);
        g.drawString(font, "Speed", ra[0] + 10, syy + 28, DIM, false);
        g.drawString(font, engaged ? String.format("%.1f m/s", spd) : "--", ra[0] + 64, syy + 28, TXT, false);
        g.drawString(font, "ETA", ra[0] + 10, syy + 42, DIM, false);
        g.drawString(font, eta, ra[0] + 64, syy + 42, TXT, false);
        int chx = ra[0] + ra[2] / 2 + 6;
        String[][] chips = air
                ? new String[][]{{"Req", String.format("%.0f m/s", 1 + autoCruise * 31)},
                {"Reach", engaged ? "11 m/s" : "--"}, {"Eff", engaged ? "11 m/s" : "--"}, {"Props", engaged ? "4 / 4" : "--"}}
                : new String[][]{{"Conf", engaged ? "92%" : "--"}, {"WP", engaged ? "3 / 7" : "--"},
                {"Risk", engaged ? "Clear" : "--"}, {"Pts", engaged ? "47" : "--"}};
        for (int i = 0; i < chips.length; i++) {
            g.drawString(font, chips[i][0], chx, syy + i * 14, DIM, false);
            g.drawString(font, chips[i][1], chx + 52, syy + i * 14, engaged ? TXT : DIM, false);
        }
    }

    private void drawAutoRow(GuiGraphics g, String id, int[] r, boolean hover) {
        g.fill(r[0], r[1], r[0] + r[2], r[1] + r[3], hover ? CARD : 0xFF11161D);
        g.renderOutline(r[0], r[1], r[2], r[3], hover ? GOLD : GOLD_DK);
        String label = switch (id) {
            case "cruise" -> "CRUISE";
            case "approach" -> "APPROACH";
            case "announce" -> "ANNOUNCE";
            case "reverse" -> "REVERSE";
            case "evade" -> "EVADE";
            case "pf" -> "PF SYSTEMS";
            case "center" -> "CENTER STEER";
            default -> id;
        };
        int ly = r[1] + (r[3] - 8) / 2;
        g.drawString(font, label, r[0] + 8, ly, TXT, false);
        int vx = r[0] + 90;
        int vw = r[0] + r[2] - 6 - vx;
        switch (id) {
            case "cruise" -> {
                int sy = r[1] + r[3] / 2;
                g.fill(vx, sy - 1, vx + vw, sy + 1, GOLD_DK);
                int kx = vx + (int) (autoCruise * (vw - 6));
                g.fill(kx, r[1] + 5, kx + 6, r[1] + r[3] - 5, CYAN);
                String v = String.format("%.0f", 1 + autoCruise * 31);
                g.drawString(font, v, vx + vw - font.width(v), ly, CYAN, false);
            }
            case "approach" -> valueChip(g, r, AUTO_APPROACH[autoApproach] + " b");
            case "announce" -> valueChip(g, r, AUTO_ANNOUNCE[autoAnnounce] + " b");
            case "reverse" -> valueChip(g, r, String.format("%.1fs", AUTO_REVERSE[autoReverse]));
            case "evade" -> valueChip(g, r, AUTO_EVADE[autoEvade] + " b");
            case "pf" -> valueChip(g, r, autoPfOpen ? "OPEN" : "CLOSED");
            case "center" -> {
                String s = "TAP TO CENTER";
                g.drawString(font, s, r[0] + r[2] - 6 - font.width(s), ly, AMBER, false);
            }
            default -> {
            }
        }
    }

    private void valueChip(GuiGraphics g, int[] r, String val) {
        int w = font.width(val) + 12;
        int x = r[0] + r[2] - 6 - w;
        g.fill(x, r[1] + 4, x + w, r[1] + r[3] - 4, 0xFF161B22);
        g.renderOutline(x, r[1] + 4, w, r[3] - 8, GOLD_DK);
        g.drawString(font, val, x + 6, r[1] + (r[3] - 8) / 2, CYAN, false);
    }

    private boolean handleAutoClick(double mx, double my) {
        VccMk2BlockEntity be = menu.blockEntity();
        VccMk2CraftProfile chosen = be != null && be.profileChosen() ? be.profile() : VccMk2CraftProfile.CUSTOM;
        boolean air = !chosen.isGround() && chosen != VccMk2CraftProfile.BOAT && chosen != VccMk2CraftProfile.CUSTOM;
        for (int i = 0; i < 4; i++) {
            int[] r = autoChipRect(i);
            if (inRect(mx, my, r[0], r[1], r[2], r[3])) {
                if (i == 3 && air) {
                    return true;
                }
                autoSource = i;
                click();
                return true;
            }
        }
        List<String> set = autoSettings(chosen);
        for (int idx = 0; idx < set.size(); idx++) {
            int[] r = autoRowRect(idx);
            if (inRect(mx, my, r[0], r[1], r[2], r[3])) {
                switch (set.get(idx)) {
                    case "cruise" -> {
                        autoDragCruise = true;
                        int vx = r[0] + 90;
                        int vw = r[0] + r[2] - 6 - vx;
                        autoCruise = clampF((float) ((mx - vx) / vw));
                    }
                    case "approach" -> autoApproach = (autoApproach + 1) % AUTO_APPROACH.length;
                    case "announce" -> autoAnnounce = (autoAnnounce + 1) % AUTO_ANNOUNCE.length;
                    case "reverse" -> autoReverse = (autoReverse + 1) % AUTO_REVERSE.length;
                    case "evade" -> autoEvade = (autoEvade + 1) % AUTO_EVADE.length;
                    case "pf" -> autoPfOpen = !autoPfOpen;
                    default -> {
                    }
                }
                click();
                return true;
            }
        }
        return false;
    }

    private static float clampF(float v) {
        return v < 0 ? 0 : v > 1 ? 1 : v;
    }

    private static String fmtEta(double sec) {
        int s = (int) Math.max(0, sec);
        return String.format("%d:%02d", s / 60, s % 60);
    }

    private static String autoSrcTip(int i) {
        return switch (i) {
            case 0 -> "Autopilot off, you fly manually.";
            case 1 -> "Fallback hold if a source drops out.";
            case 2 -> "Follow a Nav Table heading or target.";
            default -> "Follow Pathfinder waypoints (ground).";
        };
    }

    private static String autoSettingTip(String id) {
        return switch (id) {
            case "cruise" -> "Target cruise speed.";
            case "approach" -> "Distance to start slowing for arrival.";
            case "announce" -> "Distance to announce a waypoint.";
            case "reverse" -> "How long reverse thrust is allowed.";
            case "evade" -> "Obstacle avoidance radius.";
            case "pf" -> "Open or close Pathfinder systems.";
            case "center" -> "Re-center the steering.";
            default -> "Autopilot setting.";
        };
    }

    private List<String[]> advChannels(VccMk2CraftProfile p) {
        boolean hovers = p == VccMk2CraftProfile.AIRSHIP || p == VccMk2CraftProfile.MULTIROTOR || p == VccMk2CraftProfile.VTOL;
        boolean surfaces = p == VccMk2CraftProfile.PLANE || p == VccMk2CraftProfile.MULTIROTOR || p == VccMk2CraftProfile.VTOL;
        boolean ground = p != null && p.isGround();
        List<String[]> r = new ArrayList<>();
        r.add(new String[]{"H", "", "MOVEMENT"});
        r.add(new String[]{"R", "throttle_up", "Throttle +"});
        r.add(new String[]{"R", "throttle_dn", "Throttle -"});
        r.add(new String[]{"R", "yaw_left", "Yaw / Steer Left"});
        r.add(new String[]{"R", "yaw_right", "Yaw / Steer Right"});
        if (hovers) {
            r.add(new String[]{"R", "lev_up", "Levitation Up"});
            r.add(new String[]{"R", "lev_dn", "Levitation Down"});
        }
        if (surfaces) {
            r.add(new String[]{"R", "pitch_up", "Pitch Up"});
            r.add(new String[]{"R", "pitch_dn", "Pitch Down"});
            r.add(new String[]{"R", "roll_left", "Roll Left"});
            r.add(new String[]{"R", "roll_right", "Roll Right"});
        }
        if (ground) {
            r.add(new String[]{"R", "brake", "Brake"});
        }
        r.add(new String[]{"H", "", "SWITCHES"});
        r.add(new String[]{"R", "arm", "Master Arm"});
        r.add(new String[]{"R", "mode", "Mode (Manual / Auto)"});
        r.add(new String[]{"R", "stab", "Stabilization"});
        String[] aux = auxLabels(p);
        r.add(new String[]{"R", "aux0", title(aux[0])});
        r.add(new String[]{"R", "aux1", title(aux[1])});
        r.add(new String[]{"H", "", "PAYLOAD"});
        r.add(new String[]{"R", "bay", "Bomb Bay"});
        r.add(new String[]{"R", "drop", "Bomb Drop"});
        return r;
    }

    private static String title(String s) {
        if (s == null || s.isEmpty()) {
            return s;
        }
        return s.charAt(0) + s.substring(1).toLowerCase();
    }

    private int[] advInvRect() {
        int h = 98;
        int y = stageY() + stageH() - PAD - h;
        return new int[]{stageX() + PAD, y, stageW() - 2 * PAD, h};
    }

    private int[] advMatrix() {
        int sx = stageX() + PAD;
        int topY = stageY() + 44;
        int w = (int) ((stageW() - 2 * PAD) * 0.60);
        int bottom = advInvRect()[1] - 8;
        return new int[]{sx, topY, w, bottom - topY};
    }

    private int[] advRightPanel() {
        int[] m = advMatrix();
        int x = m[0] + m[2] + 18;
        int w = stageX() + PAD + (stageW() - 2 * PAD) - x;
        return new int[]{x, m[1], w, m[3]};
    }

    private int[] advFreqCell(int rowX, int rowY, int rowW, int which) {
        int b = rowX + rowW - 24;
        int a = rowX + rowW - 44;
        return new int[]{which == 0 ? a : b, rowY + 1, 16, 16};
    }

    private void drawAdvanced(GuiGraphics g, int mouseX, int mouseY) {
        VccMk2BlockEntity be = menu.blockEntity();
        VccMk2CraftProfile chosen = be != null && be.profileChosen() ? be.profile() : VccMk2CraftProfile.CUSTOM;
        int sx = stageX() + PAD;
        g.drawString(font, "Advanced  \u00b7  Input Channels", sx, stageY() + 12, TXT, false);
        g.drawString(font, "Give each control a Redstone Link frequency (2 items), then pair a transmitter to the same frequency: Linked Controller, Aeronautics keyboard, or a link.",
                sx, stageY() + 26, DIM, false);

        int[] m = advMatrix();
        panel(g, m[0], m[1], m[2], m[3]);
        g.drawString(font, "CHANNELS", m[0] + 10, m[1] + 8, GOLD, false);
        g.drawString(font, "frequency", m[0] + m[2] - 78, m[1] + 8, DIM, false);
        int y = m[1] + 22;
        double t = (System.currentTimeMillis() % 1000000L) / 1000.0;
        for (String[] e : advChannels(chosen)) {
            if ("H".equals(e[0])) {
                y += 4;
                g.drawString(font, e[2], m[0] + 10, y, AMBER, false);
                g.fill(m[0] + 10, y + 10, m[0] + m[2] - 10, y + 11, GOLD_DK);
                y += 16;
            } else {
                drawChannelRow(g, be, e[1], e[2], m[0] + 10, y, m[2] - 20, t, mouseX, mouseY);
                y += 18;
            }
        }
        drawAdvActions(g, mouseX, mouseY);
        drawAdvInventory(g, mouseX, mouseY);
    }

    private void drawChannelRow(GuiGraphics g, VccMk2BlockEntity be, String id, String label,
                                int x, int y, int w, double t, int mouseX, int mouseY) {
        int rowH = 18;
        boolean hover = inRect(mouseX, mouseY, x, y, w, rowH);
        g.fill(x, y, x + w, y + rowH, hover ? CARD : 0xFF11161D);
        g.drawString(font, label, x + 6, y + 5, TXT, false);
        tip(mouseX, mouseY, x, y, 124, rowH, label, "Assign 2 frequency items, then pair a transmitter.");
        int ch = VccMk2BlockEntity.channelIndex(id);
        boolean paired = be != null && be.channelPaired(ch);
        int barX = x + 132;
        int barW = (x + w - 56) - barX;
        if (barW > 12) {
            g.fill(barX, y + 7, barX + barW, y + 11, 0xFF05080C);
            if (paired) {
                double s = 0.5 + 0.5 * Math.sin(t * 3 + ch);
                g.fill(barX, y + 7, barX + (int) (barW * (0.2 + 0.6 * s)), y + 11, GREEN);
            }
        }
        for (int wi = 0; wi < 2; wi++) {
            int[] c = advFreqCell(x, y, w, wi);
            int slot = ch * 2 + wi;
            boolean activeT = slot == activeFreqTarget;
            boolean chover = inRect(mouseX, mouseY, c[0], c[1], 16, 16);
            ItemStack fs = be != null ? be.freqStack(ch, wi) : ItemStack.EMPTY;
            g.fill(c[0], c[1], c[0] + 16, c[1] + 16, activeT ? CARD_SEL : 0xFF05080C);
            g.renderOutline(c[0], c[1], 16, 16, activeT ? GOLD : (chover ? CYAN : GOLD_DK));
            if (!fs.isEmpty()) {
                g.renderItem(fs, c[0], c[1]);
            } else {
                g.drawString(font, "+", c[0] + 5, c[1] + 4, GREY, false);
            }
        }
        int dotX = x + w - 6;
        g.fill(dotX, y + 6, dotX + 4, y + 12, paired ? GREEN : GREY);
    }

    private void drawAdvActions(GuiGraphics g, int mouseX, int mouseY) {
        int[] r = advRightPanel();
        int rx = r[0];
        int ry = r[1];
        int rw = r[2];
        int ph = 116;
        panel(g, rx, ry, rw, ph);
        g.drawString(font, "PAYLOAD  \u00b7  manual + guarded", rx + 10, ry + 8, GOLD, false);
        int x = rx + 10;
        int w = rw - 20;
        advToggleRow(g, "ARM SAFETY", bombSafety ? "ARMED" : "SAFE", bombSafety ? RED : GREEN, x, ry + 26, w, mouseX, mouseY);
        advToggleRow(g, "BOMB BAY", bayOpen ? "OPEN" : "CLOSED", bayOpen ? GREEN : DIM, x, ry + 48, w, mouseX, mouseY);
        tip(mouseX, mouseY, x, ry + 26, w, 18, "Arm Safety", "Master safety for the payload.");
        tip(mouseX, mouseY, x, ry + 48, w, 18, "Bomb Bay", "Open the bay before dropping.");
        boolean allowed = bombSafety && bayOpen;
        int dy = ry + 72;
        int dh = 30;
        boolean dhover = allowed && inRect(mouseX, mouseY, x, dy, w, dh);
        long since = System.currentTimeMillis() - dropFlash;
        boolean flashing = since < 500;
        int dcol = flashing ? (since % 200 < 100 ? 0xFFFFFFFF : RED) : (allowed ? RED : GREY);
        g.fill(x, dy, x + w, dy + dh, allowed ? (dhover ? 0xFF3A1414 : 0xFF241010) : 0xFF161B22);
        g.renderOutline(x, dy, w, dh, dcol);
        String dl = flashing ? "BOMB AWAY" : "DROP";
        g.drawString(font, dl, x + (w - font.width(dl)) / 2, dy + 7, dcol, false);
        if (!allowed) {
            String reason = !bombSafety ? "safety on" : "bay closed";
            g.drawString(font, reason, x + (w - font.width(reason)) / 2, dy + 18, AMBER, false);
        }
        tip(mouseX, mouseY, x, dy, w, dh, "Bomb Drop",
                allowed ? "Armed and open, click to drop." : "Arm safety and open the bay first.");

        int ly = ry + ph + 12;
        int lh = advInvRect()[1] - 8 - ly;
        if (lh > 36) {
            panel(g, rx, ly, rw, lh);
            g.drawString(font, "HOW PAIRING WORKS", rx + 10, ly + 8, GOLD, false);
            String[] leg = {
                    "1. Click a freq slot, then click an item",
                    "   below to set that half of the frequency.",
                    "2. Put the same 2 items on a transmitter",
                    "   (Linked Controller / keyboard / link).",
                    "3. The VCC receives that channel's signal.",
                    "Both slots filled = paired (green dot)."
            };
            int lyy = ly + 24;
            for (String s : leg) {
                g.drawString(font, s, rx + 10, lyy, DIM, false);
                lyy += 12;
            }
        }
    }

    private void advToggleRow(GuiGraphics g, String label, String state, int stateCol,
                              int x, int y, int w, int mouseX, int mouseY) {
        int rowH = 18;
        boolean hover = inRect(mouseX, mouseY, x, y, w, rowH);
        g.fill(x, y, x + w, y + rowH, hover ? CARD : 0xFF11161D);
        g.drawString(font, label, x + 6, y + 5, TXT, false);
        int cwd = font.width(state) + 10;
        int cx2 = x + w - cwd - 4;
        g.fill(cx2, y + 2, cx2 + cwd, y + rowH - 2, dim(stateCol, 0.25));
        g.renderOutline(cx2, y + 2, cwd, rowH - 4, stateCol);
        g.drawString(font, state, cx2 + 5, y + 5, stateCol, false);
    }

    private void drawAdvInventory(GuiGraphics g, int mouseX, int mouseY) {
        int[] r = advInvRect();
        panel(g, r[0], r[1], r[2], r[3]);
        boolean picking = activeFreqTarget >= 0;
        g.drawString(font, picking ? "PICK A FREQUENCY ITEM  \u00b7  click one"
                : "YOUR ITEMS  \u00b7  click a freq slot first", r[0] + 10, r[1] + 5, picking ? GOLD : DIM, false);
        Player p = Minecraft.getInstance().player;
        if (p == null) {
            return;
        }
        int startX = r[0] + 12;
        int cell = 18;
        int mainY = r[1] + 18;
        int hotY = mainY + 56;
        for (int i = 0; i < 36; i++) {
            int cx;
            int cy;
            if (i < 9) {
                cx = startX + i * cell;
                cy = hotY;
            } else {
                int j = i - 9;
                cx = startX + (j % 9) * cell;
                cy = mainY + (j / 9) * cell;
            }
            ItemStack s = p.getInventory().items.get(i);
            boolean hov = inRect(mouseX, mouseY, cx, cy, 16, 16);
            g.fill(cx, cy, cx + 16, cy + 16, hov && picking ? CARD_SEL : 0xFF05080C);
            g.renderOutline(cx, cy, 16, 16, hov && picking ? GOLD : GOLD_DK);
            if (!s.isEmpty()) {
                g.renderItem(s, cx, cy);
                g.renderItemDecorations(font, s, cx, cy);
            }
        }
    }

    private boolean handleAdvancedClick(double mx, double my) {
        VccMk2BlockEntity be = menu.blockEntity();
        VccMk2CraftProfile chosen = be != null && be.profileChosen() ? be.profile() : VccMk2CraftProfile.CUSTOM;
        int[] m = advMatrix();
        int rowX = m[0] + 10;
        int rowW = m[2] - 20;
        int y = m[1] + 22;
        for (String[] e : advChannels(chosen)) {
            if ("H".equals(e[0])) {
                y += 20;
                continue;
            }
            int ch = VccMk2BlockEntity.channelIndex(e[1]);
            for (int wi = 0; wi < 2; wi++) {
                int[] c = advFreqCell(rowX, y, rowW, wi);
                if (inRect(mx, my, c[0], c[1], 16, 16)) {
                    int slot = ch * 2 + wi;
                    if (be != null && !be.freqStack(ch, wi).isEmpty()) {
                        send(VccMk2ActionPacket.FREQ_RETURN, slot);
                        activeFreqTarget = -1;
                    } else {
                        activeFreqTarget = activeFreqTarget == slot ? -1 : slot;
                    }
                    click();
                    return true;
                }
            }
            y += 18;
        }
        int[] r = advRightPanel();
        int x = r[0] + 10;
        int w = r[2] - 20;
        int ry = r[1];
        if (inRect(mx, my, x, ry + 26, w, 18)) {
            bombSafety = !bombSafety;
            click();
            return true;
        }
        if (inRect(mx, my, x, ry + 48, w, 18)) {
            bayOpen = !bayOpen;
            click();
            return true;
        }
        if (inRect(mx, my, x, ry + 72, w, 30)) {
            if (bombSafety && bayOpen) {
                dropFlash = System.currentTimeMillis();
                click();
            }
            return true;
        }
        if (activeFreqTarget >= 0) {
            int[] inv = advInvRect();
            Player p = Minecraft.getInstance().player;
            if (p != null) {
                int startX = inv[0] + 12;
                int cell = 18;
                int mainY = inv[1] + 18;
                int hotY = mainY + 56;
                for (int i = 0; i < 36; i++) {
                    int cx;
                    int cy;
                    if (i < 9) {
                        cx = startX + i * cell;
                        cy = hotY;
                    } else {
                        int j = i - 9;
                        cx = startX + (j % 9) * cell;
                        cy = mainY + (j / 9) * cell;
                    }
                    if (inRect(mx, my, cx, cy, 16, 16) && !p.getInventory().items.get(i).isEmpty()) {
                        send(VccMk2ActionPacket.FREQ_ASSIGN, activeFreqTarget * 64 + i);
                        activeFreqTarget = -1;
                        click();
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private void drawPlaceholder(GuiGraphics g) {
        int cx = stageX() + stageW() / 2;
        int cy = stageY() + stageH() / 2;
        String msg = active.label + ", coming next";
        g.drawString(font, msg, cx - font.width(msg) / 2, cy - 14, GOLD, false);
        String note = "This station's controls land in the next phase.";
        g.drawString(font, note, cx - font.width(note) / 2, cy + 2, DIM, false);
    }

    private void panel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, 0xFF12171F);
        g.renderOutline(x, y, w, h, GOLD_DK);
    }

    private void button(GuiGraphics g, int x, int y, int w, int h, String label, int color, int mouseX, int mouseY) {
        boolean hover = inRect(mouseX, mouseY, x, y, w, h);
        g.fill(x, y, x + w, y + h, hover ? 0xFF20262E : 0xFF161B22);
        g.renderOutline(x, y, w, h, color);
        g.drawString(font, label, x + (w - font.width(label)) / 2, y + (h - 8) / 2, color, false);
    }

    private void badge(GuiGraphics g, int x, int y, String s, int color) {
        int w = font.width(s) + 12;
        g.fill(x, y, x + w, y + 13, 0xFF161B22);
        g.renderOutline(x, y, w, 13, color);
        g.drawString(font, s, x + 6, y + 3, color, false);
    }

    private int badgeRight(GuiGraphics g, int rightX, int y, String s, int color) {
        int w = font.width(s) + 12;
        int x = rightX - w;
        g.fill(x, y, x + w, y + 13, 0xFF161B22);
        g.renderOutline(x, y, w, 13, color);
        g.drawString(font, s, x + 6, y + 3, color, false);
        return w;
    }

    private boolean inRect(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private void tip(int mouseX, int mouseY, int x, int y, int w, int h, String title, String... body) {
        if (!inRect(mouseX, mouseY, x, y, w, h)) {
            return;
        }
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal(title).withStyle(s -> s.withColor(TextColor.fromRgb(0xF0CD78)).withBold(true)));
        for (String b : body) {
            lines.add(Component.literal(b).withStyle(s -> s.withColor(TextColor.fromRgb(0xB8C2CD))));
        }
        pendingTip = lines;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            Station[] all = Station.values();
            for (int i = 0; i < all.length; i++) {
                int y = navItemY(i);
                if (inRect(mouseX, mouseY, 8, y, NAV_W - 18, 36)) {
                    if (active != all[i]) {
                        active = all[i];
                        click();
                    }
                    return true;
                }
            }
            if (active == Station.HANGAR && handleHangarClick(mouseX, mouseY)) {
                return true;
            }
            if (active == Station.BIND && handleBindClick(mouseX, mouseY)) {
                return true;
            }
            if (active == Station.MIXER && handleMixerClick(mouseX, mouseY)) {
                return true;
            }
            if (active == Station.COCKPIT && handleCockpitClick(mouseX, mouseY)) {
                return true;
            }
            if (active == Station.AUTO && handleAutoClick(mouseX, mouseY)) {
                return true;
            }
            if (active == Station.ADVANCED && handleAdvancedClick(mouseX, mouseY)) {
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (active == Station.ADVANCED && activeFreqTarget >= 0 && keyCode == 256) {
            activeFreqTarget = -1;
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0 && active == Station.COCKPIT && dragCtl != null) {
            updateCtl(dragCtl, mouseX, mouseY);
            return true;
        }
        if (button == 0 && active == Station.AUTO && autoDragCruise) {
            int[] r = autoRowRect(0);
            autoCruise = clampF((float) ((mouseX - (r[0] + 90)) / (r[0] + r[2] - 6 - (r[0] + 90))));
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (dragCtl != null) {
            dragCtl = null;
        }
        autoDragCruise = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private boolean handleHangarClick(double mouseX, double mouseY) {
        VccMk2CraftProfile[] profiles = VccMk2CraftProfile.values();
        for (int i = 0; i < profiles.length; i++) {
            int[] r = profileCardRect(i);
            if (inRect(mouseX, mouseY, r[0], r[1], r[2], r[3])) {
                send(VccMk2ActionPacket.SET_PROFILE, i);
                click();
                return true;
            }
        }
        int rx = stageX() + PAD + (int) ((stageW() - 2 * PAD) * 0.56) + 24;
        int rw = stageX() + stageW() - PAD - rx;
        int ry = stageY() + 50;
        int by = ry + 162 + 104;
        int half = (rw - 12) / 2;
        if (inRect(mouseX, mouseY, rx, by, half, 26)) {
            send(VccMk2ActionPacket.TOGGLE_ARM, 0);
            click();
            return true;
        }
        if (inRect(mouseX, mouseY, rx + half + 12, by, half, 26)) {
            send(VccMk2ActionPacket.TOGGLE_MODE, 0);
            click();
            return true;
        }
        for (int i = 0; i < VccMk2BlockEntity.SENSOR_COUNT; i++) {
            int[] sr = hangarSensorRect(i);
            if (inRect(mouseX, mouseY, sr[0], sr[1], sr[2], sr[3])) {
                VccMk2BlockEntity sbe = menu.blockEntity();
                if (sbe != null && sbe.sensorManualPos(i) != null) {
                    send(VccMk2ActionPacket.SENSOR_CLEAR, i);
                    click();
                }
                return true;
            }
        }
        VccMk2BlockEntity be = menu.blockEntity();
        if (be != null && be.profileChosen() && !proposalDismissed) {
            int px = stageX() + PAD;
            int pw = stageW() - 2 * PAD;
            int py = stageY() + stageH() - 86;
            if (inRect(mouseX, mouseY, px + pw - 250, py + 38, 110, 24)
                    || inRect(mouseX, mouseY, px + pw - 132, py + 38, 120, 24)) {
                proposalDismissed = true;
                click();
                return true;
            }
        }
        return false;
    }

    private boolean handleBindClick(double mouseX, double mouseY) {
        for (int row = 0; row < VccMk2BlockEntity.CARD_ROWS; row++) {
            int[] tr = bindTypeRect(row);
            if (inRect(mouseX, mouseY, tr[0], tr[1], tr[2], tr[3])) {
                send(VccMk2ActionPacket.SET_ENGINE_TYPE, row);
                click();
                return true;
            }
        }
        int[] geom = bindTableGeom();
        int right = geom[0] + geom[1];
        int fy = bindFooterY();
        if (inRect(mouseX, mouseY, right - 226, fy, 108, 14)) {
            send(VccMk2ActionPacket.BIND_ALL, 0);
            click();
            return true;
        }
        if (inRect(mouseX, mouseY, right - 112, fy, 108, 14)) {
            send(VccMk2ActionPacket.CLEAR_ALL, 0);
            click();
            return true;
        }

        return false;
    }

    private boolean handleMixerClick(double mouseX, double mouseY) {
        MixView[] mv = MixView.values();
        for (int i = 0; i < 6; i++) {
            int[] r = mixViewBtnRect(i);
            if (inRect(mouseX, mouseY, r[0], r[1], r[2], r[3])) {
                mixView = mv[i];
                click();
                return true;
            }
        }
        int[] nr = mixNoseBtnRect();
        if (inRect(mouseX, mouseY, nr[0], nr[1], nr[2], nr[3])) {
            send(VccMk2ActionPacket.NOSE_ROTATE, 0);
            click();
            return true;
        }
        for (int i = 0; i < AXES.length; i++) {
            int[] r = mixAxisBtnRect(i);
            if (inRect(mouseX, mouseY, r[0], r[1], r[2], r[3])) {
                mixPreviewAxis = (mixPreviewAxis == i) ? -1 : i;
                click();
                return true;
            }
        }
        VccMk2BlockEntity be = menu.blockEntity();
        boolean haveSel = mixSelRow >= 0 && be != null
                && be.linkedPos(VccMk2BlockEntity.propSlot(mixSelRow), PropellerCardItem.TAG_KEY) != null;
        if (haveSel) {
            int[] side = mixSideRect();
            if (inRect(mouseX, mouseY, side[0] + side[2] - 58, side[1] + 8, 46, 14)) {
                mixSelRow = -1;
                click();
                return true;
            }
            VccMk2Role[] pick = VccMk2Role.ASSIGNABLE;
            for (int i = 0; i < pick.length; i++) {
                int[] r = mixRoleBtnRect(i);
                if (inRect(mouseX, mouseY, r[0], r[1], r[2], r[3])) {
                    send(VccMk2ActionPacket.SET_ROLE, (mixSelRow << 4) | pick[i].ordinal());
                    click();
                    return true;
                }
            }
        } else {
            int[] ab = mixAutoBtnRect();
            if (inRect(mouseX, mouseY, ab[0], ab[1], ab[2], ab[3])) {
                send(VccMk2ActionPacket.AUTO_ASSIGN, 0);
                click();
                return true;
            }
        }
        if (be != null) {
            int[][] pos = mixNodePositions(mixSchematicRect());
            for (int row = 0; row < VccMk2BlockEntity.CARD_ROWS; row++) {
                if (pos[row] != null && inRect(mouseX, mouseY, pos[row][0] - 9, pos[row][1] - 9, 18, 18)) {
                    mixSelRow = row;
                    click();
                    return true;
                }
            }
        }
        return false;
    }

    private void send(int action, int value) {
        PacketDistributor.sendToServer(new VccMk2ActionPacket(menu.containerId, action, value));
    }

    private void click() {
        Minecraft.getInstance().getSoundManager()
                .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
    }
}
