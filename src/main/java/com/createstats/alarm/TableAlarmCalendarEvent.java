package com.createstats.alarm;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

public class TableAlarmCalendarEvent {

    public static final int MAX_TITLE_LENGTH = 48;
    public static final int MINUTES_PER_DAY = 24 * 60;
    public static final Comparator<TableAlarmCalendarEvent> SORT_ORDER = Comparator
        .comparing(TableAlarmCalendarEvent::getDate)
        .thenComparingInt(TableAlarmCalendarEvent::getMinuteOfDay)
        .thenComparing(TableAlarmCalendarEvent::getTitle, String.CASE_INSENSITIVE_ORDER);

    private final UUID id;
    private final LocalDate date;
    private final int minuteOfDay;
    private final String title;
    private final String invitedBy;
    private final boolean invitation;

    public TableAlarmCalendarEvent(UUID id, LocalDate date, int minuteOfDay, String title) {
        this(id, date, minuteOfDay, title, "", false);
    }

    public TableAlarmCalendarEvent(UUID id, LocalDate date, int minuteOfDay, String title, String invitedBy, boolean invitation) {
        this.id = id == null ? UUID.randomUUID() : id;
        this.date = date == null ? LocalDate.now() : date;
        this.minuteOfDay = Math.floorMod(minuteOfDay, MINUTES_PER_DAY);
        this.title = sanitizeTitle(title);
        this.invitedBy = sanitizeInvitedBy(invitedBy);
        this.invitation = invitation;
    }

    public UUID getId() {
        return this.id;
    }

    public LocalDate getDate() {
        return this.date;
    }

    public int getMinuteOfDay() {
        return this.minuteOfDay;
    }

    public String getTitle() {
        return this.title;
    }

    public String getInvitedBy() {
        return this.invitedBy;
    }

    public boolean isInvitation() {
        return this.invitation;
    }

    public TableAlarmCalendarEvent asInvitationFrom(String senderName) {
        return new TableAlarmCalendarEvent(UUID.randomUUID(), this.date, this.minuteOfDay, this.title, senderName, true);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Id", this.id);
        tag.putInt("Year", this.date.getYear());
        tag.putInt("Month", this.date.getMonthValue());
        tag.putInt("Day", this.date.getDayOfMonth());
        tag.putInt("MinuteOfDay", this.minuteOfDay);
        tag.putString("Title", this.title);
        tag.putString("InvitedBy", this.invitedBy);
        tag.putBoolean("Invitation", this.invitation);
        return tag;
    }

    @Nullable
    public static TableAlarmCalendarEvent load(CompoundTag tag) {
        if (tag == null || !tag.hasUUID("Id")) {
            return null;
        }
        try {
            LocalDate date = LocalDate.of(tag.getInt("Year"), tag.getInt("Month"), tag.getInt("Day"));
            return new TableAlarmCalendarEvent(tag.getUUID("Id"), date, tag.getInt("MinuteOfDay"), tag.getString("Title"), tag.getString("InvitedBy"), tag.getBoolean("Invitation"));
        } catch (DateTimeException ignored) {
            return null;
        }
    }

    public static ListTag saveListTag(List<TableAlarmCalendarEvent> events) {
        ListTag listTag = new ListTag();
        if (events == null) {
            return listTag;
        }
        for (TableAlarmCalendarEvent event : events) {
            if (event != null) {
                listTag.add(event.save());
            }
        }
        return listTag;
    }

    public static List<TableAlarmCalendarEvent> loadListTag(ListTag listTag) {
        List<TableAlarmCalendarEvent> events = new ArrayList<>();
        if (listTag == null) {
            return events;
        }
        for (int index = 0; index < listTag.size(); ++index) {
            if (listTag.get(index).getId() != Tag.TAG_COMPOUND) {
                continue;
            }
            TableAlarmCalendarEvent event = load(listTag.getCompound(index));
            if (event != null) {
                events.add(event);
            }
        }
        events.sort(SORT_ORDER);
        return events;
    }

    public static CompoundTag savePacketTag(List<TableAlarmCalendarEvent> events) {
        CompoundTag tag = new CompoundTag();
        tag.put("Events", saveListTag(events));
        return tag;
    }

    public static List<TableAlarmCalendarEvent> loadPacketTag(CompoundTag tag) {
        if (tag == null) {
            return new ArrayList<>();
        }
        return loadListTag(tag.getList("Events", Tag.TAG_COMPOUND));
    }

    public static String sanitizeTitle(String title) {
        String cleaned = title == null ? "" : title.trim();
        if (cleaned.isEmpty()) {
            cleaned = "Event";
        }
        return cleaned.length() > MAX_TITLE_LENGTH ? cleaned.substring(0, MAX_TITLE_LENGTH) : cleaned;
    }

    private static String sanitizeInvitedBy(String invitedBy) {
        String cleaned = invitedBy == null ? "" : invitedBy.trim();
        return cleaned.length() > 32 ? cleaned.substring(0, 32) : cleaned;
    }
}