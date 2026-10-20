package com.createstats.stormcoil;

import java.util.ArrayList;
import java.util.List;

public final class CoilGaze {
    public static final int MIN = 3;

    public static final int MAX = CoilWatch.MAX;

    public static final int FADE_TICKS = 6;

    public static final double EASE_TARGET = 0.45;
    public static final double EASE_IGNORED = 0.14;
    public static final double EASE_SPARED = 0.08;
    public static final double EASE_FREE = 0.07;
    public static final double EASE_GATHER = 0.2;

    public enum Kind {
        FREE, TARGET, SPARED, IGNORED;

        static Kind of(CoilWatch.Verdict v) {
            return switch (v) {
                case TARGET -> TARGET;
                case SPARED -> SPARED;
                case IGNORED -> IGNORED;
            };
        }
    }

    public record Look(int id, CoilWatch.Verdict verdict, double x, double y, double z) {
    }

    public record Strand(Kind kind, int id, double x, double y, double z, double weight, int seed, boolean aimed) {
    }

    private static final class Filament {
        final int seed;
        int id = -1;
        Kind kind = Kind.FREE;
        boolean aimed;
        boolean leaving;
        double x;
        double y = 1;
        double z;
        double bx;
        double by = 1;
        double bz;
        double weight;
        double weightBefore;

        Filament(int seed, double[] start) {
            this.seed = seed;
            x = bx = start[0];
            y = by = start[1];
            z = bz = start[2];
        }
    }

    private final List<Filament> strands = new ArrayList<>();
    private int nextSeed;

    public void tick(long now, List<Look> looks, int[] aimed, boolean up) {
        final List<Look> use = looks.size() > MAX ? looks.subList(0, MAX) : looks;
        for (Filament f : strands) {
            f.bx = f.x;
            f.by = f.y;
            f.bz = f.z;
            f.weightBefore = f.weight;
        }
        strands.removeIf(f -> f.leaving && f.weight <= 0.0);

        for (Filament f : strands) {
            if (f.id >= 0 && find(use, f.id) == null) {
                f.id = -1;
                f.kind = Kind.FREE;
            }
        }
        for (Look l : use) {
            Filament mine = null;
            for (Filament f : strands) {
                if (!f.leaving && f.id == l.id()) {
                    mine = f;
                }
            }
            if (mine == null) {
                mine = nearestFree(unit(l.x(), l.y(), l.z()));
            }
            if (mine == null) {
                mine = grow(unit(l.x(), l.y(), l.z()));
            }
            mine.id = l.id();
            mine.kind = Kind.of(l.verdict());
            mine.leaving = false;
        }

        final int want = Math.max(MIN, Math.min(MAX, use.size()));
        int active = 0;
        for (Filament f : strands) {
            if (!f.leaving) {
                active++;
            }
        }
        for (int i = strands.size() - 1; i >= 0 && active > want; i--) {
            final Filament f = strands.get(i);
            if (!f.leaving && f.id < 0) {
                f.leaving = true;
                active--;
            }
        }
        while (active < want) {
            grow(wander(nextSeed, now));
            active++;
        }

        final Look nearestTarget = nearestTarget(use);
        final List<Look> aimedLooks = new ArrayList<>();
        for (int id : aimed) {
            final Look l = find(use, id);
            if (l != null) {
                aimedLooks.add(l);
            }
        }
        int freeIndex = 0;
        for (Filament f : strands) {
            f.aimed = f.id >= 0 && contains(aimed, f.id);
            final double[] wantDir;
            final double ease;
            final Look l = f.id >= 0 ? find(use, f.id) : null;
            if (l != null && f.kind == Kind.TARGET) {
                wantDir = wobble(unit(l.x(), l.y(), l.z()), 0.05, f.seed, now, 0.35);
                ease = EASE_TARGET;
            } else if (l != null && up) {
                wantDir = wobble(new double[] {0, 1, 0}, 0.3, f.seed, now, 0.2);
                ease = EASE_GATHER;
            } else if (l != null && f.kind == Kind.SPARED) {
                wantDir = wobble(unit(l.x(), l.y(), l.z()), 0.24, f.seed, now, 0.045);
                ease = EASE_SPARED;
            } else if (l != null) {
                wantDir = wobble(unit(l.x(), l.y(), l.z()), 0.4, f.seed, now, 0.08);
                ease = EASE_IGNORED;
            } else if (!aimedLooks.isEmpty()) {
                final Look a = aimedLooks.get(freeIndex++ % aimedLooks.size());
                wantDir = wobble(unit(a.x(), a.y(), a.z()), 0.22, f.seed, now, 0.4);
                ease = EASE_GATHER;
            } else if (up) {
                wantDir = wobble(new double[] {0, 1, 0}, 0.3, f.seed, now, 0.2);
                ease = EASE_GATHER;
            } else if (nearestTarget != null) {
                wantDir = wobble(unit(nearestTarget.x(), nearestTarget.y(), nearestTarget.z()), 0.38, f.seed, now,
                        0.12);
                ease = EASE_GATHER;
            } else {
                wantDir = wander(f.seed, now);
                ease = EASE_FREE;
            }
            final double[] d = unit(f.x + (wantDir[0] - f.x) * ease, f.y + (wantDir[1] - f.y) * ease,
                    f.z + (wantDir[2] - f.z) * ease);
            f.x = d[0];
            f.y = d[1];
            f.z = d[2];
            f.weight = Math.max(0.0, Math.min(1.0, f.weight + (f.leaving ? -1.0 : 1.0) / FADE_TICKS));
        }
    }

    public List<Strand> strands(float partial) {
        final List<Strand> out = new ArrayList<>(strands.size());
        for (Filament f : strands) {
            final double[] d = unit(f.bx + (f.x - f.bx) * partial, f.by + (f.y - f.by) * partial,
                    f.bz + (f.z - f.bz) * partial);
            final double w = f.weightBefore + (f.weight - f.weightBefore) * partial;
            out.add(new Strand(f.kind, f.id, d[0], d[1], d[2], w, f.seed, f.aimed));
        }
        return out;
    }

    public static double[] wander(int seed, long now) {
        final double t = now;
        final double s = seed * 2.399963;
        final double around = s + t * 0.021 * (seed % 2 == 0 ? 1 : -1) + 0.7 * Math.sin(t * 0.043 + s * 1.3);
        final double lift = 0.25 + 0.45 * Math.sin(t * 0.037 + s * 0.7) * Math.cos(t * 0.011 + s);
        final double c = Math.cos(lift);
        return new double[] {Math.cos(around) * c, Math.sin(lift), Math.sin(around) * c};
    }

    static double[] wobble(double[] dir, double amount, int seed, long now, double rate) {
        if (amount <= 0) {
            return dir;
        }

        final double[] a = Math.abs(dir[1]) < 0.9 ? new double[] {0, 1, 0} : new double[] {1, 0, 0};
        final double[] u = unit(dir[1] * a[2] - dir[2] * a[1], dir[2] * a[0] - dir[0] * a[2], dir[0] * a[1] - dir[1] * a[0]);
        final double[] v = {dir[1] * u[2] - dir[2] * u[1], dir[2] * u[0] - dir[0] * u[2], dir[0] * u[1] - dir[1] * u[0]};
        final double ph = now * rate + seed * 1.618;
        final double r = amount * (0.6 + 0.4 * Math.sin(ph * 0.73 + seed));
        final double cu = Math.cos(ph) * r;
        final double cv = Math.sin(ph * 1.31) * r;
        return unit(dir[0] + u[0] * cu + v[0] * cv, dir[1] + u[1] * cu + v[1] * cv, dir[2] + u[2] * cu + v[2] * cv);
    }

    private Filament grow(double[] start) {
        final Filament f = new Filament(nextSeed++, start);
        strands.add(f);
        return f;
    }

    private Filament nearestFree(double[] dir) {
        Filament best = null;
        double bestDot = -2;
        for (Filament f : strands) {
            if (!f.leaving && f.id < 0) {
                final double dot = f.x * dir[0] + f.y * dir[1] + f.z * dir[2];
                if (dot > bestDot) {
                    bestDot = dot;
                    best = f;
                }
            }
        }
        return best;
    }

    private static Look find(List<Look> looks, int id) {
        for (Look l : looks) {
            if (l.id() == id) {
                return l;
            }
        }
        return null;
    }

    private static Look nearestTarget(List<Look> looks) {
        Look best = null;
        double bestD = Double.MAX_VALUE;
        for (Look l : looks) {
            final double d = l.x() * l.x() + l.y() * l.y() + l.z() * l.z();
            if (l.verdict() == CoilWatch.Verdict.TARGET && d < bestD) {
                bestD = d;
                best = l;
            }
        }
        return best;
    }

    private static boolean contains(int[] ids, int id) {
        for (int i : ids) {
            if (i == id) {
                return true;
            }
        }
        return false;
    }

    static double[] unit(double x, double y, double z) {
        final double l = Math.sqrt(x * x + y * y + z * z);
        if (l < 1e-9) {
            return new double[] {0, 1, 0};
        }
        return new double[] {x / l, y / l, z / l};
    }
}
