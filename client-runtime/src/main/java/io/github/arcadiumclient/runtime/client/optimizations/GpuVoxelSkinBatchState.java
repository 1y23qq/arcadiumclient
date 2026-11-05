// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

final class GpuVoxelSkinBatchState {
    private long epoch=-1;
    private boolean active;
    private int pending;
    void prepare(long value){epoch=value;active=true;pending=0;}
    boolean accepts(long value){return active&&epoch==value;}
    void queued(long value){if(!accepts(value))throw new IllegalStateException("Unprepared voxel batch");pending++;}
    int claim(long value){if(!accepts(value))return -1;active=false;int count=pending;pending=0;return count;}
    void discard(){active=false;pending=0;}
}
