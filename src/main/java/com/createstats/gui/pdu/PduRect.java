package com.createstats.gui.pdu;

public record PduRect(int x, int y, int w, int h) {
    public int right() {
        return x + w;
    }

    public int bottom() {
        return y + h;
    }

    public int centerX() {
        return x + w / 2;
    }

    public int centerY() {
        return y + h / 2;
    }

    public boolean contains(double mx, double my) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    public PduRect inset(int n) {
        return new PduRect(x + n, y + n, Math.max(0, w - 2 * n), Math.max(0, h - 2 * n));
    }

    public PduRect inset(int dx, int dy) {
        return new PduRect(x + dx, y + dy, Math.max(0, w - 2 * dx), Math.max(0, h - 2 * dy));
    }
}
