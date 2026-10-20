// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.client.skins;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.function.BooleanSupplier;

public final class SkinUploadClient {
    static final URI OFFICIAL = URI.create("https://api.minecraftservices.com/minecraft/profile");
    private final URI profile;
    private static final java.util.concurrent.atomic.AtomicInteger REQUESTS = new java.util.concurrent.atomic.AtomicInteger();

    public static int requestCount() { return REQUESTS.get(); }

    public SkinUploadClient() { this(OFFICIAL); }

    SkinUploadClient(URI profile) {
        this.profile = validateEndpoint(profile);
    }

    static URI validateEndpoint(URI profile) {
        if (!profile.equals(OFFICIAL) && !("http".equals(profile.getScheme()) && "127.0.0.1".equals(profile.getHost())))
            throw new IllegalArgumentException("Unsupported skin service");
        return profile;
    }

    static void validateToken(String token) throws IOException {
        if (token == null || token.isBlank() || token.equals("0") || token.equalsIgnoreCase("null")
            || token.length() > 16_384 || token.chars().anyMatch(character -> character < 33 || character > 126))
            throw new IOException("Sign in to your Minecraft account before changing its appearance.");
    }

    public void upload(String accessToken, UUID account, byte[] png, String variant, BooleanSupplier sameAccount) throws IOException {
        validateToken(accessToken);
        if (!"classic".equals(variant) && !"slim".equals(variant)) throw new IOException("Choose Classic or Slim.");
        SkinLibrary.Image image = SkinLibrary.canonicalize(png);
        if (image.height() == 32 && "slim".equals(variant)) throw new IOException("A 64 by 32 skin uses the Classic model.");
        if (!sameAccount.getAsBoolean()) throw new IOException("Your active account changed. Open Skins again.");
        byte[] identity = request(profile, "GET", accessToken, null, null);
        try {
            JsonObject json = new JsonParser().parse(new String(identity, StandardCharsets.UTF_8)).getAsJsonObject();
            String id = json.get("id").getAsString().replace("-", "");
            if (!account.toString().replace("-", "").equalsIgnoreCase(id))
                throw new IOException("Your session belongs to another Minecraft account. Sign in again.");
        } catch (RuntimeException invalid) { throw new IOException("Minecraft returned an invalid account profile. Try again later."); }
        if (!sameAccount.getAsBoolean()) throw new IOException("Your active account changed. Open Skins again.");
        String boundary = "ArcadiumSkin" + UUID.randomUUID().toString().replace("-", "");
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"variant\"\r\n\r\n" + variant +
                "\r\n--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"skin.png\"\r\nContent-Type: image/png\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
        body.write(image.png());
        body.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.US_ASCII));
        request(URI.create(profile.toString() + "/skins"), "POST", accessToken, "multipart/form-data; boundary=" + boundary, body.toByteArray());
    }

    private static byte[] request(URI uri, String method, String token, String contentType, byte[] body) throws IOException {
        return request(uri, method, token, contentType, body, "skin");
    }

    static byte[] request(URI uri, String method, String token, String contentType, byte[] body, String appearance) throws IOException {
        REQUESTS.incrementAndGet();
        HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(10_000);
        connection.setReadTimeout(15_000);
        connection.setRequestMethod(method);
        connection.setRequestProperty("Authorization", "Bearer " + token);
        connection.setRequestProperty("Accept", "application/json");
        try {
            if (body != null) {
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", contentType);
                connection.setFixedLengthStreamingMode(body.length);
                try (var output = connection.getOutputStream()) { output.write(body); }
            }
            int status = connection.getResponseCode();
            if (status == 401) throw new IOException("Your Minecraft session expired. Sign in again.");
            if (status == 403) throw new IOException("This account could not change " + appearance + "s. Check that it owns Minecraft Java Edition.");
            if (status == 429) throw new IOException("Too many " + appearance + " changes. Try again later.");
            if (status < 200 || status >= 300) throw new IOException(status >= 500 ? "Minecraft is unavailable. Try again later."
                : "skin".equals(appearance) ? "Minecraft could not accept the skin. Check the PNG and try again."
                : "Minecraft could not accept the cape change. Refresh your capes and try again.");
            try (InputStream input = connection.getInputStream()) {
                byte[] result = input.readNBytes(65_537);
                if (result.length > 65_536) throw new IOException("Minecraft returned an oversized response. Try again later.");
                return result;
            }
        } finally { connection.disconnect(); }
    }
}
