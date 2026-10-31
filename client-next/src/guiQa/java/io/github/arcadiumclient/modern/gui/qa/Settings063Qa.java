package io.github.arcadiumclient.modern.gui.qa;

import com.google.gson.GsonBuilder;
import io.github.arcadiumclient.modern.client.features.CustomF3Config;
import io.github.arcadiumclient.shared.debug.DebugLayout;
import java.nio.file.*;
import java.util.*;
import org.polyfrost.oneconfig.api.ui.v1.OneConfigUI;
import org.polyfrost.oneconfig.internal.ui.api.*;
import org.polyfrost.oneconfig.internal.ui.screens.ModsKt;
import org.polyfrost.oneconfig.internal.ui.shell.LocalNavController;
import org.polyfrost.oneconfig.internal.ui.shell.ShellState;
import org.polyfrost.oneconfig.internal.ui.navigation.graph.ModConfigRoute;
import net.minecraft.client.Minecraft;

public final class Settings063Qa {
    private static int ticks,phase,wait;
    private static boolean done;
    private static Object screen;
    private static final Map<String,Object> report=new LinkedHashMap<>();
    public static boolean enabled(){return System.getProperty("arcadium.settings063.output")!=null;}
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
    public static void tick(){
        if(done)return;
        var mc=Minecraft.getInstance();
        try{
            require(++ticks<1800,"Module settings QA timed out at phase "+phase+" / "+(mc.gui.screen()==null?"none":mc.gui.screen().getClass().getSimpleName()));wait++;
            if(mc.gui.overlay()!=null)return;
            if(mc.gui.screen() instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen onboarding){onboarding.onClose();return;}
            if(phase==0 && ticks>65 && mc.gui.screen()!=null && mc.gui.screen().getClass().getSimpleName().contains("MainMenu")){
                io.github.arcadiumclient.modern.client.ClientPreferences.setSetupComplete(true);
                var tree=CustomF3Config.INSTANCE.getTree();
                for(String key:List.of("enabled","autoStart","optimizeDebugText","spaceModules","shadowText","animations","animationSpeed","fontScale","backgroundColor","hideDebugCrosshair","hideSidebar","hideBossbar","alwaysProfiler","alwaysTps","alwaysPing","spoofSpecs","cpu","gpu","gpuVendor","gpuDriver","graphicsApi","javaVersion","operatingSystem")){
                    var property=tree.getProp(key);require(property!=null && property.getMetadata("visualizer")!=null,"Setting is absent from the module GUI: "+key);
                }
                require(ModuleSettingsPanels.INSTANCE.forModule("arcadium-custom-f3.json").size()==1,"Layout panel was not registered");
                var cpu=tree.getProp("cpu");Object original=cpu.get();cpu.setAs("Module GUI CPU");require(CustomF3Config.getCpu().equals("Module GUI CPU"),"GUI property did not update the runtime setting");cpu.setAs(original);
                var layout=CustomF3Config.layout();String originalLayout=layout.encode();var coordinate=layout.left.stream().filter(m->m.kind.equals("coords")).findFirst().orElseThrow();coordinate.colors.put("colorX",0x123456);coordinate.lines.put("velocity",false);CustomF3Config.INSTANCE.storeLayout(layout);
                var restored=DebugLayout.decode(CustomF3Config.getLayoutJson());var modified=restored.left.stream().filter(m->m.kind.equals("coords")).findFirst().orElseThrow();require(modified.colors.get("colorX")==0x123456 && !modified.lines.get("velocity"),"Layout setting did not persist");CustomF3Config.INSTANCE.storeLayout(DebugLayout.decode(originalLayout));
                report.put("allGeneralAndSpecProperties",23);report.put("layoutPersistence",true);OneConfigUI.open();phase=1;wait=0;
            }else if(phase==1 && wait>30 && LocalNavController.INSTANCE.isReady()){
                var card=ConfigRegistry.INSTANCE.findById("arcadium-custom-f3.json");require(card!=null && card.getOnOpen()==null,"Module card bypasses the client settings");ModsKt.openModCard(card);phase=2;wait=0;
            }else if(phase==2 && wait>25){
                require(LocalNavController.INSTANCE.getWrapper().getCurrentRoute() instanceof ModConfigRoute route && route.getId().equals("arcadium-custom-f3.json"),"Module card did not open its settings page");screen=mc.gui.screen();require(screen.getClass().getSimpleName().equals("OneConfigUIScreen"),"Settings opened a separate Minecraft screen");net.minecraft.client.Screenshot.takeScreenshot(mc.gameRenderer.mainRenderTarget(),image->{try(image){image.writeToFile(Path.of(System.getProperty("arcadium.settings063.output")).getParent().resolve("module-settings.png"));}catch(Exception e){throw new RuntimeException(e);}});
                ModuleSettingsPanels.INSTANCE.selectPanel("arcadium-custom-f3.json","Layout");phase=3;wait=0;
            }else if(phase==3 && wait>25){
                require(mc.gui.screen()==screen,"Layout replaced the client GUI");require(ModuleSettingsPanels.INSTANCE.selectedTitle("arcadium-custom-f3.json").equals("Layout"),"Layout tab was not selected");net.minecraft.client.Screenshot.takeScreenshot(mc.gameRenderer.mainRenderTarget(),image->{try(image){image.writeToFile(Path.of(System.getProperty("arcadium.settings063.output")).getParent().resolve("module-layout.png"));}catch(Exception e){throw new RuntimeException(e);}});
                ModuleSettingsPanels.INSTANCE.selectPanel("arcadium-custom-f3.json",null);ShellState.INSTANCE.getSelectedCategories().put("arcadium-custom-f3.json","Spec Spoof");phase=4;wait=0;
            }else if(phase==4 && wait>25){
                require(mc.gui.screen()==screen,"Spec settings replaced the client GUI");net.minecraft.client.Screenshot.takeScreenshot(mc.gameRenderer.mainRenderTarget(),image->{try(image){image.writeToFile(Path.of(System.getProperty("arcadium.settings063.output")).getParent().resolve("module-specs.png"));}catch(Exception e){throw new RuntimeException(e);}});
                CustomF3Config.INSTANCE.edit();phase=5;wait=0;
            }else if(phase==5 && wait>15){require(mc.gui.screen()==screen,"F3 shortcut left the module GUI");report.put("moduleCardEntry",true);report.put("layoutAndSpecsInClientGui",true);finish(null);}
        }catch(Throwable error){finish(error);}
    }
    private static void finish(Throwable error){done=true;report.put("passed",error==null);if(error!=null){report.put("error",error.toString());error.printStackTrace();}try{Path file=Path.of(System.getProperty("arcadium.settings063.output"));Files.createDirectories(file.getParent());Files.writeString(file,new GsonBuilder().setPrettyPrinting().create().toJson(report));}catch(Exception failure){failure.printStackTrace();}Minecraft.getInstance().stop();}
}
