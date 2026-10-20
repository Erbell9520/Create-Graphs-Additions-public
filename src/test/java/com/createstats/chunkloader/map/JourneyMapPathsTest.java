package com.createstats.chunkloader.map;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JourneyMapPathsTest {
    private static final Path GAME = Path.of("C:/game");

    @Test
    @DisplayName("single player: journeymap/data/sp/<save folder>, the legacy safe name only when it alone exists")
    void singlePlayer() {
        assertEquals(List.of(GAME.resolve("journeymap/data/sp/Manual Alias Test")),
                JourneyMapPaths.worldDirs(GAME, true, "Manual Alias Test", "", p -> false, List.of()));
        final Path legacy = GAME.resolve("journeymap/data/sp/Bob~s World~");
        assertEquals(List.of(legacy), JourneyMapPaths.worldDirs(GAME, true, "Bob's World!", "", legacy::equals, List.of()));
        final Path current = GAME.resolve("journeymap/data/sp/Bob's World!");
        assertEquals(List.of(current), JourneyMapPaths.worldDirs(GAME, true, "Bob's World!", "",
                p -> p.equals(current) || p.equals(legacy), List.of()), "the current folder wins when both exist");
    }

    @Test
    @DisplayName("multiplayer: the server entry's name with non-word runs made ~, its address when that leaves nothing")
    void serverName() {
        assertEquals("offline", JourneyMapPaths.serverFolder(null, null));
        assertEquals("My~Server", JourneyMapPaths.serverFolder("My Server", "1.2.3.4"));
        assertEquals("play~example~com", JourneyMapPaths.serverFolder("!!!", "play.example.com"));
        assertEquals("Mod~Test", JourneyMapPaths.serverFolder("Mod Test", null));
    }

    @Test
    @DisplayName("multiplayer: the bare folder first, then every folder with a world id suffix, newest first")
    void multiplayerCandidates() {
        final List<JourneyMapPaths.Folder> folders = List.of(new JourneyMapPaths.Folder("My~Server_abc", 10),
                new JourneyMapPaths.Folder("My~Server_def", 20), new JourneyMapPaths.Folder("Other", 30),
                new JourneyMapPaths.Folder("My~ServerX", 40));
        assertEquals(List.of(GAME.resolve("journeymap/data/mp/My~Server"), GAME.resolve("journeymap/data/mp/My~Server_def"),
                GAME.resolve("journeymap/data/mp/My~Server_abc")),
                JourneyMapPaths.worldDirs(GAME, false, "", "My~Server", p -> true, folders));
    }

    @Test
    @DisplayName("dimension folders: the legacy DIM names when present, a vanilla path, a modded id made safe")
    void dimensionFolders() {
        assertEquals("overworld", JourneyMapPaths.dimensionFolder("minecraft:overworld", n -> false));
        assertEquals("DIM0", JourneyMapPaths.dimensionFolder("minecraft:overworld", Set.of("DIM0")::contains));
        assertEquals("the_nether", JourneyMapPaths.dimensionFolder("minecraft:the_nether", n -> false));
        assertEquals("DIM-1", JourneyMapPaths.dimensionFolder("minecraft:the_nether", Set.of("DIM-1")::contains));
        assertEquals("DIM1", JourneyMapPaths.dimensionFolder("minecraft:the_end", Set.of("DIM1")::contains));
        assertEquals("the_end", JourneyMapPaths.dimensionFolder("minecraft:the_end", Set.of("DIM0")::contains));
        assertEquals("aether~the_aether", JourneyMapPaths.dimensionFolder("aether:the_aether", n -> true));
        assertEquals("twilightforest~twilight_forest",
                JourneyMapPaths.dimensionFolder("twilightforest:twilight_forest", n -> false));
    }

    @Test
    @DisplayName("the day tile is <dimension>/day/<rx>,<rz>.png")
    void dayTile() {
        assertEquals(GAME.resolve("w/overworld/day/0,-1.png"),
                JourneyMapPaths.dayTile(GAME.resolve("w"), "overworld", 0, -1));
    }

    @Test
    @DisplayName("E1 on the owner's profile: Manual Alias Test's region 0, -1 and its eight neighbours resolve to files")
    void ownersProfile() {
        final Path game = Path.of(System.getProperty("user.home"), "AppData/Roaming/ModrinthApp/profiles/Mod Test");
        Assumptions.assumeTrue(Files.isDirectory(game.resolve("journeymap/data/sp/Manual Alias Test")),
                "only on the owner's machine");
        final Path world = JourneyMapPaths.worldDirs(game, true, "Manual Alias Test", "", Files::isDirectory, List.of())
                .get(0);
        final String dim = JourneyMapPaths.dimensionFolder("minecraft:overworld", n -> Files.isDirectory(world.resolve(n)));
        for (int rx = -1; rx <= 1; rx++) {
            for (int rz = -2; rz <= 0; rz++) {
                assertTrue(Files.isRegularFile(JourneyMapPaths.dayTile(world, dim, rx, rz)), rx + "," + rz);
            }
        }
    }
}
