// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

final class GpuResidentBodyMesh {
    static final int BONES=12,VERTICES_PER_BONE=24,VERTEX_WORDS=12;
    static final int WORDS_PER_BONE=VERTICES_PER_BONE*VERTEX_WORDS;
    private GpuResidentBodyMesh() { }
    static FloatBuffer build(FloatBuffer normal,FloatBuffer mirrored,boolean[] mirror) {
        if(normal==null||mirrored==null||normal.remaining()!=WORDS_PER_BONE||mirrored.remaining()!=WORDS_PER_BONE
                ||mirror==null||mirror.length!=BONES)throw new IllegalArgumentException("Use the verified native box vertex layout and twelve bone selectors");
        FloatBuffer output=ByteBuffer.allocateDirect(BONES*WORDS_PER_BONE*4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        for(int bone=0;bone<BONES;bone++) {
            FloatBuffer source=mirror[bone]?mirrored:normal;
            int start=source.position();
            for(int vertex=0;vertex<VERTICES_PER_BONE;vertex++)for(int word=0;word<VERTEX_WORDS;word++) {
                output.put(word==3?bone:source.get(start+vertex*VERTEX_WORDS+word));
            }
        }
        return output.flip().asReadOnlyBuffer();
    }
}
