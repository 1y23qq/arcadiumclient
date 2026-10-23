// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.featuretest;

import static io.github.arcadiumclient.featuretest.Visual033Qa.*;

import com.google.gson.GsonBuilder;
import java.awt.image.BufferedImage;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.LiteralText;
import io.github.arcadiumclient.featuretest.mixin.ScreenProbeAccess;

public final class Feature045Qa {
    private static final String BASE = "io.github.arcadiumclient.runtime.client.";
    private static final Map<String, Object> RESULT = new LinkedHashMap<>();
    private static int ticks, stage;
    private static Screen parent;
    private static Object packs, maker, skins, selectedSkin;
    private static List<Object> sources;
    private static Path output;
    private static boolean capture, done;
    private Feature045Qa() { }
    public static boolean enabled() { return Boolean.getBoolean("arcadium.features045"); }

    public static void tick() {
        if (done || !enabled()) return;
        Minecraft mc = Minecraft.getInstance();
        try {
            require(mc.getSession().getAccessToken().equals("0") && mc.getSession().getUsername().equals("ArcadiumTest"), "QA requires its isolated offline account");
            require(++ticks < 1500, "Feature QA timed out at stage " + stage);
            if (ticks < 60 || ticks % 20 != 0) return;
            switch (stage++) {
                case 0 -> {
                    parent = mc.screen;
                    Object registry = object("org.polyfrost.oneconfig.internal.ui.api.ConfigRegistry");
                    Collection<?> cards = (Collection<?>) call(registry, "getModCardConfigs");
                    require(cards.stream().anyMatch(card -> id(card).equals("arcadium-chat")), "The Chat module group is missing");
                    require(cards.stream().noneMatch(card -> List.of("chatting.json", "chattweaks.json", "arcadium-chat-filters.json", "arcadium-auto-text.json").contains(id(card))), "Duplicate chat cards remain");
                    RESULT.put("chatModulesGrouped", true);
                    sources = new ArrayList<>();
                    Class<?> sourceType = Class.forName(BASE + "resourcepack.CustomPackMaker$Source");
                    for (int index = 0; index < 4; index++) {
                        Path folder = mc.gameDir.toPath().resolve("resourcepacks/QA/Pack " + index);
                        Files.createDirectories(folder);
                        Files.writeString(folder.resolve("pack.mcmeta"), "{\"pack\":{\"pack_format\":1,\"description\":\"QA pack\"}}");
                        String asset = new String[]{"textures/items/diamond_sword.png", "textures/blocks/stone.png", "mcpatcher/sky/world0/sky1.png", "textures/items/fire_charge.png"}[index];
                        Path file = folder.resolve("assets/minecraft/" + asset); Files.createDirectories(file.getParent());
                        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
                        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) image.setRGB(x, y, 0xFF00AA44 + index * 0x00220022);
                        ImageIO.write(image, "png", file.toFile());
                        sources.add(sourceType.getConstructor(String.class, Path.class).newInstance("Pack " + index, folder));
                    }
                    packs = Class.forName(BASE + "gui.ArcadiumResourcePacksScreen").getConstructor(Screen.class).newInstance(parent);
                    mc.openScreen((Screen) packs); capture = true;
                }
                case 1 -> {
                    Class<?> fn = Class.forName("kotlin.jvm.functions.Function0");
                    Object callback = Proxy.newProxyInstance(fn.getClassLoader(), new Class<?>[]{fn}, (proxy, method, args) -> Class.forName("kotlin.Unit").getField("INSTANCE").get(null));
                    maker = Class.forName(BASE + "gui.CustomTexturePackScreen").getConstructors()[0].newInstance(packs, mc.gameDir.toPath().resolve("resourcepacks"), sources, sources.get(0), callback);
                    Map<Object, Object> choices = new LinkedHashMap<>();
                    Class<? extends Enum> element = (Class<? extends Enum>) Class.forName(BASE + "resourcepack.CustomPackMaker$Element");
                    for (int index = 0; index < 4; index++) choices.put(Enum.valueOf(element, new String[]{"SWORDS", "BLOCKS", "SKY", "FIREBALL"}[index]), sources.get(index));
                    call(maker, "setChoices", choices);
                    call(maker, "setName", "QA Combined");
                    mc.openScreen((Screen) maker); capture = true;
                }
                case 2 -> call(maker, "create");
                case 3 -> {
                    if ((boolean) call(maker, "getBusy")) { stage--; return; }
                    require(call(maker, "getError") == null, "Pack maker failed: " + call(maker, "getError"));
                    output = mc.gameDir.toPath().resolve("resourcepacks/QA Combined.zip");
                    require(Files.isRegularFile(output), "The combined ZIP was not saved");
                    RESULT.put("customPackCreated", output.getFileName().toString());
                    mc.getResourcePacks().load();
                    require(mc.getResourcePacks().getAvailable().stream().anyMatch(pack -> pack.getName().contains("QA Combined")), "The combined pack is not discoverable");
                    capture = true;
                }
                case 4 -> {
                    Object changer = object(BASE + "skins.SkinChanger");
                    Object library = call(changer, "getLibrary");
                    BufferedImage skin = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
                    for (int y = 0; y < 64; y++) for (int x = 0; x < 64; x++) skin.setRGB(x, y, 0xFF3388DD);
                    Path file = mc.gameDir.toPath().resolve("qa-skin.png"); ImageIO.write(skin, "png", file.toFile());
                    selectedSkin = call(library, "add", file, "QA local skin", "slim");
                    skins = Class.forName(BASE + "gui.SkinChangerScreen").getConstructor(Screen.class).newInstance(parent);
                    mc.openScreen((Screen) skins);
                }
                case 5 -> {
                    if (call(skins, "getBusy") != null) { stage--; return; }
                    Object entry = ((List<?>) call(skins, "getEntries")).get(0);
                    call(skins, "pick", entry);
                    call(skins, "applySkin");
                }
                case 6 -> {
                    if (call(skins, "getBusy") != null) { stage--; return; }
                    require(call(skins, "getError") == null, "Cracked skin apply failed: " + call(skins, "getError"));
                    require(stat(BASE + "skins.SkinChanger", "textureFor", mc.getSession().getProfile().getId()) != null, "The local skin texture was not installed");
                    require("slim".equals(stat(BASE + "skins.SkinChanger", "modelFor", mc.getSession().getProfile().getId())), "The Slim model was not applied");
                    RESULT.put("crackedSkinAppliedInGame", true); capture = true;
                }
                case 7 -> {
                    if (!LevelHead045Qa.tick(mc)) { stage--; return; }
                    RESULT.put("levelHeadAndEmojiInGame", true);
                    mc.gui.getChat().addMessage(new LiteralText("Emoji :smile: :heart: :fire: :thumbsup:"));
                    Screen chat = new ChatScreen("Hello ");
                    mc.openScreen(chat);
                    ScreenProbeAccess clicks = (ScreenProbeAccess) chat;
                    clicks.arcadium$click(chat.width - 25, chat.height - 32, 0);
                    int cell = Math.max(12, Math.min(20, (chat.height - 66) / 8));
                    clicks.arcadium$click(chat.width - cell * 8 - 12 + cell + 5, chat.height - 43 - cell * 8 - 22 + 5, 0);
                    boolean inserted = false;
                    for (Class<?> type = chat.getClass(); type != null; type = type.getSuperclass()) for (var field : type.getDeclaredFields()) {
                        if (field.getType() != TextFieldWidget.class) continue;
                        field.setAccessible(true);
                        TextFieldWidget input = (TextFieldWidget) field.get(chat);
                        if (input != null && input.getText().startsWith("Hello ")) inserted = input.getText().contains(":smile:");
                    }
                    require(inserted, "The emoji picker did not insert a shortcode into the chat input");
                    RESULT.put("emojiPickerInput", true); capture = true;
                }
                case 8 -> {
                    RESULT.put("passed", true); done = true;
                    Files.writeString(mc.gameDir.toPath().resolve("features045-result.json"), new GsonBuilder().setPrettyPrinting().create().toJson(RESULT));
                    mc.stop();
                }
                default -> throw new AssertionError("Invalid stage");
            }
        } catch (Throwable failure) {
            failure.printStackTrace(); RESULT.put("passed", false); RESULT.put("error", failure.toString()); done = true;
            try { Files.writeString(mc.gameDir.toPath().resolve("features045-result.json"), new GsonBuilder().setPrettyPrinting().create().toJson(RESULT)); } catch (Exception ignored) { }
            mc.stop();
        }
    }

    private static String id(Object value) {
        try { return call(value, "getId").toString(); } catch (Exception failure) { throw new IllegalStateException(failure); }
    }

    public static void afterFrame() {
        LevelHead045Qa.afterFrame();
        if (!capture || done) return;
        capture = false;
        Minecraft mc = Minecraft.getInstance();
        Screenshot.take(mc.gameDir, "features045-" + stage + ".png", mc.width, mc.height, mc.getRenderTarget());
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
