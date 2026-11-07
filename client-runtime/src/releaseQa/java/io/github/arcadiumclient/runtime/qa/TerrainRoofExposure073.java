// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa;

import java.util.*;

public final class TerrainRoofExposure073 {
    public record Section(int x,int y,int z) { }
    public record Aim(Section target,float yaw,float pitch) { }
    private TerrainRoofExposure073() { }
    public static boolean retained(double x,double z,Collection<Section> targets) {
        int cx=((int)x)>>4,cz=((int)z)>>4;
        return targets.stream().allMatch(s->Math.abs(s.x-cx)<=8&&Math.abs(s.z-cz)<=8);
    }
    public static List<Aim> plan(double x,double y,double z,Collection<Section> finalTargets,Collection<Section> unbuilt) {
        if(!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z)||Math.abs(x)>4096||Math.abs(z)>4096||y<0||y>256)throw new IllegalArgumentException("Unknown owned roof anchor");
        if(!retained(x,z,finalTargets))throw new IllegalArgumentException("Roof does not retain every final target column");
        var selected=new TreeSet<Section>(Comparator.comparingInt(Section::x).thenComparingInt(Section::y).thenComparingInt(Section::z));
        for(var section:unbuilt)if(finalTargets.contains(section))selected.add(section);
        if(selected.size()>32)throw new IllegalStateException("Native exposure survey exceeds32 targets");
        var result=new ArrayList<Aim>();
        for(var section:selected){double dx=section.x*16+8-x,dy=section.y*16+8-y,dz=section.z*16+8-z;
            result.add(new Aim(section,(float)Math.toDegrees(Math.atan2(-dx,dz)),(float)Math.toDegrees(Math.atan2(-dy,Math.hypot(dx,dz)))));}
        return List.copyOf(result);
    }
}
