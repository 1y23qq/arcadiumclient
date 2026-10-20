package org.codeberg.chromatic.freelook.mixin;

import org.codeberg.chromatic.freelook.handler.CameraCycleHandler;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
//? if >1.8.9
//import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
//? if <=1.8.9
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Minecraft.class)
public class MixinMinecraft {

    //? if >1.8.9 {
    /*@WrapWithCondition(
            method = "handleKeybinds",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/Options;setCameraType(Lnet/minecraft/client/CameraType;)V"
            )
    )
    private boolean overrideCameraCycle(Options instance, CameraType cameraType) {
    *///?} else {
    @WrapWithCondition(
            method = "tick",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/client/Options;perspective:I",
                    opcode = Opcodes.PUTFIELD,
                    ordinal = 0
            )
    )
    private boolean overrideCameraCycle(Options instance, int perspective) {
    //?}
        return CameraCycleHandler.shouldOverrideCameraCycle();
    }

}
