package org.polyfrost.oneconfig.internal.ui.api;

import org.junit.jupiter.api.Test;
import org.polyfrost.oneconfig.internal.ui.components.IconKt;

import static org.junit.jupiter.api.Assertions.*;

class ModuleIconCatalogTest {
    @Test
    void builtinModuleAndHudAliasesResolveSharedResources() {
        for (String id : new String[]{"arcadium-auto-text.json", "arcadium-custom-f3.json", "arcadium-no-render.json", "polyhitbox.json"}) {
            String path = ModuleIconCatalog.forConfig(id);
            assertNotNull(path, id);
            assertTrue(IconKt.canRenderIcon(path), path);
            assertTrue(IconKt.canRenderIcon("/" + path), path);
        }
        assertTrue(IconKt.canRenderIcon(ModuleIconCatalog.forHud("ukus_fps", "FPS HUD")));
        assertTrue(IconKt.canRenderIcon(ModuleIconCatalog.forHud("health", "Health HUD")));
    }

    @Test
    void thirdPartyIdentitiesKeepTheirOwnArtwork() {
        assertNull(ModuleIconCatalog.forConfig("third-party-config.json"));
        assertNull(ModuleIconCatalog.forHud("third-party-stat", "Custom External Display"));
        assertNull(ModuleIconCatalog.forConfig("arcadium-players.json"));
    }

    @Test
    void classpathIconsAreNotMistakenForAbsoluteFilesystemPaths() {
        assertTrue(IconKt.canRenderIcon("/assets/oneconfig/ico/close.svg"));
        assertTrue(IconKt.canRenderIcon("assets/oneconfig/ico/close.svg"));
        assertFalse(IconKt.canRenderIcon("/assets/arcadium/lucide/not-an-icon.svg"));
    }
}
