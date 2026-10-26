// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.forge1710.bootstrap;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.net.JarURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

public final class HostedMixinRuntime {
    private static Class<?> verifiedCaller;
    private static String verifiedWarning;

    private HostedMixinRuntime() { }

    public static List<String> checkContainer(List<String> errors, Class<?> caller) {
        if (errors.size() != 1) return errors;
        return filter(errors, acceptedWarning(caller));
    }

    static List<String> filter(List<String> errors, String expectedWarning) {
        return errors.size() == 1 && expectedWarning != null && expectedWarning.equals(errors.get(0)) ? List.of() : errors;
    }

    private static synchronized String acceptedWarning(Class<?> caller) {
        if (verifiedCaller == caller) return verifiedWarning;
        verifiedCaller = caller;
        verifiedWarning = null;
        try {
            if (!caller.getName().equals("io.github.legacymoddingmc.unimixins.mixin.MixinSanityCheck")) return null;
            URL coreResource = caller.getResource("/io/github/legacymoddingmc/unimixins/mixin/MixinCore.class");
            URL tweakerResource = caller.getResource("/org/spongepowered/asm/launch/MixinTweaker.class");
            Path core = resourceArchive(coreResource), engine = resourceArchive(tweakerResource);
            ClassLoader loader = caller.getClassLoader();
            Class<?> environment = Class.forName("org.spongepowered.asm.mixin.MixinEnvironment", false, loader);
            Class<?> tweaker = Class.forName("org.spongepowered.asm.launch.MixinTweaker", false, loader);
            if (Boolean.getBoolean("arcadium.forge.debugBootstrap"))
                System.out.println("[Arcadium] Mixin sources: core=" + core + ", caller=" + classArchive(caller)
                    + ", engine=" + engine + ", environment=" + classArchive(environment) + ", tweaker=" + classArchive(tweaker));
            if (!Files.isSameFile(core, classArchive(caller)) || !Files.isSameFile(engine, classArchive(environment))
                    || !Files.isSameFile(engine, classArchive(tweaker))) return null;
            if (!matches(core, pin("arcadium-unimixins-runtime.sha256")) || !matches(engine, pin("arcadium-mixin-runtime.sha256"))) return null;
            verifiedWarning = warning(jarName(coreResource), jarName(tweakerResource));
            System.out.println("[Arcadium] Verified the hosted Mixin runtime for UniMixins.");
        } catch (Exception error) {
            if (Boolean.getBoolean("arcadium.forge.debugBootstrap")) error.printStackTrace();
        }
        return verifiedWarning;
    }

    private static String pin(String name) throws Exception {
        try (var input = HostedMixinRuntime.class.getResourceAsStream("/" + name)) {
            if (input == null) throw new java.io.IOException("Missing runtime pin: " + name);
            return new String(input.readAllBytes(), StandardCharsets.US_ASCII).trim();
        }
    }

    private static Path classArchive(Class<?> type) throws Exception {
        URL location = type.getProtectionDomain().getCodeSource().getLocation();
        if ("jar".equals(location.getProtocol())) return resourceArchive(location);
        if (!"file".equals(location.getProtocol())) throw new java.io.IOException("Unknown runtime class source");
        return Path.of(location.toURI());
    }

    private static Path resourceArchive(URL resource) throws Exception {
        if (resource == null || !"jar".equals(resource.getProtocol()) || !(resource.openConnection() instanceof JarURLConnection jar))
            throw new java.io.IOException("Unknown runtime resource source");
        URL location = jar.getJarFileURL();
        if (!"file".equals(location.getProtocol())) throw new java.io.IOException("Unknown runtime archive source");
        return Path.of(location.toURI());
    }

    private static String jarName(URL resource) {
        String url = resource.toString();
        String archive = url.substring(0, url.indexOf("!/"));
        return archive.substring(archive.lastIndexOf('/') + 1);
    }

    static String warning(String core, String tweaker) {
        String warning = "A different version of Mixin (the one inside " + tweaker
            + ") is getting loaded instead of UniMixins's one (inside " + core + ")!";
        return core.compareToIgnoreCase(tweaker) > 0 ? warning
            + " This is probably because because UniMixins's jar name comes later alphabetically. Try renaming it to come first (for example, by adding a '!' character at the beginning of the file name)." : warning;
    }

    static boolean matches(Path archive, String expected) throws Exception {
        if (!expected.matches("[a-f0-9]{64}") || !Files.isRegularFile(archive) || Files.size(archive) > 64 * 1024 * 1024) return false;
        var digest = MessageDigest.getInstance("SHA-256");
        try (var input = Files.newInputStream(archive)) {
            byte[] buffer = new byte[64 * 1024];
            for (int read; (read = input.read(buffer)) != -1;) digest.update(buffer, 0, read);
        }
        return expected.equals(HexFormat.of().formatHex(digest.digest()));
    }
}
