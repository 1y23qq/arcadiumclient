// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.mixin.lowernames073;
import io.github.arcadiumclient.runtime.qa.HologramNameNative073Qa;
import io.github.arcadiumclient.runtime.qa.lowernames073.LowerNameNative073Evidence;
import java.util.List;
import net.minecraft.entity.living.ArmorStandEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
@Mixin(value=HologramNameNative073Qa.class,remap=false)
public abstract class LowerName073SceneProbe {
    @Inject(method="verify",at=@At("HEAD"),remap=false) private static void lower073$start(CallbackInfo c){LowerNameNative073Evidence.start();}
    @Inject(method="capture",at=@At("HEAD"),remap=false) private static void lower073$captureHead(List<ArmorStandEntity> a,boolean b,boolean d,boolean e,boolean f,int g,int h,String name,CallbackInfoReturnable<?> c){LowerNameNative073Evidence.capture(name,true);}
    @Inject(method="capture",at=@At("RETURN"),remap=false) private static void lower073$captureReturn(List<ArmorStandEntity> a,boolean b,boolean d,boolean e,boolean f,int g,int h,String name,CallbackInfoReturnable<?> c){LowerNameNative073Evidence.capture(name,false);}
    @Inject(method="image",at=@At("HEAD"),remap=false) private static void lower073$imageHead(List<ArmorStandEntity> a,boolean b,boolean d,boolean e,boolean f,int g,CallbackInfoReturnable<?> c){LowerNameNative073Evidence.image(true);}
    @Inject(method="image",at=@At("RETURN"),remap=false) private static void lower073$imageReturn(List<ArmorStandEntity> a,boolean b,boolean d,boolean e,boolean f,int g,CallbackInfoReturnable<?> c){LowerNameNative073Evidence.image(false);}
    @Inject(method="finish",at=@At("HEAD"),remap=false) private static void lower073$finish(Throwable failure,CallbackInfo c){LowerNameNative073Evidence.finish(failure);}
}
