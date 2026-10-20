// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.
package io.github.arcadiumclient.featuretest;

import static io.github.arcadiumclient.featuretest.LegacyComposeQa.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.platform.GLX;
import org.lwjgl.opengl.GL11;

final class LegacyBlur040Qa {
    private static final String BASE="org.polyfrost.polyblur.client.";
    private static final List<Map<String,Object>> CHECKS=new ArrayList<>();
    private static Object config;
    private static boolean enabled,blurHand,complete;
    private static int type,scenario,ticks;
    private static long frame;

    private LegacyBlur040Qa() { }

    static boolean tick(Minecraft client,Map<String,Object> result)throws Exception{
        if(complete)return true;
        require(client.world!=null&&client.player!=null&&GLX.usePostProcess,"Blur QA requires native world post-processing support");
        if(config==null){
            config=instance(BASE+"PolyBlurConfig");
            enabled=(boolean)call(config,"isEnabled");blurHand=(boolean)call(config,"getBlurHand");type=((Number)call(config,"getBlurType")).intValue();
            result.put("nativeBlurModes",CHECKS);
            call(config,"setEnabled",true);select();
        }
        if(++ticks<20)return false;
        try{
            long current=((Number)call(instance(BASE+"blur.FrameClock"),"getFrame")).longValue();
            require(current>frame+5,"The native frame-clock injection did not execute while testing blur");
            int mode=scenario/2;
            Map<String,Object> check=new LinkedHashMap<>();
            check.put("mode",List.of("Phosphor","Unity","Hybrid").get(mode));check.put("blurHand",scenario%2!=0);check.put("renderedFrames",current-frame);
            if(mode==0)check.put("phosphorPasses",passes("phosphor.PhosphorBlur",2));
            if(mode==1)check.put("unityPasses",passes("motion.MotionBlur",2));
            if(mode==2){check.put("unityPasses",passes("motion.MotionBlur",2));check.put("handPasses",passes("phosphor.HybridHandPhosphor",4));}
            int error=GL11.glGetError();require(error==GL11.GL_NO_ERROR,"Native blur left an OpenGL error: "+error);
            check.put("openGlError",error);CHECKS.add(check);
            if(++scenario<6){select();return false;}
            restore();complete=true;return true;
        }catch(Throwable failure){restore();throw failure;}
    }

    private static int passes(String name,int minimum)throws Exception{
        Object chain=field(Class.forName(BASE+"blur."+name),"postChain");
        require(chain!=null,"The native "+name+" shader pipeline failed to initialize");
        int count=((Collection<?>)call(chain,"getPasses")).size();
        require(count>=minimum,"The native "+name+" shader pipeline is incomplete: "+count);
        return count;
    }
    private static void select()throws Exception{
        call(config,"setBlurType",scenario/2);call(config,"setBlurHand",scenario%2!=0);
        frame=((Number)call(instance(BASE+"blur.FrameClock"),"getFrame")).longValue();ticks=0;
        int attempts=16;
        while(attempts>0&&GL11.glGetError()!=GL11.GL_NO_ERROR)attempts--;
        require(attempts>0,"The native OpenGL context retained an error before blur testing");
    }
    private static void restore()throws Exception{call(config,"setBlurType",type);call(config,"setBlurHand",blurHand);call(config,"setEnabled",enabled);}
}
