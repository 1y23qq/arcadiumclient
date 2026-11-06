// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

final class TerrainResidentCameraContract {
    private static final double LIMIT=1<<27;
    private static final double SPLIT_ERROR=2.0*Math.ulp(128.0f);
    private TerrainResidentCameraContract(){}
    static boolean axis(double position,int integer,float fraction){
        if(!Double.isFinite(position)||Math.abs(position)>LIMIT||integer!=(int)position||!Float.isFinite(fraction)||Math.abs(fraction)>1)return false;
        return Math.abs(position-integer-fraction)<=SPLIT_ERROR;
    }
}
