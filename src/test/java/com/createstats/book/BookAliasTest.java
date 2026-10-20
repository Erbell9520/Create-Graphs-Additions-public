package com.createstats.book;

import com.mojang.serialization.Lifecycle;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BookAliasTest {
    private static String read(String path) throws IOException {
        final String s = Files.readString(Path.of(path), StandardCharsets.UTF_8).replace("\r\n", "\n");
        return s.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("//[^\n]*", "");
    }

    @Test
    void theAliasIsWired() throws IOException {
        final String mod = read("src/main/java/com/createstats/CreateStats.java");
        assertTrue(Pattern.compile("\\bBookAliases\\.register\\(\\s*modEventBus\\s*\\)\\s*;").matcher(mod).find(),
                "CreateStats must register BookAliases on the mod event bus, or the alias is never applied");

        final String aliases = read("src/main/java/com/createstats/book/BookAliases.java");
        assertTrue(aliases.contains("ITEMS.addAlias(FACTORY_MANUAL, STUDENTS_MANUAL);"),
                "BookAliases must alias FACTORY_MANUAL to STUDENTS_MANUAL");
        assertTrue(aliases.contains("ITEMS.register(modEventBus);"),
                "BookAliases.register must put its DeferredRegister on the bus, or RegisterEvent never applies it");
        assertTrue(aliases.contains("fromNamespaceAndPath(\"create_stats\", \"factory_manual\")"),
                "FACTORY_MANUAL must be create_stats:factory_manual");
        assertTrue(aliases.contains("fromNamespaceAndPath(\"create_stats\", \"students_manual\")"),
                "STUDENTS_MANUAL must be create_stats:students_manual");
        assertTrue(aliases.contains("DeferredRegister.create(Registries.ITEM,"),
                "the alias must sit on an item DeferredRegister, so it lands in the frozen snapshot");

        final String items = read("src/main/java/com/createstats/registry/ModItems.java");
        assertTrue(items.contains(".item(\"students_manual\", StudentsManualItem::new)"),
                "ModItems must register students_manual");
        assertFalse(items.contains("\"factory_manual\""),
                "factory_manual must not be registered as well: an alias only applies to an id with no entry");
    }

    @Test
    void anAliasedIdFindsTheNewEntry() {
        final ResourceKey<net.minecraft.core.Registry<String>> key =
                ResourceKey.createRegistryKey(ResourceLocation.fromNamespaceAndPath("create_stats", "book_alias_test"));
        final MappedRegistry<String> registry = new MappedRegistry<>(key, Lifecycle.stable());
        registry.register(ResourceKey.create(key, BookAliases.STUDENTS_MANUAL), "the book", RegistrationInfo.BUILT_IN);
        registry.addAlias(BookAliases.FACTORY_MANUAL, BookAliases.STUDENTS_MANUAL);
        assertEquals("the book", registry.get(BookAliases.FACTORY_MANUAL));
        assertEquals("the book", registry.get(ResourceKey.create(key, BookAliases.FACTORY_MANUAL)));
        assertTrue(registry.containsKey(BookAliases.STUDENTS_MANUAL));
    }
}
