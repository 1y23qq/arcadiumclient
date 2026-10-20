package dev.microcontrollers.betterscreens.mixin.accessor;

import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.widget.ServerListEntryWidget;
import net.minecraft.client.options.ServerListEntry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ServerListEntryWidget.class)
public interface ServerListEntryWidgetAccessor {
    @Invoker("<init>")
    static ServerListEntryWidget betterscreens$init(MultiplayerScreen screen, ServerListEntry entry) {
        throw new AssertionError();
    }

    @Accessor("entry")
    ServerListEntry betterscreens$getEntry();
}
