package io.github.arcadiumclient.featuretest;

import static io.github.arcadiumclient.featuretest.Visual033Qa.call;
import static io.github.arcadiumclient.featuretest.Visual033Qa.field;
import static io.github.arcadiumclient.featuretest.Visual033Qa.nodes;
import static io.github.arcadiumclient.featuretest.Visual033Qa.object;
import static io.github.arcadiumclient.featuretest.Visual033Qa.properties;
import static io.github.arcadiumclient.featuretest.Visual033Qa.rectangle;
import static io.github.arcadiumclient.featuretest.Visual033Qa.semanticsRoot;
import static io.github.arcadiumclient.featuretest.Visual033Qa.stat;

import com.google.gson.JsonParser;
import io.github.arcadiumclient.featuretest.mixin.NoHitDelayProbeAccess;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import net.minecraft.block.Blocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.ArmSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerHandActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.HitResult;
import net.minecraft.world.WorldSettings.GameMode;

public final class NoHitDelay034Qa {
    private static final String CONFIG = "io.github.arcadiumclient.runtime.client.features.NoHitDelayConfig";
    private static final BlockPos STONE = new BlockPos(8, 4, 11);
    private static final List<String> SENT = new ArrayList<>();
    private static final Map<String, Object> RESULT = new LinkedHashMap<>();
    private static Object config, shell;
    private static Screen screen;
    private static Path file;
    private static byte[] contents;
    private static String search;
    private static HitResult target;
    private static GameMode gameMode, serverMode;
    private static Future<GameMode> preparing;
    private static Future<?> restoring;
    private static Pose pose, serverPose;
    private static float health, serverHealth;
    private static int attackCooldown, useCooldown, phase, ticks;
    private static boolean enabled, flying, canFly, invulnerable, creative, canModify;
    private static boolean serverFlying, serverInvulnerable;
    private static boolean begun, done, scope, restored;
    private static List<Map<String, Object>> off;

    private NoHitDelay034Qa() { }

    public static boolean tick(Minecraft mc, Map<String, Object> checks) throws Exception {
        if (!Boolean.getBoolean("arcadium.noHitDelay034Qa") || done) return true;
        require(Boolean.getBoolean("arcadium.forgeQa") && "ArcadiumTest".equals(mc.getSession().getUsername())
                && "0".equals(mc.getSession().getAccessToken()), "NoHitDelay QA requires the isolated offline fixture");
        require(mc.isSingleplayer() && mc.world != null && mc.player != null, "NoHitDelay QA requires its loaded local world");
        try {
            if (!begun) begin(mc, checks);
            require(++ticks < 160, "NoHitDelay QA timed out at phase " + phase);
            switch (phase) {
                case 0 -> {
                    if (ticks < 14 || !preparing.isDone()) return false;
                    serverMode = preparing.get();
                    require(mc.screen != null && mc.screen.getClass().getSimpleName().contains("OneConfigUIScreen"), "The NoHitDelay module settings did not open");
                    require(Visual033Qa.hasText(mc.screen, "NoHitDelay"), "The NoHitDelay module title is missing");
                    toggle(mc);
                    require(!enabled(), "The real module switch did not disable NoHitDelay");
                }
                case 1 -> {
                    if (ticks < 7) return false;
                    persisted(false);
                    off = samples(mc, false);
                    Screenshot.take(mc.gameDir, "nohitdelay034-off.png", mc.width, mc.height, mc.getRenderTarget());
                    toggle(mc);
                    require(enabled(), "The real module switch did not enable NoHitDelay");
                }
                case 2 -> {
                    if (ticks < 7) return false;
                    persisted(true);
                    List<Map<String, Object>> on = samples(mc, true);
                    for (int i = 0; i < off.size(); i++)
                        require(off.get(i).get("packets").equals(on.get(i).get("packets")), "NoHitDelay changed vanilla packet behavior for " + off.get(i).get("case"));
                    RESULT.put("off", off); RESULT.put("on", on);
                    RESULT.put("uiToggleAndPersistence", true);
                    Screenshot.take(mc.gameDir, "nohitdelay034-on.png", mc.width, mc.height, mc.getRenderTarget());
                    restore(mc);
                }
                case 3 -> {
                    if (ticks < 7 || !restoring.isDone()) return false;
                    restoring.get();
                    restoreClient(mc);
                    require(mc.player.isAlive() && mc.player.getHealth() == health, "NoHitDelay QA did not preserve the live player");
                    require(mc.world.getBlockState(STONE).getBlock() == Blocks.STONE, "NoHitDelay QA changed the fixture stone block");
                    require(enabled() == enabled && Arrays.equals(contents, Files.exists(file) ? Files.readAllBytes(file) : null), "The original NoHitDelay configuration was not restored");
                    RESULT.put("vanillaPacketBehavior", true);
                    RESULT.put("livePlayerRestored", Map.of("health", mc.player.getHealth(), "serverHealth", serverHealth,
                            "gameMode", mc.interactionManager.getGameMode().name(), "position", List.of(mc.player.x, mc.player.y, mc.player.z)));
                    RESULT.put("restored", true); RESULT.put("completed", true);
                    done = true; return true;
                }
                default -> throw new IllegalStateException("Unknown NoHitDelay QA phase");
            }
            phase++; ticks = 0; return false;
        } catch (Exception failure) {
            if (begun) try { restore(mc); } catch (Exception cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }

    private static void begin(Minecraft mc, Map<String, Object> checks) throws Exception {
        Path game = mc.gameDir.toPath().toRealPath();
        require(Path.of(System.getProperty("arcadium.forgeQa.gameDir")).toRealPath().equals(game), "NoHitDelay QA must use its exact isolated game directory");
        config = object(CONFIG);
        enabled = enabled();
        require(enabled, "NoHitDelay did not default to enabled in the fresh fixture");
        require("arcadium-no-hit-delay.json".equals(field(config, "id")), "The NoHitDelay module ID is incorrect");
        require("oneconfig.combat".equals(call(field(config, "category"), "getName")), "NoHitDelay is not in Combat");
        require(call(call(config, "getTree"), "get", "enabled") != null, "The NoHitDelay switch is missing from its config tree");
        Path folder = ((Path) call(stat("org.polyfrost.oneconfig.api.config.v1.ConfigManager", "active"), "getFolder")).toAbsolutePath().normalize();
        file = folder.resolve("arcadium-no-hit-delay.json");
        require(file.startsWith(game) && !Files.isSymbolicLink(file), "The NoHitDelay fixture file must stay inside its game directory");
        contents = Files.exists(file) ? Files.readAllBytes(file) : null;
        require(contents == null || contents.length < 1024 * 1024, "The NoHitDelay fixture config is unexpectedly large");
        screen = mc.screen; target = mc.crosshairTarget; gameMode = mc.interactionManager.getGameMode();
        require(mc.player.isAlive(), "NoHitDelay QA requires a live fixture player");
        pose = Pose.capture(mc.player); health = mc.player.getHealth();
        NoHitDelayProbeAccess access = (NoHitDelayProbeAccess) mc;
        attackCooldown = access.arcadium$attackCooldown(); useCooldown = access.arcadium$useCooldown();
        flying = mc.player.abilities.flying; canFly = mc.player.abilities.canFly;
        invulnerable = mc.player.abilities.invulnerable; creative = mc.player.abilities.creativeMode; canModify = mc.player.abilities.canModifyWorld;
        shell = object("org.polyfrost.oneconfig.internal.ui.shell.ShellState"); search = (String) call(shell, "getSearchQuery");
        checks.put("noHitDelay034", RESULT); RESULT.put("defaultEnabled", true);
        begun = true;
        preparing = mc.getServer().executeTask(() -> {
            var player = mc.getServer().getPlayerManager().getAll().get(0);
            require(player.isAlive(), "NoHitDelay QA requires a live server fixture player");
            serverPose = Pose.capture(player); serverHealth = player.getHealth();
            serverFlying = player.abilities.flying; serverInvulnerable = player.abilities.invulnerable;
            GameMode original = player.interactionManager.getGameMode();
            player.setGameMode(GameMode.SURVIVAL);
            player.abilities.invulnerable = true;
            player.fallDistance = 0;
            return original;
        });
        call(shell, "setSearchQuery", "");
        Object route = Class.forName("org.polyfrost.oneconfig.internal.ui.navigation.graph.ModConfigRoute")
                .getConstructor(String.class, String.class).newInstance("arcadium-no-hit-delay.json", "NoHitDelay");
        stat("org.polyfrost.oneconfig.api.ui.v1.OneConfigUI", "open", route);
    }

    private static List<Map<String, Object>> samples(Minecraft mc, boolean active) throws Exception {
        require(enabled() == active, "The NoHitDelay switch changed before the attack samples");
        mc.interactionManager.setGameMode(GameMode.SURVIVAL);
        mc.player.abilities.invulnerable = true;
        mc.player.fallDistance = 0;
        require(mc.interactionManager.hasAttackCooldown(), "The NoHitDelay samples must exercise the vanilla survival penalty");
        List<Map<String, Object>> samples = new ArrayList<>();
        HitResult miss = new HitResult(HitResult.Type.MISS, new Vec3d(8.5, 6, 8.5), Direction.UP, new BlockPos(8, 6, 8));
        samples.add(attack(mc, "miss", miss, 0, active ? 0 : 10, false));
        samples.add(attack(mc, "null", null, 0, active ? 0 : 10, false));
        samples.add(attack(mc, "existingGuiCooldown", miss, 7, 7, false));
        require(mc.world.getBlockState(STONE).getBlock() == Blocks.STONE, "The known stone mining fixture is missing");
        int slot = mc.player.inventory.selectedSlot;
        ItemStack held = mc.player.inventory.items[slot];
        mc.player.inventory.items[slot] = null;
        try { samples.add(attack(mc, "firstMiningClick", new HitResult(new Vec3d(8.5, 4.5, 11), Direction.NORTH, STONE), 0, 0, true)); }
        finally { mc.player.inventory.items[slot] = held; }
        return samples;
    }

    private static Map<String, Object> attack(Minecraft mc, String name, HitResult hit, int initial, int expected, boolean mining) {
        NoHitDelayProbeAccess access = (NoHitDelayProbeAccess) mc;
        mc.crosshairTarget = hit;
        access.arcadium$attackCooldown(initial); access.arcadium$useCooldown(13);
        SENT.clear(); scope = true;
        try {
            access.arcadium$attack();
            require(access.arcadium$attackCooldown() == expected, "NoHitDelay " + name + " cooldown: expected " + expected + ", got " + access.arcadium$attackCooldown());
            require(access.arcadium$useCooldown() == 13, "NoHitDelay changed the right-click cooldown");
            if (mining) {
                require(mc.interactionManager.isMiningBlock(), "The first mining click did not start actual block mining");
                mc.interactionManager.stopMiningBlock();
                require(!mc.interactionManager.isMiningBlock(), "The mining fixture did not stop");
            }
            List<String> expectedPackets = initial > 0 ? List.of() : mining ? List.of("swing", "mining:start", "mining:abort") : List.of("swing");
            require(SENT.equals(expectedPackets), "The vanilla " + name + " packet sequence changed: " + SENT);
            return Map.of("case", name, "initialCooldown", initial, "cooldown", expected, "useCooldown", 13,
                    "packets", List.copyOf(SENT), "mining", mining);
        } finally { scope = false; }
    }

    public static void packet(Packet<?> packet) {
        if (!scope) return;
        if (packet instanceof ArmSwingC2SPacket) SENT.add("swing");
        else if (packet instanceof PlayerHandActionC2SPacket action) {
            SENT.add(action.getAction() == PlayerHandActionC2SPacket.Action.START_DESTROY_BLOCK ? "mining:start"
                    : action.getAction() == PlayerHandActionC2SPacket.Action.ABORT_DESTROY_BLOCK ? "mining:abort" : "otherHandAction");
        } else if (packet instanceof PlayerInteractEntityC2SPacket) SENT.add("entityInteraction");
        else SENT.add(packet.getClass().getName());
    }

    private static void toggle(Minecraft mc) throws Exception {
        Object selected = null;
        for (Object node : nodes(semanticsRoot(mc.screen))) {
            Map<String, Object> props = properties(node);
            if (!props.containsKey("OnClick") || props.containsKey("Disabled")) continue;
            Object rect = call(node, "getBoundsInRoot");
            float width = ((Number) call(rect, "getRight")).floatValue() - ((Number) call(rect, "getLeft")).floatValue();
            float height = ((Number) call(rect, "getBottom")).floatValue() - ((Number) call(rect, "getTop")).floatValue();
            if (width < 20 || width > 160 || height < 10 || height > 80 || Math.abs(width / height - 2) > 0.08) continue;
            require(selected == null, "The NoHitDelay settings exposed multiple switch candidates");
            selected = node;
        }
        require(selected != null, "The actual NoHitDelay module switch was not found");
        RESULT.put("switchBounds", rectangle(selected, mc, "NoHitDelay switch"));
        require(Boolean.TRUE.equals(call(call(properties(selected).get("OnClick"), "getAction"), "invoke")), "The NoHitDelay module switch rejected its click");
    }

    private static boolean enabled() throws Exception { return Boolean.TRUE.equals(stat(CONFIG, "getEnabled")); }

    private static void persisted(boolean expected) throws Exception {
        call(config, "save");
        require(Files.exists(file) && new JsonParser().parse(Files.readString(file)).getAsJsonObject().get("enabled").getAsBoolean() == expected,
                "The NoHitDelay UI switch was not saved");
    }

    private static void restore(Minecraft mc) throws Exception {
        scope = false;
        if (!restored) {
            restored = true;
            stat(CONFIG, "setEnabled", enabled); call(config, "save");
            if (contents == null) Files.deleteIfExists(file); else Files.write(file, contents);
            call(shell, "setSearchQuery", search);
            mc.interactionManager.stopMiningBlock();
            GameMode original = serverMode != null ? serverMode : preparing.get(5, TimeUnit.SECONDS);
            restoring = mc.getServer().executeTask(() -> {
                var player = mc.getServer().getPlayerManager().getAll().get(0);
                boolean healthy = player.isAlive() && player.getHealth() == serverHealth;
                player.setGameMode(original);
                player.abilities.flying = serverFlying; player.abilities.invulnerable = serverInvulnerable;
                serverPose.restore(player);
                require(healthy, "The temporary survival fixture changed server health");
            });
            mc.openScreen(screen);
        }
        restoreClient(mc);
    }

    private static void restoreClient(Minecraft mc) {
        mc.interactionManager.setGameMode(gameMode);
        pose.restore(mc.player);
        mc.player.abilities.flying = flying; mc.player.abilities.canFly = canFly;
        mc.player.abilities.invulnerable = invulnerable; mc.player.abilities.creativeMode = creative; mc.player.abilities.canModifyWorld = canModify;
        mc.crosshairTarget = target;
        NoHitDelayProbeAccess access = (NoHitDelayProbeAccess) mc;
        access.arcadium$attackCooldown(attackCooldown); access.arcadium$useCooldown(useCooldown);
    }

    private record Pose(double x, double y, double z, float yaw, float pitch,
                        double velocityX, double velocityY, double velocityZ, float fallDistance) {
        static Pose capture(Entity entity) {
            return new Pose(entity.x, entity.y, entity.z, entity.yaw, entity.pitch,
                    entity.velocityX, entity.velocityY, entity.velocityZ, entity.fallDistance);
        }
        void restore(Entity entity) {
            entity.setPositionAndAngles(x, y, z, yaw, pitch);
            entity.velocityX = velocityX; entity.velocityY = velocityY; entity.velocityZ = velocityZ;
            entity.fallDistance = fallDistance;
        }
    }

    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
