package org.polyfrost.oneconfig.internal.mixin.network;

//? if <= 1.8.9 {
/*import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import org.polyfrost.oneconfig.internal.network.ChatSendTrace;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value = ChatScreen.class, priority = 1900)
public abstract class Mixin_LegacyChatInputTrace {
    @Shadow protected TextFieldWidget chatField;

    @WrapMethod(method = "keyPressed")
    private void arcadium$chatInput(char character, int key, Operation<Void> original) {
        if ((key != 28 && key != 156) || chatField == null) { original.call(character, key); return; }
        String text = chatField.getText();
        try (ChatSendTrace.Attempt trace = ChatSendTrace.input(text.length(), text.startsWith("/"), Minecraft.getInstance().isSameThread())) {
            original.call(character, key);
        }
    }
}
*///?}
