package com.createstats.vccmk3;

import com.createstats.CreateStats;
import com.createstats.DebugLogging;
import com.createstats.flight.blockentity.RpmGovernorBE;
import com.createstats.flight.bridge.SableBridge;
import com.createstats.flight.item.GovernorCardItem;
import com.createstats.flight.item.PropellerCardItem;
import com.createstats.vccmk3.avionics.AvionicsSampler;
import com.createstats.vccmk3.avionics.SensorDiscovery;
import com.createstats.vccmk3.avionics.AvionicsState;
import com.createstats.vccmk3.hardware.DeviceCategory;
import com.createstats.vccmk3.hardware.HardwareScan;
import com.createstats.vccmk3.hardware.LinkSlot;
import com.createstats.vccmk3.model.Airframe;
import com.createstats.vccmk3.model.ControlAxis;
import com.createstats.vccmk3.model.SlotSpec;
import com.createstats.vccmk3.model.VccDevice;
import com.createstats.vccmk3.runtime.AutoPilot;
import com.createstats.vccmk3.runtime.CraftFrame;
import com.createstats.vccmk3.runtime.RigReport;
import com.createstats.vccmk3.runtime.RigSolution;
import com.createstats.vccmk3.runtime.RouteFollower;
import com.createstats.vccmk3.runtime.CommandBatch;
import com.createstats.vccmk3.network.VccMk3HudPacket;
import com.createstats.vccmk3.network.VccMk3SnapshotPacket;
import com.simibubi.create.Create;
import com.simibubi.create.content.redstone.link.IRedstoneLinkable;
import com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler;
import net.createmod.catnip.data.Couple;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import net.neoforged.neoforge.items.ItemStackHandler;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class VccMk3BlockEntity extends BlockEntity
        implements MenuProvider, IHaveGoggleInformation, com.createstats.power.IFeConsumer {
    public static final int NBT_VERSION = 1;

    public static final int ROW_COUNT = 24;
    public static final int SLOT_COUNT = ROW_COUNT * 2;

    private static final int SCAN_INTERVAL_TICKS = 10;

    private static final int GOVERNOR_REFRESH_TICKS = 20;

    private static final int SNAPSHOT_INTERVAL_TICKS = 4;

    private static final int HUD_INTERVAL_TICKS = 4;

    private static final int RENDER_INTERVAL_TICKS = 3;

    private static final float RENDER_ANGLE_EPSILON = 0.35f;

    private static final float RENDER_LEVEL_EPSILON = 0.01f;

    private static final float RENDER_EASE = 0.28f;

    @Nullable private UUID controllerId;

    private Airframe airframe = Airframe.FIXED_WING;

    private final Map<UUID, String> slotAssignments = new LinkedHashMap<>();
    private boolean armed;
    private int linkRowMask;
    private String craftName = "";

    private final Map<ControlAxis, Double> axisDemand = new EnumMap<>(ControlAxis.class);

    private final SensorDiscovery sensors = new SensorDiscovery();

    private final Map<UUID, Integer> manualOverrides = new LinkedHashMap<>();

    private boolean autoPilotEngaged;

    private boolean autoUseRoute;

    private double flipGuardDegrees = 62.0;

    private int linkRxMask;

    private final Map<Integer, ControlAxis> rxBindings = new LinkedHashMap<>();

    private final Map<Integer, Integer> rxStrength = new LinkedHashMap<>();

    private final com.createstats.power.FeConsumer feConsumer = com.createstats.power.FeConsumer
            .forBlockEntity(this, com.createstats.power.FeProfiles.settings(com.createstats.power.FeProfiles.KEY_FCC));

    private List<VccDevice> devices = new ArrayList<>();
    private List<LinkSlot> slots = new ArrayList<>();
    private AvionicsState avionics = AvionicsState.EMPTY;
    private final Map<BlockPos, Integer> lastGovernorCommand = new LinkedHashMap<>();
    private final Map<Integer, SignalTransmitter> transmitters = new LinkedHashMap<>();
    private long tickCount;
    private long lastRenderTick = Long.MIN_VALUE;

    private float renderPitch = Float.NaN;
    private float renderRoll = Float.NaN;

    private float renderCommand;

    private boolean renderAuto;

    private float easedPitch;
    private float easedRoll;
    private float easedCommand;
    private float prevEasedPitch;
    private float prevEasedRoll;
    private float prevEasedCommand;
    private long lastScanTick = Long.MIN_VALUE;
    @Nullable private CraftFrame cachedFrame;
    private long cachedFrameTick = Long.MIN_VALUE;
    private long lastGovernorRefreshTick = Long.MIN_VALUE;
    private long lastSnapshotTick = Long.MIN_VALUE;
    private long lastSensorScanTick = Long.MIN_VALUE;
    private boolean flipGuardActive;
    private double lastTiltDegrees = Double.NaN;
    private String lastNotice = "";
    private AutoPilot.Guidance guidance = AutoPilot.Guidance.idle("Autopilot off");
    private RouteFollower.Progress routeProgress = RouteFollower.Progress.inactive("Autopilot off");

    private final Set<UUID> hudSubscribers = new LinkedHashSet<>();
    private long lastHudTick = Long.MIN_VALUE;
    private List<com.createstats.vccmk3.alert.ConsoleAlert> lastAlerts = List.of();
    private int lastRoutesApplied;
    private int lastRoutesUnsupported;

    private final ItemStackHandler inventory = new ItemStackHandler(SLOT_COUNT) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            int row = slot / 2;
            if ((linkRowMask & (1 << row)) != 0) return true;
            return (slot % 2 == 0)
                    ? stack.getItem() instanceof PropellerCardItem
                    : stack.getItem() instanceof GovernorCardItem;
        }

        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();

            lastScanTick = Long.MIN_VALUE;
        }
    };

    public VccMk3BlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public UUID controllerId() {
        if (controllerId == null) {
            controllerId = UUID.randomUUID();
            setChanged();
        }
        return controllerId;
    }

    public ItemStackHandler inventory() {
        return inventory;
    }

    public boolean isArmed() {
        return armed;
    }

    public Airframe airframe() {
        return airframe;
    }

    public int setAirframe(@Nullable Airframe next) {
        if (next == null || next == airframe) return 0;
        airframe = next;
        int dropped = 0;
        for (Iterator<Map.Entry<UUID, String>> it = slotAssignments.entrySet().iterator(); it.hasNext(); ) {
            if (!airframe.hasSlot(it.next().getValue())) {
                it.remove();
                dropped++;
            }
        }
        setChanged();
        return dropped;
    }

    @Nullable
    public String slotOf(@Nullable UUID deviceId) {
        return deviceId == null ? null : slotAssignments.get(deviceId);
    }

    public Map<UUID, String> slotAssignments() {
        return Collections.unmodifiableMap(slotAssignments);
    }

    public List<VccDevice> devicesInSlot(@Nullable String slotKey) {
        List<VccDevice> out = new ArrayList<>();
        if (slotKey == null) return out;
        for (VccDevice device : devices) {
            if (slotKey.equals(slotAssignments.get(device.id()))) out.add(device);
        }
        return out;
    }

    public boolean assignToSlot(@Nullable UUID deviceId, @Nullable String slotKey) {
        if (deviceId == null) return false;
        if (slotKey == null || slotKey.isEmpty()) {
            boolean removed = slotAssignments.remove(deviceId) != null;
            if (removed) {
                lastNotice = "";
                setChanged();
            }
            return removed;
        }
        SlotSpec spec = airframe.slot(slotKey);
        if (spec == null) return false;
        VccDevice device = deviceById(deviceId);
        if (device == null) return false;
        if (!spec.accepts(device.category())) {
            lastNotice = "A " + device.category().label().toLowerCase(java.util.Locale.ROOT)
                    + " cannot be a " + spec.label().toLowerCase(java.util.Locale.ROOT) + ".";
            setChanged();
            return false;
        }
        slotAssignments.put(deviceId, slotKey);
        lastNotice = "";
        setChanged();
        return true;
    }

    public boolean clearSlot(@Nullable String slotKey) {
        if (slotKey == null) return false;
        boolean any = slotAssignments.values().removeIf(slotKey::equals);
        if (any) {
            lastNotice = "";
            setChanged();
        }
        return any;
    }

    @Nullable
    private VccDevice deviceById(UUID id) {
        for (VccDevice device : devices) {
            if (device.id().equals(id)) return device;
        }
        return null;
    }

    public String craftName() {
        return craftName;
    }

    public void setCraftName(@Nullable String name) {
        craftName = name == null ? "" : name.trim();
        setChanged();
    }

    public void setArmed(boolean next) {
        if (armed == next) return;
        armed = next;
        setChanged();
        if (!armed) {
            centreAllAxes();
            if (level instanceof ServerLevel serverLevel) {
                zeroAllOutputs(serverLevel);
            }
        }
    }

    public List<VccDevice> devices() {
        return List.copyOf(devices);
    }

    public List<LinkSlot> slots() {
        return List.copyOf(slots);
    }

    public AvionicsState avionics() {
        return avionics;
    }

    public boolean isLinkRow(int row) {
        return row >= 0 && row < ROW_COUNT && (linkRowMask & (1 << row)) != 0;
    }

    public void toggleLinkRow(int row) {
        if (row < 0 || row >= ROW_COUNT) return;
        linkRowMask ^= (1 << row);

        dropRowContents(row);
        lastScanTick = Long.MIN_VALUE;
        setChanged();
    }

    private void dropRowContents(int row) {
        if (level == null || level.isClientSide) return;
        for (int slot : new int[] { HardwareScan.actuatorSlot(row), HardwareScan.governorSlot(row) }) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (stack.isEmpty()) continue;
            inventory.setStackInSlot(slot, ItemStack.EMPTY);
            net.minecraft.world.Containers.dropItemStack(level,
                    worldPosition.getX() + 0.5, worldPosition.getY() + 1.0, worldPosition.getZ() + 0.5, stack);
        }
    }

    public double axisDemand(@Nullable ControlAxis axis) {
        if (axis == null) return 0.0;
        Double value = axisDemand.get(axis);
        return value == null ? 0.0 : value;
    }

    public void setAxisDemand(@Nullable ControlAxis axis, double value) {
        if (axis == null || !Double.isFinite(value)) return;
        axisDemand.put(axis, Math.max(-1.0, Math.min(1.0, value)));
    }

    public void centreAllAxes() {
        axisDemand.clear();
    }

    public Map<ControlAxis, Double> axisDemands() {
        return Collections.unmodifiableMap(axisDemand);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, VccMk3BlockEntity be) {
        if (level.isClientSide || !(level instanceof ServerLevel serverLevel)) return;
        be.tick(serverLevel);
    }

    private void tick(ServerLevel serverLevel) {
        tickCount++;
        SableBridge.beginTick(serverLevel.getGameTime());

        if (tickCount - lastScanTick >= SCAN_INTERVAL_TICKS || lastScanTick == Long.MIN_VALUE) {
            lastScanTick = tickCount;
            rescan();
        }

        if (tickCount - lastSensorScanTick >= SensorDiscovery.SCAN_PERIOD_TICKS
                || lastSensorScanTick == Long.MIN_VALUE) {
            lastSensorScanTick = tickCount;
            sensors.scan(level, worldPosition);
        }

        avionics = AvionicsSampler.sample(level, this,
                sensors.effective(SensorDiscovery.Kind.GIMBAL),
                sensors.effective(SensorDiscovery.Kind.ALTITUDE),
                sensors.effective(SensorDiscovery.Kind.VELOCITY),
                sensors.effective(SensorDiscovery.Kind.OPTICAL),
                null);

        if (tickCount - lastSnapshotTick >= SNAPSHOT_INTERVAL_TICKS || lastSnapshotTick == Long.MIN_VALUE) {
            lastSnapshotTick = tickCount;
            broadcastSnapshot(serverLevel);
        }

        if (!hudSubscribers.isEmpty()
                && (tickCount - lastHudTick >= HUD_INTERVAL_TICKS || lastHudTick == Long.MIN_VALUE)) {
            lastHudTick = tickCount;
            broadcastHud(serverLevel);
        }

        if (tickCount - lastRenderTick >= RENDER_INTERVAL_TICKS || lastRenderTick == Long.MIN_VALUE) {
            lastRenderTick = tickCount;
            publishRenderState(serverLevel);
        }

        if (!armed) {
            zeroAllOutputs(serverLevel);
            return;
        }

        applyReceivedSignals();
        updateAutoPilot();
        updateFlipGuard(serverLevel);

        CommandBatch batch = new CommandBatch();

        RigSolution solution = solveRig();
        if (flipGuardActive) {
            forceRecoveryLift(solution, batch);
        } else {
            solution.dispatch(axisDemand, commandableDeviceIds(), batch);
        }
        applyManualOverrides(batch);
        applyBatch(serverLevel, batch);

        lastRoutesApplied = batch.routesApplied();
        lastRoutesUnsupported = batch.routesUnsupported();
    }

    private void broadcastSnapshot(ServerLevel serverLevel) {
        List<net.minecraft.server.level.ServerPlayer> viewers = consoleViewers(serverLevel);
        if (viewers.isEmpty() && hudSubscribers.isEmpty()) return;

        // solve here, the tick doesn't run while disarmed and that's when you rig

        CraftFrame craftFrame = craftFrame();
        RigSolution solution = RigSolution.solve(airframe, slotAssignments, devices, craftFrame);

        List<com.createstats.vccmk3.alert.ConsoleAlert> computed =
                com.createstats.vccmk3.alert.ConsoleAlert.evaluate(
                        armed, flipGuardActive, airframe, slots, solution, avionics,
                        sensors, level, manualOverrides.size(),
                        autoPilotEngaged,
                        autoUseRoute ? routeProgress.active() : guidance.hasFix(),
                        autoUseRoute ? routeProgress.status() : guidance.status(),
                        kineticallyDrivenSurfaces());
        lastAlerts = computed;

        if (viewers.isEmpty()) return;

        net.minecraft.core.Direction consoleFacing = consoleFacing();
        RigReport.Result rig = RigReport.build(level, this, airframe, slotAssignments,
                devices, craftFrame, solution);

        List<VccMk3SnapshotPacket.SlotEntry> slotEntries = new ArrayList<>(slots.size());
        for (LinkSlot slot : slots) {
            slotEntries.add(new VccMk3SnapshotPacket.SlotEntry(
                    slot.row(),
                    (byte) slot.health().ordinal(),
                    (byte) (slot.category() == null ? -1 : slot.category().ordinal()),
                    slot.deviceId() != null,
                    slot.detail()));
        }

        byte[] sensorStates = new byte[VccMk3SnapshotPacket.SENSOR_COUNT];
        SensorDiscovery.Kind[] kinds = SensorDiscovery.Kind.values();
        for (int i = 0; i < sensorStates.length && i < kinds.length; i++) {
            SensorDiscovery.Kind kind = kinds[i];
            boolean live = level != null && sensors.isLive(level, kind);
            if (sensors.isManual(kind)) {
                sensorStates[i] = live ? VccMk3SnapshotPacket.SENSOR_MANUAL : VccMk3SnapshotPacket.SENSOR_BROKEN;
            } else {
                sensorStates[i] = live ? VccMk3SnapshotPacket.SENSOR_AUTO : VccMk3SnapshotPacket.SENSOR_NONE;
            }
        }

        List<VccMk3SnapshotPacket.DeviceEntry> deviceEntries = new ArrayList<>(devices.size());
        for (VccDevice device : devices) {
            int actual = 0;
            if (device.governorPos() != null) {
                actual = lastGovernorCommand.getOrDefault(device.governorPos(), 0);
            }

            BlockPos actuator = device.actuatorPos();
            deviceEntries.add(new VccMk3SnapshotPacket.DeviceEntry(
                    device.id(), device.label(), (byte) device.category().ordinal(),
                    device.enabled(), manualOverrides.getOrDefault(device.id(), -1), actual,
                    VccMk3SnapshotPacket.clampPosition(actuator.getX() - worldPosition.getX()),
                    VccMk3SnapshotPacket.clampPosition(actuator.getY() - worldPosition.getY()),
                    VccMk3SnapshotPacket.clampPosition(actuator.getZ() - worldPosition.getZ()),
                    device.cardRow(),
                    rig.deviceNotes().getOrDefault(device.id(), "")));
        }

        List<VccMk3SnapshotPacket.AlertEntry> alertEntries = new ArrayList<>(computed.size());
        for (com.createstats.vccmk3.alert.ConsoleAlert alert : computed) {
            alertEntries.add(new VccMk3SnapshotPacket.AlertEntry(
                    (byte) alert.level().ordinal(), alert.code(), alert.message()));
        }

        float[] demand = new float[VccMk3SnapshotPacket.AXIS_COUNT];
        for (ControlAxis axis : ControlAxis.values()) {
            demand[axis.ordinal()] = (float) axisDemand(axis);
        }

        VccMk3SnapshotPacket packet = new VccMk3SnapshotPacket(
                worldPosition, consoleFacing.ordinal(), armed, flipGuardActive,
                (float) flipGuardDegrees, craftName, linkRowMask, linkRxMask,
                lastNotice,
                new VccMk3SnapshotPacket.AutoState(autoPilotEngaged,
                        autoUseRoute ? routeProgress.active() : guidance.hasFix(), autoUseRoute,
                        routeProgress.waypointIndex(), routeProgress.waypointCount(),
                        (float) (autoUseRoute ? routeProgress.distanceToEnd() : guidance.distance()),
                        (float) guidance.bearingDeg(),
                        autoUseRoute ? routeProgress.status() : guidance.status()),
                slotEntries, deviceEntries, alertEntries, sensorStates,
                VccMk3SnapshotPacket.packValues(avionics),
                VccMk3SnapshotPacket.packSources(avionics),
                airframe.ordinal(), rig.assignments(), rig.kinetics(), rig.authority(),
                demand, rig.axisReports(), rig.slotReports(), rig.bus(), rig.coupling());

        for (net.minecraft.server.level.ServerPlayer player : viewers) {
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, packet);
        }
    }

    private List<net.minecraft.server.level.ServerPlayer> consoleViewers(ServerLevel serverLevel) {
        List<net.minecraft.server.level.ServerPlayer> out = null;
        for (net.minecraft.server.level.ServerPlayer player : serverLevel.players()) {
            if (player.containerMenu instanceof VccMk3Menu menu && menu.controller() == this) {
                if (out == null) out = new ArrayList<>(2);
                out.add(player);
            }
        }
        return out == null ? List.of() : out;
    }

    private void publishRenderState(ServerLevel serverLevel) {
        float pitch = Float.NaN;
        float roll = Float.NaN;
        if (armed && avionics.pitchDeg().present() && avionics.rollDeg().present()) {
            pitch = (float) avionics.pitchDeg().value();
            roll = (float) avionics.rollDeg().value();
        }

        float command = armed ? dominantDemand() : 0.0f;
        boolean auto = armed && autoPilotEngaged;

        boolean attitudeChanged = (Float.isNaN(pitch) != Float.isNaN(renderPitch))
                || (!Float.isNaN(pitch)
                        && (Math.abs(pitch - renderPitch) > RENDER_ANGLE_EPSILON
                            || Math.abs(roll - renderRoll) > RENDER_ANGLE_EPSILON));
        boolean commandChanged = Math.abs(command - renderCommand) > RENDER_LEVEL_EPSILON
                || auto != renderAuto;
        if (!attitudeChanged && !commandChanged) return;

        renderPitch = pitch;
        renderRoll = roll;
        renderCommand = command;
        renderAuto = auto;

        BlockState state = getBlockState();
        serverLevel.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
    }

    private float dominantDemand() {
        for (ControlAxis preferred : new ControlAxis[] { ControlAxis.FORWARD, ControlAxis.UP }) {
            if (hasSlotServing(preferred)) {
                return (float) Math.max(-1.0, Math.min(1.0, axisDemand(preferred)));
            }
        }
        double best = 0.0;
        for (ControlAxis axis : ControlAxis.values()) {
            double value = axisDemand(axis);
            if (Math.abs(value) > Math.abs(best)) best = value;
        }
        return (float) Math.max(-1.0, Math.min(1.0, best));
    }

    private boolean hasSlotServing(ControlAxis axis) {
        return airframe.serves(axis);
    }

    private CraftFrame craftFrame() {
        if (cachedFrame == null || tickCount - cachedFrameTick >= SCAN_INTERVAL_TICKS) {
            cachedFrame = CraftFrame.of(this, consoleFacing(), devices);
            cachedFrameTick = tickCount;
        }
        return cachedFrame;
    }

    private net.minecraft.core.Direction consoleFacing() {
        return getBlockState().hasProperty(VccMk3Block.FACING)
                ? getBlockState().getValue(VccMk3Block.FACING)
                : net.minecraft.core.Direction.NORTH;
    }

    private RigSolution solveRig() {
        return RigSolution.solve(airframe, slotAssignments, devices, craftFrame());
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, VccMk3BlockEntity be) {
        be.prevEasedPitch = be.easedPitch;
        be.prevEasedRoll = be.easedRoll;
        be.prevEasedCommand = be.easedCommand;

        if (!Float.isNaN(be.renderPitch)) {
            be.easedPitch += (be.renderPitch - be.easedPitch) * RENDER_EASE;
            be.easedRoll += (be.renderRoll - be.easedRoll) * RENDER_EASE;
        }
        be.easedCommand += (be.renderCommand - be.easedCommand) * RENDER_EASE;
    }

    public boolean hasAttitude() {
        return !Float.isNaN(renderPitch);
    }

    public float renderPitch(float partialTick) {
        return prevEasedPitch + (easedPitch - prevEasedPitch) * partialTick;
    }

    public float renderRoll(float partialTick) {
        return prevEasedRoll + (easedRoll - prevEasedRoll) * partialTick;
    }

    public float renderCommand(float partialTick) {
        return prevEasedCommand + (easedCommand - prevEasedCommand) * partialTick;
    }

    public boolean isRenderAuto() {
        return renderAuto;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putFloat("RPitch", renderPitch);
        tag.putFloat("RRoll", renderRoll);
        tag.putFloat("RCommand", renderCommand);
        tag.putBoolean("RAuto", renderAuto);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        renderPitch = tag.contains("RPitch") ? tag.getFloat("RPitch") : Float.NaN;
        renderRoll = tag.contains("RRoll") ? tag.getFloat("RRoll") : Float.NaN;
        renderCommand = tag.getFloat("RCommand");
        renderAuto = tag.getBoolean("RAuto");
    }

    @Override
    public void onDataPacket(net.minecraft.network.Connection net,
                             net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket packet,
                             HolderLookup.Provider registries) {
        final CompoundTag tag = packet.getTag();
        if (tag != null) {
            handleUpdateTag(tag, registries);
        }
    }

    @Nullable
    @Override
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    public void setHudSubscribed(net.minecraft.server.level.ServerPlayer player, boolean subscribed) {
        if (player == null) return;
        if (subscribed) {
            hudSubscribers.add(player.getUUID());

            if (level instanceof ServerLevel serverLevel) broadcastHud(serverLevel);
        } else {
            hudSubscribers.remove(player.getUUID());
        }
    }

    public boolean isHudSubscribed(java.util.UUID playerId) {
        return hudSubscribers.contains(playerId);
    }

    private void broadcastHud(ServerLevel serverLevel) {
        com.createstats.vccmk3.alert.ConsoleAlert top = lastAlerts.isEmpty()
                ? com.createstats.vccmk3.alert.ConsoleAlert.NOMINAL : lastAlerts.get(0);

        VccMk3HudPacket packet = VccMk3HudPacket.of(worldPosition, craftName, armed, autoPilotEngaged,
                top.level().ordinal(), top.code(), top.message(), avionics);

        java.util.Iterator<UUID> iterator = hudSubscribers.iterator();
        while (iterator.hasNext()) {
            UUID id = iterator.next();
            net.minecraft.server.level.ServerPlayer player = serverLevel.getServer().getPlayerList().getPlayer(id);
            if (player == null) {
                iterator.remove();
                continue;
            }
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, packet);
        }
    }

    public void rescan() {
        if (level == null) return;
        HardwareScan.ScanResult result = HardwareScan.scan(
                level, inventory, ROW_COUNT, linkRowMask, devices, controllerId, worldPosition);
        devices = new ArrayList<>(result.devices());
        slots = new ArrayList<>(result.slots());

        // never hold a device that's gone

        Set<UUID> live = new LinkedHashSet<>();
        for (VccDevice device : devices) live.add(device.id());
        slotAssignments.keySet().retainAll(live);

        cachedFrame = null;
    }

    private Map<UUID, VccDevice> devicesById() {
        Map<UUID, VccDevice> map = new LinkedHashMap<>();
        for (VccDevice device : devices) map.put(device.id(), device);
        return map;
    }

    private Set<UUID> commandableDeviceIds() {
        Set<UUID> out = new LinkedHashSet<>();
        for (LinkSlot slot : slots) {
            if (slot.isCommandable() && slot.deviceId() != null) out.add(slot.deviceId());
        }
        return out;
    }

    private void applyBatch(ServerLevel serverLevel, CommandBatch batch) {
        boolean refresh = lastGovernorRefreshTick == Long.MIN_VALUE
                || tickCount - lastGovernorRefreshTick >= GOVERNOR_REFRESH_TICKS;
        if (refresh) lastGovernorRefreshTick = tickCount;

        Map<BlockPos, Integer> targets = new LinkedHashMap<>();
        for (VccDevice device : devices) {
            if (device.governorPos() != null) targets.put(device.governorPos(), 0);
        }
        targets.putAll(batch.rpmCommands());

        for (Map.Entry<BlockPos, Integer> entry : targets.entrySet()) {
            BlockPos governorPos = entry.getKey();
            int target = RigSolution.clampRpm(entry.getValue());
            if (target < 0 && !batch.allowsReverse(governorPos)) target = 0;

            Integer previous = lastGovernorCommand.get(governorPos);
            if (previous != null && previous == target && !refresh) continue;

            BlockEntity be = SableBridge.getBlockEntityOnContraption(serverLevel, governorPos);
            if (!(be instanceof RpmGovernorBE governor)) continue;
            if (governor.setCommand(worldPosition, controllerId(), target)) {
                lastGovernorCommand.put(governorPos, target);
            }
        }
        lastGovernorCommand.keySet().retainAll(targets.keySet());

        Map<BlockPos, Double> angleTargets = new LinkedHashMap<>();
        for (VccDevice device : devices) {
            if (device.category() == DeviceCategory.CONTROL_SURFACE
                    || device.category() == DeviceCategory.GYRO_BEARING) {
                angleTargets.put(device.actuatorPos(), 0.0);
            }
        }
        angleTargets.putAll(batch.angleCommands());
        for (Map.Entry<BlockPos, Double> entry : angleTargets.entrySet()) {
            applyAngle(serverLevel, entry.getKey(), entry.getValue());
        }

        long gameTime = serverLevel.getGameTime();
        for (VccDevice device : devices) {
            if (device.category() != DeviceCategory.WHEEL) continue;
            int signal = batch.steerCommands().getOrDefault(device.actuatorPos(), 0);
            int left = signal < 0 ? Math.min(15, -signal) : 0;
            int right = signal > 0 ? Math.min(15, signal) : 0;
            com.createstats.flight.runtime.WheelMountSteeringOverrides.setForTick(
                    device.actuatorPos(), left, right, gameTime);
        }

        applySignals(serverLevel, batch.signalCommands());
    }

    private void applyAngle(ServerLevel serverLevel, BlockPos pos, double degrees) {
        BlockEntity be = SableBridge.getBlockEntityOnContraption(serverLevel, pos);
        if (be == null) return;

        if (SableBridge.matchesSwivelBearing(be)) {
            SableBridge.writeSwivelTargetAngle(be, degrees);
            return;
        }

        SableBridge.PropData prop = SableBridge.readProp(be);
        if (prop == null || !prop.isGyro()) return;

        double radians = Math.toRadians(Math.max(-80.0, Math.min(80.0, degrees)));
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);
        double x = sin;
        double z = cos;
        double length = Math.sqrt(x * x + z * z);
        if (length < 1.0e-9) return;
        SableBridge.writeGyroTiltVector(be, x / length, 0.0, z / length);
    }

    private void zeroAllOutputs(ServerLevel serverLevel) {
        boolean any = false;
        for (Map.Entry<BlockPos, Integer> entry : new LinkedHashMap<>(lastGovernorCommand).entrySet()) {
            if (entry.getValue() == 0) continue;
            BlockEntity be = SableBridge.getBlockEntityOnContraption(serverLevel, entry.getKey());
            if (be instanceof RpmGovernorBE governor && governor.setCommand(worldPosition, controllerId(), 0)) {
                lastGovernorCommand.put(entry.getKey(), 0);
                any = true;
            }
        }
        if (!transmitters.isEmpty()) {
            applySignals(serverLevel, Map.of());
            any = true;
        }
        if (any) {
            DebugLogging.fccDebug("[VCC3@{}] outputs zeroed", worldPosition);
        }
    }

    private void applySignals(ServerLevel serverLevel, Map<Integer, Integer> commands) {
        refreshTransmitters(serverLevel);
        for (SignalTransmitter transmitter : new ArrayList<>(transmitters.values())) {
            int target = Math.max(0, Math.min(15, commands.getOrDefault(transmitter.row, 0)));
            if (!transmitter.setStrength(target)) continue;
            try {
                Create.REDSTONE_LINK_NETWORK_HANDLER.updateNetworkOf(serverLevel, transmitter);
            } catch (RuntimeException | LinkageError ex) {
                DebugLogging.fccDebug("[VCC3@{}] link update failed row={}", worldPosition, transmitter.row, ex);
            }
        }
    }

    private void refreshTransmitters(ServerLevel serverLevel) {
        Set<Integer> keep = new LinkedHashSet<>();
        for (int row = 0; row < ROW_COUNT; row++) {
            if (!isLinkRow(row)) continue;
            Couple<RedstoneLinkNetworkHandler.Frequency> key = frequencyFor(row);
            if (key == null) continue;
            keep.add(row);
            SignalTransmitter existing = transmitters.get(row);
            if (existing != null && existing.key.equals(key)) continue;
            if (existing != null) removeTransmitter(row);
            SignalTransmitter transmitter = new SignalTransmitter(row, key);
            transmitters.put(row, transmitter);
            try {
                Create.REDSTONE_LINK_NETWORK_HANDLER.addToNetwork(serverLevel, transmitter);
            } catch (RuntimeException | LinkageError ex) {
                transmitters.remove(row);
                DebugLogging.fccDebug("[VCC3@{}] link add failed row={}", worldPosition, row, ex);
            }
        }
        for (Integer row : new ArrayList<>(transmitters.keySet())) {
            if (!keep.contains(row)) removeTransmitter(row);
        }
    }

    private void removeTransmitter(int row) {
        SignalTransmitter transmitter = transmitters.remove(row);
        if (transmitter == null) return;
        transmitter.alive = false;
        if (level == null || level.isClientSide) return;
        try {
            Create.REDSTONE_LINK_NETWORK_HANDLER.removeFromNetwork(level, transmitter);
        } catch (RuntimeException | LinkageError ex) {
            DebugLogging.fccDebug("[VCC3@{}] link remove failed row={}", worldPosition, row, ex);
        }
    }

    @Nullable
    private Couple<RedstoneLinkNetworkHandler.Frequency> frequencyFor(int row) {
        ItemStack first = single(inventory.getStackInSlot(HardwareScan.actuatorSlot(row)));
        ItemStack second = single(inventory.getStackInSlot(HardwareScan.governorSlot(row)));
        if (first.isEmpty() && second.isEmpty()) return null;
        if (first.isEmpty()) {
            first = second;
            second = ItemStack.EMPTY;
        }
        return Couple.create(RedstoneLinkNetworkHandler.Frequency.of(first),
                RedstoneLinkNetworkHandler.Frequency.of(second));
    }

    private static ItemStack single(ItemStack stack) {
        if (stack.isEmpty()) return ItemStack.EMPTY;
        ItemStack copy = stack.copy();
        copy.setCount(1);
        return copy;
    }

    private final class SignalTransmitter implements IRedstoneLinkable {
        private final int row;
        private final Couple<RedstoneLinkNetworkHandler.Frequency> key;
        private int strength;
        private boolean alive = true;

        private SignalTransmitter(int row, Couple<RedstoneLinkNetworkHandler.Frequency> key) {
            this.row = row;
            this.key = key;
        }

        private boolean setStrength(int next) {
            if (strength == next) return false;
            strength = next;
            return true;
        }

        @Override
        public int getTransmittedStrength() {
            return isRxRow(row) ? 0 : strength;
        }

        @Override
        public void setReceivedStrength(int received) {
            if (!isRxRow(row)) return;
            rxStrength.put(row, Math.max(0, Math.min(15, received)));
        }

        @Override
        public boolean isListening() {
            return isRxRow(row);
        }

        @Override
        public boolean isAlive() {
            return alive && !VccMk3BlockEntity.this.isRemoved()
                    && level != null && !level.isClientSide && isLinkRow(row);
        }

        @Override
        public Couple<RedstoneLinkNetworkHandler.Frequency> getNetworkKey() {
            return key;
        }

        @Override
        public BlockPos getLocation() {
            return worldPosition;
        }
    }

    @Override
    public void setRemoved() {
        if (level instanceof ServerLevel serverLevel) {
            zeroAllOutputs(serverLevel);
        }
        for (Integer row : new ArrayList<>(transmitters.keySet())) removeTransmitter(row);
        super.setRemoved();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Version", NBT_VERSION);
        if (controllerId != null) tag.putUUID("ControllerId", controllerId);
        tag.putString("Airframe", airframe.name());
        if (!slotAssignments.isEmpty()) {
            ListTag assigned = new ListTag();
            for (Map.Entry<UUID, String> entry : slotAssignments.entrySet()) {
                CompoundTag one = new CompoundTag();
                one.putUUID("Device", entry.getKey());
                one.putString("Slot", entry.getValue());
                assigned.add(one);
            }
            tag.put("SlotAssignments", assigned);
        }
        tag.putBoolean("Armed", armed);
        tag.putInt("LinkRows", linkRowMask);
        if (!craftName.isEmpty()) tag.putString("CraftName", craftName);
        tag.put("Inventory", inventory.serializeNBT(registries));

        tag.put("Sensors", sensors.toNbt());
        tag.putDouble("FlipGuard", flipGuardDegrees);
        tag.putInt("LinkRx", linkRxMask);
        tag.putBoolean("AutoPilot", autoPilotEngaged);
        tag.putBoolean("AutoRoute", autoUseRoute);
        if (!rxBindings.isEmpty()) {
            ListTag rxTags = new ListTag();
            for (Map.Entry<Integer, ControlAxis> entry : rxBindings.entrySet()) {
                CompoundTag binding = new CompoundTag();
                binding.putInt("Row", entry.getKey());
                binding.putString("Axis", entry.getValue().name());
                rxTags.add(binding);
            }
            tag.put("RxBindings", rxTags);
        }

        if (!manualOverrides.isEmpty()) {
            ListTag overrideTags = new ListTag();
            for (Map.Entry<UUID, Integer> entry : manualOverrides.entrySet()) {
                CompoundTag override = new CompoundTag();
                override.putUUID("Id", entry.getKey());
                override.putInt("Rpm", entry.getValue());
                overrideTags.add(override);
            }
            tag.put("Overrides", overrideTags);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        controllerId = tag.hasUUID("ControllerId") ? tag.getUUID("ControllerId") : null;
        airframe = Airframe.safeValueOf(tag.getString("Airframe"), Airframe.FIXED_WING);
        slotAssignments.clear();
        if (tag.contains("SlotAssignments")) {
            ListTag assigned = tag.getList("SlotAssignments", Tag.TAG_COMPOUND);
            for (int i = 0; i < assigned.size(); i++) {
                CompoundTag one = assigned.getCompound(i);
                if (!one.hasUUID("Device")) continue;
                String slot = one.getString("Slot");

                if (airframe.hasSlot(slot)) slotAssignments.put(one.getUUID("Device"), slot);
            }
        }

        armed = false;
        linkRowMask = tag.getInt("LinkRows");
        craftName = tag.getString("CraftName");
        if (tag.contains("Inventory")) inventory.deserializeNBT(registries, tag.getCompound("Inventory"));

        sensors.fromNbt(tag.contains("Sensors") ? tag.getCompound("Sensors") : null);
        flipGuardDegrees = tag.contains("FlipGuard") ? tag.getDouble("FlipGuard") : 62.0;
        linkRxMask = tag.getInt("LinkRx");

        autoPilotEngaged = false;
        autoUseRoute = tag.getBoolean("AutoRoute");
        rxBindings.clear();
        rxStrength.clear();
        if (tag.contains("RxBindings", Tag.TAG_LIST)) {
            ListTag rxTags = tag.getList("RxBindings", Tag.TAG_COMPOUND);
            for (int i = 0; i < rxTags.size(); i++) {
                CompoundTag binding = rxTags.getCompound(i);
                if (binding.contains("Axis")) {
                    rxBindings.put(binding.getInt("Row"),
                            ControlAxis.safeValueOf(binding.getString("Axis"), ControlAxis.AUX));
                }
            }
        }

        manualOverrides.clear();
        if (tag.contains("Overrides", Tag.TAG_LIST)) {
            ListTag overrideTags = tag.getList("Overrides", Tag.TAG_COMPOUND);
            for (int i = 0; i < overrideTags.size(); i++) {
                CompoundTag override = overrideTags.getCompound(i);
                if (override.hasUUID("Id")) manualOverrides.put(override.getUUID("Id"), override.getInt("Rpm"));
            }
        }

        axisDemand.clear();
        lastScanTick = Long.MIN_VALUE;
    }

    public void bindSensor(String kind, @Nullable BlockPos pos) {
        SensorDiscovery.Kind resolved = switch (kind == null ? "" : kind.toLowerCase(java.util.Locale.ROOT)) {
            case "gimbal" -> SensorDiscovery.Kind.GIMBAL;
            case "altitude" -> SensorDiscovery.Kind.ALTITUDE;
            case "velocity" -> SensorDiscovery.Kind.VELOCITY;
            case "optical" -> SensorDiscovery.Kind.OPTICAL;
            default -> null;
        };
        if (resolved == null) return;
        sensors.bind(resolved, pos);
        setChanged();
    }

    public SensorDiscovery sensors() {
        return sensors;
    }

    public void setNotice(String message) {
        lastNotice = message == null ? "" : message;
        setChanged();
    }

    public String lastNotice() {
        return lastNotice;
    }

    public void setManualOverride(UUID deviceId, int rpm) {
        if (deviceId == null) return;
        if (rpm < 0) {
            manualOverrides.remove(deviceId);
        } else {
            manualOverrides.put(deviceId, RigSolution.clampRpm(rpm));
        }
        setChanged();
    }

    public void toggleDeviceEnabled(UUID deviceId) {
        for (int i = 0; i < devices.size(); i++) {
            VccDevice device = devices.get(i);
            if (!device.id().equals(deviceId)) continue;
            devices.set(i, device.withEnabled(!device.enabled()));
            setChanged();
            return;
        }
    }

    public int manualOverride(UUID deviceId) {
        return manualOverrides.getOrDefault(deviceId, -1);
    }

    public boolean hasManualOverrides() {
        return !manualOverrides.isEmpty();
    }

    public void clearManualOverrides() {
        if (manualOverrides.isEmpty()) return;
        manualOverrides.clear();
        setChanged();
    }

    private void applyManualOverrides(CommandBatch batch) {
        if (manualOverrides.isEmpty()) return;
        Map<UUID, VccDevice> byId = devicesById();
        for (Map.Entry<UUID, Integer> entry : manualOverrides.entrySet()) {
            VccDevice device = byId.get(entry.getKey());
            if (device == null || device.governorPos() == null) continue;
            batch.setRpm(device.governorPos(), entry.getValue());
        }
    }

    public boolean isRxRow(int row) {
        return row >= 0 && row < ROW_COUNT && (linkRxMask & (1 << row)) != 0;
    }

    public void toggleRxRow(int row) {
        if (row < 0 || row >= ROW_COUNT || !isLinkRow(row)) return;
        linkRxMask ^= (1 << row);
        rxStrength.remove(row);

        removeTransmitter(row);
        setChanged();
    }

    public void bindRxAxis(int row, @Nullable ControlAxis axis) {
        if (row < 0 || row >= ROW_COUNT) return;
        if (axis == null) {
            rxBindings.remove(row);
        } else {
            rxBindings.put(row, axis);
        }
        setChanged();
    }

    @Nullable
    public ControlAxis rxBinding(int row) {
        return rxBindings.get(row);
    }

    private void applyReceivedSignals() {
        if (rxBindings.isEmpty()) return;
        Map<ControlAxis, Double> received = new EnumMap<>(ControlAxis.class);
        for (Map.Entry<Integer, ControlAxis> entry : rxBindings.entrySet()) {
            int row = entry.getKey();
            if (!isRxRow(row)) continue;
            int strength = rxStrength.getOrDefault(row, 0);
            double fraction = Math.max(0.0, Math.min(1.0, strength / 15.0));
            received.merge(entry.getValue(), fraction, Double::max);
        }
        for (Map.Entry<ControlAxis, Double> entry : received.entrySet()) {
            setAxisDemand(entry.getKey(), entry.getValue());
        }
    }

    public boolean isAutoPilotEngaged() {
        return autoPilotEngaged;
    }

    public AutoPilot.Guidance guidance() {
        return guidance;
    }

    public void setAutoPilotEngaged(boolean engaged) {
        if (autoPilotEngaged == engaged) return;
        autoPilotEngaged = engaged;
        if (!engaged) {
            guidance = AutoPilot.Guidance.idle("Autopilot off");

            centreAutoPilotControls();
        }
        setChanged();
    }

    private void updateAutoPilot() {
        if (!autoPilotEngaged || level == null) {
            if (!autoPilotEngaged) {
                guidance = AutoPilot.Guidance.idle("Autopilot off");
                routeProgress = RouteFollower.Progress.inactive("Autopilot off");
            }
            return;
        }

        double thrustDemand;
        double yawDemand;

        if (autoUseRoute) {
            routeProgress = followRoute();
            guidance = AutoPilot.Guidance.idle(routeProgress.status());
            if (!routeProgress.active()) return;
            thrustDemand = routeProgress.thrustDemand();
            yawDemand = routeProgress.yawDemand();
        } else {
            BlockPos navPos = sensors.effective(SensorDiscovery.Kind.NAV_TABLE);
            BlockEntity navBe = navPos != null && level.isLoaded(navPos)
                    ? SableBridge.getBlockEntityOnContraption(level, navPos) : null;
            guidance = AutoPilot.compute(navBe, avionics, airframe == Airframe.GROUND,
                    craftWorldPosition());
            routeProgress = RouteFollower.Progress.inactive(guidance.status());
            if (!guidance.engaged()) return;
            thrustDemand = guidance.thrustDemand();
            yawDemand = guidance.yawDemand();
        }

        setAxisDemand(ControlAxis.FORWARD, thrustDemand);

        setAxisDemand(ControlAxis.YAW, yawDemand);
    }

    private RouteFollower.Progress followRoute() {
        BlockPos plannerPos = sensors.effective(SensorDiscovery.Kind.PATHFINDER);
        if (plannerPos == null || !level.isLoaded(plannerPos)) {
            return RouteFollower.Progress.inactive("No Pathfinder Computer on this craft");
        }
        BlockEntity be = SableBridge.getBlockEntityOnContraption(level, plannerPos);
        if (!(be instanceof com.createstats.pathfinder.blockentity.PathfinderComputerBE planner)) {
            return RouteFollower.Progress.inactive("No Pathfinder Computer on this craft");
        }

        net.minecraft.world.phys.Vec3 craftPos = craftWorldPosition();
        RouteFollower.Progress progress = RouteFollower.follow(
                planner.activeGuidance(), craftPos,
                avionics.headingDeg().present() ? avionics.headingDeg().value() : Double.NaN,
                avionics.groundSpeed().present() ? avionics.groundSpeed().value() : Double.NaN);

        planner.markFccGuidanceLink(worldPosition, progress.active(), progress.status(),
                progress.waypointIndex(), progress.waypointCount());
        return progress;
    }

    private net.minecraft.world.phys.Vec3 craftWorldPosition() {
        Object subLevel = SableBridge.subLevelAccessOf(this);
        if (subLevel != null) {
            org.joml.Vector3d world = SableBridge.localToWorld(subLevel, new org.joml.Vector3d(
                    worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5));
            if (world != null && Double.isFinite(world.x) && Double.isFinite(world.y) && Double.isFinite(world.z)) {
                return new net.minecraft.world.phys.Vec3(world.x, world.y, world.z);
            }
        }
        return net.minecraft.world.phys.Vec3.atCenterOf(worldPosition);
    }

    public boolean isAutoUsingRoute() {
        return autoUseRoute;
    }

    public RouteFollower.Progress routeProgress() {
        return routeProgress;
    }

    public void cycleAutoSource() {
        autoUseRoute = !autoUseRoute;
        centreAutoPilotControls();
        setChanged();
    }

    private void centreAutoPilotControls() {
        setAxisDemand(ControlAxis.FORWARD, 0.0);
        setAxisDemand(ControlAxis.YAW, 0.0);
    }

    // release shallower than engage or it chatters full lift on and off
    private void updateFlipGuard(ServerLevel serverLevel) {
        if (flipGuardDegrees <= 0.0) {
            flipGuardActive = false;
            return;
        }
        double tilt = sampleTiltDegrees();
        if (!Double.isFinite(tilt)) return;

        boolean worsening = Double.isFinite(lastTiltDegrees) && tilt > lastTiltDegrees + 0.15;
        lastTiltDegrees = tilt;

        if (flipGuardActive) {
            if (tilt <= flipGuardDegrees * 0.72) {
                flipGuardActive = false;
                DebugLogging.fccInfo("[VCC3@{}] flip guard released at {} deg", worldPosition, Math.round(tilt));
            }
            return;
        }
        if (tilt > flipGuardDegrees && worsening) {
            flipGuardActive = true;
            serverLevel.playSound(null, worldPosition, net.minecraft.sounds.SoundEvents.BELL_BLOCK,
                    net.minecraft.sounds.SoundSource.BLOCKS, 0.8f, 1.6f);
            DebugLogging.fccInfo("[VCC3@{}] flip guard engaged at {} deg", worldPosition, Math.round(tilt));
        }
    }

    private double sampleTiltDegrees() {
        Object subLevel = SableBridge.subLevelAccessOf(this);
        if (subLevel == null) return Double.NaN;
        org.joml.Quaterniond orientation = SableBridge.bodyOrientation(subLevel);
        org.joml.Vector3d gravity = SableBridge.gravityWorld(this);
        if (orientation == null || gravity == null || gravity.length() < 1.0e-6) return Double.NaN;

        org.joml.Vector3d worldUp = new org.joml.Vector3d(gravity).normalize().mul(-1.0);
        org.joml.Vector3d craftUp = new org.joml.Quaterniond(orientation)
                .transform(new org.joml.Vector3d(0.0, 1.0, 0.0));
        if (craftUp.length() < 1.0e-6) return Double.NaN;
        craftUp.normalize();
        double dot = Math.max(-1.0, Math.min(1.0, craftUp.dot(worldUp)));
        return Math.toDegrees(Math.acos(dot));
    }

    private void forceRecoveryLift(RigSolution solution, CommandBatch batch) {
        Map<ControlAxis, Double> recovery = new EnumMap<>(ControlAxis.class);
        recovery.put(ControlAxis.UP, 1.0);
        solution.dispatch(recovery, commandableDeviceIds(), batch);
    }

    public int kineticallyDrivenSurfaces() {
        if (level == null) return 0;
        int count = 0;
        for (VccDevice device : devices) {
            if (device.category() != DeviceCategory.CONTROL_SURFACE) continue;
            if (!level.isLoaded(device.actuatorPos())) continue;
            BlockEntity be = SableBridge.getBlockEntityOnContraption(level, device.actuatorPos());
            Double speed = SableBridge.swivelKineticSpeed(be);
            if (speed != null && Math.abs(speed) > 0.5) count++;
        }
        return count;
    }

    public boolean isFlipGuardActive() {
        return flipGuardActive;
    }

    public double flipGuardDegrees() {
        return flipGuardDegrees;
    }

    public void setFlipGuardDegrees(double degrees) {
        flipGuardDegrees = Double.isFinite(degrees) ? Math.max(0.0, Math.min(90.0, degrees)) : 0.0;
        setChanged();
    }

    @Override
    public com.createstats.power.FeConsumer feConsumer() {
        return feConsumer;
    }

    public int routesApplied() {
        return lastRoutesApplied;
    }

    public int routesUnsupported() {
        return lastRoutesUnsupported;
    }

    @Override
    public Component getDisplayName() {
        return craftName.isEmpty()
                ? Component.translatable("block." + CreateStats.MOD_ID + ".vcc_mk3")
                : Component.literal(craftName);
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new VccMk3Menu(containerId, playerInventory, this);
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        tooltip.add(Component.literal("  ").append(
                Component.literal("VCC Mk III").withStyle(ChatFormatting.GRAY)));

        if (feConsumer.requiresPower()) {
            int percent = Math.round(feConsumer.fillPct());
            ChatFormatting colour = !feConsumer.isPowered() ? ChatFormatting.RED
                    : feConsumer.isBrownout() ? ChatFormatting.GOLD : ChatFormatting.GREEN;
            String label = feConsumer.isPowered()
                    ? String.format(java.util.Locale.ROOT, "Power: %d %% (%,d / %,d FE)",
                            percent, feConsumer.getEnergyStored(), feConsumer.getMaxEnergyStored())
                    : "Power: OFFLINE (buffer empty)";
            tooltip.add(Component.literal("    ").append(Component.literal(label).withStyle(colour)));
        }

        tooltip.add(Component.literal("    ").append(Component.literal(armed ? "ARMED" : "SAFE")
                .withStyle(armed ? ChatFormatting.RED : ChatFormatting.GREEN)));

        if (flipGuardActive) {
            tooltip.add(Component.literal("    ").append(
                    Component.literal("FLIP GUARD ACTIVE, lift only").withStyle(ChatFormatting.RED)));
        }

        int ready = 0;
        int faults = 0;
        for (LinkSlot slot : slots) {
            if (slot.isCommandable()) ready++;
            else if (slot.needsAttention()) faults++;
        }

        if (isPlayerSneaking || ready == 0) {
            tooltip.add(Component.literal("    ").append(Component.literal(
                            ready + " hardware links ready")
                    .withStyle(ready > 0 ? ChatFormatting.WHITE : ChatFormatting.DARK_GRAY)));
        }
        if (faults > 0) {
            tooltip.add(Component.literal("    ").append(Component.literal(
                            faults + " link" + (faults == 1 ? "" : "s") + " need attention")
                    .withStyle(ChatFormatting.GOLD)));
        }

        if (isPlayerSneaking || !avionics.hasAnySensor()) {
            final String provenance = avionics.hasAnySensor() ? "reading real sensors"
                    : avionics.isBlank() ? "no telemetry" : "estimated from physics, no sensors installed";
            tooltip.add(Component.literal("    ").append(Component.literal("Avionics: " + provenance)
                    .withStyle(avionics.hasAnySensor() ? ChatFormatting.AQUA : ChatFormatting.GRAY)));
        }

        if (!manualOverrides.isEmpty()) {
            tooltip.add(Component.literal("    ").append(Component.literal(
                            manualOverrides.size() + " manual override"
                                    + (manualOverrides.size() == 1 ? "" : "s") + " active")
                    .withStyle(ChatFormatting.LIGHT_PURPLE)));
        }
        if (!isPlayerSneaking) {
            tooltip.add(com.createstats.power.FeGoggle.more());
        }
        return true;
    }

    public Map<DeviceCategory, Integer> deviceCensus() {
        Map<DeviceCategory, Integer> census = new LinkedHashMap<>();
        for (VccDevice device : devices) {
            census.merge(device.category(), 1, Integer::sum);
        }
        return census;
    }
}
