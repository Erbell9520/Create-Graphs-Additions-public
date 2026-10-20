package com.createstats.power;

import com.createstats.CreateStats;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// our wire, start to end. their block never knows
public final class ForeignTerminalNode implements FeGridMember {
    public static final TagKey<Block> FOREIGN_CONNECTORS = TagKey.create(Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(CreateStats.MOD_ID, "foreign_wire_connectors"));

    private static final String[] CONNECTOR_CLASSES = {
            "com.mrh0.createaddition.blocks.connector.",
            "com.mrh0.createaddition.energy.IWireNode",

            "org.antarcticgardens.cna.content.electricity.connector.",
    };

    private static final Map<String, Boolean> RECOGNISED = new ConcurrentHashMap<>();

    private static final Map<Class<?>, Method> ANCHOR_METHODS = new ConcurrentHashMap<>();
    private static final Method NO_ANCHOR;

    static {
        Method placeholder = null;
        try {
            placeholder = ForeignTerminalNode.class.getDeclaredMethod("noAnchor");
        } catch (NoSuchMethodException ignored) {
        }
        NO_ANCHOR = placeholder;
    }

    private final Level level;
    private final BlockPos pos;
    private final BlockEntity foreign;
    private final IEnergyStorage mouth;
    private final int rate;
    private int lastFlow;

    private ForeignTerminalNode(Level level, BlockPos pos, BlockEntity foreign,
                                IEnergyStorage handler, int rate) {
        this.level = level;
        this.pos = pos;
        this.foreign = foreign;
        this.mouth = mouthFor(foreign, handler);
        this.rate = rate;
    }

    public static boolean isBridgeTarget(@Nullable BlockGetter level, @Nullable BlockPos pos) {
        if (level == null || pos == null) {
            return false;
        }
        final BlockEntity be = level.getBlockEntity(pos);
        if (be == null || be instanceof FeWireTerminal || be instanceof FeStudPanel) {
            return false;
        }
        if (level.getBlockState(pos).is(FOREIGN_CONNECTORS)) {
            return true;
        }
        final Class<?> type = be.getClass();
        return RECOGNISED.computeIfAbsent(type.getName(), ignored -> matches(type));
    }

    @Nullable
    public static ForeignTerminalNode at(@Nullable Level level, BlockPos pos, WireTier tier) {
        if (level == null || !level.isLoaded(pos) || !isBridgeTarget(level, pos)) {
            return null;
        }
        final BlockEntity be = level.getBlockEntity(pos);
        if (be == null) {
            return null;
        }
        final IEnergyStorage handler = handlerAt(level, pos);
        if (handler == null) {
            return null;
        }
        final WireTier grade = tier == null ? WireTier.COPPER : tier;
        return new ForeignTerminalNode(level, pos.immutable(), be, handler,
                com.createstats.blockentity.PowerConnectorBE.baseTransferRate() * grade.multiplier());
    }

    @Nullable
    private static IEnergyStorage handlerAt(Level level, BlockPos pos) {
        final IEnergyStorage any = level.getCapability(Capabilities.EnergyStorage.BLOCK, pos, null);
        if (any != null) {
            return any;
        }
        for (Direction face : Direction.values()) {
            final IEnergyStorage sided = level.getCapability(Capabilities.EnergyStorage.BLOCK, pos, face);
            if (sided != null) {
                return sided;
            }
        }
        return null;
    }

    public static Vec3 anchorOf(@Nullable BlockGetter level, BlockPos pos) {
        final Vec3 centre = new Vec3(0.5, 0.5, 0.5);
        if (level == null) {
            return centre;
        }
        final BlockEntity be = level.getBlockEntity(pos);
        if (be == null) {
            return centre;
        }
        final Method anchor = ANCHOR_METHODS.computeIfAbsent(be.getClass(), ForeignTerminalNode::findAnchor);
        if (anchor == NO_ANCHOR) {
            return centre;
        }
        try {
            final Object value = anchor.getParameterCount() == 1
                    ? anchor.invoke(be, 0) : anchor.invoke(be);
            if (!(value instanceof Vec3 point)) {
                return centre;
            }

            return anchor.getParameterCount() == 1 ? centre.add(point) : point;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return centre;
        }
    }

    private static Method findAnchor(Class<?> type) {
        try {
            return type.getMethod("getNodeOffset", int.class);
        } catch (NoSuchMethodException ignored) {
        }
        try {
            return type.getMethod("getConnectionPoint");
        } catch (NoSuchMethodException ignored) {
            return NO_ANCHOR;
        }
    }

    private static void noAnchor() {
    }

    private static boolean matches(Class<?> type) {
        for (Class<?> step = type; step != null && step != Object.class; step = step.getSuperclass()) {
            if (matchesName(step.getName())) {
                return true;
            }
            for (Class<?> face : step.getInterfaces()) {
                if (matchesName(face.getName())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean matchesName(String className) {
        for (String prefix : CONNECTOR_CLASSES) {
            if (className.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    public static IEnergyStorage asMouth(@Nullable Level level, BlockPos pos, IEnergyStorage handler) {
        if (handler == null || !isBridgeTarget(level, pos)) {
            return handler;
        }
        return mouthFor(level == null ? null : level.getBlockEntity(pos), handler);
    }

    public static IEnergyStorage mouth(IEnergyStorage handler) {
        return new Mouth(handler);
    }

    private static IEnergyStorage mouthFor(@Nullable BlockEntity foreign, IEnergyStorage handler) {
        if (NewAgeFeed.isNetworkMouth(foreign, handler)) {
            return NewAgeFeed.mouth(foreign, handler);
        }
        return new Mouth(handler);
    }

    public BlockPos pos() {
        return pos;
    }

    public int lastFlow() {
        return lastFlow;
    }

    @Override
    public long nodeKey() {
        return pos.asLong();
    }

    @Override
    public int storedEnergy() {
        return 0;
    }

    @Override
    public void setStoredEnergy(int amount) {
    }

    @Override
    public int bufferCapacity() {
        return 0;
    }

    @Override
    public int transferRate() {
        return rate;
    }

    @Override
    public IEnergyStorage attachedStorage() {
        return mouth;
    }

    @Override
    public boolean isGone(Level owner) {
        return foreign.isRemoved() || foreign.getLevel() != owner || level != owner;
    }

    @Override
    public void setLastFlow(int flow) {
        this.lastFlow = flow;
    }

    @Override
    public void invalidateNetwork() {
    }

    record Mouth(IEnergyStorage handler) implements IEnergyStorage {
        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            return handler.receiveEnergy(maxReceive, simulate);
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            return handler.extractEnergy(maxExtract, simulate);
        }

        @Override
        public int getEnergyStored() {
            return 0;
        }

        @Override
        public int getMaxEnergyStored() {
            return 0;
        }

        @Override
        public boolean canExtract() {
            return handler.canExtract();
        }

        @Override
        public boolean canReceive() {
            return handler.canReceive();
        }
    }
}
