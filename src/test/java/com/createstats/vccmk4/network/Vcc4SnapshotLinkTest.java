package com.createstats.vccmk4.network;

import com.createstats.vccmk4.core.LinkSource;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class Vcc4SnapshotLinkTest {
    @Test
    void lampsAndReceiversRoundTrip() {
        Vcc4Snapshot.Builder b = new Vcc4Snapshot.Builder();
        b.onCraft = true;
        b.links = List.of(
                new Vcc4Snapshot.Link(11L, -1.5f, 2.0f, 0, LinkSource.TAIL_BRAKE.ordinal(), false, 6,
                        "block.create_stats.vehicle_light_red", "", false, 2, true),
                new Vcc4Snapshot.Link(12L, 1.0f, -3.0f, 1, LinkSource.BRAKE.ordinal(), true, 15,
                        "item.minecraft.redstone", "item.minecraft.lever", true));
        Vcc4Snapshot sent = b.build();
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        Vcc4Snapshot.write(buf, sent);
        Vcc4Snapshot got = Vcc4Snapshot.read(buf);
        assertEquals(sent.links(), got.links());
        assertEquals(1, got.links().get(0).lampKind(), "a red lamp");
        assertEquals(-1, got.links().get(1).lampKind(), "a receiver");
        assertEquals(0, buf.readableBytes(), "and nothing left unread");
    }
}
