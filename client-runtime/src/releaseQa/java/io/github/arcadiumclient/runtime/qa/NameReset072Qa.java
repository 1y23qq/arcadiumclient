// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa;

import io.github.arcadiumclient.runtime.client.optimizations.EntityNameFormatting;
import io.github.arcadiumclient.runtime.client.optimizations.RenderHookAllocationControl;
import java.lang.management.ManagementFactory;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.IdentityHashMap;
import net.minecraft.client.Minecraft;

public final class NameReset072Qa {
    private static volatile String sink;
    private static long labelCalls;
    private static final IdentityHashMap<Object, Boolean> labelActors = new IdentityHashMap<>();
    private NameReset072Qa() { }
    public static boolean enabled() { return Boolean.getBoolean("arcadium.nameReset072.proof"); }
    public static void beforeWorld() {
        if (enabled()) EntityNameFormatting.resetNameCacheEnabled = Boolean.parseBoolean(System.getProperty("arcadium.nameReset072.cache", "true"));
    }
    public static void label(Object entity, String name) {
        if (!enabled() || entity == null || !entity.getClass().getName().equals("io.github.arcadiumclient.runtime.qa.PlayerPipeline072Qa$Actor")) return;
        require(Minecraft.getInstance().getSession().getAccessToken().equals("0"), "Use only the owned fake account");
        require(name != null && !name.isEmpty() && labelActors.size() < 65, "Invalid native name label");
        labelCalls++; labelActors.put(entity, Boolean.TRUE);
    }
    public static Map<String, Object> verify() {
        Minecraft mc = Minecraft.getInstance();
        require(PlayerPipeline072Qa.enabled() && System.getProperty("arcadium.playerPipeline072.mode", "visual").equals("visual"), "Use the explicit visual fixture");
        require(mc.getSession().getUsername().equals("ArcadiumTest") && mc.getSession().getAccessToken().equals("0"), "Use only the owned fake account");
        require(RenderHookAllocationControl.nameSites == 1, "The actual Sarcio reset-concat site was not transformed");
        boolean expected = Boolean.parseBoolean(System.getProperty("arcadium.nameReset072.cache", "true"));
        require(EntityNameFormatting.resetCalls > 100 && (!expected || EntityNameFormatting.resetHits > 100), "Actual world rendering did not use the expected reset-name path");
        require(EntityNameFormatting.resetNameCacheEnabled == expected, "The explicit cache control did not apply");
        require(labelCalls > 0 && labelActors.size() == Integer.getInteger("arcadium.playerPipeline072.players", 20), "Actual native actor labels were not rendered");
        Map<String, Object> result = new LinkedHashMap<>(); result.put("actualTransformedSites", RenderHookAllocationControl.nameSites);
        result.put("worldCalls", EntityNameFormatting.resetCalls); result.put("worldHits", EntityNameFormatting.resetHits);
        result.put("worldCacheEnabled", expected); result.put("nativeLabelCalls", labelCalls); result.put("nativeLabelActors", labelActors.size());
        boolean enabled = EntityNameFormatting.resetNameCacheEnabled;
        try {
            var bean = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
            require(bean.isThreadAllocatedMemorySupported(), "Per-thread allocation counter unavailable");
            bean.setThreadAllocatedMemoryEnabled(true); long thread = Thread.currentThread().threadId();
            String[] names = new String[16]; for (int i = 0; i < names.length; i++) names[i] = "§b[MVP+] NativeName" + i;
            EntityNameFormatting.resetNameCacheEnabled = true;
            for (int i = 0; i < 200000; i++) sink = EntityNameFormatting.appendReset(names[i & 15]);
            long before = bean.getThreadAllocatedBytes(thread);
            for (int i = 0; i < 1000000; i++) sink = EntityNameFormatting.appendReset(names[i & 15]);
            long cached = bean.getThreadAllocatedBytes(thread) - before;
            EntityNameFormatting.resetNameCacheEnabled = false;
            for (int i = 0; i < 10000; i++) sink = EntityNameFormatting.appendReset(names[i & 15]);
            before = bean.getThreadAllocatedBytes(thread);
            for (int i = 0; i < 1000000; i++) sink = EntityNameFormatting.appendReset(names[i & 15]);
            long original = bean.getThreadAllocatedBytes(thread) - before;
            require(cached == 0 && original > 1000000, "The loaded native name-cache allocation contract failed");
            result.put("iterations", 1000000); result.put("cachedMethodBytes", cached); result.put("originalMethodBytes", original);
            require(EntityNameFormatting.resetEntries <= 2048 && EntityNameFormatting.resetCharacters <= 512 << 10, "Name cache exceeded its budget");
            result.put("passed", true); result.put("actualLoadedMethodMeasured", true); result.put("notFrameOrFpsMeasurement", true);
        } finally { EntityNameFormatting.resetNameCacheEnabled = enabled; }
        return result;
    }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
