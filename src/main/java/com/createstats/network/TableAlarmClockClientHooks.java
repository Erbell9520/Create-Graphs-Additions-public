package com.createstats.network;

import com.createstats.alarm.TableAlarmCalendarEvent;
import java.lang.reflect.Method;
import java.util.List;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;

public final class TableAlarmClockClientHooks {

    private static final String CLIENT_PACKET_HANDLER = "com.createstats.client.ClientPacketHandler";

    private TableAlarmClockClientHooks() {
    }

    public static void handleCalendarData(List<TableAlarmCalendarEvent> events) {
        invoke("handleTableAlarmCalendarData", new Class<?>[]{List.class}, events);
    }

    private static void invoke(String methodName, Class<?>[] parameterTypes, Object... args) {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return;
        }
        try {
            Class<?> helperClass = Class.forName(CLIENT_PACKET_HANDLER);
            Method method = helperClass.getMethod(methodName, parameterTypes);
            method.invoke(null, args);
        } catch (ClassNotFoundException | NoSuchMethodException ignored) {
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to invoke table alarm clock client hook " + methodName, e);
        }
    }
}