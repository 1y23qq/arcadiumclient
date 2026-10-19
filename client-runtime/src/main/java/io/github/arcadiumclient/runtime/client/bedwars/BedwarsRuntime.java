// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.client.bedwars;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.properties.Property;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.game.BookEditScreen;
import net.minecraft.client.gui.screen.game.inventory.ChestScreen;
import net.minecraft.client.network.PlayerInfo;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtList;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.team.Team;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import io.github.arcadiumclient.runtime.mixin.client.MixinBedwarsBookAccessor;
import io.github.arcadiumclient.runtime.mixin.client.MixinBedwarsChestAccessor;
import io.github.arcadiumclient.runtime.mixin.client.MixinBedwarsGuiAccessor;

final class BedwarsRuntime {
    static final class Entry {
        final UUID id;
        final String name;
        final String vanilla;
        final String teamPrefix;
        final String skin;
        Entry(UUID id, String name, String vanilla, String teamPrefix, String skin) {
            this.id = id; this.name = name; this.vanilla = vanilla; this.teamPrefix = teamPrefix; this.skin = skin;
        }
    }

    private static final NickBookReader BOOKS = new NickBookReader();

    static Object world() { return Minecraft.getInstance().world; }
    static String server() {
        net.minecraft.client.options.ServerListEntry server = Minecraft.getInstance().getCurrentServerEntry();
        return server == null ? "" : server.ip;
    }
    static UUID localId() { return Minecraft.getInstance().player == null ? null : Minecraft.getInstance().player.getGameProfile().getId(); }
    static String sidebar() {
        if (Minecraft.getInstance().world == null) return "";
        ScoreboardObjective objective = Minecraft.getInstance().world.getScoreboard().getDisplayObjective(1);
        return objective == null ? "" : PlayerNameUtil.plain(objective.getDisplayName());
    }
    static String title() {
        Object gui = Minecraft.getInstance().gui;
        return gui instanceof MixinBedwarsGuiAccessor ? PlayerNameUtil.plain(((MixinBedwarsGuiAccessor) gui).arcadium$bedwarsTitle()) : "";
    }
    static Path directory() { return Minecraft.getInstance().gameDir.toPath().resolve("config/arcadium"); }
    static void chat(String message) {
        if (Minecraft.getInstance().gui != null) Minecraft.getInstance().gui.getChat().addMessage(new LiteralText(message));
    }
    static void command(String command) {
        if (Minecraft.getInstance().player != null) Minecraft.getInstance().player.sendChat(command);
    }
    static List<Entry> roster() {
        List<Entry> entries = new ArrayList<>();
        if (Minecraft.getInstance().getNetworkHandler() == null) return entries;
        for (PlayerInfo info : Minecraft.getInstance().getNetworkHandler().getOnlinePlayers()) {
            UUID id = info.getProfile().getId();
            String name = info.getProfile().getName();
            if (id == null || !PlayerNameUtil.valid(name)) continue;
            Team team = info.getTeam();
            String vanilla = info.getDisplayName() == null ? Team.getMemberDisplayName(team, name) : info.getDisplayName().getFormattedString();
            String skin = "";
            java.util.Collection<Property> textures = info.getProfile().getProperties().get("textures");
            if (!textures.isEmpty()) skin = textures.iterator().next().getValue();
            entries.add(new Entry(id, name, vanilla, team == null ? "" : team.getPrefix(), skin));
            if (entries.size() >= 128) break;
        }
        return entries;
    }
    static Object screen() { return Minecraft.getInstance().screen; }
    static String bookNick(Object screen) {
        if (!(screen instanceof BookEditScreen)) return "";
        String nick = BOOKS.read(screen, Text.class, value -> ((Text) value).getString());
        if (!nick.isEmpty()) return nick;
        NbtList pages = ((MixinBedwarsBookAccessor) screen).arcadium$bedwarsPages();
        if (pages == null) return "";
        for (int page = 0; page < Math.min(8, pages.size()); page++) {
            String value = pages.getString(page);
            if (value.length() > 16384) continue;
            try {
                Text text = Text.Serializer.fromJson(value);
                if (text == null) continue;
                String plain = text.getString();
                java.util.regex.Matcher match = java.util.regex.Pattern.compile("(?im)\\byou:\\s*([A-Za-z0-9_]{1,16})(?:\\s|$)").matcher(plain);
                if (match.find()) return match.group(1);
            } catch (RuntimeException ignored) { }
        }
        return "";
    }
    static void closeBook(Object screen) {
        if (screen != Minecraft.getInstance().screen || !(screen instanceof BookEditScreen)) return;
        Minecraft.getInstance().openScreen(null);
    }
    static boolean lobby() {
        if (Minecraft.getInstance().player == null) return false;
        ItemStack item = Minecraft.getInstance().player.inventory.getItem(4);
        return item != null && Item.getId(item.getItem()) == 146;
    }
    static boolean limbo() { return Minecraft.getInstance().world != null && Minecraft.getInstance().world.dimension.getId() == 1; }
    static int selectedSlot() { return Minecraft.getInstance().player.inventory.selectedSlot; }
    static void selectSlot(int slot) { if (Minecraft.getInstance().player != null) Minecraft.getInstance().player.inventory.selectedSlot = slot; }
    static void openLobbySelector() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.interactionManager == null) return;
        ItemStack item = mc.player.inventory.getItem(8);
        if (item == null) return;
        selectSlot(8);
        mc.interactionManager.useItem(mc.player, mc.world, item);
    }
    static boolean selectLobby() {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof ChestScreen) || mc.player == null || mc.interactionManager == null) return false;
        Inventory inventory = ((MixinBedwarsChestAccessor) mc.screen).arcadium$bedwarsInventory();
        if (!PlayerNameUtil.plain(inventory.getName()).endsWith("Lobby Selector")) return false;
        for (int slot = inventory.getSize() - 1; slot >= 0; slot--) {
            ItemStack stack = inventory.getItem(slot);
            if (stack != null && stack.getHoverName().startsWith("§a") && stack.getHoverName().contains("Lobby #")) {
                mc.interactionManager.clickSlot(mc.player.menu.networkId, slot, 0, 0, mc.player);
                return true;
            }
        }
        return false;
    }
}
