//? if =1.7.10 {
package org.polyfrost.damagetint.client.utils;

import net.minecraft.world.entity.LivingEntity;
import org.polyfrost.damagetint.client.DamageTintConfig;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

public final class NativeTint1710 {
    private NativeTint1710() { }

    public static void apply(Args args, LivingEntity entity) {
        if (!DamageTintConfig.enabled || entity.hurtTime == 0 && entity.deathTime == 0) return;
        int color = DamageTintConfig.colorFor(DamageVariantTracker.get(entity)).getArgb();
        float alpha = (color >>> 24) / 255f;
        if (DamageTintConfig.fade) {
            if (entity.deathTime > 0 && DamageTintConfig.fadeDeath)
                alpha *= Math.clamp(1f - entity.deathTime / DamageTintConfig.fadeDuration, 0f, 1f);
            else if (entity.deathTime == 0)
                alpha *= Math.clamp(entity.hurtTime / DamageTintConfig.fadeDuration, 0f, 1f);
        }
        args.set(0, (color >>> 16 & 255) / 255f);
        args.set(1, (color >>> 8 & 255) / 255f);
        args.set(2, (color & 255) / 255f);
        args.set(3, alpha);
    }
}
//?}
