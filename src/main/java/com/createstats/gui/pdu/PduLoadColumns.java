package com.createstats.gui.pdu;

public record PduLoadColumns(int pipX, int nameX, int nameW, int tierX, int tierW,
                             int drawX, int drawW, int stateX, int stateW,
                             int legX, int legW, int lockX, int lockW) {
    public static final int PIP_W = 10;

    public static final int TIER_W = 52;

    public static final int TIER_MIN = 40;

    public static final int DRAW_W = 74;

    public static final int DRAW_MIN = 52;

    public static final int LOCK_W = 12;

    public static final int LEG_W = 30;

    public static final int STATE_FLAG_W = 26;

    public static final int STATE_FULL_W = 64;

    public static final int STATE_LABEL_MIN = 60;

    public static final int NAME_MIN = 78;

    private static final int PAD = 4;
    private static final int GAP = 6;

    // drop the leg column first, the row tab already says it
    public static PduLoadColumns of(int x, int w) {
        final int inner = w - 2 * PAD;
        int tierW = TIER_W;
        int drawW = DRAW_W;
        int legW = LEG_W;
        int flex = flexFor(inner, tierW, drawW, legW);

        if (splitName(flex) < NAME_MIN) {
            legW = 0;
            flex = flexFor(inner, tierW, drawW, legW);
        }
        if (splitName(flex) < NAME_MIN) {
            tierW = TIER_MIN;
            drawW = DRAW_MIN;
            flex = flexFor(inner, tierW, drawW, legW);
        }
        if (flex < 0) {
            final int deficit = -flex;
            final int fromDraw = Math.min(deficit, drawW);
            drawW -= fromDraw;
            tierW -= Math.min(deficit - fromDraw, tierW);
            flex = flexFor(inner, tierW, drawW, legW);
        }
        flex = Math.max(0, flex);
        final int stateW = Math.min(flex, splitState(flex));
        final int nameW = flex - stateW;

        final int pipX = x + PAD;
        final int nameX = pipX + PIP_W;
        final int tierX = nameX + nameW + GAP;
        final int drawX = tierX + tierW + GAP;
        final int stateX = drawX + drawW + GAP;
        final int legX = stateX + stateW + GAP;
        final int lockX = legX + legW + (legW > 0 ? GAP : 0);
        return new PduLoadColumns(pipX, nameX, nameW, tierX, tierW, drawX, drawW,
                stateX, stateW, legX, legW, lockX, LOCK_W);
    }

    private static int flexFor(int inner, int tierW, int drawW, int legW) {
        final int gaps = GAP * (legW > 0 ? 5 : 4);
        return inner - PIP_W - tierW - drawW - LOCK_W - legW - gaps;
    }

    private static int splitName(int flex) {
        return flex - splitState(flex);
    }

    private static int splitState(int flex) {
        return Math.max(STATE_FLAG_W, Math.min(STATE_FULL_W, Math.round(flex * 0.40f)));
    }

    public boolean legShown() {
        return legW > 0;
    }

    public boolean stateWordsShown() {
        return stateW >= STATE_LABEL_MIN;
    }

    public int right() {
        return lockX + lockW;
    }

    public PduTableModel.Sort columnAt(double mouseX) {
        if (within(mouseX, nameX, nameW)) {
            return PduTableModel.Sort.NAME;
        }
        if (within(mouseX, tierX, tierW)) {
            return PduTableModel.Sort.TIER;
        }
        if (within(mouseX, drawX, drawW)) {
            return PduTableModel.Sort.DRAW;
        }
        if (within(mouseX, stateX, stateW)) {
            return PduTableModel.Sort.STATUS;
        }
        if (legShown() && within(mouseX, legX, legW)) {
            return PduTableModel.Sort.LEG;
        }
        return null;
    }

    public int[] boundsOf(PduTableModel.Sort sort) {
        return switch (sort) {
            case NAME -> new int[]{nameX, nameW};
            case TIER -> new int[]{tierX, tierW};
            case DRAW -> new int[]{drawX, drawW};
            case STATUS -> new int[]{stateX, stateW};
            case LEG -> new int[]{legX, legW};
        };
    }

    private static boolean within(double v, int start, int width) {
        return v >= start && v < start + width;
    }
}
