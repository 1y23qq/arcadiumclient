// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.*;

public final class TerrainResidentSceneBlueprint {
    public enum Material { AIR,STONE,STONE_BRICKS,STONE_SLAB,DOUBLE_STONE_SLAB,STONE_STAIRS,GLASS,STAINED_GLASS,LEAVES,FENCE,GLOWSTONE,WOOL }
    public record Placement(int x,int y,int z,Material material,int metadata){}
    public record Camera(String name,double x,double y,double z,float yaw,float pitch,float fov){}
    public record Edit(String name,List<Placement> blocks){}
    public record Plan(List<Placement> blocks,List<Camera> cameras,List<Edit> edits,String sha256,int sections,int originQuadrants){}
    private record Position(int x,int y,int z){}
    private final LinkedHashMap<Position,Placement> blocks=new LinkedHashMap<>();
    private TerrainResidentSceneBlueprint(){}
    private void put(int x,int y,int z,Material material,int metadata){
        if(x< -96||x>96||z< -64||z>128||y<4||y>64||metadata<0||metadata>15)throw new IllegalArgumentException("Fixture placement outside its bounded region");
        blocks.put(new Position(x,y,z),new Placement(x,y,z,material,metadata));
        if(blocks.size()>65536)throw new IllegalArgumentException("Fixture block budget exceeded");
    }
    public static Plan create(){
        var b=new TerrainResidentSceneBlueprint();
        for(int x=-88;x<=88;x++)for(int z=-48;z<=112;z++)
            if(Math.floorMod(x+3,32)<7||Math.floorMod(z+3,32)<7)b.put(x,4,z,Material.STONE_BRICKS,Math.floorMod(x+z,4));
        for(int cx=-64;cx<=64;cx+=32)for(int cz=16;cz<=80;cz+=32){
            int height=12+8*Math.floorMod(cx/32+cz/16,4),roof=4+height;
            for(int x=cx-7;x<=cx+7;x++)for(int z=cz-7;z<=cz+7;z++){
                boolean wall=x==cx-7||x==cx+7||z==cz-7||z==cz+7;
                if(wall)for(int y=5;y<roof;y++){
                    boolean window=y%6>=2&&y%6<=4&&Math.floorMod(x+z,5)<3;
                    b.put(x,y,z,window?Material.STAINED_GLASS:Material.STONE_BRICKS,window?Math.floorMod(cx+cz,16):0);
                }
                for(int y=4;y<=roof;y+=8)b.put(x,y,z,Material.STONE,0);
                b.put(x,roof,z,Material.STONE,0);
                if(wall)b.put(x,roof+1,z,Material.STONE_SLAB,0);
            }
        }
        for(int x=-76;x<=76;x++)for(int z=-24;z<=-20;z++){
            b.put(x,10,z,Material.STONE_SLAB,0);
            if(z==-24||z==-20)b.put(x,11,z,Material.FENCE,0);
            if(Math.floorMod(x,16)==0)for(int y=5;y<10;y++)b.put(x,y,z,Material.STONE_BRICKS,0);
        }
        for(int x=-44;x<=-24;x++)for(int z=-12;z<=8;z++)b.put(x,5,z,(x&1)==0?Material.STONE:Material.DOUBLE_STONE_SLAB,0);
        for(int i=0;i<12;i++)for(int x=26;x<=31;x++)b.put(x,4+i,-8+i,Material.STONE_STAIRS,i%4);
        for(int cx=-72;cx<=72;cx+=36){
            for(int y=5;y<=11;y++)b.put(cx,y,-38,Material.FENCE,0);
            for(int x=cx-4;x<=cx+4;x++)for(int z=-42;z<=-34;z++)for(int y=10;y<=13;y++)
                if(Math.abs(x-cx)+Math.abs(z+38)<7)b.put(x,y,z,Material.LEAVES,4);
            b.put(cx,9,-38,Material.GLOWSTONE,0);
        }
        for(int x=-24;x<=24;x++)for(int y=4;y<=26;y++)b.put(x,y,0,Material.STONE_BRICKS,0);
        for(int x=44;x<=58;x++)for(int y=5;y<=11;y++)b.put(x,y,-6,Material.GLASS,0);
        for(int x=-6;x<=6;x++)b.put(x,5,40,Material.WOOL,Math.floorMod(x,16));
        var edits=new ArrayList<Edit>();var opening=new ArrayList<Placement>();var closing=new ArrayList<Placement>();
        for(int x=-4;x<=4;x++)for(int y=5;y<=16;y++){
            opening.add(new Placement(x,y,0,Material.AIR,0));closing.add(new Placement(x,y,0,Material.STONE_BRICKS,0));
        }
        edits.add(new Edit("open-occluder",List.copyOf(opening)));edits.add(new Edit("close-occluder",List.copyOf(closing)));
        edits.add(new Edit("region-boundary-replace",List.of(new Placement(-1,20,0,Material.GLASS,0),new Placement(0,20,0,Material.GLOWSTONE,0))));
        var cameras=List.of(new Camera("occluded-front",0,14,-28,0,10,75),new Camera("front-diagonal",-54,25,-36,-40,14,75),
                new Camera("reverse",0,20,100,180,15,75),new Camera("coplanar",-42,8,-10,-35,9,75),
                new Camera("bridge",-55,12,-31,-60,8,75),new Camera("roof",30,54,45,135,45,75),
                new Camera("negative-boundary",-.03125,18,-.03125,45,10,95),new Camera("rapid-reverse",0,14,-28,180,10,75));
        var ordered=new ArrayList<>(b.blocks.values());ordered.sort(Comparator.comparingInt(Placement::x).thenComparingInt(Placement::y).thenComparingInt(Placement::z));
        Set<String> sections=new HashSet<>(),regions=new HashSet<>();
        for(var p:ordered){sections.add((p.x>>4)+":"+(p.y>>4)+":"+(p.z>>4));regions.add((p.x<0?-1:0)+":"+(p.z<0?-1:0));}
        try{
            var digest=MessageDigest.getInstance("SHA-256");var packet=ByteBuffer.allocate(20);
            for(var p:ordered){packet.clear();packet.putInt(p.x).putInt(p.y).putInt(p.z).putInt(p.material.ordinal()).putInt(p.metadata);digest.update(packet.array());}
            return new Plan(List.copyOf(ordered),cameras,List.copyOf(edits),HexFormat.of().formatHex(digest.digest()),sections.size(),regions.size());
        }catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
}
