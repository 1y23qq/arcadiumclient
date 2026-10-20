// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.client.features

import org.apache.logging.log4j.LogManager
import org.polyfrost.oneconfig.api.config.v1.Config
import org.polyfrost.oneconfig.api.config.v1.ConfigManager
import org.polyfrost.oneconfig.api.config.v1.Properties
import org.polyfrost.oneconfig.api.config.v1.Property
import org.polyfrost.oneconfig.api.config.v1.Tree
import org.polyfrost.oneconfig.api.config.v1.Visualizer
import org.polyfrost.oneconfig.api.config.v1.annotations.Dropdown
import org.polyfrost.oneconfig.api.config.v1.annotations.Slider
import org.polyfrost.oneconfig.api.config.v1.annotations.Switch
import org.polyfrost.oneconfig.api.event.v1.eventHandler
import org.polyfrost.oneconfig.api.event.v1.events.TickEvent
import org.polyfrost.oneconfig.internal.ui.api.ConfigRegistry
import java.util.function.Consumer
import java.util.function.Supplier

object AnimationsConfig : Config("arcadium-animations.json", "Animations", Category.VISUALS) {
    @JvmStatic @Switch(title = "Enabled")
    var enabled = true

    @JvmStatic @Dropdown(title = "Mode", options = ["Overflow Animations", "Badlion Animations"])
    var mode = 0

    @JvmStatic @Slider(title = "Item Scale X", min = 0.1f, max = 3f, step = 0.01f, category = "Badlion Animations", subcategory = "Items")
    var itemScaleX = 1f
    @JvmStatic @Slider(title = "Item Scale Y", min = 0.1f, max = 3f, step = 0.01f, category = "Badlion Animations", subcategory = "Items")
    var itemScaleY = 1f
    @JvmStatic @Slider(title = "Item Scale Z", min = 0.1f, max = 3f, step = 0.01f, category = "Badlion Animations", subcategory = "Items")
    var itemScaleZ = 1f

    @JvmStatic @Switch(title = "Item Hold", description = "Use the 1.7 held item positions.", category = "Badlion Animations", subcategory = "Items")
    var itemHeld = true
    @JvmStatic @Switch(title = "Item Switch", description = "Keep the 1.7 equip behavior when a stack changes.", category = "Badlion Animations", subcategory = "Items")
    var itemSwitch = true
    @JvmStatic @Switch(title = "Hitbox", description = "Hide the eye height and look direction lines in F3+B hitboxes.", category = "Badlion Animations", subcategory = "Player")
    var hitBox = true
    @JvmStatic @Switch(title = "Inventory", description = "Use the relaxed 1.7 player pose in the inventory preview.", category = "Badlion Animations", subcategory = "Player")
    var inventory = true
    @JvmStatic @Switch(title = "Block Hit", description = "Animate swings while blocking with a sword.", category = "Badlion Animations", subcategory = "Items")
    var blockHit = true
    @JvmStatic @Switch(title = "Health Animation", description = "Use the 1.7 heart flash behavior.", category = "Badlion Animations", subcategory = "Player")
    var heartAnimation = true
    @JvmStatic @Switch(title = "Damage Animation", description = "Tint armor and held items with the player when hurt.", category = "Badlion Animations", subcategory = "Player")
    var damageAnimation = true
    @JvmStatic @Switch(title = "Sneaking", description = "Use the 1.7 sneak transition.", category = "Badlion Animations", subcategory = "Player")
    var sneakingAnimation = true
    @JvmStatic @Switch(title = "Eating", description = "Animate swings while eating and drinking.", category = "Badlion Animations", subcategory = "Items")
    var eatAnimation = true
    @JvmStatic @Switch(title = "Thrown Items", description = "Render dropped items as flat sprites facing the camera.", category = "Badlion Animations", subcategory = "Items")
    var thrownItems = true
    @JvmStatic @Switch(title = "Fishing Rod", description = "Use the 1.7 fishing rod position.", category = "Badlion Animations", subcategory = "Items")
    var fishingRod = true
    @JvmStatic @Switch(title = "Sword Swing", description = "Use the compact 1.7 sword swing position.", category = "Badlion Animations", subcategory = "Items")
    var swordSwing = true
    @JvmStatic @Switch(title = "Bow Animation", description = "Use the 1.7 bow position and allow bow swings.", category = "Badlion Animations", subcategory = "Items")
    var bowAnimation = true
    @JvmStatic @Switch(title = "Always Swing", description = "Continue visual swings while using an item or waiting after a miss.", category = "Badlion Animations", subcategory = "Items")
    var alwaysSwing = true
    @JvmStatic @Switch(title = "Remove Titles", category = "Badlion Animations", subcategory = "Screen")
    var removeTitles = false
    @JvmStatic @Switch(title = "Old Enchant Glint", category = "Badlion Animations", subcategory = "Items")
    var oldEnchantGlint = true
    @JvmStatic @Switch(title = "Left Handed", description = "Mirror the first person hand and held item.", category = "Badlion Animations", subcategory = "Items")
    var leftHanded = false
    @JvmStatic @Switch(title = "No Small Arms", description = "Render slim player skins with the wide arm model.", category = "Badlion Animations", subcategory = "Player")
    var noSmallArms = false
    @JvmStatic @Switch(title = "No Shake", description = "Disable the camera tilt when hurt.", category = "Badlion Animations", subcategory = "Screen")
    var noShake = false
    @JvmStatic @Slider(title = "Shake Intensity", min = 0f, max = 100f, step = 1f, category = "Badlion Animations", subcategory = "Screen")
    var shakeIntensity = 100f
    @JvmStatic @Switch(title = "Fix Liquid Placement", description = "Refresh the held bucket after a successful use.", category = "Badlion Animations", subcategory = "Items")
    var fixLiquidPlace = false
    @JvmStatic @Switch(title = "Movable Titles", category = "Badlion Animations", subcategory = "Screen")
    var movableTitles = false
    @JvmStatic @Slider(title = "Title X", description = "Horizontal offset as a fraction of the screen width.", min = -0.45f, max = 0.45f, step = 0.01f, category = "Badlion Animations", subcategory = "Screen")
    var titleX = 0f
    @JvmStatic @Slider(title = "Title Y", description = "Vertical offset as a fraction of the screen height.", min = -0.45f, max = 0.45f, step = 0.01f, category = "Badlion Animations", subcategory = "Screen")
    var titleY = 0f

    private val logger = LogManager.getLogger("Arcadium/Animations")
    private var bridge: AnimationModeBridge? = null
    private var overflowTree: Tree? = null
    private var displayTree: Tree? = null
    private var configuredTree: Tree? = null
    private var initialized = false
    private var failed = false
    @Volatile private var runtimeState: BadlionAnimationState? = null

    override fun initialize(byConfigManager: Boolean) {
        super.initialize(byConfigManager)
        configureTree()
    }

    fun initialize() {
        if (initialized) return
        initialized = true
        preload()
        attachOverflow()
        ConfigManager.addProfileChangeListener(object : ConfigManager.ProfileChangeListener {
            override fun onProfileSaving(profile: String) { bridge?.restore() }
            override fun onProfileChanged(newProfile: String) {
                bridge?.restore()
                bridge = null
                overflowTree = null
                configureTree()
                attachOverflow()
            }
        })
        eventHandler { _: TickEvent.End ->
            if (overflowTree == null && !failed) attachOverflow()
            if ((mode == 1 || !enabled) && bridge?.isAttached == false) applyRuntime()
        }
    }

    private fun configureTree() {
        val own = tree ?: return
        if (configuredTree === own) return
        configuredTree = own
        own.addMetadata("ui_tree_provider", Supplier { displayTree ?: own })
        own.addMetadata("aliases", listOf("Overflow Animations", "Badlion Animations", "Old Animations"))
        own.map.values.filterIsInstance<Property<*>>().forEach { property ->
            property.addCallback {
                if (!ConfigManager.isRebindingProfiles()) {
                    applyRuntime()
                    rebuildDisplayTree()
                }
                false
            }
        }
        rebuildDisplayTree()
    }

    private fun attachOverflow() {
        try {
            val type = Class.forName("org.polyfrost.overflowanimations.config.OverflowAnimationsConfig")
            type.getMethod("load").invoke(null)
            val config = type.getMethod("instance").invoke(null) as Config
            if (config.tree == null) config.preload()
            val native = config.tree ?: return
            if (overflowTree === native) return
            bridge?.restore()
            bridge = AnimationModeBridge(config)
            overflowTree = native
            applyRuntime()
            rebuildDisplayTree()
        } catch (failure: Throwable) {
            failed = true
            runtimeState = null
            logger.error("Could not initialize animation modes", failure)
        }
    }

    private fun applyRuntime() {
        try {
            val current = bridge ?: return
            runtimeState = if (enabled && mode == 1) snapshot() else null
            when {
                !enabled -> current.apply(emptyMap())
                mode == 1 -> current.apply(runtimeState!!.overflowValues())
                else -> current.restore()
            }
        } catch (failure: Throwable) {
            runtimeState = null
            bridge?.restore()
            logger.error("Could not apply animation mode", failure)
        }
    }

    private fun rebuildDisplayTree() {
        val own = tree ?: return
        val shown = Tree.tree(id).apply { title = "Animations" }
        shown.put(own.getProp("enabled"), own.getProp("mode"))
        if (mode == 1) {
            own.map.values.filter { it.id != "enabled" && it.id != "mode" }.forEach(shown::put)
            val noHit = Properties.functional(Supplier { NoHitDelayConfig.enabled }, Consumer { value: Boolean ->
                val shared = NoHitDelayConfig.tree?.getProp("enabled")
                if (shared != null) shared.setAs(value) else NoHitDelayConfig.enabled = value
                NoHitDelayConfig.save()
            }, "removeHitDelay", "Remove Hit Delay", "Uses the shared NoHitDelay setting.", Boolean::class.javaObjectType)
            noHit.addMetadata("visualizer", Visualizer.SwitchVisualizer::class.java)
            noHit.addMetadata("category", "Badlion Animations")
            noHit.addMetadata("subcategory", "Player")
            noHit.addMetadata("default", true)
            shown.put(noHit)
        } else {
            overflowTree?.map?.values?.forEach(shown::put)
        }
        displayTree = shown
        ConfigRegistry.invalidate()
    }

    private fun snapshot() = BadlionAnimationState(itemHeld, itemSwitch, hitBox, inventory, blockHit,
        heartAnimation, damageAnimation, sneakingAnimation, eatAnimation, thrownItems, fishingRod,
        swordSwing, bowAnimation, alwaysSwing, removeTitles, oldEnchantGlint, leftHanded, noSmallArms,
        noShake, fixLiquidPlace, movableTitles, shakeIntensity, titleX, titleY, itemScaleX, itemScaleY, itemScaleZ)

    @JvmStatic fun badlion(): BadlionAnimationState? = runtimeState
}
