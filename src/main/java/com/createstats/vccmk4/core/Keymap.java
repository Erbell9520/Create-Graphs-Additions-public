package com.createstats.vccmk4.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

// per vehicle, not per player
public final class Keymap {
    public static final int STRIDE = 3;
    public static final int PLUS = 0;
    public static final int MINUS = 1;

    public record Slot(Control control, int slot) {
    }

    private final Map<Control, Integer> plus = new EnumMap<>(Control.class);
    private final Map<Control, Integer> minus = new EnumMap<>(Control.class);
    private final Map<Control, Boolean> inverted = new EnumMap<>(Control.class);

    public Keymap() {
        reset();
    }

    public static Keymap defaults() {
        return new Keymap();
    }

    public Keymap copy() {
        return fromInts(toInts());
    }

    public int plus(Control c) {
        return plus.get(c);
    }

    public int minus(Control c) {
        return minus.get(c);
    }

    public int key(Control c, int slot) {
        return slot == MINUS ? minus(c) : plus(c);
    }

    public boolean inverted(Control c) {
        return inverted.get(c);
    }

    public void setPlus(Control c, int key) {
        plus.put(c, KeyIds.sane(key));
    }

    public void setMinus(Control c, int key) {
        minus.put(c, c.isAxis() ? KeyIds.sane(key) : KeyIds.NONE);
    }

    public void set(Control c, int slot, int key) {
        if (slot == MINUS) setMinus(c, key);
        else setPlus(c, key);
    }

    public void setInverted(Control c, boolean value) {
        inverted.put(c, c.isAxis() && value);
    }

    public void reset() {
        for (Control c : Control.values()) {
            plus.put(c, c.defaultPlus());
            minus.put(c, c.defaultMinus());
            inverted.put(c, false);
        }
    }

    public boolean isDefault() {
        return Arrays.equals(toInts(), new Keymap().toInts());
    }

    public double axis(Control c, HeldKeys held) {
        double v = 0.0;
        if (held.has(plus.get(c))) v += 1.0;
        if (held.has(minus.get(c))) v -= 1.0;
        if (!c.isAxis()) return v > 0.0 ? 1.0 : 0.0;
        return inverted.get(c) ? -v : v;
    }

    public boolean held(Control c, HeldKeys held) {
        return held.has(plus.get(c)) || held.has(minus.get(c));
    }

    public boolean bound(Control c) {
        return plus.get(c) != KeyIds.NONE || minus.get(c) != KeyIds.NONE;
    }

    public int[] keysInUse() {
        TreeSet<Integer> keys = new TreeSet<>();
        for (Control c : Control.values()) {
            if (plus.get(c) != KeyIds.NONE) keys.add(plus.get(c));
            if (minus.get(c) != KeyIds.NONE) keys.add(minus.get(c));
        }
        return keys.stream().mapToInt(Integer::intValue).toArray();
    }

    public List<Slot> slotsOf(int key) {
        List<Slot> out = new ArrayList<>();
        if (key == KeyIds.NONE) return out;
        for (Control c : Control.DISPLAY) {
            if (plus.get(c) == key) out.add(new Slot(c, PLUS));
            if (minus.get(c) == key) out.add(new Slot(c, MINUS));
        }
        return out;
    }

    public int[] toInts() {
        Control[] all = Control.values();
        int[] out = new int[all.length * STRIDE];
        for (int i = 0; i < all.length; i++) {
            out[i * STRIDE] = plus.get(all[i]);
            out[i * STRIDE + 1] = minus.get(all[i]);
            out[i * STRIDE + 2] = inverted.get(all[i]) ? 1 : 0;
        }
        return out;
    }

    public static Keymap fromInts(int[] ints) {
        Keymap k = new Keymap();
        if (ints == null) return k;
        Control[] all = Control.values();
        for (int i = 0; i < all.length; i++) {
            if (ints.length < (i + 1) * STRIDE) break;
            k.setPlus(all[i], ints[i * STRIDE]);
            k.setMinus(all[i], ints[i * STRIDE + 1]);
            k.setInverted(all[i], ints[i * STRIDE + 2] != 0);
        }
        return k;
    }
}
