package com.createstats.data;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MetricParser
{
    private static final Pattern NUMBER_PATTERN;
    private static final Pattern PERCENT_PATTERN;
    private static final Pattern RPM_PATTERN;
    private static final Pattern SU_PATTERN;

    public static float parse(String text) {
        if (text == null || text.isEmpty()) {
            return 0.0f;
        }
        final String cleaned = text.replaceAll("§[0-9a-fk-or]", "").trim();
        final Matcher numberMatcher = MetricParser.NUMBER_PATTERN.matcher(cleaned);
        if (numberMatcher.find()) {
            try {
                final String numStr = numberMatcher.group(1).replace(",", "");
                return Float.parseFloat(numStr);
            }
            catch (NumberFormatException e) {
                return 0.0f;
            }
        }
        return 0.0f;
    }

    public static MetricType detectType(String text) {
        if (text == null || text.isEmpty()) {
            return MetricType.GENERIC;
        }
        final String cleaned = text.replaceAll("§[0-9a-fk-or]", "").trim().toLowerCase();
        final boolean isPercent = MetricParser.PERCENT_PATTERN.matcher(cleaned).find();
        if (MetricParser.RPM_PATTERN.matcher(cleaned).find() || cleaned.contains("rpm") || cleaned.contains("speed") || cleaned.contains("rotation")) {
            return MetricType.RPM;
        }
        if (cleaned.contains("fe/t") || cleaned.contains("fe per tick")) {
            return MetricType.ENERGY_FE_PER_TICK;
        }
        if (!isPercent && (cleaned.contains(" fe") || cleaned.contains("energy") || cleaned.contains("charge"))) {
            return MetricType.ENERGY_FE;
        }
        if (MetricParser.SU_PATTERN.matcher(cleaned).find() || cleaned.contains("stress") || cleaned.contains("capacity") || cleaned.contains("network")) {
            if (MetricParser.PERCENT_PATTERN.matcher(cleaned).find()) {
                return MetricType.STRESS_PERCENT;
            }
            return MetricType.STRESS_SU;
        }
        else {
            if (cleaned.contains("/s") || cleaned.contains("per sec") || cleaned.contains("per second")) {
                return MetricType.ITEMS_PER_SEC;
            }
            if (cleaned.contains("/hr") || cleaned.contains("per hour") || cleaned.contains("per hr")) {
                return MetricType.ITEMS_PER_HR;
            }
            if (cleaned.contains("item") || cleaned.contains("/min") || cleaned.contains("per") || cleaned.contains("throughput")) {
                return MetricType.ITEMS_PER_MIN;
            }
            if (cleaned.contains("mb") || cleaned.contains("bucket") || cleaned.contains("fluid")) {
                return MetricType.FLUID_MB;
            }
            if (cleaned.contains("heat") || cleaned.contains("blaze") || cleaned.contains("boiler")) {
                return MetricType.HEAT_LEVEL;
            }
            if (cleaned.contains("redstone") || cleaned.contains("signal")) {
                return MetricType.REDSTONE_POWER;
            }
            if (isPercent && (cleaned.contains("fill") || cleaned.contains("level") || cleaned.contains("tank") || cleaned.contains("stock") || cleaned.contains("charge") || cleaned.contains("energy"))) {
                return MetricType.FILL_LEVEL;
            }
            return MetricType.GENERIC;
        }
    }

    public static ParseResult parseWithType(String text) {
        return new ParseResult(parse(text), detectType(text));
    }

    static {
        NUMBER_PATTERN = Pattern.compile("([+-]?[\\d,]+\\.?\\d*)");
        PERCENT_PATTERN = Pattern.compile("([\\d.]+)\\s*%");
        RPM_PATTERN = Pattern.compile("([\\d,.]+)\\s*(?:RPM|rpm)", 2);
        SU_PATTERN = Pattern.compile("([\\d,.]+)\\s*(?:su|SU)");
    }

    public record ParseResult(float value, MetricType type) {}
}
