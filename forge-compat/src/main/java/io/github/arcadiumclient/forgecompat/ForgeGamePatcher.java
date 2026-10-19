package io.github.arcadiumclient.forgecompat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;
import java.util.zip.Adler32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.objectweb.asm.ClassReader;

public final class ForgeGamePatcher {
    public static final String CLIENT_SHA1 = "3870888a6c3d349d3771a3e9d16c9bf5e076b908";
    private static final int MAX_CLASS_BYTES = 16 * 1024 * 1024;
    private record Patch(String name, String source, String target, boolean exists, int checksum, byte[] delta) { }

    public Path patch(Path client, Path patches, Path cacheDirectory) throws IOException {
        if (!CLIENT_SHA1.equals(digest(client, "SHA-1"))) {
            throw new IOException("Forge compatibility requires the original Minecraft 1.8.9 client archive.");
        }
        Files.createDirectories(cacheDirectory);
        String patchHash = ForgeModScanner.sha256(patches);
        Path destination = cacheDirectory.resolve("minecraft-1.8.9-forge-" + patchHash.substring(0, 24) + ".jar");
        Path checksum = destination.resolveSibling(destination.getFileName() + ".sha256");
        if (Files.isRegularFile(destination) && Files.isRegularFile(checksum)
                && ForgeModScanner.sha256(destination).equals(Files.readString(checksum).trim())) return destination;

        Map<String, List<Patch>> bySource = readPatches(patches);
        Set<String> applied = new HashSet<>();
        Path temporary = Files.createTempFile(cacheDirectory, "minecraft-forge-", ".tmp");
        try (ZipFile vanilla = new ZipFile(client.toFile());
             JarOutputStream output = new JarOutputStream(Files.newOutputStream(temporary))) {
            var entries = vanilla.entries();
            Set<String> written = new HashSet<>();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                if (entry.isDirectory() || signature(name)) continue;
                byte[] bytes;
                try (InputStream input = vanilla.getInputStream(entry)) { bytes = limited(input, MAX_CLASS_BYTES); }
                String internal = name.endsWith(".class") ? name.substring(0, name.length() - 6) : "";
                if (bySource.containsKey(internal)) {
                    for (Patch patch : bySource.get(internal)) bytes = apply(patch, bytes);
                    applied.add(internal);
                }
                write(output, name, bytes, written);
            }
            for (var group : bySource.entrySet()) {
                if (applied.contains(group.getKey())) continue;
                byte[] bytes = new byte[0];
                for (Patch patch : group.getValue()) bytes = apply(patch, bytes);
                String className = new ClassReader(bytes).getClassName() + ".class";
                write(output, className, bytes, written);
            }
        } catch (Throwable failure) {
            Files.deleteIfExists(temporary);
            throw failure;
        }
        String hash = ForgeModScanner.sha256(temporary);
        move(temporary, destination);
        Path pendingChecksum = Files.createTempFile(cacheDirectory, "minecraft-forge-hash-", ".tmp");
        Files.writeString(pendingChecksum, hash + "\n", StandardCharsets.US_ASCII);
        move(pendingChecksum, checksum);
        return destination;
    }

    private static Map<String, List<Patch>> readPatches(Path path) throws IOException {
        Map<String, List<Patch>> result = new LinkedHashMap<>();
        try (JarInputStream input = new JarInputStream(Files.newInputStream(path))) {
            JarEntry entry;
            int count = 0;
            while ((entry = input.getNextJarEntry()) != null) {
                if (!entry.getName().startsWith("binpatch/client/") || !entry.getName().endsWith(".binpatch")) continue;
                if (++count > 10_000) throw new IOException("Forge patch archive exceeds the entry limit.");
                DataInputStream data = new DataInputStream(new ByteArrayInputStream(limited(input, MAX_CLASS_BYTES)));
                String name = data.readUTF();
                String source = data.readUTF().replace('.', '/');
                String target = data.readUTF().replace('.', '/');
                boolean exists = data.readBoolean();
                int checksum = exists ? data.readInt() : 0;
                int length = data.readInt();
                if (!source.matches("[a-zA-Z0-9_$/]+") || length < 0 || length > MAX_CLASS_BYTES) {
                    throw new IOException("Invalid Forge patch: " + name);
                }
                byte[] delta = data.readNBytes(length);
                if (delta.length != length || data.read() != -1) throw new IOException("Truncated Forge patch: " + name);
                result.computeIfAbsent(source, ignored -> new ArrayList<>()).add(new Patch(name, source, target, exists, checksum, delta));
            }
        }
        if (result.isEmpty()) throw new IOException("The Forge patch archive contains no client patches.");
        return result;
    }

    private static byte[] apply(Patch patch, byte[] original) throws IOException {
        if (patch.exists) {
            Adler32 adler = new Adler32();
            adler.update(original);
            if ((int) adler.getValue() != patch.checksum) {
                throw new IOException("Minecraft class verification failed for Forge patch " + patch.name + ".");
            }
        } else if (original.length != 0) {
            throw new IOException("Forge patch would replace an unexpected class: " + patch.name);
        }
        byte[] transformed = applyDelta(original, patch.delta);
        if (transformed.length < 8 || new ClassReader(transformed).getClassName().isBlank()) {
            throw new IOException("Forge patch produced an invalid class: " + patch.name);
        }
        return transformed;
    }

    static byte[] applyDelta(byte[] source, byte[] patch) throws IOException {
        DataInputStream input = new DataInputStream(new ByteArrayInputStream(patch));
        if (input.readInt() != 0xD1FFD1FF || input.readUnsignedByte() != 4) {
            throw new IOException("Unsupported Forge binary patch format.");
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        while (true) {
            int command = input.readUnsignedByte();
            if (command == 0) {
                if (input.read() != -1) throw new IOException("Unexpected bytes after Forge binary patch.");
                return output.toByteArray();
            }
            if (command <= 248) {
                int length = command <= 246 ? command : command == 247 ? input.readUnsignedShort() : input.readInt();
                limit(output.size(), length);
                byte[] data = input.readNBytes(length);
                if (data.length != length) throw new EOFException("Truncated Forge binary patch literal.");
                output.write(data);
            } else {
                long offset = command <= 251 ? input.readUnsignedShort()
                        : command <= 254 ? Integer.toUnsignedLong(input.readInt()) : input.readLong();
                int length = switch (command) {
                    case 249, 252 -> input.readUnsignedByte();
                    case 250, 253 -> input.readUnsignedShort();
                    default -> input.readInt();
                };
                limit(output.size(), length);
                if (offset < 0 || offset > source.length || length > source.length - offset) {
                    throw new IOException("Forge binary patch references data outside its source class.");
                }
                output.write(source, (int) offset, length);
            }
        }
    }

    private static void limit(int size, int length) throws IOException {
        if (length < 0 || length > MAX_CLASS_BYTES - size) throw new IOException("Forge binary patch exceeds the class size limit.");
    }

    private static byte[] limited(InputStream input, int limit) throws IOException {
        byte[] bytes = input.readNBytes(limit + 1);
        if (bytes.length > limit) throw new IOException("Forge compatibility entry exceeds the size limit.");
        return bytes;
    }

    private static boolean signature(String name) {
        String upper = name.toUpperCase(java.util.Locale.ROOT);
        return upper.startsWith("META-INF/") && (upper.endsWith(".SF") || upper.endsWith(".RSA")
                || upper.endsWith(".DSA") || upper.equals("META-INF/MANIFEST.MF"));
    }

    private static void write(JarOutputStream output, String name, byte[] data, Set<String> names) throws IOException {
        if (!names.add(name)) throw new IOException("Duplicate patched Minecraft entry: " + name);
        JarEntry entry = new JarEntry(name);
        entry.setTime(0);
        output.putNextEntry(entry);
        output.write(data);
        output.closeEntry();
    }

    private static String digest(Path path, String algorithm) throws IOException {
        try (InputStream input = Files.newInputStream(path)) {
            MessageDigest digest = MessageDigest.getInstance(algorithm);
            byte[] buffer = new byte[64 * 1024];
            int length;
            while ((length = input.read(buffer)) >= 0) digest.update(buffer, 0, length);
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }

    private static void move(Path source, Path target) throws IOException {
        try { Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
        catch (java.nio.file.AtomicMoveNotSupportedException e) { Files.move(source, target, StandardCopyOption.REPLACE_EXISTING); }
    }
}
