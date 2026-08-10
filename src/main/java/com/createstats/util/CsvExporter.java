package com.createstats.util;

import java.nio.file.Path;
import java.io.IOException;
import com.createstats.CreateStats;
import java.nio.file.OpenOption;
import java.time.LocalDateTime;
import java.nio.file.Files;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.Paths;
import net.minecraft.core.BlockPos;
import com.createstats.data.TimeSeriesStore;
import java.time.format.DateTimeFormatter;

public class CsvExporter
{
    private static final DateTimeFormatter TIMESTAMP_FORMAT;
    
    public static void export(TimeSeriesStore store, BlockPos recorderPos) {
        try {
            final Path exportDir = Paths.get("create_stats", "exports");
            Files.createDirectories(exportDir, (FileAttribute<?>[])new FileAttribute[0]);
            final String timestamp = LocalDateTime.now().format(CsvExporter.TIMESTAMP_FORMAT);
            final String filename = String.format("recorder_%d_%d_%d_%s.csv", recorderPos.getX(), recorderPos.getY(), recorderPos.getZ(), timestamp);
            final Path file = exportDir.resolve(filename);
            final StringBuilder csv = new StringBuilder();
            csv.append("sample_index,value,metric_type\n");
            final float[] data = store.getData();
            for (int i = 0; i < data.length; ++i) {
                csv.append(i).append(",").append(data[i]).append(",").append(store.getMetricType().name()).append("\n");
            }
            Files.writeString(file, csv.toString(), new OpenOption[0]);
            CreateStats.LOGGER.info("Exported {} samples to {}", data.length, file);
        }
        catch (IOException e) {
            CreateStats.LOGGER.error("Failed to export stats CSV", (Throwable)e);
        }
    }
    
    static {
        TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    }
}
