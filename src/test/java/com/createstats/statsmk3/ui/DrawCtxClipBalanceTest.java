package com.createstats.statsmk3.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.Deque;

import org.junit.jupiter.api.Test;

class DrawCtxClipBalanceTest {
    private static final class FakeScissors implements DrawCtx.Scissors {
        private final Deque<int[]> stack = new ArrayDeque<>();

        @Override
        public void enable(int x, int y, int right, int bottom) {
            final int[] top = this.stack.peekLast();
            final int[] rect = top == null ? new int[]{x, y, right, bottom} : new int[]{
                Math.max(x, top[0]), Math.max(y, top[1]),
                Math.min(right, top[2]), Math.min(bottom, top[3])};
            this.stack.addLast(rect);
        }

        @Override
        public void disable() {
            if (this.stack.isEmpty()) {
                throw new IllegalStateException("Scissor stack underflow");
            }
            this.stack.removeLast();
        }

        int depth() {
            return this.stack.size();
        }

        int[] current() {
            return this.stack.peekLast();
        }
    }

    private static DrawCtx ctx(FakeScissors scissors) {
        return new DrawCtx(null, null, scissors);
    }

    @Test
    void nestedClipsLeaveNothingOnTheStack() {
        final FakeScissors gpu = new FakeScissors();
        final DrawCtx c = ctx(gpu);

        c.clip(new Rect(0.0f, 0.0f, 200.0f, 200.0f));
        c.clip(new Rect(50.0f, 50.0f, 50.0f, 50.0f));
        c.unclip();
        c.unclip();
        c.finish();

        assertEquals(0, gpu.depth(), "scissoring must be off once the frame is finished");
        assertNull(gpu.current());
    }

    @Test
    void endingANestedClipRestoresItsParent() {
        final FakeScissors gpu = new FakeScissors();
        final DrawCtx c = ctx(gpu);

        final Rect outer = new Rect(10.0f, 20.0f, 100.0f, 60.0f);
        c.clip(outer);
        c.clip(new Rect(30.0f, 30.0f, 10.0f, 10.0f));
        c.unclip();

        assertEquals(1, gpu.depth());
        assertArray(new int[]{10, 20, 110, 80}, gpu.current());
        assertTrue(c.culled(new Rect(500.0f, 500.0f, 5.0f, 5.0f)),
                "the mirror culled() reads must agree with the rect actually in force");

        c.unclip();
        assertEquals(0, gpu.depth());
    }

    @Test
    void finishClosesClipsTheCallerLeftOpen() {
        final FakeScissors gpu = new FakeScissors();
        final DrawCtx c = ctx(gpu);

        c.clip(new Rect(0.0f, 0.0f, 40.0f, 40.0f));
        c.clip(new Rect(5.0f, 5.0f, 10.0f, 10.0f));
        c.finish();

        assertEquals(0, gpu.depth(), "an early return mid-draw must not strand a clip");
    }

    @Test
    void unclipWithoutAClipNeverTouchesTheStack() {
        final FakeScissors gpu = new FakeScissors();
        final DrawCtx c = ctx(gpu);

        c.unclip();
        c.finish();

        assertEquals(0, gpu.depth());
    }

    @Test
    void theSecurityPanelsOwnNestingBalances() {
        final FakeScissors gpu = new FakeScissors();
        final DrawCtx c = ctx(gpu);

        c.clip(new Rect(120.0f, 40.0f, 600.0f, 400.0f));
        c.clip(new Rect(150.0f, 200.0f, 540.0f, 90.0f));
        c.unclip();
        c.unclip();
        c.finish();

        assertEquals(0, gpu.depth(), "the panel must hand the GPU back an unclipped frame");
    }

    private static void assertArray(int[] expected, int[] actual) {
        assertEquals(expected.length, actual.length);
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], actual[i], "component " + i);
        }
    }
}
