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

/** [1] */
public class CopperWireItem extends Item {

    /** The grade of wire this stack strings. Set per registered item, not per stack. */
    private final com.createstats.power.WireTier tier;

    /** First-clicked terminal per player, cleared on logout via {@link #clearPending(UUID)}. */
    private static final Map<UUID, PduStudWiring.Endpoint> PENDING = new HashMap<>();

    public CopperWireItem(Properties properties) {
        this(properties, com.createstats.power.WireTier.COPPER);
    }

    /** The grade this wire strings. */
    public com.createstats.power.WireTier tier() {
        return tier;
    }

    public CopperWireItem(Properties properties, com.createstats.power.WireTier tier) {
        super(properties);
        this.tier = tier;
    }

    public static void clearPending(UUID playerId) {
        PENDING.remove(playerId);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        final Level level = context.getLevel();
        final BlockPos pos = context.getClickedPos();
        final Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }
        final net.minecraft.world.level.block.Block block = level.getBlockState(pos).getBlock();
        if (block instanceof PowerDistributionUnitBlock) {
            return useOnPanel(context, level, pos, player);
        }
        // A Power Connector, or a machine that is its own connector (the FE Fluid Pump), are both
        // terminals a wire can land on and chain through.
        if (!(block instanceof PowerConnectorBlock)
                && !(block instanceof com.createstats.block.FluidPumpBlock)
                && !(block instanceof com.createstats.block.FeMotorBlock)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!(level.getBlockEntity(pos) instanceof com.createstats.power.FeWireTerminal terminal)) {
            return InteractionResult.PASS;
        }

        // Sneak: strip all wires off this terminal.
        if (player.isShiftKeyDown()) {
            final int removed = terminal.links().size();
            terminal.dropWiresAndUnlinkAll();
            PENDING.remove(player.getUUID());
            actionBar(player, removed > 0
                    ? Component.translatable("message.create_stats.copper_wire.stripped", removed)
                    : Component.translatable("message.create_stats.copper_wire.nothing_to_strip"));
            return InteractionResult.CONSUME;
        }

        return connect(context, player, level,
                PduStudWiring.Endpoint.connector(pos.asLong()), terminal.links().size());
    }

    /** [2] */
    private InteractionResult useOnPanel(UseOnContext context, Level level, BlockPos pos, Player player) {
        if (context.getClickedFace() != Direction.UP) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!(level.getBlockEntity(pos) instanceof PowerDistributionUnitBE panel)) {
            return InteractionResult.PASS;
        }
        final Direction facing = panel.getBlockState().hasProperty(HorizontalDirectionalBlock.FACING)
                ? panel.getBlockState().getValue(HorizontalDirectionalBlock.FACING) : Direction.NORTH;
        final Vec3 hit = context.getClickLocation();
        final int stud = PduStuds.pick(facing, hit.x - pos.getX(), hit.z - pos.getZ());
        if (!PduStudWiring.isStudIndex(stud)) {
            actionBar(player, Component.translatable("message.create_stats.copper_wire.no_stud"));
            return InteractionResult.CONSUME;
        }

        // Sneak: strip every wire off this one stud, which is how a leg is taken apart.
        if (player.isShiftKeyDown()) {
            final int removed = panel.dropStudWires(stud);
            PENDING.remove(player.getUUID());
            actionBar(player, removed > 0
                    ? Component.translatable("message.create_stats.copper_wire.stud_stripped",
                            removed, stud + 1)
                    : Component.translatable("message.create_stats.copper_wire.nothing_to_strip"));
            return InteractionResult.CONSUME;
        }

        return connect(context, player, level,
                PduStudWiring.Endpoint.stud(pos.asLong(), stud), panel.studLinkCount(stud));
    }

    /**
     * The two-click flow itself, for whichever kind of terminal was clicked.
     *
     * @param here     the terminal under the cursor
     * @param hereLoad how many wires it already carries, so a full one is refused on the first click
     *                 rather than after the player has walked to the second
     */
    private InteractionResult connect(UseOnContext context, Player player, Level level,
                                      PduStudWiring.Endpoint here, int hereLoad) {
        final PduStudWiring.Endpoint pending = PENDING.get(player.getUUID());
        if (pending == null) {
            if (hereLoad >= PowerConnectorBE.maxLinks()) {
                actionBar(player, Component.translatable("message.create_stats.copper_wire.full"));
                return InteractionResult.CONSUME;
            }
            PENDING.put(player.getUUID(), here);
            level.playSound(null, BlockPos.of(here.pos()), SoundEvents.CHAIN_HIT, SoundSource.BLOCKS, 0.6f, 1.4f);
            actionBar(player, here.isStud()
                    ? Component.translatable("message.create_stats.copper_wire.stud_attached", here.stud() + 1)
                    : Component.translatable("message.create_stats.copper_wire.attached"));
            return InteractionResult.CONSUME;
        }

        final BlockPos pendingPos = BlockPos.of(pending.pos());
        final BlockPos herePos = BlockPos.of(here.pos());
        // Whatever was clicked first has to still be there, and still be what it was.
        final com.createstats.power.FeWireTerminal firstTerminal = pending.isStud() ? null
                : level.getBlockEntity(pendingPos) instanceof com.createstats.power.FeWireTerminal t ? t : null;
        final PowerDistributionUnitBE firstPanel = !pending.isStud() ? null
                : level.getBlockEntity(pendingPos) instanceof PowerDistributionUnitBE p ? p : null;
        if (!pending.sameAs(here) && firstTerminal == null && firstPanel == null) {
            PENDING.remove(player.getUUID());
            actionBar(player, Component.translatable("message.create_stats.copper_wire.lost_first"));
            return InteractionResult.CONSUME;
        }

        // [3]
        final boolean alreadyLinked = firstTerminal != null
                ? firstTerminal.links().contains(herePos)
                : firstPanel != null && firstPanel.isLinkedToConnector(herePos);
        final int pendingLoad = firstTerminal != null
                ? firstTerminal.links().size()
                : firstPanel != null ? firstPanel.studLinkCount(pending.stud()) : 0;
        final PduStudWiring.Verdict verdict = PduStudWiring.check(pending, here,
                pendingPos.distSqr(herePos), PowerConnectorBE.wireRange(), alreadyLinked,
                pendingLoad, hereLoad, PowerConnectorBE.maxLinks());
        switch (verdict) {
            case SAME_POINT -> {
                PENDING.remove(player.getUUID());
                actionBar(player, Component.translatable("message.create_stats.copper_wire.cancelled"));
                return InteractionResult.CONSUME;
            }
            case BOTH_STUDS -> {
                actionBar(player, Component.translatable("message.create_stats.copper_wire.stud_to_stud"));
                return InteractionResult.CONSUME;
            }
            case BAD_STUD -> {
                PENDING.remove(player.getUUID());
                actionBar(player, Component.translatable("message.create_stats.copper_wire.no_stud"));
                return InteractionResult.CONSUME;
            }
            case TOO_FAR -> {
                actionBar(player, Component.translatable("message.create_stats.copper_wire.too_far",
                        PowerConnectorBE.wireRange()));
                return InteractionResult.CONSUME;
            }
            case ALREADY_LINKED -> {
                PENDING.remove(player.getUUID());
                actionBar(player, Component.translatable("message.create_stats.copper_wire.already_linked"));
                return InteractionResult.CONSUME;
            }
            case FULL -> {
                actionBar(player, Component.translatable("message.create_stats.copper_wire.full"));
                return InteractionResult.CONSUME;
            }
            default -> {
                // Falls through to the link below.
            }
        }

        // One end is a stud and the other is a connector, or both are connectors. Anything else was
        // refused above, so exactly one of these two shapes is left.
        final Component done;
        if (pending.isStud() || here.isStud()) {
            final PduStudWiring.Endpoint studEnd = pending.isStud() ? pending : here;
            final PduStudWiring.Endpoint wireEnd = pending.isStud() ? here : pending;
            if (!(level.getBlockEntity(BlockPos.of(studEnd.pos())) instanceof PowerDistributionUnitBE panel)
                    || !(level.getBlockEntity(BlockPos.of(wireEnd.pos())) instanceof PowerConnectorBE connector)) {
                PENDING.remove(player.getUUID());
                actionBar(player, Component.translatable("message.create_stats.copper_wire.lost_first"));
                return InteractionResult.CONSUME;
            }
            panel.linkStud(studEnd.stud(), connector, tier);
            done = Component.translatable("message.create_stats.copper_wire.stud_linked", studEnd.stud() + 1);
        } else {
            if (!(level.getBlockEntity(pendingPos) instanceof com.createstats.power.FeWireTerminal first)
                    || !(level.getBlockEntity(herePos) instanceof com.createstats.power.FeWireTerminal second)) {
                PENDING.remove(player.getUUID());
                actionBar(player, Component.translatable("message.create_stats.copper_wire.lost_first"));
                return InteractionResult.CONSUME;
            }
            first.addLink(second.terminalPos(), tier);
            second.addLink(first.terminalPos(), tier);
            done = Component.translatable("message.create_stats.copper_wire.linked");
        }
        PENDING.remove(player.getUUID());
        if (!player.getAbilities().instabuild) {
            context.getItemInHand().shrink(1);
        }
        level.playSound(null, herePos, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 0.8f, 1.1f);
        actionBar(player, done);
        return InteractionResult.CONSUME;
    }

    private static void actionBar(Player player, Component message) {
        player.displayClientMessage(message.copy().withStyle(ChatFormatting.GOLD), true);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        // Keyed off this item's own id rather than a fixed copper key, or every grade of wire would
        // describe itself as copper.
        final String key = getDescriptionId();
        tooltip.add(Component.translatable(key + ".tooltip").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(key + ".tooltip.usage").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("tooltip.create_stats.wire_rate", tier.multiplier())
                .withStyle(ChatFormatting.DARK_AQUA));
    }
}
