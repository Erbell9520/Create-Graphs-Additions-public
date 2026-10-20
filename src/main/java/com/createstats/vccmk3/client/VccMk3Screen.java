package com.createstats.vccmk3.client;

import com.createstats.vccmk3.VccMk3BlockEntity;
import com.createstats.vccmk3.VccMk3Menu;
import com.createstats.vccmk3.avionics.AvionicsChannel;
import com.createstats.vccmk3.avionics.SensorDiscovery;
import com.createstats.vccmk3.hardware.DeviceCategory;
import com.createstats.vccmk3.hardware.LinkHealth;
import com.createstats.vccmk3.model.Airframe;
import com.createstats.vccmk3.model.ControlAxis;
import com.createstats.vccmk3.model.SlotSpec;
import com.createstats.vccmk3.model.WidgetType;
import com.createstats.vccmk3.network.VccMk3ActionPacket;
import com.createstats.vccmk3.network.VccMk3SnapshotPacket;
import com.createstats.vccmk3.alert.AlertLevel;
import com.createstats.client.hud.HudAnim;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.BlockPos;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Locale;
import java.util.UUID;

public class VccMk3Screen extends AbstractContainerScreen<VccMk3Menu> {
    private static final int W = 1280;
    private static final int H = 800;

    private static final int HEADER_H = 36;
    private static final int TABS_H = 22;
    private static final int BODY_TOP = HEADER_H + TABS_H;
    private static final int STATUS_H = 26;
    private static final int PAD = 8;

    private static final int LEFT_W = 248;
    private static final int LEFT_W_MIN = 168;
    private static final int RIGHT_W = 288;
    private static final int RIGHT_W_MIN = 206;

    private static final int RACK_COL_W = 396;

    private static final int PANEL = 0xFF141A22;
    private static final int PANEL_RAISED = 0xFF1D2530;
    private static final int PANEL_SUNK = 0xFF0E131A;
    private static final int PANEL_LIVE = 0xFF121A22;
    private static final int EDGE = 0xFF3D4C5C;
    private static final int EDGE_BRIGHT = 0xFF6E8398;
    private static final int TEXT = 0xFFE4EEF8;
    private static final int TEXT_DIM = 0xFF8CA1B4;
    private static final int TEXT_FAINT = 0xFF53677A;
    private static final int BRASS = 0xFFD4AF37;
    private static final int BRASS_DARK = 0xFF785C26;
    private static final int CYAN = 0xFF62D5FF;
    private static final int GOOD = 0xFF6EE7A8;
    private static final int WARN = 0xFFFFB454;
    private static final int BAD = 0xFFFF6B6B;
    private static final int HORIZON_SKY = 0xFF122E3A;
    private static final int HORIZON_GROUND = 0xFF3A281A;

    private enum Page {
        RIG("RIG", "Fit your hardware onto the craft"),
        FLY("FLY", "Fly it"),
        HARDWARE("HARDWARE", "Cards, links and instruments"),
        SYSTEMS("SYSTEMS", "What the craft is actually doing");

        private final String label;
        private final String hint;

        Page(String label, String hint) {
            this.label = label;
            this.hint = hint;
        }
    }

    private record Hit(int x, int y, int w, int h, Runnable action, @Nullable String tooltip) {
        boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    private record Drag(ControlAxis axis, WidgetType widget, int x, int y, int w, int h) {}

    private enum HardwareFilter {
        ALL("ALL"), SPARE("SPARE"), FAULT("FAULT");

        private final String label;

        HardwareFilter(String label) {
            this.label = label;
        }
    }

    private Page page = Page.RIG;
    private final List<Hit> hits = new ArrayList<>();
    @Nullable private Drag drag;
    @Nullable private UUID selectedDevice;

    @Nullable private String selectedSlot;
    private HardwareFilter hardwareFilter = HardwareFilter.ALL;

    @Nullable private Map<String, RigShapes.SlotShape> rigSlotShapes;
    private boolean alertsExpanded;

    private int frame;

    private long lastDetentMs;
    private int lastDetentStep = Integer.MIN_VALUE;

    private final Set<ControlAxis> heldAxes = new HashSet<>();
    private final Map<ControlAxis, Double> keyboardValues = new HashMap<>();

    private int bootTicks;
    private static final int BOOT_TICKS = 14;

    @Nullable private EditBox nameField;

    private final Map<ControlAxis, float[]> widgetDisplay = new HashMap<>();

    private long lastFrameNanos;

    private float animClock;

    private final Map<UUID, Float> rotorAngle = new HashMap<>();

    // client only, a sweep never reaches the hardware
    @Nullable private ControlAxis testAxis;

    private float testClock;

    private int rigDeviceScroll;

    private int ledgerScroll;

    @Nullable private int[] rigDeviceBounds;
    @Nullable private int[] ledgerBounds;

    private int rightInstrH = 138;
    private int rightSafetyH = 128;
    private int rightRemoteY;
    private int rightRemoteH;

    public VccMk3Screen(VccMk3Menu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = W;
        this.imageHeight = H;
    }

    public net.minecraft.client.renderer.Rect2i consoleBounds() {
        return new net.minecraft.client.renderer.Rect2i(0, 0, Math.max(1, this.width), Math.max(1, this.height));
    }

    private int canvasW() {
        return Math.max(640, Math.min(W, this.width));
    }

    private int canvasH() {
        return Math.max(400, Math.min(H, this.height));
    }

    private int leftCol() {
        return Math.max(LEFT_W_MIN, Math.min(LEFT_W, canvasW() * 21 / 100));
    }

    private int rightCol() {
        return Math.max(RIGHT_W_MIN, Math.min(RIGHT_W, canvasW() * 24 / 100));
    }

    private boolean hardwareNarrow() {
        return canvasW() < RACK_COL_W + PAD * 3 + 240 + RIGHT_W_MIN;
    }

    private int bodyTop() {
        return topPos + BODY_TOP;
    }

    private int bodyHeight() {
        return canvasH() - BODY_TOP - STATUS_H;
    }

    private void placeCanvas() {
        this.leftPos = (this.width - canvasW()) / 2;
        this.topPos = (this.height - canvasH()) / 2;
    }

    @Override
    protected void init() {
        super.init();
        placeCanvas();

        this.titleLabelX = -10_000;
        this.inventoryLabelX = -10_000;
    }

    private void beginRename(String current) {
        if (nameField != null) removeWidget(nameField);
        nameField = new EditBox(font, leftPos + 12, topPos + 6, 240, 14, Component.empty());
        nameField.setBordered(false);
        nameField.setMaxLength(32);
        nameField.setTextColor(BRASS);
        nameField.setValue(current);
        nameField.setResponder(value -> {});
        addRenderableWidget(nameField);
        setFocused(nameField);
        nameField.setFocused(true);
        click(1.3f);
    }

    private void commitRename() {
        if (nameField == null) return;
        send(VccMk3ActionPacket.Action.SET_CRAFT_NAME, null, 0, nameField.getValue().trim());
        removeWidget(nameField);
        nameField = null;
        click(0.9f);
    }

    private void cancelRename() {
        if (nameField == null) return;
        removeWidget(nameField);
        nameField = null;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderHitTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.drawManaged(() -> renderConsole(graphics, mouseX, mouseY));
    }

    private void renderConsole(GuiGraphics graphics, int mouseX, int mouseY) {
        placeCanvas();
        hits.clear();
        pendingAlertList = null;
        alertListBounds = null;
        rigDeviceBounds = null;
        ledgerBounds = null;

        pendingDrag = null;
        advanceFrameClock();
        int x = leftPos;
        int y = topPos;
        VccMk3SnapshotPacket snapshot = VccMk3ClientState.snapshot();

        graphics.fill(x, y, x + canvasW(), y + canvasH(), PANEL);
        ConsoleDraw.outline(graphics, x, y, canvasW(), canvasH(), EDGE);

        renderHeader(graphics, x, y, snapshot, mouseX, mouseY);
        renderTabs(graphics, x, y + HEADER_H, mouseX, mouseY);

        menu.setSlotsVisible(page == Page.HARDWARE && snapshot != null);

        switch (page) {
            case RIG -> renderRig(graphics, x, y, snapshot, mouseX, mouseY);
            case FLY -> renderFly(graphics, x, y, snapshot, mouseX, mouseY);
            case HARDWARE -> renderHardware(graphics, x, y, snapshot, mouseX, mouseY);
            case SYSTEMS -> renderSystems(graphics, x, y, snapshot, mouseX, mouseY);
        }

        if (pendingAlertList != null && snapshot != null) {
            renderAlertList(graphics, pendingAlertList[0], pendingAlertList[1], pendingAlertList[2], snapshot);
            pendingAlertList = null;
        }

        if (nameField != null) {
            int fx = nameField.getX() - 3;
            int fy = nameField.getY() - 3;
            int fw = nameField.getWidth() + 6;
            graphics.fill(fx, fy, fx + fw, fy + 18, 0xFF0A0E13);
            ConsoleDraw.outline(graphics, fx, fy, fw, 18, BRASS);
            String help = "enter to save, esc to cancel";
            graphics.drawString(font, help, fx, fy + 20, TEXT_FAINT, false);
        }

        renderBoot(graphics, x, y);
    }

    private void renderHeader(GuiGraphics graphics, int x, int y,
                              @Nullable VccMk3SnapshotPacket snapshot, int mouseX, int mouseY) {
        int w = canvasW();
        graphics.fill(x + 1, y + 1, x + w - 1, y + HEADER_H - 1, PANEL_RAISED);
        graphics.fill(x + 1, y + HEADER_H - 1, x + w - 1, y + HEADER_H, EDGE);

        String name = snapshot != null && !snapshot.craftName().isEmpty()
                ? snapshot.craftName() : "VCC MK III";
        if (nameField == null) {
            graphics.pose().pushPose();
            graphics.pose().translate(x + 10, y + 5, 0);
            graphics.pose().scale(2f, 2f, 1f);
            graphics.drawString(font, name, 0, 0, BRASS, false);
            graphics.pose().popPose();
            String currentName = snapshot != null ? snapshot.craftName() : "";
            hit(x + 8, y + 4, Math.max(80, font.width(name) * 2 + 8), 16,
                    () -> beginRename(currentName),
                    "Click to name this craft. The name shows on the console and in the block's tooltip.");
        }

        graphics.drawString(font, subtitle(snapshot), x + 12, y + 24, TEXT_FAINT, false);

        boolean pips = w >= 900;
        if (pips) renderSensorPips(graphics, x + 330, y + 12, snapshot);

        boolean armed = snapshot != null && snapshot.armed();
        String word = armed ? "ARMED" : "SAFE";
        int chipW = font.width(word) + 16;
        int chipX = x + w - chipW - 10;
        graphics.fill(chipX, y + 11, chipX + chipW, y + 27, armed ? 0xFF401A1C : 0xFF142820);
        ConsoleDraw.outline(graphics, chipX, y + 11, chipW, 16, armed ? BAD : GOOD);
        graphics.drawString(font, word, chipX + 8, y + 15, armed ? BAD : GOOD, false);

        boolean pinned = snapshot != null && VccMk3HudState.isPinned(snapshot.controllerPos());
        String pinWord = "HUD";
        int pinW = font.width(pinWord) + 14;
        int pinX = chipX - pinW - 6;
        graphics.fill(pinX, y + 11, pinX + pinW, y + 27, pinned ? 0xFF10303C : PANEL_SUNK);
        ConsoleDraw.outline(graphics, pinX, y + 11, pinW, 16, pinned ? CYAN : EDGE);
        graphics.drawString(font, pinWord, pinX + 7, y + 15, pinned ? CYAN : TEXT_FAINT, false);
        if (snapshot != null) {
            BlockPos controller = snapshot.controllerPos();
            hit(pinX, y + 11, pinW, 16, () -> {
                boolean next = !VccMk3HudState.isPinned(controller);
                VccMk3HudState.setPinned(next ? controller : null);
                send(VccMk3ActionPacket.Action.SET_HUD_PIN, null, next ? 1.0 : 0.0, "");
                click(next ? 1.3f : 0.9f);
            }, pinned
                    ? "Flight HUD is pinned. Click to remove it from your screen."
                    : "Pin a compact flight HUD to your screen, so you keep the instruments after "
                            + "closing this console.");
        }

        boolean nowArmed = armed;
        hit(chipX, y + 11, chipW, 16,
                () -> {
                    send(VccMk3ActionPacket.Action.SET_ARMED, null, nowArmed ? 0.0 : 1.0, "");
                    armSound(!nowArmed);
                },
                nowArmed ? "Disarm. Every governor goes to zero immediately."
                         : "Arm. Your controls start commanding hardware.");

        int alertX = x + (pips ? 528 : 330);
        renderAnnunciator(graphics, alertX, y + 10, pinX - 12 - alertX, snapshot);
    }

    private String subtitle(@Nullable VccMk3SnapshotPacket snapshot) {
        if (snapshot == null) return "NO SIGNAL";
        Airframe frame = Airframe.byOrdinal(snapshot.airframeOrdinal());
        int bound = snapshot.devices().size();
        int fitted = snapshot.assignments().size();
        int spare = Math.max(0, bound - fitted);
        StringBuilder text = new StringBuilder(frame.label().toUpperCase(Locale.ROOT));
        text.append("   ").append(bound).append(bound == 1 ? " device bound" : " devices bound");
        if (spare > 0) text.append("   ").append(spare).append(" spare");
        return text.toString();
    }

    private void renderSensorPips(GuiGraphics graphics, int x, int y, @Nullable VccMk3SnapshotPacket snapshot) {
        SensorDiscovery.Kind[] kinds = SensorDiscovery.Kind.values();
        for (int i = 0; i < kinds.length; i++) {
            SensorDiscovery.Kind kind = kinds[i];
            byte state = snapshot == null ? VccMk3SnapshotPacket.SENSOR_NONE
                    : snapshot.sensorStates()[Math.min(i, snapshot.sensorStates().length - 1)];
            boolean live = state == VccMk3SnapshotPacket.SENSOR_AUTO || state == VccMk3SnapshotPacket.SENSOR_MANUAL;
            int colour = state == VccMk3SnapshotPacket.SENSOR_BROKEN ? BAD : (live ? GOOD : TEXT_FAINT);

            int px = x + i * 30;
            graphics.fill(px, y, px + 11, y + 11, PANEL_SUNK);
            ConsoleDraw.outline(graphics, px, y, 11, 11, colour);
            if (live) {
                graphics.fill(px + 3, y + 3, px + 8, y + 8, colour);
            } else {
                ConsoleDraw.outline(graphics, px + 3, y + 3, 5, 5, colour);
            }
            graphics.drawString(font, kind.label().substring(0, 1), px + 14, y + 2, colour, false);

            String detail = switch (state) {
                case VccMk3SnapshotPacket.SENSOR_AUTO -> kind.label() + " found automatically. Providing " + kind.provides() + ".";
                case VccMk3SnapshotPacket.SENSOR_MANUAL -> kind.label() + " pinned by hand. Providing " + kind.provides() + ".";
                case VccMk3SnapshotPacket.SENSOR_BROKEN -> kind.label() + " was pinned by hand but is no longer there.";
                default -> "No " + kind.label() + ". " + capitalise(kind.provides()) + " is estimated from physics.";
            };
            hit(px, y, 26, 11, null, detail);
        }
    }

    private void renderTabs(GuiGraphics graphics, int x, int y, int mouseX, int mouseY) {
        int w = canvasW();
        graphics.fill(x + 1, y, x + w - 1, y + TABS_H, PANEL);
        graphics.fill(x + 1, y + TABS_H - 1, x + w - 1, y + TABS_H, EDGE);

        int tabX = x + PAD;
        for (Page candidate : Page.values()) {
            int tw = font.width(candidate.label) + 26;
            boolean active = candidate == page;
            boolean hover = mouseX >= tabX && mouseX < tabX + tw && mouseY >= y + 3 && mouseY < y + TABS_H - 1;
            graphics.fill(tabX, y + 3, tabX + tw, y + TABS_H - 1, active ? PANEL_SUNK : PANEL_RAISED);
            ConsoleDraw.outline(graphics, tabX, y + 3, tw, TABS_H - 4,
                    active ? EDGE_BRIGHT : (hover ? EDGE_BRIGHT : EDGE));

            if (active) graphics.fill(tabX + 1, y + 3, tabX + tw - 1, y + 5, CYAN);
            graphics.drawString(font, candidate.label, tabX + 13, y + 9,
                    active ? TEXT : (hover ? TEXT_DIM : TEXT_FAINT), false);
            Page target = candidate;
            hit(tabX, y + 3, tw, TABS_H - 4, () -> {
                page = target;
                click(1.1f);
            }, candidate.hint);
            tabX += tw + 4;
        }
    }

    private void renderStatus(GuiGraphics graphics, int ox, int oy, String message, int colour,
                              String hints) {
        int y = oy + canvasH() - STATUS_H;
        int w = canvasW();
        graphics.fill(ox + 1, y, ox + w - 1, oy + canvasH() - 1, PANEL_RAISED);
        graphics.fill(ox + 1, y, ox + w - 1, y + 1, EDGE);
        graphics.fill(ox + PAD, y + 6, ox + PAD + 3, y + STATUS_H - 6, colour);
        int hintW = hints.isEmpty() ? 0 : font.width(hints) + 24;
        graphics.drawString(font, trim(message, w - PAD * 2 - 12 - hintW), ox + PAD + 10, y + 9,
                colour, false);
        if (!hints.isEmpty()) {
            graphics.drawString(font, hints, ox + w - PAD - font.width(hints), y + 9, TEXT_FAINT, false);
        }
    }

    private void renderAnnunciator(GuiGraphics graphics, int x, int y, int w,
                                   @Nullable VccMk3SnapshotPacket snapshot) {
        if (w < 60) return;
        VccMk3SnapshotPacket.AlertEntry top = snapshot == null
                ? new VccMk3SnapshotPacket.AlertEntry((byte) AlertLevel.CAUTION.ordinal(), "LINK", "No console data")
                : snapshot.topAlert();
        AlertLevel level = AlertLevel.byOrdinal(top.level());
        int colour = level.color();

        if (level.shouldPulse()) {
            float phase = (float) ((Math.sin(frame * 0.18) + 1.0) * 0.5);
            colour = ConsoleDraw.blend(level.color(), PANEL_SUNK, 0.45f * (1f - phase));
        }

        graphics.fill(x, y, x + w, y + 16, PANEL_SUNK);
        ConsoleDraw.outline(graphics, x, y, w, 16, colour);
        graphics.fill(x + 1, y + 1, x + 5, y + 15, colour);
        graphics.drawString(font, top.code(), x + 10, y + 4, colour, false);

        int messageX = x + 10 + font.width(top.code()) + 8;
        int extra = snapshot == null ? 0 : Math.max(0, snapshot.alerts().size() - 1);
        int messageRoom = w - (messageX - x) - (extra > 0 ? 34 : 12);
        graphics.drawString(font, trim(top.message(), messageRoom), messageX, y + 4,
                level == AlertLevel.OK ? TEXT_FAINT : TEXT, false);

        if (extra > 0) {
            String tag = "+" + extra;
            graphics.drawString(font, tag, x + w - font.width(tag) - 8, y + 4, colour, false);
        }
        hit(x, y, w, 16, () -> {
            alertsExpanded = !alertsExpanded;
            click(1.4f);
        }, extra > 0 ? "Click to see all " + (extra + 1) + " active conditions." : top.message());

        if (alertsExpanded && snapshot != null && !snapshot.alerts().isEmpty()) {
            pendingAlertList = new int[] { x, y + 18, w };
        }
    }

    @Nullable private Drag pendingDrag;

    @Nullable private int[] pendingAlertList;

    @Nullable private int[] alertListBounds;

    private void renderAlertList(GuiGraphics graphics, int x, int y, int w,
                                 VccMk3SnapshotPacket snapshot) {
        int rowH = 20;
        int h = 6 + snapshot.alerts().size() * rowH;
        alertListBounds = new int[] { x, y, w, h };
        graphics.fill(x, y, x + w, y + h, 0xF0080C11);
        ConsoleDraw.outline(graphics, x, y, w, h, EDGE_BRIGHT);
        int row = y + 4;
        for (VccMk3SnapshotPacket.AlertEntry alert : snapshot.alerts()) {
            AlertLevel level = AlertLevel.byOrdinal(alert.level());
            graphics.fill(x + 3, row, x + 6, row + 16, level.color());
            graphics.drawString(font, alert.code(), x + 11, row + 1, level.color(), false);
            graphics.drawString(font, trim(alert.message(), w - 60), x + 50, row + 1, TEXT_DIM, false);
            row += rowH;
        }

        hit(x, y, w, h, () -> {
            alertsExpanded = false;
            click(1.0f);
        }, null);
    }

    // derived cockpit, it can't drift out of step with the hardware
    private void renderFly(GuiGraphics graphics, int ox, int oy,
                           @Nullable VccMk3SnapshotPacket snapshot, int mouseX, int mouseY) {
        int bodyY = bodyTop();
        int bodyH = bodyHeight();
        int w = canvasW();

        int cockpitW = Math.max(232, Math.min(348, w * 27 / 100));
        panel(graphics, ox + PAD, bodyY + PAD, cockpitW - PAD, bodyH - PAD * 2, "COCKPIT", CYAN);

        int rightColW = rightCol();
        int rightX = ox + w - rightColW + PAD;
        int rightW = rightColW - PAD * 2;
        int atmosH = Math.min(232, (bodyH - PAD * 3) / 2);
        panel(graphics, rightX, bodyY + PAD, rightW, atmosH, "ATMOSPHERE", GOOD);
        panel(graphics, rightX, bodyY + PAD + atmosH + PAD, rightW,
                bodyH - atmosH - PAD * 3, "AUTOPILOT", BRASS);

        int pfdX = ox + cockpitW + PAD;
        int pfdW = (ox + w - rightColW) - pfdX - PAD;
        int dataH = 132;
        int pfdH = bodyH - PAD * 2 - dataH - PAD;
        panel(graphics, pfdX, bodyY + PAD, pfdW, pfdH, "PRIMARY FLIGHT DISPLAY", CYAN);
        panel(graphics, pfdX, bodyY + PAD + pfdH + PAD, pfdW, dataH, "FLIGHT DATA");

        if (snapshot == null) {
            graphics.drawString(font, "Waiting for the controller.", ox + PAD + 12, bodyY + 40,
                    TEXT_FAINT, false);
            renderStatus(graphics, ox, oy, "No signal from the controller.", WARN, "");
            return;
        }

        renderCockpit(graphics, ox + PAD, bodyY + PAD, cockpitW - PAD, bodyH - PAD * 2,
                snapshot, mouseX, mouseY);
        renderPfd(graphics, pfdX, bodyY + PAD, pfdW, pfdH, snapshot);
        renderFlightData(graphics, pfdX, bodyY + PAD + pfdH + PAD, pfdW, dataH, snapshot);
        renderAtmosphere(graphics, rightX, bodyY + PAD, rightW, snapshot);
        renderAutoPilot(graphics, rightX, bodyY + PAD + atmosH + PAD, rightW,
                bodyH - atmosH - PAD * 3, snapshot);

        renderFlyStatus(graphics, ox, oy, snapshot);
    }

    private static final ControlAxis[] COCKPIT_ORDER = {
            ControlAxis.FORWARD, ControlAxis.UP, ControlAxis.YAW,
            ControlAxis.PITCH, ControlAxis.ROLL, ControlAxis.AUX
    };

    private void renderCockpit(GuiGraphics graphics, int x, int y, int w, int h,
                               VccMk3SnapshotPacket snapshot, int mouseX, int mouseY) {
        Airframe frame = Airframe.byOrdinal(snapshot.airframeOrdinal());

        List<ControlAxis> shown = new ArrayList<>();
        for (ControlAxis axis : COCKPIT_ORDER) {
            if (!hasSlotFor(frame, axis)) continue;
            if (axis == ControlAxis.ROLL && hasSlotFor(frame, ControlAxis.PITCH)) continue;
            shown.add(axis);
        }
        if (shown.isEmpty()) {
            graphics.drawString(font, "This airframe has no controls.", x + 10, y + 28, TEXT_DIM, false);
            return;
        }

        boolean flyable = false;
        for (ControlAxis axis : shown) {
            if (snapshot.axisUsable(axis)) {
                flyable = true;
                break;
            }
        }
        if (flyable) {
            String hint = heldAxes.isEmpty() ? "WASD, space, shift" : "flying by keyboard";
            graphics.drawString(font, hint, x + w - font.width(hint) - 8, y + 5,
                    heldAxes.isEmpty() ? TEXT_FAINT : CYAN, false);
            hit(x + w - font.width(hint) - 10, y + 4, font.width(hint) + 6, 10, null,
                    "W and S for forward, A and D for yaw, space and shift for lift, "
                            + "arrow keys for pitch and roll. Hold a key; releasing it centres the axis.");
        }

        int top = y + 20;
        int available = h - 26;
        int tileH = Math.max(74, Math.min(150, (available - (shown.size() - 1) * 4) / shown.size()));
        int rowY = top;
        for (ControlAxis axis : shown) {
            renderAxisTile(graphics, x + 6, rowY, w - 12, tileH, axis, snapshot, mouseX, mouseY);
            rowY += tileH + 4;
        }
    }

    private void renderAxisTile(GuiGraphics graphics, int x, int y, int w, int h,
                                ControlAxis axis, VccMk3SnapshotPacket snapshot,
                                int mouseX, int mouseY) {
        boolean pair = axis == ControlAxis.PITCH
                && hasSlotFor(Airframe.byOrdinal(snapshot.airframeOrdinal()), ControlAxis.ROLL);
        float authority = snapshot.authority(axis);
        boolean absent = Float.isNaN(authority);
        boolean usable = !absent && authority > 0f;
        double demand = smoothed(axis, snapshot.demand(axis));
        double second = pair ? smoothed(ControlAxis.ROLL, snapshot.demand(ControlAxis.ROLL)) : 0.0;
        boolean live = usable && (Math.abs(demand) > 1.0e-3 || Math.abs(second) > 1.0e-3);
        int colour = usable ? axis.color() : EDGE;

        graphics.fill(x, y, x + w, y + h, live ? PANEL_LIVE : PANEL_SUNK);
        ConsoleDraw.outline(graphics, x, y, w, h, live ? colour : EDGE);

        String title = pair ? "PITCH / ROLL" : axis.label().toUpperCase(Locale.ROOT);
        graphics.drawString(font, title, x + 7, y + 5, usable ? TEXT : TEXT_FAINT, false);

        int barX = x + w - 78;
        if (absent) {
            graphics.fill(barX, y + 5, x + w - 8, y + 12, 0xFF0A0E13);
            for (int dx = barX + 2; dx < x + w - 10; dx += 6) {
                graphics.fill(dx, y + 8, dx + 3, y + 9, 0xFF243040);
            }
        } else {
            ConsoleDraw.bar(graphics, barX, y + 5, w - 78 - 8 + (x + w - 8 - barX) - (w - 78 - 8), 7,
                    authority, 0xFF0A0E13, EDGE, usable ? axis.color() : BAD);
        }

        if (!usable) {
            String reason = absent
                    ? snapshot.axisReport(axis).note()
                    : (snapshot.axisReport(axis).note().isEmpty()
                            ? "nothing fitted to this axis yet" : snapshot.axisReport(axis).note());
            drawWrapped(graphics, capitalise(reason) + ".", x + 7, y + 20, w - 14, 2, TEXT_FAINT);
            String go = absent ? "" : "Fit hardware on RIG";
            if (!go.isEmpty() && h > 52) {
                graphics.drawString(font, go, x + 7, y + h - 13, WARN, false);
                hit(x, y, w, h, () -> {
                    page = Page.RIG;
                    selectedSlot = firstSlotFor(Airframe.byOrdinal(snapshot.airframeOrdinal()), axis);
                    click(1.1f);
                }, "Nothing on this axis. Click to open RIG and fit something.");
            }
            return;
        }

        WidgetType widget = WidgetType.defaultFor(axis);
        int gy = y + 20;
        int gh = h - 34;
        switch (widget) {
            case LEVER -> drawLever(graphics, x + 7, gy, w - 14, gh, (demand + 1.0) / 2.0);
            case WHEEL -> drawWheel(graphics, x + 7, gy, w - 14, gh, demand, axis.color());
            case STICK -> drawStick(graphics, x + 7, gy, w - 14, gh, second, demand);
            case TOGGLE -> drawGuarded(graphics, x + 7, gy, w - 14, gh, demand > 0.5, false);
            default -> drawDial(graphics, x + 7, gy, w - 14, gh, (demand + 1.0) / 2.0, axis.color());
        }

        String value = pair
                ? String.format(Locale.ROOT, "%+.0f%%  %+.0f%%", demand * 100, second * 100)
                : String.format(Locale.ROOT, "%+.0f%%", demand * 100);
        graphics.drawString(font, value, x + w - font.width(value) - 7, y + h - 12,
                live ? colour : TEXT_FAINT, false);

        boolean armed = snapshot.armed();
        graphics.drawString(font, trim(snapshot.axisReport(axis).note(), w - 22 - font.width(value)),
                x + 7, y + h - 12, TEXT_FAINT, false);

        hit(x, y, w, h, () -> {
            if (widget == WidgetType.TOGGLE) {
                send(VccMk3ActionPacket.Action.SET_AXIS, null, demand > 0.5 ? 0.0 : 1.0, axis.name());
                click(1.2f);
            }
        }, title + ". " + (armed ? "Drag to command." : "Arm the console to command hardware."));

        if (widget != WidgetType.TOGGLE
                && mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h) {
            pendingDrag = new Drag(axis, widget, x + 7, gy, w - 14, gh);
        }
    }

    @Nullable
    private static String firstSlotFor(Airframe frame, ControlAxis axis) {
        for (SlotSpec spec : frame.slotList()) {
            if (spec.axis() == axis) return spec.key();
        }
        return null;
    }

    private static boolean hasSlotFor(Airframe frame, ControlAxis axis) {
        return frame.serves(axis);
    }

    private void renderPfd(GuiGraphics graphics, int x, int y, int w, int h,
                           VccMk3SnapshotPacket snapshot) {
        AvionicsChannel pitch = snapshot.channel(VccMk3SnapshotPacket.CH_PITCH);
        AvionicsChannel roll = snapshot.channel(VccMk3SnapshotPacket.CH_ROLL);
        AvionicsChannel heading = snapshot.channel(VccMk3SnapshotPacket.CH_HEADING);
        AvionicsChannel speed = snapshot.channel(VccMk3SnapshotPacket.CH_GROUND_SPEED);
        AvionicsChannel altitude = snapshot.channel(VccMk3SnapshotPacket.CH_ALTITUDE);

        boolean tapes = w >= 300;
        int cx = x + w / 2;
        int cy = y + 18 + (h - 40) / 2;
        int r = Math.max(36, Math.min((h - 60) / 2, (w - (tapes ? 200 : 40)) / 2));

        drawAttitudeBall(graphics, cx, cy, r, pitch, roll);

        if (tapes) {
            drawTape(graphics, x + 10, cy - r, 62, r * 2, "SPD", speed, 1, "m/s", true);
            drawTape(graphics, x + w - 72, cy - r, 62, r * 2, "ALT", altitude, 0, "m", false);
        }
        int tapeW = Math.min(r * 2, w - 20);
        drawHeadingTape(graphics, cx - tapeW / 2, y + 20, tapeW, heading);

        if (!pitch.present() || !roll.present()) {
            String warning = "NO ATTITUDE";
            graphics.drawString(font, warning, cx - font.width(warning) / 2, cy + r + 8, BAD, false);
            hit(cx - r, cy - r, r * 2, r * 2, null,
                    "No Gimbal Sensor on this craft, so pitch and roll are unknown. The horizon is "
                            + "showing level because it has nothing else to show, not because you are level.");
        }
    }

    private void drawTape(GuiGraphics graphics, int x, int y, int w, int h, String label,
                          AvionicsChannel value, int decimals, String unit, boolean left) {
        graphics.fill(x, y, x + w, y + h, 0xFF0A0E13);
        ConsoleDraw.outline(graphics, x, y, w, h, EDGE);
        graphics.drawString(font, label, x + 4, y + 3, TEXT_FAINT, false);

        int midY = y + h / 2;
        if (value.present()) {
            double v = value.value();

            double step = Math.max(1.0, Math.pow(10, Math.floor(Math.log10(Math.max(1.0, Math.abs(v)) )) - 1) * 5);
            for (int i = -4; i <= 4; i++) {
                double mark = Math.round(v / step) * step + i * step;
                int ty = midY - (int) Math.round((mark - v) / step * (h / 11.0));
                if (ty < y + 14 || ty > y + h - 6) continue;
                graphics.fill(left ? x + w - 6 : x, ty, left ? x + w : x + 6, ty + 1, EDGE);
                String text = String.format(Locale.ROOT, "%.0f", mark);
                graphics.drawString(font, text, left ? x + w - 9 - font.width(text) : x + 9,
                        ty - 3, TEXT_FAINT, false);
            }
        }

        String text = value.present() ? value.format(decimals) : "---";
        int boxW = Math.max(font.width(text) + 10, 40);
        int boxX = left ? x + w - boxW : x;
        graphics.fill(boxX, midY - 8, boxX + boxW, midY + 8, PANEL_RAISED);
        ConsoleDraw.outline(graphics, boxX, midY - 8, boxW, 16, value.present() ? CYAN : EDGE);
        graphics.drawString(font, text, boxX + 5, midY - 4, value.present() ? TEXT : TEXT_FAINT, false);
        graphics.drawString(font, unit, x + 4, y + h - 11, TEXT_FAINT, false);
        hit(x, y, w, h, null, label + ": " + value.source().description());
    }

    private void drawHeadingTape(GuiGraphics graphics, int x, int y, int w, AvionicsChannel heading) {
        graphics.fill(x, y, x + w, y + 18, 0xFF0A0E13);
        ConsoleDraw.outline(graphics, x, y, w, 18, EDGE);
        int cx = x + w / 2;

        if (heading.present()) {
            double hdg = heading.value();
            double pixelsPerDegree = w / 120.0;
            for (int mark = 0; mark < 360; mark += 10) {
                double delta = ((mark - hdg + 540) % 360) - 180;
                int mx = cx + (int) Math.round(delta * pixelsPerDegree);
                if (mx < x + 2 || mx > x + w - 2) continue;
                boolean cardinal = mark % 90 == 0;
                graphics.fill(mx, y + 1, mx + 1, y + (cardinal ? 8 : 5), cardinal ? TEXT_DIM : EDGE);
                if (cardinal) {
                    String name = switch (mark) {
                        case 0 -> "N";
                        case 90 -> "E";
                        case 180 -> "S";
                        default -> "W";
                    };
                    graphics.drawString(font, name, mx - font.width(name) / 2, y + 9, TEXT_DIM, false);
                }
            }
        }

        ConsoleDraw.polygon(graphics, new int[] {cx - 5, cx + 5, cx},
                new int[] {y + 18, y + 18, y + 12}, CYAN);
        String text = heading.present()
                ? String.format(Locale.ROOT, "%03.0f", heading.value()) : "---";
        graphics.fill(cx - 16, y + 19, cx + 17, y + 32, PANEL_RAISED);
        ConsoleDraw.outline(graphics, cx - 16, y + 19, 33, 13, heading.present() ? CYAN : EDGE);
        graphics.drawString(font, text, cx - font.width(text) / 2, y + 22,
                heading.present() ? TEXT : TEXT_FAINT, false);
        hit(x, y, w, 32, null, "Heading: " + heading.source().description());
    }

    private void renderFlyStatus(GuiGraphics graphics, int ox, int oy, VccMk3SnapshotPacket snapshot) {
        Airframe frame = Airframe.byOrdinal(snapshot.airframeOrdinal());
        String message;
        int colour;
        if (snapshot.flipGuardActive()) {
            message = "Flip guard engaged. Lift is at full and everything else is cut until the craft rights itself.";
            colour = BAD;
        } else if (!snapshot.armed()) {
            boolean anything = false;
            for (ControlAxis axis : ControlAxis.values()) {
                if (snapshot.axisUsable(axis)) {
                    anything = true;
                    break;
                }
            }
            message = anything
                    ? "Ready. Click SAFE in the header to arm, then use the controls on the left."
                    : "Nothing is rigged yet. Open RIG and fit your hardware onto the craft.";
            colour = anything ? GOOD : WARN;
        } else {
            ControlAxis worst = null;
            for (ControlAxis axis : ControlAxis.values()) {
                if (axis == ControlAxis.AUX || !hasSlotFor(frame, axis)) continue;
                float authority = snapshot.authority(axis);
                if (!Float.isNaN(authority) && authority <= 0f) {
                    worst = axis;
                    break;
                }
            }
            if (worst != null) {
                message = "Armed, but " + worst.label().toLowerCase(Locale.ROOT)
                        + " has no authority. " + capitalise(snapshot.axisReport(worst).note()) + ".";
                colour = WARN;
            } else {
                message = "Armed. Every axis has authority.";
                colour = GOOD;
            }
        }
        renderStatus(graphics, ox, oy, message, colour, "drag a control   or hold WASD, space, shift");
    }

    private float frameDt = 1f / 60f;

    private void advanceFrameClock() {
        long now = System.nanoTime();
        if (lastFrameNanos != 0L) {
            frameDt = Math.max(1f / 240f, Math.min(0.1f, (now - lastFrameNanos) / 1.0e9f));
        }
        lastFrameNanos = now;
        animClock += frameDt;

        if (testAxis != null && page == Page.RIG) testClock += frameDt;
        else if (page != Page.RIG) testAxis = null;
        frame++;
    }

    private double smoothed(ControlAxis axis, double target) {
        if (!Double.isFinite(target)) target = 0.0;
        float[] state = widgetDisplay.computeIfAbsent(axis, k -> new float[2]);
        if (drag != null && drag.axis() == axis) {
            state[0] = (float) target;
            state[1] = 0f;
            return target;
        }

        float omega = 18f;
        float dt = Math.min(0.05f, frameDt);
        float delta = (float) target - state[0];
        state[1] += (omega * omega * delta - 2f * omega * state[1]) * dt;
        state[0] += state[1] * dt;
        if (Math.abs(delta) < 1.0e-4f && Math.abs(state[1]) < 1.0e-3f) {
            state[0] = (float) target;
            state[1] = 0f;
        }
        return state[0];
    }

    private void renderFlightData(GuiGraphics graphics, int x, int y, int w, int h,
                                  VccMk3SnapshotPacket snapshot) {
        int columns = Math.max(1, Math.min(3, (w - 16) / 200));
        int colW = (w - 16 - (columns - 1) * 10) / columns;

        int[][] readouts = {
                { VccMk3SnapshotPacket.CH_ALTITUDE, 1 },
                { VccMk3SnapshotPacket.CH_CLIMB, 2 },
                { VccMk3SnapshotPacket.CH_TERRAIN, 1 },
                { VccMk3SnapshotPacket.CH_GROUND_SPEED, 1 },
                { VccMk3SnapshotPacket.CH_AIRSPEED, 1 },
                { VccMk3SnapshotPacket.CH_HEADING, 0 },
                { VccMk3SnapshotPacket.CH_PITCH, 1 },
                { VccMk3SnapshotPacket.CH_ROLL, 1 },
                { VccMk3SnapshotPacket.CH_PRESSURE, 2 },
        };
        String[] labels = { "ALTITUDE", "CLIMB", "TERRAIN", "GROUND SPD", "AIRSPEED",
                "HEADING", "PITCH", "ROLL", "PRESSURE" };
        String[] units = { " m", " m/s", " m", " m/s", " m/s", " deg", " deg", " deg", "" };

        int perColumn = (int) Math.ceil(readouts.length / (double) columns);
        for (int i = 0; i < readouts.length; i++) {
            int column = i / perColumn;
            int row = i % perColumn;
            int cx = x + 8 + column * (colW + 10);
            int cy = y + 22 + row * 13;
            if (cy > y + h - 12) continue;
            instrument(graphics, cx, cy, colW, labels[i],
                    snapshot.channel(readouts[i][0]), readouts[i][1], units[i]);
        }
    }

    private void renderAtmosphere(GuiGraphics graphics, int x, int y, int w,
                                  @Nullable VccMk3SnapshotPacket snapshot) {
        int row = y + 22;
        AvionicsChannel ground = channel(snapshot, VccMk3SnapshotPacket.CH_GROUND_SPEED);
        AvionicsChannel air = channel(snapshot, VccMk3SnapshotPacket.CH_AIRSPEED);
        row = instrument(graphics, x + 8, row, w - 16, "GND SPD", ground, 2, " m/s");
        row = instrument(graphics, x + 8, row, w - 16, "AIRSPD", air, 2, " m/s");

        if (ground.present() && air.present()) {
            double delta = ground.value() - air.value();
            String label = Math.abs(delta) < 0.05 ? "still air"
                    : (delta > 0 ? "tailwind" : "headwind");
            String text = String.format(Locale.ROOT, "%+.2f %s", delta, label);
            graphics.drawString(font, text, x + 8, row, Math.abs(delta) < 0.05 ? TEXT_FAINT : CYAN, false);
        } else {
            graphics.drawString(font, "wind unavailable", x + 8, row, TEXT_FAINT, false);
        }
        row += 14;

        AvionicsChannel pressure = channel(snapshot, VccMk3SnapshotPacket.CH_PRESSURE);
        row = instrument(graphics, x + 8, row, w - 16, "AIR PRESS", pressure, 3, "");
        if (pressure.present()) {
            String verdict;
            int colour;
            if (pressure.value() > 0.75) {
                verdict = "dense, full thrust";
                colour = GOOD;
            } else if (pressure.value() > 0.45) {
                verdict = "thinning, thrust down";
                colour = WARN;
            } else {
                verdict = "thin, near ceiling";
                colour = BAD;
            }
            graphics.drawString(font, verdict, x + 8, row, colour, false);
        } else {
            graphics.drawString(font, "no altimeter fitted", x + 8, row, TEXT_FAINT, false);
        }
        row += 14;
        graphics.fill(x + 8, row, x + w - 8, row + 1, EDGE);

        AvionicsChannel windSpeed = channel(snapshot, VccMk3SnapshotPacket.CH_WIND_SPEED);
        AvionicsChannel windFrom = channel(snapshot, VccMk3SnapshotPacket.CH_WIND_FROM);
        AvionicsChannel heading = channel(snapshot, VccMk3SnapshotPacket.CH_HEADING);
        drawWindRose(graphics, x + w / 2, row + 42, 30, windFrom, heading);

        graphics.drawString(font, "WND " + (windFrom.present() ? windFrom.format(0) : "---"),
                x + 8, row + 77, windFrom.present() ? CYAN : TEXT_FAINT, false);
        String speedText = windSpeed.present() ? windSpeed.format(2) : "---";
        graphics.drawString(font, speedText, x + w - 8 - font.width(speedText), row + 77, TEXT_DIM, false);
    }

    private void renderAutoPilot(GuiGraphics graphics, int x, int y, int w, int h,
                                 @Nullable VccMk3SnapshotPacket snapshot) {
        VccMk3SnapshotPacket.AutoState auto = snapshot == null
                ? VccMk3SnapshotPacket.AutoState.OFF : snapshot.auto();

        int statusColour = !auto.engaged() ? TEXT_FAINT : (auto.hasFix() ? CYAN : WARN);
        graphics.drawString(font, trim(auto.status(), w - 16), x + 8, y + 20, statusColour, false);

        String primary;
        String secondary;
        if (auto.routeSource()) {
            primary = auto.hasFix()
                    ? String.format(Locale.ROOT, "RUN %.0f m", auto.distance()) : "RUN ---";
            secondary = auto.waypointCount() > 0
                    ? "WPT " + auto.waypointIndex() + "/" + auto.waypointCount() : "WPT ---";
        } else {
            primary = auto.hasFix()
                    ? String.format(Locale.ROOT, "RNG %.0f m", auto.distance()) : "RNG ---";
            secondary = auto.hasFix() && Float.isFinite(auto.bearingDeg())
                    ? String.format(Locale.ROOT, "BRG %+.0f", auto.bearingDeg()) : "BRG ---";
        }
        graphics.drawString(font, primary, x + 8, y + 32, auto.hasFix() ? TEXT_DIM : TEXT_FAINT, false);
        graphics.drawString(font, secondary, x + 8 + 78, y + 32, auto.hasFix() ? TEXT_DIM : TEXT_FAINT, false);

        if (auto.routeSource() && auto.waypointCount() > 0) {
            float progress = Math.min(1f, auto.waypointIndex() / (float) auto.waypointCount());
            ConsoleDraw.bar(graphics, x + 8, y + 44, w - 16, 5, progress, PANEL, EDGE,
                    auto.engaged() ? CYAN : EDGE_BRIGHT);
        }

        String hands = autoPilotHands(snapshot);
        graphics.drawString(font, trim(hands, w - 16), x + 8, y + 53,
                auto.engaged() ? CYAN : TEXT_FAINT, false);
        hit(x + 8, y + 51, w - 16, 10, null, auto.engaged()
                ? "The autopilot is moving these controls. Grab one to take over."
                : "When engaged, the autopilot will move these controls rather than commanding the "
                        + "hardware behind them, so every route and curve you authored still applies.");

        int buttonY = y + h - 24;
        String source = auto.routeSource() ? "ROUTE" : "NAV";
        int sourceW = 52;
        graphics.fill(x + 8, buttonY, x + 8 + sourceW, buttonY + 16, PANEL_SUNK);
        ConsoleDraw.outline(graphics, x + 8, buttonY, sourceW, 16, CYAN);
        graphics.drawString(font, source, x + 8 + (sourceW - font.width(source)) / 2, buttonY + 4, CYAN, false);
        hit(x + 8, buttonY, sourceW, 16, () -> {
            send(VccMk3ActionPacket.Action.CYCLE_AUTO_SOURCE, null, 0, "");
            click(1.1f);
        }, auto.routeSource()
                ? "Following a Pathfinder Computer's planned route. Click to steer to a Nav Table target instead."
                : "Steering to a Nav Table target. Click to follow a Pathfinder Computer's planned route instead.");

        boolean engaged = auto.engaged();
        String label = engaged ? "DISENGAGE" : "ENGAGE";
        int colour = engaged ? WARN : GOOD;
        int buttonX = x + 8 + sourceW + 4;
        int buttonW = w - 8 - (buttonX - x);
        graphics.fill(buttonX, buttonY, buttonX + buttonW, buttonY + 16, engaged ? 0xFF342A12 : PANEL_SUNK);
        ConsoleDraw.outline(graphics, buttonX, buttonY, buttonW, 16, colour);
        graphics.drawString(font, label, buttonX + (buttonW - font.width(label)) / 2, buttonY + 4, colour, false);
        hit(buttonX, buttonY, buttonW, 16, () -> {
            send(VccMk3ActionPacket.Action.SET_AUTOPILOT, null, engaged ? 0.0 : 1.0, "");
            armSound(!engaged);
        }, engaged
                ? "Hand the levers back. Thrust and yaw return to neutral."
                : "Let the autopilot steer. It moves the cockpit levers rather than the hardware, so "
                        + "you can watch what it is asking for and take over by grabbing them.");
    }

    private String autoPilotHands(@Nullable VccMk3SnapshotPacket snapshot) {
        if (snapshot == null) return "no controller";
        boolean forward = snapshot.axisUsable(ControlAxis.FORWARD);
        boolean yaw = snapshot.axisUsable(ControlAxis.YAW);
        if (forward && yaw) return "hands on forward and yaw";
        if (forward) return "forward only, nothing can turn the craft";
        if (yaw) return "yaw only, nothing can drive the craft";
        return "neither forward nor yaw has authority";
    }

    private void drawLever(GuiGraphics graphics, int x, int y, int w, int h, double fraction) {
        int trackX = x + w / 2 - 6;
        graphics.fill(trackX, y, trackX + 12, y + h, PANEL);
        ConsoleDraw.outline(graphics, trackX, y, 12, h, EDGE);
        for (int t = 0; t <= 5; t++) {
            int ty = y + (int) Math.round(t * (h - 2) / 5.0) + 1;
            graphics.fill(trackX + 13, ty, trackX + (t % 5 == 0 ? 20 : 17), ty + 1, TEXT_FAINT);
        }
        int knobY = y + (int) Math.round((1.0 - clamp01(fraction)) * (h - 12));
        graphics.fill(trackX - 4, knobY, trackX + 16, knobY + 12, BRASS);
        ConsoleDraw.outline(graphics, trackX - 4, knobY, 20, 12, BRASS_DARK);

        graphics.fill(trackX - 2, knobY + 3, trackX + 14, knobY + 4, 0xFF50401A);
        graphics.fill(trackX - 2, knobY + 7, trackX + 14, knobY + 8, 0xFF50401A);
    }

    private void drawWheel(GuiGraphics graphics, int x, int y, int w, int h, double deflection, int accent) {
        int cx = x + w / 2;
        int cy = y + h / 2;
        int r = Math.min(w, h) / 2 - 3;
        if (r < 6) return;
        ConsoleDraw.ring(graphics, cx, cy, r, 3, EDGE_BRIGHT);
        ConsoleDraw.circle(graphics, cx, cy, r - 7, EDGE);
        double turn = clampSigned(deflection) * 42.0;
        for (int i = 0; i < 6; i++) {
            double angle = i * 60.0 + turn;
            ConsoleDraw.tick(graphics, cx, cy, 8, r - 3, angle, EDGE_BRIGHT);
        }
        ConsoleDraw.disc(graphics, cx, cy, 7, BRASS);
        ConsoleDraw.circle(graphics, cx, cy, 7, BRASS_DARK);
        int[] marker = ConsoleDraw.onCircle(cx, cy, r, turn);
        ConsoleDraw.disc(graphics, marker[0], marker[1], 3, accent);
    }

    private void drawDial(GuiGraphics graphics, int x, int y, int w, int h, double fraction, int accent) {
        int cx = x + w / 2;
        int cy = y + h / 2;
        int r = Math.min(w, h) / 2 - 3;
        if (r < 6) return;
        ConsoleDraw.disc(graphics, cx, cy, r, PANEL);
        ConsoleDraw.ring(graphics, cx, cy, r, 2, EDGE_BRIGHT);

        for (int i = 0; i <= 10; i++) {
            ConsoleDraw.tick(graphics, cx, cy, r - 7, r - 2, -135.0 + i * 27.0, TEXT_FAINT);
        }
        ConsoleDraw.needle(graphics, cx, cy, r - 5, -135.0 + clamp01(fraction) * 270.0, 2, accent);
        ConsoleDraw.disc(graphics, cx, cy, 3, BRASS);
    }

    private void drawStick(GuiGraphics graphics, int x, int y, int w, int h, double dx, double dy) {
        int cx = x + w / 2;
        int cy = y + h / 2;
        int gate = Math.min(w, h) / 2 - 4;
        if (gate < 6) return;
        graphics.fill(cx - gate, cy - gate, cx + gate, cy + gate, PANEL);
        ConsoleDraw.outline(graphics, cx - gate, cy - gate, gate * 2, gate * 2, EDGE);
        graphics.fill(cx - gate + 2, cy, cx + gate - 2, cy + 1, EDGE);
        graphics.fill(cx, cy - gate + 2, cx + 1, cy + gate - 2, EDGE);
        int hx = cx + (int) Math.round(clampSigned(dx) * (gate - 6));
        int hy = cy + (int) Math.round(clampSigned(dy) * (gate - 6));
        ConsoleDraw.thickLine(graphics, cx, cy, hx, hy, 2, EDGE_BRIGHT);
        ConsoleDraw.disc(graphics, hx, hy, 5, BRASS);
        ConsoleDraw.circle(graphics, hx, hy, 5, BRASS_DARK);
    }

    private void drawGuarded(GuiGraphics graphics, int x, int y, int w, int h, boolean on, boolean guarded) {
        int bx = x + w / 2 - 22;
        int by = y + h / 2 - 15;
        graphics.fill(bx, by, bx + 44, by + 30, PANEL);
        ConsoleDraw.outline(graphics, bx, by, 44, 30, EDGE);
        if (guarded) {
            graphics.fill(bx + 3, by + 3, bx + 41, by + 12, on ? 0xFF284630 : 0xFF5C2828);
            ConsoleDraw.outline(graphics, bx + 3, by + 3, 38, 9, on ? GOOD : BAD);
        }
        int paddleY = on ? by + 15 : by + 19;
        graphics.fill(bx + 15, paddleY, bx + 29, paddleY + 9, on ? GOOD : EDGE_BRIGHT);
        ConsoleDraw.outline(graphics, bx + 15, paddleY, 14, 9, EDGE);
    }

    private void drawSelector(GuiGraphics graphics, int x, int y, int w, int h, double fraction, int accent) {
        int cx = x + w / 2;
        int cy = y + h / 2;
        int r = Math.min(w, h) / 2 - 3;
        if (r < 6) return;
        ConsoleDraw.disc(graphics, cx, cy, r, PANEL);
        ConsoleDraw.ring(graphics, cx, cy, r, 2, EDGE_BRIGHT);

        for (int i = 0; i < 5; i++) {
            ConsoleDraw.tick(graphics, cx, cy, r - 8, r - 2, -90.0 + i * 45.0, TEXT_FAINT);
        }
        double angle = -90.0 + Math.round(clamp01(fraction) * 4) * 45.0;
        ConsoleDraw.needle(graphics, cx, cy, r - 6, angle, 3, accent);
        ConsoleDraw.disc(graphics, cx, cy, 4, BRASS);
    }

    private void drawButton(GuiGraphics graphics, int x, int y, int w, int h, boolean pressed, int accent) {
        int size = Math.min(w, h) - 12;
        int bx = x + (w - size) / 2;
        int by = y + (h - size) / 2;
        graphics.fill(bx, by, bx + size, by + size, PANEL);
        ConsoleDraw.outline(graphics, bx, by, size, size, EDGE);
        int inset = pressed ? 5 : 3;
        graphics.fill(bx + inset, by + inset, bx + size - inset, by + size - inset,
                pressed ? accent : ConsoleDraw.blend(accent, PANEL_SUNK, 0.55f));
    }

    private void drawAttitudeBall(GuiGraphics graphics, int cx, int cy, int r,
                                  AvionicsChannel pitch, AvionicsChannel roll) {
        if (pitch.present() || roll.present()) {
            double pitchDeg = pitch.orElse(0.0);
            double slope = Math.tan(Math.toRadians(roll.orElse(0.0)));

            double offset = pitchDeg * (r / 45.0);
            ConsoleDraw.horizonDisc(graphics, cx, cy, r, offset, slope, HORIZON_SKY, HORIZON_GROUND, CYAN);
        } else {
            ConsoleDraw.disc(graphics, cx, cy, r, HORIZON_SKY);
        }

        ConsoleDraw.ring(graphics, cx, cy, r, 2, EDGE_BRIGHT);

        graphics.fill(cx - 20, cy, cx - 7, cy + 2, BRASS);
        graphics.fill(cx + 8, cy, cx + 21, cy + 2, BRASS);
        graphics.fill(cx - 2, cy - 2, cx + 3, cy + 3, BRASS);
    }

    private void drawWindRose(GuiGraphics graphics, int cx, int cy, int r,
                              AvionicsChannel windFrom, AvionicsChannel heading) {
        ConsoleDraw.circle(graphics, cx, cy, r, EDGE);
        for (int i = 0; i < 8; i++) {
            ConsoleDraw.tick(graphics, cx, cy, r - 5, r, i * 45.0, TEXT_FAINT);
        }

        graphics.fill(cx - 1, cy - r + 2, cx + 2, cy - r + 10, BRASS);

        if (!windFrom.present()) {
            graphics.drawString(font, "--", cx - font.width("--") / 2, cy - 4, TEXT_FAINT, false);
            return;
        }

        double relative = windFrom.value() - heading.orElse(0.0);
        int[] tip = ConsoleDraw.onCircle(cx, cy, r - 6, relative);
        ConsoleDraw.thickLine(graphics, cx, cy, tip[0], tip[1], 3, CYAN);
        ConsoleDraw.disc(graphics, cx, cy, 2, BRASS);
    }

    private void renderHardware(GuiGraphics graphics, int ox, int oy,
                                @Nullable VccMk3SnapshotPacket snapshot, int mouseX, int mouseY) {
        int bodyY = bodyTop();
        int bodyH = bodyHeight();
        int w = canvasW();

        boolean narrow = hardwareNarrow();
        int rackW = RACK_COL_W;
        int rackX = ox + PAD;
        int rightColW = narrow ? 0 : rightCol();
        int rightX = ox + w - rightColW + PAD;
        int rightW = rightColW - PAD * 2;
        int ledgerX = ox + PAD + rackW + PAD;
        int ledgerW = Math.max(200, (narrow ? ox + w - PAD : rightX - PAD) - ledgerX);

        int foldY = bodyY + PAD + 326;
        int foldH = bodyH - PAD * 2 - 326;

        boolean hasFold = foldH >= 76;
        int foldHalf = (foldH - PAD) / 2;

        panel(graphics, rackX, bodyY + PAD, rackW, 194, "CARD RACK", BRASS);
        panel(graphics, rackX, bodyY + PAD + 202, rackW, 116, "YOUR CARDS");
        panel(graphics, ledgerX, bodyY + PAD, ledgerW, bodyH - PAD * 2, "LINK LEDGER", CYAN);

        if (narrow) {
            if (hasFold) {
                panel(graphics, rackX, foldY, rackW, foldHalf, "INSTRUMENTS", GOOD);
                panel(graphics, rackX, foldY + foldHalf + PAD, rackW, foldH - foldHalf - PAD,
                        "SAFETY", WARN);
            }
        } else {
            if (hasFold) panel(graphics, rackX, foldY, rackW, foldH, "HOW A BAY WORKS");
            rightInstrH = Math.min(138, (bodyH - PAD * 2) / 4);
            rightSafetyH = Math.min(128, (bodyH - PAD * 2) / 4);
            rightRemoteY = bodyY + PAD + rightInstrH + rightSafetyH + PAD * 2;
            rightRemoteH = bodyY + bodyH - PAD - rightRemoteY;
            panel(graphics, rightX, bodyY + PAD, rightW, rightInstrH, "INSTRUMENTS", GOOD);
            panel(graphics, rightX, bodyY + PAD + rightInstrH + PAD, rightW, rightSafetyH,
                    "SAFETY", WARN);
            if (rightRemoteH >= 60) {
                panel(graphics, rightX, rightRemoteY, rightW, rightRemoteH, "REMOTE CONTROL", CYAN);
            }
        }

        if (snapshot == null) {
            graphics.drawString(font, "Waiting for the controller.", ledgerX + 12, bodyY + 40,
                    TEXT_FAINT, false);
            renderStatus(graphics, ox, oy, "No signal from the controller.", WARN, "");
            return;
        }

        int ready = 0;
        int setup = 0;
        int broken = 0;
        for (VccMk3SnapshotPacket.SlotEntry entry : snapshot.slots()) {
            LinkHealth health = VccMk3SnapshotPacket.healthOf(entry);
            if (health == LinkHealth.OK) ready++;
            else if (health.severity() == LinkHealth.Severity.WARN) setup++;
            else if (health.severity() == LinkHealth.Severity.BAD) broken++;
        }

        int summaryY = bodyY + PAD + 20;
        graphics.fill(ledgerX + 6, summaryY, ledgerX + ledgerW - 6, summaryY + 18, PANEL);
        graphics.drawString(font, ready + " ready", ledgerX + 12, summaryY + 5, GOOD, false);
        graphics.drawString(font, setup + " need setup", ledgerX + 92, summaryY + 5,
                setup > 0 ? WARN : TEXT_FAINT, false);
        graphics.drawString(font, broken + " broken", ledgerX + 196, summaryY + 5,
                broken > 0 ? BAD : TEXT_FAINT, false);
        smallButton(graphics, ledgerX + ledgerW - 76, summaryY + 1, 66, 16, "RESCAN", CYAN,
                () -> {
                    send(VccMk3ActionPacket.Action.RESCAN, null, 0, "");
                    click(1.2f);
                },
                "Sweep the craft for hardware and instruments again, without touching your rig. "
                        + "Press it after adding or moving a bearing.");

        if (narrow && !hasFold) {
            smallButton(graphics, ledgerX + ledgerW - 150, summaryY + 1, 68, 16,
                    "GUARD " + (snapshot.flipGuardDegrees() <= 0 ? "OFF"
                            : String.valueOf(Math.round(snapshot.flipGuardDegrees()))),
                    snapshot.flipGuardActive() ? BAD : WARN,
                    () -> {
                        send(VccMk3ActionPacket.Action.SET_FLIP_GUARD, null, nextFlipGuard(snapshot), "");
                        click(1.1f);
                    },
                    "Flip guard. Tilt past this angle and everything except lift is cut until the "
                            + "craft rights itself. Click to cycle: off, 45, 62, 75 degrees.");
        }

        int listTop = summaryY + 24;
        int listH = bodyY + PAD + bodyH - PAD * 2 - listTop - 4;
        int rowH = Math.max(13, Math.min(26, listH / VccMk3BlockEntity.ROW_COUNT));
        int visible = Math.max(4, listH / rowH);
        int total = snapshot.slots().size();
        ledgerScroll = Math.max(0, Math.min(ledgerScroll, Math.max(0, total - visible)));
        ledgerBounds = listBounds(ledgerX, listTop, ledgerW, listH, total, visible);
        for (int i = ledgerScroll; i < total && (i - ledgerScroll) < visible; i++) {
            renderLedgerRow(graphics, ledgerX + 6, listTop + (i - ledgerScroll) * rowH,
                    ledgerW - 12, rowH, snapshot.slots().get(i), snapshot, mouseX, mouseY);
        }
        if (total > visible) {
            scrollbar(graphics, ledgerX + ledgerW - 7, listTop, 3, visible * rowH,
                    total, ledgerScroll, visible, EDGE_BRIGHT);
        }

        renderCardRack(graphics, ox, oy, snapshot);
        renderPlayerInventory(graphics, ox, oy);
        if (narrow) {
            if (hasFold) {
                    renderInstruments(graphics, rackX, foldY, rackW, foldHalf, snapshot);
                renderSafety(graphics, rackX, foldY + foldHalf + PAD, rackW,
                        foldH - foldHalf - PAD, snapshot);
            }
        } else {
            if (hasFold) renderBayHelp(graphics, rackX, foldY, rackW, foldH);
            renderInstruments(graphics, rightX, bodyY + PAD, rightW, rightInstrH, snapshot);
            renderSafety(graphics, rightX, bodyY + PAD + rightInstrH + PAD, rightW,
                    rightSafetyH, snapshot);
            if (rightRemoteH >= 60) {
                renderRemote(graphics, rightX, rightRemoteY, rightW, rightRemoteH, snapshot);
            }
        }

        String message;
        int colour;
        if (broken > 0) {
            message = broken + (broken == 1 ? " bay is broken." : " bays are broken.")
                    + " Each row says what to do about it.";
            colour = BAD;
        } else if (setup > 0) {
            message = setup + (setup == 1 ? " bay is half-finished." : " bays are half-finished.")
                    + " A bay needs the card that names the hardware and a Governor Card beside it.";
            colour = WARN;
        } else if (ready == 0) {
            message = "No hardware yet. Put a Propeller Card in a bay's left slot and a Governor Card beside it.";
            colour = WARN;
        } else {
            message = ready + (ready == 1 ? " bay is" : " bays are") + " ready. Fit them onto the craft on RIG.";
            colour = GOOD;
        }
        renderStatus(graphics, ox, oy, message, colour, "click a row to switch it to a signal link");
    }

    private void renderInstruments(GuiGraphics graphics, int x, int y, int w, int h,
                                   VccMk3SnapshotPacket snapshot) {
        SensorDiscovery.Kind[] kinds = SensorDiscovery.Kind.values();
        int row = y + 22;
        int bottom = y + h - 6;
        for (int i = 0; i < kinds.length; i++) {
            if (row + 11 > bottom) {
                int hidden = kinds.length - i;
                graphics.drawString(font, hidden + " more, widen the window", x + 10, row,
                        TEXT_FAINT, false);
                break;
            }
            byte state = snapshot.sensorStates()[Math.min(i, snapshot.sensorStates().length - 1)];
            boolean live = state == VccMk3SnapshotPacket.SENSOR_AUTO
                    || state == VccMk3SnapshotPacket.SENSOR_MANUAL;
            String tag = switch (state) {
                case VccMk3SnapshotPacket.SENSOR_AUTO -> "AUTO";
                case VccMk3SnapshotPacket.SENSOR_MANUAL -> "PIN";
                case VccMk3SnapshotPacket.SENSOR_BROKEN -> "GONE";
                default -> "NONE";
            };
            int colour = state == VccMk3SnapshotPacket.SENSOR_BROKEN ? BAD : (live ? GOOD : TEXT_FAINT);
            String instrument = kinds[i].label().replace(" Sensor", "").replace(" Computer", "");
            graphics.drawString(font, trim(instrument, w - 34 - font.width(tag)), x + 10, row,
                    live ? TEXT_DIM : TEXT_FAINT, false);
            graphics.drawString(font, tag, x + w - 10 - font.width(tag), row, colour, false);
            hit(x + 8, row - 1, w - 16, 11, null, switch (state) {
                case VccMk3SnapshotPacket.SENSOR_AUTO ->
                        kinds[i].label() + " found automatically. Providing " + kinds[i].provides() + ".";
                case VccMk3SnapshotPacket.SENSOR_MANUAL ->
                        kinds[i].label() + " pinned by hand. Providing " + kinds[i].provides() + ".";
                case VccMk3SnapshotPacket.SENSOR_BROKEN ->
                        kinds[i].label() + " was pinned by hand but is no longer there.";
                default -> "No " + kinds[i].label() + ". " + capitalise(kinds[i].provides())
                        + " is estimated from physics.";
            });
            row += 13;
        }
    }

    private void renderCardRack(GuiGraphics graphics, int ox, int oy, VccMk3SnapshotPacket snapshot) {
        for (int bay = 0; bay < VccMk3BlockEntity.ROW_COUNT; bay++) {
            int column = bay % VccMk3Menu.RACK_COLUMNS;
            int row = bay / VccMk3Menu.RACK_COLUMNS;
            int bayX = ox + VccMk3Menu.RACK_GRID_X + column * VccMk3Menu.RACK_BAY_PITCH;
            int bayY = oy + VccMk3Menu.RACK_GRID_Y + row * VccMk3Menu.RACK_ROW_PITCH;

            LinkHealth health = bay < snapshot.slots().size()
                    ? VccMk3SnapshotPacket.healthOf(snapshot.slots().get(bay)) : LinkHealth.EMPTY;
            boolean linkRow = (snapshot.linkRowMask() & (1 << bay)) != 0;
            int accent = linkRow ? CYAN : health.color();

            graphics.drawString(font, String.format(Locale.ROOT, "%02d", bay + 1),
                    bayX, bayY + 5, health == LinkHealth.EMPTY && !linkRow ? TEXT_FAINT : accent, false);

            int slotsX = bayX + VccMk3Menu.RACK_BAY_LABEL_W;
            slotBackdrop(graphics, slotsX, bayY, accent, false);
            slotBackdrop(graphics, slotsX + 18, bayY, accent, false);

            String detail = linkRow
                    ? "Bay " + (bay + 1) + " is a Create signal link, not a card row."
                    : "Bay " + (bay + 1) + ": left slot takes the card that names the hardware, "
                            + "right slot takes its Governor Card. "
                            + (health == LinkHealth.OK ? "Linked and ready."
                                    : health == LinkHealth.EMPTY ? "Empty." : health.problem() + ".");
            hit(bayX, bayY, VccMk3Menu.RACK_BAY_PITCH - 4, 18, null, detail);
        }
    }

    private void renderPlayerInventory(GuiGraphics graphics, int ox, int oy) {
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                slotBackdrop(graphics, ox + VccMk3Menu.PLAYER_INV_X + column * 18,
                        oy + VccMk3Menu.PLAYER_INV_Y + row * 18, EDGE, false);
            }
        }
        for (int column = 0; column < 9; column++) {
            slotBackdrop(graphics, ox + VccMk3Menu.PLAYER_INV_X + column * 18,
                    oy + VccMk3Menu.HOTBAR_Y, EDGE_BRIGHT, false);
        }
    }

    private void slotBackdrop(GuiGraphics graphics, int x, int y, int accent, boolean lifted) {
        graphics.fill(x, y, x + 18, y + 18, lifted ? PANEL_RAISED : PANEL_SUNK);
        ConsoleDraw.outline(graphics, x, y, 18, 18, accent);
    }

    private void renderLedgerRow(GuiGraphics graphics, int x, int y, int w, int rowH,
                                 VccMk3SnapshotPacket.SlotEntry entry,
                                 VccMk3SnapshotPacket snapshot, int mouseX, int mouseY) {
        int h = rowH - 2;
        LinkHealth health = VccMk3SnapshotPacket.healthOf(entry);
        DeviceCategory category = VccMk3SnapshotPacket.categoryOf(entry);
        int accent = health.color();
        boolean hover = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
        boolean linkRow = (snapshot.linkRowMask() & (1 << entry.row())) != 0;

        graphics.fill(x, y, x + w, y + h, hover ? PANEL_RAISED : PANEL_SUNK);
        graphics.fill(x, y, x + 3, y + h, accent);

        int labelY = h >= 19 ? y + 2 : y + (h - 8) / 2;
        graphics.drawString(font, String.format(Locale.ROOT, "%02d", entry.row() + 1),
                x + 9, labelY, TEXT_FAINT, false);
        String kind = category == null ? "-" : category.shortLabel();
        graphics.drawString(font, kind, x + 30, labelY,
                category == null ? TEXT_FAINT : (category.color() | 0xFF000000), false);
        graphics.drawString(font, statusWord(health), x + 72, labelY, accent, false);

        boolean twoLine = h >= 19;
        int textY = twoLine ? y + 2 : y + (h - 8) / 2;
        if (health != LinkHealth.OK && health != LinkHealth.EMPTY) {
            String problem = health.problem();
            if (!entry.detail().isEmpty()) problem = problem + " (" + entry.detail() + ")";
            graphics.drawString(font, trim(problem, w - 136), x + 128, textY, TEXT, false);

            if (twoLine) {
                graphics.drawString(font, trim(health.fix(), w - 136), x + 128, y + 11,
                        TEXT_FAINT, false);
            }
        } else if (health == LinkHealth.OK && category != null) {
            graphics.drawString(font, category.label() + " linked and ready", x + 128,
                    twoLine ? y + 6 : textY, TEXT_DIM, false);
        } else if (linkRow) {
            boolean receiving = (snapshot.linkRxMask() & (1 << entry.row())) != 0;
            graphics.drawString(font, receiving ? "Signal link, RECEIVING" : "Signal link, transmitting",
                    x + 128, twoLine ? y + 6 : textY, receiving ? CYAN : TEXT_FAINT, false);
        }

        int row = entry.row();
        boolean receiving = (snapshot.linkRxMask() & (1 << row)) != 0;
        String tooltip;
        if (!linkRow) {
            tooltip = "Hardware card row. Click to switch it to a Create signal link. "
                    + "Anything in the slots is handed back.";
        } else if (receiving) {
            tooltip = "This link listens, so a Linked Controller can fly the craft from outside. "
                    + "Shift-click to go back to transmitting.";
        } else {
            tooltip = "This link transmits, driving burners or doors. "
                    + "Shift-click to make it listen instead. Click to go back to hardware cards.";
        }
        hit(x, y, w, h,
                () -> {
                    if (hasShiftDown() && linkRow) {
                        send(VccMk3ActionPacket.Action.TOGGLE_RX_ROW, null, row, "");
                    } else {
                        send(VccMk3ActionPacket.Action.TOGGLE_LINK_ROW, null, row, "");
                    }
                    click(1.1f);
                },
                tooltip);
    }

    private void renderBayHelp(GuiGraphics graphics, int x, int y, int w, int h) {
        if (h < 60) return;
        int row = y + 22;
        String[][] lines = {
                {"1", "Right-click a Propeller Card on a bearing, a wheel mount or a swivel bearing."},
                {"2", "Put that card in a bay's LEFT slot. It names the hardware."},
                {"3", "Right-click a Governor Card on the RPM Governor driving it."},
                {"4", "Put that card in the SAME bay's right slot."},
                {"5", "The bay turns green. Go to RIG and click where that part sits on the craft."},
        };
        for (String[] line : lines) {
            if (row > y + h - 22) break;
            graphics.fill(x + 8, row, x + 20, row + 12, PANEL_SUNK);
            ConsoleDraw.outline(graphics, x + 8, row, 12, 12, BRASS);
            graphics.drawString(font, line[0], x + 12, row + 3, BRASS, false);
            int used = drawWrapped(graphics, line[1], x + 26, row + 2, w - 36, 2, TEXT_DIM);
            row += Math.max(18, used * 10 + 8);
        }
        if (row < y + h - 24) {
            graphics.fill(x + 8, row, x + w - 8, row + 1, EDGE);
            drawWrapped(graphics, "A control surface needs a governor too. It is turned by its own "
                            + "cogwheel, so the console drives that cogwheel to hold an angle.",
                    x + 8, row + 6, w - 16, 3, TEXT_FAINT);
        }
    }

    private void renderSafety(GuiGraphics graphics, int x, int y, int w, int h,
                              VccMk3SnapshotPacket snapshot) {
        if (h < 46) return;
        int row = y + 22;
        graphics.drawString(font, "FLIP GUARD", x + 8, row, TEXT_DIM, false);
        row += 12;
        graphics.fill(x + 8, row, x + w - 8, row + 16, PANEL_SUNK);
        ConsoleDraw.outline(graphics, x + 8, row, w - 16, 16, snapshot.flipGuardActive() ? BAD : EDGE);
        String guardLabel = flipGuardLabel(snapshot);
        graphics.drawString(font, guardLabel, x + 14, row + 4,
                snapshot.flipGuardActive() ? BAD : TEXT_DIM, false);
        hit(x + 8, row, w - 16, 16, () -> {
            send(VccMk3ActionPacket.Action.SET_FLIP_GUARD, null, nextFlipGuard(snapshot), "");
            click(1.1f);
        }, "Tilt past this angle and the controller cuts everything except lift until the craft "
                + "recovers. Click to cycle: off, 45, 62, 75 degrees.");
        row += 22;

        int lines = Math.max(0, (y + h - 18 - row) / 10);
        if (lines > 0) {
            drawWrapped(graphics, "Past this tilt, every output except lift is cut until the craft "
                            + "rights itself. It is what stops a rolled airship becoming a crater.",
                    x + 8, row, w - 16, Math.min(4, lines), TEXT_FAINT);
        }

        int missing = 0;
        SensorDiscovery.Kind[] kinds = SensorDiscovery.Kind.values();
        for (int i = 0; i < kinds.length; i++) {
            byte state = snapshot.sensorStates()[Math.min(i, snapshot.sensorStates().length - 1)];
            if (state != VccMk3SnapshotPacket.SENSOR_AUTO && state != VccMk3SnapshotPacket.SENSOR_MANUAL) {
                missing++;
            }
        }
        if (missing > 0 && h > 108) {
            String text = missing + (missing == 1 ? " instrument missing" : " instruments missing");
            graphics.drawString(font, text, x + 8, y + h - 16, WARN, false);
            hit(x + 8, y + h - 18, w - 16, 12, null,
                    "Readings without a sensor are estimated from physics and badged as such. "
                            + "The flip guard needs a Gimbal Sensor to know which way is up.");
        }
    }

    private void renderRemote(GuiGraphics graphics, int x, int y, int w, int h,
                              VccMk3SnapshotPacket snapshot) {
        if (h < 50) return;
        int listening = 0;
        int transmitting = 0;
        for (int row = 0; row < VccMk3BlockEntity.ROW_COUNT; row++) {
            if ((snapshot.linkRowMask() & (1 << row)) == 0) continue;
            if ((snapshot.linkRxMask() & (1 << row)) != 0) listening++;
            else transmitting++;
        }

        int row = y + 22;
        graphics.drawString(font, "listening", x + 8, row, listening > 0 ? CYAN : TEXT_FAINT, false);
        String text = String.valueOf(listening);
        graphics.drawString(font, text, x + w - 8 - font.width(text), row,
                listening > 0 ? CYAN : TEXT_FAINT, false);
        row += 12;
        graphics.drawString(font, "transmitting", x + 8, row, transmitting > 0 ? TEXT_DIM : TEXT_FAINT, false);
        text = String.valueOf(transmitting);
        graphics.drawString(font, text, x + w - 8 - font.width(text), row,
                transmitting > 0 ? TEXT_DIM : TEXT_FAINT, false);
        row += 16;

        drawWrapped(graphics, "Click a ledger row to make it a signal link. Shift-click a link to "
                        + "make it listen, and a Linked Controller can then fly the craft from "
                        + "outside this console.",
                x + 8, row, w - 16, 5, TEXT_FAINT);
    }

    private void renderSystems(GuiGraphics graphics, int ox, int oy,
                               @Nullable VccMk3SnapshotPacket snapshot, int mouseX, int mouseY) {
        int bodyY = bodyTop();
        int bodyH = bodyHeight();
        int w = canvasW();

        int mixW = Math.max(300, Math.min(420, w * 34 / 100));
        int craftW = w - mixW - PAD * 3;
        panel(graphics, ox + PAD, bodyY + PAD, craftW, bodyH - PAD * 2, "CRAFT");
        int mixX = ox + PAD + craftW + PAD;
        panel(graphics, mixX, bodyY + PAD, mixW, bodyH - PAD * 2, "AXIS MIX", CYAN);

        if (snapshot == null) {
            graphics.drawString(font, "Waiting for the controller.", ox + PAD + 12, bodyY + 40,
                    TEXT_FAINT, false);
            renderStatus(graphics, ox, oy, "No signal from the controller.", WARN, "");
            return;
        }

        int planX = ox + PAD + 8;
        int planY = bodyY + PAD + 18;
        int planW = craftW - 16;
        int planH = (bodyH - PAD * 2 - 32) * 3 / 5;
        renderCraftPlan(graphics, planX, planY, planW, planH, snapshot, mouseX, mouseY);

        int elevY = planY + planH + 8;
        int elevH = bodyY + PAD + bodyH - PAD * 2 - 8 - elevY;
        renderCraftElevation(graphics, planX, elevY, planW, elevH, snapshot);

        renderAxisMix(graphics, mixX, bodyY + PAD, mixW, bodyH - PAD * 2, snapshot);

        renderStatus(graphics, ox, oy,
                "Everything on this page is measured from the craft, not authored. "
                        + "If it disagrees with RIG, the craft has changed since you rigged it.",
                TEXT_DIM, "hover a device for its offset");
    }

    private void renderAxisMix(GuiGraphics graphics, int x, int y, int w, int h,
                               VccMk3SnapshotPacket snapshot) {
        Airframe frame = Airframe.byOrdinal(snapshot.airframeOrdinal());
        Map<UUID, String> assigned = assignmentMap(snapshot);
        Map<UUID, VccMk3SnapshotPacket.DeviceEntry> byId = new HashMap<>();
        for (VccMk3SnapshotPacket.DeviceEntry device : snapshot.devices()) byId.put(device.id(), device);

        int row = y + 20;
        for (ControlAxis axis : ControlAxis.values()) {
            if (!hasSlotFor(frame, axis)) continue;
            if (row > y + h - 30) break;

            float authority = snapshot.authority(axis);
            float demand = snapshot.demand(axis);
            boolean absent = Float.isNaN(authority);

            graphics.fill(x + 6, row, x + w - 6, row + 15, PANEL_SUNK);
            graphics.fill(x + 6, row, x + 9, row + 15, absent ? EDGE : axis.color());
            graphics.drawString(font, axis.label().toUpperCase(Locale.ROOT), x + 14, row + 4,
                    absent ? TEXT_FAINT : axis.color(), false);
            String ask = String.format(Locale.ROOT, "asking %+.0f%%", demand * 100);
            graphics.drawString(font, ask, x + w - 12 - font.width(ask), row + 4,
                    Math.abs(demand) > 1.0e-3 ? TEXT : TEXT_FAINT, false);
            row += 18;

            int listed = 0;
            for (SlotSpec spec : frame.slotList()) {
                if (spec.axis() != axis) continue;
                VccMk3SnapshotPacket.SlotReport report = snapshot.slotReport(spec.key());
                if (report == null || report.state() == VccMk3SnapshotPacket.SLOT_EMPTY) continue;
                if (row > y + h - 16) break;

                String name = spec.label();
                for (Map.Entry<UUID, String> entry : assigned.entrySet()) {
                    if (!spec.key().equals(entry.getValue())) continue;
                    VccMk3SnapshotPacket.DeviceEntry device = byId.get(entry.getKey());
                    if (device != null) name = spec.shortLabel() + "  " + device.label();
                    break;
                }

                boolean fault = report.state() == VccMk3SnapshotPacket.SLOT_FAULT;
                graphics.drawString(font, trim(name, w - 150), x + 16, row,
                        fault ? BAD : TEXT_DIM, false);
                ConsoleDraw.signedBar(graphics, x + w - 136, row - 1, 92, 9,
                        report.gain() * (Math.abs(demand) > 1.0e-3 ? demand : 1.0f),
                        0xFF0A0E13, EDGE, fault ? BAD : axis.color());
                String gain = fault ? "none" : String.format(Locale.ROOT, "%+.2f", report.gain());
                graphics.drawString(font, gain, x + w - 38, row, fault ? BAD : TEXT_FAINT, false);
                hit(x + 14, row - 2, w - 24, 11, null, fault
                        ? spec.label() + ": " + report.note()
                        : spec.label() + " contributes " + String.format(Locale.ROOT, "%.0f%%", report.gain() * 100)
                                + " of full travel to " + axis.label().toLowerCase(Locale.ROOT)
                                + (report.gain() < 0 ? ", in the opposite sense to its partner." : "."));
                row += 11;
                listed++;
            }
            if (listed == 0) {
                String line = absent || !snapshot.axisUsable(axis)
                        ? capitalise(snapshot.axisReport(axis).note())
                        : capitalise(snapshot.axisReport(axis).note()) + ", listed above";
                graphics.drawString(font, trim(line, w - 28), x + 16, row, TEXT_FAINT, false);
                row += 11;
            }
            row += 6;
        }

        if (row < y + h - 44) {
            VccMk3SnapshotPacket.Coupling coupling = snapshot.coupling();
            int colour = coupling.clean() ? GOOD : WARN;
            graphics.fill(x + 6, y + h - 44, x + w - 6, y + h - 43, EDGE);
            graphics.fill(x + 6, y + h - 36, x + w - 6, y + h - 8,
                    ConsoleDraw.blend(0xFF0A0E13, colour, 0.10f));
            ConsoleDraw.outline(graphics, x + 6, y + h - 36, w - 12, 28, colour);
            graphics.drawString(font, coupling.title(), x + 12, y + h - 31, colour, false);
            graphics.drawString(font, trim(coupling.detail(), w - 24), x + 12, y + h - 20,
                    TEXT_DIM, false);
        }
    }

    private static int[] cockpitFrame(VccMk3SnapshotPacket snapshot, int relX, int relZ) {
        net.minecraft.core.Direction[] all = net.minecraft.core.Direction.values();
        net.minecraft.core.Direction facing = snapshot.facingOrdinal() >= 0
                && snapshot.facingOrdinal() < all.length
                ? all[snapshot.facingOrdinal()] : net.minecraft.core.Direction.NORTH;
        return switch (facing) {
            case SOUTH -> new int[] { -relX, -relZ };
            case EAST -> new int[] { relZ, -relX };
            case WEST -> new int[] { -relZ, relX };

            default -> new int[] { relX, relZ };
        };
    }

    private record PlanScale(double scale, int minRight, int maxRight, int minAft, int maxAft) {}

    private static PlanScale planScale(VccMk3SnapshotPacket snapshot, int w, int h) {
        int minRight = 0;
        int maxRight = 0;
        int minAft = 0;
        int maxAft = 0;
        for (VccMk3SnapshotPacket.DeviceEntry device : snapshot.devices()) {
            int[] frame = cockpitFrame(snapshot, device.relX(), device.relZ());
            minRight = Math.min(minRight, frame[0]);
            maxRight = Math.max(maxRight, frame[0]);
            minAft = Math.min(minAft, frame[1]);
            maxAft = Math.max(maxAft, frame[1]);
        }

        int spanRight = Math.max(6, maxRight - minRight);
        int spanAft = Math.max(6, maxAft - minAft);
        double scale = Math.min((w - 40) / (double) spanRight, (h - 40) / (double) spanAft);
        return new PlanScale(Math.max(1.5, Math.min(18.0, scale)), minRight, maxRight, minAft, maxAft);
    }

    private void renderCraftPlan(GuiGraphics graphics, int x, int y, int w, int h,
                                 VccMk3SnapshotPacket snapshot, int mouseX, int mouseY) {
        graphics.fill(x, y, x + w, y + h, 0xFF0A0E13);
        ConsoleDraw.outline(graphics, x, y, w, h, EDGE);

        for (int gx = x + 24; gx < x + w - 1; gx += 24) {
            graphics.fill(gx, y + 1, gx + 1, y + h - 1, 0xFF121820);
        }
        for (int gy = y + 24; gy < y + h - 1; gy += 24) {
            graphics.fill(x + 1, gy, x + w - 1, gy + 1, 0xFF121820);
        }

        List<VccMk3SnapshotPacket.DeviceEntry> devices = snapshot.devices();
        if (devices.isEmpty()) {
            drawWrapped(graphics, "No hardware linked yet. Link a Propeller Card in BUILD and the "
                    + "craft will draw itself here.", x + 10, y + h / 2 - 10, w - 20, 3, TEXT_FAINT);
            return;
        }

        PlanScale plan = planScale(snapshot, w, h);
        int cx = x + w / 2 - (int) Math.round((plan.minRight() + plan.maxRight()) / 2.0 * plan.scale());
        int cy = y + h / 2 - (int) Math.round((plan.minAft() + plan.maxAft()) / 2.0 * plan.scale());

        ConsoleDraw.thickLine(graphics, x + w / 2, y + 6, x + w / 2, y + 15, 2, BRASS);
        graphics.fill(x + w / 2 - 4, y + 13, x + w / 2 + 5, y + 15, BRASS);
        graphics.drawString(font, "FWD", x + w / 2 + 9, y + 7, TEXT_FAINT, false);

        boolean armed = snapshot.armed();
        int offView = 0;
        for (VccMk3SnapshotPacket.DeviceEntry device : devices) {
            int[] frame = cockpitFrame(snapshot, device.relX(), device.relZ());
            int dx = cx + (int) Math.round(frame[0] * plan.scale());
            int dy = cy + (int) Math.round(frame[1] * plan.scale());
            if (dx < x + 4 || dx > x + w - 4 || dy < y + 4 || dy > y + h - 4) {
                offView++;
                continue;
            }
            renderPlanDevice(graphics, dx, dy, device, snapshot, armed);
        }

        graphics.fill(cx - 5, cy - 4, cx + 6, cy + 5, PANEL_RAISED);
        ConsoleDraw.outline(graphics, cx - 5, cy - 4, 11, 9, BRASS);

        int barW = (int) Math.round(5 * plan.scale());
        graphics.fill(x + 8, y + h - 12, x + 8 + barW, y + h - 11, TEXT_FAINT);
        graphics.drawString(font, "5 m", x + 12 + barW, y + h - 16, TEXT_FAINT, false);

        int driven = 0;
        for (VccMk3SnapshotPacket.DeviceEntry device : devices) {
            if (device.actualRpm() != 0) driven++;
        }
        String count = devices.size() + " device" + (devices.size() == 1 ? "" : "s")
                + ", " + driven + " driven";
        graphics.drawString(font, count, x + w - 8 - font.width(count), y + 5, TEXT_FAINT, false);

        if (offView > 0) {
            String warning = offView + " off view, craft too large to fit";
            graphics.drawString(font, warning, x + 8, y + 5, WARN, false);
        }
    }

    private void renderPlanDevice(GuiGraphics graphics, int dx, int dy,
                                  VccMk3SnapshotPacket.DeviceEntry device,
                                  VccMk3SnapshotPacket snapshot, boolean armed) {
        DeviceCategory category = categoryOfDevice(device);
        int colour = slotColourOf(snapshot, device.id(),
                category == null ? EDGE : (category.color() | 0xFF000000));
        int rpm = device.overrideRpm() >= 0 ? device.overrideRpm() : device.actualRpm();
        float level = Math.max(-1f, Math.min(1f, rpm / 256f));

        if (!device.enabled()) {
            ConsoleDraw.outline(graphics, dx - 6, dy - 6, 12, 12, BAD);
            ConsoleDraw.line(graphics, dx - 4, dy - 4, dx + 3, dy + 3, BAD);
            ConsoleDraw.line(graphics, dx + 3, dy - 4, dx - 4, dy + 3, BAD);
            hit(dx - 7, dy - 7, 14, 14, null, device.label() + " is switched off.");
            return;
        }

        float angle = rotorAngle.getOrDefault(device.id(), 0f) + level * 520f * frameDt;
        if (angle > 3600f || angle < -3600f) angle %= 360f;
        rotorAngle.put(device.id(), angle);

        if (category == null) {
            ConsoleDraw.disc(graphics, dx, dy, 3, colour);
        } else {
            switch (category) {
                case PROPELLER -> {
                    if (Math.abs(level) > 0.02f) {
                        int tip = dy - Math.round(level * 26f);
                        ConsoleDraw.thickLine(graphics, dx, dy, dx, tip, 2, colour);
                        int head = level > 0 ? 3 : -3;
                        ConsoleDraw.line(graphics, dx - 3, tip + head, dx, tip, colour);
                        ConsoleDraw.line(graphics, dx + 3, tip + head, dx, tip, colour);
                    }
                    for (int blade = 0; blade < 3; blade++) {
                        int[] end = ConsoleDraw.onCircle(dx, dy, 7, angle + blade * 120.0);
                        ConsoleDraw.line(graphics, dx, dy, end[0], end[1], colour);
                    }
                    ConsoleDraw.disc(graphics, dx, dy, 3, colour);
                }
                case CONTROL_SURFACE -> {
                    ConsoleDraw.line(graphics, dx - 8, dy, dx + 8, dy, EDGE_BRIGHT);
                    int[] end = ConsoleDraw.onCircle(dx, dy, 10, level * 55.0);
                    ConsoleDraw.thickLine(graphics, dx, dy, end[0], end[1], 2, colour);
                    ConsoleDraw.disc(graphics, dx, dy, 2, BRASS);
                }
                case GYRO_BEARING -> {
                    ConsoleDraw.circle(graphics, dx, dy, 8, colour);
                    for (int i = 0; i < 4; i++) {
                        ConsoleDraw.tick(graphics, dx, dy, 4, 8, angle * 0.4 + i * 90.0, colour);
                    }
                    ConsoleDraw.disc(graphics, dx, dy, 2, colour);
                }
                case WHEEL -> {
                    ConsoleDraw.circle(graphics, dx, dy, 7, colour);
                    for (int spoke = 0; spoke < 4; spoke++) {
                        int[] end = ConsoleDraw.onCircle(dx, dy, 6, angle + spoke * 90.0);
                        ConsoleDraw.line(graphics, dx, dy, end[0], end[1], colour);
                    }
                }
                case SIGNAL_LINK -> {
                    float pulse = (float) ((Math.sin(animClock * 3.0) + 1.0) * 0.5);
                    ConsoleDraw.thickLine(graphics, dx, dy + 6, dx, dy - 7, 2, colour);
                    ConsoleDraw.circle(graphics, dx, dy - 7, armed ? 2 + Math.round(pulse * 3f) : 2,
                            armed ? colour : EDGE);
                }
            }
        }

        String detail = device.label()
                + (category == null ? "" : ", " + category.label().toLowerCase(Locale.ROOT))
                + ". " + (rpm == 0 ? "Not turning."
                        : (device.overrideRpm() >= 0 ? "Pinned at " : "Turning at ") + rpm + " rpm.")
                + " Bay " + (device.cardRow() + 1) + ", "
                + describeOffset(snapshot, device) + ".";
        hit(dx - 9, dy - 9, 18, 18, null, detail);
    }

    private static String describeOffset(VccMk3SnapshotPacket snapshot,
                                         VccMk3SnapshotPacket.DeviceEntry device) {
        int[] frame = cockpitFrame(snapshot, device.relX(), device.relZ());
        int right = frame[0];
        int aft = frame[1];
        int up = device.relY();
        StringBuilder out = new StringBuilder();
        if (aft != 0) out.append(Math.abs(aft)).append(aft < 0 ? " forward" : " aft");
        if (right != 0) {
            if (out.length() > 0) out.append(", ");
            out.append(Math.abs(right)).append(right < 0 ? " to port" : " to starboard");
        }
        if (up != 0) {
            if (out.length() > 0) out.append(", ");
            out.append(Math.abs(up)).append(up < 0 ? " below" : " above");
        }
        return out.length() == 0 ? "at the console" : out.toString();
    }

    private void renderCraftElevation(GuiGraphics graphics, int x, int y, int w, int h,
                                      VccMk3SnapshotPacket snapshot) {
        if (h < 24) return;
        graphics.fill(x, y, x + w, y + h, 0xFF0A0E13);
        ConsoleDraw.outline(graphics, x, y, w, h, EDGE);
        graphics.drawString(font, "ELEVATION", x + 6, y + 4, TEXT_FAINT, false);

        List<VccMk3SnapshotPacket.DeviceEntry> devices = snapshot.devices();
        if (devices.isEmpty()) return;

        int minY = 0;
        int maxY = 0;
        for (VccMk3SnapshotPacket.DeviceEntry device : devices) {
            minY = Math.min(minY, device.relY());
            maxY = Math.max(maxY, device.relY());
        }
        int spanY = Math.max(4, maxY - minY);
        PlanScale plan = planScale(snapshot, w, 218);

        int deck = y + h / 2;
        graphics.fill(x + 1, deck, x + w - 1, deck + 1, 0xFF1A2230);
        double verticalScale = (h - 20) / (double) spanY;

        int cx = x + w / 2 - (int) Math.round((plan.minAft() + plan.maxAft()) / 2.0 * plan.scale());
        for (VccMk3SnapshotPacket.DeviceEntry device : devices) {
            int[] frame = cockpitFrame(snapshot, device.relX(), device.relZ());
            int ex = cx + (int) Math.round(frame[1] * plan.scale());
            int ey = deck - (int) Math.round(device.relY() * verticalScale);
            if (ex < x + 3 || ex > x + w - 3 || ey < y + 3 || ey > y + h - 3) continue;
            DeviceCategory category = categoryOfDevice(device);
            int colour = device.enabled()
                    ? slotColourOf(snapshot, device.id(),
                            category == null ? EDGE : (category.color() | 0xFF000000))
                    : BAD;
            ConsoleDraw.disc(graphics, ex, ey, 2, colour);
        }
        graphics.drawString(font, "deck", x + w - 8 - font.width("deck"), deck - 10, TEXT_FAINT, false);
    }

    private static DeviceCategory categoryOfDevice(VccMk3SnapshotPacket.DeviceEntry device) {
        DeviceCategory[] all = DeviceCategory.values();
        return (device.category() >= 0 && device.category() < all.length)
                ? all[device.category()] : null;
    }

    private static int slotColourOf(VccMk3SnapshotPacket snapshot, UUID deviceId, int fallback) {
        Airframe frame = Airframe.byOrdinal(snapshot.airframeOrdinal());
        for (VccMk3SnapshotPacket.AssignEntry entry : snapshot.assignments()) {
            if (!entry.deviceId().equals(deviceId)) continue;
            SlotSpec spec = frame.slot(entry.slotKey());
            if (spec != null) return spec.axis().color();
        }
        return fallback;
    }

    private void renderRig(GuiGraphics graphics, int ox, int oy,
                           @Nullable VccMk3SnapshotPacket snapshot, int mouseX, int mouseY) {
        int bodyY = bodyTop();
        int bodyH = bodyHeight();
        int w = canvasW();

        if (snapshot == null) {
            panel(graphics, ox + PAD, bodyY + PAD, w - PAD * 2, bodyH - PAD * 2, "AIRFRAME", BRASS);
            graphics.drawString(font, "No signal from the controller.", ox + 24, bodyY + 40,
                    TEXT_FAINT, false);
            renderStatus(graphics, ox, oy, "No signal from the controller.", WARN, "");
            return;
        }

        Airframe frame = Airframe.byOrdinal(snapshot.airframeOrdinal());

        int leftW = leftCol();
        int rightColW = rightCol();
        renderRigHardware(graphics, ox + PAD, bodyY + PAD,
                leftW - PAD * 2, bodyH - PAD * 2, snapshot, frame, mouseX, mouseY);

        int boardX = ox + leftW;
        int boardW = w - leftW - rightColW;
        renderRigBoard(graphics, boardX, bodyY + PAD, boardW, bodyH - PAD * 2,
                snapshot, frame, mouseX, mouseY);

        int rightX = ox + w - rightColW + PAD;
        int rightW = rightColW - PAD * 2;
        int inspectorH = Math.min(310, bodyH / 2);
        renderRigInspector(graphics, rightX, bodyY + PAD, rightW, inspectorH, snapshot, frame);
        renderRigAuthority(graphics, rightX, bodyY + PAD + inspectorH + PAD, rightW,
                bodyH - inspectorH - PAD * 3, snapshot, frame);

        renderRigStatus(graphics, ox, oy, snapshot, frame);
    }

    private void renderRigHardware(GuiGraphics graphics, int x, int y, int w, int h,
                                   VccMk3SnapshotPacket snapshot, Airframe frame,
                                   int mouseX, int mouseY) {
        panel(graphics, x, y, w, h, "BOUND HARDWARE", CYAN);
        List<VccMk3SnapshotPacket.DeviceEntry> all = snapshot.devices();
        String total = String.valueOf(all.size());
        graphics.drawString(font, total, x + w - 8 - font.width(total), y + 5, TEXT_FAINT, false);

        Map<UUID, String> assigned = assignmentMap(snapshot);
        int onBoard = 0;
        int faulty = 0;
        for (VccMk3SnapshotPacket.DeviceEntry device : all) {
            if (assigned.containsKey(device.id())) onBoard++;
            if (!device.note().isEmpty() && assigned.containsKey(device.id())) faulty++;
        }
        int spare = all.size() - onBoard;

        int inner = y + 16;
        graphics.fill(x + 4, inner + 3, x + w - 4, inner + 19, PANEL);
        graphics.drawString(font, onBoard + " on the craft", x + 9, inner + 7, GOOD, false);
        String spareText = spare + " spare";
        graphics.drawString(font, spareText, x + w - 9 - font.width(spareText), inner + 7,
                spare > 0 ? WARN : TEXT_FAINT, false);

        List<VccMk3SnapshotPacket.DeviceEntry> shown = new ArrayList<>();
        for (VccMk3SnapshotPacket.DeviceEntry device : all) {
            boolean fitted = assigned.containsKey(device.id());
            boolean broken = !device.note().isEmpty();
            switch (hardwareFilter) {
                case SPARE -> { if (!fitted) shown.add(device); }
                case FAULT -> { if (broken) shown.add(device); }
                default -> shown.add(device);
            }
        }

        int rowH = 36;
        int listTop = inner + 24;
        int listBottom = y + h - 30;
        int maxRows = Math.max(1, (listBottom - listTop) / rowH);
        rigDeviceScroll = Math.max(0, Math.min(rigDeviceScroll, Math.max(0, shown.size() - maxRows)));
        rigDeviceBounds = listBounds(x + 4, listTop, w - 8, listBottom - listTop, shown.size(), maxRows);

        int rowY = listTop;
        for (int i = rigDeviceScroll; i < shown.size() && (i - rigDeviceScroll) < maxRows; i++) {
            VccMk3SnapshotPacket.DeviceEntry device = shown.get(i);
            boolean selected = device.id().equals(selectedDevice);
            boolean hovered = mouseX >= x + 4 && mouseX < x + w - 4
                    && mouseY >= rowY && mouseY < rowY + rowH - 3;
            graphics.fill(x + 4, rowY, x + w - 4, rowY + rowH - 3,
                    selected ? PANEL_RAISED : (hovered ? PANEL_LIVE : 0xFF0A0E13));
            if (selected) ConsoleDraw.outline(graphics, x + 4, rowY, w - 8, rowH - 3, CYAN);

            DeviceCategory category = DeviceCategory.values()[
                    Math.max(0, Math.min(DeviceCategory.values().length - 1, device.category()))];
            int kindColour = category.color();
            graphics.fill(x + 9, rowY + 6, x + 21, rowY + 18,
                    ConsoleDraw.blend(0xFF0A0E13, kindColour, 0.35f));
            ConsoleDraw.outline(graphics, x + 9, rowY + 6, 12, 12, kindColour);
            graphics.drawString(font, trim(device.label(), 92), x + 27, rowY + 5,
                    selected ? TEXT : TEXT_DIM, false);
            String pos = device.relX() + ", " + device.relY() + ", " + device.relZ();
            graphics.drawString(font, pos, x + w - 10 - font.width(pos), rowY + 5, TEXT_FAINT, false);

            String slotKey = assigned.get(device.id());
            if (slotKey == null) {
                graphics.drawString(font, trim(device.note().isEmpty()
                                ? "spare, click a part of the craft" : device.note(), w - 36),
                        x + 27, rowY + 18, WARN, false);
            } else if (!device.note().isEmpty()) {
                SlotSpec spec = frame.slot(slotKey);
                graphics.fill(x + 27, rowY + 19, x + 33, rowY + 25, BAD);
                graphics.drawString(font, trim((spec == null ? slotKey : spec.label())
                        + ", cannot act", w - 52), x + 38, rowY + 18, BAD, false);
            } else {
                SlotSpec spec = frame.slot(slotKey);
                graphics.fill(x + 27, rowY + 19, x + 33, rowY + 25, GOOD);
                graphics.drawString(font, trim(spec == null ? slotKey : spec.label(), w - 52),
                        x + 38, rowY + 18, TEXT_FAINT, false);
            }

            UUID id = device.id();
            String note = device.note();
            hit(x + 4, rowY, w - 8, rowH - 3,
                    () -> {
                        selectedDevice = id.equals(selectedDevice) ? null : id;
                        click(1.0f);
                    },
                    note.isEmpty()
                            ? "Select, then click the part of the craft this is."
                            : note);
            rowY += rowH;
        }

        if (shown.isEmpty()) {
            String empty = switch (hardwareFilter) {
                case SPARE -> "Everything is fitted.";
                case FAULT -> "Nothing is faulty.";
                default -> "No hardware bound. See HARDWARE.";
            };
            graphics.drawString(font, empty, x + 10, listTop + 6,
                    hardwareFilter == HardwareFilter.ALL ? WARN : GOOD, false);
        }
        if (shown.size() > maxRows) {
            scrollbar(graphics, x + w - 7, listTop, 3, maxRows * rowH,
                    shown.size(), rigDeviceScroll, maxRows, EDGE_BRIGHT);
        }

        int fy = y + h - 26;
        graphics.fill(x + 4, fy, x + w - 4, fy + 22, PANEL);
        ConsoleDraw.outline(graphics, x + 4, fy, w - 8, 22, EDGE);
        int fx = x + 10;
        for (HardwareFilter filter : HardwareFilter.values()) {
            boolean on = filter == hardwareFilter;
            int count = switch (filter) {
                case SPARE -> spare;
                case FAULT -> faulty;
                default -> all.size();
            };
            String label = filter.label + (filter == HardwareFilter.ALL ? "" : " " + count);
            int tw = font.width(label) + 12;
            int accent = filter == HardwareFilter.FAULT && count > 0 ? BAD
                    : (filter == HardwareFilter.SPARE && count > 0 ? WARN : CYAN);
            graphics.fill(fx, fy + 4, fx + tw, fy + 18, on ? PANEL_SUNK : PANEL_RAISED);
            ConsoleDraw.outline(graphics, fx, fy + 4, tw, 14, on ? accent : EDGE);
            graphics.drawString(font, label, fx + 6, fy + 8, on ? accent : TEXT_FAINT, false);
            hit(fx, fy + 4, tw, 14, () -> {
                hardwareFilter = filter;
                rigDeviceScroll = 0;
                click(1.1f);
            }, switch (filter) {
                case SPARE -> "Only hardware that is not on the craft yet.";
                case FAULT -> "Only hardware that is fitted and cannot do its job.";
                default -> "Everything this controller can command.";
            });
            fx += tw + 5;
        }
    }

    private void renderRigBoard(GuiGraphics graphics, int x, int y, int w, int h,
                                VccMk3SnapshotPacket snapshot, Airframe frame,
                                int mouseX, int mouseY) {
        panel(graphics, x, y, w, h, "AIRFRAME", BRASS);

        int ax = x + w - 8;
        Airframe[] all = Airframe.values();
        for (int i = all.length - 1; i >= 0; i--) {
            Airframe candidate = all[i];
            String label = candidate.shortLabel();
            int tw = font.width(label) + 12;
            ax -= tw + 3;
            boolean on = candidate == frame;
            graphics.fill(ax, y + 2, ax + tw, y + 14, on ? PANEL_SUNK : PANEL_RAISED);
            ConsoleDraw.outline(graphics, ax, y + 2, tw, 12, on ? BRASS : EDGE);
            graphics.drawString(font, label, ax + 6, y + 5, on ? BRASS : TEXT_FAINT, false);
            int ordinal = candidate.ordinal();
            hit(ax, y + 2, tw, 12, () -> {
                send(VccMk3ActionPacket.Action.SET_AIRFRAME, null, ordinal, "");
                selectedSlot = null;
                click(1.0f);
            }, candidate.label() + ". Changing this changes the picture and the names, and creates "
                    + "nothing. Assignments that still fit are kept.");
        }

        int bx = x + 1;
        int by = y + 16;
        int bw = w - 2;
        int bh = h - 17;

        RigBoardRenderer.BoardState state = buildBoardState(snapshot, frame, mouseX, mouseY, bx, by, bw, bh);
        Map<String, RigShapes.SlotShape> shapes =
                RigBoardRenderer.draw(graphics, font, bx, by, bw, bh, state);
        rigSlotShapes = shapes;

        if (frame.isFreeform()) {
            for (Map.Entry<UUID, int[]> plotted : RigBoardRenderer.LAST_PLOT_HITS.entrySet()) {
                int[] r = plotted.getValue();
                UUID id = plotted.getKey();
                hit(r[0], r[1], r[2], r[3], () -> onPlotClicked(id),
                        "Click to assign this to the function picked on the left.");
            }
            drawFunctionPalette(graphics, bx + 8, by + 8, frame, snapshot);
        } else {
            drawCentreOfMass(graphics, bx, by, bw, bh, snapshot, frame, shapes);
        }

        for (Map.Entry<String, RigShapes.SlotShape> entry : shapes.entrySet()) {
            RigShapes.SlotShape shape = entry.getValue();
            int x0 = boundsMinX(shape);
            int y0 = boundsMinY(shape);
            int x1 = boundsMaxX(shape);
            int y1 = boundsMaxY(shape);
            String key = entry.getKey();
            SlotSpec spec = frame.slot(key);
            VccMk3SnapshotPacket.SlotReport report = snapshot.slotReport(key);
            String tip;
            if (spec == null) {
                tip = key;
            } else if (report != null && report.state() == VccMk3SnapshotPacket.SLOT_FAULT) {
                tip = spec.label() + ": " + report.note();
            } else if (report != null && report.state() == VccMk3SnapshotPacket.SLOT_EMPTY) {
                tip = spec.label() + ", empty. Serves " + spec.axis().label().toLowerCase(Locale.ROOT)
                        + ". Pick a device on the left, then click here.";
            } else {
                tip = spec.label() + ", serving " + spec.axis().label().toLowerCase(Locale.ROOT) + ".";
            }
            hit(x0 - 3, y0 - 3, x1 - x0 + 6, y1 - y0 + 6, () -> onSlotClicked(key), tip);
        }

        drawRigCards(graphics, bx, by, bx + bw, by + bh, snapshot, frame);
        drawRigTest(graphics, bx, by + bh - 38, bw, snapshot, frame);
        drawRigLegend(graphics, bx, by + bh - 16, bw, frame);
    }

    private void drawRigTest(GuiGraphics graphics, int x, int y, int w,
                             VccMk3SnapshotPacket snapshot, Airframe frame) {
        List<ControlAxis> testable = new ArrayList<>();
        for (ControlAxis axis : ControlAxis.values()) {
            if (axis == ControlAxis.AUX) continue;
            if (hasSlotFor(frame, axis)) testable.add(axis);
        }
        if (testable.isEmpty()) return;

        String lead = testAxis == null ? "TEST" : "TESTING";
        int total = font.width(lead) + 10;
        for (ControlAxis axis : testable) total += font.width(axis.shortLabel()) + 16;

        int lx = x + (w - total) / 2;
        if (lx < x + 4) return;
        graphics.drawString(font, lead, lx, y + 5, testAxis == null ? TEXT_FAINT : CYAN, false);
        lx += font.width(lead) + 10;

        for (ControlAxis axis : testable) {
            boolean on = axis == testAxis;
            boolean usable = snapshot.axisUsable(axis);
            int tw = font.width(axis.shortLabel()) + 12;
            graphics.fill(lx, y, lx + tw, y + 15, on ? PANEL_RAISED : PANEL_SUNK);
            ConsoleDraw.outline(graphics, lx, y, tw, 15,
                    on ? axis.color() : (usable ? EDGE : 0xFF2A3440));
            graphics.drawString(font, axis.shortLabel(), lx + 6, y + 4,
                    on ? axis.color() : (usable ? TEXT_FAINT : 0xFF3A4757), false);
            ControlAxis target = axis;
            hit(lx, y, tw, 15, () -> {
                testAxis = target.equals(testAxis) ? null : target;
                testClock = 0f;
                click(1.2f);
            }, usable
                    ? "Sweep " + axis.label().toLowerCase(Locale.ROOT)
                            + " and watch the craft respond. Nothing moves: this drives the picture "
                            + "only, so it is safe while parked."
                    : axis.label() + " has no authority, so a sweep would show nothing moving. "
                            + "Fit hardware to it first.");
            lx += tw + 4;
        }
    }

    private void drawCentreOfMass(GuiGraphics graphics, int bx, int by, int bw, int bh,
                                  VccMk3SnapshotPacket snapshot, Airframe frame,
                                  Map<String, RigShapes.SlotShape> shapes) {
        VccMk3SnapshotPacket.RigKinetics k = snapshot.kinetics();
        int cx = bx + bw / 2;
        int cy = by + bh / 2;

        int comX = cx;
        int comY = cy;
        if (Float.isFinite(k.comForward())) {
            comY = cy - Math.round(k.comForward() * (bh / 26f));
            comY = Math.max(by + 30, Math.min(by + bh - 30, comY));
        }

        ConsoleDraw.disc(graphics, comX, comY, 7, ConsoleDraw.blend(0xFF0A0E13, BRASS, 0.25f));

        for (int dy = -7; dy <= 7; dy++) {
            int span = (int) Math.floor(Math.sqrt(49.0 - dy * dy));
            if (span < 0) continue;
            if (dy >= 0) {
                graphics.fill(comX, comY + dy, comX + span + 1, comY + dy + 1, BRASS);
            } else {
                graphics.fill(comX - span, comY + dy, comX + 1, comY + dy + 1, BRASS);
            }
        }
        ConsoleDraw.circle(graphics, comX, comY, 8, BRASS);
        graphics.drawString(font, "CoM", comX + 12, comY - 3, BRASS, false);
        hit(comX - 9, comY - 9, 18, 18, null, Float.isFinite(k.comForward())
                ? String.format(Locale.ROOT,
                        "Centre of mass: %.1f blocks %s of the console, %.1f %s it. "
                                + "Every moment on this board is measured about this point.",
                        Math.abs(k.comForward()), k.comForward() >= 0 ? "forward" : "aft",
                        Math.abs(k.comUp()), k.comUp() >= 0 ? "above" : "below")
                : "No centre of mass yet. Assemble the craft and this becomes the real one.");

        if (selectedSlot == null) return;
        RigShapes.SlotShape shape = shapes.get(selectedSlot);
        VccMk3SnapshotPacket.SlotReport report = snapshot.slotReport(selectedSlot);
        if (shape == null || report == null
                || report.state() == VccMk3SnapshotPacket.SLOT_EMPTY) return;

        int tx = shape.anchorX();
        int ty = shape.anchorY();
        ConsoleDraw.dashedLine(graphics, comX, comY, tx, ty,
                ConsoleDraw.blend(BRASS, 0xFF0A0E13, 0.45f), 4, 4);

        if (Float.isFinite(report.arm())) {
            int mx = comX + (tx - comX) / 3;
            int my = comY + (ty - comY) / 3;
            double dx = tx - comX;
            double dy = ty - comY;
            double length = Math.max(1.0, Math.hypot(dx, dy));
            double px = -dy / length;
            double py = dx / length;
            String label = String.format(Locale.ROOT, "arm %.1f", report.arm());
            int lx = (int) (mx + px * 22) - (px < 0 ? font.width(label) : 0);
            graphics.drawString(font, label, lx, (int) (my + py * 22) - 3,
                    ConsoleDraw.blend(BRASS, TEXT_DIM, 0.4f), false);
        }
    }

    private void drawFunctionPalette(GuiGraphics graphics, int x, int y, Airframe frame,
                                     VccMk3SnapshotPacket snapshot) {
        Map<String, Integer> counts = new HashMap<>();
        for (VccMk3SnapshotPacket.AssignEntry entry : snapshot.assignments()) {
            counts.merge(entry.slotKey(), 1, Integer::sum);
        }

        int w = 140;
        int rowH = 22;
        int h = 20 + frame.slotList().size() * rowH + 6;
        graphics.fill(x, y, x + w, y + h, 0xFF0C1117);
        ConsoleDraw.outline(graphics, x, y, w, h, EDGE);
        graphics.fill(x + 1, y + 1, x + w - 1, y + 13, 0xFF141B24);
        graphics.fill(x + 1, y + 13, x + w - 1, y + 14, CYAN);
        graphics.drawString(font, "FUNCTION", x + 6, y + 4, TEXT_DIM, false);

        int row = y + 18;
        for (SlotSpec spec : frame.slotList()) {
            boolean on = spec.key().equals(selectedSlot);
            int colour = spec.axis().color();
            graphics.fill(x + 4, row, x + w - 4, row + rowH - 3, on ? PANEL_RAISED : PANEL_SUNK);
            ConsoleDraw.outline(graphics, x + 4, row, w - 8, rowH - 3, on ? colour : EDGE);
            graphics.fill(x + 7, row + 5, x + 13, row + 11, colour);
            graphics.drawString(font, spec.label(), x + 18, row + 5, on ? TEXT : TEXT_DIM, false);
            int count = counts.getOrDefault(spec.key(), 0);
            String tally = count == 0 ? "-" : String.valueOf(count);
            graphics.drawString(font, tally, x + w - 10 - font.width(tally), row + 5,
                    count == 0 ? TEXT_FAINT : colour, false);
            String key = spec.key();
            hit(x + 4, row, w - 8, rowH - 3, () -> {
                selectedSlot = key.equals(selectedSlot) ? null : key;
                click(1.0f);
            }, "Pick this function, then click hardware on the board.");
            row += rowH;
        }
    }

    private void onPlotClicked(UUID deviceId) {
        if (selectedSlot != null) {
            send(VccMk3ActionPacket.Action.ASSIGN_SLOT, deviceId, 0.0, selectedSlot);
            click(1.2f);
            return;
        }
        selectedDevice = deviceId.equals(selectedDevice) ? null : deviceId;
        click(1.0f);
    }

    private void onSlotClicked(String slotKey) {
        if (selectedDevice != null) {
            send(VccMk3ActionPacket.Action.ASSIGN_SLOT, selectedDevice, 0.0, slotKey);
            selectedDevice = null;
            selectedSlot = slotKey;
            click(1.2f);
            return;
        }
        selectedSlot = slotKey.equals(selectedSlot) ? null : slotKey;
        click(1.0f);
    }

    private RigBoardRenderer.BoardState buildBoardState(VccMk3SnapshotPacket snapshot, Airframe frame,
                                                        int mouseX, int mouseY,
                                                        int bx, int by, int bw, int bh) {
        Map<UUID, String> assigned = assignmentMap(snapshot);

        Map<String, RigBoardRenderer.SlotView> views = new HashMap<>();
        for (SlotSpec spec : frame.slotList()) {
            VccMk3SnapshotPacket.SlotReport report = snapshot.slotReport(spec.key());
            if (report == null) {
                views.put(spec.key(), new RigBoardRenderer.SlotView(spec.key(),
                        RigBoardRenderer.SlotView.State.EMPTY, 0f, 0, 0f, ""));
                continue;
            }
            RigBoardRenderer.SlotView.State slotState = switch (report.state()) {
                case VccMk3SnapshotPacket.SLOT_OK -> RigBoardRenderer.SlotView.State.FILLED;
                case VccMk3SnapshotPacket.SLOT_FAULT -> RigBoardRenderer.SlotView.State.FAULT;
                default -> RigBoardRenderer.SlotView.State.EMPTY;
            };

            float deflection = report.gain() * axisDrive(snapshot, spec.axis());
            views.put(spec.key(), new RigBoardRenderer.SlotView(spec.key(), slotState,
                    Math.max(-1f, Math.min(1f, deflection)), report.deviceCount(),
                    report.rpmFraction(), report.note()));
        }

        String hovered = null;
        if (rigSlotShapes != null) {
            for (Map.Entry<String, RigShapes.SlotShape> entry : rigSlotShapes.entrySet()) {
                if (entry.getValue().contains(mouseX, mouseY)) {
                    hovered = entry.getKey();
                    break;
                }
            }
        }

        float steer = axisDrive(snapshot, ControlAxis.YAW);
        float scroll = (this.frame * 1.6f) % 18f;
        return new RigBoardRenderer.BoardState(frame, views, selectedSlot, hovered, steer, scroll,
                snapshot.armed(), buildPlots(snapshot, assigned));
    }

    private float axisDrive(VccMk3SnapshotPacket snapshot, ControlAxis axis) {
        if (testAxis == axis) return (float) Math.sin(testClock * 2.2);
        return snapshot.demand(axis);
    }

    private List<RigBoardRenderer.DevicePlot> buildPlots(VccMk3SnapshotPacket snapshot,
                                                         Map<UUID, String> assigned) {
        net.minecraft.core.Direction facing = net.minecraft.core.Direction.from3DDataValue(
                Math.max(0, Math.min(5, snapshot.facingOrdinal())));
        if (facing.getAxis().isVertical()) facing = net.minecraft.core.Direction.NORTH;
        int fx = facing.getStepX();
        int fz = facing.getStepZ();

        int rx = -fz;
        int rz = fx;

        List<RigBoardRenderer.DevicePlot> out = new ArrayList<>();
        for (VccMk3SnapshotPacket.DeviceEntry device : snapshot.devices()) {
            double forward = device.relX() * fx + device.relZ() * fz;
            double right = device.relX() * rx + device.relZ() * rz;
            out.add(new RigBoardRenderer.DevicePlot(device.id(), device.label(), device.category(),
                    forward, right, assigned.get(device.id()),
                    Math.min(1f, Math.abs(device.actualRpm()) / 256f),
                    device.id().equals(selectedDevice)));
        }
        return out;
    }

    private Map<UUID, String> assignmentMap(VccMk3SnapshotPacket snapshot) {
        Map<UUID, String> out = new HashMap<>();
        for (VccMk3SnapshotPacket.AssignEntry entry : snapshot.assignments()) {
            out.put(entry.deviceId(), entry.slotKey());
        }
        return out;
    }

    private void drawRigCards(GuiGraphics graphics, int bx0, int by0, int bx1, int by1,
                              VccMk3SnapshotPacket snapshot, Airframe frame) {
        VccMk3SnapshotPacket.RigKinetics k = snapshot.kinetics();
        int cw = 176;
        if (bx1 - bx0 < cw * 2 + 40) return;

        boolean ground = frame == Airframe.GROUND;
        List<String[]> kinetics = new ArrayList<>();
        kinetics.add(new String[] {"mass", fmt(k.massTonnes(), 1, " t"), null});
        kinetics.add(new String[] {ground ? "drive : weight" : "thrust : weight",
                fmt(k.thrustToWeight(), 2, ""),
                Float.isNaN(k.thrustToWeight()) ? null
                        : (k.thrustToWeight() >= 1.0f ? "good" : "warn")});
        if (!Float.isNaN(k.thrustToWeight())) {
            kinetics.add(new String[] {"bar",
                    String.valueOf(Math.min(1f, k.thrustToWeight() / 1.6f)),
                    k.thrustToWeight() >= 1.0f ? "good" : "warn"});
        }
        kinetics.add(new String[] {"governors", String.valueOf(k.governorCount()), null});
        card(graphics, bx0 + 8, by0 + 8, cw, "KINETICS", kinetics, CYAN);

        List<String[]> balance = new ArrayList<>();
        balance.add(new String[] {"roll I", fmt(k.rollInertia(), 0, ""), null});
        balance.add(new String[] {"pitch I", fmt(k.pitchInertia(), 0, ""), null});
        balance.add(new String[] {"yaw I", fmt(k.yawInertia(), 0, ""), null});
        balance.add(new String[] {"rule", null, null});
        balance.add(new String[] {"CoM", Float.isNaN(k.comForward()) ? "---"
                : String.format(Locale.ROOT, "%.1f %s", Math.abs(k.comForward()),
                        k.comForward() >= 0 ? "fwd" : "aft"), null});
        balance.add(new String[] {"CoM height", Float.isNaN(k.comUp()) ? "---"
                : String.format(Locale.ROOT, "%.1f %s", Math.abs(k.comUp()),
                        k.comUp() >= 0 ? "high" : "low"),
                Float.isNaN(k.comUp()) ? null : (k.comUp() > 1.0f ? "warn" : "good")});
        card(graphics, bx1 - 8 - cw, by0 + 8, cw, "MASS & BALANCE", balance, BRASS);

        List<String[]> bus = new ArrayList<>();
        for (VccMk3SnapshotPacket.BusEntry entry : snapshot.bus()) {
            boolean ramping = entry.commanded() != entry.applied();
            bus.add(new String[] {entry.label(), entry.commanded() + " > " + entry.applied(),
                    ramping ? "warn" : "good"});
            if (bus.size() >= 6) break;
        }
        if (bus.isEmpty()) {
            bus.add(new String[] {"no governors", "", null});
        } else {
            bus.add(new String[] {"rule", null, null});
            bus.add(new String[] {"governors", k.governorCount() + " owned", null});
        }
        int busH = cardHeight(bus);
        card(graphics, bx0 + 8, by1 - 8 - busH, cw, "RPM BUS", bus, BRASS);

        List<String[]> stress = new ArrayList<>();
        if (Float.isNaN(k.stressCapacity())) {
            stress.add(new String[] {"no network", "", null});
        } else {
            float used = Math.max(0f, k.stressUsed());
            float capacity = Math.max(0.001f, k.stressCapacity());
            boolean over = used > capacity;
            stress.add(new String[] {"used", Math.round(used) + " su", over ? "bad" : null});
            stress.add(new String[] {"capacity", Math.round(capacity) + " su", null});
            stress.add(new String[] {"bar", String.valueOf(used / capacity), over ? "bad" : "good"});
            stress.add(new String[] {"network", fmt(k.networkRpm(), 0, " rpm"), null});
        }
        int stressH = cardHeight(stress);
        card(graphics, bx1 - 8 - cw, by1 - 8 - stressH, cw, "KINETIC NETWORK", stress, GOOD);
    }

    private int cardHeight(List<String[]> rows) {
        int h = 20 + 6;
        for (String[] row : rows) h += "bar".equals(row[0]) ? 12 : 11;
        return h;
    }

    private void card(GuiGraphics graphics, int x, int y, int w, String title,
                      List<String[]> rows, int accent) {
        int h = cardHeight(rows);
        graphics.fill(x, y, x + w, y + h, 0xFF0C1117);
        ConsoleDraw.outline(graphics, x, y, w, h, EDGE);
        graphics.fill(x + 1, y + 1, x + w - 1, y + 13, 0xFF141B24);
        graphics.fill(x + 1, y + 13, x + w - 1, y + 14, accent);
        graphics.drawString(font, title, x + 6, y + 4, TEXT_DIM, false);

        int row = y + 19;
        for (String[] entry : rows) {
            if ("rule".equals(entry[0])) {
                graphics.fill(x + 6, row + 4, x + w - 6, row + 5, EDGE);
                row += 11;
            } else if ("bar".equals(entry[0])) {
                float fraction = 0f;
                try {
                    fraction = Float.parseFloat(entry[1]);
                } catch (NumberFormatException ignored) {
                }
                ConsoleDraw.bar(graphics, x + 6, row, w - 12, 8, fraction, 0xFF0A0E13, EDGE,
                        toneColour(entry[2], accent));
                row += 12;
            } else {
                graphics.drawString(font, entry[0], x + 6, row, TEXT_FAINT, false);
                String value = entry[1] == null ? "" : entry[1];
                graphics.drawString(font, value, x + w - 6 - font.width(value), row,
                        toneColour(entry[2], TEXT_DIM), false);
                row += 11;
            }
        }
    }

    private static int toneColour(@Nullable String tone, int fallback) {
        if (tone == null) return fallback;
        return switch (tone) {
            case "good" -> GOOD;
            case "warn" -> WARN;
            case "bad" -> BAD;
            default -> fallback;
        };
    }

    private void drawRigLegend(GuiGraphics graphics, int x, int y, int w, Airframe frame) {
        int total = 0;
        for (ControlAxis axis : ControlAxis.values()) {
            total += font.width(axis.label().toLowerCase(Locale.ROOT)) + 26;
        }
        int lx = x + (w - total) / 2;
        for (ControlAxis axis : ControlAxis.values()) {
            String label = axis.label().toLowerCase(Locale.ROOT);
            graphics.fill(lx, y + 2, lx + 8, y + 8, axis.color());
            graphics.drawString(font, label, lx + 12, y + 1, TEXT_FAINT, false);
            lx += font.width(label) + 26;
        }
    }

    private void renderRigInspector(GuiGraphics graphics, int x, int y, int w, int h,
                                    VccMk3SnapshotPacket snapshot, Airframe frame) {
        SlotSpec spec = frame.slot(selectedSlot);
        int accent = spec == null ? EDGE : spec.axis().color();
        panel(graphics, x, y, w, h,
                spec == null ? "SLOT" : "SLOT   " + spec.label().toUpperCase(Locale.ROOT), accent);

        if (spec == null) {
            graphics.drawString(font, "Click a part of the craft.", x + 8, y + 26, TEXT_DIM, false);
            drawWrapped(graphics, "Or pick a device on the left first, then click where it goes. "
                            + "Either order works.", x + 8, y + 40, w - 16, 3, TEXT_FAINT);
            graphics.fill(x + 8, y + 78, x + w - 8, y + 79, EDGE);
            drawWrapped(graphics, "Right-click a part to empty it. Nothing here is destructive: "
                            + "putting a device back is one click.",
                    x + 8, y + 86, w - 16, 3, TEXT_FAINT);
            return;
        }

        Map<UUID, String> assigned = assignmentMap(snapshot);
        List<VccMk3SnapshotPacket.DeviceEntry> inSlot = new ArrayList<>();
        for (VccMk3SnapshotPacket.DeviceEntry device : snapshot.devices()) {
            if (spec.key().equals(assigned.get(device.id()))) inSlot.add(device);
        }
        VccMk3SnapshotPacket.SlotReport report = snapshot.slotReport(spec.key());

        int row = y + 24;
        if (inSlot.isEmpty()) {
            graphics.drawString(font, "empty", x + 8, row, TEXT_FAINT, false);
            String hint = "nothing assigned";
            graphics.drawString(font, hint, x + w - 8 - font.width(hint), row, TEXT_FAINT, false);
        } else {
            String names = inSlot.get(0).label() + (inSlot.size() > 1 ? " +" + (inSlot.size() - 1) : "");
            graphics.drawString(font, trim(names, w - 96), x + 8, row, TEXT, false);
            VccMk3SnapshotPacket.DeviceEntry first = inSlot.get(0);
            String pos = first.relX() + ", " + first.relY() + ", " + first.relZ();
            graphics.drawString(font, pos, x + w - 8 - font.width(pos), row, TEXT_FAINT, false);
        }
        row += 15;
        graphics.fill(x + 8, row, x + w - 8, row + 1, EDGE);
        row += 7;

        row = inspectorField(graphics, x, w, row, "serves", spec.axis().label(), spec.axis().color());
        row = inspectorField(graphics, x, w, row, "pairing",
                switch (spec.symmetry()) {
                    case ANTISYMMETRIC -> "opposes its partner";
                    case SYMMETRIC -> "moves with its partner";
                    case SINGLE -> "on its own";
                }, TEXT_DIM);
        StringBuilder accepts = new StringBuilder();
        for (DeviceCategory category : DeviceCategory.values()) {
            if (!spec.accepts(category)) continue;
            if (accepts.length() > 0) accepts.append(", ");
            accepts.append(category.shortLabel());
        }
        row = inspectorField(graphics, x, w, row, "accepts", accepts.toString(), TEXT_DIM);

        if (report != null && report.state() != VccMk3SnapshotPacket.SLOT_EMPTY) {
            row = inspectorField(graphics, x, w, row, "arm from CoM",
                    Float.isNaN(report.arm()) ? "unknown"
                            : String.format(Locale.ROOT, "%.1f blocks", report.arm()),
                    Float.isNaN(report.arm()) ? TEXT_FAINT : TEXT_DIM);
            row = inspectorField(graphics, x, w, row, "gain on " + spec.axis().shortLabel(),
                    String.format(Locale.ROOT, "%+.2f", report.gain()),
                    Math.abs(report.gain()) > 1.0e-6 ? spec.axis().color() : BAD);
            if (Float.isFinite(report.angleDeg())) {
                row = inspectorField(graphics, x, w, row, "actual angle",
                        String.format(Locale.ROOT, "%+.0f deg", report.angleDeg()), TEXT_DIM);
            }
        }

        row += 5;
        graphics.fill(x + 8, row, x + w - 8, row + 1, EDGE);
        row += 7;

        int notesBottom = y + h - 32;
        if (report != null && report.state() == VccMk3SnapshotPacket.SLOT_FAULT
                && !report.note().isEmpty() && row < notesBottom) {
            row = noteBox(graphics, x, w, row, "This cannot act", wrap(report.note(), w - 28)
                    .toArray(new String[0]), BAD);
        }
        if (row < notesBottom - 20) {
            if (spec.wantsLift()) {
                row = noteBox(graphics, x, w, row, "Needs lifting sails",
                        new String[] {"Create Sails lift. Simulated's",
                                "Symmetric Sails do not, they only",
                                "drag, so this part needs Create Sails."}, WARN);
            } else if (spec.isSurface()) {
                row = noteBox(graphics, x, w, row, "Drag surface",
                        new String[] {"This works on drag, so Simulated's",
                                "Symmetric Sails suit it well."}, TEXT_DIM);
            }
        }

        int by = y + h - 26;
        int bxx = x + 8;
        bxx += inspectorButton(graphics, bxx, by, "CLEAR", !inSlot.isEmpty(), BAD,
                () -> {
                    send(VccMk3ActionPacket.Action.CLEAR_SLOT, null, 0.0, spec.key());
                    click(0.9f);
                });
        if (selectedDevice != null) {
            bxx += inspectorButton(graphics, bxx, by, "FIT HERE", true, GOOD,
                    () -> onSlotClicked(spec.key()));
        }
        if (!inSlot.isEmpty()) {
            VccMk3SnapshotPacket.DeviceEntry first = inSlot.get(0);
            BlockPos where = snapshot.controllerPos().offset(first.relX(), first.relY(), first.relZ());
            boolean reachable = VccMk3Highlight.canShow(where);
            inspectorButton(graphics, bxx, by, "SHOW", reachable, CYAN, () -> {
                VccMk3Highlight.show(where);
                click(1.2f);
                onClose();
            });
            if (!reachable) {
                hit(bxx, by, font.width("SHOW") + 18, 18, null,
                        "The craft is assembled, so this block is inside its own sub level and a "
                                + "box drawn in the world would land somewhere arbitrary. "
                                + "Disassemble to be shown where it is.");
            }
        }
    }

    private int inspectorField(GuiGraphics graphics, int x, int w, int row, String label,
                               String value, int colour) {
        graphics.drawString(font, label, x + 8, row, TEXT_FAINT, false);
        graphics.drawString(font, trim(value, w - 24 - font.width(label)),
                x + w - 8 - font.width(trim(value, w - 24 - font.width(label))), row, colour, false);
        return row + 13;
    }

    private int noteBox(GuiGraphics graphics, int x, int w, int row, String title,
                        String[] lines, int colour) {
        int shown = Math.min(4, lines.length);
        int h = 17 + shown * 11;
        graphics.fill(x + 8, row, x + w - 8, row + h, ConsoleDraw.blend(0xFF0A0E13, colour, 0.13f));
        ConsoleDraw.outline(graphics, x + 8, row, w - 16, h, colour);
        graphics.drawString(font, title, x + 14, row + 5, colour, false);
        for (int i = 0; i < shown; i++) {
            graphics.drawString(font, lines[i], x + 14, row + 18 + i * 11, TEXT_DIM, false);
        }
        return row + h + 7;
    }

    private int inspectorButton(GuiGraphics graphics, int x, int y, String label, boolean enabled,
                                int accent, Runnable action) {
        int w = font.width(label) + 18;
        graphics.fill(x, y, x + w, y + 18, enabled ? PANEL_RAISED : PANEL_SUNK);
        ConsoleDraw.outline(graphics, x, y, w, 18, enabled ? accent : EDGE);
        graphics.drawString(font, label, x + 9, y + 6, enabled ? accent : TEXT_FAINT, false);
        if (enabled) hit(x, y, w, 18, action, null);
        return w + 6;
    }

    private void renderRigAuthority(GuiGraphics graphics, int x, int y, int w, int h,
                                    VccMk3SnapshotPacket snapshot, Airframe frame) {
        panel(graphics, x, y, w, h, "AXIS AUTHORITY", CYAN);
        int row = y + 22;

        for (ControlAxis axis : ControlAxis.values()) {
            if (axis == ControlAxis.AUX) continue;
            if (row > y + h - 70) break;
            float value = snapshot.authority(axis);

            boolean absent = Float.isNaN(value);
            VccMk3SnapshotPacket.AxisEntry report = snapshot.axisReport(axis);

            graphics.drawString(font, axis.shortLabel(), x + 8, row,
                    absent ? TEXT_FAINT : axis.color(), false);
            String readout = absent ? "n/a" : (value <= 0f ? "none" : Math.round(value * 100) + "%");
            int readColour = absent ? TEXT_FAINT : (value > 0f ? axis.color() : BAD);
            graphics.drawString(font, readout, x + w - 8 - font.width(readout), row, readColour, false);
            row += 11;

            if (absent) {
                graphics.fill(x + 8, row, x + w - 8, row + 9, 0xFF0A0E13);
                ConsoleDraw.outline(graphics, x + 8, row, w - 16, 9, EDGE);
                for (int dx = x + 10; dx < x + w - 10; dx += 6) {
                    graphics.fill(dx, row + 4, dx + 3, row + 5, 0xFF243040);
                }
            } else {
                ConsoleDraw.bar(graphics, x + 8, row, w - 16, 9, value, 0xFF0A0E13, EDGE,
                        value > 0f ? axis.color() : BAD);
            }
            row += 12;

            graphics.drawString(font, trim(report.note(), w - 24 - font.width(report.flag())),
                    x + 8, row, TEXT_FAINT, false);
            if (!report.flag().isEmpty()) {
                graphics.drawString(font, report.flag(), x + w - 8 - font.width(report.flag()), row,
                        absent ? TEXT_FAINT : (value > 0f ? WARN : BAD), false);
            }
            hit(x + 8, row - 23, w - 16, 32, null, absent
                    ? capitalise(report.note()) + "."
                    : axis.label() + ": " + capitalise(report.note())
                            + (report.flag().isEmpty() ? "." : ", " + report.flag() + "."));
            row += 15;
        }

        int boxY = y + h - 50;
        if (boxY > row) {
            graphics.drawString(font, "CROSS COUPLING", x + 8, boxY - 14, TEXT_FAINT, false);
            VccMk3SnapshotPacket.Coupling coupling = snapshot.coupling();
            int colour = coupling.clean() ? GOOD : WARN;
            graphics.fill(x + 8, boxY, x + w - 8, boxY + 34,
                    ConsoleDraw.blend(0xFF0A0E13, colour, 0.10f));
            ConsoleDraw.outline(graphics, x + 8, boxY, w - 16, 34, colour);
            graphics.drawString(font, trim(coupling.title(), w - 24), x + 14, boxY + 6, colour, false);
            drawWrapped(graphics, coupling.detail(), x + 14, boxY + 18, w - 28, 1, TEXT_DIM);
            hit(x + 8, boxY, w - 16, 34, null, coupling.clean()
                    ? "No axis pushes another. Asking for one thing gets you one thing."
                    : coupling.title() + ". " + coupling.detail());
        }
    }

    private void renderRigStatus(GuiGraphics graphics, int ox, int oy,
                                 VccMk3SnapshotPacket snapshot, Airframe frame) {
        String message = snapshot.notice();
        int colour = TEXT_DIM;

        if (message == null || message.isEmpty()) {
            int empty = 0;
            int faulty = 0;
            String faultSlot = null;
            String faultNote = "";
            for (VccMk3SnapshotPacket.SlotReport report : snapshot.slotReports()) {
                if (report.state() == VccMk3SnapshotPacket.SLOT_EMPTY) empty++;
                else if (report.state() == VccMk3SnapshotPacket.SLOT_FAULT) {
                    faulty++;
                    if (faultSlot == null) {
                        SlotSpec spec = frame.slot(report.slotKey());
                        faultSlot = spec == null ? report.slotKey() : spec.label();
                        faultNote = report.note();
                    }
                }
            }
            int spare = snapshot.devices().size() - snapshot.assignments().size();

            if (selectedDevice != null) {
                message = "Now click the part of the craft this device is.";
                colour = GOOD;
            } else if (faulty > 0) {
                message = faultSlot + ": " + faultNote;
                colour = BAD;
            } else if (spare > 0) {
                message = spare + (spare == 1 ? " device is spare." : " devices are spare.")
                        + " Click one on the left, then click where it goes on the craft.";
                colour = WARN;
            } else if (empty > 0) {
                message = empty + (empty == 1 ? " part is empty." : " parts are empty.")
                        + " Amber outlines are waiting for hardware you have not bound yet.";
                colour = WARN;
            } else {
                message = "Every part is filled and working.";
                colour = GOOD;
            }
        }
        renderStatus(graphics, ox, oy, message, colour,
                "LMB fit   RMB clear   scroll the list");
    }

    private static int boundsMinX(RigShapes.SlotShape shape) {
        if (shape.kind() == RigShapes.Kind.DISC) return shape.anchorX() - shape.radius();
        int m = Integer.MAX_VALUE;
        for (int v : shape.xs()) m = Math.min(m, v);
        return m;
    }

    private static int boundsMaxX(RigShapes.SlotShape shape) {
        if (shape.kind() == RigShapes.Kind.DISC) return shape.anchorX() + shape.radius();
        int m = Integer.MIN_VALUE;
        for (int v : shape.xs()) m = Math.max(m, v);
        return m;
    }

    private static int boundsMinY(RigShapes.SlotShape shape) {
        if (shape.kind() == RigShapes.Kind.DISC) return shape.anchorY() - shape.radius();
        int m = Integer.MAX_VALUE;
        for (int v : shape.ys()) m = Math.min(m, v);
        return m;
    }

    private static int boundsMaxY(RigShapes.SlotShape shape) {
        if (shape.kind() == RigShapes.Kind.DISC) return shape.anchorY() + shape.radius();
        int m = Integer.MIN_VALUE;
        for (int v : shape.ys()) m = Math.max(m, v);
        return m;
    }

    private String fmt(float value, int decimals, String unit) {
        if (Float.isNaN(value)) return "---";
        return String.format(java.util.Locale.ROOT, "%." + decimals + "f", value) + unit;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (nameField != null) {
            if (nameField.isMouseOver(mouseX, mouseY)) {
                return super.mouseClicked(mouseX, mouseY, button);
            }
            commitRename();
        }
        if (alertListBounds != null
                && mouseX >= alertListBounds[0] && mouseX < alertListBounds[0] + alertListBounds[2]
                && mouseY >= alertListBounds[1] && mouseY < alertListBounds[1] + alertListBounds[3]) {
            alertsExpanded = false;
            click(1.0f);
            return true;
        }

        if (hoveredSlot != null && hoveredSlot.isActive()) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        if (pendingDrag != null && button == 0
                && mouseX >= pendingDrag.x() - 8 && mouseX < pendingDrag.x() + pendingDrag.w() + 8
                && mouseY >= pendingDrag.y() && mouseY < pendingDrag.y() + pendingDrag.h()) {
            drag = pendingDrag;
            applyDrag(mouseX, mouseY);
            return true;
        }

        if (button == 1 && page == Page.RIG) {
            String slot = slotUnder(mouseX, mouseY);
            if (slot != null) {
                send(VccMk3ActionPacket.Action.CLEAR_SLOT, null, 0.0, slot);
                click(0.8f);
                return true;
            }
        }

        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit hit = hits.get(i);
            if (hit.contains(mouseX, mouseY) && hit.action() != null) {
                hit.action().run();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (drag != null) {
            applyDrag(mouseX, mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (drag != null) {
            if (drag.axis() != ControlAxis.FORWARD && drag.axis() != ControlAxis.UP) {
                send(VccMk3ActionPacket.Action.SET_AXIS, null, 0.0, drag.axis().name());
                if (drag.widget() == WidgetType.STICK) {
                    send(VccMk3ActionPacket.Action.SET_AXIS, null, 0.0, ControlAxis.ROLL.name());
                }
            }
            drag = null;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void applyDrag(double mouseX, double mouseY) {
        if (drag == null) return;
        switch (drag.widget()) {
            case LEVER -> {
                double fraction = 1.0 - (mouseY - drag.y()) / Math.max(1.0, drag.h());
                double value = clampSigned(fraction * 2.0 - 1.0);
                detent(clamp01(fraction));
                send(VccMk3ActionPacket.Action.SET_AXIS, null, value, drag.axis().name());
            }

            case WHEEL, DIAL, SELECTOR -> {
                double fraction = (mouseX - drag.x()) / Math.max(1.0, drag.w());
                double value = clampSigned(fraction * 2.0 - 1.0);
                detent(clamp01(fraction));
                send(VccMk3ActionPacket.Action.SET_AXIS, null, value, drag.axis().name());
            }

            case STICK -> {
                double dx = clampSigned(((mouseX - drag.x()) / Math.max(1.0, drag.w())) * 2.0 - 1.0);
                double dy = clampSigned(((mouseY - drag.y()) / Math.max(1.0, drag.h())) * 2.0 - 1.0);
                detent(clamp01((dx + 1.0) / 2.0));
                send(VccMk3ActionPacket.Action.SET_AXIS, null, dy, ControlAxis.PITCH.name());
                send(VccMk3ActionPacket.Action.SET_AXIS, null, dx, ControlAxis.ROLL.name());
            }
            default -> { }
        }
    }

    @Nullable
    private String slotUnder(double mouseX, double mouseY) {
        if (rigSlotShapes == null) return null;
        for (Map.Entry<String, RigShapes.SlotShape> entry : rigSlotShapes.entrySet()) {
            if (entry.getValue().contains(mouseX, mouseY)) return entry.getKey();
        }
        return null;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int step = (int) Math.signum(scrollY);
        if (page == Page.RIG && withinColumn(rigDeviceBounds, mouseX, mouseY)) {
            rigDeviceScroll = clampScroll(rigDeviceScroll - step, rigDeviceBounds);
            return true;
        }
        if (page == Page.HARDWARE && withinColumn(ledgerBounds, mouseX, mouseY)) {
            ledgerScroll = clampScroll(ledgerScroll - step, ledgerBounds);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void onClose() {
        VccMk3ClientState.clear();
        super.onClose();
    }

    private void hit(int x, int y, int w, int h, @Nullable Runnable action, @Nullable String tooltip) {
        hits.add(new Hit(x, y, w, h, action, tooltip));
    }

    private void renderHitTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (alertListBounds != null
                && mouseX >= alertListBounds[0] && mouseX < alertListBounds[0] + alertListBounds[2]
                && mouseY >= alertListBounds[1] && mouseY < alertListBounds[1] + alertListBounds[3]) {
            return;
        }

        Hit found = null;
        for (Hit hit : hits) {
            if (hit.tooltip() != null && hit.contains(mouseX, mouseY)) found = hit;
        }
        if (found == null) return;
        List<Component> lines = new ArrayList<>();
        for (String line : wrap(found.tooltip(), 220)) {
            lines.add(Component.literal(line));
        }
        graphics.renderComponentTooltip(font, lines, mouseX, mouseY);
    }

    private int instrument(GuiGraphics graphics, int x, int y, int w, String label,
                           AvionicsChannel value, int decimals, String unit) {
        graphics.drawString(font, label, x, y, TEXT_DIM, false);
        String text = value.present() ? value.format(decimals) + unit : "---";
        graphics.drawString(font, text, x + w - font.width(text) - 32, y,
                value.present() ? TEXT : TEXT_FAINT, false);
        graphics.drawString(font, value.source().tag(), x + w - 28, y, value.source().color(), false);
        hit(x, y, w, 11, null, label + ": " + value.source().description());
        return y + 13;
    }

    private static int[] listBounds(int x, int y, int w, int h, int total, int capacity) {
        return new int[] { x, y, w, h, total, capacity };
    }

    private static boolean withinColumn(@Nullable int[] bounds, double mx, double my) {
        return bounds != null && mx >= bounds[0] && mx < bounds[0] + bounds[2]
                && my >= bounds[1] && my < bounds[1] + bounds[3];
    }

    private static int clampScroll(int candidate, @Nullable int[] bounds) {
        if (bounds == null) return Math.max(0, candidate);
        return Math.max(0, Math.min(candidate, Math.max(0, bounds[4] - bounds[5])));
    }

    private void scrollbar(GuiGraphics graphics, int x, int y, int w, int h,
                           int total, int start, int capacity, int accent) {
        if (total <= capacity || h <= 4) return;
        graphics.fill(x, y, x + w, y + h, PANEL_SUNK);
        int thumbH = Math.max(6, h * capacity / total);
        int span = h - thumbH;
        int maxStart = Math.max(1, total - capacity);
        int thumbY = y + (int) Math.round(span * (start / (double) maxStart));
        graphics.fill(x, thumbY, x + w, thumbY + thumbH, accent);
    }

    private void smallButton(GuiGraphics graphics, int x, int y, int w, int h,
                             String label, int accent, Runnable action, String tooltip) {
        graphics.fill(x, y, x + w, y + h, PANEL_SUNK);
        ConsoleDraw.outline(graphics, x, y, w, h, accent);
        graphics.drawString(font, label, x + (w - font.width(label)) / 2, y + (h - 8) / 2, accent, false);
        hit(x, y, w, h, action, tooltip);
    }

    private void panel(GuiGraphics graphics, int x, int y, int w, int h, String title, int accent) {
        panel(graphics, x, y, w, h, title);
        graphics.fill(x + 1, y + 15, x + w - 1, y + 16, accent);
    }

    private void panel(GuiGraphics graphics, int x, int y, int w, int h, String title) {
        graphics.fill(x, y, x + w, y + h, PANEL_SUNK);
        ConsoleDraw.outline(graphics, x, y, w, h, EDGE);
        graphics.fill(x + 1, y + 1, x + w - 1, y + 15, PANEL_RAISED);
        graphics.drawString(font, title, x + 8, y + 4, TEXT_DIM, false);
    }

    private int drawWrapped(GuiGraphics graphics, String text, int x, int y, int width,
                            int maxLines, int colour) {
        List<String> lines = wrap(text, width);
        int drawn = Math.min(maxLines, lines.size());
        for (int i = 0; i < drawn; i++) {
            graphics.drawString(font, lines.get(i), x, y + i * 10, colour, false);
        }
        return drawn;
    }

    private List<String> wrap(String text, int width) {
        List<String> out = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String word : text.split(" ")) {
            String candidate = current.isEmpty() ? word : current + " " + word;
            if (font.width(candidate) > width && !current.isEmpty()) {
                out.add(current.toString());
                current = new StringBuilder(word);
            } else {
                current = new StringBuilder(candidate);
            }
        }
        if (!current.isEmpty()) out.add(current.toString());
        return out;
    }

    private String trim(String text, int maxWidth) {
        if (font.width(text) <= maxWidth) return text;
        String cut = text;
        while (font.width(cut + "...") > maxWidth && cut.length() > 3) {
            cut = cut.substring(0, cut.length() - 1);
        }
        return cut + "...";
    }

    private static final double[] FLIP_GUARD_STEPS = { 0.0, 45.0, 62.0, 75.0 };

    private double nextFlipGuard(VccMk3SnapshotPacket snapshot) {
        double current = snapshot.flipGuardDegrees();
        int nearest = 0;
        double bestDistance = Double.MAX_VALUE;
        for (int i = 0; i < FLIP_GUARD_STEPS.length; i++) {
            double distance = Math.abs(FLIP_GUARD_STEPS[i] - current);
            if (distance < bestDistance) {
                bestDistance = distance;
                nearest = i;
            }
        }
        return FLIP_GUARD_STEPS[(nearest + 1) % FLIP_GUARD_STEPS.length];
    }

    private String flipGuardLabel(VccMk3SnapshotPacket snapshot) {
        if (snapshot.flipGuardActive()) return "ENGAGED, recovering";
        double degrees = snapshot.flipGuardDegrees();
        return degrees <= 0.0 ? "Off" : Math.round(degrees) + " degrees";
    }

    private static String statusWord(LinkHealth health) {
        return switch (health) {
            case OK -> "READY";
            case EMPTY -> "EMPTY";
            case DISABLED -> "OFF";
            case MISSING_ACTUATOR_CARD, MISSING_GOVERNOR_CARD, CARD_BLANK -> "SETUP";
            case CROSS_DIMENSION, WRONG_TARGET_TYPE -> "WRONG";
            case ACTUATOR_MISSING, GOVERNOR_MISSING -> "GONE";
            case GOVERNOR_CLAIMED -> "TAKEN";
            case OFFLINE -> "OFFLINE";
        };
    }

    private AvionicsChannel channel(@Nullable VccMk3SnapshotPacket snapshot, int index) {
        return snapshot == null ? AvionicsChannel.NONE : snapshot.channel(index);
    }

    private static double clamp01(double v) {
        return v < 0 ? 0 : Math.min(v, 1);
    }

    private static double clampSigned(double v) {
        return v < -1 ? -1 : Math.min(v, 1);
    }

    private static String capitalise(String text) {
        return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    private void send(VccMk3ActionPacket.Action action, @Nullable UUID target, double value, String text) {
        PacketDistributor.sendToServer(new VccMk3ActionPacket(menu.containerId, action, target, value, text));
    }

    private void click(float pitch) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) return;
        minecraft.getSoundManager().play(
                SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), pitch, 0.35f));
    }

    private void detent(double fraction) {
        int step = (int) Math.round(fraction * 12.0);
        long now = System.currentTimeMillis();
        if (step == lastDetentStep || now - lastDetentMs < 35L) return;
        lastDetentStep = step;
        lastDetentMs = now;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) return;
        minecraft.getSoundManager().play(
                SimpleSoundInstance.forUI(SoundEvents.LEVER_CLICK, 1.6f, 0.18f));
    }

    private void armSound(boolean arming) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) return;
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(
                arming ? SoundEvents.NOTE_BLOCK_BELL.value() : SoundEvents.NOTE_BLOCK_BASS.value(),
                arming ? 1.5f : 0.7f, 0.5f));
    }

    @Nullable
    private static ControlAxis axisForKey(int keyCode) {
        return switch (keyCode) {
            case 87, 83 -> ControlAxis.FORWARD;
            case 65, 68 -> ControlAxis.YAW;
            case 32, 340 -> ControlAxis.UP;
            case 265, 264 -> ControlAxis.PITCH;
            case 263, 262 -> ControlAxis.ROLL;
            default -> null;
        };
    }

    private static double axisDirection(int keyCode) {
        return switch (keyCode) {
            case 87, 68, 32, 264, 262 -> 1.0;
            case 83, 65, 340, 265, 263 -> -1.0;
            default -> 0.0;
        };
    }

    private final Map<ControlAxis, Double> axisDirections = new HashMap<>();

    private final Map<Integer, ControlAxis> heldKeyAxes = new HashMap<>();

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (nameField != null) {
            if (keyCode == 257 || keyCode == 335) {
                commitRename();
                return true;
            }
            if (keyCode == 256) {
                cancelRename();
                return true;
            }
            return nameField.keyPressed(keyCode, scanCode, modifiers)
                    || nameField.canConsumeInput()
                    || super.keyPressed(keyCode, scanCode, modifiers);
        }
        if (page == Page.FLY) {
            ControlAxis axis = axisForKey(keyCode);
            VccMk3SnapshotPacket snapshot = VccMk3ClientState.snapshot();

            if (axis != null && snapshot != null && snapshot.axisUsable(axis)) {
                heldAxes.add(axis);
                heldKeyAxes.put(keyCode, axis);
                axisDirections.put(axis, axisDirection(keyCode));
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        ControlAxis axis = heldKeyAxes.remove(keyCode);
        if (axis != null && heldAxes.remove(axis)) {
            axisDirections.remove(axis);
            releaseAxis(axis);
            return true;
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    private void releaseAxis(ControlAxis axis) {
        keyboardValues.remove(axis);
        if (axis == ControlAxis.FORWARD) return;
        send(VccMk3ActionPacket.Action.SET_AXIS, null, 0.0, axis.name());
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (bootTicks < BOOT_TICKS) bootTicks++;
        if (page != Page.FLY || heldAxes.isEmpty()) return;

        VccMk3SnapshotPacket snapshot = VccMk3ClientState.snapshot();
        if (snapshot == null) return;

        for (ControlAxis axis : heldAxes) {
            double direction = axisDirections.getOrDefault(axis, 0.0);
            if (direction == 0.0) continue;
            // seed once then integrate locally, the snapshot is 5Hz

            double current = keyboardValues.computeIfAbsent(axis, a -> (double) snapshot.demand(a));

            double floor = axis == ControlAxis.UP ? 0.0 : -1.0;
            double next = Math.max(floor, Math.min(1.0, current + direction * 0.06));
            keyboardValues.put(axis, next);
            send(VccMk3ActionPacket.Action.SET_AXIS, null, next, axis.name());
        }
    }

    private void renderBoot(GuiGraphics graphics, int x, int y) {
        if (bootTicks >= BOOT_TICKS) return;
        float progress = bootTicks / (float) BOOT_TICKS;
        int sweepY = y + (int) (progress * canvasH());

        graphics.fill(x + 1, sweepY, x + canvasW() - 1, y + canvasH() - 1, ConsoleDraw.withAlpha(PANEL_SUNK, 235));
        graphics.fill(x + 1, sweepY, x + canvasW() - 1, sweepY + 1, CYAN);
        graphics.fill(x + 1, sweepY + 1, x + canvasW() - 1, sweepY + 2, ConsoleDraw.withAlpha(CYAN, 90));

        if (progress < 0.75f) {
            String line = "VCC MK III  SELF TEST";
            graphics.drawString(font, line, x + (canvasW() - font.width(line)) / 2, sweepY + 14,
                    ConsoleDraw.withAlpha(CYAN, 200), false);
        }
    }
}
