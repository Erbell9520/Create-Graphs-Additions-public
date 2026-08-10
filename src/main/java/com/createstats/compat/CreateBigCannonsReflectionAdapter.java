package com.createstats.compat;

import com.createstats.CreateStats;
import com.createstats.compat.runtime.BattleScreenMountControlSuppressions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    /** [1] */
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
            // [2]
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

    /** [3] */
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

            // [4]
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
                // Could not introspect the bore; fall back to assuming the fire call took effect.
                return true;
            }
            return loadedAfter < loadedBefore;
        } catch (ReflectiveOperationException | RuntimeException error) {
            CreateStats.LOGGER.debug("[CBC compat] Mounted cannon fire command failed for {}: {}", blockEntity.getBlockPos(), error.toString());
            return false;
        }
    }

    /** [5] */
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

    public static boolean setMountedAimAngles(@Nullable BlockEntity blockEntity, float yawDegrees, float pitchDegrees) {
        if (!CreateBigCannonsCompat.isAvailable() || blockEntity == null) {
            return false;
        }
        try {
            Method yawMethod = singleNumberMethod(blockEntity.getClass(), "setYaw");
            Method pitchMethod = singleNumberMethod(blockEntity.getClass(), "setPitch");
            if (yawMethod == null && pitchMethod == null) {
                return false;
            }
            if (yawMethod != null) {
                invokeSingleNumber(yawMethod, blockEntity, yawDegrees);
            }
            if (pitchMethod != null) {
                invokeSingleNumber(pitchMethod, blockEntity, pitchDegrees);
            }
            return true;
        } catch (ReflectiveOperationException | RuntimeException error) {
            CreateStats.LOGGER.debug("[CBC compat] Mounted aim setter failed for {}: {}", blockEntity.getBlockPos(), error.toString());
            return false;
        }
    }

    public static boolean commandMountedAimStep(@Nullable BlockEntity blockEntity, float yawStepDegrees, float pitchStepDegrees) {
        if (!CreateBigCannonsCompat.isAvailable() || blockEntity == null) {
            return false;
        }
        try {
            boolean usedInterface = false;
            usedInterface |= setMountedInterfaceAngleLimit(fieldValue(blockEntity, "yawInterface"), yawStepDegrees);
            usedInterface |= setMountedInterfaceAngleLimit(fieldValue(blockEntity, "pitchInterface"), pitchStepDegrees);
            if (usedInterface) {
                return true;
            }
            float currentYaw = floatFieldValue(blockEntity, "cannonYaw");
            float currentPitch = floatFieldValue(blockEntity, "cannonPitch");
            return setMountedAimAngles(blockEntity, currentYaw + yawStepDegrees, currentPitch + pitchStepDegrees);
        } catch (ReflectiveOperationException | RuntimeException error) {
            CreateStats.LOGGER.debug("[CBC compat] Mounted aim step command failed for {}: {}", blockEntity.getBlockPos(), error.toString());
            return false;
        }
    }

    public static void suppressMountedInterfaceControl(@Nullable BlockEntity blockEntity, long tick) {
        if (!CreateBigCannonsCompat.isAvailable() || blockEntity == null) {
            return;
        }
        try {
            BattleScreenMountControlSuppressions.suppressForTick(blockEntity.getBlockPos(), tick);
            Object yawInterface = fieldValue(blockEntity, "yawInterface");
            if (yawInterface instanceof BlockEntity yawBlockEntity) {
                BattleScreenMountControlSuppressions.suppressForTick(yawBlockEntity.getBlockPos(), tick);
            }
            Object pitchInterface = fieldValue(blockEntity, "pitchInterface");
            if (pitchInterface instanceof BlockEntity pitchBlockEntity) {
                BattleScreenMountControlSuppressions.suppressForTick(pitchBlockEntity.getBlockPos(), tick);
            }
        } catch (ReflectiveOperationException | RuntimeException error) {
            CreateStats.LOGGER.debug("[CBC compat] Mounted interface suppression failed for {}: {}", blockEntity.getBlockPos(), error.toString());
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
        // [6]
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
            // [7]
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

    private static void invokeSingleNumber(Method method, Object owner, float value) throws ReflectiveOperationException {
        Class<?> parameterType = method.getParameterTypes()[0];
        if (parameterType == double.class || parameterType == Double.class) {
            method.invoke(owner, (double) value);
            return;
        }
        method.invoke(owner, value);
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
    private static Method singleNumberMethod(Class<?> type, String name) {
        try {
            Method method = type.getMethod(name, float.class);
            method.setAccessible(true);
            return method;
        } catch (NoSuchMethodException ignored) {
        }
        Class<?> cursor = type;
        while (cursor != null) {
            for (Method method : cursor.getDeclaredMethods()) {
                if (!name.equals(method.getName()) || method.getParameterCount() != 1) {
                    continue;
                }
                Class<?> parameterType = method.getParameterTypes()[0];
                if (parameterType == float.class || parameterType == Float.class || parameterType == double.class || parameterType == Double.class) {
                    method.setAccessible(true);
                    return method;
                }
            }
            cursor = cursor.getSuperclass();
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

    private static boolean setMountedInterfaceAngleLimit(@Nullable Object interfaceBlockEntity, float angleLimit) throws ReflectiveOperationException {
        if (interfaceBlockEntity == null || Math.abs(angleLimit) <= 1.0E-3f) {
            return false;
        }
        Method limitMethod = singleNumberMethod(interfaceBlockEntity.getClass(), "setSequencedAngleLimit");
        if (limitMethod == null) {
            return false;
        }
        invokeSingleNumber(limitMethod, interfaceBlockEntity, angleLimit);
        Method tryUpdateSpeed = noArgMethod(interfaceBlockEntity.getClass(), "tryUpdateSpeed");
        if (tryUpdateSpeed != null) {
            tryUpdateSpeed.invoke(interfaceBlockEntity);
        }
        Method sendData = noArgMethod(interfaceBlockEntity.getClass(), "sendData");
        if (sendData != null) {
            sendData.invoke(interfaceBlockEntity);
        }
        return true;
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
