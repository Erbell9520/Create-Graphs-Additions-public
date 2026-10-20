package com.createstats.vccmk4.client;

import com.createstats.CreateStats;
import com.createstats.vccmk4.core.Channel;
import com.createstats.vccmk4.core.KeyIds;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

@EventBusSubscriber(modid = CreateStats.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class Vcc4Keys {
    private static final String CATEGORY = "key.categories.create_stats";

    public static final KeyMapping AUX1 = new KeyMapping("key.create_stats.vcc4_aux1",
            KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Z, CATEGORY);
    public static final KeyMapping AUX2 = new KeyMapping("key.create_stats.vcc4_aux2",
            KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_X, CATEGORY);
    public static final KeyMapping AUX3 = new KeyMapping("key.create_stats.vcc4_aux3",
            KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_C, CATEGORY);

    private Vcc4Keys() {
    }

    @SubscribeEvent
    public static void register(RegisterKeyMappingsEvent event) {
        event.register(AUX1);
        event.register(AUX2);
        event.register(AUX3);
    }

    @Nullable
    public static KeyMapping mapping(Channel channel) {
        Minecraft mc = Minecraft.getInstance();
        return switch (channel) {
            case FORWARD -> mc.options.keyUp;
            case BACK -> mc.options.keyDown;
            case LEFT -> mc.options.keyLeft;
            case RIGHT -> mc.options.keyRight;
            case JUMP -> mc.options.keyJump;
            case SPRINT -> mc.options.keySprint;
            case AUX1 -> AUX1;
            case AUX2 -> AUX2;
            case AUX3 -> AUX3;
            case NONE -> null;
        };
    }

    @Nullable
    public static InputConstants.Key physical(int id) {
        if (KeyIds.isBinding(id)) {
            KeyMapping m = mapping(Channel.byId(id));
            return m == null || m.isUnbound() ? null : m.getKey();
        }
        if (KeyIds.isKey(id)) return InputConstants.Type.KEYSYM.getOrCreate(KeyIds.glfwKey(id));
        if (KeyIds.isMouse(id)) return InputConstants.Type.MOUSE.getOrCreate(KeyIds.mouseButton(id));
        return null;
    }

    public static boolean isHeld(int id) {
        InputConstants.Key key = physical(id);
        if (key == null) return false;
        long window = Minecraft.getInstance().getWindow().getWindow();
        return switch (key.getType()) {
            case KEYSYM -> InputConstants.isKeyDown(window, key.getValue());
            case MOUSE -> GLFW.glfwGetMouseButton(window, key.getValue()) == GLFW.GLFW_PRESS;

            case SCANCODE -> KeyIds.isBinding(id) && mapping(Channel.byId(id)) != null && mapping(Channel.byId(id)).isDown();
        };
    }

    public static String name(int id) {
        InputConstants.Key key = physical(id);
        return key == null ? "" : key.getDisplayName().getString();
    }

    public static int resolve(int glfwKey, int scanCode) {
        for (Channel c : Channel.values()) {
            KeyMapping m = mapping(c);
            if (m != null && !m.isUnbound() && m.matches(glfwKey, scanCode)) return c.id();
        }
        return KeyIds.key(glfwKey);
    }

    public static int resolveMouse(int button) {
        for (Channel c : Channel.values()) {
            KeyMapping m = mapping(c);
            if (m != null && !m.isUnbound() && m.matchesMouse(button)) return c.id();
        }
        return KeyIds.mouse(button);
    }

    public static boolean leavesSeat(int id) {
        InputConstants.Key key = physical(id);
        KeyMapping sneak = Minecraft.getInstance().options.keyShift;
        return key != null && !sneak.isUnbound() && key.equals(sneak.getKey());
    }

    public static List<String> alsoBound(int id) {
        List<String> out = new ArrayList<>();
        InputConstants.Key key = physical(id);
        if (key == null) return out;
        KeyMapping own = KeyIds.isBinding(id) ? mapping(Channel.byId(id)) : null;
        for (KeyMapping m : Minecraft.getInstance().options.keyMappings) {
            if (m == own || m == AUX1 || m == AUX2 || m == AUX3 || m.isUnbound() || !key.equals(m.getKey())) continue;
            if (m.getKeyConflictContext() == KeyConflictContext.GUI) continue;
            String name = Component.translatable(m.getName()).getString();
            if (!out.contains(name)) out.add(name);
        }
        return out;
    }
}
