package xyz.tryfle.oitemphysic.mixin;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.block.SnowLayerBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.entity.ItemRenderer;
import net.minecraft.entity.ItemEntity;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.tryfle.oitemphysic.config.OSLConfig;

@Mixin(ItemRenderer.class)
public abstract class ItemEntityRendererMixin {
    @Unique private final Map<ItemEntity, Float> oitemphysic$groundYaws = new WeakHashMap<>();

    @ModifyVariable(method = "render(Lnet/minecraft/entity/ItemEntity;DDDFF)V", at = @At("STORE"), ordinal = 2)
    private float oitemphysic$removeBobbing(float original) { return OSLConfig.toggled.get() ? 0 : original; }

    @Redirect(method = "render(Lnet/minecraft/entity/ItemEntity;DDDFF)V", at = @At(value = "INVOKE",
            target = "Lorg/lwjgl/opengl/GL11;glRotatef(FFFF)V", ordinal = 0, remap = false))
    private void oitemphysic$blockSpin(float angle, float x, float y, float z) {
        if (!OSLConfig.toggled.get()) GL11.glRotatef(angle, x, y, z);
    }

    @Redirect(method = "render(Lnet/minecraft/entity/ItemEntity;Lnet/minecraft/client/render/texture/Sprite;IFFFF)V",
            at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glRotatef(FFFF)V", ordinal = 1, remap = false))
    private void oitemphysic$itemSpin(float angle, float x, float y, float z) {
        if (!OSLConfig.toggled.get()) GL11.glRotatef(angle, x, y, z);
    }

    @Redirect(method = "render(Lnet/minecraft/entity/ItemEntity;Lnet/minecraft/client/render/texture/Sprite;IFFFF)V",
            at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glRotatef(FFFF)V", ordinal = 4, remap = false))
    private void oitemphysic$flatBillboard(float angle, float x, float y, float z) {
        if (!OSLConfig.toggled.get()) GL11.glRotatef(angle, x, y, z);
    }

    @Inject(method = "render(Lnet/minecraft/entity/ItemEntity;DDDFF)V", at = @At(value = "INVOKE",
            target = "Lorg/lwjgl/opengl/GL11;glTranslatef(FFF)V", ordinal = 0, shift = At.Shift.AFTER, remap = false))
    private void oitemphysic$orient(ItemEntity entity, double dx, double dy, double dz, float yaw,
                                   float tickDelta, CallbackInfo callback) {
        if (!OSLConfig.toggled.get()) return;
        double vx = entity.velocityX, vz = entity.velocityZ;
        float movementYaw;
        if (vx * vx + vz * vz > 0.0001) {
            movementYaw = (float) Math.toDegrees(Math.atan2(-vx, vz)) + entity.getNetworkId() % 30 - 15;
            oitemphysic$groundYaws.put(entity, movementYaw);
        } else {
            Float cached = oitemphysic$groundYaws.get(entity);
            if (cached == null) { cached = (float) (entity.getNetworkId() % 360); oitemphysic$groundYaws.put(entity, cached); }
            movementYaw = cached;
        }
        GL11.glTranslatef(0, -0.1f, 0);
        GL11.glRotatef(movementYaw, 0, 1, 0);
        if (entity.onGround) {
            if (!(Minecraft.getInstance().world.getBlock(MathHelper.floor(entity.x), MathHelper.floor(entity.y),
                    MathHelper.floor(entity.z)) instanceof SnowLayerBlock)) GL11.glTranslatef(0, -0.1f, 0);
            GL11.glRotatef(90, 1, 0, 0);
        } else {
            float speed = OSLConfig.fallingRotationSpeed.get();
            GL11.glRotatef((entity.age + tickDelta) * 8 * speed, 1, 0, 0);
            GL11.glRotatef((float) (vx * speed) * 180, 0, 1, 0);
            GL11.glRotatef((float) (vz * speed) * 180, 0, 0, 1);
        }
    }
}
