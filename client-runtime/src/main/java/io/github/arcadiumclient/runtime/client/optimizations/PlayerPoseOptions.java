// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import java.lang.reflect.*;

public final class PlayerPoseOptions {
    private static boolean checked,compatible=true;
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
            }catch(ClassNotFoundException ignored){}catch(ReflectiveOperationException error){compatible=false;}
        }
        flags=0;
        if(instance==null)return;
        try{
            config=instance.invoke(null);Object move=movement.get(config),misc=other.get(config);
            compatible=!wavy.getBoolean(move)&&!dinnerbone.getBoolean(move)&&!dinnerboneEntities.getBoolean(move);
            if(blocking.getBoolean(misc))flags|=1;
            if(oldSneak!=null&&(boolean)oldSneak.invoke(null))flags|=2;
        }catch(ReflectiveOperationException error){compatible=false;}
    }
    public static int flags(){return flags;}
    public static boolean compatible(){return compatible;}
}
