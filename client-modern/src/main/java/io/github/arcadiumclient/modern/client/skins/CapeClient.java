// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.modern.client.skins;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import javax.imageio.ImageIO;

public final class CapeClient {
    public record Cape(String id, String name, URI texture, boolean active) { }
    public record Profile(List<Cape> capes) {
        public String activeId() { return capes.stream().filter(Cape::active).map(Cape::id).findFirst().orElse(null); }
    }

    private final URI profile;

    public CapeClient() { this(SkinUploadClient.OFFICIAL); }
    CapeClient(URI profile) { this.profile = SkinUploadClient.validateEndpoint(profile); }

    public Profile load(String token, UUID account, BooleanSupplier sameAccount) throws IOException {
        SkinUploadClient.validateToken(token);
        if (account == null || !sameAccount.getAsBoolean()) throw changedAccount();
        byte[] response = SkinUploadClient.request(profile, "GET", token, null, null, "cape");
        Profile result = parse(response, account);
        if (!sameAccount.getAsBoolean()) throw changedAccount();
        return result;
    }

    public Profile select(String token, UUID account, String capeId, BooleanSupplier sameAccount) throws IOException {
        Profile owned = load(token, account, sameAccount);
        if (capeId != null && owned.capes().stream().noneMatch(cape -> cape.id().equals(capeId)))
            throw new IOException("Choose a cape owned by this Minecraft account.");
        if (Objects.equals(capeId, owned.activeId())) return owned;
        if (!sameAccount.getAsBoolean()) throw changedAccount();
        JsonObject body = new JsonObject();
        if (capeId != null) body.addProperty("capeId", capeId);
        byte[] response = SkinUploadClient.request(URI.create(profile + "/capes/active"), capeId == null ? "DELETE" : "PUT", token,
            capeId == null ? null : "application/json", capeId == null ? null : body.toString().getBytes(StandardCharsets.UTF_8), "cape");
        if (response.length > 0) {
            Profile confirmed = parse(response, account);
            if (!Objects.equals(confirmed.activeId(), capeId))
                throw new IOException("Minecraft did not confirm the cape change. Refresh your capes and try again.");
            return confirmed;
        }
        return new Profile(owned.capes().stream().map(cape -> new Cape(cape.id(), cape.name(), cape.texture(), cape.id().equals(capeId))).toList());
    }

    private static Profile parse(byte[] bytes, UUID account) throws IOException {
        try {
            JsonObject value = new JsonParser().parse(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
            if (!account.toString().replace("-", "").equalsIgnoreCase(value.get("id").getAsString().replace("-", "")))
                throw new IOException("Your session belongs to another Minecraft account. Sign in again.");
            List<Cape> capes = new ArrayList<>();
            if (value.has("capes")) {
                var entries = value.getAsJsonArray("capes");
                if (entries.size() > 256) throw new IOException("Minecraft returned too many capes. Try again later.");
                for (var entry : entries) {
                    JsonObject cape = entry.getAsJsonObject();
                    String id = cape.get("id").getAsString();
                    if (!id.matches("[a-zA-Z0-9_-]{1,128}") || capes.stream().anyMatch(existing -> existing.id().equals(id)))
                        throw new IOException("Minecraft returned an invalid cape. Try again later.");
                    String name = cape.has("alias") && !cape.get("alias").isJsonNull() ? cape.get("alias").getAsString() : "Cape " + id;
                    capes.add(new Cape(id, name, textureUri(cape.get("url").getAsString()), "ACTIVE".equals(cape.get("state").getAsString())));
                }
            }
            return new Profile(List.copyOf(capes));
        } catch (RuntimeException invalid) {
            throw new IOException("Minecraft returned an invalid account profile. Try again later.");
        }
    }

    private static URI textureUri(String value) throws IOException {
        try {
            URI uri = URI.create(value);
            if (!("https".equals(uri.getScheme()) || "http".equals(uri.getScheme())) || !"textures.minecraft.net".equals(uri.getHost())
                || uri.getUserInfo() != null || uri.getPort() != -1 || uri.getQuery() != null || uri.getFragment() != null
                || !uri.getPath().matches("/texture/[a-fA-F0-9]{40,64}")) throw new IllegalArgumentException();
            return URI.create("https://textures.minecraft.net" + uri.getPath());
        } catch (RuntimeException invalid) { throw new IOException("Minecraft returned an invalid cape texture."); }
    }

    public byte[] texture(Cape cape) throws IOException {
        URI uri = textureUri(cape.texture().toString());
        HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(5_000);
        connection.setReadTimeout(7_000);
        try {
            if (connection.getResponseCode() != 200) throw new IOException("Could not load this cape's preview.");
            try (var input = connection.getInputStream()) {
                byte[] png = input.readNBytes(1_048_577);
                decodeTexture(png);
                return png;
            }
        } finally { connection.disconnect(); }
    }

    public static BufferedImage decodeTexture(byte[] png) throws IOException {
        if (png == null || png.length > 1_048_576) throw new IOException("The cape preview is too large.");
        try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(png))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IOException("The cape preview is not a PNG.");
            var reader = readers.next();
            try {
                reader.setInput(input);
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (!"png".equalsIgnoreCase(reader.getFormatName()) || width < 64 || width > 1024 || width % 64 != 0
                    || height < width / 2 || height > 1024) throw new IOException("The cape preview has unsupported dimensions.");
                return reader.read(0);
            } finally { reader.dispose(); }
        }
    }

    private static IOException changedAccount() { return new IOException("Your active account changed. Open Skins again."); }
}
