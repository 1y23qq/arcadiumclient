// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.client.skins;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CapeClientTest {
    private static final UUID ACCOUNT = UUID.fromString("12345678-1234-1234-1234-123456789abc");
    private static final String TOKEN = "local-cape-test-token";
    private final AtomicReference<String> owner = new AtomicReference<>(ACCOUNT.toString().replace("-", ""));
    private final AtomicReference<String> selected = new AtomicReference<>("one");
    private final AtomicReference<String> texture = new AtomicReference<>("http://textures.minecraft.net/texture/" + "a".repeat(64));
    private final AtomicReference<String> authorization = new AtomicReference<>();
    private final AtomicReference<List<String>> owned = new AtomicReference<>(List.of("one", "two"));
    private final AtomicInteger writes = new AtomicInteger(), status = new AtomicInteger(200), redirects = new AtomicInteger();
    private final AtomicInteger mutationStatus = new AtomicInteger(200);
    private final AtomicBoolean ignoreCapeChanges = new AtomicBoolean(), changeAccountOnWrite = new AtomicBoolean();
    private final AtomicBoolean sameAccount = new AtomicBoolean(true);
    private HttpServer server;
    private CapeClient client;

    @BeforeEach void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/minecraft/profile", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            String method = exchange.getRequestMethod();
            int code = method.equals("GET") ? status.get() : mutationStatus.get();
            if (code != 200) {
                if (code == 302) exchange.getResponseHeaders().set("Location", "/redirected");
                exchange.sendResponseHeaders(code, -1); exchange.close(); return;
            }
            if (method.equals("PUT")) {
                assertEquals("/minecraft/profile/capes/active", exchange.getRequestURI().getPath());
                assertEquals("application/json", exchange.getRequestHeaders().getFirst("Content-Type"));
                JsonObject body = new JsonParser().parse(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
                if (!ignoreCapeChanges.get()) selected.set(body.get("capeId").getAsString());
                writes.incrementAndGet();
                if (changeAccountOnWrite.get()) sameAccount.set(false);
            } else if (method.equals("DELETE")) {
                assertEquals("/minecraft/profile/capes/active", exchange.getRequestURI().getPath());
                selected.set(null); writes.incrementAndGet();
                if (changeAccountOnWrite.get()) sameAccount.set(false);
                exchange.sendResponseHeaders(204, -1); exchange.close(); return;
            } else assertEquals("GET", method);
            StringBuilder capes = new StringBuilder();
            for (String id : owned.get()) {
                if (!capes.isEmpty()) capes.append(',');
                JsonObject cape = new JsonObject();
                cape.addProperty("id", id); cape.addProperty("alias", "Cape " + id);
                cape.addProperty("url", texture.get()); cape.addProperty("state", id.equals(selected.get()) ? "ACTIVE" : "INACTIVE");
                capes.append(cape);
            }
            byte[] body = ("{\"id\":\"" + owner.get() + "\",\"capes\":[" + capes + "]}").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length); exchange.getResponseBody().write(body); exchange.close();
        });
        server.createContext("/redirected", exchange -> { redirects.incrementAndGet(); exchange.sendResponseHeaders(204, -1); exchange.close(); });
        server.start();
        client = new CapeClient(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/minecraft/profile"));
    }

    @AfterEach void stop() { server.stop(0); }

    @Test void listsOwnedCapesEquipsOneAndCanRemoveIt() throws Exception {
        var profile = client.load(TOKEN, ACCOUNT, () -> true);
        assertEquals(2, profile.capes().size()); assertEquals("one", profile.activeId());
        assertEquals("https", profile.capes().getFirst().texture().getScheme());
        assertEquals("two", client.select(TOKEN, ACCOUNT, "two", () -> true).activeId());
        assertEquals("Bearer " + TOKEN, authorization.get());
        assertEquals(1, writes.get());
        assertNull(client.select(TOKEN, ACCOUNT, null, () -> true).activeId());
        assertEquals(2, writes.get());
        client.select(TOKEN, ACCOUNT, null, () -> true);
        assertEquals(2, writes.get());
    }

    @Test void rejectsUnownedCapesWrongAccountsAndAccountChangesWithoutMutation() throws Exception {
        assertThrows(IOException.class, () -> client.select(TOKEN, ACCOUNT, "not-owned", () -> true));
        owner.set("00000000000000000000000000000000");
        assertTrue(assertThrows(IOException.class, () -> client.select(TOKEN, ACCOUNT, "two", () -> true)).getMessage().contains("another Minecraft account"));
        owner.set(ACCOUNT.toString().replace("-", ""));
        AtomicInteger guards = new AtomicInteger();
        assertThrows(IOException.class, () -> client.select(TOKEN, ACCOUNT, "two", () -> guards.getAndIncrement() < 2));
        assertEquals(0, writes.get());
    }

    @Test void accountWithoutOwnedCapesKeepsNoCapeWithoutSendingAMutation() throws Exception {
        owned.set(List.of()); selected.set(null);
        var profile = client.load(TOKEN, ACCOUNT, () -> true);
        assertTrue(profile.capes().isEmpty()); assertNull(profile.activeId());
        assertTrue(client.select(TOKEN, ACCOUNT, null, () -> true).capes().isEmpty());
        assertTrue(assertThrows(IOException.class, () -> client.select(TOKEN, ACCOUNT, "one", () -> true))
            .getMessage().contains("owned by this Minecraft account"));
        assertEquals(0, writes.get());
    }

    @Test void rejectsPutWhoseProfileDoesNotConfirmTheSelectedCape() throws Exception {
        ignoreCapeChanges.set(true);
        IOException failure = assertThrows(IOException.class, () -> client.select(TOKEN, ACCOUNT, "two", () -> true));
        assertTrue(failure.getMessage().contains("did not confirm"));
        assertFalse(failure.getMessage().contains(TOKEN));
        assertEquals("one", selected.get()); assertEquals(1, writes.get());
    }

    @Test void completedMutationsStillReportTheCapturedTargetAfterAnInFlightAccountSwitch() throws Exception {
        changeAccountOnWrite.set(true);
        var equipped = client.select(TOKEN, ACCOUNT, "two", sameAccount::get);
        assertFalse(sameAccount.get()); assertEquals("two", equipped.activeId());
        assertEquals(ACCOUNT.toString().replace("-", ""), owner.get());
        assertEquals("Bearer " + TOKEN, authorization.get()); assertEquals(1, writes.get());
        sameAccount.set(true);
        var removed = client.select(TOKEN, ACCOUNT, null, sameAccount::get);
        assertFalse(sameAccount.get()); assertNull(removed.activeId());
        assertEquals(List.of("one", "two"), removed.capes().stream().map(CapeClient.Cape::id).toList());
        assertEquals(2, writes.get());
    }

    @Test void rejectsRateLimitsAndInvalidTokensWithoutChangingCapes() throws Exception {
        for (String invalid : new String[]{null, "", " ", "0", "NULL", TOKEN + (char) 0, TOKEN + "\n", "a".repeat(16_385)})
            assertThrows(IOException.class, () -> client.load(invalid, ACCOUNT, () -> true));
        assertNull(authorization.get());
        status.set(429);
        String failure = assertThrows(IOException.class, () -> client.load(TOKEN, ACCOUNT, () -> true)).getMessage();
        assertTrue(failure.contains("Too many cape changes")); assertFalse(failure.contains(TOKEN));
        status.set(200); mutationStatus.set(429);
        failure = assertThrows(IOException.class, () -> client.select(TOKEN, ACCOUNT, "two", () -> true)).getMessage();
        assertTrue(failure.contains("Too many cape changes")); assertFalse(failure.contains(TOKEN));
        assertEquals("one", selected.get()); assertEquals(0, writes.get());
    }

    @Test void refusesRedirectsInvalidTexturesAndCredentialInjection() throws Exception {
        status.set(302);
        assertThrows(IOException.class, () -> client.load(TOKEN, ACCOUNT, () -> true));
        assertEquals(0, redirects.get());
        status.set(401);
        String error = assertThrows(IOException.class, () -> client.load(TOKEN, ACCOUNT, () -> true)).getMessage();
        assertTrue(error.contains("expired")); assertFalse(error.contains(TOKEN));
        status.set(200); texture.set("https://example.invalid/cape.png");
        assertThrows(IOException.class, () -> client.load(TOKEN, ACCOUNT, () -> true));
        assertThrows(IOException.class, () -> client.load(TOKEN + "\r\nInjected: value", ACCOUNT, () -> true));
        assertThrows(IOException.class, () -> client.load("0", ACCOUNT, () -> true));
        assertThrows(IllegalArgumentException.class, () -> new CapeClient(URI.create("https://example.invalid/minecraft/profile")));
        assertEquals(0, writes.get());
    }

    @Test void capePreviewUsesBoundedPngDimensions() throws Exception {
        BufferedImage image = new BufferedImage(64, 32, BufferedImage.TYPE_INT_ARGB);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(); ImageIO.write(image, "PNG", bytes);
        assertEquals(64, CapeClient.decodeTexture(bytes.toByteArray()).getWidth());
        assertThrows(IOException.class, () -> CapeClient.decodeTexture(new byte[1_048_577]));
        assertThrows(IOException.class, () -> CapeClient.decodeTexture("not a png".getBytes(StandardCharsets.UTF_8)));
        bytes.reset(); ImageIO.write(new BufferedImage(32, 16, BufferedImage.TYPE_INT_ARGB), "PNG", bytes);
        byte[] small = bytes.toByteArray();
        assertThrows(IOException.class, () -> CapeClient.decodeTexture(small));
    }
}
