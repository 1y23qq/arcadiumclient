// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.client.skins;

import com.sun.net.httpserver.HttpServer;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.imageio.ImageIO;

public final class SkinServicesPureTest {
    private static final UUID ACCOUNT = UUID.fromString("12345678-1234-1234-1234-123456789abc");
    private static final String TOKEN = "local-test-token";

    public static void main(String[] args) throws Exception {
        Path testRoot = Path.of("build", "skin-services-pure", "library-" + UUID.randomUUID()).toAbsolutePath().normalize();
        library(testRoot);
        protocol();
        System.out.println("Skin service checks passed: PNG validation, library, multipart upload, account guards, HTTP errors, redirect refusal");
    }

    private static void library(Path root) throws Exception {
        Files.createDirectories(root);
        Path source = root.resolve("source.png");
        Files.write(source, png(64, 64));
        SkinLibrary library = new SkinLibrary(root.resolve("saved"));
        SkinLibrary.Entry first = library.add(source, "First", "slim");
        check(first.variant.equals("slim") && library.list().size() == 1, "Import preserves model and adds a named skin");
        library.edit(first.id, "Renamed", "classic");
        SkinLibrary reloaded = new SkinLibrary(root.resolve("saved"));
        check(reloaded.list().get(0).name.equals("Renamed"), "Renamed skins survive reload");
        check(reloaded.list().get(0).variant.equals("classic"), "Model changes survive reload");
        check(SkinLibrary.canonicalize(reloaded.png(first.id)).height() == 64, "Saved PNG is canonical and readable");
        String index = Files.readString(root.resolve("saved/index.json"));
        check(!index.contains("token") && !index.contains("Authorization"), "Saved index contains no account credentials");
        Path legacy = root.resolve("legacy.png");
        Files.write(legacy, png(64, 32));
        SkinLibrary.Entry second = library.add(legacy, "Legacy", "slim");
        check(second.variant.equals("classic") && second.height == 32, "Legacy skins use Classic");
        reject(() -> SkinLibrary.canonicalize(png(128, 128)), "64 by 64");
        reject(() -> SkinLibrary.canonicalize(new byte[]{1, 2, 3}), "PNG");
        reject(() -> reloaded.png("../../outside"), "found");
        reloaded.delete(first.id);
        check(!Files.exists(root.resolve("saved").resolve(first.id + ".png")), "Delete removes only the owned image");
        check(Files.exists(source), "Delete preserves imported source images");
        Path badRoot = root.resolve("bad");
        Files.createDirectories(badRoot);
        Files.writeString(badRoot.resolve("index.json"), "{\"skins\":[{\"id\":\"../../outside\",\"name\":\"bad\",\"variant\":\"classic\"}]}");
        reject(() -> new SkinLibrary(badRoot).list(), "path");
    }

    private static void protocol() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicInteger posts = new AtomicInteger(), redirects = new AtomicInteger();
        AtomicInteger profileStatus = new AtomicInteger(200), uploadStatus = new AtomicInteger(200);
        AtomicReference<String> account = new AtomicReference<>(ACCOUNT.toString().replace("-", ""));
        AtomicReference<String> requestType = new AtomicReference<>(), authorization = new AtomicReference<>();
        AtomicReference<byte[]> upload = new AtomicReference<>();
        server.createContext("/minecraft/profile", exchange -> {
            boolean post = exchange.getRequestMethod().equals("POST");
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            int status = post ? uploadStatus.get() : profileStatus.get();
            if (post) {
                posts.incrementAndGet();
                check(exchange.getRequestURI().getPath().equals("/minecraft/profile/skins"), "Upload uses the official skin path");
                requestType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
                upload.set(exchange.getRequestBody().readAllBytes());
            }
            if (status == 302) exchange.getResponseHeaders().set("Location", "http://127.0.0.1:" + server.getAddress().getPort() + "/redirected");
            byte[] response = ("{\"id\":\"" + account.get() + "\"}").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.createContext("/redirected", exchange -> { redirects.incrementAndGet(); exchange.sendResponseHeaders(204, -1); exchange.close(); });
        server.start();
        try {
            SkinUploadClient client = new SkinUploadClient(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/minecraft/profile"));
            byte[] image = png(64, 64);
            client.upload(TOKEN, ACCOUNT, image, "slim", () -> true);
            check(posts.get() == 1 && ("Bearer " + TOKEN).equals(authorization.get()), "Upload authenticates the active session once");
            String contentType = requestType.get();
            check(contentType.startsWith("multipart/form-data; boundary="), "Upload uses multipart encoding");
            String boundary = contentType.substring(contentType.indexOf("boundary=") + 9);
            String body = new String(upload.get(), StandardCharsets.ISO_8859_1);
            check(body.contains("name=\"variant\"\r\n\r\nslim\r\n"), "Multipart model is slim");
            check(body.contains("name=\"file\"; filename=\"skin.png\"\r\nContent-Type: image/png\r\n\r\n"), "Multipart file has correct name and MIME type");
            check(body.startsWith("--" + boundary + "\r\n") && body.endsWith("\r\n--" + boundary + "--\r\n"), "Multipart boundaries are complete");
            int start = body.indexOf("Content-Type: image/png\r\n\r\n") + "Content-Type: image/png\r\n\r\n".length();
            int end = body.lastIndexOf("\r\n--" + boundary + "--");
            SkinLibrary.canonicalize(java.util.Arrays.copyOfRange(upload.get(), start, end));
            account.set("00000000000000000000000000000000");
            reject(() -> client.upload(TOKEN, ACCOUNT, image, "classic", () -> true), "another Minecraft account");
            check(posts.get() == 1, "Mismatched account never sends a skin");
            account.set(ACCOUNT.toString().replace("-", ""));
            AtomicInteger guards = new AtomicInteger();
            reject(() -> client.upload(TOKEN, ACCOUNT, image, "classic", () -> guards.getAndIncrement() == 0), "active account changed");
            check(posts.get() == 1, "Account switch between verification and upload prevents POST");
            profileStatus.set(401);
            reject(() -> client.upload(TOKEN, ACCOUNT, image, "classic", () -> true), "session expired");
            profileStatus.set(302);
            reject(() -> client.upload(TOKEN, ACCOUNT, image, "classic", () -> true), "could not accept");
            check(redirects.get() == 0, "Credentials are never forwarded through redirects");
            profileStatus.set(200);
            uploadStatus.set(429);
            reject(() -> client.upload(TOKEN, ACCOUNT, image, "classic", () -> true), "Too many");
            reject(() -> client.upload(TOKEN + "\r\nInjected: value", ACCOUNT, image, "classic", () -> true), "Sign in");
        } finally { server.stop(0); }
    }

    private static byte[] png(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(8, 8, 0xFFAABBCC);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "PNG", out);
        return out.toByteArray();
    }

    @FunctionalInterface private interface Operation { void run() throws Exception; }

    private static void reject(Operation operation, String message) throws Exception {
        try { operation.run(); throw new AssertionError("Expected rejection: " + message); }
        catch (IOException expected) {
            check(expected.getMessage().contains(message), "Friendly failure: " + message);
            check(!expected.getMessage().contains(TOKEN), "Failures never expose the access token");
        }
    }

    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
