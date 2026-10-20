package com.createstats.train.manifest;

import java.util.List;
import java.util.UUID;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FreightViewConsistencyTest {
    @Test
    void emptyQueueStillReportsDockedTrainAndRetainsReceipts() {
        FreightStationCargoView cargo = new FreightStationCargoView(true, "Ore Run", "", 2,
            new TrainCargoSnapshot(), new TrainCargoSnapshot(), new TrainCargoSnapshot());
        List<DeliveryOrderHistoryEntry> receipts = List.of(new DeliveryOrderHistoryEntry(
            DeliveryOrderHistoryType.COMPLETED, 100, "One-shot Archived", "Load 16 iron"));
        DeliveryOrderBoardView view = FreightManifestLogic.buildDeliveryOrderBoardView(
            new FreightManifest(), new DeliveryOrderBoard(), null, cargo, 101, false, false, receipts);
        assertTrue(view.isTrainPresent());
        assertEquals("Ore Run", view.getTrainName());
        assertEquals(0, view.getTotalOrders());
        assertEquals(1, view.getHistoryEntries().size());
        assertTrue(FreightManifestPageSummary.from(view).isTrainPresent());
    }

    @Test
    void legacyZeroPositionLinkKeepsItsValidIdentityAcrossSaveAndWire() {
        UUID id = UUID.fromString("4eeb9ad5-e21e-4675-9f84-70b5dcbdcc8a");
        FreightStationLink link = new FreightStationLink(id, "Station A",
            ResourceLocation.withDefaultNamespace("overworld"), BlockPos.ZERO);
        FreightStationLink saved = FreightStationLink.load(link.save());
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            saved.encode(buffer);
            FreightStationLink decoded = FreightStationLink.decode(buffer);
            assertTrue(decoded.isLinked(), "zero coordinates must not invalidate a valid UUID");
            assertEquals(id, decoded.getStationId());
            assertEquals(link.getStationDimension(), decoded.getStationDimension());
            assertEquals(BlockPos.ZERO, decoded.getStationPos());
            assertEquals(0, buffer.readableBytes());
        } finally {
            buffer.release();
        }
    }
}
