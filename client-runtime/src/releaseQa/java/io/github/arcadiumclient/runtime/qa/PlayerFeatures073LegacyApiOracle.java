// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa;

import com.google.gson.GsonBuilder;
import java.lang.invoke.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.model.block.BakedModel;
import net.minecraft.client.resource.model.ModelTransformations;
import net.minecraft.item.ItemStack;
import io.github.arcadiumclient.runtime.client.features.AnimationsConfig;

public final class PlayerFeatures073LegacyApiOracle {
    private static final String MODEL="net.minecraftforge.client.model.ItemLayerModel$BakedItemModel";
    private static final boolean REFERENCE=Boolean.getBoolean("arcadium.playerFeatures073.upstreamLegacyReference");
    private static final ArrayDeque<Scope> scopes=new ArrayDeque<>();
    private static final List<Map<String,Object>> examples=new ArrayList<>();
    private static final List<String> failures=new ArrayList<>();
    private static final Set<String> exampleKinds=new HashSet<>();
    private static long calls,applied,scopeCount,perspectives,rights,lefts,multiplyBodies,compatCalls,compatApplied;private static boolean finished,agentBound;
    private PlayerFeatures073LegacyApiOracle(){}
    private static final class Scope {
        ItemStack stack;BakedModel input,camera;ModelTransformations.Type type;int cameraDepth,callbacks,right,left,multiply,body,compat,compatUsed,api,used;Object matrix;boolean rightNull,nested;
    }
    public static int enter(ItemStack stack,BakedModel model,ModelTransformations.Type type){
        if(!syntheticWorld()||type!=ModelTransformations.Type.FIRST_PERSON&&type!=ModelTransformations.Type.THIRD_PERSON)return 0;
        bindAgent();if(REFERENCE&&!referenceFlags())throw new IllegalStateException("Upstream API reference is outside its owned public072 Forge scope");if(scopes.size()==8)throw new IllegalStateException("Upstream reference scope budget exceeded");Scope scope=new Scope();scope.stack=stack;scope.input=model;scope.type=type;scopes.push(scope);return scopes.size();
    }
    public static void leave(int token){if(token==0)return;if(scopes.size()!=token)throw new IllegalStateException("Upstream reference item scope order changed");Scope scope=scopes.pop();scopeCount++;if(scope.camera!=null&&scope.camera.getClass().getName().equals(MODEL)&&!scope.nested){int expectedRight=scope.rightNull?1:2;if(scope.callbacks!=1||scope.right!=expectedRight||scope.left!=1)fail("Actual canonical perspective or Pair getter counts changed");}
        String item=scope.stack==null?"null":scope.stack.getItem().getClass().getName(),kind=scope.type.name()+":"+item+":"+scope.api+":"+scope.used+":"+scope.callbacks+":"+scope.right+":"+scope.left;
        if(examples.size()<64&&exampleKinds.add(kind)){Map<String,Object> value=new LinkedHashMap<>();value.put("type",scope.type.name());value.put("itemClass",item);value.put("callerModel",scope.input==null?"null":scope.input.getClass().getName());value.put("cameraModel",scope.camera==null?"none":scope.camera.getClass().getName());value.put("sameInputIdentity",scope.input==scope.camera);value.put("perspectiveCalls",scope.callbacks);value.put("pairRightCalls",scope.right);value.put("pairLeftCalls",scope.left);value.put("multiplyMethodEntries",scope.multiply);value.put("matrixBodyEntries",scope.body);value.put("compatBridgeCalls",scope.compat);value.put("compatBridgeApplied",scope.compatUsed);value.put("referenceCalls",scope.api);value.put("referenceApplied",scope.used);value.put("nativeNestedFallback",scope.nested);examples.add(value);}}
    public static int cameraEnter(Object model,Object type){if(scopes.isEmpty())return 0;Scope scope=scopes.peek();if(scope.type!=type)return 0;scope.cameraDepth++;if(scope.cameraDepth!=1)scope.nested=true;else if(model instanceof BakedModel baked)scope.camera=baked;return scope.cameraDepth;}
    public static void cameraExit(int token){if(token==0)return;if(scopes.isEmpty()||scopes.peek().cameraDepth!=token)throw new IllegalStateException("Upstream reference camera scope order changed");scopes.peek().cameraDepth--;}
    public static void perspective(Object model,Object type){if(scopes.isEmpty())return;Scope scope=scopes.peek();if(scope.camera==model&&scope.type==type){scope.callbacks++;perspectives++;}}
    public static void right(Object value){if(scopes.isEmpty())return;Scope scope=scopes.peek();if(scope.cameraDepth==0)return;scope.right++;rights++;if(scope.right==1)scope.rightNull=value==null;scope.matrix=value;}
    public static void left(Object value){if(scopes.isEmpty())return;Scope scope=scopes.peek();if(scope.cameraDepth!=0){scope.left++;lefts++;}}
    public static boolean referenceTransform(Object matrix){
        if(scopes.isEmpty())return false;Scope scope=scopes.peek();
        if(!REFERENCE||!referenceFlags()||!AnimationsConfig.getEnabled()||AnimationsConfig.getMode()!=0||scope.nested||scope.cameraDepth!=1||scope.type!=ModelTransformations.Type.FIRST_PERSON||scope.camera==null||!scope.camera.getClass().getName().equals(MODEL)||scope.stack==null||scope.matrix!=matrix||matrix==null||!matrix.getClass().getName().equals("javax.vecmath.Matrix4f")||scope.right!=2||scope.left!=0||scope.callbacks!=1)return false;
        if(scope.camera.isGui3d()||scope.camera.isCustomRenderer())return false;
        scope.api++;calls++;
        try{boolean result=(boolean)Api.METHOD.invokeExact(scope.stack,scope.camera);if(result){scope.used++;applied++;}return result;}catch(Throwable failure){fail("Original LegacyFirstPerson API threw "+failure.getClass().getName());throw new IllegalStateException("Original upstream API reference failed",failure);}
    }
    public static Object agentEvent(int event,Object a,Object b,Object c){
        switch(event){case 0:return Integer.valueOf(cameraEnter(a,b));case 1:cameraExit(((Integer)a).intValue());break;case 2:perspective(a,b);break;case 3:right(a);break;case 4:left(a);break;case 5:if(!scopes.isEmpty())scopes.peek().multiply++;break;case 6:if(!scopes.isEmpty()){scopes.peek().body++;multiplyBodies++;}break;case 7:if(!scopes.isEmpty()){scopes.peek().compat++;compatCalls++;if(Boolean.TRUE.equals(a)){scopes.peek().compatUsed++;compatApplied++;}}break;default:throw new IllegalArgumentException("Unknown bounded agent event");}return null;
    }
    private static void bindAgent(){if(agentBound)return;try{Class<?> bridge=Class.forName("arcadium.qa.legacy.bootstrap.LegacyProbeBridge",true,null);if(bridge.getClassLoader()!=null)throw new IllegalStateException("QA bridge is not bootstrap defined");MethodHandles.Lookup lookup=MethodHandles.lookup();MethodHandle observer=lookup.findStatic(PlayerFeatures073LegacyApiOracle.class,"agentEvent",MethodType.methodType(Object.class,int.class,Object.class,Object.class,Object.class));bridge.getMethod("registerObserver",MethodHandle.class).invoke(null,observer);if(REFERENCE){MethodHandle provider=lookup.findStatic(PlayerFeatures073LegacyApiOracle.class,"referenceTransform",MethodType.methodType(boolean.class,Object.class));bridge.getMethod("registerReference",MethodHandle.class).invoke(null,provider);}agentBound=true;}catch(ReflectiveOperationException failure){throw new IllegalStateException("Static QA agent registration failed",failure);}}
    private static Map<?,?> agentSnapshot(){try{Class<?> bridge=Class.forName("arcadium.qa.legacy.bootstrap.LegacyProbeBridge",false,null);return (Map<?,?>)bridge.getMethod("snapshot").invoke(null);}catch(ReflectiveOperationException failure){fail("Static agent snapshot unavailable");return Map.of();}}
    private static final class Api {
        static final MethodHandle METHOD=resolve();
        private static MethodHandle resolve(){try{Class<?> type=Class.forName("org.polyfrost.overflowanimations.legacy.firstperson.LegacyFirstPerson");return MethodHandles.publicLookup().unreflect(type.getMethod("applyFirstPersonTransform",ItemStack.class,BakedModel.class)).asType(MethodType.methodType(boolean.class,ItemStack.class,BakedModel.class));}catch(ReflectiveOperationException failure){throw new IllegalStateException("Original LegacyFirstPerson API is unavailable",failure);}}
    }
    private static boolean syntheticWorld(){if(!PlayerFeatures073Qa.enabled())return false;Minecraft mc=Minecraft.getInstance();return Boolean.getBoolean("arcadium.playerFeatures073.staticAgentOwned")&&mc.world!=null&&mc.getCurrentServerEntry()==null;}
    private static boolean referenceFlags(){return Boolean.getBoolean("arcadium.playerFeatures073.public072")&&Boolean.getBoolean("arcadium.playerFeatures073.forge")&&Boolean.getBoolean("arcadium.forge.prepared")&&Boolean.getBoolean("arcadium.playerFeatures073.legacyPositionToggle")&&!Boolean.getBoolean("arcadium.playerFeatures073.voxelIsolation")&&!Boolean.getBoolean("arcadium.playerFeatures073.raven")&&System.getProperty("arcadium.playerFeatures073.variant","").equals("held")&&System.getProperty("arcadium.playerFeatures073.heldProfile","").equals("animations")&&System.getProperty("arcadium.playerFeatures073.animationControls","").equals("overflow-custom");}
    public static void finish(){if(!PlayerFeatures073Qa.enabled()||finished)return;finished=true;if(!scopes.isEmpty())fail("Reference scope leaked");if(scopeCount==0||perspectives==0||rights==0||lefts==0)fail("No actual perspective/Pair callback activity was observed");if(REFERENCE&&(calls==0||applied==0||!referenceFlags()))fail("Explicit original-API reference did not execute");if(!REFERENCE&&calls!=0)fail("Current candidate invoked the independent reference API");Map<String,Object> result=new LinkedHashMap<>();result.put("passed",failures.isEmpty());Map<?,?> agent=agentSnapshot();if(!Boolean.TRUE.equals(agent.get("observerBound"))||!Boolean.valueOf(REFERENCE).equals(agent.get("referenceProviderBound"))||!Boolean.valueOf(REFERENCE).equals(agent.get("referenceMode"))||!List.of().equals(agent.get("errors")))fail("Bootstrap provider role or loader-safe observer failed");if(compatCalls==0||REFERENCE&&compatApplied!=0||!REFERENCE&&compatApplied==0)fail("Actual optional compat bridge did not preserve the selected reference/candidate role");result.put("passed",failures.isEmpty());result.put("agent",agent);result.put("matrixBodyEntries",multiplyBodies);result.put("compatBridgeCalls",compatCalls);result.put("compatBridgeApplied",compatApplied);result.put("referenceFlag",REFERENCE);result.put("referenceCalls",calls);result.put("referenceApplied",applied);result.put("actualItemScopes",scopeCount);result.put("perspectiveCalls",perspectives);result.put("pairRightCalls",rights);result.put("pairLeftCalls",lefts);result.put("failures",new ArrayList<>(failures));result.put("examples",new ArrayList<>(examples));result.put("api","org.polyfrost.overflowanimations.legacy.firstperson.LegacyFirstPerson.applyFirstPersonTransform");result.put("productionHelperCalledByReference",false);try{Path output=Path.of(System.getProperty("arcadium.playerFeatures073.output")).toAbsolutePath().resolveSibling("legacy-upstream-api-evidence.json");Files.writeString(output,new GsonBuilder().setPrettyPrinting().create().toJson(result));}catch(Exception error){throw new IllegalStateException("Cannot preserve upstream reference evidence",error);}}
    private static void fail(String value){if(failures.size()<16)failures.add(value);}
}
