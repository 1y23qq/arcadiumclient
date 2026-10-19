package io.github.arcadiumclient.featuretest.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Invoker;

@Pseudo
@Mixin(targets = "org.polyfrost.oneconfig.internal.ui.compose.ComposeScreen", remap = false)
public interface AutoTextInputProbeAccess {
    @Invoker(value = "sendKeyPressedEvent", remap = false)
    boolean arcadium$autoTextKeyDown(int key, int shortcut, int modifiers);

    @Invoker(value = "sendCharacterEvent", remap = false)
    boolean arcadium$autoTextCharacter(char character, int codePoint, int modifiers);

    @Invoker(value = "sendKeyReleasedEvent", remap = false)
    boolean arcadium$autoTextKeyUp(int key, int shortcut, int modifiers);
}
