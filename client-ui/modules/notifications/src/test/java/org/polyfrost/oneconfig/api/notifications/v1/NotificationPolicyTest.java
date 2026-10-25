package org.polyfrost.oneconfig.api.notifications.v1;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NotificationPolicyTest {
    @AfterEach void clear() {
        NotificationsManager.INSTANCE.clearAll();
        NotificationsManager.INSTANCE.clearHistory();
        NotificationsManager.INSTANCE.setToastsEnabled(() -> true);
        NotificationsManager.INSTANCE.setOnNotification(null);
    }

    @Test void disabledToastsKeepHistoryWithoutSound() {
        AtomicInteger sounds = new AtomicInteger();
        NotificationsManager.INSTANCE.setToastsEnabled(() -> false);
        NotificationsManager.INSTANCE.setOnNotification(notification -> sounds.incrementAndGet());
        Notification notification = new Notification("Saved", "Profile saved");
        NotificationsManager.INSTANCE.push(notification);
        assertEquals(0, NotificationsManager.INSTANCE.getCount());
        assertTrue(NotificationsManager.INSTANCE.getHistory().contains(notification));
        assertEquals(0, sounds.get());
    }

    @Test void burstKeepsOnlyFiveToastsAndFiftyHistoryEntries() {
        AtomicInteger sounds = new AtomicInteger();
        NotificationsManager.INSTANCE.setOnNotification(notification -> sounds.incrementAndGet());
        for (int i = 0; i < 70; i++) NotificationsManager.INSTANCE.push(new Notification("Saved " + i, ""));
        assertEquals(5, NotificationsManager.INSTANCE.getCount());
        assertEquals(50, NotificationsManager.INSTANCE.getHistory().size());
        assertEquals("Saved 65", NotificationsManager.INSTANCE.getActive().get(0).getTitle());
        assertEquals(70, sounds.get());
    }

    @Test void timedNotificationsHaveFiniteBounds() {
        assertEquals(4_000f, timed(Float.NaN).getEffectiveDurationMillis());
        assertEquals(60_000f, timed(Float.POSITIVE_INFINITY).getEffectiveDurationMillis());
        assertEquals(250f, timed(1f).getEffectiveDurationMillis());
        assertEquals(8_000f, timed(8_000f).getEffectiveDurationMillis());
        assertFalse(timed(Float.NaN).getPersistent());
        assertTrue(timed(-1f).getPersistent());
    }

    private static Notification timed(float duration) {
        return new Notification("Test", "", NotificationType.INFO, null, duration);
    }
}
