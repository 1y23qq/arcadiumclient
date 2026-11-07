package io.github.arcadiumclient.runtime.mixin.compat.argentum;

import com.moulberry.mixinconstraints.annotations.IfModLoaded;
import net.minecraft.client.render.model.entity.HumanoidModel;
import org.spongepowered.asm.mixin.Mixin;

@IfModLoaded("argentum")
@Mixin(value=HumanoidModel.class,priority=1)
public abstract class MixinGpuModelPreparedHumanoidMarker073 {}
