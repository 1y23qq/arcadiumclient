// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.client.features

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.nio.file.Files
import java.nio.file.Path
import java.lang.reflect.Field
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screen.game.ChatScreen
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.screen.game.BookEditScreen
import net.minecraft.client.gui.screen.game.SignEditScreen
import net.minecraft.client.gui.screen.menu.options.ControlsOptionsScreen
import net.minecraft.client.gui.widget.TextFieldWidget
import org.apache.logging.log4j.LogManager
import org.polyfrost.oneconfig.api.config.v1.Config
import org.polyfrost.oneconfig.api.config.v1.Properties
import org.polyfrost.oneconfig.api.config.v1.Property
import org.polyfrost.oneconfig.api.config.v1.annotations.Button
import org.polyfrost.oneconfig.api.config.v1.annotations.Switch
import org.polyfrost.oneconfig.api.ui.v1.keybind.KeybindManager
import org.polyfrost.oneconfig.api.ui.v1.keybind.OneConfigKeybind
import org.polyfrost.oneconfig.api.ui.v1.keybind.TextInputFocus
import org.polyfrost.oneconfig.internal.ui.keybind.KeybindRecordingBus
import io.github.arcadiumclient.runtime.client.gui.ArcadiumAutoTextScreen

object AutoTextConfig : Config("arcadium-auto-text.json", "AutoText", Category.QOL) {
    @JvmStatic @Switch(title = "Enabled")
    var enabled = false

    @JvmStatic @Switch(title = "Only While Playing", description = "Ignore shortcuts while a menu is open. Typing in chat is always protected.")
    var onlyWhilePlaying = true

    @Button(title = "Messages", text = "Manage messages")
    fun edit() {
        val mc = Minecraft.getInstance()
        mc.tell { mc.openScreen(ArcadiumAutoTextScreen(mc.screen)) }
    }
}

object AutoText {
    const val MAX_ENTRIES = AutoTextData.MAX_ENTRIES
    private val logger = LogManager.getLogger("Arcadium/AutoText")
    private val file: Path get() = FabricLoader.getInstance().configDir.resolve("arcadium/autotext.json")
    private val properties = HashMap<String, Property<OneConfigKeybind>>()
    private val nativeInputs = HashMap<Class<*>, List<Field>>()
    private val presses = AutoTextPressGate()
    private var store: AutoTextFileStore? = null
    private var writable = true
    private var initialized = false
    private val lastSent = HashMap<String, Long>()
    var entries by mutableStateOf<List<AutoTextEntry>>(emptyList())
        private set
    var error by mutableStateOf<String?>(null)
        private set

    @JvmStatic
    fun initialize() {
        if (initialized) return
        initialized = true
        AutoTextConfig.preload()
        try {
            if (Files.exists(file)) {
                entries = AutoTextData.read(file)
            }
        } catch (_: Exception) {
            writable = false
            error = "Could not load AutoText data. Your file has been kept unchanged."
            logger.warn("Could not load AutoText data; changes are disabled to preserve the original file")
        }
        if (writable) store = AutoTextFileStore(file) {
            logger.warn("Could not save AutoText data")
            Minecraft.getInstance().tell { error = "Could not save AutoText changes. Try saving again." }
        }
        entries.forEach { binding(it.id) }
        KeybindManager.addMenuEditListener { bind ->
            val id = properties.entries.firstOrNull { it.value.get() === bind }?.key ?: return@addMenuEditListener
            storeBinding(id, bind)
        }
        Runtime.getRuntime().addShutdownHook(Thread({ store?.close() }, "Arcadium-AutoText-Shutdown"))
    }

    fun canAdd(): Boolean = writable && entries.size < MAX_ENTRIES

    fun add() {
        if (!canAdd()) return
        val entry = AutoTextEntry(name = "Message ${entries.size + 1}")
        entries = entries + entry
        binding(entry.id)
        scheduleSave()
    }

    fun update(entry: AutoTextEntry) {
        if (!writable) return
        val clean = AutoTextData.checked(entry)
        entries = entries.map { if (it.id == clean.id) clean else it }
        refreshNames()
        scheduleSave()
    }

    fun rename(id: String, name: String) = edit(id) { it.copy(name = name) }
    fun setText(id: String, text: String) = edit(id) { it.copy(text = text.take(100)) }
    fun toggle(id: String) = edit(id) { it.copy(enabled = !it.enabled) }

    private fun edit(id: String, change: (AutoTextEntry) -> AutoTextEntry) {
        if (!writable) return
        entries = AutoTextData.edit(entries, id, change)
        refreshNames()
        scheduleSave()
    }

    private fun refreshNames() {
        entries.forEachIndexed { index, entry ->
            val bind = properties[entry.id]?.get() ?: return@forEachIndexed
            val title = "AutoText ${index + 1}: ${entry.name.ifBlank { "Message" }}"
            if (bind.name != title) {
                KeybindManager.unregister(bind)
                presses.forget(entry.id)
                bind.name = title
                KeybindManager.register(bind)
            }
        }
    }

    fun remove(id: String) {
        if (!writable) return
        properties.remove(id)?.get()?.let(KeybindManager::unregister)
        entries = entries.filterNot { it.id == id }
        lastSent.remove(id)
        presses.forget(id)
        refreshNames()
        scheduleSave()
    }

    fun binding(id: String): Property<OneConfigKeybind> = properties.getOrPut(id) {
        val entry = entries.first { it.id == id }
        val bind = OneConfigKeybind(entry.keys.toIntArray(), entry.mouse.toIntArray(), entry.modifiers.toByte(), 0L) { down ->
            if (presses.press(id, down)) send(id)
            true
        }.apply {
            name = "AutoText ${entries.indexOf(entry) + 1}: ${entry.name.ifBlank { "Message" }}"
            category = "Arcadium AutoText"
            defaultKeybind = OneConfigKeybind(null, null, 0, 0L) { true }
            // Track a held key through typing without sending it when focus changes.
            firesWhileTyping = true
        }
        KeybindManager.register(bind)
        Properties.simple(id = id, name = "Shortcut", value = bind).apply {
            addCallback { changed ->
                storeBinding(id, changed)
                false
            }
        }
    }

    private fun storeBinding(id: String, bind: OneConfigKeybind) {
        if (!writable) return
        entries = AutoTextData.edit(entries, id) {
            it.copy(keys = bind.keyCodes?.toList().orEmpty(), mouse = bind.mouseBtns?.toList().orEmpty(), modifiers = bind.mods.toInt())
        }
        presses.forget(id)
        scheduleSave()
    }

    @JvmStatic
    fun validMessage(text: String): Boolean = AutoTextData.validMessage(text)

    fun send(id: String): Boolean {
        val mc = Minecraft.getInstance()
        val player = mc.player ?: return false
        val entry = entries.firstOrNull { it.id == id } ?: return false
        if (!AutoTextConfig.enabled || !entry.enabled || mc.world == null || !validMessage(entry.text)) return false
        if (typing(mc.screen) || TextInputFocus.isTyping || KeybindRecordingBus.isRecording) return false
        if (AutoTextConfig.onlyWhilePlaying && mc.screen != null) return false
        val now = System.nanoTime()
        if (now - (lastSent[id] ?: Long.MIN_VALUE / 2) < 250_000_000L) return false
        lastSent[id] = now
        player.sendChat(entry.text.trim())
        return true
    }

    private fun typing(screen: Screen?): Boolean {
        if (screen == null) return false
        if (screen is ChatScreen || screen is BookEditScreen || screen is SignEditScreen
            || screen is ControlsOptionsScreen || screen is ArcadiumAutoTextScreen) return true
        val fields = nativeInputs.getOrPut(screen.javaClass) {
            val result = ArrayList<Field>()
            var type: Class<*>? = screen.javaClass
            while (type != null) {
                for (field in type.declaredFields) {
                    if (TextFieldWidget::class.java.isAssignableFrom(field.type) && runCatching { field.trySetAccessible() }.getOrDefault(false)) result += field
                }
                type = type.superclass
            }
            result
        }
        return fields.any { field -> runCatching { (field.get(screen) as? TextFieldWidget)?.isFocused == true }.getOrDefault(false) }
    }

    fun save() = scheduleSave()

    private fun scheduleSave() {
        if (!writable) return
        store?.schedule(entries)
    }
}
