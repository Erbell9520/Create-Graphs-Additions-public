package com.createstats.vccmk4.console;

import java.util.List;
import java.util.UUID;

public interface ConsoleHost {
    int width(String text);

    String tr(String key, Object... args);

    String name(String idOrName);

    String keyName(int keyId);

    List<String> keyAlso(int keyId);

    boolean keyLeavesSeat(int keyId);

    int resolveKey(int glfwKey, int scanCode);

    int resolveMouse(int button);

    void send(int action, int a, int b, long target);

    default void sendOrder(int op, UUID console, UUID pathway, int a) {
    }

    void click();
}
