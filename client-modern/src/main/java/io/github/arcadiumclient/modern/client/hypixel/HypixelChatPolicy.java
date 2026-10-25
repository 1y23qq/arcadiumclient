// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.client.hypixel;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public final class HypixelChatPolicy {
    public record Rule(String id, Pattern pattern) { }
    public record Coordinates(int x, int y, int z) { }
    public record PlayerNotice(String name, int current, int maximum, boolean joined) { }
    public record DangerousTaunt(String animal, int x, int z) { }
    private static final Pattern FORMAT = Pattern.compile("(?i)§[0-9A-FK-ORX]");
    private static final Pattern LABELLED_COORDS = Pattern.compile("(?i)\\bx\\s*[:=]\\s*(-?\\d{1,8})\\s*[,; ]+\\by\\s*[:=]\\s*(-?\\d{1,5})\\s*[,; ]+\\bz\\s*[:=]\\s*(-?\\d{1,8})\\b");
    private static final Pattern TRIPLE_COORDS = Pattern.compile("\\((-?\\d{1,8}),\\s*(-?\\d{1,5}),\\s*(-?\\d{1,8})\\)");
    private static final Pattern JOIN = Pattern.compile("^(.*?) has joined \\((\\d{1,5})/(\\d{1,5})\\)!$");
    private static final Pattern LEAVE = Pattern.compile("^(.*?) has (?:quit|left)!$");
    private static final Pattern PLAYER_CHAT = Pattern.compile("^(?:(?:Party|Guild|Officer|Co-op) > )?(?:\\[[^]\\r\\n]+] )*[A-Za-z0-9_]{1,16}(?: \\[[^]]+])?: .+", Pattern.DOTALL);
    private static final Pattern FRIEND = Pattern.compile("^Friend request from (?:\\[[^]\\r\\n]+] )*([A-Za-z0-9_]{1,16})[!.]?$", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
    private static final Pattern TAUNT = Pattern.compile("^(?:A|An) (Chicken|Cow|Pig|Sheep|Horse|Ocelot|Wolf|Donkey) \\w+ from \\d+ blocks away! They are around: \\((-?\\d{1,8}),\\s*(-?\\d{1,8})\\)$", Pattern.CASE_INSENSITIVE);
    private static final Map<String, String> EMOJI = Map.ofEntries(
        Map.entry("<3", "❤"), Map.entry(":heart:", "❤"), Map.entry(":arrow:", "➜"), Map.entry(":bum:", "♿"),
        Map.entry(":cat:", "= ＾● ⋏ ●＾ ="), Map.entry(":cute:", "(✿◠‿◠)"), Map.entry(":dab:", "<o/"),
        Map.entry(":dj:", "ヽ(⌐■_■)ノ♬"), Map.entry(":dog:", "(ᵔᴥᵔ)"), Map.entry(":gimme:", "༼つ◕_◕༽つ"),
        Map.entry(":java:", "☕"), Map.entry(":maths:", "√(π+x)=L"), Map.entry(":no:", "✖"), Map.entry(":peace:", "✌"),
        Map.entry(":puffer:", "<('O')>"), Map.entry(":pvp:", "⚔"), Map.entry(":shrug:", "¯\\_(ツ)_/¯"), Map.entry(":skull:", "☠"),
        Map.entry(":sloth:", "(・⊝・)"), Map.entry(":snail:", "@'-'"), Map.entry(":snow:", "☃"), Map.entry(":star:", "✮"),
        Map.entry(":tableflip:", "(╯°□°）╯︵ ┻━┻"), Map.entry(":thinking:", "(0.o?)"), Map.entry(":totem:", "☉_☉"),
        Map.entry(":typing:", "✎..."), Map.entry(":wizard:", "('-')⊃━☆ﾟ.*･｡ﾟ"), Map.entry(":yes:", "✔"),
        Map.entry(":yey:", "ヽ (◕◡◕) ﾉ"), Map.entry("h/", "ヽ(^◇^*)/"), Map.entry("o/", "( ﾟ◡ﾟ)/"));
    private static final Pattern EMOJI_PATTERN = Pattern.compile(EMOJI.keySet().stream()
        .sorted(java.util.Comparator.comparingInt(String::length).reversed())
        .map(key -> key.equals("o/") || key.equals("h/") ? "(?<!\\S)" + Pattern.quote(key) + "(?!\\S)" : Pattern.quote(key))
        .collect(java.util.stream.Collectors.joining("|")));
    private static Rule rule(String id, String expression) { return new Rule(id, Pattern.compile(expression, Pattern.CASE_INSENSITIVE)); }
    public static final List<Rule> CLEANUP = List.of(
        rule("removeLobbyJoin", ".*(?:joined|entered) (?:the |a )?lobby[!.]?$"),
        rule("removeConnectionStatus", "^(?:(?:Friend|Guild) > )?.{1,48} (?:joined|left|logged (?:in|out))\\.[ ]*$"),
        rule("removedServerConnectedMsgs", "^(?:Sending you to |You are (?:already )?connected to |Connected to server ).*"),
        rule("removeOnlineStatus", "^Your (?:online )?status (?:is|has been|was) .*"),
        rule("removeAutoQuests", "^(?:You have (?:automatically )?(?:started|completed) (?:the )?(?:quest|challenge)|AUTO.?QUEST|Quest (?:Started|Complete)|CHALLENGE COMPLETED).*"),
        rule("removeEarnedCoinsAndExp", "^(?:\\+[0-9,]+ (?:coins?|experience|xp|Hypixel Experience)|You earned [0-9,]+ (?:coins?|experience)|(?:Coins|Experience) Earned:).*"),
        rule("hideKarmaMessages", "^\\+[0-9,]+ Karma!?$"),
        rule("removeTipMessages", "^(?:You (?:tipped|have already tipped)|You received a tip|Thanks for tipping|[A-Za-z0-9_]+ tipped you).*"),
        rule("removeGameAnnouncements", "^(?:[=-]{8,}|Your next game (?:will start|starts)|You will be sent to a new game|Play again\\?|You can join a new game).*"),
        rule("removeGameTips", "^(?:TIP[!: ]|PRO TIP[!: ]|You can use /(?:help|play|rejoin)|Visit the .* guide).*"),
        rule("removeViewStats", "^(?:Click here to view (?:your |the )?stats|View your stats|Click to view your stats).*"),
        rule("removeReplayMessage", "^(?:You can watch (?:a |the )?replay|Watch the replay|This game has been recorded|Replay recorded).*"),
        rule("removeGiftedRanksAmount", ".* (?:gifted|has gifted) (?:a rank|[0-9,]+ ranks?|[A-Z+]+ rank) .*"),
        rule("removeLobbyFishingMsgs", "^(?:\\[FISHING]|.* (?:caught|fished up) (?:a |an )?(?:LEGENDARY|MYTHIC|EPIC)|You caught a).*"),
        rule("removeSoulWellAnnouncements", "^.*(?:found|obtained|rolled) .*(?:in|from) the Soul Well[!.]?$"),
        rule("removeTicketMachineAnnouncements", "^.*(?:won|found|obtained) .*Ticket Machine.*"),
        rule("removeHypeLimitReminder", "^(?:You have reached (?:the |your )?(?:maximum )?Hype|Your Hype (?:limit|meter)).*"),
        rule("removeBridgeOwnGoalDeathMsg", "^.*(?:scored in their own goal|fell into their own goal|scored an own goal).*"),
        rule("removeDuelsBlockTrailDisabled", "^(?:Your |The )?(?:block trail|cosmetic).*disabled.*"),
        rule("removeDuelsNoStatsChange", "^(?:Your stats (?:were not|have not been|will not be) (?:changed|affected)|This (?:game|duel) did not affect your stats).*"),
        rule("removeSimulatorCollectedMsgs", "^(?:You (?:found|collected) (?:a |an )?(?:gift|egg|present|candy|treasure)|\\+[0-9,]+ (?:presents?|eggs?|candy)).*"),
        rule("removeHotPotato", "^(?:You (?:have|received|gave) the Hot Potato|.* passed (?:the |you the )?Hot Potato|HOT POTATO).*"),
        rule("removeCurseOfSpam", "^(?:\\[CURSE OF SPAM]|You have been (?:given|cursed with) the Curse of Spam).*"),
        rule("removeDiscordSafetyWarning", "^(?:Never share (?:your |a )?(?:password|verification code)|Beware of Discord|Discord safety|We will never ask you to).*"));

    private HypixelChatPolicy() { }
    public static String plain(String text) { return FORMAT.matcher(text == null ? "" : text).replaceAll("").strip(); }
    public static boolean isHypixel(String address) {
        if (address == null) return false;
        String host = address.strip().toLowerCase(Locale.ROOT).replaceFirst(":\\d+$", "");
        return host.equals("hypixel.net") || host.endsWith(".hypixel.net");
    }
    public static boolean isPlayerMessage(String text) { return PLAYER_CHAT.matcher(plain(text)).matches(); }
    public static String friendRequest(String text) {
        if (isPlayerMessage(text)) return null;
        var friend = FRIEND.matcher(plain(text));
        return friend.find() ? friend.group(1) : null;
    }
    public static DangerousTaunt dangerousTaunt(String text) {
        var taunt = TAUNT.matcher(plain(text));
        if (!taunt.matches()) return null;
        int x = Integer.parseInt(taunt.group(2)), z = Integer.parseInt(taunt.group(3));
        return Math.abs((long) x) <= 30_000_000 && Math.abs((long) z) <= 30_000_000 ? new DangerousTaunt(taunt.group(1), x, z) : null;
    }
    public static Set<String> matchingCleanup(String text) {
        String clean = plain(text);
        if (isPlayerMessage(clean)) return Set.of();
        var result = new java.util.HashSet<String>();
        for (Rule rule : CLEANUP) if (rule.pattern().matcher(clean).matches()) result.add(rule.id());
        return Set.copyOf(result);
    }
    public static boolean advertisement(String text, boolean bedwars) {
        String clean = plain(text);
        if (!isPlayerMessage(clean)) return false;
        clean = clean.toLowerCase(Locale.ROOT);
        String body = clean.substring(clean.indexOf(": ") + 2);
        return bedwars ? body.contains("carry") && (body.contains("bedwars") || body.contains("bw ")) || body.contains("/p join") && body.contains("bedwars")
            : body.contains("discord.gg/") || body.contains("/visit ") || body.contains("/housing join ") || body.contains("/guild join ") || body.contains("youtube.com/") || body.contains("twitch.tv/");
    }
    public static Coordinates coordinates(String text) {
        String clean = plain(text);
        var match = LABELLED_COORDS.matcher(clean);
        if (!match.find()) { match = TRIPLE_COORDS.matcher(clean); if (!match.find()) return null; }
        try {
            int x = Integer.parseInt(match.group(1)), y = Integer.parseInt(match.group(2)), z = Integer.parseInt(match.group(3));
            return Math.abs((long) x) <= 30_000_000 && Math.abs((long) z) <= 30_000_000 && y >= -2048 && y <= 2048 ? new Coordinates(x, y, z) : null;
        } catch (NumberFormatException ignored) { return null; }
    }
    public static PlayerNotice playerNotice(String text, int lastCount, int lastMaximum) {
        var joined = JOIN.matcher(plain(text));
        if (joined.matches()) {
            int count = Integer.parseInt(joined.group(2)), maximum = Integer.parseInt(joined.group(3));
            if (count > maximum || maximum < 1 || maximum > 10000) return null;
            return new PlayerNotice(joined.group(1), count, maximum, true);
        }
        var left = LEAVE.matcher(plain(text));
        return left.matches() && lastMaximum > 0 ? new PlayerNotice(left.group(1), Math.max(0, lastCount - 1), lastMaximum, false) : null;
    }
    public static String hypixelEmoji(String text) {
        return EMOJI_PATTERN.matcher(text).replaceAll(match -> java.util.regex.Matcher.quoteReplacement(EMOJI.get(match.group())));
    }
    public static String coordinatesText(int x, int y, int z) { return "x: " + x + ", y: " + y + ", z: " + z; }
}
