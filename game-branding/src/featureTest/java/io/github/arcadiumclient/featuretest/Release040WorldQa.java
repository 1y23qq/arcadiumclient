// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.featuretest;

import static io.github.arcadiumclient.featuretest.Visual033Qa.*;

import io.github.arcadiumclient.featuretest.mixin.MessagingChatProbeAccess;
import io.github.arcadiumclient.featuretest.mixin.GameGuiAnimationProbeAccess;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.block.Blocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.ChatMessage;
import net.minecraft.client.gui.chat.ChatGui;
import net.minecraft.client.render.model.block.ModelTransformations;
import net.minecraft.client.resource.model.BakedModel;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Formatting;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

public final class Release040WorldQa {
    private static final String FEATURES = "io.github.arcadiumclient.runtime.client.features.";
    private static final String CROSSHAIR = "dev.microcontrollers.crosshairtweaks.config.CrosshairTweaksConfig";
    private static final Map<String,Object> RESULT = new LinkedHashMap<>();
    private static final Map<Object,Object> SAVED = new LinkedHashMap<>();
    private static final List<Map<String,Object>> CROSSHAIR_CASES = new ArrayList<>();
    private static Object animations, crosshair, items, originalDocument, originalMesh;
    private static ItemStack held;
    private static int slot, perspective, stage, ticks, crossCase, beforeCustom, beforeVanilla, vanilla;
    private static boolean hideGui, debug, begun, done;
    private static double determinant, baseline;

    private Release040WorldQa() { }

    static boolean tick(Minecraft mc, Map<String,Object> checks) throws Exception {
        if(done)return true;
        if(!begun){
            require(mc.world!=null&&mc.player!=null&&"ArcadiumTest".equals(mc.getSession().getUsername())&&"0".equals(mc.getSession().getAccessToken()),"World QA requires its offline local world");
            begun=true;checks.put("release040World",RESULT);
            Object registry=object("org.polyfrost.oneconfig.internal.ui.api.ConfigRegistry");
            animations=call(stat("org.polyfrost.oneconfig.api.config.v1.ConfigManager","active"),"get","arcadium-animations.json");
            crosshair=call(registry,"findTree","crosshairtweaks.json");
            require(animations!=null&&crosshair!=null,"Required native configuration trees were not registered");
            verifyChat(mc,registry);
            for(String name:List.of("enabled","mode","itemScaleX","itemScaleY","itemScaleZ"))save(animations,name);
            for(String name:List.of("arcadiumThirdPersonCrosshair","disableCrosshair","hideCrosshairInContainers","crosshairOpacityFirstPerson","crosshairOpacityThirdPerson"))save(crosshair,name);
            perspective=mc.options.perspective;hideGui=mc.options.hideGui;debug=mc.options.debugEnabled;
            mc.options.perspective=0;mc.options.hideGui=false;mc.options.debugEnabled=false;mc.openScreen(null);
            slot=mc.player.inventory.selectedSlot;held=mc.player.inventory.items[slot];mc.player.inventory.items[slot]=new ItemStack(Blocks.STONE);
            Object nativeConfig=stat("org.polyfrost.overflowanimations.config.OverflowAnimationsConfig","instance");
            set(animations,"mode",0);items=field(nativeConfig,"items");
            set(animations,"enabled",true);set(animations,"itemScaleX",1f);set(animations,"itemScaleY",1f);set(animations,"itemScaleZ",1f);set(animations,"mode",1);
            Object shown=call(registry,"findTree","arcadium-animations.json");
            for(String name:List.of("itemScaleX","itemScaleY","itemScaleZ"))require(call(shown,"getProp",name)!=null,"Badlion settings omitted "+name);
            originalDocument=staticField(FEATURES+"CustomCrosshair","data").get(null);
            originalMesh=staticField(FEATURES+"CustomCrosshair","cachedMesh").get(null);
            Class<?> document=originalDocument.getClass();var constructor=document.getDeclaredConstructor();constructor.setAccessible(true);
            staticField(FEATURES+"CustomCrosshair","data").set(null,constructor.newInstance());
            Object design=Class.forName(FEATURES+"CrosshairDesign").getConstructor().newInstance();
            call(object(FEATURES+"CustomCrosshair"),"update",design);
            set(crosshair,"disableCrosshair",false);set(crosshair,"crosshairOpacityFirstPerson",100f);set(crosshair,"crosshairOpacityThirdPerson",100f);
        }
        if(++ticks<12)return false;
        ticks=0;
        switch(stage){
            case 0 -> {
                require(determinant>0,"The actual first-person held block did not draw");baseline=determinant;
                RESULT.put("badlionBaseline",nativeScales());
                capture(mc,"release040-badlion-default.png");
                set(animations,"itemScaleX",.65f);set(animations,"itemScaleY",1.2f);set(animations,"itemScaleZ",1.4f);
                determinant=0;stage++;
            }
            case 1 -> {
                Object nativeConfig=stat("org.polyfrost.overflowanimations.config.OverflowAnimationsConfig","instance");
                Object selected=field(nativeConfig,"items");
                require(Math.abs(((Number)field(selected,"itemScaleX")).floatValue()-.65f)<.001,"Badlion X scale did not reach the actual renderer settings");
                require(determinant>0,"The scaled held block did not draw");
                double ratio=determinant/baseline;
                RESULT.put("badlionHeldTransform",Map.of("baselineVolume",baseline,"scaledVolume",determinant,"ratio",ratio,"expected",.65*1.2*1.4));
                RESULT.put("badlionSelected",nativeScales());
                require(Math.abs(ratio-.65*1.2*1.4)<.04||Boolean.getBoolean("arcadium.release040Diagnostics"),"The rendered block transform does not apply all three scales: "+ratio);
                capture(mc,"release040-badlion-scaled.png");set(animations,"mode",0);
                require(field(nativeConfig,"items")==items,"Switching back to Overflow did not restore its independent item settings");
                RESULT.put("overflowSettingsPreserved",true);stage++;
                selectCrosshair(mc);
            }
            case 2 -> {
                boolean custom=crossCase<4;
                boolean show=(crossCase%2)==0;
                int customDraws=count()-beforeCustom,vanillaDraws=vanilla-beforeVanilla;
                RESULT.put("crosshairDiagnostics",Map.of("case",crossCase,"custom",stat(FEATURES+"CustomCrosshair","isActive"),
                    "nativeVisible",((GameGuiAnimationProbeAccess)mc.gui).arcadium$hasCrosshair(),"nativeHidden",staticField(CROSSHAIR,"hideCrosshairInPerspective").get(null),
                    "nativeDisabled",staticField(CROSSHAIR,"disableCrosshair").get(null),"visibleOverride",stat(FEATURES+"CustomCrosshairRenderer","visible",true),
                    "debug",mc.options.debugEnabled,"hideGui",mc.options.hideGui,"screen",mc.screen==null?"none":mc.screen.getClass().getName()));
                require((custom?customDraws:vanillaDraws)>0==show,"Crosshair visibility did not follow its third-person setting: "+crossCase+" custom="+customDraws+" vanilla="+vanillaDraws);
                require(((GameGuiAnimationProbeAccess)mc.gui).arcadium$hasCrosshair()==show,"Native crosshair visibility differs from the setting");
                CROSSHAIR_CASES.add(Map.of("custom",custom,"perspective",mc.options.perspective,"show",show,"customDraws",customDraws,"vanillaDraws",vanillaDraws));
                if(show)capture(mc,"release040-crosshair-"+crossCase+".png");
                if(++crossCase<8)selectCrosshair(mc);else{
                    RESULT.put("thirdPersonCrosshair",CROSSHAIR_CASES);
                    mc.options.perspective=1;mc.options.debugEnabled=true;
                    set(crosshair,"arcadiumThirdPersonCrosshair",true);
                    call(object(FEATURES+"CustomCrosshair"),"setEnabled",true);
                    beforeCustom=count();stage++;
                }
            }
            case 3 -> {
                require(!((GameGuiAnimationProbeAccess)mc.gui).arcadium$hasCrosshair()&&count()==beforeCustom,"Showing a third-person crosshair bypassed the normal debug visibility condition");
                mc.options.debugEnabled=false;mc.options.hideGui=true;beforeCustom=count();stage++;
            }
            case 4 -> {
                require(count()==beforeCustom,"The third-person option bypassed F1 HUD hiding");
                mc.options.hideGui=false;set(crosshair,"disableCrosshair",true);stage++;
            }
            case 5 -> {
                require(!((GameGuiAnimationProbeAccess)mc.gui).arcadium$hasCrosshair()&&count()==beforeCustom,"The third-person option bypassed Disable Crosshair");
                set(crosshair,"disableCrosshair",false);set(crosshair,"hideCrosshairInContainers",true);
                mc.openScreen(new net.minecraft.client.gui.screen.GameMenuScreen());stage++;
            }
            case 6 -> {
                require(!((GameGuiAnimationProbeAccess)mc.gui).arcadium$hasCrosshair(),"The third-person option bypassed Hide Crosshair in Containers");
                mc.openScreen(null);RESULT.put("nativeDebugF1DisabledAndContainerRulesPreserved",true);
                restore(mc);stage++;
            }
            case 7 -> {
                RESULT.put("completed",true);done=true;return true;
            }
            default -> throw new IllegalStateException("Unknown world QA stage");
        }
        return false;
    }

    private static void selectCrosshair(Minecraft mc)throws Exception{
        mc.options.perspective=(crossCase/2)%2+1;
        set(crosshair,"arcadiumThirdPersonCrosshair",crossCase%2==0);
        call(object(FEATURES+"CustomCrosshair"),"setEnabled",crossCase<4);
        beforeCustom=count();beforeVanilla=vanilla;
    }
    private static int count()throws Exception{return ((Number)stat(FEATURES+"CustomCrosshairRenderer","renderedCount")).intValue();}
    private static Map<String,Object> nativeScales()throws Exception{
        Object current=field(stat("org.polyfrost.overflowanimations.config.OverflowAnimationsConfig","instance"),"items");
        return Map.of("x",field(current,"itemScaleX"),"y",field(current,"itemScaleY"),"z",field(current,"itemScaleZ"),"blocks",field(current,"applyCustomizationToBlockItems"),"mode",call(call(animations,"getProp","mode"),"get"));
    }
    public static void vanillaCrosshairDraw(){if(Boolean.getBoolean("arcadium.release040Qa")&&begun&&!done)vanilla++;}
    public static void heldDraw(ItemStack stack,BakedModel model,ModelTransformations.Type transform){
        if(!Boolean.getBoolean("arcadium.release040Qa")||!begun||done||stage>1||Minecraft.getInstance().screen!=null||transform!=ModelTransformations.Type.FIRST_PERSON)return;
        FloatBuffer matrix=BufferUtils.createFloatBuffer(16);GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX,matrix);
        double a=matrix.get(0),b=matrix.get(4),c=matrix.get(8),d=matrix.get(1),e=matrix.get(5),f=matrix.get(9),g=matrix.get(2),h=matrix.get(6),i=matrix.get(10);
        determinant=Math.abs(a*(e*i-f*h)-b*(d*i-f*g)+c*(d*h-e*g));
    }

    private static void verifyChat(Minecraft mc,Object registry)throws Exception{
        Object config=call(registry,"findTree","chattweaks.json"),filters=call(registry,"findTree","arcadium-chat-filters.json");
        String[] names={"compactChat","compactChatLimit","compactChatLines","compactChatTime","consecutiveCompactChat","hideDuplicateCount","timestamps","removeBlankMessages","dontCompactScreenshots","compactChatFormat","saveChatHistory"};
        Map<Object,Object> previous=new LinkedHashMap<>();
        for(String name:names){Object p=call(config,"getProp",name);require(p!=null,"Missing compact chat setting: "+name);previous.put(p,call(p,"get"));}
        Object filter=call(filters,"getProp","enabled");previous.put(filter,call(filter,"get"));call(filter,"setAs",false);
        ChatGui chat=mc.gui.getChat();MessagingChatProbeAccess access=(MessagingChatProbeAccess)chat;
        List<ChatMessage> raw=new ArrayList<>(access.arcadium$rawMessages());
        try{
            for(String name:List.of("timestamps","removeBlankMessages","dontCompactScreenshots","consecutiveCompactChat","hideDuplicateCount","saveChatHistory"))set(config,name,false);
            set(config,"compactChat",true);set(config,"compactChatFormat",0);set(config,"compactChatTime",1);set(config,"compactChatLines",3);
            set(config,"compactChatLimit",1);chat.clear();
            message(chat,"RANGE",1000);message(chat,"A",2000);message(chat,"B",3000);message(chat,"RANGE",60000);
            require(lines(access).contains("RANGE (2)")&&access.arcadium$rawMessages().size()==4,"Line-only compaction did not merge at its boundary independently of time");
            chat.reset();require(lines(access).contains("RANGE (2)"),"Line-limited compaction changed after chat reflow");
            chat.clear();message(chat,"OUTSIDE",1000);message(chat,"A",2000);message(chat,"B",3000);message(chat,"C",4000);message(chat,"OUTSIDE",5000);
            require(lines(access).stream().filter(v->v.equals("OUTSIDE")).count()==2,"An out-of-range chat line was collapsed");
            chat.clear();set(config,"compactChatLimit",2);message(chat,"BOTH",1000);message(chat,"A",1100);message(chat,"BOTH",2000);
            require(lines(access).contains("BOTH (2)"),"Combined time and line conditions did not merge at the time boundary");
            chat.clear();message(chat,"EXPIRED",1000);message(chat,"A",1500);message(chat,"EXPIRED",2001);
            require(lines(access).stream().filter(v->v.equals("EXPIRED")).count()==2,"Combined limits ignored time expiry");
            chat.clear();set(config,"compactChatLimit",0);message(chat,"TIME",1000);for(int i=0;i<7;i++)message(chat,"GAP"+i,1100+i);message(chat,"TIME",1900);
            require(lines(access).contains("TIME (2)"),"The existing time-only mode acquired an unwanted line limit");
            chat.clear();set(config,"compactChatLimit",1);message(chat,"WRAPPED",1000);message(chat,"wide ".repeat(160),1100);
            require(access.arcadium$visibleMessages().size()>4,"The real chat fixture did not wrap");message(chat,"WRAPPED",1200);
            require(lines(access).stream().filter(v->v.equals("WRAPPED")).count()==2,"Line range counted messages instead of actual wrapped lines");
            RESULT.put("compactChat",Map.of("lineBoundary",true,"outsideRange",true,"combinedBoundaryAndExpiry",true,"timeOnlyPreserved",true,"wrappedLines",true,"reflowAndRawHistory",true));
        }finally{
            for(var entry:previous.entrySet())call(entry.getKey(),"setAs",entry.getValue());
            access.arcadium$rawMessages().clear();access.arcadium$rawMessages().addAll(raw);access.arcadium$visibleMessages().clear();chat.reset();
        }
    }
    private static void message(ChatGui chat,String text,long time)throws Exception{Text raw=new LiteralText(text);stat("io.github.arcadiumclient.runtime.client.chat.ChatTweaks","received",raw,time);chat.addMessage(raw);}
    private static List<String> lines(MessagingChatProbeAccess access)throws Exception{
        var result=new ArrayList<String>();for(ChatMessage message:access.arcadium$visibleMessages())result.add(Formatting.strip((String)stat("io.github.arcadiumclient.runtime.client.chat.ChatTextMarks","strip",message.getText().getString())));return result;
    }
    private static void save(Object tree,String name)throws Exception{Object p=call(tree,"getProp",name);require(p!=null,"Missing setting: "+name);SAVED.put(p,call(p,"get"));}
    private static void set(Object tree,String name,Object value)throws Exception{call(call(tree,"getProp",name),"setAs",value);}
    private static void restore(Minecraft mc)throws Exception{
        for(var entry:SAVED.entrySet())call(entry.getKey(),"setAs",entry.getValue());
        staticField(FEATURES+"CustomCrosshair","data").set(null,originalDocument);staticField(FEATURES+"CustomCrosshair","cachedMesh").set(null,originalMesh);
        mc.player.inventory.items[slot]=held;mc.options.perspective=perspective;mc.options.hideGui=hideGui;mc.options.debugEnabled=debug;
    }
    private static void capture(Minecraft mc,String file){Screenshot.take(mc.gameDir,file,mc.width,mc.height,mc.getRenderTarget());}
    private static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
