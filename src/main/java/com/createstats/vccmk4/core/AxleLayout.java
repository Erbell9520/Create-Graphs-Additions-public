package com.createstats.vccmk4.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class AxleLayout {
    public static final double AXLE_TOLERANCE = 0.75;

    public record WheelSpec(double lateral, double longitudinal) {
    }

    public record Axle(int index, double longitudinal, int[] wheels) {
    }

    private AxleLayout() {
    }

    public static List<Axle> cluster(List<WheelSpec> wheels) {
        List<Axle> out = new ArrayList<>();
        if (wheels.isEmpty()) return out;
        Integer[] order = new Integer[wheels.size()];
        for (int i = 0; i < order.length; i++) order[i] = i;

        Arrays.sort(order, (a, b) -> {
            int byZ = Double.compare(wheels.get(b).longitudinal(), wheels.get(a).longitudinal());
            return byZ != 0 ? byZ : Double.compare(wheels.get(a).lateral(), wheels.get(b).lateral());
        });
        List<Integer> group = new ArrayList<>();
        double groupFront = wheels.get(order[0]).longitudinal();
        for (int k = 0; k <= order.length; k++) {
            boolean flush = k == order.length
                    || groupFront - wheels.get(order[k]).longitudinal() > AXLE_TOLERANCE;
            if (flush) {
                group.sort((a, b) -> Double.compare(wheels.get(a).lateral(), wheels.get(b).lateral()));
                double sum = 0.0;
                int[] idx = new int[group.size()];
                for (int i = 0; i < idx.length; i++) {
                    idx[i] = group.get(i);
                    sum += wheels.get(idx[i]).longitudinal();
                }
                out.add(new Axle(out.size(), sum / idx.length, idx));
                group.clear();
                if (k < order.length) groupFront = wheels.get(order[k]).longitudinal();
            }
            if (k < order.length) group.add(order[k]);
        }
        return out;
    }

    public static int[] axleOf(int wheelCount, List<Axle> axles) {
        int[] out = new int[wheelCount];
        Arrays.fill(out, -1);
        for (Axle a : axles) {
            for (int w : a.wheels()) {
                if (w >= 0 && w < wheelCount) out[w] = a.index();
            }
        }
        return out;
    }
}
