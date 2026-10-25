package org.polyfrost.oneconfig.api.ui.v1.keybind;

import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class KeybindMirrorSyncTest {
    @Test void editorChangeUpdatesMirrorBeforeListenersAndReconcileCannotEraseIt() {
        var bind = new OneConfigKeybind(null, null, (byte) 0, 0, down -> true);
        KeybindManager.register(bind);
        try {
            assertNull(TestMinecraftKeybindBridge.mapping(bind).keys());
            var listenerSawUpdatedMirror = new AtomicBoolean();
            KeybindManager.addMenuEditListener(edited -> {
                if (edited == bind) listenerSawUpdatedMirror.set(TestMinecraftKeybindBridge.mapping(bind).keys()[0] == 86);
                return kotlin.Unit.INSTANCE;
            });
            bind.setKeyCodes(new int[]{86});
            KeybindManager.notifyMenuEdit(bind);
            assertTrue(listenerSawUpdatedMirror.get());
            var mirror = TestMinecraftKeybindBridge.mapping(bind);
            KeybindManager.rebindFromMinecraft(bind, mirror.keys(), mirror.mouse(), mirror.modifiers());
            assertArrayEquals(new int[]{86}, bind.getKeyCodes());
            assertEquals(2, TestMinecraftKeybindBridge.syncs(bind));
        } finally { KeybindManager.unregister(bind); }
    }

    @Test void nativeRebindSynchronizesExactlyOnceAndPreservesMouseModifiers() {
        var bind = new OneConfigKeybind(new int[]{86}, null, (byte) 0, 0, down -> true);
        KeybindManager.register(bind);
        try {
            KeybindManager.rebindFromMinecraft(bind, null, new int[]{4}, KeyModifiers.CTRL);
            assertEquals(1, TestMinecraftKeybindBridge.syncs(bind));
            var mirror = TestMinecraftKeybindBridge.mapping(bind);
            assertNull(mirror.keys());
            assertArrayEquals(new int[]{4}, mirror.mouse());
            assertEquals(KeyModifiers.CTRL, mirror.modifiers());
        } finally { KeybindManager.unregister(bind); }
    }
}
