package com.createstats.support;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

public final class SupportLogTail {
    private SupportLogTail() {
    }

    public record Result(List<String> notes, List<String> matches) {
    }

    public static Result read(Path log, long windowBytes, int limit, String... markers) throws IOException {
        long start;
        String text;
        try (SeekableByteChannel channel = Files.newByteChannel(log, StandardOpenOption.READ)) {
            long size = channel.size();
            start = Math.max(0, size - windowBytes);
            ByteBuffer buffer = ByteBuffer.allocate((int) (size - start));
            channel.position(start);
            while (buffer.hasRemaining() && channel.read(buffer) > 0) {
            }
            text = new String(buffer.array(), 0, buffer.position(), StandardCharsets.UTF_8);
        }

        String[] lines = text.split("\r?\n");

        int first = start > 0 && lines.length > 0 ? 1 : 0;
        List<String> matched = new ArrayList<>();
        for (int i = first; i < lines.length; i++) {
            if (relevant(lines[i], markers) && severe(lines[i])) {
                matched.add(lines[i]);
            }
        }

        List<String> notes = new ArrayList<>();
        if (start > 0) {
            notes.add("(searched the last " + (windowBytes >= 1 << 20 ? (windowBytes >> 20) + " MB" : windowBytes + " bytes")
                + " of the log)");
        }
        int from = Math.max(0, matched.size() - limit);
        if (from > 0) {
            notes.add("... " + from + " earlier entries omitted ...");
        }
        return new Result(notes, List.copyOf(matched.subList(from, matched.size())));
    }

    private static boolean relevant(String line, String[] markers) {
        for (String marker : markers) {
            if (line.contains(marker)) {
                return true;
            }
        }
        return false;
    }

    private static boolean severe(String line) {
        return line.contains("/ERROR") || line.contains("/WARN") || line.contains("Exception")
            || line.contains("\tat com.createstats");
    }
}
