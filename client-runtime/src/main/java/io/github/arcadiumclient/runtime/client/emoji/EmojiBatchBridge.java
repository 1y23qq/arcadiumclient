// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.client.emoji;

import java.lang.reflect.Method;

final class EmojiBatchBridge {
    private static boolean resolved, flushing;
    private static Method getBatcher, shouldFlush, beforeImmediate;
    private EmojiBatchBridge() { }

    static void beforeImage(Object font) {
        if (flushing || font == null) return;
        if (!resolved) {
            resolved = true;
            try {
                getBatcher = font.getClass().getMethod("argentum$getBatcher");
                Class<?> batcher = getBatcher.getReturnType();
                shouldFlush = batcher.getMethod("shouldFlushBeforeImmediate", String.class);
                beforeImmediate = batcher.getMethod("runBeforeImmediateText");
            } catch (ReflectiveOperationException absent) { getBatcher = null; }
        }
        if (getBatcher == null) return;
        flushing = true;
        try {
            Object batcher = getBatcher.invoke(font);
            // Request the same flush as uncacheable text before drawing an immediate image.
            if (Boolean.TRUE.equals(shouldFlush.invoke(batcher, "§k"))) beforeImmediate.invoke(batcher);
        } catch (ReflectiveOperationException failure) {
            getBatcher = null;
            org.slf4j.LoggerFactory.getLogger("Arcadium/Emoji").debug("Could not flush deferred text", failure);
        } finally { flushing = false; }
    }
}
