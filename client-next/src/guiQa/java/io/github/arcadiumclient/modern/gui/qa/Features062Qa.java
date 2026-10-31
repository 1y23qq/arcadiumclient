package io.github.arcadiumclient.modern.gui.qa;

import com.google.gson.GsonBuilder;
import io.github.arcadiumclient.modern.client.ClientPreferences;
import io.github.arcadiumclient.modern.client.debug.CustomF3Data;
import io.github.arcadiumclient.modern.client.features.CustomF3Config;
import io.github.arcadiumclient.modern.gui.*;
import io.github.arcadiumclient.modern.mixin.gui.CreateWorldAccess;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState.SelectedGameMode;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.PackCompatibility;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

public final class Features062Qa {
    private int phase,ticks,wait;
    private final Path output;
    private CompletableFuture<?> pending;
    private final Map<String,Object> report=new LinkedHashMap<>();
    public Features062Qa(Path output){this.output=output;}
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
    boolean tick(Minecraft mc) throws Exception {
        QaRuntimeErrors.check();require(++ticks<2600,"Feature QA timed out at phase "+phase);wait++;
        if(mc.gui.overlay()!=null)return false;
        if(mc.gui.screen() instanceof AccessibilityOnboardingScreen screen){screen.onClose();return false;}
        if(pending!=null){if(!pending.isDone())return false;pending.join();pending=null;}
        if(phase==0 && ticks>60 && mc.gui.screen() instanceof ArcadiumMainMenu){
            ClientPreferences.setSetupComplete(true);require(!CustomF3Config.getEnabled(),"Custom F3 default was enabled");
            var repository=mc.getResourcePackRepository();repository.reload();
            var pack=repository.getAvailablePacks().stream().filter(p->p.getId().equals("file/Compat062.zip")).findFirst().orElseThrow();
            require(pack.getCompatibility()==PackCompatibility.COMPATIBLE,"Catalog metadata was not adapted");
            repository.setSelected(List.of(pack.getId()));mc.options.resourcePacks.clear();mc.options.resourcePacks.add(pack.getId());
            pending=mc.reloadResourcePacks();phase=1;wait=0;
        }else if(phase==1){
            try(var stream=mc.getResourceManager().getResource(Identifier.parse("minecraft:textures/item/diamond_sword.png")).orElseThrow().open()){
                require(ImageIO.read(stream).getRGB(0,0)==0xFF123456,"Converted texture did not reach the resource manager");
            }
            report.put("crossVersionPackApplied",true);mc.options.pauseOnLostFocus=false;mc.options.renderDistance().set(3);
            CreateWorldScreen.openFresh(mc,()->mc.gui.setScreen(new ArcadiumMainMenu()));phase=2;wait=0;
        }else if(phase==2 && mc.gui.screen() instanceof ArcadiumCreateWorldScreen){
            var field=mc.gui.screen().getClass().getDeclaredField("delegate");field.setAccessible(true);var screen=(CreateWorldScreen)field.get(mc.gui.screen());
            var state=screen.getUiState();state.setName("Arcadium 062 QA");state.setGameMode(SelectedGameMode.CREATIVE);state.setAllowCommands(true);state.setGenerateStructures(false);
            state.setWorldType(state.getNormalPresetList().stream().filter(p->p.preset().is(WorldPresets.FLAT)).findFirst().orElseThrow());((CreateWorldAccess)screen).arcadium$create();phase=3;wait=0;
        }else if(phase==3 && mc.level!=null && mc.player!=null && wait>35){
            mc.gui.setScreen(null);CustomF3Config.setEnabled(true);CustomF3Config.setAnimations(false);CustomF3Config.setSpoofSpecs(true);CustomF3Config.setCpu("QA CPU");CustomF3Config.setGpu("QA GPU");
            mc.debugEntries.setOverlayVisible(true);phase=4;wait=0;
        }else if(phase==4 && wait>30){
            require(CustomF3Config.overrideText("fps","1234").equals("1234"),"Spec override touched FPS");
            var frame=CustomF3Data.collect(mc);String text=frame.left().toString()+frame.right();
            require(text.contains("QA CPU") && text.contains("QA GPU"),"Native debug collectors did not apply overrides");
            require(!text.contains("BetterF3 Version: null"),"Debug HUD contains an uninitialized version");
            require(frame.left().stream().filter(line->line.text().startsWith("XYZ:")).count()==1,"Vanilla coordinates are duplicated");
            var warned=CustomF3Data.class.getDeclaredField("WARNED");warned.setAccessible(true);require(((Set<?>)warned.get(null)).isEmpty(),"Some native debug modules failed");
            report.put("customF3",true);report.put("specOverridesDisplayOnly",true);
            pending=capture(mc,output.resolve("custom-f3.png"));phase=5;wait=0;
        }else if(phase==5){mc.gui.setScreen(new ArcadiumCustomF3Screen(null));phase=6;wait=0;}
        else if(phase==6 && wait>25){pending=capture(mc,output.resolve("custom-f3-editor.png"));phase=7;wait=0;}
        else if(phase==7){mc.gui.screen().onClose();phase=8;wait=0;}
        else if(phase==8 && wait>10){
            require(mc.gui.screen()==null,"Editor Escape did not return to game");report.put("editorEscape",true);
            mc.gui.setScreen(new net.minecraft.client.gui.screens.options.VideoSettingsScreen(null,mc,mc.options));phase=9;wait=0;
        }else if(phase==9 && mc.gui.screen() instanceof ArcadiumVideoSettingsScreen){
            var query=mc.gui.screen().getClass().getDeclaredField("query$delegate");query.setAccessible(true);((androidx.compose.runtime.MutableState<String>)query.get(mc.gui.screen())).setValue("mipmap");phase=10;wait=0;
        }else if(phase==10 && wait>25){pending=capture(mc,output.resolve("video-search.png"));phase=11;wait=0;}
        else if(phase==11){report.put("videoMainSearch",true);report.put("passed",true);Files.writeString(output.resolve("result.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report));mc.stop();return true;}

        return false;
    }
    private static CompletableFuture<?> capture(Minecraft mc,Path path) {
        var future=new CompletableFuture<Boolean>();
        net.minecraft.client.Screenshot.takeScreenshot(mc.gameRenderer.mainRenderTarget(),image->{
            try(image){image.writeToFile(path);future.complete(true);}catch(Exception failure){future.completeExceptionally(failure);}
        });return future;
    }
}
