package com.createstats.book;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.TickRateManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkSource;
import net.minecraft.world.level.entity.LevelEntityGetter;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.ticks.LevelTickAccess;

import java.util.List;

final class ClientSideLevel extends Level {
    private ClientSideLevel() {
        super(null, null, null, null, null, true, false, 0L, 0);
    }

    private static UnsupportedOperationException no() {
        return new UnsupportedOperationException("a book fixture's level answers only isClientSide");
    }

    @Override
    public void sendBlockUpdated(BlockPos pos, BlockState a, BlockState b, int flags) {
        throw no();
    }

    @Override
    public void playSeededSound(Player p, double x, double y, double z, Holder<SoundEvent> s, SoundSource src, float v,
                                float pitch, long seed) {
        throw no();
    }

    @Override
    public void playSeededSound(Player p, Entity e, Holder<SoundEvent> s, SoundSource src, float v, float pitch,
                                long seed) {
        throw no();
    }

    @Override
    public String gatherChunkSourceStats() {
        throw no();
    }

    @Override
    public Entity getEntity(int id) {
        throw no();
    }

    @Override
    public TickRateManager tickRateManager() {
        throw no();
    }

    @Override
    public MapItemSavedData getMapData(MapId id) {
        throw no();
    }

    @Override
    public void setMapData(MapId id, MapItemSavedData data) {
        throw no();
    }

    @Override
    public MapId getFreeMapId() {
        throw no();
    }

    @Override
    public void destroyBlockProgress(int id, BlockPos pos, int progress) {
        throw no();
    }

    @Override
    public Scoreboard getScoreboard() {
        throw no();
    }

    @Override
    public RecipeManager getRecipeManager() {
        throw no();
    }

    @Override
    protected LevelEntityGetter<Entity> getEntities() {
        throw no();
    }

    @Override
    public PotionBrewing potionBrewing() {
        throw no();
    }

    @Override
    public void setDayTimeFraction(float f) {
        throw no();
    }

    @Override
    public float getDayTimeFraction() {
        throw no();
    }

    @Override
    public float getDayTimePerTick() {
        throw no();
    }

    @Override
    public void setDayTimePerTick(float f) {
        throw no();
    }

    @Override
    public ChunkSource getChunkSource() {
        throw no();
    }

    @Override
    public LevelTickAccess<Block> getBlockTicks() {
        throw no();
    }

    @Override
    public LevelTickAccess<Fluid> getFluidTicks() {
        throw no();
    }

    @Override
    public void levelEvent(Player p, int type, BlockPos pos, int data) {
        throw no();
    }

    @Override
    public void gameEvent(Holder<GameEvent> e, Vec3 at, GameEvent.Context c) {
        throw no();
    }

    @Override
    public float getShade(Direction d, boolean shade) {
        throw no();
    }

    @Override
    public List<? extends Player> players() {
        throw no();
    }

    @Override
    public Holder<Biome> getUncachedNoiseBiome(int x, int y, int z) {
        throw no();
    }

    @Override
    public RegistryAccess registryAccess() {
        throw no();
    }

    @Override
    public FeatureFlagSet enabledFeatures() {
        throw no();
    }
}
