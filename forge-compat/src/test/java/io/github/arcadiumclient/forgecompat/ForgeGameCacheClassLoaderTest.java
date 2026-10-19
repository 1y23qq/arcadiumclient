package io.github.arcadiumclient.forgecompat;

import java.net.URL;
import java.nio.file.Path;
import java.util.Arrays;
import net.fabricmc.loader.impl.game.GameProviderHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;

import static org.junit.jupiter.api.Assertions.*;

final class ForgeGameCacheClassLoaderTest {
    @TempDir Path directory;
    private static final String HELPER = "net.fabricmc.loader.impl.game.GameProviderHelper";

    @Test void actualLoaderUsesSeparateCachesForVanillaAndPatchedGameIdentities() throws Exception {
        URL library = GameProviderHelper.class.getProtectionDomain().getCodeSource().getLocation();
        var original = GameProviderHelper.class.getDeclaredMethod("getDeobfJarDir", Path.class, String.class, String.class);
        original.setAccessible(true);
        Path vanilla = (Path) original.invoke(null, directory, "minecraft", "1.8.9");
        assertTrue(vanilla.startsWith(directory.resolve(".fabric/remappedJars")));
        for (String hash : Arrays.asList("a".repeat(64), "b".repeat(64))) {
            try (var loader = new ForgeGameCacheClassLoader(new URL[] {library}, hash)) {
                Class<?> helper = loader.loadClass(HELPER);
                assertSame(loader, helper.getClassLoader());
                assertEquals(library, helper.getProtectionDomain().getCodeSource().getLocation());
                var method = helper.getDeclaredMethod("getDeobfJarDir", Path.class, String.class, String.class);
                method.setAccessible(true);
                Path patched = (Path) method.invoke(null, directory, "minecraft", "1.8.9");
                assertEquals(directory.resolve(".arcadium/forge-compat/game-cache/" + hash + "/remappedJars")
                    .resolve(vanilla.getFileName()), patched);
                assertNotEquals(vanilla, patched);
            }
        }
        assertEquals(vanilla, original.invoke(null, directory, "minecraft", "1.8.9"));
    }

    @Test void rejectsUnknownHelperShapesAndUnsafeCacheIdentities() throws Exception {
        byte[] bytes;
        try (var input = GameProviderHelper.class.getResourceAsStream("GameProviderHelper.class")) {
            assertNotNull(input); bytes = input.readAllBytes();
        }
        ClassNode node = new ClassNode(); new ClassReader(bytes).accept(node, 0);
        node.methods.removeIf(method -> method.name.equals("getDeobfJarDir"));
        ClassWriter writer = new ClassWriter(0); node.accept(writer);
        byte[] unknown = writer.toByteArray();
        assertThrows(IllegalStateException.class, () -> ForgeGameCacheClassLoader.isolateCache(unknown, "cache"));
        node.name = "unexpected/Helper"; writer = new ClassWriter(0); node.accept(writer);
        byte[] renamed = writer.toByteArray();
        assertThrows(IllegalArgumentException.class, () -> ForgeGameCacheClassLoader.isolateCache(renamed, "cache"));
        assertThrows(IllegalArgumentException.class, () -> new ForgeGameCacheClassLoader(new URL[0], "../other"));
    }
}
