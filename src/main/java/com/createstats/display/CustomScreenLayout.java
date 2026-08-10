package com.createstats.display;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CustomScreenLayout {
    public static final int CURRENT_VERSION = 1;
    public static final int DEFAULT_GRID_SIZE = 16;

    private int version;
    private LayoutMode mode;
    private int gridSize;
    private VariantFamily baseVariant;
    private ArrayList<CustomModuleInstance> modules;
    private EnumMap<VariantFamily, VariantLayout> variants;
    private LayoutMeta layoutMeta;

    public CustomScreenLayout() {
        this.version = CURRENT_VERSION;
        this.mode = LayoutMode.CUSTOM;
        this.gridSize = DEFAULT_GRID_SIZE;
        this.baseVariant = VariantFamily.BASE;
        this.modules = new ArrayList<CustomModuleInstance>();
        this.variants = new EnumMap<VariantFamily, VariantLayout>(VariantFamily.class);
        this.layoutMeta = new LayoutMeta();
    }

    public CustomScreenLayout(CustomScreenLayout other) {
        this.version = other != null ? other.version : CURRENT_VERSION;
        this.mode = other != null && other.mode != null ? other.mode : LayoutMode.CUSTOM;
        this.gridSize = other != null ? other.gridSize : DEFAULT_GRID_SIZE;
        this.baseVariant = other != null && other.baseVariant != null ? other.baseVariant : VariantFamily.BASE;
        this.modules = new ArrayList<CustomModuleInstance>();
        if (other != null) {
            for (CustomModuleInstance module : other.modules) {
                if (module != null) {
                    this.modules.add(module.copy());
                }
            }
        }
        this.variants = new EnumMap<VariantFamily, VariantLayout>(VariantFamily.class);
        if (other != null) {
            for (Map.Entry<VariantFamily, VariantLayout> entry : other.variants.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    this.variants.put(entry.getKey(), entry.getValue().copy());
                }
            }
        }
        this.layoutMeta = other != null && other.layoutMeta != null ? other.layoutMeta.copy() : new LayoutMeta();
        this.normalize();
    }

    public CustomScreenLayout copy() {
        return new CustomScreenLayout(this);
    }

    public int getVersion() {
        return this.version;
    }

    public LayoutMode getMode() {
        return this.mode;
    }

    public void setMode(LayoutMode mode) {
        this.mode = mode != null ? mode : LayoutMode.CUSTOM;
    }

    public int getGridSize() {
        return this.gridSize;
    }

    public void setGridSize(int gridSize) {
        this.gridSize = Math.max(4, gridSize);
    }

    public VariantFamily getBaseVariant() {
        return this.baseVariant;
    }

    public void setBaseVariant(VariantFamily baseVariant) {
        this.baseVariant = baseVariant != null ? baseVariant : VariantFamily.BASE;
        if (this.baseVariant != VariantFamily.BASE) {
            this.baseVariant = VariantFamily.BASE;
        }
    }

    public List<CustomModuleInstance> getModules() {
        return Collections.unmodifiableList(this.modules);
    }

    public void setModules(List<CustomModuleInstance> modules) {
        this.modules.clear();
        if (modules == null) {
            return;
        }
        for (CustomModuleInstance module : modules) {
            if (module != null) {
                this.modules.add(module.copy());
            }
        }
    }

    public void addModule(CustomModuleInstance module) {
        if (module != null) {
            this.modules.add(module.copy());
        }
    }

    public CustomModuleInstance findModule(String moduleId) {
        if (moduleId == null || moduleId.isEmpty()) {
            return null;
        }
        for (CustomModuleInstance module : this.modules) {
            if (module != null && moduleId.equals(module.getId())) {
                return module;
            }
        }
        return null;
    }

    public boolean removeModule(String moduleId) {
        if (moduleId == null || moduleId.isEmpty()) {
            return false;
        }
        return this.modules.removeIf(module -> module != null && moduleId.equals(module.getId()));
    }

    public VariantLayout getVariant(VariantFamily family) {
        return family != null ? this.variants.get(family) : null;
    }

    public VariantLayout getOrCreateVariant(VariantFamily family) {
        if (family == null || family == VariantFamily.BASE) {
            return null;
        }
        VariantLayout layout = this.variants.get(family);
        if (layout == null) {
            layout = new VariantLayout();
            this.variants.put(family, layout);
        }
        return layout;
    }

    public void setVariant(VariantFamily family, VariantLayout variantLayout) {
        if (family == null || family == VariantFamily.BASE) {
            return;
        }
        if (variantLayout == null || variantLayout.isEmpty()) {
            this.variants.remove(family);
            return;
        }
        this.variants.put(family, variantLayout.copy());
    }

    public LayoutMeta getLayoutMeta() {
        return this.layoutMeta;
    }

    public void setLayoutMeta(LayoutMeta layoutMeta) {
        this.layoutMeta = layoutMeta != null ? layoutMeta.copy() : new LayoutMeta();
    }

    public boolean isEmpty() {
        return this.modules.isEmpty() && this.variants.isEmpty() && this.layoutMeta.isEmpty();
    }

    public boolean normalize() {
        boolean changed = false;
        if (this.version != CURRENT_VERSION) {
            this.version = CURRENT_VERSION;
            changed = true;
        }
        if (this.mode == null) {
            this.mode = LayoutMode.CUSTOM;
            changed = true;
        }
        if (this.gridSize < 4) {
            this.gridSize = DEFAULT_GRID_SIZE;
            changed = true;
        }
        if (this.baseVariant != VariantFamily.BASE) {
            this.baseVariant = VariantFamily.BASE;
            changed = true;
        }
        final ArrayList<CustomModuleInstance> normalizedModules = new ArrayList<CustomModuleInstance>();
        final LinkedHashSet<String> seenIds = new LinkedHashSet<String>();
        for (CustomModuleInstance module : this.modules) {
            if (module == null) {
                changed = true;
                continue;
            }
            changed |= module.normalize();
            if (!module.getId().isEmpty() && !seenIds.add(module.getId())) {
                changed = true;
                continue;
            }
            normalizedModules.add(module);
        }
        if (normalizedModules.size() != this.modules.size()) {
            this.modules = normalizedModules;
            changed = true;
        }
        if (this.variants == null) {
            this.variants = new EnumMap<VariantFamily, VariantLayout>(VariantFamily.class);
            changed = true;
        }
        final EnumMap<VariantFamily, VariantLayout> normalizedVariants = new EnumMap<VariantFamily, VariantLayout>(VariantFamily.class);
        for (VariantFamily family : VariantFamily.values()) {
            if (family == VariantFamily.BASE) {
                continue;
            }
            final VariantLayout variantLayout = this.variants.get(family);
            if (variantLayout == null) {
                continue;
            }
            changed |= variantLayout.normalize();
            if (!variantLayout.isEmpty()) {
                normalizedVariants.put(family, variantLayout);
            }
            else {
                changed = true;
            }
        }
        if (!normalizedVariants.equals(this.variants)) {
            this.variants = normalizedVariants;
            changed = true;
        }
        if (this.layoutMeta == null) {
            this.layoutMeta = new LayoutMeta();
            changed = true;
        }
        else {
            changed |= this.layoutMeta.normalize();
        }
        return changed;
    }

    public CompoundTag save() {
        final CompoundTag tag = new CompoundTag();
        tag.putInt("Version", this.version);
        tag.putString("Mode", this.mode.getSerializedName());
        tag.putInt("GridSize", this.gridSize);
        tag.putString("BaseVariant", this.baseVariant.getSerializedName());
        final ListTag moduleList = new ListTag();
        for (CustomModuleInstance module : this.modules) {
            moduleList.add(module.save());
        }
        tag.put("Modules", moduleList);
        final CompoundTag variantTag = new CompoundTag();
        for (Map.Entry<VariantFamily, VariantLayout> entry : this.variants.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null && !entry.getValue().isEmpty()) {
                variantTag.put(entry.getKey().getSerializedName(), entry.getValue().save());
            }
        }
        if (!variantTag.isEmpty()) {
            tag.put("Variants", variantTag);
        }
        if (!this.layoutMeta.isEmpty()) {
            tag.put("LayoutMeta", this.layoutMeta.save());
        }
        return tag;
    }

    public static CustomScreenLayout load(CompoundTag tag) {
        final CustomScreenLayout layout = new CustomScreenLayout();
        if (tag == null) {
            return layout;
        }
        if (tag.contains("Version")) {
            layout.version = tag.getInt("Version");
        }
        if (tag.contains("Mode")) {
            layout.mode = LayoutMode.fromSerializedName(tag.getString("Mode"));
        }
        if (tag.contains("GridSize")) {
            layout.gridSize = tag.getInt("GridSize");
        }
        if (tag.contains("BaseVariant")) {
            layout.baseVariant = VariantFamily.fromSerializedName(tag.getString("BaseVariant"));
        }
        if (tag.contains("Modules", 9)) {
            layout.modules.clear();
            final ListTag moduleList = tag.getList("Modules", 10);
            for (int i = 0; i < moduleList.size(); ++i) {
                layout.modules.add(CustomModuleInstance.load(moduleList.getCompound(i)));
            }
        }
        if (tag.contains("Variants", 10)) {
            final CompoundTag variantTag = tag.getCompound("Variants");
            for (VariantFamily family : VariantFamily.values()) {
                if (family == VariantFamily.BASE || !variantTag.contains(family.getSerializedName(), 10)) {
                    continue;
                }
                layout.variants.put(family, VariantLayout.load(variantTag.getCompound(family.getSerializedName())));
            }
        }
        if (tag.contains("LayoutMeta", 10)) {
            layout.layoutMeta = LayoutMeta.load(tag.getCompound("LayoutMeta"));
        }
        layout.normalize();
        return layout;
    }

    public enum LayoutMode {
        CUSTOM("custom");

        private final String serializedName;

        LayoutMode(String serializedName) {
            this.serializedName = serializedName;
        }

        public String getSerializedName() {
            return this.serializedName;
        }

        public static LayoutMode fromSerializedName(String serializedName) {
            for (LayoutMode value : values()) {
                if (value.serializedName.equals(serializedName)) {
                    return value;
                }
            }
            return CUSTOM;
        }
    }

    public enum VariantFamily {
        BASE("base"),
        WIDE("wide"),
        SQUARE("square"),
        TALL("tall"),
        COMPACT("compact");

        private final String serializedName;

        VariantFamily(String serializedName) {
            this.serializedName = serializedName;
        }

        public String getSerializedName() {
            return this.serializedName;
        }

        public static VariantFamily fromSerializedName(String serializedName) {
            for (VariantFamily value : values()) {
                if (value.serializedName.equals(serializedName)) {
                    return value;
                }
            }
            return BASE;
        }
    }

    public static class LayoutMeta {
        private String name;
        private String notes;

        public LayoutMeta() {
            this.name = "";
            this.notes = "";
        }

        public LayoutMeta(LayoutMeta other) {
            this.name = other != null ? safe(other.name) : "";
            this.notes = other != null ? safe(other.notes) : "";
        }

        public LayoutMeta copy() {
            return new LayoutMeta(this);
        }

        public String getName() {
            return this.name;
        }

        public void setName(String name) {
            this.name = safe(name);
        }

        public String getNotes() {
            return this.notes;
        }

        public void setNotes(String notes) {
            this.notes = safe(notes);
        }

        public boolean isEmpty() {
            return this.name.isEmpty() && this.notes.isEmpty();
        }

        public boolean normalize() {
            boolean changed = false;
            final String normalizedName = safe(this.name);
            if (!normalizedName.equals(this.name)) {
                this.name = normalizedName;
                changed = true;
            }
            final String normalizedNotes = safe(this.notes);
            if (!normalizedNotes.equals(this.notes)) {
                this.notes = normalizedNotes;
                changed = true;
            }
            return changed;
        }

        public CompoundTag save() {
            final CompoundTag tag = new CompoundTag();
            if (!this.name.isEmpty()) {
                tag.putString("Name", this.name);
            }
            if (!this.notes.isEmpty()) {
                tag.putString("Notes", this.notes);
            }
            return tag;
        }

        public static LayoutMeta load(CompoundTag tag) {
            final LayoutMeta meta = new LayoutMeta();
            if (tag == null) {
                return meta;
            }
            if (tag.contains("Name")) {
                meta.name = safe(tag.getString("Name"));
            }
            if (tag.contains("Notes")) {
                meta.notes = safe(tag.getString("Notes"));
            }
            meta.normalize();
            return meta;
        }
    }

    public static class VariantLayout {
        private LinkedHashMap<String, CustomModuleInstance.VariantOverride> moduleOverrides;
        private ArrayList<String> omittedModules;

        public VariantLayout() {
            this.moduleOverrides = new LinkedHashMap<String, CustomModuleInstance.VariantOverride>();
            this.omittedModules = new ArrayList<String>();
        }

        public VariantLayout(VariantLayout other) {
            this();
            if (other == null) {
                return;
            }
            for (Map.Entry<String, CustomModuleInstance.VariantOverride> entry : other.moduleOverrides.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    this.moduleOverrides.put(entry.getKey(), entry.getValue().copy());
                }
            }
            this.omittedModules.addAll(other.omittedModules);
        }

        public VariantLayout copy() {
            return new VariantLayout(this);
        }

        public Map<String, CustomModuleInstance.VariantOverride> getModuleOverrides() {
            return Collections.unmodifiableMap(this.moduleOverrides);
        }

        public void putModuleOverride(String moduleId, CustomModuleInstance.VariantOverride override) {
            if (moduleId == null || moduleId.isEmpty() || override == null || override.isEmpty()) {
                return;
            }
            this.moduleOverrides.put(moduleId, override.copy());
        }

        public Set<String> getOmittedModules() {
            return Collections.unmodifiableSet(new LinkedHashSet<String>(this.omittedModules));
        }

        public void addOmittedModule(String moduleId) {
            if (moduleId != null && !moduleId.isEmpty() && !this.omittedModules.contains(moduleId)) {
                this.omittedModules.add(moduleId);
            }
        }

        public void removeModuleOverride(String moduleId) {
            if (moduleId == null || moduleId.isEmpty()) {
                return;
            }
            this.moduleOverrides.remove(moduleId);
        }

        public void removeOmittedModule(String moduleId) {
            if (moduleId == null || moduleId.isEmpty()) {
                return;
            }
            this.omittedModules.removeIf(moduleId::equals);
        }

        public boolean isEmpty() {
            return this.moduleOverrides.isEmpty() && this.omittedModules.isEmpty();
        }

        public boolean normalize() {
            boolean changed = false;
            final LinkedHashMap<String, CustomModuleInstance.VariantOverride> normalizedOverrides = new LinkedHashMap<String, CustomModuleInstance.VariantOverride>();
            for (Map.Entry<String, CustomModuleInstance.VariantOverride> entry : this.moduleOverrides.entrySet()) {
                final String moduleId = safe(entry.getKey());
                final CustomModuleInstance.VariantOverride override = entry.getValue();
                if (moduleId.isEmpty() || override == null) {
                    changed = true;
                    continue;
                }
                changed |= override.normalize();
                if (override.isEmpty()) {
                    changed = true;
                    continue;
                }
                normalizedOverrides.put(moduleId, override);
            }
            if (!normalizedOverrides.equals(this.moduleOverrides)) {
                this.moduleOverrides = normalizedOverrides;
                changed = true;
            }
            final LinkedHashSet<String> dedupedOmittedModules = new LinkedHashSet<String>();
            for (String omittedModule : this.omittedModules) {
                final String moduleId = safe(omittedModule);
                if (moduleId.isEmpty()) {
                    changed = true;
                    continue;
                }
                if (!dedupedOmittedModules.add(moduleId)) {
                    changed = true;
                }
            }
            if (dedupedOmittedModules.size() != this.omittedModules.size()) {
                this.omittedModules = new ArrayList<String>(dedupedOmittedModules);
                changed = true;
            }
            return changed;
        }

        public CompoundTag save() {
            final CompoundTag tag = new CompoundTag();
            final CompoundTag overrideTag = new CompoundTag();
            for (Map.Entry<String, CustomModuleInstance.VariantOverride> entry : this.moduleOverrides.entrySet()) {
                if (entry.getKey() != null && !entry.getKey().isEmpty() && entry.getValue() != null && !entry.getValue().isEmpty()) {
                    overrideTag.put(entry.getKey(), entry.getValue().save());
                }
            }
            if (!overrideTag.isEmpty()) {
                tag.put("ModuleOverrides", overrideTag);
            }
            if (!this.omittedModules.isEmpty()) {
                final ListTag omittedList = new ListTag();
                for (String omittedModule : this.omittedModules) {
                    final CompoundTag omittedTag = new CompoundTag();
                    omittedTag.putString("Id", omittedModule);
                    omittedList.add(omittedTag);
                }
                tag.put("OmittedModules", omittedList);
            }
            return tag;
        }

        public static VariantLayout load(CompoundTag tag) {
            final VariantLayout layout = new VariantLayout();
            if (tag == null) {
                return layout;
            }
            if (tag.contains("ModuleOverrides", 10)) {
                final CompoundTag overrideTag = tag.getCompound("ModuleOverrides");
                for (String key : overrideTag.getAllKeys()) {
                    layout.moduleOverrides.put(key, CustomModuleInstance.VariantOverride.load(overrideTag.getCompound(key)));
                }
            }
            if (tag.contains("OmittedModules", 9)) {
                final ListTag omittedList = tag.getList("OmittedModules", 10);
                for (int i = 0; i < omittedList.size(); ++i) {
                    layout.omittedModules.add(safe(omittedList.getCompound(i).getString("Id")));
                }
            }
            layout.normalize();
            return layout;
        }
    }

    private static String safe(String value) {
        return value != null ? value : "";
    }
}