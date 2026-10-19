// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.client.features;

import java.util.HashSet;

public final class BundledSkinMeshProbe {
    public static void main(String[] args) throws Exception {
        Class<?> configClass = Class.forName("org.polyfrost.skinlayers.config.SkinLayersConfig");
        Object config = configClass.getConstructor().newInstance();
        String[] parts = {"enableHat", "enableJacket", "enableLeftSleeve", "enableRightSleeve", "enableLeftPants", "enableRightPants"};
        for (String part : parts) {
            SkinLayersBridge.write(config, part, false);
            if (!Boolean.FALSE.equals(SkinLayersBridge.read(config, part))) throw new AssertionError(part + " did not disable");
            SkinLayersBridge.write(config, part, true);
            if (!Boolean.TRUE.equals(SkinLayersBridge.read(config, part))) throw new AssertionError(part + " did not enable");
        }
        Class<?> pixelsClass = Class.forName("org.polyfrost.skinlayers.mesh.SkinPixels");
        Class<?> boxClass = Class.forName("org.polyfrost.skinlayers.mesh.BoxLayout");
        Class<?> mesherClass = Class.forName("org.polyfrost.skinlayers.mesh.LayerMesher");
        int[] pixels = new int[64 * 64];
        pixels[11 * 64 + 43] = 0xFFFFFFFF;
        Object skin = pixelsClass.getConstructor(int.class, int.class, int[].class).newInstance(64, 64, pixels);
        Object box = boxClass.getConstructor(int.class, int.class, int.class, int.class, int.class).newInstance(8, 8, 8, 32, 0);
        Object mesher = mesherClass.getConstructor(pixelsClass, boxClass, boolean.class).newInstance(skin, box, false);
        float[] mesh = (float[]) mesherClass.getMethod("build", boolean.class, float.class).invoke(mesher, false, 0f);
        if (mesh == null || mesh.length % 23 != 0 || mesh.length < 5 * 23) throw new AssertionError("A single outer pixel is not a voxel");
        HashSet<String> normals = new HashSet<>();
        float[] min = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE};
        float[] max = {-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
        for (int quad = 0; quad < mesh.length; quad += 23) {
            normals.add(mesh[quad] + "," + mesh[quad + 1] + "," + mesh[quad + 2]);
            for (int vertex = 0; vertex < 4; vertex++) for (int axis = 0; axis < 3; axis++) {
                float value = mesh[quad + 3 + vertex * 5 + axis];
                min[axis] = Math.min(min[axis], value);
                max[axis] = Math.max(max[axis], value);
            }
        }
        if (normals.size() < 5 || max[0] - min[0] <= 0.1f || max[1] - min[1] <= 0.1f || max[2] - min[2] <= 0.1f)
            throw new AssertionError("The outer pixel does not have side faces and measurable depth");
        System.out.println("Bundled 3D skin probe passed: six body-part switches, " + mesh.length / 23 +
                " voxel faces for one pixel, depth=" + (max[2] - min[2]));
    }
}
