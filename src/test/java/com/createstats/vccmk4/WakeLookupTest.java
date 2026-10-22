package com.createstats.vccmk4;

import com.createstats.flight.bridge.ReflectionAccessors;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WakeLookupTest {
    interface Body {
    }

    static class SubLevel implements Body {
    }

    public static class Pipeline {
        public void wakeUp(Body body) {
        }
    }

    private static final List<Path> WAKERS = List.of(
            Path.of("src/main/java/com/createstats/vccmk4/Vcc4Reflect.java"),
            Path.of("src/main/java/com/createstats/vccmk4/air/AerialReflect.java"),
            Path.of("src/main/java/com/createstats/vcu/VcuActuators.java"),

            Path.of("src/main/java/com/createstats/vccmk4/route/RouteParking.java"));

    private static final Path SABLE_WAKE = Path.of("src/main/java/com/createstats/flight/bridge/SableWake.java");

    @Test
    void anExactLookupMissesAMethodDeclaredOnTheInterface() throws NoSuchMethodException {
        assertThrows(NoSuchMethodException.class, () -> Pipeline.class.getMethod("wakeUp", SubLevel.class));
        Method m = ReflectionAccessors.findMethodAccepting(Pipeline.class, "wakeUp", SubLevel.class);
        assertNotNull(m, "the assignable finder takes the interface's method");
        assertTrue(m.equals(Pipeline.class.getMethod("wakeUp", Body.class)));
    }

    @Test
    void everyWakeUpLookupTakesTheDeclaredInterface() throws IOException {
        List<String> problems = new ArrayList<>();
        Pattern lookup = Pattern.compile("(\\w+)\\s*\\(([^;]*?)\"wakeUp\"([^;]*?)\\)");
        String wake = code(SABLE_WAKE);
        Matcher m = lookup.matcher(wake);
        int found = 0;
        while (m.find()) {
            found++;
            if (!m.group(1).equals("findVirtual")) problems.add("SableWake: wakeUp looked up by " + m.group(1));
            if (!m.group(3).matches("\\s*,\\s*void\\.class\\s*,\\s*body\\s*")) {
                problems.add("SableWake: wakeUp must be asked for as (void, body), body being PhysicsPipelineBody: " + m.group());
            }
        }
        if (found != 1) problems.add("SableWake: expected one wakeUp lookup, found " + found);
        if (!wake.contains("BODY = \"dev.ryanhcode.sable.api.physics.PhysicsPipelineBody\"")) {
            problems.add("SableWake: the body type must be Sable's declared interface, PhysicsPipelineBody");
        }
        if (!wake.matches("(?s).*Class<\\?> body = ReflectionAccessors\\.findClass\\(BODY\\);.*")) {
            problems.add("SableWake: the handle's body type must be the class named by BODY");
        }
        for (Path p : WAKERS) {
            String src = code(p);
            if (lookup.matcher(src).find()) problems.add(p.getFileName() + ": looks up wakeUp itself instead of SableWake");
            if (!src.contains("SableWake.wake(level, ")) problems.add(p.getFileName() + ": does not hand its wake to SableWake");
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    private static String code(Path p) throws IOException {
        return Files.readString(p, StandardCharsets.UTF_8).replace("\r\n", "\n")
                .replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("//[^\n]*", "");
    }
}
