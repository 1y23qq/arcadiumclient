// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.client.features;

import io.github.arcadiumclient.modern.client.ModernSettingsMigration;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.polyfrost.oneconfig.api.config.v1.Properties;
import org.polyfrost.oneconfig.api.config.v1.Tree;
import org.polyfrost.oneconfig.api.config.v1.backend.impl.FileBackend;
import org.polyfrost.oneconfig.api.config.v1.serialize.impl.NightConfigSerializer;
import static org.junit.jupiter.api.Assertions.*;

public class AnimationStorageTest {
    @TempDir Path folder;
    public static final class Category {
        public boolean active = true;
        public float scale = 1.65f;
    }
    public static final class NativeConfig {
        public final Category items = new Category(), movement = new Category(), screen = new Category(), fixes = new Category(), other = new Category();
    }
    private Tree tree(NativeConfig config) throws Exception {
        Tree tree = Tree.tree("overflowanimations.json");
        Tree items = tree.getOrPutChild("items");
        items.put(Properties.field(null, null, Category.class.getField("active"), config.items));
        items.put(Properties.field(null, null, Category.class.getField("scale"), config.items));
        return tree;
    }
    @Test void realStorageWritesOverflowChoicesWhileKeepingTheExactActiveTemporaryState() throws Exception {
        var config = new NativeConfig();
        var backend = new FileBackend(folder, NightConfigSerializer.JSON);
        Files.writeString(folder.resolve("overflowanimations.json"), "{\"items\":{\"active\":true,\"scale\":1.65,\"unknown\":17},\"future\":{\"retained\":true}}");
        Tree tree = backend.register(tree(config)).get();
        var bridge = new AnimationModeBridge(config);
        bridge.apply(Map.of("items.active", false, "items.scale", .4f));
        assertFalse(config.items.active); assertEquals(.4f, config.items.scale);
        assertTrue(bridge.withOriginalValues(() -> backend.save0(tree)));
        var saved = ModernSettingsMigration.read(folder.resolve("overflowanimations.json"));
        assertTrue(saved.getAsJsonObject("items").get("active").getAsBoolean());
        assertEquals(1.65f, saved.getAsJsonObject("items").get("scale").getAsFloat());
        assertEquals(17, saved.getAsJsonObject("items").get("unknown").getAsInt());
        assertTrue(saved.getAsJsonObject("future").get("retained").getAsBoolean());
        assertTrue(bridge.isAttached()); assertFalse(config.items.active); assertEquals(.4f, config.items.scale);
        bridge.restore(); assertTrue(config.items.active); assertEquals(1.65f, config.items.scale);
    }
    @Test void storageFailureAndExceptionRestoreExactLiveValuesWithoutRecapturingThem() throws Exception {
        var config = new NativeConfig();
        var bridge = new AnimationModeBridge(config);
        bridge.apply(Map.of("items.active", false, "items.scale", .4f));
        assertFalse(bridge.withOriginalValues(() -> false));
        assertTrue(bridge.isAttached()); assertFalse(config.items.active); assertEquals(.4f, config.items.scale);
        Path blocked = folder.resolve("blocked");
        Files.createDirectories(blocked.resolve("overflowanimations.json"));
        var backend = new FileBackend(blocked, NightConfigSerializer.JSON);
        Tree tree = tree(config);
        assertThrows(RuntimeException.class, () -> bridge.withOriginalValues(() -> backend.save0(tree)));
        assertTrue(bridge.isAttached()); assertFalse(config.items.active); assertEquals(.4f, config.items.scale);
        bridge.restore(); assertTrue(config.items.active); assertEquals(1.65f, config.items.scale);
    }
}
