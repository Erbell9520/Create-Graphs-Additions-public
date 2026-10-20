package com.createstats.data;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.CompoundTag;
import java.util.Collections;
import java.util.UUID;
import java.util.Iterator;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.List;

public class AnnotationManager
{
    public static final int MAX_ANNOTATIONS = 64;
    private final List<Annotation> annotations;

    public AnnotationManager() {
        this.annotations = new ArrayList<Annotation>();
    }

    public boolean add(Annotation annotation) {
        if (this.annotations.size() >= 64) {
            return false;
        }
        for (Annotation a : this.annotations) {
            if (a.getTickStamp() == annotation.getTickStamp() && a.getLabel().equalsIgnoreCase(annotation.getLabel())) {
                return false;
            }
        }
        this.annotations.add(annotation);
        this.annotations.sort(Comparator.comparingLong(Annotation::getTickStamp));
        return true;
    }

    public boolean remove(UUID id) {
        return this.annotations.removeIf(a -> a.getId().equals(id));
    }

    public boolean edit(UUID id, String newLabel, int newColor) {
        for (Annotation a : this.annotations) {
            if (a.getId().equals(id)) {
                String trimmed = newLabel.trim();
                if (trimmed.isEmpty()) {
                    return false;
                }
                if (trimmed.length() > 48) {
                    trimmed = trimmed.substring(0, 48);
                }
                a.setLabel(trimmed);
                a.setColor(newColor);
                return true;
            }
        }
        return false;
    }

    public Annotation getById(UUID id) {
        for (Annotation a : this.annotations) {
            if (a.getId().equals(id)) {
                return a;
            }
        }
        return null;
    }

    public List<Annotation> getAll() {
        return Collections.unmodifiableList((List<? extends Annotation>)this.annotations);
    }

    public List<Annotation> getInRange(long startTick, long endTick) {
        final List<Annotation> result = new ArrayList<Annotation>();
        for (Annotation a : this.annotations) {
            if (a.getType() == Annotation.AnnotationType.REGION) {
                if (a.getTickStamp() > endTick || a.getEndTickStamp() < startTick) {
                    continue;
                }
                result.add(a);
            }
            else {
                if (a.getTickStamp() < startTick || a.getTickStamp() > endTick) {
                    continue;
                }
                result.add(a);
            }
        }
        return result;
    }

    public int size() {
        return this.annotations.size();
    }

    public void clear() {
        this.annotations.clear();
    }

    public CompoundTag save() {
        final CompoundTag tag = new CompoundTag();
        final ListTag list = new ListTag();
        for (Annotation a : this.annotations) {
            list.add(a.save());
        }
        tag.put("List", (Tag)list);
        return tag;
    }

    public static AnnotationManager load(CompoundTag tag) {
        final AnnotationManager mgr = new AnnotationManager();
        if (tag.contains("List", 9)) {
            final ListTag list = tag.getList("List", 10);
            for (int i = 0; i < list.size(); ++i) {
                mgr.annotations.add(Annotation.load(list.getCompound(i)));
            }
            mgr.annotations.sort(Comparator.comparingLong(Annotation::getTickStamp));
        }
        return mgr;
    }

    public void writeToBuf(FriendlyByteBuf buf) {
        buf.writeVarInt(this.annotations.size());
        for (Annotation a : this.annotations) {
            a.writeToBuf(buf);
        }
    }

    public static AnnotationManager readFromBuf(FriendlyByteBuf buf) {
        final AnnotationManager mgr = new AnnotationManager();
        for (int count = buf.readVarInt(), i = 0; i < count; ++i) {
            mgr.annotations.add(Annotation.readFromBuf(buf));
        }
        mgr.annotations.sort(Comparator.comparingLong(Annotation::getTickStamp));
        return mgr;
    }
}
