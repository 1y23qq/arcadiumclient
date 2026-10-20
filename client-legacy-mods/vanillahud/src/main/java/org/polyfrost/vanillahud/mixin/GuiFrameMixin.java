package org.polyfrost.vanillahud.mixin;

//? if > 1.8.9 {
/*//? if > 1.8.9
//import net.minecraft.client.DeltaTracker;
//? if > 1.8.9
//import net.minecraft.client.gui.GuiGraphics;
*///?}
//? if >=26.2 {
/*import net.minecraft.client.gui.Hud;
*///?} elif > 1.8.9 {
/*import net.minecraft.client.gui.Gui;
*///?} else {
import net.minecraft.client.gui.GameGui;
import org.polyfrost.vanillahud.compat.LegacyDrawContext;
//?}
import org.polyfrost.vanillahud.hud.VanillaHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

//? if >=26.2 {
/*@Mixin(Hud.class)
*///?} elif > 1.8.9 {
/*@Mixin(Gui.class)
*///?} else {
@Mixin(GameGui.class)
//?}
public class GuiFrameMixin {

    //? if <= 1.8.9 {
    @Inject(method = "render", at = @At("HEAD"))
    //? if = 1.7.10 {
    private void vanillahud$beginFrame(float tickDelta, boolean mouseGrabbed, int mouseX, int mouseY, CallbackInfo ci) {
    //?} else {
    /*private void vanillahud$beginFrame(float tickDelta, CallbackInfo ci) {
    *///?}
        VanillaHud.beginFrame(LegacyDrawContext.INSTANCE);
    }
    //?} elif <26 {
    /*@Inject(method = "render", at = @At("HEAD"))
    private void vanillahud$beginFrame(GuiGraphics graphics, DeltaTracker delta, CallbackInfo ci) {
        VanillaHud.beginFrame(graphics);
    }
    *///?} else {
    /*@Inject(method = "extractRenderState", at = @At("HEAD"))
    private void vanillahud$beginFrame(GuiGraphics graphics, DeltaTracker delta, CallbackInfo ci) {
        VanillaHud.beginFrame(graphics);
    }
    *///?}
}
