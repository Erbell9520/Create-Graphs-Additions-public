package com.createstats.flight.control;

/** [1] */
public final class Matrix6xN {

    public static final int ROWS = 6;
    public static final int FX = 0;
    public static final int FY = 1;
    public static final int FZ = 2;
    public static final int TX = 3;
    public static final int TY = 4;
    public static final int TZ = 5;

    private final int cols;
    private final double[] data;

    private Matrix6xN(int cols, double[] columnMajor, boolean takeOwnership) {
        if (cols < 0) throw new IllegalArgumentException("cols < 0");
        if (columnMajor.length != cols * ROWS)
            throw new IllegalArgumentException("data length mismatch: " + columnMajor.length + " != " + (cols * ROWS));
        this.cols = cols;
        this.data = takeOwnership ? columnMajor : columnMajor.clone();
    }

    public static Matrix6xN of(int cols, double[] columnMajorCopy) {
        return new Matrix6xN(cols, columnMajorCopy, false);
    }

    public static Matrix6xN empty() {
        return new Matrix6xN(0, new double[0], true);
    }

    public int cols() { return cols; }

    public double get(int row, int col) {
        check(row, col);
        return data[col * ROWS + row];
    }

    /** Copy column {@code col} into {@code out6} (length must be 6). */
    public void copyColumn(int col, double[] out6) {
        if (col < 0 || col >= cols) throw new IndexOutOfBoundsException("col=" + col);
        if (out6.length != 6) throw new IllegalArgumentException("out6 length != 6");
        System.arraycopy(data, col * ROWS, out6, 0, ROWS);
    }

    /** L2 norm of the given row. */
    public double rowNorm(int row) {
        if (row < 0 || row >= ROWS) throw new IndexOutOfBoundsException("row=" + row);
        double s = 0.0;
        for (int c = 0; c < cols; c++) {
            double v = data[c * ROWS + row];
            s += v * v;
        }
        return Math.sqrt(s);
    }

    public double[] rowNorms() {
        double[] out = new double[ROWS];
        for (int r = 0; r < ROWS; r++) out[r] = rowNorm(r);
        return out;
    }

    /** L2 norm of column {@code col}. */
    public double columnNorm(int col) {
        if (col < 0 || col >= cols) throw new IndexOutOfBoundsException("col=" + col);
        double s = 0.0;
        int base = col * ROWS;
        for (int r = 0; r < ROWS; r++) {
            double v = data[base + r];
            s += v * v;
        }
        return Math.sqrt(s);
    }

    /** {@code out6 = B * x}. */
    public void multiply(double[] x, double[] out6) {
        if (x.length != cols) throw new IllegalArgumentException("x length != cols");
        if (out6.length != 6) throw new IllegalArgumentException("out6 length != 6");
        for (int r = 0; r < ROWS; r++) out6[r] = 0.0;
        for (int c = 0; c < cols; c++) {
            double xc = x[c];
            if (xc == 0.0) continue;
            int base = c * ROWS;
            for (int r = 0; r < ROWS; r++) out6[r] += data[base + r] * xc;
        }
    }

    /** {@code outN = B^T * w6}. */
    public void transposeMultiply(double[] w6, double[] outN) {
        if (w6.length != 6) throw new IllegalArgumentException("w6 length != 6");
        if (outN.length != cols) throw new IllegalArgumentException("outN length != cols");
        for (int c = 0; c < cols; c++) {
            int base = c * ROWS;
            double s = 0.0;
            for (int r = 0; r < ROWS; r++) s += data[base + r] * w6[r];
            outN[c] = s;
        }
    }

    /** Defensive copy of underlying storage. */
    public double[] toArrayColumnMajor() { return data.clone(); }

    private void check(int row, int col) {
        if (row < 0 || row >= ROWS) throw new IndexOutOfBoundsException("row=" + row);
        if (col < 0 || col >= cols) throw new IndexOutOfBoundsException("col=" + col);
    }

    /** Builder accumulates one column at a time then hands off to an immutable matrix. */
    public static final class Builder {
        private final int cols;
        private final double[] data;
        private final boolean[] written;

        public Builder(int cols) {
            if (cols < 0) throw new IllegalArgumentException("cols < 0");
            this.cols = cols;
            this.data = new double[cols * ROWS];
            this.written = new boolean[cols];
        }

        public Builder setColumn(int col, double fx, double fy, double fz,
                                 double tx, double ty, double tz) {
            if (col < 0 || col >= cols) throw new IndexOutOfBoundsException("col=" + col);
            int base = col * ROWS;
            data[base + FX] = fx;
            data[base + FY] = fy;
            data[base + FZ] = fz;
            data[base + TX] = tx;
            data[base + TY] = ty;
            data[base + TZ] = tz;
            written[col] = true;
            return this;
        }

        /** Sets a column to all zeros (used for failed/invalid props). */
        public Builder zeroColumn(int col) {
            return setColumn(col, 0, 0, 0, 0, 0, 0);
        }

        public int cols() { return cols; }

        public boolean isWritten(int col) {
            if (col < 0 || col >= cols) throw new IndexOutOfBoundsException("col=" + col);
            return written[col];
        }

        public Matrix6xN build() {
            for (int c = 0; c < cols; c++) {
                if (!written[c]) zeroColumn(c);
            }
            return new Matrix6xN(cols, data, true);
        }
    }
}
