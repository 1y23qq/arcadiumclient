// SPDX-License-Identifier: GPL-3.0-only
package org.polyfrost.oneconfig.api.ui.v1;

import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.polyfrost.oneconfig.api.toggle.v1.ModToggle;
import static org.junit.jupiter.api.Assertions.*;

class ModTogglesTest {
    @Test
    void moduleCardsDelegateToTheModAndNotifyListeners() {
        AtomicBoolean enabled = new AtomicBoolean(true);
        AtomicReference<String> changed = new AtomicReference<>();
        ModToggle toggle = toggle(enabled);
        ModToggles.register("ABI-TEST", toggle);
        ModToggles.Listener listener = (id, value) -> changed.set(id + ":" + value);
        ModToggles.addListener(listener);
        try {
            assertSame(toggle, ModToggles.toggleFor("abi-test.json"));
            ModToggles.setEnabled("ABI-TEST.json", false);
            assertFalse(enabled.get());
            assertFalse(ModToggles.isEnabled("abi-test"));
            assertEquals("abi-test:false", changed.get());
        } finally {
            ModToggles.removeListener(listener);
        }
    }

    @Test
    void persistedDisabledModsApplyWhenTheirToggleRegistersLater() {
        AtomicReference<Set<String>> saved = new AtomicReference<>();
        ModToggles.setStore(new ModToggles.Store() {
            @Override public Set<String> load() { return Set.of("ABI-SAVED"); }
            @Override public void save(Set<String> disabled) { saved.set(disabled); }
        });
        ModToggles.manage("abi-saved");
        AtomicBoolean enabled = new AtomicBoolean(true);
        ModToggles.register("abi-saved.json", toggle(enabled));
        assertFalse(enabled.get());
        ModToggles.setEnabled("abi-saved", true);
        assertTrue(enabled.get());
        assertNotNull(saved.get());
        assertFalse(saved.get().contains("abi-saved"));
    }

    private static ModToggle toggle(AtomicBoolean enabled) {
        return new ModToggle() {
            @Override public boolean isEnabled() { return enabled.get(); }
            @Override public void setEnabled(boolean value) { enabled.set(value); }
        };
    }
}
