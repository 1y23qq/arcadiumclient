// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.forge1710.platform;

public final class LegacyPortLauncherBridge {
    public static LegacyPortLauncher getLauncher() { return PortLauncher.getLauncher()::propose; }
}
