package com.createstats.battle.world;

import com.createstats.battle.core.BattleSystem;
import com.createstats.battle.core.SystemRules;
import com.createstats.flight.blockentity.RpmGovernorBE;
import com.createstats.flight.bridge.SableBridge;
import com.simibubi.create.content.contraptions.actors.seat.SeatBlock;
import com.simibubi.create.content.kinetics.base.GeneratingKineticBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.capabilities.Capabilities;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class BlockInfo {
    public static final TagKey<Block> IGNORE = tag(SystemRules.TAG_IGNORE);
    public static final TagKey<Block> NATURAL = tag(SystemRules.TAG_NATURAL);
    private static final TagKey<Block> ENVELOPE = tag("aeronautics:envelope");
    private static final TagKey<Block> LEVITITE = tag("aeronautics:levitite");
    private static final Map<BattleSystem, TagKey<Block>> SYSTEM_TAGS = new EnumMap<>(BattleSystem.class);

    static {
        for (BattleSystem s : BattleSystem.values()) SYSTEM_TAGS.put(s, tag(SystemRules.TAG_PREFIX + s.key()));
    }

    private BlockInfo() {
    }

    private static TagKey<Block> tag(String id) {
        return BlockTags.create(ResourceLocation.parse(id));
    }

    public static boolean counts(BlockState state, boolean ground) {
        if (state.isAir() || state.getBlock() instanceof LiquidBlock || state.is(BlockTags.FIRE)) return false;
        if (state.is(IGNORE)) return false;
        return !ground || !state.is(NATURAL);
    }

    public static boolean isGone(BlockState state) {
        return state.isAir() || state.is(BlockTags.FIRE) || state.getBlock() instanceof LiquidBlock;
    }

    public static boolean isFire(BlockState state) {
        return state.is(BlockTags.FIRE);
    }

    public static String id(Block block) {
        return BuiltInRegistries.BLOCK.getKey(block).toString();
    }

    @Nullable
    public static Block block(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null || !BuiltInRegistries.BLOCK.containsKey(rl)) return null;
        return BuiltInRegistries.BLOCK.get(rl);
    }

    public static String descriptionId(String blockId) {
        Block b = block(blockId);
        return b == null ? blockId : b.getDescriptionId();
    }

    public static BattleSystem classify(Level level, BlockPos pos, BlockState state, @Nullable LevelChunk chunk) {
        BlockEntity be = chunk != null ? chunk.getBlockEntity(pos) : SableBridge.getBlockEntityOnContraption(level, pos);
        Set<String> tags = new HashSet<>();
        for (Map.Entry<BattleSystem, TagKey<Block>> e : SYSTEM_TAGS.entrySet()) {
            if (state.is(e.getValue())) tags.add(e.getValue().location().toString());
        }
        if (state.is(ENVELOPE)) tags.add("aeronautics:envelope");
        if (state.is(LEVITITE)) tags.add("aeronautics:levitite");
        boolean energy = false;
        boolean storage = false;
        if (be != null) {
            try {
                energy = level.getCapability(Capabilities.EnergyStorage.BLOCK, pos, state, be, null) != null;
                storage = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, state, be, null) != null
                        || level.getCapability(Capabilities.FluidHandler.BLOCK, pos, state, be, null) != null;
            } catch (RuntimeException e) {
            }
        }
        boolean control = state.getBlock() instanceof SeatBlock || (be != null && (SableBridge.matchesGimbalSensor(be)
                || SableBridge.matchesAltitudeSensor(be) || SableBridge.matchesVelocitySensor(be)
                || SableBridge.matchesOpticalSensor(be) || SableBridge.matchesNavTable(be)));
        SystemRules.Descriptor d = new SystemRules.Descriptor(id(state.getBlock()), tags,
                be != null && SableBridge.matchesAnyPropeller(be),
                be instanceof GeneratingKineticBlockEntity,
                be instanceof RpmGovernorBE,
                be instanceof KineticBlockEntity,
                energy, storage, control);
        return SystemRules.classify(d);
    }
}
