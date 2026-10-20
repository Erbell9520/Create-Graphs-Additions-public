package com.createstats.battle.core;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// parallel primitive arrays, 4096 reads a tick, no objects
public final class SurveyStore {
    public static final int CAP = 65_536;

    public static final byte CAUSE_HIT = 0;
    public static final byte CAUSE_BLAST = 1;
    public static final byte CAUSE_FIRE = 2;
    public static final byte CAUSE_PLAYER = 3;
    public static final byte CAUSE_SEPARATED = 4;

    public static final long INTACT = Long.MIN_VALUE;

    private static final int SYSTEMS = BattleSystem.values().length;

    private int size;
    private int[] pos = new int[256];
    private short[] pal = new short[256];
    private long[] lostAt = new long[256];
    private byte[] cause = new byte[256];
    private final Int2IntOpenHashMap slots = new Int2IntOpenHashMap();
    private final List<String> palette = new ArrayList<>();
    private final Map<String, Integer> paletteIndex = new HashMap<>();
    private byte[] paletteSystem = new byte[16];
    private final int[] total = new int[SYSTEMS];
    private final int[] lost = new int[SYSTEMS];
    private int lostCount;
    private int cursor;
    private long taken = -1L;

    public SurveyStore() {
        slots.defaultReturnValue(-1);
    }

    public int size() {
        return size;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    public boolean isFull() {
        return size >= CAP;
    }

    public int lostCount() {
        return lostCount;
    }

    public int intactCount() {
        return size - lostCount;
    }

    public long taken() {
        return taken;
    }

    public void setTaken(long gameTime) {
        this.taken = gameTime;
    }

    public int slotOf(int packed) {
        return slots.get(packed);
    }

    public int packedAt(int slot) {
        return pos[slot];
    }

    public int paletteAt(int slot) {
        return pal[slot];
    }

    public String blockAt(int slot) {
        return palette.get(pal[slot]);
    }

    public BattleSystem systemAt(int slot) {
        return BattleSystem.byOrdinal(paletteSystem[pal[slot]]);
    }

    public boolean isLost(int slot) {
        return lostAt[slot] != INTACT;
    }

    public long lostAt(int slot) {
        return lostAt[slot];
    }

    public byte causeAt(int slot) {
        return cause[slot];
    }

    public int total(BattleSystem s) {
        return total[s.ordinal()];
    }

    public int lost(BattleSystem s) {
        return lost[s.ordinal()];
    }

    public List<String> palette() {
        return List.copyOf(palette);
    }

    public int paletteSize() {
        return palette.size();
    }

    public String paletteId(int index) {
        return palette.get(index);
    }

    public BattleSystem paletteSystem(int index) {
        return BattleSystem.byOrdinal(paletteSystem[index]);
    }

    public int nextSlot() {
        if (size == 0) return -1;
        if (cursor >= size) cursor = 0;
        return cursor++;
    }

    public void clear() {
        size = 0;
        slots.clear();
        palette.clear();
        paletteIndex.clear();
        Arrays.fill(total, 0);
        Arrays.fill(lost, 0);
        lostCount = 0;
        cursor = 0;
        taken = -1L;
    }

    public int paletteOf(String blockId, BattleSystem system) {
        Integer known = paletteIndex.get(blockId);
        if (known != null) return known;
        int index = palette.size();
        if (index >= Short.MAX_VALUE) throw new IllegalStateException("palette full");
        palette.add(blockId);
        paletteIndex.put(blockId, index);
        if (index >= paletteSystem.length) paletteSystem = Arrays.copyOf(paletteSystem, paletteSystem.length * 2);
        paletteSystem[index] = (byte) system.ordinal();
        return index;
    }

    public int paletteIndexOf(String blockId) {
        Integer known = paletteIndex.get(blockId);
        return known == null ? -1 : known;
    }

    public void setPaletteSystem(int index, BattleSystem system) {
        byte old = paletteSystem[index];
        if (old == system.ordinal()) return;
        for (int i = 0; i < size; i++) {
            if (pal[i] != index) continue;
            total[old]--;
            total[system.ordinal()]++;
            if (lostAt[i] != INTACT) {
                lost[old]--;
                lost[system.ordinal()]++;
            }
        }
        paletteSystem[index] = (byte) system.ordinal();
    }

    public int add(int packed, String blockId, BattleSystem system) {
        if (size >= CAP || slots.containsKey(packed)) return -1;
        int p = paletteOf(blockId, system);
        grow(size + 1);
        pos[size] = packed;
        pal[size] = (short) p;
        lostAt[size] = INTACT;
        cause[size] = CAUSE_HIT;
        slots.put(packed, size);
        total[paletteSystem[p]]++;
        return size++;
    }

    public int load(int packed, int paletteIndex, long lostTime, byte lostCause) {
        if (size >= CAP || slots.containsKey(packed) || paletteIndex < 0 || paletteIndex >= palette.size()) return -1;
        grow(size + 1);
        pos[size] = packed;
        pal[size] = (short) paletteIndex;
        lostAt[size] = lostTime;
        cause[size] = lostCause;
        slots.put(packed, size);
        byte sys = paletteSystem[paletteIndex];
        total[sys]++;
        if (lostTime != INTACT) {
            lost[sys]++;
            lostCount++;
        }
        return size++;
    }

    public boolean markLost(int slot, long gameTime, byte why) {
        if (lostAt[slot] != INTACT) return false;
        lostAt[slot] = gameTime;
        cause[slot] = why;
        lost[paletteSystem[pal[slot]]]++;
        lostCount++;
        return true;
    }

    public void putBack(int slot, String blockId, BattleSystem system) {
        if (lostAt[slot] != INTACT) {
            lost[paletteSystem[pal[slot]]]--;
            lostCount--;
            lostAt[slot] = INTACT;
            cause[slot] = CAUSE_HIT;
        }
        adopt(slot, blockId, system);
    }

    public void adopt(int slot, String blockId, BattleSystem system) {
        int p = paletteOf(blockId, system);
        if (p == pal[slot]) return;
        byte oldSys = paletteSystem[pal[slot]];
        byte newSys = paletteSystem[p];
        total[oldSys]--;
        total[newSys]++;
        if (lostAt[slot] != INTACT) {
            lost[oldSys]--;
            lost[newSys]++;
        }
        pal[slot] = (short) p;
    }

    public void remove(int slot) {
        if (slot < 0 || slot >= size) return;
        byte sys = paletteSystem[pal[slot]];
        total[sys]--;
        if (lostAt[slot] != INTACT) {
            lost[sys]--;
            lostCount--;
        }
        slots.remove(pos[slot]);
        int last = size - 1;
        if (slot != last) {
            pos[slot] = pos[last];
            pal[slot] = pal[last];
            lostAt[slot] = lostAt[last];
            cause[slot] = cause[last];
            slots.put(pos[slot], slot);
        }
        size = last;
        if (cursor > size) cursor = 0;
    }

    public boolean removePacked(int packed) {
        int slot = slotOf(packed);
        if (slot < 0) return false;
        remove(slot);
        return true;
    }

    public int[] lostPositions() {
        int[] out = new int[lostCount];
        int n = 0;
        for (int i = 0; i < size && n < out.length; i++) {
            if (lostAt[i] != INTACT) out[n++] = pos[i];
        }
        return n == out.length ? out : Arrays.copyOf(out, n);
    }

    private void grow(int need) {
        if (need <= pos.length) return;
        int cap = Math.min(CAP, Math.max(need, pos.length * 2));
        pos = Arrays.copyOf(pos, cap);
        pal = Arrays.copyOf(pal, cap);
        lostAt = Arrays.copyOf(lostAt, cap);
        cause = Arrays.copyOf(cause, cap);
    }
}
