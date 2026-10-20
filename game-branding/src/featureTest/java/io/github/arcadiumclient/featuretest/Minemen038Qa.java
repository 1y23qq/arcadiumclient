// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.featuretest;

import io.github.arcadiumclient.featuretest.mixin.MinemenMinecraftAccess;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.options.ServerListEntry;

public final class Minemen038Qa {
    private static final String SOURCE = "io.github.arcadiumclient.runtime.client.features.HeightLimitSource";

    private Minemen038Qa() { }

    public static Map<String, Object> run(Minecraft mc) throws Exception {
        require("ArcadiumTest".equals(mc.getSession().getUsername()) && "0".equals(mc.getSession().getAccessToken()),
            "Minemen QA requires the isolated offline session");
        require(mc.world != null && mc.player != null, "Minemen QA requires the local fixture world");
        Class<?> configType = Class.forName("tomeko.hybedwars.config.HyBedWarsConfig");
        Object config = configType.getField("INSTANCE").get(null);
        call(config, "preload");
        Object tree = call(config, "getTree");
        require(call(tree, "getProp", "minemenHeightLimit") != null, "The Minemen switch is absent from Height Overlay settings");
        Field minemen = field(configType, "minemenHeightLimit");
        Field overlay = field(configType, "heightOverlay");
        Field debug = field(configType, "debugModeEnabled");
        MinemenMinecraftAccess integrated = (MinemenMinecraftAccess) mc;
        Object settings = minemen.get(config), overlayEnabled = overlay.get(null), debugEnabled = debug.get(null);
        boolean wasIntegrated = mc.isIntegratedServerRunning();
        ServerListEntry server = mc.getCurrentServerEntry();
        Class<?> packets = Class.forName("tomeko.hybedwars.location.HypixelPackets");
        Field inBedwars = field(packets, "inBedwars");
        boolean wasInBedwars = inBedwars.getBoolean(null);
        Object manager = Class.forName("org.polyfrost.oneconfig.api.hud.v1.HudManager").getField("INSTANCE").get(null);
        Object hud = ((Collection<?>) call(manager, "providers")).stream()
            .filter(value -> value.getClass().getName().endsWith(".HeightLimitHud")).findFirst().orElseThrow();
        Object previousStyle = call(hud, "getStyle");
        Map<String, Object> checks = new LinkedHashMap<>();
        try {
            require(Boolean.TRUE.equals(settings), "Minemen support should be enabled in a fresh profile");
            integrated.arcadium$setIntegratedServerRunning(false);
            inBedwars.setBoolean(null, false);
            minemen.set(config, true);
            overlay.setBoolean(null, true);
            debug.setBoolean(null, false);
            call(hud, "setStyle", 0);
            int cases = 0;
            for (String address : List.of("minemen.club", "as.minemen.club", "na.minemen.club", "eu.minemen.club", "AS.MINEMEN.CLUB.:25565")) {
                mc.setCurrentServerEntry(new ServerListEntry("Local Minemen QA", address, false));
                Object snapshot = source("snapshot");
                require(Integer.valueOf(100).equals(call(snapshot, "getMaximum")), "The Minemen limit was not 100: " + address);
                require(Boolean.TRUE.equals(call(snapshot, "getSupported")), "The Minemen HUD still requires Hypixel Bedwars");
                require(Boolean.TRUE.equals(call(hud, "shouldShow")), "The HUD is hidden on Minemen");
                require(((String) call(hud, "getText")).endsWith(" / 100"), "The HUD did not display the server limit");
                require(Boolean.TRUE.equals(source("minemenOverlayActive")), "The block overlay did not share the Minemen override");
                verifyRenderer(config, true);
                cases++;
            }
            minemen.set(config, false);
            require(call(source("snapshot"), "getMaximum") == null && Boolean.FALSE.equals(source("minemenOverlayActive")),
                "Disabling Minemen left its cached limit active");
            verifyRenderer(config, false);
            minemen.set(config, true);
            require(Integer.valueOf(100).equals(call(source("snapshot"), "getMaximum")), "Re-enabling Minemen was delayed by the HUD cache");
            overlay.setBoolean(null, false);
            require(Boolean.FALSE.equals(source("minemenOverlayActive")), "The Minemen override ignored the block overlay switch");
            require(Integer.valueOf(100).equals(call(source("snapshot"), "getMaximum")), "The independent HUD disappeared with the block overlay");
            overlay.setBoolean(null, true);
            for (String address : List.of("minemen.club.evil.test", "play.minemen.club", "mc.hypixel.net", "localhost")) {
                mc.setCurrentServerEntry(new ServerListEntry("Local Other QA", address, false));
                require(call(source("snapshot"), "getMaximum") == null && Boolean.FALSE.equals(source("minemenOverlayActive")),
                    "The previous Minemen limit survived a server change: " + address);
            }
            mc.setCurrentServerEntry(new ServerListEntry("Local Minemen QA", "minemen.club", false));
            integrated.arcadium$setIntegratedServerRunning(true);
            require(call(source("snapshot"), "getMaximum") == null && Boolean.FALSE.equals(source("minemenOverlayActive")),
                "A stale server entry changed a singleplayer world's limit");
            require(!inBedwars.getBoolean(null) && !debug.getBoolean(null), "Minemen changed the global Hypixel/debug state");
            checks.put("minemenHosts", cases);
            checks.put("limit", 100);
            checks.put("overlayBlockY", 99);
            checks.put("immediateToggleAndServerChanges", true);
            checks.put("singleplayerAndLookalikesIgnored", true);
            checks.put("rendererHooksAndHud", true);
        } finally {
            minemen.set(config, settings);
            overlay.set(null, overlayEnabled);
            debug.set(null, debugEnabled);
            inBedwars.setBoolean(null, wasInBedwars);
            integrated.arcadium$setIntegratedServerRunning(wasIntegrated);
            mc.setCurrentServerEntry(server);
            call(hud, "setStyle", previousStyle);
            source("snapshot");
        }
        return checks;
    }

    private static void verifyRenderer(Object config, boolean enabled) throws Exception {
        Class<?> renderer = Class.forName("tomeko.hybedwars.heightlimit.HeightLimitRenderer");
        Object instance = renderer.getField("INSTANCE").get(null);
        for (Map.Entry<String, Object> check : Map.<String, Object>of("arcadium$minemenOverlay", false,
            "arcadium$minemenOverlayHeight", 17, "arcadium$minemenMap", "Original map").entrySet()) {
            Method handler = java.util.Arrays.stream(renderer.getDeclaredMethods())
                .filter(method -> method.getName().contains(check.getKey()) && method.getParameterCount() == 2)
                .filter(method -> check.getKey().equals("arcadium$minemenOverlay") ? method.getReturnType() == boolean.class : true)
                .findFirst().orElseThrow(() -> new AssertionError("Missing applied Minemen renderer hook: " + check.getKey()));
            handler.setAccessible(true);
            Class<?> operation = handler.getParameterTypes()[1];
            Object original = Proxy.newProxyInstance(operation.getClassLoader(), new Class<?>[] {operation}, (proxy, method, arguments) -> check.getValue());
            Object result = handler.invoke(instance, config, original);
            Object expected = enabled ? switch (check.getKey()) {
                case "arcadium$minemenOverlay" -> true;
                case "arcadium$minemenOverlayHeight" -> 99;
                default -> "arcadium:minemen";
            } : check.getValue();
            require(expected.equals(result), "Minemen renderer hook produced the wrong result: " + check.getKey());
        }
    }

    private static Object source(String method) throws Exception {
        return Class.forName(SOURCE).getMethod(method).invoke(null);
    }

    private static Object call(Object target, String method, Object... arguments) throws Exception {
        Method selected = java.util.stream.Stream.concat(java.util.Arrays.stream(target.getClass().getMethods()),
                java.util.Arrays.stream(target.getClass().getDeclaredMethods()))
            .filter(value -> value.getName().equals(method) && value.getParameterCount() == arguments.length)
            .filter(value -> {
                for (int i = 0; i < arguments.length; i++) {
                    Class<?> type = value.getParameterTypes()[i];
                    if (type == int.class) type = Integer.class;
                    if (arguments[i] != null && !type.isInstance(arguments[i])) return false;
                }
                return true;
            }).findFirst().orElseThrow();
        selected.setAccessible(true);
        return selected.invoke(target, arguments);
    }

    private static Field field(Class<?> type, String name) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
