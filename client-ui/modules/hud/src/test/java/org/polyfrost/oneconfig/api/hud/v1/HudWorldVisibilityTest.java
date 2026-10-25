package org.polyfrost.oneconfig.api.hud.v1;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HudWorldVisibilityTest {
    private final TextHud hud = new TextHud("world-visibility", "Visibility", Hud.Category.getINFO(), "", "") {
        @Override public String getText() { return "Test"; }
    };
    private Method isShown;
    private Field editorOpen;
    private boolean previousWorld;
    private boolean previousConfig;
    private boolean previousEditor;

    @BeforeEach void prepare() throws Exception {
        isShown = HudManager.class.getDeclaredMethod("isShown", Hud.class);
        isShown.setAccessible(true);
        editorOpen = HudManager.class.getDeclaredField("isEditorOpen");
        editorOpen.setAccessible(true);
        previousWorld = HudManager.inWorld;
        previousConfig = HudManager.isConfigUiOpen;
        previousEditor = editorOpen.getBoolean(HudManager.INSTANCE);
        editorOpen.setBoolean(HudManager.INSTANCE, false);
    }

    @AfterEach void restore() throws Exception {
        HudManager.inWorld = previousWorld;
        HudManager.isConfigUiOpen = previousConfig;
        editorOpen.setBoolean(HudManager.INSTANCE, previousEditor);
    }

    private boolean shown() throws Exception { return (boolean) isShown.invoke(HudManager.INSTANCE, hud); }

    @Test void titleOptionsDoNotDisplayWorldHuds() throws Exception {
        HudManager.inWorld = false;
        HudManager.isConfigUiOpen = true;
        assertFalse(shown());
        HudManager.isConfigUiOpen = false;
        assertFalse(shown());
    }

    @Test void explicitEditorStillDisplaysTitlePreviews() throws Exception {
        HudManager.inWorld = false;
        editorOpen.setBoolean(HudManager.INSTANCE, true);
        assertTrue(shown());
    }

    @Test void worldOptionsKeepHudPreviews() throws Exception {
        HudManager.inWorld = true;
        HudManager.isConfigUiOpen = true;
        assertTrue(shown());
    }
}
