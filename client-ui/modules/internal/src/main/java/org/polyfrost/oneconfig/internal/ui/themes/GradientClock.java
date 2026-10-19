package org.polyfrost.oneconfig.internal.ui.themes;

final class GradientClock {
    private record Timing(long nanos, double phase, float speed) {
        double at(long now) { return wrap(phase + (now - nanos) / 4_000_000_000.0 * speed); }
    }

    private volatile Timing timing;

    GradientClock(long now, float speed) { timing = new Timing(now, 0, speed); }

    double at(long now) { return timing.at(now); }

    synchronized void change(float speed, long now, boolean reset) {
        Timing previous = timing;
        if (reset) {
            if (previous.speed != 0 || previous.phase != 0) timing = new Timing(now, 0, 0);
        } else if (speed != previous.speed) {
            timing = new Timing(now, previous.at(now), speed);
        }
    }

    static double wrap(double phase) { return phase - Math.floor(phase); }
}
