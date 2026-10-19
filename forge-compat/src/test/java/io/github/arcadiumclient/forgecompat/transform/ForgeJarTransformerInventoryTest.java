package io.github.arcadiumclient.forgecompat.transform;

import io.github.arcadiumclient.forgecompat.ForgeTestFixtures;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.arcadiumclient.forgecompat.mapping.ForgeMappings;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.tree.ClassNode;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.jar.Manifest;

import static io.github.arcadiumclient.forgecompat.transform.TransformerFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class ForgeJarTransformerInventoryTest {
    @TempDir Path cacheDirectory;

    @Test void transformsAllRealInputsAndVerifiesEveryRetainedClassWithoutDefiningGuestClasses() throws Exception {
        Path root = Path.of("..").toAbsolutePath().normalize();
        Path dependencies = root.resolve("build/forge-compat/dependencies");
        Path srg = dependencies.resolve("forge-1.8.9.srg");
        Path loom = Path.of(System.getProperty("user.home"), ".gradle/caches/fabric-loom/1.8.9");
        Path tiny = loom.resolve("calamus-gen2-intermediary-v2.tiny");
        Path vanilla = loom.resolve("minecraft-merged.jar");
        Path forge = dependencies.resolve("forge-1.8.9-11.15.1.2318-1.8.9-universal.jar");
        List<Path> libraries = List.of(dependencies.resolve("CodeChickenCore-1.8.9-2.0.2.null-universal.jar"),
            dependencies.resolve("CodeChickenLib-1.8.9-2.0.2.1-universal.jar"), dependencies.resolve("Waila-1.6.0-B3_1.8.8.jar"));
        Path modDirectory = ForgeTestFixtures.optionalGuestDirectory();
        Assumptions.assumeTrue(Files.isRegularFile(srg) && Files.isRegularFile(tiny) && Files.isRegularFile(vanilla)
            && Files.isRegularFile(forge) && Files.isDirectory(modDirectory) && libraries.stream().allMatch(Files::isRegularFile));
        List<Path> mods;
        try (var files = Files.list(modDirectory)) { mods = files.filter(path -> path.toString().endsWith(".jar")).sorted().toList(); }
        assertEquals(5, mods.size());
        ForgeMappings mappings;
        try (var reader = Files.newBufferedReader(srg)) {
            mappings = new ForgeMappings(resolver("intermediary", Files.readString(tiny)), reader);
        }
        ForgeJarTransformer transformer = new ForgeJarTransformer();
        transformer.index(vanilla, mappings, true);
        transformer.index(forge, mappings, true);
        for (Path library : libraries) transformer.index(library, mappings, false);
        for (Path mod : mods) transformer.index(mod, mappings, false);
        Map<String, Path> outputs = new LinkedHashMap<>();
        AtomicInteger trustedCalls = new AtomicInteger();
        outputs.put(forge.getFileName().toString(), transformer.transform(forge, cacheDirectory, mappings, true, node -> trustedCalls.incrementAndGet()));
        for (Path library : libraries) outputs.put(library.getFileName().toString(), transformer.transform(library, cacheDirectory, mappings, false, null));
        for (Path mod : mods) outputs.put(mod.getFileName().toString(), transformer.transform(mod, cacheDirectory, mappings, false, null));
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (var entry : outputs.entrySet()) counts.put(entry.getKey(), verify(entry.getValue()));
        assertEquals(1142, trustedCalls.get());
        assertEquals(3459, counts.values().stream().mapToInt(Integer::intValue).sum());
        assertEquals(822, counts.get("raven-bS-17 (13).jar"));
        Path raven = outputs.get("raven-bS-17 (13).jar");
        Map<String, byte[]> resources = entries(raven);
        assertFalse(resources.keySet().stream().anyMatch(name -> name.startsWith("org/spongepowered/asm/")
            || name.startsWith("org/spongepowered/tools/") || name.startsWith("org/objectweb/asm/")));
        Manifest manifest = new Manifest(new java.io.ByteArrayInputStream(resources.get("META-INF/MANIFEST.MF")));
        assertNull(manifest.getMainAttributes().getValue("TweakClass"));
        assertEquals("mixins.raven.json", manifest.getMainAttributes().getValue("MixinConfigs"));
        JsonObject config = new JsonParser().parse(new String(resources.get("mixins.raven.json"), StandardCharsets.UTF_8)).getAsJsonObject();
        JsonObject originalConfig = new JsonParser().parse(new String(entries(modDirectory.resolve("raven-bS-17 (13).jar")).get("mixins.raven.json"), StandardCharsets.UTF_8)).getAsJsonObject();
        JsonObject expectedOverwrites = originalConfig.has("overwrites") ? originalConfig.getAsJsonObject("overwrites") : new JsonObject();
        expectedOverwrites.addProperty("conformVisibility", true); originalConfig.add("overwrites", expectedOverwrites);
        assertEquals(originalConfig, config);
        JsonObject refmap = new JsonParser().parse(new String(resources.get("mixins.raven.refmap.json"), StandardCharsets.UTF_8)).getAsJsonObject();
        String mixin = "keystrokesmod/mixin/impl/client/MixinMinecraft";
        String expectedTick = mappings.mixinSelectorFromSrg("Lnet/minecraft/client/Minecraft;func_71407_l()V");
        assertEquals(expectedTick, refmap.getAsJsonObject("mappings").getAsJsonObject(mixin).get("runTick").getAsString());
        ClassNode accessor = node(resources.get("keystrokesmod/mixin/impl/accessor/IAccessorMinecraft.class"));
        String expectedTimer = mappings.fieldFromSrg(GAME, "field_71428_T", "Lnet/minecraft/util/Timer;");
        boolean timerMapped = accessor.methods.stream().flatMap(method -> method.visibleAnnotations == null ? java.util.stream.Stream.empty() : method.visibleAnnotations.stream())
            .anyMatch(annotation -> annotation.values != null && annotation.values.contains(expectedTimer));
        assertTrue(timerMapped, "Real Raven timer accessor must target the host field");
        assertEquals(refmap.get("mappings"), refmap.getAsJsonObject("data").get("named:intermediary"));
        System.out.println("Real Forge JAR transform retained classes (3965 source, 506 delegated): " + counts);
    }
}
