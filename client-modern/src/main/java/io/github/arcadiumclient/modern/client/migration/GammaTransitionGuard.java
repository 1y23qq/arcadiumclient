// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.client.migration;

import java.util.concurrent.locks.ReentrantLock;

public final class GammaTransitionGuard {
    private static final ReentrantLock LOCK = new ReentrantLock();
    private static long generation;
    private GammaTransitionGuard() { }
    @FunctionalInterface public interface Change { void run() throws Exception; }
    public static long token() { LOCK.lock(); try { return generation; } finally { LOCK.unlock(); } }
    public static void run(long token, Runnable task) {
        LOCK.lock();
        try { if (token == generation) task.run(); }
        finally { LOCK.unlock(); }
    }
    public static void replace(Change cancel, Change restore) throws Exception {
        LOCK.lock();
        try { generation++; cancel.run(); restore.run(); }
        finally { LOCK.unlock(); }
    }
}
