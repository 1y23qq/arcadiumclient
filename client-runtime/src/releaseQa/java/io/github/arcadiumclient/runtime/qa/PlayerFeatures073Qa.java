// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa;

import com.google.gson.GsonBuilder;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.authlib.GameProfile;
import io.github.arcadiumclient.runtime.client.capes.LocalCape;
import io.github.arcadiumclient.runtime.client.features.*;
import io.github.arcadiumclient.runtime.client.skins.UploadedSkinTexture;
import java.awt.image.BufferedImage;
import java.lang.reflect.*;
import java.nio.IntBuffer;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.Future;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.living.player.RemoteClientPlayerEntity;
import net.minecraft.client.render.model.PlayerModelPart;
import net.minecraft.entity.Entity;
import net.minecraft.item.*;
import net.minecraft.resource.Identifier;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.gen.WorldGeneratorType;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;

public final class PlayerFeatures073Qa {
    private static final boolean CONTROL=Boolean.getBoolean("arcadium.playerFeatures073.public072");
    private static final boolean FORGE=Boolean.getBoolean("arcadium.playerFeatures073.forge");
    private static final String REFERENCE=System.getProperty("arcadium.playerFeatures073.reference");
    private static final String VARIANT=System.getProperty("arcadium.playerFeatures073.variant","core");
    private static final boolean HELD=VARIANT.equals("held");
    private static final String HELD_PROFILE=System.getProperty("arcadium.playerFeatures073.heldProfile","vanilla");
    private static final String ANIMATION_CONTROLS=System.getProperty("arcadium.playerFeatures073.animationControls","default");
    private static final boolean RAVEN=Boolean.getBoolean("arcadium.playerFeatures073.raven");
    private static final boolean LEGACY_TOGGLE=Boolean.getBoolean("arcadium.playerFeatures073.legacyPositionToggle");
    private static final boolean VOXEL_ISOLATION=Boolean.getBoolean("arcadium.playerFeatures073.voxelIsolation");
    private static String voxelVariant="optimized";
    private static Map<String,Object> lastVoxelEvidence=Map.of();
    private static final boolean STONE=Boolean.getBoolean("arcadium.playerFeatures073.stone");
    private static final Identifier SELF_SKIN=new Identifier("arcadium_qa","textures/entity/p3_self.png"),OTHER_SKIN=new Identifier("arcadium_qa","textures/entity/p3_other.png");
    private static final String[] COUNTERS={"GpuPlayerPipeline.playersDrawn","NativePlayerPosePipeline.bodyPartsQueued","NativePlayerPosePipeline.armorPartsQueued","GpuEquipmentPipeline.partsQueued","GpuVoxelSkinPipeline.analyticParts","GpuVoxelSkinPipeline.nativeParts","GpuModelTransforms.dispatches","GpuModelTransforms.offloadedParts","GpuResidentBodies.actorsDrawn","ModernArmorGlintPipeline.layersSupported","ModernArmorGlintPipeline.glintParts","NametagLayout.scopesReused"};
    private static final String[] CORE={"sword","bow","cape-change","nick-change","hurt","old-sneak"},MODERN={"modern19","modern15","hurt"},FORGE_CASES={"forge-observe","forge-mutate","forge-cancel-player","forge-cancel-living"};
    private static final String[] HELD_CASES={"fp-sword","fp-block","fp-bow","back-sword","back-block","back-bow","front-sword","front-block","front-bow","other-sword","other-block","other-bow"};
    private static final String[] HELD_WITH_STONE={"fp-sword","fp-block","fp-bow","back-sword","back-block","back-bow","front-sword","front-block","front-bow","other-sword","other-block","other-bow","fp-stone","back-stone","front-stone","other-stone"};
    private static final Map<String,Object> report=new LinkedHashMap<>();
    private static final Actor[] actors=new Actor[2];
    private static final long[][][] traces=new long[3][3][COUNTERS.length+3];
    private static final Map<String,String>[][] traceStates=new Map[3][3];
    private static final List<Map<String,Object>>[] itemSamples=new List[]{null,new ArrayList<>(),new ArrayList<>()};
    private static final String[] itemOwners=new String[8],itemTransforms=new String[8];
    private static final Map<String,Object>[] cameraInputs=new Map[8];
    private static final int[] legacyCalls=new int[8],legacyApplied=new int[8];
    private static final java.nio.FloatBuffer matrixScratch=BufferUtils.createFloatBuffer(16);
    private static int itemDepth;
    private static int phase,ticks,wait,warm,scenario,mode,rendering=-1,menuFrames,anchorWait,caseSelectedTick;
    private static boolean done,cameraPrepared,saved,configured,configuredGpu;
    private static String failure;
    private static Future<?> environment;
    private static Class<?> probe;
    private static Object animationItems,animationMovement,animationOther,oldGlint,oldSneak;
    private static Field glintField,sneakField,wavyField,blockingField;
    private static boolean oldWavy,oldBlocking,oldNick,oldCape,oldSkin,oldAnimations;
    private static int oldAnimationMode;
    private static String oldNickname,oldRules,oldPreset;
    private static final Map<String,Object> originalItemControls=new LinkedHashMap<>();
    private static final List<String> ITEM_CONTROLS=List.of("itemPositions","itemPositionsInThirdPerson","lunarItemPositions","lunarBlockHitPosition","applyCustomizationToBlockItems","itemOffsetX","itemOffsetY","itemOffsetZ","itemScaleX","itemScaleY","itemScaleZ","itemRotationX","itemRotationY","itemRotationZ");
    private static float oldBadlionScaleX,oldBadlionScaleY,oldBadlionScaleZ;
    private static BufferedImage firstCape,firstNames;
    private static long currentLayerCalls;
    private static final FovConvergence fovConvergence=new FovConvergence();
    private static Map<?,?> referenceReport;
    private PlayerFeatures073Qa(){}
    public static boolean enabled(){return System.getProperty("arcadium.playerFeatures073.output")!=null;}
    private static String[] cases(){if(VOXEL_ISOLATION)return new String[]{"fp-sword"};return HELD?(STONE?HELD_WITH_STONE:HELD_CASES):FORGE?FORGE_CASES:VARIANT.equals("modern")?MODERN:CORE;}
    private static String current(){return cases()[scenario];}

    public static void tick(){
        if(!enabled()||done)return;
        try{
            Minecraft mc=Minecraft.getInstance();require(++ticks<6000,"Player feature fixture timed out");wait++;
            if(phase==0&&wait>60&&mc.screen!=null&&mc.screen.getClass().getSimpleName().contains("MainMenu")){
                require("ArcadiumTest".equals(mc.getSession().getUsername())&&"0".equals(mc.getSession().getAccessToken())&&mc.getCurrentServerEntry()==null,"Use only the owned offline fake account");
                require(List.of("core","modern","held").contains(VARIANT)&&List.of("vanilla","animations","all").contains(HELD_PROFILE),"Unknown player feature variant/profile");
                require(List.of("default","overflow-custom","badlion-custom").contains(ANIMATION_CONTROLS)&&(ANIMATION_CONTROLS.equals("default")||HELD&&!HELD_PROFILE.equals("vanilla")),"Nondefault controls require an explicit enabled held-animation profile");
                require(!RAVEN||FORGE&&HELD,"Raven is restricted to the owned actual Forge held fixture");
                require(!VOXEL_ISOLATION||HELD&&FORGE&&HELD_PROFILE.equals("all")&&ANIMATION_CONTROLS.equals("default")&&!LEGACY_TOGGLE&&!RAVEN,"Voxel diagnosis requires the exact isolated all-profile fixture");
                require(!LEGACY_TOGGLE||HELD&&ANIMATION_CONTROLS.equals("overflow-custom"),"Legacy position toggle requires explicit fake held Overflow controls");
                require(List.of("off","readonly","pack").contains(PlayerFeatures073QuadEvidence.mode()),"Unknown quad observation mode");
                require(!CONTROL||REFERENCE==null,"A public control cannot consume another reference");
                if(!CONTROL){require(REFERENCE!=null,"Candidate requires the matching successful public072 reference");var baseline=read(Path.of(REFERENCE).resolve("result.json"));referenceReport=baseline;require(Boolean.TRUE.equals(baseline.get("voxelIsolationOnly"))==VOXEL_ISOLATION,"Reference one-case diagnostic scope differs");var run=read(Path.of(REFERENCE).resolve("run.json"));require(Boolean.TRUE.equals(baseline.get("passed"))&&Boolean.TRUE.equals(baseline.get("public072Control"))&&Boolean.TRUE.equals(run.get("passed"))&&Boolean.TRUE.equals(run.get("naturalExit"))&&!Boolean.TRUE.equals(run.get("forcedTermination")),"Public072 reference is not a natural complete pass");}
                if(FORGE){
                    require(Boolean.getBoolean("arcadium.forge.prepared"),"Forge was not prepared by the real launch path");
                    Class<?> loader=Class.forName("net.minecraftforge.fml.common.Loader");Object instance=loader.getMethod("instance").invoke(null);Map<?,?> mods=(Map<?,?>)loader.getMethod("getIndexedModList").invoke(instance);Object container=mods.get("arcadiumrenderprobe");require(container!=null,"Real Forge callback probe is absent");probe=container.getClass().getMethod("getMod").invoke(container).getClass();require(probe.getField("registered").getBoolean(null),"Real Forge observer was not registered");
                    if(RAVEN){Object raven=mods.get("keystrokes");require(raven!=null,"Pinned Raven was not loaded by real FML");Object mod=raven.getClass().getMethod("getMod").invoke(raven);require(mod!=null&&mod.getClass().getName().equals("keystrokesmod.Raven"),"Unexpected Raven container instance");report.put("actualRaven",Map.of("fmlModId","keystrokes","modClass",mod.getClass().getName(),"loaded",true));}
                }else require(!Boolean.getBoolean("arcadium.forge.prepared"),"Non-Forge fixture unexpectedly uses Forge");
                saveAndConfigure();io.github.arcadiumclient.runtime.client.ClientPreferences.setSetupComplete(true);
                mc.options.pauseOnUnfocus=false;mc.options.vsync=false;mc.options.fpsLimit=120;mc.options.viewDistance=3;mc.options.fov=70;mc.options.cloudRenderMode=0;mc.options.perspective=0;mc.options.viewBobbing=false;mc.options.hideGui=false;
                org.polyfrost.oneconfig.internal.OneConfigConfig.limitUnfocusedFps=false;
                configure(false);seedSkins(mc);
                report.put("purpose","Public072 native pixels versus merged073 player feature compatibility; not FPS data");report.put("public072Control",CONTROL);report.put("forgePrepared",FORGE);report.put("variant",VARIANT);report.put("heldProfile",HELD_PROFILE);report.put("cases",List.of(cases()));report.put("reference",REFERENCE==null?"none":REFERENCE);report.put("counterOrder",List.of(COUNTERS));
                report.put("heldCaseMeaning","sword idle, sword blocking and bow; explicit stone suffixes are true stone cubes");report.put("quadMode",PlayerFeatures073QuadEvidence.mode());
                report.put("tracedSubjectOrder",List.of("self-avatar","ordinary-neighbor","actual-local-player"));report.put("productionQuadCompatibilityAtStart",productionQuadMetrics());
                report.put("animationControls",animationControls());report.put("ravenRequested",RAVEN);report.put("voxelIsolationOnly",VOXEL_ISOLATION);
                report.put("settings",Map.of("width",mc.width,"height",mc.height,"fov",70,"viewDistance",3,"fpsLimit",120,"renderDelta",.5,"worldRenderScale",false,"minimumPlayers",1,"full3DSkin",Skin3DConfig.getEnabled()));
                mc.startGame("arcadium-player-features073","Player Features QA",new WorldSettings(730721,WorldSettings.GameMode.CREATIVE,false,false,WorldGeneratorType.FLAT).enableCommands());phase=1;wait=0;
            }else if(phase==1&&mc.world!=null&&mc.player!=null&&wait>100){
                if(environment==null){mc.player.abilities.flying=true;environment=mc.getServer().executeTask(()->{var world=mc.getServer().getWorld(0);world.getGameRules().set("doMobSpawning","false");world.getGameRules().set("doDaylightCycle","false");world.getGameRules().set("randomTickSpeed","0");world.setTimeOfDay(6000);world.getData().setRaining(false);world.getData().setThundering(false);for(var entity:new ArrayList<>(world.entities))if(!(entity instanceof net.minecraft.entity.living.player.PlayerEntity))world.removeEntityNow(entity);var players=world.players;require(players.size()==1&&players.getFirst() instanceof net.minecraft.server.entity.living.player.ServerPlayerEntity,"Owned native server player is absent");var player=(net.minecraft.server.entity.living.player.ServerPlayerEntity)players.getFirst();require("ArcadiumTest".equals(player.getName()),"Unexpected native server account");player.abilities.flying=true;player.networkHandler.teleport(0,HELD?4:6,HELD?-2:-10,0,5);return Map.of("nativeServerTeleport",true,"offlineOnly",true,"position",List.of(player.x,player.y,player.z));});return;}
                if(!environment.isDone()||mc.world.entities.stream().anyMatch(entity->!(entity instanceof net.minecraft.entity.living.player.PlayerEntity)))return;report.put("serverAnchor",environment.get());
                if(!cameraPrepared){mc.openScreen(null);mc.player.abilities.flying=true;report.put("clientAnchor",List.of(mc.player.x,mc.player.y,mc.player.z));require(++anchorWait<=100,"Native server anchor was not acknowledged by the owned client");if(!anchorMatches(mc))return;cameraPrepared=true;report.put("nativeServerAnchorAcknowledged",true);progress("native-server-anchor-acknowledged");return;}
                requireAnchor(mc,"before-readiness");
                if(!CeraAdmission072Readiness.ready(mc,output()))return;
                require(mc.player.getUuid().equals(mc.getSession().getProfile().getId()),"Fake player does not own the fake session");
                actors[0]=new Actor(mc,new GameProfile(mc.player.getUuid(),"ArcadiumTest"),0);actors[1]=new Actor(mc,new GameProfile(new UUID(0x7307300000000000L,2),"BB"),1);
                for(int i=0;i<actors.length;i++){Actor actor=actors[i];actor.updatePositionAndAngles(20,20,20,10,8);actor.nativeTick();actor.updatePositionAndAngles(i==0?-1.7:1.7,4,3,10,8);actor.nativeTick();actor.getSyncedData().update(10,(byte)127);mc.world.forceEntity(730730+i,actor);}
                if(FORGE)probe.getField("target").set(null,actors[0]);selectCase();phase=2;warm=0;
                report.put("gpu",GL11.glGetString(GL11.GL_RENDERER));report.put("resolution",mc.width+"x"+mc.height);
            }
            if(phase==2)freeze(mc);
        }catch(Throwable error){fail(error);}
    }

    private static void saveAndConfigure()throws Exception{
        oldNick=NickHiderConfig.getEnabled();oldNickname=NickHiderConfig.getNickname();oldRules=NickHiderConfig.getReplacementRulesJson();oldCape=CapeConfig.getEnabled();oldPreset=CapeConfig.getPreset();oldSkin=Skin3DConfig.getEnabled();oldAnimations=AnimationsConfig.getEnabled();oldAnimationMode=AnimationsConfig.getMode();
        Class<?> type=Class.forName("org.polyfrost.overflowanimations.config.OverflowAnimationsConfig");Object config=type.getMethod("instance").invoke(null);animationItems=type.getField("items").get(config);animationMovement=type.getField("movement").get(config);animationOther=type.getField("other").get(config);
        glintField=animationItems.getClass().getField("armorGlint");oldGlint=glintField.get(animationItems);sneakField=animationMovement.getClass().getField("sneakAnimation");oldSneak=sneakField.get(animationMovement);wavyField=animationMovement.getClass().getField("wavyArms");oldWavy=wavyField.getBoolean(animationMovement);blockingField=animationOther.getClass().getField("thirdPersonSwordBlockingPosition");oldBlocking=blockingField.getBoolean(animationOther);
        if(!ANIMATION_CONTROLS.equals("default")){for(String name:ITEM_CONTROLS)originalItemControls.put(name,animationItems.getClass().getField(name).get(animationItems));oldBadlionScaleX=AnimationsConfig.getItemScaleX();oldBadlionScaleY=AnimationsConfig.getItemScaleY();oldBadlionScaleZ=AnimationsConfig.getItemScaleZ();}
        saved=true;
        AnimationsConfig.setEnabled(!HELD||!HELD_PROFILE.equals("vanilla"));AnimationsConfig.setMode(0);wavyField.setBoolean(animationMovement,HELD?oldWavy:true);blockingField.setBoolean(animationOther,HELD?oldBlocking:true);Skin3DConfig.INSTANCE.chooseEnabled(!HELD||HELD_PROFILE.equals("all"));NickHiderConfig.setEnabled(!HELD);NickHiderConfig.setNickname("Hidden");CapeConfig.setEnabled(!HELD);LocalCape.changed();
        if(!ANIMATION_CONTROLS.equals("default")){animationProperty("enabled",true);animationProperty("mode",ANIMATION_CONTROLS.equals("badlion-custom")?1:0);refreshAnimationCategories();applyControlNumerics(true);}
    }
    private static void animationProperty(String name,Object value){org.polyfrost.oneconfig.api.config.v1.Config config=AnimationsConfig.INSTANCE;var tree=config.getTree();require(tree!=null,"Actual Animations settings tree is absent");var property=tree.getProp(name);require(property!=null,"Actual Animations public property is absent: "+name);property.setAs(value);}
    private static void refreshAnimationCategories()throws Exception{Class<?> type=Class.forName("org.polyfrost.overflowanimations.config.OverflowAnimationsConfig");Object config=type.getMethod("instance").invoke(null);animationItems=type.getField("items").get(config);animationMovement=type.getField("movement").get(config);animationOther=type.getField("other").get(config);}
    private static void itemControl(String name,Object value)throws Exception{animationItems.getClass().getField(name).set(animationItems,value);}
    private static void applyControlNumerics(boolean custom)throws Exception{
        if(ANIMATION_CONTROLS.equals("badlion-custom")){
            animationProperty("itemScaleX",custom?.8f:1f);animationProperty("itemScaleY",custom?1.2f:1f);animationProperty("itemScaleZ",custom?.9f:1f);refreshAnimationCategories();
            require(AnimationsConfig.getEnabled()&&AnimationsConfig.getMode()==1&&AnimationsConfig.badlion()!=null,"Actual Badlion public callbacks did not install their runtime state");
        }else if(ANIMATION_CONTROLS.equals("overflow-custom")){
            require(AnimationsConfig.getEnabled()&&AnimationsConfig.getMode()==0&&AnimationsConfig.badlion()==null,"Overflow controls are not active on the actual public mode");
            itemControl("itemPositions",true);itemControl("itemPositionsInThirdPerson",true);itemControl("applyCustomizationToBlockItems",true);
            if(LEGACY_TOGGLE){itemControl("lunarItemPositions",false);itemControl("lunarBlockHitPosition",false);}
            itemControl("itemOffsetX",custom?1f:0f);itemControl("itemOffsetY",custom?-.75f:0f);itemControl("itemOffsetZ",custom?.5f:0f);
            itemControl("itemScaleX",custom?.8f:1f);itemControl("itemScaleY",custom?1.2f:1f);itemControl("itemScaleZ",custom?.9f:1f);
            itemControl("itemRotationX",0f);itemControl("itemRotationY",custom?12f:0f);itemControl("itemRotationZ",custom?-8f:0f);
        }
    }
    private static Map<String,Object> animationControls()throws Exception{refreshAnimationCategories();Map<String,Object> state=new LinkedHashMap<>();state.put("requested",ANIMATION_CONTROLS);state.put("moduleEnabled",AnimationsConfig.getEnabled());state.put("publicMode",AnimationsConfig.getMode());state.put("badlionRuntimeActive",AnimationsConfig.badlion()!=null);for(String name:ITEM_CONTROLS)state.put(name,animationItems.getClass().getField(name).get(animationItems));return state;}
    private static void enumValue(Field field,Object target,String value)throws Exception{for(Object choice:field.getType().getEnumConstants())if(((Enum<?>)choice).name().equals(value)){field.set(target,choice);return;}throw new IllegalStateException("Native public enum is absent: "+value);}
    private static NickReplacementRule rule(String source,String target){return new NickReplacementRule(UUID.nameUUIDFromBytes((source+":"+target).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString(),source,target,true,true);}
    private static void selectCase()throws Exception{
        fovConvergence.reset();caseSelectedTick=ticks;
        String name=current();if(!ANIMATION_CONTROLS.equals("badlion-custom")){enumValue(glintField,animationItems,name.equals("modern19")?"V1_19_4":name.equals("modern15")?"V1_15":"VANILLA");enumValue(sneakField,animationMovement,name.equals("old-sneak")?"V1_7":"VANILLA");}
        if(HELD){
            Minecraft mc=Minecraft.getInstance();mc.options.perspective=name.startsWith("back-")?1:name.startsWith("front-")?2:0;
            Item item=name.endsWith("stone")?new ItemStack(net.minecraft.block.Blocks.STONE).getItem():name.endsWith("bow")?Items.BOW:Items.DIAMOND_SWORD;boolean using=usesItemCase(name);
            for(Actor actor:actors){ItemStack held=new ItemStack(item);actor.setEquipment(0,held);if(using)actor.setItemInUse(held,71980);else actor.clearItemInUse();for(int slot=1;slot<=4;slot++)actor.setEquipment(slot,null);}
            ItemStack local=name.startsWith("other-")?null:new ItemStack(item);mc.player.inventory.setItem(mc.player.inventory.selectedSlot,local);if(using&&local!=null)mc.player.setItemInUse(local,71980);else mc.player.clearItemInUse();
            if(FORGE){probe.getField("mode").set(null,"observe");Object observed=mc.options.perspective==0?actors[0]:mc.player;probe.getField("target").set(null,observed);require(probe.getField("target").get(null)==observed,"Actual FML observer target was not installed");}caseSelectedTick=ticks;report.put("assigned-"+name,handState(mc));warm=0;configured=false;progress("case-selected-"+name);return;
        }
        String preset=scenario>=2&&!FORGE&&!VARIANT.equals("modern")?"cherry-blossom":"migrator";CapeConfig.INSTANCE.select(preset);LocalCape.changed();
        String alias=scenario>=3&&!FORGE&&!VARIANT.equals("modern")?"DD":"BB";NickHiderConfig.INSTANCE.storeRules(List.of(rule("ArcadiumTest",alias),rule("BB","CCCC")));
        for(Actor actor:actors){ItemStack held=new ItemStack(name.equals("sword")||FORGE?Items.DIAMOND_SWORD:Items.BOW);actor.setEquipment(0,held);actor.setItemInUse(held,71980);Item[] armor={Items.DIAMOND_BOOTS,Items.DIAMOND_LEGGINGS,Items.DIAMOND_CHESTPLATE,Items.DIAMOND_HELMET};for(int slot=1;slot<=4;slot++){ItemStack piece=new ItemStack(armor[slot-1]);piece.addEnchantment(net.minecraft.enchantment.Enchantment.UNBREAKING,1);actor.setEquipment(slot,piece);}}
        if(FORGE)probe.getField("mode").set(null,name.substring("forge-".length()));
        warm=0;configured=false;progress("case-selected-"+name);
    }
    private static void freeze(Minecraft mc){
        requireAnchor(mc,"before-capture");mc.player.abilities.flying=true;mc.player.velocityX=mc.player.velocityY=mc.player.velocityZ=0;
        if(HELD){ItemStack selected=mc.player.inventory.getSelectedItem();if(selected!=null&&usesItemCase(current())&&!current().startsWith("other-"))mc.player.setItemInUse(selected,71980);else mc.player.clearItemInUse();}
        for(Actor actor:actors)if(actor!=null){actor.ticks=160;actor.yaw=actor.lastYaw=actor.bodyYaw=actor.lastBodyYaw=10;actor.headYaw=actor.lastHeadYaw=25;actor.pitch=actor.lastPitch=8;actor.walkAnimationProgress=2.4f;actor.walkAnimationSpeed=actor.lastWalkAnimationSpeed=.6f;actor.attackAnimationProgress=actor.lastAttackAnimationProgress=.35f;actor.setSneaking(current().equals("old-sneak"));actor.damagedTimer=current().equals("hurt")?5:0;actor.lastX=actor.prevX=actor.x;actor.lastY=actor.prevY=actor.y;actor.lastZ=actor.prevZ=actor.z;}
        if(HELD){mc.player.ticks=160;mc.player.bodyYaw=mc.player.lastBodyYaw=mc.player.headYaw=mc.player.lastHeadYaw=0;mc.player.walkAnimationProgress=mc.player.walkAnimationSpeed=mc.player.lastWalkAnimationSpeed=mc.player.attackAnimationProgress=mc.player.lastAttackAnimationProgress=0;}
        mc.player.yaw=mc.player.lastYaw=0;mc.player.pitch=mc.player.lastPitch=5;mc.player.lastX=mc.player.prevX=mc.player.x;mc.player.lastY=mc.player.prevY=mc.player.y;mc.player.lastZ=mc.player.prevZ=mc.player.z;mc.world.setTimeOfDay(6000);mc.world.getData().setRaining(false);mc.world.getData().setThundering(false);
    }
    private static boolean anchorMatches(Minecraft mc){return mc.player.x==0&&mc.player.y==(HELD?4:6)&&mc.player.z==(HELD?-2:-10);}
    private static boolean usesItemCase(String name){return name.endsWith("block")||name.endsWith("bow");}
    private static void requireAnchor(Minecraft mc,String step){if(anchorMatches(mc))return;report.put("anchorFailure",Map.of("step",step,"actual",List.of(mc.player.x,mc.player.y,mc.player.z),"expected",List.of(0,HELD?4:6,HELD?-2:-10)));throw new IllegalStateException("Native acknowledged player anchor drifted: "+step);}
    private static Object pinnedField(Class<?> type,Object instance,String named,String intermediary)throws Exception{Field field;try{field=type.getDeclaredField(named);}catch(NoSuchFieldException runtime){field=type.getDeclaredField(intermediary);}field.setAccessible(true);return field.get(instance);}
    private static Object stackDescription(ItemStack stack){return stack==null?"none":Map.of("itemClass",stack.getItem().getClass().getName(),"objectIdentity",System.identityHashCode(stack));}
    private static Map<String,Object> handState(Minecraft mc)throws Exception{
        var renderer=mc.gameRenderer.itemInHandRenderer;ItemStack selected=mc.player.inventory.getSelectedItem();ItemStack cached=(ItemStack)pinnedField(net.minecraft.client.render.ItemInHandRenderer.class,renderer,"itemInHand","f_85016326");
        float height=(float)pinnedField(net.minecraft.client.render.ItemInHandRenderer.class,renderer,"handHeight","f_47136511"),previous=(float)pinnedField(net.minecraft.client.render.ItemInHandRenderer.class,renderer,"lastHandHeight","f_46631024");
        Map<String,Object> state=new LinkedHashMap<>();state.put("elapsedNativeTicks",ticks-caseSelectedTick);state.put("playerAnimationAge",mc.player.ticks);state.put("selectedSlot",mc.player.inventory.selectedSlot);state.put("localSelectedStack",stackDescription(selected));state.put("localItemInHand",stackDescription(mc.player.getItemInHand()));state.put("nativeCachedStack",stackDescription(cached));state.put("handHeight",height);state.put("lastHandHeight",previous);state.put("nativeCacheMatchesSelected",cached==null?selected==null:selected!=null&&cached.isEqualForHoldAnimation(selected));state.put("nativeProgressSettled",height==1&&previous==1);state.put("renderHandFlag",pinnedField(net.minecraft.client.render.GameRenderer.class,mc.gameRenderer,"newCamPitch","f_73581010"));state.put("hideGui",mc.options.hideGui);state.put("cameraIsActualLocalPlayer",mc.getCamera()==mc.player);state.put("sameNativeRendererInstance",renderer==mc.getItemInHandRenderer());state.put("localHasItemInUse",mc.player.hasItemInUse());state.put("localItemUseTimer",mc.player.getItemUseTimer());state.put("privateRendererFieldsWritten",false);return state;
    }
    private static void configure(boolean gpu)throws Exception{
        gpu&=!CONTROL;if(configured&&configuredGpu==gpu)return;
        for(Method method:BetaOptimizationsConfig.class.getMethods())if(Modifier.isStatic(method.getModifiers())&&method.getName().startsWith("set")&&Arrays.equals(method.getParameterTypes(),new Class<?>[]{boolean.class}))method.invoke(null,false);
        for(String name:List.of("GpuModelTransforms","GpuPlayerPipeline","GpuEquipmentPipeline","GpuVoxelSkinLayers","GpuNameTags","CachedNameMasking","CompactNameTagPreparation","RetainedNameTagScenes","ReduceRenderHookAllocations"))beta(name,gpu);
        io.github.arcadiumclient.runtime.client.branding.NametagLayout.optimized=gpu;org.polyfrost.oneconfig.internal.TextOptimizationOptions.nameMaskCache=gpu;
        beta("MinimumPlayers",1);beta("MinimumGpuLabels",1);boolean voxels=gpu&&(!HELD||HELD_PROFILE.equals("all"))&&(!VOXEL_ISOLATION||!voxelVariant.equals("body-native3d"));beta("GpuVoxelSkinLayers",voxels);try{Class.forName("io.github.arcadiumclient.runtime.client.optimizations.GpuVoxelSkinPipeline").getField("enabled").setBoolean(null,voxels);}catch(ClassNotFoundException absent){require(CONTROL,"Merged voxel pipeline is absent");}
        configured=true;configuredGpu=gpu;
    }
    private static void beta(String name,Object value)throws Exception{try{BetaOptimizationsConfig.class.getMethod("set"+name,value instanceof Boolean?boolean.class:int.class).invoke(null,value);}catch(NoSuchMethodException absent){require(CONTROL,"Merged beta setter is absent: "+name);}}
    private static long metric(String key){int dot=key.indexOf('.');String type=key.substring(0,dot),field=key.substring(dot+1);String prefix=type.equals("NametagLayout")?"io.github.arcadiumclient.runtime.client.branding.":"io.github.arcadiumclient.runtime.client.optimizations.";try{return Class.forName(prefix+type).getField(field).getLong(null);}catch(ReflectiveOperationException absent){if(CONTROL)return 0;throw new IllegalStateException(key,absent);}}
    private static long[] metrics(){long[] values=new long[COUNTERS.length];for(int i=0;i<values.length;i++)values[i]=metric(COUNTERS[i]);return values;}
    private static Map<String,Object> callbacks()throws Exception{if(!FORGE)return Map.of();require(probe.getField("failure").get(null)==null,"Forge probe failed");require((boolean)probe.getMethod("restored").invoke(null),"Forge pose mutation was not restored");Map<String,Object> values=new LinkedHashMap<>();for(String name:List.of("playerPre","livingPre","livingPost","playerPost","canceled","mutations"))values.put(name,probe.getField(name).getLong(null));values.put("sequence",probe.getField("sequence").get(null).toString());return values;}
    private static Map<String,Object> localFirstPersonSample(){return itemSamples[1].stream().filter(sample->"local-player".equals(sample.get("owner"))&&"FIRST_PERSON".equals(sample.get("transform"))).findFirst().orElseThrow(()->new IllegalStateException("Actual local first-person draw was absent from the toggle proof"));}
    private static void controlsEffect(Minecraft mc,String key,Map<String,Object> result,BufferedImage custom,long deadline,Operation<Void> original)throws Exception{
        if(ANIMATION_CONTROLS.equals("default")||!current().equals("fp-sword")&&!current().equals("fp-stone"))return;
        BufferedImage neutral;
        try{
            applyControlNumerics(false);result.put("neutralPublicAnimationControls",animationControls());
            if(LEGACY_TOGGLE&&current().equals("fp-sword")&&FORGE)probe.getMethod("reset").invoke(null);
            neutral=render(mc,1,false,deadline,original);ImageIO.write(neutral,"png",output().getParent().resolve(key+"-neutral-controls.png").toFile());
            if(LEGACY_TOGGLE&&current().equals("fp-sword")){
                Map<String,Object> enabledSample=localFirstPersonSample(),enabledCallbacks=callbacks();
                itemControl("itemPositions",false);Map<String,Object> disabledSettings=animationControls();
                if(FORGE)probe.getMethod("reset").invoke(null);
                BufferedImage disabled=render(mc,1,false,deadline,original);Map<String,Object> disabledSample=localFirstPersonSample(),disabledCallbacks=callbacks(),pixels=compare(neutral,disabled,0);
                Map<String,Object> toggle=new LinkedHashMap<>();result.put("legacyPositionToggle",toggle);toggle.put("enabledPublicSettings",result.get("neutralPublicAnimationControls"));toggle.put("disabledPublicSettings",disabledSettings);toggle.put("enabledActualDraw",enabledSample);toggle.put("disabledActualDraw",disabledSample);toggle.put("enabledCallbacks",enabledCallbacks);toggle.put("disabledCallbacks",disabledCallbacks);toggle.put("pixels",pixels);Map<?,?> enabledSettings=(Map<?,?>)result.get("neutralPublicAnimationControls");List<String> changedSettings=new ArrayList<>();for(Object name:enabledSettings.keySet())if(!Objects.equals(enabledSettings.get(name),disabledSettings.get(name)))changedSettings.add(name.toString());toggle.put("changedPublicSettings",changedSettings);require(changedSettings.equals(List.of("itemPositions")),"The legacy toggle changed another public setting");Map<?,?> enabledProjection=(Map<?,?>)enabledSample.get("projectionAtDraw"),disabledProjection=(Map<?,?>)disabledSample.get("projectionAtDraw");boolean sameProjection=Arrays.equals((int[])enabledProjection.get("rawBits"),(int[])disabledProjection.get("rawBits"));toggle.put("projectionRawBitsEqual",sameProjection);require(sameProjection,"The actual native projection changed during the public position toggle");
                Path file=output().getParent().resolve(key+"-legacy-position-disabled.png");ImageIO.write(disabled,"png",file.toFile());
                require(enabledCallbacks.equals(disabledCallbacks),"Actual Forge callbacks changed across the public position toggle");
                int enabledCalls=((Number)enabledSample.get("legacyFirstPersonCalls")).intValue(),enabledApplied=((Number)enabledSample.get("legacyFirstPersonApplied")).intValue(),disabledCalls=((Number)disabledSample.get("legacyFirstPersonCalls")).intValue(),disabledApplied=((Number)disabledSample.get("legacyFirstPersonApplied")).intValue();
                boolean effective=enabledCalls>0&&enabledApplied>0&&disabledCalls>0&&disabledApplied==0&&((Number)pixels.get("changedPixels")).intValue()>0;toggle.put("actualLegacySettingEffective",effective);
                require(effective,"Actual public legacy position toggle bypassed its native helper or did not affect any scene pixel");
                if(REFERENCE!=null)recordPixels(result,"public072DisabledLegacyPositionPixels",ImageIO.read(Path.of(REFERENCE).resolve(file.getFileName()).toFile()),disabled,0);
            }
        }finally{applyControlNumerics(true);result.put("restoredCustomAnimationControls",animationControls());}
        Map<String,Object> pixels=compare(neutral,custom,0);result.put("customControlVisibleEffect",pixels);require(((Number)pixels.get("changedPixels")).intValue()>0,"Selected nondefault public item controls did not change a scene pixel");
        Path file=output().getParent().resolve(key+"-neutral-controls.png");ImageIO.write(neutral,"png",file.toFile());
        if(REFERENCE!=null)recordPixels(result,"public072NeutralControlPixels",ImageIO.read(Path.of(REFERENCE).resolve(file.getFileName()).toFile()),neutral,0);
    }

    private static void verifyHeldForgeCallbacks(Map<String,Object> result,Map<String,Object> nativeCallbacks,Map<String,Object> gpuCallbacks){
        if(!FORGE||!HELD)return;int subject=Minecraft.getInstance().options.perspective==0?0:2;long nativeEntries=traces[1][subject][COUNTERS.length],gpuEntries=traces[2][subject][COUNTERS.length];
        result.put("actualFmlTarget",Map.of("subject",subject,"kind",subject==2?"actual-local-player":"visible-self-avatar","nativeRendererEntries",nativeEntries,"gpuRendererEntries",gpuEntries));
        require(nativeEntries>0&&nativeEntries==gpuEntries,"The actual FML observer target was not rendered equally in both modes");
        for(String key:List.of("playerPre","livingPre","livingPost","playerPost")){require(((Number)nativeCallbacks.get(key)).longValue()==nativeEntries,"Native FML event count does not match actual renderer invocations: "+key);require(((Number)gpuCallbacks.get(key)).longValue()==gpuEntries,"GPU FML event count does not match actual renderer invocations: "+key);}
        String once="playerPre,livingPre,livingPost,playerPost";String expected=String.join(",",Collections.nCopies(Math.toIntExact(nativeEntries),once));require(expected.equals(nativeCallbacks.get("sequence"))&&expected.equals(gpuCallbacks.get("sequence")),"Actual FML event order changed or an event was duplicated");
    }
    static final class FovConvergence {
        private int tick=-1,bits,stable;
        void reset(){tick=-1;bits=stable=0;}
        int observe(int actualTick,float current,float previous){
            if(!Float.isFinite(current)||!Float.isFinite(previous)||current<=0||previous<=0)throw new IllegalStateException("Actual native FOV state is invalid");
            int currentBits=Float.floatToRawIntBits(current),previousBits=Float.floatToRawIntBits(previous);
            if(tick!=actualTick){boolean consecutive=tick>=0&&actualTick==tick+1;stable=currentBits==previousBits?(consecutive&&currentBits==bits?stable+1:1):0;tick=actualTick;}
            else if(currentBits!=previousBits||currentBits!=bits)stable=0;
            bits=currentBits;return stable;
        }
    }
    private static Map<String,Object> nativeFovState(Minecraft mc)throws Exception{
        float current=(float)pinnedField(net.minecraft.client.render.GameRenderer.class,mc.gameRenderer,"fov","f_94566859"),previous=(float)pinnedField(net.minecraft.client.render.GameRenderer.class,mc.gameRenderer,"lastFov","f_40701228");
        int stable=fovConvergence.observe(ticks,current,previous);return Map.of("current",current,"previous",previous,"currentRawBits",Float.floatToRawIntBits(current),"previousRawBits",Float.floatToRawIntBits(previous),"realTick",ticks,"stableConsecutiveNativeTicks",stable,"privateFieldsWritten",false,"readinessInvokesFovModifier",false);
    }
    private static boolean sameVoxelPublication(Map<?,?> full,Map<?,?> affine){
        if(!Objects.equals(full.get("mdiArguments"),affine.get("mdiArguments")))return false;List<?> before=(List<?>)full.get("cpuUploadInputs"),after=(List<?>)affine.get("cpuUploadInputs");if(before.isEmpty()||before.size()!=after.size())return false;
        for(int draw=0;draw<before.size();draw++){Map<?,?> a=(Map<?,?>)before.get(draw),b=(Map<?,?>)after.get(draw);if(!Arrays.equals((int[])a.get("vertexWords"),(int[])b.get("vertexWords")))return false;int[] x=(int[])a.get("instanceWords"),y=(int[])b.get("instanceWords");if(x.length!=y.length)return false;for(int row=0;row<x.length;row+=19)for(int word=12;word<19;word++)if(x[row+word]!=y[row+word])return false;}
        List<?> a=(List<?>)full.get("submissions"),b=(List<?>)affine.get("submissions");if(a.isEmpty()||a.size()!=b.size())return false;
        for(int i=0;i<a.size();i++){Map<?,?> x=(Map<?,?>)a.get(i),y=(Map<?,?>)b.get(i);if(!Boolean.TRUE.equals(x.get("accepted"))||!Boolean.TRUE.equals(y.get("accepted")))return false;for(String key:List.of("captureMatrixAtSubmitEntry","physicalModelViewAtSubmitEntry","projectionAtSubmitEntry"))if(!Arrays.equals((int[])((Map<?,?>)x.get(key)).get("rawBits"),(int[])((Map<?,?>)y.get(key)).get("rawBits")))return false;}
        return true;
    }
    private static Map<String,Object> voxelControl(Minecraft mc,String key,String variant,boolean gpu,BufferedImage nativeImage,Map<String,Object> nativeCallbacks,long deadline,Operation<Void> original)throws Exception{
        Map<String,Object> control=new LinkedHashMap<>();voxelVariant=variant;configured=false;if(FORGE)probe.getMethod("reset").invoke(null);long[] before=metrics();BufferedImage image=render(mc,2,gpu,deadline,original);long[] after=metrics(),delta=new long[before.length];for(int i=0;i<delta.length;i++)delta[i]=after[i]-before[i];
        control.put("counterDeltas",delta);control.put("evidence",lastVoxelEvidence);control.put("traces",traceSnapshot(2));control.put("states",traceStates[2].clone());control.put("itemSamples",new ArrayList<>(itemSamples[2]));control.put("callbacks",callbacks());control.put("nativePixels",compare(nativeImage,image,2));control.put("actualNativeTick",ticks);verifyHeldForgeCallbacks(control,nativeCallbacks,(Map<String,Object>)control.get("callbacks"));require(nativeCallbacks.equals(control.get("callbacks")),"Actual diagnostic callbacks changed");
        ImageIO.write(image,"png",output().getParent().resolve(key+"-"+variant+"-diagnostic.png").toFile());try{control.put("actualReducedHooks",Class.forName("io.github.arcadiumclient.runtime.client.optimizations.RenderHookAllocationControl").getField("enabled").getBoolean(null));}catch(ClassNotFoundException absent){require(CONTROL,"Missing current hook state");control.put("actualReducedHooks","absent in public control");}return control;
    }
    private static void voxelControls(Minecraft mc,String key,Map<String,Object> result,BufferedImage nativeImage,Map<String,Object> nativeCallbacks,long deadline,Operation<Void> original)throws Exception{
        Map<String,Object> fullEvidence=lastVoxelEvidence;long[][] fullTraces=traceSnapshot(2);Map<String,String>[] fullStates=traceStates[2].clone();List<Map<String,Object>> fullSamples=new ArrayList<>(itemSamples[2]);result.put("fullOptimizedVoxelEvidence",fullEvidence);Map<String,Object> controls=new LinkedHashMap<>();List<String> errors=new ArrayList<>();result.put("voxelIsolationControls",controls);result.put("voxelControlErrors",errors);int actualTick=ticks;if(fullEvidence.get("failure")!=null)errors.add("Original full-path evidence failed");
        try{
            Map<String,Object> affine=voxelControl(mc,key,"voxel-affine",true,nativeImage,nativeCallbacks,deadline,original);controls.put("voxel-affine",affine);
            Map<String,Object> native3d=voxelControl(mc,key,"body-native3d",true,nativeImage,nativeCallbacks,deadline,original);controls.put("body-native3d",native3d);
            Map<String,Object> repeat=voxelControl(mc,key,"native-repeat",false,nativeImage,nativeCallbacks,deadline,original);controls.put("native-repeat",repeat);
            if(ticks!=actualTick)errors.add("An actual native tick advanced inside the same-frame controls");
            if(((Number)((Map<?,?>)repeat.get("nativePixels")).get("changedPixels")).intValue()!=0)errors.add("Native-to-native RGBA repeat was not exact");
            if(!CONTROL){
                long[] affineCounts=(long[])affine.get("counterDeltas"),nativeCounts=(long[])native3d.get("counterDeltas");Map<?,?> evidence=(Map<?,?>)affine.get("evidence");
                if(affineCounts[1]<=0||affineCounts[5]<=0||((Number)evidence.get("readyBypasses")).longValue()<=0||((Number)evidence.get("actualMdiCalls")).longValue()<=0)errors.add("CPU-affine control did not retain real compact body and GPU voxel/MDI routes");
                boolean samePublication=sameVoxelPublication(fullEvidence,evidence);affine.put("sameVertexMaterialAndInputMatrices",samePublication);if(!samePublication)errors.add("The affine control changed another voxel publication input");
                if(nativeCounts[1]<=0||nativeCounts[4]!=0||nativeCounts[5]!=0)errors.add("Native3D control did not retain compact body with the original skin layer");
                for(Object item:(List<?>)evidence.get("submissions"))if(Boolean.TRUE.equals(((Map<?,?>)item).get("marker")))errors.add("CPU-affine voxel control retained a pose marker");
            }
            for(var entry:controls.entrySet())if(((Map<?,?>)((Map<?,?>)entry.getValue()).get("evidence")).get("failure")!=null)errors.add("Voxel evidence failed in "+entry.getKey());
        }finally{for(int subject=0;subject<fullTraces.length;subject++){System.arraycopy(fullTraces[subject],0,traces[2][subject],0,fullTraces[subject].length);traceStates[2][subject]=fullStates[subject];}itemSamples[2].clear();itemSamples[2].addAll(fullSamples);voxelVariant="optimized";configured=false;}
        progress("collected-full-3d-controls-before-original-quality-assert");
    }
    public static void world(float delta,long deadline,Operation<Void> original){
        if(!enabled()||done||phase!=2){original.call(delta,deadline);return;}
        try{
            Minecraft mc=Minecraft.getInstance();freeze(mc);Map<String,Object> nativeFov=nativeFovState(mc);
            if(++warm<=80){configure(!CONTROL);original.call(.5f,deadline);return;}
            if(HELD){Map<String,Object> state=handState(mc);report.put("settled-"+current(),state);boolean needsCache=current().startsWith("fp-")||current().startsWith("other-");if(ticks-caseSelectedTick<10||needsCache&&(!Boolean.TRUE.equals(state.get("nativeCacheMatchesSelected"))||!Boolean.TRUE.equals(state.get("nativeProgressSettled")))){require(ticks-caseSelectedTick<=100,"Native held-item cache did not settle after public hotbar selection");configure(!CONTROL);original.call(.5f,deadline);return;}require(Boolean.TRUE.equals(state.get("cameraIsActualLocalPlayer")),"Held fixture camera is not the actual local player");require(mc.player.hasItemInUse()==(usesItemCase(current())&&!current().startsWith("other-")),"Actual local use state does not match the selected item case");}
            report.put("nativeFov-"+current(),nativeFov);if(((Number)nativeFov.get("stableConsecutiveNativeTicks")).intValue()<2){require(ticks-caseSelectedTick<=100,"Native FOV did not converge across consecutive real ticks");configure(!CONTROL);original.call(.5f,deadline);return;}
            var membership=CeraAdmission072Readiness.actors(mc,List.of(actors));require(Boolean.TRUE.equals(membership.get("allRegistered")),"Actor is absent from its authoritative mature native section");
            if(!HELD){require(LocalCape.textureFor(actors[0].getUuid())!=null&&LocalCape.textureFor(actors[1].getUuid())==null,"Local cape did not remain self-only");require(actors[0].getCapeTextureLocation().equals(LocalCape.textureFor(actors[0].getUuid())),"Actual self cape getter did not return the public preset");}
            require(NickHider.display(new String("BB")).equals(HELD?"BB":"CCCC"),"Raw neighbor name did not preserve its selected policy");
            String expected=HELD?"ArcadiumTest":scenario>=3&&!FORGE&&!VARIANT.equals("modern")?"DD":"BB";require(NickHider.display(new String("ArcadiumTest")).equals(expected),"Explicit own-name rule did not produce one pass");
            if(FORGE)probe.getMethod("reset").invoke(null);
            BufferedImage nativeImage=render(mc,1,false,deadline,original);Map<String,Object> nativeCallbacks=callbacks();
            if(FORGE)probe.getMethod("reset").invoke(null);
            long[] before=metrics();BufferedImage gpuImage=render(mc,2,!CONTROL,deadline,original);long[] after=metrics();Map<String,Object> gpuCallbacks=callbacks();
            Path directory=output().getParent();Files.createDirectories(directory);String key=String.format(Locale.ROOT,"%02d-%s",scenario,current());ImageIO.write(nativeImage,"png",directory.resolve(key+"-native.png").toFile());ImageIO.write(gpuImage,"png",directory.resolve(key+"-gpu.png").toFile());
            Map<String,Object> result=new LinkedHashMap<>();report.put(key,result);result.put("membership",membership);result.put("nativeCallbacks",nativeCallbacks);result.put("gpuCallbacks",gpuCallbacks);result.put("nativeTraces",traceSnapshot(1));result.put("gpuTraces",traceSnapshot(2));result.put("nativeStates",traceStates[1].clone());result.put("gpuStates",traceStates[2].clone());result.put("nativeItemSamples",new ArrayList<>(itemSamples[1]));result.put("gpuItemSamples",new ArrayList<>(itemSamples[2]));result.put("actualCape",Map.of("enabled",CapeConfig.getEnabled(),"preset",CapeConfig.getPreset(),"selfTextureSelected",!HELD,"remoteUnchanged",true));result.put("actualNick",Map.of("enabled",NickHiderConfig.getEnabled(),"own",expected,"rawBB",HELD?"BB":"CCCC","sourceOwnName",actors[0].getGameProfile().getName(),"sessionUnchanged",mc.getSession().getUsername().equals("ArcadiumTest")&&mc.getSession().getAccessToken().equals("0")));result.put("style",((Enum<?>)glintField.get(animationItems)).name());result.put("sneak",((Enum<?>)sneakField.get(animationMovement)).name());result.put("perspective",mc.options.perspective);result.put("actualAnimations",Map.of("enabled",AnimationsConfig.getEnabled(),"mode",AnimationsConfig.getMode(),"full3DSkin",Skin3DConfig.getEnabled()));result.put("actualHeldInputs",List.of(Map.of("item",actors[0].getItemInHand().getItem().getClass().getSimpleName(),"hasItemInUse",actors[0].hasItemInUse(),"itemUseTimer",actors[0].getItemUseTimer()),Map.of("item",actors[1].getItemInHand().getItem().getClass().getSimpleName(),"hasItemInUse",actors[1].hasItemInUse(),"itemUseTimer",actors[1].getItemUseTimer())));if(HELD)result.put("actualLocalHandState",handState(mc));
            require(nativeCallbacks.equals(gpuCallbacks),"Real Forge callback sequence/cancel/mutation changed");verifyHeldForgeCallbacks(result,nativeCallbacks,gpuCallbacks);if(VOXEL_ISOLATION){result.put("pixels",compare(nativeImage,gpuImage,2));voxelControls(mc,key,result,nativeImage,nativeCallbacks,deadline,original);}recordPixels(result,"pixels",nativeImage,gpuImage,2);if(VOXEL_ISOLATION)require(((List<?>)result.get("voxelControlErrors")).isEmpty(),"A diagnostic control did not exercise its declared route");
            long[] deltaCounts=new long[before.length];for(int i=0;i<before.length;i++)deltaCounts[i]=after[i]-before[i];result.put("counterDeltas",deltaCounts);
            if(REFERENCE!=null){Map<?,?> baselineCase=(Map<?,?>)referenceReport.get(key);Map<?,?> baselineFov=(Map<?,?>)baselineCase.get("nativeFov");require(baselineFov!=null&&((Number)baselineFov.get("currentRawBits")).longValue()==((Number)nativeFov.get("currentRawBits")).longValue()&&((Number)baselineFov.get("previousRawBits")).longValue()==((Number)nativeFov.get("previousRawBits")).longValue(),"Reference native FOV does not match the converged actual camera");BufferedImage released=ImageIO.read(Path.of(REFERENCE).resolve(key+"-native.png").toFile());recordPixels(result,"public072NativePixels",released,nativeImage,0);recordPixels(result,"public072GpuPixels",released,gpuImage,2);}
            if(HELD){require(!itemSamples[1].isEmpty()&&!itemSamples[2].isEmpty(),"Actual held-item draw entry was not observed");if(current().startsWith("fp-"))require(itemSamples[1].stream().anyMatch(sample->"FIRST_PERSON".equals(sample.get("transform"))),"Actual first-person item route was not observed");}
            result.put("productionQuadCompatibility",productionQuadMetrics());result.put("nativeFov",nativeFov);
            result.put("publicAnimationControls",animationControls());
            if(!CONTROL){
                List<Map<String,Object>> coverage=new ArrayList<>();result.put("visibleSubjectGpuCoverage",coverage);int visible=0;
                for(int subject=0;subject<traces[1].length;subject++){
                    long nativeEntries=traces[1][subject][COUNTERS.length],gpuEntries=traces[2][subject][COUNTERS.length];boolean canceled=subject==0&&current().startsWith("forge-cancel");
                    long nativeHeld=traces[1][subject][COUNTERS.length+2],gpuHeld=traces[2][subject][COUNTERS.length+2],gpuBodies=traces[2][subject][0]+traces[2][subject][1];
                    coverage.add(Map.of("subject",subject,"nativeRenderEntries",nativeEntries,"gpuRenderEntries",gpuEntries,"nativeHeldCallbacks",nativeHeld,"gpuHeldCallbacks",gpuHeld,"gpuBodyRouteCount",gpuBodies,"coverage",nativeEntries==0?"not visible in the actual native reference":"actual rendered subject","expectedCanceled",canceled));
                    require(nativeEntries==gpuEntries,"Native/GPU subject visibility or renderer entry count changed");
                    if(nativeEntries==0||canceled)continue;visible++;
                    require(gpuBodies>0,"Actually visible ordinary subject did not queue a GPU body path: "+subject);
                    require(nativeHeld>0&&gpuHeld>0,"Actually visible held-item layer callback was lost: "+subject);
                }
                require(visible>0,"No uncanceled native player subject was actually rendered");
                require(deltaCounts[6]>0&&deltaCounts[7]>0,"No actual GPU model expansion dispatch was observed");require(HELD||current().startsWith("forge-cancel")?traces[2][0][COUNTERS.length+1]==0:traces[2][0][COUNTERS.length+1]>0,"Actual self Cape callback did not preserve the current mode");if(VARIANT.equals("modern")&&!FORGE&&!current().equals("hurt"))require(deltaCounts[9]>0&&deltaCounts[10]>0,"Modern style retained its original layer without exercising the requested private adapter");
            }
            controlsEffect(mc,key,result,nativeImage,deadline,original);
            if(current().equals("bow")){firstCape=nativeImage;firstNames=nativeImage;}
            if(current().equals("cape-change")){result.put("presetChangedVisiblePixels",compare(firstCape,nativeImage,255).get("changedPixels"));require(((Number)result.get("presetChangedVisiblePixels")).intValue()>0,"Selected cape did not affect any actual scene pixel");firstNames=nativeImage;}
            if(current().equals("nick-change")){result.put("nickChangedVisiblePixels",compare(firstNames,nativeImage,255).get("changedPixels"));require(((Number)result.get("nickChangedVisiblePixels")).intValue()>0,"Changed Nick rule did not affect any actual scene pixel");}
            report.put(key,result);progress("validated-"+key);
            if(++scenario<cases().length){selectCase();return;}
            report.put("jointResidentModern",jointCoverage());Map<String,Object> quads=PlayerFeatures073QuadEvidence.summary();report.put("quadEvidence",quads);if(PlayerFeatures073QuadEvidence.enabled()){require(quads.get("failure")==null&&((Number)quads.get("nativeBuildsCaptured")).intValue()>0,"Native quad build observation failed or missed all cold mesh builds");if(PlayerFeatures073QuadEvidence.mode().equals("pack"))require(((Number)quads.get("officialGetterCalls")).longValue()>0&&quads.get("officialGetterCalls").equals(quads.get("getterReturnsMirroredInNativeBacking")),"Explicit pack diagnostic did not validate its official getter action");}
            Map<String,Object> production=productionQuadMetrics();report.put("productionQuadCompatibility",production);if(!CONTROL&&FORGE&&HELD){require(Boolean.TRUE.equals(production.get("available")),"Production quad helper is absent from the selected runtime");long prepared=((Number)production.get("materializedQuads")).longValue(),verified=((Number)production.get("verifiedQuads")).longValue(),fallback=((Number)production.get("nativeModelFallbacks")).longValue();require(prepared>0&&prepared==verified&&fallback==0,"Official Forge item profile bypassed verified native materialization: "+production);}phase=3;progress("all-pixel-cases-validated");
        }catch(Throwable error){fail(error);}finally{mode=0;rendering=-1;}
    }
    private static BufferedImage render(Minecraft mc,int which,boolean gpu,long deadline,Operation<Void> original)throws Exception{
        if(VOXEL_ISOLATION)PlayerFeatures073VoxelEvidence.begin(gpu&&!CONTROL?voxelVariant:"native");
        try{
        configure(gpu);freeze(mc);mode=which;itemSamples[which].clear();itemDepth=0;for(int i=0;i<traces[which].length;i++){Arrays.fill(traces[which][i],0);traceStates[which][i]=Map.of();}GL11.glClear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT);original.call(.5f,deadline);require(itemDepth==0,"Held-item observation scope leaked");require(GL11.glGetError()==GL11.GL_NO_ERROR,"OpenGL error after actual feature world draw");return capture(mc);
    
        }finally{if(VOXEL_ISOLATION)lastVoxelEvidence=PlayerFeatures073VoxelEvidence.end();}
    }

    public static int player(Entity entity){if(!enabled()||done||phase!=2||mode==0)return -1;int subject=-1;for(int i=0;i<actors.length;i++)if(entity==actors[i]){subject=i;break;}if(subject<0&&entity==Minecraft.getInstance().player)subject=2;if(subject>=0){rendering=subject;traces[mode][subject][COUNTERS.length]++;}return subject;}
    public static void playerEnd(int actor,long[] before){if(actor<0)return;long[] after=metrics();for(int i=0;i<after.length;i++)traces[mode][actor][i]+=after[i]-before[i];traceStates[mode][actor]=pipelineStates();rendering=-1;}
    public static long[] playerMetrics(){return metrics();}
    private static long[][] traceSnapshot(int which){long[][] value=new long[traces[which].length][];for(int i=0;i<value.length;i++)value[i]=traces[which][i].clone();return value;}
    private static Map<String,String> pipelineStates(){Map<String,String> values=new LinkedHashMap<>();for(String name:List.of("GpuPlayerPipeline","NativePlayerPosePipeline","GpuEquipmentPipeline","GpuVoxelSkinPipeline","ModernArmorGlintPipeline","CeraEmissiveAdmission"))try{values.put(name,String.valueOf(Class.forName("io.github.arcadiumclient.runtime.client.optimizations."+name).getMethod("status").invoke(null)));}catch(ReflectiveOperationException absent){values.put(name,CONTROL?"Not present in public072 control":absent.getClass().getSimpleName());}return values;}
    private static Map<String,Object> productionQuadMetrics(){try{Class<?> helper=Class.forName("io.github.arcadiumclient.runtime.client.optimizations.ForgeBakedItemVertices");return Map.of("available",true,"materializedQuads",helper.getField("materializedQuads").getLong(null),"verifiedQuads",helper.getField("verifiedQuads").getLong(null),"nativeModelFallbacks",helper.getField("nativeModelFallbacks").getLong(null));}catch(ClassNotFoundException absent){return Map.of("available",false,"scope","Explicitly absent from public072 reference");}catch(ReflectiveOperationException error){throw new IllegalStateException("Production item metric schema changed",error);}}
    public static void cape(){if(enabled()&&!done&&phase==2&&mode!=0&&rendering>=0)traces[mode][rendering][COUNTERS.length+1]++;}
    public static void item(){if(enabled()&&!done&&phase==2&&mode!=0&&rendering>=0)traces[mode][rendering][COUNTERS.length+2]++;}
    public static int heldEnter(Entity entity,Object transform){
        if(!enabled()||done||!HELD||phase!=2)return 0;
        require(itemDepth<itemOwners.length,"Held-item scope budget exceeded");int at=itemDepth++;
        itemOwners[at]=entity==Minecraft.getInstance().player?"local-player":entity==actors[0]?"self-avatar":entity==actors[1]?"other-avatar":"untracked-entity";
        itemTransforms[at]=((Enum<?>)transform).name();cameraInputs[at]=null;legacyCalls[at]=legacyApplied[at]=0;return itemDepth;
    }
    public static void heldExit(int token){if(token==0)return;require(token==itemDepth,"Held-item observation scope order changed");int at=--itemDepth;itemOwners[at]=itemTransforms[at]=null;cameraInputs[at]=null;}
    public static void cameraInput(Object model){
        if(itemDepth==0||mode!=0&&itemSamples[mode].size()>=16)return;
        Map<String,Object> sample=new LinkedHashMap<>();sample.put("inputModel",model.getClass().getName());sample.put("inputPerspectiveAware",perspectiveAware(model.getClass()));if(mode!=0){sample.put("physicalBeforeCamera",matrix(GL11.GL_MODELVIEW_MATRIX));sample.put("captureBeforeCamera",captureMatrix());}cameraInputs[itemDepth-1]=sample;
    }
    public static void legacyTransform(boolean applied){if(itemDepth>0){legacyCalls[itemDepth-1]++;if(applied)legacyApplied[itemDepth-1]++;}}
    public static void itemDraw(Object model){
        if(itemDepth==0||mode==0||itemSamples[mode].size()>=16)return;int at=itemDepth-1;
        Map<String,Object> sample=new LinkedHashMap<>();sample.put("owner",itemOwners[at]);sample.put("transform",itemTransforms[at]);sample.put("drawModel",model.getClass().getName());sample.put("drawPerspectiveAware",perspectiveAware(model.getClass()));sample.put("cameraInput",cameraInputs[at]);sample.put("legacyFirstPersonCalls",legacyCalls[at]);sample.put("legacyFirstPersonApplied",legacyApplied[at]);sample.put("physicalAtDraw",matrix(GL11.GL_MODELVIEW_MATRIX));sample.put("projectionAtDraw",matrix(GL11.GL_PROJECTION_MATRIX));sample.put("captureAtDraw",captureMatrix());itemSamples[mode].add(sample);
    }
    public static boolean observingQuadBuild(){return enabled()&&!done&&HELD&&phase==2;}
    public static Map<String,Object> quadContext(){Map<String,Object> context=new LinkedHashMap<>();context.put("case",current());context.put("phase",mode==0?"native-warmup":mode==1?"native-pair":"paired-repeat");context.put("owner",itemDepth==0?"outside-held-scope":itemOwners[itemDepth-1]);context.put("transform",itemDepth==0?"none":itemTransforms[itemDepth-1]);context.put("cameraInput",itemDepth==0?null:cameraInputs[itemDepth-1]);context.put("capture",captureMatrix());return context;}
    private static boolean perspectiveAware(Class<?> type){if(type==null)return false;for(Class<?> iface:type.getInterfaces())if(iface.getName().equals("net.minecraftforge.client.model.IPerspectiveAwareModel")||perspectiveAware(iface))return true;return perspectiveAware(type.getSuperclass());}
    private static Map<String,Object> matrix(int name){matrixScratch.clear();GL11.glGetFloatv(name,matrixScratch);float[] values=new float[16];matrixScratch.get(values);return matrixValues(values);}
    private static Map<String,Object> matrixValues(float[] values){int[] bits=new int[16];for(int i=0;i<16;i++){require(Float.isFinite(values[i]),"Nonfinite actual item matrix");bits[i]=Float.floatToRawIntBits(values[i]);}return Map.of("values",values,"rawBits",bits);}
    private static Map<String,Object> captureMatrix(){
        try{
            Class<?> type=Class.forName("dev.rdh.argentum.impl.render.entity.instancing.EntityCapture");Object capture=type.getMethod("current").invoke(null);if(capture==null)return Map.of("active",false);
            Map<String,Object> result=new LinkedHashMap<>();result.put("active",true);for(String flag:List.of("item","player","armorLayer","suppressFixedFunction")){Field field=type.getDeclaredField(flag);field.setAccessible(true);result.put(flag,field.get(capture));}
            Field field=type.getDeclaredField("matrices");field.setAccessible(true);float[] values=new float[16];((org.joml.Matrix4fc)field.get(capture)).get(values);result.put("matrix",matrixValues(values));return result;
        }catch(ReflectiveOperationException error){return Map.of("observationError",error.getClass().getSimpleName());}
    }
    public static long clock(long original){return enabled()?1234567L:original;}
    public static long modernClock(long original,double speed){return enabled()?(long)(1234567L*speed*8):original;}
    private static Map<String,Object> jointCoverage(){boolean modern=Boolean.getBoolean("arcadium.modernGlint072.experimental"),resident=!System.getProperty("arcadium.residentBodies072.mode","off").equals("off");long drawn=metric("GpuResidentBodies.actorsDrawn");Map<String,Object> result=new LinkedHashMap<>();result.put("modernRequested",modern);result.put("residentRequested",resident);result.put("actualResidentActorsDrawn",drawn);result.put("qualified",modern&&resident&&drawn>0);result.put("coverageGap",modern&&resident&&drawn==0?"Resident body shader is explicitly skipped while the Modern adapter is enabled":"Joint private shader composition not exercised by this profile");return result;}

    public static void frame(){
        if(!enabled()||done)return;
        try{
            Minecraft mc=Minecraft.getInstance();
            if(phase==3){mc.openScreen(new net.minecraft.client.gui.screen.menu.GameMenuScreen());phase=4;menuFrames=0;progress("before-normal-menu");return;}
            if(phase==4){require(++menuFrames<240,"Normal pause menu did not become ready");require(mc.screen instanceof net.minecraft.client.gui.screen.menu.GameMenuScreen,"Normal pause menu was replaced");if(menuFrames<3||!mc.isPaused())return;var button=((io.github.arcadiumclient.runtime.mixin.client.access.ScreenAccess)mc.screen).arcadium$buttons().stream().filter(value->value.id==1&&value.active).findFirst().orElseThrow();((io.github.arcadiumclient.runtime.qa.mixin.ResolvedInstance072ScreenClick)mc.screen).resolved$click(button);if(mc.world!=null&&mc.screen!=null&&mc.screen.getClass().getName().equals("dev.microcontrollers.confirmdisconnect.ConfirmDisconnectScreen")){phase=5;menuFrames=0;return;}unloaded(mc);}
            if(phase==5){require(++menuFrames<240,"Normal confirmation did not become ready");require(mc.screen!=null&&mc.screen.getClass().getName().equals("dev.microcontrollers.confirmdisconnect.ConfirmDisconnectScreen"),"Normal confirmation was replaced");if(menuFrames<3||!mc.isPaused())return;var button=((io.github.arcadiumclient.runtime.mixin.client.access.ScreenAccess)mc.screen).arcadium$buttons().stream().filter(value->value.id==0&&value.active).findFirst();if(button.isEmpty())return;((io.github.arcadiumclient.runtime.qa.mixin.ResolvedInstance072ScreenClick)mc.screen).resolved$click(button.get());unloaded(mc);}
            if(phase==6)finish();
        }catch(Throwable error){failure=error.toString();finish();}
    }
    private static void unloaded(Minecraft mc)throws Exception{require(mc.world==null,"Normal menu did not unload the world");int auxiliary=0;if(!CONTROL)auxiliary=((Number)Class.forName("io.github.arcadiumclient.runtime.client.optimizations.GpuModelAuxiliaries").getMethod("size").invoke(null)).intValue();require(auxiliary==0,"Owned renderer auxiliaries survived world unload");report.put("normalUnload",Map.of("worldNull",true,"auxiliaries",auxiliary,"via","native pause menu and actual optional confirmation"));phase=6;}
    private static void seedSkins(Minecraft mc)throws Exception{
        Class<?> pixels=Class.forName("org.polyfrost.skinlayers.mesh.SkinPixels"),meshes=Class.forName("org.polyfrost.skinlayers.skin.SkinMeshes");Object companion=meshes.getField("Companion").get(null);Method build=companion.getClass().getMethod("build",pixels,boolean.class);Class<?> key=Class.forName("org.polyfrost.skinlayers.skin.SkinMeshCache$Key");Constructor<?> constructor=key.getDeclaredConstructor(Identifier.class,boolean.class);constructor.setAccessible(true);Field field=Class.forName("org.polyfrost.skinlayers.skin.SkinMeshCache").getDeclaredField("players");field.setAccessible(true);Map<Object,Object> cache=(Map<Object,Object>)field.get(null);
        for(int index=0;index<2;index++){Identifier id=index==0?SELF_SKIN:OTHER_SKIN;BufferedImage image=new BufferedImage(64,64,BufferedImage.TYPE_INT_ARGB);for(int y=0;y<64;y++)for(int x=0;x<64;x++)image.setRGB(x,y,index==0?(((x/4+y/4)&1)==0?0xff774433:0xff996644):(((x/4+y/4)&1)==0?0xff335577:0xff557799));mc.getTextureManager().register(id,new UploadedSkinTexture(image));Object data=pixels.getConstructor(int.class,int.class,int[].class).newInstance(64,64,image.getRGB(0,0,64,64,null,0,64));for(boolean slim:new boolean[]{false,true})cache.put(constructor.newInstance(id,slim),build.invoke(companion,data,slim));}
    }
    private static BufferedImage capture(Minecraft mc){int previous=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);IntBuffer pixels=BufferUtils.createIntBuffer(mc.width*mc.height);try{GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,mc.getRenderTarget().frameBufferId);GL11.glReadPixels(0,0,mc.width,mc.height,GL12.GL_BGRA,GL12.GL_UNSIGNED_INT_8_8_8_8_REV,pixels);}finally{GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,previous);}BufferedImage image=new BufferedImage(mc.width,mc.height,BufferedImage.TYPE_INT_ARGB);int[] row=new int[mc.width];for(int y=0;y<mc.height;y++){pixels.position((mc.height-1-y)*mc.width);pixels.get(row);image.setRGB(0,y,mc.width,1,row,0,mc.width);}return image;}
    private static Map<String,Object> compare(BufferedImage first,BufferedImage second,int tolerance){require(first!=null&&second!=null&&first.getWidth()==second.getWidth()&&first.getHeight()==second.getHeight(),"Frame dimensions differ");int changed=0,above=0,maximum=0;for(int y=0;y<first.getHeight();y++)for(int x=0;x<first.getWidth();x++){int a=first.getRGB(x,y),b=second.getRGB(x,y),difference=0;for(int shift=0;shift<=24;shift+=8)difference=Math.max(difference,Math.abs((a>>>shift&255)-(b>>>shift&255)));if(difference>0)changed++;if(difference>tolerance)above++;maximum=Math.max(maximum,difference);}return Map.of("maximumChannelError",maximum,"changedPixels",changed,"pixelsAboveTolerance",above,"tolerance",tolerance);}
    private static void recordPixels(Map<String,Object> result,String name,BufferedImage first,BufferedImage second,int tolerance){Map<String,Object> pixels=compare(first,second,tolerance);result.put(name,pixels);require(((Number)pixels.get("pixelsAboveTolerance")).intValue()==0,"Full RGBA mismatch "+name+": "+pixels);}
    private static Path output(){return Path.of(System.getProperty("arcadium.playerFeatures073.output")).toAbsolutePath();}
    private static Map<?,?> read(Path path)throws Exception{return new com.google.gson.Gson().fromJson(Files.readString(path),Map.class);}
    private static void progress(String stage)throws Exception{report.put("stage",stage);Files.createDirectories(output().getParent());Files.writeString(output().resolveSibling("progress.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report));}
    private static void fail(Throwable error){if(failure==null){failure=error.toString();error.printStackTrace();}try{progress("failed");}catch(Exception ignored){}Minecraft mc=Minecraft.getInstance();if(mc.world!=null)phase=3;else finish();}
    private static void restoreAnimationControls()throws Exception{
        if(!ANIMATION_CONTROLS.equals("default")){animationProperty("itemScaleX",oldBadlionScaleX);animationProperty("itemScaleY",oldBadlionScaleY);animationProperty("itemScaleZ",oldBadlionScaleZ);animationProperty("mode",oldAnimationMode);animationProperty("enabled",oldAnimations);refreshAnimationCategories();for(var entry:originalItemControls.entrySet())itemControl(entry.getKey(),entry.getValue());}
        else{AnimationsConfig.setEnabled(oldAnimations);AnimationsConfig.setMode(oldAnimationMode);}
        glintField.set(animationItems,oldGlint);sneakField.set(animationMovement,oldSneak);wavyField.setBoolean(animationMovement,oldWavy);blockingField.setBoolean(animationOther,oldBlocking);
        require(AnimationsConfig.getEnabled()==oldAnimations&&AnimationsConfig.getMode()==oldAnimationMode,"Public Animations mode was not restored");
        if(!ANIMATION_CONTROLS.equals("default")){require(AnimationsConfig.getItemScaleX()==oldBadlionScaleX&&AnimationsConfig.getItemScaleY()==oldBadlionScaleY&&AnimationsConfig.getItemScaleZ()==oldBadlionScaleZ,"Public Badlion scales were not restored");for(var entry:originalItemControls.entrySet())require(Objects.equals(animationItems.getClass().getField(entry.getKey()).get(animationItems),entry.getValue()),"Public Overflow field was not restored: "+entry.getKey());}
    }
    private static void finish(){if(done)return;done=true;try{if(saved){NickHiderConfig.setEnabled(oldNick);NickHiderConfig.setNickname(oldNickname);NickHiderConfig.setReplacementRulesJson(oldRules);NickHider.settingsChanged();CapeConfig.setEnabled(oldCape);CapeConfig.setPreset(oldPreset);LocalCape.changed();Skin3DConfig.INSTANCE.chooseEnabled(oldSkin);restoreAnimationControls();}if(probe!=null){probe.getMethod("reset").invoke(null);probe.getField("target").set(null,null);}report.put("publicSettingsRestored",true);}catch(Throwable error){if(failure==null)failure=error.toString();}report.put("passed",failure==null);if(failure!=null)report.put("error",failure);try{Files.createDirectories(output().getParent());Files.writeString(output(),new GsonBuilder().setPrettyPrinting().create().toJson(report));}catch(Exception error){error.printStackTrace();}Minecraft.getInstance().stop();}
    private static void require(boolean value,String reason){if(!value)throw new IllegalStateException(reason);}
    private static final class Actor extends RemoteClientPlayerEntity{
        final int index;Actor(Minecraft mc,GameProfile profile,int index){super(mc.world,profile);this.index=index;}
        void nativeTick(){super.tick();}
        @Override public void tick(){super.tick();ticks=160;}
        @Override public Identifier getSkinTextureLocation(){return index==0?SELF_SKIN:OTHER_SKIN;}
    }
}
