// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.features;

import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.living.player.ClientPlayerEntity;

public final class SkinDetailBudget {
    public record Candidate(UUID id,double squaredDistance) { }
    private static java.lang.ref.WeakReference<Object> world=new java.lang.ref.WeakReference<>(null);
    private static long nextRefresh;
    private static int previousLimit;
    private static Set<UUID> detailed=Set.of();
    private SkinDetailBudget() { }
    public static Set<UUID> choose(List<Candidate> candidates,int limit,Set<UUID> previous) {
        List<Candidate> ordered=new ArrayList<>(candidates);
        ordered.sort(Comparator.comparingDouble((Candidate c)->c.squaredDistance*(previous.contains(c.id)?0.8:1)).thenComparing(Candidate::id));
        Set<UUID> chosen=new HashSet<>();for(int i=0;i<Math.min(Math.max(1,limit),ordered.size());i++)chosen.add(ordered.get(i).id);
        return Set.copyOf(chosen);
    }
    public static boolean allow(Object player) {
        if(!Skin3DConfig.getEnabled())return false;
        if(!Skin3DConfig.getAdaptiveDetail())return true;
        Minecraft mc=Minecraft.getInstance();
        if(player==mc.player || player==mc.getCamera())return true;
        if(!(player instanceof ClientPlayerEntity entity) || mc.world==null || mc.getCamera()==null)return false;
        int limit=Math.max(4,Math.min(64,Skin3DConfig.getDetailedPlayers()));long now=System.nanoTime();
        if(world.get()!=mc.world || now>=nextRefresh || limit!=previousLimit) {
            if(world.get()!=mc.world){detailed=Set.of();world=new java.lang.ref.WeakReference<>(mc.world);}previousLimit=limit;nextRefresh=now+200_000_000L;
            var camera=mc.getCamera();double radius=Skin3DConfig.getDistance();radius*=radius;
            List<Candidate> nearby=new ArrayList<>();
            for(var candidate:mc.world.players) {
                double dx=candidate.x-camera.x,dy=candidate.y-camera.y,dz=candidate.z-camera.z,distance=dx*dx+dy*dy+dz*dz;
                if(distance<=radius)nearby.add(new Candidate(candidate.getUuid(),distance));
            }
            detailed=choose(nearby,limit,detailed);
        }
        return detailed.contains(entity.getUuid());
    }
}
