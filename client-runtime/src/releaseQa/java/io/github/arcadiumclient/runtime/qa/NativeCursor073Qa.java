// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa;

import com.google.gson.GsonBuilder;
import io.github.arcadiumclient.runtime.client.ClientPreferences;
import java.lang.reflect.Field;
import java.nio.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.menu.GameMenuScreen;
import net.minecraft.client.gui.screen.menu.options.OptionsScreen;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.gen.WorldGeneratorType;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;
import org.lwjgl.sdl.SDLMouse;
import org.lwjgl.sdl.SDLVideo;
import org.lwjgl.system.*;
import org.lwjgl.system.windows.User32;
import pl.tomgirl.pylon.window.DisplaySdl;

public final class NativeCursor073Qa {
    private record State(String name,boolean ui,boolean fullscreen,int fps,long minimumMillis,boolean rootBlankClick){ }
    private static final List<State> STATES=List.of(
            new State("gameplay-normal",false,true,120,1200,false),
            new State("pause-normal",true,true,120,1000,false),
            new State("pause-lowfps-root-motion",true,true,5,3000,true),
            new State("options-normal",true,true,120,1000,false),
            new State("options-lowfps",true,true,5,2000,false),
            new State("options-resource-reloaded",true,true,120,1000,false),
            new State("options-windowed",true,false,120,1000,false),
            new State("options-fullscreen-restored",true,true,120,1000,false),
            new State("gameplay-grab-restored",false,true,120,1200,false));
    private static final List<Map<String,Object>> states=new ArrayList<>(),inputEvents=new ArrayList<>();
    private static final Map<String,Object> report=new LinkedHashMap<>();
    private static int phase,ticks,index,matchedFrames;
    private static long frames,stageFrame,started,lastStatus;
    private static boolean done,blankClicked,oldSoftwareField,oldVsync;
    private static int oldFps;
    private static Screen expectedScreen;
    private static Map<String,Object> first,last,blankClick;
    private static long osCursorInfo;
    private NativeCursor073Qa(){ }
    public static boolean enabled(){return System.getProperty("arcadium.nativeCursor073.output")!=null;}
    private static boolean baseline(){return System.getProperty("arcadium.nativeCursor073.mode").equals("baseline");}
    private static Path output(){return Path.of(System.getProperty("arcadium.nativeCursor073.output"));}
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
    private static void owned(){var mc=Minecraft.getInstance();require("ArcadiumTest".equals(mc.getSession().getUsername())&&"0".equals(mc.getSession().getAccessToken())&&mc.getCurrentServerEntry()==null,"Only fake offline identity/no external server allowed");require(Set.of("baseline","candidate").contains(System.getProperty("arcadium.nativeCursor073.mode")),"Explicit cursor mode required");}
    public static void tick(){
        if(!enabled()||done)return;var mc=Minecraft.getInstance();
        try{owned();require(++ticks<6000,"Cursor fixture exceeded its own lifecycle bound");
            if(phase==0&&ticks>65&&mc.world==null&&mc.screen!=null){
                require(mc.isFullscreen()&&Display.isFullscreen(),"Fullscreen must be enabled at native startup");oldFps=mc.options.fpsLimit;oldVsync=mc.options.vsync;mc.options.pauseOnUnfocus=false;mc.options.vsync=false;Display.setVSyncEnabled(false);mc.options.fpsLimit=120;mc.options.viewDistance=8;org.polyfrost.oneconfig.internal.OneConfigConfig.limitUnfocusedFps=false;ClientPreferences.setSetupComplete(true);
                Class<?> cursor=Class.forName("org.polyfrost.oneconfig.internal.legacy.FullscreenCursor");try{cursor.getDeclaredField("software");oldSoftwareField=true;}catch(NoSuchFieldException nativeOnly){oldSoftwareField=false;}require(oldSoftwareField==baseline(),"Actual Core cursor implementation differs from declared mode");
                osCursorInfo=User32.getLibrary().getFunctionAddress("GetCursorInfo");require(osCursorInfo!=0,"Windows native cursor query unavailable");
                mc.startGame("arcadium-native-cursor073","Owned native cursor073",new WorldSettings(73073,WorldSettings.GameMode.CREATIVE,false,false,WorldGeneratorType.FLAT));phase=1;
            }else if(phase==1&&mc.world!=null&&mc.player!=null&&mc.getServer()!=null){phase=2;enter(mc);}
            if(phase==2){require(mc.world!=null&&mc.player!=null&&mc.getServer()!=null,"Owned SP world changed");require(mc.screen==expectedScreen,"Normal UI state changed unexpectedly; inspect recorded external input separately");
                if(matchedFrames>=3&&System.nanoTime()-started>=STATES.get(index).minimumMillis*1_000_000L&&(!STATES.get(index).rootBlankClick||blankClicked)){
                    var state=new LinkedHashMap<String,Object>();state.put("index",index);state.put("name",STATES.get(index).name);state.put("requestedFps",STATES.get(index).fps);state.put("actualFrames",frames-stageFrame);state.put("observedDiagnosticWholeFrameFps",(frames-stageFrame)*1_000_000_000.0/(System.nanoTime()-started));state.put("matchedVisibilityFrames",matchedFrames);state.put("elapsedMillis",(System.nanoTime()-started)/1_000_000.0);state.put("first",first);state.put("last",last);state.put("rootBlankClickRequired",STATES.get(index).rootBlankClick);if(blankClick!=null)state.put("actualRootBlankClick",blankClick);state.put("passed",true);states.add(state);
                    if(++index==STATES.size()){finish(null);return;}enter(mc);
                }
            }
            if(System.nanoTime()-lastStatus>300_000_000L){lastStatus=System.nanoTime();write(false);}
        }catch(Throwable failure){finish(failure);}
    }
    private static void enter(Minecraft mc)throws Exception{
        State state=STATES.get(index);mc.options.fpsLimit=state.fps;
        if(mc.isFullscreen()!=state.fullscreen)mc.toggleFullscreen();
        if(!state.ui){mc.openScreen(null);expectedScreen=null;}
        else if(index<=2){expectedScreen=new GameMenuScreen();mc.openScreen(expectedScreen);}
        else if(index==3){expectedScreen=new OptionsScreen(new GameMenuScreen(),mc.options);mc.openScreen(expectedScreen);}
        if(index==5){mc.reloadResources();report.put("actualResourceReloadCalled",true);}
        if(state.ui&&index>3){require(mc.screen==expectedScreen,"Reload/fullscreen transition replaced native options");}
        started=System.nanoTime();stageFrame=frames;matchedFrames=0;first=last=blankClick=null;blankClicked=false;write(false);
    }
    public static void frame(){
        if(!enabled()||done||phase!=2)return;
        try{frames++;var mc=Minecraft.getInstance();State state=STATES.get(index);var sample=snapshot(mc);if(first==null)first=sample;last=sample;
            if(!Boolean.TRUE.equals(sample.get("focused")))return;
            require(mc.isFullscreen()==state.fullscreen&&Display.isFullscreen()==state.fullscreen,"Actual fullscreen mode differs");require(mc.options.fpsLimit==state.fps,"Native frame limit changed unexpectedly");
            boolean grabbed=(boolean)sample.get("lwjglGrabbed"),relative=(boolean)sample.get("sdlRelativeMode"),nativeVisible=(boolean)sample.get("sdlCursorVisible"),osVisible=(boolean)sample.get("osCursorVisible"),software=(boolean)sample.get("oldSoftwareCursorActive");
            if(state.ui){require(!grabbed&&!relative,"Normal menu left gameplay relative input active");boolean softwareExpected=baseline()&&state.fullscreen;require(software==softwareExpected,"Actual software cursor mode differs");require(nativeVisible!=softwareExpected&&osVisible!=softwareExpected,"Native OS cursor visibility differs from baseline/candidate expected path");require(((Number)sample.get("currentSdlCursor")).longValue()!=0,"No selected native cursor");}
            else{require(grabbed&&relative&&!osVisible,"Gameplay grab/native effective cursor hiding did not restore");}
            matchedFrames++;
        }catch(Throwable error){finish(error);}
    }
    private static Map<String,Object> snapshot(Minecraft mc)throws Exception{
        long window=DisplaySdl.instance().getHandle(),cursor=SDLMouse.SDL_GetCursor(),def=SDLMouse.SDL_GetDefaultCursor();var value=new LinkedHashMap<String,Object>();value.put("frame",frames);value.put("tick",ticks);value.put("focused",Display.isActive());value.put("fullscreen",Display.isFullscreen());value.put("windowHandle",window);value.put("framebuffer",List.of(Display.getWidth(),Display.getHeight()));value.put("sdlCursorVisible",SDLMouse.SDL_CursorVisible());value.put("currentSdlCursor",cursor);value.put("defaultSdlCursor",def);value.put("cursorRole",cursor==def?"selected-system-default":"selected-native-nondefault");value.put("lwjglGrabbed",Mouse.isGrabbed());value.put("sdlRelativeMode",SDLMouse.SDL_GetWindowRelativeMouseMode(window));value.put("screen",mc.screen==null?"none":mc.screen.getClass().getName());
        boolean software=false;if(oldSoftwareField){Field field=Class.forName("org.polyfrost.oneconfig.internal.legacy.FullscreenCursor").getDeclaredField("software");field.setAccessible(true);software=field.getBoolean(null);}value.put("oldSoftwareCursorActive",software);
        try(MemoryStack stack=MemoryStack.stackPush()){ByteBuffer info=stack.calloc(24);info.putInt(0,24);require(JNI.invokePI(MemoryUtil.memAddress(info),osCursorInfo)!=0,"Actual Win32 cursor info query failed");value.put("osCursorVisible",(info.getInt(4)&1)!=0);value.put("osCursorHandle",info.getLong(8));value.put("osCursorDesktopPosition",List.of(info.getInt(16),info.getInt(20)));}
        return value;
    }
    public static void mouse(Screen screen){
        if(!enabled()||done||phase!=2||!STATES.get(index).rootBlankClick||screen!=expectedScreen)return;
        try{if(Mouse.getEventButton()!=0||!Mouse.getEventButtonState())return;var mc=Minecraft.getInstance();int x=Mouse.getEventX()*screen.width/mc.width,y=screen.height-Mouse.getEventY()*screen.height/mc.height-1;boolean blank=x>=screen.width*4/5&&y<=screen.height/5;
            var event=Map.<String,Object>of("kind","actual-root-mouse","tick",ticks,"x",x,"y",y,"blankTopRight",blank,"actualLwjglEvent",true,"state",STATES.get(index).name);record(event);if(blank){blankClicked=true;blankClick=event;}write(false);
        }catch(Throwable failure){finish(failure);}
    }
    public static void key(Screen screen){if(!enabled()||done||phase!=2||screen!=expectedScreen)return;record(Map.of("kind","actual-key-event-separate-interference-evidence","tick",ticks,"key",Keyboard.getEventKey(),"characterCode",(int)Keyboard.getEventCharacter(),"pressed",Keyboard.getEventKeyState()));}
    private static void record(Map<String,Object> value){if(inputEvents.size()==32)inputEvents.removeFirst();inputEvents.add(value);}
    private static void finish(Throwable failure){if(done)return;done=true;try{var mc=Minecraft.getInstance();mc.options.fpsLimit=oldFps;mc.options.vsync=oldVsync;Display.setVSyncEnabled(oldVsync);report.put("frameLimitRestored",mc.options.fpsLimit==oldFps);}catch(Throwable restore){if(failure==null)failure=restore;else failure.addSuppressed(restore);}report.put("failure",failure==null?null:failure.toString());try{write(failure==null&&states.size()==STATES.size());}catch(Exception ignored){}expectedScreen=null;Minecraft.getInstance().stop();}
    private static void write(boolean passed)throws Exception{report.put("schema","arcadium.native-cursor073.runtime.v1");report.put("passed",passed);report.put("mode",System.getProperty("arcadium.nativeCursor073.mode"));report.put("states",states);report.put("phase",phase);report.put("ticks",ticks);report.put("actualInputEvents",inputEvents);if(index<STATES.size()){State state=STATES.get(index);report.put("activeState",state.name);report.put("requestedFps",state.fps);report.put("awaitingRootBlankClick",state.rootBlankClick&&!blankClicked);report.put("blankClickHint","Top-right blank area: x>=80% and y<=20% of client GUI");report.put("matchedVisibilityFrames",matchedFrames);if(last!=null)report.put("latestNativeCursor",last);}report.put("syntheticOffline",true);report.put("qaSetCursorShowHideOrRelativeModeCalls",0);report.put("newInputOrRenderThreads",0);report.put("gameCommandsSent",0);report.put("rootVisualLowFpsMotionQualified",false);report.put("performanceClaim",false);Files.createDirectories(output().getParent());Files.writeString(output(),new GsonBuilder().setPrettyPrinting().create().toJson(report));}
}
