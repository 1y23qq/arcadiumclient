// Copyright (c) 2026 0aurorq3.
// SPDX-License-Identifier: MIT
package io.github.arcadiumclient.performance;

public final class FrustumIntersection {
    private FrustumIntersection() {}

    public static boolean contains(float[][] planes, double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        if (minX > maxX || minY > maxY || minZ > maxZ)
            return corners(planes, minX, minY, minZ, maxX, maxY, maxZ);
        for (int index = 0; index < 6; index++) {
            float[] plane = planes[index];
            double x = plane[0], y = plane[1], z = plane[2], distance = plane[3];
            if (x * minX + y * minY + z * minZ + distance > 0) continue;
            double pointX = x >= 0 ? maxX : minX;
            double pointY = y >= 0 ? maxY : minY;
            double pointZ = z >= 0 ? maxZ : minZ;
            double projection = x * pointX + y * pointY + z * pointZ + distance;
            if (projection > 0) continue;
            if (!Double.isNaN(projection) || !planeCorners(plane, minX, minY, minZ, maxX, maxY, maxZ)) return false;
        }
        return true;
    }

    private static boolean corners(float[][] planes, double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        for (int planeIndex = 0; planeIndex < 6; planeIndex++) {
            float[] plane = planes[planeIndex];
            if (!planeCorners(plane, minX, minY, minZ, maxX, maxY, maxZ)) return false;
        }
        return true;
    }

    private static boolean planeCorners(float[] plane, double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        for (int corner = 0; corner < 8; corner++) {
            double x = (corner & 1) == 0 ? minX : maxX;
            double y = (corner & 2) == 0 ? minY : maxY;
            double z = (corner & 4) == 0 ? minZ : maxZ;
            if (plane[0] * x + plane[1] * y + plane[2] * z + plane[3] > 0) return true;
        }
        return false;
    }
}
