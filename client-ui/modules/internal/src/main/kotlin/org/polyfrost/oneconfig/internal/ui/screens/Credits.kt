package org.polyfrost.oneconfig.internal.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.polyfrost.oneconfig.api.platform.v1.ModInfo
import org.polyfrost.oneconfig.internal.ui.components.Text
import org.polyfrost.oneconfig.internal.ui.components.rememberBrandTint
import org.polyfrost.oneconfig.internal.ui.components.rememberSvgResourcePainter
import org.polyfrost.oneconfig.internal.ui.shell.ShellState
import org.polyfrost.oneconfig.internal.ui.themes.Accent
import org.polyfrost.oneconfig.internal.ui.themes.LocalTheme

private val oneConfigVersion: String? by lazy {
    runCatching {
        ModInfo.loadedMods.firstOrNull { it.id == "oneconfigv1" }?.version
    }.getOrNull()?.trim()?.takeIf { it.isNotEmpty() }
}

private data class CreditSection(val title: String, val entries: List<String>)

private val creditSections = listOf(
    CreditSection(
        "OneConfig contributors",
        listOf(
            "Wyvest",
            "Lynith",
            "Qewer",
            "Mo2men",
            "Caledonian",
            "nextdaydelivery",
            "Lunasa",
            "Mona",
            "Deftu",
            "ThinkSeal",
            "Zetvue (sound design)",
            "rachel",
            "Pauline",
            "xtrm",
            "MoonTidez",
            "DeDiamondPro",
        )
    ),
    CreditSection(
        "Special Thanks",
        listOf(
            "Scherso and FireStorm",
            "ImToggle for contributions to OneConfig and Polyfrost mods",
            "Sk1er for his lasting impact on Minecraft modding",
            "The Polyfrost team",
            "OneConfig's open source contributors and testers",
        )
    ),
)

@Composable
fun Credits() {
    val theme = LocalTheme.current
    val uriHandler = LocalUriHandler.current
    val scrollState = rememberScrollState()

    DisposableEffect(Unit) {
        ShellState.title = "Credits"
        onDispose { }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(scrollState).padding(end = 16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
            ) {
                rememberSvgResourcePainter(theme.branding.logoPath)?.let { logo ->
                    Image(
                        painter = logo,
                        contentDescription = null,
                        colorFilter = rememberBrandTint(theme.branding.logoPath, Accent),
                        modifier = Modifier.size(54.dp)
                    )
                }
                Text("Arcadium", color = theme.textColor, fontSize = 30.sp, modifier = Modifier.padding(start = 12.dp))
                oneConfigVersion?.let { version ->
                    Text(
                        "v$version",
                        color = theme.textColorSecondary,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(start = 8.dp, bottom = 6.dp)
                    )
                }
            }
            Text("Arcadium modifications: 0aurorq3 and contributors, 2026.", color = theme.textColorSecondary, fontSize = 13.sp)
            Text("Based on OneConfig by Polyfrost and contributors. This is an independent adaptation.", color = theme.textColorSecondary, fontSize = 13.sp)
            creditSections.forEach { section ->
                Text(
                    section.title,
                    color = theme.textColor,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    section.entries.forEach { entry ->
                        Text(" - $entry", color = theme.textColorSecondary, fontSize = 13.sp)
                    }
                }
            }
            Text(
                "License and source",
                color = theme.textColorSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)
            )
            Text("Conveyed under GNU GPL version 3. No warranty. Original copyright notices, licenses and OneConfig Additional Terms are retained.", color = theme.textColorSecondary, fontSize = 13.sp)
            listOf(
                "Arcadium source and notices" to "https://github.com/1y23qq/arcadiumclient",
                "Original OneConfig source" to "https://github.com/Polyfrost/OneConfig",
                "OneConfig Additional Terms" to "https://polyfrost.org/legal/oneconfig/additional-terms",
                "Arcadium support" to "https://discord.gg/QbNfaCHjUz",
            ).forEach { (label, url) ->
                Text(
                    label,
                    color = Accent,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 8.dp).clickable { uriHandler.openUri(url) }
                )
            }
        }
        VerticalScrollbar(
            adapter = rememberScrollbarAdapter(scrollState),
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight()
        )
    }
}
