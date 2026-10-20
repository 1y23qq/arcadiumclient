package org.codeberg.chromatic.freelook.mixin.legacy1710;

//? if =1.7.10 {
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.entity.living.LivingEntity;
import org.codeberg.chromatic.freelook.handler.FreelookHandler;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(EntityRenderDispatcher.class)
public abstract class MixinEntityRenderDispatcher {

    @Redirect(
            method = "prepare",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/entity/living/LivingEntity;yaw:F",
                    opcode = Opcodes.GETFIELD
            )
    )
    private float modifyYaw(LivingEntity entity) {
        return FreelookHandler.INSTANCE.cameraYaw(entity, entity.yaw);
    }

    @Redirect(
            method = "prepare",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/entity/living/LivingEntity;lastYaw:F",
                    opcode = Opcodes.GETFIELD
            )
    )
    private float modifyLastYaw(LivingEntity entity) {
        return FreelookHandler.INSTANCE.cameraYaw(entity, entity.lastYaw);
    }

    @Redirect(
            method = "prepare",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/entity/living/LivingEntity;pitch:F",
                    opcode = Opcodes.GETFIELD
            )
    )
    private float modifyPitch(LivingEntity entity) {
        return FreelookHandler.INSTANCE.cameraPitch(entity, entity.pitch);
    }

    @Redirect(
            method = "prepare",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/entity/living/LivingEntity;lastPitch:F",
                    opcode = Opcodes.GETFIELD
            )
    )
    private float modifyLastPitch(LivingEntity entity) {
        return FreelookHandler.INSTANCE.cameraPitch(entity, entity.lastPitch);
    }

}
//?}
