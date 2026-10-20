package com.createstats.statsmk3.layout;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

public final class Mk3Layout {
    private final List<Tile> tiles = new ArrayList<>();
    private int cols = GridSpec.CELLS_PER_BLOCK;
    private int rows = GridSpec.CELLS_PER_BLOCK;

    public String presetId = "";

    public int cols() {
        return this.cols;
    }

    public int rows() {
        return this.rows;
    }

    public List<Tile> tiles() {
        return this.tiles;
    }

    public int size() {
        return this.tiles.size();
    }

    public boolean isEmpty() {
        return this.tiles.isEmpty();
    }

    public Tile get(int index) {
        return index >= 0 && index < this.tiles.size() ? this.tiles.get(index) : null;
    }

    public int indexOf(Tile tile) {
        return this.tiles.indexOf(tile);
    }

    public void clear() {
        this.tiles.clear();
        this.presetId = "";
    }

    public Mk3Layout copy() {
        final Mk3Layout out = new Mk3Layout();
        out.cols = this.cols;
        out.rows = this.rows;
        out.presetId = this.presetId;
        for (final Tile t : this.tiles) {
            out.tiles.add(t.copy());
        }
        return out;
    }

    public Tile add(Tile tile) {
        if (tile == null || this.tiles.size() >= GridSpec.MAX_TILES || !fits(tile)
                || collides(tile, null)) {
            return null;
        }
        this.tiles.add(tile);
        this.presetId = "";
        return tile;
    }

    public boolean remove(Tile tile) {
        final boolean removed = this.tiles.remove(tile);
        if (removed) {
            this.presetId = "";
        }
        return removed;
    }

    public boolean canPlace(int gx, int gy, int gw, int gh, Tile ignore) {
        final Tile probe = new Tile(gx, gy, gw, gh);
        return fits(probe) && !collides(probe, ignore);
    }

    public boolean place(Tile tile, int gx, int gy, int gw, int gh) {
        if (tile == null || !canPlace(gx, gy, gw, gh, tile)) {
            return false;
        }
        if (tile.gx == gx && tile.gy == gy && tile.gw == Math.max(1, gw)
                && tile.gh == Math.max(1, gh)) {
            return false;
        }
        tile.gx = gx;
        tile.gy = gy;
        tile.gw = Math.max(1, gw);
        tile.gh = Math.max(1, gh);
        this.presetId = "";
        return true;
    }

    public Tile tileAtCell(int cx, int cy) {
        for (final Tile t : this.tiles) {
            if (t.containsCell(cx, cy)) {
                return t;
            }
        }
        return null;
    }

    public Tile freeTileAt(int cx, int cy, int preferW, int preferH) {
        if (cx < 0 || cy < 0 || cx >= this.cols || cy >= this.rows || tileAtCell(cx, cy) != null) {
            return null;
        }
        int w = 1;
        while (w < preferW && cx + w < this.cols && isColumnFree(cx + w, cy, 1)) {
            w++;
        }
        int h = 1;
        while (h < preferH && cy + h < this.rows && isRowFree(cx, cy + h, w)) {
            h++;
        }
        return new Tile(cx, cy, w, h);
    }

    private boolean isColumnFree(int cx, int cy, int h) {
        for (int y = cy; y < cy + h; y++) {
            if (tileAtCell(cx, y) != null) {
                return false;
            }
        }
        return true;
    }

    private boolean isRowFree(int cx, int cy, int w) {
        for (int x = cx; x < cx + w; x++) {
            if (tileAtCell(x, cy) != null) {
                return false;
            }
        }
        return true;
    }

    public void reorder(int from, int to) {
        if (from < 0 || from >= this.tiles.size() || to < 0 || to >= this.tiles.size()
                || from == to) {
            return;
        }
        this.tiles.add(to, this.tiles.remove(from));
        this.presetId = "";
    }

    public Tile duplicate(Tile source) {
        if (source == null || this.tiles.size() >= GridSpec.MAX_TILES) {
            return null;
        }
        final int w = source.gw;
        final int h = source.gh;
        final int[][] preferred = {
                {source.right(), source.gy},
                {source.gx, source.bottom()},
                {source.gx - w, source.gy},
                {source.gx, source.gy - h},
        };
        for (final int[] at : preferred) {
            if (canPlace(at[0], at[1], w, h, null)) {
                return add(copyOf(source, at[0], at[1]));
            }
        }
        for (int y = 0; y + h <= this.rows; y++) {
            for (int x = 0; x + w <= this.cols; x++) {
                if (canPlace(x, y, w, h, null)) {
                    return add(copyOf(source, x, y));
                }
            }
        }
        return null;
    }

    private static Tile copyOf(Tile source, int gx, int gy) {
        final Tile t = source.copy();
        t.gx = gx;
        t.gy = gy;
        return t;
    }

    private boolean fits(Tile t) {
        return t.gx >= 0 && t.gy >= 0 && t.gw >= 1 && t.gh >= 1
                && t.right() <= this.cols && t.bottom() <= this.rows;
    }

    private boolean collides(Tile probe, Tile ignore) {
        for (final Tile t : this.tiles) {
            if (t != ignore && t.overlaps(probe)) {
                return true;
            }
        }
        return false;
    }

    public boolean fitTo(int newCols, int newRows) {
        final int c = Math.max(1, newCols);
        final int r = Math.max(1, newRows);
        if (c == this.cols && r == this.rows) {
            return false;
        }
        final float sx = c / (float) this.cols;
        final float sy = r / (float) this.rows;
        final int oldCols = this.cols;
        final int oldRows = this.rows;
        this.cols = c;
        this.rows = r;

        for (final Tile t : this.tiles) {
            final int x0 = Math.round(t.gx * sx);
            final int y0 = Math.round(t.gy * sy);
            final int x1 = t.right() >= oldCols ? c : Math.round(t.right() * sx);
            final int y1 = t.bottom() >= oldRows ? r : Math.round(t.bottom() * sy);
            t.gx = clamp(x0, 0, c - 1);
            t.gy = clamp(y0, 0, r - 1);
            t.gw = clamp(x1 - t.gx, 1, c - t.gx);
            t.gh = clamp(y1 - t.gy, 1, r - t.gy);
        }
        dropOverlaps();
        return true;
    }

    public int tilesLostFitting(int cols, int rows) {
        final Mk3Layout probe = copy();
        probe.fitTo(cols, rows);
        return this.tiles.size() - probe.tiles.size();
    }

    public void normalise() {
        this.cols = clamp(this.cols, 1, GridSpec.MAX_COLS);
        this.rows = clamp(this.rows, 1, GridSpec.MAX_ROWS);
        for (final Tile t : this.tiles) {
            t.gx = clamp(t.gx, 0, this.cols - 1);
            t.gy = clamp(t.gy, 0, this.rows - 1);
            t.gw = clamp(t.gw, 1, this.cols - t.gx);
            t.gh = clamp(t.gh, 1, this.rows - t.gy);
        }
        dropOverlaps();
        while (this.tiles.size() > GridSpec.MAX_TILES) {
            this.tiles.remove(this.tiles.size() - 1);
        }
    }

    private void dropOverlaps() {
        for (int i = this.tiles.size() - 1; i >= 0; i--) {
            for (int j = 0; j < i; j++) {
                if (this.tiles.get(i).overlaps(this.tiles.get(j))) {
                    this.tiles.remove(i);
                    break;
                }
            }
        }
    }

    public void save(CompoundTag parent) {
        final CompoundTag t = new CompoundTag();
        t.putByte("Cols", (byte) this.cols);
        t.putByte("Rows", (byte) this.rows);
        if (!this.presetId.isEmpty()) {
            t.putString("Preset", this.presetId);
        }
        final ListTag list = new ListTag();
        for (final Tile tile : this.tiles) {
            list.add(tile.save());
        }
        t.put("Tiles", list);
        parent.put("Layout", t);
    }

    public void load(CompoundTag parent) {
        if (!parent.contains("Layout")) {
            return;
        }
        final CompoundTag t = parent.getCompound("Layout");
        this.cols = t.contains("Cols") ? t.getByte("Cols") : GridSpec.CELLS_PER_BLOCK;
        this.rows = t.contains("Rows") ? t.getByte("Rows") : GridSpec.CELLS_PER_BLOCK;
        this.presetId = t.contains("Preset") ? t.getString("Preset") : "";
        this.tiles.clear();
        final ListTag list = t.getList("Tiles", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size() && i < GridSpec.MAX_TILES; i++) {
            this.tiles.add(Tile.load(list.getCompound(i)));
        }
        normalise();
    }

    public static Mk3Layout onGrid(int cols, int rows) {
        final Mk3Layout out = new Mk3Layout();
        out.cols = clamp(cols, 1, GridSpec.MAX_COLS);
        out.rows = clamp(rows, 1, GridSpec.MAX_ROWS);
        return out;
    }

    void addRaw(Tile tile) {
        if (tile != null && this.tiles.size() < GridSpec.MAX_TILES) {
            this.tiles.add(tile);
        }
    }

    void setPreset(String id) {
        this.presetId = id == null ? "" : id;
    }

    private static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : Math.min(v, hi);
    }
}
