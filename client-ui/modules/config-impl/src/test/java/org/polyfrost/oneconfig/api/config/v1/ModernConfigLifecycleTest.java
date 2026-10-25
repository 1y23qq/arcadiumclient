// SPDX-License-Identifier: GPL-3.0-only
package org.polyfrost.oneconfig.api.config.v1;

import org.junit.jupiter.api.Test;
import org.polyfrost.oneconfig.api.config.v1.annotations.MasterToggle;
import org.polyfrost.oneconfig.api.config.v1.annotations.Switch;
import org.polyfrost.oneconfig.api.config.v1.collect.impl.OneConfigCollector;
import static org.junit.jupiter.api.Assertions.*;

class ModernConfigLifecycleTest {
    @Test
    void initializedHookReceivesLoadedTreeAndRunsAgainAfterProfileRebind() {
        HookConfig config = new HookConfig();
        config.preload();
        assertEquals(1, config.calls);
        assertSame(config.getTree(), config.observed);
        assertNotNull(config.observed.getProp("value"));
        config.rebindToActiveProfile(false);
        assertEquals(2, config.calls);
        assertSame(config.getTree(), config.observed);
    }

    @Test
    void masterToggleIsCollectedAndDisablesDependentSettings() {
        ModernConfig config = new ModernConfig();
        try {
            Tree tree = new OneConfigCollector().collect(config);
            assertNotNull(tree);
            assertEquals("enabled", tree.getMetadata("mod_card_toggle"));
            assertEquals(Property.Display.SHOWN, tree.getProp("setting").getDisplay());
            tree.getProp("enabled").setAs(false);
            assertFalse(config.enabled);
            assertEquals(Property.Display.DISABLED, tree.getProp("setting").getDisplay());
            assertEquals(Property.Display.SHOWN, tree.getProp("enabled").getDisplay());
            tree.getProp("enabled").setAs(true);
            assertEquals(Property.Display.SHOWN, tree.getProp("setting").getDisplay());
        } finally {
            ConfigManager.removePendingInitialization(config);
        }
    }

    static class ModernConfig extends Config {
        @MasterToggle public boolean enabled = true;
        @Switch(title = "Setting") public boolean setting = true;
        ModernConfig() { super("modern-config-api-test.json", "Modern config test", Category.OTHER); }
    }

    static class HookConfig extends Config {
        @Switch(title = "Value") public boolean value = true;
        int calls;
        Tree observed;
        HookConfig() { super("modern-config-hook-test.json", "Modern config hook test", Category.OTHER); }
        @Override protected void onInitialized(Tree tree) { calls++; observed = tree; }
    }
}
