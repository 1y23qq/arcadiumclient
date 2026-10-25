// SPDX-License-Identifier: GPL-3.0-only
package org.polyfrost.oneconfig.internal;

//? if >= 1.21.11 && < 26.3 {
/*import net.minecraft.client.Minecraft;
import org.polyfrost.compose.render.RenderContext;
import org.polyfrost.oneconfig.api.notifications.v1.NotificationsManager;
import org.polyfrost.oneconfig.api.notifications.v1.NotificationsRenderer;
import org.polyfrost.oneconfig.api.platform.v1.Platform;
import org.polyfrost.oneconfig.internal.ui.compose.SkiaCtx;
import org.polyfrost.oneconfig.internal.ui.sound.UiSoundEvent;
import org.polyfrost.oneconfig.internal.ui.sound.UiSounds;

public final class ModernNotifications {
    private static long lastSoundAt;
    private ModernNotifications() { }

    public static void install() {
        NotificationsManager.INSTANCE.setToastsEnabled(() -> OneConfigConfig.enableNotifications);
        NotificationsManager.INSTANCE.setOnNotification(notification -> Minecraft.getInstance().execute(() -> {
            long now = System.nanoTime();
            if (OneConfigConfig.enableNotifications && now - lastSoundAt >= 350_000_000L) {
                lastSoundAt = now;
                UiSounds.INSTANCE.play(UiSoundEvent.NOTIFICATION);
            }
        }));
        SkiaCtx.INSTANCE.setNotifRenderer(() -> NotificationsRenderer.render(
            new RenderContext(SkiaCtx.INSTANCE.getCanvas()), Platform.screen().guiWidth(), Platform.screen().guiHeight()));
    }
}
*///?}
