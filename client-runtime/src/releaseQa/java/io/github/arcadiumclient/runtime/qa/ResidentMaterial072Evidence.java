// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa;

import java.util.Map;
import net.minecraft.client.Minecraft;

public final class ResidentMaterial072Evidence {
    private static int depth;
    private static long calls,positive,managerReads,textureGetters,generationGetters;
    private static Throwable failure;
    private ResidentMaterial072Evidence(){}
    public static boolean enabled(){return PlayerPipeline072Qa.enabled()&&Boolean.getBoolean("arcadium.residentBodies072.materialProof");}
    public static void begin(){
        if(!enabled())return;
        try{Minecraft mc=Minecraft.getInstance();require(mc.getSession().getUsername().equals("ArcadiumTest")&&mc.getSession().getAccessToken().equals("0"),"Use the owned fake material fixture");require(depth==0,"Material validation unexpectedly nested");depth++;calls++;}catch(Throwable error){failure=error;}
    }
    public static void end(Object fact){if(!enabled())return;try{require(depth==1,"Material validation did not preserve its scope");if(fact!=null)positive++;depth--;}catch(Throwable error){failure=error;}}
    public static void managerRead(){if(enabled()&&depth>0)managerReads++;}
    public static void textureGetter(){if(enabled()&&depth>0)textureGetters++;}
    public static void generationGetter(){if(enabled()&&depth>0)generationGetters++;}
    public static Map<String,Object> verify(){
        require(failure==null,"Material source trace failed: "+failure);require(depth==0&&calls>0&&positive>0,"The actual material validation did not activate");boolean selected=Boolean.getBoolean("arcadium.residentBodies072.selectedMaterial");
        if(selected)require(managerReads==0&&textureGetters==0&&generationGetters==0,"Selected native materials repeated source lookup/getter calls");
        else require(managerReads>=calls&&textureGetters>0&&generationGetters>0,"The original material source path was not observed");
        return Map.of("passed",true,"selectedMaterial",selected,"actualValidationCalls",calls,"positiveMaterials",positive,"managerReadsInsideValidation",managerReads,"textureGettersInsideValidation",textureGetters,"generationGettersInsideValidation",generationGetters,"correctnessOnly",true);
    }
    private static void require(boolean condition,String message){if(!condition)throw new IllegalStateException(message);}
}
