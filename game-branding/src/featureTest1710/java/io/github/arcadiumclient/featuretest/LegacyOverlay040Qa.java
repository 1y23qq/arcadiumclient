// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.
package io.github.arcadiumclient.featuretest;

import static io.github.arcadiumclient.featuretest.LegacyComposeQa.*;

import io.github.arcadiumclient.featuretest.mixin.LegacyOverlayHudProbe;
import io.github.arcadiumclient.featuretest.mixin.LegacyOverlayItemProbe;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.ItemInHandRenderer;
import net.minecraft.client.render.pipeline.RenderTarget;
import net.minecraft.client.render.platform.GLX;
import net.minecraft.item.ItemStack;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

public final class LegacyOverlay040Qa {
    private static final int SIZE = 128;
    private static final String CONFIG = "dev.microcontrollers.overlaytweaks.config.OverlayTweaksConfig";
    private static boolean complete, collectHand;
    private static int handDraws;
    private static float handAlpha;

    private LegacyOverlay040Qa() { }

    static boolean tick(Minecraft client, Map<String,Object> result) throws Exception {
        if(complete)return true;
        require(client.world!=null&&client.player!=null,"Overlay QA requires its real native world");
        String[] names={"removeWaterOverlay","removeFireOverlay","customFireOverlayOpacity","fireOverlayHeight","equipableOpacity","netherPortalOpacity","handInvisibilityOpacity"};
        Map<Field,Object> saved=new LinkedHashMap<>();
        for(String name:names){Field field=Class.forName(CONFIG).getField(name);saved.put(field,field.get(null));}
        int framebuffer=GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING),matrix=GL11.glGetInteger(GL11.GL_MATRIX_MODE),program=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        boolean invisible=client.player.isInvisible();int slot=client.player.inventory.selectedSlot;ItemStack held=client.player.inventory.items[slot];
        RenderTarget target=null;
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);GL11.glPushClientAttrib(GL11.GL_CLIENT_VERTEX_ARRAY_BIT);
        GL11.glMatrixMode(GL11.GL_PROJECTION);GL11.glPushMatrix();GL11.glMatrixMode(GL11.GL_MODELVIEW);GL11.glPushMatrix();
        try{
            GL20.glUseProgram(0);target=new RenderTarget(SIZE,SIZE,true);target.bindWrite(true);
            ItemInHandRenderer hand=new ItemInHandRenderer(client);
            LegacyOverlayItemProbe effects=(LegacyOverlayItemProbe)hand;
            LegacyOverlayHudProbe hud=(LegacyOverlayHudProbe)client.gui;
            set("removeFireOverlay",false);set("fireOverlayHeight",0f);set("customFireOverlayOpacity",100f);
            byte[] background=draw(0,()->{}),fire=draw(0,()->effects.arcadium$fire(0f));
            require(changed(background,fire)>100,"Native fire overlay did not render into the real framebuffer");
            set("customFireOverlayOpacity",0f);require(changed(background,draw(0,()->effects.arcadium$fire(0f)))==0,"Zero fire opacity still drew pixels");
            set("customFireOverlayOpacity",40f);byte[] faint=draw(0,()->effects.arcadium$fire(0f));
            double ratio=distance(background,faint)/(double)distance(background,fire);
            require(ratio>.34&&ratio<.46,"Native fire opacity did not blend proportionally: "+ratio);
            set("customFireOverlayOpacity",100f);set("fireOverlayHeight",-.3f);
            require(changed(fire,draw(0,()->effects.arcadium$fire(0f)))>100,"The configured fire height did not move its actual pixels");
            require(client.player.abilities.creativeMode,"The native overlay fixture must remain creative");
            set("removeFireOverlay",true);require(changed(background,draw(0,()->effects.arcadium$fire(0f)))==0,"Fire resistance/creative removal did not suppress the overlay");
            set("removeWaterOverlay",false);byte[] water=draw(0,()->effects.arcadium$water(0f));
            require(changed(background,water)>100,"Native water overlay did not render");
            set("removeWaterOverlay",true);require(changed(background,draw(0,()->effects.arcadium$water(0f)))==0,"Remove Water Overlay still drew pixels");
            set("equipableOpacity",100f);require(changed(background,draw(1,()->hud.arcadium$pumpkin(SIZE,SIZE)))>100,"Native pumpkin overlay did not render");
            set("equipableOpacity",0f);require(changed(background,draw(1,()->hud.arcadium$pumpkin(SIZE,SIZE)))==0,"Zero pumpkin opacity still drew pixels");
            set("netherPortalOpacity",100f);require(changed(background,draw(1,()->hud.arcadium$portal(1f,SIZE,SIZE)))>100,"Native portal overlay did not render");
            set("netherPortalOpacity",0f);require(changed(background,draw(1,()->hud.arcadium$portal(1f,SIZE,SIZE)))==0,"Zero portal opacity still drew pixels");
            client.player.inventory.items[slot]=null;client.player.setInvisible(true);
            for(int i=0;i<5;i++)hand.tick();
            set("handInvisibilityOpacity",0f);collectHand=true;handDraws=0;draw(2,()->hand.renderHand(0f));
            require(handDraws==0,"An invisible hand at zero opacity still reached its model draw");
            set("handInvisibilityOpacity",40f);draw(2,()->hand.renderHand(0f));
            require(handDraws>0&&Math.abs(handAlpha-.4f)<.002f,"Native hand opacity was overwritten before the real model draw: "+handDraws+" / "+handAlpha);
            result.put("nativeOverlayPixels",Map.of("firePixels",changed(background,fire),"fire40PercentRatio",ratio,
                "waterPixels",changed(background,water),"heightAndCreativeRemoval",true,"pumpkinPortalZeroOpacity",true,
                "invisibleHandDraws",handDraws,"invisibleHandAlpha",handAlpha));
            require(GL11.glGetError()==GL11.GL_NO_ERROR,"Native overlays introduced an OpenGL error");
            complete=true;return true;
        }finally{
            collectHand=false;
            client.player.setInvisible(invisible);client.player.inventory.items[slot]=held;
            for(var entry:saved.entrySet())entry.getKey().set(null,entry.getValue());
            if(target!=null)target.destroyBuffers();GLX.bindFramebuffer(GL30.GL_FRAMEBUFFER,framebuffer);
            GL11.glMatrixMode(GL11.GL_MODELVIEW);GL11.glPopMatrix();GL11.glMatrixMode(GL11.GL_PROJECTION);GL11.glPopMatrix();
            GL11.glMatrixMode(matrix);GL11.glPopClientAttrib();GL11.glPopAttrib();GL20.glUseProgram(program);
        }
    }

    public static void handModel(){if(collectHand){FloatBuffer color=BufferUtils.createFloatBuffer(4);GL11.glGetFloat(GL11.GL_CURRENT_COLOR,color);handAlpha=color.get(3);handDraws++;}}
    private static void set(String name,Object value)throws Exception{Class.forName(CONFIG).getField(name).set(null,value);}
    private static byte[] draw(int projection,Runnable action){
        GL11.glViewport(0,0,SIZE,SIZE);GL11.glDepthMask(true);GL11.glClearColor(.1f,.25f,.7f,1f);GL11.glClear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT);
        GL11.glMatrixMode(GL11.GL_PROJECTION);GL11.glLoadIdentity();
        if(projection==0)GL11.glOrtho(-1,1,-1,1,-100,100);else if(projection==1)GL11.glOrtho(0,SIZE,SIZE,0,-1000,1000);else GL11.glFrustum(-.1,.1,-.1,.1,.1,100);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);GL11.glLoadIdentity();GL11.glColor4f(1,1,1,1);
        GL11.glEnable(GL11.GL_TEXTURE_2D);GL11.glDisable(GL11.GL_DEPTH_TEST);GL11.glDisable(GL11.GL_LIGHTING);GL11.glDisable(GL11.GL_FOG);GL11.glDisable(GL11.GL_BLEND);GL11.glDisable(GL11.GL_CULL_FACE);
        action.run();GL11.glFinish();ByteBuffer pixels=ByteBuffer.allocateDirect(SIZE*SIZE*4);GL11.glReadPixels(0,0,SIZE,SIZE,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,pixels);byte[] bytes=new byte[pixels.remaining()];pixels.get(bytes);return bytes;
    }
    private static int changed(byte[] first,byte[] second){int count=0;for(int i=0;i<first.length;i+=4)if(first[i]!=second[i]||first[i+1]!=second[i+1]||first[i+2]!=second[i+2])count++;return count;}
    private static long distance(byte[] first,byte[] second){long sum=0;for(int i=0;i<first.length;i++)if(i%4!=3)sum+=Math.abs((first[i]&255)-(second[i]&255));return sum;}
}
