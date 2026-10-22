// SPDX-License-Identifier: MIT
package io.github.arcadiumclient.forge1710.platform;

import net.minecraft.client.Minecraft;
import org.lwjglx.input.Keyboard;
import org.lwjglx.input.Mouse;
import org.polyfrost.oneconfig.api.event.v1.EventManager;
import org.polyfrost.oneconfig.api.event.v1.events.KeyInputEvent;
import org.polyfrost.oneconfig.api.event.v1.events.MouseInputEvent;
import org.polyfrost.oneconfig.internal.legacy.KeyCodes;

public final class ForgeInputEvents {
    private ForgeInputEvents() {}

    public static boolean keyboard(boolean available) {
        if (!available) return false;
        int key = Keyboard.getEventKey();
        if (key > 0) EventManager.INSTANCE.post(new KeyInputEvent(KeyCodes.fromLegacy(key).getValue(), (char) 0,
            Keyboard.isRepeatEvent() ? KeyInputEvent.REPEAT : Keyboard.getEventKeyState() ? KeyInputEvent.PRESSED : KeyInputEvent.RELEASED));
        char character = Keyboard.getEventCharacter();
        if (character != 0) EventManager.INSTANCE.post(new KeyInputEvent(0, character, KeyInputEvent.PRESSED));
        return true;
    }

    public static boolean mouse(boolean available) {
        if (!available) return false;
        int button = Mouse.getEventButton();
        if (button >= 0) {
            EventManager.INSTANCE.post(new MouseInputEvent(KeyCodes.mouseFromLegacy(button), Mouse.getEventButtonState() ? 1 : 0));
        } else if (Mouse.getEventDWheel() == 0 && Minecraft.func_71410_x().field_71462_r != null &&
            (Mouse.getEventDX() != 0 || Mouse.getEventDY() != 0)) {
            var display = WindowHost.instance();
            float scaleX = (float) display.getWidth() / display.getWindowWidth();
            float scaleY = (float) display.getHeight() / display.getWindowHeight();
            MouseInputEvent.Moved.post(Mouse.getEventX() / scaleX, (display.getHeight() - 1 - Mouse.getEventY()) / scaleY);
        }
        return true;
    }
}
