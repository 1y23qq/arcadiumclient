// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.gui.qa;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;

final class QaTickScheduler {
    private static final ConcurrentLinkedQueue<Runnable> pending = new ConcurrentLinkedQueue<>();
    static final Executor EXECUTOR = QaTickScheduler::execute;

    static void execute(Runnable action) { pending.add(action); }

    static java.util.concurrent.CompletableFuture<Void> afterTicks(int ticks) {
        var completion = new java.util.concurrent.CompletableFuture<Void>();
        execute(new Runnable() {
            private int remaining = ticks;
            @Override public void run() {
                if (--remaining <= 0) completion.complete(null);
                else execute(this);
            }
        });
        return completion;
    }

    static java.util.concurrent.CompletableFuture<Void> afterFrames(int frames) {
        var completion = new java.util.concurrent.CompletableFuture<Void>();
        long target = ModernFrameSampler.renderedFrames() + frames, started = System.nanoTime();
        execute(new Runnable() {
            @Override public void run() {
                if (ModernFrameSampler.renderedFrames() >= target) completion.complete(null);
                else if (System.nanoTime() - started > 15_000_000_000L) completion.completeExceptionally(new IllegalStateException("The native renderer did not complete the required capture-settle frames"));
                else execute(this);
            }
        });
        return completion;
    }

    static void drain() {
        int count = Math.min(pending.size(), 64);
        for (int i = 0; i < count; i++) {
            Runnable action = pending.poll();
            if (action != null) action.run();
        }
    }
}
