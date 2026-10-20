package org.codeberg.chromatic.freelook.mixin.modern;

//? if >1.8.9 {
/*import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.MouseHandler;
import org.codeberg.chromatic.freelook.handler.FreelookHandler;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(MouseHandler.class)
public abstract class MixinMouseHandler {

    @ModifyExpressionValue(
            method = "turnPlayer",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/client/Options;smoothCamera:Z",
                    opcode = Opcodes.GETFIELD
            )
    )
    private boolean useCinematicCamera(boolean original) {
        return original || FreelookHandler.INSTANCE.useCinematicCamera();
    }

    @ModifyArg(
            method = "turnPlayer",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/SmoothDouble;getNewDeltaValue(DD)D"
            ),
            index = 1
    )
    private double scaleCinematicSmoothing(double factor) {
        return FreelookHandler.INSTANCE.cinematicSmoothing(factor);
    }

}
*///?}
