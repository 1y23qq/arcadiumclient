// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import java.util.BitSet;

final class GpuVoxelSkinImagePolicy {
    enum Alpha { OPAQUE, BINARY, FRACTIONAL, UNKNOWN }
    record Stamp(Object source, Object image, int texture, int generation) {
        boolean sameNative(Object value,int name,int revision){return source==value&&texture==name&&generation==revision;}
        boolean matches(Object value, Object bitmap, int name, int revision) {
            return sameNative(value,name,revision) && image == bitmap;
        }
    }
    static boolean ambiguousImage(Stamp previous,boolean alreadyAmbiguous,Object source,Object image,int texture,int generation){
        return previous!=null&&previous.sameNative(source,texture,generation)&&(alreadyAmbiguous||previous.image()!=image);
    }
    private GpuVoxelSkinImagePolicy() {}

    static boolean defaultDimensions(GpuVoxelSkinMath.Options options) {
        return options != null && options.base() == 1.15f && options.bodyWidth() == 1.05f && options.head() == 1.18f;
    }
    static boolean supportedSampler(int min, int mag, int s, int t) {
        return min == 9728 && mag == 9728 && supportedWrap(s) && supportedWrap(t);
    }
    static boolean rgba8(int format,int red,int green,int blue,int alpha){
        return (format==32856||format==6408)&&red==8&&green==8&&blue==8&&alpha==8;
    }
    static int requiredParts(int enabled,int visible){return enabled&visible&0xFC0;}
    static boolean attachmentsReady(int required,int meshes,int offsets){return (required&meshes&offsets)==required;}
    private static boolean supportedWrap(int wrap) { return wrap == 10497 || wrap == 33071; }

    static Alpha classify(byte[] alpha, BitSet domain) {
        if (alpha == null || alpha.length != 4096 || domain == null || domain.isEmpty() || domain.length() > alpha.length) return Alpha.UNKNOWN;
        boolean zero = false;
        for (int index = domain.nextSetBit(0); index >= 0; index = domain.nextSetBit(index + 1)) {
            int value = alpha[index] & 255;
            if (value == 0) zero = true;
            else if (value != 255) return Alpha.FRACTIONAL;
        }
        return zero ? Alpha.BINARY : Alpha.OPAQUE;
    }

    static BitSet footprint(float[] quads, BitSet faceTexels, int min, int mag, int wrapS, int wrapT) {
        if (quads == null || quads.length % 23 != 0 || faceTexels == null || !supportedSampler(min, mag, wrapS, wrapT)) return null;
        BitSet result = (BitSet)faceTexels.clone();
        for (int start = 0; start < quads.length; start += 23) {
            float lowU = Float.POSITIVE_INFINITY, lowV = lowU, highU = Float.NEGATIVE_INFINITY, highV = highU;
            for (int corner = 0; corner < 4; corner++) {
                float u = quads[start + 6 + corner*5] * 64, v = quads[start + 7 + corner*5] * 64;
                if (!Float.isFinite(u + v) || Math.abs(u) > 256 || Math.abs(v) > 256) return null;
                lowU = Math.min(lowU,u); lowV = Math.min(lowV,v); highU = Math.max(highU,u); highV = Math.max(highV,v);
            }
            int u0 = (int)Math.floor(lowU), u1 = (int)Math.floor(highU), v0 = (int)Math.floor(lowV), v1 = (int)Math.floor(highV);
            for (int v = v0; v <= v1; v++) for (int u = u0; u <= u1; u++) result.set(wrap(v,wrapT)*64 + wrap(u,wrapS));
        }
        return result;
    }
    private static int wrap(int value, int mode) { return mode == 10497 ? Math.floorMod(value,64) : Math.max(0,Math.min(63,value)); }
}
