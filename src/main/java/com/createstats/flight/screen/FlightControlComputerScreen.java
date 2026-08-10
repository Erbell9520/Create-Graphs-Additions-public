package com.createstats.flight.screen;

import com.createstats.flight.FlightMode;
import com.createstats.flight.SensorRequirement;
import com.createstats.flight.client.FccClientState;
import com.createstats.flight.menu.FlightControlComputerMenu;
import com.createstats.flight.network.FccGuiControlEntry;
import com.createstats.flight.network.FccGuiDeviceEntry;
import com.createstats.flight.network.FccGuiGroupEntry;
import com.createstats.flight.network.FccGuiRouteEntry;
import com.createstats.flight.network.FccGuiSnapshot;
import com.createstats.flight.network.FccHudSubscriptionPacket;
import com.createstats.flight.network.FccModeChangePacket;
import com.createstats.flight.network.FccTelemetryPacket;
import com.createstats.flight.network.FccWorkbenchActionPacket;
import com.createstats.flight.workbench.FccDiagramProfile;
import com.createstats.pathfinder.model.HazardFlags;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

public class FlightControlComputerScreen extends AbstractContainerScreen<FlightControlComputerMenu> {

    private static final int W = 920;
    private static final int H = 520;

    private static final int MARGIN = 14;
    private static final int TITLE_Y = 9;
    private static final int NAV_Y = 28;
    private static final int NAV_H = 20;
    private static final int HELP_X = W - 32;
    private static final int HELP_Y = 6;
    private static final int HELP_SIZE = 18;

    private static final int DOCK_X = 14;
    private static final int DOCK_Y = 56;
    private static final int DOCK_W = 174;
    private static final int DOCK_H = 276;

    private static final int MAIN_X = 198;
    private static final int MAIN_Y = 56;
    private static final int MAIN_W = 558;
    private static final int MAIN_H = 336;

    private static final int INSPECT_X = 766;
    private static final int INSPECT_Y = 56;
    private static final int INSPECT_W = 140;
    private static final int INSPECT_H = 336;

    private static final int DECK_X = 14;
    private static final int DECK_Y = 398;
    private static final int DECK_W = 188;
    private static final int DECK_H = 108;

    private static final int STATUS_X = 212;
    private static final int STATUS_Y = 398;
    private static final int STATUS_W = 694;
    private static final int STATUS_H = 108;

    private static final int ROW_H = 26;
    private static final int ROW_GAP = 4;
    private static final int SMALL_ROW_H = 22;
    private static final int DEVICE_ROWS = 9;
    private static final int GROUP_ROWS = 8;
    private static final int CONTROL_ROWS = 8;
    private static final int ROUTE_ROWS = 7;
    private static final int CONTROL_LIST_ROWS = 4;
    private static final int CONTROL_GROUP_ROWS = 4;
    private static final int CONTROL_ROUTE_ROWS = 5;
    private static final int SHIP_SWITCH_COLUMNS = 8;
    private static final int SHIP_SWITCH_W = 52;
    private static final int SHIP_SWITCH_H = 20;
    private static final int SHIP_SWITCH_GAP = 2;
    private static final int SHIP_SAFETY_SWITCH_H = 18;
    private static final int BOMB_PANEL_H = 62;
    private static final int SHIP_THROTTLE_Y = 104;
    private static final int SHIP_THROTTLE_H = 108;

    private static final int SCREEN_BG = 0xE60B0F14;
    private static final int FRAME_BG = 0xF0111720;
    private static final int PANEL_BG = 0xF0151D28;
    private static final int PANEL_ALT = 0xF01A2632;
    private static final int PANEL_HDR = 0xF0202A36;
    private static final int CARD_BG = 0xF0182430;
    private static final int CARD_HOVER = 0xF0233342;
    private static final int CARD_ACTIVE = 0xF02B4054;
    private static final int SLOT_BG = 0xAA1D2B38;
    private static final int BORDER = 0xFF53718A;
    private static final int SOFT_BORDER = 0x664A6278;
    private static final int TEXT = 0xFFE4EEF8;
    private static final int DIM = 0xFF8CA1B4;
    private static final int MUTED = 0xFF53677A;
    private static final int ACCENT = 0xFFFFDD55;
    private static final int CYAN = 0xFF62D5FF;
    private static final int OK_GREEN = 0xFF6EE7A8;
    private static final int WARN = 0xFFFFB454;
    private static final int BAD = 0xFFFF6B6B;
    private static final int VEHICLE_MAX_WHEEL_RPM = 256;
    private static final String AUTO_EVADE_RADIUS_LABEL = "Evade Radius Dial";
    private static final String AUTO_APPROACH_DISTANCE_LABEL = "Approach Distance Dial";
    private static final String AUTO_ANNOUNCE_INTERVAL_LABEL = "Announcement Interval Dial";
    private static final String AUTO_REVERSE_TIME_LABEL = "Reverse Time Dial";
    private static final double[] AUTO_EVADE_RADIUS_STEPS = { 12.0, 18.0, 24.0, 30.0, 36.0 };
    private static final double[] AUTO_APPROACH_DISTANCE_STEPS = { 100.0, 150.0, 200.0, 300.0, 500.0, 800.0 };
    private static final double[] AUTO_ANNOUNCE_INTERVAL_STEPS = { 50.0, 100.0, 200.0, 250.0, 500.0, 1000.0 };
    private static final double[] AUTO_REVERSE_TIME_STEPS = { 2.0, 3.0, 4.0, 5.0, 7.0, 10.0 };
    private static final String[] LINKED_CONTROLLER_KEYS = { "W", "S", "A", "D", "SPACE", "SHIFT", "BAY", "DROP" };
    private static final String[] LINKED_CONTROLLER_ACTIONS = {
            "Thrust Up", "Thrust Down / Reverse", "Wheel Left", "Wheel Right",
            "Levitation Up", "Levitation Down", "Bay Open Toggle", "Bomb Drop Hold"
    };

    // One canonical player-facing name per view, used identically in the main panel
    // header and the sub-nav pills (they used to disagree for 9 of 11 views).
    private enum WorkView {
        TEMPLATE("Starter"),
        DEVICES("Hardware"),
        DIAGRAM("Airship Layout"),
        OTOMOBIL("Ground Layout"),
        SHIP("Helm"),
        AUTO_MODE("Auto Drive"),
        GROUPS("Groups"),
        CONTROLS("Routing"),
        E_MANUEL("Overrides"),
        LINKED_CONTROLLER("Controller"),
        TEST("Testing");

        private final String label;

        WorkView(String label) {
            this.label = label;
        }
    }

    private enum WorkStation {
        SETUP("Setup", WorkView.TEMPLATE, WorkView.DEVICES, WorkView.GROUPS),
        LAYOUT("Layout", WorkView.DIAGRAM, WorkView.OTOMOBIL),
        CONTROL("Control", WorkView.CONTROLS),
        OPERATE("Operate", WorkView.SHIP, WorkView.AUTO_MODE),
        POWER_USER("Power User", WorkView.LINKED_CONTROLLER, WorkView.E_MANUEL, WorkView.TEST);

        private final String label;
        private final WorkView[] views;

        WorkStation(String label, WorkView... views) {
            this.label = label;
            this.views = views;
        }

        private WorkView defaultView() {
            return views[0];
        }

        private boolean contains(WorkView view) {
            for (WorkView candidate : views) {
                if (candidate == view) return true;
            }
            return false;
        }
    }

    private enum ShipDrag {
        NONE,
        YAW,
        LIFT,
        TRIM
    }

    private enum AutoDriveMode {
        OFF("Off"),
        FALLBACK("All"),
        NAV_TABLE("Nav"),
        PATHFINDER("PF");

        private final String label;

        AutoDriveMode(String label) {
            this.label = label;
        }
    }

    private enum OperateProfile {
        AIRSHIP("Zeplin/Balloon"),
        GROUND("Ground Vehicle");

        private final String label;

        OperateProfile(String label) {
            this.label = label;
        }
    }

    private record ShipControls(
            @Nullable FccGuiControlEntry thrust,
            @Nullable FccGuiControlEntry yaw,
            @Nullable FccGuiControlEntry trim) {}

    private record OperateProfileState(@Nullable OperateProfile profile, boolean manual, boolean hybrid,
                                       int airshipDevices, int groundDevices, String reason) {}

    private record VehicleWheelPreview(OtomobilZone zone, int rpm, boolean invert, int deviceCount, int steerSignal) {}

    private record VehiclePreview(List<VehicleWheelPreview> wheels, String resolvedModeLabel, boolean runtimeResolved) {}

    private record RouteMapBounds(int minX, int maxX, int minZ, int maxZ) {}

    private enum VehicleSteerModePreview {
        FRONT,
        ALL_WHEEL,
        CRAB
    }

    private enum DiagramZone {
            FRONT_MAIN_THRUST("FRONT_MAIN_THRUST", "Front Main", "Front Main Thrust", 0xFF62D5FF, 118, 18, 86, 24,
                "front thrust", "fore thrust", "nose thrust"),
            BACK_MAIN_THRUST("BACK_MAIN_THRUST", "Back Main", "Back Main Thrust", 0xFFFFB454, 108, 224, 106, 24,
                "rear thrust", "back thrust", "aft thrust"),
            MAIN_THRUST("MAIN_THRUST", "Main Thrust", "Main Thrust", 0xFFFFDD55, 121, 54, 80, 28,
                "thrust", "thrust props", "main engine"),
            LEFT_THRUST("LEFT_THRUST", "Left Thrust", "Left Thrust", 0xFF39C8FF, 16, 126, 88, 28,
                "left props", "left propellers", "port thrust"),
            RIGHT_THRUST("RIGHT_THRUST", "Right Thrust", "Right Thrust", 0xFFFFB454, 218, 126, 88, 28,
                "right props", "right propellers", "starboard thrust"),
            LEFT_PROPELLERS("LEFT_PROPELLERS", "Left Props", "Left Propellers", 0xFF58A6FF, 28, 82, 78, 26,
                "left propeller bank", "port propellers"),
            RIGHT_PROPELLERS("RIGHT_PROPELLERS", "Right Props", "Right Propellers", 0xFFFF9F6E, 216, 82, 78, 26,
                "right propeller bank", "starboard propellers"),
            UPWARD_LEVITATION("UPWARD_LEVITATION", "Lift", "Upward / Levitation", 0xFF6EE7A8, 116, 102, 90, 28,
                "lift props", "levitation", "upward propellers", "up thrust"),
            DOWNWARD_DESCENSION("DOWNWARD_DESCENSION", "Descend", "Downward / Descension", 0xFFB48CFF, 116, 160, 90, 28,
                "downward propellers", "descension", "down thrust"),
            STEAM_BURNER("STEAM_BURNER", "Vent/Burner", "Steam Vent / Burner", 0xFFFF6B6B, 110, 134, 102, 20,
                "steam vent", "hot air burner", "burner"),
            LEFT_MAIN_FLAP("LEFT_MAIN_FLAP", "L Flap", "Left Main Flap", 0xFF82D9A0, 22, 160, 82, 18,
                "left flap", "port flap", "left wing flap", "main flap left"),
            RIGHT_MAIN_FLAP("RIGHT_MAIN_FLAP", "R Flap", "Right Main Flap", 0xFFFFB88A, 218, 160, 82, 18,
                "right flap", "starboard flap", "right wing flap", "main flap right"),
            FINS("FINS", "Fins", "Fins", 0xFF55E0A0, 106, 198, 110, 20,
                "control surfaces", "vector bearings", "bearing fins"),
            LEFT_ELEVATOR("LEFT_ELEVATOR", "L Elev", "Left Elevator", 0xFFAED6F1, 30, 224, 72, 18,
                "left elevator", "port elevator", "left tail flap", "elevator left"),
            RIGHT_ELEVATOR("RIGHT_ELEVATOR", "R Elev", "Right Elevator", 0xFFFFD59E, 220, 224, 72, 18,
                "right elevator", "starboard elevator", "right tail flap", "elevator right"),
            AUX_BOOST("AUX_BOOST", "Aux Boost", "Aux / Boost", 0xFFD4AF37, 110, 253, 102, 20,
                "aux thrust", "boost", "auxiliary thrust");

            private final String key;
            private final String shortLabel;
            private final String label;
            private final int color;
            private final int x;
            private final int y;
            private final int width;
            private final int height;
            private final String[] aliases;

            DiagramZone(String key, String shortLabel, String label, int color, int x, int y, int width, int height, String... aliases) {
                this.key = key;
                this.shortLabel = shortLabel;
                this.label = label;
                this.color = color;
                this.x = x;
                this.y = y;
                this.width = width;
                this.height = height;
                this.aliases = aliases;
            }

            private boolean matches(String value) {
                String token = FlightControlComputerScreen.normalizeDiagramToken(value);
                if (token.equals(FlightControlComputerScreen.normalizeDiagramToken(key)) || token.equals(FlightControlComputerScreen.normalizeDiagramToken(label))) return true;
                for (String alias : aliases) {
                    if (token.equals(FlightControlComputerScreen.normalizeDiagramToken(alias))) return true;
                }
                return false;
            }

            private String switchLabel() {
                return label + " Switch";
            }
    }

    private enum OtomobilZone {
            FRONT_LEFT_WHEEL("FRONT_LEFT_WHEEL", "FL Wheel", "Front Left Wheel", 0xFF62D5FF, 42, 48, 78, 28,
                "front left", "frontleft", "fl wheel"),
            FRONT_RIGHT_WHEEL("FRONT_RIGHT_WHEEL", "FR Wheel", "Front Right Wheel", 0xFFFFB454, 204, 48, 78, 28,
                "front right", "frontright", "fr wheel"),
            MID_LEFT_WHEEL("MID_LEFT_WHEEL", "ML Wheel", "Mid Left Wheel", 0xFF58A6FF, 42, 118, 78, 28,
                "mid left", "middle left", "ml wheel"),
            MID_RIGHT_WHEEL("MID_RIGHT_WHEEL", "MR Wheel", "Mid Right Wheel", 0xFFFF9F6E, 204, 118, 78, 28,
                "mid right", "middle right", "mr wheel"),
            REAR_LEFT_WHEEL("REAR_LEFT_WHEEL", "RL Wheel", "Rear Left Wheel", 0xFF6EE7A8, 42, 190, 78, 28,
                "rear left", "back left", "rl wheel"),
            REAR_RIGHT_WHEEL("REAR_RIGHT_WHEEL", "RR Wheel", "Rear Right Wheel", 0xFFFFD59E, 204, 190, 78, 28,
                "rear right", "back right", "rr wheel");

            private final String key;
            private final String shortLabel;
            private final String label;
            private final int color;
            private final int x;
            private final int y;
            private final int width;
            private final int height;
            private final String[] aliases;

            OtomobilZone(String key, String shortLabel, String label, int color, int x, int y, int width, int height, String... aliases) {
                this.key = key;
                this.shortLabel = shortLabel;
                this.label = label;
                this.color = color;
                this.x = x;
                this.y = y;
                this.width = width;
                this.height = height;
                this.aliases = aliases;
            }

            private boolean matches(String value) {
                String token = FlightControlComputerScreen.normalizeDiagramToken(value);
                if (token.equals(FlightControlComputerScreen.normalizeDiagramToken(key)) || token.equals(FlightControlComputerScreen.normalizeDiagramToken(label))) return true;
                for (String alias : aliases) {
                    if (token.equals(FlightControlComputerScreen.normalizeDiagramToken(alias))) return true;
                }
                return false;
            }
    }

    // Instance defaults; init() overrides these via applyInitialStation based on whether the FCC
    // is configured yet (fresh -> Setup/Starter, configured -> Operate/Helm) and the last-used view.
    private WorkStation activeStation = WorkStation.SETUP;
    private WorkView activeView = WorkView.TEMPLATE;
    private boolean stationInitialized;
    // Remembered per client session so a configured FCC reopens where the player last was.
    // A fresh/unconfigured FCC always forces Setup/Starter regardless (see applyInitialStation).
    @Nullable private static WorkStation lastStation;
    @Nullable private static WorkView lastView;
    private final List<AbstractWidget> viewWidgets = new ArrayList<>();

    private EditBox deviceNameBox;
    private EditBox groupNameBox;
    private EditBox groupRenameBox;
    private EditBox controlNameBox;

    private int selectedDeviceIndex;
    private int selectedGroupIndex;
    private int selectedDiagramZoneIndex;
    private int selectedOtomobilZoneIndex;
    private int selectedControlIndex;
    private int selectedRouteIndex;
    private int selectedEmanuelIndex;
    private int selectedEmanuelRowHint = -1;
    // E-Manuel override system: per-binding control via fuse switches
    private boolean[] emanuelOverrideActive = new boolean[0];     // track which bindings are overridden
    private int[] emanuelRpmCommands = new int[0];               // RPM command per overridden binding
    private int emanuelSliderRpmMax = 256;
    private int controlScrollStart;
    private int controlGroupScrollStart;
    private int controlRouteScrollStart;
    private String lastWorkbenchSignature = "";
    private String notice = "";
    private String latchedWarning = "";
    private long warningLatchUntilMs;
    private String lastNoticeSeen = "";
    private long noticeSetAtMs;
    /** Snapshot rebuild deferred while the player is typing in an edit box or dragging a slider. */
    private boolean pendingViewRebuild;
    // [1]
    private long helmFrameNanos;
    private float helmWheelDisplay;
    private final float[] helmWheelVel = new float[1];
    private float helmThrottleDisplay;
    private final float[] helmThrottleVel = new float[1];
    private float helmTrimDisplay;
    private final float[] helmTrimVel = new float[1];
    private boolean helmArmedSeen;
    private boolean helmArmedInit;
    private long helmArmedChangeMs = Long.MIN_VALUE;
    private static final long NOTICE_TTL_MS = 4000L;
    private record TooltipHotspot(int x, int y, int w, int h, List<Component> lines) {}
    private final List<TooltipHotspot> tooltipHotspots = new ArrayList<>();
    @Nullable private OperateProfile manualOperateProfile;
    private boolean helpOpen;
    private FlightMode lastServerMode;
    private EnumSet<SensorRequirement> lastAvail = EnumSet.noneOf(SensorRequirement.class);
    private int lastCalState;
    private ShipDrag shipDrag = ShipDrag.NONE;
    @Nullable private FccGuiControlEntry shipDragControl;
    @Nullable private UUID heldMomentaryControlId;

    public FlightControlComputerScreen(FlightControlComputerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = W;
        this.imageHeight = H;
        this.titleLabelY = 0;
        this.inventoryLabelY = 0;
    }

    @Override
    protected void init() {
        super.init();
        FccGuiSnapshot snapshot = snapshotOrEmpty();
        applyInitialStation(snapshot);
        menu.setClientRedstoneRowMask(snapshot.redstoneRowMask());
        syncMenuViewState();
        this.lastServerMode = snapshot.mode();
        this.lastAvail = sensorCopy(snapshot);
        this.lastCalState = snapshot.calStateOrdinal();
        this.lastWorkbenchSignature = workbenchSignature(snapshot);
        rebuildViewWidgets();
    }

    // [2]
    private void applyInitialStation(FccGuiSnapshot snapshot) {
        if (stationInitialized) return;
        stationInitialized = true;
        boolean configured = snapshot.deviceCount() > 0 || snapshot.controlCount() > 0 || snapshot.groupCount() > 0;
        if (!configured) {
            activeStation = WorkStation.SETUP;
            activeView = WorkView.TEMPLATE;
            return;
        }
        if (lastStation != null && lastView != null && lastStation.contains(lastView)) {
            activeStation = lastStation;
            activeView = lastView;
            return;
        }
        activeStation = WorkStation.OPERATE;
        activeView = WorkView.SHIP;
    }

    private void rememberStation() {
        lastStation = activeStation;
        lastView = activeView;
    }

    public void onSnapshot(FccGuiSnapshot snapshot) {
        menu.setClientRedstoneRowMask(snapshot.redstoneRowMask());
        syncMenuViewState();
        boolean modeChanged = snapshot.mode() != lastServerMode;
        boolean calChanged = snapshot.calStateOrdinal() != lastCalState;
        EnumSet<SensorRequirement> currentSensors = sensorCopy(snapshot);
        boolean availChanged = !lastAvail.equals(currentSensors);
        String signature = workbenchSignature(snapshot);
        boolean workbenchChanged = !signature.equals(lastWorkbenchSignature);
        lastServerMode = snapshot.mode();
        lastAvail = currentSensors;
        lastCalState = snapshot.calStateOrdinal();
        lastWorkbenchSignature = signature;
        if (workbenchChanged || modeChanged || calChanged || availChanged) {
            if (isWorkbenchEditFocused() || isSliderDragActive()) {
                // [3]
                pendingViewRebuild = true;
            } else {
                rebuildViewWidgets();
            }
        }
    }

    public void onTelemetry(FccTelemetryPacket packet) {
        // Ensure override state arrays are properly sized for this binding count
        int bindingCount = packet.propBoundCount();
        if (emanuelOverrideActive.length != bindingCount) {
            boolean[] newOverrideActive = new boolean[bindingCount];
            int[] newRpmCommands = new int[bindingCount];
            // Copy existing state if shrinking
            if (emanuelOverrideActive.length > 0) {
                System.arraycopy(emanuelOverrideActive, 0, newOverrideActive, 0, Math.min(emanuelOverrideActive.length, bindingCount));
                System.arraycopy(emanuelRpmCommands, 0, newRpmCommands, 0, Math.min(emanuelRpmCommands.length, bindingCount));
            }
            emanuelOverrideActive = newOverrideActive;
            emanuelRpmCommands = newRpmCommands;
        }
        emanuelSliderRpmMax = 256;
    }

    private boolean isWorkbenchEditFocused() {
        return (deviceNameBox != null && deviceNameBox.isFocused())
                || (groupNameBox != null && groupNameBox.isFocused())
                || (groupRenameBox != null && groupRenameBox.isFocused())
                || (controlNameBox != null && controlNameBox.isFocused());
    }

    /** [4] */
    private boolean isSliderDragActive() {
        return isDragging() && getFocused() instanceof net.minecraft.client.gui.components.AbstractSliderButton;
    }

    /**
     * Warning shown instead of the normal selection notice when a name edit box holds typed but
     * unapplied text that the imminent row-change rebuild would otherwise silently discard.
     */
    @Nullable
    private String pendingUnsavedEditNotice(FccGuiSnapshot snapshot) {
        if (deviceNameBox != null && deviceNameBox.isFocused()) {
            FccGuiDeviceEntry selected = selectedDevice(snapshot);
            String typed = deviceNameBox.getValue().trim();
            if (!typed.isEmpty() && (selected == null || !typed.equals(selected.label()))) {
                return "Typed device name discarded (use Rename Device to apply)";
            }
        }
        if (groupRenameBox != null && groupRenameBox.isFocused()) {
            FccGuiGroupEntry selected = selectedGroup(snapshot);
            String typed = groupRenameBox.getValue().trim();
            if (!typed.isEmpty() && (selected == null || !typed.equals(selected.label()))) {
                return "Typed group name discarded (use Rename to apply)";
            }
        }
        if (controlNameBox != null && controlNameBox.isFocused()) {
            FccGuiControlEntry selected = selectedControl(snapshot);
            String typed = controlNameBox.getValue().trim();
            if (!typed.isEmpty() && (selected == null || !typed.equals(selected.label()))) {
                return "Typed control name discarded (use Rename to apply)";
            }
        }
        if (groupNameBox != null && groupNameBox.isFocused() && !groupNameBox.getValue().trim().isEmpty()) {
            return "Typed group name discarded (use Create Group to apply)";
        }
        return null;
    }

    private void rebuildViewWidgets() {
        pendingViewRebuild = false;
        syncMenuViewState();
        setFocused(null);
        for (AbstractWidget widget : viewWidgets) removeWidget(widget);
        viewWidgets.clear();
        deviceNameBox = null;
        groupNameBox = null;
        groupRenameBox = null;
        controlNameBox = null;
        FccGuiSnapshot snapshot = snapshotOrEmpty();
        clampSelections(snapshot);
        switch (activeView) {
            case TEMPLATE -> initTemplateWidgets(snapshot);
            case DEVICES -> initDeviceWidgets(snapshot);
            case GROUPS -> initGroupWidgets(snapshot);
            case DIAGRAM -> initDiagramWidgets(snapshot);
            case OTOMOBIL -> initOtomobilWidgets(snapshot);
            case AUTO_MODE -> initAutoModeWidgets(snapshot);
            case CONTROLS -> initControlWidgets(snapshot);
            case E_MANUEL -> initEmanuelWidgets(snapshot);
            case LINKED_CONTROLLER -> initLinkedControllerWidgets(snapshot);
            case TEST -> initTestWidgets(snapshot);
            case SHIP -> initShipWidgets(snapshot);
        }
    }

    private void syncMenuViewState() {
        activeStation = stationForView(activeView);
        menu.setClientLinkedControllerPageActive(activeView == WorkView.LINKED_CONTROLLER);
    }

    private WorkStation stationForView(WorkView view) {
        for (WorkStation station : WorkStation.values()) {
            if (station.contains(view)) return station;
        }
        return WorkStation.SETUP;
    }

    private void initTemplateWidgets(FccGuiSnapshot snapshot) {
        int x = leftPos + INSPECT_X + 10;
        int y = topPos + INSPECT_Y + 58;
        addViewWidget(Button.builder(Component.literal("Apply Zeppelin"), button -> sendApplyZeppelinTemplate())
            .bounds(x, y, INSPECT_W - 20, 20).build());
        addViewWidget(Button.builder(Component.literal(snapshot.mode() == FlightMode.OFF ? "Arm Test Output" : "Disarm Output"), button -> sendWorkbenchArmToggle())
            .bounds(x, y + 28, INSPECT_W - 20, 20).build());
    }

    private void initDeviceWidgets(FccGuiSnapshot snapshot) {
        int x = leftPos + INSPECT_X + 10;
        int y = topPos + INSPECT_Y + 70;
        FccGuiDeviceEntry selected = selectedDevice(snapshot);
        deviceNameBox = new EditBox(font, x, y, INSPECT_W - 20, 18, Component.literal("Device name"));
        deviceNameBox.setMaxLength(32);
        deviceNameBox.setValue(selected == null ? "" : selected.label());
        addViewWidget(deviceNameBox);
        Button rename = Button.builder(Component.literal("Rename Device"), button -> sendDeviceRename())
            .bounds(x, y + 24, INSPECT_W - 20, 18).build();
        rename.active = selected != null;
        addViewWidget(rename);
        Button category = Button.builder(Component.literal(selected == null ? "Category" : "Category: " + selected.categoryLabel()), button -> sendCycleDeviceCategory())
            .bounds(x, y + 48, INSPECT_W - 20, 18).build();
        category.active = selected != null;
        addViewWidget(category);
    }

    private void initGroupWidgets(FccGuiSnapshot snapshot) {
        int x = leftPos + INSPECT_X + 10;
        int y = topPos + INSPECT_Y + 52;
        groupNameBox = new EditBox(font, x, y, INSPECT_W - 20, 18, Component.literal("New group"));
        groupNameBox.setMaxLength(32);
        addViewWidget(groupNameBox);
        addViewWidget(Button.builder(Component.literal("Create Group"), button -> sendCreateGroup())
            .bounds(x, y + 24, INSPECT_W - 20, 18).build());

        FccGuiGroupEntry group = selectedGroup(snapshot);
        groupRenameBox = new EditBox(font, x, y + 70, INSPECT_W - 20, 18, Component.literal("Group name"));
        groupRenameBox.setMaxLength(32);
        groupRenameBox.setValue(group == null ? "" : group.label());
        groupRenameBox.active = group != null;
        addViewWidget(groupRenameBox);

        Button rename = Button.builder(Component.literal("Rename Group"), button -> sendGroupRename())
            .bounds(x, y + 94, INSPECT_W - 20, 18).build();
        rename.active = group != null;
        addViewWidget(rename);

        Button add = Button.builder(Component.literal("Add Device"), button -> sendAssignDeviceToGroup())
            .bounds(x, y + 124, (INSPECT_W - 22) / 2, 18).build();
        add.active = group != null && selectedDevice(snapshot) != null;
        addViewWidget(add);
        Button remove = Button.builder(Component.literal("Remove"), button -> sendUnassignDeviceFromGroup())
            .bounds(x + (INSPECT_W - 22) / 2 + 4, y + 124, (INSPECT_W - 22) / 2, 18).build();
        remove.active = add.active;
        addViewWidget(remove);

        Button delete = Button.builder(Component.literal("Delete Empty Group"), button -> sendDeleteGroup())
            .bounds(x, y + 154, INSPECT_W - 20, 18).build();
        delete.active = group != null && group.deviceCount() == 0;
        addViewWidget(delete);
    }

    private void initDiagramWidgets(FccGuiSnapshot snapshot) {
        int x = leftPos + INSPECT_X + 10;
        int y = topPos + INSPECT_Y + 50;
        DiagramZone zone = selectedDiagramZone();
        boolean enabled = canEditShipDiagram(snapshot);
        boolean linkSelected = isRedstoneLinkDevice(selectedDevice(snapshot));

        Button assign = Button.builder(Component.literal("Assign Device"), button -> sendAssignDeviceToDiagramZone())
            .bounds(x, y, INSPECT_W - 20, 18).build();
        assign.active = enabled && selectedDevice(snapshot) != null && zone != null;
        addViewWidget(assign);

        Button bayLink = Button.builder(Component.literal(payloadGroupDefined(snapshot, true) ? "Replace Bay Link" : "Define Bay Link"), button -> sendAssignPayloadLink("BAY"))
            .bounds(x, y + 26, INSPECT_W - 20, 18).build();
        bayLink.active = enabled && linkSelected;
        addViewWidget(bayLink);

        Button dropLink = Button.builder(Component.literal(payloadGroupDefined(snapshot, false) ? "Replace Drop Link" : "Define Drop Link"), button -> sendAssignPayloadLink("DROP"))
            .bounds(x, y + 50, INSPECT_W - 20, 18).build();
        dropLink.active = enabled && linkSelected;
        addViewWidget(dropLink);

        Button refresh = Button.builder(Component.literal("Refresh Cockpit"), button -> sendDiagramAutoSync())
            .bounds(x, y + 84, INSPECT_W - 20, 18).build();
        refresh.active = enabled && !snapshot.groupEntries().isEmpty();
        addViewWidget(refresh);

        Button switches = Button.builder(Component.literal("Ceiling Switches: Auto"), button -> {})
            .bounds(x, y + 114, INSPECT_W - 20, 18).build();
        switches.active = false;
        addViewWidget(switches);

        Button routing = Button.builder(Component.literal("Ship Controls: Auto"), button -> {})
            .bounds(x, y + 138, INSPECT_W - 20, 18).build();
        routing.active = false;
        addViewWidget(routing);

        Button reset = Button.builder(Component.literal("Reset Diagram"), button -> sendResetDiagram())
            .bounds(x, y + 168, INSPECT_W - 20, 18).build();
        addViewWidget(reset);
    }

    private void initControlWidgets(FccGuiSnapshot snapshot) {
        int x = leftPos + INSPECT_X + 10;
        int y = topPos + INSPECT_Y + 48;
        FccGuiControlEntry control = selectedControl(snapshot);
        FccGuiRouteEntry route = selectedRouteOrNull(snapshot);

        controlNameBox = new EditBox(font, x, y, INSPECT_W - 20, 18, Component.literal("Control name"));
        controlNameBox.setMaxLength(32);
        controlNameBox.setValue(control == null ? "" : control.label());
        addViewWidget(controlNameBox);

        addViewWidget(Button.builder(Component.literal("New Control"), button -> sendCreateControl())
            .bounds(x, y + 24, INSPECT_W - 20, 18).build());
        Button rename = Button.builder(Component.literal("Rename Control"), button -> sendControlRename())
            .bounds(x, y + 48, INSPECT_W - 20, 18).build();
        rename.active = control != null;
        addViewWidget(rename);

            Button widget = Button.builder(Component.literal(control == null ? "Control Type" : "Type: " + displayControlType(control)), button -> sendCycleControlWidget())
            .bounds(x, y + 78, INSPECT_W - 20, 18).build();
        widget.active = control != null;
        addViewWidget(widget);

        Button addRoute = Button.builder(Component.literal("Route To Group"), button -> sendAddRouteToControl())
            .bounds(x, y + 108, INSPECT_W - 20, 18).build();
        addRoute.active = control != null && selectedGroup(snapshot) != null;
        addViewWidget(addRoute);

        Button output = Button.builder(Component.literal(route == null ? "Output" : "Output: " + shortOutput(route.outputType())), button -> sendCycleRouteOutput())
            .bounds(x, y + 138, INSPECT_W - 20, 18).build();
        output.active = route != null;
        addViewWidget(output);

        Button invert = Button.builder(Component.literal(routeInvertButtonLabel(snapshot, route)), button -> sendToggleRouteInvert())
            .bounds(x, y + 162, INSPECT_W - 20, 18).build();
        invert.active = route != null;
        addViewWidget(invert);

        Button scaleDown = Button.builder(Component.literal("Scale -"), button -> sendAdjustRouteScale(-0.05))
            .bounds(x, y + 192, (INSPECT_W - 22) / 2, 18).build();
        scaleDown.active = route != null;
        addViewWidget(scaleDown);
        Button scaleUp = Button.builder(Component.literal("Scale +"), button -> sendAdjustRouteScale(0.05))
            .bounds(x + (INSPECT_W - 22) / 2 + 4, y + 192, (INSPECT_W - 22) / 2, 18).build();
        scaleUp.active = route != null;
        addViewWidget(scaleUp);

        Button deleteRoute = Button.builder(Component.literal("Delete Route"), button -> sendDeleteRoute())
            .bounds(x, y + 222, INSPECT_W - 20, 18).build();
        deleteRoute.active = route != null;
        addViewWidget(deleteRoute);
        Button deleteControl = Button.builder(Component.literal("Delete Control"), button -> sendDeleteControl())
            .bounds(x, y + 246, INSPECT_W - 20, 18).build();
        deleteControl.active = control != null;
        addViewWidget(deleteControl);
    }

    private void initEmanuelWidgets(FccGuiSnapshot snapshot) {
        // Emergency kill switch button (top-right of E-Manuel detail panel)
        int emergencyBtnX = leftPos + MAIN_X + MAIN_W - 120;
        int emergencyBtnY = topPos + MAIN_Y + 38;
        addViewWidget(Button.builder(Component.literal("Emergency Kill"), button -> {
            // Disable all overrides
            for (int i = 0; i < emanuelOverrideActive.length; i++) {
                emanuelOverrideActive[i] = false;
                emanuelRpmCommands[i] = 0;
            }
            sendOverrideCommand();
            notice = "Manual overrides disabled";
        }).bounds(emergencyBtnX, emergencyBtnY, 110, 20).build());
    }

    private void initTestWidgets(FccGuiSnapshot snapshot) {
        int inspectorX = leftPos + INSPECT_X + 10;
        int inspectorY = topPos + INSPECT_Y + 54;
        addViewWidget(Button.builder(Component.literal(snapshot.mode() == FlightMode.OFF ? "Arm Output" : "Disarm Output"), button -> sendWorkbenchArmToggle())
            .bounds(inspectorX, inspectorY, INSPECT_W - 20, 20).build());

        FccGuiControlEntry control = selectedControl(snapshot);
        int listW = 160;
        int boardX = leftPos + MAIN_X + 18 + listW + 22;
        int boardW = MAIN_W - listW - 62;
        int sliderX = boardX + 24;
        int sliderY = topPos + MAIN_Y + 42 + 130;
        int sliderW = boardW - 104;
        ControlValueSlider slider = new ControlValueSlider(sliderX, sliderY, sliderW, 20, this, control);
        slider.active = control != null;
        addViewWidget(slider);
        Button center = Button.builder(Component.literal("Center"), button -> sendCenterControlValue())
            .bounds(sliderX + sliderW + 10, sliderY, 58, 20).build();
        center.active = control != null;
        addViewWidget(center);
    }

    private void initShipWidgets(FccGuiSnapshot snapshot) {
        int inspectorX = leftPos + INSPECT_X + 10;
        // Buttons placed below the dashboard content block (which renders from INSPECT_Y+30 down ~160px)
        int inspectorY = topPos + INSPECT_Y + 196;
        boolean vehicleEnabled = canUseVehicleControls(snapshot);
        addViewWidget(Button.builder(Component.literal(snapshot.mode() == FlightMode.OFF ? "Arm Output" : "Disarm Output"), button -> sendWorkbenchArmToggle())
            .bounds(inspectorX, inspectorY, INSPECT_W - 20, 20).build());
        int halfW = (INSPECT_W - 24) / 2;
        addViewWidget(Button.builder(Component.literal("Center Steering"), button -> sendCenterShipControls())
            .bounds(inspectorX, inspectorY + 26, halfW, 20).build());
        Button invert = Button.builder(Component.literal("Invert"), button -> sendToggleControl(findControl(snapshot, "Steering Invert Switch")))
            .bounds(inspectorX + halfW + 4, inspectorY + 26, halfW, 20).build();
        invert.active = true;
        addViewWidget(invert);
        addViewWidget(Button.builder(Component.literal("Zero Thrust"), button -> sendZeroShipLift())
            .bounds(inspectorX, inspectorY + 52, INSPECT_W - 20, 20).build());
        if (vehicleEnabled) {
            Button autoMode = Button.builder(Component.literal(autoDriveModeButtonLabel(snapshot)), button -> sendCycleAutoDriveMode())
                .bounds(inspectorX, inspectorY + 82, INSPECT_W - 20, 20).build();
            autoMode.active = hasAnyAutoDriveModeControl(snapshot);
            addViewWidget(autoMode);
            Button evadeRadius = Button.builder(Component.literal(evadeRadiusButtonLabel(snapshot)), button -> sendCycleEvadeRadius())
                .bounds(inspectorX, inspectorY + 108, INSPECT_W - 20, 20).build();
            evadeRadius.active = findEvadeRadiusControl(snapshot) != null;
            addViewWidget(evadeRadius);
            Button pathfinder = Button.builder(Component.literal(pathfinderSystemsButtonLabel(snapshot)), button -> sendTogglePathfinderSystems())
                .bounds(inspectorX, inspectorY + 134, INSPECT_W - 20, 20).build();
            pathfinder.active = snapshot.pathfinderComputerPos() != null;
            addViewWidget(pathfinder);
        }
    }

    private void initOtomobilWidgets(FccGuiSnapshot snapshot) {
        int x = leftPos + INSPECT_X + 10;
        int y = topPos + INSPECT_Y + 154;
        OtomobilZone zone = selectedOtomobilZone();
        boolean enabled = canEditOtomobilDiagram(snapshot);

        addViewWidget(Button.builder(Component.literal(snapshot.mode() == FlightMode.OFF ? "Arm Output" : "Disarm Output"), button -> sendWorkbenchArmToggle())
            .bounds(x, y, INSPECT_W - 20, 18).build());

        Button assign = Button.builder(Component.literal("Assign Wheel"), button -> sendAssignDeviceToOtomobilZone())
            .bounds(x, y + 24, INSPECT_W - 20, 18).build();
        assign.active = enabled && selectedDevice(snapshot) != null && zone != null;
        addViewWidget(assign);

        Button refresh = Button.builder(Component.literal("Refresh Vehicles"), button -> sendDiagramAutoSync())
            .bounds(x, y + 48, INSPECT_W - 20, 18).build();
        refresh.active = enabled && !snapshot.groupEntries().isEmpty();
        addViewWidget(refresh);

        Button reset = Button.builder(Component.literal("Reset Diagrams"), button -> sendResetDiagram())
            .bounds(x, y + 72, INSPECT_W - 20, 18).build();
        reset.active = !snapshot.groupEntries().isEmpty();
        addViewWidget(reset);
    }

    private void initAutoModeWidgets(FccGuiSnapshot snapshot) {
        int x = leftPos + INSPECT_X + 10;
        int y = topPos + INSPECT_Y + 78;
        int totalW = INSPECT_W - 20;
        OperateProfileState profile = resolveOperateProfile(snapshot);
        boolean airshipMode = profile.profile() == OperateProfile.AIRSHIP && !canUseVehicleControls(snapshot);

        // 4-chip mode selector row
        AutoDriveMode current = autoDriveMode(snapshot);
        AutoDriveMode[] modes = AutoDriveMode.values();
        int chipGap = 2;
        int chipW = (totalW - chipGap * (modes.length - 1)) / modes.length;
        for (int i = 0; i < modes.length; i++) {
            AutoDriveMode mode = modes[i];
            int chipX = x + i * (chipW + chipGap);
            int modeColor = switch (mode) {
                case PATHFINDER -> CYAN;
                case NAV_TABLE -> ACCENT;
                case FALLBACK -> OK_GREEN;
                case OFF -> MUTED;
            };
            StyleChipButton chip = new StyleChipButton(chipX, y, chipW, 16, mode.label,
                mode == current, modeColor, () -> sendSetAutoDriveMode(snapshotOrEmpty(), mode));
            chip.active = autoDriveModeAvailable(snapshot, mode) && !(airshipMode && mode == AutoDriveMode.PATHFINDER);
            addViewWidget(chip);
        }

        // Arm / Disarm
        addViewWidget(Button.builder(Component.literal(snapshot.mode() == FlightMode.OFF ? "Arm Output" : "Disarm Output"),
            button -> sendWorkbenchArmToggle()).bounds(x, y + 22, totalW, 16).build());

        // Nav Table speed slider (only meaningful when Nav Table mode is possible)
        NavTableSpeedSlider speedSlider = new NavTableSpeedSlider(x, y + 44, totalW, 16, this, snapshot.navTableMaxSpeed());
        speedSlider.active = autoDriveModeAvailable(snapshot, AutoDriveMode.NAV_TABLE);
        addViewWidget(speedSlider);

        // Pathfinder tracking smoothness slider
        PathfinderSmoothnessSlider smoothSlider = new PathfinderSmoothnessSlider(x, y + 66, totalW, 16, this, snapshot.pathfinderLookaheadScale());
        smoothSlider.active = !airshipMode && autoDriveModeAvailable(snapshot, AutoDriveMode.PATHFINDER);
        addViewWidget(smoothSlider);

        // Evade radius
        Button evadeRadius = Button.builder(Component.literal(evadeRadiusButtonLabel(snapshot)), button -> sendCycleEvadeRadius())
            .bounds(x, y + 88, totalW, 16).build();
        evadeRadius.active = !airshipMode && findEvadeRadiusControl(snapshot) != null;
        addViewWidget(evadeRadius);

        // Pathfinder systems
        Button pathfinder = Button.builder(Component.literal(pathfinderSystemsButtonLabel(snapshot)), button -> sendTogglePathfinderSystems())
            .bounds(x, y + 110, totalW, 16).build();
        pathfinder.active = !airshipMode && snapshot.pathfinderComputerPos() != null;
        addViewWidget(pathfinder);

        Button approach = Button.builder(Component.literal(autoApproachButtonLabel(snapshot)), button -> sendCycleAutoApproachDistance())
            .bounds(x, y + 132, totalW, 16).build();
        approach.active = findAutoApproachControl(snapshot) != null;
        addViewWidget(approach);

        Button announce = Button.builder(Component.literal(autoAnnounceButtonLabel(snapshot)), button -> sendCycleAutoAnnounceInterval())
            .bounds(x, y + 154, totalW, 16).build();
        announce.active = findAutoAnnounceControl(snapshot) != null;
        addViewWidget(announce);

        Button reverse = Button.builder(Component.literal(autoReverseButtonLabel(snapshot)), button -> sendCycleAutoReverseTime())
            .bounds(x, y + 176, totalW, 16).build();
        reverse.active = findAutoReverseControl(snapshot) != null;
        addViewWidget(reverse);

        // Center steering
        Button center = Button.builder(Component.literal("Center Steering"), button -> sendCenterShipControls())
            .bounds(x, y + 198, totalW, 16).build();
        center.active = canUseVehicleControls(snapshot) || airshipDiagramDeviceCount(snapshot) > 0;
        addViewWidget(center);
    }

        private void initLinkedControllerWidgets(FccGuiSnapshot snapshot) {
        int inspectorX = leftPos + INSPECT_X + 10;
        int inspectorY = topPos + INSPECT_Y + 54;
            boolean hudPinned = FccClientState.isHudPinned(menu.getController().getBlockPos());
        addViewWidget(new LinkedControllerIntervalSlider(inspectorX, inspectorY, INSPECT_W - 20, 20, this,
            snapshot.linkedControllerRampIntervalTicks()));
        addViewWidget(Button.builder(Component.literal(snapshot.mode() == FlightMode.OFF ? "Arm Output" : "Disarm Output"), button -> sendWorkbenchArmToggle())
            .bounds(inspectorX, inspectorY + 32, INSPECT_W - 20, 20).build());
        addViewWidget(Button.builder(Component.literal("Center Wheel/Thrust"), button -> sendCenterLinkedControllerSpringControls())
            .bounds(inspectorX, inspectorY + 62, INSPECT_W - 20, 20).build());
            addViewWidget(Button.builder(Component.literal(hudPinned ? "Unpin HUD" : "Pin HUD"), button -> sendTogglePinnedHud(hudPinned))
                .bounds(inspectorX, inspectorY + 92, INSPECT_W - 20, 20).build());
        }

    private void addViewWidget(AbstractWidget widget) {
        viewWidgets.add(addRenderableWidget(widget));
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        renderConsole(graphics, mouseX, mouseY);
    }

    private void renderConsole(GuiGraphics graphics, int mouseX, int mouseY) {
        syncMenuViewState();
        tooltipHotspots.clear();
        int x = leftPos;
        int y = topPos;
        graphics.fill(x, y, x + W, y + H, SCREEN_BG);
        graphics.fill(x + 2, y + 2, x + W - 2, y + H - 2, FRAME_BG);
        border(graphics, x, y, W, H, BORDER);
        graphics.fill(x + 4, y + 2, x + W - 4, y + 3, 0x33FFFFFF);

        renderHeader(graphics);
        renderNavBar(graphics, mouseX, mouseY);
        renderPanel(graphics, x + DOCK_X, y + DOCK_Y, DOCK_W, DOCK_H, "Card Rack");
        renderPanel(graphics, x + MAIN_X, y + MAIN_Y, MAIN_W, MAIN_H, activeStation.label + " / " + activeView.label);
        renderPanel(graphics, x + INSPECT_X, y + INSPECT_Y, INSPECT_W, INSPECT_H, "Inspector");
        renderPanel(graphics, x + DECK_X, y + DECK_Y, DECK_W, DECK_H, "Player Deck");
        renderPanel(graphics, x + STATUS_X, y + STATUS_Y, STATUS_W, STATUS_H, "Selected Output");
        renderSubNavBar(graphics, mouseX, mouseY);

        renderCardRack(graphics);
        renderInventoryBackdrop(graphics);
        renderActiveView(graphics, mouseX, mouseY);
        renderInspector(graphics, mouseX, mouseY);
        renderEmanuelAlertBanner(graphics);
        renderStatusPanel(graphics);
        // [5]
        if (!effectiveWarning(snapshotOrEmpty()).isEmpty()) {
            com.createstats.client.hud.HudEffects.warningRimFlash(graphics, x, y, W, H, true, WARN);
        }
    }

    private void renderHeader(GuiGraphics graphics) {
        FccGuiSnapshot snapshot = snapshotOrEmpty();
        int x = leftPos + MARGIN;
        int y = topPos + TITLE_Y;
        graphics.drawString(font, "FCC", x, y, ACCENT, false);
        graphics.drawString(font, "Flight Control Computer", x + 28, y, TEXT, false);
        boolean armed = snapshot.mode() != FlightMode.OFF;
        String state = armed ? "ARMED" : "DISARMED";
        // Armed state breathes slowly (wall clock) so a live craft is distinguishable from a
        // static label at a glance.
        int armedColor = armed
            ? withAlpha(OK_GREEN, 200 + (int) (55 * com.createstats.client.hud.HudAnim.pulse(System.currentTimeMillis(), 1600L)))
            : DIM;
        graphics.drawString(font, state, x + 170, y, armedColor, false);
        addTooltipHotspot(x + 170, y - 1, font.width(state) + 2, 9,
            Component.literal(armed ? "Armed" : "Disarmed").withStyle(armed ? ChatFormatting.GREEN : ChatFormatting.GRAY),
            Component.literal(armed
                ? "Controls are routed to props, governors, and redstone."
                : "Open the craft controls and arm to route your inputs.").withStyle(ChatFormatting.DARK_GRAY));
        String warning = effectiveWarning(snapshot);
        if (!warning.isEmpty()) {
            graphics.drawString(font, font.plainSubstrByWidth(warning, 390), x + 245, y, WARN, false);
        }
        int sensorX = leftPos + W - 72;
        renderSensorPip(graphics, sensorX, y + 1, "Gimbal Sensor",
            snapshot.availableSensors().contains(SensorRequirement.GIMBAL), snapshot.effectiveGimbalPos(), sensorManual(snapshot, SensorRequirement.GIMBAL));
        renderSensorPip(graphics, sensorX + 12, y + 1, "Altitude Sensor",
            snapshot.availableSensors().contains(SensorRequirement.ALTITUDE), snapshot.effectiveAltitudePos(), sensorManual(snapshot, SensorRequirement.ALTITUDE));
        renderSensorPip(graphics, sensorX + 24, y + 1, "Velocity Sensor",
            snapshot.availableSensors().contains(SensorRequirement.VELOCITY), snapshot.effectiveVelocityPos(), sensorManual(snapshot, SensorRequirement.VELOCITY));
        renderSensorPip(graphics, sensorX + 36, y + 1, "Nav Table",
            snapshot.availableSensors().contains(SensorRequirement.NAV_TABLE), snapshot.effectiveNavPos(), sensorManual(snapshot, SensorRequirement.NAV_TABLE));
        renderHelpButton(graphics);
    }

    private void renderHelpButton(GuiGraphics graphics) {
        int x = leftPos + HELP_X;
        int y = topPos + HELP_Y;
        graphics.fill(x, y, x + HELP_SIZE, y + HELP_SIZE, helpOpen ? CARD_ACTIVE : CARD_BG);
        border(graphics, x, y, HELP_SIZE, HELP_SIZE, helpOpen ? ACCENT : SOFT_BORDER);
        graphics.drawCenteredString(font, "?", x + HELP_SIZE / 2, y + 5, helpOpen ? ACCENT : TEXT);
    }

    private void renderSensorPip(GuiGraphics graphics, int x, int y, String name, boolean live,
                                 @Nullable BlockPos effectivePos, boolean manual) {
        if (live) {
            graphics.fill(x, y, x + 7, y + 7, OK_GREEN);
        } else {
            // Hollow square is a non-color cue so a missing sensor reads without relying on red vs green.
            border(graphics, x, y, 7, 7, BAD);
        }
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal(name).withStyle(ChatFormatting.WHITE));
        lines.add(live
            ? Component.literal("Detected and live").withStyle(ChatFormatting.GREEN)
            : Component.literal("Not detected").withStyle(ChatFormatting.RED));
        if (effectivePos != null) {
            lines.add(Component.literal((manual ? "Manual link: " : "Auto: ")
                    + effectivePos.getX() + ", " + effectivePos.getY() + ", " + effectivePos.getZ())
                .withStyle(ChatFormatting.GRAY));
        } else if (!live) {
            lines.add(Component.literal("Link one on the same contraption").withStyle(ChatFormatting.DARK_GRAY));
        }
        tooltipHotspots.add(new TooltipHotspot(x - 1, y - 1, 9, 9, List.copyOf(lines)));
    }

    private static boolean sensorManual(FccGuiSnapshot snapshot, SensorRequirement req) {
        return (snapshot.manualSensorMask() & (1 << req.ordinal())) != 0;
    }

    private void addTooltipHotspot(int x, int y, int w, int h, Component... lines) {
        tooltipHotspots.add(new TooltipHotspot(x, y, w, h, List.of(lines)));
    }

    // Rendered on top after all widgets and slot tooltips. Iterates in reverse so the most recently
    // registered (visually top-most) hotspot under the cursor wins.
    private void renderHotspotTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        for (int i = tooltipHotspots.size() - 1; i >= 0; i--) {
            TooltipHotspot h = tooltipHotspots.get(i);
            if (mouseX >= h.x() && mouseX < h.x() + h.w() && mouseY >= h.y() && mouseY < h.y() + h.h()) {
                graphics.renderComponentTooltip(font, h.lines(), mouseX, mouseY);
                return;
            }
        }
    }

    private void renderNavBar(GuiGraphics graphics, int mouseX, int mouseY) {
        FccGuiSnapshot snapshot = snapshotOrEmpty();
        int x = leftPos + MARGIN;
        int y = topPos + NAV_Y;
        int gap = 4;
        int w = (W - MARGIN * 2 - gap * (WorkStation.values().length - 1)) / WorkStation.values().length;
        int pulse = 38 + (int) (((Math.sin(System.currentTimeMillis() / 260.0) + 1.0) * 0.5) * 70.0);
        WorkStation[] values = WorkStation.values();
        for (int i = 0; i < values.length; i++) {
            WorkStation station = values[i];
            int bx = x + i * (w + gap);
            boolean active = activeStation == station;
            boolean hover = mouseX >= bx && mouseX < bx + w && mouseY >= y && mouseY < y + NAV_H;
            graphics.fill(bx, y, bx + w, y + NAV_H, active ? CARD_ACTIVE : hover ? CARD_HOVER : CARD_BG);
            border(graphics, bx, y, w, NAV_H, active ? ACCENT : SOFT_BORDER);
            graphics.fill(bx + 4, y + NAV_H / 2 - 2, bx + 10, y + NAV_H / 2 + 3, navColor(station));
            String status = stationStatusLabel(snapshot, station);
            int statusColor = stationStatusColor(snapshot, station);
            String label = font.plainSubstrByWidth((i + 1) + "  " + station.label, w - 72);
            graphics.drawString(font, label, bx + 14, y + 6, active ? TEXT : DIM, false);
            graphics.fill(bx + w - 48, y + 7, bx + w - 43, y + 12, active ? withAlpha(statusColor, 185 + pulse / 4) : statusColor);
            graphics.drawString(font, font.plainSubstrByWidth(status, 40), bx + w - 40, y + 6, active ? statusColor : DIM, false);
        }
    }

    private void renderSubNavBar(GuiGraphics graphics, int mouseX, int mouseY) {
        WorkView[] views = activeStation.views;
        int x = leftPos + MAIN_X + 10;
        int y = topPos + MAIN_Y + 21;
        int gap = 4;
        int totalW = MAIN_W - 20;
        int w = views.length <= 0 ? totalW : (totalW - gap * (views.length - 1)) / views.length;
        for (int i = 0; i < views.length; i++) {
            WorkView view = views[i];
            int bx = x + i * (w + gap);
            boolean active = activeView == view;
            boolean hover = mouseX >= bx && mouseX < bx + w && mouseY >= y && mouseY < y + 14;
            graphics.fill(bx, y, bx + w, y + 14, active ? 0xF02B4054 : hover ? CARD_HOVER : 0xCC101A24);
            border(graphics, bx, y, w, 14, active ? navColor(view) : SOFT_BORDER);
            graphics.drawCenteredString(font, font.plainSubstrByWidth(subViewLabel(view), w - 8), bx + w / 2, y + 3, active ? TEXT : DIM);
        }
    }

    private String subViewLabel(WorkView view) {
        return view.label;
    }

    private int navColor(WorkStation station) {
        return switch (station) {
            case SETUP -> ACCENT;
            case LAYOUT -> CYAN;
            case CONTROL -> WARN;
            case OPERATE -> OK_GREEN;
            case POWER_USER -> BAD;
        };
    }

    private String stationStatusLabel(FccGuiSnapshot snapshot, WorkStation station) {
        return switch (station) {
            case SETUP -> snapshot.deviceCount() + " hw";
            case LAYOUT -> resolveOperateProfile(snapshot).hybrid() ? "Hybrid" : resolveOperateProfile(snapshot).profile() == OperateProfile.GROUND ? "Ground" : "Zeplin";
            case CONTROL -> snapshot.controlCount() + " ctl";
            case OPERATE -> {
                OperateProfileState state = resolveOperateProfile(snapshot);
                yield state.hybrid() && !state.manual() ? "Pick" : state.profile() == OperateProfile.GROUND ? "Ground" : "Air";
            }
            case POWER_USER -> "Manual";
        };
    }

    private int stationStatusColor(FccGuiSnapshot snapshot, WorkStation station) {
        return switch (station) {
            case SETUP -> snapshot.ungroupedDeviceCount() > 0 ? WARN : OK_GREEN;
            case LAYOUT -> resolveOperateProfile(snapshot).hybrid() ? WARN : CYAN;
            case CONTROL -> snapshot.controlCount() > 0 ? OK_GREEN : WARN;
            case OPERATE -> {
                OperateProfileState state = resolveOperateProfile(snapshot);
                yield state.hybrid() && !state.manual() ? WARN : state.profile() == OperateProfile.GROUND ? OK_GREEN : CYAN;
            }
            case POWER_USER -> ACCENT;
        };
    }

    private int navColor(WorkView view) {
        return switch (view) {
            case TEMPLATE -> ACCENT;
            case DEVICES -> CYAN;
            case GROUPS -> OK_GREEN;
            case DIAGRAM -> CYAN;
            case OTOMOBIL -> OK_GREEN;
            case AUTO_MODE -> ACCENT;
            case CONTROLS -> WARN;
            case E_MANUEL -> OK_GREEN;
            case LINKED_CONTROLLER -> ACCENT;
            case TEST -> BAD;
            case SHIP -> CYAN;
        };
    }

    private void renderPanel(GuiGraphics graphics, int x, int y, int width, int height, String title) {
        graphics.fill(x, y, x + width, y + height, PANEL_BG);
        border(graphics, x, y, width, height, BORDER);
        graphics.fill(x + 1, y + 1, x + width - 1, y + 18, PANEL_HDR);
        graphics.fill(x + 1, y + 18, x + width - 1, y + 19, SOFT_BORDER);
        graphics.drawString(font, title, x + 8, y + 6, DIM, false);
    }

    private void renderActiveView(GuiGraphics graphics, int mouseX, int mouseY) {
        switch (activeView) {
            case TEMPLATE -> renderTemplateView(graphics);
            case DEVICES -> renderDevicesView(graphics, mouseX, mouseY);
            case GROUPS -> renderGroupsView(graphics, mouseX, mouseY);
            case DIAGRAM -> renderDiagramView(graphics, mouseX, mouseY);
            case OTOMOBIL -> renderOtomobilView(graphics, mouseX, mouseY);
            case AUTO_MODE -> renderAutoModeView(graphics, mouseX, mouseY);
            case CONTROLS -> renderControlsView(graphics, mouseX, mouseY);
            case E_MANUEL -> renderEmanuelView(graphics, mouseX, mouseY);
            case LINKED_CONTROLLER -> renderLinkedControllerView(graphics, mouseX, mouseY);
            case TEST -> renderTestView(graphics, mouseX, mouseY);
            case SHIP -> renderShipView(graphics, mouseX, mouseY);
        }
    }

    private void renderTemplateView(GuiGraphics graphics) {
        FccGuiSnapshot snapshot = snapshotOrEmpty();
        int x = leftPos + MAIN_X + 18;
        int y = topPos + MAIN_Y + 38;
        int cardW = (MAIN_W - 54) / 4;
        renderMetricCard(graphics, x, y, cardW, 54, "Devices", String.valueOf(snapshot.deviceCount()), CYAN);
        renderMetricCard(graphics, x + cardW + 6, y, cardW, 54, "Groups", String.valueOf(snapshot.groupCount()), OK_GREEN);
        renderMetricCard(graphics, x + (cardW + 6) * 2, y, cardW, 54, "Controls", String.valueOf(snapshot.controlCount()), WARN);
        renderMetricCard(graphics, x + (cardW + 6) * 3, y, cardW, 54, "Ungrouped", String.valueOf(snapshot.ungroupedDeviceCount()), ACCENT);

        int stageY = y + 78;
        renderStageRow(graphics, x, stageY, MAIN_W - 36, "1", "Starter", snapshot.groupCount() > 0 || snapshot.controlCount() > 0, "Zeppelin starter available");
        renderStageRow(graphics, x, stageY + 38, MAIN_W - 36, "2", "Hardware", snapshot.deviceCount() > 0, snapshot.deviceCount() + " linked cards found");
        renderStageRow(graphics, x, stageY + 76, MAIN_W - 36, "3", "Layout", snapshot.groupCount() > 0 && snapshot.ungroupedDeviceCount() == 0, snapshot.groupCount() + " zones, " + snapshot.ungroupedDeviceCount() + " unassigned");
        renderStageRow(graphics, x, stageY + 114, MAIN_W - 36, "4", "Controls", snapshot.controlCount() > 0, snapshot.controlCount() + " cockpit controls");

        renderMiniList(graphics, x, topPos + MAIN_Y + MAIN_H - 72, MAIN_W - 36, snapshot.overviewLines(), TEXT);
    }

    private void renderEmanuelView(GuiGraphics graphics, int mouseX, int mouseY) {
        FccGuiSnapshot snapshot = snapshotOrEmpty();
        FccTelemetryPacket telemetry = FccClientState.telemetryFor(menu.containerId);
        int count = telemetryBindingCount(telemetry);
        int x = leftPos + MAIN_X + 14;
        int y = topPos + MAIN_Y + 38;
        int listW = 170;
        int listRows = 8;
        int listY = y + 22;

        if (count <= 0) {
            graphics.drawString(font, "Override telemetry", x, y - 2, OK_GREEN, false);
            renderEmptyState(graphics, x, listY, MAIN_W - 32, "No row telemetry yet");
            return;
        }

        if (selectedEmanuelRowHint > 0) {
            int desiredRowIndex = selectedEmanuelRowHint - 1;
            int[] rowsMap = telemetry.bindingRows();
            if (rowsMap != null) {
                for (int idx = 0; idx < count && idx < rowsMap.length; idx++) {
                    if (rowsMap[idx] == desiredRowIndex) {
                        selectedEmanuelIndex = idx;
                        break;
                    }
                }
            }
        }

        selectedEmanuelIndex = clampIndex(selectedEmanuelIndex, count);
        selectedEmanuelRowHint = intAt(telemetry.bindingRows(), selectedEmanuelIndex, selectedEmanuelIndex) + 1;
        graphics.drawString(font, "Override telemetry", x, y - 2, OK_GREEN, false);
        graphics.drawString(font, count + " bound rows", x + 108, y - 2, DIM, false);

        int start = visibleStartIndex(selectedEmanuelIndex, count, listRows);
        int rows = Math.min(listRows, count - start);
        for (int visible = 0; visible < rows; visible++) {
            int index = start + visible;
            int rowY = listY + visible * (ROW_H + ROW_GAP);
            boolean selected = index == selectedEmanuelIndex;
            boolean hover = mouseX >= x && mouseX < x + listW && mouseY >= rowY && mouseY < rowY + ROW_H;
            boolean gyro = isGyroBinding(telemetry, index);
            // Fuse/override switch (left edge, 16x16)
            boolean overrideActive = index < emanuelOverrideActive.length && emanuelOverrideActive[index];
            int fuseX = x + 8;
            int fuseY = rowY + 5;
            int fuseColor = overrideActive ? 0xFFEE0000 : 0xFF555555; // red if active, grey if inactive
            graphics.fill(fuseX, fuseY, fuseX + 16, fuseY + 16, fuseColor);
            border(graphics, fuseX, fuseY, 16, 16, overrideActive ? 0xFFFF5555 : 0xFF999999);
            if (overrideActive) {
                graphics.drawCenteredString(font, "!", fuseX + 8, fuseY + 4, 0xFFFFFFFF); // fuse "blown" indicator
            }

            renderListRowBase(graphics, x + 22, rowY, listW - 22, ROW_H, selected, hover, gyro ? ACCENT : CYAN);
            int row = intAt(telemetry.bindingRows(), index, index) + 1;
            int rpm = intAt(telemetry.actualRpm(), index, 0);
            String rowType = emanuelRowTypeLabel(snapshot, row, gyro);
            graphics.drawString(font, "R" + Math.max(1, row), x + 32, rowY + 6, selected ? TEXT : DIM, false);
            graphics.drawString(font, rowType, x + 62, rowY + 6, gyro ? ACCENT : CYAN, false);
            graphics.drawString(font, rpm + " rpm", x + listW - 50, rowY + 6, selected ? TEXT : DIM, false);
            // Live signed RPM spark bar under the readout so the row list reads like a bank of
            // gauges instead of static text.
            int sparkX = x + listW - 50;
            int sparkW = 40;
            int sparkY = rowY + 17;
            graphics.fill(sparkX, sparkY, sparkX + sparkW, sparkY + 3, 0xFF0A141B);
            int sparkCx = sparkX + sparkW / 2;
            graphics.fill(sparkCx, sparkY - 1, sparkCx + 1, sparkY + 4, MUTED);
            float sparkFrac = Math.max(-1.0f, Math.min(1.0f, rpm / 256.0f));
            if (Math.abs(sparkFrac) > 0.01f) {
                int sparkFill = Math.max(1, Math.round(Math.abs(sparkFrac) * (sparkW / 2 - 1)));
                graphics.fill(sparkCx, sparkY, sparkCx + (sparkFrac >= 0 ? sparkFill : -sparkFill), sparkY + 3,
                    sparkFrac >= 0 ? OK_GREEN : WARN);
            }
        }
        renderOverflowHint(graphics, count, start, rows, x, listY + rows * (ROW_H + ROW_GAP), listW);

        int detailX = x + listW + 14;
        int detailW = MAIN_W - 42 - listW;
        int detailY = y + 22;
        int i = selectedEmanuelIndex;

        float thrust = floatAt(telemetry.thrustSigned(), i, 0f);
        float vertical = floatAt(telemetry.thrustVertical(), i, 0f);
        float horizontal = floatAt(telemetry.thrustHorizontal(), i, 0f);
        float tilt = floatAt(telemetry.tiltAngleDeg(), i, 0f);
        float sail = floatAt(telemetry.sailPower(), i, 0f);
        int commanded = intAt(telemetry.commandedRpm(), i, 0);
        int actual = intAt(telemetry.actualRpm(), i, 0);
        boolean gyro = isGyroBinding(telemetry, i);
        int selectedRow = intAt(telemetry.bindingRows(), i, i) + 1;
        boolean wheelMountRow = isWheelMountRow(snapshot, selectedRow);

        int cardW = (detailW - 8) / 2;
        // Check if this row has override active
        boolean overrideActive = i < emanuelOverrideActive.length && emanuelOverrideActive[i];
        int overrideRpm = i < emanuelRpmCommands.length ? emanuelRpmCommands[i] : 0;

        renderMetricCard(graphics, detailX, detailY, cardW, 54, "RPM", actual + "", CYAN);
        renderMetricCard(graphics, detailX + cardW + 8, detailY, cardW, 54, "Commanded", commanded + "", ACCENT);

        renderMetricCard(graphics, detailX, detailY + (overrideActive ? 62 : 62), cardW, 54, "Vertical thrust", signed1(vertical), OK_GREEN);
        renderMetricCard(graphics, detailX + cardW + 8, detailY + (overrideActive ? 62 : 62), cardW, 54, "Horizontal thrust", signed1(horizontal), WARN);
        renderMetricCard(graphics, detailX, detailY + (overrideActive ? 124 : 124), cardW, 54, "Signed thrust", signed1(thrust), CYAN);
        renderMetricCard(graphics, detailX + cardW + 8, detailY + (overrideActive ? 124 : 124), cardW, 54, "Sail power", oneDecimal(sail), DIM);

        graphics.fill(detailX, detailY + 188, detailX + detailW, detailY + 238, CARD_BG);
        border(graphics, detailX, detailY + 188, detailW, 50, SOFT_BORDER);
        String detailKind = gyro ? "Gyroscopic propeller" : wheelMountRow ? "Wheel mount" : "Smart/fixed propeller";
        graphics.drawString(font, detailKind, detailX + 10, detailY + 196, gyro ? ACCENT : CYAN, false);
        if (wheelMountRow && !overrideActive) {
            graphics.drawString(font, "Wheel rows expose RPM + steering angle.", detailX + 10, detailY + 210, DIM, false);
        }
        if (overrideActive) {
            graphics.drawString(font, "Override RPM: " + overrideRpm, detailX + 10, detailY + 210, ACCENT, false);
            int sliderTrackX = detailX + 126;
            int sliderTrackY = detailY + 210;
            int sliderTrackW = detailW - 138;
            graphics.fill(sliderTrackX, sliderTrackY, sliderTrackX + sliderTrackW, sliderTrackY + 8, 0xFF222222);
            int sliderX = sliderTrackX + (int) ((overrideRpm / (float) emanuelSliderRpmMax) * (sliderTrackW - 10));
            graphics.fill(sliderX, sliderTrackY - 2, sliderX + 10, sliderTrackY + 10, ACCENT);
            graphics.drawString(font, "0", sliderTrackX, detailY + 222, DIM, false);
            graphics.drawString(font, "256", sliderTrackX + sliderTrackW - 16, detailY + 222, DIM, false);
        } else {
            graphics.drawString(font, "Tilt angle: " + oneDecimal(tilt) + " deg", detailX + 10, detailY + 210, TEXT, false);
            graphics.drawString(font, "Selected row " + (intAt(telemetry.bindingRows(), i, i) + 1), detailX + 10, detailY + 224, DIM, false);
        }
    }

    private void renderStageRow(GuiGraphics graphics, int x, int y, int width, String step, String label, boolean ready, String detail) {
        graphics.fill(x, y, x + width, y + 30, ready ? CARD_ACTIVE : CARD_BG);
        border(graphics, x, y, width, 30, ready ? OK_GREEN : SOFT_BORDER);
        graphics.fill(x + 8, y + 7, x + 24, y + 23, ready ? OK_GREEN : MUTED);
        graphics.drawCenteredString(font, step, x + 16, y + 11, 0xFF071016);
        graphics.drawString(font, label, x + 34, y + 6, TEXT, false);
        graphics.drawString(font, font.plainSubstrByWidth(detail, width - 150), x + 124, y + 6, ready ? OK_GREEN : DIM, false);
    }

    // [6]
    private void renderReadinessPanel(GuiGraphics graphics, int x, int y, FccGuiSnapshot snapshot) {
        int flags = snapshot.readinessFlags();
        graphics.drawString(font, Component.translatable("gui.create_stats.fcc.readiness"), x, y, ACCENT, false);
        int rowY = y + 13;
        if (flags == 0) {
            rowY = drawReadinessRow(graphics, x, rowY, Component.translatable("gui.create_stats.fcc.ready.to_fly"), true, false);
        } else {
            if ((flags & FccGuiSnapshot.READY_NO_BINDINGS) != 0)
                rowY = drawReadinessRow(graphics, x, rowY, Component.translatable("gui.create_stats.fcc.ready.no_bindings"), false, true);
            if ((flags & FccGuiSnapshot.READY_BMATRIX_INVALID) != 0)
                rowY = drawReadinessRow(graphics, x, rowY, Component.translatable("gui.create_stats.fcc.ready.bmatrix"), false, true);
            if ((flags & FccGuiSnapshot.READY_ZERO_YAW_AUTHORITY) != 0)
                rowY = drawReadinessRow(graphics, x, rowY, Component.translatable("gui.create_stats.fcc.ready.yaw"), false, false);
            if ((flags & FccGuiSnapshot.READY_UNKNOWN_SPIN) != 0)
                rowY = drawReadinessRow(graphics, x, rowY, Component.translatable("gui.create_stats.fcc.ready.spin_unknown"), false, false);
        }
        rowY += 3;
        boolean warm = snapshot.sensorsWarmedUp();
        rowY = drawReadinessRow(graphics, x, rowY,
            Component.translatable(warm ? "gui.create_stats.fcc.ready.sensors_warmed" : "gui.create_stats.fcc.ready.sensors_warming"),
            warm, false);
        graphics.drawString(font, Component.translatable("gui.create_stats.fcc.ready.gains", snapshot.calibrationGainCount()), x, rowY, DIM, false);
    }

    // Returns the next row Y. ok = filled square + green; a severe issue = red, a soft issue = amber.
    // The filled-vs-hollow square is a non-color cue for colorblind readability.
    private int drawReadinessRow(GuiGraphics graphics, int x, int y, Component text, boolean ok, boolean severe) {
        int color = ok ? OK_GREEN : severe ? BAD : WARN;
        if (ok) {
            graphics.fill(x, y + 1, x + 6, y + 7, color);
        } else {
            border(graphics, x, y + 1, 6, 6, color);
        }
        graphics.drawString(font, text, x + 11, y, color, false);
        return y + 12;
    }

    private void renderDevicesView(GuiGraphics graphics, int mouseX, int mouseY) {
        FccGuiSnapshot snapshot = snapshotOrEmpty();
        int x = leftPos + MAIN_X + 18;
        int y = topPos + MAIN_Y + 42;
        graphics.drawString(font, snapshot.deviceCount() + " detected devices", x, y - 16, CYAN, false);
        renderDeviceRows(graphics, snapshot.deviceEntries(), x, y, MAIN_W - 36, DEVICE_ROWS, mouseX, mouseY);
    }

    private void renderGroupsView(GuiGraphics graphics, int mouseX, int mouseY) {
        FccGuiSnapshot snapshot = snapshotOrEmpty();
        int x = leftPos + MAIN_X + 18;
        int y = topPos + MAIN_Y + 42;
        int leftW = 220;
        int rightX = x + leftW + 18;
        int rightW = MAIN_W - 54 - leftW;
        graphics.drawString(font, "Groups", x, y - 16, OK_GREEN, false);
        graphics.drawString(font, "Devices", rightX, y - 16, CYAN, false);
        renderGroupRows(graphics, snapshot.groupEntries(), x, y, leftW, GROUP_ROWS, mouseX, mouseY);
        renderDeviceRows(graphics, snapshot.deviceEntries(), rightX, y, rightW, GROUP_ROWS, mouseX, mouseY);
    }

    private void renderDiagramView(GuiGraphics graphics, int mouseX, int mouseY) {
        FccGuiSnapshot snapshot = snapshotOrEmpty();
        int x = leftPos + MAIN_X + 14;
        int y = topPos + MAIN_Y + 58;
        int boardW = 322;
        int boardH = 278;
        int deviceX = x + boardW + 14;
        int deviceW = MAIN_W - 42 - boardW;
        graphics.drawString(font, "Zone Diagram", x, y - 16, CYAN, false);
        graphics.drawString(font, "Detected", deviceX, y - 16, OK_GREEN, false);
        renderDiagramBoard(graphics, snapshot, x, y, boardW, boardH, mouseX, mouseY);
        renderDiagramDeviceRows(graphics, snapshot.deviceEntries(), deviceX, y, deviceW, 9, mouseX, mouseY);
        if (!canEditShipDiagram(snapshot)) {
            renderDiagramLockOverlay(graphics, x, y, boardW, boardH, "Airship Layout disabled while Ground Layout is populated");
        }
    }

    private void renderOtomobilView(GuiGraphics graphics, int mouseX, int mouseY) {
        FccGuiSnapshot snapshot = snapshotOrEmpty();
        int x = leftPos + MAIN_X + 14;
        int y = topPos + MAIN_Y + 58;
        int boardW = 322;
        int boardH = 278;
        int deviceX = x + boardW + 14;
        int deviceW = MAIN_W - 42 - boardW;
        graphics.drawString(font, "Truck Wheel Diagram", x, y - 16, OK_GREEN, false);
        graphics.drawString(font, "Detected Wheels", deviceX, y - 16, CYAN, false);

        renderOtomobilBoard(graphics, snapshot, x, y, boardW, boardH, mouseX, mouseY);
        renderDiagramDeviceRows(graphics, snapshot.deviceEntries(), deviceX, y, deviceW, 9, mouseX, mouseY);

        if (!canEditOtomobilDiagram(snapshot)) {
            renderDiagramLockOverlay(graphics, x, y, boardW, boardH, "Ground Layout disabled while Airship Layout is populated");
        }
    }

    private void renderAutoModeView(GuiGraphics graphics, int mouseX, int mouseY) {
        FccGuiSnapshot snapshot = snapshotOrEmpty();
        FccTelemetryPacket telemetry = FccClientState.telemetryFor(menu.containerId);
        int x = leftPos + MAIN_X + 14;
        int y = topPos + MAIN_Y + 58;
        int width = MAIN_W - 28;
        int height = MAIN_H - 86;
        int leftW = 184;
        int mapX = x + leftW + 12;
        int mapW = width - leftW - 12;
        OperateProfileState profile = resolveOperateProfile(snapshot);
        boolean airship = profile.profile() == OperateProfile.AIRSHIP && !canUseVehicleControls(snapshot);

        graphics.drawString(font, airship ? "Flight Assistant" : "Self Drive", x, y - 16, CYAN, false);
        graphics.drawString(font, airship ? "Zeplin Thrust Assist" : "Silhouette Route", mapX, y - 16, ACCENT, false);
        renderSelfDrivePanel(graphics, snapshot, telemetry, x, y, leftW, 78, true);
        renderAutoModeSummary(graphics, snapshot, telemetry, x, y + 88, leftW, height - 88);
        if (airship) {
            renderAirshipAutoMap(graphics, snapshot, telemetry, mapX, y, mapW, height, mouseX, mouseY);
        } else {
            renderAutoRouteMap(graphics, snapshot, mapX, y, mapW, height, mouseX, mouseY);
        }
    }

    private void renderAutoModeSummary(GuiGraphics graphics, FccGuiSnapshot snapshot,
                                       @Nullable FccTelemetryPacket telemetry, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, 0xFF0B141C);
        border(graphics, x, y, width, height, SOFT_BORDER);
        graphics.fill(x + 1, y + 1, x + width - 1, y + 18, 0xFF101E29);
        OperateProfileState profile = resolveOperateProfile(snapshot);
        boolean airship = profile.profile() == OperateProfile.AIRSHIP && !canUseVehicleControls(snapshot);
        graphics.drawString(font, airship ? "FLIGHT ASSIST" : "PATHFINDER LINK", x + 8, y + 6, ACCENT, false);

        int lineY = y + 28;
        drawSelfDriveLine(graphics, x + 8, lineY, width - 16, "Source", autoModeLabel(snapshot), isAutoDriveEnabled(snapshot) ? OK_GREEN : DIM);
        lineY += 18;
        drawSelfDriveLine(graphics, x + 8, lineY, width - 16, "Target", airship ? airshipAutoTargetLabel(snapshot) : selfDriveTargetLabel(snapshot), autoTargetReady(snapshot, airship) ? ACCENT : WARN);
        lineY += 18;
        drawSelfDriveLine(graphics, x + 8, lineY, width - 16, airship ? "Speed" : "Route", airship ? airshipSpeedEnvelopeLine(snapshot) : autoRouteStateLabel(snapshot), airship ? airshipSpeedEnvelopeColor(snapshot) : autoRouteStateColor(snapshot));
        lineY += 18;
        drawSelfDriveLine(graphics, x + 8, lineY, width - 16, "Dist", autoTargetDistanceText(telemetry), TEXT);
        lineY += 18;
        drawSelfDriveLine(graphics, x + 8, lineY, width - 16, airship ? "Why" : "Scanner", airship ? airshipReachabilityReason(snapshot) : snapshot.pathfinderSystemsEnabled() ? "5x5 open" : "closed", airship ? airshipSpeedEnvelopeColor(snapshot) : snapshot.pathfinderSystemsEnabled() ? OK_GREEN : WARN);

        int chipY = y + height - 56;
        int chipW = (width - 22) / 2;
        if (airship) {
            drawAutoStatusChip(graphics, x + 8, chipY, chipW, 22, "Req", formatSpeedCap(snapshot.airshipAutoRequestedSpeedCap(), snapshot.navTableMaxSpeed()), ACCENT);
            drawAutoStatusChip(graphics, x + 14 + chipW, chipY, chipW, 22, "Reach", formatSpeedCap(snapshot.airshipAutoReachableSpeedCap(), Double.NaN), airshipSpeedEnvelopeColor(snapshot));
            drawAutoStatusChip(graphics, x + 8, chipY + 28, chipW, 22, "Eff", formatSpeedCap(snapshot.airshipAutoEffectiveSpeedCap(), snapshot.navTableMaxSpeed()), airshipSpeedEnvelopeColor(snapshot));
            drawAutoStatusChip(graphics, x + 14 + chipW, chipY + 28, chipW, 22, "Props", airshipPropAuthorityChip(telemetry), airshipPropAuthorityColor(telemetry));
        } else {
            drawAutoStatusChip(graphics, x + 8, chipY, chipW, 22, "Conf", String.format(java.util.Locale.ROOT, "%.0f%%", snapshot.pathfinderRouteConfidence() * 100.0f), snapshot.pathfinderRouteAccepted() ? OK_GREEN : DIM);
            drawAutoStatusChip(graphics, x + 14 + chipW, chipY, chipW, 22, "WP", snapshot.pathfinderRouteWaypointCount() <= 0 ? "--" : (snapshot.pathfinderRouteWaypointIndex() + 1) + "/" + snapshot.pathfinderRouteWaypointCount(), TEXT);
            drawAutoStatusChip(graphics, x + 8, chipY + 28, chipW, 22, "Risk", autoHazardLabel(snapshot.pathfinderRouteHazardFlags()), autoHazardColor(snapshot.pathfinderRouteHazardFlags()));
            drawAutoStatusChip(graphics, x + 14 + chipW, chipY + 28, chipW, 22, "5x5", snapshot.pathfinderRoutePreview().size() + " pts", snapshot.pathfinderRoutePreview().size() >= 2 ? CYAN : DIM);
        }
    }

    private void renderAirshipAutoMap(GuiGraphics graphics, FccGuiSnapshot snapshot, @Nullable FccTelemetryPacket telemetry,
                                      int x, int y, int width, int height, int mouseX, int mouseY) {
        boolean active = isAutoDriveEnabled(snapshot);
        boolean targetReady = snapshot.availableSensors().contains(SensorRequirement.NAV_TABLE);
        int edge = active && targetReady ? OK_GREEN : targetReady ? CYAN : WARN;
        graphics.fill(x, y, x + width, y + height, 0xFF071018);
        border(graphics, x, y, width, height, edge);
        graphics.fill(x + 1, y + 1, x + width - 1, y + 20, 0xFF0F1D28);
        graphics.drawString(font, active ? "THRUST ASSIST ONLINE" : "THRUST ASSIST STANDBY", x + 10, y + 6, edge, false);
        String progress = autoTargetDistanceText(telemetry);
        graphics.drawString(font, progress, x + width - font.width(progress) - 10, y + 6, TEXT, false);

        int mapX = x + 10;
        int mapY = y + 28;
        int mapW = width - 20;
        int mapH = height - 38;
        graphics.fill(mapX, mapY, mapX + mapW, mapY + mapH, 0xFF0A141B);
        border(graphics, mapX, mapY, mapW, mapH, 0x553F5368);
        for (int i = 1; i < 5; i++) {
            int gy = mapY + i * mapH / 5;
            graphics.fill(mapX + 1, gy, mapX + mapW - 1, gy + 1, 0x223F5368);
        }

        double thrust = controlSignedFraction(findControl(snapshot, "Thrust Lever", "Thrust", "Throttle"));
        double yaw = controlSignedFraction(findControl(snapshot, "Yaw Wheel", "Yaw", "Steering"));
        double left = Math.max(-1.0, Math.min(1.0, thrust + yaw * 0.7));
        double right = Math.max(-1.0, Math.min(1.0, thrust - yaw * 0.7));
        int centerX = mapX + mapW / 2;
        int bodyY = mapY + mapH / 2 - 4;
        int pulse = 40 + (int) ((System.currentTimeMillis() / 120L) % 70L);
        graphics.fill(centerX - 64, bodyY - 12, centerX + 64, bodyY + 13, 0xFF111D27);
        border(graphics, centerX - 64, bodyY - 12, 128, 25, edge);
        graphics.fill(centerX - 48, bodyY - 25, centerX + 48, bodyY - 12, 0xFF152534);
        border(graphics, centerX - 48, bodyY - 25, 96, 13, SOFT_BORDER);
        renderAirshipThrustPod(graphics, centerX - 86, bodyY + 20, 58, left, CYAN, "LEFT");
        renderAirshipThrustPod(graphics, centerX + 28, bodyY + 20, 58, right, WARN, "RIGHT");
        graphics.fill(centerX - 2, bodyY - 44, centerX + 3, bodyY + 48, 0x334A6278);
        if (active && targetReady) {
            int flowY = bodyY - 58;
            for (int i = 0; i < 6; i++) {
                int fy = flowY - i * 18 + (pulse / 12) % 18;
                graphics.fill(centerX - 3, fy, centerX + 4, fy + 8, withAlpha(OK_GREEN, 70 + i * 16));
            }
        }
        String status = !targetReady ? "NO NAV TABLE TARGET" : active ? "NAV TABLE CRUISE" : "READY FOR NAV TABLE";
        graphics.drawCenteredString(font, status, centerX, mapY + mapH - 18, targetReady ? edge : WARN);
    }

    private void renderAirshipThrustPod(GuiGraphics graphics, int x, int y, int width, double value, int color, String label) {
        graphics.fill(x, y, x + width, y + 28, 0xFF101A24);
        border(graphics, x, y, width, 28, SOFT_BORDER);
        graphics.drawString(font, label, x + 6, y + 4, DIM, false);
        int barW = width - 12;
        int mid = x + 6 + barW / 2;
        graphics.fill(x + 6, y + 18, x + 6 + barW, y + 22, 0xFF071016);
        int fill = (int) Math.round((barW / 2.0) * Math.abs(value));
        if (value >= 0.0) graphics.fill(mid, y + 18, mid + fill, y + 22, color);
        else graphics.fill(mid - fill, y + 18, mid, y + 22, BAD);
        graphics.fill(mid, y + 16, mid + 1, y + 24, TEXT);
    }

    private void drawAutoStatusChip(GuiGraphics graphics, int x, int y, int width, int height,
                                    String label, String value, int color) {
        graphics.fill(x, y, x + width, y + height, 0xFF101A24);
        border(graphics, x, y, width, height, SOFT_BORDER);
        graphics.drawString(font, label, x + 5, y + 3, DIM, false);
        graphics.drawString(font, font.plainSubstrByWidth(value, width - 10), x + 5, y + 12, color, false);
    }

    private void renderAutoRouteMap(GuiGraphics graphics, FccGuiSnapshot snapshot,
                                    int x, int y, int width, int height, int mouseX, int mouseY) {
        List<BlockPos> route = snapshot.pathfinderRoutePreview();
        int edge = snapshot.pathfinderRouteAccepted() ? OK_GREEN : snapshot.pathfinderComputerPos() != null ? WARN : SOFT_BORDER;
        graphics.fill(x, y, x + width, y + height, 0xFF071018);
        border(graphics, x, y, width, height, edge);
        graphics.fill(x + 1, y + 1, x + width - 1, y + 20, 0xFF0F1D28);
        graphics.drawString(font, autoRouteStateLabel(snapshot), x + 10, y + 6, edge, false);
        String progress = selfDriveProgressLabel(snapshot);
        if (!progress.isEmpty()) {
            graphics.drawString(font, progress, x + width - font.width(progress) - 10, y + 6, TEXT, false);
        }

        int mapX = x + 10;
        int mapY = y + 28;
        int mapW = width - 20;
        int mapH = height - 38;
        graphics.fill(mapX, mapY, mapX + mapW, mapY + mapH, 0xFF0A141B);
        border(graphics, mapX, mapY, mapW, mapH, 0x553F5368);
        for (int i = 1; i < 6; i++) {
            int gx = mapX + i * mapW / 6;
            int gy = mapY + i * mapH / 6;
            graphics.fill(gx, mapY + 1, gx + 1, mapY + mapH - 1, 0x223F5368);
            graphics.fill(mapX + 1, gy, mapX + mapW - 1, gy + 1, 0x223F5368);
        }

        if (route.size() < 2) {
            graphics.drawCenteredString(font, snapshot.pathfinderComputerPos() == null ? "NO PATHFINDER" : "NO ACTIVE 5X5 ROUTE",
                mapX + mapW / 2, mapY + mapH / 2 - 4, snapshot.pathfinderComputerPos() == null ? MUTED : WARN);
            return;
        }

        RouteMapBounds bounds = routeMapBounds(route);
        int activeIndex = Math.max(0, Math.min(snapshot.pathfinderRouteWaypointIndex(), route.size() - 2));
        for (int i = 0; i < route.size() - 1; i++) {
            BlockPos a = route.get(i);
            BlockPos b = route.get(i + 1);
            int ax = mapRouteX(a, bounds, mapX, mapW);
            int ay = mapRouteY(a, bounds, mapY, mapH);
            int bx = mapRouteX(b, bounds, mapX, mapW);
            int by = mapRouteY(b, bounds, mapY, mapH);
            int color = i < activeIndex ? 0xFF2E6F56 : i == activeIndex ? ACCENT : CYAN;
            drawNeedleLine(graphics, ax, ay, bx, by, color);
        }

        BlockPos start = route.get(0);
        BlockPos target = route.get(route.size() - 1);
        fillCircle(graphics, mapRouteX(start, bounds, mapX, mapW), mapRouteY(start, bounds, mapY, mapH), 3, OK_GREEN);
        fillCircle(graphics, mapRouteX(target, bounds, mapX, mapW), mapRouteY(target, bounds, mapY, mapH), 4, ACCENT);

        double phase = (System.currentTimeMillis() % 1400L) / 1400.0;
        BlockPos a = route.get(activeIndex);
        BlockPos b = route.get(activeIndex + 1);
        double animX = a.getX() + (b.getX() - a.getX()) * phase;
        double animZ = a.getZ() + (b.getZ() - a.getZ()) * phase;
        int pulseX = mapRouteX(animX, bounds, mapX, mapW);
        int pulseY = mapRouteY(animZ, bounds, mapY, mapH);
        int pulse = 2 + (int) ((System.currentTimeMillis() / 180L) % 3L);
        fillCircle(graphics, pulseX, pulseY, pulse, 0xFFFFFFFF);
        fillCircle(graphics, pulseX, pulseY, 2, ACCENT);

        int vehicleX = mapRouteX(a, bounds, mapX, mapW);
        int vehicleY = mapRouteY(a, bounds, mapY, mapH);
        graphics.fill(vehicleX - 3, vehicleY - 3, vehicleX + 4, vehicleY + 4, 0xFF0B141C);
        border(graphics, vehicleX - 4, vehicleY - 4, 8, 8, TEXT);
    }

    private RouteMapBounds routeMapBounds(List<BlockPos> route) {
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (BlockPos pos : route) {
            minX = Math.min(minX, pos.getX());
            maxX = Math.max(maxX, pos.getX());
            minZ = Math.min(minZ, pos.getZ());
            maxZ = Math.max(maxZ, pos.getZ());
        }
        int pad = 8;
        return new RouteMapBounds(minX - pad, maxX + pad, minZ - pad, maxZ + pad);
    }

    private int mapRouteX(BlockPos pos, RouteMapBounds bounds, int x, int width) {
        return mapRouteX(pos.getX(), bounds, x, width);
    }

    private int mapRouteX(double value, RouteMapBounds bounds, int x, int width) {
        double span = Math.max(1.0, bounds.maxX() - bounds.minX());
        return x + (int) Math.round((value - bounds.minX()) / span * Math.max(1, width - 1));
    }

    private int mapRouteY(BlockPos pos, RouteMapBounds bounds, int y, int height) {
        return mapRouteY(pos.getZ(), bounds, y, height);
    }

    private int mapRouteY(double value, RouteMapBounds bounds, int y, int height) {
        double span = Math.max(1.0, bounds.maxZ() - bounds.minZ());
        return y + height - 1 - (int) Math.round((value - bounds.minZ()) / span * Math.max(1, height - 1));
    }

    private String autoRouteStateLabel(FccGuiSnapshot snapshot) {
        if (!snapshot.pathfinderSystemsEnabled()) return "PATHFINDER CLOSED";
        if (snapshot.pathfinderComputerPos() == null) return "NO PATHFINDER";
        if (!snapshot.pathfinderRouteAccepted()) return basePathfinderStatus(snapshot.pathfinderRouteStatus()).toUpperCase(java.util.Locale.ROOT);
        return snapshot.pathfinderRouteName().isBlank() ? "5X5 SILHOUETTE ROUTE" : snapshot.pathfinderRouteName().toUpperCase(java.util.Locale.ROOT);
    }

    private int autoRouteStateColor(FccGuiSnapshot snapshot) {
        if (!snapshot.pathfinderSystemsEnabled() || snapshot.pathfinderComputerPos() == null) return MUTED;
        return snapshot.pathfinderRouteAccepted() ? OK_GREEN : WARN;
    }

    private String basePathfinderStatus(String status) {
        if (status == null || status.isBlank()) return "Pathfinder hold";
        String cleaned = status;
        String prefix = "Hold: pathfinder rejected status=";
        if (cleaned.startsWith(prefix)) cleaned = cleaned.substring(prefix.length());
        prefix = "Rejected: ";
        if (cleaned.startsWith(prefix)) cleaned = cleaned.substring(prefix.length());
        return cleaned.isBlank() ? "Pathfinder hold" : font.plainSubstrByWidth(cleaned, 120);
    }

    private String autoHazardLabel(int hazards) {
        if (hazards == 0) return "Clear";
        if ((hazards & HazardFlags.UNKNOWN_CHUNK) != 0) return "Unknown";
        if ((hazards & HazardFlags.LAVA) != 0) return "Lava";
        if ((hazards & HazardFlags.LIQUID) != 0) return "Liquid";
        if ((hazards & HazardFlags.UNSTABLE) != 0) return "Unstable";
        if ((hazards & HazardFlags.TWO_BLOCK) != 0) return "2-Block";
        return "Caution";
    }

    private int autoHazardColor(int hazards) {
        if (hazards == 0) return OK_GREEN;
        if ((hazards & (HazardFlags.LAVA | HazardFlags.UNSTABLE)) != 0) return BAD;
        if ((hazards & (HazardFlags.LIQUID | HazardFlags.UNKNOWN_CHUNK | HazardFlags.TWO_BLOCK)) != 0) return WARN;
        return ACCENT;
    }

    private void renderOtomobilBoard(GuiGraphics graphics, FccGuiSnapshot snapshot, int x, int y, int width, int height, int mouseX, int mouseY) {
        float pulse = (float) (0.5 + 0.5 * Math.sin((System.currentTimeMillis() % 2200L) / 2200.0 * Math.PI * 2.0));
        graphics.fill(x, y, x + width, y + height, 0xFF0D141C);
        border(graphics, x, y, width, height, SOFT_BORDER);
        for (int gx = x + 16; gx < x + width; gx += 24) graphics.fill(gx, y + 8, gx + 1, y + height - 8, 0x221C3342);
        for (int gy = y + 16; gy < y + height; gy += 24) graphics.fill(x + 8, gy, x + width - 8, gy + 1, 0x221C3342);

        int bodyX = x + 88;
        int bodyY = y + 28;
        int bodyW = 146;
        int bodyH = 216;
        graphics.fill(bodyX, bodyY, bodyX + bodyW, bodyY + bodyH, 0xFF1A2632);
        border(graphics, bodyX, bodyY, bodyW, bodyH, 0xFF31495E);
        graphics.fill(bodyX + 18, bodyY + 20, bodyX + bodyW - 18, bodyY + 62, 0xFF203242);
        graphics.fill(bodyX + 24, bodyY + 74, bodyX + bodyW - 24, bodyY + 194, 0xFF17222E);
        int glow = FlightControlComputerScreen.withAlpha(ACCENT, 60 + (int) (pulse * 80.0f));
        graphics.fill(bodyX + 10, bodyY + bodyH - 14, bodyX + bodyW - 10, bodyY + bodyH - 10, glow);

        boolean armed = snapshot.mode() != FlightMode.OFF;
        renderBoardArmedChip(graphics, x + width - 46, y + 6, armed, pulse);
        // Per-wheel LIVE telemetry (vehiclePreview merges the 5 Hz wheel arrays); index-aligned
        // with OtomobilZone.values().
        VehiclePreview livePreview = vehiclePreview(snapshot);
        for (int i = 0; i < OtomobilZone.values().length; i++) {
            OtomobilZone zone = OtomobilZone.values()[i];
            boolean selected = i == selectedOtomobilZoneIndex;
            boolean hover = inside(mouseX, mouseY, x + zone.x, y + zone.y, zone.width, zone.height);
            int wheelRpm = i < livePreview.wheels().size() ? livePreview.wheels().get(i).rpm() : 0;
            float activity = Math.max(-1.0f, Math.min(1.0f, wheelRpm / (float) VEHICLE_MAX_WHEEL_RPM));
            renderOtomobilZone(graphics, snapshot, zone, x + zone.x, y + zone.y, selected, hover, pulse);
            renderZoneActivity(graphics, x + zone.x, y + zone.y, zone.width, zone.height, activity, armed, pulse);
            int zoneDevices = otomobilZoneDeviceCount(snapshot, zone);
            Component activityLine = wheelRpm == 0
                ? Component.literal(armed ? "Idle" : "No command").withStyle(ChatFormatting.DARK_GRAY)
                : Component.literal(String.format(java.util.Locale.ROOT, "%s %+d RPM",
                        armed ? "Live" : "Preview", wheelRpm))
                    .withStyle(armed ? (wheelRpm >= 0 ? ChatFormatting.GREEN : ChatFormatting.YELLOW) : ChatFormatting.GRAY);
            addTooltipHotspot(x + zone.x, y + zone.y, zone.width, zone.height,
                Component.literal(zone.label).withStyle(ChatFormatting.WHITE),
                Component.literal(zoneDevices == 0 ? "No wheel device assigned"
                        : zoneDevices + (zoneDevices == 1 ? " device assigned" : " devices assigned"))
                    .withStyle(zoneDevices == 0 ? ChatFormatting.GRAY : ChatFormatting.GREEN),
                activityLine,
                Component.literal("Select a wheel device, then click to assign it here.").withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    private void renderOtomobilZone(GuiGraphics graphics, FccGuiSnapshot snapshot, OtomobilZone zone, int x, int y,
                                    boolean selected, boolean hover, float pulse) {
        int count = otomobilZoneDeviceCount(snapshot, zone);
        FccGuiRouteEntry invertRoute = otomobilZoneThrottleRoute(snapshot, zone);
        boolean normalOverride = invertRoute != null && !invertRoute.invert();
        int alpha = selected ? 140 : hover ? 112 : count > 0 ? 82 : 54;
        graphics.fill(x, y, x + zone.width, y + zone.height, FlightControlComputerScreen.withAlpha(zone.color, alpha));
        border(graphics, x, y, zone.width, zone.height, selected ? ACCENT : hover ? TEXT : FlightControlComputerScreen.withAlpha(zone.color, 190));
        if (selected) {
            border(graphics, x - 2, y - 2, zone.width + 4, zone.height + 4, FlightControlComputerScreen.withAlpha(ACCENT, 160 + (int) (pulse * 70.0f)));
        }
        graphics.drawCenteredString(font, font.plainSubstrByWidth(zone.shortLabel, zone.width - 8), x + zone.width / 2, y + 6, selected ? TEXT : 0xFFE4EEF8);
        if (count > 0) {
            String badge = String.valueOf(count);
            int badgeW = Math.max(12, font.width(badge) + 6);
            graphics.fill(x + zone.width - badgeW - 3, y + zone.height - 10, x + zone.width - 3, y + zone.height - 2, 0xCC0D141C);
            graphics.drawCenteredString(font, badge, x + zone.width - badgeW / 2 - 3, y + zone.height - 10, OK_GREEN);
        }
        int toggleX = x + 5;
        int toggleY = y + zone.height - 11;
        graphics.fill(toggleX, toggleY, toggleX + 22, toggleY + 8, normalOverride ? 0xDD402018 : 0xCC0D141C);
        border(graphics, toggleX, toggleY, 22, 8, invertRoute == null ? SOFT_BORDER : normalOverride ? WARN : MUTED);
        graphics.drawString(font, "DIR", toggleX + 4, toggleY - 1, invertRoute == null ? MUTED : normalOverride ? WARN : DIM, false);
    }

    private void renderDiagramLockOverlay(GuiGraphics graphics, int x, int y, int width, int height, String text) {
        graphics.fill(x, y, x + width, y + height, 0xAA070A0F);
        graphics.fill(x + 14, y + height / 2 - 22, x + width - 14, y + height / 2 + 22, 0xDD141D27);
        border(graphics, x + 14, y + height / 2 - 22, width - 28, 44, WARN);
        graphics.drawCenteredString(font, font.plainSubstrByWidth(text, width - 48), x + width / 2, y + height / 2 - 5, WARN);
    }

    private void renderDiagramBoard(GuiGraphics graphics, FccGuiSnapshot snapshot, int x, int y, int width, int height, int mouseX, int mouseY) {
        float pulse = (float) (0.5 + 0.5 * Math.sin((System.currentTimeMillis() % 2200L) / 2200.0 * Math.PI * 2.0));
        graphics.fill(x, y, x + width, y + height, 0xFF0D141C);
        border(graphics, x, y, width, height, SOFT_BORDER);
        for (int gx = x + 16; gx < x + width; gx += 24) graphics.fill(gx, y + 8, gx + 1, y + height - 8, 0x221C3342);
        for (int gy = y + 16; gy < y + height; gy += 24) graphics.fill(x + 8, gy, x + width - 8, gy + 1, 0x221C3342);
        renderDiagramHull(graphics, x, y, width, pulse);
        boolean armed = snapshot.mode() != FlightMode.OFF;
        renderBoardArmedChip(graphics, x + width - 46, y + 6, armed, pulse);
        for (int i = 0; i < DiagramZone.values().length; i++) {
            DiagramZone zone = DiagramZone.values()[i];
            boolean selected = i == selectedDiagramZoneIndex;
            boolean hover = inside(mouseX, mouseY, x + zone.x, y + zone.y, zone.width, zone.height);
            float activity = diagramZoneSignedActivity(snapshot, zone);
            renderDiagramZone(graphics, snapshot, zone, x + zone.x, y + zone.y, selected, hover, pulse);
            renderZoneActivity(graphics, x + zone.x, y + zone.y, zone.width, zone.height, activity, armed, pulse);
            int zoneDevices = diagramZoneDeviceCount(snapshot, zone);
            Component activityLine = Math.abs(activity) < 0.02f
                ? Component.literal(armed ? "Idle" : "No command").withStyle(ChatFormatting.DARK_GRAY)
                : Component.literal(String.format(java.util.Locale.ROOT, "%s command %+.0f%%",
                        armed ? "Live" : "Preview", activity * 100.0f))
                    .withStyle(armed ? (activity >= 0 ? ChatFormatting.GREEN : ChatFormatting.YELLOW) : ChatFormatting.GRAY);
            addTooltipHotspot(x + zone.x, y + zone.y, zone.width, zone.height,
                Component.literal(zone.label).withStyle(ChatFormatting.WHITE),
                Component.literal(zoneDevices == 0 ? "No devices assigned"
                        : zoneDevices + (zoneDevices == 1 ? " device assigned" : " devices assigned"))
                    .withStyle(zoneDevices == 0 ? ChatFormatting.GRAY : ChatFormatting.GREEN),
                activityLine,
                Component.literal("Select a device, then click to assign it here.").withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    private void renderDiagramHull(GuiGraphics graphics, int x, int y, int width, float pulse) {
        int cx = x + width / 2;
        int hull = 0xFF1A2632;
        int hullEdge = 0xFF31495E;
        for (int i = 0; i < 46; i++) {
            int half = 10 + i * 42 / 45;
            graphics.fill(cx - half, y + 22 + i, cx + half, y + 23 + i, hull);
        }
        graphics.fill(cx - 54, y + 68, cx + 54, y + 214, hull);
        graphics.fill(cx - 132, y + 94, cx + 132, y + 144, 0xFF17222E);
        graphics.fill(cx - 98, y + 154, cx + 98, y + 180, 0xFF17222E);
        for (int i = 0; i < 34; i++) {
            int half = 52 - i * 30 / 33;
            graphics.fill(cx - half, y + 214 + i, cx + half, y + 215 + i, hull);
        }
        border(graphics, cx - 54, y + 68, 108, 146, hullEdge);
        border(graphics, cx - 132, y + 94, 264, 50, 0x6631495E);
        border(graphics, cx - 98, y + 154, 196, 26, 0x6631495E);
        graphics.fill(cx - 2, y + 30, cx + 2, y + 244, 0x5539C8FF);
        int glow = FlightControlComputerScreen.withAlpha(ACCENT, 70 + (int) (pulse * 70.0f));
        graphics.fill(cx - 34, y + 231, cx + 34, y + 236, glow);
        graphics.fill(cx - 118, y + 132, cx - 102, y + 137, FlightControlComputerScreen.withAlpha(CYAN, 50 + (int) (pulse * 70.0f)));
        graphics.fill(cx + 102, y + 132, cx + 118, y + 137, FlightControlComputerScreen.withAlpha(WARN, 50 + (int) (pulse * 70.0f)));
        for (int i = 0; i < 7; i++) {
            int rx = cx - 36 + i * 12;
            graphics.fill(rx, y + 78, rx + 3, y + 81, 0xFF53677A);
            graphics.fill(rx, y + 205, rx + 3, y + 208, 0xFF53677A);
        }
    }

    private void renderDiagramZone(GuiGraphics graphics, FccGuiSnapshot snapshot, DiagramZone zone, int x, int y, boolean selected, boolean hover, float pulse) {
        String numbers = diagramZoneDeviceNumberLabel(snapshot, zone);
        int count = diagramZoneDeviceCount(snapshot, zone);
        int alpha = selected ? 140 : hover ? 112 : count > 0 ? 82 : 54;
        graphics.fill(x, y, x + zone.width, y + zone.height, FlightControlComputerScreen.withAlpha(zone.color, alpha));
        border(graphics, x, y, zone.width, zone.height, selected ? ACCENT : hover ? TEXT : FlightControlComputerScreen.withAlpha(zone.color, 190));
        if (selected) {
            border(graphics, x - 2, y - 2, zone.width + 4, zone.height + 4, FlightControlComputerScreen.withAlpha(ACCENT, 160 + (int) (pulse * 70.0f)));
        }
        graphics.drawCenteredString(font, font.plainSubstrByWidth(zone.shortLabel, zone.width - 8), x + zone.width / 2, y + 6, selected ? TEXT : 0xFFE4EEF8);
        if (count > 0) {
            String badge = font.plainSubstrByWidth(numbers.isEmpty() ? String.valueOf(count) : numbers, zone.width - 10);
            int badgeW = Math.max(12, font.width(badge) + 6);
            graphics.fill(x + zone.width - badgeW - 3, y + zone.height - 10, x + zone.width - 3, y + zone.height - 2, 0xCC0D141C);
            graphics.drawCenteredString(font, badge, x + zone.width - badgeW / 2 - 3, y + zone.height - 10, OK_GREEN);
        }
    }

    private void renderDiagramActionButtons(GuiGraphics graphics, FccGuiSnapshot snapshot, int x, int y, int mouseX, int mouseY) {
        renderBombActionPanel(graphics, snapshot, x, y - 10, 118, mouseX, mouseY);
    }

    private void renderBombActionPanel(GuiGraphics graphics, FccGuiSnapshot snapshot, int x, int y, int width, int mouseX, int mouseY) {
        FccGuiControlEntry bay = findBayOpenControl(snapshot);
        FccGuiControlEntry safety = findBombSafetyControl(snapshot);
        FccGuiControlEntry bomb = findBombDropControl(snapshot);
        boolean bayDefined = hasRoutesForControl(snapshot, bay);
        boolean bayOpen = !bayDefined || controlFraction(bay) >= 0.5;
        boolean safetyArmed = controlFraction(safety) >= 0.5;
        boolean bombDefined = hasRoutesForControl(snapshot, bomb);
        boolean dropAllowed = bombDefined && safetyArmed && bayOpen;
        int panelH = bayDefined ? BOMB_PANEL_H : 42;
        graphics.fill(x, y, x + width, y + panelH, 0xF0101218);
        border(graphics, x, y, width, panelH, dropAllowed ? withAlpha(BAD, 190) : SOFT_BORDER);
        graphics.fill(x + 3, y + 3, x + width - 3, y + 5, safetyArmed ? BAD : WARN);
        int rowY = y + 8;
        if (bayDefined) {
            drawBayDoorButton(graphics, x + 6, rowY, width - 12, 18, bayOpen, bay != null, mouseX, mouseY);
            rowY += 24;
        }
        int safetyW = Math.min(44, Math.max(36, width / 3));
        int dropW = width - safetyW - 16;
        drawBombSafetyCap(graphics, x + 6, rowY, safetyW, 24, safetyArmed, safety != null, mouseX, mouseY);
        drawBombDropButton(graphics, x + safetyW + 10, rowY, dropW, 24, dropAllowed, bombDefined, controlFraction(bomb) >= 0.5, mouseX, mouseY);
        if (!dropAllowed) {
            int coverX = x + safetyW + 10;
            graphics.fill(coverX + 2, rowY + 3, coverX + dropW - 2, rowY + 21, withAlpha(BAD, safetyArmed ? 42 : 96));
            graphics.fill(coverX + 8, rowY + 4, coverX + dropW - 8, rowY + 7, withAlpha(WARN, 130));
        }
    }

    private void drawBayDoorButton(GuiGraphics graphics, int x, int y, int width, int height, boolean open, boolean defined, int mouseX, int mouseY) {
        boolean hover = defined && inside(mouseX, mouseY, x, y, width, height);
        int accent = defined ? open ? OK_GREEN : CYAN : MUTED;
        graphics.fill(x, y, x + width, y + height, hover ? 0xF0233342 : 0xF013202B);
        border(graphics, x, y, width, height, hover ? accent : withAlpha(accent, 120));
        graphics.fill(x + 4, y + 4, x + 12, y + height - 4, open ? OK_GREEN : 0xFF0A1520);
        graphics.fill(x + width - 14, y + 4, x + width - 6, y + height - 4, open ? OK_GREEN : 0xFF0A1520);
        graphics.drawCenteredString(font, defined ? open ? "BAY OPEN" : "OPEN BAY" : "NO BAY LINK", x + width / 2, y + 5, defined ? TEXT : MUTED);
    }

    private void drawBombSafetyCap(GuiGraphics graphics, int x, int y, int width, int height, boolean armed, boolean defined, int mouseX, int mouseY) {
        boolean hover = defined && inside(mouseX, mouseY, x, y, width, height);
        int edge = defined ? armed ? BAD : WARN : MUTED;
        graphics.fill(x, y, x + width, y + height, 0xFF190D0D);
        border(graphics, x, y, width, height, hover ? edge : withAlpha(edge, 150));
        if (armed) {
            graphics.fill(x + 4, y + 3, x + width - 4, y + 8, BAD);
            graphics.fill(x + 8, y + 10, x + width - 8, y + 13, withAlpha(BAD, 90));
            graphics.drawCenteredString(font, "ARM", x + width / 2, y + 15, BAD);
        } else {
            graphics.fill(x + 4, y + 4, x + width - 4, y + height - 4, withAlpha(BAD, 180));
            graphics.fill(x + 7, y + 7, x + width - 7, y + 10, WARN);
            graphics.drawCenteredString(font, "SAFE", x + width / 2, y + 15, TEXT);
        }
    }

    private void drawBombDropButton(GuiGraphics graphics, int x, int y, int width, int height, boolean allowed, boolean defined, boolean active, int mouseX, int mouseY) {
        boolean hover = allowed && inside(mouseX, mouseY, x, y, width, height);
        int edge = !defined ? MUTED : allowed ? BAD : SOFT_BORDER;
        graphics.fill(x, y, x + width, y + height, allowed ? 0xFF2A0D12 : 0xFF15191F);
        border(graphics, x, y, width, height, hover ? ACCENT : edge);
        int cx = x + 14;
        int cy = y + height / 2;
        fillCircle(graphics, cx, cy, 7, defined ? active ? ACCENT : allowed ? BAD : MUTED : MUTED);
        fillCircle(graphics, cx, cy, 4, allowed ? 0xFFFF2020 : 0xFF2B333C);
        String label = !defined ? "NO DROP" : active ? "RELEASE" : "DROP";
        graphics.drawString(font, label, x + 28, y + 8, allowed ? TEXT : MUTED, false);
    }

    private void renderDiagramDeviceRows(GuiGraphics graphics, List<FccGuiDeviceEntry> devices, int x, int y, int width, int maxRows, int mouseX, int mouseY) {
        if (devices.isEmpty()) {
            renderEmptyState(graphics, x, y, width, "No devices");
            return;
        }
        int start = visibleStartIndex(selectedDeviceIndex, devices.size(), maxRows);
        int rows = Math.min(maxRows, devices.size() - start);
        for (int visible = 0; visible < rows; visible++) {
            int index = start + visible;
            FccGuiDeviceEntry device = devices.get(index);
            int rowY = y + visible * (ROW_H + ROW_GAP);
            boolean selected = index == selectedDeviceIndex;
            boolean hover = mouseX >= x && mouseX < x + width && mouseY >= rowY && mouseY < rowY + ROW_H;
            renderListRowBase(graphics, x, rowY, width, ROW_H, selected, hover, device.resolved() ? OK_GREEN : WARN);
                String number = "#" + device.displayNumber();
                graphics.drawString(font, number, x + 8, rowY + 5, device.grouped() ? OK_GREEN : WARN, false);
                graphics.drawString(font, font.plainSubstrByWidth(device.label(), width - 62), x + 34, rowY + 5, selected ? TEXT : DIM, false);
                String assignment = device.assignmentLabel() == null || device.assignmentLabel().isBlank()
                    ? device.categoryLabel()
                    : device.categoryLabel() + " -> " + trimLabel(device.assignmentLabel(), 18);
                graphics.drawString(font, font.plainSubstrByWidth(assignment, width - 46), x + 34, rowY + 15, CYAN, false);
            graphics.fill(x + width - 14, rowY + 9, x + width - 8, rowY + 15, device.grouped() ? OK_GREEN : WARN);
        }
        renderOverflowHint(graphics, devices.size(), start, rows, x, y + rows * (ROW_H + ROW_GAP), width);
    }

    private void renderControlsView(GuiGraphics graphics, int mouseX, int mouseY) {
        FccGuiSnapshot snapshot = snapshotOrEmpty();
        int x = leftPos + MAIN_X + 14;
        int y = topPos + MAIN_Y + 38;
        int listW = 140;
        int boardX = x + listW + 14;
        int boardW = MAIN_W - 42 - listW;
        int controlY = y + 18;
        int groupLabelY = y + 154;
        int groupY = y + 172;

        graphics.drawString(font, "Cockpit Controls", x, y, WARN, false);
        graphics.drawString(font, "Target Zones", x, groupLabelY, OK_GREEN, false);
        graphics.drawString(font, "Connection Schematic", boardX, y, ACCENT, false);
        renderControlRowsFrom(graphics, snapshot.controlEntries(), x, controlY, listW, CONTROL_LIST_ROWS, controlScrollStart, mouseX, mouseY);
        renderGroupRowsFrom(graphics, snapshot.groupEntries(), x, groupY, listW, CONTROL_GROUP_ROWS, controlGroupScrollStart, mouseX, mouseY);
        renderControlSchematic(graphics, snapshot, boardX, controlY, boardW, 270, mouseX, mouseY);
    }

    private void renderLinkedControllerView(GuiGraphics graphics, int mouseX, int mouseY) {
        FccGuiSnapshot snapshot = snapshotOrEmpty();
        ShipControls shipControls = resolveShipControls(snapshot);
        VehiclePreview preview = vehiclePreview(snapshot);
        boolean vehicleEnabled = canUseVehicleControls(snapshot);
        int x = leftPos + MAIN_X + 18;
        int y = topPos + MAIN_Y + 38;
        int width = MAIN_W - 36;

        graphics.drawString(font, vehicleEnabled ? "Vehicle Linked Controller" : "Create Linked Controller", x, y, ACCENT, false);
        String rate = "Ramp " + snapshot.linkedControllerRampIntervalTicks() + " tick" + (snapshot.linkedControllerRampIntervalTicks() == 1 ? "" : "s");
        graphics.drawString(font, rate, x + width - font.width(rate), y, CYAN, false);

        int rowsX = x + 8;
        int rowsY = y + 28;
        int rowW = width - 16;
        for (int button = 0; button < FlightControlComputerMenu.LINKED_CONTROLLER_BUTTON_COUNT; button++) {
            int rowY = rowsY + button * FlightControlComputerMenu.LINKED_CONTROLLER_ROW_HEIGHT;
            boolean configured = (snapshot.linkedControllerConfiguredMask() & (1 << button)) != 0;
            boolean input = (snapshot.linkedControllerInputMask() & (1 << button)) != 0;
            int signal = linkedControllerSignal(snapshot, button);
            int color = input ? ACCENT : configured ? OK_GREEN : MUTED;
            boolean hover = inside(mouseX, mouseY, rowsX, rowY - 4, rowW, 28);
            graphics.fill(rowsX, rowY - 4, rowsX + rowW, rowY + 24, hover ? CARD_HOVER : configured ? CARD_ACTIVE : CARD_BG);
            border(graphics, rowsX, rowY - 4, rowW, 28, input ? ACCENT : configured ? OK_GREEN : SOFT_BORDER);
            graphics.fill(rowsX, rowY - 4, rowsX + 4, rowY + 24, color);
            graphics.drawString(font, LINKED_CONTROLLER_KEYS[button], rowsX + 12, rowY + 4, color, false);
            graphics.drawString(font, font.plainSubstrByWidth(linkedControllerActionLabel(vehicleEnabled, button), 176), rowsX + 58, rowY + 4, TEXT, false);
            renderSlotBackdrop(graphics, leftPos + FlightControlComputerMenu.LINKED_CONTROLLER_SLOT_A_X - 1, topPos + rowSlotY(button) - 1);
            renderSlotBackdrop(graphics, leftPos + FlightControlComputerMenu.LINKED_CONTROLLER_SLOT_B_X - 1, topPos + rowSlotY(button) - 1);
            renderTinyProgress(graphics, rowsX + rowW - 96, rowY + 6, 58, signal, 15, color);
            String sig = String.valueOf(signal);
            graphics.drawString(font, sig, rowsX + rowW - font.width(sig) - 10, rowY + 4, color, false);
        }

        int gaugeY = rowsY + FlightControlComputerMenu.LINKED_CONTROLLER_BUTTON_COUNT * FlightControlComputerMenu.LINKED_CONTROLLER_ROW_HEIGHT + 16;
        renderLinkedAxis(graphics, x + 18, gaugeY, 136, vehicleEnabled ? "Steering" : "Wheel", shipControls.yaw(), CYAN);
        renderLinkedAxis(graphics, x + 170, gaugeY, 136, "Thrust", shipControls.thrust(), ACCENT);
        if (vehicleEnabled) {
            int pipX = x + 322;
            int pipY = gaugeY;
            int pipW = width - 322 - 8;
            renderSelfDrivePanel(graphics, snapshot, FccClientState.telemetryFor(menu.containerId), pipX, pipY, pipW, 74, true);
        } else {
            renderLinkedAxis(graphics, x + 322, gaugeY, 136, "Levitation", shipControls.trim(), OK_GREEN);
        }
    }

    private void renderLinkedAxis(GuiGraphics graphics, int x, int y, int width, String label,
                                  @Nullable FccGuiControlEntry control, int color) {
        graphics.fill(x, y, x + width, y + 46, 0xFF0D141C);
        border(graphics, x, y, width, 46, control == null ? SOFT_BORDER : color);
        graphics.drawString(font, label, x + 8, y + 7, control == null ? MUTED : color, false);
        graphics.drawString(font, formatControlValue(control), x + width - font.width(formatControlValue(control)) - 8, y + 7, control == null ? MUTED : TEXT, false);
        renderValueTrack(graphics, x + 10, y + 27, width - 20, 10,
                control == null ? 0.0 : control.value(),
                control == null ? -1.0 : control.inputMin(),
                control == null ? 1.0 : control.inputMax(),
                control == null ? 0.0 : control.neutralValue());
    }

    private static int rowSlotY(int button) {
        return FlightControlComputerMenu.LINKED_CONTROLLER_SLOT_START_Y
                + button * FlightControlComputerMenu.LINKED_CONTROLLER_ROW_HEIGHT;
    }

    private static int linkedControllerSignal(FccGuiSnapshot snapshot, int button) {
        return (snapshot.linkedControllerSignalPack() >> (button * 4)) & 0xF;
    }

    private void renderControlSchematic(GuiGraphics graphics, FccGuiSnapshot snapshot, int x, int y, int width, int height,
                                        int mouseX, int mouseY) {
        FccGuiControlEntry control = selectedControl(snapshot);
        FccGuiGroupEntry target = selectedGroup(snapshot);
        List<FccGuiRouteEntry> routes = selectedRoutes(snapshot);
        graphics.fill(x, y, x + width, y + height, 0xFF0D141C);
        border(graphics, x, y, width, height, SOFT_BORDER);
        graphics.fill(x + 1, y + 1, x + width - 1, y + 22, 0xFF14202B);
        String targetLine = target == null ? "No target selected" : trimLabel(target.label(), 24) + " / " + target.deviceCount() + " dev";
        graphics.drawString(font, font.plainSubstrByWidth(targetLine, width - 92), x + 10, y + 8, target == null ? MUTED : OK_GREEN, false);
        String count = routes.size() + " line" + (routes.size() == 1 ? "" : "s");
        graphics.drawString(font, count, x + width - font.width(count) - 10, y + 8, routes.isEmpty() ? MUTED : ACCENT, false);

        if (control == null) {
            graphics.drawCenteredString(font, "No cockpit control selected", x + width / 2, y + height / 2 - 6, DIM);
            return;
        }

        int controlX = x + 14;
        int controlY = y + 92;
        int controlW = 118;
        int controlH = 54;
        renderControlNode(graphics, control, controlX, controlY, controlW, controlH);

        if (routes.isEmpty()) {
            int emptyX = controlX + controlW + 24;
            int emptyW = Math.max(70, x + width - emptyX - 18);
            graphics.drawString(font, "No connection lines", emptyX, y + 104, WARN, false);
            graphics.drawString(font, font.plainSubstrByWidth(displayControlType(control) + " is ready for a target zone", emptyW), emptyX, y + 118, DIM, false);
            return;
        }

        int start = Math.max(0, Math.min(controlRouteScrollStart, Math.max(0, routes.size() - CONTROL_ROUTE_ROWS)));
        int rows = Math.min(CONTROL_ROUTE_ROWS, routes.size() - start);
        int nodeW = 130;
        int nodeH = 32;
        int nodeX = x + width - nodeW - 14;
        int firstY = y + 38;
        int wireStartX = controlX + controlW;
        int wireStartY = controlY + controlH / 2;
        boolean armed = snapshot.mode() != FlightMode.OFF;
        float deflection = (float) controlSignedFraction(control);
        boolean flowing = armed && Math.abs(deflection) > 0.02f;
        for (int visible = 0; visible < rows; visible++) {
            int index = start + visible;
            FccGuiRouteEntry route = routes.get(index);
            int nodeY = firstY + visible * 40;
            boolean selected = index == selectedRouteIndex;
            int color = routeTone(route);
            drawSchematicWire(graphics, wireStartX, wireStartY, nodeX, nodeY + nodeH / 2, selected ? ACCENT : color, selected);
            if (flowing) {
                drawWireFlow(graphics, wireStartX, wireStartY, nodeX, nodeY + nodeH / 2, Math.abs(deflection));
            }
            // Live contribution of this route right now (deflection oriented by scale/invert).
            float liveSigned = deflection;
            if (route.scale() < 0.0) liveSigned = -liveSigned;
            if (route.invert()) liveSigned = -liveSigned;
            renderRouteNode(graphics, snapshot, route, nodeX, nodeY, nodeW, nodeH, selected,
                    inside(mouseX, mouseY, nodeX, nodeY, nodeW, nodeH), color,
                    armed && Math.abs(liveSigned) > 0.02f ? liveSigned : Float.NaN);
        }
        renderListScrollbar(graphics, x + width - 7, firstY, 6, rows * 40 - 8, routes.size(), start, CONTROL_ROUTE_ROWS, ACCENT);
    }

    /** [7] */
    private void drawWireFlow(GuiGraphics graphics, int startX, int startY, int endX, int endY, float intensity) {
        int midX = startX + Math.max(18, (endX - startX) / 2);
        int len1 = Math.abs(midX - startX);
        int len2 = Math.abs(endY - startY);
        int len3 = Math.abs(endX - midX);
        int total = Math.max(1, len1 + len2 + len3);
        long period = (long) (1100 - 700 * Math.min(1.0f, intensity));
        int alpha = 120 + (int) (135 * Math.min(1.0f, intensity));
        for (int dot = 0; dot < 2; dot++) {
            float phase = ((System.currentTimeMillis() + dot * period / 2) % period) / (float) period;
            int dist = (int) (phase * total);
            int px;
            int py;
            if (dist < len1) {
                px = startX + Integer.signum(midX - startX) * dist;
                py = startY;
            } else if (dist < len1 + len2) {
                px = midX;
                py = startY + Integer.signum(endY - startY) * (dist - len1);
            } else {
                px = midX + Integer.signum(endX - midX) * (dist - len1 - len2);
                py = endY;
            }
            graphics.fill(px - 1, py - 1, px + 2, py + 2, withAlpha(0xFFFFFFFF, alpha));
        }
    }

    private void renderControlNode(GuiGraphics graphics, FccGuiControlEntry control, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, CARD_ACTIVE);
        border(graphics, x, y, width, height, WARN);
        graphics.fill(x, y, x + 4, y + height, WARN);
        graphics.drawString(font, font.plainSubstrByWidth(control.label(), width - 18), x + 10, y + 8, TEXT, false);
        graphics.drawString(font, displayControlType(control), x + 10, y + 21, DIM, false);
        renderValueTrack(graphics, x + 10, y + 38, width - 20, 8, control.value(), control.inputMin(), control.inputMax(), control.neutralValue());
    }

    private void renderRouteNode(GuiGraphics graphics, FccGuiSnapshot snapshot, FccGuiRouteEntry route, int x, int y, int width, int height,
                                 boolean selected, boolean hover, int color, float liveSigned) {
        graphics.fill(x, y, x + width, y + height, selected ? CARD_ACTIVE : hover ? CARD_HOVER : CARD_BG);
        border(graphics, x, y, width, height, selected ? ACCENT : hover ? color : SOFT_BORDER);
        graphics.fill(x, y, x + 4, y + height, color);
        // Live contribution readout: what this route is sending RIGHT NOW (NaN = idle/disarmed).
        String label = font.plainSubstrByWidth(route.targetLabel(), width - 18);
        if (!Float.isNaN(liveSigned)) {
            String livePct = String.format(java.util.Locale.ROOT, "%+.0f%%", liveSigned * 100.0f);
            graphics.drawString(font, livePct, x + width - font.width(livePct) - 5, y + 5,
                liveSigned >= 0 ? OK_GREEN : WARN, false);
            label = font.plainSubstrByWidth(route.targetLabel(), width - 24 - font.width(livePct));
        }
        graphics.drawString(font, label, x + 10, y + 5, selected ? TEXT : DIM, false);
        int deviceCount = routeTargetDeviceCount(snapshot, route);
        String devices = deviceCount < 0 ? "? dev" : deviceCount + " dev";
        String detail = routeEffectLabel(snapshot, route) + " / " + devices;
        graphics.drawString(font, font.plainSubstrByWidth(detail, width - 18), x + 10, y + 17, selected ? ACCENT : MUTED, false);
    }

    private void drawSchematicWire(GuiGraphics graphics, int startX, int startY, int endX, int endY, int color, boolean selected) {
        int midX = startX + Math.max(18, (endX - startX) / 2);
        if (selected) {
            drawWireSegment(graphics, startX, startY, midX, startY, 0xAAFFFFFF, 3);
            drawWireSegment(graphics, midX, startY, midX, endY, 0xAAFFFFFF, 3);
            drawWireSegment(graphics, midX, endY, endX, endY, 0xAAFFFFFF, 3);
        }
        drawWireSegment(graphics, startX, startY, midX, startY, color, 1);
        drawWireSegment(graphics, midX, startY, midX, endY, color, 1);
        drawWireSegment(graphics, midX, endY, endX, endY, color, 1);
        graphics.fill(startX - 2, startY - 2, startX + 3, startY + 3, color);
        graphics.fill(endX - 2, endY - 2, endX + 3, endY + 3, color);
    }

    private void drawWireSegment(GuiGraphics graphics, int startX, int startY, int endX, int endY, int color, int thickness) {
        int half = Math.max(0, thickness / 2);
        if (startY == endY) {
            int left = Math.min(startX, endX);
            int right = Math.max(startX, endX);
            graphics.fill(left, startY - half, right + 1, startY + half + 1, color);
            return;
        }
        int top = Math.min(startY, endY);
        int bottom = Math.max(startY, endY);
        graphics.fill(startX - half, top, startX + half + 1, bottom + 1, color);
    }

    private void renderTestView(GuiGraphics graphics, int mouseX, int mouseY) {
        FccGuiSnapshot snapshot = snapshotOrEmpty();
        boolean vehicleEnabled = canUseVehicleControls(snapshot);
        VehiclePreview preview = vehiclePreview(snapshot);
        int x = leftPos + MAIN_X + 18;
        int y = topPos + MAIN_Y + 42;
        int listW = 160;
        renderControlRows(graphics, snapshot.controlEntries(), x, y, listW, 7, mouseX, mouseY);
        FccGuiControlEntry control = selectedControl(snapshot);
        int boardX = x + listW + 22;
        int boardW = MAIN_W - listW - 62;
        int boardH = 252;
        graphics.fill(boardX, y, boardX + boardW, y + boardH, PANEL_ALT);
        border(graphics, boardX, y, boardW, boardH, SOFT_BORDER);
        if (control == null) {
            graphics.drawCenteredString(font, "No control selected", boardX + boardW / 2, y + 112, DIM);
            return;
        }
        graphics.drawString(font, trimLabel(control.label(), 32), boardX + 18, y + 18, TEXT, false);
        graphics.drawString(font, displayControlType(control), boardX + 18, y + 34, DIM, false);
        String value = String.format(java.util.Locale.ROOT, "%.2f", control.value());
        graphics.drawString(font, value, boardX + boardW - font.width(value) - 18, y + 18, ACCENT, false);
        renderValueTrack(graphics, boardX + 26, y + 58, boardW - 52, 22, control.value(), control.inputMin(), control.inputMax(), control.neutralValue());

        int bayX = boardX + 18;
        int bayY = y + 104;
        int bayW = boardW - 36;
        graphics.fill(bayX, bayY, bayX + bayW, bayY + 74, 0xFF0D141C);
        border(graphics, bayX, bayY, bayW, 74, SOFT_BORDER);
        graphics.drawString(font, vehicleEnabled ? "Wheel Output Preview" : "Command Rail", bayX + 10, bayY + 10, CYAN, false);
        if (vehicleEnabled) {
            int rowY = bayY + 24;
            for (VehicleWheelPreview wheel : preview.wheels()) {
                String line = wheel.zone().shortLabel + "  " + String.format(java.util.Locale.ROOT, "%+d RPM", wheel.rpm())
                    + (!wheel.invert() ? "  NORM" : "") + (wheel.deviceCount() > 0 ? "  x" + wheel.deviceCount() : "");
                graphics.drawString(font, font.plainSubstrByWidth(line, bayW - 20), bayX + 10, rowY,
                        wheel.rpm() == 0 ? DIM : wheel.rpm() > 0 ? OK_GREEN : WARN, false);
                rowY += 11;
            }
        } else {
            graphics.drawString(font, "Output", bayX + 10, bayY + 26, DIM, false);
            List<FccGuiRouteEntry> routes = selectedRoutes(snapshot);
            int routeY = y + 194;
            for (int i = 0; i < Math.min(3, routes.size()); i++) {
                FccGuiRouteEntry route = routes.get(i);
                String line = routeSentence(control, route);
                graphics.drawString(font, font.plainSubstrByWidth(line, boardW - 36), boardX + 18, routeY + i * 20, i == selectedRouteIndex ? ACCENT : DIM, false);
            }
        }
    }

    private void renderShipView(GuiGraphics graphics, int mouseX, int mouseY) {
        FccGuiSnapshot snapshot = snapshotOrEmpty();
        ShipControls shipControls = resolveShipControls(snapshot);
        VehiclePreview preview = vehiclePreview(snapshot);
        FccTelemetryPacket telemetry = FccClientState.telemetryFor(menu.containerId);
        FccGuiControlEntry thrust = shipControls.thrust();
        FccGuiControlEntry yaw = shipControls.yaw();

        // Content origin and dimensions
        int x = leftPos + MAIN_X + 18;
        int y = topPos + MAIN_Y + 38;
        int width = MAIN_W - 36;   // 462
        int height = MAIN_H - 72;  // 264
        boolean vehicleEnabled = canUseVehicleControls(snapshot);
        float pulse = (float) ((Math.sin(System.currentTimeMillis() / 180.0) + 1.0) * 0.5);
        OperateProfileState profileState = resolveOperateProfile(snapshot);
        if (profileState.hybrid() && !profileState.manual()) {
            renderHybridOperateView(graphics, snapshot, profileState, x, y, width, height, mouseX, mouseY, pulse);
            return;
        }
        if (profileState.profile() == OperateProfile.AIRSHIP) {
            renderAirshipHelmView(graphics, snapshot, profileState, x, y, width, height, mouseX, mouseY, pulse);
            return;
        }

        // Background + header bar
        graphics.fill(x, y, x + width, y + height, 0xFF0A1219);
        border(graphics, x, y, width, height, vehicleEnabled ? CYAN : SOFT_BORDER);
        graphics.fill(x + 1, y + 1, x + width - 1, y + 20, 0xFF0F1D28);
        graphics.fill(x + 1, y + 20, x + width - 1, y + 21, SOFT_BORDER);
        graphics.drawString(font, "Ground Vehicle", x + 10, y + 6, vehicleEnabled ? OK_GREEN : DIM, false);
        renderOperateProfileSelector(graphics, profileState, x + 150, y + 4, mouseX, mouseY);
        String lock = vehicleEnabled ? "OTOMOBIL ACTIVE" : "DIAGRAM LOCKED";
        graphics.drawString(font, lock, x + width - font.width(lock) - 10, y + 6, vehicleEnabled ? OK_GREEN : WARN, false);

        // ── LEFT PANEL: steering wheel + throttle ──────────────────
        int leftPanelW = 175;
        int divX = x + leftPanelW;
        graphics.fill(divX, y + 22, divX + 1, y + height - 2, SOFT_BORDER);

        int wheelCx = x + 65;
        int wheelCy = y + 108;
        float helmDt = helmFrameDt();
        helmWheelDisplay = com.createstats.client.hud.HudAnim.spring(
            helmWheelDisplay, (float) controlSignedFraction(yaw), helmWheelVel, helmDt, 0.06f);
        helmThrottleDisplay = com.createstats.client.hud.HudAnim.spring(
            helmThrottleDisplay, (float) controlSignedFraction(thrust), helmThrottleVel, helmDt, 0.07f);
        drawShipWheel(graphics, wheelCx, wheelCy, 46, helmWheelDisplay, yaw != null, pulse);
        graphics.drawString(font, "L", x + 6, wheelCy - 4, yaw == null ? MUTED : CYAN, false);
        graphics.drawString(font, "R", wheelCx + 49, wheelCy - 4, yaw == null ? MUTED : WARN, false);

        int throttleX = x + 122;
        int throttleY = y + 30;
        drawShipThrottle(graphics, throttleX, throttleY, 42, SHIP_THROTTLE_H, thrust, helmThrottleDisplay, pulse);
        renderSelfDrivePanel(graphics, snapshot, telemetry, x + 8, y + 184, leftPanelW - 16, 74, true);

        // ── RIGHT PANEL: mode buttons (top) + wheel cards (bottom) ─
        int rX = divX + 8;                  // right panel left edge
        int rW = width - leftPanelW - 10;   // right panel width (~277)
        int btnH = 18;
        int btnGap = 4;

        // drive mode row (row 0)
        int row0Y = y + 26;
        int btnW4 = (rW - btnGap * 3) / 4;  // ~65px each
        FccGuiControlEntry drive4x4     = findControl(snapshot, "Drive Mode 4x4 Switch");
        FccGuiControlEntry drive2x4Front = findControl(snapshot, "Drive Mode 2x4 Front Switch");
        FccGuiControlEntry drive2x4Rear  = findControl(snapshot, "Drive Mode 2x4 Rear Switch");
        FccGuiControlEntry midAxle       = findControl(snapshot, "Mid Axle Drive Switch");
        drawVehicleModeButton(graphics, rX,                         row0Y, btnW4, btnH, "4x4",     drive4x4,     vehicleEnabled, mouseX, mouseY, OK_GREEN);
        drawVehicleModeButton(graphics, rX + (btnW4 + btnGap),      row0Y, btnW4, btnH, "2x4 FWD", drive2x4Front,vehicleEnabled, mouseX, mouseY, CYAN);
        drawVehicleModeButton(graphics, rX + (btnW4 + btnGap) * 2,  row0Y, btnW4, btnH, "2x4 RWD", drive2x4Rear, vehicleEnabled, mouseX, mouseY, CYAN);
        drawVehicleModeButton(graphics, rX + (btnW4 + btnGap) * 3,  row0Y, btnW4, btnH, "Mid Axle",midAxle,      vehicleEnabled, mouseX, mouseY, OK_GREEN);

        // steer mode row (row 1)
        int row1Y = row0Y + btnH + btnGap;
        FccGuiControlEntry steerFront = findControl(snapshot, "Steer Mode Front Switch");
        FccGuiControlEntry steerAll   = findControl(snapshot, "Steer Mode All-Wheel Switch");
        FccGuiControlEntry steerCrab  = findControl(snapshot, "Steer Mode Crab Switch");
        drawVehicleModeButton(graphics, rX,                         row1Y, btnW4, btnH, "Front",   steerFront, vehicleEnabled, mouseX, mouseY, WARN);
        drawVehicleModeButton(graphics, rX + (btnW4 + btnGap),      row1Y, btnW4, btnH, "4WS",     steerAll,   vehicleEnabled, mouseX, mouseY, WARN);
        drawVehicleModeButton(graphics, rX + (btnW4 + btnGap) * 2,  row1Y, btnW4, btnH, "Crab",    steerCrab,  vehicleEnabled, mouseX, mouseY, WARN);
        // LIVE mode badge in 4th slot of steer row
        int liveBadgeX = rX + (btnW4 + btnGap) * 3;
        int liveBadgeColor = preview.runtimeResolved() ? OK_GREEN : WARN;
        graphics.fill(liveBadgeX, row1Y, liveBadgeX + btnW4, row1Y + btnH, preview.runtimeResolved() ? 0xFF0D2218 : 0xFF221510);
        border(graphics, liveBadgeX, row1Y, btnW4, btnH, liveBadgeColor);
        graphics.drawCenteredString(font, font.plainSubstrByWidth("LIVE:" + preview.resolvedModeLabel(), btnW4 - 4),
            liveBadgeX + btnW4 / 2, row1Y + 5, liveBadgeColor);
        addTooltipHotspot(liveBadgeX, row1Y, btnW4, btnH,
            Component.literal("Live steering mode").withStyle(ChatFormatting.WHITE),
            Component.literal(preview.runtimeResolved()
                    ? "Reported by the running vehicle: " + preview.resolvedModeLabel()
                    : "Inferred from switches (arm the FCC for the live mode): " + preview.resolvedModeLabel())
                .withStyle(preview.runtimeResolved() ? ChatFormatting.GREEN : ChatFormatting.YELLOW));

        // diff lock row (row 2)
        int row2Y = row1Y + btnH + btnGap;
        int btnWHalf = (rW - btnGap) / 2;
        FccGuiControlEntry diffRear = findControl(snapshot, "Diff Lock Rear Switch");
        FccGuiControlEntry diffFull = findControl(snapshot, "Diff Lock Full Switch");
        drawVehicleModeButton(graphics, rX,                 row2Y, btnWHalf, btnH, "Diff Rear Lock", diffRear, vehicleEnabled, mouseX, mouseY, ACCENT);
        drawVehicleModeButton(graphics, rX + btnWHalf + btnGap, row2Y, btnWHalf, btnH, "Diff Full Lock", diffFull, vehicleEnabled, mouseX, mouseY, BAD);

        // wheel status cards (below mode buttons)
        int cardsTopY = row2Y + btnH + 6;
        int cardAvailH = height - (cardsTopY - y) - 4;
        int cardH = (cardAvailH - 2 * 6) / 3;
        int cardW2 = (rW - 6) / 2;

        for (int i = 0; i < preview.wheels().size(); i++) {
            VehicleWheelPreview wheel = preview.wheels().get(i);
            int col = i % 2;
            int row = i / 2;
            int cx = rX + col * (cardW2 + 6);
            int cy = cardsTopY + row * (cardH + 6);
            renderVehicleWheelCard(graphics, cx, cy, cardW2, cardH, wheel, mouseX, mouseY);
        }

        if (!vehicleEnabled) {
            graphics.drawCenteredString(font, "Populate the Ground Layout to enable vehicle controls.",
                x + width / 2, y + height - 10, WARN);
        }
    }

    private void renderHybridOperateView(GuiGraphics graphics, FccGuiSnapshot snapshot, OperateProfileState state,
                                         int x, int y, int width, int height, int mouseX, int mouseY, float pulse) {
        graphics.fill(x, y, x + width, y + height, 0xFF0A1219);
        border(graphics, x, y, width, height, WARN);
        graphics.fill(x + 1, y + 1, x + width - 1, y + 20, 0xFF1E1A12);
        graphics.fill(x + 1, y + 20, x + width - 1, y + 21, SOFT_BORDER);
        graphics.drawString(font, "Hybrid Craft", x + 10, y + 6, WARN, false);
        renderOperateProfileSelector(graphics, state, x + 150, y + 4, mouseX, mouseY);
        String lock = "CHOOSE HELM";
        graphics.drawString(font, lock, x + width - font.width(lock) - 10, y + 6, ACCENT, false);

        int glow = withAlpha(WARN, 50 + (int) (pulse * 85.0f));
        graphics.fill(x + 18, y + 48, x + width - 18, y + 50, glow);
        graphics.drawCenteredString(font, "Airship and ground layouts are both populated.", x + width / 2, y + 62, TEXT);
        graphics.drawCenteredString(font, "Pick Zeplin/Balloon or Ground Vehicle above; Auto will stay neutral here.", x + width / 2, y + 78, DIM);

        int cardW = (width - 54) / 2;
        int cardY = y + 118;
        renderOperateChoiceCard(graphics, x + 18, cardY, cardW, 92, "Zeplin/Balloon", state.airshipDevices() + " air devices",
            "Lift, yaw, thrust, altitude, safety", CYAN, mouseX, mouseY, OperateProfile.AIRSHIP);
        renderOperateChoiceCard(graphics, x + 36 + cardW, cardY, cardW, 92, "Ground Vehicle", state.groundDevices() + " wheel devices",
            "Steering, drive, diff lock, route", OK_GREEN, mouseX, mouseY, OperateProfile.GROUND);
        graphics.drawCenteredString(font, font.plainSubstrByWidth(state.reason(), width - 40), x + width / 2, y + height - 26, ACCENT);
    }

    private void renderOperateChoiceCard(GuiGraphics graphics, int x, int y, int width, int height, String title,
                                         String value, String detail, int color, int mouseX, int mouseY,
                                         OperateProfile profile) {
        boolean hover = inside(mouseX, mouseY, x, y, width, height);
        boolean active = manualOperateProfile == profile;
        graphics.fill(x, y, x + width, y + height, active ? CARD_ACTIVE : hover ? CARD_HOVER : CARD_BG);
        border(graphics, x, y, width, height, active ? color : hover ? ACCENT : SOFT_BORDER);
        graphics.fill(x, y, x + width, y + 4, color);
        graphics.drawString(font, title, x + 10, y + 14, color, false);
        graphics.drawString(font, value, x + 10, y + 36, TEXT, false);
        graphics.drawString(font, font.plainSubstrByWidth(detail, width - 20), x + 10, y + 58, DIM, false);
    }

    private void renderAirshipHelmView(GuiGraphics graphics, FccGuiSnapshot snapshot, OperateProfileState state,
                                       int x, int y, int width, int height, int mouseX, int mouseY, float pulse) {
        ShipControls shipControls = resolveShipControls(snapshot);
        FccTelemetryPacket telemetry = FccClientState.telemetryFor(menu.containerId);
        FccGuiControlEntry thrust = shipControls.thrust();
        FccGuiControlEntry yaw = shipControls.yaw();
        FccGuiControlEntry trim = shipControls.trim();
        boolean armed = snapshot.mode() != FlightMode.OFF;
        boolean hasGimbal = snapshot.availableSensors().contains(SensorRequirement.GIMBAL);
        boolean hasAltitude = snapshot.availableSensors().contains(SensorRequirement.ALTITUDE);
        boolean hasVelocity = snapshot.availableSensors().contains(SensorRequirement.VELOCITY);
        boolean ready = thrust != null && yaw != null && trim != null && hasGimbal && hasAltitude;

        graphics.fill(x, y, x + width, y + height, 0xFF07121A);
        border(graphics, x, y, width, height, ready ? CYAN : WARN);
        graphics.fill(x + 1, y + 1, x + width - 1, y + 20, 0xFF0D2230);
        graphics.fill(x + 1, y + 20, x + width - 1, y + 21, SOFT_BORDER);
        graphics.drawString(font, "Zeplin / Balloon Helm", x + 10, y + 6, ready ? CYAN : WARN, false);
        renderOperateProfileSelector(graphics, state, x + 150, y + 4, mouseX, mouseY);
        String stateText = !ready ? "NEEDS SETUP" : !armed ? "READY" : "AIRBORNE";
        graphics.drawString(font, stateText, x + width - font.width(stateText) - 10, y + 6, ready && armed ? OK_GREEN : ready ? CYAN : WARN, false);

        // Arm/disarm transition flash: a brief bright border pulse marks the exact moment the
        // craft went live or safe, so the state change is felt, not just read.
        if (helmArmedInit && armed != helmArmedSeen) {
            helmArmedChangeMs = System.currentTimeMillis();
        }
        helmArmedSeen = armed;
        helmArmedInit = true;
        if (helmArmedChangeMs != Long.MIN_VALUE) {
            float flashT = (System.currentTimeMillis() - helmArmedChangeMs) / 700.0f;
            if (flashT < 1.0f) {
                int flashAlpha = Math.round(220 * (1.0f - com.createstats.client.hud.HudAnim.easeOutCubic(flashT)));
                border(graphics, x, y, width, height, withAlpha(armed ? OK_GREEN : WARN, flashAlpha));
            }
        }
        // Active warnings flash the helm rim (same shared effect as the in-flight HUDs).
        String helmWarning = snapshot.lastWarning();
        if (helmWarning != null && !helmWarning.isEmpty()) {
            com.createstats.client.hud.HudEffects.warningRimFlash(graphics, x, y, width, height, true, WARN);
        }

        // Air-flow trace is a live-state cue, not decoration: it only moves while the craft is
        // armed (props able to spin), and holds a faint static line when safe.
        for (int i = 0; i < 8; i++) {
            int flowX = x + 24 + i * 34;
            int flowY = armed
                ? y + 226 - (int) (Math.sin((System.currentTimeMillis() / 210.0) + i * 0.7) * 9.0)
                : y + 226;
            graphics.fill(flowX, flowY, flowX + 18, flowY + 2,
                withAlpha(CYAN, armed ? 48 + (int) (pulse * 70.0f) : 28));
        }

        int divX = x + 302;
        graphics.fill(divX, y + 22, divX + 1, y + height - 2, SOFT_BORDER);
        float helmDt = helmFrameDt();
        helmWheelDisplay = com.createstats.client.hud.HudAnim.spring(
            helmWheelDisplay, (float) controlSignedFraction(yaw), helmWheelVel, helmDt, 0.06f);
        helmThrottleDisplay = com.createstats.client.hud.HudAnim.spring(
            helmThrottleDisplay, (float) controlSignedFraction(thrust), helmThrottleVel, helmDt, 0.07f);
        helmTrimDisplay = com.createstats.client.hud.HudAnim.spring(
            helmTrimDisplay, (float) controlSignedFraction(trim), helmTrimVel, helmDt, 0.08f);
        drawShipWheel(graphics, x + 65, y + 108, 46, helmWheelDisplay, yaw != null, pulse);
        drawShipThrottle(graphics, x + 122, y + 30, 42, SHIP_THROTTLE_H, thrust, helmThrottleDisplay, pulse);
        drawAirshipTrimCompact(graphics, x + 234, y + 104, 36, trim, helmTrimDisplay, pulse);

        int cardX = divX + 12;
        int cardW = width - 314;
        renderAirshipStatusCard(graphics, cardX, y + 34, cardW, 44, "Profile", state.manual() ? "Manual override" : "Auto detected", state.reason(), CYAN);
        // Live instrument cluster (replaces the old text-only Sensors and Governors cards; the
        // sensor state lives on as the LED row, the V/H thrust sums as the footer line).
        renderHelmFlightData(graphics, snapshot, telemetry, cardX, y + 86, cardW, 100);
        graphics.drawString(font, "Zone Switches", x + 16, y + 174, ACCENT, false);
        graphics.drawString(font, countDiagramSwitchControls(snapshot) + "/" + DiagramZone.values().length, x + width - 46, y + 174, countDiagramSwitchControls(snapshot) > 0 ? OK_GREEN : WARN, false);
        renderShipOverheadSwitches(graphics, snapshot, x, y + 156, width, mouseX, mouseY, pulse);
    }

    /** [8] */
    private void renderHelmFlightData(GuiGraphics graphics, FccGuiSnapshot snapshot,
                                      @Nullable FccTelemetryPacket telemetry, int x, int y, int width, int height) {
        boolean live = telemetry != null;
        graphics.fill(x, y, x + width, y + height, CARD_BG);
        border(graphics, x, y, width, height, SOFT_BORDER);
        graphics.fill(x, y, x + 4, y + height, live ? CYAN : WARN);
        graphics.drawString(font, "Flight Data", x + 10, y + 4, DIM, false);
        String liveTag = live ? "LIVE" : "NO SIGNAL";
        graphics.drawString(font, liveTag, x + width - font.width(liveTag) - 6, y + 4, live ? OK_GREEN : WARN, false);

        com.createstats.flight.client.FccHud.Theme theme = com.createstats.flight.client.FccHud.Theme.NAVY;
        if (live) {
            com.createstats.flight.client.FccHud.drawCompass(graphics, font, x + 8, y + 15, width - 16, 12,
                telemetry.headingDeg(), theme);
        } else {
            graphics.fill(x + 8, y + 15, x + width - 8, y + 27, 0xFF0E2A4D);
            graphics.drawCenteredString(font, "---", x + width / 2, y + 17, MUTED);
        }

        double speed = live
            ? Math.sqrt(telemetry.velX() * telemetry.velX() + telemetry.velY() * telemetry.velY()
                + telemetry.velZ() * telemetry.velZ())
            : 0.0;
        int rowY = y + 31;
        drawHelmDataRow(graphics, x + 10, rowY, "ALT",
            live ? String.format(java.util.Locale.ROOT, "%.1f m", telemetry.altitude()) : "---", live);
        drawHelmDataRow(graphics, x + 10, rowY + 11, "CLM",
            live ? String.format(java.util.Locale.ROOT, "%+.1f m/s", telemetry.climbRate()) : "---", live);
        drawHelmDataRow(graphics, x + 10, rowY + 22, "SPD",
            live ? String.format(java.util.Locale.ROOT, "%.1f m/s", speed) : "---", live);
        drawHelmDataRow(graphics, x + 10, rowY + 33, "ATT",
            live ? String.format(java.util.Locale.ROOT, "P%+.0f° R%+.0f°", telemetry.pitchDeg(), telemetry.rollDeg()) : "---", live);

        com.createstats.flight.client.FccHud.drawSensorLeds(graphics, font, x + 10, y + height - 21,
            snapshot.availableSensors().contains(SensorRequirement.GIMBAL),
            snapshot.availableSensors().contains(SensorRequirement.ALTITUDE),
            snapshot.availableSensors().contains(SensorRequirement.VELOCITY),
            snapshot.availableSensors().contains(SensorRequirement.NAV_TABLE), theme);
        String sums = "V " + airshipTelemetrySum(telemetry, true) + "  H " + airshipTelemetrySum(telemetry, false);
        graphics.drawString(font, sums, x + 10, y + height - 11, live ? TEXT : MUTED, false);
        addTooltipHotspot(x, y, width, height,
            Component.literal("Flight Data").withStyle(ChatFormatting.WHITE),
            Component.literal(live ? "Live telemetry from the craft (5 Hz)."
                    : "No telemetry stream. Check sensors and assembly.")
                .withStyle(live ? ChatFormatting.GREEN : ChatFormatting.YELLOW),
            Component.literal("V/H: summed vertical and horizontal propeller thrust.")
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    private void drawHelmDataRow(GuiGraphics graphics, int x, int y, String label, String value, boolean live) {
        graphics.drawString(font, label, x, y, DIM, false);
        graphics.drawString(font, value, x + 30, y, live ? TEXT : MUTED, false);
    }

    private void renderAirshipStatusCard(GuiGraphics graphics, int x, int y, int width, int height,
                                         String label, String value, String detail, int color) {
        graphics.fill(x, y, x + width, y + height, CARD_BG);
        border(graphics, x, y, width, height, SOFT_BORDER);
        graphics.fill(x, y, x + 4, y + height, color);
        graphics.drawString(font, label, x + 10, y + 6, DIM, false);
        graphics.drawString(font, font.plainSubstrByWidth(value, width - 20), x + 10, y + 18, color, false);
        graphics.drawString(font, font.plainSubstrByWidth(detail, width - 20), x + 10, y + 30, TEXT, false);
    }

    private void renderOperateProfileSelector(GuiGraphics graphics, OperateProfileState state, int x, int y, int mouseX, int mouseY) {
        drawOperateProfileChip(graphics, x, y, 34, "Auto", !state.manual(), ACCENT, mouseX, mouseY);
        drawOperateProfileChip(graphics, x + 38, y, 52, "Zeplin", state.manual() && state.profile() == OperateProfile.AIRSHIP, CYAN, mouseX, mouseY);
        drawOperateProfileChip(graphics, x + 94, y, 54, "Ground", state.manual() && state.profile() == OperateProfile.GROUND, OK_GREEN, mouseX, mouseY);
    }

    private void drawOperateProfileChip(GuiGraphics graphics, int x, int y, int width, String label, boolean active,
                                        int color, int mouseX, int mouseY) {
        boolean hover = inside(mouseX, mouseY, x, y, width, 12);
        graphics.fill(x, y, x + width, y + 12, active ? CARD_ACTIVE : hover ? CARD_HOVER : 0xCC101A24);
        border(graphics, x, y, width, 12, active ? color : SOFT_BORDER);
        graphics.drawCenteredString(font, font.plainSubstrByWidth(label, width - 4), x + width / 2, y + 2, active ? TEXT : DIM);
    }

    private String airshipSensorLabel(boolean gimbal, boolean altitude, boolean velocity) {
        return "G " + (gimbal ? "OK" : "--") + "  A " + (altitude ? "OK" : "--") + "  V " + (velocity ? "OK" : "--");
    }

    private String airshipControlLabel(@Nullable FccGuiControlEntry thrust, @Nullable FccGuiControlEntry yaw,
                                       @Nullable FccGuiControlEntry trim) {
        int count = (thrust == null ? 0 : 1) + (yaw == null ? 0 : 1) + (trim == null ? 0 : 1);
        return count + "/3 helm controls";
    }

    private String airshipTelemetrySum(@Nullable FccTelemetryPacket telemetry, boolean vertical) {
        int count = telemetryBindingCount(telemetry);
        float total = 0.0f;
        for (int i = 0; i < count; i++) {
            total += vertical ? floatAt(telemetry.thrustVertical(), i, 0.0f) : floatAt(telemetry.thrustHorizontal(), i, 0.0f);
        }
        return signed1(total);
    }

    private void renderVehicleWheelCard(GuiGraphics graphics, int x, int y, int width, int height,
                                        VehicleWheelPreview wheel, int mouseX, int mouseY) {
        boolean hover = inside(mouseX, mouseY, x, y, width, height);
        int edge = wheel.rpm() == 0 ? SOFT_BORDER : wheel.rpm() > 0 ? OK_GREEN : WARN;
        int cardFill = hover ? CARD_HOVER : CARD_BG;
        graphics.fill(x, y, x + width, y + height, cardFill);
        graphics.fill(x + 1, y + 1, x + width - 1, y + 11, 0xFF172330);
        border(graphics, x, y, width, height, edge);
        graphics.drawString(font, wheel.zone().shortLabel, x + 6, y + 2, TEXT, false);

        String rpm = String.format(java.util.Locale.ROOT, "%+d", wheel.rpm());
        graphics.drawString(font, rpm, x + width - font.width(rpm) - 6, y + 14, wheel.rpm() == 0 ? DIM : edge, false);

        String steer = wheel.steerSignal() == 0 ? "-" : (wheel.steerSignal() < 0 ? "L<" : "R>");
        int steerColor = wheel.steerSignal() == 0 ? DIM : wheel.steerSignal() < 0 ? CYAN : WARN;
        graphics.drawString(font, "S " + steer, x + 6, y + 14, steerColor, false);

        int trackX = x + 28;
        int trackY = y + 25;
        int trackW = Math.max(18, width - 34);
        graphics.fill(trackX, trackY, trackX + trackW, trackY + 2, 0xFF102030);
        graphics.fill(trackX + trackW / 2, trackY - 1, trackX + trackW / 2 + 1, trackY + 3, MUTED);
        int needleOffset = (int) Math.round((wheel.steerSignal() / 15.0) * (trackW / 2 - 3));
        int needleX = Math.max(trackX + 1, Math.min(trackX + trackW - 3, trackX + trackW / 2 + needleOffset));
        graphics.fill(needleX, trackY - 1, needleX + 2, trackY + 3, steerColor);

        if (!wheel.invert()) {
            graphics.drawString(font, "NORM", x + 6, y + 28, WARN, false);
        }
        graphics.drawString(font, wheel.deviceCount() > 0 ? "x" + wheel.deviceCount() : "empty", x + width - 24, y + 28, DIM, false);
    }

    private void drawVehicleModeButton(GuiGraphics graphics, int x, int y, int width, int height, String label,
                                       @Nullable FccGuiControlEntry control, boolean enabled,
                                       int mouseX, int mouseY, int accent) {
        boolean active = enabled && control != null && controlFraction(control) >= 0.5;
        boolean hover = inside(mouseX, mouseY, x, y, width, height);
        int edge = !enabled ? SOFT_BORDER : active ? accent : hover ? ACCENT : SOFT_BORDER;
        int text = !enabled ? MUTED : active ? TEXT : DIM;
        graphics.fill(x, y, x + width, y + height, active ? CARD_ACTIVE : hover ? CARD_HOVER : CARD_BG);
        border(graphics, x, y, width, height, edge);
        graphics.drawCenteredString(font, label, x + width / 2, y + 6, text);
    }

    private void renderSelfDrivePanel(GuiGraphics graphics, FccGuiSnapshot snapshot,
                                      @Nullable FccTelemetryPacket telemetry,
                                      int x, int y, int width, int height, boolean compact) {
        OperateProfileState profile = resolveOperateProfile(snapshot);
        boolean vehicleEnabled = canUseVehicleControls(snapshot);
        boolean airshipEnabled = profile.profile() == OperateProfile.AIRSHIP && airshipDiagramDeviceCount(snapshot) > 0 && !vehicleEnabled;
        boolean platformEnabled = vehicleEnabled || airshipEnabled;
        boolean armed = snapshot.mode() != FlightMode.OFF;
        boolean autoTracking = telemetry != null && Float.isFinite(telemetry.autoTargetDistance());
        boolean autoEnabled = isAutoDriveEnabled(snapshot) || autoTracking;
        boolean pathfinderReady = hasAcceptedPathfinderRoute(snapshot);
        boolean pathfinderLinked = snapshot.pathfinderComputerPos() != null;
        boolean hasGimbal = snapshot.availableSensors().contains(SensorRequirement.GIMBAL);
        boolean hasVelocity = snapshot.availableSensors().contains(SensorRequirement.VELOCITY);
        boolean hasNav = snapshot.availableSensors().contains(SensorRequirement.NAV_TABLE);
        boolean targetReady = airshipEnabled ? hasNav : pathfinderReady || (!pathfinderLinked && hasNav);
        boolean ready = platformEnabled && hasGimbal && hasVelocity && targetReady;

        int edge = !platformEnabled ? SOFT_BORDER : autoEnabled ? (ready ? OK_GREEN : WARN) : CYAN;
        graphics.fill(x, y, x + width, y + height, 0xFF0B141C);
        border(graphics, x, y, width, height, edge);
        graphics.fill(x + 1, y + 1, x + width - 1, y + 16, 0xFF101E29);

        String mode = !platformEnabled ? "NO LAYOUT" : airshipEnabled ? (autoEnabled ? "AIRSHIP CRUISE" : "AIRSHIP MANUAL") : !autoEnabled ? "MANUAL"
            : pathfinderReady ? "SCANNER ROUTE"
            : pathfinderLinked ? "SCANNER HOLD" : "NAV DRIVE";
        String autoMode = autoModeLabel(snapshot);
        String state = !platformEnabled ? "ASSIGN" : !ready ? "NEEDS SETUP" : !armed ? "READY" : autoTracking ? "TRACKING" : autoEnabled ? "AUTO READY" : "ARMED";
        int stateColor = !platformEnabled ? MUTED : !ready ? WARN : autoTracking ? OK_GREEN : CYAN;
        String progress = selfDriveProgressLabel(snapshot);
        String distance = autoTargetDistanceText(telemetry) + (progress.isEmpty() ? "" : "  " + progress);
        graphics.drawString(font, airshipEnabled ? "FLIGHT ASSIST" : "SELF DRIVE", x + 8, y + 5, edge, false);
        graphics.drawString(font, state, x + width - font.width(state) - 8, y + 5, stateColor, false);

        if (compact) {
            drawSelfDriveLine(graphics, x + 8, y + 20, width - 16, "MODE", mode, autoEnabled ? OK_GREEN : TEXT);
            drawSelfDriveLine(graphics, x + 8, y + 35, width - 16, "AUTO", autoMode, autoEnabled ? OK_GREEN : DIM);
            drawSelfDriveLine(graphics, x + 8, y + 50, width - 16, "TARGET", airshipEnabled ? airshipAutoTargetLabel(snapshot) : selfDriveTargetLabel(snapshot), targetReady ? ACCENT : WARN);
            drawSelfDriveLine(graphics, x + 8, y + 65, width - 16, "DIST", distance, TEXT);
            return;
        }

        int colW = (width - 26) / 2;
        drawSelfDriveChip(graphics, x + 8, y + 22, colW, 24, "Mode", mode, autoEnabled ? OK_GREEN : TEXT);
        drawSelfDriveChip(graphics, x + 14 + colW, y + 22, colW, 24, "Auto Mode", autoMode, autoEnabled ? OK_GREEN : DIM);
        drawSelfDriveChip(graphics, x + 8, y + 50, colW, 24, "Target", airshipEnabled ? airshipAutoTargetLabel(snapshot) : selfDriveTargetLabel(snapshot), targetReady ? ACCENT : WARN);
        drawSelfDriveChip(graphics, x + 14 + colW, y + 50, colW, 24, "Distance", distance, TEXT);
    }

    private void drawSelfDriveChip(GuiGraphics graphics, int x, int y, int width, int height,
                                   String label, String value, int valueColor) {
        graphics.fill(x, y, x + width, y + height, 0xFF101A24);
        border(graphics, x, y, width, height, SOFT_BORDER);
        graphics.drawString(font, label, x + 5, y + 3, DIM, false);
        graphics.drawString(font, font.plainSubstrByWidth(value, width - 10), x + 5, y + 13, valueColor, false);
    }

    private void drawSelfDriveLine(GuiGraphics graphics, int x, int y, int width,
                                   String label, String value, int valueColor) {
        graphics.drawString(font, label, x, y, DIM, false);
        int labelW = Math.min(54, font.width(label) + 10);
        graphics.drawString(font, font.plainSubstrByWidth(value, Math.max(20, width - labelW)), x + labelW, y, valueColor, false);
    }

    private boolean isAutoDriveEnabled(FccGuiSnapshot snapshot) {
        return isMasterAutoActive(snapshot) || isPathfinderModeSwitchActive(snapshot) || isNavTableModeSwitchActive(snapshot);
    }

    private boolean hasAnyAutoDriveModeControl(FccGuiSnapshot snapshot) {
        return findControl(snapshot, "Auto Drive Switch") != null
            || findControl(snapshot, "Pathfinder Auto Drive Switch") != null
            || findControl(snapshot, "Nav Table Auto Drive Switch") != null;
    }

    private boolean isMasterAutoActive(FccGuiSnapshot snapshot) {
        return controlFraction(findControl(snapshot, "Auto Drive Switch")) >= 0.5;
    }

    private boolean isPathfinderModeSwitchActive(FccGuiSnapshot snapshot) {
        return controlFraction(findControl(snapshot, "Pathfinder Auto Drive Switch")) >= 0.5;
    }

    private boolean isNavTableModeSwitchActive(FccGuiSnapshot snapshot) {
        return controlFraction(findControl(snapshot, "Nav Table Auto Drive Switch")) >= 0.5;
    }

    private String autoModeLabel(FccGuiSnapshot snapshot) {
        return switch (autoDriveMode(snapshot)) {
            case PATHFINDER -> "PATHFINDER";
            case NAV_TABLE -> "NAV TABLE";
            case FALLBACK -> "FALLBACK";
            case OFF -> "MANUAL";
        };
    }

    private String autoDriveModeButtonLabel(FccGuiSnapshot snapshot) {
        return "Auto Mode: " + autoDriveMode(snapshot).label;
    }

    private String evadeRadiusButtonLabel(FccGuiSnapshot snapshot) {
        FccGuiControlEntry control = findEvadeRadiusControl(snapshot);
        return control == null ? "Evade: --" : String.format(java.util.Locale.ROOT, "Evade: %.0fb", control.value());
    }

    private String autoApproachButtonLabel(FccGuiSnapshot snapshot) {
        FccGuiControlEntry control = findAutoApproachControl(snapshot);
        return control == null ? "Approach at: --" : String.format(java.util.Locale.ROOT, "Approach at: %.0fb", control.value());
    }

    private String autoAnnounceButtonLabel(FccGuiSnapshot snapshot) {
        FccGuiControlEntry control = findAutoAnnounceControl(snapshot);
        return control == null ? "Announce every: --" : String.format(java.util.Locale.ROOT, "Announce every: %.0fb", control.value());
    }

    private String autoReverseButtonLabel(FccGuiSnapshot snapshot) {
        FccGuiControlEntry control = findAutoReverseControl(snapshot);
        return control == null ? "Reverse: --" : String.format(java.util.Locale.ROOT, "Reverse: %.0fs", control.value());
    }

    private String pathfinderSystemsButtonLabel(FccGuiSnapshot snapshot) {
        return snapshot.pathfinderSystemsEnabled() ? "PF Systems: Open" : "PF Systems: Closed";
    }

    @Nullable
    private FccGuiControlEntry findEvadeRadiusControl(FccGuiSnapshot snapshot) {
        FccGuiControlEntry control = findControl(snapshot, AUTO_EVADE_RADIUS_LABEL);
        if (control != null) return control;
        control = findControl(snapshot, "Evade Radius");
        return control != null ? control : findControl(snapshot, "Auto Evade Radius");
    }

    @Nullable
    private FccGuiControlEntry findAutoApproachControl(FccGuiSnapshot snapshot) {
        FccGuiControlEntry control = findControl(snapshot, AUTO_APPROACH_DISTANCE_LABEL);
        if (control != null) return control;
        control = findControl(snapshot, "Approach Distance");
        return control != null ? control : findControl(snapshot, "Slowdown Distance", "Arrival Slowdown");
    }

    @Nullable
    private FccGuiControlEntry findAutoAnnounceControl(FccGuiSnapshot snapshot) {
        FccGuiControlEntry control = findControl(snapshot, AUTO_ANNOUNCE_INTERVAL_LABEL);
        if (control != null) return control;
        control = findControl(snapshot, "Announcement Interval");
        return control != null ? control : findControl(snapshot, "Announce Every", "Assistant Interval");
    }

    @Nullable
    private FccGuiControlEntry findAutoReverseControl(FccGuiSnapshot snapshot) {
        FccGuiControlEntry control = findControl(snapshot, AUTO_REVERSE_TIME_LABEL);
        if (control != null) return control;
        control = findControl(snapshot, "Reverse Time");
        return control != null ? control : findControl(snapshot, "Reverse Duration", "Unstick Reverse Time");
    }

    private AutoDriveMode autoDriveMode(FccGuiSnapshot snapshot) {
        if (isPathfinderModeSwitchActive(snapshot)) return AutoDriveMode.PATHFINDER;
        if (isNavTableModeSwitchActive(snapshot)) return AutoDriveMode.NAV_TABLE;
        if (isMasterAutoActive(snapshot)) return AutoDriveMode.FALLBACK;
        return AutoDriveMode.OFF;
    }

    private boolean autoDriveModeAvailable(FccGuiSnapshot snapshot, AutoDriveMode mode) {
        return switch (mode) {
            case OFF -> true;
            case FALLBACK -> findControl(snapshot, "Auto Drive Switch") != null
                && (snapshot.effectiveNavPos() != null || snapshot.pathfinderComputerPos() != null);
            case NAV_TABLE -> findControl(snapshot, "Nav Table Auto Drive Switch") != null
                && snapshot.effectiveNavPos() != null;
            case PATHFINDER -> findControl(snapshot, "Pathfinder Auto Drive Switch") != null
                && snapshot.pathfinderComputerPos() != null;
        };
    }

    private AutoDriveMode nextAutoDriveMode(FccGuiSnapshot snapshot) {
        AutoDriveMode current = autoDriveMode(snapshot);
        AutoDriveMode[] modes = AutoDriveMode.values();
        int start = current.ordinal();
        for (int offset = 1; offset <= modes.length; offset++) {
            AutoDriveMode candidate = modes[(start + offset) % modes.length];
            if (autoDriveModeAvailable(snapshot, candidate)) return candidate;
        }
        return AutoDriveMode.OFF;
    }

    private boolean hasAcceptedPathfinderRoute(FccGuiSnapshot snapshot) {
        return snapshot.pathfinderRouteAccepted() && snapshot.pathfinderRouteWaypointCount() >= 2;
    }

    private String selfDriveTargetLabel(FccGuiSnapshot snapshot) {
        if (hasAcceptedPathfinderRoute(snapshot)) {
            return snapshot.pathfinderRouteName().isBlank() ? "Scanner route" : trimLabel(snapshot.pathfinderRouteName(), 28);
        }
        if (snapshot.pathfinderComputerPos() != null) {
            return snapshot.pathfinderRouteStatus().isBlank() ? "Scanner route unavailable" : trimLabel(snapshot.pathfinderRouteStatus(), 28);
        }
        return snapshot.availableSensors().contains(SensorRequirement.NAV_TABLE) ? "Nav Table" : "No target";
    }

    private String airshipAutoTargetLabel(FccGuiSnapshot snapshot) {
        return snapshot.availableSensors().contains(SensorRequirement.NAV_TABLE) ? "Nav Table target" : "No Nav Table target";
    }

    private String airshipSpeedEnvelopeLine(FccGuiSnapshot snapshot) {
        String requested = formatSpeedCap(snapshot.airshipAutoRequestedSpeedCap(), snapshot.navTableMaxSpeed());
        String effective = formatSpeedCap(snapshot.airshipAutoEffectiveSpeedCap(), snapshot.navTableMaxSpeed());
        return requested + " selected / " + effective + " usable";
    }

    private String airshipReachabilityReason(FccGuiSnapshot snapshot) {
        String reason = snapshot.airshipAutoReachabilityReason();
        if (reason == null || reason.isBlank()) reason = "measuring ship response";
        return trimLabel(reason, 28);
    }

    private int airshipSpeedEnvelopeColor(FccGuiSnapshot snapshot) {
        double requested = Double.isFinite(snapshot.airshipAutoRequestedSpeedCap())
            ? snapshot.airshipAutoRequestedSpeedCap()
            : snapshot.navTableMaxSpeed();
        double effective = Double.isFinite(snapshot.airshipAutoEffectiveSpeedCap())
            ? snapshot.airshipAutoEffectiveSpeedCap()
            : snapshot.navTableMaxSpeed();
        if (effective + 0.1 < requested) return WARN;
        return Double.isFinite(snapshot.airshipAutoReachableSpeedCap()) ? OK_GREEN : CYAN;
    }

    private boolean autoTargetReady(FccGuiSnapshot snapshot, boolean airship) {
        if (airship) return snapshot.availableSensors().contains(SensorRequirement.NAV_TABLE);
        return hasAcceptedPathfinderRoute(snapshot) || snapshot.effectiveNavPos() != null;
    }

    private String autoAssistSettingsLine(FccGuiSnapshot snapshot) {
        return formatBlocks(autoAssistControlValue(snapshot, AUTO_APPROACH_DISTANCE_LABEL, 200.0))
            + " / " + formatSeconds(autoAssistControlValue(snapshot, AUTO_REVERSE_TIME_LABEL, 4.0));
    }

    private double autoAssistControlValue(FccGuiSnapshot snapshot, String label, double fallback) {
        FccGuiControlEntry control = findControl(snapshot, label);
        return control == null ? fallback : control.value();
    }

    private static String formatBlocks(double value) {
        return String.format(java.util.Locale.ROOT, "%.0fb", value);
    }

    private static String formatSeconds(double value) {
        return String.format(java.util.Locale.ROOT, "%.0fs", value);
    }

    private static String formatSpeedCap(double value, double fallback) {
        double speed = Double.isFinite(value) && value > 0.0 ? value : fallback;
        return Double.isFinite(speed) && speed > 0.0
            ? String.format(java.util.Locale.ROOT, "%.1fm/s", speed)
            : "measuring";
    }

    private static String airshipPropAuthorityChip(@Nullable FccTelemetryPacket telemetry) {
        if (telemetry == null || telemetry.propBoundCount() <= 0) return "no data";
        double horizontal = 0.0;
        double vertical = 0.0;
        float[] horizontalThrust = telemetry.thrustHorizontal();
        float[] verticalThrust = telemetry.thrustVertical();
        for (float value : horizontalThrust) horizontal += Math.abs(value);
        for (float value : verticalThrust) vertical += Math.abs(value);
        double ratio = vertical <= 1.0e-4 ? horizontal : horizontal / vertical;
        return String.format(java.util.Locale.ROOT, "%d/%d %.2f", telemetry.propResolvedCount(),
            telemetry.propBoundCount(), ratio);
    }

    private static int airshipPropAuthorityColor(@Nullable FccTelemetryPacket telemetry) {
        if (telemetry == null || telemetry.propBoundCount() <= 0) return DIM;
        if (telemetry.propResolvedCount() < telemetry.propBoundCount()) return WARN;
        return OK_GREEN;
    }

    private String pathfinderStatusLabel(FccGuiSnapshot snapshot) {
        if (snapshot.pathfinderComputerPos() == null) {
            return "Pathfinder: not linked to FCC";
        }
        String route = snapshot.pathfinderRouteName().isBlank() ? "no route" : trimLabel(snapshot.pathfinderRouteName(), 22);
        if (snapshot.pathfinderRouteAccepted()) {
            return String.format(java.util.Locale.ROOT, "Pathfinder: %s | WP %d/%d | %.0fm | %.0f%% | scanner route",
                route, snapshot.pathfinderRouteWaypointIndex() + 1, snapshot.pathfinderRouteWaypointCount(),
                snapshot.pathfinderRouteDistance(), snapshot.pathfinderRouteConfidence() * 100.0f);
        }
        String status = snapshot.pathfinderRouteStatus().isBlank() ? "waiting" : snapshot.pathfinderRouteStatus();
        return "Pathfinder: " + status;
    }

    private static String autoTargetDistanceText(@Nullable FccTelemetryPacket telemetry) {
        if (telemetry == null || !Float.isFinite(telemetry.autoTargetDistance())) {
            return "--";
        }
        return String.format(java.util.Locale.ROOT, "%.1fm", telemetry.autoTargetDistance());
    }

    private static String autoEtaText(@Nullable FccTelemetryPacket telemetry) {
        if (telemetry == null || !Float.isFinite(telemetry.autoTargetDistance())) return "--:--";
        double speed = Math.sqrt(telemetry.velX() * telemetry.velX() + telemetry.velZ() * telemetry.velZ());
        if (speed <= 0.1) return "--:--";
        int seconds = Math.max(0, (int) Math.round(telemetry.autoTargetDistance() / speed));
        return String.format(java.util.Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60);
    }

    private static String selfDriveProgressLabel(FccGuiSnapshot snapshot) {
        if (snapshot.pathfinderRouteAccepted() && snapshot.pathfinderRouteWaypointCount() > 0) {
            return "PF " + (snapshot.pathfinderRouteWaypointIndex() + 1) + "/" + snapshot.pathfinderRouteWaypointCount();
        }
        return "";
    }

    private static String selfDriveSensorLabel(boolean gimbal, boolean velocity, boolean targetReady) {
        return "G " + (gimbal ? "OK" : "--") + "  V " + (velocity ? "OK" : "--") + "  T " + (targetReady ? "OK" : "--");
    }

    /** Per-view frame delta for the helm instrument springs. */
    private float helmFrameDt() {
        long now = System.nanoTime();
        float dt = helmFrameNanos == 0L ? 1.0f / 60.0f
            : Math.min(0.25f, (now - helmFrameNanos) / 1_000_000_000.0f);
        helmFrameNanos = now;
        return dt;
    }

    private void drawShipWheel(GuiGraphics graphics, int cx, int cy, int radius, double value, boolean enabled, float pulse) {
        int rim = enabled ? mixColor(CYAN, ACCENT, pulse * 0.35f) : MUTED;
        fillCircle(graphics, cx, cy, radius, 0xFF101923);
        fillCircle(graphics, cx, cy, radius - 4, rim);
        fillCircle(graphics, cx, cy, radius - 9, 0xFF0D141C);
        graphics.fill(cx - radius + 13, cy - 3, cx + radius - 13, cy + 4, rim);
        graphics.fill(cx - 3, cy - radius + 13, cx + 4, cy + radius - 13, rim);
        double angle = value * Math.toRadians(110.0) - Math.PI / 2.0;
        int gripX = cx + (int) Math.round(Math.cos(angle) * (radius - 18));
        int gripY = cy + (int) Math.round(Math.sin(angle) * (radius - 18));
        fillCircle(graphics, gripX, gripY, 8, enabled ? ACCENT : MUTED);
        graphics.drawCenteredString(font, "STEERING", cx, cy + radius + 10, enabled ? CYAN : MUTED);
    }

    private void drawShipThrottle(GuiGraphics graphics, int x, int y, int width, int height,
                                  @Nullable FccGuiControlEntry control, double displaySigned, float pulse) {
        // displaySigned is the spring-smoothed lever position supplied by the caller, so the
        // lever glides between values instead of snapping.
        double signed = Math.max(-1.0, Math.min(1.0, displaySigned));
        int trackTop = y + 10;
        int trackBottom = y + height - 10;
        int centerY = trackTop + (trackBottom - trackTop) / 2;
        graphics.fill(x, y, x + width, y + height, 0xFF121D27);
        border(graphics, x, y, width, height, SOFT_BORDER);
        graphics.fill(x + 12, trackTop, x + width - 12, trackBottom, 0xFF071016);
        graphics.fill(x + 14, centerY - 1, x + width - 14, centerY + 1, 0xFF53677A);
        if (signed > 0.0) {
            int fillTop = centerY - (int) Math.round(signed * (centerY - trackTop));
            graphics.fill(x + 14, fillTop, x + width - 14, centerY, mixColor(OK_GREEN, ACCENT, pulse * 0.25f));
        } else if (signed < 0.0) {
            int fillBottom = centerY + (int) Math.round(-signed * (trackBottom - centerY));
            graphics.fill(x + 14, centerY, x + width - 14, fillBottom, mixColor(BAD, WARN, pulse * 0.25f));
        }
        int knobY = centerY - 7 - (int) Math.round(signed * (centerY - trackTop - 7));
        knobY = Math.max(y + 12, Math.min(y + height - 18, knobY));
        graphics.fill(x + 6, knobY, x + width - 6, knobY + 14, control == null ? MUTED : ACCENT);
        border(graphics, x + 6, knobY, width - 12, 14, TEXT);
        graphics.drawString(font, "FWD", x + width + 6, trackTop, control == null ? MUTED : OK_GREEN, false);
        graphics.drawString(font, "0", x + width + 6, centerY - 4, DIM, false);
        graphics.drawString(font, "REV", x + width + 6, trackBottom - 8, control == null ? MUTED : BAD, false);
        graphics.drawCenteredString(font, "THRUST", x + width / 2, y + height + 10, control == null ? MUTED : OK_GREEN);
        if (control != null) graphics.drawCenteredString(font, formatControlValue(control), x + width / 2, y - 14, ACCENT);
    }

    private void drawShipTrim(GuiGraphics graphics, int cx, int cy, int radius, @Nullable FccGuiControlEntry control, float pulse) {
        double value = controlSignedFraction(control);
        fillCircle(graphics, cx, cy, radius, 0xFF121D27);
        fillCircle(graphics, cx, cy, radius - 4, control == null ? MUTED : mixColor(OK_GREEN, CYAN, pulse * 0.3f));
        fillCircle(graphics, cx, cy, radius - 11, 0xFF0D141C);
        double angle = value * Math.toRadians(140.0) - Math.PI / 2.0;
        int needleX = cx + (int) Math.round(Math.cos(angle) * (radius - 15));
        int needleY = cy + (int) Math.round(Math.sin(angle) * (radius - 15));
        drawNeedleLine(graphics, cx, cy, needleX, needleY, control == null ? MUTED : ACCENT);
        fillCircle(graphics, cx, cy, 5, control == null ? MUTED : ACCENT);
        int barX = cx - 72;
        int barY = cy + radius + 28;
        renderValueTrack(graphics, barX, barY, 144, 14, control == null ? 0.0 : control.value(), control == null ? -1.0 : control.inputMin(), control == null ? 1.0 : control.inputMax(), control == null ? 0.0 : control.neutralValue());
        graphics.drawCenteredString(font, "LEVITATION", cx, cy + radius + 10, control == null ? MUTED : OK_GREEN);
    }

    private void drawAirshipTrimCompact(GuiGraphics graphics, int cx, int cy, int radius,
                                        @Nullable FccGuiControlEntry control, double displayValue, float pulse) {
        double value = Math.max(-1.0, Math.min(1.0, displayValue));
        fillCircle(graphics, cx, cy, radius, 0xFF121D27);
        fillCircle(graphics, cx, cy, radius - 4, control == null ? MUTED : mixColor(OK_GREEN, CYAN, pulse * 0.3f));
        fillCircle(graphics, cx, cy, radius - 11, 0xFF0D141C);
        double angle = value * Math.toRadians(140.0) - Math.PI / 2.0;
        int needleX = cx + (int) Math.round(Math.cos(angle) * (radius - 15));
        int needleY = cy + (int) Math.round(Math.sin(angle) * (radius - 15));
        drawNeedleLine(graphics, cx, cy, needleX, needleY, control == null ? MUTED : ACCENT);
        fillCircle(graphics, cx, cy, 5, control == null ? MUTED : ACCENT);
        graphics.drawCenteredString(font, "LEVITATION", cx, cy + radius + 10, control == null ? MUTED : OK_GREEN);
    }

    private void drawShipButton(GuiGraphics graphics, int x, int y, int width, int height, String label, int color, int mouseX, int mouseY) {
        boolean hover = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        graphics.fill(x, y, x + width, y + height, hover ? CARD_HOVER : CARD_BG);
        border(graphics, x, y, width, height, hover ? color : SOFT_BORDER);
        graphics.fill(x, y, x + 4, y + height, color);
        graphics.drawCenteredString(font, label, x + width / 2, y + Math.max(5, (height - 8) / 2), hover ? TEXT : DIM);
    }

    private void renderShipOverheadSwitches(GuiGraphics graphics, FccGuiSnapshot snapshot, int x, int y, int width,
                                            int mouseX, int mouseY, float pulse) {
        int railX = x + 14;
        int railY = y + 28;
        int railW = width - 28;
        int railH = shipSwitchRows() * SHIP_SWITCH_H + Math.max(0, shipSwitchRows() - 1) * SHIP_SWITCH_GAP
            + SHIP_SAFETY_SWITCH_H + 14;
        graphics.fill(railX - 4, railY - 4, railX + railW + 4, railY + railH, 0xFF101923);
        border(graphics, railX - 4, railY - 4, railW + 8, railH + 4, SOFT_BORDER);
        DiagramZone[] zones = DiagramZone.values();
        for (int i = 0; i < zones.length; i++) {
            DiagramZone zone = zones[i];
            int switchX = shipSwitchX(railX, i);
            int switchY = shipSwitchY(railY, i);
            drawOverheadSwitch(graphics, switchX, switchY, zone, findDiagramSwitchControl(snapshot, zone),
                    diagramZoneDeviceCount(snapshot, zone), mouseX, mouseY, pulse);
        }
            drawFlipPreventionSwitch(graphics, railX, shipSafetySwitchY(railY), railW,
                findFlipPreventionControl(snapshot), mouseX, mouseY, pulse);
    }

    private int shipSwitchX(int railX, int index) {
        return railX + (index % SHIP_SWITCH_COLUMNS) * (SHIP_SWITCH_W + SHIP_SWITCH_GAP);
    }

    private int shipSwitchY(int railY, int index) {
        return railY + (index / SHIP_SWITCH_COLUMNS) * (SHIP_SWITCH_H + SHIP_SWITCH_GAP);
    }

    private int shipSafetySwitchY(int railY) {
        return railY + shipSwitchRows() * (SHIP_SWITCH_H + SHIP_SWITCH_GAP) - SHIP_SWITCH_GAP + 2;
    }

    private int shipSwitchRows() {
        return Math.max(1, (DiagramZone.values().length + SHIP_SWITCH_COLUMNS - 1) / SHIP_SWITCH_COLUMNS);
    }

    private void drawOverheadSwitch(GuiGraphics graphics, int x, int y, DiagramZone zone,
                                    @Nullable FccGuiControlEntry control, int deviceCount,
                                    int mouseX, int mouseY, float pulse) {
        boolean on = control != null && controlFraction(control) >= 0.5;
        boolean hover = inside(mouseX, mouseY, x, y, SHIP_SWITCH_W, SHIP_SWITCH_H);
        int accent = control == null ? MUTED : deviceCount > 0 ? zone.color : mixColor(MUTED, zone.color, 0.25f);
        int fill = hover ? CARD_HOVER : on ? 0xF0202A36 : CARD_BG;
        graphics.fill(x, y, x + SHIP_SWITCH_W, y + SHIP_SWITCH_H, fill);
        border(graphics, x, y, SHIP_SWITCH_W, SHIP_SWITCH_H, hover ? accent : SOFT_BORDER);
        graphics.fill(x + 3, y + 3, x + 10, y + SHIP_SWITCH_H - 3, on ? mixColor(accent, ACCENT, pulse * 0.35f) : 0xFF071016);
        graphics.fill(x + 5, on ? y + 4 : y + SHIP_SWITCH_H - 8, x + 8, (on ? y + 11 : y + SHIP_SWITCH_H - 3), control == null ? MUTED : ACCENT);
        String label = font.plainSubstrByWidth(zone.shortLabel.toUpperCase(java.util.Locale.ROOT), SHIP_SWITCH_W - 18);
        graphics.drawString(font, label, x + 14, y + 6, control == null ? MUTED : TEXT, false);
        if (deviceCount > 0) graphics.fill(x + SHIP_SWITCH_W - 7, y + 3, x + SHIP_SWITCH_W - 3, y + 7, OK_GREEN);
    }

    private void drawFlipPreventionSwitch(GuiGraphics graphics, int x, int y, int width,
                                          @Nullable FccGuiControlEntry control, int mouseX, int mouseY, float pulse) {
        boolean on = control != null && controlFraction(control) >= 0.5;
        boolean hover = inside(mouseX, mouseY, x, y, width, SHIP_SAFETY_SWITCH_H);
        int accent = control == null ? MUTED : on ? mixColor(BAD, ACCENT, pulse * 0.35f) : WARN;
        graphics.fill(x, y, x + width, y + SHIP_SAFETY_SWITCH_H, hover ? CARD_HOVER : on ? 0xF02B1D24 : CARD_BG);
        border(graphics, x, y, width, SHIP_SAFETY_SWITCH_H, hover ? accent : SOFT_BORDER);
        graphics.fill(x + 3, y + 3, x + 15, y + SHIP_SAFETY_SWITCH_H - 3, accent);
        graphics.drawCenteredString(font, "!", x + 9, y + 5, 0xFF071016);
        String label = on ? "FLIP PREVENTION ARMED" : "FLIP PREVENTION";
        graphics.drawString(font, label, x + 22, y + 5, control == null ? MUTED : TEXT, false);
        String state = control == null ? "MISSING" : on ? "ON" : "OFF";
        graphics.drawString(font, state, x + width - font.width(state) - 10, y + 5, on ? BAD : DIM, false);
    }

    private boolean clickShipOverheadSwitch(FccGuiSnapshot snapshot, double mouseX, double mouseY, int x, int y) {
        int railX = x + 14;
        int railY = y + 28;
        DiagramZone[] zones = DiagramZone.values();
        for (int i = 0; i < zones.length; i++) {
            int switchX = shipSwitchX(railX, i);
            int switchY = shipSwitchY(railY, i);
            if (!inside(mouseX, mouseY, switchX, switchY, SHIP_SWITCH_W, SHIP_SWITCH_H)) continue;
            FccGuiControlEntry control = findDiagramSwitchControl(snapshot, zones[i]);
            if (control == null) {
                notice = "Assign a device to build cockpit switches";
                return true;
            }
            selectControlAndFirstRoute(snapshot, control);
            sendToggleShipSwitch(control);
            notice = trimLabel(zones[i].shortLabel, 16) + " switch toggled";
            return true;
        }
        int safetyY = shipSafetySwitchY(railY);
        if (inside(mouseX, mouseY, railX, safetyY, (MAIN_W - 36) - 28, SHIP_SAFETY_SWITCH_H)) {
            FccGuiControlEntry control = findFlipPreventionControl(snapshot);
            if (control == null) {
                notice = "Refresh Cockpit to build Flip Prevention";
                return true;
            }
            selectControlAndFirstRoute(snapshot, control);
            sendToggleShipSwitch(control);
            notice = "Flip Prevention " + (controlFraction(control) >= 0.5 ? "off" : "armed");
            return true;
        }
        return false;
    }

    private void fillCircle(GuiGraphics graphics, int cx, int cy, int radius, int color) {
        int rr = radius * radius;
        for (int dy = -radius; dy <= radius; dy++) {
            int dx = (int) Math.sqrt(Math.max(0, rr - dy * dy));
            graphics.fill(cx - dx, cy + dy, cx + dx + 1, cy + dy + 1, color);
        }
    }

    private void drawNeedleLine(GuiGraphics graphics, int x0, int y0, int x1, int y1, int color) {
        int steps = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
        if (steps <= 0) {
            graphics.fill(x0, y0, x0 + 1, y0 + 1, color);
            return;
        }
        for (int i = 0; i <= steps; i++) {
            int x = x0 + (x1 - x0) * i / steps;
            int y = y0 + (y1 - y0) * i / steps;
            graphics.fill(x, y, x + 2, y + 2, color);
        }
    }

    private void renderInspector(GuiGraphics graphics, int mouseX, int mouseY) {
        FccGuiSnapshot snapshot = snapshotOrEmpty();
        int x = leftPos + INSPECT_X + 10;
        int y = topPos + INSPECT_Y + 30;
        switch (activeView) {
            case TEMPLATE -> {
                graphics.drawString(font, "Starter", x, y, ACCENT, false);
                graphics.drawString(font, "Template builds groups and controls.", x, y + 12, DIM, false);
                renderReadinessPanel(graphics, x, y + 32, snapshot);
                graphics.drawString(font, "Current: " + snapshot.groupCount() + " groups / " + snapshot.controlCount() + " controls", x, y + 132, TEXT, false);
            }
            case DEVICES -> {
                FccGuiDeviceEntry device = selectedDevice(snapshot);
                graphics.drawString(font, "Selected Device", x, y, CYAN, false);
                if (device != null) {
                    graphics.drawString(font, trimLabel(device.label(), 28), x, y + 16, TEXT, false);
                    graphics.drawString(font, device.categoryLabel() + " / " + (device.grouped() ? "grouped" : "ungrouped"), x, y + 30, device.grouped() ? OK_GREEN : WARN, false);
                }
            }
            case GROUPS -> {
                FccGuiGroupEntry group = selectedGroup(snapshot);
                FccGuiDeviceEntry device = selectedDevice(snapshot);
                graphics.drawString(font, "Group Builder", x, y, OK_GREEN, false);
                graphics.drawString(font, group == null ? "No group selected" : trimLabel(group.label(), 28), x, y + 16, TEXT, false);
                graphics.drawString(font, device == null ? "No device selected" : "Device: " + trimLabel(device.label(), 18), x, y + 30, DIM, false);
            }
            case DIAGRAM -> {
                DiagramZone zone = selectedDiagramZone();
                FccGuiDeviceEntry device = selectedDevice(snapshot);
                int count = diagramZoneDeviceCount(snapshot, zone);
                FccGuiGroupEntry bayPayload = payloadGroup(snapshot, true);
                FccGuiGroupEntry dropPayload = payloadGroup(snapshot, false);
                graphics.drawString(font, "Ship Routing", x, y, CYAN, false);
                graphics.drawString(font, trimLabel(zone.label, 28), x, y + 16, TEXT, false);
                graphics.drawString(font, count + " device" + (count == 1 ? "" : "s") + " in zone", x, y + 30, count > 0 ? OK_GREEN : WARN, false);
                graphics.drawString(font, device == null ? "No device selected" : "Device: " + trimLabel(device.label(), 18), x, y + 44, DIM, false);
                graphics.drawString(font, "Payload Links", x, y + 68, WARN, false);
                graphics.drawString(font, "Bay: " + payloadGroupState(bayPayload), x, y + 84, payloadGroupDefined(bayPayload) ? OK_GREEN : DIM, false);
                graphics.drawString(font, "Drop: " + payloadGroupState(dropPayload), x, y + 98, payloadGroupDefined(dropPayload) ? BAD : DIM, false);
                graphics.drawString(font, "Cockpit Sync", x, y + 204, OK_GREEN, false);
                graphics.drawString(font, countDiagramSwitchControls(snapshot) + "/" + DiagramZone.values().length + " ceiling switches", x, y + 218, DIM, false);
            }
            case OTOMOBIL -> {
                OtomobilZone zone = selectedOtomobilZone();
                FccGuiDeviceEntry device = selectedDevice(snapshot);
                int count = otomobilZoneDeviceCount(snapshot, zone);
                graphics.drawString(font, "Otomobil Routing", x, y, OK_GREEN, false);
                graphics.drawString(font, trimLabel(zone.label, 28), x, y + 16, TEXT, false);
                graphics.drawString(font, count + " device" + (count == 1 ? "" : "s") + " in zone", x, y + 30, count > 0 ? OK_GREEN : WARN, false);
                graphics.drawString(font, device == null ? "No device selected" : "Device: " + trimLabel(device.label(), 18), x, y + 44, DIM, false);
                if (!canEditOtomobilDiagram(snapshot)) {
                    graphics.drawString(font, "Locked while Airship Layout has assigned zones", x, y + 218, WARN, false);
                }
            }
            case AUTO_MODE -> {
                boolean autoEnabled = isAutoDriveEnabled(snapshot);
                int inspW = INSPECT_W - 20;
                graphics.drawString(font, "Auto Mode", x, y, ACCENT, false);
                graphics.drawString(font, autoEnabled ? autoModeLabel(snapshot) : "MANUAL", x, y + 14, autoEnabled ? OK_GREEN : DIM, false);
                graphics.drawString(font, font.plainSubstrByWidth(selfDriveTargetLabel(snapshot), inspW), x, y + 28,
                    hasAcceptedPathfinderRoute(snapshot) ? ACCENT : DIM, false);
            }
            case CONTROLS -> {
                FccGuiControlEntry control = selectedControl(snapshot);
                FccGuiGroupEntry group = selectedGroup(snapshot);
                FccGuiRouteEntry route = selectedRouteOrNull(snapshot);
                graphics.drawString(font, "Cockpit Controls", x, y, WARN, false);
                graphics.drawString(font, control == null ? "No control" : trimLabel(control.label(), 28), x, y + 16, TEXT, false);
                graphics.drawString(font, group == null ? "No target group" : "Target: " + trimLabel(group.label(), 18), x, y + 30, DIM, false);
                if (route != null) graphics.drawString(font, "Scale " + String.format(java.util.Locale.ROOT, "%.2f", route.scale()), x, y + 286, ACCENT, false);
            }
            case E_MANUEL -> {
                FccTelemetryPacket telemetry = FccClientState.telemetryFor(menu.containerId);
                int count = telemetryBindingCount(telemetry);
                graphics.drawString(font, "Overrides", x, y, OK_GREEN, false);
                graphics.drawString(font, count <= 0 ? "Waiting for live row telemetry" : count + " telemetry rows", x, y + 16, count <= 0 ? DIM : TEXT, false);
                if (count > 0) {
                    int idx = clampIndex(selectedEmanuelIndex, count);
                    boolean gyro = isGyroBinding(telemetry, idx);
                    int row = intAt(telemetry.bindingRows(), idx, idx) + 1;
                    graphics.drawString(font, "Selected: row " + row, x, y + 30, DIM, false);
                    graphics.drawString(font, emanuelRowTypeLabel(snapshot, row, gyro), x, y + 44, gyro ? ACCENT : CYAN, false);
                    graphics.drawString(font, "Tilt " + oneDecimal(floatAt(telemetry.tiltAngleDeg(), idx, 0f)) + " deg", x, y + 58, TEXT, false);
                }
            }
            case LINKED_CONTROLLER -> {
                graphics.drawString(font, "Linked Controller", x, y, ACCENT, false);
                graphics.drawString(font, "Remote signal receiver", x, y + 12, DIM, false);
            }
            case TEST -> {
                graphics.drawString(font, snapshot.mode() == FlightMode.OFF ? "Output locked" : "Output armed", x, y, snapshot.mode() == FlightMode.OFF ? DIM : OK_GREEN, false);
                graphics.drawString(font, testStateText(snapshot), x, y + 102, snapshot.calStateOrdinal() == 3 ? WARN : DIM, false);
                String warning = effectiveWarning(snapshot);
                if (!warning.isEmpty()) graphics.drawString(font, font.plainSubstrByWidth(warning, INSPECT_W - 20), x, y + 124, WARN, false);
            }
            case SHIP -> {
                ShipControls shipControls = resolveShipControls(snapshot);
                FccGuiControlEntry thrust = shipControls.thrust();
                FccGuiControlEntry yaw = shipControls.yaw();
                FccGuiControlEntry flip = findFlipPreventionControl(snapshot);
                boolean vehicleEnabled = canUseVehicleControls(snapshot);
                boolean armed = snapshot.mode() != FlightMode.OFF;
                int innerW = INSPECT_W - 20;
                OperateProfileState profileState = resolveOperateProfile(snapshot);
                if (profileState.hybrid() && !profileState.manual()) {
                    graphics.drawString(font, "Hybrid Helm", x, y, WARN, false);
                    graphics.drawString(font, "Pick a profile", x, y + 16, ACCENT, false);
                    graphics.drawString(font, "Air " + profileState.airshipDevices() + " / Ground " + profileState.groundDevices(), x, y + 30, TEXT, false);
                    graphics.drawString(font, "Auto is paused", x, y + 44, DIM, false);
                    return;
                }
                if (profileState.profile() == OperateProfile.AIRSHIP) {
                    boolean hasGimbal = snapshot.availableSensors().contains(SensorRequirement.GIMBAL);
                    boolean hasAltitude = snapshot.availableSensors().contains(SensorRequirement.ALTITUDE);
                    boolean hasVelocity = snapshot.availableSensors().contains(SensorRequirement.VELOCITY);
                    boolean ready = thrust != null && yaw != null && shipControls.trim() != null && hasGimbal && hasAltitude;
                    graphics.drawString(font, ready ? "Airship Ready" : "Airship Setup", x, y, ready ? CYAN : WARN, false);
                    graphics.drawString(font, profileState.manual() ? "Manual helm" : "Auto helm", x + innerW - font.width(profileState.manual() ? "Manual helm" : "Auto helm"), y, MUTED, false);
                    graphics.drawString(font, airshipSensorLabel(hasGimbal, hasAltitude, hasVelocity), x, y + 18, ready ? OK_GREEN : WARN, false);
                    graphics.drawString(font, airshipControlLabel(thrust, yaw, shipControls.trim()), x, y + 34, ready ? OK_GREEN : DIM, false);
                    graphics.drawString(font, "Devices " + profileState.airshipDevices(), x, y + 50, CYAN, false);
                    graphics.drawString(font, "Mode " + (armed ? "ARMED" : "Ready"), x, y + 66, armed ? OK_GREEN : DIM, false);
                    renderBombActionPanel(graphics, snapshot, x, y + 92, innerW, mouseX, mouseY);
                    return;
                }

                // ── Status header ──────────────────────────────────────
                String statusStr = vehicleEnabled ? (armed ? "ARMED" : "Ready") : "Disabled";
                int statusColor = vehicleEnabled ? (armed ? OK_GREEN : CYAN) : WARN;
                graphics.drawString(font, statusStr, x, y, statusColor, false);
                graphics.drawString(font, "OTOMOBIL", x + innerW - font.width("OTOMOBIL"), y, MUTED, false);

                // ── Steering mode badge ────────────────────────────────
                FccGuiControlEntry steerFrontC = findControl(snapshot, "Steer Mode Front Switch");
                FccGuiControlEntry steerAllC   = findControl(snapshot, "Steer Mode All-Wheel Switch");
                FccGuiControlEntry steerCrabC  = findControl(snapshot, "Steer Mode Crab Switch");
                boolean isCrab = controlFraction(steerCrabC) >= 0.5;
                boolean is4WS  = controlFraction(steerAllC)  >= 0.5;
                String steerLabel = isCrab ? "CRAB" : is4WS ? "4WS" : "FRONT";
                int steerColor = isCrab ? CYAN : is4WS ? WARN : OK_GREEN;
                graphics.fill(x, y + 14, x + innerW, y + 26, withAlpha(steerColor, 30));
                border(graphics, x, y + 14, innerW, 12, withAlpha(steerColor, 160));
                graphics.drawCenteredString(font, "STEER \u25b8 " + steerLabel, x + innerW / 2, y + 17, steerColor);

                // ── Drive mode badge ───────────────────────────────────
                FccGuiControlEntry drive4x4C      = findControl(snapshot, "Drive Mode 4x4 Switch");
                FccGuiControlEntry drive2x4FrontC = findControl(snapshot, "Drive Mode 2x4 Front Switch");
                FccGuiControlEntry drive2x4RearC  = findControl(snapshot, "Drive Mode 2x4 Rear Switch");
                FccGuiControlEntry midAxleC       = findControl(snapshot, "Mid Axle Drive Switch");
                boolean is4x4   = controlFraction(drive4x4C)      >= 0.5;
                boolean isFWD   = controlFraction(drive2x4FrontC) >= 0.5;
                boolean isMidOn = controlFraction(midAxleC)       >= 0.5;
                String driveLabel = is4x4 ? "4WD" : isFWD ? "FWD" : "RWD";
                String midLabel   = isMidOn ? " +MID" : "";
                int driveColor = is4x4 ? OK_GREEN : ACCENT;
                graphics.fill(x, y + 29, x + innerW, y + 41, withAlpha(driveColor, 25));
                border(graphics, x, y + 29, innerW, 12, withAlpha(driveColor, 130));
                graphics.drawCenteredString(font, "DRIVE \u25b8 " + driveLabel + midLabel, x + innerW / 2, y + 32, driveColor);

                // ── Throttle bar ───────────────────────────────────────
                graphics.drawString(font, "THROTTLE", x, y + 45, MUTED, false);
                double thrustFrac = controlSignedFraction(thrust);
                int barW = innerW - 2;
                int centerBarX = x + 1 + barW / 2;
                graphics.fill(x, y + 55, x + innerW, y + 66, 0xFF0A1520);
                border(graphics, x, y + 55, innerW, 11, SOFT_BORDER);
                if (thrustFrac > 0.001) {
                    int fillW = (int)(thrustFrac * (barW / 2));
                    graphics.fill(centerBarX, y + 56, Math.min(centerBarX + fillW, x + innerW - 1), y + 65, OK_GREEN);
                } else if (thrustFrac < -0.001) {
                    int fillW = (int)(-thrustFrac * (barW / 2));
                    graphics.fill(Math.max(x + 1, centerBarX - fillW), y + 56, centerBarX, y + 65, WARN);
                }
                graphics.fill(centerBarX, y + 56, centerBarX + 1, y + 65, MUTED);
                String thrustPct = thrust == null ? "--" : String.format(java.util.Locale.ROOT, "%+.0f%%", thrustFrac * 100);
                graphics.drawCenteredString(font, thrustPct, x + innerW / 2, y + 57, thrust == null ? MUTED : TEXT);

                // ── Steering bar ───────────────────────────────────────
                graphics.drawString(font, "STEERING", x, y + 70, MUTED, false);
                double yawFrac = controlSignedFraction(yaw);
                graphics.fill(x, y + 80, x + innerW, y + 90, 0xFF0A1520);
                border(graphics, x, y + 80, innerW, 10, SOFT_BORDER);
                if (yawFrac > 0.001) {
                    int fillW = (int)(yawFrac * (barW / 2));
                    graphics.fill(centerBarX, y + 81, Math.min(centerBarX + fillW, x + innerW - 1), y + 89, CYAN);
                } else if (yawFrac < -0.001) {
                    int fillW = (int)(-yawFrac * (barW / 2));
                    graphics.fill(Math.max(x + 1, centerBarX - fillW), y + 81, centerBarX, y + 89, ACCENT);
                }
                graphics.fill(centerBarX, y + 81, centerBarX + 1, y + 89, MUTED);
                // cursor pip
                int cursorX = centerBarX + (int)(yawFrac * (barW / 2));
                int cpx = Math.max(x + 1, Math.min(x + innerW - 3, cursorX));
                graphics.fill(cpx, y + 81, cpx + 2, y + 89, TEXT);
                String yawPct = yaw == null ? "--" : String.format(java.util.Locale.ROOT, "%+.0f%%", yawFrac * 100);
                graphics.drawCenteredString(font, yawPct, x + innerW / 2, y + 81, yaw == null ? MUTED : TEXT);

                // ── Separator ──────────────────────────────────────────
                graphics.fill(x, y + 95, x + innerW, y + 96, SOFT_BORDER);

                // ── Live telemetry values ──────────────────────────────
                String thrustVal = thrust == null ? "No throttle control" : "Thrust  " + String.format(java.util.Locale.ROOT, "%+.2f", thrustFrac);
                String yawVal    = yaw   == null ? "No steering control" : "Steer   " + String.format(java.util.Locale.ROOT, "%+.2f", yawFrac);
                graphics.drawString(font, thrustVal, x, y + 100, thrust == null ? MUTED : ACCENT, false);
                graphics.drawString(font, yawVal,    x, y + 112, yaw   == null ? MUTED : CYAN,   false);

                FccGuiControlEntry diffRearC = findControl(snapshot, "Diff Lock Rear Switch");
                FccGuiControlEntry diffFullC = findControl(snapshot, "Diff Lock Full Switch");
                boolean diffFull = controlFraction(diffFullC) >= 0.5;
                boolean diffRear = controlFraction(diffRearC) >= 0.5;
                String diffStr = diffFull ? "Diff  FULL LOCK" : diffRear ? "Diff  REAR LOCK" : "Diff  open";
                int diffColor  = diffFull ? BAD : diffRear ? ACCENT : DIM;
                graphics.drawString(font, diffStr, x, y + 124, diffColor, false);

                String flipStr = flip == null ? "Flip guard  off" : controlFraction(flip) >= 0.5 ? "Flip guard  ON" : "Flip guard  off";
                int flipColor  = flip != null && controlFraction(flip) >= 0.5 ? WARN : DIM;
                graphics.drawString(font, flipStr, x, y + 136, flipColor, false);

                // ── Bottom separator before action buttons ─────────────
                graphics.fill(x, y + 150, x + innerW, y + 151, SOFT_BORDER);
                FccTelemetryPacket telemetry = FccClientState.telemetryFor(menu.containerId);
                boolean autoEnabled = isAutoDriveEnabled(snapshot);
                graphics.drawString(font, autoEnabled ? "Self drive  ON" : "Self drive  off", x, y + 156, autoEnabled ? OK_GREEN : DIM, false);
                graphics.drawString(font, "Target  " + trimLabel(selfDriveTargetLabel(snapshot), 16), x, y + 168,
                    hasAcceptedPathfinderRoute(snapshot) ? ACCENT : DIM, false);
                String progress = selfDriveProgressLabel(snapshot);
                graphics.drawString(font, "Distance  " + autoTargetDistanceText(telemetry) + (progress.isEmpty() ? "" : "  " + progress), x, y + 180, TEXT, false);
            }
        }
    }

    private void renderStatusPanel(GuiGraphics graphics) {
        FccGuiSnapshot snapshot = snapshotOrEmpty();
        int x = leftPos + STATUS_X + 16;
        int y = topPos + STATUS_Y + 30;
        if (activeView == WorkView.DIAGRAM) {
            DiagramZone zone = selectedDiagramZone();
            FccGuiGroupEntry group = diagramZoneGroup(snapshot, zone);
            FccGuiDeviceEntry device = selectedDevice(snapshot);
            graphics.drawString(font, "Zone: " + trimLabel(zone.label, 32), x, y, TEXT, false);
            graphics.drawString(font, group == null ? "Group will be created on assign" : group.deviceCount() + " devices assigned", x, y + 16, group == null ? WARN : OK_GREEN, false);
            graphics.drawString(font, device == null ? "Select a detected device on the right." : "Selected device: " + trimLabel(device.label(), 30), x, y + 30, DIM, false);
            String footer = notice.isEmpty() ? testStateText(snapshot) : notice;
            graphics.drawString(font, font.plainSubstrByWidth(footer, STATUS_W - 32), x, y + 62, notice.isEmpty() ? DIM : OK_GREEN, false);
            renderTinyProgress(graphics, x + STATUS_W - 178, y + 62, 142, snapshot.resolvedBindingCount(), Math.max(1, snapshot.boundRowCount()), CYAN);
            return;
        }
        if (activeView == WorkView.OTOMOBIL) {
            OtomobilZone zone = selectedOtomobilZone();
            FccGuiGroupEntry group = otomobilZoneGroup(snapshot, zone);
            FccGuiDeviceEntry device = selectedDevice(snapshot);
            FccTelemetryPacket telemetry = FccClientState.telemetryFor(menu.containerId);
            graphics.drawString(font, "Zone: " + trimLabel(zone.label, 32), x, y, TEXT, false);
            graphics.drawString(font, group == null ? "Group will be created on assign" : group.deviceCount() + " wheels assigned", x, y + 16, group == null ? WARN : OK_GREEN, false);
            graphics.drawString(font, device == null ? "Select a detected wheel device on the right." : "Selected device: " + trimLabel(device.label(), 30), x, y + 30, DIM, false);
            String progress = selfDriveProgressLabel(snapshot);
            String target = "Self-drive target: " + selfDriveTargetLabel(snapshot) + " | Distance " + autoTargetDistanceText(telemetry)
                + (progress.isEmpty() ? "" : " | " + progress);
            if (!canEditOtomobilDiagram(snapshot)) {
                graphics.drawString(font, "Locked: Airship Layout already has assigned zones", x, y + 62, WARN, false);
            } else {
                graphics.drawString(font, font.plainSubstrByWidth(pathfinderStatusLabel(snapshot), STATUS_W - 32), x, y + 44,
                    snapshot.pathfinderRouteAccepted() ? OK_GREEN : DIM, false);
                graphics.drawString(font, font.plainSubstrByWidth(notice.isEmpty() ? target : notice, STATUS_W - 32), x, y + 62, notice.isEmpty() ? CYAN : OK_GREEN, false);
            }
            renderTinyProgress(graphics, x + STATUS_W - 178, y + 62, 142, snapshot.resolvedBindingCount(), Math.max(1, snapshot.boundRowCount()), OK_GREEN);
            return;
        }
        if (activeView == WorkView.SHIP) {
            OperateProfileState profileState = resolveOperateProfile(snapshot);
            FccTelemetryPacket telemetry = FccClientState.telemetryFor(menu.containerId);
            if (profileState.hybrid() && !profileState.manual()) {
                graphics.drawString(font, "Hybrid craft detected", x, y, WARN, false);
                graphics.drawString(font, "Airship devices " + profileState.airshipDevices() + " | Ground devices " + profileState.groundDevices(), x, y + 16, TEXT, false);
                graphics.drawString(font, "Use the Operate profile selector to choose Zeplin/Balloon or Ground Vehicle.", x, y + 30, ACCENT, false);
                String footer = notice.isEmpty() ? profileState.reason() : notice;
                graphics.drawString(font, font.plainSubstrByWidth(footer, STATUS_W - 32), x, y + 62, notice.isEmpty() ? DIM : OK_GREEN, false);
                renderTinyProgress(graphics, x + STATUS_W - 178, y + 62, 142, profileState.airshipDevices() + profileState.groundDevices(), Math.max(1, snapshot.deviceCount()), WARN);
                return;
            }
            if (profileState.profile() == OperateProfile.AIRSHIP) {
                ShipControls shipControls = resolveShipControls(snapshot);
                boolean hasGimbal = snapshot.availableSensors().contains(SensorRequirement.GIMBAL);
                boolean hasAltitude = snapshot.availableSensors().contains(SensorRequirement.ALTITUDE);
                boolean hasVelocity = snapshot.availableSensors().contains(SensorRequirement.VELOCITY);
                boolean controlsReady = shipControls.thrust() != null && shipControls.yaw() != null && shipControls.trim() != null;
                graphics.drawString(font, "Zeplin/Balloon helm", x, y, controlsReady && hasGimbal && hasAltitude ? CYAN : WARN, false);
                graphics.drawString(font, profileState.manual() ? "Manual profile override" : profileState.reason(), x, y + 16, profileState.manual() ? ACCENT : DIM, false);
                graphics.drawString(font, "Sensors " + airshipSensorLabel(hasGimbal, hasAltitude, hasVelocity) + " | Controls " + airshipControlLabel(shipControls.thrust(), shipControls.yaw(), shipControls.trim()), x, y + 30, TEXT, false);
                graphics.drawString(font, "Thrust V " + airshipTelemetrySum(telemetry, true) + " | H " + airshipTelemetrySum(telemetry, false) + " | Airship devices " + profileState.airshipDevices(), x, y + 44, ACCENT, false);
                String footer = notice.isEmpty() ? testStateText(snapshot) : notice;
                graphics.drawString(font, font.plainSubstrByWidth(footer, STATUS_W - 32), x, y + 62, notice.isEmpty() ? DIM : OK_GREEN, false);
                renderTinyProgress(graphics, x + STATUS_W - 178, y + 62, 142, profileState.airshipDevices(), Math.max(1, snapshot.deviceCount()), CYAN);
                return;
            }
            boolean autoEnabled = isAutoDriveEnabled(snapshot);
            int wheelCount = 0;
            for (OtomobilZone zone : OtomobilZone.values()) {
                wheelCount += otomobilZoneDeviceCount(snapshot, zone);
            }
            graphics.drawString(font, autoEnabled ? "Self drive armed" : "Manual vehicle control", x, y, autoEnabled ? OK_GREEN : TEXT, false);
            graphics.drawString(font, "Target: " + trimLabel(selfDriveTargetLabel(snapshot), 42), x, y + 16, hasAcceptedPathfinderRoute(snapshot) ? ACCENT : DIM, false);
            String progress = selfDriveProgressLabel(snapshot);
            graphics.drawString(font, "Distance " + autoTargetDistanceText(telemetry) + (progress.isEmpty() ? "" : " | " + progress) + " | Wheels assigned " + wheelCount, x, y + 30, CYAN, false);
            graphics.drawString(font, font.plainSubstrByWidth(pathfinderStatusLabel(snapshot), STATUS_W - 32), x, y + 44,
                snapshot.pathfinderRouteAccepted() ? OK_GREEN : DIM, false);
            String footer = notice.isEmpty() ? testStateText(snapshot) : notice;
            graphics.drawString(font, font.plainSubstrByWidth(footer, STATUS_W - 32), x, y + 62, notice.isEmpty() ? DIM : OK_GREEN, false);
            renderTinyProgress(graphics, x + STATUS_W - 178, y + 62, 142, snapshot.resolvedBindingCount(), Math.max(1, snapshot.boundRowCount()), CYAN);
            return;
        }
        if (activeView == WorkView.E_MANUEL) {
            FccTelemetryPacket telemetry = FccClientState.telemetryFor(menu.containerId);
            int count = telemetryBindingCount(telemetry);
            graphics.drawString(font, "Override status", x, y, TEXT, false);
            if (count <= 0) {
                graphics.drawString(font, "No telemetry stream yet. Open while the craft is loaded.", x, y + 16, DIM, false);
                return;
            }
            int idx = clampIndex(selectedEmanuelIndex, count);
            graphics.drawString(font, "Selected row: R" + (intAt(telemetry.bindingRows(), idx, idx) + 1), x, y + 16, OK_GREEN, false);
            graphics.drawString(font, "RPM " + intAt(telemetry.actualRpm(), idx, 0) + " (cmd " + intAt(telemetry.commandedRpm(), idx, 0) + ")", x, y + 30, CYAN, false);
            graphics.drawString(font, "Vertical " + signed1(floatAt(telemetry.thrustVertical(), idx, 0f))
                    + " | Horizontal " + signed1(floatAt(telemetry.thrustHorizontal(), idx, 0f)), x, y + 44, ACCENT, false);
            graphics.drawString(font, "Tilt " + oneDecimal(floatAt(telemetry.tiltAngleDeg(), idx, 0f))
                    + " deg | " + emanuelRowTypeLabel(snapshot, intAt(telemetry.bindingRows(), idx, idx) + 1, isGyroBinding(telemetry, idx)), x, y + 58, DIM, false);
            renderTinyProgress(graphics, x + STATUS_W - 178, y + 62, 142, count, Math.max(1, snapshot.boundRowCount()), OK_GREEN);
            return;
        }
        FccGuiControlEntry control = selectedControl(snapshot);
        FccGuiRouteEntry route = selectedRouteOrNull(snapshot);
        String title = control == null ? "No control selected" : trimLabel(control.label(), 28);
        graphics.drawString(font, title, x, y, TEXT, false);
        if (control != null && route != null) {
            graphics.drawString(font, font.plainSubstrByWidth(routeSentence(control, route), STATUS_W - 32), x, y + 16, ACCENT, false);
            graphics.drawString(font, "Output " + shortOutput(route.outputType()) + " | Scale " + String.format(java.util.Locale.ROOT, "%.2f", route.scale()) + routeDirectionSuffix(snapshot, route), x, y + 30, DIM, false);
        } else {
            graphics.drawString(font, "Choose a control and route in Controls or Test.", x, y + 16, DIM, false);
        }
        String warning = effectiveWarning(snapshot);
        String footer = notice.isEmpty() ? testStateText(snapshot) : notice;
        if (!warning.isEmpty()) graphics.drawString(font, font.plainSubstrByWidth(warning, STATUS_W - 32), x, y + 62, WARN, false);
        else graphics.drawString(font, font.plainSubstrByWidth(footer, STATUS_W - 32), x, y + 62, notice.isEmpty() ? DIM : OK_GREEN, false);
        renderTinyProgress(graphics, x + STATUS_W - 178, y + 62, 142, snapshot.resolvedBindingCount(), Math.max(1, snapshot.boundRowCount()), CYAN);
    }

        private void renderHelpOverlay(GuiGraphics graphics) {
        FccGuiSnapshot snapshot = snapshotOrEmpty();
        int x = leftPos + 254;
        int y = topPos + 86;
        int width = 412;
        int height = 306;
        graphics.fill(leftPos + 4, topPos + 4, leftPos + W - 4, topPos + H - 4, 0x99000000);
        graphics.fill(x, y, x + width, y + height, 0xF0141D27);
        border(graphics, x, y, width, height, ACCENT);
        graphics.fill(x + 1, y + 1, x + width - 1, y + 24, 0xFF1B2A36);
        graphics.drawString(font, activeView.label + " Guide", x + 14, y + 9, ACCENT, false);
        graphics.fill(x + width - 28, y + 8, x + width - 10, y + 26, CARD_BG);
        border(graphics, x + width - 28, y + 8, 18, 18, SOFT_BORDER);
        graphics.drawCenteredString(font, "x", x + width - 19, y + 13, TEXT);

        int lineY = y + 42;
        graphics.drawString(font, "Fast path", x + 16, lineY, CYAN, false);
        lineY += 16;
        for (String line : helpLinesForActiveView()) {
            lineY = drawHelpLine(graphics, x + 16, lineY, width - 32, line);
        }

        lineY += 8;
        graphics.drawString(font, "Wiring check", x + 16, lineY, OK_GREEN, false);
        lineY += 16;
        ShipControls shipControls = resolveShipControls(snapshot);
        lineY = drawHelpStatus(graphics, x + 16, lineY, width - 32, shipControls.thrust() != null,
            "Thrust Lever exists");
        FccGuiControlEntry yaw = shipControls.yaw();
        lineY = drawHelpStatus(graphics, x + 16, lineY, width - 32, yaw != null,
            "Yaw Wheel exists");
        lineY = drawHelpStatus(graphics, x + 16, lineY, width - 32, hasRoute(snapshot, yaw, "left", "RPM_DELTA", 1),
            "Yaw adds left thrust");
        lineY = drawHelpStatus(graphics, x + 16, lineY, width - 32, hasRoute(snapshot, yaw, "right", "RPM_DELTA", -1),
            "Yaw reduces right thrust and can reverse at the edge");
        int switchCount = countDiagramSwitchControls(snapshot);
        lineY = drawHelpStatus(graphics, x + 16, lineY, width - 32, switchCount > 0,
            switchCount + " ceiling switches exist");
        lineY = drawHelpStatus(graphics, x + 16, lineY, width - 32, switchCount >= DiagramZone.values().length,
            "All diagram zones have cockpit switches");
        drawHelpStatus(graphics, x + 16, lineY, width - 32, findFlipPreventionControl(snapshot) != null,
            "Flip Prevention safety switch exists");
        }

        private List<String> helpLinesForActiveView() {
        return switch (activeView) {
            case TEMPLATE -> List.of(
                "1. Put linked propeller/wheel-mount and governor cards in the rack.",
                "2. Press Apply Zeppelin to create aircraft controls.",
                "3. Go to Ship, arm output, then test thrust and yaw gently.");
            case DEVICES -> List.of(
                "1. Select a detected device row.",
                "2. Rename it to aircraft language, such as left lift prop.",
                "3. Use Category only when the detected type is wrong.");
            case GROUPS -> List.of(
                "1. Select a group on the left and a device on the right.",
                "2. Add Device puts that hardware into the group.",
                "3. Left Thrust and Right Thrust are what yaw uses.");
            case DIAGRAM -> List.of(
                "1. Select a detected device, then click a craft zone.",
                "2. Assign Device creates the matching zone group.",
                "3. Ship controls and ceiling switches are auto-managed.");
            case OTOMOBIL -> List.of(
                "1. Select a wheel-linked device, then click a truck wheel zone.",
                "2. Assign Wheel creates matching vehicle groups.",
                "3. This page locks if the Airship Layout already has assigned zones.");
            case AUTO_MODE -> List.of(
                "1. Select Auto Mode for the active route source.",
                "2. Watch the route map, state, distance, and hazard badges.",
                "3. Pathfinder systems can be opened or closed from this page.");
            case CONTROLS -> List.of(
                "1. Select a cockpit control and a target zone.",
                "2. Route To Group draws the connection in the schematic.",
                "3. Output and Scale show beside each connected zone.");
            case E_MANUEL -> List.of(
                "1. Select a propeller or wheel-mount row from the left list.",
                "2. Watch live RPM, vertical thrust, and horizontal thrust values.",
                "3. Tilt angle indicates smart/gyro bearing vector offset.");
            case LINKED_CONTROLLER -> List.of(
                "1. Match these six frequency pairs to a Create Linked Controller.",
                "2. W/S ramp thrust, A/D ramp wheel, Space/Shift ramp levitation.",
                "3. The interval slider sets how fast controls accumulate.");
            case TEST -> List.of(
                "1. Select a control from the list.",
                "2. Use the Command Rail to move it without guessing values.",
                "3. Center returns spring controls to neutral.");
            case SHIP -> List.of(
                "1. Operate auto-selects Zeplin/Balloon or Ground from populated layouts.",
                "2. Use Auto, Zeplin, and Ground chips when a hybrid craft needs a manual helm.",
                "3. Ground keeps wheel modes; Zeplin shows lift, thrust, yaw, and sensor readiness.");
        };
        }

        private int drawHelpLine(GuiGraphics graphics, int x, int y, int width, String text) {
        String remaining = text;
        while (!remaining.isEmpty()) {
            String line = font.plainSubstrByWidth(remaining, width);
            graphics.drawString(font, line, x, y, TEXT, false);
            remaining = remaining.substring(line.length()).stripLeading();
            y += 12;
        }
        return y + 2;
        }

        private int drawHelpStatus(GuiGraphics graphics, int x, int y, int width, boolean ok, String label) {
        int color = ok ? OK_GREEN : WARN;
        graphics.fill(x, y + 2, x + 7, y + 9, color);
        graphics.drawString(font, font.plainSubstrByWidth(label, width - 16), x + 14, y, ok ? TEXT : DIM, false);
        return y + 13;
        }

        private boolean hasRoute(FccGuiSnapshot snapshot, @Nullable FccGuiControlEntry control, String targetNeedle,
                     String outputType, int sign) {
        if (control == null) return false;
        String needle = targetNeedle.toLowerCase(java.util.Locale.ROOT);
        for (FccGuiRouteEntry route : snapshot.routeEntries()) {
            if (!control.id().equals(route.controlId())) continue;
            if (!route.outputType().equals(outputType)) continue;
            if (!routeTargetMatches(route, needle)) continue;
            double effectiveScale = effectiveRouteScale(route);
            if (sign > 0 && effectiveScale <= 0.0) continue;
            if (sign < 0 && effectiveScale >= 0.0) continue;
            return true;
        }
        return false;
        }

    private void renderCardRack(GuiGraphics graphics) {
        FccGuiSnapshot snapshot = snapshotOrEmpty();
        int panelLeft = leftPos + DOCK_X;
        int visibleRows = menu.getVisibleRowCount();
        int viewportRows = menu.getRackViewportRows();
        int scrollStart = menu.getCardScrollStart();
        graphics.drawString(font, visibleRows + "/" + FlightControlComputerMenu.ROW_COUNT, panelLeft + DOCK_W - 48, topPos + DOCK_Y + 6, MUTED, false);
        for (int visual = 0; visual < viewportRows; visual++) {
            int row = scrollStart + visual;
            int rowY = topPos + FlightControlComputerMenu.ROW_START_Y + visual * FlightControlComputerMenu.ROW_HEIGHT;
            int rowBit = 1 << row;
            boolean linkMode = (snapshot.redstoneRowMask() & rowBit) != 0;
            boolean duplicate = (snapshot.duplicateRowMask() & rowBit) != 0;
            boolean bound = linkMode ? (snapshot.redstoneActiveRowMask() & rowBit) != 0 : (snapshot.activeRowMask() & rowBit) != 0;
            boolean resolved = linkMode ? bound : (snapshot.resolvedRowMask() & rowBit) != 0;
            boolean powered = linkMode && (snapshot.redstonePoweredRowMask() & rowBit) != 0;
            int color = linkMode ? powered ? ACCENT : resolved ? OK_GREEN : bound ? WARN : BAD : duplicate ? BAD : resolved ? OK_GREEN : bound ? WARN : MUTED;
            graphics.fill(panelLeft + 10, rowY - 5, panelLeft + DOCK_W - 10, rowY + 24, bound ? CARD_ACTIVE : CARD_BG);
            border(graphics, panelLeft + 10, rowY - 5, DOCK_W - 20, 29, resolved ? OK_GREEN : SOFT_BORDER);
            graphics.fill(panelLeft + 10, rowY - 5, panelLeft + 15, rowY + 24, color);
            renderSlotBackdrop(graphics, leftPos + FlightControlComputerMenu.PROP_SLOT_X - 1, rowY - 1);
            renderSlotBackdrop(graphics, leftPos + FlightControlComputerMenu.GOV_SLOT_X - 1, rowY - 1);
            int modeX = panelLeft + 88;
            int modeY = rowY + 2;
            graphics.fill(modeX, modeY, modeX + 42, modeY + 15, linkMode ? 0xF02B4054 : 0xF0182430);
            border(graphics, modeX, modeY, 42, 15, linkMode ? color : SOFT_BORDER);
            graphics.drawCenteredString(font, linkMode ? "LINK" : "CARD", modeX + 21, modeY + 4, linkMode ? TEXT : DIM);
            graphics.drawString(font, "R" + (row + 1), panelLeft + DOCK_W - 34, rowY + 5, color, false);
        }
        if (visibleRows > viewportRows) {
            int trackX = panelLeft + DOCK_W - 8;
            int trackY = topPos + FlightControlComputerMenu.ROW_START_Y - 5;
            int trackH = viewportRows * FlightControlComputerMenu.ROW_HEIGHT - 5;
            graphics.fill(trackX, trackY, trackX + 3, trackY + trackH, 0x66071016);
            int barH = Math.max(18, trackH * viewportRows / visibleRows);
            int maxScroll = Math.max(1, visibleRows - viewportRows);
            int barY = trackY + (int) Math.round(scrollStart / (double) maxScroll * (trackH - barH));
            graphics.fill(trackX, barY, trackX + 3, barY + barH, CYAN);
        }
    }

    private void renderInventoryBackdrop(GuiGraphics graphics) {
        int ix = leftPos + FlightControlComputerMenu.PLAYER_INV_X;
        int iy = topPos + FlightControlComputerMenu.PLAYER_INV_Y;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                renderSlotBackdrop(graphics, ix + col * 18 - 1, iy + row * 18 - 1);
            }
        }
        int hotbarY = topPos + FlightControlComputerMenu.HOTBAR_Y;
        graphics.fill(ix - 2, hotbarY - 5, ix + 9 * 18 + 2, hotbarY - 4, SOFT_BORDER);
        for (int col = 0; col < 9; col++) {
            renderSlotBackdrop(graphics, ix + col * 18 - 1, hotbarY - 1);
        }
    }

    private void renderSlotBackdrop(GuiGraphics graphics, int x, int y) {
        graphics.fill(x, y, x + 18, y + 18, SLOT_BG);
        border(graphics, x, y, 18, 18, SOFT_BORDER);
    }

    private void renderDeviceRows(GuiGraphics graphics, List<FccGuiDeviceEntry> devices, int x, int y, int width, int maxRows, int mouseX, int mouseY) {
        if (devices.isEmpty()) {
            renderEmptyState(graphics, x, y, width, "No linked devices yet");
            return;
        }
        int start = visibleStartIndex(selectedDeviceIndex, devices.size(), maxRows);
        int rows = Math.min(maxRows, devices.size() - start);
        for (int visible = 0; visible < rows; visible++) {
            int index = start + visible;
            FccGuiDeviceEntry device = devices.get(index);
            int rowY = y + visible * (ROW_H + ROW_GAP);
            boolean selected = index == selectedDeviceIndex;
            boolean hover = mouseX >= x && mouseX < x + width && mouseY >= rowY && mouseY < rowY + ROW_H;
            renderListRowBase(graphics, x, rowY, width, ROW_H, selected, hover, device.resolved() ? OK_GREEN : WARN);
            graphics.drawString(font, trimLabel(device.label(), 24), x + 12, rowY + 6, selected ? TEXT : DIM, false);
            graphics.drawString(font, device.categoryLabel(), x + width - 96, rowY + 6, CYAN, false);
            graphics.drawString(font, device.grouped() ? "grouped" : "ungrouped", x + width - 48, rowY + 6, device.grouped() ? OK_GREEN : WARN, false);
        }
        renderOverflowHint(graphics, devices.size(), start, rows, x, y + rows * (ROW_H + ROW_GAP), width);
    }

    private void renderGroupRows(GuiGraphics graphics, List<FccGuiGroupEntry> groups, int x, int y, int width, int maxRows, int mouseX, int mouseY) {
        if (groups.isEmpty()) {
            renderEmptyState(graphics, x, y, width, "No groups yet");
            return;
        }
        int start = visibleStartIndex(selectedGroupIndex, groups.size(), maxRows);
        int rows = Math.min(maxRows, groups.size() - start);
        for (int visible = 0; visible < rows; visible++) {
            int index = start + visible;
            FccGuiGroupEntry group = groups.get(index);
            int rowY = y + visible * (ROW_H + ROW_GAP);
            boolean selected = index == selectedGroupIndex;
            boolean hover = mouseX >= x && mouseX < x + width && mouseY >= rowY && mouseY < rowY + ROW_H;
            renderListRowBase(graphics, x, rowY, width, ROW_H, selected, hover, OK_GREEN);
            graphics.drawString(font, font.plainSubstrByWidth(group.label(), width - 64), x + 12, rowY + 6, selected ? TEXT : DIM, false);
            graphics.drawString(font, group.deviceCount() + " dev", x + width - 44, rowY + 6, selected ? OK_GREEN : MUTED, false);
        }
        renderOverflowHint(graphics, groups.size(), start, rows, x, y + rows * (ROW_H + ROW_GAP), width);
    }

    private void renderControlRows(GuiGraphics graphics, List<FccGuiControlEntry> controls, int x, int y, int width, int maxRows, int mouseX, int mouseY) {
        if (controls.isEmpty()) {
            renderEmptyState(graphics, x, y, width, "No controls yet");
            return;
        }
        int start = visibleStartIndex(selectedControlIndex, controls.size(), maxRows);
        int rows = Math.min(maxRows, controls.size() - start);
        for (int visible = 0; visible < rows; visible++) {
            int index = start + visible;
            FccGuiControlEntry control = controls.get(index);
            int rowY = y + visible * (ROW_H + ROW_GAP);
            boolean selected = index == selectedControlIndex;
            boolean hover = mouseX >= x && mouseX < x + width && mouseY >= rowY && mouseY < rowY + ROW_H;
            renderListRowBase(graphics, x, rowY, width, ROW_H, selected, hover, WARN);
            String routes = control.routeCount() + " rt";
            int routeX = x + width - font.width(routes) - 8;
            int labelW = Math.max(24, routeX - (x + 12) - 8);
            graphics.drawString(font, font.plainSubstrByWidth(control.label(), labelW), x + 12, rowY + 5, selected ? TEXT : DIM, false);
            graphics.drawString(font, displayControlType(control), x + 12, rowY + 15, MUTED, false);
            graphics.drawString(font, routes, routeX, rowY + 8, selected ? ACCENT : MUTED, false);
        }
        renderOverflowHint(graphics, controls.size(), start, rows, x, y + rows * (ROW_H + ROW_GAP), width);
    }

    private void renderControlRowsFrom(GuiGraphics graphics, List<FccGuiControlEntry> controls, int x, int y, int width, int maxRows,
                                       int start, int mouseX, int mouseY) {
        if (controls.isEmpty()) {
            renderEmptyState(graphics, x, y, width, "No controls yet");
            return;
        }
        int safeStart = clampScrollStart(start, controls.size(), maxRows);
        int rows = Math.min(maxRows, controls.size() - safeStart);
        for (int visible = 0; visible < rows; visible++) {
            int index = safeStart + visible;
            FccGuiControlEntry control = controls.get(index);
            int rowY = y + visible * (ROW_H + ROW_GAP);
            boolean selected = index == selectedControlIndex;
            boolean hover = mouseX >= x && mouseX < x + width && mouseY >= rowY && mouseY < rowY + ROW_H;
            renderListRowBase(graphics, x, rowY, width, ROW_H, selected, hover, WARN);
            String routes = control.routeCount() + " rt";
            int routeX = x + width - font.width(routes) - 8;
            int labelW = Math.max(24, routeX - (x + 12) - 8);
            graphics.drawString(font, font.plainSubstrByWidth(control.label(), labelW), x + 12, rowY + 5, selected ? TEXT : DIM, false);
            graphics.drawString(font, displayControlType(control), x + 12, rowY + 15, MUTED, false);
            graphics.drawString(font, routes, routeX, rowY + 8, selected ? ACCENT : MUTED, false);
        }
        renderListScrollbar(graphics, x + width - 6, y, 5, rows * (ROW_H + ROW_GAP) - ROW_GAP, controls.size(), safeStart, maxRows, WARN);
    }

    private void renderGroupRowsFrom(GuiGraphics graphics, List<FccGuiGroupEntry> groups, int x, int y, int width, int maxRows,
                                     int start, int mouseX, int mouseY) {
        if (groups.isEmpty()) {
            renderEmptyState(graphics, x, y, width, "No groups yet");
            return;
        }
        int safeStart = clampScrollStart(start, groups.size(), maxRows);
        int rows = Math.min(maxRows, groups.size() - safeStart);
        for (int visible = 0; visible < rows; visible++) {
            int index = safeStart + visible;
            FccGuiGroupEntry group = groups.get(index);
            int rowY = y + visible * (ROW_H + ROW_GAP);
            boolean selected = index == selectedGroupIndex;
            boolean hover = mouseX >= x && mouseX < x + width && mouseY >= rowY && mouseY < rowY + ROW_H;
            renderListRowBase(graphics, x, rowY, width, ROW_H, selected, hover, OK_GREEN);
            graphics.drawString(font, font.plainSubstrByWidth(group.label(), width - 58), x + 12, rowY + 6, selected ? TEXT : DIM, false);
            graphics.drawString(font, group.deviceCount() + " dev", x + width - 42, rowY + 6, selected ? OK_GREEN : MUTED, false);
        }
        renderListScrollbar(graphics, x + width - 6, y, 5, rows * (ROW_H + ROW_GAP) - ROW_GAP, groups.size(), safeStart, maxRows, OK_GREEN);
    }

    private void renderRouteRows(GuiGraphics graphics, List<FccGuiRouteEntry> routes, int x, int y, int width, int maxRows, int mouseX, int mouseY) {
        if (routes.isEmpty()) {
            renderEmptyState(graphics, x, y, width, "No routes yet");
            return;
        }
        int rows = Math.min(maxRows, routes.size());
        for (int index = 0; index < rows; index++) {
            FccGuiRouteEntry route = routes.get(index);
            int rowY = y + index * (SMALL_ROW_H + ROW_GAP);
            boolean selected = index == selectedRouteIndex;
            boolean hover = mouseX >= x && mouseX < x + width && mouseY >= rowY && mouseY < rowY + SMALL_ROW_H;
            renderListRowBase(graphics, x, rowY, width, SMALL_ROW_H, selected, hover, ACCENT);
            String left = trimLabel(route.targetLabel(), 18);
            graphics.drawString(font, left, x + 10, rowY + 5, selected ? TEXT : DIM, false);
            String right = shortOutput(route.outputType()) + " " + String.format(java.util.Locale.ROOT, "%.2f", route.scale());
            graphics.drawString(font, font.plainSubstrByWidth(right, width - 92), x + width - 78, rowY + 5, route.invert() ? WARN : ACCENT, false);
        }
        renderOverflowHint(graphics, routes.size(), 0, rows, x, y + rows * (SMALL_ROW_H + ROW_GAP), width);
    }

    private void renderListScrollbar(GuiGraphics graphics, int x, int y, int width, int height, int total, int start, int maxRows, int color) {
        if (total <= maxRows || height <= 0) return;
        graphics.fill(x, y, x + width, y + height, 0x66071016);
        int barH = Math.max(14, height * maxRows / total);
        int maxScroll = Math.max(1, total - maxRows);
        int barY = y + (int) Math.round(start / (double) maxScroll * (height - barH));
        graphics.fill(x, barY, x + width, barY + barH, color);
    }

    private void renderListRowBase(GuiGraphics graphics, int x, int y, int width, int height, boolean selected, boolean hover, int accent) {
        graphics.fill(x, y, x + width, y + height, selected ? CARD_ACTIVE : hover ? CARD_HOVER : CARD_BG);
        border(graphics, x, y, width, height, selected ? accent : SOFT_BORDER);
        graphics.fill(x, y, x + 4, y + height, accent);
    }

    private void renderEmptyState(GuiGraphics graphics, int x, int y, int width, String label) {
        graphics.fill(x, y, x + width, y + 42, CARD_BG);
        border(graphics, x, y, width, 42, SOFT_BORDER);
        graphics.drawCenteredString(font, label, x + width / 2, y + 17, DIM);
    }

    private void renderMetricCard(GuiGraphics graphics, int x, int y, int width, int height, String label, String value, int color) {
        graphics.fill(x, y, x + width, y + height, CARD_BG);
        border(graphics, x, y, width, height, SOFT_BORDER);
        graphics.fill(x, y, x + width, y + 3, color);
        graphics.drawString(font, label, x + 8, y + 10, DIM, false);
        graphics.drawString(font, value, x + 8, y + 28, TEXT, false);
    }

    private void renderMiniList(GuiGraphics graphics, int x, int y, int width, List<String> lines, int color) {
        int rows = Math.min(3, lines.size());
        for (int i = 0; i < rows; i++) {
            graphics.drawString(font, font.plainSubstrByWidth(lines.get(i), width), x, y + i * 12, color, false);
        }
    }

    private void renderValueTrack(GuiGraphics graphics, int x, int y, int width, int height, double value, double min, double max, double neutral) {
        graphics.fill(x, y, x + width, y + height, 0xFF0D141C);
        border(graphics, x, y, width, height, SOFT_BORDER);
        double span = Math.max(1.0e-6, max - min);
        int neutralX = x + (int) Math.round((neutral - min) / span * width);
        int valueX = x + (int) Math.round((value - min) / span * width);
        neutralX = Math.max(x, Math.min(x + width, neutralX));
        valueX = Math.max(x, Math.min(x + width, valueX));
        graphics.fill(neutralX, y - 3, neutralX + 1, y + height + 3, MUTED);
        graphics.fill(Math.min(neutralX, valueX), y + 5, Math.max(neutralX, valueX), y + height - 5, valueX >= neutralX ? OK_GREEN : WARN);
        graphics.fill(valueX - 2, y + 2, valueX + 3, y + height - 2, ACCENT);
    }

    private void renderTinyProgress(GuiGraphics graphics, int x, int y, int width, int value, int max, int color) {
        graphics.fill(x, y, x + width, y + 8, 0xFF0D141C);
        border(graphics, x, y, width, 8, SOFT_BORDER);
        int fill = max <= 0 ? 0 : Math.max(0, Math.min(width, width * value / max));
        graphics.fill(x, y, x + fill, y + 8, color);
    }

    private void renderOverflowHint(GuiGraphics graphics, int total, int start, int rows, int x, int y, int width) {
        int remaining = Math.max(0, total - start - rows);
        if (start <= 0 && remaining <= 0) return;
        String text = (start > 0 ? start + " above" : "") + (start > 0 && remaining > 0 ? " | " : "") + (remaining > 0 ? remaining + " below" : "");
        graphics.drawString(font, font.plainSubstrByWidth(text, width - 8), x + 6, y + 2, MUTED, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (handleHelpClick(mouseX, mouseY)) return true;
        if (helpOpen) return true;
        if (handleNavClick(mouseX, mouseY)) return true;
        if (handleCardRackClick(mouseX, mouseY)) return true;
        if (handleViewClick(mouseX, mouseY)) return true;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean handleCardRackClick(double mouseX, double mouseY) {
        int panelLeft = leftPos + DOCK_X;
        int viewportRows = menu.getRackViewportRows();
        int scrollStart = menu.getCardScrollStart();
        for (int visual = 0; visual < viewportRows; visual++) {
            int row = scrollStart + visual;
            int rowY = topPos + FlightControlComputerMenu.ROW_START_Y + visual * FlightControlComputerMenu.ROW_HEIGHT;
            int modeX = panelLeft + 88;
            int modeY = rowY + 2;
            if (!inside(mouseX, mouseY, modeX, modeY, 42, 15)) continue;
            sendToggleRackRowMode(row);
            notice = "Rack row " + (row + 1) + " mode toggled";
            return true;
        }
        return false;
    }

    private boolean handleHelpClick(double mouseX, double mouseY) {
        int helpX = leftPos + HELP_X;
        int helpY = topPos + HELP_Y;
        if (inside(mouseX, mouseY, helpX, helpY, HELP_SIZE, HELP_SIZE)) {
            helpOpen = !helpOpen;
            return true;
        }
        if (!helpOpen) return false;
        int panelX = leftPos + 254;
        int panelY = topPos + 86;
        int panelW = 412;
        if (inside(mouseX, mouseY, panelX + panelW - 28, panelY + 8, 18, 18)) {
            helpOpen = false;
            return true;
        }
        return true;
    }

    private boolean handleNavClick(double mouseX, double mouseY) {
        int x = leftPos + MARGIN;
        int y = topPos + NAV_Y;
        int gap = 4;
        int width = (W - MARGIN * 2 - gap * (WorkStation.values().length - 1)) / WorkStation.values().length;
        WorkStation[] values = WorkStation.values();
        for (int i = 0; i < values.length; i++) {
            int bx = x + i * (width + gap);
            if (mouseX >= bx && mouseX < bx + width && mouseY >= y && mouseY < y + NAV_H) {
                if (activeStation != values[i]) {
                    activeStation = values[i];
                    activeView = values[i].defaultView();
                    rememberStation();
                    notice = "";
                    rebuildViewWidgets();
                }
                return true;
            }
        }
        WorkView[] subViews = activeStation.views;
        int subX = leftPos + MAIN_X + 10;
        int subY = topPos + MAIN_Y + 21;
        int subGap = 4;
        int totalW = MAIN_W - 20;
        int subW = subViews.length <= 0 ? totalW : (totalW - subGap * (subViews.length - 1)) / subViews.length;
        for (int i = 0; i < subViews.length; i++) {
            int bx = subX + i * (subW + subGap);
            if (mouseX >= bx && mouseX < bx + subW && mouseY >= subY && mouseY < subY + 14) {
                if (activeView != subViews[i]) {
                    activeView = subViews[i];
                    activeStation = stationForView(activeView);
                    rememberStation();
                    notice = "";
                    rebuildViewWidgets();
                }
                return true;
            }
        }
        return false;
    }

    private boolean handleViewClick(double mouseX, double mouseY) {
        FccGuiSnapshot snapshot = snapshotOrEmpty();
        return switch (activeView) {
            case DEVICES -> clickDeviceList(snapshot, mouseX, mouseY, leftPos + MAIN_X + 18, topPos + MAIN_Y + 42, MAIN_W - 36, DEVICE_ROWS);
            case GROUPS -> clickGroupsView(snapshot, mouseX, mouseY);
            case DIAGRAM -> clickDiagramView(snapshot, mouseX, mouseY);
            case OTOMOBIL -> clickOtomobilView(snapshot, mouseX, mouseY);
            case AUTO_MODE -> false;
            case CONTROLS -> clickControlsView(snapshot, mouseX, mouseY);
            case E_MANUEL -> clickEmanuelView(mouseX, mouseY);
            case LINKED_CONTROLLER -> false;
            case TEST -> clickTestView(snapshot, mouseX, mouseY);
            case SHIP -> clickShipView(snapshot, mouseX, mouseY);
            case TEMPLATE -> false;
        };
    }

    private boolean clickEmanuelView(double mouseX, double mouseY) {
        FccTelemetryPacket telemetry = FccClientState.telemetryFor(menu.containerId);
        int count = telemetryBindingCount(telemetry);
        if (count <= 0) {
            return false;
        }
        int x = leftPos + MAIN_X + 14;
        int y = topPos + MAIN_Y + 38;
        int listW = 170;
        int listRows = 8;
        int listY = y + 22;

        // Check for fuse switch clicks (left edge, 16x16 each row)
        int start = visibleStartIndex(selectedEmanuelIndex, count, listRows);
        int rows = Math.min(listRows, count - start);
        for (int visible = 0; visible < rows; visible++) {
            int index = start + visible;
            int rowY = listY + visible * (ROW_H + ROW_GAP);
            int fuseX = x + 8;
            int fuseY = rowY + 5;
            if (inside(mouseX, mouseY, fuseX, fuseY, 16, 16)) {
                // Toggle override for this binding
                if (index < emanuelOverrideActive.length) {
                    emanuelOverrideActive[index] = !emanuelOverrideActive[index];
                    if (!emanuelOverrideActive[index]) {
                        emanuelRpmCommands[index] = 0; // reset RPM when disabling
                    }
                    sendOverrideCommand();
                    selectedEmanuelIndex = index;
                    selectedEmanuelRowHint = intAt(telemetry.bindingRows(), index, index) + 1;
                    return true;
                }
            }
        }

        // Check for row selection (in list body, not fuse)
        for (int visible = 0; visible < rows; visible++) {
            int index = start + visible;
            int rowY = listY + visible * (ROW_H + ROW_GAP);
            if (inside(mouseX, mouseY, x + 22, rowY, listW - 22, ROW_H)) {
                selectedEmanuelIndex = index;
                selectedEmanuelRowHint = intAt(telemetry.bindingRows(), index, index) + 1;
                return true;
            }
        }

        // Check for RPM slider interaction (detail panel)
        if (selectedEmanuelIndex < count && selectedEmanuelIndex < emanuelOverrideActive.length && emanuelOverrideActive[selectedEmanuelIndex]) {
            int detailX = x + listW + 14;
            int detailW = MAIN_W - 42 - listW;
            int detailY = topPos + MAIN_Y + 38 + 22;
            int sliderTrackX = detailX + 126;
            int sliderTrackY = detailY + 210;
            int sliderTrackW = detailW - 138;
            int sliderTrackHeight = 10;
            if (inside(mouseX, mouseY, sliderTrackX, sliderTrackY - 2, sliderTrackW, sliderTrackHeight + 4)) {
                // Adjust slider based on mouse X
                int relativeX = (int)(mouseX - sliderTrackX);
                int newRpm = (int)((relativeX / (float)(sliderTrackW - 10)) * emanuelSliderRpmMax);
                newRpm = Math.max(0, Math.min(emanuelSliderRpmMax, newRpm));
                emanuelRpmCommands[selectedEmanuelIndex] = newRpm;
                sendOverrideCommand();
                return true;
            }
        }

        return false;
    }

    private void sendOverrideCommand() {
        PacketDistributor.sendToServer(new com.createstats.flight.network.FccOverrideCommandPacket(
            menu.containerId,
            getActiveBindingIndices(),
            emanuelRpmCommands,
            emanuelOverrideActive
        ));
    }

    private int[] getActiveBindingIndices() {
        int count = Math.min(emanuelOverrideActive.length, emanuelRpmCommands.length);
        int[] indices = new int[count];
        for (int i = 0; i < count; i++) {
            indices[i] = i;
        }
        return indices;
    }

    private boolean clickGroupsView(FccGuiSnapshot snapshot, double mouseX, double mouseY) {
        int x = leftPos + MAIN_X + 18;
        int y = topPos + MAIN_Y + 42;
        int leftW = 220;
        int rightX = x + leftW + 18;
        int rightW = MAIN_W - 54 - leftW;
        return clickGroupList(snapshot, mouseX, mouseY, x, y, leftW, GROUP_ROWS)
                || clickDeviceList(snapshot, mouseX, mouseY, rightX, y, rightW, GROUP_ROWS);
    }

    private boolean clickDiagramView(FccGuiSnapshot snapshot, double mouseX, double mouseY) {
        if (!canEditShipDiagram(snapshot)) {
            notice = "Airship Layout locked by active Ground Layout";
            return true;
        }
        int x = leftPos + MAIN_X + 14;
        int y = topPos + MAIN_Y + 58;
        int boardW = 322;
        int boardH = 278;
        int deviceX = x + boardW + 14;
        int deviceW = MAIN_W - 42 - boardW;
        if (inside(mouseX, mouseY, x, y, boardW, boardH)) {
            DiagramZone[] zones = DiagramZone.values();
            for (int i = 0; i < zones.length; i++) {
                DiagramZone zone = zones[i];
                if (!inside(mouseX, mouseY, x + zone.x, y + zone.y, zone.width, zone.height)) continue;
                selectedDiagramZoneIndex = i;
                int groupIndex = diagramZoneGroupIndex(snapshot, zone);
                if (groupIndex >= 0) selectedGroupIndex = groupIndex;
                notice = "Zone: " + trimLabel(zone.label, 24);
                rebuildViewWidgets();
                return true;
            }
            return true;
        }
        return clickDeviceList(snapshot, mouseX, mouseY, deviceX, y, deviceW, 9);
    }

    private boolean clickOtomobilView(FccGuiSnapshot snapshot, double mouseX, double mouseY) {
        if (!canEditOtomobilDiagram(snapshot)) {
            notice = "Ground Layout locked by active Airship Layout";
            return true;
        }
        int x = leftPos + MAIN_X + 14;
        int y = topPos + MAIN_Y + 58;
        int boardW = 322;
        int boardH = 278;
        int deviceX = x + boardW + 14;
        int deviceW = MAIN_W - 42 - boardW;
        if (inside(mouseX, mouseY, x, y, boardW, boardH)) {
            OtomobilZone[] zones = OtomobilZone.values();
            for (int i = 0; i < zones.length; i++) {
                OtomobilZone zone = zones[i];
                if (!inside(mouseX, mouseY, x + zone.x, y + zone.y, zone.width, zone.height)) continue;
                if (inside(mouseX, mouseY, x + zone.x + 5, y + zone.y + zone.height - 11, 22, 8)) {
                    List<FccGuiRouteEntry> routes = otomobilZoneThrottleRoutes(snapshot, zone);
                    if (!routes.isEmpty()) {
                        boolean currentlyInverted = false;
                        for (FccGuiRouteEntry route : routes) {
                            currentlyInverted |= route.invert();
                            sendToggleRouteInvert(route.id());
                        }
                        notice = zone.shortLabel + (currentlyInverted ? " invert off" : " invert on");
                    }
                    return true;
                }
                selectedOtomobilZoneIndex = i;
                int groupIndex = otomobilZoneGroupIndex(snapshot, zone);
                if (groupIndex >= 0) selectedGroupIndex = groupIndex;
                notice = "Zone: " + trimLabel(zone.label, 24);
                rebuildViewWidgets();
                return true;
            }
            return true;
        }
        return clickDeviceList(snapshot, mouseX, mouseY, deviceX, y, deviceW, 9);
    }

    private boolean clickControlsView(FccGuiSnapshot snapshot, double mouseX, double mouseY) {
        int x = leftPos + MAIN_X + 14;
        int y = topPos + MAIN_Y + 38;
        int listW = 140;
        int boardX = x + listW + 14;
        int boardW = MAIN_W - 42 - listW;
        int controlY = y + 18;
        int groupY = y + 172;
        return clickControlListFrom(snapshot, mouseX, mouseY, x, controlY, listW, CONTROL_LIST_ROWS, controlScrollStart)
            || clickGroupListFrom(snapshot, mouseX, mouseY, x, groupY, listW, CONTROL_GROUP_ROWS, controlGroupScrollStart)
                || clickRouteSchematic(snapshot, mouseX, mouseY, boardX, controlY, boardW);
    }

    private boolean clickRouteSchematic(FccGuiSnapshot snapshot, double mouseX, double mouseY, int x, int y, int width) {
        List<FccGuiRouteEntry> routes = selectedRoutes(snapshot);
        if (routes.isEmpty()) return false;
        int start = clampScrollStart(controlRouteScrollStart, routes.size(), CONTROL_ROUTE_ROWS);
        int rows = Math.min(CONTROL_ROUTE_ROWS, routes.size() - start);
        int nodeW = 130;
        int nodeH = 32;
        int nodeX = x + width - nodeW - 14;
        int firstY = y + 38;
        for (int visible = 0; visible < rows; visible++) {
            int index = start + visible;
            int nodeY = firstY + visible * 40;
            if (!inside(mouseX, mouseY, nodeX, nodeY, nodeW, nodeH)) continue;
            selectedRouteIndex = index;
            notice = "Route: " + trimLabel(routes.get(index).targetLabel(), 24);
            rebuildViewWidgets();
            return true;
        }
        return false;
    }

    private boolean clickTestView(FccGuiSnapshot snapshot, double mouseX, double mouseY) {
        int x = leftPos + MAIN_X + 18;
        int y = topPos + MAIN_Y + 42;
        int listW = 160;
        return clickControlList(snapshot, mouseX, mouseY, x, y, listW, 7);
    }

    private boolean clickShipView(FccGuiSnapshot snapshot, double mouseX, double mouseY) {
        OperateProfileState inspectorProfile = resolveOperateProfile(snapshot);
        if (inspectorProfile.profile() == OperateProfile.AIRSHIP) {
            int inspectorX = leftPos + INSPECT_X + 10;
            int inspectorY = topPos + INSPECT_Y + 30;
            if (clickBombActionPanel(snapshot, mouseX, mouseY, inspectorX, inspectorY + 92, INSPECT_W - 20)) return true;
        }
        int x = leftPos + MAIN_X + 18;
        int y = topPos + MAIN_Y + 38;
        int width = MAIN_W - 36;
        int height = MAIN_H - 72;
        OperateProfileState profileState = resolveOperateProfile(snapshot);
        if (handleOperateProfileClick(mouseX, mouseY, profileState, x, y, width, height)) return true;
        boolean vehicleEnabled = canUseVehicleControls(snapshot);
        ShipControls shipControls = resolveShipControls(snapshot);
        FccGuiControlEntry thrust = shipControls.thrust();
        FccGuiControlEntry yaw = shipControls.yaw();
        FccGuiControlEntry trim = shipControls.trim();

        // steering wheel (left panel)
        int wheelCx = x + 65;
        int wheelCy = y + 108;
        if (yaw != null && distanceSq(mouseX, mouseY, wheelCx, wheelCy) <= 46 * 46) {
            shipDrag = ShipDrag.YAW;
            shipDragControl = yaw;
            updateShipDrag(mouseX, mouseY, true);
            return true;
        }

        // throttle slider (left panel)
        int throttleX = x + 122;
        int throttleY = y + 30;
        if (thrust != null && mouseX >= throttleX && mouseX < throttleX + 42 && mouseY >= throttleY && mouseY < throttleY + SHIP_THROTTLE_H) {
            shipDrag = ShipDrag.LIFT;
            shipDragControl = thrust;
            updateShipDrag(mouseX, mouseY, true);
            return true;
        }

        if (profileState.profile() == OperateProfile.AIRSHIP) {
            if (clickShipOverheadSwitch(snapshot, mouseX, mouseY, x, y + 156)) return true;
            int trimCx = x + 234;
            int trimCy = y + 104;
            if (trim != null && distanceSq(mouseX, mouseY, trimCx, trimCy) <= 36 * 36) {
                shipDrag = ShipDrag.TRIM;
                shipDragControl = trim;
                updateShipDrag(mouseX, mouseY, true);
                return true;
            }
            return profileState.hybrid() && !profileState.manual();
        }

        // mode buttons (right panel)
        // Coordinates must match renderShipView exactly
        int leftPanelW = 175;
        int rX = x + leftPanelW + 8;
        int rW = width - leftPanelW - 10;
        int btnH = 18;
        int btnGap = 4;
        int btnW4 = (rW - btnGap * 3) / 4;
        int row0Y = y + 26;
        int row1Y = row0Y + btnH + btnGap;
        int row2Y = row1Y + btnH + btnGap;
        int btnWHalf = (rW - btnGap) / 2;

        if (vehicleEnabled) {
            FccGuiControlEntry drive4x4      = findControl(snapshot, "Drive Mode 4x4 Switch");
            FccGuiControlEntry drive2x4Front = findControl(snapshot, "Drive Mode 2x4 Front Switch");
            FccGuiControlEntry drive2x4Rear  = findControl(snapshot, "Drive Mode 2x4 Rear Switch");
            FccGuiControlEntry midAxle       = findControl(snapshot, "Mid Axle Drive Switch");
            FccGuiControlEntry steerFront    = findControl(snapshot, "Steer Mode Front Switch");
            FccGuiControlEntry steerAll      = findControl(snapshot, "Steer Mode All-Wheel Switch");
            FccGuiControlEntry steerCrab     = findControl(snapshot, "Steer Mode Crab Switch");
            FccGuiControlEntry diffRear      = findControl(snapshot, "Diff Lock Rear Switch");
            FccGuiControlEntry diffFull      = findControl(snapshot, "Diff Lock Full Switch");

            // Drive row
            if (inside(mouseX, mouseY, rX,                        row0Y, btnW4, btnH)) { sendSetExclusiveVehicleMode(drive4x4, drive2x4Front, drive2x4Rear); return true; }
            if (inside(mouseX, mouseY, rX + (btnW4 + btnGap),     row0Y, btnW4, btnH)) { sendSetExclusiveVehicleMode(drive2x4Front, drive4x4, drive2x4Rear); return true; }
            if (inside(mouseX, mouseY, rX + (btnW4 + btnGap) * 2, row0Y, btnW4, btnH)) { sendSetExclusiveVehicleMode(drive2x4Rear, drive4x4, drive2x4Front); return true; }
            if (inside(mouseX, mouseY, rX + (btnW4 + btnGap) * 3, row0Y, btnW4, btnH)) { sendToggleControl(midAxle); return true; }
            // Steer row
            if (inside(mouseX, mouseY, rX,                        row1Y, btnW4, btnH)) { sendSetExclusiveVehicleMode(steerFront, steerAll, steerCrab); return true; }
            if (inside(mouseX, mouseY, rX + (btnW4 + btnGap),     row1Y, btnW4, btnH)) { sendSetExclusiveVehicleMode(steerAll, steerFront, steerCrab); return true; }
            if (inside(mouseX, mouseY, rX + (btnW4 + btnGap) * 2, row1Y, btnW4, btnH)) { sendSetExclusiveVehicleMode(steerCrab, steerFront, steerAll); return true; }
            // Diff row
            if (inside(mouseX, mouseY, rX,                  row2Y, btnWHalf, btnH)) { sendSetExclusiveVehicleMode(diffRear, diffFull); return true; }
            if (inside(mouseX, mouseY, rX + btnWHalf + btnGap, row2Y, btnWHalf, btnH)) { sendSetExclusiveVehicleMode(diffFull, diffRear); return true; }
        }
        if (!vehicleEnabled && inside(mouseX, mouseY, x + leftPanelW, y + 22, width - leftPanelW, height - 24)) {
            notice = "Vehicle modes are available after Otomobil zones are assigned";
            return true;
        }
        return false;
    }

    private boolean clickBombActionPanel(FccGuiSnapshot snapshot, double mouseX, double mouseY, int x, int y, int width) {
        FccGuiControlEntry bay = findBayOpenControl(snapshot);
        FccGuiControlEntry safety = findBombSafetyControl(snapshot);
        FccGuiControlEntry bomb = findBombDropControl(snapshot);
        boolean bayDefined = hasRoutesForControl(snapshot, bay);
        boolean bayOpen = !bayDefined || controlFraction(bay) >= 0.5;
        boolean safetyArmed = controlFraction(safety) >= 0.5;
        boolean bombDefined = hasRoutesForControl(snapshot, bomb);
        int rowY = y + 8;
        if (bayDefined) {
            if (inside(mouseX, mouseY, x + 6, rowY, width - 12, 18)) {
                sendToggleControl(bay);
                return true;
            }
            rowY += 24;
        }
        int safetyW = Math.min(44, Math.max(36, width / 3));
        int dropW = width - safetyW - 16;
        if (inside(mouseX, mouseY, x + 6, rowY, safetyW, 24)) {
            if (safety == null) {
                notice = "Refresh Cockpit to build Bomb Safety";
            } else {
                sendToggleShipSwitch(safety);
            }
            return true;
        }
        if (inside(mouseX, mouseY, x + safetyW + 10, rowY, dropW, 24)) {
            if (!bombDefined) {
                notice = "No Bomb Drop link defined";
            } else if (!safetyArmed) {
                notice = "Bomb safety is locked";
            } else if (!bayOpen) {
                notice = "Open bomb bay first";
            } else {
                sendMomentaryControl(bomb);
            }
            return true;
        }
        return false;
    }

    private boolean handleOperateProfileClick(double mouseX, double mouseY, OperateProfileState state,
                                              int x, int y, int width, int height) {
        int chipX = x + 150;
        int chipY = y + 4;
        if (inside(mouseX, mouseY, chipX, chipY, 34, 12)) {
            setManualOperateProfile(null);
            return true;
        }
        if (inside(mouseX, mouseY, chipX + 38, chipY, 52, 12)) {
            setManualOperateProfile(OperateProfile.AIRSHIP);
            return true;
        }
        if (inside(mouseX, mouseY, chipX + 94, chipY, 54, 12)) {
            setManualOperateProfile(OperateProfile.GROUND);
            return true;
        }
        if (state.hybrid() && !state.manual()) {
            int cardW = (width - 54) / 2;
            int cardY = y + 118;
            if (inside(mouseX, mouseY, x + 18, cardY, cardW, 92)) {
                setManualOperateProfile(OperateProfile.AIRSHIP);
                return true;
            }
            if (inside(mouseX, mouseY, x + 36 + cardW, cardY, cardW, 92)) {
                setManualOperateProfile(OperateProfile.GROUND);
                return true;
            }
        }
        return false;
    }

    private void setManualOperateProfile(@Nullable OperateProfile profile) {
        manualOperateProfile = profile;
        notice = profile == null ? "Operate profile set to Auto" : "Operate profile: " + profile.label;
        rebuildViewWidgets();
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (shipDrag != ShipDrag.NONE && shipDragControl != null) {
            updateShipDrag(mouseX, mouseY, false);
            return true;
        }
        if (getFocused() instanceof AbstractWidget widget && widget.mouseDragged(mouseX, mouseY, button, dragX, dragY)) {
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (shipDrag != ShipDrag.NONE && shipDragControl != null) {
            updateShipDrag(mouseX, mouseY, true);
            shipDrag = ShipDrag.NONE;
            shipDragControl = null;
            return true;
        }
        if (heldMomentaryControlId != null) {
            UUID releaseId = heldMomentaryControlId;
            heldMomentaryControlId = null;
            sendSetControlValue(releaseId, 0.0, false);
            notice = "Momentary released";
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseX >= leftPos + DOCK_X && mouseX < leftPos + DOCK_X + DOCK_W
                && mouseY >= topPos + DOCK_Y && mouseY < topPos + DOCK_Y + DOCK_H
                && menu.getMaxCardScrollStart() > 0) {
            int next = menu.getCardScrollStart() - (int) Math.signum(scrollY);
            setCardScrollStart(next);
            return true;
        }
        if (activeView == WorkView.CONTROLS && handleControlsScroll(mouseX, mouseY, scrollY)) return true;
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private boolean handleControlsScroll(double mouseX, double mouseY, double scrollY) {
        FccGuiSnapshot snapshot = snapshotOrEmpty();
        int x = leftPos + MAIN_X + 14;
        int y = topPos + MAIN_Y + 38;
        int listW = 140;
        int boardX = x + listW + 14;
        int boardW = MAIN_W - 42 - listW;
        int controlY = y + 18;
        int groupY = y + 172;
        int listH = CONTROL_LIST_ROWS * (ROW_H + ROW_GAP) - ROW_GAP;
        int groupH = CONTROL_GROUP_ROWS * (ROW_H + ROW_GAP) - ROW_GAP;
        if (inside(mouseX, mouseY, x, controlY, listW, listH)) {
            controlScrollStart = scrollStart(controlScrollStart, snapshot.controlEntries().size(), CONTROL_LIST_ROWS, scrollY);
            return true;
        }
        if (inside(mouseX, mouseY, x, groupY, listW, groupH)) {
            controlGroupScrollStart = scrollStart(controlGroupScrollStart, snapshot.groupEntries().size(), CONTROL_GROUP_ROWS, scrollY);
            return true;
        }
        if (inside(mouseX, mouseY, boardX, controlY, boardW, 270)) {
            controlRouteScrollStart = scrollStart(controlRouteScrollStart, selectedRoutes(snapshot).size(), CONTROL_ROUTE_ROWS, scrollY);
            return true;
        }
        return false;
    }

    private boolean clickDeviceList(FccGuiSnapshot snapshot, double mouseX, double mouseY, int x, int y, int width, int maxRows) {
        List<FccGuiDeviceEntry> devices = snapshot.deviceEntries();
        int index = clickedListIndex(mouseX, mouseY, x, y, width, maxRows, devices.size(), selectedDeviceIndex, ROW_H);
        if (index < 0) return false;
        String discarded = pendingUnsavedEditNotice(snapshot);
        selectedDeviceIndex = index;
        notice = discarded != null ? discarded : "Device: " + trimLabel(devices.get(index).label(), 24);
        rebuildViewWidgets();
        return true;
    }

    private boolean clickGroupList(FccGuiSnapshot snapshot, double mouseX, double mouseY, int x, int y, int width, int maxRows) {
        List<FccGuiGroupEntry> groups = snapshot.groupEntries();
        int index = clickedListIndex(mouseX, mouseY, x, y, width, maxRows, groups.size(), selectedGroupIndex, ROW_H);
        if (index < 0) return false;
        String discarded = pendingUnsavedEditNotice(snapshot);
        selectedGroupIndex = index;
        notice = discarded != null ? discarded : "Group: " + trimLabel(groups.get(index).label(), 24);
        rebuildViewWidgets();
        return true;
    }

    private boolean clickControlList(FccGuiSnapshot snapshot, double mouseX, double mouseY, int x, int y, int width, int maxRows) {
        List<FccGuiControlEntry> controls = snapshot.controlEntries();
        int index = clickedListIndex(mouseX, mouseY, x, y, width, maxRows, controls.size(), selectedControlIndex, ROW_H);
        if (index < 0) return false;
        String discarded = pendingUnsavedEditNotice(snapshot);
        selectedControlIndex = index;
        selectedRouteIndex = 0;
        notice = discarded != null ? discarded : "Control: " + trimLabel(controls.get(index).label(), 24);
        rebuildViewWidgets();
        return true;
    }

    private boolean clickControlListFrom(FccGuiSnapshot snapshot, double mouseX, double mouseY, int x, int y, int width, int maxRows, int start) {
        List<FccGuiControlEntry> controls = snapshot.controlEntries();
        int index = clickedListIndexFrom(mouseX, mouseY, x, y, width, maxRows, controls.size(), start, ROW_H);
        if (index < 0) return false;
        String discarded = pendingUnsavedEditNotice(snapshot);
        selectedControlIndex = index;
        selectedRouteIndex = 0;
        controlRouteScrollStart = 0;
        notice = discarded != null ? discarded : "Control: " + trimLabel(controls.get(index).label(), 24);
        rebuildViewWidgets();
        return true;
    }

    private boolean clickGroupListFrom(FccGuiSnapshot snapshot, double mouseX, double mouseY, int x, int y, int width, int maxRows, int start) {
        List<FccGuiGroupEntry> groups = snapshot.groupEntries();
        int index = clickedListIndexFrom(mouseX, mouseY, x, y, width, maxRows, groups.size(), start, ROW_H);
        if (index < 0) return false;
        String discarded = pendingUnsavedEditNotice(snapshot);
        selectedGroupIndex = index;
        notice = discarded != null ? discarded : "Group: " + trimLabel(groups.get(index).label(), 24);
        rebuildViewWidgets();
        return true;
    }

    private boolean clickRouteList(FccGuiSnapshot snapshot, double mouseX, double mouseY, int x, int y, int width, int maxRows) {
        List<FccGuiRouteEntry> routes = selectedRoutes(snapshot);
        int index = clickedListIndex(mouseX, mouseY, x, y, width, maxRows, routes.size(), 0, SMALL_ROW_H);
        if (index < 0) return false;
        selectedRouteIndex = index;
        notice = "Route: " + trimLabel(routes.get(index).targetLabel(), 24);
        rebuildViewWidgets();
        return true;
    }

    private int clickedListIndex(double mouseX, double mouseY, int x, int y, int width, int maxRows, int size, int selected, int rowHeight) {
        if (size <= 0 || mouseX < x || mouseX >= x + width || mouseY < y) return -1;
        int row = (int) ((mouseY - y) / (rowHeight + ROW_GAP));
        int rowY = y + row * (rowHeight + ROW_GAP);
        if (row < 0 || row >= maxRows || mouseY >= rowY + rowHeight) return -1;
        int start = visibleStartIndex(selected, size, maxRows);
        int index = start + row;
        return index >= 0 && index < size ? index : -1;
    }

    private int clickedListIndexFrom(double mouseX, double mouseY, int x, int y, int width, int maxRows, int size, int start, int rowHeight) {
        if (size <= 0 || mouseX < x || mouseX >= x + width || mouseY < y) return -1;
        int row = (int) ((mouseY - y) / (rowHeight + ROW_GAP));
        int rowY = y + row * (rowHeight + ROW_GAP);
        if (row < 0 || row >= maxRows || mouseY >= rowY + rowHeight) return -1;
        int index = clampScrollStart(start, size, maxRows) + row;
        return index >= 0 && index < size ? index : -1;
    }

    private void sendDeviceRename() {
        FccGuiSnapshot snapshot = snapshotOrEmpty();
        FccGuiDeviceEntry device = selectedDevice(snapshot);
        if (device == null || deviceNameBox == null) return;
        notice = "Device rename sent";
        PacketDistributor.sendToServer(new FccWorkbenchActionPacket(menu.containerId, FccWorkbenchActionPacket.Action.RENAME_DEVICE, device.id(), null, deviceNameBox.getValue()));
    }

    private void sendCycleDeviceCategory() {
        FccGuiDeviceEntry device = selectedDevice(snapshotOrEmpty());
        if (device == null) return;
        notice = "Device category sent";
        PacketDistributor.sendToServer(new FccWorkbenchActionPacket(menu.containerId, FccWorkbenchActionPacket.Action.CYCLE_DEVICE_CATEGORY, device.id(), null, ""));
    }

    private void sendCreateGroup() {
        if (groupNameBox == null) return;
        notice = "Group create sent";
        PacketDistributor.sendToServer(new FccWorkbenchActionPacket(menu.containerId, FccWorkbenchActionPacket.Action.CREATE_GROUP, null, null, groupNameBox.getValue()));
    }

    private void sendGroupRename() {
        FccGuiGroupEntry group = selectedGroup(snapshotOrEmpty());
        if (group == null || groupRenameBox == null) return;
        notice = "Group rename sent";
        PacketDistributor.sendToServer(new FccWorkbenchActionPacket(menu.containerId, FccWorkbenchActionPacket.Action.RENAME_GROUP, group.id(), null, groupRenameBox.getValue()));
    }

    private void sendDeleteGroup() {
        FccGuiGroupEntry group = selectedGroup(snapshotOrEmpty());
        if (group == null || group.deviceCount() > 0) return;
        notice = "Group delete sent";
        PacketDistributor.sendToServer(new FccWorkbenchActionPacket(menu.containerId, FccWorkbenchActionPacket.Action.DELETE_GROUP, group.id(), null, ""));
    }

    private void sendAssignDeviceToGroup() {
        FccGuiSnapshot snapshot = snapshotOrEmpty();
        FccGuiDeviceEntry device = selectedDevice(snapshot);
        FccGuiGroupEntry group = selectedGroup(snapshot);
        if (device == null || group == null) return;
        notice = "Device added to group";
        PacketDistributor.sendToServer(new FccWorkbenchActionPacket(menu.containerId, FccWorkbenchActionPacket.Action.ASSIGN_DEVICE_TO_GROUP, device.id(), group.id(), ""));
    }

    private void sendUnassignDeviceFromGroup() {
        FccGuiSnapshot snapshot = snapshotOrEmpty();
        FccGuiDeviceEntry device = selectedDevice(snapshot);
        FccGuiGroupEntry group = selectedGroup(snapshot);
        if (device == null || group == null) return;
        notice = "Device removed from group";
        PacketDistributor.sendToServer(new FccWorkbenchActionPacket(menu.containerId, FccWorkbenchActionPacket.Action.UNASSIGN_DEVICE_FROM_GROUP, device.id(), group.id(), ""));
    }

    private void sendAssignDeviceToDiagramZone() {
        if (!canEditShipDiagram(snapshotOrEmpty())) {
            notice = "Airship Layout locked by active Ground Layout";
            return;
        }
        FccGuiDeviceEntry device = selectedDevice(snapshotOrEmpty());
        DiagramZone zone = selectedDiagramZone();
        if (device == null || zone == null) return;
        notice = trimLabel(device.label(), 14) + " -> " + trimLabel(zone.label, 14) + " + cockpit";
        PacketDistributor.sendToServer(new FccWorkbenchActionPacket(menu.containerId,
                FccWorkbenchActionPacket.Action.ASSIGN_DEVICE_TO_DIAGRAM_ZONE, device.id(), null, zone.key));
    }

    private void sendAssignPayloadLink(String role) {
        FccGuiSnapshot snapshot = snapshotOrEmpty();
        FccGuiDeviceEntry device = selectedDevice(snapshot);
        if (!isRedstoneLinkDevice(device)) {
            notice = "Select a LINK row first";
            return;
        }
        notice = "DROP".equals(role) ? "Bomb Drop link defined" : "Bomb Bay link defined";
        PacketDistributor.sendToServer(new FccWorkbenchActionPacket(menu.containerId,
                FccWorkbenchActionPacket.Action.ASSIGN_PAYLOAD_LINK, device.id(), null, role));
    }

    private void sendAssignDeviceToOtomobilZone() {
        FccGuiDeviceEntry device = selectedDevice(snapshotOrEmpty());
        OtomobilZone zone = selectedOtomobilZone();
        if (device == null || zone == null) return;
        notice = trimLabel(device.label(), 14) + " -> " + trimLabel(zone.label, 14) + " + vehicle";
        PacketDistributor.sendToServer(new FccWorkbenchActionPacket(menu.containerId,
                FccWorkbenchActionPacket.Action.ASSIGN_DEVICE_TO_DIAGRAM_ZONE, device.id(), null, zone.key));
    }

    private void sendDiagramAutoSync() {
        if (!canEditShipDiagram(snapshotOrEmpty()) && activeView == WorkView.DIAGRAM) {
            notice = "Airship Layout locked by active Ground Layout";
            return;
        }
        notice = "Cockpit refresh sent";
        PacketDistributor.sendToServer(new FccWorkbenchActionPacket(menu.containerId,
                FccWorkbenchActionPacket.Action.SYNC_DIAGRAM_COCKPIT, null, null, ""));
    }

    private void sendResetDiagram() {
        notice = "Diagram reset sent";
        PacketDistributor.sendToServer(new FccWorkbenchActionPacket(menu.containerId,
                FccWorkbenchActionPacket.Action.RESET_DIAGRAM, null, null, ""));
    }

    private void sendCreateControl() {
        if (controlNameBox == null) return;
        notice = "Control create sent";
        PacketDistributor.sendToServer(new FccWorkbenchActionPacket(menu.containerId, FccWorkbenchActionPacket.Action.CREATE_CONTROL, null, null, controlNameBox.getValue()));
    }

    private void sendControlRename() {
        FccGuiControlEntry control = selectedControl(snapshotOrEmpty());
        if (control == null || controlNameBox == null) return;
        notice = "Control rename sent";
        PacketDistributor.sendToServer(new FccWorkbenchActionPacket(menu.containerId, FccWorkbenchActionPacket.Action.RENAME_CONTROL, control.id(), null, controlNameBox.getValue()));
    }

    private void sendCycleControlWidget() {
        FccGuiControlEntry control = selectedControl(snapshotOrEmpty());
        if (control == null) return;
        notice = "Control type sent";
        PacketDistributor.sendToServer(new FccWorkbenchActionPacket(menu.containerId, FccWorkbenchActionPacket.Action.CYCLE_CONTROL_WIDGET, control.id(), null, ""));
    }

    private void sendDeleteControl() {
        FccGuiControlEntry control = selectedControl(snapshotOrEmpty());
        if (control == null) return;
        notice = "Control delete sent";
        PacketDistributor.sendToServer(new FccWorkbenchActionPacket(menu.containerId, FccWorkbenchActionPacket.Action.DELETE_CONTROL, control.id(), null, ""));
    }

    private void sendAddRouteToControl() {
        FccGuiSnapshot snapshot = snapshotOrEmpty();
        FccGuiControlEntry control = selectedControl(snapshot);
        FccGuiGroupEntry group = selectedGroup(snapshot);
        if (control == null || group == null) return;
        notice = "Route add sent";
        PacketDistributor.sendToServer(new FccWorkbenchActionPacket(menu.containerId, FccWorkbenchActionPacket.Action.ADD_ROUTE_TO_CONTROL, control.id(), group.id(), "RPM_DELTA"));
    }

    private void sendDeleteRoute() {
        FccGuiRouteEntry route = selectedRouteOrNull(snapshotOrEmpty());
        if (route == null) return;
        notice = "Route delete sent";
        PacketDistributor.sendToServer(new FccWorkbenchActionPacket(menu.containerId, FccWorkbenchActionPacket.Action.DELETE_ROUTE, route.id(), null, ""));
    }

    private void sendCycleRouteOutput() {
        FccGuiRouteEntry route = selectedRouteOrNull(snapshotOrEmpty());
        if (route == null) return;
        notice = "Route output sent";
        PacketDistributor.sendToServer(new FccWorkbenchActionPacket(menu.containerId, FccWorkbenchActionPacket.Action.CYCLE_ROUTE_OUTPUT, route.id(), null, ""));
    }

    private void sendToggleRouteInvert() {
        FccGuiRouteEntry route = selectedRouteOrNull(snapshotOrEmpty());
        if (route == null) return;
        notice = "Route direction sent";
        sendToggleRouteInvert(route.id());
    }

    private void sendToggleRouteInvert(UUID routeId) {
        PacketDistributor.sendToServer(new FccWorkbenchActionPacket(menu.containerId, FccWorkbenchActionPacket.Action.TOGGLE_ROUTE_INVERT, routeId, null, ""));
    }

    private void sendAdjustRouteScale(double delta) {
        FccGuiRouteEntry route = selectedRouteOrNull(snapshotOrEmpty());
        if (route == null) return;
        notice = delta > 0.0 ? "Route scale up" : "Route scale down";
        PacketDistributor.sendToServer(new FccWorkbenchActionPacket(menu.containerId, FccWorkbenchActionPacket.Action.ADJUST_ROUTE_SCALE, route.id(), null, String.format(java.util.Locale.ROOT, "%.2f", delta)));
    }

    private void sendApplyZeppelinTemplate() {
        notice = "Zeppelin template sent";
        PacketDistributor.sendToServer(new FccWorkbenchActionPacket(menu.containerId, FccWorkbenchActionPacket.Action.APPLY_ZEPPELIN_TEMPLATE, null, null, ""));
    }

    private void sendToggleRackRowMode(int row) {
        PacketDistributor.sendToServer(new FccWorkbenchActionPacket(menu.containerId,
                FccWorkbenchActionPacket.Action.TOGGLE_RACK_ROW_MODE, null, null,
                String.valueOf(row)));
    }

    private void sendCenterControlValue() {
        FccGuiControlEntry control = selectedControl(snapshotOrEmpty());
        if (control == null) return;
        notice = "Control centered";
        PacketDistributor.sendToServer(new FccWorkbenchActionPacket(menu.containerId, FccWorkbenchActionPacket.Action.CENTER_CONTROL_VALUE, control.id(), null, ""));
    }

    private void sendCenterShipControls() {
        ShipControls shipControls = resolveShipControls(snapshotOrEmpty());
        FccGuiControlEntry yaw = shipControls.yaw();
        FccGuiControlEntry trim = shipControls.trim();
        sendControlValue(yaw, yaw == null ? 0.0 : yaw.neutralValue(), false);
        sendControlValue(trim, trim == null ? 0.0 : trim.neutralValue(), false);
        notice = "Ship wheels centered";
    }

    private void sendCenterLinkedControllerSpringControls() {
        ShipControls shipControls = resolveShipControls(snapshotOrEmpty());
        FccGuiControlEntry yaw = shipControls.yaw();
        FccGuiControlEntry thrust = shipControls.thrust();
        sendControlValue(yaw, yaw == null ? 0.0 : yaw.neutralValue(), false);
        sendControlValue(thrust, thrust == null ? 0.0 : thrust.neutralValue(), false);
        notice = "Linked springs centered";
    }

    private void sendTogglePinnedHud(boolean currentlyPinned) {
        FccHudSubscriptionPacket packet = new FccHudSubscriptionPacket(menu.containerId, menu.getController().getBlockPos().asLong(), !currentlyPinned);
        PacketDistributor.sendToServer(packet);
        FccClientState.pinHud(!currentlyPinned ? menu.getController().getBlockPos() : null);
        notice = currentlyPinned ? "HUD unpinned" : "HUD pinned";
        rebuildViewWidgets();
    }

    private void sendZeroShipLift() {
        FccGuiControlEntry thrust = resolveShipControls(snapshotOrEmpty()).thrust();
        if (thrust == null) return;
        sendControlValue(thrust, thrust.neutralValue(), false);
        notice = "Ship thrust zeroed";
    }

    private void sendToggleShipSwitch(@Nullable FccGuiControlEntry control) {
        if (control == null) return;
        double next = controlFraction(control) >= 0.5 ? control.inputMin() : control.inputMax();
        sendControlValue(control, next, true);
        notice = trimLabel(control.label(), 18) + " toggled";
    }

    private void sendToggleControl(@Nullable FccGuiControlEntry control) {
        if (control == null) {
            notice = "Refresh Cockpit to build Bay Open";
            return;
        }
        sendToggleShipSwitch(control);
    }

    private void sendSetExclusiveVehicleMode(@Nullable FccGuiControlEntry active, @Nullable FccGuiControlEntry... others) {
        if (active == null) {
            notice = "Refresh controls to build vehicle mode switches";
            return;
        }
        sendControlValue(active, active.inputMax(), true);
        if (others != null) {
            for (FccGuiControlEntry other : others) {
                if (other == null || other.id().equals(active.id())) continue;
                sendControlValue(other, other.inputMin(), true);
            }
        }
        notice = trimLabel(active.label(), 22) + " selected";
    }

    private void sendCycleAutoDriveMode() {
        FccGuiSnapshot snapshot = snapshotOrEmpty();
        AutoDriveMode next = nextAutoDriveMode(snapshot);
        sendSetAutoDriveMode(snapshot, next);
    }

    private void sendSetAutoDriveMode(FccGuiSnapshot snapshot, AutoDriveMode mode) {
        if (!autoDriveModeAvailable(snapshot, mode)) {
            notice = "No target source for " + mode.label;
            return;
        }
        FccGuiControlEntry fallback = findControl(snapshot, "Auto Drive Switch");
        FccGuiControlEntry pathfinder = findControl(snapshot, "Pathfinder Auto Drive Switch");
        FccGuiControlEntry navTable = findControl(snapshot, "Nav Table Auto Drive Switch");
        sendAutoModeSwitchValue(fallback, mode == AutoDriveMode.FALLBACK);
        sendAutoModeSwitchValue(pathfinder, mode == AutoDriveMode.PATHFINDER);
        sendAutoModeSwitchValue(navTable, mode == AutoDriveMode.NAV_TABLE);
        notice = "Auto mode: " + mode.label;
        rebuildViewWidgets();
    }

    private void sendCycleEvadeRadius() {
        FccGuiControlEntry control = findEvadeRadiusControl(snapshotOrEmpty());
        if (control == null) {
            notice = "Refresh controls to build evade radius";
            return;
        }
        double current = control.value();
        double next = AUTO_EVADE_RADIUS_STEPS[0];
        for (double step : AUTO_EVADE_RADIUS_STEPS) {
            if (step > current + 0.5) {
                next = step;
                break;
            }
        }
        if (current >= AUTO_EVADE_RADIUS_STEPS[AUTO_EVADE_RADIUS_STEPS.length - 1] - 0.5) {
            next = AUTO_EVADE_RADIUS_STEPS[0];
        }
        sendControlValue(control, next, true);
        notice = String.format(java.util.Locale.ROOT, "Evade radius: %.0f blocks", next);
        rebuildViewWidgets();
    }

    private void sendCycleAutoApproachDistance() {
        sendCycleAutoAssistControl(findAutoApproachControl(snapshotOrEmpty()), AUTO_APPROACH_DISTANCE_STEPS, "Approach", "blocks");
    }

    private void sendCycleAutoAnnounceInterval() {
        sendCycleAutoAssistControl(findAutoAnnounceControl(snapshotOrEmpty()), AUTO_ANNOUNCE_INTERVAL_STEPS, "Announce every", "blocks");
    }

    private void sendCycleAutoReverseTime() {
        sendCycleAutoAssistControl(findAutoReverseControl(snapshotOrEmpty()), AUTO_REVERSE_TIME_STEPS, "Reverse time", "seconds");
    }

    private void sendCycleAutoAssistControl(@Nullable FccGuiControlEntry control, double[] steps, String label, String unit) {
        if (control == null || steps == null || steps.length == 0) {
            notice = "Refresh controls to build " + label;
            return;
        }
        double current = control.value();
        double next = steps[0];
        for (double step : steps) {
            if (step > current + 0.1) {
                next = step;
                break;
            }
        }
        if (current >= steps[steps.length - 1] - 0.1) {
            next = steps[0];
        }
        sendControlValue(control, next, true);
        notice = String.format(java.util.Locale.ROOT, "%s: %.0f %s", label, next, unit);
        rebuildViewWidgets();
    }

    private void sendTogglePathfinderSystems() {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, FlightControlComputerMenu.BUTTON_TOGGLE_PATHFINDER_SYSTEMS);
        }
    }

    private void sendAutoModeSwitchValue(@Nullable FccGuiControlEntry control, boolean enabled) {
        if (control == null) return;
        double value = enabled ? control.inputMax() : control.inputMin();
        sendSetControlValue(control.id(), value, true);
    }

    private void sendMomentaryControl(@Nullable FccGuiControlEntry control) {
        if (control == null) {
            notice = "Refresh Cockpit to build Bomb Drop";
            return;
        }
        heldMomentaryControlId = control.id();
        sendControlValue(control, control.inputMax(), true);
        notice = trimLabel(control.label(), 18) + " active";
    }

    private void sendControlValue(@Nullable FccGuiControlEntry control, double value, boolean exact) {
        if (control == null) return;
        double clamped = Math.max(control.inputMin(), Math.min(control.inputMax(), value));
        sendSetControlValue(control.id(), clamped, exact);
    }

    private void setCardScrollStart(int scrollStart) {
        int before = menu.getCardScrollStart();
        menu.setCardScrollStart(scrollStart);
        int after = menu.getCardScrollStart();
        if (before == after) return;
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, FlightControlComputerMenu.SCROLL_BUTTON_BASE + after);
        }
    }

    private void updateShipDrag(double mouseX, double mouseY, boolean exact) {
        if (shipDragControl == null) return;
        int x = leftPos + MAIN_X + 18;
        int y = topPos + MAIN_Y + 38;
        double raw = shipDragControl.value();
        if (shipDrag == ShipDrag.YAW) {
            double dx = Math.max(-46.0, Math.min(46.0, mouseX - (x + 65)));
            double fraction = (dx / 46.0 + 1.0) * 0.5;
            raw = shipDragControl.inputMin() + fraction * (shipDragControl.inputMax() - shipDragControl.inputMin());
        } else if (shipDrag == ShipDrag.LIFT) {
            double throttleY = y + 30;
            double fraction = 1.0 - Math.max(0.0, Math.min((double) SHIP_THROTTLE_H, mouseY - throttleY)) / SHIP_THROTTLE_H;
            raw = shipDragControl.inputMin() + fraction * (shipDragControl.inputMax() - shipDragControl.inputMin());
        } else if (shipDrag == ShipDrag.TRIM) {
            double dx = Math.max(-40.0, Math.min(40.0, mouseX - (x + 234)));
            double fraction = (dx / 40.0 + 1.0) * 0.5;
            raw = shipDragControl.inputMin() + fraction * (shipDragControl.inputMax() - shipDragControl.inputMin());
        }
        sendControlValue(shipDragControl, raw, exact);
    }

    private void sendSetControlValue(UUID controlId, double value, boolean exact) {
        if (exact) notice = "Slider value set";
        PacketDistributor.sendToServer(new FccWorkbenchActionPacket(menu.containerId, FccWorkbenchActionPacket.Action.SET_CONTROL_VALUE, controlId, null, String.format(java.util.Locale.ROOT, "%.4f", value)));
    }

    private void sendSetLinkedControllerInterval(int ticks) {
        int clamped = Math.max(1, Math.min(60, ticks));
        notice = "Linked interval " + clamped + " ticks";
        PacketDistributor.sendToServer(new FccWorkbenchActionPacket(menu.containerId,
                FccWorkbenchActionPacket.Action.SET_LINKED_CONTROLLER_INTERVAL, null, null, String.valueOf(clamped)));
    }

    private void sendSetNavTableSpeed(float speed) {
        PacketDistributor.sendToServer(new FccWorkbenchActionPacket(menu.containerId,
                FccWorkbenchActionPacket.Action.SET_NAV_TABLE_SPEED, null, null,
                String.format(java.util.Locale.ROOT, "%.2f", speed)));
    }

    private void sendSetPathfinderLookaheadScale(float scale) {
        PacketDistributor.sendToServer(new FccWorkbenchActionPacket(menu.containerId,
                FccWorkbenchActionPacket.Action.SET_PATHFINDER_LOOKAHEAD_SCALE, null, null,
                String.format(java.util.Locale.ROOT, "%.3f", scale)));
    }

    private void sendWorkbenchArmToggle() {
        FccGuiSnapshot snapshot = snapshotOrEmpty();
        FlightMode next = snapshot.mode() == FlightMode.OFF ? FlightMode.STANDBY : FlightMode.OFF;
        notice = next == FlightMode.OFF ? "Disarm sent" : "Arm sent";
        PacketDistributor.sendToServer(new FccModeChangePacket(menu.containerId, next, snapshot.noseAxis(), snapshot.targetAlt(), snapshot.climbRate(), snapshot.descendFloor()));
    }

    @Nullable
    private FccGuiDeviceEntry selectedDevice(FccGuiSnapshot snapshot) {
        if (snapshot.deviceEntries().isEmpty()) return null;
        return snapshot.deviceEntries().get(Math.min(selectedDeviceIndex, snapshot.deviceEntries().size() - 1));
    }

    private DiagramZone selectedDiagramZone() {
        DiagramZone[] zones = DiagramZone.values();
        if (zones.length == 0) return null;
        return zones[Math.max(0, Math.min(selectedDiagramZoneIndex, zones.length - 1))];
    }

    private OtomobilZone selectedOtomobilZone() {
        OtomobilZone[] zones = OtomobilZone.values();
        if (zones.length == 0) return null;
        return zones[Math.max(0, Math.min(selectedOtomobilZoneIndex, zones.length - 1))];
    }

    @Nullable
    private FccGuiGroupEntry diagramZoneGroup(FccGuiSnapshot snapshot, DiagramZone zone) {
        int index = diagramZoneGroupIndex(snapshot, zone);
        return index < 0 ? null : snapshot.groupEntries().get(index);
    }

    private int diagramZoneGroupIndex(FccGuiSnapshot snapshot, DiagramZone zone) {
        if (zone == null) return -1;
        List<FccGuiGroupEntry> groups = snapshot.groupEntries();
        for (int i = 0; i < groups.size(); i++) {
            FccGuiGroupEntry group = groups.get(i);
            if (!group.zoneKey().isEmpty() && zone.key.equalsIgnoreCase(group.zoneKey())) return i;
            if (zone.matches(group.label())) return i;
        }
        return -1;
    }

    private int diagramZoneDeviceCount(FccGuiSnapshot snapshot, DiagramZone zone) {
        FccGuiGroupEntry group = diagramZoneGroup(snapshot, zone);
        return group == null ? 0 : group.deviceCount();
    }

    @Nullable
    private FccGuiGroupEntry otomobilZoneGroup(FccGuiSnapshot snapshot, OtomobilZone zone) {
        int index = otomobilZoneGroupIndex(snapshot, zone);
        return index < 0 ? null : snapshot.groupEntries().get(index);
    }

    private int otomobilZoneGroupIndex(FccGuiSnapshot snapshot, OtomobilZone zone) {
        if (zone == null) return -1;
        List<FccGuiGroupEntry> groups = snapshot.groupEntries();
        for (int i = 0; i < groups.size(); i++) {
            FccGuiGroupEntry group = groups.get(i);
            if (!group.zoneKey().isEmpty() && zone.key.equalsIgnoreCase(group.zoneKey())) return i;
            if (zone.matches(group.label())) return i;
        }
        return -1;
    }

    private int otomobilZoneDeviceCount(FccGuiSnapshot snapshot, OtomobilZone zone) {
        FccGuiGroupEntry group = otomobilZoneGroup(snapshot, zone);
        return group == null ? 0 : group.deviceCount();
    }

    private List<FccGuiRouteEntry> otomobilZoneThrottleRoutes(FccGuiSnapshot snapshot, OtomobilZone zone) {
        FccGuiGroupEntry group = otomobilZoneGroup(snapshot, zone);
        if (group == null) return List.of();
        FccGuiControlEntry thrust = resolveShipControls(snapshot).thrust();
        List<FccGuiRouteEntry> preferred = new ArrayList<>();
        List<FccGuiRouteEntry> fallback = new ArrayList<>();
        for (FccGuiRouteEntry route : snapshot.routeEntries()) {
            if (!route.targetGroupId().equals(group.id())) continue;
            if (!"RPM_SCALE".equals(route.outputType())) continue;
            if (thrust != null && route.controlId().equals(thrust.id())) {
                preferred.add(route);
            } else {
                fallback.add(route);
            }
        }
        if (!preferred.isEmpty()) return preferred;
        return fallback;
    }

    @Nullable
    private FccGuiRouteEntry otomobilZoneThrottleRoute(FccGuiSnapshot snapshot, OtomobilZone zone) {
        List<FccGuiRouteEntry> routes = otomobilZoneThrottleRoutes(snapshot, zone);
        return routes.isEmpty() ? null : routes.get(0);
    }

    private VehiclePreview vehiclePreview(FccGuiSnapshot snapshot) {
        ShipControls shipControls = resolveShipControls(snapshot);
        FccTelemetryPacket telemetry = FccClientState.telemetryFor(menu.containerId);
        int baseRpm = clampVehicleRpm((int) Math.round(controlSignedFraction(shipControls.thrust()) * VEHICLE_MAX_WHEEL_RPM));
        double yawSigned = controlSignedFraction(shipControls.yaw());
        if (controlFraction(findControl(snapshot, "Steering Invert Switch")) >= 0.5) {
            yawSigned = -yawSigned;
        }
        boolean driveFrontOnly = controlFraction(findControl(snapshot, "Drive Mode 2x4 Front Switch")) >= 0.5
                && controlFraction(findControl(snapshot, "Drive Mode 2x4 Rear Switch")) < 0.5;
        boolean driveRearOnly = controlFraction(findControl(snapshot, "Drive Mode 2x4 Rear Switch")) >= 0.5
                && controlFraction(findControl(snapshot, "Drive Mode 2x4 Front Switch")) < 0.5;
        boolean midDriveEnabled = controlFraction(findControl(snapshot, "Mid Axle Drive Switch")) >= 0.5;

        VehicleModeRuntime runtime = resolveVehicleModeRuntime(snapshot);
        VehicleSteerModePreview steerMode = runtime.mode();

        boolean driveFront = !driveRearOnly;
        boolean driveRear = !driveFrontOnly;
        boolean driveMid = midDriveEnabled && (driveFront || driveRear);

        List<VehicleWheelPreview> wheels = new ArrayList<>();
        wheels.add(buildVehicleWheelPreview(snapshot, OtomobilZone.FRONT_LEFT_WHEEL, driveFront, baseRpm, steerSignalForWheel(OtomobilZone.FRONT_LEFT_WHEEL, steerMode, yawSigned), telemetry));
        wheels.add(buildVehicleWheelPreview(snapshot, OtomobilZone.FRONT_RIGHT_WHEEL, driveFront, baseRpm, steerSignalForWheel(OtomobilZone.FRONT_RIGHT_WHEEL, steerMode, yawSigned), telemetry));
        wheels.add(buildVehicleWheelPreview(snapshot, OtomobilZone.MID_LEFT_WHEEL, driveMid, baseRpm, steerSignalForWheel(OtomobilZone.MID_LEFT_WHEEL, steerMode, yawSigned), telemetry));
        wheels.add(buildVehicleWheelPreview(snapshot, OtomobilZone.MID_RIGHT_WHEEL, driveMid, baseRpm, steerSignalForWheel(OtomobilZone.MID_RIGHT_WHEEL, steerMode, yawSigned), telemetry));
        wheels.add(buildVehicleWheelPreview(snapshot, OtomobilZone.REAR_LEFT_WHEEL, driveRear, baseRpm, steerSignalForWheel(OtomobilZone.REAR_LEFT_WHEEL, steerMode, yawSigned), telemetry));
        wheels.add(buildVehicleWheelPreview(snapshot, OtomobilZone.REAR_RIGHT_WHEEL, driveRear, baseRpm, steerSignalForWheel(OtomobilZone.REAR_RIGHT_WHEEL, steerMode, yawSigned), telemetry));
        return new VehiclePreview(List.copyOf(wheels), runtime.label(), runtime.fromRuntime());
    }

    private VehicleWheelPreview buildVehicleWheelPreview(FccGuiSnapshot snapshot, OtomobilZone zone, boolean driven,
                                                         int baseRpm, int steerSignal, @Nullable FccTelemetryPacket telemetry) {
        FccGuiRouteEntry route = otomobilZoneThrottleRoute(snapshot, zone);
        int rpm = driven ? clampVehicleRpm(baseRpm) : 0;
        boolean invert = route == null || route.invert();
        if (invert) rpm = -rpm;
        int telemetryIndex = vehicleTelemetryIndex(zone);
        if (telemetry != null) {
            rpm = intAt(telemetry.vehicleWheelRpm(), telemetryIndex, rpm);
            steerSignal = intAt(telemetry.vehicleWheelSteer(), telemetryIndex, steerSignal);
        }
        return new VehicleWheelPreview(zone, rpm, invert, otomobilZoneDeviceCount(snapshot, zone), steerSignal);
    }

    private static int vehicleTelemetryIndex(OtomobilZone zone) {
        return switch (zone) {
            case FRONT_LEFT_WHEEL -> 0;
            case FRONT_RIGHT_WHEEL -> 1;
            case MID_LEFT_WHEEL -> 2;
            case MID_RIGHT_WHEEL -> 3;
            case REAR_LEFT_WHEEL -> 4;
            case REAR_RIGHT_WHEEL -> 5;
        };
    }

    private record VehicleModeRuntime(VehicleSteerModePreview mode, String label, boolean fromRuntime) {}

    private VehicleModeRuntime resolveVehicleModeRuntime(FccGuiSnapshot snapshot) {
        // [9]
        FccTelemetryPacket telemetry = FccClientState.telemetryFor(menu.containerId);
        if (telemetry != null && snapshot.mode() != FlightMode.OFF) {
            int ord = telemetry.vehicleSteerModeOrdinal();
            if (ord >= 0) {
                VehicleSteerModePreview mode = switch (ord) {
                    case 1 -> VehicleSteerModePreview.ALL_WHEEL;
                    case 2 -> VehicleSteerModePreview.CRAB;
                    default -> VehicleSteerModePreview.FRONT;
                };
                return new VehicleModeRuntime(mode, steerModeLabel(mode), true);
            }
        }

        boolean front = controlFraction(findControl(snapshot, "Steer Mode Front Switch")) >= 0.5;
        boolean all = controlFraction(findControl(snapshot, "Steer Mode All-Wheel Switch")) >= 0.5;
        boolean crab = controlFraction(findControl(snapshot, "Steer Mode Crab Switch")) >= 0.5;
        if (crab) return new VehicleModeRuntime(VehicleSteerModePreview.CRAB, "CRAB", false);
        if (all) return new VehicleModeRuntime(VehicleSteerModePreview.ALL_WHEEL, "4WS", false);
        if (front) return new VehicleModeRuntime(VehicleSteerModePreview.FRONT, "FRONT", false);
        return new VehicleModeRuntime(VehicleSteerModePreview.FRONT, "FRONT", false);
    }

    private static String steerModeLabel(VehicleSteerModePreview mode) {
        return switch (mode) {
            case CRAB -> "CRAB";
            case ALL_WHEEL -> "4WS";
            case FRONT -> "FRONT";
        };
    }

    private int steerSignalForWheel(OtomobilZone zone, VehicleSteerModePreview mode, double yawSigned) {
        double steerSigned = switch (mode) {
            case CRAB -> yawSigned * 0.80;
            case ALL_WHEEL -> switch (zone) {
                case REAR_LEFT_WHEEL, REAR_RIGHT_WHEEL -> -yawSigned * 0.70;
                case MID_LEFT_WHEEL, MID_RIGHT_WHEEL -> 0.0;
                default -> yawSigned * 0.85;
            };
            case FRONT -> switch (zone) {
                case REAR_LEFT_WHEEL, REAR_RIGHT_WHEEL, MID_LEFT_WHEEL, MID_RIGHT_WHEEL -> 0.0;
                default -> yawSigned;
            };
        };
        int strength = Math.max(0, Math.min(15, (int) Math.round(Math.abs(steerSigned) * 15.0)));
        if (strength == 0) return 0;
        // Both wheels on same axle use same signal direction (server-side logic matches).
        // Positive return = R> (turn right), Negative = L< (turn left).
        return steerSigned > 0.0 ? strength : -strength;
    }

    private static int clampVehicleRpm(int rpm) {
        return Math.max(-VEHICLE_MAX_WHEEL_RPM, Math.min(VEHICLE_MAX_WHEEL_RPM, rpm));
    }

    private static String linkedControllerActionLabel(boolean vehicleEnabled, int button) {
        if (!vehicleEnabled) return LINKED_CONTROLLER_ACTIONS[button];
        return switch (button) {
            case 0 -> "Throttle Forward";
            case 1 -> "Throttle Reverse";
            case 2 -> "Steer Left";
            case 3 -> "Steer Right";
            case 4 -> "Mid Axle Toggle";
            case 5 -> "Thrust Lock";
            case 6 -> "Bay Toggle";
            case 7 -> "Drop Hold";
            default -> LINKED_CONTROLLER_ACTIONS[button];
        };
    }

    private boolean isShipDiagramPopulated(FccGuiSnapshot snapshot) {
        for (FccGuiGroupEntry group : snapshot.groupEntries()) {
            for (DiagramZone zone : DiagramZone.values()) {
                if (group.deviceCount() <= 0) continue;
                if (!group.zoneKey().isEmpty() && zone.key.equalsIgnoreCase(group.zoneKey())) return true;
                if (zone.matches(group.label())) return true;
            }
        }
        return false;
    }

    private OperateProfileState resolveOperateProfile(FccGuiSnapshot snapshot) {
        boolean airshipPopulated = isShipDiagramPopulated(snapshot);
        boolean groundPopulated = isOtomobilDiagramPopulated(snapshot);
        int airshipDevices = airshipDiagramDeviceCount(snapshot);
        int groundDevices = groundDiagramDeviceCount(snapshot);
        boolean hybrid = airshipPopulated && groundPopulated;
        if (manualOperateProfile != null) {
            return new OperateProfileState(manualOperateProfile, true, hybrid, airshipDevices, groundDevices,
                "Manual helm override is active");
        }
        if (hybrid) {
            return new OperateProfileState(null, false, true, airshipDevices, groundDevices,
                "Hybrid layout detected: both airship and ground zones have assignments");
        }
        if (groundPopulated) {
            return new OperateProfileState(OperateProfile.GROUND, false, false, airshipDevices, groundDevices,
                "Ground Vehicle selected from populated Otomobil wheel zones");
        }
        if (airshipPopulated) {
            return new OperateProfileState(OperateProfile.AIRSHIP, false, false, airshipDevices, groundDevices,
                "Airship zones populated");
        }
        if (snapshot.diagramProfile() == FccDiagramProfile.BALLOON_ZEPPELIN) {
            return new OperateProfileState(OperateProfile.AIRSHIP, false, false, airshipDevices, groundDevices,
                "Balloon Zeppelin profile selected");
        }
        return new OperateProfileState(OperateProfile.AIRSHIP, false, false, airshipDevices, groundDevices,
            "Default airship helm");
    }

    private int airshipDiagramDeviceCount(FccGuiSnapshot snapshot) {
        int count = 0;
        for (DiagramZone zone : DiagramZone.values()) {
            count += diagramZoneDeviceCount(snapshot, zone);
        }
        return count;
    }

    private int groundDiagramDeviceCount(FccGuiSnapshot snapshot) {
        int count = 0;
        for (OtomobilZone zone : OtomobilZone.values()) {
            count += otomobilZoneDeviceCount(snapshot, zone);
        }
        return count;
    }

    private boolean isOtomobilDiagramPopulated(FccGuiSnapshot snapshot) {
        for (FccGuiGroupEntry group : snapshot.groupEntries()) {
            for (OtomobilZone zone : OtomobilZone.values()) {
                if (group.deviceCount() <= 0) continue;
                if (!group.zoneKey().isEmpty() && zone.key.equalsIgnoreCase(group.zoneKey())) return true;
                if (zone.matches(group.label())) return true;
            }
        }
        return false;
    }

    private boolean canEditShipDiagram(FccGuiSnapshot snapshot) {
        if (isShipDiagramPopulated(snapshot) && isOtomobilDiagramPopulated(snapshot)) return false;
        return !isOtomobilDiagramPopulated(snapshot);
    }

    private boolean canEditOtomobilDiagram(FccGuiSnapshot snapshot) {
        if (isShipDiagramPopulated(snapshot) && isOtomobilDiagramPopulated(snapshot)) return false;
        return !isShipDiagramPopulated(snapshot);
    }

    private boolean canUseVehicleControls(FccGuiSnapshot snapshot) {
        return isOtomobilDiagramPopulated(snapshot) && !isShipDiagramPopulated(snapshot);
    }

    private String diagramZoneDeviceNumberLabel(FccGuiSnapshot snapshot, DiagramZone zone) {
        StringBuilder builder = new StringBuilder();
        int shown = 0;
        int total = 0;
        for (FccGuiDeviceEntry device : snapshot.deviceEntries()) {
            if (device.assignmentLabel() == null || !zone.matches(device.assignmentLabel())) continue;
            total++;
            if (shown >= 3) continue;
            if (builder.length() > 0) builder.append(',');
            builder.append(device.displayNumber());
            shown++;
        }
        if (total > shown) builder.append('+');
        return builder.toString();
    }

    private int countDiagramSwitchControls(FccGuiSnapshot snapshot) {
        int count = 0;
        for (DiagramZone zone : DiagramZone.values()) {
            if (findDiagramSwitchControl(snapshot, zone) != null) count++;
        }
        return count;
    }

    @Nullable
    private FccGuiControlEntry findDiagramSwitchControl(FccGuiSnapshot snapshot, DiagramZone zone) {
        String exact = normalizeDiagramToken(zone.switchLabel());
        String switchFirst = normalizeDiagramToken("Switch " + zone.label);
        for (FccGuiControlEntry control : snapshot.controlEntries()) {
            String token = normalizeDiagramToken(control.label());
            if (token.equals(exact) || token.equals(switchFirst)) return control;
        }
        for (FccGuiControlEntry control : snapshot.controlEntries()) {
            String token = normalizeDiagramToken(control.label());
            if (token.contains("switch") && zone.matches(control.label())) return control;
        }
        return null;
    }

    @Nullable
    private FccGuiControlEntry findFlipPreventionControl(FccGuiSnapshot snapshot) {
        for (FccGuiControlEntry control : snapshot.controlEntries()) {
            String token = normalizeDiagramToken(control.label());
            if (token.contains("flip") && (token.contains("prevent") || token.contains("guard"))) return control;
        }
        return null;
    }

    @Nullable
    private FccGuiControlEntry findBayOpenControl(FccGuiSnapshot snapshot) {
        for (FccGuiControlEntry control : snapshot.controlEntries()) {
            String token = normalizeDiagramToken(control.label());
            if (token.contains("bombbay") || (token.contains("bay") && (token.contains("open") || token.contains("door")))) return control;
        }
        return null;
    }

    @Nullable
    private FccGuiControlEntry findBombSafetyControl(FccGuiSnapshot snapshot) {
        for (FccGuiControlEntry control : snapshot.controlEntries()) {
            String token = normalizeDiagramToken(control.label());
            if (token.contains("bomb") && (token.contains("safety") || token.contains("arm"))) return control;
            if (token.contains("drop") && token.contains("safety")) return control;
        }
        return null;
    }

    @Nullable
    private FccGuiControlEntry findBombDropControl(FccGuiSnapshot snapshot) {
        for (FccGuiControlEntry control : snapshot.controlEntries()) {
            String token = normalizeDiagramToken(control.label());
            if (token.contains("bomb") && (token.contains("drop") || token.contains("release"))) return control;
        }
        return null;
    }

    private boolean hasRoutesForControl(FccGuiSnapshot snapshot, @Nullable FccGuiControlEntry control) {
        if (control == null) return false;
        for (FccGuiRouteEntry route : snapshot.routeEntries()) {
            if (route.controlId().equals(control.id()) && groupDeviceCount(snapshot, route.targetGroupId()) > 0) return true;
        }
        return false;
    }

    private int groupDeviceCount(FccGuiSnapshot snapshot, UUID groupId) {
        for (FccGuiGroupEntry group : snapshot.groupEntries()) {
            if (group.id().equals(groupId)) return group.deviceCount();
        }
        return 0;
    }

    private boolean isRedstoneLinkDevice(@Nullable FccGuiDeviceEntry device) {
        return device != null && normalizeDiagramToken(device.categoryLabel()).contains("link");
    }

    @Nullable
    private FccGuiGroupEntry payloadGroup(FccGuiSnapshot snapshot, boolean bay) {
        for (FccGuiGroupEntry group : snapshot.groupEntries()) {
            if (isPayloadGroup(group, bay)) return group;
        }
        return null;
    }

    private boolean payloadGroupDefined(FccGuiSnapshot snapshot, boolean bay) {
        return payloadGroupDefined(payloadGroup(snapshot, bay));
    }

    private boolean payloadGroupDefined(@Nullable FccGuiGroupEntry group) {
        return group != null && group.deviceCount() > 0;
    }

    private String payloadGroupState(@Nullable FccGuiGroupEntry group) {
        return payloadGroupDefined(group) ? group.deviceCount() + " link" : "not defined";
    }

    private boolean isPayloadGroup(FccGuiGroupEntry group, boolean bay) {
        String token = normalizeDiagramToken(group.label() + " " + group.zoneKey());
        return bay
            ? token.contains("payloadbay") || token.contains("bombbay") || (token.contains("bay") && token.contains("door"))
            : token.contains("payloaddrop") || (token.contains("bomb") && token.contains("drop"));
    }

    @Nullable
    private FccGuiGroupEntry selectedGroup(FccGuiSnapshot snapshot) {
        if (snapshot.groupEntries().isEmpty()) return null;
        return snapshot.groupEntries().get(Math.min(selectedGroupIndex, snapshot.groupEntries().size() - 1));
    }

    @Nullable
    private FccGuiControlEntry selectedControl(FccGuiSnapshot snapshot) {
        if (snapshot.controlEntries().isEmpty()) return null;
        return snapshot.controlEntries().get(Math.min(selectedControlIndex, snapshot.controlEntries().size() - 1));
    }

    @Nullable
    private FccGuiRouteEntry selectedRouteOrNull(FccGuiSnapshot snapshot) {
        List<FccGuiRouteEntry> routes = selectedRoutes(snapshot);
        if (routes.isEmpty()) return null;
        return routes.get(Math.min(selectedRouteIndex, routes.size() - 1));
    }

    private List<FccGuiRouteEntry> selectedRoutes(FccGuiSnapshot snapshot) {
        FccGuiControlEntry control = selectedControl(snapshot);
        if (control == null) return List.of();
        List<FccGuiRouteEntry> routes = new ArrayList<>();
        for (FccGuiRouteEntry route : snapshot.routeEntries()) {
            if (control.id().equals(route.controlId())) routes.add(route);
        }
        return routes;
    }

    private void selectControlAndFirstRoute(FccGuiSnapshot snapshot, FccGuiControlEntry control) {
        for (int i = 0; i < snapshot.controlEntries().size(); i++) {
            if (!snapshot.controlEntries().get(i).id().equals(control.id())) continue;
            selectedControlIndex = i;
            selectedRouteIndex = 0;
            controlScrollStart = visibleStartForIndex(i, snapshot.controlEntries().size(), CONTROL_LIST_ROWS);
            controlRouteScrollStart = 0;
            break;
        }
        List<FccGuiRouteEntry> routes = selectedRoutes(snapshot);
        if (routes.isEmpty()) return;
        UUID groupId = routes.get(0).targetGroupId();
        for (int i = 0; i < snapshot.groupEntries().size(); i++) {
            if (!snapshot.groupEntries().get(i).id().equals(groupId)) continue;
            selectedGroupIndex = i;
            controlGroupScrollStart = visibleStartForIndex(i, snapshot.groupEntries().size(), CONTROL_GROUP_ROWS);
            break;
        }
    }

    private int routeTargetDeviceCount(FccGuiSnapshot snapshot, FccGuiRouteEntry route) {
        for (FccGuiGroupEntry group : snapshot.groupEntries()) {
            if (group.id().equals(route.targetGroupId())) return group.deviceCount();
        }
        return -1;
    }

    @Nullable
    private FccGuiGroupEntry groupById(FccGuiSnapshot snapshot, UUID id) {
        if (id == null) return null;
        for (FccGuiGroupEntry group : snapshot.groupEntries()) {
            if (group.id().equals(id)) return group;
        }
        return null;
    }

    @Nullable
    private FccGuiControlEntry controlById(FccGuiSnapshot snapshot, UUID id) {
        if (id == null) return null;
        for (FccGuiControlEntry control : snapshot.controlEntries()) {
            if (control.id().equals(id)) return control;
        }
        return null;
    }

    /** [10] */
    private float diagramZoneSignedActivity(FccGuiSnapshot snapshot, DiagramZone zone) {
        double sum = 0.0;
        for (FccGuiRouteEntry route : snapshot.routeEntries()) {
            FccGuiGroupEntry group = groupById(snapshot, route.targetGroupId());
            if (group == null) continue;
            boolean matches = (!group.zoneKey().isEmpty() && zone.key.equalsIgnoreCase(group.zoneKey()))
                || zone.matches(group.label());
            if (!matches) continue;
            FccGuiControlEntry control = controlById(snapshot, route.controlId());
            if (control == null) continue;
            double signed = controlSignedFraction(control);
            if (route.scale() < 0.0) signed = -signed;
            if (route.invert()) signed = -signed;
            sum += signed;
        }
        return (float) Math.max(-1.0, Math.min(1.0, sum));
    }

    /** [11] */
    private void renderZoneActivity(GuiGraphics graphics, int x, int y, int zoneW, int zoneH,
                                    float activity, boolean armed, float pulse) {
        if (Math.abs(activity) < 0.02f) return;
        int half = Math.max(2, zoneW / 2 - 4);
        int cx = x + zoneW / 2;
        int fill = Math.max(1, Math.round(Math.abs(activity) * half));
        int barColor = activity >= 0 ? OK_GREEN : WARN;
        int bar = withAlpha(barColor, armed ? 255 : 120);
        graphics.fill(cx, y + 2, cx + (activity >= 0 ? fill : -fill), y + 4, bar);
        graphics.fill(cx, y + 1, cx + 1, y + 5, withAlpha(TEXT, armed ? 255 : 120));
        if (armed) {
            int glowAlpha = Math.min(255, (int) (50 + 110 * Math.abs(activity) + pulse * 40));
            border(graphics, x - 1, y - 1, zoneW + 2, zoneH + 2, withAlpha(barColor, glowAlpha));
        }
    }

    /** Small LIVE / SAFE chip on the layout boards so it is obvious why zones do or do not glow. */
    private void renderBoardArmedChip(GuiGraphics graphics, int x, int y, boolean armed, float pulse) {
        int w = 40;
        graphics.fill(x, y, x + w, y + 12, 0xCC0D141C);
        border(graphics, x, y, w, 12, armed ? withAlpha(OK_GREEN, 140 + (int) (pulse * 80.0f)) : SOFT_BORDER);
        graphics.drawCenteredString(font, armed ? "LIVE" : "SAFE", x + w / 2, y + 2, armed ? OK_GREEN : DIM);
        addTooltipHotspot(x, y, w, 12,
            Component.literal(armed ? "Armed: zones show live activity" : "Disarmed")
                .withStyle(armed ? ChatFormatting.GREEN : ChatFormatting.GRAY),
            Component.literal(armed ? "Bars and glow track what the cockpit is commanding."
                    : "Deflected controls show a dim preview bar; arm the FCC to go live.")
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    @Nullable
    private FccGuiControlEntry findControl(FccGuiSnapshot snapshot, String... labels) {
        for (String label : labels) {
            for (FccGuiControlEntry control : snapshot.controlEntries()) {
                if (control.label().equalsIgnoreCase(label)) return control;
            }
        }
        for (String label : labels) {
            String needle = label.toLowerCase(java.util.Locale.ROOT);
            for (FccGuiControlEntry control : snapshot.controlEntries()) {
                if (control.label().toLowerCase(java.util.Locale.ROOT).contains(needle)) return control;
            }
        }
        return null;
    }

    private ShipControls resolveShipControls(FccGuiSnapshot snapshot) {
        FccGuiControlEntry yaw = findControl(snapshot, "Yaw Wheel", "Yaw");
        if (yaw == null) yaw = findDifferentialYawControl(snapshot);

        FccGuiControlEntry thrust = findControl(snapshot, "Thrust Lever", "Throttle", "Main Thrust", "Thrust");
        if (sameControl(thrust, yaw)) thrust = null;
        if (thrust == null) thrust = findThrustRouteControl(snapshot, yaw);

        FccGuiControlEntry trim = findControl(snapshot, "Levitation Dial", "Levitation", "Fin Trim", "Trim");
        if (sameControl(trim, yaw) || sameControl(trim, thrust)) trim = null;
        if (trim == null) trim = findTrimRouteControl(snapshot, yaw, thrust);

        return new ShipControls(thrust, yaw, trim);
    }

    @Nullable
    private FccGuiControlEntry findDifferentialYawControl(FccGuiSnapshot snapshot) {
        for (FccGuiControlEntry control : snapshot.controlEntries()) {
            boolean leftPositive = false;
            boolean leftNegative = false;
            boolean rightPositive = false;
            boolean rightNegative = false;
            for (FccGuiRouteEntry route : snapshot.routeEntries()) {
                if (!control.id().equals(route.controlId()) || !isRpmRoute(route)) continue;
                double scale = effectiveRouteScale(route);
                if (Math.abs(scale) < 1.0e-6) continue;
                if (looksLeftLabel(route.targetLabel())) {
                    leftPositive |= scale > 0.0;
                    leftNegative |= scale < 0.0;
                }
                if (looksRightLabel(route.targetLabel())) {
                    rightPositive |= scale > 0.0;
                    rightNegative |= scale < 0.0;
                }
            }
            if ((leftPositive && rightNegative) || (leftNegative && rightPositive)) return control;
        }
        return null;
    }

    @Nullable
    private FccGuiControlEntry findThrustRouteControl(FccGuiSnapshot snapshot, @Nullable FccGuiControlEntry yaw) {
        for (FccGuiControlEntry control : snapshot.controlEntries()) {
            if (sameControl(control, yaw)) continue;
            int positivePropRoutes = 0;
            boolean hasNegativePropRoute = false;
            for (FccGuiRouteEntry route : snapshot.routeEntries()) {
                if (!control.id().equals(route.controlId()) || !isRpmRoute(route)) continue;
                if (looksLiftLabel(route.targetLabel())) continue;
                if (!looksPropLabel(route.targetLabel())) continue;
                double scale = effectiveRouteScale(route);
                positivePropRoutes += scale > 0.0 ? 1 : 0;
                hasNegativePropRoute |= scale < 0.0;
            }
            if (positivePropRoutes > 0 && !hasNegativePropRoute) return control;
        }
        return null;
    }

    @Nullable
    private FccGuiControlEntry findTrimRouteControl(FccGuiSnapshot snapshot, @Nullable FccGuiControlEntry yaw,
                                                    @Nullable FccGuiControlEntry thrust) {
        for (FccGuiControlEntry control : snapshot.controlEntries()) {
            if (sameControl(control, yaw) || sameControl(control, thrust)) continue;
            for (FccGuiRouteEntry route : snapshot.routeEntries()) {
                if (!control.id().equals(route.controlId())) continue;
                if (looksLiftLabel(route.targetLabel()) || route.outputType().startsWith("ANGLE_")) return control;
            }
        }
        return null;
    }

    private static boolean sameControl(@Nullable FccGuiControlEntry first, @Nullable FccGuiControlEntry second) {
        return first != null && second != null && first.id().equals(second.id());
    }

    private static boolean isRpmRoute(FccGuiRouteEntry route) {
        return route.outputType().equals("RPM_DELTA") || route.outputType().equals("RPM_SCALE");
    }

    private static double effectiveRouteScale(FccGuiRouteEntry route) {
        return route.scale() * (route.invert() ? -1.0 : 1.0);
    }

    private static boolean routeTargetMatches(FccGuiRouteEntry route, String targetNeedle) {
        String needle = targetNeedle.toLowerCase(java.util.Locale.ROOT);
        if (needle.contains("left")) return looksLeftLabel(route.targetLabel());
        if (needle.contains("right")) return looksRightLabel(route.targetLabel());
        return route.targetLabel().toLowerCase(java.util.Locale.ROOT).contains(needle);
    }

    private static boolean looksLeftLabel(String label) {
        String lower = label.toLowerCase(java.util.Locale.ROOT);
        return lower.contains("left") || lower.contains("lft") || lower.contains("port");
    }

    private static boolean looksRightLabel(String label) {
        String lower = label.toLowerCase(java.util.Locale.ROOT);
        return lower.contains("right") || lower.contains("rght") || lower.contains("rgt") || lower.contains("starboard");
    }

    private static boolean looksLiftLabel(String label) {
        String lower = label.toLowerCase(java.util.Locale.ROOT);
        return lower.contains("lift") || lower.contains("levitat") || lower.contains("hover");
    }

    private static boolean looksPropLabel(String label) {
        String lower = label.toLowerCase(java.util.Locale.ROOT);
        return looksLeftLabel(label) || looksRightLabel(label) || lower.contains("prop") || lower.contains("thrust")
                || lower.contains("trst") || lower.contains("engine") || lower.contains("motor") || lower.contains("rpm");
    }

    private static double controlFraction(@Nullable FccGuiControlEntry control) {
        if (control == null) return 0.0;
        double span = Math.max(1.0e-6, control.inputMax() - control.inputMin());
        return Math.max(0.0, Math.min(1.0, (control.value() - control.inputMin()) / span));
    }

    private static double controlSignedFraction(@Nullable FccGuiControlEntry control) {
        if (control == null) return 0.0;
        double min = control.inputMin();
        double max = control.inputMax();
        double neutral = control.neutralValue();
        if (control.value() >= neutral) {
            return Math.max(0.0, Math.min(1.0, (control.value() - neutral) / Math.max(1.0e-6, max - neutral)));
        }
        return -Math.max(0.0, Math.min(1.0, (neutral - control.value()) / Math.max(1.0e-6, neutral - min)));
    }

    private static String formatControlValue(@Nullable FccGuiControlEntry control) {
        return control == null ? "--" : String.format(java.util.Locale.ROOT, "%.2f", control.value());
    }

    private static String displayControlType(FccGuiControlEntry control) {
        return switch (control.widgetType()) {
            case "WHEEL" -> "Wheel Control";
            case "LEVER" -> "Lever Control";
            case "DIAL" -> "Dial Control";
            case "TOGGLE" -> "Aircraft Switch";
            case "BUTTON" -> "Custom Button";
            default -> "Cockpit Control";
        };
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private static double distanceSq(double mouseX, double mouseY, int x, int y) {
        double dx = mouseX - x;
        double dy = mouseY - y;
        return dx * dx + dy * dy;
    }

    private static int mixColor(int a, int b, float t) {
        float clamped = Math.max(0.0f, Math.min(1.0f, t));
        int ar = (a >> 16) & 0xFF;
        int ag = (a >> 8) & 0xFF;
        int ab = a & 0xFF;
        int br = (b >> 16) & 0xFF;
        int bg = (b >> 8) & 0xFF;
        int bb = b & 0xFF;
        int r = Math.round(ar + (br - ar) * clamped);
        int g = Math.round(ag + (bg - ag) * clamped);
        int bl = Math.round(ab + (bb - ab) * clamped);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    private static int telemetryBindingCount(@Nullable FccTelemetryPacket telemetry) {
        if (telemetry == null) {
            return 0;
        }
        return Math.max(0, telemetry.propBoundCount());
    }

    private static int intAt(@Nullable int[] values, int index, int fallback) {
        if (values == null || index < 0 || index >= values.length) {
            return fallback;
        }
        return values[index];
    }

    private static float floatAt(@Nullable float[] values, int index, float fallback) {
        if (values == null || index < 0 || index >= values.length) {
            return fallback;
        }
        float value = values[index];
        return Float.isFinite(value) ? value : fallback;
    }

    private static boolean isGyroBinding(@Nullable FccTelemetryPacket telemetry, int index) {
        return telemetry != null && index >= 0 && index < Integer.SIZE && ((telemetry.gyroMask() & (1 << index)) != 0);
    }

    private static String oneDecimal(float value) {
        return String.format(java.util.Locale.ROOT, "%.1f", value);
    }

    private static String signed1(float value) {
        return String.format(java.util.Locale.ROOT, "%+.1f", value);
    }

    private String emanuelRowTypeLabel(FccGuiSnapshot snapshot, int rowOneBased, boolean gyro) {
        if (gyro) return "GYRO";
        for (FccGuiDeviceEntry device : snapshot.deviceEntries()) {
            if (device.displayNumber() != rowOneBased) continue;
            String label = device.categoryLabel();
            if (label == null || label.isBlank()) break;
            String upper = label.toUpperCase(java.util.Locale.ROOT);
            return upper.length() <= 6 ? upper : upper.substring(0, 6);
        }
        return "PROP";
    }

    private boolean isWheelMountRow(FccGuiSnapshot snapshot, int rowOneBased) {
        for (FccGuiDeviceEntry device : snapshot.deviceEntries()) {
            if (device.displayNumber() != rowOneBased) continue;
            return "WHEEL".equalsIgnoreCase(device.categoryLabel());
        }
        return false;
    }

    private void clampSelections(FccGuiSnapshot snapshot) {
        selectedDeviceIndex = clampIndex(selectedDeviceIndex, snapshot.deviceEntries().size());
        selectedGroupIndex = clampIndex(selectedGroupIndex, snapshot.groupEntries().size());
        selectedDiagramZoneIndex = clampIndex(selectedDiagramZoneIndex, DiagramZone.values().length);
        selectedOtomobilZoneIndex = clampIndex(selectedOtomobilZoneIndex, OtomobilZone.values().length);
        selectedControlIndex = clampIndex(selectedControlIndex, snapshot.controlEntries().size());
        selectedRouteIndex = clampIndex(selectedRouteIndex, selectedRoutes(snapshot).size());
        selectedEmanuelIndex = clampIndex(selectedEmanuelIndex, telemetryBindingCount(FccClientState.telemetryFor(menu.containerId)));
        controlScrollStart = clampScrollStart(controlScrollStart, snapshot.controlEntries().size(), CONTROL_LIST_ROWS);
        controlGroupScrollStart = clampScrollStart(controlGroupScrollStart, snapshot.groupEntries().size(), CONTROL_GROUP_ROWS);
        controlRouteScrollStart = clampScrollStart(controlRouteScrollStart, selectedRoutes(snapshot).size(), CONTROL_ROUTE_ROWS);
    }

    private static int clampIndex(int index, int size) {
        return size <= 0 ? 0 : Math.max(0, Math.min(index, size - 1));
    }

    private static int scrollStart(int current, int size, int maxRows, double scrollY) {
        return clampScrollStart(current - (int) Math.signum(scrollY), size, maxRows);
    }

    private static int clampScrollStart(int start, int size, int maxRows) {
        if (size <= maxRows) return 0;
        return Math.max(0, Math.min(start, size - maxRows));
    }

    private static int visibleStartForIndex(int index, int size, int maxRows) {
        if (size <= maxRows) return 0;
        int clamped = clampIndex(index, size);
        return Math.max(0, Math.min(clamped - maxRows / 2, size - maxRows));
    }

    private static int visibleStartIndex(int selected, int size, int maxRows) {
        if (size <= maxRows) return 0;
        int clamped = Math.max(0, Math.min(selected, size - 1));
        return Math.max(0, Math.min(clamped - maxRows / 2, size - maxRows));
    }

    private static String trimLabel(String value, int maxLen) {
        if (value == null) return "";
        return value.length() <= maxLen ? value : value.substring(0, Math.max(0, maxLen - 2)) + "..";
    }

    private static String normalizeDiagramToken(String value) {
        if (value == null) return "";
        String lower = value.toLowerCase(java.util.Locale.ROOT);
        StringBuilder builder = new StringBuilder(lower.length());
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            if (Character.isLetterOrDigit(c)) builder.append(c);
        }
        return builder.toString();
    }

    private static int withAlpha(int color, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (color & 0x00FFFFFF);
    }

    private String routeInvertButtonLabel(FccGuiSnapshot snapshot, @Nullable FccGuiRouteEntry route) {
        if (route == null) return "Direction";
        if (isOtomobilWheelRpmRoute(snapshot, route)) {
            return route.invert() ? "Wheel Direction" : "Direction: Normal";
        }
        return route.invert() ? "Direction: Inverted" : "Direction: Normal";
    }

    private String routeDirectionSuffix(FccGuiSnapshot snapshot, FccGuiRouteEntry route) {
        if (isOtomobilWheelRpmRoute(snapshot, route)) return route.invert() ? "" : " | Normal";
        return route.invert() ? " | Inverted" : "";
    }

    private static boolean isOtomobilWheelRpmRoute(FccGuiSnapshot snapshot, FccGuiRouteEntry route) {
        if (route == null || !"RPM_SCALE".equals(route.outputType())) return false;
        for (FccGuiGroupEntry group : snapshot.groupEntries()) {
            if (group.id().equals(route.targetGroupId())) return isOtomobilWheelZoneKey(group.zoneKey());
        }
        return false;
    }

    private static boolean isOtomobilWheelZoneKey(String zoneKey) {
        if (zoneKey == null || zoneKey.isBlank()) return false;
        for (OtomobilZone zone : OtomobilZone.values()) {
            if (zone.key.equalsIgnoreCase(zoneKey)) return true;
        }
        return false;
    }

    private static String shortOutput(String outputType) {
        if (outputType == null) return "Output";
        return switch (outputType) {
            case "RPM_DELTA" -> "RPM+";
            case "RPM_SCALE" -> "RPM%";
            case "ANGLE_TARGET" -> "Angle";
            case "ANGLE_OFFSET" -> "Tilt";
            case "SIGNAL_LEVEL" -> "Signal";
            case "TOGGLE_STATE" -> "Gate";
            case "PULSE_EVENT" -> "Pulse";
            default -> outputType;
        };
    }

    private static int routeTone(FccGuiRouteEntry route) {
        return switch (route.outputType()) {
            case "RPM_DELTA", "RPM_SCALE" -> CYAN;
            case "ANGLE_TARGET", "ANGLE_OFFSET" -> OK_GREEN;
            case "SIGNAL_LEVEL" -> BAD;
            case "TOGGLE_STATE" -> ACCENT;
            case "PULSE_EVENT" -> WARN;
            default -> DIM;
        };
    }

    private static String routeEffectLabel(FccGuiRouteEntry route) {
        double effect = effectiveRouteScale(route);
        String sign = effect > 0.0 ? "+" : "";
        return shortOutput(route.outputType()) + " " + sign + String.format(java.util.Locale.ROOT, "%.2f", effect);
    }

    private String routeEffectLabel(FccGuiSnapshot snapshot, FccGuiRouteEntry route) {
        if (isOtomobilWheelRpmRoute(snapshot, route)) {
            String sign = route.scale() > 0.0 ? "+" : "";
            return shortOutput(route.outputType()) + " " + sign + String.format(java.util.Locale.ROOT, "%.2f", route.scale())
                + (route.invert() ? "" : " NORM");
        }
        return routeEffectLabel(route);
    }

    private static String routeSentence(FccGuiControlEntry control, FccGuiRouteEntry route) {
        boolean negative = route.scale() < 0.0 ^ route.invert();
        String action = switch (route.outputType()) {
            case "RPM_DELTA" -> negative ? "reduces" : "adds";
            case "RPM_SCALE" -> "feeds";
            case "ANGLE_TARGET", "ANGLE_OFFSET" -> negative ? "tilts back" : "tilts";
            case "SIGNAL_LEVEL" -> "signals";
            case "TOGGLE_STATE" -> "switches";
            case "PULSE_EVENT" -> "pulses";
            default -> "drives";
        };
        return control.label() + " " + action + " " + route.targetLabel() + " (" + shortOutput(route.outputType()) + ")";
    }

    private static String testStateText(FccGuiSnapshot snapshot) {
        return switch (snapshot.calStateOrdinal()) {
            case 1 -> "Test running " + snapshot.calProgressPercent() + "%";
            case 2 -> "Surface test complete";
            case 3 -> "Surface test aborted";
            default -> {
                int linkRows = Integer.bitCount(snapshot.redstoneActiveRowMask());
                String cardRows = snapshot.resolvedBindingCount() + "/" + snapshot.boundRowCount() + " card rows";
                yield linkRows > 0 ? cardRows + ", " + linkRows + " link rows live" : cardRows + " live";
            }
        };
    }

    private String effectiveWarning(FccGuiSnapshot snapshot) {
        String warning = snapshot.lastWarning();
        if (warning == null || warning.isEmpty()) {
            warning = snapshot.duplicateRowMask() != 0 ? "Duplicate propeller/governor rows disabled" : "";
        }
        if (!warning.isEmpty()) {
            latchedWarning = warning;
            warningLatchUntilMs = System.currentTimeMillis() + (activeView == WorkView.E_MANUEL ? 4000L : 1200L);
            return warning;
        }
        if (activeView == WorkView.E_MANUEL && !latchedWarning.isEmpty() && System.currentTimeMillis() <= warningLatchUntilMs) {
            return latchedWarning;
        }
        if (System.currentTimeMillis() > warningLatchUntilMs) {
            latchedWarning = "";
        }
        return "";
    }

    private static String workbenchSignature(FccGuiSnapshot snapshot) {
        StringBuilder builder = new StringBuilder();
        builder.append(snapshot.mode()).append('|')
                .append(snapshot.deviceCount()).append('|')
                .append(snapshot.groupCount()).append('|')
                .append(snapshot.controlCount()).append('|')
                .append(snapshot.ungroupedDeviceCount()).append('|')
                .append(snapshot.calStateOrdinal()).append('|')
                .append(snapshot.calProgressPercent()).append('|')
                .append(snapshot.redstoneRowMask()).append('|')
                .append(snapshot.redstoneActiveRowMask()).append('|')
                .append(snapshot.redstonePoweredRowMask()).append('|')
                .append(snapshot.linkedControllerConfiguredMask()).append('|')
                .append(snapshot.linkedControllerInputMask()).append('|')
                .append(snapshot.linkedControllerSignalPack()).append('|')
                .append(snapshot.linkedControllerRampIntervalTicks()).append('|')
                .append(snapshot.pathfinderSystemsEnabled()).append('|')
                .append(snapshot.pathfinderComputerPos()).append('|')
                .append(snapshot.pathfinderRouteStatus()).append('|');
        builder.append('|');
        for (FccGuiDeviceEntry device : snapshot.deviceEntries()) {
            builder.append(device.id()).append(':').append(device.label()).append(':').append(device.categoryLabel()).append(':').append(device.resolved()).append(':').append(device.grouped()).append(';');
        }
        builder.append('|');
        for (FccGuiGroupEntry group : snapshot.groupEntries()) {
            builder.append(group.id()).append(':').append(group.label()).append(':').append(group.deviceCount()).append(';');
        }
        builder.append('|');
        for (FccGuiControlEntry control : snapshot.controlEntries()) {
            builder.append(control.id()).append(':').append(control.label()).append(':').append(control.widgetType()).append(':').append(control.routeCount()).append(':').append(control.value()).append(':').append(control.inputMin()).append(':').append(control.inputMax()).append(':').append(control.neutralValue()).append(';');
        }
        builder.append('|');
        for (FccGuiRouteEntry route : snapshot.routeEntries()) {
            builder.append(route.id()).append(':').append(route.controlId()).append(':').append(route.targetLabel()).append(':').append(route.outputType()).append(':').append(route.scale()).append(':').append(route.invert()).append(';');
        }
        return builder.toString();
    }

    private FccGuiSnapshot snapshotOrEmpty() {
        FccGuiSnapshot snapshot = FccClientState.snapshotFor(menu.containerId);
        return snapshot != null ? snapshot : FccClientState.empty();
    }

    private static EnumSet<SensorRequirement> sensorCopy(FccGuiSnapshot snapshot) {
        EnumSet<SensorRequirement> copy = EnumSet.noneOf(SensorRequirement.class);
        copy.addAll(snapshot.availableSensors());
        return copy;
    }

    private static void border(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        graphics.fill(x, y, x + width, y + 1, color);
        graphics.fill(x, y + height - 1, x + width, y + height, color);
        graphics.fill(x, y + 1, x + 1, y + height - 1, color);
        graphics.fill(x + width - 1, y + 1, x + width, y + height - 1, color);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        updateNoticeTimer();
        // Run a rebuild that was deferred while an edit box was focused or a slider was mid-drag.
        if (pendingViewRebuild && !isWorkbenchEditFocused() && !isSliderDragActive()) {
            rebuildViewWidgets();
        }
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        // FE Dependent Mode side panel (self-gating: draws nothing while the mode is off).
        com.createstats.power.client.FeStatusWidget.draw(graphics, font, leftPos + imageWidth + 6, topPos + 6, 110,
                menu.getController() == null ? null : menu.getController().feConsumer());
        if (helpOpen) renderHelpOverlay(graphics);
        renderTooltip(graphics, mouseX, mouseY);
        if (!helpOpen) renderHotspotTooltip(graphics, mouseX, mouseY);
    }

    // [12]
    private void updateNoticeTimer() {
        if (!notice.equals(lastNoticeSeen)) {
            lastNoticeSeen = notice;
            noticeSetAtMs = System.currentTimeMillis();
        } else if (!notice.isEmpty() && System.currentTimeMillis() - noticeSetAtMs > NOTICE_TTL_MS) {
            notice = "";
            lastNoticeSeen = "";
        }
    }

    @Override
    public void onClose() {
        super.onClose();
    }

    private static final class StyleChipButton extends AbstractWidget {
        private final boolean selected;
        private final int chipColor;
        private final Runnable action;

        StyleChipButton(int x, int y, int w, int h, String label, boolean selected, int chipColor, Runnable action) {
            super(x, y, w, h, Component.literal(label));
            this.selected = selected;
            this.chipColor = chipColor;
            this.action = action;
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
            boolean hov = isHoveredOrFocused();
            int bg = selected ? 0xFF0D1E2C : 0xFF070E17;
            int bord = !active ? 0xFF222F3A : selected ? chipColor : hov ? 0xFF3A5570 : 0xFF1D2E3C;
            int tc = !active ? 0xFF3A4A5A : selected ? chipColor : hov ? 0xFFCCDFEF : 0xFF667A8C;
            g.fill(getX(), getY(), getX() + width, getY() + height, bg);
            g.fill(getX(), getY(), getX() + width, getY() + 1, bord);
            g.fill(getX(), getY() + height - 1, getX() + width, getY() + height, bord);
            g.fill(getX(), getY(), getX() + 1, getY() + height, bord);
            g.fill(getX() + width - 1, getY(), getX() + width, getY() + height, bord);
            g.drawCenteredString(net.minecraft.client.Minecraft.getInstance().font, getMessage(), getX() + width / 2, getY() + (height - 7) / 2, tc);
        }

        @Override
        public void onClick(double mx, double my) {
            if (active) action.run();
        }

        @Override
        protected boolean isValidClickButton(int b) {
            return b == 0;
        }

        @Override
        public void updateWidgetNarration(net.minecraft.client.gui.narration.NarrationElementOutput o) {
            defaultButtonNarrationText(o);
        }
    }

    private static final class PathfinderSmoothnessSlider extends AbstractSliderButton {
        private static final float MIN_SCALE = 0.5f;
        private static final float MAX_SCALE = 3.0f;
        private final FlightControlComputerScreen screen;
        private float lastSent;

        PathfinderSmoothnessSlider(int x, int y, int w, int h, FlightControlComputerScreen screen, float current) {
            super(x, y, w, h, Component.empty(),
                Math.max(0.0, Math.min(1.0, (Math.max(MIN_SCALE, Math.min(MAX_SCALE, current)) - MIN_SCALE) / (MAX_SCALE - MIN_SCALE))));
            this.screen = screen;
            this.lastSent = current;
            updateMessage();
        }

        @Override
        public void renderWidget(GuiGraphics g, int mx, int my, float pt) {
            int x = getX(), y = getY();
            boolean hov = mx >= x && mx < x + width && my >= y && my < y + height;
            int railY = y + height / 2 - 3;
            g.fill(x, y, x + width, y + height, 0xFF101923);
            border(g, x, y, width, height, hov ? OK_GREEN : SOFT_BORDER);
            g.fill(x + 7, railY, x + width - 7, railY + 6, 0xFF071016);
            int fill = (int) Math.round((width - 14) * value);
            g.fill(x + 7, railY, x + 7 + fill, railY + 6, OK_GREEN);
            int hx = x + 7 + fill;
            g.fill(hx - 3, y + 3, hx + 4, y + height - 3, OK_GREEN);
            g.drawCenteredString(screen.font, getMessage().getString(), x + width / 2, y + 6, TEXT);
        }

        @Override
        public void onClick(double mx, double my) { setFromMouse(mx); }

        @Override
        protected void onDrag(double mx, double my, double dx, double dy) { setFromMouse(mx); }

        @Override
        protected void updateMessage() {
            setMessage(Component.literal(String.format(java.util.Locale.ROOT, "Smoothness: %.2fx", curVal())));
        }

        @Override
        protected void applyValue() {
            float v = curVal();
            if (Math.abs(v - lastSent) >= 0.02f) {
                lastSent = v;
                screen.sendSetPathfinderLookaheadScale(v);
            }
            updateMessage();
        }

        @Override
        public void onRelease(double mx, double my) {
            super.onRelease(mx, my);
            float v = curVal();
            lastSent = v;
            screen.sendSetPathfinderLookaheadScale(v);
            updateMessage();
        }

        private void setFromMouse(double mx) {
            value = Math.max(0.0, Math.min(1.0, (mx - getX() - 7.0) / Math.max(1.0, width - 14.0)));
            applyValue();
        }

        private float curVal() {
            return MIN_SCALE + (float) value * (MAX_SCALE - MIN_SCALE);
        }
    }

    private static final class NavTableSpeedSlider extends AbstractSliderButton {
        private static final float MIN_SPD = 1.0f;
        private static final float MAX_SPD = 32.0f;
        private final FlightControlComputerScreen screen;
        private float lastSent;

        NavTableSpeedSlider(int x, int y, int w, int h, FlightControlComputerScreen screen, float current) {
            super(x, y, w, h, Component.empty(),
                Math.max(0.0, Math.min(1.0, (Math.max(MIN_SPD, Math.min(MAX_SPD, current)) - MIN_SPD) / (MAX_SPD - MIN_SPD))));
            this.screen = screen;
            this.lastSent = current;
            updateMessage();
        }

        @Override
        public void renderWidget(GuiGraphics g, int mx, int my, float pt) {
            int x = getX(), y = getY();
            boolean hov = mx >= x && mx < x + width && my >= y && my < y + height;
            int railY = y + height / 2 - 3;
            g.fill(x, y, x + width, y + height, 0xFF101923);
            border(g, x, y, width, height, hov ? ACCENT : SOFT_BORDER);
            g.fill(x + 7, railY, x + width - 7, railY + 6, 0xFF071016);
            int fill = (int) Math.round((width - 14) * value);
            g.fill(x + 7, railY, x + 7 + fill, railY + 6, ACCENT);
            int hx = x + 7 + fill;
            g.fill(hx - 3, y + 3, hx + 4, y + height - 3, ACCENT);
            g.drawCenteredString(screen.font, getMessage().getString(), x + width / 2, y + 6, TEXT);
        }

        @Override
        public void onClick(double mx, double my) {
            setFromMouse(mx);
        }

        @Override
        protected void onDrag(double mx, double my, double dx, double dy) {
            setFromMouse(mx);
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.literal(String.format(java.util.Locale.ROOT, "Nav Spd: %.0f m/s", curVal())));
        }

        @Override
        protected void applyValue() {
            float v = curVal();
            if (Math.abs(v - lastSent) >= 0.5f) {
                lastSent = v;
                screen.sendSetNavTableSpeed(v);
            }
            updateMessage();
        }

        @Override
        public void onRelease(double mx, double my) {
            super.onRelease(mx, my);
            float v = curVal();
            lastSent = v;
            screen.sendSetNavTableSpeed(v);
            updateMessage();
        }

        private void setFromMouse(double mx) {
            value = Math.max(0.0, Math.min(1.0, (mx - getX() - 7.0) / Math.max(1.0, width - 14.0)));
            applyValue();
        }

        private float curVal() {
            return MIN_SPD + (float) value * (MAX_SPD - MIN_SPD);
        }
    }

    private static final class ControlValueSlider extends AbstractSliderButton {
        private final FlightControlComputerScreen screen;
        @Nullable private final FccGuiControlEntry control;
        private double lastSent;

        ControlValueSlider(int x, int y, int width, int height, FlightControlComputerScreen screen, @Nullable FccGuiControlEntry control) {
            super(x, y, width, height, Component.literal(sliderMessage(control)), sliderPosition(control));
            this.screen = screen;
            this.control = control;
            this.lastSent = rawValue();
            updateMessage();
        }

        @Override
        public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            int x = getX();
            int y = getY();
            boolean hover = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
            int railY = y + height / 2 - 3;
            graphics.fill(x, y, x + width, y + height, active ? 0xFF101923 : 0xFF0A0E13);
            border(graphics, x, y, width, height, hover ? CYAN : SOFT_BORDER);
            graphics.fill(x + 7, railY, x + width - 7, railY + 6, 0xFF071016);
            int fill = (int) Math.round((width - 14) * value);
            graphics.fill(x + 7, railY, x + 7 + fill, railY + 6, active ? OK_GREEN : MUTED);
            int handleX = x + 7 + fill;
            graphics.fill(handleX - 3, y + 3, handleX + 4, y + height - 3, active ? ACCENT : MUTED);
            String label = getMessage().getString();
            graphics.drawCenteredString(screen.font, label, x + width / 2, y + 6, active ? TEXT : MUTED);
        }

        @Override
        public void onClick(double mouseX, double mouseY) {
            setValueFromMouse(mouseX);
        }

        @Override
        protected void onDrag(double mouseX, double mouseY, double dragX, double dragY) {
            setValueFromMouse(mouseX);
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.literal(sliderMessage(control, rawValue())));
        }

        @Override
        protected void applyValue() {
            if (control == null) return;
            double raw = rawValue();
            if (Math.abs(raw - lastSent) >= 0.02) {
                lastSent = raw;
                screen.sendSetControlValue(control.id(), raw, false);
            }
            updateMessage();
        }

        @Override
        public void onRelease(double mouseX, double mouseY) {
            super.onRelease(mouseX, mouseY);
            if (control == null) return;
            double raw = rawValue();
            lastSent = raw;
            screen.sendSetControlValue(control.id(), raw, true);
            updateMessage();
        }

        private void setValueFromMouse(double mouseX) {
            double inner = Math.max(1.0, width - 14.0);
            value = Math.max(0.0, Math.min(1.0, (mouseX - getX() - 7.0) / inner));
            applyValue();
        }

        private double rawValue() {
            if (control == null) return 0.0;
            double span = Math.max(1.0e-6, control.inputMax() - control.inputMin());
            return control.inputMin() + value * span;
        }

        private static double sliderPosition(@Nullable FccGuiControlEntry control) {
            if (control == null) return 0.5;
            double span = Math.max(1.0e-6, control.inputMax() - control.inputMin());
            return Math.max(0.0, Math.min(1.0, (control.value() - control.inputMin()) / span));
        }

        private static String sliderMessage(@Nullable FccGuiControlEntry control) {
            return sliderMessage(control, control == null ? 0.0 : control.value());
        }

        private static String sliderMessage(@Nullable FccGuiControlEntry control, double raw) {
            if (control == null) return "No control";
            return String.format(java.util.Locale.ROOT, "%.2f", raw);
        }
    }

    private static final class LinkedControllerIntervalSlider extends AbstractSliderButton {
        private final FlightControlComputerScreen screen;
        private int lastSent;

        LinkedControllerIntervalSlider(int x, int y, int width, int height, FlightControlComputerScreen screen, int ticks) {
            super(x, y, width, height, Component.literal(intervalMessage(clampTicks(ticks))), intervalPosition(clampTicks(ticks)));
            this.screen = screen;
            this.lastSent = clampTicks(ticks);
            updateMessage();
        }

        @Override
        public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            int x = getX();
            int y = getY();
            boolean hover = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
            int railY = y + height / 2 - 3;
            graphics.fill(x, y, x + width, y + height, 0xFF101923);
            border(graphics, x, y, width, height, hover ? ACCENT : SOFT_BORDER);
            graphics.fill(x + 7, railY, x + width - 7, railY + 6, 0xFF071016);
            int fill = (int) Math.round((width - 14) * value);
            graphics.fill(x + 7, railY, x + 7 + fill, railY + 6, CYAN);
            int handleX = x + 7 + fill;
            graphics.fill(handleX - 3, y + 3, handleX + 4, y + height - 3, ACCENT);
            graphics.drawCenteredString(screen.font, getMessage().getString(), x + width / 2, y + 6, TEXT);
        }

        @Override
        public void onClick(double mouseX, double mouseY) {
            setValueFromMouse(mouseX);
        }

        @Override
        protected void onDrag(double mouseX, double mouseY, double dragX, double dragY) {
            setValueFromMouse(mouseX);
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.literal(intervalMessage(currentTicks())));
        }

        @Override
        protected void applyValue() {
            int ticks = currentTicks();
            if (ticks != lastSent) {
                lastSent = ticks;
                screen.sendSetLinkedControllerInterval(ticks);
            }
            updateMessage();
        }

        @Override
        public void onRelease(double mouseX, double mouseY) {
            super.onRelease(mouseX, mouseY);
            int ticks = currentTicks();
            lastSent = ticks;
            screen.sendSetLinkedControllerInterval(ticks);
            updateMessage();
        }

        private void setValueFromMouse(double mouseX) {
            double inner = Math.max(1.0, width - 14.0);
            value = Math.max(0.0, Math.min(1.0, (mouseX - getX() - 7.0) / inner));
            applyValue();
        }

        private int currentTicks() {
            return clampTicks(1 + (int) Math.round(value * 59.0));
        }

        private static double intervalPosition(int ticks) {
            return (clampTicks(ticks) - 1) / 59.0;
        }

        private static int clampTicks(int ticks) {
            return Math.max(1, Math.min(60, ticks));
        }

        private static String intervalMessage(int ticks) {
            return "Ramp: " + ticks + " tick" + (ticks == 1 ? "" : "s");
        }
    }

    private void renderEmanuelAlertBanner(GuiGraphics graphics) {
        // Show alert if any E-Manuel overrides are active
        java.util.List<Integer> activeRows = new java.util.ArrayList<>();
        for (int i = 0; i < emanuelOverrideActive.length; i++) {
            if (emanuelOverrideActive[i]) {
                activeRows.add(i);
            }
        }
        if (activeRows.isEmpty()) return;

        // Build alert text like "! MANUAL OVERRIDE ACTIVE: R1, R3, R7". Uses U+26A0 without the
        // emoji variation selector, which Minecraft's font cannot render.
        StringBuilder text = new StringBuilder("⚠ MANUAL OVERRIDE ACTIVE: ");
        for (int i = 0; i < activeRows.size(); i++) {
            if (i > 0) text.append(", ");
            text.append("R").append(activeRows.get(i) + 1);
        }

        int x = leftPos + DECK_X;
        int y = topPos + STATUS_Y - 22; // Just above status panel
        int w = DECK_W + STATUS_W + 4; // Span across both panels
        int h = 18;

        // Yellow alert background
        graphics.fill(x, y, x + w, y + h, 0xFF332200); // dark yellow
        border(graphics, x, y, w, h, WARN);
        graphics.drawString(font, font.plainSubstrByWidth(text.toString(), w - 14), x + 8, y + 4, WARN, false);
    }
}
