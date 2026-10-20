package com.createstats.gui.home;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.AbstractCauldronBlock;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.AttachedStemBlock;
import net.minecraft.world.level.block.BambooStalkBlock;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.BasePressurePlateBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.ChiseledBookShelfBlock;
import net.minecraft.world.level.block.CraftingTableBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.DirtPathBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.EnchantingTableBlock;
import net.minecraft.world.level.block.EnderChestBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LavaCauldronBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.SugarCaneBlock;
import net.minecraft.world.level.block.TorchBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.block.state.properties.WallSide;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.common.Tags;

// read the client's own world, no packet
public final class PlanSampler {
    private PlanSampler() {
    }

    static final double BARRIER = 0.5;

    static final double WALL_FOOTPRINT = 0.5;

    static final double SMALL_FOOTPRINT = 0.3;

    static final int ROOF_SCAN = 24;

    static final int FLIGHT = 6;

    static final int DROP = 3;

    static final int FAR = 12;

    static final int SKY = 0;
    static final int ROOF = 1;
    static final int CANOPY = 2;

    public static PlanGrid sample(ClientLevel level, int x0, int z0, int cols, int rows, int key, boolean light) {
        final PlanGrid g = new PlanGrid(x0, z0, cols, rows);
        final BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        final int f = walkingLevel(level, x0, z0, cols, rows, key, p);
        g.floorY(f);
        for (int j = 0; j < rows; j++) {
            for (int i = 0; i < cols; i++) {
                column(level, g, x0 + i, z0 + j, f, light, p);
            }
        }
        flights(level, g, f, p);
        g.settle();
        if (light) {
            for (int z = z0; z < z0 + rows; z++) {
                for (int x = x0; x < x0 + cols; x++) {
                    if (g.has(x, z, PlanGrid.F_SPAWN) && !g.has(x, z, PlanGrid.F_ROOFED) && !touchesRoof(g, x, z)) {
                        g.flags[g.index(x, z)] &= ~PlanGrid.F_SPAWN;
                    }
                }
            }
        }
        return g;
    }

    static int walkingLevel(ClientLevel level, int x0, int z0, int cols, int rows, int key,
                            BlockPos.MutableBlockPos p) {
        int best = key;
        int bestScore = -1;
        for (int f = key - 1; f <= key + 1; f++) {
            int score = 0;
            for (int j = 0; j < rows; j += 2) {
                for (int i = 0; i < cols; i += 2) {
                    final int x = x0 + i;
                    final int z = z0 + j;
                    p.set(x, f, z);
                    if (!level.isLoaded(p)) {
                        continue;
                    }
                    if (level.getBlockState(p).getCollisionShape(level, p).isEmpty()) {
                        continue;
                    }
                    p.set(x, f + 1, z);
                    final BlockState feet = level.getBlockState(p);
                    final VoxelShape fs = feet.getCollisionShape(level, p);
                    if (!fs.isEmpty() && fs.max(Direction.Axis.Y) > BARRIER) {
                        continue;
                    }
                    p.set(x, f + 2, z);
                    if (!level.getBlockState(p).getCollisionShape(level, p).isEmpty()) {
                        continue;
                    }
                    score += roofed(level, x, f, z, p) ? 4 : 1;
                }
            }

            if (score > bestScore || (score == bestScore && f == key)) {
                best = f;
                bestScore = score;
            }
        }
        return best;
    }

    private static boolean touchesRoof(PlanGrid g, int x, int z) {
        return houseSide(g, x + 1, z) || houseSide(g, x - 1, z) || houseSide(g, x, z + 1) || houseSide(g, x, z - 1);
    }

    private static boolean houseSide(PlanGrid g, int x, int z) {
        return g.has(x, z, PlanGrid.F_ROOFED) && g.kind(x, z) != PlanGrid.POST;
    }

    private static void column(ClientLevel level, PlanGrid g, int x, int z, int f, boolean light,
                               BlockPos.MutableBlockPos p) {
        p.set(x, f, z);
        if (!level.isLoaded(p)) {
            return;
        }
        final BlockPos floorPos = p.immutable();
        final BlockPos feetPos = floorPos.above();
        final BlockPos headPos = feetPos.above();
        final BlockState floor = level.getBlockState(floorPos);
        final BlockState feet = level.getBlockState(feetPos);
        final BlockState head = level.getBlockState(headPos);
        switch (cover(level, x, f, z, p)) {
            case ROOF -> g.flag(x, z, PlanGrid.F_ROOFED);
            case CANOPY -> g.flag(x, z, PlanGrid.F_CANOPY);
            default -> {
            }
        }
        if (light) {
            light(level, g, x, z, floor, floorPos, feet, feetPos, head, headPos);
        }

        final int floorColour = colour(level, floor, floorPos);
        g.under(x, z, floorColour >= 0 ? floorColour : belowColour(level, x, f, z, p));

        final Block fb = feet.getBlock();
        final Block hb = head.getBlock();

        if (fb instanceof StairBlock && feet.getValue(StairBlock.HALF) == Half.BOTTOM && flightStart(level, x, f, z, feet)) {
            set(g, x, z, PlanGrid.STAIRS, colour(level, feet, feetPos));
            g.dir(x, z, dir(feet.getValue(StairBlock.FACING)));
            return;
        }
        if (fb instanceof DoorBlock || hb instanceof DoorBlock) {
            final BlockState door = fb instanceof DoorBlock ? feet : head;
            final BlockPos at = fb instanceof DoorBlock ? feetPos : headPos;
            set(g, x, z, PlanGrid.DOOR, colour(level, door, at));
            final byte closed = edge(door.setValue(DoorBlock.OPEN, false).getShape(level, at));
            final byte open = edge(door.setValue(DoorBlock.OPEN, true).getShape(level, at));
            g.doorEdges(x, z, closed < 0 ? PlanGrid.NORTH : closed, open < 0 ? PlanGrid.EAST : open);
            if (door.getValue(DoorBlock.OPEN)) {
                g.flag(x, z, PlanGrid.F_OPEN);
            }
            return;
        }
        if (fb instanceof FenceGateBlock) {
            set(g, x, z, PlanGrid.GATE, colour(level, feet, feetPos));
            final Direction d = feet.getValue(FenceGateBlock.FACING);
            if (d.getAxis() == Direction.Axis.X) {
                g.flag(x, z, PlanGrid.F_AXIS_Z);
            }
            if (feet.getValue(FenceGateBlock.OPEN)) {
                g.flag(x, z, PlanGrid.F_OPEN);
            }
            return;
        }
        if (fb instanceof LadderBlock) {
            set(g, x, z, PlanGrid.LADDER, colour(level, feet, feetPos));
            g.dir(x, z, dir(feet.getValue(LadderBlock.FACING)));
            return;
        }
        if (fb instanceof LeavesBlock || hb instanceof LeavesBlock) {
            final BlockState leaves = fb instanceof LeavesBlock ? feet : head;
            set(g, x, z, PlanGrid.FOLIAGE, colour(level, leaves, fb instanceof LeavesBlock ? feetPos : headPos));
            return;
        }
        final boolean paneFeet = glass(feet) || bars(feet);
        final boolean paneHead = glass(head) || bars(head);
        final VoxelShape feetShape = feet.getCollisionShape(level, feetPos);
        final VoxelShape headShape = head.getCollisionShape(level, headPos);
        final double feetTop = feetShape.isEmpty() ? 0 : feetShape.max(Direction.Axis.Y);
        final boolean feetBlocks = feetTop > BARRIER;
        final boolean headWall = wallLike(head, headShape);
        final boolean feetThin = thin(fb);
        if ((paneFeet || paneHead) && (feetBlocks || paneFeet) && (headWall || paneHead)) {
            final int wallColour = !paneFeet ? colour(level, feet, feetPos) : !paneHead ? colour(level, head, headPos) : -1;
            set(g, x, z, PlanGrid.WINDOW, wallColour);
            final BlockState pane = paneFeet ? feet : head;
            connections(g, x, z, pane);
            if (bars(pane)) {
                g.flag(x, z, PlanGrid.F_BARS);
            } else {
                final int tint = colour(level, pane, paneFeet ? feetPos : headPos);
                if (tint >= 0) {
                    g.accent(x, z, tint);
                }
            }
            return;
        }
        if (fb instanceof FenceBlock && (hb instanceof BasePressurePlateBlock || hb instanceof CarpetBlock
                || head.is(BlockTags.WOOL_CARPETS))) {
            final int c = colour(level, head, headPos);
            set(g, x, z, PlanGrid.RAISED, c >= 0 ? c : colour(level, feet, feetPos));
            g.sym(x, z, PlanGrid.SYM_TABLE);
            return;
        }
        if (feetThin) {
            set(g, x, z, PlanGrid.FENCE, glass(feet) ? PlanPainter.GLASS : colour(level, feet, feetPos));
            connections(g, x, z, feet);
            return;
        }
        if (fb instanceof TrapDoorBlock) {
            final boolean open = feet.getValue(TrapDoorBlock.OPEN);
            final int c = colour(level, feet, feetPos);
            if (open) {
                floorAt(level, g, x, z, f, floor, floorPos, p);
                g.sym(x, z, PlanGrid.SYM_THIN);
                g.accent(x, z, c);
                final byte e = edge(feet.getShape(level, feetPos));
                g.dir(x, z, e < 0 ? dir(feet.getValue(TrapDoorBlock.FACING)) : e);
            } else if (feet.getValue(TrapDoorBlock.HALF) == Half.TOP) {
                set(g, x, z, PlanGrid.RAISED, c);
                g.sym(x, z, PlanGrid.SYM_HATCH);
            } else {
                floorAt(level, g, x, z, f, floor, floorPos, p);
                g.sym(x, z, PlanGrid.SYM_HATCH);
                g.accent(x, z, c);
            }
            return;
        }

        if (feetBlocks && headWall) {
            final int c = colour(level, feet, feetPos);
            set(g, x, z, PlanGrid.WALL, c >= 0 ? c : colour(level, head, headPos));
            if (natural(feet) || natural(head)) {
                g.flag(x, z, PlanGrid.F_NATURAL);
            }
            return;
        }

        if (feetBlocks && footprint(feetShape) >= SMALL_FOOTPRINT) {
            final int c = colour(level, feet, feetPos);
            set(g, x, z, PlanGrid.RAISED, c >= 0 ? c : glass(feet) ? PlanPainter.GLASS : 0x999999);
            symbol(g, x, z, feet);
            if (natural(feet)) {
                g.flag(x, z, PlanGrid.F_NATURAL);
            }
            return;
        }

        if (!feet.getFluidState().isEmpty() && feetShape.isEmpty()) {
            set(g, x, z, feet.getFluidState().is(net.minecraft.tags.FluidTags.LAVA) ? PlanGrid.LAVA : PlanGrid.WATER,
                    colour(level, feet, feetPos));
            if (!floor.getFluidState().isEmpty() && floor.getCollisionShape(level, floorPos).isEmpty()) {
                g.flag(x, z, PlanGrid.F_DEEP);
            }
            return;
        }
        floorAt(level, g, x, z, f, floor, floorPos, p);
        if (g.kind(x, z) != PlanGrid.FLOOR && g.kind(x, z) != PlanGrid.GROUND) {
            return;
        }

        if (feet.is(BlockTags.WOOL_CARPETS) || fb instanceof CarpetBlock) {
            g.accent(x, z, colour(level, feet, feetPos));
        } else if (fb instanceof FlowerPotBlock pot) {
            g.sym(x, z, PlanGrid.SYM_POT);
            final Block plant = pot.getPotted();
            if (plant != Blocks.AIR) {
                g.accent(x, z, colour(level, plant.defaultBlockState(), feetPos));
            }
        } else if (fb instanceof CandleBlock) {
            g.sym(x, z, PlanGrid.SYM_CANDLE);
            g.accent(x, z, colour(level, feet, feetPos));
            if (feet.getValue(CandleBlock.LIT)) {
                g.flag(x, z, PlanGrid.F_LIT);
            }
        } else if (fb instanceof TorchBlock) {
            g.sym(x, z, PlanGrid.SYM_CANDLE);
            g.accent(x, z, 0x8F7748);
            g.flag(x, z, PlanGrid.F_LIT);
        } else if (fb instanceof LanternBlock || hb instanceof LanternBlock) {
            lantern(g, x, z, fb instanceof LanternBlock ? feet : head);
        } else if (fb instanceof CropBlock crop) {
            g.sym(x, z, PlanGrid.SYM_CROP);
            final boolean ripe = crop.isMaxAge(feet);
            if (ripe) {
                g.flag(x, z, PlanGrid.F_GROWN);
            }
            g.accent(x, z, ripe && feet.is(Blocks.WHEAT) ? 0xD7B45A : 0x4E9A2A);
        } else if (fb instanceof StemBlock || fb instanceof AttachedStemBlock) {
            g.sym(x, z, PlanGrid.SYM_CROP);
            g.accent(x, z, 0x4E9A2A);
            if (fb instanceof AttachedStemBlock) {
                g.flag(x, z, PlanGrid.F_GROWN);
            }
        } else if (fb instanceof NetherWartBlock) {
            g.sym(x, z, PlanGrid.SYM_CROP);
            g.accent(x, z, 0xA83030);
            if (feet.getValue(NetherWartBlock.AGE) >= NetherWartBlock.MAX_AGE) {
                g.flag(x, z, PlanGrid.F_GROWN);
            }
        } else if (fb instanceof SugarCaneBlock || fb instanceof BambooStalkBlock) {
            g.sym(x, z, PlanGrid.SYM_CANE);
            g.accent(x, z, fb instanceof BambooStalkBlock ? 0x6CA83A : 0x8FBF4A);
        } else if (surface(feet, feetShape)) {
            final int c = colour(level, feet, feetPos);
            if (c >= 0) {
                g.colour[g.index(x, z)] = c;
            }
            if (BuiltInRegistries.BLOCK.getKey(fb).getPath().equals("photovoltaic_array")) {
                g.sym(x, z, PlanGrid.SYM_PANELS);
            }
        } else if (fb instanceof BushBlock || feet.is(BlockTags.FLOWERS) || feet.is(BlockTags.SAPLINGS)) {
            final int c = flowerColour(level, feet, feetPos);
            if (c >= 0) {
                g.sym(x, z, PlanGrid.SYM_PLANT);
                g.accent(x, z, c);
            }
        }
    }

    // every vanilla flower is PLANT green on a map, so name the petals. the old rule never fired once
    static int flowerColour(ClientLevel level, BlockState st, BlockPos pos) {
        final String name = BuiltInRegistries.BLOCK.getKey(st.getBlock()).getPath();
        final int byName = switch (name) {
            case "poppy", "red_tulip", "rose_bush" -> 0xD8262B;
            case "dandelion", "sunflower" -> 0xF2D33A;
            case "blue_orchid" -> 0x2FB8DA;
            case "allium", "lilac" -> 0xC66BD8;
            case "azure_bluet", "oxeye_daisy", "lily_of_the_valley", "white_tulip" -> 0xF2F2E8;
            case "orange_tulip", "torchflower" -> 0xE8862C;
            case "pink_tulip", "peony", "pink_petals" -> 0xEEA2C4;
            case "cornflower" -> 0x4A6EE0;
            case "wither_rose" -> 0x2A2A2A;
            case "cherry_sapling" -> 0xF0A6C8;
            default -> -1;
        };
        if (byName >= 0) {
            return byName;
        }
        if (!st.is(BlockTags.FLOWERS)) {
            return -1;
        }
        final int c = colour(level, st, pos);
        return c < 0 || c == MapColor.PLANT.col || c == MapColor.GRASS.col ? -1 : c;
    }

    private static void lantern(PlanGrid g, int x, int z, BlockState st) {
        g.sym(x, z, PlanGrid.SYM_LANTERN);
        if (st.is(Blocks.SOUL_LANTERN)) {
            g.accent(x, z, 0x62D8D8);
        }
    }

    private static void floorAt(ClientLevel level, PlanGrid g, int x, int z, int f, BlockState floor, BlockPos floorPos,
                                BlockPos.MutableBlockPos p) {
        final boolean roofed = g.has(x, z, PlanGrid.F_ROOFED);
        if (!floor.getFluidState().isEmpty() && floor.getCollisionShape(level, floorPos).isEmpty()) {
            set(g, x, z, floor.getFluidState().is(net.minecraft.tags.FluidTags.LAVA) ? PlanGrid.LAVA : PlanGrid.WATER,
                    colour(level, floor, floorPos));
            p.set(x, f - 1, z);
            if (level.isLoaded(p)) {
                final BlockState bed = level.getBlockState(p);
                if (!bed.getFluidState().isEmpty() && bed.getCollisionShape(level, p).isEmpty()) {
                    g.flag(x, z, PlanGrid.F_DEEP);
                }
            }
            return;
        }
        if (floor.getBlock() instanceof StairBlock && floor.getValue(StairBlock.HALF) == Half.BOTTOM
                && topStep(level, x, f, z, floor)) {
            set(g, x, z, PlanGrid.STAIRS_BELOW, colour(level, floor, floorPos));
            g.dir(x, z, dir(floor.getValue(StairBlock.FACING)));
            return;
        }
        if (!floor.getCollisionShape(level, floorPos).isEmpty()) {
            set(g, x, z, roofed ? PlanGrid.FLOOR : PlanGrid.GROUND, colour(level, floor, floorPos));
            final Block b = floor.getBlock();
            if (b instanceof DirtPathBlock) {
                g.flag(x, z, PlanGrid.F_PATH);
            } else if (b instanceof FarmBlock) {
                g.flag(x, z, PlanGrid.F_FURROW);
            } else if (b.builtInRegistryHolder().key().location().getPath().equals("photovoltaic_array")) {
                g.sym(x, z, PlanGrid.SYM_PANELS);
            }
            return;
        }
        // open sky drop = garden below, fade it. upper floors looked like a house floating in a black void

        for (int k = 1; k <= FAR; k++) {
            p.set(x, f - k, z);
            if (!level.isLoaded(p)) {
                break;
            }
            final BlockState below = level.getBlockState(p);
            if (k <= DROP && below.getBlock() instanceof StairBlock && below.getValue(StairBlock.HALF) == Half.BOTTOM) {
                set(g, x, z, PlanGrid.STAIRS_BELOW, colour(level, below, p));
                g.dir(x, z, dir(below.getValue(StairBlock.FACING)));
                return;
            }
            if (k <= DROP && below.getBlock() instanceof LadderBlock) {
                set(g, x, z, PlanGrid.LADDER, colour(level, below, p));
                g.dir(x, z, dir(below.getValue(LadderBlock.FACING)));
                return;
            }
            final boolean fluid = !below.getFluidState().isEmpty() && below.getCollisionShape(level, p).isEmpty();
            final boolean solid = !below.getCollisionShape(level, p).isEmpty();
            if (!fluid && !solid) {
                continue;
            }
            final boolean seenBelow = k > 1 && (!roofed || k > DROP);
            if (seenBelow) {
                set(g, x, z, roofed ? PlanGrid.FLOOR : PlanGrid.GROUND, colour(level, below, p));
                g.flag(x, z, PlanGrid.F_DEEP);
                g.dir(x, z, (byte) k);
                return;
            }
            if (fluid) {
                set(g, x, z, below.getFluidState().is(net.minecraft.tags.FluidTags.LAVA) ? PlanGrid.LAVA : PlanGrid.WATER,
                        colour(level, below, p));
                return;
            }
            if (k == 1) {
                set(g, x, z, roofed ? PlanGrid.FLOOR : PlanGrid.GROUND, colour(level, below, p));
                g.flag(x, z, PlanGrid.F_LOW);
            } else {
                set(g, x, z, PlanGrid.HOLE, colour(level, below, p));
                if (k == DROP) {
                    g.flag(x, z, PlanGrid.F_DEEP);
                }
            }
            return;
        }
        set(g, x, z, PlanGrid.HOLE, -1);
        g.flag(x, z, PlanGrid.F_DEEP);
    }

    private static void set(PlanGrid g, int x, int z, byte kind, int colour) {
        g.set(x, z, kind, colour);
    }

    private static boolean flightStart(ClientLevel level, int x, int f, int z, BlockState stair) {
        final Direction up = stair.getValue(StairBlock.FACING);
        if (nextStep(level, x + up.getStepX(), f + 2, z + up.getStepZ(), up) != null) {
            return true;
        }
        for (Direction d : Direction.Plane.HORIZONTAL) {
            final BlockState prev = level.getBlockState(new BlockPos(x - d.getStepX(), f, z - d.getStepZ()));
            if (prev.getBlock() instanceof StairBlock && prev.getValue(StairBlock.HALF) == Half.BOTTOM
                    && prev.getValue(StairBlock.FACING) == d) {
                return true;
            }
        }
        return false;
    }

    private static BlockState nextStep(ClientLevel level, int x, int y, int z, Direction from) {
        final BlockState st = level.getBlockState(new BlockPos(x, y, z));
        if (!(st.getBlock() instanceof StairBlock) || st.getValue(StairBlock.HALF) != Half.BOTTOM) {
            return null;
        }
        return st.getValue(StairBlock.FACING) == from.getOpposite() ? null : st;
    }

    private static boolean topStep(ClientLevel level, int x, int f, int z, BlockState stair) {
        final Direction up = stair.getValue(StairBlock.FACING);
        for (Direction d : Direction.Plane.HORIZONTAL) {
            if (d == up.getOpposite()) {
                continue;
            }
            final BlockState prev = level.getBlockState(new BlockPos(x - d.getStepX(), f - 1, z - d.getStepZ()));
            if (prev.getBlock() instanceof StairBlock && prev.getValue(StairBlock.HALF) == Half.BOTTOM
                    && prev.getValue(StairBlock.FACING) == d) {
                return true;
            }
        }
        return false;
    }

    private static void flights(ClientLevel level, PlanGrid g, int f, BlockPos.MutableBlockPos p) {
        final java.util.List<int[]> starts = new java.util.ArrayList<>();
        for (int z = g.z0; z < g.z0 + g.rows; z++) {
            for (int x = g.x0; x < g.x0 + g.cols; x++) {
                if (g.kind(x, z) == PlanGrid.STAIRS) {
                    starts.add(new int[] {x, z, g.dir(x, z)});
                }
            }
        }
        for (int[] s : starts) {
            int x = s[0];
            int z = s[1];
            Direction d = direction((byte) s[2]);
            for (int k = 1; k < FLIGHT && d != null; k++) {
                x += d.getStepX();
                z += d.getStepZ();
                if (!g.contains(x, z)) {
                    break;
                }
                p.set(x, f + 1 + k, z);
                if (!level.isLoaded(p)) {
                    break;
                }
                final BlockState st = nextStep(level, x, f + 1 + k, z, d);
                if (st == null) {
                    break;
                }
                d = st.getValue(StairBlock.FACING);
                g.set(x, z, PlanGrid.STAIRS, colour(level, st, p));
                g.dir(x, z, dir(d));
                g.sym(x, z, PlanGrid.SYM_NONE);
            }
        }
    }

    private static void symbol(PlanGrid g, int x, int z, BlockState st) {
        final Block b = st.getBlock();
        if (b instanceof BedBlock) {
            g.sym(x, z, PlanGrid.SYM_BED);
            g.dir(x, z, dir(st.getValue(BedBlock.FACING)));
            if (st.getValue(BedBlock.PART) == BedPart.HEAD) {
                g.flag(x, z, PlanGrid.F_HEAD);
            }
        } else if (b instanceof ChestBlock || b instanceof EnderChestBlock || st.is(Tags.Blocks.CHESTS)) {
            g.sym(x, z, PlanGrid.SYM_CHEST);
            if (st.hasProperty(ChestBlock.FACING)) {
                g.dir(x, z, dir(st.getValue(ChestBlock.FACING)));
            }
            if (st.hasProperty(ChestBlock.TYPE) && st.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
                g.flag(x, z, PlanGrid.F_DOUBLE);
            }
        } else if (b instanceof BarrelBlock || st.is(Tags.Blocks.BARRELS)) {
            g.sym(x, z, PlanGrid.SYM_BARREL);
            if (st.hasProperty(BarrelBlock.FACING)) {
                final Direction d = st.getValue(BarrelBlock.FACING);
                g.dir(x, z, d.getAxis() == Direction.Axis.Y ? PlanGrid.UP : dir(d));
            }
        } else if (b instanceof ChiseledBookShelfBlock) {
            g.sym(x, z, PlanGrid.SYM_SHELF);
            if (st.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
                g.dir(x, z, dir(st.getValue(BlockStateProperties.HORIZONTAL_FACING)));
            }
        } else if (st.is(Tags.Blocks.BOOKSHELVES)) {
            g.sym(x, z, PlanGrid.SYM_SHELF);
        } else if (b instanceof AbstractFurnaceBlock) {
            g.sym(x, z, PlanGrid.SYM_FURNACE);
            g.dir(x, z, dir(st.getValue(AbstractFurnaceBlock.FACING)));
            if (st.getValue(AbstractFurnaceBlock.LIT)) {
                g.flag(x, z, PlanGrid.F_LIT);
            }
        } else if (b instanceof CraftingTableBlock || st.is(Tags.Blocks.PLAYER_WORKSTATIONS_CRAFTING_TABLES)) {
            g.sym(x, z, PlanGrid.SYM_BENCH);
        } else if (b instanceof AnvilBlock) {
            g.sym(x, z, PlanGrid.SYM_ANVIL);
            g.dir(x, z, dir(st.getValue(AnvilBlock.FACING)));
        } else if (b instanceof AbstractCauldronBlock) {
            g.sym(x, z, PlanGrid.SYM_CAULDRON);
            if (b instanceof LavaCauldronBlock) {
                g.accent(x, z, 0xD8621C);
            } else if (st.is(Blocks.WATER_CAULDRON)) {
                g.accent(x, z, MapColor.WATER.col);
            } else if (st.is(Blocks.POWDER_SNOW_CAULDRON)) {
                g.accent(x, z, MapColor.SNOW.col);
            }
        } else if (b instanceof LecternBlock) {
            g.sym(x, z, PlanGrid.SYM_LECTERN);
            g.dir(x, z, dir(st.getValue(LecternBlock.FACING)));
        } else if (b instanceof EnchantingTableBlock) {
            g.sym(x, z, PlanGrid.SYM_BOOK);
        } else if (b instanceof StairBlock) {
            if (st.getValue(StairBlock.HALF) == Half.TOP) {
                g.sym(x, z, PlanGrid.SYM_TABLE);
            } else {
                g.sym(x, z, PlanGrid.SYM_SEAT);
                g.dir(x, z, dir(st.getValue(StairBlock.FACING)));
            }
        } else if (b instanceof SlabBlock && st.getValue(SlabBlock.TYPE) == SlabType.TOP) {
            g.sym(x, z, PlanGrid.SYM_TABLE);
        } else if (b instanceof FlowerPotBlock) {
            g.sym(x, z, PlanGrid.SYM_POT);
        } else if (BuiltInRegistries.BLOCK.getKey(b).getPath().equals("photovoltaic_array")) {
            g.sym(x, z, PlanGrid.SYM_PANELS);
        }
    }

    private static void connections(PlanGrid g, int x, int z, BlockState st) {
        if (st.getBlock() instanceof CrossCollisionBlock) {
            if (st.getValue(CrossCollisionBlock.NORTH)) {
                g.flag(x, z, PlanGrid.F_N);
            }
            if (st.getValue(CrossCollisionBlock.EAST)) {
                g.flag(x, z, PlanGrid.F_E);
            }
            if (st.getValue(CrossCollisionBlock.SOUTH)) {
                g.flag(x, z, PlanGrid.F_S);
            }
            if (st.getValue(CrossCollisionBlock.WEST)) {
                g.flag(x, z, PlanGrid.F_W);
            }
        } else if (st.getBlock() instanceof WallBlock) {
            if (st.getValue(WallBlock.NORTH_WALL) != WallSide.NONE) {
                g.flag(x, z, PlanGrid.F_N);
            }
            if (st.getValue(WallBlock.EAST_WALL) != WallSide.NONE) {
                g.flag(x, z, PlanGrid.F_E);
            }
            if (st.getValue(WallBlock.SOUTH_WALL) != WallSide.NONE) {
                g.flag(x, z, PlanGrid.F_S);
            }
            if (st.getValue(WallBlock.WEST_WALL) != WallSide.NONE) {
                g.flag(x, z, PlanGrid.F_W);
            }
        }
        final boolean eastWest = g.has(x, z, PlanGrid.F_E) || g.has(x, z, PlanGrid.F_W);
        final boolean northSouth = g.has(x, z, PlanGrid.F_N) || g.has(x, z, PlanGrid.F_S);
        if (northSouth && !eastWest) {
            g.flag(x, z, PlanGrid.F_AXIS_Z);
        }
    }

    private static boolean glass(BlockState st) {
        return st.is(Tags.Blocks.GLASS_BLOCKS) || st.is(Tags.Blocks.GLASS_PANES);
    }

    private static boolean bars(BlockState st) {
        return st.getBlock() instanceof IronBarsBlock && !glass(st);
    }

    private static double footprint(VoxelShape shape) {
        if (shape.isEmpty()) {
            return 0;
        }
        final AABB b = shape.bounds();
        return (b.maxX - b.minX) * (b.maxZ - b.minZ);
    }

    private static boolean surface(BlockState st, VoxelShape shape) {
        if (st.getBlock() instanceof SnowLayerBlock
                || st.getBlock() instanceof SlabBlock && st.getValue(SlabBlock.TYPE) == SlabType.BOTTOM) {
            return true;
        }
        if (shape.isEmpty()) {
            return false;
        }
        final AABB b = shape.bounds();
        return b.maxY <= BARRIER && (b.maxX - b.minX) * (b.maxZ - b.minZ) >= 0.9;
    }

    private static boolean thin(Block b) {
        return b instanceof CrossCollisionBlock || b instanceof WallBlock;
    }

    private static boolean wallLike(BlockState st, VoxelShape shape) {
        if (shape.isEmpty()) {
            return false;
        }
        if (thin(st.getBlock()) || glass(st)) {
            return true;
        }
        return footprint(shape) >= WALL_FOOTPRINT;
    }

    private static boolean natural(BlockState st) {
        return st.is(BlockTags.DIRT) || st.is(BlockTags.SAND) || st.is(BlockTags.BASE_STONE_OVERWORLD)
                || st.is(BlockTags.BASE_STONE_NETHER) || st.is(BlockTags.TERRACOTTA) || st.is(Tags.Blocks.GRAVELS)
                || st.is(Tags.Blocks.ORES) || st.is(Tags.Blocks.SANDSTONE_BLOCKS) || st.is(BlockTags.SNOW)
                || st.is(BlockTags.ICE) || st.is(BlockTags.NYLIUM) || st.is(Tags.Blocks.END_STONES)
                || st.is(Tags.Blocks.NETHERRACKS);
    }

    static int cover(ClientLevel level, int x, int f, int z, BlockPos.MutableBlockPos p) {
        final int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        boolean leaves = false;
        for (int y = f + 3; y < top && y <= f + 3 + ROOF_SCAN; y++) {
            p.set(x, y, z);
            final BlockState st = level.getBlockState(p);
            if (st.getBlock() instanceof LeavesBlock) {
                leaves = true;
            } else if (st.blocksMotion()) {
                return ROOF;
            }
        }
        return leaves ? CANOPY : SKY;
    }

    static boolean roofed(ClientLevel level, int x, int f, int z, BlockPos.MutableBlockPos p) {
        return cover(level, x, f, z, p) == ROOF;
    }

    static byte edge(VoxelShape shape) {
        if (shape.isEmpty()) {
            return -1;
        }
        final AABB b = shape.bounds();
        if (b.maxZ - b.minZ <= 0.25 && b.maxX - b.minX > 0.5) {
            return b.minZ < 0.5 && b.maxZ <= 0.5 ? PlanGrid.NORTH : b.minZ >= 0.5 ? PlanGrid.SOUTH : -1;
        }
        if (b.maxX - b.minX <= 0.25 && b.maxZ - b.minZ > 0.5) {
            return b.minX < 0.5 && b.maxX <= 0.5 ? PlanGrid.WEST : b.minX >= 0.5 ? PlanGrid.EAST : -1;
        }
        return -1;
    }

    static byte dir(Direction d) {
        return switch (d) {
            case NORTH -> PlanGrid.NORTH;
            case EAST -> PlanGrid.EAST;
            case SOUTH -> PlanGrid.SOUTH;
            case WEST -> PlanGrid.WEST;
            case UP -> PlanGrid.UP;
            default -> PlanGrid.DOWN;
        };
    }

    static Direction direction(byte d) {
        return switch (d) {
            case PlanGrid.NORTH -> Direction.NORTH;
            case PlanGrid.EAST -> Direction.EAST;
            case PlanGrid.SOUTH -> Direction.SOUTH;
            case PlanGrid.WEST -> Direction.WEST;
            default -> null;
        };
    }

    static int colour(ClientLevel level, BlockState st, BlockPos pos) {
        final MapColor c = st.getMapColor(level, pos);
        return c == MapColor.NONE ? -1 : c.col;
    }

    private static int belowColour(ClientLevel level, int x, int f, int z, BlockPos.MutableBlockPos p) {
        for (int k = 1; k <= DROP; k++) {
            p.set(x, f - k, z);
            if (!level.isLoaded(p)) {
                return -1;
            }
            final BlockState st = level.getBlockState(p);
            final int c = colour(level, st, p);
            if (c >= 0) {
                return c;
            }
        }
        return -1;
    }

    private static void light(ClientLevel level, PlanGrid g, int x, int z, BlockState floor, BlockPos floorPos,
                              BlockState feet, BlockPos feetPos, BlockState head, BlockPos headPos) {
        final int block = level.getBrightness(LightLayer.BLOCK, feetPos);
        g.light(x, z, block);
        if (block > 0) {
            return;
        }
        if (!floor.isValidSpawn(level, floorPos, EntityType.ZOMBIE) || !floor.isFaceSturdy(level, floorPos, Direction.UP)) {
            return;
        }
        if (!feet.getCollisionShape(level, feetPos).isEmpty() || !head.getCollisionShape(level, headPos).isEmpty()
                || !feet.getFluidState().isEmpty()) {
            return;
        }
        g.flag(x, z, PlanGrid.F_SPAWN);
    }
}
