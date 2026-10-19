package io.github.arcadiumclient.featuretest;

import static io.github.arcadiumclient.featuretest.Visual033Qa.call;
import static io.github.arcadiumclient.featuretest.Visual033Qa.field;
import static io.github.arcadiumclient.featuretest.Visual033Qa.findField;
import static io.github.arcadiumclient.featuretest.Visual033Qa.nodes;
import static io.github.arcadiumclient.featuretest.Visual033Qa.properties;

import com.google.gson.JsonParser;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Writer;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

public final class AccountTypesQa {
    private static final String BRIDGE = "io.github.arcadiumclient.runtime.client.launcher.NativeAccountBridge";
    private static final String USERNAME = "OfflineUiQA";
    private static final String SYNTHETIC_TOKEN = "arcadium-ui-fixture-token";
    private static final List<String> CHOICES = List.of("Microsoft", "Cookie", "Cracked", "Session");
    private static final Map<String, Object> RESULT = new LinkedHashMap<>();
    private static final List<String> CAPTURES = new ArrayList<>();
    private static Object bridge, connection;
    private static GuardWriter guard;
    private static Path auth;
    private static byte[] savedAuth;
    private static Set<?> initialAccounts;
    private static int stage, ticks;
    private static boolean begun, done;

    private AccountTypesQa() { }

    static boolean tick(Minecraft mc, Map<String, Object> checks) throws Exception {
        if (done) return true;
        require("ArcadiumTest".equals(mc.getSession().getUsername()) && "0".equals(mc.getSession().getAccessToken()), "Account type QA requires the isolated offline session");
        require(mc.world == null && mc.screen != null && mc.screen.getClass().getSimpleName().equals("ArcadiumMainMenu"), "Account type QA must stay on the actual main menu");
        if (!begun) {
            begun = true;
            checks.put("accountTypes", RESULT);
            Path game = mc.gameDir.toPath().toRealPath();
            Path storage = Path.of(System.getProperty("arcadium.launcherDataDir")).toAbsolutePath().normalize();
            require(storage.startsWith(game) && !storage.equals(game), "Account type QA storage must be inside the game fixture");
            if (Files.exists(storage)) require(storage.toRealPath().startsWith(game), "Account type QA storage escaped its fixture");
            Path executable = Path.of(System.getProperty("arcadium.launcherExecutable")).toAbsolutePath().normalize();
            require(Files.isRegularFile(executable), "The test native launcher executable is missing");
            auth = storage.resolve("auth.dat");
            savedAuth = Files.isRegularFile(auth) ? Files.readAllBytes(auth) : null;
            bridge = Class.forName(BRIDGE).getField("INSTANCE").get(null);
            initialAccounts = accountIds(call(bridge, "list"));
            connection = field(bridge, "connection");
            require(connection != null && ((Process) field(connection, "process")).isAlive(), "The actual native account helper did not start");
            BufferedWriter original = (BufferedWriter) field(connection, "writer");
            guard = new GuardWriter(original);
            Field writer = findField(connection.getClass(), "writer");
            writer.set(connection, new BufferedWriter(guard));
            RESULT.put("realNativeHelper", true);
            RESULT.put("syntheticInputsOnly", true);
            RESULT.put("screenshots", CAPTURES);
        }
        require(field(bridge, "connection") == connection && ((Process) field(connection, "process")).isAlive(), "The guarded native account helper was replaced or stopped");
        guard.requireReadOnly();
        require(++ticks < 180, "Account type QA timed out at stage " + stage);
        if (ticks < 15) return false;
        switch (stage) {
            case 0 -> click(mc, "ArcadiumTest");
            case 1 -> { require(hasText(mc, "Add account"), "The account dropdown did not open"); click(mc, "Add account"); }
            case 2 -> { verifyChoices(mc); capture(mc, "account-types-choices.png"); click(mc, "Cracked"); }
            case 3 -> {
                require(hasText(mc, "Cracked username") && hasText(mc, "Singleplayer and offline-mode servers."), "The Cracked account form did not appear");
                Object input = input(mc);
                require(!properties(input).containsKey("Password"), "The Cracked username field was incorrectly masked");
                require(editable(input).isEmpty(), "The Cracked input was not initially empty");
                setText(input, USERNAME);
            }
            case 4 -> {
                require(USERNAME.equals(editable(input(mc))), "The Cracked username field rejected its synthetic text");
                require(USERNAME.equals(renderedText(input(mc))), "The Cracked username did not render as plain text");
                capture(mc, "account-types-cracked.png");
                click(mc, "Back");
            }
            case 5 -> { verifyChoices(mc); require(inputs(mc).isEmpty(), "Back left the Cracked input on screen"); click(mc, "Cracked"); }
            case 6 -> { require(editable(input(mc)).isEmpty(), "Back retained the Cracked username"); RESULT.put("crackedInputClearedOnBack", true); click(mc, "Back"); }
            case 7 -> { verifyChoices(mc); click(mc, "Session"); }
            case 8 -> {
                require(hasText(mc, "Minecraft access token") && hasText(mc, "Valid until the session expires."), "The Session account form did not appear");
                Object input = input(mc);
                require(properties(input).containsKey("Password"), "The Session field has no password semantics");
                require(editable(input).isEmpty(), "The Session input was not initially empty");
                setText(input, SYNTHETIC_TOKEN);
            }
            case 9 -> {
                Object input = input(mc);
                String visible = renderedText(input);
                require(!visible.isEmpty() && !visible.contains(SYNTHETIC_TOKEN)
                    && visible.chars().allMatch(value -> value == 0x2022 || value == '*'), "The Session token rendered without masking");
                RESULT.put("sessionTextRenderedMasked", true);
                capture(mc, "account-types-session-masked.png");
                click(mc, "Back");
            }
            case 10 -> { verifyChoices(mc); require(inputs(mc).isEmpty(), "Back left the Session input on screen"); click(mc, "Session"); }
            case 11 -> { require(editable(input(mc)).isEmpty(), "Back retained the Session token"); RESULT.put("sessionInputClearedOnBack", true); click(mc, "Back"); }
            case 12 -> { verifyChoices(mc); click(mc, "Back"); }
            case 13 -> {
                require(hasText(mc, "Add account") && !hasText(mc, "Microsoft") && inputs(mc).isEmpty(), "Back did not return to the account dropdown");
                click(mc, "ArcadiumTest");
            }
            case 14 -> {
                require(!hasText(mc, "Add account"), "The account dropdown did not close");
                require(initialAccounts.equals(accountIds(call(bridge, "list"))), "Browsing account forms changed saved accounts");
                require(savedAuth == null ? !Files.exists(auth) : Files.isRegularFile(auth) && Arrays.equals(savedAuth, Files.readAllBytes(auth)), "Browsing account forms changed protected credentials");
                guard.requireReadOnly();
                RESULT.put("choices", CHOICES);
                RESULT.put("requests", guard.snapshot());
                RESULT.put("providerLoginStarted", false);
                RESULT.put("accountsUnchanged", true);
                RESULT.put("gameSessionUnchanged", true);
                call(bridge, "close");
                done = true;
                return true;
            }
            default -> throw new IllegalStateException("Unknown account type QA stage");
        }
        stage++;
        ticks = 0;
        return false;
    }

    private static Set<?> accountIds(Object store) throws Exception {
        return Set.copyOf(((Map<?, ?>) call(store, "getUsers")).keySet());
    }

    private static List<Object> allNodes(Minecraft mc) throws Exception {
        Object scene = field(mc.screen, "sceneOrNull");
        require(scene != null, "The account menu has no rendered Compose scene");
        List<Object> result = nodes(call(call(field(scene, "mainOwner"), "getSemanticsOwner"), "getRootSemanticsNode"));
        for (Object layer : (Collection<?>) field(scene, "layers")) {
            Object owner = call(layer, "getOwner");
            result.addAll(nodes(call(call(owner, "getSemanticsOwner"), "getRootSemanticsNode")));
        }
        Collections.reverse(result);
        return result;
    }

    private static Object textNode(Minecraft mc, String label, boolean clickable) throws Exception {
        for (Object node : allNodes(mc)) {
            Map<String, Object> props = properties(node);
            if (clickable && !props.containsKey("OnClick")) continue;
            if (props.get("Text") instanceof Collection<?> values)
                for (Object value : values) if (label.equals(call(value, "getText"))) return node;
        }
        throw new IllegalStateException("Account menu control was not found: " + label + " at stage " + stage);
    }

    private static boolean hasText(Minecraft mc, String text) throws Exception {
        try { textNode(mc, text, false); return true; } catch (IllegalStateException absent) { return false; }
    }

    private static void click(Minecraft mc, String label) throws Exception {
        Object action = call(properties(textNode(mc, label, true)).get("OnClick"), "getAction");
        require(Boolean.TRUE.equals(call(action, "invoke")), "Account menu rejected a click: " + label);
    }

    private static void verifyChoices(Minecraft mc) throws Exception {
        for (String choice : CHOICES) textNode(mc, choice, true);
        require(hasText(mc, "Back"), "The provider chooser has no Back action");
    }

    private static List<Object> inputs(Minecraft mc) throws Exception {
        List<Object> result = new ArrayList<>();
        for (Object node : allNodes(mc)) if (properties(node).containsKey("SetText")) result.add(node);
        return result;
    }

    private static Object input(Minecraft mc) throws Exception {
        List<Object> result = inputs(mc);
        require(result.size() == 1, "The account form must have one editable input");
        return result.get(0);
    }

    private static String editable(Object node) throws Exception {
        Object text = properties(node).get("EditableText");
        return text == null ? "" : (String) call(text, "getText");
    }

    private static void setText(Object node, String value) throws Exception {
        Object action = call(properties(node).get("SetText"), "getAction");
        Object text = Class.forName("androidx.compose.ui.text.AnnotatedString").getConstructor(String.class, List.class, List.class).newInstance(value, List.of(), List.of());
        require(Boolean.TRUE.equals(call(action, "invoke", text)), "The account form rejected synthetic input");
    }

    private static String renderedText(Object node) throws Exception {
        Object entry = properties(node).get("GetTextLayoutResult");
        require(entry != null, "The account input exposes no rendered text layout");
        List<Object> layouts = new ArrayList<>();
        require(Boolean.TRUE.equals(call(call(entry, "getAction"), "invoke", layouts)) && !layouts.isEmpty(), "The account input has no text layout result");
        Object text = call(call(layouts.get(0), "getLayoutInput"), "getText");
        return (String) call(text, "getText");
    }

    private static void capture(Minecraft mc, String name) {
        Screenshot.take(mc.gameDir, name, mc.width, mc.height, mc.getRenderTarget());
        CAPTURES.add(name);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private static final class GuardWriter extends Writer {
        private final Writer delegate;
        private final StringBuilder pending = new StringBuilder();
        private final Map<String, Integer> requests = new LinkedHashMap<>();
        private boolean forbidden;

        GuardWriter(Writer delegate) { this.delegate = delegate; }

        @Override public synchronized void write(char[] chars, int offset, int length) {
            pending.append(chars, offset, length);
        }

        @Override public synchronized void flush() throws IOException {
            String[] lines = pending.toString().split("\\R");
            pending.setLength(0);
            for (String line : lines) {
                if (line.isBlank()) continue;
                String operation = JsonParser.parseString(line).getAsJsonObject().get("op").getAsString();
                requests.merge(operation, 1, Integer::sum);
                if (!operation.equals("list") && !operation.equals("cancel")) {
                    forbidden = true;
                    throw new IOException("Account type QA blocked a provider or mutation request");
                }
                delegate.write(line);
                delegate.write("\n");
            }
            delegate.flush();
        }

        synchronized Map<String, Integer> snapshot() { return new LinkedHashMap<>(requests); }
        synchronized void requireReadOnly() { require(!forbidden, "Browsing account forms attempted a provider login or account mutation"); }
        @Override public synchronized void close() throws IOException { delegate.close(); }
    }
}
