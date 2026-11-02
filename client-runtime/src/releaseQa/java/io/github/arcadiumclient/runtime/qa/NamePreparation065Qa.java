// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa;

import com.google.gson.GsonBuilder;
import com.mojang.authlib.GameProfile;
import io.github.arcadiumclient.runtime.client.ClientPreferences;
import io.github.arcadiumclient.runtime.client.branding.NametagLayout;
import io.github.arcadiumclient.runtime.client.features.BetaOptimizationsConfig;
import io.github.arcadiumclient.runtime.client.features.NickHiderConfig;
import io.github.arcadiumclient.runtime.client.optimizations.GpuNameTags;
import io.github.arcadiumclient.runtime.client.optimizations.RetainedNameTags;
import io.github.arcadiumclient.runtime.client.presence.ArcadiumPresence;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.living.player.RemoteClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.living.ArmorStandEntity;
import net.minecraft.scoreboard.team.Team;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.gen.WorldGeneratorType;
import org.joml.Matrix4fc;
import org.lwjgl.opengl.GL11;

public final class NamePreparation065Qa {
    private static final boolean COMPACT=Boolean.getBoolean("arcadium.compactNames066");
    private record Packet(int[] source, int[] layout, Object[] pass, float[] matrix) { }
    private static final Set<Entity> actors = Collections.newSetFromMap(new IdentityHashMap<>());
    private static final List<Frozen> players = new ArrayList<>();
    private static final List<ArmorStandEntity> stands = new ArrayList<>();
    private static final List<Packet> referencePackets = new ArrayList<>(), actualPackets = new ArrayList<>();
    private static final List<String> referenceText = new ArrayList<>(), actualText = new ArrayList<>();
    private static final Set<Integer> options = new LinkedHashSet<>();
    private static final Map<String, Object> report = new LinkedHashMap<>();
    private static Field[] passFields;
    private static Team team;
    private static Entity current;
    private static boolean reference, done;
    private static int phase, ticks, wait, option = -1, referenceAssemblies, actualAssemblies;
    private static long pairs, packets, textChecks, assembliesSaved, badgeChecks;
    private static double matrixError;
    private static String failure;

    private NamePreparation065Qa() { }
    public static boolean enabled() { return System.getProperty("arcadium.namePreparation065.output") != null; }

    public static boolean beginPair(Entity entity) {
        if (phase != 2 || current != null || !actors.contains(entity)) return false;
        current = entity;
        reference = true;
        referenceAssemblies = actualAssemblies = 0;
        referencePackets.clear(); actualPackets.clear(); referenceText.clear(); actualText.clear();
        return true;
    }
    public static void actual() { reference = false; }
    public static void assembly() { if (current != null) { if (reference) referenceAssemblies++; else actualAssemblies++; } }
    public static void text(String kind, String value) {
        if (current == null) return;
        (reference ? referenceText : actualText).add(kind + ":" + value);
        if (kind.equals("badge") && value.equals("true")) badgeChecks++;
    }
    public static boolean packet(Object pass, int[] source, int length, int stride, int color, boolean text, int alpha,
                                 Matrix4fc matrix, float x, float y, float u, float v) {
        if (current == null) return false;
        try {
            if (passFields == null) {
                String[] names = {"texture", "depthTest", "depthMask", "depthFunc", "blend", "sourceRgb", "destinationRgb", "sourceAlpha", "destinationAlpha", "alphaTest", "alphaFunc", "alphaRef", "cull"};
                passFields = new Field[names.length];
                for (int i = 0; i < names.length; i++) { passFields[i] = pass.getClass().getDeclaredField(names[i]); passFields[i].setAccessible(true); }
            }
            Object[] state = new Object[passFields.length];
            for (int i = 0; i < state.length; i++) state[i] = passFields[i].get(pass);
            float[] transform = new float[20]; matrix.get(transform);
            transform[16] = x; transform[17] = y; transform[18] = u; transform[19] = v;
            (reference ? referencePackets : actualPackets).add(new Packet(Arrays.copyOf(source, length), new int[]{stride, color, text ? 1 : 0, alpha}, state, transform));
            return reference;
        } catch (Throwable error) { failure = error.toString(); throw new IllegalStateException(error); }
    }
    public static void endPair() {
        try {
            require(referenceText.equals(actualText), "Prepared name/badge/font text changed: " + referenceText + " != " + actualText);
            require(referencePackets.size() == actualPackets.size(), "Native name packet count changed");
            for (int i = 0; i < referencePackets.size(); i++) {
                Packet a = referencePackets.get(i), b = actualPackets.get(i);
                require(Arrays.equals(a.source, b.source) && Arrays.equals(a.layout, b.layout) && Arrays.equals(a.pass, b.pass), "Native name geometry/material/alpha/order changed at packet " + i);
                for (int axis = 0; axis < a.matrix.length; axis++) matrixError = Math.max(matrixError, Math.abs(a.matrix[axis] - b.matrix[axis]));
                require(matrixError < .0001, "Native name transform changed: " + matrixError);
                packets++;
            }
            textChecks += actualText.size();
            if (!current.isSneaking()) {
                require(referenceAssemblies > actualAssemblies, "Normal name did not remove redundant display-name assembly: reference="
                        + referenceAssemblies + " actual=" + actualAssemblies + " actor=" + current.getClass().getName());
                assembliesSaved += referenceAssemblies - actualAssemblies;
            }
            pairs++;
        } catch (Throwable error) { failure = error.toString(); }
        finally { current = null; }
    }

    public static void tick() {
        if (done) return;
        Minecraft mc = Minecraft.getInstance();
        try {
            require(++ticks < 2200, "Name preparation QA timed out at phase " + phase);
            require(failure == null, failure == null ? "" : failure);
            require(RetainedSceneQa.failure == null, RetainedSceneQa.failure == null ? "" : RetainedSceneQa.failure);
            wait++;
            if (phase == 0 && ticks > 60 && mc.screen != null && mc.screen.getClass().getSimpleName().contains("MainMenu")) {
                ClientPreferences.setSetupComplete(true);
                BetaOptimizationsConfig.setGpuPlayerPipeline(false); BetaOptimizationsConfig.setGpuModelTransforms(false);
                BetaOptimizationsConfig.setGpuParticles(false); BetaOptimizationsConfig.setGpuNameTags(true);
                BetaOptimizationsConfig.setMinimumGpuLabels(1); BetaOptimizationsConfig.setRetainedNameTagScenes(false);
                BetaOptimizationsConfig.setCompactNameTagPreparation(COMPACT);
                NametagLayout.optimized = true;
                mc.options.pauseOnUnfocus = false; mc.options.vsync = false; mc.options.fpsLimit = 180; mc.options.viewDistance = 3;
                mc.startGame("arcadium-name-preparation065", "Name Preparation QA", new WorldSettings(65065, WorldSettings.GameMode.CREATIVE, false, false, WorldGeneratorType.FLAT).enableCommands());
                phase = 1; wait = 0;
            } else if (phase == 1 && mc.world != null && mc.player != null && wait > 60) {
                mc.openScreen(null); mc.player.abilities.flying = true;
                verifyScopeHolder();
                mc.player.updatePositionAndAngles(0, 5, -10, 0, 4);
                team = mc.world.getScoreboard().addTeam("prep065");
                for (int i = 0; i < 13; i++) {
                    Frozen player = new Frozen(mc.world, new GameProfile(new UUID(0x1234000000004000L + i, 0x8000000000000065L), i == 0 ? "ArcadiumTest" : "PrepareQA" + i));
                    player.updatePositionAndAngles((i - 6) * 1.2, 4, 4, 180, 0);
                    player.lastYaw = player.yaw; player.lastPitch = player.pitch;
                    mc.world.forceEntity(65000 + i, player);
                    mc.world.getScoreboard().addMemberToTeam(player.getGameProfile().getName(), team.getName());
                    players.add(player); actors.add(player);
                }
                for (int i = 0; i < 91; i++) {
                    ArmorStandEntity stand = new ArmorStandEntity(mc.world, (i % 13 - 6) * 1.2, 5 + i / 13 * .4, 5 + i / 13 * 1.1);
                    stand.setInvisible(true); stand.setCustomName("§bPreparation " + i + " §7[QUEST]  §r"); stand.setCustomNameVisible(true);
                    mc.world.forceEntity(66000 + i, stand); stands.add(stand); actors.add(stand);
                }
                report.put("syntheticPlayers", 13); report.put("syntheticLabels", actors.size());
                phase = 2; wait = 0;
            } else if (phase == 2) {
                int next = wait / 18;
                if (next >= 9) {
                    require(pairs > 1000 && packets > 2000 && textChecks > 2000, "Insufficient same-frame native name comparisons");
                    require(assembliesSaved > 1000 && badgeChecks > 0, "Redundant preparation removal/badge validation did not run");
                    resetOptions();
                    report.put("nativePairs", pairs); report.put("nativePackets", packets); report.put("preparedTextChecks", textChecks);
                    report.put("displayNameAssembliesRemoved", assembliesSaved); report.put("badgeChecks", badgeChecks);
                    report.put("maximumMatrixError", matrixError); report.put("optionsCompared", options);
                    System.setProperty("arcadium.nextRendering.focus", "scene-verify");
                    RetainedSceneQa.checks = 0; RetainedSceneQa.error = 0;
                    BetaOptimizationsConfig.setRetainedNameTagScenes(true); RetainedNameTags.invalidate();
                    phase = 3; wait = 0;
                } else if (next != option) { option = next; mutate(next); options.add(next); }
            } else if (phase == 3) {
                RetainedSceneQa.tick(mc);
                if (wait > 205) {
                    require(RetainedSceneQa.checks > 4000, "Insufficient retained/native packet comparisons");
                    require(RetainedNameTags.passBindingHits > RetainedNameTags.passBindingsResolved, "Native pass bindings were not reused across labels");
                    report.put("retainedNativeChecks", RetainedSceneQa.checks);
                    report.put("retainedMatrixError", RetainedSceneQa.error);
                    report.put("passBindingHits", RetainedNameTags.passBindingHits);
                    report.put("passBindingsResolved", RetainedNameTags.passBindingsResolved);
                    report.put("compactPreparation",COMPACT);
                    if(COMPACT){
                        Object transformCache=field(RetainedNameTags.class,"transforms");
                        long linearReuses=transformCache.getClass().getField("linearReuses").getLong(transformCache);
                        long basisUpdates=transformCache.getClass().getField("basisUpdates").getLong(transformCache);
                        require(linearReuses>1000,"Exact input transforms were not reused");
                        report.put("exactLinearReuses",linearReuses);report.put("billboardBasisUpdates",basisUpdates);
                    }
                    report.put("retainedOptions", RetainedSceneQa.testedOptions());
                    RetainedSceneQa.verifyRendererReplacement();
                    Field cache = RetainedNameTags.class.getDeclaredField("framePasses"); cache.setAccessible(true);
                    require(((Map<?, ?>) cache.get(null)).isEmpty(), "Resource/renderer cleanup retained native pass bindings");
                    report.put("rendererAndResourceInvalidation", true);
                    finish(null); return;
                }
            }
            if (mc.world != null) {
                mc.world.setTimeOfDay(6000);
                for (ArmorStandEntity stand : stands) { stand.velocityX = stand.velocityY = stand.velocityZ = 0; }
                if (mc.player != null) { mc.player.yaw = (float) Math.sin(ticks * .011) * 7; mc.player.lastYaw = mc.player.yaw; mc.player.pitch = 4; mc.player.lastPitch = 4; }
            }
        } catch (Throwable error) { finish(error); }
    }

    private static void verifyScopeHolder()throws Exception {
        Field context=NametagLayout.class.getDeclaredField("CURRENT");context.setAccessible(true);
        ThreadLocal<?> local=(ThreadLocal<?>)context.get(null);Object holder=local.get();
        NametagLayout.Scope outer=NametagLayout.begin(null,"§aOuter label");
        NametagLayout.Scope nested=NametagLayout.begin(null,"§bNested label");
        try{
            require(NametagLayout.display("§bNested label").equals("§bNested label"),"Nested name scope lost its label");
            require(nested!=null&&nested.label().equals("§aOuter label"),"Outer immutable name scope changed");
            NametagLayout.end(nested);
            require(NametagLayout.display("§aOuter label").equals("§aOuter label"),"Outer name scope was not restored");
        }finally{NametagLayout.end(outer);}
        for(int i=0;i<256;i++){NametagLayout.Scope previous=NametagLayout.begin(null,"§fEntry reuse "+i);NametagLayout.end(previous);require(local.get()==holder,"Name preparation replaced its ThreadLocal holder");}
        Field current=holder.getClass().getDeclaredField("current");current.setAccessible(true);
        require(current.get(holder)==null,"Completed name scope retained its entity or label");
        report.put("immutableNestedScopesAndStableThreadLocalHolder",true);
    }

    private static void mutate(int step) throws Exception {
        resetOptions();
        team.setPrefix(step == 1 ? "§d[MVP+] " : "§a"); team.setSuffix(step == 2 ? " §7[TEAM]" : "");
        if (step == 3) for (int i = 0; i < stands.size(); i++) stands.get(i).setCustomName("§eUpdated " + i + "   §r");
        if (step == 4) { NickHiderConfig.setNickname("NameAlias"); NickHiderConfig.setEnabled(true); }
        if (step == 5) for (int i = 0; i < players.size(); i++) players.get(i).setSneaking(i % 2 == 0);
        if (step == 6) set("org.polyfrost.oneconfig.internal.OneConfigConfig", "useInterInGame", true);
        if (step == 7) { set("org.polyfrost.polynametag.client.PolyNametagConfig", "rounded", true); set("org.polyfrost.polynametag.client.PolyNametagConfig", "paddingX", 2f); }
        if (step == 8) for (int i = 0; i < stands.size(); i++) stands.get(i).setCustomName("§9Color " + i + " §lBOLD§r");
        Object snapshot = field(ArcadiumPresence.class, "snapshot");
        var type = snapshot.getClass();
        type.getMethod("begin", long.class).invoke(snapshot, 65L);
        type.getMethod("watch", long.class, Collection.class).invoke(snapshot, 65L, List.of(players.get(0).getUuid()));
        type.getMethod("accept", long.class, List.class).invoke(snapshot, 65L, List.of(players.get(0).getUuid().toString().replace("-", "")));
    }
    private static void resetOptions() throws Exception {
        NickHiderConfig.setEnabled(false);
        set("org.polyfrost.oneconfig.internal.OneConfigConfig", "useInterInGame", false);
        set("org.polyfrost.polynametag.client.PolyNametagConfig", "rounded", false);
        set("org.polyfrost.polynametag.client.PolyNametagConfig", "paddingX", 0f);
        for (Frozen player : players) player.setSneaking(false);
    }
    private static void set(String name, String field, Object value) throws Exception { Field target = Class.forName(name).getDeclaredField(field); target.setAccessible(true); target.set(null, value); }
    private static Object field(Class<?> owner, String name) throws Exception { Field field = owner.getDeclaredField(name); field.setAccessible(true); return field.get(null); }
    private static void finish(Throwable error) {
        if (done) return; done = true; current = null;
        report.put("passed", error == null);
        if (error != null) { report.put("error", error.toString()); error.printStackTrace(); }
        report.put("glError", GL11.glGetError());
        try { Path path = Path.of(System.getProperty("arcadium.namePreparation065.output")); Files.createDirectories(path.toAbsolutePath().getParent()); Files.writeString(path, new GsonBuilder().setPrettyPrinting().create().toJson(report)); }
        catch (Exception failure) { failure.printStackTrace(); }
        Minecraft.getInstance().stop();
    }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
    private static final class Frozen extends RemoteClientPlayerEntity {
        Frozen(net.minecraft.world.World world, GameProfile profile) { super(world, profile); }
        @Override public void tick() { }
    }
}
