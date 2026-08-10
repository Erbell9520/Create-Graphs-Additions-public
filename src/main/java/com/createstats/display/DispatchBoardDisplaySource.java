package com.createstats.display;

import com.createstats.blockentity.DispatchBoardBE;
import com.createstats.train.manifest.DeliveryOrderBoardView;
import com.createstats.train.manifest.FreightManifest;
import com.createstats.train.manifest.FreightStationCargoView;
import com.createstats.train.manifest.ManifestOperation;
import com.simibubi.create.api.behaviour.display.DisplaySource;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import com.simibubi.create.content.redstone.displayLink.target.DisplayTargetStats;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.block.entity.BlockEntity;

public class DispatchBoardDisplaySource extends DisplaySource implements CargoLinkSource {

    @Override
    public List<MutableComponent> provideText(DisplayLinkContext context, DisplayTargetStats stats) {
        BlockEntity blockEntity = context.getSourceBlockEntity();
        if (!(blockEntity instanceof DispatchBoardBE board)) {
            return EMPTY;
        }

        // Always resolve through the controller for freight data
        DispatchBoardBE dispatchBoard = board.getControllerBE();

        FreightManifest manifest = dispatchBoard.getManifest();
        FreightStationCargoView cargoView = dispatchBoard.getCargoView();
        DeliveryOrderBoardView deliveryBoard = dispatchBoard.getDeliveryBoard();
        int maxRows = Math.max(1, stats.maxRows());
        List<MutableComponent> lines = new ArrayList<>();
        Component stationName = dispatchBoard.isLinked() && !dispatchBoard.getStationLink().getStationName().isBlank()
            ? Component.literal(dispatchBoard.getStationLink().getStationName())
            : Component.translatable("block.create_stats.dispatch_board");
        lines.add(Component.translatable("create_stats.dispatch_board.display.line", stationName));
        if (maxRows >= 2) {
            boolean complete = deliveryBoard.getTotalOrders() > 0 && deliveryBoard.getCompletedOrders() >= deliveryBoard.getTotalOrders();
            lines.add(Component.translatable(complete
                ? "create_stats.dispatch_board.display.summary_complete"
                : "create_stats.dispatch_board.display.summary_incomplete", deliveryBoard.getSummaryStatus()));
        }
        if (maxRows >= 3) {
            lines.add(Component.translatable("create_stats.dispatch_board.display.counts",
                manifest.getLines().size(), deliveryBoard.getCompletedOrders(), deliveryBoard.getDismissedOrders()));
        }
        if (maxRows >= 4) {
            String trainText = cargoView.isTrainPresent() ? cargoView.getTrainName() : cargoView.getStatusMessage();
            lines.add(Component.translatable("create_stats.dispatch_board.display.train", trainText));
        }
        if (maxRows >= 5) {
            lines.add(Component.translatable("create_stats.dispatch_board.display.load_unload",
                countLines(manifest, ManifestOperation.LOAD),
                countLines(manifest, ManifestOperation.UNLOAD)));
        }
        return lines;
    }

    @Override
    protected String getTranslationKey() {
        return "cargo_link";
    }

    @Override
    public int getPassiveRefreshTicks() {
        return 20;
    }

    private static int countLines(FreightManifest manifest, ManifestOperation operation) {
        int count = 0;
        for (var line : manifest.getLines()) {
            if (line.getOperation() == operation) {
                count++;
            }
        }
        return count;
    }
}
