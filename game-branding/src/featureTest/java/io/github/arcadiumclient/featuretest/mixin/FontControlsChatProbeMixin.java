package io.github.arcadiumclient.featuretest.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.arcadiumclient.featuretest.FontControlsQa;
import java.util.List;
import java.util.ArrayList;
import net.minecraft.client.gui.ChatMessage;
import net.minecraft.client.gui.chat.ChatGui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value = ChatGui.class, priority = 350)
public abstract class FontControlsChatProbeMixin {
    @Shadow private List<ChatMessage> trimmedMessages;
    @Shadow private int scroll;

    @WrapMethod(method = "render")
    private void arcadium$chatProbe(int ticks, Operation<Void> original) {
        boolean replay = FontControlsQa.chatStart(trimmedMessages.size());
        if (FontControlsQa.sampleChatLines()) {
            List<String> sample = new ArrayList<>();
            int marker = -1;
            for (int i = 0; i < trimmedMessages.size(); i++) {
                String text = trimmedMessages.get(i).getText().getString();
                if (text.contains("FONT_QA_CHAT_033")) marker = i;
                if (i < 10) sample.add(text);
            }
            FontControlsQa.chatLines(scroll, marker, sample);
        }
        try { original.call(ticks); }
        finally { FontControlsQa.chatEnd(replay); }
    }
}
