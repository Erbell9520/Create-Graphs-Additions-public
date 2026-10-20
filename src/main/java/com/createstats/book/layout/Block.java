package com.createstats.book.layout;

import com.createstats.book.BookTokens;

public abstract class Block {
    public abstract int height(Ctx x, int w);

    public abstract void paint(Ctx x, int left, int top, int w);

    public boolean deferrable() {
        return false;
    }

    public Block[] split(Ctx x, int w, int room) {
        return null;
    }

    public int gap() {
        return BookTokens.PART_GAP;
    }

    public boolean isBreak() {
        return false;
    }

    public int breakBefore() {
        return Integer.MAX_VALUE;
    }

    public String bandKey() {
        return null;
    }

    public java.util.function.Supplier<Object[]> bandArgs() {
        return null;
    }

    public boolean breaksToLeft() {
        return false;
    }
}
