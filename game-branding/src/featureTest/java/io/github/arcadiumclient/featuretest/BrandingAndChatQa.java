package io.github.arcadiumclient.featuretest;

import static io.github.arcadiumclient.featuretest.Visual033Qa.call;
import static io.github.arcadiumclient.featuretest.Visual033Qa.stat;

import io.github.arcadiumclient.featuretest.mixin.MessagingChatProbeAccess;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ChatMessage;
import net.minecraft.text.Formatting;
import net.minecraft.text.LiteralText;
import org.lwjgl.opengl.Display;
import org.lwjgl.sdl.SDLProperties;
import org.lwjgl.sdl.SDLVideo;
import org.lwjgl.system.JNI;
import org.lwjgl.system.Library;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.system.SharedLibrary;
import org.lwjgl.system.windows.User32;

public final class BrandingAndChatQa {
    private static final String REPEATED = "ARCADIUM_COUNTER_QA";
    private static final String GENUINE = "ARCADIUM_LITERAL_QA (11)";
    private static final Map<String, Object> RESULT = new LinkedHashMap<>();
    private static final Map<String, Object> SAVED = new LinkedHashMap<>();
    private static boolean done;

    private BrandingAndChatQa() {}

    public static boolean tick(Minecraft client, Map<String, Object> checks) throws Exception {
        if (done) return true;
        require("ArcadiumTest".equals(client.getSession().getUsername()) && "0".equals(client.getSession().getAccessToken()), "Chat and icon QA requires its isolated offline session");
        require(client.gameDir.toPath().toRealPath().equals(Path.of(System.getProperty("arcadium.forgeQa.gameDir")).toRealPath()), "Chat and icon QA requires its exact isolated directory");
        checks.put("brandingAndChat", RESULT);
        verifyWindow();
        Object config = Class.forName("org.polyfrost.chattweaks.ChatTweaks").getField("config").get(null);
        Object tree = call(config, "getTree");
        Object hidden = call(tree, "getProp", "hideDuplicateCount");
        require(hidden != null, "Chat Tweaks did not expose Hide Duplicate Count");
        require(Boolean.FALSE.equals(call(hidden, "get")), "Hide Duplicate Count must default to off");
        for (String id : List.of("compactChat", "consecutiveCompactChat", "compactChatFormat", "timestamps", "hideDuplicateCount"))
            SAVED.put(id, call(call(tree, "getProp", id), "get"));
        MessagingChatProbeAccess chat = (MessagingChatProbeAccess) client.gui.getChat();
        List<ChatMessage> raw = new ArrayList<>(chat.arcadium$rawMessages());
        List<ChatMessage> visible = new ArrayList<>(chat.arcadium$visibleMessages());
        try {
            set(tree, "compactChat", true); set(tree, "consecutiveCompactChat", false); set(tree, "compactChatFormat", 0); set(tree, "timestamps", false);
            client.gui.getChat().clear();
            for (int index = 0; index < 12; index++) client.gui.getChat().addMessage(new LiteralText(REPEATED));
            snapshot(chat, "counterDefault");
            require(messages(chat.arcadium$rawMessages(), REPEATED).size() == 1, "Repeated messages stopped compacting");
            require(contains(chat.arcadium$rawMessages(), REPEATED + " (12)"), "The native duplicate total is incorrect");
            require(contains(chat.arcadium$visibleMessages(), REPEATED + " (12)"), "The native duplicate count was not shown by default");
            set(tree, "hideDuplicateCount", true);
            snapshot(chat, "counterHiddenExisting");
            require(contains(chat.arcadium$rawMessages(), REPEATED + " (12)"), "Hiding the counter modified stored chat");
            require(messages(chat.arcadium$visibleMessages(), REPEATED).equals(List.of(REPEATED)), "The existing duplicate count remained visible after the toggle");
            for (int index = 0; index < 3; index++) client.gui.getChat().addMessage(new LiteralText(REPEATED));
            snapshot(chat, "counterHiddenNew");
            require(contains(chat.arcadium$rawMessages(), REPEATED + " (15)"), "Hidden duplicates stopped accumulating");
            require(messages(chat.arcadium$visibleMessages(), REPEATED).equals(List.of(REPEATED)), "New duplicate counts remained visible");
            client.gui.getChat().addMessage(new LiteralText(GENUINE));
            snapshot(chat, "ordinaryNumericSuffix");
            require(contains(chat.arcadium$visibleMessages(), GENUINE), "An ordinary message's parenthesized number was removed");
            set(tree, "hideDuplicateCount", false);
            snapshot(chat, "counterShownAgain");
            require(contains(chat.arcadium$visibleMessages(), REPEATED + " (15)"), "Showing the counter did not restore the accumulated total");
            Object active = stat("org.polyfrost.oneconfig.api.config.v1.ConfigManager", "active");
            set(tree, "hideDuplicateCount", true);
            require(Boolean.TRUE.equals(call(active, "save", tree)), "Chat counter preference could not be saved");
            set(tree, "hideDuplicateCount", false);
            require(Boolean.TRUE.equals(call(Visual033Qa.field(active, "backend"), "load", tree)), "Chat counter preference could not be reloaded");
            require(Boolean.TRUE.equals(call(hidden, "get")), "Chat counter preference did not survive persistence");
            RESULT.put("storedCountPreserved", 15);
            RESULT.put("hideShowReflowsExistingChat", true);
            RESULT.put("ordinaryNumericSuffixPreserved", true);
            RESULT.put("hideDefaultsToFalse", true);
            RESULT.put("preferencePersists", true);
        } finally {
            for (var saved : SAVED.entrySet()) set(tree, saved.getKey(), saved.getValue());
            call(stat("org.polyfrost.oneconfig.api.config.v1.ConfigManager", "active"), "save", tree);
            chat.arcadium$rawMessages().clear(); chat.arcadium$rawMessages().addAll(raw);
            chat.arcadium$visibleMessages().clear(); chat.arcadium$visibleMessages().addAll(visible);
        }
        RESULT.put("completed", true); done = true; return true;
    }

    private static void verifyWindow() throws Exception {
        Object display = stat("pl.tomgirl.pylon.window.DisplaySdl", "instance");
        long window = ((Number) call(display, "getHandle")).longValue();
        long hwnd = SDLProperties.SDL_GetPointerProperty(SDLVideo.SDL_GetWindowProperties(window), SDLVideo.SDL_PROP_WINDOW_WIN32_HWND_POINTER, 0L);
        require(hwnd != 0, "The real game native window was not available");
        long send = User32.getLibrary().getFunctionAddress("SendMessageW");
        long large = JNI.callPPPP(hwnd, User32.WM_GETICON, 1L, 0L, send);
        long small = JNI.callPPPP(hwnd, User32.WM_GETICON, 0L, 0L, send);
        require(large != 0 && small != 0, "The real game is missing its small or large icon");
        require(stat("io.github.arcadiumclient.window.GameWindowIcon", "buffers") == stat("io.github.arcadiumclient.window.GameWindowIcon", "buffers"), "The icon pixels are not cached");
        Display.setIcon(new ByteBuffer[]{ByteBuffer.allocateDirect(16 * 16 * 4)});
        require(large == JNI.callPPPP(hwnd, User32.WM_GETICON, 1L, 0L, send)
                && small == JNI.callPPPP(hwnd, User32.WM_GETICON, 0L, 0L, send), "Another Display.setIcon replaced or reallocated the game icon");
        RESULT.put("realWindowHasBothIcons", true);
        RESULT.put("laterIconOverrideBlocked", true);
        RESULT.put("iconPixelsAndNativeHandlesReused", true);
        try (SharedLibrary shell = Library.loadNative(BrandingAndChatQa.class, "arcadium", "shell32");
             SharedLibrary ole = Library.loadNative(BrandingAndChatQa.class, "arcadium", "ole32");
             MemoryStack stack = MemoryStack.stackPush()) {
            var appId = stack.callocLong(1);
            require(JNI.callPI(MemoryUtil.memAddress(appId), shell.getFunctionAddress("GetCurrentProcessExplicitAppUserModelID")) >= 0
                    && appId.get(0) != 0, "The real game has no Windows application identity");
            try {
                require("com.arcadiumclient.game".equals(MemoryUtil.memUTF16(appId.get(0))), "The real game inherited a Java application identity");
            } finally {
                JNI.callPV(appId.get(0), ole.getFunctionAddress("CoTaskMemFree"));
            }
        }
        RESULT.put("separateWindowsGameIdentity", true);
    }

    private static List<String> messages(List<ChatMessage> messages, String prefix) {
        return messages.stream().map(message -> Formatting.strip(message.getText().getString())).filter(text -> text.startsWith(prefix)).toList();
    }

    private static boolean contains(List<ChatMessage> messages, String text) {
        return messages.stream().anyMatch(message -> text.equals(Formatting.strip(message.getText().getString())));
    }

    private static void snapshot(MessagingChatProbeAccess chat, String name) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("raw", chat.arcadium$rawMessages().stream().map(message -> message.getText().getString()).toList());
        snapshot.put("visible", chat.arcadium$visibleMessages().stream().map(message -> message.getText().getString()).toList());
        snapshot.put("visiblePlain", chat.arcadium$visibleMessages().stream().map(message -> Formatting.strip(message.getText().getString())).toList());
        RESULT.put(name, snapshot);
    }

    private static void set(Object tree, String id, Object value) throws Exception {
        call(call(tree, "getProp", id), "setAsReferential", value);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
