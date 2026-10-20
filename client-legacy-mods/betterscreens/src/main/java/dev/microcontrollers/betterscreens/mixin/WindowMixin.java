package dev.microcontrollers.betterscreens.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.microcontrollers.betterscreens.hook.ContainerScaleHook;
import net.minecraft.client.options.GameOptions;
import net.minecraft.client.render.Window;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Window.class)
public class WindowMixin {
    @WrapOperation(method = "<init>", at = @At(value = "FIELD", target = "Lnet/minecraft/client/options/GameOptions;guiScale:I", opcode = Opcodes.GETFIELD))
    private int betterscreens$overrideGuiScale(GameOptions options, Operation<Integer> original) {
        int scale = ContainerScaleHook.getNewScale();
        return scale == -1 ? original.call(options) : scale;
    }
}
