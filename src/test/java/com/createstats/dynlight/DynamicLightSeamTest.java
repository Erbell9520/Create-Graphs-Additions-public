package com.createstats.dynlight;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DynamicLightSeamTest {
    private static final String BLOCK_STATE = "net/minecraft/world/level/block/state/BlockState";
    private static final String EMISSION = "getLightEmission";
    private static final String EMISSION_DESC = "(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)I";
    private static final String BLOCK_LIGHT_ENGINE = "net/minecraft/world/level/lighting/BlockLightEngine";
    private static final String GET_EMISSION_DESC = "(JLnet/minecraft/world/level/block/state/BlockState;)I";
    private static final String PACKET_LISTENER = "net/minecraft/client/multiplayer/ClientPacketListener";
    private static final String APPLY_LIGHT_DATA_DESC =
            "(IILnet/minecraft/network/protocol/game/ClientboundLightUpdatePacketData;)V";
    private static final String READ_SECTION_LIST_DESC = "(IILnet/minecraft/world/level/lighting/LevelLightEngine;"
            + "Lnet/minecraft/world/level/LightLayer;Ljava/util/BitSet;Ljava/util/BitSet;Ljava/util/Iterator;)V";
    private static final Path MIXINS = Path.of("src/main/java/com/createstats/mixin");

    private static ClassNode classNode(String internalName) throws IOException {
        try (InputStream in = DynamicLightSeamTest.class.getClassLoader().getResourceAsStream(internalName + ".class")) {
            assertNotNull(in, internalName + " is not on the test classpath");
            final ClassNode node = new ClassNode();
            new ClassReader(in).accept(node, ClassReader.SKIP_FRAMES);
            return node;
        }
    }

    private static MethodNode method(ClassNode owner, String name, String desc) {
        for (MethodNode m : owner.methods) {
            if (m.name.equals(name) && m.desc.equals(desc)) {
                return m;
            }
        }
        throw new AssertionError(owner.name + "." + name + desc + " no longer exists");
    }

    private static boolean calls(MethodNode method, String owner, String name, String desc) {
        for (AbstractInsnNode insn : method.instructions) {
            if (insn instanceof MethodInsnNode call && call.owner.equals(owner) && call.name.equals(name)
                    && call.desc.equals(desc)) {
                return true;
            }
        }
        return false;
    }

    @Test
    void getEmissionAsksTheBlockThroughTheCallTheHookWraps() throws IOException {
        final MethodNode getEmission = method(classNode(BLOCK_LIGHT_ENGINE), "getEmission", GET_EMISSION_DESC);
        assertTrue(calls(getEmission, BLOCK_STATE, EMISSION, EMISSION_DESC),
                "NeoForge's level-aware emission call is what BlockLightEngineMixin wraps");
        assertFalse(calls(getEmission, BLOCK_STATE, EMISSION, "()I"),
                "vanilla's no-argument call is patched out; if it is back, the hook wraps a call that never runs");
    }

    @Test
    void aLightTurningOnAndALightGoingOutBothReadEmissionThroughGetEmission() throws IOException {
        final ClassNode engine = classNode(BLOCK_LIGHT_ENGINE);
        assertTrue(calls(method(engine, "checkNode", "(J)V"), BLOCK_LIGHT_ENGINE, "getEmission", GET_EMISSION_DESC),
                "checkBlock's node check, which a light turning on or off starts");
        assertTrue(calls(method(engine, "propagateDecrease", "(JJ)V"), BLOCK_LIGHT_ENGINE, "getEmission",
                GET_EMISSION_DESC), "a decrease re-reads emission as it passes, so a dimmed cell is re-seeded");
    }

    @Test
    void theServerLightHookFollowsTheMethodThatReplacesSectionLight() throws IOException {
        final MethodNode apply = method(classNode(PACKET_LISTENER), "applyLightData", APPLY_LIGHT_DATA_DESC);
        assertTrue(calls(apply, PACKET_LISTENER, "readSectionList", READ_SECTION_LIST_DESC));
        final MethodNode read = method(classNode(PACKET_LISTENER), "readSectionList", READ_SECTION_LIST_DESC);
        assertTrue(calls(read, "net/minecraft/world/level/lighting/LevelLightEngine", "queueSectionData",
                "(Lnet/minecraft/world/level/LightLayer;Lnet/minecraft/core/SectionPos;"
                        + "Lnet/minecraft/world/level/chunk/DataLayer;)V"),
                "the server's arrays replace the client's here, wiping dynamic light");
    }

    @Test
    void theAccessorsFieldIsTheClientsBlockLightEngine() throws IOException {
        final ClassNode engine = classNode("net/minecraft/world/level/lighting/LevelLightEngine");
        FieldNode blockEngine = null;
        for (FieldNode f : engine.fields) {
            if (f.name.equals("blockEngine")) {
                blockEngine = f;
            }
        }
        assertNotNull(blockEngine, "LevelLightEngine.blockEngine is gone");
        assertEquals("Lnet/minecraft/world/level/lighting/LightEngine;", blockEngine.desc);
    }

    @Test
    void theMixinsNameExactlyWhatThisTestChecked() throws IOException {
        final String emission = Files.readString(MIXINS.resolve("BlockLightEngineMixin.java"));
        assertTrue(emission.contains("method = \"getEmission\""));
        assertTrue(emission.contains("target = \"L" + BLOCK_STATE + ";" + EMISSION + EMISSION_DESC + "\""),
                "the INVOKE target must be the call checked above, character for character");
        final String packets = Files.readString(MIXINS.resolve("ClientPacketListenerLightMixin.java"));
        assertTrue(packets.contains("method = \"applyLightData\""));
        final String accessor = Files.readString(MIXINS.resolve("LevelLightEngineAccessor.java"));
        assertTrue(accessor.contains("@Accessor(\"blockEngine\")"));
    }
}
