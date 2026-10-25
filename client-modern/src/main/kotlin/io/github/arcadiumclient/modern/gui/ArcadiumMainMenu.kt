// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.modern.gui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import org.polyfrost.oneconfig.internal.ui.themes.accentAwareBackground as background
import org.polyfrost.oneconfig.internal.ui.themes.accentAwareBorder as border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import java.awt.Desktop
import java.net.URI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.options.OptionsScreen
import org.polyfrost.oneconfig.api.config.v1.ConfigManager
import org.polyfrost.oneconfig.api.ui.v1.OneConfigUI
import org.polyfrost.oneconfig.internal.ui.components.Icon
import org.polyfrost.oneconfig.internal.ui.compose.ComposeScreen
import org.polyfrost.oneconfig.internal.ui.themes.*
import io.github.arcadiumclient.modern.ClientBuild
import io.github.arcadiumclient.modern.client.ClientPreferences
import io.github.arcadiumclient.modern.client.MenuPreferences
import io.github.arcadiumclient.modern.client.background.PanoramaCatalog
import io.github.arcadiumclient.modern.client.launcher.AccountRepository
import io.github.arcadiumclient.modern.client.launcher.NativeAccountBridge
import io.github.arcadiumclient.modern.client.launcher.AccountSession

class ArcadiumMainMenu(private val showAccounts: Boolean = false) : ArcadiumScreen() {
    override val panoramaBlurRadius: Float
        get() = PanoramaCatalog.blurRadius(MenuPreferences.titleBlurPercent)
    override val shadesPanorama: Boolean get() = MenuPreferences.titleGradient
    private var setup by mutableStateOf(!ClientPreferences.setupComplete)
    private var page by mutableIntStateOf(0)

    override fun shouldCloseOnEsc(): Boolean = false

    @Composable
    override fun compose() {
        Theme {
            val mc = Minecraft.getInstance()
            var legal by remember { mutableStateOf(false) }
            BoxWithConstraints(Modifier.fillMaxSize()) {
                if (setup) Setup() else {
                    AccountMenu(Modifier.align(Alignment.TopStart).padding(16.dp), showAccounts) {
                        mc.setScreen(SkinChangerScreen(this@ArcadiumMainMenu))
                    }
                    val density = LocalDensity.current
                    val menuScale = minOf(maxWidth.value / 1280f, maxHeight.value / 720f).coerceIn(.73f, 1.2f)
                    CompositionLocalProvider(LocalDensity provides Density(density.density * menuScale, density.fontScale)) {
                        Column(Modifier.width(400.dp).align(Alignment.Center),
                            verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(UI_ASSETS + "logo.svg", UiTextPrimary, Modifier.size(72.dp))
                            UiText("ARCADIUM", 40.sp, fontWeight = FontWeight.Medium)
                            Spacer(Modifier.height(18.dp))
                            UiButton("Singleplayer", Modifier.fillMaxWidth().height(42.dp), icon = UI_ASSETS + "icons/user.svg") { mc.setScreen(ArcadiumWorldsScreen(this@ArcadiumMainMenu)) }
                            UiButton("Multiplayer", Modifier.fillMaxWidth().height(42.dp), icon = MENU_ICONS + "profiles.svg") { mc.setScreen(ArcadiumServersScreen(this@ArcadiumMainMenu)) }
                            LastServerJoined.address()?.let { address ->
                                UiButton("Last server: $address", Modifier.fillMaxWidth().height(34.dp)) { LastServerJoined.join(this@ArcadiumMainMenu) }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                UiButton("Options", Modifier.weight(1f).height(42.dp), icon = MENU_ICONS + "cog.svg") { mc.setScreen(OptionsScreen(this@ArcadiumMainMenu, mc.options)) }
                                UiButton("Arcadium Options", Modifier.weight(1f).height(42.dp)) { OneConfigUI.open() }
                            }
                        }
                    }
                    Row(Modifier.align(Alignment.BottomStart).padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        UiButton("Licenses") { legal = true }
                        UiButton("Discord") { browse(ClientBuild.SUPPORT_URL) }
                    }
                    UiButton("Background", Modifier.align(Alignment.BottomEnd).padding(16.dp),
                        icon = MENU_ICONS + "paintbrush.svg") {
                        mc.setScreen(TitleBackgroundScreen(this@ArcadiumMainMenu))
                    }
                    if (legal) UiModalScrim({ legal = false }) {
                        ModalPanel(440.dp) {
                            UiText("Licenses", 19.sp, fontWeight = FontWeight.SemiBold)
                            UiText("Arcadium Client", 14.sp)
                            UiText("Launcher and configuration interface derived from OneLauncher and OneConfig. Copyright Polyfrost and contributors. Arcadium modifications copyright 2026 0aurorq3 and contributors.", 12.sp)
                            UiText("GPL version 3. This software comes with no warranty. You may redistribute and modify it under its license. Dependencies retain their own licenses.", 12.sp)
                            UiButton("Source and license notices", Modifier.fillMaxWidth()) {
                                browse("https://github.com/1y23qq/arcadiumclient/blob/main/NOTICE.md")
                            }
                            UiButton("Done", Modifier.fillMaxWidth(), filled = true) { legal = false }
                        }
                    }
                }
                MainWindowActions(Modifier.align(Alignment.TopEnd).padding(16.dp))
            }
        }
    }

    @Composable
    private fun Setup() {
        OnboardingFlow(page, { page = it }) { setup = false }
    }
}

@Composable
private fun AccountMenu(modifier: Modifier, initiallyExpanded: Boolean, onSkins: () -> Unit) {
    val scope = rememberCoroutineScope()
    var accounts by remember { mutableStateOf<List<AccountRepository.StoredAccount>>(emptyList()) }
    var expanded by remember { mutableStateOf(initiallyExpanded) }
    var add by remember { mutableStateOf(false) }
    var method by remember { mutableStateOf<String?>(null) }
    var input by remember { mutableStateOf("") }
    var refreshToken by remember { mutableStateOf(false) }
    var oauthClientId by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var progress by remember { mutableStateOf("") }
    var username by remember { mutableStateOf(Minecraft.getInstance().user.name) }

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
        check(mc.level == null) { "Leave the world before switching accounts." }
        AccountSession.activate(account)
        username = account.username
        expanded = false
        add = false
        method = null
        input = ""
        oauthClientId = ""
        refreshToken = false
    }
    LaunchedEffect(Unit) { runCatching { refresh() }.onFailure { error = "Open the Arcadium launcher to manage accounts." } }
    DisposableEffect(Unit) { onDispose { if (busy) NativeAccountBridge.cancelLogin() } }

    val (interaction, hovered) = rememberUiHover()
    val arrow by animateFloatAsState(if (expanded) 180f else 0f, tween(150))
    val headerBorder by animateColorAsState(if (hovered && !busy) Accent else UiBorderColor)
    Column(modifier.width(232.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth().height(38.dp).clip(UiFieldShape).background(UiControlBackground)
            .border(1.dp, headerBorder, UiFieldShape).alpha(if (busy) .5f else 1f)
            .hoverable(interaction).semantics {
                contentDescription = "Account menu"
                stateDescription = if (expanded) "Expanded" else "Collapsed"
            }.clickable(interactionSource = interaction, indication = null, enabled = !busy, role = Role.Button) { expanded = !expanded }
            .padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(UI_ASSETS + "icons/user.svg", UiTextPrimary, Modifier.size(18.dp))
            UiText(username, 13.sp, Modifier.weight(1f), maxLines = 1)
            Icon(MENU_ICONS + "down.svg", UiTextPrimary, Modifier.size(15.dp).rotate(arrow))
        }
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
            UiButton("Add account", Modifier.fillMaxWidth(), enabled = !busy) {
                add = true; method = null; input = ""; oauthClientId = ""; refreshToken = false
            }
        }
        if (expanded || add) error?.let { UiText(it, 11.sp, color = UiDangerColor, maxLines = 3) }
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
            if (!offline) {
                UiDropdown("", listOf(false, true), refreshToken,
                    { if (it) "Microsoft refresh token" else "Minecraft access token" }, Modifier.fillMaxWidth()) { if (!busy) refreshToken = it }
                if (refreshToken) {
                    UiTextField(oauthClientId, { if (!busy) oauthClientId = it }, Modifier.fillMaxWidth(), "OAuth client ID (optional)", maxLength = 36)
                    UiText("Use the issuing app's client ID. Leave empty for an Arcadium token.", 11.sp, color = UiTextSecondary, maxLines = 3)
                }
            }
            UiTextField(input, { if (!busy) input = it }, Modifier.fillMaxWidth(),
                if (offline) "Username" else if (refreshToken) "Microsoft refresh token" else "Minecraft access token",
                maxLength = if (offline) 16 else 16384, password = !offline)
            UiButton("Add", Modifier.fillMaxWidth(), filled = true, enabled = !busy && input.isNotBlank()) {
                val value = input
                val refresh = refreshToken
                val clientId = oauthClientId
                if (!offline) input = ""
                run {
                    val result = withContext(Dispatchers.IO) {
                        if (offline) NativeAccountBridge.addOffline(value) else NativeAccountBridge.importSession(value, refresh, clientId)
                    }
                    activate(result)
                }
            }
        }
        if (add) UiButton("Cancel", Modifier.fillMaxWidth()) {
            NativeAccountBridge.cancelLogin(); add = false; method = null; input = ""; oauthClientId = ""; refreshToken = false
        }
        UiButton("Skins", Modifier.fillMaxWidth().height(34.dp), icon = UI_ASSETS + "icons/shirt.svg", onClick = onSkins)
    }
}

@Composable
private fun MainWindowActions(modifier: Modifier) {
    val mc = Minecraft.getInstance()
    var fullscreen by remember { mutableStateOf(mc.window.isFullscreen) }
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MainWindowButton(if (fullscreen) "Exit fullscreen" else "Enter fullscreen", "maximise.svg") {
            mc.execute { mc.window.toggleFullScreen(); fullscreen = mc.window.isFullscreen }
        }
        MainWindowButton("Quit game", "close.svg", danger = true) { mc.stop() }
    }
}

@Composable
private fun MainWindowButton(label: String, icon: String, danger: Boolean = false, onClick: () -> Unit) {
    val (interaction, hovered) = rememberUiHover()
    val background by animateColorAsState(if (danger) {
        if (hovered) Color(0xE6C65C70) else Color(0xB3B7677A)
    } else {
        if (hovered) Color(0xB34C6373) else Color(0x80506674)
    }, tween(130))
    Box(Modifier.size(32.dp).clip(uiShape(6.dp)).background(background)
        .border(1.dp, Color.White.copy(alpha = if (hovered) .5f else .3f), uiShape(6.dp))
        .hoverable(interaction).semantics { contentDescription = label }
        .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center) {
        Icon(MENU_ICONS + icon, Color.White, Modifier.size(16.dp))
    }
}

private fun browse(url: String) {
    if (Desktop.isDesktopSupported()) runCatching { Desktop.getDesktop().browse(URI.create(url)) }
}
