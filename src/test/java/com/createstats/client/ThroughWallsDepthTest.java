package com.createstats.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThroughWallsDepthTest {
    private static final Path SRC = Path.of("src/main/java/com/createstats/client/ThroughWalls.java");

    @Test
    @DisplayName("the through-wall type forces ALWAYS while it draws and puts LEQUAL back")
    void theTypeForcesTheDepthFunction() {
        final String src = code(read(SRC));
        final Matcher shard = Pattern.compile("LayeringStateShard\\s+(\\w+)\\s*=\\s*new\\s+LayeringStateShard\\(\\s*\"[^\"]*\""
                + "\\s*,\\s*\\(\\)\\s*->\\s*RenderSystem\\.depthFunc\\(GL11\\.GL_ALWAYS\\)\\s*,"
                + "\\s*\\(\\)\\s*->\\s*RenderSystem\\.depthFunc\\(GL11\\.GL_LEQUAL\\)\\s*\\)\\s*;").matcher(src);
        assertTrue(shard.find(), "ThroughWalls needs a shard that sets depthFunc(GL_ALWAYS) on setup and"
                + " depthFunc(GL_LEQUAL) on clear: NO_DEPTH_TEST alone is depth tested after particles");
        final String chain = statement(src, "static final RenderType XRAY");
        assertNotNull(chain, "ThroughWalls builds no XRAY type");
        assertTrue(chain.contains(".setLayeringState(Shards." + shard.group(1) + ")"),
                "the XRAY type must carry the depth shard, or the outlines are buried again: " + chain);
        assertTrue(chain.contains(".setWriteMaskState(Shards.COLOUR_ONLY)"),
                "a type that passes every fragment must write no depth, or a ghost line hides the world behind it");
    }

    @Test
    @DisplayName("always() forces ALWAYS around its draws and restores LEQUAL even when they throw")
    void alwaysRestoresInAFinally() {
        final String body = body(code(read(SRC)), "public static void always(Runnable draw)");
        assertNotNull(body, "ThroughWalls.always(Runnable) is gone: vanilla's see-through text draws buried");
        final int set = body.indexOf("RenderSystem.depthFunc(GL11.GL_ALWAYS)");
        final int run = body.indexOf("draw.run()");
        final int fin = body.indexOf("finally");
        final int back = body.indexOf("RenderSystem.depthFunc(GL11.GL_LEQUAL)");
        assertTrue(set >= 0 && run > set, "always() must force GL_ALWAYS before it runs the draws: " + body);
        assertTrue(fin > run && back > fin, "always() must put GL_LEQUAL back in a finally, or one failed draw leaves"
                + " the whole frame's depth test passing everything: " + body);
    }

    private static String statement(String code, String head) {
        final int at = code.indexOf(head);
        if (at < 0) {
            return null;
        }
        int depth = 0;
        for (int i = at; i < code.length(); i++) {
            final char c = code.charAt(i);
            if (c == '"' || c == '\'') {
                i = skipLiteral(code, i, c);
            } else if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
            } else if (c == ';' && depth == 0) {
                return code.substring(at, i + 1);
            }
        }
        return null;
    }

    private static String body(String code, String signature) {
        final int at = code.indexOf(signature);
        if (at < 0) {
            return null;
        }
        final int open = code.indexOf('{', at);
        int depth = 0;
        for (int i = open; i < code.length(); i++) {
            final char c = code.charAt(i);
            if (c == '"' || c == '\'') {
                i = skipLiteral(code, i, c);
            } else if (c == '{') {
                depth++;
            } else if (c == '}' && --depth == 0) {
                return code.substring(open + 1, i);
            }
        }
        return null;
    }

    private static String code(String s) {
        final StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            final char c = s.charAt(i);
            if (c == '"' || c == '\'') {
                final int close = skipLiteral(s, i, c);
                out.append(s, i, Math.min(s.length(), close + 1));
                i = close;
            } else if (c == '/' && i + 1 < s.length() && s.charAt(i + 1) == '/') {
                final int nl = s.indexOf('\n', i);
                i = nl < 0 ? s.length() : nl - 1;
            } else if (c == '/' && i + 1 < s.length() && s.charAt(i + 1) == '*') {
                final int end = s.indexOf("*/", i + 2);
                i = end < 0 ? s.length() : end + 1;
                out.append(' ');
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    private static int skipLiteral(String s, int start, char quote) {
        for (int i = start + 1; i < s.length(); i++) {
            final char c = s.charAt(i);
            if (c == '\\') {
                i++;
            } else if (c == quote) {
                return i;
            }
        }
        return s.length();
    }

    private static String read(Path p) {
        try {
            return Files.readString(p, StandardCharsets.UTF_8).replace("\r\n", "\n");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
