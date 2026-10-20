package com.createstats.vccmk4.core;

import java.util.List;

public final class TrainRoles {
    public record Facts(int parent, boolean wheels, boolean load, boolean towed, boolean articulatedHalf) {
    }

    private TrainRoles() {
    }

    public static BodyRole[] classify(List<Facts> bodies) {
        BodyRole[] out = new BodyRole[bodies.size()];
        for (int b = 0; b < out.length; b++) {
            Facts f = bodies.get(b);
            if (b == 0) out[b] = BodyRole.PART;
            else if (f.load()) out[b] = BodyRole.LOAD;
            else if (f.towed()) out[b] = BodyRole.TOWED;
            else if (f.articulatedHalf() || !f.wheels()) out[b] = BodyRole.PART;
            else out[b] = BodyRole.TRAILER;
        }
        return out;
    }

    public static int[] owners(List<Facts> bodies, BodyRole[] roles) {
        int[] out = new int[bodies.size()];
        for (int b = 0; b < out.length; b++) {
            int at = b;
            int guard = 0;
            while (at > 0 && at < roles.length && roles[at] == BodyRole.PART && guard++ < out.length) {
                at = bodies.get(at).parent();
            }
            out[b] = at > 0 && at < roles.length && guard <= out.length ? at : 0;
        }
        return out;
    }

    public static int[] rigidRoots(int[] parent, boolean[] rigidLink) {
        int n = Math.min(parent.length, rigidLink.length);
        int[] out = new int[parent.length];
        for (int b = 1; b < out.length; b++) {
            int at = b;
            int guard = 0;
            while (at > 0 && at < n && rigidLink[at] && guard++ < n) at = parent[at];
            out[b] = at >= 0 && at < n && guard <= n ? at : b;
        }
        return out;
    }
}
