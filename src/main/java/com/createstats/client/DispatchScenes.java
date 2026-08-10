package com.createstats.client;

import com.createstats.registry.ModBlocks;
import com.createstats.registry.ModItems;
import com.createstats.blockentity.DispatchBoardBE;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import java.util.UUID;

/** Dispatch Board ponder + NBT helpers, split out of CreateStatsPonderScenes (topic-class layout). */
public class DispatchScenes {

    private static CompoundTag dispatchLine(String id, String name, int target, int delivered, String op, String status) {
        CompoundTag t = new CompoundTag();
        t.putUUID("LineId", UUID.randomUUID());
        t.putString("CargoKind", "item");
        t.putString("Operation", op);
        t.putString("ResetMode", "every_arrival");
        t.putInt("ResetIntervalValue", 1);
        CompoundTag stack = new CompoundTag();
        stack.putString("id", id);
        stack.putInt("count", 1);
        t.put("DisplayStack", stack);
        t.putString("DisplayName", name);
        t.putInt("TargetAmount", target);
        t.putInt("DeliveredAmount", delivered);
        t.putInt("FreightAmount", delivered);
        t.putBoolean("Dismissed", false);
        t.putLong("TicksUntilReset", 0L);
        t.putString("StatusText", status);
        return t;
    }

    private static void injectDispatchBoard(CompoundTag tag) {
        CompoundTag link = new CompoundTag();
        link.putUUID("StationId", UUID.randomUUID());
        link.putString("StationDimension", "minecraft:overworld");
        link.putLong("StationPos", new BlockPos(40, 64, 40).asLong());
        link.putString("StationName", "Coal Junction");
        tag.put("StationLink", link);

        tag.putBoolean("TrainPresent", true);
        tag.putBoolean("ArrivalTrackingActive", false);
        tag.putString("TrainName", "Iron Hauler");
        tag.putString("SummaryStatus", "Unloading");
        tag.putInt("TotalOrders", 3);
        tag.putInt("CompletedOrders", 1);
        tag.putInt("DismissedOrders", 0);

        CompoundTag board = new CompoundTag();
        board.putBoolean("TrainPresent", true);
        board.putBoolean("ArrivalTrackingActive", false);
        board.putString("TrainName", "Iron Hauler");
        board.putString("SummaryStatus", "Unloading");
        board.putLong("CurrentTick", 0L);
        board.putInt("TotalOrders", 3);
        board.putInt("CompletedOrders", 1);
        board.putInt("DismissedOrders", 0);
        ListTag lines = new ListTag();
        lines.add(dispatchLine("minecraft:coal", "Coal", 64, 64, "load", "Done 64/64"));
        lines.add(dispatchLine("minecraft:iron_ingot", "Iron Ingot", 32, 12, "load", "Loading 12/32"));
        lines.add(dispatchLine("minecraft:redstone", "Redstone", 48, 0, "unload", "Waiting"));
        board.put("LineViews", lines);
        board.put("HistoryEntries", new ListTag());
        tag.put("DeliveryBoard", board);
    }

    public static void dispatchBoardGuide(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("dispatch_board_station_board", "Dispatch Board: live station log");
        scene.configureBasePlate(0, 0, 9);
        scene.scaleSceneView(0.7f);
        scene.setSceneOffsetY(-1);

        BlockPos board = util.grid().at(4, 2, 4);
        BlockPos pillar = util.grid().at(4, 1, 4);
        Selection boardSel = CreateStatsPonderScenes.select(util, board);
        Selection all = CreateStatsPonderScenes.select(util, board, pillar);

        CreateStatsPonderScenes.showBase(scene, util);

        scene.world().showSection(all, Direction.DOWN);
        scene.idle(8);
        scene.world().modifyBlockEntityNBT(boardSel, DispatchBoardBE.class, DispatchScenes::injectDispatchBoard);
        scene.idle(10);
        scene.overlay().showControls(util.vector().blockSurface(board, Direction.SOUTH), Pointing.RIGHT, 60)
                        .withItem(ModBlocks.DISPATCH_BOARD.asStack());
        CreateStatsPonderScenes.say(scene, util, "The Dispatch Board is a public live-ops display for a Create train station", board, PonderPalette.BLUE, 100);

        scene.overlay().showOutline(PonderPalette.INPUT, board, boardSel, 70);
        CreateStatsPonderScenes.say(scene, util, "Link it to a station and it shows the active train and every delivery order in real time", board, PonderPalette.INPUT, 105);
        scene.idle(8);

        scene.world().createItemEntity(util.vector().topOf(pillar).add(0, 1.4, 0), Vec3.ZERO, new ItemStack(Items.COAL));
        CreateStatsPonderScenes.say(scene, util, "Each row is a cargo order, with the item, load or unload, and a live progress bar toward its target", board, PonderPalette.GREEN, 110);
        scene.idle(8);

        scene.world().createItemEntity(util.vector().topOf(pillar).add(0, 1.4, 0), Vec3.ZERO, new ItemStack(Items.IRON_INGOT));
        scene.effects().indicateRedstone(board);
        CreateStatsPonderScenes.say(scene, util, "The header tracks the train: arriving, loading, unloading, plus completed and remaining order counts", board, PonderPalette.MEDIUM, 110);
        scene.idle(8);

        scene.overlay().showControls(util.vector().blockSurface(board, Direction.SOUTH), Pointing.UP, 50)
                        .withItem(ModItems.SMALL_SIGNAL_ANTENNA.asStack());
        CreateStatsPonderScenes.say(scene, util, "Stack boards into a wall for a big display, and add an antenna to monitor the station from afar", board, PonderPalette.OUTPUT, 115);
        scene.idle(8);

        scene.effects().indicateSuccess(board);
    }
}
