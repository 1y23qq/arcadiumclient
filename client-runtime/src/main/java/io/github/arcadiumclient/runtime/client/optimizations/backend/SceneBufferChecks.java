// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations.backend;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class SceneBufferChecks {
    public static final int MAX_RESOURCE_ID = 4095;
    private SceneBufferChecks() {}

    public static ByteBuffer direct(ByteBuffer bytes) {
        if (bytes == null || !bytes.isDirect()) throw new IllegalArgumentException("A direct byte buffer is required");
        return bytes;
    }

    public static void range(long offset, long length, long capacity) {
        if (offset < 0 || length < 0 || capacity < 0 || offset > capacity || length > capacity - offset) {
            throw new IndexOutOfBoundsException("Resident buffer range is outside its allocation");
        }
    }

    public static void resourceId(int id) {
        if (id < 1 || id > MAX_RESOURCE_ID) throw new IllegalArgumentException("Resource ID must be between 1 and 4095");
    }

    public static int rgbaBytes(int width, int height) {
        if (width < 1 || height < 1 || width > 16_384 || height > 16_384) {
            throw new IllegalArgumentException("Invalid texture dimensions");
        }
        long size = (long) width * height * 4;
        if (size > 64L * 1024 * 1024) throw new IllegalArgumentException("Texture exceeds the 64 MiB limit");
        return (int) size;
    }

    public static void commands(ByteBuffer bytes, int count, int capacity) {
        direct(bytes);
        if (count < 0 || count > 65_536 || (long) count * ResidentSceneBackend.COMMAND_BYTES > bytes.remaining()) {
            throw new IllegalArgumentException("Invalid command buffer length");
        }
        ByteBuffer view = bytes.duplicate().order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0, base = view.position(); i < count; i++, base += ResidentSceneBackend.COMMAND_BYTES) {
            resourceId(view.getInt(base));
            int material = view.getInt(base + 4);
            if (material != 0) resourceId(material);
            int firstVertex = view.getInt(base + 8), vertexCount = view.getInt(base + 12);
            int firstActor = view.getInt(base + 16), actorCount = view.getInt(base + 20);
            if (firstVertex < 0 || vertexCount < 0) throw new IllegalArgumentException("Invalid vertex range");
            range(firstActor, actorCount, capacity);
        }
    }
}
