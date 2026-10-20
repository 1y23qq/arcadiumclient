package org.polyfrost.chatting.mixin;

//? if >=26 {
/*import net.minecraft.client.multiplayer.chat.GuiMessage;
*///?} else {
//~ if <= 1.8.9 'net.minecraft.client.GuiMessage' -> 'net.minecraft.client.gui.ChatMessage'
import net.minecraft.client.gui.ChatMessage;
//?}
import net.minecraft.client.gui.components.ChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;

@Mixin(ChatComponent.class)
public interface ChatComponentAccessor {

    @Accessor("chatScrollbarPos")
    int chatting$getScrollbarPos();

    @Accessor("trimmedMessages")
    //~ if <= 1.8.9 'GuiMessage.Line' -> 'ChatMessage'
    List<ChatMessage> chatting$getTrimmedMessages();

    @Accessor("allMessages")
    //~ if <= 1.8.9 'GuiMessage' -> 'ChatMessage'
    List<ChatMessage> chatting$getAllMessages();

    //? if > 1.8.9 {
    /*@Invoker("getLineHeight")
    int chatting$getLineHeight();
    *///?}

    @Invoker("getScale")
    //~ if <= 1.8.9 'double' -> 'float'
    float chatting$getScale();

    @Invoker("getWidth")
    int chatting$getWidth();

    //~ if <= 1.8.9 'refreshTrimmedMessages' -> 'rescaleChat'
    @Invoker("rescaleChat")
    void chatting$refreshTrimmedMessages();
    //? if <= 1.8.9 {

    @Accessor("hasNewMessagesSinceScroll")
    boolean chatting$hasNewMessagesSinceScroll();
    //?}
}
