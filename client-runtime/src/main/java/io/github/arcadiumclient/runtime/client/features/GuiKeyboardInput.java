// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 Arcadium contributors.
package io.github.arcadiumclient.runtime.client.features;

import com.mojang.blaze3d.platform.TextInputManager;
import io.github.arcadiumclient.runtime.mixin.compat.ComposeTextInputAccess;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.game.BookEditScreen;
import net.minecraft.client.gui.screen.game.ChatScreen;
import net.minecraft.client.gui.screen.game.SignEditScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import org.polyfrost.oneconfig.api.ui.v1.keybind.TextInputFocus;

public final class GuiKeyboardInput {
    private static final Object OWNER = new Object();
    private static Screen previousScreen;
    private static boolean previousTextInput;
    private static final ClassValue<Field[]> TEXT_FIELDS = new ClassValue<>() {
        @Override protected Field[] computeValue(Class<?> type) {
            var fields = new ArrayList<Field>();
            while (type != null && Screen.class.isAssignableFrom(type)) {
                for (Field field : type.getDeclaredFields()) {
                    if (!Modifier.isStatic(field.getModifiers()) && TextFieldWidget.class.isAssignableFrom(field.getType())
                            && field.trySetAccessible()) fields.add(field);
                }
                type = type.getSuperclass();
            }
            return fields.toArray(Field[]::new);
        }
    };

    private GuiKeyboardInput() { }

    public static boolean wantsTextInput(Screen screen) {
        if (screen == null) return false;
        if (screen instanceof ChatScreen || screen instanceof BookEditScreen || screen instanceof SignEditScreen) return true;
        if (screen instanceof ComposeTextInputAccess compose) return compose.arcadium$isEditingText();
        if (TextInputFocus.isTyping()) return true;
        for (Field field : TEXT_FIELDS.get(screen.getClass())) {
            try {
                var input = (TextFieldWidget) field.get(screen);
                if (input != null && input.isVisible() && input.isFocused()) return true;
            } catch (IllegalAccessException ignored) { }
        }
        return false;
    }

    public static void update(boolean screenOpened) {
        Screen screen = Minecraft.getInstance().screen;
        boolean textInput = wantsTextInput(screen);
        if (!screenOpened && screen instanceof ComposeTextInputAccess) {
            previousScreen = screen;
            previousTextInput = textInput;
            return;
        }
        if (!screenOpened && screen == previousScreen && textInput == previousTextInput) return;
        previousScreen = screen;
        previousTextInput = textInput;
        if (textInput) TextInputManager.getInstance().startTextInput(OWNER);
        else TextInputManager.getInstance().stopTextInput(OWNER);
    }
}
