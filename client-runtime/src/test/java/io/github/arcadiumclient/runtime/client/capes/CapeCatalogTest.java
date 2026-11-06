// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.capes;

import static org.junit.jupiter.api.Assertions.*;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class CapeCatalogTest {
    @Test void everyShippedTextureMatchesItsOriginalAssetAndNativeCapeDimensions() throws Exception {
        var entries = CapeCatalog.entries();
        assertEquals(55, entries.size());
        var ids = new HashSet<String>();
        for (var entry : entries) {
            assertTrue(ids.add(entry.id()));
            assertSame(entry, CapeCatalog.find(entry.id()));
            byte[] bytes;
            try (var stream = getClass().getClassLoader().getResourceAsStream(entry.resource())) {
                assertNotNull(stream, entry.id());
                bytes = stream.readAllBytes();
            }
            assertEquals(entry.sha256(), HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)), entry.id());
            var image = ImageIO.read(new java.io.ByteArrayInputStream(bytes));
            assertNotNull(image, entry.id());
            assertEquals(64, image.getWidth(), entry.id());
            assertEquals(32, image.getHeight(), entry.id());
            assertEquals("capes/official/" + entry.id() + ".png", entry.texturePath());
            assertTrue(entry.source().startsWith("https://textures.minecraft.net/texture/")
                || entry.source().startsWith("https://www.mc-craft.com/assets/img/capes/"));
        }
    }
    @Test void includesCurrentAndHistoricalStandaloneEditionCapes() {
        for (String id : new String[]{"minecon-2011", "minecon-2012", "minecon-2013", "minecon-2015", "minecon-2016",
                "mojang-classic", "migrator", "vanilla", "cherry-blossom", "progress-pride", "christmas-2010",
                "new-year-2011", "xbox-first-birthday", "microsoft-xbox", "4j-studios", "hero", "twisted", "aurora"})
            assertNotNull(CapeCatalog.find(id), id);
        assertNull(CapeCatalog.find("test"));
        assertNull(CapeCatalog.find("../../other"));
        assertNull(CapeCatalog.find(null));
    }
    @Test void localIdentityIsRequiredEvenWhenOtherPlayersHaveCapes() {
        UUID self = new UUID(1, 2), other = new UUID(3, 4);
        assertTrue(LocalCapePolicy.applies(true, true, self, self, false));
        assertFalse(LocalCapePolicy.applies(true, true, other, self, false));
        assertFalse(LocalCapePolicy.applies(true, true, null, self, false));
        assertFalse(LocalCapePolicy.applies(true, true, self, null, false));
    }
    @Test void disablingUnknownSelectionOrAccountPreviewRetainsOriginalCape() {
        UUID self = new UUID(1, 2);
        assertFalse(LocalCapePolicy.applies(false, true, self, self, false));
        assertFalse(LocalCapePolicy.applies(true, false, self, self, false));
        assertFalse(LocalCapePolicy.applies(true, true, self, self, true));
    }
}
