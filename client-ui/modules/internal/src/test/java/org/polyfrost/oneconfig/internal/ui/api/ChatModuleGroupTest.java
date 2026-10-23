// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package org.polyfrost.oneconfig.internal.ui.api;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.polyfrost.oneconfig.api.config.v1.Properties;
import org.polyfrost.oneconfig.api.config.v1.Tree;
import org.polyfrost.oneconfig.internal.ui.search.ConfigDocumentSource;

class ChatModuleGroupTest {
    private final ArrayList<ConfigData> previous = new ArrayList<>(ConfigRegistry.INSTANCE.getConfigs());

    @AfterEach void restoreRegistry() {
        ConfigRegistry.INSTANCE.getConfigs().clear();
        ConfigRegistry.INSTANCE.getConfigs().addAll(previous);
    }

    @Test void groupsOnlyTheFourOriginalConfigsAndPreservesTheirPropertyObjects() {
        ConfigRegistry.INSTANCE.getConfigs().clear();
        for (String id : ChatModuleGroup.INSTANCE.getIds()) {
            Tree tree = Tree.tree(id);
            tree.setTitle(id);
            tree.put(Properties.simple("enabled", "Enabled", null, false, Boolean.class));
            ConfigRegistry.INSTANCE.registerTree(tree, ConfigSource.OC, null, false);
            assertTrue(ChatModuleGroup.INSTANCE.contains(id.replace(".json", "")));
            assertTrue(ChatModuleGroup.INSTANCE.contains(id.toUpperCase()));
            assertFalse(ConfigRegistry.INSTANCE.shouldShowModCardId(id));
            assertSame(tree, ConfigRegistry.INSTANCE.findTree(id));
        }
        assertEquals(4, ChatModuleGroup.INSTANCE.children().size());
        var cards = ConfigRegistry.INSTANCE.getModCardConfigs();
        assertEquals(1, cards.stream().filter(it -> it == ChatModuleGroup.INSTANCE).count());
        assertTrue(cards.stream().noneMatch(it -> ChatModuleGroup.INSTANCE.contains(it.getId())));
        assertFalse(ChatModuleGroup.INSTANCE.contains("chat-filters-addon.json"));
        assertSame(ChatModuleGroup.INSTANCE, ConfigRegistry.INSTANCE.findById("arcadium-chat"));
    }

    @Test void moduleSearchHasOneChatCardAndKeepsTheChildSettingDocuments() {
        ConfigRegistry.INSTANCE.getConfigs().clear();
        Tree tree = Tree.tree("chattweaks.json");
        tree.setTitle("Chat Tweaks");
        var emoji = Properties.simple("emoji", "Enable Emoji", null, true, Boolean.class);
        emoji.addMetadata("visualizer", "test-switch");
        tree.put(emoji);
        ConfigRegistry.INSTANCE.registerTree(tree, ConfigSource.OC, null, false);
        var documents = ConfigDocumentSource.INSTANCE.documents();
        assertTrue(documents.stream().anyMatch(it -> it.getId().equals("mod::arcadium-chat")));
        assertTrue(documents.stream().noneMatch(it -> it.getId().equals("mod::chattweaks.json")));
        assertTrue(documents.stream().anyMatch(it -> it.getId().equals("chattweaks.json::emoji")));
    }
}
