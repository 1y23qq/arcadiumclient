// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.gui.qa;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;

final class ModernRuntimeQa {
    private ModernRuntimeQa() {}
    static CompletableFuture<Map<String, Object>> run(Minecraft mc, Path output) {
        var previous = mc.gui.screen();
        mc.gui.setScreen(null);
        var report = new LinkedHashMap<String, Object>();
        return ModernGlintQa.run(mc, output).thenComposeAsync(glint -> {
            report.put("glint", glint);
            mc.gui.setScreen(null);
            return ModernIdleQa.run(mc, output);
        }, QaTickScheduler.EXECUTOR).thenComposeAsync(idle -> {
            report.put("idle", idle);
            return NativeRuntimeControlsQa.run(mc, output);
        }, QaTickScheduler.EXECUTOR).thenApplyAsync(controls -> {
            report.put("controls", controls);
            report.put("passed", true);
            mc.gui.setScreen(previous);
            return (Map<String, Object>) report;
        }, QaTickScheduler.EXECUTOR);
    }
}
