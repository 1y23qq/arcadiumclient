// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.forge1710;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.apache.logging.log4j.LogManager;

@Mod(modid = "arcadiumclient", name = "Arcadium Client", acceptedMinecraftVersions = "[1.7.10]", acceptableRemoteVersions = "*")
public final class ArcadiumForgeMod {
    @Mod.EventHandler
    public void initialize(FMLPreInitializationEvent event) {
        io.github.arcadiumclient.forge1710.platform.PortLoader.markForgeReady();
        try (var input = ArcadiumForgeMod.class.getResourceAsStream("/arcadium-forge-components.json")) {
            if (input == null) throw new IllegalStateException("Arcadium component metadata is missing.");
            var components = new JsonParser().parse(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonArray();
            for (String group : List.of("init", "client-init", "main", "client")) for (var item : components) {
                JsonObject component = item.getAsJsonObject();
                JsonObject entries = component.getAsJsonObject("entrypoints");
                if (entries == null || !entries.has(group)) continue;
                    String method = switch (group) {
                        case "main" -> "onInitialize";
                        case "client" -> "onInitializeClient";
                        case "client-init" -> "initClient";
                        default -> "init";
                    };
                    for (var entry : entries.getAsJsonArray(group)) {
                        String value = entry.isJsonPrimitive() ? entry.getAsString() : entry.getAsJsonObject().get("value").getAsString();
                        invoke(value, method);
                    }
            }
            Object start = Class.forName("net.ornithemc.osl.lifecycle.api.client.MinecraftClientEvents").getField("START").get(null);
            @SuppressWarnings("unchecked")
            var notifyStart = (java.util.function.Consumer<Object>) start.getClass().getMethod("invoker").invoke(start);
            Object client = Class.forName("net.minecraft.client.Minecraft").getMethod("func_71410_x").invoke(null);
            var playerData = (java.io.File) client.getClass().getField("field_71412_D").get(client);
            String expectedData = System.getProperty("arcadium.userGameDir");
            if (expectedData != null && !playerData.getCanonicalFile().equals(new java.io.File(expectedData).getCanonicalFile()))
                throw new IllegalStateException("The Minecraft player data directory is not shared correctly.");
            notifyStart.accept(client);
            LogManager.getLogger("Arcadium").info("Minecraft player data directory: {}", playerData.getCanonicalPath());
            LogManager.getLogger("Arcadium").info("Arcadium Forge 1.7.10 initialized with native Forge mod loading.");
        } catch (Exception error) { throw new IllegalStateException("Could not initialize Arcadium for Forge 1.7.10.", error); }
    }

    private static void invoke(String value, String method) throws ReflectiveOperationException {
        String[] reference = value.split("::", 2);
        Class<?> type = Class.forName(reference[0]);
        if (reference.length == 2) {
            type.getDeclaredMethod(reference[1]).invoke(null);
            return;
        }
        Object instance;
        try { instance = type.getField("INSTANCE").get(null); }
        catch (NoSuchFieldException ignored) { instance = type.getDeclaredConstructor().newInstance(); }
        type.getMethod(method).invoke(instance);
    }
}
