// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.mixin;
import io.github.arcadiumclient.runtime.qa.CeraAdmission072Qa;
import net.minecraft.resource.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector4fc;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Pseudo
@Mixin(targets="dev.rdh.argentum.impl.render.entity.instancing.ModelInstancer",remap=false)
public abstract class CeraAdmission072SubmitProbe {
    @Inject(method="submit",at=@At("HEAD"),remap=false)
    private void cera$submit(@Coerce Object geometry,Identifier texture,@Coerce Object pass,Matrix4f matrix,int light,Vector4fc color,float time,Vector4fc overlay,@Coerce Object template,CallbackInfoReturnable<Boolean> callback){CeraAdmission072Qa.submit(texture,pass);}
}
