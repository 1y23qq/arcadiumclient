// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 Arcadium contributors.

package io.github.arcadiumclient.modern.gui.qa;

import java.util.Map;

public final class NativeHandProbe {
    private static boolean active;
    private static int entered, completed;
    private NativeHandProbe() {}
    static void start() { entered = completed = 0; active = true; }
    static Map<String, Integer> finish() { active = false; return Map.of("entered", entered, "completed", completed); }
    public static void entered() { if (active) entered++; }
    public static void completed() { if (active) completed++; }
}
