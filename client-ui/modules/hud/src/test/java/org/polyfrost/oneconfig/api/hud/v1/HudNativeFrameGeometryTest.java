// SPDX-License-Identifier: GPL-3.0-only
package org.polyfrost.oneconfig.api.hud.v1;

import java.lang.reflect.Proxy;
import org.junit.jupiter.api.Test;
import org.polyfrost.oneconfig.api.platform.v1.CompatibilityPlatform;
import org.polyfrost.oneconfig.api.platform.v1.Options;
import org.polyfrost.oneconfig.api.platform.v1.Platform;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HudNativeFrameGeometryTest {
    private static final class NativeHud extends TextHud {
        NativeHud() { super("native-frame-test", "Native frame test", Hud.Category.getINFO(), "", ""); }
        @Override public String getText() { return "test"; }
    }

    @Test
    void nativeFrameApiUsesGuiScaleAndCurrentGrowthAnchorGeometry() throws Exception {
        var field = Platform.class.getDeclaredField("compatibilityPlatform");
        field.setAccessible(true);
        Object previous = field.get(null);
        float oldWidth = HudManager.guiScreenWidth, oldHeight = HudManager.guiScreenHeight;
        float[] guiScale = {4f};
        Options options = () -> guiScale[0];
        var platform = Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{CompatibilityPlatform.class},
            (proxy, method, arguments) -> method.getName().equals("options") ? options : null);
        field.set(null, platform);
        try {
            HudManager.guiScreenWidth = 960f; HudManager.guiScreenHeight = 540f;
            var hud = new NativeHud();
            hud.setStaticWidth(true); hud.setStaticW(80f); hud.setStaticH(20f);
            hud.setSection(Section.BottomRight); hud.setGrowthAnchor(HudAnchor.BottomRight);
            hud.setRelativeX(20f); hud.setRelativeY(30f); hud.setCustomScale(2f);
            hud.setUseGuiScale(true);
            assertEquals(2f, hud.getFrameScale(), .001f);
            assertEquals(780f, hud.getFrameX(), .001f);
            assertEquals(470f, hud.getFrameY(), .001f);

            hud.setUseGuiScale(false);
            assertEquals(.5f, hud.getFrameScale(), .001f);
            assertEquals(900f, hud.getFrameX(), .001f);
            assertEquals(500f, hud.getFrameY(), .001f);

            guiScale[0] = 2f;
            hud.setRelativeX(40f); hud.setRelativeY(50f);
            assertEquals(1f, hud.getFrameScale(), .001f);
            assertEquals(840f, hud.getFrameX(), .001f);
            assertEquals(470f, hud.getFrameY(), .001f);

            HudManager.guiScreenWidth = 1200f; HudManager.guiScreenHeight = 600f;
            assertEquals(1080f, hud.getFrameX(), .001f);
            assertEquals(530f, hud.getFrameY(), .001f);
        } finally {
            field.set(null, previous);
            HudManager.guiScreenWidth = oldWidth; HudManager.guiScreenHeight = oldHeight;
        }
    }
}
