package org.polyfrost.oneconfig.api.ui.v1.keybind;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.polyfrost.oneconfig.api.event.v1.EventManager;
import org.polyfrost.oneconfig.api.event.v1.events.KeyInputEvent;
import org.polyfrost.oneconfig.api.event.v1.events.MouseInputEvent;
import org.polyfrost.oneconfig.api.event.v1.events.ScreenOpenEvent;
import org.polyfrost.oneconfig.api.event.v1.events.TickEvent;
import org.polyfrost.oneconfig.api.event.v1.events.WindowFocusEvent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KeybindManagerTest {
    private final List<OneConfigKeybind> binds = new ArrayList<>();
    private final List<Boolean> calls = new ArrayList<>();
    private final Object focus = new Object();

    @BeforeEach
    void prepare() {
        KeybindManager.builder();
        screen(null);
    }

    @AfterEach
    void reset() {
        post(WindowFocusEvent.Lost.INSTANCE);
        binds.forEach(KeybindManager::unregister);
        TextInputFocus.clear();
        TestScreenPlatform.setCurrent(null);
    }

    @Test
    void dispatchesACompletedTapAtTickEnd() {
        bind(4);
        key(4, KeyInputEvent.PRESSED);
        key(4, KeyInputEvent.RELEASED);
        assertTrue(calls.isEmpty());
        tick();
        assertEquals(List.of(true, false), calls);
        tick();
        assertEquals(List.of(true, false), calls);
    }

    @Test
    void preservesTwoTapsInTheSameTickInOrder() {
        bind(4);
        for (int i = 0; i < 2; i++) {
            key(4, KeyInputEvent.PRESSED);
            key(4, KeyInputEvent.RELEASED);
        }
        tick();
        assertEquals(List.of(true, false, true, false), calls);
    }

    @Test
    void holdingAndNativeRepeatsDoNotSendAgain() {
        bind(4);
        key(4, KeyInputEvent.PRESSED);
        tick();
        key(4, KeyInputEvent.REPEAT);
        key(4, KeyInputEvent.PRESSED);
        post(new KeyInputEvent(0, 'a', KeyInputEvent.PRESSED));
        tick();
        tick();
        assertEquals(List.of(true), calls);
        key(4, KeyInputEvent.RELEASED);
        tick();
        assertEquals(List.of(true, false), calls);
    }

    @Test
    void preservesMouseAndModifierComboTaps() {
        OneConfigKeybind mouse = new OneConfigKeybind(null, new int[]{1}, (byte) 0, 0, down -> { calls.add(down); return true; });
        register(mouse);
        post(new MouseInputEvent(1, 1));
        post(new MouseInputEvent(1, 0));
        tick();
        assertEquals(List.of(true, false), calls);
        calls.clear();
        OneConfigKeybind combo = bind(4);
        combo.setMods(KeyModifiers.CTRL);
        key(224, KeyInputEvent.PRESSED);
        key(4, KeyInputEvent.PRESSED);
        key(4, KeyInputEvent.RELEASED);
        key(224, KeyInputEvent.RELEASED);
        tick();
        assertEquals(List.of(true, false), calls);
    }

    @Test
    void typingEligibilityStaysBlockedUntilTheKeyIsReleased() {
        bind(4);
        screen(new Object());
        TextInputFocus.acquire(focus);
        key(4, KeyInputEvent.PRESSED);
        tick();
        TextInputFocus.release(focus);
        tick();
        key(5, KeyInputEvent.PRESSED);
        tick();
        assertTrue(calls.isEmpty());
        key(4, KeyInputEvent.RELEASED);
        key(4, KeyInputEvent.PRESSED);
        tick();
        assertEquals(List.of(true), calls);
    }

    @Test
    void aTypingOptInDoesNotReplayAfterFocusMovesBeforeDispatch() {
        bind(4).setFiresWhileTyping(true);
        screen(new Object());
        TextInputFocus.acquire(focus);
        key(4, KeyInputEvent.PRESSED);
        key(4, KeyInputEvent.RELEASED);
        TextInputFocus.release(focus);
        tick();
        assertTrue(calls.isEmpty());
    }

    @Test
    void typingOptInStillWorksWhileTheOriginalFieldKeepsFocus() {
        bind(4).setFiresWhileTyping(true);
        screen(new Object());
        TextInputFocus.acquire(focus);
        key(4, KeyInputEvent.PRESSED);
        tick();
        assertEquals(List.of(true), calls);
        TextInputFocus.release(focus);
        tick();
        tick();
        assertEquals(List.of(true, false), calls);
    }

    @Test
    void anActiveKeyDoesNotRefireWhenTypingFocusLeaves() {
        bind(4);
        screen(new Object());
        key(4, KeyInputEvent.PRESSED);
        tick();
        TextInputFocus.acquire(focus);
        tick();
        TextInputFocus.release(focus);
        tick();
        key(5, KeyInputEvent.PRESSED);
        tick();
        assertEquals(List.of(true, false), calls);
    }

    @Test
    void closingAScreenDiscardsItsTypingTap() {
        bind(4).setFiresWhileTyping(true);
        screen(new Object());
        TextInputFocus.acquire(focus);
        key(4, KeyInputEvent.PRESSED);
        key(4, KeyInputEvent.RELEASED);
        screen(null);
        tick();
        assertTrue(calls.isEmpty());
        key(4, KeyInputEvent.PRESSED);
        key(4, KeyInputEvent.RELEASED);
        tick();
        assertEquals(List.of(true, false), calls);
    }

    @Test
    void inputFromAMenuCannotActivateAnInGameOnlyBindAfterClosing() {
        register(new BindNotInScreen(new int[]{4}, null, (byte) 0, 0, down -> { calls.add(down); return true; }));
        screen(new Object());
        key(4, KeyInputEvent.PRESSED);
        key(4, KeyInputEvent.RELEASED);
        TestScreenPlatform.setCurrent(null);
        tick();
        assertTrue(calls.isEmpty());
    }

    @Test
    void anActionOpeningAMenuDiscardsOtherQueuedPressesFromTheGame() {
        List<Boolean> second = new ArrayList<>();
        register(new OneConfigKeybind(new int[]{4}, null, (byte) 0, 0, down -> {
            calls.add(down);
            if (down) screen(new Object());
            return true;
        }));
        register(new OneConfigKeybind(new int[]{5}, null, (byte) 0, 0, down -> { second.add(down); return true; }));
        key(4, KeyInputEvent.PRESSED);
        key(5, KeyInputEvent.PRESSED);
        tick();
        assertEquals(List.of(true, false), calls);
        assertTrue(second.isEmpty());
        tick();
        assertTrue(second.isEmpty());
    }

    @Test
    void losingWindowFocusDropsPendingTapsAndReleasesActiveBinds() {
        bind(4);
        key(4, KeyInputEvent.PRESSED);
        key(4, KeyInputEvent.RELEASED);
        post(WindowFocusEvent.Lost.INSTANCE);
        tick();
        assertTrue(calls.isEmpty());
        key(4, KeyInputEvent.PRESSED);
        tick();
        post(WindowFocusEvent.Lost.INSTANCE);
        tick();
        assertEquals(List.of(true, false), calls);
    }

    @Test
    void replacingABindDropsItsOldTapAndKeepsTheCopiedAction() {
        OneConfigKeybind old = bind(4);
        key(4, KeyInputEvent.PRESSED);
        key(4, KeyInputEvent.RELEASED);
        OneConfigKeybind replacement = old.copyWith(new int[]{5}, null, (byte) 0);
        KeybindManager.replace(old, replacement);
        binds.add(replacement);
        tick();
        assertTrue(calls.isEmpty());
        key(5, KeyInputEvent.PRESSED);
        key(5, KeyInputEvent.RELEASED);
        tick();
        assertEquals(List.of(true, false), calls);
    }

    private OneConfigKeybind bind(int key) {
        return register(new OneConfigKeybind(new int[]{key}, null, (byte) 0, 0, down -> { calls.add(down); return true; }));
    }

    private OneConfigKeybind register(OneConfigKeybind bind) {
        binds.add(bind);
        return KeybindManager.register(bind);
    }

    private static void key(int code, int state) { post(new KeyInputEvent(code, (char) 0, state)); }
    private static void tick() { post(TickEvent.End.INSTANCE); }
    private static void post(org.polyfrost.oneconfig.api.event.v1.events.Event event) { EventManager.INSTANCE.post(event); }
    private static void screen(Object screen) {
        post(new ScreenOpenEvent(screen));
        TestScreenPlatform.setCurrent(screen);
    }
}
