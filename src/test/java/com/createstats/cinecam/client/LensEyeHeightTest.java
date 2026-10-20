package com.createstats.cinecam.client;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LensEyeHeightTest {
    private static final float STAND = 1.62f;
    private static final float CROUCH = 1.27f;
    private static final float LENS = 0f;
    private static final float[] PARTIALS = {0f, 1f / 3f, 2f / 3f};

    enum Fix { NONE, ONCE, HOLD }

    private static final class VanillaCamera {
        float eyeHeight = STAND;
        float eyeHeightOld = STAND;

        boolean lastLens;

        void tick(float playerEye) {
            eyeHeightOld = eyeHeight;
            eyeHeight = eyeHeight + ((lastLens ? LENS : playerEye) - eyeHeight) * 0.5F;
        }

        float setup(boolean lens, float partial) {
            lastLens = lens;
            return eyeHeightOld + partial * (eyeHeight - eyeHeightOld);
        }

        void set(float h) {
            eyeHeight = h;
            eyeHeightOld = h;
        }
    }

    private record Shot(int tick, float partial, boolean lens, float height, float playerEye) {
    }

    private static final class Game {
        final Fix fix;
        final VanillaCamera cam = new VanillaCamera();
        final List<Shot> shots = new ArrayList<>();
        boolean inView;
        boolean away;
        boolean lensIsCameraEntity;
        boolean playerEyeDue;
        float playerEye = STAND;
        int tick;

        Game(Fix fix) {
            this.fix = fix;
        }

        void lookThrough() {
            inView = true;
            away = false;
            lensIsCameraEntity = true;
            if (fix == Fix.ONCE) cam.set(LENS);
            playerEyeDue = false;
        }

        void giveBack(boolean filmGoesOn) {
            inView = false;
            away = filmGoesOn;
            lensIsCameraEntity = false;
            if (fix == Fix.ONCE) cam.set(playerEye);
            if (fix == Fix.HOLD) playerEyeDue = true;
        }

        void tick(float eye, Runnable pre, Runnable keybinds) {
            playerEye = eye;
            pre.run();
            keybinds.run();
            cam.tick(playerEye);
            for (float p : PARTIALS) frame(p);
            tick++;
        }

        void frame(float partial) {
            if (fix == Fix.HOLD) {
                if (playerEyeDue) {
                    playerEyeDue = false;
                    if (!inView) cam.set(playerEye);
                }
                if (inView) cam.set(LENS);
            }
            if (away) {
                float now = cam.eyeHeight;
                float old = cam.eyeHeightOld;
                if (fix != Fix.NONE) cam.set(LENS);
                shots.add(new Shot(tick, partial, true, cam.setup(true, partial), playerEye));
                if (fix != Fix.NONE) {
                    cam.eyeHeight = now;
                    cam.eyeHeightOld = old;
                }
            }
            shots.add(new Shot(tick, partial, lensIsCameraEntity, cam.setup(lensIsCameraEntity, partial), playerEye));
        }
    }

    private static final Runnable NOTHING = () -> {
    };

    private static Game play(Fix fix) {
        Game g = new Game(fix);
        for (int t = 0; t < 120; t++) {
            boolean crouch = t == 30 || (t >= 50 && t <= 55) || t == 90;
            Runnable pre = t == 30 ? () -> g.giveBack(true) : t == 90 ? () -> g.giveBack(false) : NOTHING;
            Runnable keys = t == 10 || t == 70 ? g::lookThrough : NOTHING;
            g.tick(crouch ? CROUCH : STAND, pre, keys);
        }
        return g;
    }

    private static List<Shot> lens(Game g) {
        return g.shots.stream().filter(Shot::lens).toList();
    }

    private static float shotAt(Game g, boolean lens, int tick, float partial) {
        return g.shots.stream().filter(s -> s.lens() == lens && s.tick() == tick && s.partial() == partial)
                .findFirst().orElseThrow().height();
    }

    @Test
    void heldEveryFrameTheLensNeverMoves() {
        Game g = play(Fix.HOLD);
        List<Shot> lens = lens(g);
        assertTrue(lens.size() > 200, "the lens drew in view and away");
        for (Shot s : lens) assertEquals(LENS, s.height(), 0f, "tick " + s.tick() + " partial " + s.partial());
    }

    @Test
    void heldEveryFrameThePlayersViewComesBackAtTheirEyes() {
        Game g = play(Fix.HOLD);
        assertEquals(CROUCH, shotAt(g, false, 30, 0f), 0f, "stepping out mid-take, crouched: at their eyes at once");
        assertEquals(CROUCH, shotAt(g, false, 90, 0f), 0f, "leaving, crouched: at their eyes at once");

        assertTrue(shotAt(g, false, 92, 0f) > CROUCH && shotAt(g, false, 95, 0f) > STAND - 0.05f);
        assertEquals(STAND, shotAt(g, false, 5, 0f), 0f, "and untouched before the camera was ever used");
    }

    @Test
    void unheldTheFilmRoseWhenItsOperatorSteppedOut() {
        Game g = play(Fix.NONE);
        assertEquals(0f, shotAt(g, true, 30, 0f), 1e-5f, "the first away frame is still at the lens");
        assertEquals(0.81f, shotAt(g, true, 32, 0f), 1e-4f, "one tick of easing: half");
        assertEquals(1.215f, shotAt(g, true, 33, 0f), 1e-4f, "two ticks: three quarters, as the film shows");
        assertTrue(shotAt(g, true, 37, 0f) > 1.59f, "nearly all of the player's eye height in six");
        float before = shotAt(g, true, 50, 0f);
        float dipped = shotAt(g, true, 56, 0f);
        assertTrue(before - dipped > 0.3f, "a crouch moved the film " + (before - dipped) + " blocks");

        assertTrue(shotAt(g, true, 70, 0f) > 1.5f && shotAt(g, true, 75, 0f) < 0.2f, "entering sank 1.62 over a few ticks");
    }

    @Test
    void setOnceTheHandOverStillJumped() {
        Game g = play(Fix.ONCE);
        float up = 0f;
        for (Shot s : lens(g)) if (s.tick() >= 10 && s.tick() <= 14) up = Math.max(up, s.height());
        assertTrue(up > 0.5f, "entering lifted the picture " + up + " blocks");
        float low = Float.MAX_VALUE;
        for (Shot s : g.shots) if (!s.lens() && s.tick() >= 90 && s.tick() <= 94) low = Math.min(low, s.height() - s.playerEye());
        assertTrue(low < -0.3f, "leaving dropped the player's view " + (-low) + " blocks");
    }
}
