package org.polyfrost.vanillahud.hook;

//? if > 1.8.9 {
/*import net.minecraft.client.gui.GuiGraphics;
//? if >= 26.1 {
/^import net.minecraft.client.gui.components.PlayerFaceExtractor;
^///?} else {
import net.minecraft.client.gui.components.PlayerFaceRenderer;
//?}
import net.minecraft.resources.ResourceLocation;

@SuppressWarnings("InstantiationOfUtilityClass")
public interface HeadHook {
    //~ if >=26 'PlayerFaceRenderer' -> 'PlayerFaceExtractor'
    HeadHook INSTANCE = (HeadHook) new PlayerFaceRenderer();

    void vanillahud$draw(GuiGraphics graphics, ResourceLocation texture, int x, int y, int size, int color,
                         boolean hatVisible, boolean flip);
}
*///?} else {
// nothing implements this on 1.8.9
public interface HeadHook {
}
//?}
