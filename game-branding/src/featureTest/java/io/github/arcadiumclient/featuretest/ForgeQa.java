package io.github.arcadiumclient.featuretest;

import com.google.gson.GsonBuilder;
import io.github.arcadiumclient.featuretest.mixin.ScreenProbeAccess;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.invoke.MethodHandles;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Blocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.inventory.menu.CreativeInventoryScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.gen.WorldGeneratorType;
import org.lwjgl.opengl.GL11;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

public final class ForgeQa {
    private static final String[] PHASES = {"initial-menu", "create-world", "world", "open-inventory",
        "inventory", "open-recipe", "recipe", "open-nei-recipe", "nei-recipe", "open-map", "map", "open-mod-gui", "mod-gui",
        "return-menu", "returned-menu", "open-options", "options", "complete"};
    private static final Map<String, Object> RESULT = new LinkedHashMap<>();
    private static final Map<String, Object> CHECKS = new LinkedHashMap<>();
    private static final Map<String, Long> PENDING = new LinkedHashMap<>();
    private static final List<String> CAPTURED = new ArrayList<>();
    private static final AtomicBoolean WORLD_READY = new AtomicBoolean();
    private static final AtomicReference<Throwable> SERVER_FAILURE = new AtomicReference<>();
    private static Set<String> expected;
    private static int ticks, phase, phaseTicks, worldTicks;
    private static long started;
    private static boolean setup, done, onboardingCompleted, menuOnlyBegun;
    private static String capture;
    private static Object recipeScreen;
    private static Screen neiScreen, mapScreen, modScreen;
    private static Boolean savedJeiOverlay;
    private static final Map<String, Integer> DRAW_EVENTS = new LinkedHashMap<>();
    private static Object drawProbe;
    private static GameMenuScreen quitScreen;
    private static Screen quitConfirmation;
    private static boolean quitClicked;
    private static int quitClickedAt, quitConfirmationButton;
    private static final Map<String, Object> SAVE_QUIT = new LinkedHashMap<>();
    private static Object savedOptionsKeybind;
    private static int[] savedOptionsKeys;
    private static Object fpsHud;
    private static int nativeHudCalls, verifiedHudFrames;

    private ForgeQa() { }
    public static boolean enabled() { return Boolean.getBoolean("arcadium.forgeQa"); }

    public static void tick() {
        if (!enabled() || done) return;
        try {
            Minecraft mc = Minecraft.getInstance();
            if (++ticks == 1) initialize(mc);
            long maximum = Math.max(30, Math.min(1200, Integer.getInteger("arcadium.forgeQa.timeoutSeconds", 210)));
            require(System.nanoTime() - started < maximum * 1_000_000_000L, "Forge QA timed out at " + PHASES[phase]);
            require("ArcadiumTest".equals(mc.getSession().getUsername()) && "0".equals(mc.getSession().getAccessToken()),
                "Forge QA session changed from the isolated offline account");
            Throwable serverFailure = SERVER_FAILURE.get();
            if (serverFailure != null) throw new IllegalStateException("The fixture world setup failed", serverFailure);
            phaseTicks++;
            if (mc.world != null && mc.player != null) worldTicks++;
            if (capture != null || !PENDING.isEmpty()) return;
            if (Boolean.getBoolean("arcadium.release038OnlyQa")) {
                if (ticks < 60 || completeFreshOnboarding(mc) || !Release038Qa.tick(mc, CHECKS)) return;
                RESULT.put("passed", true);
                RESULT.put("release038Only", true);
                write(mc);
                done = true;
                if (Boolean.getBoolean("arcadium.featureTest.exit")) mc.stop();
                return;
            }
            if (Boolean.getBoolean("arcadium.themeAccentOnlyQa")) {
                if (ticks < 60 || completeFreshOnboarding(mc) || !ThemeAccentQa.tick(mc, CHECKS)) return;
                RESULT.put("passed", true);
                RESULT.put("themeAccentOnly", true);
                write(mc);
                done = true;
                if (Boolean.getBoolean("arcadium.featureTest.exit")) mc.stop();
                return;
            }
            if (Boolean.getBoolean("arcadium.onboardingOnlyQa")) {
                if (ticks < 60 || !OnboardingQa.tick(mc, CHECKS)) return;
                RESULT.put("passed", true);
                RESULT.put("onboardingOnly", true);
                write(mc);
                done = true;
                if (Boolean.getBoolean("arcadium.featureTest.exit")) mc.stop();
                return;
            }
            if (Boolean.getBoolean("arcadium.videoSettingsOnlyQa")) {
                if (ticks < 60 || completeFreshOnboarding(mc)) return;
                if (!VideoSettingsQa.tick(mc, CHECKS)) return;
                RESULT.put("passed", true);
                RESULT.put("videoSettingsOnly", true);
                write(mc);
                done = true;
                if (Boolean.getBoolean("arcadium.featureTest.exit")) mc.stop();
                return;
            }
            if (Boolean.getBoolean("arcadium.accountTypesQa")) {
                if (ticks < 60 || completeFreshOnboarding(mc)) return;
                if (!menuOnlyBegun) { verifyMenu(mc, "initialMenu"); menuOnlyBegun = true; }
                if (!AccountTypesQa.tick(mc, CHECKS)) return;
                RESULT.put("passed", true);
                RESULT.put("accountTypesQa", true);
                write(mc);
                done = true;
                if (Boolean.getBoolean("arcadium.featureTest.exit")) mc.stop();
                return;
            }
            if (Boolean.getBoolean("arcadium.playMenuOnlyQa")) {
                require(Boolean.getBoolean("arcadium.playMenuQa"), "Menu-only QA requires the play menu probe");
                if (ticks < 60 || completeFreshOnboarding(mc)) return;
                if (!menuOnlyBegun) { verifyMenu(mc, "initialMenu"); menuOnlyBegun = true; }
                if (!PlayMenusQa.tick(mc, CHECKS)) return;
                RESULT.put("passed", true);
                RESULT.put("menuOnly", true);
                write(mc);
                done = true;
                if (Boolean.getBoolean("arcadium.featureTest.exit")) mc.stop();
                return;
            }
            if (Boolean.getBoolean("arcadium.heldBlockOnlyQa") && phase == 3) {
                if (!HeldBlockQa.tick(mc, CHECKS)) return;
                RESULT.put("passed", true);
                RESULT.put("heldBlockOnly", true);
                write(mc);
                done = true;
                if (Boolean.getBoolean("arcadium.featureTest.exit")) mc.stop();
                return;
            }
            if ((Boolean.getBoolean("arcadium.crosshair034Qa") || Boolean.getBoolean("arcadium.autoText034Qa")) && phase == 3) {
                if (Boolean.getBoolean("arcadium.noHitDelay034Qa") && !NoHitDelay034Qa.tick(mc, CHECKS)) return;
                if (Boolean.getBoolean("arcadium.crosshair034Qa") && !Crosshair034Qa.tick(mc, CHECKS)) return;
                if (Boolean.getBoolean("arcadium.autoText034Qa") && !Messaging033Qa.tick(mc, CHECKS)) return;
                RESULT.put("passed", true);
                RESULT.put("release034", true);
                write(mc);
                done = true;
                if (Boolean.getBoolean("arcadium.featureTest.exit")) mc.stop();
                return;
            }
            if (Boolean.getBoolean("arcadium.themeAccentQa") && phase == 3) {
                if (!ThemeAccentQa.tick(mc, CHECKS)) return;
                if (!BrandingAndChatQa.tick(mc, CHECKS)) return;
                RESULT.put("passed", true);
                RESULT.put("themeAccentQa", true);
                write(mc);
                done = true;
                if (Boolean.getBoolean("arcadium.featureTest.exit")) mc.stop();
                return;
            }
            if (Boolean.getBoolean("arcadium.release035Qa") && phase == 3) {
                if (!Root035Qa.tick(mc, CHECKS)) return;
                if (!ChatDispatch035Qa.tick(mc, CHECKS)) return;
                if (!CuteVisuals035Qa.tick(mc, CHECKS)) return;
                if (!Animations035Qa.tick(mc, CHECKS)) return;
                RESULT.put("passed", true);
                RESULT.put("release035", true);
                write(mc);
                done = true;
                if (Boolean.getBoolean("arcadium.featureTest.exit")) mc.stop();
                return;
            }
            if (Boolean.getBoolean("arcadium.release033Qa") && phase == 3) {
                if (!Boolean.getBoolean("arcadium.release033SkipFonts") && !FontControlsQa.tick(mc, CHECKS)) return;
                if (!Boolean.getBoolean("arcadium.release033SkipVisuals") && !Visual033Qa.tick(mc, CHECKS)) return;
                if (!Root033Qa.tick(mc, CHECKS)) return;
                if (!Messaging033Qa.tick(mc, CHECKS)) return;
                if (!HeldBlockQa.tick(mc, CHECKS)) return;
                RESULT.put("passed", true);
                RESULT.put("release033", true);
                write(mc);
                done = true;
                if (Boolean.getBoolean("arcadium.featureTest.exit")) mc.stop();
                return;
            }
            switch (phase) {
                case 0 -> {
                    if (ticks >= 60) {
                        RESULT.put("menuScreenObserved", mc.screen == null ? null : mc.screen.getClass().getName());
                        if (completeFreshOnboarding(mc)) return;
                        verifyMenu(mc, "initialMenu");
                        take("01-main-menu.png");
                        advance(mc);
                    }
                }
                case 1 -> {
                    advance(mc);
                    mc.startGame("arcadium-forge-qa", "Arcadium Forge QA",
                        new WorldSettings(71, WorldSettings.GameMode.CREATIVE, false, false, WorldGeneratorType.FLAT).enableCommands());
                }
                case 2 -> {
                    if (mc.world == null || mc.player == null) return;
                    if (!setup) setupWorld(mc);
                    pin(mc);
                    if (worldTicks >= 60 && fpsHud == null && FabricLoader.getInstance().isModLoaded("evergreenhud")) setupFpsHud();
                    if (WORLD_READY.get() && worldTicks >= 120) {
                        if (!ReplacementModulesQa.verify(mc, CHECKS)) return;
                        if (!ForgeRenderQa.tick(mc, CHECKS)) return;
                        NoRenderQa.verify(mc, CHECKS);
                        require(mc.player.abilities.creativeMode, "The singleplayer fixture is not creative mode");
                        require(mc.world.getBlockState(new BlockPos(8, 3, 8)).getBlock() == Blocks.STONE,
                            "The flat fixture surface did not reach the client");
                        require(mc.world.getBlockState(new BlockPos(8, 4, 11)).getBlock() == Blocks.STONE,
                            "The Waila target block did not reach the client");
                        verifyForge(false);
                        verifyFpsHud();
                        CHECKS.put("singleplayerWorld", Map.of("creative", true, "surfaceLoaded", true,
                            "playerCount", mc.getServer().getPlayerManager().getAll().size()));
                        take("02-world.png");
                        advance(mc);
                    }
                }
                case 3 -> {
                    DRAW_EVENTS.clear();
                    CreativeInventoryScreen inventory = new CreativeInventoryScreen(mc.player);
                    mc.player.menu = inventory.menu;
                    mc.openScreen(inventory);
                    advance(mc);
                }
                case 4 -> {
                    if (phaseTicks >= 80) {
                        require(mc.screen instanceof CreativeInventoryScreen, "The creative inventory did not remain open");
                        require(!((CreativeInventoryScreen) mc.screen).menu.slots.isEmpty(), "The creative inventory has no real slots");
                        verifyForge(true);
                        verifyWatermark(mc, "inventoryWatermark");
                        CHECKS.put("creativeInventory", Map.of("screen", mc.screen.getClass().getName(),
                            "slots", ((CreativeInventoryScreen) mc.screen).menu.slots.size()));
                        take("03-inventory.png");
                        advance(mc);
                    }
                }
                case 5 -> {
                    if (expected.contains("JEI")) {
                        Class<?> focusClass = Class.forName("mezz.jei.gui.Focus");
                        Object focus = focusClass.getConstructor(ItemStack.class).newInstance(new ItemStack(Items.DIAMOND_PICKAXE));
                        recipeScreen = Class.forName("mezz.jei.gui.RecipesGui").getConstructor().newInstance();
                        recipeScreen.getClass().getMethod("showRecipes", focusClass).invoke(recipeScreen, focus);
                        require(mc.screen == recipeScreen, "JEI did not open the requested recipe screen");
                    }
                    advance(mc);
                }
                case 6 -> {
                    if (expected.contains("JEI")) {
                        if (phaseTicks < 50) return;
                        require(mc.screen == recipeScreen, "The JEI recipe screen did not remain open");
                        int layouts = size(field(recipeScreen, "recipeLayouts"));
                        require(layouts > 0, "JEI did not build a visible recipe layout");
                        require(call(recipeScreen, "getParentScreen") instanceof CreativeInventoryScreen,
                            "JEI did not retain the actual inventory as its parent");
                        CHECKS.put("JEIRecipe", Map.of("screen", recipeScreen.getClass().getName(), "layouts", layouts));
                        take("03b-jei-recipe.png");
                    }
                    advance(mc);
                }
                case 7 -> {
                    if (expected.contains("NotEnoughItems")) {
                        if (recipeScreen != null) mc.openScreen((Screen) call(recipeScreen, "getParentScreen"));
                        if (expected.contains("JEI") && Boolean.getBoolean("arcadium.forgeQa.neiOverlayProbe")) {
                            savedJeiOverlay = (boolean) staticCall("mezz.jei.config.Config", "isOverlayEnabled");
                            if (savedJeiOverlay) staticCall("mezz.jei.config.Config", "toggleOverlayEnabled");
                        }
                        boolean opened = (boolean) Class.forName("codechicken.nei.recipe.GuiCraftingRecipe")
                            .getMethod("openRecipeGui", String.class, Object[].class)
                            .invoke(null, "item", new Object[]{new ItemStack(Items.DIAMOND_PICKAXE)});
                        require(opened && mc.screen != null && mc.screen.getClass().getName().equals("codechicken.nei.recipe.GuiCraftingRecipe"),
                            "NEI did not open its real crafting recipe GUI");
                        neiScreen = mc.screen;
                    }
                    advance(mc);
                }
                case 8 -> {
                    if (expected.contains("NotEnoughItems")) {
                        if (phaseTicks < 50) return;
                        require(mc.screen == neiScreen, "The NEI crafting recipe GUI did not remain open");
                        Collection<?> handlers = (Collection<?>) call(neiScreen, "getCurrentRecipeHandlers");
                        int recipes = 0;
                        for (Object handler : handlers) recipes += (int) call(handler, "numRecipes");
                        require(recipes > 0, "The NEI crafting recipe GUI contains no real recipes");
                        require(call(neiScreen, "getFirstScreen") instanceof CreativeInventoryScreen,
                            "NEI did not retain the actual inventory as its parent");
                        Map<String, Object> nei = new LinkedHashMap<>();
                        nei.put("screen", neiScreen.getClass().getName()); nei.put("recipes", recipes); nei.put("handlers", handlers.size());
                        if (expected.contains("JEI")) {
                            nei.put("jeiOverlayEnabled", staticCall("mezz.jei.config.Config", "isOverlayEnabled"));
                            Object overlay = call(staticCall("mezz.jei.Internal", "getRuntime"), "getItemListOverlay");
                            nei.put("jeiOverlayOpen", call(overlay, "isOpen"));
                            Object properties = field(overlay, "guiProperties");
                            nei.put("jeiGui", properties == null ? "" : ((Class<?>) call(properties, "getGuiClass")).getName());
                            if (!Boolean.getBoolean("arcadium.forgeQa.neiOverlayProbe")) {
                                require(Boolean.TRUE.equals(nei.get("jeiOverlayEnabled")) && Boolean.FALSE.equals(nei.get("jeiOverlayOpen")),
                                    "JEI and NEI still draw competing item overlays, or the compatibility changed the JEI setting");
                            }
                        }
                        CHECKS.put("NEIRecipe", nei);
                        verifyWatermark(mc, "neiRecipeWatermark");
                        take("03bb-nei-recipe.png");
                    }
                    advance(mc);
                }
                case 9 -> {
                    restoreJeiOverlay();
                    if (neiScreen != null && expected.contains("JEI")) {
                        mc.openScreen((Screen) call(neiScreen, "getFirstScreen"));
                        Object overlay = call(staticCall("mezz.jei.Internal", "getRuntime"), "getItemListOverlay");
                        require(Boolean.TRUE.equals(call(overlay, "isOpen")) && Boolean.TRUE.equals(staticCall("mezz.jei.config.Config", "isOverlayEnabled")),
                            "JEI did not reopen on the original inventory after leaving the NEI recipe GUI");
                        CHECKS.put("JEIReturnedFromNei", Map.of("overlayOpen", true, "settingUnchanged", true));
                    }
                    if (expected.contains("journeymap")) {
                        mc.openScreen(null);
                        call(staticCall("journeymap.client.ui.UIManager", "getInstance"), "openFullscreenMap");
                        require(mc.screen != null && mc.screen.getClass().getName().equals("journeymap.client.ui.fullscreen.Fullscreen"),
                            "JourneyMap did not open its fullscreen map through its real UI manager");
                        mapScreen = mc.screen;
                    }
                    advance(mc);
                }
                case 10 -> {
                    if (expected.contains("journeymap")) {
                        if (phaseTicks < 60) return;
                        require(mc.screen == mapScreen, "The JourneyMap fullscreen map did not remain open");
                        require(Boolean.TRUE.equals(call(staticCall("journeymap.client.JourneymapClient", "getInstance"), "isMapping")),
                            "JourneyMap stopped mapping the real fixture world");
                        CHECKS.put("journeymapFullscreen", Map.of("screen", mapScreen.getClass().getName(), "mapping", true));
                        take("03c-journeymap.png");
                    }
                    advance(mc);
                }
                case 11 -> {
                    if (expected.contains("keystrokes")) {
                        if (phaseTicks == 1) {
                            if (mapScreen != null) call(mapScreen, "close");
                            mc.openScreen(null);
                            Object screen = staticField("keystrokesmod.Raven", "clickGui");
                            require(screen instanceof Screen, "KeystrokesMod did not create a real Minecraft GUI");
                            modScreen = (Screen) screen;
                            Object module = ((Collection<?>) staticField("keystrokesmod.module.ModuleManager", "modules")).stream()
                                .filter(value -> value.getClass().getName().equals("keystrokesmod.module.impl.client.Gui")).findFirst().orElseThrow();
                            require(Integer.valueOf(54).equals(call(module, "getKeycode")), "Raven lost its default Right Shift binding");
                        }
                        if (phaseTicks == 3) pushKey(229, true);
                        if (phaseTicks == 7) pushKey(229, false);
                        if (phaseTicks == 10) {
                            CHECKS.put("ravenDefaultKey", Map.of("screen", mc.screen == null ? "none" : mc.screen.getClass().getName(),
                                "ravenOpened", mc.screen == modScreen));
                            savedOptionsKeybind = staticField("org.polyfrost.oneconfig.internal.OneConfigConfig", "oneConfigKeybind");
                            savedOptionsKeys = (int[]) call(savedOptionsKeybind, "getKeyCodes");
                            savedOptionsKeybind.getClass().getMethod("setKeyCodes", int[].class).invoke(savedOptionsKeybind, new int[] {228});
                            mc.openScreen(null);
                        }
                        if (phaseTicks == 13) pushKey(229, true);
                        if (phaseTicks == 17) pushKey(229, false);
                        if (phaseTicks < 20) return;
                        require(mc.screen == modScreen && field(modScreen, "backgroundFade") != null,
                            "Raven did not open through Right Shift with Arcadium Options bound to Right Control; current screen: "
                                + (mc.screen == null ? "none" : mc.screen.getClass().getName()));
                        savedOptionsKeybind.getClass().getMethod("setKeyCodes", int[].class).invoke(savedOptionsKeybind, savedOptionsKeys);
                        CHECKS.put("ravenRightShift", Map.of("nativeInputQueue", true, "optionsKey", "Right Control", "opened", true));
                    }
                    advance(mc);
                }
                case 12 -> {
                    if (expected.contains("keystrokes")) {
                        if (phaseTicks < 50) return;
                        require(mc.screen == modScreen, "The KeystrokesMod GUI did not remain open");
                        int categories = size(staticField("keystrokesmod.clickgui.ClickGui", "categories"));
                        require(categories > 0, "The KeystrokesMod GUI has no categories");
                        CHECKS.put("keystrokesGui", Map.of("screen", modScreen.getClass().getName(), "categories", categories));
                        take("03d-keystrokes.png");
                    }
                    advance(mc);
                }
                case 13 -> {
                    if (quitScreen == null) {
                        quitScreen = new GameMenuScreen();
                        mc.openScreen(quitScreen);
                        CHECKS.put("saveAndQuit", SAVE_QUIT);
                        SAVE_QUIT.put("pauseMenu", true);
                        return;
                    }
                    if (phaseTicks < 10) return;
                    if (!quitClicked) {
                        require(mc.screen == quitScreen, "The real pause menu was replaced before quitting");
                        var quit = ((ScreenProbeAccess) quitScreen).arcadium$buttons().stream()
                                .filter(button -> button.id == 1).findFirst().orElseThrow();
                        clickQuitButton(mc, quitScreen, quit, "pauseButton");
                        quitClicked = true;
                        quitClickedAt = phaseTicks;
                        if (mc.world != null) {
                            require(FabricLoader.getInstance().isModLoaded("confirmdisconnect"),
                                    "Save and Quit kept the world without a configured confirmation module");
                            require(mc.screen != null, "Save and Quit left the world attached without a confirmation screen");
                            String name = mc.screen.getClass().getName();
                            if (name.equals("dev.microcontrollers.confirmdisconnect.ConfirmDisconnectScreen")) {
                                quitConfirmation = mc.screen;
                                quitConfirmationButton = 0;
                            } else {
                                require(mc.screen == quitScreen && Boolean.TRUE.equals(staticField(
                                        "dev.microcontrollers.confirmdisconnect.config.ConfirmDisconnectConfig", "useDiscreteConfirmation")),
                                        "Save and Quit did not open its actual confirmation UI: " + name);
                                quitConfirmation = quitScreen;
                                quitConfirmationButton = 1;
                            }
                            SAVE_QUIT.put("confirmationScreen", name);
                            SAVE_QUIT.put("worldPreservedUntilConfirmation", true);
                            return;
                        }
                    } else if (mc.world != null) {
                        require(mc.screen == quitConfirmation, "The disconnect confirmation unexpectedly changed screens");
                        var confirm = ((ScreenProbeAccess) quitConfirmation).arcadium$buttons().stream()
                                .filter(button -> button.id == quitConfirmationButton).findFirst().orElseThrow();
                        require(confirm.visible, "The actual disconnect confirmation is hidden");
                        if (!confirm.active) {
                            require(phaseTicks - quitClickedAt < 400, "The disconnect confirmation did not become active");
                            return;
                        }
                        SAVE_QUIT.put("confirmationDelayTicks", phaseTicks - quitClickedAt);
                        clickQuitButton(mc, quitConfirmation, confirm, "confirmationButton");
                    }
                    require(mc.world == null, "The final Save and Quit confirmation did not detach the client world");
                    SAVE_QUIT.put("worldDetached", true);
                    advance(mc);
                }
                case 14 -> {
                    if (phaseTicks >= 30) {
                        verifyMenu(mc, "returnedMenu");
                        if (Boolean.getBoolean("arcadium.forgeQa.registrySmoke")) verifyRegistrySmoke();
                        take("04-main-menu-returned.png");
                        advance(mc);
                    }
                }
                case 15 -> {
                    if (!ResourcePackQa.tick(mc, CHECKS)) return;
                    if (!PlayMenusQa.tick(mc, CHECKS)) return;
                    Object route = Class.forName("org.polyfrost.oneconfig.internal.ui.navigation.graph.PreferencesGraph")
                        .getField("INSTANCE").get(null);
                    Class.forName("org.polyfrost.oneconfig.api.ui.v1.OneConfigUI").getMethod("open", Object.class).invoke(null, route);
                    advance(mc);
                }
                case 16 -> {
                    if (phaseTicks >= 60) {
                        require(mc.screen != null && mc.screen.getClass().getName().contains("OneConfigUIScreen"),
                            "The native Arcadium Options screen did not open");
                        Object configs = Class.forName("org.polyfrost.oneconfig.internal.ui.api.ConfigRegistry").getField("INSTANCE").get(null);
                        int count = size(call(configs, "getConfigs"));
                        require(count > 0, "The native Arcadium configuration registry is empty");
                        CHECKS.put("arcadiumOptions", Map.of("screen", mc.screen.getClass().getName(), "configs", count));
                        take("05-arcadium-options.png");
                        advance(mc);
                    }
                }
                case 17 -> complete(mc);
                default -> throw new IllegalStateException("Unknown Forge QA phase");
            }
        } catch (Throwable failure) { fail(failure); }
    }

    private static void clickQuitButton(Minecraft mc, Screen screen, ButtonWidget button, String stage) {
        int x = button.x + button.getWidth() / 2, y = button.y + 10;
        LinkedHashMap<String, Object> click = new LinkedHashMap<>();
        SAVE_QUIT.put(stage, click);
        click.put("screen", screen.getClass().getName());
        click.put("guiDimensions", List.of(screen.width, screen.height));
        click.put("framebufferDimensions", List.of(mc.width, mc.height));
        click.put("buttonId", button.id);
        click.put("buttonPositionAndWidth", List.of(button.x, button.y, button.getWidth()));
        click.put("coordinates", List.of(x, y));
        click.put("label", button.message);
        click.put("active", button.active); click.put("visible", button.visible);
        boolean hit = button.mouseClicked(mc, x, y);
        click.put("nativeHitPredicate", hit);
        require(mc.screen == screen && button.active && button.visible && hit,
                "The real " + stage + " click did not hit its active button: " + click);
        ((ScreenProbeAccess) screen).arcadium$click(x, y, 0);
        click.put("screenAfter", mc.screen == null ? "none" : mc.screen.getClass().getName());
        click.put("worldAttachedAfter", mc.world != null);
    }

    private static void pushKey(int scancode, boolean down) throws Exception {
        Class<?> eventType = Class.forName("org.lwjgl.sdl.SDL_Event");
        Class<?> events = Class.forName("org.lwjgl.sdl.SDLEvents");
        Object event = eventType.getMethod("calloc").invoke(null);
        try {
            int type = events.getField(down ? "SDL_EVENT_KEY_DOWN" : "SDL_EVENT_KEY_UP").getInt(null);
            eventType.getMethod("type", int.class).invoke(event, type);
            Object key = eventType.getMethod("key").invoke(event);
            Class<?> keyType = key.getClass();
            keyType.getMethod("type", int.class).invoke(key, type);
            keyType.getMethod("scancode", int.class).invoke(key, scancode);
            keyType.getMethod("down", boolean.class).invoke(key, down);
            Class<?> display = Class.forName("pl.tomgirl.pylon.window.DisplaySdl");
            long window = (long) display.getMethod("getHandle").invoke(display.getMethod("instance").invoke(null));
            int id = (int) Class.forName("org.lwjgl.sdl.SDLVideo").getMethod("SDL_GetWindowID", long.class).invoke(null, window);
            keyType.getMethod("windowID", int.class).invoke(key, id);
            require(Boolean.TRUE.equals(events.getMethod("SDL_PushEvent", eventType).invoke(null, event)), "The native key queue rejected the QA event");
        } finally { eventType.getMethod("free").invoke(event); }
    }

    private static void initialize(Minecraft mc) throws Exception {
        started = System.nanoTime();
        require("ArcadiumTest".equals(mc.getSession().getUsername()) && "0".equals(mc.getSession().getAccessToken()),
            "Forge QA requires the isolated ArcadiumTest / token 0 account");
        String requestedGame = System.getProperty("arcadium.forgeQa.gameDir");
        require(requestedGame != null && Path.of(requestedGame).toRealPath().equals(mc.gameDir.toPath().toRealPath()),
            "Forge QA must use its exact fresh game directory");
        if (Boolean.getBoolean("arcadium.accountTypesQa")) {
            String helper = System.getProperty("arcadium.launcherExecutable");
            String storage = System.getProperty("arcadium.launcherDataDir");
            require(helper != null && storage != null && Files.isRegularFile(Path.of(helper)), "Account type QA requires its real native helper");
            Path game = mc.gameDir.toPath().toRealPath();
            Path data = Path.of(storage).toAbsolutePath().normalize();
            require(data.startsWith(game) && !data.equals(game), "Account type QA account data must stay inside its game fixture");
            if (Files.exists(data)) require(data.toRealPath().startsWith(game), "Account type QA account data escaped its fixture");
        } else require(System.getProperty("arcadium.launcherExecutable") == null, "Forge QA must not configure a native account helper");
        expected = ids("arcadium.forgeQa.expected");
        if (!expected.isEmpty()) installDrawProbe();
        require(!Boolean.getBoolean("arcadium.forgeQa.registrySmoke") || expected.contains("arcadiumregistrysmoke"),
            "The private registry probe is not an expected Forge guest");
        Set<String> nativeIds = ids("arcadium.forgeQa.nativeIds");
        require(nativeIds.containsAll(Set.of("arcadiumclient", "oneconfig", "arcadium_runtime", "compose-multiplatform", "arcadium_forge_compat")),
            "Forge QA is missing required Arcadium component identities");
        for (String removed : List.of("polyplus", "crashpatch", "chattweaks", "polysprint"))
            require(!FabricLoader.getInstance().isModLoaded(removed), "Removed dependency was loaded: " + removed);
        for (String id : nativeIds) require(FabricLoader.getInstance().isModLoaded(id), "Native Arcadium component is not loaded: " + id);
        require(FabricLoader.getInstance().isModLoaded("arcadium_feature_test"), "The development QA mod is not loaded");
        mc.options.pauseOnUnfocus = false;
        if (!Boolean.getBoolean("arcadium.videoSettingsOnlyQa")) {
            mc.options.viewDistance = 4;
            mc.options.guiScale = 2;
        }
        RESULT.put("version", net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer("arcadiumclient")
                .orElseThrow().getMetadata().getVersion().getFriendlyString());
        RESULT.put("initialScreen", mc.screen == null ? null : mc.screen.getClass().getName());
        RESULT.put("offlineSession", true);
        RESULT.put("expectedForgeModIds", expected);
        RESULT.put("nativeFabricModIds", nativeIds);
        RESULT.put("renderer", GL11.glGetString(GL11.GL_RENDERER));
        RESULT.put("checks", CHECKS);
        RESULT.put("nativeFpsModuleAvailable", FabricLoader.getInstance().isModLoaded("evergreenhud"));
        RESULT.put("screenshots", CAPTURED);
        write(mc);
    }

    private static Set<String> ids(String property) {
        Set<String> result = new LinkedHashSet<>();
        for (String id : System.getProperty(property, "").split(",")) {
            if (id.isEmpty()) continue;
            require(id.matches("[A-Za-z0-9_-]+"), "Invalid QA mod identity property");
            require(result.add(id), "Duplicate QA mod identity property");
        }
        return result;
    }

    private static void verifyMenu(Minecraft mc, String name) {
        require(mc.world == null, "Arcadium menu evidence still has a world attached");
        require(mc.screen != null && mc.screen.getClass().getName().contains("ArcadiumMainMenu"),
            "The Arcadium main menu did not open: " + (mc.screen == null ? "null" : mc.screen.getClass().getName()));
        CHECKS.put(name, Map.of("screen", mc.screen.getClass().getName(), "width", mc.width, "height", mc.height));
    }

    private static void verifyRegistrySmoke() throws Exception {
        Class<?> probe = Class.forName("arcadium.registry.smoke.RegistrySmoke");
        require(probe.getField("REGISTERED").getBoolean(null), "The private registry probe did not register its block/item");
        probe.getMethod("verifyRestored").invoke(null);
        CHECKS.put("registrySmoke", Map.of("worldSnapshotRoundtrip", true,
            "blockId", probe.getField("BLOCK_ID").getInt(null), "itemId", probe.getField("ITEM_ID").getInt(null),
            "serverStarts", probe.getField("SERVER_STARTS").getInt(null)));
    }

    private static boolean completeFreshOnboarding(Minecraft mc) throws Exception {
        if (mc.screen == null || !mc.screen.getClass().getName().equals("io.github.arcadiumclient.runtime.client.gui.ArcadiumMainMenu") || onboardingCompleted) return false;
        Class<?> config = Class.forName("io.github.arcadiumclient.runtime.client.ClientPreferences");
        if (Boolean.TRUE.equals(config.getMethod("getSetupComplete").invoke(null))) return false;
        Class<?> setup = Class.forName("io.github.arcadiumclient.runtime.client.features.FirstRunSetup");
        Object settings = setup.getField("INSTANCE").get(null);
        require(Boolean.TRUE.equals(setup.getMethod("toggleSprint", boolean.class).invoke(settings, true)), "The replacement sprint setting is not registered");
        require(Boolean.TRUE.equals(setup.getMethod("blockOverlay", boolean.class).invoke(settings, true)), "The Block Overlay setting is not registered");
        config.getMethod("setSetupComplete", boolean.class).invoke(null, true);
        call(config.getField("INSTANCE").get(null), "save");
        mc.openScreen((Screen) mc.screen.getClass().getConstructor().newInstance());
        require(Boolean.TRUE.equals(config.getMethod("getSetupComplete").invoke(null)), "Onboarding fixture state was not retained");
        onboardingCompleted = true;
        CHECKS.put("freshOnboarding", Map.of("screen", "io.github.arcadiumclient.runtime.client.gui.ArcadiumMainMenu", "fixtureConfigCompleted", true));
        write(mc);
        return true;
    }

    @SuppressWarnings("unchecked")
    private static void setupFpsHud() throws Exception {
        Object manager = staticField("org.polyfrost.oneconfig.api.hud.v1.HudManager", "INSTANCE");
        List<Object> active = (List<Object>) call(manager, "getActiveInstances");
        String fpsType = "org.polyfrost.evergreenhud.client.hud.FpsHud";
        fpsHud = active.stream().filter(hud -> hud.getClass().getName().equals(fpsType)).findFirst().orElse(null);
        if (fpsHud == null) {
            Object provider = ((Collection<?>) call(manager, "providers")).stream()
                .filter(hud -> hud.getClass().getName().equals(fpsType)).findFirst().orElseThrow();
            fpsHud = provider.getClass().getMethod("make", Class.forName("org.polyfrost.oneconfig.api.config.v1.Tree")).invoke(provider, new Object[] {null});
            active.add(fpsHud);
            call(fpsHud, "setup");
        }
        fpsHud.getClass().getMethod("setShowBackground", boolean.class).invoke(fpsHud, true);
        fpsHud.getClass().getMethod("setAbsolutePosition", float.class, float.class).invoke(fpsHud, 16f, 16f);
        fpsHud.getClass().getMethod("setHidden", boolean.class).invoke(fpsHud, false);
        manager.getClass().getMethod("invalidate").invoke(null);
    }

    private static void setupWorld(Minecraft mc) {
        setup = true;
        mc.getServer().executeTask(() -> {
            try {
                var world = mc.getServer().getWorld(0);
                world.getGameRules().set("doMobSpawning", "false");
                world.getGameRules().set("doDaylightCycle", "false");
                world.setTimeOfDay(6000);
                for (int x = 4; x <= 12; x++) for (int z = 4; z <= 14; z++) {
                    world.setBlockState(new BlockPos(x, 3, z), Blocks.STONE.defaultState());
                    for (int y = 4; y <= 7; y++) world.setBlockState(new BlockPos(x, y, z), Blocks.AIR.defaultState());
                }
                world.setBlockState(new BlockPos(8, 4, 11), Blocks.STONE.defaultState());
                mc.getServer().getPlayerManager().getAll().get(0).teleport(8.5, 4, 8.5);
                WORLD_READY.set(true);
            } catch (Throwable failure) { SERVER_FAILURE.set(failure); }
        });
        mc.player.inventory.items[0] = new ItemStack(Items.DIAMOND, 7);
        mc.player.inventory.items[1] = new ItemStack(Items.DIAMOND_PICKAXE);
    }

    private static void pin(Minecraft mc) {
        mc.player.setPositionAndAngles(8.5, 4, 8.5, 0, 20);
        mc.player.yaw = mc.player.lastYaw = 0;
        mc.player.pitch = mc.player.lastPitch = 20;
        mc.player.abilities.flying = true;
    }

    private static void verifyForge(boolean inventory) throws Exception {
        if (expected.isEmpty()) {
            require(!Boolean.getBoolean("arcadium.forge.prepared"), "No-Forge baseline unexpectedly applied Forge game patches");
            CHECKS.put("noForgeBaseline", Map.of("gamePatched", false, "guestCount", 0));
            return;
        }
        require(Boolean.getBoolean("arcadium.forge.prepared"), "Forge guests did not prepare the official game patches");
        Path game = Minecraft.getInstance().gameDir.toPath().toRealPath();
        Path origin = Path.of(Minecraft.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath();
        require(origin.startsWith(game.resolve(".arcadium/forge-compat/game-cache")),
            "The Forge game still uses the vanilla Minecraft cache: " + origin);
        CHECKS.put("forgeGameCache", Map.of("isolated", true, "gameJar", game.relativize(origin).toString()));
        Class<?> loaderClass = Class.forName("net.minecraftforge.fml.common.Loader");
        Object loader = loaderClass.getMethod("instance").invoke(null);
        Collection<?> active = (Collection<?>) call(loader, "getActiveModList");
        Set<String> activeIds = new LinkedHashSet<>();
        Map<String, String> states = new LinkedHashMap<>();
        for (Object container : active) {
            String id = (String) call(container, "getModId");
            activeIds.add(id);
            if (expected.contains(id)) {
                Object state = loaderClass.getMethod("getModState", Class.forName("net.minecraftforge.fml.common.ModContainer")).invoke(loader, container);
                require(state instanceof Enum<?>, "Forge returned an invalid mod lifecycle state: " + state);
                states.put(id, ((Enum<?>) state).name());
                require("AVAILABLE".equals(((Enum<?>) state).name()), "Forge guest is not available: " + id + " / " + state);
            }
        }
        require(activeIds.containsAll(expected), "FML active mods are missing expected guests: " + expected + " / " + activeIds);
        Class<?> loaderState = Class.forName("net.minecraftforge.fml.common.LoaderState");
        boolean initialized = (boolean) loaderClass.getMethod("hasReachedState", loaderState)
            .invoke(loader, loaderState.getField("AVAILABLE").get(null));
        require(initialized, "Forge did not complete mod initialization");
        CHECKS.put("fml", Map.of("activeModIds", activeIds, "guestStates", states, "initializationComplete", initialized));
        if (expected.contains("CodeChickenCore")) {
            Object legacy = staticField("net.minecraft.launchwrapper.Launch", "classLoader");
            Class<?> wrapped = Class.forName("net.minecraftforge.fml.common.asm.ASMTransformerWrapper$TransformerWrapper");
            Set<String> transformers = new LinkedHashSet<>();
            for (Object transformer : (Collection<?>) call(legacy, "getTransformers")) {
                Object actual = wrapped.isInstance(transformer) ? field(transformer, "parent") : transformer;
                transformers.add(actual.getClass().getName());
            }
            require(transformers.containsAll(Set.of("codechicken.lib.asm.ClassHeirachyManager", "codechicken.lib.asm.RenderHookTransformer")),
                "CodeChickenLib did not register its real coremod transformers");
            CHECKS.put("CodeChickenLib", Map.of("coremodLibrary", true, "activeTransformers", transformers));
        }
        if (expected.contains("JEI")) {
            Object runtime = staticCall("mezz.jei.Internal", "getRuntime");
            Object registry = staticCall("mezz.jei.Internal", "getItemRegistry");
            require(runtime != null && registry != null, "JEI lifecycle did not create its runtime and item registry");
            int items = size(call(registry, "getItemList"));
            require(items > 0, "JEI item registry is empty");
            Object overlay = call(runtime, "getItemListOverlay");
            require(overlay != null, "JEI has no item overlay");
            Object recipes = call(runtime, "getRecipeRegistry");
            List<?> categories = (List<?>) recipes.getClass().getMethod("getRecipeCategoriesWithOutput", ItemStack.class)
                .invoke(recipes, new ItemStack(Items.DIAMOND_PICKAXE));
            require(!categories.isEmpty(), "JEI did not index the vanilla diamond pickaxe recipe");
            boolean open = (boolean) call(overlay, "isOpen");
            if (inventory) require(open && field(overlay, "guiProperties") != null, "JEI did not receive the real inventory GUI events");
            if (inventory) CHECKS.put("forgeInventoryDrawEvents", Map.copyOf(DRAW_EVENTS));
            CHECKS.put("JEI" + (inventory ? "Inventory" : "World"),
                Map.of("items", items, "recipeCategories", categories.size(), "overlayOpen", open));
        }
        if (expected.contains("NotEnoughItems")) {
            require(Boolean.TRUE.equals(staticField("codechicken.nei.NEIClientConfig", "configLoaded")), "NEI client configuration was never initialized");
            require(staticField("codechicken.nei.NEIClientConfig", "global") != null, "NEI has no global config");
            if (inventory) {
                Object manager = staticCall("codechicken.nei.guihook.GuiContainerManager", "getManager");
                require(manager != null, "NEI coremod did not attach a manager to the actual inventory");
                int items = size(staticField("codechicken.nei.ItemList", "items"));
                require(items > 0, "NEI item loading did not finish");
                CHECKS.put("neiInventory", Map.of("manager", manager.getClass().getName(), "items", items));
            } else CHECKS.put("neiWorld", Map.of("configLoaded", true));
        }
        if (expected.contains("Waila")) {
            require(staticField("mcp.mobius.waila.Waila", "instance") != null && staticField("mcp.mobius.waila.Waila", "proxy") != null,
                "Waila instance or client proxy was not injected");
            Object registrar = staticCall("mcp.mobius.waila.api.impl.ModuleRegistrar", "instance");
            Map<?, ?> head = (Map<?, ?>) field(registrar, "headBlockProviders");
            Map<?, ?> body = (Map<?, ?>) field(registrar, "bodyBlockProviders");
            require(!head.isEmpty() && !body.isEmpty(), "Waila did not register its block providers");
            int wawlaProviders = 0;
            for (String key : List.of("headBlockProviders", "bodyBlockProviders", "tailBlockProviders", "headEntityProviders", "bodyEntityProviders", "tailEntityProviders")) {
                for (Object providers : ((Map<?, ?>) field(registrar, key)).values()) {
                    for (Object provider : (Collection<?>) providers) if (provider.getClass().getName().startsWith("net.darkhax.wawla.")) wawlaProviders++;
                }
            }
            if (!inventory) {
                Minecraft mc = Minecraft.getInstance();
                require(mc.crosshairTarget != null && new BlockPos(8, 4, 11).equals(mc.crosshairTarget.getPos()), "The real Waila target is not under the crosshair");
                Object tick = staticCall("mcp.mobius.waila.overlay.WailaTickHandler", "instance");
                Collection<?> lines = (Collection<?>) field(tick, "currenttipHead");
                require(!lines.isEmpty(), "Waila render/client events did not build a target tooltip");
                List<String> text = lines.stream().map(String::valueOf).toList();
                CHECKS.put("wailaTarget", Map.of("lines", text));
                require(text.stream().anyMatch(line -> line.contains("Stone")), "Waila did not show the translated vanilla Stone name: " + text);
            }
            if (expected.contains("wawla")) require(wawlaProviders > 0, "Wawla never registered providers with Waila");
            CHECKS.put("Waila", Map.of("headBlockTypes", head.size(), "bodyBlockTypes", body.size(), "wawlaProviders", wawlaProviders));
        }
        if (expected.contains("journeymap")) {
            Object client = staticCall("journeymap.client.JourneymapClient", "getInstance");
            require(client != null && Boolean.TRUE.equals(call(client, "isInitialized")), "JourneyMap client initialization did not finish");
            CHECKS.put("journeymap", Map.of("initialized", true, "mapping", Boolean.TRUE.equals(call(client, "isMapping"))));
        }
        if (expected.contains("keystrokes")) {
            require(staticCall("keystrokesmod.Raven", "getModuleManager") != null, "KeystrokesMod did not create its module manager");
            int modules = size(staticField("keystrokesmod.module.ModuleManager", "modules"));
            require(modules > 0, "KeystrokesMod module registration is empty");
            CHECKS.put("keystrokes", Map.of("modules", modules, "metadataIdentity", "KeystrokesMod"));
        }
        if (expected.contains("wawla")) {
            require(staticField("net.darkhax.wawla.Wawla", "instance") != null && staticField("net.darkhax.wawla.Wawla", "proxy") != null,
                "Wawla instance or proxy was not injected");
        }
    }

    private static void verifyWatermark(Minecraft mc, String name) throws Exception {
        String base = "io.github.arcadiumclient.runtime.client.gui.";
        Class<?> watermark = Class.forName(base + "ArcadiumWatermark");
        int[] bounds = (int[]) watermark.getMethod("bounds").invoke(null);
        require(bounds.length == 4 && bounds[2] > 0 && bounds[3] > 0, "The inventory watermark did not render");
        Class<?> rectangle = Class.forName(base + "WatermarkPlacement$Rect");
        Object actual = rectangle.getConstructor(int.class, int.class, int.class, int.class)
            .newInstance(bounds[0], bounds[1], bounds[2], bounds[3]);
        Class<?> overlays = Class.forName(base + "ArcadiumWatermarkOverlays");
        Collection<?> regions = (Collection<?>) overlays.getMethod("regions", Screen.class).invoke(overlays.getField("INSTANCE").get(null), mc.screen);
        var pixels = Class.forName(base + "WatermarkPlacement").getMethod("pixels", rectangle, int.class, int.class, int.class, int.class);
        var overlaps = rectangle.getMethod("overlaps", rectangle);
        int collisions = 0;
        List<List<Integer>> obstacles = new ArrayList<>();
        for (Object region : regions) {
            Object physical = pixels.invoke(null, region, mc.screen.width, mc.screen.height, mc.width, mc.height);
            if (Boolean.TRUE.equals(overlaps.invoke(actual, physical))) collisions++;
            obstacles.add(List.of((int) call(physical, "x"), (int) call(physical, "y"), (int) call(physical, "width"), (int) call(physical, "height")));
        }
        CHECKS.put(name, Map.of("bounds", bounds, "overlapCount", collisions, "obstacles", obstacles));
        require(collisions == 0, "The Arcadium watermark overlaps a container, overlay control, or hotbar");
    }

    public static void afterFrame() {
        if (!enabled() || done) return;
        try {
            if (Boolean.getBoolean("arcadium.crosshair034Qa")) Crosshair034Qa.afterFrame();
            if (Boolean.getBoolean("arcadium.autoText034Qa")) Messaging033Qa.afterFrame();
            if (Boolean.getBoolean("arcadium.release035Qa")) {
                CuteVisuals035Qa.afterFrame();
                Animations035Qa.afterFrame();
            }
            if (Boolean.getBoolean("arcadium.release033Qa")) {
                FontControlsQa.afterFrame();
                Visual033Qa.afterRender();
                Messaging033Qa.afterFrame();
            }
            ForgeEntityStressQa.afterFrame();
            Minecraft mc = Minecraft.getInstance();
            int calls = nativeHudCalls;
            nativeHudCalls = 0;
            if (phase == 2 && setup && WORLD_READY.get() && worldTicks >= 90 && mc.world != null && mc.screen == null) {
                require(calls == 1, "Arcadium HUD must render once per world frame, but rendered " + calls + " times");
                verifiedHudFrames++;
            }
            if (capture != null) {
                Screenshot.take(mc.gameDir, "forge-qa-" + capture, mc.width, mc.height, mc.getRenderTarget());
                PENDING.put(capture, System.nanoTime());
                capture = null;
            }
            for (String name : new ArrayList<>(PENDING.keySet())) {
                require(System.nanoTime() - PENDING.get(name) < 15_000_000_000L, "Forge QA screenshot did not finish: " + name);
                Path source = mc.gameDir.toPath().resolve("screenshots/forge-qa-" + name);
                if (!Files.isRegularFile(source)) continue;
                byte[] bytes = Files.readAllBytes(source);
                int n = bytes.length;
                if (n < 24 || bytes[n - 8] != 'I' || bytes[n - 7] != 'E' || bytes[n - 6] != 'N' || bytes[n - 5] != 'D') continue;
                Files.createDirectories(output(mc));
                Files.write(output(mc).resolve(name), bytes);
                if (name.equals("02-world.png")) verifyFpsPixels(source);
                PENDING.remove(name);
                CAPTURED.add(name);
                write(mc);
            }
        } catch (Throwable failure) { fail(failure); }
    }

    public static void recordNativeHud() { nativeHudCalls++; }
    public static void beginItem(net.minecraft.client.render.model.block.ModelTransformations.Type transform) { ForgeRenderQa.beginItem(transform); }
    public static void endItem() { ForgeRenderQa.endItem(); }
    public static void recordItem(ItemStack stack, net.minecraft.client.resource.model.BakedModel model) { ForgeRenderQa.recordItem(stack, model); }
    public static void recordHeldBlock(ItemStack stack, net.minecraft.client.resource.model.BakedModel model, net.minecraft.client.render.model.block.ModelTransformations.Type transform) {
        HeldBlockQa.record(stack, model, transform);
        Animations035Qa.recordHeldItem(stack, model, transform);
    }
    public static void recordHotbar(int slot, int x, int y) { ForgeRenderQa.recordHotbar(slot, x, y); }

    private static void verifyFpsHud() throws Exception {
        if (fpsHud == null) return;
        require(verifiedHudFrames > 10, "The FPS HUD was not checked over real rendered frames");
        require(Boolean.TRUE.equals(call(field(fpsHud, "isVisible"), "getValue")), "The enabled FPS HUD is not visible");
        require(((Number) call(fpsHud, "getRenderedW")).floatValue() > 0
            && ((Number) call(fpsHud, "getRenderedH")).floatValue() > 0, "The FPS HUD has no rendered dimensions");
        var getter = fpsHud.getClass().getDeclaredMethod("getText"); getter.setAccessible(true);
        String text = String.valueOf(getter.invoke(fpsHud));
        require(text.matches(".*[0-9].*"), "The FPS HUD does not contain a measured frame rate: " + text);
        CHECKS.put("arcadiumFpsHud", Map.of("visible", true, "text", text, "framesChecked", verifiedHudFrames, "callsPerFrame", 1));
    }

    private static void verifyFpsPixels(Path source) throws Exception {
        if (fpsHud == null) return;
        var image = javax.imageio.ImageIO.read(source.toFile());
        float guiWidth = ((Number) staticField("org.polyfrost.oneconfig.api.hud.v1.HudManager", "guiScreenWidth")).floatValue();
        float scale = image.getWidth() / guiWidth;
        int x = Math.max(0, (int) (((Number) call(fpsHud, "getX")).floatValue() * scale) - 2);
        int y = Math.max(0, (int) (((Number) call(fpsHud, "getY")).floatValue() * scale) - 2);
        int right = Math.min(image.getWidth(), x + (int) Math.ceil(((Number) call(fpsHud, "getRenderedW")).floatValue() * scale) + 4);
        int bottom = Math.min(image.getHeight(), y + (int) Math.ceil(((Number) call(fpsHud, "getRenderedH")).floatValue() * scale) + 4);
        int white = 0;
        for (int py = y; py < bottom; py++) for (int px = x; px < right; px++) {
            int rgb = image.getRGB(px, py);
            if ((rgb >> 16 & 255) >= 245 && (rgb >> 8 & 255) >= 245 && (rgb & 255) >= 245) white++;
        }
        require(white > 8, "The FPS text is missing from its actual screenshot bounds");
        CHECKS.put("arcadiumFpsPixels", Map.of("whiteTextPixels", white, "bounds", List.of(x, y, right, bottom)));
    }

    private static void take(String name) { require(capture == null, "A Forge QA screenshot is already queued"); capture = name; }

    public static void recordGuiDraw(String kind, Object event) {
        try {
            if (field(event, "gui") == Minecraft.getInstance().screen) DRAW_EVENTS.merge(kind, 1, Integer::sum);
        } catch (Exception failure) { throw new IllegalStateException("Forge GUI probe could not inspect its event", failure); }
    }

    private static void installDrawProbe() throws Exception {
        String name = "io/github/arcadiumclient/featuretest/ForgeGuiDrawProbe";
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL, name, null, "java/lang/Object", null);
        var constructor = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        constructor.visitCode(); constructor.visitVarInsn(Opcodes.ALOAD, 0);
        constructor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        constructor.visitInsn(Opcodes.RETURN); constructor.visitMaxs(1, 1); constructor.visitEnd();
        for (String kind : List.of("Pre", "Post", "Background")) {
            String event = kind.equals("Background") ? "net/minecraftforge/client/event/GuiScreenEvent$BackgroundDrawnEvent"
                : "net/minecraftforge/client/event/GuiScreenEvent$DrawScreenEvent$" + kind;
            var method = writer.visitMethod(Opcodes.ACC_PUBLIC, "draw" + kind, "(L" + event + ";)V", null, null);
            method.visitAnnotation("Lnet/minecraftforge/fml/common/eventhandler/SubscribeEvent;", true).visitEnd();
            method.visitCode(); method.visitLdcInsn(kind); method.visitVarInsn(Opcodes.ALOAD, 1);
            method.visitMethodInsn(Opcodes.INVOKESTATIC, "io/github/arcadiumclient/featuretest/ForgeQa", "recordGuiDraw",
                "(Ljava/lang/String;Ljava/lang/Object;)V", false);
            method.visitInsn(Opcodes.RETURN); method.visitMaxs(2, 2); method.visitEnd();
        }
        writer.visitEnd();
        drawProbe = MethodHandles.lookup().defineClass(writer.toByteArray()).getConstructor().newInstance();
        Object bus = staticField("net.minecraftforge.common.MinecraftForge", "EVENT_BUS");
        bus.getClass().getMethod("register", Object.class).invoke(bus, drawProbe);
    }
    private static void advance(Minecraft mc) throws Exception { phase++; phaseTicks = 0; write(mc); }
    private static Path output(Minecraft mc) { return mc.gameDir.toPath().resolve("forge-qa"); }
    private static void write(Minecraft mc) throws Exception {
        RESULT.put("phase", PHASES[phase]);
        RESULT.put("ticks", ticks);
        Files.createDirectories(output(mc));
        Path pending = output(mc).resolve("result.pending.json");
        Files.writeString(pending, new GsonBuilder().setPrettyPrinting().create().toJson(RESULT));
        for (int attempt = 0; ; attempt++) {
            try {
                Files.move(pending, output(mc).resolve("result.json"), StandardCopyOption.REPLACE_EXISTING);
                break;
            } catch (java.nio.file.FileSystemException locked) {
                if (attempt >= 49) throw locked;
                Thread.sleep(10);
            }
        }
    }
    private static void complete(Minecraft mc) throws Exception {
        int expectedCaptures = 5 + (expected.contains("JEI") ? 1 : 0)
            + (expected.contains("NotEnoughItems") ? 1 : 0)
            + (expected.contains("journeymap") ? 1 : 0) + (expected.contains("keystrokes") ? 1 : 0);
        require(CAPTURED.size() == expectedCaptures && PENDING.isEmpty() && capture == null,
            "Forge QA is missing scene evidence");
        RESULT.put("passed", true);
        write(mc);
        done = true;
        System.out.println("Arcadium Forge QA: completed");
        if (Boolean.getBoolean("arcadium.featureTest.exit")) mc.stop();
    }
    private static void fail(Throwable failure) {
        if (done) return;
        while (failure instanceof InvocationTargetException invocation && invocation.getCause() != null) failure = invocation.getCause();
        done = true;
        failure.printStackTrace();
        try { restoreJeiOverlay(); } catch (Exception restoreFailure) { restoreFailure.printStackTrace(); }
        RESULT.put("passed", false);
        RESULT.put("failure", failure.toString());
        try {
            Minecraft mc = Minecraft.getInstance();
            write(mc);
            if (Boolean.getBoolean("arcadium.featureTest.exit")) mc.stop();
        } catch (Exception secondary) { secondary.printStackTrace(); }
    }
    private static void restoreJeiOverlay() throws Exception {
        if (savedJeiOverlay == null) return;
        if (!savedJeiOverlay.equals(staticCall("mezz.jei.config.Config", "isOverlayEnabled"))) {
            staticCall("mezz.jei.config.Config", "toggleOverlayEnabled");
        }
        savedJeiOverlay = null;
    }
    private static Object staticCall(String type, String name) throws Exception { return Class.forName(type).getMethod(name).invoke(null); }
    private static Object call(Object target, String name) throws Exception { return target.getClass().getMethod(name).invoke(target); }
    private static Object staticField(String type, String name) throws Exception { return field(null, Class.forName(type), name); }
    private static Object field(Object target, String name) throws Exception { return field(target, target.getClass(), name); }
    private static Object field(Object target, Class<?> type, String name) throws Exception {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try { Field field = current.getDeclaredField(name); field.setAccessible(true); return field.get(target); }
            catch (NoSuchFieldException absent) { }
        }
        throw new NoSuchFieldException(type.getName() + "." + name);
    }
    private static int size(Object value) { require(value instanceof Collection<?>, "A guest runtime collection is missing"); return ((Collection<?>) value).size(); }
    private static void require(boolean value, String message) { if (!value) throw new IllegalStateException(message); }
}
