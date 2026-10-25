package org.polyfrost.oneconfig.api.ui.v1.internal

import org.polyfrost.oneconfig.api.platform.v1.Platform
import org.polyfrost.oneconfig.api.ui.v1.OneConfigUIBridge
import org.polyfrost.oneconfig.api.ui.v1.page.ModConfigTarget
import org.polyfrost.oneconfig.internal.ui.compose.impls.OneConfigUIScreen
import org.polyfrost.oneconfig.internal.ui.navigation.graph.ModConfigRoute
import org.polyfrost.oneconfig.internal.ui.shell.LocalNavController

class OneConfigUIBridgeImpl : OneConfigUIBridge {
    private fun resolve(route: Any?): Any? = when (route) {
        is ModConfigTarget -> ModConfigRoute(route.id, route.category)
        else -> route
    }

    override fun createScreen(route: Any?): Any = OneConfigUIScreen.forRoute(resolve(route))

    override fun open(route: Any?) {
        if (route == null) {
            OneConfigUIScreen.openLastSession()
            return
        }
        // isReady stays true after the menu closes so the screen check is what tells open from closed
        if (LocalNavController.isReady && Platform.screen().current<Any?>() is OneConfigUIScreen) {
            LocalNavController.wrapper.navigate(resolve(route)!!)
        } else {
            Platform.screen().display(OneConfigUIScreen.forRoute(resolve(route)))
        }
    }
}
