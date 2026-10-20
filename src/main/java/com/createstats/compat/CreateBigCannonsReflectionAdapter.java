package com.createstats.compat;

import com.createstats.CreateStats;
import com.createstats.battle.core.BoreCheck;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class CreateBigCannonsReflectionAdapter {
    private CreateBigCannonsReflectionAdapter() {
    }

    public static LoadInspection inspect(@Nullable BlockEntity blockEntity, BlockState state) {
        if (!CreateBigCannonsCompat.isAvailable()) {
            return LoadInspection.none();
        }
        LoadInspection stateInspection = inspectVisibleState(state);
        if (blockEntity == null) {
            return stateInspection;
        }
        try {
            return stateInspection
                    .merge(inspectContainedBlock(blockEntity))
                    .merge(inspectBreechOpen(blockEntity));
        } catch (ReflectiveOperationException | RuntimeException error) {
            CreateStats.LOGGER.debug("[CBC compat] Skipping reflective weapon payload read for {}: {}", blockEntity.getBlockPos(), error.toString());
            return stateInspection;
        }
    }

    public static List<VirtualBlockInspection> inspectMountedCannon(@Nullable BlockEntity blockEntity, long mountKey) {
        if (!CreateBigCannonsCompat.isAvailable() || blockEntity == null) {
            return List.of();
        }
        try {
            Method mountedEntityMethod = noArgMethod(blockEntity.getClass(), "getContraption");
            if (mountedEntityMethod == null) {
                return List.of();
            }
            Object mountedEntity = mountedEntityMethod.invoke(blockEntity);
            if (mountedEntity == null) {
                return List.of();
            }
            Method contraptionMethod = noArgMethod(mountedEntity.getClass(), "getContraption");
            if (contraptionMethod == null) {
                return List.of();
            }
            Object contraption = contraptionMethod.invoke(mountedEntity);
            if (contraption == null) {
                return List.of();
            }
            Map<?, ?> blocks = extractMapField(contraption, "blocks");
            if (blocks == null || blocks.isEmpty()) {
                return List.of();
            }
            Map<?, ?> blockEntities = extractMapField(contraption, "presentBlockEntities");
            List<VirtualBlockInspection> virtualBlocks = new ArrayList<>();
            for (Map.Entry<?, ?> entry : blocks.entrySet()) {
                if (!(entry.getKey() instanceof BlockPos localPos)) {
                    continue;
                }
                BlockState mountedState = extractBlockState(entry.getValue());
                if (mountedState == null || mountedState.isAir() || !CreateBigCannonsCompat.isLikelyWeaponBlock(mountedState)) {
                    continue;
                }
                BlockEntity mountedBlockEntity = null;
                if (blockEntities != null && blockEntities.get(localPos) instanceof BlockEntity containedBlockEntity) {
                    mountedBlockEntity = containedBlockEntity;
                }
                virtualBlocks.add(new VirtualBlockInspection(virtualKey(mountKey, localPos.asLong()), mountedState, mountedBlockEntity));
            }
            return virtualBlocks;
        } catch (ReflectiveOperationException | RuntimeException error) {
            CreateStats.LOGGER.debug("[CBC compat] Skipping mounted cannon contraption read for {}: {}", blockEntity.getBlockPos(), error.toString());
            return List.of();
        }
    }

    // assembled barrels live on the contraption entity, not the world grid
    public static Map<BlockPos, BlockState> collectAssembledCannonWorldBlocks(@Nullable BlockEntity mountBlockEntity) {
        if (!CreateBigCannonsCompat.isAvailable() || mountBlockEntity == null) {
            return Map.of();
        }
        try {
            Method mountedEntityMethod = noArgMethod(mountBlockEntity.getClass(), "getContraption");
            if (mountedEntityMethod == null) {
                return Map.of();
            }
            Object mountedEntity = mountedEntityMethod.invoke(mountBlockEntity);
            if (mountedEntity == null) {
                return Map.of();
            }
            Method contraptionMethod = noArgMethod(mountedEntity.getClass(), "getContraption");
            if (contraptionMethod == null) {
                return Map.of();
            }
            Object contraption = contraptionMethod.invoke(mountedEntity);
            if (contraption == null) {
                return Map.of();
            }
            Map<?, ?> blocks = extractMapField(contraption, "blocks");
            if (blocks == null || blocks.isEmpty()) {
                return Map.of();
            }
            // anchor + local = build pose position, independent of aim

            Method blockPosMethod = noArgMethod(mountedEntity.getClass(), "blockPosition");
            BlockPos anchorPos = null;
            if (blockPosMethod != null && blockPosMethod.invoke(mountedEntity) instanceof BlockPos resolvedAnchor) {
                anchorPos = resolvedAnchor;
            }
            if (anchorPos == null) {
                return Map.of();
            }
            Map<BlockPos, BlockState> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : blocks.entrySet()) {
                if (!(entry.getKey() instanceof BlockPos localPos)) {
                    continue;
                }
                BlockState state = extractBlockState(entry.getValue());
                if (state == null || state.isAir() || !CreateBigCannonsCompat.isCreateBigCannonsBlock(state)) {
                    continue;
                }
                out.put(anchorPos.offset(localPos), state);
            }
            return out;
        } catch (ReflectiveOperationException | RuntimeException error) {
            CreateStats.LOGGER.debug("[CBC compat] Skipping assembled cannon world capture for {}: {}", mountBlockEntity.getBlockPos(), error.toString());
            return Map.of();
        }
    }

    public static float resolveBigCannonChargePower(@Nullable BlockEntity mountBlockEntity) {
        if (!CreateBigCannonsCompat.isAvailable() || mountBlockEntity == null) {
            return 0.0f;
        }
        try {
            Method mountedEntityMethod = noArgMethod(mountBlockEntity.getClass(), "getContraption");
            if (mountedEntityMethod == null) {
                return 0.0f;
            }
            Object mountedEntity = mountedEntityMethod.invoke(mountBlockEntity);
            if (mountedEntity == null) {
                return 0.0f;
            }
            Method contraptionMethod = noArgMethod(mountedEntity.getClass(), "getContraption");
            if (contraptionMethod == null) {
                return 0.0f;
            }
            Object contraption = contraptionMethod.invoke(mountedEntity);
            if (contraption == null) {
                return 0.0f;
            }
            Map<?, ?> blockEntities = extractMapField(contraption, "presentBlockEntities");
            if (blockEntities == null || blockEntities.isEmpty()) {
                return 0.0f;
            }
            float total = 0.0f;
            for (Object value : blockEntities.values()) {
                if (!(value instanceof BlockEntity cellBlockEntity)) {
                    continue;
                }
                Method behaviorMethod = noArgMethod(cellBlockEntity.getClass(), "cannonBehavior");
                if (behaviorMethod == null) {
                    continue;
                }
                Object behavior = behaviorMethod.invoke(cellBlockEntity);
                if (behavior == null) {
                    continue;
                }
                Method blockMethod = noArgMethod(behavior.getClass(), "block");
                if (blockMethod == null) {
                    continue;
                }
                Object blockInfo = blockMethod.invoke(behavior);
                BlockState containedState = extractBlockState(blockInfo);
                if (containedState == null || containedState.isAir()) {
                    continue;
                }
                if (CreateBigCannonsCompat.classifyPart(containedState) != CreateBigCannonsCompat.WeaponPart.PROPELLANT) {
                    continue;
                }
                Method chargeMethod = chargePowerMethod(containedState.getBlock().getClass());
                if (chargeMethod == null) {
                    continue;
                }
                Object power = chargeMethod.invoke(containedState.getBlock(), blockInfo);
                total += floatValue(power);
            }
            return Math.max(0.0f, total);
        } catch (ReflectiveOperationException | RuntimeException error) {
            CreateStats.LOGGER.debug("[CBC compat] Big cannon charge power read failed for {}: {}", mountBlockEntity.getBlockPos(), error.toString());
            return 0.0f;
        }
    }

    @Nullable
    private static Method chargePowerMethod(Class<?> blockClass) {
        try {
            for (Method candidate : blockClass.getMethods()) {
                if (!"getChargePower".equals(candidate.getName())) {
                    continue;
                }
                Class<?>[] parameters = candidate.getParameterTypes();
                if (parameters.length == 1 && "StructureBlockInfo".equals(parameters[0].getSimpleName())) {
                    candidate.setAccessible(true);
                    return candidate;
                }
            }
        } catch (RuntimeException ignored) {
        }
        return null;
    }

    public static boolean tryFireMountedCannon(@Nullable BlockEntity blockEntity, ServerLevel level) {
        if (!CreateBigCannonsCompat.isAvailable() || blockEntity == null || level == null) {
            return false;
        }
        try {
            Method mountedEntityMethod = noArgMethod(blockEntity.getClass(), "getContraption");
            if (mountedEntityMethod == null) {
                return false;
            }
            Object mountedEntity = mountedEntityMethod.invoke(blockEntity);
            if (mountedEntity == null) {
                return false;
            }
            Method contraptionMethod = noArgMethod(mountedEntity.getClass(), "getContraption");
            Object contraption = contraptionMethod == null ? null : contraptionMethod.invoke(mountedEntity);

            int loadedBefore = countLoadedMunitionCells(contraption);

            Method tryFiringShotMethod = noArgMethod(mountedEntity.getClass(), "tryFiringShot");
            if (tryFiringShotMethod != null) {
                tryFiringShotMethod.invoke(mountedEntity);
            } else {
                if (contraption == null) {
                    return false;
                }
                Method fireMethod = matchingFireShotMethod(contraption.getClass(), level, mountedEntity);
                if (fireMethod == null) {
                    return false;
                }
                fireMethod.invoke(contraption, level, mountedEntity);
            }

            int loadedAfter = countLoadedMunitionCells(contraption);
            if (loadedBefore < 0 || loadedAfter < 0) {
                return true;
            }
            return loadedAfter < loadedBefore;
        } catch (ReflectiveOperationException | RuntimeException error) {
            CreateStats.LOGGER.debug("[CBC compat] Mounted cannon fire command failed for {}: {}", blockEntity.getBlockPos(), error.toString());
            return false;
        }
    }

    private static int countLoadedMunitionCells(@Nullable Object contraption) {
        if (contraption == null) {
            return -1;
        }
        try {
            Map<?, ?> blockEntities = extractMapField(contraption, "presentBlockEntities");
            if (blockEntities == null) {
                return -1;
            }
            int count = 0;
            for (Object value : blockEntities.values()) {
                if (!(value instanceof BlockEntity cellBlockEntity)) {
                    continue;
                }
                Method behaviorMethod = noArgMethod(cellBlockEntity.getClass(), "cannonBehavior");
                if (behaviorMethod == null) {
                    continue;
                }
                Object behavior = behaviorMethod.invoke(cellBlockEntity);
                if (behavior == null) {
                    continue;
                }
                Method blockMethod = noArgMethod(behavior.getClass(), "block");
                if (blockMethod == null) {
                    continue;
                }
                BlockState contained = extractBlockState(blockMethod.invoke(behavior));
                if (contained == null || contained.isAir()) {
                    continue;
                }
                CreateBigCannonsCompat.WeaponPart part = CreateBigCannonsCompat.classifyPart(contained);
                if (part == CreateBigCannonsCompat.WeaponPart.PROJECTILE || part == CreateBigCannonsCompat.WeaponPart.PROPELLANT) {
                    count++;
                }
            }
            return count;
        } catch (ReflectiveOperationException | RuntimeException error) {
            return -1;
        }
    }

    public static boolean setMountedFireSignal(@Nullable BlockEntity blockEntity, boolean fire, int firePower) {
        if (!CreateBigCannonsCompat.isAvailable() || blockEntity == null) {
            return false;
        }
        try {
            Method redstoneMethod = matchingRedstoneUpdateMethod(blockEntity.getClass());
            if (redstoneMethod == null) {
                return false;
            }
            BlockState state = blockEntity.getBlockState();
            boolean assemblyPowered = CreateBigCannonsCompat.hasBooleanProperty(state, "assembly_powered", true);
            boolean previousFirePowered = CreateBigCannonsCompat.hasBooleanProperty(state, "fire_powered", true);
            redstoneMethod.invoke(blockEntity, assemblyPowered, assemblyPowered, fire, previousFirePowered, fire ? Math.max(1, firePower) : 0);
            return true;
        } catch (ReflectiveOperationException | RuntimeException error) {
            CreateStats.LOGGER.debug("[CBC compat] Mounted fire-side signal failed for {}: {}", blockEntity.getBlockPos(), error.toString());
            return false;
        }
    }

    public static MountedAimInspection inspectMountedAim(@Nullable BlockEntity blockEntity) {
        if (!CreateBigCannonsCompat.isAvailable() || blockEntity == null) {
            return MountedAimInspection.none();
        }
        try {
            Method contraptionMethod = noArgMethod(blockEntity.getClass(), "getContraption");
            Method interactionMethod = noArgMethod(blockEntity.getClass(), "getInteractionLocation");
            Method runningMethod = noArgMethod(blockEntity.getClass(), "isRunning");
            Field runningField = field(blockEntity.getClass(), "running");
            Field yawField = field(blockEntity.getClass(), "cannonYaw");
            Field pitchField = field(blockEntity.getClass(), "cannonPitch");
            if ((runningMethod == null && runningField == null) || (contraptionMethod == null && yawField == null && pitchField == null)) {
                return MountedAimInspection.none();
            }
            Object interaction = interactionMethod == null ? null : interactionMethod.invoke(blockEntity);
            Vec3 interactionLocation = interaction instanceof Vec3 vec ? vec : Vec3.atCenterOf(blockEntity.getBlockPos());
            Object contraption = contraptionMethod == null ? null : contraptionMethod.invoke(blockEntity);
            MountedAimGeometry geometry = inspectMountedAimGeometry(contraption, interactionLocation);
            int mountedEntityId = contraption instanceof Entity entity ? entity.getId() : Integer.MIN_VALUE;
            Object runningValue = runningMethod == null ? null : runningMethod.invoke(blockEntity);
            boolean running = runningValue instanceof Boolean bool ? bool : runningField != null && booleanFieldValue(blockEntity, "running");
            boolean fixedMount = blockEntity.getClass().getSimpleName().contains("FixedCannonMount");
            return new MountedAimInspection(
                    true,
                    running,
                    fixedMount,
                    mountedEntityId,
                    interactionLocation,
                    floatFieldValue(blockEntity, "cannonYaw"),
                    floatFieldValue(blockEntity, "cannonPitch"),
                    floatValue(invokeNoArg(blockEntity, "getYawSpeed")),
                    floatValue(invokeNoArg(blockEntity, "getPitchSpeed")),
                    geometry.origin(),
                    geometry.forwardPoint());
        } catch (ReflectiveOperationException | RuntimeException error) {
            CreateStats.LOGGER.debug("[CBC compat] Skipping reflective mount aim read for {}: {}", blockEntity.getBlockPos(), error.toString());
            return MountedAimInspection.none();
        }
    }

    private static MountedAimGeometry inspectMountedAimGeometry(@Nullable Object mountedEntity, Vec3 fallbackOrigin) {
        if (mountedEntity == null) {
            return MountedAimGeometry.fallback(fallbackOrigin);
        }
        try {
            Method contraptionMethod = noArgMethod(mountedEntity.getClass(), "getContraption");
            Object contraption = contraptionMethod == null ? null : contraptionMethod.invoke(mountedEntity);
            if (contraption == null) {
                return MountedAimGeometry.fallback(fallbackOrigin);
            }
            Object start = fieldValue(contraption, "startPos");
            Object orientation = fieldValue(contraption, "initialOrientation");
            if (!(start instanceof BlockPos startPos) || !(orientation instanceof Direction initialOrientation)) {
                return MountedAimGeometry.fallback(fallbackOrigin);
            }
            int frontExtensionLength = Math.max(0, maxInt(fieldValue(contraption, "frontExtensionLength"), 0));
            Method toGlobalVector = method(mountedEntity.getClass(), "toGlobalVector", Vec3.class, float.class);
            if (toGlobalVector == null) {
                return MountedAimGeometry.fallback(fallbackOrigin);
            }
            Vec3 localOrigin = Vec3.atCenterOf(BlockPos.ZERO);
            Vec3 localForward = Vec3.atCenterOf(startPos.relative(initialOrientation, Math.max(1, frontExtensionLength + 1)));
            Object originValue = toGlobalVector.invoke(mountedEntity, localOrigin, 0.0f);
            Object forwardValue = toGlobalVector.invoke(mountedEntity, localForward, 0.0f);
            if (originValue instanceof Vec3 origin && forwardValue instanceof Vec3 forward && origin.distanceToSqr(forward) > 1.0E-6) {
                return new MountedAimGeometry(origin, forward);
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
        return MountedAimGeometry.fallback(fallbackOrigin);
    }

    private static LoadInspection inspectVisibleState(BlockState state) {
        // only the loader's "moving" means loading. ram/worm heads are permanent, they stuck autoloaded cannons on LOADING forever

        if (CreateBigCannonsCompat.hasBooleanProperty(state, "moving", true)) {
            return LoadInspection.loading(1);
        }
        return LoadInspection.none();
    }

    private static LoadInspection inspectContainedBlock(BlockEntity blockEntity) throws ReflectiveOperationException {
        Method behaviorMethod = noArgMethod(blockEntity.getClass(), "cannonBehavior");
        if (behaviorMethod == null) {
            return LoadInspection.none();
        }
        Object behavior = behaviorMethod.invoke(blockEntity);
        if (behavior == null) {
            return LoadInspection.none();
        }
        return inspectBehaviorBlock(behavior).merge(inspectBehaviorItem(behavior));
    }

    public static AmmoContainerInspection inspectAmmoContainer(@Nullable BlockEntity blockEntity) {
        if (!CreateBigCannonsCompat.isAvailable() || blockEntity == null) {
            return AmmoContainerInspection.none();
        }
        try {
            ItemStack main = extractItemStack(blockEntity, "getMainAmmoStack");
            ItemStack tracer = extractItemStack(blockEntity, "getTracerStack");
            int mainCount = main == null ? 0 : main.getCount();
            int tracerCount = tracer == null ? 0 : tracer.getCount();
            int totalCount = maxInt(invokeNoArg(blockEntity, "getTotalCount"), mainCount + tracerCount);
            int mainCapacity = maxInt(invokeNoArg(blockEntity, "getMainAmmoCapacity"), 0);
            int tracerCapacity = maxInt(invokeNoArg(blockEntity, "getTracerAmmoCapacity"), 0);
            boolean creative = booleanValue(invokeNoArg(blockEntity, "isCreativeContainer"));
            return new AmmoContainerInspection(Math.max(0, totalCount), Math.max(0, mainCapacity + tracerCapacity), Math.max(0, tracerCount), creative);
        } catch (ReflectiveOperationException | RuntimeException error) {
            CreateStats.LOGGER.debug("[CBC compat] Skipping reflective autocannon ammo read for {}: {}", blockEntity.getBlockPos(), error.toString());
            return AmmoContainerInspection.none();
        }
    }

    public static AutocannonControlInspection inspectAutocannonControl(@Nullable BlockEntity blockEntity) {
        if (!CreateBigCannonsCompat.isAvailable() || blockEntity == null) {
            return AutocannonControlInspection.none();
        }
        try {
            int fireRateRpm = maxInt(invokeNoArg(blockEntity, "getActualFireRate"), 0);
            Object canFireValue = invokeNoArg(blockEntity, "canFire");
            boolean canFireKnown = canFireValue instanceof Boolean;
            boolean canFire = canFireKnown && (Boolean) canFireValue;
            return new AutocannonControlInspection(Math.max(0, fireRateRpm), canFireKnown, canFire);
        } catch (ReflectiveOperationException | RuntimeException error) {
            CreateStats.LOGGER.debug("[CBC compat] Skipping reflective autocannon control read for {}: {}", blockEntity.getBlockPos(), error.toString());
            return AutocannonControlInspection.none();
        }
    }

    public static AutocannonBallisticsInspection inspectAutocannonBallistics(BlockState state, int barrelCount) {
        if (!CreateBigCannonsCompat.isAvailable() || state == null || state.isAir()) {
            return AutocannonBallisticsInspection.none();
        }
        try {
            Method materialMethod = noArgMethod(state.getBlock().getClass(), "getAutocannonMaterial");
            if (materialMethod == null) {
                return AutocannonBallisticsInspection.none();
            }
            Object material = materialMethod.invoke(state.getBlock());
            if (material == null) {
                return AutocannonBallisticsInspection.none();
            }
            Object properties = invokeNoArg(material, "properties");
            if (properties == null) {
                return AutocannonBallisticsInspection.none();
            }
            float baseSpeed = floatValue(invokeNoArg(properties, "baseSpeed"));
            float speedIncreasePerBarrel = floatValue(invokeNoArg(properties, "speedIncreasePerBarrel"));
            int maxSpeedIncreases = maxInt(invokeNoArg(properties, "maxSpeedIncreases"), 0);
            int speedIncreases = Math.min(Math.max(0, barrelCount), maxSpeedIncreases);
            float projectileSpeed = baseSpeed + speedIncreasePerBarrel * speedIncreases;
            if (projectileSpeed <= 0.0f) {
                return AutocannonBallisticsInspection.none();
            }
            return new AutocannonBallisticsInspection(true, projectileSpeed, baseSpeed, speedIncreasePerBarrel, maxSpeedIncreases);
        } catch (ReflectiveOperationException | RuntimeException error) {
            CreateStats.LOGGER.debug("[CBC compat] Skipping reflective autocannon ballistics read for {}: {}", state.getBlock(), error.toString());
            return AutocannonBallisticsInspection.none();
        }
    }

    private static LoadInspection inspectBehaviorBlock(Object behavior) throws ReflectiveOperationException {
        Method blockMethod = noArgMethod(behavior.getClass(), "block");
        if (blockMethod == null) {
            return LoadInspection.none();
        }
        Object blockInfo = blockMethod.invoke(behavior);
        BlockState containedState = extractBlockState(blockInfo);
        if (containedState == null) {
            return LoadInspection.none();
        }
        if (containedState.isAir()) {
            return LoadInspection.emptyPayload();
        }

        CreateBigCannonsCompat.WeaponPart containedPart = CreateBigCannonsCompat.classifyPart(containedState);
        return switch (containedPart) {
            case PROJECTILE -> LoadInspection.loadedProjectile();
            case PROPELLANT -> LoadInspection.loadedPropellant();
            // a loader head in the bore is hardware, not a load

            default -> CreateBigCannonsCompat.isCreateBigCannonsBlock(containedState)
                    ? LoadInspection.inspectedUnknownPayload()
                    : LoadInspection.none();
        };
    }

    private static LoadInspection inspectBehaviorItem(Object behavior) throws ReflectiveOperationException {
        ItemStack containedStack = extractItemStack(behavior, "getItem");
        if (containedStack == null) {
            return LoadInspection.none();
        }
        if (containedStack.isEmpty()) {
            return LoadInspection.emptyPayload();
        }

        CreateBigCannonsCompat.WeaponPart containedPart = CreateBigCannonsCompat.classifyPayloadItem(containedStack);
        return switch (containedPart) {
            case PROJECTILE -> LoadInspection.loadedProjectile();
            case PROPELLANT -> LoadInspection.loadedPropellant();
            default -> CreateBigCannonsCompat.isCreateBigCannonsItem(containedStack)
                    ? LoadInspection.inspectedUnknownPayload()
                    : LoadInspection.none();
        };
    }

    private static LoadInspection inspectBreechOpen(BlockEntity blockEntity) throws ReflectiveOperationException {
        Method isOpenMethod = noArgMethod(blockEntity.getClass(), "isOpen");
        if (isOpenMethod == null) {
            return LoadInspection.none();
        }
        Object value = isOpenMethod.invoke(blockEntity);
        return value instanceof Boolean open ? LoadInspection.breechOpen(open) : LoadInspection.none();
    }

    @Nullable
    private static BlockState extractBlockState(@Nullable Object blockInfo) throws ReflectiveOperationException {
        if (blockInfo == null) {
            return null;
        }
        Method stateMethod = noArgMethod(blockInfo.getClass(), "state");
        if (stateMethod != null) {
            Object value = stateMethod.invoke(blockInfo);
            if (value instanceof BlockState state) {
                return state;
            }
        }
        Field stateField = field(blockInfo.getClass(), "state");
        if (stateField != null) {
            Object value = stateField.get(blockInfo);
            if (value instanceof BlockState state) {
                return state;
            }
        }
        return null;
    }

    @Nullable
    private static ItemStack extractItemStack(@Nullable Object owner, String methodName) throws ReflectiveOperationException {
        if (owner == null) {
            return null;
        }
        Method method = noArgMethod(owner.getClass(), methodName);
        if (method == null) {
            return null;
        }
        Object value = method.invoke(owner);
        return value instanceof ItemStack stack ? stack : null;
    }

    @Nullable
    private static Object invokeNoArg(@Nullable Object owner, String methodName) throws ReflectiveOperationException {
        if (owner == null) {
            return null;
        }
        Method method = noArgMethod(owner.getClass(), methodName);
        return method == null ? null : method.invoke(owner);
    }

    private static int maxInt(@Nullable Object value, int fallback) {
        return value instanceof Number number ? Math.max(fallback, number.intValue()) : fallback;
    }

    private static boolean booleanValue(@Nullable Object value) {
        return value instanceof Boolean bool && bool;
    }

    private static boolean booleanFieldValue(Object owner, String name) throws ReflectiveOperationException {
        Field field = field(owner.getClass(), name);
        if (field == null) {
            return false;
        }
        Object value = field.get(owner);
        return value instanceof Boolean bool && bool;
    }

    private static float floatFieldValue(Object owner, String name) throws ReflectiveOperationException {
        Field field = field(owner.getClass(), name);
        if (field == null) {
            return 0.0f;
        }
        Object value = field.get(owner);
        return value instanceof Number number ? number.floatValue() : 0.0f;
    }

    private static float floatValue(@Nullable Object value) {
        return value instanceof Number number ? number.floatValue() : 0.0f;
    }

    @Nullable
    private static Method noArgMethod(Class<?> type, String name) {
        try {
            Method method = type.getMethod(name);
            method.setAccessible(true);
            return method.getParameterCount() == 0 ? method : null;
        } catch (NoSuchMethodException ignored) {
        }
        Class<?> cursor = type;
        while (cursor != null) {
            try {
                Method method = cursor.getDeclaredMethod(name);
                method.setAccessible(true);
                return method.getParameterCount() == 0 ? method : null;
            } catch (NoSuchMethodException ignored) {
                cursor = cursor.getSuperclass();
            }
        }
        return null;
    }

    @Nullable
    private static Method method(Class<?> type, String name, Class<?>... parameterTypes) {
        try {
            Method method = type.getMethod(name, parameterTypes);
            method.setAccessible(true);
            return method;
        } catch (NoSuchMethodException ignored) {
        }
        Class<?> cursor = type;
        while (cursor != null) {
            try {
                Method method = cursor.getDeclaredMethod(name, parameterTypes);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException ignored) {
                cursor = cursor.getSuperclass();
            }
        }
        return null;
    }

    @Nullable
    private static Field field(Class<?> type, String name) {
        Class<?> cursor = type;
        while (cursor != null) {
            try {
                Field field = cursor.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                cursor = cursor.getSuperclass();
            }
        }
        return null;
    }

    @Nullable
    private static Map<?, ?> extractMapField(Object owner, String name) throws ReflectiveOperationException {
        Field mapField = field(owner.getClass(), name);
        if (mapField == null) {
            return null;
        }
        Object value = mapField.get(owner);
        return value instanceof Map<?, ?> map ? map : null;
    }

    @Nullable
    private static Object fieldValue(Object owner, String name) throws ReflectiveOperationException {
        Field targetField = field(owner.getClass(), name);
        return targetField == null ? null : targetField.get(owner);
    }

    private static long virtualKey(long mountKey, long localKey) {
        long mixed = mountKey ^ Long.rotateLeft(localKey * 0x9E3779B97F4A7C15L, 27);
        mixed ^= mixed >>> 33;
        mixed *= 0xFF51AFD7ED558CCDL;
        mixed ^= mixed >>> 33;
        return mixed ^ Long.MIN_VALUE;
    }

    @Nullable
    private static Method matchingFireShotMethod(Class<?> type, ServerLevel level, Object mountedEntity) {
        Class<?> cursor = type;
        while (cursor != null) {
            for (Method method : cursor.getDeclaredMethods()) {
                if (!"fireShot".equals(method.getName()) || method.getParameterCount() != 2) {
                    continue;
                }
                Class<?>[] parameterTypes = method.getParameterTypes();
                if (parameterTypes[0].isInstance(level) && parameterTypes[1].isInstance(mountedEntity)) {
                    method.setAccessible(true);
                    return method;
                }
            }
            cursor = cursor.getSuperclass();
        }
        return null;
    }

    @Nullable
    private static Method matchingRedstoneUpdateMethod(Class<?> type) {
        Class<?> cursor = type;
        while (cursor != null) {
            for (Method method : cursor.getDeclaredMethods()) {
                if (!"onRedstoneUpdate".equals(method.getName()) || method.getParameterCount() != 5) {
                    continue;
                }
                Class<?>[] parameterTypes = method.getParameterTypes();
                if ((parameterTypes[0] == boolean.class || parameterTypes[0] == Boolean.class)
                        && (parameterTypes[1] == boolean.class || parameterTypes[1] == Boolean.class)
                        && (parameterTypes[2] == boolean.class || parameterTypes[2] == Boolean.class)
                        && (parameterTypes[3] == boolean.class || parameterTypes[3] == Boolean.class)
                        && (parameterTypes[4] == int.class || parameterTypes[4] == Integer.class)) {
                    method.setAccessible(true);
                    return method;
                }
            }
            cursor = cursor.getSuperclass();
        }
        return null;
    }

    private static final String BIG_CONTRAPTION = "rbasamoyai.createbigcannons.cannon_control.contraption.MountedBigCannonContraption";
    private static final String PROPELLANT_CONTEXT = BIG_CONTRAPTION + "$PropellantContext";
    private static final String BIG_CANNON_BE = "rbasamoyai.createbigcannons.cannons.big_cannons.IBigCannonBlockEntity";
    private static final String QF_BREECH = "rbasamoyai.createbigcannons.cannons.big_cannons.breeches.quickfiring_breech.QuickfiringBreechBlockEntity";
    private static final String CHARGE_BLOCK = "rbasamoyai.createbigcannons.munitions.big_cannon.propellant.BigCannonPropellantBlock";
    private static final String SHELL_BLOCK = "rbasamoyai.createbigcannons.munitions.big_cannon.ProjectileBlock";
    private static final String INTEGRATED = "rbasamoyai.createbigcannons.munitions.big_cannon.propellant.IntegratedPropellantProjectile";
    private static final String CONFIGS = "rbasamoyai.createbigcannons.config.CBCConfigs";

    private static final int MAX_BORE = 256;

    private static final Class<?>[] ARGS_INFO = {StructureBlockInfo.class};
    private static final Class<?>[] ARGS_INFO_DIR = {StructureBlockInfo.class, Direction.class};
    private static final Class<?>[] ARGS_CHARGE_VALID = {StructureBlockInfo.class, int.class, Direction.class};
    private static final Class<?>[] ARGS_SHELL_VALID = {List.class, StructureBlockInfo.class, int.class, Direction.class};
    private static final Class<?>[] ARGS_LIST_DIR = {List.class, Direction.class};
    private static final Class<?>[] ARGS_LEVEL_LIST = {Level.class, List.class};

    private static final ClassValue<Map<String, Optional<Method>>> METHODS = new ClassValue<>() {
        @Override
        protected Map<String, Optional<Method>> computeValue(Class<?> type) {
            return new ConcurrentHashMap<>();
        }
    };
    private static final ClassValue<Map<String, Optional<Field>>> FIELDS = new ClassValue<>() {
        @Override
        protected Map<String, Optional<Field>> computeValue(Class<?> type) {
            return new ConcurrentHashMap<>();
        }
    };
    private static final ClassValue<Map<String, Boolean>> KINDS = new ClassValue<>() {
        @Override
        protected Map<String, Boolean> computeValue(Class<?> type) {
            return new ConcurrentHashMap<>();
        }
    };

    private record ShellProps(float added, float minimum, boolean canSquib, boolean integrated, float power, float stress,
                              long readAt) {
    }

    private static final ShellProps STOCK_SHELL = new ShellProps(0f, 1f, true, false, 0f, 0f, 0L);

    private static final Map<Block, ShellProps> SHELLS = new ConcurrentHashMap<>();
    private static final long SHELL_KEEP_NANOS = 60_000_000_000L;

    @Nullable
    private static volatile Optional<Method> safeLoadRef;

    @Nullable
    private static volatile Optional<Method> serverConfigRef;

    @Nullable
    public static BoreCheck.Bore readBore(@Nullable BlockEntity mount) {
        if (!CreateBigCannonsCompat.isAvailable() || mount == null) {
            return null;
        }
        try {
            Object entity = call(mount, "getContraption");
            Object contraption = call(entity, "getContraption");
            if (!isA(contraption, BIG_CONTRAPTION)
                    || !(fieldValueCached(contraption, "startPos") instanceof BlockPos startPos)
                    || !(fieldValueCached(contraption, "initialOrientation") instanceof Direction dir)
                    || !(fieldValueCached(contraption, "blocks") instanceof Map<?, ?> blocks)
                    || !(fieldValueCached(contraption, "presentBlockEntities") instanceof Map<?, ?> present)
                    || !(call(contraption, "getMaxSafeCharges") instanceof Number maxSafe)) {
                return null;
            }
            boolean dropMortar = call(contraption, "isDropMortar") instanceof Boolean mortar && mortar;

            Object breech = present.get(startPos.relative(dir.getOpposite()));
            boolean breechOpen = isA(breech, QF_BREECH) && call(breech, "getOpenProgress") instanceof Number open && open.intValue() > 0;
            Object properties = call(fieldValueCached(contraption, "cannonMaterial"), "properties");
            double minVelocity = call(properties, "minimumVelocityPerBarrel") instanceof Number v ? v.doubleValue() : 1.0;
            float minimumSpread = call(properties, "minimumSpread") instanceof Number s ? s.floatValue() : 0f;
            ClassLoader loader = contraption.getClass().getClassLoader();
            Method safeLoad = safeLoadMethod(loader);

            List<BoreCheck.Cell> cells = new ArrayList<>();
            List<StructureBlockInfo> charges = new ArrayList<>();
            List<StructureBlockInfo> shell = new ArrayList<>();
            ShellProps whole = null;
            BlockPos pos = startPos;
            for (int i = 0; i < MAX_BORE; i++) {
                Object cellEntity = present.get(pos);
                if (!isA(cellEntity, BIG_CANNON_BE) || !(blocks.get(pos) instanceof StructureBlockInfo cannonInfo)) {
                    break;
                }
                if (!(call(call(cellEntity, "cannonBehavior"), "block") instanceof StructureBlockInfo held)) {
                    return null;
                }
                boolean thick = call(cellEntity, "blockCanHandle", ARGS_INFO, cannonInfo) instanceof Boolean t && t;
                Block block = held.state().getBlock();
                if (held.state().isAir()) {
                    cells.add(BoreCheck.Cell.empty(thick));
                } else if (isA(block, CHARGE_BLOCK) && !isA(block, SHELL_BLOCK)) {
                    charges.add(held);
                    float power = floatValue(call(block, "getChargePower", ARGS_INFO, held));
                    float stress = floatValue(call(block, "getStressOnCannon", ARGS_INFO, held));
                    boolean lights = call(block, "canBeIgnited", ARGS_INFO_DIR, held, dir) instanceof Boolean l && l;
                    cells.add(BoreCheck.Cell.charge(thick, power, stress, lights, safeLoad(safeLoad, charges, dir)));
                } else if (isA(block, SHELL_BLOCK) && whole == null) {
                    shell.add(held);
                    List<StructureBlockInfo> sofar = List.copyOf(shell);
                    boolean valid = true;
                    for (int k = 0; k < sofar.size() && valid; k++) {
                        Block part = sofar.get(k).state().getBlock();
                        valid = isA(part, SHELL_BLOCK)
                                && call(part, "isValidAddition", ARGS_SHELL_VALID, sofar, sofar.get(k), k, dir) instanceof Boolean ok && ok;
                    }
                    boolean complete = valid && call(block, "isComplete", ARGS_LIST_DIR, sofar, dir) instanceof Boolean c && c;
                    if (complete) {
                        whole = shellProps(block, sofar, mount.getLevel());
                        cells.add(BoreCheck.Cell.shell(thick, true, true, whole.added(), whole.minimum(), whole.canSquib()));
                    } else {
                        cells.add(BoreCheck.Cell.shell(thick, valid, false, 0f, 0f, false));
                    }
                } else if (isA(block, SHELL_BLOCK)) {
                    cells.add(BoreCheck.Cell.shell(thick, false, false, 0f, 0f, false));
                } else {
                    cells.add(BoreCheck.Cell.other(thick));
                }
                pos = pos.relative(dir);
            }
            boolean muzzleBlocked = blocks.get(pos) instanceof StructureBlockInfo past && !past.state().isAir();
            BoreCheck.Integrated integrated = null;
            if (whole != null && whole.integrated() && !shell.isEmpty()) {
                List<StructureBlockInfo> withShell = new ArrayList<>(charges);
                withShell.add(shell.get(0));
                integrated = new BoreCheck.Integrated(safeLoad(safeLoad, withShell, dir), whole.power(), whole.stress());
            }
            return new BoreCheck.Bore(cells, breechOpen, dropMortar, maxSafe.intValue(), minVelocity, minimumSpread,
                    chances(loader), muzzleBlocked, integrated);
        } catch (ReflectiveOperationException | RuntimeException error) {
            CreateStats.LOGGER.debug("[CBC compat] Bore read failed for {}: {}", mount.getBlockPos(), error.toString());
            return null;
        }
    }

    private static boolean safeLoad(@Nullable Method safeLoad, List<StructureBlockInfo> charges, Direction dir)
            throws ReflectiveOperationException {
        List<StructureBlockInfo> sofar = List.copyOf(charges);
        if (safeLoad != null) {
            return safeLoad.invoke(null, sofar, dir) instanceof Boolean ok && ok;
        }
        for (int i = 0; i < sofar.size(); i++) {
            Block block = sofar.get(i).state().getBlock();
            if (!(call(block, "isValidAddition", ARGS_CHARGE_VALID, sofar.get(i), i, dir) instanceof Boolean ok && ok)) {
                return false;
            }
        }
        return true;
    }

    @Nullable
    private static Method safeLoadMethod(ClassLoader loader) {
        Optional<Method> known = safeLoadRef;
        if (known == null) {
            Method found = null;
            try {
                found = Class.forName(PROPELLANT_CONTEXT, false, loader).getMethod("safeLoad", List.class, Direction.class);
                found.setAccessible(true);
            } catch (ReflectiveOperationException | RuntimeException error) {
                CreateStats.LOGGER.debug("[CBC compat] PropellantContext.safeLoad not found, using each charge's rule: {}", error.toString());
                found = null;
            }
            known = Optional.ofNullable(found);
            safeLoadRef = known;
        }
        return known.orElse(null);
    }

    private static ShellProps shellProps(Block block, List<StructureBlockInfo> shell, @Nullable Level level) {
        long now = System.nanoTime();
        ShellProps kept = SHELLS.get(block);
        if (kept != null && now - kept.readAt() < SHELL_KEEP_NANOS) {
            return kept;
        }
        ShellProps read = new ShellProps(STOCK_SHELL.added(), STOCK_SHELL.minimum(), STOCK_SHELL.canSquib(), false, 0f, 0f, now);
        if (level != null) {
            try {
                Object projectile = call(block, "getProjectile", ARGS_LEVEL_LIST, level, shell);
                if (projectile != null) {
                    boolean integrated = isA(projectile, INTEGRATED);
                    read = new ShellProps(
                            floatValue(call(projectile, "addedChargePower")),
                            call(projectile, "minimumChargePower") instanceof Number min ? min.floatValue() : STOCK_SHELL.minimum(),
                            !(call(projectile, "canSquib") instanceof Boolean squib) || squib,
                            integrated,
                            integrated ? floatValue(call(projectile, "getChargePower")) : 0f,
                            integrated ? floatValue(call(projectile, "getStressOnCannon")) : 0f,
                            now);
                }
            } catch (ReflectiveOperationException | RuntimeException error) {
                CreateStats.LOGGER.debug("[CBC compat] Shell properties read failed for {}: {}", block, error.toString());
            }
        }
        SHELLS.put(block, read);
        return read;
    }

    private static BoreCheck.Chances chances(ClassLoader loader) {
        try {
            Method server = serverConfigMethod(loader);
            Object failure = fieldValueCached(server == null ? null : server.invoke(null), "failure");
            if (failure == null) {
                return BoreCheck.Chances.STOCK;
            }
            boolean off = call(fieldValueCached(failure, "disableAllFailure"), "get") instanceof Boolean b && b;
            BoreCheck.Chances stock = BoreCheck.Chances.STOCK;
            return new BoreCheck.Chances(!off,
                    configFloat(failure, "squibChance", stock.squib()),
                    configFloat(failure, "barrelChargeBurstChance", stock.barrelBurst()),
                    configFloat(failure, "overloadBurstChance", stock.overload()),
                    configFloat(failure, "interruptedIgnitionChance", stock.interrupted()));
        } catch (ReflectiveOperationException | RuntimeException error) {
            return BoreCheck.Chances.STOCK;
        }
    }

    @Nullable
    private static Method serverConfigMethod(ClassLoader loader) {
        Optional<Method> known = serverConfigRef;
        if (known == null) {
            Method found;
            try {
                found = Class.forName(CONFIGS, false, loader).getMethod("server");
            } catch (ReflectiveOperationException | RuntimeException error) {
                found = null;
            }
            known = Optional.ofNullable(found);
            serverConfigRef = known;
        }
        return known.orElse(null);
    }

    private static float configFloat(Object failure, String name, float fallback) throws ReflectiveOperationException {
        return call(fieldValueCached(failure, name), "getF") instanceof Number n ? n.floatValue() : fallback;
    }

    @Nullable
    private static Object call(@Nullable Object owner, String name) throws ReflectiveOperationException {
        if (owner == null) {
            return null;
        }
        Method method = cachedMethod(owner.getClass(), name);
        return method == null ? null : method.invoke(owner);
    }

    @Nullable
    private static Object call(@Nullable Object owner, String name, Class<?>[] types, Object... args) throws ReflectiveOperationException {
        if (owner == null) {
            return null;
        }
        Method method = cachedMethod(owner.getClass(), name, types);
        return method == null ? null : method.invoke(owner, args);
    }

    @Nullable
    private static Method cachedMethod(Class<?> type, String name, Class<?>... types) {
        StringBuilder key = new StringBuilder(name).append('(');
        for (Class<?> t : types) {
            key.append(t.getName()).append(',');
        }
        return METHODS.get(type).computeIfAbsent(key.toString(),
                k -> Optional.ofNullable(types.length == 0 ? noArgMethod(type, name) : method(type, name, types))).orElse(null);
    }

    @Nullable
    private static Object fieldValueCached(@Nullable Object owner, String name) throws IllegalAccessException {
        if (owner == null) {
            return null;
        }
        Class<?> type = owner.getClass();
        Field found = FIELDS.get(type).computeIfAbsent(name, n -> Optional.ofNullable(field(type, n))).orElse(null);
        return found == null ? null : found.get(owner);
    }

    private static boolean isA(@Nullable Object value, String typeName) {
        if (value == null) {
            return false;
        }
        Class<?> type = value.getClass();
        return KINDS.get(type).computeIfAbsent(typeName, n -> isA(type, n));
    }

    private static boolean isA(@Nullable Class<?> type, String typeName) {
        if (type == null) {
            return false;
        }
        if (type.getName().equals(typeName)) {
            return true;
        }
        for (Class<?> face : type.getInterfaces()) {
            if (isA(face, typeName)) {
                return true;
            }
        }
        return isA(type.getSuperclass(), typeName);
    }

    public record VirtualBlockInspection(
            long key,
            BlockState state,
            @Nullable BlockEntity blockEntity) {
    }

    public record AmmoContainerInspection(
            int totalRounds,
            int totalCapacity,
            int tracerRounds,
            boolean creative) {
        public static AmmoContainerInspection none() {
            return new AmmoContainerInspection(0, 0, 0, false);
        }
    }

    public record AutocannonControlInspection(
            int fireRateRpm,
            boolean canFireKnown,
            boolean canFire) {
        public static AutocannonControlInspection none() {
            return new AutocannonControlInspection(0, false, false);
        }
    }

    public record AutocannonBallisticsInspection(
            boolean available,
            float projectileSpeed,
            float baseSpeed,
            float speedIncreasePerBarrel,
            int maxSpeedIncreases) {
        public static AutocannonBallisticsInspection none() {
            return new AutocannonBallisticsInspection(false, 0.0f, 0.0f, 0.0f, 0);
        }
    }

    public record MountedAimInspection(
            boolean available,
            boolean running,
            boolean fixedMount,
            int mountedEntityId,
            Vec3 interactionLocation,
            float yawDegrees,
            float pitchDegrees,
            float yawSpeed,
            float pitchSpeed,
            Vec3 aimOriginLocation,
            Vec3 aimForwardLocation) {
        public static MountedAimInspection none() {
            return new MountedAimInspection(false, false, false, Integer.MIN_VALUE, Vec3.ZERO, 0.0f, 0.0f, 0.0f, 0.0f, Vec3.ZERO, Vec3.ZERO);
        }
    }

    private record MountedAimGeometry(
            Vec3 origin,
            Vec3 forwardPoint) {
        private static MountedAimGeometry fallback(Vec3 origin) {
            return new MountedAimGeometry(origin, origin.add(0.0, 0.0, 1.0));
        }
    }

    public record LoadInspection(
            String loadedState,
            int projectileBlocks,
            int propellantBlocks,
            int loadingBlocks,
            int inspectedPayloads,
            int emptyPayloads,
            boolean breechOpenKnown,
            boolean breechOpen) {
        public static LoadInspection none() {
            return new LoadInspection("NOT_APPLICABLE", 0, 0, 0, 0, 0, false, false);
        }

        public static LoadInspection emptyPayload() {
            return new LoadInspection("EMPTY", 0, 0, 0, 1, 1, false, false);
        }

        public static LoadInspection loadedProjectile() {
            return new LoadInspection("LOADED", 1, 0, 0, 1, 0, false, false);
        }

        public static LoadInspection loadedPropellant() {
            return new LoadInspection("LOADED", 0, 1, 0, 1, 0, false, false);
        }

        public static LoadInspection loading(int loadingBlocks) {
            return new LoadInspection("LOADING", 0, 0, Math.max(1, loadingBlocks), 1, 0, false, false);
        }

        public static LoadInspection inspectedUnknownPayload() {
            return new LoadInspection("UNKNOWN", 0, 0, 0, 1, 0, false, false);
        }

        public static LoadInspection breechOpen(boolean open) {
            return new LoadInspection("NOT_APPLICABLE", 0, 0, 0, 0, 0, true, open);
        }

        public LoadInspection merge(LoadInspection other) {
            if (other == null) {
                return this;
            }
            boolean nextBreechKnown = this.breechOpenKnown || other.breechOpenKnown;
            return new LoadInspection(
                    higherPriorityState(this.loadedState, other.loadedState),
                    this.projectileBlocks + other.projectileBlocks,
                    this.propellantBlocks + other.propellantBlocks,
                    this.loadingBlocks + other.loadingBlocks,
                    this.inspectedPayloads + other.inspectedPayloads,
                    this.emptyPayloads + other.emptyPayloads,
                    nextBreechKnown,
                    other.breechOpenKnown ? other.breechOpen : this.breechOpen);
        }

        private static String higherPriorityState(String first, String second) {
            return priority(second) > priority(first) ? second : first;
        }

        private static int priority(String state) {
            return switch (state) {
                case "LOADING" -> 5;
                case "LOADED" -> 4;
                case "EMPTY" -> 3;
                case "UNKNOWN" -> 2;
                default -> 1;
            };
        }
    }
}
