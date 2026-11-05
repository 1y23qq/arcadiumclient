// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import org.joml.Matrix4fc;

public final class GpuVoxelSkinMath {
    public static final int QUAD_FLOATS = 23, VERTEX_WORDS = 9, MAX_QUADS = 32768;
    private GpuVoxelSkinMath() {}

    public record Options(float base, float bodyWidth, float head, float firstPerson, float skull) {
        public Options {
            for (float value : new float[]{base, bodyWidth, head, firstPerson, skull})
                if (!Float.isFinite(value) || value <= 0) throw new IllegalArgumentException("Unsupported voxel scale");
        }
    }
    public record Offset(float x, float y, float z, float scaleX, float scaleY, float scaleZ) {
        public float normalX() { return Math.abs(scaleZ / scaleX); }
        public float normalY() { return Math.abs(scaleZ / scaleY); }
        public float normalZ() { return 1; }
    }

    public static Offset offset(String shape, boolean mirrored, boolean firstPerson, boolean skull, Options options) {
        if (shape.equals("HEAD")) {
            float scale = skull ? options.skull : options.head;
            return new Offset(0, -.25f + scale * (.25f - .04f), 0, scale, scale, scale);
        }
        float scale = firstPerson ? options.firstPerson : options.base;
        float width = shape.equals("BODY") ? options.bodyWidth : scale;
        float inset = switch (shape) { case "ARM" -> .998f; case "SLIM_ARM" -> .499f; case "BODY", "LEG" -> 0; default -> throw new IllegalArgumentException("Unknown voxel shape"); };
        float y = shape.equals("ARM") || shape.equals("SLIM_ARM") ? -.1f : -.2f;
        return new Offset(width * ((mirrored ? -inset : inset) / 16f), 1.035f * (y / 16f), 0, width, 1.035f, scale);
    }

    public static void applyTemplate(float[] template, int part, Offset offset) {
        if (template.length != 192 || part < 0 || part >= 12) throw new IllegalArgumentException("Invalid voxel pose template");
        int base = part * 16;
        template[base + 8] = offset.x; template[base + 9] = offset.y; template[base + 10] = offset.z;
        template[base + 12] = offset.scaleX; template[base + 13] = offset.scaleY; template[base + 14] = offset.scaleZ;
    }

    public static boolean normalTransformMatches(Matrix4fc matrix, Offset offset) {
        float x2 = matrix.m00()*matrix.m00()+matrix.m01()*matrix.m01()+matrix.m02()*matrix.m02();
        float y2 = matrix.m10()*matrix.m10()+matrix.m11()*matrix.m11()+matrix.m12()*matrix.m12();
        float z2 = matrix.m20()*matrix.m20()+matrix.m21()*matrix.m21()+matrix.m22()*matrix.m22();
        if (!Float.isFinite(x2+y2+z2) || x2 < 1e-12f || y2 < 1e-12f || z2 < 1e-12f) return false;
        float xy=matrix.m00()*matrix.m10()+matrix.m01()*matrix.m11()+matrix.m02()*matrix.m12();
        float xz=matrix.m00()*matrix.m20()+matrix.m01()*matrix.m21()+matrix.m02()*matrix.m22();
        float yz=matrix.m10()*matrix.m20()+matrix.m11()*matrix.m21()+matrix.m12()*matrix.m22();
        if (Math.abs(xy)>Math.sqrt(x2*y2)*1e-5 || Math.abs(xz)>Math.sqrt(x2*z2)*1e-5 || Math.abs(yz)>Math.sqrt(y2*z2)*1e-5) return false;
        return Math.abs(Math.sqrt(z2/x2)-offset.normalX()) < 1e-4
                && Math.abs(Math.sqrt(z2/y2)-offset.normalY()) < 1e-4;
    }

    public static int[] vertices(float[] quads, float scale, Offset offset) {
        if (quads.length % QUAD_FLOATS != 0 || quads.length / QUAD_FLOATS > MAX_QUADS || !Float.isFinite(scale) || scale <= 0)
            throw new IllegalArgumentException("Invalid voxel quad data");
        if (!Float.isFinite(offset.normalX()) || !Float.isFinite(offset.normalY()) || offset.normalX() <= 0 || offset.normalY() <= 0)
            throw new IllegalArgumentException("Unsupported voxel normal scale");
        int[] result = new int[quads.length / QUAD_FLOATS * 4 * VERTEX_WORDS]; int output = 0;
        for (int quad = 0; quad < quads.length; quad += QUAD_FLOATS) {
            float nx = quads[quad], ny = quads[quad+1], nz = quads[quad+2];
            if (!axis(nx) || !axis(ny) || !axis(nz) || Math.abs(nx)+Math.abs(ny)+Math.abs(nz)!=1)
                throw new IllegalArgumentException("Unsupported voxel normal");
            for (int corner = 0; corner < 4; corner++) {
                int input = quad + 3 + corner * 5;
                for (int component = 0; component < 5; component++) {
                    float value = quads[input + component] * (component < 3 ? scale : 1);
                    if (!Float.isFinite(value)) throw new IllegalArgumentException("Non-finite voxel vertex");
                    result[output++] = Float.floatToRawIntBits(value);
                }
                result[output++] = Float.floatToRawIntBits(nx * offset.normalX());
                result[output++] = Float.floatToRawIntBits(ny * offset.normalY());
                result[output++] = Float.floatToRawIntBits(nz * offset.normalZ());
                result[output++] = -1;
            }
        }
        return result;
    }
    private static boolean axis(float value) { return value == -1 || value == 0 || value == 1; }
}
