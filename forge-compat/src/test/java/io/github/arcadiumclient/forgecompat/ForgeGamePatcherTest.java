package io.github.arcadiumclient.forgecompat;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;

class ForgeGamePatcherTest {
    @TempDir Path temporary;

    @Test void appliesLiteralAndEveryCopyEncoding() throws Exception {
        byte[] source = "0123456789abcdef".getBytes();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream patch = new DataOutputStream(bytes);
        patch.writeInt(0xD1FFD1FF); patch.writeByte(4);
        patch.writeByte(3); patch.writeBytes("abc");
        for (int command = 249; command <= 255; command++) {
            patch.writeByte(command);
            if (command <= 251) patch.writeShort(2);
            else if (command <= 254) patch.writeInt(2);
            else patch.writeLong(2);
            if (command == 249 || command == 252) patch.writeByte(3);
            else if (command == 250 || command == 253) patch.writeShort(3);
            else patch.writeInt(3);
        }
        patch.writeByte(247); patch.writeShort(2); patch.writeBytes("hi");
        patch.writeByte(248); patch.writeInt(2); patch.writeBytes("jk");
        patch.writeByte(0);
        assertEquals("abc" + "234".repeat(7) + "hijk",
                new String(ForgeGamePatcher.applyDelta(source, bytes.toByteArray())));
    }

    @Test void rejectsInvalidMagicTruncationAndSourceOverreads() throws Exception {
        assertThrows(IOException.class, () -> ForgeGamePatcher.applyDelta(new byte[0], new byte[8]));
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream patch = new DataOutputStream(bytes);
        patch.writeInt(0xD1FFD1FF); patch.writeByte(4); patch.writeByte(249);
        patch.writeShort(4); patch.writeByte(2); patch.writeByte(0);
        assertThrows(IOException.class, () -> ForgeGamePatcher.applyDelta(new byte[5], bytes.toByteArray()));
        assertThrows(IOException.class, () -> ForgeGamePatcher.applyDelta(new byte[9], java.util.Arrays.copyOf(bytes.toByteArray(), 7)));
    }

    @Test void appliesOfficialPatchesToTheRealClientWithoutLoadingMinecraftClasses() throws Exception {
        Path vanilla = Path.of(System.getProperty("user.home"), ".gradle/caches/fabric-loom/1.8.9/minecraft-client.jar");
        Assumptions.assumeTrue(Files.isRegularFile(vanilla));
        Path patches = temporary.resolve("patches.jar");
        try (var input = getClass().getResourceAsStream("/META-INF/arcadium/forge-game-patches.jar")) {
            assertNotNull(input);
            Files.copy(input, patches);
        }
        Path patched = new ForgeGamePatcher().patch(vanilla, patches, temporary.resolve("cache"));
        int changed = 0;
        boolean registryApi = false;
        try (ZipFile before = new ZipFile(vanilla.toFile()); ZipFile after = new ZipFile(patched.toFile())) {
            for (var entry : after.stream().filter(entry -> entry.getName().endsWith(".class")).toList()) {
                byte[] result = after.getInputStream(entry).readAllBytes();
                var original = before.getEntry(entry.getName());
                if (original == null || !java.util.Arrays.equals(result, before.getInputStream(original).readAllBytes())) changed++;
                ClassNode node = new ClassNode(Opcodes.ASM9);
                new ClassReader(result).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                registryApi |= node.methods.stream().anyMatch(method -> method.name.equals("getRegistryName"));
            }
        }
        assertTrue(changed > 100, "The real Forge class patches must be applied.");
        assertTrue(registryApi, "Forge registry APIs must be added to the vanilla game.");
        String expected = ForgeModScanner.sha256(patched);
        assertEquals(patched, new ForgeGamePatcher().patch(vanilla, patches, temporary.resolve("cache")));
        Files.write(patched, new byte[]{1, 2, 3});
        assertEquals(expected, ForgeModScanner.sha256(new ForgeGamePatcher().patch(vanilla, patches, temporary.resolve("cache"))));
    }
}
