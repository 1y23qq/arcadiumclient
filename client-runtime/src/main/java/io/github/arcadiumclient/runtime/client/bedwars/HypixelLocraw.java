// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.client.bedwars;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public final class HypixelLocraw {
    private final String server;
    private final String gametype;
    private final String lobbyname;
    private final String mode;
    private final String map;

    public HypixelLocraw(String server, String gametype, String lobbyname, String mode, String map) {
        this.server = server;
        this.gametype = gametype;
        this.lobbyname = lobbyname;
        this.mode = mode;
        this.map = map;
    }

    public static HypixelLocraw parse(String jsonText) {
        if (jsonText == null) {
            return null;
        }
        String trimmed = jsonText.trim();
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
            return null;
        }
        try {
            JsonObject root = new JsonParser().parse(trimmed).getAsJsonObject();
            if (!root.has("server")) {
                return null;
            }
            return new HypixelLocraw(
                    HypixelLocraw.readString(root, "server"),
                    HypixelLocraw.readString(root, "gametype"),
                    HypixelLocraw.readString(root, "lobbyname"),
                    HypixelLocraw.readString(root, "mode"),
                    HypixelLocraw.readString(root, "map")
            );
        }
        catch (Exception ignored) {
            return null;
        }
    }

    public static boolean looksLikeLocrawJson(String text) {
        if (text == null) {
            return false;
        }
        String stripped = text.replaceAll("§.", "").trim();
        return stripped.startsWith("{") && stripped.contains("\"server\"") && stripped.endsWith("}");
    }

    /**
     * AutoQueue may run only in a real Bedwars game (not lobby): gametype BEDWARS,
     * mode and map present, lobbyname absent.
     */
    public boolean isBedwarsGameEligible() {
        if (this.lobbyname != null && !this.lobbyname.isEmpty()) {
            return false;
        }
        if (this.mode == null || this.mode.isEmpty()) {
            return false;
        }
        if (this.map == null || this.map.isEmpty()) {
            return false;
        }
        return this.gametype != null && "BEDWARS".equalsIgnoreCase(this.gametype);
    }

    public String getServer() {
        return this.server;
    }

    public String getGametype() {
        return this.gametype;
    }

    public String getLobbyname() {
        return this.lobbyname;
    }

    public String getMode() {
        return this.mode;
    }

    public String getMap() {
        return this.map;
    }

    public String getPlayCommand() {
        if (this.mode == null || this.mode.isEmpty()) {
            return null;
        }
        return "/play " + this.mode;
    }

    private static String readString(JsonObject object, String key) {
        if (!object.has(key) || object.get(key).isJsonNull()) {
            return null;
        }
        JsonElement element = object.get(key);
        if (!element.isJsonPrimitive()) {
            return null;
        }
        String value = element.getAsString();
        return value == null || value.isEmpty() ? null : value;
    }

    @Override
    public String toString() {
        return "HypixelLocraw{gametype=" + this.gametype
                + ", mode=" + this.mode
                + ", map=" + this.map
                + ", lobbyname=" + this.lobbyname
                + ", server=" + this.server + "}";
    }
}
