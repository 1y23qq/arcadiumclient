// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.util.Objects;

final class GpuVoxelSkinSelector {
    private static final MethodType ERASED=MethodType.methodType(Object.class,Object.class,Object.class,Object.class);
    private final MethodHandle target;
    GpuVoxelSkinSelector(MethodHandle source){
        MethodType type=Objects.requireNonNull(source).type();
        if(type.parameterCount()!=3||type.returnType().isPrimitive())throw new IllegalArgumentException("Unknown native voxel selector type");
        for(Class<?> parameter:type.parameterArray())if(parameter.isPrimitive())throw new IllegalArgumentException("Unknown native voxel selector parameter");
        target=source.asType(ERASED);
    }
    Object select(Object backend,Object texture,Object pass)throws Throwable{return(Object)target.invokeExact(backend,texture,pass);}
}
