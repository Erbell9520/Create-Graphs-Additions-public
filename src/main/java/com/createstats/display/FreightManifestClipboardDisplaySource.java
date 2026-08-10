package com.createstats.display;

import com.createstats.blockentity.FreightManifestClipboardBE;
import com.createstats.train.manifest.DeliveryOrderBoardView;
import com.createstats.train.manifest.FreightManifest;
import com.createstats.train.manifest.FreightManifestLogic;
import com.createstats.train.manifest.FreightManifestSavedData;
import com.createstats.train.manifest.FreightManifestValidationResult;
import com.createstats.train.manifest.FreightStationCargoView;
import com.createstats.train.manifest.FreightStationLink;
import com.createstats.train.manifest.FreightManifestStationResolver;
import com.createstats.train.manifest.ManifestOperation;
import com.simibubi.create.api.behaviour.display.DisplaySource;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import com.simibubi.create.content.redstone.displayLink.target.DisplayTargetStats;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

public class FreightManifestClipboardDisplaySource extends DisplaySource implements CargoLinkSource {

    @Override
    public List<MutableComponent> provideText(DisplayLinkContext context, DisplayTargetStats stats) {
        BlockEntity blockEntity = context.getSourceBlockEntity();
        if (!(blockEntity instanceof FreightManifestClipboardBE clipboard)) {
            return EMPTY;
        }

        FreightStationLink stationLink = clipboard.getStationLink();
        if (!stationLink.isLinked()) {
            return List.of(Component.translatable("create_stats.freight_manifest.display.unlinked"));
        }

        List<MutableComponent> lines = new ArrayList<>();
        int maxRows = Math.max(1, stats.maxRows());
        Component stationName = stationLink.getStationName().isBlank()
            ? Component.translatable("create_stats.freight_manifest.display.linked_station")
            : Component.literal(stationLink.getStationName());
        lines.add(Component.translatable("create_stats.freight_manifest.display.line", stationName));

        if (clipboard.getLevel() == null || clipboard.getLevel().getServer() == null) {
            if (maxRows >= 2) {
                lines.add(Component.translatable("create_stats.freight_manifest.display.waiting"));
            }
            return lines;
        }

        ServerLevel stationLevel = FreightManifestStationResolver.resolveStationLevel(clipboard.getLevel().getServer(), stationLink);
        FreightManifest manifest = new FreightManifest();
        if (stationLevel != null && stationLink.getStationId() != null) {
            FreightManifest stored = FreightManifestSavedData.get(stationLevel).getManifest(stationLink.getStationId());
            if (stored != null) {
                manifest = stored;
            }
        }

        FreightStationCargoView cargoView = FreightManifestStationResolver.captureCargoView(clipboard.getLevel().getServer(), stationLink);
        DeliveryOrderBoardView deliveryBoard = FreightManifestStationResolver.captureDeliveryBoard(clipboard.getLevel().getServer(), stationLink);
        FreightManifestValidationResult validation = FreightManifestLogic.validate(manifest.getLines());
        MutableComponent status;
        if (stationLevel == null) {
            status = Component.translatable("create_stats.freight_manifest.display.status.cargo", cargoView.getStatusMessage());
        } else if (manifest.isEmpty()) {
            status = Component.translatable("create_stats.freight_manifest.display.status.no_lines");
        } else if (!validation.isValid()) {
            status = Component.translatable("create_stats.freight_manifest.display.status.invalid", validation.getMessage());
        } else {
            boolean complete = deliveryBoard.getTotalOrders() > 0 && deliveryBoard.getCompletedOrders() >= deliveryBoard.getTotalOrders();
            status = Component.translatable(complete
                ? "create_stats.freight_manifest.display.status.complete"
                : "create_stats.freight_manifest.display.status.incomplete", deliveryBoard.getSummaryStatus());
        }

        if (maxRows >= 2) {
            lines.add(status);
        }

        if (maxRows >= 3) {
            lines.add(Component.translatable("create_stats.freight_manifest.display.counts",
                manifest.getLines().size(),
                countLines(manifest, ManifestOperation.LOAD),
                countLines(manifest, ManifestOperation.UNLOAD)));
        }

        if (maxRows >= 4) {
            String trainText = deliveryBoard.isTrainPresent() ? deliveryBoard.getTrainName() : cargoView.getStatusMessage();
            lines.add(Component.translatable("create_stats.freight_manifest.display.train", trainText));
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
