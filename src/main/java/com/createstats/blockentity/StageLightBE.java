package com.createstats.blockentity;

import com.createstats.block.StageLightBlock;
import com.createstats.flight.bridge.SableBridge;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.joml.Vector3f;

public class StageLightBE extends SmartBlockEntity implements com.createstats.power.IFeConsumer {
    protected final com.createstats.power.FeConsumer feConsumer = com.createstats.power.FeConsumer
            .forBlockEntity(this, com.createstats.power.FeProfiles.settings(com.createstats.power.FeProfiles.KEY_STAGE_LIGHT));

    public com.createstats.power.FeConsumer feConsumer() {
        return feConsumer;
    }

    public static final int MAX_TILT_DEG = 75;

    public static final int MIN_RANGE = 4;
    public static final int PEAK_RANGE = 32;
    public static final int MAX_RANGE = 64;

    public static final int MIN_BRIGHTNESS = 1;
    public static final int MAX_BRIGHTNESS = 15;

    public static final int MIN_FOCUS = 1;
    public static final int MAX_FOCUS = 10;
    public static final int DEFAULT_FOCUS = 4;

    public static final int MIN_WIDTH = 2;
    public static final int MAX_WIDTH = 16;
    public static final int DEFAULT_WIDTH = 9;

    public static final int NO_TINT = 0xFFFFFF;

    public static final int EFFECT_NONE = 0;
    public static final int EFFECT_STROBE = 1;
    public static final int EFFECT_PULSE = 2;
    public static final int EFFECT_RAINBOW = 3;
    public static final int EFFECT_COUNT = 4;
    public static final int MIN_EFFECT_SPEED = 1;
    public static final int MAX_EFFECT_SPEED = 10;
    public static final int DEFAULT_EFFECT_SPEED = 5;

    public static final int TRACKING_RANGE = 96;

    public static final int LIT_LIGHT_LEVEL = 6;

    private int pan;
    private int tilt;
    private int range = PEAK_RANGE;
    private int brightness = MAX_BRIGHTNESS;
    private int focus = DEFAULT_FOCUS;
    private int beamWidth = DEFAULT_WIDTH;
    private int lightSpread = DEFAULT_WIDTH;
    private int tint = NO_TINT;
    private boolean beamVisible = false;
    private boolean volumetric = false;
    private int effect = EFFECT_NONE;
    private int effectSpeed = DEFAULT_EFFECT_SPEED;

    private boolean redstoneActive;
    private int handPan;
    private int handTilt;

    private java.util.UUID battleTarget;
    private boolean autoAimActive;

    private BlockPos projectedLightPos;

    private long projectedRecalcAt;

    private static final int PROJECTED_RECALC_TICKS = 5;

    public float clientPan;
    public float clientTilt;
    public boolean clientAnimInit;

    public float clientPanVel;
    public float clientTiltVel;
    public long clientLastNanos;

    public float clientBeamLen;
    public boolean clientBeamBlocked;
    public long clientBeamClipNanos;

    public StageLightBE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(java.util.List<com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour> behaviours) {
    }

    public int getPan() {
        return this.pan;
    }

    public int getTilt() {
        return this.tilt;
    }

    public int getRange() {
        return this.range;
    }

    public int getBrightness() {
        return this.brightness;
    }

    public int getFocus() {
        return this.focus;
    }

    public int getBeamWidth() {
        return this.beamWidth;
    }

    public int getLightSpread() {
        return this.lightSpread;
    }

    public int getTrackingRange() {
        return TRACKING_RANGE;
    }

    public int getPeakRange() {
        return PEAK_RANGE;
    }

    public int getMaxRange() {
        return MAX_RANGE;
    }

    public int getTint() {
        return this.tint;
    }

    public boolean hasTint() {
        return this.tint != NO_TINT;
    }

    public boolean isBeamVisible() {
        return this.beamVisible;
    }

    public boolean isVolumetric() {
        return this.volumetric;
    }

    public int getEffect() {
        return this.effect;
    }

    public int getEffectSpeed() {
        return this.effectSpeed;
    }

    public void setTint(int rgb) {
        this.tint = rgb & 0xFFFFFF;
        this.markUpdated();
    }

    public float getTiltX() {
        return this.pan;
    }

    public float getTiltY() {
        return this.tilt;
    }

    public boolean isLit() {
        final BlockState state = this.getBlockState();
        return state.hasProperty(StageLightBlock.LIT) && state.getValue(StageLightBlock.LIT);
    }

    public void applyConfig(int newPan, int newTilt, int newRange, int newBrightness, int newFocus, int newTint,
                            boolean newBeamVisible, boolean newVolumetric, int newBeamWidth, int newLightSpread,
                            int newEffect, int newEffectSpeed) {
        this.pan = Mth.clamp(newPan, -MAX_TILT_DEG, MAX_TILT_DEG);
        this.tilt = Mth.clamp(newTilt, -MAX_TILT_DEG, MAX_TILT_DEG);
        this.range = Mth.clamp(newRange, MIN_RANGE, this.getMaxRange());
        this.brightness = Mth.clamp(newBrightness, MIN_BRIGHTNESS, MAX_BRIGHTNESS);
        this.focus = Mth.clamp(newFocus, MIN_FOCUS, MAX_FOCUS);
        this.tint = newTint & 0xFFFFFF;
        this.beamVisible = newBeamVisible;
        this.volumetric = newVolumetric;
        this.beamWidth = Mth.clamp(newBeamWidth, MIN_WIDTH, MAX_WIDTH);
        this.lightSpread = Mth.clamp(newLightSpread, MIN_WIDTH, MAX_WIDTH);
        this.effect = Mth.clamp(newEffect, 0, EFFECT_COUNT - 1);
        this.effectSpeed = Mth.clamp(newEffectSpeed, MIN_EFFECT_SPEED, MAX_EFFECT_SPEED);
        if (!this.redstoneActive) {
            this.handPan = this.pan;
            this.handTilt = this.tilt;
        }
        this.markUpdated();
    }

    private void markUpdated() {
        this.setChanged();
        if (this.level != null && !this.level.isClientSide()) {
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
        }
    }

    public void setLit(boolean lit) {
        if (this.level == null || this.level.isClientSide()) {
            return;
        }
        final BlockState state = this.getBlockState();
        if (state.hasProperty(StageLightBlock.LIT) && state.getValue(StageLightBlock.LIT) != lit) {
            this.level.setBlock(this.worldPosition, state.setValue(StageLightBlock.LIT, lit), 3);
        }
    }

    public void toggleLit() {
        this.setLit(!this.isLit());
    }

    public void adjustRange(int delta) {
        this.range = Mth.clamp(this.range + delta, MIN_RANGE, this.getMaxRange());
        this.markUpdated();
    }

    public void adjustBrightness(int delta) {
        this.brightness = Mth.clamp(this.brightness + delta, MIN_BRIGHTNESS, MAX_BRIGHTNESS);
        this.markUpdated();
    }

    public void adjustFocus(int delta) {
        this.focus = Mth.clamp(this.focus + delta, MIN_FOCUS, MAX_FOCUS);
        this.markUpdated();
    }

    public void adjustBeamWidth(int delta) {
        this.beamWidth = Mth.clamp(this.beamWidth + delta, MIN_WIDTH, MAX_WIDTH);
        this.markUpdated();
    }

    public void adjustLightSpread(int delta) {
        this.lightSpread = Mth.clamp(this.lightSpread + delta, MIN_WIDTH, MAX_WIDTH);
        this.markUpdated();
    }

    public void cycleColor(int delta) {
        final net.minecraft.world.item.DyeColor[] dyes = net.minecraft.world.item.DyeColor.values();
        final net.minecraft.world.item.DyeColor current = com.createstats.util.StageLightColors.fromRgb(this.tint);
        int index = current == null ? 0 : current.ordinal() + 1;
        final int count = dyes.length + 1;
        index = ((index + delta) % count + count) % count;
        this.tint = index == 0 ? NO_TINT : com.createstats.util.StageLightColors.rgb(dyes[index - 1]);
        this.markUpdated();
    }

    public void cycleEffect(int delta) {
        this.effect = ((this.effect + delta) % EFFECT_COUNT + EFFECT_COUNT) % EFFECT_COUNT;
        this.markUpdated();
    }

    public void adjustEffectSpeed(int delta) {
        this.effectSpeed = Mth.clamp(this.effectSpeed + delta, MIN_EFFECT_SPEED, MAX_EFFECT_SPEED);
        this.markUpdated();
    }

    public static float effectBrightnessFactor(int effect, int speed, long gameTime, float partialTick) {
        if (effect != EFFECT_STROBE && effect != EFFECT_PULSE) {
            return 1.0f;
        }

        final double t = (double) gameTime + partialTick;
        final double tempo = 1.0 + (Mth.clamp(speed, MIN_EFFECT_SPEED, MAX_EFFECT_SPEED) - 1) * 0.35;
        if (effect == EFFECT_STROBE) {
            final double period = 30.0 / tempo;
            return (t % period) < period * 0.65 ? 1.0f : 0.0f;
        }
        final double period = 80.0 / tempo;
        final double s = 0.5 + 0.5 * Math.sin((t / period) * Math.PI * 2.0);
        return (float) (0.35 + 0.65 * s);
    }

    public static int effectTint(int effect, int speed, int baseTint, long gameTime, float partialTick) {
        if (effect != EFFECT_RAINBOW) {
            return baseTint;
        }
        final double tempo = 1.0 + (Mth.clamp(speed, MIN_EFFECT_SPEED, MAX_EFFECT_SPEED) - 1) * 0.35;
        final double period = 200.0 / tempo;
        final float hue = (float) ((((double) gameTime + partialTick) % period) / period);
        return Mth.hsvToRgb(hue, 0.85f, 1.0f) & 0xFFFFFF;
    }

    public void toggleBeamVisible() {
        this.beamVisible = !this.beamVisible;
        this.markUpdated();
    }

    public void toggleVolumetric() {
        this.volumetric = !this.volumetric;
        this.markUpdated();
    }

    public void setBattleTarget(java.util.UUID id) {
        this.battleTarget = id;
        this.autoAimActive = id != null;
        this.markUpdated();
    }

    public void clearBattleTarget() {
        this.battleTarget = null;
        this.autoAimActive = false;
        this.markUpdated();
    }

    public boolean hasBattleTarget() {
        return this.battleTarget != null;
    }

    public String getBattleTargetName() {
        if (!this.autoAimActive || this.battleTarget == null) {
            return "";
        }
        final net.minecraft.world.entity.Entity entity = this.findTargetEntity(this.battleTarget);
        if (entity != null) {
            return entity.getName().getString();
        }
        final Object subLevel = SableBridge.findSubLevelById(this.level, this.battleTarget);
        if (subLevel != null) {
            return SableBridge.subLevelName(subLevel) + " (ship)";
        }
        return "";
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level == null || this.level.isClientSide()) {
            return;
        }

        final boolean fePowered = this.feConsumer.serviceTick(this.redstoneActive || this.autoAimActive);
        com.createstats.power.FePowerFx.handleTransition(this.level, this.worldPosition, this.feConsumer);
        if (!fePowered) {
            this.clearProjectedLight();
            return;
        }
        if (this.autoAimActive && this.battleTarget != null) {
            this.aimAtBattleTarget();
        }
        this.updateProjectedLight();
    }

    private void aimAtBattleTarget() {
        final net.minecraft.world.phys.Vec3 tgtWorld = this.resolveBattleTargetWorld();
        if (tgtWorld == null) {
            return;
        }
        final double hx = this.worldPosition.getX() + 0.5;
        final double hy = this.worldPosition.getY() + 0.8;
        final double hz = this.worldPosition.getZ() + 0.5;

        final Object subLevel = SableBridge.isAvailable() ? SableBridge.subLevelAccessOf(this) : null;
        final Vector3f localDir;
        if (subLevel != null) {
            final org.joml.Vector3d targetLocal = SableBridge.worldToLocal(subLevel,
                    new org.joml.Vector3d(tgtWorld.x, tgtWorld.y, tgtWorld.z));
            if (targetLocal == null) {
                return;
            }
            localDir = new Vector3f((float) (targetLocal.x - hx), (float) (targetLocal.y - hy), (float) (targetLocal.z - hz));
        } else {
            localDir = new Vector3f((float) (tgtWorld.x - hx), (float) (tgtWorld.y - hy), (float) (tgtWorld.z - hz));
        }
        final float dist = localDir.length();
        if (dist < 1.0e-3f || dist > this.getTrackingRange()) {
            return;
        }
        localDir.normalize();

        final Direction face = this.getBlockState().hasProperty(StageLightBlock.FACING)
                ? this.getBlockState().getValue(StageLightBlock.FACING)
                : Direction.UP;
        final Vector3f normal = new Vector3f(face.getStepX(), face.getStepY(), face.getStepZ());
        final Direction rightDir = tangentRight(face);
        final Direction upDir = tangentUp(face);
        final Vector3f right = new Vector3f(rightDir.getStepX(), rightDir.getStepY(), rightDir.getStepZ());
        final Vector3f up = new Vector3f(upDir.getStepX(), upDir.getStepY(), upDir.getStepZ());
        final float dn = localDir.dot(normal);
        if (dn <= 0.08f) {
            return;
        }
        final int newPan = Mth.clamp(Math.round((float) Math.toDegrees(Math.atan2(localDir.dot(right), dn))), -MAX_TILT_DEG, MAX_TILT_DEG);
        final int newTilt = Mth.clamp(Math.round((float) Math.toDegrees(Math.atan2(localDir.dot(up), dn))), -MAX_TILT_DEG, MAX_TILT_DEG);
        if (newPan != this.pan || newTilt != this.tilt) {
            this.pan = newPan;
            this.tilt = newTilt;
            this.markUpdated();
        }
    }

    private net.minecraft.world.phys.Vec3 resolveBattleTargetWorld() {
        final net.minecraft.world.entity.Entity entity = this.findTargetEntity(this.battleTarget);
        if (entity != null) {
            return entity instanceof net.minecraft.world.entity.LivingEntity
                    ? entity.getEyePosition()
                    : entity.getBoundingBox().getCenter();
        }

        final Object subLevel = SableBridge.findSubLevelById(this.level, this.battleTarget);
        if (subLevel != null) {
            final org.joml.Vector3d c = SableBridge.subLevelWorldCenter(subLevel);
            if (c != null && Double.isFinite(c.x) && Double.isFinite(c.y) && Double.isFinite(c.z)) {
                return new net.minecraft.world.phys.Vec3(c.x, c.y, c.z);
            }
        }
        return null;
    }

    private net.minecraft.world.entity.Entity findTargetEntity(java.util.UUID id) {
        if (id == null || this.level == null) {
            return null;
        }
        final net.minecraft.server.MinecraftServer server = this.level.getServer();
        if (server == null) {
            return null;
        }
        for (net.minecraft.server.level.ServerLevel serverLevel : server.getAllLevels()) {
            final net.minecraft.world.entity.Entity entity = serverLevel.getEntity(id);
            if (entity != null) {
                return entity;
            }
        }
        return null;
    }

    public void applyRedstoneGimbal(Level level, BlockPos pos, Direction face) {
        if (level.isClientSide()) {
            return;
        }
        final Direction rightDir = tangentRight(face);
        final Direction leftDir = rightDir.getOpposite();
        final Direction upDir = tangentUp(face);
        final Direction downDir = upDir.getOpposite();

        final int right = incomingPower(level, pos, rightDir);
        final int left = incomingPower(level, pos, leftDir);
        final int up = incomingPower(level, pos, upDir);
        final int down = incomingPower(level, pos, downDir);

        final boolean anyPowered = (right | left | up | down) > 0;
        if (anyPowered) {
            if (!this.redstoneActive) {
                this.handPan = this.pan;
                this.handTilt = this.tilt;
            }
            this.redstoneActive = true;
            this.pan = Mth.clamp(Math.round(((right - left) / 15.0f) * MAX_TILT_DEG), -MAX_TILT_DEG, MAX_TILT_DEG);
            this.tilt = Mth.clamp(Math.round(((up - down) / 15.0f) * MAX_TILT_DEG), -MAX_TILT_DEG, MAX_TILT_DEG);
            this.markUpdated();
        } else if (this.redstoneActive) {
            this.redstoneActive = false;
            this.pan = Mth.clamp(this.handPan, -MAX_TILT_DEG, MAX_TILT_DEG);
            this.tilt = Mth.clamp(this.handTilt, -MAX_TILT_DEG, MAX_TILT_DEG);
            this.markUpdated();
        }
    }

    private static int incomingPower(Level level, BlockPos pos, Direction side) {
        final BlockPos neighbor = pos.relative(side);
        return level.getSignal(neighbor, side);
    }

    public Vector3f computeBeamDirection() {
        final BlockState state = this.getBlockState();
        final Direction face = state.hasProperty(StageLightBlock.FACING)
                ? state.getValue(StageLightBlock.FACING)
                : Direction.UP;
        return computeBeamDirection(face, this.pan, this.tilt);
    }

    public static Vector3f computeBeamDirection(Direction face, float panDeg, float tiltDeg) {
        final Vector3f normal = new Vector3f(face.getStepX(), face.getStepY(), face.getStepZ());
        final Direction rightDir = tangentRight(face);
        final Direction upDir = tangentUp(face);
        final Vector3f right = new Vector3f(rightDir.getStepX(), rightDir.getStepY(), rightDir.getStepZ());
        final Vector3f up = new Vector3f(upDir.getStepX(), upDir.getStepY(), upDir.getStepZ());

        final float rx = (float) Math.toRadians(panDeg);
        final float ry = (float) Math.toRadians(tiltDeg);

        final Vector3f dir = new Vector3f(normal);
        dir.fma((float) Math.tan(rx), right);
        dir.fma((float) Math.tan(ry), up);
        if (dir.lengthSquared() < 1.0e-6f) {
            return normal.normalize();
        }
        return dir.normalize();
    }

    public static Direction tangentRight(Direction face) {
        return switch (face) {
            case UP, DOWN -> Direction.EAST;
            case NORTH -> Direction.WEST;
            case SOUTH -> Direction.EAST;
            case WEST -> Direction.SOUTH;
            case EAST -> Direction.NORTH;
        };
    }

    public static Direction tangentUp(Direction face) {
        return switch (face) {
            case UP -> Direction.SOUTH;
            case DOWN -> Direction.NORTH;
            default -> Direction.UP;
        };
    }

    public static Vector3f rotateFacingToUpFrame(Direction facing, Vector3f d) {
        final float x = d.x;
        final float y = d.y;
        final float z = d.z;
        return switch (facing) {
            case UP -> new Vector3f(x, y, z);
            case DOWN -> new Vector3f(x, -y, -z);
            case NORTH -> new Vector3f(x, -z, y);
            case SOUTH -> new Vector3f(x, z, -y);
            case EAST -> new Vector3f(-y, x, z);
            case WEST -> new Vector3f(y, -x, z);
        };
    }

    public int computePoolLuminance() {
        final int peak = this.getPeakRange();
        if (this.range <= peak) {
            return this.brightness;
        }
        final float t = (this.range - peak) / (float) (this.getMaxRange() - peak);
        return Math.round(Mth.lerp(t, this.brightness, MIN_BRIGHTNESS));
    }

    public double computePoolRadius(double baseRadius) {
        return baseRadius * Math.sqrt(this.range / (double) this.getPeakRange());
    }

    private void updateProjectedLight() {
        if (!(this.level instanceof net.minecraft.server.level.ServerLevel sl)) {
            return;
        }
        final long now = sl.getGameTime();
        if (now < this.projectedRecalcAt) {
            return;
        }
        this.projectedRecalcAt = now + PROJECTED_RECALC_TICKS;

        final boolean onSubLevel = SableBridge.isAvailable() && SableBridge.subLevelAccessOf(this) != null;
        if (!this.isLit() || onSubLevel) {
            this.clearProjectedLight();
            return;
        }
        final BlockPos target = this.computeProjectedTargetPos(sl);
        if (target == null) {
            this.clearProjectedLight();
            return;
        }
        if (!target.equals(this.projectedLightPos)) {
            this.clearProjectedLight();
            this.projectedLightPos = target;
        }
        this.ensureProjectedLight(sl);
    }

    private BlockPos computeProjectedTargetPos(net.minecraft.server.level.ServerLevel sl) {
        final Vector3f a = this.computeBeamDirection();
        net.minecraft.world.phys.Vec3 dir = new net.minecraft.world.phys.Vec3(a.x, a.y, a.z);
        if (dir.lengthSqr() < 1.0e-6) {
            return null;
        }
        dir = dir.normalize();
        final net.minecraft.world.phys.Vec3 start =
                net.minecraft.world.phys.Vec3.atCenterOf(this.worldPosition).add(dir.scale(0.6));
        final net.minecraft.world.phys.Vec3 end = start.add(dir.scale(this.range));
        final net.minecraft.world.phys.BlockHitResult hit = sl.clip(new net.minecraft.world.level.ClipContext(
                start, end, net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE,
                net.minecraft.world.phys.shapes.CollisionContext.empty()));
        if (hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
            return hit.getBlockPos().relative(hit.getDirection());
        }
        return BlockPos.containing(end);
    }

    private void ensureProjectedLight(net.minecraft.server.level.ServerLevel sl) {
        if (this.projectedLightPos == null || !sl.isLoaded(this.projectedLightPos)) {
            return;
        }

        int lum = Mth.clamp(this.computePoolLuminance(), 1, MAX_BRIGHTNESS);
        if (this.effect == EFFECT_PULSE) {
            lum = Math.max(1, Math.round(lum * effectBrightnessFactor(EFFECT_PULSE, this.effectSpeed, sl.getGameTime(), 0.0f)));
        }
        final net.minecraft.world.level.block.Block block = com.createstats.registry.ModBlocks.PROJECTED_LIGHT.get();
        final BlockState cur = sl.getBlockState(this.projectedLightPos);
        if (cur.is(block)) {
            if (cur.getValue(com.createstats.block.ProjectedLightBlock.LEVEL) != lum) {
                sl.setBlock(this.projectedLightPos,
                        cur.setValue(com.createstats.block.ProjectedLightBlock.LEVEL, lum), 3);
            }
        } else if (cur.isAir()) {
            sl.setBlock(this.projectedLightPos,
                    block.defaultBlockState().setValue(com.createstats.block.ProjectedLightBlock.LEVEL, lum), 3);
        }
    }

    private void clearProjectedLight() {
        if (this.projectedLightPos == null) {
            return;
        }
        if (this.level instanceof net.minecraft.server.level.ServerLevel sl
                && sl.isLoaded(this.projectedLightPos)
                && sl.getBlockState(this.projectedLightPos).is(com.createstats.registry.ModBlocks.PROJECTED_LIGHT.get())) {
            sl.setBlock(this.projectedLightPos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
        }
        this.projectedLightPos = null;
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putInt("Pan", this.pan);
        tag.putInt("Tilt", this.tilt);
        tag.putInt("Range", this.range);
        tag.putInt("Brightness", this.brightness);
        tag.putInt("Focus", this.focus);
        tag.putInt("BeamWidth", this.beamWidth);
        tag.putInt("LightSpread", this.lightSpread);
        tag.putInt("Tint", this.tint);
        tag.putBoolean("BeamVisible", this.beamVisible);
        tag.putBoolean("Volumetric", this.volumetric);
        tag.putInt("Effect", this.effect);
        tag.putInt("EffectSpeed", this.effectSpeed);
        tag.putBoolean("RedstoneActive", this.redstoneActive);
        tag.putInt("HandPan", this.handPan);
        tag.putInt("HandTilt", this.handTilt);
        tag.putBoolean("AutoAim", this.autoAimActive);
        if (this.battleTarget != null) {
            tag.putUUID("BattleTarget", this.battleTarget);
        }
        if (!clientPacket && this.projectedLightPos != null) {
            tag.putLong("ProjectedLight", this.projectedLightPos.asLong());
        }
        if (!clientPacket) {
            this.feConsumer.save(tag);
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        this.pan = Mth.clamp(tag.getInt("Pan"), -MAX_TILT_DEG, MAX_TILT_DEG);
        this.tilt = Mth.clamp(tag.getInt("Tilt"), -MAX_TILT_DEG, MAX_TILT_DEG);
        this.range = tag.contains("Range") ? Mth.clamp(tag.getInt("Range"), MIN_RANGE, this.getMaxRange()) : this.getPeakRange();
        this.brightness = tag.contains("Brightness") ? Mth.clamp(tag.getInt("Brightness"), MIN_BRIGHTNESS, MAX_BRIGHTNESS) : MAX_BRIGHTNESS;
        this.focus = tag.contains("Focus") ? Mth.clamp(tag.getInt("Focus"), MIN_FOCUS, MAX_FOCUS) : DEFAULT_FOCUS;
        this.beamWidth = tag.contains("BeamWidth") ? Mth.clamp(tag.getInt("BeamWidth"), MIN_WIDTH, MAX_WIDTH) : DEFAULT_WIDTH;
        this.lightSpread = tag.contains("LightSpread") ? Mth.clamp(tag.getInt("LightSpread"), MIN_WIDTH, MAX_WIDTH) : DEFAULT_WIDTH;
        this.tint = tag.contains("Tint") ? (tag.getInt("Tint") & 0xFFFFFF) : NO_TINT;
        this.beamVisible = tag.getBoolean("BeamVisible");
        this.volumetric = tag.getBoolean("Volumetric");
        this.effect = Mth.clamp(tag.getInt("Effect"), 0, EFFECT_COUNT - 1);
        this.effectSpeed = tag.contains("EffectSpeed") ? Mth.clamp(tag.getInt("EffectSpeed"), MIN_EFFECT_SPEED, MAX_EFFECT_SPEED) : DEFAULT_EFFECT_SPEED;
        this.redstoneActive = tag.getBoolean("RedstoneActive");
        this.handPan = Mth.clamp(tag.getInt("HandPan"), -MAX_TILT_DEG, MAX_TILT_DEG);
        this.handTilt = Mth.clamp(tag.getInt("HandTilt"), -MAX_TILT_DEG, MAX_TILT_DEG);
        this.autoAimActive = tag.getBoolean("AutoAim");
        this.battleTarget = tag.hasUUID("BattleTarget") ? tag.getUUID("BattleTarget") : null;
        if (!clientPacket) {
            this.projectedLightPos = tag.contains("ProjectedLight") ? BlockPos.of(tag.getLong("ProjectedLight")) : null;
        }
        this.feConsumer.load(tag);
    }

    @Override
    public void invalidate() {
        if (this.level != null && this.level.isClientSide()) {
            com.createstats.dynlight.ClientLightOwners.unloaded(this);
        }
        if (this.level != null && !this.level.isClientSide()) {
            this.clearProjectedLight();
        }
        super.invalidate();
    }

    @Override
    public void initialize() {
        super.initialize();
        if (this.level != null && this.level.isClientSide()) {
            com.createstats.dynlight.ClientLightOwners.loaded(this);
        }
    }
}
