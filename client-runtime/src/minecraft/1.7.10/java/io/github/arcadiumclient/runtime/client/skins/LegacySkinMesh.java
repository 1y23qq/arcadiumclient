// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.client.skins;

import java.awt.image.BufferedImage;
import java.util.Arrays;

public final class LegacySkinMesh {
    public record Box(int u, int v, int width, int height, int depth, float x, float y, float z) {}
    private record Face(int u, int v, int width, int height, float[] origin, float[] across, float[] down, float[] normal) {}
    private float[] vertices = new float[2048];
    private int count;

    private LegacySkinMesh() {}

    public static float[] build(BufferedImage image, Box box) {
        LegacySkinMesh mesh = new LegacySkinMesh();
        float x = box.x(), y = box.y(), z = box.z();
        int w = box.width(), h = box.height(), d = box.depth(), u = box.u(), v = box.v();
        Face[] faces = {
            new Face(u + d + w, v + d, d, h, vec(x + w, y, z), vec(0, 0, 1), vec(0, 1, 0), vec(1, 0, 0)),
            new Face(u, v + d, d, h, vec(x, y, z + d), vec(0, 0, -1), vec(0, 1, 0), vec(-1, 0, 0)),
            new Face(u + d, v, w, d, vec(x, y, z + d), vec(1, 0, 0), vec(0, 0, -1), vec(0, -1, 0)),
            new Face(u + d + w, v, w, d, vec(x, y + h, z + d), vec(1, 0, 0), vec(0, 0, -1), vec(0, 1, 0)),
            new Face(u + d, v + d, w, h, vec(x, y, z), vec(1, 0, 0), vec(0, 1, 0), vec(0, 0, -1)),
            new Face(u + 2 * d + w, v + d, w, h, vec(x + w, y, z + d), vec(-1, 0, 0), vec(0, 1, 0), vec(0, 0, 1))
        };
        for (Face face : faces) mesh.face(image, face);
        return Arrays.copyOf(mesh.vertices, mesh.count);
    }

    private void face(BufferedImage image, Face face) {
        for (int row = 0; row < face.height(); row++) for (int column = 0; column < face.width(); column++) {
            if (!solid(image, face.u() + column, face.v() + row)) continue;
            float[] a = point(face, column, row, .52f);
            float[] b = point(face, column + 1, row, .52f);
            float[] c = point(face, column + 1, row + 1, .52f);
            float[] d = point(face, column, row + 1, .52f);
            float tu = (face.u() + column + .5f) / 64f, tv = (face.v() + row + .5f) / 64f;
            quad(a, b, c, d, face.normal(), tu, tv);
            if (column == 0 || !solid(image, face.u() + column - 1, face.v() + row)) {
                quad(point(face, column, row, .01f), a, d, point(face, column, row + 1, .01f), negate(face.across()), tu, tv);
            }
            if (column + 1 == face.width() || !solid(image, face.u() + column + 1, face.v() + row)) {
                quad(b, point(face, column + 1, row, .01f), point(face, column + 1, row + 1, .01f), c, face.across(), tu, tv);
            }
            if (row == 0 || !solid(image, face.u() + column, face.v() + row - 1)) {
                quad(a, point(face, column, row, .01f), point(face, column + 1, row, .01f), b, negate(face.down()), tu, tv);
            }
            if (row + 1 == face.height() || !solid(image, face.u() + column, face.v() + row + 1)) {
                quad(d, c, point(face, column + 1, row + 1, .01f), point(face, column, row + 1, .01f), face.down(), tu, tv);
            }
        }
    }

    private static boolean solid(BufferedImage image, int u, int v) {
        return u >= 0 && v >= 0 && u < image.getWidth() && v < image.getHeight() && (image.getRGB(u, v) >>> 24) >= 26;
    }

    private static float[] point(Face face, int u, int v, float depth) {
        return vec(face.origin()[0] + face.across()[0] * u + face.down()[0] * v + face.normal()[0] * depth,
            face.origin()[1] + face.across()[1] * u + face.down()[1] * v + face.normal()[1] * depth,
            face.origin()[2] + face.across()[2] * u + face.down()[2] * v + face.normal()[2] * depth);
    }

    private void quad(float[] a, float[] b, float[] c, float[] d, float[] normal, float u, float v) {
        float ax = b[0] - a[0], ay = b[1] - a[1], az = b[2] - a[2];
        float bx = c[0] - a[0], by = c[1] - a[1], bz = c[2] - a[2];
        boolean reverse = (ay * bz - az * by) * normal[0] + (az * bx - ax * bz) * normal[1] + (ax * by - ay * bx) * normal[2] < 0;
        vertex(a, normal, u, v);
        vertex(reverse ? d : b, normal, u, v);
        vertex(c, normal, u, v);
        vertex(reverse ? b : d, normal, u, v);
    }

    private void vertex(float[] position, float[] normal, float u, float v) {
        if (count + 8 > vertices.length) vertices = Arrays.copyOf(vertices, vertices.length * 2);
        for (float value : normal) vertices[count++] = value;
        for (float value : position) vertices[count++] = value;
        vertices[count++] = u;
        vertices[count++] = v;
    }

    private static float[] vec(float x, float y, float z) { return new float[]{x, y, z}; }
    private static float[] negate(float[] vector) { return vec(-vector[0], -vector[1], -vector[2]); }
}
