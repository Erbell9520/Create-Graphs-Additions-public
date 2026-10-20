package com.createstats.dynlight;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DrawnBeamWiringTest {
    private static final Path SOURCE_ROOT = Path.of("src/main/java");
    private static final Path LIGHTS = SOURCE_ROOT.resolve("com/createstats/client/dynlight/VehicleLightLights.java");
    private static final Path DRAWN = SOURCE_ROOT.resolve("com/createstats/client/dynlight/DrawnBeams.java");
    private static final Path ENGINE = SOURCE_ROOT.resolve("com/createstats/client/dynlight/ClientDynamicLights.java");
    private static final Path CONFIG = SOURCE_ROOT.resolve("com/createstats/ModConfiguration.java");

    static String code(Path file) throws IOException {
        return Files.readString(file).replace("\r\n", "\n")
                .replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("//[^\\n]*", "");
    }

    static String method(String source, String header) {
        final int at = source.indexOf(header);
        assertTrue(at >= 0, "found: " + header);
        assertEquals(at, source.lastIndexOf(header), "exactly one: " + header);
        final int open = source.indexOf('{', at);
        assertTrue(open >= 0, "a body: " + header);
        int depth = 0;
        for (int i = open; i < source.length(); i++) {
            final char c = source.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}' && --depth == 0) {
                return source.substring(open, i + 1);
            }
        }
        return source.substring(open);
    }

    @Test
    void theHeadlightsAreHandedToTheDrawnPassAndTheirBlockLightIsTheDimmedUnderlay() throws IOException {
        final String collect = method(code(LIGHTS), "static void collect(ClientLevel level, LightSink sink)");
        assertTrue(collect.contains("DrawnBeams.publish(be, craft)"), "collect hands every lit headlight over");
        assertTrue(collect.contains("DrawnBeams.isActive()"), "and asks whether the beam is drawn");
        final int dim = collect.indexOf("VehicleLightBeams.UNDERLAY_DIM");
        assertTrue(dim >= 0, "the block light is dimmed by the one constant");
        final int headlight = collect.indexOf("VehicleLightBeams.headlight(");
        assertTrue(headlight >= 0 && dim > headlight && dim < collect.indexOf(';', headlight),
                "and the dim goes into the headlight beam itself, not somewhere else");
        assertTrue(collect.contains("? VehicleLightBeams.UNDERLAY_DIM : 0"),
                "full strength when the beam is not drawn");
    }

    @Test
    void theDrawnPassIsAClientSubscriberAtTheCutoutStage() throws IOException {
        final String drawn = code(DRAWN);
        assertTrue(drawn.contains("@EventBusSubscriber(modid = CreateStats.MOD_ID, value = Dist.CLIENT)"),
                "a client game-bus subscriber, registered by the annotation scan");
        final int handler = drawn.indexOf("public static void onRenderStage(RenderLevelStageEvent event)");
        assertTrue(handler >= 0, "a RenderLevelStageEvent handler");
        final int annotation = drawn.lastIndexOf("@SubscribeEvent", handler);
        assertTrue(annotation >= 0 && drawn.substring(annotation + "@SubscribeEvent".length(), handler).isBlank(),
                "subscribed: the annotation sits right on the handler");
        final String body = method(drawn, "public static void onRenderStage(RenderLevelStageEvent event)");
        assertTrue(body.contains("event.getStage() != RenderLevelStageEvent.Stage.AFTER_CUTOUT_BLOCKS"),
                "drawn after the cutout terrain and before entities and translucent blocks, nowhere else");
        assertTrue(body.contains("failed = true"), "a throw switches the pass off");
    }

    @Test
    void theOffSwitchesReachTheRealConfigKeyAndTheRealIrisApi() throws IOException {
        final String config = code(CONFIG);
        final int section = config.indexOf(".push(\"dynamicLights\")");
        assertTrue(section >= 0, "the dynamicLights section");
        final int end = config.indexOf("builder.pop()", section);
        final int key = config.indexOf(".define(\"drawnBeams\", true)", section);
        assertTrue(key >= 0 && key < end, "drawnBeams is defined inside the dynamicLights section, on by default");
        assertTrue(config.substring(section, key).contains("this.dynamicLightsDrawnBeams = builder"),
                "into the field the engine reads");
        final String engine = code(ENGINE);
        assertTrue(method(engine, "static Settings read()").contains("c.dynamicLightsDrawnBeams.get()"),
                "the engine's settings read the key");
        final String drawn = code(DRAWN);
        assertTrue(method(drawn, "static boolean isActive()").contains("settings().drawnBeams()"),
                "and the pass asks the settings");
        assertTrue(drawn.contains("\"net.irisshaders.iris.api.v0.IrisApi\"") && drawn.contains("\"isShaderPackInUse\""),
                "the Iris check names the real API");
        assertTrue(method(drawn, "static boolean isActive()").contains("!shaderPack"), "and the pass honours it");
    }
}
