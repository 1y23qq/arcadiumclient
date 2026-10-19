package org.polyfrost.oneconfig.api.ui.v1.keybind

import java.util.ServiceLoader
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.experimental.and
import kotlin.experimental.inv
import kotlin.experimental.or
import org.apache.logging.log4j.LogManager
import org.polyfrost.oneconfig.api.event.v1.eventHandler
import org.polyfrost.oneconfig.api.event.v1.events.KeyInputEvent
import org.polyfrost.oneconfig.api.event.v1.events.MouseInputEvent
import org.polyfrost.oneconfig.api.event.v1.events.ScreenOpenEvent
import org.polyfrost.oneconfig.api.event.v1.events.TickEvent
import org.polyfrost.oneconfig.api.event.v1.events.WindowFocusEvent
import org.polyfrost.oneconfig.api.platform.v1.Platform

@Suppress("UnstableApiUsage")
object KeybindManager {
    private val LOGGER = LogManager.getLogger("Arcadium Options/Keybinds")

    private val binds = CopyOnWriteArrayList<OneConfigKeybind>()
    private val activeBinds = HashSet<OneConfigKeybind>()

    /**
     * Bridge into Minecraft's native Controls menu if present
     */
    private val bridge: MinecraftKeybindBridge? by lazy {
        try {
            ServiceLoader.load(MinecraftKeybindBridge::class.java, MinecraftKeybindBridge::class.java.classLoader)
                .iterator().let { if (it.hasNext()) it.next() else null }
        } catch (t: Throwable) {
            LOGGER.warn("Failed to load MinecraftKeybindBridge; Arcadium Options keybinds will not appear in the Controls menu", t)
            null
        }
    }

    /** Listeners notified when a keybind is rebound from Minecraft's Controls menu */
    private val rebindListeners = ArrayList<(OneConfigKeybind, OneConfigKeybind) -> Unit>()

    /** Listeners notified when a keybind is edited in-place from Minecraft's Controls menu (same instance) */
    private val menuEditListeners = ArrayList<(OneConfigKeybind) -> Unit>()

    private val downKeys = HashSet<Int>()
    private val downMouse = HashSet<Int>()
    private var mods: Byte = KeyModifiers.NONE
    private class Press(val screen: Any?, val typing: Boolean, val epoch: Long, var eligible: Boolean)
    private class Transition(val bind: OneConfigKeybind, val press: Press, val down: Boolean)
    private val heldBinds = HashMap<OneConfigKeybind, Press>()
    private val pendingInputs = ArrayDeque<Transition>()
    private var inputEpoch = 0L

    init {
        eventHandler { (key, _, state): KeyInputEvent ->
            // key == 0 marks a character event not a coded key press and it never reports a release
            // so tracking it in downKeys would leave 0 stuck and match any keybind bound to code 0
            if (key == 0) return@eventHandler
            if (state == KeyInputEvent.REPEAT) return@eventHandler
            val down = state == KeyInputEvent.PRESSED
            val mod = KeyModifiers.of(key)
            if (mod != KeyModifiers.NONE) {
                mods = if (down) (mods or mod) else (mods and mod.inv())
            }
            if (down) downKeys.add(key) else downKeys.remove(key)
            captureTransitions()
        }

        eventHandler { (btn, state): MouseInputEvent ->
            if (state == 1) {
                downMouse += btn
            } else {
                downMouse -= btn
            }
            captureTransitions()
        }

        eventHandler { _: TickEvent.End ->
            checkBinds()
        }

        eventHandler { (screen): ScreenOpenEvent ->
            inputEpoch++
            if (screen == null) {
                TextInputFocus.clear()
                clearState()
            }
        }

        eventHandler { _: WindowFocusEvent.Lost ->
            clearState()
        }
    }

    @JvmStatic
    fun register(bind: OneConfigKeybind): OneConfigKeybind {
        if (bind !in binds) binds.add(bind)
        bridge?.register(bind)
        return bind
    }

    @JvmStatic
    fun unregister(bind: OneConfigKeybind) {
        binds.remove(bind)
        activeBinds.remove(bind)
        heldBinds.remove(bind)
        pendingInputs.removeAll { it.bind === bind }
        bridge?.unregister(bind)
    }

    @JvmStatic
    fun isRegistered(bind: OneConfigKeybind): Boolean = bind in binds

    /**
     * Re-push an already-registered keybind to the Minecraft Controls menu
     *
     * Use after mutating a keybind's [OneConfigKeybind.name]/[OneConfigKeybind.category] so the vanilla
     * mapping picks up the new metadata
     */
    @JvmStatic
    fun refreshMinecraftBinding(bind: OneConfigKeybind) {
        if (bind in binds) bridge?.register(bind)
    }

    /**
     * Swap a registered keybind for a new one while preserving its registration
     *
     * Setting the config property may either mutate [old] in place (so [old] and [new] are the same instance and
     * already registered) or hand back a fresh instance in which case the stale one is swapped out here
     *
     * Nothing happens if [old] was never registered
     */
    @JvmStatic
    fun replace(old: OneConfigKeybind?, new: OneConfigKeybind): OneConfigKeybind {
        if (old != null && new !== old) {
            if (new.name == null) new.name = old.name
            if (new.category == null) new.category = old.category
            if (new.defaultKeybind == null) new.defaultKeybind = old.defaultKeybind
        }
        if (old === new) {
            bridge?.sync(new)
            return new
        }
        if (old == null || old !in binds) return new
        unregister(old)
        register(new)
        return new
    }

    @JvmStatic
    fun addRebindListener(listener: (OneConfigKeybind, OneConfigKeybind) -> Unit) {
        rebindListeners.add(listener)
    }

    @JvmStatic
    fun addMenuEditListener(listener: (OneConfigKeybind) -> Unit) {
        menuEditListeners.add(listener)
    }

    /**
     * Notify that [bind] was mutated in place by Minecraft's Controls menu (rebind/unbind/reset)
     *
     * Re-syncs the vanilla mapping and notifies [menuEditListeners]
     */
    @JvmStatic
    fun notifyMenuEdit(bind: OneConfigKeybind) {
        for (listener in menuEditListeners) {
            try {
                listener(bind)
            } catch (t: Throwable) {
                LOGGER.error("Keybind menu-edit listener threw an exception", t)
            }
        }
    }

    @JvmStatic
    @JvmOverloads
    fun rebindFromMinecraft(
        old: OneConfigKeybind,
        keyCodes: IntArray?,
        mouseBtns: IntArray? = null,
        mods: Byte = KeyModifiers.NONE,
    ): OneConfigKeybind {
        old.keyCodes = keyCodes
        old.mouseBtns = mouseBtns
        old.mods = mods
        old.unresolvedKeyInputs = null
        old.unresolvedMouseInputs = null
        bridge?.sync(old)
        notifyMenuEdit(old)
        for (listener in rebindListeners) {
            try {
                listener(old, old)
            } catch (t: Throwable) {
                LOGGER.error("Keybind rebind listener threw an exception", t)
            }
        }
        return old
    }

    @JvmStatic
    fun findConflicts(bind: OneConfigKeybind): List<OneConfigKeybind> {
        if (!bind.isBound) return emptyList()
        return binds.filter { it.conflictsWith(bind) }
    }

    /** True when a registered keybind containing [button] matches the current input state */
    @JvmStatic
    fun hasTriggeredMouseBind(button: Int): Boolean = binds.any { isTriggeredByMouse(it, button) }

    /** True when [bind] contains [button] and matches the current input state */
    @JvmStatic
    fun isTriggeredByMouse(bind: OneConfigKeybind, button: Int): Boolean =
        bind.mouseBtns?.contains(button) == true && bind.test(downKeys, downMouse, mods)

    @JvmStatic
    fun builder() = KeybindHelper()

    private fun physicallyHeld(bind: OneConfigKeybind): Boolean =
        bind.isBound && (mods and bind.mods) == bind.mods &&
            (bind.keyCodes?.all { it in downKeys } != false) &&
            (bind.mouseBtns?.all { it in downMouse } != false)

    private fun captureTransitions() {
        val screen = Platform.screen().current<Any?>()
        val typing = TextInputFocus.isTyping
        for (bind in binds) {
            val press = heldBinds[bind]
            if (!physicallyHeld(bind)) {
                if (press != null) {
                    heldBinds.remove(bind)
                    pendingInputs.addLast(Transition(bind, press, false))
                }
            } else if (press == null) {
                val eligible = (!typing || bind.firesWhileTyping) && bind.test(downKeys, downMouse, mods)
                val next = Press(screen, typing, inputEpoch, eligible)
                heldBinds[bind] = next
                pendingInputs.addLast(Transition(bind, next, true))
            } else if (!sameContext(press, screen, typing)) {
                press.eligible = false
            }
        }
    }

    private fun sameContext(press: Press, screen: Any?, typing: Boolean): Boolean =
        press.epoch == inputEpoch && press.screen === screen && press.typing == typing

    private fun dispatch(bind: OneConfigKeybind, down: Boolean) {
        val changed = if (down) activeBinds.add(bind) else activeBinds.remove(bind)
        if (changed) {
            try {
                bind.action(down)
            } catch (t: Throwable) {
                LOGGER.error("Keybind action threw an exception", t)
            }
        }
    }

    private fun checkBinds() {
        // Keep actions on the tick thread while preserving taps completed between ticks.
        val inputs = pendingInputs.toList()
        pendingInputs.clear()
        for (input in inputs) {
            if (input.bind !in binds) continue
            if (input.down) {
                val press = input.press
                if (press.eligible && sameContext(press, Platform.screen().current<Any?>(), TextInputFocus.isTyping)) {
                    dispatch(input.bind, true)
                } else {
                    press.eligible = false
                }
            } else {
                dispatch(input.bind, false)
            }
        }
        for (bind in activeBinds.toList()) {
            val press = heldBinds[bind]
            if (press == null || !press.eligible ||
                !sameContext(press, Platform.screen().current<Any?>(), TextInputFocus.isTyping) ||
                !bind.test(downKeys, downMouse, mods)) {
                press?.eligible = false
                dispatch(bind, false)
            }
        }
    }

    private fun clearState() {
        inputEpoch++
        pendingInputs.clear()
        heldBinds.clear()
        for (bind in activeBinds) {
            try { bind.action(false) } catch (_: Throwable) {}
        }
        activeBinds.clear()
        downKeys.clear()
        downMouse.clear()
        mods = KeyModifiers.NONE
    }
}
