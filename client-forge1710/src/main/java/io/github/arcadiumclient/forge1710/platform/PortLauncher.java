// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.forge1710.platform;

import java.net.URL;
import java.nio.file.Path;
import net.minecraft.launchwrapper.Launch;

public interface PortLauncher {
    static PortLauncher getLauncher() { return Instance.VALUE; }
    void addToClassPath(Path path, String... allowedPrefixes);
    void propose(URL url);

    final class Instance implements PortLauncher {
        private static final Instance VALUE = new Instance();
        @Override public void addToClassPath(Path path, String... ignored) {
            try { propose(path.toUri().toURL()); }
            catch (java.net.MalformedURLException error) { throw new IllegalArgumentException(error); }
        }
        @Override public void propose(URL url) { Launch.classLoader.addURL(url); }
    }
}
