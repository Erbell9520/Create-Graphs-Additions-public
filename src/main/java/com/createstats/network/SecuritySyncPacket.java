package com.createstats.network;

import com.createstats.blockentity.HomeSecurityPanelBE;
import com.createstats.power.SecurityDevice;
import com.createstats.security.SecurityLog;
import com.createstats.security.SecurityRole;
import com.createstats.security.SecurityRoster;
import com.createstats.security.SecuritySegment;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// polled not tracked, it's a whole circuit
public record SecuritySyncPacket(BlockPos panelPos, boolean awake, boolean failSecure, int proximity,
                                 String house, int viewerRole, int mode, boolean lockdown,
                                 String lockdownCause, boolean released, boolean hazard,
                                 String hazardCause, boolean configured, boolean hasPassword,
                                 boolean alwaysAsk, int panels, int sleeping, String houseRaw,
                                 List<Candidate> joinable, List<Online> online, List<Row> devices,
                                 List<Person> people, List<Line> log, List<RelayRow> relays,
                                 int hiddenRows, int legacyUnresolved, List<CoilRow> coils,
                                 boolean pvp) implements CustomPacketPayload {
    public record Candidate(UUID id, String house, boolean noPanels, boolean locked,
                            boolean joinable, boolean removable) {
    }

    public record Online(UUID id, String name) {
    }

    public com.createstats.security.SecurityMode modeOf() {
        return com.createstats.security.SecurityMode.byOrdinal(mode);
    }

    public record Row(BlockPos pos, String name, int deviceClass, int state, float integrity,
                      int required, String bearing, boolean manual, int units,
                      int sensorKind, boolean armed, int range, boolean entrance, boolean named) {
        public boolean paired() {
            return units > 1;
        }

        // -1 ordinal, saves a presence boolean per row
        public com.createstats.security.SensorKind sensorKindOf() {
            return sensorKind < 0 ? null : com.createstats.security.SensorKind.byOrdinal(sensorKind);
        }

        public boolean isShutter() {
            return classOf() == SecurityDevice.DeviceClass.SHUTTER;
        }

        public boolean isLocker() {
            return classOf() == SecurityDevice.DeviceClass.LOCKER;
        }

        public boolean isSensor() {
            return classOf() == SecurityDevice.DeviceClass.SENSOR;
        }

        public boolean isRepeller() {
            return classOf() == SecurityDevice.DeviceClass.REPELLER;
        }

        public boolean isCoil() {
            return classOf() == SecurityDevice.DeviceClass.COIL;
        }

        public boolean clearanceGated() {
            return !isHazard() && !isShutter() && !isRelay();
        }

        public boolean isRelay() {
            return classOf() == SecurityDevice.DeviceClass.RELAY;
        }

        public boolean isHazard() {
            final com.createstats.security.SensorKind kind = sensorKindOf();
            return kind != null && kind.isHazard();
        }

        public SecurityDevice.DeviceClass classOf() {
            return SecurityDevice.DeviceClass.byOrdinal(deviceClass);
        }

        public SecurityDevice.DeviceState stateOf() {
            return SecurityDevice.DeviceState.byOrdinal(state);
        }

        public boolean damageable() {
            return integrity >= 0.0f;
        }
    }

    public record RelayRow(BlockPos pos, int contact, boolean powered, int output, boolean subscribed,
                           String condition, String behavior, int pulse, String mode, BlockPos scope,
                           boolean scoped, long revision, boolean pending, int pulseLeft, int truth,
                           int sources, int unavailable, int sinceTrigger) {
    }

    public record CoilRow(BlockPos pos, boolean loaded, boolean governed, int rules, boolean following,
                          boolean on, boolean locked, boolean hears, int status, int stage, int volleys,
                          int volleysMax, int strikes, int strikesMax, int sinceArc, String lastArc,
                          String blockedBy, boolean engaged) {
        public com.createstats.stormcoil.CoilRules.Grid grid() {
            return new com.createstats.stormcoil.CoilRules.Grid(this.rules);
        }

        public com.createstats.security.CoilStatus statusOf() {
            return com.createstats.security.CoilStatus.byOrdinal(this.status);
        }
    }

    public record Person(UUID id, String name, int role, int level, int opens) {
        public SecurityRole roleOf() {
            return SecurityRole.byOrdinal(role);
        }

        public boolean isOwner() {
            return roleOf() == SecurityRole.OWNER;
        }
    }

    public record Line(long gameTime, String who, String target, String detail, int verdict) {
        public SecurityLog.Verdict verdictOf() {
            return SecurityLog.Verdict.byOrdinal(verdict);
        }
    }

    public static final CustomPacketPayload.Type<SecuritySyncPacket> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath("create_stats", "security_sync"));

    public static final StreamCodec<FriendlyByteBuf, SecuritySyncPacket> STREAM_CODEC =
            StreamCodec.of(SecuritySyncPacket::write, SecuritySyncPacket::read);

    private static void utf(FriendlyByteBuf buf, String text, int max) {
        buf.writeUtf(SecurityLog.clip(text, max), max);
    }

    private static void write(FriendlyByteBuf buf, SecuritySyncPacket pkt) {
        buf.writeBlockPos(pkt.panelPos());
        buf.writeBoolean(pkt.awake());
        buf.writeBoolean(pkt.failSecure());
        buf.writeVarInt(pkt.proximity());
        utf(buf, pkt.house(), 32);
        buf.writeVarInt(pkt.viewerRole());
        buf.writeVarInt(pkt.mode());
        buf.writeBoolean(pkt.lockdown());
        utf(buf, pkt.lockdownCause(), 64);
        buf.writeBoolean(pkt.released());
        buf.writeBoolean(pkt.hazard());
        utf(buf, pkt.hazardCause(), 64);
        buf.writeBoolean(pkt.configured());

        buf.writeBoolean(pkt.hasPassword());
        buf.writeBoolean(pkt.alwaysAsk());
        buf.writeVarInt(pkt.panels());
        buf.writeVarInt(pkt.sleeping());

        utf(buf, pkt.houseRaw(), 32);

        buf.writeVarInt(pkt.joinable().size());
        for (Candidate c : pkt.joinable()) {
            buf.writeUUID(c.id());
            utf(buf, c.house(), 32);
            buf.writeBoolean(c.noPanels());
            buf.writeBoolean(c.locked());
            buf.writeBoolean(c.joinable());
            buf.writeBoolean(c.removable());
        }

        buf.writeVarInt(pkt.online().size());
        for (Online o : pkt.online()) {
            buf.writeUUID(o.id());
            utf(buf, o.name(), 32);
        }

        buf.writeVarInt(pkt.devices().size());
        for (Row r : pkt.devices()) {
            buf.writeBlockPos(r.pos());
            utf(buf, r.name(), 64);
            buf.writeVarInt(r.deviceClass());
            buf.writeVarInt(r.state());
            buf.writeFloat(r.integrity());
            buf.writeVarInt(r.required());
            utf(buf, r.bearing(), 16);
            buf.writeBoolean(r.manual());
            buf.writeVarInt(r.units());
            buf.writeVarInt(r.sensorKind() + 1);
            buf.writeBoolean(r.armed());
            buf.writeVarInt(r.range());
            buf.writeBoolean(r.entrance());
            buf.writeBoolean(r.named());
        }

        buf.writeVarInt(pkt.people().size());
        for (Person p : pkt.people()) {
            buf.writeUUID(p.id());
            utf(buf, p.name(), 32);
            buf.writeVarInt(p.role());
            buf.writeVarInt(p.level());
            buf.writeVarInt(p.opens());
        }

        buf.writeVarInt(pkt.log().size());
        for (Line l : pkt.log()) {
            buf.writeVarLong(l.gameTime());
            utf(buf, l.who(), SecurityLog.MAX_WHO);
            utf(buf, l.target(), SecurityLog.MAX_TARGET);
            utf(buf, l.detail(), SecurityLog.MAX_DETAIL);
            buf.writeVarInt(l.verdict());
        }

        buf.writeVarInt(pkt.relays().size());
        for (RelayRow r : pkt.relays()) {
            buf.writeBlockPos(r.pos());
            buf.writeVarInt(r.contact() + 1);
            buf.writeBoolean(r.powered());
            buf.writeVarInt(r.output());
            buf.writeBoolean(r.subscribed());
            utf(buf, r.condition(), 64);
            utf(buf, r.behavior(), 16);
            buf.writeVarInt(r.pulse());
            utf(buf, r.mode(), 16);
            buf.writeBlockPos(r.scope());
            buf.writeBoolean(r.scoped());
            buf.writeVarLong(r.revision());
            buf.writeBoolean(r.pending());
            buf.writeVarInt(r.pulseLeft());
            buf.writeVarInt(r.truth());
            buf.writeVarInt(r.sources());
            buf.writeVarInt(r.unavailable());
            buf.writeVarInt(r.sinceTrigger() + 1);
        }
        buf.writeVarInt(pkt.hiddenRows());
        buf.writeVarInt(pkt.legacyUnresolved());

        buf.writeVarInt(pkt.coils().size());
        for (CoilRow c : pkt.coils()) {
            buf.writeBlockPos(c.pos());
            buf.writeBoolean(c.loaded());
            buf.writeBoolean(c.governed());
            buf.writeVarInt(c.rules());
            buf.writeBoolean(c.following());
            buf.writeBoolean(c.on());
            buf.writeBoolean(c.locked());
            buf.writeBoolean(c.hears());
            buf.writeVarInt(c.status());
            buf.writeVarInt(c.stage());
            buf.writeVarInt(c.volleys());
            buf.writeVarInt(c.volleysMax());
            buf.writeVarInt(c.strikes());
            buf.writeVarInt(c.strikesMax());
            buf.writeVarInt(c.sinceArc() + 1);
            utf(buf, c.lastArc(), 48);
            utf(buf, c.blockedBy(), 48);
            buf.writeBoolean(c.engaged());
        }

        buf.writeBoolean(pkt.pvp());
    }

    private static SecuritySyncPacket read(FriendlyByteBuf buf) {
        final BlockPos pos = buf.readBlockPos();
        final boolean awake = buf.readBoolean();
        final boolean failSecure = buf.readBoolean();
        final int proximity = buf.readVarInt();
        final String house = buf.readUtf(32);
        final int viewerRole = buf.readVarInt();
        final int mode = buf.readVarInt();
        final boolean lockdown = buf.readBoolean();
        final String lockdownCause = buf.readUtf(64);
        final boolean released = buf.readBoolean();
        final boolean hazard = buf.readBoolean();
        final String hazardCause = buf.readUtf(64);
        final boolean configured = buf.readBoolean();
        final boolean hasPassword = buf.readBoolean();
        final boolean alwaysAsk = buf.readBoolean();
        final int panels = buf.readVarInt();
        final int sleeping = buf.readVarInt();
        final String houseRaw = buf.readUtf(32);

        final int joinCount = buf.readVarInt();
        final List<Candidate> joinable = new ArrayList<>(Math.max(0, Math.min(joinCount, 64)));
        for (int i = 0; i < joinCount; i++) {
            joinable.add(new Candidate(buf.readUUID(), buf.readUtf(32), buf.readBoolean(),
                    buf.readBoolean(), buf.readBoolean(), buf.readBoolean()));
        }

        final int onlineCount = buf.readVarInt();
        final List<Online> online = new ArrayList<>(Math.max(0, Math.min(onlineCount, 64)));
        for (int i = 0; i < onlineCount; i++) {
            online.add(new Online(buf.readUUID(), buf.readUtf(32)));
        }

        final int deviceCount = buf.readVarInt();
        final List<Row> devices = new ArrayList<>(Math.max(0, Math.min(deviceCount, SecuritySegment.MAX_ROWS)));
        for (int i = 0; i < deviceCount; i++) {
            devices.add(new Row(buf.readBlockPos(), buf.readUtf(64), buf.readVarInt(),
                    buf.readVarInt(), buf.readFloat(), buf.readVarInt(), buf.readUtf(16),
                    buf.readBoolean(), buf.readVarInt(),

                    buf.readVarInt() - 1, buf.readBoolean(), buf.readVarInt(), buf.readBoolean(),
                    buf.readBoolean()));
        }

        final int personCount = buf.readVarInt();
        final List<Person> people = new ArrayList<>(Math.max(0, Math.min(personCount, SecurityRoster.MAX_ENTRIES)));
        for (int i = 0; i < personCount; i++) {
            people.add(new Person(buf.readUUID(), buf.readUtf(32), buf.readVarInt(),
                    buf.readVarInt(), buf.readVarInt()));
        }

        final int lineCount = buf.readVarInt();
        final List<Line> log = new ArrayList<>(Math.max(0, Math.min(lineCount, SecurityLog.SENT)));
        for (int i = 0; i < lineCount; i++) {
            log.add(new Line(buf.readVarLong(), buf.readUtf(SecurityLog.MAX_WHO),
                    buf.readUtf(SecurityLog.MAX_TARGET), buf.readUtf(SecurityLog.MAX_DETAIL),
                    buf.readVarInt()));
        }

        final int relayCount = buf.readVarInt();
        final List<RelayRow> relays = new ArrayList<>(Math.max(0, Math.min(relayCount, SecuritySegment.MAX_ROWS)));
        for (int i = 0; i < relayCount; i++) {
            relays.add(new RelayRow(buf.readBlockPos(), buf.readVarInt() - 1, buf.readBoolean(), buf.readVarInt(),
                    buf.readBoolean(), buf.readUtf(64), buf.readUtf(16), buf.readVarInt(), buf.readUtf(16),
                    buf.readBlockPos(), buf.readBoolean(), buf.readVarLong(), buf.readBoolean(), buf.readVarInt(),
                    buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt() - 1));
        }
        final int hidden = buf.readVarInt();
        final int legacy = buf.readVarInt();

        final int coilCount = buf.readVarInt();
        final List<CoilRow> coils = new ArrayList<>(Math.max(0, Math.min(coilCount, SecuritySegment.MAX_ROWS)));
        for (int i = 0; i < coilCount; i++) {
            coils.add(new CoilRow(buf.readBlockPos(), buf.readBoolean(), buf.readBoolean(), buf.readVarInt(),
                    buf.readBoolean(), buf.readBoolean(), buf.readBoolean(), buf.readBoolean(), buf.readVarInt(),
                    buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                    buf.readVarInt() - 1, buf.readUtf(48), buf.readUtf(48), buf.readBoolean()));
        }
        final boolean pvp = buf.readBoolean();

        return new SecuritySyncPacket(pos, awake, failSecure, proximity, house, viewerRole,
                mode, lockdown, lockdownCause, released, hazard, hazardCause,
                configured, hasPassword, alwaysAsk, panels, sleeping, houseRaw, joinable, online,
                devices, people, log, relays, hidden, legacy, coils, pvp);
    }

    public static SecuritySyncPacket of(HomeSecurityPanelBE panel, ServerPlayer viewer) {
        final List<SecuritySegment.Device> found = panel.unionDevices();
        final SecurityRoster roster = panel.roster();

        final List<Row> rows = new ArrayList<>(Math.min(found.size(), 8 * SecuritySegment.MAX_ROWS));

        final int[] perClass = new int[SecurityDevice.DeviceClass.all().length];
        int hidden = 0;
        for (SecuritySegment.Device d : found) {
            if (perClass[d.deviceClass().ordinal()]++ >= SecuritySegment.MAX_ROWS) {
                hidden++;
                continue;
            }

            int kindOrdinal = -1;
            boolean armed = false;

            int range = d.deviceClass() == SecurityDevice.DeviceClass.DOOR
                    ? panel.proximityOverride(d.pos()) : 0;
            if (d.deviceClass() == SecurityDevice.DeviceClass.SENSOR
                    && panel.getLevel() != null
                    && panel.getLevel().getBlockEntity(d.pos())
                            instanceof com.createstats.blockentity.SecuritySensorBE sensor) {
                final com.createstats.security.SensorKind kind = sensor.kind();
                kindOrdinal = kind.ordinal();
                armed = panel.sensorArmed(kind, d.pos());
                range = panel.sensorRadius(d.pos(), kind);
            }
            rows.add(new Row(d.pos(), d.name(), d.deviceClass().ordinal(), d.state().ordinal(),
                    d.integrity(), d.required(), d.bearing(), d.manual(), d.units(),
                    kindOrdinal, armed, range, panel.isEntrance(d.pos()), d.named()));
        }

        final List<Person> people = new ArrayList<>(roster.size());
        for (SecurityRoster.Entry e : roster.sorted()) {
            int opens = 0;
            for (Row r : rows) {
                if (r.clearanceGated() && roster.passes(e.id(), r.required())) {
                    opens++;
                }
            }
            people.add(new Person(e.id(), e.name(), e.role().ordinal(), e.level(), opens));
        }

        final List<Line> log = new ArrayList<>();
        for (SecurityLog.Event e : panel.log().recent(SecurityLog.SENT)) {
            log.add(new Line(e.gameTime(), e.who(), e.target(), e.detail(), e.verdict().ordinal()));
        }

        final List<Candidate> joinable = new ArrayList<>();
        for (com.createstats.security.SecurityHousehold h : panel.joinable(viewer)) {
            final boolean noPanels = h.panels().isEmpty();
            final boolean mayJoin = panel.roleOf(viewer).mayEditPanel()
                    && h.roster().isOwner(viewer.getUUID());
            final boolean mayRemove = noPanels
                    && (h.roster().isOwner(viewer.getUUID()) || viewer.hasPermissions(2));
            joinable.add(new Candidate(h.id(), h.house(), noPanels, h.hasPassword(), mayJoin,
                    mayRemove));
        }

        final List<Online> online = new ArrayList<>();
        if (viewer.getServer() != null) {
            for (ServerPlayer p : viewer.getServer().getPlayerList().getPlayers()) {
                if (roster.find(p.getUUID()) == null) {
                    online.add(new Online(p.getUUID(), p.getGameProfile().getName()));
                }

                if (online.size() >= 64) {
                    break;
                }
            }
        }

        final List<RelayRow> relays = relayRows(panel, rows);
        final List<CoilRow> coils = coilRows(panel, rows);
        final boolean pvp = viewer.getServer() != null && viewer.getServer().isPvpAllowed();

        return new SecuritySyncPacket(panel.getBlockPos(), panel.isAwake(), panel.failSecure(),
                panel.proximity(), panel.houseName().getString(),
                panel.roleOf(viewer).ordinal(), panel.mode().ordinal(), panel.lockedDown(),
                panel.lockdownCause(), panel.released(), panel.hazard(), panel.hazardCause(),
                panel.configured(), panel.hasPassword(), panel.alwaysAsk(),
                panel.panelCount(), panel.sleepingPanels(), panel.houseRaw(), joinable, online,
                rows, people, log, relays, hidden, panel.legacyUnresolved(), coils, pvp);
    }

    private static List<RelayRow> relayRows(HomeSecurityPanelBE panel, List<Row> rows) {
        final List<RelayRow> out = new ArrayList<>();
        final net.minecraft.world.level.Level level = panel.getLevel();
        final long now = level == null ? 0L : level.getGameTime();
        for (Row r : rows) {
            if (!r.isRelay()) {
                continue;
            }
            final com.createstats.security.RelayDevice block = level != null && level.isLoaded(r.pos())
                    && level.getBlockEntity(r.pos()) instanceof com.createstats.security.RelayDevice found
                    ? found : null;
            final com.createstats.security.relay.RelaySubscription sub = panel.relayAt(r.pos());
            final int contact = block == null ? -1 : block.contact().ordinal();
            final boolean powered = block != null && block.powered();
            final int output = block == null ? 0 : block.output();
            if (sub == null) {
                out.add(new RelayRow(r.pos(), contact, powered, output, false, "", "", 0, "", r.pos(), false, 0L,
                        false, 0, com.createstats.security.Truth.UNKNOWN.ordinal(), 0, 0, -1));
                continue;
            }
            final com.createstats.security.relay.RelayConfig c = sub.config();
            final com.createstats.security.relay.RelayState s = sub.state();
            final boolean scoped = c.scope() != null;
            final BlockPos scope = scoped ? BlockPos.of(c.scope().pos()) : r.pos();
            final int pulseLeft = c.behavior() == com.createstats.security.relay.RelayBehavior.PULSE
                    && s.pulseUntil() != com.createstats.security.relay.RelayState.NEVER && now < s.pulseUntil()
                    ? (int) Math.min(com.createstats.security.relay.RelayConfig.MAX_PULSE, s.pulseUntil() - now) : 0;
            final long since = s.ticksSinceTrigger(now);
            out.add(new RelayRow(r.pos(), contact, powered, output, true, c.conditionId(), c.behavior().key(),
                    c.pulseTicks(), c.mode(), scope, scoped, sub.revision(), s.pending(), pulseLeft,
                    sub.reading().truth().ordinal(), sub.reading().sources(), sub.reading().unavailable(),
                    (int) Math.min(Integer.MAX_VALUE, since)));
        }
        return out;
    }

    private static List<CoilRow> coilRows(HomeSecurityPanelBE panel, List<Row> rows) {
        final List<CoilRow> out = new ArrayList<>();
        final net.minecraft.world.level.Level level = panel.getLevel();
        final long now = level == null ? 0L : level.getGameTime();
        final com.createstats.stormcoil.CoilRules.HouseState state = panel.houseState();
        for (Row r : rows) {
            if (!r.isCoil()) {
                continue;
            }
            final net.minecraft.world.level.block.entity.BlockEntity be = level != null && level.isLoaded(r.pos())
                    ? level.getBlockEntity(r.pos()) : null;
            final com.createstats.security.CoilReading reading =
                    be instanceof com.createstats.security.CoilDevice coil ? coil.coilReading() : null;
            final SecurityDevice device = com.createstats.security.SecurityDevices.of(be);
            final boolean governed = device != null && panel.householdId() != null
                    && panel.householdId().equals(device.governingHousehold());
            final com.createstats.stormcoil.CoilRules.Grid held = panel.coilRules(r.pos());
            final com.createstats.stormcoil.CoilRules.Grid grid = held != null ? held
                    : reading != null ? com.createstats.stormcoil.CoilRules.Grid.ofMode(reading.targets())
                    : com.createstats.stormcoil.CoilRules.Grid.NOTHING;
            final boolean on = panel.coilOn(r.pos());
            final boolean hears = panel.coilHearsRedstone(r.pos());
            final com.createstats.security.CoilStatus status = com.createstats.security.CoilStatus.of(on, hears,
                    reading, grid.rule(state).arcsAtAnything());
            final long since = reading == null ? -1L : reading.sinceArc(now);
            out.add(new CoilRow(r.pos(), reading != null, governed, grid.bits(), held == null, on,
                    panel.coilLocked(r.pos()), hears, status.ordinal(),
                    reading == null ? 0 : reading.stage(), reading == null ? 0 : reading.volleys(),
                    reading == null ? 0 : reading.volleysMax(), reading == null ? 0 : reading.strikes(),
                    reading == null ? 0 : reading.strikesMax(), (int) Math.min(Integer.MAX_VALUE, since),
                    reading == null ? "" : reading.lastArc(), reading == null ? "" : reading.blockedBy(),
                    reading != null && reading.engaged()));
        }
        return out;
    }

    public SecurityRole viewerRoleOf() {
        return SecurityRole.byOrdinal(viewerRole);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SecuritySyncPacket packet, IPayloadContext context) {
        context.enqueueWork(() ->
                com.createstats.util.ClientScreenAccess.acceptSecurityRoster(packet));
    }
}
