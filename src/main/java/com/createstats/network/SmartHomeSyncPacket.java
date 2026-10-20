package com.createstats.network;

import com.createstats.blockentity.SmartHomePanelBE;
import com.createstats.home.HouseModel;
import com.createstats.home.RoomFill;
import com.createstats.home.SceneDiff;
import com.createstats.power.ApplianceStatus;
import com.createstats.power.HomeSegment;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record SmartHomeSyncPacket(BlockPos panelPos, boolean awake, List<Integer> storeys, int panelStorey,
                                  int minX, int minZ, int maxX, int maxZ, List<Row> rows, int dropped,
                                  List<Hint> unlisted, List<Player> players, int[] dayChart, int hourOfDay,
                                  int revision, @Nullable Config config, @Nullable List<Wire> wiring)
        implements CustomPacketPayload {
    private static final int MAX_MODES = 16;
    private static final int MAX_PLUGGED = HomeSegment.MAX_PLUGGED;

    public record Row(List<BlockPos> members, int category, Component name, boolean manual,
                      int state, int mode, List<Component> modes, int drawFe, List<Row> plugged,
                      int floorY, int colour, @Nullable Component status, float progress, boolean running,
                      boolean standby, int roomId) {
        public BlockPos anchor() {
            return members.get(0);
        }
    }

    public record Hint(BlockPos pos, Component name, int floorY) {
    }

    public record Player(String name, int x, int z, float yaw, int floorY) {
    }

    public record Wire(BlockPos pos, int flow) {
    }

    public record Room(int id, String name, String icon, int tint, int floorY, long[] cells, boolean follow) {
    }

    public record Scene(String name, String icon, Map<Long, SceneDiff.State> states) {
    }

    public record Config(List<Room> rooms, List<Scene> scenes, int bedtime, int away, int home, boolean toasts,
                        int lastScene) {
    }

    public static final CustomPacketPayload.Type<SmartHomeSyncPacket> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath("create_stats", "smart_home_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SmartHomeSyncPacket> STREAM_CODEC =
            StreamCodec.of(SmartHomeSyncPacket::encode, SmartHomeSyncPacket::decode);

    public static SmartHomeSyncPacket of(SmartHomePanelBE panel, int knownRevision, boolean wantWiring) {
        final HouseModel house = panel.house();
        final int[] foot = panel.footprint();
        final List<Hint> hints = new ArrayList<>();
        for (HomeSegment.Hint h : panel.unlisted()) {
            hints.add(new Hint(h.pos(), h.name(), h.floorY()));
        }
        final List<Player> players = new ArrayList<>();
        for (SmartHomePanelBE.PlayerMark p : panel.players()) {
            players.add(new Player(p.name(), p.x(), p.z(), p.yaw(), p.floorY()));
        }
        List<Wire> wires = null;
        if (wantWiring) {
            wires = new ArrayList<>();
            for (SmartHomePanelBE.WireNode w : panel.wiring()) {
                wires.add(new Wire(w.pos(), w.flow()));
            }
        }
        return new SmartHomeSyncPacket(panel.getBlockPos(), panel.isAwake(), List.copyOf(panel.storeys()),
                panel.panelStorey(), foot[0], foot[1], foot[2], foot[3], rowsOf(panel.roster(), house),
                panel.rosterDropped(), hints, players, panel.dayChart(), panel.hourOfDay(), house.revision(),
                knownRevision == house.revision() ? null : configOf(house), wires);
    }

    private static List<Row> rowsOf(List<HomeSegment.Device> devices, HouseModel house) {
        final List<Row> rows = new ArrayList<>(devices.size());
        for (HomeSegment.Device d : devices) {
            final HouseModel.Room room = house.roomOf(d.anchor().getX(), d.anchor().getZ(), d.floorY());
            final ApplianceStatus s = d.status();
            rows.add(new Row(d.members(), d.category().ordinal(), d.name(), d.manual(), d.state().ordinal(),
                    d.mode(), d.modes(), d.drawFe(), rowsOf(d.plugged(), house), d.floorY(), d.colour(),
                    s == null ? null : s.text(), s == null ? ApplianceStatus.NO_PROGRESS : s.progress(),
                    s != null && s.running(), SmartHomePanelBE.isStandby(d), room == null ? -1 : room.id()));
        }
        return rows;
    }

    private static Config configOf(HouseModel house) {
        final List<Room> rooms = new ArrayList<>();
        for (HouseModel.Room r : house.rooms()) {
            final long[] cells = new long[r.cells().size()];
            int i = 0;
            for (long c : r.cells()) {
                cells[i++] = c;
            }
            rooms.add(new Room(r.id(), r.name(), r.icon(), r.tint(), r.floorY(), cells, r.followPeople()));
        }
        final List<Scene> scenes = new ArrayList<>(HouseModel.SCENE_SLOTS);
        for (int slot = 0; slot < HouseModel.SCENE_SLOTS; slot++) {
            final HouseModel.Scene s = house.scene(slot);
            scenes.add(s == null ? null : new Scene(s.name(), s.icon(), s.states()));
        }
        return new Config(rooms, scenes, house.bedtimeScene(), house.awayScene(), house.homeScene(),
                house.toasts(), house.lastScene());
    }

    private static void encode(RegistryFriendlyByteBuf buf, SmartHomeSyncPacket p) {
        buf.writeBlockPos(p.panelPos());
        buf.writeBoolean(p.awake());
        buf.writeVarInt(p.storeys().size());
        for (int y : p.storeys()) {
            buf.writeVarInt(y);
        }
        buf.writeVarInt(p.panelStorey());
        buf.writeVarInt(p.minX());
        buf.writeVarInt(p.minZ());
        buf.writeVarInt(p.maxX());
        buf.writeVarInt(p.maxZ());
        encodeRows(buf, p.rows());

        buf.writeVarInt(Math.max(0, p.dropped()));
        buf.writeVarInt(p.unlisted().size());
        for (Hint h : p.unlisted()) {
            buf.writeBlockPos(h.pos());
            ComponentSerialization.STREAM_CODEC.encode(buf, h.name());
            buf.writeVarInt(h.floorY());
        }
        buf.writeVarInt(p.players().size());
        for (Player pl : p.players()) {
            buf.writeUtf(pl.name(), 32);
            buf.writeVarInt(pl.x());
            buf.writeVarInt(pl.z());
            buf.writeFloat(pl.yaw());
            buf.writeVarInt(pl.floorY());
        }
        buf.writeVarIntArray(p.dayChart());
        buf.writeVarInt(p.hourOfDay());
        buf.writeVarInt(p.revision());
        buf.writeBoolean(p.config() != null);
        if (p.config() != null) {
            encodeConfig(buf, p.config());
        }
        buf.writeBoolean(p.wiring() != null);
        if (p.wiring() != null) {
            buf.writeVarInt(p.wiring().size());
            for (Wire w : p.wiring()) {
                buf.writeBlockPos(w.pos());
                buf.writeVarInt(Math.max(-1, w.flow()) + 1);
            }
        }
    }

    private static void encodeRows(RegistryFriendlyByteBuf buf, List<Row> rows) {
        buf.writeVarInt(rows.size());
        for (Row row : rows) {
            buf.writeVarInt(row.members().size());
            for (BlockPos pos : row.members()) {
                buf.writeBlockPos(pos);
            }
            buf.writeVarInt(row.category());
            ComponentSerialization.STREAM_CODEC.encode(buf, row.name());
            buf.writeBoolean(row.manual());
            buf.writeVarInt(row.state());
            buf.writeVarInt(row.mode());
            buf.writeVarInt(row.modes().size());
            for (Component label : row.modes()) {
                ComponentSerialization.STREAM_CODEC.encode(buf, label);
            }
            buf.writeVarInt(row.drawFe());

            encodeRows(buf, row.plugged());
            buf.writeVarInt(row.floorY());
            buf.writeVarInt(row.colour() + 1);
            buf.writeBoolean(row.status() != null);
            if (row.status() != null) {
                ComponentSerialization.STREAM_CODEC.encode(buf, row.status());
            }
            buf.writeFloat(row.progress());
            buf.writeBoolean(row.running());
            buf.writeBoolean(row.standby());
            buf.writeVarInt(row.roomId() + 1);
        }
    }

    private static void encodeConfig(RegistryFriendlyByteBuf buf, Config c) {
        buf.writeVarInt(c.rooms().size());
        for (Room r : c.rooms()) {
            buf.writeVarInt(r.id());
            buf.writeUtf(r.name(), HouseModel.MAX_NAME * 4);
            buf.writeUtf(r.icon(), 128);
            buf.writeVarInt(r.tint());
            buf.writeVarInt(r.floorY());
            buf.writeLongArray(r.cells());
            buf.writeBoolean(r.follow());
        }
        buf.writeVarInt(c.scenes().size());
        for (Scene s : c.scenes()) {
            buf.writeBoolean(s != null);
            if (s == null) {
                continue;
            }
            buf.writeUtf(s.name(), HouseModel.MAX_NAME * 4);
            buf.writeUtf(s.icon(), 128);
            buf.writeVarInt(s.states().size());
            for (Map.Entry<Long, SceneDiff.State> e : s.states().entrySet()) {
                buf.writeLong(e.getKey());
                buf.writeBoolean(e.getValue().on());
                buf.writeVarInt(e.getValue().mode() + 1);
                buf.writeVarInt(e.getValue().colour() + 1);
            }
        }
        buf.writeVarInt(c.bedtime() + 1);
        buf.writeVarInt(c.away() + 1);
        buf.writeVarInt(c.home() + 1);
        buf.writeBoolean(c.toasts());
        buf.writeVarInt(c.lastScene() + 1);
    }

    private static final int HARD_LIMIT = 1 << 16;

    private static int readCount(RegistryFriendlyByteBuf buf) {
        final int declared = buf.readVarInt();
        if (declared < 0 || declared > HARD_LIMIT) {
            throw new io.netty.handler.codec.DecoderException(
                    "smart_home_sync declared " + declared + " entries");
        }
        return declared;
    }

    private static SmartHomeSyncPacket decode(RegistryFriendlyByteBuf buf) {
        final BlockPos pos = buf.readBlockPos();
        final boolean awake = buf.readBoolean();
        final int storeyCount = readCount(buf);
        final List<Integer> storeys = new ArrayList<>(Math.min(storeyCount, 64));
        for (int i = 0; i < storeyCount; i++) {
            final int y = buf.readVarInt();
            if (storeys.size() < 64) {
                storeys.add(y);
            }
        }
        final int panelStorey = buf.readVarInt();
        final int minX = buf.readVarInt();
        final int minZ = buf.readVarInt();
        final int maxX = buf.readVarInt();
        final int maxZ = buf.readVarInt();
        final List<Row> rows = decodeRows(buf, 2);
        final int dropped = Math.max(0, buf.readVarInt());
        final int hintCount = readCount(buf);
        final List<Hint> hints = new ArrayList<>(Math.min(hintCount, HomeSegment.MAX_HINTS));
        for (int i = 0; i < hintCount; i++) {
            final BlockPos at = buf.readBlockPos();
            final Component name = ComponentSerialization.STREAM_CODEC.decode(buf);
            final int floor = buf.readVarInt();
            if (hints.size() < HomeSegment.MAX_HINTS) {
                hints.add(new Hint(at, name, floor));
            }
        }
        final int playerCount = readCount(buf);
        final List<Player> players = new ArrayList<>(Math.min(playerCount, 16));
        for (int i = 0; i < playerCount; i++) {
            final String name = buf.readUtf(32);
            final int x = buf.readVarInt();
            final int z = buf.readVarInt();
            final float yaw = buf.readFloat();
            final int floor = buf.readVarInt();
            if (players.size() < 16) {
                players.add(new Player(name, x, z, yaw, floor));
            }
        }
        final int[] chart = buf.readVarIntArray(64);
        final int hour = buf.readVarInt();
        final int revision = buf.readVarInt();
        final Config config = buf.readBoolean() ? decodeConfig(buf) : null;
        List<Wire> wiring = null;
        if (buf.readBoolean()) {
            final int count = readCount(buf);
            wiring = new ArrayList<>(Math.min(count, SmartHomePanelBE.MAX_WIRES));
            for (int i = 0; i < count; i++) {
                final BlockPos at = buf.readBlockPos();
                final int flow = buf.readVarInt() - 1;
                if (wiring.size() < SmartHomePanelBE.MAX_WIRES) {
                    wiring.add(new Wire(at, flow));
                }
            }
        }
        return new SmartHomeSyncPacket(pos, awake, storeys, panelStorey, minX, minZ, maxX, maxZ, rows, dropped, hints,
                players, chart, hour, revision, config, wiring);
    }

    private static List<Row> decodeRows(RegistryFriendlyByteBuf buf, int depthLeft) {
        final int bound = depthLeft >= 2 ? HomeSegment.MAX_ROWS : depthLeft == 1 ? MAX_PLUGGED : 0;
        final int count = readCount(buf);
        final List<Row> rows = new ArrayList<>(Math.min(count, bound));
        for (int i = 0; i < count; i++) {
            final int memberCount = readCount(buf);
            final List<BlockPos> members = new ArrayList<>(Math.min(memberCount, HomeSegment.MAX_ROWS));
            for (int m = 0; m < memberCount; m++) {
                final BlockPos member = buf.readBlockPos();
                if (members.size() < HomeSegment.MAX_ROWS) {
                    members.add(member);
                }
            }
            final int category = buf.readVarInt();
            final Component name = ComponentSerialization.STREAM_CODEC.decode(buf);
            final boolean manual = buf.readBoolean();
            final int state = buf.readVarInt();
            final int mode = buf.readVarInt();
            final int modeCount = readCount(buf);
            final List<Component> modes = new ArrayList<>(Math.min(modeCount, MAX_MODES));
            for (int m = 0; m < modeCount; m++) {
                final Component label = ComponentSerialization.STREAM_CODEC.decode(buf);
                if (modes.size() < MAX_MODES) {
                    modes.add(label);
                }
            }
            final int draw = buf.readVarInt();
            final List<Row> plugged = decodeRows(buf, depthLeft - 1);
            final int floorY = buf.readVarInt();
            final int colour = buf.readVarInt() - 1;
            final Component status = buf.readBoolean() ? ComponentSerialization.STREAM_CODEC.decode(buf) : null;
            final float progress = buf.readFloat();
            final boolean running = buf.readBoolean();
            final boolean standby = buf.readBoolean();
            final int roomId = buf.readVarInt() - 1;
            if (!members.isEmpty() && rows.size() < bound) {
                rows.add(new Row(members, category, name, manual, state, mode, modes, draw, plugged, floorY,
                        colour, status, progress, running, standby, roomId));
            }
        }
        return rows;
    }

    private static Config decodeConfig(RegistryFriendlyByteBuf buf) {
        final int roomCount = readCount(buf);
        final List<Room> rooms = new ArrayList<>(Math.min(roomCount, HouseModel.MAX_ROOMS));
        for (int i = 0; i < roomCount; i++) {
            final int id = buf.readVarInt();
            final String name = buf.readUtf(HouseModel.MAX_NAME * 4);
            final String icon = buf.readUtf(128);
            final int tint = buf.readVarInt();
            final int floorY = buf.readVarInt();
            final long[] cells = buf.readLongArray(null, RoomFill.MAX_CELLS);
            final boolean follow = buf.readBoolean();
            if (rooms.size() < HouseModel.MAX_ROOMS) {
                rooms.add(new Room(id, name, icon, Math.floorMod(tint, 16), floorY, cells, follow));
            }
        }
        final int sceneCount = readCount(buf);
        final List<Scene> scenes = new ArrayList<>(HouseModel.SCENE_SLOTS);
        for (int i = 0; i < sceneCount; i++) {
            Scene scene = null;
            if (buf.readBoolean()) {
                final String name = buf.readUtf(HouseModel.MAX_NAME * 4);
                final String icon = buf.readUtf(128);
                final int n = readCount(buf);
                final Map<Long, SceneDiff.State> states = new LinkedHashMap<>();
                for (int k = 0; k < n; k++) {
                    final long key = buf.readLong();
                    final boolean on = buf.readBoolean();
                    final int mode = buf.readVarInt() - 1;
                    final int colour = buf.readVarInt() - 1;
                    if (states.size() < HouseModel.MAX_SCENE_ENTRIES) {
                        states.put(key, new SceneDiff.State(on, mode, colour));
                    }
                }
                scene = new Scene(name, icon, states);
            }
            if (scenes.size() < HouseModel.SCENE_SLOTS) {
                scenes.add(scene);
            }
        }
        while (scenes.size() < HouseModel.SCENE_SLOTS) {
            scenes.add(null);
        }
        final int bed = buf.readVarInt() - 1;
        final int away = buf.readVarInt() - 1;
        final int home = buf.readVarInt() - 1;
        final boolean toasts = buf.readBoolean();
        final int last = buf.readVarInt() - 1;
        return new Config(rooms, scenes, bed, away, home, toasts, last);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SmartHomeSyncPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> com.createstats.util.ClientScreenAccess.acceptSmartHomeRoster(packet));
    }
}
