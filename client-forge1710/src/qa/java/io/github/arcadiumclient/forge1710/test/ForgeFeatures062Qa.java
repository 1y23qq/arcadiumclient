package io.github.arcadiumclient.forge1710.test;

import com.google.gson.GsonBuilder;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.ResourcePackRepository;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;

public final class ForgeFeatures062Qa {
    private int phase,ticks,wait;
    private boolean done;
    private final Map<String,Object> report=new LinkedHashMap<>();
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
    private static Class<?> config() throws Exception{return Class.forName("io.github.arcadiumclient.runtime.client.features.CustomF3Config");}
    private static void set(String key,Class<?> type,Object value) throws Exception{config().getMethod("set"+key,type).invoke(null,value);}
    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event){
        if(done || event.phase!=TickEvent.Phase.END)return;
        Minecraft mc=Minecraft.func_71410_x();
        try{
            require(++ticks<2600,"Forge feature QA timed out at phase "+phase);wait++;
            if(phase==0 && ticks>90 && mc.field_71462_r!=null){
                require(!(boolean)config().getMethod("getEnabled").invoke(null),"Custom F3 default was enabled");
                org.polyfrost.oneconfig.internal.OneConfigConfig.limitUnfocusedFps=false;mc.field_71474_y.field_82881_y=false;
                var repository=mc.func_110438_M();repository.func_110611_a();
                Object selected=null;for(Object value:repository.func_110609_b())if(value.getClass().getMethod("func_110515_d").invoke(value).equals("Compat062.zip")){selected=value;break;}
                require(selected!=null,"Cross-version pack was not discovered");repository.func_148527_a(List.of(selected));mc.func_110436_a();
                try(var stream=mc.func_110442_L().func_110536_a(new ResourceLocation("minecraft","textures/items/diamond_sword.png")).func_110527_b()) {require(ImageIO.read(stream).getRGB(0,0)==0xFF123456,"Converted texture did not reach the Forge resource manager");}
                report.put("crossVersionPackApplied",true);
                mc.func_71371_a("arcadium-features062","Arcadium 062 QA",new WorldSettings(62,WorldSettings.func_77161_a(1),false,false,WorldType.field_77138_c).func_77166_b());phase=1;wait=0;
            }else if(phase==1 && mc.field_71441_e!=null && mc.field_71439_g!=null && wait>40){
                mc.func_147108_a(null);set("Enabled",boolean.class,true);set("Animations",boolean.class,false);set("SpoofSpecs",boolean.class,true);set("Cpu",String.class,"QA CPU");set("Gpu",String.class,"QA GPU");mc.field_71474_y.field_74330_P=true;phase=2;wait=0;
            }else if(phase==2 && wait>30){
                Class<?> data=Class.forName("io.github.arcadiumclient.runtime.client.debug.CustomF3Data");
                var method=Arrays.stream(data.getMethods()).filter(m->m.getName().equals("collect")).findFirst().orElseThrow();
                String frame=method.invoke(null,mc,null).toString();require(frame.contains("QA CPU") && frame.contains("QA GPU"),"Forge debug data was not customized");
                require(config().getMethod("overrideText",String.class,String.class).invoke(null,"fps","1234").equals("1234"),"Spec overrides changed FPS");
                report.put("customF3",true);report.put("specOverridesDisplayOnly",true);ForgeRuntimeQa.capture(mc,"custom-f3-062",ticks);
                Class<?> editor=Class.forName("io.github.arcadiumclient.runtime.client.gui.ArcadiumCustomF3Screen");mc.func_147108_a((GuiScreen)editor.getConstructor(GuiScreen.class).newInstance((Object)null));phase=3;wait=0;
            }else if(phase==3 && wait>25){
                ForgeRuntimeQa.capture(mc,"custom-f3-editor-062",ticks);
                var method=mc.field_71462_r.getClass().getDeclaredMethod("handleKeyPressed",int.class,int.class);method.setAccessible(true);require((boolean)method.invoke(mc.field_71462_r,256,0),"Editor Escape was not handled");phase=4;wait=0;
            }else if(phase==4 && wait>10){require(mc.field_71462_r==null,"Editor did not return to the game");report.put("editorEscape",true);finish(mc,null);}
        }catch(Throwable failure){finish(mc,failure);}
    }
    private void finish(Minecraft mc,Throwable failure){done=true;report.put("passed",failure==null);if(failure!=null){report.put("error",failure.toString());failure.printStackTrace();}
        try{Path file=Path.of(System.getProperty("arcadium.qa.features062.output"));Files.createDirectories(file.getParent());Files.writeString(file,new GsonBuilder().setPrettyPrinting().create().toJson(report));}catch(Exception error){error.printStackTrace();}mc.func_71400_g();}
}
