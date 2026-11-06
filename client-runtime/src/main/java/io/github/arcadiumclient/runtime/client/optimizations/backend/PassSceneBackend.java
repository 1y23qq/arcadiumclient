// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations.backend;

import java.nio.ByteBuffer;

public interface PassSceneBackend extends ResidentSceneBackend {
    boolean supportsPasses();
    void putPipeline(int id,String source,String vertex,String fragment,ScenePasses.Pipeline state);
    void removePipeline(int id);
    boolean submitPasses(int width,int height,ByteBuffer uniforms,ByteBuffer passes,int passCount,
                         ByteBuffer commands,int commandCount);
    void readDepth(ByteBuffer destination);
}
