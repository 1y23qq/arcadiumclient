// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package org.polyfrost.oneconfig.internal.ui.api;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.polyfrost.oneconfig.api.config.v1.Properties;
import org.polyfrost.oneconfig.api.config.v1.Property;
import org.polyfrost.oneconfig.api.config.v1.Tree;
import org.polyfrost.oneconfig.api.hud.v1.Hud;
import org.polyfrost.oneconfig.api.hud.v1.HudManager;
import org.polyfrost.oneconfig.api.hud.v1.TextHud;
import org.polyfrost.oneconfig.internal.ui.hud.HudModCardData;

import static org.junit.jupiter.api.Assertions.*;

class ModCardToggleTest {
    @Test
    void masterToggleUsesItsRealPropertyAndCallbacks() {
        Tree tree = Tree.tree("simple-card-test");
        Property<Boolean> enabled = Properties.simple("enabled", "Enabled", null, false, Boolean.class);
        AtomicInteger callbacks = new AtomicInteger();
        enabled.addCallback(value -> { callbacks.incrementAndGet(); return false; });
        tree.put(enabled);

        Property<Boolean> resolved = ModCardToggleKt.findModEnabledProperty(tree);
        assertSame(enabled, resolved);
        resolved.set(true);
        assertTrue(enabled.get());
        assertEquals(1, callbacks.get());
    }

    @Test
    void globalToggleWinsOverASecondaryEnabledOption() {
        Tree tree = Tree.tree("simple-card-legacy");
        Property<Boolean> global = Properties.simple("globalModToggle", "Enabled", null, false, Boolean.class);
        tree.put(Properties.simple("enabled", "Subfeature", null, true, Boolean.class), global);
        assertSame(global, ModCardToggleKt.findModEnabledProperty(tree));
    }

    @Test
    void settingsOnlyCardsDoNotBorrowNestedOrUnrelatedSwitches() {
        Tree tree = Tree.tree("simple-card-settings");
        tree.put(Properties.simple("showMessages", "Show messages", null, true, Boolean.class));
        tree.getOrPutChild("subfeature").put(Properties.simple("enabled", "Enabled", null, true, Boolean.class));
        assertNull(ModCardToggleKt.findModEnabledProperty(tree));
        tree.put(Properties.simple("enabled", "Enabled", null, 1, Integer.class));
        assertNull(ModCardToggleKt.findModEnabledProperty(tree));
    }

    @Test
    void explicitToggleMetadataDoesNotFallBackToAnotherFeature() {
        Tree tree = Tree.tree("simple-card-explicit");
        Property<Boolean> master = Properties.simple("active", "Active", null, true, Boolean.class);
        tree.put(master, Properties.simple("enabled", "Subfeature", null, false, Boolean.class));
        tree.addMetadata("mod_card_toggle", "active");
        assertSame(master, ModCardToggleKt.findModEnabledProperty(tree));
        tree.addMetadata("mod_card_toggle", "absent");
        assertNull(ModCardToggleKt.findModEnabledProperty(tree));
    }

    @Test
    void hiddenAndExternalOnlyControlsHaveNoInventedSwitch() {
        Tree tree = Tree.tree("simple-card-hidden");
        Property<Boolean> master = Properties.simple("enabled", "Enabled", null, false, Boolean.class);
        tree.put(master);
        master.addDisplayCondition(() -> Property.Display.HIDDEN);
        assertNull(ModCardToggleKt.findModEnabledProperty(tree));

        Tree external = Tree.tree("simple-card-external");
        external.put(Properties.simple("enabled", "Enabled", null, true, Boolean.class));
        external.addMetadata("ui_only", true);
        assertNull(ModCardToggleKt.findModEnabledProperty(external));
    }

    @Test
    void hudSwitchHidesAndRestoresExistingInstancesWithoutLosingPlacement() {
        TestHud hud = new TestHud();
        hud.setRelativeX(42f);
        hud.setRelativeY(27f);
        HudManager.INSTANCE.getActiveInstances().add(hud);
        try {
            var card = new HudModCardData(hud, "oneconfig.builtin", null, "simple-card-hud");
            ModCardToggle toggle = ModCardToggleKt.modCardToggle(card);
            assertNotNull(toggle);
            assertTrue(toggle.getEnabled());
            toggle.setEnabled(false);
            assertTrue(hud.getHidden());
            assertFalse(toggle.getEnabled());
            assertTrue(HudManager.INSTANCE.getActiveInstances().contains(hud));
            toggle.setEnabled(true);
            assertFalse(hud.getHidden());
            assertEquals(42f, hud.getRelativeX());
            assertEquals(27f, hud.getRelativeY());
        } finally {
            HudManager.INSTANCE.getActiveInstances().remove(hud);
        }
    }

    private static final class TestHud extends TextHud {
        private TestHud() { super("simple-card-hud", "Simple HUD", Hud.Category.Companion.getINFO(), "Test", ""); }
        @Override protected String getText() { return "HUD"; }
    }
}
