package com.createstats.vccmk4.network;

import com.createstats.surveyor.PathwayOps;
import com.createstats.surveyor.PathwayStore;
import com.createstats.surveyor.core.Pathway;
import com.createstats.vccmk4.route.Vcc4Route;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record RouteSnapshot(boolean assigned, UUID pathway, String pathwayName, int colour, int state, String reason,
                            float progress, float length, boolean closed, int nextStop, String nextStopName, float eta,
                            String rules, float waitFraction, String waitText, int laps, float lastLap, boolean recording,
                            boolean recordArmed, int recordedPoints, boolean learned, float cruiseOverride, int comfort,
                            boolean startOnAssign, boolean horn, float[] stopS, float plannedSpeed, float pathwayCruise,
                            List<Choice> choices, boolean hasSchedule, String schedule) {
    public record Choice(UUID id, String name, int colour, int nodes) {
    }

    public static final RouteSnapshot NONE = new RouteSnapshot(false, new UUID(0, 0), "", 0, 9, "", 0f, 0f, false, -1, "", -1f,
            "", -1f, "", 0, -1f, false, false, 0, false, 0f, -1, true, true, new float[0], 0f, 6f, List.of(), false, "");

    public static RouteSnapshot of(Vcc4Route.View v, List<Choice> choices) {
        float[] stops = new float[v.stops().size()];
        for (int i = 0; i < stops.length; i++) stops[i] = (float) v.stops().get(i)[0];
        return new RouteSnapshot(v.assigned(), v.pathway(), v.pathwayName(), v.colour(), v.state(), v.reason(), (float) v.progress(),
                (float) v.length(), v.closed(), v.nextStop(), v.nextStopName(), (float) v.eta(), v.rules(), v.waitFraction(), v.waitText(),
                v.laps(), (float) v.lastLap(), v.recording(), v.recordArmed(), v.recordedPoints(), v.learned(),
                (float) v.cruiseOverride(), v.comfort(), v.startOnAssign(), v.horn(), stops, (float) v.plannedSpeed(),
                (float) v.pathwayCruise(), choices, v.hasSchedule(), v.schedule());
    }

    public static List<Choice> choices(ServerLevel level, ServerPlayer viewer) {
        List<Choice> out = new ArrayList<>();
        for (Pathway p : PathwayStore.get(level).sortedFor(viewer.getUUID())) {
            if (!PathwayOps.mayEdit(viewer, p) || p.nodes().size() < 2) continue;
            out.add(new Choice(p.id(), p.name(), p.colour(), p.nodes().size()));
            if (out.size() >= 32) break;
        }
        return out;
    }

    public static void write(FriendlyByteBuf buf, RouteSnapshot r) {
        buf.writeBoolean(r.assigned);
        buf.writeUUID(r.pathway);
        buf.writeUtf(cut(r.pathwayName, 48), 48);
        buf.writeVarInt(r.colour);
        buf.writeVarInt(r.state);
        buf.writeUtf(cut(r.reason, 96), 96);
        buf.writeFloat(r.progress);
        buf.writeFloat(r.length);
        buf.writeBoolean(r.closed);
        buf.writeVarInt(r.nextStop + 1);
        buf.writeUtf(cut(r.nextStopName, 48), 48);
        buf.writeFloat(r.eta);
        buf.writeUtf(cut(r.rules, 160), 160);
        buf.writeFloat(r.waitFraction);
        buf.writeUtf(cut(r.waitText, 160), 160);
        buf.writeVarInt(r.laps);
        buf.writeFloat(r.lastLap);
        buf.writeBoolean(r.recording);
        buf.writeBoolean(r.recordArmed);
        buf.writeVarInt(r.recordedPoints);
        buf.writeBoolean(r.learned);
        buf.writeFloat(r.cruiseOverride);
        buf.writeVarInt(r.comfort + 1);
        buf.writeBoolean(r.startOnAssign);
        buf.writeBoolean(r.horn);
        buf.writeVarInt(Math.min(64, r.stopS.length));
        for (int i = 0; i < Math.min(64, r.stopS.length); i++) buf.writeFloat(r.stopS[i]);
        buf.writeFloat(r.plannedSpeed);
        buf.writeFloat(r.pathwayCruise);
        buf.writeVarInt(Math.min(32, r.choices.size()));
        for (int i = 0; i < Math.min(32, r.choices.size()); i++) {
            Choice c = r.choices.get(i);
            buf.writeUUID(c.id());
            buf.writeUtf(cut(c.name(), 48), 48);
            buf.writeVarInt(c.colour());
            buf.writeVarInt(c.nodes());
        }
        buf.writeBoolean(r.hasSchedule);
        buf.writeUtf(cut(r.schedule, 160), 160);
    }

    public static RouteSnapshot read(FriendlyByteBuf buf) {
        boolean assigned = buf.readBoolean();
        UUID pathway = buf.readUUID();
        String name = buf.readUtf(48);
        int colour = buf.readVarInt();
        int state = buf.readVarInt();
        String reason = buf.readUtf(96);
        float progress = buf.readFloat();
        float length = buf.readFloat();
        boolean closed = buf.readBoolean();
        int next = buf.readVarInt() - 1;
        String nextName = buf.readUtf(48);
        float eta = buf.readFloat();
        String rules = buf.readUtf(160);
        float wait = buf.readFloat();
        String waitText = buf.readUtf(160);
        int laps = buf.readVarInt();
        float lastLap = buf.readFloat();
        boolean recording = buf.readBoolean();
        boolean armed = buf.readBoolean();
        int points = buf.readVarInt();
        boolean learned = buf.readBoolean();
        float cruise = buf.readFloat();
        int comfort = buf.readVarInt() - 1;
        boolean start = buf.readBoolean();
        boolean horn = buf.readBoolean();
        int ns = Math.max(0, Math.min(64, buf.readVarInt()));
        float[] stops = new float[ns];
        for (int i = 0; i < ns; i++) stops[i] = buf.readFloat();
        float planned = buf.readFloat();
        float pathwayCruise = buf.readFloat();
        int nc = Math.max(0, Math.min(32, buf.readVarInt()));
        List<Choice> choices = new ArrayList<>(nc);
        for (int i = 0; i < nc; i++) choices.add(new Choice(buf.readUUID(), buf.readUtf(48), buf.readVarInt(), buf.readVarInt()));
        boolean hasSchedule = buf.readBoolean();
        String schedule = buf.readUtf(160);
        return new RouteSnapshot(assigned, pathway, name, colour, state, reason, progress, length, closed, next, nextName, eta,
                rules, wait, waitText, laps, lastLap, recording, armed, points, learned, cruise, comfort, start, horn, stops,
                planned, pathwayCruise, List.copyOf(choices), hasSchedule, schedule);
    }

    private static String cut(String s, int max) {
        return s == null ? "" : (s.length() > max ? s.substring(0, max) : s);
    }
}
