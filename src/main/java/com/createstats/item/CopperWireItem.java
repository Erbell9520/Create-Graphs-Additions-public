package com.createstats.item;

import com.createstats.block.PowerConnectorBlock;
import com.createstats.block.PowerDistributionUnitBlock;
import com.createstats.blockentity.PowerConnectorBE;
import com.createstats.blockentity.PowerDistributionUnitBE;
import com.createstats.power.PduStudWiring;
import com.createstats.power.PduStuds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;

public class CopperWireItem extends Item {
    private final com.createstats.power.WireTier tier;

    private static final Map<UUID, PduStudWiring.Endpoint> PENDING = new HashMap<>();

    private static final Map<UUID, com.createstats.power.CouplerSockets.End> PENDING_SOCKET =
            new HashMap<>();

    public CopperWireItem(Properties properties) {
        this(properties, com.createstats.power.WireTier.COPPER);
    }

    public com.createstats.power.WireTier tier() {
        return tier;
    }

    public CopperWireItem(Properties properties, com.createstats.power.WireTier tier) {
        super(properties);
        this.tier = tier;
    }

    public static void clearPending(UUID playerId) {
        PENDING_SOCKET.remove(playerId);
        PENDING.remove(playerId);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        final Player holder = context.getPlayer();
        return useWith(context, tier, CopperWireItem::spendOwnWire,
                holder != null && holder.isShiftKeyDown());
    }

    private static void spendOwnWire(Player player, ItemStack stack) {
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
    }

    public static PduStudWiring.Endpoint pendingOf(UUID playerId) {
        return PENDING.get(playerId);
    }

    @org.jetbrains.annotations.Nullable
    public static com.createstats.power.CouplerSockets.End pendingSocketOf(UUID playerId) {
        return PENDING_SOCKET.get(playerId);
    }

    public static InteractionResult useWith(UseOnContext context, com.createstats.power.WireTier tier,
                                            BiConsumer<Player, ItemStack> stock, boolean strip) {
        final Level level = context.getLevel();
        final BlockPos pos = context.getClickedPos();
        final Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }
        final net.minecraft.world.level.block.Block block = level.getBlockState(pos).getBlock();
        if (block instanceof PowerDistributionUnitBlock) {
            return useOnPanel(context, level, pos, player, tier, stock, strip);
        }
        if (block instanceof com.createstats.block.BatteryControllerBlock) {
            return useOnBatteryController(context, level, pos, player, tier, stock, strip);
        }
        if (block instanceof com.createstats.block.DiagnosticJunctionBlock junction
                && junction.kind() == com.createstats.block.DiagnosticJunctionBlock.Kind.WIRE) {
            return useOnDiagnosticJunction(context, level, pos, player, tier, stock, strip);
        }
        if (block instanceof com.createstats.block.MainBreakerBlock) {
            return useOnMainBreaker(context, level, pos, player, tier, stock, strip);
        }
        if (block instanceof com.createstats.block.ContactorBlock) {
            return useOnContactor(context, level, pos, player, tier, stock, strip);
        }
        if (block instanceof com.createstats.block.StormCoilBlock) {
            return useOnStormCoil(context, level, pos, player, tier, stock, strip);
        }

        if (block instanceof com.createstats.block.DiagnosticJunctionBlock) {
            return InteractionResult.PASS;
        }

        if (!(level.getBlockEntity(pos) instanceof com.createstats.power.FeWireTerminal terminal)) {
            return InteractionResult.PASS;
        }

        if (!takesWireOn(level, pos, level.getBlockState(pos), context.getClickedFace(),
                context.getClickLocation())) {
            return InteractionResult.PASS;
        }

        if (block instanceof com.createstats.block.WaterCoolerBlock
                && level.getBlockState(pos).getValue(com.createstats.block.WaterCoolerBlock.HALF)
                != net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        if (strip) {
            final int removed = terminal.links().size();
            terminal.dropWiresAndUnlinkAll();
            clearPending(player.getUUID());
            actionBar(player, removed > 0
                    ? Component.translatable("message.create_stats.copper_wire.stripped", removed)
                    : Component.translatable("message.create_stats.copper_wire.nothing_to_strip"));
            return InteractionResult.CONSUME;
        }

        if (com.createstats.compat.ForeignSpoolHandler.hasPendingBridge(player)) {
            return com.createstats.compat.ForeignSpoolHandler.finishBridge(context, player, level,
                    terminal, tier, stock);
        }
        return connect(context, player, level, PduStudWiring.Endpoint.connector(pos.asLong()),
                terminal.links().size(), terminal.maxLinks(), tier, stock);
    }

    @org.jetbrains.annotations.Nullable
    private static Direction wireFaceOf(Level level, BlockPos pos,
                                        net.minecraft.world.level.block.state.BlockState state) {
        final net.minecraft.world.level.block.Block block = state.getBlock();
        if (block instanceof com.createstats.block.CoffeeMachineBlock) {
            return com.createstats.block.CoffeeMachineBlock.wireFace(state);
        }
        if (block instanceof com.createstats.block.WaterCoolerBlock) {
            return com.createstats.block.WaterCoolerBlock.wireFace(state);
        }
        if (block instanceof com.createstats.block.AirFryerBlock) {
            return com.createstats.block.AirFryerBlock.wireFace(state);
        }
        if (block instanceof com.createstats.block.BulkWasherBlock) {
            return com.createstats.block.BulkWasherBlock.wireFace(state);
        }
        if (block instanceof com.createstats.block.ToasterBlock) {
            return com.createstats.block.ToasterBlock.wireFace(state);
        }
        if (block instanceof com.createstats.block.BreadSlicerBlock) {
            return com.createstats.block.BreadSlicerBlock.wireFace(state);
        }
        if (block instanceof com.createstats.block.OvenBlock) {
            return com.createstats.block.OvenBlock.wireFace(state);
        }

        if (block instanceof com.createstats.block.SparkCrownBlock) {
            return com.createstats.block.SparkCrownBlock.wireFace(state);
        }
        if (level.getBlockEntity(pos)
                instanceof com.createstats.blockentity.AnnouncementComputerBE brain) {
            return brain.backFace();
        }
        return null;
    }

    private static boolean takesWireOn(Level level, BlockPos pos,
                                       net.minecraft.world.level.block.state.BlockState state,
                                       Direction face, Vec3 hit) {
        if (state.getBlock() instanceof com.createstats.block.UniversalPowerCouplerBlock) {
            return com.createstats.power.CouplerSockets.endAt(state, pos, hit, face) != null;
        }
        final Direction only = wireFaceOf(level, pos, state);
        return only == null || face == only;
    }

    private static Component attachedMessage(Level level, BlockPos pos,
                                             net.minecraft.world.level.block.state.BlockState state,
                                             Direction face, Vec3 hit) {
        if (!(state.getBlock() instanceof com.createstats.block.UniversalPowerCouplerBlock)) {
            return Component.translatable("message.create_stats.copper_wire.attached");
        }
        return Component.translatable(
                com.createstats.power.CouplerSockets.endAt(state, pos, hit, face)
                        == com.createstats.power.CouplerSockets.End.FOREIGN
                        ? "message.create_stats.coupler.seam_socket_attached"
                        : "message.create_stats.coupler.grid_socket_attached");
    }

    private static InteractionResult useOnPanel(UseOnContext context, Level level, BlockPos pos,
                                                Player player, com.createstats.power.WireTier tier,
                                                BiConsumer<Player, ItemStack> stock, boolean strip) {
        final net.minecraft.world.level.block.state.BlockState clickedState = level.getBlockState(pos);
        final boolean cabinet = clickedState.getBlock() instanceof com.createstats.block.PduCabinetBlock;
        if (context.getClickedFace() != (cabinet ? clickedState.getValue(HorizontalDirectionalBlock.FACING).getOpposite() : Direction.UP)) {
            return InteractionResult.PASS;
        }
        final BlockPos clickedPos = pos;
        if (cabinet) pos = com.createstats.block.PduCabinetBlock.controllerPos(clickedState, pos);
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!(level.getBlockEntity(pos) instanceof PowerDistributionUnitBE panel)) {
            return InteractionResult.PASS;
        }
        final Direction facing = panel.getBlockState().hasProperty(HorizontalDirectionalBlock.FACING)
                ? panel.getBlockState().getValue(HorizontalDirectionalBlock.FACING) : Direction.NORTH;
        final Vec3 hit = context.getClickLocation();
        final int stud = cabinet
                ? com.createstats.block.PduCabinetBlock.pickedStud(clickedState, clickedPos, hit, context.getClickedFace())
                : PduStuds.pick(facing, hit.x - pos.getX(), hit.z - pos.getZ());
        if (!PduStudWiring.isStudIndex(stud)) {
            actionBar(player, Component.translatable("message.create_stats.copper_wire.no_stud"));
            return InteractionResult.CONSUME;
        }

        if (strip) {
            final int removed = panel.dropStudWires(stud);
            clearPending(player.getUUID());
            actionBar(player, removed > 0
                    ? Component.translatable("message.create_stats.copper_wire.stud_stripped",
                            removed, stud + 1)
                    : Component.translatable("message.create_stats.copper_wire.nothing_to_strip"));
            return InteractionResult.CONSUME;
        }

        if (com.createstats.compat.ForeignSpoolHandler.refuseBridgeFromStud(player)) {
            return InteractionResult.CONSUME;
        }
        return connect(context, player, level, PduStudWiring.Endpoint.stud(pos.asLong(), stud),
                panel.studLinkCount(stud), panel.maxLinksPerStud(), tier, stock);
    }

    private static InteractionResult useOnBatteryController(UseOnContext context, Level level,
                                                            BlockPos pos, Player player,
                                                            com.createstats.power.WireTier tier,
                                                            BiConsumer<Player, ItemStack> stock,
                                                            boolean strip) {
        if (context.getClickedFace() != Direction.UP) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!(level.getBlockEntity(pos) instanceof com.createstats.blockentity.BatteryControllerBE controller)) {
            return InteractionResult.PASS;
        }
        final Direction facing = controller.getBlockState().hasProperty(HorizontalDirectionalBlock.FACING)
                ? controller.getBlockState().getValue(HorizontalDirectionalBlock.FACING) : Direction.NORTH;
        final Vec3 hit = context.getClickLocation();
        final int port = com.createstats.power.BatteryControllerPorts.pick(
                facing, hit.x - pos.getX(), hit.z - pos.getZ());
        if (!com.createstats.power.BatteryControllerPorts.isPortIndex(port)) {
            actionBar(player, Component.translatable("message.create_stats.copper_wire.no_stud"));
            return InteractionResult.CONSUME;
        }

        if (strip) {
            final int removed = controller.dropStudWires(port);
            clearPending(player.getUUID());
            actionBar(player, removed > 0
                    ? Component.translatable("message.create_stats.copper_wire.port_stripped", removed,
                            com.createstats.power.BatteryControllerPorts.describe(port))
                    : Component.translatable("message.create_stats.copper_wire.nothing_to_strip"));
            return InteractionResult.CONSUME;
        }

        if (com.createstats.compat.ForeignSpoolHandler.refuseBridgeFromStud(player)) {
            return InteractionResult.CONSUME;
        }
        return connect(context, player, level, PduStudWiring.Endpoint.stud(pos.asLong(), port),
                controller.studLinkCount(port), controller.maxLinksPerStud(), tier, stock);
    }

    private static InteractionResult useOnMainBreaker(UseOnContext context, Level level, BlockPos pos,
                                                      Player player,
                                                      com.createstats.power.WireTier tier,
                                                      BiConsumer<Player, ItemStack> stock,
                                                      boolean strip) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!(level.getBlockEntity(pos) instanceof com.createstats.blockentity.MainBreakerBE breaker)) {
            return InteractionResult.PASS;
        }
        final Vec3 hit = context.getClickLocation();
        final int stud = com.createstats.power.MainBreakerStuds.pickIndex(
                level.getBlockState(pos).getValue(com.createstats.block.MainBreakerBlock.FACING),
                hit.x - pos.getX(), hit.y - pos.getY(), hit.z - pos.getZ());
        if (!com.createstats.power.MainBreakerStuds.isStudIndex(stud)) {
            actionBar(player, Component.translatable("message.create_stats.main_breaker.no_stud"));
            return InteractionResult.CONSUME;
        }

        if (strip) {
            final int removed = breaker.dropStudWires(stud);
            clearPending(player.getUUID());
            actionBar(player, removed > 0
                    ? Component.translatable("message.create_stats.main_breaker.stripped", removed,
                            studName(stud))
                    : Component.translatable("message.create_stats.copper_wire.nothing_to_strip"));
            return InteractionResult.CONSUME;
        }

        if (com.createstats.compat.ForeignSpoolHandler.refuseBridgeFromStud(player)) {
            return InteractionResult.CONSUME;
        }
        return connect(context, player, level, PduStudWiring.Endpoint.stud(pos.asLong(), stud),
                breaker.studLinkCount(stud), breaker.maxLinksPerStud(), tier, stock);
    }

    private static InteractionResult useOnContactor(UseOnContext context, Level level, BlockPos pos,
                                                    Player player, com.createstats.power.WireTier tier,
                                                    BiConsumer<Player, ItemStack> stock, boolean strip) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!(level.getBlockEntity(pos) instanceof com.createstats.blockentity.ContactorBE contactor)) {
            return InteractionResult.PASS;
        }
        final Vec3 hit = context.getClickLocation();
        final int stud = com.createstats.contactor.ContactorStuds.pick(level.getBlockState(pos),
                new Vec3(hit.x - pos.getX(), hit.y - pos.getY(), hit.z - pos.getZ()));
        if (!com.createstats.contactor.ContactorLogic.isStud(stud)) {
            actionBar(player, Component.translatable("message.create_stats.contactor.no_stud"));
            return InteractionResult.CONSUME;
        }
        if (strip) {
            final int removed = contactor.dropStudWires(stud);
            clearPending(player.getUUID());
            actionBar(player, removed > 0
                    ? Component.translatable("message.create_stats.contactor.stripped", removed,
                            com.createstats.contactor.ContactorLogic.studName(stud))
                    : Component.translatable("message.create_stats.copper_wire.nothing_to_strip"));
            return InteractionResult.CONSUME;
        }
        if (com.createstats.compat.ForeignSpoolHandler.refuseBridgeFromStud(player)) {
            return InteractionResult.CONSUME;
        }
        return connect(context, player, level, PduStudWiring.Endpoint.stud(pos.asLong(), stud),
                contactor.studLinkCount(stud), contactor.maxLinksPerStud(), tier, stock);
    }

    private static InteractionResult useOnStormCoil(UseOnContext context, Level level, BlockPos pos, Player player,
                                                    com.createstats.power.WireTier tier,
                                                    BiConsumer<Player, ItemStack> stock, boolean strip) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!(level.getBlockEntity(pos) instanceof com.createstats.blockentity.StormCoilBE coil)) {
            return InteractionResult.PASS;
        }
        final Vec3 hit = context.getClickLocation();
        final int stud = com.createstats.stormcoil.StormCoilStuds.pick(
                com.createstats.block.StormCoilBlock.turns(level.getBlockState(pos)),
                hit.x - pos.getX(), hit.y - pos.getY(), hit.z - pos.getZ());
        if (!com.createstats.stormcoil.StormCoilStuds.isStud(stud)) {
            actionBar(player, Component.translatable("message.create_stats.storm_coil.no_stud"));
            return InteractionResult.CONSUME;
        }
        if (strip) {
            final int removed = coil.dropStudWires(stud);
            clearPending(player.getUUID());
            actionBar(player, removed > 0
                    ? Component.translatable("message.create_stats.storm_coil.stripped", removed, coilStudName(stud))
                    : Component.translatable("message.create_stats.copper_wire.nothing_to_strip"));
            return InteractionResult.CONSUME;
        }
        if (com.createstats.compat.ForeignSpoolHandler.refuseBridgeFromStud(player)) {
            return InteractionResult.CONSUME;
        }
        return connect(context, player, level, PduStudWiring.Endpoint.stud(pos.asLong(), stud),
                coil.studLinkCount(stud), coil.maxLinksPerStud(), tier, stock);
    }

    private static Component coilStudName(int stud) {
        return Component.translatable(stud == com.createstats.stormcoil.StormCoilStuds.OUT
                ? "message.create_stats.storm_coil.stud.out"
                : "message.create_stats.storm_coil.stud.in");
    }

    private static Component studName(int stud) {
        return Component.translatable(stud == com.createstats.power.MainBreakerStuds.Stud.OUTPUT.index()
                ? "message.create_stats.main_breaker.stud.output"
                : "message.create_stats.main_breaker.stud.input");
    }

    private static InteractionResult useOnDiagnosticJunction(UseOnContext context, Level level,
                                                             BlockPos pos, Player player,
                                                             com.createstats.power.WireTier tier,
                                                             BiConsumer<Player, ItemStack> stock,
                                                             boolean strip) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!(level.getBlockEntity(pos) instanceof com.createstats.blockentity.DiagnosticJunctionBE junction)) {
            return InteractionResult.PASS;
        }
        final Direction facing = junction.getBlockState().hasProperty(com.createstats.block.DiagnosticJunctionBlock.FACING)
                ? junction.getBlockState().getValue(com.createstats.block.DiagnosticJunctionBlock.FACING) : Direction.NORTH;
        final Vec3 hit = context.getClickLocation();
        final int stud = com.createstats.power.DiagnosticJunctionStuds.pick(facing,
                hit.x - pos.getX(), hit.y - pos.getY(), hit.z - pos.getZ());
        if (!com.createstats.power.DiagnosticJunctionStuds.isStudIndex(stud)) {
            actionBar(player, Component.translatable("message.create_stats.copper_wire.no_stud"));
            return InteractionResult.CONSUME;
        }

        if (strip) {
            final int removed = junction.dropStudWires(stud);
            clearPending(player.getUUID());
            actionBar(player, removed > 0
                    ? Component.translatable("message.create_stats.copper_wire.stud_stripped", removed, stud + 1)
                    : Component.translatable("message.create_stats.copper_wire.nothing_to_strip"));
            return InteractionResult.CONSUME;
        }

        if (com.createstats.compat.ForeignSpoolHandler.hasPendingBridge(player)) {
            return com.createstats.compat.ForeignSpoolHandler.finishBridge(context, player, level,
                    junction, tier, stock);
        }
        return connect(context, player, level, PduStudWiring.Endpoint.stud(pos.asLong(), stud),
                junction.studLinkCount(stud), 1, tier, stock);
    }

    private static InteractionResult connect(UseOnContext context, Player player, Level level,
                                             PduStudWiring.Endpoint here, int hereLoad, int hereMax,
                                             com.createstats.power.WireTier tier,
                                             BiConsumer<Player, ItemStack> stock) {
        final PduStudWiring.Endpoint pending = PENDING.get(player.getUUID());
        if (pending == null) {
            if (hereLoad >= hereMax) {
                actionBar(player, Component.translatable("message.create_stats.copper_wire.full"));
                return InteractionResult.CONSUME;
            }

            final com.createstats.power.CouplerSockets.End socket =
                    com.createstats.power.CouplerSockets.endAt(level.getBlockState(BlockPos.of(here.pos())),
                            BlockPos.of(here.pos()), context.getClickLocation(), context.getClickedFace());

            if (socket != null
                    && level.getBlockEntity(BlockPos.of(here.pos()))
                            instanceof com.createstats.blockentity.UniversalPowerCouplerBE coupler) {
                final Component taken = coupler.refuseSocket(socket);
                if (taken != null) {
                    actionBar(player, taken);
                    return InteractionResult.CONSUME;
                }
            }
            PENDING.put(player.getUUID(), here);
            if (socket == null) {
                PENDING_SOCKET.remove(player.getUUID());
            } else {
                PENDING_SOCKET.put(player.getUUID(), socket);
            }
            level.playSound(null, BlockPos.of(here.pos()), SoundEvents.CHAIN_HIT, SoundSource.BLOCKS, 0.6f, 1.4f);
            final net.minecraft.world.level.block.entity.BlockEntity hereBe =
                    level.getBlockEntity(BlockPos.of(here.pos()));
            final boolean controller = here.isStud()
                    && hereBe instanceof com.createstats.blockentity.BatteryControllerBE;

            final boolean breaker = here.isStud()
                    && hereBe instanceof com.createstats.blockentity.MainBreakerBE;

            final boolean contactor = here.isStud()
                    && hereBe instanceof com.createstats.blockentity.ContactorBE;

            final boolean coil = here.isStud()
                    && hereBe instanceof com.createstats.blockentity.StormCoilBE;
            actionBar(player, controller
                    ? Component.translatable("message.create_stats.copper_wire.port_attached",
                            com.createstats.power.BatteryControllerPorts.describe(here.stud()))
                    : breaker
                    ? Component.translatable("message.create_stats.main_breaker.attached", studName(here.stud()))
                    : contactor
                    ? Component.translatable("message.create_stats.contactor.attached",
                            com.createstats.contactor.ContactorLogic.studName(here.stud()))
                    : coil
                    ? Component.translatable("message.create_stats.storm_coil.attached", coilStudName(here.stud()))
                    : here.isStud()
                    ? Component.translatable("message.create_stats.copper_wire.stud_attached", here.stud() + 1)
                    : attachedMessage(level, BlockPos.of(here.pos()),
                            level.getBlockState(BlockPos.of(here.pos())), context.getClickedFace(),
                            context.getClickLocation()));
            return InteractionResult.CONSUME;
        }

        final BlockPos pendingPos = BlockPos.of(pending.pos());
        final BlockPos herePos = BlockPos.of(here.pos());

        final com.createstats.power.FeWireTerminal firstTerminal = pending.isStud() ? null
                : level.getBlockEntity(pendingPos) instanceof com.createstats.power.FeWireTerminal t ? t : null;

        final com.createstats.power.FeStudPanel firstPanel = !pending.isStud() ? null
                : level.getBlockEntity(pendingPos) instanceof com.createstats.power.FeStudPanel p ? p : null;
        if (!pending.sameAs(here) && firstTerminal == null && firstPanel == null) {
            clearPending(player.getUUID());
            actionBar(player, Component.translatable("message.create_stats.copper_wire.lost_first"));
            return InteractionResult.CONSUME;
        }

        final boolean alreadyLinked = firstTerminal != null
                ? firstTerminal.links().contains(herePos)
                : firstPanel != null && firstPanel.isLinkedToConnector(herePos);
        final int pendingLoad = firstTerminal != null
                ? firstTerminal.links().size()
                : firstPanel != null ? firstPanel.studLinkCount(pending.stud()) : 0;
        final int pendingMax = firstTerminal != null
                ? firstTerminal.maxLinks()
                : firstPanel != null ? firstPanel.maxLinksPerStud()
                : com.createstats.power.ConnectorTier.ADVANCED.maxLinks();
        final PduStudWiring.Verdict verdict = PduStudWiring.check(pending, here,
                pendingPos.distSqr(herePos), PowerConnectorBE.wireRange(), alreadyLinked,
                pendingLoad, hereLoad, pendingMax, hereMax);
        switch (verdict) {
            case SAME_POINT -> {
                clearPending(player.getUUID());
                actionBar(player, Component.translatable("message.create_stats.copper_wire.cancelled"));
                return InteractionResult.CONSUME;
            }
            case BOTH_STUDS -> {
                actionBar(player, pendingPos.equals(herePos)
                        ? Component.translatable("message.create_stats.copper_wire.stud_to_stud")
                        : Component.translatable("message.create_stats.copper_wire.panel_to_panel"));
                return InteractionResult.CONSUME;
            }
            case BAD_STUD -> {
                clearPending(player.getUUID());
                actionBar(player, Component.translatable("message.create_stats.copper_wire.no_stud"));
                return InteractionResult.CONSUME;
            }
            case TOO_FAR -> {
                actionBar(player, Component.translatable("message.create_stats.copper_wire.too_far",
                        PowerConnectorBE.wireRange()));
                return InteractionResult.CONSUME;
            }
            case ALREADY_LINKED -> {
                clearPending(player.getUUID());

                final boolean contactor = level.getBlockEntity(pendingPos)
                        instanceof com.createstats.blockentity.ContactorBE
                        || level.getBlockEntity(herePos) instanceof com.createstats.blockentity.ContactorBE;
                actionBar(player, Component.translatable(contactor
                        ? "message.create_stats.contactor.one_per_terminal"
                        : "message.create_stats.copper_wire.already_linked"));
                return InteractionResult.CONSUME;
            }
            case FULL -> {
                actionBar(player, Component.translatable("message.create_stats.copper_wire.full"));
                return InteractionResult.CONSUME;
            }
            default -> {
            }
        }

        final Component wrongSocket = socketMismatch(level, pendingPos,
                PENDING_SOCKET.get(player.getUUID()), herePos,
                com.createstats.power.CouplerSockets.endAt(level.getBlockState(herePos), herePos,
                        context.getClickLocation(), context.getClickedFace()));
        final Component objection = wrongSocket != null ? wrongSocket
                : firstObjection(level, pendingPos, herePos);
        if (objection != null) {
            clearPending(player.getUUID());
            actionBar(player, objection);
            return InteractionResult.CONSUME;
        }

        final Component done;
        if (pending.isStud() || here.isStud()) {
            final PduStudWiring.Endpoint studEnd = pending.isStud() ? pending : here;
            final PduStudWiring.Endpoint wireEnd = pending.isStud() ? here : pending;
            final net.minecraft.world.level.block.entity.BlockEntity studBe = level.getBlockEntity(BlockPos.of(studEnd.pos()));

            if (!(level.getBlockEntity(BlockPos.of(wireEnd.pos()))
                    instanceof com.createstats.power.FeWireTerminal terminal)) {
                clearPending(player.getUUID());
                actionBar(player, Component.translatable("message.create_stats.copper_wire.lost_first"));
                return InteractionResult.CONSUME;
            }
            if (!(studBe instanceof com.createstats.power.FeStudPanel panel)) {
                clearPending(player.getUUID());
                actionBar(player, Component.translatable("message.create_stats.copper_wire.lost_first"));
                return InteractionResult.CONSUME;
            }
            panel.linkStud(studEnd.stud(), terminal, tier);

            done = studBe instanceof com.createstats.blockentity.BatteryControllerBE
                    ? Component.translatable("message.create_stats.copper_wire.port_linked",
                            com.createstats.power.BatteryControllerPorts.describe(studEnd.stud()))
                    : studBe instanceof com.createstats.blockentity.MainBreakerBE
                    ? Component.translatable("message.create_stats.main_breaker.linked",
                            studName(studEnd.stud()))
                    : studBe instanceof com.createstats.blockentity.ContactorBE
                    ? Component.translatable("message.create_stats.contactor.linked",
                            com.createstats.contactor.ContactorLogic.studName(studEnd.stud()))
                    : studBe instanceof com.createstats.blockentity.StormCoilBE
                    ? Component.translatable("message.create_stats.storm_coil.linked", coilStudName(studEnd.stud()))
                    : Component.translatable("message.create_stats.copper_wire.stud_linked", studEnd.stud() + 1);
        } else {
            if (!(level.getBlockEntity(pendingPos) instanceof com.createstats.power.FeWireTerminal first)
                    || !(level.getBlockEntity(herePos) instanceof com.createstats.power.FeWireTerminal second)) {
                clearPending(player.getUUID());
                actionBar(player, Component.translatable("message.create_stats.copper_wire.lost_first"));
                return InteractionResult.CONSUME;
            }
            first.addLink(second.terminalPos(), tier);
            second.addLink(first.terminalPos(), tier);
            done = Component.translatable("message.create_stats.copper_wire.linked");
        }
        clearPending(player.getUUID());
        stock.accept(player, context.getItemInHand());
        level.playSound(null, herePos, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 0.8f, 1.1f);
        actionBar(player, done);
        return InteractionResult.CONSUME;
    }

    @org.jetbrains.annotations.Nullable
    public static Component socketMismatch(
            Level level, BlockPos first,
            @org.jetbrains.annotations.Nullable com.createstats.power.CouplerSockets.End firstSocket,
            BlockPos second,
            @org.jetbrains.annotations.Nullable com.createstats.power.CouplerSockets.End secondSocket) {
        final Component fromFirst = socketSuits(level, firstSocket, second);
        return fromFirst != null ? fromFirst : socketSuits(level, secondSocket, first);
    }

    @org.jetbrains.annotations.Nullable
    private static Component socketSuits(
            Level level,
            @org.jetbrains.annotations.Nullable com.createstats.power.CouplerSockets.End socket,
            BlockPos far) {
        if (socket == null) {
            return null;
        }
        final boolean foreignFar =
                com.createstats.blockentity.UniversalPowerCouplerBE.isSeamTarget(level, far);

        final String refusal = com.createstats.power.CouplerSockets.refusalKey(socket, foreignFar, false);
        return refusal == null ? null : Component.translatable(refusal);
    }

    @org.jetbrains.annotations.Nullable
    public static Component firstObjection(Level level, BlockPos first, BlockPos second) {
        final Component fromFirst =
                level.getBlockEntity(first) instanceof com.createstats.power.FeWireTerminal a
                        ? a.refuseLink(level, second) : null;
        if (fromFirst != null) {
            return fromFirst;
        }
        return level.getBlockEntity(second) instanceof com.createstats.power.FeWireTerminal b
                ? b.refuseLink(level, first) : null;
    }

    private static void actionBar(Player player, Component message) {
        player.displayClientMessage(message.copy().withStyle(ChatFormatting.GOLD), true);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        final java.util.List<Component> detail = new java.util.ArrayList<>(1);

        if (com.createstats.compat.ForeignSpools.anyForeignWireLoaded()) {
            detail.add(com.createstats.util.CreateStatsTooltipHelper.note("tooltip.create_stats.wire_bridge"));
        }
        com.createstats.util.CreateStatsTooltipHelper.addBlockTooltip(tooltip, getDescriptionId(),
                List.of(com.createstats.util.CreateStatsTooltipHelper.spec("tooltip.create_stats.wire_rate",
                        com.createstats.power.FeRates.perTick(com.createstats.power.FeRates.wire(tier)))),
                detail);
    }
}
