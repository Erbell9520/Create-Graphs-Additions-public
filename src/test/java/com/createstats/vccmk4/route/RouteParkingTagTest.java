package com.createstats.vccmk4.route;

import com.createstats.surveyor.core.RigidMove;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RouteParkingTagTest {
    private static CompoundTag vec(String a, double x, String b, double y, String c, double z) {
        CompoundTag t = new CompoundTag();
        t.putDouble(a, x);
        t.putDouble(b, y);
        t.putDouble(c, z);
        return t;
    }

    private static ListTag point(double x, double y, double z) {
        ListTag l = new ListTag();
        l.add(DoubleTag.valueOf(x));
        l.add(DoubleTag.valueOf(y));
        l.add(DoubleTag.valueOf(z));
        return l;
    }

    private static CompoundTag trailer(UUID tractor, UUID self) {
        CompoundTag tag = new CompoundTag();
        CompoundTag pose = new CompoundTag();
        pose.put("position", vec("x", 182.54, "y", 68.02, "z", -260.01));
        CompoundTag o = new CompoundTag();
        o.putDouble("x", 0.0696);
        o.putDouble("y", 0.1988);
        o.putDouble("z", -0.3230);
        o.putDouble("w", -0.9226);
        pose.put("orientation", o);
        pose.put("rotation_point", vec("x", 20481028.2, "y", 130.26, "z", 20485128.5));
        tag.put("pose", pose);
        CompoundTag box = new CompoundTag();
        box.putDouble("minX", 171.8);
        box.putDouble("minY", 61.4);
        box.putDouble("minZ", -269.6);
        box.putDouble("maxX", 195.3);
        box.putDouble("maxY", 80.8);
        box.putDouble("maxZ", -249.5);
        tag.put("world_bounds", box);
        tag.put("linear_velocity", vec("x", 1.0, "y", 0.0, "z", 0.0));
        CompoundTag strand = new CompoundTag();
        ListTag points = new ListTag();
        points.add(point(181.61, 63.66, -260.45));
        points.add(point(173.23, 65.41, -262.57));
        strand.put("points", points);
        ListTag ends = new ListTag();
        CompoundTag end = new CompoundTag();
        end.put("blockAttachment", new IntArrayTag(new int[]{20481018, 127, 20481034}));
        end.put("subLevelID", StringTag.valueOf(tractor.toString()));
        end.put("point", StringTag.valueOf("END"));
        ends.add(end);
        CompoundTag start = new CompoundTag();
        start.put("blockAttachment", new IntArrayTag(new int[]{20481024, 127, 20485128}));
        start.put("subLevelID", StringTag.valueOf(self.toString()));
        start.put("point", StringTag.valueOf("START"));
        ends.add(start);
        strand.put("attachments", ends);
        CompoundTag connector = new CompoundTag();
        connector.putString("id", "simulated:rope_connector");
        connector.put("Strand", strand);
        CompoundTag plain = new CompoundTag();
        plain.putString("id", "simulated:rope_connector");
        ListTag bes = new ListTag();
        bes.add(plain);
        bes.add(connector);
        CompoundTag chunk = new CompoundTag();
        chunk.put("block_entities", bes);
        CompoundTag chunks = new CompoundTag();
        chunks.put("123", chunk);
        CompoundTag plot = new CompoundTag();
        plot.put("chunks", chunks);
        tag.put("plot", plot);
        return tag;
    }

    @Test
    void theWholeTagMovesAsOne() {
        UUID tractor = UUID.randomUUID();
        UUID self = UUID.randomUUID();
        CompoundTag tag = trailer(tractor, self);
        RigidMove m = RigidMove.between(165.8, 67.6, -264.8, 0.0, 400.0, 70.0, 100.0, 1.3);
        RouteParking.moveTag(tag, m);

        CompoundTag pos = tag.getCompound("pose").getCompound("position");
        double[] want = m.point(182.54, 68.02, -260.01);
        assertEquals(want[0], pos.getDouble("x"), 1e-9);
        assertEquals(want[1], pos.getDouble("y"), 1e-9);
        assertEquals(want[2], pos.getDouble("z"), 1e-9);

        CompoundTag o = tag.getCompound("pose").getCompound("orientation");
        double[] q = m.orientation(0.0696, 0.1988, -0.3230, -0.9226);
        double n = Math.sqrt(q[0] * q[0] + q[1] * q[1] + q[2] * q[2] + q[3] * q[3]);
        assertEquals(q[1] / n, o.getDouble("y"), 1e-9);
        assertEquals(1.0, Math.sqrt(o.getDouble("x") * o.getDouble("x") + o.getDouble("y") * o.getDouble("y")
                + o.getDouble("z") * o.getDouble("z") + o.getDouble("w") * o.getDouble("w")), 1e-9);

        assertEquals(20481028.2, tag.getCompound("pose").getCompound("rotation_point").getDouble("x"), 1e-9);

        CompoundTag box = tag.getCompound("world_bounds");
        double[] nb = m.box(171.8, 61.4, -269.6, 195.3, 80.8, -249.5);
        assertEquals(nb[0], box.getDouble("minX"), 1e-9);
        assertEquals(nb[5], box.getDouble("maxZ"), 1e-9);

        assertFalse(tag.contains("linear_velocity"), "it comes back at rest");

        ListTag points = tag.getCompound("plot").getCompound("chunks").getCompound("123").getList("block_entities", 10)
                .getCompound(1).getCompound("Strand").getList("points", 9);
        double[] p0 = m.point(181.61, 63.66, -260.45);
        assertEquals(p0[0], points.getList(0).getDouble(0), 1e-9);
        assertEquals(p0[2], points.getList(0).getDouble(2), 1e-9);
        double[] p1 = m.point(173.23, 65.41, -262.57);
        assertEquals(p1[1], points.getList(1).getDouble(1), 1e-9);

        double before = Math.sqrt(Math.pow(181.61 - 173.23, 2) + Math.pow(63.66 - 65.41, 2) + Math.pow(-260.45 + 262.57, 2));
        double after = Math.sqrt(Math.pow(p0[0] - p1[0], 2) + Math.pow(p0[1] - p1[1], 2) + Math.pow(p0[2] - p1[2], 2));
        assertEquals(before, after, 1e-9);
    }

    @Test
    void ropeTiesNameBothEndsAndTheWorld() {
        UUID tractor = UUID.randomUUID();
        UUID self = UUID.randomUUID();
        CompoundTag tag = trailer(tractor, self);
        List<UUID> ties = RouteParking.ropeTies(tag);
        assertEquals(Arrays.asList(tractor, self), ties);

        tag.getCompound("plot").getCompound("chunks").getCompound("123").getList("block_entities", 10).getCompound(1)
                .getCompound("Strand").getList("attachments", 10).getCompound(0).remove("subLevelID");
        List<UUID> world = RouteParking.ropeTies(tag);
        assertNull(world.get(0));
        assertTrue(world.contains(self));
    }

    @Test
    void aTagWithNoRopeOrBoxStillMoves() {
        CompoundTag tag = new CompoundTag();
        CompoundTag pose = new CompoundTag();
        pose.put("position", vec("x", 1, "y", 2, "z", 3));
        CompoundTag o = new CompoundTag();
        o.putDouble("w", 1.0);
        pose.put("orientation", o);
        tag.put("pose", pose);
        RouteParking.moveTag(tag, new RigidMove(0, 0, 0, 0, 10, 0, 0));
        assertEquals(11.0, tag.getCompound("pose").getCompound("position").getDouble("x"), 1e-12);
        assertTrue(RouteParking.ropeTies(tag).isEmpty());
    }
}
