package com.createstats.gui.pdu;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class PduTopology {
    private PduTopology() {
    }

    public static final int MAX_NODES = 256;

    public enum Kind {
        PDU, CABLE, TRAY, SWITCH, CONNECTOR, DEVICE, DUCT,

        TRUNK;

        public static Kind byOrdinal(int ordinal) {
            final Kind[] values = values();
            return ordinal >= 0 && ordinal < values.length ? values[ordinal] : CABLE;
        }
    }

    public record RawNode(BlockPos pos, byte kind, byte tier, boolean open, List<BlockPos> links) {
    }

    public record BranchNode(BlockPos pos, byte kind, byte tier, boolean open, int parentIndex) {
        public Kind kindEnum() {
            return Kind.byOrdinal(kind);
        }
    }

    public record Result(List<BranchNode> nodes, boolean truncated) {
        public static final Result EMPTY = new Result(List.of(), false);
    }

    public static Result build(List<RawNode> raws, BlockPos rootPos, int cap) {
        if (raws.isEmpty()) {
            return Result.EMPTY;
        }

        final Map<BlockPos, Integer> idx = new HashMap<>();
        for (int i = 0; i < raws.size(); i++) {
            idx.putIfAbsent(raws.get(i).pos(), i);
        }
        final Integer root = idx.get(rootPos);
        if (root == null) {
            return Result.EMPTY;
        }

        final int[] parentRaw = new int[raws.size()];
        Arrays.fill(parentRaw, -2);
        final int[] outIndex = new int[raws.size()];
        Arrays.fill(outIndex, -1);
        final Deque<Integer> queue = new ArrayDeque<>();
        parentRaw[root] = -1;
        queue.add(root);

        final List<BranchNode> out = new ArrayList<>();
        boolean truncated = false;
        while (!queue.isEmpty()) {
            final int cur = queue.poll();
            if (out.size() >= cap) {
                truncated = true;
                break;
            }
            outIndex[cur] = out.size();
            final RawNode rn = raws.get(cur);
            final int parentOut = parentRaw[cur] < 0 ? -1 : outIndex[parentRaw[cur]];
            out.add(new BranchNode(rn.pos(), rn.kind(), rn.tier(), rn.open(), parentOut));
            for (BlockPos neighbour : neighbours(rn)) {
                final Integer ni = idx.get(neighbour);
                if (ni != null && parentRaw[ni] == -2) {
                    parentRaw[ni] = cur;
                    queue.add(ni);
                }
            }
        }
        return new Result(out, truncated);
    }

    private static List<BlockPos> neighbours(RawNode node) {
        final List<BlockPos> out = new ArrayList<>(6 + node.links().size());
        for (Direction d : Direction.values()) {
            out.add(node.pos().relative(d));
        }
        out.addAll(node.links());
        return out;
    }
}
