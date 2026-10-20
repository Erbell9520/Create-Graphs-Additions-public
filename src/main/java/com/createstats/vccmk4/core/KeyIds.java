package com.createstats.vccmk4.core;

// one int per slot, same meaning on client and server
public final class KeyIds {
    public static final int NONE = 0;
    public static final int KEY_BASE = 1000;
    public static final int MOUSE_BASE = 2000;

    public static final int FIRST_KEY = 32;
    public static final int LAST_KEY = 348;

    public static final int FIRST_MOUSE = 2;
    public static final int LAST_MOUSE = 7;

    private KeyIds() {
    }

    public static boolean isBinding(int id) {
        return id >= 1 && id <= Channel.LAST_ID;
    }

    public static boolean isKey(int id) {
        return id >= KEY_BASE + FIRST_KEY && id <= KEY_BASE + LAST_KEY;
    }

    public static boolean isMouse(int id) {
        return id >= MOUSE_BASE + FIRST_MOUSE && id <= MOUSE_BASE + LAST_MOUSE;
    }

    public static boolean valid(int id) {
        return id == NONE || isBinding(id) || isKey(id) || isMouse(id);
    }

    public static int sane(int id) {
        return valid(id) ? id : NONE;
    }

    public static int key(int glfwKey) {
        int id = KEY_BASE + glfwKey;
        return isKey(id) ? id : NONE;
    }

    public static int mouse(int button) {
        int id = MOUSE_BASE + button;
        return isMouse(id) ? id : NONE;
    }

    public static int glfwKey(int id) {
        return id - KEY_BASE;
    }

    public static int mouseButton(int id) {
        return id - MOUSE_BASE;
    }
}
