// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa;

import com.google.gson.GsonBuilder;
import io.github.arcadiumclient.runtime.client.ClientPreferences;
import io.github.arcadiumclient.runtime.client.features.NickHiderConfig;
import io.github.arcadiumclient.runtime.mixin.client.access.ChatScreenAccessor;
import io.github.arcadiumclient.runtime.qa.mixin.chatclicks073.ChatBookClicks073BookAccess;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.game.BookEditScreen;
import net.minecraft.client.gui.screen.game.ChatScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.*;
import net.minecraft.text.*;
import net.minecraft.world.*;
import net.minecraft.world.gen.WorldGeneratorType;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;

public final class ChatBookClicks073Qa {
    private record Case(String name,boolean book,boolean nick,String alias,ClickEvent.Action action,String value,boolean inherited,boolean wrapped,boolean unmatched){ }
    private static final List<Case> CASES=List.of(
            new Case("chat-off-run",false,false,"Guest073",ClickEvent.Action.RUN_COMMAND,"/say ArcadiumTest QA_CHAT_OFF_073",false,false,false),
            new Case("chat-on-unmatched-run",false,true,"Guest073",ClickEvent.Action.RUN_COMMAND,"/say ArcadiumTest QA_CHAT_UNMATCHED_073",false,false,true),
            new Case("chat-short-suggest",false,true,"G",ClickEvent.Action.SUGGEST_COMMAND,"/say ArcadiumTest QA_SUGGEST_073",true,false,false),
            new Case("chat-long-wrapped-run",false,true,"LongDisplayedPlayerAlias073",ClickEvent.Action.RUN_COMMAND,"/say ArcadiumTest QA_CHAT_WRAPPED_073",true,true,false),
            new Case("book-off-page",true,false,"Guest073",ClickEvent.Action.CHANGE_PAGE,"2",false,false,false),
            new Case("book-on-unmatched-page",true,true,"Guest073",ClickEvent.Action.CHANGE_PAGE,"2",true,false,true),
            new Case("book-long-wrapped-page",true,true,"LongDisplayedPlayerAlias073",ClickEvent.Action.CHANGE_PAGE,"2",true,true,false),
            new Case("book-on-run",true,true,"Guest073",ClickEvent.Action.RUN_COMMAND,"/say ArcadiumTest QA_BOOK_RUN_073",true,false,false));
    private static final List<Map<String,Object>> results=new ArrayList<>();
    private static final Map<String,Object> report=new LinkedHashMap<>();
    private static int ticks,phase,index,stageTicks,clickSerial,outgoingCalls;
    private static boolean done,capturePending,restored,oldNick,mouseEvent,mouseClick,actionSeen,actionHandled,outgoing,echo;
    private static String oldAlias,oldRules;
    private static Screen owner;
    private static Map<String,Object> stage,lastClick;
    private static Path screenshot;
    private static int expectedMouseButton;
    private ChatBookClicks073Qa(){ }
    public static boolean enabled(){return System.getProperty("arcadium.chatBookClicks073.output")!=null;}
    private static Path output(){return Path.of(System.getProperty("arcadium.chatBookClicks073.output"));}
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
    private static Case current(){return CASES.get(index);}
    private static void owned(){var mc=Minecraft.getInstance();require("ArcadiumTest".equals(mc.getSession().getUsername())&&"0".equals(mc.getSession().getAccessToken())&&mc.getCurrentServerEntry()==null,"Only the owned fake identity with no external server is allowed");}
    public static void tick(){
        if(!enabled()||done)return;var mc=Minecraft.getInstance();
        try{
            owned();require(++ticks<12000,"Owned chat/book QA timed out");
            if(phase==0&&ticks>65&&mc.world==null&&mc.screen!=null){ClientPreferences.setSetupComplete(true);mc.options.pauseOnUnfocus=false;mc.options.vsync=false;Display.setVSyncEnabled(false);mc.options.fpsLimit=120;mc.options.viewDistance=8;org.polyfrost.oneconfig.internal.OneConfigConfig.limitUnfocusedFps=false;oldNick=NickHiderConfig.getEnabled();oldAlias=NickHiderConfig.getNickname();oldRules=NickHiderConfig.getReplacementRulesJson();mc.startGame("arcadium-chat-book073","Owned chat/book click QA",new WorldSettings(73073,WorldSettings.GameMode.CREATIVE,false,false,WorldGeneratorType.FLAT).enableCommands());phase=1;}
            else if(phase==1&&mc.world!=null&&mc.player!=null&&mc.getServer()!=null){mc.openScreen(null);start(mc);phase=2;}
            else if(phase==2){
                require(mc.world!=null&&mc.player!=null&&mc.getServer()!=null,"The owned integrated world ended");stageTicks++;
                require(mc.width==1920&&mc.height==1080,"The native UI fixture requires full1920x1080");
                require(NickHiderConfig.getEnabled()==current().nick&&NickHiderConfig.getNickname().equals(current().alias),"The actual Nick Hider settings changed during the case");stage.put("actualNickHiderEnabled",NickHiderConfig.getEnabled());stage.put("actualAlias",NickHiderConfig.getNickname());
                if(stageTicks==10)capturePending=true;
                if(screenshot!=null&&pngReady(screenshot)){stage.put("screenshot",screenshot.toString());stage.put("screenshotReady",true);}
                require(mc.screen==owner||current().book&&current().action==ClickEvent.Action.RUN_COMMAND&&actionSeen&&mc.screen==null,"The native case screen was closed/replaced before its expected action");
                if(actionSeen&&actionHandled){
                    boolean complete=false;
                    if(current().action==ClickEvent.Action.RUN_COMMAND)complete=outgoing&&echo;
                    else if(current().action==ClickEvent.Action.SUGGEST_COMMAND){require(mc.screen==owner,"Suggest command changed the current chat screen");String text=((ChatScreenAccessor)owner).arcadium$chatField().getText();stage.put("actualSuggestedInput",text);complete=text.equals(current().value);}
                    else{int page=((ChatBookClicks073BookAccess)owner).clicks073$currentPage();stage.put("actualBookPageIndex",page);complete=page==1;}
                    if(complete){stage.put("passed",true);stage.put("completedAtTick",ticks);stage.put("originalPayloadPreserved",true);results.add(stage);require(results.size()==index+1,"Case order changed");if(++index==CASES.size()){finish(null);return;}start(mc);}
                }
            }
            if(ticks%10==0)write(false);
        }catch(Throwable error){finish(error);}
    }
    private static void start(Minecraft mc)throws Exception{
        require(index<CASES.size(),"Unexpected case");Case test=current();NickHiderConfig.setReplacementRulesJson("[]");NickHiderConfig.setNickname(test.alias);NickHiderConfig.setEnabled(test.nick);stageTicks=outgoingCalls=0;capturePending=false;mouseEvent=mouseClick=actionSeen=actionHandled=outgoing=echo=false;lastClick=null;screenshot=null;
        stage=new LinkedHashMap<>();stage.put("name",test.name);stage.put("index",index);stage.put("nickHiderEnabled",test.nick);stage.put("alias",test.alias);stage.put("expectedAction",test.action.toString());stage.put("expectedOriginalValue",test.value);stage.put("inheritedStyle",test.inherited);stage.put("wrappedText",test.wrapped);stage.put("nonmatchingText",test.unmatched);stage.put("awaitingRootMouseClick",true);stage.put("clickTarget","Green underlined text on the actual native chat/book screen");stage.put("startedAtTick",ticks);
        Text label=component(test);
        if(!test.book){mc.gui.getChat().clear();mc.gui.getChat().addMessage(label);owner=new ChatScreen("");mc.openScreen(owner);}
        else{var pages=new NbtList();pages.addElement(new NbtString(Text.Serializer.toJson(label)));pages.addElement(new NbtString(Text.Serializer.toJson(new LiteralText("PAGE 2: native CHANGE_PAGE completed"))));var nbt=new NbtCompound();nbt.putString("title","Local click proof");nbt.putString("author","ArcadiumTest");nbt.put("pages",pages);nbt.putBoolean("resolved",true);var book=new ItemStack(Items.WRITTEN_BOOK);book.setNbt(nbt);owner=new BookEditScreen(mc.player,book,false);mc.openScreen(owner);require(!((ChatBookClicks073BookAccess)owner).clicks073$unsigned(),"The local book must be readonly/written");}
        stage.put("screen",owner.getClass().getName());stage.put("originalComponentJson",Text.Serializer.toJson(label));write(false);
    }
    private static Text component(Case test){
        var style=new Style().setColor(Formatting.GREEN).setUnderlined(true).setClickEvent(new ClickEvent(test.action,test.value)).setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,new LiteralText("Original hover ArcadiumTest"))).setInsertion("Original ArcadiumTest insertion");
        String label=test.unmatched?"[CLICK LOCAL CHECK]":"[ArcadiumTest: CLICK LOCAL CHECK]";
        if(test.inherited){Text root=new LiteralText("");if(test.wrapped)root.append(new LiteralText("Native wrapped text check. ".repeat(test.book?3:5)));return root.append(new LiteralText("").setStyle(style).append(new LiteralText(label)));}
        return new LiteralText(label).setStyle(style);
    }
    public static void frame(){
        if(!enabled()||done||phase!=2||!capturePending)return;
        try{var mc=Minecraft.getInstance();owned();require(mc.screen==owner,"The case screen changed before the Root click");capturePending=false;String name=String.format(java.util.Locale.ROOT,"chat-book073-%02d-%s.png",index,current().name);Screenshot.take(mc.gameDir,name,mc.width,mc.height,mc.getRenderTarget());screenshot=mc.gameDir.toPath().resolve("screenshots").resolve(name);stage.put("gameScreenshot",screenshot.toString());write(false);}
        catch(Throwable error){finish(error);}
    }
    public static void mouseEvent(Screen screen,boolean begin){
        if(!enabled()||done||phase!=2)return;
        if(begin){mouseEvent=screen==owner&&Mouse.getEventButton()==0&&Mouse.getEventButtonState();expectedMouseButton=Mouse.getEventButton();}else{mouseEvent=false;mouseClick=false;}
    }
    public static void clicked(Screen screen,int x,int y,int button){
        if(!enabled()||done||phase!=2||screen!=owner)return;
        try{owned();if(!mouseEvent||button!=0||expectedMouseButton!=button)return;mouseClick=true;lastClick=new LinkedHashMap<>();lastClick.put("serial",++clickSerial);lastClick.put("tick",ticks);lastClick.put("x",x);lastClick.put("y",y);lastClick.put("button",button);lastClick.put("actualLwjglMouseEvent",true);lastClick.put("rawEventX",Mouse.getEventX());lastClick.put("rawEventY",Mouse.getEventY());stage.put("lastActualMouseClick",lastClick);write(false);}catch(Throwable error){finish(error);}
    }
    public static void hit(Text text){
        if(!enabled()||done||!mouseClick||lastClick==null)return;
        try{lastClick.put("nativeHitWasNull",text==null);if(text==null)return;lastClick.put("nativeHitClass",text.getClass().getName());lastClick.put("nativeHitLiteral",text instanceof LiteralText);lastClick.put("nativeDisplayedContent",text instanceof LiteralText literal?literal.getRawString():text.getContent());ClickEvent click=text.getStyle().getClickEvent();if(click!=null){lastClick.put("nativeAction",click.getAction().toString());lastClick.put("nativeOriginalValue",click.getValue());}lastClick.put("nativeInsertion",text.getStyle().getInsertion());HoverEvent hover=text.getStyle().getHoverEvent();if(hover!=null)lastClick.put("nativeHoverText",hover.getValue().getString());write(false);}catch(Throwable error){finish(error);}
    }
    public static void action(Screen screen,Text text,boolean returned,boolean handled){
        if(!enabled()||done||phase!=2||screen!=owner||!mouseClick||text==null)return;
        try{owned();ClickEvent click=text.getStyle().getClickEvent();if(click==null||click.getAction()!=current().action||!click.getValue().equals(current().value))return;require(lastClick!=null,"Native action has no actual Root mouse event");require(text instanceof LiteralText,"Native hit did not return the required literal component");if(current().inherited){require("Original ArcadiumTest insertion".equals(text.getStyle().getInsertion()),"Inherited insertion metadata changed");require(text.getStyle().getHoverEvent()!=null&&"Original hover ArcadiumTest".equals(text.getStyle().getHoverEvent().getValue().getString()),"Inherited hover metadata changed");}actionSeen=true;if(returned)actionHandled|=handled;stage.put("nativeActionReached",true);stage.put("nativeActionHandled",actionHandled);stage.put("awaitingRootMouseClick",false);write(false);}catch(Throwable error){finish(error);}
    }
    public static void sent(String value){
        if(!enabled()||done||phase!=2||value==null||!value.contains("QA_")||!value.startsWith("/say ArcadiumTest "))return;
        try{owned();require(mouseEvent&&mouseClick&&actionSeen&&current().action==ClickEvent.Action.RUN_COMMAND&&value.equals(current().value),"Local command was not the original payload from the real mouse action");require(++outgoingCalls==1,"One Root click sent the local command more than once");outgoing=true;stage.put("actualLocalPlayerOutgoingPayload",value);stage.put("actualLocalPlayerOutgoingCalls",outgoingCalls);stage.put("outgoingAtTick",ticks);write(false);}catch(Throwable error){finish(error);}
    }
    public static void received(Text text){
        if(!enabled()||done||phase!=2||current().action!=ClickEvent.Action.RUN_COMMAND||text==null)return;
        try{String marker=current().value.substring(current().value.lastIndexOf(' ')+1);String value=text.getString();if(outgoing&&value.contains(marker)){echo=true;stage.put("actualLocalServerEcho",value);stage.put("serverEffectObserved",true);write(false);}}catch(Throwable error){finish(error);}
    }
    private static void finish(Throwable error){
        if(done)return;done=true;
        try{NickHiderConfig.setReplacementRulesJson(oldRules==null?"[]":oldRules);NickHiderConfig.setNickname(oldAlias==null?"You":oldAlias);NickHiderConfig.setEnabled(oldNick);restored=true;}catch(Throwable restore){if(error==null)error=restore;else error.addSuppressed(restore);}
        report.put("failure",error==null?null:error.toString());try{write(error==null&&results.size()==CASES.size());}catch(Exception ignored){}owner=null;Minecraft.getInstance().stop();
    }
    private static boolean pngReady(Path path)throws Exception{if(!Files.isRegularFile(path)||Files.size(path)<12)return false;try(var channel=Files.newByteChannel(path)){var tail=java.nio.ByteBuffer.allocate(12);channel.position(channel.size()-12);while(tail.hasRemaining()&&channel.read(tail)>0){}return tail.position()==12&&tail.getInt(0)==0&&tail.getInt(4)==0x49454e44;}}
    private static void write(boolean passed)throws Exception{report.put("schema","arcadium.chat-book-click073.native.v1");report.put("passed",passed);report.put("cases",results);if(stage!=null)report.put("activeCase",stage);report.put("phase",phase);report.put("ticks",ticks);report.put("syntheticOffline",true);report.put("session","ArcadiumTest/token0");report.put("nicknameSettingsRestored",restored);report.put("actualMouseClicksObserved",clickSerial);report.put("syntheticHandleClickEventInvocations",0);report.put("nativeInputOrCommandsCancelled",false);report.put("externalServerOrGiftCommandsUsed",false);report.put("performanceClaim",false);Files.createDirectories(output().getParent());Files.writeString(output(),new GsonBuilder().setPrettyPrinting().create().toJson(report));}
}
