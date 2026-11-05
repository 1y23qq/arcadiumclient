// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.util.*;

public final class GpuVoxelSkin072InterpolationData {
    public record Sample(String name,int quadOffset,float[] quad,String meshHash,String quadHash,boolean collapsed){}
    private GpuVoxelSkin072InterpolationData(){}
    public static List<Sample> extract()throws Exception{
        var image=GpuVoxelSkin072AlphaSeed.image();int[] pixels=image.getRGB(0,0,64,64,null,0,64);
        Class<?> pixelType=Class.forName("org.polyfrost.skinlayers.mesh.SkinPixels"),meshes=Class.forName("org.polyfrost.skinlayers.skin.SkinMeshes");
        Object source=pixelType.getConstructor(int.class,int.class,int[].class).newInstance(64,64,pixels),companion=meshes.getField("Companion").get(null);
        var build=companion.getClass().getMethod("build",pixelType,boolean.class);List<Sample> output=new ArrayList<>();
        String[] parts={"Head","Body","LeftArm","RightArm","LeftLeg","RightLeg"};
        for(boolean slim:new boolean[]{false,true}){
            Object model=build.invoke(companion,source,slim);
            for(String part:parts){
                Object mesh=meshes.getMethod("get"+part).invoke(model);var field=mesh.getClass().getDeclaredField("quads");field.setAccessible(true);float[] quads=(float[])field.get(mesh);
                String hash=hash(quads);int degenerate=-1,ordinary=-1;
                for(int offset=0;offset<quads.length;offset+=23){float[] quad=Arrays.copyOfRange(quads,offset,offset+23);if(collapsed(quad)){if(degenerate<0)degenerate=offset;}else if(ordinary<0)ordinary=offset;}
                if(degenerate>=0)add(output,(slim?"alex-":"steve-")+part+"-collapsed",quads,degenerate,hash,true);
                if(!slim&&ordinary>=0&&List.of("Head","Body","LeftArm","LeftLeg").contains(part))add(output,"steve-"+part+"-ordinary",quads,ordinary,hash,false);
            }
        }
        if(output.isEmpty()||output.stream().noneMatch(Sample::collapsed))throw new IllegalStateException("Actual alpha meshes had no collapsed quad samples");
        return output;
    }
    private static void add(List<Sample> target,String name,float[] mesh,int offset,String hash,boolean collapsed)throws Exception{
        float[] quad=Arrays.copyOfRange(mesh,offset,offset+23);target.add(new Sample(name,offset,quad,hash,hash(quad),collapsed));
    }
    private static boolean collapsed(float[] quad){
        boolean same=true;for(int i=0;i<3;i++)same&=Float.floatToRawIntBits(quad[3+2*5+i])==Float.floatToRawIntBits(quad[3+3*5+i]);
        return same&&(quad[3+2*5+3]!=quad[3+3*5+3]||quad[3+2*5+4]!=quad[3+3*5+4]);
    }
    public static float[] vertices(Sample sample){
        float[] vertices=new float[20];for(int vertex=0;vertex<4;vertex++)for(int component=0;component<5;component++)
            vertices[vertex*5+component]=sample.quad[3+vertex*5+component]*(component<3?.0625f:1);return vertices;
    }
    public static double areaMeasure(Sample sample){
        float[] vertices=vertices(sample);double maximum=0;
        for(int a=0;a<2;a++)for(int b=a+1;b<3;b++)for(int c=b+1;c<4;c++){
            double ax=vertices[b*5]-vertices[a*5],ay=vertices[b*5+1]-vertices[a*5+1],az=vertices[b*5+2]-vertices[a*5+2];
            double bx=vertices[c*5]-vertices[a*5],by=vertices[c*5+1]-vertices[a*5+1],bz=vertices[c*5+2]-vertices[a*5+2];
            double x=ay*bz-az*by,y=az*bx-ax*bz,z=ax*by-ay*bx;maximum=Math.max(maximum,x*x+y*y+z*z);
        }
        return maximum;
    }
    public static String hash(float[] values)throws Exception{
        ByteBuffer bytes=ByteBuffer.allocate(values.length*4).order(ByteOrder.LITTLE_ENDIAN);for(float value:values)bytes.putInt(Float.floatToRawIntBits(value));
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.array()));
    }
}
