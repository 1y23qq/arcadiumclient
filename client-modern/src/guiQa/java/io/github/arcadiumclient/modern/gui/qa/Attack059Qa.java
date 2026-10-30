package io.github.arcadiumclient.modern.gui.qa;

import com.google.gson.GsonBuilder;
import io.github.arcadiumclient.modern.client.ClientPreferences;
import io.github.arcadiumclient.modern.client.features.AttackParticleEffects;
import io.github.arcadiumclient.modern.gui.ArcadiumCreateWorldScreen;
import io.github.arcadiumclient.modern.gui.ArcadiumMainMenu;
import io.github.arcadiumclient.modern.gui.qa.mixin.Attack059PlayerAccess;
import io.github.arcadiumclient.modern.mixin.PlayerSweepAccess;
import io.github.arcadiumclient.modern.mixin.gui.CreateWorldAccess;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState.SelectedGameMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;

public final class Attack059Qa {
    public static int criticalEmitters;
    private int ticks, phase;
    private final Path output;
    private final Map<String, Object> report = new LinkedHashMap<>();
    Attack059Qa(Path output) { this.output = output; }
    private static void require(boolean value, String message) { if (!value) throw new IllegalStateException(message); }

    boolean tick(Minecraft mc) throws Exception {
        QaRuntimeErrors.check();
        require(++ticks < 2200, "Attack QA timed out in phase " + phase);
        if (mc.getOverlay() != null) return false;
        if (mc.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen onboarding) {
            onboarding.onClose(); return false;
        }
        if (phase == 0 && ticks > 60 && mc.screen instanceof ArcadiumMainMenu) {
            ClientPreferences.setSetupComplete(true);
            mc.options.pauseOnLostFocus = false;
            mc.options.renderDistance().set(3);
            CreateWorldScreen.openFresh(mc, () -> mc.setScreen(new ArcadiumMainMenu())); phase = 1;
        } else if (phase == 1 && mc.screen instanceof ArcadiumCreateWorldScreen) {
            var field = mc.screen.getClass().getDeclaredField("delegate"); field.setAccessible(true);
            var screen = (CreateWorldScreen) field.get(mc.screen);
            var state = screen.getUiState(); state.setName("Arcadium Attack 059 QA");
            state.setGameMode(SelectedGameMode.CREATIVE); state.setAllowCommands(true);
            state.setGenerateStructures(false);
            state.setWorldType(state.getNormalPresetList().stream().filter(entry -> entry.preset().is(WorldPresets.FLAT)).findFirst().orElseThrow());
            ((CreateWorldAccess) screen).arcadium$create(); phase = 2;
        } else if (phase == 2 && mc.level != null && mc.player != null) {
            mc.setScreen(null); verify(mc); phase = 3;
        } else if (phase == 3) {
            report.put("passed", true); Files.createDirectories(output);
            Files.writeString(output.resolve("result.json"), new GsonBuilder().setPrettyPrinting().create().toJson(report));
            mc.stop(); return true;
        }
        return false;
    }

    private void verify(Minecraft mc) throws Exception {
        var player = mc.player;
        var target = new ArmorStand(mc.level, player.getX() + 1, player.getY(), player.getZ());
        Class<?> config = Class.forName("org.polyfrost.overflowparticles.client.config.OverflowParticlesConfig");
        Object settings = config.getField("INSTANCE").get(null);
        config.getMethod("setModEnabled", boolean.class).invoke(null, true);
        config.getMethod("setAlwaysCritical", boolean.class).invoke(settings, true);
        Class<?> handler = Class.forName("org.polyfrost.overflowparticles.client.OverflowParticlesEventHandler");
        Object events = handler.getField("INSTANCE").get(null);
        Method critical = java.util.Arrays.stream(handler.getDeclaredMethods()).filter(m -> m.getName().equals("doCritical")).findFirst().orElseThrow();
        critical.setAccessible(true);
        List<Object> cases = new ArrayList<>();
        for (boolean deferred : new boolean[]{false, true}) {
            config.getMethod("setCheckInvulnerable", boolean.class).invoke(settings, deferred);
            for (String mode : new String[]{"sweep", "sprinting", "low-cooldown", "empty-hand", "falling-critical"}) {
                player.setItemInHand(InteractionHand.MAIN_HAND, mode.equals("empty-hand") ? ItemStack.EMPTY : new ItemStack(Items.DIAMOND_SWORD));
                player.setSprinting(mode.equals("sprinting"));
                player.setOnGround(!mode.equals("falling-critical"));
                player.fallDistance = mode.equals("falling-critical") ? 1 : 0;
                player.setDeltaMovement(Vec3.ZERO);
                ((Attack059PlayerAccess) player).release$attackTicks(mode.equals("low-cooldown") ? 0 : 100);
                boolean charged = player.getAttackStrengthScale(.5f) > .9f;
                boolean natural = charged && ((PlayerSweepAccess) player).arcadium$canCriticalAttack(target);
                boolean sweep = ((PlayerSweepAccess) player).arcadium$isSweepAttack(charged, natural, charged && player.isSprinting());
                require(sweep == mode.equals("sweep"), "Unexpected native sweep decision for " + mode);
                criticalEmitters = 0;
                player.attack(target);
                require(AttackParticleEffects.suppressExtraCritical(player, target) == sweep, "Wrapped attack did not retain its actual sweep state");
                criticalEmitters = 0;
                critical.invoke(events, player, target);
                require(criticalEmitters == (sweep || natural ? 0 : 1), "Wrong extra critical emitter count for " + mode + ": " + criticalEmitters);
                int extra = criticalEmitters;
                criticalEmitters = 0;
                player.crit(target);
                require(criticalEmitters == 1, "Native critical particles were suppressed");
                cases.add(Map.of("mode", mode, "deferred", deferred, "nativeSweep", sweep, "nativeCritical", natural, "extraCriticalEmitters", extra, "nativeCriticalPreserved", true));
            }
        }
        config.getMethod("setAlwaysCritical", boolean.class).invoke(settings, false);
        criticalEmitters = 0; critical.invoke(events, player, target);
        require(criticalEmitters == 0, "Disabled Always Critical still emitted particles");
        report.put("cases", cases);
        report.put("actualOverflowHandler", handler.getName());
        report.put("alwaysCriticalOff", true);
    }
}
