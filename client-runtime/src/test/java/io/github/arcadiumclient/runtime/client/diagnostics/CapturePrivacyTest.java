// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.diagnostics;

import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.time.Instant;
import java.util.*;
import java.util.zip.*;
import jdk.jfr.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.polyfrost.oneconfig.api.config.v1.*;
import org.polyfrost.oneconfig.api.config.v1.Properties;
import static org.junit.jupiter.api.Assertions.*;

class CapturePrivacyTest {
    @TempDir Path temporary;
    private static volatile Object blackhole;
    @Name("arcadium.test.PrivateEvent") static class PrivateEvent extends Event { String password; String address; }

    @Test void arbitraryTextTokensKeybindsAndNonfiniteNumbersCannotEnterNumericSettings() {
        Map<String, Node> nodes = new HashMap<>();
        nodes.put("enabled", Properties.simple(true)); nodes.put("minimumPlayers", Properties.simple(1));
        nodes.put("text", Properties.simple((Object) "private-chat-content")); nodes.put("token", Properties.simple((Object) "secret-token"));
        nodes.put("passwordLength", Properties.simple(30)); nodes.put("uuid", Properties.simple(4321));
        nodes.put("apiKey", Properties.simple(12345)); nodes.put("prefix", Properties.simple((Object) "PlayerName"));
        nodes.put("badNumber", Properties.simple(Double.NaN)); nodes.put("keybind", Properties.simple(new Object()));
        nodes.put("path/name", Properties.simple(25));
        assertEquals(Map.of("enabled", true, "minimumPlayers", 1), CaptureContext.numericTree(new Tree("test", null, null, nodes), 0));
    }

    @Test void jvmFlagAllowlistRejectsCredentialsPathsAgentsAndShellCommands() {
        var actual = CaptureContext.safeJvmFlags(List.of("-Xmx4G", "-Xms512M", "-XX:+UseG1GC", "-XX:MaxGCPauseMillis=80",
            "-DaccessToken=secret", "-Duser.home=C:\\PrivateName", "-javaagent:C:\\PrivateAgent.jar", "-XX:OnError=upload-secrets", "--username=PrivateName"));
        assertEquals(List.of("-Xmx4G", "-Xms512M", "-XX:+UseG1GC", "-XX:MaxGCPauseMillis=80"), actual);
    }

    @Test void idleWorkerParksCannotEvictGarbageCollectionEvidence() {
        try (var profile = new CaptureProfile(Thread.currentThread().threadId())) {
            for (int i = 0; i < 10000; i++) profile.recordPause("jdk.ThreadPark", i / 100.0, 20_000_000L, "worker");
            profile.recordPause("jdk.GCPhasePause", 99, 100_000_000L, "jvm");
            var details = profile.details();
            assertEquals(513, ((List<?>) details.get("pauseEvents")).size());
            assertEquals(9488L, details.get("omittedPauseEvents"));
            var totals = (Map<?, ?>) details.get("pauseDurations");
            assertEquals(200_000.0, ((Map<?, ?>) totals.get("jdk.ThreadPark")).get("totalMs"));
            assertEquals(100.0, ((Map<?, ?>) totals.get("jdk.GCPhasePause")).get("totalMs"));
        }
    }

    @Test void realSamplingExportsOnlyAllowedFieldsEvenWhenAnotherRecordingEnablesPrivateEvents() throws Exception {
        String secret = "DIAGNOSTIC_SECRET_46c9cba2", oldThread = Thread.currentThread().getName();
        System.setProperty("arcadium.capture.test.secret", secret); Thread.currentThread().setName(secret);
        try (Recording other = new Recording(); CaptureProfile profile = new CaptureProfile(Thread.currentThread().threadId())) {
            other.enable(PrivateEvent.class); other.enable("jdk.JVMInformation"); other.start();
            profile.open(); profile.begin(Instant.now());
            PrivateEvent event = new PrivateEvent(); event.password = secret; event.address = "private.example.test"; event.commit();
            long until = System.nanoTime() + 2_500_000_000L;
            long value = 41;
            while (System.nanoTime() < until) { value = arithmetic(value); byte[] bytes = new byte[8192]; bytes[(int) (value & 8191)] = 1; blackhole = bytes; }
            profile.end(Instant.now()); profile.finish();
            var summary = profile.summary();
            assertNotNull(((Map<?, ?>) summary.get("eventCounts")).get("jdk.ExecutionSample"), summary.toString());
            assertTrue(((Number) ((Map<?, ?>) summary.get("eventCounts")).get("jdk.ExecutionSample")).longValue() > 0, summary.toString());
            var data = new CaptureData(Map.of("minecraftVersion", "1.8.9"), Map.of("minimumPlayers", 1), Map.of(), 2);
            data.frame(1_000_000, 1_000_000, 3, new long[10], new long[10], -1, 1_000_000); data.close(2_500_000_000L, "manual");
            Path output = CaptureArchive.write(temporary, data, profile, 30);
            try (ZipFile archive = new ZipFile(output.toFile())) {
                assertEquals(7, archive.size());
                StringBuilder contents = new StringBuilder();
                var manifest = JsonParser.parseString(new String(archive.getInputStream(archive.getEntry("manifest.json")).readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("files");
                for (var entry : Collections.list(archive.entries())) {
                    assertFalse(entry.getName().endsWith(".jfr")); assertFalse(entry.getName().contains("/"));
                    byte[] bytes = archive.getInputStream(entry).readAllBytes(); contents.append(new String(bytes, StandardCharsets.UTF_8));
                    if (!entry.getName().equals("manifest.json")) {
                        var check = manifest.getAsJsonObject(entry.getName());
                        assertEquals(bytes.length, check.get("bytes").getAsInt());
                        assertEquals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)), check.get("sha256").getAsString());
                    }
                }
                assertFalse(contents.toString().contains(secret)); assertFalse(contents.toString().contains("private.example.test"));
                assertFalse(contents.toString().contains("arcadium.test.PrivateEvent"));
                assertTrue(contents.toString().contains("CapturePrivacyTest"));
            }
            try (var files = Files.list(temporary)) { assertEquals(1, files.count()); }
        } finally { Thread.currentThread().setName(oldThread); System.clearProperty("arcadium.capture.test.secret"); }
    }

    private static long arithmetic(long value) {
        for (int i = 0; i < 200000; i++) value = Long.rotateLeft(value * 6364136223846793005L + i, 13) ^ (value >>> 7);
        return value;
    }
}
