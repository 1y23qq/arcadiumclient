// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa;

import com.google.gson.GsonBuilder;
import io.github.arcadiumclient.runtime.client.ClientPreferences;
import io.github.arcadiumclient.runtime.client.features.StarHider;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.Window;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.gen.WorldGeneratorType;
import org.lwjgl.opengl.GL11;

public final class UrgentStar072Qa {
    private static int ticks,phase;
    private static boolean done,capturing;
    private static final List<String> text=new ArrayList<>();
    private UrgentStar072Qa(){}
    public static boolean enabled(){return System.getProperty("arcadium.star072.output")!=null;}
    public static void observed(String value){if(capturing)text.add(value);}
    public static void tick(){
        if(!enabled()||done)return;var mc=Minecraft.getInstance();
        try{
            require("ArcadiumTest".equals(mc.getSession().getUsername())&&"0".equals(mc.getSession().getAccessToken())&&mc.getCurrentServerEntry()==null,"Use only the owned offline Star Hider fixture");
            if(++ticks>2000)throw new IllegalStateException("Star Hider fixture did not become ready");
            if(phase==0&&ticks>60&&mc.screen!=null&&mc.world==null){ClientPreferences.setSetupComplete(true);mc.options.pauseOnUnfocus=false;mc.startGame("arcadium-star072","Star Hider072 QA",new WorldSettings(7202,WorldSettings.GameMode.CREATIVE,false,false,WorldGeneratorType.FLAT).enableCommands());phase=1;}
            if(phase==1&&mc.world!=null&&mc.player!=null&&mc.getServer()!=null){mc.openScreen(null);phase=2;}
        }catch(Throwable error){finish(Map.of("passed",false,"error",error.toString()));}
    }
    public static void frame(){
        if(!enabled()||done||phase!=2)return;var mc=Minecraft.getInstance();
        int originalLevel=mc.player.xpLevel;float originalProgress=mc.player.xpProgress;boolean oldEnabled=StarHider.getEnabled();int oldDisplay=StarHider.getDisplayedLevel();
        try{
            require(StarHider.INSTANCE.getTree().getProp("enabled")!=null&&StarHider.INSTANCE.getTree().getProp("displayedLevel")!=null,"Module settings are not registered");
            mc.player.xpLevel=37;mc.player.xpProgress=.25f;var cases=new ArrayList<Map<String,Object>>();
            StarHider.setDisplayedLevel(1000);StarHider.setEnabled(false);cases.add(draw(mc,"disabled",37));
            StarHider.setEnabled(true);cases.add(draw(mc,"custom",1000));
            StarHider.setDisplayedLevel(0);cases.add(draw(mc,"zero",0));
            StarHider.setEnabled(false);cases.add(draw(mc,"disabled-restored",37));
            var cape=UrgentCape072Qa.verify(mc);
            var nick=UrgentNick072Qa.verify(mc);
            require(GL11.glGetError()==GL11.GL_NO_ERROR,"OpenGL error during XP HUD proof");
            finish(Map.of("passed",true,"nativeGameGuiXpBar",true,"settingsRegistered",true,"actualPlayerLevelUnchanged",true,"actualProgressUnchanged",true,"cases",cases,"cape",cape,"nickHider",nick,"onlineAccountUsed",false));
        }catch(Throwable error){error.printStackTrace();finish(Map.of("passed",false,"error",error.toString()));}
        finally{capturing=false;mc.player.xpLevel=originalLevel;mc.player.xpProgress=originalProgress;StarHider.setEnabled(oldEnabled);StarHider.setDisplayedLevel(oldDisplay);}
    }
    private static Map<String,Object> draw(Minecraft mc,String label,int expected){
        text.clear();capturing=true;var window=new Window(mc);
        try{mc.gui.renderXpBar(window,window.getWidth()/2-91);}finally{capturing=false;}
        if(expected==0)require(text.stream().noneMatch(value->value.matches("[0-9]+")),"Zero Star Hider level did not hide the numeric label");
        else require(text.contains(Integer.toString(expected)),"Native XP HUD did not render the expected displayed level: "+expected);
        require(mc.player.xpLevel==37&&Float.floatToRawIntBits(mc.player.xpProgress)==Float.floatToRawIntBits(.25f),"Rendering changed actual player XP");
        return Map.of("case",label,"expected",expected,"observedText",List.copyOf(text),"actualLevel",mc.player.xpLevel,"actualProgress",mc.player.xpProgress);
    }
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
    private static void finish(Map<String,Object> result){
        if(done)return;done=true;
        try{Path path=Path.of(System.getProperty("arcadium.star072.output"));Files.createDirectories(path.toAbsolutePath().getParent());Files.writeString(path,new GsonBuilder().setPrettyPrinting().create().toJson(result));}catch(Exception error){error.printStackTrace();}if(!Boolean.getBoolean("arcadium.urgent072.keepOpen"))Minecraft.getInstance().stop();
    }
}
