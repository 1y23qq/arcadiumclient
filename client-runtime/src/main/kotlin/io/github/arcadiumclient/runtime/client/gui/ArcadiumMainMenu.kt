// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.client.gui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.awt.Desktop
import java.net.URI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.minecraft.client.Minecraft
import net.minecraft.client.Session
import net.minecraft.client.gui.screen.menu.options.OptionsScreen
import org.polyfrost.oneconfig.api.config.v1.ConfigManager
import org.polyfrost.oneconfig.api.ui.v1.OneConfigUI
import org.polyfrost.oneconfig.internal.ui.components.Icon
import org.polyfrost.oneconfig.internal.ui.compose.ComposeScreen
import org.polyfrost.oneconfig.internal.ui.themes.*
import io.github.arcadiumclient.runtime.ClientBuild
import io.github.arcadiumclient.runtime.client.ClientPreferences
import io.github.arcadiumclient.runtime.client.features.FirstRunSetup
import io.github.arcadiumclient.runtime.client.launcher.AccountRepository
import io.github.arcadiumclient.runtime.client.launcher.NativeAccountBridge
import io.github.arcadiumclient.runtime.mixin.client.access.SessionAccess

class ArcadiumMainMenu : ComposeScreen(RenderMode.CONTINUOUS) {
    private var setup by mutableStateOf(!ClientPreferences.setupComplete)
    private var page by mutableIntStateOf(0)
    private var sprint by mutableStateOf(true)
    private var overlay by mutableStateOf(true)

    @Composable
    override fun compose() {
        Theme {
            if (setup) {
                Setup()
                return@Theme
            }
            val mc = Minecraft.getInstance()
            var legal by remember { mutableStateOf(false) }
            Box(Modifier.fillMaxSize().background(UiWindowBackground)) {
                AccountMenu(Modifier.align(Alignment.TopStart).padding(20.dp))
                Column(Modifier.width(260.dp).align(Alignment.Center),
                    verticalArrangement = Arrangement.spacedBy(9.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(UI_ASSETS + "logo.svg", UiTextPrimary, Modifier.size(54.dp))
                    UiText("Arcadium", 28.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(14.dp))
                    UiButton("Singleplayer", Modifier.fillMaxWidth()) { mc.openScreen(ArcadiumWorldsScreen(this@ArcadiumMainMenu)) }
                    UiButton("Multiplayer", Modifier.fillMaxWidth()) { mc.openScreen(ArcadiumServersScreen(this@ArcadiumMainMenu)) }
                    UiButton("Arcadium Options", Modifier.fillMaxWidth(), filled = true) { OneConfigUI.open() }
                    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        UiButton("Options", Modifier.weight(1f)) { mc.openScreen(OptionsScreen(this@ArcadiumMainMenu, mc.options)) }
                        UiButton("Skins", Modifier.weight(1f)) { mc.openScreen(SkinChangerScreen(this@ArcadiumMainMenu)) }
                    }
                    UiButton("Quit", Modifier.fillMaxWidth()) { mc.stop() }
                }
                Row(Modifier.align(Alignment.BottomStart).padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    UiButton("Licenses") { legal = true }
                    UiButton("Discord") { browse(ClientBuild.SUPPORT_URL) }
                }
                if (legal) UiModalScrim({ legal = false }) {
                    ModalPanel(440.dp) {
                        UiText("Licenses", 19.sp, fontWeight = FontWeight.SemiBold)
                        UiText("Arcadium Client", 14.sp)
                        UiText("Launcher and configuration interface derived from OneLauncher and OneConfig. Copyright Polyfrost and contributors. Arcadium modifications copyright 2026 0aurorq3 and contributors.", 12.sp)
                        UiText("GPL version 3. This software comes with no warranty. You may redistribute and modify it under its license. Dependencies retain their own licenses.", 12.sp)
                        UiButton("Source and license notices", Modifier.fillMaxWidth()) {
                            browse("https://github.com/0aurorq3/arcadiumclient/blob/main/NOTICE.md")
                        }
                        UiButton("Done", Modifier.fillMaxWidth(), filled = true) { legal = false }
                    }
                }
            }
        }
    }

    @Composable
    private fun Setup() {
        Box(Modifier.fillMaxSize().background(UiWindowBackground), contentAlignment = Alignment.Center) {
            ModalPanel(450.dp) {
                UiText(when (page) { 0 -> "Select a theme"; 1 -> "Toggle Sprint"; 2 -> "Block Overlay"; else -> "Ready to play" },
                    22.sp, fontWeight = FontWeight.SemiBold)
                when (page) {
                    0 -> listOf(PolyGlassDark, PolyGlassLight, MinecraftDark, MinecraftLight).forEach { theme ->
                        UiButton(theme.name.replace("PolyGlass", "Glass"), Modifier.fillMaxWidth(),
                            filled = LocalTheme.current.name == theme.name) { ThemeRegistry.activate(theme) }
                    }
                    1 -> UiToggle("Toggle sprint with one press", sprint) { sprint = !sprint }
                    2 -> UiToggle("Show the selected block outline", overlay) { overlay = !overlay }
                    else -> UiText("You can change these settings in Arcadium Options.", color = UiTextSecondary)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (page > 0) UiButton("Back", Modifier.weight(1f)) { page-- }
                    UiButton(if (page == 3) "Done" else "Next", Modifier.weight(1f), filled = true) {
                        if (page < 3) page++ else {
                            FirstRunSetup.toggleSprint(sprint)
                            FirstRunSetup.blockOverlay(overlay)
                            ClientPreferences.setupComplete = true
                            ClientPreferences.save()
                            setup = false
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountMenu(modifier: Modifier) {
    val scope = rememberCoroutineScope()
    var accounts by remember { mutableStateOf<List<AccountRepository.StoredAccount>>(emptyList()) }
    var expanded by remember { mutableStateOf(false) }
    var add by remember { mutableStateOf(false) }
    var method by remember { mutableStateOf<String?>(null) }
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var progress by remember { mutableStateOf("") }
    var username by remember { mutableStateOf(Minecraft.getInstance().session.username) }

    suspend fun refresh() {
        accounts = withContext(Dispatchers.IO) { NativeAccountBridge.list().users.values.toList() }
    }
    fun run(action: suspend () -> Unit) {
        if (busy) return
        busy = true
        error = null
        scope.launch {
            try { action(); refresh() }
            catch (failure: Exception) { error = failure.message ?: "Account operation failed." }
            finally { busy = false; progress = "" }
        }
    }
    fun activate(account: AccountRepository.StoredAccount) {
        val mc = Minecraft.getInstance()
        check(mc.world == null) { "Leave the world before switching accounts." }
        val session = Session(account.username, account.id.replace("-", ""), account.accessToken,
            if (AccountRepository.isOnline(account)) "mojang" else "legacy")
        (mc as SessionAccess).`arcadium$session`(session)
        username = account.username
        expanded = false
        add = false
        method = null
        input = ""
    }
    LaunchedEffect(Unit) { runCatching { refresh() }.onFailure { error = "Open the Arcadium launcher to manage accounts." } }
    DisposableEffect(Unit) { onDispose { if (busy) NativeAccountBridge.cancelLogin() } }

    Column(modifier.widthIn(min = 220.dp, max = 300.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        UiButton(username, Modifier.fillMaxWidth(), enabled = !busy, icon = MENU_ICONS + "user.svg") { expanded = !expanded }
        if (expanded) {
            accounts.forEach { account ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    UiButton(account.username, Modifier.weight(1f), enabled = !busy, filled = account.username == username) {
                        run { activate(withContext(Dispatchers.IO) { NativeAccountBridge.switchAccount(account.id) }) }
                    }
                    UiIconButton(MENU_ICONS + "close.svg", tooltip = "Remove account") {
                        run { withContext(Dispatchers.IO) { NativeAccountBridge.removeAccount(account.id) } }
                    }
                }
            }
            UiButton("Add account", Modifier.fillMaxWidth(), enabled = !busy) { add = true; method = null }
        }
        error?.let { UiText(it, 11.sp, color = UiDangerColor) }
        if (busy) UiText(progress.ifBlank { "Working..." }, 12.sp, color = UiTextSecondary)
        if (add && method == null) {
            listOf("Microsoft", "Cookie", "Cracked", "Session").forEach { option ->
                UiButton(option, Modifier.fillMaxWidth(), enabled = !busy) {
                    method = option
                    when (option) {
                        "Microsoft" -> run {
                            try { activate(NativeAccountBridge.login()) } finally { method = null }
                        }
                        "Cookie" -> run {
                            try { NativeAccountBridge.importCookies { progress = it }?.let(::activate) } finally { method = null }
                        }
                    }
                }
            }
        }
        if (add && method in listOf("Cracked", "Session")) {
            val offline = method == "Cracked"
            UiTextField(input, { input = it }, Modifier.fillMaxWidth(),
                if (offline) "Username" else "Session token", maxLength = if (offline) 16 else 16384, password = !offline)
            UiButton("Add", Modifier.fillMaxWidth(), filled = true, enabled = !busy && input.isNotBlank()) {
                run {
                    val result = withContext(Dispatchers.IO) {
                        if (offline) NativeAccountBridge.addOffline(input) else NativeAccountBridge.importSession(input)
                    }
                    activate(result)
                }
            }
        }
        if (add) UiButton("Cancel", Modifier.fillMaxWidth()) { NativeAccountBridge.cancelLogin(); add = false; method = null; input = "" }
    }
}

private fun browse(url: String) {
    if (Desktop.isDesktopSupported()) runCatching { Desktop.getDesktop().browse(URI.create(url)) }
}
