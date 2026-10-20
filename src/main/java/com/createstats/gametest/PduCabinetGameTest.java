package com.createstats.gametest;

import com.createstats.CreateStats;
import com.createstats.block.PduCabinetBlock;
import com.createstats.block.PowerConnectorBlock;
import com.createstats.blockentity.FeMotorBE;
import com.createstats.blockentity.PduCabinetBE;
import com.createstats.blockentity.PowerConnectorBE;
import com.createstats.block.MainBreakerBlock;
import com.createstats.blockentity.MainBreakerBE;
import com.createstats.pducabinet.CabinetGeometry;
import com.createstats.power.PduMode;
import com.createstats.power.WireTier;
import com.createstats.registry.ModBlockEntities;
import com.createstats.registry.ModBlocks;
import com.createstats.registry.ModItems;
import com.mojang.authlib.GameProfile;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@GameTestHolder(CreateStats.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PduCabinetGameTest {
    private PduCabinetGameTest() {}

    private static ServerPlayer player(GameTestHelper h) {
        return FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.randomUUID(),"cabinet_tester"));
    }

    private static PduCabinetBE place(GameTestHelper h,BlockPos relative,Direction facing,ServerPlayer player) {
        h.setBlock(relative.below(),Blocks.STONE);
        BlockPos floor=h.absolutePos(relative.below());
        player.setYRot(facing.getOpposite().toYRot());
        player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(ModBlocks.PDU_CABINET.asItem()));
        UseOnContext use=new UseOnContext(player,InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(floor).add(0,.5,0),Direction.UP,floor,false));
        var result=((BlockItem)ModBlocks.PDU_CABINET.asItem()).place(new BlockPlaceContext(use));
        check(h,result.consumesAction(),"cabinet did not place with its real block item");
        BlockPos base=h.absolutePos(relative);
        check(h,h.getLevel().getBlockState(base).getValue(PduCabinetBlock.FACING)==facing,"wrong placement facing");
        check(h,h.getLevel().getBlockEntity(base) instanceof PduCabinetBE,"lower controller missing");
        check(h,h.getLevel().getBlockState(base.above()).is(ModBlocks.PDU_CABINET.get()),"upper half missing");
        check(h,h.getLevel().getBlockState(base.above()).getValue(PduCabinetBlock.HALF)==DoubleBlockHalf.UPPER,"upper half wrong");
        check(h,h.getLevel().getBlockEntity(base.above())==null,"upper half created a second controller");
        return (PduCabinetBE)h.getLevel().getBlockEntity(base);
    }

    @GameTest(template="fe_bench",timeoutTicks=80)
    public static void openDoorQueriesReuseTheirShape(GameTestHelper h) {
        PduCabinetBE be=place(h,new BlockPos(4,1,4),Direction.NORTH,player(h));
        be.toggleDoor();
        h.runAtTickTime(28,()-> {
            var state=be.getBlockState();var pos=be.getBlockPos();
            long start=System.nanoTime();
            var first=state.getShape(h.getLevel(),pos);
            boolean reused=true;
            for(int i=0;i<64;i++)reused &= first==state.getShape(h.getLevel(),pos);
            System.out.println("[CABINET-SHAPE] 65 open queries: "+((System.nanoTime()-start)/1_000_000.0)+"ms; reused="+reused);
            check(h,reused,"open cabinet rebuilds its voxel union on every query");
            h.succeed();
        });
    }

    @GameTest(template="fe_bench",timeoutTicks=80)
    public static void heldItemFrontClickOpensTheDoor(GameTestHelper h) {
        ServerPlayer player=player(h);
        int n=0;
        for(Direction facing:new Direction[]{Direction.NORTH,Direction.EAST,Direction.SOUTH,Direction.WEST}) {
            PduCabinetBE be=place(h,new BlockPos(4+(n%2)*10,1,4+(n/2)*10),facing,player);

            player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(ModItems.COPPER_WIRE.get(),8));
            double[] p=CabinetGeometry.toWorld(PduCabinetBlock.turn(facing),8.0/16,18.5/16,2.85/16);
            BlockPos clicked=be.getBlockPos().above();
            var hit=new BlockHitResult(Vec3.atLowerCornerOf(be.getBlockPos()).add(p[0],p[1],p[2]),facing,clicked,false);
            var state=h.getLevel().getBlockState(clicked);
            var result=state.useItemOn(player.getMainHandItem(),h.getLevel(),player,InteractionHand.MAIN_HAND,hit);
            if(result==net.minecraft.world.ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION)
                state.useWithoutItem(h.getLevel(),player,hit);
            check(h,be.doorOpen(),"held-item front click did not open the door facing "+facing);
            check(h,player.getMainHandItem().getCount()==8,"door click spent wire");
            be.toggleDoor();
            player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(ModBlocks.SURFACE_CABLE.asItem(),8));
            state.useItemOn(player.getMainHandItem(),h.getLevel(),player,InteractionHand.MAIN_HAND,hit);
            check(h,be.doorOpen(),"front click with CableItem did not open facing "+facing);
            check(h,player.getMainHandItem().getCount()==8,"front click spent cable");
            be.toggleDoor();
            player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Blocks.STONE.asItem(),8));
            state.useItemOn(player.getMainHandItem(),h.getLevel(),player,InteractionHand.MAIN_HAND,hit);
            check(h,be.doorOpen(),"ordinary held block did not open facing "+facing);
            n++;
        }
        h.succeed();
    }

    @GameTest(template="fe_bench",timeoutTicks=80)
    public static void revealedScreenAndSwungLatchKeepTheirOwnClicks(GameTestHelper h) {
        ServerPlayer player=player(h);List<PduCabinetBE> cabinets=new ArrayList<>();
        int n=0;
        for(Direction facing:new Direction[]{Direction.NORTH,Direction.EAST,Direction.SOUTH,Direction.WEST}) {
            PduCabinetBE be=place(h,new BlockPos(4+(n%2)*10,1,4+(n/2)*10),facing,player);
            be.toggleDoor();cabinets.add(be);n++;
        }
        h.runAtTickTime(28,()-> {
            for(PduCabinetBE be:cabinets) {
                Direction facing=be.getBlockState().getValue(PduCabinetBlock.FACING);
                int turn=PduCabinetBlock.turn(facing);BlockPos base=be.getBlockPos();
                player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(ModItems.COPPER_WIRE.get(),8));
                double[] a=CabinetGeometry.toWorld(turn,.5,18.0/16,-1);
                double[] b=CabinetGeometry.toWorld(turn,.5,18.0/16,1);
                var screen=h.getLevel().clip(new net.minecraft.world.level.ClipContext(
                        Vec3.atLowerCornerOf(base).add(a[0],a[1],a[2]),Vec3.atLowerCornerOf(base).add(b[0],b[1],b[2]),
                        net.minecraft.world.level.ClipContext.Block.OUTLINE,net.minecraft.world.level.ClipContext.Fluid.NONE,player));
                check(h,screen.getBlockPos().equals(base.above()),"open door blocks the upper screen ray facing "+facing);
                var state=h.getLevel().getBlockState(screen.getBlockPos());
                state.useItemOn(player.getMainHandItem(),h.getLevel(),player,InteractionHand.MAIN_HAND,screen);
                check(h,be.doorOpen(),"revealed screen click closed the door facing "+facing);
                double[] latch=CabinetGeometry.doorPoint(new double[]{12.75/16,11.5/16,1.8/16},105);
                latch=CabinetGeometry.toWorld(turn,latch[0],latch[1],latch[2]);
                var latchHit=new BlockHitResult(Vec3.atLowerCornerOf(base).add(latch[0],latch[1],latch[2]),
                        facing.getOpposite(),base,false);
                be.getBlockState().useItemOn(player.getMainHandItem(),h.getLevel(),player,InteractionHand.MAIN_HAND,latchHit);
                check(h,!be.doorOpen(),"held wire swallowed the swung latch click facing "+facing);
                check(h,player.getMainHandItem().getCount()==8,"screen/latch clicks spent wire");
                double[] rear=CabinetGeometry.stud(0,turn);
                var rearHit=new BlockHitResult(Vec3.atLowerCornerOf(base).add(rear[0],rear[1],rear[2]),
                        facing.getOpposite(),base.above(),false);
                var rearState=h.getLevel().getBlockState(base.above());
                check(h,rearState.useItemOn(player.getMainHandItem(),h.getLevel(),player,InteractionHand.MAIN_HAND,rearHit)
                        ==net.minecraft.world.ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION,
                        "rear stud no longer yields to Copper Wire facing "+facing);
            }
            h.succeed();
        });
    }

    @GameTest(template="fe_bench",timeoutTicks=100)
    public static void bothHalvesWireAndBreakOnceInEveryFacing(GameTestHelper h) {
        ServerPlayer player=player(h);
        List<PduCabinetBE> cabinets=new ArrayList<>();
        List<PowerConnectorBE> terminals=new ArrayList<>();
        int n=0;
        for(Direction facing:new Direction[]{Direction.NORTH,Direction.EAST,Direction.SOUTH,Direction.WEST}) {
            BlockPos rel=new BlockPos(4+(n%2)*10,1,4+(n/2)*10);
            PduCabinetBE cabinet=place(h,rel,facing,player);
            cabinets.add(cabinet);
            BlockPos socketRel=rel.offset(3,1,0);
            h.setBlock(socketRel.below(),Blocks.STONE);
            h.setBlock(socketRel,ModBlocks.POWER_CONNECTOR.get().defaultBlockState().setValue(PowerConnectorBlock.FACING,Direction.UP));
            PowerConnectorBE terminal=h.getBlockEntity(socketRel);terminals.add(terminal);
            player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(ModItems.COPPER_WIRE.get(),16));

            int port=n%2==0?0:7;
            double[] p=CabinetGeometry.stud(port,PduCabinetBlock.turn(facing));
            BlockPos clicked=port<4?cabinet.getBlockPos().above():cabinet.getBlockPos();
            Vec3 hit=Vec3.atLowerCornerOf(cabinet.getBlockPos()).add(p[0],p[1],p[2]);
            ModItems.COPPER_WIRE.get().useOn(new UseOnContext(player,InteractionHand.MAIN_HAND,
                    new BlockHitResult(hit,facing.getOpposite(),clicked,false)));
            ModItems.COPPER_WIRE.get().useOn(new UseOnContext(player,InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(terminal.getBlockPos()).add(0,.5,0),Direction.UP,terminal.getBlockPos(),false)));
            check(h,cabinet.studLinkCount(port)==1,"native wire click did not attach to numbered port "+port);
            check(h,terminal.links().contains(cabinet.getBlockPos()),"connector did not record the lower controller");
            check(h,!terminal.links().contains(cabinet.getBlockPos().above()),"upper position leaked into wiring");
            check(h,player.getMainHandItem().getCount()==15,"one span did not spend exactly one wire");
            check(h,h.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK,cabinet.getBlockPos().above(),Direction.UP)==null,
                    "structural upper half exposes a second FE store");
            n++;
        }
        h.runAtTickTime(30,()-> {
            for(int i=0;i<cabinets.size();i++) {
                PduCabinetBE be=cabinets.get(i);BlockPos base=be.getBlockPos();
                h.getLevel().destroyBlock(i%2==0?base.above():base,true,player);
                check(h,h.getLevel().getBlockState(base).isAir() && h.getLevel().getBlockState(base.above()).isAir(),"orphaned half after breaking");
                check(h,terminals.get(i).links().isEmpty(),"remote terminal retained a dead cabinet wire");
            }
            var drops=h.getLevel().getEntitiesOfClass(ItemEntity.class,new AABB(Vec3.atLowerCornerOf(h.absolutePos(new BlockPos(0,0,0))),Vec3.atLowerCornerOf(h.absolutePos(new BlockPos(24,6,24)))));
            int items=drops.stream().filter(e->e.getItem().is(ModBlocks.PDU_CABINET.asItem())).mapToInt(e->e.getItem().getCount()).sum();
            int wires=drops.stream().filter(e->e.getItem().is(ModItems.COPPER_WIRE.get())).mapToInt(e->e.getItem().getCount()).sum();
            check(h,items==4,"four cabinets returned "+items+" cabinet items");
            check(h,wires==4,"four spans returned "+wires+" wire items");
            h.succeed();
        });
    }

    @GameTest(template="fe_bench",timeoutTicks=240)
    public static void realPowerReadoutsBreakersAndSavedControls(GameTestHelper h) {
        PduCabinetBE be=place(h,new BlockPos(4,1,4),Direction.NORTH,player(h));
        BlockPos motorPos=new BlockPos(9,1,4);
        h.setBlock(motorPos,ModBlocks.FE_MOTOR_MK1.get().defaultBlockState());
        FeMotorBE motor=h.getBlockEntity(motorPos);
        be.linkStud(0,motor,WireTier.HV);
        be.setMode(PduMode.AUTO);
        BlockPos base=be.getBlockPos();
        IEnergyStorage a=h.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK,base,Direction.WEST);
        IEnergyStorage b=h.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK,base,Direction.EAST);
        IEnergyStorage bus=h.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK,base,Direction.SOUTH);
        check(h,a!=null && b!=null && a!=b,"side bosses do not have independent inherited input views");
        check(h,bus!=null && bus.canExtract() && !bus.canReceive(),"rear BUS is not output only");
        check(h,h.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK,base,Direction.NORTH)==null,"front accepts an FE feed");

        for(Direction side:new Direction[]{Direction.WEST,Direction.EAST}) {
            BlockPos socket=new BlockPos(4,1,4).relative(side);
            h.setBlock(socket,ModBlocks.INDUSTRIAL_POWER_CONNECTOR.get().defaultBlockState()
                    .setValue(PowerConnectorBlock.FACING,side));
            BlockPos source=new BlockPos(side==Direction.WEST?2:7,1,8);
            h.setBlock(source,ModBlocks.CREATIVE_MAIN_BREAKER.get().defaultBlockState()
                    .setValue(MainBreakerBlock.FACING,Direction.NORTH).setValue(MainBreakerBlock.POWERED,true));
            MainBreakerBE breaker=h.getBlockEntity(source);
            PowerConnectorBE terminal=h.getBlockEntity(socket);
            breaker.linkStud(0,terminal,WireTier.HV);
        }
        final long[] total={0};final int[] inputA={0},inputB={0};
        h.onEachTick(()-> {
            if(h.getTick()==2) {
                ScrollValueBehaviour dial=motor.getBehaviour(ScrollValueBehaviour.TYPE);
                check(h,dial!=null,"motor dial unavailable");dial.setValue(64);
            }
            if(h.getTick()>0 && h.getTick()<100) {
                total[0]=Math.max(total[0],be.readout().ports().get(0).delivered());
                inputA[0]=Math.max(inputA[0],be.readout().inputA());inputB[0]=Math.max(inputB[0],be.readout().inputB());
            }
        });
        h.runAtTickTime(100,()-> {
            check(h,total[0]>0 && motor.energyHandle().getEnergyStored()>0,"inherited PDU did not deliver real FE to the motor");
            check(h,inputA[0]>0 && inputB[0]>0,"completed-tick input metering omitted a boss");
            check(h,!be.isLegTripped(0),"normal cabinet load tripped its breaker");
            be.setLegClosed(0,false);be.toggleDoor();
        });
        h.runAtTickTime(130,()-> {
            check(h,be.readout().ports().get(0).delivered()==0,"open breaker still reports real output");
            check(h,be.readout().ports().get(0).state().equals("OPEN"),"open breaker has wrong screen state");
            check(h,Math.abs(be.doorAngle(0)-105)<.001,"door did not reach its open pose");
            var registries=h.getLevel().registryAccess();
            var saved=be.saveWithFullMetadata(registries);
            BlockEntity restored=BlockEntity.loadStatic(base,be.getBlockState(),saved,registries);
            check(h,restored instanceof PduCabinetBE,"saved cabinet restored as another BE type");
            PduCabinetBE copy=(PduCabinetBE)restored;
            check(h,copy.mode()==PduMode.AUTO && !copy.isLegClosed(0) && copy.doorOpen(),"saved control settings were lost");
            check(h,copy.studLinkCount(0)==1,"saved cabinet lost its numbered wire");
            PduCabinetBE client=ModBlockEntities.PDU_CABINET.create(base,be.getBlockState());
            client.setLevel(h.getLevel());client.handleUpdateTag(be.getUpdateTag(registries),registries);
            check(h,client.readout().equals(be.readout()),"live instrument packet did not preserve all port values");
            check(h,client.doorOpen() && !client.isLegClosed(0),"door and breaker packet states differ");
            be.toggleDoor();
        });
        h.runAtTickTime(160,()-> {
            check(h,Math.abs(be.doorAngle(0))<.001,"door did not close");
            be.setMode(PduMode.MANUAL);
        });
        h.runAtTickTime(170,()-> {
            check(h,be.readout().mode().equals("MANUAL") && be.readout().ports().get(0).requested()==0,
                    "Manual screen reused cached Auto requests");
            System.out.println("[CABINET-GT] real delivery="+total[0]+" FE/t, input A="+inputA[0]+", B="+inputB[0]+"; breaker, save and packet checks passed");
            h.succeed();
        });
    }

    private static void check(GameTestHelper h,boolean condition,String message) { if(!condition)h.fail(message); }
}
