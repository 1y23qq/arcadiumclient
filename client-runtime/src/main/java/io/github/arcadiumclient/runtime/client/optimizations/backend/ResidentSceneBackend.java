// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations.backend;

import java.nio.ByteBuffer;

public interface ResidentSceneBackend extends AutoCloseable {
    int COMMAND_BYTES = 24;

    record Layout(int actorCapacity, int actorStride, int vertexStride, int uniformBytes) {
        public Layout {
            if (actorCapacity < 1 || actorCapacity > 65_536 || actorStride < 4 || actorStride > 4096
                    || (actorStride & 3) != 0 || vertexStride < 4 || vertexStride > 1024 || (vertexStride & 3) != 0
                    || uniformBytes < 4 || uniformBytes > 16_384 || (uniformBytes & 3) != 0
                    || (long) actorCapacity * actorStride > 256L * 1024 * 1024) {
                throw new IllegalArgumentException("Invalid resident scene layout");
            }
        }
        public long actorBytes() { return (long) actorCapacity * actorStride; }
    }

    record Statistics(long submittedFrames, long completedFrames, long busyFrames, long drawCalls,
                      long uploadedActorBytes, long residentBytes, long gpuNanoseconds) {}

    String name();
    Layout layout();
    void putMesh(int id, ByteBuffer vertices);
    void putMaterial(int id, int width, int height, ByteBuffer rgba);
    void removeMesh(int id);
    void removeMaterial(int id);
    void writeActors(long offset, ByteBuffer bytes);
    boolean submit(int width, int height, ByteBuffer uniforms, ByteBuffer commands, int commandCount);
    void awaitFrame();
    void readRgba(ByteBuffer destination);
    Statistics statistics();
    void reset();
    @Override void close();
}
