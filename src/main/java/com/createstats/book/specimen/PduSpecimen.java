package com.createstats.book.specimen;

import com.createstats.block.PowerDistributionUnitBlock;
import com.createstats.blockentity.PowerDistributionUnitBE;
import com.createstats.book.scene.BlockFigure;
import com.createstats.book.scene.FigureWorld;
import com.createstats.gui.PowerDistributionUnitScreen;
import com.createstats.gui.pdu.PduScene3D;
import com.createstats.registry.ModBlockEntities;
import com.createstats.registry.ModBlocks;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@OnlyIn(Dist.CLIENT)
public final class PduSpecimen {
    private PduSpecimen() {
    }

    public static ManualSpecimen create(String id) {
        final Minecraft mc = Minecraft.getInstance();
        final BlockState state = ModBlocks.POWER_DISTRIBUTION_UNIT.get().defaultBlockState()
                .setValue(PowerDistributionUnitBlock.FACING, Direction.NORTH);
        final PowerDistributionUnitBE board = new PowerDistributionUnitBE(ModBlockEntities.POWER_DISTRIBUTION_UNIT.get(),
                PduPlates.BOARD, state);
        board.setLevel(mc.level);
        board.handleUpdateTag(PduPlates.fixtureTag(null, true), registries(mc));
        return PowerDistributionUnitScreen.specimen(board, PduPlates.view(id), PduPlates.W, PduPlates.H, null, mc.font,
                PduPlates.selected(id), PduPlates.selectAll(id), () -> PduPlates.anatomy(id), PduPlates.HISTORY_RANGE,
                PduPlates.FLOW.equals(id) ? scene(mc) : null);
    }

    private static HolderLookup.Provider registries(Minecraft mc) {
        return mc.level != null ? mc.level.registryAccess() : RegistryAccess.EMPTY;
    }

    private static PowerDistributionUnitScreen.SpecimenScene scene(Minecraft mc) {
        final BlockFigure figure = PduPlates.flowScene();
        final List<BlockFigure.Placement> placed = figure.placements();
        final List<BlockState> byIndex = new ArrayList<>(placed.size());
        for (final BlockFigure.Placement p : placed) {
            byIndex.add(BlockSpecimen.state(p.block(), p.props()));
        }

        final FigureWorld world = FigureWorld.of(figure);
        final Map<BlockPos, BlockState> states = new HashMap<>();
        final Map<BlockPos, BlockEntity> entities = new HashMap<>();
        for (int i = 0; i < placed.size(); i++) {
            final BlockFigure.Placement p = placed.get(i);
            if (PduPlates.isGlass(p)) {
                continue;
            }
            final BlockPos at = PduPlates.BOARD.offset((int) Math.round(p.x()), (int) Math.round(p.y()),
                    (int) Math.round(p.z()));
            final BlockState state = byIndex.get(i);
            states.put(at, state);
            BlockEntity entity = null;
            if (p.renderer() && p.fixture() != null) {
                entity = BlockFixtures.create(figure, i, state, cell -> {
                    final int k = world.indexAt(cell);
                    return k < 0 ? null : byIndex.get(k);
                });
            } else if (p.renderer() && state.getBlock() instanceof EntityBlock eb) {
                entity = eb.newBlockEntity(at, state);
            }
            if (entity != null) {
                if (mc.level != null) {
                    entity.setLevel(mc.level);
                }
                entities.put(at, entity);
            }
        }
        final BlockGetter scene = new BlockGetter() {
            @Override
            public BlockEntity getBlockEntity(BlockPos pos) {
                return entities.get(pos);
            }

            @Override
            public BlockState getBlockState(BlockPos pos) {
                return states.getOrDefault(pos, Blocks.AIR.defaultBlockState());
            }

            @Override
            public FluidState getFluidState(BlockPos pos) {
                return Fluids.EMPTY.defaultFluidState();
            }

            @Override
            public int getHeight() {
                return 384;
            }

            @Override
            public int getMinBuildHeight() {
                return -64;
            }
        };
        return (g, x, y, w, h, positions, devices, board, camera) -> {
            PduScene3D.renderStill(g, x, y, w, h, scene, positions, devices, board, camera);
            g.enableScissor(x, y, x + w, y + h);
            RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
            g.disableScissor();
        };
    }
}
