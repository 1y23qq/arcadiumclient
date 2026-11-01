// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import org.joml.Matrix4fc;

public interface NameTagSceneAccess {
    GpuNameTags arcadium$packets();
    Matrix4fc arcadium$camera();
    float arcadium$lightU();
    float arcadium$lightV();
}
