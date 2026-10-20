package org.polyfrost.chatting.compat;

//? if = 1.7.10 {
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.mojang.authlib.properties.Property;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.living.player.ClientPlayerEntity;
import net.minecraft.resources.Identifier;
import org.polyfrost.oneconfig.api.platform.v1.Platform;
import java.io.InputStreamReader;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public final class LegacySkinLookup {
    private static final Identifier STEVE = new Identifier("textures/entity/steve.png");
    private static final Gson JSON = new Gson();
    private static final ThreadPoolExecutor WORKER = new ThreadPoolExecutor(2, 2, 30, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(64), task -> {
                Thread thread = new Thread(task, "Arcadium Chat Skin Lookup");
                thread.setDaemon(true);
                return thread;
            });
    private static final Map<String, State> CACHE = new LinkedHashMap<>(32, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, State> eldest) { return size() > 256; }
    };

    private static final class State {
        final String username;
        volatile Identifier texture;
        volatile UUID uuid;
        long retryAfter;
        long expiresAt;
        boolean pending;
        long probeAfter;
        WeakReference<Object> world = new WeakReference<>(null);
        WeakReference<ClientPlayerEntity> player = new WeakReference<>(null);

        State(String name) {
            username = net.minecraft.ChatFormatting.stripFormatting(name);
        }
    }

    private LegacySkinLookup() {}

    public static UUID cachedUuid(String name) {
        if (name == null) return null;
        synchronized (CACHE) {
            State state = CACHE.get(name);
            return state == null ? null : state.uuid;
        }
    }

    public static void request(String name) {
        texture(name);
    }

    public static Identifier texture(String name) {
        if (name == null || name.isEmpty() || name.length() > 64) return STEVE;
        Minecraft client = Minecraft.getInstance();
        synchronized (CACHE) {
            State state = CACHE.computeIfAbsent(name, State::new);
            long now = System.nanoTime();
            if (state.world.get() != client.world || now >= state.probeAfter) {
                var player = client.world == null ? null : client.world.getPlayer(state.username);
                state.player = new WeakReference<>(player instanceof ClientPlayerEntity loaded ? loaded : null);
                state.world = new WeakReference<>(client.world);
                state.probeAfter = now + TimeUnit.MILLISECONDS.toNanos(500);
            }
            var loaded = state.player.get();
            if (loaded != null) return loaded.getSkinTextureLocation();
            if (validName(state.username) && (state.texture == null || now >= state.expiresAt) && !state.pending && now >= state.retryAfter) {
                state.pending = true;
                try { WORKER.execute(() -> load(state.username, state)); }
                catch (RejectedExecutionException ignored) { failed(state); }
            }
            return state.texture == null ? STEVE : state.texture;
        }
    }

    private static boolean validName(String name) {
        if (name == null || name.isEmpty() || name.length() > 16) return false;
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c != '_' && !(c >= '0' && c <= '9') && !(c >= 'A' && c <= 'Z') && !(c >= 'a' && c <= 'z')) return false;
        }
        return true;
    }

    private static void load(String name, State state) {
        try {
            JsonObject identity = read("https://api.mojang.com/users/profiles/minecraft/" + name);
            String id = identity.get("id").getAsString();
            if (!id.matches("[0-9a-fA-F]{32}")) throw new IllegalArgumentException("Invalid profile UUID");
            String dashed = id.substring(0, 8) + "-" + id.substring(8, 12) + "-" + id.substring(12, 16)
                    + "-" + id.substring(16, 20) + "-" + id.substring(20);
            JsonObject profile = read("https://sessionserver.mojang.com/session/minecraft/profile/" + id + "?unsigned=true");
            GameProfile gameProfile = new GameProfile(UUID.fromString(dashed), identity.get("name").getAsString());
            for (var property : profile.getAsJsonArray("properties")) {
                JsonObject value = property.getAsJsonObject();
                if ("textures".equals(value.get("name").getAsString())) {
                    gameProfile.getProperties().put("textures", new Property("textures", value.get("value").getAsString()));
                }
            }
            state.uuid = gameProfile.getId();
            Platform.screen().runOnUiThread(() -> register(gameProfile, state));
        } catch (Exception ignored) { failed(state); }
    }

    private static void register(GameProfile profile, State state) {
        try {
            var manager = Minecraft.getInstance().getSkinManager();
            var textures = manager.getTextures(profile);
            var skin = (MinecraftProfileTexture) textures.get(MinecraftProfileTexture.Type.SKIN);
            if (skin == null) { failed(state); return; }
            URI uri = URI.create(skin.getUrl());
            if (!"textures.minecraft.net".equalsIgnoreCase(uri.getHost()) || !uri.getPath().startsWith("/texture/")) {
                failed(state);
                return;
            }
            var secured = new MinecraftProfileTexture("https://textures.minecraft.net" + uri.getPath(), Map.of());
            state.texture = manager.register(secured, MinecraftProfileTexture.Type.SKIN);
            synchronized (CACHE) {
                state.pending = false;
                state.expiresAt = System.nanoTime() + TimeUnit.MINUTES.toNanos(10);
            }
        } catch (Exception ignored) { failed(state); }
    }

    private static void failed(State state) {
        synchronized (CACHE) {
            state.pending = false;
            state.retryAfter = System.nanoTime() + TimeUnit.MINUTES.toNanos(1);
        }
    }

    private static JsonObject read(String address) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) URI.create(address).toURL().openConnection();
        connection.setConnectTimeout(4000);
        connection.setReadTimeout(4000);
        connection.setRequestProperty("User-Agent", "Arcadium/1.7.10");
        try (var input = new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8)) {
            return JSON.fromJson(input, JsonObject.class);
        } finally { connection.disconnect(); }
    }
}
//?}
