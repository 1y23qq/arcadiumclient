// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

final class NativePartBounds {
    private NativePartBounds() {}
    static void write(float[] template, int part, boolean originalVertices, float x, float y, float z, float width, float height, float depth) {
        int at = part * 16;
        template[at + 8] = originalVertices ? 0 : x * .0625f;
        template[at + 9] = originalVertices ? 0 : y * .0625f;
        template[at + 10] = originalVertices ? 0 : z * .0625f;
        template[at + 12] = originalVertices ? 1 : width * .0625f;
        template[at + 13] = originalVertices ? 1 : height * .0625f;
        template[at + 14] = originalVertices ? 1 : depth * .0625f;
    }
}
