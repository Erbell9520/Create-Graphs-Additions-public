package com.createstats.battle.core;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

// ONE record for every reader. the old bridge had one per reader and they all disagreed about health
public record BattleReport(
        String key,
        boolean ground,
        String shipName,
        boolean powered,
        boolean master,
        long now,
        int surveyPermille,
        long surveyedAt,
        int refCount,
        int lostCount,
        int placeCount,
        int newBlocks,
        int banner,
        boolean capped,
        int bow,
        int bowSource,
        int groundSize,
        int options,
        int redstoneIn,
        int comparatorMode,
        List<SystemLine> systems,
        List<HitLine> hits,
        long lastHitAt,
        String firePlace,
        List<SectionLine> sections,
        List<String> missing,
        int shapeVersion,
        int lostVersion,
        byte[] face,
        String refitBy,
        int refitCount,
        long refitAt,
        boolean weaponsFree,
        boolean weaponsByRedstone,
        TargetLine target,
        List<GunLine> guns,
        int gunCount,
        long salvoAt,
        String salvo) {
    public static final int FORMAT = 4;

    public static final int MAX_GUNS = 16;
    public static final int MAX_HITS = 5;
    public static final int MAX_SECTIONS = 12;
    public static final int MAX_MISSING = 32;

    public static final int OPT_BELL = 1;
    public static final int OPT_LIGHTS = 2;
    public static final int OPT_CHAT = 4;

    public static final int BOW_PLACER = 0;
    public static final int BOW_THRUST = 1;
    public static final int BOW_MANUAL = 2;

    public record SystemLine(int intact, int total) {
    }

    public record HitLine(long at, String place, int count, String what) {
    }

    public record SectionLine(long pos, String name, int permille, String place) {
    }

    public record TargetLine(int kind, String name, int entityId, String shipId, float range, float bearing,
                             float elevation, float closing, float course, float speed, String by, long at,
                             long lostSince) {
        public static final TargetLine NONE = new TargetLine(-1, "", -1, "", 0f, 0f, 0f, 0f, 0f, 0f, "", -1L, -1L);

        public boolean present() {
            return kind >= 0;
        }
    }

    public record GunLine(int id, String name, int state, int mode, int kind, int load, float train, float elev,
                          int axis, float argA, float argB, String argS, GunArc arc, long mount) {
        public static final long NO_MOUNT = Long.MIN_VALUE;

        public GunState gunState() {
            return GunState.byOrdinal(state);
        }
    }

    public static BattleReport empty(String key, boolean ground, boolean powered) {
        List<SystemLine> systems = new ArrayList<>();
        for (int i = 0; i < BattleSystem.values().length; i++) systems.add(new SystemLine(0, 0));
        return new BattleReport(key, ground, "", powered, false, 0L, 0, -1L, 0, 0, 0, -1, 0, false, 0, BOW_PLACER,
                33, OPT_BELL | OPT_LIGHTS, 0, ComparatorScale.MODE_INTEGRITY, systems, List.of(), -1L, "",
                List.of(), List.of(), 0, 0, new byte[0], "", 0, -1L, false, false, TargetLine.NONE, List.of(), 0,
                -1L, "");
    }

    public int gunsReady() {
        int n = 0;
        for (GunLine g : guns) if (g.gunState().ready()) n++;
        return n;
    }

    public int integrityPermille() {
        return refCount <= 0 ? 1000 : (int) ((refCount - lostCount) * 1000L / refCount);
    }

    public boolean hasReference() {
        return refCount > 0 && surveyPermille < 0;
    }

    public SystemLine system(BattleSystem s) {
        int i = s.ordinal();
        return i < systems.size() ? systems.get(i) : new SystemLine(0, 0);
    }

    public boolean option(int bit) {
        return (options & bit) != 0;
    }

    public long hitAge(long clock) {
        return lastHitAt < 0 ? -1L : Math.max(0L, clock - lastHitAt);
    }

    public HeaderStatus.Status status(long clock) {
        return HeaderStatus.of(powered, !firePlace.isEmpty(), firePlace, hitAge(clock), weaponsFree, surveyPermille);
    }

    public byte[] encode() {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(512);
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeByte(FORMAT);
            out.writeUTF(cut(key, 128));
            out.writeBoolean(ground);
            out.writeUTF(cut(shipName, 64));
            out.writeBoolean(powered);
            out.writeBoolean(master);
            out.writeLong(now);
            out.writeShort(surveyPermille);
            out.writeLong(surveyedAt);
            out.writeInt(refCount);
            out.writeInt(lostCount);
            out.writeShort(placeCount);
            out.writeInt(newBlocks);
            out.writeInt(banner);
            out.writeBoolean(capped);
            out.writeByte(bow);
            out.writeByte(bowSource);
            out.writeShort(groundSize);
            out.writeByte(options);
            out.writeByte(redstoneIn);
            out.writeByte(comparatorMode);
            out.writeByte(systems.size());
            for (SystemLine s : systems) {
                out.writeInt(s.intact());
                out.writeInt(s.total());
            }
            int nh = Math.min(MAX_HITS, hits.size());
            out.writeByte(nh);
            for (int i = 0; i < nh; i++) {
                HitLine h = hits.get(i);
                out.writeLong(h.at());
                out.writeUTF(cut(h.place(), 64));
                out.writeInt(h.count());
                out.writeUTF(cut(h.what(), 96));
            }
            out.writeLong(lastHitAt);
            out.writeUTF(cut(firePlace, 64));
            int ns = Math.min(MAX_SECTIONS, sections.size());
            out.writeByte(ns);
            for (int i = 0; i < ns; i++) {
                SectionLine s = sections.get(i);
                out.writeLong(s.pos());
                out.writeUTF(cut(s.name(), 32));
                out.writeShort(s.permille());
                out.writeUTF(cut(s.place(), 64));
            }
            int nm = Math.min(MAX_MISSING, missing.size());
            out.writeByte(nm);
            for (int i = 0; i < nm; i++) out.writeUTF(cut(missing.get(i), 96));
            out.writeInt(shapeVersion);
            out.writeInt(lostVersion);
            out.writeShort(face.length);
            out.write(face);
            out.writeUTF(cut(refitBy, 32));
            out.writeInt(refitCount);
            out.writeLong(refitAt);
            out.writeBoolean(weaponsFree);
            out.writeBoolean(weaponsByRedstone);
            TargetLine t = target == null ? TargetLine.NONE : target;
            out.writeByte(t.kind());
            if (t.present()) {
                out.writeUTF(cut(t.name(), 64));
                out.writeInt(t.entityId());
                out.writeUTF(cut(t.shipId(), 40));
                out.writeFloat(t.range());
                out.writeFloat(t.bearing());
                out.writeFloat(t.elevation());
                out.writeFloat(t.closing());
                out.writeFloat(t.course());
                out.writeFloat(t.speed());
                out.writeUTF(cut(t.by(), 32));
                out.writeLong(t.at());
                out.writeLong(t.lostSince());
            }
            int ng = Math.min(MAX_GUNS, guns.size());
            out.writeByte(ng);
            for (int i = 0; i < ng; i++) {
                GunLine g = guns.get(i);
                out.writeShort(g.id());
                out.writeUTF(cut(g.name(), 24));
                out.writeByte(g.state());
                out.writeByte(g.mode());
                out.writeByte(g.kind());
                out.writeShort(Math.max(-1, Math.min(Short.MAX_VALUE, g.load())));
                out.writeShort(Math.round(g.train() * 10f));
                out.writeShort(Math.round(g.elev() * 10f));
                out.writeByte(g.axis());
                out.writeFloat(g.argA());
                out.writeFloat(g.argB());
                out.writeUTF(cut(g.argS(), 64));
                out.writeShort(g.arc().from());
                out.writeShort(g.arc().to());
                out.writeByte(g.arc().low());
                out.writeByte(g.arc().high());
                out.writeLong(g.mount());
            }
            out.writeShort(gunCount);
            out.writeLong(salvoAt);
            out.writeUTF(cut(salvo, 600));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return bytes.toByteArray();
    }

    public static BattleReport decode(byte[] data) {
        if (data == null || data.length == 0) return null;
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(data))) {
            if (in.readByte() != FORMAT) return null;
            String key = in.readUTF();
            boolean ground = in.readBoolean();
            String shipName = in.readUTF();
            boolean powered = in.readBoolean();
            boolean master = in.readBoolean();
            long now = in.readLong();
            int survey = in.readShort();
            long surveyedAt = in.readLong();
            int ref = in.readInt();
            int lost = in.readInt();
            int places = in.readShort();
            int newBlocks = in.readInt();
            int banner = in.readInt();
            boolean capped = in.readBoolean();
            int bow = in.readByte();
            int bowSource = in.readByte();
            int groundSize = in.readShort();
            int options = in.readByte() & 0xFF;
            int rsIn = in.readByte();
            int cmp = in.readByte();
            int nsys = in.readByte() & 0xFF;
            List<SystemLine> systems = new ArrayList<>(nsys);
            for (int i = 0; i < nsys; i++) systems.add(new SystemLine(in.readInt(), in.readInt()));
            int nh = in.readByte() & 0xFF;
            List<HitLine> hits = new ArrayList<>(nh);
            for (int i = 0; i < nh; i++) hits.add(new HitLine(in.readLong(), in.readUTF(), in.readInt(), in.readUTF()));
            long lastHitAt = in.readLong();
            String firePlace = in.readUTF();
            int ns = in.readByte() & 0xFF;
            List<SectionLine> sections = new ArrayList<>(ns);
            for (int i = 0; i < ns; i++) sections.add(new SectionLine(in.readLong(), in.readUTF(), in.readShort(), in.readUTF()));
            int nm = in.readByte() & 0xFF;
            List<String> missing = new ArrayList<>(nm);
            for (int i = 0; i < nm; i++) missing.add(in.readUTF());
            int shapeV = in.readInt();
            int lostV = in.readInt();
            byte[] face = new byte[in.readShort() & 0xFFFF];
            in.readFully(face);
            String refitBy = in.readUTF();
            int refitCount = in.readInt();
            long refitAt = in.readLong();
            boolean weaponsFree = in.readBoolean();
            boolean weaponsByRedstone = in.readBoolean();
            int tk = in.readByte();
            TargetLine target = TargetLine.NONE;
            if (tk >= 0) {
                target = new TargetLine(tk, in.readUTF(), in.readInt(), in.readUTF(), in.readFloat(), in.readFloat(),
                        in.readFloat(), in.readFloat(), in.readFloat(), in.readFloat(), in.readUTF(), in.readLong(),
                        in.readLong());
            }
            int ng = in.readByte() & 0xFF;
            List<GunLine> guns = new ArrayList<>(ng);
            for (int i = 0; i < ng; i++) {
                guns.add(new GunLine(in.readShort(), in.readUTF(), in.readByte(), in.readByte(), in.readByte(),
                        in.readShort(), in.readShort() / 10f, in.readShort() / 10f, in.readByte(), in.readFloat(),
                        in.readFloat(), in.readUTF(), new GunArc(in.readShort(), in.readShort(), in.readByte(), in.readByte()),
                        in.readLong()));
            }
            int gunCount = in.readShort();
            long salvoAt = in.readLong();
            String salvo = in.readUTF();
            return new BattleReport(key, ground, shipName, powered, master, now, survey, surveyedAt, ref, lost, places,
                    newBlocks, banner, capped, bow, bowSource, groundSize, options, rsIn, cmp, List.copyOf(systems),
                    List.copyOf(hits), lastHitAt, firePlace, List.copyOf(sections), List.copyOf(missing), shapeV, lostV,
                    face, refitBy, refitCount, refitAt, weaponsFree, weaponsByRedstone, target, List.copyOf(guns),
                    gunCount, salvoAt, salvo);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    static String cut(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max);
    }
}
