// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa;

import io.github.arcadiumclient.runtime.client.optimizations.TagGeometryCache;
import java.lang.reflect.Field;
import java.util.*;
import net.minecraft.client.render.vertex.BufferBuilder;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

public final class RetainedSceneQa {
    private record Packet(BufferBuilder pass,TagGeometryCache.Mesh mesh,int alpha,Matrix4f matrix,float u,float v) { }
    private static final ArrayDeque<Packet> packets=new ArrayDeque<>();
    private static final Matrix4f expected=new Matrix4f();
    private static Field buffer;
    private static boolean original;
    public static long checks;
    public static double error;
    public static String failure;
    private static int ticks,step=-1;
    private static final Set<Integer> tested=new HashSet<>();
    public static Set<Integer> testedOptions(){return tested;}
    public static boolean enabled(){return System.getProperty("arcadium.nextRendering.focus","").equals("scene-verify");}
    public static void queued(BufferBuilder pass,TagGeometryCache.Mesh mesh,int alpha,Matrix4fc matrix,float u,float v){packets.add(new Packet(pass,mesh,alpha,new Matrix4f(matrix),u,v));}
    public static boolean prepareOriginal(boolean reused){
        if(!enabled()||!reused)return reused;
        if(packets.isEmpty())throw new IllegalStateException("Retained plan had no packets");original=true;return false;
    }
    public static void original(Object pass,TagGeometryCache.Mesh mesh,Matrix4fc matrix,float x,float y,int alpha,float u,float v){
        if(!enabled()||!original)return;
        Packet actual=packets.poll();if(actual==null)throw new IllegalStateException("Original plan had extra packets");
        try{
            if(buffer==null){buffer=pass.getClass().getDeclaredField("buffer");buffer.setAccessible(true);}
            if(buffer.get(pass)!=actual.pass||actual.alpha!=alpha||u!=actual.u||v!=actual.v||!Arrays.equals(actual.mesh.quads(),mesh.quads()))throw new IllegalStateException("Retained name geometry, material, order, light or alpha differs");
            expected.set(matrix).translate(x,y,0);
            for(int col=0;col<4;col++)for(int row=0;row<3;row++)error=Math.max(error,Math.abs(expected.get(col,row)-actual.matrix.get(col,row)));
            if(error>.0001)throw new IllegalStateException("Retained name transform differs: "+error);
            checks++;
        }catch(Throwable problem){failure=problem.toString();throw new IllegalStateException(problem);}
    }
    public static void end(){if(!enabled()||!original)return;original=false;if(!packets.isEmpty())throw new IllegalStateException("Retained plan had extra packets");}
    public static void tick(net.minecraft.client.Minecraft mc){
        if(!enabled()||!io.github.arcadiumclient.runtime.client.features.BetaOptimizationsConfig.getRetainedNameTagScenes())return;
        int next=++ticks/20%10;if(next==step)return;step=next;tested.add(step);
        try{
            Class<?> config=Class.forName("org.polyfrost.polynametag.client.PolyNametagConfig");
            set(config,"scale",step==1?.6f:1f);set(config,"heightOffset",step==1?1f:0f);set(config,"textType",step==2?1:0);
            set(config,"background",step!=3);set(config,"paddingX",step==8?2f:0f);set(config,"rounded",step==9);
            for(var player:mc.world.players)if(player!=mc.player)player.setSneaking(step==5);
            if(step==4){Field color=config.getDeclaredField("backgroundColor");color.setAccessible(true);color.getType().getMethod("setArgb",int.class).invoke(color.get(null),0x60206080);}
            if(step==7)mc.reloadResources();
        }catch(Throwable problem){failure=problem.toString();}
    }
    private static void set(Class<?> type,String name,Object value)throws ReflectiveOperationException{Field field=type.getDeclaredField(name);field.setAccessible(true);field.set(null,value);}
}
