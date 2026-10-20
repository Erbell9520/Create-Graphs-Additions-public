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

    private static final List<Path> SOURCES = List.of(
            Path.of("src/main/java/com/createstats/vccmk4/Vcc4Reflect.java"),
            Path.of("src/main/java/com/createstats/vccmk4/air/AerialReflect.java"));

    @Test
    void anExactLookupMissesAMethodDeclaredOnTheInterface() throws NoSuchMethodException {
        assertThrows(NoSuchMethodException.class, () -> Pipeline.class.getMethod("wakeUp", SubLevel.class));
        Method m = ReflectionAccessors.findMethodAccepting(Pipeline.class, "wakeUp", SubLevel.class);
        assertNotNull(m, "the assignable finder takes the interface's method");
        assertTrue(m.equals(Pipeline.class.getMethod("wakeUp", Body.class)));
    }

    @Test
    void everyWakeUpLookupTakesAnAssignableParameter() throws IOException {
        List<String> problems = new ArrayList<>();
        Pattern lookup = Pattern.compile("(\\w+)\\s*\\(([^;]*?)\"wakeUp\"");
        for (Path p : SOURCES) {
            String src = Files.readString(p, StandardCharsets.UTF_8).replace("\r\n", "\n")
                    .replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("//[^\n]*", "");
            Matcher m = lookup.matcher(src);
            int found = 0;
            while (m.find()) {
                found++;
                String call = m.group(1);
                if (!call.equals("findMethodAccepting")) problems.add(p.getFileName() + ": wakeUp looked up by " + call);
            }
            if (found == 0) problems.add(p.getFileName() + ": no wakeUp lookup found (moved? this scan must follow it)");
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }
}
