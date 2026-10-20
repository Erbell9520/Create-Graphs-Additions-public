package com.createstats.flight.control;

import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class BMatrix {
    private final Matrix6xN matrix;
    private final double[] verticalAuthorityPerRpm;
    private final double[] rowNorms;
    private final long fingerprint;
    private final boolean gravityFallbackUsed;
    private final List<Diagnostic> perPropDiagnostics;
    @Nullable private final String unable;

    public BMatrix(Matrix6xN matrix,
                   double[] verticalAuthorityPerRpm,
                   double[] rowNorms,
                   long fingerprint,
                   boolean gravityFallbackUsed,
                   List<Diagnostic> perPropDiagnostics,
                   @Nullable String unable) {
        if (rowNorms.length != Matrix6xN.ROWS)
            throw new IllegalArgumentException("rowNorms length != 6");
        if (verticalAuthorityPerRpm.length != matrix.cols())
            throw new IllegalArgumentException("verticalAuthority length != matrix.cols()");
        this.matrix = matrix;
        this.verticalAuthorityPerRpm = verticalAuthorityPerRpm.clone();
        this.rowNorms = rowNorms.clone();
        this.fingerprint = fingerprint;
        this.gravityFallbackUsed = gravityFallbackUsed;
        this.perPropDiagnostics = List.copyOf(perPropDiagnostics);
        this.unable = unable;
    }

    public Matrix6xN matrix() { return matrix; }
    public int cols() { return matrix.cols(); }
    public double[] verticalAuthorityPerRpm() { return verticalAuthorityPerRpm.clone(); }
    public double verticalAuthorityFor(int col) { return verticalAuthorityPerRpm[col]; }
    public double[] rowNorms() { return rowNorms.clone(); }
    public long fingerprint() { return fingerprint; }
    public boolean gravityFallbackUsed() { return gravityFallbackUsed; }
    public List<Diagnostic> perPropDiagnostics() { return perPropDiagnostics; }
    @Nullable public String unable() { return unable; }
    public boolean usable() { return unable == null && matrix.cols() > 0; }

    public double maxLiftAuthority(double maxAbsRpm) {
        double s = 0.0;
        for (double v : verticalAuthorityPerRpm) {
            s += Math.abs(v) * maxAbsRpm;
        }
        return s;
    }

    public double maxRowAuthority(int row, double maxAbsRpm) {
        double s = 0.0;
        for (int c = 0; c < matrix.cols(); c++) {
            s += Math.abs(matrix.get(row, c));
        }
        return s * maxAbsRpm;
    }

    public record Diagnostic(int col,
                              boolean valid,
                              @Nullable String reason,
                              double effectiveK,
                              boolean usedStaticFallback,
                              boolean usedCalibration,
                              double yawFactor,
                              double reactionTorquePerRpm) {}

    public static BMatrix unable(String reason) {
        return new BMatrix(Matrix6xN.empty(),
                new double[0],
                new double[Matrix6xN.ROWS],
                0L,
                false,
                List.of(),
                reason);
    }
}
