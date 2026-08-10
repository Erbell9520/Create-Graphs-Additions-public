package com.createstats.display;

import net.minecraft.nbt.CompoundTag;

import java.util.Locale;
import java.util.UUID;

public class CustomScreenProfile {
    public static final String DEFAULT_PROFILE_ID = "default_custom";

    private String id;
    private String name;
    private CustomScreenLayout layout;

    public CustomScreenProfile() {
        this(DEFAULT_PROFILE_ID, "Default Custom", new CustomScreenLayout());
    }

    public CustomScreenProfile(String id, String name, CustomScreenLayout layout) {
        this.id = safe(id);
        this.name = safe(name);
        this.layout = layout != null ? layout.copy() : new CustomScreenLayout();
        this.normalize();
    }

    public CustomScreenProfile(CustomScreenProfile other) {
        this(other != null ? other.id : DEFAULT_PROFILE_ID,
                other != null ? other.name : "Default Custom",
                other != null ? other.layout : new CustomScreenLayout());
    }

    public CustomScreenProfile copy() {
        return new CustomScreenProfile(this);
    }

    public String getId() {
        return this.id;
    }

    public void setId(String id) {
        this.id = safe(id);
    }

    public String getName() {
        return this.name;
    }

    public void setName(String name) {
        this.name = safe(name);
    }

    public CustomScreenLayout getLayout() {
        return this.layout;
    }

    public void setLayout(CustomScreenLayout layout) {
        this.layout = layout != null ? layout.copy() : new CustomScreenLayout();
    }

    public boolean normalize() {
        boolean changed = false;
        final String normalizedId = safe(this.id);
        if (!normalizedId.equals(this.id)) {
            this.id = normalizedId;
            changed = true;
        }
        if (this.id.isEmpty()) {
            this.id = createProfileId(this.name);
            changed = true;
        }
        final String normalizedName = safe(this.name).trim();
        if (!normalizedName.equals(this.name)) {
            this.name = normalizedName;
            changed = true;
        }
        if (this.name.isEmpty()) {
            this.name = "Custom Layout";
            changed = true;
        }
        if (this.layout == null) {
            this.layout = new CustomScreenLayout();
            changed = true;
        }
        else {
            changed |= this.layout.normalize();
        }
        final CustomScreenLayout.LayoutMeta meta = this.layout.getLayoutMeta() != null ? this.layout.getLayoutMeta().copy() : new CustomScreenLayout.LayoutMeta();
        if (meta.getName().isEmpty()) {
            meta.setName(this.name);
            this.layout.setLayoutMeta(meta);
            changed = true;
        }
        return changed;
    }

    public CompoundTag save() {
        this.normalize();
        final CompoundTag tag = new CompoundTag();
        tag.putString("Id", this.id);
        tag.putString("Name", this.name);
        tag.put("Layout", this.layout.save());
        return tag;
    }

    public static CustomScreenProfile load(CompoundTag tag) {
        if (tag == null) {
            return new CustomScreenProfile();
        }
        final String id = tag.contains("Id") ? tag.getString("Id") : "";
        final String name = tag.contains("Name") ? tag.getString("Name") : "";
        final CustomScreenLayout layout = tag.contains("Layout", 10) ? CustomScreenLayout.load(tag.getCompound("Layout")) : new CustomScreenLayout();
        return new CustomScreenProfile(id, name, layout);
    }

    public static CustomScreenProfile create(String name, CustomScreenLayout layout) {
        return new CustomScreenProfile(createProfileId(name), name, layout);
    }

    private static String createProfileId(String name) {
        final String trimmed = safe(name).trim().toLowerCase(Locale.ROOT);
        final StringBuilder slug = new StringBuilder();
        for (int index = 0; index < trimmed.length(); ++index) {
            final char character = trimmed.charAt(index);
            if ((character >= 'a' && character <= 'z') || (character >= '0' && character <= '9')) {
                slug.append(character);
            }
            else if (slug.length() > 0 && slug.charAt(slug.length() - 1) != '_') {
                slug.append('_');
            }
        }
        final String prefix = slug.isEmpty() ? "custom" : slug.toString();
        return prefix + "_" + UUID.randomUUID().toString().substring(0, 8);
    }

    private static String safe(String value) {
        return value != null ? value : "";
    }
}