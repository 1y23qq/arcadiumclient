// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.arcadiumclient.runtime.client.optimizations.ModernArmorGlintPipeline;
import io.github.arcadiumclient.runtime.client.optimizations.ModernArmorMvp072Qa;
import org.lwjgl.opengl.GL20;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value=ModernArmorGlintPipeline.class,remap=false)
public abstract class ModernGlint072MvpProgramProbe {
    @WrapMethod(method="beginProgram")
    private static int modern$program(int program,Operation<Integer> original){int location=ModernArmorMvp072Qa.uniformLocation(program),value=location<0?0:GL20.glGetUniformi(program,location);int token=original.call(program);ModernArmorMvp072Qa.program(token,program,location,value);return token;}
    @WrapMethod(method="endDraw")
    private static void modern$restore(int token,Operation<Void> original){original.call(token);ModernArmorMvp072Qa.restored(token);}
}
