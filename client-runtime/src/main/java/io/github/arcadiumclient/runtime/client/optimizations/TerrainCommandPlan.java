// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import java.util.Arrays;

public final class TerrainCommandPlan {
    public static final int SECTIONS = 256, FACES = 7, WORDS = 20, COMMAND_WORDS = 5;
    public static final int MAX_COMMANDS = SECTIONS * FACES;
    private TerrainCommandPlan() {}

    public static int visibleFaces(int cameraX, int cameraY, int cameraZ, int x, int y, int z) {
        int mask = 64;
        if (cameraX > x - 3) mask |= 1;
        if (cameraY > y - 3) mask |= 2;
        if (cameraZ > z - 3) mask |= 4;
        if (cameraX < x + 19) mask |= 8;
        if (cameraY < y + 19) mask |= 16;
        if (cameraZ < z + 19) mask |= 32;
        return mask;
    }

    public static int cameraKey(int cameraX, int cameraY, int cameraZ, int x, int y, int z) {
        return axisKey(cameraX, x, 8) | axisKey(cameraY, y, 4) << 8 | axisKey(cameraZ, z, 8) << 16;
    }

    private static int axisKey(int camera, int origin, int sections) {
        int positive = Math.clamp(Math.floorDiv(camera - origin + 2, 16), -1, sections) + 1;
        int negative = Math.clamp(Math.floorDiv(camera - origin - 19, 16), -1, sections) + 1;
        return positive | negative << 4;
    }

    public static int[] reference(int[] mesh, byte[] sections, int count, int cameraX, int cameraY, int cameraZ,
                                  boolean faceCulling, int verticesPerPrimitive, int elementsPerPrimitive) {
        if (mesh.length != SECTIONS * WORDS || count < 0 || count > SECTIONS || count > sections.length
                || verticesPerPrimitive < 1 || elementsPerPrimitive < 1) throw new IllegalArgumentException("Invalid terrain scene");
        int[] commands = new int[MAX_COMMANDS * COMMAND_WORDS];
        int commandCount = 0;
        for (int index = 0; index < count; index++) {
            int row = (sections[index] & 255) * WORDS;
            int mask = faceCulling ? visibleFaces(cameraX, cameraY, cameraZ, mesh[row], mesh[row + 1], mesh[row + 2]) : 127;
            int previousEnd = Integer.MIN_VALUE;
            for (int face = 0; face < FACES; face++) {
                int elements = mesh[row + 12 + face];
                if (elements <= 0 || (mask & 1 << face) == 0) continue;
                int vertex = mesh[row + 4 + face];
                if (previousEnd == vertex) commands[(commandCount - 1) * COMMAND_WORDS] += elements;
                else {
                    int output = commandCount++ * COMMAND_WORDS;
                    commands[output] = elements;
                    commands[output + 1] = 1;
                    commands[output + 3] = vertex;
                }
                previousEnd = vertex + elements / elementsPerPrimitive * verticesPerPrimitive;
            }
        }
        return Arrays.copyOf(commands, commandCount * COMMAND_WORDS);
    }
}
