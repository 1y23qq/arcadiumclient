// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.client.bedwars;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.polyfrost.oneconfig.api.event.v1.EventManager;
import org.polyfrost.oneconfig.api.event.v1.events.ChatEvent;
import org.polyfrost.oneconfig.api.event.v1.events.TickEvent;

public final class BedwarsOverlay {
    private static final BedwarsOverlay INSTANCE = new BedwarsOverlay();
    private final BedwarsOverlayConfig config = BedwarsOverlayConfig.INSTANCE;
    private final AtomicBoolean initialized = new AtomicBoolean();
    private final AtomicLong generation = new AtomicLong();
    private final OverlayHttp http = HypixelDataService.http();
    private final BedwarsData data = HypixelDataService.data();
    private final DenickEvidence denick = new DenickEvidence();
    private final NickBotController nickBot = new NickBotController(config, this::message);
    private final ArrayBlockingQueue<Runnable> updates = new ArrayBlockingQueue<>(256);
    private final Map<UUID, TabLine> tabLines = new HashMap<>();
    private final Set<String> announced = new HashSet<>();
    private final Map<UUID, Long> seraphAt = new HashMap<>();
    private final Set<UUID> seraphDone = new HashSet<>();
    private final Set<UUID> skinDone = new HashSet<>();
    private final Map<UUID, Integer> skinDelay = new HashMap<>();
    private final Set<UUID> uuidRequested = new HashSet<>();
    private final Map<String, Long> cosmeticAt = new HashMap<>();
    private Object world;
    private String server = "";
    private boolean hypixel;
    private boolean context;
    private boolean match;
    private boolean announceReady;
    private boolean invalidKeyWarned;
    private boolean gameEndHandled;
    private boolean mapDodgeHandled;
    private boolean lastDenick;
    private boolean lastEnabled;
    private BedwarsData.Policy lastPolicy;
    private String lastSeraphKey = "";
    private boolean lastSeraph;
    private HypixelLocraw location;
    private int scanTicks;
    private int announceTicks = -1;
    private int locrawTicks = -1;
    private long locrawSentAt;
    private long ownLocrawUntil;
    private long queuedAt = -1;
    private String queuedCommand;
    private long lastQueueAt;

    private BedwarsOverlay() { }

    public static void initialize() {
        if (!INSTANCE.initialized.compareAndSet(false, true)) return;
        INSTANCE.config.preload();
        Path directory = BedwarsRuntime.directory();
        INSTANCE.http.submit(() -> true, () -> { INSTANCE.denick.load(directory); return true; });
        EventManager.register(TickEvent.End.class, INSTANCE::tick);
        EventManager.register(ChatEvent.Receive.class, INSTANCE::receive);
        EventManager.register(ChatEvent.Send.class, INSTANCE::send);
    }

    public static boolean isInBedwars() { return INSTANCE.hypixel && INSTANCE.context; }
    public static boolean isInMatch() { return INSTANCE.hypixel && INSTANCE.match; }
    public static String mapName() { return INSTANCE.location == null || INSTANCE.location.getMap() == null ? "" : INSTANCE.location.getMap(); }
    public static String gameMode() { return INSTANCE.location == null || INSTANCE.location.getMode() == null ? "" : INSTANCE.location.getMode(); }

    public static String tabLine(UUID id, String name, String vanilla) {
        if (!INSTANCE.hypixel || !INSTANCE.config.enabled || !INSTANCE.context) return vanilla;
        TabLine line = INSTANCE.tabLines.get(id);
        return line != null && line.name.equals(name) ? line.text : vanilla;
    }

    public static void reload() { INSTANCE.updates.offer(INSTANCE::reloadNow); }
    public static void cancelQueue() {
        INSTANCE.updates.offer(() -> { INSTANCE.queuedAt = -1; INSTANCE.queuedCommand = null; INSTANCE.message("Pending queue cancelled."); });
    }
    public static void importNickRules() { INSTANCE.importRules(); }

    private void tick() {
        Object nowWorld = BedwarsRuntime.world();
        String nowServer = BedwarsRuntime.server();
        if (world != nowWorld || !server.equals(nowServer)) {
            if (!server.equals(nowServer) || !onHypixel(nowServer)) data.releaseMatch();
            world = nowWorld;
            server = nowServer;
            hypixel = nowWorld != null && onHypixel(nowServer);
            resetWorld();
        }
        BedwarsData.Policy policy = new BedwarsData.Policy(config.keyless, config.useHypixelApiKey, config.hypixelApiKey);
        if (!policy.same(lastPolicy) || lastSeraph != config.seraphAlerts || !safe(config.seraphApiKey).equals(lastSeraphKey)
                || lastDenick != config.denick || lastEnabled != config.enabled) {
            if (!policy.same(lastPolicy)) data.retryUnavailable();
            invalidate();
            lastPolicy = policy;
            lastSeraph = config.seraphAlerts;
            lastSeraphKey = safe(config.seraphApiKey);
            lastDenick = config.denick;
            lastEnabled = config.enabled;
        }
        for (int i = 0; i < 12; i++) {
            Runnable update = updates.poll();
            if (update == null) break;
            update.run();
        }
        if (!hypixel || world == null) { nickBot.tick(false); return; }
        nickBot.tick(true);
        if (announceTicks > 0 && --announceTicks == 0) { announceReady = true; scanTicks = 19; }
        if (locrawTicks > 0 && --locrawTicks == 0 && config.enabled) requestLocraw();
        if (config.enabled && config.autoQueue) {
            gameEnd(BedwarsRuntime.title());
            if (queuedAt >= 0 && System.currentTimeMillis() >= queuedAt) flushQueue();
        } else { queuedAt = -1; queuedCommand = null; }
        if (++scanTicks < 20) return;
        scanTicks = 0;
        if (BedwarsRuntime.sidebar().toUpperCase(Locale.ROOT).contains("BED WARS")) context = true;
        if (!config.enabled) { tabLines.clear(); return; }
        if (data.invalidKey(policy.hypixelKey) && config.useHypixelApiKey && !config.hideInvalidKeyWarning && !invalidKeyWarned) {
            invalidKeyWarned = true;
            message("§cHypixel rejected the API key. Update it or switch to API Key-Less.");
        }
        List<BedwarsRuntime.Entry> roster = BedwarsRuntime.roster();
        if (context) scanRoster(roster, policy);
        if (config.denick && context) scanDenick(roster);
    }

    private void resetWorld() {
        invalidate();
        context = false; match = false; announceReady = false;
        announceTicks = -1; location = null; gameEndHandled = false; mapDodgeHandled = false;
        queuedAt = -1; queuedCommand = null; ownLocrawUntil = 0;
        locrawTicks = hypixel ? 40 : -1;
        nickBot.worldChanged();
    }

    private void invalidate() {
        generation.incrementAndGet();
        tabLines.clear(); announced.clear(); seraphAt.clear(); seraphDone.clear();
        skinDone.clear(); skinDelay.clear(); uuidRequested.clear(); cosmeticAt.clear();
        denick.clear(); invalidKeyWarned = false;
        scanTicks = 19;
    }

    private void scanRoster(List<BedwarsRuntime.Entry> roster, BedwarsData.Policy policy) {
        if (data.hasMatch()) data.retainForMatch(roster.stream().map(entry -> entry.id).toList());
        tabLines.clear();
        UUID local = BedwarsRuntime.localId();
        for (BedwarsRuntime.Entry entry : roster) {
            String text = entry.vanilla;
            if (config.hasStatsProvider() && (config.tabStats || config.chatStats)) {
                BedwarsStats stats = data.present(entry.id);
                if (stats == null && data.pendingCount() < 24) data.get(entry.id, entry.name, policy);
                if (stats != null && !stats.isUnavailable() && (stats.isNicked() || PlayerNameUtil.sameAccountName(entry.name, stats.getName()))) {
                    if (config.tabStats) text = BedwarsTabLineFormatter.format(stats, entry.name,
                            BedwarsTabLineFormatter.resolveTabNameColor(entry.vanilla, entry.name, entry.teamPrefix));
                    if (announceReady && config.chatStats && !entry.id.equals(local) && announced.add(entry.id + ":" + entry.name)) announce(entry, stats);
                }
            }
            if (config.denick && config.denickTab) {
                DenickEvidence.Candidate candidate = denick.candidate(entry.id);
                if (candidate != null && !candidate.name.equalsIgnoreCase(entry.name)) text += " §7(" + candidate.name + "?)";
            }
            tabLines.put(entry.id, new TabLine(entry.name, text));
            if (config.seraphAlerts && !safe(config.seraphApiKey).isEmpty() && !entry.id.equals(local)) seraph(entry);
        }
    }

    private void announce(BedwarsRuntime.Entry entry, BedwarsStats stats) {
        String color = BedwarsTabLineFormatter.resolveTabNameColor(entry.vanilla, entry.name, entry.teamPrefix);
        String name = (color == null ? "§f" : color) + entry.name;
        if (stats.isNicked()) {
            if (config.includeNickedPlayers) message(name + " §7is §cNicked§7.");
            return;
        }
        boolean stars = config.useStarThreshold && stats.getStar() >= Math.max(0, config.starThreshold);
        boolean fkdr = config.useFkdrThreshold && stats.getFkdr() >= Math.max(0, config.fkdrThreshold);
        if (!config.useStarThreshold && !config.useFkdrThreshold) { stars = true; fkdr = true; }
        if (!stars && !fkdr) return;
        String value = stars ? BedwarsStarFormatter.formatColoredStarValue(stats.getStar()) + " §7Star" : "";
        if (fkdr) value += (stars ? ", " : "") + FkdrColorFormatter.format(stats.getFkdr()) + " §7FKDR";
        message(name + " §7has " + value + "§7.");
    }

    private void receive(ChatEvent.Receive event) {
        if (!hypixel || world == null) return;
        String text = PlayerNameUtil.plain(event.getFullyUnformattedMessage());
        if (text.contains(ArcadiumPrefix.MARKED)) return;
        if (HypixelLocraw.looksLikeLocrawJson(text)) {
            HypixelLocraw parsed = HypixelLocraw.parse(text);
            if (parsed != null) applyLocation(parsed);
            if (System.currentTimeMillis() < ownLocrawUntil) event.cancelled = true;
            return;
        }
        if (!config.enabled) return;
        if (nickBot.chat(text)) event.cancelled = true;
        if (text.contains("Protect your bed and destroy the enemy beds.") && !match) {
            context = true; match = true; announceReady = false; announceTicks = 60;
            scanTicks = 19; locrawTicks = 10;
        }
        if (config.denick && config.denickCosmetic && context) denick.observe(text);
        gameEnd(text);
    }

    private void send(ChatEvent.Send event) {
        String command = event.message.trim();
        if ((command.equalsIgnoreCase("/reload") && hypixel && config.enabled) || command.equalsIgnoreCase("/overlay reload")) {
            event.cancelled = true; reloadNow(); return;
        }
        String lower = command.toLowerCase(Locale.ROOT);
        if (lower.startsWith("/denick ") || lower.startsWith(".denick ")) {
            event.cancelled = true;
            cosmetic(command.substring(8).trim());
        }
    }

    private void reloadNow() {
        String gameServer = location != null && location.isBedwarsGameEligible() ? location.getServer() : null;
        data.clear();
        data.beginMatch(gameServer);
        invalidate();
        announceReady = true;
        if (hypixel && context && config.enabled) {
            scanRoster(BedwarsRuntime.roster(), new BedwarsData.Policy(config.keyless, config.useHypixelApiKey, config.hypixelApiKey));
            message("Reloading Overlay data for the current player list.");
        } else message("Overlay cache cleared. Waiting for a Bedwars player list.");
    }

    private void applyLocation(HypixelLocraw locraw) {
        data.beginMatch(locraw.isBedwarsGameEligible() ? locraw.getServer() : null);
        location = locraw;
        context = "BEDWARS".equalsIgnoreCase(locraw.getGametype()) || match;
        if (!locraw.isBedwarsGameEligible()) return;
        if (!match && config.enabled && config.autoQueue && config.mapDodge && !mapDodgeHandled
                && PlayerNameUtil.mapMatches(config.mapBlacklist, locraw.getMap())) {
            mapDodgeHandled = true;
            schedule(locraw.getPlayCommand(), config.mapDodgeDelay);
            message("Skipping blacklisted map §f" + locraw.getMap() + "§7.");
        }
    }

    private void gameEnd(String text) {
        if (!config.enabled || !config.autoQueue || gameEndHandled || !match || location == null || !location.isBedwarsGameEligible()) return;
        String plain = PlayerNameUtil.plain(text);
        boolean won = plain.equals("VICTORY!");
        boolean lost = plain.equals("GAME OVER!") || plain.equals("GAME OVER");
        if (won && config.queueOnWin || lost && config.queueOnLose) {
            gameEndHandled = true;
            schedule(location.getPlayCommand(), config.queueDelay);
        }
    }

    private void schedule(String command, long delay) {
        if (command == null || !command.matches("/play [A-Za-z0-9_]{1,64}")) return;
        queuedCommand = command;
        queuedAt = Math.max(System.currentTimeMillis() + Math.max(0, Math.min(10000, delay)), lastQueueAt + 4000);
    }

    private void flushQueue() {
        String command = queuedCommand;
        queuedCommand = null; queuedAt = -1;
        if (command == null || !config.enabled || !config.autoQueue || !hypixel || BedwarsRuntime.world() == null) return;
        BedwarsRuntime.command(command);
        lastQueueAt = System.currentTimeMillis();
        locrawTicks = 40;
    }

    private void requestLocraw() {
        long now = System.currentTimeMillis();
        if (now - locrawSentAt < 2500) { locrawTicks = 30; return; }
        ownLocrawUntil = now + 5000;
        locrawSentAt = now;
        BedwarsRuntime.command("/locraw");
    }

    private void seraph(BedwarsRuntime.Entry entry) {
        if (seraphDone.contains(entry.id)) return;
        long now = System.currentTimeMillis();
        if (now - seraphAt.getOrDefault(entry.id, 0L) < 60000) return;
        seraphAt.put(entry.id, now);
        long ticket = generation.get();
        String key = safe(config.seraphApiKey);
        http.submit(() -> generation.get() == ticket, () -> SeraphLookup.fetch(http, entry.id, key, () -> generation.get() == ticket))
                .whenComplete((result, error) -> enqueue(ticket, () -> {
                    if (error != null || result == null || !config.seraphAlerts || !config.enabled) return;
                    seraphDone.add(entry.id);
                    if (PlayerNameUtil.valid(result.name) && !result.name.equalsIgnoreCase(entry.name))
                        message("§f" + entry.name + "§7: Seraph lists §f" + result.name + "§7. Identity not confirmed.");
                    if (!result.tags.isEmpty()) message("§f" + entry.name + "§7: Seraph reports §c" + String.join(", ", result.tags) + "§7.");
                }));
    }

    private void scanDenick(List<BedwarsRuntime.Entry> roster) {
        Map<String, List<BedwarsRuntime.Entry>> groups = new HashMap<>();
        long ticket = generation.get();
        for (BedwarsRuntime.Entry entry : roster) {
            groups.computeIfAbsent(entry.name.toLowerCase(Locale.ROOT), ignored -> new ArrayList<>()).add(entry);
            if (!config.denickSkin || entry.id.version() != 1 || skinDone.contains(entry.id)) continue;
            if (!entry.vanilla.contains("§") && skinDelay.merge(entry.id, 1, Integer::sum) < 10) continue;
            skinDone.add(entry.id);
            http.submit(() -> generation.get() == ticket, () -> denick.skin(entry.name, entry.skin))
                    .whenComplete((candidate, error) -> enqueue(ticket, () -> {
                        if (!config.denick || !config.denickSkin) return;
                        if (candidate != null) remember(entry, candidate, ticket);
                        else if (config.denickShowFailed) message("§f" + entry.name + "§7 is nicked. No identifying skin profile was found.");
                    }));
        }
        if (!config.denickUuid) return;
        for (List<BedwarsRuntime.Entry> group : groups.values()) {
            if (group.size() < 2) continue;
            for (BedwarsRuntime.Entry nick : group) {
                if (nick.id.version() != 1) continue;
                for (BedwarsRuntime.Entry real : group) {
                    if (real.id.version() != 4 || !uuidRequested.add(real.id)) continue;
                    http.submit(() -> generation.get() == ticket, () -> {
                        OverlayHttp.Response response = http.request("https://sessionserver.mojang.com/session/minecraft/profile/" + real.id.toString().replace("-", ""),
                                OverlayHttp.Credential.NONE, "", null, () -> generation.get() == ticket);
                        return response.status == 200 ? BedwarsData.string(BedwarsData.object(response.body), "name") : null;
                    }).whenComplete((name, error) -> enqueue(ticket, () -> {
                        if (!config.denick || !config.denickUuid) return;
                        if (PlayerNameUtil.valid(name) && !name.equalsIgnoreCase(nick.name)) remember(nick, new DenickEvidence.Candidate(name, "duplicate UUID"), ticket);
                        else if (config.denickShowFailed) message("No UUID candidate found for §f" + nick.name + "§7.");
                    }));
                }
            }
        }
    }

    private void remember(BedwarsRuntime.Entry entry, DenickEvidence.Candidate candidate, long ticket) {
        if (!denick.remember(entry.id, candidate)) return;
        message("§f" + entry.name + "§7: " + candidate.method + " candidate §f" + candidate.name + "§7. Identity not confirmed.");
        Path directory = BedwarsRuntime.directory();
        http.submit(() -> generation.get() == ticket, () -> { denick.save(directory, entry.id, entry.name, candidate); return true; });
        scanTicks = 19;
    }

    private void cosmetic(String nick) {
        if (!PlayerNameUtil.valid(nick)) { message("Use /denick <nickname>."); return; }
        if (!config.enabled || !config.denick || !config.denickCosmetic) { message("Enable Denick and Bedwars Cosmetic Lookup first."); return; }
        if (!hypixel || !context) { message("Cosmetic lookup is available in Bedwars."); return; }
        String endpoint = safe(config.cosmeticApiUrl);
        if (endpoint.isEmpty()) { message("Set Cosmetic API URL in Bedwars Overlay > Denick to use this lookup."); return; }
        JsonObject body = denick.cosmeticBody(nick);
        if (body == null) { message("No observed cosmetic data for §f" + nick + "§7 yet."); return; }
        long now = System.currentTimeMillis();
        String lower = nick.toLowerCase(Locale.ROOT);
        if (now - cosmeticAt.getOrDefault(lower, 0L) < 10000) { message("That cosmetic lookup is still on cooldown."); return; }
        cosmeticAt.put(lower, now);
        long ticket = generation.get();
        message("Looking up cosmetic candidates for §f" + nick + "§7.");
        http.submit(() -> generation.get() == ticket, () -> {
            OverlayHttp.Response response = http.request(endpoint, OverlayHttp.Credential.NONE, "", body.toString(), () -> generation.get() == ticket);
            return response.status == 200 ? DenickEvidence.cosmeticNames(response.body) : null;
        }).whenComplete((names, error) -> enqueue(ticket, () -> {
            if (!config.denick || !config.denickCosmetic) return;
            if (error != null || names == null) message("§cCosmetic lookup failed. Check the HTTPS endpoint.");
            else if (names.isEmpty()) message("No matching cosmetic candidates for §f" + nick + "§7.");
            else message("§f" + nick + "§7 cosmetic candidates: §f" + String.join(", ", names.subList(0, Math.min(12, names.size()))) + "§7. These are not confirmed identities.");
        }));
    }

    private void importRules() {
        Path path = BedwarsRuntime.directory().resolve("nickbot.json");
        http.submit(() -> true, () -> {
            try {
                if (!Files.isRegularFile(path) || Files.size(path) > 256000) return null;
                return BedwarsData.object(new String(Files.readAllBytes(path), StandardCharsets.UTF_8));
            } catch (Exception ignored) { return null; }
        }).whenComplete((root, error) -> updates.offer(() -> {
            if (root == null) { message("Could not read config/arcadium/nickbot.json."); return; }
            config.nickExact = imported(root, "names", config.nickExact);
            config.nickStartsWith = imported(root, "startsWith", config.nickStartsWith);
            config.nickEndsWith = imported(root, "endsWith", config.nickEndsWith);
            config.nickContains = imported(root, "contains", config.nickContains);
            config.nickLobbies = imported(root, "lobbies", config.nickLobbies);
            config.save();
            message("Nick Bot rules imported.");
        }));
    }

    private static String imported(JsonObject root, String key, String fallback) {
        if (!root.has(key) || !root.get(key).isJsonArray()) return fallback;
        List<String> values = new ArrayList<>();
        for (JsonElement value : root.getAsJsonArray(key)) if (value.isJsonPrimitive()) {
            String text = value.getAsString().trim();
            if (!text.isEmpty() && text.length() <= 64) values.add(text);
            if (values.size() >= 256) break;
        }
        return String.join("\n", values);
    }

    private void enqueue(long ticket, Runnable action) { updates.offer(() -> { if (generation.get() == ticket) action.run(); }); }
    private void message(String content) {
        long ticket = generation.get();
        enqueue(ticket, () -> { if (world != null) BedwarsRuntime.chat(ArcadiumPrefix.message(content)); });
    }
    private static String safe(String text) { return text == null ? "" : text.trim(); }

    public static boolean onHypixel(String address) {
        if (address == null) return false;
        String host = address.toLowerCase(Locale.ROOT).trim();
        int colon = host.lastIndexOf(':');
        if (colon >= 0) host = host.substring(0, colon);
        return host.equals("hypixel.net") || host.endsWith(".hypixel.net");
    }

    private static final class TabLine {
        final String name;
        final String text;
        TabLine(String name, String text) { this.name = name; this.text = text; }
    }
}
