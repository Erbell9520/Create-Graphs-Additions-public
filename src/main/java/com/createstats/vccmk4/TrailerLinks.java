package com.createstats.vccmk4;

import com.createstats.flight.bridge.SableBridge;
import com.createstats.vccmk4.core.LinkKind;
import com.createstats.vccmk4.core.LinkTrim;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.joml.Vector3d;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

final class TrailerLinks {
    interface Type {
        String name();

        boolean matches(BlockEntity be);

        @Nullable
        Tie tie(ServerLevel level, BlockEntity be);

        @Nullable
        Reading read(ServerLevel level, Ends ends);
    }

    record Tie(Object key, LinkKind kind, @Nullable UUID farBody, BlockPos farBlock) {
    }

    interface Points {
        @Nullable
        Vector3d world(int body, Vector3d local);
    }

    record Ends(@Nullable BlockEntity near, int nearBody, @Nullable BlockEntity far, int farBody, Points points) {
        @Nullable
        Vector3d world(int body, @Nullable Vector3d local) {
            return local == null ? null : points.world(body, local);
        }

        @Nullable
        BlockEntity either() {
            return near != null ? near : far;
        }
    }

    record Reading(double value, double force, boolean warning, boolean settling, @Nullable LinkKind kind,
                   double share) {
        Reading(double value, double force, boolean warning, boolean settling, @Nullable LinkKind kind) {
            this(value, force, warning, settling, kind, Double.NaN);
        }

        static Reading present(@Nullable LinkKind kind) {
            return new Reading(Double.NaN, Double.NaN, false, false, kind);
        }
    }

    @Nullable
    static Reading combine(LinkKind.Reading how, List<Reading> each) {
        double value = Double.NaN;
        double sum = 0.0;
        int counted = 0;
        double force = Double.NaN;
        double share = Double.NaN;
        boolean warning = false;
        boolean settling = false;
        boolean any = false;
        LinkKind kind = null;
        for (Reading r : each) {
            if (r == null) continue;
            any = true;
            warning |= r.warning();
            settling |= r.settling();

            if (Double.isFinite(r.share())) share = Double.isFinite(share) ? Math.max(share, r.share()) : r.share();
            if (kind == null) kind = r.kind();
            if (Double.isFinite(r.force())) force = (Double.isFinite(force) ? force : 0.0) + r.force();
            if (!Double.isFinite(r.value())) continue;
            if (how == LinkKind.Reading.GAP) {
                value = Double.isFinite(value) ? Math.max(value, r.value()) : r.value();
            } else {
                sum += r.value();
                counted++;
            }
        }
        if (!any) return null;
        if (how != LinkKind.Reading.GAP && counted > 0) value = sum / counted;
        return new Reading(value, force, warning, settling, kind, share);
    }

    static final List<Type> ALL = List.of(new Swivel(), new Rope(), new Spring(), new Dock());

    private TrailerLinks() {
    }

    @Nullable
    static Type of(@Nullable BlockEntity be) {
        if (be == null) return null;
        for (Type t : ALL) {
            if (t.matches(be)) return t;
        }
        return null;
    }

    static String pairKey(String kind, BlockPos x, BlockPos y) {
        long a = x.asLong();
        long c = y.asLong();
        return kind + " " + Math.min(a, c) + " " + Math.max(a, c);
    }

    static final class Swivel implements Type {
        @Override
        public String name() {
            return "swivel bearing";
        }

        @Override
        public boolean matches(BlockEntity be) {
            return SableBridge.matchesSwivelBearing(be) || Vcc4Reflect.is(Vcc4Reflect.SWIVEL_PLATE, be);
        }

        @Nullable
        @Override
        public Tie tie(ServerLevel level, BlockEntity be) {
            if (SableBridge.matchesSwivelBearing(be)) {
                if (!SableBridge.isSwivelAssembled(be)) return null;
                UUID other = Vcc4Reflect.swivelSubLevelId(be);
                BlockPos plate = Vcc4Reflect.swivelPlatePos(be);
                if (other == null || plate == null) return null;
                return new Tie("swivel " + be.getBlockPos().asLong(), kindOf(be), other, plate);
            }
            Vcc4Reflect.Joint joint = Vcc4Reflect.plateParent(be);
            if (joint == null) return null;
            BlockEntity bearing = SableBridge.getBlockEntityOnContraption(level, joint.anchor());
            LinkKind kind = bearing != null ? kindOf(bearing) : LinkKind.HITCH;
            return new Tie("swivel " + joint.anchor().asLong(), kind, joint.body(), joint.anchor());
        }

        @Nullable
        @Override
        public Reading read(ServerLevel level, Ends ends) {
            BlockEntity bearing = SableBridge.matchesSwivelBearing(ends.near()) ? ends.near()
                    : (SableBridge.matchesSwivelBearing(ends.far()) ? ends.far() : null);
            if (bearing == null || !SableBridge.isSwivelAssembled(bearing)) return null;
            return Reading.present(kindOf(bearing));
        }

        private static LinkKind kindOf(BlockEntity bearing) {
            return Vcc4Scanner.locked(bearing.getBlockState()) ? LinkKind.JOINT : LinkKind.HITCH;
        }
    }

    static final class Rope implements Type {
        @Override
        public String name() {
            return "rope";
        }

        @Override
        public boolean matches(BlockEntity be) {
            return Vcc4Reflect.is(Vcc4Reflect.ROPE_HOLDER, be);
        }

        @Nullable
        @Override
        public Tie tie(ServerLevel level, BlockEntity be) {
            Vcc4Reflect.Rope rope = Vcc4Reflect.rope(be);
            if (rope == null) return null;
            Vcc4Reflect.RopeEnd far = rope.other(be.getBlockPos());
            return new Tie(rope.strand(), LinkKind.ROPE, far.body(), far.block());
        }

        @Nullable
        @Override
        public Reading read(ServerLevel level, Ends ends) {
            Vcc4Reflect.Rope rope = Vcc4Reflect.rope(ends.either());
            if (rope == null) return null;
            Vector3d a = ends.world(ends.nearBody(), Vcc4Reflect.ropeAttachPoint(ends.near()));
            Vector3d c = ends.world(ends.farBody(), Vcc4Reflect.ropeAttachPoint(ends.far()));
            double gap = a != null && c != null ? a.distance(c) - rope.nominal() : Double.NaN;
            return new Reading(gap, Double.NaN, false, false, null);
        }
    }

    static final class Spring implements Type {
        @Override
        public String name() {
            return "spring";
        }

        @Override
        public boolean matches(BlockEntity be) {
            return Vcc4Reflect.is(Vcc4Reflect.SPRING, be);
        }

        @Nullable
        @Override
        public Tie tie(ServerLevel level, BlockEntity be) {
            BlockEntity partner = Vcc4Reflect.springPartner(be);
            if (partner == null) return null;
            UUID other = SableBridge.subLevelId(SableBridge.subLevelAccessOf(partner));
            return new Tie(pairKey("spring", be.getBlockPos(), partner.getBlockPos()), LinkKind.SPRING, other, partner.getBlockPos());
        }

        @Nullable
        @Override
        public Reading read(ServerLevel level, Ends ends) {
            if (Vcc4Reflect.springPartner(ends.either()) == null) return null;
            boolean nearControls = Vcc4Reflect.springController(ends.near());
            BlockEntity ctrl = nearControls ? ends.near() : ends.far();
            BlockEntity other = nearControls ? ends.far() : ends.near();
            Vector3d a = ends.world(nearControls ? ends.nearBody() : ends.farBody(), Vcc4Reflect.springCentre(ctrl));
            Vector3d c = other == null ? null : ends.world(nearControls ? ends.farBody() : ends.nearBody(),
                    Vcc4Scanner.centre(other.getBlockPos()));
            double desired = Vcc4Reflect.springDesired(ctrl);
            if (ctrl == null || a == null || c == null || !Double.isFinite(desired)) {
                return new Reading(Double.NaN, Double.NaN, false, false, null);
            }
            double distance = a.distance(c);
            double extension = distance - (desired - 0.75);
            double force = LinkTrim.springForce(extension, Vcc4Reflect.springSize(ctrl.getBlockState()));
            double share = LinkTrim.snapShare(distance, desired);
            return new Reading(extension, force, share >= LinkTrim.SNAP_WARN, false, null, share);
        }
    }

    static final class Dock implements Type {
        @Override
        public String name() {
            return "docking connector";
        }

        @Override
        public boolean matches(BlockEntity be) {
            return Vcc4Reflect.is(Vcc4Reflect.DOCKING_CONNECTOR, be);
        }

        @Nullable
        @Override
        public Tie tie(ServerLevel level, BlockEntity be) {
            Vcc4Reflect.Dock dock = Vcc4Reflect.dock(be);
            if (dock == null || dock.other() == null) return null;
            return new Tie(pairKey("dock", be.getBlockPos(), dock.other()), LinkKind.DOCK, dock.otherBody(), dock.other());
        }

        @Nullable
        @Override
        public Reading read(ServerLevel level, Ends ends) {
            Vcc4Reflect.Dock dock = Vcc4Reflect.dock(ends.either());
            if (dock == null || dock.other() == null) return null;
            return new Reading(Double.NaN, Double.NaN, false, !dock.locked(), LinkKind.DOCK);
        }
    }
}
