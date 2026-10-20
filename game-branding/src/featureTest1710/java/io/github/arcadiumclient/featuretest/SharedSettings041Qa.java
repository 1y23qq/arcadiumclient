// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.
package io.github.arcadiumclient.featuretest;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

public final class SharedSettings041Qa {
    private static final String CM="org.polyfrost.oneconfig.api.config.v1.ConfigManager";
    private static final String HM="org.polyfrost.oneconfig.api.hud.v1.HudManager";
    private static final String RUNTIME="io.github.arcadiumclient.runtime.client.features.";
    private static final String PROFILE="Shared041";
    private static final String FPS="org.polyfrost.evergreenhud.client.hud.FpsHud";
    private static final String TITLE="org.polyfrost.vanillahud.hud.TitleHud";
    private static final Map<String,Object> RESULT=new LinkedHashMap<>();
    private static final Map<String,Map<String,Object>> SCHEMAS=new LinkedHashMap<>();
    private static Path base, game, report, manifestFile;
    private static Map<String,Object> manifest;
    private static int ticks, phase, entered;
    private static boolean done;
    private static Object fps;
    private static String stage;
    static {
        SCHEMAS.put("fullbright.json",Map.of("enable",false,"lightLevel",7));
        SCHEMAS.put("fovchanger.json",Map.of("speed",37.5,"freezing",12.5,"sprint",62.5,"aiming",25.,"flying",40.,"submerged",15.));
        SCHEMAS.put("hybedwars.json",Map.of("heightOverlay",true,"heightOverlayOpacity",63,"minemenHeightLimit",false,"debugModeEnabled",true,"debugModeHeight",173,"middleClickInBedwarsShop",true));
        SCHEMAS.put("crosshairtweaks.json",Map.of("removeCrosshairBlendingFirstPerson",true,"removeCrosshairBlendingThirdPerson",false,"crosshairOpacityFirstPerson",61,"crosshairOpacityThirdPerson",39));
    }
    private SharedSettings041Qa() {}
    public static boolean enabled(){return Boolean.getBoolean("arcadium.sharedSettings041");}
    @SuppressWarnings("unchecked") public static void tick(){
        if(!enabled()||done)return;
        Minecraft client=Minecraft.getInstance();
        try{
            if(++ticks==1){
                require("ArcadiumTest".equals(client.getSession().getUsername())&&"0".equals(client.getSession().getAccessToken()),"Shared QA requires the isolated offline account");
                base=Path.of(System.getProperty("arcadium.sharedSettings041.base")).toRealPath();
                game=client.gameDir.toPath().toRealPath();
                require(game.equals(base.resolve("primary").toRealPath())||game.equals(base.resolve("legacy").toRealPath()),"Shared QA escaped its fixture");
                stage=System.getProperty("arcadium.sharedSettings041.stage");
                require(Set.of("write189","nativeRoundtrip","read189").contains(stage),"Unknown shared-settings stage");
                report=base.resolve(stage+"-result.json");manifestFile=base.resolve("expected.json");
                manifest=stage.equals("write189")?new LinkedHashMap<>():readMap(manifestFile);
                RESULT.put("stage",stage);RESULT.put("gameDir",game.toString());RESULT.put("syntheticSession",true);
            }
            require(ticks<900,"Shared-settings QA timed out at phase"+phase);
            if(ticks<100||ticks-entered<10)return;
            Object manager=object(HM);
            if(!(boolean)field(manager,"init")||call(manager,"getProvider",Class.forName(FPS))==null)return;
            if(phase==0){
                if(stage.equals("write189")){if(((Collection<?>)stat(CM,"profiles")).contains(PROFILE))stat(CM,"openProfile",PROFILE);else stat(CM,"createProfile",PROFILE);}
                require(PROFILE.equals(stat(CM,"activeProfile")),"The shared active profile did not load: "+stat(CM,"activeProfile"));
                verifyPaths();phase++;entered=ticks;return;
            }
            if(phase==1){
                if(stage.equals("write189")){
                    fps=ensureHud(FPS); configureHud(fps,false);Object title=ensureHud(TITLE);call(title,"setRelativeX",19.25f);call(title,"setRelativeY",31.5f);
                    manifest.put("fpsTree",call(call(fps,"getTree"),"getID"));manifest.put("titleTree",call(call(title,"getTree"),"getID"));
                    changeCommon(false);writeAutoText(false);stat(RUNTIME+"NoHitDelayConfig","setEnabled",false);
                }else{
                    verifyCommon();verifyAutoText();verifyHud();verifyUnknown(false);
                    if(stage.equals("nativeRoundtrip")){configureHud(fps,true);changeCommon(true);writeAutoText(true);}
                }
                save();phase++;entered=ticks;return;
            }
            if(phase==2){
                if(!stage.equals("read189")){
                    if(!autoTextWritten())return;
                    manifest.put("common",snapshotCommon());manifest.put("hud",snapshotHud(fps));manifest.put("autoText",snapshotAutoText());
                    if(stage.equals("write189")){
                        Path profile=Path.of(String.valueOf(call(stat(CM,"active"),"getFolder")));
                        Map<String,Object> unknown=new LinkedHashMap<>();
                        for(String id:List.of((String)manifest.get("titleTree"),"arcadium-no-hit-delay.json","arcadium-bedwars-overlay.json")){
                            Path file=profile.resolve(id);require(Files.isRegularFile(file),"Missing real1.8-only fixture file:"+id);unknown.put(id,new JsonParser().parse(Files.readString(file)));
                        }
                        manifest.put("unknown",unknown);
                    }else verifyUnknown(true);
                    write(manifestFile,manifest);
                }else verifyUnknown(true);
                RESULT.put("actualProfile",stat(CM,"activeProfile"));RESULT.put("fpsHud",snapshotHud(fps));RESULT.put("commonSettings",snapshotCommon());RESULT.put("autoText",snapshotAutoText());
                RESULT.put("unknown189OnlyFilesPreserved",!stage.equals("write189"));RESULT.put("passed",true);finish(client);return;
            }
        }catch(Throwable error){error.printStackTrace();RESULT.put("passed",false);RESULT.put("error",error.toString());RESULT.put("phase",phase);finish(client);}
    }
    private static void verifyPaths()throws Exception{
        Path active=((Path)call(stat(CM,"active"),"getFolder")).toRealPath();
        Path expected=base.resolve("primary/profiles/"+PROFILE).toRealPath();require(active.equals(expected),"OneConfig active folder is not shared: "+active);
        Path internal=((Path)call(stat(CM,"internal"),"getFolder")).toRealPath();require(internal.equals(base.resolve("primary/arcadium").toRealPath()),"Internal profile state is not shared");
        Path fabric=FabricLoader.getInstance().getConfigDir().toRealPath();require(fabric.equals(base.resolve("primary/config").toRealPath()),"Fabric config extras are not shared:"+fabric);
        require(Files.isSameFile(base.resolve("primary/oneconfig/sounds"),base.resolve("legacy/oneconfig/sounds")),"The sound cache was not shared");
        require(!Files.isSameFile(base.resolve("primary/oneconfig/.skiko"),base.resolve("legacy/oneconfig/.skiko")),"The private renderer cache was shared");
        for(String id:List.of("saves","options.txt"))require(!Files.isSameFile(base.resolve("primary").resolve(id),base.resolve("legacy").resolve(id)),"World or vanilla options unexpectedly shared:"+id);
        RESULT.put("resolvedPaths",Map.of("profile",active.toString(),"internal",internal.toString(),"fabricConfig",fabric.toString(),"worldsAndOptionsSeparate",true));
    }
    private static Object tree(String id)throws Exception{
        Object value=call(object("org.polyfrost.oneconfig.internal.ui.api.ConfigRegistry"),"findTree",id);
        require(value!=null,"Missing shared module:"+id);return value;
    }
    private static Object prop(String id,String key)throws Exception{Object p=call(tree(id),"getProp",key);require(p!=null,"Missing shared property:"+id+"/"+key);return p;}
    private static void set(String id,String key,Object value)throws Exception{
        Object p=prop(id,key),old=call(p,"get");
        if(value instanceof Number n){if(old instanceof Float)value=n.floatValue();else if(old instanceof Double)value=n.doubleValue();else if(old instanceof Integer)value=n.intValue();else if(old instanceof Long)value=n.longValue();}
        call(p,"setAs",value);
    }
    private static void changeCommon(boolean nativeWrite)throws Exception{
        for(var module:SCHEMAS.entrySet())for(var option:module.getValue().entrySet())set(module.getKey(),option.getKey(),nativeWrite?changed(option.getValue()):option.getValue());
        set("arcadium-auto-text.json","enabled",!nativeWrite);set("arcadium-auto-text.json","onlyWhilePlaying",nativeWrite);
        set("themes.json","gradientAccent",true);set("themes.json","customGradient",true);set("themes.json","gradientStart",nativeWrite?0xFFB14587:0xFF168B65);set("themes.json","gradientEnd",nativeWrite?0xFF35BCC4:0xFFE4A23D);set("themes.json","gradientSpeed",nativeWrite?2.25f:1.75f);set("themes.json","gradientSmoothness",nativeWrite?64f:37f);
        Object bind=call(prop("oneconfig.json","oneConfigKeybind"),"get");Object keys=stat("org.polyfrost.oneconfig.api.platform.v1.Platform","compatibility");keys=call(keys,"keys");int code=((Number)call(keys,nativeWrite?"getKeyL":"getKeyR")).intValue();call(bind,"setKeyCodes",new int[]{code});call(bind,"setMods",(byte)(nativeWrite?1:0));
    }
    private static Object changed(Object value){if(value instanceof Boolean b)return!b;if(value instanceof Number n)return n.doubleValue()+1.25;return value;}
    private static Map<String,Object> snapshotCommon()throws Exception{
        Map<String,Object> out=new LinkedHashMap<>();
        for(var module:SCHEMAS.entrySet()){Map<String,Object> values=new LinkedHashMap<>();for(String key:module.getValue().keySet())values.put(key,call(prop(module.getKey(),key),"get"));out.put(module.getKey(),values);}
        for(String id:List.of("arcadium-auto-text.json","themes.json")){Map<String,Object> values=new LinkedHashMap<>();List<String> names=id.equals("themes.json")?List.of("gradientAccent","customGradient","gradientStart","gradientEnd","gradientSpeed","gradientSmoothness"):List.of("enabled","onlyWhilePlaying");for(String key:names)values.put(key,call(prop(id,key),"get"));out.put(id,values);}
        out.put("menuKeybind",keySnapshot(call(prop("oneconfig.json","oneConfigKeybind"),"get")));return out;
    }
    private static void verifyCommon()throws Exception{equal(manifest.get("common"),snapshotCommon(),"Module/theme/keybind settings did not survive the version switch");}
    private static Object keySnapshot(Object bind)throws Exception{Object keys=call(stat("org.polyfrost.oneconfig.api.platform.v1.Platform","compatibility"),"keys");List<String> names=new ArrayList<>();int[] codes=(int[])call(bind,"getKeyCodes");if(codes!=null)for(int code:codes)names.add(String.valueOf(call(keys,"keyName",code)));return Map.of("names",names,"mods",call(bind,"getMods"));}
    @SuppressWarnings("unchecked") private static Object ensureHud(String cls)throws Exception{
        Object manager=object(HM);for(Object h:(List<?>)call(manager,"getActiveInstances"))if(h.getClass().getName().equals(cls))return h;
        Object provider=call(manager,"getProvider",Class.forName(cls));require(provider!=null,"Missing real HUD provider:"+cls);Object hud=call(provider,"make",(Object)null);call(manager,"markProviderKnown",hud);((List<Object>)call(manager,"getActiveInstances")).add(hud);call(hud,"setup");Object revision=call(manager,"getInstancesRevision");call(revision,"setIntValue",((Number)call(revision,"getIntValue")).intValue()+1);return hud;
    }
    @SuppressWarnings({"rawtypes","unchecked"}) private static void configureHud(Object hud,boolean nativeWrite)throws Exception{
        call(hud,"setSection",Enum.valueOf((Class)Class.forName("org.polyfrost.oneconfig.api.hud.v1.Section"),"TopLeft"));call(hud,"setAlignment",Enum.valueOf((Class)Class.forName("org.polyfrost.compose.layout.PolyAlign"),"TopLeft"));
        call(hud,"setRelativeX",nativeWrite?131.75f:73.25f);call(hud,"setRelativeY",nativeWrite?81.25f:46.5f);call(hud,"setUseGuiScale",false);call(hud,"setCustomScale",nativeWrite?1.65f:1.35f);call(hud,"setTextAlign",nativeWrite?1:2);call(hud,"setTextColor",nativeWrite?0xFF8A35C2:0xFF46BA91);call(hud,"setTextChroma",false);call(hud,"setHidden",false);call(hud,"setShowBackground",true);call(hud,"setBgColor",nativeWrite?0xB2233844:0xA1412535);
    }
    private static Map<String,Object> snapshotHud(Object hud)throws Exception{Map<String,Object> out=new LinkedHashMap<>();for(String key:List.of("RelativeX","RelativeY","UseGuiScale","CustomScale","TextAlign","TextColor","Hidden","ShowBackground","BgColor"))out.put(key,call(hud,"get"+key));out.put("tree",call(call(hud,"getTree"),"getID"));return out;}
    private static void verifyHud()throws Exception{for(Object h:(List<?>)call(object(HM),"getActiveInstances"))if(manifest.get("fpsTree").equals(call(call(h,"getTree"),"getID"))){fps=h;break;}require(fps!=null,"The existing customized FPS HUD was not loaded");equal(manifest.get("hud"),snapshotHud(fps),"FPS HUD position/scale/alignment/colors changed across versions");}
    private static void writeAutoText(boolean nativeWrite)throws Exception{
        Object api=object(RUNTIME+"AutoText");if(!nativeWrite){call(api,"add");List<?> entries=(List<?>)call(api,"getEntries");Object e=entries.get(entries.size()-1);manifest.put("autoTextId",call(e,"getId"));}
        String id=(String)manifest.get("autoTextId");call(api,"rename",id,nativeWrite?"Native shared entry":"Primary shared entry");call(api,"setText",id,nativeWrite?"/shared_native_041":"/shared_primary_041");Object property=call(api,"binding",id),bind=call(property,"get");Object keys=call(stat("org.polyfrost.oneconfig.api.platform.v1.Platform","compatibility"),"keys");int code=((Number)call(keys,nativeWrite?"getKeyH":"getKeyV")).intValue();Object replacement=call(bind,"copyWith",new int[]{code},null,(byte)0);call(property,"setAs",replacement);call(api,"save");
    }
    private static Object snapshotAutoText()throws Exception{Object api=object(RUNTIME+"AutoText");for(Object e:(List<?>)call(api,"getEntries"))if(manifest.get("autoTextId").equals(call(e,"getId")))return Map.of("id",call(e,"getId"),"name",call(e,"getName"),"text",call(e,"getText"),"keys",keySnapshot(call(call(api,"binding",manifest.get("autoTextId")),"get")));throw new AssertionError("The shared AutoText entry is missing");}
    private static void verifyAutoText()throws Exception{equal(manifest.get("autoText"),snapshotAutoText(),"Fabric-config AutoText message/key did not survive the version switch");}
    private static boolean autoTextWritten()throws Exception{Path file=FabricLoader.getInstance().getConfigDir().resolve("arcadium/autotext.json");return Files.isRegularFile(file)&&Files.readString(file).contains((String)((Map<?,?>)snapshotAutoText()).get("text"));}
    @SuppressWarnings("unchecked") private static void verifyUnknown(boolean afterSave)throws Exception{Map<String,Object> unknown=(Map<String,Object>)manifest.get("unknown");Path dir=(Path)call(stat(CM,"active"),"getFolder");for(var e:unknown.entrySet()){Path file=dir.resolve(e.getKey());require(Files.isRegularFile(file),"Native deleted an unsupported1.8-only module/HUD file:"+e.getKey());equal(e.getValue(),new JsonParser().parse(Files.readString(file)),"Unsupported1.8 file changed:"+e.getKey());}if(stage.equals("read189")){boolean title=false;for(Object h:(List<?>)call(object(HM),"getActiveInstances"))if(h.getClass().getName().equals(TITLE)&&manifest.get("titleTree").equals(call(call(h,"getTree"),"getID")))title=true;require(title,"The1.8-onlyTitle HUD was lost after returning from native");}if(manifest.get("unknownProperties") instanceof Map<?,?> extras)for(var extra:extras.entrySet()){var actual=new JsonParser().parse(Files.readString(dir.resolve(extra.getKey().toString()))).getAsJsonObject().get("shared041Unknown");equal(extra.getValue(),actual,"An unknown property in a supported module was lost:"+extra.getKey());}RESULT.put(afterSave?"unknownFilesAfterSave":"unknownFilesLoaded",unknown.keySet());}
    private static void save()throws Exception{call(stat(CM,"active"),"saveAll");call(stat(CM,"internal"),"saveAll");}
    private static void finish(Minecraft client){done=true;try{write(report,RESULT);}catch(Exception e){e.printStackTrace();}client.stop();}
    private static void equal(Object expected,Object actual,String message){var gson=new GsonBuilder().create();require(new JsonParser().parse(gson.toJson(expected)).equals(new JsonParser().parse(gson.toJson(actual))),message+": expected="+gson.toJson(expected)+", actual="+gson.toJson(actual));}
    private static void write(Path path,Object value)throws Exception{Files.writeString(path,new GsonBuilder().setPrettyPrinting().create().toJson(value));}
    @SuppressWarnings("unchecked") private static Map<String,Object> readMap(Path path)throws Exception{return new GsonBuilder().create().fromJson(Files.readString(path),LinkedHashMap.class);}
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static Object object(String name)throws Exception{return Class.forName(name).getField("INSTANCE").get(null);}
    private static Object stat(String cls,String method,Object...args)throws Exception{return call(Class.forName(cls),method,args);}
    private static Object field(Object target,String name)throws Exception{for(Class<?> c=target instanceof Class<?> t?t:target.getClass();c!=null;c=c.getSuperclass())try{Field f=c.getDeclaredField(name);f.setAccessible(true);return f.get(target instanceof Class<?>?null:target);}catch(NoSuchFieldException ignored){}throw new NoSuchFieldException(name);}
    private static Object call(Object target,String name,Object...args)throws Exception{
        Class<?> type=target instanceof Class<?> t?t:target.getClass();List<Method> methods=new ArrayList<>();for(Class<?> c=type;c!=null;c=c.getSuperclass())methods.addAll(Arrays.asList(c.getDeclaredMethods()));methods.addAll(Arrays.asList(type.getMethods()));for(Method m:methods){
            if(!m.getName().equals(name)||m.getParameterCount()!=args.length||target instanceof Class<?> && !Modifier.isStatic(m.getModifiers()))continue;
            Class<?>[] ps=m.getParameterTypes();boolean ok=true;for(int i=0;i<ps.length;i++)if(!compatible(ps[i],args[i]))ok=false;if(!ok)continue;
            m.setAccessible(true);try{return m.invoke(Modifier.isStatic(m.getModifiers())?null:target,args);}catch(InvocationTargetException e){if(e.getCause() instanceof Exception x)throw x;if(e.getCause() instanceof Error x)throw x;throw e;}
        }
        if(target instanceof Class<?>)try{return call(type.getField("INSTANCE").get(null),name,args);}catch(NoSuchFieldException ignored){}
        throw new NoSuchMethodException(type.getName()+"."+name);
    }
    private static boolean compatible(Class<?> type,Object value){if(value==null)return!type.isPrimitive();if(!type.isPrimitive())return type.isInstance(value);return type==int.class&&value instanceof Integer||type==float.class&&value instanceof Float||type==double.class&&value instanceof Double||type==boolean.class&&value instanceof Boolean||type==long.class&&value instanceof Long||type==byte.class&&value instanceof Byte;}
}
