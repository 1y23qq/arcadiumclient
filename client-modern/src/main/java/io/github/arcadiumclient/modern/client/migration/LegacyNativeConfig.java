// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.client.migration;

import java.nio.file.Files;
import java.nio.file.Path;
import org.polyfrost.oneconfig.api.config.v1.Config;
import org.polyfrost.oneconfig.api.config.v1.ConfigManager;
import org.polyfrost.oneconfig.api.config.v1.Tree;

abstract class LegacyNativeConfig extends Config {
    private boolean legacyDocument;
    LegacyNativeConfig(String id, String title, Category category) { super(id, "/assets/arcadium/logo.svg", title, category); }
    protected Path documentPath() { return ConfigManager.active().getFolder().resolve(id); }
    @Override protected final Tree makeTree() {
        legacyDocument = Files.isRegularFile(documentPath());
        return super.makeTree();
    }
    protected final boolean hadLegacyDocument() { return legacyDocument; }
}
