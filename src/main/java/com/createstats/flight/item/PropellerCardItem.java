package com.createstats.flight.item;

import com.createstats.flight.bridge.SableBridge;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Locale;

/** [1] */
public class PropellerCardItem extends Item {

    /** Stable tag key in {@code DataComponents.CUSTOM_DATA}. */
    public static final String TAG_KEY = "FccPropellerCard";

    public PropellerCardItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockPos targetPos = findSupportedTargetPos(level, pos);

        if (targetPos == null) {
            Player player = context.getPlayer();
            if (!level.isClientSide() && player != null) {
                player.displayClientMessage(
                    Component.translatable("message.create_stats.propeller_card.incompatible")
                        .withStyle(ChatFormatting.RED),
                    true);
            }
            return InteractionResult.FAIL;
        }

        ItemStack stack = context.getItemInHand();
        FlightCardData previous = FlightCardData.get(stack, TAG_KEY);
        FlightCardData data = new FlightCardData(targetPos, level.dimension().location());
        FlightCardData.set(stack, TAG_KEY, data);

        Player player = context.getPlayer();
        if (!level.isClientSide() && player != null) {
            TargetKind kind = classifyTarget(level, targetPos);
            Component targetLabel = describeTarget(kind);
            Component msg;
            if (previous.isLinked() && previous.getLinkedPos() != null
                    && !previous.getLinkedPos().equals(targetPos)) {
                msg = Component.translatable("message.create_stats.propeller_card.relinked",
                        formatPos(previous.getLinkedPos()), targetLabel, formatPos(targetPos))
                    .withStyle(ChatFormatting.AQUA);
            } else {
                msg = Component.translatable("message.create_stats.propeller_card.linked", targetLabel, formatPos(targetPos))
                    .withStyle(ChatFormatting.AQUA);
            }
            player.displayClientMessage(msg, true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!player.isShiftKeyDown()) {
            return InteractionResultHolder.pass(stack);
        }
        FlightCardData data = FlightCardData.get(stack, TAG_KEY);
        if (data.isLinked()) {
            data.clear();
            FlightCardData.set(stack, TAG_KEY, data);
            if (!level.isClientSide()) {
                player.displayClientMessage(
                    Component.translatable("message.create_stats.propeller_card.cleared")
                        .withStyle(ChatFormatting.RED),
                    true);
            }
        } else if (!level.isClientSide()) {
            player.displayClientMessage(
                Component.translatable("message.create_stats.propeller_card.empty")
                    .withStyle(ChatFormatting.GRAY),
                true);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        FlightCardData data = FlightCardData.get(stack, TAG_KEY);
        if (data.isLinked()) {
            tooltip.add(Component.translatable("item.create_stats.propeller_card.tooltip.linked").withStyle(ChatFormatting.AQUA));
            tooltip.add(Component.literal(formatPos(data.getLinkedPos())).withStyle(ChatFormatting.DARK_AQUA));
            if (data.getDimension() != null) {
                tooltip.add(Component.literal(data.getDimension().toString()).withStyle(ChatFormatting.DARK_GRAY));
            }
        } else {
            tooltip.add(Component.translatable("item.create_stats.propeller_card.tooltip.hint")
                .withStyle(ChatFormatting.GRAY));
        }
        tooltip.add(Component.translatable("item.create_stats.propeller_card.tooltip.clear").withStyle(ChatFormatting.DARK_GRAY));
    }

    public static boolean isSupportedTarget(Level level, BlockPos pos) {
        return classifyTarget(level, pos) != TargetKind.NONE;
    }

    public static boolean isFinBearingTarget(Level level, BlockPos pos) {
        return classifyTarget(level, pos) == TargetKind.CREATE_BEARING;
    }

    public static boolean isWheelMountTarget(Level level, BlockPos pos) {
        return classifyTarget(level, pos) == TargetKind.WHEEL_MOUNT;
    }

    private static BlockPos findSupportedTargetPos(Level level, BlockPos clickedPos) {
        TargetKind direct = classifyTarget(level, clickedPos);
        if (direct == TargetKind.AERONAUTICS_PROPELLER_BEARING
                || direct == TargetKind.CREATE_BEARING
                || direct == TargetKind.WHEEL_MOUNT) {
            return clickedPos.immutable();
        }
        if (direct == TargetKind.PROPELLER_BLOCK) {
            BlockPos nearbyBearing = findAdjacentBearingOrWheelMount(level, clickedPos);
            return nearbyBearing != null ? nearbyBearing : clickedPos.immutable();
        }
        return findAdjacentBearingOrWheelMount(level, clickedPos);
    }

    private static BlockPos findAdjacentBearingOrWheelMount(Level level, BlockPos clickedPos) {
        for (Direction direction : Direction.values()) {
            BlockPos neighbor = clickedPos.relative(direction);
            TargetKind kind = classifyTarget(level, neighbor);
            if (kind == TargetKind.AERONAUTICS_PROPELLER_BEARING
                    || kind == TargetKind.CREATE_BEARING
                    || kind == TargetKind.WHEEL_MOUNT) {
                return neighbor.immutable();
            }
        }
        return null;
    }

    private static TargetKind classifyTarget(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        BlockEntity be = blockEntityAt(level, pos);
        if (isAeronauticsPropellerBearing(be)) return TargetKind.AERONAUTICS_PROPELLER_BEARING;
        if (isWheelMountTarget(state, be)) return TargetKind.WHEEL_MOUNT;
        if (isCreateBearingTarget(state, be)) return TargetKind.CREATE_BEARING;
        if (isPropellerBlockTarget(state, be)) return TargetKind.PROPELLER_BLOCK;
        return TargetKind.NONE;
    }

    private static BlockEntity blockEntityAt(Level level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be != null) return be;
        try {
            return SableBridge.getBlockEntityOnContraption(level, pos);
        } catch (RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    private static boolean isAeronauticsPropellerBearing(BlockEntity be) {
        if (be == null) return false;
        try {
            if (SableBridge.readProp(be) != null) return true;
        } catch (Throwable ignored) {
        }
        String name = be.getClass().getName().toLowerCase(Locale.ROOT);
        return name.contains("propellerbearing") || name.contains("propeller_bearing");
    }

    private static boolean isCreateBearingTarget(BlockState state, BlockEntity be) {
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        String namespace = id.getNamespace().toLowerCase(Locale.ROOT);
        String path = id.getPath().toLowerCase(Locale.ROOT);
        String blockClass = state.getBlock().getClass().getName().toLowerCase(Locale.ROOT);
        String beClass = be != null ? be.getClass().getName().toLowerCase(Locale.ROOT) : "";
        boolean bearingName = path.contains("bearing") || blockClass.contains("bearing") || beClass.contains("bearing");
        boolean createNamespace = "create".equals(namespace) || blockClass.contains("simibubi.create") || beClass.contains("simibubi.create");
        boolean knownCreateBearing = path.contains("mechanical_bearing")
                || path.contains("windmill_bearing")
                || path.contains("clockwork_bearing")
                || blockClass.contains("mechanicalbearing")
                || blockClass.contains("windmillbearing")
                || blockClass.contains("clockworkbearing")
                || beClass.contains("mechanicalbearing")
                || beClass.contains("windmillbearing")
                || beClass.contains("clockworkbearing");
        return bearingName && (createNamespace || knownCreateBearing);
    }

    private static boolean isWheelMountTarget(BlockState state, BlockEntity be) {
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        String namespace = id.getNamespace().toLowerCase(Locale.ROOT);
        String path = id.getPath().toLowerCase(Locale.ROOT);
        String blockClass = state.getBlock().getClass().getName().toLowerCase(Locale.ROOT);
        String beClass = be != null ? be.getClass().getName().toLowerCase(Locale.ROOT) : "";

        // Offroad wheel mounts are bundled with Aeronautics and exposed under offroad:* IDs.
        if ("offroad".equals(namespace) && ("wheel_mount".equals(path) || path.endsWith("/wheel_mount"))) {
            return true;
        }

        // Class-name fallback keeps support for contraption lookups where registry IDs may be indirect.
        boolean offroadClass = blockClass.contains("dev.ryanhcode.offroad") || beClass.contains("dev.ryanhcode.offroad");
        boolean wheelMountClass = blockClass.contains("wheel_mount") || beClass.contains("wheel_mount")
            || blockClass.contains("wheelmount") || beClass.contains("wheelmount");
        return offroadClass && wheelMountClass;
    }

    private static boolean isPropellerBlockTarget(BlockState state, BlockEntity be) {
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        String path = id.getPath().toLowerCase(Locale.ROOT);
        String blockClass = state.getBlock().getClass().getName().toLowerCase(Locale.ROOT);
        String beClass = be != null ? be.getClass().getName().toLowerCase(Locale.ROOT) : "";
        return path.contains("propeller") || blockClass.contains("propeller") || beClass.contains("propeller");
    }

    private static Component describeTarget(TargetKind kind) {
        return Component.translatable(switch (kind) {
            case AERONAUTICS_PROPELLER_BEARING -> "create_stats.propeller_card.target.bearing";
            case CREATE_BEARING -> "create_stats.propeller_card.target.create_bearing";
            case WHEEL_MOUNT -> "create_stats.propeller_card.target.wheel_mount";
            case PROPELLER_BLOCK -> "create_stats.propeller_card.target.propeller";
            case NONE -> "create_stats.propeller_card.target.none";
        });
    }

    private static String formatPos(BlockPos pos) {
        if (pos == null) return Component.translatable("create_stats.card.unset").getString();
        return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    }

    private enum TargetKind {
        NONE,
        AERONAUTICS_PROPELLER_BEARING,
        CREATE_BEARING,
        WHEEL_MOUNT,
        PROPELLER_BLOCK
    }
}
