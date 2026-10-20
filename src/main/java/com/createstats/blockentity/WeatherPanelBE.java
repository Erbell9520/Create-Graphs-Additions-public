package com.createstats.blockentity;

import com.createstats.block.WeatherPanelBlock;
import com.createstats.power.BoreLinks;
import com.createstats.power.FeGridMember;
import com.createstats.power.FeLock;
import com.createstats.power.FeManagedLoad;
import com.createstats.power.FeManagedState;
import com.createstats.power.FePriority;
import com.createstats.power.FeWireNetwork;
import com.createstats.weather.BarometerModel;
import com.createstats.weather.ClimateModel;
import com.createstats.weather.Condition;
import com.createstats.weather.Forecast;
import com.createstats.weather.ForecastEngine;
import com.createstats.weather.SimState;
import com.createstats.weather.WeatherHistory;
import com.createstats.weather.WeatherSnapshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ServerLevelData;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

// one method touches mc weather, the rest is pure and tested
public class WeatherPanelBE extends BlockEntity implements FeGridMember, FeManagedLoad,
        BoreLinks.Socket, com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation {
    public static final int DRAIN = 1;

    private static final int BUFFER = 200;

    private static final int SAMPLE_TICKS = 20;

    private static final int FORECAST_SAMPLES = 20;

    private static final int RECORD_SAMPLES = WeatherHistory.SAMPLE_TICKS / SAMPLE_TICKS;

    private int energy;
    @Nullable
    private FeWireNetwork network;
    private final FeManagedState managed = new FeManagedState();
    private int ratedDraw;
    private int measuredDraw;

    private final List<BlockPos> bores = new ArrayList<>(1);

    @Nullable
    private WeatherSnapshot snapshot;
    private Forecast forecast = Forecast.none();
    private float temperatureC;
    private float humidity;
    private float pressure = Float.NaN;
    private WeatherHistory history = new WeatherHistory();

    private int sinceSample = SAMPLE_TICKS;
    private int sinceForecast = FORECAST_SAMPLES;
    private int sinceRecord = RECORD_SAMPLES;

    private WeatherPanelBlock.Screen savedBand = WeatherPanelBlock.Screen.DARK;
    private float savedTemperature = Float.NaN;
    private float savedHumidity = Float.NaN;
    private float savedPressure = Float.NaN;
    private Forecast savedForecast = Forecast.none();
    private int savedCountdownBucket = Integer.MIN_VALUE;

    private final IEnergyStorage energyView = new IEnergyStorage() {
        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            final int room = Math.min(maxReceive, BUFFER - energy);
            if (room <= 0) {
                return 0;
            }
            if (!simulate) {
                energy += room;
                setChanged();
            }
            return room;
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            return 0;
        }

        @Override
        public int getEnergyStored() {
            return energy;
        }

        @Override
        public int getMaxEnergyStored() {
            return BUFFER;
        }

        @Override
        public boolean canExtract() {
            return false;
        }

        @Override
        public boolean canReceive() {
            return true;
        }
    };

    public WeatherPanelBE(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public IEnergyStorage energyHandle() {
        return energyView;
    }

    public static int drawPerTick() {
        return DRAIN;
    }

    public void tick() {
        if (level == null || level.isClientSide()) {
            return;
        }
        if (network == null || network.isDirty()) {
            if (BoreLinks.prune(level, this)) {
                return;
            }
            FeWireNetwork.buildFrom(this);
        }
        if (network != null) {
            network.solveOnce(level);
        }

        if (network == null || !network.isPduManaged(level.getGameTime())) {
            setManagedShed(false);
        }

        final boolean wantsToRun = !managed.isSuppressed();
        ratedDraw = wantsToRun ? DRAIN : 0;
        final boolean awake = wantsToRun && energy >= DRAIN;
        if (awake) {
            energy -= DRAIN;
            measuredDraw = DRAIN;
            setChanged();
        } else {
            measuredDraw = 0;
        }

        if (!awake) {
            refresh(false);
            return;
        }

        if (++sinceSample < SAMPLE_TICKS) {
            return;
        }
        sinceSample = 0;

        final WeatherSnapshot fresh = sample();
        final boolean moved = snapshot == null || !fresh.sim().equals(snapshot.sim());
        snapshot = fresh;
        if (moved || ++sinceForecast >= FORECAST_SAMPLES) {
            sinceForecast = 0;
            forecast = ForecastEngine.compute(fresh);
        }

        final float wet = ClimateModel.wetnessOf(fresh.sim());
        temperatureC = ClimateModel.temperatureC(fresh, wet);
        humidity = ClimateModel.humidity(fresh, wet);
        pressure = BarometerModel.pressure(fresh, forecast);

        if (++sinceRecord >= RECORD_SAMPLES) {
            sinceRecord = 0;
            history.record(Math.round(temperatureC * 10f), Math.round(humidity),
                    Float.isNaN(pressure) ? 1013 : Math.round(pressure), fresh.condition());
        }
        refresh(true);
    }

    private WeatherSnapshot sample() {
        final ServerLevel server = (ServerLevel) level;
        int clear = 0;
        int thunder = 0;
        int rain = 0;
        if (server.getLevelData() instanceof ServerLevelData data) {
            clear = data.getClearWeatherTime();
            thunder = data.getThunderTime();
            rain = data.getRainTime();
        }
        final SimState sim = new SimState(clear, thunder, rain,
                server.isThundering(), server.isRaining());

        final Biome biome = server.getBiome(worldPosition).value();

        final Biome.ClimateSettings climate = biome.getModifiedClimateSettings();
        // temperature modifier makes frozen ocean patchy. height is done in ClimateModel

        final float temperature = climate.temperatureModifier()
                .modifyTemperature(worldPosition, biome.getBaseTemperature());

        final Condition local = switch (biome.getPrecipitationAt(worldPosition)) {
            case SNOW -> Condition.SNOW;
            case NONE -> Condition.DRY;
            default -> Condition.RAIN;
        };

        return new WeatherSnapshot(sim, server.getSeed(), server.getDayTime(), server.getGameTime(),
                (int) (server.getDayTime() / 24000L), temperature, climate.downfall(), local,
                worldPosition.getY(), server.dimensionType().hasSkyLight(),
                server.getGameRules().getBoolean(GameRules.RULE_WEATHER_CYCLE));
    }

    private void refresh(boolean awake) {
        if (level == null || !(getBlockState().getBlock() instanceof WeatherPanelBlock)) {
            return;
        }
        final WeatherPanelBlock.Screen band = awake ? bandOf() : WeatherPanelBlock.Screen.DARK;
        final BlockState state = getBlockState();
        if (state.getValue(WeatherPanelBlock.SCREEN) != band) {
            savedBand = band;
            remember();
            level.setBlock(worldPosition, state.setValue(WeatherPanelBlock.SCREEN, band),
                    Block.UPDATE_ALL);
        } else if (awake && worthSending()) {
            remember();
            setChanged();

            level.sendBlockUpdated(worldPosition, state, state, 2);
        }
    }

    private WeatherPanelBlock.Screen bandOf() {
        if (snapshot == null) {
            return WeatherPanelBlock.Screen.DARK;
        }
        return switch (snapshot.condition()) {
            case CLEAR -> WeatherPanelBlock.Screen.CLEAR;
            case RAIN -> WeatherPanelBlock.Screen.RAIN;
            case STORM -> WeatherPanelBlock.Screen.STORM;
            case SNOW -> WeatherPanelBlock.Screen.SNOW;
            case DRY -> WeatherPanelBlock.Screen.DRY;
            case NONE -> WeatherPanelBlock.Screen.NO_SKY;
        };
    }

    private boolean worthSending() {
        return !nearly(temperatureC, savedTemperature, 0.5f)
                || !nearly(humidity, savedHumidity, 1.0f)
                || !nearly(pressure, savedPressure, 1.0f)
                || !forecast.equals(savedForecast)
                || countdownBucket() != savedCountdownBucket;
    }

    private static boolean nearly(float a, float b, float tolerance) {
        if (Float.isNaN(a) || Float.isNaN(b)) {
            return Float.isNaN(a) && Float.isNaN(b);
        }
        return Math.abs(a - b) < tolerance;
    }

    private int countdownBucket() {
        return ticksToNextChange() / 200;
    }

    private void remember() {
        savedTemperature = temperatureC;
        savedHumidity = humidity;
        savedPressure = pressure;
        savedForecast = forecast;
        savedCountdownBucket = countdownBucket();
    }

    @Nullable
    public WeatherSnapshot snapshot() {
        return snapshot;
    }

    public Forecast forecast() {
        return forecast;
    }

    public Condition condition() {
        return snapshot == null ? Condition.CLEAR : snapshot.condition();
    }

    public float temperatureC() {
        return temperatureC;
    }

    public float humidity() {
        return humidity;
    }

    public float pressure() {
        return pressure;
    }

    public float pressureOneHourAgo() {
        return history.pressureOneHourAgo(pressure);
    }

    public int[] historyTemperatureTenths() {
        return history.temperatureTenths();
    }

    public int historySize() {
        return history.size();
    }

    public int ticksToNextChange() {
        if (snapshot == null || !snapshot.hasSkyLight() || !snapshot.weatherCycle()) {
            return -1;
        }
        final SimState sim = snapshot.sim();
        return sim.clearWeatherTime() > 0 ? sim.clearWeatherTime() : sim.rainTime();
    }

    public int ticksToNextChangeFor(long gameTime) {
        return com.createstats.power.FeSupplyOutlook.liveCountdown(ticksToNextChange(), syncGameTime(),
                gameTime);
    }

    public long syncGameTime() {
        return snapshot == null ? 0L : snapshot.gameTime();
    }

    public boolean isAwake() {
        return getBlockState().hasProperty(WeatherPanelBlock.SCREEN)
                && getBlockState().getValue(WeatherPanelBlock.SCREEN)
                        != WeatherPanelBlock.Screen.DARK;
    }

    @Override
    public long nodeKey() {
        return worldPosition.asLong();
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
        return 64;
    }

    @Override
    public IEnergyStorage attachedStorage() {
        return energyView;
    }

    @Override
    public boolean isGone(Level owner) {
        return isRemoved() || getLevel() != owner;
    }

    @Override
    public void setLastFlow(int flow) {
    }

    @Override
    public void adoptNetwork(FeWireNetwork network) {
        this.network = network;
    }

    @Override
    @Nullable
    public FeWireNetwork network() {
        return network;
    }

    @Override
    public void invalidateNetwork() {
        if (network != null) {
            network.markDirty();
            network = null;
        }
    }

    @Override
    public BlockPos borePosition() {
        return worldPosition;
    }

    @Override
    public List<BlockPos> bores() {
        return bores;
    }

    @Override
    public boolean addBore(BlockPos far) {
        if (bores.contains(far)) {
            return false;
        }
        bores.add(far.immutable());
        return true;
    }

    @Override
    public boolean removeBore(BlockPos far) {
        return bores.remove(far);
    }

    @Override
    public void invalidate(Level owner) {
        setChanged();
        invalidateNetwork();
        if (owner != null && !owner.isClientSide()) {
            owner.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
        }
    }

    @Override
    public int ratedDrawFePerTick() {
        return ratedDraw;
    }

    @Override
    public int measuredDrawFePerTick() {
        return measuredDraw;
    }

    @Override
    public FePriority managedPriority() {
        return managed.priority();
    }

    @Override
    public void setManagedPriority(FePriority tier) {
        managed.setPriority(tier);
        setChanged();
    }

    @Override
    public FeLock managedLock() {
        return managed.lock();
    }

    @Override
    public void setManagedLock(FeLock lock) {
        managed.setLock(lock);
        setChanged();
    }

    @Override
    public void setManagedShed(boolean shed) {
        if (managed.isShed() == shed) {
            return;
        }
        managed.setShed(shed);
    }

    @Override
    public boolean isManagedShed() {
        return managed.isShed();
    }

    @Override
    public boolean addToGoggleTooltip(List<net.minecraft.network.chat.Component> tooltip,
                                      boolean isPlayerSneaking) {
        tooltip.add(com.createstats.power.FeGoggle.title("Weather Panel"));
        if (!isAwake()) {
            tooltip.add(com.createstats.power.FeGoggle.bad("No power, screen dark"));
        } else if (snapshot == null) {
            tooltip.add(com.createstats.power.FeGoggle.dim("Taking a reading"));
        } else if (!snapshot.hasSkyLight()) {
            tooltip.add(com.createstats.power.FeGoggle.warn("No sky here, nothing to report"));
        } else {
            tooltip.add(com.createstats.power.FeGoggle.ok(
                    conditionWord() + ", " + Math.round(temperatureC) + " C, "
                            + Math.round(humidity) + "%"));
            final int next = ticksToNextChange();
            if (next >= 0) {
                tooltip.add(com.createstats.power.FeGoggle.dim(nextWord() + " in " + mcTime(next)));
            }
        }
        tooltip.add(com.createstats.power.FeGoggle.draw(measuredDraw));
        return true;
    }

    private String conditionWord() {
        return switch (condition()) {
            case CLEAR -> "Clear";
            case RAIN -> "Rain";
            case STORM -> "Storm";
            case SNOW -> "Snow";
            case DRY -> "Dry here, rain elsewhere";
            case NONE -> "No sky";
        };
    }

    private String nextWord() {
        return snapshot != null && snapshot.sim().raining() ? "Clearing" : "Rain";
    }

    public static String mcTime(int ticks) {
        if (ticks < 0) {
            return "--";
        }
        final int minutes = (int) (ticks / (24000.0 / 1440.0));
        if (minutes >= 60) {
            return (minutes / 60) + "h " + String.format("%02dm", minutes % 60);
        }
        if (minutes >= 1) {
            return minutes + "m";
        }
        return Math.max(1, ticks / 20) + "s";
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Energy", energy);
        tag.putInt("Tier", managed.priorityOrdinal());
        tag.putInt("Lock", managed.lockOrdinal());
        tag.putIntArray("History", history.save());
        tag.putFloat("TempC", temperatureC);
        tag.putFloat("Humidity", humidity);
        tag.putFloat("Pressure", pressure);
        if (snapshot != null) {
            final SimState sim = snapshot.sim();
            tag.putIntArray("Sim", new int[]{sim.clearWeatherTime(), sim.thunderTime(),
                    sim.rainTime(), sim.thundering() ? 1 : 0, sim.raining() ? 1 : 0});
            tag.putLong("Seed", snapshot.worldSeed());
            tag.putLong("DayTime", snapshot.dayTime());
            tag.putLong("SyncTime", snapshot.gameTime());
            tag.putFloat("BiomeTemp", snapshot.biomeTemperature());
            tag.putFloat("Downfall", snapshot.downfall());
            tag.putInt("Local", snapshot.localPrecipitation().ordinal());
            tag.putBoolean("HasSky", snapshot.hasSkyLight());
            tag.putBoolean("Cycle", snapshot.weatherCycle());
        }
        saveForecast(tag);
    }

    private void saveForecast(CompoundTag tag) {
        final List<Forecast.DayCell> days = forecast.days();
        final int[] packedDays = new int[days.size() * 6];
        for (int i = 0; i < days.size(); i++) {
            final Forecast.DayCell d = days.get(i);
            packedDays[i * 6] = d.dayIndex();
            packedDays[i * 6 + 1] = d.condition().ordinal();
            packedDays[i * 6 + 2] = d.popPercent();
            packedDays[i * 6 + 3] = d.certain() ? 1 : 0;
            packedDays[i * 6 + 4] = Math.round(d.highC() * 10f);
            packedDays[i * 6 + 5] = Math.round(d.lowC() * 10f);
        }
        final List<Forecast.Band> bands = forecast.bands();
        final int[] packedBands = new int[bands.size() * 4];
        for (int i = 0; i < bands.size(); i++) {
            final Forecast.Band b = bands.get(i);
            packedBands[i * 4] = b.startTick();
            packedBands[i * 4 + 1] = b.endTick();
            packedBands[i * 4 + 2] = b.condition().ordinal();
            packedBands[i * 4 + 3] = b.certain() ? 1 : 0;
        }
        tag.putIntArray("FcDays", packedDays);
        tag.putIntArray("FcBands", packedBands);
        tag.putInt("FcCertain", forecast.certainUntilTicks());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        energy = tag.getInt("Energy");
        managed.load(tag.getInt("Tier"), tag.getInt("Lock"));
        history = WeatherHistory.load(tag.getIntArray("History"));
        temperatureC = tag.getFloat("TempC");
        humidity = tag.getFloat("Humidity");
        pressure = tag.contains("Pressure") ? tag.getFloat("Pressure") : Float.NaN;

        final int[] sim = tag.getIntArray("Sim");
        if (sim.length == 5) {
            snapshot = new WeatherSnapshot(
                    new SimState(sim[0], sim[1], sim[2], sim[3] != 0, sim[4] != 0),
                    tag.getLong("Seed"), tag.getLong("DayTime"), tag.getLong("SyncTime"),
                    (int) (tag.getLong("DayTime") / 24000L),
                    tag.getFloat("BiomeTemp"), tag.getFloat("Downfall"),
                    Condition.values()[Math.floorMod(tag.getInt("Local"), Condition.values().length)],
                    worldPosition.getY(), tag.getBoolean("HasSky"), tag.getBoolean("Cycle"));
        } else {
            snapshot = null;
        }
        loadForecast(tag);
    }

    private void loadForecast(CompoundTag tag) {
        final Condition[] conditions = Condition.values();
        final int[] packedDays = tag.getIntArray("FcDays");
        final List<Forecast.DayCell> days = new ArrayList<>(packedDays.length / 6);
        for (int i = 0; i + 5 < packedDays.length; i += 6) {
            days.add(new Forecast.DayCell(packedDays[i],
                    conditions[Math.floorMod(packedDays[i + 1], conditions.length)],
                    packedDays[i + 2], packedDays[i + 3] != 0,
                    packedDays[i + 4] / 10f, packedDays[i + 5] / 10f));
        }
        final int[] packedBands = tag.getIntArray("FcBands");
        final List<Forecast.Band> bands = new ArrayList<>(packedBands.length / 4);
        for (int i = 0; i + 3 < packedBands.length; i += 4) {
            bands.add(new Forecast.Band(packedBands[i], packedBands[i + 1],
                    conditions[Math.floorMod(packedBands[i + 2], conditions.length)],
                    packedBands[i + 3] != 0));
        }
        forecast = days.isEmpty() && bands.isEmpty()
                ? Forecast.none()
                : new Forecast(List.copyOf(bands), List.copyOf(days), tag.getInt("FcCertain"));
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        final CompoundTag tag = super.getUpdateTag(registries);
        saveAdditional(tag, registries);
        return tag;
    }

    @Override
    public net.minecraft.network.protocol.Packet<
            net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        loadAdditional(tag, registries);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        invalidateNetwork();
        if (level != null && level.isClientSide()) {
            com.createstats.renderer.WeatherFaceTextureManager.disposeFor(worldPosition);
        }
    }
}
