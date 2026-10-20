package com.createstats.vcu.core;

// one allocator for any number of thrusters, not a gain per diagram zone
public final class Allocator {
    public static final double MOMENT_PRIORITY = 1000.0;

    private Allocator() {
    }

    public record Result(double[] commands, double[] achieved, int pinned) {
    }

    public static Result fly(double[][] b, double[] demand, double[] axisScale, double[] lo, double[] hi) {
        double[] pos = authorityPositive(b, lo, hi);
        double[] neg = authorityNegative(b, lo, hi);
        double[] target = new double[Axis6.COUNT];
        double[] weight = new double[Axis6.COUNT];
        for (int a = 0; a < Axis6.COUNT; a++) {
            double d = clamp(demand[a], -1.0, 1.0) * axisScale[a];
            target[a] = d >= 0.0 ? d * pos[a] : d * neg[a];
            double scale = Math.max(pos[a], neg[a]);
            weight[a] = scale > 1.0e-9 ? 1.0 / (scale * scale) : 0.0;
            if (Axis6.values()[a].isMoment()) weight[a] *= MOMENT_PRIORITY;
        }
        return solve(b, target, weight, lo, hi);
    }

    public static Result solve(double[][] b, double[] target, double[] weight, double[] lo, double[] hi) {
        int n = lo.length;
        double[] u = new double[n];
        if (n == 0) return new Result(u, new double[target.length], 0);

        double[][] h = new double[n][n];
        double[] g = new double[n];
        double trace = 0.0;
        for (int p = 0; p < n; p++) {
            for (int q = 0; q < n; q++) {
                double s = 0.0;
                for (int a = 0; a < target.length; a++) s += weight[a] * b[a][p] * b[a][q];
                h[p][q] = s;
            }
            double s = 0.0;
            for (int a = 0; a < target.length; a++) s += weight[a] * b[a][p] * target[a];
            g[p] = s;
            trace += h[p][p];
        }

        double lambda = trace > 0.0 ? 1.0e-9 * trace / n : 1.0e-12;
        for (int p = 0; p < n; p++) h[p][p] += lambda;

        int[] state = new int[n];
        for (int j = 0; j < n; j++) u[j] = clamp(0.0, lo[j], hi[j]);

        for (int iter = 0; iter < 8 * n + 8; iter++) {
            int[] free = indicesWithState(state, 0);
            double[] x = free.length == 0 ? new double[0] : solveFree(h, g, u, free);

            boolean inside = true;
            for (int p = 0; p < free.length; p++) {
                int j = free[p];
                if (x[p] < lo[j] - 1.0e-12 || x[p] > hi[j] + 1.0e-12) {
                    inside = false;
                    break;
                }
            }

            if (inside) {
                for (int p = 0; p < free.length; p++) u[free[p]] = x[p];

                int release = -1;
                double worst = 1.0e-12;
                for (int j = 0; j < n; j++) {
                    if (state[j] == 0) continue;
                    double grad = -g[j];
                    for (int q = 0; q < n; q++) grad += h[j][q] * u[q];
                    double pull = state[j] < 0 ? -grad : grad;
                    if (pull > worst) {
                        worst = pull;
                        release = j;
                    }
                }
                if (release < 0) break;
                state[release] = 0;
                continue;
            }

            double alpha = 1.0;
            for (int p = 0; p < free.length; p++) {
                int j = free[p];
                double step = x[p] - u[j];
                if (x[p] < lo[j] && step < 0.0) alpha = Math.min(alpha, (lo[j] - u[j]) / step);
                if (x[p] > hi[j] && step > 0.0) alpha = Math.max(0.0, Math.min(alpha, (hi[j] - u[j]) / step));
            }
            alpha = clamp(alpha, 0.0, 1.0);
            for (int p = 0; p < free.length; p++) {
                int j = free[p];
                u[j] += alpha * (x[p] - u[j]);
                if (u[j] <= lo[j] + 1.0e-12 && x[p] < lo[j]) {
                    u[j] = lo[j];
                    state[j] = -1;
                } else if (u[j] >= hi[j] - 1.0e-12 && x[p] > hi[j]) {
                    u[j] = hi[j];
                    state[j] = 1;
                }
            }
        }

        int held = 0;
        for (int j = 0; j < n; j++) {
            u[j] = clamp(u[j], lo[j], hi[j]);
            if (state[j] != 0) held++;
        }
        return new Result(u, multiply(b, u), held);
    }

    public static double[] authorityPositive(double[][] b, double[] lo, double[] hi) {
        double[] out = new double[b.length];
        for (int a = 0; a < b.length; a++) {
            for (int j = 0; j < lo.length; j++) {
                out[a] += Math.max(0.0, Math.max(b[a][j] * lo[j], b[a][j] * hi[j]));
            }
        }
        return out;
    }

    public static double[] authorityNegative(double[][] b, double[] lo, double[] hi) {
        double[] out = new double[b.length];
        for (int a = 0; a < b.length; a++) {
            for (int j = 0; j < lo.length; j++) {
                out[a] += Math.max(0.0, -Math.min(b[a][j] * lo[j], b[a][j] * hi[j]));
            }
        }
        return out;
    }

    public static double[] multiply(double[][] b, double[] u) {
        double[] out = new double[b.length];
        for (int a = 0; a < b.length; a++) {
            for (int j = 0; j < u.length; j++) {
                out[a] += b[a][j] * u[j];
            }
        }
        return out;
    }

    private static double[] solveFree(double[][] h, double[] g, double[] u, int[] free) {
        int m = free.length;
        boolean[] isFree = new boolean[u.length];
        for (int j : free) isFree[j] = true;
        double[][] aug = new double[m][m + 1];
        for (int p = 0; p < m; p++) {
            int i = free[p];
            for (int q = 0; q < m; q++) aug[p][q] = h[i][free[q]];
            double rhs = g[i];
            for (int k = 0; k < u.length; k++) {
                if (!isFree[k]) rhs -= h[i][k] * u[k];
            }
            aug[p][m] = rhs;
        }
        return gauss(aug, m);
    }

    private static double[] gauss(double[][] h, int m) {
        for (int col = 0; col < m; col++) {
            int pivot = col;
            for (int r = col + 1; r < m; r++) {
                if (Math.abs(h[r][col]) > Math.abs(h[pivot][col])) pivot = r;
            }
            double[] swap = h[col];
            h[col] = h[pivot];
            h[pivot] = swap;
            double diag = h[col][col];
            if (Math.abs(diag) < 1.0e-15) continue;
            for (int r = col + 1; r < m; r++) {
                double f = h[r][col] / diag;
                if (f == 0.0) continue;
                for (int c = col; c <= m; c++) {
                    h[r][c] -= f * h[col][c];
                }
            }
        }
        double[] x = new double[m];
        for (int r = m - 1; r >= 0; r--) {
            double diag = h[r][r];
            if (Math.abs(diag) < 1.0e-15) {
                x[r] = 0.0;
                continue;
            }
            double s = h[r][m];
            for (int c = r + 1; c < m; c++) {
                s -= h[r][c] * x[c];
            }
            x[r] = s / diag;
        }
        return x;
    }

    private static int[] indicesWithState(int[] state, int wanted) {
        int count = 0;
        for (int s : state) {
            if (s == wanted) count++;
        }
        int[] out = new int[count];
        int k = 0;
        for (int j = 0; j < state.length; j++) {
            if (state[j] == wanted) out[k++] = j;
        }
        return out;
    }

    public static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}
