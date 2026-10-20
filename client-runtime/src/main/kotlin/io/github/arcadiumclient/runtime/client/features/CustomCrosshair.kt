// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.client.features

import com.google.gson.GsonBuilder
import net.minecraft.client.Minecraft
import org.apache.logging.log4j.LogManager
import org.polyfrost.oneconfig.api.config.v1.Config
import org.polyfrost.oneconfig.api.config.v1.Properties
import org.polyfrost.oneconfig.api.config.v1.Property
import org.polyfrost.oneconfig.api.config.v1.Tree
import org.polyfrost.oneconfig.api.config.v1.Visualizer
import org.polyfrost.oneconfig.api.event.v1.eventHandler
import org.polyfrost.oneconfig.api.event.v1.events.TickEvent
import io.github.arcadiumclient.runtime.client.gui.CustomCrosshairScreen
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.function.Consumer
import java.util.function.Supplier
import org.polyfrost.oneconfig.internal.ui.api.ConfigRegistry

object CustomCrosshair {
    private class Document {
        var enabled = false
        var current = CrosshairDesign()
        var profiles = mutableListOf<CrosshairDesign>()
    }

    private val logger = LogManager.getLogger("Arcadium/Crosshair")
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val file = Path.of("config", "arcadium-crosshairs.json")
    private var data = Document()
    private var cachedMesh = data.current.mesh()
    private var attached: Tree? = null
    private var retry = 0
    private var warned = false
    private var builds = 1

    fun initialize() {
        runCatching {
            if (Files.isRegularFile(file) && Files.size(file) <= 8 * 1024 * 1024) {
                data = Files.newBufferedReader(file).use { gson.fromJson(it, Document::class.java) } ?: Document()
            }
            data.current.normalize()
            data.profiles = data.profiles.filterNotNull().take(64).map { it.apply { normalize() } }.toMutableList()
        }.onFailure { logger.warn("Could not read saved crosshairs", it); data = Document() }
        cachedMesh = data.current.mesh()
        attach()
        eventHandler { _: TickEvent.End ->
            if (++retry >= 20) { retry = 0; attach() }
        }
    }

    private fun attach() {
        runCatching {
            val config = Class.forName("dev.microcontrollers.crosshairtweaks.config.CrosshairTweaksConfig")
                .getField("INSTANCE").get(null) as Config
            if (config.tree == null) config.preload()
            val tree = config.tree ?: return
            if (attached === tree) return
            tree.getProp("hideCrosshairInPerspective")?.let { hidden ->
                val thirdPerson = Properties.functional(Supplier { hidden.get() != true }, Consumer { value: Boolean ->
                    hidden.setAs(!value)
                }, "arcadiumThirdPersonCrosshair", "Show Crosshair in Third Person",
                    "Show the crosshair in both front and rear third-person views.", Boolean::class.javaObjectType)
                thirdPerson.addMetadata("visualizer", Visualizer.SwitchVisualizer::class.java)
                thirdPerson.addMetadata("category", "General")
                thirdPerson.addMetadata("subcategory", "Visibility")
                thirdPerson.addMetadata("default", hidden.getMetadata<Boolean?>("default") != true)
                hidden.addDisplayCondition { Property.Display.HIDDEN }
                tree.put(thirdPerson)
            }
            val enabled = Properties.functional(Supplier { data.enabled }, Consumer { value: Boolean -> setEnabled(value) },
                "arcadiumCustomCrosshair", "Custom Crosshair", "Use your selected shape or pixel canvas.", Boolean::class.javaObjectType)
            enabled.addMetadata("visualizer", Visualizer.SwitchVisualizer::class.java)
            enabled.addMetadata("category", "Custom Crosshair")
            enabled.addMetadata("subcategory", "General")
            enabled.addMetadata("default", false)
            tree.put(enabled)
            val editor = Properties.dummy("arcadiumCrosshairEditor", "Crosshair Editor")
            editor.addMetadata("visualizer", Visualizer.ButtonVisualizer::class.java)
            editor.addMetadata("text", "Open editor")
            editor.addMetadata("category", "Custom Crosshair")
            editor.addMetadata("subcategory", "General")
            editor.addMetadata("runnable", Runnable {
                val mc = Minecraft.getInstance()
                mc.tell { mc.openScreen(CustomCrosshairScreen(mc.screen)) }
            })
            tree.put(editor)
            attached = tree
            ConfigRegistry.invalidate()
        }.onFailure {
            if (!warned) logger.warn("Could not add the custom crosshair controls to Crosshair Tweaks", it)
            warned = true
        }
    }

    @JvmStatic fun isActive(): Boolean = data.enabled && !cachedMesh.isEmpty
    @JvmStatic fun mesh(): CrosshairDesign.Mesh = cachedMesh
    @JvmStatic fun design(): CrosshairDesign = data.current
    @JvmStatic fun meshBuildCount(): Int = builds
    fun enabled(): Boolean = data.enabled
    fun profiles(): List<CrosshairDesign> = data.profiles.map(CrosshairDesign::copy)

    fun setEnabled(value: Boolean) {
        data.enabled = value
        save()
    }

    fun update(design: CrosshairDesign) {
        data.current = design.copy().apply { normalize() }
        cachedMesh = data.current.mesh()
        builds++
    }

    fun saveProfile(name: String): Boolean {
        val copy = data.current.copy().apply { this.name = name; normalize() }
        val index = data.profiles.indexOfFirst { it.name.equals(copy.name, true) }
        if (index >= 0) data.profiles[index] = copy
        else if (data.profiles.size < 64) data.profiles.add(copy) else return false
        return save()
    }

    fun deleteProfile(index: Int): Boolean {
        if (index !in data.profiles.indices) return false
        data.profiles.removeAt(index)
        return save()
    }

    fun save(): Boolean = runCatching {
        Files.createDirectories(file.parent)
        val temp = file.resolveSibling(file.fileName.toString() + ".tmp")
        Files.newBufferedWriter(temp).use { gson.toJson(data, it) }
        try { Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE) }
        catch (_: java.nio.file.AtomicMoveNotSupportedException) { Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING) }
        true
    }.getOrElse { logger.warn("Could not save crosshairs", it); false }
}
