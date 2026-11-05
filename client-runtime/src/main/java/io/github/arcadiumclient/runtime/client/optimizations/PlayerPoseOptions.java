// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import java.lang.reflect.*;

public final class PlayerPoseOptions {
    public static final int OLD_BLOCKING = 1, OLD_SNEAK = 2, WAVY_ARMS = 4;
    private static boolean checked,compatible=true;
    private static boolean invertedPlayers;
    private static Object config;
    private static Method instance,oldSneak;
    private static Field movement,other,blocking,wavy,dinnerbone,dinnerboneEntities;
    private static int flags;
    private PlayerPoseOptions(){}
    public static void refresh(){
        if(!checked){
            checked=true;
            try{
                Class<?> type=Class.forName("org.polyfrost.overflowanimations.config.OverflowAnimationsConfig");
                instance=type.getMethod("instance");config=instance.invoke(null);movement=type.getField("movement");other=type.getField("other");
                var move=movement.get(config);var misc=other.get(config);
                blocking=misc.getClass().getField("thirdPersonSwordBlockingPosition");wavy=move.getClass().getField("wavyArms");
                dinnerbone=move.getClass().getField("dinnerboneMode");dinnerboneEntities=move.getClass().getField("dinnerboneModeEntities");
                try{oldSneak=Class.forName("org.polyfrost.overflowanimations.legacy.model.LegacyEyeHeight").getMethod("isOldSneakModel");}catch(ClassNotFoundException|NoSuchMethodException ignored){}
            }catch(ClassNotFoundException ignored){}catch(ReflectiveOperationException error){compatible=false;instance=null;}
        }
        flags=0;invertedPlayers=false;
        if(instance==null)return;
        try{
            config=instance.invoke(null);Object move=movement.get(config),misc=other.get(config);
            compatible=true;
            if(blocking.getBoolean(misc))flags|=OLD_BLOCKING;
            if(oldSneak!=null&&(boolean)oldSneak.invoke(null))flags|=OLD_SNEAK;
            if(wavy.getBoolean(move))flags|=WAVY_ARMS;
            invertedPlayers=dinnerbone.getBoolean(move)||dinnerboneEntities.getBoolean(move);
        }catch(ReflectiveOperationException error){compatible=false;}
    }
    public static int flags(){return flags;}
    public static boolean compatible(){return compatible;}
    public static boolean upsideDown(String name,boolean capePartVisible){
        return invertedPlayers||nativeUpsideDown(name,capePartVisible);
    }
    static boolean nativeUpsideDown(String name,boolean capePartVisible){
        if(!capePartVisible||name==null)return false;
        if(name.indexOf('\u00a7')>=0)name=name.replaceAll("(?i)\\u00a7[0-9A-FK-OR]","");
        return name.equals("Dinnerbone")||name.equals("Grumm");
    }
}
