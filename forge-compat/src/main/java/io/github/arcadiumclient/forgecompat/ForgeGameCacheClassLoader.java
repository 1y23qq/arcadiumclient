package io.github.arcadiumclient.forgecompat;

import java.io.IOException;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.CodeSource;
import java.security.cert.Certificate;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LdcInsnNode;

final class ForgeGameCacheClassLoader extends URLClassLoader {
    private static final String HELPER = "net.fabricmc.loader.impl.game.GameProviderHelper";
    private static final int MAX_CLASS_BYTES = 1024 * 1024;
    private final String cacheRoot;

    ForgeGameCacheClassLoader(URL[] urls, String gameHash) {
        super(urls, ClassLoader.getPlatformClassLoader());
        if (!gameHash.matches("[a-f0-9]{64}")) throw new IllegalArgumentException("Invalid Forge game cache identity");
        cacheRoot = ".arcadium/forge-compat/game-cache/" + gameHash;
    }

    @Override protected Class<?> findClass(String name) throws ClassNotFoundException {
        if (!HELPER.equals(name)) return super.findClass(name);
        URL resource = findResource(name.replace('.', '/') + ".class");
        if (resource == null) throw new ClassNotFoundException(name);
        try {
            var connection = resource.openConnection();
            connection.setUseCaches(false);
            byte[] original;
            Certificate[] certificates;
            try (var input = connection.getInputStream()) {
                original = input.readNBytes(MAX_CLASS_BYTES + 1);
                certificates = connection instanceof JarURLConnection jar ? jar.getCertificates() : null;
            }
            if (original.length > MAX_CLASS_BYTES) throw new IOException("Fabric game cache helper exceeds its size limit");
            byte[] bytes = isolateCache(original, cacheRoot);
            URL origin = connection instanceof JarURLConnection jar ? jar.getJarFileURL() : resource;
            return defineClass(name, bytes, 0, bytes.length, new CodeSource(origin, certificates));
        } catch (IOException | RuntimeException failure) {
            throw new ClassNotFoundException("Could not isolate the Forge game cache", failure);
        }
    }

    static byte[] isolateCache(byte[] bytes, String cacheRoot) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        if (!node.name.equals(HELPER.replace('.', '/'))) throw new IllegalArgumentException("Unexpected Fabric game cache helper");
        int changed = 0;
        for (var method : node.methods) {
            if (!method.name.equals("getDeobfJarDir") || !method.desc.equals(
                    "(Ljava/nio/file/Path;Ljava/lang/String;Ljava/lang/String;)Ljava/nio/file/Path;")) continue;
            for (var instruction : method.instructions) {
                if (instruction instanceof LdcInsnNode value && ".fabric".equals(value.cst)) {
                    value.cst = cacheRoot;
                    changed++;
                }
            }
        }
        if (changed != 1) throw new IllegalStateException("This Fabric Loader version cannot isolate the Forge game cache");
        ClassWriter writer = new ClassWriter(0);
        node.accept(writer);
        return writer.toByteArray();
    }
}
