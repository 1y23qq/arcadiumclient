// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.gui.qa;

import io.github.arcadiumclient.modern.gui.qa.mixin.FramerateLimitQaAccess;
import io.github.arcadiumclient.modern.gui.qa.mixin.KeyboardQaAccess;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

final class NativeInputQa {
    private NativeInputQa() {}
    static Map<String, Object> state(Minecraft minecraft) {
        var state = new LinkedHashMap<String, Object>();
        state.put("nativeThrottleReason", minecraft.getFramerateLimitTracker().getThrottleReason().name());
        state.put("nativeInputAgeMs", Math.max(0, Util.getMillis() - ((FramerateLimitQaAccess) minecraft.getFramerateLimitTracker()).arcadium$latestInputTime()));
        state.put("nativeWindowIconified", minecraft.getWindow().isIconified());
        state.put("nativeOverlay", minecraft.getOverlay() == null ? "none" : minecraft.getOverlay().getClass().getName());
        state.put("nativeScreen", minecraft.screen == null ? "none" : minecraft.screen.getClass().getName());
        state.put("nativeInactivityPolicy", minecraft.options.inactivityFpsLimit().get().name());
        return state;
    }
    static Map<String, Object> pulse(Minecraft minecraft) {
        var report = new LinkedHashMap<String, Object>();
        report.put("before", state(minecraft));
        for (int key = GLFW.GLFW_KEY_F13; key <= GLFW.GLFW_KEY_F25; key++) {
            var event = new KeyEvent(key, 0, 0);
            boolean bound = false;
            for (var mapping : minecraft.options.keyMappings) if (mapping.matches(event)) { bound = true; break; }
            if (bound) continue;
            var keyboard = (KeyboardQaAccess) minecraft.keyboardHandler;
            try { keyboard.arcadium$nativeKey(minecraft.getWindow().handle(), GLFW.GLFW_PRESS, event); }
            finally { keyboard.arcadium$nativeKey(minecraft.getWindow().handle(), GLFW.GLFW_RELEASE, event); }
            report.put("nativeUnboundKey", key); report.put("after", state(minecraft));
            report.put("nativeKeyboardCallback", true); report.put("inactivityPolicyChanged", false);
            return report;
        }
        throw new IllegalStateException("No unbound native function key is available for the controlled input pulse");
    }
}
