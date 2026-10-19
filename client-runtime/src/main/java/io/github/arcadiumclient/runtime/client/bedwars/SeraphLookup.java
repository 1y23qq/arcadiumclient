// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.client.bedwars;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;

public final class SeraphLookup {
    public static final class Result {
        public final String name;
        public final List<String> tags;
        Result(String name, List<String> tags) { this.name = name; this.tags = tags; }
    }

    private SeraphLookup() { }

    public static Result fetch(OverlayHttp http, UUID id, String key, BooleanSupplier active) {
        for (String path : new String[]{"blacklist/" + id, id + "/blacklist"}) {
            OverlayHttp.Response response = http.request("https://api.seraph.si/" + path, OverlayHttp.Credential.SERAPH, key, null, active);
            if (response.status == 404) continue;
            if (response.status != 200) return null;
            JsonObject root = BedwarsData.object(response.body);
            if (root == null || root.has("success") && !BedwarsData.bool(root, "success")) return null;
            JsonObject data = BedwarsData.child(root, "data");
            return parse(data == null ? root : data);
        }
        return null;
    }

    public static Result parse(JsonObject data) {
        Set<String> tags = new LinkedHashSet<>();
        JsonObject bot = BedwarsData.child(data, "bot");
        if (BedwarsData.bool(bot, "tagged")) tags.add("botting");
        JsonObject blacklist = BedwarsData.child(data, "blacklist");
        if (BedwarsData.bool(blacklist, "tagged")) {
            for (String field : new String[]{"report_type", "reason", "tooltip"}) {
                String tag = tag(BedwarsData.string(blacklist, field));
                if (tag != null) { tags.add(tag); break; }
            }
        }
        String custom = tag(BedwarsData.string(data, "customTag"));
        if (custom != null) tags.add(custom);
        return new Result(BedwarsData.string(data, "username"), new ArrayList<>(tags));
    }

    private static String tag(String raw) {
        if (raw == null) return null;
        String tag = raw.toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        if (tag.contains("cheating_blatant") || tag.contains("blatant_cheater") || tag.equals("blatant")) return "blatant cheating";
        if (tag.contains("cheating_closet") || tag.contains("closet_cheater") || tag.equals("closet")) return "closet cheating";
        if (tag.contains("sniping_potential") || tag.contains("possible_sniper") || tag.contains("potential_sniper")) return "possible sniper";
        if (tag.contains("sniping_legit") || tag.contains("legit_sniper")) return "legit sniper";
        if (tag.equals("sniping") || tag.contains("hacking_sniper")) return "sniping";
        if (tag.equals("alt") || tag.contains("alt_account")) return "alt account";
        if (tag.contains("caution")) return "caution";
        if (tag.equals("bot") || tag.contains("botting")) return "botting";
        return null;
    }
}
