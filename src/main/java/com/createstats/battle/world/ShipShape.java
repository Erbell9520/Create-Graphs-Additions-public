package com.createstats.battle.world;

import com.createstats.battle.core.BattleSystem;
import com.createstats.battle.core.PackedPos;
import com.createstats.battle.core.Projection;
import com.createstats.battle.core.ShipFrame;
import com.createstats.battle.core.SurveyStore;

public final class ShipShape {
    public final int minFore;
    public final int maxFore;
    public final int minStbd;
    public final int maxStbd;
    public final int minUp;
    public final int maxUp;
    public final Projection.Frame frame;
    public final byte[] plan;
    public final byte[] profile;

    private ShipShape(int minFore, int maxFore, int minStbd, int maxStbd, int minUp, int maxUp, Projection.Frame frame,
                      byte[] plan, byte[] profile) {
        this.minFore = minFore;
        this.maxFore = maxFore;
        this.minStbd = minStbd;
        this.maxStbd = maxStbd;
        this.minUp = minUp;
        this.maxUp = maxUp;
        this.frame = frame;
        this.plan = plan;
        this.profile = profile;
    }

    static ShipShape build(ShipRecord r) {
        SurveyStore s = r.store;
        ShipFrame f = r.frame();
        int minF = Integer.MAX_VALUE, maxF = Integer.MIN_VALUE, minS = Integer.MAX_VALUE, maxS = Integer.MIN_VALUE;
        int minU = Integer.MAX_VALUE, maxU = Integer.MIN_VALUE;
        for (int i = 0; i < s.size(); i++) {
            int p = s.packedAt(i);
            int dx = PackedPos.dx(p);
            int dz = PackedPos.dz(p);
            int fore = f.fore(dx, dz);
            int stbd = f.stbd(dx, dz);
            int up = PackedPos.dy(p);
            minF = Math.min(minF, fore);
            maxF = Math.max(maxF, fore);
            minS = Math.min(minS, stbd);
            maxS = Math.max(maxS, stbd);
            minU = Math.min(minU, up);
            maxU = Math.max(maxU, up);
        }
        if (s.isEmpty()) {
            minF = maxF = minS = maxS = minU = maxU = 0;
        }
        Projection.Frame frame = Projection.frame(minF, maxF, minS, maxS, minU, maxU, Projection.MAX_W, Projection.MAX_H);
        byte[] plan = new byte[frame.planW() * frame.planH()];
        byte[] profile = new byte[frame.profileW() * frame.profileH()];
        for (int i = 0; i < s.size(); i++) {
            int p = s.packedAt(i);
            int dx = PackedPos.dx(p);
            int dz = PackedPos.dz(p);
            byte state = s.systemAt(i) == BattleSystem.LIFT ? Projection.LIFT : Projection.STRUCTURE;
            int x = frame.x(f.fore(dx, dz));
            Projection.put(plan, frame.planW(), x, frame.planY(f.stbd(dx, dz)), state);
            Projection.put(profile, frame.profileW(), x, frame.profileY(PackedPos.dy(p)), state);
        }
        return new ShipShape(minF, maxF, minS, maxS, minU, maxU, frame, plan, profile);
    }

    byte[] face(ShipRecord r) {
        if (r.store.isEmpty()) return new byte[0];
        byte[] cells = plan.clone();
        ShipFrame f = r.frame();
        for (int p : r.store.lostPositions()) {
            int dx = PackedPos.dx(p);
            int dz = PackedPos.dz(p);
            Projection.put(cells, frame.planW(), frame.x(f.fore(dx, dz)), frame.planY(f.stbd(dx, dz)), Projection.LOST);
        }
        return Projection.pack2(Projection.face(cells, frame.planW(), frame.planH()));
    }
}
