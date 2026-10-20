package com.createstats.book.layout;

public interface BookText {
    String get(String key, Object... args);

    default String joined(String key, Object... args) {
        return get(key, args).replace('\n', ' ');
    }

    String itemName(String registryId);
}
