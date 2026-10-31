package io.github.arcadiumclient.modern.gui.qa.mixin;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GlyphSource;
import net.minecraft.network.chat.FontDescription;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
@Mixin(Font.class)
public interface FontSource060Access {
    @Invoker("getGlyphSource") GlyphSource release$source(FontDescription description);
}
