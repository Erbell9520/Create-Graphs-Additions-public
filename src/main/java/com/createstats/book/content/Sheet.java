package com.createstats.book.content;

import java.util.ArrayList;
import java.util.List;

public record Sheet(String id, Section section, String groupKey, String titleKey, List<String> ids,
                   List<String> also, String iconId, String hookKey, boolean opensLeft, List<Part> parts) {
    public Sheet {
        ids = List.copyOf(ids);
        also = List.copyOf(also);
        parts = List.copyOf(parts);
    }

    public String icon() {
        return iconId != null ? iconId : ids.isEmpty() ? null : ids.get(0);
    }

    public boolean covers(String registryId) {
        return ids.contains(registryId);
    }

    public boolean explains(String registryId) {
        return ids.contains(registryId) || also.contains(registryId);
    }

    public boolean coversBlocks() {
        return !ids.isEmpty();
    }

    public static Builder of(String id, Section section) {
        return new Builder(id, section);
    }

    public static final class Builder {
        private final String id;
        private final Section section;
        private String groupKey;
        private String titleKey;
        private final List<String> ids = new ArrayList<>();
        private final List<String> also = new ArrayList<>();
        private String iconId;
        private String hookKey;
        private boolean opensLeft = true;
        private final List<Part> parts = new ArrayList<>();

        private Builder(String id, Section section) {
            this.id = id;
            this.section = section;
        }

        public Builder group(String key) {
            this.groupKey = key;
            return this;
        }

        public Builder title(String key) {
            this.titleKey = key;
            return this;
        }

        public Builder covers(String... registryIds) {
            this.ids.addAll(List.of(registryIds));
            return this;
        }

        public Builder also(String... registryIds) {
            this.also.addAll(List.of(registryIds));
            return this;
        }

        public Builder icon(String itemId) {
            this.iconId = itemId;
            return this;
        }

        public Builder hook(String key) {
            this.hookKey = key;
            return this;
        }

        public Builder loose() {
            this.opensLeft = false;
            return this;
        }

        public Builder part(Part part) {
            this.parts.add(part);
            return this;
        }

        public Sheet build() {
            if (titleKey == null || hookKey == null || groupKey == null) {
                throw new IllegalStateException("sheet " + id + " needs a title, a hook and a group");
            }
            if (ids.isEmpty() && section != Section.BASICS && section != Section.FAULTS) {
                throw new IllegalStateException("sheet " + id + " covers no block; only front and back matter may");
            }
            return new Sheet(id, section, groupKey, titleKey, ids, also, iconId, hookKey, opensLeft, parts);
        }
    }
}
