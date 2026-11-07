// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa.mixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.arcadiumclient.runtime.client.optimizations.GpuVoxelSkinProjectionRows;
import io.github.arcadiumclient.runtime.qa.P3PhysicalOrdinary073Evidence;
import org.spongepowered.asm.mixin.Mixin;
@Mixin(value=GpuVoxelSkinProjectionRows.class,remap=false)
public abstract class P3PhysicalOrdinaryRows073Probe {
    @WrapMethod(method="captureAtCompiledList",remap=false)
    private long p3$captured(Object owner,long epoch,long frame,Object mesh,int part,float scale,Operation<Long> original){long ticket=original.call(owner,epoch,frame,mesh,part,scale);P3PhysicalOrdinary073Evidence.row((GpuVoxelSkinProjectionRows)(Object)this,owner,epoch,frame,mesh,part,ticket);return ticket;}
    @WrapMethod(method="reserveBeforeNativeSubmit",remap=false)
    private boolean p3$reserved(long ticket,Object instances,int count,Operation<Boolean> original){boolean value=original.call(ticket,instances,count);P3PhysicalOrdinary073Evidence.reserve(this,ticket,instances,count,value);return value;}
    @WrapMethod(method="commitAfterNativeAppend",remap=false)
    private boolean p3$committed(long ticket,int count,Operation<Boolean> original){boolean value=original.call(ticket,count);P3PhysicalOrdinary073Evidence.committed(this,ticket,count,value);return value;}
}
