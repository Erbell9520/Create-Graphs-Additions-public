package com.createstats.pathfinder.menu;

import com.createstats.flight.bridge.SableBridge;
import com.createstats.pathfinder.blockentity.PathScannerBE;
import com.createstats.pathfinder.model.RiskPreset;
import com.createstats.pathfinder.model.UnknownPolicy;
import com.createstats.pathfinder.settings.PathScanSettings;
import com.createstats.registry.ModMenuTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import javax.annotation.Nullable;

public class PathScannerMenu extends AbstractContainerMenu {
    public static final int BUTTON_SCAN_NOW = 1;
    public static final int BUTTON_FORWARD_MINUS = 10;
    public static final int BUTTON_FORWARD_PLUS = 11;
    public static final int BUTTON_SIDE_MINUS = 12;
    public static final int BUTTON_SIDE_PLUS = 13;
    public static final int BUTTON_STRIDE_TOGGLE = 14;
    public static final int BUTTON_BUDGET_MINUS = 15;
    public static final int BUTTON_BUDGET_PLUS = 16;
    public static final int BUTTON_UNKNOWN_TOGGLE = 17;
    public static final int BUTTON_TWO_BLOCK_TOGGLE = 18;
    public static final int BUTTON_VERTICAL_MINUS = 19;
    public static final int BUTTON_VERTICAL_PLUS = 20;

    private final PathScannerBE scanner;

    public PathScannerMenu(int containerId, Inventory inv, RegistryFriendlyByteBuf extraData) {
        this(containerId, inv, lookupBE(inv, extraData.readBlockPos()));
    }

    public PathScannerMenu(int containerId, Inventory inv, @Nullable PathScannerBE scanner) {
        super(ModMenuTypes.PATH_SCANNER.get(), containerId);
        if (scanner == null) {
            throw new IllegalStateException("PathScannerMenu opened without a backing block entity");
        }
        this.scanner = scanner;
    }

    public PathScannerBE getScanner() {
        return scanner;
    }

    @Override
    public boolean stillValid(Player player) {
        return scanner.isPlayerInInteractionRange(player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (!scanner.isPlayerInInteractionRange(player)) return false;
        PathScanSettings settings = scanner.settings();
        switch (id) {
            case BUTTON_SCAN_NOW -> scanner.requestManualRescan();
            case BUTTON_FORWARD_MINUS -> scanner.setSettings(copy(settings, settings.forwardChunks() - 1,
                settings.sideChunks(), settings.chunkStride(), settings.chunksPerTick(), settings.verticalWindow(),
                settings.allowTwoBlockPaths(), settings.unknownPolicy()));
            case BUTTON_FORWARD_PLUS -> scanner.setSettings(copy(settings, settings.forwardChunks() + 1,
                settings.sideChunks(), settings.chunkStride(), settings.chunksPerTick(), settings.verticalWindow(),
                settings.allowTwoBlockPaths(), settings.unknownPolicy()));
            case BUTTON_SIDE_MINUS -> scanner.setSettings(copy(settings, settings.forwardChunks(),
                settings.sideChunks() - 1, settings.chunkStride(), settings.chunksPerTick(), settings.verticalWindow(),
                settings.allowTwoBlockPaths(), settings.unknownPolicy()));
            case BUTTON_SIDE_PLUS -> scanner.setSettings(copy(settings, settings.forwardChunks(),
                settings.sideChunks() + 1, settings.chunkStride(), settings.chunksPerTick(), settings.verticalWindow(),
                settings.allowTwoBlockPaths(), settings.unknownPolicy()));
            case BUTTON_STRIDE_TOGGLE -> scanner.setSettings(copy(settings, settings.forwardChunks(),
                settings.sideChunks(), settings.chunkStride() == 1 ? 2 : 1, settings.chunksPerTick(),
                settings.verticalWindow(), settings.allowTwoBlockPaths(), settings.unknownPolicy()));
            case BUTTON_BUDGET_MINUS -> scanner.setSettings(copy(settings, settings.forwardChunks(),
                settings.sideChunks(), settings.chunkStride(), settings.chunksPerTick() - 1, settings.verticalWindow(),
                settings.allowTwoBlockPaths(), settings.unknownPolicy()));
            case BUTTON_BUDGET_PLUS -> scanner.setSettings(copy(settings, settings.forwardChunks(),
                settings.sideChunks(), settings.chunkStride(), settings.chunksPerTick() + 1, settings.verticalWindow(),
                settings.allowTwoBlockPaths(), settings.unknownPolicy()));
            case BUTTON_VERTICAL_MINUS -> scanner.setSettings(copy(settings, settings.forwardChunks(),
                settings.sideChunks(), settings.chunkStride(), settings.chunksPerTick(), settings.verticalWindow() - 16,
                settings.allowTwoBlockPaths(), settings.unknownPolicy()));
            case BUTTON_VERTICAL_PLUS -> scanner.setSettings(copy(settings, settings.forwardChunks(),
                settings.sideChunks(), settings.chunkStride(), settings.chunksPerTick(), settings.verticalWindow() + 16,
                settings.allowTwoBlockPaths(), settings.unknownPolicy()));
            case BUTTON_UNKNOWN_TOGGLE -> scanner.setSettings(copy(settings, settings.forwardChunks(),
                settings.sideChunks(), settings.chunkStride(), settings.chunksPerTick(), settings.verticalWindow(),
                settings.allowTwoBlockPaths(), settings.unknownPolicy() == UnknownPolicy.BLOCK ? UnknownPolicy.WANDERING : UnknownPolicy.BLOCK));
            case BUTTON_TWO_BLOCK_TOGGLE -> scanner.setSettings(copy(settings, settings.forwardChunks(),
                settings.sideChunks(), settings.chunkStride(), settings.chunksPerTick(), settings.verticalWindow(),
                !settings.allowTwoBlockPaths(), settings.unknownPolicy()));
            default -> {
                return false;
            }
        }
        scanner.markDirtyAndSync();
        return true;
    }

    private static PathScanSettings copy(PathScanSettings settings, int forwardChunks, int sideChunks, int chunkStride,
                                         int chunksPerTick, int verticalWindow, boolean allowTwoBlockPaths,
                                         UnknownPolicy unknownPolicy) {
        RiskPreset riskPreset = settings.riskPreset() == null ? RiskPreset.BALANCED : settings.riskPreset();
        return new PathScanSettings(forwardChunks, sideChunks, chunkStride, chunksPerTick, verticalWindow,
            allowTwoBlockPaths, unknownPolicy, riskPreset, settings.cacheTtlTicks());
    }

    @Nullable
    private static PathScannerBE lookupBE(Inventory inv, BlockPos pos) {
        Level level = inv.player.level();
        BlockEntity blockEntity = SableBridge.getBlockEntityOnContraption(level, pos);
        if (!(blockEntity instanceof PathScannerBE) && level.isLoaded(pos)) {
            blockEntity = level.getBlockEntity(pos);
        }
        return blockEntity instanceof PathScannerBE scanner ? scanner : null;
    }
}
