// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.gui.qa;

public final class PotionVisualizerQa {
    private static int draws;
    private PotionVisualizerQa() {}
    public static void rendered() { draws++; }
    public static int draws() { return draws; }
}
