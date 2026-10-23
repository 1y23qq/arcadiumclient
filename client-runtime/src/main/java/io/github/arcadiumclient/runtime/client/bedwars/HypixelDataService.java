// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.client.bedwars;

public final class HypixelDataService {
    private static final OverlayHttp HTTP = new OverlayHttp();
    private static final BedwarsData DATA = new BedwarsData(HTTP);
    static { Runtime.getRuntime().addShutdownHook(new Thread(HTTP::close, "arcadium-hypixel-shutdown")); }
    private HypixelDataService() { }
    public static OverlayHttp http() { return HTTP; }
    public static BedwarsData data() { return DATA; }
}
