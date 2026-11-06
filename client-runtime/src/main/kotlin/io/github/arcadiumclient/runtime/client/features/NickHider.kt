// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.client.features

import net.minecraft.client.Minecraft
import org.polyfrost.oneconfig.api.event.v1.eventHandler
import org.polyfrost.oneconfig.api.event.v1.events.TickEvent

object NickHider {
    private val cache = ThreadLocal.withInitial { NickMaskCache() }
    private data class State(val name: String, val nickname: String, val json: String, val enabled: Boolean, val policy: NickRenderPolicy)
    private val revisions = java.util.concurrent.atomic.AtomicLong()
    @Volatile private var state: State? = null
    private var refreshedRevision = Long.MIN_VALUE

    fun initialize() {
        NickHiderConfig.preload()
        org.polyfrost.oneconfig.internal.ui.api.ModuleSettingsPanels.register(NickHiderConfig.id, "Replacements") {
            io.github.arcadiumclient.runtime.client.gui.NickReplacementSettings()
        }
        eventHandler { _: TickEvent.End ->
            val name = sourceName()
            val policy = policy()
            val displayName = display(name)
            if (org.polyfrost.oneconfig.internal.ui.shell.ShellState.playerName != displayName) {
                org.polyfrost.oneconfig.internal.ui.shell.ShellState.playerName = displayName
            }
            if (refreshedRevision != policy.revision) {
                refreshedRevision = policy.revision
                cache.get().synchronize(policy)
                Minecraft.getInstance().gui?.chat?.reset()
                io.github.arcadiumclient.runtime.client.optimizations.RetainedNameTags.invalidate()
            }
        }
    }

    @JvmStatic
    fun sourceName(): String =
        Minecraft.getInstance().session.username

    @JvmStatic
    fun display(text: String): String {
        if (!NickHiderConfig.enabled) return text
        return cache.get().replace(text, policy(), org.polyfrost.oneconfig.internal.TextOptimizationOptions.nameMaskCache)
    }

    @JvmStatic fun cacheHits(): Long = cache.get().hits
    @JvmStatic fun cacheMisses(): Long = cache.get().misses

    @JvmStatic
    fun displayRuns(runs: List<String>): List<String> {
        if (!NickHiderConfig.enabled) return runs
        val policy = policy(); val local = cache.get(); local.synchronize(policy)
        val value = policy.replaceRuns(runs)
        if (value !== runs) value.forEach(local::rememberRendered)
        return value
    }

    internal fun policy(): NickRenderPolicy {
        val name = sourceName(); val nickname = NickHiderConfig.nickname; val json = NickHiderConfig.replacementRulesJson; val enabled = NickHiderConfig.enabled
        val current = state
        if (current != null && current.name == name && current.nickname == nickname && current.json == json && current.enabled == enabled) return current.policy
        val policy = NickRenderPolicy(revisions.incrementAndGet(), enabled, name, nickname, NickHiderConfig.rules())
        state = State(name, nickname, json, enabled, policy)
        return policy
    }
    @JvmStatic fun renderRevision(): Long = if (NickHiderConfig.enabled) policy().revision else 0
    @JvmStatic fun rememberRendered(text: String, revision: Long): String {
        if (!NickHiderConfig.enabled) return text
        val policy = policy(); val local = cache.get(); local.synchronize(policy)
        if (policy.enabled && policy.revision == revision) local.rememberRendered(text)
        return text
    }
    @JvmStatic fun beginRender(): Boolean = cache.get().enter()
    @JvmStatic fun endRender() { cache.get().exit() }
    @JvmStatic fun settingsChanged() {
        runCatching {
            val mc = Minecraft.getInstance()
            mc.tell {
                val policy = policy()
                if (refreshedRevision != policy.revision) {
                    refreshedRevision = policy.revision
                    cache.get().synchronize(policy)
                    mc.gui?.chat?.reset()
                    io.github.arcadiumclient.runtime.client.optimizations.RetainedNameTags.invalidate()
                }
            }
        }
    }
}
