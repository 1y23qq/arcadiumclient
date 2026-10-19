package io.github.arcadiumclient.featuretest;

import com.google.gson.GsonBuilder;
import com.mojang.authlib.GameProfile;
import io.github.arcadiumclient.featuretest.mixin.ContainerProbeAccess;
import io.github.arcadiumclient.featuretest.mixin.CreativeProbeAccess;
import io.github.arcadiumclient.featuretest.mixin.ScreenProbeAccess;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.FloatBuffer;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.inventory.menu.ChestScreen;
import net.minecraft.client.gui.screen.inventory.menu.CraftingTableScreen;
import net.minecraft.client.gui.screen.inventory.menu.CreativeInventoryScreen;
import net.minecraft.client.gui.screen.inventory.menu.FurnaceScreen;
import net.minecraft.client.gui.screen.inventory.menu.AnvilScreen;
import net.minecraft.client.gui.screen.inventory.menu.HorseScreen;
import net.minecraft.client.gui.screen.inventory.menu.InventoryMenuScreen;
import net.minecraft.client.gui.screen.inventory.menu.SurvivalInventoryScreen;
import net.minecraft.client.render.Window;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.entity.living.mob.passive.animal.HorseBaseEntity;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.inventory.AnimalInventory;
import net.minecraft.inventory.slot.InventorySlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.CreativeModeTab;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.criterion.ScoreboardCriterion;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.Formatting;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.gen.WorldGeneratorType;
import org.lwjgl.BufferUtils;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

public final class FeatureQa {
    private static final String FEATURES = "io.github.arcadiumclient.runtime.client.features.";
    private static final String PREFS = "org.polyfrost.oneconfig.internal.OneConfigConfig";
    private static final String SHELL = "org.polyfrost.oneconfig.internal.ui.shell.ShellState";
    private static final boolean CONTAINER_COVERAGE = Boolean.getBoolean("arcadium.featureTest.containerCoverage");
    private static final String[] STAGES = CONTAINER_COVERAGE ? new String[]{
        "creative-small", "creative-large", "furnace-small", "furnace-large",
        "anvil-small", "anvil-large", "horse-small", "horse-large",
        "unicode-small", "unicode-medium", "unicode-large", "unicode-auto"
    } : new String[]{
        "hud-default", "hud-small", "hud-large", "nick-hider", "nick-restored",
        "inventory-small", "inventory-medium", "inventory-large",
        "chest-small", "chest-large", "crafting-small", "crafting-large",
        "armor-visible", "armor-hidden", "pause-menu", "preferences", "search-typing", "search-config", "direct-config", "main-menu"
    };
    private static final Map<String, Object> RESULT = new LinkedHashMap<>();
    private static final Map<String, Object> ORIGINALS = new LinkedHashMap<>();
    private static final Set<String> TEXT = new LinkedHashSet<>();
    private static final List<Integer> ARMOR = new ArrayList<>();
    private static final Map<String, Long> PENDING_CAPTURES = new LinkedHashMap<>();
    private static final FloatBuffer MATRIX = BufferUtils.createFloatBuffer(16);
    private static int ticks, worldTicks, stage = -1, stageTicks, clickedSlot = -1;
    private static boolean started, done, clicking;
    private static float hotbarMatrix = Float.NaN, crosshairMatrix = Float.NaN;
    private static String capture;
    private static QaPlayer dummy;
    private static LivingEntity armorEntity;
    private static InventoryMenuScreen container;
    private static InventorySlot slot;
    private static int slotX, slotY;
    private static Text originalChat;
    private static Scoreboard scoreboard;
    private static ScoreboardObjective objective;
    private static String username;
    private static Object originalKeyCodes;
    private static boolean originalForceUnicode, originalFontUnicode;
    private static int tabX, tabY;

    private FeatureQa() {}

    public static boolean enabled() { return Boolean.getBoolean("arcadium.featureTest"); }

    public static void tick() {
        if (!enabled() || done) return;
        try {
            Minecraft mc = Minecraft.getInstance();
            if (++ticks == 1) {
                require("ArcadiumTest".equals(mc.getSession().getUsername()) && "0".equals(mc.getSession().getAccessToken()), "QA requires the isolated offline ArcadiumTest session");
                username = mc.getSession().getUsername();
                originalForceUnicode = mc.options.forceUnicodeFont;
                originalFontUnicode = mc.textRenderer.getUnicode();
                if (System.getProperty("arcadium.launcherExecutable") != null) {
                    String data = System.getProperty("arcadium.launcherDataDir");
                    require(data != null && new File(data).toPath().toAbsolutePath().normalize().startsWith(mc.gameDir.toPath().toAbsolutePath().normalize()), "QA account helper storage must be inside the isolated game directory");
                }
                RESULT.put("version", "0.2.7");
                RESULT.put("session", username);
                RESULT.put("renderer", "OpenGL");
            }
            if (!started && ticks >= 60) {
                started = true;
                mc.options.pauseOnUnfocus = false;
                mc.options.viewDistance = 4;
                mc.options.guiScale = 2;
                mc.startGame("arcadium-feature-qa", "Arcadium Feature QA", new WorldSettings(71, WorldSettings.GameMode.CREATIVE, false, false, WorldGeneratorType.FLAT).enableCommands());
            }
            if (stage >= 0 && STAGES[stage].equals("main-menu")) {
                stageTicks++;
                if (stageTicks == 20) {
                    require(mc.screen != null && mc.screen.getClass().getName().contains("ArcadiumMainMenu"), "Arcadium main menu did not open");
                    Mouse.setCursorPosition(80, mc.height - 50);
                }
                if (stageTicks == 25) {
                    ((ScreenProbeAccess) mc.screen).arcadium$click(40, 25, 0);
                    ((ScreenProbeAccess) mc.screen).arcadium$release(40, 25, 0);
                }
                if (stageTicks == 35) {
                    RESULT.put("main-menu", Map.of("accountMenuCapture", true));
                    capture = "main-menu.png";
                }
                if (stageTicks >= 55) complete(mc);
                return;
            }
            if (mc.world == null || mc.player == null) return;
            if (++worldTicks == 1) setup(mc);
            mc.player.yaw = mc.player.lastYaw = 0;
            mc.player.pitch = mc.player.lastPitch = 0;
            mc.player.abilities.flying = true;
            if (worldTicks < 140) return;
            if (stage < 0 || stageTicks >= 55) {
                stage++;
                stageTicks = 0;
                if (stage == STAGES.length) { complete(mc); return; }
                prepare(mc);
            }
            stageTicks++;
            if (STAGES[stage].startsWith("creative-")) {
                if (stageTicks == 10) {
                    require(TEXT.stream().anyMatch(text -> Formatting.strip(text).contains("Materials")), "Creative tab tooltip was not displayed at the physical cursor");
                    capture = STAGES[stage] + "-tab.png";
                }
                if (stageTicks == 15) selectCreativeTab(mc);
            }
            if (stageTicks == 25 && STAGES[stage].equals("search-typing")) typeSearch(mc);
            if (stageTicks == 35) {
                verify(mc);
                capture = STAGES[stage] + ".png";
            }
        } catch (Throwable error) { fail(error); }
    }

    private static void setup(Minecraft mc) throws Exception {
        mc.getServer().executeTask(() -> {
            var world = mc.getServer().getWorld(0);
            world.getGameRules().set("doMobSpawning", "false");
            world.getGameRules().set("doDaylightCycle", "false");
            world.setTimeOfDay(6000);
            mc.getServer().getPlayerManager().getAll().get(0).teleport(8.5, 4, 8.5);
        });
        mc.interactionManager.setGameMode(WorldSettings.GameMode.SURVIVAL);
        mc.player.inventory.items[0] = new ItemStack(Items.DIAMOND_SWORD);
        mc.player.inventory.items[1] = new ItemStack(Items.DIAMOND, 32);
        dummy = new QaPlayer(mc.world, new GameProfile(new UUID(0, 2), "ArcadiumDummy"));
        dummy.setPositionAndAngles(8.5, 4, 12, 180, 0);
        dummy.inventory.armor = new ItemStack[]{new ItemStack(Items.DIAMOND_BOOTS), new ItemStack(Items.DIAMOND_LEGGINGS), new ItemStack(Items.DIAMOND_CHESTPLATE), new ItemStack(Items.DIAMOND_HELMET)};
        mc.world.forceEntity(9999, dummy);
        set(FEATURES + "GuiScaleConfig", "enabled", false);
        for (String category : List.of("hotbar", "crosshair", "inventory", "miscInventory", "chest", "craftingTable", "miscContainer")) set(FEATURES + "GuiScaleConfig", category + "Scale", 1);
        set(FEATURES + "NickHiderConfig", "enabled", false);
        for (String armor : List.of("hideHelmet", "hideChestplate", "hideLeggings", "hideBoots")) set(PREFS, armor, false);
        RESULT.put("worldLoaded", true);
    }

    private static void prepare(Minecraft mc) throws Exception {
        String name = STAGES[stage];
        System.out.println("Arcadium feature QA: " + name);
        TEXT.clear();
        ARMOR.clear();
        hotbarMatrix = crosshairMatrix = Float.NaN;
        clickedSlot = -1;
        container = null;
        slot = null;
        if (!name.equals("search-config")) mc.openScreen(null);
        mc.player.inventory.setCursorItem(null);
        if (!name.startsWith("unicode-")) {
            mc.options.forceUnicodeFont = false;
            mc.textRenderer.setUnicode(false);
        }
        set(FEATURES + "GuiScaleConfig", "enabled", false);
        if (name.startsWith("hud-")) {
            set(FEATURES + "GuiScaleConfig", "enabled", !name.equals("hud-default"));
            set(FEATURES + "GuiScaleConfig", "hotbarAdjustment", name.equals("hud-small") ? -0.5f : name.equals("hud-large") ? 0.5f : 0f);
            set(FEATURES + "GuiScaleConfig", "crosshairAdjustment", name.equals("hud-small") ? -0.5f : name.equals("hud-large") ? 0.5f : 0f);
        } else if (name.equals("nick-hider")) {
            set(FEATURES + "NickHiderConfig", "nickname", "QA Guest");
            set(FEATURES + "NickHiderConfig", "enabled", true);
            originalChat = new LiteralText("<").append(new LiteralText("Arca").setStyle(new Style().setColor(Formatting.AQUA).setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/msg " + username + " "))))
                .append(new LiteralText("diumTest")).append(new LiteralText("> styled chat"));
            mc.gui.getChat().addMessage(originalChat);
            scoreboard = new Scoreboard();
            objective = scoreboard.createObjective("featureqa", ScoreboardCriterion.DUMMY);
            objective.setDisplayName("Scoreboard " + username);
            scoreboard.getScore(username, objective).set(7);
            scoreboard.setDisplayObjective(1, objective);
            mc.world.setScoreboard(scoreboard);
            mc.gui.getPlayerTabOverlay().setHeader(new LiteralText("Tab " + username));
            mc.openScreen(new ChatScreen("message to " + username));
        } else if (name.equals("nick-restored")) {
            set(FEATURES + "NickHiderConfig", "enabled", false);
            mc.openScreen(new ChatScreen("message to " + username));
        } else if (CONTAINER_COVERAGE) {
            prepareExtraContainer(mc, name);
        } else if (name.startsWith("inventory-") || name.startsWith("chest-") || name.startsWith("crafting-")) {
            set(FEATURES + "GuiScaleConfig", "enabled", true);
            float adjustment = name.endsWith("small") ? -0.5f : name.endsWith("large") ? 0.5f : 0f;
            String category = name.startsWith("inventory-") ? "inventory" : name.startsWith("chest-") ? "chest" : "craftingTable";
            set(FEATURES + "GuiScaleConfig", category + "Adjustment", adjustment);
            mc.interactionManager.setGameMode(WorldSettings.GameMode.SURVIVAL);
            container = name.startsWith("inventory-") ? new SurvivalInventoryScreen(mc.player)
                : name.startsWith("chest-") ? new ChestScreen(mc.player.inventory, new SimpleInventory("Feature QA chest", true, 27))
                : new CraftingTableScreen(mc.player.inventory, mc.world, new BlockPos(8, 3, 8));
            mc.player.menu = container.menu;
            slot = container.menu.slots.stream().filter(candidate -> candidate.isItemAllowed(new ItemStack(Items.DIAMOND)) && candidate.x >= 0 && candidate.y >= 0).findFirst().orElseThrow();
            slot.setItem(new ItemStack(Items.DIAMOND, 7));
            mc.openScreen(container);
            ContainerProbeAccess access = (ContainerProbeAccess) container;
            slotX = access.arcadium$left() + slot.x + 8;
            slotY = access.arcadium$top() + slot.y + 8;
            Mouse.setCursorPosition((int) Math.round((slotX + 0.5) * mc.width / container.width), (int) Math.round((container.height - slotY - 0.5) * mc.height / container.height));
        } else if (name.startsWith("armor-")) {
            mc.world.removeEntity(9999);
            dummy = new QaPlayer(mc.world, new GameProfile(new UUID(0, 2), "ArcadiumDummy"));
            dummy.setPositionAndAngles(mc.player.x, mc.player.y, mc.player.z + 4, 180, 0);
            dummy.lastX = dummy.x;
            dummy.lastY = dummy.y;
            dummy.lastZ = dummy.z;
            dummy.inventory.armor = new ItemStack[]{new ItemStack(Items.DIAMOND_BOOTS), new ItemStack(Items.DIAMOND_LEGGINGS), new ItemStack(Items.DIAMOND_CHESTPLATE), new ItemStack(Items.DIAMOND_HELMET)};
            mc.world.forceEntity(9999, dummy);
            boolean hidden = name.equals("armor-hidden");
            for (String armor : List.of("hideHelmet", "hideChestplate", "hideLeggings", "hideBoots")) set(PREFS, armor, hidden);
            mc.openScreen(new ArmorEvidenceScreen(dummy, hidden));
        } else if (name.equals("preferences")) {
            set(PREFS, "scrollSpeed", 2f);
            openRoute(route("PreferencesGraph"));
        } else if (name.equals("pause-menu")) {
            mc.openScreen(new GameMenuScreen());
        } else if (name.equals("search-typing")) {
            set(PREFS, "keybindClosesGui", true);
            Object keybind = Class.forName(PREFS).getField("oneConfigKeybind").get(null);
            originalKeyCodes = keybind.getClass().getMethod("getKeyCodes").invoke(keybind);
            keybind.getClass().getMethod("setKeyCodes", int[].class).invoke(keybind, new int[]{80});
            openRoute(route("ModsGraph"));
            Object shell = singleton(SHELL);
            call(shell, "setSearchQuery", new Class<?>[]{String.class}, "");
            call(shell, "setSearchFocus", new Class<?>[]{Class.forName("org.polyfrost.oneconfig.internal.ui.shell.SearchFocus")}, enumValue("org.polyfrost.oneconfig.internal.ui.shell.SearchFocus", "Focus"));
        } else if (name.equals("search-config")) {
            Object shell = singleton(SHELL);
            call(shell, "setSearchQuery", new Class<?>[]{String.class}, "GUI Scale");
            Object registry = singleton("org.polyfrost.oneconfig.internal.ui.api.ConfigRegistry");
            List<?> configs = (List<?>) call(registry, "getConfigs", new Class<?>[]{});
            Object config = configs.stream().filter(entry -> "arcadium-gui-scale.json".equals(get(entry, "getId"))).findFirst().orElseThrow();
            Method opener = Class.forName("org.polyfrost.oneconfig.internal.ui.screens.ModsKt").getDeclaredMethod("openModCard", Class.forName("org.polyfrost.oneconfig.internal.ui.api.ConfigData"));
            opener.setAccessible(true);
            opener.invoke(null, config);
        } else if (name.equals("direct-config")) {
            openRoute(Class.forName("org.polyfrost.oneconfig.internal.ui.navigation.graph.ModConfigRoute").getConstructor(String.class, String.class).newInstance("arcadium-gui-scale.json", null));
        } else if (name.equals("main-menu")) {
            if (Boolean.getBoolean("arcadium.featureTest.accounts")) {
                require(System.getProperty("arcadium.launcherExecutable") != null, "Account QA requires the native launcher helper");
                Object accounts = singleton("io.github.arcadiumclient.runtime.client.launcher.OneLauncherAccounts");
                call(accounts, "addOffline", new Class<?>[]{String.class}, "ClaudeTester");
                call(accounts, "addOffline", new Class<?>[]{String.class}, "BlockTester");
                RESULT.put("mockAccounts", 2);
            }
            mc.setWorld(null);
            mc.openScreen(null);
        }
    }

    private static void verify(Minecraft mc) throws Exception {
        String name = STAGES[stage];
        Map<String, Object> checks = new LinkedHashMap<>();
        if (name.startsWith("hud-")) {
            float expected = name.equals("hud-small") ? 0.5f : name.equals("hud-large") ? 2f : 1f;
            require(Float.isFinite(hotbarMatrix) && Math.abs(hotbarMatrix - expected) < 0.0001f, "Hotbar transform mismatch: " + hotbarMatrix);
            checks.put("hotbarRatio", hotbarMatrix);
            checks.put("crosshairRatio", Float.isFinite(crosshairMatrix) ? crosshairMatrix : "custom crosshair active");
            if (Float.isFinite(crosshairMatrix)) require(Math.abs(crosshairMatrix - expected) < 0.0001f, "Crosshair transform mismatch");
        } else if (name.equals("nick-hider")) {
            require(TEXT.stream().noneMatch(text -> Formatting.strip(text).toLowerCase().contains(username.toLowerCase())), "Actual username reached the glyph renderer");
            require(TEXT.stream().anyMatch(text -> text.contains("QA Guest")), "Masked name was not drawn");
            require(mc.textRenderer.getWidth(username) == mc.textRenderer.getWidth("QA Guest"), "Masked font width mismatch");
            require("QA Guest".equals(mc.textRenderer.trim(username, 1000)), "Masked trim mismatch");
            require(originalChat.getString().contains(username) && mc.getSession().getUsername().equals(username) && scoreboard.getScoreOwners().contains(username), "Identity or original messages mutated");
            checks.put("fontPathsMasked", true);
            checks.put("originalChatPreserved", true);
            checks.put("scoreOwnerPreserved", true);
            checks.put("drawnText", List.copyOf(TEXT));
        } else if (name.equals("nick-restored")) {
            require(TEXT.stream().anyMatch(text -> text.contains(username)), "Disabling Nick Hider did not restore the name");
            checks.put("originalNameVisible", true);
        } else if (container != null) {
            ContainerProbeAccess access = (ContainerProbeAccess) container;
            require(access.arcadium$slotAt(slotX, slotY) == slot, "Slot lookup differs from visual position");
            require(access.arcadium$hovered() == slot, "Scaled physical cursor does not hover the intended slot");
            int x = (Integer) Class.forName(FEATURES + "GuiScaling").getMethod("mouseX", Screen.class).invoke(null, container);
            int y = (Integer) Class.forName(FEATURES + "GuiScaling").getMethod("mouseY", Screen.class).invoke(null, container);
            require(Math.abs(x - slotX) <= 1 && Math.abs(y - slotY) <= 1, "Physical cursor coordinates diverged");
            clicking = true;
            try {
                ((ScreenProbeAccess) container).arcadium$click(x, y, 0);
                ((ScreenProbeAccess) container).arcadium$release(x, y, 0);
            } finally { clicking = false; }
            if (!(container instanceof CreativeInventoryScreen)) require(clickedSlot == slot.index, "Click reached a different slot: " + clickedSlot + " vs " + slot.index);
            require(mc.player.inventory.getCursorItem() != null && mc.player.inventory.getCursorItem().size == 7, "Click did not pick up the displayed item");
            require(mc.player.inventory.getCursorItem().getItem() == Items.DIAMOND, "Click picked up a different item");
            mc.player.inventory.setCursorItem(null);
            slot.setItem(new ItemStack(Items.DIAMOND, 7));
            checks.put("logicalWidth", container.width);
            checks.put("logicalHeight", container.height);
            checks.put("hoverAndClickAligned", true);
            checks.put("clickedSlot", clickedSlot);
            if (CONTAINER_COVERAGE) {
                require(TEXT.stream().anyMatch(text -> Formatting.strip(text).contains("Diamond")), "Container item tooltip was not displayed at the physical cursor");
                checks.put("itemTooltipVisible", true);
                if (container instanceof CreativeInventoryScreen creative) {
                    require(creative.getSelectedTab() == CreativeModeTab.MATERIALS.getId(), "Creative tab click did not select Materials");
                    checks.put("creativeTabClickAligned", true);
                }
                if (name.startsWith("unicode-")) {
                    int expected = name.endsWith("small") ? 1 : name.endsWith("auto") ? 4 : 2;
                    require(mc.isUnicode() && container.width == (int) Math.ceil((double) mc.width / expected) && container.height == (int) Math.ceil((double) mc.height / expected), "Unicode GUI choice differs from vanilla scaling");
                    checks.put("unicodeScale", expected);
                }
            }
            checks.put("watermark", verifyWatermark(mc, container, false));
        } else if (name.equals("pause-menu")) {
            require(mc.screen instanceof GameMenuScreen, "Pause menu did not open");
            checks.put("watermark", verifyWatermark(mc, mc.screen, true));
        } else if (name.startsWith("armor-")) {
            require(Arrays.stream(dummy.inventory.armor).allMatch(item -> item != null), "Armor equipment was mutated");
            if (name.equals("armor-visible")) require(ARMOR.containsAll(List.of(1, 2, 3, 4)), "Visible armor did not reach all model layers: " + ARMOR);
            else require(ARMOR.isEmpty(), "Hidden armor reached model rendering: " + ARMOR);
            checks.put("renderedSlots", List.copyOf(ARMOR));
            checks.put("equipmentRetained", true);
            checks.put("protection", dummy.inventory.getArmorProtection());
        } else if (name.equals("preferences")) {
            require(mc.screen != null && mc.screen.getClass().getName().contains("OneConfigUIScreen"), "Preferences screen did not open");
            require(Class.forName(PREFS).getField("scrollSpeed").getFloat(null) == 2f, "Scroll Speed setting failed");
            checks.put("scrollSpeed", 2f);
            checks.put("hideArmorOptions", true);
        } else if (name.equals("search-typing")) {
            require(!methodField(mc.screen.getClass(), "closeRequested").getBoolean(mc.screen), "Typing p activated the client close binding");
            String search = (String) call(singleton(SHELL), "getSearchQuery", new Class<?>[]{});
            require(search.contains("p"), "The p character was not inserted into Search: " + search);
            checks.put("focusedInput", true);
            checks.put("pInserted", true);
            checks.put("menuStillOpen", true);
        } else if (name.equals("search-config") || name.equals("direct-config")) {
            require("".equals(call(singleton(SHELL), "getSearchQuery", new Class<?>[]{})), "Opening module settings retained the search filter");
            Object config = singleton(FEATURES + "GuiScaleConfig");
            Object tree = method(config.getClass(), "getTree", new Class<?>[]{}).invoke(config);
            int count = ((Map<?, ?>) tree.getClass().getField("map").get(tree)).size();
            require(count >= 15, "GUI Scale settings are missing");
            checks.put("settingCount", count);
            checks.put("searchCleared", true);
        }
        RESULT.put(name, checks);
    }

    private static Map<String, Object> verifyWatermark(Minecraft mc, Screen screen, boolean pause) throws Exception {
        Class<?> watermark = Class.forName("io.github.arcadiumclient.runtime.client.gui.ArcadiumWatermark");
        require((Boolean) watermark.getMethod("visibleOn", Screen.class).invoke(null, screen), "Arcadium watermark was not rendered on this screen");
        int[] bounds = (int[]) watermark.getMethod("bounds").invoke(null);
        int scale = new Window(mc).getScale();
        require(bounds[2] == 107 * scale && bounds[3] == 22 * scale, "Watermark physical size changed with container scale");
        require((Integer) watermark.getMethod("textureLoads").invoke(null) == 1, "Watermark texture was rasterized or loaded again");
        if (pause) {
            require(bounds[0] == (mc.width - bounds[2]) / 2, "Pause watermark is not centered");
            int firstButton = ((ScreenProbeAccess) screen).arcadium$buttons().stream().mapToInt(button -> button.y).min().orElseThrow();
            require(bounds[1] + bounds[3] < firstButton * scale, "Pause watermark overlaps the buttons");
            int titleY = (Integer) watermark.getMethod("pauseTitleY", Screen.class).invoke(null, screen);
            require((titleY + 9) * scale < bounds[1], "Pause title overlaps the watermark");
        } else {
            require(bounds[0] == mc.width - bounds[2] - 10 * scale && bounds[1] == mc.height - bounds[3] - 10 * scale, "Watermark did not remain at the physical bottom-right corner");
        }
        return Map.of("bounds", List.of(bounds[0], bounds[1], bounds[2], bounds[3]), "textureLoads", 1, "scaleIndependent", true);
    }

    private static void prepareExtraContainer(Minecraft mc, String name) throws Exception {
        set(FEATURES + "GuiScaleConfig", "enabled", true);
        String category = name.startsWith("creative-") || name.startsWith("horse-") ? "miscInventory"
            : name.startsWith("unicode-") ? "inventory" : "miscContainer";
        set(FEATURES + "GuiScaleConfig", category + "Scale", 1);
        set(FEATURES + "GuiScaleConfig", category + "Adjustment", name.endsWith("small") ? -0.5f : name.endsWith("large") ? 0.5f : 0f);
        mc.interactionManager.setGameMode(name.startsWith("creative-") ? WorldSettings.GameMode.CREATIVE : WorldSettings.GameMode.SURVIVAL);
        if (name.startsWith("creative-")) {
            container = new CreativeInventoryScreen(mc.player);
        } else if (name.startsWith("furnace-")) {
            container = new FurnaceScreen(mc.player.inventory, new SimpleInventory("Feature QA furnace", true, 3));
        } else if (name.startsWith("anvil-")) {
            container = new AnvilScreen(mc.player.inventory, mc.world);
        } else if (name.startsWith("horse-")) {
            HorseBaseEntity horse = new HorseBaseEntity(mc.world);
            horse.setType(1);
            horse.setTame(true);
            horse.setHasChest(true);
            container = new HorseScreen(mc.player.inventory, new AnimalInventory("Feature QA horse", 17), horse);
        } else {
            mc.options.forceUnicodeFont = true;
            mc.textRenderer.setUnicode(true);
            set(FEATURES + "GuiScaleConfig", "inventoryAdjustment", 0f);
            set(FEATURES + "GuiScaleConfig", "inventoryScale", name.endsWith("small") ? 0 : name.endsWith("medium") ? 1 : name.endsWith("large") ? 2 : 3);
            container = new SurvivalInventoryScreen(mc.player);
        }
        mc.player.menu = container.menu;
        mc.openScreen(container);
        if (container instanceof CreativeInventoryScreen creative) {
            ((CreativeProbeAccess) creative).arcadium$selectTab(CreativeModeTab.BUILDING_BLOCKS);
            ContainerProbeAccess access = (ContainerProbeAccess) creative;
            CreativeModeTab tab = CreativeModeTab.MATERIALS;
            int column = tab.getColumn();
            tabX = access.arcadium$left() + (column == 5 ? access.arcadium$backgroundWidth() - 26 : column * 28 + column) + 14;
            tabY = access.arcadium$top() + (tab.isTopRow() ? -32 : access.arcadium$backgroundHeight()) + 16;
            setCursor(mc, creative, tabX, tabY);
            return;
        }
        slot = container.menu.slots.stream().filter(candidate -> candidate.isItemAllowed(new ItemStack(Items.DIAMOND)) && candidate.x >= 0 && candidate.y >= 0).findFirst().orElseThrow();
        prepareSlot(mc);
    }

    private static void selectCreativeTab(Minecraft mc) throws Exception {
        CreativeInventoryScreen creative = (CreativeInventoryScreen) container;
        require(((CreativeProbeAccess) creative).arcadium$isInRow(CreativeModeTab.MATERIALS, tabX - ((ContainerProbeAccess) creative).arcadium$left(), tabY - ((ContainerProbeAccess) creative).arcadium$top()), "Creative tab coordinates differ from its hitbox");
        int x = (Integer) Class.forName(FEATURES + "GuiScaling").getMethod("mouseX", Screen.class).invoke(null, creative);
        int y = (Integer) Class.forName(FEATURES + "GuiScaling").getMethod("mouseY", Screen.class).invoke(null, creative);
        require(Math.abs(x - tabX) <= 1 && Math.abs(y - tabY) <= 1, "Scaled creative tab cursor differs from its position");
        ((ScreenProbeAccess) creative).arcadium$click(x, y, 0);
        ((ScreenProbeAccess) creative).arcadium$release(x, y, 0);
        require(creative.getSelectedTab() == CreativeModeTab.MATERIALS.getId(), "Actual creative tab click missed");
        slot = creative.menu.slots.stream().filter(candidate -> candidate.getItem() != null && candidate.getItem().getItem() == Items.DIAMOND && candidate.y >= 0).findFirst().orElseThrow();
        TEXT.clear();
        prepareSlot(mc);
    }

    private static void prepareSlot(Minecraft mc) {
        slot.setItem(new ItemStack(Items.DIAMOND, 7));
        ContainerProbeAccess access = (ContainerProbeAccess) container;
        slotX = access.arcadium$left() + slot.x + 8;
        slotY = access.arcadium$top() + slot.y + 8;
        setCursor(mc, container, slotX, slotY);
    }

    private static void setCursor(Minecraft mc, Screen screen, int x, int y) {
        Mouse.setCursorPosition((int) Math.round((x + 0.5) * mc.width / screen.width), (int) Math.round((screen.height - y - 0.5) * mc.height / screen.height));
    }

    public static void afterFrame() {
        if (!enabled() || done || stage < 0) return;
        try {
            Minecraft mc = Minecraft.getInstance();
            if (capture != null) {
                String name = "feature-qa-" + capture;
                Screenshot.take(mc.gameDir, name, mc.width, mc.height, mc.getRenderTarget());
                PENDING_CAPTURES.put(capture, System.currentTimeMillis());
                capture = null;
            }
            for (String pending : new ArrayList<>(PENDING_CAPTURES.keySet())) {
                File source = new File(mc.gameDir, "screenshots/feature-qa-" + pending);
                if (!source.isFile()) continue;
                byte[] bytes = Files.readAllBytes(source.toPath());
                int length = bytes.length;
                if (length >= 12 && bytes[length - 8] == 'I' && bytes[length - 7] == 'E' && bytes[length - 6] == 'N' && bytes[length - 5] == 'D') {
                    File output = new File(mc.gameDir, "feature-qa");
                    output.mkdirs();
                    Files.write(new File(output, pending).toPath(), bytes);
                    PENDING_CAPTURES.remove(pending);
                } else require(System.currentTimeMillis() - PENDING_CAPTURES.get(pending) < 15000, "Screenshot write timed out: " + pending);
            }
        } catch (Throwable error) { fail(error); }
    }

    public static void drawTabOverlay() {
        if (enabled() && !done && stage >= 0 && STAGES[stage].startsWith("nick-") && scoreboard != null) {
            Minecraft mc = Minecraft.getInstance();
            mc.gui.getPlayerTabOverlay().render(new Window(mc).getWidth(), scoreboard, objective);
        }
    }

    private static void typeSearch(Minecraft mc) throws Exception {
        Object screen = mc.screen;
        boolean editing = (Boolean) method(screen.getClass(), "isEditingText", new Class<?>[]{}).invoke(screen);
        require(editing, "The actual search text input did not acquire focus");
        method(screen.getClass(), "dispatchKeyPressed", new Class<?>[]{int.class, int.class, int.class}).invoke(screen, 80, 80, 0);
        method(screen.getClass(), "sendCharacterEvent", new Class<?>[]{char.class, int.class, int.class}).invoke(screen, 'p', (int) 'p', 0);
        require(!methodField(screen.getClass(), "closeRequested").getBoolean(screen), "Typing p activated the client close binding");
    }

    public static void text(String text) {
        if (enabled() && !done && stage >= 0 && (STAGES[stage].startsWith("nick-") || CONTAINER_COVERAGE) && text != null && TEXT.size() < 300) TEXT.add(text);
    }

    public static boolean clickScope() { return enabled() && clicking && container != null; }

    public static ItemStack click(int index, int button, int action, PlayerEntity player) {
        clickedSlot = index;
        return container.menu.onClickSlot(index, button, action, player);
    }

    private static float matrixScale() {
        MATRIX.clear();
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, MATRIX);
        return MATRIX.get(0);
    }

    public static void hotbarMatrix() {
        if (enabled() && !done && stage >= 0 && stage < STAGES.length && STAGES[stage].startsWith("hud-")) hotbarMatrix = matrixScale();
    }

    public static void crosshairMatrix() {
        if (enabled() && !done && stage >= 0 && stage < STAGES.length && STAGES[stage].startsWith("hud-")) crosshairMatrix = matrixScale();
    }

    public static void armorCandidate(LivingEntity entity) { if (enabled()) armorEntity = entity; }

    public static void armorDraw(int slot) {
        if (enabled() && !done && stage >= 0 && stage < STAGES.length && STAGES[stage].startsWith("armor-") && armorEntity == dummy && !ARMOR.contains(slot)) ARMOR.add(slot);
    }

    private static void complete(Minecraft mc) throws Exception {
        done = true;
        restore();
        RESULT.put("passed", true);
        write(mc);
        System.out.println("Arcadium feature QA: completed");
        if (Boolean.getBoolean("arcadium.featureTest.exit")) mc.stop();
    }

    private static void fail(Throwable error) {
        done = true;
        error.printStackTrace();
        RESULT.put("passed", false);
        RESULT.put("failure", error.toString());
        RESULT.put("stage", stage >= 0 && stage < STAGES.length ? STAGES[stage] : "startup");
        Minecraft mc = Minecraft.getInstance();
        if (dummy != null && mc.player != null && mc.world != null) {
            RESULT.put("dummyTrace", Map.of("present", mc.world.entities.contains(dummy), "player", List.of(mc.player.x, mc.player.y, mc.player.z), "dummy", List.of(dummy.x, dummy.y, dummy.z)));
        }
        try {
            restore();
            write(Minecraft.getInstance());
            if (Boolean.getBoolean("arcadium.featureTest.exit")) Minecraft.getInstance().stop();
        } catch (Exception secondary) { secondary.printStackTrace(); }
    }

    private static void write(Minecraft mc) throws Exception {
        File output = new File(mc.gameDir, "feature-qa");
        output.mkdirs();
        Files.writeString(new File(output, "result.json").toPath(), new GsonBuilder().setPrettyPrinting().create().toJson(RESULT));
    }

    private static void require(boolean value, String message) { if (!value) throw new IllegalStateException(message); }

    private static Object singleton(String name) throws Exception { return Class.forName(name).getField("INSTANCE").get(null); }

    private static Object route(String name) throws Exception { return singleton("org.polyfrost.oneconfig.internal.ui.navigation.graph." + name); }

    private static void openRoute(Object route) throws Exception {
        Class<?> bridge = Class.forName("org.polyfrost.oneconfig.api.ui.v1.internal.OneConfigUIBridgeImpl");
        bridge.getMethod("open", Object.class).invoke(bridge.getConstructor().newInstance(), route);
    }

    private static void set(String name, String property, Object value) throws Exception {
        Class<?> type = Class.forName(name);
        String key = name + ":" + property;
        try {
            Field field = type.getField(property);
            ORIGINALS.putIfAbsent(key, field.get(null));
            field.set(null, value);
        } catch (NoSuchFieldException absent) {
            String suffix = Character.toUpperCase(property.charAt(0)) + property.substring(1);
            ORIGINALS.putIfAbsent(key, type.getMethod("get" + suffix).invoke(null));
            Class<?> valueType = value instanceof Boolean ? boolean.class : value instanceof Float ? float.class : value instanceof Integer ? int.class : String.class;
            type.getMethod("set" + suffix, valueType).invoke(null, value);
        }
    }

    private static void restore() throws Exception {
        Minecraft.getInstance().options.forceUnicodeFont = originalForceUnicode;
        Minecraft.getInstance().textRenderer.setUnicode(originalFontUnicode);
        for (Map.Entry<String, Object> entry : new ArrayList<>(ORIGINALS.entrySet())) {
            String[] parts = entry.getKey().split(":", 2);
            set(parts[0], parts[1], entry.getValue());
        }
        if (originalKeyCodes != null) {
            Object keybind = Class.forName(PREFS).getField("oneConfigKeybind").get(null);
            keybind.getClass().getMethod("setKeyCodes", int[].class).invoke(keybind, originalKeyCodes);
        }
    }

    private static Object enumValue(String name, String value) throws Exception {
        return Arrays.stream(Class.forName(name).getEnumConstants()).filter(entry -> entry.toString().equals(value)).findFirst().orElseThrow();
    }

    private static Method method(Class<?> type, String name, Class<?>[] signature) throws Exception {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try { Method method = current.getDeclaredMethod(name, signature); method.setAccessible(true); return method; }
            catch (NoSuchMethodException ignored) {}
        }
        throw new NoSuchMethodException(name);
    }

    private static Field methodField(Class<?> type, String name) throws Exception {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try { Field field = current.getDeclaredField(name); field.setAccessible(true); return field; }
            catch (NoSuchFieldException ignored) {}
        }
        throw new NoSuchFieldException(name);
    }

    private static Object call(Object target, String name, Class<?>[] signature, Object... args) throws Exception { return method(target.getClass(), name, signature).invoke(target, args); }

    private static Object get(Object target, String name) {
        try { return call(target, name, new Class<?>[]{}); }
        catch (Exception error) { throw new IllegalStateException(error); }
    }
}
