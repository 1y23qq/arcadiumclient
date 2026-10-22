// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.featuretest;

import com.google.gson.GsonBuilder;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screen.ConnectScreen;
import net.minecraft.client.gui.screen.TitleScreen;

public final class ConnectingQa {
    private static int ticks, port;
    private static boolean captured, done;
    private static ServerSocket server;
    private static final LinkedHashMap<String, Object> result = new LinkedHashMap<>();
    public static boolean enabled() { return Boolean.getBoolean("arcadium.connectionQa"); }

    public static void tick() {
        if (done) return;
        Minecraft mc = Minecraft.getInstance();
        try {
            if (++ticks == 60) {
                if (!"ArcadiumTest".equals(mc.getSession().getUsername()) || !"0".equals(mc.getSession().getAccessToken()))
                    throw new IllegalStateException("Connecting QA requires a synthetic session.");
                Visual033Qa.staticField("io.github.arcadiumclient.runtime.client.ClientPreferences", "setupComplete").setBoolean(null, true);
                mc.options.pauseOnUnfocus = false;
                server = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")); port = server.getLocalPort();
                Thread listener = new Thread(() -> {
                    try (var socket = server.accept()) { Thread.sleep(15_000); } catch (Exception ignored) {}
                });
                listener.setDaemon(true); listener.start();
                mc.openScreen(new ConnectScreen(new TitleScreen(), mc, "127.0.0.1", port));
            }
            if (ticks == 80) {
                if (!(mc.screen instanceof ConnectScreen)) throw new IllegalStateException("Connecting screen closed unexpectedly.");
                var caption = java.util.Arrays.stream(mc.screen.getClass().getDeclaredMethods())
                    .filter(method -> method.getName().contains("arcadium$caption")).findFirst().orElseThrow();
                caption.setAccessible(true);
                for (String key : new String[]{"connect.connecting", "connect.authorizing"}) {
                    String text = (String) caption.invoke(mc.screen, key, new Object[0], null);
                    if (!text.contains("127.0.0.1:" + port) || text.toLowerCase().contains("hypixel"))
                        throw new IllegalStateException("The server caption did not match the actual connection: " + text);
                    result.put(key, text);
                }
                result.put("passed", true);
            }
            if (ticks >= 86 && captured) finish(mc);
        } catch (Throwable error) {
            error.printStackTrace(); result.put("passed", false); result.put("error", error.toString()); finish(mc);
        }
    }

    public static void afterFrame() {
        if (done || ticks < 82 || captured) return;
        Minecraft mc = Minecraft.getInstance();
        Screenshot.take(mc.gameDir, "connection-caption.png", mc.width, mc.height, mc.getRenderTarget()); captured = true;
    }

    private static void finish(Minecraft mc) {
        done = true;
        try {
            Files.writeString(mc.gameDir.toPath().resolve("connecting-result.json"), new GsonBuilder().setPrettyPrinting().create().toJson(result));
            if (server != null) server.close();
        } catch (Exception error) { throw new IllegalStateException(error); }
        mc.stop();
    }
}
