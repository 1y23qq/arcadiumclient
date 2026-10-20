package dev.microcontrollers.betterscreens;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.microcontrollers.betterscreens.config.BetterScreensConfig;
import net.minecraft.client.Minecraft;
import org.polyfrost.oneconfig.api.event.v1.EventManager;
import org.polyfrost.oneconfig.api.event.v1.events.ServerJoinEvent;
import org.polyfrost.oneconfig.api.notifications.v1.Notifications;
import org.polyfrost.oneconfig.utils.v1.JsonUtils;
import org.polyfrost.oneconfig.utils.v1.Multithreading;

import java.util.HashMap;
import java.util.Map;

public final class ServerManager {
    private static long lastCacheAttempt = 0L;
    private static final Map<String, String> serverList = new HashMap<>();

    private ServerManager() {
    }

    public static void initialize() {
        EventManager.INSTANCE.register(ServerJoinEvent.class, event -> saveLastServerIp());

        if (BetterScreensConfig.showServerPreview) {
            Multithreading.submit(ServerManager::cacheServerNames);
        }
    }

    public static String getServerName(String ip) {
        if (serverList.isEmpty() && System.currentTimeMillis() - lastCacheAttempt > 10_000) {
            lastCacheAttempt = System.currentTimeMillis();

            // Block here so a lookup still returns a real name when startup caching failed or was disabled
            cacheServerNames();
        }

        for (Map.Entry<String, String> entry : serverList.entrySet()) {
            if (ip.toLowerCase().endsWith(entry.getKey().toLowerCase())) {
                return entry.getValue();
            }
        }

        return ip;
    }

    private static void saveLastServerIp() {
        Minecraft mc = Minecraft.getInstance();
        if (!mc.isSingleplayer()) {
            BetterScreensConfig.lastServerIP = (mc.getServer() != null ? mc.getServer().getIp() : "");
            BetterScreensConfig.INSTANCE.save();
        }
    }

    private static void cacheServerNames() {
        JsonElement json = JsonUtils.parseFromUrl("https://servermappings.lunarclientcdn.com/servers.json");
        if (json == null) {
            Notifications.info("Better Screens", "Failed to cache server names for direct connect preview: Could not fetch/parse");
            return;
        }

        if (!json.isJsonArray()) {
            Notifications.info("Better Screens", "Failed to cache server names for direct connect preview: Unexpected JSON format");
            return;
        }

        for (JsonElement element : json.getAsJsonArray()) {
            if (!element.isJsonObject()) {
                continue;
            }

            JsonObject serverJson = element.getAsJsonObject();
            JsonElement addressesElement = serverJson.get("addresses");
            if (addressesElement == null || !addressesElement.isJsonArray()) {
                continue;
            }

            for (JsonElement address : addressesElement.getAsJsonArray()) {
                if (!address.isJsonPrimitive()) {
                    continue;
                }

                JsonElement name = serverJson.get("name");
                if (name == null || !name.isJsonPrimitive()) {
                    continue;
                }

                serverList.put(address.getAsString(), name.getAsString());
            }
        }
    }
}