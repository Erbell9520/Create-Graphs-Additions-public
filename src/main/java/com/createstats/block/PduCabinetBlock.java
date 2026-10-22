package com.createstats.block;

import com.createstats.blockentity.PduCabinetBE;
import com.createstats.guard.BlockFault;
import com.createstats.pducabinet.CabinetGeometry;
import com.createstats.pducabinet.CabinetShapeCache;
import com.createstats.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class PduCabinetBlock extends PowerDistributionUnitBlock {
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;
    private static final CabinetShapeCache<VoxelShape> OUTLINES=new CabinetShapeCache<>();
    private static final VoxelShape[] ENCLOSURES=new VoxelShape[8];

    public PduCabinetBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH).setValue(HALF, DoubleBlockHalf.LOWER));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING, HALF); }

    public static int turn(Direction facing) {
        return switch (facing) { case EAST -> 1; case SOUTH -> 2; case WEST -> 3; default -> 0; };
    }

    public static BlockPos controllerPos(BlockState state, BlockPos pos) {
        return state.getValue(HALF) == DoubleBlockHalf.UPPER ? pos.below() : pos;
    }

    @Nullable
    public static PduCabinetBE controller(BlockGetter level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof PduCabinetBlock)) return null;
        BlockPos base = controllerPos(state, pos);
        BlockState lower = level.getBlockState(base);
        return lower.is(state.getBlock()) && lower.getValue(HALF) == DoubleBlockHalf.LOWER
                && lower.getValue(FACING) == state.getValue(FACING)
                && level.getBlockEntity(base) instanceof PduCabinetBE be ? be : null;
    }

    public static int pickedStud(BlockState state, BlockPos clicked, Vec3 hit, Direction face) {
        if (face != state.getValue(FACING).getOpposite()) return -1;
        BlockPos base = controllerPos(state, clicked);
        return CabinetGeometry.pick(turn(state.getValue(FACING)), hit.x - base.getX(),
                hit.y - base.getY(), hit.z - base.getZ());
    }

    @Override @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        if (pos.getY() >= context.getLevel().getMaxBuildHeight() - 1
                || !context.getLevel().getBlockState(pos.above()).canBeReplaced(context)) return null;
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        level.setBlock(pos.above(), state.setValue(HALF, DoubleBlockHalf.UPPER), Block.UPDATE_ALL);
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        if (state.getValue(HALF) == DoubleBlockHalf.UPPER) {
            BlockState lower = level.getBlockState(pos.below());
            return lower.is(this) && lower.getValue(HALF) == DoubleBlockHalf.LOWER;
        }
        return level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighbor,
                                  LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        boolean lower = state.getValue(HALF) == DoubleBlockHalf.LOWER;
        if (direction == (lower ? Direction.UP : Direction.DOWN)) {
            return neighbor.is(this) && neighbor.getValue(HALF) != state.getValue(HALF)
                    && neighbor.getValue(FACING) == state.getValue(FACING) ? state : Blocks.AIR.defaultBlockState();
        }
        if (lower && direction == Direction.DOWN && !state.canSurvive(level, pos)) return Blocks.AIR.defaultBlockState();
        return super.updateShape(state, direction, neighbor, level, pos, neighborPos);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && player.isCreative() && state.getValue(HALF) == DoubleBlockHalf.UPPER) {
            BlockPos below = pos.below();
            if (level.getBlockState(below).is(this))
                level.setBlock(below, Blocks.AIR.defaultBlockState(), Block.UPDATE_SUPPRESS_DROPS | Block.UPDATE_ALL);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override @Nullable
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? ModBlockEntities.PDU_CABINET.create(pos, state) : null;
    }

    @Override @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return !level.isClientSide() && state.getValue(HALF) == DoubleBlockHalf.LOWER
                && type == ModBlockEntities.PDU_CABINET.get()
                ? (l, p, s, b) -> BlockFault.tick("PDU Cabinet", b, ((PduCabinetBE) b)::tick) : null;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        PduCabinetBE be=controller(level,pos);
        boolean doorTarget=hit.getDirection()==state.getValue(FACING);
        if(be!=null) {
            BlockPos base=be.getBlockPos();
            double[] p=CabinetGeometry.toModel(turn(state.getValue(FACING)),hit.getLocation().x-base.getX(),
                    hit.getLocation().y-base.getY(),hit.getLocation().z-base.getZ());
            doorTarget |= onDoor(CabinetGeometry.doorPoint(p,-be.doorAngle(0)));
        }
        if (!doorTarget && (stack.getItem() instanceof com.createstats.item.CableItem
                || (stack.getItem() instanceof com.createstats.item.CopperWireItem
                    && hit.getDirection() == state.getValue(FACING).getOpposite())))
            return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        return useWithoutItem(state,level,pos,player,hit).consumesAction()
                ? ItemInteractionResult.sidedSuccess(level.isClientSide()) : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        PduCabinetBE be = controller(level, pos);
        if (be == null) return InteractionResult.PASS;
        BlockPos base = be.getBlockPos();
        double[] p = CabinetGeometry.toModel(turn(state.getValue(FACING)),
                hit.getLocation().x - base.getX(), hit.getLocation().y - base.getY(), hit.getLocation().z - base.getZ());
        double[] closed = CabinetGeometry.doorPoint(p, -be.doorAngle(0));

        boolean latch = closed[0] >= 12.35/16 && closed[0] <= 13.15/16 && closed[1] >= 8.4/16
                && closed[1] <= 14.6/16 && closed[2] <= 2.95/16 && closed[2] >= 1.55/16;
        boolean front=hit.getDirection()==state.getValue(FACING);
        boolean screen=be.doorAngle(0)>80 && p[0]>=3.7/16 && p[0]<=12.3/16
                && p[1]>=2.6/16 && p[1]<=19.6/16 && p[2]>=4.8/16 && p[2]<=5.4/16;

        if (latch || onDoor(closed) || front && (!screen || player.isShiftKeyDown())) {
            if (!level.isClientSide()) {
                be.toggleDoor();
                level.playSound(null, base, be.doorOpen() ? SoundEvents.IRON_DOOR_OPEN : SoundEvents.IRON_DOOR_CLOSE,
                        SoundSource.BLOCKS, .45f, 1.05f);
            }
        } else if (level.isClientSide()) com.createstats.util.ClientScreenAccess.openPowerDistributionUnit(be);
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    private static boolean onDoor(double[] p) {
        return p[0]>=2.55/16 && p[0]<=13.45/16 && p[1]>=1.9/16
                && p[1]<=20.75/16 && p[2]>=1.4/16 && p[2]<=3.8/16;
    }

    private static VoxelShape box(double[] b, int turn, double offset) {
        double[] a = CabinetGeometry.toWorld(turn, b[0]/16, b[1]/16-offset, b[2]/16);
        double[] c = CabinetGeometry.toWorld(turn, b[3]/16, b[4]/16-offset, b[5]/16);
        return Shapes.box(Math.min(a[0],c[0]), Math.min(a[1],c[1]), Math.min(a[2],c[2]),
                Math.max(a[0],c[0]), Math.max(a[1],c[1]), Math.max(a[2],c[2]));
    }

    private static VoxelShape enclosure(BlockState state) {
        double offset = state.getValue(HALF) == DoubleBlockHalf.UPPER ? 1 : 0;
        int turn = turn(state.getValue(FACING));
        int key=turn*2+(int)offset;
        if(ENCLOSURES[key]!=null)return ENCLOSURES[key];
        VoxelShape shape = Shapes.empty();
        double[][] boxes = {{1.4,0,3.4,14.6,2.4,13.2}, {2,2.4,2.4,3.7,20.1,12.8},
                {12.3,2.4,2.4,14,20.1,12.8}, {3.7,2.4,4.92,12.3,20.1,12.8},
                {1.4,20.1,1.3,14.6,30.7,13.5}, {0.8,30.7,3.5,15.2,32,13.2},
                {2.9,4.45,12.42,10.8,30.65,15.6}, {6.7,1.55,14,9.3,3.8,15.85},
                {.2,7.1,5.6,2,11.9,10.4}, {14,7.1,5.6,15.8,11.9,10.4}};
        for (double[] b : boxes) {
            double lo = Math.max(b[1], offset*16), hi = Math.min(b[4], offset*16+16);
            if (lo < hi) shape = Shapes.joinUnoptimized(shape, box(new double[]{b[0],lo,b[2],b[3],hi,b[5]},turn,offset),BooleanOp.OR);
        }
        return ENCLOSURES[key]=shape.optimize();
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context) {
        return enclosure(state);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        PduCabinetBE be = controller(level, pos);
        double angle = be == null ? 0 : be.doorAngle(0);
        int turn=turn(state.getValue(FACING));
        boolean upper=state.getValue(HALF)==DoubleBlockHalf.UPPER;
        return OUTLINES.get(turn,upper,angle,pose->outline(state,pose));
    }

    private static VoxelShape outline(BlockState state,double angle) {
        int turn=turn(state.getValue(FACING));
        double offset=state.getValue(HALF)==DoubleBlockHalf.UPPER?1:0;
        VoxelShape shape=enclosure(state);

        for (int i=0;i<12;i++) shape=Shapes.joinUnoptimized(shape,doorBox(
                new double[]{2.85+i*10.3/12,2,2.85,2.85+(i+1)*10.3/12,20.65,3.5},angle,turn,offset),BooleanOp.OR);
        shape=Shapes.joinUnoptimized(shape,doorBox(new double[]{12.45,8.6,1.7,13.05,14.4,2.85},angle,turn,offset),BooleanOp.OR);
        return shape.optimize();
    }

    private static VoxelShape doorBox(double[] b,double angle,int turn,double offset) {
        double low=Math.max(b[1]/16,offset), high=Math.min(b[4]/16,offset+1);
        if(low>=high)return Shapes.empty();
        double x0=Double.POSITIVE_INFINITY,x1=Double.NEGATIVE_INFINITY;
        double z0=Double.POSITIVE_INFINITY,z1=Double.NEGATIVE_INFINITY;
        for(double x:new double[]{b[0],b[3]})for(double z:new double[]{b[2],b[5]}) {
            double[] q=CabinetGeometry.doorPoint(new double[]{x/16,0,z/16},angle);
            x0=Math.min(x0,q[0]);x1=Math.max(x1,q[0]);z0=Math.min(z0,q[2]);z1=Math.max(z1,q[2]);
        }
        return box(new double[]{x0*16,low*16,z0*16,x1*16,high*16,z1*16},turn,offset);
    }

    @Override
    public void appendHoverText(ItemStack stack, net.minecraft.world.item.Item.TooltipContext context,
                                List<net.minecraft.network.chat.Component> tooltip, TooltipFlag flag) {
        com.createstats.util.CreateStatsTooltipHelper.addBlockTooltip(tooltip,"block.create_stats.pdu_cabinet");
    }
}
