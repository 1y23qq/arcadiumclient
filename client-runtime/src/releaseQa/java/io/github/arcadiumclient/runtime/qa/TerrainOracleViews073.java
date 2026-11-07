// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa;

import java.util.*;

public final class TerrainOracleViews073 {
    public static final int STEADY_VIEWS=8,GROUPS=9,MAX_AIMS=256,MAX_BATCHES=8;
    private TerrainOracleViews073(){ }
    public static boolean rapid(int index){check(index);return index==STEADY_VIEWS;}
    public static TerrainResidentSceneBlueprint.Camera source(TerrainResidentSceneBlueprint.Plan plan,int index){check(index);if(plan.cameras().size()!=STEADY_VIEWS)throw new IllegalArgumentException("Original eight cameras required");return plan.cameras().get(index==STEADY_VIEWS?0:index);}
    public static TerrainResidentSceneBlueprint.Camera capture(TerrainResidentSceneBlueprint.Plan plan,int index){var source=source(plan,index);return !rapid(index)?source:new TerrainResidentSceneBlueprint.Camera("rapid-yaw-gpu-first",source.x(),source.y(),source.z(),source.yaw()+180,source.pitch(),source.fov());}
    public static List<String> names(int index){return rapid(index)?List.of("gpu","native-a","native-b","native-c"):List.of("native-a","native-b","gpu","native-c");}
    public static boolean gpu(int index,int stage){if(stage<0||stage>3)throw new IllegalArgumentException("Unknown comparison stage");return names(index).get(stage).equals("gpu");}
    public static Set<TerrainRoofExposure073.Section> targets(TerrainResidentSceneBlueprint.Plan plan,TerrainResidentSceneBlueprint.Camera camera){
        int x=TerrainTrackingInteriorSurvey073.trackingChunk(camera.x()),z=TerrainTrackingInteriorSurvey073.trackingChunk(camera.z());
        Set<TerrainRoofExposure073.Section> result=new HashSet<>();for(var block:plan.blocks())if(Math.abs((block.x()>>4)-x)<=8&&Math.abs((block.z()>>4)-z)<=8)result.add(new TerrainRoofExposure073.Section(block.x()>>4,block.y()>>4,block.z()>>4));return Set.copyOf(result);
    }
    public static List<TerrainRoofExposure073.Aim> batch(TerrainResidentSceneBlueprint.Plan plan,TerrainResidentSceneBlueprint.Camera target,Collection<TerrainRoofExposure073.Section> unbuilt,Set<TerrainRoofExposure073.Section> completed){
        var roof=plan.cameras().get(5);if(!roof.name().equals("roof"))throw new IllegalArgumentException("Original roof anchor required");var targets=targets(plan,target);
        if(!TerrainRoofExposure073.retained(roof.x(),roof.z(),targets)||!TerrainRoofExposure073.retained(target.x(),target.z(),targets))throw new IllegalArgumentException("Preparation cannot retain all target columns");
        var ordered=new TreeSet<TerrainRoofExposure073.Section>(Comparator.comparingInt(TerrainRoofExposure073.Section::x).thenComparingInt(TerrainRoofExposure073.Section::y).thenComparingInt(TerrainRoofExposure073.Section::z));
        for(var section:unbuilt)if(targets.contains(section)){if(completed.contains(section))throw new IllegalStateException("A normally built retained target became unready again");ordered.add(section);}
        if(ordered.size()>MAX_AIMS||completed.size()+ordered.size()>MAX_AIMS)throw new IllegalStateException("Owned per-view target bound exceeded");
        return TerrainRoofExposure073.plan(roof.x(),roof.y(),roof.z(),targets,ordered.stream().limit(32).toList());
    }
    private static void check(int index){if(index<0||index>=GROUPS)throw new IllegalArgumentException("Unknown owned view");}
}
