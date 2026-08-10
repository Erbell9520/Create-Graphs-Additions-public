package com.createstats.alarm;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

public class TableAlarmCalendarSavedData extends SavedData {

    private static final String DATA_NAME = "create_stats_table_alarm_calendar";
    private static final int MAX_EVENTS_PER_PLAYER = 64;

    private final Map<UUID, List<TableAlarmCalendarEvent>> playerEvents;

    public TableAlarmCalendarSavedData() {
        this.playerEvents = new LinkedHashMap<>();
    }

    public static TableAlarmCalendarSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
            new SavedData.Factory<>(TableAlarmCalendarSavedData::new, TableAlarmCalendarSavedData::load), DATA_NAME);
    }

    private static TableAlarmCalendarSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        TableAlarmCalendarSavedData data = new TableAlarmCalendarSavedData();
        ListTag playersTag = tag.getList("Players", Tag.TAG_COMPOUND);
        for (int index = 0; index < playersTag.size(); ++index) {
            CompoundTag playerTag = playersTag.getCompound(index);
            if (!playerTag.hasUUID("PlayerId")) {
                continue;
            }
            UUID playerId = playerTag.getUUID("PlayerId");
            data.playerEvents.put(playerId, sanitizeEvents(TableAlarmCalendarEvent.loadListTag(playerTag.getList("Events", Tag.TAG_COMPOUND))));
        }
        return data;
    }

    public List<TableAlarmCalendarEvent> getEvents(UUID playerId) {
        return new ArrayList<>(this.playerEvents.getOrDefault(playerId, List.of()));
    }

    public void replaceEvents(UUID playerId, List<TableAlarmCalendarEvent> events) {
        List<TableAlarmCalendarEvent> sanitizedEvents = sanitizeEvents(events);
        if (sanitizedEvents.isEmpty()) {
            this.playerEvents.remove(playerId);
        } else {
            this.playerEvents.put(playerId, sanitizedEvents);
        }
        this.setDirty();
    }

    public void addEvent(UUID playerId, TableAlarmCalendarEvent event) {
        List<TableAlarmCalendarEvent> events = this.getEvents(playerId);
        if (event != null) {
            events.add(event);
        }
        this.replaceEvents(playerId, events);
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag playersTag = new ListTag();
        for (Map.Entry<UUID, List<TableAlarmCalendarEvent>> entry : this.playerEvents.entrySet()) {
            CompoundTag playerTag = new CompoundTag();
            playerTag.putUUID("PlayerId", entry.getKey());
            playerTag.put("Events", TableAlarmCalendarEvent.saveListTag(entry.getValue()));
            playersTag.add(playerTag);
        }
        tag.put("Players", playersTag);
        return tag;
    }

    private static List<TableAlarmCalendarEvent> sanitizeEvents(List<TableAlarmCalendarEvent> events) {
        Map<UUID, TableAlarmCalendarEvent> dedupedEvents = new LinkedHashMap<>();
        if (events != null) {
            for (TableAlarmCalendarEvent event : events) {
                if (event != null) {
                    dedupedEvents.put(event.getId(), event);
                }
            }
        }
        List<TableAlarmCalendarEvent> sanitizedEvents = new ArrayList<>(dedupedEvents.values());
        sanitizedEvents.sort(TableAlarmCalendarEvent.SORT_ORDER);
        if (sanitizedEvents.size() > MAX_EVENTS_PER_PLAYER) {
            return new ArrayList<>(sanitizedEvents.subList(0, MAX_EVENTS_PER_PLAYER));
        }
        return sanitizedEvents;
    }
}