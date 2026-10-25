// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.client.hypixel;

import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import io.github.arcadiumclient.modern.client.hypixel.mixin.ServerMenuAccess;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.regex.Pattern;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.LevelRenderState;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.scores.DisplaySlot;
import org.polyfrost.oneconfig.api.notifications.v1.Notifications;

public final class ModernHypixel {
    private record Pending(String command, long at, ClientPacketListener connection) { }
    private record Label(int distance, int settings, net.minecraft.util.FormattedCharSequence text) { }
    private static final FontDescription PREFIX_FONT = new FontDescription.Resource(Identifier.fromNamespaceAndPath("arcadium_builtin", "prefix"));
    private static final FontDescription MOD_FONT = new FontDescription.Resource(Identifier.fromNamespaceAndPath("arcadium_builtin", "mod"));
    private static final Pattern GUILD_JOIN = Pattern.compile("^(?:Guild > )?(?:\\[[^]]+] )?([A-Za-z0-9_]{1,16}) joined the guild[!.]$", Pattern.CASE_INSENSITIVE);
    private static final Pattern LEVEL = Pattern.compile("(?i)(?:Hypixel|Network) (?:Level )?(\\d{1,5})");
    private static final Pattern ACHIEVEMENT = Pattern.compile("(?i)^Achievement Unlocked[!: ]+(.+)$");
    private static final Pattern COUNTDOWN = Pattern.compile("(?i)^The game starts in (\\d+) seconds?[!.]?$" );
    private static final Pattern COOLDOWN = Pattern.compile("(?i)(?:wait|in|every) (\\d+(?:\\.\\d+)?) seconds?");
    private static final Pattern TIP_SUMMARY = Pattern.compile("(?i)^You tipped ([0-9][0-9,]{0,8}) players?.*");
    private static final Map<String, BooleanSupplier> CLEANUP = Map.ofEntries(
        Map.entry("removeLobbyJoin", () -> HypixelChatConfig.removeLobbyJoin),
        Map.entry("removeConnectionStatus", () -> HypixelChatConfig.removeConnectionStatus),
        Map.entry("removedServerConnectedMsgs", () -> HypixelChatConfig.removedServerConnectedMsgs),
        Map.entry("removeOnlineStatus", () -> HypixelChatConfig.removeOnlineStatus),
        Map.entry("removeAutoQuests", () -> HypixelChatConfig.removeAutoQuests),
        Map.entry("removeEarnedCoinsAndExp", () -> HypixelChatConfig.removeEarnedCoinsAndExp),
        Map.entry("hideKarmaMessages", () -> HypixelChatConfig.hideKarmaMessages),
        Map.entry("removeTipMessages", () -> HypixelChatConfig.removeTipMessages),
        Map.entry("removeGameAnnouncements", () -> HypixelChatConfig.removeGameAnnouncements),
        Map.entry("removeGameTips", () -> HypixelChatConfig.removeGameTips),
        Map.entry("removeViewStats", () -> HypixelChatConfig.removeViewStats),
        Map.entry("removeReplayMessage", () -> HypixelChatConfig.removeReplayMessage),
        Map.entry("removeGiftedRanksAmount", () -> HypixelChatConfig.removeGiftedRanksAmount),
        Map.entry("removeLobbyFishingMsgs", () -> HypixelChatConfig.removeLobbyFishingMsgs),
        Map.entry("removeSoulWellAnnouncements", () -> HypixelChatConfig.removeSoulWellAnnouncements),
        Map.entry("removeTicketMachineAnnouncements", () -> HypixelChatConfig.removeTicketMachineAnnouncements),
        Map.entry("removeHypeLimitReminder", () -> HypixelChatConfig.removeHypeLimitReminder),
        Map.entry("removeBridgeOwnGoalDeathMsg", () -> HypixelChatConfig.removeBridgeOwnGoalDeathMsg),
        Map.entry("removeDuelsBlockTrailDisabled", () -> HypixelChatConfig.removeDuelsBlockTrailDisabled),
        Map.entry("removeDuelsNoStatsChange", () -> HypixelChatConfig.removeDuelsNoStatsChange),
        Map.entry("removeSimulatorCollectedMsgs", () -> HypixelChatConfig.removeSimulatorCollectedMsgs),
        Map.entry("removeHotPotato", () -> HypixelChatConfig.removeHotPotato),
        Map.entry("removeCurseOfSpam", () -> HypixelChatConfig.removeCurseOfSpam),
        Map.entry("removeDiscordSafetyWarning", () -> HypixelChatConfig.removeDiscordSafetyWarning));
    private static final PriorityQueue<Pending> AUTOMATIC = new PriorityQueue<>(Comparator.comparingLong(Pending::at));
    private static final WaypointStore WAYPOINTS = new WaypointStore();
    private static final Map<WaypointStore.Waypoint, Label> LABELS = new java.util.HashMap<>();
    private static final Map<String, HypixelChatPolicy.PlayerNotice> NOTICES = new LinkedHashMap<>();
    private static volatile List<WaypointStore.Waypoint> points = List.of();
    private static volatile boolean hypixel, lobby;
    private static Object world;
    private static boolean initialized, locationKnown, requestedLocation;
    private static long locateAt, scoreboardAt, nextTipAt, lastCommandAt, lastTipAt, tipSummaryAt, guildMotdUntil;
    private static long shoutUntil, chatUntil, watchdogAt, broadcastAt, friendAt;
    private static int playerCount, maximumPlayers, tipCount;
    private ModernHypixel() { }

    public static void initialize() {
        if (initialized) return;
        initialized = true;
        HypixelChatConfig.INSTANCE.preload();
        SilentLobbyConfig.INSTANCE.preload();
        AutoTipConfig.INSTANCE.preload();
        ClientReceiveMessageEvents.ALLOW_GAME.register(ModernHypixel::allowGame);
        ClientReceiveMessageEvents.ALLOW_CHAT.register((message, signature, sender, parameters, time) -> allowGame(message, false));
        ClientReceiveMessageEvents.MODIFY_GAME.register((message, overlay) -> overlay || !hypixel || !HypixelChatConfig.enabled ? message : format(message));
        ClientSendMessageEvents.MODIFY_CHAT.register(text -> hypixel && HypixelChatConfig.enabled && HypixelChatConfig.mvpEmojisEnabled ? HypixelChatPolicy.hypixelEmoji(text) : text);
        ClientSendMessageEvents.ALLOW_CHAT.register(ModernHypixel::allowSend);
        ClientSendMessageEvents.ALLOW_COMMAND.register(command -> {
            if (!hypixel || !HypixelChatConfig.enabled || !command.startsWith("shout ") || !HypixelChatConfig.preventShoutingOnCooldown) return true;
            return checkCooldown(shoutUntil, "shout");
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> tick());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (!(screen instanceof AbstractContainerScreen<?>)) return;
            ScreenMouseEvents.allowMouseClick(screen).register((menu, button) -> {
                if (!hypixel || !SilentLobbyConfig.middleClickGUIItems || button.button() != 2 || client.player == null || client.gameMode == null) return true;
                AbstractContainerScreen<?> container = (AbstractContainerScreen<?>) menu;
                if (container.getMenu() == client.player.inventoryMenu) return true;
                var slot = ((ServerMenuAccess) container).arcadium$slotAt(button.x(), button.y());
                if (slot == null || !slot.hasItem() || slot.container == client.player.getInventory()) return true;
                client.gameMode.handleInventoryMouseClick(container.getMenu().containerId, slot.index, 0, ClickType.PICKUP, client.player);
                return false;
            });
        });
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registry) -> {
            dispatcher.register(ClientCommandManager.literal("sendcoords").executes(context -> { sendCoordinates(); return 1; }));
            dispatcher.register(ClientCommandManager.literal("arcadium-waypoints").then(ClientCommandManager.literal("clear").executes(context -> { clearWaypoints(); return 1; })));
        });
    }

    private static void reset() {
        hypixel = false; lobby = false; world = null; locationKnown = false; requestedLocation = false;
        AUTOMATIC.clear(); WAYPOINTS.clear(); points = List.of(); LABELS.clear(); NOTICES.clear();
        playerCount = 0; maximumPlayers = 0; tipCount = 0; tipSummaryAt = 0; guildMotdUntil = 0; shoutUntil = 0; chatUntil = 0;
    }

    private static void tick() {
        Minecraft client = Minecraft.getInstance();
        long now = System.nanoTime();
        String host = client.getCurrentServer() == null ? null : client.getCurrentServer().ip;
        hypixel = client.level != null && client.player != null && HypixelChatPolicy.isHypixel(host);
        if (!hypixel) { if (world != null) reset(); return; }
        if (world != client.level) {
            world = client.level; lobby = false; locationKnown = false; requestedLocation = false; locateAt = now + 1_500_000_000L;
            WAYPOINTS.clear(); LABELS.clear(); points = List.of(); NOTICES.clear(); playerCount = 0; maximumPlayers = 0;
            if (nextTipAt == 0) {
                nextTipAt = now + 30_000_000_000L;
                if (AutoTipConfig.enabled && !AutoTipConfig.hideLoginMessage) local("AutoTip is enabled.");
            }
        }
        if (SilentLobbyConfig.silentLobby && !requestedLocation && now >= locateAt) { requestedLocation = true; enqueue("locraw", 0); }
        if (SilentLobbyConfig.silentLobby && !locationKnown && now >= scoreboardAt) {
            scoreboardAt = now + 1_000_000_000L;
            var scoreboard = client.level.getScoreboard();
            var objective = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
            lobby = objective != null && HypixelChatPolicy.plain(objective.getDisplayName().getString()).equalsIgnoreCase("HYPIXEL");
            if (objective != null) for (var entry : scoreboard.listPlayerScores(objective)) {
                var team = scoreboard.getPlayersTeam(entry.owner());
                String line = (team == null ? "" : team.getPlayerPrefix().getString()) + entry.owner() + (team == null ? "" : team.getPlayerSuffix().getString());
                if (HypixelChatPolicy.plain(line).matches("(?i).*\\bLobby:.*")) lobby = true;
            }
        }
        WAYPOINTS.expire(now);
        if (points.size() != WAYPOINTS.size() || !points.equals(WAYPOINTS.snapshot())) {
            points = WAYPOINTS.snapshot(); LABELS.keySet().retainAll(points);
        }
        if (AutoTipConfig.enabled && now >= nextTipAt) tipNow();
        if (tipCount > 0 && now >= tipSummaryAt) {
            if (AutoTipConfig.messages == 1) local("Tipped " + tipCount + (tipCount == 1 ? " player." : " players."));
            tipCount = 0;
        }
        Pending command = AUTOMATIC.peek();
        if (command != null && now >= command.at() && now - lastCommandAt >= 1_000_000_000L) {
            AUTOMATIC.remove();
            if (command.connection() == client.getConnection() && client.getConnection() != null) {
                client.getConnection().sendCommand(command.command()); lastCommandAt = now;
            }
        }
    }

    private static boolean allowGame(Component message, boolean overlay) {
        if (!hypixel || overlay) return true;
        String text = HypixelChatPolicy.plain(message.getString());
        long now = System.nanoTime();
        if (requestedLocation && text.startsWith("{") && text.length() < 2048) {
            try {
                var data = JsonParser.parseString(text).getAsJsonObject();
                if (data.has("server")) {
                    String mode = data.has("mode") ? data.get("mode").getAsString() : "";
                    String server = data.get("server").getAsString();
                    lobby = mode.toLowerCase(Locale.ROOT).contains("lobby") || server.toLowerCase(Locale.ROOT).contains("lobby") || data.has("gametype") && data.get("gametype").getAsString().equalsIgnoreCase("MAIN");
                    locationKnown = true; requestedLocation = false; locateAt = Long.MAX_VALUE;
                    return false;
                }
            } catch (RuntimeException ignored) { }
        }
        if (AutoTipConfig.enabled && text.matches("(?i)^You (?:tipped|received a tip|have tipped).*")) {
            if (text.toLowerCase(Locale.ROOT).startsWith("you tipped")) {
                var summary = TIP_SUMMARY.matcher(text);
                if (summary.matches()) tipCount += Math.min(10000, Integer.parseInt(summary.group(1).replace(",", ""))); else tipCount++;
                tipSummaryAt = now + 1_000_000_000L;
            }
            if (AutoTipConfig.messages < 2) return false;
        }
        if (!HypixelChatConfig.enabled) return true;
        var notice = HypixelChatPolicy.playerNotice(text, playerCount, maximumPlayers);
        if (notice != null) {
            playerCount = notice.current(); maximumPlayers = notice.maximum();
            NOTICES.put(text, notice); if (NOTICES.size() > 32) NOTICES.remove(NOTICES.keySet().iterator().next());
        }
        boolean player = HypixelChatPolicy.isPlayerMessage(text);
        if (!player) social(message, text, now);
        receiveCoordinates(text, now);
        if (HypixelChatConfig.hideGuildMOTDEnabled && text.matches("(?i).*(?:Guild MOTD|Guild Message of the Day).*")) { guildMotdUntil = now + 3_000_000_000L; return false; }
        if (now < guildMotdUntil && !player && (message.getString().startsWith("  ") || text.matches("[=-]{8,}"))) return false;
        if (HypixelChatConfig.removePlayerAds && HypixelChatPolicy.advertisement(text, false) || HypixelChatConfig.removePlayerBedwarsAds && HypixelChatPolicy.advertisement(text, true)) return false;
        for (String id : HypixelChatPolicy.matchingCleanup(text)) {
            BooleanSupplier enabled = CLEANUP.get(id);
            if (enabled != null && enabled.getAsBoolean()) return false;
        }
        return true;
    }

    private static void social(Component message, String text, long now) {
        String lower = text.toLowerCase(Locale.ROOT);
        var cooldown = COOLDOWN.matcher(text);
        if (cooldown.find()) {
            long until = now + (long) (Math.min(300, Double.parseDouble(cooldown.group(1))) * 1_000_000_000L);
            if (lower.contains("shout")) shoutUntil = until;
            else if (lower.contains("chat") || lower.contains("message")) chatUntil = until;
        }
        if (HypixelChatConfig.autoFriend && now >= friendAt) {
            String friend = HypixelChatPolicy.friendRequest(text);
            if (friend != null) { enqueue("friend accept " + friend, 1000); friendAt = now + 5_000_000_000L; }
        }
        if (HypixelChatConfig.autoPartyWarpConfirm && (lower.contains("are you sure") || lower.contains("confirm"))) {
            String clicked = clickedCommand(message, Set.of("/party warp", "/p warp", "/party warp confirm", "/p warp confirm"));
            if (clicked != null) enqueue(clicked.substring(1), 1500);
            else if (lower.contains("/p warp") || lower.contains("/party warp")) enqueue("party warp", 1500);
        }
        if (HypixelChatConfig.notifyWhenKick && (lower.contains("you have been kicked") || lower.contains("you were kicked"))) {
            Notifications.send("Arcadium", HypixelChatConfig.notifyWhenKickInCaps ? text.toUpperCase(Locale.ROOT) : text);
        }
        if (now >= broadcastAt) {
            var level = LEVEL.matcher(text);
            var achievement = ACHIEVEMENT.matcher(text);
            var guild = GUILD_JOIN.matcher(text);
            if (HypixelChatConfig.broadcastLevelUp && lower.contains("now") && level.find()) {
                enqueue("gc I reached Hypixel Level " + level.group(1) + "!", 1000); broadcastAt = now + 10_000_000_000L;
            } else if (HypixelChatConfig.broadcastAchievements && achievement.matches()) {
                enqueue("gc I unlocked " + achievement.group(1).substring(0, Math.min(120, achievement.group(1).length())) + "!", 1000); broadcastAt = now + 10_000_000_000L;
            } else if (HypixelChatConfig.guildWelcomeMessage && guild.matches()) {
                enqueue("gc Welcome " + guild.group(1) + "!", 1000); broadcastAt = now + 10_000_000_000L;
            }
        }
        if (HypixelChatConfig.thankWatchdog && now >= watchdogAt && lower.contains("watchdog") && lower.contains("banned")) {
            enqueue("ac Thank you Watchdog!", 1000); watchdogAt = now + 300_000_000_000L;
        }
    }

    private static String clickedCommand(Component source, Set<String> allowed) {
        return source.visit((style, text) -> style.getClickEvent() instanceof ClickEvent.RunCommand command && allowed.contains(command.command())
            ? Optional.of(command.command()) : Optional.empty(), Style.EMPTY).orElse(null);
    }

    private static Component format(Component source) {
        String text = HypixelChatPolicy.plain(source.getString());
        Component result = source;
        if (HypixelChatConfig.whitePrivateMessagesEnabled && (text.startsWith("From ") || text.startsWith("To ")) ||
                HypixelChatConfig.whiteNoRankMessagesEnabled && text.matches("^[A-Za-z0-9_]{1,16}: .+")) {
            int colon = source.getString().indexOf(": ");
            if (colon >= 0) result = HypixelRichText.restyle(result, colon + 2, source.getString().length(), style -> style.withColor(ChatFormatting.WHITE));
        }
        if (HypixelChatConfig.shortPMChannelNames && (text.startsWith("From ") || text.startsWith("To "))) {
            int end = text.indexOf(' '); result = HypixelRichText.replace(result, 0, end, text.startsWith("From ") ? "F" : "T");
        }
        if (HypixelChatConfig.shortChannelNames) {
            for (var entry : Map.of("Party > ", "P > ", "Guild > ", "G > ", "Officer > ", "O > ", "Co-op > ", "C > ").entrySet()) {
                if (text.startsWith(entry.getKey())) { result = HypixelRichText.replace(result, 0, entry.getKey().length(), entry.getValue()); break; }
            }
        }
        if (HypixelChatConfig.coloredStatuses && (text.startsWith("Friend > ") || text.startsWith("Guild > "))) {
            String current = result.getString();
            int start = current.lastIndexOf(" joined"); boolean online = start >= 0;
            if (!online) start = current.lastIndexOf(" left");
            if (start >= 0) result = HypixelRichText.restyle(result, start, result.getString().length(), style -> style.withColor(online ? ChatFormatting.GREEN : ChatFormatting.RED));
        }
        HypixelChatPolicy.PlayerNotice notice = NOTICES.get(text);
        if (HypixelChatConfig.gameStatusRestyle && notice != null) {
            int nameEnd = text.indexOf(" has ");
            Component name = HypixelRichText.slice(source, 0, nameEnd);
            MutableComponent status = Component.literal(notice.joined() ? "+ " : "- ").withStyle(notice.joined() ? ChatFormatting.GREEN : ChatFormatting.RED).append(name);
            boolean count = notice.joined() || HypixelChatConfig.playerCountOnPlayerLeave;
            if (count) {
                String current = HypixelChatConfig.padPlayerCount ? String.format(Locale.ROOT, "%" + Integer.toString(notice.maximum()).length() + "d", notice.current()) : Integer.toString(notice.current());
                Component amount = Component.literal("[" + current + "/" + notice.maximum() + "]").withStyle(ChatFormatting.YELLOW);
                result = HypixelChatConfig.playerCountBeforePlayerName ? Component.empty().append(amount).append(" ").append(status) : status.append(" ").append(amount);
            } else result = status;
        }
        var countdown = COUNTDOWN.matcher(text);
        if (HypixelChatConfig.compactGameStartAnnouncements && countdown.matches()) result = Component.literal("Starting in " + countdown.group(1) + "s.").withStyle(ChatFormatting.YELLOW);
        return result;
    }

    private static boolean allowSend(String text) {
        return !hypixel || !HypixelChatConfig.enabled || !HypixelChatConfig.preventNonCooldown || checkCooldown(chatUntil, "send another message");
    }
    private static boolean checkCooldown(long until, String action) {
        long remaining = until - System.nanoTime();
        if (remaining <= 0) return true;
        local("Wait " + (remaining / 1_000_000_000L + 1) + "s before you " + action + ".");
        return false;
    }
    private static void enqueue(String command, long delayMillis) {
        Minecraft client = Minecraft.getInstance();
        if (!hypixel || client.getConnection() == null || AUTOMATIC.size() >= 16) return;
        if (AUTOMATIC.stream().anyMatch(pending -> pending.command().equals(command))) return;
        AUTOMATIC.add(new Pending(command, System.nanoTime() + Math.max(0, delayMillis) * 1_000_000L, client.getConnection()));
    }
    public static void tipNow() {
        long now = System.nanoTime();
        if (!hypixel) { local("AutoTip is only available on Hypixel."); return; }
        if (lastTipAt != 0 && now - lastTipAt < 10_000_000_000L) return;
        lastTipAt = now;
        nextTipAt = now + Math.max(5, Math.min(120, AutoTipConfig.intervalMinutes)) * 60_000_000_000L;
        enqueue("tip all", 0);
    }
    public static void sendCoordinates() {
        Minecraft client = Minecraft.getInstance();
        if (!hypixel || client.player == null || client.getConnection() == null) { local("Coordinate sharing is only available on Hypixel."); return; }
        var at = client.player.blockPosition();
        String text = HypixelChatPolicy.coordinatesText(at.getX(), at.getY(), at.getZ());
        switch (HypixelChatConfig.sendcoordsMode) {
            case 1 -> client.getConnection().sendCommand("pc " + text);
            case 2 -> client.getConnection().sendCommand("gc " + text);
            case 3 -> client.getConnection().sendCommand("cc " + text);
            case 4 -> client.getConnection().sendCommand("tc " + text);
            default -> client.getConnection().sendChat(text);
        }
    }
    public static void clearWaypoints() { WAYPOINTS.clear(); points = List.of(); LABELS.clear(); }
    private static void receiveCoordinates(String text, long now) {
        if (HypixelChatConfig.dangerousTauntWaypointEnabled) {
            var taunt = HypixelChatPolicy.dangerousTaunt(text);
            Minecraft client = Minecraft.getInstance();
            if (taunt != null && client.level != null && client.player != null) {
                int y = client.player.blockPosition().getY();
                if (client.level.hasChunk(taunt.x() >> 4, taunt.z() >> 4)) {
                    int target = y;
                    int closest = Integer.MAX_VALUE;
                    for (int at = Math.max(client.level.getMinY(), target - 24); at <= Math.min(client.level.getMaxY(), target + 24); at++) {
                        var block = new net.minecraft.core.BlockPos(taunt.x(), at, taunt.z());
                        if (!client.level.getBlockState(block).getCollisionShape(client.level, block).isEmpty() && client.level.getBlockState(block.above()).isAir() && Math.abs(at + 1 - target) < closest) {
                            closest = Math.abs(at + 1 - target); y = at + 1;
                        }
                    }
                }
                WAYPOINTS.put(taunt.animal(), new HypixelChatPolicy.Coordinates(taunt.x(), y, taunt.z()), true, now, 15);
                points = WAYPOINTS.snapshot();
                return;
            }
        }
        boolean dangerous = HypixelChatConfig.dangerousTauntWaypointEnabled && text.toLowerCase(Locale.ROOT).contains("dangerous");
        if (!HypixelChatConfig.coordsWaypointsEnabled && !dangerous) return;
        if (!dangerous && !HypixelChatPolicy.isPlayerMessage(text) && !text.startsWith("From ")) return;
        var coordinates = HypixelChatPolicy.coordinates(text);
        if (coordinates == null) return;
        String owner = dangerous ? "Danger" : text.substring(0, Math.max(0, text.indexOf(": "))).replaceFirst("^(?:Party|Guild|Officer|Co-op) > ", "").replaceFirst("^From ", "").replaceAll("\\[[^]]+]", "").strip();
        WAYPOINTS.put(owner.isEmpty() ? "Waypoint" : owner, coordinates, dangerous, now, HypixelChatConfig.coordsWaypointsTime);
        points = WAYPOINTS.snapshot();
    }
    public static Component message(String text) {
        return Component.empty().append(Component.literal("Arcadium").setStyle(Style.EMPTY.withFont(PREFIX_FONT)))
            .append(Component.literal(" > ").withStyle(ChatFormatting.GRAY)).append(Component.literal(text).withStyle(ChatFormatting.WHITE));
    }
    public static void local(String text) {
        Minecraft client = Minecraft.getInstance();
        client.execute(() -> client.gui.getChat().addMessage(message(text)));
    }
    public static boolean muteSound(String identifier) {
        if (!hypixel || !lobby || !SilentLobbyConfig.silentLobby) return false;
        for (String category : SilentLobbyPolicy.categories(identifier)) {
            boolean mute = switch (category) {
                case "firework" -> SilentLobbyConfig.lobbyDisableFireworkSounds;
                case "explosion" -> SilentLobbyConfig.lobbyDisableExplosionSounds;
                case "tnt" -> SilentLobbyConfig.lobbyDisablePrimedTntSounds;
                case "delivery" -> SilentLobbyConfig.lobbyDisableDeliveryManSounds;
                case "step" -> SilentLobbyConfig.lobbyDisableSteppingSounds;
                case "portal" -> SilentLobbyConfig.lobbyDisablePortalSounds;
                case "note" -> SilentLobbyConfig.lobbyDisableNoteBlockSounds;
                case "door" -> SilentLobbyConfig.lobbyDisableDoorSounds;
                case "wither" -> SilentLobbyConfig.lobbyDisableWitherSounds;
                case "bat" -> SilentLobbyConfig.lobbyDisableBatSounds;
                case "slime" -> SilentLobbyConfig.lobbyDisableSlimeSounds;
                case "arrow" -> SilentLobbyConfig.lobbyDisableArrowSounds;
                case "dragon" -> SilentLobbyConfig.lobbyDisableDragonSounds;
                case "enderman" -> SilentLobbyConfig.lobbyDisableEndermanSounds;
                case "item" -> SilentLobbyConfig.lobbyDisableItemPickupSounds;
                case "experience" -> SilentLobbyConfig.lobbyDisableExperienceOrbSounds;
                case "fire" -> SilentLobbyConfig.lobbyDisableFireSounds;
                case "level" -> SilentLobbyConfig.lobbyDisableLevelupSounds;
                default -> false;
            };
            if (mute) return true;
        }
        return false;
    }

    public static void submit(PoseStack pose, LevelRenderState state, SubmitNodeCollector collector) {
        Minecraft client = Minecraft.getInstance();
        if (!hypixel || !HypixelChatConfig.enabled || client.player == null || state.cameraRenderState == null || client.options.hideGui) return;
        var camera = state.cameraRenderState.pos;
        int settings = (HypixelChatConfig.coordsWaypointsRenderOwner ? 1 : 0) | (HypixelChatConfig.coordsWaypointsRenderDistance ? 2 : 0);
        settings = 31 * settings + HypixelChatConfig.coordsWaypointsTextColor.getRawArgb();
        settings = 31 * settings + HypixelChatConfig.coordsWaypointsOwnerColor.getRawArgb();
        settings = 31 * settings + HypixelChatConfig.coordsWaypointsDistanceTextColor.getRawArgb();
        double maximum = Math.max(16, Math.min(2048, HypixelChatConfig.waypointRenderDistance));
        for (var point : points) {
            double distance = client.player.distanceToSqr(point.x() + .5, point.y(), point.z() + .5);
            if (distance > maximum * maximum) continue;
            pose.pushPose();
            pose.translate(point.x() - camera.x, point.y() - camera.y, point.z() - camera.z);
            int box = point.dangerous() ? 0xFFFF5555 : HypixelChatConfig.coordsWaypointsBoxColor.getRawArgb();
            int beam = HypixelChatConfig.coordsWaypointsBeamColor.getRawArgb();
            boolean drawBox = HypixelChatConfig.coordsWaypointsRenderBox, drawBeam = HypixelChatConfig.coordsWaypointsRenderBeam;
            float beamHeight = Math.max(16, Math.min(256, client.level == null ? 128 : client.level.getMaxY() - point.y()));
            if (drawBox || drawBeam) collector.submitCustomGeometry(pose, RenderTypes.lines(), (matrix, vertices) -> {
                if (drawBox) for (int axis = 0; axis < 3; axis++) for (int a = 0; a <= 1; a++) for (int b = 0; b <= 1; b++) {
                    float x = axis == 0 ? 0 : a, y = axis == 1 ? 0 : axis == 0 ? a : b, z = axis == 2 ? 0 : b;
                    edge(vertices, matrix, x, y, z, axis == 0 ? 1 : x, axis == 1 ? 1 : y, axis == 2 ? 1 : z, box);
                }
                if (drawBeam) edge(vertices, matrix, .5f, 0, .5f, .5f, beamHeight, .5f, beam);
            });
            if (HypixelChatConfig.coordsWaypointsRenderText) {
                int metres = (int) Math.sqrt(distance);
                Label label = LABELS.get(point);
                if (label == null || label.distance() != metres || label.settings() != settings) {
                    MutableComponent text = Component.empty().setStyle(Style.EMPTY.withFont(MOD_FONT));
                    if (HypixelChatConfig.coordsWaypointsRenderOwner) text.append(Component.literal(point.owner() + " ").withColor(HypixelChatConfig.coordsWaypointsOwnerColor.getRawArgb()));
                    text.append(Component.literal(point.x() + ", " + point.y() + ", " + point.z()).withColor(HypixelChatConfig.coordsWaypointsTextColor.getRawArgb()));
                    if (HypixelChatConfig.coordsWaypointsRenderDistance) text.append(Component.literal(" [" + metres + "m]").withColor(HypixelChatConfig.coordsWaypointsDistanceTextColor.getRawArgb()));
                    label = new Label(metres, settings, text.getVisualOrderText()); LABELS.put(point, label);
                }
                pose.translate(.5, 1.5, .5);
                pose.mulPose(client.gameRenderer.getMainCamera().rotation());
                pose.scale(-.025f, -.025f, .025f);
                collector.submitText(pose, -client.font.width(label.text()) / 2f, 0, label.text(), false, Font.DisplayMode.SEE_THROUGH, 0xF000F0, 0xFFFFFFFF, 0, 0);
            }
            pose.popPose();
        }
    }
    private static void edge(com.mojang.blaze3d.vertex.VertexConsumer vertices, PoseStack.Pose pose, float x1, float y1, float z1, float x2, float y2, float z2, int color) {
        io.github.arcadiumclient.modern.client.features.NativeLines.segment(vertices, pose, x1, y1, z1, x2, y2, z2, color, 2f);
    }
}
