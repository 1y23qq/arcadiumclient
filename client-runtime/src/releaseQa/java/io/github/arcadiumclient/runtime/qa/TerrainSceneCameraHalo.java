// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa;

import java.util.*;

public final class TerrainSceneCameraHalo {
    public record Column(int x,int z){}
    public static final int MEASURED_RADIUS=8,HALO_RADIUS=9,MAX_CAMERAS=16,MAX_COLUMNS=2048;
    private static final Comparator<Column> ORDER=Comparator.comparingInt(Column::x).thenComparingInt(Column::z);
    private TerrainSceneCameraHalo(){}
    public static List<Column> halo(List<TerrainResidentSceneBlueprint.Camera> cameras){return columns(cameras,HALO_RADIUS);}
    public static List<Column> measured(List<TerrainResidentSceneBlueprint.Camera> cameras){return columns(cameras,MEASURED_RADIUS);}
    public static int trackingChunk(double coordinate){if(!Double.isFinite(coordinate)||Math.abs(coordinate)>4096)throw new IllegalArgumentException("Owned camera lies outside the bounded halo fixture");return ((int)coordinate)>>4;}
    private static List<Column> columns(List<TerrainResidentSceneBlueprint.Camera> cameras,int radius){
        if(cameras==null||cameras.isEmpty()||cameras.size()>MAX_CAMERAS)throw new IllegalArgumentException("Unknown owned camera count");
        var columns=new TreeSet<>(ORDER);
        for(var camera:cameras){if(camera==null)throw new IllegalArgumentException("Missing owned camera");int x=trackingChunk(camera.x()),z=trackingChunk(camera.z());for(int dx=-radius;dx<=radius;dx++)for(int dz=-radius;dz<=radius;dz++)columns.add(new Column(x+dx,z+dz));if(columns.size()>MAX_COLUMNS)throw new IllegalArgumentException("Owned camera halo exceeds its preparation bound");}
        return List.copyOf(columns);
    }
}
