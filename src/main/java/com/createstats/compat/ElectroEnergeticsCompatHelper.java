package com.createstats.compat;

import com.createstats.blockentity.IAutoDataReceiver;
import com.createstats.data.MetricType;
import com.createstats.util.InstrumentSourceHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

// read the meter, not its text. "1.20kV" landed as 1.20 before
public final class ElectroEnergeticsCompatHelper {
    public static final String SOURCE_AMMETER = "Ammeter";
    public static final String SOURCE_VOLTMETER = "Voltmeter";

    private static final String AMMETER_PATH = "ammeter";
    private static final String VOLTMETER_PATH = "voltmeter";

    private record Accessors(Field voltage, @Nullable Field scaling, @Nullable Method scale) {
    }

    private static final ClassValue<Optional<Accessors>> ACCESSORS = new ClassValue<>() {
        @Override
        protected Optional<Accessors> computeValue(Class<?> type) {
            try {
                final Field voltage = type.getField("voltage");
                Field scaling = null;
                Method scale = null;
                try {
                    scaling = type.getField("scaling");
                    scale = scaling.getType().getMethod("getScale");
                } catch (ReflectiveOperationException ignored) {
                }
                return Optional.of(new Accessors(voltage, scaling, scale));
            } catch (ReflectiveOperationException e) {
                return Optional.empty();
            }
        }
    };

    private ElectroEnergeticsCompatHelper() {
    }

    public static String getSourceType(@Nullable BlockEntity sourceBE) {
        if (sourceBE == null || !ElectroEnergeticsCompat.isAvailable()) {
            return "";
        }
        final ResourceLocation id = BuiltInRegistries.BLOCK.getKey(sourceBE.getBlockState().getBlock());
        if (id == null || !ElectroEnergeticsCompat.MOD_ID.equals(id.getNamespace())) {
            return "";
        }
        return switch (id.getPath()) {
            case AMMETER_PATH -> SOURCE_AMMETER;
            case VOLTMETER_PATH -> SOURCE_VOLTMETER;
            default -> "";
        };
    }

    public static boolean isMeter(@Nullable BlockEntity sourceBE) {
        return !getSourceType(sourceBE).isEmpty();
    }

    @Nullable
    public static InstrumentSourceHelper.SourceReadout readSource(@Nullable BlockEntity sourceBE) {
        final String sourceType = getSourceType(sourceBE);
        if (sourceType.isEmpty()) {
            return null;
        }
        final Optional<Accessors> accessors = ACCESSORS.get(sourceBE.getClass());
        if (accessors.isEmpty()) {
            return null;
        }
        final boolean voltmeter = SOURCE_VOLTMETER.equals(sourceType);
        final double reading;
        try {
            final Accessors a = accessors.get();
            final double voltage = a.voltage().getDouble(sourceBE);
            double scale = 1.0;
            if (a.scaling() != null && a.scale() != null) {
                final Object knob = a.scaling().get(sourceBE);
                if (knob != null) {
                    scale = ((Number) a.scale().invoke(knob)).doubleValue();
                }
            }
            reading = ElectroMeterReading.reading(voltmeter, voltage, scale);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
        if (!Double.isFinite(reading)) {
            return null;
        }
        final Map<String, InstrumentSourceHelper.MetricSample> metrics = new LinkedHashMap<>();
        final String metricId = voltmeter ? "voltage" : "current";
        metrics.put(metricId, new InstrumentSourceHelper.MetricSample(metricId,
                voltmeter ? "Voltage" : "Current", (float) reading,
                voltmeter ? MetricType.VOLTAGE : MetricType.CURRENT));
        return new InstrumentSourceHelper.SourceReadout(sourceType,
                InstrumentSourceHelper.SourceKind.UNKNOWN, metrics);
    }

    public static boolean emitAutoData(IAutoDataReceiver receiver, BlockPos sourcePos,
                                       @Nullable BlockEntity sourceBE) {
        final InstrumentSourceHelper.SourceReadout readout = readSource(sourceBE);
        if (readout == null) {
            return false;
        }
        receiver.setSourceType(readout.sourceType());
        for (InstrumentSourceHelper.MetricSample sample : readout.metrics().values()) {
            receiver.onAutoData(sourcePos, readout.sourceType(), sample.metricId(), sample.label(),
                    sample.value(), sample.type());
        }
        return true;
    }
}
