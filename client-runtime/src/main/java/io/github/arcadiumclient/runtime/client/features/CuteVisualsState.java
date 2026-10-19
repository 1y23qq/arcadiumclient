// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.client.features;

import java.util.SplittableRandom;

public final class CuteVisualsState {
    public static final int CAPACITY = 384;
    public enum Kind { HEART, SPARK, CONFETTI, RAINBOW }
    public enum Dig { BEGIN, FINISH, CANCEL }

    public record Options(boolean hearts, boolean sparks, boolean movingOnly, boolean pulse,
                          boolean confetti, boolean rainbow, int heartInterval, int heartLife,
                          int sparkInterval, int sparkLife, int burstCount, int burstLife,
                          float burstSize, float burstSpeed, int rainbowLife, float rainbowWidth, float opacity) {
        public Options {
            heartInterval = clamp(heartInterval, 50, 500);
            heartLife = clamp(heartLife, 500, 4000);
            sparkInterval = clamp(sparkInterval, 20, 200);
            sparkLife = clamp(sparkLife, 500, 5000);
            burstCount = clamp(burstCount, 5, 40);
            burstLife = clamp(burstLife, 500, 3000);
            burstSize = finite(burstSize, .05f, .6f, .2f);
            burstSpeed = finite(burstSpeed, .5f, 7f, 2.5f);
            rainbowLife = clamp(rainbowLife, 1000, 6000);
            rainbowWidth = finite(rainbowWidth, 1f, 12f, 5f);
            opacity = finite(opacity, .2f, 1f, .85f);
        }
    }

    public static final class Sprite {
        public Kind kind;
        public long born;
        public int life;
        public double x, y, z, vx, vy, vz;
        public float size, angle, hue;
        public int variant;

        public boolean alive(long now) { return kind != null && now >= born && now - born < life; }
        public float progress(long now) { return Math.max(0f, Math.min(1f, (now - born) / (float) life)); }
    }

    private record Breaking(int x, int y, int z) {
        boolean matches(int otherX, int otherY, int otherZ) { return x == otherX && y == otherY && z == otherZ; }
    }

    private final Sprite[] sprites = new Sprite[CAPACITY];
    private final SplittableRandom random;
    private int cursor;
    private long clock = Long.MIN_VALUE, nextHeart = Long.MIN_VALUE, nextSpark = Long.MIN_VALUE;
    private double previousX, previousY, previousZ;
    private boolean positioned;
    private Breaking breaking;

    public CuteVisualsState() { this(System.nanoTime()); }
    public CuteVisualsState(long seed) {
        random = new SplittableRandom(seed);
        for (int i = 0; i < sprites.length; i++) sprites[i] = new Sprite();
    }

    public Sprite[] sprites() { return sprites; }

    public void tick(long now, double x, double y, double z, Options options) {
        if (now < clock) clear();
        clock = now;
        expire(now, options);
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            positioned = false;
            return;
        }
        double dx = x - previousX, dy = y - previousY, dz = z - previousZ;
        double travel = dx * dx + dy * dy + dz * dz;
        boolean moved = positioned && travel > .000025 && travel < 256;
        if (!options.movingOnly || moved) {
            if (options.hearts && now >= nextHeart) {
                Sprite heart = emit(Kind.HEART, x + random.nextDouble(-.55, .55),
                        y + random.nextDouble(.45, 1.2), z + random.nextDouble(-.55, .55), now, options.heartLife);
                heart.vy = random.nextDouble(.28, .48);
                heart.size = random.nextFloat() * .08f + .12f;
                heart.hue = .88f + random.nextFloat() * .1f;
                nextHeart = now + options.heartInterval;
            }
            if (options.sparks && now >= nextSpark) {
                double anchorX = moved ? previousX : x, anchorZ = moved ? previousZ : z;
                Sprite spark = emit(Kind.SPARK, anchorX + random.nextDouble(-.3, .3),
                        y + random.nextDouble(.2, 1.3), anchorZ + random.nextDouble(-.3, .3), now, options.sparkLife);
                spark.vx = random.nextDouble(-.12, .12);
                spark.vy = random.nextDouble(.06, .28);
                spark.vz = random.nextDouble(-.12, .12);
                spark.size = random.nextFloat() * .035f + .025f;
                spark.hue = .83f + random.nextFloat() * .15f;
                nextSpark = now + options.sparkInterval;
            }
        }
        previousX = x; previousY = y; previousZ = z;
        positioned = true;
    }

    public void expire(long now, Options options) {
        for (Sprite sprite : sprites) {
            if (sprite.kind == null) continue;
            boolean visible = switch (sprite.kind) {
                case HEART -> options.hearts;
                case SPARK -> options.sparks;
                case CONFETTI -> options.confetti;
                case RAINBOW -> options.rainbow;
            };
            if (!visible || !sprite.alive(now)) sprite.kind = null;
        }
    }

    public boolean dig(Dig action, int x, int y, int z, boolean bed, boolean instant) {
        if (action == Dig.BEGIN) {
            breaking = bed && !instant ? new Breaking(x, y, z) : null;
            return bed && instant;
        }
        Breaking pending = breaking;
        breaking = null;
        return action == Dig.FINISH && pending != null && pending.matches(x, y, z);
    }

    public void burst(long now, double x, double y, double z, double viewerX, double viewerZ, Options options) {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) return;
        if (options.confetti) {
            for (int i = 0; i < options.burstCount; i++) {
                Sprite sprite = emit(Kind.CONFETTI, x, y, z, now, options.burstLife);
                double bearing = (i + random.nextDouble()) * Math.TAU / options.burstCount;
                double radial = options.burstSpeed * random.nextDouble(.25, .7);
                sprite.vx = Math.cos(bearing) * radial;
                sprite.vz = Math.sin(bearing) * radial;
                sprite.vy = options.burstSpeed * random.nextDouble(.3, .8);
                sprite.size = options.burstSize * (.65f + random.nextFloat() * .7f);
                sprite.variant = i % 3;
                sprite.hue = i / (float) options.burstCount;
            }
        }
        if (options.rainbow) {
            Sprite arc = emit(Kind.RAINBOW, x, y, z, now, options.rainbowLife);
            arc.size = 1.8f;
            arc.angle = (float) Math.atan2(viewerX - x, viewerZ - z);
        }
    }

    private Sprite emit(Kind kind, double x, double y, double z, long now, int life) {
        Sprite sprite = sprites[cursor];
        cursor = (cursor + 1) % sprites.length;
        if (kind == Kind.RAINBOW && count(Kind.RAINBOW) >= 8) {
            Sprite oldest = null;
            for (Sprite candidate : sprites) if (candidate.kind == Kind.RAINBOW && (oldest == null || candidate.born < oldest.born)) oldest = candidate;
            if (oldest != null) sprite = oldest;
        }
        sprite.kind = kind; sprite.x = x; sprite.y = y; sprite.z = z;
        sprite.born = now; sprite.life = life;
        sprite.vx = sprite.vy = sprite.vz = 0;
        sprite.size = .1f; sprite.angle = random.nextFloat() * (float) Math.TAU;
        sprite.hue = 0; sprite.variant = 0;
        return sprite;
    }

    public int count(Kind kind) {
        int count = 0;
        for (Sprite sprite : sprites) if (sprite.kind == kind) count++;
        return count;
    }

    public int count() {
        int count = 0;
        for (Sprite sprite : sprites) if (sprite.kind != null) count++;
        return count;
    }

    public void clear() {
        for (Sprite sprite : sprites) sprite.kind = null;
        breaking = null; positioned = false; cursor = 0;
        clock = nextHeart = nextSpark = Long.MIN_VALUE;
    }

    public static float opacity(float progress) {
        if (!Float.isFinite(progress) || progress <= 0f || progress >= 1f) return 0f;
        float entering = Math.min(1f, progress / .12f), leaving = Math.min(1f, (1f - progress) / .4f);
        return entering * leaving;
    }

    private static int clamp(int value, int low, int high) { return Math.max(low, Math.min(high, value)); }
    private static float finite(float value, float low, float high, float fallback) {
        return Float.isFinite(value) ? Math.max(low, Math.min(high, value)) : fallback;
    }
}
