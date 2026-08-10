package com.createstats.display;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CustomModuleInstance {
    private String id;
    private String moduleType;
    private ModuleScale scale;
    private GridBounds bounds;
    private String variant;
    private ArrayList<CustomModuleBinding> bindings;
    private CompoundTag moduleConfig;
    private VisibilityPolicy visibilityPolicy;

    public CustomModuleInstance() {
        this.id = "";
        this.moduleType = "";
        this.scale = ModuleScale.WIDGET;
        this.bounds = new GridBounds(0, 0, 4, 4);
        this.variant = "default";
        this.bindings = new ArrayList<CustomModuleBinding>();
        this.moduleConfig = new CompoundTag();
        this.moduleConfig.putInt("configVersion", 1);
        this.visibilityPolicy = new VisibilityPolicy();
    }

    public CustomModuleInstance(CustomModuleInstance other) {
        this.id = other != null ? safe(other.id) : "";
        this.moduleType = other != null ? safe(other.moduleType) : "";
        this.scale = other != null && other.scale != null ? other.scale : ModuleScale.WIDGET;
        this.bounds = other != null && other.bounds != null ? other.bounds.copy() : new GridBounds(0, 0, 4, 4);
        this.variant = other != null ? safe(other.variant) : "default";
        this.bindings = new ArrayList<CustomModuleBinding>();
        if (other != null) {
            for (CustomModuleBinding binding : other.bindings) {
                if (binding != null) {
                    this.bindings.add(binding.copy());
                }
            }
        }
        this.moduleConfig = other != null && other.moduleConfig != null ? other.moduleConfig.copy() : new CompoundTag();
        if (!this.moduleConfig.contains("configVersion")) {
            this.moduleConfig.putInt("configVersion", 1);
        }
        this.visibilityPolicy = other != null && other.visibilityPolicy != null ? other.visibilityPolicy.copy() : new VisibilityPolicy();
    }

    public CustomModuleInstance copy() {
        return new CustomModuleInstance(this);
    }

    public String getId() {
        return this.id;
    }

    public void setId(String id) {
        this.id = safe(id);
    }

    public String getModuleType() {
        return this.moduleType;
    }

    public void setModuleType(String moduleType) {
        this.moduleType = safe(moduleType);
    }

    public ModuleScale getScale() {
        return this.scale;
    }

    public void setScale(ModuleScale scale) {
        this.scale = scale != null ? scale : ModuleScale.WIDGET;
    }

    public GridBounds getBounds() {
        return this.bounds;
    }

    public void setBounds(GridBounds bounds) {
        this.bounds = bounds != null ? bounds.copy() : new GridBounds(0, 0, 4, 4);
    }

    public String getVariant() {
        return this.variant;
    }

    public void setVariant(String variant) {
        this.variant = safe(variant);
    }

    public List<CustomModuleBinding> getBindings() {
        return Collections.unmodifiableList(this.bindings);
    }

    public void setBindings(List<CustomModuleBinding> bindings) {
        this.bindings.clear();
        if (bindings == null) {
            return;
        }
        for (CustomModuleBinding binding : bindings) {
            if (binding != null) {
                this.bindings.add(binding.copy());
            }
        }
    }

    public void addBinding(CustomModuleBinding binding) {
        if (binding != null) {
            this.bindings.add(binding.copy());
        }
    }

    public CompoundTag getModuleConfig() {
        return this.moduleConfig.copy();
    }

    public void setModuleConfig(CompoundTag moduleConfig) {
        this.moduleConfig = moduleConfig != null ? moduleConfig.copy() : new CompoundTag();
        if (!this.moduleConfig.contains("configVersion")) {
            this.moduleConfig.putInt("configVersion", 1);
        }
    }

    public VisibilityPolicy getVisibilityPolicy() {
        return this.visibilityPolicy;
    }

    public void setVisibilityPolicy(VisibilityPolicy visibilityPolicy) {
        this.visibilityPolicy = visibilityPolicy != null ? visibilityPolicy.copy() : new VisibilityPolicy();
    }

    public CustomModuleBinding resolveBinding(String bindingKey) {
        if (bindingKey == null || bindingKey.isEmpty()) {
            return null;
        }
        CustomModuleBinding semanticMatch = null;
        CustomModuleBinding firstMatch = null;
        for (CustomModuleBinding binding : this.bindings) {
            if (binding == null) {
                continue;
            }
            binding.normalize();
            if (!binding.matchesField(bindingKey)) {
                continue;
            }
            if (firstMatch == null) {
                firstMatch = binding;
            }
            if (binding.getMode() == CustomModuleBinding.Mode.EXPLICIT_SOURCE && !binding.getSourceId().isEmpty()) {
                return binding;
            }
            if (semanticMatch == null
                    && binding.getMode() == CustomModuleBinding.Mode.SEMANTIC_SLOT
                    && !binding.getSlot().isEmpty()) {
                semanticMatch = binding;
            }
        }
        return semanticMatch != null ? semanticMatch : firstMatch;
    }

    public boolean normalize() {
        boolean changed = false;
        final String normalizedId = safe(this.id);
        if (!normalizedId.equals(this.id)) {
            this.id = normalizedId;
            changed = true;
        }
        final String normalizedModuleType = safe(this.moduleType);
        if (!normalizedModuleType.equals(this.moduleType)) {
            this.moduleType = normalizedModuleType;
            changed = true;
        }
        if (this.scale == null) {
            this.scale = ModuleScale.WIDGET;
            changed = true;
        }
        if (this.bounds == null) {
            this.bounds = new GridBounds(0, 0, 4, 4);
            changed = true;
        }
        else {
            changed |= this.bounds.normalize();
        }
        final String normalizedVariant = safe(this.variant);
        if (!normalizedVariant.equals(this.variant)) {
            this.variant = normalizedVariant;
            changed = true;
        }
        final ArrayList<CustomModuleBinding> normalizedBindings = new ArrayList<CustomModuleBinding>();
        for (CustomModuleBinding binding : this.bindings) {
            if (binding == null) {
                changed = true;
                continue;
            }
            binding.normalize();
            normalizedBindings.add(binding);
        }
        if (normalizedBindings.size() != this.bindings.size()) {
            this.bindings = normalizedBindings;
            changed = true;
        }
        if (this.moduleConfig == null) {
            this.moduleConfig = new CompoundTag();
            changed = true;
        }
        if (!this.moduleConfig.contains("configVersion")) {
            this.moduleConfig.putInt("configVersion", 1);
            changed = true;
        }
        if (this.visibilityPolicy == null) {
            this.visibilityPolicy = new VisibilityPolicy();
            changed = true;
        }
        else {
            changed |= this.visibilityPolicy.normalize();
        }
        return changed;
    }

    public CompoundTag save() {
        final CompoundTag tag = new CompoundTag();
        tag.putString("Id", this.id);
        tag.putString("ModuleType", this.moduleType);
        tag.putString("Scale", this.scale.getSerializedName());
        tag.put("Bounds", this.bounds.save());
        tag.putString("Variant", this.variant);
        final ListTag bindingList = new ListTag();
        for (CustomModuleBinding binding : this.bindings) {
            bindingList.add(binding.save());
        }
        tag.put("Bindings", bindingList);
        tag.put("ModuleConfig", this.moduleConfig.copy());
        tag.put("VisibilityPolicy", this.visibilityPolicy.save());
        return tag;
    }

    public static CustomModuleInstance load(CompoundTag tag) {
        final CustomModuleInstance instance = new CustomModuleInstance();
        if (tag == null) {
            return instance;
        }
        if (tag.contains("Id")) {
            instance.id = safe(tag.getString("Id"));
        }
        if (tag.contains("ModuleType")) {
            instance.moduleType = safe(tag.getString("ModuleType"));
        }
        if (tag.contains("Scale")) {
            instance.scale = ModuleScale.fromSerializedName(tag.getString("Scale"));
        }
        if (tag.contains("Bounds", 10)) {
            instance.bounds = GridBounds.load(tag.getCompound("Bounds"));
        }
        if (tag.contains("Variant")) {
            instance.variant = safe(tag.getString("Variant"));
        }
        if (tag.contains("Bindings", 9)) {
            instance.bindings.clear();
            final ListTag bindingList = tag.getList("Bindings", 10);
            for (int i = 0; i < bindingList.size(); ++i) {
                instance.bindings.add(CustomModuleBinding.load(bindingList.getCompound(i)));
            }
        }
        if (tag.contains("ModuleConfig", 10)) {
            instance.moduleConfig = tag.getCompound("ModuleConfig").copy();
        }
        if (tag.contains("VisibilityPolicy", 10)) {
            instance.visibilityPolicy = VisibilityPolicy.load(tag.getCompound("VisibilityPolicy"));
        }
        instance.normalize();
        return instance;
    }

    private static String safe(String value) {
        return value != null ? value : "";
    }

    public enum ModuleScale {
        ATOM("atom"),
        WIDGET("widget"),
        MACRO_SCENE("macro-scene");

        private final String serializedName;

        ModuleScale(String serializedName) {
            this.serializedName = serializedName;
        }

        public String getSerializedName() {
            return this.serializedName;
        }

        public static ModuleScale fromSerializedName(String serializedName) {
            for (ModuleScale value : values()) {
                if (value.serializedName.equals(serializedName)) {
                    return value;
                }
            }
            return WIDGET;
        }
    }

    public static class GridBounds {
        private int x;
        private int y;
        private int w;
        private int h;

        public GridBounds(int x, int y, int w, int h) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.normalize();
        }

        public GridBounds(GridBounds other) {
            this(other != null ? other.x : 0, other != null ? other.y : 0, other != null ? other.w : 4, other != null ? other.h : 4);
        }

        public GridBounds copy() {
            return new GridBounds(this);
        }

        public int getX() {
            return this.x;
        }

        public void setX(int x) {
            this.x = x;
        }

        public int getY() {
            return this.y;
        }

        public void setY(int y) {
            this.y = y;
        }

        public int getWidth() {
            return this.w;
        }

        public void setWidth(int w) {
            this.w = w;
            this.normalize();
        }

        public int getHeight() {
            return this.h;
        }

        public void setHeight(int h) {
            this.h = h;
            this.normalize();
        }

        public boolean normalize() {
            boolean changed = false;
            if (this.w < 1) {
                this.w = 1;
                changed = true;
            }
            if (this.h < 1) {
                this.h = 1;
                changed = true;
            }
            return changed;
        }

        public CompoundTag save() {
            final CompoundTag tag = new CompoundTag();
            tag.putInt("X", this.x);
            tag.putInt("Y", this.y);
            tag.putInt("W", this.w);
            tag.putInt("H", this.h);
            return tag;
        }

        public static GridBounds load(CompoundTag tag) {
            return new GridBounds(tag.getInt("X"), tag.getInt("Y"), tag.getInt("W"), tag.getInt("H"));
        }
    }

    public static class VisibilityPolicy {
        private VisibilityMode base;
        private VisibilityMode wide;
        private VisibilityMode square;
        private VisibilityMode tall;
        private VisibilityMode compact;

        public VisibilityPolicy() {
            this.base = VisibilityMode.SHOW;
            this.wide = VisibilityMode.SHOW;
            this.square = VisibilityMode.SHOW;
            this.tall = VisibilityMode.SHOW;
            this.compact = VisibilityMode.HIDE;
        }

        public VisibilityPolicy(VisibilityPolicy other) {
            this.base = other != null && other.base != null ? other.base : VisibilityMode.SHOW;
            this.wide = other != null && other.wide != null ? other.wide : VisibilityMode.SHOW;
            this.square = other != null && other.square != null ? other.square : VisibilityMode.SHOW;
            this.tall = other != null && other.tall != null ? other.tall : VisibilityMode.SHOW;
            this.compact = other != null && other.compact != null ? other.compact : VisibilityMode.HIDE;
        }

        public VisibilityPolicy copy() {
            return new VisibilityPolicy(this);
        }

        public VisibilityMode get(CustomScreenLayout.VariantFamily family) {
            if (family == null) {
                return this.base;
            }
            return switch (family) {
                case BASE -> this.base;
                case WIDE -> this.wide;
                case SQUARE -> this.square;
                case TALL -> this.tall;
                case COMPACT -> this.compact;
            };
        }

        public void set(CustomScreenLayout.VariantFamily family, VisibilityMode mode) {
            final VisibilityMode normalizedMode = mode != null ? mode : VisibilityMode.SHOW;
            if (family == null) {
                this.base = normalizedMode;
                return;
            }
            switch (family) {
                case BASE -> this.base = normalizedMode;
                case WIDE -> this.wide = normalizedMode;
                case SQUARE -> this.square = normalizedMode;
                case TALL -> this.tall = normalizedMode;
                case COMPACT -> this.compact = normalizedMode;
            }
        }

        public boolean normalize() {
            boolean changed = false;
            if (this.base == null) {
                this.base = VisibilityMode.SHOW;
                changed = true;
            }
            if (this.wide == null) {
                this.wide = VisibilityMode.SHOW;
                changed = true;
            }
            if (this.square == null) {
                this.square = VisibilityMode.SHOW;
                changed = true;
            }
            if (this.tall == null) {
                this.tall = VisibilityMode.SHOW;
                changed = true;
            }
            if (this.compact == null) {
                this.compact = VisibilityMode.HIDE;
                changed = true;
            }
            return changed;
        }

        public CompoundTag save() {
            final CompoundTag tag = new CompoundTag();
            tag.putString("Base", this.base.getSerializedName());
            tag.putString("Wide", this.wide.getSerializedName());
            tag.putString("Square", this.square.getSerializedName());
            tag.putString("Tall", this.tall.getSerializedName());
            tag.putString("Compact", this.compact.getSerializedName());
            return tag;
        }

        public static VisibilityPolicy load(CompoundTag tag) {
            final VisibilityPolicy policy = new VisibilityPolicy();
            if (tag == null) {
                return policy;
            }
            if (tag.contains("Base")) {
                policy.base = VisibilityMode.fromSerializedName(tag.getString("Base"));
            }
            if (tag.contains("Wide")) {
                policy.wide = VisibilityMode.fromSerializedName(tag.getString("Wide"));
            }
            if (tag.contains("Square")) {
                policy.square = VisibilityMode.fromSerializedName(tag.getString("Square"));
            }
            if (tag.contains("Tall")) {
                policy.tall = VisibilityMode.fromSerializedName(tag.getString("Tall"));
            }
            if (tag.contains("Compact")) {
                policy.compact = VisibilityMode.fromSerializedName(tag.getString("Compact"));
            }
            policy.normalize();
            return policy;
        }

        public enum VisibilityMode {
            SHOW("show"),
            HIDE("hide"),
            INHERIT("inherit");

            private final String serializedName;

            VisibilityMode(String serializedName) {
                this.serializedName = serializedName;
            }

            public String getSerializedName() {
                return this.serializedName;
            }

            public static VisibilityMode fromSerializedName(String serializedName) {
                for (VisibilityMode value : values()) {
                    if (value.serializedName.equals(serializedName)) {
                        return value;
                    }
                }
                return SHOW;
            }
        }
    }

    public static class VariantOverride {
        private GridBounds bounds;
        private String variant;
        private Integer order;
        private VisibilityPolicy.VisibilityMode visibility;

        public VariantOverride() {
            this.bounds = null;
            this.variant = "";
            this.order = null;
            this.visibility = VisibilityPolicy.VisibilityMode.INHERIT;
        }

        public VariantOverride(VariantOverride other) {
            this.bounds = other != null && other.bounds != null ? other.bounds.copy() : null;
            this.variant = other != null ? safe(other.variant) : "";
            this.order = other != null ? other.order : null;
            this.visibility = other != null && other.visibility != null ? other.visibility : VisibilityPolicy.VisibilityMode.INHERIT;
        }

        public VariantOverride copy() {
            return new VariantOverride(this);
        }

        public GridBounds getBounds() {
            return this.bounds;
        }

        public void setBounds(GridBounds bounds) {
            this.bounds = bounds != null ? bounds.copy() : null;
        }

        public String getVariant() {
            return this.variant;
        }

        public void setVariant(String variant) {
            this.variant = safe(variant);
        }

        public Integer getOrder() {
            return this.order;
        }

        public void setOrder(Integer order) {
            this.order = order;
        }

        public VisibilityPolicy.VisibilityMode getVisibility() {
            return this.visibility;
        }

        public void setVisibility(VisibilityPolicy.VisibilityMode visibility) {
            this.visibility = visibility != null ? visibility : VisibilityPolicy.VisibilityMode.INHERIT;
        }

        public boolean normalize() {
            boolean changed = false;
            if (this.bounds != null) {
                changed |= this.bounds.normalize();
            }
            final String normalizedVariant = safe(this.variant);
            if (!normalizedVariant.equals(this.variant)) {
                this.variant = normalizedVariant;
                changed = true;
            }
            if (this.visibility == null) {
                this.visibility = VisibilityPolicy.VisibilityMode.INHERIT;
                changed = true;
            }
            return changed;
        }

        public boolean isEmpty() {
            return this.bounds == null
                    && this.order == null
                    && (this.variant == null || this.variant.isEmpty())
                    && this.visibility == VisibilityPolicy.VisibilityMode.INHERIT;
        }

        public CompoundTag save() {
            final CompoundTag tag = new CompoundTag();
            if (this.bounds != null) {
                tag.put("Bounds", this.bounds.save());
            }
            if (this.order != null) {
                tag.putInt("Order", this.order);
            }
            if (this.variant != null && !this.variant.isEmpty()) {
                tag.putString("Variant", this.variant);
            }
            if (this.visibility != null && this.visibility != VisibilityPolicy.VisibilityMode.INHERIT) {
                tag.putString("Visibility", this.visibility.getSerializedName());
            }
            return tag;
        }

        public static VariantOverride load(CompoundTag tag) {
            final VariantOverride override = new VariantOverride();
            if (tag == null) {
                return override;
            }
            if (tag.contains("Bounds", 10)) {
                override.bounds = GridBounds.load(tag.getCompound("Bounds"));
            }
            if (tag.contains("Order")) {
                override.order = tag.getInt("Order");
            }
            if (tag.contains("Variant")) {
                override.variant = safe(tag.getString("Variant"));
            }
            if (tag.contains("Visibility")) {
                override.visibility = VisibilityPolicy.VisibilityMode.fromSerializedName(tag.getString("Visibility"));
            }
            override.normalize();
            return override;
        }
    }
}