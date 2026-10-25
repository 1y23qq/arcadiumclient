// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.client.hypixel;

import org.polyfrost.oneconfig.api.config.v1.Config;
import org.polyfrost.oneconfig.api.config.v1.annotations.*;

public final class AutoTipConfig extends Config {
    public static final AutoTipConfig INSTANCE = new AutoTipConfig();
    @Switch(title = "Enabled") public static boolean enabled = true;
    @Dropdown(title = "Messages", options = {"Hidden", "Summary", "All"}) public static int messages = 1;
    @Switch(title = "Hide Login Message") public static boolean hideLoginMessage;
    @Slider(title = "Interval", description = "Minutes between tip requests. Hypixel applies its own tip limits.", min = 5, max = 120, step = 5) public static int intervalMinutes = 5;
    private AutoTipConfig() { super("tipper.json", "/assets/arcadium/logo.svg", "AutoTip", Category.HYPIXEL); }
    @Button(title = "Tip Players", text = "Tip now") public void tip() { ModernHypixel.tipNow(); }
}
