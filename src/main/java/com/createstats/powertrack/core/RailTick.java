package com.createstats.powertrack.core;

import com.createstats.power.PortableFeDirectionLock;
import com.createstats.power.PortableFeTransfer;

import java.util.ArrayList;
import java.util.List;

// one section per car per tick or a car straddling an insulator is a wire
public final class RailTick {
    public record CarIn(long train, int[] shoeSections, int stored, int capacity, boolean mayCharge,
                        boolean mayDischarge) {
    }

    public record FeederIn(int section, SectionExchange.Feeder feeder) {
    }

    public record Result(int[] feederDelta, int[] carDelta, int[] carSection, SectionExchange.Plan[] plans) {
    }

    private RailTick() {
    }

    public static int[] settle(int[] feederDelta, int[] planned, int[] actual) {
        return settle(feederDelta, new int[feederDelta.length], planned, actual, new int[planned.length]);
    }

    public static int[] settle(int[] feederDelta, int[] feederSection, int[] planned, int[] actual,
                               int[] carSection) {
        final int[] out = feederDelta.clone();
        final java.util.TreeMap<Integer, Long> bySection = new java.util.TreeMap<>();
        for (int j = 0; j < planned.length; j++) {
            if (planned[j] != actual[j]) {
                bySection.merge(carSection[j], (long) planned[j] - actual[j], Long::sum);
            }
        }
        for (java.util.Map.Entry<Integer, Long> en : bySection.entrySet()) {
            final int s = en.getKey();
            long diff = en.getValue();
            for (int i = 0; i < out.length && diff > 0; i++) {
                if (feederSection[i] == s && out[i] < 0) {
                    final long back = Math.min(diff, -(long) out[i]);
                    out[i] += (int) back;
                    diff -= back;
                }
            }
            for (int i = 0; i < out.length && diff < 0; i++) {
                if (feederSection[i] == s && out[i] > 0) {
                    final long take = Math.min(-diff, out[i]);
                    out[i] -= (int) take;
                    diff += take;
                }
            }
        }
        return out;
    }

    public static int sectionOf(int[] shoeSections) {
        int best = -1;
        int bestCount = 0;
        for (int i = 0; i < shoeSections.length; i++) {
            final int s = shoeSections[i];
            if (s < 0) {
                continue;
            }
            int count = 0;
            for (int t : shoeSections) {
                if (t == s) {
                    count++;
                }
            }
            if (count > bestCount) {
                best = s;
                bestCount = count;
            }
        }
        return best;
    }

    public static int shoesOn(int[] shoeSections, int section) {
        int n = 0;
        for (int s : shoeSections) {
            if (s == section && section >= 0) {
                n++;
            }
        }
        return n;
    }

    public static Result run(List<FeederIn> feeders, List<CarIn> cars, int sectionCount, int ceiling,
                             int collectorRate, PortableFeDirectionLock trainLock, long now) {
        final int[] fd = new int[feeders.size()];
        final int[] cd = new int[cars.size()];
        final int[] carSection = new int[cars.size()];
        final SectionExchange.Plan[] plans = new SectionExchange.Plan[Math.max(0, sectionCount)];

        final java.util.TreeMap<Integer, List<Integer>> feedersOn = new java.util.TreeMap<>();
        final java.util.TreeMap<Integer, List<Integer>> carsOn = new java.util.TreeMap<>();
        for (int i = 0; i < feeders.size(); i++) {
            final int s = feeders.get(i).section();
            if (s >= 0 && s < sectionCount) {
                feedersOn.computeIfAbsent(s, x -> new ArrayList<>()).add(i);
            }
        }
        for (int j = 0; j < cars.size(); j++) {
            carSection[j] = sectionOf(cars.get(j).shoeSections());
            if (carSection[j] >= 0 && carSection[j] < sectionCount) {
                carsOn.computeIfAbsent(carSection[j], x -> new ArrayList<>()).add(j);
            }
        }
        final java.util.TreeSet<Integer> touched = new java.util.TreeSet<>(feedersOn.keySet());
        touched.addAll(carsOn.keySet());
        for (int s : touched) {
            final List<Integer> fIdx = feedersOn.getOrDefault(s, List.of());
            final List<SectionExchange.Feeder> fs = new ArrayList<>(fIdx.size());
            for (int i : fIdx) {
                fs.add(feeders.get(i).feeder());
            }
            final List<Integer> cIdx = carsOn.getOrDefault(s, List.of());
            final List<SectionExchange.Car> cs = new ArrayList<>(cIdx.size());
            for (int j : cIdx) {
                final CarIn c = cars.get(j);
                final boolean charge = c.mayCharge()
                        && trainLock.mayMove(c.train(), PortableFeTransfer.Mode.CHARGE, now);
                final boolean discharge = c.mayDischarge()
                        && trainLock.mayMove(c.train(), PortableFeTransfer.Mode.DRAW, now);
                cs.add(new SectionExchange.Car(c.train(), shoesOn(c.shoeSections(), s), c.stored(),
                        c.capacity(), charge, discharge));
            }
            if (fs.isEmpty() && cs.isEmpty()) {
                continue;
            }
            final SectionExchange.Plan plan = SectionExchange.plan(fs, cs, ceiling, collectorRate);
            plans[s] = plan;
            for (int k = 0; k < fIdx.size(); k++) {
                fd[fIdx.get(k)] += plan.feederDelta()[k];
            }
            for (int k = 0; k < cIdx.size(); k++) {
                final int delta = plan.carDelta()[k];
                cd[cIdx.get(k)] += delta;
                if (delta != 0) {
                    trainLock.stamp(cs.get(k).train(),
                            delta > 0 ? PortableFeTransfer.Mode.CHARGE : PortableFeTransfer.Mode.DRAW, now);
                }
            }
        }
        return new Result(fd, cd, carSection, plans);
    }
}
