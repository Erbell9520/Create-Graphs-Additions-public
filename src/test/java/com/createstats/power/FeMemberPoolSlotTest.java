package com.createstats.power;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

class FeMemberPoolSlotTest {
    private static final Path SOURCES = Path.of("src/main/java/com/createstats");

    private static final Pattern SETTER = Pattern.compile("void\\s+setStoredEnergy\\s*\\(\\s*int\\s+\\w+\\s*\\)\\s*\\{");
    private static final Pattern CAPACITY = Pattern.compile("int\\s+bufferCapacity\\s*\\(\\s*\\)\\s*\\{");

    private static String body(String source, int open) {
        int depth = 0;
        for (int i = open; i < source.length(); i++) {
            final char c = source.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}' && --depth == 0) {
                return source.substring(open + 1, i);
            }
        }
        throw new IllegalStateException("unbalanced braces after " + open);
    }

    private static String code(String text) {
        return text.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("//[^\\n]*", "").replaceAll("\\s+", " ").trim();
    }

    @Test
    @DisplayName("every node with an empty setStoredEnergy answers bufferCapacity() with 0")
    void noPhantomPoolSlots() throws IOException {
        final List<String> checked = new ArrayList<>();
        final List<String> wrong = new ArrayList<>();
        final List<Path> files;
        try (Stream<Path> walk = Files.walk(SOURCES)) {
            files = walk.filter(p -> p.toString().endsWith(".java")).toList();
        }
        for (Path file : files) {
            final String source = Files.readString(file, StandardCharsets.UTF_8);
            final Matcher setter = SETTER.matcher(source);
            if (!setter.find() || !code(body(source, setter.end() - 1)).isEmpty()) {
                continue;
            }
            final String name = file.getFileName().toString();
            checked.add(name);
            final Matcher capacity = CAPACITY.matcher(source);
            if (!capacity.find()) {
                wrong.add(name + ": no bufferCapacity() to check");
                continue;
            }
            final String answer = code(body(source, capacity.end() - 1));
            if (!answer.equals("return 0;")) {
                wrong.add(name + ": bufferCapacity() is `" + answer + "`");
            }
        }

        assertTrue(checked.contains("PortablePowerInterfaceBE.java"), "scan missed the interface: " + checked);
        assertTrue(checked.contains("LampBE.java") && checked.contains("FeAttachment.java"),
                "scan missed a known machine: " + checked);
        assertTrue(checked.size() >= 20, "only " + checked.size() + " members found: " + checked);
        assertTrue(wrong.isEmpty(), "a node the settle cannot fill advertises room for pool FE, "
                + "which the solve then deletes (KI-33): " + wrong);
    }
}
