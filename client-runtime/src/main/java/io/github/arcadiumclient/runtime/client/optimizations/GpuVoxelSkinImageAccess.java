// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import java.awt.image.BufferedImage;
import java.lang.invoke.*;
import java.util.BitSet;

final class GpuVoxelSkinImageAccess {
    private final Class<?> imageAccess;
    private final MethodHandle image, uvConstructor, uvU, uvV, sizeOf, texelOf;
    private final Object[][] layouts = new Object[2][12];
    private final BitSet[][] domains = new BitSet[2][12];
    private final Object[] faces;

    GpuVoxelSkinImageAccess() throws ReflectiveOperationException { this(GpuVoxelSkinImageAccess.class.getClassLoader()); }
    GpuVoxelSkinImageAccess(ClassLoader loader) throws ReflectiveOperationException {
        imageAccess = Class.forName("org.polyfrost.skinlayers.skin.SkinTextureImageAccess",true,loader);
        image = method(imageAccess,"image");
        Class<?> layout = Class.forName("org.polyfrost.skinlayers.mesh.BoxLayout",true,loader), face = Class.forName("org.polyfrost.skinlayers.mesh.Face",true,loader), uv = Class.forName("org.polyfrost.skinlayers.mesh.Uv",true,loader);
        faces = face.getEnumConstants(); uvConstructor = MethodHandles.publicLookup().unreflectConstructor(uv.getConstructor(int.class,int.class));
        uvU = method(uv,"getU"); uvV = method(uv,"getV"); sizeOf = method(layout,"sizeOf",face); texelOf = method(layout,"texelOf",face,uv);
        Class<?> meshes = Class.forName("org.polyfrost.skinlayers.skin.SkinMeshes",true,loader);
        var constructor = layout.getConstructor(int.class,int.class,int.class,int.class,int.class);
        for (int slim=0;slim<2;slim++) {
            layouts[slim][6] = field(meshes,"HEAD").get(); layouts[slim][7] = field(meshes,"LEFT_LEG").get();
            layouts[slim][8] = field(meshes,"RIGHT_LEG").get(); layouts[slim][11] = field(meshes,"BODY").get();
            layouts[slim][9] = constructor.newInstance(slim == 0 ? 4 : 3,12,4,48,48);
            layouts[slim][10] = constructor.newInstance(slim == 0 ? 4 : 3,12,4,40,32);
        }
        if (faces.length != 6) throw new NoSuchFieldException("Unknown skin image/layout ABI");
    }

    boolean supports(Object source){return imageAccess.isInstance(source);}
    Object imageIdentity(Object source) throws Throwable { return imageAccess.isInstance(source) ? image.invoke(source) : null; }
    byte[] snapshot(Object identity) {
        if(identity instanceof GpuVoxelSkinUploadFacts.Fact fact)return fact.alpha;
        if (!(identity instanceof BufferedImage bitmap) || bitmap.getWidth() != 64 || bitmap.getHeight() != 64) return null;
        int[] values = bitmap.getRGB(0,0,64,64,null,0,64);
        byte[] alpha = new byte[values.length];
        for (int index=0;index<values.length;index++) alpha[index] = (byte)(values[index] >>> 24);
        return alpha;
    }

    BitSet domain(int part, boolean slim) throws Throwable {
        int kind = slim ? 1 : 0;
        if (part < 6 || part > 11) return null;
        BitSet known = domains[kind][part]; if (known != null) return known;
        known = new BitSet(4096); Object layout = layouts[kind][part];
        for (Object face:faces) {
            Object size = sizeOf.invoke(layout,face); int w=(int)uvU.invoke(size), h=(int)uvV.invoke(size);
            if (w < 1 || w > 64 || h < 1 || h > 64) return null;
            for (int y=0;y<h;y++) for (int x=0;x<w;x++) {
                Object texel = texelOf.invoke(layout,face,uvConstructor.invoke(x,y)); int u=(int)uvU.invoke(texel),v=(int)uvV.invoke(texel);
                if (u < 0 || u >= 64 || v < 0 || v >= 64) return null;
                known.set(v*64+u);
            }
        }
        domains[kind][part] = known; return known;
    }
    private static MethodHandle method(Class<?> type,String name,Class<?>... arguments) throws ReflectiveOperationException {
        return MethodHandles.privateLookupIn(type,MethodHandles.lookup()).unreflect(type.getDeclaredMethod(name,arguments));
    }
    private static VarHandle field(Class<?> type,String name) throws ReflectiveOperationException {
        return MethodHandles.privateLookupIn(type,MethodHandles.lookup()).unreflectVarHandle(type.getDeclaredField(name));
    }
}
