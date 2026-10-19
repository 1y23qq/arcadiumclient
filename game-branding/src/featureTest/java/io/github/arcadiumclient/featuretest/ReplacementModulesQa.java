// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.featuretest;

import io.github.arcadiumclient.featuretest.mixin.MessagingChatProbeAccess;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ChatMessage;
import net.minecraft.client.gui.chat.ChatGui;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.Formatting;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;

public final class ReplacementModulesQa {
    private static final String BASE = "io.github.arcadiumclient.runtime.client.";
    private static final String DUPLICATE = "ARCADIUM_REPLACEMENT_DUP";
    private static final String[] CHAT_FIELDS = {"removeBlankMessages", "increaseChatHistoryLimit", "dontClearChatHistory",
            "saveChatHistory", "saveChatHistoryInterval", "sessionMarkers", "compactChat", "consecutiveCompactChat",
            "dontCompactScreenshots", "compactChatTime", "compactChatFormat", "compactChatColor", "timestamps",
            "timestampsFormat", "timestampsStyle", "timestampsBrackets", "timestampsLeftBracket", "timestampsRightBracket",
            "timestampsColor", "onlyNewTimestamps", "secondsOnTimestamps", "shiftChat", "bypassCommandLimit",
            "safeChatClicks", "safeChatClicksHistory", "imagePreview", "imagePreviewWidth", "hideDuplicateCount"};
    private static final String[] SPRINT_FIELDS = {"isEnabled", "toggleSprint", "toggleSneak", "disableWTapSprint",
            "toggleSprintState", "toggleSneakState", "keybindToggleSprint", "keybindToggleSprintKey",
            "keybindToggleSneak", "keybindToggleSneakKey", "showSprintTextWhileFlying", "unsneakOnFlightStart",
            "toggleFlyBoost", "flyBoostAmount"};
    private static boolean done;

    private ReplacementModulesQa() { }

    public static boolean verify(Minecraft client, Map<String, Object> checks) throws Exception {
        if (done) return true;
        require(client.world != null && client.player != null, "Replacement module QA requires its fixture world");
        require("ArcadiumTest".equals(client.getSession().getUsername()) && "0".equals(client.getSession().getAccessToken()),
                "Replacement module QA requires the isolated offline session");
        String directory = System.getProperty("arcadium.forgeQa.gameDir");
        require(directory != null && client.gameDir.toPath().toRealPath().equals(Path.of(directory).toRealPath()),
                "Replacement module QA requires its exact isolated directory");
        Field loading = Class.forName(BASE + "chat.ChatHistory").getDeclaredField("loading");
        loading.setAccessible(true);
        if (loading.getBoolean(null)) return false;

        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        checks.put("independentReplacementModules", result);
        Object manager = invoke(Class.forName("org.polyfrost.oneconfig.api.config.v1.ConfigManager"), "active");
        Object chatConfig = requiredTree(manager, "chattweaks.json");
        Object sprintConfig = requiredTree(manager, "polysprint.json");
        for (String id : List.of("arcadium-client.json", "arcadium-cute-visuals.json", "arcadium-players.json")) requiredTree(manager, id);
        for (String field : CHAT_FIELDS) require(property(chatConfig, field) != null, "Missing Chat Tweaks option: " + field);
        for (String field : SPRINT_FIELDS) require(property(sprintConfig, field) != null, "Missing Toggle Sprint option: " + field);
        require(Boolean.FALSE.equals(value(chatConfig, "hideDuplicateCount")), "Hide Duplicate Count must default to false");
        require(Boolean.FALSE.equals(value(requiredTree(manager, "arcadium-cute-visuals.json"), "enabled")), "Cute Visuals must default to false");
        require(Boolean.TRUE.equals(value(sprintConfig, "toggleSprint")), "Toggle Sprint must default to true");
        require(Boolean.FALSE.equals(value(sprintConfig, "toggleSneak")), "Toggle Sneak must default to false");
        require(Boolean.FALSE.equals(value(sprintConfig, "toggleFlyBoost")), "Singleplayer Fly Boost must default to false");
        Object hudManager = Class.forName("org.polyfrost.oneconfig.api.hud.v1.HudManager").getField("INSTANCE").get(null);
        Object sprintHud = invoke(hudManager, "getProvider", Class.forName(BASE + "features.ToggleSprintHud"));
        require(sprintHud != null, "The real Toggle Sprint HUD provider was not registered");
        result.put("chatOptionCount", CHAT_FIELDS.length);
        result.put("sprintConfigurationFieldCount", SPRINT_FIELDS.length);
        result.put("requiredDefaultsAndRegistrations", true);

        LinkedHashMap<String, Object> savedChat = save(chatConfig, CHAT_FIELDS);
        LinkedHashMap<String, Object> savedSprint = save(sprintConfig, SPRINT_FIELDS);
        Object filterConfig = requiredTree(manager, "arcadium-chat-filters.json");
        LinkedHashMap<String, Object> savedFilters = save(filterConfig, new String[]{"enabled", "words", "caseSensitive", "wholeWords"});
        ChatGui chat = client.gui.getChat();
        MessagingChatProbeAccess access = (MessagingChatProbeAccess) chat;
        List<ChatMessage> raw = new ArrayList<>(access.arcadium$rawMessages());
        try {
            set(chatConfig, "saveChatHistory", false);
            set(chatConfig, "compactChat", true); set(chatConfig, "consecutiveCompactChat", false);
            set(chatConfig, "timestamps", false); set(chatConfig, "compactChatFormat", 0);
            set(chatConfig, "hideDuplicateCount", false); set(chatConfig, "compactChatTime", 60);
            set(chatConfig, "removeBlankMessages", false); set(filterConfig, "enabled", false);
            chat.clear();

            Text original = new LiteralText(DUPLICATE).setStyle(new Style().setColor(Formatting.AQUA)
                    .setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/help"))
                    .setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new LiteralText("Original hover"))));
            Text repeated = original.copy();
            String serialized = Text.Serializer.toJson(original);
            received(original, 1000L); received(repeated, 2000L);
            require(((Number) invoke(Class.forName(BASE + "chat.ChatTweaks"), "received", original)).longValue() == 1000,
                    "Equal components reused another arrival's receipt time");
            chat.addMessage(original); chat.addMessage(repeated);
            require(access.arcadium$rawMessages().size() == 2, "Compaction altered raw incoming history");
            require(visible(access).equals(List.of(DUPLICATE + " (2)")), "Native visible lines did not merge their duplicate count");
            require(Text.Serializer.toJson(original).equals(serialized) && Text.Serializer.toJson(repeated).equals(serialized),
                    "Chat decoration mutated an original component/style");
            require(hasClick(access.arcadium$visibleMessages(), "/help") && hasHover(access.arcadium$visibleMessages(), "Original hover"),
                    "Compaction lost original click or hover metadata");
            set(chatConfig, "hideDuplicateCount", true); chat.reset();
            require(visible(access).equals(List.of(DUPLICATE)), "Hiding the counter did not reflow existing native chat");
            chat.addMessage(new LiteralText("ARCADIUM_LITERAL (11)"));
            require(visible(access).contains("ARCADIUM_LITERAL (11)"), "Counter hiding altered an ordinary numeric suffix");
            set(chatConfig, "hideDuplicateCount", false); chat.reset();
            require(visible(access).contains(DUPLICATE + " (2)"), "Re-enabling counters lost the duplicate total");
            result.put("nativeDuplicateCountAndHideShow", true);
            result.put("rawHistoryAndStylesUnmodified", true);

            chat.clear();
            Text earlier = new LiteralText("ARCADIUM_EXPIRY"), later = earlier.copy();
            received(earlier, 1000L); received(later, 62001L);
            chat.addMessage(earlier); chat.addMessage(later);
            require(visible(access).size() == 2, "A duplicate outside the configured merge window was collapsed");
            chat.reset();
            require(visible(access).size() == 2, "Refreshing history discarded independent receipt times");
            result.put("mergeWindowSurvivesRefresh", true);

            chat.clear(); set(chatConfig, "consecutiveCompactChat", true);
            chat.addMessage(new LiteralText("ARCADIUM_A")); chat.addMessage(new LiteralText("ARCADIUM_B")); chat.addMessage(new LiteralText("ARCADIUM_A"));
            require(visible(access).size() == 3, "Consecutive-only mode merged across another message");
            chat.clear(); set(chatConfig, "consecutiveCompactChat", false); set(chatConfig, "dontCompactScreenshots", true);
            Text screenshot = new LiteralText("ARCADIUM_SCREENSHOT").setStyle(new Style()
                    .setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_FILE, client.gameDir.toPath().resolve("fixture.png").toString())));
            chat.addMessage(screenshot); chat.addMessage(screenshot.copy());
            require(visible(access).size() == 2, "Screenshot file links were compacted together");
            result.put("consecutiveAndScreenshotExemption", true);

            chat.clear(); set(chatConfig, "removeBlankMessages", true);
            chat.addMessage(new LiteralText("   "));
            require(access.arcadium$rawMessages().size() == 1 && visible(access).isEmpty(), "Blank cleanup did not preserve hidden raw history");
            chat.clear(); set(chatConfig, "removeBlankMessages", false);
            set(filterConfig, "words", List.of("ARCADIUM_FILTER_QA")); set(filterConfig, "caseSensitive", false);
            set(filterConfig, "wholeWords", false); set(filterConfig, "enabled", true);
            chat.addMessage(new LiteralText("ARCADIUM_FILTER_QA"));
            require(access.arcadium$rawMessages().size() == 1 && visible(access).isEmpty(), "Incoming Chat Filters integration failed");
            set(filterConfig, "enabled", false); chat.reset();
            require(visible(access).equals(List.of("ARCADIUM_FILTER_QA")), "Disabling Chat Filters did not restore historical text");
            result.put("filterAndBlankHistoryRestore", true);

            chat.clear(); set(chatConfig, "timestamps", true); set(chatConfig, "timestampsStyle", 0);
            set(chatConfig, "timestampsFormat", 1); set(chatConfig, "timestampsBrackets", 0);
            set(chatConfig, "secondsOnTimestamps", false); set(chatConfig, "onlyNewTimestamps", false);
            Text timed = new LiteralText("ARCADIUM_TIME");
            chat.addMessage(timed);
            List<String> timestampLines = visible(access);
            Object decorator = Class.forName(BASE + "chat.ChatTweaks").getConstructor().newInstance();
            Object prepared = invoke(decorator, "prepare", timed, 0, 0, new ArrayList<ChatMessage>());
            result.put("timestampDiagnostics", Map.of("visible", timestampLines,
                    "nativeComponents", access.arcadium$visibleMessages().stream().map(line -> Text.Serializer.toJson(line.getText())).toList(),
                    "prepared", Text.Serializer.toJson((Text) invoke(prepared, "text")),
                    "enabled", value(chatConfig, "timestamps"), "style", value(chatConfig, "timestampsStyle"),
                    "clock", value(chatConfig, "timestampsFormat"), "brackets", value(chatConfig, "timestampsBrackets")));
            require(timestampLines.size() == 1 && timestampLines.get(0).matches("\\[\\d{2}:\\d{2}\\] ARCADIUM_TIME"),
                    "Timestamp text was not decorated into native chat: " + timestampLines);
            require(access.arcadium$rawMessages().get(0).getText().getString().equals("ARCADIUM_TIME"), "Timestamp changed raw history");
            String[] leftBrackets = {"[", "(", "{", "<", "", "<<"};
            String[] rightBrackets = {"]", ")", "}", ">", "", ">>"};
            ArrayList<String> bracketLines = new ArrayList<>();
            set(chatConfig, "timestampsLeftBracket", "<<"); set(chatConfig, "timestampsRightBracket", ">>");
            for (int choice = 0; choice < leftBrackets.length; choice++) {
                chat.clear(); set(chatConfig, "timestampsBrackets", choice);
                chat.addMessage(new LiteralText("ARCADIUM_TIME_BRACKETS"));
                List<String> lines = visible(access);
                String expected = java.util.regex.Pattern.quote(leftBrackets[choice]) + "\\d{2}:\\d{2}"
                        + java.util.regex.Pattern.quote(rightBrackets[choice]) + " ARCADIUM_TIME_BRACKETS";
                require(lines.size() == 1 && lines.get(0).matches(expected),
                        "Timestamp bracket option " + choice + " does not match its label: " + lines);
                bracketLines.add(lines.get(0));
            }
            result.put("timestampBracketChoices", bracketLines);
            set(chatConfig, "timestampsBrackets", 0);
            chat.clear(); set(chatConfig, "timestampsStyle", 1);
            chat.addMessage(new LiteralText("ARCADIUM_HOVER_TIME"));
            require(visible(access).equals(List.of("ARCADIUM_HOVER_TIME")), "Hover timestamp leaked into visible text");
            require(hasAnyHover(access.arcadium$visibleMessages()), "Hover timestamp metadata was not present on a native display component");
            chat.clear(); set(chatConfig, "timestamps", false);
            chat.addMessage(new LiteralText("\u2063Arcadium\u2064 > ARCADIUM_PREFIX_QA"));
            String marked = access.arcadium$visibleMessages().get(0).getText().getString();
            require(marked.indexOf('\u2063') >= 0 && marked.indexOf('\u2064') >= 0, "Animated Arcadium prefix markers were removed by chat wrapping");
            result.put("timestampAndAnimatedPrefixMetadata", true);

            set(sprintConfig, "toggleSprintState", true); set(sprintConfig, "toggleSneakState", false);
            require(String.valueOf(invoke(sprintHud, "getText")).contains("Toggled"), "Sprint HUD lost its toggled state label");
            set(sprintConfig, "toggleSneak", true); set(sprintConfig, "toggleSneakState", true);
            require(String.valueOf(invoke(sprintHud, "getText")).startsWith("Sneaking"), "Sprint HUD did not prioritize the sneak state");
            result.put("sprintAndSneakHudLabels", true);
        } finally {
            restore(chatConfig, savedChat); restore(sprintConfig, savedSprint); restore(filterConfig, savedFilters);
            access.arcadium$rawMessages().clear(); access.arcadium$rawMessages().addAll(raw);
            access.arcadium$visibleMessages().clear(); chat.reset();
        }
        result.put("noServerChatOrAccountWrites", true);
        result.put("complete", true); done = true; return true;
    }

    private static List<String> visible(MessagingChatProbeAccess chat) throws Exception {
        ArrayList<String> result = new ArrayList<>();
        for (ChatMessage message : chat.arcadium$visibleMessages()) {
            String text = (String) invoke(Class.forName(BASE + "chat.ChatTextMarks"), "strip", message.getText().getString());
            result.add(Formatting.strip(text));
        }
        return result;
    }

    private static boolean hasClick(List<ChatMessage> lines, String value) {
        for (ChatMessage line : lines) for (Text piece : line.getText()) {
            ClickEvent click = piece.getStyle().getClickEvent();
            if (click != null && value.equals(click.getValue())) return true;
        }
        return false;
    }

    private static boolean hasHover(List<ChatMessage> lines, String value) {
        for (ChatMessage line : lines) for (Text piece : line.getText()) {
            HoverEvent hover = piece.getStyle().getHoverEvent();
            if (hover != null && value.equals(hover.getValue().getString())) return true;
        }
        return false;
    }

    private static boolean hasAnyHover(List<ChatMessage> lines) {
        for (ChatMessage line : lines) for (Text piece : line.getText()) if (piece.getStyle().getHoverEvent() != null) return true;
        return false;
    }

    private static void received(Text text, long millis) throws Exception { invoke(Class.forName(BASE + "chat.ChatTweaks"), "received", text, millis); }
    private static Object requiredTree(Object manager, String id) throws Exception {
        Object tree = invoke(manager, "get", id); require(tree != null, "Missing registered config: " + id); return tree;
    }
    private static Object property(Object tree, String id) throws Exception { return invoke(tree, "getProp", id); }
    private static Object value(Object tree, String id) throws Exception { return invoke(property(tree, id), "get"); }
    private static void set(Object tree, String id, Object value) throws Exception { invoke(property(tree, id), "setAsReferential", value); }
    private static LinkedHashMap<String, Object> save(Object tree, String[] fields) throws Exception {
        LinkedHashMap<String, Object> saved = new LinkedHashMap<>();
        for (String field : fields) saved.put(field, value(tree, field));
        return saved;
    }
    private static void restore(Object tree, Map<String, Object> values) throws Exception {
        for (var entry : values.entrySet()) set(tree, entry.getKey(), entry.getValue());
    }
    private static Object invoke(Object target, String name, Object... arguments) throws Exception {
        Class<?> type = target instanceof Class<?> own ? own : target.getClass();
        for (Class<?> owner = type; owner != null; owner = owner.getSuperclass()) for (Method method : owner.getDeclaredMethods()) {
            if (!method.getName().equals(name) || method.getParameterCount() != arguments.length) continue;
            Class<?>[] parameters = method.getParameterTypes();
            boolean accepts = true;
            for (int i = 0; i < parameters.length; i++) {
                Class<?> expected = parameters[i];
                if (expected == boolean.class) expected = Boolean.class;
                else if (expected == int.class) expected = Integer.class;
                else if (expected == long.class) expected = Long.class;
                else if (expected == float.class) expected = Float.class;
                if (arguments[i] != null && !expected.isInstance(arguments[i])) accepts = false;
            }
            if (!accepts) continue;
            method.setAccessible(true);
            try { return method.invoke(Modifier.isStatic(method.getModifiers()) ? null : target, arguments); }
            catch (InvocationTargetException failure) { throw new IllegalStateException("Replacement QA call failed: " + name, failure.getCause()); }
        }
        throw new NoSuchMethodException(type.getName() + "." + name);
    }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
